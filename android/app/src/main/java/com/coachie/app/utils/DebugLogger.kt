package com.coachie.app.utils

import android.util.Log

/**
 * Debug logging utility for Coachie app
 * Centralizes debug logging with consistent tagging
 */
object DebugLogger {

    private const val DEBUG_TAG = "CoachieDebug"

    /**
     * Log debug message with consistent CoachieDebug tag
     *
     * @param tag Category or component name (e.g., "HomeScreen", "DashboardClick")
     * @param message Debug message to log
     */
    fun logDebug(tag: String, message: String) {
        Log.d(DEBUG_TAG, "$tag: $message")
    }

    /**
     * Log state change with before/after values
     *
     * @param tag Category or component name
     * @param stateName Name of the state being changed
     * @param beforeValue Value before change
     * @param afterValue Value after change
     */
    fun logStateChange(tag: String, stateName: String, beforeValue: Any?, afterValue: Any?) {
        logDebug(tag, "State change - $stateName: $beforeValue → $afterValue")
    }

    /**
     * Log Firebase operation
     *
     * @param tag Category or component name
     * @param operation Description of the Firebase operation
     * @param success Whether the operation was successful
     */
    fun logFirebaseOperation(tag: String, operation: String, success: Boolean? = null) {
        val status = when (success) {
            true -> "SUCCESS"
            false -> "FAILED"
            null -> "STARTED"
        }
        logDebug(tag, "Firebase: $operation - $status")
    }

    /**
     * Log user interaction
     *
     * @param tag Category or component name
     * @param interaction Description of user interaction
     */
    fun logUserInteraction(tag: String, interaction: String) {
        logDebug(tag, "User Interaction: $interaction")
    }

    /**
     * Log authentication status
     */
    fun logAuthStatus() {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            logDebug("AuthIssue", "No user signed in")
        } else {
            logDebug("AuthStatus", "User signed in: ${currentUser.uid}")
        }
    }

    /**
     * Log state value
     *
     * @param tag Category or component name
     * @param stateName Name of the state
     * @param value Current value of the state
     */
    fun logState(tag: String, stateName: String, value: Any?) {
        logDebug(tag, "State - $stateName: $value")
    }
}
