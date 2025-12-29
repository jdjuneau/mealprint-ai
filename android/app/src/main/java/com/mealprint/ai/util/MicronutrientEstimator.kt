package com.coachie.app.util

import com.mealprint.ai.data.model.MicronutrientType
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Provides heuristic micronutrient estimates for foods when detailed data is missing.
 * Values are rough, data-driven approximations derived from macronutrients and food keywords.
 */
object MicronutrientEstimator {

    fun estimate(
        foodName: String,
        calories: Double,
        protein: Double,
        carbs: Double,
        fat: Double
    ): Map<MicronutrientType, Double> {
        val normalized = foodName.lowercase(Locale.getDefault())
        val totals = mutableMapOf<MicronutrientType, Double>()

        fun add(type: MicronutrientType, amount: Double) {
            if (amount <= 0.0) return
            totals[type] = (totals[type] ?: 0.0) + amount
        }

        if (protein > 5) {
            add(MicronutrientType.VITAMIN_B6, protein * 0.02)
            add(MicronutrientType.VITAMIN_B3, protein * 0.04)
            if (containsAny(normalized, meatKeywords + fishKeywords + eggKeywords)) {
                add(MicronutrientType.VITAMIN_B12, protein * 0.004)
                add(MicronutrientType.IRON, protein * 0.04)
                add(MicronutrientType.ZINC, protein * 0.05)
                add(MicronutrientType.SELENIUM, protein * 1.2)
            }
        }

        if (carbs > 10) {
            add(MicronutrientType.VITAMIN_B1, carbs * 0.01)
            add(MicronutrientType.VITAMIN_B2, carbs * 0.005)
            add(MicronutrientType.VITAMIN_B9, carbs * 0.05)
            add(MicronutrientType.MAGNESIUM, carbs * 1.5)
        }

        if (fat > 5) {
            add(MicronutrientType.VITAMIN_E, fat * 0.9)
        }

        when {
            containsAny(normalized, leafyGreenKeywords) -> {
                add(MicronutrientType.VITAMIN_K, calories * 2.5)
                add(MicronutrientType.VITAMIN_A, calories * 60)
                add(MicronutrientType.VITAMIN_B9, calories * 0.6)
                add(MicronutrientType.MAGNESIUM, calories * 0.8)
                // Leafy greens are good sources of calcium and potassium
                // Spinach: ~99mg calcium, ~558mg potassium per 100g (raw)
                add(MicronutrientType.CALCIUM, calories * 2.0)
                add(MicronutrientType.POTASSIUM, calories * 7.5)
            }
            containsAny(normalized, cruciferousKeywords) -> {
                add(MicronutrientType.VITAMIN_C, calories * 1.0)
                add(MicronutrientType.VITAMIN_K, calories * 1.0)
                // CRITICAL: Broccoli and cruciferous vegetables are excellent sources of calcium and potassium
                // Broccoli: ~47mg calcium per 100g, ~316mg potassium per 100g (raw)
                // Cooked broccoli: ~40mg calcium, ~293mg potassium per 100g
                add(MicronutrientType.CALCIUM, calories * 2.5) // Increased from 1.2 - broccoli has significant calcium
                add(MicronutrientType.POTASSIUM, calories * 8.0) // Broccoli is high in potassium (~293mg per 100g cooked)
                add(MicronutrientType.VITAMIN_B9, calories * 0.4) // Folate
            }
            containsAny(normalized, rootVegKeywords) -> {
                add(MicronutrientType.VITAMIN_A, calories * 40)
                add(MicronutrientType.VITAMIN_C, calories * 0.8)
                add(MicronutrientType.POTASSIUM, calories * 7)
            }
        }

        if (containsAny(normalized, fruitKeywords)) {
            add(MicronutrientType.VITAMIN_C, calories * 1.1)
            add(MicronutrientType.POTASSIUM, calories * 6.5)
            add(MicronutrientType.VITAMIN_A, calories * 8)
        }

        if (containsAny(normalized, grainKeywords)) {
            add(MicronutrientType.VITAMIN_B1, carbs * 0.03)
            add(MicronutrientType.VITAMIN_B3, carbs * 0.04)
            add(MicronutrientType.IRON, carbs * 0.02)
            add(MicronutrientType.MAGNESIUM, carbs * 1.8)
        }

        if (containsAny(normalized, legumeKeywords)) {
            add(MicronutrientType.VITAMIN_B9, carbs * 0.08)
            add(MicronutrientType.MAGNESIUM, protein * 2.0)
            add(MicronutrientType.IRON, protein * 0.03)
            add(MicronutrientType.ZINC, protein * 0.02)
        }

        if (containsAny(normalized, nutKeywords)) {
            add(MicronutrientType.MAGNESIUM, fat * 3.0)
            add(MicronutrientType.VITAMIN_E, fat * 1.5)
            add(MicronutrientType.SELENIUM, fat * 2.0)
        }

        if (containsAny(normalized, dairyKeywords)) {
            add(MicronutrientType.CALCIUM, calories * 3.2)
            add(MicronutrientType.VITAMIN_D, calories * 0.08)
            add(MicronutrientType.VITAMIN_B12, protein * 0.01)
            add(MicronutrientType.VITAMIN_A, calories * 12)
        }

        if (containsAny(normalized, meatKeywords)) {
            add(MicronutrientType.VITAMIN_B12, protein * 0.015)
            add(MicronutrientType.IRON, protein * 0.06)
            add(MicronutrientType.ZINC, protein * 0.07)
        }

        if (containsAny(normalized, fishKeywords)) {
            add(MicronutrientType.VITAMIN_D, calories * 0.12)
            add(MicronutrientType.SELENIUM, protein * 2.5)
            add(MicronutrientType.IRON, protein * 0.02)
        }

        if (containsAny(normalized, eggKeywords)) {
            add(MicronutrientType.VITAMIN_B12, protein * 0.01)
            add(MicronutrientType.VITAMIN_D, calories * 0.1)
            add(MicronutrientType.SELENIUM, protein * 1.5)
        }

        if (normalized.contains("avocado")) {
            add(MicronutrientType.VITAMIN_E, fat * 1.4)
            add(MicronutrientType.POTASSIUM, calories * 6)
            add(MicronutrientType.VITAMIN_B9, calories * 0.5)
        }

        if (normalized.contains("banana")) {
            add(MicronutrientType.POTASSIUM, 360.0 * (calories / 90.0))
            add(MicronutrientType.VITAMIN_B6, 0.4 * (calories / 90.0))
            add(MicronutrientType.VITAMIN_C, 9.0 * (calories / 90.0))
        }

        if (normalized.contains("tomato") || normalized.contains("pepper")) {
            add(MicronutrientType.VITAMIN_C, calories * 1.3)
            add(MicronutrientType.VITAMIN_A, calories * 4)
        }

        val rounded = totals
            .mapValues { (_, value) -> roundMicronutrient(value) }
            .filterValues { it > 0.05 }

        return rounded
    }

