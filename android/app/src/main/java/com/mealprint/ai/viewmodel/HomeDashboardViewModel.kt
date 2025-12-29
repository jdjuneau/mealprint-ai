// Sample Firestore Security Rules for testing (allow anonymous writes)
// Copy to firestore.rules and deploy with: firebase deploy --only firestore:rules
//
// rules_version = '2';
// service cloud.firestore {
//   match /databases/{database}/documents {
//     // Allow anonymous users to write to logs collection for testing
//     match /logs/{document=**} {
//       allow read, write: if true;
//     }
//
//     // Allow anonymous users to read/write to users collection for testing
//     match /users/{document=**} {
//       allow read, write: if true;
//     }
//
//     // For production, use authenticated user rules:
//     // match /logs/{userId}/{document=**} {
//     //   allow read, write: if request.auth != null && request.auth.uid == userId;
//     // }
//     // match /users/{userId} {
//     //   allow read, write: if request.auth != null && request.auth.uid == userId;
//     // }
//   }
// }

package com.coachie.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.data.HabitRepository
import com.mealprint.ai.data.ai.SmartCoachEngine
import com.mealprint.ai.data.model.DailyLog
import com.mealprint.ai.data.model.HealthLog
import com.mealprint.ai.data.model.Habit
import com.mealprint.ai.data.model.HabitCompletion
import com.mealprint.ai.data.model.Streak
import com.mealprint.ai.data.model.UserProfile
import com.mealprint.ai.util.SunshineVitaminDCalculator
import com.mealprint.ai.utils.DebugLogger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.mealprint.ai.data.model.MicronutrientType
import com.mealprint.ai.domain.MacroTargets
import com.mealprint.ai.domain.MacroTargetsCalculator

/**
 * ViewModel for HomeScreen dashboard
 */
