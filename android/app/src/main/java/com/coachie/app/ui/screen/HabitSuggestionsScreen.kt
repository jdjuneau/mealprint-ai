package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.components.RemainingCountBadge
import com.coachie.app.ui.components.UpgradePromptDialog
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.viewmodel.HabitSuggestionsViewModel
import com.coachie.app.viewmodel.HabitSuggestion
import com.coachie.app.viewmodel.HabitDifficulty
import com.coachie.app.data.SubscriptionService
import com.coachie.app.data.model.SubscriptionTier
import com.coachie.app.data.model.AIFeature
import com.coachie.app.util.AuthUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitSuggestionsScreen(
    onBack: () -> Unit,
    onCreateHabit: (String) -> Unit,
    onNavigateToSubscription: () -> Unit = {},
    viewModel: HabitSuggestionsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val coroutineScope = rememberCoroutineScope()
    val userId = AuthUtils.getAuthenticatedUserId() ?: ""
    
    // Subscription state
    var subscriptionTier by remember { mutableStateOf<SubscriptionTier?>(null) }
    var remainingSuggestions by remember { mutableStateOf<Int?>(null) }
    var showUpgradeDialog by remember { mutableStateOf(false) }
    
    // Load subscription info
    LaunchedEffect(userId) {
        if (userId.isNotBlank()) {
            coroutineScope.launch {
                subscriptionTier = SubscriptionService.getUserTier(userId)
                if (subscriptionTier == SubscriptionTier.FREE) {
                    val remaining = SubscriptionService.getRemainingAICalls(userId, AIFeature.HABIT_SUGGESTIONS)
                    remainingSuggestions = remaining
                    // Show upgrade prompt if limit reached
                    if (remaining <= 0) {
                        showUpgradeDialog = true
                    }
                } else {
                    remainingSuggestions = Int.MAX_VALUE
                }
            }
        }
    }
    
    // Refresh remaining count when suggestions are generated
    LaunchedEffect(uiState.suggestions.size) {
        if (userId.isNotBlank() && subscriptionTier == SubscriptionTier.FREE && uiState.suggestions.isNotEmpty()) {
            coroutineScope.launch {
                remainingSuggestions = SubscriptionService.getRemainingAICalls(userId, AIFeature.HABIT_SUGGESTIONS)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Habit Suggestions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBackground)
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                CoachieCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🤖 AI-Powered Recommendations",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Based on your behavioral profile, Coachie has analyzed thousands of habit success patterns to recommend the best habits for you.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // Show remaining suggestions for free tier
                        if (subscriptionTier == SubscriptionTier.FREE && remainingSuggestions != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            RemainingCountBadge(
                                remaining = remainingSuggestions!!,
                                total = 5,
                                featureName = "suggestions this week",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Loading state
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Analyzing your profile...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    // Suggestions list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.suggestions) { suggestion ->
                            SuggestionCard(
                                suggestion = suggestion,
                                onClick = { viewModel.selectSuggestion(suggestion) }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }

            // Suggestion details dialog
            if (uiState.showSuggestionDetails && uiState.selectedSuggestion != null) {
                SuggestionDetailsDialog(
                    suggestion = uiState.selectedSuggestion!!,
                    onDismiss = { viewModel.dismissSuggestionDetails() },
                    onCreateHabit = { suggestion ->
                        viewModel.createHabitFromSuggestion(suggestion) { habitId ->
                            viewModel.dismissSuggestionDetails()
                            onCreateHabit(habitId)
                        }
                    },
                    isLoading = uiState.isLoading
                )
            }

            // Upgrade dialog
            if (showUpgradeDialog) {
                UpgradePromptDialog(
                    onDismiss = { showUpgradeDialog = false },
                    onUpgrade = {
                        showUpgradeDialog = false
                        onNavigateToSubscription()
                    },
                    featureName = "AI Habit Suggestions",
                    remainingCalls = remainingSuggestions
                )
            }

            // Error snackbar
            val context = LocalContext.current
            uiState.error?.let { error ->
                LaunchedEffect(error) {
                    // Show error snackbar
                    android.widget.Toast.makeText(
                        context,
                        error,
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    viewModel.clearError()
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: HabitSuggestion,
    onClick: () -> Unit
) {
    CoachieCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = if (suggestion.isRecommended)
            CoachieCardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        else
            CoachieCardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with name and recommended badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = suggestion.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (suggestion.isRecommended) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = "Recommended",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Text(
                        text = suggestion.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Success probability
                Surface(
                    color = when {
                        suggestion.successProbability >= 80 -> MaterialTheme.colorScheme.primaryContainer
                        suggestion.successProbability >= 60 -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.errorContainer
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${suggestion.successProbability}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            suggestion.successProbability >= 80 -> MaterialTheme.colorScheme.onPrimaryContainer
                            suggestion.successProbability >= 60 -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onErrorContainer
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Details row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Category
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = suggestion.category.name.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Frequency
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = suggestion.frequency.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Frequency",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Time commitment
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = suggestion.timeCommitment,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Time",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Difficulty
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when (suggestion.difficulty) {
                            HabitDifficulty.EASY -> "🟢 Easy"
                            HabitDifficulty.MEDIUM -> "🟡 Medium"
                            HabitDifficulty.HARD -> "🔴 Hard"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Difficulty",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Tap to see details
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap for details and to add this habit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionDetailsDialog(
    suggestion: HabitSuggestion,
    onDismiss: () -> Unit,
    onCreateHabit: (HabitSuggestion) -> Unit,
    isLoading: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = suggestion.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (suggestion.isRecommended) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Recommended",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Description
                Text(
                    text = suggestion.description,
                    style = MaterialTheme.typography.bodyLarge
                )

                // Rationale
                CoachieCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "🧠 Why this habit works for you:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = suggestion.rationale,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Expected impact
                CoachieCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "🎯 Expected Impact:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = suggestion.expectedImpact,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Success metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Success Rate",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${suggestion.successProbability}%",
                            style = MaterialTheme.typography.headlineSmall,
                            color = when {
                                suggestion.successProbability >= 80 -> MaterialTheme.colorScheme.primary
                                suggestion.successProbability >= 60 -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Time Commitment",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = suggestion.timeCommitment,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Difficulty",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = when (suggestion.difficulty) {
                                HabitDifficulty.EASY -> "🟢 Easy"
                                HabitDifficulty.MEDIUM -> "🟡 Medium"
                                HabitDifficulty.HARD -> "🔴 Hard"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                // Prerequisites (if any)
                if (suggestion.prerequisites.isNotEmpty()) {
                    CoachieCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "📋 Prerequisites:",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            suggestion.prerequisites.forEach { prereq ->
                                Text(
                                    text = "• $prereq",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreateHabit(suggestion) },
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Filled.Add, contentDescription = "Add")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isLoading) "Adding..." else "Add This Habit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
