package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.data.model.DietaryPreference
import com.mealprint.ai.domain.MacroTargetsCalculator
import com.mealprint.ai.ui.components.CoachieCard
import com.mealprint.ai.ui.components.CoachieCardDefaults
import com.mealprint.ai.ui.components.UserGoalsCard
import com.mealprint.ai.ui.theme.Primary40
import com.mealprint.ai.ui.theme.rememberCoachieGradient
import com.mealprint.ai.viewmodel.AuthState
import com.mealprint.ai.viewmodel.CoachieViewModel
import com.mealprint.ai.viewmodel.HomeDashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToPersonalInfoEdit: () -> Unit = {},
    onNavigateToGoalsEdit: () -> Unit = {},
    onNavigateToDietaryPreferencesEdit: () -> Unit = {},
    onNavigateToMenstrualTracker: () -> Unit = {},
    onNavigateToMoodTracker: () -> Unit = {},
    onNavigateToVoiceSettings: () -> Unit = {},
    viewModel: CoachieViewModel = viewModel(),
    refreshTrigger: Int = 0
) {
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val authState by viewModel.authState.collectAsState()
    val currentUser = (authState as? AuthState.Authenticated)?.user
    val userId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: return

    // Get profile data from HomeDashboardViewModel
    val context = LocalContext.current
    val dashboardViewModel = viewModel<HomeDashboardViewModel>(
        key = userId,
        factory = HomeDashboardViewModel.Factory(
            repository = FirebaseRepository.getInstance(),
            userId = userId,
            context = context
        )
    )
    val profile by dashboardViewModel.profile.collectAsState()
    val useImperial by dashboardViewModel.useImperial.collectAsState()
    val userGoals by dashboardViewModel.userGoals.collectAsState()

    // Load profile data
    LaunchedEffect(userId, refreshTrigger) {
        dashboardViewModel.refresh()
    }

    // Calculate macro targets
    val macroTargets = remember(profile) {
        MacroTargetsCalculator.calculate(profile)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
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
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Profile Information Section
                Text(
                    text = "My Profile",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Personal Information Card
                CoachieCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToPersonalInfoEdit),
                    colors = CoachieCardDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Personal Information",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        ProfileInfoRow("Name", profile?.name ?: "Not set")
                        ProfileInfoRow("Age", if (profile?.age != null && profile!!.age > 0) "${profile!!.age} years" else "Not set")
                        ProfileInfoRow("Gender", profile?.gender?.takeIf { it.isNotBlank() }?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } ?: "Not set")
                        
                        if (profile?.heightCm != null && profile!!.heightCm > 0) {
                            val heightDisplay = if (useImperial) {
                                // Convert cm to total inches first, then split into feet and inches
                                val totalInches = (profile!!.heightCm / 2.54).toInt()
                                val feet = totalInches / 12
                                val inches = totalInches % 12
                                "$feet'$inches\""
                            } else {
                                "${profile!!.heightCm.toInt()} cm"
                            }
                            ProfileInfoRow("Height", heightDisplay)
                        }
                        
                        if (profile?.currentWeight != null && profile!!.currentWeight > 0) {
                            val weightDisplay = if (useImperial) {
                                "${(profile!!.currentWeight * 2.205).toInt()} lbs"
                            } else {
                                "${profile!!.currentWeight.toInt()} kg"
                            }
                            ProfileInfoRow("Current Weight", weightDisplay)
                        }
                        
                        if (profile?.goalWeight != null && profile!!.goalWeight > 0) {
                            val goalWeightDisplay = if (useImperial) {
                                "${(profile!!.goalWeight * 2.205).toInt()} lbs"
                            } else {
                                "${profile!!.goalWeight.toInt()} kg"
                            }
                            ProfileInfoRow("Goal Weight", goalWeightDisplay)
                        }
                        
                        ProfileInfoRow("Activity Level", profile?.activityLevel?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } ?: "Not set")
                    }
                }

                // My Goals Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToGoalsEdit)
                ) {
                    UserGoalsCard(
                        userGoals = userGoals,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Dietary Preferences Card
                CoachieCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToDietaryPreferencesEdit),
                    colors = CoachieCardDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Dietary Preferences",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        ProfileInfoRow("Dietary Preference", profile?.dietaryPreferenceEnum?.title ?: "Not set")
                        ProfileInfoRow("Daily Calorie Goal", "${macroTargets.calorieGoal} cal")
                        
                        Divider()
                        
                        Text(
                            text = "Macro Targets",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        ProfileInfoRow("Protein", "${macroTargets.proteinGrams}g (${macroTargets.proteinPercent}%)")
                        ProfileInfoRow("Carbs", "${macroTargets.carbsGrams}g (${macroTargets.carbsPercent}%)")
                        ProfileInfoRow("Fat", "${macroTargets.fatGrams}g (${macroTargets.fatPercent}%)")
                        
                        if (macroTargets.recommendation.isNotBlank()) {
                            Divider()
                            Text(
                                text = macroTargets.recommendation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability
                            )
                        }
                    }
                }

                // Removed Settings section - profile editing is now handled through the clickable cards above

                Spacer(modifier = Modifier.height(16.dp))

                // Sign Out Button
                Button(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Sign Out", color = MaterialTheme.colorScheme.onError)
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability on white cards
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ProfileMenuItem(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(end = 16.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            Text(
                text = "→",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}
