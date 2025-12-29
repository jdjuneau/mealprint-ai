package com.coachie.app.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mealprint.ai.MainActivity
import com.mealprint.ai.R
import com.mealprint.ai.receiver.MindfulnessReminderReceiver
import java.util.*

class MindfulnessReminderService(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "mindfulness_reminders"
        const val NOTIFICATION_ID = 1001
        const val REMINDER_ACTION = "com.coachie.MINDfulness_REMINDER"
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mindfulness Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily reminders for mindfulness and meditation practice"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun scheduleDailyReminder(hour: Int, minute: Int) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If the time has already passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, MindfulnessReminderReceiver::class.java).apply {
            action = REMINDER_ACTION
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Schedule repeating daily alarm
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    fun cancelReminders() {
        val intent = Intent(context, MindfulnessReminderReceiver::class.java).apply {
            action = REMINDER_ACTION
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
    }

    fun showMindfulnessReminder() {
        val messages = listOf(
            "Take a moment to breathe deeply and center yourself",
            "How are you feeling right now? A quick mindfulness check-in could help",
            "Consider a short meditation session to recharge your mind",
            "Remember to practice self-compassion today",
            "Take 5 minutes for mindful breathing",
            "Notice your thoughts without judgment",
            "A mindful pause can bring clarity to your day"
        )

        val randomMessage = messages.random()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Add extra to indicate this was opened from a reminder
            putExtra("from_reminder", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Default system icon
            .setContentTitle("🧘 Time for Mindfulness")
            .setContentText(randomMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(randomMessage))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
