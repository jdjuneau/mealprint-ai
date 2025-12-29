package com.coachie.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.viewmodel.AuthState
import com.coachie.app.viewmodel.CoachieViewModel
import com.coachie.app.viewmodel.HomeDashboardViewModel
import com.coachie.app.ui.components.CoachieCard as Card
import com.coachie.app.ui.components.CoachieCardDefaults as CardDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsBreakdownScreen(
    onBack: () -> Unit,
    viewModel: CoachieViewModel = viewModel()
) {
    val authState by viewModel.authState.collectAsState()
    val currentUser = (authState as? AuthState.Authenticated)?.user
    val userId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: return

    // Create dashboard ViewModel to access user goals
    val context = LocalContext.current
    val dashboardViewModel = viewModel<HomeDashboardViewModel>(
        key = userId,
        factory = HomeDashboardViewModel.Factory(
            repository = FirebaseRepository.getInstance(),
            userId = userId,
            context = context
        )
    )

    val userGoals by dashboardViewModel.userGoals.collectAsState()
    val profile by dashboardViewModel.profile.collectAsState()

    // Load dashboard data when screen appears
    LaunchedEffect(userId) {
        dashboardViewModel.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Goals Breakdown") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🎯 Your Fitness Goals",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Track your progress and stay motivated",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            val goals = userGoals // Local variable to avoid smart cast issues
            
            if (goals != null && goals.isNotEmpty()) {
                // Main Goal
                val selectedGoal = goals["selectedGoal"] as? String
                if (selectedGoal != null) {
                    GoalDetailCard(
                        title = "Main Goal",
                        value = selectedGoal,
                        icon = "🎯",
                        description = "Your primary fitness objective"
                    )
                }

                // Fitness Level
                val fitnessLevel = goals["fitnessLevel"] as? String
                if (fitnessLevel != null) {
                    GoalDetailCard(
                        title = "Fitness Level",
                        value = fitnessLevel,
                        icon = "💪",
                        description = "Your current fitness experience"
                    )
                }

                // Weekly Workouts
                val weeklyWorkouts = goals["weeklyWorkouts"] as? Number
                if (weeklyWorkouts != null) {
                    GoalDetailCard(
                        title = "Weekly Workouts",
                        value = "${weeklyWorkouts} per week",
                        icon = "📅",
                        description = "Target number of workouts each week"
                    )
                }

                // Daily Steps
                val dailySteps = goals["dailySteps"] as? Number
                if (dailySteps != null) {
                    GoalDetailCard(
                        title = "Daily Steps",
                        value = "${dailySteps}K steps",
                        icon = "👣",
                        description = "Your daily step goal"
                    )
                }

                // Gender
                val gender = goals["gender"] as? String
                if (gender != null) {
                    GoalDetailCard(
                        title = "Gender",
                        value = gender.replaceFirstChar { it.uppercase() },
                        icon = "👤",
                        description = "Used for personalized calculations"
                    )
                }

                // Get measurement preference first
                val useImperial = goals["useImperial"] as? Boolean ?: true

                // Profile-based goals
                profile?.let { userProfile ->
                    if (userProfile.goalWeight != null) {
                        GoalDetailCard(
                            title = "Goal Weight",
                            value = if (useImperial) {
                                "${"%.1f".format(userProfile.goalWeight * 2.20462262)} lbs"
                            } else {
                                "${userProfile.goalWeight} kg"
                            },
                            icon = "⚖️",
                            description = "Your target weight"
                        )
                    }

                    if (userProfile.estimatedDailyCalories != null) {
                        GoalDetailCard(
                            title = "Daily Calories",
                            value = "${userProfile.estimatedDailyCalories} cal",
                            icon = "🔥",
                            description = "Recommended daily calorie intake"
                        )
                    }
                }

                // Profile-based goals
                GoalDetailCard(
                    title = "Measurement Units",
                    value = if (useImperial) "Imperial (lbs, oz)" else "Metric (kg, ml)",
                    icon = "📏",
                    description = "Your preferred measurement system"
                )
            } else {
                // No goals set
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "🎯",
                            style = MaterialTheme.typography.displayMedium
                        )
                        Text(
                            text = "No Goals Set Yet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Set your fitness goals to get personalized recommendations and track your progress!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalDetailCard(
    title: String,
    value: String,
    icon: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Text(
                text = icon,
                style = MaterialTheme.typography.displaySmall
            )

            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

