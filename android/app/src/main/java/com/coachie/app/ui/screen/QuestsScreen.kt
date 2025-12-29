package com.coachie.app.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.ui.theme.LocalGender
import com.coachie.app.ui.theme.SemanticColorCategory
import com.coachie.app.ui.theme.getSemanticColorPrimary
import com.coachie.app.service.ShareService
import com.coachie.app.service.ShareImageData
import com.coachie.app.service.ShareImageType
import com.coachie.app.ui.components.SharePlatformDialog
import androidx.compose.ui.platform.LocalContext
import com.coachie.app.data.SubscriptionService
import com.coachie.app.data.model.AIFeature
import com.coachie.app.data.model.SubscriptionTier
import com.coachie.app.ui.components.UpgradePromptDialog
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import kotlin.math.min

data class Quest(
    val id: String,
    val title: String,
    val description: String,
    val progress: Int, // 0-100
    val target: Int,
    val current: Int,
    val type: String, // 'habit', 'streak', 'goal', 'challenge'
    val icon: String,
    val color: Color
)

// Generate intelligent fallback quests based on user profile and goals
suspend fun generateIntelligentFallbackQuests(userId: String): List<Quest> {
    try {
        // Try to get user profile and goals for personalization
        val repository = com.coachie.app.data.FirebaseRepository.getInstance()
        val profile = repository.getUserProfile(userId).getOrNull()
        val goals = repository.getUserGoals(userId).getOrNull()

        val quests = mutableListOf<Quest>()

        // Base hydration quest (always relevant)
        quests.add(Quest(
            id = "hydration_quest",
            title = "7-Day Hydration Quest",
            description = "Drink 8 glasses of water daily for 7 days",
            progress = 43,
            target = 7,
            current = 3,
            type = "habit",
            icon = "water_drop",
            color = Color(0xFF3B82F6)
        ))

        // Steps quest based on user's goal
        val dailyStepsGoal = goals?.get("dailySteps") as? Long ?: 10000L
        val stepsTitle = when {
            dailyStepsGoal >= 15000 -> "15K Steps Power Walk"
            dailyStepsGoal >= 12000 -> "12K Steps Challenge"
            else -> "10K Steps Challenge"
        }

        quests.add(Quest(
            id = "steps_quest",
            title = stepsTitle,
            description = "Walk ${dailyStepsGoal.toInt()} steps daily for 5 days",
            progress = 40,
            target = 5,
            current = 2,
            type = "goal",
            icon = "directions_walk",
            color = Color(0xFF10B981)
        ))

        // Meditation quest (always good)
        quests.add(Quest(
            id = "meditation_quest",
            title = "Morning Mindfulness",
            description = "Meditate for 10 minutes each morning for 10 days",
            progress = 20,
            target = 10,
            current = 2,
            type = "habit",
            icon = "self_improvement",
            color = Color(0xFF8B5CF6)
        ))

        // Protein quest based on user's profile
        val isMale = profile?.gender?.lowercase() == "male"
        val proteinGoal = if (isMale) 150 else 120
        val proteinTitle = if (isMale) "Protein Power Quest" else "Protein Balance Quest"

        quests.add(Quest(
            id = "protein_quest",
            title = proteinTitle,
            description = "Eat ${proteinGoal}g of protein daily for 7 days",
            progress = 71,
            target = 7,
            current = 5,
            type = "nutrition",
            icon = "fitness_center",
            color = Color(0xFFF59E0B)
        ))

        // Sleep quest (always important)
        quests.add(Quest(
            id = "sleep_quest",
            title = "Sleep Champion",
            description = "Get 8 hours of sleep for 5 nights",
            progress = 0,
            target = 5,
            current = 0,
            type = "habit",
            icon = "bedtime",
            color = Color(0xFF6366F1)
        ))

        // Weight goal quest if user has weight goals
        if (profile != null && profile.goalWeight != profile.currentWeight) {
            val isLosingWeight = profile.goalWeight < profile.currentWeight
            val direction = if (isLosingWeight) "lose" else "gain"
            val weeklyGoal = if (isLosingWeight) 0.5 else 0.25 // 0.5kg/week loss, 0.25kg/week gain

            quests.add(Quest(
                id = "weight_goal_quest",
                title = "Weekly Weight Goal",
                description = "${direction.replaceFirstChar { it.uppercase() }} ${weeklyGoal}kg this week",
                progress = 60,
                target = 7,
                current = 4,
                type = "goal",
                icon = "monitor_weight",
                color = Color(0xFFEF4444)
            ))
        }

        // Weekly workout quest based on user's goals
        val weeklyWorkouts = goals?.get("weeklyWorkouts") as? Long ?: 4L
        if (weeklyWorkouts > 0) {
            quests.add(Quest(
                id = "workout_quest",
                title = "Weekly Workout Quest",
                description = "Complete ${weeklyWorkouts} workouts this week",
                progress = 50,
                target = weeklyWorkouts.toInt(),
                current = (weeklyWorkouts.toInt() / 2),
                type = "goal",
                icon = "sports_gymnastics",
                color = Color(0xFF06B6D4)
            ))
        }

        android.util.Log.d("Quests", "Generated ${quests.size} intelligent fallback quests for user $userId")
        return quests

    } catch (e: Exception) {
        android.util.Log.e("Quests", "Error generating intelligent quests, using basic fallback", e)
        // Basic fallback if everything fails
        return listOf(
            Quest(
                id = "fallback_1",
                title = "7-Day Hydration Quest",
                description = "Drink 8 glasses of water daily for 7 days",
                progress = 43,
                target = 7,
                current = 3,
                type = "habit",
                icon = "water_drop",
                color = Color(0xFF3B82F6)
            ),
            Quest(
                id = "fallback_2",
                title = "10K Steps Challenge",
                description = "Walk 10,000 steps daily for 5 days",
                progress = 40,
                target = 5,
                current = 2,
                type = "goal",
                icon = "directions_walk",
                color = Color(0xFF10B981)
            ),
            Quest(
                id = "fallback_3",
                title = "Morning Meditation",
                description = "Meditate for 10 minutes each morning for 10 days",
                progress = 20,
                target = 10,
                current = 2,
                type = "habit",
                icon = "self_improvement",
                color = Color(0xFF8B5CF6)
            ),
            Quest(
                id = "fallback_4",
                title = "Protein Power Quest",
                description = "Eat 150g of protein daily for 7 days",
                progress = 71,
                target = 7,
                current = 5,
                type = "nutrition",
                icon = "fitness_center",
                color = Color(0xFFF59E0B)
            ),
            Quest(
                id = "fallback_5",
                title = "Sleep Champion",
                description = "Get 8 hours of sleep for 5 nights",
                progress = 0,
                target = 5,
                current = 0,
                type = "habit",
                icon = "bedtime",
                color = Color(0xFF6366F1)
            )
        )
    }
}

