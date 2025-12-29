package com.coachie.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.coachie.app.R
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.local.PreferencesManager
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class AnxietyDetectionService : Service() {

    companion object {
        private const val TAG = "AnxietyDetectionService"
        private const val NOTIFICATION_CHANNEL_ID = "anxiety_detection"
        private const val NOTIFICATION_ID = 3001
        private const val ANXIETY_SPIKE_THRESHOLD_BPM = 20
        private const val ANXIETY_DURATION_MINUTES = 3
        private const val MONITORING_INTERVAL_SECONDS = 30L // Check every 30 seconds
        const val EXTRA_GROUNDING_EXERCISE = "grounding_exercise"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitoringJob: Job? = null
    private var firebaseRepository: FirebaseRepository? = null
    private var preferencesManager: PreferencesManager? = null

    private var userBaselineRHR: Float? = null
    private var anxietySpikeStartTime: Long? = null
    private var lastNotificationTime: Long = 0
    private val NOTIFICATION_COOLDOWN_HOURS = 2 // Don't spam notifications

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AnxietyDetectionService created")

        try {
            // Create notification channel for foreground service
            createNotificationChannel()

            // Start as foreground service with type (required for Android 14+)
            val notification = createForegroundNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                    )
                } catch (e: SecurityException) {
                    Log.e(TAG, "Failed to start foreground service with health type, trying without type", e)
                    // Fallback: try without service type (may not work on Android 14+)
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            firebaseRepository = FirebaseRepository.getInstance()
            preferencesManager = PreferencesManager(this)

            // Check if anxiety detection is enabled
            if (preferencesManager?.anxietyDetectionEnabled != true) {
                Log.d(TAG, "Anxiety detection is disabled, stopping service")
                stopSelf()
                return
            }

            // Load user's baseline resting heart rate
            loadUserBaselineRHR()

            // Start monitoring
            startAnxietyMonitoring()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AnxietyDetectionService", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AnxietyDetectionService started")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AnxietyDetectionService destroyed")
        monitoringJob?.cancel()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAnxietyMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = serviceScope.launch {
            while (isActive) {
                try {
                    checkForAnxietySpike()
                    delay(TimeUnit.SECONDS.toMillis(MONITORING_INTERVAL_SECONDS))
                } catch (e: Exception) {
                    Log.e(TAG, "Error in anxiety monitoring loop", e)
                    delay(5000) // Wait 5 seconds before retrying
                }
            }
        }
    }

    private suspend fun checkForAnxietySpike() {
        // Google Fit removed - anxiety detection disabled
        return
    }

    private suspend fun isUserInWorkout(): Boolean {
        // Google Fit removed - cannot check workout status
        return false
    }

    private fun triggerAnxietyNotification(currentHR: Float) {
        // Check cooldown period
        val now = System.currentTimeMillis()
        val timeSinceLastNotification = now - lastNotificationTime
        val cooldownMillis = TimeUnit.HOURS.toMillis(NOTIFICATION_COOLDOWN_HOURS.toLong())

        if (timeSinceLastNotification < cooldownMillis) {
            Log.d(TAG, "Notification cooldown active, skipping anxiety notification")
            return
        }

        lastNotificationTime = now

        // Create anxiety detection service and show notification
        val anxietyService = ProactiveHealthService(this)
        anxietyService.showAnxietyNotification(currentHR.toInt())
    }

    private fun loadUserBaselineRHR() {
        serviceScope.launch {
            try {
                val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return@launch

                // Try to get baseline RHR from user profile
                val profileResult = firebaseRepository?.getUserProfile(uid)
                if (profileResult?.isSuccess == true) {
                    val profile = profileResult.getOrNull()
                    // For now, use a default baseline. In a real implementation,
                    // you'd calculate this from historical data
                    userBaselineRHR = profile?.let { calculateBaselineRHR(it) } ?: 70f
                    Log.d(TAG, "Loaded baseline RHR: $userBaselineRHR")
                } else {
                    // Use default baseline if no profile available
                    userBaselineRHR = 70f
                    Log.d(TAG, "Using default baseline RHR: $userBaselineRHR")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading baseline RHR", e)
                userBaselineRHR = 70f // Default fallback
            }
        }
    }

    private fun calculateBaselineRHR(profile: com.coachie.app.data.model.UserProfile): Float {
        // In a real implementation, you'd calculate this from historical heart rate data
        // For now, return a reasonable default based on age/gender if available
        return 70f // Default resting heart rate
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Anxiety Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoring heart rate for anxiety detection"
                setSound(null, null) // Silent
                enableVibration(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("💙 Anxiety Detection Active")
            .setContentText("Monitoring your heart rate for anxiety patterns")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
