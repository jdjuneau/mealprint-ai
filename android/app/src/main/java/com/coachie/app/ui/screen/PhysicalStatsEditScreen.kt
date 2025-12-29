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
import com.coachie.app.data.local.PreferencesManager
import androidx.compose.ui.platform.LocalContext
import com.coachie.app.ui.theme.rememberCoachieGradient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhysicalStatsEditScreen(
    userId: String,
    userProfile: UserProfile?,
    isLoading: Boolean,
    errorMessage: String? = null,
    onSaveProfile: (UserProfile, Map<String, Any?>) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    val scrollState = rememberScrollState()
    val gradientBackground = rememberCoachieGradient(endY = 1600f)

    // Get unit preferences
    val weightUnit = preferencesManager.weightUnit
    val heightUnit = preferencesManager.heightUnit
    val useImperial = weightUnit == "lbs" || heightUnit == "in" || heightUnit == "ft"
    
    // CRITICAL: Update fields when userProfile loads or changes
    var currentWeightInput by rememberSaveable { mutableStateOf("") }
    var goalWeightInput by rememberSaveable { mutableStateOf("") }
    var heightInput by rememberSaveable { mutableStateOf("") }
    var activityLevel by rememberSaveable { mutableStateOf("") }
    
    // Update fields when profile loads or changes
    LaunchedEffect(userProfile, useImperial) {
        currentWeightInput = if (useImperial) {
            ((userProfile?.currentWeight ?: 0.0) * 2.205).takeIf { it > 0 }?.let { String.format("%.1f", it) } ?: ""
        } else {
            (userProfile?.currentWeight ?: 0.0).takeIf { it > 0 }?.let { String.format("%.1f", it) } ?: ""
        }
        goalWeightInput = if (useImperial) {
            ((userProfile?.goalWeight ?: 0.0) * 2.205).takeIf { it > 0 }?.let { String.format("%.1f", it) } ?: ""
        } else {
            (userProfile?.goalWeight ?: 0.0).takeIf { it > 0 }?.let { String.format("%.1f", it) } ?: ""
        }
        heightInput = if (useImperial) {
            // Convert cm to inches
            ((userProfile?.heightCm ?: 0.0) / 2.54).takeIf { it > 0 }?.let { String.format("%.1f", it) } ?: ""
        } else {
            (userProfile?.heightCm ?: 0.0).takeIf { it > 0 }?.let { String.format("%.1f", it) } ?: ""
        }
        activityLevel = userProfile?.activityLevel ?: ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Physical Stats") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.Black,
                    actionIconContentColor = Color.Black,
                    navigationIconContentColor = Color.Black
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
                                text = "Physical Statistics",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            OutlinedTextField(
                                value = currentWeightInput,
                                onValueChange = { txt ->
                                    if (txt.isEmpty() || txt.matches(Regex("^\\d*\\.?\\d*$"))) {
                                        currentWeightInput = txt
                                    }
                                },
                                label = { Text("Current Weight (${if (useImperial) "lbs" else "kg"})") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text(if (useImperial) "150.5" else "68.3") }
                            )

                            OutlinedTextField(
                                value = goalWeightInput,
                                onValueChange = { txt ->
                                    if (txt.isEmpty() || txt.matches(Regex("^\\d*\\.?\\d*$"))) {
                                        goalWeightInput = txt
                                    }
                                },
                                label = { Text("Goal Weight (${if (useImperial) "lbs" else "kg"})") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text(if (useImperial) "145.0" else "65.8") }
                            )

                            OutlinedTextField(
                                value = heightInput,
                                onValueChange = { txt ->
                                    if (txt.isEmpty() || txt.matches(Regex("^\\d*\\.?\\d*$"))) {
                                        heightInput = txt
                                    }
                                },
                                label = { Text("Height (${if (useImperial) "inches" else "cm"})") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text(if (useImperial) "68.0" else "173.0") }
                            )

                            OutlinedTextField(
                                value = activityLevel,
                                onValueChange = { activityLevel = it },
                                label = { Text("Activity Level") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("e.g., sedentary, lightly active, moderately active, very active") }
                            )
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
                                // Convert inputs back to metric for storage
                                val currentWeightMetric = currentWeightInput.toDoubleOrNull()?.let {
                                    if (useImperial) it / 2.205 else it
                                } ?: (userProfile?.currentWeight ?: 0.0)
                                val goalWeightMetric = goalWeightInput.toDoubleOrNull()?.let {
                                    if (useImperial) it / 2.205 else it
                                } ?: (userProfile?.goalWeight ?: 0.0)
                                val heightMetric = heightInput.toDoubleOrNull()?.let {
                                    if (useImperial) it * 2.54 else it
                                } ?: (userProfile?.heightCm ?: 0.0)

                                val updated = (userProfile ?: UserProfile()).copy(
                                    uid = userId,
                                    currentWeight = currentWeightMetric,
                                    goalWeight = goalWeightMetric,
                                    heightCm = heightMetric,
                                    activityLevel = activityLevel.trim().lowercase()
                                )
                                onSaveProfile(updated, emptyMap())
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
