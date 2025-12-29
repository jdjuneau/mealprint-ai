package com.coachie.app.service

import android.util.Log
import com.coachie.app.data.HabitRepository
import com.coachie.app.data.model.Habit
import com.coachie.app.data.model.HabitCategory
import com.coachie.app.data.model.HabitCompletion
import com.coachie.app.data.model.HealthLog
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * Service to automatically complete habits when related activities are logged.
 * This eliminates the need for users to manually mark habits as complete when they log activities.
 */
object HabitAutoCompletionService {
    
    private const val TAG = "HabitAutoCompletion"
    private val habitRepository = HabitRepository.getInstance()
    
    /**
     * Called when a meal is logged - checks and auto-completes related nutrition habits
     */
    suspend fun onMealLogged(userId: String) {
        try {
            Log.d(TAG, "Meal logged for user: $userId - checking for nutrition habits")
            val habits = habitRepository.getHabits(userId).first()
            val nutritionHabits = habits.filter { 
                it.isActive && 
                (it.category == HabitCategory.NUTRITION || 
                 it.title.lowercase().contains("protein") ||
                 it.title.lowercase().contains("meal") ||
                 it.title.lowercase().contains("eat"))
            }
            
            for (habit in nutritionHabits) {
                val titleLower = habit.title.lowercase()
                
                // Check if this is a per-meal habit (e.g., "eat protein with every meal")
                // These should complete for each meal logged
                if (titleLower.contains("protein") && titleLower.contains("meal")) {
                    // Per-meal habit - complete it (don't check if already completed today)
                    autoCompleteHabit(userId, habit, 1, "Auto-completed: meal logged")
                } else if (titleLower.contains("every meal") || titleLower.contains("each meal")) {
                    // Per-meal habit - complete it
                    autoCompleteHabit(userId, habit, 1, "Auto-completed: meal logged")
                } else if (habit.category == HabitCategory.NUTRITION && habit.targetValue == 1) {
                    // Simple nutrition habit - only complete once per day
                    if (shouldAutoComplete(habit, userId)) {
                        autoCompleteHabit(userId, habit, 1, "Auto-completed: meal logged")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error auto-completing habits for meal log", e)
        }
    }
    
    /**
     * Called when water is logged - checks and auto-completes water-related habits
     */
    suspend fun onWaterLogged(userId: String, amountMl: Int) {
        try {
            Log.i(TAG, "💧💧💧 WATER LOGGED: ${amountMl}ml for user: $userId - CHECKING FOR WATER HABITS 💧💧💧")
            val habits = habitRepository.getHabits(userId).first()
            Log.d(TAG, "Total habits found: ${habits.size}, active: ${habits.count { it.isActive }}")
            
            val waterHabits = habits.filter { 
                it.isActive && 
                (it.category == HabitCategory.HEALTH ||
                 it.title.lowercase().contains("water") ||
                 it.title.lowercase().contains("hydrate") ||
                 it.title.lowercase().contains("drink"))
            }
            
            Log.i(TAG, "Found ${waterHabits.size} water-related habits")
            if (waterHabits.isEmpty()) {
                Log.w(TAG, "⚠️⚠️⚠️ NO WATER HABITS FOUND! Check habit titles and categories ⚠️⚠️⚠️")
                habits.forEach { habit ->
                    Log.d(TAG, "  Habit: '${habit.title}' (active=${habit.isActive}, category=${habit.category})")
                }
            }
            
            for (habit in waterHabits) {
                val titleLower = habit.title.lowercase()
                Log.i(TAG, "🔍 Checking water habit: '${habit.title}' (id: ${habit.id})")
                Log.i(TAG, "   Target: ${habit.targetValue} ${habit.unit}")
                
                // Check if this is a water-related habit
                if (titleLower.contains("water") || titleLower.contains("hydrate") || titleLower.contains("drink")) {
                    val totalMlToday = getTotalWaterToday(userId)
                    val totalGlassesToday = totalMlToday / 240.0
                    
                    Log.i(TAG, "   💧 Total water today: ${totalMlToday}ml (${totalGlassesToday.toInt()} glasses)")
                    Log.i(TAG, "   🎯 Habit target: ${habit.targetValue} ${habit.unit}")
                    
                    // Check if habit target is met (e.g., "drink 8 glasses of water")
                    val targetMet = if (habit.unit.lowercase().contains("glass") || habit.unit.lowercase().contains("cup")) {
                        val met = totalGlassesToday >= habit.targetValue
                        Log.i(TAG, "   ✅ Target check (glasses): ${totalGlassesToday.toInt()} >= ${habit.targetValue} = $met")
                        met
                    } else if (habit.unit.lowercase().contains("ml") || habit.unit.lowercase().contains("liter")) {
                        val met = totalMlToday >= habit.targetValue
                        Log.i(TAG, "   ✅ Target check (ml): ${totalMlToday} >= ${habit.targetValue} = $met")
                        met
                    } else {
                        // Default: assume glasses if no unit specified
                        val met = totalGlassesToday >= habit.targetValue
                        Log.i(TAG, "   ✅ Target check (default glasses): ${totalGlassesToday.toInt()} >= ${habit.targetValue} = $met")
                        met
                    }
                    
                    if (targetMet) {
                        val canAutoComplete = shouldAutoComplete(habit, userId)
                        Log.i(TAG, "   🎯 Target MET! Can auto-complete: $canAutoComplete")
                        
                        if (canAutoComplete) {
                            Log.i(TAG, "   ✅✅✅ AUTO-COMPLETING HABIT NOW ✅✅✅")
                            if (habit.unit.lowercase().contains("glass") || habit.unit.lowercase().contains("cup")) {
                                autoCompleteHabit(userId, habit, habit.targetValue, "Auto-completed: ${totalGlassesToday.toInt()} glasses logged")
                            } else {
                                autoCompleteHabit(userId, habit, habit.targetValue, "Auto-completed: ${totalMlToday}ml logged")
                            }
                        } else {
                            Log.w(TAG, "   ⚠️ Target met but habit already completed today - skipping")
                        }
                    } else {
                        Log.d(TAG, "   ⏳ Target not met yet (need ${habit.targetValue} ${habit.unit}, have ${if (habit.unit.lowercase().contains("glass")) totalGlassesToday.toInt() else totalMlToday})")
                    }
                } else {
                    Log.d(TAG, "   ⚠️ Habit doesn't contain 'water', 'hydrate', or 'drink' in title - skipping")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ ERROR AUTO-COMPLETING HABITS FOR WATER LOG ❌❌❌", e)
            e.printStackTrace()
        }
    }
    
    /**
     * Called when sleep is logged - checks and auto-completes sleep-related habits
     */
    suspend fun onSleepLogged(userId: String, hours: Double) {
        try {
            Log.d(TAG, "Sleep logged: ${hours}h for user: $userId - checking for sleep habits")
            val habits = habitRepository.getHabits(userId).first()
            val sleepHabits = habits.filter { 
                it.isActive && 
                (it.category == HabitCategory.SLEEP ||
                 it.title.lowercase().contains("sleep") ||
                 it.title.lowercase().contains("bed"))
            }
            
            for (habit in sleepHabits) {
                // Skip avoidance habits (e.g., "No social media before bed")
                if (com.coachie.app.util.HabitUtils.isAvoidanceHabit(habit)) {
                    Log.d(TAG, "Skipping avoidance habit: ${habit.title}")
                    continue
                }
                
                if (shouldAutoComplete(habit, userId)) {
                    val titleLower = habit.title.lowercase()
                    
                    // Check if this is a sleep duration habit (e.g., "8 hours of sleep")
                    if (titleLower.contains("sleep") && habit.unit.lowercase().contains("hour")) {
                        if (hours >= habit.targetValue) {
                            autoCompleteHabit(userId, habit, hours.toInt(), "Auto-completed: ${hours}h sleep logged")
                        }
                    } else if (titleLower.contains("bed") || titleLower.contains("sleep")) {
                        // General sleep habit - complete if sleep was logged
                        autoCompleteHabit(userId, habit, 1, "Auto-completed: sleep logged")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error auto-completing habits for sleep log", e)
        }
    }
    
    /**
     * Called when a workout is logged - checks and auto-completes workout-related habits
     */
    suspend fun onWorkoutLogged(userId: String, durationMinutes: Int? = null, calories: Int? = null) {
        try {
            Log.d(TAG, "💪💪💪 WORKOUT LOGGED: duration=$durationMinutes min, calories=$calories for user: $userId - checking for workout habits 💪💪💪")
            val habits = habitRepository.getHabits(userId).first()
            val workoutHabits = habits.filter { 
                it.isActive && 
                (it.category == HabitCategory.FITNESS ||
                 it.title.lowercase().contains("workout") ||
                 it.title.lowercase().contains("exercise") ||
                 it.title.lowercase().contains("gym") ||
                 it.title.lowercase().contains("run") ||
                 it.title.lowercase().contains("walk"))
            }
            
            if (workoutHabits.isEmpty()) {
                Log.d(TAG, "No workout-related habits found for user: $userId")
                return
            } else {
                Log.d(TAG, "Found ${workoutHabits.size} workout-related habits for user: $userId")
            }
            
            for (habit in workoutHabits) {
                val titleLower = habit.title.lowercase()
                Log.d(TAG, "Checking workout habit: '${habit.title}' (id: ${habit.id}), target: ${habit.targetValue} ${habit.unit}")
                
                val canAutoComplete = shouldAutoComplete(habit, userId)
                Log.d(TAG, "  Can auto-complete: $canAutoComplete")
                
                if (canAutoComplete) {
                    // Check if this is a duration-based workout habit (e.g., "30-minute workout")
                    if (durationMinutes != null && habit.unit.lowercase().contains("min")) {
                        val targetMet = durationMinutes >= habit.targetValue
                        Log.d(TAG, "  Duration-based habit: ${durationMinutes}min >= ${habit.targetValue}min = $targetMet")
                        if (targetMet) {
                            Log.d(TAG, "  ✅✅✅ TARGET MET - COMPLETING NOW ✅✅✅")
                            autoCompleteHabit(userId, habit, durationMinutes, "Auto-completed: ${durationMinutes}min workout logged")
                        } else {
                            Log.d(TAG, "  ⏳ Target not met yet (need ${habit.targetValue}min, have ${durationMinutes}min)")
                        }
                    } else if (titleLower.contains("workout") || titleLower.contains("exercise")) {
                        // General workout habit - complete if workout was logged
                        Log.d(TAG, "  ✅✅✅ GENERAL WORKOUT HABIT - COMPLETING NOW ✅✅✅")
                        autoCompleteHabit(userId, habit, 1, "Auto-completed: workout logged")
                    } else {
                        Log.d(TAG, "  ⚠️ Habit doesn't contain 'workout' or 'exercise' in title - skipping")
                    }
                } else {
                    Log.d(TAG, "  ⚠️ Habit already completed today - skipping")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ ERROR AUTO-COMPLETING HABITS FOR WORKOUT LOG ❌❌❌", e)
            e.printStackTrace()
        }
    }
    
    /**
     * Called when a breathing exercise is logged - checks and auto-completes breathing habits
     */
    suspend fun onBreathingExerciseLogged(userId: String, durationSeconds: Int) {
        try {
            Log.d(TAG, "Breathing exercise logged: ${durationSeconds}s for user: $userId - checking for breathing habits")
            val habits = habitRepository.getHabits(userId).first()
            val breathingHabits = habits.filter { 
                it.isActive && 
                (it.category == HabitCategory.MENTAL_HEALTH ||
                 it.title.lowercase().contains("breathing") ||
                 it.title.lowercase().contains("breath"))
            }
            
            for (habit in breathingHabits) {
                if (shouldAutoComplete(habit, userId)) {
                    val titleLower = habit.title.lowercase()
                    val durationMinutes = durationSeconds / 60
                    
                    // Check if this is a duration-based breathing habit
                    if (habit.unit.lowercase().contains("min")) {
                        if (durationMinutes >= habit.targetValue) {
                            autoCompleteHabit(userId, habit, durationMinutes, "Auto-completed: ${durationMinutes}min breathing exercise logged")
                        }
                    } else if (titleLower.contains("breathing") || titleLower.contains("breath")) {
                        // General breathing habit - complete if breathing exercise was logged
                        autoCompleteHabit(userId, habit, 1, "Auto-completed: breathing exercise logged")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error auto-completing habits for breathing exercise log", e)
        }
    }
    
    /**
     * Check if a habit should be auto-completed (hasn't been completed today)
     */
    private suspend fun shouldAutoComplete(habit: Habit, userId: String): Boolean {
        try {
            // Check if habit was already completed today
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            val tomorrow = today + (24 * 60 * 60 * 1000)
            
            val completions = habitRepository.getRecentCompletions(userId, days = 1).first()
            val todayCompletions = completions.filter { 
                it.habitId == habit.id && 
                it.completedAt.time >= today && 
                it.completedAt.time < tomorrow
            }
            
            return todayCompletions.isEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if habit should be auto-completed", e)
            return false
        }
    }
    
    /**
     * Get total water logged today in ml
     * CRITICAL: Must include DailyLog.water (which includes voice logs) + WaterLog entries
     */
    private suspend fun getTotalWaterToday(userId: String): Int {
        return try {
            val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
            val repository = com.coachie.app.data.FirebaseRepository.getInstance()
            
            // Get water from DailyLog (includes voice logs and all sources)
            val dailyLog = repository.getDailyLog(userId, today).getOrNull()
            val waterFromDailyLog = dailyLog?.water ?: 0
            
            // Get water from WaterLog entries (manual logs)
            val healthLogs = repository.getHealthLogs(userId, today).getOrNull() ?: emptyList()
            val waterFromLogs = healthLogs.filterIsInstance<HealthLog.WaterLog>()
                .sumOf { it.ml }
            
            // CRITICAL: Use DailyLog.water as source of truth (it already includes voice logs)
            // But also add WaterLog entries that might not be in DailyLog yet
            val totalWater = if (waterFromDailyLog > 0) {
                // DailyLog.water is the source of truth - use it
                waterFromDailyLog
            } else {
                // Fallback to WaterLog entries if DailyLog.water is 0
                waterFromLogs
            }
            
            Log.d(TAG, "Total water today: DailyLog=${waterFromDailyLog}ml + WaterLogs=${waterFromLogs}ml = ${totalWater}ml")
            totalWater
        } catch (e: Exception) {
            Log.e(TAG, "Error getting total water today", e)
            0
        }
    }
    
    /**
     * Auto-complete a habit
     */
    private suspend fun autoCompleteHabit(userId: String, habit: Habit, value: Int, notes: String) {
        try {
            Log.i(TAG, "✅✅✅ AUTO-COMPLETING HABIT: ${habit.title} (id: ${habit.id}) - $notes ✅✅✅")
            val result = habitRepository.completeHabit(userId, habit.id, value, notes)
            result.onSuccess {
                Log.i(TAG, "✅✅✅ SUCCESSFULLY AUTO-COMPLETED HABIT: ${habit.title} (id: ${habit.id}) ✅✅✅")
                Log.i(TAG, "   Value: $value, Notes: $notes")
                
                // CRITICAL: Also mark the corresponding Today's Focus task as completed
                try {
                    val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                    val repository = com.coachie.app.data.FirebaseRepository.getInstance()
                    
                    // Query ALL tasks for today (not just incomplete ones) to find the habit task
                    val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val snapshot = firestore.collection("users")
                        .document(userId)
                        .collection("todaysFocusTasks")
                        .whereEqualTo("date", today)
                        .get()
                        .await()
                    
                    // Find tasks that are linked to this habit and not yet completed
                    val habitTasks = snapshot.documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        val actionTypeStr = data["actionType"] as? String
                        val actionData = data["actionData"] as? Map<String, Any> ?: emptyMap()
                        val completedAt = data["completedAt"] as? com.google.firebase.Timestamp
                        
                        if (actionTypeStr == "COMPLETE_HABIT" && 
                            actionData["habitId"] == habit.id &&
                            completedAt == null) {
                            doc.id to (data["title"] as? String ?: "")
                        } else {
                            null
                        }
                    }
                    
                    if (habitTasks.isNotEmpty()) {
                        Log.i(TAG, "   Found ${habitTasks.size} Today's Focus task(s) linked to this habit - marking as completed")
                        for ((taskId, taskTitle) in habitTasks) {
                            val completeResult = repository.completeTodaysFocusTask(userId, taskId)
                            if (completeResult.isSuccess) {
                                Log.i(TAG, "   ✅ Marked Today's Focus task '$taskTitle' as completed")
                            } else {
                                Log.w(TAG, "   ⚠️ Failed to mark task '$taskTitle' as completed: ${completeResult.exceptionOrNull()?.message}")
                            }
                        }
                    } else {
                        Log.d(TAG, "   No incomplete Today's Focus tasks found linked to this habit")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Error marking Today's Focus task as completed (non-critical)", e)
                    // Don't fail the habit completion if task marking fails
                }
                
                // The RemindersViewModel should pick this up via its Flow, but add a small delay
                // to ensure Firestore has propagated the change
                kotlinx.coroutines.delay(500)
            }.onFailure { error ->
                Log.e(TAG, "❌❌❌ FAILED TO AUTO-COMPLETE HABIT: ${habit.title} (id: ${habit.id}) ❌❌❌", error)
                Log.e(TAG, "   Error: ${error.message}")
                error.printStackTrace()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ EXCEPTION AUTO-COMPLETING HABIT: ${habit.title} (id: ${habit.id}) ❌❌❌", e)
            e.printStackTrace()
        }
    }
}

