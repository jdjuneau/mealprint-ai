package com.coachie.app.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mealprint.ai.data.local.PreferencesManager

/**
 * Ensures local nudges are rescheduled after device reboot or app update.
 */
class LocalNudgeBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val preferencesManager = PreferencesManager(context)
        if (!preferencesManager.nudgesEnabled) {
            android.util.Log.d("LocalNudgeBootReceiver", "Nudges disabled, skipping reschedule")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: run {
                android.util.Log.e("LocalNudgeBootReceiver", "AlarmManager not available, cannot reschedule nudges")
                return
            }

        // DISABLED: Brief times (8 AM, 2 PM, 6 PM) are handled by Firebase functions
        // Only schedule non-brief times as fallback
        // scheduleNudgeAlarm(context, alarmManager, 8, 0, 1001)  // Morning - DISABLED (handled by briefs)
        // scheduleNudgeAlarm(context, alarmManager, 14, 0, 1002) // Midday - DISABLED (handled by briefs)
        // scheduleNudgeAlarm(context, alarmManager, 18, 0, 1003) // Evening - DISABLED (handled by briefs at 6 PM)

        android.util.Log.i("LocalNudgeBootReceiver", "Local nudge alarms rescheduled after reboot/update")
    }

    private fun scheduleNudgeAlarm(
        context: Context,
        alarmManager: AlarmManager,
        hour: Int,
        minute: Int,
        requestCode: Int
    ) {
        val intent = Intent(context, LocalNudgeReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }
}