    private fun containsAny(target: String, keywords: List<String>): Boolean =
        keywords.any { target.contains(it) }

    private fun roundMicronutrient(value: Double): Double = when {
        value <= 0.0 -> 0.0
        value < 1.0 -> (value * 10).roundToInt() / 10.0
        value < 10.0 -> (value * 10).roundToInt() / 10.0
        else -> value.roundToInt().toDouble()
    }

    private val fruitKeywords = listOf(
        "fruit", "berry", "banana", "apple", "orange", "grape", "melon", "pineapple",
        "mango", "pear", "peach", "plum", "kiwi", "papaya", "cherry"
    )

    private val leafyGreenKeywords = listOf(
        "spinach", "kale", "arugula", "romaine", "lettuce", "collard", "greens", "chard"
    )

    private val cruciferousKeywords = listOf(
        "broccoli", "cauliflower", "brussels", "cabbage"
    )

    private val rootVegKeywords = listOf(
        "carrot", "sweet potato", "pumpkin", "beet", "yam"
    )

    private val grainKeywords = listOf(
        "bread", "rice", "pasta", "quinoa", "oat", "grain", "cereal", "barley", "wheat"
    )

    private val legumeKeywords = listOf(
        "bean", "lentil", "chickpea", "pea", "soy", "edamame"
    )

    private val nutKeywords = listOf(
        "nut", "almond", "peanut", "cashew", "walnut", "pecan", "hazelnut", "seed", "chia", "flax"
    )

    private val dairyKeywords = listOf(
        "milk", "cheese", "yogurt", "cream", "butter", "cottage", "whey"
    )

    private val meatKeywords = listOf(
        "beef", "steak", "pork", "chicken", "turkey", "lamb", "bacon", "sausage", "ham", "meat"
    )

    private val fishKeywords = listOf(
        "salmon", "tuna", "cod", "fish", "shrimp", "mackerel", "sardine", "trout", "seafood"
    )

    private val eggKeywords = listOf(
        "egg", "omelette", "scramble"
    )
}
