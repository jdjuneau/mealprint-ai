package com.coachie.app.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Data class representing a recipe with ingredients, instructions, and nutrition per serving
 */
@Parcelize
data class Recipe(
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val name: String, // Recipe name like "Chicken Stir Fry"
    val description: String? = null,
    val servings: Int, // Number of servings the recipe makes
    val ingredients: List<RecipeIngredient>, // List of ingredients with quantities
    val instructions: List<String>? = null, // Cooking instructions
    val photoUrl: String? = null, // Photo of the recipe
    val totalCalories: Int, // Total calories for entire recipe
    val totalProteinG: Int, // Total protein for entire recipe
    val totalCarbsG: Int, // Total carbs for entire recipe
    val totalFatG: Int, // Total fat for entire recipe
    val totalSugarG: Int = 0,
    val totalAddedSugarG: Int = 0,
    val micronutrients: Map<String, Double> = emptyMap(), // Total micronutrients for entire recipe
    val createdAt: Long = System.currentTimeMillis(),
    val isShared: Boolean = false, // Whether recipe is shared with friends
    val sharedWith: List<String> = emptyList() // List of friend user IDs who can access this recipe
) : Parcelable {
    /**
     * Get nutrition per serving
     */
    fun getNutritionPerServing(): RecipeNutrition {
        return RecipeNutrition(
            calories = (totalCalories.toDouble() / servings).roundToInt(),
            proteinG = (totalProteinG.toDouble() / servings).roundToInt(),
            carbsG = (totalCarbsG.toDouble() / servings).roundToInt(),
            fatG = (totalFatG.toDouble() / servings).roundToInt(),
            sugarG = (totalSugarG.toDouble() / servings).roundToInt(),
            addedSugarG = (totalAddedSugarG.toDouble() / servings).roundToInt(),
            micronutrients = micronutrients.mapValues { (it.value / servings) }
        )
    }

    /**
     * Convert to SavedMeal with single serving nutrition (preserves all macros and micros)
     * CRITICAL: Includes recipeId so the recipe can be accessed later
     */
    fun toSavedMeal(newUserId: String? = null): SavedMeal {
        val perServing = getNutritionPerServing()
        return SavedMeal(
            id = UUID.randomUUID().toString(), // New ID for saved meal
            userId = newUserId ?: userId,
            name = name,
            foodName = name,
            calories = perServing.calories,
            proteinG = perServing.proteinG,
            carbsG = perServing.carbsG,
            fatG = perServing.fatG,
            sugarG = perServing.sugarG,
            addedSugarG = perServing.addedSugarG,
            micronutrients = perServing.micronutrients, // Preserve micronutrients
            recipeId = id, // CRITICAL: Link to recipe so it can be accessed later
            createdAt = System.currentTimeMillis()
        )
    }

    /**
     * Convert to HealthLog.MealLog for a specific number of servings
     */
    fun toMealLog(servingsConsumed: Int = 1): HealthLog.MealLog {
        val perServing = getNutritionPerServing()
        return HealthLog.MealLog(
            foodName = name,
            calories = perServing.calories * servingsConsumed,
            protein = perServing.proteinG * servingsConsumed,
            carbs = perServing.carbsG * servingsConsumed,
            fat = perServing.fatG * servingsConsumed,
            sugar = perServing.sugarG * servingsConsumed,
            addedSugar = perServing.addedSugarG * servingsConsumed,
            micronutrients = perServing.micronutrients.mapValues { (it.value * servingsConsumed) }
        )
    }
}

/**
 * Data class for a single ingredient in a recipe
 */
@Parcelize
data class RecipeIngredient(
    val name: String, // Ingredient name
    val quantity: Double, // Quantity amount
    val unit: String, // Unit (cups, oz, g, etc.)
    val calories: Int = 0, // Calories for this ingredient
    val proteinG: Int = 0,
    val carbsG: Int = 0,
    val fatG: Int = 0,
    val sugarG: Int = 0,
    val micronutrients: Map<String, Double> = emptyMap()
) : Parcelable

/**
 * Data class for nutrition per serving
 */
data class RecipeNutrition(
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val sugarG: Int = 0,
    val addedSugarG: Int = 0,
    val micronutrients: Map<String, Double> = emptyMap()
)

