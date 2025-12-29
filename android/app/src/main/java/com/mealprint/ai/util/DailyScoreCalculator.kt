package com.coachie.app.util

import com.mealprint.ai.data.model.HealthLog
import com.mealprint.ai.data.model.DailyLog
import com.mealprint.ai.data.model.Habit
import com.mealprint.ai.data.model.HabitCompletion
import java.util.Calendar

/**
 * Data class to hold individual category scores
 */
data class CategoryScores(
    val healthScore: Int,
    val wellnessScore: Int,
    val habitsScore: Int
) {
    /**
     * Calculate daily score with health-focused weighting:
     * - Health Tracking: 50%
     * - Wellness: 30%
     * - Habits: 20%
     */
    fun calculateDailyScore(): Int {
        return ((healthScore * 0.50) + (wellnessScore * 0.30) + (habitsScore * 0.20)).toInt()
    }
}

/**
 * Utility class for calculating daily scores across different categories
 */
object DailyScoreCalculator {
    
    /**
     * Calculate Health Tracking Score (0-100)
     * Based on:
     * - Calories: progress toward goal (0-25 points)
     * - Water: progress toward goal (0-20 points)
     * - Steps: progress toward goal (0-15 points) - Lower weight since Google Fit doesn't track in real time
     * - Sleep: quality/hours logged (0-15 points)
     * - Weight: logged today (0-10 points)
     * - Workouts: any logged today (0-10 points)
     * - Consistency: logging multiple health metrics (0-5 points)
     */
    fun calculateHealthScore(
        meals: List<HealthLog.MealLog>,
        workouts: List<HealthLog.WorkoutLog>,
        sleepLogs: List<HealthLog.SleepLog>,
        waterLogs: List<HealthLog.WaterLog>,
        dailyLog: DailyLog?,
        allHealthLogs: List<HealthLog> = emptyList(),
        calorieGoal: Int = 2000,
        stepsGoal: Int = 10000,
        waterGoal: Int = 2000, // ml
        sleepGoal: Double = 8.0 // hours
    ): Int {
        var score = 0
        
        // Calories (0-25 points) - More generous scoring: 50% of goal = 15 points, 75% = 20 points, 100% = 25 points
        val caloriesConsumed = meals.sumOf { it.calories }
        val calorieProgress = (caloriesConsumed.toFloat() / calorieGoal).coerceIn(0f, 1f)
        // Use square root to make partial progress more rewarding
        val calorieScore = (kotlin.math.sqrt(calorieProgress) * 25).toInt()
        score += calorieScore
        
        // Water (0-20 points) - More generous scoring
        // CRITICAL FIX: Use DailyLog.water as source of truth (it already includes voice logs)
        // Only add WaterLog entries if DailyLog.water is not set (matches HealthTrackingDashboardScreen logic)
        val waterMl = if (dailyLog?.water != null && dailyLog.water!! > 0) {
            dailyLog.water!!
        } else {
            waterLogs.sumOf { it.ml }
        }
        val waterProgress = (waterMl.toFloat() / waterGoal).coerceIn(0f, 1f)
        val waterScore = (kotlin.math.sqrt(waterProgress) * 20).toInt()
        score += waterScore
        
        // Steps (0-15 points) - Lower weight since Google Fit doesn't track in real time
        val steps = dailyLog?.steps ?: 0
        val stepsProgress = (steps.toFloat() / stepsGoal).coerceIn(0f, 1f)
        val stepsScore = (kotlin.math.sqrt(stepsProgress) * 15).toInt()
        score += stepsScore
        
        // Sleep (0-15 points) - More generous scoring
        val sleepHours = sleepLogs.maxByOrNull { it.timestamp }?.durationHours ?: 0.0
        val sleepProgress = (sleepHours / sleepGoal).coerceIn(0.0, 1.0)
        val sleepScore = (kotlin.math.sqrt(sleepProgress) * 15).toInt()
        score += sleepScore
        
        // Weight (0-10 points) - Direct points for logging weight
        val weightLogs = allHealthLogs.filterIsInstance<HealthLog.WeightLog>()
        if (weightLogs.isNotEmpty()) {
            score += 10
        }
        
        // Workouts (0-10 points) - Points for logging workouts
        if (workouts.isNotEmpty()) {
            val totalWorkoutMinutes = workouts.sumOf { it.durationMin }
            // 1 workout = 8 points, 2+ workouts = 10 points, bonus for longer workouts
            val workoutCount = workouts.size
            val baseWorkoutScore = if (workoutCount >= 2) 10 else 8
            // Bonus for longer total duration (up to 2 extra points)
            val durationBonus = (totalWorkoutMinutes / 45.0).coerceIn(0.0, 2.0).toInt()
            score += baseWorkoutScore + durationBonus
        }
        
        // Consistency (0-5 points) - Bonus for logging multiple metrics
        val loggedMetrics = listOf(
            meals.isNotEmpty(),
            workouts.isNotEmpty(),
            sleepLogs.isNotEmpty(),
            waterLogs.isNotEmpty(),
            dailyLog?.steps != null && (dailyLog.steps ?: 0) > 0,
            weightLogs.isNotEmpty()
        ).count { it }
        // 2-3 metrics = 3 points, 4+ metrics = 5 points
        val consistencyScore = when {
            loggedMetrics >= 4 -> 5
            loggedMetrics >= 2 -> 3
            else -> 0
        }
        score += consistencyScore
        
        return score.coerceIn(0, 100)
    }
    
