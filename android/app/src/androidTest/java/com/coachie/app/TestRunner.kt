package com.coachie.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Custom test runner for Coachie app UI tests.
 * Ensures proper setup for Compose UI testing.
 */
class TestRunner : AndroidJUnitRunner() {

    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application {
        return super.newApplication(cl, CoachieTestApplication::class.java.name, context)
    }
}

/**
 * Test application class for UI tests.
 * Can be extended to provide test-specific configuration.
 */
class CoachieTestApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize test-specific setup here if needed

        // For example, you could initialize test databases,
        // mock services, or configure test-specific behavior
    }
}
