import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import { lookupIngredientNutrition } from './usdaNutritionService';
import { calculateTotalNutrition } from './analyzeRecipe';

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

/**
 * Refresh macros and micronutrients for existing recipes using USDA API
 * This function updates recipes with accurate nutrition data from USDA instead of GPT estimates
 * 
 * Usage:
 * - Refresh all recipes: refreshRecipeNutrition({ allUsers: true })
 * - Refresh user's recipes: refreshRecipeNutrition({ userId: "user123" })
 * - Refresh specific recipe: refreshRecipeNutrition({ userId: "user123", recipeId: "recipe456" })
 */
export const refreshRecipeNutrition = functions.runWith({
  timeoutSeconds: 540, // 9 minutes (max for v1 functions)
  memory: '1GB'
}).https.onCall(async (data, context) => {
  // Require authentication
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const { userId, recipeId, allUsers, batchSize = 10, dryRun = false } = data;
  const callerId = context.auth.uid;

  // Only allow admins to refresh all users' recipes
  if (allUsers) {
    const callerDoc = await db.collection('users').doc(callerId).get();
    const callerData = callerDoc.data();
    const isAdmin = callerData?.isAdmin === true;
    
    if (!isAdmin) {
      throw new functions.https.HttpsError('permission-denied', 'Only admins can refresh all users\' recipes');
    }
  }

  const results = {
    processed: 0,
    updated: 0,
    failed: 0,
    skipped: 0,
    errors: [] as string[],
    details: [] as Array<{ recipeId: string; recipeName: string; status: string; error?: string }>
  };

  try {
    if (recipeId && userId) {
      // Refresh single recipe
      await refreshSingleRecipe(userId, recipeId, results, dryRun);
    } else if (userId) {
      // Refresh all recipes for a user
      await refreshUserRecipes(userId, results, batchSize, dryRun);
    } else if (allUsers) {
      // Refresh all recipes for all users (admin only)
      await refreshAllRecipes(results, batchSize, dryRun);
    } else {
      throw new functions.https.HttpsError('invalid-argument', 'Must provide userId, userId+recipeId, or allUsers=true');
    }

    return {
      success: true,
      message: dryRun 
        ? `Dry run complete. Would update ${results.updated} recipes.`
        : `Successfully refreshed ${results.updated} recipes.`,
      results
    };
  } catch (error: any) {
    console.error('Error refreshing recipe nutrition:', error);
    throw new functions.https.HttpsError('internal', `Failed to refresh recipes: ${error.message}`);
  }
});

/**
 * Refresh a single recipe
 */
async function refreshSingleRecipe(
  userId: string,
  recipeId: string,
  results: any,
  dryRun: boolean
): Promise<void> {
  const recipeRef = db.collection('users').doc(userId).collection('recipes').doc(recipeId);
  const recipeDoc = await recipeRef.get();

  if (!recipeDoc.exists) {
    results.failed++;
    results.errors.push(`Recipe ${recipeId} not found for user ${userId}`);
    return;
  }

  const recipeData = recipeDoc.data()!;
  results.processed++;

  try {
    const updated = await refreshRecipeData(recipeData, dryRun);
    
    if (updated) {
      if (!dryRun) {
        await recipeRef.update(updated);
      }
      results.updated++;
      results.details.push({
        recipeId,
        recipeName: recipeData.name || 'Unknown',
        status: dryRun ? 'would-update' : 'updated'
      });
    } else {
      results.skipped++;
      results.details.push({
        recipeId,
        recipeName: recipeData.name || 'Unknown',
        status: 'skipped'
      });
    }
  } catch (error: any) {
    results.failed++;
    const errorMsg = `Failed to refresh recipe ${recipeId}: ${error.message}`;
    results.errors.push(errorMsg);
    results.details.push({
      recipeId,
      recipeName: recipeData.name || 'Unknown',
      status: 'error',
      error: error.message
    });
  }
}

/**
 * Refresh all recipes for a user
 */
async function refreshUserRecipes(
  userId: string,
  results: any,
  batchSize: number,
  dryRun: boolean
): Promise<void> {
  const recipesRef = db.collection('users').doc(userId).collection('recipes');
  let lastDoc: admin.firestore.DocumentSnapshot | null = null;
  let hasMore = true;

  while (hasMore) {
    let query = recipesRef.limit(batchSize);
    if (lastDoc) {
      query = query.startAfter(lastDoc);
    }

    const snapshot = await query.get();
    
    if (snapshot.empty) {
      hasMore = false;
      break;
    }

    // Process batch
    const batch = db.batch();
    let batchCount = 0;

    for (const doc of snapshot.docs) {
      const recipeData = doc.data();
      results.processed++;

      try {
        const updated = await refreshRecipeData(recipeData, dryRun);
        
        if (updated) {
          if (!dryRun) {
            batch.update(doc.ref, updated);
            batchCount++;
          }
          results.updated++;
          results.details.push({
            recipeId: doc.id,
            recipeName: recipeData.name || 'Unknown',
            status: dryRun ? 'would-update' : 'updated'
          });
        } else {
          results.skipped++;
          results.details.push({
            recipeId: doc.id,
            recipeName: recipeData.name || 'Unknown',
            status: 'skipped'
          });
        }
      } catch (error: any) {
        results.failed++;
        const errorMsg = `Failed to refresh recipe ${doc.id}: ${error.message}`;
        results.errors.push(errorMsg);
        results.details.push({
          recipeId: doc.id,
          recipeName: recipeData.name || 'Unknown',
          status: 'error',
          error: error.message
        });
      }
    }

    if (batchCount > 0 && !dryRun) {
      await batch.commit();
    }

    lastDoc = snapshot.docs[snapshot.docs.length - 1];
    hasMore = snapshot.docs.length === batchSize;
  }
}

