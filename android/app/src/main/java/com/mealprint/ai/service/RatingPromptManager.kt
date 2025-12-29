package com.coachie.app.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.mealprint.ai.data.local.PreferencesManager
import java.util.concurrent.TimeUnit

/**
 * Manages app rating prompts in a non-intrusive way.
 * 
 * Strategy:
 * - Only prompts after positive milestones (streaks, achievements)
 * - Maximum 3 prompts total
 * - Minimum 14 days between prompts
 * - Easy to dismiss with "Don't ask again" option
 * - Respects user choice permanently
 */
class RatingPromptManager(private val context: Context) {
    
    private val preferencesManager = PreferencesManager(context)
    
    companion object {
        private const val TAG = "RatingPromptManager"
        
        // Configuration
        private const val MAX_PROMPTS = 3
        private const val MIN_DAYS_BETWEEN_PROMPTS = 14L
        private const val MIN_APP_LAUNCHES = 5 // Minimum app launches before first prompt
        private const val MIN_DAYS_SINCE_INSTALL = 7L // Minimum days since install
        
        // Preference keys
        private const val KEY_RATING_PROMPT_COUNT = "rating_prompt_count"
        private const val KEY_LAST_RATING_PROMPT_TIME = "last_rating_prompt_time"
        private const val KEY_RATING_DISMISSED_PERMANENTLY = "rating_dismissed_permanently"
        private const val KEY_APP_LAUNCH_COUNT = "app_launch_count"
        private const val KEY_FIRST_LAUNCH_TIME = "first_launch_time"
        private const val KEY_LAST_MILESTONE_SHOWN = "last_milestone_shown"
    }
    
    /**
     * Check if rating prompt should be shown based on milestones and timing
     */
    fun shouldShowRatingPrompt(milestone: RatingMilestone? = null): Boolean {
        // Don't show if user permanently dismissed or already rated
        if (preferencesManager.getBoolean(KEY_RATING_DISMISSED_PERMANENTLY, false)) {
            Log.d(TAG, "Rating prompt dismissed permanently or user already rated")
            return false
        }
        
        // Check if we've exceeded max prompts
        val promptCount = preferencesManager.getInt(KEY_RATING_PROMPT_COUNT, 0)
        if (promptCount >= MAX_PROMPTS) {
            Log.d(TAG, "Max rating prompts reached: $promptCount")
            return false
        }
        
        // Check minimum app launches
        val launchCount = preferencesManager.getInt(KEY_APP_LAUNCH_COUNT, 0)
        if (launchCount < MIN_APP_LAUNCHES) {
            Log.d(TAG, "Not enough app launches: $launchCount < $MIN_APP_LAUNCHES")
            return false
        }
        
        // Check minimum days since install
        val firstLaunchTime = preferencesManager.getLong(KEY_FIRST_LAUNCH_TIME, 0L)
        if (firstLaunchTime == 0L) {
            // First launch - record it
            preferencesManager.saveLong(KEY_FIRST_LAUNCH_TIME, System.currentTimeMillis())
            return false
        }
        
        val daysSinceInstall = TimeUnit.MILLISECONDS.toDays(
            System.currentTimeMillis() - firstLaunchTime
        )
        if (daysSinceInstall < MIN_DAYS_SINCE_INSTALL) {
            Log.d(TAG, "Not enough days since install: $daysSinceInstall < $MIN_DAYS_SINCE_INSTALL")
            return false
        }
        
        // Check time since last prompt
        val lastPromptTime = preferencesManager.getLong(KEY_LAST_RATING_PROMPT_TIME, 0L)
        if (lastPromptTime > 0) {
            val daysSinceLastPrompt = TimeUnit.MILLISECONDS.toDays(
                System.currentTimeMillis() - lastPromptTime
            )
            if (daysSinceLastPrompt < MIN_DAYS_BETWEEN_PROMPTS) {
                Log.d(TAG, "Too soon since last prompt: $daysSinceLastPrompt < $MIN_DAYS_BETWEEN_PROMPTS")
                return false
            }
        }
        
        // If milestone provided, check if we should show for this specific milestone
        if (milestone != null) {
            val lastMilestone = preferencesManager.getString(KEY_LAST_MILESTONE_SHOWN, "")
            // Don't show for the same milestone twice
            if (lastMilestone == milestone.name) {
                Log.d(TAG, "Already shown for milestone: ${milestone.name}")
                return false
            }
        }
        
        Log.d(TAG, "✅ Rating prompt should be shown (count: $promptCount, milestone: ${milestone?.name})")
        return true
    }
    
