package com.coachie.app.viewmodel

import org.junit.Assert.*
import org.junit.Test

class WeightStatsTest {

    @Test
    fun `WeightStats formats weight loss change correctly`() {
        // Given
        val stats = WeightStats(
            currentWeight = 75.0,
            previousWeight = 76.0,
            change = -1.0,
            dataPoints = 2
        )

        // Then
        assertEquals("-1.0kg", stats.changeText)
    }

    @Test
    fun `WeightStats formats weight gain change correctly`() {
        // Given
        val stats = WeightStats(
            currentWeight = 75.2,
            previousWeight = 74.5,
            change = 0.7,
            dataPoints = 2
        )

        // Then
        assertEquals("+0.7kg", stats.changeText)
    }

    @Test
    fun `WeightStats shows no change for small differences`() {
        // Given
        val stats = WeightStats(
            currentWeight = 75.02,
            previousWeight = 75.01,
            change = 0.01,
            dataPoints = 2
        )

        // Then
        assertEquals("No change", stats.changeText)
    }

    @Test
    fun `WeightStats shows no change for zero change`() {
        // Given
        val stats = WeightStats(
            currentWeight = 75.0,
            previousWeight = 75.0,
            change = 0.0,
            dataPoints = 2
        )

        // Then
        assertEquals("No change", stats.changeText)
    }

    @Test
    fun `WeightStats handles single data point correctly`() {
        // Given
        val stats = WeightStats(
            currentWeight = 75.0,
            previousWeight = null,
            change = 0.0,
            dataPoints = 1
        )

        // Then
        assertEquals(75.0, stats.currentWeight)
        assertNull(stats.previousWeight)
        assertEquals(0.0, stats.change, 0.0)
        assertEquals("No change", stats.changeText)
    }

    @Test
    fun `WeightStats handles decimal precision correctly`() {
        // Given
        val stats = WeightStats(
            currentWeight = 74.85,
            previousWeight = 75.23,
            change = -0.38,
            dataPoints = 2
        )

        // Then
        assertEquals("-0.4kg", stats.changeText) // Should round to 1 decimal place
    }
}
