package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.DailyLog
import com.coachie.app.viewmodel.DailyLogUiState
import com.coachie.app.viewmodel.DailyLogViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.theme.Primary40
import com.coachie.app.ui.theme.rememberCoachieGradient

@Composable
fun DailyLogScreen(
    userId: String,
    onBack: () -> Unit,
    onNavigateToMealLog: () -> Unit = {},
    onNavigateToSupplementLog: () -> Unit = {},
    onNavigateToWorkoutLog: () -> Unit = {},
    onNavigateToSleepLog: () -> Unit = {},
    onNavigateToWaterLog: () -> Unit = {},
    onNavigateToWeightLog: () -> Unit = {},
    onNavigateToHealthTracking: () -> Unit = {},
    viewModel: DailyLogViewModel = viewModel(
        factory = DailyLogViewModel.Factory(FirebaseRepository.getInstance(), userId)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val useImperial by viewModel.useImperial.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(userId) {
        viewModel.loadTodayLog()
    }

    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBackground)
    ) {
        Scaffold(
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
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                "Back",
                                tint = Primary40
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Daily Log",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Primary40
                        )
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {

        // Date selector
        DateSelector(
            selectedDate = selectedDate,
            onDateSelected = { viewModel.selectDate(it) }
        )

        when (uiState) {
            is DailyLogUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is DailyLogUiState.Success -> {
                val state = uiState as DailyLogUiState.Success
                if (!state.log.hasData) {
                    // Show quick log buttons when no data
                    EmptyLogState(
                        onNavigateToMealLog = onNavigateToMealLog,
                        onNavigateToSupplementLog = onNavigateToSupplementLog,
                        onNavigateToWorkoutLog = onNavigateToWorkoutLog,
                        onNavigateToSleepLog = onNavigateToSleepLog,
                        onNavigateToWaterLog = onNavigateToWaterLog,
                        onNavigateToWeightLog = onNavigateToWeightLog,
                        onNavigateToHealthTracking = onNavigateToHealthTracking
                    )
                } else {
                    DailyLogForm(
                        log = state.log,
                        useImperial = useImperial,
                        onLogChanged = { viewModel.updateLog(it) },
                        onSave = { viewModel.saveLog() }
                    )
                }
            }

            is DailyLogUiState.Error -> {
                val state = uiState as DailyLogUiState.Error
                ErrorContent(
                    error = state.message,
                    onRetry = { viewModel.loadTodayLog() }
                )
            }
        }

        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("Back")
            }

            Button(
                onClick = { viewModel.saveLog() },
                modifier = Modifier.weight(1f),
                enabled = uiState is DailyLogUiState.Success,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary40
                )
            ) {
                Text("Save Log", color = Color.White)
            }
        }
            }
        }
    }
}

@Composable
private fun DateSelector(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = { onDateSelected(yesterday) },
            enabled = selectedDate != yesterday
        ) {
            Text("Yesterday")
        }

        Text(
            text = selectedDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        OutlinedButton(
            onClick = { onDateSelected(today) },
            enabled = selectedDate != today
        ) {
            Text("Today")
        }
    }
}

