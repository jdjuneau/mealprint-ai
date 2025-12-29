package com.coachie.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coachie.app.data.HabitRepository
import com.coachie.app.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlin.math.roundToInt

data class HabitProgressUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val overallStats: OverallStats = OverallStats(),
    val habitProgressData: List<HabitProgressData> = emptyList(),
    val streakData: List<StreakDataPoint> = emptyList(),
    val categoryPerformance: List<CategoryPerformance> = emptyList(),
    val weeklyCompletionRate: List<WeeklyCompletionData> = emptyList(),
    val longestStreaks: List<HabitStreakInfo> = emptyList(),
    val recentAchievements: List<HabitAchievement> = emptyList(),
    val selectedTimeRange: TimeRange = TimeRange.WEEK
)

data class OverallStats(
    val totalHabits: Int = 0,
    val activeHabits: Int = 0,
    val completedToday: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val averageCompletionRate: Double = 0.0,
    val totalCompletions: Int = 0,
    val perfectDays: Int = 0
)

data class HabitProgressData(
    val habitId: String,
    val habitName: String,
    val category: HabitCategory,
    val currentStreak: Int,
    val longestStreak: Int,
    val completionRate: Double,
    val totalCompletions: Int,
    val weeklyCompletions: Int,
    val isActive: Boolean,
    val lastCompleted: LocalDate?,
    val trend: Trend
)

data class StreakDataPoint(
    val date: LocalDate,
    val streakCount: Int,
    val habitsCompleted: Int
)

data class CategoryPerformance(
    val category: HabitCategory,
    val totalHabits: Int,
    val completedHabits: Int,
    val completionRate: Double,
    val averageStreak: Double
)

data class WeeklyCompletionData(
    val week: String,
    val completionRate: Double,
    val totalHabits: Int,
    val completedHabits: Int
)

data class HabitStreakInfo(
    val habitName: String,
    val currentStreak: Int,
    val longestStreak: Int,
    val category: HabitCategory
)

data class HabitAchievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val unlockedAt: LocalDate,
    val rarity: HabitAchievementRarity
)

enum class HabitAchievementRarity {
    COMMON, RARE, EPIC, LEGENDARY
}

enum class Trend {
    UP, DOWN, STABLE
}

enum class TimeRange {
    WEEK, MONTH, QUARTER, YEAR
}

