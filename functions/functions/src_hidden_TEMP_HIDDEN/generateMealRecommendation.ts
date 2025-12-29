import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import OpenAI from 'openai';
import { getUserSubscriptionTier, SubscriptionTier } from './subscriptionVerification';
import { logUsage } from './usage';

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();
const openai = new OpenAI({
  apiKey: process.env.OPENAI_API_KEY || functions.config().openai?.key || '',
});

/**
 * Calculate macro targets based on user profile
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

  carbsRatio = Math.max(0, Math.min(carbsRatio, 0.65));
  proteinRatio = Math.max(0.15, Math.min(proteinRatio, 0.45));
  fatRatio = Math.max(0.2, Math.min(fatRatio, 0.8));

  const total = carbsRatio + proteinRatio + fatRatio;
  if (total > 0) {
    carbsRatio /= total;
    proteinRatio /= total;
    fatRatio /= total;
  }

  const provisionalProtein = (calorieGoal * proteinRatio) / 4;
  const proteinMinPerKg = goalTrend === 'lose' ? 1.5 : (goalTrend === 'gain' ? 1.4 : 1.3);
  const proteinMaxPerKg = dietaryPreference === 'high_protein' ? 2.2 : (dietaryPreference === 'carnivore' ? 2.4 : 2.0);
  const proteinMin = Math.max(proteinMinPerKg * currentWeight, 80);
  const proteinMax = Math.min(Math.max(proteinMaxPerKg * currentWeight, proteinMin), 220);
  const proteinGrams = Math.round(Math.max(proteinMin, Math.min(provisionalProtein, proteinMax)));

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
 * Generate meal recommendation using AI
 * Supports both free tier (Gemini Flash via OpenAI gpt-4o-mini) and Pro tier (OpenAI gpt-3.5-turbo)
 * 
 * Deployed to multiple regions for global performance:
 * - us-central1 (Iowa, USA) - Americas
 * - europe-west1 (Belgium) - Europe
 * - asia-southeast1 (Singapore) - Asia-Pacific
 */
