package com.coachie.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealprint.ai.data.HabitRepository
import com.mealprint.ai.data.SubscriptionService
import com.mealprint.ai.data.ai.SmartCoachEngine
import com.mealprint.ai.data.model.*
import com.mealprint.ai.util.await
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class HabitSuggestionsUiState(
    val isLoading: Boolean = false,
    val suggestions: List<HabitSuggestion> = emptyList(),
    val selectedSuggestion: HabitSuggestion? = null,
    val showSuggestionDetails: Boolean = false,
    val error: String? = null,
    val behavioralProfile: UserHabitProfile? = null
)

data class HabitSuggestion(
    val id: String = "",
    val name: String,
    val description: String,
    val category: HabitCategory,
    val frequency: HabitFrequency,
    val priority: HabitPriority,
    val rationale: String,
    val expectedImpact: String,
    val successProbability: Int, // 0-100
    val timeCommitment: String, // e.g., "5 minutes daily"
    val difficulty: HabitDifficulty,
    val prerequisites: List<String> = emptyList(),
    val isRecommended: Boolean = false
)

enum class HabitDifficulty {
    EASY, MEDIUM, HARD
}

class HabitSuggestionsViewModel(
    private val habitRepository: HabitRepository = HabitRepository.getInstance(),
    private val smartCoachEngine: SmartCoachEngine = SmartCoachEngine.getInstance(),
    private val userId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitSuggestionsUiState())
    val uiState: StateFlow<HabitSuggestionsUiState> = _uiState.asStateFlow()

    init {
        loadBehavioralProfile()
        generateSuggestions()
    }

    private fun loadBehavioralProfile() {
        viewModelScope.launch {
            try {
                val profile = habitRepository.getUserHabitProfile(userId).getOrNull()
                _uiState.value = _uiState.value.copy(behavioralProfile = profile)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to load behavioral profile: ${e.message}")
            }
        }
    }

    fun generateSuggestions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Check subscription before generating AI suggestions
                val tier = SubscriptionService.getUserTier(userId)
                val remaining = if (tier == SubscriptionTier.FREE) {
                    SubscriptionService.getRemainingAICalls(userId, AIFeature.HABIT_SUGGESTIONS)
                } else {
                    Int.MAX_VALUE
                }

                val profile = _uiState.value.behavioralProfile
                val existingHabits = habitRepository.getUserHabits(userId).first().map { it.title }

                // Try to get AI-powered suggestions from Cloud Function first (if allowed)
                val aiSuggestions = if (tier == SubscriptionTier.FREE && remaining <= 0) {
                    // Free user hit limit - use local suggestions only
                    android.util.Log.d("HabitSuggestionsViewModel", "Free user hit limit, using local suggestions only")
                    emptyList()
                } else {
                    try {
                        val suggestions = getAIPoweredSuggestions()
                        // Record usage for free tier
                        if (tier == SubscriptionTier.FREE) {
                            SubscriptionService.recordAIFeatureUsage(userId, AIFeature.HABIT_SUGGESTIONS)
                        }
                        suggestions
                    } catch (e: Exception) {
                        android.util.Log.w("HabitSuggestionsViewModel", "Failed to get AI suggestions, falling back to local: ${e.message}")
                        emptyList()
                    }
                }

                // Generate personalized suggestions based on behavioral profile (fallback)
                val localSuggestions = generatePersonalizedSuggestions(profile, existingHabits)

                // Combine AI suggestions with local suggestions, prioritizing AI
                val allSuggestions = (aiSuggestions + localSuggestions)
                    .distinctBy { it.name }
                    .sortedByDescending { it.successProbability }

                _uiState.value = _uiState.value.copy(
                    suggestions = allSuggestions,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to generate suggestions: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    private suspend fun getAIPoweredSuggestions(): List<HabitSuggestion> {
        return try {
            val functions = FirebaseFunctions.getInstance()
            val predictHabits = functions.getHttpsCallable("predictHabits")
            
            val data = hashMapOf("userId" to userId)
            val task = predictHabits.call(data)
            val result = task.await()
            
            val resultData = result.data as? Map<*, *>
            val predictions = resultData?.get("predictions") as? Map<*, *>
            val predictedHabits = predictions?.get("predictedHabits") as? List<Map<*, *>>
            
            predictedHabits?.mapNotNull { habitData ->
                try {
                    HabitSuggestion(
                        name = habitData["title"] as? String ?: return@mapNotNull null,
                        description = habitData["description"] as? String ?: "",
                        category = parseCategory(habitData["category"] as? String),
                        frequency = HabitFrequency.DAILY, // Default, could be parsed from timing
                        priority = HabitPriority.MEDIUM,
                        rationale = habitData["rationale"] as? String ?: "",
                        expectedImpact = "Based on your behavioral patterns",
                        successProbability = (habitData["confidenceScore"] as? Number)?.toInt() ?: 70,
                        timeCommitment = parseTimeCommitment(habitData["timing"] as? String),
                        difficulty = parseDifficulty(habitData["difficulty"] as? String),
                        isRecommended = true
                    )
                } catch (e: Exception) {
                    android.util.Log.e("HabitSuggestionsViewModel", "Error parsing AI suggestion: ${e.message}")
                    null
                }
            } ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("HabitSuggestionsViewModel", "Error calling predictHabits: ${e.message}")
            throw e
        }
    }

    private fun parseCategory(categoryStr: String?): HabitCategory {
        return when (categoryStr?.uppercase()) {
            "HEALTH" -> HabitCategory.HEALTH
            "FITNESS" -> HabitCategory.FITNESS
            "NUTRITION" -> HabitCategory.NUTRITION
            "SLEEP" -> HabitCategory.SLEEP
            "HYDRATION" -> HabitCategory.HEALTH
            "PRODUCTIVITY" -> HabitCategory.PRODUCTIVITY
            "MINDFULNESS", "MENTAL_HEALTH" -> HabitCategory.MENTAL_HEALTH
            "SOCIAL" -> HabitCategory.SOCIAL
            "LEARNING" -> HabitCategory.LEARNING
            else -> HabitCategory.OTHER
        }
    }

    private fun parseDifficulty(difficultyStr: String?): HabitDifficulty {
        return when (difficultyStr?.uppercase()) {
            "VERY_EASY", "EASY" -> HabitDifficulty.EASY
            "MODERATE" -> HabitDifficulty.MEDIUM
            "CHALLENGING", "HARD" -> HabitDifficulty.HARD
            else -> HabitDifficulty.MEDIUM
        }
    }

    private fun parseTimeCommitment(timing: String?): String {
        return when (timing?.lowercase()) {
            "morning" -> "5-10 minutes in the morning"
            "afternoon" -> "5-10 minutes in the afternoon"
            "evening" -> "5-10 minutes in the evening"
            "weekend" -> "30-60 minutes weekly"
            else -> "5-15 minutes daily"
        }
    }
    
    /**
     * Parse timeCommitment string to extract targetValue and unit
     * Examples:
     * - "10 minutes daily" -> (10, "minutes")
     * - "5-10 minutes in the morning" -> (10, "minutes")
     * - "10 seconds when triggered" -> (10, "seconds")
     * - "8 glasses of water" -> (8, "glasses")
     * - "30 minutes weekly" -> (30, "minutes")
     */
    private fun parseTimeCommitment(timeCommitment: String): Pair<Int, String> {
        val lower = timeCommitment.lowercase()
        
        // Extract number and unit from timeCommitment
        // Try to find patterns like "10 minutes", "5-10 minutes", "8 glasses", "10 seconds"
        val numberPattern = Regex("""(\d+)(?:-(\d+))?""")
        val match = numberPattern.find(lower)
        
        val targetValue = if (match != null) {
            // If there's a range (e.g., "5-10"), use the higher value
            val firstNum = match.groupValues[1].toIntOrNull() ?: 1
            val secondNum = match.groupValues[2].toIntOrNull()
            secondNum ?: firstNum
        } else {
            // Default to 1 if no number found
            1
        }
        
        // Determine unit based on keywords
        val unit = when {
            lower.contains("second") -> "seconds"
            lower.contains("minute") -> "minutes"
            lower.contains("hour") -> "hours"
            lower.contains("glass") -> "glasses"
            lower.contains("cup") -> "cups"
            lower.contains("item") -> "items"
            lower.contains("time") || lower.contains("trigger") -> "times"
            else -> ""
        }
        
        return Pair(targetValue, unit)
    }

    private fun generatePersonalizedSuggestions(
        profile: UserHabitProfile?,
        existingHabits: List<String>
    ): List<HabitSuggestion> {
        val suggestions = mutableListOf<HabitSuggestion>()

        // Base suggestions that work for everyone
        val baseSuggestions = listOf(
            HabitSuggestion(
                name = "Drink 8 glasses of water",
                description = "Stay hydrated throughout the day for better energy and health",
                category = HabitCategory.HEALTH,
                frequency = HabitFrequency.DAILY,
                priority = HabitPriority.MEDIUM,
                rationale = "Proper hydration improves cognitive function, reduces fatigue, and supports overall health",
                expectedImpact = "Increased energy, clearer thinking, better digestion",
                successProbability = 85,
                timeCommitment = "8 glasses daily",
                difficulty = HabitDifficulty.EASY,
                isRecommended = true
            ),
            HabitSuggestion(
                name = "10-minute morning meditation",
                description = "Start your day with mindfulness to reduce stress and improve focus",
                category = HabitCategory.MENTAL_HEALTH,
                frequency = HabitFrequency.DAILY,
                priority = HabitPriority.MEDIUM,
                rationale = "Morning meditation builds mental resilience and sets a positive tone for the day",
                expectedImpact = "Reduced anxiety, better emotional regulation, improved concentration",
                successProbability = 75,
                timeCommitment = "10 minutes daily",
                difficulty = HabitDifficulty.MEDIUM
            ),
            HabitSuggestion(
                name = "Evening gratitude journaling",
                description = "Write down 3 things you're grateful for each evening",
                category = HabitCategory.MENTAL_HEALTH,
                frequency = HabitFrequency.DAILY,
                priority = HabitPriority.LOW,
                rationale = "Gratitude practices rewire the brain for positivity and improve sleep quality",
                expectedImpact = "Better sleep, improved mood, increased life satisfaction",
                successProbability = 80,
                timeCommitment = "5 minutes daily",
                difficulty = HabitDifficulty.EASY
            ),
            HabitSuggestion(
                name = "15-minute daily walk",
                description = "Take a brisk walk outside to get fresh air and movement",
                category = HabitCategory.FITNESS,
                frequency = HabitFrequency.DAILY,
                priority = HabitPriority.MEDIUM,
                rationale = "Regular walking improves cardiovascular health and mental well-being",
                expectedImpact = "Better fitness, stress reduction, vitamin D from sunlight",
                successProbability = 70,
                timeCommitment = "15 minutes daily",
                difficulty = HabitDifficulty.EASY
            ),
            HabitSuggestion(
                name = "5-minute morning stretching",
                description = "Start your day with gentle stretching to improve flexibility and reduce stiffness",
                category = HabitCategory.FITNESS,
                frequency = HabitFrequency.DAILY,
                priority = HabitPriority.MEDIUM,
                rationale = "Morning stretching improves flexibility, reduces muscle tension, and prepares your body for the day",
                expectedImpact = "Better flexibility, reduced stiffness, improved posture",
                successProbability = 80,
                timeCommitment = "5 minutes daily",
                difficulty = HabitDifficulty.EASY,
                isRecommended = true
            ),
            HabitSuggestion(
                name = "Meal planning on Sundays",
                description = "Plan your meals for the week ahead to eat healthier and save time",
                category = HabitCategory.NUTRITION,
                frequency = HabitFrequency.WEEKLY,
                priority = HabitPriority.MEDIUM,
                rationale = "Planning reduces decision fatigue and ensures healthier eating choices",
                expectedImpact = "Better nutrition, time savings, reduced food waste",
                successProbability = 65,
                timeCommitment = "30 minutes weekly",
                difficulty = HabitDifficulty.MEDIUM
            ),
            // Mindful tiny habits - designed to be very easy and automatic
            HabitSuggestion(
                name = "Name 3 things you can see when anxious",
                description = "When you feel anxious, pause and name 3 things you can see around you",
                category = HabitCategory.MENTAL_HEALTH,
                frequency = HabitFrequency.DAILY,
                priority = HabitPriority.HIGH,
                rationale = "This grounding technique interrupts anxiety spirals and brings you back to the present",
                expectedImpact = "Reduced anxiety, better emotional regulation, increased mindfulness",
                successProbability = 88,
                timeCommitment = "10 seconds when triggered",
                difficulty = HabitDifficulty.EASY,
                isRecommended = true
            ),
            HabitSuggestion(
                name = "Conscious breath before scrolling",
                description = "Before opening Instagram/TikTok, take one conscious breath",
                category = HabitCategory.MENTAL_HEALTH,
                frequency = HabitFrequency.DAILY,
                priority = HabitPriority.MEDIUM,
                rationale = "Creates a mindful pause before social media consumption",
                expectedImpact = "More conscious social media use, reduced mindless scrolling",
                successProbability = 82,
                timeCommitment = "5 seconds per trigger",
                difficulty = HabitDifficulty.EASY,
                isRecommended = true
            ),
            HabitSuggestion(
                name = "10-second eye close after meetings",
                description = "After every meeting ends, close your eyes for 10 seconds",
                category = HabitCategory.MENTAL_HEALTH,
                frequency = HabitFrequency.DAILY,
                priority = HabitPriority.MEDIUM,
                rationale = "Provides a brief mental reset between meetings and transitions",
                expectedImpact = "Better focus between tasks, reduced mental fatigue",
                successProbability = 85,
                timeCommitment = "10 seconds after each meeting",
                difficulty = HabitDifficulty.EASY,
                isRecommended = true
            ),
            HabitSuggestion(
                name = "Heart hand for self-criticism",
                description = "When you say something self-critical, put your hand on your heart and say 'I'm doing my best'",
                category = HabitCategory.MENTAL_HEALTH,
                frequency = HabitFrequency.DAILY,
                priority = HabitPriority.HIGH,
                rationale = "Self-compassion counteracts negative self-talk and builds emotional resilience",
                expectedImpact = "Improved self-esteem, reduced self-criticism, better emotional health",
                successProbability = 80,
                timeCommitment = "15 seconds when triggered",
                difficulty = HabitDifficulty.MEDIUM,
                isRecommended = true
            ),
            HabitSuggestion(
                name = "Gratitude before bed phone check",
                description = "Before checking your phone in bed, name one thing you're grateful for today",
                category = HabitCategory.MENTAL_HEALTH,
                frequency = HabitFrequency.DAILY,
                priority = HabitPriority.MEDIUM,
                rationale = "Ends the day on a positive note and improves sleep quality",
                expectedImpact = "Better sleep, improved mood, increased gratitude awareness",
                successProbability = 78,
                timeCommitment = "10 seconds before bed",
                difficulty = HabitDifficulty.EASY,
                isRecommended = true
            ),
            HabitSuggestion(
                name = "Daily breathing exercise",
                description = "Practice 3-5 minutes of guided breathing exercises for stress relief and focus",
                category = HabitCategory.MENTAL_HEALTH,
                frequency = HabitFrequency.DAILY,
                priority = HabitPriority.HIGH,
                rationale = "Breathing exercises reduce stress, improve focus, and regulate the nervous system",
                expectedImpact = "Reduced anxiety, better focus, improved emotional regulation",
                successProbability = 85,
                timeCommitment = "3-5 minutes daily",
                difficulty = HabitDifficulty.EASY,
                isRecommended = true
            ),
            HabitSuggestion(
                name = "Morning breathing routine",
                description = "Start your day with 3 minutes of box breathing to set a calm, focused tone",
                category = HabitCategory.MENTAL_HEALTH,
                frequency = HabitFrequency.DAILY,
                priority = HabitPriority.MEDIUM,
                rationale = "Morning breathing exercises prime your nervous system for a calm, productive day",
                expectedImpact = "Better morning mood, reduced stress, improved focus throughout the day",
                successProbability = 80,
                timeCommitment = "3 minutes daily",
                difficulty = HabitDifficulty.EASY,
                isRecommended = true
            ),
            HabitSuggestion(
                name = "Social media break",
                description = "Take a 30-minute break from social media to reduce stress and improve focus",
                category = HabitCategory.MENTAL_HEALTH,
                frequency = HabitFrequency.DAILY,
                priority = HabitPriority.MEDIUM,
                rationale = "Regular social media breaks reduce comparison, anxiety, and improve real-world connections",
                expectedImpact = "Reduced anxiety, better focus, improved sleep, more time for meaningful activities",
                successProbability = 75,
                timeCommitment = "30 minutes daily",
                difficulty = HabitDifficulty.MEDIUM,
                isRecommended = true
            ),
            HabitSuggestion(
                name = "No social media before bed",
                description = "Avoid social media for 1 hour before bedtime to improve sleep quality",
                category = HabitCategory.SLEEP,
                frequency = HabitFrequency.DAILY,
                priority = HabitPriority.HIGH,
                rationale = "Social media before bed disrupts sleep patterns and increases anxiety",
                expectedImpact = "Better sleep quality, reduced anxiety, improved morning mood",
                successProbability = 82,
                timeCommitment = "1 hour before bed",
                difficulty = HabitDifficulty.MEDIUM,
                isRecommended = true
            )
        )

        // Add base suggestions that aren't already being done
        baseSuggestions.forEach { suggestion ->
            if (!existingHabits.contains(suggestion.name)) {
                suggestions.add(suggestion)
            }
        }

        // Customize suggestions based on behavioral tendencies
        profile?.fourTendencies?.let { tendencies ->
            when (tendencies.tendency) {
                FourTendencies.UPHOLDER -> {
                    // Upholders respond well to clear expectations and accountability
                    suggestions.add(HabitSuggestion(
                        name = "Weekly habit review",
                        description = "Review your habit progress every Sunday and set clear goals",
                        category = HabitCategory.PRODUCTIVITY,
                        frequency = HabitFrequency.WEEKLY,
                        priority = HabitPriority.HIGH,
                        rationale = "Upholders thrive on clear expectations and regular check-ins",
                        expectedImpact = "Better habit consistency, increased accountability",
                        successProbability = 90,
                        timeCommitment = "15 minutes weekly",
                        difficulty = HabitDifficulty.MEDIUM,
                        isRecommended = true
                    ))
                }
                FourTendencies.QUESTIONER -> {
                    // Questioners need to understand the 'why' and see logical benefits
                    suggestions.add(HabitSuggestion(
                        name = "Read health articles",
                        description = "Read one evidence-based health article per week",
                        category = HabitCategory.LEARNING,
                        frequency = HabitFrequency.WEEKLY,
                        priority = HabitPriority.MEDIUM,
                        rationale = "Questioners are motivated by understanding the science behind health practices",
                        expectedImpact = "Deeper knowledge, better decision-making, sustained motivation",
                        successProbability = 85,
                        timeCommitment = "20 minutes weekly",
                        difficulty = HabitDifficulty.EASY,
                        isRecommended = true
                    ))
                }
                FourTendencies.OBLIGER -> {
                    // Obligers need external accountability
                    suggestions.add(HabitSuggestion(
                        name = "Habit accountability partner",
                        description = "Share your habits with a friend and check in weekly",
                        category = HabitCategory.SOCIAL,
                        frequency = HabitFrequency.WEEKLY,
                        priority = HabitPriority.HIGH,
                        rationale = "Obligers are highly motivated by external accountability and commitments to others",
                        expectedImpact = "Dramatically increased success rates through social accountability",
                        successProbability = 95,
                        timeCommitment = "10 minutes weekly",
                        difficulty = HabitDifficulty.EASY,
                        isRecommended = true
                    ))
                }
                FourTendencies.REBEL -> {
                    // Rebels want freedom and autonomy
                    suggestions.add(HabitSuggestion(
                        name = "Flexible habit experimentation",
                        description = "Try different versions of habits to find what feels right for you",
                        category = HabitCategory.OTHER,
                        frequency = HabitFrequency.CUSTOM,
                        priority = HabitPriority.MEDIUM,
                        rationale = "Rebels resist rigid structures but respond well to freedom and experimentation",
                        expectedImpact = "More sustainable habits that align with your personal style",
                        successProbability = 80,
                        timeCommitment = "Varies",
                        difficulty = HabitDifficulty.MEDIUM,
                        isRecommended = true
                    ))
                }
            }
        }

        // Customize based on friction points
        profile?.biggestFrictions?.let { frictions ->
            if (frictions.contains("Lack of time")) {
                suggestions.add(HabitSuggestion(
                    name = "5-minute habits only",
                    description = "Focus on habits that take 5 minutes or less to build momentum",
                    category = HabitCategory.PRODUCTIVITY,
                    frequency = HabitFrequency.DAILY,
                    priority = HabitPriority.HIGH,
                    rationale = "Starting small reduces time barriers and builds confidence",
                    expectedImpact = "Overcoming time constraints, building habit momentum",
                    successProbability = 88,
                    timeCommitment = "5 minutes daily",
                    difficulty = HabitDifficulty.EASY,
                    isRecommended = true
                ))
            }

            if (frictions.contains("Lack of motivation")) {
                suggestions.add(HabitSuggestion(
                    name = "Motivation visualization",
                    description = "Spend 2 minutes visualizing the benefits of your habits each morning",
                    category = HabitCategory.MENTAL_HEALTH,
                    frequency = HabitFrequency.DAILY,
                    priority = HabitPriority.MEDIUM,
                    rationale = "Visualization activates the brain's reward system and increases motivation",
                    expectedImpact = "Higher motivation, better habit adherence, positive mindset",
                    successProbability = 82,
                    timeCommitment = "2 minutes daily",
                    difficulty = HabitDifficulty.EASY
                ))
            }
        }

        // Add nutrition-focused suggestions if needed
        if (!existingHabits.any { it.contains("meal", ignoreCase = true) || it.contains("food", ignoreCase = true) }) {
            suggestions.add(HabitSuggestion(
                name = "Protein with every meal",
                description = "Include a protein source with each meal to stay full and energized",
                category = HabitCategory.NUTRITION,
                frequency = HabitFrequency.DAILY,
                priority = HabitPriority.MEDIUM,
                rationale = "Protein supports muscle maintenance, satiety, and stable energy levels",
                expectedImpact = "Better energy, reduced cravings, improved muscle health",
                successProbability = 78,
                timeCommitment = "Planning time only",
                difficulty = HabitDifficulty.EASY
            ))
        }

        return suggestions.take(10) // Limit to top 10 suggestions
    }

    fun selectSuggestion(suggestion: HabitSuggestion) {
        _uiState.value = _uiState.value.copy(
            selectedSuggestion = suggestion,
            showSuggestionDetails = true
        )
    }

    fun dismissSuggestionDetails() {
        _uiState.value = _uiState.value.copy(
            selectedSuggestion = null,
            showSuggestionDetails = false
        )
    }

    fun createHabitFromSuggestion(suggestion: HabitSuggestion, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                // Extract targetValue and unit from timeCommitment string
                val (targetValue, unit) = parseTimeCommitment(suggestion.timeCommitment)
                
                val habit = Habit(
                    userId = userId,
                    title = suggestion.name,
                    description = suggestion.description,
                    category = suggestion.category,
                    frequency = suggestion.frequency,
                    priority = suggestion.priority,
                    targetValue = targetValue,
                    unit = unit,
                    isActive = true,
                    createdAt = java.util.Date(),
                    updatedAt = java.util.Date()
                )

                val result = habitRepository.createHabit(userId, habit)
                
                if (result.isSuccess) {
                    val habitId = result.getOrNull() ?: throw IllegalStateException("Habit created but no ID returned")
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    onSuccess(habitId)
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error occurred"
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to create habit: $error",
                        isLoading = false
                    )
                    android.util.Log.e("HabitSuggestionsViewModel", "Failed to create habit: $error", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                android.util.Log.e("HabitSuggestionsViewModel", "Exception creating habit: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    error = "Failed to create habit: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
