package com.coachie.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mealprint.ai.service.MindfulnessReminderService

class MindfulnessReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == MindfulnessReminderService.REMINDER_ACTION) {
            val reminderService = MindfulnessReminderService(context)
            reminderService.showMindfulnessReminder()
        }
    }
}
