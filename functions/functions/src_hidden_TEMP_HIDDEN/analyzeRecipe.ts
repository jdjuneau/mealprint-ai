import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import OpenAI from 'openai';
import { logUsage } from './usage';
import { lookupIngredientNutrition } from './usdaNutritionService';

if (!admin.apps.length) {
  admin.initializeApp();
}

// Migration: Using process.env first (future-proof), functions.config() as fallback (works until March 2026)
const openai = new OpenAI({
  apiKey: process.env.OPENAI_API_KEY || functions.config().openai?.key || '',
});

/**
 * Analyze a recipe from image or text and calculate nutrition per serving
 * Uses extended timeout for image processing and AI analysis
 */
export const analyzeRecipe = functions.runWith({
  timeoutSeconds: 300, // 5 minutes for image processing and AI analysis
  memory: '512MB'
}).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = context.auth.uid;
  const { recipeText, imageBase64, servings } = data;

  if (!recipeText && !imageBase64) {
    throw new functions.https.HttpsError('invalid-argument', 'Either recipeText or imageBase64 must be provided');
  }

  if (!servings || servings < 1) {
    throw new functions.https.HttpsError('invalid-argument', 'Servings must be at least 1');
  }

  try {
    let recipeContent = recipeText;

    // If image provided, extract text from image first
    if (imageBase64) {
      const imageAnalysis = await analyzeRecipeImage(imageBase64);
      recipeContent = imageAnalysis;
    }

    // Analyze recipe and extract ingredients with nutrition
    const analysis = await analyzeRecipeIngredients(recipeContent, servings, userId);

    // Calculate total nutrition
    const totalNutrition = calculateTotalNutrition(analysis.ingredients);

    // Calculate per-serving nutrition
    const perServing = {
      calories: Math.round(totalNutrition.calories / servings),
      proteinG: Math.round(totalNutrition.proteinG / servings),
      carbsG: Math.round(totalNutrition.carbsG / servings),
      fatG: Math.round(totalNutrition.fatG / servings),
      sugarG: Math.round(totalNutrition.sugarG / servings),
      addedSugarG: Math.round(totalNutrition.addedSugarG / servings),
      micronutrients: Object.fromEntries(
        Object.entries(totalNutrition.micronutrients).map(([key, value]) => [
          key,
          value / servings
        ])
      )
    };

    // Log usage
    try {
      const now = new Date();
      const date = now.toISOString().split('T')[0];
      await logUsage({
        userId,
        date,
        timestamp: Date.now(),
        source: 'analyzeRecipe',
        model: 'gpt-4o',
        promptTokens: analysis.promptTokens,
        completionTokens: analysis.completionTokens,
        totalTokens: (analysis.promptTokens || 0) + (analysis.completionTokens || 0),
      });
    } catch (logError) {
      console.warn('Failed to log usage:', logError);
    }

    return {
      success: true,
      recipe: {
        name: analysis.recipeName,
        description: analysis.description,
        servings: servings,
        ingredients: analysis.ingredients,
        instructions: analysis.instructions,
        totalCalories: totalNutrition.calories,
        totalProteinG: totalNutrition.proteinG,
        totalCarbsG: totalNutrition.carbsG,
        totalFatG: totalNutrition.fatG,
        totalSugarG: totalNutrition.sugarG,
        totalAddedSugarG: totalNutrition.addedSugarG,
        micronutrients: totalNutrition.micronutrients,
        perServing: perServing
      }
    };
  } catch (error: any) {
    console.error('Error analyzing recipe:', error);
    
    // Handle timeout errors specifically
    if (error?.code === 'ECONNABORTED' || error?.message?.includes('timeout') || error?.code === 'DEADLINE_EXCEEDED') {
      throw new functions.https.HttpsError('deadline-exceeded', 'Recipe analysis is taking too long. Please try again or use a simpler recipe.');
    }
    
    // Handle OpenAI API errors
    if (error?.status === 429) {
      throw new functions.https.HttpsError('resource-exhausted', 'Too many requests. Please wait a minute and try again.');
    }
    
    throw new functions.https.HttpsError('internal', error.message || 'Failed to analyze recipe');
  }
});

/**
 * Analyze recipe image to extract text
 */
