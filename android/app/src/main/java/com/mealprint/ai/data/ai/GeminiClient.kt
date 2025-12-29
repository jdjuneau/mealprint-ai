package com.coachie.app.data.ai

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import android.content.Context
import com.mealprint.ai.data.Secrets
import com.mealprint.ai.data.SubscriptionService
import com.mealprint.ai.data.model.DailyLog
import com.mealprint.ai.data.model.MacroSnapshot
import com.mealprint.ai.data.model.MealRecommendation
import com.mealprint.ai.data.model.MealRecommendationRequest
import com.mealprint.ai.data.model.SubscriptionTier
import com.mealprint.ai.data.model.UserProfile
import com.mealprint.ai.data.ai.CoachPrompts
import com.mealprint.ai.domain.MacroTargetsCalculator
import com.mealprint.ai.data.api.USDANutritionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONException

/**
 * AI client for generating personalized coaching messages.
 * Routes free users to Gemini Flash (5x cheaper), Pro users to OpenAI (premium quality).
 */
class GeminiClient(private val context: Context? = null) {

    // Simple in-memory cache for chat responses (last 20 conversations)
    private val chatCache = mutableMapOf<String, Pair<String, Long>>()
    private val MAX_CACHE_SIZE = 20
    private val CACHE_EXPIRY_MS = 60 * 60 * 1000L // 1 hour

    // Initialize OpenAI client lazily to ensure Secrets is initialized first
    private val openAI: OpenAI by lazy {
        val apiKey = try {
            Secrets.getOpenAIApiKey()
        } catch (e: Exception) {
            android.util.Log.e("GeminiClient", "Failed to get OpenAI API key during initialization", e)
            throw IllegalStateException("OpenAI API key not available", e)
        }

        OpenAI(token = apiKey)
    }

    // Initialize Gemini Flash client lazily
    private val geminiFlashClient: GeminiFlashClient? by lazy {
        context?.let { GeminiFlashClient(it) }
    }

    private val subscriptionService = SubscriptionService()

