package com.coachie.app.ui.components

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.data.model.HealthLog
import com.mealprint.ai.viewmodel.BoxBreathingViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun BoxBreathing(
    stressLevel: Int,
    userId: String,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    customDurationSeconds: Int? = null,
    pattern: BoxBreathingViewModel.BreathingPattern = BoxBreathingViewModel.BreathingPattern.BOX
) {
    val context = LocalContext.current
    val viewModel: BoxBreathingViewModel = viewModel()

    val phase by viewModel.phase.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val timeRemaining by viewModel.timeRemaining.collectAsState()
    val instruction by viewModel.instruction.collectAsState()
    val isComplete by viewModel.isComplete.collectAsState()

    // Initialize TTS and haptics when component is created
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    // Start breathing exercise with the specified pattern
    LaunchedEffect(Unit) {
        viewModel.startBreathingExercise(customDurationSeconds, pattern)
    }

    // Track duration for saving
    var exerciseDuration by remember { mutableStateOf(60) }
    LaunchedEffect(customDurationSeconds) {
        exerciseDuration = customDurationSeconds ?: 60
    }

    // Handle completion and save mood data
    LaunchedEffect(isComplete) {
        if (isComplete) {
            // Save breathing exercise data to Firebase
            saveBreathingExerciseData(userId, stressLevel, exerciseDuration, context)

            delay(2000) // Show completion message for 2 seconds
            onComplete()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            // Square animation
            Box(
                modifier = Modifier.size(200.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedSquare(phase = phase, progress = progress)
            }

            // Instructions
            Text(
                text = instruction,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )

            // Time remaining
            if (!isComplete) {
                Text(
                    text = timeRemaining,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            // Completion message
            if (isComplete) {
                Text(
                    text = "You're back in control ❤️",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AnimatedSquare(
    phase: BoxBreathingViewModel.BreathingPhase,
    progress: Float
) {
    val squareSize by animateFloatAsState(
        targetValue = when (phase) {
            BoxBreathingViewModel.BreathingPhase.INHALE,
            BoxBreathingViewModel.BreathingPhase.EXHALE -> 1f + (progress * 0.5f)
            BoxBreathingViewModel.BreathingPhase.HOLD_IN,
            BoxBreathingViewModel.BreathingPhase.HOLD_OUT -> 1.5f
            BoxBreathingViewModel.BreathingPhase.COMPLETE -> 1f
        },
        animationSpec = tween(durationMillis = 100),
        label = "squareSize"
    )

    val squareColor = when (phase) {
        BoxBreathingViewModel.BreathingPhase.INHALE -> Color(0xFF4CAF50) // Green
        BoxBreathingViewModel.BreathingPhase.HOLD_IN -> Color(0xFFFF9800) // Orange
        BoxBreathingViewModel.BreathingPhase.EXHALE -> Color(0xFF2196F3) // Blue
        BoxBreathingViewModel.BreathingPhase.HOLD_OUT -> Color(0xFFFF9800) // Orange
        BoxBreathingViewModel.BreathingPhase.COMPLETE -> Color(0xFFE91E63) // Pink
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val size = Size(100f * squareSize, 100f * squareSize)
        val offset = Offset(
            (size.width / 2),
            (size.height / 2)
        )

        drawRect(
            color = squareColor,
            topLeft = Offset(
                center.x - size.width / 2,
                center.y - size.height / 2
            ),
            size = size,
            style = Stroke(width = 8f)
        )
    }
}

private fun saveBreathingExerciseData(userId: String, stressLevel: Int, durationSeconds: Int, context: Context) {
    // Save breathing exercise log
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val repository = FirebaseRepository.getInstance()
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            
            val breathingLog = HealthLog.BreathingExerciseLog(
                durationSeconds = durationSeconds,
                exerciseType = "breathing_exercise",
                stressLevelBefore = stressLevel,
                stressLevelAfter = when (stressLevel) {
                    in 1..3 -> stressLevel // Low stress stays low
                    in 4..6 -> (stressLevel - 1).coerceAtLeast(1) // Medium stress improves
                    in 7..10 -> (stressLevel - 2).coerceAtLeast(1) // High stress improves more
                    else -> stressLevel
                }
            )
            
            repository.saveHealthLog(userId, today, breathingLog)
        } catch (e: Exception) {
            // Handle error silently - don't interrupt user experience
            e.printStackTrace()
        }
    }
}
