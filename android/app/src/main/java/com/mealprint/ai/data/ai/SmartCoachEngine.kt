package com.coachie.app.data.ai

import android.content.Context
import com.mealprint.ai.data.Secrets
import com.mealprint.ai.data.SubscriptionService
import com.mealprint.ai.data.model.HealthLog
import com.mealprint.ai.data.model.SubscriptionTier
import com.mealprint.ai.data.model.UserProfile
import com.mealprint.ai.data.model.DailyLog
import com.mealprint.ai.data.model.Habit
import com.mealprint.ai.data.model.HabitCompletion
import com.mealprint.ai.util.DailyScoreCalculator
import com.mealprint.ai.util.CategoryScores
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mealprint.ai.domain.MacroTargetsCalculator
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.functions.FirebaseFunctions

/**
 * Smart Coach Engine that analyzes health logs and generates personalized daily insights.
 * Routes free users to Gemini Flash (5x cheaper), Pro users to OpenAI (premium quality).
 */
class SmartCoachEngine(private val context: Context? = null) {

    // Cache for daily insights (prevents duplicate API calls for same data)
    private val insightCache = mutableMapOf<String, Pair<String, Long>>()
    private val MAX_CACHE_SIZE = 10
    private val CACHE_EXPIRY_MS = 4 * 60 * 60 * 1000L // 4 hours

    private val openAI: OpenAI by lazy {
        OpenAI(token = Secrets.getOpenAIApiKey())
    }

    private val geminiFlashClient: GeminiFlashClient? by lazy {
        context?.let { GeminiFlashClient(it) }
    }

    private val subscriptionService = SubscriptionService()

    /**
     * Generate comprehensive insight based on recent health logs and user profile.
     *
     * @param recentLogs List of date-health log pairs for recent days
     * @param profile User's fitness profile
     * @param dailyLog Today's daily log (optional, for scoring)
     * @param habits List of user's habits (optional, for scoring)
     * @param habitCompletions List of habit completions (optional, for scoring)
     * @param userId User ID for subscription tier check (optional, defaults to free tier)
     * @return Personalized insight message
     */
    suspend fun generateDailyInsight(
        recentLogs: List<Pair<String, List<HealthLog>>> = emptyList(),
        profile: UserProfile,
        dailyLog: DailyLog? = null,
        habits: List<Habit> = emptyList(),
        habitCompletions: List<HabitCompletion> = emptyList(),
        userId: String = ""
    ): String {
        // Extract today's logs for backward compatibility
        val todayLogs = recentLogs.firstOrNull { it.first == java.time.LocalDate.now().toString() }?.second ?: emptyList()

        // Generate cache key based on key data points
        val cacheKey = generateCacheKey(todayLogs, profile)
        val now = System.currentTimeMillis()

        // Clean expired cache entries
        insightCache.entries.removeIf { (_, value) -> now - value.second > CACHE_EXPIRY_MS }

        // Check cache first to reduce API costs
        val cached = insightCache[cacheKey]
        if (cached != null && (now - cached.second) < CACHE_EXPIRY_MS) {
            android.util.Log.d("SmartCoachEngine", "Using cached insight (age: ${(now - cached.second) / 1000 / 60} minutes)")
            return cached.first
        }

        return withContext(Dispatchers.IO) {
            try {
                // Analyze patterns from recent logs
                val patterns = analyzePatterns(todayLogs, profile, recentLogs)

                // DON'T use quickInsight - always generate comprehensive AI insight
                // Quick insights are too basic and boring

                // Calculate daily scores for motivation
                val meals = todayLogs.filterIsInstance<HealthLog.MealLog>()
                val workouts = todayLogs.filterIsInstance<HealthLog.WorkoutLog>()
                val sleepLogs = todayLogs.filterIsInstance<HealthLog.SleepLog>()
                val waterLogs = todayLogs.filterIsInstance<HealthLog.WaterLog>()
                
                val macroTargets = MacroTargetsCalculator.calculate(profile)
                val categoryScores = DailyScoreCalculator.calculateAllScores(
                    meals = meals,
                    workouts = workouts,
                    sleepLogs = sleepLogs,
                    waterLogs = waterLogs,
                    allHealthLogs = todayLogs,
                    dailyLog = dailyLog,
                    habits = habits,
                    habitCompletions = habitCompletions,
                    calorieGoal = macroTargets.calorieGoal,
                    stepsGoal = 10000 // Default steps goal, can be loaded from user goals if needed
                )
                val dailyScore = categoryScores.calculateDailyScore()

                // Generate AI-powered insight with full context (including habits and recent logs)
                val insight = generateAIInsight(
                    todayLogs, 
                    profile, 
                    patterns, 
                    categoryScores, 
                    dailyScore,
                    habits,
                    habitCompletions,
                    recentLogs,
                    userId
                )

                // Cache the result
                insightCache[cacheKey] = Pair(insight, now)

                // Maintain cache size limit
                if (insightCache.size > MAX_CACHE_SIZE) {
                    insightCache.remove(insightCache.keys.first())
                }

                insight
            } catch (e: Exception) {
                android.util.Log.e("SmartCoachEngine", "Error generating insight", e)
                // Calculate daily score for fallback
                val meals = todayLogs.filterIsInstance<HealthLog.MealLog>()
                val workouts = todayLogs.filterIsInstance<HealthLog.WorkoutLog>()
                val sleepLogs = todayLogs.filterIsInstance<HealthLog.SleepLog>()
                val waterLogs = todayLogs.filterIsInstance<HealthLog.WaterLog>()
                val macroTargets = MacroTargetsCalculator.calculate(profile)
                val categoryScores = DailyScoreCalculator.calculateAllScores(
                    meals = meals,
                    workouts = workouts,
                    sleepLogs = sleepLogs,
                    waterLogs = waterLogs,
                    allHealthLogs = todayLogs,
                    dailyLog = dailyLog,
                    habits = habits,
                    habitCompletions = habitCompletions,
                    calorieGoal = macroTargets.calorieGoal,
                    stepsGoal = 10000
                )
                val dailyScore = categoryScores.calculateDailyScore()
                getFallbackInsight(todayLogs, profile, dailyScore)
            }
        }
    }

