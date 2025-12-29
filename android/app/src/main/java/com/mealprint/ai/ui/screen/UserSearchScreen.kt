package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.ui.components.CoachieCard
import com.mealprint.ai.ui.components.CoachieCardDefaults
import com.mealprint.ai.ui.theme.rememberCoachieGradient
import com.mealprint.ai.viewmodel.UserSearchViewModel
import com.mealprint.ai.viewmodel.FriendsViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSearchScreen(
    onNavigateBack: () -> Unit,
    onUserSelected: (String) -> Unit = {}, // Callback when user is selected (for invite/message)
    onInviteToCircle: ((String) -> Unit)? = null, // Optional: if provided, show invite option
    userId: String,
    circleId: String? = null, // Optional: circleId for invites
    viewModel: UserSearchViewModel = viewModel(
        factory = UserSearchViewModel.Factory(
            repository = FirebaseRepository.getInstance(),
            currentUserId = userId
        )
    ),
    friendsViewModel: FriendsViewModel = viewModel(
        factory = FriendsViewModel.Factory(
            repository = FirebaseRepository.getInstance(),
            currentUserId = userId
        )
    )
) {
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val repository = remember { FirebaseRepository.getInstance() }
    // Track which action is selected for each user
    var selectedActions by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Search Users", color = Color.Black) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { 
                        searchQuery = it
                        if (it.isNotBlank()) {
                            viewModel.searchUsers(it)
                        } else {
                            viewModel.clearSearch()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search by name or username...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                error?.let {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(16.dp),
                            color = Color.Black
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (searchResults.isEmpty() && searchQuery.isNotBlank()) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No users found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Black
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchResults) { user ->
                            CoachieCard(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = user.displayName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "@${user.username}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val selectedAction = selectedActions[user.uid] ?: ""
                                        val isAddFriendSelected = selectedAction == "addFriend"
                                        val isInviteSelected = selectedAction == "invite"
                                        
                                        OutlinedButton(
                                            onClick = { 
                                                selectedActions = selectedActions + (user.uid to "addFriend")
                                                friendsViewModel.sendFriendRequest(user.uid)
                                                onUserSelected(user.uid)
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (isAddFriendSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                contentColor = if (isAddFriendSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                        ) {
                                            Text("Add Friend")
                                        }
                                        if (onInviteToCircle != null || circleId != null) {
                                            OutlinedButton(
                                                onClick = { 
                                                    selectedActions = selectedActions + (user.uid to "invite")
                                                    android.util.Log.d("UserSearchScreen", "Invite to Circle clicked for user: ${user.uid}")
                                                    scope.launch {
                                                        if (circleId != null) {
                                                            // Handle invite internally with proper feedback
                                                            try {
                                                                snackbarHostState.showSnackbar(
                                                                    message = "Sending invitation to ${user.displayName}...",
                                                                    duration = SnackbarDuration.Short
                                                                )
                                                                val result = repository.inviteUserToCircle(circleId, userId, user.uid)
                                                                result.onSuccess {
                                                                    snackbarHostState.showSnackbar(
                                                                        message = "Invitation sent to ${user.displayName}!",
                                                                        duration = SnackbarDuration.Short
                                                                    )
                                                                    delay(1500)
                                                                    onNavigateBack()
                                                                }.onFailure { exception ->
                                                                    snackbarHostState.showSnackbar(
                                                                        message = "Failed to send invitation: ${exception.message ?: "Unknown error"}",
                                                                        duration = SnackbarDuration.Long
                                                                    )
                                                                }
                                                            } catch (e: Exception) {
                                                                snackbarHostState.showSnackbar(
                                                                    message = "Failed to send invitation: ${e.message ?: "Unknown error"}",
                                                                    duration = SnackbarDuration.Long
                                                                )
                                                            }
                                                        } else {
                                                            // Use callback if provided
                                                            onInviteToCircle?.invoke(user.uid)
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    containerColor = if (isInviteSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                    contentColor = if (isInviteSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                                )
                                            ) {
                                                Text("Invite to Circle")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

