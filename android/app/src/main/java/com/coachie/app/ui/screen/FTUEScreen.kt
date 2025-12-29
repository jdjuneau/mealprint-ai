package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

data class FTUEFeature(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconColor: Color,
    val navigationHint: String,
    val emoji: String = ""
)

@OptIn(ExperimentalPagerApi::class)
@Composable
fun FTUEScreen(
    onComplete: () -> Unit
) {
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val pagerState = rememberPagerState()
    val coroutineScope = rememberCoroutineScope()
    
    val features = listOf(
        FTUEFeature(
            title = "Track Your Health",
            description = "Log meals, workouts, sleep, water, and more. Sync with Google Fit or Health Connect for automatic tracking.",
            icon = Icons.Filled.Favorite,
            iconColor = MaterialTheme.colorScheme.primary,
            navigationHint = "Tap 'Health Tracking' on the home screen to log activities",
            emoji = "📊"
        ),
        FTUEFeature(
            title = "Voice Logging",
            description = "Log meals, workouts, water, sleep, habits, and journal entries by simply speaking. Just say what you want to log and Coachie understands.",
            icon = Icons.Filled.Mic,
            iconColor = MaterialTheme.colorScheme.tertiary,
            navigationHint = "Tap the microphone button in the bottom right corner of the home screen to start voice logging",
            emoji = "🎤"
        ),
        FTUEFeature(
            title = "Build Better Habits",
            description = "Create custom habits, track your streaks, and get AI-powered suggestions to help you stay consistent.",
            icon = Icons.Filled.CheckCircle,
            iconColor = MaterialTheme.colorScheme.secondary,
            navigationHint = "Go to 'Habits' in the bottom navigation to get started",
            emoji = "🎯"
        ),
        FTUEFeature(
            title = "AI-Powered Coaching",
            description = "Chat with your AI coach, get personalized meal plans, and receive daily insights based on your progress.",
            icon = Icons.Filled.Psychology,
            iconColor = MaterialTheme.colorScheme.tertiary,
            navigationHint = "Tap on the daily brief/insight card on your home screen to chat with your AI coach",
            emoji = "🤖"
        ),
        FTUEFeature(
            title = "Weekly Meal Planning",
            description = "Get AI-generated meal plans with shopping lists. Save recipes, share with friends, and never wonder what to cook.",
            icon = Icons.Filled.RestaurantMenu,
            iconColor = MaterialTheme.colorScheme.primary,
            navigationHint = "Look for 'Weekly Blueprint' on your home screen, or use 'AI Meal Inspiration' when logging meals",
            emoji = "🍽️"
        ),
        FTUEFeature(
            title = "Connect & Share",
            description = "Join Circles for accountability, connect with friends, share your progress, and participate in community forums.",
            icon = Icons.Filled.People,
            iconColor = MaterialTheme.colorScheme.secondary,
            navigationHint = "Visit 'Community' in the bottom navigation to explore",
            emoji = "👥"
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Skip button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onComplete) {
                    Text("Skip", color = MaterialTheme.colorScheme.onSurface)
                }
            }

            // Pager
            HorizontalPager(
                count = features.size,
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                FeaturePage(feature = features[page])
            }

            // Page indicators
            Row(
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(features.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isSelected) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                }
                            )
                    )
                }
            }

            // Navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .padding(bottom = 16.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()), // Account for system navigation bar
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button (hidden on first page)
                if (pagerState.currentPage > 0) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Previous",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.width(80.dp))
                }

                // Next/Get Started button
                Button(
                    onClick = {
                        if (pagerState.currentPage < features.size - 1) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onComplete()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (pagerState.currentPage < features.size - 1) "Next" else "Get Started",
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FeaturePage(feature: FTUEFeature) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Emoji
        Text(
            text = feature.emoji,
            fontSize = 80.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(feature.iconColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = feature.iconColor
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Title
        Text(
            text = feature.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Description
        Text(
            text = feature.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Navigation hint
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = feature.navigationHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

