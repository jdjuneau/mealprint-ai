package com.coachie.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.HabitRepository
import com.coachie.app.data.model.*
import com.google.firebase.auth.FirebaseAuth
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

data class AchievementsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val unlockedAchievements: List<Achievement> = emptyList(),
    val lockedAchievements: List<Achievement> = emptyList(),
    val recentAchievements: List<Achievement> = emptyList(),
    val achievementStats: AchievementStats = AchievementStats(),
    val selectedCategory: AchievementCategory = AchievementCategory.ALL,
    val showCelebration: Boolean = false,
    val newAchievement: Achievement? = null
)

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val category: AchievementCategory,
    val rarity: AchievementRarity,
    val requirement: AchievementRequirement,
    val progress: AchievementProgress,
    val unlockedAt: LocalDate? = null,
    val isUnlocked: Boolean = false,
    val unlockCriteria: String = ""
)

data class AchievementRequirement(
    val type: AchievementType,
    val targetValue: Int,
    val timeFrame: TimeFrame? = null
)

data class AchievementProgress(
    val currentValue: Int = 0,
    val targetValue: Int,
    val percentage: Double = 0.0
)

data class AchievementStats(
    val totalAchievements: Int = 0,
    val unlockedAchievements: Int = 0,
    val completionRate: Double = 0.0,
    val rareAchievements: Int = 0,
    val epicAchievements: Int = 0,
    val legendaryAchievements: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0
)

enum class AchievementCategory {
    ALL, STREAKS, COMPLETION, CONSISTENCY, SPECIAL
}

enum class AchievementType {
    TOTAL_COMPLETIONS,
    CURRENT_STREAK,
    LONGEST_STREAK,
    PERFECT_DAYS,
    CATEGORY_MASTERY,
    TIME_BASED,
    SOCIAL,
    SPECIAL
}

enum class AchievementRarity {
    COMMON, RARE, EPIC, LEGENDARY
}

enum class TimeFrame {
    DAILY, WEEKLY, MONTHLY, YEARLY, ALL_TIME
}

