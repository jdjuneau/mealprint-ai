import { onSchedule } from "firebase-functions/v2/scheduler";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import OpenAI from "openai";
import { logUsage } from './usage';

if (!require('firebase-admin').apps.length) {
  require('firebase-admin').initializeApp();
}

const db = getFirestore();
const messaging = getMessaging();

// Initialize OpenAI - validate API key exists
const getOpenAIKey = (): string => {
  const apiKey = process.env.OPENAI_API_KEY || require('firebase-functions').config().openai?.key;
  if (!apiKey) {
    console.error('❌ CRITICAL: OpenAI API key not configured!');
    console.error('Set it with: firebase functions:config:set openai.key="YOUR_KEY"');
    throw new Error('OpenAI API key not configured. Please contact support.');
  }
  return apiKey;
};

const openai = new OpenAI({ 
  apiKey: getOpenAIKey()
});

// ——— VALIDATED MACRO PRESETS (2025–2026 Coachie Standards) ———
const MACRO_PRESETS: Record<string, { protein: number; carbs: number; fat: number }> = {
  "Balanced": { protein: 25, carbs: 50, fat: 25 },
  "balanced": { protein: 25, carbs: 50, fat: 25 },
  "High Protein": { protein: 35, carbs: 40, fat: 25 },
  "high_protein": { protein: 35, carbs: 40, fat: 25 },
  "Moderate Low-Carb": { protein: 30, carbs: 25, fat: 45 },
  "moderate_low_carb": { protein: 30, carbs: 25, fat: 45 },
  "Keto": { protein: 20, carbs: 5, fat: 75 },
  "ketogenic": { protein: 20, carbs: 5, fat: 75 },
  "Very Low-Carb": { protein: 35, carbs: 5, fat: 60 },
  "very_low_carb": { protein: 35, carbs: 5, fat: 60 },
  "Carnivore": { protein: 45, carbs: 1, fat: 54 },
  "carnivore": { protein: 45, carbs: 1, fat: 54 },
  "Mediterranean": { protein: 20, carbs: 50, fat: 30 },
  "mediterranean": { protein: 20, carbs: 50, fat: 30 },
  "Plant-Based": { protein: 20, carbs: 55, fat: 25 },
  "plant_based": { protein: 20, carbs: 55, fat: 25 },
  "Vegetarian": { protein: 20, carbs: 55, fat: 25 },
  "vegetarian": { protein: 20, carbs: 55, fat: 25 },
  "Vegan": { protein: 20, carbs: 58, fat: 22 },
  "vegan": { protein: 20, carbs: 58, fat: 22 },
  "Paleo": { protein: 30, carbs: 35, fat: 35 },
  "paleo": { protein: 30, carbs: 35, fat: 35 },
  "Zone Diet": { protein: 30, carbs: 40, fat: 30 },
  "zone_diet": { protein: 30, carbs: 40, fat: 30 },
  "Low Fat": { protein: 20, carbs: 65, fat: 15 },
  "low_fat": { protein: 20, carbs: 65, fat: 15 },
};

/**
 * Validate that the generated blueprint follows dietary preference restrictions
 */
function validateDietaryPreference(data: any, dietaryPref: string): void {
  if (!data || !data.meals || !data.shoppingList) return;
  
  const pref = dietaryPref.toLowerCase();
  const allIngredients: string[] = [];
  const allShoppingItems: string[] = [];
  
  // Collect all ingredients from meals
  if (Array.isArray(data.meals)) {
    data.meals.forEach((day: any) => {
      ['breakfast', 'lunch', 'dinner', 'meal', 'meal4', 'meal5'].forEach(mealType => {
        if (day[mealType]?.ingredients) {
          allIngredients.push(...(day[mealType].ingredients || []));
        }
      });
      if (day.snacks) {
        day.snacks.forEach((snack: any) => {
          allIngredients.push(...(snack.ingredients || []));
        });
      }
    });
  }
  
  // Collect all shopping list items
  Object.values(data.shoppingList).forEach((categoryItems: any) => {
    if (Array.isArray(categoryItems)) {
      categoryItems.forEach((item: any) => {
        const itemName = typeof item === 'string' ? item.split('–')[0].trim() : (item.item || '').toString();
        allShoppingItems.push(itemName.toLowerCase());
      });
    }
  });
  
  const allText = [...allIngredients, ...allShoppingItems].join(' ').toLowerCase();
  
  // Check for violations
  if (pref === 'vegetarian') {
    const meatKeywords = ['chicken', 'beef', 'pork', 'lamb', 'turkey', 'fish', 'salmon', 'tuna', 'seafood', 'shrimp', 'crab', 'lobster', 'bacon', 'sausage', 'ham', 'steak', 'ground beef', 'ground pork'];
    const violations = meatKeywords.filter(keyword => allText.includes(keyword));
    if (violations.length > 0) {
      console.error(`⚠️ DIETARY VIOLATION: Vegetarian diet but found: ${violations.join(', ')}`);
    }
  }
  
  if (pref === 'vegan') {
    const animalKeywords = ['chicken', 'beef', 'pork', 'lamb', 'turkey', 'fish', 'salmon', 'tuna', 'seafood', 'shrimp', 'crab', 'lobster', 'bacon', 'sausage', 'ham', 'steak', 'ground beef', 'ground pork', 'egg', 'eggs', 'milk', 'cheese', 'yogurt', 'butter', 'honey', 'whey'];
    const violations = animalKeywords.filter(keyword => allText.includes(keyword));
    if (violations.length > 0) {
      console.error(`⚠️ DIETARY VIOLATION: Vegan diet but found: ${violations.join(', ')}`);
    }
  }
  
  if (pref === 'carnivore') {
    const plantKeywords = ['broccoli', 'spinach', 'lettuce', 'carrot', 'tomato', 'onion', 'pepper', 'rice', 'quinoa', 'oats', 'bread', 'pasta', 'potato', 'apple', 'banana', 'orange', 'berry', 'bean', 'lentil', 'chickpea', 'tofu', 'tempeh'];
    const violations = plantKeywords.filter(keyword => allText.includes(keyword));
    if (violations.length > 0) {
      console.error(`⚠️ DIETARY VIOLATION: Carnivore diet but found: ${violations.join(', ')}`);
    }
  }
  
  if (pref === 'ketogenic' || pref === 'keto' || pref === 'very_low_carb' || pref === 'very low-carb') {
    const highCarbKeywords = ['bread', 'pasta', 'rice', 'potato', 'potatoes', 'quinoa', 'oats', 'barley', 'wheat', 'corn', 'sugar', 'honey', 'maple syrup'];
    const violations = highCarbKeywords.filter(keyword => allText.includes(keyword));
    if (violations.length > 0) {
      console.error(`⚠️ DIETARY VIOLATION: Keto/Very Low-Carb diet but found: ${violations.join(', ')}`);
    }
  }
  
  if (pref === 'paleo') {
    const forbiddenKeywords = ['bread', 'pasta', 'rice', 'wheat', 'corn', 'bean', 'lentil', 'chickpea', 'peanut', 'milk', 'cheese', 'yogurt'];
    const violations = forbiddenKeywords.filter(keyword => allText.includes(keyword));
    if (violations.length > 0) {
      console.error(`⚠️ DIETARY VIOLATION: Paleo diet but found: ${violations.join(', ')}`);
    }
  }
}

/**
 * Get explicit dietary preference rules for the AI prompt
 */
function getDietaryPreferenceRules(dietaryPref: string): string {
  const pref = dietaryPref.toLowerCase();
  
  if (pref === 'vegetarian') {
    return `⚠️⚠️⚠️ CRITICAL DIETARY RESTRICTION: VEGETARIAN ⚠️⚠️⚠️
- ABSOLUTELY NO MEAT: No chicken, beef, pork, lamb, turkey, fish, seafood, or any animal flesh
- ALLOWED: Eggs, dairy (milk, cheese, yogurt, butter), plant-based proteins (beans, lentils, tofu, tempeh, seitan)
- ALLOWED: All vegetables, fruits, grains, nuts, seeds
- If you include ANY meat, fish, or seafood in recipes or shopping list, you have FAILED`;
  }
  
  if (pref === 'vegan') {
    return `⚠️⚠️⚠️ CRITICAL DIETARY RESTRICTION: VEGAN ⚠️⚠️⚠️
- ABSOLUTELY NO ANIMAL PRODUCTS: No meat, fish, seafood, eggs, dairy (milk, cheese, yogurt, butter), honey, or any animal-derived ingredients
- ALLOWED: Plant-based proteins only (beans, lentils, tofu, tempeh, seitan, nuts, seeds)
- ALLOWED: All vegetables, fruits, grains, plant-based milks (almond, soy, oat, coconut)
- If you include ANY animal products (meat, fish, eggs, dairy, honey) in recipes or shopping list, you have FAILED`;
  }
  
  if (pref === 'carnivore') {
    return `⚠️⚠️⚠️ CRITICAL DIETARY RESTRICTION: CARNIVORE ⚠️⚠️⚠️
- ONLY ANIMAL PRODUCTS: Meat, fish, seafood, eggs, and animal fats ONLY
- ABSOLUTELY NO PLANTS: No vegetables, fruits, grains, legumes, nuts, seeds, or any plant foods
- ALLOWED: Beef, pork, lamb, chicken, turkey, fish, seafood, eggs, butter, animal fats
- If you include ANY plant foods (vegetables, fruits, grains, etc.) in recipes or shopping list, you have FAILED`;
  }
  
  if (pref === 'ketogenic' || pref === 'keto') {
    return `⚠️⚠️⚠️ CRITICAL DIETARY RESTRICTION: KETOGENIC/KETO ⚠️⚠️⚠️
- VERY LOW CARBS: Maximum 20-50g net carbs per day (from non-starchy vegetables only)
- ABSOLUTELY NO HIGH-CARB FOODS: No bread, pasta, rice, potatoes, grains, beans, most fruits, sugar, or high-carb vegetables
- ALLOWED: Meat, fish, eggs, low-carb vegetables (leafy greens, broccoli, cauliflower, zucchini), high-fat foods (avocado, nuts, oils)
- If you include bread, pasta, rice, potatoes, grains, or high-carb foods in recipes or shopping list, you have FAILED`;
  }
  
  if (pref === 'very_low_carb' || pref === 'very low-carb') {
    return `⚠️⚠️⚠️ CRITICAL DIETARY RESTRICTION: VERY LOW-CARB ⚠️⚠️⚠️
- VERY LOW CARBS: Maximum 20-50g net carbs per day (from non-starchy vegetables only)
- ABSOLUTELY NO HIGH-CARB FOODS: No bread, pasta, rice, potatoes, grains, beans, most fruits, sugar
- ALLOWED: Meat, fish, eggs, low-carb vegetables (leafy greens, broccoli, cauliflower), high-fat foods
- If you include bread, pasta, rice, potatoes, or high-carb foods in recipes or shopping list, you have FAILED`;
  }
  
  if (pref === 'paleo') {
    return `⚠️⚠️⚠️ CRITICAL DIETARY RESTRICTION: PALEO ⚠️⚠️⚠️
- NO GRAINS OR LEGUMES: No wheat, rice, corn, beans, lentils, peanuts, or processed foods
- NO DAIRY: No milk, cheese, yogurt (except clarified butter/ghee)
- ALLOWED: Meat, fish, eggs, vegetables, fruits, nuts, seeds, healthy fats
- If you include grains, legumes, or dairy in recipes or shopping list, you have FAILED`;
  }
  
  if (pref === 'plant_based' || pref === 'plant-based') {
    return `⚠️⚠️⚠️ CRITICAL DIETARY RESTRICTION: PLANT-BASED ⚠️⚠️⚠️
- PRIMARILY PLANTS: 80-90% of meals should be plant-based
- MINIMAL ANIMAL PRODUCTS: Small amounts of animal products allowed if needed for protein/macros
- FOCUS ON: Vegetables, fruits, grains, legumes, nuts, seeds, plant-based proteins
- If you include excessive meat or animal products, you have FAILED`;
  }
  
  if (pref === 'low_fat' || pref === 'low fat') {
    return `⚠️⚠️⚠️ CRITICAL DIETARY RESTRICTION: LOW-FAT ⚠️⚠️⚠️
- VERY LOW FAT: Maximum 15-20% of calories from fat
- MINIMIZE: Oils, butter, high-fat meats, nuts, avocados, full-fat dairy
- FOCUS ON: Lean proteins, whole grains, fruits, vegetables, low-fat dairy
- If you include excessive high-fat foods in recipes or shopping list, you have FAILED`;
  }
  
  // Default for balanced and other preferences
  return `- Follow the ${dietaryPref} dietary preference guidelines
- Ensure all foods align with ${dietaryPref} principles`;
}

