package com.coachie.app.viewmodel

import android.util.Log
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlin.math.roundToInt

data class HabitIntelligenceUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val habitPatterns: List<HabitPattern> = emptyList(),
    val adaptiveSuggestions: List<AdaptiveSuggestion> = emptyList(),
    val performanceInsights: List<PerformanceInsight> = emptyList(),
    val difficultyAdjustments: List<DifficultyAdjustment> = emptyList(),
    val selectedTimeRange: TimeRange = TimeRange.WEEK,
    val intelligenceScore: IntelligenceScore = IntelligenceScore()
)

data class HabitPattern(
    val habitId: String,
    val habitName: String,
    val patternType: PatternType,
    val strength: Double, // 0-1, how strong the pattern is
    val description: String,
    val insight: String,
    val actionableAdvice: String,
    val dataPoints: List<PatternDataPoint> = emptyList()
)

data class PatternDataPoint(
    val date: LocalDate,
    val value: Double,
    val context: String = ""
)

enum class PatternType {
    CONSISTENCY, TIMING, WEEKDAY_WEEKEND, SEQUENTIAL, ENVIRONMENTAL, STRESS_CORRELATED
}

data class AdaptiveSuggestion(
    val habitId: String,
    val habitName: String,
    val suggestionType: SuggestionType,
    val title: String,
    val description: String,
    val expectedImprovement: Double, // percentage improvement
    val confidence: Double, // 0-1
    val implementation: String,
    val potentialRisks: List<String> = emptyList()
)

enum class SuggestionType {
    DIFFICULTY_ADJUSTMENT, TIMING_OPTIMIZATION, FREQUENCY_CHANGE, ENVIRONMENTAL_ADAPTATION,
    SOCIAL_ACCOUNTABILITY, REWARD_SYSTEM, HABIT_STACKING, BREAKDOWN_COMPLEX_HABITS
}

data class PerformanceInsight(
    val habitId: String,
    val habitName: String,
    val insightType: InsightType,
    val title: String,
    val description: String,
    val trend: Trend,
    val magnitude: Double, // strength of the insight
    val timeFrame: String,
    val recommendation: String
)

enum class InsightType {
    SUCCESS_RATE, STREAK_ANALYSIS, TIME_OPTIMALITY, ENVIRONMENT_IMPACT, CONSISTENCY_PATTERNS,
    PROGRESSION_SPEED, FAILURE_PATTERNS, MOTIVATION_CORRELATION
}

data class DifficultyAdjustment(
    val habitId: String,
    val habitName: String,
    val currentDifficulty: HabitDifficulty,
    val recommendedDifficulty: HabitDifficulty,
    val reason: String,
    val expectedOutcome: String,
    val confidence: Double,
    val implementationSteps: List<String>
)

data class IntelligenceScore(
    val overallScore: Double = 0.0, // 0-100
    val patternRecognition: Double = 0.0,
    val adaptiveLearning: Double = 0.0,
    val predictiveAccuracy: Double = 0.0,
    val insightQuality: Double = 0.0,
    val lastUpdated: LocalDate = LocalDate.now()
)

