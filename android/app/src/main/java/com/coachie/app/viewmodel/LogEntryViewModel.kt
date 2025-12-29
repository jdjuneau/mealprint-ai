package com.coachie.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.DailyLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed class LogEntryUiState {
    data class Success(
        val weight: Double? = null,
        val water: Int = 0,
        val mood: Int? = null
    ) : LogEntryUiState() {
        val canSave: Boolean
            get() = weight != null || water > 0 || mood != null
    }

    object Saving : LogEntryUiState()
    data class Error(val message: String) : LogEntryUiState()
}

class LogEntryViewModel(
    private val repository: FirebaseRepository,
    private val userId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<LogEntryUiState>(
        LogEntryUiState.Success()
    )
    val uiState: StateFlow<LogEntryUiState> = _uiState

    private val _useImperial = MutableStateFlow(true) // Default to imperial
    val useImperial: StateFlow<Boolean> = _useImperial.asStateFlow()

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val today = LocalDate.now().format(dateFormatter)

    init {
        loadUserPreferences()
    }
    
    /**
     * Load user preferences for measurement units
     */
    private fun loadUserPreferences() {
        viewModelScope.launch {
            try {
                val goalsResult = repository.getUserGoals(userId)
                val goals = goalsResult.getOrNull()
                val useImperialValue = goals?.get("useImperial") as? Boolean ?: true
                _useImperial.value = useImperialValue
            } catch (e: Exception) {
                // Default to imperial on error
                _useImperial.value = true
            }
        }
    }

    fun updateWeight(weight: Double?) {
        val currentState = _uiState.value
        if (currentState is LogEntryUiState.Success) {
            _uiState.value = currentState.copy(weight = weight)
        }
    }

    fun updateWater(water: Int) {
        val currentState = _uiState.value
        if (currentState is LogEntryUiState.Success) {
            _uiState.value = currentState.copy(water = water.coerceIn(0, 3000))
        }
    }

    fun updateMood(mood: Int) {
        val currentState = _uiState.value
        if (currentState is LogEntryUiState.Success) {
            _uiState.value = currentState.copy(mood = mood.coerceIn(1, 5))
        }
    }

    fun saveEntry(onSuccess: () -> Unit = {}) {
        val currentState = _uiState.value
        if (currentState is LogEntryUiState.Success && currentState.canSave) {
            _uiState.value = LogEntryUiState.Saving

            viewModelScope.launch {
                try {
                    // First, try to get existing log for today
                    val existingLogResult = repository.getDailyLog(userId, today)

                    val logToSave = if (existingLogResult.isSuccess && existingLogResult.getOrNull() != null) {
                        // Update existing log
                        val existingLog = existingLogResult.getOrNull()!!
                        existingLog.copy(
                            weight = currentState.weight ?: existingLog.weight,
                            water = if (currentState.water > 0) currentState.water else existingLog.water,
                            mood = currentState.mood ?: existingLog.mood,
                            updatedAt = System.currentTimeMillis()
                        )
                    } else {
                        // Create new log
                        DailyLog.createForDate(userId, today).copy(
                            weight = currentState.weight,
                            water = if (currentState.water > 0) currentState.water else null,
                            mood = currentState.mood
                        )
                    }

                    val saveResult = repository.saveDailyLog(logToSave)

                    if (saveResult.isSuccess) {
                        onSuccess()
                    } else {
                        _uiState.value = LogEntryUiState.Error(
                            saveResult.exceptionOrNull()?.message ?: "Failed to save entry"
                        )
                    }
                } catch (e: Exception) {
                    _uiState.value = LogEntryUiState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    fun reset() {
        _uiState.value = LogEntryUiState.Success()
    }

    class Factory(
        private val repository: FirebaseRepository,
        private val userId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LogEntryViewModel::class.java)) {
                return LogEntryViewModel(repository, userId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
