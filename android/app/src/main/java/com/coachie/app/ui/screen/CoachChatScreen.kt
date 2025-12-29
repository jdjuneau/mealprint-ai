package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.theme.Primary40
import com.coachie.app.ui.theme.Tertiary40
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import android.speech.SpeechRecognizer
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.content.Intent
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.local.PreferencesManager
import com.coachie.app.data.model.ChatMessage
import java.util.*
import com.coachie.app.ui.components.ConfettiAnimation
import com.coachie.app.ui.components.UpgradePromptDialog
import com.coachie.app.ui.components.RemainingCountBadge
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.viewmodel.CoachChatViewModel
import com.coachie.app.data.model.SubscriptionTier
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachChatScreen(
    userId: String? = null,
    preferencesManager: PreferencesManager? = null,
    showConfetti: Boolean = false,
    onConfettiShown: () -> Unit = {},
    onNavigateToSubscription: () -> Unit = {},
    viewModel: CoachChatViewModel = viewModel(
        factory = CoachChatViewModel.Factory(
            firebaseRepository = FirebaseRepository.getInstance(),
            preferencesManager = preferencesManager ?: PreferencesManager(androidx.compose.ui.platform.LocalContext.current),
            userId = (userId?.takeIf { it.isNotBlank() } ?: com.coachie.app.util.AuthUtils.getAuthenticatedUserId()) ?: "anonymous_user",
            context = androidx.compose.ui.platform.LocalContext.current
        )
    )
) {
    val messages by viewModel.messages.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val remainingChats by viewModel.remainingChats.collectAsState()
    val subscriptionTier by viewModel.subscriptionTier.collectAsState()
    val showUpgradePrompt by viewModel.showUpgradePrompt.collectAsState()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var confettiVisible by remember { mutableStateOf(false) }

    // Voice functionality
    val context = LocalContext.current

    // Initialize preferences manager
    val preferencesManager = remember { PreferencesManager(context) }

    var isListening by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var textToSpeech: TextToSpeech? by remember { mutableStateOf(null) }
    var voiceEnabled by remember { mutableStateOf(preferencesManager.voiceEnabled) }

    // Show confetti when requested
    LaunchedEffect(showConfetti) {
        if (showConfetti && !confettiVisible) {
            confettiVisible = true
            // Hide confetti after animation
            kotlinx.coroutines.delay(2000)
            confettiVisible = false
            onConfettiShown()
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    // Show error in snackbar
    LaunchedEffect(error) {
        error?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
                viewModel.clearError()
            }
        }
    }

    // Voice functions
    fun startVoiceListening() {
        android.util.Log.d("VoiceDebug", "startVoiceListening called")
        if (isListening) {
            android.util.Log.d("VoiceDebug", "Already listening, returning")
            return
        }

        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        android.util.Log.d("VoiceDebug", "SpeechRecognizer created: ${speechRecognizer != null}")
        if (speechRecognizer == null) {
            android.util.Log.d("VoiceDebug", "SpeechRecognizer is null, showing error")
            scope.launch {
                snackbarHostState.showSnackbar("Speech recognition not available on this device")
            }
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message to Coachie")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                isListening = true
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                isListening = false
                val errorMessage = when (error) {
                    android.speech.SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                    android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                    else -> "Speech recognition error"
                }
                scope.launch {
                    snackbarHostState.showSnackbar(errorMessage)
                }
            }

            override fun onResults(results: android.os.Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = matches?.firstOrNull()
                if (!recognizedText.isNullOrBlank()) {
                    viewModel.updateInputText(recognizedText)
                    // Auto-send if voice command
                    if (!isLoading) {
                        viewModel.sendMessage(recognizedText)
                    }
                }
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })

        speechRecognizer.startListening(intent)
    }

    fun speakText(text: String) {
        if (!voiceEnabled || textToSpeech == null) return

        isSpeaking = true
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "coachie_response")
        textToSpeech?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onError(utteranceId: String?) {
                isSpeaking = false
            }
            override fun onDone(utteranceId: String?) {
                isSpeaking = false
            }
        })
    }

    // Permission launcher for microphone
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceListening()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Microphone permission required for voice input")
            }
        }
    }

    fun toggleVoiceInput() {
        android.util.Log.d("VoiceDebug", "toggleVoiceInput called")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            android.util.Log.d("VoiceDebug", "Requesting microphone permission")
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            android.util.Log.d("VoiceDebug", "Permission granted, starting voice listening")
            startVoiceListening()
        }
    }

    LaunchedEffect(Unit) {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Apply saved voice settings
                preferencesManager.voiceLanguage?.let { voiceName ->
                    textToSpeech?.voices?.find { it.name == voiceName }?.let { voice ->
                        textToSpeech?.voice = voice
                    }
                } ?: run {
                    textToSpeech?.language = Locale.getDefault()
                }

                textToSpeech?.setPitch(preferencesManager.voicePitch)
                textToSpeech?.setSpeechRate(preferencesManager.voiceRate)
            }
        }
    }

    // Cleanup TTS on dispose
    DisposableEffect(Unit) {
        onDispose {
            textToSpeech?.shutdown()
        }
    }

    // DISABLED: Auto-speak bot messages (voice disabled in chat bot)
    // LaunchedEffect(messages) {
    //     val lastMessage = messages.lastOrNull()
    //     if (lastMessage?.isFromUser == false && voiceEnabled) {
    //         // Small delay to ensure UI is updated
    //         kotlinx.coroutines.delay(500)
    //         speakText(lastMessage.content)
    //     }
    // }

    val gradientBackground = rememberCoachieGradient(endY = 1600f)

    Box(modifier = Modifier
        .fillMaxSize()
        .background(gradientBackground)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                ChatInputBar(
                    inputText = inputText,
                    onInputChange = { viewModel.updateInputText(it) },
                    onSendMessage = { viewModel.sendMessage(it) },
                    isLoading = isLoading,
                    isListening = isListening,
                    isSpeaking = isSpeaking,
                    voiceEnabled = voiceEnabled,
                    onToggleVoiceInput = { toggleVoiceInput() },
                    onToggleVoiceOutput = { voiceEnabled = !voiceEnabled },
                    onSpeakLastMessage = {
                        messages.lastOrNull { !it.isFromUser }?.content?.let { speakText(it) }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Header
                ChatHeader()

                // Show remaining chats for free tier
                if (subscriptionTier == SubscriptionTier.FREE && remainingChats != null) {
                    RemainingCountBadge(
                        remaining = remainingChats!!,
                        total = 10,
                        featureName = "messages",
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth()
                    )
                }

                // Messages list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(messages) { message ->
                        ChatMessageItem(message = message)
                    }

                    // Loading indicator when AI is responding
                    if (isLoading) {
                        item {
                            LoadingMessageItem()
                        }
                    }
                }

                // Empty state when no messages
                if (messages.isEmpty() && !isLoading) {
                    EmptyChatState(
                        onQuestionClick = { question ->
                            viewModel.sendMessage(question)
                        }
                    )
                }
            }
        }
        
        // Confetti overlay
        ConfettiAnimation(visible = confettiVisible)
        
        // Upgrade dialog
        if (showUpgradePrompt) {
            UpgradePromptDialog(
                onDismiss = { viewModel.dismissUpgradePrompt() },
                onUpgrade = {
                    viewModel.dismissUpgradePrompt()
                    onNavigateToSubscription()
                },
                featureName = "AI Coach Chat",
                remainingCalls = remainingChats
            )
        }
    }
}

