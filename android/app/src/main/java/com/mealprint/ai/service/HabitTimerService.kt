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
import com.mealprint.ai.MainActivity
import com.mealprint.ai.R
import kotlinx.coroutines.*

/**
 * Foreground service for habit timer (especially reading)
 * Runs continuously even when app is closed and sends notification when timer completes
 */
class HabitTimerService : Service() {

    companion object {
        private const val TAG = "HabitTimerService"
        private const val CHANNEL_ID = "habit_timer"
        private const val NOTIFICATION_ID = 5002
        private const val PREFS_NAME = "habit_timer_prefs"
        private const val KEY_START_TIME = "start_time"
        private const val KEY_DURATION_SECONDS = "duration_seconds"
        private const val KEY_HABIT_TITLE = "habit_title"
        private const val KEY_IS_RUNNING = "is_running"

        const val ACTION_START_TIMER = "com.coachie.app.START_HABIT_TIMER"
        const val ACTION_STOP_TIMER = "com.coachie.app.STOP_HABIT_TIMER"
        const val EXTRA_DURATION_SECONDS = "duration_seconds"
        const val EXTRA_HABIT_TITLE = "habit_title"

        fun startTimer(context: Context, durationSeconds: Int, habitTitle: String) {
            val intent = Intent(context, HabitTimerService::class.java).apply {
                action = ACTION_START_TIMER
                putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
                putExtra(EXTRA_HABIT_TITLE, habitTitle)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopTimer(context: Context) {
            val intent = Intent(context, HabitTimerService::class.java).apply {
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
    private var habitTitle: String = ""

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
                val title = intent.getStringExtra(EXTRA_HABIT_TITLE) ?: "Habit Timer"
                startTimer(duration, title)
            }
            ACTION_STOP_TIMER -> {
                stopTimer()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startTimer(durationSeconds: Int, habitTitle: String) {
        this.durationSeconds = durationSeconds
        this.habitTitle = habitTitle
        this.startTime = System.currentTimeMillis()
        
        // Save to preferences
        prefs.edit().apply {
            putLong(KEY_START_TIME, startTime)
            putInt(KEY_DURATION_SECONDS, durationSeconds)
            putString(KEY_HABIT_TITLE, habitTitle)
            putBoolean(KEY_IS_RUNNING, true)
            apply()
        }

        // Start foreground service
        val notification = createForegroundNotification(durationSeconds)
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
                    // Play sound and vibrate
                    playCompletionSound()
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

        Log.d(TAG, "Habit timer started: ${durationSeconds}s for $habitTitle")
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null

        // Clear preferences
        prefs.edit().apply {
            remove(KEY_START_TIME)
            remove(KEY_DURATION_SECONDS)
            remove(KEY_HABIT_TITLE)
            putBoolean(KEY_IS_RUNNING, false)
            apply()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "Habit timer stopped")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Habit Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for habit timer"
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
            putExtra("open_habit_timer", true)
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
            .setContentTitle(habitTitle)
            .setContentText("Time remaining: $timeText")
            .setSmallIcon(R.drawable.ic_coachie_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun showCompletionNotification() {
        val channelId = CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Habit Timer",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for habit timer completion"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_habit_timer", true)
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
            .setContentTitle("🎉 $habitTitle Complete!")
            .setContentText("Great job! You completed your habit timer.")
            .setSmallIcon(R.drawable.ic_coachie_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }
    
    private fun playCompletionSound() {
        try {
            val notification = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = android.media.RingtoneManager.getRingtone(this, notification)
            ringtone.play()
            
            // Stop after 3 seconds
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (ringtone.isPlaying) {
                    ringtone.stop()
                }
            }, 3000)
            
            // Vibrate
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            vibrator?.let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    it.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(500)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play completion sound", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        serviceScope.cancel()
    }
}

