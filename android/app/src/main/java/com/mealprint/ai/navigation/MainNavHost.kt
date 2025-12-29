package com.mealprint.ai.navigation

import android.content.Intent
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.mealprint.ai.ui.auth.AuthScreen
import com.mealprint.ai.ui.screen.*
import com.mealprint.ai.ui.screen.FTUEScreen
import com.mealprint.ai.ui.screen.StreakDetailScreen
import com.mealprint.ai.ui.screen.WaterLogScreen
import com.mealprint.ai.ui.screen.WeightLogScreen
import com.mealprint.ai.ui.screen.WorkoutLogScreen
import com.mealprint.ai.ui.screen.MenstrualTrackerScreen
import com.mealprint.ai.ui.screen.MoodTrackerScreen
import com.mealprint.ai.ui.screen.VoiceLoggingScreen
import com.mealprint.ai.ui.screen.VoiceSettingsScreen
import com.mealprint.ai.ui.screen.BehavioralProfileScreen
import com.mealprint.ai.ui.screen.HabitCreationScreen
import com.mealprint.ai.ui.screen.HabitSuggestionsScreen
import com.mealprint.ai.ui.screen.HabitTemplatesScreen
import com.mealprint.ai.ui.screen.UserSearchScreen
import com.mealprint.ai.ui.screen.FriendsListScreen
import com.mealprint.ai.ui.screen.MessagingScreen
import com.mealprint.ai.ui.screen.HabitProgressScreen
import com.mealprint.ai.ui.screen.SmartSchedulingScreen
import com.mealprint.ai.ui.screen.AchievementsScreen
import com.mealprint.ai.ui.screen.HabitIntelligenceScreen
import com.mealprint.ai.ui.screen.QuestsScreen
import com.mealprint.ai.ui.screen.InsightsScreen
import com.mealprint.ai.ui.screen.JournalFlowScreen
import com.mealprint.ai.ui.screen.MyWinsScreen
import com.mealprint.ai.ui.screen.MeditationScreen
import com.mealprint.ai.ui.screen.JournalHistoryScreen
import com.mealprint.ai.ui.screen.HealthTrackingDashboardScreen
import com.mealprint.ai.ui.screen.HabitsDashboardScreen
import com.mealprint.ai.ui.screen.WellnessDashboardScreen
import com.mealprint.ai.ui.screen.SocialMediaBreakScreen
import com.mealprint.ai.ui.screen.BreathingExercisesScreen
import com.mealprint.ai.ui.screen.PermissionsScreen
import com.mealprint.ai.ui.screen.CommunityScreen
import com.mealprint.ai.ui.screen.CircleJoinScreen
import com.mealprint.ai.ui.screen.CircleDetailScreen
import com.mealprint.ai.ui.screen.CircleCreateScreen
import com.mealprint.ai.ui.screen.WinDetailsScreen
import com.mealprint.ai.ui.screen.FlowScoreDetailsScreen
import com.mealprint.ai.ui.screen.ForumDetailScreen
import com.mealprint.ai.ui.screen.SharedRecipesScreen
import com.mealprint.ai.ui.screen.MyRecipesScreen
import com.mealprint.ai.ui.screen.NotificationDetailScreen
import com.mealprint.ai.ui.screen.HabitTimerScreen
import com.mealprint.ai.ui.screen.StretchingScreen
import com.mealprint.ai.ui.screen.WeeklyBlueprintScreen
import com.mealprint.ai.ui.screen.SplashScreen
import com.mealprint.ai.ui.screen.WelcomeScreen
import com.mealprint.ai.utils.DebugLogger
import com.mealprint.ai.viewmodel.AuthViewModel
import com.mealprint.ai.viewmodel.AuthState
import com.mealprint.ai.viewmodel.HabitSuggestionsViewModel
import com.mealprint.ai.viewmodel.HabitProgressViewModel
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import com.mealprint.ai.ui.components.CoachieCard
import com.mealprint.ai.ui.components.CoachieCardDefaults
import com.mealprint.ai.ui.theme.Primary40
import com.mealprint.ai.ui.theme.rememberCoachieGradient
import com.mealprint.ai.viewmodel.HabitCreationViewModel
import com.mealprint.ai.viewmodel.BehavioralProfileViewModel
import com.mealprint.ai.viewmodel.SmartSchedulingViewModel
import com.mealprint.ai.viewmodel.HabitIntelligenceViewModel
import com.mealprint.ai.viewmodel.HomeDashboardViewModel
import com.mealprint.ai.data.FirebaseRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.auth.FirebaseAuth
import com.mealprint.ai.data.FcmService
import com.mealprint.ai.data.local.PreferencesManager
import com.mealprint.ai.ui.components.FloatingMicButton

/**
 * Helper to get authenticated user ID.
 * Since the app requires authentication to start, we can safely get the user ID directly.
 * NEVER use `currentUser?.uid ?: ""` - this creates blank user IDs which cause Firestore errors.
 */