    /**
     * Record that rating prompt was shown
     */
    fun recordPromptShown(milestone: RatingMilestone? = null) {
        val currentCount = preferencesManager.getInt(KEY_RATING_PROMPT_COUNT, 0)
        preferencesManager.saveInt(KEY_RATING_PROMPT_COUNT, currentCount + 1)
        preferencesManager.saveLong(KEY_LAST_RATING_PROMPT_TIME, System.currentTimeMillis())
        
        if (milestone != null) {
            preferencesManager.saveString(KEY_LAST_MILESTONE_SHOWN, milestone.name)
        }
        
        Log.d(TAG, "Recorded rating prompt shown (count: ${currentCount + 1})")
    }
    
    /**
     * Record that user dismissed permanently
     */
    fun recordDismissedPermanently() {
        preferencesManager.saveBoolean(KEY_RATING_DISMISSED_PERMANENTLY, true)
        Log.d(TAG, "User dismissed rating prompt permanently")
    }
    
    /**
     * Record that user rated the app.
     * Once rated, we never ask again (even if they rated outside our prompt).
     * Note: We can't detect if user rated directly in Play Store, but if they did,
     * they can use "Don't ask again" if our prompt appears.
     */
    fun recordRated() {
        preferencesManager.saveBoolean(KEY_RATING_DISMISSED_PERMANENTLY, true)
        Log.d(TAG, "User rated the app - will never ask again")
    }
    
    /**
     * Check if user has already rated (through our prompt).
     * Note: We can't detect ratings made directly in Play Store, but once they
     * click "Rate Now" in our prompt, we mark it as rated and never ask again.
     */
    fun hasRated(): Boolean {
        return preferencesManager.getBoolean(KEY_RATING_DISMISSED_PERMANENTLY, false)
    }
    
    /**
     * Increment app launch count (call this on app launch)
     */
    fun incrementLaunchCount() {
        val currentCount = preferencesManager.getInt(KEY_APP_LAUNCH_COUNT, 0)
        preferencesManager.saveInt(KEY_APP_LAUNCH_COUNT, currentCount + 1)
    }
    
    /**
     * Open Play Store rating page.
     * Once opened, we mark as rated and never ask again.
     * Note: We can't verify if they actually submitted a rating, but opening
     * the Play Store page is considered intent to rate, so we respect that.
     */
    fun openPlayStoreRating() {
        try {
            val packageName = context.packageName
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=$packageName")
                setPackage("com.android.vending")
            }
            context.startActivity(intent)
            // Mark as rated - we won't ask again
            recordRated()
        } catch (e: Exception) {
            // Fallback to web browser
            try {
                val packageName = context.packageName
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                }
                context.startActivity(intent)
                // Mark as rated - we won't ask again
                recordRated()
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open Play Store", e2)
            }
        }
    }
    
    /**
     * Get current prompt count
     */
    fun getPromptCount(): Int {
        return preferencesManager.getInt(KEY_RATING_PROMPT_COUNT, 0)
    }
}

/**
 * Milestones that can trigger a rating prompt
 */
enum class RatingMilestone {
    SEVEN_DAY_STREAK,
    FOURTEEN_DAY_STREAK,
    THIRTY_DAY_STREAK,
    FIRST_ACHIEVEMENT,
    GOAL_REACHED,
    CONSISTENT_USAGE // 7+ days of consistent logging
}

