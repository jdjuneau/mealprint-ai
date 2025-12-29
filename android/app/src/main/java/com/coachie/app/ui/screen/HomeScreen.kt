package com.coachie.app.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.style.TextAlign
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.components.TodaysResetCard
import com.coachie.app.ui.components.TodaysLogCard
import com.coachie.app.ui.components.StreakBadgeCard
import com.coachie.app.ui.components.ProgressRingCard
import com.coachie.app.ui.components.QuickLogButtonsCard
import com.coachie.app.ui.components.AIInsightCard
import com.coachie.app.ui.components.HabitProgressCard
import com.coachie.app.ui.components.WellnessQuickAccessCard
import com.coachie.app.ui.components.NavigationTileCard
import com.coachie.app.ui.components.CirclePulseCard
import com.coachie.app.ui.components.SharePlatformDialog
import com.coachie.app.ui.components.EnergyScoreCard
import com.coachie.app.ui.components.WinOfTheDayCard
import com.coachie.app.ui.components.MorningBriefInsightCard
import com.coachie.app.ui.components.WeeklyBlueprintCard
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.ui.theme.Tertiary40
import com.coachie.app.service.TtsService
import com.coachie.app.service.VoiceStyle
import com.coachie.app.service.ShareService
import com.coachie.app.service.ShareImageData
import com.coachie.app.service.ShareImageType
import com.coachie.app.util.DailyScoreCalculator
import com.coachie.app.util.CoachieScoreTracker
import com.coachie.app.data.model.Circle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.coachie.app.ui.components.generateContextualSuggestions
import com.coachie.app.ui.components.Suggestion
import com.coachie.app.data.HabitRepository
import com.coachie.app.data.model.Habit
import com.coachie.app.data.model.HabitCompletion
import com.coachie.app.ui.theme.Primary40
import com.coachie.app.ui.theme.Secondary40
import com.coachie.app.ui.theme.Accent40
import com.coachie.app.ui.theme.MaleAccent40
import com.coachie.app.ui.theme.LocalGender
import com.coachie.app.ui.theme.SemanticColorCategory
import com.coachie.app.ui.theme.getSemanticColorPrimary
import java.util.Calendar
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ShareCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.local.PreferencesManager
import com.coachie.app.data.model.Conversation
import com.coachie.app.data.model.DailyLog
import com.coachie.app.data.model.HealthLog
import com.coachie.app.data.model.MicronutrientType
import com.coachie.app.data.model.isSatisfiedBy
import com.coachie.app.data.model.toMicronutrientTypeMap
import com.coachie.app.service.ProactiveHealthService
import com.coachie.app.service.AnxietyDetectionService
import com.coachie.app.ui.components.BoxBreathing
import com.coachie.app.ui.theme.Tertiary40
import com.coachie.app.viewmodel.AuthState
import com.coachie.app.viewmodel.CoachieViewModel
import com.coachie.app.viewmodel.HomeDashboardViewModel
import com.coachie.app.utils.DebugLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenWithMindfulness(
    navBackStackEntry: NavBackStackEntry,
    navController: androidx.navigation.NavHostController? = null,
    onSignOut: () -> Unit = {},
    onNavigateToMealLog: () -> Unit = {},
    onNavigateToSupplementPhotoLog: () -> Unit = {},
    onNavigateToWorkoutLog: () -> Unit = {},
    onNavigateToSleepLog: () -> Unit = {},
    onNavigateToWaterLog: () -> Unit = {},
    onNavigateToWeightLog: () -> Unit = {},
    onNavigateToDebug: () -> Unit = {},
    onNavigateToDailyLog: () -> Unit = {},
    onNavigateToAI: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToHelp: () -> Unit = {},
    onShareApp: () -> Unit = {},
    onNavigateToCaloriesDetail: () -> Unit = {},
    onNavigateToStreakDetail: () -> Unit = {},
    onNavigateToGoalsBreakdown: () -> Unit = {},
    onNavigateToMicronutrientTracker: () -> Unit = {},
    onNavigateToMealRecommendation: () -> Unit = {},
    onNavigateToMoodTracker: () -> Unit = {},
    onNavigateToCycleTracker: () -> Unit = {},
    onNavigateToVoiceLogging: () -> Unit = {},
    onNavigateToJournalFlow: () -> Unit = {},
    onNavigateToMyWins: () -> Unit = {},
    onNavigateToWinDetails: (String) -> Unit = {},
    onNavigateToFlowScoreDetails: () -> Unit = {},
    onNavigateToHabits: () -> Unit = {},
    onNavigateToHabitSuggestions: () -> Unit = {},
    onNavigateToCommunity: () -> Unit = {},
    onNavigateToCircle: (String) -> Unit = {},
    onNavigateToMeditation: () -> Unit = {},
    onNavigateToHealthTracking: () -> Unit = {},
    onNavigateToWellness: () -> Unit = {},
    onNavigateToPermissions: () -> Unit = {},
    onNavigateToQuests: () -> Unit = {},
    onNavigateToInsights: () -> Unit = {},
    onNavigateToHabitTimer: (String, String, Int) -> Unit = { _, _, _ -> }, // habitId, habitTitle, durationSeconds
    onNavigateToSubscription: () -> Unit = {},
    viewModel: CoachieViewModel = viewModel()
) {
    // State for mindfulness features
    var showStressAssessment by remember { mutableStateOf(false) }
    var showBoxBreathing by remember { mutableStateOf(false) }
    var showWindDown by remember { mutableStateOf(false) }
    var showGroundingExercise by remember { mutableStateOf(false) }
    var currentStressLevel by remember { mutableStateOf(5f) }
    var threeMinuteBreathing by remember { mutableStateOf(false) }

    // Get current user
    val authState by viewModel.authState.collectAsState()
    val currentUser = (authState as? AuthState.Authenticated)?.user

    // Redirect to auth if not authenticated - handled by navigation

    // Check for proactive notification intents
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val activity = context as? android.app.Activity
        activity?.intent?.let { intent ->
            when {
                intent.getBooleanExtra(ProactiveHealthService.EXTRA_3_MINUTE_BREATHING, false) -> {
                    // Morning health notification - show 3-minute breathing
                    threeMinuteBreathing = true
                    showBoxBreathing = true
                    currentStressLevel = 5f // Default stress level
                }
                intent.getBooleanExtra("show_winddown", false) -> {
                    // Evening wind-down notification
                    showWindDown = true
                }
                intent.getBooleanExtra(AnxietyDetectionService.EXTRA_GROUNDING_EXERCISE, false) -> {
                    // Anxiety detection notification - show grounding exercise
                    showGroundingExercise = true
                }
            }
        }
    }

    // Home screen content
    HomeScreenContent(
        navBackStackEntry = navBackStackEntry,
        navController = navController,
        onSignOut = onSignOut,
        onNavigateToMealLog = onNavigateToMealLog,
        onNavigateToSupplementPhotoLog = onNavigateToSupplementPhotoLog,
        onNavigateToWorkoutLog = onNavigateToWorkoutLog,
        onNavigateToSleepLog = onNavigateToSleepLog,
        onNavigateToWaterLog = onNavigateToWaterLog,
        onNavigateToWeightLog = onNavigateToWeightLog,
        onNavigateToDebug = onNavigateToDebug,
        onNavigateToDailyLog = onNavigateToDailyLog,
        onNavigateToAI = onNavigateToAI,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToHelp = onNavigateToHelp,
        onShareApp = onShareApp,
        onNavigateToCaloriesDetail = onNavigateToCaloriesDetail,
        onNavigateToStreakDetail = onNavigateToStreakDetail,
        onNavigateToGoalsBreakdown = onNavigateToGoalsBreakdown,
        onNavigateToMicronutrientTracker = onNavigateToMicronutrientTracker,
        onNavigateToMealRecommendation = onNavigateToMealRecommendation,
        onNavigateToMoodTracker = onNavigateToMoodTracker,
        onNavigateToVoiceLogging = onNavigateToVoiceLogging,
        onNavigateToJournalFlow = onNavigateToJournalFlow,
        onNavigateToMyWins = onNavigateToMyWins,
        onNavigateToWinDetails = onNavigateToWinDetails,
        onNavigateToFlowScoreDetails = onNavigateToFlowScoreDetails,
        onNavigateToHabits = onNavigateToHabits,
        onNavigateToHabitSuggestions = onNavigateToHabitSuggestions,
        onNavigateToCommunity = onNavigateToCommunity,
        onNavigateToCircle = onNavigateToCircle,
        onNavigateToMeditation = onNavigateToMeditation,
        onNavigateToHealthTracking = onNavigateToHealthTracking,
        onNavigateToWellness = onNavigateToWellness,
        onNavigateToPermissions = onNavigateToPermissions,
        onNavigateToQuests = onNavigateToQuests,
        onNavigateToInsights = onNavigateToInsights,
        onNavigateToHabitTimer = onNavigateToHabitTimer,
        onNavigateToSubscription = onNavigateToSubscription,
        viewModel = viewModel,
        showStressAssessment = showStressAssessment,
        onShowStressAssessmentChange = { showStressAssessment = it },
        onStartBoxBreathing = { stressLevel -> currentStressLevel = stressLevel; showStressAssessment = false; showBoxBreathing = true }
    )

    // Box Breathing Full Screen
    if (showBoxBreathing) {
        val boxBreathingUserId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId()
        if (boxBreathingUserId == null) {
            return
        }
        BoxBreathing(
            stressLevel = currentStressLevel.toInt(),
            userId = boxBreathingUserId,
            customDurationSeconds = if (threeMinuteBreathing) 180 else null, // 3 minutes for morning check
            onComplete = {
                showBoxBreathing = false
                threeMinuteBreathing = false
            }
        )
    }

    // Wind-down screen
    if (showWindDown) {
        WindDownAudioScreen(
            onNavigateBack = { showWindDown = false }
        )
    }

    // Grounding exercise screen
    if (showGroundingExercise) {
        GroundingExerciseScreen(
            onNavigateBack = { showGroundingExercise = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    navBackStackEntry: NavBackStackEntry,
    navController: androidx.navigation.NavHostController? = null,
    onSignOut: () -> Unit,
    onNavigateToMealLog: () -> Unit = {},
    onNavigateToSupplementPhotoLog: () -> Unit = {},
    onNavigateToWorkoutLog: () -> Unit = {},
    onNavigateToSleepLog: () -> Unit = {},
    onNavigateToWaterLog: () -> Unit = {},
    onNavigateToWeightLog: () -> Unit = {},
    onNavigateToDebug: () -> Unit = {},
    onNavigateToDailyLog: () -> Unit = {},
    onNavigateToAI: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToHelp: () -> Unit = {},
    onShareApp: () -> Unit = {},
    onNavigateToCaloriesDetail: () -> Unit = {},
    onNavigateToStreakDetail: () -> Unit = {},
    onNavigateToGoalsBreakdown: () -> Unit = {},
    onNavigateToMicronutrientTracker: () -> Unit = {},
    onNavigateToMealRecommendation: () -> Unit = {},
    onNavigateToMoodTracker: () -> Unit = {},
    onNavigateToCycleTracker: () -> Unit = {},
    onNavigateToVoiceLogging: () -> Unit = {},
    onNavigateToJournalFlow: () -> Unit = {},
    onNavigateToMyWins: () -> Unit = {},
    onNavigateToWinDetails: (String) -> Unit = {},
    onNavigateToFlowScoreDetails: () -> Unit = {},
    onNavigateToHabits: () -> Unit = {},
    onNavigateToHabitSuggestions: () -> Unit = {},
    onNavigateToCommunity: () -> Unit = {},
    onNavigateToCircle: (String) -> Unit = {},
    onNavigateToMeditation: () -> Unit = {},
    onNavigateToHealthTracking: () -> Unit = {},
    onNavigateToWellness: () -> Unit = {},
    onNavigateToPermissions: () -> Unit = {},
    onNavigateToQuests: () -> Unit = {},
    onNavigateToInsights: () -> Unit = {},
    onNavigateToHabitTimer: (String, String, Int) -> Unit = { _, _, _ -> }, // habitId, habitTitle, durationSeconds
    onNavigateToSubscription: () -> Unit = {},
    viewModel: CoachieViewModel = viewModel(),
    showStressAssessment: Boolean = false,
    onShowStressAssessmentChange: (Boolean) -> Unit = {},
    onStartBoxBreathing: (Float) -> Unit = {}
) {
        val authState by viewModel.authState.collectAsState()
        val currentUser = (authState as? AuthState.Authenticated)?.user

        val authenticatedUserId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId()
        if (authenticatedUserId == null) {
            // User not authenticated - show login screen or return early
            return
        }
        
        val context = LocalContext.current
        val dashboardContext = context
        val dashboardViewModel = viewModel<HomeDashboardViewModel>(
            viewModelStoreOwner = navBackStackEntry,
            key = authenticatedUserId,
            factory = HomeDashboardViewModel.Factory(
                repository = com.coachie.app.data.FirebaseRepository.getInstance(),
                userId = authenticatedUserId,
                context = dashboardContext
            )
        )

        // Refresh dashboard data when screen appears or when user changes
        LaunchedEffect(navBackStackEntry.lifecycle.currentState, currentUser?.uid) {
            DebugLogger.logDebug("HomeScreen", "Screen lifecycle/state changed - refreshing dashboard")
            dashboardViewModel.refresh()
        }
        
        // CRITICAL: Refresh when screen resumes (e.g., returning from voice logging)
        androidx.compose.runtime.DisposableEffect(navBackStackEntry) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    android.util.Log.d("HomeScreen", "🔄🔄🔄 SCREEN RESUMED - REFRESHING DASHBOARD DATA 🔄🔄🔄")
                    dashboardViewModel.refresh()
                    
                    // VERIFICATION: Log that refresh was called
                    android.util.Log.d("HomeScreen", "✅ Dashboard refresh triggered on resume")
                }
            }
            navBackStackEntry.lifecycle.addObserver(observer)
            onDispose {
                navBackStackEntry.lifecycle.removeObserver(observer)
            }
        }

    // Dashboard state
    val profile by dashboardViewModel.profile.collectAsState()
    val todayLog by dashboardViewModel.todayLog.collectAsState()
    val todayHealthLogs by dashboardViewModel.todayHealthLogs.collectAsState()
    val streak by dashboardViewModel.streak.collectAsState()
    val aiInsight by dashboardViewModel.aiInsight.collectAsState()
    val userGoals by dashboardViewModel.userGoals.collectAsState()
    val useImperial by dashboardViewModel.useImperial.collectAsState()
    val isLoading by dashboardViewModel.isLoading.collectAsState()
    
    // Subscription state
    var subscriptionTier by remember { mutableStateOf<com.coachie.app.data.model.SubscriptionTier?>(null) }
    var remainingDailyInsights by remember { mutableStateOf<Int?>(null) }
    var showMorningBriefUpgrade by remember { mutableStateOf(false) }
    
    LaunchedEffect(authenticatedUserId) {
        if (authenticatedUserId != null) {
            subscriptionTier = com.coachie.app.data.SubscriptionService.getUserTier(authenticatedUserId)
            if (subscriptionTier == com.coachie.app.data.model.SubscriptionTier.FREE) {
                remainingDailyInsights = com.coachie.app.data.SubscriptionService.getRemainingAICalls(
                    authenticatedUserId,
                    com.coachie.app.data.model.AIFeature.DAILY_INSIGHT
                )
            } else {
                remainingDailyInsights = Int.MAX_VALUE
            }
        }
    }

    // Get gender for semantic colors
    val gender = LocalGender.current
    val isMale = gender.lowercase() == "male"

    // Calculate stats from health logs
    val meals = todayHealthLogs.filterIsInstance<HealthLog.MealLog>()
    val workouts = todayHealthLogs.filterIsInstance<HealthLog.WorkoutLog>()
    val sleepLogs = todayHealthLogs.filterIsInstance<HealthLog.SleepLog>()
    val waterLogs = todayHealthLogs.filterIsInstance<HealthLog.WaterLog>()
    val weightLogs = todayHealthLogs.filterIsInstance<HealthLog.WeightLog>()
    val caloriesConsumed = meals.sumOf { it.calories }
    // Calories burned: prefer Google Fit energy expended (total daily energy), otherwise use workout calories
    val workoutCalories = workouts.sumOf { it.caloriesBurned ?: 0 }
    val caloriesBurned = workoutCalories
    // Get daily calorie goal from profile's computed property (calculated using Mifflin-St Jeor BMR formula)
    // This is calculated based on: weight, height, age, activity level, and weight goal
    val dailyCalorieGoal = profile?.estimatedDailyCalories ?: 2000
    val stepsGoal = (userGoals?.get("dailySteps") as? Number)?.toInt() ?: 10000

    // Load habit data
    val habitRepository = remember { HabitRepository.getInstance() }
    var habits by remember { mutableStateOf<List<Habit>>(emptyList()) }
    var habitCompletions by remember { mutableStateOf<List<HabitCompletion>>(emptyList()) }
    var dismissedSuggestions by remember { mutableStateOf(mutableSetOf<String>()) }
    
    // Load habits
    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { userId ->
            habitRepository.getHabits(userId).collect { habitList ->
                habits = habitList
            }
        }
    }
    
    // Load today's completions - refresh when returning to screen
    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { userId ->
            habitRepository.getRecentCompletions(userId, days = 1).collect { completions ->
                val today = java.util.Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                
                val tomorrow = today + (24 * 60 * 60 * 1000)
                
                val filtered = completions.filter {
                    it.completedAt.time >= today && it.completedAt.time < tomorrow
                }
                
                android.util.Log.d("HomeScreen", "Habit completions updated: ${filtered.size} completions today")
                habitCompletions = filtered
            }
        }
    }
    
    
    // Calculate habit stats
    val completedHabitsToday = habitCompletions.map { it.habitId }.distinct().size
    val hasHabits = habits.isNotEmpty()
    
    // LifeOS Dashboard data
    val functions = Firebase.functions
    val ttsService = remember(context) { TtsService.getInstance(context) }
    val shareService = remember(context) { ShareService.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    // Use Activity context for sharing (LocalContext.current in Compose is Activity)
    
    // Weekly Blueprint state
    var weeklyBlueprint by remember { mutableStateOf<Map<String, Any>?>(null) }
    var generatingBlueprint by remember { mutableStateOf(false) }
    
    // Load weekly blueprint initially
    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { userId ->
            try {
                val db = Firebase.firestore
                // Calculate Monday of the current week (matches function's getWeekStarting logic)
                val weekStarting = java.time.LocalDate.now().let { date ->
                    val dayOfWeek = date.dayOfWeek.value // Monday=1, Sunday=7
                    val daysToSubtract = if (dayOfWeek == 7) 6 else dayOfWeek - 1 // Sunday: subtract 6 to get Monday, others: subtract to get Monday
                    date.minusDays(daysToSubtract.toLong())
                }.toString()
                
                // Try weeklyBlueprints first, then weeklyPlans
                val blueprintDoc = db.collection("users")
                    .document(userId)
                    .collection("weeklyBlueprints")
                    .document(weekStarting)
                    .get()
                    .await()
                
                if (blueprintDoc.exists()) {
                    weeklyBlueprint = blueprintDoc.data
                } else {
                    val planDoc = db.collection("users")
                        .document(userId)
                        .collection("weeklyPlans")
                        .document(weekStarting)
                        .get()
                        .await()
                    if (planDoc.exists()) {
                        weeklyBlueprint = planDoc.data
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeScreen", "Error loading weekly blueprint", e)
            }
        }
    }
    
    // Listen for real-time updates to weekly blueprint
    DisposableEffect(currentUser?.uid) {
        val userId = currentUser?.uid
        val blueprintListener = if (userId != null) {
            val db = Firebase.firestore
            // Calculate Monday of the current week (matches function's getWeekStarting logic)
            val weekStarting = java.time.LocalDate.now().let { date ->
                val dayOfWeek = date.dayOfWeek.value // Monday=1, Sunday=7
                val daysToSubtract = if (dayOfWeek == 7) 6 else dayOfWeek - 1 // Sunday: subtract 6 to get Monday, others: subtract to get Monday
                date.minusDays(daysToSubtract.toLong())
            }.toString()
            
            // Listen for real-time updates to weeklyBlueprints
            db.collection("users")
                .document(userId)
                .collection("weeklyBlueprints")
                .document(weekStarting)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.e("HomeScreen", "Blueprint listener error", error)
                        return@addSnapshotListener
                    }
                    
                    if (snapshot != null && snapshot.exists()) {
                        weeklyBlueprint = snapshot.data
                        android.util.Log.d("HomeScreen", "Blueprint updated from listener")
                    } else if (snapshot != null && !snapshot.exists()) {
                        // Document was deleted, check weeklyPlans
                        db.collection("users")
                            .document(userId)
                            .collection("weeklyPlans")
                            .document(weekStarting)
                            .get()
                            .addOnSuccessListener { planDoc ->
                                if (planDoc.exists()) {
                                    weeklyBlueprint = planDoc.data
                                } else {
                                    weeklyBlueprint = null
                                }
                            }
                    }
                }
        } else {
            null
        }
        
        val planListener = if (userId != null) {
            val db = Firebase.firestore
            // Calculate Monday of the current week (matches function's getWeekStarting logic)
            val weekStarting = java.time.LocalDate.now().let { date ->
                val dayOfWeek = date.dayOfWeek.value // Monday=1, Sunday=7
                val daysToSubtract = if (dayOfWeek == 7) 6 else dayOfWeek - 1 // Sunday: subtract 6 to get Monday, others: subtract to get Monday
                date.minusDays(daysToSubtract.toLong())
            }.toString()
            
            // Also listen to weeklyPlans for backward compatibility
            db.collection("users")
                .document(userId)
                .collection("weeklyPlans")
                .document(weekStarting)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.e("HomeScreen", "Plan listener error", error)
                        return@addSnapshotListener
                    }
                    
                    // Only update if weeklyBlueprints doesn't exist
                    if (snapshot != null && snapshot.exists()) {
                        db.collection("users")
                            .document(userId)
                            .collection("weeklyBlueprints")
                            .document(weekStarting)
                            .get()
                            .addOnSuccessListener { blueprintDoc ->
                                if (!blueprintDoc.exists()) {
                                    weeklyBlueprint = snapshot.data
                                    android.util.Log.d("HomeScreen", "Plan updated from listener")
                                }
                            }
                    }
                }
        } else {
            null
        }
        
        onDispose {
            blueprintListener?.remove()
            planListener?.remove()
        }
    }
    val activityContext = context
    val isSpeaking by ttsService.isSpeaking.collectAsState()
    
    // Share dialog state
    var showShareDialog by remember { mutableStateOf(false) }
    var pendingShareData by remember { mutableStateOf<ShareImageData?>(null) }
    var sharePhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    
    // Photo capture/selection launchers
    var hasCameraPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }
    
    var currentPhotoFile: java.io.File? = null
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && sharePhotoUri != null) {
            // Photo captured, it's already in sharePhotoUri
        }
    }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        sharePhotoUri = uri
    }
    
    // CRITICAL: Start at 50 instead of 0 to be encouraging (user requested)
    var energyScore by remember { mutableStateOf(50) }
    var energyHrv by remember { mutableStateOf<Double?>(null) }
    var energySleepHours by remember { mutableStateOf<Double?>(null) }
    var energyColor by remember { mutableStateOf(Color(0xFF6B46C1)) }
    var userCircles by remember { mutableStateOf<List<Circle>>(emptyList()) }
    var winOfTheDay by remember { mutableStateOf<String?>(null) }
    var hasNotifications by remember { mutableStateOf(false) }
    
    // Animation
    val scoreScale = remember { Animatable(1f) }
    val pulseScale = rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // Check for circle interactions today
    var hasCircleInteraction by remember { mutableStateOf(false) }
    var allTodaysFocusTasksCompleted by remember { mutableStateOf(false) }
    val repository = remember { com.coachie.app.data.FirebaseRepository.getInstance() }
    val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    
    LaunchedEffect(currentUser?.uid, today) {
        currentUser?.uid?.let { userId ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val interactionResult = repository.hasCircleInteractionToday(userId, today)
                hasCircleInteraction = interactionResult.getOrNull() ?: false
                
                // Check if all Today's Focus tasks are completed
                val allCompletedResult = repository.areAllTodaysFocusTasksCompleted(userId, today)
                allTodaysFocusTasksCompleted = allCompletedResult.getOrNull() ?: false
            }
        }
    }
    
    // Calculate energy score when data changes (separate from circle loading to avoid cancellation issues)
    // Always use local calculation to include habits (Cloud Function doesn't include habits)
    LaunchedEffect(currentUser?.uid, meals, workouts, sleepLogs, waterLogs, todayHealthLogs, habits, habitCompletions, todayLog, hasCircleInteraction, allTodaysFocusTasksCompleted) {
        currentUser?.uid?.let { userId ->
            try {
                // Always use local calculation to ensure habits are included and scores match between screens
                android.util.Log.d("HomeScreen", "Starting local energy score calculation")
                android.util.Log.d("HomeScreen", "Data counts - meals: ${meals.size}, workouts: ${workouts.size}, sleepLogs: ${sleepLogs.size}, waterLogs: ${waterLogs.size}, healthLogs: ${todayHealthLogs.size}, habits: ${habits.size}, completions: ${habitCompletions.size}, circleInteraction: $hasCircleInteraction, allTasksCompleted: $allTodaysFocusTasksCompleted")

                val categoryScores = DailyScoreCalculator.calculateAllScores(
                    meals = meals,
                    workouts = workouts,
                    sleepLogs = sleepLogs,
                    waterLogs = waterLogs,
                    allHealthLogs = todayHealthLogs,
                    dailyLog = todayLog,
                    habits = habits,
                    habitCompletions = habitCompletions,
                    hasCircleInteractionToday = hasCircleInteraction,
                    allTodaysFocusTasksCompleted = allTodaysFocusTasksCompleted
                )

                val calculatedScore = categoryScores.calculateDailyScore()
                android.util.Log.d("HomeScreen", "Category scores - health: ${categoryScores.healthScore}, wellness: ${categoryScores.wellnessScore}, habits: ${categoryScores.habitsScore}, total: $calculatedScore")

                // Show actual calculated score (can be 0 if no data logged yet)
                energyScore = calculatedScore
                
                // Store score locally using CoachieScoreTracker
                val scoreTracker = CoachieScoreTracker(context)
                val goals = userGoals
                val calorieGoal = profile?.estimatedDailyCalories?.toInt() ?: 2000
                val stepsGoal = (goals?.get("dailySteps") as? Number)?.toInt() ?: 10000
                val waterGoal = (goals?.get("waterGoal") as? Number)?.toInt() ?: 2000
                val sleepGoal = 8.0
                
                scoreTracker.calculateAndStoreTodayScore(
                    meals = meals,
                    workouts = workouts,
                    sleepLogs = sleepLogs,
                    waterLogs = waterLogs,
                    allHealthLogs = todayHealthLogs,
                    dailyLog = todayLog,
                    habits = habits,
                    habitCompletions = habitCompletions,
                    calorieGoal = calorieGoal,
                    stepsGoal = stepsGoal,
                    waterGoal = waterGoal,
                    sleepGoal = sleepGoal
                )
                android.util.Log.d("HomeScreen", "Stored Coachie score locally: $calculatedScore")

                energyColor = when {
                    calculatedScore >= 80 -> Color(0xFF10B981)
                    calculatedScore >= 60 -> Color(0xFFF59E0B)
                    calculatedScore >= 40 -> Color(0xFFEF4444)
                    else -> Color(0xFF6B46C1)
                }

                scoreScale.animateTo(1.1f, animationSpec = spring())
                delay(300)
                scoreScale.animateTo(1f, animationSpec = spring())

                android.util.Log.d("HomeScreen", "Used local calculation for energy score: $calculatedScore")
                    
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Ignore cancellation - composition changed, new LaunchedEffect will handle it
                android.util.Log.d("HomeScreen", "Energy score calculation cancelled (composition changed)")
            } catch (e: Exception) {
                android.util.Log.e("HomeScreen", "Error calculating energy score", e)
                energyScore = 50 // Fallback to minimum
                energyColor = Color(0xFF6B46C1)
            }
        }
    }
    
    // Load circle data and win of the day IN PARALLEL - both start at the same time
    // CRITICAL: Include today's date as a key so win only reloads when date changes
    LaunchedEffect(currentUser?.uid, today) {
        currentUser?.uid?.let { userId ->
            val repository = FirebaseRepository.getInstance()
            
            android.util.Log.d("HomeScreen", "🔄 Starting PARALLEL load: circles + win of the day for user: $userId")
            android.util.Log.d("HomeScreen", "  User ID: $userId")
            android.util.Log.d("HomeScreen", "  Is new user: ${profile?.isFirstTimeUser == true}")
            
            // Load BOTH in parallel using async
            val circlesDeferred = async(Dispatchers.IO) {
                android.util.Log.d("HomeScreen", "  📊 Loading circles...")
                val result = repository.getUserCircles(userId)
                android.util.Log.d("HomeScreen", "  📊 Circles result: ${result.isSuccess}, ${result.getOrNull()?.size ?: 0} circles")
                result
            }
            
            val winDeferred = async(Dispatchers.IO) {
                try {
                    android.util.Log.d("HomeScreen", "  🏆 Loading win of the day for TODAY...")
                    val result = repository.getWinOfTheDay(userId)
                    android.util.Log.d("HomeScreen", "  🏆 Win result: ${result.isSuccess}, win=${result.getOrNull()?.win ?: result.getOrNull()?.gratitude ?: "none"}")
                    result
                } catch (e: Exception) {
                    android.util.Log.e("HomeScreen", "  ❌ Error loading win in parallel", e)
                    Result.failure<HealthLog.WinEntry?>(e)
                }
            }
            
            // Wait for both to complete and update UI
            launch(Dispatchers.Main) {
                // Update circles
                circlesDeferred.await().onSuccess { circles ->
                    android.util.Log.d("HomeScreen", "✅ Loaded ${circles.size} circles: ${circles.map { it.name }}")
                    userCircles = circles
                    if (circles.isEmpty()) {
                        android.util.Log.w("HomeScreen", "⚠️ No circles found for user - this is normal for new users")
                    }
                }.onFailure { error ->
                    android.util.Log.e("HomeScreen", "❌ Failed to load circles: ${error.message}", error)
                    error.printStackTrace()
                    userCircles = emptyList()
                }
                
                // Update win of the day - use getWinOfTheDay() which returns win for TODAY specifically
                winDeferred.await().onSuccess { winEntry ->
                    val loadedWin = winEntry?.win ?: winEntry?.gratitude
                    winOfTheDay = loadedWin
                    android.util.Log.d("HomeScreen", "✅ Loaded win of the day: ${winOfTheDay ?: "none"}")
                    if (winOfTheDay == null) {
                        android.util.Log.w("HomeScreen", "⚠️ No win of the day found for today - this is normal for new users or if no wins generated yet")
                    }
                }.onFailure { error ->
                    android.util.Log.e("HomeScreen", "❌ Failed to load win: ${error.message}", error)
                    error.printStackTrace()
                    winOfTheDay = null
                }
            }
            
            // Auto-refresh circles every 30 seconds (win doesn't need auto-refresh)
            launch(Dispatchers.IO) {
                while (true) {
                    kotlinx.coroutines.delay(30000) // 30 seconds
                    repository.getUserCircles(userId).onSuccess { circles ->
                        android.util.Log.d("HomeScreen", "🔄 Refreshed circles: ${circles.size}")
                        withContext(Dispatchers.Main) {
                            userCircles = circles
                        }
                    }.onFailure {
                        // Silently fail on refresh - don't clear existing circles
                    }
                }
            }
        } ?: run {
            android.util.Log.w("HomeScreen", "⚠️ No userId available for loading circles")
            userCircles = emptyList()
            winOfTheDay = null
        }
    }
    
    // Check for notifications (messages, friend requests, etc.) - AGGRESSIVE CHECK
    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { userId ->
            val repository = FirebaseRepository.getInstance()
            
            // Function to check for notifications - SIMPLIFIED AND AGGRESSIVE
            suspend fun checkNotifications(): Boolean {
                return try {
                    // Check for unread messages
                    val conversationsResult = repository.getConversations(userId)
                    val conversations = conversationsResult.getOrNull() ?: emptyList()
                    val hasUnreadMessages = conversations.any { conversation ->
                        val unread = conversation.getUnreadCount(userId)
                        unread > 0
                    }
                    
                    // Check for pending friend requests (incoming only)
                    val friendRequestsResult = repository.getPendingFriendRequests(userId)
                    val friendRequests = friendRequestsResult.getOrNull() ?: emptyList()
                    val hasFriendRequests = friendRequests.any { request ->
                        request.toUserId == userId && request.status == "pending"
                    }
                    
                    val result = hasUnreadMessages || hasFriendRequests
                    android.util.Log.w("HomeScreen", "🔔🔔🔔 NOTIFICATION CHECK: unread=$hasUnreadMessages (${conversations.size} convos), requests=$hasFriendRequests (${friendRequests.size} reqs), RESULT=$result 🔔🔔🔔")
                    
                    result
                } catch (e: Exception) {
                    android.util.Log.e("HomeScreen", "Error checking notifications", e)
                    false
                }
            }
            
            // Initial check immediately
            val initialState = checkNotifications()
            withContext(Dispatchers.Main) {
                hasNotifications = initialState
            }
            android.util.Log.w("HomeScreen", "🔔🔔🔔 INITIAL STATE: hasNotifications=$initialState 🔔🔔🔔")
            
            // Refresh every 3 seconds (very aggressive)
            while (true) {
                kotlinx.coroutines.delay(3000)
                val newState = checkNotifications()
                if (newState != hasNotifications) {
                    android.util.Log.w("HomeScreen", "🔔🔔🔔 STATE CHANGED: $hasNotifications -> $newState 🔔🔔🔔")
                    withContext(Dispatchers.Main) {
                        hasNotifications = newState
                    }
                }
            }
        }
    }
    
    // Generate achievement wins in background (doesn't block anything)
    // Analyze both yesterday (for completeness) and today (for immediate wins)
    LaunchedEffect(currentUser?.uid, today) {
        currentUser?.uid?.let { userId ->
            launch(Dispatchers.IO) {
                try {
                    val repository = FirebaseRepository.getInstance()
                    val habitRepository = com.coachie.app.data.HabitRepository.getInstance()
                    val achievementService = com.coachie.app.service.AchievementWinService(repository, habitRepository)
                    
                    // Generate Today's Focus tasks if needed (7-9 tasks per day)
                    val taskGenerator = com.coachie.app.service.TodaysFocusTaskGenerator(repository, habitRepository)
                    taskGenerator.generateTodaysTasksIfNeeded(userId)
                    android.util.Log.d("HomeScreen", "Generated Today's Focus tasks if needed")
                    
                    // Analyze yesterday's data (for completeness)
                    val yesterday = java.time.LocalDate.now().minusDays(1).format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                    achievementService.analyzeAndCreateWins(userId, yesterday)
                    android.util.Log.d("HomeScreen", "Generated achievement wins for $yesterday")
                    
                    // CRITICAL: Also analyze TODAY's data so wins appear immediately
                    val todayStr = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                    achievementService.analyzeAndCreateWins(userId, todayStr)
                    android.util.Log.d("HomeScreen", "Generated achievement wins for $todayStr (today)")
                    
                    // After generating wins, reload the win of the day
                    val winResult = repository.getWinOfTheDay(userId)
                    winResult.getOrNull()?.let { win ->
                        withContext(Dispatchers.Main) {
                            winOfTheDay = win.win ?: win.gratitude
                            android.util.Log.d("HomeScreen", "✅ Updated win of the day after generation: ${winOfTheDay}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("HomeScreen", "Error generating achievement wins", e)
                }
            }
        }
    }
    
        // DISABLED: Auto-play morning brief TTS (user requested to disable voice)
        // LaunchedEffect(aiInsight) {
        //     val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        //     if (hour >= 6 && hour <= 10 && !aiInsight.isNullOrBlank()) {
        //         delay(1000)
        //         val greeting = when (hour) {
        //             in 6..11 -> "Good morning! "
        //             in 12..17 -> "Good afternoon! "
        //             else -> "Good evening! "
        //         }
        //         ttsService.speak("$greeting$aiInsight", VoiceStyle.MORNING)
        //     }
        // }
    
    // Cleanup TTS on dispose
    DisposableEffect(Unit) {
        onDispose {
            ttsService.stop()
        }
    }
    
    // Generate contextual suggestions
    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val hasJournaledToday = todayHealthLogs.any { it is HealthLog.JournalEntry }
    val lastMeditationDaysAgo = 7 // TODO: Calculate from health logs
    
    val contextualSuggestions = remember(currentHour, hasHabits, habits.size, completedHabitsToday, hasJournaledToday, lastMeditationDaysAgo) {
        generateContextualSuggestions(
            currentHour = currentHour,
            hasHabits = hasHabits,
            habitCount = habits.size,
            completedHabitsToday = completedHabitsToday,
            hasJournaledToday = hasJournaledToday,
            lastMeditationDaysAgo = lastMeditationDaysAgo
        ).filter { it.id !in dismissedSuggestions }
    }
    
    // Menu state
    var showMenu by remember { mutableStateOf(false) }
    
    // Home screen content with simplified dashboard
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Scaffold(
            containerColor = Color.Transparent,
        topBar = {
            CoachieCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CoachieCardDefaults.colors()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Today's Journey",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                "Menu",
                                tint = Primary40
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sync Google Fit") },
                                onClick = {
                                    showMenu = false
                                    // Trigger Google Fit sync
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            android.util.Log.d("HomeScreen", "🚀 Manual sync triggered from menu")
                                            com.coachie.app.service.HealthSyncService.sync(context)
                                        } catch (e: Exception) {
                                            android.util.Log.e("HomeScreen", "Sync failed", e)
                                            e.printStackTrace()
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.Sync, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Permissions") },
                                onClick = {
                                    showMenu = false
                                    onNavigateToPermissions()
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.Lock, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Profile") },
                                onClick = {
                                    showMenu = false
                                    onNavigateToProfile()
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.Person, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    showMenu = false
                                    onNavigateToSettings()
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.Settings, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Help & QA") },
                                onClick = {
                                    showMenu = false
                                    onNavigateToHelp()
                                },
                                leadingIcon = {
                                    Text("❓", style = MaterialTheme.typography.headlineSmall)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share App") },
                                onClick = {
                                    showMenu = false
                                    onShareApp()
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.Share, contentDescription = null)
                                }
                            )
                            // Debug menu item removed
                        }
                    }
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
                top = 8.dp,
                bottom = 16.dp + androidx.compose.foundation.layout.WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
            )
        ) {
        // LifeOS Engagement Section
        // Energy Score Card
        item {
            EnergyScoreCard(
                score = energyScore,
                hrv = energyHrv,
                sleepHours = energySleepHours,
                color = energyColor,
                scale = scoreScale.value,
                onShare = {
                    pendingShareData = ShareImageData(
                        type = ShareImageType.READINESS,
                        title = "Energy Score",
                        metric = "$energyScore",
                        subtitle = energyHrv?.let { "HRV: ${it.toInt()}" }
                            ?: energySleepHours?.let { "Sleep: ${it.toInt()}h" }
                    )
                    showShareDialog = true
                },
                onClick = { onNavigateToFlowScoreDetails() }
            )
        }
        
        // Circle and Win Cards Row - Small cards in one row below Energy Score
        // Always render both slots to maintain equal sizing and prevent layout shift
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Circle Pulse Card - ALWAYS show, with placeholder if no data
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight() // Match heights
                ) {
                    if (userCircles.isNotEmpty()) {
                        val primaryCircle = userCircles.first()
                        CirclePulseCard(
                            circle = primaryCircle,
                            pulseScale = pulseScale.value,
                            onNavigateToCircle = { 
                                // Always navigate to community screen
                                onNavigateToCommunity()
                            },
                            totalCircles = userCircles.size,
                            hasNotifications = hasNotifications,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Placeholder card for new users - always show
                        CirclePulseCard(
                            circle = com.coachie.app.data.model.Circle(
                                id = "",
                                name = "", // Empty name will show "Join a circle" in the card
                                goal = "",
                                createdBy = "",
                                members = emptyList()
                            ),
                            pulseScale = 1f,
                            onNavigateToCircle = { 
                                // Navigate to community to join/create circles
                                onNavigateToCommunity()
                            },
                            totalCircles = 0,
                            hasNotifications = false,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                
                // Win of the Day Card - ALWAYS show, with placeholder if no data
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight() // Match heights
                ) {
                    if (winOfTheDay != null && winOfTheDay!!.isNotBlank()) {
                        WinOfTheDayCard(
                            win = winOfTheDay!!,
                            onShare = {
                                pendingShareData = ShareImageData(
                                    type = ShareImageType.CIRCLE_WIN,
                                    title = "Win of the Day",
                                    metric = winOfTheDay!!
                                )
                                showShareDialog = true
                            },
                            onClick = {
                                // Navigate to the specific win details, NOT the wins journal
                                onNavigateToWinDetails(winOfTheDay!!)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Placeholder card for new users
                        WinOfTheDayCard(
                            win = "Share your first win!",
                            onShare = {
                                // Navigate to wins journal to create first win
                                onNavigateToMyWins()
                            },
                            onClick = {
                                // Navigate to wins journal to create first win
                                onNavigateToMyWins()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        
        // Weekly Blueprint Card - MOVED OUTSIDE Today's Journey (can be accessed via navigation)
        // item(key = "weekly_blueprint_home") {
        //     WeeklyBlueprintCard(...)
        // }
        
        // Weekly Blueprint Card
        item(key = "weekly_blueprint_home") {
            WeeklyBlueprintCard(
                weeklyBlueprint = weeklyBlueprint,
                generatingBlueprint = generatingBlueprint,
                onGenerate = {
                    // Check if user is authenticated before proceeding
                    if (currentUser == null) {
                        android.util.Log.e("HomeScreen", "Cannot generate blueprint: user is not authenticated")
                        Toast.makeText(
                            context,
                            "Please sign in to generate your weekly blueprint",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        withContext(Dispatchers.Main) {
                            generatingBlueprint = true
                        }
                        
                        // Verify user is still authenticated and get auth token
                        val userId = currentUser?.uid
                        if (userId.isNullOrBlank()) {
                            android.util.Log.e("HomeScreen", "Cannot generate blueprint: userId is null or blank")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Authentication error. Please sign in again.",
                                    Toast.LENGTH_LONG
                                ).show()
                                generatingBlueprint = false
                            }
                            return@launch
                        }
                        
                        // Ensure auth token is ready (especially for new accounts)
                        try {
                            val authToken = currentUser.getIdToken(false).await()
                            android.util.Log.d("HomeScreen", "Auth token retrieved successfully for user: $userId")
                            if (authToken.token.isNullOrBlank()) {
                                android.util.Log.w("HomeScreen", "Auth token is blank, waiting a bit longer...")
                                kotlinx.coroutines.delay(1000)
                                // Try again
                                currentUser.getIdToken(true).await()
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("HomeScreen", "Failed to get auth token", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Authentication error. Please wait a moment and try again.",
                                    Toast.LENGTH_LONG
                                ).show()
                                generatingBlueprint = false
                            }
                            return@launch
                        }
                        
                        // Retry logic for transient errors
                        var retryCount = 0
                        val maxRetries = 2
                        var success = false
                        
                        while (retryCount <= maxRetries && !success) {
                            try {
                                if (retryCount > 0) {
                                    android.util.Log.i("HomeScreen", "Retrying blueprint generation (attempt ${retryCount + 1}/${maxRetries + 1})...")
                                    // Wait before retry (exponential backoff)
                                    kotlinx.coroutines.delay(2000L * retryCount)
                                }
                                
                                // Try generateWeeklyBlueprint first, fallback to generateWeeklyShoppingList
                                val generateFunction = try {
                                    functions.getHttpsCallable("generateWeeklyBlueprint")
                                } catch (e: Exception) {
                                    android.util.Log.w("HomeScreen", "generateWeeklyBlueprint not found, trying generateWeeklyShoppingList", e)
                                    functions.getHttpsCallable("generateWeeklyShoppingList")
                                }
                                
                                android.util.Log.d("HomeScreen", "Calling blueprint generation function for user: $userId")
                                
                                // CRITICAL: Handle timeout - function can take 2-4 minutes but client times out at 60s
                                // If we get a timeout, poll Firestore instead of waiting for function response
                                // Calculate Monday of the current week (matches function's getWeekStarting logic)
                                val weekStarting = java.time.LocalDate.now().let { date ->
                                    val dayOfWeek = date.dayOfWeek.value // Monday=1, Sunday=7
                                    val daysToSubtract = if (dayOfWeek == 7) 6 else dayOfWeek - 1 // Sunday: subtract 6 to get Monday, others: subtract to get Monday
                                    date.minusDays(daysToSubtract.toLong())
                                }.toString()
                                
                                try {
                                    // Try to call and wait - but with timeout handling
                                    generateFunction.call().await()
                                    android.util.Log.d("HomeScreen", "✅ Blueprint generation completed successfully")
                                } catch (e: java.io.InterruptedIOException) {
                                    // Client timeout - function is still running in background
                                    android.util.Log.w("HomeScreen", "⏱️ Client timeout - function still running, polling Firestore...")
                                    // Fall through to polling logic below
                                } catch (e: java.io.IOException) {
                                    // Check if it's a timeout/canceled error
                                    if (e.message?.contains("timeout", ignoreCase = true) == true || 
                                        e.message?.contains("Canceled", ignoreCase = true) == true) {
                                        android.util.Log.w("HomeScreen", "⏱️ Timeout/Canceled - function still running, polling Firestore...")
                                        // Fall through to polling logic below
                                    } else {
                                        throw e // Re-throw if it's a different IOException
                                    }
                                } catch (e: com.google.firebase.functions.FirebaseFunctionsException) {
                                    // Check if it's DEADLINE_EXCEEDED
                                    if (e.code == com.google.firebase.functions.FirebaseFunctionsException.Code.DEADLINE_EXCEEDED) {
                                        android.util.Log.w("HomeScreen", "⏱️ DEADLINE_EXCEEDED - function still running, polling Firestore...")
                                        // Fall through to polling logic below
                                    } else {
                                        throw e // Re-throw other FirebaseFunctionsException
                                    }
                                }
                                
                                // Poll Firestore for blueprint (works for both successful calls and timeouts)
                                android.util.Log.d("HomeScreen", "Polling Firestore for blueprint (week: $weekStarting)...")
                                val db = Firebase.firestore
                                var blueprintFound = false
                                var pollAttempts = 0
                                val maxPollAttempts = 20 // Poll for up to 2 minutes (20 * 6 seconds = 120 seconds) - user requested max 20 attempts
                                
                                // Wait 6 seconds before first poll (give function time to start)
                                kotlinx.coroutines.delay(6000)
                                
                                while (!blueprintFound && pollAttempts < maxPollAttempts) {
                                    pollAttempts++
                                    
                                    try {
                                        // CRITICAL: Use correct path - users/{userId}/weeklyBlueprints/{week}
                                        val blueprintDoc = db.collection("users")
                                            .document(userId)
                                            .collection("weeklyBlueprints")
                                            .document(weekStarting)
                                            .get()
                                            .await()
                                        
                                        if (blueprintDoc.exists()) {
                                            val data = blueprintDoc.data
                                            if (data != null && data.containsKey("meals")) {
                                                android.util.Log.d("HomeScreen", "✅ Blueprint found in Firestore after ${pollAttempts * 6} seconds")
                                                blueprintFound = true
                                                break
                                            }
                                        }
                                        
                                        if (!blueprintFound) {
                                            android.util.Log.d("HomeScreen", "⏳ Blueprint not ready yet (attempt $pollAttempts/$maxPollAttempts)...")
                                            // Wait 6 seconds before next poll
                                            kotlinx.coroutines.delay(6000)
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.w("HomeScreen", "Error polling for blueprint: ${e.message}")
                                        // Wait before retrying on error
                                        kotlinx.coroutines.delay(6000)
                                    }
                                }
                                
                                if (!blueprintFound) {
                                    android.util.Log.w("HomeScreen", "⚠️ Blueprint not found after ${maxPollAttempts * 6} seconds of polling")
                                }
                                
                                // Small delay to ensure Firestore has propagated
                                kotlinx.coroutines.delay(2000)
                                
                                // Try weeklyBlueprints first, then weeklyPlans
                                val blueprintDoc = db.collection("users")
                                    .document(userId)
                                    .collection("weeklyBlueprints")
                                    .document(weekStarting)
                                    .get()
                                    .await()
                                
                                val loadedBlueprint = if (blueprintDoc.exists()) {
                                    blueprintDoc.data
                                } else {
                                    // Fallback to weeklyPlans
                                    val planDoc = db.collection("users")
                                        .document(userId)
                                        .collection("weeklyPlans")
                                        .document(weekStarting)
                                        .get()
                                        .await()
                                    if (planDoc.exists()) {
                                        planDoc.data
                                    } else {
                                        null
                                    }
                                }
                                
                                // Update state on main thread
                                withContext(Dispatchers.Main) {
                                    weeklyBlueprint = loadedBlueprint
                                    generatingBlueprint = false
                                }
                                success = true
                            } catch (e: com.google.firebase.functions.FirebaseFunctionsException) {
                                val errorCode = e.code
                                val errorMessage = e.message ?: "Unknown error"
                                
                                android.util.Log.e("HomeScreen", "Error generating blueprint (attempt ${retryCount + 1}): $errorCode - $errorMessage", e)
                                
                                // Check if this is an authentication error
                                if (errorCode == com.google.firebase.functions.FirebaseFunctionsException.Code.UNAUTHENTICATED) {
                                    android.util.Log.e("HomeScreen", "Authentication error - user may not be fully authenticated")
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            "Authentication error. Please wait a moment and try again, or sign out and sign back in.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        generatingBlueprint = false
                                    }
                                    break
                                }
                                
                                // Check if this is a retryable error
                                val isRetryable = when (errorCode) {
                                    com.google.firebase.functions.FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> {
                                        // These errors might be transient, retry
                                        retryCount < maxRetries
                                    }
                                    com.google.firebase.functions.FirebaseFunctionsException.Code.INTERNAL -> {
                                        // INTERNAL errors (like JSON parsing) - don't retry, show error immediately
                                        // These are usually not transient and retrying won't help
                                        false
                                    }
                                    com.google.firebase.functions.FirebaseFunctionsException.Code.NOT_FOUND -> {
                                        // Function not found - don't retry
                                        false
                                    }
                                    com.google.firebase.functions.FirebaseFunctionsException.Code.FAILED_PRECONDITION -> {
                                        // Profile/data validation error - don't retry, show error immediately
                                        false
                                    }
                                    else -> {
                                        // Other errors - don't retry
                                        false
                                    }
                                }
                                
                                if (!isRetryable) {
                                    // Not retryable or max retries reached - show error
                                    withContext(Dispatchers.Main) {
                                        // Truncate error message if too long (Toast has character limit)
                                        val displayMessage = if (errorMessage.length > 100) {
                                            errorMessage.substring(0, 97) + "..."
                                        } else {
                                            errorMessage
                                        }
                                        
                                        if (errorCode == com.google.firebase.functions.FirebaseFunctionsException.Code.NOT_FOUND) {
                                            android.util.Log.e("HomeScreen", "Function not found. Please deploy the generateWeeklyBlueprint function.")
                                            Toast.makeText(
                                                context,
                                                "Weekly Blueprint feature is not available yet. Please contact support.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else if (errorCode == com.google.firebase.functions.FirebaseFunctionsException.Code.FAILED_PRECONDITION) {
                                            // Profile validation error - show the detailed error message
                                            android.util.Log.e("HomeScreen", "Profile validation failed: $errorMessage")
                                            Toast.makeText(
                                                context,
                                                displayMessage,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            // INTERNAL and other errors - show user-friendly message
                                            android.util.Log.e("HomeScreen", "Blueprint generation failed: $errorCode - $errorMessage")
                                            Toast.makeText(
                                                context,
                                                displayMessage,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        generatingBlueprint = false
                                    }
                                    break
                                } else {
                                    // Retryable error - increment retry count and continue loop
                                    retryCount++
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("HomeScreen", "Error generating blueprint (attempt ${retryCount + 1})", e)
                                
                                // Check if we should retry
                                if (retryCount < maxRetries) {
                                    retryCount++
                                } else {
                                    // Max retries reached - show error
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            "Error generating blueprint: ${e.localizedMessage ?: e.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        generatingBlueprint = false
                                    }
                                    break
                                }
                            }
                        }
                        
                        // If we exhausted retries without success
                        if (!success) {
                            withContext(Dispatchers.Main) {
                                if (generatingBlueprint) {
                                    Toast.makeText(
                                        context,
                                        "Failed to generate blueprint after ${maxRetries + 1} attempts. Please try again later.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    generatingBlueprint = false
                                }
                            }
                        }
                    }
                    }
                },
                onNavigate = {
                    if (navController != null) {
                        navController.navigate("weekly_blueprint")
                    }
                }
            )
        }
        
        // Today's Focus / Reminders Card
        item {
            // Create refresh trigger based on dashboard data changes
            // CRITICAL: Include habit completion IDs AND total water amount in refresh trigger to ensure Today's Focus updates
            val habitCompletionIds = habitCompletions.map { it.habitId }.sorted().joinToString(",")
            // Calculate total water to trigger refresh when water amount changes (not just count)
            // CRITICAL FIX: Use DailyLog.water as source of truth (it already includes voice logs)
            val currentLog = todayLog
            val totalWaterMl = if (currentLog?.water != null && currentLog.water!! > 0) {
                currentLog.water!!
            } else {
                waterLogs.sumOf { it.ml }
            }
            val refreshTrigger = remember(meals.size, waterLogs.size, totalWaterMl, workouts.size, sleepLogs.size, weightLogs.size, habitCompletions.size, habitCompletionIds) {
                "${meals.size}-${waterLogs.size}-${totalWaterMl}ml-${workouts.size}-${sleepLogs.size}-${weightLogs.size}-${habitCompletions.size}-$habitCompletionIds"
            }
            
            val todaysResetUserId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId()
            if (todaysResetUserId != null) {
                TodaysResetCard(
                    userId = todaysResetUserId,
                refreshTrigger = refreshTrigger,
                contextualSuggestions = contextualSuggestions,
                onSuggestionAction = { suggestion ->
                    // Handle different suggestion actions based on actionRoute
                    android.util.Log.d("HomeScreen", "Suggestion action triggered: ${suggestion.actionRoute}")
                    when (suggestion.actionRoute) {
                        "habits" -> {
                            android.util.Log.d("HomeScreen", "Navigating to habits")
                            onNavigateToHabits()
                        }
                        "habit_suggestions" -> {
                            android.util.Log.d("HomeScreen", "Navigating to habit suggestions")
                            onNavigateToHabitSuggestions()
                        }
                        "meditation" -> {
                            android.util.Log.d("HomeScreen", "Navigating to meditation")
                            onNavigateToMeditation()
                        }
                        "journal_flow" -> {
                            android.util.Log.d("HomeScreen", "Navigating to journal flow")
                            onNavigateToJournalFlow()
                        }
                        "habit_progress" -> {
                            android.util.Log.d("HomeScreen", "Navigating to habit progress")
                            onNavigateToHabits() // Show habit progress
                        }
                        "water_log" -> {
                            android.util.Log.d("HomeScreen", "Navigating to water log")
                            if (navController != null) {
                                navController.navigate("water_log")
                            }
                        }
                        "meal_log" -> {
                            android.util.Log.d("HomeScreen", "Navigating to meal log")
                            if (navController != null) {
                                navController.navigate("meal_log")
                            }
                        }
                        "breathing_exercises" -> {
                            android.util.Log.d("HomeScreen", "Navigating to breathing exercises")
                            if (navController != null) {
                                navController.navigate("breathing_exercises")
                            }
                        }
                        "community" -> {
                            android.util.Log.d("HomeScreen", "Navigating to community")
                            onNavigateToCommunity()
                        }
                        "sleep_log" -> {
                            android.util.Log.d("HomeScreen", "Navigating to sleep log")
                            if (navController != null) {
                                navController.navigate("sleep_log")
                            }
                        }
                        else -> {
                            android.util.Log.d("HomeScreen", "Default navigation to habits")
                            // Default to habits screen
                            onNavigateToHabits()
                        }
                    }
                },
                onSuggestionDismiss = { suggestionId ->
                    // Add to dismissed suggestions
                    dismissedSuggestions = dismissedSuggestions.toMutableSet().apply {
                        add(suggestionId)
                    }
                    android.util.Log.d("HomeScreen", "Suggestion dismissed: $suggestionId")
                },
                onNavigateToMealLog = onNavigateToMealLog,
                onNavigateToWaterLog = onNavigateToWaterLog,
                onNavigateToWeightLog = onNavigateToWeightLog,
                onNavigateToSleepLog = onNavigateToSleepLog,
                onNavigateToWorkoutLog = onNavigateToWorkoutLog,
                onNavigateToSupplementLog = onNavigateToSupplementPhotoLog,
                onNavigateToJournal = onNavigateToJournalFlow,
                onNavigateToMeditation = onNavigateToMeditation,
                onNavigateToMeditationWithHabit = { duration, habitId ->
                    if (navController != null) {
                        // Use navigation arguments instead of savedStateHandle
                        navController.navigate("meditation?duration=$duration&habitId=$habitId")
                    } else {
                        onNavigateToMeditation()
                    }
                },
                onNavigateToHabits = onNavigateToHabits,
                onNavigateToHealthTracking = onNavigateToHealthTracking,
                onNavigateToWellness = onNavigateToWellness,
                onNavigateToBreathingExercises = {
                    if (navController != null) {
                        navController.navigate("breathing_exercises")
                    }
                },
                onNavigateToHabitTimer = onNavigateToHabitTimer
                )
            }
        }

        // Morning Brief / AI Insight Card - Combined
        item {
            // Use the same logic as ViewModel to determine time of day based on scheduled brief times
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val timeInMinutes = hour * 60 + minute
            
            val greeting = when {
                timeInMinutes >= 1080 -> "Good evening"  // After 6 PM, show evening brief
                timeInMinutes >= 840 -> "Good afternoon"  // After 2 PM, show afternoon brief
                timeInMinutes >= 540 -> "Good morning"  // After 9 AM, show morning brief
                else -> "Good evening"  // Before 9 AM, show previous evening brief
            }
            
            MorningBriefInsightCard(
                greeting = greeting,
                insight = aiInsight ?: "",
                isSpeaking = false, // Disabled TTS
                onPlay = {
                    // DISABLED: TTS for morning brief (user requested to disable voice)
                    // val fullBrief = "$greeting! ${aiInsight ?: ""}"
                    // ttsService.speak(fullBrief, VoiceStyle.MORNING)
                },
                onStop = {
                    // DISABLED: TTS for morning brief
                    // ttsService.stop()
                },
                onClick = { 
                    if (subscriptionTier == com.coachie.app.data.model.SubscriptionTier.FREE) {
                        showMorningBriefUpgrade = true
                    } else {
                        onNavigateToAI()
                    }
                },
                subscriptionTier = subscriptionTier,
                onUpgrade = { showMorningBriefUpgrade = true }
            )
            
            // Show remaining daily insights count for free tier
            if (subscriptionTier == com.coachie.app.data.model.SubscriptionTier.FREE && remainingDailyInsights != null) {
                Spacer(modifier = Modifier.height(8.dp))
                com.coachie.app.ui.components.RemainingCountBadge(
                    remaining = remainingDailyInsights!!,
                    total = 1,
                    featureName = "daily insight",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
        }

        // Streak and Calories Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Check if user has actual log data
                val hasActualLogData = todayHealthLogs.isNotEmpty() || todayLog != null
                
                StreakBadgeCard(
                    streak = streak,
                    hasActualLogData = hasActualLogData,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToStreakDetail() }
                )
                ProgressRingCard(
                    caloriesConsumed = caloriesConsumed,
                    caloriesBurned = caloriesBurned,
                    dailyGoal = dailyCalorieGoal,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToCaloriesDetail() }
                )
            }
        }

        // Today's Log Card
        item {
            // CRITICAL FIX: Voice logging saves water to BOTH DailyLog.water AND WaterLog entries
            // So we should use DailyLog.water as the source of truth (it already includes voice logs)
            // Only add WaterLog entries that are NOT from voice logging (manual entries)
            // For now, use DailyLog.water if available, otherwise sum WaterLog entries
            val currentLog = todayLog
            val totalWaterMl = if (currentLog?.water != null && currentLog.water!! > 0) {
                // DailyLog.water already includes voice logs, so use it directly
                currentLog.water!!
            } else {
                // Fallback: sum WaterLog entries if DailyLog.water is not set
                waterLogs.sumOf { it.ml }
            }
            android.util.Log.d("HomeScreen", "Today's Log water calculation: DailyLog=${currentLog?.water ?: 0}ml, WaterLogs=${waterLogs.sumOf { it.ml }}ml, Using Total=$totalWaterMl ml")
            
            TodaysLogCard(
                meals = meals,
                workouts = workouts,
                sleepLogs = sleepLogs,
                waterMl = totalWaterMl,
                weightLogs = weightLogs,
                useImperial = useImperial,
                onClick = { onNavigateToDailyLog() }
            )
        }


        // Navigation Tiles - Using semantic colors with gender-specific variants
        item {
            val healthColor = getSemanticColorPrimary(SemanticColorCategory.HEALTH_TRACKING, isMale)
            NavigationTileCard(
                title = "Health Tracking",
                description = "Log meals, workouts, sleep, water, and more",
                icon = Icons.Filled.FitnessCenter,
                iconTint = healthColor,
                backgroundColor = healthColor,
                onClick = { onNavigateToHealthTracking() }
            )
        }

        item {
            val wellnessColor = getSemanticColorPrimary(SemanticColorCategory.WELLNESS, isMale)
            NavigationTileCard(
                title = "Wellness",
                description = "Meditation, journaling, and mindfulness",
                icon = Icons.Filled.SelfImprovement,
                iconTint = wellnessColor,
                backgroundColor = wellnessColor,
                onClick = { onNavigateToWellness() }
            )
        }

        item {
            val habitsColor = getSemanticColorPrimary(SemanticColorCategory.HABITS, isMale)
            NavigationTileCard(
                title = "Habits",
                description = "Build and track your daily habits",
                icon = Icons.Filled.CheckCircle,
                iconTint = habitsColor,
                backgroundColor = habitsColor,
                onClick = { onNavigateToHabits() }
            )
        }

        item {
            val communityColor = getSemanticColorPrimary(SemanticColorCategory.COMMUNITY, isMale)
            NavigationTileCard(
                title = "Community",
                description = "Connect with others on your wellness journey",
                icon = Icons.Filled.Group,
                iconTint = communityColor,
                backgroundColor = communityColor,
                onClick = { onNavigateToCommunity() }
            )
        }
        
        item {
            val questsColor = getSemanticColorPrimary(SemanticColorCategory.QUESTS, isMale)
            NavigationTileCard(
                title = "Quests",
                description = "AI-suggested challenges to level up your wellness",
                icon = Icons.Filled.EmojiEvents,
                iconTint = questsColor,
                backgroundColor = questsColor,
                onClick = { onNavigateToQuests() }
            )
        }
        
        item {
            val insightsColor = getSemanticColorPrimary(SemanticColorCategory.INSIGHTS, isMale)
            NavigationTileCard(
                title = "Insights",
                description = "Monthly AI insights based on your data",
                icon = Icons.Filled.Lightbulb,
                iconTint = insightsColor,
                backgroundColor = insightsColor,
                onClick = { onNavigateToInsights() }
            )
        }
    }
    
    // Morning Brief upgrade dialog
    if (showMorningBriefUpgrade) {
        com.coachie.app.ui.components.UpgradePromptDialog(
            onDismiss = { showMorningBriefUpgrade = false },
            onUpgrade = {
                showMorningBriefUpgrade = false
                onNavigateToSubscription()
            },
            featureName = "Morning Brief",
            remainingCalls = null // Pro-only feature
        )
    }

    // Stress Assessment Modal
    if (showStressAssessment) {
        StressAssessmentDialog(
            onDismiss = { onShowStressAssessmentChange(false) },
            onStartBreathing = { stressLevel ->
                onStartBoxBreathing(stressLevel)
            }
        )
    }
    
    // Share platform dialog
    if (showShareDialog) {
        SharePlatformDialog(
            onDismiss = { 
                showShareDialog = false
                sharePhotoUri = null // Clear photo when dialog closes
            },
            onShareToPlatform = { platform ->
                showShareDialog = false
                pendingShareData?.let { data ->
                    shareService.generateAndShareWithContext(data, activityContext, platform, sharePhotoUri)
                }
                pendingShareData = null
                sharePhotoUri = null // Clear photo after sharing
            },
            photoUri = sharePhotoUri,
            onCapturePhoto = {
                if (hasCameraPermission) {
                    val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                    val imageFileName = "SHARE_${timeStamp}_"
                    val storageDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
                    val imageFile = java.io.File.createTempFile(imageFileName, ".jpg", storageDir)
                    currentPhotoFile = imageFile
                    
                    sharePhotoUri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        imageFile
                    )
                    sharePhotoUri?.let { uri ->
                        cameraLauncher.launch(uri)
                    }
                } else {
                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                }
            },
            onSelectPhoto = {
                imagePickerLauncher.launch("image/*")
            }
        )
    }
    } // Close Scaffold content lambda and Scaffold
    } // Close Box
}

@Composable
fun StressAssessmentDialog(
    onDismiss: () -> Unit,
    onStartBreathing: (Float) -> Unit
) {
    var stressLevel by remember { mutableStateOf(5f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "How stressed are you right now?",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = "Rate your current stress level from 1 to 10:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Slider(
                    value = stressLevel,
                    onValueChange = { stressLevel = it },
                    valueRange = 1f..10f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "1",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${stressLevel.toInt()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "10",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onStartBreathing(stressLevel) }) {
                Text("Start Breathing Exercise")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
