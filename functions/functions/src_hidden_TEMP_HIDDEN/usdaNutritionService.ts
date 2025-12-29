import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import * as https from 'https';

if (!admin.apps.length) {
  admin.initializeApp();
}

// Get USDA API key - prioritize environment variable (new method), fallback to functions.config() (old method)
// Migration: Using process.env first (future-proof), functions.config() as fallback (works until March 2026)
// To set: Set USDA_API_KEY environment variable in Firebase Console or .env file
// Or: firebase functions:config:set usda.api_key="your_key_here" (deprecated, but still works)
const USDA_API_KEY = process.env.USDA_API_KEY || functions.config().usda?.api_key || 'hFfdKmbbg6otRUUa4cT2LGsX4Q5uyao0iag6ttk7';
const USDA_BASE_URL = 'https://api.nal.usda.gov/fdc/v1';

/**
 * Parse quantity and unit from ingredient text
 * Examples: "8 eggs" -> {quantity: 8, unit: "egg", name: "eggs"}
 *           "1 cup broccoli" -> {quantity: 1, unit: "cup", name: "broccoli"}
 *           "200g chicken" -> {quantity: 200, unit: "g", name: "chicken"}
 */
function parseIngredient(ingredientText: string): { quantity: number; unit: string; name: string } {
  const parts = ingredientText.trim().split(/\s+/);
  if (parts.length < 2) {
    return { quantity: 1, unit: '', name: ingredientText };
  }

  // Try to parse quantity (first part)
  const quantityStr = parts[0];
  const quantity = parseFloat(quantityStr) || 1;

  // Remaining parts are unit + name
  const rest = parts.slice(1).join(' ');

  // Common units
  const unitPatterns = [
    /^(cup|cups|C|CUP|CUPS)\s+/i,
    /^(tbsp|tablespoon|tablespoons|T|TBSP)\s+/i,
    /^(tsp|teaspoon|teaspoons|t|TSP)\s+/i,
    /^(oz|ounce|ounces|OZ)\s+/i,
    /^(lb|lbs|pound|pounds|LB|LBS)\s+/i,
    /^(g|gram|grams|G|GRAM|GRAMS)\s+/i,
    /^(kg|kilogram|kilograms|KG)\s+/i,
    /^(ml|milliliter|milliliters|mL|ML)\s+/i,
    /^(fl\s*oz|fluid\s*ounce|fluid\s*ounces|FL\s*OZ)\s+/i,
    /^(l|liter|liters|L|LITER|LITERS)\s+/i,
    /^(egg|eggs|EGG|EGGS)\s+/i,
    /^(piece|pieces|PIECE|PIECES)\s+/i,
    /^(slice|slices|SLICE|SLICES)\s+/i,
  ];

  let unit = '';
  let name = rest;

  for (const pattern of unitPatterns) {
    const match = rest.match(pattern);
    if (match) {
      unit = match[1].toLowerCase();
      name = rest.substring(match[0].length).trim();
      break;
    }
  }

  // If no unit found and quantity is a number, check if it's a count (like "8 eggs")
  if (!unit && quantity > 0 && quantity < 100) {
    // Check if name starts with a countable noun
    const countablePattern = /^(egg|eggs|apple|apples|banana|bananas|piece|pieces|slice|slices)/i;
    const nameMatch = name.match(countablePattern);
    if (nameMatch) {
      unit = nameMatch[1].toLowerCase().replace(/s$/, ''); // Remove plural
      name = name.substring(nameMatch[0].length).trim() || nameMatch[1];
    }
  }

  return { quantity, unit, name: name || ingredientText };
}

/**
 * Get gram weight for a unit from USDA foodPortions
 */
function getGramWeightForUnit(foodPortions: any[], unit: string, quantity: number): number | null {
  if (!foodPortions || foodPortions.length === 0) return null;

  const unitLower = unit.toLowerCase();
  
  // Try to match unit in foodPortions
  for (const portion of foodPortions) {
    const measure = (portion.measureUnit || '').toLowerCase();
    const description = (portion.measureDescription || '').toLowerCase();
    
    // Check if unit matches
    if (measure.includes(unitLower) || description.includes(unitLower) || 
        description.includes(unitLower + 's') || description.includes(unitLower.slice(0, -1))) {
      const gramWeight = portion.gramWeight || 0;
      if (gramWeight > 0) {
        return gramWeight * quantity;
      }
    }
  }

  // Fallback: if unit is "g" or "gram", quantity is already in grams
  if (unitLower === 'g' || unitLower === 'gram' || unitLower === 'grams') {
    return quantity;
  }

  // Fallback: common unit conversions (approximate)
  const unitConversions: Record<string, number> = {
    'cup': 240, // 1 cup ≈ 240g (varies by food, but common default)
    'tbsp': 15, // 1 tbsp ≈ 15g
    'tsp': 5, // 1 tsp ≈ 5g
    'oz': 28.35, // 1 oz ≈ 28.35g
    'lb': 453.6, // 1 lb ≈ 453.6g
    'egg': 50, // 1 large egg ≈ 50g
    'piece': 100, // Generic piece ≈ 100g
    'slice': 25, // Generic slice ≈ 25g
  };

  const baseGrams = unitConversions[unitLower] || null;
  if (baseGrams) {
    return baseGrams * quantity;
  }

  return null;
}

