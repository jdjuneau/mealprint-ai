package com.coachie.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.data.Secrets
import com.mealprint.ai.data.ai.SmartCoachEngine
import com.mealprint.ai.data.model.HealthLog
import com.mealprint.ai.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore

class JournalFlowViewModel : ViewModel() {

    companion object {
        private const val TAG = "JournalFlowViewModel"
    }

    private val firebaseRepository = FirebaseRepository.getInstance()
    private val openAI = com.aallam.openai.client.OpenAI(token = Secrets.getOpenAIApiKey())

    // UI States
    private val _currentEntry = MutableStateFlow<HealthLog.JournalEntry?>(null)
    val currentEntry: StateFlow<HealthLog.JournalEntry?> = _currentEntry.asStateFlow()

    private val _prompts = MutableStateFlow<List<String>>(emptyList())
    val prompts: StateFlow<List<String>> = _prompts.asStateFlow()

    private val _conversation = MutableStateFlow<List<HealthLog.ChatMessage>>(emptyList())
    val conversation: StateFlow<List<HealthLog.ChatMessage>> = _conversation.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isGeneratingResponse = MutableStateFlow(false)
    val isGeneratingResponse: StateFlow<Boolean> = _isGeneratingResponse.asStateFlow()

    private val _currentInput = MutableStateFlow("")
    val currentInput: StateFlow<String> = _currentInput.asStateFlow()

    private val _saveSuccessMessage = MutableStateFlow<String?>(null)
    val saveSuccessMessage: StateFlow<String?> = _saveSuccessMessage.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId.asStateFlow()

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    fun initialize(userId: String, profile: UserProfile? = null) {
        _userId.value = userId
        _profile.value = profile

        // Check if there's already a journal entry for today
        loadTodaysEntry()

        // Generate prompts if we don't have an entry yet
        if (_currentEntry.value == null) {
            generatePersonalizedPrompts()
        }
    }