    /**
     * Calculate Wellness Score (0-100)
     * Based on:
     * - Mood entry logged (0-30 points)
     * - Meditation session completed (0-25 points)
     * - Journal entry written (0-20 points)
     * - Breathing exercises completed (0-15 points)
     * - My Wins logged (0-10 points)
     * - Circle interaction bonus (0-10 points) - Daily bonus for interacting with circles
     * - All Today's Focus tasks completed (0-15 points) - Bonus for completing all daily tasks
     */
    fun calculateWellnessScore(
        healthLogs: List<HealthLog>,
        hasCircleInteractionToday: Boolean = false,
        allTodaysFocusTasksCompleted: Boolean = false
    ): Int {
        var score = 0
        
        // Mood entry (0-30 points)
        val hasMoodEntry = healthLogs.any { it is HealthLog.MoodLog }
        if (hasMoodEntry) {
            score += 30
        }
        
        // Meditation session (0-25 points)
        val hasMeditation = healthLogs.any { it is HealthLog.MeditationLog }
        if (hasMeditation) {
            score += 25
        }
        
        // Journal entry (0-20 points)
        val hasJournal = healthLogs.any { it is HealthLog.JournalEntry }
        if (hasJournal) {
            score += 20
        }
        
        // Breathing exercises (0-15 points) - check for mindfulness sessions or breathing exercises
        val hasBreathing = healthLogs.any { 
            it is HealthLog.MindfulSession || 
            (it is HealthLog.MoodLog && it.emotions.contains("breathing_exercise_completed"))
        }
        if (hasBreathing) {
            score += 15
        }
        
        // My Wins (0-10 points) - check for win entries
        val hasWins = healthLogs.any { it is HealthLog.WinEntry }
        if (hasWins) {
            score += 10
        }
        
        // Circle interaction bonus (0-10 points) - Daily bonus for any circle interaction
        // This includes: posting, liking, commenting, or checking in to a circle
        if (hasCircleInteractionToday) {
            score += 10
        }
        
        // All Today's Focus tasks completed bonus (0-15 points)
        // This is a significant bonus for completing all daily focus tasks
        if (allTodaysFocusTasksCompleted) {
            score += 15
        }
        
        return score.coerceIn(0, 100)
    }
    
    /**
     * Calculate Habits Score (0-100)
     * Based on:
     * - Habits completed today: (completed / total) × 100
     * - Streak maintenance: bonus points for maintaining streaks
     */
    fun calculateHabitsScore(
        habits: List<Habit>,
        completions: List<HabitCompletion>
    ): Int {
        val totalHabits = habits.size
        if (totalHabits == 0) {
            return 0
        }
        
        // Get today's date range
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val todayStart = calendar.time
        
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val tomorrowStart = calendar.time
        
        // Filter completions to today only
        val todayCompletions = completions.filter {
            it.completedAt.time >= todayStart.time && it.completedAt.time < tomorrowStart.time
        }
        
        val completedHabitIds = todayCompletions.map { it.habitId }.distinct()
        val completedCount = completedHabitIds.size
        
        // Base score: percentage of habits completed
        val baseScore = (completedCount.toFloat() / totalHabits * 100).toInt()
        
        // Streak bonus: +5 points if all habits are completed
        val streakBonus = if (completedCount == totalHabits && totalHabits > 0) 5 else 0
        
        return (baseScore + streakBonus).coerceIn(0, 100)
    }
    
    /**
     * Calculate all category scores and daily score
     * @param allTodaysFocusTasksCompleted If true, adds bonus points to wellness score
     */
    fun calculateAllScores(
        meals: List<HealthLog.MealLog>,
        workouts: List<HealthLog.WorkoutLog>,
        sleepLogs: List<HealthLog.SleepLog>,
        waterLogs: List<HealthLog.WaterLog>,
        allHealthLogs: List<HealthLog>,
        dailyLog: DailyLog?,
        habits: List<Habit>,
        habitCompletions: List<HabitCompletion>,
        calorieGoal: Int = 2000,
        stepsGoal: Int = 10000,
        waterGoal: Int = 2000,
        sleepGoal: Double = 8.0,
        hasCircleInteractionToday: Boolean = false,
        allTodaysFocusTasksCompleted: Boolean = false
    ): CategoryScores {
        val healthScore = calculateHealthScore(
            meals = meals,
            workouts = workouts,
            sleepLogs = sleepLogs,
            waterLogs = waterLogs,
            dailyLog = dailyLog,
            allHealthLogs = allHealthLogs,
            calorieGoal = calorieGoal,
            stepsGoal = stepsGoal,
            waterGoal = waterGoal,
            sleepGoal = sleepGoal
        )
        
        val wellnessScore = calculateWellnessScore(
            healthLogs = allHealthLogs,
            hasCircleInteractionToday = hasCircleInteractionToday,
            allTodaysFocusTasksCompleted = allTodaysFocusTasksCompleted
        )
        
        val habitsScore = calculateHabitsScore(
            habits = habits,
            completions = habitCompletions
        )
        
        return CategoryScores(
            healthScore = healthScore,
            wellnessScore = wellnessScore,
            habitsScore = habitsScore
        )
    }
}

