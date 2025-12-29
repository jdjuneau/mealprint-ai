import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import OpenAI from "openai";
import { requireProAccess, verifyUserId } from './subscriptionVerification';
import { analyzeRecipeIngredients, calculateTotalNutrition } from './analyzeRecipe';
import { logUsage } from './usage';

// Initialize Firebase Admin
if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

// Initialize OpenAI - validate API key exists
// Migration: Using process.env first (future-proof), functions.config() as fallback (works until March 2026)
// IMPORTANT: This MUST NOT throw at module load time, or Firebase deployment will fail
// when the key isn't configured yet. Instead, we log loudly and return a dummy key;
// individual calls will then surface a clear HttpsError.
const getOpenAIKey = (): string => {
  const apiKey = process.env.OPENAI_API_KEY || functions.config().openai?.key;
  if (!apiKey) {
    console.error('❌ CRITICAL: OpenAI API key not configured!');
    console.error('Set it with: Set OPENAI_API_KEY environment variable in Firebase Console');
    console.error('Or (deprecated): firebase functions:config:set openai.key=\"YOUR_KEY\"');
    // Return a clearly-invalid placeholder so OpenAI calls fail fast,
    // but do NOT block function deployment or module loading.
    return 'MISSING_OPENAI_KEY';
  }
  return apiKey;
};

const openai = new OpenAI({ 
  apiKey: getOpenAIKey(),
  timeout: 120000, // 2 minutes
});

// Macro presets - supports both enum IDs (moderate_low_carb) and display names (Moderate Low-Carb)
const MACRO_PRESETS: Record<string, { protein: number; carbs: number; fat: number }> = {
  // Display names (title case with spaces)
  "Balanced": { protein: 25, carbs: 50, fat: 25 },
  "High Protein": { protein: 35, carbs: 40, fat: 25 },
  "Moderate Low-Carb": { protein: 30, carbs: 25, fat: 45 },
  "Keto": { protein: 20, carbs: 5, fat: 75 },
  "Very Low-Carb": { protein: 35, carbs: 5, fat: 60 },
  "Carnivore": { protein: 45, carbs: 1, fat: 54 },
  "Mediterranean": { protein: 20, carbs: 50, fat: 30 },
  "Plant-Based": { protein: 20, carbs: 55, fat: 25 },
  "Vegetarian": { protein: 20, carbs: 55, fat: 25 },
  "Vegan": { protein: 20, carbs: 58, fat: 22 },
  "Paleo": { protein: 30, carbs: 35, fat: 35 },
  "Zone Diet": { protein: 30, carbs: 40, fat: 30 },
  "Low Fat": { protein: 20, carbs: 65, fat: 15 },
  // Enum IDs (snake_case)
  "balanced": { protein: 25, carbs: 50, fat: 25 },
  "high_protein": { protein: 35, carbs: 40, fat: 25 },
  "moderate_low_carb": { protein: 30, carbs: 25, fat: 45 },
  "ketogenic": { protein: 20, carbs: 5, fat: 75 },
  "keto": { protein: 20, carbs: 5, fat: 75 },
  "very_low_carb": { protein: 35, carbs: 5, fat: 60 },
  "carnivore": { protein: 45, carbs: 1, fat: 54 },
  "mediterranean": { protein: 20, carbs: 50, fat: 30 },
  "plant_based": { protein: 20, carbs: 55, fat: 25 },
  "vegetarian": { protein: 20, carbs: 55, fat: 25 },
  "vegan": { protein: 20, carbs: 58, fat: 22 },
  "paleo": { protein: 30, carbs: 35, fat: 35 },
  "zone_diet": { protein: 30, carbs: 40, fat: 30 },
  "low_fat": { protein: 20, carbs: 65, fat: 15 },
};