@Composable
private fun getAuthenticatedUserId(): String? {
    val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
    return if (firebaseUser != null && firebaseUser.uid.isNotBlank() && !firebaseUser.isAnonymous && !firebaseUser.email.isNullOrBlank()) {
        firebaseUser.uid
    } else {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavHost(
    navController: NavHostController,
    initialNavigationTarget: String? = null,
    notificationTitle: String? = null,
    notificationMessage: String? = null,
    notificationDeepLinkTarget: String? = null
) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    var lastSyncedUserId by remember { mutableStateOf<String?>(null) }
    var lastSyncedToken by remember { mutableStateOf<String?>(null) }
    var profileRefreshTrigger by remember { mutableStateOf(0) }
    
    // Handle initial navigation from notifications
    LaunchedEffect(initialNavigationTarget, notificationTitle, notificationMessage, notificationDeepLinkTarget) {
        when (initialNavigationTarget) {
            "messaging" -> {
                // Navigate to messaging - conversationId will be passed via intent
                navController.navigate("messaging") {
                    popUpTo("home") { inclusive = false }
                }
            }
            "forum_thread" -> {
                // Navigate to forum thread - threadId will be passed via intent
                // TODO: Add forum thread navigation when implemented
                navController.navigate("home") {
                    popUpTo("home") { inclusive = false }
                }
            }
            "notification_detail" -> {
                if (notificationTitle != null && notificationMessage != null) {
                    // Pass data as navigation arguments (URL encoded to handle special characters)
                    val encodedTitle = java.net.URLEncoder.encode(notificationTitle, "UTF-8")
                    val encodedMessage = java.net.URLEncoder.encode(notificationMessage, "UTF-8")
                    val encodedDeepLink = notificationDeepLinkTarget?.let { 
                        java.net.URLEncoder.encode(it, "UTF-8") 
                    } ?: ""
                    navController.navigate("notification_detail/$encodedTitle/$encodedMessage/$encodedDeepLink") {
                        popUpTo("home") { inclusive = false }
                    }
                }
            }
            "coach_chat" -> {
                navController.navigate("ai_chat") {
                    popUpTo("home") { inclusive = false }
                }
            }
            "sleep_log" -> {
                navController.navigate("sleep_log") {
                    popUpTo("home") { inclusive = false }
                }
            }
            "workout_log" -> {
                navController.navigate("workout_log") {
                    popUpTo("home") { inclusive = false }
                }
            }
            "habits" -> {
                navController.navigate("habits") {
                    popUpTo("home") { inclusive = false }
                }
            }
            "meal_log" -> {
                navController.navigate("meal_log") {
                    popUpTo("home") { inclusive = false }
                }
            }
            "water_log" -> {
                navController.navigate("water_log") {
                    popUpTo("home") { inclusive = false }
                }
            }
            "health_tracking" -> {
                navController.navigate("health_tracking") {
                    popUpTo("home") { inclusive = false }
                }
            }
        }
    }

    // Start at splash screen, then navigate to auth
    androidx.navigation.compose.NavHost(navController = navController, startDestination = "splash") {

        composable("splash") {
            SplashScreen(
                onSplashComplete = {
                    navController.navigate("welcome") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }

        composable("welcome") {
            WelcomeScreen(navController = navController)
        }

        // Auth route with optional returnTo parameter
        composable(
            route = "auth?returnTo={returnTo}",
            arguments = listOf(
                navArgument("returnTo") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val returnTo = backStackEntry.arguments?.getString("returnTo")
            android.util.Log.d("MainNavHost", "Auth screen - returnTo: $returnTo")
            val viewModel: AuthViewModel = viewModel()
            val authState by viewModel.authState.collectAsState()
            val userGoals by viewModel.userGoals.collectAsState()
            val areGoalsLoading by viewModel.areGoalsLoading.collectAsState()
            val errorMessage by viewModel.errorMessage.collectAsState()
            val coroutineScope = rememberCoroutineScope()

            LaunchedEffect(authState) {
                val user = (authState as? AuthState.Authenticated)?.user
                if (user != null) {
                    // CRITICAL: Clear the explicit sign-out flag when user is authenticated
                    // This must happen on every authentication state change, not just on sign-in
                    if (preferencesManager.userExplicitlySignedOut) {
                        android.util.Log.i("MainNavHost", "✅ User authenticated - clearing userExplicitlySignedOut flag")
                        preferencesManager.userExplicitlySignedOut = false
                    }
                    preferencesManager.userId = user.uid

                    val token = FcmService.getFcmToken()
                    if (!token.isNullOrBlank() && (lastSyncedUserId != user.uid || lastSyncedToken != token)) {
                        FcmService.updateUserFcmToken(context, user.uid, token)
                        lastSyncedUserId = user.uid
                        lastSyncedToken = token
                    }
                }
            }

            Log.d("CoachieDebug", "Auth state: $authState, Goals: $userGoals, Loading: $areGoalsLoading")

            LaunchedEffect(authState, areGoalsLoading, userGoals) {
                android.util.Log.e("AUTH_DEBUG", "authState: $authState, areGoalsLoading: $areGoalsLoading, userGoals: $userGoals")
                
                // Store authState in local variable for smart cast
                val currentAuthState = authState
                
                // CRITICAL: If user is not authenticated, don't navigate anywhere - stay on auth screen (login page)
                if (currentAuthState !is AuthState.Authenticated) {
                    android.util.Log.d("AUTH_DEBUG", "User is not authenticated - staying on auth screen (login page)")
                    return@LaunchedEffect
                }
                
                // CRITICAL: Only navigate if user is authenticated AND not explicitly signed out
                // If user explicitly signed out, they should stay on auth screen (login page)
                // BUT: If user is authenticated, clear the flag (they may have signed in again)
                if (preferencesManager.userExplicitlySignedOut) {
                    // User is authenticated but flag is still set - clear it
                    android.util.Log.i("AUTH_DEBUG", "✅ User authenticated but userExplicitlySignedOut was true - clearing flag")
                    preferencesManager.userExplicitlySignedOut = false
                }
                
                // Now we can use smart cast
                val user = currentAuthState.user
                
                // CRITICAL: Only navigate if user has proper email authentication
                // Anonymous users or users without email should stay on login screen
                if (user.isAnonymous || user.email.isNullOrBlank()) {
                    android.util.Log.w("AUTH_DEBUG", "⚠️ Authenticated user is anonymous or has no email - staying on auth screen")
                    // Sign out the anonymous/invalid user
                    coroutineScope.launch {
                        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                        // State will update automatically via auth state listener
                    }
                    return@LaunchedEffect
                }
                
                // Only navigate after goals have finished loading
                if (!areGoalsLoading) {
                    val goalsSetValue = userGoals?.get("goalsSet")
                    val hasGoalsSetField = goalsSetValue == true

                    // If goalsSet field is missing but we have goal-related fields, consider goals set
                    val hasGoalFields = userGoals?.containsKey("selectedGoal") == true ||
                                       userGoals?.containsKey("weeklyWorkouts") == true ||
                                       userGoals?.containsKey("dailySteps") == true

                    val hasGoals = hasGoalsSetField || hasGoalFields

                    android.util.Log.e("GOALS_DEBUG", "userGoals data: $userGoals")
                    android.util.Log.e("GOALS_DEBUG", "goalsSet value: $goalsSetValue")
                    android.util.Log.e("GOALS_DEBUG", "hasGoalsSetField: $hasGoalsSetField")
                    android.util.Log.e("GOALS_DEBUG", "hasGoalFields: $hasGoalFields")
                    android.util.Log.e("GOALS_DEBUG", "FINAL hasGoals: $hasGoals")

                    // If there's a returnTo destination (e.g., from ai_chat), navigate there instead
                    if (returnTo != null && returnTo.isNotBlank()) {
                        android.util.Log.d("CoachieDebug", "✅ Navigating to return destination: $returnTo")
                        navController.navigate(returnTo) {
                            popUpTo("auth") { inclusive = true }
                        }
                        return@LaunchedEffect // Don't continue with normal navigation
                    } else if (hasGoals) {
                        Log.d("CoachieDebug", "Navigating to home - goals exist")
                        navController.navigate("home") {
                            popUpTo(0) { inclusive = true }
                        }
                    } else {
                        Log.d("CoachieDebug", "Navigating to set_goals - no goals found")
                        navController.navigate("set_goals") {
                            popUpTo("splash") { inclusive = true } // Clear entire back stack including splash/welcome
                        }
                    }
                }
            }

            AuthScreen(
                authState = authState,
                errorMessage = errorMessage,
                onSignIn = { email, pass -> viewModel.signIn(email, pass) },
                onSignUp = { name, email, pass -> viewModel.signUp(name, email, pass) }
            )
        }
        
        // Auth route without returnTo parameter (fallback for other navigation paths)
        composable("auth") {
            val viewModel: AuthViewModel = viewModel()
            val authState by viewModel.authState.collectAsState()
            val userGoals by viewModel.userGoals.collectAsState()
            val areGoalsLoading by viewModel.areGoalsLoading.collectAsState()
            val errorMessage by viewModel.errorMessage.collectAsState()
            val coroutineScope = rememberCoroutineScope()

            LaunchedEffect(authState) {
                val user = (authState as? AuthState.Authenticated)?.user
                if (user != null) {
                    if (preferencesManager.userExplicitlySignedOut) {
                        android.util.Log.i("MainNavHost", "✅ User authenticated - clearing userExplicitlySignedOut flag")
                        preferencesManager.userExplicitlySignedOut = false
                    }
                    preferencesManager.userId = user.uid

                    val token = FcmService.getFcmToken()
                    if (!token.isNullOrBlank() && (lastSyncedUserId != user.uid || lastSyncedToken != token)) {
                        FcmService.updateUserFcmToken(context, user.uid, token)
                        lastSyncedUserId = user.uid
                        lastSyncedToken = token
                    }
                }
            }

            LaunchedEffect(authState, areGoalsLoading, userGoals) {
                val currentAuthState = authState
                if (currentAuthState !is AuthState.Authenticated) {
                    return@LaunchedEffect
                }
                if (preferencesManager.userExplicitlySignedOut) {
                    preferencesManager.userExplicitlySignedOut = false
                }
                val user = currentAuthState.user
                if (user.isAnonymous || user.email.isNullOrBlank()) {
                    coroutineScope.launch {
                        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                    }
                    return@LaunchedEffect
                }
                if (!areGoalsLoading) {
                    val goalsSetValue = userGoals?.get("goalsSet")
                    val hasGoalsSetField = goalsSetValue == true
                    val hasGoalFields = userGoals?.containsKey("selectedGoal") == true ||
                                       userGoals?.containsKey("weeklyWorkouts") == true ||
                                       userGoals?.containsKey("dailySteps") == true
                    val hasGoals = hasGoalsSetField || hasGoalFields
                    if (hasGoals) {
                        navController.navigate("home") {
                            popUpTo(0) { inclusive = true }
                        }
                    } else {
                        navController.navigate("set_goals") {
                            popUpTo("splash") { inclusive = true } // Clear entire back stack including splash/welcome
                        }
                    }
                }
            }

            AuthScreen(
                authState = authState,
                errorMessage = errorMessage,
                onSignIn = { email, pass -> viewModel.signIn(email, pass) },
                onSignUp = { name, email, pass -> viewModel.signUp(name, email, pass) }
            )
        }

        composable("ftue") {
            val userId = getAuthenticatedUserId()
            val repository = FirebaseRepository.getInstance()
            val coroutineScope = rememberCoroutineScope()
            val context = LocalContext.current

            FTUEScreen(
                onComplete = {
                    coroutineScope.launch {
                        // Mark FTUE as completed
                        userId?.let { uid ->
                            if (uid.isNotBlank()) {
                                val profileResult = repository.getUserProfile(uid)
                                val profile = profileResult.getOrNull()
                                if (profile != null) {
                                    val updatedProfile = profile.copy(ftueCompleted = true)
                                    repository.saveUserProfile(updatedProfile)
                                    
                                    // CRITICAL: Trigger gender reload AFTER FTUE is completed
                                    // This ensures the UI theme updates without interfering with FTUE navigation
                                    try {
                                        val prefs = context.getSharedPreferences("coachie_prefs", android.content.Context.MODE_PRIVATE)
                                        prefs.edit().putString("gender_reload_trigger", System.currentTimeMillis().toString()).apply()
                                        android.util.Log.d("MainNavHost", "✅ Gender reload trigger set after FTUE completion")
                                    } catch (e: Exception) {
                                        android.util.Log.e("MainNavHost", "Failed to set gender reload trigger", e)
                                    }
                                } else {
                                    // If profile doesn't exist yet, create it with ftueCompleted = true
                                    FirebaseFirestore.getInstance()
                                        .collection("users")
                                        .document(uid)
                                        .update("ftueCompleted", true)
                                        .await()
                                    
                                    // Still trigger gender reload even if profile didn't exist
                                    try {
                                        val prefs = context.getSharedPreferences("coachie_prefs", android.content.Context.MODE_PRIVATE)
                                        prefs.edit().putString("gender_reload_trigger", System.currentTimeMillis().toString()).apply()
                                        android.util.Log.d("MainNavHost", "✅ Gender reload trigger set after FTUE completion")
                                    } catch (e: Exception) {
                                        android.util.Log.e("MainNavHost", "Failed to set gender reload trigger", e)
                                    }
                                }
                            }
                        }
                        // Navigate to home
                        navController.navigate("home") {
                            popUpTo("ftue") { inclusive = true }
                        }
                    }
                }
            )
        }

        composable("set_goals") {
            SetGoalsScreen(navController = navController)
        }

        composable("home") { backStackEntry ->
            val homeAuthViewModel: AuthViewModel = viewModel()
            HomeScreenWithMindfulness(
                navBackStackEntry = backStackEntry,
                navController = navController,
                onSignOut = {
                    DebugLogger.logDebug("HomeScreen", "Signing out user")
                    homeAuthViewModel.signOut(preferencesManager)
                    navController.navigate("auth") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToMealLog = {
                    DebugLogger.logDebug("Navigation", "Navigate to meal log requested")
                    navController.navigate("meal_log")
                },
                onNavigateToSupplementPhotoLog = {
                    DebugLogger.logDebug("Navigation", "Navigate to supplement photo log requested")
                    navController.navigate("supplement_photo_log")
                },
                onNavigateToWorkoutLog = {
                    DebugLogger.logDebug("Navigation", "Navigate to workout log requested")
                    navController.navigate("workout_log")
                },
                onNavigateToSleepLog = {
                    DebugLogger.logDebug("Navigation", "Navigate to sleep log requested")
                    navController.navigate("sleep_log")
                },
                onNavigateToWaterLog = {
                    DebugLogger.logDebug("Navigation", "Navigate to water log requested")
                    navController.navigate("water_log")
                },
                onNavigateToWeightLog = {
                    DebugLogger.logDebug("Navigation", "Navigate to weight log requested")
                    navController.navigate("weight_log")
                },
                onNavigateToDailyLog = {
                    DebugLogger.logDebug("Navigation", "Navigate to daily log requested")
                    navController.navigate("daily_log")
                },
                // Debug menu removed
                onNavigateToAI = {
                    DebugLogger.logDebug("Navigation", "Navigate to AI chat requested")
                    // Check if user is authenticated before navigating
                    val currentUser = (homeAuthViewModel.authState.value as? AuthState.Authenticated)?.user
                    if (currentUser != null && currentUser.uid.isNotBlank() && !currentUser.isAnonymous && !currentUser.email.isNullOrBlank()) {
                        // User is authenticated, navigate directly
                        navController.navigate("ai_chat")
                    } else {
                        // User not authenticated, navigate to auth with return destination
                        navController.navigate("auth?returnTo=ai_chat")
                    }
                },
                onNavigateToProfile = {
                    DebugLogger.logDebug("Navigation", "Navigate to profile requested")
                    navController.navigate("profile")
                },
                onNavigateToSettings = {
                    DebugLogger.logDebug("Navigation", "Navigate to settings requested")
                    navController.navigate("settings")
                },
                onNavigateToHelp = {
                    DebugLogger.logDebug("Navigation", "Navigate to help requested")
                    navController.navigate("help")
                },
                onShareApp = {
                    val shareText = "Check out Coachie - Your AI-powered wellness coach! Track meals, workouts, habits, and get personalized insights.\n\nDownload: https://coachie.app"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        putExtra(Intent.EXTRA_SUBJECT, "Check out Coachie")
                    }
                    val chooser = Intent.createChooser(intent, "Share Coachie")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                },
                onNavigateToCaloriesDetail = {
                    DebugLogger.logDebug("Navigation", "Navigate to calories detail requested")
                    navController.navigate("calories_detail")
                },
                onNavigateToStreakDetail = {
                    DebugLogger.logDebug("Navigation", "Navigate to streak detail requested")
                    navController.navigate("streak_detail")
                },
                onNavigateToGoalsBreakdown = {
                    DebugLogger.logDebug("Navigation", "Navigate to goals breakdown requested")
                    navController.navigate("goals_breakdown")
                },
                onNavigateToMicronutrientTracker = {
                    DebugLogger.logDebug("Navigation", "Navigate to micronutrient tracker requested")
                    navController.navigate("micronutrients")
                },
                onNavigateToMealRecommendation = {
                    DebugLogger.logDebug("Navigation", "Navigate to meal recommendation requested")
                    navController.navigate("meal_recommendation")
                },
                onNavigateToMoodTracker = {
                    DebugLogger.logDebug("Navigation", "Navigate to mood tracker requested")
                    navController.navigate("mood_tracker")
                },
                onNavigateToCycleTracker = {
                    DebugLogger.logDebug("Navigation", "Navigate to cycle tracker requested")
                    navController.navigate("menstrual_tracker")
                },
                onNavigateToVoiceLogging = {
                    DebugLogger.logDebug("Navigation", "Navigate to voice logging requested")
                    navController.navigate("voice_logging")
                },
                onNavigateToMyWins = {
                    DebugLogger.logDebug("Navigation", "Navigate to my wins requested")
                    navController.navigate("my_wins")
                },
                onNavigateToWinDetails = { win ->
                    DebugLogger.logDebug("Navigation", "Navigate to win details requested")
                    navController.navigate("win_details/${java.net.URLEncoder.encode(win, "UTF-8")}")
                },
                onNavigateToFlowScoreDetails = {
                    DebugLogger.logDebug("Navigation", "Navigate to flow score details requested")
                    navController.navigate("flow_score_details")
                },
                onNavigateToJournalFlow = {
                    DebugLogger.logDebug("Navigation", "Navigate to journal flow requested")
                    navController.navigate("journal_flow")
                },
                onNavigateToHabits = {
                    DebugLogger.logDebug("Navigation", "Navigate to habits requested")
                    navController.navigate("habits")
                },
                onNavigateToHabitSuggestions = {
                    DebugLogger.logDebug("Navigation", "Navigate to habit suggestions requested")
                    navController.navigate("habit_suggestions")
                },
                onNavigateToCommunity = {
                    DebugLogger.logDebug("Navigation", "Navigate to community requested")
                    navController.navigate("community")
                },
                onNavigateToCircle = { circleId ->
                    DebugLogger.logDebug("Navigation", "Navigate to circle detail requested: $circleId")
                    navController.navigate("circle_detail/$circleId")
                },
                onNavigateToMeditation = {
                    DebugLogger.logDebug("Navigation", "Navigate to meditation requested")
                    navController.navigate("meditation")
                },
                onNavigateToHealthTracking = {
                    DebugLogger.logDebug("Navigation", "Navigate to health tracking requested")
                    navController.navigate("health_tracking")
                },
                onNavigateToWellness = {
                    DebugLogger.logDebug("Navigation", "Navigate to wellness requested")
                    navController.navigate("wellness")
                },
                onNavigateToHabitTimer = { habitId, habitTitle, durationSeconds ->
                    DebugLogger.logDebug("Navigation", "Navigate to habit timer requested: habitId=$habitId, title=$habitTitle ($durationSeconds seconds)")
                    // CRITICAL: Use navigation arguments to pass data reliably
                    val durationValue = if (durationSeconds < 60) durationSeconds else (durationSeconds / 60)
                    // Encode habitId and other data in the route as arguments
                    val encodedHabitId = habitId?.let { java.net.URLEncoder.encode(it, "UTF-8") } ?: ""
                    val encodedTitle = java.net.URLEncoder.encode(habitTitle, "UTF-8")
                    val route = "habit_timer?habitId=$encodedHabitId&title=$encodedTitle&duration=$durationValue"
                    android.util.Log.d("Navigation", "Navigating to: $route")
                    navController.navigate(route)
                },
                onNavigateToPermissions = {
                    DebugLogger.logDebug("Navigation", "Navigate to permissions requested")
                    navController.navigate("permissions")
                },
                onNavigateToQuests = {
                    DebugLogger.logDebug("Navigation", "Navigate to Quests requested")
                    navController.navigate("quests")
                },
                onNavigateToInsights = {
                    DebugLogger.logDebug("Navigation", "Navigate to Insights requested")
                    navController.navigate("insights")
                },
                onNavigateToSubscription = {
                    DebugLogger.logDebug("Navigation", "Navigate to subscription requested")
                    navController.navigate("subscription")
                },
            )
        }

        composable("my_habits") { backStackEntry ->
            android.util.Log.d("Navigation", "my_habits route composable executed")
            val userId = getAuthenticatedUserId()

            android.util.Log.d("Navigation", "my_habits route: userId=$userId")

            HabitScreen(
                onNavigateBack = {
                    DebugLogger.logDebug("HabitScreen", "Back navigation requested")
                    navController.popBackStack()
                },
                onNavigateToCreateHabit = {
                    DebugLogger.logDebug("HabitScreen", "Create habit requested")
                    navController.navigate("habit_creation")
                },
                onNavigateToEditHabit = { habitId ->
                    DebugLogger.logDebug("HabitScreen", "Edit habit requested: $habitId")
                    navController.navigate("habit_creation?habitId=$habitId")
                },
                onNavigateToSuggestions = {
                    DebugLogger.logDebug("HabitScreen", "Suggestions requested")
                    navController.navigate("habit_suggestions")
                },
                onNavigateToMeditation = { duration, habitId ->
                    // Use navigation arguments instead of savedStateHandle
                    navController.navigate("meditation?duration=$duration&habitId=$habitId")
                },
                onNavigateToJournal = { habitId ->
                    // Store habit ID for journal completion
                    backStackEntry.savedStateHandle["journal_habit_id"] = habitId
                    navController.navigate("journal_flow")
                },
                onNavigateToWorkout = {
                    navController.navigate("workout_log")
                },
                onNavigateToWater = {
                    navController.navigate("water_log")
                },
                onNavigateToSleep = {
                    navController.navigate("sleep_log")
                },
                onStartTimer = { duration, label, habitId ->
                    // CRITICAL: Use navigation arguments to pass habitId reliably
                    android.util.Log.d("Navigation", "onStartTimer: habitId=$habitId, label=$label, duration=$duration")
                    val encodedHabitId = habitId?.let { java.net.URLEncoder.encode(it, "UTF-8") } ?: ""
                    val encodedTitle = java.net.URLEncoder.encode(label, "UTF-8")
                    val route = "habit_timer?habitId=$encodedHabitId&title=$encodedTitle&duration=$duration"
                    android.util.Log.d("Navigation", "Navigating to: $route")
                    navController.navigate(route)
                },
                viewModel = viewModel(
                    viewModelStoreOwner = backStackEntry,
                    key = "habits_$userId", // Unique key to ensure ViewModel persists
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return com.coachie.app.viewmodel.HabitViewModel(
                                habitRepository = com.coachie.app.data.HabitRepository.getInstance(),
                                userId = userId ?: ""
                            ) as T
                        }
                    }
                ),
                refreshTrigger = 0L // Habits Flow will auto-update from Firestore
            )
        }

        composable("daily_log") {
            TodaysLogDetailScreen(
                onBack = { navController.popBackStack() },
                onNavigateToMealLog = { navController.navigate("meal_log") },
                onNavigateToMealDetail = { meal ->
                    // Store meal data as a map of primitives (MealLog is not Parcelable)
                    val mealData = mapOf(
                        "foodName" to meal.foodName,
                        "calories" to meal.calories,
                        "protein" to meal.protein,
                        "carbs" to meal.carbs,
                        "fat" to meal.fat,
                        "sugar" to meal.sugar,
                        "addedSugar" to meal.addedSugar,
                        "timestamp" to meal.timestamp,
                        "photoUrl" to (meal.photoUrl ?: ""),
                        "micronutrients" to meal.micronutrients
                    )
                    // Set the data on the current entry's savedStateHandle (accessible via previousBackStackEntry in destination)
                    navController.currentBackStackEntry?.savedStateHandle?.set("meal_data", mealData)
                    // Navigate to meal detail screen
                    navController.navigate("meal_detail")
                },
                onNavigateToSupplementLog = { navController.navigate("supplement_photo_log") },
                onNavigateToWorkoutLog = { navController.navigate("workout_log") },
                onNavigateToSleepLog = { navController.navigate("sleep_log") },
                onNavigateToWaterLog = { navController.navigate("water_log") },
                onNavigateToWeightLog = { navController.navigate("weight_log") },
                onNavigateToHealthTracking = { navController.navigate("health_tracking") }
            )
        }

        composable("meal_detail") { backStackEntry ->
            @OptIn(ExperimentalMaterial3Api::class)
            // Use LaunchedEffect to read meal data from savedStateHandle after navigation
            var mealData by remember { mutableStateOf<Map<String, Any>?>(null) }
            
            LaunchedEffect(backStackEntry) {
                // Try to get meal data from previous entry's savedStateHandle (where it was set before navigation)
                // Also check current entry's savedStateHandle as fallback
                mealData = navController.previousBackStackEntry?.savedStateHandle?.get<Map<String, Any>>("meal_data")
                    ?: backStackEntry.savedStateHandle.get<Map<String, Any>>("meal_data")
                
                // Log for debugging
                if (mealData == null) {
                    DebugLogger.logDebug("Navigation", "Meal data not found in savedStateHandle for meal_detail screen. Previous entry: ${navController.previousBackStackEntry?.destination?.route}")
                } else {
                    DebugLogger.logDebug("Navigation", "Meal data found: ${mealData?.get("foodName")}")
                }
            }
            
            if (mealData != null) {
                // Reconstruct MealLog from the map
                val meal = com.coachie.app.data.model.HealthLog.MealLog(
                    foodName = mealData!!["foodName"] as? String ?: "",
                    calories = (mealData!!["calories"] as? Number)?.toInt() ?: 0,
                    protein = (mealData!!["protein"] as? Number)?.toInt() ?: 0,
                    carbs = (mealData!!["carbs"] as? Number)?.toInt() ?: 0,
                    fat = (mealData!!["fat"] as? Number)?.toInt() ?: 0,
                    sugar = (mealData!!["sugar"] as? Number)?.toInt() ?: 0,
                    addedSugar = (mealData!!["addedSugar"] as? Number)?.toInt() ?: 0,
                    timestamp = (mealData!!["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    photoUrl = (mealData!!["photoUrl"] as? String)?.takeIf { it.isNotBlank() },
                    micronutrients = (mealData!!["micronutrients"] as? Map<String, Double>) ?: emptyMap()
                )
                
                com.coachie.app.ui.screen.MealDetailScreen(
                    meal = meal,
                    entryId = null, // TODO: Get entryId from repository
                    onBack = { navController.popBackStack() },
                    onEdit = {
                        // Navigate to meal log screen - for now just navigate, edit functionality can be added later
                        navController.navigate("meal_log")
                    },
                    onNavigateToMyRecipes = {
                        navController.navigate("my_recipes")
                    }
                )
            } else {
                // Show error state if meal data is missing - match app UI style
                MealDetailErrorScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable("micronutrients") {
            val authViewModel: AuthViewModel = viewModel()
            val authState by authViewModel.authState.collectAsState()
            
            val userId = getAuthenticatedUserId()
            
            MicronutrientTrackerScreen(
                userId = userId ?: "",
                onBack = { navController.popBackStack() },
                onNavigateToSupplementPhotoLog = {
                    DebugLogger.logDebug("Navigation", "Navigate to supplement photo log from micronutrient tracker")
                    navController.navigate("supplement_photo_log")
                },
                onNavigateToSavedSupplements = {
                    navController.navigate("saved_supplements")
                }
            )
        }
        
        composable("saved_supplements") {
            val authViewModel: AuthViewModel = viewModel()
            val authState by authViewModel.authState.collectAsState()
            val currentUser = (authState as? AuthState.Authenticated)?.user
            
            if (currentUser != null) {
                SavedSupplementsScreen(
                    userId = currentUser.uid,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        composable("menstrual_tracker") {
            val userId = getAuthenticatedUserId()

            MenstrualTrackerScreen(
                userId = userId ?: "",
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("mood_tracker") {
            val userId = getAuthenticatedUserId()

            MoodTrackerScreen(
                userId = userId ?: "",
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("voice_logging") {
            val userId = getAuthenticatedUserId()

            VoiceLoggingScreen(
                userId = userId ?: "",
                onNavigateBack = { navController.popBackStack() }
            )
        }

                    composable("voice_settings") {
                        VoiceSettingsScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable("behavioral_profile") { backStackEntry ->
                        val userId = getAuthenticatedUserId()
                        
                        BehavioralProfileScreen(
                            onComplete = { navController.navigate("my_habits") },
                            onBack = { navController.popBackStack() },
                            viewModel = viewModel(
                                viewModelStoreOwner = backStackEntry,
                                key = "behavioral_profile_$userId",
                                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                        @Suppress("UNCHECKED_CAST")
                                        return BehavioralProfileViewModel(
                                            habitRepository = com.coachie.app.data.HabitRepository.getInstance(),
                                            userId = userId ?: ""
                                        ) as T
                                    }
                                }
                            )
                        )
                    }
                    composable("habit_creation?habitId={habitId}") { backStackEntry ->
                        val userId = getAuthenticatedUserId()
                        val habitId = backStackEntry.arguments?.getString("habitId")
                        
                        HabitCreationScreen(
                            habitId = habitId,
                            onSave = { newHabitId ->
                                navController.navigate("my_habits") {
                                    popUpTo("my_habits") { inclusive = true }
                                }
                            },
                            onBack = { navController.popBackStack() },
                            onNavigateToSuggestions = {
                                navController.navigate("habit_suggestions")
                            },
                            habitCreationViewModel = viewModel(
                                viewModelStoreOwner = backStackEntry,
                                key = "habit_creation_${habitId ?: "new"}_$userId",
                                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                        @Suppress("UNCHECKED_CAST")
                                        return HabitCreationViewModel(
                                            habitRepository = com.coachie.app.data.HabitRepository.getInstance(),
                                            userId = userId ?: ""
                                        ) as T
                                    }
                                }
                            )
                        )
                    }
                    composable("habit_suggestions") { backStackEntry ->
                        val userId = getAuthenticatedUserId()
                        
                        HabitSuggestionsScreen(
                            onBack = { navController.popBackStack() },
                            onNavigateToSubscription = { navController.navigate("subscription") },
                            onCreateHabit = { habitId ->
                                // Navigate to habits screen and refresh
                                navController.navigate("my_habits") {
                                    // Don't pop the stack, just navigate
                                    // This allows user to go back to suggestions if needed
                                    launchSingleTop = true
                                }
                            },
                            viewModel = viewModel(
                                viewModelStoreOwner = backStackEntry,
                                key = userId ?: "",
                                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                        @Suppress("UNCHECKED_CAST")
                                        return HabitSuggestionsViewModel(
                                            habitRepository = com.coachie.app.data.HabitRepository.getInstance(),
                                            smartCoachEngine = com.coachie.app.data.ai.SmartCoachEngine.getInstance(),
                                            userId = userId ?: ""
                                        ) as T
                                    }
                                }
                            )
                        )
                    }
                    composable("habit_progress") { backStackEntry ->
                        val userId = getAuthenticatedUserId()
                        
                        HabitProgressScreen(
                            onBack = { navController.popBackStack() },
                            viewModel = viewModel(
                                viewModelStoreOwner = backStackEntry,
                                key = userId ?: "",
                                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                        @Suppress("UNCHECKED_CAST")
                                        return HabitProgressViewModel(
                                            habitRepository = com.coachie.app.data.HabitRepository.getInstance(),
                                            userId = userId ?: ""
                                        ) as T
                                    }
                                }
                            )
                        )
                    }
                    composable("smart_scheduling") { backStackEntry ->
                        val userId = getAuthenticatedUserId()
                        
                        SmartSchedulingScreen(
                            onBack = { navController.popBackStack() },
                            viewModel = viewModel(
                                viewModelStoreOwner = backStackEntry,
                                key = "smart_scheduling_$userId",
                                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                        @Suppress("UNCHECKED_CAST")
                                        return SmartSchedulingViewModel(
                                            habitRepository = com.coachie.app.data.HabitRepository.getInstance(),
                                            firebaseRepository = com.coachie.app.data.FirebaseRepository.getInstance(),
                                            userId = userId ?: ""
                                        ) as T
                                    }
                                }
                            )
                        )
                    }
                    composable("habit_intelligence") { backStackEntry ->
                        val userId = getAuthenticatedUserId()
                        
                        HabitIntelligenceScreen(
                            onBack = { navController.popBackStack() },
                            viewModel = viewModel(
                                viewModelStoreOwner = backStackEntry,
                                key = "habit_intelligence_$userId",
                                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                        @Suppress("UNCHECKED_CAST")
                                        return HabitIntelligenceViewModel(
                                            habitRepository = com.coachie.app.data.HabitRepository.getInstance(),
                                            firebaseRepository = com.coachie.app.data.FirebaseRepository.getInstance(),
                                            userId = userId ?: ""
                                        ) as T
                                    }
                                }
                            )
                        )
                    }
                    composable("journal_flow") { backStackEntry ->
                        val habitId = backStackEntry.savedStateHandle.get<String>("journal_habit_id")
                        JournalFlowScreen(
                            onNavigateBack = { navController.popBackStack() },
                            habitId = habitId
                        )
                    }
                    composable("my_wins") {
                        MyWinsScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable("win_details/{win}") { backStackEntry ->
                        val encodedWin = backStackEntry.arguments?.getString("win") ?: ""
                        // Decode the URL-encoded win text (spaces become + signs)
                        val win = try {
                            java.net.URLDecoder.decode(encodedWin, "UTF-8")
                        } catch (e: Exception) {
                            encodedWin // Fallback to original if decoding fails
                        }
                        WinDetailsScreen(
                            win = win,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable("flow_score_details") {
                        FlowScoreDetailsScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable("journal_history") {
                        JournalHistoryScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "meditation?duration={duration}&habitId={habitId}",
                        arguments = listOf(
                            navArgument("duration") {
                                type = androidx.navigation.NavType.IntType
                                defaultValue = 0  // Use 0 as default to indicate not set
                            },
                            navArgument("habitId") {
                                type = androidx.navigation.NavType.StringType
                                defaultValue = null
                                nullable = true
                            }
                        )
                    ) { backStackEntry ->
                        val userId = getAuthenticatedUserId()
                        val durationArg = backStackEntry.arguments?.getInt("duration") ?: 0
                        val duration = if (durationArg > 0) durationArg else null
                        val habitId = backStackEntry.arguments?.getString("habitId")
                        
                        MeditationScreen(
                            onNavigateBack = { navController.popBackStack() },
                            userId = userId ?: "",
                            initialDuration = duration,
                            habitId = habitId
                        )
                    }

        composable(
            route = "habit_timer?habitId={habitId}&title={title}&duration={duration}",
            arguments = listOf(
                navArgument("habitId") { 
                    type = androidx.navigation.NavType.StringType
                    defaultValue = null
                    nullable = true
                },
                navArgument("title") { 
                    type = androidx.navigation.NavType.StringType
                    defaultValue = "Habit"
                },
                navArgument("duration") { 
                    type = androidx.navigation.NavType.IntType
                    defaultValue = 10
                }
            )
        ) { backStackEntry ->
            val userId = getAuthenticatedUserId()
            
            // CRITICAL: Get habitId from navigation arguments first, then fallback to savedStateHandle
            val habitIdArg = backStackEntry.arguments?.getString("habitId")
            val titleArg = backStackEntry.arguments?.getString("title") ?: "Habit"
            val durationArg = backStackEntry.arguments?.getInt("duration") ?: 10
            
            // Decode URL-encoded title (replaces + with spaces and decodes other URL encoding)
            val decodedTitleArg = try {
                java.net.URLDecoder.decode(titleArg, "UTF-8")
            } catch (e: Exception) {
                // If decoding fails, try simple replacement of + with spaces
                titleArg.replace("+", " ")
            }
            
            // Fallback to savedStateHandle if arguments are not available (for backward compatibility)
            val habitId = habitIdArg?.takeIf { it.isNotBlank() }
                ?: backStackEntry.savedStateHandle.get<String>("timer_habit_id")
                ?: navController.previousBackStackEntry?.savedStateHandle?.get<String>("timer_habit_id")
            val label = decodedTitleArg.takeIf { it.isNotBlank() }
                ?: backStackEntry.savedStateHandle.get<String>("timer_label")
                ?: navController.previousBackStackEntry?.savedStateHandle?.get<String>("timer_label")
                ?: "Habit"
            val duration = durationArg.takeIf { it > 0 }
                ?: backStackEntry.savedStateHandle.get<Int>("timer_duration")
                ?: navController.previousBackStackEntry?.savedStateHandle?.get<Int>("timer_duration")
                ?: 10
            
            android.util.Log.d("Navigation", "habit_timer route: habitId=$habitId (from arg: $habitIdArg), label=$label, duration=$duration")
            
            // CRITICAL: Log error if habitId is still null
            if (habitId == null) {
                android.util.Log.e("Navigation", "❌ habitId is NULL in habit_timer route! Arguments: habitId=$habitIdArg, title=$titleArg, duration=$durationArg")
            }
            
            // Check if it's a stretching habit
            val isStretching = label.lowercase().contains("stretch") || label.lowercase().contains("stretching")
            
            if (isStretching) {
                StretchingScreen(
                    habitTitle = label,
                    durationMinutes = duration,
                    habitId = habitId,
                    userId = userId ?: "",
                    onComplete = { navController.popBackStack() },
                    onNavigateBack = { navController.popBackStack() }
                )
            } else {
                HabitTimerScreen(
                    habitTitle = label,
                    durationMinutes = duration,
                    habitId = habitId,
                    userId = userId ?: "",
                    onComplete = { navController.popBackStack() },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        composable("meal_recommendation") {
            val userId = getAuthenticatedUserId()

            MealRecommendationScreen(
                userId = userId ?: "",
                onBack = { navController.popBackStack() },
                onNavigateToSubscription = { navController.navigate("subscription") }
            )
        }

        composable("meal_log") { backStackEntry ->
            val userId = getAuthenticatedUserId()

            MealCaptureScreen(
                userId = userId ?: "",
                navBackStackEntry = backStackEntry,
                onBack = {
                    navController.popBackStack()
                },
                onMealSaved = {
                    navController.popBackStack()
                },
                onNavigateToSavedMeals = {
                    navController.navigate("saved_meals")
                },
                onNavigateToMealRecommendation = {
                    DebugLogger.logDebug("Navigation", "Navigate to meal recommendation from meal log")
                    navController.navigate("meal_recommendation")
                },
                onNavigateToRecipeCapture = {
                    navController.navigate("recipe_capture")
                },
                onNavigateToMyRecipes = {
                    navController.navigate("my_recipes")
                }
            )
        }

        composable("saved_meals") {
            val viewModel: AuthViewModel = viewModel()
            val authState by viewModel.authState.collectAsState()
            val currentUser = (authState as? AuthState.Authenticated)?.user
            val userId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId()
            
            android.util.Log.w("MainNavHost", "💾💾💾 SavedMealsScreen - userId: $userId 💾💾💾")
            
            if (userId == null || userId.isBlank()) {
                // Wait for auth - show loading
                android.util.Log.w("MainNavHost", "💾💾💾 SavedMealsScreen - userId is null/blank, showing loading 💾💾💾")
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                return@composable
            }

            SavedMealsScreen(
                userId = userId ?: "",
                onNavigateBack = {
                    navController.popBackStack()
                },
                onMealSelected = { savedMeal ->
                    // Pass the selected meal back to the meal capture screen
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        "selected_saved_meal",
                        savedMeal
                    )
                    navController.popBackStack()
                }
            )
        }

        composable("recipe_capture") {
            val userId = getAuthenticatedUserId()

            RecipeCaptureScreen(
                userId = userId ?: "",
                onBack = {
                    navController.popBackStack()
                },
                onRecipeSaved = {
                    navController.popBackStack()
                }
            )
        }

        composable("supplement_photo_log") {
            val userId = getAuthenticatedUserId()

            SupplementPhotoCaptureScreen(
                userId = userId ?: "",
                onBack = {
                    navController.popBackStack()
                },
                onSupplementSaved = {
                    navController.popBackStack()
                }
            )
        }



        composable("water_log") {
            val userId = getAuthenticatedUserId()

            WaterLogScreen(
                userId = userId ?: "",
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("weight_log") {
            val userId = getAuthenticatedUserId()

            WeightLogScreen(
                userId = userId ?: "",
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("sleep_log") {
            val userId = getAuthenticatedUserId()

            SleepLogScreen(
                userId = userId ?: "",
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("workout_log") {
            val userId = getAuthenticatedUserId()

            WorkoutLogScreen(
                userId = userId ?: "",
                onBack = {
                    navController.popBackStack()
                }
            )
        }


        composable("profile") {
            val userId = getAuthenticatedUserId()
            val viewModel: AuthViewModel = viewModel()
            val context = LocalContext.current
            val preferencesManager = remember { com.coachie.app.data.local.PreferencesManager(context) }

            ProfileScreen(
                onSignOut = {
                    DebugLogger.logDebug("ProfileScreen", "Sign out requested")
                    viewModel.signOut(preferencesManager)
                    navController.navigate("auth") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToPersonalInfoEdit = {
                    DebugLogger.logDebug("ProfileScreen", "Personal info edit requested")
                    navController.navigate("personal_info_edit")
                },
                onNavigateToGoalsEdit = {
                    DebugLogger.logDebug("ProfileScreen", "Goals edit requested")
                    navController.navigate("goals_edit")
                },
                onNavigateToDietaryPreferencesEdit = {
                    DebugLogger.logDebug("ProfileScreen", "Dietary preferences edit requested")
                    navController.navigate("dietary_preferences_edit")
                },
                onNavigateToSettings = {
                    DebugLogger.logDebug("ProfileScreen", "Settings requested")
                    navController.navigate("settings")
                },
                onNavigateToMenstrualTracker = {
                    DebugLogger.logDebug("ProfileScreen", "Menstrual tracker requested")
                    navController.navigate("menstrual_tracker")
                },
                onNavigateToMoodTracker = {
                    DebugLogger.logDebug("ProfileScreen", "Mood tracker requested")
                    navController.navigate("mood_tracker")
                },
                onNavigateToVoiceSettings = {
                    DebugLogger.logDebug("ProfileScreen", "Voice settings requested")
                    navController.navigate("voice_settings")
                },
                refreshTrigger = profileRefreshTrigger
            )
        }

        composable("health_tracking") {
            val authViewModel: AuthViewModel = viewModel()
            val authState by authViewModel.authState.collectAsState()
            val currentUser = (authState as? AuthState.Authenticated)?.user
            
            HealthTrackingDashboardScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMealLog = { navController.navigate("meal_log") },
                onNavigateToSupplementLog = { navController.navigate("supplement_photo_log") },
                onNavigateToWorkoutLog = { navController.navigate("workout_log") },
                onNavigateToSleepLog = { navController.navigate("sleep_log") },
                onNavigateToWaterLog = { navController.navigate("water_log") },
                onNavigateToWeightLog = { navController.navigate("weight_log") },
                onNavigateToCycleTracker = { navController.navigate("menstrual_tracker") },
                onNavigateToDailyLog = { navController.navigate("daily_log") },
                onNavigateToMicronutrientTracker = { navController.navigate("micronutrients") },
                onNavigateToSugarDetail = { navController.navigate("sugar_intake_detail") }
            )
        }
        
        composable("sugar_intake_detail") {
            val authViewModel: AuthViewModel = viewModel()
            val authState by authViewModel.authState.collectAsState()
            val currentUser = (authState as? AuthState.Authenticated)?.user
            
            if (currentUser != null) {
                SugarIntakeDetailScreen(
                    userId = currentUser.uid,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
        
        composable("wellness") {
            val authViewModel: AuthViewModel = viewModel()
            val authState by authViewModel.authState.collectAsState()
            val authenticatedUserId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId()
            if (authenticatedUserId == null) {
                // User not authenticated - return early
                return@composable
            }
            
            val dashboardViewModel = viewModel<HomeDashboardViewModel>(
                key = authenticatedUserId,
                factory = HomeDashboardViewModel.Factory(
                    repository = com.coachie.app.data.FirebaseRepository.getInstance(),
                    userId = authenticatedUserId
                )
            )
            
            WellnessDashboardScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHabits = { navController.navigate("habits") },
                onNavigateToMeditation = { navController.navigate("meditation") },
                onNavigateToBreathingExercises = { navController.navigate("breathing_exercises") },
                onNavigateToJournal = { navController.navigate("journal_flow") },
                onNavigateToJournalHistory = { navController.navigate("journal_history") },
                onNavigateToMyWins = { navController.navigate("my_wins") },
                onNavigateToMoodTracker = { navController.navigate("mood_tracker") },
                onNavigateToSocialMediaBreak = { navController.navigate("social_media_break") },
                dashboardViewModel = dashboardViewModel
            )
        }
        
        composable("breathing_exercises") {
            val authenticatedUserId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId()
            if (authenticatedUserId == null) {
                return@composable
            }
            
            BreathingExercisesScreen(
                onNavigateBack = { navController.popBackStack() },
                userId = authenticatedUserId
            )
        }
        
        composable("social_media_break") {
            val authenticatedUserId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId()
            if (authenticatedUserId == null) {
                return@composable
            }
            
            SocialMediaBreakScreen(
                userId = authenticatedUserId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("permissions") {
            PermissionsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("habits") {
            HabitsDashboardScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMyHabits = {
                    android.util.Log.d("Navigation", "Attempting to navigate to my_habits")
                    try {
                        navController.navigate("my_habits")
                        android.util.Log.d("Navigation", "Successfully navigated to my_habits")
                    } catch (e: Exception) {
                        android.util.Log.e("Navigation", "Failed to navigate to my_habits", e)
                    }
                },
                onNavigateToHabitTemplates = { navController.navigate("habit_templates") },
                onNavigateToHabitProgress = { navController.navigate("habit_progress") },
                onNavigateToHabitIntelligence = { navController.navigate("habit_intelligence") }
            )
        }
        
        composable("habit_templates") { backStackEntry ->
            val userId = getAuthenticatedUserId()

            HabitTemplatesScreen(
                onBack = { navController.popBackStack() },
                            onCreateHabit = { template ->
                                // Navigate to habits screen to show the newly created habit
                                android.util.Log.d("HabitTemplates", "Habit created, navigating to my_habits")
                                navController.navigate("my_habits") {
                                    // Pop back to habits screen if it exists, otherwise just navigate
                                    popUpTo("habits") { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                userId = userId ?: ""
            )
        }

        composable("community") {
            val authViewModel: AuthViewModel = viewModel()
            val authState by authViewModel.authState.collectAsState()
            val currentUser = (authState as? AuthState.Authenticated)?.user
            val userId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: return@composable
            
            CommunityScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCircleJoin = { navController.navigate("circle_join") },
                onNavigateToCircleCreate = { navController.navigate("circle_create") },
                onNavigateToCircleDetail = { circleId ->
                    navController.navigate("circle_detail/$circleId")
                },
                onNavigateToForumDetail = { forumId ->
                    navController.navigate("forum_detail/$forumId")
                },
                onNavigateToUserSearch = { navController.navigate("user_search") },
                onNavigateToFriends = { 
                    navController.navigate("friends_list") {
                        // When returning from friends list, refresh circles
                        popUpTo("community") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToMessages = { navController.navigate("messaging") },
                onNavigateToSharedRecipes = { navController.navigate("shared_recipes") },
                userId = userId ?: ""
            )
        }

        composable("circle_create") {
            val authViewModel: AuthViewModel = viewModel()
            val authState by authViewModel.authState.collectAsState()
            val currentUser = (authState as? AuthState.Authenticated)?.user
            val userId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: return@composable
            
            CircleCreateScreen(
                onNavigateBack = { navController.popBackStack() },
                onCreateSuccess = {
                    navController.popBackStack() // Go back to community screen
                },
                userId = userId ?: ""
            )
        }

        composable("circle_join") {
            val authViewModel: AuthViewModel = viewModel()
            val authState by authViewModel.authState.collectAsState()
            val currentUser = (authState as? AuthState.Authenticated)?.user
            val userId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: return@composable
            
            // TODO: Get user goal and tendency from profile
            // For now, pass null and let the screen handle it
            CircleJoinScreen(
                onNavigateBack = { navController.popBackStack() },
                onJoinSuccess = {
                    navController.popBackStack()
                    // Optionally refresh community screen
                },
                userId = userId ?: "",
                userGoal = null, // TODO: Get from user profile
                userTendency = null, // TODO: Get from behavioral profile
                onNavigateToSubscription = { navController.navigate("subscription") }
            )
        }

        composable(
            route = "circle_detail/{circleId}",
            arguments = listOf(
                navArgument("circleId") {
                    type = androidx.navigation.NavType.StringType
                }
            )
        ) { backStackEntry ->
            val authViewModel: AuthViewModel = viewModel()
            val authState by authViewModel.authState.collectAsState()
            val currentUser = (authState as? AuthState.Authenticated)?.user
            val userId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: return@composable
            val circleId = backStackEntry.arguments?.getString("circleId") ?: ""
            
            CircleDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                circleId = circleId,
                userId = userId ?: "",
                onNavigateToInvite = { inviteCircleId ->
                    // Navigate to user search with circle invite context
                    android.util.Log.d("CircleInvite", "Navigating to user search with circleId: $inviteCircleId")
                    navController.navigate("user_search_invite/$inviteCircleId")
                },
                onNavigateToRecipe = { recipeId ->
                    // Navigate to recipe detail screen
                    navController.currentBackStackEntry?.savedStateHandle?.set("recipe_id", recipeId)
                    navController.navigate("recipe_detail")
                }
            )
        }

        // User search route with circleId for invites (must be defined before general user_search route)
        composable(
            route = "user_search_invite/{circleId}",
            arguments = listOf(
                navArgument("circleId") {
                    type = androidx.navigation.NavType.StringType
                }
            )
        ) { backStackEntry ->
            val authViewModel: AuthViewModel = viewModel()
            val authState by authViewModel.authState.collectAsState()
            val currentUser = (authState as? AuthState.Authenticated)?.user
            val userId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: return@composable
            val repository = FirebaseRepository.getInstance()
            val scope = rememberCoroutineScope()
            
            // Get circleId from route arguments
            val circleId = backStackEntry.arguments?.getString("circleId") ?: ""
            android.util.Log.d("CircleInvite", "UserSearchScreen - circleId from route: '$circleId'")
            
            UserSearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onUserSelected = { selectedUserId ->
                    // Navigate to friends list or show options
                    navController.navigate("friends_list")
                },
                onInviteToCircle = null, // Not needed when circleId is provided
                userId = userId ?: "",
                circleId = circleId // Pass circleId directly for internal handling
            )
        }
        
        // User search route without circleId (for general user search)
        composable("user_search") { backStackEntry ->
            val authViewModel: AuthViewModel = viewModel()
            val authState by authViewModel.authState.collectAsState()
            val currentUser = (authState as? AuthState.Authenticated)?.user
            val userId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: return@composable
            
            UserSearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onUserSelected = { selectedUserId ->
                    // Navigate to friends list or show options
                    navController.navigate("friends_list")
                },
                onInviteToCircle = null, // No circle invite option for general search
                userId = userId ?: ""
            )
        }

        composable("friends_list") {
            val authViewModel: AuthViewModel = viewModel()
            val authState by authViewModel.authState.collectAsState()
            val currentUser = (authState as? AuthState.Authenticated)?.user
            val userId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: return@composable
            
            FriendsListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToUserSearch = { navController.navigate("user_search") },
                onNavigateToMessage = { friendUserId ->
                    navController.navigate("messaging?userId=$friendUserId")
                },
                userId = userId ?: ""
            )
        }

        // Messaging route without userId parameter - shows all conversations
        composable("messaging") { backStackEntry ->
            val authViewModel: AuthViewModel = viewModel()
            val authState by authViewModel.authState.collectAsState()
            val currentUser = (authState as? AuthState.Authenticated)?.user
            val currentUserId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: return@composable
            
            // Check if conversationId was passed from notification intent
            val conversationIdFromIntent = (backStackEntry.arguments?.getString("conversationId"))
                ?: (androidx.compose.ui.platform.LocalContext.current as? android.app.Activity)?.intent?.getStringExtra("conversationId")
            
            MessagingScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToConversation = { convId ->
                    // CRASH DEBUG: Navigate to conversation using query parameter
                    android.util.Log.d("MESSAGING_CRASH", "Navigating to conversation: messaging?userId=$convId")
                    navController.navigate("messaging?userId=$convId")
                },
                userId = currentUserId,
                conversationId = conversationIdFromIntent
            )
        }
        
        // Messaging route with userId query parameter - shows specific conversation
        composable(
            route = "messaging?userId={userId}",
            arguments = listOf(
                navArgument("userId") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val authViewModel: AuthViewModel = viewModel()
            val authState by authViewModel.authState.collectAsState()
            val currentUser = (authState as? AuthState.Authenticated)?.user
            val currentUserId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: return@composable
            val conversationUserId = backStackEntry.arguments?.getString("userId")
            
            // CRASH DEBUG: Log navigation to help debug crashes
            android.util.Log.d("MESSAGING_CRASH", "Messaging screen loaded - userId: $currentUserId, conversationId: $conversationUserId")
            
            MessagingScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToConversation = { convId ->
                    // CRASH DEBUG: Navigate to conversation using query parameter
                    android.util.Log.d("MESSAGING_CRASH", "Navigating to conversation: messaging?userId=$convId")
                    navController.navigate("messaging?userId=$convId")
                },
                userId = currentUserId,
                conversationId = conversationUserId
            )
        }

        composable("profile_edit") {
            val userId = getAuthenticatedUserId()

            val repository = com.coachie.app.data.FirebaseRepository.getInstance()
            var userProfile by remember { mutableStateOf<com.coachie.app.data.model.UserProfile?>(null) }
            var userGoals by remember { mutableStateOf<Map<String, Any>?>(null) }
            var isLoadingProfile by remember { mutableStateOf(true) }
            var profileEditError by remember { mutableStateOf<String?>(null) }
 
            LaunchedEffect(userId) {
                userId?.let { uid ->
                    if (uid.isNotBlank()) {
                        val profileResult = repository.getUserProfile(uid)
                        userProfile = profileResult.getOrNull()
                        val goalsResult = repository.getUserGoals(uid)
                        userGoals = goalsResult.getOrNull()
                    }
                }
                isLoadingProfile = false
            }
 
            ProfileEditScreen(
                userId = userId ?: "",
                userProfile = userProfile,
                userGoals = userGoals,
                isLoading = isLoadingProfile,
                errorMessage = profileEditError,
                onSaveProfile = { profile, updatedGoals ->
                    // Validate user ID before saving
                    android.util.Log.d("ProfileEdit", "Attempting to save profile - userId: '$userId', profile.uid: '${profile.uid}'")
                    if (userId.isNullOrBlank()) {
                        android.util.Log.e("ProfileEdit", "Cannot save profile: userId is blank")
                        profileEditError = "Authentication error. Opening settings to help you sign back in."
                        // Open app settings to help with authentication issues
                        val permissionsManager = com.coachie.app.data.PermissionsManager(context, com.coachie.app.data.local.PreferencesManager(context))
                        permissionsManager.openAppSettings()
                        return@ProfileEditScreen
                    }
                    if (profile.uid.isBlank()) {
                        android.util.Log.e("ProfileEdit", "Cannot save profile: profile.uid is blank")
                        profileEditError = "Profile error. Please try again."
                        return@ProfileEditScreen
                    }

                    // Clear any previous errors
                    profileEditError = null

                    // Save profile
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        val saveResult = repository.saveUserProfile(profile)
                        android.util.Log.d("ProfileEdit", "Profile save result: ${saveResult.isSuccess} ${saveResult.exceptionOrNull()?.message ?: ""}")

                        if (saveResult.isSuccess) {
                            userProfile = profile
                            android.util.Log.d("ProfileEdit", "Updated local userProfile: name=${profile.name}, activity=${profile.activityLevel}")

                            // Save updated goals if any
                            if (updatedGoals.isNotEmpty()) {
                                userId?.let { uid ->
                                    val goalsResult = repository.updateUserSettings(uid, updatedGoals)
                                    android.util.Log.d("ProfileEdit", "Goals save result: ${goalsResult.isSuccess} ${goalsResult.exceptionOrNull()?.message ?: ""}")

                                    // Refresh goals data
                                    val refreshedGoalsResult = repository.getUserGoals(uid)
                                    userGoals = refreshedGoalsResult.getOrNull()
                                }
                                android.util.Log.d("ProfileEdit", "Refreshed goals: $userGoals")
                            }
                        } else {
                            android.util.Log.e("ProfileEdit", "Failed to save profile", saveResult.exceptionOrNull())
                        }
                    }
                    // Trigger profile screen refresh
                    profileRefreshTrigger++
                    navController.popBackStack()
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }

        composable("personal_info_edit") {
            val userId = getAuthenticatedUserId()

            val repository = com.coachie.app.data.FirebaseRepository.getInstance()
            var userProfile by remember { mutableStateOf<com.coachie.app.data.model.UserProfile?>(null) }
            var userGoals by remember { mutableStateOf<Map<String, Any?>?>(null) }
            var isLoadingProfile by remember { mutableStateOf(true) }
            var profileEditError by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(userId) {
                userId?.let { uid ->
                    if (uid.isNotBlank()) {
                        try {
                            val profileResult = repository.getUserProfile(uid)
                            val goalsResult = repository.getUserGoals(uid)
                            userProfile = profileResult.getOrNull()
                            userGoals = goalsResult.getOrNull()
                        } catch (e: Exception) {
                            profileEditError = "Failed to load data: ${e.message}"
                        }
                    }
                }
                isLoadingProfile = false
            }

            PersonalInfoEditScreen(
                userId = userId ?: "",
                userProfile = userProfile,
                userGoals = userGoals,
                isLoading = isLoadingProfile,
                errorMessage = profileEditError,
                onSaveProfile = { profile, _ ->
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        isLoadingProfile = true
                        profileEditError = null
                        android.util.Log.d("PersonalInfoEdit", "About to save profile: heightCm=${profile.heightCm}, currentWeight=${profile.currentWeight}, goalWeight=${profile.goalWeight}")
                        val saveResult = repository.saveUserProfile(profile)
                        if (saveResult.isSuccess) {
                            android.util.Log.d("PersonalInfoEdit", "Profile saved successfully, updating local state")
                            // Update local state with saved profile
                            userProfile = profile
                            // Also reload from Firestore to ensure we have the latest data
                            userId?.let { uid ->
                                val reloadResult = repository.getUserProfile(uid)
                                reloadResult.onSuccess { reloadedProfile ->
                                    if (reloadedProfile != null) {
                                        userProfile = reloadedProfile
                                        android.util.Log.d("PersonalInfoEdit", "Profile reloaded: name=${reloadedProfile.name}, heightCm=${reloadedProfile.heightCm}, currentWeight=${reloadedProfile.currentWeight}")
                                    }
                                }.onFailure { error ->
                                    android.util.Log.e("PersonalInfoEdit", "Failed to reload profile after save", error)
                                }
                            }
                            isLoadingProfile = false
                            // Trigger profile refresh on ProfileScreen
                            profileRefreshTrigger++
                            navController.popBackStack()
                        } else {
                            android.util.Log.e("PersonalInfoEdit", "Failed to save profile", saveResult.exceptionOrNull())
                            profileEditError = "Failed to save profile: ${saveResult.exceptionOrNull()?.message ?: "Unknown error"}"
                            isLoadingProfile = false
                        }
                    }
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }

        composable("physical_stats_edit") {
            val userId = getAuthenticatedUserId()

            val repository = com.coachie.app.data.FirebaseRepository.getInstance()
            var userProfile by remember { mutableStateOf<com.coachie.app.data.model.UserProfile?>(null) }
            var isLoadingProfile by remember { mutableStateOf(true) }
            var profileEditError by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(userId) {
                userId?.let { uid ->
                    if (uid.isNotBlank()) {
                        val profileResult = repository.getUserProfile(uid)
                        userProfile = profileResult.getOrNull()
                    }
                }
                isLoadingProfile = false
            }

            PhysicalStatsEditScreen(
                userId = userId ?: "",
                userProfile = userProfile,
                isLoading = isLoadingProfile,
                errorMessage = profileEditError,
                onSaveProfile = { profile, _ ->
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        isLoadingProfile = true
                        profileEditError = null
                        val saveResult = repository.saveUserProfile(profile)
                        if (saveResult.isSuccess) {
                            android.util.Log.d("PhysicalStatsEdit", "Profile saved successfully, updating local state")
                            // Update local state with saved profile
                            userProfile = profile
                            // Also reload from Firestore to ensure we have the latest data
                            userId?.let { uid ->
                                val reloadResult = repository.getUserProfile(uid)
                                reloadResult.onSuccess { reloadedProfile ->
                                    if (reloadedProfile != null) {
                                        userProfile = reloadedProfile
                                        android.util.Log.d("PhysicalStatsEdit", "Profile reloaded: weight=${reloadedProfile.currentWeight}")
                                    }
                                }.onFailure { error ->
                                    android.util.Log.e("PhysicalStatsEdit", "Failed to reload profile after save", error)
                                }
                            }
                            isLoadingProfile = false
                            // Trigger profile refresh on ProfileScreen
                            profileRefreshTrigger++
                            navController.popBackStack()
                        } else {
                            android.util.Log.e("PhysicalStatsEdit", "Failed to save profile", saveResult.exceptionOrNull())
                            profileEditError = "Failed to save profile: ${saveResult.exceptionOrNull()?.message ?: "Unknown error"}"
                            isLoadingProfile = false
                        }
                    }
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }

        composable("preferences_edit") {
            val userId = getAuthenticatedUserId()

            val repository = com.coachie.app.data.FirebaseRepository.getInstance()
            var userProfile by remember { mutableStateOf<com.coachie.app.data.model.UserProfile?>(null) }
            var isLoadingProfile by remember { mutableStateOf(true) }
            var profileEditError by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(userId) {
                userId?.let { uid ->
                    if (uid.isNotBlank()) {
                        val profileResult = repository.getUserProfile(uid)
                        userProfile = profileResult.getOrNull()
                    }
                }
                isLoadingProfile = false
            }

            PreferencesEditScreen(
                userId = userId ?: "",
                userProfile = userProfile,
                isLoading = isLoadingProfile,
                errorMessage = profileEditError,
                onSaveProfile = { profile, goalsUpdate ->
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        isLoadingProfile = true
                        profileEditError = null
                        val saveResult = repository.saveUserProfile(profile)
                        // Also save goals if provided
                        if (saveResult.isSuccess && goalsUpdate.isNotEmpty()) {
                            userId?.let { uid ->
                                val goalsResult = repository.saveUserGoals(uid, goalsUpdate)
                                if (goalsResult.isFailure) {
                                    android.util.Log.e("PreferencesEdit", "Failed to save goals", goalsResult.exceptionOrNull())
                                }
                            }
                        }
                        if (saveResult.isSuccess) {
                            android.util.Log.d("PreferencesEdit", "Profile saved successfully, updating local state")
                            // Update local state with saved profile
                            userProfile = profile
                            // Also reload from Firestore to ensure we have the latest data
                            userId?.let { uid ->
                                val reloadResult = repository.getUserProfile(uid)
                                reloadResult.onSuccess { reloadedProfile ->
                                    if (reloadedProfile != null) {
                                        userProfile = reloadedProfile
                                        android.util.Log.d("PreferencesEdit", "Profile reloaded: dietaryPreference=${reloadedProfile.dietaryPreference}")
                                    }
                                }.onFailure { error ->
                                    android.util.Log.e("PreferencesEdit", "Failed to reload profile after save", error)
                                }
                            }
                            isLoadingProfile = false
                            // Trigger profile refresh on ProfileScreen
                            profileRefreshTrigger++
                            navController.popBackStack()
                        } else {
                            android.util.Log.e("PreferencesEdit", "Failed to save profile", saveResult.exceptionOrNull())
                            profileEditError = "Failed to save profile: ${saveResult.exceptionOrNull()?.message ?: "Unknown error"}"
                            isLoadingProfile = false
                        }
                    }
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }

        composable("settings") {
            val userId = getAuthenticatedUserId()
            val authViewModel: AuthViewModel = viewModel()
            val context = LocalContext.current
            val preferencesManager = remember { com.coachie.app.data.local.PreferencesManager(context) }
            
            SettingsScreen(
                userId = userId ?: "",
                onBack = { navController.popBackStack() },
                onNavigateToVoiceSettings = { navController.navigate("voice_settings") },
                onNavigateToPermissions = { navController.navigate("permissions") },
                onNavigateToPersonalInfo = { navController.navigate("personal_info_edit") },
                onNavigateToPhysicalStats = { navController.navigate("physical_stats_edit") },
                onNavigateToPreferences = { navController.navigate("preferences_edit") },
                onNavigateToSubscription = { navController.navigate("subscription") },
                onAccountDeleted = {
                    // Account is already deleted, so user is logged out
                    // Navigate to auth screen immediately and clear back stack
                    android.util.Log.d("MainNavHost", "Account deleted - navigating to auth screen")
                    try {
                        // Try to sign out (may fail if account already deleted, which is fine)
                        authViewModel.signOut(preferencesManager)
                    } catch (e: Exception) {
                        android.util.Log.w("MainNavHost", "Sign out failed (expected if account already deleted): ${e.message}")
                    }
                    // Always navigate to auth screen, even if signOut fails
                    navController.navigate("auth") {
                        // Clear entire back stack
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToFTUE = {
                    // Navigate to FTUE screen after clearing data
                    // Refresh goals to trigger navigation logic
                    authViewModel.refreshUserGoals()
                    navController.navigate("set_goals") {
                        popUpTo("settings") { inclusive = true }
                    }
                }
            )
        }

        composable("subscription") {
            SubscriptionScreen(
                onNavigateBack = { navController.popBackStack() },
                onUpgrade = {
                    // TODO: Implement Google Play Billing
                    android.util.Log.d("SubscriptionScreen", "Upgrade clicked - implement billing")
                }
            )
        }

        // Alias routes for profile editing - both point to the same preferences screen
        composable("goals_edit") {
            val userId = getAuthenticatedUserId()

            val repository = com.coachie.app.data.FirebaseRepository.getInstance()
            var userProfile by remember { mutableStateOf<com.coachie.app.data.model.UserProfile?>(null) }
            var userGoals by remember { mutableStateOf<Map<String, Any?>?>(null) }
            var isLoadingProfile by remember { mutableStateOf(true) }
            var profileEditError by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(userId) {
                userId?.let { uid ->
                    if (uid.isNotBlank()) {
                        try {
                            val profileResult = repository.getUserProfile(uid)
                            val goalsResult = repository.getUserGoals(uid)
                            userProfile = profileResult.getOrNull()
                            userGoals = goalsResult.getOrNull()
                            isLoadingProfile = false
                        } catch (e: Exception) {
                            profileEditError = "Failed to load data: ${e.message}"
                            isLoadingProfile = false
                        }
                    }
                }
            }

            GoalsEditScreen(
                userId = userId ?: "",
                userProfile = userProfile,
                userGoals = userGoals,
                isLoading = isLoadingProfile,
                errorMessage = profileEditError,
                onSaveProfile = { profile: com.coachie.app.data.model.UserProfile, goalsUpdates: Map<String, Any?> ->
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        // Save goals separately from profile
                        userId?.let { uid ->
                            val saveResult = repository.saveUserGoals(uid, goalsUpdates)
                            if (saveResult.isSuccess) {
                                android.util.Log.d("GoalsEdit", "Goals saved successfully")
                                navController.popBackStack()
                            } else {
                                android.util.Log.e("GoalsEdit", "Failed to save goals", saveResult.exceptionOrNull())
                                profileEditError = "Failed to save goals: ${saveResult.exceptionOrNull()?.message ?: "Unknown error"}"
                            }
                        }
                    }
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }

        composable("dietary_preferences_edit") {
            val userId = getAuthenticatedUserId()

            val repository = com.coachie.app.data.FirebaseRepository.getInstance()
            var userProfile by remember { mutableStateOf<com.coachie.app.data.model.UserProfile?>(null) }
            var isLoadingProfile by remember { mutableStateOf(true) }
            var profileEditError by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(userId) {
                userId?.let { uid ->
                    if (uid.isNotBlank()) {
                        try {
                            val profileResult = repository.getUserProfile(uid)
                            userProfile = profileResult.getOrNull()
                            isLoadingProfile = false
                        } catch (e: Exception) {
                            profileEditError = "Failed to load profile: ${e.message}"
                            isLoadingProfile = false
                        }
                    }
                }
            }

            PreferencesEditScreen(
                userId = userId ?: "",
                userProfile = userProfile,
                isLoading = isLoadingProfile,
                errorMessage = profileEditError,
                onSaveProfile = { profile, updates ->
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        val saveResult = repository.saveUserProfile(profile)
                        if (saveResult.isSuccess) {
                            android.util.Log.d("DietaryPreferencesEdit", "Profile saved successfully")
                            navController.popBackStack()
                        } else {
                            android.util.Log.e("DietaryPreferencesEdit", "Failed to save profile", saveResult.exceptionOrNull())
                            profileEditError = "Failed to save profile: ${saveResult.exceptionOrNull()?.message ?: "Unknown error"}"
                        }
                    }
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }

        composable("achievements") {
            AchievementsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("help") {
            HelpScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("calories_detail") {
            CaloriesDetailScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("streak_detail") {
            StreakDetailScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("goals_breakdown") {
            GoalsBreakdownScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // Debug screen removed - no longer accessible

        
        composable("weekly_blueprint") { backStackEntry ->
            val authViewModel: AuthViewModel = viewModel()
            val authState by authViewModel.authState.collectAsState()
            val currentUser = (authState as? AuthState.Authenticated)?.user
            val userId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: return@composable
            
            WeeklyBlueprintScreen(
                onNavigateBack = { navController.popBackStack() },
                userId = userId ?: "",
                onNavigateToSubscription = { navController.navigate("subscription") }
            )
        }
        
        composable("quests") { backStackEntry ->
            val authViewModel: AuthViewModel = viewModel()
            val authState by authViewModel.authState.collectAsState()
            val currentUser = (authState as? AuthState.Authenticated)?.user
            val userId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: return@composable
            
            QuestsScreen(
                onNavigateBack = { navController.popBackStack() },
                userId = userId ?: "",
                onNavigateToCircle = { circleId ->
                    navController.navigate("circle_detail/$circleId")
                }
            )
        }
        
        composable("insights") { backStackEntry ->
            val authViewModel: AuthViewModel = viewModel()
            val authState by authViewModel.authState.collectAsState()
            val currentUser = (authState as? AuthState.Authenticated)?.user
            val userId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: return@composable
            
            InsightsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHabitCreation = { navController.navigate("habit_creation") },
                onNavigateToHabitTemplates = { navController.navigate("habit_templates") },
                onNavigateToGoalsEdit = { navController.navigate("goals_edit") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToSubscription = { navController.navigate("subscription") },
                onNavigateToMealLog = { navController.navigate("meal_log") },
                onNavigateToMeditation = { navController.navigate("meditation") },
                onNavigateToWeeklyBlueprint = { navController.navigate("weekly_blueprint") },
                onNavigateToWellness = { navController.navigate("wellness") },
                onNavigateToWaterLog = { navController.navigate("water_log") },
                onNavigateToHabits = { navController.navigate("my_habits") },
                userId = userId ?: ""
            )
        }

        composable("ai_chat") {
            // Check FirebaseAuth directly for immediate authentication status
            val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            val viewModel: AuthViewModel = viewModel()
            val authState by viewModel.authState.collectAsState()
            
            // If FirebaseAuth has a user, use it immediately (more reliable than waiting for ViewModel state)
            if (firebaseUser != null && firebaseUser.uid.isNotBlank() && !firebaseUser.isAnonymous && !firebaseUser.email.isNullOrBlank()) {
                // User is authenticated via FirebaseAuth, show chat immediately
                CoachChatScreen(
                    userId = firebaseUser.uid,
                    preferencesManager = null,
                    showConfetti = false,
                    onConfettiShown = {},
                    onNavigateToSubscription = { navController.navigate("subscription") }
                )
            } else {
                // No user in FirebaseAuth, check ViewModel state
                val currentAuthState = authState // Store in local variable for smart cast
                when (currentAuthState) {
                    is AuthState.Authenticated -> {
                        val currentUser = currentAuthState.user
                        if (currentUser.uid.isNotBlank() && !currentUser.isAnonymous && !currentUser.email.isNullOrBlank()) {
                            CoachChatScreen(
                                userId = currentUser.uid,
                                preferencesManager = null,
                                showConfetti = false,
                                onConfettiShown = {},
                                onNavigateToSubscription = { navController.navigate("subscription") }
                            )
                        } else {
                            // Invalid user, redirect to auth
                            LaunchedEffect(Unit) {
                                navController.navigate("auth?returnTo=ai_chat") {
                                    popUpTo("ai_chat") { inclusive = true }
                                }
                            }
                        }
                    }
                    is AuthState.Unauthenticated -> {
                        // User is not authenticated, redirect to auth with return destination
                        LaunchedEffect(Unit) {
                            navController.navigate("auth?returnTo=ai_chat") {
                                popUpTo("ai_chat") { inclusive = true }
                            }
                        }
                    }
                    is AuthState.Loading -> {
                        // Auth state is loading, show loading indicator (but don't redirect)
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is AuthState.Error -> {
                        // Auth error, redirect to auth
                        LaunchedEffect(Unit) {
                            navController.navigate("auth?returnTo=ai_chat") {
                                popUpTo("ai_chat") { inclusive = true }
                            }
                        }
                    }
                }
            }
        }
        
        composable("notification_detail/{title}/{message}/{deepLink?}") { backStackEntry ->
            val encodedTitle = backStackEntry.arguments?.getString("title") ?: "Notification"
            val encodedMessage = backStackEntry.arguments?.getString("message") ?: ""
            val encodedDeepLink = backStackEntry.arguments?.getString("deepLink") ?: ""
            val title = java.net.URLDecoder.decode(encodedTitle, "UTF-8")
            val message = java.net.URLDecoder.decode(encodedMessage, "UTF-8")
            val deepLink = if (encodedDeepLink.isNotBlank()) {
                java.net.URLDecoder.decode(encodedDeepLink, "UTF-8")
            } else {
                null
            }
            
            NotificationDetailScreen(
                title = title,
                message = message,
                deepLinkTarget = deepLink,
                onBack = { navController.popBackStack() },
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo("notification_detail") { inclusive = true }
                    }
                }
            )
        }

        composable("forum_detail/{forumId}") { backStackEntry ->
            val forumId = backStackEntry.arguments?.getString("forumId") ?: ""

            ForumDetailScreen(
                forumId = forumId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRecipe = { recipeId ->
                    // Navigate to recipe detail screen
                    navController.currentBackStackEntry?.savedStateHandle?.set("recipe_id", recipeId)
                    navController.navigate("recipe_detail")
                }
            )
        }

        composable("shared_recipes") {
            SharedRecipesScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("my_recipes") {
            val userId = getAuthenticatedUserId()
            
            if (userId != null) {
                MyRecipesScreen(
                    onNavigateToSubscription = { navController.navigate("subscription") },
                    userId = userId,
                    onNavigateBack = { navController.popBackStack() },
                    onRecipeSelected = { recipe ->
                        // Navigate to recipe detail screen
                        navController.currentBackStackEntry?.savedStateHandle?.set("recipe_id", recipe.id)
                        navController.navigate("recipe_detail")
                    }
                )
            }
        }

        composable("recipe_detail") { backStackEntry ->
            val userId = getAuthenticatedUserId()
            val recipeId = backStackEntry.savedStateHandle.get<String>("recipe_id")
                ?: navController.previousBackStackEntry?.savedStateHandle?.get<String>("recipe_id")
                ?: ""
            
            if (userId != null && recipeId.isNotBlank()) {
                com.coachie.app.ui.screen.RecipeDetailScreen(
                    recipeId = recipeId,
                    userId = userId,
                    onNavigateBack = { navController.popBackStack() }
                )
            } else {
                // Show error state
                MealDetailErrorScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

    // Floating microphone button - only appears on the dashboard (home screen)
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val shouldShowMicButton = currentRoute == "home"
    
    if (shouldShowMicButton) {
        FloatingMicButton(
            onClick = {
                DebugLogger.logDebug("Navigation", "Floating mic button clicked")
                navController.navigate("voice_logging")
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealDetailErrorScreen(
    onBack: () -> Unit
) {
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = gradientBackground)
    ) {
        Scaffold(
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
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                "Back",
                                tint = Primary40
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Meal Details",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Primary40,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            containerColor = Color.Transparent
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CoachieCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CoachieCardDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Filled.Restaurant,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Meal data not found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Unable to load meal details. Please try again.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
