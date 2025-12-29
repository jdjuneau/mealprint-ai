/**
 * Daily Score Calculator (Web Version)
 * Ported from Android DailyScoreCalculator.kt
 * Calculates Coachie scores from health logs
 */

import type { DailyLog, HealthLog } from '../../types'

export interface CategoryScores {
  healthScore: number
  wellnessScore: number
  habitsScore: number
}

export class DailyScoreCalculator {
  /**
   * Calculate daily score with health-focused weighting:
   * - Health Tracking: 50%
   * - Wellness: 30%
   * - Habits: 20%
   */
  static calculateDailyScore(categoryScores: CategoryScores): number {
    return Math.round(
      categoryScores.healthScore * 0.50 +
      categoryScores.wellnessScore * 0.30 +
      categoryScores.habitsScore * 0.20
    )
  }

  /**
   * Calculate Health Tracking Score (0-100)
   */
  static calculateHealthScore(
    meals: HealthLog[],
    workouts: HealthLog[],
    sleepLogs: HealthLog[],
    waterLogs: HealthLog[],
    dailyLog: DailyLog | null,
    allHealthLogs: HealthLog[],
    calorieGoal: number = 2000,
    stepsGoal: number = 10000,
    waterGoal: number = 2000, // ml
    sleepGoal: number = 8.0 // hours
  ): number {
    let score = 0

    // Calories (0-25 points)
    const caloriesConsumed = meals.reduce((sum, log: any) => sum + (log.calories || 0), 0)
    const calorieProgress = Math.min(Math.max(caloriesConsumed / calorieGoal, 0), 1)
    const calorieScore = Math.round(Math.sqrt(calorieProgress) * 25)
    score += calorieScore

    // Water (0-20 points) - Use DailyLog.waterAmount as source of truth, fallback to waterLogs
    const waterMl = (dailyLog?.waterAmount && dailyLog.waterAmount > 0)
      ? dailyLog.waterAmount
      : waterLogs.reduce((sum, log: any) => sum + (log.amount || 0), 0)
    const waterProgress = Math.min(Math.max(waterMl / waterGoal, 0), 1)
    const waterScore = Math.round(Math.sqrt(waterProgress) * 20)
    score += waterScore

    // Steps (0-15 points)
    const steps = dailyLog?.steps || 0
    const stepsProgress = Math.min(Math.max(steps / stepsGoal, 0), 1)
    const stepsScore = Math.round(Math.sqrt(stepsProgress) * 15)
    score += stepsScore

    // Sleep (0-15 points)
    const sleepHours = sleepLogs.length > 0
      ? Math.max(...sleepLogs.map((log: any) => log.hours || 0))
      : 0
    const sleepProgress = Math.min(Math.max(sleepHours / sleepGoal, 0), 1)
    const sleepScore = Math.round(Math.sqrt(sleepProgress) * 15)
    score += sleepScore

    // Weight (0-10 points)
    const weightLogs = allHealthLogs.filter(log => log.type === 'weight')
    if (weightLogs.length > 0) {
      score += 10
    }

    // Workouts (0-10 points)
    if (workouts.length > 0) {
      const totalWorkoutMinutes = workouts.reduce((sum, log: any) => sum + (log.duration || 0), 0)
      const workoutCount = workouts.length
      const baseWorkoutScore = workoutCount >= 2 ? 10 : 8
      const durationBonus = Math.min(Math.floor(totalWorkoutMinutes / 45), 2)
      score += baseWorkoutScore + durationBonus
    }

    // Consistency (0-5 points)
    const loggedMetrics = [
      meals.length > 0,
      workouts.length > 0,
      sleepLogs.length > 0,
      waterLogs.length > 0,
      (dailyLog?.steps || 0) > 0,
      weightLogs.length > 0,
    ].filter(Boolean).length

    const consistencyScore = loggedMetrics >= 4 ? 5 : loggedMetrics >= 2 ? 3 : 0
    score += consistencyScore

    return Math.min(Math.max(score, 0), 100)
  }

  /**
   * Calculate Wellness Score (0-100)
   */
  static calculateWellnessScore(
    healthLogs: HealthLog[],
    hasCircleInteractionToday: boolean = false,
    allTodaysFocusTasksCompleted: boolean = false
  ): number {
    let score = 0

    // Mood entry (0-30 points)
    if (healthLogs.some(log => log.type === 'mood')) {
      score += 30
    }

    // Meditation session (0-25 points)
    if (healthLogs.some(log => (log as any).type === 'meditation' || (log as any).meditation)) {
      score += 25
    }

    // Journal entry (0-20 points)
    if (healthLogs.some(log => (log as any).type === 'journal' || (log as any).journal)) {
      score += 20
    }

    // Breathing exercises (0-15 points)
    if (healthLogs.some(log => 
      (log as any).type === 'breathing' || 
      (log as any).mindfulSession ||
      (log.type === 'mood' && (log as any).emotions?.includes('breathing_exercise_completed'))
    )) {
      score += 15
    }

    // My Wins (0-10 points)
    if (healthLogs.some(log => (log as any).type === 'win' || (log as any).win)) {
      score += 10
    }

    // Circle interaction bonus (0-10 points)
    if (hasCircleInteractionToday) {
      score += 10
    }

    // All Today's Focus tasks completed bonus (0-15 points)
    if (allTodaysFocusTasksCompleted) {
      score += 15
    }

    return Math.min(Math.max(score, 0), 100)
  }

  /**
   * Calculate Habits Score (0-100)
   * Note: Requires habits and completions data - will be implemented when habits service is available
   */
  static calculateHabitsScore(
    totalHabits: number = 0,
    completedHabits: number = 0
  ): number {
    if (totalHabits === 0) {
      return 0
    }

    // Base score: percentage of habits completed
    const baseScore = Math.round((completedHabits / totalHabits) * 100)

    // Streak bonus: +5 points if all habits are completed
    const streakBonus = completedHabits === totalHabits && totalHabits > 0 ? 5 : 0

    return Math.min(Math.max(baseScore + streakBonus, 0), 100)
  }

  /**
   * Calculate all category scores and daily score
   */
  static calculateAllScores(
    meals: HealthLog[],
    workouts: HealthLog[],
    sleepLogs: HealthLog[],
    waterLogs: HealthLog[],
    allHealthLogs: HealthLog[],
    dailyLog: DailyLog | null,
    totalHabits: number = 0,
    completedHabits: number = 0,
    calorieGoal: number = 2000,
    stepsGoal: number = 10000,
    waterGoal: number = 2000,
    sleepGoal: number = 8.0,
    hasCircleInteractionToday: boolean = false,
    allTodaysFocusTasksCompleted: boolean = false
  ): CategoryScores {
    const healthScore = this.calculateHealthScore(
      meals,
      workouts,
      sleepLogs,
      waterLogs,
      dailyLog,
      allHealthLogs,
      calorieGoal,
      stepsGoal,
      waterGoal,
      sleepGoal
    )

    const wellnessScore = this.calculateWellnessScore(
      allHealthLogs,
      hasCircleInteractionToday,
      allTodaysFocusTasksCompleted
    )

    const habitsScore = this.calculateHabitsScore(totalHabits, completedHabits)

    return {
      healthScore,
      wellnessScore,
      habitsScore,
    }
  }
}

