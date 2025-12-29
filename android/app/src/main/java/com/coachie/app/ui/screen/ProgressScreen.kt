package com.coachie.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import com.coachie.app.ui.components.CoachieCard as Card
import com.coachie.app.ui.components.CoachieCardDefaults as CardDefaults
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.local.PreferencesManager
import com.coachie.app.ui.components.WeightChart
import com.coachie.app.ui.components.StreakDisplay
import com.coachie.app.ui.components.BadgesCollection
import com.coachie.app.viewmodel.WeightChartViewModel
import com.coachie.app.navigation.Screen

data class ProgressData(
    val date: String,
    val workoutName: String,
    val duration: String,
    val calories: Int
)

@Composable
fun ProgressScreen(
    userId: String? = null,
    onNavigateToDailyLog: () -> Unit = {},
    onNavigateToScanManagement: () -> Unit = {},
    onNavigateToLogEntry: () -> Unit = {},
    onNavigateToBodyScan: () -> Unit = {},
    onNavigateToHealthConnect: () -> Unit = {}
) {
    val progressData = remember {
        listOf(
            ProgressData("Today", "Full Body Strength", "45 min", 320),
            ProgressData("Yesterday", "HIIT Cardio", "30 min", 280),
            ProgressData("2 days ago", "Upper Body", "40 min", 250),
            ProgressData("3 days ago", "Core & Abs", "25 min", 180),
            ProgressData("4 days ago", "Leg Day", "50 min", 350),
            ProgressData("5 days ago", "Rest Day", "0 min", 0),
            ProgressData("6 days ago", "Push/Pull", "55 min", 420)
        )
    }

    val weightChartViewModel: WeightChartViewModel = viewModel(
        factory = WeightChartViewModel.Factory(
            firebaseRepository = FirebaseRepository.getInstance(),
            preferencesManager = PreferencesManager(androidx.compose.ui.platform.LocalContext.current),
            userId = userId ?: com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: "anonymous_user"
        )
    )

    val weightData by weightChartViewModel.weightData.collectAsState()
    val goalWeight by weightChartViewModel.goalWeight.collectAsState()
    val isWeightLoading by weightChartViewModel.isLoading.collectAsState()

    // Streak and badge data
    var userStreak by remember { mutableStateOf<com.coachie.app.data.model.Streak?>(null) }
    var userBadges by remember { mutableStateOf<List<com.coachie.app.data.model.Badge>>(emptyList()) }
    var achievementProgress by remember { mutableStateOf<List<com.coachie.app.data.model.AchievementProgress>>(emptyList()) }
    var isStreakLoading by remember { mutableStateOf(true) }

    // Load streak and badge data
    LaunchedEffect(userId) {
        userId?.let { uid ->
            try {
                val streakResult = FirebaseRepository.getInstance().getUserStreak(uid)
                val badgesResult = FirebaseRepository.getInstance().getUserBadges(uid)
                val progressResult = FirebaseRepository.getInstance().getAchievementProgress(uid)

                userStreak = streakResult.getOrNull()
                userBadges = badgesResult.getOrDefault(emptyList())
                achievementProgress = progressResult.getOrDefault(emptyList())
            } catch (e: Exception) {
                println("Error loading streak data: ${e.message}")
            } finally {
                isStreakLoading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        Text(
            text = "📊 Your Progress",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            textAlign = TextAlign.Center
        )

        // Weight Trend Chart
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Weight Trend (30 Days)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (isWeightLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = { weightChartViewModel.refreshData() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh weight data",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                WeightChart(
                    weightData = weightData,
                    goalWeight = goalWeight,
                    modifier = Modifier.fillMaxWidth()
                )

                // Weight stats
                weightChartViewModel.getWeightStats()?.let { stats ->
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            label = "Current",
                            value = stats.currentWeight?.let { String.format("%.1f", it) + "kg" } ?: "N/A"
                        )

                        StatItem(
                            label = "Change",
                            value = stats.changeText,
                            valueColor = stats.changeColor
                        )

                        StatItem(
                            label = "Data Points",
                            value = "${stats.dataPoints}"
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Streak Display
        userStreak?.let { streak ->
            StreakDisplay(
                streak = streak,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Badges and Achievements
        if (!isStreakLoading) {
            BadgesCollection(
                earnedBadges = userBadges,
                inProgressAchievements = achievementProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Stats cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "This Week",
                value = "4 workouts",
                subtitle = "3.5 avg/day",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Calories",
                value = "1800",
                subtitle = "burned",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick logging section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Quick Entry",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Log weight, water, and mood in seconds",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onNavigateToLogEntry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Quick Log")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Detailed logging section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Detailed Tracking",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Full daily log with steps, notes, and more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onNavigateToDailyLog,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Full Daily Log")
                }
            }
        }

                // Body scans section
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Body Scans",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Track your body composition changes over time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onNavigateToBodyScan,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Take Scan")
                            }
                            OutlinedButton(
                                onClick = onNavigateToScanManagement,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("View Scans")
                            }
                        }
                    }
                }

                // Health Connect section
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Google Fit Integration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Automatically sync your daily steps from Google Fit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onNavigateToHealthConnect,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Connect Google Fit")
                        }
                    }
                }

        Spacer(modifier = Modifier.height(16.dp))

        // Recent workouts
        Text(
            text = "Recent Activity",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(progressData) { data ->
                ProgressItem(data)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProgressItem(data: ProgressData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = data.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = data.workoutName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 16.dp)
            ) {
                Text(
                    text = data.duration,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                if (data.calories > 0) {
                    Text(
                        text = "${data.calories} cal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
