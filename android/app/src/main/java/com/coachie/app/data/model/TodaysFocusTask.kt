package com.coachie.app.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Represents a task in Today's Focus
 * Each day starts with 7-9 tasks that can be completed
 */
data class TodaysFocusTask(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val date: String = "", // ISO date string (yyyy-MM-dd)
    val type: TodaysFocusTaskType,
    val title: String,
    val description: String,
    val actionType: ReminderActionType,
    val actionData: Map<String, Any> = emptyMap(),
    val estimatedDuration: Int? = null, // minutes
    val priority: Int = 0, // 0 = normal, 1 = high
    val completedAt: Date? = null,
    val createdAt: Date = Date(),
    @ServerTimestamp
    val updatedAt: Date? = null
) {
    val isCompleted: Boolean
        get() = completedAt != null
}

enum class TodaysFocusTaskType {
    HEALTH_LOG,      // Log meals, water, weight, sleep, workout, supplement
    HABIT,           // Complete a habit
    WELLNESS         // Meditation, journaling, breathing exercises
}

