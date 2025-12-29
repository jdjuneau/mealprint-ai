package com.coachie.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.data.HabitRepository
import com.mealprint.ai.data.model.*
import com.mealprint.ai.service.ReminderGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class RemindersViewModel(
    private val repository: FirebaseRepository = FirebaseRepository.getInstance(),
    private val habitRepository: HabitRepository = HabitRepository.getInstance()
) : ViewModel() {

    companion object {
        private const val TAG = "RemindersViewModel"
    }

    private val reminderGenerator = ReminderGenerator(repository)

    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders.asStateFlow()

    private val _currentReminder = MutableStateFlow<Reminder?>(null)
    val currentReminder: StateFlow<Reminder?> = _currentReminder.asStateFlow()

    // Persist completed reminders in memory (reset daily)
    private val _completedReminders = MutableStateFlow<Set<String>>(emptySet())
    val completedReminders: StateFlow<Set<String>> = _completedReminders.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var userId: String? = null
    
    // CRITICAL: Keep a persistent Flow for habit completions so we always get the latest data
    private var completionsFlow: Flow<List<HabitCompletion>>? = null

    fun initialize(userId: String) {
        this.userId = userId
        // Initialize the persistent Flow once - this Flow will emit updates in real-time
        completionsFlow = habitRepository.getRecentCompletions(userId, days = 1)
        loadReminders()
    }

    fun loadReminders() {
        val uid = userId ?: return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

                // Load all necessary data
                // Get habits (Flow)
                val habitsFlow = habitRepository.getHabits(uid)
                val habits = habitsFlow.first().filter { it.isActive }

                // Get completions - use Flow but get current value immediately
                // CRITICAL: Add delay to allow Firestore to propagate changes
                delay(1500) // Wait for Firestore to propagate completion saves
                val allCompletions = try {
                    val flow = completionsFlow ?: habitRepository.getRecentCompletions(uid, days = 1).also { 
                        completionsFlow = it 
                    }
                    // Get current value from Flow (snapshot listener emits immediately)
                    // Use withTimeout to ensure we don't wait forever
                    withTimeout(5000) {
                        flow.first()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting habit completions", e)
                    emptyList()
                }
                
                val todayStart = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
                val tomorrowStart = todayStart + (24 * 60 * 60 * 1000)
                
                val habitCompletions = allCompletions.filter {
                    it.completedAt.time >= todayStart && it.completedAt.time < tomorrowStart
                }
                
                Log.d(TAG, "Loaded ${habitCompletions.size} habit completions for today (from ${allCompletions.size} total)")
                habitCompletions.forEach { completion ->
                    Log.d(TAG, "  - Habit ${completion.habitId} completed at ${completion.completedAt}")
                }

                val healthLogsResult = repository.getHealthLogs(uid, today)
                val todayHealthLogs = healthLogsResult.getOrNull() ?: emptyList()

                val dailyLogResult = repository.getDailyLog(uid, today)
                val todayLog = dailyLogResult.getOrNull()

                val mindfulnessSessions = todayHealthLogs.filterIsInstance<HealthLog.MindfulSession>()
                val mindfulnessSession = mindfulnessSessions.firstOrNull { 
                    it.generatedDate == today 
                }

                // Generate reminders
                val allReminders = reminderGenerator.generateReminders(
                    userId = uid,
                    habits = habits,
                    habitCompletions = habitCompletions,
                    todayHealthLogs = todayHealthLogs,
                    todayLog = todayLog,
                    mindfulnessSession = mindfulnessSession
                )

                // CRITICAL: Filter out completed reminders - both manually completed and actually completed
                // This ensures that goals that have been achieved (e.g., water intake goal met) 
                // do NOT appear in Today's Focus
                val activeReminders = allReminders.filter { reminder ->
                    // Skip if manually marked as completed
                    if (reminder.id in _completedReminders.value) {
                        Log.d(TAG, "🚫 Filtering out reminder ${reminder.id} - manually marked as completed")
                        return@filter false
                    }
                    
                    // Check if the underlying action is actually completed
                    val isActuallyCompleted = when (reminder.actionType) {
                        ReminderActionType.LOG_MEAL -> {
                            val meals = todayHealthLogs.filterIsInstance<HealthLog.MealLog>()
                            meals.isNotEmpty()
                        }
                        ReminderActionType.LOG_WATER -> {
                            val waterLogs = todayHealthLogs.filterIsInstance<HealthLog.WaterLog>()
                            val waterFromDailyLog = todayLog?.water ?: 0
                            val totalWater = waterFromDailyLog + waterLogs.sumOf { it.ml }
                            
                            Log.d(TAG, "🔍 Checking water reminder completion:")
                            Log.d(TAG, "   Water from DailyLog: ${waterFromDailyLog}ml")
                            Log.d(TAG, "   Water from WaterLogs: ${waterLogs.sumOf { it.ml }}ml")
                            Log.d(TAG, "   Total water: ${totalWater}ml")
                            
                            // Check if this is a habit-based reminder - if so, check habit target
                            val habitId = reminder.actionData["habitId"] as? String
                            Log.d(TAG, "   Habit ID from reminder: $habitId")
                            
                            if (habitId != null) {
                                val habit = habits.find { it.id == habitId }
                                if (habit != null) {
                                    Log.d(TAG, "   Found habit: ${habit.title}, targetValue: ${habit.targetValue}, unit: ${habit.unit}")
                                    // Check if habit target is met
                                    val targetMl = if (habit.unit.lowercase().contains("glass") || habit.unit.lowercase().contains("cup")) {
                                        // Convert glasses to ml (1 glass = 240ml)
                                        habit.targetValue * 240
                                    } else if (habit.unit.lowercase().contains("ml") || habit.unit.lowercase().contains("liter")) {
                                        // Already in ml
                                        habit.targetValue
                                    } else {
                                        // Default: assume glasses if no unit specified
                                        habit.targetValue * 240
                                    }
                                    val isCompleted = totalWater >= targetMl
                                    if (isCompleted) {
                                        Log.d(TAG, "✅✅✅ Water reminder COMPLETED: ${totalWater}ml >= ${targetMl}ml (habit: ${habit.title}) ✅✅✅")
                                    } else {
                                        Log.d(TAG, "⏳ Water reminder NOT completed: ${totalWater}ml < ${targetMl}ml (habit: ${habit.title})")
                                    }
                                    isCompleted
                                } else {
                                    Log.w(TAG, "   ⚠️ Habit not found for ID: $habitId")
                                    // Habit not found - fallback to 8 glasses check (8 * 240ml = 1920ml)
                                    val targetMl = 8 * 240 // 8 glasses = 1920ml
                                    val isCompleted = totalWater >= targetMl
                                    Log.d(TAG, "   Fallback check: ${totalWater}ml >= ${targetMl}ml (8 glasses) = $isCompleted")
                                    isCompleted
                                }
                            } else {
                                Log.d(TAG, "   Not a habit-based reminder - checking for 8 glasses (1920ml)")
                                // Not a habit-based reminder - check for 8 glasses (8 * 240ml = 1920ml)
                                val targetMl = 8 * 240 // 8 glasses = 1920ml
                                val isCompleted = totalWater >= targetMl
                                Log.d(TAG, "   Generic check: ${totalWater}ml >= ${targetMl}ml (8 glasses) = $isCompleted")
                                isCompleted
                            }
                        }
                        ReminderActionType.LOG_WEIGHT -> {
                            val weightLogs = todayHealthLogs.filterIsInstance<HealthLog.WeightLog>()
                            weightLogs.isNotEmpty()
                        }
                        ReminderActionType.LOG_SLEEP -> {
                            val sleepLogs = todayHealthLogs.filterIsInstance<HealthLog.SleepLog>()
                            sleepLogs.isNotEmpty()
                        }
                        ReminderActionType.LOG_WORKOUT -> {
                            val workouts = todayHealthLogs.filterIsInstance<HealthLog.WorkoutLog>()
                            workouts.isNotEmpty()
                        }
                        ReminderActionType.LOG_SUPPLEMENT -> {
                            val supplements = todayHealthLogs.filterIsInstance<HealthLog.SupplementLog>()
                            supplements.isNotEmpty()
                        }
                        ReminderActionType.START_JOURNAL -> {
                            val journalEntries = todayHealthLogs.filterIsInstance<HealthLog.JournalEntry>()
                            journalEntries.any { it.isCompleted }
                        }
                        ReminderActionType.START_MINDFULNESS -> {
                            mindfulnessSession?.let { session ->
                                (session.playedCount ?: 0) > 0 &&
                                session.lastPlayedAt != null &&
                                (System.currentTimeMillis() - session.lastPlayedAt!!) < 24 * 60 * 60 * 1000
                            } ?: false
                        }
                        ReminderActionType.COMPLETE_HABIT -> {
                            val habitId = reminder.actionData["habitId"] as? String
                            if (habitId != null) {
                                // First check if habit was manually completed
                                val manuallyCompleted = habitCompletions.any { it.habitId == habitId }
                                if (manuallyCompleted) {
                                    Log.d(TAG, "✅ Habit reminder $habitId is COMPLETED (manually) - will be filtered out")
                                    return@filter false
                                }
                                
                                // For water habits, also check if water target is met
                                val habit = habits.find { it.id == habitId }
                                if (habit != null) {
                                    val habitTitleLower = habit.title.lowercase()
                                    val isWaterHabit = habitTitleLower.contains("water") || 
                                                      habitTitleLower.contains("drink") ||
                                                      habitTitleLower.contains("hydrate") ||
                                                      habitTitleLower.contains("glass")
                                    
                                    if (isWaterHabit) {
                                        // Check if water target is met
                                        val waterLogs = todayHealthLogs.filterIsInstance<HealthLog.WaterLog>()
                                        val waterFromDailyLog = todayLog?.water ?: 0
                                        val totalWater = waterFromDailyLog + waterLogs.sumOf { it.ml }
                                        
                                        Log.d(TAG, "🔍 Checking water habit completion for '${habit.title}':")
                                        Log.d(TAG, "   Water from DailyLog: ${waterFromDailyLog}ml")
                                        Log.d(TAG, "   Water from WaterLogs: ${waterLogs.sumOf { it.ml }}ml")
                                        Log.d(TAG, "   Total water: ${totalWater}ml")
                                        Log.d(TAG, "   Habit target: ${habit.targetValue} ${habit.unit}")
                                        
                                        val targetMl = if (habit.unit.lowercase().contains("glass") || habit.unit.lowercase().contains("cup")) {
                                            // Convert glasses to ml (1 glass = 240ml)
                                            habit.targetValue * 240
                                        } else if (habit.unit.lowercase().contains("ml") || habit.unit.lowercase().contains("liter")) {
                                            // Already in ml
                                            habit.targetValue
                                        } else {
                                            // Default: assume glasses if no unit specified
                                            habit.targetValue * 240
                                        }
                                        
                                        val isCompleted = totalWater >= targetMl
                                        if (isCompleted) {
                                            Log.d(TAG, "✅✅✅ Water habit '${habit.title}' COMPLETED: ${totalWater}ml >= ${targetMl}ml ✅✅✅")
                                        } else {
                                            Log.d(TAG, "⏳ Water habit '${habit.title}' NOT completed: ${totalWater}ml < ${targetMl}ml")
                                        }
                                        isCompleted
                                    } else {
                                        // Not a water habit - only check manual completion
                                        false
                                    }
                                } else {
                                    // Habit not found - only check manual completion
                                    false
                                }
                            } else {
                                false
                            }
                        }
                        else -> false // VIEW actions don't need completion check
                    }
                    
                    // Only include if not actually completed
                    val shouldInclude = !isActuallyCompleted
                    if (!shouldInclude) {
                        Log.d(TAG, "🚫 Filtering out reminder ${reminder.id} (${reminder.actionType}) - goal already achieved")
                    } else {
                        Log.d(TAG, "✅ Including reminder ${reminder.id} (${reminder.actionType}) - goal not yet achieved")
                    }
                    shouldInclude
                }
                
                Log.d(TAG, "📊 Filtered reminders: ${allReminders.size} total -> ${activeReminders.size} active (filtered out ${allReminders.size - activeReminders.size} completed)")

                // CRITICAL: Ensure at least 7 active reminders - add defaults if needed AFTER filtering
                val minReminders = 7
                val finalReminders = if (activeReminders.size < minReminders) {
                    val additionalNeeded = minReminders - activeReminders.size
                    val existingIds = activeReminders.map { it.id }.toSet()
                    val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    
                    // Check what types of reminders already exist to avoid duplicates
                    val hasMealReminder = activeReminders.any { it.actionType == ReminderActionType.LOG_MEAL }
                    val hasWaterReminder = activeReminders.any { it.actionType == ReminderActionType.LOG_WATER }
                    val hasWorkoutReminder = activeReminders.any { it.actionType == ReminderActionType.LOG_WORKOUT }
                    val hasJournalReminder = activeReminders.any { it.actionType == ReminderActionType.START_JOURNAL }
                    val hasHealthTrackingReminder = activeReminders.any { it.actionType == ReminderActionType.VIEW_HEALTH_TRACKING }
                    val hasWellnessReminder = activeReminders.any { it.actionType == ReminderActionType.VIEW_WELLNESS }
                    val hasHabitsReminder = activeReminders.any { it.actionType == ReminderActionType.VIEW_HABITS }
                    
                    val additionalReminders = mutableListOf<Reminder>()
                    
                    // Add VIEW action reminders first (these are less likely to be filtered out)
                    if (!hasHealthTrackingReminder && additionalReminders.size < additionalNeeded) {
                        additionalReminders.add(
                            Reminder(
                                id = "health_check_${System.currentTimeMillis()}",
                                type = ReminderType.HEALTH_LOG,
                                title = "Health Check-In",
                                description = "Take a moment to check in with your body and mind",
                                icon = "favorite",
                                priority = ReminderPriority.MEDIUM,
                                actionType = ReminderActionType.VIEW_HEALTH_TRACKING,
                                estimatedDuration = 2
                            )
                        )
                    }
                    
                    if (!hasWellnessReminder && additionalReminders.size < additionalNeeded) {
                        additionalReminders.add(
                            Reminder(
                                id = "wellness_break_${System.currentTimeMillis()}",
                                type = ReminderType.WELLNESS,
                                title = "Wellness Break",
                                description = "Take a short break to focus on your wellbeing",
                                icon = "self_improvement",
                                priority = ReminderPriority.MEDIUM,
                                actionType = ReminderActionType.VIEW_WELLNESS,
                                estimatedDuration = 5
                            )
                        )
                    }
                    
                    if (!hasHabitsReminder && additionalReminders.size < additionalNeeded) {
                        additionalReminders.add(
                            Reminder(
                                id = "habit_review_${System.currentTimeMillis()}",
                                type = ReminderType.HABIT,
                                title = "Review Habits",
                                description = "Check in on your daily habits and progress",
                                icon = "check_circle",
                                priority = ReminderPriority.MEDIUM,
                                actionType = ReminderActionType.VIEW_HABITS,
                                estimatedDuration = 3
                            )
                        )
                    }
                    
                    // Add action reminders if still needed (these might be filtered if already done, but worth trying)
                    if (!hasMealReminder && additionalReminders.size < additionalNeeded) {
                        additionalReminders.add(
                            Reminder(
                                id = "nutrition_tracking_${System.currentTimeMillis()}",
                                type = ReminderType.HEALTH_LOG,
                                title = "Track Nutrition",
                                description = "Keep track of your meals for better health insights",
                                icon = "restaurant",
                                priority = ReminderPriority.MEDIUM,
                                actionType = ReminderActionType.LOG_MEAL,
                                estimatedDuration = 2
                            )
                        )
                    }
                    
                    if (!hasWaterReminder && additionalReminders.size < additionalNeeded) {
                        additionalReminders.add(
                            Reminder(
                                id = "hydration_check_${System.currentTimeMillis()}",
                                type = ReminderType.HEALTH_LOG,
                                title = "Stay Hydrated",
                                description = "Make sure you're drinking enough water throughout the day",
                                icon = "local_drink",
                                priority = ReminderPriority.MEDIUM,
                                actionType = ReminderActionType.LOG_WATER,
                                estimatedDuration = 1
                            )
                        )
                    }
                    
                    if (!hasWorkoutReminder && additionalReminders.size < additionalNeeded) {
                        additionalReminders.add(
                            Reminder(
                                id = "movement_reminder_${System.currentTimeMillis()}",
                                type = ReminderType.HEALTH_LOG,
                                title = "Move Your Body",
                                description = "Even a short walk or stretch can boost your energy",
                                icon = "directions_walk",
                                priority = ReminderPriority.MEDIUM,
                                actionType = ReminderActionType.LOG_WORKOUT,
                                estimatedDuration = 10
                            )
                        )
                    }
                    
                    if (!hasJournalReminder && additionalReminders.size < additionalNeeded) {
                        additionalReminders.add(
                            Reminder(
                                id = "daily_reflection_${System.currentTimeMillis()}",
                                type = ReminderType.WELLNESS,
                                title = "Daily Reflection",
                                description = "Take a moment to reflect on your day",
                                icon = "edit_note",
                                priority = ReminderPriority.MEDIUM,
                                actionType = ReminderActionType.START_JOURNAL,
                                estimatedDuration = 5
                            )
                        )
                    }
                    
                    // Add more generic reminders if still needed
                    while (additionalReminders.size < additionalNeeded) {
                        val timestamp = System.currentTimeMillis()
                        additionalReminders.add(
                            Reminder(
                                id = "wellness_activity_${timestamp}_${additionalReminders.size}",
                                type = ReminderType.WELLNESS,
                                title = "Wellness Activity",
                                description = "Take time for your wellbeing today",
                                icon = "self_improvement",
                                priority = ReminderPriority.MEDIUM,
                                actionType = ReminderActionType.VIEW_WELLNESS,
                                estimatedDuration = 5
                            )
                        )
                    }
                    
                    val combined = activeReminders + additionalReminders.take(additionalNeeded)
                    Log.d(TAG, "✅ Added ${additionalReminders.take(additionalNeeded).size} default reminders to reach minimum of $minReminders (was ${activeReminders.size}, now ${combined.size})")
                    combined
                } else {
                    activeReminders
                }

                _reminders.value = finalReminders

                // CRITICAL: If current reminder is no longer in active reminders (was completed),
                // automatically advance to the first active reminder
                val currentReminderId = _currentReminder.value?.id
                if (currentReminderId != null && !finalReminders.any { it.id == currentReminderId }) {
                    Log.d(TAG, "✅ Current reminder $currentReminderId was completed - advancing to next")
                    _currentReminder.value = finalReminders.firstOrNull()
                } else {
                    // Set current reminder (first active one) if not set
                    if (_currentReminder.value == null) {
                        _currentReminder.value = finalReminders.firstOrNull()
                    }
                }

                Log.d(TAG, "📊 Loaded ${finalReminders.size} active reminders out of ${allReminders.size} total (minimum: $minReminders)")
                if (finalReminders.isEmpty()) {
                    Log.d(TAG, "   ✅ All reminders completed!")
                } else {
                    finalReminders.forEach { reminder ->
                        Log.d(TAG, "   - Active reminder: ${reminder.id} (${reminder.actionType})")
                    }
                }
                
                // FORCE UI UPDATE: If a water reminder was filtered out, make sure we advance IMMEDIATELY
                val waterReminderWasFiltered = allReminders.any { reminder ->
                    reminder.actionType == ReminderActionType.LOG_WATER && 
                    !finalReminders.any { it.id == reminder.id }
                }
                if (waterReminderWasFiltered) {
                    Log.d(TAG, "💧💧💧 Water reminder was filtered out - forcing UI update NOW 💧💧💧")
                    // The current reminder check above should handle this, but force it anyway
                    val currentReminderId = _currentReminder.value?.id
                    if (_currentReminder.value?.actionType == ReminderActionType.LOG_WATER && 
                        currentReminderId != null &&
                        !finalReminders.any { it.id == currentReminderId }) {
                        Log.d(TAG, "💧💧💧💧💧 Current reminder is water and was filtered - advancing NOW 💧💧💧💧💧")
                        _currentReminder.value = finalReminders.firstOrNull()
                        // Force a state update to trigger UI recomposition
                        _reminders.value = finalReminders
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading reminders", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun completeCurrentReminder() {
        val current = _currentReminder.value ?: return
        val reminderId = current.id

        try {
            viewModelScope.launch {
                // Mark as completed
                val completedSet = _completedReminders.value.toMutableSet()
                completedSet.add(reminderId)
                _completedReminders.value = completedSet

                // Remove completed reminder from active list immediately
                val updatedReminders = _reminders.value.filter { it.id != reminderId }
                _reminders.value = updatedReminders

                // Move to next reminder from the updated list
                val nextReminder = updatedReminders.firstOrNull()
                _currentReminder.value = nextReminder

                Log.d(TAG, "Completed reminder: $reminderId, next: ${nextReminder?.id}, remaining: ${updatedReminders.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error completing reminder", e)
        }
    }
    
    /**
     * Skip the current reminder (mark as completed without action)
     */
    fun skipCurrentReminder() {
        val current = _currentReminder.value ?: return
        val reminderId = current.id

        viewModelScope.launch {
            // Mark as completed (skipped)
            val completedSet = _completedReminders.value.toMutableSet()
            completedSet.add(reminderId)
            _completedReminders.value = completedSet

            // Remove skipped reminder from active list immediately
            val updatedReminders = _reminders.value.filter { it.id != reminderId }
            _reminders.value = updatedReminders

            // Move to next reminder from the updated list
            val nextReminder = updatedReminders.firstOrNull()
            _currentReminder.value = nextReminder

            Log.d(TAG, "Skipped reminder: $reminderId, next: ${nextReminder?.id}, remaining: ${updatedReminders.size}")
        }
    }

    fun refresh() {
        loadReminders()
    }
    
    /**
     * Navigate to next reminder (without completing current)
     */
    fun navigateToNext() {
        val current = _currentReminder.value ?: return
        val currentIndex = _reminders.value.indexOfFirst { it.id == current.id }
        if (currentIndex >= 0 && currentIndex < _reminders.value.size - 1) {
            _currentReminder.value = _reminders.value[currentIndex + 1]
        }
    }
    
    /**
     * Navigate to previous reminder
     */
    fun navigateToPrevious() {
        val current = _currentReminder.value ?: return
        val currentIndex = _reminders.value.indexOfFirst { it.id == current.id }
        if (currentIndex > 0) {
            _currentReminder.value = _reminders.value[currentIndex - 1]
        }
    }
    
    /**
     * Navigate to reminder at specific index
     */
    fun navigateToIndex(index: Int) {
        val reminders = _reminders.value
        if (index >= 0 && index < reminders.size) {
            _currentReminder.value = reminders[index]
        }
    }
    
    /**
     * Get current reminder index
     */
    fun getCurrentReminderIndex(): Int {
        val current = _currentReminder.value ?: return -1
        return _reminders.value.indexOfFirst { it.id == current.id }
    }
    
    /**
     * Check if a reminder's action has been completed and automatically advance to next
     */
    fun checkAndAdvanceIfCompleted(reminder: Reminder) {
        val uid = userId ?: return
        
        viewModelScope.launch {
            try {
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                
                // Load current data to check if reminder is completed
                val healthLogsResult = repository.getHealthLogs(uid, today)
                val todayHealthLogs = healthLogsResult.getOrNull() ?: emptyList()
                
                val dailyLogResult = repository.getDailyLog(uid, today)
                val todayLog = dailyLogResult.getOrNull()
                
                val habitsFlow = habitRepository.getHabits(uid)
                val habits = habitsFlow.first().filter { it.isActive }
                
                // CRITICAL: Wait for Firestore to propagate completion saves
                delay(2000) // Increased delay for Firestore propagation
                val completionsFlow = habitRepository.getRecentCompletions(uid, days = 1)
                val allCompletions = try {
                    withTimeout(5000) {
                        completionsFlow.first()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting completions in checkAndAdvanceIfCompleted", e)
                    emptyList()
                }
                val todayStart = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
                val tomorrowStart = todayStart + (24 * 60 * 60 * 1000)
                
                val habitCompletions = allCompletions.filter {
                    it.completedAt.time >= todayStart && it.completedAt.time < tomorrowStart
                }
                
                val mindfulnessSessions = todayHealthLogs.filterIsInstance<HealthLog.MindfulSession>()
                val mindfulnessSession = mindfulnessSessions.firstOrNull { 
                    it.generatedDate == today 
                }
                
                // Check if reminder is completed based on its action type
                val isCompleted = when (reminder.actionType) {
                    ReminderActionType.LOG_MEAL -> {
                        val meals = todayHealthLogs.filterIsInstance<HealthLog.MealLog>()
                        meals.isNotEmpty()
                    }
                    ReminderActionType.LOG_WATER -> {
                        val waterLogs = todayHealthLogs.filterIsInstance<HealthLog.WaterLog>()
                        val waterFromDailyLog = todayLog?.water ?: 0
                        val totalWater = waterFromDailyLog + waterLogs.sumOf { it.ml }
                        
                        Log.d(TAG, "🔍 [checkAndAdvanceIfCompleted] Checking water reminder:")
                        Log.d(TAG, "   Water from DailyLog: ${waterFromDailyLog}ml")
                        Log.d(TAG, "   Water from WaterLogs: ${waterLogs.sumOf { it.ml }}ml")
                        Log.d(TAG, "   Total water: ${totalWater}ml")
                        
                        // Check if this is a habit-based reminder - if so, check habit target
                        val habitId = reminder.actionData["habitId"] as? String
                        Log.d(TAG, "   Habit ID from reminder: $habitId")
                        
                        if (habitId != null) {
                            val habit = habits.find { it.id == habitId }
                            if (habit != null) {
                                Log.d(TAG, "   Found habit: ${habit.title}, targetValue: ${habit.targetValue}, unit: ${habit.unit}")
                                // Check if habit target is met
                                val targetMl = if (habit.unit.lowercase().contains("glass") || habit.unit.lowercase().contains("cup")) {
                                    // Convert glasses to ml (1 glass = 240ml)
                                    habit.targetValue * 240
                                } else if (habit.unit.lowercase().contains("ml") || habit.unit.lowercase().contains("liter")) {
                                    // Already in ml
                                    habit.targetValue
                                } else {
                                    // Default: assume glasses if no unit specified
                                    habit.targetValue * 240
                                }
                                val isCompleted = totalWater >= targetMl
                                if (isCompleted) {
                                    Log.d(TAG, "✅✅✅ [checkAndAdvanceIfCompleted] Water reminder COMPLETED: ${totalWater}ml >= ${targetMl}ml (habit: ${habit.title}) ✅✅✅")
                                } else {
                                    Log.d(TAG, "⏳ [checkAndAdvanceIfCompleted] Water reminder NOT completed: ${totalWater}ml < ${targetMl}ml (habit: ${habit.title})")
                                }
                                isCompleted
                            } else {
                                Log.w(TAG, "   ⚠️ Habit not found for ID: $habitId")
                                // Habit not found - fallback to 8 glasses check (8 * 240ml = 1920ml)
                                val targetMl = 8 * 240 // 8 glasses = 1920ml
                                val isCompleted = totalWater >= targetMl
                                Log.d(TAG, "   Fallback check: ${totalWater}ml >= ${targetMl}ml (8 glasses) = $isCompleted")
                                isCompleted
                            }
                        } else {
                            Log.d(TAG, "   Not a habit-based reminder - checking for 8 glasses (1920ml)")
                            // Not a habit-based reminder - check for 8 glasses (8 * 240ml = 1920ml)
                            val targetMl = 8 * 240 // 8 glasses = 1920ml
                            val isCompleted = totalWater >= targetMl
                            Log.d(TAG, "   Generic check: ${totalWater}ml >= ${targetMl}ml (8 glasses) = $isCompleted")
                            isCompleted
                        }
                    }
                    ReminderActionType.LOG_WEIGHT -> {
                        val weightLogs = todayHealthLogs.filterIsInstance<HealthLog.WeightLog>()
                        weightLogs.isNotEmpty()
                    }
                    ReminderActionType.LOG_SLEEP -> {
                        val sleepLogs = todayHealthLogs.filterIsInstance<HealthLog.SleepLog>()
                        sleepLogs.isNotEmpty()
                    }
                    ReminderActionType.LOG_WORKOUT -> {
                        val workouts = todayHealthLogs.filterIsInstance<HealthLog.WorkoutLog>()
                        workouts.isNotEmpty()
                    }
                    ReminderActionType.LOG_SUPPLEMENT -> {
                        val supplements = todayHealthLogs.filterIsInstance<HealthLog.SupplementLog>()
                        supplements.isNotEmpty()
                    }
                    ReminderActionType.START_JOURNAL -> {
                        val journalEntries = todayHealthLogs.filterIsInstance<HealthLog.JournalEntry>()
                        journalEntries.any { it.isCompleted }
                    }
                    ReminderActionType.START_MINDFULNESS -> {
                        mindfulnessSession?.let { session ->
                            (session.playedCount ?: 0) > 0 &&
                            session.lastPlayedAt != null &&
                            (System.currentTimeMillis() - session.lastPlayedAt!!) < 24 * 60 * 60 * 1000
                        } ?: false
                    }
                    ReminderActionType.COMPLETE_HABIT -> {
                        val habitId = reminder.actionData["habitId"] as? String
                        if (habitId != null) {
                            // First check if habit was manually completed
                            val manuallyCompleted = habitCompletions.any { it.habitId == habitId }
                            if (manuallyCompleted) {
                                Log.d(TAG, "✅ [checkAndAdvanceIfCompleted] Habit $habitId is COMPLETED (manually)")
                                true
                            } else {
                                // For water habits, also check if water target is met
                                val habit = habits.find { it.id == habitId }
                                if (habit != null) {
                                    val habitTitleLower = habit.title.lowercase()
                                    val isWaterHabit = habitTitleLower.contains("water") || 
                                                      habitTitleLower.contains("drink") ||
                                                      habitTitleLower.contains("hydrate") ||
                                                      habitTitleLower.contains("glass")
                                    
                                    if (isWaterHabit) {
                                        // Check if water target is met
                                        val waterLogs = todayHealthLogs.filterIsInstance<HealthLog.WaterLog>()
                                        val waterFromDailyLog = todayLog?.water ?: 0
                                        val totalWater = waterFromDailyLog + waterLogs.sumOf { it.ml }
                                        
                                        Log.d(TAG, "🔍 [checkAndAdvanceIfCompleted] Checking water habit '${habit.title}':")
                                        Log.d(TAG, "   Total water: ${totalWater}ml, target: ${habit.targetValue} ${habit.unit}")
                                        
                                        val targetMl = if (habit.unit.lowercase().contains("glass") || habit.unit.lowercase().contains("cup")) {
                                            habit.targetValue * 240
                                        } else if (habit.unit.lowercase().contains("ml") || habit.unit.lowercase().contains("liter")) {
                                            habit.targetValue
                                        } else {
                                            habit.targetValue * 240
                                        }
                                        
                                        val isCompleted = totalWater >= targetMl
                                        if (isCompleted) {
                                            Log.d(TAG, "✅✅✅ [checkAndAdvanceIfCompleted] Water habit '${habit.title}' COMPLETED: ${totalWater}ml >= ${targetMl}ml ✅✅✅")
                                        } else {
                                            Log.d(TAG, "⏳ [checkAndAdvanceIfCompleted] Water habit '${habit.title}' NOT completed: ${totalWater}ml < ${targetMl}ml")
                                        }
                                        isCompleted
                                    } else {
                                        // Not a water habit - only check manual completion
                                        false
                                    }
                                } else {
                                    // Habit not found - only check manual completion
                                    false
                                }
                            }
                        } else {
                            false
                        }
                    }
                    else -> false // VIEW actions don't need completion check
                }
                
                // If completed and this is the current reminder, advance to next
                if (isCompleted && _currentReminder.value?.id == reminder.id) {
                    Log.d(TAG, "Reminder ${reminder.id} is completed, advancing to next")
                    completeCurrentReminder()
                    // Refresh to get updated list with a longer delay to ensure Firestore has propagated
                    delay(1000) // Increased delay to ensure data is saved and Flows have updated
                    refresh()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking reminder completion", e)
            }
        }
    }
}

