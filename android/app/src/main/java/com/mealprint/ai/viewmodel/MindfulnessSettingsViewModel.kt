package com.coachie.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealprint.ai.data.local.PreferencesManager
import com.mealprint.ai.service.MindfulnessReminderService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MindfulnessSettingsViewModel : ViewModel() {

    private val _reminderEnabled = MutableStateFlow(false)
    val reminderEnabled: StateFlow<Boolean> = _reminderEnabled.asStateFlow()

    private val _reminderHour = MutableStateFlow(14) // 2 PM default
    val reminderHour: StateFlow<Int> = _reminderHour.asStateFlow()

    private val _reminderMinute = MutableStateFlow(0)
    val reminderMinute: StateFlow<Int> = _reminderMinute.asStateFlow()

    private val _defaultDuration = MutableStateFlow(10) // 10 minutes default
    val defaultDuration: StateFlow<Int> = _defaultDuration.asStateFlow()

    private var context: Context? = null
    private var reminderService: MindfulnessReminderService? = null
    private var preferencesManager: PreferencesManager? = null

    fun initialize(context: Context) {
        this.context = context
        this.reminderService = MindfulnessReminderService(context)
        this.preferencesManager = PreferencesManager(context)

        // Load saved preferences
        viewModelScope.launch {
            preferencesManager?.let { prefs ->
                _reminderEnabled.value = prefs.getMindfulnessRemindersEnabled()
                _reminderHour.value = prefs.getMindfulnessReminderHour()
                _reminderMinute.value = prefs.getMindfulnessReminderMinute()
                _defaultDuration.value = prefs.getDefaultMeditationDuration()
            }
        }
    }

    suspend fun setReminderEnabled(enabled: Boolean) {
        _reminderEnabled.value = enabled
        preferencesManager?.setMindfulnessRemindersEnabled(enabled)

        if (enabled) {
            reminderService?.scheduleDailyReminder(_reminderHour.value, _reminderMinute.value)
        } else {
            reminderService?.cancelReminders()
        }
    }

    suspend fun setReminderTime(hour: Int, minute: Int) {
        _reminderHour.value = hour
        _reminderMinute.value = minute

        preferencesManager?.setMindfulnessReminderHour(hour)
        preferencesManager?.setMindfulnessReminderMinute(minute)

        // Reschedule if reminders are enabled
        if (_reminderEnabled.value) {
            reminderService?.cancelReminders()
            reminderService?.scheduleDailyReminder(hour, minute)
        }
    }

    suspend fun setDefaultDuration(duration: Int) {
        _defaultDuration.value = duration
        preferencesManager?.setDefaultMeditationDuration(duration)
    }
}
