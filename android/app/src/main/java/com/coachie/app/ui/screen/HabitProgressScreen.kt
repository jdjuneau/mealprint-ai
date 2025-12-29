package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.viewmodel.HabitProgressViewModel
import com.coachie.app.viewmodel.HabitAchievement
import com.coachie.app.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitProgressScreen(
    onBack: () -> Unit,
    viewModel: HabitProgressViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val gradientBackground = rememberCoachieGradient(endY = 2000f)
    
    // Log state changes to debug
    LaunchedEffect(uiState.selectedTimeRange, uiState.overallStats.totalCompletions, uiState.streakData.size) {
        android.util.Log.d("HabitProgressScreen", "UI State changed:")
        android.util.Log.d("HabitProgressScreen", "  - Time range: ${uiState.selectedTimeRange}")
        android.util.Log.d("HabitProgressScreen", "  - Total completions: ${uiState.overallStats.totalCompletions}")
        android.util.Log.d("HabitProgressScreen", "  - Current streak: ${uiState.overallStats.currentStreak}")
        android.util.Log.d("HabitProgressScreen", "  - Streak data points: ${uiState.streakData.size}")
        android.util.Log.d("HabitProgressScreen", "  - Is loading: ${uiState.isLoading}")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Habit Progress", color = Color.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.Black,
                    navigationIconContentColor = Color.Black
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBackground)
                .padding(paddingValues)
        ) {
            // Force LazyColumn recreation when time range changes
            key(uiState.selectedTimeRange) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    // Overall Stats
                    item(key = "overall_stats") {
                        OverallStatsSection(uiState.overallStats)
                    }

                    // Streak Chart
                    item(key = "streak_chart") {
                        StreakChartSection(uiState.streakData)
                    }

                    // Category Performance
                    item(key = "category_performance") {
                        CategoryPerformanceSection(uiState.categoryPerformance)
                    }

                    // Individual Habit Progress
                    item(key = "habit_progress") {
                        HabitProgressSection(uiState.habitProgressData)
                    }

                    // Weekly Completion Rate
                    item(key = "weekly_completion") {
                        WeeklyCompletionSection(uiState.weeklyCompletionRate)
                    }

                    // Longest Streaks
                    item(key = "longest_streaks") {
                        LongestStreaksSection(uiState.longestStreaks)
                    }

                    // Recent Achievements
                    item(key = "achievements") {
                        AchievementsSection(uiState.recentAchievements)
                    }

                // Error message
                uiState.error?.let { error ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }

            // Loading overlay
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun OverallStatsSection(stats: OverallStats) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "📊 Overall Performance",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "Active Habits",
                value = stats.activeHabits.toString(),
                icon = "🎯",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Completed Today",
                value = stats.completedToday.toString(),
                icon = "✅",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "Current Streak",
                value = "${stats.currentStreak} days",
                icon = "🔥",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Best Streak",
                value = "${stats.longestStreak} days",
                icon = "👑",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "Completion Rate",
                value = "${(stats.averageCompletionRate * 100).toInt()}%",
                icon = "📈",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Perfect Days",
                value = stats.perfectDays.toString(),
                icon = "✨",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: String,
    modifier: Modifier = Modifier
) {
    CoachieCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TimeRangeSelector(
    selectedRange: TimeRange,
    onRangeSelected: (TimeRange) -> Unit
) {
    CoachieCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "📅 Time Range",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimeRange.values().forEach { range ->
                    FilterChip(
                        selected = selectedRange == range,
                        onClick = { onRangeSelected(range) },
                        label = {
                            Text(
                                range.name.lowercase().replaceFirstChar { it.uppercase() },
                                color = Color.Black
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            selectedLabelColor = Color.Black
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StreakChartSection(streakData: List<StreakDataPoint>) {
    CoachieCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🔥 Streak Progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (streakData.isEmpty()) {
                Text(
                    text = "No streak data available yet. Start completing habits to see your progress!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Simple bar chart representation
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    streakData.takeLast(7).forEach { dataPoint ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dataPoint.date.dayOfWeek.name.take(3),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.width(40.dp)
                            )
                            LinearProgressIndicator(
                                progress = (dataPoint.habitsCompleted.toFloat() / 7f).coerceAtMost(1f),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                            )
                            Text(
                                text = "${dataPoint.habitsCompleted}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.width(30.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPerformanceSection(categoryPerformance: List<CategoryPerformance>) {
    CoachieCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "📊 Category Performance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (categoryPerformance.isEmpty()) {
                Text(
                    text = "No category data available yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                categoryPerformance.forEach { category ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = category.category.name.replace("_", " "),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${category.completedHabits}/${category.totalHabits} habits",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${(category.completionRate * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Avg streak: ${category.averageStreak.toInt()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = category.completionRate.toFloat(),
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )

                    if (category != categoryPerformance.last()) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitProgressSection(habitProgressData: List<HabitProgressData>) {
    CoachieCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🎯 Individual Habit Progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (habitProgressData.isEmpty()) {
                Text(
                    text = "No habits to show progress for yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                habitProgressData.forEach { habit ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = habit.habitName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when (habit.trend) {
                                        Trend.UP -> "📈"
                                        Trend.DOWN -> "📉"
                                        Trend.STABLE -> "➡️"
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Text(
                                text = "Streak: ${habit.currentStreak} days • Rate: ${(habit.completionRate * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "🔥 ${habit.currentStreak}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (habit != habitProgressData.last()) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyCompletionSection(weeklyData: List<WeeklyCompletionData>) {
    CoachieCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "📅 Weekly Completion Trends",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (weeklyData.isEmpty()) {
                Text(
                    text = "No weekly data available yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                weeklyData.takeLast(8).forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = week.week,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(60.dp)
                        )
                        LinearProgressIndicator(
                            progress = week.completionRate.toFloat(),
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                        )
                        Text(
                            text = "${(week.completionRate * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(50.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LongestStreaksSection(longestStreaks: List<HabitStreakInfo>) {
    CoachieCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🏆 Longest Streaks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (longestStreaks.isEmpty()) {
                Text(
                    text = "No streaks to show yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                longestStreaks.forEach { streak ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = streak.habitName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Current: ${streak.currentStreak} days",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "🏆 ${streak.longestStreak}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    if (streak != longestStreaks.last()) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementsSection(achievements: List<HabitAchievement>) {
    CoachieCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🎖️ Recent Achievements",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (achievements.isEmpty()) {
                Text(
                    text = "No achievements unlocked yet. Keep completing habits to earn badges!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                achievements.forEach { achievement ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = achievement.icon,
                            style = MaterialTheme.typography.headlineSmall
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = achievement.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = achievement.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            color = when (achievement.rarity) {
                                HabitAchievementRarity.COMMON -> MaterialTheme.colorScheme.secondaryContainer
                                HabitAchievementRarity.RARE -> MaterialTheme.colorScheme.primaryContainer
                                HabitAchievementRarity.EPIC -> Color(0xFF9C27B0).copy(alpha = 0.1f) // Purple
                                HabitAchievementRarity.LEGENDARY -> Color(0xFFFF9800).copy(alpha = 0.1f) // Orange
                            },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = achievement.rarity.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when (achievement.rarity) {
                                    HabitAchievementRarity.COMMON -> MaterialTheme.colorScheme.onSurface
                                    HabitAchievementRarity.RARE -> MaterialTheme.colorScheme.onSurface
                                    HabitAchievementRarity.EPIC -> MaterialTheme.colorScheme.onSurface
                                    HabitAchievementRarity.LEGENDARY -> MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (achievement != achievements.last()) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}
