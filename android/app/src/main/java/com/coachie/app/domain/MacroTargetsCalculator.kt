package com.coachie.app.domain

import com.coachie.app.data.model.DietaryPreference
import com.coachie.app.data.model.UserProfile
import kotlin.math.roundToInt

data class MacroTargets(
    val calorieGoal: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
    val proteinPercent: Int,
    val carbsPercent: Int,
    val fatPercent: Int,
    val recommendation: String
)

object MacroTargetsCalculator {
    fun calculate(profile: UserProfile?): MacroTargets {
        val calories = profile?.estimatedDailyCalories ?: 2000
        val preference = profile?.dietaryPreferenceEnum ?: DietaryPreference.BALANCED

        var carbsRatio = preference.carbsRatio
        var proteinRatio = preference.proteinRatio
        var fatRatio = preference.fatRatio

        val goalTrend = when {
            profile == null -> GoalTrend.MAINTAIN
            profile.goalWeight < profile.currentWeight - 0.1 -> GoalTrend.LOSE
            profile.goalWeight > profile.currentWeight + 0.1 -> GoalTrend.GAIN
            else -> GoalTrend.MAINTAIN
        }

        // Adjust ratios for the goal when diet allows moderate flexibility
        if (preference !in setOf(DietaryPreference.KETOGENIC, DietaryPreference.VERY_LOW_CARB, DietaryPreference.CARNIVORE)) {
            when (goalTrend) {
                GoalTrend.LOSE -> {
                    proteinRatio += 0.05
                    carbsRatio -= 0.05
                }

                GoalTrend.GAIN -> {
                    carbsRatio += 0.05
                    fatRatio += 0.02
                    proteinRatio -= 0.02
                }

                else -> Unit
            }
        }

        // Keep ratios within sensible bounds
        carbsRatio = carbsRatio.coerceIn(0.0, 0.65)
        proteinRatio = proteinRatio.coerceIn(0.15, 0.45)
        fatRatio = fatRatio.coerceIn(0.2, 0.8)

        // Normalize to ensure total equals 1.0
        val total = carbsRatio + proteinRatio + fatRatio
        if (total != 0.0) {
            carbsRatio /= total
            proteinRatio /= total
            fatRatio /= total
        }

        val weightKg = profile?.currentWeight?.takeIf { it > 0 } ?: 75.0

        val provisionalProteinGrams = (calories * proteinRatio / 4.0)
        val proteinMinPerKg = when (goalTrend) {
            GoalTrend.LOSE -> 1.5
            GoalTrend.GAIN -> 1.4
            GoalTrend.MAINTAIN -> 1.3
        }
        val proteinMaxPerKg = when (preference) {
            DietaryPreference.HIGH_PROTEIN -> 2.2
            DietaryPreference.CARNIVORE -> 2.4
            else -> 2.0
        }
        val proteinMinGrams = (proteinMinPerKg * weightKg).coerceAtLeast(80.0)
        val proteinMaxGrams = (proteinMaxPerKg * weightKg).coerceAtMost(220.0).coerceAtLeast(proteinMinGrams)
        val proteinGrams = provisionalProteinGrams.coerceIn(proteinMinGrams, proteinMaxGrams)

        val minFatCalories = (calories * 0.20).coerceAtLeast(weightKg * 9 * 0.5)

        val caloriesRemainingAfterProtein = (calories - proteinGrams * 4.0).coerceAtLeast(0.0)
        val carbAndFatRatioTotal = carbsRatio + fatRatio
        val carbsShare = if (carbAndFatRatioTotal > 0) carbsRatio / carbAndFatRatioTotal else 0.6

        var fatCalories = (caloriesRemainingAfterProtein * (1 - carbsShare)).coerceAtLeast(minFatCalories)
        fatCalories = fatCalories.coerceAtMost(caloriesRemainingAfterProtein)
        val carbCalories = (caloriesRemainingAfterProtein - fatCalories).coerceAtLeast(0.0)

        val carbsGrams = (carbCalories / 4.0).roundToInt().coerceAtLeast(0)
        val fatGrams = (fatCalories / 9.0).roundToInt().coerceAtLeast(0)
        val proteinGramsInt = proteinGrams.roundToInt()

        val proteinPercent = ((proteinGramsInt * 4.0) / calories * 100).roundToInt()
        val carbsPercent = if (calories > 0) ((carbsGrams * 4.0) / calories * 100).roundToInt() else 0
        val fatPercent = if (calories > 0) ((fatGrams * 9.0) / calories * 100).roundToInt() else 0

        val recommendation = buildString {
            append("${preference.title} focus: ${carbsPercent}% carbs / ${proteinPercent}% protein / ${fatPercent}% fat")
            when (goalTrend) {
                GoalTrend.LOSE -> append(" • Elevated protein and slightly lower carbs to support fat loss.")
                GoalTrend.GAIN -> append(" • Extra carbs and fats to fuel muscle gain and recovery.")
                GoalTrend.MAINTAIN -> append(" • Balanced ratios to support maintenance.")
            }
        }

        return MacroTargets(
            calorieGoal = calories,
            proteinGrams = proteinGramsInt,
            carbsGrams = carbsGrams,
            fatGrams = fatGrams,
            proteinPercent = proteinPercent,
            carbsPercent = carbsPercent,
            fatPercent = fatPercent,
            recommendation = recommendation
        )
    }

    private enum class GoalTrend {
        LOSE, GAIN, MAINTAIN
    }
}

