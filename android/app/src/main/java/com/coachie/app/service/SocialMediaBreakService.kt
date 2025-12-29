package com.coachie.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.coachie.app.MainActivity
import com.coachie.app.R
import kotlinx.coroutines.*

/**
 * Foreground service for social media break timer
 * Runs continuously even when app is closed and sends notification when timer completes
 */
class SocialMediaBreakService : Service() {

    companion object {
        private const val TAG = "SocialMediaBreakService"
        private const val CHANNEL_ID = "social_media_break_timer"
        private const val NOTIFICATION_ID = 5001
        private const val PREFS_NAME = "social_media_break_prefs"
        private const val KEY_START_TIME = "start_time"
        private const val KEY_DURATION_SECONDS = "duration_seconds"
        private const val KEY_IS_RUNNING = "is_running"

        const val ACTION_START_TIMER = "com.coachie.app.START_TIMER"
        const val ACTION_STOP_TIMER = "com.coachie.app.STOP_TIMER"
        const val EXTRA_DURATION_SECONDS = "duration_seconds"

        fun startTimer(context: Context, durationSeconds: Int) {
            val intent = Intent(context, SocialMediaBreakService::class.java).apply {
                action = ACTION_START_TIMER
                putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopTimer(context: Context) {
            val intent = Intent(context, SocialMediaBreakService::class.java).apply {
                action = ACTION_STOP_TIMER
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var notificationManager: NotificationManager
    private lateinit var prefs: SharedPreferences
    private var timerJob: Job? = null
    private var startTime: Long = 0
    private var durationSeconds: Int = 0

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TIMER -> {
                val duration = intent.getIntExtra(EXTRA_DURATION_SECONDS, 1800) // Default 30 minutes
                startTimer(duration)
            }
            ACTION_STOP_TIMER -> {
                stopTimer()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startTimer(durationSeconds: Int) {
        this.durationSeconds = durationSeconds
        this.startTime = System.currentTimeMillis()
        
        // Save to preferences
        prefs.edit().apply {
            putLong(KEY_START_TIME, startTime)
            putInt(KEY_DURATION_SECONDS, durationSeconds)
            putBoolean(KEY_IS_RUNNING, true)
            apply()
        }

        // Start foreground service
        val notification = createForegroundNotification(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to start foreground service with health type", e)
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Start timer coroutine
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                val remaining = (durationSeconds - elapsed).coerceAtLeast(0)

                if (remaining <= 0) {
                    // Timer completed
                    showCompletionNotification()
                    stopTimer()
                    break
                } else {
                    // Update notification
                    val updatedNotification = createForegroundNotification(remaining.toInt())
                    notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                }

                delay(1000) // Update every second
            }
        }

        Log.d(TAG, "Social media break timer started: ${durationSeconds}s")
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null

        // Clear preferences
        prefs.edit().apply {
            remove(KEY_START_TIME)
            remove(KEY_DURATION_SECONDS)
            putBoolean(KEY_IS_RUNNING, false)
            apply()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "Social media break timer stopped")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Social Media Break Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for social media break timer"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(remainingSeconds: Int): Notification {
        val hours = remainingSeconds / 3600
        val minutes = (remainingSeconds % 3600) / 60
        val seconds = remainingSeconds % 60
        val timeText = if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_social_media_break", true)
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            intent,
            pendingIntentFlags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Social Media Break")
            .setContentText("Time remaining: $timeText")
            .setSmallIcon(R.drawable.ic_coachie_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showCompletionNotification() {
        val channelId = "social_media_break_complete"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Social Media Break Complete",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when social media break timer completes"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_social_media_break", true)
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            intent,
            pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("🎉 Social Media Break Complete!")
            .setContentText("Your break is over. You can use social media again.")
            .setSmallIcon(R.drawable.ic_coachie_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        serviceScope.cancel()
    }
}

