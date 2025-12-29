package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.ui.theme.LocalGender
import com.coachie.app.ui.theme.getSemanticColorPrimary
import com.coachie.app.ui.theme.SemanticColorCategory
import com.coachie.app.viewmodel.HabitTimerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.util.concurrent.TimeUnit

/**
 * Format habit title by replacing URL-encoded characters with proper formatting
 */
private fun formatHabitTitle(title: String): String {
    return try {
        // Decode URL encoding (replaces + with spaces, %20 with spaces, etc.)
        java.net.URLDecoder.decode(title, "UTF-8")
            .replace("+", " ") // Extra safety: replace any remaining + signs
            .trim()
    } catch (e: Exception) {
        // If decoding fails, at least replace + with spaces
        title.replace("+", " ").trim()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitTimerScreen(
    habitTitle: String,
    durationMinutes: Int,
    habitId: String?,
    userId: String,
    onComplete: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: HabitTimerViewModel = viewModel()
) {
    // Format the habit title to fix URL encoding issues
    val formattedTitle = remember(habitTitle) { formatHabitTitle(habitTitle) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val state by viewModel.state.collectAsState()
    val timeRemaining by viewModel.timeRemaining.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    
    // Check if this is a reading habit (for background timer)
    val isReadingHabit = remember(formattedTitle) {
        formattedTitle.lowercase().contains("read") || 
        formattedTitle.lowercase().contains("reading")
    }
    
    // Get gender-aware text color
    val gender = LocalGender.current
    val isMale = gender.lowercase() == "male"
    val textColor = if (isMale) {
        Color(0xFF1F2937) // Dark gray for male UI on light gradient
    } else {
        Color(0xFF1F2937) // Dark gray for female UI too
    }
    val secondaryTextColor = if (isMale) {
        Color(0xFF374151) // Medium gray
    } else {
        Color(0xFF374151)
    }
    
    // Get actual userId from FirebaseAuth if not provided or invalid
    val actualUserId = remember(userId) {
        val uid = userId.takeIf { it.isNotBlank() && it != "anonymous" }
            ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: ""
        android.util.Log.d("HabitTimerScreen", "Resolved userId: '$uid' (provided: '$userId')")
        uid
    }
    
    // For reading habits, allow duration selection
    var selectedDurationMinutes by remember { mutableStateOf(durationMinutes) }
    
    // CRITICAL: Log all parameters on screen initialization
    LaunchedEffect(Unit) {
        android.util.Log.i("HabitTimerScreen", "=========================================")
        android.util.Log.i("HabitTimerScreen", "HABIT TIMER SCREEN INITIALIZED")
        android.util.Log.i("HabitTimerScreen", "  habitTitle (raw): '$habitTitle'")
        android.util.Log.i("HabitTimerScreen", "  habitTitle (formatted): '$formattedTitle'")
        android.util.Log.i("HabitTimerScreen", "  durationMinutes: $durationMinutes")
        android.util.Log.i("HabitTimerScreen", "  habitId: $habitId")
        android.util.Log.i("HabitTimerScreen", "  userId (provided): '$userId'")
        android.util.Log.i("HabitTimerScreen", "  actualUserId (resolved): '$actualUserId'")
        android.util.Log.i("HabitTimerScreen", "=========================================")
        viewModel.initialize(selectedDurationMinutes, formattedTitle)
    }
    
    // Re-initialize when selected duration changes (for reading habits)
    LaunchedEffect(selectedDurationMinutes) {
        if (state == HabitTimerViewModel.TimerState.IDLE) {
            viewModel.initialize(selectedDurationMinutes, formattedTitle)
        }
    }
    
    // Track if we've already saved the completion to prevent duplicate saves
    var hasSavedCompletion by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // CRITICAL: Log all state changes to debug why completion isn't triggering
    LaunchedEffect(state) {
        android.util.Log.d("HabitTimerScreen", "🔔 STATE CHANGED: $state, habitId=$habitId, actualUserId='$actualUserId', hasSavedCompletion=$hasSavedCompletion")
    }
    
    // Auto-complete habit when timer finishes - use state as key to ensure it triggers
    LaunchedEffect(state, habitId, actualUserId) {
        android.util.Log.d("HabitTimerScreen", "🔔 LaunchedEffect triggered: state=$state, habitId=$habitId, actualUserId='$actualUserId', hasSavedCompletion=$hasSavedCompletion")
        
        if (state == HabitTimerViewModel.TimerState.COMPLETED) {
            android.util.Log.i("HabitTimerScreen", "✅ Timer state is COMPLETED - checking conditions...")
            
            if (habitId == null) {
                android.util.Log.e("HabitTimerScreen", "❌ CANNOT SAVE: habitId is NULL!")
                return@LaunchedEffect
            }
            
            if (actualUserId.isBlank()) {
                android.util.Log.e("HabitTimerScreen", "❌ CANNOT SAVE: actualUserId is BLANK!")
                return@LaunchedEffect
            }
            
            if (hasSavedCompletion) {
                android.util.Log.w("HabitTimerScreen", "⚠️ Already saved completion, skipping")
                return@LaunchedEffect
            }
            
            // Mark as saved immediately to prevent duplicates
            hasSavedCompletion = true
            android.util.Log.i("HabitTimerScreen", "🚀 STARTING HABIT SAVE: habitId=$habitId, userId=$actualUserId, duration=$durationMinutes")
            
            viewModel.playCompletionNotification(context) // Play sound and vibrate
            
            // CRITICAL: Wait for habit completion to finish before navigating
            val completionResult = viewModel.completeHabit(context, actualUserId, habitId, selectedDurationMinutes)
            
            completionResult.onSuccess { completionId ->
                android.util.Log.i("HabitTimerScreen", "✅✅✅ HABIT SAVED SUCCESSFULLY! Completion ID: $completionId")
                
                // Small delay to allow Firestore to propagate, then navigate
                kotlinx.coroutines.delay(1500)
                
                android.util.Log.d("HabitTimerScreen", "Navigating back to home screen after habit save")
                // Navigate on main thread to ensure clean navigation
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete()
                }
            }.onFailure { error ->
                android.util.Log.e("HabitTimerScreen", "❌❌❌ FAILED TO SAVE HABIT! Error: ${error.message}", error)
                error.printStackTrace()
                hasSavedCompletion = false // Reset so user can try again
                // Don't navigate away if save failed - user can see the error toast
            }
        } else {
            android.util.Log.d("HabitTimerScreen", "Timer state is $state (not COMPLETED yet)")
        }
    }
    
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(formattedTitle) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = textColor,
                    navigationIconContentColor = textColor
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBackground)
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                when (state) {
                    HabitTimerViewModel.TimerState.IDLE -> {
                        Text(
                            text = "Ready to start?",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        
                        // Duration selection for reading habits
                        if (isReadingHabit) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Select Duration",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val readingDurations = listOf(30, 60) // 30 mins, 1 hour
                                readingDurations.forEach { duration ->
                                    OutlinedButton(
                                        onClick = {
                                            selectedDurationMinutes = duration
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (selectedDurationMinutes == duration) 
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) 
                                            else Color.Transparent,
                                            contentColor = textColor
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = 1.dp,
                                            color = if (selectedDurationMinutes == duration) 
                                                MaterialTheme.colorScheme.primary 
                                            else Color.Gray.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Text(
                                            text = if (duration == 60) "1 hour" else "${duration} min",
                                            color = textColor
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        Text(
                            text = formatTime(timeRemaining),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        
                        Button(
                            onClick = { 
                                viewModel.start(context, useBackgroundService = isReadingHabit)
                            },
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Start",
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                    
                    HabitTimerViewModel.TimerState.RUNNING -> {
                        Text(
                            text = "Keep going!",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        
                        Text(
                            text = formatTime(timeRemaining),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = { 
                                    viewModel.pause()
                                    if (isReadingHabit) {
                                        com.coachie.app.service.HabitTimerService.stopTimer(context)
                                    }
                                },
                                modifier = Modifier.size(80.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Pause,
                                    contentDescription = "Pause",
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            
                            Button(
                                onClick = { viewModel.stop(context) },
                                modifier = Modifier.size(80.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Stop,
                                    contentDescription = "Stop",
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }
                    
                    HabitTimerViewModel.TimerState.PAUSED -> {
                        Text(
                            text = "Paused",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        
                        Text(
                            text = formatTime(timeRemaining),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = { 
                                    viewModel.resume(context, useBackgroundService = isReadingHabit)
                                },
                                modifier = Modifier.size(80.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Resume",
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            
                            Button(
                                onClick = { viewModel.stop(context) },
                                modifier = Modifier.size(80.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Stop,
                                    contentDescription = "Stop",
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }
                    
                    HabitTimerViewModel.TimerState.COMPLETED -> {
                        Text(
                            text = "🎉 Great job!",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            textAlign = TextAlign.Center
                        )
                        
                        Text(
                            text = "You completed $formattedTitle",
                            style = MaterialTheme.typography.titleLarge,
                            color = secondaryTextColor,
                            textAlign = TextAlign.Center
                        )
                        
                        // Manual save button as fallback if auto-save didn't work
                        val scope = rememberCoroutineScope()
                        if (habitId != null && !hasSavedCompletion && actualUserId.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        android.util.Log.w("HabitTimerScreen", "Manual save button clicked - saving habit")
                                        val result = viewModel.completeHabit(context, actualUserId, habitId, selectedDurationMinutes)
                                        result.onSuccess {
                                            hasSavedCompletion = true
                                            kotlinx.coroutines.delay(2000)
                                            onComplete()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Save & Continue")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%d:%02d", minutes, secs)
    }
}