    /**
     * Generate a response to a general chat message from the user.
     * Provides helpful, contextual responses to fitness-related questions.
     *
     * @param prompt The user's message/question
     * @param userId User ID for subscription tier check (optional, defaults to free tier)
     * @return AI-generated response as a string
     */
    suspend fun generateResponse(prompt: String, userId: String = ""): String {
        return withContext(Dispatchers.IO) {
            try {
                // Clean expired cache entries
                val now = System.currentTimeMillis()
                chatCache.entries.removeIf { (_, value) -> now - value.second > CACHE_EXPIRY_MS }

                // Generate cache key from prompt (simple hash)
                val cacheKey = prompt.hashCode().toString()

                // Check cache first
                chatCache[cacheKey]?.let { (cachedResponse, timestamp) ->
                    if (now - timestamp < CACHE_EXPIRY_MS) {
                        android.util.Log.d("GeminiClient", "Using cached chat response")
                        return@withContext cachedResponse
                    }
                }

                // Route based on subscription tier
                val tier = if (userId.isNotEmpty() && context != null) {
                    subscriptionService.getUserTier(userId)
                } else {
                    SubscriptionTier.FREE // Default to free if no userId or context
                }

                val finalResponse = if (tier == SubscriptionTier.PRO) {
                    // Pro users: Use OpenAI
                    if (!Secrets.hasOpenAIApiKey()) {
                        android.util.Log.e("GeminiClient", "OpenAI API key not available")
                        return@withContext "I'm sorry, but I'm not properly configured right now. Please check with the app developer."
                    }

                    val chatCompletionRequest = ChatCompletionRequest(
                        model = ModelId("gpt-3.5-turbo"),
                        messages = listOf(
                            ChatMessage(
                                role = ChatRole.System,
                                content = "You are Coachie, a friendly and helpful AI fitness coach. Provide helpful, accurate responses to fitness and health-related questions. Keep responses concise and actionable. Limit responses to 100 words or less."
                            ),
                            ChatMessage(
                                role = ChatRole.User,
                                content = prompt
                            )
                        ),
                        temperature = 0.7,
                        maxTokens = 120
                    )

                    val response = openAI.chatCompletion(chatCompletionRequest)
                    val text = response.choices.firstOrNull()?.message?.content
                    
                    // Log OpenAI usage for PRO users
                    if (userId.isNotEmpty() && context != null) {
                        try {
                            val usage = response.usage
                            val promptTokens = usage?.promptTokens ?: (prompt.length / 4)
                            val completionTokens = usage?.completionTokens ?: ((text?.length ?: 0) / 4)
                            val totalTokens = usage?.totalTokens ?: (promptTokens + completionTokens)
                            
                            val dateStr = java.time.LocalDate.now().toString()
                            val estimatedCostUsd = (totalTokens * 0.000002).toDouble() // gpt-3.5-turbo conservative estimate
                            
                            val event = hashMapOf(
                                "userId" to userId,
                                "timestamp" to System.currentTimeMillis(),
                                "source" to "chat",
                                "model" to "gpt-3.5-turbo",
                                "promptTokens" to promptTokens,
                                "completionTokens" to completionTokens,
                                "totalTokens" to totalTokens,
                                "estimatedCostUsd" to estimatedCostUsd
                            )
                            
                            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            db.collection("logs").document(userId)
                                .collection("daily").document(dateStr)
                                .collection("ai_usage").add(event)
                                .addOnSuccessListener {
                                    android.util.Log.d("GeminiClient", "✅ Logged OpenAI chat usage: $totalTokens tokens")
                                }
                                .addOnFailureListener { e ->
                                    android.util.Log.e("GeminiClient", "❌ Failed to log OpenAI chat usage: ${e.message}")
                                }
                        } catch (e: Exception) {
                            android.util.Log.e("GeminiClient", "Error logging OpenAI usage", e)
                        }
                    }
                    
                    text?.takeIf { it.isNotBlank() && it.length > 5 }
                        ?: "I'm sorry, I'm having trouble understanding that right now. Can you try rephrasing your question?"
                } else {
                    // Free users: Use Gemini Flash
                    android.util.Log.d("GeminiClient", "Using Gemini Flash for free tier user")
                    val client = geminiFlashClient
                    if (context == null || client == null) {
                        return@withContext "I'm sorry, but I'm not properly configured right now. Please check with the app developer."
                    }
                    val result = client.generateText(
                        prompt = prompt,
                        systemPrompt = "You are Coachie, a friendly and helpful AI fitness coach. Provide helpful, accurate responses to fitness and health-related questions. Keep responses concise and actionable. Limit responses to 100 words or less.",
                        temperature = 0.7,
                        maxTokens = 120,
                        userId = userId.takeIf { it.isNotEmpty() },
                        source = "chat"
                    )
                    result.getOrElse {
                        "I'm sorry, I'm having trouble understanding that right now. Can you try rephrasing your question?"
                    }
                }

                // Cache the response
                chatCache[cacheKey] = Pair(finalResponse, now)

                // Maintain cache size limit
                if (chatCache.size > MAX_CACHE_SIZE) {
                    chatCache.remove(chatCache.keys.first())
                }

                finalResponse

            } catch (e: Exception) {
                android.util.Log.e("GeminiClient", "Error generating response: ${e.javaClass.simpleName}", e)
                android.util.Log.e("GeminiClient", "Error message: ${e.message}")
                android.util.Log.e("GeminiClient", "Error cause: ${e.cause?.message}")

                // Provide more specific error messages based on exception type
                when {
                    e is IllegalStateException && e.message?.contains("API key", ignoreCase = true) == true -> {
                        "I'm sorry, but I'm not properly configured right now. Please check with the app developer."
                    }
                    e.message?.contains("API_KEY", ignoreCase = true) == true -> {
                        "I'm sorry, but I'm not properly configured right now. Please check with the app developer."
                    }
                    e.message?.contains("not found", ignoreCase = true) == true ||
                    e.message?.contains("not supported", ignoreCase = true) == true -> {
                        "I'm sorry, but there's a configuration issue. Please try again later or contact support."
                    }
                    e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true -> {
                        "I'm sorry, but I don't have permission to access the AI service right now."
                    }
                    e.message?.contains("QUOTA", ignoreCase = true) == true -> {
                        "I'm sorry, but I've reached my usage limit. Please try again later."
                    }
                    e.message?.contains("NETWORK", ignoreCase = true) == true ||
                    e.cause is java.net.UnknownHostException -> {
                        "I'm sorry, I'm having trouble connecting to the internet. Please check your connection and try again."
                    }
                    else -> {
                        "I'm sorry, I'm having trouble connecting right now. Please try again later. (Error: ${e.javaClass.simpleName})"
                    }
                }
            }
        }
    }

