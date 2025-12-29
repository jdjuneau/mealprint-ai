package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.SharePlatformDialog
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.viewmodel.HabitViewModel
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCreateHabit: () -> Unit = {},
    onNavigateToEditHabit: (String) -> Unit = {},
    onNavigateToSuggestions: () -> Unit = {},
    onNavigateToMeditation: (Int, String) -> Unit = { _, _ -> }, // duration in minutes, habitId
    onNavigateToJournal: (String) -> Unit = {}, // habitId
    onNavigateToWorkout: () -> Unit = {},
    onNavigateToWater: () -> Unit = {},
    onNavigateToSleep: () -> Unit = {},
    onStartTimer: (Int, String, String) -> Unit = { _, _, _ -> }, // duration in minutes, label, habitId
    viewModel: HabitViewModel = viewModel(),
    refreshTrigger: Long = 0L
) {
    // Helper function to handle habit editing
    val handleEditHabit: (com.coachie.app.data.model.Habit) -> Unit = { habit ->
        onNavigateToEditHabit(habit.id)
    }
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val uiState by viewModel.uiState.collectAsState()
    val habits by viewModel.habits.collectAsState()
    val context = LocalContext.current
    
    // Share dialog state
    var showShareDialog by remember { mutableStateOf(false) }
    var pendingShareData by remember { mutableStateOf<com.coachie.app.service.ShareImageData?>(null) }
    
    // Debug logging for habits
    LaunchedEffect(habits.size) {
        android.util.Log.d("HabitScreen", "Habits list updated: ${habits.size} habits")
        habits.forEach { habit ->
            android.util.Log.d("HabitScreen", "  - Habit: ${habit.title} (id: ${habit.id}, active: ${habit.isActive})")
        }
    }

    // Resolve current user id for save/complete actions
    val authViewModel: com.coachie.app.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val authState by authViewModel.authState.collectAsState()
    val currentUserId = (authState as? com.coachie.app.viewmodel.AuthState.Authenticated)?.user?.uid ?: ""
    
    // Ensure ViewModel is collecting habits when screen is visible
    // The Flow uses Eagerly so it should always be collecting, but we'll trigger a check
    androidx.compose.runtime.LaunchedEffect(currentUserId, Unit) {
        if (currentUserId.isNotEmpty()) {
            android.util.Log.d("HabitScreen", "Screen loaded, checking habits for userId: $currentUserId")
            // The habits Flow should automatically update, but log to verify
            kotlinx.coroutines.delay(1000) // Give Firestore a moment to sync
            android.util.Log.d("HabitScreen", "After delay, habits count: ${habits.size}")
        }
    }
    
    // Also observe refreshTrigger for when returning from habit creation
    androidx.compose.runtime.LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            android.util.Log.d("HabitScreen", "Returned from habit creation, refreshTrigger=$refreshTrigger")
            // Wait a moment for Firestore to propagate
            kotlinx.coroutines.delay(1000)
            android.util.Log.d("HabitScreen", "After refresh delay, habits count: ${habits.size}")
        }
    }
    
    // Schedule reminders for manual completion habits
    androidx.compose.runtime.LaunchedEffect(habits.size, currentUserId) {
        if (currentUserId.isNotEmpty() && habits.isNotEmpty()) {
            try {
                val habitReminderService = com.coachie.app.service.HabitReminderService(context)
                val manualHabits = habits.filter { 
                    com.coachie.app.util.HabitUtils.shouldScheduleReminders(it) 
                }
                if (manualHabits.isNotEmpty()) {
                    habitReminderService.scheduleRemindersForHabits(manualHabits)
                    android.util.Log.d("HabitScreen", "Scheduled reminders for ${manualHabits.size} manual completion habits")
                }
            } catch (e: Exception) {
                android.util.Log.e("HabitScreen", "Error scheduling habit reminders", e)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Habits & Behavior Change") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToSuggestions
                    ) {
                        Icon(Icons.Filled.Lightbulb, contentDescription = "Get Suggestions")
                    }
                    IconButton(
                        onClick = onNavigateToCreateHabit
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Habit")
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
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (habits.isEmpty()) {
                    // Empty state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "🚀 Start Your Habit Journey",
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Build lasting habits with personalized coaching and tracking",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // AI Suggestions Button (prominent for first-time users)
                        Button(
                            onClick = onNavigateToSuggestions,
                            modifier = Modifier.fillMaxWidth(0.9f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Filled.Lightbulb, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Get AI-Powered Suggestions", style = MaterialTheme.typography.titleMedium)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = onNavigateToCreateHabit,
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            Text("Create Custom Habit")
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                text = "Your Active Habits",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        items(habits) { habit ->
                            var showDeleteDialog by remember { mutableStateOf(false) }
                            
                            HabitCard(
                                habit = habit,
                                onShare = {
                                    pendingShareData = com.coachie.app.service.ShareImageData(
                                        type = com.coachie.app.service.ShareImageType.STREAK,
                                        title = habit.title,
                                        metric = "${habit.streakCount}",
                                        subtitle = "${habit.streakCount}-day streak",
                                        streakFlame = habit.streakCount
                                    )
                                    showShareDialog = true
                                },
                                onStart = { 
                                    // Contextual action based on habit category
                                    when (habit.category) {
                                        com.coachie.app.data.model.HabitCategory.MENTAL_HEALTH -> {
                                            // Meditation or mindfulness habits
                                            if (habit.title.lowercase().contains("meditation") || 
                                                habit.title.lowercase().contains("mindful")) {
                                                onNavigateToMeditation(habit.targetValue, habit.id)
                                            } else if (habit.title.lowercase().contains("journal")) {
                                                // Journal habit - navigate to journal with habit ID
                                                onNavigateToJournal(habit.id)
                                            } else {
                                                // Other mental health habits - use timer
                                                onStartTimer(habit.targetValue, habit.title, habit.id)
                                            }
                                        }
                                        com.coachie.app.data.model.HabitCategory.LEARNING -> {
                                            // Reading or learning habits - use timer
                                            onStartTimer(habit.targetValue, habit.title, habit.id)
                                        }
                                        com.coachie.app.data.model.HabitCategory.FITNESS -> {
                                            // Check if it's a stretching habit
                                            val titleLower = habit.title.lowercase()
                                            if (titleLower.contains("stretch") || titleLower.contains("stretching")) {
                                                // Stretching habit - use timer with stretching examples
                                                onStartTimer(habit.targetValue, habit.title, habit.id)
                                            } else {
                                                // Other fitness habits - navigate to workout log
                                                onNavigateToWorkout()
                                            }
                                        }
                                        com.coachie.app.data.model.HabitCategory.HEALTH -> {
                                            // Check if it's water or sleep related
                                            val titleLower = habit.title.lowercase()
                                            when {
                                                titleLower.contains("water") || titleLower.contains("hydrate") -> {
                                                    onNavigateToWater()
                                                }
                                                titleLower.contains("sleep") -> {
                                                    onNavigateToSleep()
                                                }
                                                else -> {
                                                    // Generic health habit - complete directly
                                                    val uid = currentUserId
                                                    if (uid.isNotBlank()) {
                                                        viewModel.completeHabit(context, uid, habit.id, habit.targetValue)
                                                    } else {
                                                        viewModel.completeHabit(context, habit.userId, habit.id, habit.targetValue)
                                                    }
                                                }
                                            }
                                        }
                                        com.coachie.app.data.model.HabitCategory.NUTRITION -> {
                                            // Nutrition habits are auto-tracked when meals are logged - no Start button needed
                                            // But allow manual completion if needed
                                            val uid = currentUserId
                                            if (uid.isNotBlank()) {
                                                viewModel.completeHabit(context, uid, habit.id, habit.targetValue)
                                            } else {
                                                viewModel.completeHabit(context, habit.userId, habit.id, habit.targetValue)
                                            }
                                        }
                                        else -> {
                                            // Generic completion for other categories
                                            val uid = currentUserId
                                            if (uid.isNotBlank()) {
                                                viewModel.completeHabit(context, uid, habit.id, habit.targetValue)
                                            } else {
                                                viewModel.completeHabit(context, habit.userId, habit.id, habit.targetValue)
                                            }
                                        }
                                    }
                                },
                                onEdit = { handleEditHabit(habit) },
                                onDelete = { showDeleteDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            // Delete confirmation dialog
                            if (showDeleteDialog) {
                                AlertDialog(
                                    onDismissRequest = { showDeleteDialog = false },
                                    title = { Text("Delete Habit?") },
                                    text = { Text("Are you sure you want to delete \"${habit.title}\"? This action cannot be undone.") },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                val uid = currentUserId
                                                if (uid.isNotBlank()) {
                                                    viewModel.deleteHabit(uid, habit.id)
                                                } else {
                                                    viewModel.deleteHabit(habit.userId, habit.id)
                                                }
                                                showDeleteDialog = false
                                            },
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Text("Delete")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDeleteDialog = false }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onNavigateToCreateHabit,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add New Habit")
                            }
                        }
                    }
                }
            }

            // Success/Error messages
            uiState.successMessage?.let { message ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(message)
                }
            }

            uiState.errorMessage?.let { message ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(message)
                }
            }
        }
    }
    
    // Share platform dialog
    if (showShareDialog) {
        SharePlatformDialog(
            onDismiss = { showShareDialog = false },
            onShareToPlatform = { platform ->
                showShareDialog = false
                pendingShareData?.let { data ->
                    val shareService = com.coachie.app.service.ShareService.getInstance(context)
                    shareService.generateAndShare(data, platform)
                }
                pendingShareData = null
            }
        )
    }
}

