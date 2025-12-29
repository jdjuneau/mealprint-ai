package com.coachie.app.data.ai

import com.coachie.app.data.Secrets
import com.coachie.app.data.model.DailyLog
import com.coachie.app.data.model.UserProfile
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RunWith(MockitoJUnitRunner::class)
@ExperimentalCoroutinesApi
class GeminiClientTest {

    // Note: Real Gemini API calls would be integration tests
    // For unit tests, we'll focus on testable logic like prompt building and fallback messages

    private val testProfile = UserProfile.create(
        uid = "test_user",
        name = "John Doe",
        currentWeight = 75.0,
        goalWeight = 70.0,
        heightCm = 175.0,
        activityLevel = "moderately active"
    ).getOrThrow()

    private val testDailyLog = DailyLog(
        uid = "test_user",
        date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
        weight = 74.5,
        steps = 8500,
        water = 2000,
        mood = 4,
        notes = "Feeling good today"
    )

    @Test
    fun `buildCoachPrompt creates correct prompt structure`() {
        // Given
        val client = GeminiClient()

        // Use reflection to access private method for testing
        val method = GeminiClient::class.java.getDeclaredMethod("buildCoachPrompt", UserProfile::class.java, DailyLog::class.java)
        method.isAccessible = true

        // When
        val prompt = method.invoke(client, testProfile, testDailyLog) as String

        // Then
        assertTrue(prompt.contains("You are Coachie"))
        assertTrue(prompt.contains("John Doe"))
        assertTrue(prompt.contains("75.0kg"))
        assertTrue(prompt.contains("70.0kg"))
        assertTrue(prompt.contains("175.0cm"))
        assertTrue(prompt.contains("8500 steps"))
        assertTrue(prompt.contains("moderately active"))
        assertTrue(prompt.contains("INSTRUCTIONS"))
    }

    @Test
    fun `getFallbackMessage returns goal reached message for completed goals`() {
        // Given
        val client = GeminiClient()
        val completedProfile = testProfile.copy(currentWeight = 70.0, goalWeight = 70.0)

        // Use reflection to access private method
        val method = GeminiClient::class.java.getDeclaredMethod("getFallbackMessage", UserProfile::class.java, DailyLog::class.java)
        method.isAccessible = true

        // When
        val message = method.invoke(client, completedProfile, testDailyLog) as String

        // Then
        assertTrue(message.contains("reached your goal"))
        assertTrue(message.contains("John"))
    }

    @Test
    fun `getFallbackMessage returns activity message for logged data`() {
        // Given
        val client = GeminiClient()

        // Use reflection to access private method
        val method = GeminiClient::class.java.getDeclaredMethod("getFallbackMessage", UserProfile::class.java, DailyLog::class.java)
        method.isAccessible = true

        // When
        val message = method.invoke(client, testProfile, testDailyLog) as String

        // Then
        assertTrue(message.contains("Great job logging"))
        assertTrue(message.contains("John"))
    }

    @Test
    fun `getFallbackMessage returns weight loss message for weight loss goals`() {
        // Given
        val client = GeminiClient()
        val emptyLog = DailyLog(
            uid = "test_user",
            date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        )

        // Use reflection to access private method
        val method = GeminiClient::class.java.getDeclaredMethod("getFallbackMessage", UserProfile::class.java, DailyLog::class.java)
        method.isAccessible = true

        // When
        val message = method.invoke(client, testProfile, emptyLog) as String

        // Then - Since profile has weight loss goal (weightDifference < 0), it should show general message
        assertTrue(message.contains("Ready to crush"))
        assertTrue(message.contains("John"))
    }

    @Test
    fun `getFallbackMessage returns general message for weight gain goals`() {
        // Given
        val client = GeminiClient()
        val gainProfile = testProfile.copy(currentWeight = 65.0, goalWeight = 70.0)
        val emptyLog = DailyLog(
            uid = "test_user",
            date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        )

        // Use reflection to access private method
        val method = GeminiClient::class.java.getDeclaredMethod("getFallbackMessage", UserProfile::class.java, DailyLog::class.java)
        method.isAccessible = true

        // When
        val message = method.invoke(client, gainProfile, emptyLog) as String

        // Then - Weight gain goal shows "consistency is key" message
        assertTrue(message.contains("consistency is key"))
        assertTrue(message.contains("John"))
    }

    @Test
    fun `getFallbackMessage handles null names gracefully`() {
        // Given
        val client = GeminiClient()
        val namelessProfile = testProfile.copy(name = "")
        val emptyLog = DailyLog(
            uid = "test_user",
            date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        )

        // Use reflection to access private method
        val method = GeminiClient::class.java.getDeclaredMethod("getFallbackMessage", UserProfile::class.java, DailyLog::class.java)
        method.isAccessible = true

        // When
        val message = method.invoke(client, namelessProfile, emptyLog) as String

        // Then
        assertTrue(message.contains("there")) // Should use "there" as fallback
    }

}