    /**
     * Generate a cache key based on key data points to prevent duplicate API calls
     */
    private fun generateCacheKey(
        todayLogs: List<HealthLog>,
        profile: UserProfile
    ): String {
        // Create a key based on essential data that affects the insight
        val logsHash = todayLogs.sumOf { it.hashCode() }
        val profileHash = profile.hashCode()
        val today = java.time.LocalDate.now().toString()

        return "$today-$logsHash-$profileHash"
    }

    /**
     * Analyze health logs for specific patterns
     */
    private fun analyzePatterns(
        allLogs: List<HealthLog>,
        profile: UserProfile,
        recentLogs: List<Pair<String, List<HealthLog>>> = emptyList()
    ): HealthPatterns {
        val meals = allLogs.filterIsInstance<HealthLog.MealLog>()
        val workouts = allLogs.filterIsInstance<HealthLog.WorkoutLog>()
        val sleepLogs = allLogs.filterIsInstance<HealthLog.SleepLog>()
        val waterLogs = allLogs.filterIsInstance<HealthLog.WaterLog>()
        val moodLogs = allLogs.filterIsInstance<HealthLog.MoodLog>()
        val weightLogs = allLogs.filterIsInstance<HealthLog.WeightLog>()

        // Extract weight logs from recent days for trend analysis
        val allWeightLogs = recentLogs.flatMap { (_, logs) ->
            logs.filterIsInstance<HealthLog.WeightLog>()
        }.sortedBy { it.timestamp }

        // Calculate totals
        val totalProtein = meals.sumOf { it.protein }
        val totalCaloriesBurned = workouts.sumOf { it.caloriesBurned }
        val totalWater = waterLogs.sumOf { it.ml }

        // Get latest sleep from logs
        val sleepHours = sleepLogs.maxByOrNull { it.timestamp }?.durationHours ?: 0.0

        // Default heart rate (since we don't log heart rate)
        val avgHeartRate = 72.0f

        // Count habits logged (at least one entry per type)
        val habitsLogged = listOf(
            meals.isNotEmpty(),
            workouts.isNotEmpty(),
            sleepLogs.isNotEmpty(),
            waterLogs.isNotEmpty(),
            moodLogs.isNotEmpty()
        ).count { it }

        // Weight trend analysis
        val weightTrend = if (allWeightLogs.size >= 2) {
            val recentWeights = allWeightLogs.takeLast(3) // Last 3 weight entries
            if (recentWeights.size >= 2) {
                val firstWeight = recentWeights.first().weightKg
                val lastWeight = recentWeights.last().weightKg
                val weightChange = lastWeight - firstWeight
                when {
                    weightChange < -0.5 -> "losing_weight" // Lost more than 0.5kg
                    weightChange > 0.5 -> "gaining_weight" // Gained more than 0.5kg
                    else -> "maintaining_weight"
                }
            } else "insufficient_data"
        } else "no_weight_data"

        // Pattern detection
        val hasLowProtein = totalProtein < 50 // Less than 50g protein
        val hasLowSleep = sleepHours < 6.0 // Less than 6 hours
        val hasHighWorkout = totalCaloriesBurned >= 400 // High calorie burn
        val hasLowWater = totalWater < 1000 // Less than 1L water
        val hasMultipleHabits = habitsLogged >= 3
        val hasLowHeartRate = avgHeartRate < 70.0f // Resting HR below 70 BPM (improvement indicator)
        val hasHighSteps = totalCaloriesBurned > 300 // Estimate high activity from logged workouts
        val isLosingWeight = weightTrend == "losing_weight"
        val isGainingWeight = weightTrend == "gaining_weight"

        // Quick insight for specific patterns
        val quickInsight = when {
            // Weight-related insights
            isLosingWeight && hasLowProtein -> {
                "Great weight loss! Add protein to maintain muscle"
            }
            isGainingWeight && hasHighWorkout -> {
                "Watch calories if building muscle — log meals carefully"
            }
            weightTrend == "no_weight_data" && meals.isNotEmpty() -> {
                "Track your weight to see progress from ${meals.size} logged meals"
            }
            isLosingWeight && hasHighWorkout -> {
                "Perfect combo: exercise + weight loss = muscle preservation!"
            }

            hasLowProtein && hasLowSleep -> {
                "Add eggs to breakfast + sleep by 10 PM"
            }
            hasHighWorkout && hasLowWater -> {
                "Hydrate! You burned $totalCaloriesBurned cal"
            }
            hasLowHeartRate -> {
                "Your resting HR is improving — keep hydrating!"
            }
            hasMultipleHabits -> {
                "You're crushing it! Keep the streak alive"
            }
            else -> null
        }

        return HealthPatterns(
            totalProtein = totalProtein,
            totalCaloriesBurned = totalCaloriesBurned,
            totalWater = totalWater,
            sleepHours = sleepHours,
            avgHeartRate = avgHeartRate,
            habitsLogged = habitsLogged,
            quickInsight = quickInsight,
            hasLowProtein = hasLowProtein,
            hasLowSleep = hasLowSleep,
            hasHighWorkout = hasHighWorkout,
            hasLowWater = hasLowWater,
            hasLowHeartRate = hasLowHeartRate,
            hasMultipleHabits = hasMultipleHabits
        )
    }