/**
 * Refresh all recipes for all users (admin only)
 */
async function refreshAllRecipes(
  results: any,
  batchSize: number,
  dryRun: boolean
): Promise<void> {
  const usersRef = db.collection('users');
  let lastUserDoc: admin.firestore.DocumentSnapshot | null = null;
  let hasMoreUsers = true;

  while (hasMoreUsers) {
    let userQuery = usersRef.limit(10); // Process 10 users at a time
    if (lastUserDoc) {
      userQuery = userQuery.startAfter(lastUserDoc);
    }

    const usersSnapshot = await userQuery.get();
    
    if (usersSnapshot.empty) {
      hasMoreUsers = false;
      break;
    }

    // For each user, refresh their recipes
    for (const userDoc of usersSnapshot.docs) {
      const userId = userDoc.id;
      await refreshUserRecipes(userId, results, batchSize, dryRun);
    }

    lastUserDoc = usersSnapshot.docs[usersSnapshot.docs.length - 1];
    hasMoreUsers = usersSnapshot.docs.length === 10;
  }
}

/**
 * Refresh nutrition data for a single recipe
 * Returns updated data if changes were made, null if skipped
 */
async function refreshRecipeData(
  recipeData: admin.firestore.DocumentData,
  dryRun: boolean
): Promise<admin.firestore.UpdateData<admin.firestore.DocumentData> | null> {
  const ingredients = recipeData.ingredients || [];
  
  if (ingredients.length === 0) {
    return null; // Skip recipes with no ingredients
  }

  const updatedIngredients: any[] = [];
  let hasChanges = false;

  // Refresh nutrition for each ingredient using USDA API
  for (const ingredient of ingredients) {
    const name = ingredient.name || '';
    const quantity = ingredient.quantity || 0;
    const unit = ingredient.unit || '';

    if (!name || quantity <= 0) {
      // Keep original if invalid
      updatedIngredients.push(ingredient);
      continue;
    }

    const ingredientText = `${quantity} ${unit} ${name}`.trim();
    
    try {
      // Look up nutrition from USDA API
      const nutrition = await lookupIngredientNutrition(ingredientText);
      
      // Check if nutrition changed
      const oldCalories = ingredient.calories || 0;
      const oldProtein = ingredient.proteinG || 0;
      const oldCarbs = ingredient.carbsG || 0;
      const oldFat = ingredient.fatG || 0;
      const oldSugar = ingredient.sugarG || 0;

      if (nutrition.calories !== oldCalories ||
          Math.abs(nutrition.proteinG - oldProtein) > 0.1 ||
          Math.abs(nutrition.carbsG - oldCarbs) > 0.1 ||
          Math.abs(nutrition.fatG - oldFat) > 0.1 ||
          Math.abs(nutrition.sugarG - oldSugar) > 0.1) {
        hasChanges = true;
      }

      // Update ingredient with USDA nutrition
      updatedIngredients.push({
        name,
        quantity,
        unit,
        calories: Math.round(nutrition.calories),
        proteinG: Math.round(nutrition.proteinG),
        carbsG: Math.round(nutrition.carbsG),
        fatG: Math.round(nutrition.fatG),
        sugarG: Math.round(nutrition.sugarG),
        micronutrients: nutrition.micronutrients || {}
      });

      console.log(`[REFRESH] ✅ ${ingredientText}: ${nutrition.calories} cal (was ${oldCalories})`);
    } catch (error: any) {
      console.warn(`[REFRESH] ⚠️ Failed to lookup ${ingredientText} from USDA:`, error.message);
      
      // Keep original nutrition if lookup fails
      updatedIngredients.push(ingredient);
    }
  }

  if (!hasChanges && !dryRun) {
    return null; // No changes needed
  }

  // Recalculate totals
  const totals = calculateTotalNutrition(updatedIngredients.map(ing => ({
    calories: ing.calories || 0,
    proteinG: ing.proteinG || 0,
    carbsG: ing.carbsG || 0,
    fatG: ing.fatG || 0,
    sugarG: ing.sugarG || 0,
    micronutrients: ing.micronutrients || {}
  })));

  // Return update data
  return {
    ingredients: updatedIngredients,
    totalCalories: Math.round(totals.calories),
    totalProteinG: Math.round(totals.proteinG),
    totalCarbsG: Math.round(totals.carbsG),
    totalFatG: Math.round(totals.fatG),
    totalSugarG: Math.round(totals.sugarG),
    totalAddedSugarG: Math.round(totals.addedSugarG),
    micronutrients: totals.micronutrients,
    nutritionRefreshedAt: admin.firestore.FieldValue.serverTimestamp()
  };
}
