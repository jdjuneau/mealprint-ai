package com.coachie.app.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.ui.theme.LocalGender
import com.coachie.app.ui.theme.SemanticColorCategory
import com.coachie.app.ui.theme.getSemanticColorPrimary
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.viewmodel.MeditationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeditationScreen(
    onNavigateBack: () -> Unit,
    userId: String,
    initialDuration: Int? = null,
    habitId: String? = null,
    viewModel: MeditationViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val timeRemaining by viewModel.timeRemaining.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val timeRemainingFormatted by viewModel.timeRemainingFormatted.collectAsState()
    val isActive by viewModel.isActive.collectAsState()
    val isPreparing by viewModel.isPreparing.collectAsState()
    val isCompleted by viewModel.isCompleted.collectAsState()

    var selectedDuration by remember { mutableStateOf(initialDuration ?: 10) }
    var selectedType by remember { mutableStateOf(if (initialDuration != null) "guided" else "guided") }
    var showMoodDialog by remember { mutableStateOf(false) }

    // Initialize ViewModel
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
        // Set meditation type to guided by default
        viewModel.setMeditationType("guided")
    }
    
    // Start meditation immediately if initial duration is provided
    LaunchedEffect(initialDuration) {
        initialDuration?.let { duration ->
            viewModel.setMeditationType("guided")
            viewModel.startMeditation(duration)
        }
    }

    val gradientBackground = rememberCoachieGradient(endY = 2000f)
    
    val gender = com.coachie.app.ui.theme.LocalGender.current
    val isMale = gender.lowercase() == "male"
    val textColor = if (isMale) Color(0xFF1F2937) else Color(0xFF1F2937)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meditation", color = textColor) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textColor)
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
                .background(brush = gradientBackground)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (state) {
                    MeditationViewModel.MeditationState.IDLE -> {
                        // Setup screen
                        MeditationSetupScreen(
                            selectedDuration = selectedDuration,
                            onDurationChange = { newDuration -> selectedDuration = newDuration },
                            selectedType = selectedType,
                            onTypeChange = { newType -> 
                                selectedType = newType
                                viewModel.setMeditationType(newType)
                            },
                            onStartMeditation = {
                                viewModel.startMeditation(selectedDuration)
                            }
                        )
                    }

                    MeditationViewModel.MeditationState.PREPARING -> {
                        // Preparation screen
                        MeditationPreparationScreen()
                    }

                    MeditationViewModel.MeditationState.MEDITATING -> {
                        // Active meditation screen
                        val currentInstruction by viewModel.currentInstruction.collectAsState()
                        MeditationActiveScreen(
                            timeRemaining = timeRemainingFormatted,
                            progress = progress,
                            currentInstruction = currentInstruction,
                            onStop = { viewModel.stopMeditation() }
                        )
                    }

                    MeditationViewModel.MeditationState.COMPLETED -> {
                        // Completion screen
                        MeditationCompletionScreen(
                            onSaveSession = { notes: String? ->
                                viewModel.saveMeditationSession(userId, notes ?: "", habitId)
                                showMoodDialog = true
                            },
                            onNewSession = {
                                viewModel.reset()
                            }
                        )
                    }
                }
            }
        }
    }

    // Mood tracking dialog after completion
    if (showMoodDialog) {
        MoodTrackingDialog(
            onDismiss = {
                showMoodDialog = false
                viewModel.reset()
                onNavigateBack()
            },
            onSaveMood = { moodBefore: Int, moodAfter: Int, stressBefore: Int, stressAfter: Int ->
                viewModel.setMoodBefore(moodBefore)
                viewModel.setMoodAfter(moodAfter)
                viewModel.setStressBefore(stressBefore)
                viewModel.setStressAfter(stressAfter)
                showMoodDialog = false
                viewModel.reset()
                onNavigateBack()
            }
        )
    }
}

