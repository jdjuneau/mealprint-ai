package com.coachie.app.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.mealprint.ai.service.DailyMindfulSessionGenerator
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that generates daily mindful sessions at 8 AM
 */
class DailyMindfulSessionWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    companion object {
        private const val TAG = "DailyMindfulSessionWorker"
        private const val WORK_NAME = "daily_mindful_session_generation"

        /**
         * Schedule daily mindful session generation at 8 AM
         */
        fun scheduleDailyWork(context: Context) {
            val workManager = WorkManager.getInstance(context)

            // Cancel existing work
            workManager.cancelUniqueWork(WORK_NAME)

            // Calculate initial delay to next 8 AM
            val now = LocalDateTime.now()
            val next8AM = if (now.toLocalTime().isBefore(LocalTime.of(8, 0))) {
                // Today at 8 AM
                now.toLocalDate().atTime(8, 0)
            } else {
                // Tomorrow at 8 AM
                now.toLocalDate().plusDays(1).atTime(8, 0)
            }

            val initialDelay = Duration.between(now, next8AM).toMillis()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // Need internet for OpenAI
                .build()

            val workRequest = PeriodicWorkRequestBuilder<DailyMindfulSessionWorker>(
                repeatInterval = 24, // Every 24 hours
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

            Log.d(TAG, "Scheduled daily mindful session generation starting at ${next8AM}")
        }
    }

    override fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting daily mindful session generation")

            // Get current user
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Log.w(TAG, "No authenticated user, skipping session generation")
                return Result.failure()
            }

            // Generate session for current user
            val generator = DailyMindfulSessionGenerator(context)
            val result = runBlocking {
                generator.generateTodaysSession(currentUser.uid)
            }

            if (result.isSuccess) {
                Log.d(TAG, "Successfully generated daily mindful session for user: ${currentUser.uid}")
                Result.success()
            } else {
                Log.e(TAG, "Failed to generate daily mindful session", result.exceptionOrNull())
                Result.retry() // Retry on failure
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in daily mindful session worker", e)
            Result.retry()
        }
    }
}
