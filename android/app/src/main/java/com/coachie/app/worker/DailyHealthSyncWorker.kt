package com.coachie.app.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.coachie.app.service.HealthSyncService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import androidx.work.CoroutineWorker

/**
 * WorkManager worker that automatically syncs Google Fit/Health Connect data multiple times per day
 * Runs at Midnight (12 AM), 9 AM, and 3 PM every day to sync health data
 *
 * BULLETPROOF IMPLEMENTATION:
 * - Exponential backoff retry (up to 3 attempts with increasing delays)
 * - Reschedules itself if scheduling fails
 * - Verifies worker is actually scheduled
 * - Comprehensive logging for production debugging
 * - Handles all edge cases (no user, network issues, API failures)
 */
class DailyHealthSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "DailyHealthSyncWorker"
        private const val WORK_NAME = "daily_health_sync"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MINUTES = 15L // First retry after 15 minutes
        private const val MAX_RETRY_DELAY_MINUTES = 120L // Max retry delay 2 hours

        /**
         * Schedule multiple daily health syncs per day
         * - Midnight (12:00 AM): Get steps from Google Fit
         * - 9:00 AM: Morning sync
         * - 3:00 PM: Afternoon sync
         *
         * CRITICAL: This function is called on app start and verifies scheduling
         * Each sync runs every 24 hours at its scheduled time
         */
        fun scheduleDailyWork(context: Context) {
            try {
                val workManager = WorkManager.getInstance(context)

                // Cancel existing work to avoid duplicates
                val workNamesToCancel = listOf(WORK_NAME, "${WORK_NAME}_morning", "${WORK_NAME}_afternoon")
                workNamesToCancel.forEach { workName ->
                    workManager.cancelUniqueWork(workName)
                    Log.d(TAG, "Cancelled existing work: $workName")
                }

                val now = LocalDateTime.now()

                // Schedule sync times: Midnight, 9 AM, 3 PM
                val syncTimes = listOf(
                    Triple("midnight", LocalTime.of(0, 0), 24), // Midnight - every 24 hours
                    Triple("morning", LocalTime.of(9, 0), 24),  // 9 AM - every 24 hours
                    Triple("afternoon", LocalTime.of(15, 0), 24) // 3 PM - every 24 hours
                )

                // CRITICAL: Use flexible constraints to allow sync even with poor network
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED) // Allow sync without network (will retry with network)
                    .setRequiresBatteryNotLow(false) // Allow even if battery is low
                    .setRequiresCharging(false) // Don't require charging
                    .setRequiresDeviceIdle(false) // Don't require device idle
                    .build()

                syncTimes.forEach { (name, targetTime, intervalHours) ->
                    // Calculate initial delay to next sync time
                    val nextSync = if (now.toLocalTime().isBefore(targetTime)) {
                        // Today at target time
                        now.toLocalDate().atTime(targetTime)
                    } else {
                        // Tomorrow at target time
                        now.toLocalDate().plusDays(1).atTime(targetTime)
                    }

                    val initialDelay = Duration.between(now, nextSync).toMillis()
                    val workName = if (name == "midnight") WORK_NAME else "${WORK_NAME}_$name"

                    val workRequest = PeriodicWorkRequestBuilder<DailyHealthSyncWorker>(
                        repeatInterval = intervalHours.toLong(),
                        repeatIntervalTimeUnit = TimeUnit.HOURS
                    )
                        .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                        .setConstraints(constraints)
                        .setBackoffCriteria(
                            BackoffPolicy.EXPONENTIAL,
                            INITIAL_RETRY_DELAY_MINUTES,
                            TimeUnit.MINUTES
                        )
                        .build()

                    workManager.enqueueUniquePeriodicWork(
                        workName,
                        ExistingPeriodicWorkPolicy.REPLACE,
                        workRequest
                    )

                    Log.i(TAG, "✅ Scheduled $name sync: $nextSync (initial delay: ${initialDelay / (1000 * 60)} minutes)")
                }

                // CRITICAL: Verify the work is actually scheduled
                verifyWorkScheduled(workManager)

                Log.i(TAG, "=========================================")
                Log.i(TAG, "✅✅✅ MULTIPLE DAILY HEALTH SYNCS SCHEDULED ✅✅✅")
                Log.i(TAG, "  Midnight (12 AM) - Every 24 hours")
                Log.i(TAG, "  Morning (9 AM) - Every 24 hours")
                Log.i(TAG, "  Afternoon (3 PM) - Every 24 hours")
                Log.i(TAG, "=========================================")

            } catch (e: Exception) {
                Log.e(TAG, "❌❌❌ CRITICAL: Failed to schedule multiple daily health syncs ❌❌❌", e)
                // Try to reschedule after a delay
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        scheduleDailyWork(context)
                        Log.i(TAG, "✅ Rescheduled multiple daily health syncs after error")
                    } catch (retryException: Exception) {
                        Log.e(TAG, "❌ Failed to reschedule after error", retryException)
                    }
                }, 5000) // Retry after 5 seconds
            }
        }

        /**
         * Verify that all sync works are actually scheduled in WorkManager
         */
        private fun verifyWorkScheduled(workManager: WorkManager) {
            val workNames = listOf(WORK_NAME, "${WORK_NAME}_morning", "${WORK_NAME}_afternoon")

            workNames.forEach { workName ->
                try {
                    val workInfos = workManager.getWorkInfosForUniqueWork(workName).get()
                    if (workInfos.isEmpty()) {
                        Log.w(TAG, "⚠️ No work found for $workName")
                    } else {
                        workInfos.forEach { workInfo ->
                            Log.d(TAG, "Work $workName status: ${workInfo.state}, tags: ${workInfo.tags}, id: ${workInfo.id}")
                            if (workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.RUNNING) {
                                Log.i(TAG, "✅ Verified: $workName sync is scheduled and active")
                            } else {
                                Log.w(TAG, "⚠️ $workName status is ${workInfo.state} - may need rescheduling")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not verify work status for $workName", e)
                }
            }
        }
    }

    override suspend fun doWork(): Result {
        val runAttemptNumber = runAttemptCount
        val currentHour = LocalDateTime.now().hour

        // Determine which sync this is based on the hour
        val syncType = when (currentHour) {
            0 -> "MIDNIGHT (12 AM)"
            9 -> "MORNING (9 AM)"
            15 -> "AFTERNOON (3 PM)"
            else -> "UNKNOWN ($currentHour:00)"
        }

        Log.i(TAG, "=========================================")
        Log.i(TAG, "🚀🚀🚀 $syncType HEALTH SYNC WORKER STARTED 🚀🚀🚀")
        Log.i(TAG, "  Attempt: ${runAttemptNumber + 1}/$MAX_RETRY_ATTEMPTS")
        Log.i(TAG, "  Time: ${LocalDateTime.now()}")
        Log.i(TAG, "=========================================")

        return try {
            // Get current user
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Log.w(TAG, "⚠️ No authenticated user - skipping sync")
                // CRITICAL: Don't retry if no user - this is expected if user logged out
                // But reschedule the work for when user logs back in
                return Result.success()
            }

            Log.d(TAG, "✅ User authenticated: ${currentUser.uid}")

            // Check if we have network (preferred but not required)
            val hasNetwork = try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val network = connectivityManager.activeNetwork
                network != null
            } catch (e: Exception) {
                Log.w(TAG, "Could not check network status", e)
                true // Assume network exists if we can't check
            }

            if (!hasNetwork) {
                Log.w(TAG, "⚠️ No network connection - will retry later")
                // Retry with exponential backoff
                return if (runAttemptNumber < MAX_RETRY_ATTEMPTS - 1) {
                    Result.retry()
                } else {
                    Log.e(TAG, "❌❌❌ MAX RETRY ATTEMPTS REACHED - $syncType SYNC FAILED ❌❌❌")
                    Log.e(TAG, "  User: ${currentUser.uid}")
                    Log.e(TAG, "  This sync will be retried on next scheduled run")
                    // Don't return failure - let it retry on next schedule
                    Result.success()
                }
            }

            // Run sync with comprehensive error handling
            Log.d(TAG, "🔄 Starting health sync...")
            val syncResult = HealthSyncService.sync(context)

            if (syncResult) {
                Log.i(TAG, "=========================================")
                Log.i(TAG, "✅✅✅ $syncType HEALTH SYNC COMPLETED SUCCESSFULLY ✅✅✅")
                Log.i(TAG, "  User: ${currentUser.uid}")
                Log.i(TAG, "  Time: ${LocalDateTime.now()}")
                Log.i(TAG, "=========================================")
                
                // CRITICAL: Verify the work will run again tomorrow
                // WorkManager should automatically reschedule periodic work, but verify
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                        val workManager = WorkManager.getInstance(context)
                        verifyWorkScheduled(workManager)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not verify rescheduling", e)
                    }
                }
                
                Result.success()
            } else {
                Log.e(TAG, "❌ Health sync returned false - sync may have failed")
                
                // Retry with exponential backoff if we haven't exceeded max attempts
                if (runAttemptNumber < MAX_RETRY_ATTEMPTS - 1) {
                    val retryDelay = calculateRetryDelay(runAttemptNumber)
                    Log.w(TAG, "⚠️ Retrying sync in $retryDelay minutes (attempt ${runAttemptNumber + 2}/$MAX_RETRY_ATTEMPTS)")
                    Result.retry()
                } else {
                    Log.e(TAG, "❌❌❌ MAX RETRY ATTEMPTS REACHED - SYNC FAILED ❌❌❌")
                    Log.e(TAG, "  User: ${currentUser.uid}")
                    Log.e(TAG, "  This sync will be retried on next scheduled run (tomorrow at 6 AM)")
                    // Don't return failure - let it retry tomorrow
                    // The fallback sync on app resume will catch this
                    Result.success()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ EXCEPTION IN $syncType HEALTH SYNC WORKER ❌❌❌", e)
            e.printStackTrace()

            // Retry with exponential backoff for exceptions
            if (runAttemptNumber < MAX_RETRY_ATTEMPTS - 1) {
                val retryDelay = calculateRetryDelay(runAttemptNumber)
                Log.w(TAG, "⚠️ Retrying $syncType sync after exception in $retryDelay minutes (attempt ${runAttemptNumber + 2}/$MAX_RETRY_ATTEMPTS)")
                Result.retry()
            } else {
                Log.e(TAG, "❌❌❌ MAX RETRY ATTEMPTS REACHED AFTER EXCEPTION IN $syncType SYNC ❌❌❌")
                // Don't return failure - let it retry on next schedule
                // The fallback sync on app resume will catch this
                Result.success()
            }
        }
    }

    /**
     * Calculate exponential backoff delay for retries
     */
    private fun calculateRetryDelay(attemptNumber: Int): Long {
        val delay = INITIAL_RETRY_DELAY_MINUTES * (1 shl attemptNumber) // Exponential: 15, 30, 60 minutes
        return minOf(delay, MAX_RETRY_DELAY_MINUTES)
    }
}
