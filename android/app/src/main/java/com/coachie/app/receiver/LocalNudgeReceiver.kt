package com.coachie.app.receiver

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.coachie.app.MainActivity
import com.coachie.app.R
import com.coachie.app.data.local.PreferencesManager
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.HabitRepository
import com.coachie.app.data.model.DailyLog
import com.coachie.app.data.model.HealthLog
import com.coachie.app.data.model.Habit
import com.coachie.app.data.model.HabitCompletion
import com.coachie.app.domain.MacroTargetsCalculator
import com.coachie.app.domain.MacroTargets
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import java.util.Calendar

/**
 * BroadcastReceiver for local daily nudge notifications
 * Acts as a fallback when Firebase functions are not available
 */
class LocalNudgeReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "coachie_local_nudges"
        // CRITICAL: Use same notification ID as FcmService to consolidate notifications
        private const val NOTIFICATION_ID = 1001
        // CRITICAL: Use same cooldown as FcmService for shared cooldown (60 minutes)
        private const val NUDGE_COOLDOWN_MS = 60 * 60 * 1000L // 60 minutes cooldown (increased to prevent spam)
        // CRITICAL: Use same preference key as FcmService for shared cooldown tracking
        private const val PREF_LAST_NUDGE_TIME = "last_nudge_notification_time"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val currentTime = java.util.Calendar.getInstance()
        val currentHour = currentTime.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = currentTime.get(java.util.Calendar.MINUTE)

        android.util.Log.d("LocalNudgeReceiver", "=== NUDGE RECEIVED ===")
        android.util.Log.d("LocalNudgeReceiver", "Received broadcast intent: ${intent.action}")
        android.util.Log.d("LocalNudgeReceiver", "Current time: ${currentHour}:${String.format("%02d", currentMinute)}")

        val preferencesManager = PreferencesManager(context)

        // Check if nudges are enabled
        if (!preferencesManager.nudgesEnabled) {
            android.util.Log.d("LocalNudgeReceiver", "Nudges disabled, skipping notification")
            return
        }

        android.util.Log.d("LocalNudgeReceiver", "Nudges are enabled, proceeding...")

        // CRITICAL: Skip if this is a brief time (9 AM, 2 PM, 6 PM) - briefs are handled by Firebase functions
        // LocalNudgeReceiver should only act as a fallback if briefs fail
        // But allow meal nudges to go through (breakfast 8 AM, lunch 12:30 PM, dinner 6 PM)
        val isBriefTime = (currentHour == 9 && currentMinute < 5 && currentMinute >= 0) || 
                         (currentHour == 14 && currentMinute < 5) || 
                         (currentHour == 18 && currentMinute < 5)
        
        // Don't skip if it's a meal time (breakfast 8 AM, lunch 12:30 PM, dinner 6 PM)
        val isMealTime = (currentHour == 8 && currentMinute >= 0) ||
                         (currentHour == 12 && currentMinute >= 30) ||
                         (currentHour == 18 && currentMinute >= 0)
        
        if (isBriefTime && !isMealTime) {
            android.util.Log.d("LocalNudgeReceiver", "Skipping - this is a brief time (${currentHour}:${String.format("%02d", currentMinute)}). Briefs are handled by Firebase functions.")
            return
        }

        // Create notification channel if needed
        createNotificationChannel(context)

        // Determine time of day and meal time based on current hour
        val (timeOfDay, mealTime) = when {
            currentHour in 7..9 -> "morning" to "breakfast"
            currentHour in 11..13 -> "midday" to "lunch"
            currentHour in 17..19 -> "evening" to "dinner"
            currentHour in 6..17 -> "morning" to null
            else -> "evening" to null
        }

        android.util.Log.d("LocalNudgeReceiver", "Detected time of day: $timeOfDay, meal time: $mealTime (hour: $currentHour)")

        // Generate personalized nudge focused on habits and health logs
        val nudgeData = generatePersonalizedNudge(context, preferencesManager, timeOfDay, mealTime)
        
        if (nudgeData == null) {
            android.util.Log.d("LocalNudgeReceiver", "No nudge data generated - skipping notification")
            return
        }

        // Check cooldown before showing notification
        if (shouldShowNotification(context)) {
            // Show notification with deep link
            showNotificationWithDeepLink(
                context, 
                nudgeData.message, 
                nudgeData.deepLinkType,
                nudgeData.deepLinkData,
                timeOfDay
            )
            // Record the notification time
            recordNotificationTime(context)
        } else {
            android.util.Log.d("LocalNudgeReceiver", "Skipping notification - within cooldown period")
        }

        // Reschedule the next alarm for this time slot (since setRepeating may not be reliable)
        rescheduleNextAlarm(context, timeOfDay)
    }

    /**
     * Data class for nudge information with deep link
     */
    private data class NudgeData(
        val message: String,
        val deepLinkType: String, // "habit", "health_log", or "habits" (general)
        val deepLinkData: Map<String, String> = emptyMap() // e.g., {"habitId": "123"} or {"logType": "water"}
    )

    /**
     * Generate personalized nudge focused on user's habits and health logs
     */
    private fun generatePersonalizedNudge(
        context: Context,
        preferencesManager: PreferencesManager,
        timeOfDay: String,
        mealTime: String? = null
    ): NudgeData? {
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return null

        return runBlocking {
            try {
                val repository = FirebaseRepository.getInstance()
                val habitRepository = HabitRepository()
                
                // Get today's date
                val today = java.time.LocalDate.now()
                val todayString = today.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val todayEnd = todayStart + (24 * 60 * 60 * 1000)

                // Get active habits
                val allHabits = habitRepository.getHabits(userId).first()
                val activeHabits = allHabits.filter { it.isActive }
                
                // Get today's habit completions
                val allCompletions = habitRepository.getRecentCompletions(userId, days = 1).first()
                val todayCompletions = allCompletions.filter {
                    it.completedAt.time >= todayStart && it.completedAt.time < todayEnd
                }
                val completedHabitIds = todayCompletions.map { it.habitId }.toSet()
                
                // Get incomplete habits
                val incompleteHabits = activeHabits.filter { it.id !in completedHabitIds }
                
                // Get today's health logs
                val todayLog = repository.getDailyLog(userId, todayString).getOrNull()
                val todayHealthLogs = repository.getHealthLogs(userId, todayString).getOrNull() ?: emptyList()
                
                val meals = todayHealthLogs.filterIsInstance<HealthLog.MealLog>()
                val waterLogs = todayHealthLogs.filterIsInstance<HealthLog.WaterLog>()
                val totalWaterMl = (todayLog?.water ?: 0) + waterLogs.sumOf { it.ml }
                val sleepLogs = todayHealthLogs.filterIsInstance<HealthLog.SleepLog>()
                val workouts = todayHealthLogs.filterIsInstance<HealthLog.WorkoutLog>()
                
                // Generate positive, personalized nudge
                val positiveMessages = when {
                    mealTime == "breakfast" -> listOf(
                        "Good morning! 🌅",
                        "Rise and shine! ☀️",
                        "Morning motivation! 💪",
                        "Hey there! 🌟",
                        "Good morning! Let's make today great! ✨"
                    )
                    mealTime == "lunch" -> listOf(
                        "Lunchtime check-in! 🥗",
                        "Midday reminder! ☀️",
                        "Lunch break! 🍽️",
                        "Afternoon boost! 💪",
                        "How's your day going? 🌟"
                    )
                    mealTime == "dinner" -> listOf(
                        "Evening check-in! 🌙",
                        "Dinnertime reminder! 🍽️",
                        "Evening motivation! 💫",
                        "End of day! 🌟",
                        "Time to wind down! ✨"
                    )
                    timeOfDay == "morning" -> listOf(
                        "Good morning! 🌅",
                        "Rise and shine! ☀️",
                        "Morning motivation! 💪",
                        "Hey there! 🌟",
                        "Good morning! Let's make today great! ✨"
                    )
                    else -> listOf(
                        "Evening check-in! 🌙",
                        "End of day reminder! 📊",
                        "Evening motivation! 💫",
                        "Nighttime reminder! 🌟",
                        "Evening reflection time! 🎉"
                    )
                }
                
                val baseMessage = positiveMessages.random()
                val suggestions = mutableListOf<String>()
                var deepLinkType = "habits"
                var deepLinkData = mapOf<String, String>()
                
                // PRIORITIZE meal logging if it's meal time and meal hasn't been logged
                val hasBreakfast = meals.any { 
                    val mealHour = Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.HOUR_OF_DAY)
                    mealHour in 6..10
                }
                val hasLunch = meals.any { 
                    val mealHour = Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.HOUR_OF_DAY)
                    mealHour in 11..14
                }
                val hasDinner = meals.any { 
                    val mealHour = Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.HOUR_OF_DAY)
                    mealHour in 17..21
                }
                
                when (mealTime) {
                    "breakfast" -> {
                        if (!hasBreakfast) {
                            suggestions.add("🍳 Log your breakfast to start the day right!")
                            deepLinkType = "health_log"
                            deepLinkData = mapOf("logType" to "meal", "mealType" to "breakfast")
                        }
                    }
                    "lunch" -> {
                        if (!hasLunch) {
                            suggestions.add("🥗 Log your lunch to track nutrition!")
                            deepLinkType = "health_log"
                            deepLinkData = mapOf("logType" to "meal", "mealType" to "lunch")
                        }
                    }
                    "dinner" -> {
                        if (!hasDinner) {
                            suggestions.add("🍽️ Log your dinner to complete your day!")
                            deepLinkType = "health_log"
                            deepLinkData = mapOf("logType" to "meal", "mealType" to "dinner")
                        }
                    }
                }
                
                // If no meal reminder needed, check for incomplete habits
                if (suggestions.isEmpty() && incompleteHabits.isNotEmpty()) {
                    val habit = incompleteHabits.random()
                    val habitEmoji = when {
                        habit.title.lowercase().contains("water") || habit.title.lowercase().contains("hydrate") -> "💧"
                        habit.title.lowercase().contains("exercise") || habit.title.lowercase().contains("workout") -> "💪"
                        habit.title.lowercase().contains("sleep") -> "😴"
                        habit.title.lowercase().contains("meditation") || habit.title.lowercase().contains("mindful") -> "🧘"
                        habit.title.lowercase().contains("read") -> "📚"
                        habit.title.lowercase().contains("journal") -> "📝"
                        else -> "✅"
                    }
                    suggestions.add("$habitEmoji Complete: ${habit.title}")
                    if (deepLinkType == "habits") {
                        deepLinkType = "habit"
                        deepLinkData = mapOf("habitId" to habit.id)
                    }
                } else if (suggestions.isEmpty() && activeHabits.isNotEmpty()) {
                    // All habits done - celebrate!
                    suggestions.add("🎉 All habits completed today! Keep it up!")
                    deepLinkType = "habits"
                }
                
                // Check other health logs that need attention (only if we have room)
                if (suggestions.size < 2) {
                    when {
                        totalWaterMl < 1000 -> {
                            suggestions.add("💧 Stay hydrated - log your water intake")
                            if (deepLinkType == "habits") {
                                deepLinkType = "health_log"
                                deepLinkData = mapOf("logType" to "water")
                            }
                        }
                        sleepLogs.isEmpty() && timeOfDay == "evening" && mealTime != "dinner" -> {
                            suggestions.add("😴 Log your sleep when you're ready")
                            if (deepLinkType == "habits") {
                                deepLinkType = "health_log"
                                deepLinkData = mapOf("logType" to "sleep")
                            }
                        }
                        workouts.isEmpty() && timeOfDay != "evening" && mealTime == null -> {
                            suggestions.add("💪 Log your workout to track progress")
                            if (deepLinkType == "habits") {
                                deepLinkType = "health_log"
                                deepLinkData = mapOf("logType" to "workout")
                            }
                        }
                    }
                }
                
                // If no specific suggestions, provide general encouragement
                if (suggestions.isEmpty()) {
                    suggestions.add("🌟 You're doing great! Keep tracking your progress")
                    deepLinkType = "habits"
                }
                
                val message = buildString {
                    append(baseMessage)
                    append("\n\n")
                    suggestions.take(2).forEach { suggestion ->
                        append("• $suggestion\n")
                    }
                }.trim()
                
                NudgeData(message, deepLinkType, deepLinkData)
            } catch (e: Exception) {
                android.util.Log.e("LocalNudgeReceiver", "Error generating personalized nudge", e)
                // Fallback to simple nudge
                val fallbackMessage = generateTimedLocalNudge(timeOfDay)
                NudgeData(fallbackMessage, "habits", emptyMap())
            }
        }
    }

    private fun generateMorningBrief(context: Context, preferencesManager: PreferencesManager): String? {
        // CRITICAL SECURITY: Use authenticated user, not SharedPreferences
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return null

        return runBlocking {
            try {
                val repo = FirebaseRepository.getInstance()

                // Get user's unit preferences
                val goals = repo.getUserGoals(userId).getOrNull()
                val useImperial = goals?.get("useImperial") as? Boolean ?: true // Default to imperial

                // Get yesterday's data
                val yesterday = java.time.LocalDate.now().minusDays(1).toString()
                val yesterdayLog = repo.getDailyLog(userId, yesterday).getOrNull()
                val yesterdayHealthLogs = repo.getHealthLogs(userId, yesterday).getOrNull() ?: emptyList()

                // Get today's goal for context
                val profile = repo.getUserProfile(userId).getOrNull()
                val targets = profile?.let { MacroTargetsCalculator.calculate(it) }

                val insights = mutableListOf<String>()

                // Check ALL types of logs to determine if anything was logged
                val yesterdayMeals = yesterdayHealthLogs.filterIsInstance<HealthLog.MealLog>()
                val yesterdayWorkouts = yesterdayHealthLogs.filterIsInstance<HealthLog.WorkoutLog>()
                val yesterdaySleep = yesterdayHealthLogs.filterIsInstance<HealthLog.SleepLog>()
                val yesterdaySupplements = yesterdayHealthLogs.filterIsInstance<HealthLog.SupplementLog>()
                val yesterdayWeight = yesterdayHealthLogs.filterIsInstance<HealthLog.WeightLog>()
                val yesterdayJournal = yesterdayHealthLogs.filterIsInstance<HealthLog.JournalEntry>()
                val yesterdayMindfulness = yesterdayHealthLogs.filterIsInstance<HealthLog.MindfulSession>()
                
                val steps = yesterdayLog?.steps ?: 0
                val waterMl = yesterdayLog?.water ?: 0
                val hasAnyActivity = steps > 0 || 
                    yesterdayMeals.isNotEmpty() || 
                    yesterdayWorkouts.isNotEmpty() || 
                    yesterdaySleep.isNotEmpty() ||
                    yesterdaySupplements.isNotEmpty() ||
                    yesterdayWeight.isNotEmpty() ||
                    yesterdayJournal.isNotEmpty() ||
                    yesterdayMindfulness.isNotEmpty() ||
                    waterMl > 0

                // Only show "nothing logged" if truly nothing was logged
                if (!hasAnyActivity && yesterdayLog == null) {
                    insights.add("📝 Yesterday: No activity logged - let's start tracking today!")
                } else {
                    // Analyze yesterday's performance
                    if (yesterdayLog != null) {
                        // Steps analysis
                        val stepGoal = 10000
                        val stepProgress = steps.toDouble() / stepGoal
                        if (steps > 0) {
                            insights.add(when {
                                stepProgress >= 1.0 -> "🎯 Yesterday: Steps goal CRUSHED! (${steps} steps)"
                                stepProgress >= 0.8 -> "👟 Yesterday: Strong on steps (${steps}/${stepGoal})"
                                stepProgress >= 0.5 -> "🚶 Yesterday: Halfway to steps goal"
                                else -> "💪 Yesterday: Started moving (${steps} steps)"
                            })
                        }

                        // Calories analysis
                        if (targets != null) {
                            val calories = yesterdayLog.caloriesBurned ?: 0
                            if (calories > 0) {
                                val calorieProgress = calories.toDouble() / targets.calorieGoal
                                insights.add(when {
                                    calorieProgress >= 1.0 -> "🔥 Yesterday: Calorie goal exceeded!"
                                    calorieProgress >= 0.8 -> "💪 Yesterday: Strong calorie burn (${calories}/${targets.calorieGoal})"
                                    calorieProgress >= 0.5 -> "🏃 Yesterday: Halfway to calorie goal"
                                    else -> "⚡ Yesterday: Focus on activity today"
                                })
                            }
                        }
                    }

                    // Meal logging analysis
                    if (yesterdayMeals.isNotEmpty()) {
                        insights.add(when (yesterdayMeals.size) {
                            1 -> "🍽️ Yesterday: 1 meal logged - aim for better coverage today"
                            2 -> "🍽️ Yesterday: 2 meals logged - almost there, get that third meal!"
                            3 -> "🍽️ Yesterday: Perfect! All 3 meals logged 🎉"
                            else -> "🍽️ Yesterday: Great job logging ${yesterdayMeals.size} meals!"
                        })
                    }

                    // Workout analysis
                    if (yesterdayWorkouts.isNotEmpty()) {
                        val totalWorkoutMinutes = yesterdayWorkouts.sumOf { it.durationMin }
                        insights.add("💪 Yesterday: ${yesterdayWorkouts.size} workout${if (yesterdayWorkouts.size > 1) "s" else ""} logged (${totalWorkoutMinutes} min)")
                    }

                    // Sleep analysis
                    if (yesterdaySleep.isNotEmpty()) {
                        val totalSleepHours = yesterdaySleep.sumOf { it.durationHours }
                        insights.add("😴 Yesterday: ${String.format("%.1f", totalSleepHours)} hours of sleep logged")
                    }

                    // Water analysis
                    val yesterdayWaterLogs = yesterdayHealthLogs.filterIsInstance<HealthLog.WaterLog>()
                    val totalWaterMl = yesterdayWaterLogs.sumOf { it.ml } + (yesterdayLog?.water ?: 0)
                    if (totalWaterMl > 0) {
                        val (waterDisplay, unit) = if (useImperial) {
                            val totalWaterOz = (totalWaterMl * 0.033814).roundToInt()
                            Pair(totalWaterOz, "fl oz")
                        } else {
                            Pair(totalWaterMl, "ml")
                        }
                        insights.add(when {
                            totalWaterMl >= 2000 -> "💧 Yesterday: Hydration goal smashed! (${waterDisplay}${unit})"
                            totalWaterMl >= 1500 -> "💧 Yesterday: Good hydration (${waterDisplay}${unit})"
                            totalWaterMl >= 500 -> "💧 Yesterday: Started hydrating - keep it up! (${waterDisplay}${unit})"
                            else -> "💧 Yesterday: Low on water - prioritize hydration today!"
                        })
                    }

                    // Supplement analysis
                    if (yesterdaySupplements.isNotEmpty()) {
                        insights.add("💊 Yesterday: ${yesterdaySupplements.size} supplement${if (yesterdaySupplements.size > 1) "s" else ""} logged")
                    }

                    // Weight analysis
                    if (yesterdayWeight.isNotEmpty()) {
                        val latestWeight = yesterdayWeight.maxByOrNull { it.timestamp }
                        latestWeight?.let { weight ->
                            val weightDisplay = if (useImperial) {
                                "${String.format("%.1f", weight.weight)} lbs"
                            } else {
                                "${String.format("%.1f", weight.weight)} kg"
                            }
                            insights.add("⚖️ Yesterday: Weight logged ($weightDisplay)")
                        }
                    }

                    // Journal analysis
                    if (yesterdayJournal.isNotEmpty()) {
                        val completedEntries = yesterdayJournal.count { it.isCompleted }
                        insights.add("📝 Yesterday: ${completedEntries} journal entr${if (completedEntries == 1) "y" else "ies"} completed")
                    }

                    // Mindfulness analysis
                    if (yesterdayMindfulness.isNotEmpty()) {
                        val totalMinutes = yesterdayMindfulness.sumOf { (it.durationSeconds ?: 0) / 60 }
                        insights.add("🧘 Yesterday: ${yesterdayMindfulness.size} mindfulness session${if (yesterdayMindfulness.size > 1) "s" else ""} (${totalMinutes} min)")
                    }
                }

                // Generate focus areas for today (only if we have data to compare)
                val focusAreas = mutableListOf<String>()

                if (hasAnyActivity) {
                    if (steps < 5000) {
                        focusAreas.add("🚶‍♂️ Focus: More steps today")
                    }
                    if (targets != null && (yesterdayLog?.caloriesBurned ?: 0) < targets.calorieGoal * 0.7) {
                        focusAreas.add("🔥 Focus: Increase activity")
                    }
                    if (yesterdayMeals.size < 2) {
                        focusAreas.add("🍽️ Focus: Better meal tracking")
                    }
                    val totalWaterMl = (yesterdayHealthLogs.filterIsInstance<HealthLog.WaterLog>().sumOf { it.ml } + (yesterdayLog?.water ?: 0))
                    if (totalWaterMl < 1000) {
                        focusAreas.add("💧 Focus: Stay hydrated")
                    }
                }

                // Build the morning brief
                val brief = StringBuilder()
                brief.append("📊 Yesterday's Highlights:")
                insights.take(3).forEach { insight ->
                    brief.append("\n• $insight")
                }

                if (focusAreas.isNotEmpty()) {
                    brief.append("\n\n🎯 Today's Focus:")
                    focusAreas.take(2).forEach { focus ->
                        brief.append("\n• $focus")
                    }
                }

                brief.toString()
            } catch (e: Exception) {
                android.util.Log.w("LocalNudgeReceiver", "Unable to generate morning brief", e)
                null
            }
        }
    }

    private fun generateAfternoonBrief(context: Context, preferencesManager: PreferencesManager): String? {
        // CRITICAL SECURITY: Use authenticated user, not SharedPreferences
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return null

        return runBlocking {
            try {
                val repo = FirebaseRepository.getInstance()

                // Get user's unit preferences
                val goals = repo.getUserGoals(userId).getOrNull()
                val useImperial = goals?.get("useImperial") as? Boolean ?: true // Default to imperial

                // Get today's data
                val today = java.time.LocalDate.now().toString()
                val todayLog = repo.getDailyLog(userId, today).getOrNull()
                val todayHealthLogs = repo.getHealthLogs(userId, today).getOrNull() ?: emptyList()

                // Get today's goal for context
                val profile = repo.getUserProfile(userId).getOrNull()
                val targets = profile?.let { MacroTargetsCalculator.calculate(it) }

                val insights = mutableListOf<String>()

                // Today's progress analysis
                val todayMeals = todayHealthLogs.filterIsInstance<HealthLog.MealLog>()
                val todayWorkouts = todayHealthLogs.filterIsInstance<HealthLog.WorkoutLog>()
                val todaySleep = todayHealthLogs.filterIsInstance<HealthLog.SleepLog>()
                val todayWaterLogs = todayHealthLogs.filterIsInstance<HealthLog.WaterLog>()
                val totalWaterMl = todayWaterLogs.sumOf { it.ml } + (todayLog?.water ?: 0)

                val steps = todayLog?.steps ?: 0
                val calories = todayLog?.caloriesBurned ?: 0

                // Steps progress
                val stepGoal = 10000
                val stepProgress = if (stepGoal > 0) steps.toDouble() / stepGoal else 0.0
                if (steps > 0) {
                    insights.add(when {
                        stepProgress >= 1.0 -> "🎯 Steps goal CRUSHED! (${steps} steps)"
                        stepProgress >= 0.7 -> "👟 Strong on steps (${steps}/${stepGoal}) - keep it up!"
                        stepProgress >= 0.5 -> "🚶 Halfway to steps goal (${steps}/${stepGoal})"
                        stepProgress >= 0.3 -> "💪 Good start on steps (${steps}/${stepGoal})"
                        else -> "🏃 Started moving (${steps} steps) - let's build momentum!"
                    })
                } else {
                    insights.add("🚶‍♂️ Focus: Get those steps in this afternoon!")
                }

                // Calories progress
                if (targets != null && calories > 0) {
                    val calorieProgress = calories.toDouble() / targets.calorieGoal
                    insights.add(when {
                        calorieProgress >= 1.0 -> "🔥 Calorie goal exceeded! (${calories}/${targets.calorieGoal})"
                        calorieProgress >= 0.7 -> "💪 Strong calorie burn (${calories}/${targets.calorieGoal})"
                        calorieProgress >= 0.5 -> "🏃 Halfway to calorie goal"
                        else -> "⚡ Building calorie burn (${calories}/${targets.calorieGoal})"
                    })
                } else if (targets != null) {
                    insights.add("🔥 Focus: Increase activity to hit ${targets.calorieGoal} calories")
                }

                // Meal logging progress
                when {
                    todayMeals.size >= 3 -> insights.add("🍽️ Perfect! All 3 meals logged 🎉")
                    todayMeals.size == 2 -> insights.add("🍽️ 2 meals logged - one more to go!")
                    todayMeals.size == 1 -> insights.add("🍽️ 1 meal logged - aim for 2-3 today")
                    else -> insights.add("🍽️ Focus: Log your meals to track nutrition")
                }

                // Workout progress
                if (todayWorkouts.isNotEmpty()) {
                    val totalWorkoutMinutes = todayWorkouts.sumOf { it.durationMin }
                    insights.add("💪 ${todayWorkouts.size} workout${if (todayWorkouts.size > 1) "s" else ""} logged (${totalWorkoutMinutes} min)")
                } else {
                    insights.add("💪 Consider an afternoon workout to boost energy!")
                }

                // Water progress
                val (waterDisplay, unit) = if (useImperial) {
                    val totalWaterOz = (totalWaterMl * 0.033814).roundToInt()
                    Pair(totalWaterOz, "fl oz")
                } else {
                    Pair(totalWaterMl, "ml")
                }
                when {
                    totalWaterMl >= 2000 -> insights.add("💧 Hydration goal smashed! (${waterDisplay}${unit})")
                    totalWaterMl >= 1500 -> insights.add("💧 Good hydration (${waterDisplay}${unit}) - almost there!")
                    totalWaterMl >= 1000 -> insights.add("💧 Halfway to hydration goal (${waterDisplay}${unit})")
                    totalWaterMl >= 500 -> insights.add("💧 Started hydrating (${waterDisplay}${unit}) - keep it up!")
                    else -> insights.add("💧 Focus: Prioritize hydration this afternoon!")
                }

                // Build the afternoon brief - FOCUS ONLY ON TODAY
                val brief = StringBuilder()
                brief.append("📊 Today's Progress So Far:")
                
                // Today's current stats
                val todayStats = mutableListOf<String>()
                if (steps > 0) {
                    todayStats.add("🚶 $steps steps")
                }
                if (calories > 0) {
                    todayStats.add("🔥 $calories calories")
                }
                if (totalWaterMl > 0) {
                    val waterDisplay = if (useImperial) {
                        "${(totalWaterMl * 0.033814).roundToInt()} fl oz"
                    } else {
                        "${totalWaterMl}ml"
                    }
                    todayStats.add("💧 $waterDisplay water")
                }
                if (todayMeals.isNotEmpty()) {
                    todayStats.add("🍽️ ${todayMeals.size} meal${if (todayMeals.size > 1) "s" else ""} logged")
                }
                
                todayStats.take(4).forEach { stat ->
                    brief.append("\n• $stat")
                }

                // Add remaining goals for this afternoon
                val remainingGoals = mutableListOf<String>()
                if (steps < stepGoal * 0.7) {
                    val remaining = stepGoal - steps
                    remainingGoals.add("🚶 ${remaining} more steps to reach your goal")
                }
                if (targets != null && calories < targets.calorieGoal * 0.7) {
                    val remaining = targets.calorieGoal - calories
                    remainingGoals.add("🔥 ${remaining} more calories to reach your goal")
                }
                if (totalWaterMl < 1500) {
                    val remaining = 2000 - totalWaterMl
                    val remainingDisplay = if (useImperial) {
                        "${(remaining * 0.033814).roundToInt()} fl oz"
                    } else {
                        "${remaining} ml"
                    }
                    remainingGoals.add("💧 ${remainingDisplay} more water to reach your goal")
                }
                if (todayMeals.size < 2) {
                    remainingGoals.add("🍽️ Log ${2 - todayMeals.size} more meal${if (2 - todayMeals.size > 1) "s" else ""} today")
                }

                if (remainingGoals.isNotEmpty()) {
                    brief.append("\n\n🎯 This Afternoon:")
                    remainingGoals.take(2).forEach { goal ->
                        brief.append("\n• $goal")
                    }
                }

                brief.toString()
            } catch (e: Exception) {
                android.util.Log.w("LocalNudgeReceiver", "Unable to generate afternoon brief", e)
                null
            }
        }
    }

    private fun buildGoalSummary(context: Context, preferencesManager: PreferencesManager): String? {
        // CRITICAL SECURITY: Use authenticated user, not SharedPreferences
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return null
        return runBlocking {
            try {
                val repo = FirebaseRepository.getInstance()
                val profile = repo.getUserProfile(userId).getOrNull() ?: return@runBlocking null
                val targets = MacroTargetsCalculator.calculate(profile)

                // Get today's and yesterday's logs for performance analysis
                val today = java.time.LocalDate.now().toString()
                val yesterday = java.time.LocalDate.now().minusDays(1).toString()

                val todayLog = repo.getDailyLog(userId, today).getOrNull()
                val yesterdayLog = repo.getDailyLog(userId, yesterday).getOrNull()

                val goalSummary = "Goal today: ${targets.calorieGoal} kcal — Protein ${targets.proteinGrams}g · Carbs ${targets.carbsGrams}g · Fat ${targets.fatGrams}g"

                val performanceAnalysis = analyzePerformance(todayLog, yesterdayLog, targets)
                if (performanceAnalysis.isNotEmpty()) {
                    "$goalSummary\n$performanceAnalysis"
                } else {
                    goalSummary
                }
            } catch (e: Exception) {
                android.util.Log.w("LocalNudgeReceiver", "Unable to build goal summary", e)
                null
            }
        }
    }

    private fun analyzePerformance(todayLog: DailyLog?, yesterdayLog: DailyLog?, targets: MacroTargets): String {
        val insights = mutableListOf<String>()

        // Analyze steps
        if (todayLog?.steps != null) {
            val stepProgress = todayLog.steps.toDouble() / 10000.0
            val stepInsight = when {
                stepProgress >= 1.0 -> "🎯 Steps goal crushed! (${todayLog.steps})"
                stepProgress >= 0.8 -> "🚶 Good progress on steps (${todayLog.steps}/10k)"
                stepProgress >= 0.5 -> "👟 Halfway to steps goal (${todayLog.steps}/10k)"
                stepProgress >= 0.2 -> "🏃‍♂️ Started moving! (${todayLog.steps})"
                else -> "💪 Let's get those steps going!"
            }
            insights.add(stepInsight)

            // Compare with yesterday
            if (yesterdayLog?.steps != null) {
                val stepChange = todayLog.steps - yesterdayLog.steps
                if (stepChange > 500) {
                    insights.add("📈 ${stepChange} more steps than yesterday!")
                } else if (stepChange < -500) {
                    insights.add("📉 ${-stepChange} fewer steps than yesterday")
                }
            }
        }

        // Analyze calories burned
        if (todayLog?.caloriesBurned != null) {
            val calorieProgress = todayLog.caloriesBurned.toDouble() / targets.calorieGoal
            val calorieInsight = when {
                calorieProgress >= 1.0 -> "🔥 Calorie goal exceeded! (${todayLog.caloriesBurned})"
                calorieProgress >= 0.8 -> "💪 Strong calorie burn (${todayLog.caloriesBurned}/${targets.calorieGoal})"
                calorieProgress >= 0.5 -> "🏃‍♀️ Halfway to calorie goal"
                else -> "⚡ Keep building that burn!"
            }
            insights.add(calorieInsight)

            // Compare with yesterday
            if (yesterdayLog?.caloriesBurned != null) {
                val calorieChange = todayLog.caloriesBurned - yesterdayLog.caloriesBurned
                if (calorieChange > 200) {
                    insights.add("🔥 ${calorieChange} more calories burned than yesterday!")
                }
            }
        }

        // Analyze water intake
        if (todayLog?.water != null) {
            val waterLiters = todayLog.water / 1000.0
            val waterProgress = todayLog.water.toDouble() / 2000.0
            val waterInsight = when {
                waterProgress >= 1.0 -> "💧 Hydration goal met! (${waterLiters}L)"
                waterProgress >= 0.75 -> "🚰 Good hydration (${waterLiters}L/2L)"
                waterProgress >= 0.5 -> "💦 Halfway to hydration goal"
                waterProgress >= 0.25 -> "💧 Started hydrating (${waterLiters}L)"
                else -> "🚰 Time for some water!"
            }
            insights.add(waterInsight)

            // Compare with yesterday
            if (yesterdayLog?.water != null) {
                val waterChange = todayLog.water - yesterdayLog.water
                if (waterChange > 500) {
                    insights.add("💧 ${(waterChange/1000.0)}L more water than yesterday!")
                }
            }
        }

        // Analyze mood and energy
        if (todayLog?.mood != null) {
            val moodDescription = when (todayLog.mood) {
                5 -> "🌟 Feeling excellent today!"
                4 -> "😊 In a good mood"
                3 -> "😐 Feeling okay"
                2 -> "😔 Having a rough day"
                1 -> "😞 Struggling today"
                else -> null
            }
            moodDescription?.let { insights.add(it) }

            // Compare mood with yesterday
            if (yesterdayLog?.mood != null) {
                val moodChange = todayLog.mood - yesterdayLog.mood
                if (moodChange > 0) {
                    insights.add("📈 Mood improved from yesterday!")
                } else if (moodChange < 0) {
                    insights.add("🤗 Tomorrow will be better")
                }
            }
        }

        if (todayLog?.energy != null) {
            val energyDescription = when (todayLog.energy) {
                5 -> "⚡ Feeling very energetic!"
                4 -> "🔋 Good energy levels"
                3 -> "🪫 Moderate energy"
                2 -> "😴 Feeling tired"
                1 -> "🛌 Very low energy"
                else -> null
            }
            energyDescription?.let { insights.add(it) }
        }

        // Overall performance summary
        val dataPoints = listOf(todayLog?.steps, todayLog?.caloriesBurned, todayLog?.water, todayLog?.mood, todayLog?.energy).count { it != null }

        if (dataPoints >= 3) {
            insights.add("📊 Great tracking consistency!")
        } else if (dataPoints == 0) {
            insights.add("📝 Ready to start tracking today?")
        }

        return insights.take(3).joinToString(" · ") // Limit to 3 insights for notification length
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Daily Nudges",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Local daily motivation reminders"
                setShowBadge(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun generateTimedLocalNudge(timeOfDay: String): String {
        val nudges = when (timeOfDay) {
            "morning" -> arrayOf(
                "Good morning! 🌅 Ready to crush your fitness goals today?",
                "Rise and shine! 💪 Every healthy choice builds momentum.",
                "Morning motivation! ☀️ Start strong and stay consistent!",
                "Hey there! 🌟 Today is another opportunity to get closer to your goals!",
                "Good morning! 💪 Time to fuel up and get moving!"
            )
            "midday" -> arrayOf(
                "Afternoon check-in! 📝 How are your habits looking today?",
                "Midday motivation! 💪 Keep that momentum going!",
                "How's your day going? 🚰 Don't forget to stay hydrated!",
                "Lunchtime reminder! 🥗 Log your meals and stay on track!",
                "Afternoon boost! 🔥 You're doing great - keep it up!"
            )
            "evening" -> arrayOf(
                "Evening review! 🌙 How did your healthy habits go today?",
                "End of day check-in! 📊 Ready to log your progress?",
                "Evening motivation! 💫 Celebrate your wins and plan tomorrow!",
                "Nighttime reminder! 🌟 Wind down and prepare for success!",
                "Evening reflection! 🎉 What healthy choices did you make today?"
            )
            else -> arrayOf(
                "Time for a healthy habit check-in! 💪",
                "Keep up the great work! 🌟",
                "Consistency is key! 🔥"
            )
        }

        return nudges.random()
    }

    /**
     * Show notification with deep link support
     */
    private fun showNotificationWithDeepLink(
        context: Context,
        message: String,
        deepLinkType: String,
        deepLinkData: Map<String, String>,
        timeOfDay: String = "general"
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            
            // Determine navigation target based on deep link type
            val navigateTo = when (deepLinkType) {
                "habit" -> {
                    // Navigate to specific habit
                    val habitId = deepLinkData["habitId"]
                    if (habitId != null) {
                        putExtra("habitId", habitId)
                    }
                    "habits"
                }
                "health_log" -> {
                    // Navigate to specific health log type
                    val logType = deepLinkData["logType"]
                    when (logType) {
                        "meal" -> "meal_log"
                        "water" -> "water_log"
                        "sleep" -> "sleep_log"
                        "workout" -> "workout_log"
                        else -> "health_tracking"
                    }
                }
                "habits" -> "habits"
                else -> "habits" // Default to habits
            }
            
            // CRITICAL: Always navigate to notification_detail first to show the message
            // Store the deep link target separately so NotificationDetailScreen can show actionable link
            putExtra("navigate_to", "notification_detail")
            putExtra("notification_title", "Coachie")
            putExtra("notification_message", message)
            if (navigateTo != "notification_detail") {
                putExtra("deep_link_target", navigateTo)
            }
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(), // Use unique request code to ensure intent updates
            intent,
            pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_coachie_notification)
            .setContentTitle("Coachie")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setSummaryText("Coachie Daily Nudge")
                    .bigText(message)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)

        android.util.Log.i("LocalNudgeReceiver", "Local nudge notification sent: $message")
    }

    /**
     * Check if enough time has passed since the last notification
     * Returns true if we should show the notification, false if we're in cooldown
     */
    private fun shouldShowNotification(context: Context): Boolean {
        // CRITICAL: Use same SharedPreferences file as FcmService for shared cooldown
        val prefs = context.getSharedPreferences("coachie_nudges", Context.MODE_PRIVATE)
        val lastNotificationTime = prefs.getLong(PREF_LAST_NUDGE_TIME, 0L)
        val currentTime = System.currentTimeMillis()
        val timeSinceLastNotification = currentTime - lastNotificationTime
        
        android.util.Log.d("LocalNudgeReceiver", "Cooldown check: lastNotificationTime=$lastNotificationTime, currentTime=$currentTime, timeSince=$timeSinceLastNotification ms (${timeSinceLastNotification / 60000} minutes), cooldown=$NUDGE_COOLDOWN_MS ms (${NUDGE_COOLDOWN_MS / 60000} minutes)")
        
        val shouldShow = timeSinceLastNotification >= NUDGE_COOLDOWN_MS
        if (!shouldShow) {
            val minutesRemaining = (NUDGE_COOLDOWN_MS - timeSinceLastNotification) / 60000
            android.util.Log.d("LocalNudgeReceiver", "Notification blocked - $minutesRemaining minutes remaining in cooldown")
        }
        
        return shouldShow
    }
    
    /**
     * Record the current time as the last notification time
     */
    private fun recordNotificationTime(context: Context) {
        // CRITICAL: Use same SharedPreferences file as FcmService for shared cooldown
        val prefs = context.getSharedPreferences("coachie_nudges", Context.MODE_PRIVATE)
        val currentTime = System.currentTimeMillis()
        prefs.edit().putLong(PREF_LAST_NUDGE_TIME, currentTime).apply()
        android.util.Log.d("LocalNudgeReceiver", "Recorded notification time: $currentTime")
    }

    private fun rescheduleNextAlarm(context: Context, timeOfDay: String) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // Determine the next time for this time slot
            val (hour, requestCode) = when (timeOfDay) {
                "morning" -> 8 to 1001  // Breakfast
                "midday" -> 12 to 1002  // Lunch (will be 12:30, but hour is 12)
                "evening" -> 18 to 1003 // Dinner
                else -> return // Don't reschedule unknown times
            }

            val intent = Intent(context, LocalNudgeReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )

            val calendar = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                // Set minute based on time slot (lunch is 12:30, others are on the hour)
                set(java.util.Calendar.MINUTE, if (hour == 12) 30 else 0)
                set(java.util.Calendar.SECOND, 0)

                // If it's already past the scheduled time today, schedule for tomorrow
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
            }

            // Use setRepeating for consistent daily scheduling (like the boot receiver)
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )

            android.util.Log.d("LocalNudgeReceiver", "Rescheduled $timeOfDay alarm for ${calendar.time} (repeating daily)")
        } catch (e: Exception) {
            android.util.Log.e("LocalNudgeReceiver", "Failed to reschedule alarm", e)
        }
    }
}