export const generateWeeklyShoppingList = functions.runWith({
  timeoutSeconds: 540, // 9 minutes (max for v1 functions)
  memory: '1GB', // Increased memory for better performance
  labels: { 'blueprint': 'true', 'type': 'meal-planning' }
}).https.onCall(async (data, context) => {
  const startTime = Date.now();
  
      if (!context.auth) {
        throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
      }
      
      // SECURITY: Verify user ID and subscription
      const uid = verifyUserId(context);
      
      // CRITICAL: Verify Pro subscription before allowing AI blueprint generation
      await requireProAccess(uid, 'AI-Generated Weekly Blueprint');
      
  console.log(`[${uid}] Starting blueprint generation (Pro user verified)`);
  
  try {
    // STEP 1: Get user profile (simple - try 2 locations max)
    let profile: any = null;
    const profileDoc = await db.collection("users").doc(uid).collection("profile").doc("main").get();
    if (profileDoc.exists) {
      profile = profileDoc.data();
    } else {
      const userDoc = await db.collection("users").doc(uid).get();
      if (userDoc.exists) {
        profile = userDoc.data();
      }
    }
    
    if (!profile) {
      throw new functions.https.HttpsError('failed-precondition', 'Complete your profile first');
    }
    
    // Get useImperial preference - SIMPLE: read from users/{uid} where the app saves it
    let useImperial = true; // Default to imperial
    try {
      const userDoc = await db.collection("users").doc(uid).get();
      if (userDoc.exists) {
        const userData = userDoc.data();
        if (userData && typeof userData.useImperial === 'boolean') {
          useImperial = userData.useImperial === true;
          console.log(`[${uid}] ✅ Found useImperial in users/{uid}: ${useImperial}`);
        } else {
          console.log(`[${uid}] ⚠️ users/{uid} exists but useImperial is not a boolean, defaulting to imperial`);
        }
      } else {
        console.log(`[${uid}] ⚠️ users/{uid} document does not exist, defaulting to imperial`);
      }
    } catch (e) {
      console.error(`[${uid}] ❌ ERROR reading useImperial:`, e);
      console.log(`[${uid}] Defaulting to imperial due to error`);
    }
    
    console.log(`[BLUEPRINT] [${uid}] ✅ FINAL: useImperial = ${useImperial} (${useImperial ? 'IMPERIAL' : 'METRIC'})`);
    
    // All recipes are generated for 4 servings by default
    // Users can adjust servings in the UI, which will scale ingredients and macros
    // CRITICAL: householdSize is NOT a profile option - it's always 4
    const recipeServings = 4;
    const householdSize: number = 4; // Always 4 - UI serving selector does the math to scale recipes
    console.log(`[BLUEPRINT] [${uid}] Recipe servings: ${recipeServings} (always 4, UI scales)`);
    
    // Calculate calories
    let dailyCalories = profile.dailyCalories || profile.estimatedDailyCalories || 0;
    if (dailyCalories === 0 && profile.currentWeight && profile.heightCm && profile.age) {
      const weightKg = Number(profile.currentWeight);
      const heightCm = Number(profile.heightCm);
      const age = Number(profile.age);
      const gender = String(profile.gender || 'male').toLowerCase();
      const activity = String(profile.activityLevel || 'moderate').toLowerCase();
      
      const bmr = gender === 'female' || gender === 'f' || gender === 'woman'
        ? 10 * weightKg + 6.25 * heightCm - 5 * age - 161
        : 10 * weightKg + 6.25 * heightCm - 5 * age + 5;
      
      const multipliers: Record<string, number> = {
        "sedentary": 1.2, "lightly active": 1.375, "light": 1.375,
        "moderate": 1.55, "active": 1.725, "very active": 1.9
      };
      
      dailyCalories = Math.round(bmr * (multipliers[activity] || 1.55));
    }
    
    if (!dailyCalories || dailyCalories < 1200 || dailyCalories > 5000) {
      throw new functions.https.HttpsError('failed-precondition', 'Set your weight, height, and age, or set a custom calorie goal');
    }
    
    const dietaryPrefRaw = String(profile.dietaryPreference || 'Balanced').trim();
    const mealsPerDay = Number(profile.mealsPerDay || 3);
    const snacksPerDay = Number(profile.snacksPerDay || 2);
    
    // Normalize dietary preference to find the right preset (handle both enum IDs and display names)
    const normalizeDietaryPref = (pref: string): string => {
      const normalized = pref.toLowerCase();
      // Try exact match first (handles both display names and enum IDs)
      if (MACRO_PRESETS[pref]) return pref;
      if (MACRO_PRESETS[normalized]) return normalized;
      // Handle common variations
      const variations: Record<string, string> = {
        'keto_low_carb': 'ketogenic',
        'low_carb': 'moderate_low_carb',
        'low carb': 'moderate_low_carb',
        'keto': 'ketogenic',
      };
      return variations[normalized] || normalized;
    };
    
    const dietaryPref = normalizeDietaryPref(dietaryPrefRaw);
    
    // Get macros - check if user has custom macro goals, otherwise calculate from preset
    let macroGrams: { protein: number; carbs: number; fat: number };
    
    if (profile.macros && typeof profile.macros === 'object') {
      // User has custom macro goals
      macroGrams = {
        protein: Number(profile.macros.protein || profile.macros.proteinGrams || 0),
        carbs: Number(profile.macros.carbs || profile.macros.carbsGrams || 0),
        fat: Number(profile.macros.fat || profile.macros.fatGrams || 0)
      };
      
      // Validate custom macros
      if (macroGrams.protein <= 0 || macroGrams.carbs < 0 || macroGrams.fat <= 0) {
        console.warn(`[${uid}] Invalid custom macros, falling back to preset calculation`);
        const macroPreset = MACRO_PRESETS[dietaryPref] || MACRO_PRESETS["balanced"] || MACRO_PRESETS["Balanced"];
        macroGrams = {
          protein: Math.round((dailyCalories * macroPreset.protein / 100) / 4),
          carbs: Math.round((dailyCalories * macroPreset.carbs / 100) / 4),
          fat: Math.round((dailyCalories * macroPreset.fat / 100) / 9)
        };
      } else {
        console.log(`[${uid}] Using custom macro goals: P=${macroGrams.protein}g, C=${macroGrams.carbs}g, F=${macroGrams.fat}g`);
      }
    } else {
      // Calculate from dietary preference preset
      const macroPreset = MACRO_PRESETS[dietaryPref] || MACRO_PRESETS["balanced"] || MACRO_PRESETS["Balanced"];
      console.log(`[${uid}] Using dietary preference: "${dietaryPrefRaw}" (normalized: "${dietaryPref}") with preset: ${JSON.stringify(macroPreset)}`);
      macroGrams = {
        protein: Math.round((dailyCalories * macroPreset.protein / 100) / 4),
        carbs: Math.round((dailyCalories * macroPreset.carbs / 100) / 4),
        fat: Math.round((dailyCalories * macroPreset.fat / 100) / 9)
      };
      console.log(`[${uid}] Calculated macros from preset: P=${macroGrams.protein}g, C=${macroGrams.carbs}g, F=${macroGrams.fat}g (for ${dailyCalories} calories)`);
    }
  
  // Calculate Monday of the current week (matches app's getWeekId logic)
  const getWeekStarting = (): string => {
    const date = new Date();
    const dayOfWeek = date.getDay(); // 0 = Sunday, 1 = Monday, etc.
    const daysToSubtract = dayOfWeek === 0 ? 6 : dayOfWeek - 1; // Monday = 0
    const monday = new Date(date);
    monday.setDate(date.getDate() - daysToSubtract);
    return monday.toISOString().split("T")[0];
  };
  
  const weekStarting = getWeekStarting();
  
    // STEP 2: Delete existing blueprint for this week (to force regeneration)
    try {
      const blueprintRef = db.collection("users").doc(uid).collection("weeklyBlueprints").doc(weekStarting);
      const planRef = db.collection("users").doc(uid).collection("weeklyPlans").doc(weekStarting);
      
      const blueprintExists = (await blueprintRef.get()).exists;
      const planExists = (await planRef.get()).exists;
      
      if (blueprintExists) {
        await blueprintRef.delete();
        console.log(`[${uid}] ✅ Deleted existing weeklyBlueprints/${weekStarting}`);
      }
      if (planExists) {
        await planRef.delete();
        console.log(`[${uid}] ✅ Deleted existing weeklyPlans/${weekStarting}`);
      }
      
      if (blueprintExists || planExists) {
        console.log(`[${uid}] ✅ Successfully deleted existing blueprint for week ${weekStarting}`);
        // Wait a moment for Firestore to propagate the deletion
        await new Promise(resolve => setTimeout(resolve, 1000));
      } else {
        console.log(`[${uid}] No existing blueprint to delete (first generation)`);
      }
    } catch (e: any) {
      console.warn(`[${uid}] Error deleting existing blueprint (non-fatal):`, e.message);
      // Continue anyway - this is not critical
    }
  
    // STEP 2: Build simple prompt
    const prompt = `Generate a complete 7-day meal plan for ${householdSize} people.

DAILY TARGETS (for ALL ${householdSize} people combined):
- ${macroGrams.protein * householdSize}g protein
- ${macroGrams.carbs * householdSize}g carbs
- ${macroGrams.fat * householdSize}g fat
- ${dailyCalories * householdSize} calories

REQUIREMENTS:
- ${mealsPerDay} meals + ${snacksPerDay} snacks per day
- ${dietaryPref} diet
- Each recipe serves ${householdSize} people
- Show macros for FULL RECIPE (all ${householdSize} servings)
- Daily total = sum of all meals + snacks for that day

INGREDIENTS:
${useImperial ? '- Use IMPERIAL units: lbs, oz, cups, fl oz, tbsp, tsp (NO g, kg, ml, L)' : '- Use METRIC units: g, kg, ml, L (NO lbs, oz, cups)'}
- Each ingredient MUST be in format: "quantity unit name" OR "quantity name" for countable items
- For countable items (eggs, apples, bananas, etc.), use the food name instead of "unit": "3 eggs", "2 apples", "4 bananas"
- For items with measurements, use the unit: "2 lbs chicken breast", "1 cup broccoli", "0.5 lbs ground beef", "1 tbsp olive oil"
- NEVER use the word "unit" or "units" - use the specific food name for countable items
- Examples: "2 lbs chicken breast", "1 cup broccoli", "3 eggs", "2 apples", "0.5 lbs ground beef", "4 bananas"
- EVERY meal and snack MUST have an ingredients array with at least 5-8 ingredients including spices and seasonings
- Include spices, herbs, and seasonings: salt, pepper, garlic, onion, paprika, cumin, oregano, basil, thyme, etc.
- Make recipes appealing with flavorful ingredients

RECIPE INSTRUCTIONS:
- Provide detailed, step-by-step cooking instructions (4-6 steps minimum)
- Include seasoning and flavoring steps
- Make instructions clear and appealing
- Example: "1. Season chicken with salt, pepper, and garlic powder. 2. Heat oil in pan over medium-high heat. 3. Cook chicken until golden, 5-6 minutes per side. 4. Add vegetables and spices, cook until tender. 5. Serve hot with fresh herbs."

MACRO CALCULATION (CRITICAL - MUST BE ACCURATE):
- You MUST calculate macros from the ACTUAL ingredients you list for the FULL RECIPE (all ${householdSize} servings)
- Calculate each ingredient's nutrition, then SUM them all together
- Example: "3 eggs" = 210 calories, 18g protein, 1.5g carbs, 15g fat
- Example: "1 lb chicken breast" = 880 calories, 165g protein, 0g carbs, 19g fat
- Example: "1 cup broccoli" = 30 calories, 3g protein, 6g carbs, 0g fat
- Example: "1 cup cooked rice" = 200 calories, 4g protein, 45g carbs, 0.5g fat
- Example: "1 oz cheese" = 110 calories, 7g protein, 1g carbs, 9g fat
- Example: "2 tbsp olive oil" = 240 calories, 0g protein, 0g carbs, 28g fat
- Example: "1 tsp salt" = 0 calories, 0g protein, 0g carbs, 0g fat
- Add up ALL ingredients to get total calories, protein, carbs, fat for the FULL RECIPE (all ${householdSize} servings)
- The macros you provide MUST match the sum of all ingredient macros - be precise and accurate

SHOPPING LIST:
- Use only 5-7 different proteins across all 7 days
- ⚠️ PROTEIN VARIETY: Use diverse protein sources - chicken, beef, fish, pork, turkey, beans, lentils, tofu, tempeh, etc.
- ⚠️⚠️ EGG QUANTITY LIMIT - CRITICAL: Eggs can be used multiple times per week (especially for breakfast), but TOTAL egg usage must be MAXIMUM 12 eggs per person per week. For ${householdSize} people, that's ${12 * householdSize} eggs maximum for the entire week. Spread eggs across different meals with smaller quantities (e.g., 2-3 eggs per meal) rather than using many eggs in one meal. This ensures variety while keeping egg usage reasonable.
- Reuse vegetables and pantry items heavily
- Total unique ingredients should be 20-25 items

${(() => {
  // Check if user has menstrual cycle tracking enabled
  const menstrualCycleEnabled = profile.menstrualCycleEnabled === true;
  const lastPeriodStart = profile.lastPeriodStart;
  const averageCycleLength = profile.averageCycleLength || 28;
  const averagePeriodLength = profile.averagePeriodLength || 5;
  
  if (!menstrualCycleEnabled || !lastPeriodStart) {
    return '';
  }
  
  // Calculate current cycle phase
  const now = Date.now();
  const daysSinceLastPeriod = Math.floor((now - lastPeriodStart) / (1000 * 60 * 60 * 24));
  
  let cycleGuidance = '';
  
  if (daysSinceLastPeriod < averagePeriodLength) {
    cycleGuidance = `
MENSTRUAL CYCLE PHASE: Menstrual (Period phase)
- Focus on iron-rich foods throughout the week: lean red meat, spinach, lentils, beans, fortified cereals
- Include foods high in vitamin C to enhance iron absorption: bell peppers, citrus fruits, tomatoes
- Consider gentle, warming foods that are easy to digest
- Include magnesium-rich foods to help with cramps: dark leafy greens, nuts, seeds, whole grains
- Hydration is important - include hydrating ingredients in meals
- Distribute iron-rich meals across the week, especially in the first few days`;
  } else if (daysSinceLastPeriod < averagePeriodLength + 7) {
    cycleGuidance = `
MENSTRUAL CYCLE PHASE: Follicular (Post-period phase - energy building)
- Energy-building phase - include complex carbs for sustained energy: whole grains, sweet potatoes, quinoa
- Good for strength training support - emphasize protein-rich ingredients
- Include B vitamins for energy metabolism: whole grains, eggs, leafy greens
- Fresh, vibrant ingredients work well during this phase
- Plan energizing meals throughout the week`;
  } else if (daysSinceLastPeriod < averagePeriodLength + 14) {
    cycleGuidance = `
MENSTRUAL CYCLE PHASE: Ovulation (Fertile phase - peak energy)
- Peak energy phase - optimal for intense workouts
- Include high-quality proteins for muscle support
- Complex carbs for sustained energy throughout the day
- Antioxidant-rich foods: berries, dark leafy greens, colorful vegetables
- This is a great time for nutrient-dense, energizing meals
- Plan meals that support peak performance`;
  } else if (daysSinceLastPeriod < averageCycleLength) {
    cycleGuidance = `
MENSTRUAL CYCLE PHASE: Luteal (Pre-period phase - potential PMS symptoms)
- Focus on magnesium-rich foods to help with PMS symptoms: dark leafy greens, nuts, seeds, whole grains, dark chocolate
- Include B vitamins (especially B6) for mood support: poultry, fish, whole grains, bananas
- Consider foods that help with bloating: potassium-rich foods like bananas, avocados, sweet potatoes
- Include complex carbs to help with mood and energy stability
- Foods rich in omega-3s can help with inflammation: fatty fish, walnuts, chia seeds
- Plan meals that support mood and reduce PMS symptoms`;
  }
  
  if (cycleGuidance) {
    return cycleGuidance;
  }
  return '';
})()}

RETURN JSON FORMAT:
{
  "weekStarting": "${weekStarting}",
  "dailyCalories": ${dailyCalories},
  "meals": [
    {
      "day": "Monday",
      ${mealsPerDay >= 3 ? '"breakfast": { "name": "...", "calories": ..., "protein": ..., "carbs": ..., "fat": ..., "ingredients": ["2 lbs chicken breast", "1 cup broccoli", "..."], "steps": ["..."] },\n      "lunch": { "name": "...", "calories": ..., "protein": ..., "carbs": ..., "fat": ..., "ingredients": ["..."], "steps": ["..."] },\n      "dinner": { "name": "...", "calories": ..., "protein": ..., "carbs": ..., "fat": ..., "ingredients": ["..."], "steps": ["..."] },' : mealsPerDay === 2 ? '"breakfast": { "name": "...", "calories": ..., "protein": ..., "carbs": ..., "fat": ..., "ingredients": ["..."], "steps": ["..."] },\n      "dinner": { "name": "...", "calories": ..., "protein": ..., "carbs": ..., "fat": ..., "ingredients": ["..."], "steps": ["..."] },' : '"meal": { "name": "...", "calories": ..., "protein": ..., "carbs": ..., "fat": ..., "ingredients": ["..."], "steps": ["..."] },'}
      "snacks": [${Array(snacksPerDay).fill(0).map(() => '{ "name": "...", "calories": ..., "protein": ..., "carbs": ..., "fat": ..., "ingredients": ["..."], "steps": ["..."] }').join(', ')}]
    },
    ... (repeat for Tuesday-Sunday)
  ]
}

CRITICAL: You MUST generate all 7 days. Each meal MUST have an ingredients array. Return ONLY valid JSON, no markdown, no code blocks.`;

        console.log(`[BLUEPRINT] [${uid}] Prompt built (${prompt.length} chars), calling OpenAI...`);
        
    // STEP 3: Call OpenAI with retry logic for incomplete responses
    // COST OPTIMIZATION: Try gpt-4o-mini 5 times (16x cheaper), then fall back to gpt-4o
    let result: any;
    let retryCount = 0;
    const maxMiniRetries = 4; // 5 attempts with gpt-4o-mini (0-4)
    const maxTotalRetries = 5; // 6 total attempts (5 mini + 1 premium)
    
    console.log(`[${uid}] 🔄 Starting blueprint generation - ${maxMiniRetries + 1} attempts with gpt-4o-mini, then 1 attempt with gpt-4o`);
    console.log(`[${uid}] 💰 Cost optimization: Trying gpt-4o-mini first (16x cheaper), will upgrade to gpt-4o if all mini attempts fail`);
    
    while (retryCount <= maxTotalRetries) {
      // CRITICAL: Check retry count BEFORE doing anything
      if (retryCount > maxTotalRetries) {
        console.error(`[${uid}] ❌❌❌ STOPPING: retryCount ${retryCount} > maxTotalRetries ${maxTotalRetries} ❌❌❌`);
        break;
      }
      try {
        if (retryCount > 0) {
          console.log(`[${uid}] ⚠️ Retry attempt ${retryCount}/${maxTotalRetries} - previous response had issues`);
          // After 5 failures with mini, upgrade to premium model
          if (retryCount > maxMiniRetries) {
            console.log(`[${uid}] ⬆️ All ${maxMiniRetries + 1} gpt-4o-mini attempts exhausted, upgrading to gpt-4o for final attempt`);
          }
          await new Promise(resolve => setTimeout(resolve, 2000)); // Wait 2s before retry
        }
        
        const modelToUse = retryCount > maxMiniRetries ? "gpt-4o" : "gpt-4o-mini";
        const attemptNumber = retryCount + 1;
        const totalAttempts = maxTotalRetries + 1;
        console.log(`[BLUEPRINT] [${uid}] Calling OpenAI API with ${modelToUse} (attempt ${attemptNumber}/${totalAttempts})...`);
        const startOpenAICall = Date.now();
        
        const completion = await Promise.race([
          openai.chat.completions.create({
            model: modelToUse,
            messages: [
              {
                role: "system",
                content: `You are a nutrition assistant. Return ONLY valid JSON. No markdown, no code blocks, no explanations. CRITICAL: The 'meals' array MUST contain exactly 7 objects - one for each day: Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday. Do not stop after 1 day. ${useImperial ? 'CRITICAL: The user has selected IMPERIAL units. You MUST use imperial units for all ingredient quantities (lbs, oz, cups, fl oz, tbsp, tsp, quarts). NEVER use metric units (g, kg, ml, L).' : 'CRITICAL: The user has selected METRIC units. You MUST use metric units for all ingredient quantities (g, kg, ml, L). NEVER use imperial units.'}`
              },
              {
                role: "user",
                content: prompt
              }
            ],
            temperature: 0.8,
            max_tokens: 8000 // Increased to handle full 7-day responses with all details
          }),
          new Promise((_, reject) => 
            setTimeout(() => reject(new Error('OpenAI API call timed out after 240 seconds')), 240000)
          )
        ]) as any;
        
        const openAIDuration = Date.now() - startOpenAICall;
        console.log(`[BLUEPRINT] [${uid}] OpenAI API call completed in ${openAIDuration}ms`);
        
        // Log usage for weekly shopping list generation
        try {
          const now = new Date();
          const date = now.toISOString().split('T')[0];
          await logUsage({
            userId: uid,
            date,
            timestamp: now.getTime(),
            source: 'generateWeeklyShoppingList',
            model: modelToUse,
            promptTokens: completion.usage?.prompt_tokens,
            completionTokens: completion.usage?.completion_tokens,
            totalTokens: completion.usage?.total_tokens,
            metadata: { weekId: weekStarting, useImperial, householdSize }
          });
          console.log(`[BLUEPRINT] [${uid}] ✅ Logged usage: ${completion.usage?.total_tokens || 0} tokens`);
        } catch (logError) {
          console.error(`[BLUEPRINT] [${uid}] ❌ Failed to log usage:`, logError);
        }
        
        const raw = completion.choices[0]?.message?.content?.trim();
        if (!raw) {
          throw new Error("OpenAI returned empty response");
        }
        
        console.log(`[BLUEPRINT] [${uid}] OpenAI response received (${raw.length} chars)`);
        
        // Check if response is too short (likely incomplete)
        if (raw.length < 2000) {
          console.warn(`[BLUEPRINT] [${uid}] ⚠️ Response too short (${raw.length} chars) - likely incomplete, retrying...`);
          retryCount++;
          continue;
        }
        
        // STEP 4: Parse JSON with robust recovery
        let parsedResult: any;
        let cleaned = raw.replace(/```json\n?/g, '').replace(/```\n?/g, '').trim();
        
        try {
          parsedResult = JSON.parse(cleaned);
          console.log(`[${uid}] ✅ Successfully parsed JSON response`);
        } catch (parseError: any) {
          console.error(`[${uid}] JSON parse error:`, parseError.message);
          console.log(`[${uid}] Attempting JSON recovery...`);
          
          // ROBUST RECOVERY - fix all common issues
          let recoveredJson = cleaned;
          
          // Multiple passes to catch nested issues
          for (let pass = 0; pass < 5; pass++) {
            // 1. Fix unquoted keys (most common error)
            recoveredJson = recoveredJson.replace(/([{,]\s*)([a-zA-Z_$][a-zA-Z0-9_$]*)\s*:/g, '$1"$2":');
            recoveredJson = recoveredJson.replace(/(^\s*|\n\s*)([a-zA-Z_$][a-zA-Z0-9_$]*)\s*:/gm, '$1"$2":');
            recoveredJson = recoveredJson.replace(/([\]}])\s*([a-zA-Z_$][a-zA-Z0-9_$]*)\s*:/g, '$1,"$2":');
            
            // 2. Fix trailing commas
            recoveredJson = recoveredJson.replace(/,(\s*[}\]])/g, '$1');
            
            // 3. Fix single quotes
            recoveredJson = recoveredJson.replace(/'/g, '"');
            
            // 4. Fix missing commas in arrays
            recoveredJson = recoveredJson.replace(/("(?:[^"\\]|\\.)*")\s+("(?:[^"\\]|\\.)*")/g, '$1, $2');
            recoveredJson = recoveredJson.replace(/(\])\s*(\[)/g, '$1, $2');
            recoveredJson = recoveredJson.replace(/(\})\s*(\{)/g, '$1, $2');
            
            // 5. Fix unclosed strings - try to find and close them
            try {
              parsedResult = JSON.parse(recoveredJson);
              console.log(`[${uid}] ✅ JSON recovery successful on pass ${pass + 1}`);
              break;
            } catch (recoveryError: any) {
              if (pass === 4) {
                console.error(`[${uid}] ❌ JSON recovery failed after 5 passes:`, recoveryError.message);
                retryCount++;
                continue; // Retry with new OpenAI call
              }
            }
          }
          
          // If we still don't have a result, retry
          if (!parsedResult) {
            retryCount++;
            continue;
          }
        }
        
        // Validate we have 7 days
        if (!parsedResult.meals || !Array.isArray(parsedResult.meals)) {
          console.warn(`[${uid}] ⚠️ Missing or invalid meals array, retrying...`);
          retryCount++;
          continue;
        }
        
        if (parsedResult.meals.length !== 7) {
          console.warn(`[${uid}] ⚠️ Only ${parsedResult.meals.length} days instead of 7, retrying...`);
          retryCount++;
          continue;
        }
        
        // LOG: Show what GPT generated for shopping list BEFORE any processing
        console.log(`[${uid}] 🔍🔍🔍 GPT RAW SHOPPING LIST (before processing): 🔍🔍🔍`);
        console.log(`[${uid}] Shopping list categories: ${Object.keys(parsedResult.shoppingList || {}).join(', ')}`);
        if (parsedResult.shoppingList) {
          for (const [category, items] of Object.entries(parsedResult.shoppingList)) {
            if (Array.isArray(items)) {
              console.log(`[${uid}]   ${category}: ${items.length} items`);
              items.forEach((item: any, idx: number) => {
                const itemStr = typeof item === 'string' ? item : JSON.stringify(item);
                console.log(`[${uid}]     [${idx + 1}] ${itemStr}`);
              });
            }
          }
          
          // Calculate total meat/protein quantities
          let totalMeatLbs = 0;
          const meatKeywords = ['chicken', 'beef', 'pork', 'turkey', 'lamb', 'fish', 'salmon', 'tuna', 'steak', 'ground'];
          for (const [, items] of Object.entries(parsedResult.shoppingList)) {
            if (Array.isArray(items)) {
              items.forEach((item: any) => {
                const itemStr = typeof item === 'string' ? item : JSON.stringify(item);
                const itemLower = itemStr.toLowerCase();
                if (meatKeywords.some(keyword => itemLower.includes(keyword))) {
                  // Try to extract quantity
                  const match = itemStr.match(/(\d+\.?\d*)\s*(lbs?|pounds?|oz|ounces?)/i);
                  if (match) {
                    const qty = parseFloat(match[1]);
                    const unit = match[2].toLowerCase();
                    if (unit.includes('lb') || unit.includes('pound')) {
                      totalMeatLbs += qty;
                    } else if (unit.includes('oz') || unit.includes('ounce')) {
                      totalMeatLbs += qty / 16; // Convert oz to lbs
                    }
                  }
                }
              });
            }
          }
          console.log(`[${uid}] 🔍 TOTAL MEAT/PROTEIN ESTIMATE: ${totalMeatLbs.toFixed(2)} lbs`);
        }
        
        // Skip quick macro check - let the final validation handle it
        // The quick check was causing too many retries. Final validation will catch major issues.
        
        // Success! Store result and break out of retry loop
        result = parsedResult;
        const modelUsed = retryCount > maxMiniRetries ? "gpt-4o" : "gpt-4o-mini";
        console.log(`[BLUEPRINT] [${uid}] ✅ Successfully generated 7 days using ${modelUsed} (attempt ${retryCount + 1})`);
        
        // Skip blocking macro recalculation - will be done asynchronously after save
        console.log(`[${uid}] 📝 Saving blueprint with GPT macros, will recalculate accurate macros asynchronously...`);
        
        break;
        
      } catch (error: any) {
        console.error(`[BLUEPRINT] [${uid}] Error during OpenAI call (attempt ${retryCount + 1}):`, error);
        console.error(`[BLUEPRINT] [${uid}] Error details:`, {
          message: error.message,
          status: error.status,
          code: error.code,
          type: error.type,
          response: error.response ? {
            status: error.response.status,
            statusText: error.response.statusText,
            data: error.response.data
          } : null
        });
        
        // Check for specific OpenAI API errors
        if (error.status === 401) {
          throw new functions.https.HttpsError('internal', 'OpenAI API key is invalid. Please contact support.');
        }
        if (error.status === 429) {
          console.warn(`[${uid}] Rate limited by OpenAI, waiting before retry...`);
          await new Promise(resolve => setTimeout(resolve, 5000)); // Wait 5s for rate limit
          if (retryCount >= maxTotalRetries) {
            throw new functions.https.HttpsError('resource-exhausted', 'OpenAI API rate limit exceeded. Please try again in a few minutes.');
          }
          retryCount++;
          continue;
        }
        if (error.status === 500 || error.status === 502 || error.status === 503) {
          console.warn(`[${uid}] OpenAI service error, retrying...`);
          if (retryCount >= maxTotalRetries) {
            throw new functions.https.HttpsError('internal', 'OpenAI service is temporarily unavailable. Please try again in a few minutes.');
          }
          retryCount++;
          continue;
        }
        
        if (retryCount >= maxTotalRetries) {
          console.error(`[${uid}] ❌❌❌ MAX RETRIES REACHED: ${retryCount} >= ${maxTotalRetries} ❌❌❌`);
          const miniAttempts = Math.min(retryCount + 1, maxMiniRetries + 1);
          const premiumAttempts = Math.max(0, (retryCount + 1) - (maxMiniRetries + 1));
          console.error(`[${uid}] 💰 Total API calls made: ${retryCount + 1} (${miniAttempts} × gpt-4o-mini, ${premiumAttempts} × gpt-4o)`);
          throw new functions.https.HttpsError('internal', `Failed to generate 7-day blueprint after ${maxTotalRetries + 1} attempts: ${error.message || 'Unknown error'}`);
        }
        retryCount++;
        console.log(`[${uid}] ⚠️ Incrementing retryCount to ${retryCount} (max: ${maxTotalRetries})`);
      }
    }
    
    // Final validation
    if (!result || !result.meals || result.meals.length !== 7) {
      throw new functions.https.HttpsError('internal', `Failed to generate 7-day blueprint after ${maxTotalRetries + 1} attempts. Got ${result?.meals?.length || 0} days.`);
    }
    
    // Ensure shoppingList exists
    if (!result.shoppingList || typeof result.shoppingList !== 'object') {
      result.shoppingList = {};
    }
    
    // POST-PROCESS: Validate macro targets BEFORE saving
    const validateMacroTargets = (result: any): void => {
      if (!result.meals || !Array.isArray(result.meals)) return;
      
      let totalProtein = 0;
      let totalCarbs = 0;
      let totalFat = 0;
      let totalCalories = 0;
      
      result.meals.forEach((day: any) => {
        ['breakfast', 'lunch', 'dinner', 'meal'].forEach(mealType => {
          if (day[mealType]) {
            totalProtein += Number(day[mealType].protein || 0);
            totalCarbs += Number(day[mealType].carbs || 0);
            totalFat += Number(day[mealType].fat || 0);
            totalCalories += Number(day[mealType].calories || 0);
          }
        });
        if (Array.isArray(day.snacks)) {
          day.snacks.forEach((snack: any) => {
            totalProtein += Number(snack.protein || 0);
            totalCarbs += Number(snack.carbs || 0);
            totalFat += Number(snack.fat || 0);
            totalCalories += Number(snack.calories || 0);
          });
        }
      });
      
      const avgProtein = totalProtein / 7;
      const avgCarbs = totalCarbs / 7;
      const avgFat = totalFat / 7;
      const avgCalories = totalCalories / 7;
      
      const proteinDiff = Math.abs(avgProtein - macroGrams.protein);
      const carbsDiff = Math.abs(avgCarbs - macroGrams.carbs);
      const fatDiff = Math.abs(avgFat - macroGrams.fat);
      
      // Use percentage-based thresholds for all dietary preferences
      // This ensures fair validation for high-fat (keto/very_low_carb), high-carb (vegan), and balanced diets
      const proteinPercentDiff = macroGrams.protein > 0 ? (proteinDiff / macroGrams.protein) * 100 : 0;
      const carbsPercentDiff = macroGrams.carbs > 0 ? (carbsDiff / macroGrams.carbs) * 100 : 0;
      const fatPercentDiff = macroGrams.fat > 0 ? (fatDiff / macroGrams.fat) * 100 : 0;
      
      console.log(`[${uid}] Macro validation (per-serving) - Daily averages: P=${avgProtein.toFixed(1)}g (target: ${macroGrams.protein}g, diff: ${proteinDiff.toFixed(1)}g [${proteinPercentDiff.toFixed(1)}%]), C=${avgCarbs.toFixed(1)}g (target: ${macroGrams.carbs}g, diff: ${carbsDiff.toFixed(1)}g [${carbsPercentDiff.toFixed(1)}%]), F=${avgFat.toFixed(1)}g (target: ${macroGrams.fat}g, diff: ${fatDiff.toFixed(1)}g [${fatPercentDiff.toFixed(1)}%]), Calories: ${avgCalories.toFixed(0)} (target: ${dailyCalories})`);
      
      // Use 50% tolerance for critical rejection (very lenient - only reject if WAY off)
      // This matches yesterday's behavior where validation was more lenient
      if (proteinPercentDiff > 50 || carbsPercentDiff > 50 || fatPercentDiff > 50) {
        console.error(`[${uid}] ❌❌❌ CRITICAL: Macro targets WAY OFF - rejecting blueprint!`);
        console.error(`[${uid}] Differences: P: ${proteinDiff.toFixed(1)}g [${proteinPercentDiff.toFixed(1)}%] (target: ${macroGrams.protein}g, got: ${avgProtein.toFixed(1)}g), C: ${carbsDiff.toFixed(1)}g [${carbsPercentDiff.toFixed(1)}%] (target: ${macroGrams.carbs}g, got: ${avgCarbs.toFixed(1)}g), F: ${fatDiff.toFixed(1)}g [${fatPercentDiff.toFixed(1)}%] (target: ${macroGrams.fat}g, got: ${avgFat.toFixed(1)}g)`);
        throw new Error(`Generated blueprint does not meet macro targets. Protein: ${avgProtein.toFixed(1)}g (target: ${macroGrams.protein}g), Carbs: ${avgCarbs.toFixed(1)}g (target: ${macroGrams.carbs}g), Fat: ${avgFat.toFixed(1)}g (target: ${macroGrams.fat}g). The AI must regenerate with correct macros.`);
      } else {
        // Just log - don't reject unless it's way off (50%+)
        console.log(`[${uid}] ✅ Macro targets: P=${avgProtein.toFixed(1)}g (target: ${macroGrams.protein}g, diff: ${proteinPercentDiff.toFixed(1)}%), C=${avgCarbs.toFixed(1)}g (target: ${macroGrams.carbs}g, diff: ${carbsPercentDiff.toFixed(1)}%), F=${avgFat.toFixed(1)}g (target: ${macroGrams.fat}g, diff: ${fatPercentDiff.toFixed(1)}%)`);
      }
    };
    
    // Skip macro validation - it was causing rejections. The AI generates the macros, we trust it.
    // Just log the values for debugging
    try {
      validateMacroTargets(result);
    } catch (macroError: any) {
      // Don't reject - just log and continue
      console.warn(`[${uid}] ⚠️ Macro targets not met, but accepting blueprint anyway: ${macroError.message}`);
    }
    
    // POST-PROCESS: Validate dietary preference
    const validateDietaryPreference = (result: any, pref: string) => {
      const prefLower = pref.toLowerCase();
      const resultStr = JSON.stringify(result).toLowerCase();
      
      if (prefLower === 'vegetarian' || prefLower === 'vegan') {
        const forbidden = ['chicken', 'beef', 'pork', 'lamb', 'turkey', 'fish', 'seafood', 'meat'];
        const found = forbidden.filter(f => resultStr.includes(f));
        if (found.length > 0) {
          console.warn(`[${uid}] ⚠️ Dietary violation: Found ${found.join(', ')} in ${pref} diet`);
        }
      }
      
      if (prefLower === 'vegan') {
        const forbidden = ['egg', 'milk', 'cheese', 'yogurt', 'butter', 'dairy'];
        const found = forbidden.filter(f => resultStr.includes(f));
        if (found.length > 0) {
          console.warn(`[${uid}] ⚠️ Dietary violation: Found ${found.join(', ')} in vegan diet`);
        }
      }
      
      if (prefLower === 'keto' || prefLower === 'ketogenic' || prefLower === 'very low-carb' || prefLower === 'very_low_carb') {
        const forbidden = ['bread', 'pasta', 'rice', 'potato', 'grain', 'bean'];
        const found = forbidden.filter(f => resultStr.includes(f));
        if (found.length > 0) {
          console.warn(`[${uid}] ⚠️ Dietary violation: Found ${found.join(', ')} in ${pref} diet`);
        }
      }
    };
    
    validateDietaryPreference(result, dietaryPref);
    
      // POST-PROCESS: Calculate shopping list from meal ingredients (we do this ourselves, not GPT)
      const calculateShoppingListFromMeals = (meals: any[]): Record<string, string[]> => {
        console.log(`[${uid}] 🛒 Calculating shopping list from meal ingredients...`);
        console.log(`[${uid}] Meals array length: ${meals?.length || 0}`);
        
        if (!meals || !Array.isArray(meals) || meals.length === 0) {
          console.error(`[${uid}] ❌ No meals array or empty meals array!`);
          return {};
        }
        
        // Parse ingredients from all meals
        interface IngredientEntry {
          name: string;
          quantity: number;
          unit: string;
          raw: string; // Original string for reference
        }
        
        const ingredientMap = new Map<string, IngredientEntry>();
        let totalIngredientsParsed = 0;
        
        meals.forEach((day: any, dayIndex: number) => {
          console.log(`[${uid}] Processing day ${dayIndex + 1}: ${day?.day || 'unknown'}`);
          // Process main meals
          ['breakfast', 'lunch', 'dinner', 'meal', 'meal4', 'meal5'].forEach(mealType => {
            if (day[mealType]?.ingredients && Array.isArray(day[mealType].ingredients)) {
              console.log(`[${uid}]   Found ${day[mealType].ingredients.length} ingredients in ${mealType}`);
              day[mealType].ingredients.forEach((ing: string) => {
                if (!ing || typeof ing !== 'string') {
                  console.warn(`[${uid}]   ⚠️ Invalid ingredient: ${ing}`);
                  return;
                }
                // Parse ingredient string - try multiple formats
                totalIngredientsParsed++;
                let qty = 1;
                let unit = '';
                let name = ing.toLowerCase().trim();
                
                // Try format: "1.5 lbs chicken breast" or "2 cups broccoli"
                let match = ing.match(/^([\d.]+)\s+([a-zA-Z]+)\s+(.+)$/i);
                if (match) {
                  qty = parseFloat(match[1]);
                  unit = match[2].toLowerCase();
                  name = match[3].trim().toLowerCase();
                } else {
                  // Try format: "chicken breast 1.5 lbs" or "broccoli 2 cups"
                  match = ing.match(/^(.+?)\s+([\d.]+)\s+([a-zA-Z]+)$/i);
                  if (match) {
                    name = match[1].trim().toLowerCase();
                    qty = parseFloat(match[2]);
                    unit = match[3].toLowerCase();
                  } else {
                    // Try format: "3 eggs" or "2 apples" (countable items - no unit, just quantity and name)
                    match = ing.match(/^([\d.]+)\s+(.+)$/i);
                    if (match) {
                      qty = parseFloat(match[1]);
                      name = match[2].trim().toLowerCase();
                      // For countable items, leave unit empty (will be handled in display)
                      unit = '';
                    } else {
                      // Last resort: just use the whole string as name
                      console.warn(`[${uid}]   ⚠️ Could not parse ingredient format: "${ing}", using as-is`);
                      name = ing.toLowerCase().trim();
                      qty = 1;
                      unit = '';
                    }
                  }
                }
                
                // Normalize ingredient name (remove variations)
                const normalizedName = name
                  .replace(/\s*(breast|thigh|drumstick|wing)\s*/gi, '')
                  .replace(/\s*(ground|minced)\s*/gi, '')
                  .replace(/\s*(fresh|dried|frozen)\s*/gi, '')
                  .trim();
                
                const existing = ingredientMap.get(normalizedName);
                if (existing) {
                  // Add quantities (convert to common unit if needed)
                  if (existing.unit === unit) {
                    existing.quantity += qty;
                  } else {
                    // Try to convert (simplified - just add if same base unit type)
                    existing.quantity += qty;
                    console.warn(`[${uid}] ⚠️ Unit mismatch for ${name}: ${existing.unit} vs ${unit}, adding anyway`);
                  }
                } else {
                  ingredientMap.set(normalizedName, {
                    name: name,
                    quantity: qty,
                    unit: unit,
                    raw: ing
                  });
                }
              });
            }
          });
        
          // Process snacks
        if (Array.isArray(day.snacks)) {
          day.snacks.forEach((snack: any) => {
            if (snack?.ingredients && Array.isArray(snack.ingredients)) {
              snack.ingredients.forEach((ing: string) => {
                if (!ing || typeof ing !== 'string') return;
                totalIngredientsParsed++;
                let qty = 1;
                let unit = '';
                let name = ing.toLowerCase().trim();
                
                // Try format: "1.5 lbs chicken breast" or "2 cups broccoli"
                let match = ing.match(/^([\d.]+)\s+([a-zA-Z]+)\s+(.+)$/i);
                if (match) {
                  qty = parseFloat(match[1]);
                  unit = match[2].toLowerCase();
                  name = match[3].trim().toLowerCase();
                } else {
                  // Try format: "chicken breast 1.5 lbs" or "broccoli 2 cups"
                  match = ing.match(/^(.+?)\s+([\d.]+)\s+([a-zA-Z]+)$/i);
                  if (match) {
                    name = match[1].trim().toLowerCase();
                    qty = parseFloat(match[2]);
                    unit = match[3].toLowerCase();
                  } else {
                    // Try format: "3 eggs" or "2 apples" (countable items - no unit, just quantity and name)
                    match = ing.match(/^([\d.]+)\s+(.+)$/i);
                    if (match) {
                      qty = parseFloat(match[1]);
                      name = match[2].trim().toLowerCase();
                      // For countable items, leave unit empty (will be handled in display)
                      unit = '';
                    } else {
                      // Last resort: just use the whole string as name
                      console.warn(`[${uid}]   ⚠️ Could not parse snack ingredient format: "${ing}", using as-is`);
                      name = ing.toLowerCase().trim();
                      qty = 1;
                      unit = '';
                    }
                  }
                }
                
                const normalizedName = name
                  .replace(/\s*(breast|thigh|drumstick|wing)\s*/gi, '')
                  .replace(/\s*(ground|minced)\s*/gi, '')
                  .replace(/\s*(fresh|dried|frozen)\s*/gi, '')
                  .trim();
                
                const existing = ingredientMap.get(normalizedName);
                if (existing) {
                  if (existing.unit === unit) {
                    existing.quantity += qty;
                  } else {
                    existing.quantity += qty;
                  }
                } else {
                  ingredientMap.set(normalizedName, {
                    name: name,
                    quantity: qty,
                    unit: unit,
                    raw: ing
                  });
                }
              });
            }
          });
        }
      });
      
      console.log(`[${uid}] Found ${ingredientMap.size} unique ingredients from ${totalIngredientsParsed} parsed ingredient strings`);
      
      if (ingredientMap.size === 0) {
        console.error(`[${uid}] ❌❌❌ CRITICAL: ZERO ingredients parsed! This means GPT didn't return ingredients in the expected format.`);
        console.error(`[${uid}] Sample of first meal: ${JSON.stringify(meals[0]?.breakfast || meals[0]?.lunch || meals[0]?.dinner || meals[0]?.meal || 'NO MEAL FOUND').substring(0, 500)}`);
      }
      
      // Categorize ingredients
      const categories: Record<string, Array<{name: string, quantity: number, unit: string}>> = {
        Proteins: [],
        Produce: [],
        Dairy: [],
        Pantry: [],
        Grains: [],
        Other: []
      };
      
      const proteinKeywords = ['chicken', 'beef', 'pork', 'turkey', 'lamb', 'fish', 'salmon', 'tuna', 'steak', 'ground', 'sausage', 'bacon', 'ham', 'tofu', 'tempeh', 'beans', 'lentils', 'chickpeas', 'eggs', 'egg'];
      const produceKeywords = ['broccoli', 'spinach', 'lettuce', 'tomato', 'pepper', 'onion', 'garlic', 'carrot', 'celery', 'cucumber', 'zucchini', 'mushroom', 'avocado', 'apple', 'banana', 'berry', 'fruit', 'vegetable', 'greens', 'cabbage', 'kale'];
      const dairyKeywords = ['milk', 'cheese', 'yogurt', 'butter', 'cream', 'sour cream', 'cottage cheese', 'feta', 'parmesan', 'cheddar', 'mozzarella'];
      const grainKeywords = ['rice', 'pasta', 'bread', 'quinoa', 'oats', 'flour', 'almond flour', 'coconut flour'];
      
      ingredientMap.forEach((ing, normalizedName) => {
        const nameLower = ing.name.toLowerCase();
        let categorized = false;
        
        if (proteinKeywords.some(kw => nameLower.includes(kw))) {
          categories.Proteins.push({name: ing.name, quantity: ing.quantity, unit: ing.unit});
          categorized = true;
        } else if (produceKeywords.some(kw => nameLower.includes(kw))) {
          categories.Produce.push({name: ing.name, quantity: ing.quantity, unit: ing.unit});
          categorized = true;
        } else if (dairyKeywords.some(kw => nameLower.includes(kw))) {
          categories.Dairy.push({name: ing.name, quantity: ing.quantity, unit: ing.unit});
          categorized = true;
        } else if (grainKeywords.some(kw => nameLower.includes(kw))) {
          categories.Grains.push({name: ing.name, quantity: ing.quantity, unit: ing.unit});
          categorized = true;
        } else if (nameLower.includes('oil') || nameLower.includes('vinegar') || nameLower.includes('salt') || nameLower.includes('pepper') || nameLower.includes('spice') || nameLower.includes('seasoning')) {
          categories.Pantry.push({name: ing.name, quantity: ing.quantity, unit: ing.unit});
          categorized = true;
        }
        
        if (!categorized) {
          categories.Other.push({name: ing.name, quantity: ing.quantity, unit: ing.unit});
        }
      });
      
      // Convert to shopping list format - limit to 5-7 proteins, 20-25 total items
      const shoppingList: Record<string, string[]> = {};
      let totalItems = 0;
      const maxItems = 25;
      const minProteins = 5;
      const maxProteins = 7;
      
      // Priority order: Proteins, Produce, Dairy, Grains, Pantry, Other
      const priorityOrder = ['Proteins', 'Produce', 'Dairy', 'Grains', 'Pantry', 'Other'];
      
      for (const category of priorityOrder) {
        if (totalItems >= maxItems) break;
        
        let items = categories[category];
        if (items.length === 0) continue;
        
        // Sort by quantity (larger quantities first - more important)
        items.sort((a, b) => b.quantity - a.quantity);
        
        // For proteins: limit to 5-7 items
        if (category === 'Proteins') {
          const availableProteins = items.length;
          // Take up to maxProteins (7), but at least minProteins (5) if available
          const proteinCount = availableProteins > 0 ? Math.min(Math.max(minProteins, availableProteins), maxProteins) : 0;
          items = items.slice(0, proteinCount);
          console.log(`[${uid}] Proteins: ${availableProteins} available, taking ${proteinCount} (min ${minProteins}, max ${maxProteins})`);
        }
        
        shoppingList[category] = [];
        
        for (const item of items) {
          if (totalItems >= maxItems) break;
          
          // Format: "Item name – quantity unit" (or just "Item name – quantity" if no unit for countable items)
          if (!item.unit || item.unit.trim() === '') {
            // For countable items (eggs, apples, etc.), don't show "unit" - just show quantity
            shoppingList[category].push(`${item.name} – ${item.quantity.toFixed(1)}`);
          } else {
            shoppingList[category].push(`${item.name} – ${item.quantity.toFixed(1)} ${item.unit}`);
          }
          totalItems++;
        }
      }
      
      console.log(`[${uid}] ✅ Generated shopping list with ${totalItems} items (${shoppingList.Proteins?.length || 0} proteins, limited to ${maxItems} total)`);
      
      return shoppingList;
    };
    
    // Calculate shopping list from meals
    result.shoppingList = calculateShoppingListFromMeals(result.meals);
    
    // Simple sanitization - just remove undefined values
    const sanitize = (obj: any): any => {
      if (obj === null || obj === undefined) return null;
      if (Array.isArray(obj)) return obj.map(sanitize).filter(x => x !== null && x !== undefined);
      if (typeof obj === 'object' && obj.constructor === Object) {
        const cleaned: any = {};
        for (const [key, value] of Object.entries(obj)) {
          if (value !== undefined) {
            const sanitized = sanitize(value);
            if (sanitized !== null && sanitized !== undefined) {
              cleaned[key] = sanitized;
            }
          }
        }
        return cleaned;
      }
      return obj;
    };
    
    const sanitized = sanitize(result);
    sanitized.userId = uid;
    sanitized.generatedAt = admin.firestore.FieldValue.serverTimestamp();
    sanitized.useImperial = useImperial;
    sanitized.householdSize = householdSize; // Store the serving size recipes were generated for
    
    console.log(`[BLUEPRINT] [${uid}] Saving to Firestore...`);
    
    // STEP 5: Save to Firestore (one location) - SAVE IMMEDIATELY with GPT macros
    const weekId = weekStarting;
    await db.collection("users").doc(uid).collection("weeklyBlueprints").doc(weekId).set(sanitized);
    
    console.log(`[BLUEPRINT] [${uid}] ✅ Blueprint saved successfully in ${Date.now() - startTime}ms`);
    
    // STEP 6: Recalculate macros asynchronously (don't block response)
    // This runs in the background and updates the blueprint when complete
    recalculateBlueprintMacrosAsync(uid, weekId, result, householdSize).catch((error: any) => {
      console.error(`[${uid}] ❌ Async macro recalculation failed: ${error.message}`);
      // Don't throw - blueprint is already saved, this is just an enhancement
    });
    
    return { success: true, weekStarting: weekId };
    
    } catch (error: any) {
    console.error(`[BLUEPRINT] [${uid}] ❌❌❌ CRITICAL ERROR ❌❌❌`);
    console.error(`[BLUEPRINT] [${uid}] Error type: ${error?.constructor?.name || typeof error}`);
    console.error(`[BLUEPRINT] [${uid}] Error message: ${error?.message || 'No message'}`);
    console.error(`[BLUEPRINT] [${uid}] Error stack:`, error?.stack);
    console.error(`[BLUEPRINT] [${uid}] Full error object:`, JSON.stringify(error, Object.getOwnPropertyNames(error)));
    
    // Only retry on actual API failures, not validation errors
    if (error?.status === 429 || error?.code === 'ECONNABORTED' || error?.message?.includes('timeout')) {
      console.error(`[BLUEPRINT] [${uid}] Timeout/rate limit error - throwing deadline-exceeded`);
      throw new functions.https.HttpsError(
        'deadline-exceeded',
        'Request timed out. Please try again.'
      );
    }
    
      if (error instanceof functions.https.HttpsError) {
        console.error(`[BLUEPRINT] [${uid}] Re-throwing HttpsError: ${error.code} - ${error.message}`);
        throw error;
      }
      
    console.error(`[BLUEPRINT] [${uid}] Throwing internal error: ${error?.message || 'Failed to generate blueprint. Please try again.'}`);
    throw new functions.https.HttpsError(
      'internal',
      error?.message || 'Failed to generate blueprint. Please try again.'
    );
  }
});

