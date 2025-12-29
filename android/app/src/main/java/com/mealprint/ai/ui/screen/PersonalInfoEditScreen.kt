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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mealprint.ai.data.model.UserProfile
import com.mealprint.ai.ui.components.CoachieCard as Card
import com.mealprint.ai.ui.components.CoachieCardDefaults as CardDefaults
import com.mealprint.ai.ui.theme.rememberCoachieGradient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalInfoEditScreen(
    userId: String,
    userProfile: UserProfile?,
    userGoals: Map<String, Any?>? = null,
    isLoading: Boolean,
    errorMessage: String? = null,
    onSaveProfile: (UserProfile, Map<String, Any?>) -> Unit,
    onCancel: () -> Unit
) {
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val scrollState = rememberScrollState()

    // Get useImperial preference from userGoals
    val useImperial = (userGoals?.get("useImperial") as? Boolean) ?: true

    var name by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("30") }
    var currentWeight by rememberSaveable { mutableStateOf("") }
    var goalWeight by rememberSaveable { mutableStateOf("") }
    var heightInput by rememberSaveable { mutableStateOf("") }
    var activityLevel by rememberSaveable { mutableStateOf("moderately active") }
    var menstrualCycleEnabled by rememberSaveable { mutableStateOf(false) }
    var averageCycleLength by rememberSaveable { mutableStateOf("28") }
    var averagePeriodLength by rememberSaveable { mutableStateOf("5") }
    var lastPeriodStart by rememberSaveable { mutableStateOf("") }

    // Update fields when profile data loads
    LaunchedEffect(userProfile, userGoals) {
        if (userProfile != null) {
            name = userProfile.name ?: ""
            gender = userProfile.gender ?: ""
            age = (userProfile.age ?: 30).toString()
            
            // Convert weight/height to display units based on preference
            currentWeight = if (useImperial && userProfile.currentWeight != null && userProfile.currentWeight > 0) {
                String.format("%.1f", userProfile.currentWeight * 2.205)
            } else {
                (userProfile.currentWeight ?: 70.0).toString()
            }
            
            goalWeight = if (useImperial && userProfile.goalWeight != null && userProfile.goalWeight > 0) {
                String.format("%.1f", userProfile.goalWeight * 2.205)
            } else {
                (userProfile.goalWeight ?: 65.0).toString()
            }
            
            heightInput = if (useImperial && userProfile.heightCm != null && userProfile.heightCm > 0) {
                // Convert cm to inches
                String.format("%.1f", userProfile.heightCm / 2.54)
            } else {
                (userProfile.heightCm ?: 170.0).toString()
            }
            
            activityLevel = userProfile.activityLevel ?: "moderately active"
            menstrualCycleEnabled = userProfile.menstrualCycleEnabled ?: false
            averageCycleLength = (userProfile.averageCycleLength ?: 28).toString()
            averagePeriodLength = (userProfile.averagePeriodLength ?: 5).toString()
            lastPeriodStart = userProfile.lastPeriodStart?.let {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(it))
            } ?: ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Personal Information") },
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
            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Saving...",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.colors()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Personal Information",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )

                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Full Name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = gender,
                                onValueChange = { gender = it },
                                label = { Text("Gender") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("e.g., Male, Female, Other") }
                            )

                            OutlinedTextField(
                                value = age,
                                onValueChange = { age = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Age") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.colors()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Physical Stats",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )

                            OutlinedTextField(
                                value = currentWeight,
                                onValueChange = { currentWeight = it },
                                label = { Text(if (useImperial) "Current Weight (lbs)" else "Current Weight (kg)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = goalWeight,
                                onValueChange = { goalWeight = it },
                                label = { Text(if (useImperial) "Goal Weight (lbs)" else "Goal Weight (kg)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = heightInput,
                                onValueChange = { heightInput = it },
                                label = { Text(if (useImperial) "Height (inches)" else "Height (cm)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            var activityExpanded by remember { mutableStateOf(false) }
                            val activityOptions = listOf(
                                "sedentary" to "Sedentary (little/no exercise)",
                                "lightly active" to "Lightly Active (1-3 days/week)",
                                "moderately active" to "Moderately Active (3-5 days/week)",
                                "very active" to "Very Active (6-7 days/week)",
                                "extremely active" to "Extremely Active (physical job)"
                            )

                            ExposedDropdownMenuBox(
                                expanded = activityExpanded,
                                onExpandedChange = { activityExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = activityOptions.find { it.first == activityLevel }?.second ?: "Moderately Active",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Activity Level") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = activityExpanded)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = activityExpanded,
                                    onDismissRequest = { activityExpanded = false }
                                ) {
                                    activityOptions.forEach { (value, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                activityLevel = value
                                                activityExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Menstrual cycle section (only show for females)
                    if (gender.lowercase() == "female" || gender.lowercase() == "woman" || gender.lowercase() == "f") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.colors()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "Menstrual Cycle Tracking",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Enable menstrual cycle tracking",
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = menstrualCycleEnabled,
                                        onCheckedChange = { menstrualCycleEnabled = it }
                                    )
                                }

                                if (menstrualCycleEnabled) {
                                    OutlinedTextField(
                                        value = averageCycleLength,
                                        onValueChange = { averageCycleLength = it.filter { ch -> ch.isDigit() } },
                                        label = { Text("Average Cycle Length (days)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    OutlinedTextField(
                                        value = averagePeriodLength,
                                        onValueChange = { averagePeriodLength = it.filter { ch -> ch.isDigit() } },
                                        label = { Text("Average Period Length (days)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    OutlinedTextField(
                                        value = lastPeriodStart,
                                        onValueChange = { lastPeriodStart = it },
                                        label = { Text("Last Period Start Date (YYYY-MM-DD)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        placeholder = { Text("e.g., 2025-11-15") }
                                    )
                                }
                            }
                        }
                    }

                    errorMessage?.let { error ->
                        Card(
                            colors = CardDefaults.colors(
                                containerColor = Color(0xFFF44336).copy(alpha = 0.1f)
                            )
                        ) {
                            Text(
                                text = error,
                                color = Color(0xFFF44336),
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                // Convert weight/height back to metric if imperial was used
                                val currentWeightKg = if (useImperial) {
                                    (currentWeight.toDoubleOrNull() ?: 0.0) * 0.453592 // lbs to kg
                                } else {
                                    currentWeight.toDoubleOrNull() ?: (userProfile?.currentWeight ?: 70.0)
                                }
                                
                                val goalWeightKg = if (useImperial) {
                                    (goalWeight.toDoubleOrNull() ?: 0.0) * 0.453592 // lbs to kg
                                } else {
                                    goalWeight.toDoubleOrNull() ?: (userProfile?.goalWeight ?: 65.0)
                                }
                                
                                // Calculate heightCm - ensure we don't save 0.0 if input is invalid
                                val heightCmValue = if (heightInput.isNotBlank()) {
                                    if (useImperial) {
                                        val inches = heightInput.toDoubleOrNull()
                                        if (inches != null && inches > 0) {
                                            inches * 2.54 // inches to cm
                                        } else {
                                            // Invalid input, preserve existing
                                            userProfile?.heightCm ?: 170.0
                                        }
                                    } else {
                                        val cm = heightInput.toDoubleOrNull()
                                        if (cm != null && cm > 0) {
                                            cm
                                        } else {
                                            // Invalid input, preserve existing
                                            userProfile?.heightCm ?: 170.0
                                        }
                                    }
                                } else {
                                    // Empty input, preserve existing height
                                    userProfile?.heightCm ?: 170.0
                                }
                                
                                android.util.Log.d("PersonalInfoEdit", "Saving height: input='$heightInput', useImperial=$useImperial, existingHeight=${userProfile?.heightCm}, calculatedHeightCm=$heightCmValue")
                                
                                val updated = (userProfile ?: UserProfile()).copy(
                                    uid = userId,
                                    name = name.trim(),
                                    gender = gender.trim(),
                                    age = age.toIntOrNull() ?: (userProfile?.age ?: 30),
                                    currentWeight = currentWeightKg,
                                    goalWeight = goalWeightKg,
                                    heightCm = heightCmValue,
                                    activityLevel = activityLevel,
                                    menstrualCycleEnabled = menstrualCycleEnabled,
                                    averageCycleLength = averageCycleLength.toIntOrNull() ?: (userProfile?.averageCycleLength ?: 28),
                                    averagePeriodLength = averagePeriodLength.toIntOrNull() ?: (userProfile?.averagePeriodLength ?: 5),
                                    lastPeriodStart = if (lastPeriodStart.isNotBlank()) {
                                        try {
                                            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                                .parse(lastPeriodStart)?.time
                                        } catch (e: Exception) {
                                            userProfile?.lastPeriodStart
                                        }
                                    } else {
                                        userProfile?.lastPeriodStart
                                    }
                                )
                                onSaveProfile(updated, emptyMap())
                            },
                            modifier = Modifier.weight(1f),
                            enabled = name.isNotBlank()
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
