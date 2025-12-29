package com.coachie.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.coachie.app.service.ProactiveHealthService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProactiveHealthReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ProactiveHealthService.MORNING_CHECK_ACTION -> {
                handleMorningCheck(context)
            }
            ProactiveHealthService.EVENING_WINDDOWN_ACTION -> {
                handleEveningWinddown(context)
            }
            ProactiveHealthService.EVENING_JOURNAL_ACTION -> {
                handleEveningJournal(context)
            }
        }
    }

    private fun handleMorningCheck(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid

                if (userId != null) {
                    val service = ProactiveHealthService(context)
                    service.checkMorningHealthAndNotify(userId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleEveningWinddown(context: Context) {
        val service = ProactiveHealthService(context)
        service.showEveningWinddownNotification()
    }

    private fun handleEveningJournal(context: Context) {
        val service = ProactiveHealthService(context)
        service.showEveningJournalNotification()
    }
}