@Composable
fun QuestCard(
    quest: Quest,
    isCompleted: Boolean = false,
    onProgressUpdate: (Int) -> Unit = {}, // Disabled - quests update automatically
    onShare: () -> Unit = {},
    onConvertToCircle: () -> Unit = {}
) {
    // Progress dialog removed - quests update automatically

    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
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
            // Progress Ring - Display only (automatic updates, no manual interaction)
            Box(
                modifier = Modifier
                    .size(80.dp),
                contentAlignment = Alignment.Center
            ) {
                // Add subtle background circle for better visual feedback
                if (!isCompleted) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.size(76.dp)
                    ) {
                        drawCircle(
                            color = quest.color.copy(alpha = 0.1f),
                            radius = size.minDimension / 2
                        )
                    }
                }
                CircularProgressIndicator(
                    progress = { quest.progress / 100f },
                    modifier = Modifier.size(80.dp),
                    color = quest.color,
                    strokeWidth = 8.dp
                )
                Text(
                    text = "${quest.progress}%",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = quest.color // Use quest color for progress text
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = quest.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F2937) // Dark gray for better contrast
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = quest.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF374151) // Dark gray for readability
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${quest.current}/${quest.target}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280) // Medium gray for secondary text
                )
                if (!isCompleted) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "✨ Updates automatically as you complete activities!",
                        style = MaterialTheme.typography.labelSmall,
                        color = quest.color,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Action buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Convert to Circle button (always visible)
                IconButton(onClick = onConvertToCircle) {
                    Icon(
                        imageVector = Icons.Filled.Group,
                        contentDescription = "Convert to Circle",
                        tint = Color(0xFF3B82F6) // Blue for circle/community
                    )
                }
                
                if (isCompleted) {
                    IconButton(onClick = onShare) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share",
                            tint = Color(0xFF6B46C1) // Coachie purple
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Completed",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestsScreen(
    onNavigateBack: () -> Unit = {},
    userId: String,
    onNavigateToCircle: (String) -> Unit = {}, // Navigate to circle detail after creation
    onNavigateToSubscription: () -> Unit = {}
) {
    val context = LocalContext.current
    val shareService = remember { ShareService.getInstance(context) }
    val functions = Firebase.functions
    val coroutineScope = rememberCoroutineScope()
    var quests by remember { mutableStateOf<List<Quest>>(emptyList()) }
    var completedQuests by remember { mutableStateOf<List<Quest>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Share dialog state
    var showShareDialog by remember { mutableStateOf(false) }
    var pendingShareData by remember { mutableStateOf<ShareImageData?>(null) }
    
    // Subscription state
    var showUpgradeDialog by remember { mutableStateOf(false) }
    var subscriptionTier by remember { mutableStateOf<SubscriptionTier?>(null) }
    
    // Check subscription on load
    LaunchedEffect(userId) {
        coroutineScope.launch {
            subscriptionTier = SubscriptionService.getUserTier(userId)
            val canUse = SubscriptionService.canUseAIFeature(userId, AIFeature.QUEST_GENERATION)
            
            if (!canUse && subscriptionTier == SubscriptionTier.FREE) {
                // Free users can still see quests, but can't generate new ones
                // Don't show dialog immediately, only when they try to generate
            }
        }
    }
    
    suspend fun loadQuests() {
        try {
            // Check subscription before loading (server also verifies)
            val tier = SubscriptionService.getUserTier(userId)
            val canUse = SubscriptionService.canUseAIFeature(userId, AIFeature.QUEST_GENERATION)
            
            if (!canUse) {
                // Show upgrade dialog and use fallback quests
                showUpgradeDialog = true
                quests = generateIntelligentFallbackQuests(userId)
                return
            }
            
            // Try Firebase Functions first
            val getQuests = functions.getHttpsCallable("getUserQuests")
            val task = getQuests.call(mapOf("userId" to userId))
            val result = task.await()
            val data = result.data as? Map<*, *>

            val active = (data?.get("activeQuests") as? List<*>)?.mapNotNull { q ->
                val questData = q as? Map<*, *> ?: return@mapNotNull null
                Quest(
                    id = questData["id"] as? String ?: "",
                    title = questData["title"] as? String ?: "",
                    description = questData["description"] as? String ?: "",
                    progress = ((questData["current"] as? Number)?.toInt() ?: 0) * 100 /
                              ((questData["target"] as? Number)?.toInt() ?: 1),
                    target = (questData["target"] as? Number)?.toInt() ?: 0,
                    current = (questData["current"] as? Number)?.toInt() ?: 0,
                    type = questData["type"] as? String ?: "habit",
                    icon = questData["icon"] as? String ?: "star",
                    color = Color(0xFF3B82F6)
                )
            } ?: emptyList()

            val completed = (data?.get("completedQuests") as? List<*>)?.mapNotNull { q ->
                val questData = q as? Map<*, *> ?: return@mapNotNull null
                Quest(
                    id = questData["id"] as? String ?: "",
                    title = questData["title"] as? String ?: "",
                    description = questData["description"] as? String ?: "",
                    progress = 100,
                    target = (questData["target"] as? Number)?.toInt() ?: 0,
                    current = (questData["current"] as? Number)?.toInt() ?: 0,
                    type = questData["type"] as? String ?: "habit",
                    icon = questData["icon"] as? String ?: "star",
                    color = Color(0xFF3B82F6)
                )
            } ?: emptyList()

            quests = active
            completedQuests = completed
            errorMessage = null
            android.util.Log.d("Quests", "Loaded ${active.size} active and ${completed.size} completed quests from Firebase")
        } catch (e: Exception) {
            android.util.Log.e("Quests", "Error loading quests from Firebase", e)
            // Show error instead of fallback - user doesn't want fallback
            errorMessage = "Failed to load quests: ${e.message ?: "Firebase Function error. Please check Firebase Functions deployment."}"
            quests = emptyList()
            completedQuests = emptyList()
        }
    }

    val convertQuestToCircle: suspend (Quest, String, (String) -> Unit) -> Unit = { quest, userId, onNavigate ->
        try {
            val repository = com.coachie.app.data.FirebaseRepository.getInstance()
            
            // Convert quest to circle - store quest details in circle so friends can join the same quest
            val circle = com.coachie.app.data.model.Circle(
                id = "", // Will be set by Firestore
                name = quest.title,
                goal = quest.description,
                members = listOf(userId),
                streak = 0,
                createdBy = userId,
                tendency = when (quest.type) {
                    "habit" -> "consistent"
                    "streak" -> "consistent"
                    "goal" -> "early_bird"
                    "challenge" -> "night_owl"
                    else -> null
                },
                maxMembers = 5,
                createdAt = java.util.Date(),
                updatedAt = java.util.Date(),
                questId = quest.id, // Link the quest to the circle
                questTitle = quest.title,
                questDescription = quest.description,
                questTarget = quest.target,
                questType = quest.type
            )
            
            val result = repository.createCircle(circle)
            result.onSuccess { circleId ->
                // Add circle to user's circles list
                repository.addCircleToUser(userId, circleId).onSuccess {
                    android.util.Log.d("Quests", "✅ Successfully converted quest '${quest.title}' to circle: $circleId")
                    android.util.Log.d("Quests", "   Quest details stored in circle: questId=${quest.id}, target=${quest.target}")
                    // Navigate to the newly created circle
                    onNavigate(circleId)
                }.onFailure { error ->
                    android.util.Log.e("Quests", "Failed to add circle to user after conversion", error)
                    // Still navigate even if adding to user failed
                    onNavigate(circleId)
                }
            }.onFailure { error ->
                android.util.Log.e("Quests", "Failed to convert quest to circle", error)
                errorMessage = "Failed to create circle: ${error.message ?: "Unknown error"}"
            }
        } catch (e: Exception) {
            android.util.Log.e("Quests", "Error converting quest to circle", e)
            errorMessage = "Error: ${e.message ?: "Unknown error"}"
        }
    }
    
    val updateQuestProgress: suspend (String, Int) -> Unit = { questId, increment ->
        try {
            errorMessage = null
            // Try Firebase Functions first
            val updateQuest = functions.getHttpsCallable("updateQuestProgress")
            val task = updateQuest.call(mapOf(
                "questId" to questId,
                "progress" to increment
            ))
            val result = task.await()

            // Reload quests to get updated progress
            loadQuests()
            android.util.Log.d("Quests", "Updated quest $questId progress by $increment via Firebase")
        } catch (e: Exception) {
            android.util.Log.e("Quests", "Error updating quest progress via Firebase, using local simulation", e)

            // Fallback: Simulate local progress update
            val currentQuest = quests.find { it.id == questId }
            if (currentQuest != null) {
                val newCurrent = minOf(currentQuest.current + increment, currentQuest.target)
                val newProgress = (newCurrent * 100) / currentQuest.target

                // Update local state
                quests = quests.map { quest ->
                    if (quest.id == questId) {
                        quest.copy(current = newCurrent, progress = newProgress)
                    } else {
                        quest
                    }
                }

                // If completed, move to completed list
                if (newCurrent >= currentQuest.target) {
                    quests = quests.filter { it.id != questId }
                    completedQuests = completedQuests + currentQuest.copy(
                        current = newCurrent,
                        progress = 100
                    )
                }

                android.util.Log.d("Quests", "Simulated local progress update for quest $questId")
            } else {
                errorMessage = "Quest not found"
            }
        }
    }
    
    LaunchedEffect(userId) {
        if (userId.isNotBlank()) {
            isLoading = true
            try {
                loadQuests()
            } catch (e: Exception) {
                android.util.Log.e("Quests", "Error in LaunchedEffect loadQuests", e)
                errorMessage = "Failed to load quests: ${e.message}"
            } finally {
                isLoading = false
            }
        } else {
            android.util.Log.w("Quests", "UserId is blank, cannot load quests")
            isLoading = false
        }
    }
    
    // Upgrade dialog
    if (showUpgradeDialog) {
        UpgradePromptDialog(
            onDismiss = { showUpgradeDialog = false },
            onUpgrade = {
                showUpgradeDialog = false
                onNavigateToSubscription()
            },
            featureName = "AI Quest Generation"
        )
    }
    
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    
    // Get semantic color for Quests (gold/amber theme)
    val gender = LocalGender.current
    val isMale = gender.lowercase() == "male"
    val questsColor = getSemanticColorPrimary(SemanticColorCategory.QUESTS, isMale)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBackground)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Quests", color = questsColor) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Filled.ArrowBack, "Back", tint = questsColor)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = questsColor,
                        navigationIconContentColor = questsColor
                    )
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                    item {
                        Column {
                            Text(
                                text = "Active Quests",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F2937), // Dark gray for better readability
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            Text(
                                text = "Quests update automatically as you complete habits, log meals, workouts, water, and sleep!",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6B7280),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                    
                    items(quests) { quest ->
                        QuestCard(
                            quest = quest,
                            onProgressUpdate = { /* Disabled - quests update automatically */ },
                            onShare = {
                                if (quest.progress >= 100) {
                                    pendingShareData = ShareImageData(
                                        type = ShareImageType.QUEST,
                                        title = quest.title,
                                        metric = "Completed!",
                                        subtitle = quest.description,
                                        progressRing = quest.progress
                                    )
                                    showShareDialog = true
                                }
                            },
                            onConvertToCircle = {
                                coroutineScope.launch {
                                    convertQuestToCircle(quest, userId, onNavigateToCircle)
                                }
                            }
                        )
                    }
                    
                    if (completedQuests.isNotEmpty()) {
                        item {
                            Text(
                                text = "Completed",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F2937), // Dark gray for better readability
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        
                        items(completedQuests) { quest ->
                            QuestCard(
                                quest = quest,
                                isCompleted = true,
                                onShare = {
                                    pendingShareData = ShareImageData(
                                        type = ShareImageType.QUEST,
                                        title = quest.title,
                                        metric = "Completed!",
                                        subtitle = quest.description,
                                        progressRing = quest.progress
                                    )
                                    showShareDialog = true
                                },
                                onConvertToCircle = {
                                    coroutineScope.launch {
                                        convertQuestToCircle(quest, userId, onNavigateToCircle)
                                    }
                                }
                            )
                        }
                    }
                    }
                }
            }
        }
        
        // Share platform dialog
        if (showShareDialog) {
            SharePlatformDialog(
                onDismiss = { showShareDialog = false },
                onShareToPlatform = { platform ->
                    showShareDialog = false
                    pendingShareData?.let { data ->
                        shareService.generateAndShare(data, platform)
                    }
                    pendingShareData = null
                }
            )
        }
    }
}