export const generateMealRecommendation = functions
  .region('us-central1', 'europe-west1', 'asia-southeast1')
  .https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = context.auth.uid;
  const { 
    platform,
    selectedIngredients = [], // Array of ingredient names
    mealType = null, // 'breakfast', 'brunch', 'lunch', 'dinner', 'dessert'
    cookingMethod = null, // 'grill', 'bake', 'roast', 'saute', etc.
    useImperial = true // Unit preference
  } = data;

  try {
    // Get user profile
    const userDoc = await db.collection('users').doc(userId).get();
    if (!userDoc.exists) {
      throw new functions.https.HttpsError('not-found', 'User profile not found');
    }

    const profile = userDoc.data();
    if (!profile) {
      throw new functions.https.HttpsError('not-found', 'User profile data not found');
    }
    
    // Get unit preference from user goals if not provided
    let finalUseImperial = useImperial;
    if (useImperial === undefined || useImperial === null) {
      try {
        const goalsDoc = await db.collection('users').doc(userId).collection('goals').doc('preferences').get();
        finalUseImperial = goalsDoc.data()?.useImperial ?? true;
      } catch {
        finalUseImperial = true; // Default to imperial
      }
    }

    // Check subscription tier
    const tier = await getUserSubscriptionTier(userId);
    
    // Get today's meals to calculate current macros
    const today = new Date().toISOString().split('T')[0];
    const todayLogDoc = await db.collection('logs').doc(userId).collection('daily').doc(today).get();
    const todayLog = todayLogDoc.data() || {};
    const healthLogs = todayLog.healthLogs || [];
    
    const mealLogs = healthLogs.filter((log: any) => log.type === 'meal');
    const currentMacros = {
      calories: mealLogs.reduce((sum: number, log: any) => sum + (log.calories || 0), 0),
      protein: mealLogs.reduce((sum: number, log: any) => sum + (log.protein || 0), 0),
      carbs: mealLogs.reduce((sum: number, log: any) => sum + (log.carbs || 0), 0),
      fat: mealLogs.reduce((sum: number, log: any) => sum + (log.fat || 0), 0),
    };

    // Calculate macro targets
    const currentWeight = profile.currentWeight || 75;
    const goalWeight = profile.goalWeight || currentWeight;
    const activityLevel = profile.activityLevel || 'moderately active';
    
    // Calculate calorie goal (simplified - matches Android logic)
    const bmr = (profile.gender === 'female' ? 655 : 66) + 
                (9.6 * currentWeight) + 
                (1.8 * (profile.heightCm || 175)) - 
                (4.7 * (profile.age || 30));
    
    const activityMultipliers: Record<string, number> = {
      'sedentary': 1.2,
      'lightly active': 1.375,
      'moderately active': 1.55,
      'very active': 1.725,
      'extremely active': 1.9
    };
    
    let calorieGoal = Math.round(bmr * (activityMultipliers[activityLevel] || 1.55));
    
    if (goalWeight < currentWeight - 0.1) {
      calorieGoal -= 500; // Deficit for weight loss
    } else if (goalWeight > currentWeight + 0.1) {
      calorieGoal += 500; // Surplus for weight gain
    }

    const macroTargets = calculateMacroTargets(profile, calorieGoal);
    const remainingMacros = {
      calories: Math.max(0, calorieGoal - currentMacros.calories),
      protein: Math.max(0, macroTargets.proteinGrams - currentMacros.protein),
      carbs: Math.max(0, macroTargets.carbsGrams - currentMacros.carbs),
      fat: Math.max(0, macroTargets.fatGrams - currentMacros.fat),
    };

    // Build prompt
    const dietaryPreference = profile.dietaryPreference || 'balanced';
    const goalTrend = goalWeight < currentWeight - 0.1 ? 'lose_weight' : 
                     (goalWeight > currentWeight + 0.1 ? 'gain_weight' : 'maintain_weight');
    
    // Build ingredient constraint
    const ingredientConstraint = selectedIngredients && selectedIngredients.length > 0
      ? `CRITICAL: You MUST ONLY use these specific ingredients that the user has available:
${selectedIngredients.map((ing: string) => `- ${ing}`).join('\n')}

DO NOT add any ingredients that are not in this list. If you need something like "chicken" or "lemons" but they are NOT in the list above, you CANNOT use them. Only use the ingredients the user has selected.`
      : 'You may use common ingredients that align with the dietary preference.';

    const prompt = `You are Coachie, a certified nutrition coach and recipe developer. Create a practical, easy-to-cook meal recommendation.

User Profile:
- Dietary Preference: ${dietaryPreference}
- Goal: ${goalTrend}
- Daily Calorie Goal: ${calorieGoal} calories
- Macro Targets: ${macroTargets.proteinGrams}g protein, ${macroTargets.carbsGrams}g carbs, ${macroTargets.fatGrams}g fat

Current Progress Today:
- Calories consumed: ${currentMacros.calories}/${calorieGoal}
- Protein: ${currentMacros.protein}/${macroTargets.proteinGrams}g
- Carbs: ${currentMacros.carbs}/${macroTargets.carbsGrams}g
- Fat: ${currentMacros.fat}/${macroTargets.fatGrams}g

Remaining Macros for Today:
- Calories: ${remainingMacros.calories}
- Protein: ${remainingMacros.protein}g
- Carbs: ${remainingMacros.carbs}g
- Fat: ${remainingMacros.fat}g

${ingredientConstraint}

Create a meal recommendation that:
1. Fits within the remaining macros
2. Aligns with the dietary preference: ${dietaryPreference}
3. Supports the goal: ${goalTrend}
4. Is practical and easy to cook
5. ${selectedIngredients && selectedIngredients.length > 0 ? 'ONLY uses the ingredients listed above - do not add any other ingredients' : 'Uses common ingredients'}

Return ONLY valid JSON in this exact format:
{
  "recipeTitle": "Meal name",
  "summary": "Brief description of the meal",
  "servings": 2,
  "ingredients": ["ingredient 1", "ingredient 2", "ingredient 3"],
  "instructions": "Step-by-step cooking instructions",
  "macrosPerServing": {
    "calories": 350,
    "protein": 30,
    "carbs": 25,
    "fat": 15,
    "sugar": 5,
    "addedSugar": 0
  }
}`;

    // Use different models based on tier
    const model = tier === SubscriptionTier.PRO ? 'gpt-3.5-turbo' : 'gpt-4o-mini';
    
    const response = await openai.chat.completions.create({
      model: model,
      messages: [
        {
          role: 'system',
          content: 'You are Coachie, a certified nutrition coach and recipe developer. Create practical, easy-to-cook meals using ONLY the ingredients the user has selected. NEVER add ingredients that are not in the user\'s selected list. Always ensure suggestions align with the dietary preference and macro goals. Respond in JSON only.'
        },
        {
          role: 'user',
          content: prompt
        }
      ],
      temperature: 0.6,
      max_tokens: 2000 // Increased for complete recipes with ingredient selection
    });

    const content = response.choices[0]?.message?.content?.trim();
    if (!content) {
      throw new Error('Empty response from AI');
    }

    // Log usage
    try {
      const now = new Date();
      const date = now.toISOString().split('T')[0];
      await logUsage({
        userId,
        date,
        timestamp: now.getTime(),
        source: 'meal_recommendation',
        model: model,
        promptTokens: response.usage?.prompt_tokens,
        completionTokens: response.usage?.completion_tokens,
        totalTokens: response.usage?.total_tokens,
        metadata: { platform: platform || 'web', tier }
      });
    } catch (logError) {
      console.error('Failed to log meal recommendation usage:', logError);
    }

    // Parse JSON from response
    let jsonText = content;
    const jsonStart = content.indexOf('{');
    const jsonEnd = content.lastIndexOf('}') + 1;
    if (jsonStart >= 0 && jsonEnd > jsonStart) {
      jsonText = content.substring(jsonStart, jsonEnd);
    }

    const recommendation = JSON.parse(jsonText);
    
    return {
      recommendation: recommendation,
      success: true
    };
  } catch (error: any) {
    console.error('[GENERATE_MEAL_RECOMMENDATION] Error:', error);
    throw new functions.https.HttpsError(
      'internal',
      `Failed to generate meal recommendation: ${error.message || 'Unknown error'}`
    );
  }
});