/**
 * Look up nutrition for a single ingredient from USDA API with quantity scaling
 * @param ingredientText - Example: "8 eggs" or "1 cup broccoli" or "200g chicken breast"
 * @returns Nutrition info with macros and micronutrients SCALED to the requested quantity
 */
export async function lookupIngredientNutrition(ingredientText: string): Promise<{
  calories: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
  sugarG: number;
  addedSugarG: number;
  micronutrients: Record<string, number>;
}> {
  return new Promise(async (resolve, reject) => {
    try {
      // Parse quantity, unit, and name from ingredient text
      const parsed = parseIngredient(ingredientText);
      const { quantity, unit, name } = parsed;

      // Search for the food (use just the name, not quantity)
      const searchQuery = name;
      const encodedQuery = encodeURIComponent(searchQuery);
      const searchUrl = `${USDA_BASE_URL}/foods/search?api_key=${USDA_API_KEY}&query=${encodedQuery}&pageSize=1`;
      const searchUrlObj = new URL(searchUrl);
      
      const searchOptions = {
        hostname: searchUrlObj.hostname,
        path: searchUrlObj.pathname + searchUrlObj.search,
        method: 'GET',
        headers: {
          'Content-Type': 'application/json'
        },
        timeout: 15000
      };

      const searchReq = https.request(searchOptions, async (searchRes) => {
        let searchData = '';

        searchRes.on('data', (chunk) => {
          searchData += chunk;
        });

        searchRes.on('end', async () => {
          try {
            if (searchRes.statusCode !== 200) {
              reject(new Error(`USDA search error: ${searchRes.statusCode} - ${searchData.substring(0, 200)}`));
              return;
            }

            const searchJson = JSON.parse(searchData);
            const foods = searchJson.foods;

            if (!foods || foods.length === 0) {
              reject(new Error(`No food found for: ${ingredientText}`));
              return;
            }

            const food = foods[0];
            const nutritionPer100g = parseUSDAFood(food);
            
            // Try fallback unit conversions first (fast, no extra API call)
            const fallbackGrams = getGramWeightForUnit([], unit, quantity);
            
            if (fallbackGrams && fallbackGrams > 0) {
              // Use fallback conversion - single API call!
              const scaleFactor = fallbackGrams / 100.0;
              
              const scaledNutrition = {
                calories: Math.round(nutritionPer100g.calories * scaleFactor),
                proteinG: nutritionPer100g.proteinG * scaleFactor,
                carbsG: nutritionPer100g.carbsG * scaleFactor,
                fatG: nutritionPer100g.fatG * scaleFactor,
                sugarG: nutritionPer100g.sugarG * scaleFactor,
                addedSugarG: nutritionPer100g.addedSugarG * scaleFactor,
                micronutrients: {} as Record<string, number>
              };

              // Scale micronutrients
              for (const [key, value] of Object.entries(nutritionPer100g.micronutrients)) {
                scaledNutrition.micronutrients[key] = value * scaleFactor;
              }

              console.log(`[USDA] Scaled ${ingredientText} using fallback: ${fallbackGrams}g = ${scaleFactor.toFixed(2)}x factor`);
              resolve(scaledNutrition);
              return;
            }

            // Fallback didn't work, get full food details for accurate foodPortions
            // (Only happens for uncommon units or when we need precise measurements)
            const fdcId = food.fdcId;
            const detailUrl = `${USDA_BASE_URL}/food/${fdcId}?api_key=${USDA_API_KEY}`;
            const detailUrlObj = new URL(detailUrl);
            
            const detailOptions = {
              hostname: detailUrlObj.hostname,
              path: detailUrlObj.pathname + detailUrlObj.search,
              method: 'GET',
              headers: {
                'Content-Type': 'application/json'
              },
              timeout: 15000
            };

            const detailReq = https.request(detailOptions, (detailRes) => {
              let detailData = '';

              detailRes.on('data', (chunk) => {
                detailData += chunk;
              });

              detailRes.on('end', () => {
                try {
                  if (detailRes.statusCode !== 200) {
                    // Fallback to search result if detail fails
                    console.warn(`[USDA] Detail lookup failed for FDC ${fdcId}, using search result`);
                    resolve(nutritionPer100g);
                    return;
                  }

                  const detailJson = JSON.parse(detailData);
                  const detailNutritionPer100g = parseUSDAFood(detailJson);
                  
                  // Get gram weight for the requested unit from foodPortions
                  const foodPortions = detailJson.foodPortions || [];
                  const totalGrams = getGramWeightForUnit(foodPortions, unit, quantity);

                  if (totalGrams && totalGrams > 0) {
                    // Scale nutrition by: (nutritionPer100g × totalGrams) / 100
                    const scaleFactor = totalGrams / 100.0;
                    
                    const scaledNutrition = {
                      calories: Math.round(detailNutritionPer100g.calories * scaleFactor),
                      proteinG: detailNutritionPer100g.proteinG * scaleFactor,
                      carbsG: detailNutritionPer100g.carbsG * scaleFactor,
                      fatG: detailNutritionPer100g.fatG * scaleFactor,
                      sugarG: detailNutritionPer100g.sugarG * scaleFactor,
                      addedSugarG: detailNutritionPer100g.addedSugarG * scaleFactor,
                      micronutrients: {} as Record<string, number>
                    };

                    // Scale micronutrients
                    for (const [key, value] of Object.entries(detailNutritionPer100g.micronutrients)) {
                      scaledNutrition.micronutrients[key] = value * scaleFactor;
                    }

                    console.log(`[USDA] Scaled ${ingredientText} using foodPortions: ${totalGrams}g = ${scaleFactor.toFixed(2)}x factor`);
                    resolve(scaledNutrition);
                  } else {
                    // No unit match found, return per-100g values
                    console.warn(`[USDA] Could not find gram weight for "${unit}" in ${ingredientText}, returning per-100g values`);
                    resolve(detailNutritionPer100g);
                  }
                } catch (error: any) {
                  reject(new Error(`Failed to parse USDA detail response: ${error.message}`));
                }
              });
            });

            detailReq.on('error', (error) => {
              console.error(`[USDA] Detail request error for ${ingredientText}`, error);
              // Fallback to search result
              resolve(nutritionPer100g);
            });

            detailReq.on('timeout', () => {
              detailReq.destroy();
              // Fallback to search result
              resolve(nutritionPer100g);
            });

            detailReq.setTimeout(15000);
            detailReq.end();

          } catch (error: any) {
            reject(new Error(`Failed to parse USDA search response: ${error.message}`));
          }
        });
      });

      searchReq.on('error', (error) => {
        console.error(`[USDA] Search request error for ${ingredientText}`, error);
        reject(new Error(`Network error: ${error.message}`));
      });

      searchReq.on('timeout', () => {
        searchReq.destroy();
        reject(new Error('USDA API search request timeout'));
      });

      searchReq.setTimeout(15000);
      searchReq.end();
    } catch (error: any) {
      console.error(`[USDA] Error looking up ingredient: ${ingredientText}`, error);
      reject(new Error(`Failed to lookup ingredient nutrition: ${error.message || 'Unknown error'}`));
    }
  });
}

