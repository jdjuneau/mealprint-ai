package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.data.model.UserProfile
import com.coachie.app.ui.components.CoachieCard as Card
import com.coachie.app.ui.components.CoachieCardDefaults as CardDefaults
import com.coachie.app.ui.theme.rememberCoachieGradient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsEditScreen(
    userId: String,
    userProfile: UserProfile?,
    userGoals: Map<String, Any?>?,
    isLoading: Boolean,
    errorMessage: String? = null,
    onSaveProfile: (UserProfile, Map<String, Any?>) -> Unit,
    onCancel: () -> Unit
) {
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val scrollState = rememberScrollState()

    var selectedGoal by rememberSaveable { mutableStateOf("lose_weight") }
    var weeklyWorkouts by rememberSaveable { mutableStateOf("4") }
    var dailySteps by rememberSaveable { mutableStateOf("10000") }
    var fitnessLevel by rememberSaveable { mutableStateOf("intermediate") }

    // Update fields when goals data loads
    LaunchedEffect(userGoals) {
        if (userGoals != null) {
            selectedGoal = userGoals["selectedGoal"] as? String ?: "lose_weight"
            weeklyWorkouts = (userGoals["weeklyWorkouts"] as? Long)?.toString() ?: "4"
            dailySteps = (userGoals["dailySteps"] as? Long)?.toString() ?: "10000"
            fitnessLevel = userGoals["fitnessLevel"] as? String ?: "intermediate"
        }
    }

    val goalOptions = listOf(
        "lose_weight" to "Lose Weight",
        "maintain_weight" to "Maintain Weight",
        "gain_weight" to "Gain Weight",
        "build_muscle" to "Build Muscle",
        "improve_fitness" to "Improve Fitness"
    )

    val fitnessLevelOptions = listOf(
        "beginner" to "Beginner",
        "intermediate" to "Intermediate",
        "advanced" to "Advanced"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Fitness Goals") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                errorMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onCancel) {
                                Text("Go Back")
                            }
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "Fitness Goals",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )

                                // Primary Goal
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Primary Goal",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    var goalExpanded by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(
                                        expanded = goalExpanded,
                                        onExpandedChange = { goalExpanded = it }
                                    ) {
                                        OutlinedTextField(
                                            value = goalOptions.find { it.first == selectedGoal }?.second ?: "Lose Weight",
                                            onValueChange = {},
                                            readOnly = true,
                                            trailingIcon = {
                                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = goalExpanded)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .menuAnchor()
                                        )
                                        ExposedDropdownMenu(
                                            expanded = goalExpanded,
                                            onDismissRequest = { goalExpanded = false }
                                        ) {
                                            goalOptions.forEach { option ->
                                                val (value, label) = option
                                                DropdownMenuItem(
                                                    text = { Text(label) },
                                                    onClick = {
                                                        selectedGoal = value
                                                        goalExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Weekly Workouts
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Weekly Workouts",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    OutlinedTextField(
                                        value = weeklyWorkouts,
                                        onValueChange = { weeklyWorkouts = it },
                                        label = { Text("Sessions per week") },
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                // Daily Steps Goal
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Daily Steps Goal",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    OutlinedTextField(
                                        value = dailySteps,
                                        onValueChange = { dailySteps = it },
                                        label = { Text("Steps per day") },
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                // Fitness Level
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Fitness Level",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    var fitnessExpanded by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(
                                        expanded = fitnessExpanded,
                                        onExpandedChange = { fitnessExpanded = it }
                                    ) {
                                        OutlinedTextField(
                                            value = fitnessLevelOptions.find { it.first == fitnessLevel }?.second ?: "Intermediate",
                                            onValueChange = {},
                                            readOnly = true,
                                            trailingIcon = {
                                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = fitnessExpanded)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .menuAnchor()
                                        )
                                        ExposedDropdownMenu(
                                            expanded = fitnessExpanded,
                                            onDismissRequest = { fitnessExpanded = false }
                                        ) {
                                            fitnessLevelOptions.forEach { option ->
                                                val (value, label) = option
                                                DropdownMenuItem(
                                                    text = { Text(label) },
                                                    onClick = {
                                                        fitnessLevel = value
                                                        fitnessExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Save/Cancel buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedButton(
                                onClick = onCancel,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text("Cancel")
                            }

                            Button(
                                onClick = {
                                    val updatedGoals: Map<String, Any?> = mapOf(
                                        "selectedGoal" to selectedGoal as Any?,
                                        "weeklyWorkouts" to (weeklyWorkouts.toLongOrNull() ?: 4L) as Any?,
                                        "dailySteps" to (dailySteps.toLongOrNull() ?: 10000L) as Any?,
                                        "fitnessLevel" to fitnessLevel as Any?
                                    )
                                    // Create a dummy profile update since goals are stored separately
                                    val dummyProfile = userProfile ?: UserProfile()
                                    onSaveProfile(dummyProfile, updatedGoals)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save Goals")
                            }
                        }
                    }
                }
            }
        }
    }
}