class HomeDashboardViewModel(
    private val repository: FirebaseRepository,
    val userId: String,
    private val context: android.content.Context? = null
) : ViewModel() {

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    private val _todayLog = MutableStateFlow<DailyLog?>(null)
    val todayLog: StateFlow<DailyLog?> = _todayLog.asStateFlow()

    private val _todayHealthLogs = MutableStateFlow<List<HealthLog>>(emptyList())
    val todayHealthLogs: StateFlow<List<HealthLog>> = _todayHealthLogs.asStateFlow()

    private val _streak = MutableStateFlow<Streak?>(null)
    val streak: StateFlow<Streak?> = _streak.asStateFlow()

    private val _aiInsight = MutableStateFlow<String?>(null)
    val aiInsight: StateFlow<String?> = _aiInsight.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _userGoals = MutableStateFlow<Map<String, Any>?>(null)
    val userGoals: StateFlow<Map<String, Any>?> = _userGoals.asStateFlow()

    private val _useImperial = MutableStateFlow(true) // Default to imperial
    val useImperial: StateFlow<Boolean> = _useImperial.asStateFlow()

    private val _sunshineLogMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val sunshineLogMessage: SharedFlow<String> = _sunshineLogMessage.asSharedFlow()

    private val _sunshineTotals = MutableStateFlow<Map<MicronutrientType, Double>>(emptyMap())
    val sunshineTotals: StateFlow<Map<MicronutrientType, Double>> = _sunshineTotals.asStateFlow()

    private val _habits = MutableStateFlow<List<Habit>>(emptyList())
    val habits: StateFlow<List<Habit>> = _habits.asStateFlow()

    private val _habitCompletions = MutableStateFlow<List<HabitCompletion>>(emptyList())
    val habitCompletions: StateFlow<List<HabitCompletion>> = _habitCompletions.asStateFlow()

    private val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    private val smartCoachEngine = SmartCoachEngine.getInstance(context)
    private val habitRepository = HabitRepository.getInstance()
    
    // Real-time listeners
    private var healthLogsListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var dailyLogListener: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        DebugLogger.logDebug("HomeDashboardViewModel", "Initializing for user: $userId")
        loadDashboardData()
        setupRealtimeListeners()
        loadHabits()
    }
    
    override fun onCleared() {
        super.onCleared()
        // Clean up listeners when ViewModel is cleared
        healthLogsListener?.remove()
        dailyLogListener?.remove()
    }
    
    /**
     * Set up real-time Firestore listeners for automatic updates
     */
    private fun setupRealtimeListeners() {
        // Validate userId before setting up listeners
        if (userId.isBlank() || userId == "anonymous") {
            android.util.Log.w("HomeDashboardViewModel", "Invalid userId for setting up listeners: '$userId'")
            return
        }
        
        viewModelScope.launch {
            try {
                val db = Firebase.firestore
                
                // Listen for health logs changes
                val healthLogsRef = db.collection("logs")
                    .document(userId)
                    .collection("daily")
                    .document(today)
                    .collection("entries")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                
                healthLogsListener = healthLogsRef.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.e("HomeDashboardViewModel", "Error listening to health logs", error)
                        return@addSnapshotListener
                    }
                    
                    if (snapshot != null) {
                        viewModelScope.launch {
                            try {
                                val logs = snapshot.documents.mapNotNull { doc ->
                                    repository.parseHealthLogFromDocument(doc)
                                }
                                DebugLogger.logDebug("HomeDashboardViewModel", "Real-time update: ${logs.size} health logs")
                                _todayHealthLogs.value = logs
                                
                                // Update sunshine totals if needed
                                val sunshineTotal = logs
                                    .filterIsInstance<HealthLog.SunshineLog>()
                                    .sumOf { it.vitaminDIu }
                                val sunshineExtras = _todayLog.value?.micronutrientExtras?.get(MicronutrientType.VITAMIN_D.id) ?: 0.0
                                _sunshineTotals.value = if (sunshineTotal + sunshineExtras > 0) {
                                    mapOf(MicronutrientType.VITAMIN_D to (sunshineTotal + sunshineExtras))
                                } else {
                                    emptyMap()
                                }
                                
                                // Don't regenerate AI insight on every log change - rate limited to prevent excessive API calls
                                // The cloud function has 12-hour caching, so we don't need to call it on every update
                            } catch (e: Exception) {
                                android.util.Log.e("HomeDashboardViewModel", "Error processing health logs update", e)
                            }
                        }
                    }
                }
                
                // Listen for daily log changes (for Google Fit calories)
                // Note: DailyLog is stored in logs/{uid}/daily/{date}, not dailyLogs collection
                val dailyLogRef = db.collection("logs")
                    .document(userId)
                    .collection("daily")
                    .document(today)
                
                dailyLogListener = dailyLogRef.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.e("HomeDashboardViewModel", "Error listening to daily log", error)
                        return@addSnapshotListener
                    }
                    
                    if (snapshot != null) {
                        viewModelScope.launch {
                            try {
                                val log = if (snapshot.exists()) {
                                    snapshot.toObject(DailyLog::class.java) ?: DailyLog.createForDate(userId, today)
                                } else {
                                    DailyLog.createForDate(userId, today)
                                }
                                DebugLogger.logDebug("HomeDashboardViewModel", "Real-time update: daily log calories burned = ${log.caloriesBurned}")
                                _todayLog.value = log
                                
                                // Update sunshine totals if needed
                                val sunshineExtras = log.micronutrientExtras[MicronutrientType.VITAMIN_D.id] ?: 0.0
                                val sunshineTotal = _todayHealthLogs.value
                                    .filterIsInstance<HealthLog.SunshineLog>()
                                    .sumOf { it.vitaminDIu }
                                _sunshineTotals.value = if (sunshineTotal + sunshineExtras > 0) {
                                    mapOf(MicronutrientType.VITAMIN_D to (sunshineTotal + sunshineExtras))
                                } else {
                                    emptyMap()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("HomeDashboardViewModel", "Error processing daily log update", e)
                            }
                        }
                    }
                }
                
                DebugLogger.logDebug("HomeDashboardViewModel", "Real-time listeners set up successfully")
            } catch (e: Exception) {
                android.util.Log.e("HomeDashboardViewModel", "Error setting up real-time listeners", e)
            }
        }
    }

    /**
     * Load all dashboard data
     */
    fun loadDashboardData() {
        DebugLogger.logDebug("HomeDashboardViewModel", "Loading dashboard data for date: $today")
        DebugLogger.logState("HomeDashboardViewModel", "isLoading", _isLoading.value)

        _isLoading.value = true
        DebugLogger.logStateChange("HomeDashboardViewModel", "isLoading", false, true)

        viewModelScope.launch {
            try {
                // Load profile
                DebugLogger.logFirebaseOperation("HomeDashboardViewModel", "getUserProfile", null)
                val profileResult = repository.getUserProfile(userId)
                val newProfile = profileResult.getOrNull()
                DebugLogger.logFirebaseOperation("HomeDashboardViewModel", "getUserProfile", profileResult.isSuccess)
                DebugLogger.logStateChange("HomeDashboardViewModel", "profile", _profile.value, newProfile)
                _profile.value = newProfile

                // Load today's log
                DebugLogger.logFirebaseOperation("HomeDashboardViewModel", "getDailyLog", null)
                val logResult = repository.getDailyLog(userId, today)
                val newLog = logResult.getOrNull() ?: DailyLog.createForDate(userId, today)
                DebugLogger.logFirebaseOperation("HomeDashboardViewModel", "getDailyLog", logResult.isSuccess)
                DebugLogger.logStateChange("HomeDashboardViewModel", "todayLog", _todayLog.value, newLog)
                _todayLog.value = newLog

                // Load today's health logs
                // CRITICAL FIX: Sleep ending before 10 AM is now saved to "today" (the day you wake up)
                // So "last night's sleep" automatically appears on "today" - no need to fetch yesterday
                DebugLogger.logFirebaseOperation("HomeDashboardViewModel", "getHealthLogs", null)
                val healthLogsResult = repository.getHealthLogs(userId, today)
                val newHealthLogs = healthLogsResult.getOrNull() ?: emptyList()
                DebugLogger.logFirebaseOperation("HomeDashboardViewModel", "getHealthLogs", healthLogsResult.isSuccess)
                DebugLogger.logStateChange("HomeDashboardViewModel", "todayHealthLogs", _todayHealthLogs.value.size, newHealthLogs.size)
                _todayHealthLogs.value = newHealthLogs

                val sunshineTotal = newHealthLogs
                    .filterIsInstance<HealthLog.SunshineLog>()
                    .sumOf { it.vitaminDIu }
                val sunshineExtras = newLog.micronutrientExtras[MicronutrientType.VITAMIN_D.id] ?: 0.0
                _sunshineTotals.value =
                    if (sunshineTotal + sunshineExtras > 0) {
                        mapOf(MicronutrientType.VITAMIN_D to (sunshineTotal + sunshineExtras))
                    } else {
                        emptyMap()
                    }

                // Load streak
                DebugLogger.logFirebaseOperation("HomeDashboardViewModel", "getUserStreak", null)
                val streakResult = repository.getUserStreak(userId)
                val newStreak = streakResult.getOrNull()
                DebugLogger.logFirebaseOperation("HomeDashboardViewModel", "getUserStreak", streakResult.isSuccess)
                DebugLogger.logStateChange("HomeDashboardViewModel", "streak", _streak.value, newStreak)
                _streak.value = newStreak

                // Load user goals
                DebugLogger.logFirebaseOperation("HomeDashboardViewModel", "getUserGoals", null)
                val goalsResult = repository.getUserGoals(userId)
                val newGoals = goalsResult.getOrNull()
                DebugLogger.logFirebaseOperation("HomeDashboardViewModel", "getUserGoals", goalsResult.isSuccess)
                DebugLogger.logStateChange("HomeDashboardViewModel", "userGoals", _userGoals.value, newGoals)
                _userGoals.value = newGoals

                // Extract useImperial preference
                val newUseImperial = newGoals?.get("useImperial") as? Boolean ?: true
                DebugLogger.logStateChange("HomeDashboardViewModel", "useImperial", _useImperial.value, newUseImperial)
                _useImperial.value = newUseImperial

                // REMOVED: Brief generation on app open - briefs are only sent via scheduled functions (9 AM, 2 PM, 6 PM)
                // Users will receive briefs via push notifications at scheduled times
                // The brief card on the home screen will show the most recent scheduled brief
                // No need to generate a new brief every time the app opens - this was causing excessive OpenAI costs
                
                // Load the most recent brief from Firestore (stored by scheduled functions)
                DebugLogger.logFirebaseOperation("HomeDashboardViewModel", "getMostRecentBrief", null)
                val briefResult = repository.getMostRecentBrief(userId)
                val briefText = briefResult.getOrNull()
                DebugLogger.logFirebaseOperation("HomeDashboardViewModel", "getMostRecentBrief", briefResult.isSuccess)
                DebugLogger.logStateChange("HomeDashboardViewModel", "aiInsight", _aiInsight.value, briefText)
                _aiInsight.value = briefText

                _isLoading.value = false
                DebugLogger.logStateChange("HomeDashboardViewModel", "isLoading", true, false)
            } catch (e: Exception) {
                android.util.Log.e("HomeDashboardViewModel", "Error loading dashboard data", e)
                DebugLogger.logDebug("HomeDashboardViewModel", "Error loading dashboard data: ${e.message}")
                _isLoading.value = false
                DebugLogger.logStateChange("HomeDashboardViewModel", "isLoading", true, false)
            }
        }
    }

    /**
     * Load habits and habit completions for AI insights
     */
    private fun loadHabits() {
        // Validate userId before loading
        if (userId.isBlank() || userId == "anonymous") {
            android.util.Log.w("HomeDashboardViewModel", "Invalid userId for loading habits: '$userId'")
            return
        }
        
        viewModelScope.launch {
            try {
                // Load all active habits - use catch to handle cancellation gracefully
                habitRepository.getHabits(userId)
                    .catch { e ->
                        if (e !is kotlinx.coroutines.CancellationException) {
                            android.util.Log.e("HomeDashboardViewModel", "Error loading habits", e)
                        }
                    }
                    .collect { habits ->
                        _habits.value = habits
                        android.util.Log.d("HomeDashboardViewModel", "Habits loaded: ${habits.size} habits")
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                android.util.Log.d("HomeDashboardViewModel", "Habits loading cancelled (normal)")
                throw e // Re-throw cancellation to respect coroutine cancellation
            } catch (e: Exception) {
                android.util.Log.e("HomeDashboardViewModel", "Error loading habits", e)
            }
        }
        
        viewModelScope.launch {
            try {
                // Load today's habit completions
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                val todayStart = calendar.timeInMillis
                
                habitRepository.getRecentCompletions(userId, days = 1)
                    .catch { e ->
                        if (e !is kotlinx.coroutines.CancellationException) {
                            android.util.Log.e("HomeDashboardViewModel", "Error loading habit completions", e)
                        }
                    }
                    .collect { completions ->
                        val todayCompletions = completions.filter {
                            it.completedAt.time >= todayStart
                        }
                        _habitCompletions.value = todayCompletions
                        android.util.Log.d("HomeDashboardViewModel", "Habit completions loaded: ${todayCompletions.size} today")
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                android.util.Log.d("HomeDashboardViewModel", "Habit completions loading cancelled (normal)")
                throw e // Re-throw cancellation to respect coroutine cancellation
            } catch (e: Exception) {
                android.util.Log.e("HomeDashboardViewModel", "Error loading habit completions", e)
            }
        }
    }

    /**
     * REMOVED: generateAIInsight() function
     * Briefs are now ONLY generated via scheduled Cloud Functions (9 AM, 2 PM, 6 PM)
     * This prevents excessive OpenAI API calls when users open the app
     * Briefs are delivered via push notifications and can be viewed in the notification detail screen
     */

    /**
     * Calculate total calories consumed today
     */
    fun getCaloriesConsumed(): Int {
        val calories = _todayHealthLogs.value
            .filterIsInstance<HealthLog.MealLog>()
            .sumOf { it.calories }
        DebugLogger.logDebug("HomeDashboardViewModel", "Calculated calories consumed: $calories")
        return calories
    }

    /**
     * Calculate total calories burned today
     */
    fun getCaloriesBurned(): Int {
        val calories = _todayHealthLogs.value
            .filterIsInstance<HealthLog.WorkoutLog>()
            .sumOf { it.caloriesBurned }
        DebugLogger.logDebug("HomeDashboardViewModel", "Calculated calories burned: $calories")
        return calories
    }

    /**
     * Get estimated daily calories from profile
     */
    fun getDailyCalorieGoal(): Int {
        val goal = _profile.value?.estimatedDailyCalories ?: 2000
        DebugLogger.logDebug("HomeDashboardViewModel", "Daily calorie goal: $goal")
        return goal
    }

    fun getMacroTargets(): MacroTargets = MacroTargetsCalculator.calculate(_profile.value)

    fun quickLogSunshine(
        minutes: Int,
        uvIndex: Double,
        exposure: SunshineVitaminDCalculator.ExposureLevel,
        skinType: SunshineVitaminDCalculator.SkinType
    ) {
        viewModelScope.launch {
            try {
                val vitaminD = SunshineVitaminDCalculator.estimateVitaminD(minutes, uvIndex, exposure, skinType)
                val log = HealthLog.SunshineLog(
                    minutes = minutes,
                    uvIndex = uvIndex,
                    bodyCoverage = exposure.bodyCoverageFraction,
                    skinType = skinType.id,
                    vitaminDIu = vitaminD
                )

                val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                val result = repository.saveHealthLog(userId, date, log)
                if (result.isSuccess) {
                    try {
                        val baseLog = repository.getDailyLog(userId, date).getOrNull()
                            ?: DailyLog.createForDate(userId, date)
                        val extras = baseLog.micronutrientExtras.toMutableMap()
                        val vitaminDKey = MicronutrientType.VITAMIN_D.id
                        val updatedAmount = (extras[vitaminDKey] ?: 0.0) + vitaminD
                        extras[vitaminDKey] = updatedAmount
                        repository.saveDailyLog(
                            baseLog.copy(
                                micronutrientExtras = extras,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    } catch (inner: Exception) {
                        android.util.Log.e("HomeDashboardViewModel", "Failed to update daily log with sunshine extras", inner)
                    }

                    _sunshineLogMessage.tryEmit("Logged sunshine • ~${vitaminD.toInt()} IU vitamin D")
                    loadDashboardData()
                } else {
                    _sunshineLogMessage.tryEmit("Failed to log sunshine: ${result.exceptionOrNull()?.message ?: "Unknown error"}")
                }
            } catch (e: Exception) {
                _sunshineLogMessage.tryEmit("Failed to log sunshine: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }


    /**
     * Refresh dashboard data
     */
    fun refresh() {
        android.util.Log.d("HomeDashboardViewModel", "🔄🔄🔄 REFRESH CALLED - RELOADING ALL DASHBOARD DATA 🔄🔄🔄")
        DebugLogger.logUserInteraction("HomeDashboardViewModel", "Dashboard refresh requested")
        loadDashboardData()
    }

    /**
     * Test Firebase read/write operations for debugging
     */
    suspend fun testFirebaseFlow() {
        try {
            val uid = com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: return
            DebugLogger.logDebug("FirebaseTest", "UID: $uid")

            val db = Firebase.firestore

            // Enable network for testing
            db.enableNetwork()

            // Test write operation
            val testData = mapOf(
                "testClick" to System.currentTimeMillis(),
                "testTimestamp" to System.currentTimeMillis(),
                "testMessage" to "Firebase connectivity test"
            )

            db.collection("logs").document(uid).set(testData)
            DebugLogger.logDebug("WriteSuccess", "Data saved to logs/$uid")

            // Test read operation
            val snapshot = db.collection("logs").document(uid).get().await()
            DebugLogger.logDebug("ReadSuccess", "Data from logs/$uid: ${snapshot.data}")

            // Also test users collection if it exists
            try {
                val userSnapshot = db.collection("users").document(uid).get().await()
                if (userSnapshot.exists()) {
                    DebugLogger.logDebug("UserReadSuccess", "User data: ${userSnapshot.data}")
                } else {
                    DebugLogger.logDebug("UserReadInfo", "No user document found for $uid")
                }
            } catch (e: Exception) {
                DebugLogger.logDebug("UserReadSkipped", "Users collection test skipped: ${e.message}")
            }

        } catch (e: Exception) {
            DebugLogger.logDebug("FirebaseError", e.message ?: "Unknown Firebase error")
        }
    }

    class Factory(
        private val repository: FirebaseRepository,
        private val userId: String,
        private val context: android.content.Context? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeDashboardViewModel::class.java)) {
                return HomeDashboardViewModel(repository, userId, context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
