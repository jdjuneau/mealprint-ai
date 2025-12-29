package com.coachie.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.ai.GeminiClient
import com.coachie.app.data.local.PreferencesManager
import com.coachie.app.data.model.ChatMessage
import com.coachie.app.data.model.HealthLog
import com.coachie.app.data.model.MicronutrientTarget
import com.coachie.app.data.model.MicronutrientType
import com.coachie.app.data.model.toMicronutrientTypeMap
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import com.coachie.app.data.SubscriptionService
import com.coachie.app.data.model.SubscriptionTier
import com.coachie.app.data.model.AIFeature

/**
 * ViewModel for Coachie chat functionality.
 * Handles chat messages, Gemini AI integration, and daily nudges.
 */
class CoachChatViewModel(
    private val firebaseRepository: FirebaseRepository,
    private val preferencesManager: PreferencesManager,
    private val userId: String,
    private val context: Context? = null
) : ViewModel() {

    companion object {
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val DISPLAY_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
    }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _remainingChats = MutableStateFlow<Int?>(null)
    val remainingChats: StateFlow<Int?> = _remainingChats.asStateFlow()

    private val _subscriptionTier = MutableStateFlow<SubscriptionTier?>(null)
    val subscriptionTier: StateFlow<SubscriptionTier?> = _subscriptionTier.asStateFlow()

    private val _showUpgradePrompt = MutableStateFlow(false)
    val showUpgradePrompt: StateFlow<Boolean> = _showUpgradePrompt.asStateFlow()

    private val geminiClient = GeminiClient(context)
    private var nudgesEnabled = preferencesManager.nudgesEnabled
    private var userName: String = "friend"
    private var userGender: String? = null

    // Firestore collection for chat messages
    // CRITICAL: Validate userId before creating Firestore reference
    private val chatCollection = if (userId.isNotBlank()) {
        FirebaseFirestore.getInstance()
            .collection("chats")
            .document(userId)
            .collection("messages")
    } else {
        throw IllegalStateException("Cannot create CoachChatViewModel with empty userId")
    }

    init {
        loadChatHistory()
        viewModelScope.launch {
            refreshNudgePreference()
            checkAndSendDailyNudge()
            // Load subscription info
            loadSubscriptionInfo()
        }
    }

    /**
     * Load subscription tier and remaining chat count
     */
    private suspend fun loadSubscriptionInfo() {
        try {
            val tier = SubscriptionService.getUserTier(userId)
            _subscriptionTier.value = tier
            
            if (tier == SubscriptionTier.FREE) {
                val remaining = SubscriptionService.getRemainingAICalls(userId, AIFeature.AI_COACH_CHAT)
                _remainingChats.value = remaining
            } else {
                _remainingChats.value = Int.MAX_VALUE // Unlimited for Pro
            }
        } catch (e: Exception) {
            android.util.Log.e("CoachChatViewModel", "Error loading subscription info", e)
        }
    }

    /**
     * Load the last 5 messages from Firestore
     */
    private fun loadChatHistory() {
        viewModelScope.launch {
            try {
                val querySnapshot = chatCollection
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(5)
                    .get()
                    .await()

                val messages = querySnapshot.documents.mapNotNull { doc ->
                    doc.toObject(ChatMessage::class.java)
                }.reversed() // Reverse to show oldest first

                _messages.value = messages
            } catch (e: Exception) {
                _error.value = "Failed to load chat history: ${e.message}"
            }
        }
    }

    /**
     * Send a message from the user
     */
    fun sendMessage(content: String) {
        if (content.trim().isEmpty()) return

        viewModelScope.launch {
            // Check subscription before sending
            val tier = _subscriptionTier.value ?: SubscriptionService.getUserTier(userId)
            val remaining = if (tier == SubscriptionTier.FREE) {
                SubscriptionService.getRemainingAICalls(userId, AIFeature.AI_COACH_CHAT)
            } else {
                Int.MAX_VALUE
            }

            if (tier == SubscriptionTier.FREE && remaining <= 0) {
                _showUpgradePrompt.value = true
                return@launch
            }

            val userMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                content = content.trim(),
                isFromUser = true,
                timestamp = System.currentTimeMillis()
            )

            // Add user message immediately
            addMessageToUI(userMessage)

            // Save to Firestore
            saveMessageToFirestore(userMessage)

            // Clear input
            _inputText.value = ""

            // Record usage for free tier
            if (tier == SubscriptionTier.FREE) {
                SubscriptionService.recordAIFeatureUsage(userId, AIFeature.AI_COACH_CHAT)
                // Update remaining count
                val newRemaining = (remaining - 1).coerceAtLeast(0)
                _remainingChats.value = newRemaining
            }

            // Get AI response
            getGeminiResponse(content)
        }
    }

    /**
     * Dismiss upgrade prompt
     */
    fun dismissUpgradePrompt() {
        _showUpgradePrompt.value = false
    }

    /**
     * Update the input text
     */
    fun updateInputText(text: String) {
        _inputText.value = text
    }

    /**
     * Get response from Gemini AI
     */
    private fun getGeminiResponse(userMessage: String) {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                // Get basic user info for personalized response
                // CRITICAL SECURITY: Use constructor userId (authenticated user) as fallback, not SharedPreferences
                val userName = this@CoachChatViewModel.userName.takeIf { it != "friend" } ?: userId.takeIf { it.isNotBlank() && it != "anonymous_user" } ?: "there"

                // Create a simple prompt based on user message
                val userDataContext = buildUserDataContext()
                
                // Calculate days tracking and check goal achievement
                val profile = firebaseRepository.getUserProfile(userId).getOrNull()
                val daysTracking = if (profile?.startDate != null && profile.startDate > 0) {
                    val startDate = java.time.Instant.ofEpochMilli(profile.startDate)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                    val today = java.time.LocalDate.now()
                    java.time.temporal.ChronoUnit.DAYS.between(startDate, today).toInt().coerceAtLeast(0)
                } else {
                    0
                }
                
                // Check if user is achieving their goals
                val isNotAchievingGoals = checkIfNotAchievingGoals(profile, userDataContext)
                
                val prompt = createChatPrompt(userMessage, userName, userDataContext, daysTracking, isNotAchievingGoals)

                val response = geminiClient.generateResponse(prompt, userId)

                val coachMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    content = response,
                    isFromUser = false,
                    timestamp = System.currentTimeMillis()
                )

                // Add coach response
                addMessageToUI(coachMessage)

                // Save to Firestore
                saveMessageToFirestore(coachMessage)

            } catch (e: Exception) {
                // CRASH DEBUG: Look for this tag when chat bot crashes
                android.util.Log.e("MESSAGING_CRASH", "❌ CHAT BOT CRASH - Error getting Gemini response", e)
                android.util.Log.e("MESSAGING_CRASH", "Error type: ${e.javaClass.simpleName} | Message: ${e.message}", e)
                android.util.Log.e("MESSAGING_CRASH", "Error cause: ${e.cause?.message}", e)
                android.util.Log.e("CoachChatViewModel", "Error getting Gemini response: ${e.javaClass.simpleName}", e)
                android.util.Log.e("CoachChatViewModel", "Error message: ${e.message}")
                android.util.Log.e("CoachChatViewModel", "Error cause: ${e.cause?.message}")
                
                _error.value = "Failed to get response: ${e.message}"
                
                // Provide a more helpful error message based on the error type
                val errorMessage = when {
                    e.message?.contains("API key", ignoreCase = true) == true -> {
                        "I'm sorry, but I'm not properly configured right now. Please check with the app developer."
                    }
                    e.message?.contains("network", ignoreCase = true) == true || 
                    e.cause is java.net.UnknownHostException -> {
                        "I'm sorry, I'm having trouble connecting to the internet. Please check your connection and try again."
                    }
                    else -> {
                        "Sorry, I'm having trouble connecting right now. Please try again later."
                    }
                }
                
                val errorChatMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    content = errorMessage,
                    isFromUser = false,
                    timestamp = System.currentTimeMillis()
                )
                addMessageToUI(errorChatMessage)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Create a contextual prompt for Gemini based on user message and name
     */
    private fun createChatPrompt(userMessage: String, userName: String, userDataContext: String, daysTracking: Int = 0, isNotAchievingGoals: Boolean = false): String {
        val context = "You are Coachie, a no-nonsense fitness AI coach. You hold users accountable to their goals and push them to succeed."

        val coachingStyle = if (daysTracking >= 30) {
            // After 30 days, be very supportive and encouraging
            """
            YOUR COACHING STYLE (User has been tracking for $daysTracking days - BE VERY SUPPORTIVE):
            - Be extremely encouraging and supportive - they've shown commitment by tracking for 30+ days
            - Celebrate their consistency and dedication to their health journey
            - Acknowledge that building lasting habits takes time and they're on the right path
            - If they're not meeting goals, frame it as an opportunity to adjust strategy, not failure
            - Provide gentle but specific guidance on what to change
            - Emphasize that progress isn't always linear and setbacks are normal
            - Be their biggest cheerleader while still providing actionable advice
            - Reference their consistency as a strength: "You've been tracking for $daysTracking days - that's incredible commitment!"
            """
        } else {
            """
            YOUR COACHING STYLE:
            - Be direct and honest about progress (or lack thereof)
            - If they're not meeting their goals (especially weight loss), call them out directly
            - Push them to do better - you're a coach, not a cheerleader
            - Be encouraging but also hold them accountable
            - Use tough love when needed - if they've been tracking for 2 weeks with no weight loss, tell them what they need to change
            - Reference specific numbers from their data (weight, calories, steps, etc.)
            - Give actionable, specific advice - not vague encouragement
            """
        }

        val recommendationsSection = if (isNotAchievingGoals) {
            """
            
            CRITICAL: User is NOT achieving their main goals. You MUST provide specific recommendations:
            
            1. HABIT RECOMMENDATIONS:
               - Suggest 2-3 specific habits that directly support their goal (e.g., "Track all meals daily", "Walk 10k steps", "Log weight every morning")
               - Explain how each habit will help them reach their goal
               - Be specific about when/how to implement each habit
            
            2. DIETARY PREFERENCES:
               - If current diet isn't working, recommend a different dietary approach
               - For weight loss: Suggest calorie deficit strategies, meal timing, or macro adjustments
               - For muscle building: Suggest higher protein intake, meal frequency, or specific meal plans
               - Reference their current dietary preference and suggest modifications if needed
            
            3. MEAL PLANS:
               - Recommend using the Weekly Blueprint feature for structured meal planning
               - Suggest specific meal patterns (e.g., "Try meal prepping 3 days of lunches on Sunday")
               - Recommend using AI Meal Inspiration to find recipes that fit their goals
            
            4. WORKOUT RECOMMENDATIONS:
               - Suggest specific workout types that align with their goal
               - For weight loss: Recommend cardio frequency, HIIT workouts, or daily step goals
               - For muscle building: Recommend strength training frequency, progressive overload, or specific exercises
               - Reference their current activity level and suggest increases if needed
            
            Be specific and actionable - don't just say "eat better" or "exercise more". Give concrete steps they can take TODAY.
            """
        } else {
            ""
        }

        return """
            $context

            $coachingStyle

            Always ground advice in the user data snapshot when it's relevant, especially micronutrient gaps. 
            If specific nutrient deficiencies are listed, recommend foods or supplements that address those exact nutrients.
            Prefer suggesting supplements the user already takes when appropriate, otherwise offer concrete product categories.
            If data is missing, acknowledge the gap before advising.
            $recommendationsSection

            USER DATA SNAPSHOT:
            ${userDataContext.ifBlank { "No recent health data available." }}

            User: $userName
            Message: "$userMessage"
            Days tracking: $daysTracking days

            Respond as Coachie: ${if (daysTracking >= 30) "Be extremely supportive and encouraging while providing actionable guidance." else "Be direct, hold them accountable, and push them to meet their goals."}
            If they're not making progress, tell them what they need to change - be specific.
            ${if (isNotAchievingGoals) "CRITICAL: Provide specific recommendations for habits, dietary changes, meal plans, and workouts that will help them achieve their goals." else ""}
            Keep responses concise but informative (under 150 words).
            Use emojis appropriately.
            Focus on fitness, nutrition, and wellness with actionable takeaways.
            Address the user by name when appropriate.
        """.trimIndent()
    }

    private suspend fun refreshNudgePreference() {
        if (userId.isBlank()) {
            nudgesEnabled = false
            return
        }

        try {
            val profile = firebaseRepository.getUserProfile(userId).getOrNull()
            if (profile != null) {
                nudgesEnabled = profile.nudgesEnabled
                preferencesManager.nudgesEnabled = profile.nudgesEnabled
                if (profile.name.isNotBlank()) {
                    userName = profile.name
                }
            } else {
                nudgesEnabled = preferencesManager.nudgesEnabled
            }

            val goals = firebaseRepository.getUserGoals(userId).getOrNull()
            val genderValue = goals?.get("gender") as? String
            if (!genderValue.isNullOrBlank()) {
                userGender = genderValue
            }
        } catch (_: Exception) {
            nudgesEnabled = preferencesManager.nudgesEnabled
        }
        if (userName == "friend") {
            // Try to get user name from profile
            viewModelScope.launch {
                try {
                    val profileResult = firebaseRepository.getUserProfile(userId)
                    if (profileResult.isSuccess) {
                        val profile = profileResult.getOrNull()
                        if (profile != null && profile.name.isNotBlank()) {
                            userName = profile.name
                        }
                    }
                } catch (_: Exception) {
                    // Keep default name if profile fetch fails
                }
            }
        }
    }

    /**
     * Check if we should send a daily nudge (8 AM)
     */
    private fun checkAndSendDailyNudge() {
        if (!nudgesEnabled) return

        val today = LocalDate.now()
        val todayString = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val nudgeKey = "daily_nudge_$todayString"

        // Check if we've already sent a nudge today
        val prefs = preferencesManager.appContext.getSharedPreferences("coachie_chat", 0)
        if (prefs.getBoolean(nudgeKey, false)) return

        // Check if it's around 8 AM (within 1 hour window)
        val now = LocalTime.now()
        val eightAM = LocalTime.of(8, 0)
        val timeDiff = java.time.Duration.between(eightAM, now).toMinutes()

        if (timeDiff in -60..60) { // Within 1 hour of 8 AM
            sendDailyNudge()
            prefs.edit().putBoolean(nudgeKey, true).apply()
        }
    }

    /**
     * Send automated daily nudge message
     */
    private fun sendDailyNudge() {
        if (!nudgesEnabled) return
        viewModelScope.launch {
            try {
                firebaseRepository.getUserProfile(userId).getOrNull()?.let { profile ->
                    if (profile.name.isNotBlank()) {
                        userName = profile.name
                    }
                }

                // Get user goals to retrieve useImperial preference
                val goals = firebaseRepository.getUserGoals(userId).getOrNull()
                val useImperial = (goals?.get("useImperial") as? Boolean) ?: true

                val yesterday = LocalDate.now().minusDays(1)
                val dateKey = yesterday.format(DATE_FORMATTER)

                val dailyLog = firebaseRepository.getDailyLog(userId, dateKey).getOrNull()
                val healthLogs = firebaseRepository.getHealthLogs(userId, dateKey).getOrNull().orEmpty()

                val steps = dailyLog?.steps ?: 0
                val waterMl = dailyLog?.water ?: 0
                val moodDescription = dailyLog?.moodDescription
                val energyLevel = dailyLog?.energy

                val supplementCount = healthLogs.count { it is HealthLog.SupplementLog }
                val mealsLogged = healthLogs.count { it is HealthLog.MealLog }

                val workoutLogs = healthLogs.filterIsInstance<HealthLog.WorkoutLog>()
                val workoutMinutes = workoutLogs.sumOf { it.durationMin }
                val workoutSessions = workoutLogs.size

                val sleepLogs = healthLogs.filterIsInstance<HealthLog.SleepLog>()
                val totalSleepMinutes = sleepLogs.sumOf { sleepLogDurationMinutes(it) }
                val totalSleepHours = totalSleepMinutes / 60.0

                val recap = buildYesterdayRecap(
                    dateLabel = yesterday.format(DISPLAY_DATE_FORMATTER),
                    steps = steps,
                    workoutSessions = workoutSessions,
                    workoutMinutes = workoutMinutes,
                    sleepHours = totalSleepHours,
                    supplements = supplementCount,
                    meals = mealsLogged,
                    waterMl = waterMl,
                    mood = moodDescription,
                    energy = energyLevel,
                    useImperial = useImperial
                )

                val score = calculateDailyScore(
                    steps = steps,
                    workoutMinutes = workoutMinutes,
                    sleepHours = totalSleepHours,
                    waterMl = waterMl,
                    meals = mealsLogged,
                    supplements = supplementCount
                )

                val focus = determineTodayFocus(
                    steps = steps,
                    workoutMinutes = workoutMinutes,
                    sleepHours = totalSleepHours,
                    waterMl = waterMl,
                    meals = mealsLogged
                )

                val friendlyName = userName.substringBefore(' ').ifBlank { userName }
                val nudgeContent = buildString {
                    appendLine("Good morning $friendlyName! ☀️")
                    appendLine()
                    appendLine("Yesterday’s recap:")
                    appendLine(recap)
                    appendLine()
                    appendLine("Coachie score: ${score}/100")
                    appendLine("Today’s focus: $focus")
                }.trimEnd()

                val nudgeMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    content = nudgeContent,
                    isFromUser = false,
                    timestamp = System.currentTimeMillis(),
                    messageType = ChatMessage.MessageType.DAILY_NUDGE
                )

                addMessageToUI(nudgeMessage)
                saveMessageToFirestore(nudgeMessage)

            } catch (_: Exception) {
                // Silently fail for daily nudges to avoid disrupting user experience
            }
        }
    }

    /**
     * Add message to UI state (keeping only last 5)
     */
    private fun addMessageToUI(message: ChatMessage) {
        val currentMessages = _messages.value.toMutableList()
        currentMessages.add(message)

        // Keep only last 5 messages
        if (currentMessages.size > 5) {
            currentMessages.removeAt(0)
        }

        _messages.value = currentMessages
    }

    /**
     * Save message to Firestore
     */
    private fun saveMessageToFirestore(message: ChatMessage) {
        viewModelScope.launch {
            try {
                chatCollection.document(message.id).set(message).await()
            } catch (e: Exception) {
                // Message might still be in UI, but not persisted
                // This is acceptable for chat messages
            }
        }
    }

    /**
     * Clear any error message
     */
    fun clearError() {
        _error.value = null
    }

    private fun sleepLogDurationMinutes(sleepLog: HealthLog.SleepLog): Double {
        val durationMillis = (sleepLog.endTime - sleepLog.startTime).coerceAtLeast(0L)
        return durationMillis / 60000.0
    }

    private fun buildYesterdayRecap(
        dateLabel: String,
        steps: Int,
        workoutSessions: Int,
        workoutMinutes: Int,
        sleepHours: Double,
        supplements: Int,
        meals: Int,
        waterMl: Int,
        mood: String?,
        energy: Int?,
        useImperial: Boolean = true
    ): String {
        val lines = mutableListOf<String>()
        lines += "• Date: $dateLabel"
        lines += "• Steps: ${formatNumber(steps)}"
        lines += if (workoutSessions > 0) {
            val sessionLabel = if (workoutSessions == 1) "session" else "sessions"
            "• Workouts: $workoutSessions $sessionLabel (${workoutMinutes} min total)"
        } else {
            "• Workouts: Rest day"
        }
        lines += if (sleepHours > 0.1) {
            "• Sleep: ${formatSleep(sleepHours)}"
        } else {
            "• Sleep: Not logged"
        }
        lines += "• Meals logged: ${if (meals > 0) meals else "Not logged"}"
        lines += "• Supplements logged: ${if (supplements > 0) supplements else "None"}"
        lines += "• Water: ${formatWater(waterMl, useImperial)}"
        mood?.let { lines += "• Mood: $it" }
        energy?.let { lines += "• Energy: ${formatEnergy(it)}" }

        return lines.joinToString("\n")
    }

    private fun calculateDailyScore(
        steps: Int,
        workoutMinutes: Int,
        sleepHours: Double,
        waterMl: Int,
        meals: Int,
        supplements: Int
    ): Int {
        var score = 0
        score += when {
            steps >= 10000 -> 25
            steps >= 8000 -> 20
            steps >= 5000 -> 15
            steps > 0 -> 10
            else -> 0
        }
        score += when {
            sleepHours in 7.0..9.0 -> 20
            sleepHours >= 6.0 -> 15
            sleepHours > 0.0 -> 10
            else -> 0
        }
        score += when {
            workoutMinutes >= 45 -> 20
            workoutMinutes >= 20 -> 15
            workoutMinutes > 0 -> 10
            else -> 5
        }
        score += when {
            waterMl >= 2200 -> 15
            waterMl >= 1800 -> 10
            waterMl > 0 -> 5
            else -> 0
        }
        score += if (meals >= 3) 10 else if (meals > 0) 5 else 0
        score += if (supplements > 0) 5 else 0

        return score.coerceIn(0, 100)
    }

    private fun determineTodayFocus(
        steps: Int,
        workoutMinutes: Int,
        sleepHours: Double,
        waterMl: Int,
        meals: Int
    ): String {
        if (steps == 0 && workoutMinutes == 0 && sleepHours < 0.1 && waterMl == 0 && meals == 0) {
            return "Let’s log your meals, movement, and sleep today so I can give you a stronger recap tomorrow."
        }

        return when {
            steps < 7000 -> "Schedule a walk and aim to push your steps past 8,000 today."
            workoutMinutes == 0 -> "Plan a quick strength or mobility session to keep your streak alive."
            sleepHours < 7.0 -> "Prioritize winding down early so you can hit 7–8 hours of sleep tonight."
            waterMl < 2000 -> "Keep a bottle nearby and sip often—let’s reach at least 2.0L of water."
            meals < 3 -> "Outline today’s meals now so you stay fueled and avoid rushed choices."
            else -> "Keep the momentum—repeat yesterday’s wins and stay consistent!"
        }
    }

    private fun formatNumber(value: Int): String =
        String.format(Locale.getDefault(), "%,d", value)

    private fun formatSleep(hours: Double): String {
        val totalMinutes = (hours * 60).roundToInt()
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            m > 0 -> "${m}m"
            else -> "Not logged"
        }
    }

    private fun formatWater(waterMl: Int, useImperial: Boolean = true): String {
        if (waterMl <= 0) return "Not logged"
        return if (useImperial) {
            // Convert ml to glasses (1 glass = 240ml = 8 fl oz)
            val glasses = waterMl / 240.0
            String.format(Locale.getDefault(), "%.1f glasses", glasses)
        } else {
            // Metric: use liters
            val liters = waterMl / 1000.0
            String.format(Locale.getDefault(), "%.1f L", liters)
        }
    }

    private fun formatEnergy(energy: Int): String {
        return when (energy) {
            1 -> "Very low"
            2 -> "Low"
            3 -> "Moderate"
            4 -> "High"
            5 -> "Very high"
            else -> "Not logged"
        }
    }

    private suspend fun buildUserDataContext(): String {
        return try {
            val today = LocalDate.now()
            val dateKey = today.format(DATE_FORMATTER)

            // Get user profile and goals
            val profile = firebaseRepository.getUserProfile(userId).getOrNull()
            val goals = firebaseRepository.getUserGoals(userId).getOrNull()
            val selectedGoal = goals?.get("selectedGoal") as? String ?: ""
            val useImperial = (goals?.get("useImperial") as? Boolean) ?: true

            val dailyLog = firebaseRepository.getDailyLog(userId, dateKey).getOrNull()
            val healthLogs = firebaseRepository.getHealthLogs(userId, dateKey).getOrNull().orEmpty()

            if (userGender.isNullOrBlank()) {
                val goalsGender = goals?.get("gender") as? String
                if (!goalsGender.isNullOrBlank()) {
                    userGender = goalsGender
                }
            }
            val gender = userGender

            // Get weight logs from last 14 days to track progress
            val weightLogs = mutableListOf<Pair<String, Double>>()
            for (i in 0..13) {
                val date = today.minusDays(i.toLong())
                val dateStr = date.format(DATE_FORMATTER)
                val dayLogs = firebaseRepository.getHealthLogs(userId, dateStr).getOrNull().orEmpty()
                val dayWeightLogs = dayLogs.filterIsInstance<HealthLog.WeightLog>()
                dayWeightLogs.maxByOrNull { it.timestamp }?.let { log ->
                    val weightKg = log.weightKg
                    weightLogs.add(Pair(dateStr, weightKg))
                }
            }
            weightLogs.sortBy { it.first } // Sort by date

            // Calculate weight loss progress
            val currentWeight = profile?.currentWeight ?: 0.0
            val goalWeight = profile?.goalWeight ?: 0.0
            val weightLossGoal = goalWeight < currentWeight && goalWeight > 0
            val weightGainGoal = goalWeight > currentWeight && goalWeight > 0
            
            val weightProgress = buildString {
                if (currentWeight > 0 && goalWeight > 0) {
                    val currentWeightDisplay = if (useImperial) currentWeight * 2.205 else currentWeight
                    val goalWeightDisplay = if (useImperial) goalWeight * 2.205 else goalWeight
                    val unit = if (useImperial) "lbs" else "kg"
                    appendLine("Weight Goal: ${String.format("%.1f", currentWeightDisplay)} $unit → ${String.format("%.1f", goalWeightDisplay)} $unit")
                    
                    if (weightLossGoal) {
                        val toLose = currentWeight - goalWeight
                        val toLoseDisplay = if (useImperial) toLose * 2.205 else toLose
                        appendLine("Goal: Lose ${String.format("%.1f", toLoseDisplay)} $unit")
                    } else if (weightGainGoal) {
                        val toGain = goalWeight - currentWeight
                        val toGainDisplay = if (useImperial) toGain * 2.205 else toGain
                        appendLine("Goal: Gain ${String.format("%.1f", toGainDisplay)} $unit")
                    }
                    
                    if (weightLogs.size >= 2) {
                        val oldestWeight = weightLogs.first().second
                        val newestWeight = weightLogs.last().second
                        val daysTracked = weightLogs.size
                        val weightChange = newestWeight - oldestWeight
                        val weightChangeDisplay = if (useImperial) weightChange * 2.205 else weightChange
                        val unit = if (useImperial) "lbs" else "kg"
                        
                        appendLine("Weight tracking: ${daysTracked} days of data")
                        if (kotlin.math.abs(weightChange) > 0.1) {
                            val changeStr = if (weightChange > 0) "+${String.format("%.1f", weightChangeDisplay)}" else String.format("%.1f", weightChangeDisplay)
                            appendLine("Weight change over ${daysTracked} days: $changeStr $unit")
                            
                            if (weightLossGoal && weightChange >= 0) {
                                appendLine("⚠️ NOT LOSING WEIGHT - Goal is weight loss but weight is stable or increasing")
                            } else if (weightGainGoal && weightChange <= 0) {
                                appendLine("⚠️ NOT GAINING WEIGHT - Goal is weight gain but weight is stable or decreasing")
                            }
                        } else {
                            appendLine("Weight change: No significant change (${String.format("%.1f", kotlin.math.abs(weightChangeDisplay))} $unit)")
                            if (weightLossGoal || weightGainGoal) {
                                appendLine("⚠️ NO PROGRESS - Weight is not moving toward goal")
                            }
                        }
                    } else if (weightLogs.size == 1) {
                        appendLine("Weight tracking: Only 1 weight entry logged - need more data to track progress")
                    } else {
                        appendLine("Weight tracking: No weight logs found - user needs to log weight to track progress")
                    }
                } else {
                    appendLine("Weight Goal: Not set")
                }
                
                if (selectedGoal.isNotBlank()) {
                    appendLine("Primary Goal: $selectedGoal")
                }
            }

            val meals = healthLogs.filterIsInstance<HealthLog.MealLog>()
            val supplements = healthLogs.filterIsInstance<HealthLog.SupplementLog>()

            val totals = mutableMapOf<MicronutrientType, Double>()
            fun accumulate(map: Map<MicronutrientType, Double>) {
                map.forEach { (type, value) ->
                    totals[type] = (totals[type] ?: 0.0) + value
                }
            }
            meals.forEach { accumulate(it.micronutrientsTyped) }
            supplements.forEach { accumulate(it.micronutrientsTyped) }
            dailyLog?.micronutrientExtras
                ?.toMicronutrientTypeMap()
                ?.let { accumulate(it) }

            val statuses = MicronutrientType.ordered.map { type ->
                MicronutrientStatus(
                    type = type,
                    total = totals[type] ?: 0.0,
                    target = type.targetForGender(gender)
                )
            }

            val deficits = statuses.filter { status ->
                val minTarget = status.target.min
                minTarget > 0 && status.total + 0.1 < minTarget
            }.sortedBy { status ->
                val minTarget = status.target.min
                if (minTarget <= 0) 1.0 else status.total / minTarget
            }

            val deficitLines = if (deficits.isEmpty()) {
                listOf("No significant micronutrient gaps logged yet today.")
            } else {
                deficits.take(6).map { status ->
                    val minTarget = status.target.min
                    val total = status.total
                    val needed = (minTarget - total).coerceAtLeast(0.0)
                    val ratio = if (minTarget > 0) (total / minTarget * 100).coerceIn(0.0, 500.0) else 100.0
                    val unit = status.type.unit.displaySuffix
                    val targetText = status.target.format()
                    val topSources = status.type.topSources.joinToString(limit = 3, truncated = "…")
                    "${status.type.displayName}: ${formatMicronutrientValue(total)} $unit of $targetText $unit (${ratio.roundToInt()}% of goal). Needs ~${formatMicronutrientValue(needed)} $unit more. Top food sources: $topSources."
                }
            }

            val supplementsList = supplements.mapNotNull { it.name.takeIf { name -> name.isNotBlank() } }.distinct()
            val mealsList = meals.mapNotNull { it.foodName.takeIf { name -> name.isNotBlank() } }.distinct()

            val steps = dailyLog?.steps ?: 0
            val waterMl = dailyLog?.water ?: 0
            val calories = meals.sumOf { it.calories }

            buildString {
                appendLine("Date: $dateKey")
                appendLine()
                appendLine("=== GOALS & PROGRESS ===")
                appendLine(weightProgress)
                appendLine()
                appendLine("=== TODAY'S DATA ===")
                appendLine("Steps today: ${formatNumber(steps)}")
                appendLine("Calories consumed: ${if (calories > 0) formatNumber(calories) else "Not logged"}")
                appendLine("Water intake: ${if (waterMl > 0) formatWater(waterMl, useImperial) else "Not logged"}")
                appendLine(
                    "Supplements logged today: ${
                        if (supplementsList.isNotEmpty()) supplementsList.joinToString(limit = 5, truncated = "…")
                        else "None"
                    }"
                )
                appendLine(
                    "Meals logged today: ${
                        if (mealsList.isNotEmpty()) mealsList.joinToString(limit = 5, truncated = "…")
                        else "Not logged"
                    }"
                )
                appendLine()
                appendLine("=== MICRONUTRIENT GAPS ===")
                deficitLines.forEach { appendLine(" • $it") }
                
                // Add score context if available
                try {
                    val scoreContext = getScoreContext()
                    if (scoreContext.isNotBlank()) {
                        appendLine()
                        appendLine("=== COACHIE SCORE TRENDS ===")
                        appendLine(scoreContext)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("CoachChatViewModel", "Failed to fetch score context", e)
                    // Don't fail the whole context if score fetch fails
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CoachChatViewModel", "Failed to build user data context", e)
            ""
        }
    }

    /**
     * Check if user is not achieving their main goals
     */
    private suspend fun checkIfNotAchievingGoals(profile: com.coachie.app.data.model.UserProfile?, userDataContext: String): Boolean {
        if (profile == null) return false
        
        val today = LocalDate.now()
        val goalWeight = profile.goalWeight
        val currentWeight = profile.currentWeight
        
        // Check if user has a weight goal
        val hasWeightGoal = goalWeight > 0 && currentWeight > 0
        if (!hasWeightGoal) return false
        
        val isWeightLossGoal = goalWeight < currentWeight - 0.1
        val isWeightGainGoal = goalWeight > currentWeight + 0.1
        
        if (!isWeightLossGoal && !isWeightGainGoal) return false
        
        // Get weight logs from last 30 days to check progress
        val weightLogs = mutableListOf<Pair<String, Double>>()
        for (i in 0..29) {
            val date = today.minusDays(i.toLong())
            val dateStr = date.format(DATE_FORMATTER)
            val dayLogs = firebaseRepository.getHealthLogs(userId, dateStr).getOrNull().orEmpty()
            val dayWeightLogs = dayLogs.filterIsInstance<HealthLog.WeightLog>()
            dayWeightLogs.maxByOrNull { it.timestamp }?.let { log ->
                weightLogs.add(Pair(dateStr, log.weightKg))
            }
        }
        weightLogs.sortBy { it.first }
        
        if (weightLogs.size < 2) return false // Need at least 2 weight entries
        
        val oldestWeight = weightLogs.first().second
        val newestWeight = weightLogs.last().second
        val weightChange = newestWeight - oldestWeight
        
        // Check if progress is not being made toward goal
        return when {
            isWeightLossGoal && weightChange >= 0 -> true // Should be losing but not losing
            isWeightGainGoal && weightChange <= 0 -> true // Should be gaining but not gaining
            isWeightLossGoal && weightChange > 0 -> true // Actually gaining weight when should lose
            isWeightGainGoal && weightChange < 0 -> true // Actually losing weight when should gain
            else -> {
                // Check if progress is too slow (less than 0.5kg change in 30 days when goal requires more)
                val weightToChange = kotlin.math.abs(goalWeight - currentWeight)
                val progressRate = kotlin.math.abs(weightChange) / weightLogs.size // per day
                val expectedProgress = weightToChange / 90.0 // Expected progress per day (assuming 90 days to goal)
                progressRate < expectedProgress * 0.5 // Less than 50% of expected progress
            }
        }
    }
    
    /**
     * Get score context for AI coach
     */
    private suspend fun getScoreContext(): String {
        return try {
            val functions = Firebase.functions
            val result = functions
                .getHttpsCallable("getScoreHistory")
                .call(mapOf("days" to 30))
                .await()
            
            val data = result.data as? Map<*, *> ?: return ""
            if ((data["success"] as? Boolean) != true) return ""
            
            val scores = data["scores"] as? List<*> ?: emptyList<Any>()
            if (scores.isEmpty()) return ""
            
            val trend = data["trend"] as? Map<*, *> ?: return ""
            val stats = data["stats"] as? Map<*, *> ?: return ""
            
            val todayScore = (scores.lastOrNull() as? Map<*, *>)?.get("score") as? Number ?: return ""
            val trendDirection = trend["direction"] as? String ?: "stable"
            val trendChange = (trend["change"] as? Number)?.toDouble() ?: 0.0
            val trendChangePercent = (trend["changePercent"] as? Number)?.toDouble() ?: 0.0
            val recentAverage = (trend["recentAverage"] as? Number)?.toDouble() ?: 0.0
            val previousAverage = (trend["previousAverage"] as? Number)?.toDouble() ?: 0.0
            val streak = (trend["streak"] as? Number)?.toInt() ?: 0
            val streakType = trend["streakType"] as? String ?: "stable"
            
            val average = (stats["average"] as? Number)?.toDouble() ?: 0.0
            val highest = (stats["highest"] as? Number)?.toInt() ?: 0
            val last7DaysAvg = (stats["last7DaysAverage"] as? Number)?.toDouble()
            val last30DaysAvg = (stats["last30DaysAverage"] as? Number)?.toDouble()
            
            val trendEmoji = when (trendDirection) {
                "up" -> "📈"
                "down" -> "📉"
                else -> "➡️"
            }
            val trendText = when (trendDirection) {
                "up" -> "improving (+${String.format("%.1f", Math.abs(trendChangePercent))}%)"
                "down" -> "declining (${String.format("%.1f", trendChangePercent)}%)"
                else -> "stable"
            }
            
            buildString {
                appendLine("Today's Score: $todayScore/100")
                appendLine("7-Day Average: ${if (last7DaysAvg != null) String.format("%.1f", last7DaysAvg) else "N/A"}/100")
                appendLine("30-Day Average: ${if (last30DaysAvg != null) String.format("%.1f", last30DaysAvg) else "N/A"}/100")
                appendLine("Overall Average: ${String.format("%.1f", average)}/100")
                appendLine("Highest Score: $highest/100")
                appendLine("Trend: $trendEmoji $trendText (${if (trendChange > 0) "+" else ""}${String.format("%.1f", trendChange)} points vs previous week)")
                if (streak > 0) {
                    appendLine("Streak: $streak day ${if (streakType == "improving") "improvement" else "decline"} streak 🔥")
                }
                appendLine()
                appendLine("Use this score data to:")
                appendLine("- Celebrate improvements and encourage continued progress")
                appendLine("- Identify declining trends and provide specific actionable advice to improve")
                appendLine("- Reference specific score numbers when motivating the user")
                appendLine("- Suggest concrete actions to improve their score")
            }
        } catch (e: Exception) {
            android.util.Log.w("CoachChatViewModel", "Error fetching score context", e)
            ""
        }
    }

    private fun formatMicronutrientValue(value: Double): String {
        return when {
            value >= 100 -> String.format(Locale.US, "%.0f", value)
            value >= 10 -> String.format(Locale.US, "%.1f", value)
            value > 0 -> String.format(Locale.US, "%.2f", value)
            else -> "0"
        }
    }

    private data class MicronutrientStatus(
        val type: MicronutrientType,
        val total: Double,
        val target: MicronutrientTarget
    )

    /**
     * Factory for creating CoachChatViewModel with dependencies
     */
    class Factory(
        private val firebaseRepository: FirebaseRepository,
        private val preferencesManager: PreferencesManager,
        private val userId: String,
        private val context: Context? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CoachChatViewModel::class.java)) {
                return CoachChatViewModel(firebaseRepository, preferencesManager, userId, context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
