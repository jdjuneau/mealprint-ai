package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import com.coachie.app.data.model.DietaryPreference
import com.coachie.app.data.model.CookingMethod
import com.coachie.app.ui.components.CoachieCard as Card
import com.coachie.app.ui.components.CoachieCardDefaults as CardDefaults
import com.coachie.app.ui.theme.rememberCoachieGradient

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PreferencesEditScreen(
    userId: String,
    userProfile: UserProfile?,
    isLoading: Boolean,
    errorMessage: String? = null,
    onSaveProfile: (UserProfile, Map<String, Any?>) -> Unit,
    onCancel: () -> Unit
) {
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val scrollState = rememberScrollState()
    val repository = com.coachie.app.data.FirebaseRepository.getInstance()

    var dietaryPreference by rememberSaveable { mutableStateOf("balanced") }
    var useImperial by rememberSaveable { mutableStateOf(true) }
    var isLoadingGoals by remember { mutableStateOf(true) }
    var selectedCookingMethods by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    var cookingMethodsExpanded by remember { mutableStateOf(false) }

    // Load user goals to get useImperial preference
    LaunchedEffect(userId) {
        if (userId.isNotBlank()) {
            val goalsResult = repository.getUserGoals(userId)
            goalsResult.onSuccess { goals ->
                useImperial = goals?.get("useImperial") as? Boolean ?: true
                isLoadingGoals = false
            }.onFailure {
                isLoadingGoals = false
            }
        }
    }

    // Update field when profile data loads
    LaunchedEffect(userProfile) {
        if (userProfile != null) {
            dietaryPreference = userProfile.dietaryPreference ?: "balanced"
            selectedCookingMethods = userProfile.preferredCookingMethods?.toSet() ?: emptySet()
        }
    }

    var expanded by remember { mutableStateOf(false) }
    var expandedUnits by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Preferences") },
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
                    // Dietary Preferences
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
                                text = "Dietary Preferences",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = !expanded },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = DietaryPreference.fromId(dietaryPreference).title,
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
                                    DietaryPreference.values().forEach { preference ->
                                        DropdownMenuItem(
                                            text = { Text(preference.title) },
                                            onClick = {
                                                dietaryPreference = preference.id
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Measurement Units
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
                                text = "Measurement Units",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            ExposedDropdownMenuBox(
                                expanded = expandedUnits,
                                onExpandedChange = { expandedUnits = !expandedUnits },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = if (useImperial) "Imperial (lbs, ft, °F)" else "Metric (kg, m, °C)",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Units") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedUnits) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedUnits,
                                    onDismissRequest = { expandedUnits = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Imperial (lbs, ft, °F)") },
                                        onClick = {
                                            useImperial = true
                                            expandedUnits = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Metric (kg, m, °C)") },
                                        onClick = {
                                            useImperial = false
                                            expandedUnits = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Cooking Method Preferences (for Weekly Blueprint)
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Preferred Cooking Methods",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Select your favorite cooking methods for weekly blueprints. Leave empty for variety.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                                IconButton(onClick = { cookingMethodsExpanded = !cookingMethodsExpanded }) {
                                    Icon(
                                        imageVector = if (cookingMethodsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = if (cookingMethodsExpanded) "Collapse" else "Expand",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = cookingMethodsExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CookingMethod.values().forEach { method ->
                                        val isSelected = selectedCookingMethods.contains(method.id)
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                selectedCookingMethods = if (isSelected) {
                                                    selectedCookingMethods - method.id
                                                } else {
                                                    selectedCookingMethods + method.id
                                                }
                                            },
                                            label = { Text(method.displayName) },
                                            leadingIcon = if (isSelected) {
                                                {
                                                    Icon(
                                                        imageVector = Icons.Filled.CheckCircle,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            } else null,
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                    }
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
                                val updated = (userProfile ?: UserProfile()).copy(
                                    uid = userId,
                                    dietaryPreference = dietaryPreference,
                                    preferredCookingMethods = if (selectedCookingMethods.isEmpty()) null else selectedCookingMethods.toList()
                                )
                                // Save useImperial to user goals
                                val goalsUpdate = mapOf("useImperial" to useImperial)
                                onSaveProfile(updated, goalsUpdate)
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
