package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.coachie.app.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    achievementsViewModel: AchievementsViewModel = viewModel()
) {
    val uiState by achievementsViewModel.uiState.collectAsState()
    val gradientBackground = rememberCoachieGradient(endY = 2000f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Achievements") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                // Header with stats
                item {
                    AchievementStatsHeader(uiState.achievementStats)
                }

                // Category filter
                item {
                    CategoryFilterSection(achievementsViewModel)
                }

                // Recent achievements
                if (uiState.recentAchievements.isNotEmpty()) {
                    item {
                        RecentAchievementsSection(uiState.recentAchievements)
                    }
                }

                // Achievement list
                item {
                    AchievementListSection(achievementsViewModel)
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            // Achievement celebration dialog
            if (uiState.showCelebration && uiState.newAchievement != null) {
                AchievementCelebrationDialog(
                    achievement = uiState.newAchievement!!,
                    onDismiss = { achievementsViewModel.dismissCelebration() }
                )
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
private fun AchievementStatsHeader(stats: AchievementStats) {
    CoachieCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🏆 Achievement Progress",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Progress overview
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatItem(
                    label = "Unlocked",
                    value = "${stats.unlockedAchievements}/${stats.totalAchievements}",
                    icon = "🎯",
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "Completion",
                    value = "${(stats.completionRate * 100).toInt()}%",
                    icon = "📊",
                    modifier = Modifier.weight(1f)
                )
            }

            // Rarity breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RarityStatItem(
                    label = "Rare",
                    count = stats.rareAchievements,
                    color = Color(0xFF2196F3),
                    modifier = Modifier.weight(1f)
                )
                RarityStatItem(
                    label = "Epic",
                    count = stats.epicAchievements,
                    color = Color(0xFF9C27B0),
                    modifier = Modifier.weight(1f)
                )
                RarityStatItem(
                    label = "Legendary",
                    count = stats.legendaryAchievements,
                    color = Color(0xFFFF9800),
                    modifier = Modifier.weight(1f)
                )
            }

            // Streak info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatItem(
                    label = "Current Streak",
                    value = "${stats.currentStreak} days",
                    icon = "🔥",
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "Best Streak",
                    value = "${stats.longestStreak} days",
                    icon = "👑",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    icon: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RarityStatItem(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            color = color,
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CategoryFilterSection(viewModelParam: AchievementsViewModel) {
    val uiState by viewModelParam.uiState.collectAsState()

    CoachieCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Filter by Category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(AchievementCategory.values().toList()) { category ->
                    val isSelected = uiState.selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModelParam.setCategoryFilter(category) },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(viewModelParam.getCategoryIcon(category))
                                Text(viewModelParam.getCategoryDisplayName(category))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentAchievementsSection(recentAchievements: List<Achievement>) {
    CoachieCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🎉 Recent Unlocks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

        }
    }
}

@Composable
private fun AchievementListSection(v: AchievementsViewModel) {
    val uiState by v.uiState.collectAsState()
    val filteredAchievements = v.getFilteredAchievements()

    CoachieCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "All Achievements",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (filteredAchievements.isEmpty()) {
                Text(
                    text = "No achievements in this category yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Found ${filteredAchievements.size} achievements")
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AchievementCelebrationDialog(
    achievement: Achievement,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🎉",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Achievement Unlocked!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = achievement.icon,
                    style = MaterialTheme.typography.displayLarge
                )

                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )

                Surface(
                    color = androidx.compose.ui.graphics.Color(0xFFFFD700),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = achievement.rarity.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = androidx.compose.ui.graphics.Color.Black,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Awesome!")
            }
        }
    )
}