    /**
     * Generate AI-powered insight using OpenAI or Gemini Flash with full context
     */
    private suspend fun generateAIInsight(
        allLogs: List<HealthLog>,
        profile: UserProfile,
        patterns: HealthPatterns,
        categoryScores: CategoryScores,
        dailyScore: Int,
        habits: List<Habit> = emptyList(),
        habitCompletions: List<HabitCompletion> = emptyList(),
        recentLogs: List<Pair<String, List<HealthLog>>> = emptyList(),
        userId: String = ""
    ): String {
        val prompt = buildInsightPrompt(
            allLogs, 
            profile, 
            patterns, 
            categoryScores, 
            dailyScore,
            habits,
            habitCompletions,
            recentLogs
        )

        // Route based on subscription tier
        val tier = if (userId.isNotEmpty() && context != null) {
            subscriptionService.getUserTier(userId)
        } else {
            SubscriptionTier.FREE // Default to free if no userId or context
        }

        val text = if (tier == SubscriptionTier.PRO) {
            // Pro users: Use OpenAI
            val chatCompletionRequest = ChatCompletionRequest(
                model = ModelId("gpt-3.5-turbo"),
                messages = listOf(
                    ChatMessage(
                        role = ChatRole.System,
                        content = "You are Coachie, an AI fitness coach. Provide a 3-5 sentence morning brief with specific numbers (calories, macros, steps, sleep). Include a greeting, yesterday's key metrics, and 1-2 actionable recommendations. Be concise but specific."
                    ),
                    ChatMessage(
                        role = ChatRole.User,
                        content = prompt
                    )
                ),
                temperature = 0.8,
                maxTokens = 150
            )

            val response = openAI.chatCompletion(chatCompletionRequest)
            response.choices.firstOrNull()?.message?.content
        } else {
            // Free users: Use Gemini Flash
            android.util.Log.d("SmartCoachEngine", "Using Gemini Flash for free tier user")
            val client = geminiFlashClient
            if (context == null || client == null) {
                return getFallbackInsight(allLogs, profile, dailyScore)
            }
            val result = client.generateText(
                prompt = prompt,
                systemPrompt = """You are Coachie, an AI fitness coach. Provide a 3-5 sentence daily insight with specific numbers (calories, macros, steps, sleep). Include a greeting, yesterday's key metrics, and 1-2 actionable recommendations. Be concise but specific.
                
IMPORTANT FOR FREE TIER USERS: At the end of your insight, naturally mention one Pro feature that could help them reach their goals. Examples:
- "Upgrade to Pro for unlimited AI meal recommendations to help you hit your macro goals!"
- "Get personalized weekly meal plans with Pro to make meal planning easier!"
- "Unlock unlimited AI coach chat with Pro for 24/7 personalized guidance!"
- "Pro members get unlimited daily insights to track their progress every day!"

Keep the Pro mention brief (1 sentence) and relevant to their current goals. Don't be pushy - make it helpful and value-focused.""",
                temperature = 0.8,
                maxTokens = 200 // Increased to allow for Pro mention
            )
            result.getOrNull()
        }

        // Log approximate OpenAI usage directly to Firestore (no Functions required)
        try {
            val uid = profile.uid.takeIf { it.isNotBlank() } ?: ""
            if (uid.isNotEmpty()) {
                val dateStr = java.time.LocalDate.now().toString()
                val approxPromptTokens = prompt.length / 4
                val approxCompletionTokens = (text?.length ?: 0) / 4
                val event = hashMapOf(
                    "userId" to uid,
                    "timestamp" to System.currentTimeMillis(),
                    "source" to "dashboardInsight",
                    "model" to "gpt-3.5-turbo",
                    "promptTokens" to approxPromptTokens,
                    "completionTokens" to approxCompletionTokens,
                    "totalTokens" to (approxPromptTokens + approxCompletionTokens),
                    "estimatedCostUsd" to ((approxPromptTokens + approxCompletionTokens) * 0.000002).toDouble() // very conservative
                )
                val db = Firebase.firestore
                android.util.Log.d("SmartCoachEngine", "📊 Logging usage: userId=$uid, date=$dateStr, tokens=${approxPromptTokens + approxCompletionTokens}")
                db.collection("logs").document(uid)
                    .collection("daily").document(dateStr)
                    .collection("ai_usage").add(event)
                    .addOnSuccessListener {
                        android.util.Log.d("SmartCoachEngine", "✅ Successfully logged usage for $dateStr")
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("SmartCoachEngine", "❌ FAILED to log usage for $dateStr: ${e.message}", e)
                    }
            } else {
                android.util.Log.w("SmartCoachEngine", "⚠️ Cannot log usage: uid is empty")
            }
        } catch (e: Exception) {
            android.util.Log.e("SmartCoachEngine", "❌ Exception logging usage: ${e.message}", e)
        }

        return text?.takeIf { it.isNotBlank() && it.length > 10 }
            ?: getFallbackInsight(allLogs, profile, dailyScore)
    }