/**
 * Fetch previous weeks' blueprints and extract meal information to avoid repetition
 */
async function getPreviousWeeksMeals(uid: string, currentWeekStarting: string, weeksToCheck: number = 8): Promise<string> {
  try {
    const previousMeals: string[] = [];
    const previousIngredients: Set<string> = new Set();
    
    // Get dates for previous weeks
    const currentDate = new Date(currentWeekStarting);
    const previousWeeks: string[] = [];
    
    for (let i = 1; i <= weeksToCheck; i++) {
      const weekDate = new Date(currentDate);
      weekDate.setDate(weekDate.getDate() - (7 * i));
      const weekId = weekDate.toISOString().split("T")[0];
      previousWeeks.push(weekId);
    }
    
    // Fetch blueprints from previous weeks
    for (const weekId of previousWeeks) {
      try {
        // Try weeklyBlueprints first
        let blueprintDoc = await db.collection("users").doc(uid).collection("weeklyBlueprints").doc(weekId).get();
        
        // Fallback to weeklyPlans
        if (!blueprintDoc.exists) {
          blueprintDoc = await db.collection("users").doc(uid).collection("weeklyPlans").doc(weekId).get();
        }
        
        if (blueprintDoc.exists) {
          const blueprint = blueprintDoc.data();
          if (blueprint?.meals && Array.isArray(blueprint.meals)) {
            blueprint.meals.forEach((day: any) => {
              // Extract meal names from all meal types
              ['breakfast', 'lunch', 'dinner', 'meal', 'meal4', 'meal5'].forEach(mealType => {
                if (day[mealType]?.name) {
                  previousMeals.push(day[mealType].name);
                }
                // Extract ingredients
                if (day[mealType]?.ingredients && Array.isArray(day[mealType].ingredients)) {
                  day[mealType].ingredients.forEach((ing: string) => {
                    // Extract ingredient name (before quantity/unit)
                    const ingName = ing.split(/[–-]/)[0].trim().toLowerCase();
                    if (ingName.length > 2) {
                      previousIngredients.add(ingName);
                    }
                  });
                }
              });
              
              // Extract snack names
              if (day.snacks && Array.isArray(day.snacks)) {
                day.snacks.forEach((snack: any) => {
                  if (snack.name) {
                    previousMeals.push(snack.name);
                  }
                });
              }
            });
          }
        }
      } catch (error) {
        // Continue if a week doesn't exist
        console.warn(`Could not fetch blueprint for week ${weekId}:`, error);
      }
    }
    
    // Build exclusion text
    if (previousMeals.length === 0 && previousIngredients.size === 0) {
      return `\n\n⚠️⚠️⚠️ VARIETY REQUIREMENT ⚠️⚠️⚠️\nEven though there are no previous meals recorded, you MUST generate completely unique and creative meals this week. Use diverse cuisines, cooking methods, and ingredient combinations.\n\n`;
    }
    
    const uniqueMeals = [...new Set(previousMeals)].slice(0, 100); // Increased to 100 most recent
    const uniqueIngredients = Array.from(previousIngredients).slice(0, 150); // Increased to 150 ingredients
    
    let exclusionText = `\n\n`;
    exclusionText += `🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫\n`;
    exclusionText += `🚫🚫🚫 CRITICAL: ABSOLUTE PROHIBITION - DO NOT REPEAT THESE 🚫🚫🚫\n`;
    exclusionText += `🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫🚫\n\n`;
    exclusionText += `The following ${uniqueMeals.length} meals and ${uniqueIngredients.length} ingredients were used in the previous ${weeksToCheck} weeks.\n`;
    exclusionText += `YOU ARE ABSOLUTELY FORBIDDEN FROM USING THESE EXACT MEAL NAMES OR CREATING SIMILAR VARIATIONS.\n\n`;
    
    if (uniqueMeals.length > 0) {
      exclusionText += `❌ FORBIDDEN MEAL NAMES (${uniqueMeals.length} total - DO NOT USE THESE OR ANY SIMILAR VARIATIONS):\n`;
      exclusionText += uniqueMeals.map((meal, idx) => `${idx + 1}. ${meal}`).join('\n');
      exclusionText += `\n\n`;
      exclusionText += `⚠️ CRITICAL: If you see "Grilled Chicken" above, you CANNOT use "Chicken Breast", "Chicken Thighs", "Roasted Chicken", or ANY chicken dish with similar preparation.\n`;
      exclusionText += `⚠️ CRITICAL: If you see "Salmon Bowl" above, you CANNOT use "Salmon", "Salmon Fillet", "Baked Salmon", or ANY salmon dish.\n`;
      exclusionText += `⚠️ CRITICAL: You must use COMPLETELY DIFFERENT proteins, cuisines, and cooking methods.\n\n`;
    }
    
    if (uniqueIngredients.length > 0) {
      exclusionText += `❌ FREQUENTLY USED INGREDIENTS (${uniqueIngredients.length} total - MINIMIZE THESE, PREFER DIFFERENT ONES):\n`;
      exclusionText += uniqueIngredients.slice(0, 50).map((ing, idx) => `${idx + 1}. ${ing}`).join(', '); // Show first 50
      if (uniqueIngredients.length > 50) {
        exclusionText += `\n... and ${uniqueIngredients.length - 50} more ingredients`;
      }
      exclusionText += `\n\n`;
      exclusionText += `⚠️ CRITICAL: If an ingredient appears above, try to use a DIFFERENT ingredient from the same category instead.\n`;
      exclusionText += `⚠️ CRITICAL: For example, if "chicken breast" appears, use "turkey", "pork tenderloin", "beef", "fish", or "tofu" instead.\n\n`;
    }
    
    exclusionText += `🚫🚫🚫 FINAL REQUIREMENT 🚫🚫🚫\n`;
    exclusionText += `1. Every meal name MUST be completely different from the list above\n`;
    exclusionText += `2. Every meal MUST use different primary proteins than what's been used\n`;
    exclusionText += `3. Every meal MUST use different cooking methods (if "grilled" was used, use "braised", "roasted", "steamed", etc.)\n`;
    exclusionText += `4. Every meal MUST use different cuisines (if "Italian" was used, use "Thai", "Mexican", "Indian", etc.)\n`;
    exclusionText += `5. Every meal MUST have different side dishes and accompaniments\n`;
    exclusionText += `6. Be CREATIVE and INNOVATIVE - think of unique flavor combinations you haven't used before\n\n`;
    
    return exclusionText;
  } catch (error) {
    console.warn("Error fetching previous weeks' meals:", error);
    return ""; // Return empty string if there's an error - don't block blueprint generation
  }
}

/**
 * Shared logic for generating weekly blueprint (used by both callable and scheduled functions)
 */
