package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Share
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.components.SharePlatformDialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.coachie.app.ui.theme.Primary40
import com.coachie.app.ui.theme.rememberCoachieGradient
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.HealthLog
import com.coachie.app.data.model.Streak
import com.coachie.app.viewmodel.HomeDashboardViewModel
import com.coachie.app.viewmodel.AuthState
import com.coachie.app.viewmodel.CoachieViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Data class and helper function definitions
data class StreakDay(
    val date: LocalDate,
    val logged: Boolean,
    val logCount: Int
)

suspend fun loadStreakHistory(
    userId: String
): List<StreakDay> {
    val repository = FirebaseRepository.getInstance()
    val today = LocalDate.now()
    val history = mutableListOf<StreakDay>()

    // Load last 7 days
    for (i in 0..6) {
        val date = today.minusDays(i.toLong())
        val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        try {
            // Check if there are any health logs for this day
            val healthLogsResult = repository.getHealthLogs(userId, dateStr)
            val healthLogs = healthLogsResult.getOrNull() ?: emptyList()
            
            // Count unique log types instead of all entries
            // This gives a more reasonable count (e.g., 1 meal, 1 workout, 1 water = 3 logs, not 100+)
            val uniqueLogTypes = healthLogs.map { it.type }.distinct().size
            val hasActivity = healthLogs.isNotEmpty()

            history.add(StreakDay(
                date = date,
                logged = hasActivity,
                logCount = if (hasActivity) uniqueLogTypes else 0
            ))
        } catch (e: Exception) {
            history.add(StreakDay(
                date = date,
                logged = false,
                logCount = 0
            ))
        }
    }

    return history.sortedByDescending { it.date }
}

@Composable
fun StreakHistoryItem(streakDay: StreakDay) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = streakDay.date.format(DateTimeFormatter.ofPattern("MMM dd")),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = streakDay.date.format(DateTimeFormatter.ofPattern("EEEE")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (streakDay.logged) {
                Icon(
                    Icons.Filled.LocalFireDepartment,
                    contentDescription = "Logged",
                    tint = Primary40,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "${streakDay.logCount} logs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "No logs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StreakRuleItem(
    title: String,
    description: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreakDetailScreen(
    onBack: () -> Unit,
    viewModel: CoachieViewModel = viewModel()
) {
    val authState by viewModel.authState.collectAsState()
    val currentUser = (authState as? AuthState.Authenticated)?.user
    val userId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: return

    // Create dashboard ViewModel with proper factory
    val streakContext = LocalContext.current
    val dashboardViewModel = viewModel<HomeDashboardViewModel>(
        key = userId,
        factory = HomeDashboardViewModel.Factory(
            repository = FirebaseRepository.getInstance(),
            userId = userId,
            context = streakContext
        )
    )

    // Get streak data from dashboard view model
    val streak by dashboardViewModel.streak.collectAsState()

    // Load recent streak history
    var streakHistory by remember { mutableStateOf<List<StreakDay>>(emptyList()) }
    
    // Share dialog state
    var showShareDialog by remember { mutableStateOf(false) }
    var pendingShareData by remember { mutableStateOf<com.coachie.app.service.ShareImageData?>(null) }
    val shareService = remember { com.coachie.app.service.ShareService.getInstance(streakContext) }

    // Load dashboard data when screen appears
    LaunchedEffect(userId) {
        dashboardViewModel.refresh()
        
        // Load recent streak history
        if (userId != "anonymous") {
            launch {
                val history = loadStreakHistory(userId)
                streakHistory = history
            }
        }
    }

    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBackground)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CoachieCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CoachieCardDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.95f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                "Back",
                                tint = Primary40
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Streak Details",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Primary40
                        )
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Current streak display
                CoachieCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CoachieCardDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.95f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.LocalFireDepartment,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Primary40
                        )

                        Text(
                            text = streak?.currentStreak?.toString() ?: "0",
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = Primary40
                        )

                        Text(
                            text = "Current Streak",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        // Share button for 7+ day streaks
                        streak?.let { streakData ->
                            if (streakData.currentStreak >= 7) {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                val shareService = remember { com.coachie.app.service.ShareService.getInstance(streakContext) }
                                IconButton(
                                    onClick = {
                                        pendingShareData = com.coachie.app.service.ShareImageData(
                                            type = com.coachie.app.service.ShareImageType.STREAK,
                                            title = "Habit Streak",
                                            metric = "${streakData.currentStreak}",
                                            subtitle = "${streakData.currentStreak}-day streak",
                                            streakFlame = streakData.currentStreak
                                        )
                                        showShareDialog = true
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Share,
                                        contentDescription = "Share streak",
                                        tint = Primary40
                                    )
                                }
                            }
                        }
                        
                        streak?.let { streakData ->
                            if (streakData.longestStreak > 0) {
                                Text(
                                    text = "Personal Best: ${streakData.longestStreak} days",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

            // Streak rules
            CoachieCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CoachieCardDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.95f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "How Streaks Work",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    StreakRuleItem(
                        title = "Daily Logging",
                        description = "Log at least one meal, workout, or water entry each day"
                    )

                    StreakRuleItem(
                        title = "Consecutive Days",
                        description = "Maintain your streak by logging every day without breaks"
                    )

                    StreakRuleItem(
                        title = "Missed Days",
                        description = "Missing a day resets your current streak to 0"
                    )

                    StreakRuleItem(
                        title = "Personal Best",
                        description = "Your longest streak is saved as your personal record"
                    )
                }
            }

            // Recent streak history
            CoachieCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CoachieCardDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.95f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Recent Activity (Last 7 Days)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (streakHistory.isEmpty()) {
                        Text(
                            text = "Loading streak history...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            streakHistory.take(7).forEach { streakDay ->
                                StreakHistoryItem(streakDay = streakDay)
                            }
                        }
                    }
                }
            }

            // Streak tips
            CoachieCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CoachieCardDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.95f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Streak Tips",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "• Set a daily reminder to log your activities",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "• Even small entries count toward your streak",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "• Use quick log buttons for faster entries",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "• Connect fitness devices for automatic logging",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            }
        }
    }
    
    // Share platform dialog
    if (showShareDialog) {
        SharePlatformDialog(
            onDismiss = { showShareDialog = false },
            onShareToPlatform = { platform ->
                showShareDialog = false
                pendingShareData?.let { data ->
                    shareService.generateAndShare(data, platform)
                }
                pendingShareData = null
            }
        )
    }
}
