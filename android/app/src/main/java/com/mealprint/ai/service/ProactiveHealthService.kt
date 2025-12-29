package com.coachie.app.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mealprint.ai.receiver.ProactiveHealthReceiver
import androidx.core.app.NotificationCompat
import com.mealprint.ai.MainActivity
import com.mealprint.ai.R
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.data.model.HealthLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.abs

class ProactiveHealthService(private val context: Context) {

    companion object {
        private const val TAG = "ProactiveHealthService"
        const val MORNING_CHECK_CHANNEL_ID = "morning_health_check"
        const val EVENING_WINDDOWN_CHANNEL_ID = "evening_winddown"
        const val EVENING_JOURNAL_CHANNEL_ID = "evening_journal"
        const val MORNING_CHECK_NOTIFICATION_ID = 2001
        const val EVENING_WINDDOWN_NOTIFICATION_ID = 2002
        const val EVENING_JOURNAL_NOTIFICATION_ID = 2003
        const val MORNING_CHECK_ACTION = "com.coachie.MORNING_HEALTH_CHECK"
        const val EVENING_WINDDOWN_ACTION = "com.coachie.EVENING_WINDDOWN"
        const val EVENING_JOURNAL_ACTION = "com.coachie.EVENING_JOURNAL"
        const val EXTRA_3_MINUTE_BREATHING = "3_minute_breathing"
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Morning health check channel
            val morningChannel = NotificationChannel(
                MORNING_CHECK_CHANNEL_ID,
                "Morning Health Check",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Gentle reminders based on your overnight recovery"
                enableVibration(false) // Silent notification
                setSound(null, null)
            }

            // Evening wind-down channel
            val eveningChannel = NotificationChannel(
                EVENING_WINDDOWN_CHANNEL_ID,
                "Evening Wind-down",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Helpful reminders to prepare for sleep"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 200, 200)
            }

            // Evening journal channel
            val journalChannel = NotificationChannel(
                EVENING_JOURNAL_CHANNEL_ID,
                "Evening Journal",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Gentle evening reflection reminders"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            }

            notificationManager.createNotificationChannel(morningChannel)
            notificationManager.createNotificationChannel(eveningChannel)
            notificationManager.createNotificationChannel(journalChannel)
        }
    }

    fun scheduleMorningCheck(hour: Int = 6, minute: Int = 0) {
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

        val intent = Intent(context, com.coachie.app.receiver.ProactiveHealthReceiver::class.java).apply {
            action = MORNING_CHECK_ACTION
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1,
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

    fun scheduleEveningWinddown() {
        CoroutineScope(Dispatchers.IO).launch {
            calculateAverageBedtime()?.let { bedtime ->
                val winddownTime = bedtime - (30 * 60 * 1000) // 30 minutes before bedtime

        val intent = Intent(context, com.coachie.app.receiver.ProactiveHealthReceiver::class.java).apply {
            action = EVENING_WINDDOWN_ACTION
        }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    2,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Schedule for today if not passed, otherwise tomorrow
                val scheduleTime = if (winddownTime > System.currentTimeMillis()) {
                    winddownTime
                } else {
                    winddownTime + (24 * 60 * 60 * 1000) // Tomorrow
                }

                // Use setRepeating instead of setExactAndAllowWhileIdle to avoid requiring SCHEDULE_EXACT_ALARM permission
                // Briefs are handled by Firebase Cloud Functions, so exact alarms aren't needed
                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    scheduleTime,
                    AlarmManager.INTERVAL_DAY, // Repeat daily
                    pendingIntent
                )
            }
        }
    }

    fun scheduleEveningJournal(hour: Int = 21, minute: Int = 0) {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If the time has already passed for today, schedule for tomorrow
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, ProactiveHealthReceiver::class.java).apply {
            action = EVENING_JOURNAL_ACTION
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Schedule repeating alarm
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY, // Repeat every day
            pendingIntent
        )

        Log.d(TAG, "Evening journal scheduled for ${hour}:${minute} daily.")
    }