    /**
     * Build comprehensive prompt for Gemini AI
     */
    private fun buildInsightPrompt(
        allLogs: List<HealthLog>,
        profile: UserProfile,
        patterns: HealthPatterns,
        categoryScores: CategoryScores,
        dailyScore: Int,
        habits: List<Habit> = emptyList(),
        habitCompletions: List<HabitCompletion> = emptyList(),
        recentLogs: List<Pair<String, List<HealthLog>>> = emptyList()
    ): String {
        val meals = allLogs.filterIsInstance<HealthLog.MealLog>()
        val workouts = allLogs.filterIsInstance<HealthLog.WorkoutLog>()
        val sleepLogs = allLogs.filterIsInstance<HealthLog.SleepLog>()
        val waterLogs = allLogs.filterIsInstance<HealthLog.WaterLog>()
        val moodLogs = allLogs.filterIsInstance<HealthLog.MoodLog>()
        val menstrualLogs = allLogs.filterIsInstance<HealthLog.MenstrualLog>()
        val journalLogs = allLogs.filterIsInstance<HealthLog.JournalEntry>()
        val meditationLogs = allLogs.filterIsInstance<HealthLog.MeditationLog>()
        val mindfulSessions = allLogs.filterIsInstance<HealthLog.MindfulSession>()
        val winEntries = allLogs.filterIsInstance<HealthLog.WinEntry>()
        val supplementLogs = allLogs.filterIsInstance<HealthLog.SupplementLog>()

        // Format meals summary (condensed to save tokens)
        val mealsSummary = if (meals.isNotEmpty()) {
            val totalCal = meals.sumOf { it.calories }
            val totalProtein = meals.sumOf { it.protein }
            "${meals.size} meals, ${totalCal}cal, ${totalProtein}g protein"
        } else {
            "No meals"
        }

        // Format workouts summary (condensed to save tokens)
        val workoutsSummary = if (workouts.isNotEmpty()) {
            val totalMin = workouts.sumOf { it.durationMin }
            val totalCal = workouts.sumOf { it.caloriesBurned }
            "${workouts.size} workouts, ${totalMin}min, ${totalCal}cal"
        } else {
            "No workouts"
        }

        // Format sleep summary
        val sleepSummary = if (sleepLogs.isNotEmpty()) {
            val latest = sleepLogs.maxByOrNull { it.timestamp }
            latest?.let {
                "Latest: ${String.format("%.1f", it.durationHours)} hours, quality ${it.quality}/5"
            } ?: "No sleep data"
        } else {
            "No sleep logged"
        }

        // Format water summary
        val waterSummary = if (waterLogs.isNotEmpty()) {
            val total = waterLogs.sumOf { it.ml }
            "${total}ml (${waterLogs.size} entries)"
        } else {
            "No water logged"
        }

        // Format mood summary
        val moodSummary = if (moodLogs.isNotEmpty()) {
            val latest = moodLogs.maxByOrNull { it.timestamp }
            latest?.let {
                val summary = "Latest: ${it.level}/5 (${it.moodDescription})"
                val details = mutableListOf<String>()
                if (it.emotions.isNotEmpty()) details.add("Emotions: ${it.emotions.joinToString(", ")}")
                if (it.energyLevel != null) details.add("Energy: ${it.energyLevel}/10")
                if (it.stressLevel != null) details.add("Stress: ${it.stressLevel}/10")
                if (it.sleepQuality != null) details.add("Sleep: ${it.sleepQuality}/5")
                if (details.isNotEmpty()) "$summary (${details.joinToString("; ")})" else summary
            } ?: "No mood data"
        } else {
            "No mood logged"
        }

        // Format menstrual cycle summary
        val menstrualSummary = if (menstrualLogs.isNotEmpty()) {
            val latest = menstrualLogs.maxByOrNull { it.timestamp }
            latest?.let {
                val activity = when {
                    it.isPeriodStart -> "Period started"
                    it.isPeriodEnd -> "Period ended"
                    it.symptoms.isNotEmpty() -> "Symptoms: ${it.symptoms.joinToString(", ")}"
                    else -> "Cycle activity"
                }
                "Latest: $activity${it.painLevel?.let { " (pain: $it/10)" } ?: ""}"
            } ?: "No cycle data"
        } else {
            "No menstrual cycle logged"
        }

        // Format journal summary (condensed to save tokens)
        val journalSummary = if (journalLogs.isNotEmpty()) {
            val latest = journalLogs.maxByOrNull { it.timestamp }
            latest?.let {
                val wordCount = it.wordCount
                val completed = if (it.isCompleted) "done" else "partial"
                "$wordCount words, $completed"
            } ?: "No journal"
        } else {
            "No journal"
        }

        // Format meditation summary (condensed to save tokens)
        val meditationSummary = if (meditationLogs.isNotEmpty() || mindfulSessions.isNotEmpty()) {
            val totalMinutes = meditationLogs.sumOf { it.durationMinutes } + 
                              mindfulSessions.sumOf { it.durationSeconds / 60 }
            "${meditationLogs.size + mindfulSessions.size} sessions, ${totalMinutes}min"
        } else {
            "No meditation"
        }

        // Format wins summary (condensed to save tokens)
        val winsSummary = if (winEntries.isNotEmpty()) {
            "${winEntries.size} wins"
        } else {
            "No wins"
        }

        // Format supplements summary
        val supplementsSummary = if (supplementLogs.isNotEmpty()) {
            supplementLogs.joinToString(", ") { it.name }
        } else {
            "No supplements logged"
        }

        // Format habits summary (condensed to save tokens)
        val habitsSummary = if (habits.isNotEmpty()) {
            val activeHabits = habits.filter { it.isActive }
            val completedToday = habitCompletions.distinctBy { it.habitId }.size
            val topStreak = habits.maxOfOrNull { it.streakCount } ?: 0
            "${activeHabits.size} active, $completedToday done today, top streak: $topStreak"
        } else {
            "No habits"
        }

        // Calculate energy score trend from recent logs (if available)
        // Note: Energy scores are stored in daily log, not in entries
        // This would need to be passed separately or fetched - placeholder for now
        val energyScoreTrend: String? = null

        val macroTargets = MacroTargetsCalculator.calculate(profile)
        
        // Calculate actual macros from meals
        val actualProtein = meals.sumOf { it.protein }
        val actualCarbs = meals.sumOf { it.carbs }
        val actualFat = meals.sumOf { it.fat }
        val actualCalories = meals.sumOf { it.calories }
        
        // Calculate macro adherence (percentage of target)
        val proteinAdherence = if (macroTargets.proteinGrams > 0) {
            (actualProtein.toDouble() / macroTargets.proteinGrams * 100).toInt()
        } else 0
        val carbsAdherence = if (macroTargets.carbsGrams > 0) {
            (actualCarbs.toDouble() / macroTargets.carbsGrams * 100).toInt()
        } else 0
        val fatAdherence = if (macroTargets.fatGrams > 0) {
            (actualFat.toDouble() / macroTargets.fatGrams * 100).toInt()
        } else 0
        
        // Determine if user is NOT hitting their goals (weight loss, muscle gain, etc.)
        val goalTrend = when {
            profile.goalWeight < profile.currentWeight - 0.1 -> "lose_weight"
            profile.goalWeight > profile.currentWeight + 0.1 -> "gain_weight"
            else -> "maintain_weight"
        }
        
        // Check if macros are significantly off target (not meeting goals)
        val isNotHittingMacroTargets = proteinAdherence < 80 || carbsAdherence < 80 || fatAdherence < 80 ||
                                      proteinAdherence > 120 || carbsAdherence > 120 || fatAdherence > 120
        
        // Check if weight goal is not being met (analyze from recent logs if available)
        val isNotMeetingWeightGoal = when (goalTrend) {
            "lose_weight" -> {
                // If trying to lose weight but macros suggest they're not in deficit or not following diet
                actualCalories > macroTargets.calorieGoal * 1.1 || isNotHittingMacroTargets
            }
            "gain_weight" -> {
                // If trying to gain weight but macros suggest they're not in surplus
                actualCalories < macroTargets.calorieGoal * 0.9 || isNotHittingMacroTargets
            }
            else -> false
        }
        
        // Analyze which macros are off
        val proteinOff = proteinAdherence < 80 || proteinAdherence > 120
        val carbsOff = carbsAdherence < 80 || carbsAdherence > 120
        val fatOff = fatAdherence < 80 || fatAdherence > 120

        // Build concise prompt to minimize tokens
        val promptBuilder = StringBuilder()
        // Condensed prompt to save tokens
        promptBuilder.append("${profile.name}: ${profile.currentWeight}kg→${profile.goalWeight}kg (${String.format("%.0f", profile.progressPercentage * 100)}%), ${macroTargets.calorieGoal}kcal goal.\n")
        promptBuilder.append("Diet: ${profile.dietaryPreferenceEnum.title} (targets: ${macroTargets.proteinGrams}g protein, ${macroTargets.carbsGrams}g carbs, ${macroTargets.fatGrams}g fat).\n")
        promptBuilder.append("Today: ${actualProtein}g protein (${proteinAdherence}% of target), ${actualCarbs}g carbs (${carbsAdherence}% of target), ${actualFat}g fat (${fatAdherence}% of target), ${actualCalories}cal.\n")
        promptBuilder.append("Activity: ${patterns.totalCaloriesBurned}cal burned, ${patterns.totalWater}ml water, ${String.format("%.1f", patterns.sleepHours)}h sleep.\n")
        
        // Add menstrual cycle phase awareness if applicable
        if (profile.menstrualCycleEnabled && profile.currentCyclePhase != com.coachie.app.data.model.CyclePhase.UNKNOWN) {
            val cycleGuidance = when (profile.currentCyclePhase) {
                com.coachie.app.data.model.CyclePhase.MENSTRUAL -> "Currently in MENSTRUAL phase (period) - focus on iron-rich foods, gentle exercise, and magnesium for cramps."
                com.coachie.app.data.model.CyclePhase.FOLLICULAR -> "Currently in FOLLICULAR phase (post-period) - energy building phase, good for strength training, include complex carbs and B vitamins."
                com.coachie.app.data.model.CyclePhase.OVULATION -> "Currently in OVULATION phase (fertile, peak energy) - optimal for intense workouts, emphasize high-quality proteins and antioxidant-rich foods."
                com.coachie.app.data.model.CyclePhase.LUTEAL -> "Currently in LUTEAL phase (pre-period, potential PMS) - focus on magnesium, B vitamins (especially B6), potassium-rich foods, and omega-3s for mood support."
                else -> ""
            }
            if (cycleGuidance.isNotEmpty()) {
                promptBuilder.append("Menstrual Cycle: $cycleGuidance\n")
            }
        }
        
        promptBuilder.append("Wellness: $journalSummary, $meditationSummary, $winsSummary. Habits: $habitsSummary.\n")
        
        // Calculate days tracking
        val daysTracking = if (profile.startDate != null && profile.startDate > 0) {
            val startDate = java.time.Instant.ofEpochMilli(profile.startDate)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            val today = java.time.LocalDate.now()
            java.time.temporal.ChronoUnit.DAYS.between(startDate, today).toInt().coerceAtLeast(0)
        } else {
            0
        }
        
        // Add diet recommendation instruction if user is NOT meeting their goals
        if (isNotMeetingWeightGoal) {
            val dietRecommendation = buildDietRecommendation(profile, goalTrend, proteinOff, carbsOff, fatOff)
            
            val tone = if (daysTracking >= 30) {
                "Be VERY supportive and encouraging - they've been tracking for $daysTracking days which shows incredible commitment. Frame recommendations as strategic adjustments, not failures. Celebrate their consistency while providing gentle guidance."
            } else {
                "Be firm but encouraging - emphasize that following the right diet is essential for reaching their goals. Don't accommodate their current eating patterns if they're not working."
            }
            
            promptBuilder.append("\nIMPORTANT: User's goal is to $goalTrend but they're not hitting their macro targets (protein: ${proteinAdherence}%, carbs: ${carbsAdherence}%, fat: ${fatAdherence}%). ")
            promptBuilder.append("User has been tracking for $daysTracking days. ")
            promptBuilder.append("As their coach, you need to provide specific recommendations to help them achieve their goals. ")
            promptBuilder.append("Provide concrete recommendations for: ")
            promptBuilder.append("1) Specific habits to add (e.g., 'Track all meals daily', 'Log weight every morning') ")
            promptBuilder.append("2) Dietary changes (suggest different dietary approach if current one isn't working: $dietRecommendation) ")
            promptBuilder.append("3) Meal planning (recommend using Weekly Blueprint or AI Meal Inspiration) ")
            promptBuilder.append("4) Workout adjustments (suggest specific workout types/frequency for their goal) ")
            promptBuilder.append("$tone ")
            promptBuilder.append("Be specific and actionable - give concrete steps they can take TODAY.")
        } else if (daysTracking >= 30) {
            // Even if meeting goals, be supportive after 30 days
            promptBuilder.append("\nUser has been tracking for $daysTracking days - be very supportive and celebrate their consistency and dedication to their health journey.")
        }
        
        // Add yesterday's data for comparison if available
        val yesterdayLogs = recentLogs.firstOrNull { 
            it.first == java.time.LocalDate.now().minusDays(1).toString() 
        }?.second ?: emptyList()
        
        // Declare variables outside if block for use in prompt examples
        val yesterdayMeals = yesterdayLogs.filterIsInstance<HealthLog.MealLog>()
        val yesterdayWorkouts = yesterdayLogs.filterIsInstance<HealthLog.WorkoutLog>()
        val yesterdaySleep = yesterdayLogs.filterIsInstance<HealthLog.SleepLog>()
        val yesterdayWater = yesterdayLogs.filterIsInstance<HealthLog.WaterLog>()
        val yesterdayStart = java.time.LocalDate.now().minusDays(1).atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val yesterdayEnd = yesterdayStart + (24 * 60 * 60 * 1000)
        val yesterdayHabitsCount = habitCompletions.count { 
            it.completedAt.time >= yesterdayStart && it.completedAt.time < yesterdayEnd 
        }
        
        val yesterdayCalories = yesterdayMeals.sumOf { it.calories }
        val yesterdayProtein = yesterdayMeals.sumOf { it.protein }
        val yesterdayWorkoutMin = yesterdayWorkouts.sumOf { it.durationMin }
        val yesterdaySleepHours = yesterdaySleep.sumOf { it.durationHours }
        val yesterdayWaterMl = yesterdayWater.sumOf { it.ml }
        
        if (yesterdayLogs.isNotEmpty()) {
            promptBuilder.append("\n\nYesterday's Performance:")
            promptBuilder.append("\n- Meals: ${yesterdayMeals.size} meals, ${yesterdayCalories}cal, ${yesterdayProtein}g protein")
            promptBuilder.append("\n- Activity: ${yesterdayWorkouts.size} workouts (${yesterdayWorkoutMin}min)")
            promptBuilder.append("\n- Sleep: ${String.format("%.1f", yesterdaySleepHours)} hours")
            promptBuilder.append("\n- Water: ${yesterdayWaterMl}ml")
            promptBuilder.append("\n- Habits: $yesterdayHabitsCount completed")
            
            // Add trend analysis
            val caloriesTrend = if (yesterdayCalories > 0) {
                val change = ((actualCalories - yesterdayCalories).toDouble() / yesterdayCalories * 100).toInt()
                if (change > 5) "↑${change}%" else if (change < -5) "↓${Math.abs(change)}%" else "→"
            } else ""
            val proteinTrend = if (yesterdayProtein > 0) {
                val change = ((actualProtein - yesterdayProtein).toDouble() / yesterdayProtein * 100).toInt()
                if (change > 5) "↑${change}%" else if (change < -5) "↓${Math.abs(change)}%" else "→"
            } else ""
            
            promptBuilder.append("\n\nTrends vs Yesterday: Calories $caloriesTrend, Protein $proteinTrend")
        }
        
        promptBuilder.append("\n\nScore: ${dailyScore}/100")
        val firstName = profile.name.split(" ").firstOrNull() ?: profile.name
        val yesterdayMealsCount = yesterdayMeals.size
        val yesterdayWorkoutsCount = yesterdayWorkouts.size
        
        promptBuilder.append("\n\n=== REQUIRED FORMAT ===")
        promptBuilder.append("\nGenerate a COMPREHENSIVE, DATA-RICH morning brief (MUST be 4-6 sentences, 100-150 words minimum).")
        promptBuilder.append("\n\nCRITICAL: Include specific numbers and metrics throughout. This is a detailed briefing, not a quick tip.")
        promptBuilder.append("\n\nYour brief MUST include ALL of the following with SPECIFIC NUMBERS:")
        promptBuilder.append("\n1. OPENING: Warm greeting with name + overall score/performance summary (e.g., 'Good morning, $firstName! You're starting today with a ${dailyScore}/100 score.')")
        if (yesterdayLogs.isNotEmpty()) {
            promptBuilder.append("\n2. YESTERDAY'S DETAILED DATA: Celebrate wins with exact numbers:")
            promptBuilder.append("\n   - Meals: $yesterdayMealsCount meals, ${yesterdayCalories} calories, ${yesterdayProtein}g protein, ${yesterdayMeals.sumOf { it.carbs }}g carbs, ${yesterdayMeals.sumOf { it.fat }}g fat")
            promptBuilder.append("\n   - Activity: $yesterdayWorkoutsCount workouts totaling ${yesterdayWorkoutMin} minutes, ${yesterdayWorkouts.sumOf { it.caloriesBurned }} calories burned")
            promptBuilder.append("\n   - Sleep: ${String.format("%.1f", yesterdaySleepHours)} hours")
            promptBuilder.append("\n   - Water: ${yesterdayWaterMl}ml (${String.format("%.1f", yesterdayWaterMl / 1000.0)}L)")
            promptBuilder.append("\n   - Habits: $yesterdayHabitsCount completed")
            if (yesterdayCalories > 0 && actualCalories > 0) {
                val calChange = ((actualCalories - yesterdayCalories).toDouble() / yesterdayCalories * 100).toInt()
                promptBuilder.append("\n   - Trends: Calories ${if (calChange > 0) "↑" else if (calChange < 0) "↓" else "→"}${Math.abs(calChange)}% vs yesterday")
            }
        } else {
            promptBuilder.append("\n2. YESTERDAY'S HIGHLIGHTS: Acknowledge their effort or set positive tone for today")
        }
        promptBuilder.append("\n3. TODAY'S SPECIFIC TARGETS: List exact goals with numbers:")
        promptBuilder.append("\n   - Calories: ${macroTargets.calorieGoal} calories")
        promptBuilder.append("\n   - Macros: ${macroTargets.proteinGrams}g protein, ${macroTargets.carbsGrams}g carbs, ${macroTargets.fatGrams}g fat")
        promptBuilder.append("\n   - Activity: 10,000 steps, ${profile.activityLevel} activity level")
        promptBuilder.append("\n   - Sleep: 7-9 hours recommended")
        promptBuilder.append("\n   - Water: ${(profile.currentWeight * 35).toInt()}ml (${String.format("%.1f", profile.currentWeight * 35 / 1000.0)}L) recommended")
        promptBuilder.append("\n4. CURRENT PROGRESS vs TARGETS: Show where they are now:")
        promptBuilder.append("\n   - Calories: ${actualCalories}/${macroTargets.calorieGoal} (${String.format("%.0f", (actualCalories.toDouble() / macroTargets.calorieGoal * 100))}% of goal)")
        promptBuilder.append("\n   - Protein: ${actualProtein}g/${macroTargets.proteinGrams}g (${proteinAdherence}% of target)")
        promptBuilder.append("\n   - Carbs: ${actualCarbs}g/${macroTargets.carbsGrams}g (${carbsAdherence}% of target)")
        promptBuilder.append("\n   - Fat: ${actualFat}g/${macroTargets.fatGrams}g (${fatAdherence}% of target)")
        promptBuilder.append("\n5. ACTIONABLE RECOMMENDATIONS: Give 2-3 specific actions with numbers:")
        if (actualProtein < macroTargets.proteinGrams * 0.8) {
            val needed = (macroTargets.proteinGrams - actualProtein).toInt()
            promptBuilder.append("\n   - Add ${needed}g protein (e.g., ${String.format("%.0f", needed / 25.0)} eggs or ${String.format("%.0f", needed / 30.0)} chicken breast) to hit your ${macroTargets.proteinGrams}g target")
        }
        if (actualCalories < macroTargets.calorieGoal * 0.7) {
            val needed = (macroTargets.calorieGoal - actualCalories).toInt()
            promptBuilder.append("\n   - Add ${needed} calories through healthy snacks to reach your ${macroTargets.calorieGoal} calorie goal")
        }
        promptBuilder.append("\n   - Take a 10-minute walk to boost activity and step count")
        promptBuilder.append("\n6. CLOSING: Motivational encouragement tied to their specific progress and goals")
        promptBuilder.append("\n\nTONE GUIDELINES:")
        if (dailyScore >= 70) {
            promptBuilder.append("\n- Celebrate their achievements enthusiastically")
            promptBuilder.append("\n- Encourage maintaining this momentum")
            promptBuilder.append("\n- Use celebratory language and emojis")
        } else if (dailyScore >= 40) {
            promptBuilder.append("\n- Acknowledge their progress positively")
            promptBuilder.append("\n- Suggest 1-2 specific, achievable improvements")
            promptBuilder.append("\n- Be encouraging and supportive")
        } else {
            promptBuilder.append("\n- Focus on the easiest wins to build momentum")
            promptBuilder.append("\n- Provide strong encouragement and support")
            promptBuilder.append("\n- Make suggestions feel achievable, not overwhelming")
        }
        promptBuilder.append("\n\nCRITICAL: Reference specific numbers from the data above. Use emojis appropriately. Make it feel personal and comprehensive. This is a morning brief, not a quick tip - it should feel substantial and informative.")

        return promptBuilder.toString()
    }