    /**
     * Generate a personalized coaching nudge based on user's profile and today's log.
     * Provides motivational, actionable advice tailored to their fitness journey.
     *
     * @param profile User's fitness profile with goals and preferences
     * @param todayLog Today's activity log (weight, water, mood, etc.)
     * @param sleepHours Optional sleep duration in hours for last night
     * @return Personalized coaching message as a string
     */
    suspend fun getCoachNudge(profile: UserProfile, todayLog: DailyLog, sleepHours: Double? = null): String {
        return withContext(Dispatchers.IO) {
            try {
                // Check sleep first - if sleep is low or high, return specific nudge
                sleepHours?.let { hours ->
                    if (hours < 6.0) {
                        val tip = CoachPrompts.getRandomTipForPrompt(CoachPrompts.SLEEP_LOW)
                        return@withContext CoachPrompts.fillSleepPrompt(CoachPrompts.SLEEP_LOW, tip)
                    } else if (hours > 9.0) {
                        val tip = CoachPrompts.getRandomTipForPrompt(CoachPrompts.SLEEP_EXCELLENT)
                        return@withContext CoachPrompts.fillSleepPrompt(CoachPrompts.SLEEP_EXCELLENT, tip)
                    }
                }

                val prompt = buildCoachPrompt(profile, todayLog, sleepHours)

                // Create chat completion request for coaching nudge
                val chatCompletionRequest = ChatCompletionRequest(
                    model = ModelId("gpt-3.5-turbo"),
                    messages = listOf(
                        ChatMessage(
                            role = ChatRole.System,
                            content = "You are Coachie, a friendly and encouraging AI fitness coach. Provide personalized, motivational coaching messages based on user data. Keep responses concise (2-4 sentences), encouraging, and actionable."
                        ),
                        ChatMessage(
                            role = ChatRole.User,
                            content = prompt
                        )
                    ),
                    temperature = 0.7,
                    maxTokens = 150
                )

                val response = openAI.chatCompletion(chatCompletionRequest)
                val text = response.choices.firstOrNull()?.message?.content
                
                // Log OpenAI usage for coach nudge
                try {
                    val usage = response.usage
                    val promptTokens = usage?.promptTokens ?: (prompt.length / 4)
                    val completionTokens = usage?.completionTokens ?: ((text?.length ?: 0) / 4)
                    val totalTokens = usage?.totalTokens ?: (promptTokens + completionTokens)
                    
                    val dateStr = java.time.LocalDate.now().toString()
                    val estimatedCostUsd = (totalTokens * 0.000002).toDouble() // gpt-3.5-turbo conservative estimate
                    
                    val event = hashMapOf(
                        "userId" to (profile.uid.takeIf { it.isNotBlank() } ?: ""),
                        "timestamp" to System.currentTimeMillis(),
                        "source" to "coachNudge",
                        "model" to "gpt-3.5-turbo",
                        "promptTokens" to promptTokens,
                        "completionTokens" to completionTokens,
                        "totalTokens" to totalTokens,
                        "estimatedCostUsd" to estimatedCostUsd
                    )
                    
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val userId = profile.uid.takeIf { it.isNotBlank() } ?: ""
                    if (userId.isNotEmpty()) {
                        db.collection("logs").document(userId)
                            .collection("daily").document(dateStr)
                            .collection("ai_usage").add(event)
                            .addOnSuccessListener {
                                android.util.Log.d("GeminiClient", "✅ Logged coach nudge usage: $totalTokens tokens")
                            }
                            .addOnFailureListener { e ->
                                android.util.Log.e("GeminiClient", "❌ Failed to log coach nudge usage: ${e.message}")
                            }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("GeminiClient", "Error logging coach nudge usage", e)
                }

                // Ensure we have meaningful content, fallback to generic message if needed
                text?.takeIf { it.isNotBlank() && it.length > 10 }
                    ?: getFallbackMessage(profile, todayLog, sleepHours)

            } catch (e: Exception) {
                // Log error and return fallback message
                android.util.Log.e("GeminiClient", "Error generating coach nudge", e)
                getFallbackMessage(profile, todayLog, sleepHours)
            }
        }
    }

    /**
     * Build a comprehensive prompt for the AI coach based on user data.
     */
    private fun buildCoachPrompt(profile: UserProfile, todayLog: DailyLog, sleepHours: Double? = null): String {
        val macroTargets = MacroTargetsCalculator.calculate(profile)
        return """
            You are Coachie, a friendly and encouraging AI fitness coach. Provide a personalized, motivational coaching message based on this user's profile and today's activity.

            USER PROFILE:
            - Name: ${profile.name}
            - Height: ${profile.heightCm}cm
            - Current Weight: ${profile.currentWeight}kg
            - Goal Weight: ${profile.goalWeight}kg
            - Weight Difference: ${profile.weightDifference}kg ${if (profile.weightDifference > 0) "to lose" else "to gain"}
            - Activity Level: ${profile.activityLevel.replace("_", " ")}
            - BMI: ${String.format("%.1f", profile.bmi)} (${profile.bmiCategory})
            - Daily Calorie Target: ${macroTargets.calorieGoal} kcal
            - Macro Targets: Protein ${macroTargets.proteinGrams}g, Carbs ${macroTargets.carbsGrams}g, Fat ${macroTargets.fatGrams}g
            - Dietary Preference: ${profile.dietaryPreferenceEnum.title} (${profile.dietaryPreferenceEnum.summary})
            - Progress: ${String.format("%.1f", profile.progressPercentage * 100)}% towards goal

            TODAY'S LOG:
            - Weight: ${todayLog.weight?.let { weight: Double -> "${weight}kg" } ?: "Not logged"}
            - Steps: ${todayLog.steps?.let { steps: Int -> "$steps steps (${(todayLog.stepGoalProgress * 100).toInt()}% of 10k goal)" } ?: "Not logged"}
            - Water: ${todayLog.water?.let { water: Int -> "${water}ml (${todayLog.waterInLiters}L)" } ?: "Not logged"}
            - Mood: ${todayLog.moodDescription ?: "Not logged"}
            - Sleep: ${sleepHours?.let { hours: Double -> String.format("%.1f hours", hours) } ?: "Not logged"}
            - Notes: ${todayLog.notes ?: "None"}

            INSTRUCTIONS:
            - Keep the message concise (2-4 sentences)
            - Be encouraging and positive
            - Provide specific, actionable advice
            - Reference their actual data and progress
            - Keep food or supplement suggestions aligned with their dietary preference
            - Focus on one key area for improvement or celebration
            - End with motivation or next steps
            - Use their name occasionally for personalization
            - Adapt tone based on their mood and progress
            - If sleep is mentioned (< 6h or > 9h), acknowledge it appropriately

            Generate a coaching message that feels personal and helpful:
        """.trimIndent()
    }

    /**
     * Provide a fallback message when AI generation fails.
     */
    private fun getFallbackMessage(profile: UserProfile, todayLog: DailyLog, sleepHours: Double? = null): String {
        val name = profile.name.takeIf { it.isNotBlank() } ?: "there"

        // Check sleep first
        sleepHours?.let { hours ->
            if (hours < 6.0) {
                val tip = CoachPrompts.getRandomTipForPrompt(CoachPrompts.SLEEP_LOW)
                return CoachPrompts.fillSleepPrompt(CoachPrompts.SLEEP_LOW, tip)
            } else if (hours > 9.0) {
                val tip = CoachPrompts.getRandomTipForPrompt(CoachPrompts.SLEEP_EXCELLENT)
                return CoachPrompts.fillSleepPrompt(CoachPrompts.SLEEP_EXCELLENT, tip)
            }
        }

        return when {
            profile.hasReachedGoal -> {
                "🎉 Amazing work, $name! You've reached your goal! Keep up the great habits that got you here."
            }

            todayLog.hasData -> {
                "💪 Great job logging your activity today, $name! Every step counts toward your goals. Keep it up!"
            }

            profile.weightDifference > 0 -> {
                "👋 Hi $name! Remember, consistency is key. Try logging your weight and water intake today to track your progress."
            }

            else -> {
                "🏃‍♀️ Hey $name! Ready to crush your fitness goals today? Start with something small and build from there!"
            }
        }
    }

    /**
     * Analyze a food image using OpenAI Vision API (not yet implemented).
     * Extracts food name, estimated calories, and macronutrients.
     *
     * @param imageBitmap The food photo to analyze
     * @return Parsed food information or null if analysis fails
     */
    suspend fun analyzeFoodImage(imageBitmap: android.graphics.Bitmap): Result<FoodAnalysis> {
        return Result.failure(
            Exception("Image analysis is temporarily unavailable while switching to OpenAI. Please use manual entry for now.")
        )
    }
    

    companion object {
        // Singleton pattern for efficient resource usage
        @Volatile
        private var instance: GeminiClient? = null

        fun getInstance(context: Context? = null): GeminiClient {
            return instance ?: synchronized(this) {
                instance ?: GeminiClient(context).also { instance = it }
            }
        }
    }

    suspend fun generateMealRecommendation(request: MealRecommendationRequest, useImperial: Boolean = true, userId: String = ""): Result<MealRecommendation> {
        return withContext(Dispatchers.IO) {
            try {
                // Route based on subscription tier
                val tier = if (userId.isNotEmpty() && context != null) {
                    subscriptionService.getUserTier(userId)
                } else {
                    SubscriptionTier.FREE // Default to free if no userId or context
                }

                val prompt = buildMealPrompt(request, useImperial)
                val text = if (tier == SubscriptionTier.PRO) {
                    // Pro users: Use OpenAI
                    if (!Secrets.hasOpenAIApiKey()) {
                        return@withContext Result.failure(IllegalStateException("OpenAI API key is missing"))
                    }

                    val chatCompletionRequest = ChatCompletionRequest(
                        model = ModelId("gpt-3.5-turbo"),
                        messages = listOf(
                            ChatMessage(
                                role = ChatRole.System,
                                content = "You are Coachie, a certified nutrition coach and recipe developer. Create practical, easy-to-cook meals using the ingredients on hand. Always ensure suggestions align with the dietary preference and macro goals. Respond in JSON only."
                            ),
                            ChatMessage(
                                role = ChatRole.User,
                                content = prompt
                            )
                        ),
                        temperature = 0.6,
                        maxTokens = 2000 // Increased for complete recipes
                    )

                    val response = openAI.chatCompletion(chatCompletionRequest)
                    val text = response.choices.firstOrNull()?.message?.content
                    
                    // Log OpenAI usage for meal recommendation (PRO users)
                    if (userId.isNotEmpty() && context != null) {
                        try {
                            val usage = response.usage
                            val promptTokens = usage?.promptTokens ?: (prompt.length / 4)
                            val completionTokens = usage?.completionTokens ?: ((text?.length ?: 0) / 4)
                            val totalTokens = usage?.totalTokens ?: (promptTokens + completionTokens)
                            
                            val dateStr = java.time.LocalDate.now().toString()
                            val estimatedCostUsd = (totalTokens * 0.000002).toDouble() // gpt-3.5-turbo conservative estimate
                            
                            val event = hashMapOf(
                                "userId" to userId,
                                "timestamp" to System.currentTimeMillis(),
                                "source" to "meal_recommendation",
                                "model" to "gpt-3.5-turbo",
                                "promptTokens" to promptTokens,
                                "completionTokens" to completionTokens,
                                "totalTokens" to totalTokens,
                                "estimatedCostUsd" to estimatedCostUsd
                            )
                            
                            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            db.collection("logs").document(userId)
                                .collection("daily").document(dateStr)
                                .collection("ai_usage").add(event)
                                .addOnSuccessListener {
                                    android.util.Log.d("GeminiClient", "✅ Logged meal recommendation usage: $totalTokens tokens")
                                }
                                .addOnFailureListener { e ->
                                    android.util.Log.e("GeminiClient", "❌ Failed to log meal recommendation usage: ${e.message}")
                                }
                        } catch (e: Exception) {
                            android.util.Log.e("GeminiClient", "Error logging meal recommendation usage", e)
                        }
                    }
                    
                    text ?: return@withContext Result.failure(IllegalStateException("Empty response from AI"))
                } else {
                    // Free users: Use OpenAI (same as Pro) for reliable recipe generation
                    // Gemini Flash was consistently failing to return complete recipes
                    android.util.Log.d("GeminiClient", "Using OpenAI for free tier meal recommendation (Gemini Flash unreliable)")
                    if (!Secrets.hasOpenAIApiKey()) {
                        return@withContext Result.failure(IllegalStateException("OpenAI API key is missing"))
                    }

                    val chatCompletionRequest = ChatCompletionRequest(
                        model = ModelId("gpt-3.5-turbo"),
                        messages = listOf(
                            ChatMessage(
                                role = ChatRole.System,
                                content = "You are Coachie, a certified nutrition coach and recipe developer. Create practical, easy-to-cook meals using the ingredients on hand. Always ensure suggestions align with the dietary preference and macro goals. Respond in JSON only."
                            ),
                            ChatMessage(
                                role = ChatRole.User,
                                content = prompt
                            )
                        ),
                        temperature = 0.6,
                        maxTokens = 2000 // Increased for complete recipes
                    )

                    val response = openAI.chatCompletion(chatCompletionRequest)
                    val text = response.choices.firstOrNull()?.message?.content
                    
                    // Log OpenAI usage for meal recommendation (FREE users now using OpenAI)
                    if (userId.isNotEmpty() && context != null) {
                        try {
                            val usage = response.usage
                            val promptTokens = usage?.promptTokens ?: (prompt.length / 4)
                            val completionTokens = usage?.completionTokens ?: ((text?.length ?: 0) / 4)
                            val totalTokens = usage?.totalTokens ?: (promptTokens + completionTokens)
                            
                            val dateStr = java.time.LocalDate.now().toString()
                            val estimatedCostUsd = (totalTokens * 0.000002).toDouble() // gpt-3.5-turbo conservative estimate
                            
                            val event = hashMapOf(
                                "userId" to userId,
                                "timestamp" to System.currentTimeMillis(),
                                "source" to "meal_recommendation",
                                "model" to "gpt-3.5-turbo",
                                "promptTokens" to promptTokens,
                                "completionTokens" to completionTokens,
                                "totalTokens" to totalTokens,
                                "estimatedCostUsd" to estimatedCostUsd,
                                "tier" to "free" // Mark as free tier usage
                            )
                            
                            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            db.collection("logs").document(userId)
                                .collection("daily").document(dateStr)
                                .collection("ai_usage").add(event)
                                .addOnSuccessListener {
                                    android.util.Log.d("GeminiClient", "✅ Logged meal recommendation usage (free tier): $totalTokens tokens")
                                }
                                .addOnFailureListener { e ->
                                    android.util.Log.e("GeminiClient", "❌ Failed to log meal recommendation usage: ${e.message}")
                                }
                        } catch (e: Exception) {
                            android.util.Log.e("GeminiClient", "Error logging meal recommendation usage", e)
                        }
                    }
                    
                    text ?: return@withContext Result.failure(IllegalStateException("Empty response from OpenAI"))
                }

                // Parse GPT response to get recipe structure
                val recommendation = parseMealRecommendation(text)
                
                // STEP 2: Look up nutrition from USDA API for each ingredient
                android.util.Log.d("GeminiClient", "🔍 Looking up nutrition from USDA API for ${recommendation.ingredients.size} ingredients...")
                val nutritionResult = calculateNutritionFromUSDA(recommendation.ingredients, recommendation.servings)
                
                // Use USDA-calculated macros if we got valid data, otherwise fall back to GPT's estimates
                val finalMacros = if (nutritionResult.first.calories > 0 || nutritionResult.first.protein > 0) {
                    android.util.Log.d("GeminiClient", "✅ Using USDA-calculated macros: ${nutritionResult.first.calories} cal, ${nutritionResult.first.protein}g P")
                    nutritionResult.first
                } else {
                    android.util.Log.w("GeminiClient", "⚠️ USDA calculation returned zeros, using GPT's estimated macros: ${recommendation.macrosPerServing.calories} cal, ${recommendation.macrosPerServing.protein}g P")
                    recommendation.macrosPerServing
                }
                
                // Replace GPT's macros with USDA-calculated macros and add micronutrients
                val updatedRecommendation = recommendation.copy(
                    macrosPerServing = finalMacros,
                    micronutrients = nutritionResult.second
                )
                
                android.util.Log.d("GeminiClient", "✅ Final macros: ${finalMacros.calories} cal, ${finalMacros.protein}g P, ${finalMacros.carbs}g C, ${finalMacros.fat}g F per serving")
                android.util.Log.d("GeminiClient", "✅ Micronutrients: ${nutritionResult.second.size} types calculated")
                
                Result.success(updatedRecommendation)
            } catch (e: Exception) {
                android.util.Log.e("GeminiClient", "Error generating meal recommendation", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Calculate nutrition from USDA API for recipe ingredients
     * Returns per-serving macros and micronutrients
     */
    private suspend fun calculateNutritionFromUSDA(
        ingredients: List<String>,
        servings: Int
    ): Pair<com.coachie.app.data.model.MacroSnapshot, Map<com.coachie.app.data.model.MicronutrientType, Double>> {
        var totalCalories = 0
        var totalProtein = 0.0
        var totalCarbs = 0.0
        var totalFat = 0.0
        var totalSugar = 0.0
        var totalAddedSugar = 0.0
        val totalMicronutrients = mutableMapOf<com.coachie.app.data.model.MicronutrientType, Double>()
        
        android.util.Log.d("GeminiClient", "📊 Calculating nutrition for ${ingredients.size} ingredients (${servings} servings)...")
        
        for (ingredient in ingredients) {
            try {
                val result = USDANutritionService.lookupIngredient(ingredient)
                val nutrition = result.getOrNull()
                
                if (nutrition != null) {
                    totalCalories += nutrition.calories
                    totalProtein += nutrition.protein
                    totalCarbs += nutrition.carbs
                    totalFat += nutrition.fat
                    totalSugar += nutrition.sugar
                    totalAddedSugar += nutrition.addedSugar
                    
                    // Sum micronutrients
                    nutrition.micronutrients.forEach { (type, value) ->
                        totalMicronutrients[type] = (totalMicronutrients[type] ?: 0.0) + value
                    }
                    
                    android.util.Log.d("GeminiClient", "  ✅ $ingredient: ${nutrition.calories} cal, ${nutrition.protein}g P, ${nutrition.carbs}g C, ${nutrition.fat}g F, ${nutrition.micronutrients.size} micros")
                } else {
                    val error = result.exceptionOrNull()
                    android.util.Log.w("GeminiClient", "  ⚠️ Failed to lookup $ingredient from USDA: ${error?.message ?: "Unknown error"}")
                    // Continue with other ingredients - don't fail completely
                }
            } catch (e: Exception) {
                android.util.Log.w("GeminiClient", "  ⚠️ Error looking up $ingredient: ${e.message}")
            }
        }
        
        // Calculate per-serving values
        val scaleFactor = if (servings > 0) 1.0 / servings else 1.0
        val perServingCalories = (totalCalories * scaleFactor).toInt()
        val perServingProtein = (totalProtein * scaleFactor).toInt()
        val perServingCarbs = (totalCarbs * scaleFactor).toInt()
        val perServingFat = (totalFat * scaleFactor).toInt()
        val perServingSugar = (totalSugar * scaleFactor).toInt()
        val perServingAddedSugar = (totalAddedSugar * scaleFactor).toInt()
        
        // Calculate per-serving micronutrients
        val perServingMicronutrients = totalMicronutrients.mapValues { (_, value) -> value * scaleFactor }
        
        android.util.Log.d("GeminiClient", "📊 Total recipe: ${totalCalories} cal, ${totalProtein}g P, ${totalCarbs}g C, ${totalFat}g F (${servings} servings)")
        android.util.Log.d("GeminiClient", "📊 Per serving: $perServingCalories cal, ${perServingProtein}g P, ${perServingCarbs}g C, ${perServingFat}g F")
        android.util.Log.d("GeminiClient", "📊 Micronutrients per serving: ${perServingMicronutrients.size} types")
        
        val macros = com.coachie.app.data.model.MacroSnapshot(
            calories = perServingCalories,
            protein = perServingProtein,
            carbs = perServingCarbs,
            fat = perServingFat,
            sugar = perServingSugar,
            addedSugar = perServingAddedSugar
        )
        
        return Pair(macros, perServingMicronutrients)
    }

    private fun buildMealPrompt(request: MealRecommendationRequest, useImperial: Boolean = true): String {
        val ingredientList = if (request.selectedIngredients.isEmpty()) {
            "None provided."
        } else {
            request.selectedIngredients.joinToString()
        }

        val unitInstructions = if (useImperial) {
            """
            CRITICAL: Use IMPERIAL units for ALL ingredient quantities:
            - Weights: lbs or oz (e.g., "1.5 lbs chicken breast", "8 oz cheese")
            - Volumes: cups, fl oz, tbsp, tsp (e.g., "2 cups milk", "1 tbsp olive oil", "1 tsp salt")
            - NEVER use metric units (g, kg, ml, L) in ingredient quantities
            - Example format: "1.5 lbs chicken breast", "2 cups broccoli", "1 tbsp butter", "8 oz cheddar cheese"
            """
        } else {
            """
            Use METRIC units for ALL ingredient quantities:
            - Weights: g or kg (e.g., "700g chicken breast", "250g cheese")
            - Volumes: ml or L (e.g., "500ml milk", "15ml olive oil")
            - Example format: "700g chicken breast", "500ml milk", "15ml olive oil"
            """
        }

        val cookingMethodText = if (request.cookingMethod != null && request.cookingMethod.isNotBlank()) {
            val methodName = when (request.cookingMethod.lowercase()) {
                "bbq" -> "BBQ (low and slow barbecue smoking)"
                "sous_vide" -> "Sous Vide (precision temperature cooking)"
                "pressure_cook" -> "Pressure Cook (pressure cooker/Instant Pot)"
                "smoke" -> "Smoke (hot or cold smoking)"
                "slow_cook" -> "Slow Cook (crock pot/slow cooker)"
                "grill" -> "Grill (outdoor or indoor grilling)"
                "bake" -> "Bake (oven baking)"
                "roast" -> "Roast (oven roasting)"
                "saute" -> "Sauté (pan sautéing)"
                "stir_fry" -> "Stir-Fry (wok stir-frying)"
                "braise" -> "Braise (braising in liquid)"
                "steam" -> "Steam (steaming)"
                "poach" -> "Poach (poaching in liquid)"
                "pan_sear" -> "Pan Sear (pan searing)"
                "air_fry" -> "Air Fry (air fryer cooking)"
                "raw" -> "Raw (no cooking required)"
                else -> request.cookingMethod
            }
            """
            COOKING METHOD REQUIREMENT:
            - User has selected: $methodName
            - You MUST use this cooking method for this meal
            - Include specific instructions for this cooking method in the recipe
            - If the selected ingredients don't work well with this method, adapt the recipe to make it work while still using the exact ingredients
            """
        } else {
            """
            COOKING METHOD:
            - Use any appropriate cooking method that works well with the selected ingredients
            - Consider: grilling, baking, roasting, sautéing, stir-frying, braising, slow-cooking, steaming, poaching, pan-searing, air frying
            """
        }

        return """
            Create a meal recommendation that fits the following profile:
            - Dietary preference: ${request.dietaryPreference}
            - Goal trend: ${request.goalTrend}
            - Daily calorie goal: ${request.calorieGoal}
            - Macro targets: ${request.macroTargets.proteinGrams}g protein, ${request.macroTargets.carbsGrams}g carbs, ${request.macroTargets.fatGrams}g fat
            - Current totals today: ${request.currentMacros.calories} kcal, ${request.currentMacros.protein}g protein, ${request.currentMacros.carbs}g carbs, ${request.currentMacros.fat}g fat
            - Remaining targets today: ${request.remainingMacros.calories} kcal, ${request.remainingMacros.protein}g protein, ${request.remainingMacros.carbs}g carbs, ${request.remainingMacros.fat}g fat
            - Available ingredients: $ingredientList

            $cookingMethodText

            ${if (request.mealType != null) {
                val mealTypeName = when (request.mealType) {
                    "breakfast" -> "Breakfast"
                    "brunch" -> "Brunch"
                    "lunch" -> "Lunch"
                    "dinner" -> "Dinner"
                    "dessert" -> "Dessert"
                    else -> request.mealType
                }
                """
                MEAL TYPE:
                - This meal is for: $mealTypeName
                - The recipe should be appropriate for this meal type
                - Consider typical foods, portion sizes, and nutritional profiles for $mealTypeName meals
                """
            } else {
                """
                MEAL TYPE:
                - No specific meal type selected - create an appropriate meal for any time of day
                """
            }}

            ${if (request.cyclePhase != null) {
                val cycleGuidance = when (request.cyclePhase) {
                    "MENSTRUAL" -> """
                    MENSTRUAL CYCLE PHASE: Menstrual (Period phase)
                    - Focus on iron-rich foods: lean red meat, spinach, lentils, beans, fortified cereals
                    - Include foods high in vitamin C to enhance iron absorption: bell peppers, citrus fruits, tomatoes
                    - Consider gentle, warming foods that are easy to digest
                    - Include magnesium-rich foods to help with cramps: dark leafy greens, nuts, seeds, whole grains
                    - Hydration is important - include hydrating ingredients
                    """
                    "FOLLICULAR" -> """
                    MENSTRUAL CYCLE PHASE: Follicular (Post-period phase)
                    - Energy-building phase - include complex carbs for sustained energy: whole grains, sweet potatoes, quinoa
                    - Good for strength training support - emphasize protein-rich ingredients
                    - Include B vitamins for energy metabolism: whole grains, eggs, leafy greens
                    - Fresh, vibrant ingredients work well during this phase
                    """
                    "OVULATION" -> """
                    MENSTRUAL CYCLE PHASE: Ovulation (Fertile phase - peak energy)
                    - Peak energy phase - optimal for intense workouts
                    - Include high-quality proteins for muscle support
                    - Complex carbs for sustained energy throughout the day
                    - Antioxidant-rich foods: berries, dark leafy greens, colorful vegetables
                    - This is a great time for nutrient-dense, energizing meals
                    """
                    "LUTEAL" -> """
                    MENSTRUAL CYCLE PHASE: Luteal (Pre-period phase - potential PMS symptoms)
                    - Focus on magnesium-rich foods to help with PMS symptoms: dark leafy greens, nuts, seeds, whole grains, dark chocolate
                    - Include B vitamins (especially B6) for mood support: poultry, fish, whole grains, bananas
                    - Consider foods that help with bloating: potassium-rich foods like bananas, avocados, sweet potatoes
                    - Include complex carbs to help with mood and energy stability
                    - Foods rich in omega-3s can help with inflammation: fatty fish, walnuts, chia seeds
                    """
                    else -> ""
                }
                cycleGuidance
            } else {
                ""
            }}

            🚨 CRITICAL INGREDIENT REQUIREMENTS 🚨
            - You MUST use ONLY the ingredients listed above. NO additional ingredients allowed unless explicitly listed.
            - For meat/protein ingredients: Use the EXACT type and cut specified. If "ground beef" is listed, you MUST use "ground beef" - NOT sirloin, NOT steak, NOT any other cut. If "chicken breast" is listed, use "chicken breast" - NOT thighs, NOT wings, NOT any other part.
            - For vegetables: Use the EXACT vegetables listed. If "broccoli" is listed, use "broccoli" - NOT cauliflower, NOT other vegetables.
            - For dairy/cheese: Use the EXACT type listed. If "cheddar cheese" is listed, use "cheddar cheese" - NOT mozzarella, NOT other cheeses.
            - For spices and seasonings: Use ONLY the spices/seasonings that are explicitly listed. If "ginger" is NOT in the list, do NOT use ginger. If "garlic" is NOT in the list, do NOT use garlic. If "onion powder" is NOT in the list, do NOT use onion powder. If "paprika" is NOT in the list, do NOT use paprika. NO spices, herbs, seasonings, or flavorings that are NOT explicitly in the selected ingredients list.
            - You may ONLY add these minimal cooking essentials if absolutely necessary for basic cooking: salt, pepper, and a small amount of cooking oil or butter (only if needed for cooking method and not already in the list). These are the ONLY exceptions, and use them sparingly.
            - DO NOT add any spices, herbs, seasonings, flavorings, or ingredients that are NOT in the selected ingredients list.
            - DO NOT substitute similar ingredients. "Ground beef" and "sirloin" are NOT the same. "Chicken breast" and "chicken thighs" are NOT the same. Use exactly what is listed.
            - If an ingredient is not in the list above, it should NOT appear in your recipe unless it's one of the minimal cooking essentials (salt, pepper, minimal oil/butter).
            - REMEMBER: The user selected specific ingredients for a reason. Only use what they selected. Do not add ginger, garlic, onion powder, paprika, or any other spices unless they are explicitly in the selected ingredients list.

            Provide a full recipe that uses the EXACT ingredients listed above. Respect the dietary preference strictly.

            CRITICAL: The recipe MUST be for 4 servings. All ingredient quantities and macros should be for 4 servings total.

            $unitInstructions

            Respond ONLY in the following JSON format:
            {
              "recipeTitle": "string",
              "summary": "string",
              "servings": 4,
              "prepTimeMinutes": number,
              "ingredients": ["1.5 lbs chicken breast", "2 cups broccoli", "8 oz cheddar cheese", "..."],
              "instructions": ["step 1", "..."],
              "macrosPerServing": {
                "calories": number,
                "protein": number,
                "carbs": number,
                "fat": number,
                "sugar": number,
                "addedSugar": number
              },
              "fitExplanation": "Explain briefly how this fits their goals and diet.",
              "groceryList": ["optional", "items"]
            }
            
            IMPORTANT: Each ingredient in the "ingredients" array MUST include the quantity with units (e.g., "1.5 lbs chicken breast", not just "chicken breast").
            IMPORTANT: The main protein/meat ingredient in your recipe MUST match EXACTLY what was listed in "Available ingredients" above. If "ground beef" was listed, your recipe MUST use "ground beef" with the exact quantity needed for 4 servings.
        """.trimIndent()
    }

    private fun parseMealRecommendation(jsonText: String): MealRecommendation {
        val cleaned = jsonText.trim().removePrefix("```json").removeSuffix("```").trim()
        
        // Check if JSON appears incomplete (common signs: unterminated strings, missing closing braces)
        if (cleaned.count { it == '{' } != cleaned.count { it == '}' } ||
            cleaned.count { it == '[' } != cleaned.count { it == ']' }) {
            android.util.Log.e("GeminiClient", "Incomplete JSON detected - mismatched braces/brackets")
            throw JSONException("Incomplete JSON response from AI. The response was truncated. Please try again.")
        }
        
        val json = try {
            JSONObject(cleaned)
        } catch (e: JSONException) {
            android.util.Log.e("GeminiClient", "JSON parsing error: ${e.message}")
            android.util.Log.e("GeminiClient", "JSON text (first 200 chars): ${cleaned.take(200)}")
            throw JSONException("Failed to parse JSON response from AI. The response may have been truncated. Please try again. Error: ${e.message}")
        }
        
        val macros = json.getJSONObject("macrosPerServing")
        val macroSnapshot = com.coachie.app.data.model.MacroSnapshot(
            calories = macros.optInt("calories"),
            protein = macros.optInt("protein"),
            carbs = macros.optInt("carbs"),
            fat = macros.optInt("fat"),
            sugar = macros.optInt("sugar", 0),
            addedSugar = macros.optInt("addedSugar", 0)
        )
        val ingredientList = json.optJSONArray("ingredients")?.let { array ->
            List(array.length()) { idx -> array.optString(idx) }
        } ?: emptyList()
        val instructions = json.optJSONArray("instructions")?.let { array ->
            List(array.length()) { idx -> array.optString(idx) }
        } ?: emptyList()
        val grocery = json.optJSONArray("groceryList")?.let { array ->
            List(array.length()) { idx -> array.optString(idx) }
        } ?: emptyList()

        return MealRecommendation(
            recipeTitle = json.optString("recipeTitle", "Coachie Meal Idea"),
            summary = json.optString("summary"),
            ingredients = ingredientList,
            instructions = instructions,
            macrosPerServing = macroSnapshot, // Will be replaced by USDA calculation
            servings = json.optInt("servings", 1),
            prepTimeMinutes = json.optInt("prepTimeMinutes", 20),
            fitExplanation = json.optString("fitExplanation"),
            groceryList = grocery,
            micronutrients = emptyMap() // Will be populated by USDA calculation
        )
    }
}

/**
 * Data class for food analysis results
 */
data class FoodAnalysis(
    val foodName: String,
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int
) {
    /**
     * Get formatted summary string
     */
    val summary: String
        get() = "Detected: $foodName, ~$calories cal, ${protein}g protein"
}
