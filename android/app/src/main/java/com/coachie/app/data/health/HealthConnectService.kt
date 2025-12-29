package com.coachie.app.data.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.ZoneOffset

class HealthConnectService(private val context: Context) {
    
    private val healthConnectClient: HealthConnectClient? by lazy {
        try {
            HealthConnectClient.getOrCreate(context)
        } catch (e: Exception) {
            Log.e("HealthConnectService", "Error getting HealthConnectClient", e)
            null
        }
    }
    
    fun isAvailable(): Boolean {
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            // CRITICAL: Just being able to create the client doesn't mean Health Connect is enabled
            // We need to verify the client actually works by checking if we can access the permission controller
            // If Health Connect is disabled, this will throw an exception
            client.permissionController
            true
        } catch (e: Exception) {
            // Health Connect is not available (not installed, disabled, or not accessible)
            Log.d("HealthConnectService", "Health Connect not available: ${e.message}")
            false
        }
    }
    
    suspend fun hasPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        return try {
            val permissions = setOf(
                androidx.health.connect.client.permission.HealthPermission.getReadPermission(StepsRecord::class),
                androidx.health.connect.client.permission.HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
                androidx.health.connect.client.permission.HealthPermission.getReadPermission(SleepSessionRecord::class),
                androidx.health.connect.client.permission.HealthPermission.getReadPermission(ExerciseSessionRecord::class)
            )
            val granted = client.permissionController.getGrantedPermissions()
            permissions.all { it in granted }
        } catch (e: Exception) {
            Log.e("HealthConnectService", "Error checking permissions", e)
            false
        }
    }
    
    suspend fun readSteps(startTime: Long, endTime: Long): Int {
        val client = healthConnectClient ?: return 0
        return try {
            val request = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    Instant.ofEpochMilli(startTime),
                    Instant.ofEpochMilli(endTime)
                )
            )
            val response = client.readRecords(request)
            response.records.sumOf { it.count.toLong() }.toInt()
        } catch (e: Exception) {
            Log.e("HealthConnectService", "Error reading steps", e)
            0
        }
    }
    
    suspend fun readCalories(startTime: Long, endTime: Long): Int {
        val client = healthConnectClient ?: return 0
        return try {
            val request = ReadRecordsRequest(
                recordType = ActiveCaloriesBurnedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    Instant.ofEpochMilli(startTime),
                    Instant.ofEpochMilli(endTime)
                )
            )
            val response = client.readRecords(request)
            response.records.sumOf { it.energy.inKilocalories.toInt() }
        } catch (e: Exception) {
            Log.e("HealthConnectService", "Error reading calories", e)
            0
        }
    }
    
    suspend fun readSleep(startTime: Long, endTime: Long): List<SleepData> {
        val client = healthConnectClient ?: return emptyList()
        return try {
            val request = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    Instant.ofEpochMilli(startTime),
                    Instant.ofEpochMilli(endTime)
                )
            )
            val response = client.readRecords(request)
            response.records.map { 
                SleepData(
                    startTime = it.startTime.toEpochMilli(),
                    endTime = it.endTime.toEpochMilli()
                )
            }
        } catch (e: Exception) {
            Log.e("HealthConnectService", "Error reading sleep", e)
            emptyList()
        }
    }
    
    suspend fun readWorkouts(startTime: Long, endTime: Long): List<WorkoutData> {
        val client = healthConnectClient ?: return emptyList()
        return try {
            Log.d("HealthConnectService", "=== READING WORKOUTS FROM HEALTH CONNECT ===")
            Log.d("HealthConnectService", "Time range: $startTime to $endTime")
            
            // Read exercise sessions
            val request = ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    Instant.ofEpochMilli(startTime),
                    Instant.ofEpochMilli(endTime)
                )
            )
            val response = client.readRecords(request)
            Log.d("HealthConnectService", "Found ${response.records.size} exercise sessions")
            
            // Read active calories to match with workouts
            val caloriesRequest = ReadRecordsRequest(
                recordType = ActiveCaloriesBurnedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    Instant.ofEpochMilli(startTime),
                    Instant.ofEpochMilli(endTime)
                )
            )
            val caloriesResponse = client.readRecords(caloriesRequest)
            Log.d("HealthConnectService", "Found ${caloriesResponse.records.size} active calorie records")
            
            // Map workouts with calories
            val workouts = response.records.map { exerciseSession ->
                val workoutStart = exerciseSession.startTime.toEpochMilli()
                val workoutEnd = exerciseSession.endTime.toEpochMilli()
                val duration = (workoutEnd - workoutStart) / (1000 * 60) // minutes
                
                // Find calories that overlap with this workout session
                var caloriesBurned = 0
                for (calorieRecord in caloriesResponse.records) {
                    val calorieStart = calorieRecord.startTime.toEpochMilli()
                    val calorieEnd = calorieRecord.endTime.toEpochMilli()
                    // Check if calorie record overlaps with workout (within 5 minutes tolerance)
                    if (calorieStart <= workoutEnd + (5 * 60 * 1000) && calorieEnd >= workoutStart - (5 * 60 * 1000)) {
                        caloriesBurned += calorieRecord.energy.inKilocalories.toInt()
                    }
                }
                
                val workoutData = WorkoutData(
                    activityType = exerciseSession.exerciseType.toString(),
                    durationMin = duration.toInt(),
                    caloriesBurned = caloriesBurned,
                    startTime = workoutStart
                )
                
                Log.d("HealthConnectService", "  💪 Workout: ${workoutData.activityType}, ${workoutData.durationMin} min, ${workoutData.caloriesBurned} cal")
                workoutData
            }
            
            Log.d("HealthConnectService", "✅ Returning ${workouts.size} workouts with calories")
            workouts
        } catch (e: Exception) {
            Log.e("HealthConnectService", "❌ Error reading workouts", e)
            e.printStackTrace()
            emptyList()
        }
    }
    
    data class SleepData(val startTime: Long, val endTime: Long)
    data class WorkoutData(val activityType: String, val durationMin: Int, val caloriesBurned: Int = 0, val startTime: Long)
}

