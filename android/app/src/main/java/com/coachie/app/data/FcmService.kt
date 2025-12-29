package com.coachie.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.coachie.app.MainActivity
import com.coachie.app.R
import com.coachie.app.data.local.PreferencesManager
import com.coachie.app.domain.MacroTargetsCalculator
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.runBlocking

/**
 * Firebase Cloud Messaging service for handling push notifications
 * Extends FirebaseMessagingService to handle FCM messages and token updates
 */
class FcmService : FirebaseMessagingService() {

    private val TAG = "FcmService"

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains a data payload
        val showConfetti = remoteMessage.data["showConfetti"] == "true"
        val messageType = remoteMessage.data["type"] ?: "notification"
        val conversationId = remoteMessage.data["conversationId"]
        val userId = remoteMessage.data["userId"] // For new conversations
        val threadId = remoteMessage.data["threadId"] // For forum threads
        val requestId = remoteMessage.data["requestId"] // For friend requests
        val circleId = remoteMessage.data["circleId"] // For circle posts
        val postId = remoteMessage.data["postId"] // For circle posts
        val commentId = remoteMessage.data["commentId"] // For circle comments

        // Check if message contains a notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            Log.d(TAG, "Show confetti: $showConfetti")
            // Important notifications (messages, friend requests, circle updates) should always show
            // Only apply cooldown to general nudges/insights
            val isImportantNotification = messageType in listOf("message", "friend_request", "circle_post", "circle_comment")
            if (isImportantNotification || shouldShowNotification()) {
                showNotification(it.title ?: "Coachie", it.body ?: "", showConfetti, remoteMessage)
                if (!isImportantNotification) {
                    recordNotificationTime()
                } else {
                    // Important notifications don't need cooldown tracking
                }
            } else {
                Log.d(TAG, "Skipping FCM notification - within cooldown period")
            }
        }

        // Handle data-only messages
        if (remoteMessage.data.isNotEmpty() && remoteMessage.notification == null) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            // Handle data-only messages if needed
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")

        // Send token to server
        sendTokenToServer(token)
    }

    /**
     * Check if enough time has passed since the last notification
     * Returns true if we should show the notification, false if we're in cooldown
     */
    private fun shouldShowNotification(): Boolean {
        // CRITICAL: Use same SharedPreferences file as LocalNudgeReceiver for shared cooldown
        val prefs = getSharedPreferences("coachie_nudges", Context.MODE_PRIVATE)
        val lastNotificationTime = prefs.getLong(PREF_LAST_NUDGE_TIME, 0L)
        val currentTime = System.currentTimeMillis()
        val timeSinceLastNotification = currentTime - lastNotificationTime
        
        Log.d(TAG, "Cooldown check: lastNotificationTime=$lastNotificationTime, currentTime=$currentTime, timeSince=$timeSinceLastNotification ms (${timeSinceLastNotification / 60000} minutes), cooldown=$NUDGE_COOLDOWN_MS ms (${NUDGE_COOLDOWN_MS / 60000} minutes)")
        
        val shouldShow = timeSinceLastNotification >= NUDGE_COOLDOWN_MS
        if (!shouldShow) {
            val minutesRemaining = (NUDGE_COOLDOWN_MS - timeSinceLastNotification) / 60000
            Log.d(TAG, "Notification blocked - $minutesRemaining minutes remaining in cooldown")
        }
        
        return shouldShow
    }
    
    /**
     * Record the current time as the last notification time
     */
    private fun recordNotificationTime() {
        // CRITICAL: Use same SharedPreferences file as LocalNudgeReceiver for shared cooldown
        val prefs = getSharedPreferences("coachie_nudges", Context.MODE_PRIVATE)
        val currentTime = System.currentTimeMillis()
        prefs.edit().putLong(PREF_LAST_NUDGE_TIME, currentTime).apply()
        Log.d(TAG, "Recorded notification time: $currentTime")
    }

    /**
     * Show notification to user with tap action to open CoachChatScreen
     * If showConfetti is true, trigger confetti animation when app opens
     */
    private fun showNotification(title: String, body: String, showConfetti: Boolean = false, remoteMessage: RemoteMessage? = null) {
        // Determine channel based on notification type
        val messageType = remoteMessage?.data?.get("type") as? String ?: "notification"
        val channelId = when (messageType) {
            "message", "friend_request", "circle_post", "circle_comment" -> "coachie_messages"
            else -> "coachie_nudges"
        }
        val channelName = when (messageType) {
            "message", "friend_request", "circle_post", "circle_comment" -> "Coachie Messages"
            else -> "Coachie Nudges"
        }
        val channelDescription = when (messageType) {
            "message" -> "Messages from friends"
            "friend_request" -> "Friend requests and circle invitations"
            "circle_post", "circle_comment" -> "Circle updates and activity"
            else -> "Daily motivation and reminders from Coachie"
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH // High importance for messages
            ).apply {
                description = channelDescription
                setShowBadge(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Build enriched body with goal summary
        val enrichedBody = buildGoalSummary()?.let { summary ->
            if (body.contains(summary)) body else "$body\n$summary"
        } ?: body

        // Create intent to open MainActivity
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Check if this is a message notification (from remoteMessage data if available)
            val convId = remoteMessage?.data?.get("conversationId") as? String
            val userId = remoteMessage?.data?.get("userId") as? String
            val threadId = remoteMessage?.data?.get("threadId") as? String
            val reqId = remoteMessage?.data?.get("requestId") as? String
            val circId = remoteMessage?.data?.get("circleId") as? String
            val pId = remoteMessage?.data?.get("postId") as? String
            val msgType = remoteMessage?.data?.get("type") as? String ?: "notification"
            
            // Check for deep link in notification data
            val deepLink = remoteMessage?.data?.get("deepLink") as? String
            val screen = remoteMessage?.data?.get("screen") as? String
            
            val navigateTo = when {
                // Check notification type first
                msgType == "message" && convId != null -> {
                    putExtra("conversationId", convId)
                    putExtra("senderId", remoteMessage?.data?.get("senderId") as? String)
                    "messaging"
                }
                msgType == "friend_request" && reqId != null -> {
                    putExtra("requestId", reqId)
                    "friends"
                }
                msgType == "circle_post" && circId != null -> {
                    putExtra("circleId", circId)
                    putExtra("postId", pId)
                    "circle_detail"
                }
                msgType == "circle_comment" && circId != null -> {
                    putExtra("circleId", circId)
                    putExtra("postId", pId)
                    "circle_detail"
                }
                // Explicit deep link from server
                deepLink != null -> {
                    when {
                        deepLink.contains("journal") || deepLink.contains("reflect") -> "journal_flow"
                        deepLink.contains("habit") -> "habits"
                        deepLink.contains("meal") || deepLink.contains("food") -> "meal_log"
                        deepLink.contains("water") || deepLink.contains("hydrate") -> "water_log"
                        deepLink.contains("goal") -> "set_goals"
                        deepLink.contains("health") || deepLink.contains("track") -> "health_tracking"
                        deepLink.contains("blueprint") || deepLink.contains("weekly") -> "weekly_blueprint"
                        deepLink.contains("coach") || deepLink.contains("chat") -> FcmService.NAVIGATE_COACH_CHAT
                        else -> null
                    }
                }
                // Screen type from server
                screen != null -> {
                    when {
                        screen.contains("Journal", ignoreCase = true) || 
                        screen.contains("Reflect", ignoreCase = true) -> "journal_flow"
                        screen.contains("Habit", ignoreCase = true) -> "habits"
                        screen.contains("Meal", ignoreCase = true) -> "meal_log"
                        screen.contains("Water", ignoreCase = true) -> "water_log"
                        screen.contains("Goal", ignoreCase = true) -> "set_goals"
                        screen.contains("Health", ignoreCase = true) -> "health_tracking"
                        screen.contains("Blueprint", ignoreCase = true) -> "weekly_blueprint"
                        screen.contains("Coach", ignoreCase = true) -> FcmService.NAVIGATE_COACH_CHAT
                        else -> null
                    }
                }
                // Legacy message/forum notifications
                convId != null || userId != null -> {
                    putExtra("conversationId", convId ?: userId)
                    "messaging"
                }
                threadId != null -> {
                    putExtra("threadId", threadId)
                    "forum_thread"
                }
                // Check message content for context-based deep linking
                enrichedBody.contains("reflection", ignoreCase = true) ||
                enrichedBody.contains("reflect", ignoreCase = true) ||
                enrichedBody.contains("journal", ignoreCase = true) -> "journal_flow"
                enrichedBody.contains("habit", ignoreCase = true) -> "habits"
                Regex("log.*meal", RegexOption.IGNORE_CASE).containsMatchIn(enrichedBody) ||
                enrichedBody.contains("breakfast", ignoreCase = true) ||
                enrichedBody.contains("lunch", ignoreCase = true) ||
                enrichedBody.contains("dinner", ignoreCase = true) -> "meal_log"
                enrichedBody.contains("water", ignoreCase = true) ||
                enrichedBody.contains("hydrate", ignoreCase = true) -> "water_log"
                enrichedBody.contains("goal", ignoreCase = true) -> "set_goals"
                enrichedBody.contains("blueprint", ignoreCase = true) ||
                enrichedBody.contains("weekly", ignoreCase = true) -> "weekly_blueprint"
                // Default notification detail screen
                else -> "notification_detail"
            }
            
            // CRITICAL: Always include title and message so notification_detail screen can display them
            // Also include the deep link target so actionable links can be shown
            putExtra(EXTRA_NOTIFICATION_TITLE, title)
            putExtra(EXTRA_NOTIFICATION_MESSAGE, enrichedBody)
            putExtra(EXTRA_NAVIGATE_TO, "notification_detail") // Always navigate to notification_detail first
            // Store the deep link target separately so NotificationDetailScreen can show actionable link
            if (navigateTo != null && navigateTo != "notification_detail") {
                putExtra("deep_link_target", navigateTo)
            }
            putExtra(EXTRA_SHOW_CONFETTI, showConfetti)
        }

        // Create PendingIntent with proper flags for Android 12+
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            pendingIntentFlags
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_coachie_notification)
            .setContentTitle(title)
            .setContentText(enrichedBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(enrichedBody))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 250, 250, 250))

        // CRITICAL: Use same notification ID to consolidate with LocalNudgeReceiver
        // This ensures only one notification shows at a time, replacing previous ones
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun buildGoalSummary(): String? {
        // CRITICAL SECURITY: Use authenticated user, not SharedPreferences
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return null
        return runBlocking {
            try {
                val repo = FirebaseRepository.getInstance()
                val profile = repo.getUserProfile(userId).getOrNull() ?: return@runBlocking null
                val targets = MacroTargetsCalculator.calculate(profile)
                "Goal today: ${targets.calorieGoal} kcal — Protein ${targets.proteinGrams}g · Carbs ${targets.carbsGrams}g · Fat ${targets.fatGrams}g"
            } catch (e: Exception) {
                Log.w(TAG, "Unable to append goal summary to notification", e)
                null
            }
        }
    }

    /**
     * Send FCM token to server for storage
     */
    private fun sendTokenToServer(token: String) {
        // This will be called from the main app context where we have access to repositories
        // For now, we'll store it in shared preferences as a backup
        val prefs = getSharedPreferences("coachie_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()

        Log.d(TAG, "FCM token stored locally: $token")
    }

    companion object {
        const val EXTRA_NAVIGATE_TO = "navigate_to"
        const val NAVIGATE_COACH_CHAT = "coach_chat"
        const val EXTRA_SHOW_CONFETTI = "show_confetti"
        const val EXTRA_NOTIFICATION_TITLE = "notification_title"
        const val EXTRA_NOTIFICATION_MESSAGE = "notification_message"
        // CRITICAL: Use same notification ID as LocalNudgeReceiver to consolidate notifications
        private const val NOTIFICATION_ID = 1001
        // CRITICAL: Use same cooldown and preference key as LocalNudgeReceiver for shared cooldown
        private const val NUDGE_COOLDOWN_MS = 60 * 60 * 1000L // 60 minutes cooldown (increased to prevent spam)
        private const val PREF_LAST_NUDGE_TIME = "last_nudge_notification_time"

        /**
         * Get FCM token for the current device
         */
        suspend fun getFcmToken(): String? {
            return try {
                val token = FirebaseMessaging.getInstance().token.await()
                Log.d("FcmService", "FCM token retrieved: $token")
                token
            } catch (e: Exception) {
                Log.e("FcmService", "Error getting FCM token", e)
                null
            }
        }

        /**
         * Update FCM token in user profile
         */
        fun updateUserFcmToken(context: Context, userId: String, token: String) {
            // Use the new registerFCMToken function to add to array
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    FirebaseRepository.getInstance().registerFCMToken(userId, token).getOrThrow()
                    Log.d("FcmService", "FCM token registered successfully")
                } catch (e: Exception) {
                    Log.e("FcmService", "Failed to register FCM token", e)
                }
            }
        }
    }
}
