package com.coachie.app.data.model

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import kotlin.jvm.JvmSynthetic

/**
 * Daily log data class representing a user's daily fitness and wellness data.
 * Stored in Firestore at: logs/{uid}/{date}
 */
@IgnoreExtraProperties
data class DailyLog(
    @PropertyName("uid")
    val uid: String = "",

    @PropertyName("date")
    val date: String = "", // Format: "yyyy-MM-dd"

    @PropertyName("weight")
    val weight: Double? = null, // Weight in kg

    @PropertyName("steps")
    val steps: Int? = null, // Step count

    @PropertyName("caloriesBurned")
    val caloriesBurned: Int? = null, // Calories burned

    @PropertyName("water")
    val water: Int? = null, // Water intake in ml

    @PropertyName("mood")
    val mood: Int? = null, // Mood rating 1-5 (1=very bad, 5=excellent)

    @PropertyName("energy")
    val energy: Int? = null, // Energy level 1-5 (1=very low, 5=very high)

    @PropertyName("notes")
    val notes: String? = null, // Optional daily notes

    @PropertyName("micronutrientExtras")
    val micronutrientExtras: Map<String, Double> = emptyMap(),

    @PropertyName("micronutrientChecklist")
    val micronutrientChecklist: Map<String, Boolean> = emptyMap(),

    @PropertyName("createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @PropertyName("updatedAt")
    val updatedAt: Long = System.currentTimeMillis()
) {

    /**
     * Check if this log has any meaningful data
     */
    @get:JvmSynthetic
    val hasData: Boolean
        get() = weight != null || steps != null || caloriesBurned != null || water != null || mood != null ||
                energy != null || !notes.isNullOrBlank() ||
                micronutrientExtras.isNotEmpty() || micronutrientChecklist.values.any { it }

    /**
     * Get mood description based on rating
     */
    @get:JvmSynthetic
    val moodDescription: String?
        get() = when (mood) {
            1 -> "Very Bad"
            2 -> "Bad"
            3 -> "Okay"
            4 -> "Good"
            5 -> "Excellent"
            else -> null
        }

    /**
     * Get water intake in liters for display
     */
    @get:JvmSynthetic
    val waterInLiters: Double?
        get() = water?.div(1000.0)

    /**
     * Calculate step goal progress (assuming 10,000 steps as goal)
     */
    @get:JvmSynthetic
    val stepGoalProgress: Double
        get() = (steps ?: 0).toDouble() / 10000.0

    companion object {
        // Mood constants
        const val MOOD_VERY_BAD = 1
        const val MOOD_BAD = 2
        const val MOOD_OKAY = 3
        const val MOOD_GOOD = 4
        const val MOOD_EXCELLENT = 5

        // Water intake constants (ml)
        const val WATER_GLASS = 250 // Standard glass
        const val WATER_BOTTLE = 500 // Standard bottle
        const val WATER_GOAL_DAILY = 2000 // Daily goal

        // Step goal
        const val STEP_GOAL_DAILY = 10000

        /**
         * Create a DailyLog for today
         */
        fun createToday(uid: String): DailyLog {
            val today = java.time.LocalDate.now().toString()
            return DailyLog(uid = uid, date = today)
        }

        /**
         * Create a DailyLog for a specific date
         */
        fun createForDate(uid: String, date: String): DailyLog {
            return DailyLog(uid = uid, date = date)
        }
    }
}
