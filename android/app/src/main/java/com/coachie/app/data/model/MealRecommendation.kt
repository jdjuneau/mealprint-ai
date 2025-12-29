package com.coachie.app.data.model

data class IngredientOption(
    val id: String,
    val name: String,
    val category: IngredientCategory
)

enum class IngredientCategory {
    PROTEIN,
    VEGETABLE,
    FRUIT,
    GRAIN,
    HEALTHY_FAT,
    PANTRY,
    OTHER
}

enum class MealType(val displayName: String, val id: String) {
    BREAKFAST("Breakfast", "breakfast"),
    BRUNCH("Brunch", "brunch"),
    LUNCH("Lunch", "lunch"),
    DINNER("Dinner", "dinner"),
    DESSERT("Dessert", "dessert");
    
    companion object {
        fun fromId(id: String?): MealType? {
            return values().find { it.id == id }
        }
    }
}

data class IngredientCategoryGroup(
    val category: IngredientCategory,
    val title: String,
    val options: List<IngredientOption>
)

data class MacroSnapshot(
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val sugar: Int = 0,
    val addedSugar: Int = 0
)

data class MealRecommendationRequest(
    val userId: String,
    val dietaryPreference: String,
    val goalTrend: String,
    val calorieGoal: Int,
    val macroTargets: MacroTargetsSnapshot,
    val currentMacros: MacroSnapshot,
    val remainingMacros: MacroSnapshot,
    val selectedIngredients: List<String>,
    val cookingMethod: String? = null, // Optional cooking method for this specific meal
    val mealType: String? = null, // Optional meal type (breakfast, brunch, lunch, dinner, dessert)
    val cyclePhase: String? = null, // Optional menstrual cycle phase (MENSTRUAL, FOLLICULAR, OVULATION, LUTEAL)
    val timestamp: Long = System.currentTimeMillis()
)

data class MacroTargetsSnapshot(
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
    val calorieGoal: Int
)

data class MealRecommendation(
    val recipeTitle: String,
    val summary: String,
    val ingredients: List<String>,
    val instructions: List<String>,
    val macrosPerServing: MacroSnapshot,
    val servings: Int,
    val prepTimeMinutes: Int,
    val fitExplanation: String,
    val groceryList: List<String> = emptyList(),
    val micronutrients: Map<com.coachie.app.data.model.MicronutrientType, Double> = emptyMap()
)

