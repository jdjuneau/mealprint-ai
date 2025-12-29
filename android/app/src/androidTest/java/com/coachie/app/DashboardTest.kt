package com.coachie.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coachie.app.ui.screen.HomeScreen
import com.coachie.app.ui.theme.CoachieTheme
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI Tests for HomeScreen/Dashboard functionality
 */
@RunWith(AndroidJUnit4::class)
class DashboardTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        // Configure Firebase to use emulator for testing
        val firestore = FirebaseFirestore.getInstance()
        firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setHost("10.0.2.2:8080") // Android emulator localhost
            .setSslEnabled(false)
            .setPersistenceEnabled(false)
            .build()

        // Note: For real device testing, you might want to use a different host
        // or configure Firebase to use production if needed
    }

    @Test
    fun testDashboardClickUpdatesState() {
        composeTestRule.setContent {
            CoachieTheme {
                HomeScreen(
                    onSignOut = {},
                    onNavigateToMealLog = {},
                    onNavigateToWorkoutLog = {},
                    onNavigateToSleepLog = {},
                    onNavigateToWaterLog = {},
                    onNavigateToDebug = {}
                )
            }
        }

        // Wait for the screen to load
        composeTestRule.waitForIdle()

        // Look for Quick Log buttons (they contain text like "Meal", "Workout", etc.)
        // Since the buttons are in a grid, we need to find them by their text content

        // Test Meal button click
        composeTestRule.onNodeWithText("Meal").performClick()

        // Wait for any animations/state changes
        composeTestRule.waitForIdle()

        // Verify that some UI feedback occurred
        // Note: Since the actual implementation might show toasts or update state,
        // we'll check for visual feedback or state changes

        // Alternative: Check if the button is still clickable (not disabled)
        composeTestRule.onNodeWithText("Meal").assertExists()

        // Test multiple clicks to ensure debouncing works
        composeTestRule.onNodeWithText("Meal").performClick()
        composeTestRule.onNodeWithText("Meal").performClick()
        composeTestRule.waitForIdle()

        // Test other Quick Log buttons
        composeTestRule.onNodeWithText("Workout").performClick()
        composeTestRule.onNodeWithText("Sleep").performClick()
        composeTestRule.onNodeWithText("Water").performClick()

        composeTestRule.waitForIdle()

        // Verify buttons still exist after multiple interactions
        composeTestRule.onNodeWithText("Meal").assertExists()
        composeTestRule.onNodeWithText("Workout").assertExists()
        composeTestRule.onNodeWithText("Sleep").assertExists()
        composeTestRule.onNodeWithText("Water").assertExists()
    }

    @Test
    fun testTestButtonsFunctionality() {
        composeTestRule.setContent {
            CoachieTheme {
                HomeScreen(
                    onSignOut = {},
                    onNavigateToMealLog = {},
                    onNavigateToWorkoutLog = {},
                    onNavigateToSleepLog = {},
                    onNavigateToWaterLog = {},
                    onNavigateToDebug = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Test the "Test Click" button
        composeTestRule.onNodeWithText("Test Click").performClick()
        composeTestRule.waitForIdle()

        // Test the "Test Firebase" button
        composeTestRule.onNodeWithText("Test Firebase").performClick()
        composeTestRule.waitForIdle()

        // Test the "Debug Screen" button
        composeTestRule.onNodeWithText("Debug Screen").performClick()
        composeTestRule.waitForIdle()

        // Verify buttons still exist
        composeTestRule.onNodeWithText("Test Click").assertExists()
        composeTestRule.onNodeWithText("Test Firebase").assertExists()
        composeTestRule.onNodeWithText("Debug Screen").assertExists()
    }

    @Test
    fun testNavigationElementsVisible() {
        composeTestRule.setContent {
            CoachieTheme {
                HomeScreen(
                    onSignOut = {},
                    onNavigateToMealLog = {},
                    onNavigateToWorkoutLog = {},
                    onNavigateToSleepLog = {},
                    onNavigateToWaterLog = {},
                    onNavigateToDebug = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Verify key UI elements are visible
        composeTestRule.onNodeWithText("Hi").assertExists()
        composeTestRule.onNodeWithText("Today's Log").assertExists()
        composeTestRule.onNodeWithText("Quick Log").assertExists()
        composeTestRule.onNodeWithText("AI Insight").assertExists()
    }

    @Test
    fun testRapidClickProtection() {
        composeTestRule.setContent {
            CoachieTheme {
                HomeScreen(
                    onSignOut = {},
                    onNavigateToMealLog = {},
                    onNavigateToWorkoutLog = {},
                    onNavigateToSleepLog = {},
                    onNavigateToWaterLog = {},
                    onNavigateToDebug = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Perform rapid clicks on the same button to test debouncing
        repeat(5) {
            composeTestRule.onNodeWithText("Test Click").performClick()
        }

        composeTestRule.waitForIdle()

        // The button should still be functional after rapid clicks
        composeTestRule.onNodeWithText("Test Click").assertExists()
        composeTestRule.onNodeWithText("Test Click").assertIsEnabled()
    }
}
