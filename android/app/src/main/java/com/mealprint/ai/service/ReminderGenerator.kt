package com.coachie.app.service

import android.util.Log
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.data.model.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Generates personalized reminders and challenges for the user
 */
class ReminderGenerator(
    private val repository: FirebaseRepository
) {
    companion object {
        private const val TAG = "ReminderGenerator"
    }

    /**
     * Generate a list of reminders for the user based on their current state
     */
    suspend fun generateReminders(
        userId: String,
        habits: List<Habit>,
        habitCompletions: List<HabitCompletion>,
        todayHealthLogs: List<HealthLog>,
        todayLog: DailyLog?,
        mindfulnessSession: HealthLog.MindfulSession?
    ): List<Reminder> {
        val reminders = mutableListOf<Reminder>()
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        // Ensure we don't generate duplicate reminders by using a set for IDs
        val usedIds = mutableSetOf<String>()

        fun addReminderIfUnique(reminder: Reminder) {
            if (!usedIds.contains(reminder.id)) {
                reminders.add(reminder)
                usedIds.add(reminder.id)
            }
        }

        // Check mindfulness session completion
        val mindfulnessCompleted = mindfulnessSession?.let { session ->
            (session.playedCount ?: 0) > 0 &&
            session.lastPlayedAt != null &&
            (System.currentTimeMillis() - session.lastPlayedAt!!) < 24 * 60 * 60 * 1000
        } ?: false

        // 1. Dynamic Mindfulness/Wellness Activities (varies daily)
        if (!mindfulnessCompleted) {
            val dayOfWeek = LocalDate.now().dayOfWeek.value // 1=Monday, 7=Sunday
            val dayOfMonth = LocalDate.now().dayOfMonth

            // Rotate through different types of mindfulness/wellness activities
            val mindfulnessOptions = listOf(
                // Standard mindfulness
                Reminder(
                    id = "mindfulness_session_$today",
                    type = ReminderType.MINDFULNESS,
                    title = "3-Min Mindfulness Reset",
                    description = "Take 3 minutes to center yourself and start the day mindfully",
                    icon = "self_improvement",
                    priority = ReminderPriority.HIGH,
                    actionType = ReminderActionType.START_MINDFULNESS,
                    estimatedDuration = 3
                ),
                // Breathing exercise
                Reminder(
                    id = "breathing_exercise_$today",
                    type = ReminderType.WELLNESS,
                    title = "Deep Breathing Break",
                    description = "Practice 4-7-8 breathing for instant stress relief",
                    icon = "air",
                    priority = ReminderPriority.HIGH,
                    actionType = ReminderActionType.START_MINDFULNESS,
                    estimatedDuration = 5
                ),
                // Gratitude practice
                Reminder(
                    id = "gratitude_practice_$today",
                    type = ReminderType.WELLNESS,
                    title = "Gratitude Moment",
                    description = "Write down 3 things you're grateful for today",
                    icon = "favorite",
                    priority = ReminderPriority.MEDIUM,
                    actionType = ReminderActionType.START_JOURNAL,
                    estimatedDuration = 3
                ),
                // Goal setting
                Reminder(
                    id = "goal_setting_$today",
                    type = ReminderType.WELLNESS,
                    title = "Set Your Intention",
                    description = "Define 1-2 key goals or intentions for today",
                    icon = "flag",
                    priority = ReminderPriority.MEDIUM,
                    actionType = ReminderActionType.START_JOURNAL,
                    estimatedDuration = 5
                ),
                // Body scan
                Reminder(
                    id = "body_scan_$today",
                    type = ReminderType.WELLNESS,
                    title = "Quick Body Check-In",
                    description = "Take a moment to notice how your body feels today",
                    icon = "accessibility",
                    priority = ReminderPriority.MEDIUM,
                    actionType = ReminderActionType.START_MINDFULNESS,
                    estimatedDuration = 2
                ),
                // Positive affirmation
                Reminder(
                    id = "positive_affirmation_$today",
                    type = ReminderType.WELLNESS,
                    title = "Positive Affirmation",
                    description = "Repeat a positive affirmation to set your mindset",
                    icon = "psychology",
                    priority = ReminderPriority.LOW,
                    actionType = ReminderActionType.START_JOURNAL,
                    estimatedDuration = 2
                )
            )

            // Select activity based on day pattern (cycles through options)
            val activityIndex = (dayOfWeek + dayOfMonth) % mindfulnessOptions.size
            val selectedActivity = mindfulnessOptions[activityIndex]

            // Only add if it's morning and we haven't done any wellness activity today
            val hasDoneWellnessToday = todayHealthLogs.any { log ->
                log is HealthLog.MindfulSession ||
                (log is HealthLog.JournalEntry && log.isCompleted)
            }

            if (currentHour < 14 && !hasDoneWellnessToday) { // Morning to early afternoon
                addReminderIfUnique(selectedActivity)
            }
        }

        // 2. Health Log Reminders
        val meals = todayHealthLogs.filterIsInstance<HealthLog.MealLog>()
        val waterLogs = todayHealthLogs.filterIsInstance<HealthLog.WaterLog>()
        val waterFromDailyLog = todayLog?.water ?: 0
        val totalWater = waterFromDailyLog + waterLogs.sumOf { it.ml }
        val weightLogs = todayHealthLogs.filterIsInstance<HealthLog.WeightLog>()
        val sleepLogs = todayHealthLogs.filterIsInstance<HealthLog.SleepLog>()
        val workouts = todayHealthLogs.filterIsInstance<HealthLog.WorkoutLog>()

        // Morning reminders (before 12 PM) - vary based on day
        if (currentHour < 12) {
            val dayOfWeek = LocalDate.now().dayOfWeek.value

            // Water reminder (if less than 500ml) - vary the messaging
            if (totalWater < 500) {
                val waterTitles = listOf(
                    "Start with Water",
                    "Hydrate First",
                    "Morning Hydration",
                    "Water Your Body",
                    "Drink Up!"
                )
                val waterDescriptions = listOf(
                    "Kickstart your metabolism with a glass of water",
                    "Begin your day hydrated and energized",
                    "Start your hydration journey for the day",
                    "Your body needs water to function optimally",
                    "Set a positive tone with proper hydration"
                )

                val waterIndex = (dayOfWeek * 3) % waterTitles.size
                addReminderIfUnique(
                    Reminder(
                        id = "water_morning_$today",
                        type = ReminderType.HEALTH_LOG,
                        title = waterTitles[waterIndex],
                        description = waterDescriptions[waterIndex],
                        icon = "local_drink",
                        priority = ReminderPriority.MEDIUM,
                        actionType = ReminderActionType.LOG_WATER,
                        estimatedDuration = 1
                    )
                )
            }

            // Breakfast reminder (if no meals logged) - vary the approach
            if (meals.isEmpty()) {
                val breakfastOptions = listOf(
                    Reminder(
                        id = "breakfast_$today",
                        type = ReminderType.HEALTH_LOG,
                        title = "Fuel Your Morning",
                        description = "Start your day with nutritious breakfast choices",
                        icon = "restaurant",
                        priority = ReminderPriority.MEDIUM,
                        actionType = ReminderActionType.LOG_MEAL,
                        estimatedDuration = 2
                    ),
                    Reminder(
                        id = "breakfast_$today",
                        type = ReminderType.HEALTH_LOG,
                        title = "Breakfast Time",
                        description = "What are you having for breakfast today?",
                        icon = "restaurant",
                        priority = ReminderPriority.MEDIUM,
                        actionType = ReminderActionType.LOG_MEAL,
                        estimatedDuration = 2
                    ),
                    Reminder(
                        id = "breakfast_$today",
                        type = ReminderType.HEALTH_LOG,
                        title = "Morning Nutrition",
                        description = "Log your breakfast to track your daily nutrition",
                        icon = "restaurant",
                        priority = ReminderPriority.MEDIUM,
                        actionType = ReminderActionType.LOG_MEAL,
                        estimatedDuration = 2
                    ),
                    Reminder(
                        id = "breakfast_$today",
                        type = ReminderType.HEALTH_LOG,
                        title = "Start Strong",
                        description = "A good breakfast sets the tone for your day",
                        icon = "restaurant",
                        priority = ReminderPriority.MEDIUM,
                        actionType = ReminderActionType.LOG_MEAL,
                        estimatedDuration = 2
                    )
                )

                val breakfastIndex = dayOfWeek % breakfastOptions.size
                addReminderIfUnique(breakfastOptions[breakfastIndex])
            }

            // Add a morning stretch or movement reminder on some days
            if (workouts.isEmpty() && (dayOfWeek == 1 || dayOfWeek == 4)) { // Monday or Thursday
                addReminderIfUnique(
                    Reminder(
                        id = "morning_movement_$today",
                        type = ReminderType.HEALTH_LOG,
                        title = "Morning Movement",
                        description = "Start your day with some light stretching or movement",
                        icon = "directions_run",
                        priority = ReminderPriority.LOW,
                        actionType = ReminderActionType.LOG_WORKOUT,
                        estimatedDuration = 5
                    )
                )
            }
        }

        // Afternoon reminders (12 PM - 5 PM) - vary based on day
        if (currentHour >= 12 && currentHour < 17) {
            val dayOfWeek = LocalDate.now().dayOfWeek.value

            // Water reminder (if less than 1000ml) - vary the messaging
            if (totalWater < 1000) {
                val afternoonWaterTitles = listOf(
                    "Afternoon Hydration",
                    "Midday Water Check",
                    "Keep Hydrating",
                    "Water Boost",
                    "Stay Refreshed"
                )
                val afternoonWaterDescriptions = listOf(
                    "Your body is working hard - keep the water flowing",
                    "Midday is the perfect time to check your hydration",
                    "Maintain your energy with consistent water intake",
                    "A quick water break can refresh your afternoon",
                    "Stay sharp and focused with proper hydration"
                )

                val waterIndex = (dayOfWeek * 2 + 1) % afternoonWaterTitles.size
                addReminderIfUnique(
                    Reminder(
                        id = "water_afternoon_$today",
                        type = ReminderType.HEALTH_LOG,
                        title = afternoonWaterTitles[waterIndex],
                        description = afternoonWaterDescriptions[waterIndex],
                        icon = "local_drink",
                        priority = ReminderPriority.MEDIUM,
                        actionType = ReminderActionType.LOG_WATER,
                        estimatedDuration = 1
                    )
                )
            }

            // Lunch reminder (if only 0-1 meals) - only if not already added above
            if (meals.size <= 1 && !(currentHour >= 11 && currentHour < 14)) {
                val lunchOptions = listOf(
                    Reminder(
                        id = "lunch_$today",
                        type = ReminderType.HEALTH_LOG,
                        title = "Lunch Break",
                        description = "What did you have for lunch? Let's log it!",
                        icon = "restaurant",
                        priority = ReminderPriority.MEDIUM,
                        actionType = ReminderActionType.LOG_MEAL,
                        estimatedDuration = 2
                    ),
                    Reminder(
                        id = "lunch_$today",
                        type = ReminderType.HEALTH_LOG,
                        title = "Midday Fuel",
                        description = "Refuel your body with a nutritious lunch",
                        icon = "restaurant",
                        priority = ReminderPriority.MEDIUM,
                        actionType = ReminderActionType.LOG_MEAL,
                        estimatedDuration = 2
                    ),
                    Reminder(
                        id = "lunch_$today",
                        type = ReminderType.HEALTH_LOG,
                        title = "Lunch Time",
                        description = "Track your midday meal for better nutrition insights",
                        icon = "restaurant",
                        priority = ReminderPriority.MEDIUM,
                        actionType = ReminderActionType.LOG_MEAL,
                        estimatedDuration = 2
                    )
                )

                val lunchIndex = (dayOfWeek + 2) % lunchOptions.size
                addReminderIfUnique(lunchOptions[lunchIndex])
            }

            // Workout reminder (if no workouts and it's a good time) - vary on different days
            if (workouts.isEmpty() && currentHour >= 14) {
                val workoutOptions = listOf(
                    Reminder(
                        id = "workout_afternoon_$today",
                        type = ReminderType.HEALTH_LOG,
                        title = "Afternoon Activity",
                        description = "A short workout now will boost your energy",
                        icon = "fitness_center",
                        priority = ReminderPriority.MEDIUM,
                        actionType = ReminderActionType.LOG_WORKOUT,
                        estimatedDuration = 10
                    ),
                    Reminder(
                        id = "workout_afternoon_$today",
                        type = ReminderType.HEALTH_LOG,
                        title = "Movement Break",
                        description = "Take a break to move your body and refresh",
                        icon = "directions_walk",
                        priority = ReminderPriority.MEDIUM,
                        actionType = ReminderActionType.LOG_WORKOUT,
                        estimatedDuration = 15
                    ),
                    Reminder(
                        id = "workout_afternoon_$today",
                        type = ReminderType.HEALTH_LOG,
                        title = "Exercise Time",
                        description = "Log your workout to track your fitness journey",
                        icon = "sports_soccer",
                        priority = ReminderPriority.MEDIUM,
                        actionType = ReminderActionType.LOG_WORKOUT,
                        estimatedDuration = 20
                    )
                )

                // Only suggest workouts on certain days to avoid daily pressure
                if (dayOfWeek in listOf(2, 4, 6)) { // Tuesday, Thursday, Saturday
                    val workoutIndex = dayOfWeek % workoutOptions.size
                    addReminderIfUnique(workoutOptions[workoutIndex])
                }
            }
        }

        // Evening reminders (after 5 PM) - vary based on day
        if (currentHour >= 17) {
            val dayOfWeek = LocalDate.now().dayOfWeek.value

            // Water reminder (if less than 1500ml) - vary the messaging
            if (totalWater < 1500) {
                val eveningWaterTitles = listOf(
                    "Evening Hydration",
                    "Wind Down with Water",
                    "Final Water Check",
                    "Nighttime Hydration",
                    "Complete Your Water Goal"
                )
                val eveningWaterDescriptions = listOf(
                    "End your day properly hydrated for better recovery",
                    "A final glass of water before bed aids digestion",
                    "Finish strong with your daily hydration goal",
                    "Prepare your body for rest with proper hydration",
                    "Complete your water intake for optimal wellness"
                )

                val waterIndex = (dayOfWeek * 4) % eveningWaterTitles.size
                addReminderIfUnique(
                    Reminder(
                        id = "water_evening_$today",
                        type = ReminderType.HEALTH_LOG,
                        title = eveningWaterTitles[waterIndex],
                        description = eveningWaterDescriptions[waterIndex],
                        icon = "local_drink",
                        priority = ReminderPriority.MEDIUM,
                        actionType = ReminderActionType.LOG_WATER,
                        estimatedDuration = 1
                    )
                )
            }

            // Dinner reminder (if less than 2 meals) - only if not already added above
            if (meals.size < 2 && !(currentHour >= 17 && currentHour < 21)) {
                val dinnerOptions = listOf(
                    Reminder(
                        id = "dinner_$today",
                        type = ReminderType.HEALTH_LOG,
                        title = "Evening Meal",
                        description = "What did you have for dinner? Let's log it!",
                        icon = "restaurant",
                        priority = ReminderPriority.MEDIUM,
                        actionType = ReminderActionType.LOG_MEAL,
                        estimatedDuration = 2
                    ),
                    Reminder(
                        id = "dinner_$today",
                        type = ReminderType.HEALTH_LOG,
                        title = "Dinner Time",
                        description = "Complete your nutrition tracking with dinner",
                        icon = "restaurant",
                        priority = ReminderPriority.MEDIUM,
                        actionType = ReminderActionType.LOG_MEAL,
                        estimatedDuration = 2
                    ),
                    Reminder(
                        id = "dinner_$today",
                        type = ReminderType.HEALTH_LOG,
                        title = "Evening Nutrition",
                        description = "Log your dinner to finish your daily nutrition",
                        icon = "restaurant",
                        priority = ReminderPriority.MEDIUM,
                        actionType = ReminderActionType.LOG_MEAL,
                        estimatedDuration = 2
                    )
                )

                val dinnerIndex = (dayOfWeek + 1) % dinnerOptions.size
                addReminderIfUnique(dinnerOptions[dinnerIndex])
            }

            // Sleep reminder (if not logged yet) - vary messaging and timing
            if (sleepLogs.isEmpty() && currentHour >= 20) { // Only after 8 PM
                val sleepOptions = listOf(
                    Reminder(
                        id = "sleep_$today",
                        type = ReminderType.HEALTH_LOG,
                        title = "Sleep Check",
                        description = "Track your sleep to understand your rest patterns",
                        icon = "bedtime",
                        priority = ReminderPriority.MEDIUM,
                        actionType = ReminderActionType.LOG_SLEEP,
                        estimatedDuration = 2
                    ),
                    Reminder(
                        id = "sleep_$today",
                        type = ReminderType.HEALTH_LOG,
                        title = "Rest Tracking",
                        description = "Log your sleep quality for better wellness insights",
                        icon = "bedtime",
                        priority = ReminderPriority.MEDIUM,
                        actionType = ReminderActionType.LOG_SLEEP,
                        estimatedDuration = 2
                    ),
                    Reminder(
                        id = "sleep_$today",
                        type = ReminderType.HEALTH_LOG,
                        title = "Evening Wind-down",
                        description = "Record your sleep to complete today's health data",
                        icon = "bedtime",
                        priority = ReminderPriority.MEDIUM,
                        actionType = ReminderActionType.LOG_SLEEP,
                        estimatedDuration = 2
                    )
                )

                // Only suggest sleep logging on certain days to avoid daily pressure
                if (dayOfWeek in listOf(1, 3, 5, 7)) { // Monday, Wednesday, Friday, Sunday
                    val sleepIndex = dayOfWeek % sleepOptions.size
                    addReminderIfUnique(sleepOptions[sleepIndex])
                }
            }

            // Journal reminder - vary based on day
            val journalEntries = todayHealthLogs.filterIsInstance<HealthLog.JournalEntry>()
            if (journalEntries.isEmpty() || journalEntries.none { it.isCompleted }) {
                val journalOptions = listOf(
                    Reminder(
                        id = "journal_$today",
                        type = ReminderType.WELLNESS,
                        title = "Evening Reflection",
                        description = "Take a moment to reflect on your day with journaling",
                        icon = "edit_note",
                        priority = ReminderPriority.HIGH,
                        actionType = ReminderActionType.START_JOURNAL,
                        estimatedDuration = 5
                    ),
                    Reminder(
                        id = "journal_$today",
                        type = ReminderType.WELLNESS,
                        title = "Daily Review",
                        description = "What went well today? What could be improved?",
                        icon = "edit_note",
                        priority = ReminderPriority.HIGH,
                        actionType = ReminderActionType.START_JOURNAL,
                        estimatedDuration = 5
                    ),
                    Reminder(
                        id = "journal_$today",
                        type = ReminderType.WELLNESS,
                        title = "Mindful Moment",
                        description = "Spend a few minutes reflecting on your thoughts and feelings",
                        icon = "edit_note",
                        priority = ReminderPriority.HIGH,
                        actionType = ReminderActionType.START_JOURNAL,
                        estimatedDuration = 5
                    ),
                    Reminder(
                        id = "journal_$today",
                        type = ReminderType.WELLNESS,
                        title = "Gratitude Practice",
                        description = "Write down 3 things you're grateful for today",
                        icon = "favorite",
                        priority = ReminderPriority.MEDIUM,
                        actionType = ReminderActionType.START_JOURNAL,
                        estimatedDuration = 3
                    )
                )

                // Only suggest journaling on certain days to avoid daily pressure
                if (dayOfWeek in listOf(1, 4, 7)) { // Monday, Thursday, Sunday
                    val journalIndex = (dayOfWeek * 2) % journalOptions.size
                    addReminderIfUnique(journalOptions[journalIndex])
                }
            }
        }

        // 3. Habit Reminders - ALWAYS include daily habits in Today's Focus
        val todayCompletions = habitCompletions.filter { completion ->
            val completionDate = LocalDate.ofInstant(
                completion.completedAt.toInstant(),
                java.time.ZoneId.systemDefault()
            )
            completionDate == LocalDate.now()
        }
        val completedHabitIds = todayCompletions.map { it.habitId }.toSet()

        // Always add ALL daily habits to Today's Focus (even if completed, so user can see progress)
        habits.filter { it.isActive && it.frequency == HabitFrequency.DAILY }
            .forEach { habit ->
                val priority = when (habit.priority) {
                    HabitPriority.CRITICAL -> ReminderPriority.CRITICAL
                    HabitPriority.HIGH -> ReminderPriority.HIGH
                    HabitPriority.MEDIUM -> ReminderPriority.MEDIUM
                    HabitPriority.LOW -> ReminderPriority.LOW
                }

                // Mark as completed if already done today
                val isCompleted = habit.id in completedHabitIds
                
                addReminderIfUnique(
                    Reminder(
                        id = "habit_${habit.id}_$today",
                        type = ReminderType.HABIT,
                        title = habit.title,
                        description = if (isCompleted) {
                            "✓ Completed! ${habit.description.ifEmpty { "Great job on ${habit.title}!" }}"
                        } else {
                            habit.description.ifEmpty { "Complete your daily habit: ${habit.title}" }
                        },
                        icon = "check_circle",
                        priority = priority,
                        actionType = ReminderActionType.COMPLETE_HABIT,
                        actionData = mapOf("habitId" to habit.id, "habitTitle" to habit.title),
                        estimatedDuration = 5,
                        completedAt = if (isCompleted) System.currentTimeMillis() else null
                    )
                )
            }

        // 4. Weight logging reminder (once per day, morning preferred)
        if (weightLogs.isEmpty() && currentHour < 12) {
            reminders.add(
                Reminder(
                    id = "weight_$today",
                    type = ReminderType.HEALTH_LOG,
                    title = "Log Your Weight",
                    description = "Track your progress by logging your weight",
                    icon = "monitor_weight",
                    priority = ReminderPriority.LOW,
                    actionType = ReminderActionType.LOG_WEIGHT,
                    estimatedDuration = 1
                )
            )
        }

        // Ensure at least 7 tasks every day - add default reminders if needed
        val minReminders = 7
        if (reminders.size < minReminders) {
            val additionalNeeded = minReminders - reminders.size
            
            // Check what types of reminders already exist to avoid duplicates
            val hasMealReminder = reminders.any { it.actionType == ReminderActionType.LOG_MEAL }
            val hasWaterReminder = reminders.any { it.actionType == ReminderActionType.LOG_WATER }
            val hasWorkoutReminder = reminders.any { it.actionType == ReminderActionType.LOG_WORKOUT }
            val hasJournalReminder = reminders.any { it.actionType == ReminderActionType.START_JOURNAL }
            val hasHealthTrackingReminder = reminders.any { it.actionType == ReminderActionType.VIEW_HEALTH_TRACKING }
            val hasWellnessReminder = reminders.any { it.actionType == ReminderActionType.VIEW_WELLNESS }
            val hasHabitsReminder = reminders.any { it.actionType == ReminderActionType.VIEW_HABITS }
            
            // Default reminders to fill up to 7 - only add if that type doesn't already exist
            val defaultReminders = mutableListOf<Reminder>()
            
            if (!hasMealReminder) {
                defaultReminders.add(
                    Reminder(
                        id = "nutrition_tracking_$today",
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
            
            if (!hasWaterReminder) {
                defaultReminders.add(
                    Reminder(
                        id = "hydration_check_$today",
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
            
            if (!hasWorkoutReminder) {
                defaultReminders.add(
                    Reminder(
                        id = "movement_reminder_$today",
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
            
            if (!hasJournalReminder) {
                defaultReminders.add(
                    Reminder(
                        id = "daily_reflection_$today",
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
            
            if (!hasHealthTrackingReminder) {
                defaultReminders.add(
                    Reminder(
                        id = "health_check_$today",
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
            
            if (!hasWellnessReminder) {
                defaultReminders.add(
                    Reminder(
                        id = "wellness_break_$today",
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
            
            if (!hasHabitsReminder) {
                defaultReminders.add(
                    Reminder(
                        id = "habit_review_$today",
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
            
            // Add default reminders that aren't already in the list
            // CRITICAL: Keep adding until we reach 7, even if we need to add duplicates with different IDs
            var added = 0
            var attemptCount = 0
            val maxAttempts = 20 // Prevent infinite loop
            
            while (reminders.size < minReminders && attemptCount < maxAttempts) {
                attemptCount++
                
                // Try to add from defaultReminders first
                for (defaultReminder in defaultReminders) {
                    if (reminders.size >= minReminders) break
                    if (!usedIds.contains(defaultReminder.id)) {
                        addReminderIfUnique(defaultReminder)
                        added++
                    }
                }
                
                // If still not enough, add generic wellness reminders
                if (reminders.size < minReminders) {
                    val genericId = "wellness_generic_${today}_${attemptCount}"
                    if (!usedIds.contains(genericId)) {
                        addReminderIfUnique(
                            Reminder(
                                id = genericId,
                                type = ReminderType.WELLNESS,
                                title = "Wellness Activity",
                                description = "Take time for your wellbeing today",
                                icon = "self_improvement",
                                priority = ReminderPriority.MEDIUM,
                                actionType = ReminderActionType.VIEW_WELLNESS,
                                estimatedDuration = 5
                            )
                        )
                        added++
                    }
                }
            }
            
            Log.d(TAG, "✅ Ensured minimum of $minReminders reminders: Added $added default reminders (total: ${reminders.size})")
        }

        // Sort by priority, but prioritize meal reminders first if they're in the current time window
        // Water reminders and water-related habits should ALWAYS be last (at the end of the day)
        return reminders.sortedWith(compareByDescending<Reminder> { reminder ->
            // Meal reminders during meal times get highest priority
            val isMealReminder = reminder.actionType == ReminderActionType.LOG_MEAL
            val isMealTime = (currentHour >= 6 && currentHour < 10) || 
                            (currentHour >= 11 && currentHour < 14) || 
                            (currentHour >= 17 && currentHour < 21)
            if (isMealReminder && isMealTime) 5 else 0
        }.thenByDescending { reminder ->
            // Water reminders and water-related habits get lowest priority (will be sorted to end)
            val isWaterReminder = reminder.actionType == ReminderActionType.LOG_WATER
            val isWaterHabit = reminder.type == ReminderType.HABIT && 
                              (reminder.title.lowercase().contains("water") || 
                               reminder.title.lowercase().contains("glass") ||
                               reminder.description.lowercase().contains("water") ||
                               reminder.description.lowercase().contains("glass"))
            if (isWaterReminder || isWaterHabit) -1 else 0
        }.thenByDescending { reminder ->
            when (reminder.priority) {
                ReminderPriority.CRITICAL -> 4
                ReminderPriority.HIGH -> 3
                ReminderPriority.MEDIUM -> 2
                ReminderPriority.LOW -> 1
            }
        })
    }

    /**
     * Mark a reminder as completed
     */
    fun markReminderCompleted(reminder: Reminder): Reminder {
        return reminder.copy(completedAt = System.currentTimeMillis())
    }
}

