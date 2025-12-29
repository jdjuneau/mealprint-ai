package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import kotlin.math.roundToInt
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.viewmodel.SmartSchedulingViewModel
import com.coachie.app.viewmodel.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartSchedulingScreen(
    onBack: () -> Unit,
    viewModel: SmartSchedulingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val gradientBackground = rememberCoachieGradient(endY = 2000f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Scheduling") },
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
                // Header
                item {
                    CoachieCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🧠 AI-Powered Scheduling",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Optimized habit timing based on your circadian rhythm, energy patterns, and environmental factors.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Circadian Rhythm Analysis
                item {
                    CircadianRhythmSection(uiState.circadianRhythm)
                }

                // Environmental Factors
                item {
                    EnvironmentalFactorsSection(uiState.environmentalFactors)
                }

                // Optimal Time Slots
                item {
                    TimeSlotsSection(uiState.optimalTimeSlots)
                }

                // Habit Schedule Recommendations
                item {
                    HabitRecommendationsSection(uiState.habitSchedules)
                }

                // Error message
                uiState.error?.let { error ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
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
private fun CircadianRhythmSection(circadianData: CircadianRhythmData) {
    CoachieCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🌅 Your Circadian Rhythm",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Energy Score and Rhythm Strength
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Energy Score",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(circadianData.energyScore * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Rhythm Strength",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(circadianData.rhythmStrength * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            circadianData.rhythmStrength > 0.8 -> MaterialTheme.colorScheme.primary
                            circadianData.rhythmStrength > 0.6 -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                }
            }

            // Sleep Schedule
            circadianData.sleepSchedule?.let { sleep ->
                CoachieCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CoachieCardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "😴 Sleep Patterns",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Avg Sleep",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${sleep.averageSleepHours.roundToInt()}h",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Bedtime",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = sleep.averageBedTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Wake Time",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = sleep.averageWakeTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Energy Peaks
            if (circadianData.energyPeaks.isNotEmpty()) {
                Text(
                    text = "⚡ Peak Energy Times:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                circadianData.energyPeaks.take(3).forEach { peak ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = peak.time.format(DateTimeFormatter.ofPattern("HH:mm")),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LinearProgressIndicator(
                            progress = peak.energyLevel.toFloat(),
                            modifier = Modifier.width(100.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${(peak.energyLevel * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnvironmentalFactorsSection(environmentalData: EnvironmentalFactors) {
    CoachieCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🌤️ Environmental Context",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Weather
            environmentalData.weather?.let { weather ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${weather.condition} • ${weather.temperature.roundToInt()}°C",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (weather.isOutdoorFriendly) "🌞 Good for outdoors" else "🏠 Better indoors",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (weather.isOutdoorFriendly)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Location and Context
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Location",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = environmentalData.location?.type?.name ?: "Unknown",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Time of Day",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = environmentalData.timeOfDay.name.lowercase().replace("_", " "),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Day Type",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = environmentalData.dayOfWeek.name.lowercase(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeSlotsSection(timeSlots: List<TimeSlot>) {
    CoachieCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "⏰ Optimal Time Slots",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (timeSlots.isEmpty()) {
                Text(
                    text = "No scheduling data available yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Show hourly breakdown
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    timeSlots.forEach { slot ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Time
                            Text(
                                text = slot.time.format(DateTimeFormatter.ofPattern("HH:mm")),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.width(60.dp)
                            )

                            // Energy bar
                            LinearProgressIndicator(
                                progress = slot.energyLevel.toFloat(),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp),
                                color = when {
                                    slot.isOptimal -> MaterialTheme.colorScheme.primary
                                    slot.energyLevel > 0.6 -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )

                            // Status
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (slot.isOptimal) {
                                    Text(
                                        text = "⭐",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                if (slot.bookedHabits.isNotEmpty()) {
                                    Text(
                                        text = "📅",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Text(
                                    text = "${(slot.energyLevel * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.width(40.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitRecommendationsSection(recommendations: List<HabitScheduleRecommendation>) {
    CoachieCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🎯 Personalized Schedule",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (recommendations.isEmpty()) {
                Text(
                    text = "No habits to schedule yet. Create some habits first!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                recommendations.forEach { recommendation ->
                    HabitRecommendationCard(recommendation)
                }
            }
        }
    }
}

@Composable
private fun HabitRecommendationCard(recommendation: HabitScheduleRecommendation) {
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.cardColors(
            containerColor = when {
                recommendation.successProbability > 0.8 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                recommendation.successProbability > 0.6 -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recommendation.habitName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = recommendation.category.name.replace("_", " "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = when {
                        recommendation.successProbability > 0.8 -> MaterialTheme.colorScheme.primary
                        recommendation.successProbability > 0.6 -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.error
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "${(recommendation.successProbability * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Recommended Time
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.Schedule,
                    contentDescription = "Recommended time",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Recommended: ${recommendation.recommendedTime.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            // Reasoning
            if (recommendation.reasoning.isNotEmpty()) {
                Text(
                    text = "💡 Why this time:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                recommendation.reasoning.forEach { reason ->
                    Text(
                        text = "• $reason",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Alternative Times
            if (recommendation.alternativeTimes.isNotEmpty()) {
                Text(
                    text = "⏰ Alternatives:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recommendation.alternativeTimes.take(3).forEach { time ->
                        AssistChip(
                            onClick = { /* Could implement time selection */ },
                            label = { Text(time.format(DateTimeFormatter.ofPattern("HH:mm"))) }
                        )
                    }
                }
            }
        }
    }
}