    fun checkMorningHealthAndNotify(userId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val shouldNotify = checkHRVDrop(userId)
                if (shouldNotify) {
                    showMorningHealthNotification()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun showMorningHealthNotification() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_3_MINUTE_BREATHING, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, MORNING_CHECK_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("💙 Your body needs extra love today")
            .setContentText("Want a 3-minute reset instead of your workout?")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Your body needs extra love today. Want a 3-minute reset instead of your workout?"))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(true) // Silent notification
            .build()

        notificationManager.notify(MORNING_CHECK_NOTIFICATION_ID, notification)
    }

    fun showEveningWinddownNotification() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Add extra to indicate wind-down flow
            putExtra("show_winddown", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            4,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, EVENING_WINDDOWN_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🌙 Phones down soon?")
            .setContentText("Let me guide you into sleep.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(EVENING_WINDDOWN_NOTIFICATION_ID, notification)
    }

    fun showEveningJournalNotification() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("show_evening_journal", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            EVENING_JOURNAL_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, EVENING_JOURNAL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📝 2 minutes to close today with kindness?")
            .setContentText("Let's reflect on your day together.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Take 2 minutes to reflect on your day with kindness. I've prepared some thoughtful prompts just for you."))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()

        notificationManager.notify(EVENING_JOURNAL_NOTIFICATION_ID, notification)
    }

    private suspend fun checkHRVDrop(userId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val repository = FirebaseRepository.getInstance()

                // Get last 8 days of HRV data (overnight measurements)
                val endDate = java.time.LocalDate.now()
                val startDate = endDate.minusDays(7)

                val hrvData = mutableListOf<Double>()

                // Check health logs for HRV data
                for (i in 0..7) {
                    val date = startDate.plusDays(i.toLong())
                    val dateString = date.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)

                    val healthLogs = repository.getHealthLogs(userId, dateString)
                    healthLogs.getOrNull()?.let { logs ->
                        // Look for HRV in the logs (assuming it's stored in some health metric)
                        logs.filterIsInstance<HealthLog.SleepLog>().firstOrNull()?.let { sleepLog ->
                            // For now, we'll simulate HRV calculation
                            // In a real implementation, you'd extract actual HRV from health data
                            hrvData.add(simulateHRVForDate(date))
                        }
                    }
                }

                if (hrvData.size < 2) return@withContext false

                // Calculate 7-day average (excluding today)
                val sevenDayAvg = hrvData.dropLast(1).average()
                val todayHRV = hrvData.last()

                // Check if overnight HRV dropped >15% from 7-day average
                val dropPercentage = ((sevenDayAvg - todayHRV) / sevenDayAvg) * 100
                dropPercentage > 15.0

            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    private suspend fun calculateAverageBedtime(): Long? {
        return withContext(Dispatchers.IO) {
            try {
                // Calculate average bedtime from last 14 days
                val endDate = java.time.LocalDate.now()
                val startDate = endDate.minusDays(14)

                val bedtimes = mutableListOf<Long>()

                val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext null

                val repository = FirebaseRepository.getInstance()

                for (i in 0..13) {
                    val date = startDate.plusDays(i.toLong())
                    val dateString = date.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)

                    val healthLogs = repository.getHealthLogs(userId, dateString)
                    healthLogs.getOrNull()?.let { logs ->
                        logs.filterIsInstance<HealthLog.SleepLog>().firstOrNull()?.let { sleepLog ->
                            // Extract bedtime from sleep log
                            // Assuming sleep log has startTime/endTime
                            bedtimes.add(extractBedtimeFromSleepLog(sleepLog))
                        }
                    }
                }

                if (bedtimes.isEmpty()) return@withContext null

                // Calculate average bedtime
                val avgBedtime = bedtimes.average().toLong()

                // Convert to today's date
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = avgBedtime

                val todayCalendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, calendar.get(Calendar.HOUR_OF_DAY))
                    set(Calendar.MINUTE, calendar.get(Calendar.MINUTE))
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                todayCalendar.timeInMillis

            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun simulateHRVForDate(date: java.time.LocalDate): Double {
        // Simulate HRV data - in real implementation, this would come from actual health data
        // Higher HRV = better recovery, lower HRV = more stressed/fatigued
        val baseHRV = 65.0
        val random = Random(date.toEpochDay())
        return baseHRV + (random.nextDouble() - 0.5) * 20 // ±10 variation
    }

    private fun extractBedtimeFromSleepLog(sleepLog: HealthLog.SleepLog): Long {
        // In a real implementation, extract actual bedtime from sleep data
        // For now, simulate based on typical bedtime patterns
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23) // Default 11 PM
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    fun showAnxietyNotification(currentHR: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(AnxietyDetectionService.EXTRA_GROUNDING_EXERCISE, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            5,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, MORNING_CHECK_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("💙 Notice your heart rate?")
            .setContentText("I notice your heart rate just spiked to ${currentHR} while sitting. Want a 90-second grounding exercise right now?")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("I notice your heart rate just spiked to ${currentHR} while sitting. Want a 90-second grounding exercise right now?"))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(true) // Silent notification
            .build()

        notificationManager.notify(MORNING_CHECK_NOTIFICATION_ID + 1, notification)
    }
}
