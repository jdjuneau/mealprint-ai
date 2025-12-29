package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.data.model.Circle
import com.mealprint.ai.ui.components.CoachieCard
import com.mealprint.ai.ui.components.CoachieCardDefaults
import com.mealprint.ai.ui.components.UpgradePromptDialog
import com.mealprint.ai.ui.theme.Accent40
import com.mealprint.ai.ui.theme.rememberCoachieGradient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleJoinScreen(
    onNavigateBack: () -> Unit,
    onJoinSuccess: () -> Unit,
    userId: String,
    userGoal: String? = null, // Optional: pass user's goal from profile
    userTendency: String? = null, // Optional: pass user's tendency (e.g., from FourTendencies)
    onNavigateToSubscription: () -> Unit = {} // Navigation callback for upgrade prompt
) {
    val repository = FirebaseRepository.getInstance()
    var matchingCircles by remember { mutableStateOf<List<Circle>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var joiningCircleId by remember { mutableStateOf<String?>(null) }
    var showJoinDialog by remember { mutableStateOf<Circle?>(null) }
    var showUpgradeDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val gradientBackground = rememberCoachieGradient(endY = 1600f)

    // Auto-match circles based on goal and tendency
    LaunchedEffect(userId, userGoal, userTendency) {
        isLoading = true
        errorMessage = null
        
        // If no goal provided, try to get from user profile
        val goal = userGoal ?: run {
            val profileResult = repository.getUserProfile(userId)
            profileResult.getOrNull()?.let { profile ->
                // Try to extract goal from profile - you may need to adjust this based on your data structure
                // For now, we'll use a default or prompt user
                null
            }
        }
        
        if (goal != null) {
            val result = repository.findMatchingCircles(
                goal = goal,
                tendency = userTendency,
                excludeUserId = userId
            )
            result.onSuccess {
                matchingCircles = it
                isLoading = false
            }.onFailure {
                errorMessage = it.message
                isLoading = false
            }
        } else {
            // No goal found - show goal selection UI
            isLoading = false
        }
    }

    fun handleJoinCircle(circle: Circle) {
        scope.launch {
            joiningCircleId = circle.id
            val result = repository.joinCircle(circle.id, userId)
            joiningCircleId = null
            result.onSuccess {
                showJoinDialog = null
                onJoinSuccess()
            }.onFailure {
                // Check if error is about circle limit
                if (it.message?.contains("Circle limit reached", ignoreCase = true) == true) {
                    showUpgradeDialog = true
                    showJoinDialog = null
                } else {
                    errorMessage = it.message
                    showJoinDialog = null
                }
            }
        }
    }

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
                                tint = Accent40
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Find a Circle",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Accent40
                        )
                    }
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(
                    top = 16.dp,
                    bottom = 16.dp
                )
            ) {
                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (errorMessage != null) {
                    item {
                        CoachieCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CoachieCardDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = "Error: $errorMessage",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                } else if (matchingCircles.isEmpty()) {
                    item {
                        EmptyMatchingCirclesState()
                    }
                } else {
                    item {
                        Text(
                            "Matching Circles",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    items(matchingCircles) { circle ->
                        MatchingCircleCard(
                            circle = circle,
                            isJoining = joiningCircleId == circle.id,
                            onJoinClick = { showJoinDialog = circle }
                        )
                    }
                }
            }
        }
    }

    // Join confirmation dialog
    showJoinDialog?.let { circle ->
        AlertDialog(
            onDismissRequest = { showJoinDialog = null },
            title = { Text("Join Circle?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Join ${circle.maxMembers}-person '${circle.goal}' circle?")
                    Text(
                        text = "Circle: ${circle.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Members: ${circle.members.size}/${circle.maxMembers}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { handleJoinCircle(circle) },
                    enabled = joiningCircleId == null
                ) {
                    if (joiningCircleId == circle.id) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Join")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Upgrade prompt dialog for circle limit
    if (showUpgradeDialog) {
        UpgradePromptDialog(
            onDismiss = { showUpgradeDialog = false },
            onUpgrade = {
                showUpgradeDialog = false
                onNavigateToSubscription()
            },
            featureName = "Unlimited Circles",
            remainingCalls = null
        )
    }
}

@Composable
fun MatchingCircleCard(
    circle: Circle,
    isJoining: Boolean,
    onJoinClick: () -> Unit
) {
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onJoinClick,
        enabled = !isJoining,
        colors = CoachieCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
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
                        text = circle.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = circle.goal,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Filled.Group,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${circle.members.size}/${circle.maxMembers}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (circle.tendency != null) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "Tendency: ${circle.tendency}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            if (circle.streak > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "🔥",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${circle.streak} day streak",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            if (isJoining) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun EmptyMatchingCirclesState() {
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🔍",
                style = MaterialTheme.typography.displayLarge
            )
            Text(
                text = "No Matching Circles",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "We couldn't find any circles matching your goals. Check back later or create your own!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

