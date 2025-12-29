package com.coachie.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.viewmodel.LogEntryUiState
import com.coachie.app.viewmodel.LogEntryViewModel
import com.coachie.app.ui.components.CoachieCard as Card
import com.coachie.app.ui.components.CoachieCardDefaults as CardDefaults

@Composable
fun LogEntryScreen(
    userId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit = {},
    viewModel: LogEntryViewModel = viewModel(
        factory = LogEntryViewModel.Factory(FirebaseRepository.getInstance(), userId)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // Header
        Text(
            text = "Quick Log",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            textAlign = TextAlign.Center
        )

        when (uiState) {
            is LogEntryUiState.Success -> {
                LogEntryForm(
                    state = uiState as LogEntryUiState.Success,
                    onWeightChanged = { viewModel.updateWeight(it) },
                    onWaterChanged = { viewModel.updateWater(it) },
                    onMoodChanged = { viewModel.updateMood(it) },
                    onSave = { viewModel.saveEntry(onSaved) },
                    viewModel = viewModel
                )
            }

            is LogEntryUiState.Saving -> {
                SavingContent()
            }

            is LogEntryUiState.Error -> {
                ErrorContent(
                    error = (uiState as LogEntryUiState.Error).message,
                    onRetry = { viewModel.reset() }
                )
            }

            else -> {
                // Handle unexpected state
                Text("Unexpected state", modifier = Modifier.padding(16.dp))
            }
        }

        // Back button
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Back")
        }
    }
}

@Composable
private fun LogEntryForm(
    state: LogEntryUiState.Success,
    onWeightChanged: (Double?) -> Unit,
    onWaterChanged: (Int) -> Unit,
    onMoodChanged: (Int) -> Unit,
    onSave: () -> Unit,
    viewModel: LogEntryViewModel = viewModel()
) {
    val useImperial by viewModel.useImperial.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Weight Section
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

                OutlinedTextField(
                    value = state.weight?.toString() ?: "",
                    onValueChange = { value ->
                        val weight = value.toDoubleOrNull()
                        onWeightChanged(weight)
                    },
                    label = { Text("Weight (kg)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("70.5") }
                )

                if (state.weight != null) {
                    Text(
                        text = "${state.weight} kg",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // Water Section
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

                Text(
                    text = if (useImperial) {
                        val waterOz = (state.water * 0.033814).roundToInt()
                        "$waterOz fl oz"
                    } else {
                        "${state.water} ml"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Slider(
                    value = state.water.toFloat(),
                    onValueChange = { value ->
                        onWaterChanged(value.toInt())
                    },
                    valueRange = 0f..3000f,
                    steps = 59, // 50ml steps
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (useImperial) "0 fl oz" else "0 ml",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        if (useImperial) {
                            val maxOz = (3000 * 0.033814).roundToInt()
                            "$maxOz fl oz"
                        } else {
                            "3000 ml"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Quick add buttons
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onWaterChanged(state.water + 250) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ Glass")
                    }

                    OutlinedButton(
                        onClick = { onWaterChanged(state.water + 500) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ Bottle")
                    }
                }
            }
        }

        // Mood Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "How are you feeling?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MoodOption(
                        emoji = "😞",
                        label = "Bad",
                        selected = state.mood == 1,
                        onClick = { onMoodChanged(1) }
                    )

                    MoodOption(
                        emoji = "😐",
                        label = "Okay",
                        selected = state.mood == 2,
                        onClick = { onMoodChanged(2) }
                    )

                    MoodOption(
                        emoji = "😊",
                        label = "Good",
                        selected = state.mood == 3,
                        onClick = { onMoodChanged(3) }
                    )

                    MoodOption(
                        emoji = "😄",
                        label = "Great",
                        selected = state.mood == 4,
                        onClick = { onMoodChanged(4) }
                    )

                    MoodOption(
                        emoji = "😍",
                        label = "Amazing",
                        selected = state.mood == 5,
                        onClick = { onMoodChanged(5) }
                    )
                }

                if (state.mood != null) {
                    Text(
                        text = "Selected: ${getMoodDescription(state.mood)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Save Button
        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = state.canSave
        ) {
            Text(
                text = "Save Entry",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (!state.canSave) {
            Text(
                text = "Add at least one measurement to save",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MoodOption(
    emoji: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp)
        ) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.headlineMedium,
                modifier = if (selected) Modifier else Modifier
            )
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SavingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator()
        Text(
            text = "Saving your entry...",
            style = MaterialTheme.typography.bodyLarge
        )
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
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "❌",
            style = MaterialTheme.typography.displayLarge
        )

        Text(
            text = "Failed to save",
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

private fun getMoodDescription(mood: Int): String {
    return when (mood) {
        1 -> "Feeling Bad"
        2 -> "Feeling Okay"
        3 -> "Feeling Good"
        4 -> "Feeling Great"
        5 -> "Feeling Amazing"
        else -> "Mood not set"
    }
}