async function generateWeeklyBlueprintLogic(uid: string): Promise<{ success: boolean; weekId: string }> {
  // Try profile/main first (new structure), then fallback to users/{uid} (main profile document)
  let profileRef = db.collection("users").doc(uid).collection("profile").doc("main");
  let profileSnap = await profileRef.get();

  // Fallback to main user document if profile/main doesn't exist
  if (!profileSnap.exists) {
    profileRef = db.collection("users").doc(uid);
    profileSnap = await profileRef.get();
  }

  if (!profileSnap.exists) {
    throw new Error("Complete your profile first to get your Weekly Blueprint!");
  }

  const profile = profileSnap.data()!;
  
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

  // ——— GET USER PREFERENCES ———
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
  
  console.log(`[${uid}] ✅ FINAL: useImperial = ${useImperial} (${useImperial ? 'IMPERIAL' : 'METRIC'})`);

  // ——— REQUIRED FIELDS WITH CLEAR ERRORS ———
  if (!profile.dailyCalories || profile.dailyCalories < 1200 || profile.dailyCalories > 5000) {
    throw new Error("Set a realistic daily calorie goal (1200–5000) in Goals → Nutrition");
  }

  if (!profile.dietaryPreference) {
    throw new Error("Choose your dietary preference in Settings → Nutrition");
  }

  const dietaryPref = profile.dietaryPreference as string;
  if (!MACRO_PRESETS[dietaryPref]) {
    throw new Error(`Unknown diet: "${dietaryPref}". Please choose a valid option.`);
  }

  // ——— SAFE DEFAULTS (only used if field missing) ———
  const defaults = {
    firstName: "Friend",
    primaryGoal: "feel great",
    mealsPerDay: 3,
    snacksPerDay: 2,
    cookingTimePref: "30 minutes or less",
    budgetLevel: "standard",
    allergies: [],
    dislikes: [],
    favoriteFoods: [],
    avoidedFoods: [],
  };

  // All recipes are generated for 4 servings by default
  // Users can adjust servings in the UI, which will scale ingredients and macros
  const recipeServings = 4;

    const {
    firstName = defaults.firstName,
    primaryGoal = defaults.primaryGoal,
    dailyCalories,
    macros,
    mealsPerDay = Math.max(1, Math.min(5, defaults.mealsPerDay)),
    snacksPerDay = Math.max(0, Math.min(3, defaults.snacksPerDay)),
    cookingTimePref = defaults.cookingTimePref,
    budgetLevel = defaults.budgetLevel,
    allergies = defaults.allergies,
    dislikes = defaults.dislikes,
    favoriteFoods = defaults.favoriteFoods,
    avoidedFoods = defaults.avoidedFoods,
    preferredCookingMethods = [],
  } = profile;

  // ——— MACRO CALCULATION WITH FULL VALIDATION ———
  const calculateMacrosInGrams = (): { protein: number; carbs: number; fat: number } => {
    if (macros?.protein && typeof macros.protein === "number") {
      const p = Math.round(macros.protein);
      const c = Math.round(macros.carbs || 0);
      const f = Math.round(macros.fat || 0);
      const total = (p * 4 + c * 4 + f * 9);

      if (Math.abs(total - dailyCalories) > dailyCalories * 0.2) {
        console.warn("User macros don't match calories – using preset fallback");
      } else {
        return { protein: p, carbs: c, fat: f };
      }
    }

    const preset = MACRO_PRESETS[dietaryPref];
    return {
      protein: Math.round((preset.protein / 100) * dailyCalories / 4),
      carbs: Math.round((preset.carbs / 100) * dailyCalories / 4),
      fat: Math.round((preset.fat / 100) * dailyCalories / 9),
    };
  };

  const macroGrams = calculateMacrosInGrams();

  // ——— FETCH PREVIOUS WEEKS' MEALS FOR VARIETY ———
  const previousWeeksExclusion = await getPreviousWeeksMeals(uid, weekStarting, 8);
  console.log(`[BLUEPRINT] [${uid}] Fetched previous weeks' meals exclusion list (${previousWeeksExclusion.length} chars)`);

  // ——— FINAL PROMPT (100% dynamic) ———
  // Determine meal structure text based on mealsPerDay
  const mealStructureText = mealsPerDay === 1 ? '1 main meal' :
                            mealsPerDay === 2 ? 'breakfast and dinner' :
                            mealsPerDay === 3 ? 'breakfast, lunch, and dinner' :
                            mealsPerDay === 4 ? 'breakfast, lunch, dinner, and a 4th meal' :
                            mealsPerDay === 5 ? 'breakfast, lunch, dinner, and 2 additional meals' :
                            'breakfast, lunch, and dinner';
  
  // Calculate week number for variety/randomization
  const weekDate = new Date(weekStarting);
  const weekNumber = Math.floor((weekDate.getTime() - new Date(weekDate.getFullYear(), 0, 1).getTime()) / (7 * 24 * 60 * 60 * 1000));
  
  const prompt = `You are Coachie AI – the world's most loved wellness coach.

${previousWeeksExclusion}

🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲
🎲🎲🎲 CRITICAL VARIETY & RANDOMIZATION REQUIREMENTS 🎲🎲🎲
🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲🎲

⚠️⚠️⚠️ THIS IS WEEK ${weekNumber} OF ${weekDate.getFullYear()} ⚠️⚠️⚠️
🚫🚫🚫 YOU MUST GENERATE COMPLETELY UNIQUE MEALS - NO REPETITION ALLOWED 🚫🚫🚫
🚫🚫🚫 DO NOT REPEAT ANY MEALS FROM PREVIOUS WEEKS - EVERY MEAL MUST BE NEW AND DIFFERENT 🚫🚫🚫
🚫🚫🚫 IF YOU GENERATE THE SAME MEALS AS BEFORE, THIS IS A CRITICAL FAILURE 🚫🚫🚫

Generate a complete 7-day meal plan with recipes + shopping list.

MANDATORY VARIETY RULES:
1. CUISINE ROTATION: Each day must feature a DIFFERENT cuisine type. Rotate through:
   - Mediterranean (Greek, Italian, Spanish)
   - Asian (Thai, Chinese, Japanese, Korean, Vietnamese)
   - Mexican/Latin American
   - Middle Eastern (Lebanese, Turkish, Israeli)
   - Indian
   - American (Southern, BBQ, Comfort Food)
   - European (French, German, Scandinavian)
   - African (Moroccan, Ethiopian)
   - Fusion/Creative combinations

2. MEAL NAME CREATIVITY: 
   - ❌ BAD: "Grilled Chicken", "Salmon Bowl", "Beef Stir-Fry"
   - ✅ GOOD: "Lemon-Herb Marinated Chicken with Quinoa Pilaf", "Miso-Glazed Salmon with Sesame Broccoli", "Szechuan Beef with Bell Peppers and Jasmine Rice"
   - Every meal name must be SPECIFIC and DESCRIPTIVE - include cooking method, key flavors, and main ingredients

3. PROTEIN ROTATION: 
   - Maximum 2-3 uses of the same protein per week
   - Rotate: chicken, beef, pork, fish, seafood, turkey, lamb, tofu, tempeh, beans, lentils, eggs
   - Each protein should be prepared differently (e.g., if using chicken twice, use different cuisines/methods)

4. COOKING METHOD ${Array.isArray(preferredCookingMethods) && preferredCookingMethods.length > 0 ? 'PREFERENCES' : 'VARIETY'}:${Array.isArray(preferredCookingMethods) && preferredCookingMethods.length > 0 ? `
   - User prefers these cooking methods: ${preferredCookingMethods.join(', ')}
   - Prioritize these methods when generating meals
   - Use each preferred method 2-3 times per week
   - Still maintain variety - you can use other methods occasionally (but less frequently)
   - If a method doesn't fit a meal, use the best alternative` : `
   - Rotate: grilling, baking, roasting, sautéing, stir-frying, braising, slow-cooking, steaming, poaching, pan-searing, air frying, BBQ, sous vide, pressure cooking, smoking
   - Don't use the same method more than 2-3 times per week`}

5. MEAL TYPE VARIETY:
   - Mix: bowls, wraps, salads, soups, stews, casseroles, sheet pan meals, one-pot meals, skewers, tacos, pasta dishes, rice dishes, noodle dishes
   - Each day should feel like a different dining experience

6. VEGETABLE VARIETY:
   - Use at least 10-15 different vegetables throughout the week
   - Don't repeat the same vegetable more than 3-4 times
   - Include: leafy greens, cruciferous, root vegetables, nightshades, alliums, etc.

7. SNACK VARIETY:
   - Rotate: nuts/seeds, fruits, yogurt, protein bars, vegetables with dip, cheese, smoothies, energy balls
   - Don't repeat the same snack more than 2 times per week

User:
- Name: ${firstName}
- Goal: ${primaryGoal}
- Daily calories: ${dailyCalories}
- Macros: ${macroGrams.protein}g protein / ${macroGrams.carbs}g carbs / ${macroGrams.fat}g fat
- Diet: ${dietaryPref}
- Meals: ${mealsPerDay} meals per day (${mealStructureText}) + ${snacksPerDay} snacks per day
- Recipe servings: 4 servings per recipe (all recipes are for 4 people by default, show FULL RECIPE macros, system will calculate per-serving)
- Allergies: ${Array.isArray(allergies) && allergies.length ? allergies.join(", ") : "none"}
- Dislikes: ${[...(Array.isArray(dislikes) ? dislikes : []), ...(Array.isArray(avoidedFoods) ? avoidedFoods : [])].join(", ") || "none"}
- Loves: ${Array.isArray(favoriteFoods) && favoriteFoods.length ? favoriteFoods.join(", ") : "variety"}
- Style: ${cookingTimePref}, ${budgetLevel} budget

CRITICAL REQUIREMENTS:
1. YOU MUST GENERATE ALL ${mealsPerDay} MEALS FOR EACH DAY - DO NOT SKIP ANY MEALS
2. If mealsPerDay = 1: Generate 1 main meal per day
3. If mealsPerDay = 2: Generate breakfast AND dinner (no lunch)
4. If mealsPerDay = 3: Generate breakfast, lunch, AND dinner
5. If mealsPerDay = 4: Generate breakfast, lunch, dinner, AND a 4th meal
6. If mealsPerDay = 5: Generate breakfast, lunch, dinner, AND 2 additional meals
7. YOU MUST ALSO GENERATE ${snacksPerDay} SNACK(S) PER DAY - DO NOT SKIP SNACKS
8. Every single day must have ALL ${mealsPerDay} meals + ${snacksPerDay} snack(s) - NO EXCEPTIONS

Rules:
- 🎲🎲🎲 CRITICAL VARIETY REQUIREMENT - THIS IS THE MOST IMPORTANT RULE 🎲🎲🎲
  * ⚠️ ABSOLUTE REQUIREMENT: This week's meal plan MUST be COMPLETELY DIFFERENT from any previous week
  * ⚠️ NO REPEATS: Do not use the same meal names, recipes, or meal combinations you've used before
  * ⚠️ UNIQUE EVERY WEEK: Each week should feel like a brand new, exciting meal plan
  * ROTATE CUISINES: Each day must feature a different cuisine - no two consecutive days should have the same cuisine type
  * VARY COOKING METHODS: Use at least 5-6 different cooking methods throughout the week
  * ROTATE PROTEINS: Maximum 2-3 uses of the same protein per week - use at least 4-5 different proteins
  * ⚠️ PROTEIN VARIETY REQUIREMENT: Use diverse protein sources - chicken, beef, fish, pork, turkey, beans, lentils, tofu, tempeh, etc.
  * ⚠️⚠️ EGG QUANTITY LIMIT - CRITICAL: Eggs can be used multiple times per week (especially for breakfast), but TOTAL egg usage must be MAXIMUM 12 eggs per person per week. For ${recipeServings} people, that's ${12 * recipeServings} eggs maximum for the entire week. Spread eggs across different meals with smaller quantities (e.g., 2-3 eggs per meal) rather than using many eggs in one meal. This ensures variety while keeping egg usage reasonable.
  * ⚠️ BALANCE PROTEINS: Don't rely heavily on any single protein source - spread protein variety across the week. Use eggs as one option among many, not the primary protein source.
  * VARY VEGETABLES: Use 10-15 different vegetables - don't repeat the same vegetable more than 3-4 times
  * DIFFERENT MEAL TYPES: Mix at least 5-6 different meal types (bowls, wraps, salads, soups, casseroles, etc.)
  * UNIQUE RECIPES: Every meal must have a SPECIFIC, DESCRIPTIVE name - no generic names allowed
  * SNACK VARIETY: Use at least 4-5 different snack types throughout the week
  * CREATIVITY: Be creative and adventurous - surprise the user with new flavor combinations and meal ideas
- CRITICAL: MAXIMIZE INGREDIENT REUSE to minimize shopping list
- ⚠️⚠️⚠️ ABSOLUTE HARD LIMIT: 20 UNIQUE ITEMS MAXIMUM ⚠️⚠️⚠️
- If you generate 21, 25, 30, 40, or 50+ items, you have FAILED - the response will be rejected
- COUNT YOUR ITEMS BEFORE FINALIZING - if you have more than 20, you MUST:
  * Combine similar items (e.g., "Bell peppers" + "Red peppers" → "Bell peppers")
  * Remove less essential items (spices, condiments that are likely already in pantry)
  * Group related items (e.g., "Garlic" + "Garlic powder" → just "Garlic")
  * Consolidate categories (e.g., "Olive oil" appears in multiple categories → list once)
- Design meals that share core ingredients across 3-4 days (not just 2):
  * Use the SAME protein source 3-4 times (e.g., chicken breast Monday, Wednesday, Friday, Sunday)
  * ⚠️ EGG EXCEPTION: Eggs can be reused 3-4 times per week, but keep TOTAL quantity to maximum 12 eggs per person per week (${12 * recipeServings} eggs total for ${recipeServings} people). Use 2-3 eggs per meal to stay within the limit.
  * Use the SAME vegetables across 4-5 meals (e.g., bell peppers in 5 different meals)
  * Use the SAME grains/starches 3-4 times (e.g., rice for 4 meals, quinoa for 3 meals)
  * Use the SAME pantry staples (olive oil, spices, salt, pepper) - only list once with total quantity
- Before finalizing, COUNT your shopping list items across ALL categories. If > 20, remove less essential items or combine similar ones
- FINAL CHECK: Count every single item in your shopping list. If the count is 21 or more, you MUST reduce it to 20 or fewer
- Every meal: catchy name, FULL RECIPE macros (for all 4 servings), ingredients for 4 servings total, 3–6 simple steps
- ⚠️⚠️⚠️ CRITICAL: You MUST calculate macros by summing nutrition from EACH ingredient you list ⚠️⚠️⚠️
- For EACH ingredient, calculate its nutrition based on the quantity you list, then ADD them all together for the meal's total macros
- Standard nutrition values (use these for calculations):
  * 1 large egg = 70 calories, 6g protein, 0.5g carbs, 5g fat
  * 1 oz chicken breast (raw) = 30 calories, 6g protein, 0g carbs, 0.5g fat
  * 1 oz cooked chicken breast = 55 calories, 11g protein, 0g carbs, 1g fat
  * 1 oz cheese (most types) = 100 calories, 6g protein, 1g carbs, 8g fat
  * 1 oz feta cheese = 75 calories, 4g protein, 1g carbs, 6g fat
  * 1 oz olive oil = 250 calories, 0g protein, 0g carbs, 28g fat
  * 1 oz butter = 200 calories, 0g protein, 0g carbs, 23g fat
  * 1 cup cooked rice = 200 calories, 4g protein, 45g carbs, 0.5g fat
  * 1 cup cooked pasta = 220 calories, 8g protein, 43g carbs, 1g fat
  * 1 oz spinach (raw) = 7 calories, 1g protein, 1g carbs, 0g fat
  * 1 oz vegetables (most) = 10-20 calories, 1-2g protein, 2-4g carbs, 0g fat
  * 1 oz nuts (almonds) = 160 calories, 6g protein, 6g carbs, 14g fat
- Example calculation: "3 large eggs" = 3 × 70 = 210 calories, 3 × 6 = 18g protein, 3 × 0.5 = 1.5g carbs, 3 × 5 = 15g fat
- Example calculation: "8 oz chicken breast" = 8 × 55 = 440 calories, 8 × 11 = 88g protein, 8 × 0 = 0g carbs, 8 × 1 = 8g fat
- Example calculation: "2 oz feta cheese" = 2 × 75 = 150 calories, 2 × 4 = 8g protein, 2 × 1 = 2g carbs, 2 × 6 = 12g fat
- Example calculation: "0.5 tbsp olive oil" = 0.5 × 15ml = 7.5ml = ~0.25 oz = 0.25 × 250 = 62.5 calories, 0g protein, 0g carbs, 7g fat
- For a meal with "3 large eggs, 2 oz spinach, 1 oz feta cheese, 0.5 tbsp olive oil":
  * Calories = 210 (eggs) + 14 (spinach) + 75 (feta) + 62.5 (oil) = ~361 calories
  * Protein = 18 (eggs) + 2 (spinach) + 4 (feta) + 0 (oil) = 24g protein
  * Carbs = 1.5 (eggs) + 2 (spinach) + 1 (feta) + 0 (oil) = 4.5g carbs
  * Fat = 15 (eggs) + 0 (spinach) + 6 (feta) + 7 (oil) = 28g fat
- ⚠️ CRITICAL: Your meal macros MUST equal the sum of all ingredient nutrition. If you list "3 large eggs" but show 61 calories, you have FAILED.
- ⚠️ CRITICAL: Calculate macros FIRST by summing ingredients, THEN ensure daily totals match targets
- ⚠️⚠️⚠️ CRITICAL MACRO TARGETS - YOU MUST HIT THESE EXACTLY ⚠️⚠️⚠️
- Each day's meals (for the FULL RECIPE with all 4 servings combined) must total EXACTLY:
  * ${dailyCalories * recipeServings} calories (${dailyCalories} per person × ${recipeServings} servings)
  * ${macroGrams.protein * recipeServings}g protein (${macroGrams.protein}g per person × ${recipeServings} servings)
  * ${macroGrams.carbs * recipeServings}g carbs (${macroGrams.carbs}g per person × ${recipeServings} servings)
  * ${macroGrams.fat * recipeServings}g fat (${macroGrams.fat}g per person × ${recipeServings} servings)
- ⚠️⚠️⚠️ CRITICAL: You MUST provide FULL RECIPE macros for ALL 4 SERVINGS COMBINED ⚠️⚠️⚠️
- Each meal should show macros for the ENTIRE RECIPE (4 servings), NOT per-serving values
- Example: If daily target is ${dailyCalories} calories per person, then ALL meals for the day should total ${dailyCalories * recipeServings} calories for the FULL RECIPE (all 4 servings combined)
- Example: Breakfast for 4 servings should be around ${Math.round(dailyCalories * recipeServings * 0.25)} calories (25% of daily total = ${Math.round(dailyCalories * 0.25)} per person)
- Example: Lunch for 4 servings should be around ${Math.round(dailyCalories * recipeServings * 0.35)} calories (35% of daily total = ${Math.round(dailyCalories * 0.35)} per person)
- Example: Dinner for 4 servings should be around ${Math.round(dailyCalories * recipeServings * 0.30)} calories (30% of daily total = ${Math.round(dailyCalories * 0.30)} per person)
- Example: Snacks for 4 servings should be around ${Math.round(dailyCalories * recipeServings * 0.10)} calories (10% of daily total = ${Math.round(dailyCalories * 0.10)} per person)
- ⚠️ CRITICAL: Calculate macros by adding up nutrition from ALL ingredients:
  * 1 large egg = ~70 calories, ~6g protein
  * 1 oz chicken breast = ~30 calories, ~6g protein
  * 1 oz cheese = ~75-100 calories, ~5-7g protein
  * 1 oz olive oil = ~250 calories, 0g protein
  * 1 cup cooked rice = ~200 calories, ~4g protein
  * 1 cup vegetables = ~20-50 calories, ~2-3g protein
- If you list "3 large eggs" in a meal, that meal MUST have at least 210 calories and 18g protein from the eggs alone
- If you list "8 oz chicken breast" in a meal, that meal MUST have at least 240 calories and 50g protein from the chicken alone
- DO NOT provide macros that are lower than the sum of your ingredients - this is a CRITICAL ERROR
- The system will automatically divide these values by 4 to display per-serving values to the user
- DO NOT divide by 4 yourself - provide the FULL RECIPE totals
- ⚠️ IF YOU PROVIDE LESS THAN ${Math.round(dailyCalories * recipeServings * 0.8)} CALORIES PER DAY, YOU HAVE FAILED ⚠️
- MUST follow dietary preference: ${dietaryPref}
${getDietaryPreferenceRules(dietaryPref)}
- Combine ALL duplicates → one consolidated shopping list with TOTAL quantities needed for the entire week
${useImperial ? 
  `🚨🚨🚨 CRITICAL: USER HAS IMPERIAL UNITS SELECTED - ABSOLUTELY NO METRIC UNITS ALLOWED 🚨🚨🚨

NEVER, EVER USE THESE METRIC UNITS IN YOUR RESPONSE:
- ❌ g, gram, grams - USE "oz" INSTEAD
- ❌ kg, kilogram, kilograms - USE "lbs" INSTEAD  
- ❌ ml, milliliter, milliliters, mL, ML - USE "fl oz", "cups", "tbsp", or "tsp" INSTEAD
- ❌ L, liter, liters, litre, litres - USE "cups", "quarts", or "fl oz" INSTEAD

YOU MUST USE IMPERIAL UNITS FOR ALL INGREDIENT QUANTITIES:
- Weights: lbs or oz ONLY (e.g., "1.5 lbs chicken breast", "8 oz cheese", "12 oz pasta")
- Volumes: cups, fl oz, tbsp, tsp ONLY (e.g., "2 cups milk", "1 tbsp olive oil", "1 tsp salt", "16 fl oz broth")
- Example CORRECT formats: "1.5 lbs chicken breast", "2 cups broccoli", "1 tbsp butter", "8 oz cheddar cheese", "16 fl oz chicken broth"
- Example WRONG formats (DO NOT USE): "700g chicken" ❌, "500ml milk" ❌, "1kg rice" ❌, "250g cheese" ❌

BEFORE YOU FINISH, CHECK YOUR ENTIRE RESPONSE FOR ANY METRIC UNITS (g, kg, ml, L, gram, kilogram, milliliter, liter). IF YOU FIND ANY, REPLACE THEM WITH IMPERIAL EQUIVALENTS IMMEDIATELY.` :
  `Use METRIC units for ALL ingredient quantities:
- Weights: g or kg (e.g., "700g chicken breast", "250g cheese", "500g ground beef")
- Volumes: ml or L (e.g., "500ml milk", "1L water", "15ml oil")
- Example format: "700g chicken breast", "500ml milk", "15ml olive oil"`
}
- Output ONLY valid JSON:

{
  "weekStarting": "${weekStarting}",
  "dailyCalories": ${dailyCalories},
  "meals": [ /* 7 full days */ ],
  "shoppingList": {
    "Produce": ["Item – quantity"],
    "Proteins": [],
    "Dairy/Alternatives": [],
    "Grains/Legumes": [],
    "Frozen": [],
    "Pantry": [],
    "Beverages": [],
    "Other": []
  }
}`;

  // ——— OPENAI CALL WITH ERROR HANDLING ———
  let result;
  let completion;
  try {
    const dietaryRules = getDietaryPreferenceRules(dietaryPref);
    const systemMessage = useImperial 
      ? `You are a helpful nutrition assistant that generates practical, organized meal plans and shopping lists.

🚨🚨🚨 CRITICAL: You MUST return ONLY valid JSON. NO markdown code blocks, NO comments, NO explanations. Just pure JSON. 🚨🚨🚨

CRITICAL JSON REQUIREMENTS:
- ALL property names MUST be in double quotes (e.g., "weekStarting" not weekStarting)
- NO trailing commas
- NO comments
- NO markdown formatting (no \`\`\`json blocks)
- Return ONLY the JSON object, nothing else

🚨🚨🚨 CRITICAL UNIT REQUIREMENTS - ABSOLUTELY NO METRIC UNITS ALLOWED 🚨🚨🚨
⚠️⚠️⚠️ USER HAS IMPERIAL UNITS SELECTED - YOU MUST USE IMPERIAL UNITS ONLY ⚠️⚠️⚠️

NEVER, EVER USE THESE METRIC UNITS IN YOUR RESPONSE:
- ❌ g, gram, grams - USE "oz" INSTEAD
- ❌ kg, kilogram, kilograms - USE "lbs" INSTEAD
- ❌ ml, milliliter, milliliters, mL, ML - USE "fl oz", "cups", "tbsp", or "tsp" INSTEAD
- ❌ L, liter, liters, litre, litres - USE "cups", "quarts", or "fl oz" INSTEAD

YOU MUST USE IMPERIAL UNITS FOR ALL INGREDIENT QUANTITIES:
- Weights: lbs or oz ONLY (e.g., "1.5 lbs chicken breast", "8 oz cheese", "12 oz pasta")
- Volumes: cups, fl oz, tbsp, tsp ONLY (e.g., "2 cups milk", "1 tbsp olive oil", "1 tsp salt", "16 fl oz broth")
- Example CORRECT formats: "1.5 lbs chicken breast", "2 cups broccoli", "1 tbsp butter", "8 oz cheddar cheese", "16 fl oz chicken broth"
- Example WRONG formats (DO NOT USE): "700g chicken" ❌, "500ml milk" ❌, "1kg rice" ❌, "250g cheese" ❌

BEFORE YOU FINISH, CHECK YOUR ENTIRE RESPONSE FOR ANY METRIC UNITS (g, kg, ml, L, gram, kilogram, milliliter, liter). IF YOU FIND ANY, REPLACE THEM WITH IMPERIAL EQUIVALENTS IMMEDIATELY.

${dietaryRules}`
      : `You are a helpful nutrition assistant that generates practical, organized meal plans and shopping lists.

🚨🚨🚨 CRITICAL: You MUST return ONLY valid JSON. NO markdown code blocks, NO comments, NO explanations. Just pure JSON. 🚨🚨🚨

CRITICAL JSON REQUIREMENTS:
- ALL property names MUST be in double quotes (e.g., "weekStarting" not weekStarting)
- NO trailing commas
- NO comments
- NO markdown formatting (no \`\`\`json blocks)
- Return ONLY the JSON object, nothing else

CRITICAL: Use METRIC units for ALL ingredient quantities:
- Weights: g or kg (e.g., "700g chicken breast", "250g cheese", "1.5 kg ground beef")
- Volumes: ml or L (e.g., "500ml milk", "1L water", "15ml olive oil")
- NEVER use imperial units (lbs, oz, cups, fl oz, tbsp, tsp) in ingredient quantities
- Example format: "700g chicken breast", "500ml milk", "15ml olive oil", "1kg rice"

${dietaryRules}`;

    completion = await openai.chat.completions.create({
      model: "gpt-4o",
      messages: [
        {
          role: "system",
          content: systemMessage
        },
        {
          role: "user",
          content: prompt
        }
      ],
      temperature: 1.1, // Increased to maximum safe value (1.0-1.2) for maximum creativity and variety
      max_tokens: 12000, // Increased from 8000 to 12000 to ensure complete 7-day meal plans are never truncated
      presence_penalty: 0.8, // Increased significantly to strongly discourage repetition of previous meals
      frequency_penalty: 0.7, // Increased significantly to encourage diverse meal names and ingredients
      response_format: { type: "json_object" }, // Force JSON output format
    });

    const raw = completion.choices[0].message.content?.trim();
    const finishReason = completion.choices[0]?.finish_reason;
    
    if (!raw) throw new Error("Empty response");
    
    // CRITICAL: Check if response is too short (likely incomplete)
    // A complete 7-day meal plan should be at least 3000-5000 characters
    if (raw.length < 2000) {
      console.error(`❌❌❌ CRITICAL: Response is too short (${raw.length} chars) - likely incomplete! ❌❌❌`);
      console.error(`❌ A complete 7-day meal plan should be at least 3000-5000 characters`);
      console.error(`❌ This response is probably incomplete or corrupted`);
      throw new Error(`Response is too short (${raw.length} characters). A complete 7-day meal plan requires at least 3000 characters. The response appears incomplete or corrupted.`);
    }
    
    // Check if response was truncated
    if (finishReason === 'length') {
      console.error(`⚠️ WARNING: OpenAI response was TRUNCATED (finish_reason: length)`);
      console.error(`Response length: ${raw.length} characters`);
      console.error(`This means max_tokens (4000) was exceeded. Consider increasing or simplifying the prompt.`);
    }

    let cleanedJson = raw
      .replace(/```json\n?/g, '')
      .replace(/```\n?/g, '')
      .trim();

    // Remove any text before the first {
    const firstBrace = cleanedJson.indexOf('{');
    if (firstBrace > 0) {
      console.log(`[${uid}] ⚠️ Found ${firstBrace} characters before first '{', removing...`);
      cleanedJson = cleanedJson.substring(firstBrace);
    }

    // Remove any text after the last }
    const lastBrace = cleanedJson.lastIndexOf('}');
    if (lastBrace >= 0 && lastBrace < cleanedJson.length - 1) {
      console.log(`[${uid}] ⚠️ Found text after last '}', removing...`);
      cleanedJson = cleanedJson.substring(0, lastBrace + 1);
    }

    // Log first 500 chars to debug JSON issues
    console.log(`[${uid}] First 500 chars of cleaned JSON:`, cleanedJson.substring(0, 500));

    // Parse JSON - with robust recovery
    try {
      result = JSON.parse(cleanedJson);
      console.log(`[BLUEPRINT] [${uid}] ✅ Successfully parsed JSON response`);
    } catch (parseError: any) {
      const errorPos = parseInt(parseError.message.match(/position (\d+)/)?.[1] || '0');
      console.error(`[${uid}] JSON parse error at position ${errorPos}:`, parseError.message);
      console.error(`[${uid}] JSON around error position (chars ${Math.max(0, errorPos - 100)}-${Math.min(cleanedJson.length, errorPos + 100)}):`, cleanedJson.substring(Math.max(0, errorPos - 100), Math.min(cleanedJson.length, errorPos + 100)));
      
      // ROBUST RECOVERY - fix all common issues
      let recoveredJson = cleanedJson;
      
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
        
        // 5. Fix unclosed strings in arrays
        recoveredJson = recoveredJson.replace(/\["([^"]+?)(\s*[,\]]|$)/g, (match, text, ending) => {
          if (ending && !text.includes('"') && text.trim().length > 0) {
            return `["${text.trim()}"]${ending.trim() === ',' ? ',' : ending.trim()}`;
          }
          return match;
        });
        
        // Try parsing after each pass
        try {
          result = JSON.parse(recoveredJson);
          console.log(`✅ Successfully parsed after recovery pass ${pass + 1}`);
          break; // Success - exit loop
        } catch (e) {
          if (pass === 4) {
            // Last pass failed - try one more time with balanced braces
            const openBraces = (recoveredJson.match(/{/g) || []).length - (recoveredJson.match(/}/g) || []).length;
            const openBrackets = (recoveredJson.match(/\[/g) || []).length - (recoveredJson.match(/\]/g) || []).length;
            if (openBraces > 0 || openBrackets > 0) {
              recoveredJson = recoveredJson.replace(/,\s*$/, '') + ']'.repeat(openBrackets) + '}'.repeat(openBraces);
              try {
                result = JSON.parse(recoveredJson);
                console.log('✅ Successfully parsed after closing braces/brackets');
                break;
                } catch (finalError: any) {
                  console.error('Final recovery attempt failed:', finalError?.message || 'Unknown error');
              }
            }
          }
        }
      }
      
      // If still not parsed, throw error
      if (!result) {
        throw new Error(`Invalid JSON response from AI: ${parseError.message?.substring(0, 100) || 'JSON parsing failed'}. Please try again.`);
      }
    }
    
    // POST-PROCESS: Detect if GPT provided per-person or full-recipe macros and adjust accordingly
    // User reports: GPT provides per-person macros (2792 cal/day for 1 person) but shopping list is correct for 4 people
    // We need to detect this and convert per-person macros to full-recipe macros for 4 servings
    console.log(`[${uid}] Analyzing macro values to determine if GPT provided per-person or full-recipe macros...`);
    
    // Safety check - with more detailed error logging
    if (!result) {
      console.error(`[${uid}] ❌ ERROR: result is null or undefined`);
      throw new Error('Invalid blueprint structure: result is missing');
    }
    if (!result.meals) {
      console.error(`[${uid}] ❌ ERROR: result.meals is missing. Result keys: ${Object.keys(result).join(', ')}`);
      throw new Error('Invalid blueprint structure: meals array missing');
    }
    if (!Array.isArray(result.meals)) {
      console.error(`[${uid}] ❌ ERROR: result.meals is not an array. Type: ${typeof result.meals}, Value: ${JSON.stringify(result.meals).substring(0, 200)}`);
      throw new Error('Invalid blueprint structure: meals is not an array');
    }
    
    // Calculate total daily macros from first day to see if they match per-person or full-recipe targets
    let firstDayTotalCalories = 0;
    let firstDayTotalProtein = 0;
    try {
      const firstDay = result.meals[0];
      if (firstDay) {
        ['breakfast', 'lunch', 'dinner', 'meal'].forEach(mealType => {
          if (firstDay[mealType]) {
            firstDayTotalCalories += firstDay[mealType].calories || 0;
            firstDayTotalProtein += firstDay[mealType].protein || 0;
          }
        });
        if (Array.isArray(firstDay.snacks)) {
          firstDay.snacks.forEach((snack: any) => {
            if (snack) {
              firstDayTotalCalories += snack.calories || 0;
              firstDayTotalProtein += snack.protein || 0;
            }
          });
        }
      }
    } catch (e) {
      console.warn(`[${uid}] ⚠️ Error calculating first day totals:`, e);
    }
    
    // Check if daily totals match per-person or full-recipe targets
    const perPersonTargetCalories = dailyCalories;
    const perPersonTargetProtein = macroGrams.protein;
    const fullRecipeTargetCalories = dailyCalories * recipeServings;
    const fullRecipeTargetProtein = macroGrams.protein * recipeServings;
    
    // Use more lenient tolerance (50%) to catch cases where GPT provides wrong per-person values
    // Also check if values are way too low - if daily total is less than 50% of per-person target,
    // GPT likely provided per-person macros (just wrong values)
    const caloriesMatchPerPerson = Math.abs(firstDayTotalCalories - perPersonTargetCalories) < (perPersonTargetCalories * 0.5);
    const caloriesMatchFullRecipe = Math.abs(firstDayTotalCalories - fullRecipeTargetCalories) < (fullRecipeTargetCalories * 0.5);
    const proteinMatchPerPerson = Math.abs(firstDayTotalProtein - perPersonTargetProtein) < (perPersonTargetProtein * 0.5);
    const proteinMatchFullRecipe = Math.abs(firstDayTotalProtein - fullRecipeTargetProtein) < (fullRecipeTargetProtein * 0.5);
    
    // CRITICAL: If daily totals are way too low (< 50% of per-person target), GPT likely provided per-person macros
    // Even if they're wrong values, we should multiply by 4 to get full-recipe
    const caloriesTooLowForPerPerson = firstDayTotalCalories < (perPersonTargetCalories * 0.5);
    const caloriesTooLowForFullRecipe = firstDayTotalCalories < (fullRecipeTargetCalories * 0.5);
    const proteinTooLowForPerPerson = firstDayTotalProtein < (perPersonTargetProtein * 0.5);
    const proteinTooLowForFullRecipe = firstDayTotalProtein < (fullRecipeTargetProtein * 0.5);
    
    console.log(`[${uid}] First day totals: ${firstDayTotalCalories} cal, ${firstDayTotalProtein}g protein`);
    console.log(`[${uid}] Per-person targets: ${perPersonTargetCalories} cal, ${perPersonTargetProtein}g protein`);
    console.log(`[${uid}] Full-recipe targets (4 servings): ${fullRecipeTargetCalories} cal, ${fullRecipeTargetProtein}g protein`);
    console.log(`[${uid}] Matches per-person: calories=${caloriesMatchPerPerson}, protein=${proteinMatchPerPerson}`);
    console.log(`[${uid}] Matches full-recipe: calories=${caloriesMatchFullRecipe}, protein=${proteinMatchFullRecipe}`);
    console.log(`[${uid}] Too low for per-person: calories=${caloriesTooLowForPerPerson}, protein=${proteinTooLowForPerPerson}`);
    console.log(`[${uid}] Too low for full-recipe: calories=${caloriesTooLowForFullRecipe}, protein=${proteinTooLowForFullRecipe}`);
    
    // CRITICAL LOGIC: Since shopping list is correct for 4 people, GPT must be providing per-person macros
    // If daily totals are way too low for full-recipe but reasonable for per-person (even if wrong), multiply by 4
    // If daily totals match full-recipe targets, keep as-is
    // Otherwise, if totals are way too low (< 50% of per-person), assume per-person and multiply by 4
    if (caloriesMatchFullRecipe && proteinMatchFullRecipe) {
      console.log(`[${uid}] ✅ GPT provided full-recipe macros for ${recipeServings} servings. Keeping as-is (no conversion needed).`);
      // Don't convert - GPT already provided full-recipe macros
    } else if (caloriesTooLowForFullRecipe && !caloriesTooLowForPerPerson) {
      // Totals are too low for full-recipe but reasonable for per-person - GPT provided per-person macros
      console.log(`[${uid}] ✅ GPT provided per-person macros (${firstDayTotalCalories} cal is too low for full-recipe ${fullRecipeTargetCalories} but reasonable for per-person). Multiplying by ${recipeServings} to get full-recipe macros.`);
      result.meals.forEach((day: any) => {
        if (!day) return;
        ['breakfast', 'lunch', 'dinner', 'meal'].forEach(mealType => {
          if (day[mealType]) {
            day[mealType].calories = Math.round((day[mealType].calories || 0) * recipeServings);
            day[mealType].protein = Math.round((day[mealType].protein || 0) * recipeServings);
            day[mealType].carbs = Math.round((day[mealType].carbs || 0) * recipeServings);
            day[mealType].fat = Math.round((day[mealType].fat || 0) * recipeServings);
          }
        });
        if (Array.isArray(day.snacks)) {
          day.snacks.forEach((snack: any) => {
            if (snack) {
              snack.calories = Math.round((snack.calories || 0) * recipeServings);
              snack.protein = Math.round((snack.protein || 0) * recipeServings);
              snack.carbs = Math.round((snack.carbs || 0) * recipeServings);
              snack.fat = Math.round((snack.fat || 0) * recipeServings);
            }
          });
        }
      });
      console.log(`[${uid}] ✅ Converted per-person macros to full-recipe macros for ${recipeServings} servings`);
    } else if (caloriesMatchPerPerson && proteinMatchPerPerson && !caloriesMatchFullRecipe) {
      console.log(`[${uid}] ✅ GPT provided per-person macros (matches targets). Multiplying by ${recipeServings} to get full-recipe macros.`);
      result.meals.forEach((day: any) => {
        if (!day) return;
        ['breakfast', 'lunch', 'dinner', 'meal'].forEach(mealType => {
          if (day[mealType]) {
            day[mealType].calories = Math.round((day[mealType].calories || 0) * recipeServings);
            day[mealType].protein = Math.round((day[mealType].protein || 0) * recipeServings);
            day[mealType].carbs = Math.round((day[mealType].carbs || 0) * recipeServings);
            day[mealType].fat = Math.round((day[mealType].fat || 0) * recipeServings);
          }
        });
        if (Array.isArray(day.snacks)) {
          day.snacks.forEach((snack: any) => {
            if (snack) {
              snack.calories = Math.round((snack.calories || 0) * recipeServings);
              snack.protein = Math.round((snack.protein || 0) * recipeServings);
              snack.carbs = Math.round((snack.carbs || 0) * recipeServings);
              snack.fat = Math.round((snack.fat || 0) * recipeServings);
            }
          });
        }
      });
      console.log(`[${uid}] ✅ Converted per-person macros to full-recipe macros for ${recipeServings} servings`);
    } else {
      // Default: Since shopping list is correct for 4 people, assume GPT provided per-person macros and multiply by 4
      console.warn(`[${uid}] ⚠️ WARNING: Daily totals unclear (${firstDayTotalCalories} cal). Since shopping list is correct for 4 people, assuming GPT provided per-person macros. Multiplying by ${recipeServings}.`);
      result.meals.forEach((day: any) => {
        if (!day) return;
        ['breakfast', 'lunch', 'dinner', 'meal'].forEach(mealType => {
          if (day[mealType]) {
            day[mealType].calories = Math.round((day[mealType].calories || 0) * recipeServings);
            day[mealType].protein = Math.round((day[mealType].protein || 0) * recipeServings);
            day[mealType].carbs = Math.round((day[mealType].carbs || 0) * recipeServings);
            day[mealType].fat = Math.round((day[mealType].fat || 0) * recipeServings);
          }
        });
        if (Array.isArray(day.snacks)) {
          day.snacks.forEach((snack: any) => {
            if (snack) {
              snack.calories = Math.round((snack.calories || 0) * recipeServings);
              snack.protein = Math.round((snack.protein || 0) * recipeServings);
              snack.carbs = Math.round((snack.carbs || 0) * recipeServings);
              snack.fat = Math.round((snack.fat || 0) * recipeServings);
            }
          });
        }
      });
      console.log(`[${uid}] ✅ Multiplied macros by ${recipeServings} to get full-recipe values`);
    }
    
    // POST-PROCESS: Validate dietary preference compliance
    validateDietaryPreference(result, dietaryPref);
    
    // Trust OpenAI to follow the explicit unit instructions - no post-processing needed
    
    // Trust AI to follow prompt instruction about 25-item limit
  } catch (error: any) {
    console.error("OpenAI error:", error);
    throw new Error("AI is having a moment – try again in 30 seconds");
  }

  // Ensure shoppingList structure matches expected format
  // CRITICAL: Always ensure shoppingList exists (never undefined)
  if (!result.shoppingList || typeof result.shoppingList !== 'object') {
    console.warn('⚠️ WARNING: shoppingList is missing or invalid, creating empty structure');
    result.shoppingList = {};
  }
  
  const shoppingList: Record<string, Array<{ item: string; quantity: string }>> = {};
  for (const [category, items] of Object.entries(result.shoppingList)) {
    if (Array.isArray(items)) {
      shoppingList[category] = items.map((item: any) => {
        if (typeof item === 'string') {
          const parts = item.split('–').map((s: string) => s.trim());
          return {
            item: parts[0] || item,
            quantity: parts[1] || '1',
          };
        }
        return item;
      });
    }
  }
  result.shoppingList = shoppingList;

  // ——— SANITIZE FOR FIRESTORE ———
  // Firestore doesn't allow: arrays of arrays, undefined values, functions, or Date objects
  const sanitizeForFirestore = (data: any, depth: number = 0, path: string = 'root'): any => {
    if (depth > 10) {
      console.warn(`⚠️ Max depth reached in sanitization at ${path}, truncating`);
      return null;
    }
    
    if (data === null || data === undefined) {
      return null;
    }
    
    // Skip functions
    if (typeof data === 'function') {
      console.warn(`⚠️ Found function at ${path}, removing`);
      return null;
    }
    
    // Convert Date to timestamp
    if (data instanceof Date) {
      return require('firebase-admin').firestore.Timestamp.fromDate(data);
    }
    
    // Handle arrays - Firestore allows arrays of primitives or objects, but NOT arrays of arrays
    if (Array.isArray(data)) {
      const sanitized: any[] = [];
      for (let i = 0; i < data.length; i++) {
        const item = data[i];
        const itemPath = `${path}[${i}]`;
        
        // If item is an array, this is invalid - flatten it
        if (Array.isArray(item)) {
          console.warn(`⚠️ Found nested array at ${itemPath}, flattening`);
          // Firestore doesn't allow arrays of arrays - flatten to single level
          const flattened = item.map((subItem, subIndex) => 
            sanitizeForFirestore(subItem, depth + 1, `${itemPath}[${subIndex}]`)
          );
          sanitized.push(...flattened);
        } else {
          const sanitizedItem = sanitizeForFirestore(item, depth + 1, itemPath);
          // Only add if not null/undefined
          if (sanitizedItem !== null && sanitizedItem !== undefined) {
            sanitized.push(sanitizedItem);
          }
        }
      }
      return sanitized;
    }
    
    // Handle objects - recursively sanitize
    if (typeof data === 'object' && data.constructor === Object) {
      const sanitized: any = {};
      for (const [key, value] of Object.entries(data)) {
        // Skip undefined, functions
        if (value === undefined || typeof value === 'function') {
          console.warn(`⚠️ Skipping ${path}.${key} (undefined or function)`);
          continue;
        }
        const sanitizedValue = sanitizeForFirestore(value, depth + 1, `${path}.${key}`);
        // Only add if not null/undefined
        if (sanitizedValue !== null && sanitizedValue !== undefined) {
          sanitized[key] = sanitizedValue;
        }
      }
      return sanitized;
    }
    
    // Primitives (string, number, boolean) are fine
    return data;
  };
  
  const sanitizedResult = sanitizeForFirestore(result);
  console.log(`[BLUEPRINT] [${uid}] ✅ Data sanitized for Firestore compatibility`);
  
  // CRITICAL: Ensure shoppingList is never undefined after sanitization
  if (!sanitizedResult.shoppingList || sanitizedResult.shoppingList === undefined) {
    console.warn('⚠️ WARNING: shoppingList became undefined after sanitization, setting to empty object');
    sanitizedResult.shoppingList = {};
  }
  
  // Validate critical fields before saving
  if (!sanitizedResult.meals || !Array.isArray(sanitizedResult.meals)) {
    throw new Error('Generated blueprint is missing meals array');
  }
  
  // CRITICAL: Validate that we have exactly 7 days
  if (sanitizedResult.meals.length !== 7) {
    console.error(`❌❌❌ CRITICAL: Blueprint has ${sanitizedResult.meals.length} days instead of 7! ❌❌❌`);
    console.error(`❌ This is a CRITICAL FAILURE - the AI did not generate a complete 7-day meal plan`);
    console.error(`❌ Response was too short or incomplete`);
    throw new Error(`Generated blueprint has ${sanitizedResult.meals.length} days instead of 7. The AI must generate a COMPLETE 7-DAY meal plan.`);
  }
  
  if (!sanitizedResult.shoppingList || typeof sanitizedResult.shoppingList !== 'object') {
    throw new Error('Generated blueprint is missing or has invalid shoppingList');
  }
  
  console.log(`[BLUEPRINT] [${uid}] ✅ Validation passed: meals=${sanitizedResult.meals.length} days, shoppingList has ${Object.keys(sanitizedResult.shoppingList).length} categories`);

  // ——— EXTRACT MEAL NAMES FOR VALIDATION ———
  const extractMealNames = (meals: any[]): string[] => {
    const mealNames: string[] = [];
    if (Array.isArray(meals)) {
      meals.forEach((day: any) => {
        if (day.breakfast?.name) mealNames.push(day.breakfast.name.toLowerCase().trim());
        if (day.lunch?.name) mealNames.push(day.lunch.name.toLowerCase().trim());
        if (day.dinner?.name) mealNames.push(day.dinner.name.toLowerCase().trim());
        if (Array.isArray(day.snacks)) {
          day.snacks.forEach((snack: any) => {
            if (snack?.name) mealNames.push(snack.name.toLowerCase().trim());
          });
        }
      });
    }
    return mealNames.sort(); // Sort for consistent hashing
  };

  const mealNames = extractMealNames(result.meals || []);
  const mealNamesHash = require('crypto').createHash('sha256')
    .update(mealNames.join('|'))
    .digest('hex');
  
  console.log(`📋 Extracted ${mealNames.length} meal names`);
  console.log(`🔐 Meal names hash: ${mealNamesHash.substring(0, 16)}...`);

  // ——— VALIDATE UNIQUENESS ———
  // Check previous blueprints for the same meal names hash
  const previousBlueprints = await db
    .collection("users")
    .doc(uid)
    .collection("weeklyBlueprints")
    .where("mealNamesHash", "==", mealNamesHash)
    .get();
  
  if (!previousBlueprints.empty) {
    const duplicateWeek = previousBlueprints.docs[0].id;
    console.warn(`⚠️ WARNING: Generated blueprint has same meal names as week ${duplicateWeek}`);
    console.warn(`⚠️ This indicates the blueprint may not be unique. Meal names: ${mealNames.slice(0, 5).join(', ')}...`);
    // Don't throw error, but log warning - the variety requirements should prevent this
  } else {
    console.log(`✅ Blueprint is unique - no previous blueprint with same meal names hash`);
  }

  // ——— SAVE & RETURN ———
  try {
    // Save to weeklyBlueprints (new structure)
    // CRITICAL: Remove any undefined values before saving to Firestore
    const dataToSave: any = {
      ...sanitizedResult,
      generatedAt: require('firebase-admin').firestore.FieldValue.serverTimestamp(),
      userId: uid,
      useImperial: useImperial, // Store unit preference with blueprint
      mealNamesHash: mealNamesHash, // Store hash for validation
      mealNames: mealNames, // Store meal names for debugging
    };
    
    // Remove any undefined values (Firestore doesn't allow them)
    Object.keys(dataToSave).forEach(key => {
      if (dataToSave[key] === undefined) {
        delete dataToSave[key];
        console.warn(`⚠️ Removed undefined field: ${key}`);
      }
    });
    
    // Ensure shoppingList is always an object, never undefined
    if (!dataToSave.shoppingList || dataToSave.shoppingList === undefined) {
      dataToSave.shoppingList = {};
    }
    
    await db
      .collection("users")
      .doc(uid)
      .collection("weeklyBlueprints")
      .doc(weekStarting)
      .set(dataToSave, { merge: true });

    // Also save to weeklyPlans for backward compatibility
    await db
      .collection("users")
      .doc(uid)
      .collection("weeklyPlans")
      .doc(weekStarting)
        .set({
          weekId: weekStarting,
          shoppingList: sanitizedResult.shoppingList || {}, // Ensure never undefined
          generatedAt: require('firebase-admin').firestore.FieldValue.serverTimestamp(),
        userId: uid,
        name: firstName,
        goal: primaryGoal,
        dailyCalories: result.dailyCalories || dailyCalories,
        macros: macroGrams,
        dietaryPreference: dietaryPref,
        mealsPerDay,
        snacksPerDay,
        servings: recipeServings,
        budgetLevel,
        allergies: Array.isArray(allergies) ? allergies.join(", ") : allergies,
        preferences: cookingTimePref,
      }, { merge: true });

    // Log usage - ALWAYS log, even if usage data is missing (use estimates)
    console.log(`[BLUEPRINT] 🚨🚨🚨 ABOUT TO CALL logUsage for user ${uid} 🚨🚨🚨`);
    console.log(`[BLUEPRINT] Completion object:`, {
      hasCompletion: !!completion,
      hasUsage: !!completion?.usage,
      totalTokens: completion?.usage?.total_tokens,
      promptTokens: completion?.usage?.prompt_tokens,
      completionTokens: completion?.usage?.completion_tokens,
      completionKeys: completion ? Object.keys(completion) : [],
      usageKeys: completion?.usage ? Object.keys(completion.usage) : []
    });
    
    try {
      const now = new Date();
      const date = now.toISOString().split('T')[0];
      console.log(`[BLUEPRINT] 📅 Date string: ${date}, timestamp: ${now.getTime()}`);
      
      // Get usage data or estimate from response
      const promptTokens = completion?.usage?.prompt_tokens;
      const completionTokens = completion?.usage?.completion_tokens;
      const totalTokens = completion?.usage?.total_tokens;
      
      // If no usage data, estimate from response length (very rough: ~4 chars per token)
      const responseText = completion?.choices?.[0]?.message?.content || '';
      // Estimate prompt tokens from prompt length (system message is ~2000 chars, prompt varies)
      const estimatedPromptTokens = promptTokens ?? Math.ceil(2000 / 4 + prompt.length / 4);
      const estimatedCompletionTokens = completionTokens ?? Math.ceil(responseText.length / 4);
      const estimatedTotalTokens = totalTokens ?? (estimatedPromptTokens + estimatedCompletionTokens);
      
      console.log(`[BLUEPRINT] 📞 CALLING logUsage NOW...`);
      console.log(`[BLUEPRINT] Tokens: total=${totalTokens ?? estimatedTotalTokens}, prompt=${promptTokens ?? estimatedPromptTokens}, completion=${completionTokens ?? estimatedCompletionTokens}`);
      
      await logUsage({
        userId: uid,
        date: date,
        timestamp: now.getTime(),
        source: 'generateWeeklyShoppingList',
        model: 'gpt-4o',
        totalTokens: totalTokens ?? estimatedTotalTokens,
        promptTokens: promptTokens ?? estimatedPromptTokens,
        completionTokens: completionTokens ?? estimatedCompletionTokens,
        metadata: {
          weekId: weekStarting,
          hasActualUsage: !!completion?.usage,
          estimated: !completion?.usage
        }
      });
      console.log(`[BLUEPRINT] ✅✅✅ logUsage COMPLETED SUCCESSFULLY ✅✅✅`);
    } catch (logError: any) {
      console.error(`[BLUEPRINT] ❌❌❌ FAILED TO LOG USAGE ❌❌❌`);
      console.error(`[BLUEPRINT] Error:`, {
        message: logError?.message,
        code: logError?.code,
        stack: logError?.stack,
        name: logError?.name,
        userId: uid,
        date: new Date().toISOString().split('T')[0]
      });
      console.warn('Failed to log usage:', logError);
      // Don't throw - blueprint generation should still succeed even if logging fails
    }

    return { success: true, weekId: weekStarting };
  } catch (error) {
    throw new Error("Failed to save – try again");
  }
}

