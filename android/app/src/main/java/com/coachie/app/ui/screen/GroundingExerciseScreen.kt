package com.coachie.app.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.viewmodel.GroundingExerciseViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class GroundingPhase(
    val number: Int,
    val instruction: String,
    val description: String,
    val progress: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroundingExerciseScreen(
    onNavigateBack: () -> Unit,
    viewModel: GroundingExerciseViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val currentPhase by viewModel.currentPhase.collectAsState()
    val timeRemaining by viewModel.timeRemaining.collectAsState()
    val isActive by viewModel.isActive.collectAsState()
    val isComplete by viewModel.isComplete.collectAsState()

    // Initialize ViewModel
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    // Auto-start the exercise
    LaunchedEffect(Unit) {
        delay(1000) // Brief pause before starting
        viewModel.startExercise()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2D5F4F), // Deep teal
                        Color(0xFF1A3D33), // Darker teal
                        Color(0xFF0F2920)  // Very dark teal
                    ),
                    center = Offset(0.5f, 0.5f),
                    radius = 1.0f
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Grounding Exercise",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isComplete -> {
                        GroundingCompletionScreen(onNavigateBack)
                    }
                    isActive -> {
                        currentPhase?.let { phase ->
                            ActiveExerciseScreen(
                                currentPhase = phase,
                                timeRemaining = timeRemaining
                            )
                        }
                    }
                    else -> {
                        StartingScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun StartingScreen() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Let's ground ourselves",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "We'll use the 5-4-3-2-1 technique",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        CircularProgressIndicator(
            color = Color.White,
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
fun ActiveExerciseScreen(
    currentPhase: GroundingPhase,
    timeRemaining: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_scale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        // Main grounding circle
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer glow circle
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = glowAlpha),
                    radius = size.minDimension / 2,
                    center = center
                )
            }

            // Breathing circle
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(breathingScale)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.9f),
                                Color.White.copy(alpha = 0.6f)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentPhase.number.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2D5F4F)
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Phase instruction
        Text(
            text = currentPhase.instruction,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Phase description
        Text(
            text = currentPhase.description,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Time remaining
        Text(
            text = timeRemaining,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Progress indicator
        LinearProgressIndicator(
            progress = { currentPhase.progress },
            modifier = Modifier
                .width(200.dp)
                .height(4.dp),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.3f)
        )
    }
}

@Composable
fun GroundingCompletionScreen(onNavigateBack: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "completion")

    val heartScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heart_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        // Heart emoji animation
        Text(
            text = "💙",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.scale(heartScale)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Well done!",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "You're back in the present moment",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onNavigateBack,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.9f),
                contentColor = Color(0xFF2D5F4F)
            )
        ) {
            Text(
                text = "Return to Coachie",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