@Composable
private fun HabitCard(
    habit: com.coachie.app.data.model.Habit,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    CoachieCard(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onEdit)
                ) {
                    Text(
                        text = habit.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (habit.description.isNotEmpty()) {
                        Text(
                            text = habit.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Streak indicator with share button for 7+ day streaks and delete button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (habit.streakCount > 0) {
                        Text(
                            text = "🔥 ${habit.streakCount}",
                            style = MaterialTheme.typography.labelLarge
                        )
                        if (habit.streakCount >= 7 && onShare != null) {
                            IconButton(
                                onClick = onShare,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "Share streak",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    
                    // Delete button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete habit",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${habit.category.name} • ${habit.frequency.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Target: ${habit.targetValue} ${habit.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Determine if habit is auto-tracked or requires manual completion
                val isAutoTracked = com.coachie.app.util.HabitUtils.isHabitAutoTracked(habit)
                val trackingDescription = com.coachie.app.util.HabitUtils.getHabitTrackingDescription(habit)
                
                if (!isAutoTracked) {
                    // Manual completion habits - show "Tap to complete" button
                    OutlinedButton(
                        onClick = onStart,
                        modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(
                            text = trackingDescription,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    // Auto-tracked habits - show info text
                    Text(
                        text = trackingDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }
    }
}
