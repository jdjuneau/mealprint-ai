package com.coachie.app.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.coachie.app.data.model.DailyLog
import com.coachie.app.data.model.HealthLog
import com.coachie.app.data.model.Habit
import com.coachie.app.data.model.HabitCompletion
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * Local storage manager for tracking daily Coachie scores
 * Stores scores in SharedPreferences for offline access and quick retrieval
 * Also syncs scores to Firestore for cross-platform access (Android & Web)
 */
class CoachieScoreTracker(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "coachie_scores"
        private const val KEY_SCORE_PREFIX = "score_"
        private const val KEY_LAST_CALCULATED_DATE = "last_calculated_date"
        private const val KEY_AVERAGE_SCORE = "average_score"
        private const val KEY_TOTAL_DAYS = "total_days"
        private const val KEY_HIGHEST_SCORE = "highest_score"
        private const val KEY_HIGHEST_SCORE_DATE = "highest_score_date"
        
        /**
         * Get date string in YYYY-MM-DD format
         */
        private fun getDateString(date: Date = Date()): String {
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
        }
    }
    
    /**
     * Calculate and store today's Coachie score
     * Returns the calculated score
     */
    fun calculateAndStoreTodayScore(
        meals: List<HealthLog.MealLog>,
        workouts: List<HealthLog.WorkoutLog>,
        sleepLogs: List<HealthLog.SleepLog>,
        waterLogs: List<HealthLog.WaterLog>,
        allHealthLogs: List<HealthLog>,
        dailyLog: DailyLog?,
        habits: List<Habit>,
        habitCompletions: List<HabitCompletion>,
        calorieGoal: Int = 2000,
        stepsGoal: Int = 10000,
        waterGoal: Int = 2000,
        sleepGoal: Double = 8.0
    ): Int {
        val categoryScores = DailyScoreCalculator.calculateAllScores(
            meals = meals,
            workouts = workouts,
            sleepLogs = sleepLogs,
            waterLogs = waterLogs,
            allHealthLogs = allHealthLogs,
            dailyLog = dailyLog,
            habits = habits,
            habitCompletions = habitCompletions,
            calorieGoal = calorieGoal,
            stepsGoal = stepsGoal,
            waterGoal = waterGoal,
            sleepGoal = sleepGoal
        )
        
        val dailyScore = categoryScores.calculateDailyScore()
        val today = getDateString()
        
        // Store today's score locally
        prefs.edit {
            putInt("${KEY_SCORE_PREFIX}$today", dailyScore)
            putString(KEY_LAST_CALCULATED_DATE, today)
        }
        
        // Update statistics
        updateStatistics(dailyScore, today)
        
        // Sync to Firestore for cross-platform access (async, don't block)
        syncScoreToFirestore(today, dailyScore, categoryScores)
        
        // CRITICAL: Trigger widget update when score changes
        try {
            val widgetUpdateIntent = android.content.Intent("com.coachie.app.WIDGET_UPDATE")
            widgetUpdateIntent.setComponent(
                android.content.ComponentName(context, com.coachie.app.widget.CoachieWidgetProvider::class.java)
            )
            context.sendBroadcast(widgetUpdateIntent)
            android.util.Log.d("CoachieScoreTracker", "Widget update triggered after score calculation")
        } catch (e: Exception) {
            android.util.Log.w("CoachieScoreTracker", "Failed to trigger widget update", e)
            // Don't fail score calculation if widget update fails
        }
        
        return dailyScore
    }
    
    /**
     * Sync score to Firestore for cross-platform access
     * Stores in users/{userId}/scores/{date} collection
     */
    private fun syncScoreToFirestore(dateStr: String, score: Int, categoryScores: CategoryScores) {
        // CRITICAL SECURITY: Use authenticated user, not SharedPreferences
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        
        if (userId == null) {
            android.util.Log.w("CoachieScoreTracker", "Cannot sync score: no authenticated user found")
            return
        }
        
        // Sync in background (fire and forget)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val scoreData = hashMapOf(
                    "date" to dateStr,
                    "score" to score,
                    "healthScore" to categoryScores.healthScore,
                    "wellnessScore" to categoryScores.wellnessScore,
                    "habitsScore" to categoryScores.habitsScore,
                    "calculatedAt" to com.google.firebase.Timestamp.now()
                )
                
                Firebase.firestore
                    .collection("users")
                    .document(userId)
                    .collection("scores")
                    .document(dateStr)
                    .set(scoreData)
                    .await()
                
                android.util.Log.d("CoachieScoreTracker", "Successfully synced score to Firestore: $dateStr = $score")
            } catch (e: Exception) {
                android.util.Log.e("CoachieScoreTracker", "Error syncing score to Firestore", e)
                // Don't throw - local storage is primary, Firestore is for sync
            }
        }
    }
    
    /**
     * Get today's score (if calculated)
     */
    fun getTodayScore(): Int? {
        val today = getDateString()
        val score = prefs.getInt("${KEY_SCORE_PREFIX}$today", -1)
        return if (score >= 0) score else null
    }
    
    /**
     * Get score for a specific date
     */
    fun getScoreForDate(date: Date): Int? {
        val dateStr = getDateString(date)
        val score = prefs.getInt("${KEY_SCORE_PREFIX}$dateStr", -1)
        return if (score >= 0) score else null
    }
    
    /**
     * Get score for a specific date string (YYYY-MM-DD)
     */
    fun getScoreForDateString(dateStr: String): Int? {
        val score = prefs.getInt("${KEY_SCORE_PREFIX}$dateStr", -1)
        return if (score >= 0) score else null
    }
    
    /**
     * Get all stored scores (date -> score map)
     */
    fun getAllScores(): Map<String, Int> {
        val scores = mutableMapOf<String, Int>()
        val allPrefs = prefs.all
        
        allPrefs.forEach { (key, value) ->
            if (key.startsWith(KEY_SCORE_PREFIX) && value is Int) {
                val dateStr = key.removePrefix(KEY_SCORE_PREFIX)
                scores[dateStr] = value
            }
        }
        
        return scores
    }
    
    /**
     * Get average score across all stored days
     */
    fun getAverageScore(): Double {
        return prefs.getFloat(KEY_AVERAGE_SCORE, 0f).toDouble()
    }
    
    /**
     * Get total number of days with scores
     */
    fun getTotalDays(): Int {
        return prefs.getInt(KEY_TOTAL_DAYS, 0)
    }
    
    /**
     * Get highest score achieved
     */
    fun getHighestScore(): Int {
        return prefs.getInt(KEY_HIGHEST_SCORE, 0)
    }
    
    /**
     * Get date of highest score
     */
    fun getHighestScoreDate(): String? {
        return prefs.getString(KEY_HIGHEST_SCORE_DATE, null)
    }
    
    /**
     * Get last 7 days average
     */
    fun getLast7DaysAverage(): Double? {
        val scores = getAllScores()
        if (scores.isEmpty()) return null
        
        val sortedDates = scores.keys.sortedDescending().take(7)
        if (sortedDates.isEmpty()) return null
        
        val recentScores = sortedDates.mapNotNull { scores[it] }
        if (recentScores.isEmpty()) return null
        
        return recentScores.average()
    }
    
    /**
     * Get last 30 days average
     */
    fun getLast30DaysAverage(): Double? {
        val scores = getAllScores()
        if (scores.isEmpty()) return null
        
        val sortedDates = scores.keys.sortedDescending().take(30)
        if (sortedDates.isEmpty()) return null
        
        val recentScores = sortedDates.mapNotNull { scores[it] }
        if (recentScores.isEmpty()) return null
        
        return recentScores.average()
    }
    
    /**
     * Update statistics (average, total days, highest score)
     */
    private fun updateStatistics(newScore: Int, dateStr: String) {
        val allScores = getAllScores()
        val totalDays = allScores.size
        val average = if (totalDays > 0) allScores.values.average() else 0.0
        
        val currentHighest = prefs.getInt(KEY_HIGHEST_SCORE, 0)
        val highestScore = maxOf(currentHighest, newScore)
        val highestScoreDate = if (newScore >= currentHighest) dateStr else prefs.getString(KEY_HIGHEST_SCORE_DATE, null)
        
        prefs.edit {
            putFloat(KEY_AVERAGE_SCORE, average.toFloat())
            putInt(KEY_TOTAL_DAYS, totalDays)
            putInt(KEY_HIGHEST_SCORE, highestScore)
            if (highestScoreDate != null) {
                putString(KEY_HIGHEST_SCORE_DATE, highestScoreDate)
            }
        }
    }
    
    /**
     * Clear all stored scores (for testing or reset)
     */
    fun clearAllScores() {
        prefs.edit {
            clear()
        }
    }
}