// Run every Sunday at 7:00 AM Eastern Time
export const weeklyBlueprintScheduler = onSchedule(
  { 
    schedule: "0 7 * * 0", 
    timeZone: "America/New_York", 
    region: "us-central1",
    labels: { 'blueprint': 'true', 'type': 'scheduled-meal-planning' }
  },
  async () => {
    console.log('[BLUEPRINT] Weekly Blueprint Scheduler started');

    try {
      // Get all user profiles from users/{uid}/profile/main
      // We need to query each user's profile/main document
      const usersSnapshot = await db.collection("users").limit(100).get();
      console.log(`Found ${usersSnapshot.docs.length} users to process`);

      const promises = usersSnapshot.docs.map(async (userDoc) => {
        const uid = userDoc.id;
        
        // Get profile/main document
        const profileDoc = await db.collection("users").doc(uid).collection("profile").doc("main").get();
        if (!profileDoc.exists) return;

        const profile = profileDoc.data()!;

        // Only users who have meal plan notifications enabled
        if (!profile.notifications?.mealPlan) {
          return;
        }

        try {
          // Generate blueprint
          await generateWeeklyBlueprintLogic(uid);

          // Send push notification
          const tokens = profile.fcmTokens || [];
          if (tokens.length > 0) {
            await messaging.sendMulticast({
              tokens,
              notification: {
                title: "Your Weekly Blueprint is ready!",
                body: "Tap to see this week's meals & shopping list",
              },
              data: { screen: "WeeklyBlueprint" },
              apns: { 
                payload: { 
                  aps: { 
                    sound: "default",
                    badge: 1,
                  } 
                } 
              },
              android: { 
                priority: "high",
                notification: {
                  channelId: "meal_plans",
                  sound: "default",
                }
              },
            });
            console.log(`Sent notification to user ${uid}`);
          }
        } catch (e) {
          console.error(`Failed to generate blueprint for ${uid}:`, e);
        }
      });

      await Promise.allSettled(promises);
      console.log('Weekly Blueprint Scheduler completed');
    } catch (error) {
      console.error('Weekly Blueprint Scheduler error:', error);
    }
  }
);

