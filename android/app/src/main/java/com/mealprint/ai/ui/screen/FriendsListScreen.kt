package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.ui.components.CoachieCard
import com.mealprint.ai.ui.components.CoachieCardDefaults
import com.mealprint.ai.ui.theme.rememberCoachieGradient
import com.mealprint.ai.viewmodel.FriendsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FriendsListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToUserSearch: () -> Unit,
    onNavigateToMessage: (String) -> Unit, // userId
    userId: String,
    viewModel: FriendsViewModel = viewModel(
        factory = FriendsViewModel.Factory(
            repository = FirebaseRepository.getInstance(),
            currentUserId = userId
        )
    )
) {
    val friends by viewModel.friends.collectAsState()
    val pendingRequests by viewModel.pendingRequests.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    
    // Refresh requests when screen becomes visible
    LaunchedEffect(Unit) {
        viewModel.loadPendingRequests()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Friends", color = Color.Black) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToUserSearch) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = "Add Friend", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.Black,
                    navigationIconContentColor = Color.Black,
                    actionIconContentColor = Color.Black
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
            ) {
                // Tab row
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(0)
                            }
                        },
                        text = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Friends", color = MaterialTheme.colorScheme.onSurface)
                                if (friends.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Badge {
                                        Text(friends.size.toString())
                                    }
                                }
                            }
                        }
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(1)
                            }
                        },
                        text = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Requests", color = MaterialTheme.colorScheme.onSurface)
                                if (pendingRequests.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Badge {
                                        Text(pendingRequests.size.toString())
                                    }
                                }
                            }
                        }
                    )
                }

                error?.let {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            IconButton(onClick = { viewModel.clearError() }) {
                                Icon(Icons.Filled.Close, contentDescription = "Dismiss")
                            }
                        }
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> FriendsTab(
                            friends = friends,
                            isLoading = isLoading,
                            onMessageClick = onNavigateToMessage,
                            onRemoveFriend = { friendId ->
                                viewModel.removeFriend(friendId)
                            }
                        )
                        1 -> FriendRequestsTab(
                            requests = pendingRequests,
                            currentUserId = userId,
                            repository = FirebaseRepository.getInstance(),
                            onAccept = { requestId ->
                                viewModel.acceptFriendRequest(requestId)
                            },
                            onReject = { requestId ->
                                viewModel.rejectFriendRequest(requestId)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendsTab(
    friends: List<com.coachie.app.data.model.PublicUserProfile>,
    isLoading: Boolean,
    onMessageClick: (String) -> Unit,
    onRemoveFriend: (String) -> Unit
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (friends.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.People,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "No friends yet",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Search for users to add them as friends",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(friends) { friend ->
                CoachieCard(
                    modifier = Modifier.fillMaxWidth()
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
                                text = friend.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "@${friend.username}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability on light card
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { onMessageClick(friend.uid) }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Message,
                                    contentDescription = "Message",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { onRemoveFriend(friend.uid) }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PersonRemove,
                                    contentDescription = "Remove Friend",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendRequestsTab(
    requests: List<com.coachie.app.data.model.FriendRequest>,
    currentUserId: String,
    repository: FirebaseRepository,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit
) {
    var userProfiles by remember { mutableStateOf<Map<String, com.coachie.app.data.model.PublicUserProfile>>(emptyMap()) }
    
    LaunchedEffect(requests) {
        val profiles = mutableMapOf<String, com.coachie.app.data.model.PublicUserProfile>()
        requests.forEach { request ->
            // Determine which user to load profile for
            // If you're the recipient (toUserId), show the sender's profile
            // If you're the sender (fromUserId), show the recipient's profile
            val otherUserId = if (request.toUserId == currentUserId) {
                request.fromUserId // Incoming request - show sender
            } else {
                request.toUserId // Outgoing request - show recipient
            }
            
            repository.getUserProfile(otherUserId).fold(
                onSuccess = { profile ->
                    profile?.let {
                        profiles[otherUserId] = com.coachie.app.data.model.PublicUserProfile(
                            uid = otherUserId,
                            username = it.username ?: "",
                            displayName = it.name,
                            photoUrl = null
                        )
                    }
                },
                onFailure = {}
            )
        }
        userProfiles = profiles
    }
    
    // Separate incoming and outgoing requests
    val incomingRequests = requests.filter { it.toUserId == currentUserId }
    val outgoingRequests = requests.filter { it.fromUserId == currentUserId }

    if (requests.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "No pending requests",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Incoming requests section
            if (incomingRequests.isNotEmpty()) {
                item {
                    Text(
                        text = "Incoming Requests",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(incomingRequests) { request ->
                    val otherUserId = request.fromUserId
                    val profile = userProfiles[otherUserId]
                    FriendRequestCard(
                        request = request,
                        profile = profile,
                        isOutgoing = false,
                        onAccept = { onAccept(request.id) },
                        onReject = { onReject(request.id) }
                    )
                }
            }
            
            // Outgoing requests section
            if (outgoingRequests.isNotEmpty()) {
                item {
                    Text(
                        text = "Sent Requests",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(outgoingRequests) { request ->
                    val otherUserId = request.toUserId
                    val profile = userProfiles[otherUserId]
                    FriendRequestCard(
                        request = request,
                        profile = profile,
                        isOutgoing = true,
                        onAccept = null,
                        onReject = null
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendRequestCard(
    request: com.coachie.app.data.model.FriendRequest,
    profile: com.coachie.app.data.model.PublicUserProfile?,
    isOutgoing: Boolean,
    onAccept: (() -> Unit)?,
    onReject: (() -> Unit)?
) {
    // Check if this is a circle invite
    val isCircleInvite = request.message?.contains("invited to join the circle:", ignoreCase = true) == true
    val circleName = if (isCircleInvite) {
        val match = Regex("circle:\\s*([^\\n]+)", RegexOption.IGNORE_CASE).find(request.message ?: "")
        match?.groupValues?.get(1)?.trim()
    } else null
    
    CoachieCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Circle invite indicator
            if (isCircleInvite && !isOutgoing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Circle Invitation",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile?.displayName ?: "Unknown User",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    profile?.username?.let {
                        Text(
                            text = "@$it",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isCircleInvite && circleName != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Invited you to join: $circleName",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Accept to join the circle and become friends",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic
                        )
                    } else if (request.message != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = request.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isOutgoing) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Waiting for response...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }
            if (!isOutgoing && onAccept != null && onReject != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onAccept,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Accept")
                    }
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("Reject")
                    }
                }
            }
        }
    }
}

