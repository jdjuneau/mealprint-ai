package com.coachie.app.data

import com.coachie.app.data.model.Badge
import com.coachie.app.data.model.Streak
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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
class StreakServiceTest {

    @Mock
    private lateinit var mockFirestore: FirebaseFirestore

    private lateinit var streakService: StreakService

    private val testUid = "test_user"
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        streakService = StreakService(mockFirestore)
    }

    @Test
    fun `calculateUpdatedStreak creates first streak correctly`() {
        // Given
        val emptyStreak = Streak.create(testUid)
        val today = LocalDate.now().format(dateFormatter)

        // When
        val result = streakService.calculateUpdatedStreak(emptyStreak, today)

        // Then
        assertEquals(1, result.currentStreak)
        assertEquals(1, result.longestStreak)
        assertEquals(today, result.lastLogDate)
        assertEquals(today, result.streakStartDate)
        assertEquals(1, result.totalLogs)
        assertEquals(testUid, result.uid)
    }

    @Test
    fun `calculateUpdatedStreak continues streak for yesterday log`() {
        // Given
        val yesterday = LocalDate.now().minusDays(1).format(dateFormatter)
        val existingStreak = Streak(
            uid = testUid,
            currentStreak = 3,
            longestStreak = 5,
            lastLogDate = yesterday,
            streakStartDate = LocalDate.now().minusDays(3).format(dateFormatter),
            totalLogs = 10
        )

        val today = LocalDate.now().format(dateFormatter)

        // When
        val result = streakService.calculateUpdatedStreak(existingStreak, today)

        // Then
        assertEquals(4, result.currentStreak) // Increased by 1
        assertEquals(5, result.longestStreak) // Longest remains 5
        assertEquals(today, result.lastLogDate)
        assertEquals(11, result.totalLogs) // Increased by 1
    }

    @Test
    fun `calculateUpdatedStreak extends longest streak when current exceeds it`() {
        // Given
        val yesterday = LocalDate.now().minusDays(1).format(dateFormatter)
        val existingStreak = Streak(
            uid = testUid,
            currentStreak = 4,
            longestStreak = 4,
            lastLogDate = yesterday,
            totalLogs = 10
        )

        val today = LocalDate.now().format(dateFormatter)

        // When
        val result = streakService.calculateUpdatedStreak(existingStreak, today)

        // Then
        assertEquals(5, result.currentStreak)
        assertEquals(5, result.longestStreak) // Updated to match current
    }

    @Test
    fun `calculateUpdatedStreak handles same day logging correctly`() {
        // Given
        val today = LocalDate.now().format(dateFormatter)
        val existingStreak = Streak(
            uid = testUid,
            currentStreak = 3,
            longestStreak = 5,
            lastLogDate = today,
            totalLogs = 10
        )

        // When - Log again today
        val result = streakService.calculateUpdatedStreak(existingStreak, today)

        // Then - Should not change streak, just update timestamp
        assertEquals(3, result.currentStreak)
        assertEquals(5, result.longestStreak)
        assertEquals(today, result.lastLogDate)
        assertEquals(10, result.totalLogs) // No increase
    }

    @Test
    fun `calculateUpdatedStreak breaks streak for gap longer than 1 day`() {
        // Given
        val threeDaysAgo = LocalDate.now().minusDays(3).format(dateFormatter)
        val existingStreak = Streak(
            uid = testUid,
            currentStreak = 5,
            longestStreak = 7,
            lastLogDate = threeDaysAgo,
            totalLogs = 15
        )

        val today = LocalDate.now().format(dateFormatter)

        // When
        val result = streakService.calculateUpdatedStreak(existingStreak, today)

        // Then - Streak should reset
        assertEquals(1, result.currentStreak)
        assertEquals(7, result.longestStreak) // Longest preserved
        assertEquals(today, result.lastLogDate)
        assertEquals(today, result.streakStartDate) // New start date
        assertEquals(16, result.totalLogs) // Still increases total
    }

    @Test
    fun `calculateUpdatedStreak handles two day gap correctly`() {
        // Given
        val twoDaysAgo = LocalDate.now().minusDays(2).format(dateFormatter)
        val existingStreak = Streak(
            uid = testUid,
            currentStreak = 4,
            longestStreak = 6,
            lastLogDate = twoDaysAgo,
            totalLogs = 12
        )

        val today = LocalDate.now().format(dateFormatter)

        // When
        val result = streakService.calculateUpdatedStreak(existingStreak, today)

        // Then - Streak should reset
        assertEquals(1, result.currentStreak)
        assertEquals(6, result.longestStreak)
        assertEquals(today, result.lastLogDate)
        assertEquals(13, result.totalLogs)
    }

    @Test
    fun `calculateUpdatedStreak works correctly after streak reset`() {
        // Given - Fresh start after reset
        val existingStreak = Streak(
            uid = testUid,
            currentStreak = 1,
            longestStreak = 10, // From previous long streak
            lastLogDate = LocalDate.now().minusDays(5).format(dateFormatter), // Gap
            totalLogs = 20
        )

        val today = LocalDate.now().format(dateFormatter)

        // When - Log today (after gap)
        val result = streakService.calculateUpdatedStreak(existingStreak, today)

        // Then
        assertEquals(1, result.currentStreak) // New streak starts
        assertEquals(10, result.longestStreak) // Personal best preserved
        assertEquals(today, result.lastLogDate)
        assertEquals(21, result.totalLogs)
    }

    @Test
    fun `calculateUpdatedStreak handles edge case of exact yesterday timing`() {
        // Given
        val yesterday = LocalDate.now().minusDays(1).format(dateFormatter)
        val existingStreak = Streak(
            uid = testUid,
            currentStreak = 2,
            longestStreak = 2,
            lastLogDate = yesterday,
            totalLogs = 5
        )

        val today = LocalDate.now().format(dateFormatter)

        // When - Log exactly one day later
        val result = streakService.calculateUpdatedStreak(existingStreak, today)

        // Then - Should continue streak
        assertEquals(3, result.currentStreak)
        assertEquals(3, result.longestStreak)
        assertEquals(today, result.lastLogDate)
        assertEquals(6, result.totalLogs)
    }

    @Test
    fun `Streak isActive returns true for recent logs`() {
        // Test active streak (logged yesterday)
        val yesterday = LocalDate.now().minusDays(1).format(dateFormatter)
        val activeStreak = Streak(
            uid = testUid,
            currentStreak = 3,
            lastLogDate = yesterday
        )

        assertTrue(activeStreak.isActive)
    }

    @Test
    fun `Streak isActive returns true for today logs`() {
        // Test active streak (logged today)
        val today = LocalDate.now().format(dateFormatter)
        val activeStreak = Streak(
            uid = testUid,
            currentStreak = 5,
            lastLogDate = today
        )

        assertTrue(activeStreak.isActive)
    }

    @Test
    fun `Streak isActive returns false for old logs`() {
        // Test inactive streak (logged 3 days ago)
        val threeDaysAgo = LocalDate.now().minusDays(3).format(dateFormatter)
        val inactiveStreak = Streak(
            uid = testUid,
            currentStreak = 2,
            lastLogDate = threeDaysAgo
        )

        assertFalse(inactiveStreak.isActive)
    }

    @Test
    fun `Streak isActive returns false for empty streak`() {
        // Test empty streak
        val emptyStreak = Streak.create(testUid)
        assertFalse(emptyStreak.isActive)
    }

    @Test
    fun `Streak statusMessage returns correct messages`() {
        // Test various streak messages
        val zeroStreak = Streak.create(testUid).copy(currentStreak = 0)
        assertEquals("Start your streak today!", zeroStreak.statusMessage)

        val singleStreak = zeroStreak.copy(currentStreak = 1)
        assertEquals("1 day logged. Keep it up!", singleStreak.statusMessage)

        val mediumStreak = zeroStreak.copy(currentStreak = 3)
        assertEquals("3 days in a row!", mediumStreak.statusMessage)

        val longStreak = zeroStreak.copy(currentStreak = 7)
        assertEquals("7 day streak! 🔥", longStreak.statusMessage)
    }

    @Test
    fun `Streak daysSinceLastLog calculates correctly`() {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val threeDaysAgo = today.minusDays(3)

        val recentStreak = Streak(
            uid = testUid,
            lastLogDate = yesterday.format(dateFormatter)
        )

        val oldStreak = Streak(
            uid = testUid,
            lastLogDate = threeDaysAgo.format(dateFormatter)
        )

        val emptyStreak = Streak.create(testUid)

        assertEquals(1, recentStreak.daysSinceLastLog)
        assertEquals(3, oldStreak.daysSinceLastLog)
        assertEquals(Int.MAX_VALUE, emptyStreak.daysSinceLastLog)
    }

    @Test
    fun `Badge constants are defined correctly`() {
        // Test badge type constants
        assertEquals("three_day_hero", Badge.TYPE_THREE_DAY_HERO)
        assertEquals("week_warrior", Badge.TYPE_WEEK_WARRIOR)
        assertEquals("scan_star", Badge.TYPE_SCAN_STAR)

        // Test badge templates
        assertEquals("3-Day Hero", Badge.THREE_DAY_HERO.name)
        assertEquals("Week Warrior", Badge.WEEK_WARRIOR.name)
        assertEquals("Scan Star", Badge.SCAN_STAR.name)
    }

    @Test
    fun `Badge createEarnedBadge creates correct badge instance`() {
        val earnedBadge = Badge.createEarnedBadge(testUid, Badge.TYPE_THREE_DAY_HERO)

        assertEquals(testUid, earnedBadge.uid)
        assertEquals(Badge.TYPE_THREE_DAY_HERO, earnedBadge.type)
        assertEquals("3-Day Hero", earnedBadge.name)
        assertEquals("badge_hero", earnedBadge.icon)
        assertTrue(earnedBadge.isNew) // Should be true for newly earned badges
        assertTrue(earnedBadge.earnedDate > 0)
    }

    // Note: Integration tests for badge awarding would require mocking Firestore
    // and are better suited for integration tests rather than unit tests
}
