package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.components.NavigationTileCard
import com.coachie.app.ui.theme.Primary40
import com.coachie.app.ui.theme.Tertiary40
import com.coachie.app.ui.theme.MaleTertiary40
import com.coachie.app.ui.theme.Secondary40
import com.coachie.app.ui.theme.MaleSecondary40
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.ui.theme.LocalGender
import com.coachie.app.ui.theme.SemanticColorCategory
import com.coachie.app.ui.theme.getSemanticColorPrimary
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.viewmodel.HomeDashboardViewModel
import com.coachie.app.viewmodel.MicronutrientTrackerViewModel
import com.coachie.app.data.model.MicronutrientType
import com.coachie.app.util.SunshineVitaminDCalculator
import com.coachie.app.ui.screen.SunshineLogCard
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WellnessDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHabits: () -> Unit = {},
    onNavigateToMeditation: () -> Unit = {},
    onNavigateToBreathingExercises: () -> Unit = {},
    onNavigateToJournal: () -> Unit = {},
    onNavigateToJournalHistory: () -> Unit = {},
    onNavigateToMyWins: () -> Unit = {},
    onNavigateToMoodTracker: () -> Unit = {},
    onNavigateToSocialMediaBreak: () -> Unit = {},
    dashboardViewModel: HomeDashboardViewModel = viewModel()
) {
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val userId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: return
    val gender = LocalGender.current
    val isMale = gender.lowercase() == "male"
    // Use semantic color for Wellness (teal theme)
    val accentColor = getSemanticColorPrimary(SemanticColorCategory.WELLNESS, isMale)
    val micronutrientViewModel: MicronutrientTrackerViewModel = viewModel(
        factory = MicronutrientTrackerViewModel.Factory(
            repository = com.coachie.app.data.FirebaseRepository.getInstance(),
            userId = userId
        )
    )
    val sunshineTotals by micronutrientViewModel.sunshineTotals.collectAsState()
    val isSaving by micronutrientViewModel.isSaving.collectAsState()
    
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
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                "Back",
                                tint = accentColor
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Wellness",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(
                    top = 16.dp,
                    bottom = 16.dp
                )
            ) {
                // Wellness Tools
                item {
                    NavigationTileCard(
                        title = "Mood Tracker",
                        description = "Track your daily mood and emotions",
                        icon = Icons.Filled.Mood,
                        iconTint = accentColor,
                        backgroundColor = accentColor,
                        onClick = onNavigateToMoodTracker
                    )
                }
                
                item {
                    NavigationTileCard(
                        title = "Meditation",
                        description = "Guided meditation sessions",
                        icon = Icons.Filled.SelfImprovement,
                        iconTint = accentColor,
                        backgroundColor = accentColor,
                        onClick = onNavigateToMeditation
                    )
                }
                
                item {
                    NavigationTileCard(
                        title = "Breathing Exercises",
                        description = "Relax and focus with guided breathing",
                        icon = Icons.Filled.Air,
                        iconTint = accentColor,
                        backgroundColor = accentColor,
                        onClick = onNavigateToBreathingExercises
                    )
                }
                
                item {
                    NavigationTileCard(
                        title = "Journal",
                        description = "Reflect on your day and track progress",
                        icon = Icons.Filled.EditNote,
                        iconTint = accentColor,
                        backgroundColor = accentColor,
                        onClick = onNavigateToJournal
                    )
                }

                item {
                    NavigationTileCard(
                        title = "Journal History",
                        description = "Revisit your past reflections",
                        icon = Icons.Filled.History,
                        iconTint = accentColor,
                        backgroundColor = accentColor,
                        onClick = onNavigateToJournalHistory
                    )
                }
                
                item {
                    NavigationTileCard(
                        title = "Social Media Break",
                        description = "Take a mindful break from social media",
                        icon = Icons.Filled.PhoneAndroid,
                        iconTint = accentColor,
                        backgroundColor = accentColor,
                        onClick = onNavigateToSocialMediaBreak
                    )
                }

                item {
                    NavigationTileCard(
                        title = "My Wins",
                        description = "Celebrate your achievements",
                        icon = Icons.Filled.EmojiEvents,
                        iconTint = accentColor,
                        backgroundColor = accentColor,
                        onClick = onNavigateToMyWins
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Sunshine Tracker",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                item {
                    SunshineLogCard(
                        currentVitaminDIu = sunshineTotals[MicronutrientType.VITAMIN_D] ?: 0.0,
                        isSaving = isSaving,
                        onLogSunshine = { minutes: Int, uvIndex: Double, exposure: SunshineVitaminDCalculator.ExposureLevel, skinType: SunshineVitaminDCalculator.SkinType ->
                            android.util.Log.d("WellnessDashboard", "Logging sunshine: $minutes min, UV $uvIndex, exposure ${exposure.name}, skin ${skinType.name}")
                            micronutrientViewModel.logSunshineExposure(minutes, uvIndex, exposure, skinType)
                        }
                    )
                }
                
                // Wellness Charts Section
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Wellness Charts & Trends",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                item {
                    // Embed wellness charts directly
                    WellnessChartsEmbedded(dashboardViewModel = dashboardViewModel)
                }
            }
        }
    }
}

@Composable
fun WellnessChartsEmbedded(
    dashboardViewModel: HomeDashboardViewModel
) {
    WellnessChartsView(
        dashboardViewModel = dashboardViewModel,
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp)
    )
}

