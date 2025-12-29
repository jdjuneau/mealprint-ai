package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.ui.theme.LocalGender
import com.coachie.app.ui.theme.SemanticColorCategory
import com.coachie.app.ui.theme.getSemanticColorPrimary
import com.coachie.app.viewmodel.StretchingViewModel
import com.coachie.app.viewmodel.StretchingState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.DisposableEffect

data class StretchExercise(
    val name: String,
    val description: String,
    val duration: Int, // seconds
    val instructions: List<String>
)

val defaultStretches = listOf(
    StretchExercise(
        name = "Neck Rolls",
        description = "Gentle neck rotation",
        duration = 30,
        instructions = listOf(
            "Slowly tilt your head to the right",
            "Roll forward and to the left",
            "Repeat 3 times each direction"
        )
    ),
    StretchExercise(
        name = "Shoulder Rolls",
        description = "Release shoulder tension",
        duration = 30,
        instructions = listOf(
            "Roll shoulders backward in circles",
            "Do 5 rotations",
            "Then reverse direction"
        )
    ),
    StretchExercise(
        name = "Standing Forward Fold",
        description = "Stretch hamstrings and back",
        duration = 45,
        instructions = listOf(
            "Stand with feet hip-width apart",
            "Slowly fold forward from hips",
            "Let arms hang naturally",
            "Hold for 30 seconds"
        )
    ),
    StretchExercise(
        name = "Quad Stretch",
        description = "Stretch front of thighs",
        duration = 30,
        instructions = listOf(
            "Stand and hold onto wall for balance",
            "Bend one knee, bringing heel toward glutes",
            "Hold ankle gently",
            "Hold for 15 seconds each leg"
        )
    ),
    StretchExercise(
        name = "Calf Stretch",
        description = "Stretch lower legs",
        duration = 30,
        instructions = listOf(
            "Step one foot back",
            "Keep back leg straight",
            "Bend front knee slightly",
            "Hold for 15 seconds each leg"
        )
    ),
    StretchExercise(
        name = "Hip Circles",
        description = "Mobilize hip joints",
        duration = 30,
        instructions = listOf(
            "Stand with hands on hips",
            "Circle hips clockwise 5 times",
            "Then counterclockwise 5 times"
        )
    ),
    StretchExercise(
        name = "Side Stretch",
        description = "Stretch side body",
        duration = 30,
        instructions = listOf(
            "Stand with feet hip-width apart",
            "Raise one arm overhead",
            "Lean to the opposite side",
            "Hold for 15 seconds each side"
        )
    ),
    StretchExercise(
        name = "Spinal Twist",
        description = "Rotate and stretch spine",
        duration = 30,
        instructions = listOf(
            "Sit or stand tall",
            "Place one hand on opposite knee",
            "Gently twist to that side",
            "Hold for 15 seconds each side"
        )
    ),
    StretchExercise(
        name = "Hamstring Stretch",
        description = "Deep stretch for back of thighs",
        duration = 45,
        instructions = listOf(
            "Sit on floor with one leg extended",
            "Bend other leg, foot against inner thigh",
            "Reach forward toward extended foot",
            "Hold for 30 seconds each leg"
        )
    ),
    StretchExercise(
        name = "Pigeon Pose",
        description = "Hip opener stretch",
        duration = 60,
        instructions = listOf(
            "Start in downward dog",
            "Bring one knee forward to wrist",
            "Extend back leg straight",
            "Hold for 30 seconds each side"
        )
    ),
    StretchExercise(
        name = "Butterfly Stretch",
        description = "Inner thigh and hip stretch",
        duration = 45,
        instructions = listOf(
            "Sit with soles of feet together",
            "Gently press knees toward floor",
            "Lean forward slightly",
            "Hold for 30 seconds"
        )
    ),
    StretchExercise(
        name = "Seated Forward Fold",
        description = "Full body stretch",
        duration = 60,
        instructions = listOf(
            "Sit with legs extended",
            "Reach forward toward toes",
            "Keep back straight",
            "Hold for 45 seconds"
        )
    ),
    StretchExercise(
        name = "Cat-Cow Stretch",
        description = "Spinal mobility",
        duration = 60,
        instructions = listOf(
            "Start on hands and knees",
            "Arch back (cow), then round (cat)",
            "Move slowly between positions",
            "Repeat 10 times"
        )
    ),
    StretchExercise(
        name = "Child's Pose",
        description = "Relaxing back and hip stretch",
        duration = 60,
        instructions = listOf(
            "Kneel on floor",
            "Sit back on heels",
            "Reach arms forward",
            "Hold for 45 seconds"
        )
    ),
    StretchExercise(
        name = "Lunge Stretch",
        description = "Hip flexor and quad stretch",
        duration = 45,
        instructions = listOf(
            "Step one foot forward into lunge",
            "Keep back leg straight",
            "Push hips forward",
            "Hold for 30 seconds each side"
        )
    ),
    StretchExercise(
        name = "Figure Four Stretch",
        description = "Deep hip and glute stretch",
        duration = 60,
        instructions = listOf(
            "Lie on back",
            "Cross one ankle over opposite knee",
            "Pull thigh toward chest",
            "Hold for 30 seconds each side"
        )
    ),
    StretchExercise(
        name = "Chest Opener",
        description = "Stretch front of shoulders and chest",
        duration = 45,
        instructions = listOf(
            "Stand in doorway",
            "Place forearm on doorframe",
            "Step forward gently",
            "Hold for 30 seconds each side"
        )
    ),
    StretchExercise(
        name = "Triceps Stretch",
        description = "Stretch back of arms",
        duration = 30,
        instructions = listOf(
            "Raise one arm overhead",
            "Bend elbow, hand behind head",
            "Gently pull elbow with other hand",
            "Hold for 20 seconds each arm"
        )
    ),
    StretchExercise(
        name = "Wrist Circles",
        description = "Mobilize wrists",
        duration = 30,
        instructions = listOf(
            "Extend arms forward",
            "Circle wrists clockwise 10 times",
            "Then counterclockwise 10 times"
        )
    ),
    StretchExercise(
        name = "Ankle Circles",
        description = "Mobilize ankles",
        duration = 30,
        instructions = listOf(
            "Sit or stand",
            "Lift one foot off ground",
            "Circle ankle 10 times each direction",
            "Repeat with other foot"
        )
    ),
    StretchExercise(
        name = "Full Body Stretch",
        description = "Reach and stretch entire body",
        duration = 45,
        instructions = listOf(
            "Stand tall",
            "Reach arms overhead",
            "Stretch up and back slightly",
            "Hold for 30 seconds"
        )
    )
)

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
fun StretchingScreen(
    habitTitle: String,
    durationMinutes: Int,
    habitId: String?,
    userId: String,
    onComplete: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: StretchingViewModel = viewModel()
) {
    // Format the habit title to fix URL encoding issues
    val formattedTitle = remember(habitTitle) { formatHabitTitle(habitTitle) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val state by viewModel.state.collectAsState()
    val timeRemaining by viewModel.timeRemaining.collectAsState()
    val currentStretch by viewModel.currentStretch.collectAsState()
    val currentStretchIndex by viewModel.currentStretchIndex.collectAsState()
    val totalProgress by viewModel.totalProgress.collectAsState()
    val selectedStretches by viewModel.selectedStretches.collectAsState()
    
    // Get gender-aware text color
    val gender = LocalGender.current
    val isMale = gender.lowercase() == "male"
    val textColor = if (isMale) {
        Color(0xFF1F2937) // Dark gray for male UI
    } else {
        Color(0xFF1F2937)
    }
    val secondaryTextColor = if (isMale) {
        Color(0xFF374151)
    } else {
        Color(0xFF374151)
    }
    val accentColor = getSemanticColorPrimary(SemanticColorCategory.HEALTH_TRACKING, isMale)
    
    // Initialize ViewModel
    LaunchedEffect(Unit) {
        viewModel.initialize(context, durationMinutes)
    }
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            viewModel.cleanup()
        }
    }
    
    // Track if we've initiated navigation to prevent multiple calls
    var hasNavigated by remember { mutableStateOf(false) }
    
    // Auto-complete habit when stretching finishes
    LaunchedEffect(state, habitId) {
        if (state == StretchingState.COMPLETED && habitId != null && !hasNavigated) {
            hasNavigated = true
            
            // Save habit completion using HabitTimerViewModel's method
            val habitTimerViewModel = com.coachie.app.viewmodel.HabitTimerViewModel()
            habitTimerViewModel.initialize(durationMinutes, formattedTitle)
            val completionResult = habitTimerViewModel.completeHabit(context, userId, habitId, durationMinutes)
            
            completionResult.onSuccess {
                // Small delay to allow Firestore to propagate, then navigate
                kotlinx.coroutines.delay(1500)
                // Navigate on main thread to ensure clean navigation
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete()
                }
            }.onFailure { error ->
                android.util.Log.e("StretchingScreen", "Failed to save habit completion", error)
                hasNavigated = false // Reset so user can try again
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(formattedTitle, color = textColor) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = textColor,
                    navigationIconContentColor = textColor
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
            when (state) {
                StretchingState.IDLE -> {
                    // Setup screen - show all stretches
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CoachieCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CoachieCardDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Ready to stretch?",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                    textAlign = TextAlign.Center
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Button(
                                    onClick = { viewModel.startStretching() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accentColor
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Start Stretching", color = Color.White)
                                }
                            }
                        }
                        
                        Text(
                            text = "Stretching Exercises",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(selectedStretches.ifEmpty { defaultStretches.take(6) }) { stretch ->
                                StretchExerciseCard(stretch = stretch)
                            }
                        }
                    }
                }
                
                StretchingState.PREPARING -> {
                    // Preparation screen
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Prepare to stretch...",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = textColor
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        CircularProgressIndicator(
                            color = accentColor,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
                
                StretchingState.STRETCHING,
                StretchingState.TRANSITIONING -> {
                    // Active stretching screen with timer
                    currentStretch?.let { stretch ->
                        StretchingActiveScreen(
                            stretch = stretch,
                            timeRemaining = timeRemaining,
                            currentIndex = currentStretchIndex + 1,
                            totalStretches = selectedStretches.ifEmpty { defaultStretches.take(6) }.size,
                            progress = totalProgress,
                            onPause = { viewModel.pause() },
                            onResume = { viewModel.resume() },
                            onStop = { viewModel.stop() },
                            isPaused = state == StretchingState.IDLE,
                            accentColor = accentColor,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            viewModel = viewModel
                        )
                    }
                }
                
                StretchingState.COMPLETED -> {
                    // Completion screen
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "🎉 Great job!",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "You completed your stretching routine!",
                            style = MaterialTheme.typography.titleLarge,
                            color = secondaryTextColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StretchingActiveScreen(
    stretch: StretchExercise,
    timeRemaining: Long,
    currentIndex: Int,
    totalStretches: Int,
    progress: Float,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    isPaused: Boolean,
    accentColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    viewModel: StretchingViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Progress indicator
        Text(
            text = "$currentIndex of $totalStretches",
            style = MaterialTheme.typography.titleMedium,
            color = secondaryTextColor
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Timer circle
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Background circle
                drawArc(
                    color = accentColor.copy(alpha = 0.2f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 12f, cap = StrokeCap.Round)
                )
                
                // Progress arc
                val stretchProgress = if (stretch.duration > 0) {
                    1f - (timeRemaining.toFloat() / stretch.duration.toFloat())
                } else {
                    0f
                }
                drawArc(
                    color = accentColor,
                    startAngle = -90f,
                    sweepAngle = stretchProgress * 360f,
                    useCenter = false,
                    style = Stroke(width = 12f, cap = StrokeCap.Round)
                )
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = viewModel.formatTime(timeRemaining),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Text(
                    text = "remaining",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Current stretch name
        Text(
            text = stretch.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = textColor,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Description
        Text(
            text = stretch.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = secondaryTextColor
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Instructions
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            stretch.instructions.take(2).forEach { instruction ->
                Text(
                    text = "• $instruction",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = secondaryTextColor
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Control buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isPaused) {
                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor
                    )
                ) {
                    Text("Resume", color = Color.White)
                }
            } else {
                OutlinedButton(
                    onClick = onPause,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = accentColor
                    )
                ) {
                    Text("Pause")
                }
            }
            
            OutlinedButton(
                onClick = onStop,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Stop")
            }
        }
    }
}

@Composable
fun StretchExerciseCard(stretch: StretchExercise) {
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stretch.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stretch.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${stretch.duration}s",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                stretch.instructions.forEach { instruction ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = instruction,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
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