    /**
     * Load today's journal entry if it exists
     */
    private fun loadTodaysEntry() {
        val uid = _userId.value ?: return
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        viewModelScope.launch {
            try {
                val healthLogs = firebaseRepository.getHealthLogs(uid, today).getOrNull() ?: emptyList()
                val entry = healthLogs.filterIsInstance<HealthLog.JournalEntry>()
                    .firstOrNull { it.date == today }

                if (entry != null) {
                    _currentEntry.value = entry
                    _prompts.value = entry.prompts
                    _conversation.value = entry.conversation
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading today's journal entry", e)
            }
        }
    }

    /**
     * Generate 3 personalized prompts based on today's data
     */
    private fun generatePersonalizedPrompts() {
        val uid = _userId.value ?: return
        val today = LocalDate.now()

        viewModelScope.launch {
            _isLoading.value = true

            try {
                // Get today's health logs for context
                val todayLogs = firebaseRepository.getHealthLogs(uid, today.format(DateTimeFormatter.ISO_LOCAL_DATE)).getOrNull() ?: emptyList()

                // Generate prompts based on today's data
                val prompts = generatePromptsFromData(todayLogs)

                _prompts.value = prompts

                // Create new journal entry
                val entry = HealthLog.JournalEntry(
                    entryId = generateEntryId(),
                    date = today.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    prompts = prompts,
                    conversation = emptyList(),
                    startedAt = System.currentTimeMillis()
                )

                _currentEntry.value = entry

            } catch (e: Exception) {
                Log.e(TAG, "Error generating prompts", e)
                // Fallback prompts
                _prompts.value = listOf(
                    "What drained you most today?",
                    "One thing your body carried you through today?",
                    "What would make tomorrow feel 1% calmer?"
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Generate personalized prompts based on today's health data
     */
    private suspend fun generatePromptsFromData(todayLogs: List<HealthLog>): List<String> {
        // Analyze today's data to create relevant prompts
        val moodLogs = todayLogs.filterIsInstance<HealthLog.MoodLog>()
        val workoutLogs = todayLogs.filterIsInstance<HealthLog.WorkoutLog>()
        val sleepLogs = todayLogs.filterIsInstance<HealthLog.SleepLog>()
        val mindfulSessions = todayLogs.filterIsInstance<HealthLog.MindfulSession>()

        val prompts = mutableListOf<String>()

        // Prompt 1: Based on energy/stress levels
        if (moodLogs.isNotEmpty()) {
            val avgEnergy = moodLogs.mapNotNull { it.energyLevel }.average()
            val hasStressEmotions = moodLogs.any { log ->
                log.emotions.any { emotion ->
                    emotion.lowercase().contains("stress") ||
                    emotion.lowercase().contains("anxious") ||
                    emotion.lowercase().contains("overwhelmed")
                }
            }

            prompts.add(when {
                hasStressEmotions -> "What drained you most today?"
                avgEnergy < 4 -> "What would help you recharge for tomorrow?"
                else -> "What energized you most today?"
            })
        } else {
            prompts.add("What drained you most today?")
        }

        // Prompt 2: Based on physical activity and body awareness
        if (workoutLogs.isNotEmpty() || mindfulSessions.isNotEmpty()) {
            prompts.add("One thing your body carried you through today?")
        } else {
            prompts.add("How did your body feel throughout the day?")
        }

        // Prompt 3: Based on sleep quality and future planning
        if (sleepLogs.isNotEmpty()) {
            val avgSleepQuality = sleepLogs.map { it.quality }.average()
            prompts.add(if (avgSleepQuality < 3) {
                "What would help you sleep more peacefully tonight?"
            } else {
                "What would make tomorrow feel 1% calmer?"
            })
        } else {
            prompts.add("What would make tomorrow feel 1% calmer?")
        }

        return prompts.take(3) // Ensure we only return 3 prompts
    }

    /**
     * Send user message and get AI response
     */
    fun sendMessage(content: String) {
        if (content.trim().isEmpty()) return

        val uid = _userId.value ?: return
        val entry = _currentEntry.value ?: return

        // Add user message to conversation
        val userMessage = HealthLog.ChatMessage(
            id = generateMessageId(),
            role = "user",
            content = content.trim(),
            timestamp = System.currentTimeMillis()
        )

        val updatedConversation = _conversation.value + userMessage
        _conversation.value = updatedConversation
        _currentInput.value = ""

        // Update entry with new message
        updateEntryInFirebase(entry.copy(
            conversation = updatedConversation,
            wordCount = updatedConversation.sumOf { it.content.split("\\s+".toRegex()).size }
        ))

        // Generate AI response
        generateAIResponse(updatedConversation, uid)
    }

    /**
     * Generate AI response to user message
     */
    private fun generateAIResponse(conversation: List<HealthLog.ChatMessage>, userId: String) {
        viewModelScope.launch {
            _isGeneratingResponse.value = true

            try {
                val prompt = buildAIResponsePrompt(conversation)
                val response = generateChatResponse(prompt)

                val coachieMessage = HealthLog.ChatMessage(
                    id = generateMessageId(),
                    role = "coachie",
                    content = response,
                    timestamp = System.currentTimeMillis()
                )

                val updatedConversation = _conversation.value + coachieMessage
                _conversation.value = updatedConversation

                // Update entry in Firebase
                val entry = _currentEntry.value ?: return@launch
                val updatedEntry = entry.copy(
                    conversation = updatedConversation,
                    wordCount = updatedConversation.sumOf { it.content.split("\\s+".toRegex()).size }
                )
                updateEntryInFirebase(updatedEntry)

            } catch (e: Exception) {
                Log.e(TAG, "Error generating AI response", e)

                // Add error message
                val errorMessage = HealthLog.ChatMessage(
                    id = generateMessageId(),
                    role = "coachie",
                    content = "I'm here to listen. Would you like to share more about that?",
                    timestamp = System.currentTimeMillis()
                )
                _conversation.value = _conversation.value + errorMessage
            } finally {
                _isGeneratingResponse.value = false
            }
        }
    }

    /**
     * Build prompt for AI response
     */
    private fun buildAIResponsePrompt(conversation: List<HealthLog.ChatMessage>): String {
        // Limit to last 3 messages (1.5 exchanges) to save tokens
        val recentMessages = conversation.takeLast(3)
        val conversationText = recentMessages.joinToString(" | ") { msg ->
            "${if (msg.role == "user") "U" else "C"}: ${msg.content.take(200)}" // Truncate long messages
        }

        return "User journaling. Context: $conversationText. Respond supportively in 1-2 sentences. Acknowledge, encourage, optionally ask a question."
    }

    /**
     * Generate AI chat response
     */
    private suspend fun generateChatResponse(prompt: String): String {
        val chatRequest = ChatCompletionRequest(
            model = ModelId("gpt-3.5-turbo"), // Changed from gpt-4 to reduce costs
            messages = listOf(
                ChatMessage(
                    role = ChatRole.System,
                    content = "You are Coachie, a fitness and wellness coach. Be supportive but also hold users accountable to their goals. If they mention not making progress (like not losing weight), be direct about what needs to change. Balance empathy with accountability - you're a coach, not just a friend. Keep responses concise (under 100 words)."
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = prompt
                )
            ),
            temperature = 0.7,
            maxTokens = 100 // Reduced to save tokens
        )

        val response = openAI.chatCompletion(chatRequest)
        val text = response.choices.firstOrNull()?.message?.content?.trim() ?: "Thank you for sharing that with me."

        // Best-effort usage logging directly to Firestore
        try {
            val uid = _userId.value ?: ""
            if (uid.isNotEmpty()) {
                val dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val approxPromptTokens = prompt.length / 4
                val approxCompletionTokens = text.length / 4
                val event = hashMapOf(
                    "userId" to uid,
                    "timestamp" to System.currentTimeMillis(),
                    "source" to "journal",
                    "model" to "gpt-4",
                    "promptTokens" to approxPromptTokens,
                    "completionTokens" to approxCompletionTokens,
                    "totalTokens" to (approxPromptTokens + approxCompletionTokens),
                    "estimatedCostUsd" to ((approxPromptTokens + approxCompletionTokens) * 0.00001).toDouble()
                )
                val db = Firebase.firestore
                db.collection("logs").document(uid)
                    .collection("daily").document(dateStr)
                    .collection("ai_usage").add(event)
            }
        } catch (_: Exception) {}

        return text
    }

    /**
     * Complete the journal session
     */
    fun completeJournal(habitId: String? = null) {
        val entry = _currentEntry.value ?: return
        val uid = _userId.value ?: return

        viewModelScope.launch {
            try {
                _saveError.value = null
                _saveSuccessMessage.value = null
                
                val completedEntry = entry.copy(
                    completedAt = System.currentTimeMillis(),
                    isCompleted = true
                )

                // Save to Firebase and wait for result
                val saveResult = firebaseRepository.saveHealthLog(uid, completedEntry.date, completedEntry)
                
                if (saveResult.isSuccess) {
                    _currentEntry.value = completedEntry
                    _saveSuccessMessage.value = "Journal saved successfully!"

                    // Extract wins and gratitudes from the completed journal
                    extractWinsAndGratitudes(completedEntry, uid)
                    
                    // If this was started from a habit, complete the habit
                    if (habitId != null) {
                        val habitRepository = com.coachie.app.data.HabitRepository.getInstance()
                        habitRepository.completeHabit(uid, habitId, 1, null)
                    }

                    Log.d(TAG, "Journal session completed for user: $uid")
                } else {
                    val errorMsg = saveResult.exceptionOrNull()?.message ?: "Failed to save journal"
                    _saveError.value = errorMsg
                    Log.e(TAG, "Error saving journal: $errorMsg", saveResult.exceptionOrNull())
                }

            } catch (e: Exception) {
                val errorMsg = e.message ?: "Failed to save journal"
                _saveError.value = errorMsg
                Log.e(TAG, "Error completing journal", e)
            }
        }
    }

    /**
     * Extract wins and gratitudes from completed journal session
     */
    private suspend fun extractWinsAndGratitudes(journalEntry: HealthLog.JournalEntry, userId: String) {
        try {
            // Limit conversation text to save tokens - only user messages and last 500 chars
            val userMessages = journalEntry.conversation.filter { it.role == "user" }
            val conversationText = userMessages.joinToString(" | ") { 
                it.content.take(150) // Truncate each message
            }.take(500) // Limit total length

            // Create concise AI prompt to extract wins and gratitudes
            val prompt = "Extract 1 win and 1 gratitude from: $conversationText. Format: WIN: [win] | GRATITUDE: [gratitude]"

            val extractionResult = generateWinExtraction(prompt)

            // Parse the result
            val win = extractWinFromResult(extractionResult)
            val gratitude = extractGratitudeFromResult(extractionResult)

            // Determine mood from conversation (simple heuristic)
            val moodScore = determineMoodScore(journalEntry.conversation)
            val moodDescription = when (moodScore) {
                1 -> "challenging"
                2 -> "difficult"
                3 -> "neutral"
                4 -> "good"
                5 -> "excellent"
                else -> null
            }

            // Create win entry
            if (win != null || gratitude != null) {
                val winEntry = HealthLog.WinEntry(
                    entryId = generateEntryId(),
                    journalEntryId = journalEntry.entryId,
                    date = journalEntry.date,
                    win = win,
                    gratitude = gratitude,
                    mood = moodDescription,
                    moodScore = moodScore,
                    tags = generateTags(win, gratitude, conversationText)
                )

                // Save to Firebase
                firebaseRepository.saveHealthLog(userId, journalEntry.date, winEntry)
                Log.d(TAG, "Saved win entry: ${winEntry.entryId}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting wins and gratitudes", e)
        }
    }

    /**
     * Generate AI response for win extraction
     */
    private suspend fun generateWinExtraction(prompt: String): String {
        val chatRequest = ChatCompletionRequest(
            model = ModelId("gpt-3.5-turbo"), // Switched from gpt-4 to reduce costs significantly
            messages = listOf(
                ChatMessage(
                    role = ChatRole.System,
                    content = "You are an expert at analyzing journal entries to extract positive insights, wins, and expressions of gratitude. Be encouraging and focus on the positive aspects."
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = prompt
                )
            ),
            temperature = 0.3,
            maxTokens = 120 // Reduced from 150 to save on output tokens
        )

        val response = openAI.chatCompletion(chatRequest)
        return response.choices.firstOrNull()?.message?.content?.trim() ?: ""
    }

    /**
     * Extract win from AI result
     */
    private fun extractWinFromResult(result: String): String? {
        val winLine = result.lines().find { it.startsWith("WIN:", ignoreCase = true) }
        return winLine?.substringAfter(":")?.trim()?.takeIf { it.isNotBlank() && it != "none" }
    }

    /**
     * Extract gratitude from AI result
     */
    private fun extractGratitudeFromResult(result: String): String? {
        val gratitudeLine = result.lines().find { it.startsWith("GRATITUDE:", ignoreCase = true) }
        return gratitudeLine?.substringAfter(":")?.trim()?.takeIf { it.isNotBlank() && it != "none" }
    }

    /**
     * Determine mood score from conversation
     */
    private fun determineMoodScore(conversation: List<HealthLog.ChatMessage>): Int? {
        val userMessages = conversation.filter { it.role == "user" }
        if (userMessages.isEmpty()) return null

        val text = userMessages.joinToString(" ") { it.content }.lowercase()

        // Simple heuristic based on positive/negative words
        val positiveWords = listOf("happy", "good", "great", "excellent", "amazing", "love", "grateful", "proud", "accomplished")
        val negativeWords = listOf("sad", "bad", "terrible", "awful", "hate", "angry", "frustrated", "overwhelmed", "stressed")

        val positiveCount = positiveWords.sumOf { word -> text.split("\\s+".toRegex()).count { it.contains(word) } }
        val negativeCount = negativeWords.sumOf { word -> text.split("\\s+".toRegex()).count { it.contains(word) } }

        return when {
            positiveCount > negativeCount * 2 -> 5 // Very positive
            positiveCount > negativeCount -> 4 // Positive
            positiveCount == negativeCount -> 3 // Neutral
            negativeCount > positiveCount -> 2 // Negative
            else -> 1 // Very negative
        }
    }

    /**
     * Generate tags based on content
     */
    private fun generateTags(win: String?, gratitude: String?, conversationText: String): List<String> {
        val tags = mutableListOf<String>()
        val text = "$win $gratitude $conversationText".lowercase()

        if (text.contains("health") || text.contains("exercise") || text.contains("workout") || text.contains("water")) {
            tags.add("health")
        }
        if (text.contains("work") || text.contains("job") || text.contains("career") || text.contains("meeting")) {
            tags.add("work")
        }
        if (text.contains("family") || text.contains("friend") || text.contains("relationship") || text.contains("love")) {
            tags.add("relationships")
        }
        if (text.contains("learn") || text.contains("study") || text.contains("skill") || text.contains("growth")) {
            tags.add("learning")
        }
        if (text.contains("mindful") || text.contains("meditat") || text.contains("breath")) {
            tags.add("mindfulness")
        }

        return tags.take(3) // Limit to 3 tags
    }

    /**
     * Update current input text
     */
    fun updateInput(input: String) {
        _currentInput.value = input
    }

    /**
     * Clear save success message
     */
    fun clearSaveMessage() {
        _saveSuccessMessage.value = null
        _saveError.value = null
    }

    /**
     * Update journal entry in Firebase
     */
    private fun updateEntryInFirebase(entry: HealthLog.JournalEntry) {
        val uid = _userId.value ?: return

        viewModelScope.launch {
            firebaseRepository.saveHealthLog(uid, entry.date, entry)
        }
    }

    /**
     * Generate unique entry ID
     */
    private fun generateEntryId(): String {
        return "journal_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
    }

    /**
     * Generate unique message ID
     */
    private fun generateMessageId(): String {
        return "msg_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
    }
}