@Composable
private fun DailyLogForm(
    log: DailyLog,
    useImperial: Boolean,
    onLogChanged: (DailyLog) -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Weight input
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Weight",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Convert weight for display
                    val displayWeight = if (useImperial && log.weight != null) {
                        log.weight * 2.205 // kg to lbs
                    } else {
                        log.weight
                    }
                    
                    OutlinedTextField(
                        value = displayWeight?.toString() ?: "",
                        onValueChange = { value ->
                            val inputWeight = value.toDoubleOrNull()
                            if (inputWeight != null) {
                                // Convert back to kg for storage
                                val weightInKg = if (useImperial) {
                                    inputWeight / 2.205
                                } else {
                                    inputWeight
                                }
                                onLogChanged(log.copy(weight = weightInKg))
                            } else {
                                onLogChanged(log.copy(weight = null))
                            }
                        },
                        label = { Text(if (useImperial) "Weight (lbs)" else "Weight (kg)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    if (log.weight != null) {
                        val displayValue = if (useImperial) {
                            "${(log.weight * 2.205).toInt()} lbs"
                        } else {
                            "${log.weight} kg"
                        }
                        Text(
                            text = displayValue,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Steps input
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Steps",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = log.steps?.toString() ?: "",
                        onValueChange = { value ->
                            val steps = value.toIntOrNull()
                            onLogChanged(log.copy(steps = steps))
                        },
                        label = { Text("Step count") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    if (log.steps != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${log.steps}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${(log.stepGoalProgress * 100).toInt()}% of goal",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (log.stepGoalProgress >= 1.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Water intake
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Water Intake",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Convert water for display
                    val displayWater = if (useImperial && log.water != null) {
                        (log.water * 0.033814).roundToInt() // ml to fl oz - use roundToInt() for proper rounding
                    } else {
                        log.water
                    }
                    
                    OutlinedTextField(
                        value = displayWater?.toString() ?: "",
                        onValueChange = { value ->
                            val inputWater = value.toIntOrNull()
                            if (inputWater != null) {
                                // Convert back to ml for storage
                                val waterInMl = if (useImperial) {
                                    (inputWater / 0.033814).toInt()
                                } else {
                                    inputWater
                                }
                                onLogChanged(log.copy(water = waterInMl))
                            } else {
                                onLogChanged(log.copy(water = null))
                            }
                        },
                        label = { Text(if (useImperial) "Water (fl oz)" else "Water (ml)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    if (log.water != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            val displayValue = if (useImperial) {
                                val flOz = (log.water * 0.033814).roundToInt()
                                val gallons = flOz / 128.0
                                if (gallons >= 1.0) {
                                    "${flOz} fl oz (${String.format("%.2f", gallons)} gal)"
                                } else {
                                    "${flOz} fl oz"
                                }
                            } else {
                                "${log.water} ml (${log.waterInLiters}L)"
                            }
                            Text(
                                text = displayValue,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Quick add buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val glassAmount = if (useImperial) {
                        (DailyLog.WATER_GLASS * 0.033814).roundToInt() // ml to fl oz
                    } else {
                        DailyLog.WATER_GLASS
                    }
                    val bottleAmount = if (useImperial) {
                        (DailyLog.WATER_BOTTLE * 0.033814).roundToInt() // ml to fl oz
                    } else {
                        DailyLog.WATER_BOTTLE
                    }
                    
                    OutlinedButton(
                        onClick = {
                            val current = log.water ?: 0
                            onLogChanged(log.copy(water = current + DailyLog.WATER_GLASS))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (useImperial) "+ Glass (${glassAmount} fl oz)" else "+ Glass (${DailyLog.WATER_GLASS}ml)")
                    }

                    OutlinedButton(
                        onClick = {
                            val current = log.water ?: 0
                            onLogChanged(log.copy(water = current + DailyLog.WATER_BOTTLE))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (useImperial) "+ Bottle (${bottleAmount} fl oz)" else "+ Bottle (${DailyLog.WATER_BOTTLE}ml)")
                    }
                }
            }
        }

        // Mood selector
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Mood",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (mood in 1..5) {
                        FilterChip(
                            selected = log.mood == mood,
                            onClick = { onLogChanged(log.copy(mood = mood)) },
                            label = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(getMoodEmoji(mood))
                                    Text(
                                        text = when (mood) {
                                            1 -> "Bad"
                                            2 -> "Low"
                                            3 -> "Okay"
                                            4 -> "Good"
                                            5 -> "Great"
                                            else -> ""
                                        },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (log.mood != null) {
                    Text(
                        text = "Feeling: ${log.moodDescription}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // Notes
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Notes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = log.notes ?: "",
                    onValueChange = { notes ->
                        onLogChanged(log.copy(notes = notes.ifBlank { null }))
                    },
                    label = { Text("Daily notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
            }
        }

        // Summary
        if (log.hasData) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Daily Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val summaryItems = mutableListOf<String>()
                    log.weight?.let { 
                        val weightText = if (useImperial) {
                            "${(it * 2.205).toInt()} lbs"
                        } else {
                            "${it} kg"
                        }
                        summaryItems.add("Weight: $weightText")
                    }
                    log.steps?.let { summaryItems.add("Steps: ${it}") }
                    log.water?.let { 
                        val waterText = if (useImperial) {
                            val flOz = (it * 0.033814).roundToInt()
                            "${flOz} fl oz"
                        } else {
                            "${log.waterInLiters}L"
                        }
                        summaryItems.add("Water: $waterText")
                    }
                    log.mood?.let { summaryItems.add("Mood: ${log.moodDescription}") }

                    summaryItems.forEach { item ->
                        Text(
                            text = "• $item",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "⚠️",
            style = MaterialTheme.typography.displayLarge
        )

        Text(
            text = "Unable to load daily log",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Button(onClick = onRetry) {
            Text("Try Again")
        }
    }
}

private fun getMoodEmoji(mood: Int): String {
    return when (mood) {
        1 -> "😞"
        2 -> "😐"
        3 -> "😊"
        4 -> "😄"
        5 -> "😍"
        else -> "🤔"
    }
}


