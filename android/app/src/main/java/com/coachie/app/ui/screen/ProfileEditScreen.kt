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
import com.coachie.app.data.model.UserProfile
import com.coachie.app.ui.components.CoachieCard as Card
import com.coachie.app.ui.components.CoachieCardDefaults as CardDefaults
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                if (isLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Loading profile...",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                } else {
                    val useImperialPref = ((userGoals?.get("useImperial") as? Boolean) == true)

                    var name by rememberSaveable { mutableStateOf(userProfile?.name ?: "") }
                    var gender by rememberSaveable { mutableStateOf(userProfile?.gender ?: "") }
                    var age by rememberSaveable { mutableStateOf((userProfile?.age ?: 30).toString()) }
                    // Inputs adapt to unit preference; convert existing metric to display units
                    var currentWeightInput by rememberSaveable {
                        mutableStateOf(
                            if (useImperialPref) ((userProfile?.currentWeight ?: 0.0) * 2.205).takeIf { it > 0 }?.let { String.format("%.1f", it) } ?: ""
                            else (userProfile?.currentWeight ?: 0.0).takeIf { it > 0 }?.let { String.format("%.1f", it) } ?: ""
                        )
                    }
                    var goalWeightInput by rememberSaveable {
                        mutableStateOf(
                            if (useImperialPref) ((userProfile?.goalWeight ?: 0.0) * 2.205).takeIf { it > 0 }?.let { String.format("%.1f", it) } ?: ""
                            else (userProfile?.goalWeight ?: 0.0).takeIf { it > 0 }?.let { String.format("%.1f", it) } ?: ""
                        )
                    }
                    var heightInput by rememberSaveable {
                        mutableStateOf(
                            if (useImperialPref) ((userProfile?.heightCm ?: 0.0) / 2.54).takeIf { it > 0 }?.let { String.format("%.1f", it) } ?: ""
                            else (userProfile?.heightCm ?: 0.0).takeIf { it > 0 }?.let { String.format("%.1f", it) } ?: ""
                        )
                    }
                    var activityLevel by rememberSaveable { mutableStateOf(userProfile?.activityLevel ?: "") }
                    var dietaryPreference by rememberSaveable { mutableStateOf(userProfile?.dietaryPreference ?: "balanced") }
                    var nudgesEnabled by rememberSaveable { mutableStateOf(userProfile?.nudgesEnabled ?: true) }
                    var menstrualCycleEnabled by rememberSaveable { mutableStateOf(userProfile?.menstrualCycleEnabled ?: false) }
                    var averageCycleLength by rememberSaveable { mutableStateOf((userProfile?.averageCycleLength ?: 28).toString()) }
                    var averagePeriodLength by rememberSaveable { mutableStateOf((userProfile?.averagePeriodLength ?: 5).toString()) }
                    var lastPeriodStart by rememberSaveable {
                        mutableStateOf(userProfile?.lastPeriodStart?.let {
                            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(it))
                        } ?: "")
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.colors()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = gender,
                                onValueChange = { gender = it },
                                label = { Text("Gender") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = age,
                                onValueChange = { age = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Age") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = currentWeightInput,
                                onValueChange = { txt ->
                                    val s = txt.filter { ch -> ch.isDigit() || ch == '.' }
                                    if (s.count { it == '.' } <= 1) currentWeightInput = s
                                },
                                label = { Text(if (useImperialPref) "Current Weight (lbs)" else "Current Weight (kg)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = goalWeightInput,
                                onValueChange = { txt ->
                                    val s = txt.filter { ch -> ch.isDigit() || ch == '.' }
                                    if (s.count { it == '.' } <= 1) goalWeightInput = s
                                },
                                label = { Text(if (useImperialPref) "Goal Weight (lbs)" else "Goal Weight (kg)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = heightInput,
                                onValueChange = { txt ->
                                    val s = txt.filter { ch -> ch.isDigit() || ch == '.' }
                                    if (s.count { it == '.' } <= 1) heightInput = s
                                },
                                label = { Text(if (useImperialPref) "Height (inches)" else "Height (cm)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = activityLevel,
                                onValueChange = { activityLevel = it },
                                label = { Text("Activity Level (sedentary, lightly active, etc.)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            // Dietary Preference Dropdown
                            var expanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = !expanded },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = when (dietaryPreference) {
                                        "vegetarian" -> "Vegetarian"
                                        "vegan" -> "Vegan"
                                        "keto" -> "Keto"
                                        "paleo" -> "Paleo"
                                        "mediterranean" -> "Mediterranean"
                                        else -> "Balanced"
                                    },
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Dietary Preference") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Balanced") },
                                        onClick = {
                                            dietaryPreference = "balanced"
                                            expanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Vegetarian") },
                                        onClick = {
                                            dietaryPreference = "vegetarian"
                                            expanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Vegan") },
                                        onClick = {
                                            dietaryPreference = "vegan"
                                            expanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Keto") },
                                        onClick = {
                                            dietaryPreference = "keto"
                                            expanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Paleo") },
                                        onClick = {
                                            dietaryPreference = "paleo"
                                            expanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Mediterranean") },
                                        onClick = {
                                            dietaryPreference = "mediterranean"
                                            expanded = false
                                        }
                                    )
                                }
                            }

                            // Nudges Toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Enable Nudges", style = MaterialTheme.typography.bodyLarge)
                                Switch(
                                    checked = nudgesEnabled,
                                    onCheckedChange = { nudgesEnabled = it }
                                )
                            }

                            // Menstrual Cycle Settings (only show for females or in debug)
                            if (gender.lowercase() == "female" || BuildConfig.DEBUG) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Enable Menstrual Tracking", style = MaterialTheme.typography.bodyLarge)
                                    Switch(
                                        checked = menstrualCycleEnabled,
                                        onCheckedChange = { menstrualCycleEnabled = it }
                                    )
                                }

                                if (menstrualCycleEnabled) {
                                    OutlinedTextField(
                                        value = averageCycleLength,
                                        onValueChange = { txt ->
                                            averageCycleLength = txt.filter { ch -> ch.isDigit() }
                                        },
                                        label = { Text("Average Cycle Length (days)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = averagePeriodLength,
                                        onValueChange = { txt ->
                                            averagePeriodLength = txt.filter { ch -> ch.isDigit() }
                                        },
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
                                        placeholder = { Text("e.g. 2024-11-16") }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                // Convert inputs back to metric for storage
                                val currentWeightMetric = currentWeightInput.toDoubleOrNull()?.let { if (useImperialPref) it / 2.205 else it } ?: (userProfile?.currentWeight ?: 0.0)
                                val goalWeightMetric = goalWeightInput.toDoubleOrNull()?.let { if (useImperialPref) it / 2.205 else it } ?: (userProfile?.goalWeight ?: 0.0)
                                val heightMetric = heightInput.toDoubleOrNull()?.let { if (useImperialPref) it * 2.54 else it } ?: (userProfile?.heightCm ?: 0.0)
                                val lastPeriodStartMillis = if (lastPeriodStart.isNotBlank()) {
                                    try {
                                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(lastPeriodStart)?.time
                                    } catch (e: Exception) {
                                        null
                                    }
                                } else null

                                val updated = (userProfile ?: UserProfile()).copy(
                                    uid = userId,
                                    name = name.trim(),
                                    gender = gender.trim(),
                                    age = age.toIntOrNull() ?: (userProfile?.age ?: 30),
                                    currentWeight = currentWeightMetric,
                                    goalWeight = goalWeightMetric,
                                    heightCm = heightMetric,
                                    activityLevel = activityLevel.trim().lowercase(),
                                    dietaryPreference = dietaryPreference,
                                    nudgesEnabled = nudgesEnabled,
                                    menstrualCycleEnabled = menstrualCycleEnabled,
                                    averageCycleLength = averageCycleLength.toIntOrNull() ?: 28,
                                    averagePeriodLength = averagePeriodLength.toIntOrNull() ?: 5,
                                    lastPeriodStart = lastPeriodStartMillis
                                )
                                onSaveProfile(updated, emptyMap())
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save")
                        }
                    }

                    errorMessage?.let { error ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.colors(
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
}