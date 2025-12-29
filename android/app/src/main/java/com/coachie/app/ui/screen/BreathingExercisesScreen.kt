package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.coachie.app.ui.components.BoxBreathing
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.components.NavigationTileCard
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.ui.theme.LocalGender
import com.coachie.app.ui.theme.SemanticColorCategory
import com.coachie.app.ui.theme.getSemanticColorPrimary
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreathingExercisesScreen(
    onNavigateBack: () -> Unit,
    userId: String? = null
) {
    val authenticatedUserId = userId ?: com.coachie.app.util.AuthUtils.getAuthenticatedUserId()
    if (authenticatedUserId == null) {
        return
    }
    val safeUserId = authenticatedUserId
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val gender = LocalGender.current
    val isMale = gender.lowercase() == "male"
    val accentColor = getSemanticColorPrimary(SemanticColorCategory.WELLNESS, isMale)
    var showBoxBreathing by remember { mutableStateOf(false) }
    var selectedDuration by remember { mutableStateOf<Int?>(null) }
    var selectedStressLevel by remember { mutableStateOf(3) }
    var selectedPattern by remember { mutableStateOf(com.coachie.app.viewmodel.BoxBreathingViewModel.BreathingPattern.BOX) }
    
    // Show breathing exercise if selected
    if (showBoxBreathing) {
        BoxBreathing(
            stressLevel = selectedStressLevel,
            userId = safeUserId,
            customDurationSeconds = selectedDuration,
            pattern = selectedPattern,
            onComplete = {
                showBoxBreathing = false
                selectedDuration = null
            }
        )
        return
    }
    
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
                            "Breathing Exercises",
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
                item {
                    Text(
                        "Choose a breathing exercise to help you relax and focus",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                // Quick 1-minute breathing
                item {
                    NavigationTileCard(
                        title = "Quick Calm",
                        description = "1-minute fast breathing (3-2-3-2) for quick stress relief",
                        icon = Icons.Filled.Air,
                        iconTint = accentColor,
                        backgroundColor = accentColor,
                        onClick = {
                            selectedDuration = 60
                            selectedStressLevel = 3
                            selectedPattern = com.coachie.app.viewmodel.BoxBreathingViewModel.BreathingPattern.QUICK_CALM
                            showBoxBreathing = true
                        }
                    )
                }
                
                // 3-minute gentle breathing
                item {
                    NavigationTileCard(
                        title = "Gentle Breathing",
                        description = "3-minute gentle breathing (4-4-6-2) with longer exhales",
                        icon = Icons.Filled.SelfImprovement,
                        iconTint = accentColor,
                        backgroundColor = accentColor,
                        onClick = {
                            selectedDuration = 180
                            selectedStressLevel = 2
                            selectedPattern = com.coachie.app.viewmodel.BoxBreathingViewModel.BreathingPattern.GENTLE
                            showBoxBreathing = true
                        }
                    )
                }
                
                // 5-minute deep breathing
                item {
                    NavigationTileCard(
                        title = "Deep Focus",
                        description = "5-minute 4-7-8 breathing technique for deep focus",
                        icon = Icons.Filled.Psychology,
                        iconTint = accentColor,
                        backgroundColor = accentColor,
                        onClick = {
                            selectedDuration = 300
                            selectedStressLevel = 1
                            selectedPattern = com.coachie.app.viewmodel.BoxBreathingViewModel.BreathingPattern.DEEP_FOCUS
                            showBoxBreathing = true
                        }
                    )
                }
                
                // Standard box breathing (default duration)
                item {
                    NavigationTileCard(
                        title = "Box Breathing",
                        description = "Classic 4-4-4-4 breathing pattern for balance",
                        icon = Icons.Filled.Favorite,
                        iconTint = accentColor,
                        backgroundColor = accentColor,
                        onClick = {
                            selectedDuration = null // Use default
                            selectedStressLevel = 3
                            selectedPattern = com.coachie.app.viewmodel.BoxBreathingViewModel.BreathingPattern.BOX
                            showBoxBreathing = true
                        }
                    )
                }
            }
        }
    }
}

