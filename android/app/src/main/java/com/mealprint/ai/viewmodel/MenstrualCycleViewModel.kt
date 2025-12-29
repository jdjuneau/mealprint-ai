package com.coachie.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.data.model.CyclePhase
import com.mealprint.ai.data.model.FlowIntensity
import com.mealprint.ai.data.model.HealthLog
import com.mealprint.ai.data.model.MenstrualCycleData
import com.mealprint.ai.data.model.MenstrualSymptom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class MenstrualCycleUiState(
    val cycleData: MenstrualCycleData? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedDate: LocalDate = LocalDate.now(),
    val showPeriodStartDialog: Boolean = false,
    val showPeriodEndDialog: Boolean = false,
    val showSymptomDialog: Boolean = false,
    val selectedSymptoms: Set<String> = emptySet(),
    val painLevel: Int = 1,
    val flowIntensity: FlowIntensity = FlowIntensity.MEDIUM,
    val notes: String = "",
    val recentLogs: List<CycleLog> = emptyList()
)

data class CycleLog(
    val date: LocalDate,
    val type: String
)

class MenstrualCycleViewModel : ViewModel() {
    private val repository = FirebaseRepository.getInstance()

    private val _uiState = MutableStateFlow(MenstrualCycleUiState())
    val uiState: StateFlow<MenstrualCycleUiState> = _uiState.asStateFlow()

    // Removed init block - data will be loaded when screen appears with userId

    fun loadCycleData(userId: String = "") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val result = repository.getMenstrualCycleData(userId)
                result.onSuccess { data ->
                    _uiState.value = _uiState.value.copy(
                        cycleData = data,
                        isLoading = false
                    )
                }.onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.localizedMessage ?: "Failed to load cycle data",
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

    fun saveCycleData(data: MenstrualCycleData, userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val result = repository.saveMenstrualCycleData(userId, data)
                result.onSuccess {
                    _uiState.value = _uiState.value.copy(
                        cycleData = data,
                        isLoading = false
                    )
                }.onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.localizedMessage ?: "Failed to save cycle data",
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

    fun loadRecentLogs(userId: String = "", days: Int = 30) {
        viewModelScope.launch {
            try {
                // Load logs for the past month
                val logs = mutableListOf<HealthLog.MenstrualLog>()
                val today = LocalDate.now()

                for (i in 0 until days) {
                    val date = today.minusDays(i.toLong())
                    val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val result = repository.getHealthLogsByType(userId, dateString, HealthLog.MenstrualLog.TYPE)

                    result.onSuccess { dayLogs ->
                        logs.addAll(dayLogs.filterIsInstance<HealthLog.MenstrualLog>())
                    }
                }

                logs.sortByDescending { it.timestamp }
                val cycleLogs = logs.map { menstrualLog ->
                    CycleLog(
                        date = java.time.Instant.ofEpochMilli(menstrualLog.timestamp)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate(),
                        type = menstrualLog.type
                    )
                }
                _uiState.value = _uiState.value.copy(recentLogs = cycleLogs.take(20)) // Show last 20 entries
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.localizedMessage)
            }
        }
    }

    fun logPeriodStart(date: LocalDate, flowIntensity: FlowIntensity, userId: String) {
        viewModelScope.launch {
            val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val timestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val log = HealthLog.MenstrualLog(
                isPeriodStart = true,
                flowIntensity = flowIntensity.name,
                timestamp = timestamp
            )

            try {
                repository.saveHealthLog(userId, dateString, log)
                loadRecentLogs(userId)
                updateCycleDataWithPeriodStart(timestamp, userId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.localizedMessage)
            }
        }
    }

    fun logPeriodEnd(date: LocalDate, userId: String) {
        viewModelScope.launch {
            val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val timestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val log = HealthLog.MenstrualLog(
                isPeriodEnd = true,
                timestamp = timestamp
            )

            try {
                repository.saveHealthLog(userId, dateString, log)
                loadRecentLogs(userId)
                updateCycleDataWithPeriodEnd(timestamp, userId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.localizedMessage)
            }
        }
    }

    fun logSymptoms(
        date: LocalDate,
        symptoms: Set<MenstrualSymptom>,
        painLevel: Int,
        notes: String,
        userId: String
    ) {
        viewModelScope.launch {
            val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val timestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val log = HealthLog.MenstrualLog(
                symptoms = symptoms.map { it.name },
                painLevel = painLevel,
                notes = notes.takeIf { it.isNotBlank() },
                timestamp = timestamp
            )

            try {
                repository.saveHealthLog(userId, dateString, log)
                loadRecentLogs(userId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.localizedMessage)
            }
        }
    }

    private fun updateCycleDataWithPeriodStart(timestamp: Long, userId: String) {
        val currentData = _uiState.value.cycleData ?: MenstrualCycleData(userId = userId)
        val updatedData = currentData.copy(lastPeriodStart = timestamp)
        saveCycleData(updatedData, userId)
    }

    private fun updateCycleDataWithPeriodEnd(timestamp: Long, userId: String) {
        // This would be more complex - we'd need to calculate cycle length
        // For now, just update the cycle data to trigger recalculation
        loadCycleData(userId)
    }

    fun updateSelectedDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
    }

    fun showPeriodStartDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showPeriodStartDialog = show)
    }

    fun showPeriodEndDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showPeriodEndDialog = show)
    }

    fun showSymptomDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSymptomDialog = show)
    }

    fun addSymptom(symptom: String) {
        val currentSymptoms = _uiState.value.selectedSymptoms
        _uiState.value = _uiState.value.copy(selectedSymptoms = currentSymptoms + symptom)
    }

    fun removeSymptom(symptom: String) {
        val currentSymptoms = _uiState.value.selectedSymptoms
        _uiState.value = _uiState.value.copy(selectedSymptoms = currentSymptoms - symptom)
    }

    fun updatePainLevel(level: Int) {
        _uiState.value = _uiState.value.copy(painLevel = level)
    }

    fun updateFlowIntensity(intensity: FlowIntensity) {
        _uiState.value = _uiState.value.copy(flowIntensity = intensity)
    }

    fun updateNotes(notes: String) {
        _uiState.value = _uiState.value.copy(notes = notes)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
