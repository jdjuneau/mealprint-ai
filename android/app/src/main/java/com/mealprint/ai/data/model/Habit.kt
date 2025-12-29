package com.coachie.app.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Habit(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val category: HabitCategory = HabitCategory.HEALTH,
    val frequency: HabitFrequency = HabitFrequency.DAILY,
    val targetValue: Int = 1, // e.g., 8 hours sleep, 10k steps, etc.
    val unit: String = "", // e.g., "hours", "steps", "glasses"
    val isActive: Boolean = true,
    val priority: HabitPriority = HabitPriority.MEDIUM,
    val reminderTime: String? = null, // HH:MM format
    val reminderDays: List<String> = emptyList(), // ["monday", "tuesday", etc.]
    val color: String = "#4CAF50", // hex color
    val icon: String = "target", // icon name
    val streakCount: Int = 0,
    val longestStreak: Int = 0,
    val totalCompletions: Int = 0,
    val successRate: Double = 0.0, // percentage 0-100
    val lastCompletedAt: Date? = null,
    val createdAt: Date = Date(),
    @ServerTimestamp
    val updatedAt: Date? = null
)

enum class HabitCategory {
    HEALTH, FITNESS, NUTRITION, SLEEP, MENTAL_HEALTH,
    PRODUCTIVITY, LEARNING, SOCIAL, CREATIVE, FINANCIAL,
    ENVIRONMENTAL, SPIRITUAL, OTHER
}

enum class HabitFrequency {
    DAILY, WEEKLY, MONTHLY, CUSTOM
}

enum class HabitPriority {
    LOW, MEDIUM, HIGH, CRITICAL
}

data class HabitCompletion(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val habitId: String = "",
    val habitTitle: String = "",
    val completedAt: Date = Date(),
    val value: Int = 1, // actual value achieved (e.g., 8000 steps)
    val notes: String? = null,
    val mood: Int? = null, // 1-5 scale
    val difficulty: Int? = null, // 1-5 scale
    val createdAt: Date = Date()
)

data class HabitMiss(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val habitId: String = "",
    val habitTitle: String = "",
    val missedAt: Date = Date(),
    val reason: String? = null,
    val plannedValue: Int = 1,
    val createdAt: Date = Date()
)

// Extended UserProfile with habit data
data class UserHabitProfile(
    val fourTendencies: FourTendenciesResult? = null,
    val rewardPreferences: List<RewardType> = emptyList(),
    val keystoneHabits: List<String> = emptyList(), // habit IDs
    val biggestFrictions: List<String> = emptyList(),
    val habitGoals: Map<String, Any> = emptyMap(), // flexible goals storage
    val profileCompleted: Boolean = false,
    val profileCompletionDate: Date? = null,
    val updatedAt: Date = Date()
)

enum class FourTendencies {
    UPHOLDER, QUESTIONER, OBLIGER, REBEL
}

data class FourTendenciesResult(
    val tendency: FourTendencies,
    val scores: Map<String, Int> = emptyMap(), // tendency scores
    val assessedAt: Date = Date()
)

enum class RewardType {
    ACHIEVEMENT_BADGE, SOCIAL_RECOGNITION, PERSONAL_GROWTH,
    MATERIAL_REWARD, EXPERIENCE_REWARD, COMPETITION, NONE
}
