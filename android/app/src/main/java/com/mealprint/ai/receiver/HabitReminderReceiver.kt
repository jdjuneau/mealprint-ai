package com.coachie.app.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mealprint.ai.MainActivity
import com.mealprint.ai.R
import com.mealprint.ai.service.HabitReminderService
import kotlinx.coroutines.runBlocking
import com.mealprint.ai.data.HabitRepository
import kotlinx.coroutines.flow.first

/**
 * BroadcastReceiver for habit reminder notifications
 */
class HabitReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != HabitReminderService.REMINDER_ACTION) {
            return
        }

        val habitId = intent.getStringExtra(HabitReminderService.EXTRA_HABIT_ID) ?: return
        val habitTitle = intent.getStringExtra(HabitReminderService.EXTRA_HABIT_TITLE) ?: "Habit"

        // Check if habit was already completed today
        runBlocking {
            try {
                val habitRepository = HabitRepository.getInstance()
                // Get user ID from habit (we'll need to pass it or get it another way)
                // For now, we'll show the notification and let the user complete it
                showNotification(context, habitId, habitTitle)
            } catch (e: Exception) {
                android.util.Log.e("HabitReminderReceiver", "Error checking habit completion", e)
                // Show notification anyway
                showNotification(context, habitId, habitTitle)
            }
        }
    }

    private fun showNotification(context: Context, habitId: String, habitTitle: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                HabitReminderService.CHANNEL_ID,
                "Habit Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders to complete your habits"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "habits")
            putExtra("habit_id", habitId)
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            habitId.hashCode(),
            intent,
            pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(context, HabitReminderService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_coachie_notification)
            .setContentTitle("⏰ Time to complete your habit!")
            .setContentText("Don't forget: $habitTitle")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Tap to complete your habit: $habitTitle"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()

        notificationManager.notify(habitId.hashCode(), notification)
        android.util.Log.d("HabitReminderReceiver", "Showed reminder notification for habit: $habitTitle")
    }
}

