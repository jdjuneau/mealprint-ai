package com.coachie.app.service

import android.util.Log
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.data.HabitRepository
import com.mealprint.ai.data.model.HealthLog
import com.mealprint.ai.data.model.DailyLog
import com.mealprint.ai.data.model.Habit
import com.mealprint.ai.data.model.HabitCompletion
import com.mealprint.ai.domain.MacroTargetsCalculator
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Service that automatically detects achievements and creates wins from daily activities
 * Analyzes yesterday's data to find accomplishments like:
 * - Personal records (most steps, most workouts, etc.)
 * - Goal achievements (hit macro goals, completed habits, etc.)
 * - Milestones (streak milestones, etc.)
 */
class AchievementWinService(
    private val repository: FirebaseRepository,
    private val habitRepository: HabitRepository
) {
    companion object {
        private const val TAG = "AchievementWinService"
        
        // Minimum thresholds for wins (to avoid too many wins)
        private const val MIN_STEPS_FOR_WIN = 5000 // Only count steps wins if > 5k steps
        private const val MIN_WATER_FOR_WIN = 1000 // Only count water wins if > 1L
    }

    /**
     * Analyze yesterday's data and generate wins
     * This should be called daily (e.g., via scheduled task or when app opens)
     */
    suspend fun analyzeAndCreateWins(userId: String, date: String = getYesterdayDate()): List<HealthLog.WinEntry> {
        val wins = mutableListOf<HealthLog.WinEntry>()
        
        try {
            // Get yesterday's data
            val dailyLogResult = repository.getDailyLog(userId, date)
            val dailyLog = dailyLogResult.getOrNull()
            
            val healthLogsResult = repository.getHealthLogs(userId, date)
            val healthLogs = healthLogsResult.getOrNull() ?: emptyList()
            
            val meals = healthLogs.filterIsInstance<HealthLog.MealLog>()
            val workouts = healthLogs.filterIsInstance<HealthLog.WorkoutLog>()
            val waterLogs = healthLogs.filterIsInstance<HealthLog.WaterLog>()
            val sleepLogs = healthLogs.filterIsInstance<HealthLog.SleepLog>()
            
            // Get user's goals/profile for comparison
            val profileResult = repository.getUserProfile(userId)
            val profile = profileResult.getOrNull()
            
            // Get habits and completions
            val habitsFlow = habitRepository.getHabits(userId)
            val habits = habitsFlow.first().filter { it.isActive }
            
            val completionsFlow = habitRepository.getRecentCompletions(userId, days = 7)
            val allCompletions = try {
                kotlinx.coroutines.withTimeout(5000) {
                    completionsFlow.first()
                }
            } catch (e: Exception) {
                emptyList()
            }
            
            val dayStart = java.util.Calendar.getInstance().apply {
                val dateObj = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
                time = java.util.Date.from(dateObj.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant())
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            val dayEnd = dayStart + (24 * 60 * 60 * 1000)
            
            val dayCompletions = allCompletions.filter {
                it.completedAt.time >= dayStart && it.completedAt.time < dayEnd
            }
            
            // Calculate totals
            val totalSteps = dailyLog?.steps ?: 0
            val totalWater = (dailyLog?.water ?: 0) + waterLogs.sumOf { it.ml }
            val totalWorkouts = workouts.size
            val totalCalories = meals.sumOf { it.calories }
            // CRITICAL: Sleep should NOT be summed - take the most recent valid sleep log
            // Multiple sleep logs can exist (e.g., naps + main sleep), but we should only count the primary one
            // For now, take the longest sleep log (most likely the main sleep session)
            // Filter out invalid sleep (>24 hours is impossible)
            val validSleepLogs = sleepLogs.filter { it.durationHours in 0.0..24.0 }
            val totalSleep = validSleepLogs.maxByOrNull { it.durationHours }?.durationHours ?: 0.0
            
            // Get historical data for personal records
            val historicalData = getHistoricalData(userId, date, 90) // Last 90 days
            
            // 1. Check for personal records (most steps ever, most workouts, etc.)
            wins.addAll(detectPersonalRecords(
                date = date,
                totalSteps = totalSteps,
                totalWater = totalWater,
                totalWorkouts = totalWorkouts,
                totalCalories = totalCalories,
                totalSleep = totalSleep,
                historicalData = historicalData
            ))
            
            // 2. Check for goal achievements
            wins.addAll(detectGoalAchievements(
                date = date,
                totalSteps = totalSteps,
                totalWater = totalWater,
                totalWorkouts = totalWorkouts,
                totalCalories = totalCalories,
                totalSleep = totalSleep,
                profile = profile
            ))
            
            // 3. Check for habit completions
            wins.addAll(detectHabitWins(
                date = date,
                habits = habits,
                completions = dayCompletions
            ))
            
            // 4. Check for macro achievements
            wins.addAll(detectMacroWins(
                date = date,
                meals = meals,
                profile = profile
            ))
            
            // 5. Check for streak milestones
            wins.addAll(detectStreakMilestones(
                date = date,
                habits = habits,
                completions = dayCompletions
            ))
            
            // Save wins to Firestore - use NonCancellable to prevent cancellation when composition ends
            wins.forEach { win ->
                try {
                    // Use NonCancellable context to ensure saves complete even if composition ends
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        repository.saveHealthLog(userId, date, win)
                    }
                    Log.d(TAG, "Saved achievement win: ${win.win}")
                } catch (e: Exception) {
                    // Only log non-cancellation exceptions (cancellation is expected if composition ends)
                    if (e !is kotlinx.coroutines.CancellationException) {
                        Log.e(TAG, "Failed to save win: ${win.win}", e)
                    }
                }
            }
            
            Log.d(TAG, "Generated ${wins.size} wins for $date")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing and creating wins", e)
        }
        
        return wins
    }
    
    /**
     * Detect personal records (most steps ever, most workouts, etc.)
     */
    private fun detectPersonalRecords(
        date: String,
        totalSteps: Int,
        totalWater: Int,
        totalWorkouts: Int,
        totalCalories: Int,
        totalSleep: Double,
        historicalData: HistoricalData
    ): List<HealthLog.WinEntry> {
        val wins = mutableListOf<HealthLog.WinEntry>()
        
        // Most steps ever
        if (totalSteps > historicalData.maxSteps && totalSteps >= MIN_STEPS_FOR_WIN) {
            wins.add(createWinEntry(
                date = date,
                win = "Personal record: ${formatSteps(totalSteps)} steps! Your best day yet!",
                tags = listOf("fitness", "steps", "personal-record")
            ))
        }
        
        // Most water ever
        if (totalWater > historicalData.maxWater && totalWater >= MIN_WATER_FOR_WIN) {
            val glasses = (totalWater / 240.0).toInt()
            wins.add(createWinEntry(
                date = date,
                win = "Personal record: $glasses glasses of water! Stay hydrated!",
                tags = listOf("health", "water", "personal-record")
            ))
        }
        
        // Most workouts in a day
        // CRITICAL: Only create personal record if there's actual historical data to compare against
        // For new accounts with no history, don't create "personal record" wins
        if (totalWorkouts > historicalData.maxWorkouts && totalWorkouts > 0 && historicalData.maxWorkouts >= 0) {
            // Only create win if this is actually a new record (not just first workout ever)
            // For new accounts, maxWorkouts will be 0, so we need at least 2 workouts to be a "record"
            if (totalWorkouts > 1 || historicalData.maxWorkouts > 0) {
                wins.add(createWinEntry(
                    date = date,
                    win = "Personal record: $totalWorkouts workout${if (totalWorkouts > 1) "s" else ""} in one day! You're on fire!",
                    tags = listOf("fitness", "workouts", "personal-record")
                ))
            }
        }
        
        // Most calories burned
        if (totalCalories > historicalData.maxCalories && totalCalories > 0) {
            wins.add(createWinEntry(
                date = date,
                win = "Personal record: ${totalCalories} calories burned! Amazing effort!",
                tags = listOf("fitness", "calories", "personal-record")
            ))
        }
        
        // Best sleep
        // CRITICAL: Validate sleep is reasonable (6-12 hours) and only create record if there's history
        if (totalSleep > historicalData.maxSleep && totalSleep >= 6.0 && totalSleep <= 12.0) {
            // Only create win if this is actually a new record (not just first sleep log ever)
            // For new accounts, maxSleep will be 0.0, so we need historical data to compare
            if (historicalData.maxSleep > 0.0) {
                wins.add(createWinEntry(
                    date = date,
                    win = "Personal record: ${String.format("%.1f", totalSleep)} hours of sleep! Great recovery!",
                    tags = listOf("health", "sleep", "personal-record")
                ))
            }
        }
        
        return wins
    }
    
    /**
     * Detect goal achievements (hit step goal, water goal, etc.)
     */
    private fun detectGoalAchievements(
        date: String,
        totalSteps: Int,
        totalWater: Int,
        totalWorkouts: Int,
        totalCalories: Int,
        totalSleep: Double,
        profile: com.coachie.app.data.model.UserProfile?
    ): List<HealthLog.WinEntry> {
        val wins = mutableListOf<HealthLog.WinEntry>()
        
        // Step goal (default 10k, or from profile)
        val stepGoal = 10000 // TODO: Get from profile/goals
        if (totalSteps >= stepGoal) {
            wins.add(createWinEntry(
                date = date,
                win = "Hit your step goal! ${formatSteps(totalSteps)} steps today!",
                tags = listOf("fitness", "steps", "goal")
            ))
        }
        
        // Water goal (default 2L, or from profile)
        val waterGoal = 2000 // TODO: Get from profile/goals
        if (totalWater >= waterGoal) {
            val glasses = (totalWater / 240.0).toInt()
            wins.add(createWinEntry(
                date = date,
                win = "Hit your water goal! $glasses glasses today!",
                tags = listOf("health", "water", "goal")
            ))
        }
        
        // Workout goal (at least 1 workout)
        if (totalWorkouts >= 1) {
            wins.add(createWinEntry(
                date = date,
                win = "Completed $totalWorkouts workout${if (totalWorkouts > 1) "s" else ""}! Great job staying active!",
                tags = listOf("fitness", "workouts", "goal")
            ))
        }
        
        // Sleep goal (7-9 hours)
        if (totalSleep >= 7.0 && totalSleep <= 9.0) {
            wins.add(createWinEntry(
                date = date,
                win = "Perfect sleep! ${String.format("%.1f", totalSleep)} hours of quality rest!",
                tags = listOf("health", "sleep", "goal")
            ))
        }
        
        return wins
    }
    
    /**
     * Detect habit completion wins
     */
    private fun detectHabitWins(
        date: String,
        habits: List<Habit>,
        completions: List<HabitCompletion>
    ): List<HealthLog.WinEntry> {
        val wins = mutableListOf<HealthLog.WinEntry>()
        
        // Check for completed habits
        val completedHabitIds = completions.map { it.habitId }.toSet()
        val completedHabits = habits.filter { it.id in completedHabitIds }
        
        if (completedHabits.isNotEmpty()) {
            val habitNames = completedHabits.map { it.title }.joinToString(", ")
            wins.add(createWinEntry(
                date = date,
                win = "Completed ${completedHabits.size} habit${if (completedHabits.size > 1) "s" else ""}: $habitNames!",
                tags = listOf("habits", "consistency")
            ))
        }
        
        // Check for streak milestones
        completedHabits.forEach { habit ->
            if (habit.streakCount > 0 && habit.streakCount % 7 == 0) {
                wins.add(createWinEntry(
                    date = date,
                    win = "${habit.title} streak: ${habit.streakCount} days! Keep it going!",
                    tags = listOf("habits", "streak", "milestone")
                ))
            }
        }
        
        return wins
    }
    
    /**
     * Detect macro achievement wins
     */
    private fun detectMacroWins(
        date: String,
        meals: List<HealthLog.MealLog>,
        profile: com.coachie.app.data.model.UserProfile?
    ): List<HealthLog.WinEntry> {
        val wins = mutableListOf<HealthLog.WinEntry>()
        
        if (meals.isEmpty()) return wins
        
        // Calculate total macros
        val totalProtein = meals.sumOf { it.protein }
        val totalCarbs = meals.sumOf { it.carbs }
        val totalFat = meals.sumOf { it.fat }
        val totalCalories = meals.sumOf { it.calories }
        
        // Get macro goals from profile using MacroTargetsCalculator (matches Health Tracking screen)
        val macroTargets = if (profile != null) {
            MacroTargetsCalculator.calculate(profile)
        } else {
            null
        }
        val proteinGoal = macroTargets?.proteinGrams?.toDouble() ?: 150.0
        val carbsGoal = macroTargets?.carbsGrams?.toDouble() ?: 200.0
        val fatGoal = macroTargets?.fatGrams?.toDouble() ?: 65.0
        
        // Check if all macros are within 10% of goals (good macro day)
        val proteinHit = totalProtein >= proteinGoal * 0.9 && totalProtein <= proteinGoal * 1.1
        val carbsHit = totalCarbs >= carbsGoal * 0.9 && totalCarbs <= carbsGoal * 1.1
        val fatHit = totalFat >= fatGoal * 0.9 && totalFat <= fatGoal * 1.1
        
        if (proteinHit && carbsHit && fatHit) {
            wins.add(createWinEntry(
                date = date,
                win = "Perfect macro day! Hit all your protein, carbs, and fat goals!",
                tags = listOf("nutrition", "macros", "goal")
            ))
        } else if (proteinHit) {
            wins.add(createWinEntry(
                date = date,
                win = "Hit your protein goal! ${totalProtein.toInt()}g of protein today!",
                tags = listOf("nutrition", "protein", "goal")
            ))
        }
        
        return wins
    }
    
    /**
     * Detect streak milestones
     */
    private fun detectStreakMilestones(
        date: String,
        habits: List<Habit>,
        completions: List<HabitCompletion>
    ): List<HealthLog.WinEntry> {
        val wins = mutableListOf<HealthLog.WinEntry>()
        
        habits.forEach { habit ->
            val streak = habit.streakCount
            
            // Milestone markers
            when {
                streak == 7 -> {
                    wins.add(createWinEntry(
                        date = date,
                        win = "${habit.title}: 7-day streak! One week strong!",
                        tags = listOf("habits", "streak", "milestone")
                    ))
                }
                streak == 30 -> {
                    wins.add(createWinEntry(
                        date = date,
                        win = "${habit.title}: 30-day streak! A full month of consistency!",
                        tags = listOf("habits", "streak", "milestone")
                    ))
                }
                streak == 100 -> {
                    wins.add(createWinEntry(
                        date = date,
                        win = "${habit.title}: 100-day streak! Incredible dedication!",
                        tags = listOf("habits", "streak", "milestone")
                    ))
                }
            }
        }
        
        return wins
    }
    
    /**
     * Get historical data for personal record comparisons
     */
    private suspend fun getHistoricalData(userId: String, currentDate: String, days: Int): HistoricalData {
        var maxSteps = 0
        var maxWater = 0
        var maxWorkouts = 0
        var maxCalories = 0
        var maxSleep = 0.0
        
        try {
            val endDate = LocalDate.parse(currentDate, DateTimeFormatter.ISO_LOCAL_DATE)
            val startDate = endDate.minusDays(days.toLong())
            
            for (i in 0 until days) {
                val date = startDate.plusDays(i.toLong())
                val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                
                // Skip current date (we're analyzing it)
                if (dateStr == currentDate) continue
                
                val dailyLogResult = repository.getDailyLog(userId, dateStr)
                val dailyLog = dailyLogResult.getOrNull()
                
                val healthLogsResult = repository.getHealthLogs(userId, dateStr)
                val healthLogs = healthLogsResult.getOrNull() ?: emptyList()
                
                val meals = healthLogs.filterIsInstance<HealthLog.MealLog>()
                val workouts = healthLogs.filterIsInstance<HealthLog.WorkoutLog>()
                val waterLogs = healthLogs.filterIsInstance<HealthLog.WaterLog>()
                val sleepLogs = healthLogs.filterIsInstance<HealthLog.SleepLog>()
                
                val steps = dailyLog?.steps ?: 0
                val water = (dailyLog?.water ?: 0) + waterLogs.sumOf { it.ml }
                val workoutCount = workouts.size
                val calories = meals.sumOf { it.calories }
                // CRITICAL: Sleep should NOT be summed - take the longest valid sleep log
                val validSleepLogs = sleepLogs.filter { it.durationHours in 0.0..24.0 }
                val sleep = validSleepLogs.maxByOrNull { it.durationHours }?.durationHours ?: 0.0
                
                maxSteps = maxOf(maxSteps, steps)
                maxWater = maxOf(maxWater, water)
                maxWorkouts = maxOf(maxWorkouts, workoutCount)
                maxCalories = maxOf(maxCalories, calories)
                maxSleep = maxOf(maxSleep, sleep)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting historical data", e)
        }
        
        return HistoricalData(
            maxSteps = maxSteps,
            maxWater = maxWater,
            maxWorkouts = maxWorkouts,
            maxCalories = maxCalories,
            maxSleep = maxSleep
        )
    }
    
    /**
     * Create a WinEntry from achievement data
     */
    private fun createWinEntry(
        date: String,
        win: String,
        tags: List<String> = emptyList()
    ): HealthLog.WinEntry {
        // Always include "achievement" tag to identify achievement-based wins
        val allTags = (tags + "achievement").distinct()
        return HealthLog.WinEntry(
            entryId = UUID.randomUUID().toString(),
            journalEntryId = "achievement_win", // Mark as achievement win
            date = date,
            win = win,
            gratitude = null,
            mood = null,
            moodScore = null,
            tags = allTags
        )
    }
    
    /**
     * Format steps for display
     */
    private fun formatSteps(steps: Int): String {
        return when {
            steps >= 10000 -> "${steps / 1000}k"
            else -> steps.toString()
        }
    }
    
    /**
     * Get yesterday's date string
     */
    private fun getYesterdayDate(): String {
        return LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
    
    /**
     * Historical data for personal record comparisons
     */
    private data class HistoricalData(
        val maxSteps: Int,
        val maxWater: Int,
        val maxWorkouts: Int,
        val maxCalories: Int,
        val maxSleep: Double
    )
}

