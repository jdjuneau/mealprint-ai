package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.data.model.*
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.viewmodel.HabitCreationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitCreationScreen(
    habitId: String? = null, // null for new habit, ID for editing
    onSave: (String) -> Unit,
    onBack: () -> Unit,
    onNavigateToSuggestions: (() -> Unit)? = null, // Optional navigation to suggestions
    habitCreationViewModel: HabitCreationViewModel = viewModel()
) {
    val uiState by habitCreationViewModel.uiState.collectAsState()
    val gradientBackground = rememberCoachieGradient(endY = 2000f)

    // Initialize for editing if habitId provided
    LaunchedEffect(habitId) {
        habitId?.let { habitCreationViewModel.initializeForEdit(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditMode) "Edit Habit" else "Create New Habit", color = Color.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.Black,
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // AI Suggestions Banner (only show for new habits)
                if (!uiState.isEditMode && habitId == null) {
                    CoachieCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CoachieCardDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "💡 Get AI-Powered Suggestions",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Based on your behavioral profile and goals",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = {
                                    onNavigateToSuggestions?.invoke() ?: run {
                                        // Fallback: could show a dialog or inline suggestions
                                    }
                                }
                            ) {
                                Text("View Suggestions")
                            }
                        }
                    }
                }

                // Basic Information
                CoachieCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "📝 Basic Information",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = uiState.name,
                            onValueChange = { habitCreationViewModel.updateName(it) },
                            label = { Text("Habit Name *") },
                            placeholder = { Text("e.g., Drink 8 glasses of water") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = uiState.description,
                            onValueChange = { habitCreationViewModel.updateDescription(it) },
                            label = { Text("Description") },
                            placeholder = { Text("Optional details about this habit...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )
                    }
                }

                // Category and Priority
                CoachieCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "🏷️ Category & Priority",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // Category Selection
                        Text(
                            text = "Category:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )

                        val categories = HabitCategory.values().toList()
                        categories.chunked(2).forEach { rowCategories ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowCategories.forEach { category ->
                                    val isSelected = uiState.category == category
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { habitCreationViewModel.updateCategory(category) },
                                        label = { 
                                            Text(
                                                habitCreationViewModel.getCategoryDisplayName(category),
                                                color = Color.Black
                                            ) 
                                        },
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

                        // Priority Selection
                        Text(
                            text = "Priority:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )

                        val priorities = HabitPriority.values()
                        priorities.forEach { priority ->
                            val isSelected = uiState.priority == priority
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { habitCreationViewModel.updatePriority(priority) }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = habitCreationViewModel.getPriorityDisplayName(priority),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = habitCreationViewModel.getPriorityColor(priority)
                                    )
                                }
                            }
                            if (priorities.last() != priority) {
                                Divider()
                            }
                        }
                    }
                }

                // Frequency and Target
                CoachieCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "⏰ Frequency & Target",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // Frequency Selection
                        Text(
                            text = "How often do you want to do this?",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )

                        val frequencies = HabitFrequency.values()
                        frequencies.forEach { frequency ->
                            val isSelected = uiState.frequency == frequency
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { habitCreationViewModel.updateFrequency(frequency) }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = habitCreationViewModel.getFrequencyDisplayName(frequency),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (frequencies.last() != frequency) {
                                Divider()
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Target Value and Unit
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = uiState.targetValue.toString(),
                                onValueChange = {
                                    it.toIntOrNull()?.let { value ->
                                        if (value > 0) habitCreationViewModel.updateTargetValue(value)
                                    }
                                },
                                label = { Text("Target *") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = uiState.unit,
                                onValueChange = { habitCreationViewModel.updateUnit(it) },
                                label = { Text("Unit") },
                                placeholder = { Text("e.g., glasses, minutes") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                }

                // Reminders and Settings
                CoachieCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "🔔 Reminders & Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // Reminder Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Enable reminders",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Switch(
                                checked = uiState.reminderEnabled,
                                onCheckedChange = { habitCreationViewModel.updateReminderEnabled(it) }
                            )
                        }

                        // Reminder Time
                        if (uiState.reminderEnabled) {
                            OutlinedTextField(
                                value = uiState.reminderTime,
                                onValueChange = { habitCreationViewModel.updateReminderTime(it) },
                                label = { Text("Reminder Time") },
                                placeholder = { Text("HH:MM") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }

                        // Active Status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Habit is active",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Switch(
                                checked = uiState.isActive,
                                onCheckedChange = { habitCreationViewModel.updateIsActive(it) }
                            )
                        }
                    }
                }

                // Notes
                CoachieCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "📝 Additional Notes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = uiState.notes,
                            onValueChange = { habitCreationViewModel.updateNotes(it) },
                            label = { Text("Notes") },
                            placeholder = { Text("Any additional context or motivation...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6
                        )
                    }
                }

                // Error message
                uiState.error?.let { error ->
                    Card(
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

                Spacer(modifier = Modifier.height(32.dp))
            }

            // Save Button (floating at bottom)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Button(
                    onClick = {
                        habitCreationViewModel.saveHabit { habitId ->
                            onSave(habitId)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading && uiState.name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            text = if (uiState.isEditMode) "Update Habit" else "Create Habit",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}
