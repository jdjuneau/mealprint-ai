package com.mealprint.ai

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.work.WorkManager
import com.mealprint.ai.worker.DailyMindfulSessionWorker
import com.mealprint.ai.worker.DailyHealthSyncWorker
import com.mealprint.ai.service.HealthSyncService
import com.google.android.gms.common.api.ApiException
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.mealprint.ai.data.AuthManager
import com.mealprint.ai.data.AuthResult
import com.mealprint.ai.viewmodel.AuthState
import com.mealprint.ai.viewmodel.AuthViewModel
import com.mealprint.ai.data.FcmService
import com.mealprint.ai.data.Secrets
import com.mealprint.ai.data.local.PreferencesManager
import com.mealprint.ai.data.PermissionsManager
import com.mealprint.ai.navigation.MainNavHost
import com.mealprint.ai.service.AnxietyDetectionService
import com.mealprint.ai.service.ProactiveHealthService
import com.mealprint.ai.service.ShakeDetectionService
import com.mealprint.ai.service.EmergencyCalmService
import com.mealprint.ai.ui.theme.CoachieTheme
import com.google.firebase.Firebase
import com.google.firebase.initialize
import com.google.firebase.ktx.Firebase as FirebaseKtx
import com.google.firebase.firestore.ktx.firestore
import kotlinx.coroutines.tasks.await
import androidx.work.*
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import com.mealprint.ai.receiver.LocalNudgeReceiver
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var authManager: AuthManager
    private lateinit var preferencesManager: PreferencesManager

    // Track navigation from notifications
    // CRITICAL: Use remember in composable to make these observable
    // These will be passed to MainNavHost which uses LaunchedEffect to observe changes
    private var initialNavigationTarget: String? = null
    private var showConfetti: Boolean = false
    private var notificationDetailTitle: String? = null
    private var notificationDetailMessage: String? = null
    private var notificationDeepLinkTarget: String? = null // Deep link target for actionable items
    // Use a trigger to force recomposition when notification intent is received
    private var notificationTrigger by mutableStateOf(0)
    private val activityRecognitionPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }


    companion object {
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // DEMAND: Show exactly how left-to-right reading works
        demonstrateLeftToRightReading()

        // Initialize dependencies
        Firebase.initialize(this)
        Secrets.initialize(this)

        // Initialize managers
        preferencesManager = PreferencesManager(this)
        authManager = AuthManager(preferencesManager)

        // CRITICAL: Sign out any anonymous users immediately on app start
        lifecycleScope.launch {
            try {
                val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                if (currentUser != null && (currentUser.isAnonymous || currentUser.email.isNullOrBlank())) {
                    android.util.Log.w("MainActivity", "⚠️⚠️⚠️ CRITICAL: Found anonymous/invalid user on app start - signing out IMMEDIATELY ⚠️⚠️⚠️")
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                    preferencesManager.clearUserData()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error checking for anonymous users", e)
            }

            // Initialize authentication (this will also check for anonymous users)
            try {
                val authResult = authManager.initializeAuth()
                when (authResult) {
                    is AuthResult.Success -> {
                        android.util.Log.d("MainActivity", "Authentication initialized successfully: ${authResult.user.uid}")
                        // CRITICAL: Clear the explicit sign-out flag if user is authenticated
                        // This ensures the app works properly even if the flag was set from a previous session
                        if (preferencesManager.userExplicitlySignedOut) {
                            android.util.Log.i("MainActivity", "✅ User authenticated on app start - clearing userExplicitlySignedOut flag")
                            preferencesManager.userExplicitlySignedOut = false
                        }
                        preferencesManager.userId = authResult.user.uid
                    }
                    is AuthResult.Error -> {
                        android.util.Log.d("MainActivity", "No authenticated user - user should sign in")
                        // This is expected if no user is logged in - continue to login screen
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error during auth initialization", e)
            }

            // Continue with UI setup after auth initialization attempt
            setupAppAfterAuth()
        }
    }

    private fun setupAppAfterAuth() {
        // Setup FCM token management
        setupFcmTokenManagement()

        // Handle notification tap navigation and confetti
        handleNotificationIntent(intent)

        // FORCE STREAK VALIDATION on app start
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val userId = authManager.getCurrentUser()?.uid
                if (userId != null) {
                    android.util.Log.i("MainActivity", "=========================================")
                    android.util.Log.i("MainActivity", "FORCING STREAK VALIDATION ON APP START")
                    android.util.Log.i("MainActivity", "=========================================")
                    val streakResult = com.coachie.app.data.FirebaseRepository.getInstance().getUserStreak(userId)
                    streakResult.getOrNull()?.let { streak ->
                        android.util.Log.i("MainActivity", "Streak after validation: ${streak.currentStreak} days")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error validating streak on app start", e)
            }
        }
        

        // Start anxiety detection service
        startAnxietyDetectionService()

        // Schedule daily mindful session generation
        scheduleDailyMindfulSessions()

        // Schedule evening journal notifications
        scheduleEveningJournal()

        // Schedule daily automatic health sync (runs at 6 AM daily)
        scheduleDailyHealthSync()

        // Start shake detection service
        startShakeDetection()

        // Auto-sync Google Fit if connected (immediate sync on app start)
        triggerHealthSync()

        setContent {
            // Use AuthViewModel for reactive authentication state
            val viewModel: AuthViewModel = viewModel()
            val authState by viewModel.authState.collectAsState()
            val currentUser = (authState as? AuthState.Authenticated)?.user
            
            // CRITICAL: Load gender IMMEDIATELY when user logs in - BLOCK UI until loaded
            // Reset state when user changes
            var userGender by remember(currentUser?.uid) { mutableStateOf<String>("") }
            var isGenderLoading by remember(currentUser?.uid) { mutableStateOf(true) }
            
            // CRITICAL: Load gender IMMEDIATELY when user logs in - BEFORE UI renders
            // Use currentUser?.uid as key to trigger reload when user changes
            LaunchedEffect(currentUser?.uid) {
                if (currentUser != null) {
                    val userId = currentUser.uid
                    android.util.Log.d("MainActivity", "🔄🔄🔄 USER LOGGED IN - LOADING GENDER IMMEDIATELY 🔄🔄🔄")
                    android.util.Log.d("MainActivity", "User ID: $userId")
                    
                    // CRITICAL: Reset and set loading BEFORE loading
                    userGender = ""
                    isGenderLoading = true
                    
                    // CRITICAL: Load gender FIRST using getUserProfile (most reliable method)
                    val repository = com.coachie.app.data.FirebaseRepository.getInstance()
                    try {
                        val profileResult = repository.getUserProfile(userId)
                        val profile = profileResult.getOrNull()
                        var loadedGender = profile?.gender ?: ""
                        
                        // CRITICAL: Trim and normalize gender string
                        loadedGender = loadedGender.trim().lowercase()
                        
                        android.util.Log.d("MainActivity", "=========================================")
                        android.util.Log.d("MainActivity", "✅✅✅ GENDER LOADED FROM PROFILE: '$loadedGender' ✅✅✅")
                        android.util.Log.d("MainActivity", "Profile gender field: '${profile?.gender}'")
                        android.util.Log.d("MainActivity", "Normalized gender: '$loadedGender'")
                        android.util.Log.d("MainActivity", "Is Male: ${loadedGender == "male" || loadedGender == "m"}")
                        android.util.Log.d("MainActivity", "Is Female: ${loadedGender == "female" || loadedGender == "f"}")
                        android.util.Log.d("MainActivity", "=========================================")
                        
                        // Set gender immediately (store as lowercase for consistency)
                        userGender = loadedGender
                        isGenderLoading = false
                        
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Failed to load user profile/gender", e)
                        e.printStackTrace()
                        // Default to female if loading fails
                        userGender = ""
                        isGenderLoading = false
                    }
                } else {
                    // User logged out - clear immediately
                    userGender = ""
                    isGenderLoading = false
                    android.util.Log.d("MainActivity", "User logged out - gender cleared")
                    
                }
            }
            
            // Don't block UI during gender loading - let navigation render first
            // Gender will load in the background and update the theme when ready
            // This prevents the black loading screen flash before splash screen
            
            // If gender is empty after loading completes, use a default (female/neutral theme)
            // This prevents infinite loading if user doesn't have gender set
            // CRITICAL: Only default to female if loading is complete AND gender is still empty
            val effectiveGender = remember(userGender, isGenderLoading) {
                val result = if (currentUser != null && userGender.isEmpty() && !isGenderLoading) {
                    android.util.Log.d("MainActivity", "⚠️ Gender is empty after loading - defaulting to 'female'")
                    "female" // Default to female/neutral theme if no gender set
                } else {
                    android.util.Log.d("MainActivity", "✅ Using loaded gender: '$userGender' (loading: $isGenderLoading)")
                    userGender
                }
                android.util.Log.d("MainActivity", "🎨 Effective gender: '$result' (raw: '$userGender', loading: $isGenderLoading)")
                result
            }
            
            // CRITICAL: Also reload gender when activity resumes OR when profile is updated
            DisposableEffect(currentUser?.uid) {
                val observer = object : androidx.lifecycle.LifecycleEventObserver {
                    override fun onStateChanged(source: androidx.lifecycle.LifecycleOwner, event: androidx.lifecycle.Lifecycle.Event) {
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && currentUser != null) {
                            // Reload gender when activity resumes
                            lifecycleScope.launch {
                                val userId = currentUser.uid
                                android.util.Log.d("MainActivity", "🔄 ON_RESUME: Reloading gender for user: $userId")
                                try {
                                    val repository = com.coachie.app.data.FirebaseRepository.getInstance()
                                    val profileResult = repository.getUserProfile(userId)
                                    val profile = profileResult.getOrNull()
                                    val loadedGender = (profile?.gender ?: "").trim().lowercase()
                                    // CRITICAL: Only update if we got a valid gender value, otherwise preserve current gender
                                    // This prevents resetting to female when reload fails or returns empty
                                    if (loadedGender.isNotEmpty()) {
                                        val oldGender = userGender
                                        userGender = loadedGender
                                        android.util.Log.d("MainActivity", "✅ Gender refreshed on resume: '$loadedGender' (was '$oldGender')")
                                    } else {
                                        android.util.Log.d("MainActivity", "⚠️ Gender reload returned empty - preserving current gender: '$userGender'")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("MainActivity", "Failed to reload gender on resume - preserving current gender: '$userGender'", e)
                                    // Don't reset gender on error - preserve current value
                                }
                            }
                        }
                    }
                }
                lifecycle.addObserver(observer)
                
                // Also check for gender reload trigger from SetGoalsScreen
                val checkGenderJob = lifecycleScope.launch {
                    while (true) {
                        delay(500) // Check every 500ms
                        if (currentUser != null) {
                            try {
                                val prefs = getSharedPreferences("coachie_prefs", android.content.Context.MODE_PRIVATE)
                                val trigger = prefs.getString("gender_reload_trigger", null)
                                if (trigger != null) {
                                    android.util.Log.d("MainActivity", "🔄 Gender reload trigger detected - reloading immediately")
                                    prefs.edit().remove("gender_reload_trigger").apply()
                                    
                                    val userId = currentUser.uid
                                    val repository = com.coachie.app.data.FirebaseRepository.getInstance()
                                    val profileResult = repository.getUserProfile(userId)
                                    val profile = profileResult.getOrNull()
                                    val loadedGender = (profile?.gender ?: "").trim().lowercase()
                                    val oldGender = userGender
                                    userGender = loadedGender
                                    android.util.Log.d("MainActivity", "✅✅✅ Gender reloaded after profile save: '$loadedGender' (was '$oldGender') ✅✅✅")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Error checking gender reload trigger", e)
                            }
                        }
                    }
                }
                
                onDispose {
                    lifecycle.removeObserver(observer)
                    checkGenderJob.cancel()
                }
            }
            
            
            // Key the theme by both user ID and gender to force recomposition when either changes
            // CRITICAL: Use effectiveGender (which includes default fallback) for theme
            val themeKey = remember(currentUser?.uid, effectiveGender) {
                "${currentUser?.uid ?: "no_user"}_${effectiveGender}"
            }
            
            // CRITICAL: Use LaunchedEffect to force recomposition when gender changes
            LaunchedEffect(effectiveGender) {
                android.util.Log.d("MainActivity", "🔄 Gender state changed to: '$effectiveGender' (raw: '$userGender') - forcing theme update")
            }
            
            // CRITICAL: Determine if user is male or female FIRST, before rendering theme
            // This ensures the theme is always correct based on gender (using effectiveGender with fallback)
            val isMale = remember(effectiveGender) {
                val gender = effectiveGender.lowercase()
                val male = gender == "male" || gender == "m"
                android.util.Log.d("MainActivity", "🎨🎨🎨 THEME GENDER CHECK: effectiveGender='$effectiveGender', isMale=$male 🎨🎨🎨")
                male
            }
            
            key(themeKey) {
                android.util.Log.d("MainActivity", "🎨🎨🎨 THEME RECOMPOSITION - Key: $themeKey, Gender: '$effectiveGender' (raw: '$userGender'), isMale: $isMale 🎨🎨🎨")
                // CRITICAL: Use effectiveGender (with default fallback) for theme
                // CRITICAL: Force recomposition by using key() with gender to ensure all children recompose
                key(effectiveGender) {
                    CoachieTheme(gender = effectiveGender) {
                        val navController = rememberNavController()
                        // Use notificationTrigger as key to force recomposition when notification is clicked
                        key(notificationTrigger) {
                            MainNavHost(
                                navController = navController,
                                initialNavigationTarget = initialNavigationTarget,
                                notificationTitle = notificationDetailTitle,
                                notificationMessage = notificationDetailMessage,
                                notificationDeepLinkTarget = notificationDeepLinkTarget
                            )
                        }
                    }
                }
            }
        }
    }

    private fun scheduleDailyMindfulSessions() {
        try {
            // Only schedule if user is authenticated
            if (authManager.isAuthenticated()) {
                DailyMindfulSessionWorker.scheduleDailyWork(this)
                android.util.Log.i("MainActivity", "Scheduled daily mindful session generation")
            } else {
                android.util.Log.i("MainActivity", "Skipping mindful session scheduling - user not authenticated")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to schedule daily mindful sessions", e)
        }
    }

    private fun scheduleEveningJournal() {
        try {
            // Only schedule if user is authenticated
            if (authManager.isAuthenticated()) {
                val proactiveHealthService = ProactiveHealthService(this)
                proactiveHealthService.scheduleEveningJournal()
                android.util.Log.i("MainActivity", "Scheduled evening journal notifications")
            } else {
                android.util.Log.i("MainActivity", "Skipping evening journal scheduling - user not authenticated")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to schedule evening journal", e)
        }
    }

    /**
     * Schedule daily automatic health sync at 6 AM
     * This ensures previous day's data is synced every morning even if app isn't opened
     * 
     * CRITICAL: This is called on app start and verifies the worker is scheduled
     */
    private fun scheduleDailyHealthSync() {
        try {
            // Only schedule if user is authenticated
            if (authManager.isAuthenticated()) {
                android.util.Log.i("MainActivity", "=========================================")
                android.util.Log.i("MainActivity", "SCHEDULING MULTIPLE DAILY HEALTH SYNC WORKERS")
                android.util.Log.i("MainActivity", "  Midnight (12 AM), 9 AM, 3 PM")
                android.util.Log.i("MainActivity", "=========================================")
                
                DailyHealthSyncWorker.scheduleDailyWork(this)
                
                // CRITICAL: Verify the work is actually scheduled
                verifyHealthSyncWorkerScheduled()
                
                android.util.Log.i("MainActivity", "✅✅✅ Multiple daily health sync workers scheduled and verified ✅✅✅")
            } else {
                android.util.Log.i("MainActivity", "Skipping health sync scheduling - user not authenticated")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌❌❌ CRITICAL: Failed to schedule multiple daily health syncs ❌❌❌", e)
            e.printStackTrace()
            // Try to reschedule after a delay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    if (authManager.isAuthenticated()) {
                        android.util.Log.i("MainActivity", "Retrying multiple health sync scheduling...")
                        DailyHealthSyncWorker.scheduleDailyWork(this)
                        verifyHealthSyncWorkerScheduled()
                    }
                } catch (retryException: Exception) {
                    android.util.Log.e("MainActivity", "Failed to reschedule after error", retryException)
                }
            }, 5000)
        }
    }
    
    /**
     * Verify that all daily health sync workers are actually scheduled in WorkManager
     */
    private fun verifyHealthSyncWorkerScheduled() {
        try {
            val workManager = androidx.work.WorkManager.getInstance(this)
            val workNames = listOf("daily_health_sync", "daily_health_sync_morning", "daily_health_sync_afternoon")

            android.util.Log.d("MainActivity", "=== HEALTH SYNC WORKERS VERIFICATION ===")

            workNames.forEach { workName ->
                val workInfos = workManager.getWorkInfosForUniqueWork(workName).get()

                android.util.Log.d("MainActivity", "--- Checking $workName ---")
                if (workInfos.isEmpty()) {
                    android.util.Log.w("MainActivity", "⚠️ No work found for $workName - may not be scheduled")
                } else {
                    workInfos.forEach { workInfo ->
                        android.util.Log.d("MainActivity", "  Work ID: ${workInfo.id}")
                        android.util.Log.d("MainActivity", "  State: ${workInfo.state}")
                        android.util.Log.d("MainActivity", "  Tags: ${workInfo.tags}")

                        when (workInfo.state) {
                            androidx.work.WorkInfo.State.ENQUEUED -> {
                                android.util.Log.i("MainActivity", "✅ $workName worker is ENQUEUED and will run at scheduled time")
                            }
                            androidx.work.WorkInfo.State.RUNNING -> {
                                android.util.Log.i("MainActivity", "✅ $workName worker is currently RUNNING")
                            }
                            androidx.work.WorkInfo.State.SUCCEEDED -> {
                                android.util.Log.i("MainActivity", "✅ $workName worker last run SUCCEEDED")
                            }
                            androidx.work.WorkInfo.State.FAILED -> {
                                android.util.Log.e("MainActivity", "❌ $workName worker last run FAILED - may need rescheduling")
                            }
                            androidx.work.WorkInfo.State.BLOCKED -> {
                                android.util.Log.w("MainActivity", "⚠️ $workName worker is BLOCKED (waiting for constraints)")
                            }
                            androidx.work.WorkInfo.State.CANCELLED -> {
                                android.util.Log.w("MainActivity", "⚠️ $workName worker was CANCELLED - rescheduling...")
                                // Reschedule if cancelled
                                if (authManager.isAuthenticated()) {
                                    DailyHealthSyncWorker.scheduleDailyWork(this)
                                }
                            }
                            else -> {
                                android.util.Log.d("MainActivity", "  $workName worker state: ${workInfo.state}")
                            }
                        }
                    }
                }
            }
            android.util.Log.d("MainActivity", "=========================================")
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Could not verify worker status", e)
        }
    }

    private fun startShakeDetection() {
        try {
            ShakeDetectionService.startService(this)
            android.util.Log.i("MainActivity", "Started shake detection service")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to start shake detection", e)
        }
    }

    private fun demonstrateLeftToRightReading() {
        // YOUR EXACT OCR OUTPUT - READ LEFT TO RIGHT, LINE BY LINE
        val ocrLines = listOf(
            "Supplement Fact",
            "Serving Size: 2 Capsules",
            "Servings Per Container: 60",
            "Amount Per Serving",
            "Vitamin A (as retinyl acetate)",
            "Vitamin C (as ascorbic acid)",
            "Vitamin D (as cholecalciferol)",
            "Vitamin E (as d-alpha tocophery succinate)",
            "Vitamin K (as phytonadione)",
            "Thiamin (as thiamin HCI)",
            "Riboflavin (as riboflavin-5-sodiun phosphate)",
            "Niacin (as niacinamide)",
            "Vitamin B6 (as pyridoxa-5-phosphate)",
            "Folate (as 5-methytetrahydrofolate)",
            "Vitamin B12 (as methylcobalamin)",
            "Biotin",
            "lOdine (as potassium iodide)",
            "Magnesium (as magnesium bisglycinate)",
            "Zinc (as zinc citrate)",
            "1,800mcg 2ES",
            "250mg",
            "Selenium (as selenomethionine)",
            "Copper (as Copper carbonate, Copper citrate)",
            "Man nganese (as manganese sulfate)",
            "Chromium (as chromium picolinate)",
            "Molybdenum (as sodium molybdate)",
            "40mg 1",
            "100mcg",
            "76mg 628N",
            "23mg",
            "15mg",
            "2,000mcg DFE",
            "Pantothenic Acid (as calcium pantothenate)",
            "Iron (as Ferrochel® ferrous bisglycinate chelate)",
            "Calcium (as calcium carbonate, calcium. aspartate) 50mg",
            "760mcg 31a8S",
            "1,600mcg S015",
            "38mg",
            "10mg",
            "113mcg",
            "15ng",
            "11ng",
            "140cg",
            "45m9"
        )

        android.util.Log.d("LEFT_TO_RIGHT_DEMO", "=== READING YOUR SUPPLEMENT LABEL LEFT-TO-RIGHT ===")

        var pendingNutrient: String? = null
        val pairs = mutableListOf<Pair<String, String>>()

        ocrLines.forEachIndexed { index, line ->
            val cleanLine = line.trim()
            android.util.Log.d("LEFT_TO_RIGHT_DEMO", "LINE ${index+1}: \"$cleanLine\"")

            if (cleanLine.isEmpty() || isHeaderLine(cleanLine)) {
                android.util.Log.d("LEFT_TO_RIGHT_DEMO", "  → SKIP (header/empty)")
                return@forEachIndexed
            }

            // Check for amounts in this line
            val amountRegex = Regex("""([\d.,]+)\s*(mcg|µg|ug|mg|g|iu)""", RegexOption.IGNORE_CASE)
            val amountMatches = amountRegex.findAll(cleanLine)
            val amountsInLine = amountMatches.map { it.value }.toList()

            if (amountsInLine.isNotEmpty() && amountsInLine.size == 1) {
                // This line has an amount
                val amountMatch = amountRegex.find(cleanLine)
                val amountStartIndex = amountMatch?.range?.first ?: 0
                val nutrientPart = cleanLine.substring(0, amountStartIndex).trim()

                if (nutrientPart.isNotBlank() && isLikelyNutrientName(nutrientPart)) {
                    // Nutrient and amount on same line
                    pairs.add(nutrientPart to amountsInLine[0])
                    android.util.Log.d("LEFT_TO_RIGHT_DEMO", "  → PAIRED (same line): $nutrientPart → ${amountsInLine[0]}")
                    pendingNutrient = null
                } else if (pendingNutrient != null) {
                    // Pair with pending nutrient
                    pairs.add(pendingNutrient!! to amountsInLine[0])
                    android.util.Log.d("LEFT_TO_RIGHT_DEMO", "  → PAIRED (with pending): $pendingNutrient → ${amountsInLine[0]}")
                    pendingNutrient = null
                } else {
                    android.util.Log.d("LEFT_TO_RIGHT_DEMO", "  → AMOUNT FOUND but no nutrient context: ${amountsInLine[0]}")
                }
            } else if (amountsInLine.isEmpty()) {
                // No amounts - check if nutrient name
                val cleanText = cleanLine.replace(Regex("""[^\w\s()]"""), "").trim()
                if (isLikelyNutrientName(cleanText)) {
                    pendingNutrient = cleanText
                    android.util.Log.d("LEFT_TO_RIGHT_DEMO", "  → NUTRIENT FOUND (pending): $cleanText")
                } else {
                    android.util.Log.d("LEFT_TO_RIGHT_DEMO", "  → NEITHER (clearing pending)")
                    pendingNutrient = null
                }
            } else {
                android.util.Log.d("LEFT_TO_RIGHT_DEMO", "  → MULTIPLE AMOUNTS (skipping): $amountsInLine")
            }
        }

        android.util.Log.d("LEFT_TO_RIGHT_DEMO", "\n=== FINAL RESULTS ===")
        android.util.Log.d("LEFT_TO_RIGHT_DEMO", "TOTAL PAIRS FOUND: ${pairs.size}")
        pairs.forEachIndexed { index, pair ->
            android.util.Log.d("LEFT_TO_RIGHT_DEMO", "${index+1}. ${pair.first} → ${pair.second}")
        }
    }

    private fun isHeaderLine(text: String): Boolean {
        val normalized = text.lowercase()
        return normalized.contains("supplement")
                || normalized.contains("serving")
                || normalized.contains("amount per")
                || normalized.contains("% dv")
                || normalized.contains("daily value")
                || normalized.contains("other ingredients")
    }

    private fun isLikelyNutrientName(text: String): Boolean {
        if (text.isBlank() || text.length < 3) return false
        val lowerText = text.lowercase()
        if (lowerText.contains("%") || lowerText.contains("daily") ||
            lowerText.contains("value") || lowerText.matches(Regex("\\d+.*"))) {
            return false
        }
        val nutrientKeywords = listOf(
            "vitamin", "calcium", "iron", "magnesium", "zinc", "copper",
            "manganese", "chromium", "molybdenum", "iodine", "selenium",
            "potassium", "sodium", "chloride", "phosphorus", "sulfur",
            "thiamin", "riboflavin", "niacin", "biotin", "pantothenic",
            "folate", "cobalamin", "pyridoxal", "methyl", "retinyl",
            "cholecalciferol", "tocopheryl", "phytonadione"
        )
        return nutrientKeywords.any { lowerText.contains(it) } ||
               lowerText.contains("(") && lowerText.contains(")") ||
               (text.length in 4..50 && text.any { it.isLetter() })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle notification tap when app is already running
        handleNotificationIntent(intent)
    }




    private fun getIntentFlagsText(flags: Int): String {
        val flagTexts = mutableListOf<String>()
        if (flags and android.content.Intent.FLAG_ACTIVITY_NEW_TASK != 0) flagTexts.add("NEW_TASK")
        if (flags and android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK != 0) flagTexts.add("CLEAR_TASK")
        if (flags and android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP != 0) flagTexts.add("SINGLE_TOP")
        if (flags and android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP != 0) flagTexts.add("CLEAR_TOP")
        if (flags and android.content.Intent.FLAG_ACTIVITY_NO_HISTORY != 0) flagTexts.add("NO_HISTORY")
        if (flags and android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS != 0) flagTexts.add("EXCLUDE_FROM_RECENTS")
        return if (flagTexts.isEmpty()) "NONE" else flagTexts.joinToString("|")
    }

    private fun getGooglePlayServicesStatusText(status: Int): String {
        return when (status) {
            com.google.android.gms.common.ConnectionResult.SUCCESS -> "SUCCESS"
            com.google.android.gms.common.ConnectionResult.SERVICE_MISSING -> "SERVICE_MISSING"
            com.google.android.gms.common.ConnectionResult.SERVICE_UPDATING -> "SERVICE_UPDATING"
            com.google.android.gms.common.ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> "SERVICE_VERSION_UPDATE_REQUIRED"
            com.google.android.gms.common.ConnectionResult.SERVICE_DISABLED -> "SERVICE_DISABLED"
            com.google.android.gms.common.ConnectionResult.SIGN_IN_REQUIRED -> "SIGN_IN_REQUIRED"
            com.google.android.gms.common.ConnectionResult.INVALID_ACCOUNT -> "INVALID_ACCOUNT"
            com.google.android.gms.common.ConnectionResult.RESOLUTION_REQUIRED -> "RESOLUTION_REQUIRED"
            com.google.android.gms.common.ConnectionResult.NETWORK_ERROR -> "NETWORK_ERROR"
            com.google.android.gms.common.ConnectionResult.INTERNAL_ERROR -> "INTERNAL_ERROR"
            com.google.android.gms.common.ConnectionResult.SERVICE_INVALID -> "SERVICE_INVALID"
            com.google.android.gms.common.ConnectionResult.DEVELOPER_ERROR -> "DEVELOPER_ERROR"
            com.google.android.gms.common.ConnectionResult.LICENSE_CHECK_FAILED -> "LICENSE_CHECK_FAILED"
            else -> "UNKNOWN_$status"
        }
    }

    @Deprecated("Deprecated in Java", ReplaceWith("Use Activity Result API"))
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }

    /**
     * Handle navigation intents from notifications
     */
    private fun handleNotificationIntent(intent: Intent) {
        // Handle widget quick log action
        if (intent.action == "com.coachie.app.QUICK_LOG" || intent.data?.scheme == "coachie" && intent.data?.host == "health_tracking") {
            initialNavigationTarget = "health_tracking"
            notificationTrigger++
            return
        }
        
        // Handle both FCM and local nudge notifications
        val navigateTo = intent.getStringExtra(FcmService.EXTRA_NAVIGATE_TO) 
            ?: intent.getStringExtra("navigate_to")
        val shouldShowConfetti = intent.getBooleanExtra(FcmService.EXTRA_SHOW_CONFETTI, false)
        val showEveningJournal = intent.getBooleanExtra("show_evening_journal", false)
        val showEmergencyCalm = intent.getBooleanExtra("emergency_calm", false)
        val notificationTitle = intent.getStringExtra(FcmService.EXTRA_NOTIFICATION_TITLE)
            ?: intent.getStringExtra("notification_title")
        val notificationMessage = intent.getStringExtra(FcmService.EXTRA_NOTIFICATION_MESSAGE)
            ?: intent.getStringExtra("notification_message")
        val deepLinkTarget = intent.getStringExtra("deep_link_target") // Deep link for actionable items
        val conversationId = intent.getStringExtra("conversationId")
        val threadId = intent.getStringExtra("threadId")
        val requestId = intent.getStringExtra("requestId")
        val circleId = intent.getStringExtra("circleId")
        val postId = intent.getStringExtra("postId")

        if (navigateTo == "messaging" && (conversationId != null || intent.getStringExtra("userId") != null)) {
            // Navigate to messaging screen with conversation
            initialNavigationTarget = "messaging"
            // Store conversation ID for navigation
            intent.putExtra("conversationId", conversationId ?: intent.getStringExtra("userId"))
            showConfetti = shouldShowConfetti
            notificationTrigger++
        } else if (navigateTo == "forum_thread" && threadId != null) {
            // Navigate to forum thread
            initialNavigationTarget = "forum_thread"
            intent.putExtra("threadId", threadId)
            showConfetti = shouldShowConfetti
            notificationTrigger++
        } else if (navigateTo == "notification_detail" && notificationTitle != null && notificationMessage != null) {
            // Store notification data for the detail screen
            notificationDetailTitle = notificationTitle
            notificationDetailMessage = notificationMessage
            // Store deep link target if available for actionable links
            notificationDeepLinkTarget = deepLinkTarget
            initialNavigationTarget = "notification_detail"
            showConfetti = shouldShowConfetti
            // CRITICAL: Increment trigger to force recomposition and navigation
            notificationTrigger++
        } else if (notificationTitle != null && notificationMessage != null) {
            // CRITICAL: If we have notification data but navigateTo is something else (like "habits"),
            // ALWAYS show notification_detail first with the message, then allow navigation to deep link
            notificationDetailTitle = notificationTitle
            notificationDetailMessage = notificationMessage
            notificationDeepLinkTarget = deepLinkTarget ?: (if (navigateTo != null && navigateTo != "notification_detail") navigateTo else null)
            initialNavigationTarget = "notification_detail"
            showConfetti = shouldShowConfetti
            notificationTrigger++
        } else if (navigateTo == FcmService.NAVIGATE_COACH_CHAT) {
            initialNavigationTarget = navigateTo
            showConfetti = shouldShowConfetti
            notificationTrigger++
        } else if (navigateTo == "weekly_blueprint" || navigateTo == "weeklyBlueprint") {
            initialNavigationTarget = "weekly_blueprint"
            showConfetti = shouldShowConfetti
            notificationTrigger++
        } else if (navigateTo == "journal_flow") {
            // Journal/reflection deep link
            initialNavigationTarget = "journal_flow"
            showConfetti = shouldShowConfetti
            notificationTrigger++
        } else if (navigateTo == "habits") {
            // Habits deep link - check if we have a specific habitId
            val habitId = intent.getStringExtra("habitId")
            if (habitId != null) {
                // Store habitId for navigation to specific habit
                intent.putExtra("habitId", habitId)
            }
            initialNavigationTarget = "habits"
            showConfetti = shouldShowConfetti
            notificationTrigger++
        } else if (navigateTo == "meal_log" || navigateTo == "meal_capture") {
            // Meal logging deep link
            initialNavigationTarget = "meal_log"
            showConfetti = shouldShowConfetti
            notificationTrigger++
        } else if (navigateTo == "water_log") {
            // Water logging deep link
            initialNavigationTarget = "water_log"
            showConfetti = shouldShowConfetti
            notificationTrigger++
        } else if (navigateTo == "goals" || navigateTo == "set_goals") {
            // Goals deep link
            initialNavigationTarget = "set_goals"
            showConfetti = shouldShowConfetti
            notificationTrigger++
        } else if (navigateTo == "meal_capture" || navigateTo == "meal_log") {
            // Meal logging deep link
            initialNavigationTarget = "meal_log"
            showConfetti = shouldShowConfetti
            notificationTrigger++
        } else if (navigateTo == "health_tracking") {
            // Health tracking deep link
            initialNavigationTarget = "health_tracking"
            showConfetti = shouldShowConfetti
            notificationTrigger++
        } else if (navigateTo == "sleep_log") {
            // Sleep logging deep link
            initialNavigationTarget = "sleep_log"
            showConfetti = shouldShowConfetti
            notificationTrigger++
        } else if (navigateTo == "workout_log") {
            // Workout logging deep link
            initialNavigationTarget = "workout_log"
            showConfetti = shouldShowConfetti
            notificationTrigger++
        } else if (navigateTo == "friends" && requestId != null) {
            // Friend request notification - navigate to friends list
            initialNavigationTarget = "friends_list"
            intent.putExtra("requestId", requestId)
            showConfetti = shouldShowConfetti
            notificationTrigger++
        } else if (navigateTo == "circle_detail" && circleId != null) {
            // Circle post/comment notification - navigate to circle detail
            initialNavigationTarget = "circle_detail/$circleId"
            if (postId != null) {
                intent.putExtra("postId", postId)
            }
            showConfetti = shouldShowConfetti
            notificationTrigger++
        }

        if (showEveningJournal) {
            initialNavigationTarget = "journal_flow"
            notificationTrigger++
        }

        if (showEmergencyCalm) {
            // Show emergency calm overlay
            EmergencyCalmService.startService(this)
        }
    }

    /**
     * Setup FCM token management for push notifications
     */
    private fun setupFcmTokenManagement() {
        lifecycleScope.launch {
            try {
                // Get FCM token
                val fcmToken = FcmService.getFcmToken()
                if (fcmToken != null) {
                    // Update token in user profile if user is authenticated
                    authManager.getCurrentUser()?.let { user ->
                        FcmService.updateUserFcmToken(
                            this@MainActivity,
                            user.uid,
                            fcmToken
                        )
                    }
                }
            } catch (e: Exception) {
                // Log error but don't crash the app
                android.util.Log.e("MainActivity", "Failed to setup FCM token", e)
            }
        }
    }

    private fun showErrorDialog(title: String, message: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showSuccessDialog(title: String, message: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }


    /**
     * Schedule or cancel multiple daily local nudge notifications based on user preference
     */
    private fun scheduleLocalNudges() {
        try {
            val preferencesManager = PreferencesManager(this)
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

            if (!preferencesManager.nudgesEnabled) {
                // Cancel all existing alarms (morning, midday, evening)
                cancelAllNudgeAlarms(alarmManager)
                android.util.Log.d("MainActivity", "All local nudge notifications cancelled - nudges disabled")
                return
            }

            // Schedule meal-time nudges: breakfast, lunch, and dinner
            scheduleNudgeAlarm(alarmManager, 8, 0, 1001)  // 8:00 AM - Breakfast
            scheduleNudgeAlarm(alarmManager, 12, 30, 1002) // 12:30 PM - Lunch
            scheduleNudgeAlarm(alarmManager, 18, 0, 1003) // 6:00 PM - Dinner

            android.util.Log.i("MainActivity", "Local nudge notifications scheduled: 8 AM and 8 PM daily")

        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to schedule local nudges", e)
        }
    }

    private fun startAnxietyDetectionService() {
        try {
            if (authManager.isAuthenticated()) {
                val intent = Intent(this, AnxietyDetectionService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                android.util.Log.i("MainActivity", "Anxiety detection service started")
            } else {
                android.util.Log.i("MainActivity", "Skipping anxiety detection service - user not authenticated")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to start anxiety detection service", e)
        }
    }

    

    /**
     * Schedule a single nudge alarm
     */
    private fun scheduleNudgeAlarm(alarmManager: AlarmManager, hour: Int, minute: Int, requestCode: Int) {
        val intent = Intent(this, LocalNudgeReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)

            // If it's already past the scheduled time today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    /**
     * Cancel all nudge alarms
     */
    private fun cancelAllNudgeAlarms(alarmManager: AlarmManager) {
        val requestCodes = arrayOf(1001, 1003) // Morning, Evening (reduced frequency)

        for (requestCode in requestCodes) {
            val intent = Intent(this, LocalNudgeReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    fun testNudgeSystem() {
        android.util.Log.d("MainActivity", "=== TESTING NUDGE SYSTEM ===")

        // Check if nudges are enabled in preferences
        val preferencesManager = com.coachie.app.data.local.PreferencesManager(this)
        android.util.Log.d("MainActivity", "Nudges enabled in preferences: ${preferencesManager.nudgesEnabled}")

        // Check current time
        val currentTime = java.util.Calendar.getInstance()
        val currentHour = currentTime.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = currentTime.get(java.util.Calendar.MINUTE)
        android.util.Log.d("MainActivity", "Current time: ${currentHour}:${String.format("%02d", currentMinute)}")

        // Determine which time slot we should be in
        val expectedTimeOfDay = when {
            currentHour in 6..11 -> "morning (6-11)"
            currentHour in 12..16 -> "midday (12-16)"
            currentHour in 17..22 -> "evening (17-22)"
            else -> "late night (fallback to evening)"
        }
        android.util.Log.d("MainActivity", "Expected time slot: $expectedTimeOfDay")

        // Check scheduled alarm times (reduced to 2 daily nudges)
        val scheduledTimes = listOf(
            Triple("Morning", 8, 1001),
            Triple("Evening", 20, 1003)
        )

        for ((label, _, requestCode) in scheduledTimes) {
            val intent = android.content.Intent(this, com.coachie.app.receiver.LocalNudgeReceiver::class.java)
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                android.app.PendingIntent.FLAG_NO_CREATE or
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
                    android.app.PendingIntent.FLAG_IMMUTABLE
                else 0
            )

            val isScheduled = pendingIntent != null
            android.util.Log.d("MainActivity", "$label nudge (requestCode $requestCode) scheduled: $isScheduled")
        }

        // Test notification permissions
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()
            android.util.Log.d("MainActivity", "Notification permission granted: $hasPermission")
        }

        android.util.Log.d("MainActivity", "=== NUDGE SYSTEM TEST COMPLETE ===")
    }

    fun logCurrentSHA1() {
        try {
            @Suppress("DEPRECATION")
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            }
            @Suppress("DEPRECATION")
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                packageInfo.signatures
            }

            if (signatures != null && signatures.isNotEmpty()) {
                val signature = signatures[0]
                val md = java.security.MessageDigest.getInstance("SHA-1")
                val signatureBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    (signature as? java.security.cert.Certificate)?.encoded ?: return
                } else {
                    (signature as? android.content.pm.Signature)?.toByteArray() ?: return
                }
                md.update(signatureBytes)
                val sha1Bytes = md.digest()

                // Format as colon-separated hex string
                val sha1Lower = StringBuilder()
                for (i in sha1Bytes.indices) {
                    if (i > 0) sha1Lower.append(":")
                    sha1Lower.append(String.format("%02x", sha1Bytes[i]))
                }

                val sha1String = sha1Lower.toString()

                // Show in a dialog so user can easily copy it
                val dialog = android.app.AlertDialog.Builder(this)
                    .setTitle("Your App's SHA-1 Fingerprint")
                    .setMessage("Copy this SHA-1 and paste it into Google Cloud Console:\n\n$sha1String\n\nLocation: APIs & Services → Credentials → OAuth Client → SHA-1 fingerprints")
                    .setPositiveButton("Copy to Clipboard") { _, _ ->
                        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("SHA-1", sha1String)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(this, "SHA-1 copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    .setNeutralButton("Close") { dialog, _ -> dialog.dismiss() }
                    .setCancelable(false)
                    .create()

                dialog.show()

                // Also log it for debugging
                android.util.Log.e("SHA1_RESULT", "=========================================")
                android.util.Log.e("SHA1_RESULT", "YOUR APP'S SHA-1 FINGERPRINT:")
                android.util.Log.e("SHA1_RESULT", sha1String)
                android.util.Log.e("SHA1_RESULT", "=========================================")
                android.util.Log.e("SHA1_RESULT", "Copy this to Google Cloud Console → OAuth Client → SHA-1 fingerprints")

            } else {
                android.widget.Toast.makeText(this, "No signatures found!", android.widget.Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Error getting SHA-1: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            android.util.Log.e("SHA1_ERROR", "Error getting SHA-1", e)
        }
    }

    override fun onResume() {
        super.onResume()
        // CRITICAL: Reschedule daily health sync worker (Android may cancel it if app was killed)
        scheduleDailyHealthSync()
        // Sync health data when app comes to foreground
        triggerHealthSync()
        // Also schedule an immediate sync test (for debugging)
        triggerImmediateSyncTest()
    }

    /**
     * Test function to trigger an immediate sync for debugging
     */
    private fun triggerImmediateSyncTest() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.i("MAIN_ACTIVITY_DEBUG", "=== TRIGGERING IMMEDIATE SYNC TEST ===")
                val userId = authManager.getCurrentUser()?.uid
                if (userId != null) {
                    val syncResult = HealthSyncService.sync(this@MainActivity)
                    android.util.Log.i("MAIN_ACTIVITY_DEBUG", "Immediate sync result: $syncResult")
                } else {
                    android.util.Log.w("MAIN_ACTIVITY_DEBUG", "No user ID for immediate sync test")
                }
            } catch (e: Exception) {
                android.util.Log.e("MAIN_ACTIVITY_DEBUG", "Error in immediate sync test", e)
            }
        }
    }
    
    /**
     * Bulletproof health sync - called on app start and resume
     * CRITICAL: Also checks if yesterday's data was synced (fallback for missed worker runs)
     */
    private fun triggerHealthSync() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("MAIN_ACTIVITY_DEBUG", "=== HEALTH SYNC TRIGGERED ===")
                val userId = authManager.getCurrentUser()?.uid
                if (userId == null) {
                    android.util.Log.w("MAIN_ACTIVITY_DEBUG", "❌ No user ID - skipping sync")
                    return@launch
                }
                android.util.Log.d("MAIN_ACTIVITY_DEBUG", "✅ User ID: $userId")
                
                // CRITICAL FALLBACK: Check if yesterday's data was synced
                // This catches cases where the worker failed silently
                val yesterday = java.time.LocalDate.now(java.time.ZoneId.systemDefault()).minusDays(1)
                val yesterdayString = yesterday.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                val repository = com.coachie.app.data.FirebaseRepository.getInstance()
                val yesterdayLogResult = repository.getDailyLog(userId, yesterdayString)
                val yesterdayLog = yesterdayLogResult.getOrNull()
                
                val currentHour = java.time.LocalTime.now(java.time.ZoneId.systemDefault()).hour
                val isMorning = currentHour >= 6 && currentHour < 12 // Between 6 AM and 12 PM
                
                if (isMorning && yesterdayLog == null) {
                    android.util.Log.w("MAIN_ACTIVITY_DEBUG", "⚠️⚠️⚠️ YESTERDAY'S DATA NOT FOUND - WORKER MAY HAVE FAILED ⚠️⚠️⚠️")
                    android.util.Log.w("MAIN_ACTIVITY_DEBUG", "  Date: $yesterdayString")
                    android.util.Log.w("MAIN_ACTIVITY_DEBUG", "  Triggering fallback sync for yesterday...")
                } else if (yesterdayLog != null) {
                    android.util.Log.d("MAIN_ACTIVITY_DEBUG", "✅ Yesterday's data exists: $yesterdayString (steps=${yesterdayLog.steps}, calories=${yesterdayLog.caloriesBurned}, water=${yesterdayLog.water})")
                }
                
                // Check permissions for both services
                val googleFitService = com.coachie.app.data.health.GoogleFitService(this@MainActivity)
                val healthConnectService = com.coachie.app.data.health.HealthConnectService(this@MainActivity)
                val hasGoogleFitPerms = googleFitService.hasPermissions()
                val hasHealthConnectPerms = healthConnectService.isAvailable() && healthConnectService.hasPermissions()
                
                android.util.Log.d("MAIN_ACTIVITY_DEBUG", "Google Fit permissions: $hasGoogleFitPerms")
                android.util.Log.d("MAIN_ACTIVITY_DEBUG", "Health Connect permissions: $hasHealthConnectPerms")
                
                if (hasGoogleFitPerms || hasHealthConnectPerms) {
                    android.util.Log.d("MAIN_ACTIVITY_DEBUG", "🚀 Starting health sync...")
                    val success = com.coachie.app.service.HealthSyncService.sync(this@MainActivity)
                    if (success) {
                        android.util.Log.d("MAIN_ACTIVITY_DEBUG", "✅ Health sync completed successfully")
                        
                        // CRITICAL: Verify yesterday's data was saved after sync
                        if (isMorning && yesterdayLog == null) {
                            val verifyResult = repository.getDailyLog(userId, yesterdayString)
                            val verifyLog = verifyResult.getOrNull()
                            if (verifyLog != null) {
                                android.util.Log.i("MAIN_ACTIVITY_DEBUG", "✅✅✅ FALLBACK SYNC SUCCESS - YESTERDAY'S DATA NOW SAVED ✅✅✅")
                            } else {
                                android.util.Log.e("MAIN_ACTIVITY_DEBUG", "❌ Fallback sync completed but yesterday's data still not found")
                            }
                        }
                    } else {
                        android.util.Log.e("MAIN_ACTIVITY_DEBUG", "❌ Health sync failed after retries")
                        // User will be notified by sync service if permission is missing
                    }
                } else {
                    android.util.Log.w("MAIN_ACTIVITY_DEBUG", "⚠️ No health service permissions - skipping sync")
                    // Show message to user
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "Health service not connected. Please connect Google Fit or Health Connect in Settings > Permissions.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MAIN_ACTIVITY_DEBUG", "❌ Error triggering health sync", e)
                e.printStackTrace()
            }
        }
    }
    
}

