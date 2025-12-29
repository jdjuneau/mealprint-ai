package com.coachie.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.data.HabitRepository
import com.mealprint.ai.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

data class SmartSchedulingUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val circadianRhythm: CircadianRhythmData = CircadianRhythmData(),
    val environmentalFactors: EnvironmentalFactors = EnvironmentalFactors(),
    val habitSchedules: List<HabitScheduleRecommendation> = emptyList(),
    val optimalTimeSlots: List<TimeSlot> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val userPreferences: SchedulingPreferences = SchedulingPreferences()
)

data class CircadianRhythmData(
    val energyPeaks: List<EnergyPeak> = emptyList(),
    val sleepSchedule: SleepSchedule? = null,
    val averageWakeTime: LocalTime? = null,
    val averageBedTime: LocalTime? = null,
    val energyScore: Double = 0.5,
    val rhythmStrength: Double = 0.0 // 0-1, how consistent their schedule is
)

data class EnergyPeak(
    val time: LocalTime,
    val energyLevel: Double, // 0-1
    val confidence: Double // 0-1, how confident we are in this data
)

data class SleepSchedule(
    val averageSleepHours: Double = 8.0,
    val averageBedTime: LocalTime = LocalTime.of(22, 30),
    val averageWakeTime: LocalTime = LocalTime.of(7, 0),
    val sleepQuality: Double = 0.7 // 0-1
)

data class EnvironmentalFactors(
    val weather: WeatherData? = null,
    val location: LocationData? = null,
    val timeOfDay: TimeOfDay = TimeOfDay.MORNING,
    val dayOfWeek: DayOfWeek = DayOfWeek.WEEKDAY,
    val socialContext: SocialContext = SocialContext.ALONE
)

data class WeatherData(
    val condition: String = "sunny",
    val temperature: Double = 20.0,
    val humidity: Double = 50.0,
    val isOutdoorFriendly: Boolean = true
)

data class LocationData(
    val type: LocationType = LocationType.HOME,
    val isFamiliar: Boolean = true,
    val hasResources: Boolean = true
)

enum class LocationType {
    HOME, WORK, GYM, OUTDOOR, TRAVEL, OTHER
}

enum class TimeOfDay {
    EARLY_MORNING, MORNING, MIDDAY, AFTERNOON, EVENING, NIGHT
}

enum class DayOfWeek {
    WEEKDAY, WEEKEND
}

enum class SocialContext {
    ALONE, WITH_FAMILY, WITH_FRIENDS, AT_WORK, PUBLIC
}

data class HabitScheduleRecommendation(
    val habitId: String,
    val habitName: String,
    val category: HabitCategory,
    val recommendedTime: LocalTime,
    val confidence: Double, // 0-1
    val reasoning: List<String>,
    val alternativeTimes: List<LocalTime> = emptyList(),
    val environmentalFactors: List<String> = emptyList(),
    val successProbability: Double = 0.7
)

data class TimeSlot(
    val time: LocalTime,
    val energyLevel: Double,
    val isOptimal: Boolean,
    val bookedHabits: List<String> = emptyList(),
    val environmentalScore: Double = 0.5
)

data class SchedulingPreferences(
    val preferredWakeTime: LocalTime = LocalTime.of(7, 0),
    val preferredBedTime: LocalTime = LocalTime.of(23, 0),
    val workStartTime: LocalTime = LocalTime.of(9, 0),
    val workEndTime: LocalTime = LocalTime.of(17, 0),
    val preferredEnergyTimes: List<LocalTime> = emptyList(),
    val avoidTimes: List<LocalTime> = emptyList(),
    val preferredLocations: List<LocationType> = emptyList()
)

