package com.coachie.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coachie.app.data.local.PreferencesManager
import com.coachie.app.service.MindfulnessReminderService
import com.coachie.app.viewmodel.MindfulnessSettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MindfulnessSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: MindfulnessSettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Initialize ViewModel
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    val reminderEnabled by viewModel.reminderEnabled.collectAsState()
    val reminderHour by viewModel.reminderHour.collectAsState()
    val reminderMinute by viewModel.reminderMinute.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mindfulness Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Daily Reminders Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Daily Reminders",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Daily Reminders",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Get gentle reminders to practice mindfulness",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = reminderEnabled,
                            onCheckedChange = { enabled ->
                                coroutineScope.launch {
                                    viewModel.setReminderEnabled(enabled)
                                }
                            }
                        )
                    }

                    if (reminderEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Reminder Time",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Hour picker
                            OutlinedTextField(
                                value = reminderHour.toString().padStart(2, '0'),
                                onValueChange = { },
                                label = { Text("Hour") },
                                modifier = Modifier.weight(1f),
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuBox(
                                        expanded = false,
                                        onExpandedChange = { /* TODO */ }
                                    ) {
                                        // Simple time picker for now
                                        Text("${reminderHour.toString().padStart(2, '0')}:${reminderMinute.toString().padStart(2, '0')}")
                                    }
                                }
                            )

                            Text(":", style = MaterialTheme.typography.headlineMedium)

                            // Minute picker
                            OutlinedTextField(
                                value = reminderMinute.toString().padStart(2, '0'),
                                onValueChange = { },
                                label = { Text("Minute") },
                                modifier = Modifier.weight(1f),
                                readOnly = true
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Quick time presets
                        Text(
                            text = "Quick Set:",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val presets = listOf(
                                "Morning" to Pair(8, 0),
                                "Afternoon" to Pair(14, 0),
                                "Evening" to Pair(19, 0)
                            )

                            presets.forEach { (label, time) ->
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            viewModel.setReminderTime(time.first, time.second)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    }
                }
            }

            // Session Preferences
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Session Preferences",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Default meditation duration
                    Text(
                        text = "Default Meditation Duration",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val durations = listOf(5, 10, 15, 20, 30)
                        durations.forEach { duration ->
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        viewModel.setDefaultDuration(duration)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("${duration}m")
                            }
                        }
                    }
                }
            }

            // Statistics & Progress
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Your Progress",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Placeholder for stats - would show meditation stats
                    Text(
                        text = "Meditation sessions completed this week: 3",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Text(
                        text = "Total meditation time: 45 minutes",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Text(
                        text = "Current streak: 5 days",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Test Reminder Button (for development)
            Button(
                onClick = {
                    val reminderService = MindfulnessReminderService(context)
                    reminderService.showMindfulnessReminder()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Test Reminder Notification")
            }
        }
    }
}
