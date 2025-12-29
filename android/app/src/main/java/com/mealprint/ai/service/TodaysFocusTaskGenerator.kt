package com.coachie.app.service

import android.util.Log
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.data.HabitRepository
import com.mealprint.ai.data.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Generates 7-9 tasks for Today's Focus at the start of each day
 */
class TodaysFocusTaskGenerator(
    private val repository: FirebaseRepository,
    private val habitRepository: HabitRepository
) {
    companion object {
        private const val TAG = "TodaysFocusTaskGenerator"
        private const val MIN_TASKS = 7
        private const val MAX_TASKS = 9
    }

    /**
     * Generate 7-9 tasks for today if they don't already exist
     */
    suspend fun generateTodaysTasksIfNeeded(userId: String): Result<List<TodaysFocusTask>> {
        return try {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            
            // Check if tasks already exist for today
            val existingTasksResult = repository.getTodaysFocusTasks(userId, today)
            val existingTasks = existingTasksResult.getOrNull() ?: emptyList()
            
            // Also check for completed tasks to see if we already generated today
            val allTasksSnapshot = repository.getTodaysFocusTasks(userId, today)
            val allTasks = allTasksSnapshot.getOrNull() ?: emptyList()
            
            // Check if we have any tasks for today (including completed ones)
            // We need to check the raw Firestore query to include completed tasks
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val allTasksSnapshotRaw = db
                .collection("users")
                .document(userId)
                .collection("todaysFocusTasks")
                .whereEqualTo("date", today)
                .get()
                .await()
            
            if (allTasksSnapshotRaw.documents.isNotEmpty()) {
                // Tasks already exist for today
                Log.d(TAG, "Tasks already exist for today: ${allTasksSnapshotRaw.documents.size} tasks")
                return Result.success(existingTasks)
            }
            
            // Generate new tasks
            val tasks = generateTasks(userId, today)
            
            // Save all tasks
            tasks.forEach { task ->
                repository.saveTodaysFocusTask(userId, task)
            }
            
            Log.d(TAG, "Generated ${tasks.size} tasks for today")
            Result.success(tasks)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating today's tasks", e)
            Result.failure(e)
        }
    }

    /**
     * Generate 7-9 tasks based on user's habits and goals
     */
    private suspend fun generateTasks(userId: String, date: String): List<TodaysFocusTask> {
        val tasks = mutableListOf<TodaysFocusTask>()
        
        // Load user data
        val habitsFlow = habitRepository.getHabits(userId)
        val habits: List<Habit> = try {
            habitsFlow.first().filter { habit: Habit -> habit.isActive }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading habits", e)
            emptyList()
        }
        
        val healthLogsResult = repository.getHealthLogs(userId, date)
        val todayHealthLogs = healthLogsResult.getOrNull() ?: emptyList()
        
        val meals = todayHealthLogs.filterIsInstance<HealthLog.MealLog>()
        val waterLogs = todayHealthLogs.filterIsInstance<HealthLog.WaterLog>()
        val dailyLogResult = repository.getDailyLog(userId, date)
        val dailyLog = dailyLogResult.getOrNull()
        val totalWater = (dailyLog?.water ?: 0) + waterLogs.sumOf { waterLog -> waterLog.ml }
        val sleepLogs = todayHealthLogs.filterIsInstance<HealthLog.SleepLog>()
        val workouts = todayHealthLogs.filterIsInstance<HealthLog.WorkoutLog>()
        val weightLogs = todayHealthLogs.filterIsInstance<HealthLog.WeightLog>()
        val supplements = todayHealthLogs.filterIsInstance<HealthLog.SupplementLog>()
        val journalEntries = todayHealthLogs.filterIsInstance<HealthLog.JournalEntry>()
        val meditationLogs = todayHealthLogs.filterIsInstance<HealthLog.MeditationLog>()
        val mindfulSessions = todayHealthLogs.filterIsInstance<HealthLog.MindfulSession>()
        
        // Get habit completions for today
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.time
        
        val tomorrowStart = java.util.Calendar.getInstance().apply {
            time = todayStart
            add(java.util.Calendar.DAY_OF_MONTH, 1)
        }.time
        
        val completionsFlow = habitRepository.getRecentCompletions(userId, days = 1)
        val allCompletions: List<HabitCompletion> = try {
            completionsFlow.first()
        } catch (e: Exception) {
            emptyList()
        }
        
        val todayCompletions = allCompletions.filter { completion: HabitCompletion ->
            completion.completedAt.time >= todayStart.time && completion.completedAt.time < tomorrowStart.time
        }
        val completedHabitIds = todayCompletions.map { completion: HabitCompletion -> completion.habitId }.toSet()
        
        // Generate tasks (7-9 total)
        var taskId = 1
        
        // 1. Health Log Tasks (2-3 tasks)
        if (meals.isEmpty()) {
            tasks.add(createTask(
                id = "task_${taskId++}",
                userId = userId,
                date = date,
                type = TodaysFocusTaskType.HEALTH_LOG,
                title = "Log Your First Meal",
                description = "Start tracking your nutrition by logging breakfast or your first meal",
                actionType = ReminderActionType.LOG_MEAL,
                estimatedDuration = 2
            ))
        }
        
        if (totalWater < 1000) { // Less than 1L
            tasks.add(createTask(
                id = "task_${taskId++}",
                userId = userId,
                date = date,
                type = TodaysFocusTaskType.HEALTH_LOG,
                title = "Drink Water",
                description = "Stay hydrated! Log at least one glass of water",
                actionType = ReminderActionType.LOG_WATER,
                estimatedDuration = 1
            ))
        }
        
        if (workouts.isEmpty()) {
            tasks.add(createTask(
                id = "task_${taskId++}",
                userId = userId,
                date = date,
                type = TodaysFocusTaskType.HEALTH_LOG,
                title = "Log a Workout",
                description = "Track your physical activity for today",
                actionType = ReminderActionType.LOG_WORKOUT,
                estimatedDuration = 1
            ))
        }
        
        // 2. Habit Tasks (4-5 tasks from active habits)
        val incompleteHabits = habits.filter { habit: Habit -> habit.id !in completedHabitIds }
        incompleteHabits.take(5).forEach { habit: Habit ->
            tasks.add(createTask(
                id = "task_${taskId++}",
                userId = userId,
                date = date,
                type = TodaysFocusTaskType.HABIT,
                title = habit.title,
                description = habit.description.ifBlank { "Complete your ${habit.title} habit" },
                actionType = ReminderActionType.COMPLETE_HABIT,
                actionData = mapOf(
                    "habitId" to habit.id,
                    "habitTitle" to habit.title
                ),
                estimatedDuration = 5
            ))
        }
        
        // 3. Wellness Tasks (2-3 tasks)
        if (journalEntries.isEmpty()) {
            tasks.add(createTask(
                id = "task_${taskId++}",
                userId = userId,
                date = date,
                type = TodaysFocusTaskType.WELLNESS,
                title = "Write in Journal",
                description = "Take a moment to reflect and write in your journal",
                actionType = ReminderActionType.START_JOURNAL,
                estimatedDuration = 5
            ))
        }
        
        if (meditationLogs.isEmpty() && mindfulSessions.isEmpty()) {
            tasks.add(createTask(
                id = "task_${taskId++}",
                userId = userId,
                date = date,
                type = TodaysFocusTaskType.WELLNESS,
                title = "Meditation or Mindfulness",
                description = "Take 5-10 minutes for meditation or a mindfulness session",
                actionType = ReminderActionType.START_MEDITATION,
                estimatedDuration = 10
            ))
        }
        
        // Add breathing exercise if no wellness activities
        if (tasks.count { it.type == TodaysFocusTaskType.WELLNESS } < 2) {
            tasks.add(createTask(
                id = "task_${taskId++}",
                userId = userId,
                date = date,
                type = TodaysFocusTaskType.WELLNESS,
                title = "Breathing Exercise",
                description = "Practice deep breathing for stress relief and focus",
                actionType = ReminderActionType.START_MINDFULNESS,
                estimatedDuration = 3
            ))
        }
        
        // Ensure we have 7-9 tasks
        val currentCount = tasks.size
        if (currentCount < MIN_TASKS) {
            // Add more generic tasks to reach minimum
            val additionalNeeded = MIN_TASKS - currentCount
            
            // Add more health log tasks if needed
            if (sleepLogs.isEmpty() && tasks.none { it.actionType == ReminderActionType.LOG_SLEEP }) {
                tasks.add(createTask(
                    id = "task_${taskId++}",
                    userId = userId,
                    date = date,
                    type = TodaysFocusTaskType.HEALTH_LOG,
                    title = "Log Sleep",
                    description = "Track your sleep duration and quality",
                    actionType = ReminderActionType.LOG_SLEEP,
                    estimatedDuration = 1
                ))
            }
            
            if (weightLogs.isEmpty() && tasks.none { it.actionType == ReminderActionType.LOG_WEIGHT }) {
                tasks.add(createTask(
                    id = "task_${taskId++}",
                    userId = userId,
                    date = date,
                    type = TodaysFocusTaskType.HEALTH_LOG,
                    title = "Log Weight",
                    description = "Track your weight for today",
                    actionType = ReminderActionType.LOG_WEIGHT,
                    estimatedDuration = 1
                ))
            }
            
            // Add more habit tasks if available
            val remainingHabits = incompleteHabits.drop(5)
            remainingHabits.take(additionalNeeded).forEach { habit: Habit ->
                tasks.add(createTask(
                    id = "task_${taskId++}",
                    userId = userId,
                    date = date,
                    type = TodaysFocusTaskType.HABIT,
                    title = habit.title,
                    description = habit.description.ifBlank { "Complete your ${habit.title} habit" },
                    actionType = ReminderActionType.COMPLETE_HABIT,
                    actionData = mapOf(
                        "habitId" to habit.id,
                        "habitTitle" to habit.title
                    ),
                    estimatedDuration = 5
                ))
            }
        }
        
        // Limit to MAX_TASKS
        return tasks.take(MAX_TASKS)
    }

    private fun createTask(
        id: String,
        userId: String,
        date: String,
        type: TodaysFocusTaskType,
        title: String,
        description: String,
        actionType: ReminderActionType,
        actionData: Map<String, Any> = emptyMap(),
        estimatedDuration: Int? = null
    ): TodaysFocusTask {
        return TodaysFocusTask(
            id = id,
            userId = userId,
            date = date,
            type = type,
            title = title,
            description = description,
            actionType = actionType,
            actionData = actionData,
            estimatedDuration = estimatedDuration,
            priority = 0,
            completedAt = null,
            createdAt = Date(),
            updatedAt = Date()
        )
    }
}