class HabitIntelligenceViewModel(
    private val habitRepository: HabitRepository = HabitRepository.getInstance(),
    private val firebaseRepository: FirebaseRepository = FirebaseRepository.getInstance(),
    private val userId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitIntelligenceUiState())
    val uiState: StateFlow<HabitIntelligenceUiState> = _uiState.asStateFlow()

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
        Log.d("HabitIntelligenceViewModel", "Filtered ${completions.size} completions to ${filtered.size} for time range $timeRange (cutoff: $cutoffDate)")
        return filtered
    }

    init {
        loadIntelligenceData()
    }

    fun loadIntelligenceData(timeRange: TimeRange? = null) {
        viewModelScope.launch {
            val currentTimeRange = timeRange ?: _uiState.value.selectedTimeRange
            Log.d("HabitIntelligenceViewModel", "Loading intelligence data for time range: $currentTimeRange")
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val habits = habitRepository.getUserHabits(userId).first()
                val allCompletions = habitRepository.getUserCompletions(userId).first()
                Log.d("HabitIntelligenceViewModel", "Loaded ${allCompletions.size} total completions")
                
                // Filter completions by selected time range
                val completions = filterCompletionsByTimeRange(allCompletions, currentTimeRange)
                Log.d("HabitIntelligenceViewModel", "Filtered to ${completions.size} completions for time range $currentTimeRange")

                // Analyze patterns
                val patterns = analyzeHabitPatterns(habits, completions)

                // Generate adaptive suggestions
                val suggestions = generateAdaptiveSuggestions(habits, completions, patterns)

                // Create performance insights
                val insights = generatePerformanceInsights(habits, completions)

                // Calculate difficulty adjustments
                val adjustments = calculateDifficultyAdjustments(habits, completions)

                // Calculate intelligence score
                val intelligenceScore = calculateIntelligenceScore(habits, completions, patterns, insights)

                Log.d("HabitIntelligenceViewModel", "Updating UI state with time range: $currentTimeRange")
                Log.d("HabitIntelligenceViewModel", "  - Patterns: ${patterns.size}")
                Log.d("HabitIntelligenceViewModel", "  - Suggestions: ${suggestions.size}")
                Log.d("HabitIntelligenceViewModel", "  - Insights: ${insights.size}")
                Log.d("HabitIntelligenceViewModel", "  - Intelligence score: ${intelligenceScore.overallScore}")

                // Create a completely new state instance to ensure Compose detects the change
                val newState = HabitIntelligenceUiState(
                    isLoading = false,
                    error = null,
                    habitPatterns = patterns,
                    adaptiveSuggestions = suggestions,
                    performanceInsights = insights,
                    difficultyAdjustments = adjustments,
                    intelligenceScore = intelligenceScore,
                    selectedTimeRange = currentTimeRange
                )
                
                _uiState.value = newState
                
                Log.d("HabitIntelligenceViewModel", "UI state updated. New selectedTimeRange: ${_uiState.value.selectedTimeRange}, patterns: ${_uiState.value.habitPatterns.size}")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to analyze habit intelligence: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    private fun analyzeHabitPatterns(
        habits: List<Habit>,
        completions: List<HabitCompletion>
    ): List<HabitPattern> {
        val patterns = mutableListOf<HabitPattern>()

        habits.filter { it.isActive }.forEach { habit ->
            val habitCompletions = completions.filter { it.habitId == habit.id }

            // Analyze consistency patterns
            val consistencyPattern = analyzeConsistencyPattern(habit, habitCompletions)
            if (consistencyPattern != null) patterns.add(consistencyPattern)

            // Analyze timing patterns
            val timingPattern = analyzeTimingPattern(habit, habitCompletions)
            if (timingPattern != null) patterns.add(timingPattern)

            // Analyze weekday/weekend patterns
            val weekdayPattern = analyzeWeekdayPattern(habit, habitCompletions)
            if (weekdayPattern != null) patterns.add(weekdayPattern)

            // Analyze sequential patterns (habits done together)
            val sequentialPattern = analyzeSequentialPattern(habit, habitCompletions, completions)
            if (sequentialPattern != null) patterns.add(sequentialPattern)
        }

        return patterns.sortedByDescending { it.strength }
    }

    private fun analyzeConsistencyPattern(
        habit: Habit,
        completions: List<HabitCompletion>
    ): HabitPattern? {
        if (completions.size < 7) return null // Need at least a week of data

        val recentDates = completions.map {
            it.completedAt.toLocalDate()
        }.sorted().distinct()

        // Calculate gaps between completions
        val gaps = mutableListOf<Int>()
        for (i in 1..recentDates.lastIndex) {
            val gap = java.time.temporal.ChronoUnit.DAYS.between(recentDates[i-1], recentDates[i]).toInt()
            gaps.add(gap)
        }

        val consistency = if (gaps.isEmpty()) 0.0 else {
            val avgGap = gaps.average()
            if (avgGap.isNaN()) return null
            val idealGap = when (habit.frequency) {
                HabitFrequency.DAILY -> 1.0
                HabitFrequency.WEEKLY -> 7.0
                else -> 1.0
            }
            1.0 / (1.0 + kotlin.math.abs(avgGap - idealGap))
        }

        val patternType = if (consistency > 0.8) PatternType.CONSISTENCY else return null

        val description = when {
            consistency > 0.9 -> "Exceptionally consistent habit performance"
            consistency > 0.8 -> "Very consistent habit performance"
            else -> "Moderately consistent habit performance"
        }

        val insight = "Your ${habit.title} habit shows ${"%.1f".format(consistency * 100)}% consistency over the analyzed period."

        val advice = when {
            consistency > 0.9 -> "Keep up the excellent consistency! Consider teaching others your success strategies."
            consistency > 0.8 -> "Great consistency! Focus on maintaining this rhythm."
            else -> "Work on building more consistent streaks. Try habit stacking or reminders."
        }

        return HabitPattern(
            habitId = habit.id,
            habitName = habit.title,
            patternType = patternType,
            strength = consistency,
            description = description,
            insight = insight,
            actionableAdvice = advice,
            dataPoints = recentDates.map { PatternDataPoint(it, 1.0, "Completed") }
        )
    }

    private fun analyzeTimingPattern(
        habit: Habit,
        completions: List<HabitCompletion>
    ): HabitPattern? {
        if (completions.size < 5) return null

        val completionTimes = completions.mapNotNull {
            try {
                it.completedAt.toLocalDateTime().toLocalTime()
            } catch (e: Exception) {
                null
            }
        }

        if (completionTimes.size < 3) return null

        // Calculate average completion time
        val hourValues = completionTimes.map { it.hour + it.minute / 60.0 }
        val avgHour = hourValues.average()
        if (avgHour.isNaN()) return null
        
        val avgTime = LocalTime.of(avgHour.toInt(), ((avgHour % 1) * 60).toInt())

        // Calculate consistency of timing
        val timeVariance = completionTimes.map {
            val hourDiff = kotlin.math.abs(it.hour - avgTime.hour)
            val minDiff = kotlin.math.abs(it.minute - avgTime.minute)
            hourDiff + minDiff / 60.0
        }.average()
        
        if (timeVariance.isNaN()) return null

        val timingConsistency = 1.0 / (1.0 + timeVariance)

        if (timingConsistency < 0.6) return null

        val description = "Consistent timing pattern at ${avgTime.format(DateTimeFormatter.ofPattern("HH:mm"))}"
        val insight = "You tend to complete ${habit.title} around ${avgTime.format(DateTimeFormatter.ofPattern("HH:mm"))} with ${"%.1f".format(timingConsistency * 100)}% timing consistency."

        val advice = when {
            timingConsistency > 0.8 -> "Excellent timing consistency! This suggests strong routine establishment."
            else -> "Good timing consistency. Consider setting a specific reminder time to improve further."
        }

        return HabitPattern(
            habitId = habit.id,
            habitName = habit.title,
            patternType = PatternType.TIMING,
            strength = timingConsistency,
            description = description,
            insight = insight,
            actionableAdvice = advice,
            dataPoints = completionTimes.map { PatternDataPoint(LocalDate.now(), it.hour + it.minute / 60.0, "Completion time") }
        )
    }

    private fun analyzeWeekdayPattern(
        habit: Habit,
        completions: List<HabitCompletion>
    ): HabitPattern? {
        if (completions.size < 10) return null

        val weekdayCompletions = completions.filter {
            val dayOfWeek = it.completedAt.toLocalDate().dayOfWeek.value
            dayOfWeek <= 5 // Monday-Friday
        }.size

        val weekendCompletions = completions.size - weekdayCompletions

        val totalCompletions = completions.size.toDouble()
        val weekdayRatio = weekdayCompletions / totalCompletions
        val weekendRatio = weekendCompletions / totalCompletions

        // Check if there's a significant difference (>20% difference)
        val difference = kotlin.math.abs(weekdayRatio - weekendRatio)
        if (difference < 0.2) return null

        val strongerDay = if (weekdayRatio > weekendRatio) "weekdays" else "weekends"
        val strongerRatio = if (weekdayRatio > weekendRatio) weekdayRatio else weekendRatio
        val weakerRatio = if (weekdayRatio < weekendRatio) weekdayRatio else weekendRatio

        val description = "Stronger performance on $strongerDay (${"%.1f".format(strongerRatio * 100)}% vs ${"%.1f".format(weakerRatio * 100)}%)"
        val insight = "${habit.title} shows ${"%.1f".format(difference * 100)}% difference between weekday and weekend performance."

        val advice = when {
            weekdayRatio > weekendRatio -> "Consider how to maintain weekday momentum on weekends, or adjust expectations for weekends."
            else -> "Great weekend consistency! Try to bring some weekend energy to weekdays."
        }

        return HabitPattern(
            habitId = habit.id,
            habitName = habit.title,
            patternType = PatternType.WEEKDAY_WEEKEND,
            strength = difference,
            description = description,
            insight = insight,
            actionableAdvice = advice,
            dataPoints = listOf(
                PatternDataPoint(LocalDate.now(), weekdayRatio, "Weekdays"),
                PatternDataPoint(LocalDate.now(), weekendRatio, "Weekends")
            )
        )
    }

    private fun analyzeSequentialPattern(
        habit: Habit,
        habitCompletions: List<HabitCompletion>,
        allCompletions: List<HabitCompletion>
    ): HabitPattern? {
        if (habitCompletions.size < 5) return null

        // Find habits that are often completed on the same day
        val habitDates = habitCompletions.map {
            it.completedAt.toLocalDate()
        }.toSet()

        val cooccurrenceCounts = mutableMapOf<String, Int>()
        allCompletions.groupBy { it.completedAt.toLocalDate() }.forEach { (date, dayCompletions) ->
            if (date in habitDates) {
                dayCompletions.forEach { completion ->
                    if (completion.habitId != habit.id) {
                        cooccurrenceCounts[completion.habitId] = cooccurrenceCounts.getOrDefault(completion.habitId, 0) + 1
                    }
                }
            }
        }

        val totalDays = habitDates.size
        val strongCorrelations = cooccurrenceCounts.filter { (habitId, count) ->
            count >= totalDays * 0.6 // Occurs on 60%+ of habit days
        }

        if (strongCorrelations.isEmpty()) return null

        val topCorrelation = strongCorrelations.maxByOrNull { it.value }
        if (topCorrelation == null) return null

        // Find the correlated habit name
        val correlatedHabitName = "another habit" // TODO: Implement habit lookup
        val correlationStrength = topCorrelation.value.toDouble() / totalDays

        val description = "Often completed with $correlatedHabitName (${"%.1f".format(correlationStrength * 100)}% overlap)"
        val insight = "${habit.title} is frequently done alongside $correlatedHabitName, suggesting a natural habit sequence."

        val advice = "Consider habit stacking: Use ${correlatedHabitName} as a cue to do ${habit.title}, or vice versa to build stronger routines."

        return HabitPattern(
            habitId = habit.id,
            habitName = habit.title,
            patternType = PatternType.SEQUENTIAL,
            strength = correlationStrength,
            description = description,
            insight = insight,
            actionableAdvice = advice,
            dataPoints = listOf(PatternDataPoint(LocalDate.now(), correlationStrength, "Co-occurrence with $correlatedHabitName"))
        )
    }

    private fun generateAdaptiveSuggestions(
        habits: List<Habit>,
        completions: List<HabitCompletion>,
        patterns: List<HabitPattern>
    ): List<AdaptiveSuggestion> {
        val suggestions = mutableListOf<AdaptiveSuggestion>()

        habits.filter { it.isActive }.forEach { habit ->
            val habitPatterns = patterns.filter { it.habitId == habit.id }
            val habitCompletions = completions.filter { it.habitId == habit.id }

            // Difficulty adjustment suggestions
            val difficultySuggestion = suggestDifficultyAdjustment(habit, habitCompletions)
            if (difficultySuggestion != null) suggestions.add(difficultySuggestion)

            // Timing optimization suggestions
            val timingSuggestion = suggestTimingOptimization(habit, habitCompletions, patterns)
            if (timingSuggestion != null) suggestions.add(timingSuggestion)

            // Frequency adjustment suggestions
            val frequencySuggestion = suggestFrequencyAdjustment(habit, habitCompletions)
            if (frequencySuggestion != null) suggestions.add(frequencySuggestion)

            // Habit stacking suggestions
            val stackingSuggestion = suggestHabitStacking(habit, patterns)
            if (stackingSuggestion != null) suggestions.add(stackingSuggestion)
        }

        return suggestions.sortedByDescending { it.expectedImprovement * it.confidence }
    }

    private fun suggestDifficultyAdjustment(
        habit: Habit,
        completions: List<HabitCompletion>
    ): AdaptiveSuggestion? {
        if (completions.size < 7) return null

        val recentCompletions = completions.sortedByDescending { it.completedAt }.take(7)
        val completionRate = recentCompletions.size / 7.0

        val suggestion = when {
            completionRate >= 0.8 && habit.targetValue > 1 -> {
                // Very successful, suggest increasing difficulty
                AdaptiveSuggestion(
                    habitId = habit.id,
                    habitName = habit.title,
                    suggestionType = SuggestionType.DIFFICULTY_ADJUSTMENT,
                    title = "Increase difficulty for continued growth",
                    description = "You're excelling at this habit. Consider increasing the target or adding complexity to maintain challenge.",
                    expectedImprovement = 0.15,
                    confidence = 0.85,
                    implementation = "Increase target from ${habit.targetValue} to ${habit.targetValue + 1}${habit.unit ?: ""}",
                    potentialRisks = listOf("May lead to burnout if increased too quickly")
                )
            }
            completionRate <= 0.3 -> {
                // Struggling, suggest decreasing difficulty
                val newTarget = maxOf(1, habit.targetValue - 1)
                if (newTarget != habit.targetValue) {
                    AdaptiveSuggestion(
                        habitId = habit.id,
                        habitName = habit.title,
                        suggestionType = SuggestionType.DIFFICULTY_ADJUSTMENT,
                        title = "Reduce difficulty to build momentum",
                        description = "This habit seems challenging. Starting smaller can help build confidence and consistency.",
                        expectedImprovement = 0.25,
                        confidence = 0.75,
                        implementation = "Reduce target from ${habit.targetValue} to $newTarget${habit.unit ?: ""}",
                        potentialRisks = listOf("May feel less challenging, but builds foundation for future increases")
                    )
                } else null
            }
            else -> null
        }

        return suggestion
    }

    private fun suggestTimingOptimization(
        habit: Habit,
        completions: List<HabitCompletion>,
        patterns: List<HabitPattern>
    ): AdaptiveSuggestion? {
        val timingPattern = patterns.find { it.habitId == habit.id && it.patternType == PatternType.TIMING }
        if (timingPattern == null || timingPattern.strength < 0.7) return null

        // Look for energy patterns from circadian data (simplified)
        val completionHours = completions.mapNotNull {
            try {
                it.completedAt.toLocalDateTime().toLocalTime().hour
            } catch (e: Exception) {
                null
            }
        }
        
        if (completionHours.isEmpty()) return null
        
        val avgHour = completionHours.average()
        if (avgHour.isNaN()) return null
        
        val currentCompletionHour = avgHour.roundToInt()

        val optimalHour = when (habit.category) {
            HabitCategory.FITNESS -> 8 // Morning for exercise
            HabitCategory.MENTAL_HEALTH -> 7 // Early morning for meditation
            HabitCategory.LEARNING -> 10 // Mid-morning for focus
            else -> 9 // Default morning
        }

        if (kotlin.math.abs(currentCompletionHour - optimalHour) <= 1) return null // Already optimal

        return AdaptiveSuggestion(
            habitId = habit.id,
            habitName = habit.title,
            suggestionType = SuggestionType.TIMING_OPTIMIZATION,
            title = "Optimize timing for better results",
            description = "Based on your energy patterns, ${optimalHour}:00 might be a better time for ${habit.title}.",
            expectedImprovement = 0.20,
            confidence = timingPattern.strength,
            implementation = "Try shifting to ${optimalHour}:00 AM. Track results for 1-2 weeks.",
            potentialRisks = listOf("May disrupt current routine", "Takes time to adjust")
        )
    }

    private fun suggestFrequencyAdjustment(
        habit: Habit,
        completions: List<HabitCompletion>
    ): AdaptiveSuggestion? {
        if (completions.size < 14) return null // Need at least 2 weeks

        val recent2Weeks = completions.sortedByDescending { it.completedAt }.take(14).size
        val previous2Weeks = completions.size - recent2Weeks

        val recentRate = recent2Weeks / 14.0
        val previousRate = if (previous2Weeks > 0) (completions.size - recent2Weeks) / maxOf(1, completions.size - 14).toDouble() else 0.0

        val trend = recentRate - previousRate

        return when {
            trend > 0.2 && habit.frequency == HabitFrequency.WEEKLY -> {
                // Improving significantly, suggest increasing frequency
                AdaptiveSuggestion(
                    habitId = habit.id,
                    habitName = habit.title,
                    suggestionType = SuggestionType.FREQUENCY_CHANGE,
                    title = "Consider daily frequency",
                    description = "Your recent performance suggests you might be ready for daily practice.",
                    expectedImprovement = 0.30,
                    confidence = 0.70,
                    implementation = "Change from weekly to daily frequency. Start with 3-4 days per week.",
                    potentialRisks = listOf("May feel overwhelming initially", "Risk of burnout")
                )
            }
            trend < -0.2 && habit.frequency == HabitFrequency.DAILY -> {
                // Declining, suggest reducing frequency
                AdaptiveSuggestion(
                    habitId = habit.id,
                    habitName = habit.title,
                    suggestionType = SuggestionType.FREQUENCY_CHANGE,
                    title = "Consider reducing frequency",
                    description = "Recent struggles suggest a less frequent schedule might help rebuild momentum.",
                    expectedImprovement = 0.20,
                    confidence = 0.65,
                    implementation = "Temporarily reduce to 4-5 days per week. Focus on quality over quantity.",
                    potentialRisks = listOf("May lose momentum if reduced too much")
                )
            }
            else -> null
        }
    }

    private fun suggestHabitStacking(
        habit: Habit,
        patterns: List<HabitPattern>
    ): AdaptiveSuggestion? {
        val sequentialPattern = patterns.find { it.habitId == habit.id && it.patternType == PatternType.SEQUENTIAL }
        if (sequentialPattern == null || sequentialPattern.strength < 0.6) return null

        return AdaptiveSuggestion(
            habitId = habit.id,
            habitName = habit.title,
            suggestionType = SuggestionType.HABIT_STACKING,
            title = "Leverage habit stacking",
            description = "Use existing routines as cues for this habit to make it automatic.",
            expectedImprovement = 0.35,
            confidence = sequentialPattern.strength,
            implementation = sequentialPattern.actionableAdvice,
            potentialRisks = listOf("May create dependency on the cue habit")
        )
    }

    private fun generatePerformanceInsights(
        habits: List<Habit>,
        completions: List<HabitCompletion>
    ): List<PerformanceInsight> {
        val insights = mutableListOf<PerformanceInsight>()

        habits.filter { it.isActive }.forEach { habit ->
            val habitCompletions = completions.filter { it.habitId == habit.id }

            // Success rate insights
            val successInsight = analyzeSuccessRate(habit, habitCompletions)
            if (successInsight != null) insights.add(successInsight)

            // Streak analysis
            val streakInsight = analyzeStreakPerformance(habit, habitCompletions)
            if (streakInsight != null) insights.add(streakInsight)

            // Time optimality
            val timingInsight = analyzeTimingOptimality(habit, habitCompletions)
            if (timingInsight != null) insights.add(timingInsight)
        }

        return insights.sortedByDescending { it.magnitude }
    }

    private fun analyzeSuccessRate(
        habit: Habit,
        completions: List<HabitCompletion>
    ): PerformanceInsight? {
        if (completions.size < 7) return null

        val totalDays = when (_uiState.value.selectedTimeRange) {
            TimeRange.WEEK -> 7
            TimeRange.MONTH -> 30
            TimeRange.QUARTER -> 90
            TimeRange.YEAR -> 365
        }

        val completionRate = completions.size.toDouble() / totalDays
        val trend = calculateTrend(completions)

        val (title, description, insightType) = when {
            completionRate >= 0.8 -> Triple(
                "Excellent Success Rate",
                "${habit.title} shows ${"%.1f".format(completionRate * 100)}% completion rate",
                InsightType.SUCCESS_RATE
            )
            completionRate >= 0.6 -> Triple(
                "Good Success Rate",
                "${habit.title} maintains ${"%.1f".format(completionRate * 100)}% completion rate",
                InsightType.SUCCESS_RATE
            )
            else -> Triple(
                "Needs Improvement",
                "${habit.title} has ${"%.1f".format(completionRate * 100)}% completion rate",
                InsightType.SUCCESS_RATE
            )
        }

        return PerformanceInsight(
            habitId = habit.id,
            habitName = habit.title,
            insightType = insightType,
            title = title,
            description = description,
            trend = trend,
            magnitude = completionRate,
            timeFrame = _uiState.value.selectedTimeRange.name.lowercase(),
            recommendation = when {
                completionRate >= 0.8 -> "Keep up the excellent work! Consider increasing difficulty."
                completionRate >= 0.6 -> "Good progress. Focus on maintaining consistency."
                else -> "Consider breaking the habit into smaller steps or adjusting timing."
            }
        )
    }

    private fun analyzeStreakPerformance(
        habit: Habit,
        completions: List<HabitCompletion>
    ): PerformanceInsight? {
        if (completions.size < 5) return null

        val currentStreak = calculateCurrentStreak(habit.id, completions)
        val longestStreak = calculateLongestStreak(habit.id, completions)

        val streakRatio = if (longestStreak > 0) currentStreak.toDouble() / longestStreak else 0.0

        return PerformanceInsight(
            habitId = habit.id,
            habitName = habit.title,
            insightType = InsightType.STREAK_ANALYSIS,
            title = "Streak Performance",
            description = "Current streak: $currentStreak days, Best: $longestStreak days",
            trend = if (streakRatio > 0.8) Trend.UP else if (streakRatio < 0.3) Trend.DOWN else Trend.STABLE,
            magnitude = streakRatio,
            timeFrame = "all time",
            recommendation = when {
                streakRatio > 0.8 -> "Outstanding streak maintenance!"
                streakRatio > 0.5 -> "Good streak performance. Keep building!"
                else -> "Focus on rebuilding momentum. Start small and consistent."
            }
        )
    }

    private fun analyzeTimingOptimality(
        habit: Habit,
        completions: List<HabitCompletion>
    ): PerformanceInsight? {
        if (completions.size < 5) return null

        val completionHours = completions.mapNotNull {
            try {
                it.completedAt.toLocalDateTime().toLocalTime().hour
            } catch (e: Exception) {
                null
            }
        }

        if (completionHours.size < 3) return null

        val avgHour = completionHours.average()
        if (avgHour.isNaN()) return null
        
        val optimalHour = when (habit.category) {
            HabitCategory.FITNESS -> 8.0
            HabitCategory.MENTAL_HEALTH -> 7.0
            HabitCategory.LEARNING -> 10.0
            else -> 9.0
        }

        val optimality = 1.0 / (1.0 + kotlin.math.abs(avgHour - optimalHour) / 24.0)

        return PerformanceInsight(
            habitId = habit.id,
            habitName = habit.title,
            insightType = InsightType.TIME_OPTIMALITY,
            title = "Timing Analysis",
            description = "Average completion time: ${avgHour.toInt()}:00",
            trend = Trend.STABLE, // Timing usually stable
            magnitude = optimality,
            timeFrame = "recent",
            recommendation = if (optimality > 0.8) {
                "Excellent timing alignment with optimal performance windows!"
            } else {
                "Consider adjusting to ${optimalHour.toInt()}:00 for potentially better results."
            }
        )
    }

    private fun calculateDifficultyAdjustments(
        habits: List<Habit>,
        completions: List<HabitCompletion>
    ): List<DifficultyAdjustment> {
        return habits.filter { it.isActive }.mapNotNull { habit ->
            val habitCompletions = completions.filter { it.habitId == habit.id }
            if (habitCompletions.size < 7) return@mapNotNull null

            val recentSuccess = habitCompletions.sortedByDescending { it.completedAt }
                .take(7).size / 7.0

            val recommendedDifficulty = when {
                recentSuccess >= 0.8 -> HabitDifficulty.HARD
                recentSuccess >= 0.6 -> HabitDifficulty.MEDIUM
                else -> HabitDifficulty.EASY
            }

            val currentDifficulty = when {
                habit.targetValue > 5 -> HabitDifficulty.HARD
                habit.targetValue > 2 -> HabitDifficulty.MEDIUM
                else -> HabitDifficulty.EASY
            }

            if (currentDifficulty == recommendedDifficulty) return@mapNotNull null

            val reason = when {
                recentSuccess >= 0.8 && currentDifficulty != HabitDifficulty.HARD ->
                    "You're excelling consistently. Ready for more challenge."
                recentSuccess < 0.4 && currentDifficulty != HabitDifficulty.EASY ->
                    "Recent struggles suggest starting smaller may help."
                else -> "Performance data suggests this difficulty level."
            }

            val expectedOutcome = when {
                recommendedDifficulty > currentDifficulty -> "Increased engagement and growth"
                recommendedDifficulty < currentDifficulty -> "Better consistency and momentum"
                else -> "Optimal balance of challenge and achievability"
            }

            DifficultyAdjustment(
                habitId = habit.id,
                habitName = habit.title,
                currentDifficulty = currentDifficulty,
                recommendedDifficulty = recommendedDifficulty,
                reason = reason,
                expectedOutcome = expectedOutcome,
                confidence = 0.75,
                implementationSteps = listOf(
                    "Monitor performance for 1-2 weeks after adjustment",
                    "Adjust target value gradually (max 20% change)",
                    "Track how the change affects consistency",
                    "Be prepared to adjust further if needed"
                )
            )
        }
    }

    private fun calculateIntelligenceScore(
        habits: List<Habit>,
        completions: List<HabitCompletion>,
        patterns: List<HabitPattern>,
        insights: List<PerformanceInsight>
    ): IntelligenceScore {
        var patternScore = 0.0
        var adaptiveScore = 0.0
        var predictiveScore = 0.0
        var insightScore = 0.0

        // Pattern recognition score (0-25 points)
        patternScore = if (patterns.isNotEmpty()) {
            val patternStrength = patterns.map { it.strength }.average()
            if (patternStrength.isNaN()) {
                patterns.size * 8.0
            } else {
                if (patterns.size >= 3) patternStrength * 25 else patterns.size * 8.0
            }
        } else {
            0.0
        }

        // Adaptive learning score (0-25 points)
        val adaptiveSuggestions = _uiState.value.adaptiveSuggestions
        adaptiveScore = minOf(adaptiveSuggestions.size * 3.0, 25.0)

        // Predictive accuracy score (0-25 points) - simplified
        predictiveScore = if (habits.isNotEmpty()) {
            val successfulHabits = habits.count { habit ->
                val habitCompletions = completions.filter { it.habitId == habit.id }
                habitCompletions.size >= 5 // Simplified success metric
            }
            (successfulHabits.toDouble() / habits.size) * 25
        } else {
            0.0
        }

        // Insight quality score (0-25 points)
        insightScore = minOf(insights.size * 2.5, 25.0)

        val totalScore = patternScore + adaptiveScore + predictiveScore + insightScore
        val overallScore = if (totalScore.isNaN()) 0.0 else totalScore.roundToInt().toDouble()

        return IntelligenceScore(
            overallScore = overallScore,
            patternRecognition = patternScore,
            adaptiveLearning = adaptiveScore,
            predictiveAccuracy = predictiveScore,
            insightQuality = insightScore,
            lastUpdated = LocalDate.now()
        )
    }

    // Helper functions
    private fun calculateCurrentStreak(habitId: String, completions: List<HabitCompletion>): Int {
        val habitCompletions = completions.filter { it.habitId == habitId }
            .map { it.completedAt.toLocalDate() }
            .sorted()
            .distinct()

        if (habitCompletions.isEmpty()) return 0

        var streak = 0
        var currentDate = LocalDate.now()

        while (habitCompletions.contains(currentDate)) {
            streak++
            currentDate = currentDate.minusDays(1)
        }

        return streak
    }

    private fun calculateLongestStreak(habitId: String, completions: List<HabitCompletion>): Int {
        val habitCompletions = completions.filter { it.habitId == habitId }
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

    private fun calculateTrend(completions: List<HabitCompletion>): Trend {
        if (completions.size < 7) return Trend.STABLE

        val recent = completions.sortedByDescending { it.completedAt }.take(7).size / 7.0
        val previous = completions.size - 7
        val previousRate = if (previous > 0) previous / maxOf(1, completions.size - 7).toDouble() else 0.0

        return when {
            recent > previousRate * 1.2 -> Trend.UP
            recent < previousRate * 0.8 -> Trend.DOWN
            else -> Trend.STABLE
        }
    }

    fun setTimeRange(timeRange: TimeRange) {
        val currentRange = _uiState.value.selectedTimeRange
        if (currentRange != timeRange) {
            Log.d("HabitIntelligenceViewModel", "Changing time range from $currentRange to $timeRange")
            // Immediately update the time range in state to trigger UI update
            _uiState.value = _uiState.value.copy(
                selectedTimeRange = timeRange,
                isLoading = true
            )
            // Then load the data for the new time range
            loadIntelligenceData(timeRange)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // UI helper functions
    fun getPatternTypeIcon(patternType: PatternType): String {
        return when (patternType) {
            PatternType.CONSISTENCY -> "📊"
            PatternType.TIMING -> "⏰"
            PatternType.WEEKDAY_WEEKEND -> "📅"
            PatternType.SEQUENTIAL -> "🔗"
            PatternType.ENVIRONMENTAL -> "🌤️"
            PatternType.STRESS_CORRELATED -> "😰"
        }
    }

    fun getPatternTypeDescription(patternType: PatternType): String {
        return when (patternType) {
            PatternType.CONSISTENCY -> "Consistency Pattern"
            PatternType.TIMING -> "Timing Pattern"
            PatternType.WEEKDAY_WEEKEND -> "Day-of-Week Pattern"
            PatternType.SEQUENTIAL -> "Habit Sequence Pattern"
            PatternType.ENVIRONMENTAL -> "Environmental Pattern"
            PatternType.STRESS_CORRELATED -> "Stress Correlation Pattern"
        }
    }

    fun getSuggestionTypeIcon(suggestionType: SuggestionType): String {
        return when (suggestionType) {
            SuggestionType.DIFFICULTY_ADJUSTMENT -> "⚖️"
            SuggestionType.TIMING_OPTIMIZATION -> "⏰"
            SuggestionType.FREQUENCY_CHANGE -> "🔄"
            SuggestionType.ENVIRONMENTAL_ADAPTATION -> "🌤️"
            SuggestionType.SOCIAL_ACCOUNTABILITY -> "👥"
            SuggestionType.REWARD_SYSTEM -> "🎁"
            SuggestionType.HABIT_STACKING -> "🔗"
            SuggestionType.BREAKDOWN_COMPLEX_HABITS -> "🧩"
        }
    }

    fun getInsightTypeIcon(insightType: InsightType): String {
        return when (insightType) {
            InsightType.SUCCESS_RATE -> "📈"
            InsightType.STREAK_ANALYSIS -> "🔥"
            InsightType.TIME_OPTIMALITY -> "⏰"
            InsightType.ENVIRONMENT_IMPACT -> "🌤️"
            InsightType.CONSISTENCY_PATTERNS -> "📊"
            InsightType.PROGRESSION_SPEED -> "🚀"
            InsightType.FAILURE_PATTERNS -> "⚠️"
            InsightType.MOTIVATION_CORRELATION -> "💪"
        }
    }

    fun getTrendIcon(trend: Trend): String {
        return when (trend) {
            Trend.UP -> "📈"
            Trend.DOWN -> "📉"
            Trend.STABLE -> "➡️"
        }
    }

    fun getTrendDescription(trend: Trend): String {
        return when (trend) {
            Trend.UP -> "Improving"
            Trend.DOWN -> "Declining"
            Trend.STABLE -> "Stable"
        }
    }
}