    /**
     * Build diet recommendation based on user's goals (not their current eating patterns)
     */
    private fun buildDietRecommendation(
        profile: UserProfile,
        goalTrend: String,
        proteinOff: Boolean,
        carbsOff: Boolean,
        fatOff: Boolean
    ): String {
        val currentDiet = profile.dietaryPreferenceEnum
        val recommendations = mutableListOf<String>()
        
        // Recommend diets based on GOALS, not current eating patterns
        when (goalTrend) {
            "lose_weight" -> {
                // For weight loss, recommend diets that support fat loss
                if (currentDiet !in listOf(
                    com.coachie.app.data.model.DietaryPreference.HIGH_PROTEIN,
                    com.coachie.app.data.model.DietaryPreference.MODERATE_LOW_CARB,
                    com.coachie.app.data.model.DietaryPreference.KETOGENIC
                )) {
                    recommendations.add("High Protein diet (better for preserving muscle during weight loss)")
                    recommendations.add("Moderate Low-Carb diet (helps control appetite and blood sugar)")
                }
            }
            "gain_weight" -> {
                // For weight gain, recommend diets that support muscle building
                if (currentDiet != com.coachie.app.data.model.DietaryPreference.HIGH_PROTEIN) {
                    recommendations.add("High Protein diet (essential for muscle building)")
                }
                if (currentDiet in listOf(
                    com.coachie.app.data.model.DietaryPreference.KETOGENIC,
                    com.coachie.app.data.model.DietaryPreference.VERY_LOW_CARB
                )) {
                    recommendations.add("Balanced or High Protein diet (you need more carbs for muscle gain)")
                }
            }
            "maintain_weight" -> {
                // For maintenance, balanced approaches work well
                if (currentDiet in listOf(
                    com.coachie.app.data.model.DietaryPreference.KETOGENIC,
                    com.coachie.app.data.model.DietaryPreference.VERY_LOW_CARB
                )) {
                    recommendations.add("Balanced diet (more sustainable for long-term maintenance)")
                }
            }
        }
        
        // If macros are way off, suggest more structured approach
        if (proteinOff && carbsOff && fatOff) {
            recommendations.add("Consider a more structured diet plan to ensure you hit your macro targets")
        }
        
        // Default recommendation if none found
        if (recommendations.isEmpty()) {
            recommendations.add("Focus on hitting your macro targets consistently - that's what matters for your goals")
        }
        
        return recommendations.joinToString(". ")
    }

