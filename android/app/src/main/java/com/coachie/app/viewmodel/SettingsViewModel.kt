package com.coachie.app.viewmodel

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.local.PreferencesManager
import com.coachie.app.receiver.LocalNudgeReceiver
import com.google.firebase.functions.ktx.functions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar

class SettingsViewModel(
    private val repository: FirebaseRepository,
    private val preferencesManager: PreferencesManager,
    private val context: Context,
    private val userId: String
) : ViewModel() {

    data class SettingsUiState(
        val isLoading: Boolean = true,
        val isSaving: Boolean = false,
        val nudgesEnabled: Boolean = true,
        val morningBriefNotifications: Boolean = true,
        val afternoonBriefNotifications: Boolean = true,
        val eveningBriefNotifications: Boolean = true,
        val mealPlanNotifications: Boolean = false,
        val mealReminders: Boolean = false,
        val breakfastTime: String = "07:30",
        val lunchTime: String = "12:30",
        val dinnerTime: String = "18:30",
        val snack1Time: String = "10:00",
        val snack2Time: String = "15:30",
        val mealsPerDay: Int = 3,
        val snacksPerDay: Int = 2,
        val statusMessage: String? = null,
        val errorMessage: String? = null,
        val showClearDataConfirmation: Boolean = false,
        val showDeleteAccountConfirmation: Boolean = false,
        val showReauthDialog: Boolean = false,
        val reauthEmail: String? = null,
        val isExportingData: Boolean = false,
        val exportDataUrl: String? = null
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        if (userId.isBlank()) {
            _uiState.value = SettingsUiState(
                isLoading = false,
                nudgesEnabled = preferencesManager.nudgesEnabled,
                errorMessage = "Sign in to manage settings"
            )
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = null) }
            try {
                val profileResult = repository.getUserProfile(userId)
                val profile = profileResult.getOrNull()
                val nudgesEnabled = profile?.nudgesEnabled ?: preferencesManager.nudgesEnabled
                preferencesManager.nudgesEnabled = nudgesEnabled
                
                // Load notification preferences
                val notifications = profile?.notifications ?: emptyMap()
                val mealPlanNotifications = notifications["mealPlan"] ?: false
                val mealReminders = notifications["mealReminders"] ?: false
                val morningBriefNotifications = notifications["morningBrief"] ?: true
                val afternoonBriefNotifications = notifications["afternoonBrief"] ?: true
                val eveningBriefNotifications = notifications["eveningBrief"] ?: true
                
                // Load meal times
                val mealTimes = profile?.mealTimes ?: emptyMap()
                val breakfastTime = mealTimes["breakfast"] ?: "07:30"
                val lunchTime = mealTimes["lunch"] ?: "12:30"
                val dinnerTime = mealTimes["dinner"] ?: "18:30"
                val snack1Time = mealTimes["snack1"] ?: "10:00"
                val snack2Time = mealTimes["snack2"] ?: "15:30"
                
                val loadedMealsPerDay = profile?.mealsPerDay ?: 3
                val loadedSnacksPerDay = profile?.snacksPerDay ?: 2
                android.util.Log.d("SettingsViewModel", "Loaded preferences - mealsPerDay: $loadedMealsPerDay, snacksPerDay: $loadedSnacksPerDay")
                
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        nudgesEnabled = nudgesEnabled,
                        morningBriefNotifications = morningBriefNotifications,
                        afternoonBriefNotifications = afternoonBriefNotifications,
                        eveningBriefNotifications = eveningBriefNotifications,
                        mealPlanNotifications = mealPlanNotifications,
                        mealReminders = mealReminders,
                        breakfastTime = breakfastTime,
                        lunchTime = lunchTime,
                        dinnerTime = dinnerTime,
                        snack1Time = snack1Time,
                        snack2Time = snack2Time,
                        mealsPerDay = loadedMealsPerDay,
                        snacksPerDay = loadedSnacksPerDay,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        nudgesEnabled = preferencesManager.nudgesEnabled,
                        errorMessage = e.message ?: "Failed to load settings"
                    )
                }
            }
        }
    }

    fun setNudgesEnabled(enabled: Boolean) {
        if (userId.isBlank()) {
            _uiState.update {
                it.copy(
                    nudgesEnabled = preferencesManager.nudgesEnabled,
                    statusMessage = null,
                    errorMessage = "Sign in to change settings"
                )
            }
            return
        }

        val previousState = _uiState.value
        _uiState.update { it.copy(nudgesEnabled = enabled, isSaving = true, errorMessage = null, statusMessage = null) }

        viewModelScope.launch {
            try {
                val result = repository.updateUserSettings(userId, mapOf("nudgesEnabled" to enabled))
                result.getOrThrow()
                preferencesManager.nudgesEnabled = enabled

                // Schedule or cancel local notifications
                updateLocalNotifications(enabled)

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        nudgesEnabled = enabled,
                        statusMessage = if (enabled) "Daily nudges enabled" else "Daily nudges disabled"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        nudgesEnabled = previousState.nudgesEnabled,
                        errorMessage = e.message ?: "Failed to update settings"
                    )
                }
            }
        }
    }

    fun setMorningBriefNotifications(enabled: Boolean) {
        setBriefNotification("morningBrief", enabled)
    }
    
    fun setAfternoonBriefNotifications(enabled: Boolean) {
        setBriefNotification("afternoonBrief", enabled)
    }
    
    fun setEveningBriefNotifications(enabled: Boolean) {
        setBriefNotification("eveningBrief", enabled)
    }
    
    private fun setBriefNotification(briefType: String, enabled: Boolean) {
        if (userId.isBlank()) {
            _uiState.update {
                it.copy(
                    statusMessage = null,
                    errorMessage = "Sign in to change settings"
                )
            }
            return
        }

        val previousState = _uiState.value
        _uiState.update { it.copy(isSaving = true, errorMessage = null, statusMessage = null) }

        viewModelScope.launch {
            try {
                val profileResult = repository.getUserProfile(userId)
                val profile = profileResult.getOrNull()
                
                if (profile != null) {
                    val currentNotifications = profile.notifications?.toMutableMap() ?: mutableMapOf()
                    currentNotifications[briefType] = enabled
                    
                    val updatedProfile = profile.copy(notifications = currentNotifications)
                    repository.saveUserProfile(updatedProfile)
                    
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            morningBriefNotifications = currentNotifications["morningBrief"] ?: true,
                            afternoonBriefNotifications = currentNotifications["afternoonBrief"] ?: true,
                            eveningBriefNotifications = currentNotifications["eveningBrief"] ?: true,
                            statusMessage = if (enabled) "${briefType.replace("Brief", " brief")} notifications enabled" else "${briefType.replace("Brief", " brief")} notifications disabled"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = "Failed to load profile"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = e.message ?: "Failed to update settings"
                    )
                }
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Clear all user data but keep the account
     * CRITICAL SECURITY: Always uses authenticated user ID, never trusts ViewModel parameter
     */
    suspend fun clearUserData(): Result<Unit> {
        // CRITICAL SECURITY: Always get the authenticated user ID directly from Firebase Auth
        val authenticatedUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (authenticatedUserId == null) {
            android.util.Log.e("SettingsViewModel", "❌❌❌ CRITICAL SECURITY ERROR: clearUserData called but no authenticated user! ❌❌❌")
            return Result.failure(IllegalStateException("User must be authenticated to clear data"))
        }
        
        // CRITICAL SECURITY: Validate that ViewModel's userId matches authenticated user
        if (userId != authenticatedUserId) {
            android.util.Log.e("SettingsViewModel", "❌❌❌ CRITICAL SECURITY ERROR: ViewModel userId mismatch! ❌❌❌")
            android.util.Log.e("SettingsViewModel", "ViewModel userId: '$userId'")
            android.util.Log.e("SettingsViewModel", "Authenticated userId: '$authenticatedUserId'")
            android.util.Log.e("SettingsViewModel", "ABORTING DATA CLEAR TO PREVENT DELETING WRONG USER'S DATA!")
            return Result.failure(SecurityException("Cannot clear data: ViewModel userId does not match authenticated user"))
        }
        
        android.util.Log.i("SettingsViewModel", "✅ SECURITY CHECK PASSED: Clearing data for authenticated user: $authenticatedUserId")
        
        return try {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            // Pass authenticatedUserId (not ViewModel's userId) to repository
            val result = repository.clearUserData(authenticatedUserId)
            
            // Clear ALL local SharedPreferences (scores, statistics, etc.)
            try {
                // Clear coachie_scores SharedPreferences
                val scoresPrefs = context.getSharedPreferences("coachie_scores", android.content.Context.MODE_PRIVATE)
                scoresPrefs.edit().clear().apply()
                android.util.Log.d("SettingsViewModel", "Cleared coachie_scores SharedPreferences")
                
                // Clear any other app SharedPreferences that might store cached data
                // Note: We don't clear the main preferences file as it contains settings
                // But we should clear score-related data
            } catch (e: Exception) {
                android.util.Log.w("SettingsViewModel", "Failed to clear local SharedPreferences", e)
            }
            
            _uiState.update { it.copy(isSaving = false, showClearDataConfirmation = false) }
            result
        } catch (e: Exception) {
            _uiState.update { it.copy(isSaving = false, errorMessage = e.message ?: "Failed to clear data", showClearDataConfirmation = false) }
            Result.failure(e)
        }
    }

    /**
     * Delete user account completely
     * This deletes ALL Firestore data and should be followed by Firebase Auth account deletion in the UI
     * CRITICAL SECURITY: Always uses authenticated user ID, never trusts ViewModel parameter
     */
    suspend fun deleteUserAccount(): Result<Unit> {
        // CRITICAL SECURITY: Always get the authenticated user ID directly from Firebase Auth
        val authenticatedUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (authenticatedUserId == null) {
            android.util.Log.e("SettingsViewModel", "❌❌❌ CRITICAL SECURITY ERROR: deleteUserAccount called but no authenticated user! ❌❌❌")
            return Result.failure(IllegalStateException("User must be authenticated to delete account"))
        }
        
        // CRITICAL SECURITY: Validate that ViewModel's userId matches authenticated user
        if (userId != authenticatedUserId) {
            android.util.Log.e("SettingsViewModel", "❌❌❌ CRITICAL SECURITY ERROR: ViewModel userId mismatch! ❌❌❌")
            android.util.Log.e("SettingsViewModel", "ViewModel userId: '$userId'")
            android.util.Log.e("SettingsViewModel", "Authenticated userId: '$authenticatedUserId'")
            android.util.Log.e("SettingsViewModel", "ABORTING ACCOUNT DELETION TO PREVENT DELETING WRONG USER'S ACCOUNT!")
            return Result.failure(SecurityException("Cannot delete account: ViewModel userId does not match authenticated user"))
        }
        
        android.util.Log.i("SettingsViewModel", "✅ SECURITY CHECK PASSED: Deleting account for authenticated user: $authenticatedUserId")
        
        return try {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            
            // Delete all Firestore data - pass authenticatedUserId (not ViewModel's userId)
            val result = repository.deleteUserAccount(authenticatedUserId)
            
            // Clear local SharedPreferences scores
            try {
                val prefs = context.getSharedPreferences("coachie_scores", android.content.Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
                android.util.Log.d("SettingsViewModel", "Cleared local SharedPreferences scores")
            } catch (e: Exception) {
                android.util.Log.w("SettingsViewModel", "Failed to clear local SharedPreferences", e)
            }
            
            _uiState.update { it.copy(isSaving = false, showDeleteAccountConfirmation = false) }
            result
        } catch (e: Exception) {
            _uiState.update { it.copy(isSaving = false, errorMessage = e.message ?: "Failed to delete account", showDeleteAccountConfirmation = false) }
            Result.failure(e)
        }
    }

    fun showClearDataConfirmation() {
        _uiState.update { it.copy(showClearDataConfirmation = true) }
    }

    fun showDeleteAccountConfirmation() {
        _uiState.update { it.copy(showDeleteAccountConfirmation = true) }
    }

    fun setStatusMessage(message: String) {
        _uiState.update { it.copy(statusMessage = message) }
    }

    fun setErrorMessage(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun dismissClearDataConfirmation() {
        _uiState.update { it.copy(showClearDataConfirmation = false) }
    }

    fun dismissDeleteAccountConfirmation() {
        _uiState.update { it.copy(showDeleteAccountConfirmation = false) }
    }

    /**
     * Export user data (GDPR Article 15 - Right of Access, CCPA Section 1798.100)
     * Calls Cloud Function to export all user data in JSON format
     */
    fun exportUserData() {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isExportingData = true, 
                    errorMessage = null, 
                    statusMessage = null,
                    exportDataUrl = null
                ) 
            }
            
            try {
                val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
                val exportFunction = functions.getHttpsCallable("exportUserData")
                
                android.util.Log.d("SettingsViewModel", "Calling exportUserData Cloud Function...")
                val result = exportFunction.call().await()
                val data = result.data as? Map<*, *>
                
                android.util.Log.d("SettingsViewModel", "Export function response: success=${data?.get("success")}")
                
                if (data != null && data["success"] == true) {
                    // Get export data
                    val exportData = data["data"]
                    
                    // Convert to JSON string using Gson (already in dependencies)
                    val gson = com.google.gson.GsonBuilder()
                        .setPrettyPrinting()
                        .create()
                    val jsonString = gson.toJson(exportData)
                    
                    // Save to app's external files directory (accessible via file manager)
                    // Using external files dir ensures FileProvider can access it
                    val fileName = "coachie_data_export_${System.currentTimeMillis()}.json"
                    val externalFilesDir = context.getExternalFilesDir(null) 
                        ?: context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                        ?: context.filesDir
                    
                    val file = java.io.File(externalFilesDir, fileName)
                    file.writeText(jsonString)
                    
                    android.util.Log.d("SettingsViewModel", "Data exported to: ${file.absolutePath}")
                    
                    // Try to open file location or share it
                    val fileUri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    
                    _uiState.update {
                        it.copy(
                            isExportingData = false,
                            statusMessage = "Data exported successfully! File saved: $fileName\nLocation: ${file.absolutePath}",
                            exportDataUrl = fileUri.toString()
                        )
                    }
                    
                    // Optionally share the file
                    try {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, fileUri)
                            putExtra(Intent.EXTRA_SUBJECT, "Coachie Data Export")
                            putExtra(Intent.EXTRA_TEXT, "My Coachie data export - ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        // Don't auto-open share dialog - let user choose when to share
                        // context.startActivity(Intent.createChooser(shareIntent, "Share Data Export"))
                    } catch (e: Exception) {
                        android.util.Log.w("SettingsViewModel", "Could not prepare share intent", e)
                    }
                } else {
                    val errorMsg = (data?.get("error") as? String) ?: "Unknown error"
                    android.util.Log.e("SettingsViewModel", "Export failed: $errorMsg")
                    _uiState.update {
                        it.copy(
                            isExportingData = false,
                            errorMessage = "Failed to export data: $errorMsg"
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Error exporting data", e)
                _uiState.update {
                    it.copy(
                        isExportingData = false,
                        errorMessage = "Failed to export data: ${e.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }
    
    fun showReauthDialog(email: String) {
        _uiState.update { it.copy(showReauthDialog = true, reauthEmail = email) }
    }
    
    fun dismissReauthDialog() {
        _uiState.update { it.copy(showReauthDialog = false, reauthEmail = null) }
    }
    
    fun setMealPlanNotifications(enabled: Boolean) {
        if (userId.isBlank()) return
        
        val previousState = _uiState.value
        _uiState.update { it.copy(mealPlanNotifications = enabled, isSaving = true) }
        
        viewModelScope.launch {
            try {
                val profileResult = repository.getUserProfile(userId)
                val profile = profileResult.getOrNull()
                val currentNotifications = profile?.notifications?.toMutableMap() ?: mutableMapOf()
                currentNotifications["mealPlan"] = enabled
                
                val result = repository.updateUserSettings(userId, mapOf("notifications" to currentNotifications))
                result.getOrThrow()
                
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        mealPlanNotifications = enabled,
                        statusMessage = if (enabled) "Weekly blueprint notifications enabled" else "Weekly blueprint notifications disabled"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        mealPlanNotifications = previousState.mealPlanNotifications,
                        errorMessage = e.message ?: "Failed to update settings"
                    )
                }
            }
        }
    }
    
    fun setMealReminders(enabled: Boolean) {
        if (userId.isBlank()) return
        
        val previousState = _uiState.value
        _uiState.update { it.copy(mealReminders = enabled, isSaving = true) }
        
        viewModelScope.launch {
            try {
                val profileResult = repository.getUserProfile(userId)
                val profile = profileResult.getOrNull()
                val currentNotifications = profile?.notifications?.toMutableMap() ?: mutableMapOf()
                currentNotifications["mealReminders"] = enabled
                
                val result = repository.updateUserSettings(userId, mapOf("notifications" to currentNotifications))
                result.getOrThrow()
                
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        mealReminders = enabled,
                        statusMessage = if (enabled) "Meal reminders enabled" else "Meal reminders disabled"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        mealReminders = previousState.mealReminders,
                        errorMessage = e.message ?: "Failed to update settings"
                    )
                }
            }
        }
    }
    
    fun setMealTime(mealType: String, time: String) {
        if (userId.isBlank()) return
        
        val previousState = _uiState.value
        _uiState.update { 
            it.copy(
                isSaving = true,
                breakfastTime = if (mealType == "breakfast") time else it.breakfastTime,
                lunchTime = if (mealType == "lunch") time else it.lunchTime,
                dinnerTime = if (mealType == "dinner") time else it.dinnerTime,
                snack1Time = if (mealType == "snack1") time else it.snack1Time,
                snack2Time = if (mealType == "snack2") time else it.snack2Time
            )
        }
        
        viewModelScope.launch {
            try {
                val profileResult = repository.getUserProfile(userId)
                val profile = profileResult.getOrNull()
                val currentMealTimes = profile?.mealTimes?.toMutableMap() ?: mutableMapOf()
                currentMealTimes[mealType] = time
                
                val result = repository.updateUserSettings(userId, mapOf("mealTimes" to currentMealTimes))
                result.getOrThrow()
                
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        statusMessage = "Meal time updated"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        breakfastTime = previousState.breakfastTime,
                        lunchTime = previousState.lunchTime,
                        dinnerTime = previousState.dinnerTime,
                        snack1Time = previousState.snack1Time,
                        snack2Time = previousState.snack2Time,
                        errorMessage = e.message ?: "Failed to update meal time"
                    )
                }
            }
        }
    }
    
    fun setMealsPerDay(count: Int) {
        if (userId.isBlank()) return
        
        val previousState = _uiState.value
        _uiState.update { it.copy(mealsPerDay = count, isSaving = true) }
        
        viewModelScope.launch {
            try {
                val result = repository.updateUserSettings(userId, mapOf("mealsPerDay" to count))
                result.getOrThrow()
                
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        mealsPerDay = count,
                        statusMessage = "Meals per day updated"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        mealsPerDay = previousState.mealsPerDay,
                        errorMessage = e.message ?: "Failed to update meals per day"
                    )
                }
            }
        }
    }
    
    fun setSnacksPerDay(count: Int) {
        if (userId.isBlank()) return
        
        val previousState = _uiState.value
        _uiState.update { it.copy(snacksPerDay = count, isSaving = true) }
        
        viewModelScope.launch {
            try {
                val result = repository.updateUserSettings(userId, mapOf("snacksPerDay" to count))
                result.getOrThrow()
                
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        snacksPerDay = count,
                        statusMessage = "Snacks per day updated"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        snacksPerDay = previousState.snacksPerDay,
                        errorMessage = e.message ?: "Failed to update snacks per day"
                    )
                }
            }
        }
    }


    /**
     * Update local notification scheduling based on nudge preference
     */
    private fun updateLocalNotifications(enabled: Boolean) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            if (!enabled) {
                // Cancel all existing alarms (morning, midday, evening)
                cancelAllNudgeAlarms(alarmManager)
                return
            }

            // DISABLED: Brief times (8 AM, 2 PM, 6 PM) are handled by Firebase functions
            // LocalNudgeReceiver now only acts as a fallback for non-brief times
            // scheduleNudgeAlarm(alarmManager, 8, 0, 1001)  // 8:00 AM - Morning - DISABLED (handled by briefs)
            // scheduleNudgeAlarm(alarmManager, 14, 0, 1002) // 2:00 PM - Midday - DISABLED (handled by briefs)
            // scheduleNudgeAlarm(alarmManager, 18, 0, 1003) // 6:00 PM - Evening - DISABLED (handled by briefs)

        } catch (e: Exception) {
            // Log error but don't fail the settings update
            android.util.Log.e("SettingsViewModel", "Failed to update local notifications", e)
        }
    }

    /**
     * Schedule a single nudge alarm
     */
    private fun scheduleNudgeAlarm(alarmManager: AlarmManager, hour: Int, minute: Int, requestCode: Int) {
        val intent = Intent(context, LocalNudgeReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)

            // If it's already past the scheduled time today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    /**
     * Cancel all nudge alarms
     */
    private fun cancelAllNudgeAlarms(alarmManager: AlarmManager) {
        val requestCodes = arrayOf(1001, 1002, 1003) // Morning, Midday, Evening

        for (requestCode in requestCodes) {
            val intent = Intent(context, LocalNudgeReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    class Factory(
        private val repository: FirebaseRepository,
        private val preferencesManager: PreferencesManager,
        private val context: Context,
        private val userId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(repository, preferencesManager, context, userId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