/**
 * Parse USDA food JSON into nutrition info
 */
function parseUSDAFood(foodJson: any): {
  calories: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
  sugarG: number;
  addedSugarG: number;
  micronutrients: Record<string, number>;
} {
  const nutrients = foodJson.foodNutrients || [];
  
  let calories = 0;
  let proteinG = 0;
  let carbsG = 0;
  let fatG = 0;
  let sugarG = 0;
  let addedSugarG = 0;
  const micronutrients: Record<string, number> = {};

  for (const nutrient of nutrients) {
    const nutrientId = nutrient.nutrientId;
    const amount = nutrient.value || 0;

    // Map USDA nutrient IDs to our nutrition values
    switch (nutrientId) {
      case 1008: // Energy (kcal)
        calories = Math.round(amount);
        break;
      case 1003: // Protein
        proteinG = amount;
        break;
      case 1005: // Carbohydrate, by difference
        carbsG = amount;
        break;
      case 1004: // Total lipid (fat)
        fatG = amount;
        break;
      case 2000: // Sugars, total including NLEA
        sugarG = amount;
        break;
      case 1235: // Added sugars
        addedSugarG = amount;
        break;
      
      // Micronutrients - map to snake_case keys
      case 1106: // Vitamin A, RAE (mcg)
        micronutrients.vitamin_a = amount;
        break;
      case 1162: // Vitamin C (mg)
        micronutrients.vitamin_c = amount;
        break;
      case 1110: // Vitamin D (D2 + D3) - Convert mcg to IU (1 mcg = 40 IU)
        micronutrients.vitamin_d = amount * 40.0;
        break;
      case 1109: // Vitamin E (alpha-tocopherol) (mg)
        micronutrients.vitamin_e = amount;
        break;
      case 1185: // Vitamin K (phylloquinone) (mcg)
        micronutrients.vitamin_k = amount;
        break;
      case 1165: // Thiamin (mg)
        micronutrients.vitamin_b1 = amount;
        break;
      case 1166: // Riboflavin (mg)
        micronutrients.vitamin_b2 = amount;
        break;
      case 1167: // Niacin (mg)
        micronutrients.vitamin_b3 = amount;
        break;
      case 1170: // Pantothenic acid (mg)
        micronutrients.vitamin_b5 = amount;
        break;
      case 1175: // Vitamin B6 (mg)
        micronutrients.vitamin_b6 = amount;
        break;
      case 1176: // Biotin (mcg)
        micronutrients.vitamin_b7 = amount;
        break;
      case 1177: // Folate, total (mcg)
        micronutrients.vitamin_b9 = amount;
        break;
      case 1178: // Vitamin B12 (mcg)
        micronutrients.vitamin_b12 = amount;
        break;
      case 1180: // Choline, total (mg)
        micronutrients.choline = amount;
        break;
      case 1087: // Calcium, Ca (mg)
        micronutrients.calcium = amount;
        break;
      case 1089: // Iron, Fe (mg)
        micronutrients.iron = amount;
        break;
      case 1090: // Magnesium, Mg (mg)
        micronutrients.magnesium = amount;
        break;
      case 1091: // Phosphorus, P (mg)
        micronutrients.phosphorus = amount;
        break;
      case 1092: // Potassium, K (mg)
        micronutrients.potassium = amount;
        break;
      case 1093: // Sodium, Na (mg)
        micronutrients.sodium = amount;
        break;
      case 1095: // Zinc, Zn (mg)
        micronutrients.zinc = amount;
        break;
      case 1103: // Copper, Cu (mg)
        micronutrients.copper = amount;
        break;
      case 1104: // Manganese, Mn (mg)
        micronutrients.manganese = amount;
        break;
      case 1105: // Selenium, Se (mcg)
        micronutrients.selenium = amount;
        break;
      case 1100: // Iodine, I (mcg)
        micronutrients.iodine = amount;
        break;
      case 1101: // Chromium, Cr (mcg)
        micronutrients.chromium = amount;
        break;
      case 1102: // Molybdenum, Mo (mcg)
        micronutrients.molybdenum = amount;
        break;
    }
  }

  // Filter out zero micronutrient values
  const filteredMicros: Record<string, number> = {};
  for (const [key, value] of Object.entries(micronutrients)) {
    if (value > 0) {
      filteredMicros[key] = value;
    }
  }

  return {
    calories,
    proteinG,
    carbsG,
    fatG,
    sugarG,
    addedSugarG,
    micronutrients: filteredMicros
  };
}

