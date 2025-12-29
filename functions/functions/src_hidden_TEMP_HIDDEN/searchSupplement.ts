import * as functions from "firebase-functions";
import OpenAI from "openai";
import { logUsage } from './usage';

if (!require('firebase-admin').apps.length) {
  require('firebase-admin').initializeApp();
}

const openai = new OpenAI({
  // Migration: process.env first (future-proof), functions.config() as fallback
  apiKey: process.env.OPENAI_API_KEY || functions.config().openai?.key || "",
});

/**
 * Search for supplement information by brand and type using AI
 * Handles complex cases like organ supplements where only organ amounts are listed
 */
export const searchSupplement = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const { brand, supplementType } = data;
  
  if (!brand || !supplementType) {
    throw new functions.https.HttpsError('invalid-argument', 'Brand and supplement type are required');
  }

  const searchQuery = `${brand} ${supplementType}`;
  console.log(`[SEARCH_SUPPLEMENT] Searching for: ${searchQuery}`);

  try {
    const prompt = `You are a supplement nutrition expert. Search for detailed nutrition information for this supplement:

Brand: ${brand}
Type: ${supplementType}

IMPORTANT: This supplement may list ingredients (like "beef liver 500mg", "beef heart 300mg") instead of direct vitamin/mineral amounts. If that's the case, you need to:

1. Identify the organ/ingredient types and amounts
2. Look up the nutritional content of those organs/ingredients
3. Calculate the vitamin and mineral amounts based on the listed organ amounts
4. Provide the micronutrient breakdown

For example, if a supplement says "Beef Liver 500mg", you need to calculate:
- How much Vitamin A is in 500mg of beef liver
- How much Iron is in 500mg of beef liver
- How much B12 is in 500mg of beef liver
- etc.

Return ONLY valid JSON in this exact format:
{
  "supplementName": "${brand} ${supplementType}",
  "nutrients": {
    "vitamin_a": <amount in mcg>,
    "vitamin_c": <amount in mg>,
    "vitamin_d": <amount in IU>,
    "vitamin_e": <amount in mg>,
    "vitamin_k": <amount in mcg>,
    "vitamin_b1": <amount in mg>,
    "vitamin_b2": <amount in mg>,
    "vitamin_b3": <amount in mg>,
    "vitamin_b5": <amount in mg>,
    "vitamin_b6": <amount in mg>,
    "vitamin_b7": <amount in mcg>,
    "vitamin_b9": <amount in mcg>,
    "vitamin_b12": <amount in mcg>,
    "calcium": <amount in mg>,
    "magnesium": <amount in mg>,
    "potassium": <amount in mg>,
    "sodium": <amount in mg>,
    "iron": <amount in mg>,
    "zinc": <amount in mg>,
    "iodine": <amount in mcg>,
    "selenium": <amount in mcg>,
    "phosphorus": <amount in mg>,
    "manganese": <amount in mg>,
    "copper": <amount in mcg>
  },
  "servingSize": "<serving size description>",
  "notes": "<any relevant notes about the supplement or calculation method>"
}

Only include nutrients that are present in significant amounts (>0). Use 0 or omit if not present.
For organ supplements, show your calculation method in the notes field.

If you cannot find reliable information, return:
{
  "supplementName": "${brand} ${supplementType}",
  "nutrients": {},
  "notes": "Could not find reliable nutrition information for this supplement"
}`;

    const response = await openai.chat.completions.create({
      model: 'gpt-4o-mini',
      messages: [
        {
          role: 'system',
          content: 'You are a supplement nutrition expert. Provide accurate vitamin and mineral information. For organ supplements, calculate micronutrients based on the organ amounts listed.'
        },
        { role: 'user', content: prompt }
      ],
      temperature: 0.3,
      max_tokens: 2000
    });

    const content = response.choices[0]?.message?.content?.trim();
    if (!content) {
      throw new Error('AI returned empty response');
    }
    
    // Log usage for supplement search
    try {
      const userId = context.auth?.uid || 'system';
      const now = new Date();
      const date = now.toISOString().split('T')[0];
      await logUsage({
        userId,
        date,
        timestamp: now.getTime(),
        source: 'searchSupplement',
        model: 'gpt-4o-mini',
        promptTokens: response.usage?.prompt_tokens,
        completionTokens: response.usage?.completion_tokens,
        totalTokens: response.usage?.total_tokens,
        metadata: { brand, supplementType }
      });
    } catch (logError) {
      console.error('Failed to log supplement search usage:', logError);
    }

    // Parse JSON from response
    let jsonText = content;
    const jsonStart = content.indexOf('{');
    const jsonEnd = content.lastIndexOf('}') + 1;
    if (jsonStart >= 0 && jsonEnd > jsonStart) {
      jsonText = content.substring(jsonStart, jsonEnd);
    }

    const result = JSON.parse(jsonText);
    console.log(`[SEARCH_SUPPLEMENT] Found ${Object.keys(result.nutrients || {}).length} nutrients`);

    return result;
  } catch (error: any) {
    console.error('[SEARCH_SUPPLEMENT] Error:', error);
    throw new functions.https.HttpsError('internal', `Failed to search supplement: ${error.message}`);
  }
});

