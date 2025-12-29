package com.coachie.app.data.model

import com.google.firebase.firestore.PropertyName
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.jvm.JvmSynthetic

/**
 * Streak data class representing a user's activity streak information.
 * Stored in Firestore at: users/{uid}/streaks/current
 */
data class Streak(
    @PropertyName("uid")
    val uid: String = "",

    @PropertyName("currentStreak")
    val currentStreak: Int = 0, // Current consecutive days logged

    @PropertyName("longestStreak")
    val longestStreak: Int = 0, // Personal best streak

    @PropertyName("lastLogDate")
    val lastLogDate: String = "", // Last date user logged (yyyy-MM-dd)

    @PropertyName("streakStartDate")
    val streakStartDate: String = "", // When current streak started (yyyy-MM-dd)

    @PropertyName("totalLogs")
    val totalLogs: Int = 0, // Total number of days logged

    @PropertyName("lastUpdated")
    val lastUpdated: Long = System.currentTimeMillis()
) {

    /**
     * Check if the streak is still active (last log MUST be TODAY - daily streak requires daily logging)
     */
    @get:JvmSynthetic
    val isActive: Boolean
        get() {
            if (currentStreak == 0 || lastLogDate.isEmpty()) return false

            val today = LocalDate.now()
            val lastLog = try {
                LocalDate.parse(lastLogDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            } catch (e: Exception) {
                return false // Invalid date format
            }

            // DAILY STREAK: Must log EVERY DAY - only active if last log was TODAY
            return lastLog == today
        }

    /**
     * Get streak status message
     */
    @get:JvmSynthetic
    val statusMessage: String
        get() = when {
            currentStreak == 0 -> "Start your streak today!"
            currentStreak == 1 -> "1 day logged. Keep it up!"
            currentStreak < 7 -> "$currentStreak days in a row!"
            else -> "$currentStreak day streak! 🔥"
        }

    /**
     * Calculate days since last log
     */
    @get:JvmSynthetic
    val daysSinceLastLog: Int
        get() {
            if (lastLogDate.isEmpty()) return Int.MAX_VALUE

            val lastLog = LocalDate.parse(lastLogDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val today = LocalDate.now()

            return today.toEpochDay().toInt() - lastLog.toEpochDay().toInt()
        }

    companion object {
        /**
         * Create a new streak for a user
         */
        fun create(uid: String): Streak {
            return Streak(
                uid = uid,
                currentStreak = 0,
                longestStreak = 0,
                totalLogs = 0,
                lastUpdated = System.currentTimeMillis()
            )
        }

        /**
         * Create streak with initial log
         */
        fun createWithFirstLog(uid: String, date: String): Streak {
            return Streak(
                uid = uid,
                currentStreak = 1,
                longestStreak = 1,
                lastLogDate = date,
                streakStartDate = date,
                totalLogs = 1,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }
}

/**
 * Badge data class representing user achievements.
 * Stored in Firestore at: users/{uid}/badges/{badgeId}
 */
data class Badge(
    @PropertyName("id")
    val id: String = "",

    @PropertyName("uid")
    val uid: String = "",

    @PropertyName("type")
    val type: String = "", // Badge type constant

    @PropertyName("name")
    val name: String = "",

    @PropertyName("description")
    val description: String = "",

    @PropertyName("icon")
    val icon: String = "", // Icon resource name

    @PropertyName("earnedDate")
    val earnedDate: Long = System.currentTimeMillis(),

    @PropertyName("isNew")
    val isNew: Boolean = true // For highlighting new badges
) {

    /**
     * Get formatted earned date
     */
    val earnedDateFormatted: String
        get() {
            val date = java.util.Date(earnedDate)
            val format = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
            return format.format(date)
        }

    companion object {
        // Badge type constants
        const val TYPE_THREE_DAY_HERO = "three_day_hero"
        const val TYPE_WEEK_WARRIOR = "week_warrior"
        const val TYPE_SCAN_STAR = "scan_star"

        // Badge definitions
        val THREE_DAY_HERO = Badge(
            id = TYPE_THREE_DAY_HERO,
            type = TYPE_THREE_DAY_HERO,
            name = "3-Day Hero",
            description = "Logged for 3 days in a row!",
            icon = "badge_hero"
        )

        val WEEK_WARRIOR = Badge(
            id = TYPE_WEEK_WARRIOR,
            type = TYPE_WEEK_WARRIOR,
            name = "Week Warrior",
            description = "Maintained a 7-day streak!",
            icon = "badge_warrior"
        )

        val SCAN_STAR = Badge(
            id = TYPE_SCAN_STAR,
            type = TYPE_SCAN_STAR,
            name = "Scan Star",
            description = "Completed 5 body scans!",
            icon = "badge_star"
        )

        /**
         * Get all available badges
         */
        fun getAllBadges(): List<Badge> {
            return listOf(THREE_DAY_HERO, WEEK_WARRIOR, SCAN_STAR)
        }

        /**
         * Create earned badge for user
         */
        fun createEarnedBadge(uid: String, badgeType: String): Badge {
            val template = when (badgeType) {
                TYPE_THREE_DAY_HERO -> THREE_DAY_HERO
                TYPE_WEEK_WARRIOR -> WEEK_WARRIOR
                TYPE_SCAN_STAR -> SCAN_STAR
                else -> THREE_DAY_HERO
            }

            return template.copy(
                uid = uid,
                earnedDate = System.currentTimeMillis(),
                isNew = true
            )
        }
    }
}

/**
 * Achievement progress data class for tracking badge progress
 */
data class AchievementProgress(
    val badge: Badge,
    val currentProgress: Int,
    val targetProgress: Int,
    val isCompleted: Boolean = false
) {

    val progressPercentage: Float
        get() = if (targetProgress > 0) (currentProgress.toFloat() / targetProgress) else 0f

    val progressText: String
        get() = "$currentProgress / $targetProgress"
}