/**
 * Look up nutrition for multiple ingredients in parallel (with rate limiting)
 * @param ingredients - Array of ingredient strings (e.g., ["1 cup broccoli", "200g chicken breast"])
 * @param maxConcurrent - Maximum concurrent API calls (default: 5 to respect rate limits)
 * @returns Array of nutrition info for each ingredient
 */
export async function lookupMultipleIngredients(
  ingredients: string[],
  maxConcurrent: number = 5
): Promise<Array<{
  ingredient: string;
  nutrition: {
    calories: number;
    proteinG: number;
    carbsG: number;
    fatG: number;
    sugarG: number;
    addedSugarG: number;
    micronutrients: Record<string, number>;
  } | null;
  error?: string;
}>> {
  const results: Array<{
    ingredient: string;
    nutrition: any;
    error?: string;
  }> = [];

  // Process in batches to respect rate limits
  for (let i = 0; i < ingredients.length; i += maxConcurrent) {
    const batch = ingredients.slice(i, i + maxConcurrent);
    const batchPromises = batch.map(async (ingredient) => {
      try {
        const nutrition = await lookupIngredientNutrition(ingredient);
        return {
          ingredient,
          nutrition
        };
      } catch (error: any) {
        console.warn(`[USDA] Failed to lookup ${ingredient}:`, error.message);
        return {
          ingredient,
          nutrition: null,
          error: error.message
        };
      }
    });

    const batchResults = await Promise.all(batchPromises);
    results.push(...batchResults);

    // Small delay between batches to avoid rate limits
    if (i + maxConcurrent < ingredients.length) {
      await new Promise(resolve => setTimeout(resolve, 200));
    }
  }

  return results;
}
