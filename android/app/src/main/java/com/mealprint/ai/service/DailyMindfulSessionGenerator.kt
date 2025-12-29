package com.coachie.app.service

import android.content.Context
import android.util.Log
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.data.ai.GeminiFlashClient
import com.mealprint.ai.data.model.HealthLog
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Service responsible for generating daily 3-minute mindful audio sessions
 */
class DailyMindfulSessionGenerator(
    private val context: Context,
    private val firebaseRepository: FirebaseRepository = FirebaseRepository.getInstance(),
) {

    companion object {
        private const val TAG = "DailyMindfulSessionGenerator"
        private const val TARGET_DURATION_SECONDS = 180 // 3 minutes
        private const val WORDS_PER_MINUTE = 150 // Average speaking rate
    }

    private val geminiFlashClient: GeminiFlashClient by lazy {
        GeminiFlashClient(context)
    }

    /**
     * Generate today's mindful session for a user
     */
    suspend fun generateTodaysSession(userId: String): Result<HealthLog.MindfulSession> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting mindful session generation for user: $userId")

            // Check if session already exists for today
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val existingSession = getExistingSession(userId, today)
            if (existingSession != null) {
                Log.d(TAG, "Session already exists for today, returning existing")
                return@withContext Result.success(existingSession)
            }

            // Gather user data for personalization
            val userData = gatherUserData(userId)

            // Generate personalized prompt
        val targetWordCount = (TARGET_DURATION_SECONDS / 60.0 * WORDS_PER_MINUTE).toInt()
        val prompt = buildPersonalizedPrompt(userData, targetWordCount)

        // Generate script using Gemini Flash (5x cheaper than OpenAI)
        val script = generateMindfulnessScript(prompt, targetWordCount, userId)

            // Create session object
            val session = HealthLog.MindfulSession(
                sessionId = generateSessionId(),
                title = "Today's 3-Min Reset",
                transcript = script,
                durationSeconds = TARGET_DURATION_SECONDS,
                generatedDate = today,
                personalizedPrompt = prompt
            )

            // Save to Firebase
            val saveResult = firebaseRepository.saveHealthLog(userId, today, session)
            if (saveResult.isSuccess) {
                Log.d(TAG, "Successfully generated and saved mindful session for $userId")
                Result.success(session)
            } else {
                Log.e(TAG, "Failed to save mindful session", saveResult.exceptionOrNull())
                Result.failure(saveResult.exceptionOrNull() ?: Exception("Failed to save session"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error generating mindful session", e)
            Result.failure(e)
        }
    }

    /**
     * Gather relevant user data for personalization
     */
    private suspend fun gatherUserData(userId: String): UserHealthData {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        // Get yesterday's health logs
        val yesterdayLogs = firebaseRepository.getHealthLogs(userId, yesterday.format(DateTimeFormatter.ISO_LOCAL_DATE)).getOrNull() ?: emptyList()

        // Extract relevant metrics
        val sleepScore = extractSleepScore(yesterdayLogs)
        val moodTrend = extractMoodTrend(yesterdayLogs)
        val stressLevel = extractStressLevel(yesterdayLogs)
        val hrvData = extractHRVData(yesterdayLogs)

        // Get weather (placeholder - would need weather API integration)
        val weather = "sunny" // Placeholder

        // Get calendar stress (placeholder - would need calendar API)
        val calendarStress = "moderate" // Placeholder

        return UserHealthData(
            sleepScore = sleepScore,
            moodTrend = moodTrend,
            stressLevel = stressLevel,
            hrvTrend = hrvData,
            weather = weather,
            calendarStress = calendarStress
        )
    }

    /**
     * Build personalized prompt based on user data
     */
    private fun buildPersonalizedPrompt(userData: UserHealthData, targetWordCount: Int): String {
        val conditions = mutableListOf<String>()

        // Sleep condition
        if (userData.sleepScore != null) {
            conditions.add("slept ${userData.sleepScore}/5 quality")
        }

        // HRV condition
        if (userData.hrvTrend != null) {
            conditions.add("HRV ${if (userData.hrvTrend < 0) "dropped" else "stable"}")
        }

        // Mood condition
        if (userData.moodTrend != null) {
            conditions.add("mood ${userData.moodTrend}")
        }

        // Stress condition
        if (userData.stressLevel != null && userData.stressLevel > 6) {
            conditions.add("high stress (${userData.stressLevel}/10)")
        }

        val conditionsText = if (conditions.isNotEmpty()) conditions.joinToString(" + ") else "general wellness"

        return "Write a calm, compassionate 3-minute mindfulness script for someone who $conditionsText. Include one body awareness exercise and one self-kindness phrase. Keep it exactly $targetWordCount words for a 3-minute delivery."
    }

    /**
     * Generate mindfulness script using Gemini Flash (5x cheaper than OpenAI)
     */
    private suspend fun generateMindfulnessScript(prompt: String, targetWordCount: Int, userId: String): String {
        try {
            val systemPrompt = """You are a compassionate mindfulness coach. Create personalized 3-minute mindfulness scripts that are:
- Calming and supportive
- Include body awareness exercises
- Include self-kindness phrases
- Perfect length for exactly 3 minutes when spoken at 150 words per minute
- Natural and conversational tone
- CRITICAL: Never repeat the same sentence or phrase back-to-back. Each sentence must be unique and flow naturally to the next. Avoid any repetition of consecutive lines. Every sentence should bring new value and progress the meditation forward."""

            val result = geminiFlashClient.generateText(
                prompt = prompt,
                systemPrompt = systemPrompt,
                temperature = 0.8, // Slightly increased for more variety
                maxTokens = 600, // Target ~450 words for 3-minute script
                userId = userId,
                source = "mindfulnessSession"
            )

            val rawScript = result.getOrNull()?.trim()
                ?: "Take a deep breath in... and slowly exhale. Feel the peace within you."
            
            // Usage logging is handled automatically by GeminiFlashClient
            
            // Post-process to remove consecutive duplicate sentences
            return removeConsecutiveDuplicates(rawScript)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini Flash API", e)
            throw Exception("Failed to generate mindfulness script: ${e.message}", e)
        }
    }

    /**
     * Remove consecutive duplicate sentences from the meditation script
     * Splits by sentence boundaries and filters out consecutive duplicates
     */
    private fun removeConsecutiveDuplicates(script: String): String {
        // Split by sentence boundaries (period, exclamation, question mark followed by space or newline)
        val sentences = script.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotBlank() }
        
        if (sentences.isEmpty()) return script
        
        val filtered = mutableListOf<String>()
        var previousSentence = ""
        
        for (sentence in sentences) {
            // Normalize for comparison (lowercase, remove extra whitespace)
            val normalizedCurrent = sentence.lowercase().replace(Regex("\\s+"), " ").trim()
            val normalizedPrevious = previousSentence.lowercase().replace(Regex("\\s+"), " ").trim()
            
            // Only add if it's different from the previous sentence
            if (normalizedCurrent != normalizedPrevious) {
                filtered.add(sentence)
                previousSentence = sentence
            } else {
                Log.d(TAG, "Removed duplicate consecutive sentence: $sentence")
            }
        }
        
        // Join back with spaces and ensure proper punctuation
        return filtered.joinToString(" ").trim()
    }

    /**
     * Extract sleep score from health logs
     */
    private fun extractSleepScore(logs: List<HealthLog>): Int? {
        return logs.filterIsInstance<HealthLog.SleepLog>()
            .maxByOrNull { it.timestamp }
            ?.quality
    }

    /**
     * Extract mood trend from health logs
     */
    private fun extractMoodTrend(logs: List<HealthLog>): String? {
        val moodLogs = logs.filterIsInstance<HealthLog.MoodLog>()
        if (moodLogs.isEmpty()) return null

        val avgMood = moodLogs.map { it.level }.average()
        return when {
            avgMood >= 4 -> "excellent"
            avgMood >= 3 -> "good"
            avgMood >= 2 -> "challenging"
            else -> "difficult"
        }
    }

    /**
     * Extract stress level from health logs
     */
    private fun extractStressLevel(logs: List<HealthLog>): Int? {
        val moodLog = logs.filterIsInstance<HealthLog.MoodLog>()
            .maxByOrNull { it.timestamp }

        // Check for stress-related emotions
        if (moodLog?.emotions?.any { emotion ->
            emotion.lowercase().contains("stress") ||
            emotion.lowercase().contains("anxious") ||
            emotion.lowercase().contains("overwhelmed")
        } == true) {
            return 7 // High stress level
        }

        // Use energy level as proxy for stress (low energy might indicate stress)
        return moodLog?.energyLevel?.let { energy ->
            when {
                energy <= 3 -> 8 // Low energy = high stress
                energy <= 5 -> 6 // Medium-low energy = medium stress
                else -> null // Normal energy = no stress indicator
            }
        }
    }

    /**
     * Extract HRV data (placeholder - would need Google Fit integration)
     */
    private fun extractHRVData(logs: List<HealthLog>): Float? {
        // Placeholder - in real implementation, would extract from Google Fit HRV data
        return null
    }

    /**
     * Check if session already exists for today
     */
    private suspend fun getExistingSession(userId: String, date: String): HealthLog.MindfulSession? {
        val logs = firebaseRepository.getHealthLogs(userId, date).getOrNull() ?: return null
        return logs.filterIsInstance<HealthLog.MindfulSession>()
            .firstOrNull { it.generatedDate == date }
    }

    /**
     * Generate unique session ID
     */
    private fun generateSessionId(): String {
        return "session_${System.currentTimeMillis()}_${(0..9999).random()}"
    }

    /**
     * Data class for user health data
     */
    private data class UserHealthData(
        val sleepScore: Int?,
        val moodTrend: String?,
        val stressLevel: Int?,
        val hrvTrend: Float?,
        val weather: String,
        val calendarStress: String
    )
}