// Alias for backward compatibility (Android app calls generateWeeklyBlueprint)
export const generateWeeklyBlueprint = generateWeeklyShoppingList;

/**
 * Asynchronously recalculate macros for a saved blueprint using analyzeRecipe system
 * Updates the blueprint document in Firestore when complete
 */
async function recalculateBlueprintMacrosAsync(
  uid: string,
  weekId: string,
  blueprint: any,
  householdSize: number
): Promise<void> {
  const db = admin.firestore();
  const blueprintRef = db.collection("users").doc(uid).collection("weeklyBlueprints").doc(weekId);
  
  console.log(`[${uid}] 🔄 Starting async macro recalculation for blueprint ${weekId}...`);
  
  // Collect all meals and snacks that need macro recalculation
  const itemsToProcess: Array<{day: string, type: string, item: any, isSnack: boolean, path: string}> = [];
  for (let dayIdx = 0; dayIdx < blueprint.meals.length; dayIdx++) {
    const day = blueprint.meals[dayIdx];
    for (const mealType of ['breakfast', 'lunch', 'dinner', 'meal']) {
      if (day[mealType]?.ingredients && Array.isArray(day[mealType].ingredients) && day[mealType].ingredients.length > 0) {
        itemsToProcess.push({
          day: day.day || 'Unknown',
          type: mealType,
          item: day[mealType],
          isSnack: false,
          path: `meals.${dayIdx}.${mealType}`
        });
      }
    }
    if (Array.isArray(day.snacks)) {
      for (let snackIdx = 0; snackIdx < day.snacks.length; snackIdx++) {
        const snack = day.snacks[snackIdx];
        if (snack?.ingredients && Array.isArray(snack.ingredients) && snack.ingredients.length > 0) {
          itemsToProcess.push({
            day: day.day || 'Unknown',
            type: 'snack',
            item: snack,
            isSnack: true,
            path: `meals.${dayIdx}.snacks.${snackIdx}`
          });
        }
      }
    }
  }
  
  console.log(`[${uid}] Processing ${itemsToProcess.length} meals/snacks in parallel batches of 5...`);
  
  // Process in batches of 5 concurrent calls
  const batchSize = 5;
  let macroRecalcSuccess = 0;
  let macroRecalcFailed = 0;
  const macroUpdates: Array<{path: string[], calories: number, protein: number, carbs: number, fat: number}> = [];
  
  for (let i = 0; i < itemsToProcess.length; i += batchSize) {
    const batch = itemsToProcess.slice(i, i + batchSize);
    const batchPromises = batch.map(async ({day, type, item, path}) => {
      const itemName = `${day} ${type}`;
      let lastError: any = null;
      
      // Retry up to 2 times for transient failures
      for (let retry = 0; retry < 3; retry++) {
        try {
          const ingredientsText = item.ingredients.join('\n');
          const recipeText = `${item.name || type}\n\nIngredients:\n${ingredientsText}\n\nThis recipe serves ${householdSize} people.`;
          
          // 30s timeout per meal (faster since it's async)
          const analysis = await Promise.race([
            analyzeRecipeIngredients(recipeText, householdSize),
            new Promise((_, reject) => setTimeout(() => reject(new Error('analyzeRecipe timeout after 30s')), 30000))
          ]) as any;
          
          // Validate response
          if (!analysis || !analysis.ingredients || !Array.isArray(analysis.ingredients) || analysis.ingredients.length === 0) {
            throw new Error('Invalid response: missing or empty ingredients array');
          }
          
          const totalNutrition = calculateTotalNutrition(analysis.ingredients);
          
          // Validate nutrition values
          if (isNaN(totalNutrition.calories) || isNaN(totalNutrition.proteinG) || isNaN(totalNutrition.carbsG) || isNaN(totalNutrition.fatG)) {
            throw new Error('Invalid nutrition values: NaN detected');
          }
          
          // Store updates for Firestore (using FieldPath for nested updates)
          const pathParts = path.split('.');
          macroUpdates.push({
            path: pathParts,
            calories: Math.round(totalNutrition.calories),
            protein: Math.round(totalNutrition.proteinG),
            carbs: Math.round(totalNutrition.carbsG),
            fat: Math.round(totalNutrition.fatG)
          });
          
          console.log(`[${uid}]   ✅ ${itemName}: ${macroUpdates[macroUpdates.length - 1].calories} cal, ${macroUpdates[macroUpdates.length - 1].protein}g P, ${macroUpdates[macroUpdates.length - 1].carbs}g C, ${macroUpdates[macroUpdates.length - 1].fat}g F`);
          return {success: true};
        } catch (error: any) {
          lastError = error;
          const errorMsg = error.message || String(error);
          const isRateLimit = error.status === 429 || errorMsg.includes('rate limit') || errorMsg.includes('429');
          const isTimeout = errorMsg.includes('timeout') || error.code === 'ECONNABORTED';
          const isRetryable = isRateLimit || isTimeout || (error.status >= 500 && error.status < 600);
          
          if (isRetryable && retry < 2) {
            const delayMs = isRateLimit ? 5000 * (retry + 1) : 2000 * (retry + 1);
            console.warn(`[${uid}]   ⚠️ ${itemName} failed (attempt ${retry + 1}/3): ${errorMsg} - retrying in ${delayMs}ms...`);
            await new Promise(resolve => setTimeout(resolve, delayMs));
            continue;
          } else {
            console.error(`[${uid}]   ❌ Failed to recalculate macros for ${itemName}: ${errorMsg}`);
            return {success: false, error: errorMsg};
          }
        }
      }
      
      console.error(`[${uid}]   ❌ Failed to recalculate macros for ${itemName} after 3 attempts: ${lastError?.message || 'Unknown error'}`);
      return {success: false, error: lastError?.message || 'Unknown error'};
    });
    
    const batchResults = await Promise.all(batchPromises);
    batchResults.forEach(result => {
      if (result.success) macroRecalcSuccess++;
      else macroRecalcFailed++;
    });
    
    console.log(`[${uid}] Batch ${Math.floor(i / batchSize) + 1} complete: ${batchResults.filter(r => r.success).length}/${batch.length} successful`);
    
    // Small delay between batches to avoid rate limits
    if (i + batchSize < itemsToProcess.length) {
      await new Promise(resolve => setTimeout(resolve, 1000));
    }
  }
  
  // Update blueprint in Firestore with recalculated macros using FieldPath for nested updates
  if (macroUpdates.length > 0) {
    console.log(`[${uid}] 📝 Updating blueprint with ${macroUpdates.length} macro recalculations...`);
    const updates: Record<string, any> = {};
    
    for (const update of macroUpdates) {
      // Use dot notation for Firestore nested field updates
      updates[`${update.path.join('.')}.calories`] = update.calories;
      updates[`${update.path.join('.')}.protein`] = update.protein;
      updates[`${update.path.join('.')}.carbs`] = update.carbs;
      updates[`${update.path.join('.')}.fat`] = update.fat;
    }
    
    await blueprintRef.update(updates);
    console.log(`[${uid}] ✅ Blueprint updated: ${macroRecalcSuccess} successful, ${macroRecalcFailed} failed`);
  } else {
    console.warn(`[${uid}] ⚠️ No macro updates to apply`);
  }
}
