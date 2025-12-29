package com.coachie.app.data.model

import android.os.Parcelable
import com.coachie.app.data.ai.MealAnalysis
import com.coachie.app.data.model.HealthLog
import com.coachie.app.data.model.toPersistedMicronutrientMap
import com.coachie.app.util.MicronutrientEstimator
import kotlinx.parcelize.Parcelize
import java.util.UUID

/**
 * Data class representing a saved meal that users can quickly re-select
 */
@Parcelize
data class SavedMeal(
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val name: String, // User-friendly name like "My Chicken Stir Fry"
    val foodName: String, // Original food description
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val sugarG: Int = 0,
    val addedSugarG: Int = 0,
    val micronutrients: Map<String, Double> = emptyMap(), // Micronutrients per serving
    val recipeId: String? = null, // Reference to saved recipe (if meal has a recipe)
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
    val useCount: Int = 1,
    val isShared: Boolean = false, // Whether saved meal is shared with friends
    val sharedWith: List<String> = emptyList() // List of friend user IDs who can access this saved meal
) : Parcelable {
    companion object {
        fun fromMealAnalysis(userId: String, name: String, analysis: MealAnalysis): SavedMeal {
            return SavedMeal(
                userId = userId,
                name = name,
                foodName = analysis.food,
                calories = analysis.calories,
                proteinG = analysis.proteinG,
                carbsG = analysis.carbsG,
                fatG = analysis.fatG,
                sugarG = analysis.sugarG,
                addedSugarG = analysis.addedSugarG
            )
        }

        fun fromMealLog(userId: String, name: String, mealLog: HealthLog.MealLog): SavedMeal {
            return SavedMeal(
                userId = userId,
                name = name,
                foodName = mealLog.foodName,
                calories = mealLog.calories,
                proteinG = mealLog.protein,
                carbsG = mealLog.carbs,
                fatG = mealLog.fat,
                sugarG = mealLog.sugar,
                addedSugarG = mealLog.addedSugar,
                micronutrients = mealLog.micronutrients,
                recipeId = mealLog.recipeId // Preserve recipe link
            )
        }
    }

    /**
     * Convert to HealthLog.MealLog for logging
     */
    fun toMealLog(): HealthLog.MealLog {
        // Use stored micronutrients if available, otherwise estimate
        val finalMicronutrients = if (micronutrients.isNotEmpty()) {
            micronutrients
        } else {
            estimateMicronutrients()
        }
        android.util.Log.d("SavedMeal", "Converting $name to MealLog with micronutrients: $finalMicronutrients")
        return HealthLog.MealLog(
            foodName = foodName,
            calories = calories,
            protein = proteinG,
            carbs = carbsG,
            fat = fatG,
            sugar = sugarG,
            addedSugar = addedSugarG,
            micronutrients = finalMicronutrients
        )
    }

    /**
     * Estimate micronutrients based on food name and macros
     * This provides basic estimates to enable vitamin/mineral tracking for saved meals
     */
    private fun estimateMicronutrients(): Map<String, Double> {
        val estimate = MicronutrientEstimator.estimate(
            foodName = foodName,
            calories = calories.toDouble(),
            protein = proteinG.toDouble(),
            carbs = carbsG.toDouble(),
            fat = fatG.toDouble()
        )

        return estimate.toPersistedMicronutrientMap()
    }

    /**
     * Create an updated version with incremented usage
     */
    fun withUpdatedUsage(): SavedMeal {
        return copy(
            lastUsedAt = System.currentTimeMillis(),
            useCount = useCount + 1
        )
    }
}
