package com.coachie.app.data

import com.coachie.app.data.model.Badge
import com.coachie.app.data.model.Streak
import com.coachie.app.data.model.AchievementProgress
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max

/**
 * Service for managing user streaks and achievements
 */
class StreakService(private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * Update user's streak after logging an activity
     */
    suspend fun updateStreakAfterLog(uid: String, logDate: String): Result<Streak> {
        return try {
            android.util.Log.i("StreakService", "=========================================")
            android.util.Log.i("StreakService", "🔄 UPDATE STREAK AFTER LOG")
            android.util.Log.i("StreakService", "User: $uid, LogDate: $logDate")
            android.util.Log.i("StreakService", "=========================================")
            
            // Get current streak
            val currentStreak = getCurrentStreak(uid)
            android.util.Log.i("StreakService", "Current streak BEFORE update: currentStreak=${currentStreak.currentStreak}, lastLogDate=${currentStreak.lastLogDate}, totalLogs=${currentStreak.totalLogs}")

            // Calculate new streak
            val updatedStreak = calculateUpdatedStreak(currentStreak, logDate)
            android.util.Log.i("StreakService", "Calculated NEW streak: currentStreak=${updatedStreak.currentStreak}, lastLogDate=${updatedStreak.lastLogDate}, totalLogs=${updatedStreak.totalLogs}")

            // Save updated streak
            saveStreak(updatedStreak)
            android.util.Log.i("StreakService", "✅✅✅ STREAK SAVED TO FIRESTORE: currentStreak=${updatedStreak.currentStreak} ✅✅✅")

            // Verify it was saved by reading it back
            val verifyStreak = getCurrentStreak(uid)
            if (verifyStreak.currentStreak == updatedStreak.currentStreak) {
                android.util.Log.i("StreakService", "✅ VERIFICATION PASSED: Streak correctly saved (currentStreak=${verifyStreak.currentStreak})")
            } else {
                android.util.Log.e("StreakService", "❌ VERIFICATION FAILED: Expected currentStreak=${updatedStreak.currentStreak}, but got ${verifyStreak.currentStreak}")
            }

            // Check for new badges
            checkAndAwardBadges(uid, updatedStreak)

            android.util.Log.i("StreakService", "=========================================")
            Result.success(updatedStreak)
        } catch (e: Exception) {
            android.util.Log.e("StreakService", "❌ ERROR updating streak after log", e)
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Get current streak for user
     * CRITICAL: Always returns a fresh streak with currentStreak=0 for new accounts
     */
    private suspend fun getCurrentStreak(uid: String): Streak {
        return try {
            val doc = firestore.collection("users")
                .document(uid)
                .collection("streaks")
                .document("current")
                .get()
                .await()

            if (doc.exists()) {
                val streak = doc.toObject(Streak::class.java)
                // CRITICAL: If streak has bad data (currentStreak > 0 but totalLogs == 0 or lastLogDate is blank),
                // reset it to a fresh streak
                if (streak != null) {
                    if ((streak.currentStreak > 0 && streak.totalLogs == 0) || 
                        (streak.currentStreak > 0 && streak.lastLogDate.isBlank())) {
                        android.util.Log.w("StreakService", "Bad streak data detected: currentStreak=${streak.currentStreak}, totalLogs=${streak.totalLogs}, lastLogDate='${streak.lastLogDate}' - resetting to 0")
                        return Streak.create(uid)
                    }
                    streak
                } else {
                    Streak.create(uid)
                }
            } else {
                Streak.create(uid)
            }
        } catch (e: Exception) {
            android.util.Log.e("StreakService", "Error getting streak, returning fresh streak", e)
            Streak.create(uid)
        }
    }

    /**
     * Calculate updated streak based on log date
     */
    internal fun calculateUpdatedStreak(currentStreak: Streak, logDate: String): Streak {
        val today = LocalDate.now().format(dateFormatter)
        val yesterday = LocalDate.now().minusDays(1).format(dateFormatter)

        android.util.Log.i("StreakService", "=== CALCULATING UPDATED STREAK ===")
        android.util.Log.i("StreakService", "Input: currentStreak=${currentStreak.currentStreak}, lastLogDate='${currentStreak.lastLogDate}', totalLogs=${currentStreak.totalLogs}")
        android.util.Log.i("StreakService", "LogDate: '$logDate', Today: '$today', Yesterday: '$yesterday'")

        return when {
            // First log ever
            currentStreak.totalLogs == 0 -> {
                val firstStreak = Streak.createWithFirstLog(currentStreak.uid, logDate)
                android.util.Log.i("StreakService", "✅✅✅ FIRST LOG: Setting streak to 1, lastLogDate=$logDate ✅✅✅")
                firstStreak
            }

            // Logging today (already logged today)
            currentStreak.lastLogDate == today -> {
                // CRITICAL FIX: If streak is 0 but we're logging today, ALWAYS set to 1
                // This fixes the bug where streak gets stuck at 0 even when user logs
                if (currentStreak.currentStreak == 0) {
                    android.util.Log.w("StreakService", "⚠️ BUG FIX: Streak is 0 but lastLogDate is today - fixing to 1")
                    android.util.Log.w("StreakService", "   This happens when validateAndResetStreakIfNeeded resets streak incorrectly")
                    val fixedStreak = currentStreak.copy(
                        currentStreak = 1, // ALWAYS set to 1 if logging today
                        longestStreak = max(currentStreak.longestStreak, 1),
                        lastUpdated = System.currentTimeMillis()
                    )
                    android.util.Log.i("StreakService", "✅✅✅ FIXED STREAK: 0 -> 1 (user is logging today) ✅✅✅")
                    fixedStreak
                } else {
                    android.util.Log.i("StreakService", "Already logged today - keeping streak at ${currentStreak.currentStreak}")
                    currentStreak.copy(lastUpdated = System.currentTimeMillis())
                }
            }

            // Logging today after logging yesterday (continuing streak)
            // CRITICAL: If streak was reset to 0 incorrectly, we'll recalculate in getUserStreak
            // For now, use a simple heuristic: if totalLogs suggests more days, use that
            currentStreak.lastLogDate == yesterday -> {
                val newStreakCount = if (currentStreak.currentStreak == 0 && currentStreak.totalLogs > 1) {
                    // Streak was reset to 0 but we have history
                    // Use a heuristic: if totalLogs is high, estimate streak from that
                    // The actual recalculation will happen in getUserStreak (suspend function)
                    val estimatedStreak = minOf(currentStreak.totalLogs, 30) // Cap at 30 for safety
                    android.util.Log.w("StreakService", "⚠️ Streak was reset to 0 but we're continuing - estimating from totalLogs: $estimatedStreak")
                    android.util.Log.w("StreakService", "   Full recalculation will happen in getUserStreak")
                    estimatedStreak + 1 // Add today's log
                } else if (currentStreak.totalLogs == 0) {
                    // New account - first log should be 1, not 2
                    android.util.Log.w("StreakService", "⚠️ BAD DATA: New account but lastLogDate=yesterday, fixing to 1")
                    1
                } else if (currentStreak.currentStreak == 0 && currentStreak.totalLogs == 1) {
                    // Second log ever: yesterday + today = 2
                    android.util.Log.i("StreakService", "Second log ever: setting to 2")
                    2
                } else if (currentStreak.currentStreak == 0) {
                    // Shouldn't happen, but default to 2
                    android.util.Log.w("StreakService", "Unexpected: currentStreak=0, totalLogs=${currentStreak.totalLogs}, defaulting to 2")
                    2
                } else if (currentStreak.currentStreak > 0) {
                    // Normal case: continuing existing streak - just increment
                    currentStreak.currentStreak + 1
                } else {
                    // Edge case: currentStreak is 0 but we're continuing from yesterday
                    // This shouldn't happen, but if it does, start at 2 (yesterday + today)
                    android.util.Log.w("StreakService", "Edge case: currentStreak=0 but lastLogDate=yesterday, setting to 2")
                    2
                }
                android.util.Log.i("StreakService", "Continuing streak: ${currentStreak.currentStreak} -> $newStreakCount (logged yesterday and today)")
                val updatedStreak = currentStreak.copy(
                    currentStreak = newStreakCount,
                    longestStreak = max(currentStreak.longestStreak, newStreakCount),
                    lastLogDate = logDate,
                    totalLogs = currentStreak.totalLogs + 1,
                    lastUpdated = System.currentTimeMillis()
                )
                android.util.Log.i("StreakService", "✅✅✅ STREAK UPDATED: currentStreak=${updatedStreak.currentStreak}, lastLogDate=${updatedStreak.lastLogDate} ✅✅✅")
                updatedStreak
            }

            // Gap in logging (streak broken) - START NEW STREAK AT 1
            else -> {
                android.util.Log.w("StreakService", "Gap detected: lastLogDate='${currentStreak.lastLogDate}' is not today or yesterday")
                android.util.Log.w("StreakService", "Starting new streak at 1 (was ${currentStreak.currentStreak})")
                val newStreak = currentStreak.copy(
                    currentStreak = 1,  // CRITICAL: Always start at 1, never 0
                    lastLogDate = logDate,
                    streakStartDate = logDate,
                    totalLogs = currentStreak.totalLogs + 1,
                    lastUpdated = System.currentTimeMillis()
                )
                android.util.Log.i("StreakService", "✅✅✅ NEW STREAK STARTED: currentStreak=1, lastLogDate=$logDate ✅✅✅")
                newStreak
            }
        }
    }

    /**
     * Save streak to Firestore
     */
    private suspend fun saveStreak(streak: Streak) {
        firestore.collection("users")
            .document(streak.uid)
            .collection("streaks")
            .document("current")
            .set(streak)
            .await()
    }

    /**
     * Check and award badges based on achievements
     */
    private suspend fun checkAndAwardBadges(uid: String, streak: Streak) {
        try {
            // Check 3-day hero badge
            if (streak.currentStreak >= 3) {
                awardBadgeIfNotExists(uid, Badge.TYPE_THREE_DAY_HERO)
            }

            // Check week warrior badge
            if (streak.currentStreak >= 7) {
                awardBadgeIfNotExists(uid, Badge.TYPE_WEEK_WARRIOR)
            }

            // Check scan star badge (count body scans)
            val scanCount = getUserScanCount(uid)
            if (scanCount >= 5) {
                awardBadgeIfNotExists(uid, Badge.TYPE_SCAN_STAR)
            }
        } catch (e: Exception) {
            // Log error but don't fail the streak update
            println("Error checking badges: ${e.message}")
        }
    }

    /**
     * Award badge if user doesn't already have it
     */
    private suspend fun awardBadgeIfNotExists(uid: String, badgeType: String) {
        try {
            val badgeDoc = firestore.collection("users")
                .document(uid)
                .collection("badges")
                .document(badgeType)
                .get()
                .await()

            if (!badgeDoc.exists()) {
                val badge = Badge.createEarnedBadge(uid, badgeType)
                firestore.collection("users")
                    .document(uid)
                    .collection("badges")
                    .document(badgeType)
                    .set(badge)
                    .await()
            }
        } catch (e: Exception) {
            println("Error awarding badge $badgeType: ${e.message}")
        }
    }

    /**
     * Get user's body scan count
     */
    private suspend fun getUserScanCount(uid: String): Int {
        return try {
            val scans = firestore.collection("scans")
                .whereEqualTo("uid", uid)
                .get()
                .await()

            scans.size()
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Get user's current streak - validates and resets if days were missed
     * CRITICAL: Only validates, does NOT reset if there's activity today
     */
    suspend fun getUserStreak(uid: String): Result<Streak> {
        return try {
            android.util.Log.i("StreakService", "=========================================")
            android.util.Log.i("StreakService", "=== GETTING USER STREAK ===")
            android.util.Log.i("StreakService", "=========================================")
            
            var streak = getCurrentStreak(uid)
            android.util.Log.i("StreakService", "Retrieved from Firestore: currentStreak=${streak.currentStreak}, lastLogDate=${streak.lastLogDate}")
            
            // CRITICAL: Only validate if there's NO activity today - don't reset if user has logged
            val today = LocalDate.now().format(dateFormatter)
            val hasActivityToday = hasAnyActivityOnDate(uid, today)
            
            // ALWAYS recalculate if streak is low - force recalculation from actual logs
            // This fixes cases where streak gets stuck at 1-2 despite consecutive logging
            if (streak.currentStreak > 0 && streak.currentStreak < 10) {
                android.util.Log.w("StreakService", "🔄 FORCING STREAK RECALCULATION (currentStreak=${streak.currentStreak})...")
                val recalculatedResult = recalculateStreakFromHistory(uid)
                if (recalculatedResult.isSuccess) {
                    val recalculated = recalculatedResult.getOrNull()
                    if (recalculated != null) {
                        if (recalculated.currentStreak != streak.currentStreak) {
                            android.util.Log.i("StreakService", "✅✅✅ STREAK CORRECTED: ${streak.currentStreak} -> ${recalculated.currentStreak} days ✅✅✅")
                        } else {
                            android.util.Log.d("StreakService", "Streak confirmed correct: ${recalculated.currentStreak} days")
                        }
                        streak = recalculated
                        // Always save the recalculated streak to ensure it's persisted
                        saveStreak(streak)
                    }
                } else {
                    android.util.Log.e("StreakService", "❌ Recalculation failed: ${recalculatedResult.exceptionOrNull()?.message}")
                    recalculatedResult.exceptionOrNull()?.printStackTrace()
                }
            }
            
            if (hasActivityToday) {
                android.util.Log.i("StreakService", "✅ Activity found today - skipping validation (streak should be updated via updateStreakAfterLog)")
                // If there's activity today, just return the streak as-is
                // The streak will be properly updated when updateStreakAfterLog is called
            } else {
                // Only validate/reset if there's no activity today
                android.util.Log.i("StreakService", "No activity today - validating streak...")
                streak = validateAndResetStreakIfNeeded(uid, streak)
            }
            
            android.util.Log.i("StreakService", "=========================================")
            android.util.Log.i("StreakService", "Final streak: currentStreak=${streak.currentStreak}, lastLogDate=${streak.lastLogDate}")
            android.util.Log.i("StreakService", "=========================================")
            
            Result.success(streak)
        } catch (e: Exception) {
            android.util.Log.e("StreakService", "ERROR in getUserStreak", e)
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * Recalculate streak from actual log history
     * This fixes cases where the streak was incorrectly reset or miscalculated
     */
    suspend fun recalculateStreakFromHistory(uid: String): Result<Streak> {
        return try {
            android.util.Log.i("StreakService", "=========================================")
            android.util.Log.i("StreakService", "🔄 RECALCULATING STREAK FROM HISTORY")
            android.util.Log.i("StreakService", "=========================================")
            
            val today = LocalDate.now()
            val todayString = today.format(dateFormatter)
            
            // CRITICAL: Streak can only exist if there's activity TODAY
            // If no activity today, streak must be 0 (broken)
            val hasActivityToday = hasAnyActivityOnDate(uid, todayString)
            if (!hasActivityToday) {
                android.util.Log.w("StreakService", "⚠️ No activity TODAY - streak must be 0 (broken)")
                val resetStreak = getCurrentStreak(uid).copy(
                    currentStreak = 0,
                    lastUpdated = System.currentTimeMillis()
                )
                saveStreak(resetStreak)
                return Result.success(resetStreak)
            }
            
            var currentStreak = 0
            var lastLogDate: LocalDate? = null
            var streakStartDate: LocalDate? = null
            
            // Check backwards from today to find consecutive days
            // CRITICAL: Only count if we have activity TODAY (already checked above)
            var checkDate = today
            var consecutiveDays = 0
            
            // Check up to 365 days back
            for (i in 0..364) {
                val dateStr = checkDate.format(dateFormatter)
                val hasActivity = hasAnyActivityOnDate(uid, dateStr)
                
                if (hasActivity) {
                    consecutiveDays++
                    if (lastLogDate == null) {
                        lastLogDate = checkDate
                    }
                    if (streakStartDate == null) {
                        streakStartDate = checkDate
                    }
                } else {
                    // Gap found - stop counting
                    break
                }
                
                checkDate = checkDate.minusDays(1)
            }
            
            currentStreak = consecutiveDays
            val longestStreak = max(currentStreak, getCurrentStreak(uid).longestStreak)
            
            val recalculatedStreak = Streak(
                uid = uid,
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                lastLogDate = lastLogDate?.format(dateFormatter) ?: "",
                streakStartDate = streakStartDate?.format(dateFormatter) ?: "",
                totalLogs = getCurrentStreak(uid).totalLogs, // Keep existing totalLogs
                lastUpdated = System.currentTimeMillis()
            )
            
            android.util.Log.i("StreakService", "✅✅✅ RECALCULATED STREAK: currentStreak=$currentStreak, lastLogDate=${recalculatedStreak.lastLogDate} ✅✅✅")
            
            // Save the recalculated streak
            saveStreak(recalculatedStreak)
            
            Result.success(recalculatedStreak)
        } catch (e: Exception) {
            android.util.Log.e("StreakService", "Error recalculating streak from history", e)
            Result.failure(e)
        }
    }
    
    /**
     * Validate streak and reset to 0 if user missed even ONE day
     * DAILY STREAK means you must use the app EVERY DAY - no exceptions
     * CRITICAL: This function ALWAYS checks for activity TODAY and resets if missing
     */
    private suspend fun validateAndResetStreakIfNeeded(uid: String, streak: Streak): Streak {
        val today = LocalDate.now()
        val todayString = today.format(dateFormatter)
        
        android.util.Log.i("StreakService", "=========================================")
        android.util.Log.i("StreakService", "VALIDATING STREAK FOR TODAY ($todayString)")
        android.util.Log.i("StreakService", "Current streak: ${streak.currentStreak}, lastLogDate: ${streak.lastLogDate}")
        android.util.Log.i("StreakService", "=========================================")
        
        // CRITICAL: ALWAYS check if there's activity TODAY first - this is the ONLY way a streak can continue
        android.util.Log.i("StreakService", "Checking for activity TODAY ($todayString)...")
        val hasActivityToday = hasAnyActivityOnDate(uid, todayString)
        android.util.Log.i("StreakService", "Has activity TODAY: $hasActivityToday")
        
        // Check if there was activity yesterday (to determine if streak should continue when user logs today)
        val yesterdayString = today.minusDays(1).format(dateFormatter)
        val hasActivityYesterday = hasAnyActivityOnDate(uid, yesterdayString)
        android.util.Log.i("StreakService", "Has activity YESTERDAY ($yesterdayString): $hasActivityYesterday")
        
        // DAILY STREAK RULE: You MUST log EVERY SINGLE DAY - NO EXCEPTIONS
        // CRITICAL FIX: NEVER reset streak if yesterday had activity - user might log today
        // Only reset if there's a gap of 2+ days (no activity yesterday AND no activity today)
        if (!hasActivityToday) {
            // If yesterday had activity, NEVER reset - user might log today
            // The streak will be properly updated when user logs today via updateStreakAfterLog
            if (hasActivityYesterday) {
                android.util.Log.i("StreakService", "✅ No activity today BUT activity yesterday - KEEPING STREAK (user may log today)")
                android.util.Log.i("StreakService", "   Current streak: ${streak.currentStreak}, will be updated when user logs")
                // Don't reset - just return current streak
                // The streak will be updated when user logs today via updateStreakAfterLog
                return streak
            }
            android.util.Log.w("StreakService", "✗✗✗ NO ACTIVITY TODAY OR YESTERDAY - STREAK MUST BE RESET ✗✗✗")
            
            // Parse last log date to calculate days since
            val lastLogDate = try {
                if (streak.lastLogDate.isNotEmpty()) {
                    LocalDate.parse(streak.lastLogDate, dateFormatter)
                } else {
                    null
                }
            } catch (e: Exception) {
                android.util.Log.w("StreakService", "Invalid lastLogDate format: ${streak.lastLogDate}")
                null
            }
            
            val daysSinceLastLog = if (lastLogDate != null) {
                java.time.temporal.ChronoUnit.DAYS.between(lastLogDate, today).toInt()
            } else {
                -1
            }
            
            android.util.Log.w("StreakService", "Last log was $daysSinceLastLog day(s) ago")
            android.util.Log.w("StreakService", "RESETTING STREAK FROM ${streak.currentStreak} TO 0")
            
            // IMPORTANT: Keep lastLogDate so we can check if user logs today after logging yesterday
            // This allows the streak to continue properly when updateStreakAfterLog is called
            // BUT: Don't reset to 0 if user just logged - check one more time
            android.util.Log.w("StreakService", "⚠️ About to reset streak to 0 - double-checking for activity today...")
            val doubleCheckActivity = hasAnyActivityOnDate(uid, todayString)
            if (doubleCheckActivity) {
                android.util.Log.w("StreakService", "✅✅✅ ACTIVITY FOUND ON DOUBLE-CHECK - NOT RESETTING STREAK ✅✅✅")
                android.util.Log.w("StreakService", "This means updateStreakAfterLog should be called soon to update the streak")
                return streak // Don't reset - let updateStreakAfterLog handle it
            }
            
            val resetStreak = streak.copy(
                currentStreak = 0,
                // Keep lastLogDate - don't clear it, so we can check if logging today continues from yesterday
                streakStartDate = "",
                lastUpdated = System.currentTimeMillis()
            )
            
            // CRITICAL: Always save the reset streak to Firestore
            saveStreak(resetStreak)
            android.util.Log.i("StreakService", "✓✓✓ STREAK RESET SAVED TO FIRESTORE: currentStreak=0, lastLogDate=${resetStreak.lastLogDate} ✓✓✓")
            android.util.Log.i("StreakService", "=========================================")
            return resetStreak
        }
        
        // There IS activity today - validate the streak can continue
        android.util.Log.i("StreakService", "✓ Activity found today - streak can continue")
        
        // Parse last log date
        val lastLogDate = try {
            if (streak.lastLogDate.isNotEmpty()) {
                LocalDate.parse(streak.lastLogDate, dateFormatter)
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.w("StreakService", "Invalid lastLogDate format: ${streak.lastLogDate}, resetting streak")
            val resetStreak = streak.copy(
                currentStreak = 0,
                lastLogDate = "",
                streakStartDate = ""
            )
            saveStreak(resetStreak)
            return resetStreak
        }
        
        // If last log date is not today, update it to today to continue the streak
        if (lastLogDate != today) {
            val yesterday = today.minusDays(1)
            val isContinuingFromYesterday = lastLogDate == yesterday
            
            android.util.Log.d("StreakService", "Activity found today but lastLogDate ($lastLogDate) != today.")
            android.util.Log.d("StreakService", "Is continuing from yesterday: $isContinuingFromYesterday")
            
            val newStreakCount = when {
                // Continuing from yesterday - increment streak
                isContinuingFromYesterday -> {
                    if (streak.currentStreak == 0 && streak.totalLogs > 0) {
                        // Streak was reset to 0 but we're continuing - restore to 1
                        android.util.Log.i("StreakService", "Restoring streak from 0 to 1 (continuing from yesterday)")
                        1
                    } else {
                        // Normal increment
                        streak.currentStreak + 1
                    }
                }
                // Gap in logging - reset to 1
                else -> {
                    android.util.Log.w("StreakService", "Gap detected - resetting streak to 1")
                    1
                }
            }
            
            val updatedStreak = streak.copy(
                currentStreak = newStreakCount,
                longestStreak = max(streak.longestStreak, newStreakCount),
                lastLogDate = todayString,
                streakStartDate = if (isContinuingFromYesterday) streak.streakStartDate else todayString,
                lastUpdated = System.currentTimeMillis()
            )
            saveStreak(updatedStreak)
            android.util.Log.i("StreakService", "✓ Streak updated: currentStreak=${updatedStreak.currentStreak}, lastLogDate set to today")
            android.util.Log.i("StreakService", "=========================================")
            return updatedStreak
        }
        
        // Activity today and last log date is today - streak continues
        android.util.Log.i("StreakService", "✓ Streak validated: Activity today and lastLogDate matches today")
        android.util.Log.i("StreakService", "Current streak: ${streak.currentStreak} days")
        android.util.Log.i("StreakService", "=========================================")
        return streak
    }
    
    /**
     * Check if user has any activity (logs) on a specific date
     */
    private suspend fun hasAnyActivityOnDate(uid: String, date: String): Boolean {
        return try {
            // Check for any log entries
            val entriesSnapshot = firestore.collection("logs")
                .document(uid)
                .collection("daily")
                .document(date)
                .collection("entries")
                .get()
                .await()
            
            if (entriesSnapshot.documents.isNotEmpty()) {
                return true
            }
            
            // Check for daily log with any data
            val dailyLogDoc = firestore.collection("logs")
                .document(uid)
                .collection("daily")
                .document(date)
                .get()
                .await()
            
            if (dailyLogDoc.exists()) {
                val dailyLog = dailyLogDoc.data
                val water = (dailyLog?.get("water") as? Number)?.toInt() ?: 0
                val steps = (dailyLog?.get("steps") as? Number)?.toInt() ?: 0
                val caloriesBurned = (dailyLog?.get("caloriesBurned") as? Number)?.toInt() ?: 0
                val weight = dailyLog?.get("weight") as? Number
                
                return water > 0 || steps > 0 || caloriesBurned > 0 || weight != null
            }
            
            false
        } catch (e: Exception) {
            android.util.Log.e("StreakService", "Error checking activity for date $date", e)
            false
        }
    }

    /**
     * Get user's earned badges
     */
    suspend fun getUserBadges(uid: String): Result<List<Badge>> {
        return try {
            val badgesSnapshot = firestore.collection("users")
                .document(uid)
                .collection("badges")
                .orderBy("earnedDate", Query.Direction.DESCENDING)
                .get()
                .await()

            val badges = badgesSnapshot.documents.mapNotNull { doc ->
                doc.toObject(Badge::class.java)
            }

            Result.success(badges)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get achievement progress for badges user hasn't earned yet
     */
    suspend fun getAchievementProgress(uid: String): Result<List<AchievementProgress>> {
        return try {
            val streak = getCurrentStreak(uid)
            val earnedBadges = getUserBadges(uid).getOrDefault(emptyList())
            val earnedBadgeTypes = earnedBadges.map { it.type }.toSet()

            val scanCount = getUserScanCount(uid)

            val progress = mutableListOf<AchievementProgress>()

            // 3-Day Hero progress
            if (!earnedBadgeTypes.contains(Badge.TYPE_THREE_DAY_HERO)) {
                progress.add(AchievementProgress(
                    badge = Badge.THREE_DAY_HERO,
                    currentProgress = streak.currentStreak,
                    targetProgress = 3,
                    isCompleted = streak.currentStreak >= 3
                ))
            }

            // Week Warrior progress
            if (!earnedBadgeTypes.contains(Badge.TYPE_WEEK_WARRIOR)) {
                progress.add(AchievementProgress(
                    badge = Badge.WEEK_WARRIOR,
                    currentProgress = streak.currentStreak,
                    targetProgress = 7,
                    isCompleted = streak.currentStreak >= 7
                ))
            }

            // Scan Star progress
            if (!earnedBadgeTypes.contains(Badge.TYPE_SCAN_STAR)) {
                progress.add(AchievementProgress(
                    badge = Badge.SCAN_STAR,
                    currentProgress = scanCount,
                    targetProgress = 5,
                    isCompleted = scanCount >= 5
                ))
            }

            Result.success(progress)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check for scan-related badges when a scan is saved
     */
    suspend fun checkScanBadges(uid: String) {
        try {
            val scanCount = getUserScanCount(uid)
            if (scanCount >= 5) {
                awardBadgeIfNotExists(uid, Badge.TYPE_SCAN_STAR)
            }
        } catch (e: Exception) {
            println("Error checking scan badges: ${e.message}")
        }
    }

    /**
     * Mark badge as seen (remove "new" status)
     */
    suspend fun markBadgeAsSeen(uid: String, badgeType: String): Result<Unit> {
        return try {
            firestore.collection("users")
                .document(uid)
                .collection("badges")
                .document(badgeType)
                .update("isNew", false)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private var instance: StreakService? = null

        fun getInstance(): StreakService {
            return instance ?: synchronized(this) {
                instance ?: StreakService().also { instance = it }
            }
        }
    }
}
