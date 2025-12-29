package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restaurant
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.coachie.app.ui.theme.rememberCoachieGradient
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.HealthLog
import com.coachie.app.viewmodel.HomeDashboardViewModel
import com.coachie.app.viewmodel.AuthState
import com.coachie.app.viewmodel.CoachieViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaloriesDetailScreen(
    onBack: () -> Unit,
    viewModel: CoachieViewModel = viewModel()
) {
    val authState by viewModel.authState.collectAsState()
    val currentUser = (authState as? AuthState.Authenticated)?.user
    val userId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: return

    // Create dashboard ViewModel with proper factory
    val context = LocalContext.current
    val dashboardViewModel = viewModel<HomeDashboardViewModel>(
        key = userId,
        factory = HomeDashboardViewModel.Factory(
            repository = FirebaseRepository.getInstance(),
            userId = userId,
            context = context
        )
    )

    // Get today's data from dashboard view model
    val todayHealthLogs by dashboardViewModel.todayHealthLogs.collectAsState()
    val profile by dashboardViewModel.profile.collectAsState()

    // Load dashboard data when screen appears
    LaunchedEffect(userId) {
        dashboardViewModel.refresh()
    }

    val meals = todayHealthLogs.filterIsInstance<HealthLog.MealLog>()
    val workouts = todayHealthLogs.filterIsInstance<HealthLog.WorkoutLog>()

    val caloriesConsumed = meals.sumOf { it.calories }
    // Calories burned: prefer Google Fit energy expended (total daily energy), otherwise use workout calories
    val workoutCalories = workouts.sumOf { it.caloriesBurned ?: 0 }
    val todayLog by dashboardViewModel.todayLog.collectAsState()
    val caloriesBurned = workoutCalories
    val dailyGoal = profile?.estimatedDailyCalories ?: 2000
    // Calculate calories remaining to match the card (goal - consumed)
    val caloriesRemaining = dailyGoal - caloriesConsumed

    val gradientBackground = rememberCoachieGradient(endY = 1600f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBackground)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Calories Breakdown") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface, // Use onSurface for better readability on gradient
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface // Use onSurface for better readability
                    )
                )
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
            // Header with net calories
            CoachieCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CoachieCardDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
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
                        Icons.Filled.Restaurant,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )

                    Text(
                        text = caloriesRemaining.toString(),
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (caloriesRemaining >= 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                    )

                    Text(
                        text = "Calories Remaining",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface // Changed from onSecondaryContainer to onSurface for readability
                    )

                    Text(
                        text = "Goal: $dailyGoal cal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Calories In Section
            CoachieCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Calories In",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface // Changed from primary (orange) to onSurface for readability
                        )
                        Text(
                            text = "+$caloriesConsumed cal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface // Changed from primary (orange) to onSurface for readability
                        )
                    }

                    if (meals.isEmpty()) {
                        Text(
                            text = "No meals logged today",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        meals.forEach { meal ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = meal.foodName.take(30) + if (meal.foodName.length > 30) "..." else "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${meal.protein}g protein • ${meal.carbs}g carbs • ${meal.fat}g fat",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${meal.calories} cal",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface // Changed from primary (orange) to onSurface for readability
                                )
                            }
                        }
                    }
                }
            }

            // Calories Out Section
            CoachieCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Calories Out",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "-$caloriesBurned cal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (workouts.isEmpty()) {
                        Text(
                            text = "No workouts logged today",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        workouts.forEach { workout ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = workout.workoutType,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${workout.durationMin} minutes",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${workout.caloriesBurned ?: 0} cal",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // Progress indicator
            CoachieCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Daily Progress",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface // Added explicit color for readability
                    )

                    val progress = if (dailyGoal > 0) {
                        (caloriesConsumed.toFloat() / dailyGoal.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                        // Use primary color with better contrast for readability
                        color = if (progress >= 1f) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            // Use a darker, more readable color for progress
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) // Lighter track for better contrast
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${caloriesConsumed} / $dailyGoal cal",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface // Added explicit color for readability
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface // Added explicit color for readability
                        )
                    }
                }
            }
        }
    }
    }
}
