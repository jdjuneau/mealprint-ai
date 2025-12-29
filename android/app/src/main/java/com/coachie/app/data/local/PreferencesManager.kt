package com.coachie.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * SharedPreferences manager for storing user preferences and app state.
 * Provides a clean API for accessing user data and app settings.
 */
class PreferencesManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Public getter for context (needed by ViewModel for shared preferences)
    val appContext: Context
        get() = context

    companion object {
        private const val PREFS_NAME = "coachie_prefs"

        // Keys
        private const val KEY_USER_ID = "user_id"
        private const val KEY_IS_FIRST_LAUNCH = "is_first_launch"
        private const val KEY_CURRENT_WEIGHT = "current_weight"
        private const val KEY_TARGET_WEIGHT = "target_weight"
        private const val KEY_WEIGHT_UNIT = "weight_unit"
        private const val KEY_HEIGHT_UNIT = "height_unit"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_GOALS_SET = "goals_set"
        private const val KEY_NUDGES_ENABLED = "nudges_enabled"
        private const val KEY_SAMSUNG_SYNC_TIME = "samsung_sync_time"
        private const val KEY_SAMSUNG_SYNC_STATUS = "samsung_sync_status"
        private const val KEY_USER_EXPLICITLY_SIGNED_OUT = "user_explicitly_signed_out"

        // Voice Settings
        private const val KEY_VOICE_ENABLED = "voice_enabled"
        private const val KEY_VOICE_ENGINE = "voice_engine"
        private const val KEY_VOICE_NAME = "voice_name"
        private const val KEY_VOICE_PITCH = "voice_pitch"
        private const val KEY_VOICE_RATE = "voice_rate"
        private const val KEY_VOICE_VOLUME = "voice_volume"

        // Mindfulness Settings
        private const val KEY_MINDFULNESS_REMINDERS_ENABLED = "mindfulness_reminders_enabled"
        private const val KEY_MINDFULNESS_REMINDER_HOUR = "mindfulness_reminder_hour"
        private const val KEY_MINDFULNESS_REMINDER_MINUTE = "mindfulness_reminder_minute"
        private const val KEY_DEFAULT_MEDITATION_DURATION = "default_meditation_duration"

        // Anxiety Detection Settings
        private const val KEY_ANXIETY_DETECTION_ENABLED = "anxiety_detection_enabled"
        
        // Health Connect Settings
        private const val KEY_HEALTH_CONNECT_ENABLED = "health_connect_enabled"
    }

    // User Authentication
    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit { putString(KEY_USER_ID, value) }

    // App State
    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_IS_FIRST_LAUNCH, true)
        set(value) = prefs.edit { putBoolean(KEY_IS_FIRST_LAUNCH, value) }

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETED, value) }

    var isGoalsSet: Boolean
        get() = prefs.getBoolean(KEY_GOALS_SET, false)
        set(value) = prefs.edit { putBoolean(KEY_GOALS_SET, value) }

    // User Goals (from onboarding)
    var currentWeight: String?
        get() = prefs.getString(KEY_CURRENT_WEIGHT, null)
        set(value) = prefs.edit { putString(KEY_CURRENT_WEIGHT, value) }

    var targetWeight: String?
        get() = prefs.getString(KEY_TARGET_WEIGHT, null)
        set(value) = prefs.edit { putString(KEY_TARGET_WEIGHT, value) }

    var weightUnit: String
        get() = prefs.getString(KEY_WEIGHT_UNIT, prefs.getString(KEY_WEIGHT_UNIT, null) ?: "kg") ?: "kg"
        set(value) = prefs.edit { putString(KEY_WEIGHT_UNIT, value) }

    var heightUnit: String?
        get() = prefs.getString(KEY_HEIGHT_UNIT, prefs.getString(KEY_HEIGHT_UNIT, null) ?: "cm") ?: "cm"
        set(value) = prefs.edit { putString(KEY_HEIGHT_UNIT, value) }

    var nudgesEnabled: Boolean
        get() = prefs.getBoolean(KEY_NUDGES_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_NUDGES_ENABLED, value) }

    var anxietyDetectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_ANXIETY_DETECTION_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_ANXIETY_DETECTION_ENABLED, value) }

    var samsungLastSyncTime: Long
        get() = prefs.getLong(KEY_SAMSUNG_SYNC_TIME, 0L)
        set(value) = prefs.edit { putLong(KEY_SAMSUNG_SYNC_TIME, value) }

    var samsungLastSyncStatus: String?
        get() = prefs.getString(KEY_SAMSUNG_SYNC_STATUS, null)
        set(value) = prefs.edit { putString(KEY_SAMSUNG_SYNC_STATUS, value) }


    /**
     * Clear all user data (useful for logout/reset)
     */
    fun clearUserData() {
        prefs.edit {
            remove(KEY_USER_ID)
            remove(KEY_CURRENT_WEIGHT)
            remove(KEY_TARGET_WEIGHT)
            remove(KEY_WEIGHT_UNIT)
            remove(KEY_HEIGHT_UNIT)
            // Mark that user explicitly signed out to prevent auto-login
            putBoolean(KEY_USER_EXPLICITLY_SIGNED_OUT, true)
        }
    }

    /**
     * Check if user explicitly signed out (to prevent auto-login)
     */
    var userExplicitlySignedOut: Boolean
        get() = prefs.getBoolean(KEY_USER_EXPLICITLY_SIGNED_OUT, false)
        set(value) = prefs.edit { putBoolean(KEY_USER_EXPLICITLY_SIGNED_OUT, value) }

    /**
     * Clear all app data (useful for fresh start)
     */
    fun clearAllData() {
        prefs.edit { clear() }
    }

    /**
     * Save onboarding data from the 3-page flow
     */
    fun saveOnboardingData(
        currentWeight: String,
        targetWeight: String,
        weightUnit: String,
        heightUnit: String
    ) {
        prefs.edit {
            putString(KEY_CURRENT_WEIGHT, currentWeight)
            putString(KEY_TARGET_WEIGHT, targetWeight)
            putString(KEY_WEIGHT_UNIT, weightUnit)
            putString(KEY_HEIGHT_UNIT, heightUnit)
            putBoolean(KEY_ONBOARDING_COMPLETED, true)
            putBoolean(KEY_IS_FIRST_LAUNCH, false)
        }
    }

    /**
     * Generic getter for boolean preferences
     */
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    /**
     * Generic setter for boolean preferences
     */
    fun saveBoolean(key: String, value: Boolean) {
        prefs.edit { putBoolean(key, value) }
    }

    /**
     * Generic getter for int preferences
     */
    fun getInt(key: String, defaultValue: Int = 0): Int {
        return prefs.getInt(key, defaultValue)
    }

    /**
     * Generic setter for int preferences
     */
    fun saveInt(key: String, value: Int) {
        prefs.edit { putInt(key, value) }
    }

    /**
     * Generic getter for long preferences
     */
    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return prefs.getLong(key, defaultValue)
    }

    /**
     * Generic setter for long preferences
     */
    fun saveLong(key: String, value: Long) {
        prefs.edit { putLong(key, value) }
    }

    /**
     * Generic getter for string preferences
     */
    fun getString(key: String, defaultValue: String = ""): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    /**
     * Generic setter for string preferences
     */
    fun saveString(key: String, value: String) {
        prefs.edit { putString(key, value) }
    }

    // Voice Settings
    // Default to false - voice disabled in chat bot by default
    var voiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_VOICE_ENABLED, value) }

    var voiceEngine: String?
        get() = prefs.getString(KEY_VOICE_ENGINE, null)
        set(value) = prefs.edit { putString(KEY_VOICE_ENGINE, value) }

    var voiceLanguage: String?
        get() = prefs.getString(KEY_VOICE_NAME, null)
        set(value) = prefs.edit { putString(KEY_VOICE_NAME, value) }

    var voicePitch: Float
        get() = prefs.getFloat(KEY_VOICE_PITCH, 1.0f)
        set(value) = prefs.edit { putFloat(KEY_VOICE_PITCH, value) }

    var voiceRate: Float
        get() = prefs.getFloat(KEY_VOICE_RATE, 1.0f)
        set(value) = prefs.edit { putFloat(KEY_VOICE_RATE, value) }

    var voiceVolume: Float
        get() = prefs.getFloat(KEY_VOICE_VOLUME, 1.0f)
        set(value) = prefs.edit { putFloat(KEY_VOICE_VOLUME, value) }

    // Mindfulness Preferences
    fun getMindfulnessRemindersEnabled(): Boolean =
        prefs.getBoolean(KEY_MINDFULNESS_REMINDERS_ENABLED, false)

    fun setMindfulnessRemindersEnabled(enabled: Boolean) =
        prefs.edit { putBoolean(KEY_MINDFULNESS_REMINDERS_ENABLED, enabled) }

    fun getMindfulnessReminderHour(): Int =
        prefs.getInt(KEY_MINDFULNESS_REMINDER_HOUR, 14) // 2 PM default

    fun setMindfulnessReminderHour(hour: Int) =
        prefs.edit { putInt(KEY_MINDFULNESS_REMINDER_HOUR, hour) }

    fun getMindfulnessReminderMinute(): Int =
        prefs.getInt(KEY_MINDFULNESS_REMINDER_MINUTE, 0)

    fun setMindfulnessReminderMinute(minute: Int) =
        prefs.edit { putInt(KEY_MINDFULNESS_REMINDER_MINUTE, minute) }

    fun getDefaultMeditationDuration(): Int =
        prefs.getInt(KEY_DEFAULT_MEDITATION_DURATION, 10) // 10 minutes default

    fun setDefaultMeditationDuration(duration: Int) =
        prefs.edit { putInt(KEY_DEFAULT_MEDITATION_DURATION, duration) }
    
    // Health Connect Settings
    // Tracks if user has explicitly enabled Health Connect in Coachie's permissions screen
    var healthConnectEnabled: Boolean
        get() = prefs.getBoolean(KEY_HEALTH_CONNECT_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_HEALTH_CONNECT_ENABLED, value) }
}
