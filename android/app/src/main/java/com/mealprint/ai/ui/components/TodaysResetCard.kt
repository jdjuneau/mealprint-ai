package com.coachie.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mealprint.ai.data.model.HealthLog
import com.mealprint.ai.ui.theme.Primary40
import com.mealprint.ai.ui.theme.Tertiary40
import com.mealprint.ai.ui.theme.Secondary40
import com.mealprint.ai.ui.theme.SemanticColorCategory
import com.mealprint.ai.ui.theme.getSemanticColorPrimary
import com.mealprint.ai.viewmodel.TodaysResetViewModel
import com.mealprint.ai.ui.components.CoachieCardDefaults
import com.mealprint.ai.ui.components.Suggestion
import com.mealprint.ai.ui.components.TimeOfDay
import com.mealprint.ai.ui.components.SuggestionType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Sealed class to represent either a reminder or a suggestion
sealed class FocusItem {
    data class ReminderItem(val reminder: com.coachie.app.data.model.Reminder) : FocusItem()
    data class SuggestionItem(val suggestion: Suggestion) : FocusItem()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodaysResetCard(
    userId: String,
    modifier: Modifier = Modifier,
    refreshTrigger: String = "", // Trigger refresh when dashboard data changes
    contextualSuggestions: List<Suggestion> = emptyList(), // Contextual suggestions to display
    onSuggestionAction: (Suggestion) -> Unit = {}, // Handle suggestion action
    onSuggestionDismiss: (String) -> Unit = {}, // Handle suggestion dismiss
    onNavigateToMealLog: () -> Unit = {},
    onNavigateToWaterLog: () -> Unit = {},
    onNavigateToWeightLog: () -> Unit = {},
    onNavigateToSleepLog: () -> Unit = {},
    onNavigateToWorkoutLog: () -> Unit = {},
    onNavigateToSupplementLog: () -> Unit = {},
    onNavigateToJournal: () -> Unit = {},
    onNavigateToMeditation: () -> Unit = {},
    onNavigateToMeditationWithHabit: (Int, String) -> Unit = { _, _ -> }, // duration (minutes), habitId
    onNavigateToHabits: () -> Unit = {},
    onNavigateToHealthTracking: () -> Unit = {},
    onNavigateToWellness: () -> Unit = {},
    onNavigateToBreathingExercises: () -> Unit = {}, // Navigate to breathing exercises screen
    onNavigateToHabitTimer: (String, String, Int) -> Unit = { _, _, _ -> } // habitId, habitTitle, durationSeconds
) {
    val context = LocalContext.current
    val repository = remember { com.coachie.app.data.FirebaseRepository.getInstance() }
    val habitRepository = remember { com.coachie.app.data.HabitRepository.getInstance() }
    val taskGenerator = remember { com.coachie.app.service.TodaysFocusTaskGenerator(repository, habitRepository) }
    
    // Task states
    var tasks by remember { mutableStateOf<List<com.coachie.app.data.model.TodaysFocusTask>>(emptyList()) }
    var isLoadingTasks by remember { mutableStateOf(true) }
    var currentTaskIndex by remember { mutableStateOf(0) }
    val today = remember { java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE) }
    
    // Generate tasks if needed and load tasks
    LaunchedEffect(userId, today, refreshTrigger) {
        isLoadingTasks = true
        try {
            // Generate tasks if needed (only generates if they don't exist for today)
            taskGenerator.generateTodaysTasksIfNeeded(userId)
            
            // Small delay to allow Firestore to propagate
            kotlinx.coroutines.delay(1000)
            
            // Load incomplete tasks
            val tasksResult = repository.getTodaysFocusTasks(userId, today)
            tasks = tasksResult.getOrNull() ?: emptyList()
            
            // Reset index if current task is no longer in list
            if (currentTaskIndex >= tasks.size && tasks.isNotEmpty()) {
                currentTaskIndex = 0
            }
            
            android.util.Log.d("TodaysResetCard", "Loaded ${tasks.size} incomplete tasks for today")
        } catch (e: Exception) {
            android.util.Log.e("TodaysResetCard", "Error loading tasks", e)
        } finally {
            isLoadingTasks = false
        }
    }

