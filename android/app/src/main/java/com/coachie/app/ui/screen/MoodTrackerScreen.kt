package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.defaultMinSize
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.viewmodel.MoodTrackerViewModel
import com.coachie.app.data.model.FlowIntensity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodTrackerScreen(
    viewModel: MoodTrackerViewModel = viewModel(),
    userId: String,
    onNavigateBack: () -> Unit
) {
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mood Tracker") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.showSaveDialog(true) },
                        enabled = !uiState.isLoading
                    ) {
                        Icon(Icons.Filled.Save, "Save Mood")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.Black,
                    navigationIconContentColor = Color.Black,
                    actionIconContentColor = Color.Black
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
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Mood Level
                CoachieCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "How are you feeling?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        val moodOptions = listOf(
                            "😀 Great" to 5,
                            "🙂 Good" to 4,
                            "😐 Okay" to 3,
                            "😕 Not great" to 2,
                            "😢 Bad" to 1
                        )

                        moodOptions.forEach { (label, value) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.updateMoodLevel(value)
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = label, style = MaterialTheme.typography.bodyLarge)
                                if (uiState.moodLevel == value) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // Emotions
                CoachieCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "What emotions are you experiencing?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // Reorganize emotions to put longer words in rows with fewer items
                        val emotions = listOf(
                            "Joyful", "Happy", "Calm",
                            "Peaceful", "Grateful", "Anxious",
                            "Sad", "Angry", "Frustrated",
                            "Overwhelmed", "Excited", "Motivated",
                            "Tired", "Stressed", "Lonely"
                        )

                        // Group emotions - put "Overwhelmed" in a row with only 2 items
                        val emotionRows = listOf(
                            listOf("Joyful", "Happy", "Calm"),
                            listOf("Peaceful", "Grateful", "Anxious"),
                            listOf("Sad", "Angry", "Frustrated"),
                            listOf("Overwhelmed", "Excited"), // Only 2 items for this row
                            listOf("Motivated", "Tired", "Stressed"),
                            listOf("Lonely")
                        )

                        emotionRows.forEach { rowEmotions ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowEmotions.forEach { emotion ->
                                    val isSelected = uiState.selectedEmotions.contains(emotion)
                                    // Use AssistChip for better text handling, or adjust FilterChip
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (isSelected) {
                                                viewModel.removeEmotion(emotion)
                                            } else {
                                                viewModel.addEmotion(emotion)
                                            }
                                        },
                                        label = { 
                                            Text(
                                                text = emotion,
                                                maxLines = 1,
                                                style = MaterialTheme.typography.labelSmall,
                                                textAlign = TextAlign.Center,
                                                color = Color.Black
                                            ) 
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            selectedLabelColor = Color.Black
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minWidth = 0.dp)
                                    )
                                }
                                // Add spacer if row has less than 3 items
                                repeat(3 - rowEmotions.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // Energy/Sleep
                CoachieCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Energy & Sleep Quality",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text("Energy Level", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = uiState.energyLevel.toFloat(),
                            onValueChange = { viewModel.updateEnergyLevel(it.toInt()) },
                            valueRange = 1f..10f,
                            steps = 8
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Low", style = MaterialTheme.typography.bodySmall)
                            Text("${uiState.energyLevel}/10", style = MaterialTheme.typography.bodyMedium)
                            Text("High", style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Sleep Quality", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = uiState.sleepQuality.toFloat(),
                            onValueChange = { viewModel.updateSleepQuality(it.toInt()) },
                            valueRange = 1f..10f,
                            steps = 8
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Poor", style = MaterialTheme.typography.bodySmall)
                            Text("${uiState.sleepQuality}/10", style = MaterialTheme.typography.bodyMedium)
                            Text("Excellent", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Stress/Triggers
                CoachieCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Stress & Triggers",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text("Stress Level", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = uiState.stressLevel.toFloat(),
                            onValueChange = { viewModel.updateStressLevel(it.toInt()) },
                            valueRange = 1f..10f,
                            steps = 8
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Low", style = MaterialTheme.typography.bodySmall)
                            Text("${uiState.stressLevel}/10", style = MaterialTheme.typography.bodyMedium)
                            Text("High", style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = uiState.triggers,
                            onValueChange = { viewModel.updateTriggers(it) },
                            label = { Text("What triggered this mood?") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )
                    }
                }

                // Context
                CoachieCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Context & Activities",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        val contexts = listOf(
                            "Alone", "With family", "With friends", "At work", "At home",
                            "Outdoors", "Indoors", "Exercise", "Social event", "Relaxing"
                        )

                        Text("Current context:", style = MaterialTheme.typography.bodyMedium)
                        contexts.chunked(3).forEach { rowContexts ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowContexts.forEach { context ->
                                    val isSelected = uiState.selectedContexts.contains(context)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (isSelected) {
                                                viewModel.removeContext(context)
                                            } else {
                                                viewModel.addContext(context)
                                            }
                                        },
                                        label = { Text(context, color = Color.Black) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            selectedLabelColor = Color.Black
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = uiState.activities,
                            onValueChange = { viewModel.updateActivities(it) },
                            label = { Text("What have you been doing?") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )
                    }
                }

                // Notes
                CoachieCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Additional Notes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = uiState.notes,
                            onValueChange = { viewModel.updateNotes(it) },
                            label = { Text("Any additional thoughts or notes?") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6
                        )
                    }
                }

                // Recent Moods
                Text("Recent mood entries")

                // Error display
                uiState.error?.let { error ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
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

                // Save Button
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.saveMoodLog(userId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            Icons.Filled.Save,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Save Mood Entry",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Save confirmation dialog
    if (uiState.showSaveDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showSaveDialog(false) },
            title = { Text("Save Mood Entry?") },
            text = { Text("Are you sure you want to save this mood entry?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveMoodLog(userId)
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.showSaveDialog(false) }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}