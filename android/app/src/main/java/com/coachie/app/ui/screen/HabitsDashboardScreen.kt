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
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.ui.theme.LocalGender
import com.coachie.app.ui.theme.SemanticColorCategory
import com.coachie.app.ui.theme.getSemanticColorPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMyHabits: () -> Unit = {},
    onNavigateToHabitTemplates: () -> Unit = {},
    onNavigateToHabitProgress: () -> Unit = {},
    onNavigateToHabitIntelligence: () -> Unit = {}
) {
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    
    // Get semantic color for Habits (blue theme)
    val gender = LocalGender.current
    val isMale = gender.lowercase() == "male"
    val habitsColor = getSemanticColorPrimary(SemanticColorCategory.HABITS, isMale)
    
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
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)
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
                                tint = habitsColor
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Habits",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = habitsColor
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
                // My Habits
                item {
                    NavigationTileCard(
                        title = "My Habits",
                        description = "View and manage your active habits",
                        icon = Icons.Filled.CheckCircle,
                        iconTint = habitsColor,
                        backgroundColor = habitsColor,
                        onClick = onNavigateToMyHabits
                    )
                }
                
                // Habit Templates
                item {
                    NavigationTileCard(
                        title = "Habit Templates",
                        description = "Quick-add popular habits from templates",
                        icon = Icons.Filled.LibraryBooks,
                        iconTint = habitsColor,
                        backgroundColor = habitsColor,
                        onClick = onNavigateToHabitTemplates
                    )
                }
                
                // Habit Progress
                item {
                    NavigationTileCard(
                        title = "Habit Progress",
                        description = "Track your habit completion and streaks",
                        icon = Icons.Filled.TrendingUp,
                        iconTint = habitsColor,
                        backgroundColor = habitsColor,
                        onClick = onNavigateToHabitProgress
                    )
                }
                
                // Habit Intelligence
                item {
                    NavigationTileCard(
                        title = "Habit Intelligence",
                        description = "AI-powered insights and recommendations",
                        icon = Icons.Filled.AutoAwesome,
                        iconTint = habitsColor,
                        backgroundColor = habitsColor,
                        onClick = onNavigateToHabitIntelligence
                    )
                }
            }
        }
    }
}

