package com.coachie.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.DailyLog
import com.coachie.app.data.model.HealthLog
import com.coachie.app.data.model.MicronutrientType
import com.coachie.app.data.model.Supplement
import com.coachie.app.data.model.toMicronutrientChecklist
import com.coachie.app.data.model.toPersistedMicronutrientChecklist
import com.coachie.app.data.model.toPersistedMicronutrientMap
import com.coachie.app.util.SunshineVitaminDCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MicronutrientTrackerViewModel(
    private val repository: FirebaseRepository,
    private val userId: String
) : ViewModel() {
    
    companion object {
        private const val TAG = "MicronutrientTrackerVM"
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    private val _mealTotals = MutableStateFlow<Map<MicronutrientType, Double>>(emptyMap())
    val mealTotals: StateFlow<Map<MicronutrientType, Double>> = _mealTotals.asStateFlow()

    private val _supplementTotals = MutableStateFlow<Map<MicronutrientType, Double>>(emptyMap())
    val supplementTotals: StateFlow<Map<MicronutrientType, Double>> = _supplementTotals.asStateFlow()

    private val _supplements = MutableStateFlow<List<HealthLog.SupplementLog>>(emptyList())
    val supplements: StateFlow<List<HealthLog.SupplementLog>> = _supplements.asStateFlow()

    private val _sunshineLogs = MutableStateFlow<List<HealthLog.SunshineLog>>(emptyList())
    val sunshineLogs: StateFlow<List<HealthLog.SunshineLog>> = _sunshineLogs.asStateFlow()

    private val _savedSupplements = MutableStateFlow<List<Supplement>>(emptyList())
    val savedSupplements: StateFlow<List<Supplement>> = _savedSupplements.asStateFlow()

    private val _manualOverrides = MutableStateFlow<Map<MicronutrientType, Boolean>>(emptyMap())
    val manualOverrides: StateFlow<Map<MicronutrientType, Boolean>> = _manualOverrides.asStateFlow()

    private val _gender = MutableStateFlow<String?>(null)
    val gender: StateFlow<String?> = _gender.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _sunshineTotals = MutableStateFlow<Map<MicronutrientType, Double>>(emptyMap())
    val sunshineTotals: StateFlow<Map<MicronutrientType, Double>> = _sunshineTotals.asStateFlow()

    val combinedTotals: StateFlow<Map<MicronutrientType, Double>> =
        combine(_mealTotals, _supplementTotals, _sunshineTotals) { meals, supplements, sunshine ->
            val totals = meals.toMutableMap()
            supplements.forEach { (type, amount) ->
                totals[type] = (totals[type] ?: 0.0) + amount
            }
            sunshine.forEach { (type, amount) ->
                totals[type] = (totals[type] ?: 0.0) + amount
            }
            totals
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private var currentDate: String = LocalDate.now().format(DATE_FORMAT)
    private var currentDailyLog: DailyLog? = null
    private var dailySupplementsEnsured = false

    init {
        refresh()
    }

    fun refresh() {
        dailySupplementsEnsured = false
        viewModelScope.launch {
            loadData(force = true)
        }
    }

    private suspend fun loadData(force: Boolean = false) {
        _isLoading.value = true
        currentDate = LocalDate.now().format(DATE_FORMAT)

        if (force) {
            dailySupplementsEnsured = false
        }

        try {
            val logsResult = repository.getHealthLogs(userId, currentDate)
            val healthLogs = logsResult.getOrNull() ?: emptyList()
            val mealTotalsMap = healthLogs
                .filterIsInstance<HealthLog.MealLog>()
                .flatMap { it.micronutrientsTyped.entries }
                .groupBy({ it.key }) { it.value }
                .mapValues { (_, values) -> values.sum() }
            _mealTotals.value = mealTotalsMap

            val supplementLogs = healthLogs.filterIsInstance<HealthLog.SupplementLog>()
            _supplements.value = supplementLogs
            val supplementTotalsMap = supplementLogs
                .flatMap { it.micronutrientsTyped.entries }
                .groupBy({ it.key }) { it.value }
                .mapValues { (_, values) -> values.sum() }
            _supplementTotals.value = supplementTotalsMap

            val sunshineEntries = healthLogs.filterIsInstance<HealthLog.SunshineLog>()
            _sunshineLogs.value = sunshineEntries
            val sunshineTotal = sunshineEntries.sumOf { it.vitaminDIu }
            _sunshineTotals.value =
                if (sunshineTotal > 0) mapOf(MicronutrientType.VITAMIN_D to sunshineTotal) else emptyMap()

            // Validate userId before calling getSupplements to prevent Firestore path errors
            if (userId.isBlank()) {
                android.util.Log.w(TAG, "userId is blank, skipping getSupplements")
                _savedSupplements.value = emptyList()
                _isLoading.value = false
                _statusMessage.value = "User not authenticated"
                return
            }
            
            Log.d(TAG, "Loading supplements for userId: $userId")
            val savedSupplementsResult = repository.getSupplements(userId)
            
            val savedSupplementsList = savedSupplementsResult.fold(
                onSuccess = { supplementsList ->
                    Log.d(TAG, "Successfully loaded ${supplementsList.size} supplements from repository")
                    supplementsList.forEach { supplement ->
                        Log.d(TAG, "  - ${supplement.name} (id: ${supplement.id}, daily: ${supplement.isDaily})")
                    }
                    
                    // Always set saved supplements first, before any other logic
                    // This ensures they're displayed even if there's a recursive call for daily supplements
                    val sorted = supplementsList.sortedBy { it.name.lowercase(Locale.getDefault()) }
                    _savedSupplements.value = sorted
                    
                    Log.d(TAG, "Set _savedSupplements to ${_savedSupplements.value.size} supplements")
                    sorted
                },
                onFailure = { error ->
                    Log.e(TAG, "Error loading saved supplements: ${error.message}", error)
                    error.printStackTrace()
                    _statusMessage.value = error.message ?: "Failed to load saved supplements"
                    _savedSupplements.value = emptyList()
                    emptyList<Supplement>()
                }
            )

            if (!dailySupplementsEnsured) {
                val missingDaily = savedSupplementsList.filter { supplement ->
                    supplement.isDaily && supplementLogs.none { log -> log.name.equals(supplement.name, ignoreCase = true) }
                }

                if (missingDaily.isNotEmpty()) {
                    missingDaily.forEach { supplement ->
                        val log = HealthLog.SupplementLog(
                            name = supplement.name,
                            micronutrients = supplement.micronutrients
                        )
                        repository.saveHealthLog(userId, currentDate, log).getOrThrow()
                    }
                    dailySupplementsEnsured = true
                    // Reload data to refresh supplement logs, but saved supplements are already set
                    loadData()
                    return
                }
                dailySupplementsEnsured = true
            }

            val dailyLogResult = repository.getDailyLog(userId, currentDate)
            val dailyLog = dailyLogResult.getOrNull()
            currentDailyLog = dailyLog ?: DailyLog(uid = userId, date = currentDate)

            val overrides = dailyLog?.micronutrientChecklist?.toMicronutrientChecklist() ?: emptyMap()
            _manualOverrides.value = overrides

            // Validate userId before calling getUserGoals to prevent Firestore path errors
            if (userId.isNotBlank()) {
                val goalsResult = repository.getUserGoals(userId)
                val genderValue = goalsResult.getOrNull()?.get("gender") as? String
                _gender.value = genderValue
            } else {
                android.util.Log.w(TAG, "userId is blank, skipping getUserGoals")
                _gender.value = null
            }

            _isLoading.value = false
        } catch (e: Exception) {
            _isLoading.value = false
            _statusMessage.value = "Failed to load micronutrients: ${e.message}"
        }
    }

    fun toggleGoal(type: MicronutrientType, isChecked: Boolean) {
        _manualOverrides.value = _manualOverrides.value + (type to isChecked)
    }

    fun saveMicronutrients() {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val overridesToSave = _manualOverrides.value
                    .toPersistedMicronutrientChecklist()

                val baseLog = (currentDailyLog ?: DailyLog(uid = userId, date = currentDate)).copy(
                    micronutrientExtras = emptyMap(),
                    micronutrientChecklist = overridesToSave,
                    updatedAt = System.currentTimeMillis()
                )

                repository.saveDailyLog(baseLog)
                currentDailyLog = baseLog
                _statusMessage.value = "Micronutrient tracker saved"
            } catch (e: Exception) {
                _statusMessage.value = "Failed to save: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun addSupplement(
        name: String,
        micronutrients: Map<MicronutrientType, Double>,
        saveForFuture: Boolean,
        markDaily: Boolean
    ) {
        if (name.isBlank() || micronutrients.isEmpty()) {
            _statusMessage.value = "Enter a name and at least one nutrient"
            return
        }

        val shouldSaveTemplate = saveForFuture || markDaily

        viewModelScope.launch {
            _isSaving.value = true
            try {
                val persistedMicros = micronutrients.toPersistedMicronutrientMap()
                val log = HealthLog.SupplementLog(
                    name = name.trim(),
                    micronutrients = persistedMicros
                )
                repository.saveHealthLog(userId, currentDate, log).getOrThrow()

                if (shouldSaveTemplate) {
                    val trimmedName = name.trim()
                    val existing = _savedSupplements.value.firstOrNull {
                        it.name.equals(trimmedName, ignoreCase = true)
                    }
                    val now = System.currentTimeMillis()
                    val supplement = Supplement(
                        id = existing?.id ?: "",
                        name = trimmedName,
                        micronutrients = persistedMicros,
                        isDaily = markDaily,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now
                    )
                    val savedId = repository.upsertSupplement(userId, supplement).getOrThrow()
                    updateSavedSupplementState(supplement.copy(id = savedId))
                    dailySupplementsEnsured = false
                }

                _statusMessage.value = if (shouldSaveTemplate) {
                    "Supplement saved and logged"
                } else {
                    "Supplement logged"
                }
                loadData(force = true)
            } catch (e: Exception) {
                _statusMessage.value = "Failed to save supplement: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun logSunshineExposure(
        minutes: Int,
        uvIndex: Double,
        exposure: SunshineVitaminDCalculator.ExposureLevel,
        skinType: SunshineVitaminDCalculator.SkinType
    ) {
        if (minutes <= 0) {
            _statusMessage.value = "Enter sunshine minutes"
            return
        }
        if (uvIndex <= 0.0) {
            _statusMessage.value = "Enter UV index"
            return
        }

        viewModelScope.launch {
            _isSaving.value = true
            try {
                val vitaminD = SunshineVitaminDCalculator.estimateVitaminD(minutes, uvIndex, exposure, skinType)
                val now = System.currentTimeMillis()
                val log = HealthLog.SunshineLog(
                    minutes = minutes,
                    uvIndex = uvIndex,
                    bodyCoverage = exposure.bodyCoverageFraction,
                    skinType = skinType.id,
                    vitaminDIu = vitaminD,
                    timestamp = now
                )

                repository.saveHealthLog(userId, currentDate, log).getOrThrow()
                _statusMessage.value = "Sunshine logged (~${vitaminD.toInt()} IU vitamin D)"
                loadData(force = true)
            } catch (e: Exception) {
                _statusMessage.value = "Failed to log sunshine: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun logSavedSupplement(supplementId: String) {
        val supplement = _savedSupplements.value.firstOrNull { it.id == supplementId } ?: run {
            _statusMessage.value = "Supplement not found"
            return
        }

        viewModelScope.launch {
            _isSaving.value = true
            try {
                val log = HealthLog.SupplementLog(
                    name = supplement.name,
                    micronutrients = supplement.micronutrients
                )
                repository.saveHealthLog(userId, currentDate, log).getOrThrow()
                _statusMessage.value = "Logged ${supplement.name}"
                loadData(force = true)
            } catch (e: Exception) {
                _statusMessage.value = "Failed to log supplement: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun toggleSupplementDaily(supplementId: String, isDaily: Boolean) {
        val supplement = _savedSupplements.value.firstOrNull { it.id == supplementId } ?: run {
            _statusMessage.value = "Supplement not found"
            return
        }

        if (supplement.isDaily == isDaily) return

        viewModelScope.launch {
            _isSaving.value = true
            try {
                val updatedSupplement = supplement.copy(isDaily = isDaily, updatedAt = System.currentTimeMillis())
                repository.upsertSupplement(userId, updatedSupplement).getOrThrow()
                updateSavedSupplementState(updatedSupplement)
                _statusMessage.value = if (isDaily) {
                    "Marked ${supplement.name} as daily"
                } else {
                    "Stopped daily schedule for ${supplement.name}"
                }
                dailySupplementsEnsured = false
                loadData(force = true)
            } catch (e: Exception) {
                _statusMessage.value = "Failed to update supplement: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    private fun updateSavedSupplementState(supplement: Supplement) {
        _savedSupplements.update { currentList ->
            val filtered = currentList.filterNot { existing ->
                existing.id == supplement.id ||
                    existing.name.equals(supplement.name, ignoreCase = true)
            }
            (filtered + supplement).sortedBy { it.name.lowercase(Locale.getDefault()) }
        }
    }

    class Factory(
        private val repository: FirebaseRepository,
        private val userId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MicronutrientTrackerViewModel::class.java)) {
                return MicronutrientTrackerViewModel(repository, userId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}


