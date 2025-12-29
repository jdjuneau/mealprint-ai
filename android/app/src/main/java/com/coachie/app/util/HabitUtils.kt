package com.coachie.app.util

import com.coachie.app.data.model.Habit
import com.coachie.app.data.model.HabitCategory

/**
 * Utility functions for habit-related operations
 */
object HabitUtils {
    
    /**
     * Determines if a habit is automatically tracked based on activity logging.
     * 
     * Auto-tracked habits are those that can be completed automatically when
     * related activities are logged (meals, water, workouts, sleep, breathing exercises).
     * 
     * Manual completion habits include:
     * - Avoidance habits (e.g., "No social media before bed", "No phone before bed")
     * - Habits that require user interaction without logging (e.g., reading, meditation without timer)
     * - Habits that don't match any auto-completion triggers
     */
    fun isHabitAutoTracked(habit: Habit): Boolean {
        val titleLower = habit.title.lowercase()
        val unitLower = habit.unit.lowercase()
        
        // Avoidance habits are never auto-tracked
        if (isAvoidanceHabit(habit)) {
            return false
        }
        
        // Check if habit matches auto-completion triggers
        return when (habit.category) {
            HabitCategory.NUTRITION -> {
                // Nutrition habits are auto-tracked when meals are logged
                titleLower.contains("protein") ||
                titleLower.contains("meal") ||
                titleLower.contains("eat") ||
                habit.category == HabitCategory.NUTRITION
            }
            HabitCategory.HEALTH -> {
                // Water habits are auto-tracked when water is logged
                titleLower.contains("water") ||
                titleLower.contains("hydrate") ||
                titleLower.contains("drink")
            }
            HabitCategory.FITNESS -> {
                // Workout habits are auto-tracked when workouts are logged
                titleLower.contains("workout") ||
                titleLower.contains("exercise") ||
                titleLower.contains("gym") ||
                titleLower.contains("run") ||
                titleLower.contains("walk")
            }
            HabitCategory.SLEEP -> {
                // Sleep duration habits are auto-tracked when sleep is logged
                // But avoidance habits (like "no social media before bed") are not
                (titleLower.contains("sleep") && unitLower.contains("hour")) ||
                (titleLower.contains("bed") && !isAvoidanceHabit(habit))
            }
            HabitCategory.MENTAL_HEALTH -> {
                // Breathing habits are auto-tracked when breathing exercises are logged
                titleLower.contains("breathing") ||
                titleLower.contains("breath")
            }
            else -> {
                // Other categories are not auto-tracked by default
                false
            }
        }
    }
    
    /**
     * Determines if a habit is an avoidance habit (not doing something)
     */
    fun isAvoidanceHabit(habit: Habit): Boolean {
        val titleLower = habit.title.lowercase()
        return titleLower.contains("no ") ||
               titleLower.contains("avoid") ||
               titleLower.contains("don't") ||
               titleLower.contains("dont") ||
               titleLower.contains("stop") ||
               titleLower.contains("quit") ||
               titleLower.startsWith("no ")
    }
    
    /**
     * Gets a user-friendly description of how the habit is tracked
     */
    fun getHabitTrackingDescription(habit: Habit): String {
        return if (isHabitAutoTracked(habit)) {
            "Auto-tracked"
        } else {
            "Tap to complete"
        }
    }
    
    /**
     * Determines if a habit should have reminders scheduled
     * Manual completion habits should have reminders
     */
    fun shouldScheduleReminders(habit: Habit): Boolean {
        return !isHabitAutoTracked(habit) && habit.isActive
    }
    
    /**
     * Gets the default reminder time for a habit based on its type
     */
    fun getDefaultReminderTime(habit: Habit): Pair<Int, Int>? {
        val titleLower = habit.title.lowercase()
        
        return when {
            // Bedtime habits should remind 1 hour before typical bedtime (9 PM reminder for 10 PM bed)
            titleLower.contains("before bed") || titleLower.contains("bedtime") -> {
                Pair(21, 0) // 9:00 PM
            }
            // Morning habits
            titleLower.contains("morning") -> {
                Pair(8, 0) // 8:00 AM
            }
            // Afternoon habits
            titleLower.contains("afternoon") -> {
                Pair(14, 0) // 2:00 PM
            }
            // Evening habits
            titleLower.contains("evening") -> {
                Pair(18, 0) // 6:00 PM
            }
            // Reading habits - remind in evening
            titleLower.contains("read") -> {
                Pair(19, 0) // 7:00 PM
            }
            // Meditation habits - remind in morning or evening
            titleLower.contains("meditation") -> {
                Pair(8, 0) // 8:00 AM
            }
            // Social media break - remind in afternoon
            titleLower.contains("social media") -> {
                Pair(15, 0) // 3:00 PM
            }
            else -> {
                // Default: remind at 8 AM
                Pair(8, 0)
            }
        }
    }
}

