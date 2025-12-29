package com.coachie.app.data

import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.mealprint.ai.data.local.PreferencesManager

/**
 * Manages app permissions: Camera, Google Fit, and Notifications
 * Handles permission requests, status checks, and persistence
 * 
 * Usage:
 * - Camera: For meal photos and body scans
 * - Google Fit: For steps, sleep, workouts syncing
 * - Notifications: For daily insights and reminders
 */
class PermissionsManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {

    companion object {
        const val PERMISSION_CAMERA = "camera"
        const val PERMISSION_HEALTH_CONNECT = "health_connect"
        const val PERMISSION_NOTIFICATIONS = "notifications"

        // Health Connect permissions for steps, workouts, sleep, and calories
        // Note: getReadPermission returns String permission identifiers

        // TODO: Uncomment when Samsung Health SDK classes are properly resolved
        // Samsung Health permissions for steps, sleep, heart rate, and exercise
        // val SAMSUNG_HEALTH_PERMISSIONS = setOf(
        //     Permission(DataTypes.STEPS, AccessType.READ),
        //     Permission(DataTypes.SLEEP, AccessType.READ),
        //     Permission(DataTypes.HEART_RATE, AccessType.READ),
        //     Permission(DataTypes.EXERCISE, AccessType.READ)
        // )

        private const val PREF_PERMISSIONS_ONBOARDING_COMPLETED = "permissions_onboarding_completed"
    }

    /**
     * Check if camera permission is granted
     * Used for: Meal photos and body scans
     */
    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }





    /**
     * Check if notification permission is granted (Android 13+)
     * Used for: Daily insights and motivation messages
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Notifications are granted by default on Android 12 and below
            true
        }
    }

    /**
     * Check if exact alarms can be scheduled (Android 12+)
     * Used for: Scheduling precise nudge notifications
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            // Exact alarms are allowed by default on Android 11 and below
            true
        }
    }

    /**
     * Check if notifications are enabled for this app
     * Used for: Detecting if user has blocked notifications
     */
    fun areNotificationsEnabled(): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            notificationManager.areNotificationsEnabled()
        } else {
            // Assume enabled on older Android versions
            true
        }
    }

    /**
     * Open the app's settings page in Android system settings
     * Used for: Allowing users to easily enable permissions
     */
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Open the exact alarm settings page (Android 12+)
     * Used for: Allowing users to enable exact alarm permissions
     */
    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } else {
            // Fall back to general app settings for older versions
            openAppSettings()
        }
    }

    /**
     * Get Health Connect permissions set for launcher
     */


    /**
     * Check if permissions onboarding was completed
     * This ensures the permissions flow only shows once
     */
    fun isPermissionsOnboardingCompleted(): Boolean {
        return preferencesManager.getBoolean(PREF_PERMISSIONS_ONBOARDING_COMPLETED, false)
    }

    /**
     * Mark permissions onboarding as completed
     * Called after user completes the permissions page in onboarding
     */
    fun markPermissionsOnboardingCompleted() {
        preferencesManager.saveBoolean(PREF_PERMISSIONS_ONBOARDING_COMPLETED, true)
    }

    /**
     * Reset permissions onboarding (for testing or reset)
     */
    fun resetPermissionsOnboarding() {
        preferencesManager.saveBoolean(PREF_PERMISSIONS_ONBOARDING_COMPLETED, false)
    }

    /**
     * Get permission status summary
     */
    suspend fun getPermissionStatus(): PermissionStatus {
        return PermissionStatus(
            camera = hasCameraPermission(),
            notifications = hasNotificationPermission()
        )
    }

    /**
     * Check if all required permissions are granted
     * Camera and Notifications are required
     */
    suspend fun hasAllRequiredPermissions(): Boolean {
        val status = getPermissionStatus()
        return status.camera && status.notifications
    }

    /**
     * Check if any permissions are missing
     */
    suspend fun hasAnyMissingPermissions(): Boolean {
        return !hasAllRequiredPermissions()
    }

    /**
     * Get list of missing permissions
     */
    suspend fun getMissingPermissions(): List<String> {
        val missing = mutableListOf<String>()
        val status = getPermissionStatus()

        if (!status.camera) {
            missing.add("Camera")
        }


        if (!status.notifications) {
            missing.add("Notifications")
        }

        return missing
    }

    /**
     * Data class for permission status
     */
    data class PermissionStatus(
        val camera: Boolean,
        val notifications: Boolean
    ) {
        /**
         * Check if all permissions are granted
         * Camera and Notifications are required
         */
        val allGranted: Boolean
            get() = camera && notifications

        /**
         * Get count of granted permissions
         */
        val grantedCount: Int
            get() {
                var count = 0
                if (camera) count++
                if (notifications) count++
                return count
            }

        /**
         * Get total required permissions count
         */
        val totalCount: Int
            get() = 2
    }
}
