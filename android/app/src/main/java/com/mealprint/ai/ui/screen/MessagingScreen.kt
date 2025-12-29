package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.ui.components.CoachieCard
import com.mealprint.ai.ui.components.CoachieCardDefaults
import com.mealprint.ai.ui.theme.rememberCoachieGradient
import com.mealprint.ai.viewmodel.MessagingViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagingScreen(
    onNavigateBack: () -> Unit,
    onNavigateToConversation: (String) -> Unit, // conversationId or userId
    userId: String,
    conversationId: String? = null, // If provided, show this conversation
    viewModel: MessagingViewModel = viewModel(
        factory = MessagingViewModel.Factory(
            repository = FirebaseRepository.getInstance(),
            currentUserId = userId
        )
    )
) {
    val conversations by viewModel.conversations.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val currentConversationId by viewModel.currentConversationId.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val listState = rememberLazyListState()

    // Track if conversationId is actually a userId (for new conversations)
    var actualReceiverId by remember { mutableStateOf<String?>(null) }
    
    // Load conversation if conversationId is provided
    // If it's a userId, we need to generate the conversation ID
    LaunchedEffect(conversationId) {
        conversationId?.let { id ->
            // Check if it's a userId (not a conversation ID format)
            // Conversation IDs are in format: "userId1_userId2" (sorted)
            val isUserId = !id.contains("_") || id.split("_").size != 2
            if (isUserId) {
                // It's a userId, generate conversation ID
                val sorted = listOf(userId, id).sorted()
                val convId = "${sorted[0]}_${sorted[1]}"
                actualReceiverId = id // Store for ConversationView
                viewModel.loadMessages(convId)
            } else {
                // It's already a conversation ID
                actualReceiverId = null // Will be determined from messages
                viewModel.loadMessages(id)
            }
        }
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (currentConversationId != null) "Messages" else "Conversations",
                        color = Color.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentConversationId != null) {
                            // Clear current conversation to go back to list
                            viewModel.clearCurrentConversation()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.Black,
                    navigationIconContentColor = Color.Black
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
            if (currentConversationId != null) {
                // Show conversation view
                ConversationView(
                    messages = messages,
                    userId = userId,
                    repository = FirebaseRepository.getInstance(),
                    onSendMessage = { receiverId, content ->
                        viewModel.sendMessage(receiverId, content)
                    },
                    listState = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    initialReceiverId = actualReceiverId // Pass receiverId for new conversations
                )
            } else {
                // Show conversations list
                ConversationsListView(
                    conversations = conversations,
                    userId = userId,
                    repository = FirebaseRepository.getInstance(),
                    onConversationClick = { convId ->
                        viewModel.loadMessages(convId)
                    },
                    isLoading = isLoading,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            error?.let { errorMessage ->
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.95f)
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Error",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD32F2F) // Dark red for error title
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF1F2937) // Dark gray for readable text
                            )
                        }
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(
                                Icons.Filled.Clear, 
                                contentDescription = "Dismiss",
                                tint = Color(0xFF1F2937) // Dark gray for icon
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationsListView(
    conversations: List<com.coachie.app.data.model.Conversation>,
    userId: String,
    repository: FirebaseRepository,
    onConversationClick: (String) -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    var userProfiles by remember { mutableStateOf<Map<String, com.coachie.app.data.model.PublicUserProfile>>(emptyMap()) }
    
    LaunchedEffect(conversations) {
        val profiles = mutableMapOf<String, com.coachie.app.data.model.PublicUserProfile>()
        conversations.forEach { conversation ->
            val otherUserId = conversation.getOtherParticipant(userId)
            otherUserId?.let { otherId ->
                repository.getUserProfile(otherId).fold(
                    onSuccess = { profile ->
                        profile?.let {
                            profiles[otherId] = com.coachie.app.data.model.PublicUserProfile(
                                uid = otherId,
                                username = it.username ?: "",
                                displayName = it.name,
                                photoUrl = null
                            )
                        }
                    },
                    onFailure = {}
                )
            }
        }
        userProfiles = profiles
    }

    if (isLoading) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (conversations.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "No conversations yet",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Start a conversation with a friend",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(conversations) { conversation ->
                val otherUserId = conversation.getOtherParticipant(userId)
                val profile = otherUserId?.let { userProfiles[it] }
                val unreadCount = conversation.getUnreadCount(userId)
                
                CoachieCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onConversationClick(conversation.id) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile?.displayName ?: "Unknown User",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F2937) // Dark gray for readability
                            )
                            profile?.username?.let {
                                Text(
                                    text = "@$it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF374151) // Medium gray for secondary text
                                )
                            }
                            conversation.lastMessage?.let {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF4B5563), // Lighter gray for preview text
                                    maxLines = 1
                                )
                            }
                        }
                        if (unreadCount > 0) {
                            Badge {
                                Text(unreadCount.toString())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationView(
    messages: List<com.coachie.app.data.model.Message>,
    userId: String,
    repository: FirebaseRepository,
    onSendMessage: (String, String) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
    initialReceiverId: String? = null // For new conversations where there are no messages yet
) {
    var messageText by remember { mutableStateOf("") }
    var receiverId by remember { mutableStateOf<String?>(initialReceiverId) }
    var receiverProfile by remember { mutableStateOf<com.coachie.app.data.model.PublicUserProfile?>(null) }
    
    // Get receiver ID from first message OR use initialReceiverId if provided
    LaunchedEffect(messages, initialReceiverId) {
        val targetReceiverId = if (receiverId != null) {
            receiverId
        } else if (initialReceiverId != null) {
            initialReceiverId
        } else if (messages.isNotEmpty()) {
            val firstMessage = messages.first()
            val otherId = if (firstMessage.senderId == userId) {
                firstMessage.receiverId
            } else {
                firstMessage.senderId
            }
            otherId
        } else {
            null
        }
        
        if (targetReceiverId != null && targetReceiverId != receiverId) {
            receiverId = targetReceiverId
            
            repository.getUserProfile(targetReceiverId).fold(
                onSuccess = { profile ->
                    profile?.let {
                        receiverProfile = com.coachie.app.data.model.PublicUserProfile(
                            uid = targetReceiverId,
                            username = it.username ?: "",
                            displayName = it.name,
                            photoUrl = null
                        )
                    }
                },
                onFailure = { }
            )
        }
    }

    Column(
        modifier = modifier
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            reverseLayout = false
        ) {
            items(messages) { message ->
                val isSent = message.senderId == userId
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        modifier = Modifier.widthIn(max = 280.dp),
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isSent) 16.dp else 4.dp,
                            bottomEnd = if (isSent) 4.dp else 16.dp
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = message.content,
                                color = if (isSent) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            message.createdAt?.let {
                                Text(
                                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(it),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSent) {
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    },
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Message input - ALWAYS VISIBLE with send button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(24.dp)
            )
            
            // Send button - ALWAYS VISIBLE, enabled only when there's text and a receiver
            val currentReceiverId = receiverId
            val hasText = messageText.isNotBlank()
            val hasReceiver = currentReceiverId != null && currentReceiverId.isNotBlank()
            val isEnabled = hasText && hasReceiver
            
            // Send button - standard send button with icon
            IconButton(
                onClick = {
                    if (isEnabled) {
                        val receiver = receiverId
                        if (receiver != null && receiver.isNotBlank() && messageText.isNotBlank()) {
                            try {
                                onSendMessage(receiver, messageText.trim())
                                messageText = ""
                            } catch (e: Exception) {
                                android.util.Log.e("MessagingScreen", "Error sending message: ${e.message}", e)
                            }
                        }
                    } else {
                        // Show feedback if button is clicked but disabled
                        android.util.Log.d("MessagingScreen", "Send button clicked but disabled - hasText: $hasText, hasReceiver: $hasReceiver, receiverId: $currentReceiverId")
                    }
                },
                enabled = isEnabled,
                modifier = Modifier
                    .size(48.dp)
                    .then(
                        if (!isEnabled) {
                            Modifier.alpha(0.5f) // Make it semi-transparent when disabled
                        } else {
                            Modifier
                        }
                    )
            ) {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = "Send",
                    modifier = Modifier.size(28.dp),
                    tint = if (isEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