async function analyzeRecipeImage(imageBase64: string): Promise<string> {
  try {
    const response = await openai.chat.completions.create({
      model: 'gpt-4o',
      messages: [
        {
          role: 'system',
          content: 'You are a recipe extraction assistant. Extract all recipe text from images including ingredients, quantities, and instructions. Return the complete recipe text as plain text.'
        },
        {
          role: 'user',
          content: [
            {
              type: 'text',
              text: 'Extract the complete recipe text from this image. Include all ingredients with quantities and any cooking instructions.'
            },
            {
              type: 'image_url',
              image_url: {
                url: `data:image/jpeg;base64,${imageBase64}`
              }
            }
          ]
        }
      ],
      max_tokens: 1000
    }, {
      timeout: 60000 // 60 seconds timeout for image processing
    });

    const content = response.choices[0]?.message?.content || '';
    
    // Log usage for recipe image analysis
    try {
      const userId = 'system'; // Image analysis doesn't have userId context
      const now = new Date();
      const date = now.toISOString().split('T')[0];
      await logUsage({
        userId,
        date,
        timestamp: now.getTime(),
        source: 'analyzeRecipeImage',
        model: 'gpt-4o',
        promptTokens: response.usage?.prompt_tokens,
        completionTokens: response.usage?.completion_tokens,
        totalTokens: response.usage?.total_tokens,
        metadata: { note: 'Recipe image extraction' }
      });
    } catch (logError) {
      console.error('Failed to log recipe image analysis usage:', logError);
    }
    
    return content;
  } catch (error: any) {
    console.error('Error analyzing recipe image:', error);
    throw new Error('Failed to extract recipe from image');
  }
}

/**
 * Analyze recipe text to extract ingredients and calculate nutrition
 * Uses GPT to parse ingredients, then USDA API for accurate nutrition data
 */
