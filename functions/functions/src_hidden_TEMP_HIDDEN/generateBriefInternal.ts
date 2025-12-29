import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import OpenAI from 'openai';
import { logUsage } from './usage';

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();
const openai = new OpenAI({
  // Migration: process.env first (future-proof), functions.config() as fallback
  apiKey: process.env.OPENAI_API_KEY || functions.config().openai?.key || '',
});

/**
 * Calculate macro targets based on user profile
 * Matches Android MacroTargetsCalculator exactly
 */
function calculateMacroTargets(
  profile: any,
  calorieGoal: number
): { proteinGrams: number; carbsGrams: number; fatGrams: number } {
  if (!profile || !calorieGoal || calorieGoal <= 0) {
    return { proteinGrams: 150, carbsGrams: 200, fatGrams: 65 };
  }

  const dietaryPreference = (profile.dietaryPreference || 'balanced').toLowerCase();
  const currentWeight = profile.currentWeight || 75;
  const goalWeight = profile.goalWeight || currentWeight;
  
  // Base ratios by dietary preference
  const ratios: Record<string, { carbs: number; protein: number; fat: number }> = {
    balanced: { carbs: 0.50, protein: 0.25, fat: 0.25 },
    high_protein: { carbs: 0.40, protein: 0.35, fat: 0.25 },
    moderate_low_carb: { carbs: 0.25, protein: 0.30, fat: 0.45 },
    ketogenic: { carbs: 0.05, protein: 0.20, fat: 0.75 },
    very_low_carb: { carbs: 0.05, protein: 0.35, fat: 0.60 },
    carnivore: { carbs: 0.01, protein: 0.45, fat: 0.55 },
    mediterranean: { carbs: 0.50, protein: 0.20, fat: 0.30 },
    plant_based: { carbs: 0.55, protein: 0.20, fat: 0.25 },
    vegetarian: { carbs: 0.55, protein: 0.20, fat: 0.25 },
    vegan: { carbs: 0.575, protein: 0.20, fat: 0.245 },
    paleo: { carbs: 0.35, protein: 0.30, fat: 0.35 },
    low_fat: { carbs: 0.65, protein: 0.20, fat: 0.15 },
    zone_diet: { carbs: 0.40, protein: 0.30, fat: 0.30 }
  };

  let { carbs: carbsRatio, protein: proteinRatio, fat: fatRatio } = ratios[dietaryPreference] || ratios.balanced;

  // Adjust for weight goal
  const goalTrend = goalWeight < currentWeight - 0.1 ? 'lose' : (goalWeight > currentWeight + 0.1 ? 'gain' : 'maintain');
  const strictDiets = ['ketogenic', 'very_low_carb', 'carnivore'];
  
  if (!strictDiets.includes(dietaryPreference)) {
    if (goalTrend === 'lose') {
      proteinRatio += 0.05;
      carbsRatio -= 0.05;
    } else if (goalTrend === 'gain') {
      carbsRatio += 0.05;
      fatRatio += 0.02;
      proteinRatio -= 0.02;
    }
  }

  // Clamp ratios
  carbsRatio = Math.max(0, Math.min(carbsRatio, 0.65));
  proteinRatio = Math.max(0.15, Math.min(proteinRatio, 0.45));
  fatRatio = Math.max(0.2, Math.min(fatRatio, 0.8));

  // Normalize
  const total = carbsRatio + proteinRatio + fatRatio;
  if (total > 0) {
    carbsRatio /= total;
    proteinRatio /= total;
    fatRatio /= total;
  }

  // Calculate protein with constraints
  const provisionalProtein = (calorieGoal * proteinRatio) / 4;
  const proteinMinPerKg = goalTrend === 'lose' ? 1.5 : (goalTrend === 'gain' ? 1.4 : 1.3);
  const proteinMaxPerKg = dietaryPreference === 'high_protein' ? 2.2 : (dietaryPreference === 'carnivore' ? 2.4 : 2.0);
  const proteinMin = Math.max(proteinMinPerKg * currentWeight, 80);
  const proteinMax = Math.min(Math.max(proteinMaxPerKg * currentWeight, proteinMin), 220);
  const proteinGrams = Math.round(Math.max(proteinMin, Math.min(provisionalProtein, proteinMax)));

  // Calculate fat minimum
  const minFatCalories = Math.max(calorieGoal * 0.20, currentWeight * 9 * 0.5);
  const caloriesAfterProtein = Math.max(calorieGoal - proteinGrams * 4, 0);
  const carbAndFatTotal = carbsRatio + fatRatio;
  const carbsShare = carbAndFatTotal > 0 ? carbsRatio / carbAndFatTotal : 0.6;

  let fatCalories = Math.max(caloriesAfterProtein * (1 - carbsShare), minFatCalories);
  fatCalories = Math.min(fatCalories, caloriesAfterProtein);
  const carbCalories = Math.max(caloriesAfterProtein - fatCalories, 0);

  const carbsGrams = Math.round(carbCalories / 4);
  const fatGrams = Math.round(fatCalories / 9);

  return { proteinGrams, carbsGrams, fatGrams };
}