class HabitProgressViewModel(
    private val habitRepository: HabitRepository = HabitRepository.getInstance(),
    private val userId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitProgressUiState())
    val uiState: StateFlow<HabitProgressUiState> = _uiState.asStateFlow()

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private fun Date.toLocalDate(): LocalDate {
        return this.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    }

    private fun Date.toLocalDateTime(): LocalDateTime {
        return this.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
    }

    /**
     * Filter completions by the selected time range
     */
    private fun filterCompletionsByTimeRange(completions: List<HabitCompletion>, timeRange: TimeRange): List<HabitCompletion> {
        val days = when (timeRange) {
            TimeRange.WEEK -> 7
            TimeRange.MONTH -> 30
            TimeRange.QUARTER -> 90
            TimeRange.YEAR -> 365
        }
        val cutoffDate = LocalDate.now().minusDays(days.toLong())
        
        val filtered = completions.filter { completion ->
            completion.completedAt.toLocalDate() >= cutoffDate
        }
        Log.d("HabitProgressViewModel", "Filtered ${completions.size} completions to ${filtered.size} for time range $timeRange (cutoff: $cutoffDate)")
        return filtered
    }

    init {
        loadProgressData()
    }

    fun loadProgressData(timeRange: TimeRange? = null) {
        viewModelScope.launch {
            val currentTimeRange = timeRange ?: _uiState.value.selectedTimeRange
            Log.d("HabitProgressViewModel", "Loading progress data for time range: $currentTimeRange")
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val habits = habitRepository.getUserHabits(userId).first()
                val allCompletions = habitRepository.getUserCompletions(userId).first()
                Log.d("HabitProgressViewModel", "Loaded ${allCompletions.size} total completions")
                
                // Filter completions by selected time range - do this once at the start
                val completions = filterCompletionsByTimeRange(allCompletions, currentTimeRange)
                Log.d("HabitProgressViewModel", "Filtered to ${completions.size} completions for time range $currentTimeRange")

                val progressData = calculateHabitProgressData(habits, completions, currentTimeRange)
                val overallStats = calculateOverallStats(habits, completions)
                val streakData = calculateStreakData(habits, completions, currentTimeRange)
                val categoryPerformance = calculateCategoryPerformance(habits, completions)
                val weeklyData = calculateWeeklyCompletionData(completions)
                val longestStreaks = calculateLongestStreaks(habits, completions)
                val achievements = calculateAchievements(completions, overallStats)

                Log.d("HabitProgressViewModel", "Updating UI state with time range: $currentTimeRange")
                Log.d("HabitProgressViewModel", "  - Overall stats: totalCompletions=${overallStats.totalCompletions}, currentStreak=${overallStats.currentStreak}, longestStreak=${overallStats.longestStreak}")
                Log.d("HabitProgressViewModel", "  - Streak data points: ${streakData.size}")
                Log.d("HabitProgressViewModel", "  - Habit progress items: ${progressData.size}")
                Log.d("HabitProgressViewModel", "  - Category performance: ${categoryPerformance.size}")

                // Create a completely new state instance to ensure Compose detects the change
                val newState = HabitProgressUiState(
                    isLoading = false,
                    error = null,
                    overallStats = overallStats,
                    habitProgressData = progressData,
                    streakData = streakData,
                    categoryPerformance = categoryPerformance,
                    weeklyCompletionRate = weeklyData,
                    longestStreaks = longestStreaks,
                    recentAchievements = achievements.take(5),
                    selectedTimeRange = currentTimeRange
                )
                
                _uiState.value = newState
                
                Log.d("HabitProgressViewModel", "UI state updated. New selectedTimeRange: ${_uiState.value.selectedTimeRange}, totalCompletions: ${_uiState.value.overallStats.totalCompletions}")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load progress data: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    private fun calculateHabitProgressData(
        habits: List<Habit>,
        completions: List<HabitCompletion>,
        timeRange: TimeRange
    ): List<HabitProgressData> {
        val today = LocalDate.now()
        val weekAgo = today.minusDays(7)

        return habits.map { habit ->
            val habitCompletions = completions.filter { it.habitId == habit.id }
            val weeklyCompletions = habitCompletions.filter {
                it.completedAt.toLocalDate() >= weekAgo
            }

            val currentStreak = calculateCurrentStreak(habit.id, completions)
            val longestStreak = calculateLongestStreak(habit.id, completions)
            val totalCompletions = habitCompletions.size
            val completionRate = calculateCompletionRate(habit.id, completions, timeRange)
            val lastCompleted = habitCompletions.maxByOrNull {
                it.completedAt.toLocalDate()
            }?.let { it.completedAt.toLocalDate() }

            val trend = calculateTrend(habit.id, completions)

            HabitProgressData(
                habitId = habit.id,
                habitName = habit.title,
                category = habit.category,
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                completionRate = completionRate,
                totalCompletions = totalCompletions,
                weeklyCompletions = weeklyCompletions.size,
                isActive = habit.isActive,
                lastCompleted = lastCompleted,
                trend = trend
            )
        }.sortedByDescending { it.currentStreak }
    }

    private fun calculateOverallStats(habits: List<Habit>, completions: List<HabitCompletion>): OverallStats {
        val today = LocalDate.now()
        val activeHabits = habits.filter { it.isActive }
        
        // Completions are already filtered by time range in loadProgressData()
        val todayCompletions = completions.filter {
            it.completedAt.toLocalDate() == today
        }

        val totalHabits = habits.size
        val activeCount = activeHabits.size
        val completedToday = todayCompletions.size
        val currentStreak = calculateOverallStreak(completions)
        val longestStreak = calculateOverallLongestStreak(completions)
        val averageCompletionRate = calculateAverageCompletionRate(habits, completions)
        val totalCompletions = completions.size
        val perfectDays = calculatePerfectDays(habits, completions)

        return OverallStats(
            totalHabits = totalHabits,
            activeHabits = activeCount,
            completedToday = completedToday,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            averageCompletionRate = averageCompletionRate,
            totalCompletions = totalCompletions,
            perfectDays = perfectDays
        )
    }

    private fun calculateStreakData(habits: List<Habit>, completions: List<HabitCompletion>, timeRange: TimeRange): List<StreakDataPoint> {
        val today = LocalDate.now()
        val days = when (timeRange) {
            TimeRange.WEEK -> 7
            TimeRange.MONTH -> 30
            TimeRange.QUARTER -> 90
            TimeRange.YEAR -> 365
        }

        return (0..days).map { dayOffset ->
            val date = today.minusDays(dayOffset.toLong())
            val dayCompletions = completions.filter {
                it.completedAt.toLocalDate() == date
            }
            val uniqueHabitsCompleted = dayCompletions.distinctBy { it.habitId }.size

            StreakDataPoint(
                date = date,
                streakCount = calculateStreakOnDate(date, completions),
                habitsCompleted = uniqueHabitsCompleted
            )
        }.sortedBy { it.date }
    }

    private fun calculateCategoryPerformance(
        habits: List<Habit>,
        completions: List<HabitCompletion>
    ): List<CategoryPerformance> {
        // Completions are already filtered by time range in loadProgressData()
        return HabitCategory.values().map { category ->
            val categoryHabits = habits.filter { it.category == category && it.isActive }
            val categoryCompletions = completions.filter { completion ->
                categoryHabits.any { it.id == completion.habitId }
            }

            val completedHabits = categoryHabits.filter { habit ->
                categoryCompletions.any { it.habitId == habit.id }
            }.size

            val completionRate = if (categoryHabits.isNotEmpty()) {
                completedHabits.toDouble() / categoryHabits.size
            } else 0.0

            val averageStreak = categoryHabits.map { habit ->
                calculateCurrentStreak(habit.id, completions)
            }.average()

            CategoryPerformance(
                category = category,
                totalHabits = categoryHabits.size,
                completedHabits = completedHabits,
                completionRate = completionRate,
                averageStreak = averageStreak
            )
        }.filter { it.totalHabits > 0 }
         .sortedByDescending { it.completionRate }
    }

    private fun calculateWeeklyCompletionData(completions: List<HabitCompletion>): List<WeeklyCompletionData> {
        val today = LocalDate.now()
        val weeks = 12 // Last 12 weeks

        // Get the most recent Monday (or today if it's Monday)
        // If today is Monday, we want this week; otherwise, go back to the most recent Monday
        val currentWeekStart = if (today.dayOfWeek == java.time.DayOfWeek.MONDAY) {
            today
        } else {
            today.with(java.time.DayOfWeek.MONDAY)
        }
        
        // Create a list with both the date and the data, sort by date (newest first), then extract just the data
        return (0..weeks).map { weekOffset ->
            // Calculate week start by going back weekOffset weeks from the current week's Monday
            val weekStart = currentWeekStart.minusWeeks(weekOffset.toLong())
            val weekEnd = weekStart.plusDays(6)

            val weekCompletions = completions.filter { completion ->
                val completionDate = completion.completedAt.toLocalDate()
                completionDate in weekStart..weekEnd
            }

            val uniqueHabitCompletions = weekCompletions.groupBy { it.habitId }.size
            val totalActiveHabits = 7 // Assuming 7 habits per week for calculation

            val completionRate = if (totalActiveHabits > 0) {
                (uniqueHabitCompletions.toDouble() / totalActiveHabits).coerceAtMost(1.0)
            } else 0.0

            // Store both date and formatted string for sorting
            Pair(weekStart, WeeklyCompletionData(
                week = weekStart.format(DateTimeFormatter.ofPattern("MMM dd")),
                completionRate = completionRate,
                totalHabits = totalActiveHabits,
                completedHabits = uniqueHabitCompletions
            ))
        }
        .sortedBy { it.first } // Sort by actual date (oldest first) so takeLast(8) in UI shows most recent
        .map { it.second } // Extract just the WeeklyCompletionData
    }

    private fun calculateLongestStreaks(
        habits: List<Habit>,
        completions: List<HabitCompletion>
    ): List<HabitStreakInfo> {
        return habits.map { habit ->
            val currentStreak = calculateCurrentStreak(habit.id, completions)
            val longestStreak = calculateLongestStreak(habit.id, completions)

            HabitStreakInfo(
                habitName = habit.title,
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                category = habit.category
            )
        }.sortedByDescending { it.longestStreak }
         .take(5) // Top 5 longest streaks
    }

    private fun calculateAchievements(
        completions: List<HabitCompletion>,
        stats: OverallStats
    ): List<HabitAchievement> {
        val achievements = mutableListOf<HabitAchievement>()
        val today = LocalDate.now()

        // Streak-based achievements
        if (stats.currentStreak >= 7) {
            achievements.add(HabitAchievement(
                id = "week_streak",
                title = "Week Warrior",
                description = "Maintained a 7-day streak",
                icon = "🔥",
                unlockedAt = today,
                rarity = HabitAchievementRarity.COMMON
            ))
        }

        if (stats.currentStreak >= 30) {
            achievements.add(HabitAchievement(
                id = "month_streak",
                title = "Month Master",
                description = "Maintained a 30-day streak",
                icon = "👑",
                unlockedAt = today,
                rarity = HabitAchievementRarity.RARE
            ))
        }

        if (stats.longestStreak >= 100) {
            achievements.add(HabitAchievement(
                id = "century_club",
                title = "Century Club",
                description = "Achieved a 100-day streak",
                icon = "💯",
                unlockedAt = today,
                rarity = HabitAchievementRarity.LEGENDARY
            ))
        }

        // Completion-based achievements
        if (stats.totalCompletions >= 100) {
            achievements.add(HabitAchievement(
                id = "hundred_habits",
                title = "Century Mark",
                description = "Completed 100 habits",
                icon = "🎯",
                unlockedAt = today,
                rarity = HabitAchievementRarity.RARE
            ))
        }

        if (stats.perfectDays >= 10) {
            achievements.add(HabitAchievement(
                id = "perfect_week",
                title = "Perfectionist",
                description = "Completed all habits for 10 days",
                icon = "✨",
                unlockedAt = today,
                rarity = HabitAchievementRarity.EPIC
            ))
        }

        return achievements.sortedByDescending { it.unlockedAt }
    }

    // Helper functions
    private fun calculateCurrentStreak(habitId: String, completions: List<HabitCompletion>): Int {
        val today = LocalDate.now()
        var streak = 0
        var checkDate = today

        while (true) {
            val hasCompletion = completions.any { completion ->
                completion.habitId == habitId &&
                completion.completedAt.toLocalDate() == checkDate
            }

            if (hasCompletion) {
                streak++
                checkDate = checkDate.minusDays(1)
            } else {
                break
            }
        }

        return streak
    }

    private fun calculateLongestStreak(habitId: String, completions: List<HabitCompletion>): Int {
        val habitCompletions = completions
            .filter { it.habitId == habitId }
            .map { it.completedAt.toLocalDate() }
            .sorted()
            .distinct()

        if (habitCompletions.isEmpty()) return 0

        var longestStreak = 1
        var currentStreak = 1

        for (i in 1..habitCompletions.lastIndex) {
            if (habitCompletions[i].minusDays(1) == habitCompletions[i - 1]) {
                currentStreak++
                longestStreak = maxOf(longestStreak, currentStreak)
            } else {
                currentStreak = 1
            }
        }

        return longestStreak
    }

    private fun calculateCompletionRate(
        habitId: String,
        completions: List<HabitCompletion>,
        timeRange: TimeRange
    ): Double {
        val days = when (timeRange) {
            TimeRange.WEEK -> 7
            TimeRange.MONTH -> 30
            TimeRange.QUARTER -> 90
            TimeRange.YEAR -> 365
        }

        val completionsInRange = completions.filter { completion ->
            val completionDate = completion.completedAt.toLocalDate()
            ChronoUnit.DAYS.between(completionDate, LocalDate.now()) <= days
        }.filter { it.habitId == habitId }

        return if (days > 0) (completionsInRange.size.toDouble() / days).coerceAtMost(1.0) else 0.0
    }

    private fun calculateTrend(habitId: String, completions: List<HabitCompletion>): Trend {
        val recentWeeks = calculateCompletionRate(habitId, completions, TimeRange.WEEK)
        val previousWeeks = calculateCompletionRate(habitId, completions, TimeRange.MONTH) * 4 // Approximate

        return when {
            recentWeeks > previousWeeks * 1.1 -> Trend.UP
            recentWeeks < previousWeeks * 0.9 -> Trend.DOWN
            else -> Trend.STABLE
        }
    }

    private fun calculateOverallStreak(completions: List<HabitCompletion>): Int {
        val today = LocalDate.now()
        var streak = 0
        var checkDate = today

        // First, check if there are completions today
        val todayCompletions = completions.filter {
            it.completedAt.toLocalDate() == today
        }.distinctBy { it.habitId }

        // If no completions today, start checking from yesterday
        if (todayCompletions.isEmpty()) {
            checkDate = today.minusDays(1)
        }

        // Count consecutive days with completions going backwards
        while (true) {
            val dayCompletions = completions.filter {
                it.completedAt.toLocalDate() == checkDate
            }.distinctBy { it.habitId }

            if (dayCompletions.isNotEmpty()) {
                streak++
                checkDate = checkDate.minusDays(1)
            } else {
                break
            }
        }

        return streak
    }

    private fun calculateOverallLongestStreak(completions: List<HabitCompletion>): Int {
        val dailyCompletions = completions
            .groupBy { it.completedAt.toLocalDate() }
            .mapValues { (_, dayCompletions) -> dayCompletions.distinctBy { it.habitId }.size }
            .filter { it.value > 0 }
            .keys
            .sorted()

        if (dailyCompletions.isEmpty()) return 0

        var longestStreak = 1
        var currentStreak = 1

        for (i in 1..dailyCompletions.lastIndex) {
            if (dailyCompletions[i].minusDays(1) == dailyCompletions[i - 1]) {
                currentStreak++
                longestStreak = maxOf(longestStreak, currentStreak)
            } else {
                currentStreak = 1
            }
        }

        return longestStreak
    }

    private fun calculateAverageCompletionRate(
        habits: List<Habit>,
        completions: List<HabitCompletion>
    ): Double {
        val activeHabits = habits.filter { it.isActive }
        if (activeHabits.isEmpty()) return 0.0

        val rates = activeHabits.map { habit ->
            calculateCompletionRate(habit.id, completions, TimeRange.WEEK)
        }

        return (rates.average() * 100).roundToInt() / 100.0
    }

    private fun calculatePerfectDays(
        habits: List<Habit>,
        completions: List<HabitCompletion>
    ): Int {
        val activeHabits = habits.filter { it.isActive }
        val dailyStats = completions
            .groupBy { it.completedAt.toLocalDate() }
            .mapValues { (_, dayCompletions) -> dayCompletions.distinctBy { it.habitId }.size }

        return dailyStats.count { (_, completedCount) ->
            completedCount >= activeHabits.size
        }
    }

    private fun calculateStreakOnDate(date: LocalDate, completions: List<HabitCompletion>): Int {
        var streak = 0
        var checkDate = date

        while (true) {
            val dayCompletions = completions.filter {
                it.completedAt.toLocalDate() == checkDate
            }.distinctBy { it.habitId }

            if (dayCompletions.isNotEmpty()) {
                streak++
                checkDate = checkDate.minusDays(1)
            } else {
                break
            }
        }

        return streak
    }

    fun changeTimeRange(timeRange: TimeRange) {
        val currentRange = _uiState.value.selectedTimeRange
        if (currentRange != timeRange) {
            Log.d("HabitProgressViewModel", "Changing time range from $currentRange to $timeRange")
            // Immediately update the time range in state to trigger UI update
            _uiState.value = _uiState.value.copy(
                selectedTimeRange = timeRange,
                isLoading = true
            )
            // Then load the data for the new time range
            loadProgressData(timeRange)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
