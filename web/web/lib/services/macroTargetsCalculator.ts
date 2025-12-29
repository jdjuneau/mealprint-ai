/**
 * Macro Targets Calculator (Web Version)
 * Ported from Android MacroTargetsCalculator.kt
 */

import type { UserProfile } from '../../types'

export interface MacroTargets {
  calorieGoal: number
  proteinGrams: number
  carbsGrams: number
  fatGrams: number
  proteinPercent: number
  carbsPercent: number
  fatPercent: number
  recommendation: string
}

// Dietary preference ratios (simplified - matches Android logic)
const DIETARY_PREFERENCE_RATIOS: Record<string, { carbsRatio: number; proteinRatio: number; fatRatio: number; title: string }> = {
  balanced: { carbsRatio: 0.45, proteinRatio: 0.25, fatRatio: 0.30, title: 'Balanced' },
  vegetarian: { carbsRatio: 0.50, proteinRatio: 0.20, fatRatio: 0.30, title: 'Vegetarian' },
  vegan: { carbsRatio: 0.55, proteinRatio: 0.15, fatRatio: 0.30, title: 'Vegan' },
  keto: { carbsRatio: 0.05, proteinRatio: 0.25, fatRatio: 0.70, title: 'Ketogenic' },
  paleo: { carbsRatio: 0.25, proteinRatio: 0.30, fatRatio: 0.45, title: 'Paleo' },
  mediterranean: { carbsRatio: 0.45, proteinRatio: 0.20, fatRatio: 0.35, title: 'Mediterranean' },
  low_carb: { carbsRatio: 0.25, proteinRatio: 0.30, fatRatio: 0.45, title: 'Low Carb' },
  low_fat: { carbsRatio: 0.60, proteinRatio: 0.20, fatRatio: 0.20, title: 'Low Fat' },
  high_protein: { carbsRatio: 0.35, proteinRatio: 0.35, fatRatio: 0.30, title: 'High Protein' },
  pescatarian: { carbsRatio: 0.45, proteinRatio: 0.25, fatRatio: 0.30, title: 'Pescatarian' },
  carnivore: { carbsRatio: 0.0, proteinRatio: 0.30, fatRatio: 0.70, title: 'Carnivore' },
}

enum GoalTrend {
  LOSE = 'LOSE',
  GAIN = 'GAIN',
  MAINTAIN = 'MAINTAIN'
}

export class MacroTargetsCalculator {
  static calculate(profile: UserProfile | null): MacroTargets {
    const calories = profile?.estimatedDailyCalories ?? 2000
    const preferenceKey = (profile?.dietaryPreference || 'balanced').toLowerCase()
    const preference = DIETARY_PREFERENCE_RATIOS[preferenceKey] || DIETARY_PREFERENCE_RATIOS['balanced']

    let carbsRatio = preference.carbsRatio
    let proteinRatio = preference.proteinRatio
    let fatRatio = preference.fatRatio

    // Determine goal trend
    const goalTrend = (() => {
      if (!profile) return GoalTrend.MAINTAIN
      if (profile.goalWeight < profile.currentWeight - 0.1) return GoalTrend.LOSE
      if (profile.goalWeight > profile.currentWeight + 0.1) return GoalTrend.GAIN
      return GoalTrend.MAINTAIN
    })()

    // Adjust ratios for the goal when diet allows moderate flexibility
    if (!['keto', 'very_low_carb', 'carnivore'].includes(preferenceKey)) {
      if (goalTrend === GoalTrend.LOSE) {
        proteinRatio += 0.05
        carbsRatio -= 0.05
      } else if (goalTrend === GoalTrend.GAIN) {
        carbsRatio += 0.05
        fatRatio += 0.02
        proteinRatio -= 0.02
      }
    }

    // Keep ratios within sensible bounds
    carbsRatio = Math.max(0, Math.min(0.65, carbsRatio))
    proteinRatio = Math.max(0.15, Math.min(0.45, proteinRatio))
    fatRatio = Math.max(0.2, Math.min(0.8, fatRatio))

    // Normalize to ensure total equals 1.0
    const total = carbsRatio + proteinRatio + fatRatio
    if (total !== 0) {
      carbsRatio /= total
      proteinRatio /= total
      fatRatio /= total
    }

    const weightKg = (profile?.currentWeight && profile.currentWeight > 0) ? profile.currentWeight : 75.0

    const provisionalProteinGrams = (calories * proteinRatio / 4.0)
    const proteinMinPerKg = goalTrend === GoalTrend.LOSE ? 1.5 : goalTrend === GoalTrend.GAIN ? 1.4 : 1.3
    const proteinMaxPerKg = preferenceKey === 'high_protein' ? 2.2 : preferenceKey === 'carnivore' ? 2.4 : 2.0
    const proteinMinGrams = Math.max(proteinMinPerKg * weightKg, 80.0)
    const proteinMaxGrams = Math.max(Math.min(proteinMaxPerKg * weightKg, 220.0), proteinMinGrams)
    const proteinGrams = Math.max(proteinMinGrams, Math.min(proteinMaxGrams, provisionalProteinGrams))

    const minFatCalories = Math.max(calories * 0.20, weightKg * 9 * 0.5)

    const caloriesRemainingAfterProtein = Math.max(calories - proteinGrams * 4.0, 0)
    const carbAndFatRatioTotal = carbsRatio + fatRatio
    const carbsShare = carbAndFatRatioTotal > 0 ? carbsRatio / carbAndFatRatioTotal : 0.6

    let fatCalories = Math.max(caloriesRemainingAfterProtein * (1 - carbsShare), minFatCalories)
    fatCalories = Math.min(fatCalories, caloriesRemainingAfterProtein)
    const carbCalories = Math.max(caloriesRemainingAfterProtein - fatCalories, 0)

    const carbsGrams = Math.max(0, Math.round(carbCalories / 4.0))
    const fatGrams = Math.max(0, Math.round(fatCalories / 9.0))
    const proteinGramsInt = Math.round(proteinGrams)

    const proteinPercent = Math.round((proteinGramsInt * 4.0) / calories * 100)
    const carbsPercent = calories > 0 ? Math.round((carbsGrams * 4.0) / calories * 100) : 0
    const fatPercent = calories > 0 ? Math.round((fatGrams * 9.0) / calories * 100) : 0

    const recommendation = `${preference.title} focus: ${carbsPercent}% carbs / ${proteinPercent}% protein / ${fatPercent}% fat${
      goalTrend === GoalTrend.LOSE
        ? ' • Elevated protein and slightly lower carbs to support fat loss.'
        : goalTrend === GoalTrend.GAIN
        ? ' • Extra carbs and fats to fuel muscle gain and recovery.'
        : ' • Balanced ratios to support maintenance.'
    }`

    return {
      calorieGoal: calories,
      proteinGrams: proteinGramsInt,
      carbsGrams,
      fatGrams,
      proteinPercent,
      carbsPercent,
      fatPercent,
      recommendation
    }
  }
}

