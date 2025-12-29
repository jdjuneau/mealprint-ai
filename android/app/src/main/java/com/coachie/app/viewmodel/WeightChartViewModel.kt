package com.coachie.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.local.PreferencesManager
import com.coachie.app.ui.components.WeightDataPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * ViewModel for weight chart data
 */
class WeightChartViewModel(
    private val firebaseRepository: FirebaseRepository,
    private val preferencesManager: PreferencesManager,
    private val userId: String
) : ViewModel() {

    private val _weightData = MutableStateFlow<List<WeightDataPoint>>(emptyList())
    val weightData: StateFlow<List<WeightDataPoint>> = _weightData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _goalWeight = MutableStateFlow<Double?>(null)
    val goalWeight: StateFlow<Double?> = _goalWeight.asStateFlow()

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    init {
        loadWeightData()
        loadGoalWeight()
    }

    /**
     * Load weight data for the last 30 days
     */
    private fun loadWeightData() {
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val endDate = LocalDate.now()
                val startDate = endDate.minusDays(30)

                // Generate date range
                val dates = (0..30).map { startDate.plusDays(it.toLong()) }

                // Load daily logs for each date
                val weightDataPoints = mutableListOf<WeightDataPoint>()

                for (date in dates) {
                    val dateString = date.format(dateFormatter)

                    try {
                        val dailyLogResult = firebaseRepository.getDailyLog(userId, dateString)
                        val weight = if (dailyLogResult.isSuccess) {
                            dailyLogResult.getOrNull()?.weight
                        } else {
                            null
                        }

                        weightDataPoints.add(WeightDataPoint(date, weight))
                    } catch (e: Exception) {
                        // Add point with null weight for missing data
                        weightDataPoints.add(WeightDataPoint(date, null))
                    }
                }

                _weightData.value = weightDataPoints

            } catch (e: Exception) {
                _weightData.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Load user's goal weight from preferences
     */
    private fun loadGoalWeight() {
        // Try to get goal weight from preferences
        // This could be stored when the user sets their goal during onboarding
        val goalWeight = preferencesManager.targetWeight?.toDoubleOrNull()
        _goalWeight.value = goalWeight
    }

    /**
     * Refresh the weight data
     */
    fun refreshData() {
        loadWeightData()
        loadGoalWeight()
    }

    /**
     * Get weight statistics
     */
    fun getWeightStats(): WeightStats? {
        val data = _weightData.value.filter { it.weight != null }
        if (data.isEmpty()) return null

        val weights = data.map { it.weight!! }
        val currentWeight = weights.lastOrNull()
        val previousWeight = weights.getOrNull(weights.size - 2)

        val change = if (currentWeight != null && previousWeight != null) {
            currentWeight - previousWeight
        } else 0.0

        return WeightStats(
            currentWeight = currentWeight,
            previousWeight = previousWeight,
            change = change,
            dataPoints = data.size
        )
    }

    /**
     * Factory for creating WeightChartViewModel
     */
    class Factory(
        private val firebaseRepository: FirebaseRepository,
        private val preferencesManager: PreferencesManager,
        private val userId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(WeightChartViewModel::class.java)) {
                return WeightChartViewModel(firebaseRepository, preferencesManager, userId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

/**
 * Weight statistics data class
 */
data class WeightStats(
    val currentWeight: Double?,
    val previousWeight: Double?,
    val change: Double,
    val dataPoints: Int
) {
    val changeText: String
        get() = when {
            change > 0.1 -> "+${String.format("%.1f", change)}kg"
            change < -0.1 -> "${String.format("%.1f", change)}kg"
            else -> "No change"
        }

    val changeColor: androidx.compose.ui.graphics.Color
        get() = when {
            change < -0.1 -> androidx.compose.ui.graphics.Color(0xFF4CAF50) // Green for weight loss
            change > 0.1 -> androidx.compose.ui.graphics.Color(0xFFF44336) // Red for weight gain
            else -> androidx.compose.ui.graphics.Color(0xFF9E9E9E) // Gray for no change
        }
}
