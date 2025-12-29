package com.coachie.app.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.core.*
import com.coachie.app.ui.theme.LocalGender
import com.coachie.app.ui.theme.SemanticColorCategory
import com.coachie.app.ui.theme.getSemanticColorPrimary
import com.coachie.app.ui.theme.rememberCoachieGradient
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.data.model.HealthLog
import com.coachie.app.viewmodel.JournalFlowViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalFlowScreen(
    onNavigateBack: () -> Unit,
    habitId: String? = null,
    viewModel: JournalFlowViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val gender = LocalGender.current
    val isMale = gender.lowercase() == "male"
    val accentColor = getSemanticColorPrimary(SemanticColorCategory.WELLNESS, isMale)
    val gradientBackground = rememberCoachieGradient(endY = 2000f)
    
    // Gender-aware text colors
    val textColor = if (isMale) Color(0xFF1F2937) else Color(0xFF1F2937)
    val secondaryTextColor = if (isMale) Color(0xFF374151) else Color(0xFF374151)

    // Get current user
    val currentUser = FirebaseAuth.getInstance().currentUser

    // ViewModel states
    val prompts by viewModel.prompts.collectAsState()
    val conversation by viewModel.conversation.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isGeneratingResponse by viewModel.isGeneratingResponse.collectAsState()
    val currentInput by viewModel.currentInput.collectAsState()
    val saveSuccessMessage by viewModel.saveSuccessMessage.collectAsState()
    val saveError by viewModel.saveError.collectAsState()

    // Initialize view model
    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { uid ->
            viewModel.initialize(uid)
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(conversation.size) {
        if (conversation.isNotEmpty()) {
            delay(100) // Small delay to ensure UI is updated
            listState.animateScrollToItem(conversation.size - 1)
        }
    }

    // Show success/error messages
    LaunchedEffect(saveSuccessMessage) {
        saveSuccessMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSaveMessage()
        }
    }

    LaunchedEffect(saveError) {
        saveError?.let { error ->
            snackbarHostState.showSnackbar("Error: $error", duration = SnackbarDuration.Long)
            viewModel.clearSaveMessage()
        }
    }

    // Determine time of day for greeting
    val currentHour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val timeOfDay = when (currentHour) {
        in 5..11 -> "Morning"
        in 12..16 -> "Afternoon"
        in 17..20 -> "Evening"
        else -> "Night"
    }
    val greeting = when (currentHour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else -> "Good night"
    }
    val greetingEmoji = when (currentHour) {
        in 5..11 -> "☀️"
        in 12..16 -> "🌤️"
        in 17..20 -> "🌙"
        else -> "🌙"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$timeOfDay Journal", color = textColor) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Back",
                            tint = textColor
                        )
                    }
                },
                actions = {
                    if (conversation.isNotEmpty()) {
                        TextButton(onClick = { viewModel.completeJournal(habitId) }) {
                            Text("Complete", color = accentColor)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = textColor,
                    navigationIconContentColor = textColor
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBackground)
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            // Prompts Section
            if (prompts.isNotEmpty()) {
                PromptsSection(
                    prompts = prompts,
                    accentColor = accentColor,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor
                )
            }

            // Chat Section
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(conversation) { message ->
                    ChatMessageItem(
                        message = message,
                        accentColor = accentColor,
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor
                    )
                }

                // Loading indicator for AI response
                if (isGeneratingResponse) {
                    item {
                        CoachieTypingIndicator(accentColor = accentColor)
                    }
                }

                // Welcome message if no conversation yet
                if (conversation.isEmpty() && !isLoading && prompts.isNotEmpty()) {
                    item {
                        WelcomeMessage(
                            greeting = greeting, 
                            greetingEmoji = greetingEmoji,
                            accentColor = accentColor,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor
                        )
                    }
                }
            }

            // Input Section
            MessageInputSection(
                input = currentInput,
                onInputChange = { viewModel.updateInput(it) },
                onSendMessage = {
                    viewModel.sendMessage(it)
                    focusManager.clearFocus()
                },
                enabled = !isGeneratingResponse,
                accentColor = accentColor
            )
        }
        }
    }
}

@Composable
private fun PromptsSection(
    prompts: List<String>,
    accentColor: Color,
    textColor: Color,
    secondaryTextColor: Color
) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Today's Reflection Prompts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            prompts.forEachIndexed { index, prompt ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Share whatever feels right - Coachie is here to listen.",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: HealthLog.ChatMessage,
    accentColor: Color,
    textColor: Color,
    secondaryTextColor: Color
) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(horizontal = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) accentColor else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Role indicator for coachie messages
                if (!isUser) {
                    Text(
                        text = "Coachie",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) Color.White else textColor,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2f
                )
            }
        }
    }
}

@Composable
private fun CoachieTypingIndicator(
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 120.dp)
                .padding(horizontal = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 4.dp,
                bottomEnd = 16.dp
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Coachie",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )

                // Animated typing dots
                TypingDotsAnimation()
            }
        }
    }
}

@Composable
private fun TypingDotsAnimation() {
    val dotCount = 3
    val animationSpec = infiniteRepeatable<Float>(
        animation = tween(durationMillis = 1000, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
    )

    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(dotCount) { index ->
            val delay = index * 200L
            val alpha by animateFloatAsState(
                targetValue = if (System.currentTimeMillis() % 1000 > delay) 1f else 0.3f,
                animationSpec = tween(durationMillis = 200),
                label = "dot_$index"
            )

            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = alpha))
            )
        }
    }
}

@Composable
private fun WelcomeMessage(
    greeting: String,
    greetingEmoji: String,
    accentColor: Color,
    textColor: Color,
    secondaryTextColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$greetingEmoji $greeting",
                    style = MaterialTheme.typography.headlineSmall,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "I'm here to listen without judgment. What would you like to reflect on today?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageInputSection(
    input: String,
    onInputChange: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    enabled: Boolean,
    accentColor: Color
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Share what's on your mind...") },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (input.trim().isNotEmpty()) {
                            onSendMessage(input.trim())
                        }
                    }
                ),
                enabled = enabled,
                maxLines = 4,
                shape = RoundedCornerShape(24.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            FloatingActionButton(
                onClick = {
                    if (input.trim().isNotEmpty()) {
                        onSendMessage(input.trim())
                    }
                },
                modifier = Modifier.size(48.dp),
                containerColor = if (input.trim().isNotEmpty() && enabled) accentColor else Color.Gray,
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "Send message"
                )
            }
        }
    }
}