    CoachieCard(
        modifier = modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today's Focus",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                // Show task count
                if (tasks.isNotEmpty()) {
                    Text(
                        text = "${tasks.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .background(
                                color = Primary40.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Reset index if current task is no longer in list
            LaunchedEffect(tasks.size) {
                if (currentTaskIndex >= tasks.size && tasks.isNotEmpty()) {
                    currentTaskIndex = 0
                } else if (tasks.isEmpty()) {
                    currentTaskIndex = 0
                }
            }
            
            val currentTask = tasks.getOrNull(currentTaskIndex)
            val canGoNext = currentTaskIndex < tasks.size - 1
            val canGoPrevious = currentTaskIndex > 0

            when {
                isLoadingTasks -> {
                    LoadingState(isGenerating = false)
                }
                tasks.isEmpty() -> {
                    EmptyRemindersState() // All tasks completed!
                }
                currentTask == null -> {
                    EmptyRemindersState()
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(canGoNext, canGoPrevious) {
                                var totalDragAmount = 0f
                                var hasNavigated = false
                                
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        totalDragAmount = 0f
                                        hasNavigated = false
                                    },
                                    onDragEnd = {
                                        if (!hasNavigated && kotlin.math.abs(totalDragAmount) > 100) {
                                            if (totalDragAmount > 0 && canGoPrevious) {
                                                currentTaskIndex = (currentTaskIndex - 1).coerceAtLeast(0)
                                            } else if (totalDragAmount < 0 && canGoNext) {
                                                currentTaskIndex = (currentTaskIndex + 1).coerceAtMost(tasks.size - 1)
                                            }
                                        }
                                        totalDragAmount = 0f
                                        hasNavigated = false
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        totalDragAmount += dragAmount
                                        if (!hasNavigated && kotlin.math.abs(totalDragAmount) > 150) {
                                            if (totalDragAmount > 0 && canGoPrevious) {
                                                currentTaskIndex = (currentTaskIndex - 1).coerceAtLeast(0)
                                                hasNavigated = true
                                            } else if (totalDragAmount < 0 && canGoNext) {
                                                currentTaskIndex = (currentTaskIndex + 1).coerceAtMost(tasks.size - 1)
                                                hasNavigated = true
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        TaskContent(
                            task = currentTask,
                            tasksCount = tasks.size,
                            currentIndex = currentTaskIndex + 1,
                            canGoNext = canGoNext,
                            canGoPrevious = canGoPrevious,
                            onNext = { currentTaskIndex = (currentTaskIndex + 1).coerceAtMost(tasks.size - 1) },
                            onPrevious = { currentTaskIndex = (currentTaskIndex - 1).coerceAtLeast(0) },
                            onAction = { actionType: com.coachie.app.data.model.ReminderActionType, actionData: Map<String, Any> ->
                                when (actionType) {
                                    com.coachie.app.data.model.ReminderActionType.LOG_MEAL -> onNavigateToMealLog()
                                    com.coachie.app.data.model.ReminderActionType.LOG_WATER -> onNavigateToWaterLog()
                                    com.coachie.app.data.model.ReminderActionType.LOG_WEIGHT -> onNavigateToWeightLog()
                                    com.coachie.app.data.model.ReminderActionType.LOG_SLEEP -> onNavigateToSleepLog()
                                    com.coachie.app.data.model.ReminderActionType.LOG_WORKOUT -> onNavigateToWorkoutLog()
                                    com.coachie.app.data.model.ReminderActionType.LOG_SUPPLEMENT -> onNavigateToSupplementLog()
                                    com.coachie.app.data.model.ReminderActionType.START_JOURNAL -> onNavigateToJournal()
                                    com.coachie.app.data.model.ReminderActionType.START_MEDITATION -> onNavigateToMeditation()
                                    com.coachie.app.data.model.ReminderActionType.COMPLETE_HABIT -> {
                                        val habitId = actionData["habitId"] as? String ?: ""
                                        val habitTitle = actionData["habitTitle"] as? String ?: ""
                                        
                                        if (habitId.isNotBlank() && habitTitle.isNotBlank()) {
                                            val habitTitleLower = habitTitle.lowercase()
                                            val taskDescription = currentTask.description.lowercase()
                                            
                                            val isBreathingHabit = habitTitleLower.contains("breathing") || 
                                                                  habitTitleLower.contains("breath")
                                            
                                            if (isBreathingHabit) {
                                                onNavigateToBreathingExercises()
                                            } else {
                                                val isWaterHabit = habitTitleLower.contains("water") || 
                                                                  habitTitleLower.contains("drink") ||
                                                                  habitTitleLower.contains("hydrate")
                                                
                                                if (isWaterHabit) {
                                                    onNavigateToWaterLog()
                                                } else {
                                                    val isSleepHabit = habitTitleLower.contains("sleep") || 
                                                                      habitTitleLower.contains("bedtime")
                                                    
                                                    if (isSleepHabit) {
                                                        onNavigateToSleepLog()
                                                    } else {
                                                        val isWorkoutHabit = habitTitleLower.contains("workout") || 
                                                                            habitTitleLower.contains("exercise") ||
                                                                            habitTitleLower.contains("gym")
                                                        
                                                        if (isWorkoutHabit) {
                                                            onNavigateToWorkoutLog()
                                                        } else {
                                                            val isMeditationHabit = habitTitleLower.contains("meditation") || 
                                                                                   habitTitleLower.contains("mindful")
                                                            
                                                            if (isMeditationHabit) {
                                                                val minutePattern = Regex("""(\d+)\s*[-]?\s*min(?:ute)?s?""", RegexOption.IGNORE_CASE)
                                                                val minuteMatch = minutePattern.find(habitTitle)
                                                                val durationMinutes = minuteMatch?.groupValues?.get(1)?.toIntOrNull() ?: 10
                                                                onNavigateToMeditationWithHabit(durationMinutes, habitId)
                                                            } else {
                                                                // Check for hour patterns first (in both title and description)
                                                                val hourPattern = Regex("""(\d+)\s*[-]?\s*h(?:our)?s?""", RegexOption.IGNORE_CASE)
                                                                val hourMatchTitle = hourPattern.find(habitTitle)
                                                                val hourMatchDesc = hourPattern.find(taskDescription)
                                                                val hourMatch = hourMatchTitle ?: hourMatchDesc
                                                                
                                                                // Calculate duration in SECONDS (as expected by onNavigateToHabitTimer)
                                                                val durationSeconds = if (hourMatch != null) {
                                                                    // Found hour pattern - convert to seconds
                                                                    val hours = hourMatch.groupValues[1].toIntOrNull() ?: 1
                                                                    hours * 60 * 60 // Convert hours to seconds (1 hour = 3600 seconds)
                                                                } else {
                                                                    // Check for seconds
                                                                    val secondPattern = Regex("""(\d+)\s*[-]?\s*second""", RegexOption.IGNORE_CASE)
                                                                    val secondMatch = secondPattern.find(habitTitle) ?: secondPattern.find(taskDescription)
                                                                    if (secondMatch != null) {
                                                                        secondMatch.groupValues[1].toIntOrNull() ?: 10
                                                                    } else {
                                                                        // Check for minutes
                                                                        val minutePattern = Regex("""(\d+)\s*[-]?\s*min(?:ute)?s?""", RegexOption.IGNORE_CASE)
                                                                        val minuteMatch = minutePattern.find(habitTitle) ?: minutePattern.find(taskDescription)
                                                                        if (minuteMatch != null) {
                                                                            minuteMatch.groupValues[1].toIntOrNull() ?: 5
                                                                        } else {
                                                                            5
                                                                        } * 60 // Convert minutes to seconds
                                                                    }
                                                                }
                                                                onNavigateToHabitTimer(habitId, habitTitle, durationSeconds)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            onNavigateToHabits()
                                        }
                                    }
                                    com.coachie.app.data.model.ReminderActionType.START_MINDFULNESS -> {
                                        onNavigateToBreathingExercises()
                                    }
                                    com.coachie.app.data.model.ReminderActionType.VIEW_HABITS -> onNavigateToHabits()
                                    com.coachie.app.data.model.ReminderActionType.VIEW_HEALTH_TRACKING -> onNavigateToHealthTracking()
                                    com.coachie.app.data.model.ReminderActionType.VIEW_WELLNESS -> {
                                        val titleLower = currentTask.title.lowercase()
                                        val descLower = currentTask.description.lowercase()
                                        val isBreathingTask = titleLower.contains("breathing") || 
                                                              titleLower.contains("breath") ||
                                                              descLower.contains("breathing")
                                        if (isBreathingTask) {
                                            onNavigateToBreathingExercises()
                                        } else {
                                            onNavigateToWellness()
                                        }
                                    }
                                }
                                // Mark task as completed after navigation
                                kotlinx.coroutines.GlobalScope.launch {
                                    try {
                                        repository.completeTodaysFocusTask(userId, currentTask.id)
                                        // Reload tasks to remove completed one
                                        kotlinx.coroutines.delay(500)
                                        val tasksResult = repository.getTodaysFocusTasks(userId, today)
                                        tasks = tasksResult.getOrNull() ?: emptyList()
                                    } catch (e: Exception) {
                                        android.util.Log.e("TodaysResetCard", "Error completing task", e)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState(isGenerating: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = Primary40,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isGenerating) "Generating your personalized reset..." else "Preparing your personalized reset...",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ErrorState(
    errorMessage: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Error",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun EmptyRemindersState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = Primary40,
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = "All Caught Up! 🎉",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = "You've completed all your reminders for today. Great job!",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TaskContent(
    task: com.coachie.app.data.model.TodaysFocusTask,
    tasksCount: Int,
    currentIndex: Int,
    canGoNext: Boolean,
    canGoPrevious: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onAction: (com.coachie.app.data.model.ReminderActionType, Map<String, Any>) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Task type indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val icon = when (task.type) {
                    com.coachie.app.data.model.TodaysFocusTaskType.HEALTH_LOG -> Icons.Filled.FitnessCenter
                    com.coachie.app.data.model.TodaysFocusTaskType.HABIT -> Icons.Filled.CheckCircle
                    com.coachie.app.data.model.TodaysFocusTaskType.WELLNESS -> Icons.Filled.SelfImprovement
                }
                
                val category = when (task.type) {
                    com.coachie.app.data.model.TodaysFocusTaskType.HEALTH_LOG -> SemanticColorCategory.HEALTH_TRACKING
                    com.coachie.app.data.model.TodaysFocusTaskType.HABIT -> SemanticColorCategory.HABITS
                    com.coachie.app.data.model.TodaysFocusTaskType.WELLNESS -> SemanticColorCategory.WELLNESS
                }
                
                val categoryColor = getSemanticColorPrimary(category)
                
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = categoryColor,
                    modifier = Modifier.size(24.dp)
                )
                
                Text(
                    text = when (task.type) {
                        com.coachie.app.data.model.TodaysFocusTaskType.HEALTH_LOG -> "Health"
                        com.coachie.app.data.model.TodaysFocusTaskType.HABIT -> "Habit"
                        com.coachie.app.data.model.TodaysFocusTaskType.WELLNESS -> "Wellness"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (tasksCount > 1) {
                val category = when (task.type) {
                    com.coachie.app.data.model.TodaysFocusTaskType.HEALTH_LOG -> SemanticColorCategory.HEALTH_TRACKING
                    com.coachie.app.data.model.TodaysFocusTaskType.HABIT -> SemanticColorCategory.HABITS
                    com.coachie.app.data.model.TodaysFocusTaskType.WELLNESS -> SemanticColorCategory.WELLNESS
                }
                val categoryColor = getSemanticColorPrimary(category)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPrevious,
                        enabled = canGoPrevious,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous",
                            tint = if (canGoPrevious) categoryColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Text(
                        text = "$currentIndex of $tasksCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    IconButton(
                        onClick = onNext,
                        enabled = canGoNext,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next",
                            tint = if (canGoNext) categoryColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Title
        Text(
            text = task.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Description
        Text(
            text = task.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Action button
        val category = when (task.type) {
            com.coachie.app.data.model.TodaysFocusTaskType.HEALTH_LOG -> SemanticColorCategory.HEALTH_TRACKING
            com.coachie.app.data.model.TodaysFocusTaskType.HABIT -> SemanticColorCategory.HABITS
            com.coachie.app.data.model.TodaysFocusTaskType.WELLNESS -> SemanticColorCategory.WELLNESS
        }
        val categoryColor = getSemanticColorPrimary(category)
        
        Button(
            onClick = { onAction(task.actionType, task.actionData) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = categoryColor
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = when (task.actionType) {
                    com.coachie.app.data.model.ReminderActionType.LOG_MEAL -> "Log Meal"
                    com.coachie.app.data.model.ReminderActionType.LOG_WATER -> "Log Water"
                    com.coachie.app.data.model.ReminderActionType.LOG_WEIGHT -> "Log Weight"
                    com.coachie.app.data.model.ReminderActionType.LOG_SLEEP -> "Log Sleep"
                    com.coachie.app.data.model.ReminderActionType.LOG_WORKOUT -> "Log Workout"
                    com.coachie.app.data.model.ReminderActionType.LOG_SUPPLEMENT -> "Log Supplement"
                    com.coachie.app.data.model.ReminderActionType.COMPLETE_HABIT -> "Complete Habit"
                    com.coachie.app.data.model.ReminderActionType.START_JOURNAL -> "Start Journaling"
                    com.coachie.app.data.model.ReminderActionType.START_MEDITATION -> "Start Meditation"
                    com.coachie.app.data.model.ReminderActionType.START_MINDFULNESS -> "Start Breathing"
                    com.coachie.app.data.model.ReminderActionType.VIEW_HABITS -> "View Habits"
                    com.coachie.app.data.model.ReminderActionType.VIEW_HEALTH_TRACKING -> "View Health"
                    com.coachie.app.data.model.ReminderActionType.VIEW_WELLNESS -> "View Wellness"
                },
                color = Color.White
            )
        }
        
        // Estimated duration
        task.estimatedDuration?.let { duration ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "~$duration min",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReminderContent(
    reminder: com.coachie.app.data.model.Reminder,
    remindersCount: Int,
    currentIndex: Int,
    canGoNext: Boolean,
    canGoPrevious: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onAction: (com.coachie.app.data.model.ReminderActionType, Map<String, Any>) -> Unit,
    onSkip: () -> Unit,
    mindfulnessSession: HealthLog.MindfulSession?,
    isPlaying: Boolean,
    playbackProgress: Float,
    isTranscriptExpanded: Boolean,
    onPlayPauseClick: () -> Unit,
    onTranscriptToggle: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Reminder type indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Icon based on reminder type
                val icon = when (reminder.type) {
                    com.coachie.app.data.model.ReminderType.HEALTH_LOG -> Icons.Filled.FitnessCenter
                    com.coachie.app.data.model.ReminderType.HABIT -> Icons.Filled.CheckCircle
                    com.coachie.app.data.model.ReminderType.WELLNESS -> Icons.Filled.SelfImprovement
                    com.coachie.app.data.model.ReminderType.MINDFULNESS -> Icons.Filled.SelfImprovement
                    com.coachie.app.data.model.ReminderType.CHALLENGE -> Icons.Filled.EmojiEvents
                }
                
                // Map reminder type to semantic category
                val category = when (reminder.type) {
                    com.coachie.app.data.model.ReminderType.HEALTH_LOG -> SemanticColorCategory.HEALTH_TRACKING
                    com.coachie.app.data.model.ReminderType.HABIT -> SemanticColorCategory.HABITS
                    com.coachie.app.data.model.ReminderType.WELLNESS -> SemanticColorCategory.WELLNESS
                    com.coachie.app.data.model.ReminderType.MINDFULNESS -> SemanticColorCategory.WELLNESS
                    com.coachie.app.data.model.ReminderType.CHALLENGE -> SemanticColorCategory.QUESTS
                }
                
                // Get category color (gender-aware)
                val categoryColor = getSemanticColorPrimary(category)
                
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = categoryColor,
                    modifier = Modifier.size(24.dp)
                )
                
                Text(
                    text = when (reminder.type) {
                        com.coachie.app.data.model.ReminderType.HEALTH_LOG -> "Health"
                        com.coachie.app.data.model.ReminderType.HABIT -> "Habit"
                        com.coachie.app.data.model.ReminderType.WELLNESS -> "Wellness"
                        com.coachie.app.data.model.ReminderType.MINDFULNESS -> "Mindfulness"
                        com.coachie.app.data.model.ReminderType.CHALLENGE -> "Challenge"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (remindersCount > 1) {
                // Map reminder type to semantic category for navigation arrow colors
                val category = when (reminder.type) {
                    com.coachie.app.data.model.ReminderType.HEALTH_LOG -> SemanticColorCategory.HEALTH_TRACKING
                    com.coachie.app.data.model.ReminderType.HABIT -> SemanticColorCategory.HABITS
                    com.coachie.app.data.model.ReminderType.WELLNESS -> SemanticColorCategory.WELLNESS
                    com.coachie.app.data.model.ReminderType.MINDFULNESS -> SemanticColorCategory.WELLNESS
                    com.coachie.app.data.model.ReminderType.CHALLENGE -> SemanticColorCategory.QUESTS
                }
                val categoryColor = getSemanticColorPrimary(category)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous button
                    IconButton(
                        onClick = onPrevious,
                        enabled = canGoPrevious,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous",
                            tint = if (canGoPrevious) categoryColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Text(
                        text = "$currentIndex of $remindersCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Next button
                    IconButton(
                        onClick = onNext,
                        enabled = canGoNext,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next",
                            tint = if (canGoNext) categoryColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Title
        Text(
            text = reminder.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Description
        Text(
            text = reminder.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Special handling for mindfulness reminders
        if (reminder.type == com.coachie.app.data.model.ReminderType.MINDFULNESS && mindfulnessSession != null) {
            // Show mindfulness session player
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Primary40, Secondary40)
                            )
                        )
                        .clickable(onClick = onPlayPauseClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        progress = { playbackProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Primary40,
                        trackColor = Color.LightGray.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "3:00",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) Color.Red else Color.Gray
                    )
                }
                
                IconButton(onClick = onTranscriptToggle) {
                    Icon(
                        imageVector = if (isTranscriptExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (isTranscriptExpanded) "Collapse transcript" else "Expand transcript",
                        tint = Primary40
                    )
                }
            }
            
            // Transcript section
            AnimatedVisibility(
                visible = isTranscriptExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.LightGray.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Transcript",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Primary40
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = mindfulnessSession.transcript,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.DarkGray,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2f,
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
        } else {
            // Regular reminder action button
            val canSkip = reminder.actionType == com.coachie.app.data.model.ReminderActionType.LOG_MEAL && 
                         (reminder.actionData["canSkip"] as? Boolean ?: false)
            
            if (canSkip) {
                // Meal reminder with skip option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Map reminder type to semantic category for button color
                    val category = when (reminder.type) {
                        com.coachie.app.data.model.ReminderType.HEALTH_LOG -> SemanticColorCategory.HEALTH_TRACKING
                        com.coachie.app.data.model.ReminderType.HABIT -> SemanticColorCategory.HABITS
                        com.coachie.app.data.model.ReminderType.WELLNESS -> SemanticColorCategory.WELLNESS
                        com.coachie.app.data.model.ReminderType.MINDFULNESS -> SemanticColorCategory.WELLNESS
                        com.coachie.app.data.model.ReminderType.CHALLENGE -> SemanticColorCategory.QUESTS
                    }
                    val categoryColor = getSemanticColorPrimary(category)
                    
                    Button(
                        onClick = { onAction(reminder.actionType, reminder.actionData) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = categoryColor
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Log Meal",
                            color = Color.White
                        )
                    }
                    
                    OutlinedButton(
                        onClick = onSkip,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Skip")
                    }
                }
            } else {
                // Map reminder type to semantic category for button color
                val category = when (reminder.type) {
                    com.coachie.app.data.model.ReminderType.HEALTH_LOG -> SemanticColorCategory.HEALTH_TRACKING
                    com.coachie.app.data.model.ReminderType.HABIT -> SemanticColorCategory.HABITS
                    com.coachie.app.data.model.ReminderType.WELLNESS -> SemanticColorCategory.WELLNESS
                    com.coachie.app.data.model.ReminderType.MINDFULNESS -> SemanticColorCategory.WELLNESS
                    com.coachie.app.data.model.ReminderType.CHALLENGE -> SemanticColorCategory.QUESTS
                }
                val categoryColor = getSemanticColorPrimary(category)
                
                Button(
                    onClick = { onAction(reminder.actionType, reminder.actionData) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = categoryColor
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = when (reminder.actionType) {
                            com.coachie.app.data.model.ReminderActionType.LOG_MEAL -> "Log Meal"
                            com.coachie.app.data.model.ReminderActionType.LOG_WATER -> "Log Water"
                            com.coachie.app.data.model.ReminderActionType.LOG_WEIGHT -> "Log Weight"
                            com.coachie.app.data.model.ReminderActionType.LOG_SLEEP -> "Log Sleep"
                            com.coachie.app.data.model.ReminderActionType.LOG_WORKOUT -> "Log Workout"
                            com.coachie.app.data.model.ReminderActionType.LOG_SUPPLEMENT -> "Log Supplement"
                            com.coachie.app.data.model.ReminderActionType.COMPLETE_HABIT -> "Complete Habit"
                            com.coachie.app.data.model.ReminderActionType.START_JOURNAL -> "Start Journaling"
                            com.coachie.app.data.model.ReminderActionType.START_MEDITATION -> "Start Meditation"
                            com.coachie.app.data.model.ReminderActionType.START_MINDFULNESS -> "Start Reset"
                            com.coachie.app.data.model.ReminderActionType.VIEW_HABITS -> "View Habits"
                            com.coachie.app.data.model.ReminderActionType.VIEW_HEALTH_TRACKING -> "View Health"
                            com.coachie.app.data.model.ReminderActionType.VIEW_WELLNESS -> "View Wellness"
                        },
                        color = Color.White
                    )
                }
            }
        }
        
        // Estimated duration
        reminder.estimatedDuration?.let { duration ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "~$duration min",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyState(
    onGenerateClick: () -> Unit,
    isGenerating: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.SelfImprovement,
            contentDescription = null,
            tint = Primary40,
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = "Start Your Day Right",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Get a personalized 3-minute mindfulness session to reset and focus",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = {
                android.util.Log.d("TodaysResetCard", "Generate button clicked!")
                onGenerateClick()
            },
            enabled = !isGenerating,
            colors = ButtonDefaults.buttonColors(
                containerColor = Primary40
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generating...")
            } else {
                Text("Generate My Reset")
            }
        }
    }
}

@Composable
private fun CompletedState(
    session: HealthLog.MindfulSession,
    onPlayAgain: () -> Unit,
    isPlaying: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = Primary40,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = "Reset Complete! ✨",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Primary40
        )
        Text(
            text = "You've completed today's 3-minute reset. Great job taking time for yourself!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onPlayAgain,
            enabled = !isPlaying,
            colors = ButtonDefaults.buttonColors(
                containerColor = Primary40
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isPlaying) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Playing...")
            } else {
                Text("Play Again")
            }
        }
    }
}

@Composable
private fun SessionContent(
    session: HealthLog.MindfulSession,
    isPlaying: Boolean,
    playbackProgress: Float,
    isTranscriptExpanded: Boolean,
    onPlayPauseClick: () -> Unit,
    onTranscriptToggle: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {

        // Personalized title based on session data
        Text(
            text = session.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = Color.DarkGray
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Audio Player Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Play/Pause Button
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Primary40, Secondary40)
                        )
                    )
                    .clickable(onClick = onPlayPauseClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Progress and Time
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    progress = { playbackProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = Primary40,
                    trackColor = Color.LightGray.copy(alpha = 0.3f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "3:00", // Fixed duration for now
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            // Transcript Toggle
            IconButton(onClick = onTranscriptToggle) {
                Icon(
                    imageVector = if (isTranscriptExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isTranscriptExpanded) "Collapse transcript" else "Expand transcript",
                    tint = Primary40
                )
            }
        }

        // Transcript Section
        AnimatedVisibility(
            visible = isTranscriptExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.LightGray.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Transcript",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Primary40
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = session.transcript,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.DarkGray,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2f,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }

        // Favorite indicator
        if (session.isFavorite) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Favorited",
                    tint = Color.Red,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Saved to favorites",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Red
                )
            }
        }
    }
}

@Composable
private fun SuggestionContent(
    suggestion: Suggestion,
    totalCount: Int,
    currentIndex: Int,
    canGoNext: Boolean,
    canGoPrevious: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onAction: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Suggestion type indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = suggestion.icon,
                    contentDescription = null,
                    tint = suggestion.accentColor,
                    modifier = Modifier.size(24.dp)
                )
                
                Text(
                    text = when (suggestion.type) {
                        SuggestionType.HABIT_SUGGESTION -> "Habit"
                        SuggestionType.WELLNESS_REMINDER -> "Wellness"
                        SuggestionType.JOURNAL_PROMPT -> "Journal"
                        SuggestionType.HABIT_PROGRESS -> "Progress"
                        SuggestionType.FIRST_TIME_HABITS -> "New Habit"
                        SuggestionType.HEALTH_TRACKING -> "Health"
                        SuggestionType.COMMUNITY -> "Community"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (totalCount > 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous button
                    IconButton(
                        onClick = onPrevious,
                        enabled = canGoPrevious,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous",
                            tint = if (canGoPrevious) suggestion.accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Text(
                        text = "$currentIndex of $totalCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Next button
                    IconButton(
                        onClick = onNext,
                        enabled = canGoNext,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next",
                            tint = if (canGoNext) suggestion.accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Title
        Text(
            text = suggestion.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Description
        Text(
            text = suggestion.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Action button
        Button(
            onClick = onAction,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = suggestion.accentColor
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Try it",
                color = Color.White
            )
        }
    }
}
