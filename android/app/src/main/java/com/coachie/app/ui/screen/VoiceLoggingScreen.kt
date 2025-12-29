package com.coachie.app.ui.screen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coachie.app.ui.theme.Primary40
import com.coachie.app.ui.theme.rememberCoachieGradient
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.HealthLog
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.util.VoiceCommandParser
import com.coachie.app.util.VoiceCommandResult
import com.coachie.app.viewmodel.VoiceLoggingViewModel
import kotlinx.coroutines.launch
import java.util.*

// Display components for parsed results - defined before use
@Composable
fun ParsedMealDisplay(meal: VoiceCommandParser.ParsedMealCommand) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("🍽️ Meal Log", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

        meal.mealType?.let {
            Text("Type: ${it.capitalize()}", style = MaterialTheme.typography.bodyMedium)
        }

        if (meal.foods.isNotEmpty()) {
            Text("Foods:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            meal.foods.forEach { food ->
                Text("• ${food.quantity ?: ""} ${food.unit ?: ""} ${food.name}".trim(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 16.dp))
            }
        }

        meal.totalCalories?.let {
            Text("Estimated calories: $it", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ParsedSupplementDisplay(supplement: VoiceCommandParser.ParsedSupplementCommand) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("💊 Supplement Log", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text("Name: ${supplement.supplementName}", style = MaterialTheme.typography.bodyMedium)
        supplement.quantity?.let {
            Text("Quantity: $it", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ParsedWorkoutDisplay(workout: VoiceCommandParser.ParsedWorkoutCommand) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("🏃 Workout Log", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text("Type: ${workout.workoutType}", style = MaterialTheme.typography.bodyMedium)
        workout.durationMinutes?.let {
            Text("Duration: $it minutes", style = MaterialTheme.typography.bodyMedium)
        }
        workout.distance?.let {
            Text("Distance: $it ${workout.distanceUnit}", style = MaterialTheme.typography.bodyMedium)
        }
        workout.caloriesBurned?.let {
            Text("Calories burned: $it", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ParsedWaterDisplay(water: VoiceCommandParser.ParsedWaterCommand, useImperial: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("💧 Water Log", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            text = if (useImperial) {
                val waterOz = (water.amount * 0.033814).roundToInt()
                "Amount: $waterOz fl oz"
            } else {
                "Amount: ${water.amount} ml (${water.unit})"
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun ParsedWeightDisplay(weight: VoiceCommandParser.ParsedWeightCommand) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("⚖️ Weight Log", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text("Weight: ${weight.weight} ${weight.unit}", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ParsedSleepDisplay(sleep: VoiceCommandParser.ParsedSleepCommand) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("😴 Sleep Log", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        sleep.hours?.let {
            Text("Duration: ${String.format("%.1f", it)} hours", style = MaterialTheme.typography.bodyMedium)
        }
        sleep.quality?.let {
            Text("Quality: ${it.capitalize()}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ParsedMoodDisplay(mood: VoiceCommandParser.ParsedMoodCommand) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("😊 Mood Log", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text("Level: ${mood.level}/5", style = MaterialTheme.typography.bodyMedium)
        if (mood.emotions.isNotEmpty()) {
            Text("Emotions: ${mood.emotions.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ParsedMeditationDisplay(meditation: VoiceCommandParser.ParsedMeditationCommand) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("🧘 Meditation Log", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text("Duration: ${meditation.durationMinutes} minutes", style = MaterialTheme.typography.bodyMedium)
        Text("Type: ${meditation.meditationType.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ParsedHabitDisplay(habit: VoiceCommandParser.ParsedHabitCommand) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("✅ Habit Completion", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text("Habit: ${habit.habitName}", style = MaterialTheme.typography.bodyMedium)
        habit.notes?.let {
            Text("Notes: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ParsedJournalDisplay(journal: VoiceCommandParser.ParsedJournalCommand) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("📔 Journal Entry", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text("Content: ${journal.content}", style = MaterialTheme.typography.bodyMedium)
        journal.mood?.let {
            Text("Mood: ${it.replaceFirstChar { char -> char.uppercase() }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceLoggingScreen(
    userId: String,
    onNavigateBack: () -> Unit
) {
    android.util.Log.d("VoiceLogging", "=========================================")
    android.util.Log.d("VoiceLogging", "VOICE LOGGING SCREEN COMPOSED")
    android.util.Log.d("VoiceLogging", "=========================================")
    android.util.Log.d("VoiceLogging", "User ID: $userId")

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val firebaseRepository = FirebaseRepository()

    val viewModel: VoiceLoggingViewModel = viewModel()

    // User preferences
    var useImperial by remember { mutableStateOf(true) }
    
    LaunchedEffect(userId) {
        try {
            val goalsResult = firebaseRepository.getUserGoals(userId)
            val goals = goalsResult.getOrNull()
            useImperial = goals?.get("useImperial") as? Boolean ?: true
        } catch (e: Exception) {
            useImperial = true // Default to imperial
        }
    }

    // Voice recognition state
    var isListening by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf("") }
    var parsedResult by remember { mutableStateOf<VoiceCommandResult?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Text-to-Speech
    var textToSpeech by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }

    // Speech Recognizer
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    android.util.Log.d("VoiceLogging", "Initial state - isListening: $isListening, isProcessing: $isProcessing, recognizedText: \"$recognizedText\", errorMessage: $errorMessage")
    android.util.Log.d("VoiceLogging", "TTS ready: $isTtsReady, Speech recognizer exists: ${speechRecognizer != null}")

    fun speakText(text: String) {
        if (isTtsReady && textToSpeech != null) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    // Define voice listening function
    fun startVoiceListening() {
        android.util.Log.d("VoiceLogging", "=========================================")
        android.util.Log.d("VoiceLogging", "START VOICE LISTENING")
        android.util.Log.d("VoiceLogging", "=========================================")

        if (speechRecognizer == null) {
            android.util.Log.e("VoiceLogging", "Speech recognizer is null, cannot start listening")
            errorMessage = "Speech recognition not initialized"
            return
        }

        android.util.Log.d("VoiceLogging", "Creating speech recognition intent")
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say what you want to log...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }

        android.util.Log.d("VoiceLogging", "Setting up recognition listener")
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                android.util.Log.d("VoiceLogging", "onReadyForSpeech: Ready for speech input")
                android.util.Log.d("VoiceLogging", "Setting isListening = true, errorMessage = null")
                isListening = true
                errorMessage = null
            }

            override fun onBeginningOfSpeech() {
                android.util.Log.d("VoiceLogging", "onBeginningOfSpeech: Speech detected")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Only log occasionally to avoid spam
                if (android.util.Log.isLoggable("VoiceLogging", android.util.Log.DEBUG)) {
                    android.util.Log.d("VoiceLogging", "onRmsChanged: $rmsdB")
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                android.util.Log.d("VoiceLogging", "onBufferReceived: Audio buffer received")
            }

            override fun onEndOfSpeech() {
                android.util.Log.d("VoiceLogging", "onEndOfSpeech: Speech input ended")
                android.util.Log.d("VoiceLogging", "Setting isListening = false")
                isListening = false
            }

            override fun onError(error: Int) {
                android.util.Log.e("VoiceLogging", "onError: Speech recognition error code: $error")
                android.util.Log.d("VoiceLogging", "Setting isListening = false due to error")
                isListening = false
                errorMessage = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Please try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout. Please try again."
                    SpeechRecognizer.ERROR_NETWORK -> "Network error. Check your connection."
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout. Check your connection."
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                    SpeechRecognizer.ERROR_CLIENT -> "Client error. Please restart the app."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions for speech recognition."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Please wait."
                    SpeechRecognizer.ERROR_SERVER -> "Server error. Please try again later."
                    else -> "Speech recognition error ($error). Please try again."
                }
                android.util.Log.d("VoiceLogging", "Error message set: $errorMessage")
            }

            override fun onResults(results: Bundle?) {
                android.util.Log.d("VoiceLogging", "onResults: Speech recognition completed")
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                android.util.Log.d("VoiceLogging", "Recognition results: ${matches?.size ?: 0} matches")

                if (!matches.isNullOrEmpty()) {
                    recognizedText = matches[0]
                    android.util.Log.d("VoiceLogging", "Recognized text: \"$recognizedText\"")
                    isProcessing = true

                    // Parse the command
                    coroutineScope.launch {
                        try {
                            android.util.Log.d("VoiceLogging", "Parsing command: \"$recognizedText\"")
                            val parser = VoiceCommandParser()
                            parsedResult = parser.parseCommand(recognizedText)
                            android.util.Log.d("VoiceLogging", "Command parsed successfully")
                            speakText("Command recognized. Please confirm to save.")
                        } catch (e: Exception) {
                            android.util.Log.e("VoiceLogging", "Command parsing failed: ${e.message}")
                            errorMessage = "Failed to parse command: ${e.message}"
                        } finally {
                            isProcessing = false
                        }
                    }
                } else {
                    android.util.Log.w("VoiceLogging", "No recognition results received")
                    errorMessage = "No speech was recognized. Please try again."
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (partial?.isNotEmpty() == true) {
                    android.util.Log.d("VoiceLogging", "Partial result: \"${partial[0]}\"")
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                android.util.Log.d("VoiceLogging", "onEvent: type=$eventType")
            }
        })

        android.util.Log.d("VoiceLogging", "Starting speech recognition")
        try {
            speechRecognizer?.startListening(intent)
            android.util.Log.d("VoiceLogging", "Speech recognition started successfully")
        } catch (e: Exception) {
            android.util.Log.e("VoiceLogging", "Failed to start speech recognition: ${e.message}")
            errorMessage = "Failed to start speech recognition: ${e.message}"
        }
        android.util.Log.d("VoiceLogging", "=========================================")
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        android.util.Log.d("VoiceLogging", "Permission launcher result: granted=$granted")
        if (granted) {
            android.util.Log.d("VoiceLogging", "Permission granted, starting voice listening")
            startVoiceListening()
        } else {
            android.util.Log.w("VoiceLogging", "Permission denied by user")
            errorMessage = "Microphone permission is required for voice logging"
        }
    }

    // Initialize TTS
    LaunchedEffect(Unit) {
        android.util.Log.d("VoiceLogging", "Initializing Text-to-Speech")
        textToSpeech = TextToSpeech(context) { status ->
            android.util.Log.d("VoiceLogging", "TTS initialization status: $status")
            isTtsReady = status == TextToSpeech.SUCCESS
            android.util.Log.d("VoiceLogging", "TTS ready: $isTtsReady")
        }
    }

    // Initialize Speech Recognizer - use remember to persist across recompositions
    val speechRecognizerInstance = remember {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                SpeechRecognizer.createSpeechRecognizer(context)
            } else {
                android.util.Log.e("VoiceLogging", "Speech recognition not available on this device")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("VoiceLogging", "Exception creating speech recognizer: ${e.message}")
            null
        }
    }
    
    LaunchedEffect(Unit) {
        android.util.Log.d("VoiceLogging", "Setting speech recognizer instance")
        speechRecognizer = speechRecognizerInstance
        if (speechRecognizerInstance == null) {
            errorMessage = "Speech recognition not available on this device"
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            android.util.Log.d("VoiceLogging", "Cleaning up speech recognizer and TTS")
            speechRecognizerInstance?.destroy()
            textToSpeech?.shutdown()
        }
    }

    fun confirmAndSave() {
        if (parsedResult == null) return

        coroutineScope.launch {
            try {
                isProcessing = true
                val success = viewModel.saveParsedCommand(userId, parsedResult!!)

                if (success) {
                    speakText("Entry saved successfully!")
                    // Reset state
                    recognizedText = ""
                    parsedResult = null
                    errorMessage = null
                    // CRITICAL: Trigger dashboard refresh by navigating back
                    // The dashboard will refresh on resume
                    onNavigateBack()
                } else {
                    errorMessage = "Failed to save entry. Please try again."
                    speakText("Failed to save entry")
                }
            } catch (e: Exception) {
                errorMessage = "Error saving entry: ${e.message}"
                speakText("Error occurred while saving")
            } finally {
                isProcessing = false
            }
        }
    }

    fun checkPermissionAndStartListening() {
        android.util.Log.d("VoiceLogging", "=========================================")
        android.util.Log.d("VoiceLogging", "CHECK PERMISSION AND START LISTENING")
        android.util.Log.d("VoiceLogging", "=========================================")
        android.util.Log.d("VoiceLogging", "Current state: isListening=$isListening, isProcessing=$isProcessing")
        android.util.Log.d("VoiceLogging", "Speech recognizer available: ${SpeechRecognizer.isRecognitionAvailable(context)}")
        android.util.Log.d("VoiceLogging", "Speech recognizer instance: ${speechRecognizer != null}")

        if (isListening) {
            android.util.Log.d("VoiceLogging", "Already listening, ignoring button press")
            return
        }

        if (isProcessing) {
            android.util.Log.d("VoiceLogging", "Already processing, ignoring button press")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            android.util.Log.e("VoiceLogging", "Speech recognition not available on this device")
            errorMessage = "Speech recognition is not available on this device"
            return
        }

        if (speechRecognizer == null) {
            android.util.Log.e("VoiceLogging", "Speech recognizer is null")
            errorMessage = "Speech recognition not initialized. Please restart the app."
            return
        }

        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        
        android.util.Log.d("VoiceLogging", "Permission check: hasPermission=$hasPermission")

        when {
            hasPermission -> {
                android.util.Log.d("VoiceLogging", "Permission granted, starting voice listening")
                errorMessage = null
                startVoiceListening()
            }
            else -> {
                android.util.Log.d("VoiceLogging", "Requesting microphone permission")
                errorMessage = null
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        android.util.Log.d("VoiceLogging", "=========================================")
    }

    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBackground)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CoachieCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CoachieCardDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.95f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                "Back",
                                tint = Primary40
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Voice Logging",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Primary40
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Voice input section
                CoachieCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CoachieCardDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Voice Logging",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "Say what you want to log, like:\n• \"Log breakfast: 2 eggs and toast\"\n• \"Add Vitamin D supplement\"\n• \"Log 30 minute run\"\n• \"Log 16 ounces of water\"",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Voice button
                        FloatingActionButton(
                            onClick = {
                                if (!isProcessing && !isListening) {
                                    android.util.Log.d("VoiceLogging", "Microphone button pressed")
                                    checkPermissionAndStartListening()
                                }
                            },
                            modifier = Modifier.size(120.dp),
                            containerColor = if (isListening) {
                                MaterialTheme.colorScheme.error
                            } else if (isProcessing) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                Primary40
                            }
                        ) {
                            if (isListening) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = Color.White,
                                    strokeWidth = 4.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = "Start voice input",
                                    tint = Color.White,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }

                        if (isListening) {
                            Text(
                                text = "Listening...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (isProcessing) {
                            CircularProgressIndicator()
                            Text(
                                text = "Processing...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Results section
                if (recognizedText.isNotEmpty()) {
                    CoachieCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CoachieCardDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Recognized Speech",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "\"$recognizedText\"",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(12.dp)
                            )

                            when (val result = parsedResult) {
                                is VoiceCommandResult.MealCommand -> {
                                    ParsedMealDisplay(result.parsedMeal)
                                }
                                is VoiceCommandResult.SupplementCommand -> {
                                    ParsedSupplementDisplay(result.parsedSupplement)
                                }
                                is VoiceCommandResult.WorkoutCommand -> {
                                    ParsedWorkoutDisplay(result.parsedWorkout)
                                }
                                is VoiceCommandResult.WaterCommand -> {
                                    ParsedWaterDisplay(result.parsedWater, useImperial)
                                }
                                is VoiceCommandResult.WeightCommand -> {
                                    ParsedWeightDisplay(result.parsedWeight)
                                }
                                is VoiceCommandResult.SleepCommand -> {
                                    ParsedSleepDisplay(result.parsedSleep)
                                }
                                is VoiceCommandResult.MoodCommand -> {
                                    ParsedMoodDisplay(result.parsedMood)
                                }
                                is VoiceCommandResult.MeditationCommand -> {
                                    ParsedMeditationDisplay(result.parsedMeditation)
                                }
                                is VoiceCommandResult.HabitCommand -> {
                                    ParsedHabitDisplay(result.parsedHabit)
                                }
                                is VoiceCommandResult.JournalCommand -> {
                                    ParsedJournalDisplay(result.parsedJournal)
                                }
                                is VoiceCommandResult.ParseError -> {
                                    Text(
                                        text = "❌ ${result.errorMessage}",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                is VoiceCommandResult.Unknown -> {
                                    Text(
                                        text = "❓ Could not understand this command. Try saying something like \"Log breakfast\" or \"Add supplement\"",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                null -> {
                                    if (!isProcessing) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        Text("Analyzing command...")
                                    }
                                }
                            }

                            // Error message
                            errorMessage?.let {
                                Text(
                                    text = "❌ $it",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Confirm button
                if (parsedResult != null && parsedResult !is VoiceCommandResult.ParseError && parsedResult !is VoiceCommandResult.Unknown) {
                    Button(
                        onClick = { confirmAndSave() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary40
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Confirm & Save", color = Color.White)
                    }
                }
            }
            }
        }
    }
}