@Composable
private fun ChatHeader() {
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "🤖",
                fontSize = 24.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Coachie",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Primary40
                )
                Text(
                    text = "Your AI Fitness Coach",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChatMessageItem(message: ChatMessage) {
    val isUserMessage = message.isFromUser

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUserMessage) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(vertical = 4.dp)
        ) {
            // Message bubble using CoachieCard
            CoachieCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CoachieCardDefaults.colors(
                    containerColor = if (isUserMessage) {
                        Primary40
                    } else {
                        Color.White.copy(alpha = 0.95f)
                    }
                ),
                shape = RoundedCornerShape(
                    topStart = if (isUserMessage) 16.dp else 4.dp,
                    topEnd = if (isUserMessage) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                applyDefaultBorder = !isUserMessage
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Daily nudge indicator
                    if (message.messageType == ChatMessage.MessageType.DAILY_NUDGE) {
                        Text(
                            text = "🌅 Daily Motivation",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUserMessage) {
                                Color.White.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            },
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    // Message content
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isUserMessage) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        lineHeight = 20.sp
                    )
                }
            }

            // Timestamp
            Text(
                text = message.getFormattedTime(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isUserMessage) {
                    Color.White.copy(alpha = 0.8f) // Light text for user messages on colored background
                } else {
                    MaterialTheme.colorScheme.onSurface // Dark text for incoming messages on white background
                },
                modifier = Modifier
                    .align(if (isUserMessage) Alignment.End else Alignment.Start)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun LoadingMessageItem() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        CoachieCard(
            modifier = Modifier.widthIn(max = 200.dp),
            colors = CoachieCardDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.95f)
            ),
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Coachie is typing",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Primary40
                )
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    isLoading: Boolean,
    isListening: Boolean = false,
    isSpeaking: Boolean = false,
    voiceEnabled: Boolean = true,
    onToggleVoiceInput: () -> Unit = {},
    onToggleVoiceOutput: () -> Unit = {},
    onSpeakLastMessage: () -> Unit = {}
) {
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.95f)
        )
    ) {
        // Main input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Voice input button (IconButton instead of FAB to save space)
            IconButton(
                onClick = onToggleVoiceInput,
                modifier = Modifier.size(40.dp),
                enabled = !isLoading
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = if (isListening) "Stop listening" else "Voice input",
                    tint = if (isListening) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Primary40
                    }
                )
            }

            // Text input field
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                placeholder = { Text("Ask Coachie anything about fitness...") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                enabled = !isLoading && !isListening,
                shape = RoundedCornerShape(24.dp),
                trailingIcon = {
                    // Voice output toggle and speak last message combined in trailing icon
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Speak last message button (only show if voice is enabled)
                        if (voiceEnabled) {
                            IconButton(
                                onClick = onSpeakLastMessage,
                                enabled = !isSpeaking,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.VolumeUp,
                                    contentDescription = "Speak last response",
                                    tint = if (isSpeaking) MaterialTheme.colorScheme.onSurfaceVariant else Primary40,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        
                        // Voice output toggle (only show if there are messages to speak)
                        IconButton(
                            onClick = onToggleVoiceOutput,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VolumeUp,
                                contentDescription = if (voiceEnabled) "Voice output enabled - tap to disable" else "Voice output disabled - tap to enable",
                                tint = if (voiceEnabled) Primary40 else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary40,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Send button (IconButton instead of FAB to save space and prevent blocking)
            IconButton(
                onClick = {
                    if (inputText.trim().isNotEmpty() && !isLoading && !isListening) {
                        onSendMessage(inputText)
                    }
                },
                enabled = inputText.trim().isNotEmpty() && !isLoading && !isListening,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send message",
                    tint = if (inputText.trim().isNotEmpty() && !isLoading && !isListening) {
                        Primary40
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyChatState(
    onQuestionClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🤖",
            fontSize = 64.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "Welcome to Coachie Chat!",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Ask me anything about fitness, nutrition, or motivation. I'm here to help you reach your goals!",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Suggested questions
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            SuggestedQuestion(
                question = "How can I stay motivated?",
                onClick = { onQuestionClick("How can I stay motivated?") }
            )
            SuggestedQuestion(
                question = "What's a good workout plan?",
                onClick = { onQuestionClick("What's a good workout plan?") }
            )
            SuggestedQuestion(
                question = "How should I track my progress?",
                onClick = { onQuestionClick("How should I track my progress?") }
            )
        }
    }
}

@Composable
private fun SuggestedQuestion(
    question: String,
    onClick: () -> Unit
) {
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CoachieCardDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.8f)
        )
    ) {
        Text(
            text = question,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(12.dp),
            color = Primary40
        )
    }
}