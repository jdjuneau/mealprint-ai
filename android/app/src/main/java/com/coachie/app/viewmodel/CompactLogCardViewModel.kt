package com.coachie.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.DailyLog
import com.coachie.app.data.model.Streak
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * ViewModel for the compact daily log card in HomeScreen
 */
class CompactLogCardViewModel(
    private val repository: FirebaseRepository,
    private val userId: String
) : ViewModel() {

    private val _todayLog = MutableStateFlow<DailyLog?>(null)
    val todayLog: StateFlow<DailyLog?> = _todayLog.asStateFlow()

    private val _streak = MutableStateFlow<Streak?>(null)
    val streak: StateFlow<Streak?> = _streak.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _useImperial = MutableStateFlow(true) // Default to imperial
    val useImperial: StateFlow<Boolean> = _useImperial.asStateFlow()

    private val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    init {
        loadTodayLog()
        loadStreak()
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

    /**
     * Load today's log from Firestore
     */
    private fun loadTodayLog() {
        viewModelScope.launch {
            val result = repository.getDailyLog(userId, today)
            result.getOrNull()?.let { log ->
                _todayLog.value = log
            } ?: run {
                // Create empty log for today
                _todayLog.value = DailyLog.createForDate(userId, today)
            }
        }
    }

    /**
     * Load user's streak information
     */
    private fun loadStreak() {
        viewModelScope.launch {
            val result = repository.getUserStreak(userId)
            result.getOrNull()?.let { streak ->
                _streak.value = streak
            }
        }
    }

    /**
     * Add water (250ml)
     */
    fun addWater() {
        val currentLog = _todayLog.value ?: DailyLog.createForDate(userId, today)
        val currentWater = currentLog.water ?: 0
        val updatedLog = currentLog.copy(water = currentWater + 250)
        saveLog(updatedLog)
    }

    /**
     * Set mood (1-5)
     */
    fun setMood(mood: Int) {
        val currentLog = _todayLog.value ?: DailyLog.createForDate(userId, today)
        val updatedLog = currentLog.copy(mood = mood.coerceIn(1, 5))
        saveLog(updatedLog)
    }

    /**
     * Set energy level (1-5)
     */
    fun setEnergy(energy: Int) {
        val currentLog = _todayLog.value ?: DailyLog.createForDate(userId, today)
        val updatedLog = currentLog.copy(energy = energy.coerceIn(1, 5))
        saveLog(updatedLog)
    }

    /**
     * Save log to Firestore
     */
    private fun saveLog(log: DailyLog) {
        _isSaving.value = true
        viewModelScope.launch {
            try {
                android.util.Log.d("CompactLogCardViewModel", "Saving log: water=${log.water}, mood=${log.mood}, energy=${log.energy}")
                val result = repository.saveDailyLog(log)
                if (result.isSuccess) {
                    android.util.Log.d("CompactLogCardViewModel", "Log saved successfully, reloading from database...")
                    // Reload from database to ensure we have the latest data
                    loadTodayLog()
                    loadStreak() // Refresh streak after saving
                } else {
                    val error = result.exceptionOrNull()
                    android.util.Log.e("CompactLogCardViewModel", "Failed to save log: ${error?.message}", error)
                }
            } catch (e: Exception) {
                android.util.Log.e("CompactLogCardViewModel", "Error saving log", e)
            } finally {
                _isSaving.value = false
            }
        }
    }

    /**
     * Get water goal progress (0.0 to 1.0)
     */
    val waterProgress: Float
        get() {
            val water = _todayLog.value?.water ?: 0
            return (water / 2000f).coerceIn(0f, 1f)
        }

    /**
     * Get water percentage
     */
    val waterPercentage: Int
        get() {
            val water = _todayLog.value?.water ?: 0
            return ((water / 2000f) * 100).toInt().coerceIn(0, 100)
        }

    class Factory(
        private val repository: FirebaseRepository,
        private val userId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CompactLogCardViewModel::class.java)) {
                return CompactLogCardViewModel(repository, userId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
