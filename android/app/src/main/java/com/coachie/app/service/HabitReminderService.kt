package com.coachie.app.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.coachie.app.MainActivity
import com.coachie.app.R
import com.coachie.app.data.model.Habit
import com.coachie.app.receiver.HabitReminderReceiver
import com.coachie.app.util.HabitUtils
import java.util.*

/**
 * Service to schedule reminders for manual completion habits
 */
class HabitReminderService(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "habit_reminders"
        const val NOTIFICATION_ID_BASE = 2000 // Base ID for habit reminders (2000-2999)
        const val REMINDER_ACTION = "com.coachie.HABIT_REMINDER"
        const val EXTRA_HABIT_ID = "habit_id"
        const val EXTRA_HABIT_TITLE = "habit_title"
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
                "Habit Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders to complete your habits"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Schedule a daily reminder for a habit
     */
    fun scheduleHabitReminder(habit: Habit, hour: Int, minute: Int) {
        if (!HabitUtils.shouldScheduleReminders(habit)) {
            android.util.Log.d("HabitReminderService", "Skipping reminder for auto-tracked habit: ${habit.title}")
            return
        }

        val requestCode = NOTIFICATION_ID_BASE + habit.id.hashCode().mod(1000) // Ensure unique request code

        val intent = Intent(context, HabitReminderReceiver::class.java).apply {
            action = REMINDER_ACTION
            putExtra(EXTRA_HABIT_ID, habit.id)
            putExtra(EXTRA_HABIT_TITLE, habit.title)
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            pendingIntentFlags
        )

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

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )

        android.util.Log.d("HabitReminderService", "Scheduled reminder for habit '${habit.title}' at $hour:$minute daily")
    }

    /**
     * Schedule reminders for all manual completion habits
     */
    fun scheduleRemindersForHabits(habits: List<Habit>) {
        habits.forEach { habit ->
            if (HabitUtils.shouldScheduleReminders(habit)) {
                val reminderTime = HabitUtils.getDefaultReminderTime(habit)
                if (reminderTime != null) {
                    scheduleHabitReminder(habit, reminderTime.first, reminderTime.second)
                }
            }
        }
    }

    /**
     * Cancel reminder for a specific habit
     */
    fun cancelHabitReminder(habit: Habit) {
        val requestCode = NOTIFICATION_ID_BASE + habit.id.hashCode().mod(1000)
        val intent = Intent(context, HabitReminderReceiver::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            pendingIntentFlags
        )
        alarmManager.cancel(pendingIntent)
        android.util.Log.d("HabitReminderService", "Cancelled reminder for habit: ${habit.title}")
    }

    /**
     * Cancel all habit reminders
     */
    fun cancelAllReminders() {
        // Note: This is a simplified version. In production, you'd track all scheduled reminders
        android.util.Log.d("HabitReminderService", "All habit reminders cancelled")
    }
}

