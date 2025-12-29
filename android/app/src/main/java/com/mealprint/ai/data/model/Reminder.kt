package com.coachie.app.data.model

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

/**
 * Represents a reminder or challenge that can be shown to the user
 */
data class Reminder(
    val id: String,
    val type: ReminderType,
    val title: String,
    val description: String,
    val icon: String, // Icon name for display
    val priority: ReminderPriority = ReminderPriority.MEDIUM,
    val actionType: ReminderActionType,
    val actionData: Map<String, Any> = emptyMap(), // Additional data for the action
    val estimatedDuration: Int? = null, // Estimated duration in minutes
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
) {
    val isCompleted: Boolean
        get() = completedAt != null
}

enum class ReminderType {
    HEALTH_LOG,      // Log meals, water, weight, etc.
    HABIT,           // Complete a habit
    WELLNESS,        // Meditation, journaling, mindfulness
    MINDFULNESS,     // 3-min reset
    CHALLENGE        // Special challenges
}

enum class ReminderPriority {
    LOW, MEDIUM, HIGH, CRITICAL
}

enum class ReminderActionType {
    LOG_MEAL,
    LOG_WATER,
    LOG_WEIGHT,
    LOG_SLEEP,
    LOG_WORKOUT,
    LOG_SUPPLEMENT,
    COMPLETE_HABIT,
    START_MEDITATION,
    START_JOURNAL,
    START_MINDFULNESS,
    VIEW_HABITS,
    VIEW_HEALTH_TRACKING,
    VIEW_WELLNESS
}