class SmartSchedulingViewModel(
    private val habitRepository: HabitRepository = HabitRepository.getInstance(),
    private val firebaseRepository: FirebaseRepository = FirebaseRepository.getInstance(),
    private val userId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(SmartSchedulingUiState())
    val uiState: StateFlow<SmartSchedulingUiState> = _uiState.asStateFlow()

    init {
        loadSchedulingData()
    }

    fun loadSchedulingData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Load circadian rhythm data from health logs
                val circadianData = analyzeCircadianRhythm()

                // Load environmental factors
                val environmentalData = getEnvironmentalFactors()

                // Load user habits
                val habits = habitRepository.getUserHabits(userId).first()

                // Generate scheduling recommendations
                val recommendations = generateHabitSchedules(habits, circadianData, environmentalData)

                // Generate optimal time slots
                val timeSlots = generateTimeSlots(circadianData, recommendations)

                _uiState.value = _uiState.value.copy(
                    circadianRhythm = circadianData,
                    environmentalFactors = environmentalData,
                    habitSchedules = recommendations,
                    optimalTimeSlots = timeSlots,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load scheduling data: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    private suspend fun analyzeCircadianRhythm(): CircadianRhythmData {
        // Analyze the last 30 days of health data to understand energy patterns
        val today = LocalDate.now()
        val thirtyDaysAgo = today.minusDays(30)

        val energyPeaks = mutableListOf<EnergyPeak>()
        var totalEnergyScore = 0.0
        var dataPoints = 0
        var rhythmConsistency = 0.0

        // Analyze energy patterns by hour
        for (hour in 0..23) {
            val hourEnergy = analyzeHourlyEnergy(hour, thirtyDaysAgo, today)
            if (hourEnergy.confidence > 0.1) { // Only include hours with reasonable data
                energyPeaks.add(hourEnergy)
                totalEnergyScore += hourEnergy.energyLevel
                dataPoints++
            }
        }

        // Calculate average energy score
        val averageEnergyScore = if (dataPoints > 0) totalEnergyScore / dataPoints else 0.5

        // Analyze sleep patterns
        val sleepSchedule = analyzeSleepPatterns(thirtyDaysAgo, today)

        // Calculate rhythm strength (consistency)
        rhythmConsistency = calculateRhythmStrength(energyPeaks, sleepSchedule)

        return CircadianRhythmData(
            energyPeaks = energyPeaks.sortedBy { it.energyLevel }.reversed(),
            sleepSchedule = sleepSchedule,
            energyScore = averageEnergyScore,
            rhythmStrength = rhythmConsistency
        )
    }

    private suspend fun analyzeHourlyEnergy(hour: Int, startDate: LocalDate, endDate: LocalDate): EnergyPeak {
        // Analyze energy levels for a specific hour across the date range
        // This would look at activity levels, heart rate variability, or self-reported energy
        val energyReadings = mutableListOf<Double>()
        var confidence = 0.0

        // For now, create simulated data based on typical circadian patterns
        val time = LocalTime.of(hour, 0)
        val energyLevel = calculateTypicalEnergyForHour(hour)

        // In a real implementation, this would query actual health data
        confidence = 0.8 // High confidence for core hours, lower for edge hours

        return EnergyPeak(time, energyLevel, confidence)
    }

    private fun calculateTypicalEnergyForHour(hour: Int): Double {
        // Simplified circadian rhythm model
        return when (hour) {
            in 6..9 -> 0.8 // Morning peak
            in 10..12 -> 0.7 // Good morning energy
            in 13..15 -> 0.5 // Post-lunch dip
            in 16..18 -> 0.75 // Afternoon energy
            in 19..21 -> 0.6 // Evening wind-down
            in 22..23, in 0..5 -> 0.3 // Night time
            else -> 0.5
        }
    }

    private suspend fun analyzeSleepPatterns(startDate: LocalDate, endDate: LocalDate): SleepSchedule {
        // Analyze sleep data from health logs
        val sleepLogs = mutableListOf<HealthLog.SleepLog>()
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        // Collect sleep data over the period
        for (i in 0..29) {
            val date = startDate.plusDays(i.toLong())
            val dateStr = date.format(dateFormatter)

            try {
                val logs = firebaseRepository.getHealthLogsByType("", dateStr, HealthLog.SleepLog.TYPE)
                logs.getOrNull()?.filterIsInstance<HealthLog.SleepLog>()?.let { sleepLogs.addAll(it) }
            } catch (e: Exception) {
                // Continue if date has no data
            }
        }

        if (sleepLogs.isEmpty()) {
            return SleepSchedule() // Default schedule
        }

        val avgSleepHours = sleepLogs.map { it.durationHours }.average()
        val avgBedTime = calculateAverageTime(sleepLogs.map {
            java.time.Instant.ofEpochMilli(it.startTime)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalTime()
        })
        val avgWakeTime = calculateAverageTime(sleepLogs.map {
            java.time.Instant.ofEpochMilli(it.endTime)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalTime()
        })
        val avgQuality = sleepLogs.map { it.quality / 10.0 }.average() // Assuming quality is 1-10 scale

        return SleepSchedule(
            averageSleepHours = avgSleepHours,
            averageBedTime = avgBedTime,
            averageWakeTime = avgWakeTime,
            sleepQuality = avgQuality
        )
    }

    private fun calculateAverageTime(times: List<LocalTime>): LocalTime {
        if (times.isEmpty()) return LocalTime.of(7, 0) // Default

        val totalMinutes = times.sumOf { it.hour * 60 + it.minute }
        val avgMinutes = totalMinutes / times.size
        val hours = avgMinutes / 60
        val minutes = avgMinutes % 60

        return LocalTime.of(hours, minutes)
    }

    private fun calculateRhythmStrength(energyPeaks: List<EnergyPeak>, sleepSchedule: SleepSchedule?): Double {
        // Calculate how consistent the user's energy patterns are
        var consistency = 0.0

        // Check if energy peaks align with expected circadian rhythm
        val hasMorningPeak = energyPeaks.any { it.time.hour in 6..10 && it.energyLevel > 0.7 }
        val hasEveningDip = energyPeaks.any { it.time.hour in 22..23 && it.energyLevel < 0.4 }

        if (hasMorningPeak) consistency += 0.3
        if (hasEveningDip) consistency += 0.3

        // Check sleep consistency
        if (sleepSchedule != null) {
            val reasonableBedTime = sleepSchedule.averageBedTime.hour in 21..23
            val reasonableWakeTime = sleepSchedule.averageWakeTime.hour in 6..9
            val goodSleepDuration = sleepSchedule.averageSleepHours in 7.0..9.0

            if (reasonableBedTime) consistency += 0.15
            if (reasonableWakeTime) consistency += 0.15
            if (goodSleepDuration) consistency += 0.1
        }

        return consistency.coerceIn(0.0, 1.0)
    }

    private fun getEnvironmentalFactors(): EnvironmentalFactors {
        val now = LocalDateTime.now()
        val timeOfDay = when (now.hour) {
            in 5..8 -> TimeOfDay.EARLY_MORNING
            in 9..11 -> TimeOfDay.MORNING
            in 12..14 -> TimeOfDay.MIDDAY
            in 15..17 -> TimeOfDay.AFTERNOON
            in 18..20 -> TimeOfDay.EVENING
            else -> TimeOfDay.NIGHT
        }

        val dayOfWeek = if (now.dayOfWeek.value <= 5) DayOfWeek.WEEKDAY else DayOfWeek.WEEKEND

        // For now, simulate environmental data
        // In a real app, this would come from weather APIs and location services
        val weather = WeatherData(
            condition = "sunny",
            temperature = 22.0,
            humidity = 45.0,
            isOutdoorFriendly = true
        )

        val location = LocationData(
            type = LocationType.HOME,
            isFamiliar = true,
            hasResources = true
        )

        return EnvironmentalFactors(
            weather = weather,
            location = location,
            timeOfDay = timeOfDay,
            dayOfWeek = dayOfWeek,
            socialContext = SocialContext.ALONE
        )
    }

    private fun generateHabitSchedules(
        habits: List<Habit>,
        circadianData: CircadianRhythmData,
        environmentalData: EnvironmentalFactors
    ): List<HabitScheduleRecommendation> {
        return habits.filter { it.isActive }.map { habit ->
            val optimalTime = findOptimalTimeForHabit(habit, circadianData, environmentalData)
            val reasoning = generateSchedulingReasoning(habit, optimalTime, circadianData, environmentalData)
            val alternatives = findAlternativeTimes(habit, circadianData, environmentalData)
            val environmentalFactors = getEnvironmentalFactorsForHabit(habit, environmentalData)
            val successProbability = calculateSuccessProbability(habit, optimalTime, circadianData, environmentalData)

            HabitScheduleRecommendation(
                habitId = habit.id,
                habitName = habit.title,
                category = habit.category,
                recommendedTime = optimalTime,
                confidence = circadianData.rhythmStrength,
                reasoning = reasoning,
                alternativeTimes = alternatives,
                environmentalFactors = environmentalFactors,
                successProbability = successProbability
            )
        }.sortedByDescending { it.successProbability }
    }

    private fun findOptimalTimeForHabit(
        habit: Habit,
        circadianData: CircadianRhythmData,
        environmentalData: EnvironmentalFactors
    ): LocalTime {
        // Find the best time based on habit category and user's energy patterns
        val energyPeaks = circadianData.energyPeaks

        return when (habit.category) {
            HabitCategory.HEALTH, HabitCategory.FITNESS -> {
                // High energy times, preferably morning
                energyPeaks.find { it.time.hour in 6..10 }?.time ?: LocalTime.of(8, 0)
            }
            HabitCategory.MENTAL_HEALTH, HabitCategory.LEARNING -> {
                // Moderate energy, focus-friendly times
                energyPeaks.find { it.time.hour in 9..14 && it.energyLevel > 0.6 }?.time ?: LocalTime.of(10, 0)
            }
            HabitCategory.NUTRITION -> {
                // Meal times
                when (habit.title.lowercase()) {
                    "breakfast" -> LocalTime.of(7, 30)
                    "lunch" -> LocalTime.of(12, 30)
                    "dinner" -> LocalTime.of(18, 30)
                    else -> LocalTime.of(12, 0)
                }
            }
            HabitCategory.SLEEP -> {
                // Evening wind-down
                LocalTime.of(21, 0)
            }
            else -> {
                // Default to user's peak energy time
                energyPeaks.firstOrNull()?.time ?: LocalTime.of(9, 0)
            }
        }
    }

    private fun generateSchedulingReasoning(
        habit: Habit,
        optimalTime: LocalTime,
        circadianData: CircadianRhythmData,
        environmentalData: EnvironmentalFactors
    ): List<String> {
        val reasoning = mutableListOf<String>()

        // Energy-based reasoning
        if (circadianData.energyPeaks.any { it.time == optimalTime && it.energyLevel > 0.7 }) {
            reasoning.add("Scheduled during your peak energy time (${optimalTime.hour}:${optimalTime.minute.toString().padStart(2, '0')})")
        }

        // Category-based reasoning
        when (habit.category) {
            HabitCategory.FITNESS -> reasoning.add("Morning workouts align with natural cortisol peaks")
            HabitCategory.MENTAL_HEALTH -> reasoning.add("Mental health activities benefit from focused morning energy")
            HabitCategory.LEARNING -> reasoning.add("Learning is most effective during peak cognitive hours")
            HabitCategory.NUTRITION -> reasoning.add("Aligned with natural meal timing for better digestion")
            HabitCategory.SLEEP -> reasoning.add("Evening timing supports natural wind-down process")
            else -> reasoning.add("Optimized for your natural energy patterns")
        }

        // Environmental reasoning
        if (environmentalData.weather?.isOutdoorFriendly == true && habit.title.contains("walk", ignoreCase = true)) {
            reasoning.add("Great weather makes outdoor activities more enjoyable")
        }

        if (circadianData.rhythmStrength > 0.7) {
            reasoning.add("High confidence based on your consistent energy patterns")
        }

        return reasoning
    }

    private fun findAlternativeTimes(
        habit: Habit,
        circadianData: CircadianRhythmData,
        environmentalData: EnvironmentalFactors
    ): List<LocalTime> {
        // Provide 2-3 alternative times as backup options
        val energyPeaks = circadianData.energyPeaks.filter { it.energyLevel > 0.5 }
        return energyPeaks.take(3).map { it.time }
    }

    private fun getEnvironmentalFactorsForHabit(
        habit: Habit,
        environmentalData: EnvironmentalFactors
    ): List<String> {
        val factors = mutableListOf<String>()

        environmentalData.weather?.let { weather ->
            if (habit.category == HabitCategory.FITNESS) {
                factors.add("Weather: ${weather.condition}, ${weather.temperature.roundToInt()}°C")
            }
        }

        environmentalData.location?.let { location ->
            factors.add("Location: ${location.type.name.lowercase().replaceFirstChar { it.uppercase() }}")
        }

        factors.add("Time: ${environmentalData.timeOfDay.name.lowercase().replace("_", " ")}")
        factors.add("Day: ${environmentalData.dayOfWeek.name.lowercase()}")

        return factors
    }

    private fun calculateSuccessProbability(
        habit: Habit,
        time: LocalTime,
        circadianData: CircadianRhythmData,
        environmentalData: EnvironmentalFactors
    ): Double {
        var probability = 0.5 // Base probability

        // Energy factor
        val energyAtTime = circadianData.energyPeaks.find { it.time.hour == time.hour }?.energyLevel ?: 0.5
        probability += (energyAtTime - 0.5) * 0.3

        // Rhythm consistency factor
        probability += (circadianData.rhythmStrength - 0.5) * 0.2

        // Category suitability factor
        val categorySuitability = when {
            habit.category == HabitCategory.FITNESS && time.hour in 6..10 -> 0.1
            habit.category == HabitCategory.MENTAL_HEALTH && time.hour in 8..12 -> 0.1
            habit.category == HabitCategory.SLEEP && time.hour >= 20 -> 0.1
            else -> 0.0
        }
        probability += categorySuitability

        // Environmental factor
        if (environmentalData.weather?.isOutdoorFriendly == true) {
            probability += 0.05
        }

        return probability.coerceIn(0.1, 0.95)
    }

    private fun generateTimeSlots(
        circadianData: CircadianRhythmData,
        recommendations: List<HabitScheduleRecommendation>
    ): List<TimeSlot> {
        val slots = mutableListOf<TimeSlot>()

        // Generate hourly slots from 6 AM to 10 PM
        for (hour in 6..22) {
            val time = LocalTime.of(hour, 0)
            val energyLevel = circadianData.energyPeaks.find { it.time.hour == hour }?.energyLevel ?: 0.5
            val bookedHabits = recommendations.filter { it.recommendedTime.hour == hour }.map { it.habitName }
            val isOptimal = energyLevel > 0.7 && bookedHabits.isEmpty()

            slots.add(TimeSlot(
                time = time,
                energyLevel = energyLevel,
                isOptimal = isOptimal,
                bookedHabits = bookedHabits,
                environmentalScore = energyLevel // Simplified
            ))
        }

        return slots
    }

    fun updateSelectedDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
        // Could reload data for specific date if needed
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
