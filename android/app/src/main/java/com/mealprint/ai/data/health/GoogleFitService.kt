package com.coachie.app.data.health

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.FitnessActivities
import com.google.android.gms.fitness.data.DataPoint
import com.google.android.gms.fitness.data.DataSet
import com.google.android.gms.fitness.data.DataSource
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.data.Session
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.request.SessionReadRequest
import com.google.android.gms.fitness.request.SessionInsertRequest
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class GoogleFitService(private val context: Context) {
    
    private val TAG = "GOOGLE_FIT_DEBUG"
    
    private val fitnessOptions = FitnessOptions.builder()
        .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_SLEEP_SEGMENT, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_READ)
        .build()
    
    // Separate fitness options for writing (only used when actually writing)
    private val fitnessOptionsWrite = FitnessOptions.builder()
        .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_WRITE)
        .build()

    fun hasPermissions(): Boolean {
        Log.d(TAG, "=== CHECKING GOOGLE FIT PERMISSIONS ===")
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account == null) {
                Log.w(TAG, "❌ No Google account signed in")
                Log.w(TAG, "  User needs to sign in via Permissions screen")
                return false
            }
            Log.d(TAG, "✅ Google account found: ${account.email}")
            Log.d(TAG, "  Account ID: ${account.id}")
            Log.d(TAG, "  Granted scopes: ${account.grantedScopes?.joinToString() ?: "none"}")
            
            val hasPerms = GoogleSignIn.hasPermissions(account, fitnessOptions)
            Log.d(TAG, "Permissions granted: $hasPerms")
            
            if (!hasPerms) {
                Log.w(TAG, "⚠️ Account exists but fitness permissions not granted")
                Log.w(TAG, "  User needs to grant permissions via Permissions screen")
            }
            
            return hasPerms
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERROR checking Google Fit permissions", e)
            e.printStackTrace()
            return false
        }
    }
    
    fun getAccount(): GoogleSignInAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        Log.d(TAG, "getAccount() - account: ${account?.email ?: "null"}")
        return account
    }
    
    suspend fun readSteps(startTime: Long, endTime: Long): Int {
        Log.d(TAG, "=========================================")
        Log.d(TAG, "=== READING STEPS FROM GOOGLE FIT ===")
        Log.d(TAG, "=========================================")
        val startTimeStr = java.time.Instant.ofEpochMilli(startTime).atZone(java.time.ZoneId.systemDefault())
        val endTimeStr = java.time.Instant.ofEpochMilli(endTime).atZone(java.time.ZoneId.systemDefault())
        Log.d(TAG, "Time range: $startTimeStr to $endTimeStr")
        Log.d(TAG, "Time range (ms): $startTime to $endTime")
        
        val account = getAccount()
        if (account == null) {
            Log.e(TAG, "❌❌❌ NO GOOGLE ACCOUNT - CANNOT READ STEPS ❌❌❌")
            Log.e(TAG, "  User must sign in to Google account with Google Fit permissions")
            return 0
        }
        Log.d(TAG, "✅ Google account found: ${account.email}")
        Log.d(TAG, "  Account ID: ${account.id}")
        Log.d(TAG, "  Has fitness permissions: ${GoogleSignIn.hasPermissions(account, fitnessOptions)}")
        
        try {
            val request = DataReadRequest.Builder()
                .read(DataType.TYPE_STEP_COUNT_DELTA)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build()

            Log.d(TAG, "📡 Requesting step data from Google Fit API...")
            val response = Fitness.getHistoryClient(context, account)
                .readData(request)
                .await()

            Log.d(TAG, "📊 Google Fit API response: ${response.dataSets.size} datasets")
            
            var totalSteps = 0
            var dataSetCount = 0
            var dataPointCount = 0
            for (dataSet in response.dataSets) {
                dataSetCount++
                Log.d(TAG, "  Dataset $dataSetCount: ${dataSet.dataPoints.size} data points")
                for (dataPoint in dataSet.dataPoints) {
                    dataPointCount++
                    val steps = dataPoint.getValue(Field.FIELD_STEPS).asInt()
                    totalSteps += steps
                    val dataPointTime = java.time.Instant.ofEpochMilli(dataPoint.getStartTime(TimeUnit.MILLISECONDS)).atZone(java.time.ZoneId.systemDefault())
                    Log.d(TAG, "    DataPoint: $steps steps at $dataPointTime")
                }
            }
            
            if (totalSteps == 0) {
                Log.w(TAG, "⚠️⚠️⚠️ GOOGLE FIT RETURNED 0 STEPS ⚠️⚠️⚠️")
                Log.w(TAG, "  This could mean:")
                Log.w(TAG, "    1. No steps were taken in this time range")
                Log.w(TAG, "    2. Google Fit hasn't synced step data yet")
                Log.w(TAG, "    3. Step tracking is disabled in Google Fit")
                Log.w(TAG, "    4. No fitness apps are writing to Google Fit")
                Log.w(TAG, "  Check: Open Google Fit app and verify steps are being tracked")
            } else {
                Log.d(TAG, "✅✅✅ TOTAL STEPS: $totalSteps (from $dataSetCount datasets, $dataPointCount datapoints) ✅✅✅")
            }
            return totalSteps
        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ ERROR READING STEPS ❌❌❌", e)
            Log.e(TAG, "  Error type: ${e.javaClass.simpleName}")
            Log.e(TAG, "  Error message: ${e.message}")
            e.printStackTrace()
            return 0
        }
    }
    
    suspend fun readCalories(startTime: Long, endTime: Long): Int {
        Log.d(TAG, "=========================================")
        Log.d(TAG, "=== READING CALORIES FROM GOOGLE FIT ===")
        Log.d(TAG, "=========================================")
        val startTimeStr = java.time.Instant.ofEpochMilli(startTime).atZone(java.time.ZoneId.systemDefault())
        val endTimeStr = java.time.Instant.ofEpochMilli(endTime).atZone(java.time.ZoneId.systemDefault())
        Log.d(TAG, "Time range: $startTimeStr to $endTimeStr")
        
        val account = getAccount()
        if (account == null) {
            Log.e(TAG, "❌❌❌ NO GOOGLE ACCOUNT - CANNOT READ CALORIES ❌❌❌")
            return 0
        }
        Log.d(TAG, "✅ Google account: ${account.email}")
        
        try {
            val request = DataReadRequest.Builder()
                .read(DataType.TYPE_CALORIES_EXPENDED)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build()

            Log.d(TAG, "📡 Requesting calorie data from Google Fit API...")
            val response = Fitness.getHistoryClient(context, account)
                .readData(request)
                .await()
            
            Log.d(TAG, "📊 Google Fit API response: ${response.dataSets.size} datasets")
            
            var totalCalories = 0.0
            var dataPointCount = 0
            for (dataSet in response.dataSets) {
                Log.d(TAG, "  Dataset: ${dataSet.dataPoints.size} data points")
                for (dataPoint in dataSet.dataPoints) {
                    dataPointCount++
                    val calories = dataPoint.getValue(Field.FIELD_CALORIES).asFloat().toDouble()
                    totalCalories += calories
                    val dataPointTime = java.time.Instant.ofEpochMilli(dataPoint.getStartTime(TimeUnit.MILLISECONDS)).atZone(java.time.ZoneId.systemDefault())
                    Log.d(TAG, "    DataPoint: ${calories.toInt()} cal at $dataPointTime")
                }
            }
            
            if (totalCalories == 0.0) {
                Log.w(TAG, "⚠️⚠️⚠️ GOOGLE FIT RETURNED 0 CALORIES ⚠️⚠️⚠️")
                Log.w(TAG, "  This could mean:")
                Log.w(TAG, "    1. No calories were burned in this time range")
                Log.w(TAG, "    2. Google Fit hasn't synced calorie data yet")
                Log.w(TAG, "    3. No fitness apps are writing calories to Google Fit")
            } else {
                Log.d(TAG, "✅✅✅ TOTAL CALORIES: ${totalCalories.toInt()} (from $dataPointCount datapoints) ✅✅✅")
            }
            return totalCalories.toInt()
        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ ERROR READING CALORIES ❌❌❌", e)
            Log.e(TAG, "  Error type: ${e.javaClass.simpleName}")
            Log.e(TAG, "  Error message: ${e.message}")
            e.printStackTrace()
            return 0
        }
    }
    
    /**
     * Helper: Read sleep segments from DataReadRequest API
     */
    private suspend fun readSleepSegments(searchStart: Long, searchEnd: Long, account: GoogleSignInAccount): List<SleepSession> {
        val sessions = mutableListOf<SleepSession>()
        try {
            val request = DataReadRequest.Builder()
                .read(DataType.TYPE_SLEEP_SEGMENT)
                .setTimeRange(searchStart, searchEnd, TimeUnit.MILLISECONDS)
                .build()

            Log.d(TAG, "Reading sleep segments: ${java.time.Instant.ofEpochMilli(searchStart).atZone(java.time.ZoneId.systemDefault())} to ${java.time.Instant.ofEpochMilli(searchEnd).atZone(java.time.ZoneId.systemDefault())}")
            
            val response = Fitness.getHistoryClient(context, account)
                .readData(request)
                .await()

            Log.d(TAG, "Sleep segments response: ${response.dataSets.size} datasets")
            
            if (response.dataSets.isEmpty()) {
                Log.w(TAG, "  ⚠️ No datasets returned from Sleep Segments API")
            }
            
            // Collect all segments
            val allSegments = mutableListOf<SleepData>()
            for (dataSet in response.dataSets) {
                Log.d(TAG, "  Dataset: ${dataSet.dataPoints.size} data points")
                if (dataSet.dataPoints.isEmpty()) {
                    Log.w(TAG, "    ⚠️ Empty dataset - no sleep segments in this dataset")
                }
                for (dataPoint in dataSet.dataPoints) {
                    val segStart = dataPoint.getStartTime(TimeUnit.MILLISECONDS)
                    val segEnd = dataPoint.getEndTime(TimeUnit.MILLISECONDS)
                    allSegments.add(SleepData(segStart, segEnd))
                    val segStartStr = java.time.Instant.ofEpochMilli(segStart).atZone(java.time.ZoneId.systemDefault())
                    val segEndStr = java.time.Instant.ofEpochMilli(segEnd).atZone(java.time.ZoneId.systemDefault())
                    val segEndDate = segEndStr.toLocalDate()
                    Log.d(TAG, "    ✅ Segment found: ends on $segEndDate")
                    Log.d(TAG, "       $segStartStr to $segEndStr")
                }
            }
            
            Log.d(TAG, "Total segments collected: ${allSegments.size}")
            if (allSegments.isEmpty()) {
                Log.w(TAG, "  ⚠️⚠️⚠️ NO SLEEP SEGMENTS FOUND ⚠️⚠️⚠️")
            }
            
            // Log ALL segments to see what we got from Google Fit
            if (allSegments.isNotEmpty()) {
                Log.d(TAG, "📋 ALL SLEEP SEGMENTS FROM GOOGLE FIT:")
                allSegments.forEachIndexed { index, segment ->
                    val segStartStr = java.time.Instant.ofEpochMilli(segment.startTime)
                        .atZone(java.time.ZoneId.systemDefault())
                    val segEndStr = java.time.Instant.ofEpochMilli(segment.endTime)
                        .atZone(java.time.ZoneId.systemDefault())
                    val segEndDate = segEndStr.toLocalDate()
                    Log.d(TAG, "  Segment ${index + 1}: ends on $segEndDate at ${segEndStr.toLocalTime()}")
                    Log.d(TAG, "     $segStartStr to $segEndStr")
                }
            }
            
            // Combine segments into continuous sessions (gaps <30 min)
            if (allSegments.isNotEmpty()) {
                val sorted = allSegments.sortedBy { it.startTime }
                var currentStart = sorted[0].startTime
                var currentEnd = sorted[0].endTime
                
                for (i in 1 until sorted.size) {
                    val segment = sorted[i]
                    val gap = segment.startTime - currentEnd
                    if (gap <= 30 * 60 * 1000) { // 30 minutes
                        currentEnd = maxOf(currentEnd, segment.endTime)
                    } else {
                        val durationMinutes = ((currentEnd - currentStart) / (1000 * 60)).toInt()
                        sessions.add(SleepSession(currentStart, currentEnd, durationMinutes, "segments"))
                        currentStart = segment.startTime
                        currentEnd = segment.endTime
                    }
                }
                // Add last session
                val durationMinutes = ((currentEnd - currentStart) / (1000 * 60)).toInt()
                sessions.add(SleepSession(currentStart, currentEnd, durationMinutes, "segments"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading sleep segments", e)
        }
        return sessions
    }
    
    /**
     * Helper: Read sleep sessions from Sessions API
     */
    private suspend fun readSleepSessions(searchStart: Long, searchEnd: Long, account: GoogleSignInAccount): List<SleepSession> {
        val sessions = mutableListOf<SleepSession>()
        try {
            // Query sleep sessions - includeSleepSessions() is needed to get Samsung Health sleep
            val sessionRequest = SessionReadRequest.Builder()
                .readSessionsFromAllApps()
                .includeSleepSessions() // This is required to get sleep from Samsung Health
                .setTimeInterval(searchStart, searchEnd, TimeUnit.MILLISECONDS)
                .build()
            
            Log.d(TAG, "Reading sleep sessions: ${java.time.Instant.ofEpochMilli(searchStart).atZone(java.time.ZoneId.systemDefault())} to ${java.time.Instant.ofEpochMilli(searchEnd).atZone(java.time.ZoneId.systemDefault())}")
            
            val sessionResponse = try {
                Fitness.getSessionsClient(context, account)
                    .readSession(sessionRequest)
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "❌❌❌ EXCEPTION READING SLEEP SESSIONS ❌❌❌", e)
                Log.e(TAG, "  Exception type: ${e.javaClass.simpleName}")
                Log.e(TAG, "  Exception message: ${e.message}")
                e.printStackTrace()
                throw e
            }
            
            Log.d(TAG, "Sleep sessions response: ${sessionResponse.sessions.size} sessions")
            
            if (sessionResponse.sessions.isEmpty()) {
                Log.w(TAG, "  ⚠️⚠️⚠️ NO SLEEP SESSIONS RETURNED FROM API ⚠️⚠️⚠️")
            } else {
                // Log ALL sessions from API before filtering
                Log.d(TAG, "📋 ALL SLEEP SESSIONS FROM GOOGLE FIT SESSIONS API:")
                sessionResponse.sessions.forEachIndexed { index, session ->
                    val start = session.getStartTime(TimeUnit.MILLISECONDS)
                    val end = session.getEndTime(TimeUnit.MILLISECONDS)
                    val startStr = java.time.Instant.ofEpochMilli(start)
                        .atZone(java.time.ZoneId.systemDefault())
                    val endStr = java.time.Instant.ofEpochMilli(end)
                        .atZone(java.time.ZoneId.systemDefault())
                    val endDate = endStr.toLocalDate()
                    val duration = (end - start) / (1000 * 60) // minutes
                    val durationHours = duration / 60.0
                    Log.d(TAG, "  ✅ Session ${index + 1}: ${String.format("%.1f", durationHours)} hours (${duration} min)")
                    Log.d(TAG, "     Activity: ${session.activity}, Name: ${session.name}")
                    Log.d(TAG, "     App: ${session.appPackageName}")
                    Log.d(TAG, "     Ends on: $endDate at ${endStr.toLocalTime()} ⬅️ THIS DATE MATTERS")
                    Log.d(TAG, "     $startStr to $endStr")
                }
            }
            
            // CRITICAL FIX: Since we used .includeSleepSessions(), ALL sessions returned SHOULD be sleep
            // ACCEPT ALL SESSIONS - don't filter them out. The API should only return sleep.
            // If it returns something that's not sleep, that's a Google Fit API bug, not our problem.
            for (session in sessionResponse.sessions) {
                val start = session.getStartTime(TimeUnit.MILLISECONDS)
                val end = session.getEndTime(TimeUnit.MILLISECONDS)
                val durationMinutes = ((end - start) / (1000 * 60)).toInt()
                val startStr = java.time.Instant.ofEpochMilli(start).atZone(java.time.ZoneId.systemDefault())
                val endStr = java.time.Instant.ofEpochMilli(end).atZone(java.time.ZoneId.systemDefault())
                val endDate = endStr.toLocalDate()
                
                Log.d(TAG, "  Processing session from .includeSleepSessions():")
                Log.d(TAG, "    Activity: ${session.activity}, Name: ${session.name}, App: ${session.appPackageName}")
                Log.d(TAG, "    Duration: ${durationMinutes} min (${String.format("%.1f", durationMinutes / 60.0)} hours)")
                Log.d(TAG, "    Ends on: $endDate at ${endStr.toLocalTime()}")
                Log.d(TAG, "    $startStr to $endStr")
                
                // CRITICAL: Accept ALL sessions from .includeSleepSessions() - the API should only return sleep
                // We trust Google Fit's API to return only sleep when we use .includeSleepSessions()
                Log.d(TAG, "    ✅ ACCEPTED as sleep (from .includeSleepSessions() - trusting API)")
                sessions.add(SleepSession(start, end, durationMinutes, "sessions"))
            }
            
            if (sessionResponse.sessions.isNotEmpty() && sessions.isEmpty()) {
                Log.e(TAG, "  ❌❌❌ CRITICAL: .includeSleepSessions() returned ${sessionResponse.sessions.size} sessions but we accepted 0 ❌❌❌")
                Log.e(TAG, "     This should NEVER happen - there's a bug in our acceptance logic!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading sleep sessions", e)
        }
        return sessions
    }
    
    /**
     * BULLETPROOF sleep sync - simple, reliable, works every day
     * 
     * Strategy: Attribute sleep to the day it ENDS on (standard approach)
     * - Sleep from 11pm Dec 13 to 7am Dec 14 = Dec 14's sleep
     * - Get all sleep sessions ending on target day
     * - Pick the longest one (main sleep)
     * - Works consistently day-to-day, no complex overlap calculations
     */
    suspend fun readSleep(startTime: Long, endTime: Long, retryAttempt: Int = 0): List<SleepData> {
        Log.d(TAG, "=== READING SLEEP (BULLETPROOF) (Attempt ${retryAttempt + 1}) ===")
        val account = getAccount()
        if (account == null) {
            Log.e(TAG, "❌ No account - cannot read sleep")
            return emptyList()
        }
        
        try {
            // Target day boundaries
            val targetDateStart = startTime
            val targetDateEnd = endTime
            
            // Search window: Look back 24 hours from target date start (sleep from last night)
            // and forward 12 hours from target date end (in case of late syncs)
            // Sleep from last night (e.g., Dec 19 11pm to Dec 20 8:30am) only needs ~24 hour lookback
            val searchStart = targetDateStart - (24 * 60 * 60 * 1000) // 24 hours before target date start
            val searchEnd = targetDateEnd + (12 * 60 * 60 * 1000) // 12 hours after target date end
            
            val targetStartStr = java.time.Instant.ofEpochMilli(targetDateStart)
                .atZone(java.time.ZoneId.systemDefault())
            val targetEndStr = java.time.Instant.ofEpochMilli(targetDateEnd)
                .atZone(java.time.ZoneId.systemDefault())
            Log.d(TAG, "Target day range: $targetStartStr to $targetEndStr")
            Log.d(TAG, "Search window: ${java.time.Instant.ofEpochMilli(searchStart).atZone(java.time.ZoneId.systemDefault())} to ${java.time.Instant.ofEpochMilli(searchEnd).atZone(java.time.ZoneId.systemDefault())}")
            
            // Pull from both APIs (Samsung uses both)
            Log.d(TAG, "🔍 Querying Google Fit for sleep data...")
            Log.d(TAG, "  Search window: ${java.time.Instant.ofEpochMilli(searchStart).atZone(java.time.ZoneId.systemDefault())} to ${java.time.Instant.ofEpochMilli(searchEnd).atZone(java.time.ZoneId.systemDefault())}")
            Log.d(TAG, "  How we identify sleep:")
            Log.d(TAG, "    1. Sleep Segments API: Queries TYPE_SLEEP_SEGMENT (only returns sleep)")
            Log.d(TAG, "    2. Sleep Sessions API: Uses .includeSleepSessions() (only returns sleep)")
            Log.d(TAG, "    3. We accept ALL sessions returned by these APIs (they should only return sleep)")
            val segmentData = readSleepSegments(searchStart, searchEnd, account)
            val sessionData = readSleepSessions(searchStart, searchEnd, account)
            
            Log.d(TAG, "=========================================")
            Log.d(TAG, "📊 RAW GOOGLE FIT API RESPONSE:")
            Log.d(TAG, "  Sleep Segments API: ${segmentData.size} sessions")
            Log.d(TAG, "  Sleep Sessions API: ${sessionData.size} sessions")
            Log.d(TAG, "  TOTAL: ${segmentData.size + sessionData.size} sessions")
            if (segmentData.isEmpty() && sessionData.isEmpty()) {
                Log.e(TAG, "  ❌❌❌ GOOGLE FIT RETURNED 0 SLEEP SESSIONS ❌❌❌")
                Log.e(TAG, "     This means either:")
                Log.e(TAG, "       1. No sleep was logged in Google Fit")
                Log.e(TAG, "       2. Google Fit hasn't synced the sleep yet (common in morning)")
                Log.e(TAG, "       3. Sleep tracking is disabled in Google Fit")
                Log.e(TAG, "     ACTION: Open Google Fit app to check if sleep is visible there")
            }
            Log.d(TAG, "=========================================")
            
            // Combine all sessions
            val allSessions = mutableListOf<SleepSession>()
            allSessions.addAll(segmentData)
            allSessions.addAll(sessionData)
            
            if (allSessions.isEmpty()) {
                Log.d(TAG, "⚠️ No sleep sessions found in search window")
                return emptyList()
            }
            
            // Deduplicate: sessions within 2 minutes of each other are duplicates
            val unique = allSessions.filterIndexed { index, session ->
                allSessions.indexOfFirst { other ->
                    kotlin.math.abs(other.startTimeMillis - session.startTimeMillis) < 120000 && // 2 min
                    kotlin.math.abs(other.endTimeMillis - session.endTimeMillis) < 120000 // 2 min
                } == index
            }
            
            Log.d(TAG, "Deduplicated to ${unique.size} unique sessions")
            
            // Merge sessions with gaps <30 min (fixes Samsung midnight split)
            val sortedUnique = unique.sortedBy { it.startTimeMillis }
            val merged = mutableListOf<SleepSession>()
            for (session in sortedUnique) {
                val last = merged.lastOrNull()
                if (last != null && (session.startTimeMillis - last.endTimeMillis) < 30 * 60 * 1000) {
                    // Merge: extend last session
                    last.endTimeMillis = session.endTimeMillis
                    last.durationMinutes = ((last.endTimeMillis - last.startTimeMillis) / 60000).toInt()
                    Log.d(TAG, "  🔗 Merged: ${last.durationMinutes} min")
                } else {
                    merged.add(SleepSession(session.startTimeMillis, session.endTimeMillis, session.durationMinutes, session.source))
                }
            }
            
            Log.d(TAG, "Merged to ${merged.size} sessions")
            
            // Log ALL merged sessions BEFORE filtering to see what we have
            Log.d(TAG, "=========================================")
            Log.d(TAG, "📋 ALL MERGED SLEEP SESSIONS FROM GOOGLE FIT (before filtering):")
            Log.d(TAG, "=========================================")
            if (merged.isEmpty()) {
                Log.e(TAG, "  ❌❌❌ NO SLEEP SESSIONS FOUND AT ALL ❌❌❌")
                Log.e(TAG, "     This means Google Fit API returned 0 sleep sessions")
                Log.e(TAG, "     Search window was: ${java.time.Instant.ofEpochMilli(searchStart).atZone(java.time.ZoneId.systemDefault())} to ${java.time.Instant.ofEpochMilli(searchEnd).atZone(java.time.ZoneId.systemDefault())}")
                Log.e(TAG, "     Query time range: ${java.time.Instant.ofEpochMilli(targetDateStart).atZone(java.time.ZoneId.systemDefault())} to ${java.time.Instant.ofEpochMilli(targetDateEnd).atZone(java.time.ZoneId.systemDefault())}")
                Log.e(TAG, "     ACTION REQUIRED:")
                Log.e(TAG, "       1. Open Google Fit app manually")
                Log.e(TAG, "       2. Check if sleep is visible in Google Fit app")
                Log.e(TAG, "       3. Verify sleep data exists for the time range above")
                Log.e(TAG, "       4. Wait 30 seconds, then sync again")
            } else {
                Log.d(TAG, "  ✅ Found ${merged.size} sleep session(s) from Google Fit API:")
                merged.forEachIndexed { index, session ->
                    val sessionStartStr = java.time.Instant.ofEpochMilli(session.startTimeMillis)
                        .atZone(java.time.ZoneId.systemDefault())
                    val sessionEndStr = java.time.Instant.ofEpochMilli(session.endTimeMillis)
                        .atZone(java.time.ZoneId.systemDefault())
                    val sessionEndDate = sessionEndStr.toLocalDate()
                    val sessionStartDate = sessionStartStr.toLocalDate()
                    val durationHours = session.durationMinutes / 60.0
                    Log.d(TAG, "  ────────────────────────────────────────")
                    Log.d(TAG, "  Session ${index + 1}: ${String.format("%.1f", durationHours)} hours (${session.durationMinutes} min)")
                    Log.d(TAG, "     Start: $sessionStartStr")
                    Log.d(TAG, "     Start date: $sessionStartDate")
                    Log.d(TAG, "     End:   $sessionEndStr")
                    Log.d(TAG, "     End date:   $sessionEndDate ⬅️ THIS IS WHAT WE CHECK")
                    Log.d(TAG, "     Raw timestamps: start=${session.startTimeMillis}, end=${session.endTimeMillis}")
                }
                Log.d(TAG, "  ────────────────────────────────────────")
            }
            Log.d(TAG, "=========================================")
            
            // SIMPLE RULE: Find sleep that ENDS on the target date. Period.
            // Sleep from last night (e.g., Dec 19 11pm to Dec 20 8:30am) ENDS on Dec 20, so it's Dec 20's sleep.
            // We don't care when it started - we only care when it ended.
            
            // Get target date using the SAME timezone conversion as the session dates
            val targetDateStartInstant = java.time.Instant.ofEpochMilli(targetDateStart)
            val targetDate = targetDateStartInstant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            
            // CRITICAL: Log target date VERY clearly
            Log.d(TAG, "=========================================")
            Log.d(TAG, "🎯🎯🎯 TARGET DATE FOR SLEEP: $targetDate 🎯🎯🎯")
            Log.d(TAG, "🎯 SIMPLE RULE: Find sleep that ENDS on $targetDate")
            Log.d(TAG, "🎯 We don't care when it started - only when it ended")
            Log.d(TAG, "🎯 Target date start timestamp: $targetDateStart (${java.time.Instant.ofEpochMilli(targetDateStart).atZone(java.time.ZoneId.systemDefault())})")
            Log.d(TAG, "🎯 Target date end timestamp: $targetDateEnd (${java.time.Instant.ofEpochMilli(targetDateEnd).atZone(java.time.ZoneId.systemDefault())})")
            Log.d(TAG, "=========================================")
            
            // Find sleep that ends on target date OR is recent sleep from previous day (likely last night's sleep)
            // Google Fit sometimes records sleep ending on the wrong date, so we need to handle both cases
            val candidates = merged.filter { session ->
                // Convert session END time to LocalDate using the SAME timezone
                val sessionEndInstant = java.time.Instant.ofEpochMilli(session.endTimeMillis)
                val sessionEndZoned = sessionEndInstant.atZone(java.time.ZoneId.systemDefault())
                val sessionEndDate = sessionEndZoned.toLocalDate()
                val sessionEndTime = sessionEndZoned.toLocalTime()
                
                // Also get start time for logging
                val sessionStartInstant = java.time.Instant.ofEpochMilli(session.startTimeMillis)
                val sessionStartZoned = sessionStartInstant.atZone(java.time.ZoneId.systemDefault())
                val sessionStartDate = sessionStartZoned.toLocalDate()
                
                // PRIMARY CHECK: Does sleep END on target date?
                val endsOnTarget = sessionEndDate == targetDate
                
                // FALLBACK CHECK: If sleep ends on previous day, check if it's likely last night's sleep
                // Google Fit sometimes records sleep ending on wrong date, so we need to be more lenient
                val yesterday = targetDate.minusDays(1)
                val endsOnYesterday = sessionEndDate == yesterday
                
                // Accept sleep from yesterday if:
                // 1. It ended within the last 36 hours (more lenient than 24 hours)
                // 2. OR it's the most recent sleep session we found (likely last night's sleep)
                val hoursSinceTargetStart = (targetDateStart - session.endTimeMillis) / (1000.0 * 60.0 * 60.0)
                val isRecent = hoursSinceTargetStart <= 36.0 // Within last 36 hours (more lenient)
                val isMostRecent = merged.maxByOrNull { it.endTimeMillis }?.endTimeMillis == session.endTimeMillis
                
                // Accept if: ends on target date OR (ends on yesterday AND (is recent OR is most recent))
                val isValid = endsOnTarget || (endsOnYesterday && (isRecent || isMostRecent))
                
                if (isValid) {
                    val reason = when {
                        endsOnTarget -> "ENDS on target date"
                        isMostRecent -> "ENDS on previous day but is the most recent sleep (likely last night)"
                        isRecent -> "ENDS on previous day but is recent (within 36 hours)"
                        else -> "ENDS on previous day (fallback acceptance)"
                    }
                    Log.d(TAG, "  ✅✅✅ ACCEPTED: ${session.durationMinutes} min (${String.format("%.1f", session.durationMinutes / 60.0)} hours)")
                    Log.d(TAG, "     Reason: $reason")
                    Log.d(TAG, "     Sleep ENDS on: $sessionEndDate at $sessionEndTime (target=$targetDate)")
                    Log.d(TAG, "     Sleep started: $sessionStartZoned (date: $sessionStartDate)")
                    Log.d(TAG, "     Sleep ended:   $sessionEndZoned (date: $sessionEndDate)")
                    if (endsOnYesterday) {
                        Log.d(TAG, "     Hours since target start: ${String.format("%.1f", hoursSinceTargetStart)}")
                        Log.d(TAG, "     Is most recent: $isMostRecent")
                    }
                } else {
                    Log.e(TAG, "  ❌ REJECTED: ${session.durationMinutes} min (${String.format("%.1f", session.durationMinutes / 60.0)} hours)")
                    Log.e(TAG, "     Sleep ENDS on: $sessionEndDate at $sessionEndTime")
                    Log.e(TAG, "     Target date:   $targetDate")
                    Log.e(TAG, "     Checks: endsOnTarget=$endsOnTarget, endsOnYesterday=$endsOnYesterday, isRecent=$isRecent, isMostRecent=$isMostRecent")
                    Log.e(TAG, "     Hours since target start: ${String.format("%.1f", hoursSinceTargetStart)}")
                    Log.e(TAG, "     Sleep: $sessionStartZoned (date: $sessionStartDate) to $sessionEndZoned (date: $sessionEndDate)")
                    Log.e(TAG, "     ⚠️ REJECTED - doesn't match acceptance criteria")
                }
                isValid
            }
            
            if (candidates.isEmpty()) {
                Log.e(TAG, "=========================================")
                Log.e(TAG, "❌❌❌ NO SLEEP FOUND FOR TARGET DATE $targetDate ❌❌❌")
                Log.e(TAG, "=========================================")
                if (merged.isNotEmpty()) {
                    Log.e(TAG, "  ⚠️ Found ${merged.size} sleep session(s) from Google Fit, but NONE end on target date $targetDate")
                    Log.e(TAG, "  Target date: $targetDate")
                    Log.e(TAG, "  Sleep sessions found (showing end dates):")
                    merged.forEachIndexed { index, session ->
                        val sessionStartStr = java.time.Instant.ofEpochMilli(session.startTimeMillis)
                            .atZone(java.time.ZoneId.systemDefault())
                        val sessionEndStr = java.time.Instant.ofEpochMilli(session.endTimeMillis)
                            .atZone(java.time.ZoneId.systemDefault())
                        val sessionEndDate = sessionEndStr.toLocalDate()
                        val sessionStartDate = sessionStartStr.toLocalDate()
                        Log.e(TAG, "    ${index + 1}. Ends on: $sessionEndDate (target=$targetDate) - ${session.durationMinutes} min")
                        Log.e(TAG, "       $sessionStartStr (date: $sessionStartDate) to $sessionEndStr (date: $sessionEndDate)")
                    }
                    Log.e(TAG, "  ⚠️ DIAGNOSIS: Google Fit returned sleep, but none end on $targetDate")
                    Log.e(TAG, "     This could mean:")
                    Log.e(TAG, "       1. Sleep hasn't been finalized in Google Fit yet")
                    Log.e(TAG, "       2. Sleep is recorded with a different end date than expected")
                    Log.e(TAG, "       3. Timezone issue causing date mismatch")
                } else {
                    Log.e(TAG, "  ⚠️ No sleep sessions found at all in search window")
                    Log.e(TAG, "     Search window: ${java.time.Instant.ofEpochMilli(searchStart).atZone(java.time.ZoneId.systemDefault())} to ${java.time.Instant.ofEpochMilli(searchEnd).atZone(java.time.ZoneId.systemDefault())}")
                    Log.e(TAG, "     This means Google Fit API returned 0 sleep sessions")
                }
                Log.e(TAG, "=========================================")
                
                // CRITICAL FIX: Retry if no candidates found (even if we got sessions from API)
                // Google Fit API can be delayed - sleep might not be synced yet
                // Retry if: 1) No sessions at all, OR 2) We got sessions but none match target date
                // Use longer delays for morning syncs when sleep data may not be finalized yet
                if (retryAttempt < 2) { // Allow up to 2 retries (3 total attempts)
                    val delayMs = when {
                        merged.isEmpty() -> 3000L // No sessions at all - shorter delay
                        retryAttempt == 0 -> 8000L // First retry - longer delay for Google Fit to sync
                        else -> 10000L // Second retry - even longer delay
                    }
                    Log.w(TAG, "  ⚠️ No sleep for target date - waiting ${delayMs}ms for Google Fit to sync... (attempt ${retryAttempt + 1}/3)")
                    Log.w(TAG, "     (This is normal if you just woke up - Google Fit may need time to sync)")
                    Log.w(TAG, "     Found ${merged.size} sessions but none match target date $targetDate")
                    delay(delayMs)
                    val retrySleep = readSleep(startTime, endTime, retryAttempt + 1)
                    if (retrySleep.isNotEmpty()) {
                        Log.d(TAG, "  ✅✅✅ Sleep found after retry: ${retrySleep.size} sessions ✅✅✅")
                        return retrySleep
                    } else {
                        Log.w(TAG, "  ⚠️ Still no sleep after retry ${retryAttempt + 1} - will retry again if attempts remaining")
                    }
                } else {
                    Log.w(TAG, "  ⚠️⚠️⚠️ No sleep found after all retries - Google Fit may not have synced yet ⚠️⚠️⚠️")
                    Log.w(TAG, "     ACTION: Open Google Fit app to force sync, then try again")
                }
                
                return emptyList()
            }
            
            // Pick the longest session (main sleep)
            val mainSleep = candidates.maxByOrNull { it.durationMinutes }
            
            if (mainSleep != null) {
                val durationHours = mainSleep.durationMinutes / 60.0
                val sessionStartStr = java.time.Instant.ofEpochMilli(mainSleep.startTimeMillis)
                    .atZone(java.time.ZoneId.systemDefault())
                val sessionEndStr = java.time.Instant.ofEpochMilli(mainSleep.endTimeMillis)
                    .atZone(java.time.ZoneId.systemDefault())
                Log.d(TAG, "✅ Main sleep: ${String.format("%.1f", durationHours)} hours")
                Log.d(TAG, "   $sessionStartStr to $sessionEndStr")
                return listOf(SleepData(mainSleep.startTimeMillis, mainSleep.endTimeMillis))
            } else {
                Log.e(TAG, "❌ ERROR: Found candidates but maxByOrNull returned null")
                return emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reading sleep", e)
            e.printStackTrace()
            return emptyList()
        }
    }
    
    /**
     * Internal data class for sleep session processing
     */
    private data class SleepSession(
        var startTimeMillis: Long,
        var endTimeMillis: Long,
        var durationMinutes: Int,
        var source: String? = null
    )
    
    /**
     * Detect walks from step patterns - if we see sustained step activity, infer a walk
     */
    private suspend fun detectWalksFromSteps(startTime: Long, endTime: Long, account: GoogleSignInAccount): List<WorkoutData> {
        val walks = mutableListOf<WorkoutData>()
        try {
            Log.d(TAG, "Attempting to detect walks from step data...")
            // Read step count data with timestamps
            val request = DataReadRequest.Builder()
                .read(DataType.TYPE_STEP_COUNT_DELTA)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .bucketByTime(5, TimeUnit.MINUTES) // Bucket into 5-minute intervals
                .build()
            
            val response = Fitness.getHistoryClient(context, account)
                .readData(request)
                .await()
            
            var currentWalkStart: Long? = null
            var currentWalkSteps = 0
            var lastStepTime: Long? = null
            
            for (bucket in response.buckets) {
                var bucketSteps = 0
                for (dataSet in bucket.dataSets) {
                    for (dataPoint in dataSet.dataPoints) {
                        val steps = dataPoint.getValue(Field.FIELD_STEPS).asInt()
                        bucketSteps += steps
                    }
                }
                
                if (bucketSteps > 0) {
                    val bucketStart = bucket.getStartTime(TimeUnit.MILLISECONDS)
                    val bucketEnd = bucket.getEndTime(TimeUnit.MILLISECONDS)
                    
                    // If we have significant steps in this bucket (50+ steps in 5 min = walking pace)
                    if (bucketSteps >= 50) {
                        if (currentWalkStart == null) {
                            // Start a new walk
                            currentWalkStart = bucketStart
                            currentWalkSteps = bucketSteps
                            lastStepTime = bucketEnd
                            Log.d(TAG, "  🚶 Walk started: ${bucketSteps} steps in 5 min bucket")
                        } else {
                            // Continue existing walk
                            currentWalkSteps += bucketSteps
                            lastStepTime = bucketEnd
                        }
                    } else if (currentWalkStart != null && lastStepTime != null) {
                        // Gap in activity - check if we have a valid walk
                        val gap = bucketStart - lastStepTime!!
                        if (gap > 10 * 60 * 1000) { // 10 minute gap = walk ended
                            val walkDuration = (lastStepTime!! - currentWalkStart!!) / (1000 * 60) // minutes
                            if (walkDuration >= 10 && currentWalkSteps >= 200) { // At least 10 min and 200 steps
                                val walk = WorkoutData("Walking", walkDuration.toInt(), 0, currentWalkStart!!)
                                walks.add(walk)
                                Log.d(TAG, "  ✅ Detected walk: ${walkDuration} min, ${currentWalkSteps} steps")
                                // Write the detected walk to Google Fit
                                try {
                                    writeWorkout(walk)
                                } catch (e: Exception) {
                                    Log.w(TAG, "  ⚠️ Failed to write detected walk to Google Fit", e)
                                }
                            }
                            currentWalkStart = null
                            currentWalkSteps = 0
                            lastStepTime = null
                        }
                    }
                }
            }
            
            // Check if we have an ongoing walk at the end
            if (currentWalkStart != null && lastStepTime != null) {
                val walkDuration = (lastStepTime!! - currentWalkStart!!) / (1000 * 60) // minutes
                if (walkDuration >= 10 && currentWalkSteps >= 200) {
                    val walk = WorkoutData("Walking", walkDuration.toInt(), 0, currentWalkStart!!)
                    walks.add(walk)
                    Log.d(TAG, "  ✅ Detected walk (ongoing): ${walkDuration} min, ${currentWalkSteps} steps")
                    // Write the detected walk to Google Fit
                    try {
                        writeWorkout(walk)
                    } catch (e: Exception) {
                        Log.w(TAG, "  ⚠️ Failed to write detected walk to Google Fit", e)
                    }
                }
            }
            
            if (walks.isEmpty()) {
                Log.d(TAG, "  ⏭️ No walks detected from step patterns")
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Error detecting walks from steps", e)
        }
        return walks
    }
    
    suspend fun readWorkouts(queryStartTime: Long, queryEndTime: Long, filterStartTime: Long? = null, filterEndTime: Long? = null, retryAttempt: Int = 0): List<WorkoutData> {
        Log.d(TAG, "=========================================")
        Log.d(TAG, "🏋️ READING WORKOUTS FROM GOOGLE FIT 🏋️ (Attempt ${retryAttempt + 1})")
        Log.d(TAG, "=========================================")
        val queryStartStr = java.time.Instant.ofEpochMilli(queryStartTime).atZone(java.time.ZoneId.systemDefault())
        val queryEndStr = java.time.Instant.ofEpochMilli(queryEndTime).atZone(java.time.ZoneId.systemDefault())
        Log.d(TAG, "Query time range: $queryStartTime to $queryEndTime")
        Log.d(TAG, "  Query Start: $queryStartStr")
        Log.d(TAG, "  Query End: $queryEndStr")
        
        // Use filter times if provided, otherwise don't filter (return all workouts in query range)
        // This allows the caller to do their own filtering with custom logic
        val shouldFilter = filterStartTime != null && filterEndTime != null
        val filterStart = filterStartTime ?: 0L
        val filterEnd = filterEndTime ?: Long.MAX_VALUE
        if (shouldFilter) {
            val filterStartStr = java.time.Instant.ofEpochMilli(filterStart).atZone(java.time.ZoneId.systemDefault())
            val filterEndStr = java.time.Instant.ofEpochMilli(filterEnd).atZone(java.time.ZoneId.systemDefault())
            Log.d(TAG, "Filter time range: $filterStart to $filterEnd")
            Log.d(TAG, "  Filter Start: $filterStartStr")
            Log.d(TAG, "  Filter End: $filterEndStr")
        } else {
            Log.d(TAG, "No filter time range provided - will return all workouts in query range (caller will filter)")
        }
        
        val account = getAccount()
        if (account == null) {
            Log.e(TAG, "❌❌❌ NO GOOGLE ACCOUNT - CANNOT READ WORKOUTS ❌❌❌")
            return emptyList()
        }
        Log.d(TAG, "✅ Google account found: ${account.email}")
        Log.d(TAG, "  Account ID: ${account.id}")
        Log.d(TAG, "  Granted scopes: ${account.grantedScopes}")
        
        // Verify account has fitness permissions
        val hasFitnessPerms = GoogleSignIn.hasPermissions(account, fitnessOptions)
        Log.d(TAG, "  Has fitness permissions: $hasFitnessPerms")
        if (!hasFitnessPerms) {
            Log.e(TAG, "❌❌❌ ACCOUNT MISSING FITNESS PERMISSIONS ❌❌❌")
            return emptyList()
        }
        
        val workouts = mutableListOf<WorkoutData>()
        val processedWorkoutIds = HashSet<String>() // To prevent duplicates
        
        // ALWAYS try Sessions API FIRST (Samsung Health stores workouts as sessions)
        // This is the primary source for Samsung Health data
        var sessionsApiWorked = false
        try {
            Log.d(TAG, "--- STEP 1: Checking Sessions API for workouts ---")
            Log.d(TAG, "  (Samsung Health, Google Fit, and other apps store workouts as sessions)")
            
            // Use the query time range (already expanded by caller)
            Log.d(TAG, "  Query time range: ${java.time.Instant.ofEpochMilli(queryStartTime).atZone(java.time.ZoneId.systemDefault())} to ${java.time.Instant.ofEpochMilli(queryEndTime).atZone(java.time.ZoneId.systemDefault())}")
            Log.d(TAG, "  Filter time range: ${java.time.Instant.ofEpochMilli(filterStart).atZone(java.time.ZoneId.systemDefault())} to ${java.time.Instant.ofEpochMilli(filterEnd).atZone(java.time.ZoneId.systemDefault())}")
            
            // CRITICAL: Query ALL sessions without any filtering - let the API return everything
            // Then we'll filter in code. This ensures we get Samsung Health sessions.
            val sessionRequest = SessionReadRequest.Builder()
                .readSessionsFromAllApps()
                // DON'T use .includeSleepSessions() or any activity filters - get EVERYTHING
                .setTimeInterval(queryStartTime, queryEndTime, TimeUnit.MILLISECONDS)
                .build()

            Log.d(TAG, "  Sending Sessions API request...")
            Log.d(TAG, "  Account: ${account.email}, ID: ${account.id}")
            Log.d(TAG, "  Request time range: ${queryStartTime} to ${queryEndTime}")
            
            val sessionResponse = try {
                val client = Fitness.getSessionsClient(context, account)
                Log.d(TAG, "  Sessions client created, calling readSession...")
                Log.d(TAG, "  Request details:")
                Log.d(TAG, "    - Time range: ${java.time.Instant.ofEpochMilli(queryStartTime).atZone(java.time.ZoneId.systemDefault())} to ${java.time.Instant.ofEpochMilli(queryEndTime).atZone(java.time.ZoneId.systemDefault())}")
                Log.d(TAG, "    - Account: ${account.email} (${account.id})")
                Log.d(TAG, "    - Permissions: ${GoogleSignIn.hasPermissions(account, fitnessOptions)}")
                
                val response = client.readSession(sessionRequest).await()
                Log.d(TAG, "  ✅✅✅ API CALL SUCCEEDED - Got ${response.sessions.size} sessions ✅✅✅")
                
                if (response.sessions.isEmpty()) {
                    Log.w(TAG, "  ⚠️⚠️⚠️ API returned 0 sessions - this is the problem ⚠️⚠️⚠️")
                    Log.w(TAG, "  This means Google Fit API is working but has no session data")
                    Log.w(TAG, "  Possible causes:")
                    Log.w(TAG, "    1. No workouts logged in Google Fit for this time range")
                    Log.w(TAG, "    2. Workouts are stored as activity segments, not sessions")
                    Log.w(TAG, "    3. Time range is wrong (check timezone)")
                }
                
                response
            } catch (e: Exception) {
                Log.e(TAG, "❌❌❌ EXCEPTION READING WORKOUT SESSIONS ❌❌❌", e)
                Log.e(TAG, "  Exception type: ${e.javaClass.simpleName}")
                Log.e(TAG, "  Exception message: ${e.message}")
                Log.e(TAG, "  Account: ${account.email}")
                Log.e(TAG, "  Has fitness permissions: ${GoogleSignIn.hasPermissions(account, fitnessOptions)}")
                Log.e(TAG, "  This exception is preventing workout sync - will try activity segments instead")
                e.printStackTrace()
                sessionsApiWorked = false
                // Skip sessions API and continue to activity segments
                // Create a null response that will be handled below
                null
            }
            
            if (sessionResponse == null) {
                Log.w(TAG, "  ⚠️ Sessions API failed - will try activity segments")
                sessionsApiWorked = false
            } else {
                sessionsApiWorked = true
                Log.d(TAG, "  ✅ Sessions API response: ${sessionResponse.sessions.size} sessions found")
                
                // CRITICAL: Log EVERY session we get, even if we filter it out later
                if (sessionResponse.sessions.isEmpty()) {
                    Log.e(TAG, "  ❌❌❌ NO SESSIONS RETURNED FROM API - THIS IS THE PROBLEM ❌❌❌")
                    Log.e(TAG, "  Query time range: ${java.time.Instant.ofEpochMilli(queryStartTime).atZone(java.time.ZoneId.systemDefault())} to ${java.time.Instant.ofEpochMilli(queryEndTime).atZone(java.time.ZoneId.systemDefault())}")
                    Log.e(TAG, "  This means either:")
                    Log.e(TAG, "    1. Google Fit API hasn't synced the workout yet (can take hours)")
                    Log.e(TAG, "    2. The workout is stored in a different format/data type")
                    Log.e(TAG, "    3. The time range is wrong")
                    Log.e(TAG, "    4. Workout app isn't syncing to Google Fit properly")
                    Log.e(TAG, "    5. Check Google Fit app > Settings > Connected apps - ensure your workout app is connected")
                    Log.e(TAG, "    6. Try manually opening Google Fit app to force a sync")
                } else {
                    Log.d(TAG, "  📋 RAW SESSIONS FROM API (before any filtering):")
                    sessionResponse.sessions.forEachIndexed { index, session ->
                        val sessionStart = session.getStartTime(TimeUnit.MILLISECONDS)
                        val sessionEnd = session.getEndTime(TimeUnit.MILLISECONDS)
                        val sessionDuration = (sessionEnd - sessionStart) / (1000 * 60)
                        Log.d(TAG, "    Session ${index + 1}:")
                        Log.d(TAG, "      - Activity: ${session.activity}")
                        Log.d(TAG, "      - Name: ${session.name}")
                        Log.d(TAG, "      - App: ${session.appPackageName}")
                        Log.d(TAG, "      - Start: ${java.time.Instant.ofEpochMilli(sessionStart).atZone(java.time.ZoneId.systemDefault())}")
                        Log.d(TAG, "      - End: ${java.time.Instant.ofEpochMilli(sessionEnd).atZone(java.time.ZoneId.systemDefault())}")
                        Log.d(TAG, "      - Duration: $sessionDuration min")
                        Log.d(TAG, "      - ID: ${session.identifier}")
                    }
                }
                
                for (session in sessionResponse.sessions) {
                val sessionId = "${session.getStartTime(TimeUnit.MILLISECONDS)}_${session.getEndTime(TimeUnit.MILLISECONDS)}"
                if (processedWorkoutIds.contains(sessionId)) {
                    Log.d(TAG, "  ⏭️ Skipping duplicate session: $sessionId")
                    continue
                }
                
                val activityType = session.activity
                val sessionName = session.name?.lowercase() ?: ""
                val start = session.getStartTime(TimeUnit.MILLISECONDS)
                val end = session.getEndTime(TimeUnit.MILLISECONDS)
                val duration = (end - start) / (1000 * 60) // minutes
                
                Log.d(TAG, "  Session: activity=$activityType, name=${session.name}, duration=${duration} min")
                Log.d(TAG, "    - App: ${session.appPackageName}")
                Log.d(TAG, "    - Start: ${java.time.Instant.ofEpochMilli(start).atZone(java.time.ZoneId.systemDefault())}")
                Log.d(TAG, "    - End: ${java.time.Instant.ofEpochMilli(end).atZone(java.time.ZoneId.systemDefault())}")
                
                // Filter out sleep sessions
                val isSleep = activityType?.lowercase()?.contains("sleep") == true || 
                             sessionName.contains("sleep") == true ||
                             activityType == "72" // SLEEP activity type code
                
                // Check for workout activities - include walking, running, cycling, etc.
                val activityTypeLower = activityType?.lowercase() ?: ""
                val sessionNameLower = sessionName.lowercase()
                val isWalking = activityTypeLower.contains("walk") || sessionNameLower.contains("walk")
                val isRunning = activityTypeLower.contains("run") || sessionNameLower.contains("run")
                val isCycling = activityTypeLower.contains("bike") || activityTypeLower.contains("cycle") || sessionNameLower.contains("bike") || sessionNameLower.contains("cycle")
                
                // CRITICAL: Only exclude CLEARLY non-workout activities
                // Be VERY permissive - if it's not clearly "still", "in_vehicle", etc., include it
                val isNonWorkout = activityTypeLower.contains("still") || 
                                  activityTypeLower.contains("in_vehicle") || 
                                  activityTypeLower.contains("tilting") || 
                                  (activityTypeLower.contains("unknown") && !sessionNameLower.contains("workout") && !sessionNameLower.contains("exercise")) ||
                                  activityType == "0" || 
                                  activityType.isNullOrEmpty()
                
                // CRITICAL FIX: Be EXTREMELY inclusive - ANY activity that isn't sleep and isn't clearly non-workout
                // Include ALL activities with duration > 0 that aren't sleep and aren't clearly stationary/vehicle
                // This catches workouts even with unusual activity types (yoga, strength, swimming, etc.)
                val isWorkout = !isSleep && 
                               !isNonWorkout && // Exclude only clearly non-workout activities
                               activityType != null && 
                               duration > 0 // ANY duration > 0, no minimum
                
                // Log why workouts are being filtered out for debugging
                if (!isWorkout && duration > 0 && activityType != null) {
                    val reason = when {
                        isSleep -> "sleep session"
                        isNonWorkout -> "non-workout activity ($activityType)"
                        else -> "unknown reason"
                    }
                    Log.d(TAG, "  ⏭️ Filtered out session: $reason (activity=$activityType, duration=$duration min, name=${session.name})")
                }
                
                // Normalize activity type for walking
                val normalizedActivityType = when {
                    isWalking -> "Walking"
                    isRunning -> "Running"
                    isCycling -> "Cycling"
                    else -> activityType
                }
                
                // CRITICAL: Only filter if filter times were provided, otherwise return all workouts
                // This allows the caller to do their own filtering with custom logic
                val workoutStartTime = start
                val workoutEndTime = end
                val isInFilterTimeRange = if (shouldFilter) {
                    val now = System.currentTimeMillis()
                    val twentyFourHoursAgo = now - (24 * 60 * 60 * 1000L)
                    val startsInRange = workoutStartTime >= filterStart && workoutStartTime <= filterEnd
                    val endsInRange = workoutEndTime >= filterStart && workoutEndTime <= filterEnd
                    val endedRecently = workoutEndTime >= twentyFourHoursAgo && workoutEndTime <= now
                    startsInRange || endsInRange || endedRecently
                } else {
                    true // No filtering - return all workouts in query range
                }
                
                if (shouldFilter && !isInFilterTimeRange) {
                    Log.d(TAG, "  ⏭️ Workout outside filter time range (start: ${java.time.Instant.ofEpochMilli(workoutStartTime).atZone(java.time.ZoneId.systemDefault())}, end: ${java.time.Instant.ofEpochMilli(workoutEndTime).atZone(java.time.ZoneId.systemDefault())}, filter: ${java.time.Instant.ofEpochMilli(filterStart).atZone(java.time.ZoneId.systemDefault())} to ${java.time.Instant.ofEpochMilli(filterEnd).atZone(java.time.ZoneId.systemDefault())})")
                }
                
                if (isWorkout && isInFilterTimeRange) {
                    // Extract calories burned from the session's time range using a separate DataReadRequest
                    var caloriesBurned = 0
                    try {
                        val caloriesRequest = DataReadRequest.Builder()
                            .read(DataType.TYPE_CALORIES_EXPENDED)
                            .setTimeRange(start, end, TimeUnit.MILLISECONDS)
                            .build()
                        
                        val caloriesResponse = Fitness.getHistoryClient(context, account)
                            .readData(caloriesRequest)
                            .await()
                        
                        for (dataSet in caloriesResponse.dataSets) {
                            if (dataSet.dataType == DataType.TYPE_CALORIES_EXPENDED) {
                                for (dataPoint in dataSet.dataPoints) {
                                    caloriesBurned += dataPoint.getValue(Field.FIELD_CALORIES).asFloat().roundToInt()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "  ⚠️ Could not read calories for workout session", e)
                        // Continue with caloriesBurned = 0
                    }
                    
                    workouts.add(WorkoutData(normalizedActivityType, duration.toInt(), caloriesBurned, start))
                    processedWorkoutIds.add(sessionId)
                    Log.d(TAG, "  ✅ Workout from Sessions API: $normalizedActivityType, ${duration.toInt()} min, ${caloriesBurned} cal")
                    Log.d(TAG, "    - Original activity: $activityType, Name: ${session.name}")
                        } else if (isWorkout && shouldFilter && !isInFilterTimeRange) {
                            Log.d(TAG, "  ⏭️ Workout filtered out: outside time range (start: ${java.time.Instant.ofEpochMilli(workoutStartTime).atZone(java.time.ZoneId.systemDefault())}, end: ${java.time.Instant.ofEpochMilli(workoutEndTime).atZone(java.time.ZoneId.systemDefault())})")
                } else {
                    val reason = when {
                        isSleep -> "sleep session"
                        activityType == null -> "null activity type"
                        duration <= 0 -> "zero duration"
                        isNonWorkout -> "non-workout activity"
                        else -> "unknown"
                    }
                    Log.d(TAG, "  ⏭️ Filtered out: $reason")
                }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ CRITICAL ERROR reading workouts from Sessions API ❌❌❌", e)
            Log.e(TAG, "  Error type: ${e.javaClass.simpleName}")
            Log.e(TAG, "  Error message: ${e.message}")
            e.printStackTrace()
            sessionsApiWorked = false
        }
        
        // Then, try DataReadRequest for activity segments (for other apps that might not use sessions)
        // CRITICAL: Always try this even if Sessions API worked, as some apps only use activity segments
        var activitySegmentsApiWorked = false
        try {
            Log.d(TAG, "--- STEP 2: Requesting workout data from Activity Segments API ---")
            Log.d(TAG, "  (Some apps store workouts as activity segments, not sessions)")
            val request = DataReadRequest.Builder()
                .read(DataType.TYPE_ACTIVITY_SEGMENT)
                .setTimeRange(queryStartTime, queryEndTime, TimeUnit.MILLISECONDS)
                .build()

            Log.d(TAG, "  Sending Activity Segments API request...")
            val response = Fitness.getHistoryClient(context, account)
                .readData(request)
                .await()
            
            activitySegmentsApiWorked = true
            Log.d(TAG, "  ✅ Activity Segments API response: ${response.dataSets.size} datasets")
            if (response.dataSets.isEmpty()) {
                Log.w(TAG, "  ⚠️ No activity segment datasets returned from Google Fit")
            } else {
                var totalDataPoints = 0
                response.dataSets.forEach { dataSet ->
                    totalDataPoints += dataSet.dataPoints.size
                }
                Log.d(TAG, "  📊 Total activity segment data points: $totalDataPoints")
            }
            
            for (dataSet in response.dataSets) {
                Log.d(TAG, "  Processing dataset: ${dataSet.dataPoints.size} data points")
                if (dataSet.dataPoints.isEmpty()) {
                    Log.w(TAG, "    ⚠️ Empty dataset - no activity segments found")
                }
                for (dataPoint in dataSet.dataPoints) {
                    try {
                        val activity = dataPoint.getValue(Field.FIELD_ACTIVITY).asActivity()
                        val start = dataPoint.getStartTime(TimeUnit.MILLISECONDS)
                        val end = dataPoint.getEndTime(TimeUnit.MILLISECONDS)
                        val duration = (end - start) / (1000 * 60) // minutes
                        
                        // Create a unique ID for this segment to check against processed sessions
                        val segmentId = "${start}_${end}"
                        if (processedWorkoutIds.contains(segmentId)) {
                            Log.d(TAG, "  ⏭️ Skipping duplicate activity segment: $segmentId")
                            continue
                        }
                        
                        val startTimeStr = java.time.Instant.ofEpochMilli(start).atZone(java.time.ZoneId.systemDefault())
                        val endTimeStr = java.time.Instant.ofEpochMilli(end).atZone(java.time.ZoneId.systemDefault())
                        Log.d(TAG, "    Activity segment: $activity, ${duration} min")
                        Log.d(TAG, "      - Start: $startTimeStr")
                        Log.d(TAG, "      - End: $endTimeStr")
                        Log.d(TAG, "      - Raw activity value: '$activity'")
                        
                        // CRITICAL: Only exclude CLEARLY non-workout activities
                        // Be VERY permissive - if it's not clearly "still", "in_vehicle", etc., include it
                        val activityLower = activity.lowercase()
                        val isNonWorkout = activityLower.contains("still") || 
                                          activityLower.contains("in_vehicle") || 
                                          activityLower.contains("tilting") || 
                                          activityLower.contains("unknown") || // Note: "unknown" might be a workout, but we exclude it to be safe
                                          activity == "0" || 
                                          activity.isEmpty()
                        
                        // Explicitly detect walking activities
                        val isWalking = activityLower.contains("walking") ||
                                      activityLower.contains("walk") ||
                                      activity == "8" // WALKING activity type code
                        
                        var finalActivityType = activity
                        if (isWalking) {
                            finalActivityType = "Walking"
                        }
                        
                        // CRITICAL: Be VERY inclusive - ANY duration > 0 (match Sessions API logic)
                        // Don't filter by minimum duration - catch all workouts including short ones
                        // Exclude only clearly non-workout activities
                        val isWorkout = !isNonWorkout && duration > 0
                        
                        // CRITICAL: Only filter if filter times were provided, otherwise return all workouts
                        // This matches the Sessions API filtering logic for consistency
                        val isInFilterTimeRange = if (shouldFilter) {
                            val now = System.currentTimeMillis()
                            val twentyFourHoursAgo = now - (24 * 60 * 60 * 1000L)
                            val startsInRange = start >= filterStart && start <= filterEnd
                            val endsInRange = end >= filterStart && end <= filterEnd
                            val endedRecently = end >= twentyFourHoursAgo && end <= now
                            startsInRange || endsInRange || endedRecently
                        } else {
                            true // No filtering - return all workouts in query range
                        }
                        
                        Log.d(TAG, "      - isNonWorkout: $isNonWorkout, isWalking: $isWalking, duration: $duration, isWorkout: $isWorkout")
                        
                        if (isWorkout && isInFilterTimeRange) {
                            workouts.add(WorkoutData(finalActivityType, duration.toInt(), 0, start)) // Calories not available from segments
                            processedWorkoutIds.add(segmentId)
                            Log.d(TAG, "  ✅ Workout from Activity Segments: $finalActivityType, ${duration.toInt()} minutes")
                        } else if (isWorkout && shouldFilter && !isInFilterTimeRange) {
                            Log.d(TAG, "  ⏭️ Workout filtered out: outside time range (start: ${java.time.Instant.ofEpochMilli(start).atZone(java.time.ZoneId.systemDefault())}, end: ${java.time.Instant.ofEpochMilli(end).atZone(java.time.ZoneId.systemDefault())})")
                        } else {
                            Log.d(TAG, "  ⏭️ Filtered out (non-workout activity segment): $activity")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing activity segment", e)
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ CRITICAL ERROR reading workouts from Activity Segments ❌❌❌", e)
            Log.e(TAG, "  Error type: ${e.javaClass.simpleName}")
            Log.e(TAG, "  Error message: ${e.message}")
            e.printStackTrace()
            activitySegmentsApiWorked = false
        }
        
        // If no explicit workouts found, try to detect walks from step patterns
        if (workouts.isEmpty()) {
            Log.d(TAG, "--- STEP 3: No explicit workouts found, attempting to detect walks from step patterns ---")
            try {
                val detectedWalks = detectWalksFromSteps(queryStartTime, queryEndTime, account)
                if (detectedWalks.isNotEmpty()) {
                    Log.d(TAG, "  ✅ Detected ${detectedWalks.size} walks from step patterns")
                    workouts.addAll(detectedWalks)
                } else {
                    Log.d(TAG, "  ⚠️ No walks detected from step patterns")
                }
            } catch (e: Exception) {
                Log.e(TAG, "  ❌ Error detecting walks from steps", e)
            }
        }
        
        Log.d(TAG, "=========================================")
        Log.d(TAG, "📊 WORKOUT SYNC SUMMARY:")
        Log.d(TAG, "  Sessions API: ${if (sessionsApiWorked) "✅ worked" else "❌ failed"}")
        Log.d(TAG, "  Activity Segments API: ${if (activitySegmentsApiWorked) "✅ worked" else "❌ failed"}")
        Log.d(TAG, "  Total workouts found: ${workouts.size}")
        if (workouts.isNotEmpty()) {
            workouts.forEachIndexed { index, workout ->
                Log.d(TAG, "    ${index + 1}. ${workout.activityType} - ${workout.durationMin} min, ${workout.caloriesBurned} cal")
            }
        } else {
            Log.w(TAG, "  ⚠️ No workouts found for query range ${queryStartStr} to ${queryEndStr}")
            Log.w(TAG, "  This could mean:")
            Log.w(TAG, "    1. No workout was logged in Google Fit for this time range")
            Log.w(TAG, "    2. Google Fit hasn't synced the workout yet (check Google Fit app)")
            Log.w(TAG, "    3. Workout app isn't syncing to Google Fit")
            // SIMPLIFIED: No aggressive retries - if workouts aren't found, they're not in Google Fit yet
            // User can manually sync again later if needed
        }
        Log.d(TAG, "=========================================")
        return workouts
    }
    
    /**
     * Write a workout session to Google Fit
     * This will create a workout session that appears in Google Fit
     */
    suspend fun writeWorkout(workout: WorkoutData): Boolean {
        Log.d(TAG, "=========================================")
        Log.d(TAG, "✍️ WRITING WORKOUT TO GOOGLE FIT ✍️")
        Log.d(TAG, "=========================================")
        Log.d(TAG, "Workout: ${workout.activityType}, ${workout.durationMin} min, ${workout.caloriesBurned} cal")
        
        val accountNullable = getAccount()
        if (accountNullable == null) {
            Log.e(TAG, "❌ No Google account - cannot write workout")
            return false
        }
        val account = accountNullable // Non-null after check
        
        // Check if we have basic permissions (READ)
        if (!hasPermissions()) {
            Log.e(TAG, "❌ No Google Fit permissions - cannot write workout")
            return false
        }
        
        // Check if we have WRITE permission (separate check)
        val hasWritePerms = GoogleSignIn.hasPermissions(account, fitnessOptionsWrite)
        if (!hasWritePerms) {
            Log.w(TAG, "⚠️ No WRITE permission - will try to write anyway")
            // Note: In a real scenario, you'd request WRITE permission here
            // For now, we'll try to write anyway and let it fail gracefully
        }
        
        try {
            val startTime = workout.startTime
            val endTime = startTime + (workout.durationMin * 60 * 1000L)
            
            // Map activity type to Google Fit activity type constant
            val activityType = when (workout.activityType.lowercase()) {
                "walking", "walk" -> FitnessActivities.WALKING
                "running", "run" -> FitnessActivities.RUNNING
                "cycling", "bike", "bicycle" -> FitnessActivities.BIKING
                "swimming", "swim" -> FitnessActivities.SWIMMING
                else -> FitnessActivities.WALKING // Default to walking for unknown types
            }
            
            Log.d(TAG, "  Activity type: $activityType (${workout.activityType})")
            Log.d(TAG, "  Start time: ${java.time.Instant.ofEpochMilli(startTime).atZone(java.time.ZoneId.systemDefault())}")
            Log.d(TAG, "  End time: ${java.time.Instant.ofEpochMilli(endTime).atZone(java.time.ZoneId.systemDefault())}")
            
            // Create a session for the workout
            val session = Session.Builder()
                .setName(workout.activityType)
                .setIdentifier("coachie_${startTime}_${workout.activityType.lowercase().replace(" ", "_")}")
                .setDescription("Workout logged from Coachie app")
                .setStartTime(startTime, TimeUnit.MILLISECONDS)
                .setEndTime(endTime, TimeUnit.MILLISECONDS)
                .setActivity(activityType)
                .build()
            
            // Create activity segment data point to associate with the session
            val dataSource = DataSource.Builder()
                .setAppPackageName(context.packageName)
                .setDataType(DataType.TYPE_ACTIVITY_SEGMENT)
                .setStreamName("Coachie_${workout.activityType}_${startTime}")
                .setType(DataSource.TYPE_RAW)
                .build()
            
            val dataPoint = DataPoint.builder(dataSource)
                .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                .setActivityField(Field.FIELD_ACTIVITY, activityType)
                .build()
            
            val dataSet = DataSet.builder(dataSource)
                .add(dataPoint)
                .build()
            
            // Insert the session with activity data
            val insertRequest = SessionInsertRequest.Builder()
                .setSession(session)
                .addDataSet(dataSet)
                .build()
            
            Log.d(TAG, "  Inserting session to Google Fit...")
            Fitness.getSessionsClient(context, account)
                .insertSession(insertRequest)
                .await()
            
            Log.d(TAG, "✅✅✅ WORKOUT WRITTEN TO GOOGLE FIT SUCCESSFULLY ✅✅✅")
            Log.d(TAG, "=========================================")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ ERROR WRITING WORKOUT TO GOOGLE FIT ❌❌❌", e)
            Log.e(TAG, "  Error type: ${e.javaClass.simpleName}")
            Log.e(TAG, "  Error message: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    data class SleepData(val startTime: Long, val endTime: Long)
    data class WorkoutData(val activityType: String, val durationMin: Int, val caloriesBurned: Int = 0, val startTime: Long)
}

