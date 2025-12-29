package com.coachie.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.HealthLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class MoodTrackerUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedDate: LocalDate = LocalDate.now(),
    val moodLevel: Int = 3, // 1-5 scale
    val selectedEmotions: Set<String> = emptySet(),
    val energyLevel: Int = 5, // 1-10 scale
    val sleepQuality: Int = 5, // 1-10 scale
    val triggers: String = "",
    val selectedTriggers: Set<String> = emptySet(),
    val stressLevel: Int = 5, // 1-10 scale
    val socialInteraction: String = "",
    val physicalActivity: String = "",
    val selectedContexts: Set<String> = emptySet(),
    val activities: String = "",
    val notes: String = "",
    val recentMoods: List<HealthLog.MoodLog> = emptyList(),
    val showSaveDialog: Boolean = false
)

class MoodTrackerViewModel : ViewModel() {
    private val repository = FirebaseRepository.getInstance()

    private val _uiState = MutableStateFlow(MoodTrackerUiState())
    val uiState: StateFlow<MoodTrackerUiState> = _uiState.asStateFlow()

    init {
        loadRecentMoods()
    }

    fun loadRecentMoods(userId: String = "", days: Int = 30) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Load mood logs for the past month
                val moods = mutableListOf<HealthLog.MoodLog>()
                val today = LocalDate.now()

                for (i in 0 until days) {
                    val date = today.minusDays(i.toLong())
                    val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val result = repository.getHealthLogsByType(userId, dateString, HealthLog.MoodLog.TYPE)

                    result.onSuccess { dayLogs ->
                        moods.addAll(dayLogs.filterIsInstance<HealthLog.MoodLog>())
                    }
                }

                moods.sortByDescending { it.timestamp }
                _uiState.value = _uiState.value.copy(
                    recentMoods = moods.take(20), // Show last 20 entries
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.localizedMessage ?: "Failed to load mood data",
                    isLoading = false
                )
            }
        }
    }

    fun saveMoodLog(userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val state = _uiState.value
                val dateString = state.selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val timestamp = state.selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

                val moodLog = HealthLog.MoodLog(
                    level = state.moodLevel,
                    emotions = state.selectedEmotions.toList(),
                    energyLevel = state.energyLevel,
                    sleepQuality = state.sleepQuality,
                    triggers = state.selectedTriggers.toList(),
                    stressLevel = state.stressLevel,
                    socialInteraction = state.socialInteraction,
                    physicalActivity = state.physicalActivity,
                    note = state.notes.takeIf { it.isNotBlank() },
                    timestamp = timestamp
                )

                val result = repository.saveHealthLog(userId, dateString, moodLog)

                result.onSuccess {
                    // Reset form
                    _uiState.value = _uiState.value.copy(
                        moodLevel = 3,
                        selectedEmotions = emptySet(),
                        energyLevel = 5,
                        sleepQuality = 3,
                        selectedTriggers = emptySet(),
                        stressLevel = 5,
                        socialInteraction = "moderate",
                        physicalActivity = "light",
                        notes = "",
                        isLoading = false,
                        showSaveDialog = false
                    )
                    loadRecentMoods(userId)
                }.onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.localizedMessage ?: "Failed to save mood log",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.localizedMessage ?: "Unknown error",
                    isLoading = false
                )
            }
        }
    }

    fun updateSelectedDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
    }


    fun updateMoodLevel(level: Int) {
        _uiState.value = _uiState.value.copy(moodLevel = level)
    }

    fun addEmotion(emotion: String) {
        val currentEmotions = _uiState.value.selectedEmotions
        _uiState.value = _uiState.value.copy(selectedEmotions = currentEmotions + emotion)
    }

    fun removeEmotion(emotion: String) {
        val currentEmotions = _uiState.value.selectedEmotions
        _uiState.value = _uiState.value.copy(selectedEmotions = currentEmotions - emotion)
    }

    fun updateEnergyLevel(level: Int) {
        _uiState.value = _uiState.value.copy(energyLevel = level)
    }

    fun updateSleepQuality(quality: Int) {
        _uiState.value = _uiState.value.copy(sleepQuality = quality)
    }

    fun updateStressLevel(level: Int) {
        _uiState.value = _uiState.value.copy(stressLevel = level)
    }

    fun updateTriggers(triggers: String) {
        _uiState.value = _uiState.value.copy(triggers = triggers)
    }

    fun addContext(context: String) {
        val currentContexts = _uiState.value.selectedContexts
        _uiState.value = _uiState.value.copy(selectedContexts = currentContexts + context)
    }

    fun removeContext(context: String) {
        val currentContexts = _uiState.value.selectedContexts
        _uiState.value = _uiState.value.copy(selectedContexts = currentContexts - context)
    }

    fun updateActivities(activities: String) {
        _uiState.value = _uiState.value.copy(activities = activities)
    }

    fun updateNotes(notes: String) {
        _uiState.value = _uiState.value.copy(notes = notes)
    }

    fun showSaveDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSaveDialog = show)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
