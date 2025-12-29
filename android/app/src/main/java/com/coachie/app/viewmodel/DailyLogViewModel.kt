package com.coachie.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.DailyLog
import com.coachie.app.utils.DebugLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed class DailyLogUiState {
    object Loading : DailyLogUiState()
    data class Success(val log: DailyLog) : DailyLogUiState()
    data class Error(val message: String) : DailyLogUiState()
}

class DailyLogViewModel(
    private val repository: FirebaseRepository,
    private val userId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<DailyLogUiState>(DailyLogUiState.Loading)
    val uiState: StateFlow<DailyLogUiState> = _uiState

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    private val _useImperial = MutableStateFlow(true) // Default to imperial
    val useImperial: StateFlow<Boolean> = _useImperial.asStateFlow()

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    init {
        loadUserPreferences()
        loadTodayLog()
    }

    private fun loadUserPreferences() {
        viewModelScope.launch {
            try {
                val goalsResult = repository.getUserGoals(userId)
                val goals = goalsResult.getOrNull()
                val useImperial = goals?.get("useImperial") as? Boolean ?: true
                DebugLogger.logStateChange("DailyLogViewModel", "useImperial", _useImperial.value, useImperial)
                _useImperial.value = useImperial
            } catch (e: Exception) {
                DebugLogger.logDebug("DailyLogViewModel", "Failed to load user preferences: ${e.message}")
                _useImperial.value = true // Default to imperial
            }
        }
    }

    fun loadTodayLog() {
        loadLogForDate(LocalDate.now())
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        loadLogForDate(date)
    }

    private fun loadLogForDate(date: LocalDate) {
        _uiState.value = DailyLogUiState.Loading

        val dateString = date.format(dateFormatter)

        viewModelScope.launch {
            try {
                val result = repository.getDailyLog(userId, dateString)
                if (result.isSuccess) {
                    val existingLog = result.getOrNull()
                    val log = existingLog ?: DailyLog.createForDate(userId, dateString)
                    _uiState.value = DailyLogUiState.Success(log)
                } else {
                    _uiState.value = DailyLogUiState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to load log"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = DailyLogUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun updateLog(log: DailyLog) {
        val currentState = _uiState.value
        if (currentState is DailyLogUiState.Success) {
            _uiState.value = DailyLogUiState.Success(log)
        }
    }

    fun saveLog() {
        val currentState = _uiState.value
        if (currentState is DailyLogUiState.Success) {
            val log = currentState.log

            viewModelScope.launch {
                try {
                    val result = repository.saveDailyLog(log)
                    if (result.isSuccess) {
                        // Log saved successfully, reload to confirm
                        loadLogForDate(selectedDate.value)
                    } else {
                        _uiState.value = DailyLogUiState.Error(
                            result.exceptionOrNull()?.message ?: "Failed to save log"
                        )
                    }
                } catch (e: Exception) {
                    _uiState.value = DailyLogUiState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    // Quick actions for common logging
    fun logWeight(weight: Double) {
        val currentState = _uiState.value
        if (currentState is DailyLogUiState.Success) {
            val updatedLog = currentState.log.copy(weight = weight)
            updateLog(updatedLog)
            saveLog()
        }
    }

    fun logSteps(steps: Int) {
        val currentState = _uiState.value
        if (currentState is DailyLogUiState.Success) {
            val updatedLog = currentState.log.copy(steps = steps)
            updateLog(updatedLog)
            saveLog()
        }
    }

    fun addWater(amountMl: Int) {
        val currentState = _uiState.value
        if (currentState is DailyLogUiState.Success) {
            val currentWater = currentState.log.water ?: 0
            val updatedLog = currentState.log.copy(water = currentWater + amountMl)
            updateLog(updatedLog)
            saveLog()
        }
    }

    fun logMood(mood: Int) {
        val currentState = _uiState.value
        if (currentState is DailyLogUiState.Success) {
            val updatedLog = currentState.log.copy(mood = mood)
            updateLog(updatedLog)
            saveLog()
        }
    }

    class Factory(
        private val repository: FirebaseRepository,
        private val userId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DailyLogViewModel::class.java)) {
                return DailyLogViewModel(repository, userId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