/**
 * Internal function to generate brief (can be called from scheduled functions without auth)
 * This is the same logic as generateBrief but without the auth requirement
 */
export async function generateBriefInternal(userId: string, timeOfDay: 'morning' | 'afternoon' | 'evening'): Promise<{ brief: string }> {
  const today = new Date();
  const yesterday = new Date(today);
  yesterday.setDate(yesterday.getDate() - 1);
  const yesterdayStr = yesterday.toISOString().split('T')[0];
  const todayStr = today.toISOString().split('T')[0];

  try {
    // Get user profile
    const profileDoc = await db.collection('users').doc(userId).get();
    const profile = profileDoc.exists ? profileDoc.data() : {};

    // Check if user is new (account created less than 2 days ago)
    const accountCreatedAt = profile?.startDate || profile?.createdAt || Date.now();
    const accountCreatedDate = new Date(accountCreatedAt);
    const daysSinceCreation = (today.getTime() - accountCreatedDate.getTime()) / (1000 * 60 * 60 * 24);
    const isNewAccount = daysSinceCreation < 2;

    // Get goals
    const goalsDoc = await db
      .collection('users')
      .doc(userId)
      .collection('profile')
      .doc('goals')
      .get();
    const goals = goalsDoc.exists ? goalsDoc.data() : {};
    const useImperial = goals?.useImperial === true;

    // Calculate calorie goal from profile
    const weightKg = profile?.currentWeight || 75;
    const heightCm = profile?.heightCm || 170;
    const age = profile?.age || 30;
    const gender = String(profile?.gender || 'male').toLowerCase();
    const activity = String(profile?.activityLevel || 'lightly active').toLowerCase();

    const genderFactor = (gender === 'female' || gender === 'f' || gender === 'woman') ? -161 : 5;
    const bmr = 10 * weightKg + 6.25 * heightCm - 5 * age + genderFactor;

    const multipliers: Record<string, number> = {
      'sedentary': 1.2,
      'lightly active': 1.375,
      'moderately active': 1.55,
      'very active': 1.725,
      'extremely active': 1.9
    };
    const maintenance = Math.round(bmr * (multipliers[activity] || 1.375));

    const goalWeight = profile?.goalWeight || weightKg;
    let calorieGoal = maintenance;
    if (goalWeight < weightKg - 0.5) {
      const weightToLose = weightKg - goalWeight;
      const deficit = weightToLose > 10 ? 750 : (weightToLose >= 5 ? 600 : 500);
      calorieGoal = Math.max(maintenance - deficit, 1200);
    } else if (goalWeight > weightKg + 0.5) {
      const weightToGain = goalWeight - weightKg;
      const surplus = weightToGain > 10 ? 500 : (weightToGain >= 5 ? 400 : 300);
      calorieGoal = Math.min(maintenance + surplus, 3500);
    }

    // Calculate macro targets
    const macroTargets = calculateMacroTargets(profile, calorieGoal);

    // Get yesterday's data (for morning brief)
    const yesterdayLogsSnapshot = await db
      .collection('logs')
      .doc(userId)
      .collection('daily')
      .doc(yesterdayStr)
      .collection('entries')
      .get();
    const yesterdayLogs = yesterdayLogsSnapshot.docs.map(doc => doc.data());

    const yesterdayMeals = yesterdayLogs.filter(log => log.type === 'meal');
    const yesterdayWorkouts = yesterdayLogs.filter(log => log.type === 'workout');

    const yesterdayDailyLog = await db
      .collection('logs')
      .doc(userId)
      .collection('daily')
      .doc(yesterdayStr)
      .get();
    const yesterdayDailyData = yesterdayDailyLog.exists ? yesterdayDailyLog.data() : {};

    // Get today's data (for afternoon and evening briefs)
    const todayLogsSnapshot = await db
      .collection('logs')
      .doc(userId)
      .collection('daily')
      .doc(todayStr)
      .collection('entries')
      .get();
    const todayLogs = todayLogsSnapshot.docs.map(doc => doc.data());

    const todayMeals = todayLogs.filter(log => log.type === 'meal');
    const todayWorkouts = todayLogs.filter(log => log.type === 'workout');

    const todayDailyLog = await db
      .collection('logs')
      .doc(userId)
      .collection('daily')
      .doc(todayStr)
      .get();
    const todayDailyData = todayDailyLog.exists ? todayDailyLog.data() : {};

    // Calculate yesterday's totals
    const yesterdayWater = yesterdayDailyData?.water || 0;
    const yesterdayCalories = yesterdayMeals.reduce((sum, m) => sum + (m.calories || 0), 0);
    const yesterdayProtein = yesterdayMeals.reduce((sum, m) => sum + (m.protein || 0), 0);
    const yesterdayWorkoutMinutes = yesterdayWorkouts.reduce((sum, w) => sum + (w.durationMin || 0), 0);
    const yesterdayWorkoutCalories = yesterdayWorkouts.reduce((sum, w) => sum + (w.caloriesBurned || 0), 0);
    const yesterdaySleepHours = yesterdayDailyData?.sleepHours || 0;

    // Calculate today's totals (for afternoon/evening)
    const todayWater = todayDailyData?.water || 0;
    const todayCalories = todayMeals.reduce((sum, m) => sum + (m.calories || 0), 0);
    const todayProtein = todayMeals.reduce((sum, m) => sum + (m.protein || 0), 0);
    const todayCarbs = todayMeals.reduce((sum, m) => sum + (m.carbs || 0), 0);
    const todayFat = todayMeals.reduce((sum, m) => sum + (m.fat || 0), 0);
    const todayWorkoutMinutes = todayWorkouts.reduce((sum, w) => sum + (w.durationMin || 0), 0);
    const todayWorkoutCalories = todayWorkouts.reduce((sum, w) => sum + (w.caloriesBurned || 0), 0);
    const todaySteps = todayDailyData?.steps || 0;

    // Check if user has any logged data
    const hasAnyData = yesterdayCalories > 0 || yesterdayWater > 0 || yesterdayWorkoutMinutes > 0 || yesterdayMeals.length > 0 || yesterdayWorkouts.length > 0;

    // Return welcome message for new users or users with no data
    if (isNewAccount || !hasAnyData) {
      const welcomeBrief = `Welcome to Coachie! 👋 We're excited to have you on your health and wellness journey. To get started, I encourage you to go to "My Habits" and select 3 to 5 habits that you'd like to track. This will help you build consistency and see your progress over time. Try generating a weekly blueprint to plan your meals ahead, and check out the AI meal inspiration feature to discover recipes that help you hit your goals. Start logging your meals, workouts, water intake, and other activities today, and you'll see your personalized briefs here after you've logged some data. For more tips and answers to common questions, check out the Help & FAQ page in the 3-dot menu on the top right of your dashboard. Let's make today amazing!`;
      
      return { brief: welcomeBrief };
    }

    // Convert water to glasses (8 oz per glass) - ALWAYS use glasses
    const mlToGlasses = (ml: number): number => {
      const oz = ml * 0.033814;
      return Math.round(oz / 8);
    };
    const waterGoal = goals?.waterGoal || 2000;
    const waterGoalGlasses = mlToGlasses(waterGoal);

    // Build prompt based on time of day
    let prompt = '';
    
    if (timeOfDay === 'morning') {
      const waterGlasses = mlToGlasses(yesterdayWater);
      const yesterdayCarbs = yesterdayMeals.reduce((sum, m) => sum + (m.carbs || 0), 0);
      const yesterdayFat = yesterdayMeals.reduce((sum, m) => sum + (m.fat || 0), 0);
      
      prompt = `You are Coachie, an AI fitness coach. Generate a MORNING brief for ${profile?.name || 'the user'}.

CRITICAL: This is a MORNING brief. You MUST:
- Start with "Good morning" NOT "Good afternoon" or "Good evening"
- Review yesterday's data and remind them of today's goals
- This is NOT an afternoon or evening brief

YESTERDAY'S ACTUAL DATA (Review):
- Calories: ${yesterdayCalories} / ${calorieGoal} (${Math.round((yesterdayCalories / calorieGoal) * 100)}%)
- Protein: ${Math.round(yesterdayProtein)}g / ${macroTargets.proteinGrams}g (${Math.round((yesterdayProtein / macroTargets.proteinGrams) * 100)}%)
- Carbs: ${Math.round(yesterdayCarbs)}g / ${macroTargets.carbsGrams}g
- Fat: ${Math.round(yesterdayFat)}g / ${macroTargets.fatGrams}g
- Water: ${waterGlasses} glasses / ${waterGoalGlasses} glasses
- Workouts: ${yesterdayWorkouts.length} (${yesterdayWorkoutMinutes} min, ${yesterdayWorkoutCalories} cal burned)
- Sleep: ${yesterdaySleepHours.toFixed(1)} hours
- Meals: ${yesterdayMeals.length}

TODAY'S GOALS (Reminder):
- Calorie Goal: ${calorieGoal} calories
- Protein Goal: ${macroTargets.proteinGrams}g
- Carbs Goal: ${macroTargets.carbsGrams}g
- Fat Goal: ${macroTargets.fatGrams}g
- Water Goal: ${waterGoalGlasses} glasses
- Steps Goal: ${goals?.dailySteps || 10000} steps

MORNING BRIEF REQUIREMENTS:
1. Review yesterday's data - summarize what they accomplished
2. Remind them of today's goals and plans
3. Be encouraging and positive
4. Use subtle negative reinforcement only if needed to keep them on track
5. Water MUST be in GLASSES only (never ml, fl oz, oz, liters)
6. Use the EXACT numbers above - do not make up numbers
7. Keep it to 4-6 sentences
8. Use the user's actual name: ${profile?.name || 'there'}
${useImperial ? '\n9. CRITICAL: User has IMPERIAL units. Weight MUST be in lbs (pounds), NEVER kg. If mentioning weight loss/gain, convert kg to lbs (1 kg = 2.20462 lbs).' : ''}

Generate the morning brief now:`;

    } else if (timeOfDay === 'afternoon') {
      const waterGlasses = mlToGlasses(todayWater);
      const hasLunch = todayMeals.some(m => {
        const mealTime = m.timestamp ? new Date(m.timestamp).getHours() : null;
        return mealTime !== null && mealTime >= 11 && mealTime <= 14;
      });
      
      prompt = `You are Coachie, an AI fitness coach. Generate an AFTERNOON brief for ${profile?.name || 'the user'}.

CRITICAL: This is an AFTERNOON brief. You MUST:
- Start with "Good afternoon" NOT "Good morning" or "Good evening"
- Give a post-lunch update on today's progress
- This is NOT a morning or evening brief

TODAY'S PROGRESS SO FAR (Post-lunch update):
- Calories: ${todayCalories} / ${calorieGoal} (${Math.round((todayCalories / calorieGoal) * 100)}%)
- Protein: ${Math.round(todayProtein)}g / ${macroTargets.proteinGrams}g (${Math.round((todayProtein / macroTargets.proteinGrams) * 100)}%)
- Carbs: ${Math.round(todayCarbs)}g / ${macroTargets.carbsGrams}g
- Fat: ${Math.round(todayFat)}g / ${macroTargets.fatGrams}g
- Water: ${waterGlasses} glasses / ${waterGoalGlasses} glasses
- Workouts: ${todayWorkouts.length} (${todayWorkoutMinutes} min, ${todayWorkoutCalories} cal burned)
- Steps: ${todaySteps} / ${goals?.dailySteps || 10000}
- Meals logged: ${todayMeals.length}
- Has logged lunch: ${hasLunch ? 'Yes' : 'No'}

TODAY'S GOALS:
- Calorie Goal: ${calorieGoal} calories
- Protein Goal: ${macroTargets.proteinGrams}g
- Carbs Goal: ${macroTargets.carbsGrams}g
- Fat Goal: ${macroTargets.fatGrams}g
- Water Goal: ${waterGoalGlasses} glasses

AFTERNOON BRIEF REQUIREMENTS:
1. Give a post-lunch update on today's data and progress
2. If lunch hasn't been logged (hasLunch: No), remind them to log their lunch
3. Be encouraging and positive
4. Use subtle negative reinforcement only if needed to keep them on track
5. Water MUST be in GLASSES only (never ml, fl oz, oz, liters)
6. Use the EXACT numbers above - do not make up numbers
7. Keep it to 4-6 sentences
8. Use the user's actual name: ${profile?.name || 'there'}
${useImperial ? '\n9. CRITICAL: User has IMPERIAL units. Weight MUST be in lbs (pounds), NEVER kg. If mentioning weight loss/gain, convert kg to lbs (1 kg = 2.20462 lbs).' : ''}

Generate the afternoon brief now:`;

    } else { // evening
      const waterGlasses = mlToGlasses(todayWater);
      const caloriesRemaining = Math.max(0, calorieGoal - todayCalories);
      const proteinRemaining = Math.max(0, macroTargets.proteinGrams - todayProtein);
      
      prompt = `You are Coachie, an AI fitness coach. Generate an EVENING brief for ${profile?.name || 'the user'}.

CRITICAL: This is an EVENING brief. You MUST:
- Start with "Good evening" NOT "Good morning" or "Good afternoon"
- Focus on pre-dinner progress and what they need to do to reach today's goals
- This is NOT a morning brief - do NOT review yesterday's data

TODAY'S PROGRESS SO FAR (Pre-dinner update):
- Calories: ${todayCalories} / ${calorieGoal} (${Math.round((todayCalories / calorieGoal) * 100)}%) - ${caloriesRemaining} remaining
- Protein: ${Math.round(todayProtein)}g / ${macroTargets.proteinGrams}g (${Math.round((todayProtein / macroTargets.proteinGrams) * 100)}%) - ${Math.round(proteinRemaining)}g remaining
- Carbs: ${Math.round(todayCarbs)}g / ${macroTargets.carbsGrams}g
- Fat: ${Math.round(todayFat)}g / ${macroTargets.fatGrams}g
- Water: ${waterGlasses} glasses / ${waterGoalGlasses} glasses
- Workouts: ${todayWorkouts.length} (${todayWorkoutMinutes} min, ${todayWorkoutCalories} cal burned)
- Steps: ${todaySteps} / ${goals?.dailySteps || 10000}
- Meals logged: ${todayMeals.length}

TODAY'S GOALS:
- Calorie Goal: ${calorieGoal} calories
- Protein Goal: ${macroTargets.proteinGrams}g
- Carbs Goal: ${macroTargets.carbsGrams}g
- Fat Goal: ${macroTargets.fatGrams}g
- Water Goal: ${waterGoalGlasses} glasses

EVENING BRIEF REQUIREMENTS:
1. Give a pre-dinner update on today's data and progress
2. Suggest what they need to do to reach today's goals (calories, protein, etc.)
3. SPECIFICALLY suggest using the AI meal inspiration feature for dinner to help reach their goals
4. Be encouraging and positive
5. Use subtle negative reinforcement only if needed to keep them on track
6. Water MUST be in GLASSES only (never ml, fl oz, oz, liters)
7. Use the EXACT numbers above - do not make up numbers
8. Keep it to 4-6 sentences
9. Use the user's actual name: ${profile?.name || 'there'}
10. CRITICAL: When suggesting protein sources, be REALISTIC. Do NOT suggest insane amounts like "8 eggs" or "7 chicken breasts". Instead, suggest reasonable meal combinations like "a chicken breast with eggs" or "protein-rich meals throughout the day". Keep suggestions practical and achievable.
${useImperial ? '\n11. CRITICAL: User has IMPERIAL units. Weight MUST be in lbs (pounds), NEVER kg. If mentioning weight loss/gain, convert kg to lbs (1 kg = 2.20462 lbs).' : ''}

Generate the evening brief now:`;
    }

    const fallbackBrief = timeOfDay === 'morning' 
      ? 'Good morning! Ready to make today great?'
      : (timeOfDay === 'afternoon' 
        ? 'Good afternoon! How is your day going?'
        : 'Good evening! Let\'s finish today strong!');

    const response = await openai.chat.completions.create({
      model: 'gpt-3.5-turbo',
      messages: [
        {
          role: 'system',
          content: `You are Coachie, an AI fitness coach. Provide accurate, specific ${timeOfDay} briefs with exact numbers from the data provided. 
          
CRITICAL RULES:
- For MORNING briefs: Start with "Good morning" and review yesterday's data
- For AFTERNOON briefs: Start with "Good afternoon" and give post-lunch update
- For EVENING briefs: Start with "Good evening" and give pre-dinner update with suggestions
- NEVER mix greetings - if it's evening, do NOT say "Good morning"
- Always use glasses for water, never ml or other units
- Be encouraging and positive, with only subtle negative reinforcement if needed
- Use the EXACT numbers provided - do not make up numbers
- CRITICAL: When suggesting protein sources, be REALISTIC and PRACTICAL. Do NOT suggest insane amounts like "8 eggs" or "7 chicken breasts". Instead, suggest reasonable meal combinations like "a protein-rich dinner with chicken and vegetables" or "spread protein across your remaining meals". Keep all suggestions practical and achievable for a normal person.
${useImperial ? '- CRITICAL: User has IMPERIAL units. Weight MUST be in lbs (pounds), NEVER kg or kilograms. If mentioning weight loss/gain, convert kg to lbs (1 kg = 2.20462 lbs).' : ''}`
        },
        { role: 'user', content: prompt }
      ],
      temperature: 0.7,
      max_tokens: 300
    });

    let brief = response.choices[0]?.message?.content?.trim() || fallbackBrief;

    // Log usage for brief generation
    try {
      const now = new Date();
      const date = now.toISOString().split('T')[0];
      await logUsage({
        userId: userId || 'system',
        date,
        timestamp: now.getTime(),
        source: `brief_${timeOfDay}`,
        model: 'gpt-3.5-turbo',
        promptTokens: response.usage?.prompt_tokens,
        completionTokens: response.usage?.completion_tokens,
        totalTokens: response.usage?.total_tokens,
        metadata: { timeOfDay, useImperial }
      });
    } catch (logError) {
      console.error('Failed to log brief generation usage:', logError);
    }

    // Post-process for imperial units
    if (useImperial && brief) {
      // Replace kg/kilograms with lbs for weight
      brief = brief.replace(/\b(\d+(?:\.\d+)?)\s*(?:kg|kilograms?|kilogrammes?)\b/gi, (match, num) => {
        const kgValue = parseFloat(num);
        const lbs = (kgValue * 2.20462).toFixed(1);
        return `${lbs} lbs`;
      });
    }

    return { brief };
  } catch (error: any) {
    console.error('Error generating brief:', error);
    const fallbackBrief = timeOfDay === 'morning' 
      ? 'Good morning! Ready to make today great?'
      : (timeOfDay === 'afternoon' 
        ? 'Good afternoon! How is your day going?'
        : 'Good evening! Let\'s finish today strong!');
    return { brief: fallbackBrief };
  }
}