    /**
     * Fallback insight when AI generation fails
     */
    private fun getFallbackInsight(allLogs: List<HealthLog>, profile: UserProfile, dailyScore: Int): String {
        val patterns = analyzePatterns(allLogs, profile, emptyList())
        val name = profile.name.takeIf { it.isNotBlank() } ?: "there"

        return when {
            dailyScore >= 70 -> {
                "Great job today, $name! You're on fire with a ${dailyScore}/100 score 🔥"
            }
            dailyScore >= 40 -> {
                "Nice progress, $name! You're at ${dailyScore}/100 - keep it up! 💪"
            }
            patterns.hasLowProtein && patterns.hasLowSleep -> {
                "Hey $name! Add eggs to breakfast + sleep by 10 PM 💪"
            }
            patterns.hasHighWorkout && patterns.hasLowWater -> {
                "Hydrate, $name! You burned ${patterns.totalCaloriesBurned} cal today 🚰"
            }
            patterns.hasMultipleHabits -> {
                "You're crushing it, $name! Keep the streak alive 🔥"
            }
            patterns.habitsLogged == 0 -> {
                "Start logging your habits today, $name! Every entry counts 📝"
            }
            else -> {
                "Keep up the great work, $name! Consistency is key 💪"
            }
        }
    }

    /**
     * Data class for health patterns analysis
     */
    private data class HealthPatterns(
        val totalProtein: Int,
        val totalCaloriesBurned: Int,
        val totalWater: Int,
        val sleepHours: Double,
        val avgHeartRate: Float,
        val habitsLogged: Int,
        val quickInsight: String?,
        val hasLowProtein: Boolean,
        val hasLowSleep: Boolean,
        val hasHighWorkout: Boolean,
        val hasLowWater: Boolean,
        val hasLowHeartRate: Boolean,
        val hasMultipleHabits: Boolean
    )

    companion object {
        @Volatile
        private var instance: SmartCoachEngine? = null

        fun getInstance(context: Context? = null): SmartCoachEngine {
            return instance ?: synchronized(this) {
                instance ?: SmartCoachEngine(context).also { instance = it }
            }
        }
    }
}