export async function analyzeRecipeIngredients(recipeText: string, servings: number, userId?: string): Promise<{
  recipeName: string;
  description: string;
  ingredients: Array<{
    name: string;
    quantity: number;
    unit: string;
    calories: number;
    proteinG: number;
    carbsG: number;
    fatG: number;
    sugarG: number;
    micronutrients: Record<string, number>;
  }>;
  instructions: string[];
  promptTokens?: number;
  completionTokens?: number;
}> {
  // STEP 1: Use GPT to extract ingredient list (name, quantity, unit) - NO nutrition
  const extractionPrompt = `Analyze this recipe and extract ONLY the ingredient names, quantities, and units. Do NOT calculate nutrition.

Recipe:
${recipeText}

This recipe makes ${servings} servings.

For each ingredient, extract:
1. Ingredient name
2. Quantity (as a number)
3. Unit (cups, oz, g, tbsp, tsp, etc.)

Return ONLY valid JSON with this structure:
{
  "recipeName": "Recipe name",
  "description": "Brief description",
  "ingredients": [
    {
      "name": "ingredient name",
      "quantity": 2.0,
      "unit": "cups"
    }
  ],
  "instructions": ["step 1", "step 2", ...]
}`;

  let parsed: any;
  let promptTokens = 0;
  let completionTokens = 0;
  let response: any = null;

  try {
    response = await openai.chat.completions.create({
      model: 'gpt-4o',
      messages: [
        {
          role: 'system',
          content: 'You are a recipe parser. Extract ingredient names, quantities, and units from recipes. Do not calculate nutrition.'
        },
        {
          role: 'user',
          content: extractionPrompt
        }
      ],
      temperature: 0.3,
      max_tokens: 2000 // Reduced since we're not asking for nutrition
    }, {
      timeout: 60000 // 60 seconds timeout
    });

    const content = response.choices[0]?.message?.content || '';
    promptTokens = response.usage?.prompt_tokens || 0;
    completionTokens = response.usage?.completion_tokens || 0;
    
    // Parse JSON response
    let jsonContent = content.trim();
    if (jsonContent.startsWith('```json')) {
      jsonContent = jsonContent.replace(/```json\n?/g, '').replace(/```\n?/g, '');
    } else if (jsonContent.startsWith('```')) {
      jsonContent = jsonContent.replace(/```\n?/g, '');
    }

    parsed = JSON.parse(jsonContent);
  } catch (error: any) {
    console.error('Error extracting ingredients with GPT:', error);
    throw new Error(`Failed to extract ingredients: ${error.message || 'Unknown error'}`);
  }

  // STEP 2: Look up nutrition for each ingredient using USDA API
  const ingredients = parsed.ingredients || [];
  
  // Log usage for ingredient extraction (after we have ingredients and response)
  if (response) {
    try {
      const now = new Date();
      const date = now.toISOString().split('T')[0];
      await logUsage({
        userId: userId || 'system',
        date,
        timestamp: now.getTime(),
        source: 'analyzeRecipeIngredients',
        model: 'gpt-4o',
        promptTokens: response.usage?.prompt_tokens,
        completionTokens: response.usage?.completion_tokens,
        totalTokens: response.usage?.total_tokens,
        metadata: { servings, ingredientCount: ingredients.length }
      });
    } catch (logError) {
      console.error('Failed to log recipe ingredient extraction usage:', logError);
    }
  }
  const ingredientsWithNutrition = [];

  console.log(`[ANALYZE] Extracted ${ingredients.length} ingredients, looking up nutrition from USDA API...`);

  for (const ingredient of ingredients) {
    const ingredientText = `${ingredient.quantity} ${ingredient.unit} ${ingredient.name}`;
    
    try {
      // Look up nutrition from USDA API
      const nutrition = await lookupIngredientNutrition(ingredientText);
      
      ingredientsWithNutrition.push({
        name: ingredient.name,
        quantity: ingredient.quantity,
        unit: ingredient.unit,
        calories: nutrition.calories,
        proteinG: nutrition.proteinG,
        carbsG: nutrition.carbsG,
        fatG: nutrition.fatG,
        sugarG: nutrition.sugarG,
        micronutrients: nutrition.micronutrients
      });

      console.log(`[ANALYZE] ✅ ${ingredientText}: ${nutrition.calories} cal, ${nutrition.proteinG}g P, ${nutrition.carbsG}g C, ${nutrition.fatG}g F`);
    } catch (error: any) {
      console.warn(`[ANALYZE] ⚠️ Failed to lookup ${ingredientText} from USDA:`, error.message);
      
      // Fallback: Use zero nutrition (better than failing completely)
      // In production, you might want to retry or use a different lookup method
      ingredientsWithNutrition.push({
        name: ingredient.name,
        quantity: ingredient.quantity,
        unit: ingredient.unit,
        calories: 0,
        proteinG: 0,
        carbsG: 0,
        fatG: 0,
        sugarG: 0,
        micronutrients: {}
      });
    }
  }

  return {
    recipeName: parsed.recipeName || 'Recipe',
    description: parsed.description || '',
    ingredients: ingredientsWithNutrition,
    instructions: parsed.instructions || [],
    promptTokens,
    completionTokens
  };
}

/**
 * Calculate total nutrition from ingredients
 */
export function calculateTotalNutrition(ingredients: Array<{
  calories: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
  sugarG: number;
  micronutrients: Record<string, number>;
}>): {
  calories: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
  sugarG: number;
  addedSugarG: number;
  micronutrients: Record<string, number>;
} {
  const totals = {
    calories: 0,
    proteinG: 0,
    carbsG: 0,
    fatG: 0,
    sugarG: 0,
    addedSugarG: 0,
    micronutrients: {} as Record<string, number>
  };

  // Map camelCase micronutrient names from AI to snake_case format expected by app
  const micronutrientKeyMap: Record<string, string> = {
    'vitamina': 'vitamin_a',
    'vitaminc': 'vitamin_c',
    'vitamind': 'vitamin_d',
    'vitamine': 'vitamin_e',
    'vitamink': 'vitamin_k',
    'vitaminb1': 'vitamin_b1',
    'thiamin': 'vitamin_b1',
    'vitaminb2': 'vitamin_b2',
    'riboflavin': 'vitamin_b2',
    'vitaminb3': 'vitamin_b3',
    'niacin': 'vitamin_b3',
    'vitaminb5': 'vitamin_b5',
    'pantothenic': 'vitamin_b5',
    'vitaminb6': 'vitamin_b6',
    'vitaminb7': 'vitamin_b7',
    'biotin': 'vitamin_b7',
    'vitaminb9': 'vitamin_b9',
    'folate': 'vitamin_b9',
    'folic': 'vitamin_b9',
    'vitaminb12': 'vitamin_b12',
    'calcium': 'calcium',
    'magnesium': 'magnesium',
    'potassium': 'potassium',
    'sodium': 'sodium',
    'iron': 'iron',
    'zinc': 'zinc',
    'iodine': 'iodine',
    'selenium': 'selenium',
    'phosphorus': 'phosphorus',
    'manganese': 'manganese',
    'copper': 'copper',
    'chromium': 'chromium',
    'molybdenum': 'molybdenum',
    'choline': 'choline'
  };

  ingredients.forEach(ingredient => {
    totals.calories += ingredient.calories || 0;
    totals.proteinG += ingredient.proteinG || 0;
    totals.carbsG += ingredient.carbsG || 0;
    totals.fatG += ingredient.fatG || 0;
    totals.sugarG += ingredient.sugarG || 0;

    // Sum micronutrients and map keys to snake_case format
    Object.entries(ingredient.micronutrients || {}).forEach(([key, value]) => {
      const normalizedKey = key.toLowerCase();
      const mappedKey = micronutrientKeyMap[normalizedKey] || normalizedKey;
      totals.micronutrients[mappedKey] = (totals.micronutrients[mappedKey] || 0) + (value || 0);
    });
  });

  return totals;
}