@Composable
fun MeditationSetupScreen(
    selectedDuration: Int,
    onDurationChange: (Int) -> Unit,
    selectedType: String,
    onTypeChange: (String) -> Unit,
    onStartMeditation: () -> Unit
) {
    val gender = LocalGender.current
    val isMale = gender.lowercase() == "male"
    val accentColor = getSemanticColorPrimary(SemanticColorCategory.WELLNESS, isMale)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Choose Your Meditation",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Duration selection
        Text(
            text = "Duration",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val durations = listOf(5, 10, 15, 20, 30)
            durations.forEach { duration ->
                OutlinedButton(
                    onClick = { onDurationChange(duration) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selectedDuration == duration) accentColor.copy(alpha = 0.3f) else Color.Transparent,
                        contentColor = Color.Black
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (selectedDuration == duration) accentColor else Color.Gray.copy(alpha = 0.5f)
                    )
                ) {
                    Text("${duration} min", color = Color.Black)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Type selection
        Text(
            text = "Type",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black
        )

        val types = listOf(
            "guided" to "Guided",
            "silent" to "Silent",
            "mindfulness" to "Mindfulness",
            "body_scan" to "Body Scan"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            types.forEach { (type, displayName) ->
                OutlinedButton(
                    onClick = { onTypeChange(type) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selectedType == type) accentColor.copy(alpha = 0.3f) else Color.Transparent,
                        contentColor = Color.Black
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (selectedType == type) accentColor else Color.Gray.copy(alpha = 0.5f)
                    )
                ) {
                    Text(displayName, color = Color.Black)
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onStartMeditation,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor
            )
        ) {
            Text(
                text = "Begin Meditation",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
fun MeditationPreparationScreen() {
    val gender = LocalGender.current
    val isMale = gender.lowercase() == "male"
    val accentColor = getSemanticColorPrimary(SemanticColorCategory.WELLNESS, isMale)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val textColor = if (isMale) Color(0xFF1F2937) else Color(0xFF1F2937)
        val secondaryTextColor = if (isMale) Color(0xFF374151) else Color(0xFF374151)
        
        Text(
            text = "Prepare to meditate...",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = textColor
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Find a comfortable position.\nClose your eyes and relax.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = secondaryTextColor
        )

        Spacer(modifier = Modifier.height(48.dp))

        CircularProgressIndicator(
            color = accentColor,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Starting in a few moments...",
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryTextColor
        )
    }
}

@Composable
fun MeditationActiveScreen(
    timeRemaining: String,
    progress: Float,
    currentInstruction: String?,
    onStop: () -> Unit
) {
    val gender = LocalGender.current
    val isMale = gender.lowercase() == "male"
    val accentColor = getSemanticColorPrimary(SemanticColorCategory.WELLNESS, isMale)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Progress circle
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
                drawArc(
                    color = accentColor,
                    startAngle = -90f,
                    sweepAngle = progress * 360f,
                    useCenter = false,
                    style = Stroke(width = 12f, cap = StrokeCap.Round)
                )
            }

            val textColor = if (isMale) Color(0xFF1F2937) else Color(0xFF1F2937)
            val secondaryTextColor = if (isMale) Color(0xFF374151) else Color(0xFF374151)
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = timeRemaining,
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

        Spacer(modifier = Modifier.height(64.dp))

        val textColor = if (isMale) Color(0xFF1F2937) else Color(0xFF1F2937)
        val secondaryTextColor = if (isMale) Color(0xFF374151) else Color(0xFF374151)
        
        // Show current guided instruction if available, otherwise show default prompts
        if (currentInstruction != null) {
            Text(
                text = currentInstruction,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = textColor,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        } else {
            Text(
                text = "Focus on your breath",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = textColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Let thoughts come and go like clouds in the sky",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = secondaryTextColor
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        OutlinedButton(
            onClick = onStop,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("End Session")
        }
    }
}

@Composable
fun MeditationCompletionScreen(
    onSaveSession: (String?) -> Unit,
    onNewSession: () -> Unit
) {
    val gender = LocalGender.current
    val isMale = gender.lowercase() == "male"
    val accentColor = getSemanticColorPrimary(SemanticColorCategory.WELLNESS, isMale)
    var notes by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "✨ Well Done! ✨",
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "You've completed your meditation session",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = accentColor
        )

        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Session Notes (optional)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onNewSession,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("New Session")
            }

            Button(
                onClick = { onSaveSession(notes.ifBlank { null }) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor
                )
            ) {
                Text("Save & Exit")
            }
        }
    }
}

@Composable
fun MoodTrackingDialog(
    onDismiss: () -> Unit,
    onSaveMood: (Int, Int, Int, Int) -> Unit
) {
    var moodBefore by remember { mutableStateOf(5f) }
    var moodAfter by remember { mutableStateOf(7f) }
    var stressBefore by remember { mutableStateOf(6f) }
    var stressAfter by remember { mutableStateOf(3f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "How do you feel?",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = "Track your mood and stress levels before and after meditation:",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Mood before
                Text("Mood Before (1-10)", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = moodBefore,
                    onValueChange = { moodBefore = it },
                    valueRange = 1f..10f,
                    steps = 8
                )
                Text("Mood: ${moodBefore.toInt()}/10", style = MaterialTheme.typography.bodySmall)

                Spacer(modifier = Modifier.height(16.dp))

                // Mood after
                Text("Mood After (1-10)", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = moodAfter,
                    onValueChange = { moodAfter = it },
                    valueRange = 1f..10f,
                    steps = 8
                )
                Text("Mood: ${moodAfter.toInt()}/10", style = MaterialTheme.typography.bodySmall)

                Spacer(modifier = Modifier.height(16.dp))

                // Stress before
                Text("Stress Before (1-10)", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = stressBefore,
                    onValueChange = { stressBefore = it },
                    valueRange = 1f..10f,
                    steps = 8
                )
                Text("Stress: ${stressBefore.toInt()}/10", style = MaterialTheme.typography.bodySmall)

                Spacer(modifier = Modifier.height(16.dp))

                // Stress after
                Text("Stress After (1-10)", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = stressAfter,
                    onValueChange = { stressAfter = it },
                    valueRange = 1f..10f,
                    steps = 8
                )
                Text("Stress: ${stressAfter.toInt()}/10", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                onSaveMood(
                    moodBefore.toInt(),
                    moodAfter.toInt(),
                    stressBefore.toInt(),
                    stressAfter.toInt()
                )
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Skip")
            }
        }
    )
}
