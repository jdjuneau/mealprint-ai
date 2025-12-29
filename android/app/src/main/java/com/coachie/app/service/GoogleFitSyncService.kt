package com.coachie.app.service

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.health.GoogleFitService
import com.coachie.app.data.health.HealthConnectService
import com.coachie.app.data.local.PreferencesManager
import com.coachie.app.data.model.DailyLog
import com.coachie.app.data.model.HealthLog
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object HealthSyncService {
    private const val TAG = "HEALTH_SYNC_DEBUG"
    private const val MAX_RETRIES = 3
    private const val INITIAL_RETRY_DELAY_MS = 1000L
    
    // Mutex to prevent concurrent sync calls
    private val syncMutex = kotlinx.coroutines.sync.Mutex()
    @Volatile
    private var isSyncing = false
    
    /**
     * Bulletproof sync with retry logic, expanded sleep query, and validation
     */
    suspend fun sync(context: Context, retryCount: Int = 0): Boolean {
        // Prevent concurrent sync calls
        if (isSyncing && retryCount == 0) {
            Log.w(TAG, "⚠️ Sync already in progress, skipping duplicate call")
            return false
        }
        
        return syncMutex.withLock {
            if (isSyncing && retryCount == 0) {
                Log.w(TAG, "⚠️ Sync already in progress, skipping duplicate call")
                return@withLock false
            }
            
            isSyncing = true
            try {
                syncInternal(context, retryCount)
            } finally {
                isSyncing = false
            }
        }
    }
    
    private suspend fun syncInternal(context: Context, retryCount: Int = 0): Boolean {
        Log.d(TAG, "=========================================")
        Log.d(TAG, "🚀🚀🚀 HEALTH SYNC STARTED (attempt ${retryCount + 1}/$MAX_RETRIES) 🚀🚀🚀")
        Log.d(TAG, "  Timestamp: ${java.time.Instant.now()}")
        Log.d(TAG, "  Context: ${context.javaClass.simpleName}")
        Log.d(TAG, "=========================================")
        
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "❌❌❌ CRITICAL ERROR: No user ID - cannot sync ❌❌❌")
            android.util.Log.e(TAG, "Stack trace:", Exception("No user ID in sync"))
            return false
        }
        Log.d(TAG, "✅ User ID: $userId")
        
        // Initialize repository early so it's available throughout the function
        val repository = FirebaseRepository.getInstance()
        
        // CRITICAL: Use system timezone consistently for date calculations
        // This ensures consecutive day syncs work correctly regardless of timezone changes
        val today = LocalDate.now(ZoneId.systemDefault())
        val yesterday = today.minusDays(1)
        Log.d(TAG, "📅 Date calculation: today=$today, yesterday=$yesterday, timezone=${ZoneId.systemDefault()}")
        val now = java.time.Instant.now()
        val currentTime = now.atZone(ZoneId.systemDefault())
        
        // For today's data (steps, calories, workouts)
        // CRITICAL: Google Fit only finalizes steps at 11:59 PM, so if syncing during the day,
        // query up to current time, not end of day (steps won't be available until 11:59 PM)
        val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = today.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // For steps: Use current time if before 11:59 PM, otherwise use end of day
        // Google Fit finalizes steps at 11:59 PM, so querying end of day before that time returns 0
        val stepsQueryEnd = if (currentTime.hour == 23 && currentTime.minute >= 59) {
            endOfDay // After 11:59 PM, use end of day
        } else {
            now.toEpochMilli() // Before 11:59 PM, use current time
        }
        
        // For sleep: 
        // - HealthConnect needs expanded search window (it filters directly)
        // - GoogleFit needs target day boundaries (it handles search window internally)
        val sleepQueryStart = yesterday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - (12 * 60 * 60 * 1000) // 12 hours before yesterday
        val sleepQueryEnd = today.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + (12 * 60 * 60 * 1000) // 12 hours after today
        // CRITICAL FIX: Use TODAY's start as the target date start
        // readSleep() uses targetDateStart to determine which date to filter for (sleep ending on that date)
        // We want sleep ending on TODAY, so we must pass today's start, not yesterday's
        // The readSleep() function already expands the search window internally (72 hours before/after),
        // so it will still catch sleep that started yesterday but ends today
        val sleepTargetStart = startOfDay // Use TODAY's start - this determines the target date for filtering
        val sleepTargetEnd = endOfDay // Target day end for GoogleFit (sleep belongs to day it ends on)
        // CRITICAL FIX: Query YESTERDAY + TODAY with buffer to catch late-syncing workouts
        // Google Fit can sync workouts hours or even days late, so we need a wider window
        // We'll filter to only save TODAY's workouts, but query wider to catch late syncs
        val yesterdayStart = yesterday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val workoutQueryStart = yesterdayStart // Start from yesterday to catch late-syncing workouts
        val workoutQueryEnd = if (currentTime.hour == 23 && currentTime.minute >= 59) {
            endOfDay + (60 * 60 * 1000) // 1 hour after end of day if after 11:59 PM
        } else {
            now.toEpochMilli() + (60 * 60 * 1000) // 1 hour after current time
        }
        val dateString = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        
        Log.d(TAG, "Date: $dateString")
        Log.d(TAG, "Today time range: $startOfDay to $endOfDay")
        Log.d(TAG, "Steps query end: ${java.time.Instant.ofEpochMilli(stepsQueryEnd).atZone(ZoneId.systemDefault())} (Google Fit finalizes at 11:59 PM)")
        Log.d(TAG, "Workout query range: $workoutQueryStart to $workoutQueryEnd (YESTERDAY + TODAY with buffer to catch late-syncing workouts)")
        Log.d(TAG, "Sleep query range (HealthConnect): $sleepQueryStart to $sleepQueryEnd")
        Log.d(TAG, "Sleep target day (GoogleFit): $sleepTargetStart to $sleepTargetEnd (includes previous day start to catch sleep starting yesterday)")
        
        var steps = 0
        var calories = 0
        var sleepSessions = emptyList<com.coachie.app.data.health.HealthConnectService.SleepData>()
        var workouts = emptyList<com.coachie.app.data.health.HealthConnectService.WorkoutData>()
        var workoutsFromHealthConnect = false // Track source of workouts
        var syncSuccess = false
        
        try {
            // Try Health Connect first (preferred)
            // CRITICAL: Only try Health Connect if user has explicitly enabled it in Coachie's permissions screen
            // Don't try Health Connect just because it's installed - user must have connected it in Coachie
            Log.d(TAG, "--- Checking Health Connect ---")
            val preferencesManager = PreferencesManager(context)
            val healthConnectEnabled = preferencesManager.healthConnectEnabled
            Log.d(TAG, "Health Connect enabled in Coachie: $healthConnectEnabled")
            
            val healthConnectService = HealthConnectService(context)
            val hcAvailable = healthConnectService.isAvailable()
            Log.d(TAG, "Health Connect available: $hcAvailable")
            var triedHealthConnect = false
            
            // CRITICAL: Only try Health Connect if:
            // 1. User has explicitly enabled it in Coachie's permissions screen (healthConnectEnabled = true)
            // 2. Health Connect is available on the device
            // 3. User has granted permissions
            Log.d(TAG, "Health Connect decision: available=$hcAvailable, enabled=$healthConnectEnabled, will try=${hcAvailable && healthConnectEnabled}")
            if (hcAvailable && healthConnectEnabled) {
                val hcHasPerms = healthConnectService.hasPermissions()
                Log.d(TAG, "Health Connect permissions: $hcHasPerms")
                
                // CRITICAL: Only try Health Connect if user has explicitly granted permissions
                // If no permissions, skip Health Connect entirely and use Google Fit
                if (!hcHasPerms) {
                    Log.d(TAG, "ℹ️ Health Connect enabled in Coachie but permissions not granted")
                    Log.d(TAG, "  Skipping Health Connect - will use Google Fit instead")
                    Log.d(TAG, "  User needs to grant permissions in Health Connect app")
                    // Don't try Health Connect - go straight to Google Fit
                } else {
                    // User has enabled Health Connect in Coachie AND granted permissions
                    Log.d(TAG, "✅ Health Connect enabled by user in Coachie (permissions granted) - will use it for sync")
                    triedHealthConnect = true
                    try {
                        Log.d(TAG, "Reading from Health Connect...")
                        steps = healthConnectService.readSteps(startOfDay, endOfDay)
                        calories = healthConnectService.readCalories(startOfDay, endOfDay)
                        // Use expanded sleep query range to catch overnight sleep
                        sleepSessions = healthConnectService.readSleep(sleepQueryStart, sleepQueryEnd)
                        // Query workouts for YESTERDAY + TODAY to catch late-syncing workouts
                        val hcWorkoutsRaw = healthConnectService.readWorkouts(workoutQueryStart, workoutQueryEnd)
                        // CRITICAL FIX: Save workouts from YESTERDAY and TODAY to their correct dates
                        // This ensures late-syncing workouts from yesterday are caught if yesterday's sync failed
                        val hcWorkoutsToSave = hcWorkoutsRaw.filter { workout ->
                            val workoutDate = java.time.Instant.ofEpochMilli(workout.startTime)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            // Save workouts from yesterday (late syncs) and today
                            workoutDate == yesterday || workoutDate == today
                        }
                        workouts = hcWorkoutsToSave
                        val todayCount = hcWorkoutsToSave.count { 
                            java.time.Instant.ofEpochMilli(it.startTime).atZone(ZoneId.systemDefault()).toLocalDate() == today 
                        }
                        val yesterdayCount = hcWorkoutsToSave.size - todayCount
                        Log.d(TAG, "📊 Health Connect returned ${hcWorkoutsRaw.size} workouts, saving ${hcWorkoutsToSave.size} ($todayCount from today, $yesterdayCount from yesterday - catching late syncs)")
                        workoutsFromHealthConnect = workouts.isNotEmpty()
                        Log.d(TAG, "✅ Synced from Health Connect: steps=$steps, calories=$calories, sleep=${sleepSessions.size} sessions, workouts=${workouts.size}")
                        if (workouts.isNotEmpty()) {
                            workouts.forEach { workout ->
                                Log.d(TAG, "  💪 Workout: ${workout.activityType}, ${workout.durationMin} min, ${workout.caloriesBurned} cal")
                            }
                        } else {
                            Log.w(TAG, "⚠️ Health Connect returned 0 workouts - will try Google Fit as fallback")
                        }
                        syncSuccess = true
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error syncing from Health Connect", e)
                        e.printStackTrace()
                        
                        // Check if it's a permission-related error
                        val errorMessage = e.message ?: ""
                        if (errorMessage.contains("permission") || 
                            errorMessage.contains("Permission") ||
                            errorMessage.contains("SecurityException") ||
                            e is SecurityException) {
                            Log.e(TAG, "⚠️ Health Connect permission error detected")
                            // Removed toast message - Health Connect not fully working yet
                        }
                        
                        // Don't set syncSuccess = true on error, so we fall back to Google Fit
                    }
                }
            }
            
            // Try Google Fit if Health Connect is NOT available, has NO permissions, sync FAILED, or returned 0 for critical metrics
            // CRITICAL: If Health Connect returned 0 for ANY metric (steps/calories/workouts/sleep), try Google Fit as fallback
            Log.d(TAG, "--- Checking Google Fit ---")
            val healthConnectReturnedZeroData = triedHealthConnect && syncSuccess && steps == 0 && calories == 0 && workouts.isEmpty() && sleepSessions.isEmpty()
            val shouldTryGoogleFit = !triedHealthConnect || !syncSuccess || healthConnectReturnedZeroData
            // Try Google Fit if:
            // 1. Health Connect wasn't tried (not available or no permissions), OR
            // 2. Health Connect sync FAILED (exception occurred), OR
            // 3. Health Connect returned 0 for steps/calories/workouts/sleep (need Google Fit fallback)
            Log.d(TAG, "--- Sync decision summary ---")
            Log.d(TAG, "  triedHealthConnect: $triedHealthConnect")
            Log.d(TAG, "  syncSuccess: $syncSuccess")
            Log.d(TAG, "  healthConnectReturnedZeroData: $healthConnectReturnedZeroData")
            Log.d(TAG, "  shouldTryGoogleFit: $shouldTryGoogleFit")

            if (shouldTryGoogleFit) {
                if (triedHealthConnect && !syncSuccess) {
                    Log.w(TAG, "⚠️ Health Connect sync failed - trying Google Fit as fallback")
                } else if (healthConnectReturnedZeroData) {
                    Log.w(TAG, "⚠️ Health Connect returned 0 for steps/calories/workouts/sleep - trying Google Fit as fallback")
                } else if (!triedHealthConnect) {
                    Log.d(TAG, "Health Connect not available or no permissions - using Google Fit")
                }
                val googleFitService = GoogleFitService(context)
                val gfHasPerms = googleFitService.hasPermissions()
                Log.d(TAG, "Google Fit permissions: $gfHasPerms")
                
                // CRITICAL: Check ACTIVITY_RECOGNITION permission (required for Android 10+)
                val hasActivityRecognition = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED
                Log.d(TAG, "ACTIVITY_RECOGNITION permission: $hasActivityRecognition")
                Log.d(TAG, "Google Fit decision: hasPerms=$gfHasPerms, will try=$gfHasPerms")

                if (gfHasPerms) {
                    if (!hasActivityRecognition) {
                        Log.e(TAG, "❌❌❌ MISSING ACTIVITY_RECOGNITION PERMISSION ❌❌❌")
                        Log.e(TAG, "  Google Fit requires ACTIVITY_RECOGNITION permission to read steps and workouts")
                        Log.e(TAG, "  Please grant this permission in Settings > Permissions")
                        
                        // CRITICAL: Inform the user - don't silently fail!
                        showPermissionRequiredMessage(context)
                        
                        // Don't try to read - it will fail with SecurityException
                        return@syncInternal false
                    } else {
                        try {
                            Log.d(TAG, "=========================================")
                            Log.d(TAG, "🔍 DIAGNOSTIC: Checking Google Fit data availability...")
                            Log.d(TAG, "  Today's date: $dateString")
                            Log.d(TAG, "  Today's time range: ${java.time.Instant.ofEpochMilli(startOfDay).atZone(ZoneId.systemDefault())} to ${java.time.Instant.ofEpochMilli(endOfDay).atZone(ZoneId.systemDefault())}")
                            Log.d(TAG, "=========================================")
                            
                            // SIMPLIFIED: Quick 1 second delay for Google Fit API sync
                            if (retryCount == 0) {
                                Log.d(TAG, "⏳ Waiting 1 second before first Google Fit queries to allow API sync...")
                                delay(1000L)
                            }
                            
                            // CRITICAL: Read ALL metrics with aggressive retry logic
                            // Google Fit API is notoriously inconsistent - data visible in app but not returned by API
                            // Apply same aggressive retry strategy to ALL metrics (steps, calories, sleep, workouts)
                            
                            // CRITICAL FIX FOR CONSECUTIVE DAYS: Query BOTH TODAY and YESTERDAY
                            // This ensures late-syncing data from yesterday is caught on day 2, 3, etc.
                            Log.d(TAG, "📊 Reading steps from Google Fit for TODAY and YESTERDAY...")
                            
                            // Query TODAY's steps
                            val todaySteps = googleFitService.readSteps(startOfDay, stepsQueryEnd)
                            Log.d(TAG, "  Today's steps: $todaySteps (query end: ${java.time.Instant.ofEpochMilli(stepsQueryEnd).atZone(ZoneId.systemDefault())})")
                            
                            // Query YESTERDAY's steps (to catch late-syncing data)
                            val yesterdayStart = yesterday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            val yesterdayEnd = yesterday.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            val yesterdaySteps = googleFitService.readSteps(yesterdayStart, yesterdayEnd)
                            Log.d(TAG, "  Yesterday's steps: $yesterdaySteps (late sync check)")
                            
                            // Use today's steps (yesterday's will be saved separately if needed)
                            steps = todaySteps
                            
                            // CRITICAL FIX: Retry if 0 steps (Google Fit might need time to sync)
                            // This is especially important in the morning when step data may not be synced yet
                            if (steps == 0 && retryCount == 0) {
                                val currentHour = currentTime.hour
                                val currentMinute = currentTime.minute
                                Log.w(TAG, "⚠️ Got 0 steps at ${currentHour}:${String.format("%02d", currentMinute)} - waiting 3 seconds for Google Fit to sync...")
                                Log.w(TAG, "     (This is normal if you just started your day - Google Fit may need time to sync)")
                                delay(3000L) // Slightly longer delay for steps
                                steps = googleFitService.readSteps(startOfDay, stepsQueryEnd)
                                if (steps > 0) {
                                    Log.d(TAG, "✅ Steps found after retry: $steps")
                                } else {
                                    Log.w(TAG, "⚠️ Still 0 steps after retry - Google Fit may not have synced yet")
                                    Log.w(TAG, "     ACTION: Open Google Fit app to force sync, then try again")
                                }
                            }
                            
                            // Save yesterday's steps if we found them and they're not already saved
                            if (yesterdaySteps > 0) {
                                val yesterdayDateString = yesterday.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                val existingYesterdayLog = repository.getDailyLog(userId, yesterdayDateString).getOrDefault(null)
                                val existingYesterdaySteps = existingYesterdayLog?.steps ?: 0
                                
                                // Only save if new value is higher (late sync) or no data exists
                                if (yesterdaySteps > existingYesterdaySteps || existingYesterdaySteps == 0) {
                                    Log.d(TAG, "💾 Saving yesterday's late-syncing steps: $yesterdaySteps (was: $existingYesterdaySteps)")
                                    val yesterdayLog = existingYesterdayLog?.copy(
                                        steps = yesterdaySteps,
                                        updatedAt = System.currentTimeMillis()
                                    ) ?: DailyLog(
                                        uid = userId,
                                        date = yesterdayDateString,
                                        steps = yesterdaySteps
                                    )
                                    repository.saveDailyLog(yesterdayLog)
                                    Log.d(TAG, "✅ Saved yesterday's steps: $yesterdaySteps")
                                } else {
                                    Log.d(TAG, "ℹ️ Yesterday's steps already saved ($existingYesterdaySteps), skipping")
                                }
                            }
                            
                            Log.d(TAG, "📊 Reading calories from Google Fit for TODAY and YESTERDAY...")
                            // Query TODAY's calories
                            calories = googleFitService.readCalories(startOfDay, endOfDay)
                            Log.d(TAG, "  Today's calories: $calories")
                            
                            // Query YESTERDAY's calories (to catch late-syncing data)
                            val yesterdayCalories = googleFitService.readCalories(yesterdayStart, yesterdayEnd)
                            Log.d(TAG, "  Yesterday's calories: $yesterdayCalories (late sync check)")
                            
                            // Save yesterday's calories if we found them and they're not already saved
                            if (yesterdayCalories > 0) {
                                val yesterdayDateString = yesterday.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                val existingYesterdayLog = repository.getDailyLog(userId, yesterdayDateString).getOrDefault(null)
                                val existingYesterdayCalories = existingYesterdayLog?.caloriesBurned ?: 0
                                
                                // Only save if new value is higher (late sync) or no data exists
                                if (yesterdayCalories > existingYesterdayCalories || existingYesterdayCalories == 0) {
                                    Log.d(TAG, "💾 Saving yesterday's late-syncing calories: $yesterdayCalories (was: $existingYesterdayCalories)")
                                    val yesterdayLog = existingYesterdayLog?.copy(
                                        caloriesBurned = yesterdayCalories,
                                        updatedAt = System.currentTimeMillis()
                                    ) ?: DailyLog(
                                        uid = userId,
                                        date = yesterdayDateString,
                                        caloriesBurned = yesterdayCalories
                                    )
                                    repository.saveDailyLog(yesterdayLog)
                                    Log.d(TAG, "✅ Saved yesterday's calories: $yesterdayCalories")
                                } else {
                                    Log.d(TAG, "ℹ️ Yesterday's calories already saved ($existingYesterdayCalories), skipping")
                                }
                            }
                            
                            // SIMPLIFIED: One quick retry if 0 calories
                            if (calories == 0 && retryCount == 0) {
                                Log.w(TAG, "⚠️ Got 0 calories - waiting 2 seconds for Google Fit to sync...")
                                delay(2000L)
                                calories = googleFitService.readCalories(startOfDay, endOfDay)
                                if (calories > 0) {
                                    Log.d(TAG, "✅ Calories found after retry: $calories")
                                }
                            }
                            
                            // CRITICAL: Only query sleep from Google Fit if Health Connect didn't return sleep
                            // Sleep is working from Health Connect, so don't override it
                            if (sleepSessions.isEmpty()) {
                                // CRITICAL FIX FOR CONSECUTIVE DAYS: Query sleep for BOTH TODAY and YESTERDAY
                                // This ensures late-syncing sleep from yesterday is caught on day 2, 3, etc.
                                Log.d(TAG, "📊 Reading sleep from Google Fit for TODAY and YESTERDAY...")
                                
                                // Query TODAY's sleep (sleep ending on today)
                                val targetDateForSleep = java.time.Instant.ofEpochMilli(sleepTargetStart)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                Log.d(TAG, "  🎯 TARGET DATE (TODAY): $targetDateForSleep (sleep ending on this date)")
                                Log.d(TAG, "  Target time range: ${java.time.Instant.ofEpochMilli(sleepTargetStart).atZone(ZoneId.systemDefault())} to ${java.time.Instant.ofEpochMilli(sleepTargetEnd).atZone(ZoneId.systemDefault())}")
                                var googleFitSleep = googleFitService.readSleep(sleepTargetStart, sleepTargetEnd)
                                Log.d(TAG, "📊 Google Fit returned ${googleFitSleep.size} sleep sessions for target date $targetDateForSleep")
                                
                                // Query YESTERDAY's sleep (to catch late-syncing data)
                                val yesterdaySleepTargetStart = yesterday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                val yesterdaySleepTargetEnd = yesterday.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                val yesterdaySleepTargetDate = java.time.Instant.ofEpochMilli(yesterdaySleepTargetStart)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                Log.d(TAG, "  🎯 TARGET DATE (YESTERDAY): $yesterdaySleepTargetDate (late sync check)")
                                val yesterdayGoogleFitSleep = googleFitService.readSleep(yesterdaySleepTargetStart, yesterdaySleepTargetEnd)
                                Log.d(TAG, "📊 Google Fit returned ${yesterdayGoogleFitSleep.size} sleep sessions for yesterday (late sync check)")
                                
                                // Process and save yesterday's sleep if found (late sync check)
                                // If found, it's likely "last night's sleep" → save to TODAY
                                if (yesterdayGoogleFitSleep.isNotEmpty()) {
                                    Log.d(TAG, "💾 Processing ${yesterdayGoogleFitSleep.size} sleep sessions from yesterday (late sync) - saving to TODAY")
                                    for (sleepSession in yesterdayGoogleFitSleep) {
                                        val durationHours = (sleepSession.endTime - sleepSession.startTime) / (1000.0 * 60.0 * 60.0)
                                        val durationMinutes = ((sleepSession.endTime - sleepSession.startTime) / (1000.0 * 60.0)).toInt()
                                        
                                        val sleepEndInstant = java.time.Instant.ofEpochMilli(sleepSession.endTime)
                                            .atZone(ZoneId.systemDefault())
                                        val sleepEndDate = sleepEndInstant.toLocalDate()
                                        
                                        // SIMPLE: If we found sleep from yesterday's query, it's "last night's sleep" → save to TODAY
                                        val sleepDate = today
                                        val sleepDateString = sleepDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                        
                                        Log.d(TAG, "💾 Saving sleep to TODAY ($sleepDate): ${String.format("%.1f", durationHours)} hours (${durationMinutes} minutes)")
                                        Log.d(TAG, "  - Start: ${java.time.Instant.ofEpochMilli(sleepSession.startTime).atZone(ZoneId.systemDefault())}")
                                        Log.d(TAG, "  - End: ${java.time.Instant.ofEpochMilli(sleepSession.endTime).atZone(ZoneId.systemDefault())}")
                                        Log.d(TAG, "  - Sleep ends on: $sleepEndDate, saving to: $sleepDate (TODAY - last night's sleep)")
                                        
                                        try {
                                            // Delete any existing Google Fit sleep logs for this date
                                            val existingLogs = repository.getHealthLogs(userId, sleepDateString).getOrDefault(emptyList())
                                            val googleFitSleepLogs = existingLogs.filterIsInstance<HealthLog.SleepLog>()
                                                .filter { it.entryId.startsWith("google_fit_sleep_") }
                                            
                                            for (oldLog in googleFitSleepLogs) {
                                                repository.deleteHealthLog(userId, sleepDateString, oldLog.entryId)
                                                Log.d(TAG, "Deleted old Google Fit sleep log from $sleepDateString: ${oldLog.entryId}")
                                            }
                                            
                                            val entryId = "google_fit_sleep_${sleepDateString}"
                                            val sleepLog = HealthLog.SleepLog(
                                                entryId = entryId,
                                                startTime = sleepSession.startTime,
                                                endTime = sleepSession.endTime,
                                                quality = 3
                                            )
                                            
                                            var saved = false
                                            var attempts = 0
                                            while (!saved && attempts < 5) {
                                                val saveResult = repository.saveHealthLog(userId, sleepDateString, sleepLog)
                                                if (saveResult.isSuccess) {
                                                    saved = true
                                                    Log.d(TAG, "✅ Successfully saved yesterday's late-syncing sleep to $sleepDateString")
                                                } else {
                                                    attempts++
                                                    if (attempts < 5) {
                                                        delay((500 * attempts).toLong())
                                                    } else {
                                                        Log.e(TAG, "❌ Failed to save yesterday's sleep after 5 attempts")
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "❌ Error saving yesterday's sleep log", e)
                                        }
                                    }
                                }
                                
                                // CRITICAL FIX: Retry if 0 sleep (Google Fit might need time to sync)
                                // This is especially important in the morning when sleep data may not be synced yet
                                // readSleep() now has its own internal retry logic (up to 3 attempts with increasing delays)
                                // So we don't need to retry here - just log the result
                                if (googleFitSleep.isEmpty() && sleepSessions.isEmpty()) {
                                    val currentHour = currentTime.hour
                                    val currentMinute = currentTime.minute
                                    Log.w(TAG, "⚠️ Got 0 sleep at ${currentHour}:${String.format("%02d", currentMinute)} for target date $targetDateForSleep")
                                    Log.w(TAG, "     readSleep() already retried internally (up to 3 attempts with increasing delays)")
                                    Log.w(TAG, "     (This is normal if you just woke up - Google Fit may need time to sync)")
                                    Log.w(TAG, "     ACTION: Open Google Fit app to force sync, then try again")
                                } else if (googleFitSleep.isNotEmpty()) {
                                    Log.d(TAG, "✅✅✅ Sleep found: ${googleFitSleep.size} sessions ✅✅✅")
                                }
                            
                            googleFitSleep.forEach { sleep ->
                                val durationHours = (sleep.endTime - sleep.startTime) / (1000.0 * 60.0 * 60.0)
                                Log.d(TAG, "  📍 Sleep: ${String.format("%.1f", durationHours)} hours (${sleep.startTime} to ${sleep.endTime})")
                            }
                            sleepSessions = googleFitSleep.map { 
                                com.coachie.app.data.health.HealthConnectService.SleepData(it.startTime, it.endTime)
                            }
                            } else {
                                Log.d(TAG, "✅ Sleep already synced from Health Connect (${sleepSessions.size} sessions) - skipping Google Fit sleep query")
                            }
                        
                        // CRITICAL: Always query for TODAY's workouts first - this is what users care about
                        // Query a wide range to catch delayed syncs, but prioritize today's workouts
                        Log.d(TAG, "🔍 Calling Google Fit readWorkouts() for TODAY's workouts...")
                        Log.d(TAG, "  Today's range: ${java.time.Instant.ofEpochMilli(startOfDay).atZone(ZoneId.systemDefault())} to ${java.time.Instant.ofEpochMilli(endOfDay).atZone(ZoneId.systemDefault())}")
                        Log.d(TAG, "  Query range: ${java.time.Instant.ofEpochMilli(workoutQueryStart).atZone(ZoneId.systemDefault())} to ${java.time.Instant.ofEpochMilli(workoutQueryEnd).atZone(ZoneId.systemDefault())}")
                        
                        // CRITICAL FIX: Get ALL workouts in query range (no filtering) - we'll save them all to their correct dates
                        // This ensures TODAY's workouts are ALWAYS found, even if they synced late
                        // Use mutable list so we can update it during retries
                        val googleFitWorkoutsRaw = googleFitService.readWorkouts(workoutQueryStart, workoutQueryEnd, null, null).toMutableList()
                        Log.d(TAG, "📊 Google Fit returned ${googleFitWorkoutsRaw.size} workouts total")
                        
                        // Separate today's workouts from older ones for logging
                        val todayWorkouts = googleFitWorkoutsRaw.filter { workout ->
                            val workoutDate = java.time.Instant.ofEpochMilli(workout.startTime)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            workoutDate == today
                        }
                        
                        if (todayWorkouts.isNotEmpty()) {
                            Log.d(TAG, "✅✅✅ FOUND ${todayWorkouts.size} WORKOUTS FOR TODAY ✅✅✅")
                            todayWorkouts.forEachIndexed { index, workout ->
                                val workoutTimeStr = java.time.Instant.ofEpochMilli(workout.startTime).atZone(ZoneId.systemDefault())
                                Log.d(TAG, "  ${index + 1}. 💪 TODAY: ${workout.activityType} - ${workout.durationMin} min, ${workout.caloriesBurned} cal at $workoutTimeStr")
                            }
                        } else {
                            Log.w(TAG, "⚠️⚠️⚠️ NO WORKOUTS FOUND FOR TODAY ⚠️⚠️⚠️")
                            Log.w(TAG, "  This could mean:")
                            Log.w(TAG, "    1. No workout was done today yet")
                            Log.w(TAG, "    2. Google Fit hasn't synced today's workout yet (can take hours)")
                            Log.w(TAG, "    3. Workout app isn't syncing to Google Fit")
                        }
                        
                        // Log all workouts found (including older ones that synced late)
                        if (googleFitWorkoutsRaw.isNotEmpty()) {
                            val olderWorkouts = googleFitWorkoutsRaw.filter { workout ->
                                val workoutDate = java.time.Instant.ofEpochMilli(workout.startTime)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                workoutDate != today
                            }
                            if (olderWorkouts.isNotEmpty()) {
                                Log.d(TAG, "📋 Also found ${olderWorkouts.size} workouts from previous days (will save to their dates):")
                                olderWorkouts.forEachIndexed { index, workout ->
                                    val workoutDate = java.time.Instant.ofEpochMilli(workout.startTime)
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate()
                                    val workoutTimeStr = java.time.Instant.ofEpochMilli(workout.startTime).atZone(ZoneId.systemDefault())
                                    Log.d(TAG, "  ${index + 1}. ${workoutDate}: ${workout.activityType} - ${workout.durationMin} min at $workoutTimeStr")
                                }
                            }
                        }
                        
                        // SIMPLIFIED: No aggressive retries - if workouts aren't found, they're not in Google Fit yet
                        // User can manually sync again later if needed
                        if (todayWorkouts.isEmpty() && googleFitWorkoutsRaw.isEmpty()) {
                            Log.w(TAG, "⚠️ No workouts found for today - check Google Fit app to ensure workouts are synced")
                        }
                        
                        // CRITICAL FIX: Save workouts from YESTERDAY and TODAY to their correct dates
                        // This ensures late-syncing workouts from yesterday are caught if yesterday's sync failed
                        // We'll save all workouts in the query range (yesterday + today) to their correct dates
                        val googleFitWorkouts = googleFitWorkoutsRaw.filter { workout ->
                            val workoutDate = java.time.Instant.ofEpochMilli(workout.startTime)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            // Save workouts from yesterday (late syncs) and today
                            workoutDate == yesterday || workoutDate == today
                        }
                        val todayCount = googleFitWorkouts.count { 
                            java.time.Instant.ofEpochMilli(it.startTime).atZone(ZoneId.systemDefault()).toLocalDate() == today 
                        }
                        val yesterdayCount = googleFitWorkouts.size - todayCount
                        Log.d(TAG, "📊 Will save ${googleFitWorkouts.size} workouts ($todayCount from today, $yesterdayCount from yesterday - catching late syncs)")
                        
                        if (googleFitWorkouts.isNotEmpty()) {
                            Log.d(TAG, "✅✅✅ GOOGLE FIT FOUND ${googleFitWorkouts.size} WORKOUTS ✅✅✅")
                            googleFitWorkouts.forEachIndexed { index, workout ->
                                val workoutTimeStr = java.time.Instant.ofEpochMilli(workout.startTime).atZone(ZoneId.systemDefault())
                                Log.d(TAG, "  ${index + 1}. 💪 ${workout.activityType} - ${workout.durationMin} min, ${workout.caloriesBurned} cal")
                                Log.d(TAG, "     Time: $workoutTimeStr")
                            }
                        } else {
                            Log.e(TAG, "❌❌❌ GOOGLE FIT RETURNED 0 WORKOUTS ❌❌❌")
                            Log.e(TAG, "  This means either:")
                            Log.e(TAG, "    1. No workouts were logged in Google Fit for this time range")
                            Log.e(TAG, "    2. Google Fit API is not returning data (permissions issue?)")
                            Log.e(TAG, "    3. Workout app is not syncing to Google Fit")
                            Log.e(TAG, "    4. API sync delay - data may not be available yet")
                        }
                        // CRITICAL: If Health Connect was used and returned workouts, keep those
                        // Only use Google Fit workouts if Health Connect wasn't used or returned 0
                        if (workoutsFromHealthConnect && workouts.isNotEmpty()) {
                            // User has Health Connect enabled - use Health Connect data, ignore Google Fit
                            Log.d(TAG, "✅ Using workouts from Health Connect (${workouts.size}) - user has Health Connect enabled")
                        if (googleFitWorkouts.isNotEmpty()) {
                                Log.d(TAG, "  (Google Fit also found ${googleFitWorkouts.size} workouts, but ignoring since Health Connect is enabled)")
                            }
                        } else if (googleFitWorkouts.isNotEmpty()) {
                            // Health Connect not used or returned 0 - use Google Fit workouts
                            workouts = googleFitWorkouts.map {
                                com.coachie.app.data.health.HealthConnectService.WorkoutData(it.activityType, it.durationMin, it.caloriesBurned, it.startTime)
                            }
                            workoutsFromHealthConnect = false // Mark as from Google Fit
                            Log.d(TAG, "✅✅✅ USING GOOGLE FIT WORKOUTS: ${workouts.size} workouts ✅✅✅")
                            Log.d(TAG, "✅ Synced from Google Fit: steps=$steps, calories=$calories, sleep=${sleepSessions.size} sessions, workouts=${workouts.size}")
                        } else {
                            Log.w(TAG, "⚠️ No workouts found from enabled health service")
                        }
                            syncSuccess = true
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error syncing from Google Fit", e)
                            e.printStackTrace()
                            
                            // Check if it's a permission-related error
                            val errorMessage = e.message ?: ""
                            if (errorMessage.contains("ACTIVITY_RECOGNITION") || 
                                errorMessage.contains("SecurityException") ||
                                e is SecurityException) {
                                Log.e(TAG, "⚠️ Permission error detected in exception - notifying user")
                                showPermissionRequiredMessage(context)
                                // Don't retry permission errors - user needs to grant permission
                                return false
                            }
                            
                            // Retry if we haven't exceeded max retries
                            if (retryCount < MAX_RETRIES - 1) {
                                val delayMs = INITIAL_RETRY_DELAY_MS * (1 shl retryCount) // Exponential backoff
                                Log.d(TAG, "⏳ Retrying in ${delayMs}ms...")
                                delay(delayMs)
                                return syncInternal(context, retryCount + 1)
                            }
                            return false
                        }
                    }
            } else {
                Log.w(TAG, "⚠️ No Google Fit permissions")
                // Only notify if we don't have Health Connect as fallback
                if (!triedHealthConnect || !syncSuccess) {
                    showGoogleFitConnectionMessage(context)
                }
            }
            
            // CRITICAL: Provide actionable feedback if CRITICAL metrics are missing after all retries
            // Note: Steps are NOT included - steps can legitimately be 0 (phone charging, sedentary day, etc.)
            // Only alert for metrics that should ALWAYS have data (sleep, workouts, calories)
            if (retryCount == 0) {
                val missingMetrics = mutableListOf<String>()
                if (sleepSessions.isEmpty()) missingMetrics.add("sleep")
                if (workouts.isEmpty()) missingMetrics.add("workouts")
                if (calories == 0) missingMetrics.add("calories")
                // Steps intentionally excluded - can be 0 legitimately
                
                if (missingMetrics.isNotEmpty()) {
                    Log.e(TAG, "❌❌❌ FINAL DIAGNOSIS: MISSING METRICS AFTER ALL RETRIES ❌❌❌")
                    Log.e(TAG, "  Missing: ${missingMetrics.joinToString(", ")}")
                    Log.e(TAG, "  Note: Steps not included (can be 0 legitimately)")
                    Log.e(TAG, "  User has data in Google Fit app but API returned 0")
                    Log.e(TAG, "  ACTION REQUIRED:")
                    Log.e(TAG, "    1. Open Google Fit app manually (this forces API sync)")
                    Log.e(TAG, "    2. Wait 30 seconds, then sync again in Coachie")
                    Log.e(TAG, "    3. Check Google Fit app > Settings > Connected apps")
                    Log.e(TAG, "    4. Ensure fitness apps are connected and syncing")
                    Log.e(TAG, "    5. Verify data exists in Google Fit app for today")
                    
                    // Show user-friendly message with specific missing metrics
                    Handler(Looper.getMainLooper()).post {
                        val metricsList = missingMetrics.joinToString(", ")
                        val message = "Missing: $metricsList. Try: 1) Open Google Fit app, 2) Wait 30 seconds, 3) Sync again"
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
            } else {
                Log.d(TAG, "Skipping Google Fit - already have data from Health Connect")
            }
            
            // CRITICAL: GoogleFitService.readSleep() already does comprehensive filtering
            // It combines segments, filters by end date, and handles overnight sleep correctly
            // We should TRUST its results and NOT filter again here
            val todaySleepSessions = sleepSessions
            
            Log.d(TAG, "Using ${todaySleepSessions.size} sleep sessions from GoogleFitService (already filtered)")
            for (session in todaySleepSessions) {
                val durationHours = (session.endTime - session.startTime) / (1000.0 * 60.0 * 60.0)
                val startTimeStr = java.time.Instant.ofEpochMilli(session.startTime).atZone(ZoneId.systemDefault())
                val endTimeStr = java.time.Instant.ofEpochMilli(session.endTime).atZone(ZoneId.systemDefault())
                Log.d(TAG, "  ✅ Sleep session: ${String.format("%.1f", durationHours)} hours")
                Log.d(TAG, "    - Start: $startTimeStr")
                Log.d(TAG, "    - End: $endTimeStr")
            }
            
            // CRITICAL DIAGNOSTIC: Log what we actually got
            Log.d(TAG, "=========================================")
            Log.d(TAG, "📊📊📊 SYNC RESULTS SUMMARY 📊📊📊")
            Log.d(TAG, "  Steps: $steps (will be saved: ${if (syncSuccess) steps else "preserving existing"})")
            Log.d(TAG, "  Calories: $calories (will be saved: ${if (syncSuccess) calories else "preserving existing"})")
            Log.d(TAG, "  Sleep sessions: ${todaySleepSessions.size} (${if (todaySleepSessions.isNotEmpty()) "will save" else "none to save"})")
            Log.d(TAG, "  Workouts: ${workouts.size} (${if (workouts.isNotEmpty()) "will save" else "none to save"})")
            Log.d(TAG, "  Sync success: $syncSuccess")
            
            // Count today's workouts for clarity
            val todaysWorkoutCount = workouts.count { workout ->
                val workoutDate = java.time.Instant.ofEpochMilli(workout.startTime)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                workoutDate == today
            }
            Log.d(TAG, "  Today's workouts: $todaysWorkoutCount")
            Log.d(TAG, "=========================================")
            
            if (!syncSuccess && steps == 0 && calories == 0 && todaySleepSessions.isEmpty() && workouts.isEmpty()) {
                Log.e(TAG, "❌❌❌ CRITICAL: NO DATA FOUND FROM ANY SOURCE ❌❌❌")
                Log.e(TAG, "  This means Google Fit/Health Connect returned 0 for everything")
                Log.e(TAG, "  Possible causes:")
                Log.e(TAG, "    1. No data exists in Google Fit for today (no steps/workouts/sleep logged)")
                Log.e(TAG, "    2. Google Fit hasn't synced data yet (can take hours)")
                Log.e(TAG, "    3. Fitness apps aren't writing to Google Fit")
                Log.e(TAG, "    4. Google Fit permissions not properly granted")
                Log.e(TAG, "    5. Google Fit account not connected")
                Log.e(TAG, "  ACTION REQUIRED:")
                Log.e(TAG, "    - Open Google Fit app and verify data exists")
                Log.e(TAG, "    - Check Settings > Permissions > Google Fit connection")
                Log.e(TAG, "    - Ensure fitness apps are syncing to Google Fit")
                // Notify user if no data sources are connected
                showNoDataSourcesMessage(context)
                // Still try to save (might have existing data to preserve)
            } else if (syncSuccess) {
                Log.d(TAG, "✅✅✅ Data successfully read from health source ✅✅✅")
            } else {
                Log.w(TAG, "⚠️ Sync did not succeed but some data may exist")
            }
            
            // Save to Firebase with validation
            Log.d(TAG, "--- Saving to Firebase ---")
            
            // Save daily log
            // CRITICAL: ALWAYS update steps and calories if we got values from Google Fit/Health Connect
            // Even if they're 0, we need to track that we synced today
            // Only preserve old values if we got NO data from any source (sync failed)
            try {
                Log.d(TAG, "🔍🔍🔍 SAVING DAILY LOG 🔍🔍🔍")
                Log.d(TAG, "  Steps from sync: $steps")
                Log.d(TAG, "  Calories from sync: $calories")
                Log.d(TAG, "  Sync success: $syncSuccess")
                
                Log.d(TAG, "Getting existing daily log...")
                val existingLog = repository.getDailyLog(userId, dateString).getOrDefault(null)
                Log.d(TAG, "Existing log: ${if (existingLog != null) "found (steps=${existingLog.steps}, calories=${existingLog.caloriesBurned})" else "not found"}")
                
                // CRITICAL: If sync succeeded, ALWAYS use the values we got (even if 0)
                // This ensures we track that we synced today
                // Only preserve old values if sync completely failed (no data from any source)
                val finalSteps = if (syncSuccess) {
                    // Sync succeeded - use what we got (even if 0)
                    steps
                } else {
                    // Sync failed - preserve existing value if we have one
                    steps.takeIf { it > 0 } ?: existingLog?.steps
                }
                
                val finalCalories = if (syncSuccess) {
                    // Sync succeeded - use what we got (even if 0)
                    calories
                } else {
                    // Sync failed - preserve existing value if we have one
                    calories.takeIf { it > 0 } ?: existingLog?.caloriesBurned
                }
                
                val updatedLog = existingLog?.copy(
                    uid = userId, // CRITICAL: Always ensure uid is set (fixes bug where existingLog might have empty uid)
                    steps = finalSteps,
                    caloriesBurned = finalCalories,
                    updatedAt = System.currentTimeMillis()
                ) ?: DailyLog(
                    uid = userId,
                    date = dateString,
                    steps = finalSteps,
                    caloriesBurned = finalCalories
                )
                
                // CRITICAL: Validate uid is not empty before saving
                if (updatedLog.uid.isBlank()) {
                    Log.e(TAG, "❌❌❌ CRITICAL: DailyLog.uid is empty! Cannot save. userId=$userId, date=$dateString ❌❌❌")
                    throw IllegalStateException("DailyLog.uid cannot be empty. userId=$userId")
                }
                
                Log.d(TAG, "💾💾💾 SAVING DAILY LOG TO FIRESTORE 💾💾💾")
                Log.d(TAG, "  Steps: ${updatedLog.steps} (from sync: $steps, syncSuccess: $syncSuccess)")
                Log.d(TAG, "  Calories: ${updatedLog.caloriesBurned} (from sync: $calories, syncSuccess: $syncSuccess)")
                Log.d(TAG, "  Date: $dateString, User: $userId, Log.uid: ${updatedLog.uid}")
                val saveResult = repository.saveDailyLog(updatedLog)
                if (saveResult.isFailure) {
                    Log.e(TAG, "❌❌❌ FAILED TO SAVE DAILY LOG ❌❌❌")
                    Log.e(TAG, "  Error: ${saveResult.exceptionOrNull()?.message}")
                    saveResult.exceptionOrNull()?.printStackTrace()
                    throw saveResult.exceptionOrNull() ?: Exception("Failed to save daily log")
                } else {
                    Log.d(TAG, "✅ Daily log save call completed (verifying...)")
                }
                
                // Validate it was saved
                delay(500) // Give Firestore time to write
                val savedLog = repository.getDailyLog(userId, dateString).getOrDefault(null)
                if (savedLog != null && (savedLog.steps == updatedLog.steps || savedLog.caloriesBurned == updatedLog.caloriesBurned)) {
                    Log.d(TAG, "✅ Daily log saved and verified: steps=${savedLog.steps}, calories=${savedLog.caloriesBurned}")
                } else {
                    Log.e(TAG, "❌ Daily log save verification failed! Expected: steps=${updatedLog.steps}, calories=${updatedLog.caloriesBurned}, Got: steps=${savedLog?.steps}, calories=${savedLog?.caloriesBurned}")
                    if (retryCount < MAX_RETRIES - 1) {
                        val delayMs = INITIAL_RETRY_DELAY_MS * (1 shl retryCount)
                        Log.d(TAG, "⏳ Retrying save in ${delayMs}ms...")
                        delay(delayMs)
                        return syncInternal(context, retryCount + 1)
                    }
                    throw Exception("Daily log save verification failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error saving daily log", e)
                e.printStackTrace()
                if (retryCount < MAX_RETRIES - 1) {
                    val delayMs = INITIAL_RETRY_DELAY_MS * (1 shl retryCount)
                    Log.d(TAG, "⏳ Retrying save in ${delayMs}ms...")
                    delay(delayMs)
                    return sync(context, retryCount + 1)
                }
                throw e
            }
            
            // SIMPLE RULE: readSleep() already filtered for sleep ending on TODAY or recent sleep from yesterday
            // If it returned any sleep, save ALL of it to TODAY (that's what the user wants to see)
            Log.d(TAG, "🔍 About to save sleep: todaySleepSessions.size=${todaySleepSessions.size}")
            if (todaySleepSessions.isNotEmpty()) {
                // Process all sleep sessions - save ALL to TODAY
                for (sleepSession in todaySleepSessions) {
                    val durationHours = (sleepSession.endTime - sleepSession.startTime) / (1000.0 * 60.0 * 60.0)
                    val durationMinutes = ((sleepSession.endTime - sleepSession.startTime) / (1000.0 * 60.0)).toInt()
                    
                    val sleepEndInstant = java.time.Instant.ofEpochMilli(sleepSession.endTime)
                        .atZone(ZoneId.systemDefault())
                    val sleepEndDate = sleepEndInstant.toLocalDate()
                    
                    // SIMPLE: Save ALL sleep returned by readSleep() to TODAY
                    // readSleep() already filtered for sleep ending today OR recent sleep from yesterday
                    val sleepDate = today
                    val sleepDateString = sleepDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    
                    Log.d(TAG, "💾 Saving sleep to TODAY ($sleepDate): ${String.format("%.1f", durationHours)} hours (${durationMinutes} minutes)")
                    Log.d(TAG, "  - Start: ${java.time.Instant.ofEpochMilli(sleepSession.startTime).atZone(ZoneId.systemDefault())}")
                    Log.d(TAG, "  - End: ${java.time.Instant.ofEpochMilli(sleepSession.endTime).atZone(ZoneId.systemDefault())}")
                    Log.d(TAG, "  - Sleep ends on: $sleepEndDate, saving to: $sleepDate (TODAY)")
                    
                    try {
                        // Delete any existing Google Fit sleep logs for this date to avoid duplicates
                        val existingLogs = repository.getHealthLogs(userId, sleepDateString).getOrDefault(emptyList())
                        val googleFitSleepLogs = existingLogs.filterIsInstance<HealthLog.SleepLog>()
                            .filter { it.entryId.startsWith("google_fit_sleep_") }
                        
                        for (oldLog in googleFitSleepLogs) {
                            repository.deleteHealthLog(userId, sleepDateString, oldLog.entryId)
                            Log.d(TAG, "Deleted old Google Fit sleep log from $sleepDateString: ${oldLog.entryId}")
                        }
                        
                        // Save the sleep with date determined by endTime
                        val entryId = "google_fit_sleep_${sleepDateString}"
                        val sleepLog = HealthLog.SleepLog(
                            entryId = entryId,
                            startTime = sleepSession.startTime,
                            endTime = sleepSession.endTime,
                            quality = 3
                        )
                        
                        // Save sleep - retry until it works
                        var saved = false
                        var attempts = 0
                        while (!saved && attempts < 5) {
                            val saveResult = repository.saveHealthLog(userId, sleepDateString, sleepLog)
                            if (saveResult.isSuccess) {
                                saved = true
                                Log.d(TAG, "✅ Successfully saved sleep to $sleepDateString")
                            } else {
                                attempts++
                                if (attempts < 5) {
                                    delay((500 * attempts).toLong())
                                } else {
                                    throw saveResult.exceptionOrNull() ?: Exception("Failed to save sleep log after 5 attempts")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error saving sleep log", e)
                        e.printStackTrace()
                        if (retryCount < MAX_RETRIES - 1) {
                            val delayMs = INITIAL_RETRY_DELAY_MS * (1 shl retryCount)
                            Log.d(TAG, "⏳ Retrying sleep save in ${delayMs}ms...")
                            delay(delayMs)
                            return syncInternal(context, retryCount + 1)
                        }
                        throw e
                    }
                }
            } else {
                if (syncSuccess) {
                    Log.w(TAG, "⚠️ No sleep sessions found but sync succeeded - Google Fit returned 0 sleep for today")
                    Log.w(TAG, "  This could mean:")
                    Log.w(TAG, "    1. No sleep logged in Google Fit for today yet")
                    Log.w(TAG, "    2. Sleep hasn't synced to Google Fit yet")
                    Log.w(TAG, "    3. Sleep tracking is disabled")
                } else {
                    Log.w(TAG, "⚠️ No sleep sessions found and sync failed - preserving existing sleep data")
                }
            }
            
            // Save workout logs - ALWAYS save today's workouts
            // CRITICAL: This ensures today's workouts are always synced, which is what users care about
            Log.d(TAG, "🔍🔍🔍 ABOUT TO SAVE WORKOUTS 🔍🔍🔍")
            Log.d(TAG, "  workouts.size: ${workouts.size}")
            Log.d(TAG, "  workoutsFromHealthConnect: $workoutsFromHealthConnect")
            workouts.forEachIndexed { index, workout ->
                val workoutDate = java.time.Instant.ofEpochMilli(workout.startTime)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                Log.d(TAG, "    Workout ${index + 1}: ${workout.activityType}, ${workout.durationMin} min, date: $workoutDate, startTime: ${workout.startTime}")
            }
            
            if (workouts.isNotEmpty()) {
                // Check if we have today's workouts
                val todayWorkoutsCount = workouts.count { workout ->
                    val workoutDate = java.time.Instant.ofEpochMilli(workout.startTime)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                    workoutDate == today
                }
                Log.d(TAG, "💾💾💾 SAVING ${workouts.size} WORKOUTS (${todayWorkoutsCount} from today, ${workouts.size - todayWorkoutsCount} from previous days) 💾💾💾")
                Log.d(TAG, "💾 Saving ${workouts.size} workouts...")
                
                try {
                    // Use appropriate prefix based on source (Health Connect vs Google Fit)
                    val workoutPrefix = if (workoutsFromHealthConnect) {
                        "health_connect_workout_"
                    } else {
                        "google_fit_workout_"
                    }
                    Log.d(TAG, "Using workout prefix: $workoutPrefix (fromHealthConnect=$workoutsFromHealthConnect)")
                    
                    // CRITICAL FIX: Only delete workouts that we're about to save (to update them)
                    // Don't delete ALL workouts - this was causing workouts to disappear when sync found 0 workouts
                    for (workout in workouts) {
                        // Get the workout's date from its startTime
                        val workoutDate = java.time.Instant.ofEpochMilli(workout.startTime)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        val workoutTimeStr = java.time.Instant.ofEpochMilli(workout.startTime).atZone(ZoneId.systemDefault())
                        val workoutDateString = workoutDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                        
                        // Create consistent entryId: {prefix}_{date}_{startTime}_{normalizedActivityType}
                        val normalizedActivity = workout.activityType.lowercase().replace(" ", "_").replace("-", "_").replace(".", "_")
                        val entryId = "${workoutPrefix}${workoutDateString}_${workout.startTime}_${normalizedActivity}"
                        
                        Log.d(TAG, "💾 Processing workout from ${workoutDate}: ${workout.activityType} at $workoutTimeStr (entryId: $entryId)")
                        
                        // Check if this exact workout already exists
                        val existingLogs = repository.getHealthLogs(userId, workoutDateString).getOrDefault(emptyList())
                        val existingWorkout = existingLogs.filterIsInstance<HealthLog.WorkoutLog>()
                            .firstOrNull { it.entryId == entryId }
                        
                        if (existingWorkout != null) {
                            // Workout already exists - check if we need to update it
                            val needsUpdate = existingWorkout.durationMin != workout.durationMin || 
                                            existingWorkout.caloriesBurned != workout.caloriesBurned ||
                                            existingWorkout.workoutType != workout.activityType
                            
                            if (needsUpdate) {
                                Log.d(TAG, "  🔄 Updating existing workout (data changed)")
                                repository.deleteHealthLog(userId, workoutDateString, entryId)
                            } else {
                                Log.d(TAG, "  ✅ Workout already exists and is up-to-date, skipping")
                                continue // Skip saving - already exists
                            }
                        } else {
                            Log.d(TAG, "  ✨ New workout, will save")
                        }
                        
                        // Save the workout
                        val workoutLog = HealthLog.WorkoutLog(
                            entryId = entryId,
                            workoutType = workout.activityType,
                            durationMin = workout.durationMin,
                            caloriesBurned = workout.caloriesBurned,
                            intensity = "Medium",
                            timestamp = workout.startTime
                        )
                        
                        // Save workout - retry until it works
                        var saved = false
                        var attempts = 0
                        while (!saved && attempts < 5) {
                            val saveResult = repository.saveHealthLog(userId, workoutDateString, workoutLog)
                            if (saveResult.isSuccess) {
                                saved = true
                                Log.d(TAG, "  ✅ Workout saved successfully")
                            } else {
                                attempts++
                                if (attempts < 5) {
                                    delay((500 * attempts).toLong())
                                } else {
                                    throw saveResult.exceptionOrNull() ?: Exception("Failed to save workout log after 5 attempts")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error saving workout logs", e)
                    e.printStackTrace()
                    if (retryCount < MAX_RETRIES - 1) {
                        val delayMs = INITIAL_RETRY_DELAY_MS * (1 shl retryCount)
                        Log.d(TAG, "⏳ Retrying workout save in ${delayMs}ms...")
                        delay(delayMs)
                        return syncInternal(context, retryCount + 1)
                    }
                    throw e
                }
            } else {
                Log.w(TAG, "⚠️⚠️⚠️ NO WORKOUTS FOUND FOR TODAY ⚠️⚠️⚠️")
                Log.w(TAG, "  This could mean:")
                Log.w(TAG, "    1. No workout was done today yet")
                Log.w(TAG, "    2. Google Fit hasn't synced today's workout yet (can take hours)")
                Log.w(TAG, "    3. Workout app isn't syncing to Google Fit")
                Log.w(TAG, "  SOLUTION: Open Google Fit app to force sync, then sync again in Coachie")
            }
            
            Log.d(TAG, "✅✅✅ SYNC COMPLETED SUCCESSFULLY ✅✅✅")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ SYNC FAILED ❌❌❌", e)
            Log.e(TAG, "  Error type: ${e.javaClass.simpleName}")
            Log.e(TAG, "  Error message: ${e.message}")
            Log.e(TAG, "  Retry count: $retryCount/$MAX_RETRIES")
            Log.e(TAG, "  Today's date: ${LocalDate.now(ZoneId.systemDefault())}")
            e.printStackTrace()
            
            // Check if it's a permission-related error
            val errorMessage = e.message ?: ""
            if (errorMessage.contains("ACTIVITY_RECOGNITION") || 
                errorMessage.contains("permission") ||
                errorMessage.contains("Permission") ||
                errorMessage.contains("SecurityException") ||
                e is SecurityException) {
                Log.e(TAG, "⚠️ Permission error in sync - notifying user")
                showPermissionRequiredMessage(context)
                return false
            }
            
            // CRITICAL: For consecutive day reliability, always retry on non-permission errors
            // This ensures temporary API issues don't cause permanent failures
            if (retryCount < MAX_RETRIES - 1) {
                val delayMs = INITIAL_RETRY_DELAY_MS * (1 shl retryCount)
                Log.d(TAG, "⏳ Retrying entire sync in ${delayMs}ms... (attempt ${retryCount + 2}/$MAX_RETRIES)")
                delay(delayMs)
                return syncInternal(context, retryCount + 1)
            } else {
                // All retries exhausted - log detailed error but don't spam user with toasts
                Log.e(TAG, "⚠️⚠️⚠️ SYNC FAILED AFTER ALL RETRIES ⚠️⚠️⚠️")
                Log.e(TAG, "  This sync failed but will retry on next sync attempt")
                Log.e(TAG, "  Error: ${e.javaClass.simpleName}: ${e.message}")
                // Only show toast for critical errors, not transient API failures
                // The sync will retry automatically on next app open or manual sync
                return false
            }
        }
    }
    
    /**
     * Show user-friendly message when ACTIVITY_RECOGNITION permission is missing
     */
    private fun showPermissionRequiredMessage(context: Context) {
        Handler(Looper.getMainLooper()).post {
            val message = "Google Fit sync requires 'Activity Recognition' permission. Please enable it in Settings > Permissions."
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            Log.w(TAG, "⚠️ User notified: $message")
        }
    }
    
    /**
     * Show user-friendly message when Health Connect permissions are missing
     */
    private fun showHealthConnectPermissionMessage(context: Context) {
        Handler(Looper.getMainLooper()).post {
            val message = "Health Connect permissions required. Please grant permissions in Settings > Permissions."
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            Log.w(TAG, "⚠️ User notified: $message")
        }
    }
    
    /**
     * Show user-friendly message when Google Fit is not connected
     */
    private fun showGoogleFitConnectionMessage(context: Context) {
        Handler(Looper.getMainLooper()).post {
            val message = "Google Fit is not connected. Please connect it in Settings > Permissions."
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            Log.w(TAG, "⚠️ User notified: $message")
        }
    }
    
    /**
     * Show user-friendly message when no health data sources are available
     */
    private fun showNoDataSourcesMessage(context: Context) {
        Handler(Looper.getMainLooper()).post {
            val message = "No health data sources connected. Please connect Google Fit or Health Connect in Settings > Permissions."
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            Log.w(TAG, "⚠️ User notified: $message")
        }
    }
    
    /**
     * Show user-friendly message when sync fails after all retries
     */
    private fun showSyncFailedMessage(context: Context) {
        Handler(Looper.getMainLooper()).post {
            val message = "Health sync failed. Please check your connection and try again, or check Settings > Permissions."
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            Log.w(TAG, "⚠️ User notified: $message")
        }
    }
}