// Daily meal reminders (runs every hour – we filter inside)
export const dailyMealReminders = onSchedule(
  { schedule: "every 60 minutes", region: "us-central1" },
  async () => {
    console.log('Daily Meal Reminders started');

    try {
      const now = new Date();
      const hour = now.getHours();
      const minute = now.getMinutes();
      const today = now.toISOString().split("T")[0];

      // Get Monday of current week for blueprint lookup
      const day = now.getDay();
      const diff = now.getDate() - day + (day === 0 ? -6 : 1);
      const monday = new Date(now.setDate(diff));
      const weekStarting = monday.toISOString().split("T")[0];

      // Get all user profiles
      const usersSnapshot = await db.collection("users").limit(100).get();
      console.log(`Checking meal reminders for ${usersSnapshot.docs.length} users`);

      for (const userDoc of usersSnapshot.docs) {
        const uid = userDoc.id;

        // Get profile/main document
        const profileDoc = await db.collection("users").doc(uid).collection("profile").doc("main").get();
        if (!profileDoc.exists) continue;

        const profile = profileDoc.data()!;

        if (!profile.notifications?.mealReminders) continue;

        const reminderTimes: Record<string, string> = {
          breakfast: profile.mealTimes?.breakfast || "07:30",
          lunch: profile.mealTimes?.lunch || "12:30",
          dinner: profile.mealTimes?.dinner || "18:30",
          snack1: profile.mealTimes?.snack1 || "10:00",
          snack2: profile.mealTimes?.snack2 || "15:30",
        };

        // Check if now matches any meal time (±3 min)
        for (const [type, time] of Object.entries(reminderTimes)) {
          const [h, m] = time.split(":").map(Number);
          if (Math.abs(hour - h) < 1 && Math.abs(minute - m) <= 3) {
            try {
              // Get blueprint for this week
              const blueprintSnap = await db
                .collection("users")
                .doc(uid)
                .collection("weeklyBlueprints")
                .doc(weekStarting)
                .get();

              if (!blueprintSnap.exists) {
                // Fallback to weeklyPlans
                const planSnap = await db
                  .collection("users")
                  .doc(uid)
                  .collection("weeklyPlans")
                  .doc(weekStarting)
                  .get();
                
                if (!planSnap.exists) continue;
              }

              const blueprint = blueprintSnap.exists 
                ? blueprintSnap.data() 
                : (await db.collection("users").doc(uid).collection("weeklyPlans").doc(weekStarting).get()).data();

              // Find meals for today
              const meals = blueprint?.meals || [];
              const todayMeals = Array.isArray(meals) 
                ? meals.find((d: any) => d.date === today || d.day === today)
                : null;

              if (!todayMeals) continue;

              // Find meal by type
              const mealList = todayMeals.meals || todayMeals;
              const meal = Array.isArray(mealList)
                ? mealList.find((m: any) => {
                    const mealType = (m.type || m.name || "").toLowerCase();
                    return mealType.includes(type) || 
                           (type === "breakfast" && mealType.includes("breakfast")) ||
                           (type === "lunch" && mealType.includes("lunch")) ||
                           (type === "dinner" && mealType.includes("dinner")) ||
                           (type === "snack1" && mealType.includes("snack")) ||
                           (type === "snack2" && mealType.includes("snack"));
                  })
                : null;

              if (meal && profile.fcmTokens?.length) {
                const mealName = meal.name || meal.title || "Your meal";
                const prepTime = meal.prepTime || meal.prepTimeMinutes || "15 min";

                await messaging.sendMulticast({
                  tokens: profile.fcmTokens,
                  notification: {
                    title: `${mealName}`,
                    body: `Tap for recipe → ready in ${prepTime}`,
                  },
                  data: {
                    screen: "WeeklyBlueprint",
                    day: today,
                    mealType: type,
                  },
                  android: { 
                    priority: "high",
                    notification: {
                      channelId: "meal_reminders",
                      sound: "default",
                    }
                  },
                  apns: { 
                    payload: { 
                      aps: { 
                        sound: "chime.caf",
                        badge: 1,
                      } 
                    } 
                  },
                });

                console.log(`Sent meal reminder to user ${uid} for ${type}`);
              }
            } catch (error) {
              console.error(`Error sending meal reminder to ${uid}:`, error);
            }
          }
        }
      }

      console.log('Daily Meal Reminders completed');
    } catch (error) {
      console.error('Daily Meal Reminders error:', error);
    }
  }
);

