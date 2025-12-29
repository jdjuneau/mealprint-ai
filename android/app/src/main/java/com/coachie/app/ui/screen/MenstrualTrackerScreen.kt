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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.data.model.CyclePhase
import com.coachie.app.data.model.FlowIntensity
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.viewmodel.MenstrualCycleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenstrualTrackerScreen(
    viewModel: MenstrualCycleViewModel = viewModel(),
    userId: String,
    onNavigateBack: () -> Unit
) {
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // Load data when screen appears or userId changes
    LaunchedEffect(userId) {
        if (userId.isNotBlank()) {
            viewModel.loadCycleData(userId)
            viewModel.loadRecentLogs(userId)
        }
    }

    // Helper function to map symptom name to enum
    fun getSymptomEnum(symptomName: String): com.coachie.app.data.model.MenstrualSymptom? {
        return when (symptomName) {
            "Cramps" -> com.coachie.app.data.model.MenstrualSymptom.CRAMPS
            "Headache" -> com.coachie.app.data.model.MenstrualSymptom.HEADACHE
            "Fatigue" -> com.coachie.app.data.model.MenstrualSymptom.FATIGUE
            "Bloating" -> com.coachie.app.data.model.MenstrualSymptom.BLOATING
            "Breast Tenderness" -> com.coachie.app.data.model.MenstrualSymptom.BREAST_TENDERNESS
            "Mood Swings" -> com.coachie.app.data.model.MenstrualSymptom.MOOD_SWINGS
            "Back Pain" -> com.coachie.app.data.model.MenstrualSymptom.BACK_PAIN
            "Nausea" -> com.coachie.app.data.model.MenstrualSymptom.NAUSEA
            "Acne" -> com.coachie.app.data.model.MenstrualSymptom.ACNE
            "Insomnia" -> com.coachie.app.data.model.MenstrualSymptom.INSOMNIA
            else -> null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Menstrual Cycle Tracker") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
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
                // Current Phase Overview
                Text("Current Phase: ${uiState.cycleData?.getCurrentPhase() ?: CyclePhase.UNKNOWN}")

                // Quick Actions
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
                            text = "Quick Actions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { 
                                    viewModel.logPeriodStart(
                                        date = uiState.selectedDate,
                                        flowIntensity = uiState.flowIntensity,
                                        userId = userId
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !uiState.isLoading && userId.isNotBlank()
                            ) {
                                Text("Period Started")
                            }
                            OutlinedButton(
                                onClick = { 
                                    viewModel.logPeriodEnd(
                                        date = uiState.selectedDate,
                                        userId = userId
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !uiState.isLoading && userId.isNotBlank()
                            ) {
                                Text("Period Ended")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { 
                                    // TODO: Add ovulation logging
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !uiState.isLoading
                            ) {
                                Text("Ovulation")
                            }
                            OutlinedButton(
                                onClick = { 
                                    // Save current symptoms and flow intensity
                                    val symptomEnums = uiState.selectedSymptoms.mapNotNull { symptomName ->
                                        getSymptomEnum(symptomName)
                                    }.toSet()
                                    
                                    viewModel.logSymptoms(
                                        date = uiState.selectedDate,
                                        symptoms = symptomEnums,
                                        painLevel = uiState.painLevel,
                                        notes = uiState.notes,
                                        userId = userId
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !uiState.isLoading && userId.isNotBlank()
                            ) {
                                Text("Save Symptoms")
                            }
                        }
                    }
                }

                // Cycle Statistics
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
                            text = "Cycle Statistics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = uiState.cycleData?.averageCycleLength?.toString() ?: "28",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Avg Cycle Length",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = uiState.cycleData?.averagePeriodLength?.toString() ?: "5",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Avg Period Length",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = uiState.cycleData?.cycleHistory?.size?.toString() ?: "0",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Cycles Logged",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        uiState.cycleData?.let { cycleData ->
                            Text(
                                text = "Last Period: ${cycleData.lastPeriodStart?.toString() ?: "Not logged"}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Next Expected: ${cycleData.nextPredictedPeriod?.toString() ?: "Unknown"}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Current Symptoms
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
                            text = "Current Symptoms",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        val symptoms = listOf(
                            "Cramps", "Headache", "Fatigue", "Bloating", "Breast Tenderness",
                            "Mood Swings", "Back Pain", "Nausea", "Acne", "Insomnia"
                        )

                        symptoms.chunked(3).forEach { symptomRow ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                symptomRow.forEach { symptom ->
                                    val isSelected = uiState.selectedSymptoms.contains(symptom)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (isSelected) {
                                                viewModel.removeSymptom(symptom)
                                            } else {
                                                viewModel.addSymptom(symptom)
                                            }
                                        },
                                        label = { Text(symptom) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Flow Intensity
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
                            text = "Flow Intensity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        val flowOptions = listOf(
                            "Light" to FlowIntensity.LIGHT,
                            "Medium" to FlowIntensity.MEDIUM,
                            "Heavy" to FlowIntensity.HEAVY,
                            "Spotting" to FlowIntensity.SPOTTING
                        )

                        flowOptions.forEach { (label, intensity) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.updateFlowIntensity(intensity) }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = label, style = MaterialTheme.typography.bodyLarge)
                                if (uiState.flowIntensity == intensity) {
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

                // Recent Logs
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
                            text = "Recent Logs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        if (uiState.recentLogs.isEmpty()) {
                            Text(
                                text = "No recent logs. Start tracking your cycle!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            uiState.recentLogs.take(5).forEach { log ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = log.date.toString(),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = log.type,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // Save Button
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        // Save current symptoms and flow intensity
                        val symptomEnums = uiState.selectedSymptoms.mapNotNull { symptomName ->
                            getSymptomEnum(symptomName)
                        }.toSet()
                        
                        viewModel.logSymptoms(
                            date = uiState.selectedDate,
                            symptoms = symptomEnums,
                            painLevel = uiState.painLevel,
                            notes = uiState.notes,
                            userId = userId
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !uiState.isLoading && userId.isNotBlank()
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
                            "Save Current Entry",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

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
            }
        }
    }
}