class AchievementsViewModel(
    private val habitRepository: HabitRepository = HabitRepository.getInstance(),
    private val firebaseRepository: FirebaseRepository = FirebaseRepository.getInstance()
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val userId: String?
        get() = auth.currentUser?.uid

    private val _uiState = MutableStateFlow(AchievementsUiState())
    val uiState: StateFlow<AchievementsUiState> = _uiState.asStateFlow()

    private val allAchievements = createAllAchievements()

    private fun Date.toLocalDate(): LocalDate {
        return this.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    }

    private fun Date.toLocalDateTime(): LocalDateTime {
        return this.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
    }

    init {
        loadAchievements()
    }

    private fun createAllAchievements(): List<Achievement> {
        return listOf(
            // Streak Achievements
            Achievement(
                id = "first_steps",
                title = "First Steps",
                description = "Complete your first habit",
                icon = "👶",
                category = AchievementCategory.STREAKS,
                rarity = AchievementRarity.COMMON,
                requirement = AchievementRequirement(AchievementType.TOTAL_COMPLETIONS, 1),
                progress = AchievementProgress(targetValue = 1),
                unlockCriteria = "Complete 1 habit"
            ),
            Achievement(
                id = "getting_started",
                title = "Getting Started",
                description = "Complete 10 habits total",
                icon = "🚀",
                category = AchievementCategory.COMPLETION,
                rarity = AchievementRarity.COMMON,
                requirement = AchievementRequirement(AchievementType.TOTAL_COMPLETIONS, 10),
                progress = AchievementProgress(targetValue = 10),
                unlockCriteria = "Complete 10 habits"
            ),
            Achievement(
                id = "habit_builder",
                title = "Habit Builder",
                description = "Complete 50 habits total",
                icon = "🔨",
                category = AchievementCategory.COMPLETION,
                rarity = AchievementRarity.COMMON,
                requirement = AchievementRequirement(AchievementType.TOTAL_COMPLETIONS, 50),
                progress = AchievementProgress(targetValue = 50),
                unlockCriteria = "Complete 50 habits"
            ),
            Achievement(
                id = "century_club",
                title = "Century Club",
                description = "Complete 100 habits total",
                icon = "💯",
                category = AchievementCategory.COMPLETION,
                rarity = AchievementRarity.RARE,
                requirement = AchievementRequirement(AchievementType.TOTAL_COMPLETIONS, 100),
                progress = AchievementProgress(targetValue = 100),
                unlockCriteria = "Complete 100 habits"
            ),
            Achievement(
                id = "week_warrior",
                title = "Week Warrior",
                description = "Maintain a 7-day streak",
                icon = "🔥",
                category = AchievementCategory.STREAKS,
                rarity = AchievementRarity.COMMON,
                requirement = AchievementRequirement(AchievementType.CURRENT_STREAK, 7),
                progress = AchievementProgress(targetValue = 7),
                unlockCriteria = "Maintain a 7-day streak"
            ),
            Achievement(
                id = "month_master",
                title = "Month Master",
                description = "Maintain a 30-day streak",
                icon = "👑",
                category = AchievementCategory.STREAKS,
                rarity = AchievementRarity.RARE,
                requirement = AchievementRequirement(AchievementType.CURRENT_STREAK, 30),
                progress = AchievementProgress(targetValue = 30),
                unlockCriteria = "Maintain a 30-day streak"
            ),
            Achievement(
                id = "streak_legend",
                title = "Streak Legend",
                description = "Maintain a 100-day streak",
                icon = "⭐",
                category = AchievementCategory.STREAKS,
                rarity = AchievementRarity.LEGENDARY,
                requirement = AchievementRequirement(AchievementType.CURRENT_STREAK, 100),
                progress = AchievementProgress(targetValue = 100),
                unlockCriteria = "Maintain a 100-day streak"
            ),
            Achievement(
                id = "perfect_week",
                title = "Perfect Week",
                description = "Complete all habits for 7 consecutive days",
                icon = "✨",
                category = AchievementCategory.CONSISTENCY,
                rarity = AchievementRarity.EPIC,
                requirement = AchievementRequirement(AchievementType.PERFECT_DAYS, 7),
                progress = AchievementProgress(targetValue = 7),
                unlockCriteria = "Complete all habits for 7 days"
            ),
            Achievement(
                id = "consistency_king",
                title = "Consistency King",
                description = "Complete all habits for 30 consecutive days",
                icon = "🏆",
                category = AchievementCategory.CONSISTENCY,
                rarity = AchievementRarity.LEGENDARY,
                requirement = AchievementRequirement(AchievementType.PERFECT_DAYS, 30),
                progress = AchievementProgress(targetValue = 30),
                unlockCriteria = "Complete all habits for 30 days"
            ),
            Achievement(
                id = "early_bird",
                title = "Early Bird",
                description = "Complete morning habits before 8 AM for a week",
                icon = "🐦",
                category = AchievementCategory.SPECIAL,
                rarity = AchievementRarity.RARE,
                requirement = AchievementRequirement(AchievementType.TIME_BASED, 7),
                progress = AchievementProgress(targetValue = 7),
                unlockCriteria = "Complete morning habits before 8 AM"
            ),
            Achievement(
                id = "fitness_enthusiast",
                title = "Fitness Enthusiast",
                description = "Complete 50 fitness-related habits",
                icon = "💪",
                category = AchievementCategory.SPECIAL,
                rarity = AchievementRarity.RARE,
                requirement = AchievementRequirement(AchievementType.CATEGORY_MASTERY, 50, TimeFrame.ALL_TIME),
                progress = AchievementProgress(targetValue = 50),
                unlockCriteria = "Complete 50 fitness habits"
            ),
            Achievement(
                id = "mindfulness_master",
                title = "Mindfulness Master",
                description = "Complete 50 mental health or learning habits",
                icon = "🧘",
                category = AchievementCategory.SPECIAL,
                rarity = AchievementRarity.RARE,
                requirement = AchievementRequirement(AchievementType.CATEGORY_MASTERY, 50, TimeFrame.ALL_TIME),
                progress = AchievementProgress(targetValue = 50),
                unlockCriteria = "Complete 50 mindfulness habits"
            ),
            Achievement(
                id = "health_hacker",
                title = "Health Hacker",
                description = "Complete 100 health-related habits",
                icon = "🏥",
                category = AchievementCategory.SPECIAL,
                rarity = AchievementRarity.EPIC,
                requirement = AchievementRequirement(AchievementType.CATEGORY_MASTERY, 100, TimeFrame.ALL_TIME),
                progress = AchievementProgress(targetValue = 100),
                unlockCriteria = "Complete 100 health habits"
            ),
            Achievement(
                id = "lifestyle_legend",
                title = "Lifestyle Legend",
                description = "Complete 500 habits across all categories",
                icon = "🌟",
                category = AchievementCategory.SPECIAL,
                rarity = AchievementRarity.LEGENDARY,
                requirement = AchievementRequirement(AchievementType.TOTAL_COMPLETIONS, 500),
                progress = AchievementProgress(targetValue = 500),
                unlockCriteria = "Complete 500 habits total"
            )
        )
    }

    fun loadAchievements() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val userId = this@AchievementsViewModel.userId
                if (userId == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "User not authenticated"
                    )
                    return@launch
                }

                val habits = habitRepository.getHabits(userId).first()
                val completions = habitRepository.getRecentCompletions(userId).first()

                // Calculate achievement progress
                val updatedAchievements = calculateAchievementProgress(allAchievements, habits, completions)

                // Separate unlocked and locked achievements
                val unlockedAchievements = updatedAchievements.filter { it.isUnlocked }
                val lockedAchievements = updatedAchievements.filter { !it.isUnlocked }

                // Calculate stats
                val stats = calculateAchievementStats(updatedAchievements)

                // Get recent achievements (last 30 days)
                val thirtyDaysAgo = LocalDate.now().minusDays(30)
                val recentAchievements = unlockedAchievements.filter {
                    it.unlockedAt?.isAfter(thirtyDaysAgo) == true
                }

                _uiState.value = _uiState.value.copy(
                    unlockedAchievements = unlockedAchievements,
                    lockedAchievements = lockedAchievements,
                    recentAchievements = recentAchievements,
                    achievementStats = stats,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load achievements: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    private fun calculateAchievementProgress(
        achievements: List<Achievement>,
        habits: List<Habit>,
        completions: List<HabitCompletion>
    ): List<Achievement> {
        return achievements.map { achievement ->
            val progress = calculateProgressForAchievement(achievement, habits, completions)
            val isUnlocked = progress.percentage >= 1.0
            val unlockedAt = if (isUnlocked && achievement.unlockedAt == null) {
                LocalDate.now()
            } else {
                achievement.unlockedAt
            }

            achievement.copy(
                progress = progress,
                isUnlocked = isUnlocked,
                unlockedAt = unlockedAt
            )
        }
    }

    private fun calculateProgressForAchievement(
        achievement: Achievement,
        habits: List<Habit>,
        completions: List<HabitCompletion>
    ): AchievementProgress {
        val targetValue = achievement.requirement.targetValue
        var currentValue = 0

        when (achievement.requirement.type) {
            AchievementType.TOTAL_COMPLETIONS -> {
                currentValue = completions.size
            }
            AchievementType.CURRENT_STREAK -> {
                currentValue = calculateCurrentOverallStreak(completions)
            }
            AchievementType.LONGEST_STREAK -> {
                currentValue = calculateLongestOverallStreak(completions)
            }
            AchievementType.PERFECT_DAYS -> {
                currentValue = calculatePerfectDays(habits, completions)
            }
            AchievementType.CATEGORY_MASTERY -> {
                // For category-specific achievements, we need to determine which category
                val categoryCompletions = when (achievement.id) {
                    "fitness_enthusiast" -> completions.filter { completion ->
                        habits.find { it.id == completion.habitId }?.category == HabitCategory.FITNESS
                    }
                    "mindfulness_master" -> completions.filter { completion ->
                        val habit = habits.find { it.id == completion.habitId }
                        habit?.category == HabitCategory.MENTAL_HEALTH || habit?.category == HabitCategory.LEARNING
                    }
                    "health_hacker" -> completions.filter { completion ->
                        val habit = habits.find { it.id == completion.habitId }
                        habit?.category == HabitCategory.HEALTH
                    }
                    else -> completions
                }
                currentValue = categoryCompletions.size
            }
            AchievementType.TIME_BASED -> {
                // For time-based achievements like early bird
                val earlyMorningCompletions = completions.filter { completion ->
                    val completionTime = completion.completedAt.toLocalDateTime().hour
                    completionTime < 8 // Before 8 AM
                }
                currentValue = earlyMorningCompletions.size
            }
            AchievementType.SOCIAL -> {
                // Future social achievements
                currentValue = 0
            }
            AchievementType.SPECIAL -> {
                // Special achievements
                currentValue = 0
            }
        }

        val percentage = (currentValue.toDouble() / targetValue).coerceAtMost(1.0)

        return AchievementProgress(
            currentValue = currentValue,
            targetValue = targetValue,
            percentage = percentage
        )
    }

    private fun calculateAchievementStats(achievements: List<Achievement>): AchievementStats {
        val totalAchievements = achievements.size
        val unlockedAchievements = achievements.count { it.isUnlocked }
        val completionRate = if (totalAchievements > 0) {
            unlockedAchievements.toDouble() / totalAchievements
        } else 0.0

        val rareAchievements = achievements.count { it.isUnlocked && it.rarity == AchievementRarity.RARE }
        val epicAchievements = achievements.count { it.isUnlocked && it.rarity == AchievementRarity.EPIC }
        val legendaryAchievements = achievements.count { it.isUnlocked && it.rarity == AchievementRarity.LEGENDARY }

        // Calculate streaks (simplified - in real app, this would be more sophisticated)
        val currentStreak = 0 // TODO: calculateCurrentOverallStreak(completions)
        val longestStreak = 0 // TODO: calculateLongestOverallStreak(completions)

        return AchievementStats(
            totalAchievements = totalAchievements,
            unlockedAchievements = unlockedAchievements,
            completionRate = completionRate,
            rareAchievements = rareAchievements,
            epicAchievements = epicAchievements,
            legendaryAchievements = legendaryAchievements,
            currentStreak = currentStreak,
            longestStreak = longestStreak
        )
    }

    private fun calculateCurrentOverallStreak(completions: List<HabitCompletion>): Int {
        val today = LocalDate.now()
        var streak = 0
        var checkDate = today

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

    private fun calculateLongestOverallStreak(completions: List<HabitCompletion>): Int {
        val dailyStats = completions
            .groupBy { it.completedAt.toLocalDate() }
            .mapValues { (_, dayCompletions) -> dayCompletions.distinctBy { it.habitId }.size }
            .filter { it.value > 0 }
            .keys
            .sorted()

        if (dailyStats.isEmpty()) return 0

        var longestStreak = 1
        var currentStreak = 1

        for (i in 1..dailyStats.lastIndex) {
            if (dailyStats[i].minusDays(1) == dailyStats[i - 1]) {
                currentStreak++
                longestStreak = maxOf(longestStreak, currentStreak)
            } else {
                currentStreak = 1
            }
        }

        return longestStreak
    }

    private fun calculatePerfectDays(habits: List<Habit>, completions: List<HabitCompletion>): Int {
        val activeHabits = habits.filter { it.isActive }
        val dailyStats = completions
            .groupBy { it.completedAt.toLocalDate() }
            .mapValues { (_, dayCompletions) -> dayCompletions.distinctBy { it.habitId }.size }

        return dailyStats.count { (_, completedCount) ->
            completedCount >= activeHabits.size
        }
    }

    fun setCategoryFilter(category: AchievementCategory) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }

    fun getFilteredAchievements(): List<Achievement> {
        val allAchievements = _uiState.value.unlockedAchievements + _uiState.value.lockedAchievements
        return when (_uiState.value.selectedCategory) {
            AchievementCategory.ALL -> allAchievements
            else -> allAchievements.filter { it.category == _uiState.value.selectedCategory }
        }
    }

    fun celebrateNewAchievement(achievement: Achievement) {
        _uiState.value = _uiState.value.copy(
            showCelebration = true,
            newAchievement = achievement
        )
    }

    fun dismissCelebration() {
        _uiState.value = _uiState.value.copy(
            showCelebration = false,
            newAchievement = null
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // Helper functions for UI
    fun getRarityColor(rarity: AchievementRarity): androidx.compose.ui.graphics.Color {
        return when (rarity) {
            AchievementRarity.COMMON -> androidx.compose.ui.graphics.Color(0xFF9E9E9E) // Gray
            AchievementRarity.RARE -> androidx.compose.ui.graphics.Color(0xFF2196F3) // Blue
            AchievementRarity.EPIC -> androidx.compose.ui.graphics.Color(0xFF9C27B0) // Purple
            AchievementRarity.LEGENDARY -> androidx.compose.ui.graphics.Color(0xFFFF9800) // Orange
        }
    }

    fun getRarityBackgroundColor(rarity: AchievementRarity): androidx.compose.ui.graphics.Color {
        return when (rarity) {
            AchievementRarity.COMMON -> androidx.compose.ui.graphics.Color(0xFFF5F5F5)
            AchievementRarity.RARE -> androidx.compose.ui.graphics.Color(0xFFE3F2FD)
            AchievementRarity.EPIC -> androidx.compose.ui.graphics.Color(0xFFF3E5F5)
            AchievementRarity.LEGENDARY -> androidx.compose.ui.graphics.Color(0xFFFFF3E0)
        }
    }

    fun getCategoryIcon(category: AchievementCategory): String {
        return when (category) {
            AchievementCategory.ALL -> "🏆"
            AchievementCategory.STREAKS -> "🔥"
            AchievementCategory.COMPLETION -> "✅"
            AchievementCategory.CONSISTENCY -> "📅"
            AchievementCategory.SPECIAL -> "⭐"
        }
    }

    fun getCategoryDisplayName(category: AchievementCategory): String {
        return when (category) {
            AchievementCategory.ALL -> "All Achievements"
            AchievementCategory.STREAKS -> "Streaks"
            AchievementCategory.COMPLETION -> "Completion"
            AchievementCategory.CONSISTENCY -> "Consistency"
            AchievementCategory.SPECIAL -> "Special"
        }
    }
}