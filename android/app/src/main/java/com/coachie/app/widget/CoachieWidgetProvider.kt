package com.coachie.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.coachie.app.MainActivity
import com.coachie.app.R
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.util.CoachieScoreTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CoachieWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // Widget enabled
    }

    override fun onDisabled(context: Context) {
        // Widget disabled
    }

    companion object {
        private const val TAG = "CoachieWidget"
        private const val ACTION_QUICK_LOG = "com.coachie.app.QUICK_LOG"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            // Show default state immediately
            val views = RemoteViews(context.packageName, R.layout.widget_coachie)
            
            // Set up click intent for widget container (opens main app)
            val mainIntent = Intent(context, MainActivity::class.java)
            val mainPendingIntent = PendingIntent.getActivity(
                context,
                0,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, mainPendingIntent)

            // Set up quick log button intent (opens health tracking screen)
            val quickLogIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_QUICK_LOG
                setData(Uri.parse("coachie://health_tracking"))
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val quickLogPendingIntent = PendingIntent.getActivity(
                context,
                1,
                quickLogIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.quick_log_button, quickLogPendingIntent)

            // Set default values
            views.setTextViewText(R.id.widget_title, "⚡ Coachie")
            views.setTextViewText(R.id.coachie_score_label, "Coachie Score")
            views.setTextViewText(R.id.coachie_score_value, "-- / 100")
            views.setProgressBar(R.id.coachie_score_progress, 100, 0, false)
            views.setTextViewText(R.id.circles_info, "Circles: --")
            views.setViewVisibility(R.id.circles_notifications_badge, android.view.View.GONE)
            views.setTextViewText(R.id.streak_info, "Streak: --")
            views.setTextViewText(R.id.water_value, "--")
            views.setTextViewText(R.id.steps_value, "--")

            // Update widget immediately with default state
            appWidgetManager.updateAppWidget(appWidgetId, views)

            // Load data asynchronously
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val userId = getUserId(context)
                    if (userId != null) {
                        val data = loadWidgetData(context, userId)
                        withContext(Dispatchers.Main) {
                            updateWidgetWithData(context, appWidgetManager, appWidgetId, data)
                        }
                    } else {
                        // No user logged in, show default state
                        withContext(Dispatchers.Main) {
                            updateWidgetWithData(context, appWidgetManager, appWidgetId, WidgetData())
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error updating widget", e)
                }
            }
        }

        private fun getUserId(context: Context): String? {
            return try {
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                    ?: context.getSharedPreferences("coachie_prefs", Context.MODE_PRIVATE)
                        .getString("user_id", null)
            } catch (e: Exception) {
                null
            }
        }

        private suspend fun loadWidgetData(context: Context, userId: String): WidgetData {
            val repository = FirebaseRepository.getInstance()
            val scoreTracker = CoachieScoreTracker(context)
            
            // Get today's date string
            val today = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val todayString = dateFormat.format(today.time)
            
            // Get today's Coachie score
            val coachieScore = scoreTracker.getTodayScore() ?: 0
            
            // Get circles data (count and notifications)
            val (totalCircles, circlesNotificationsCount) = try {
                val circlesResult = repository.getUserCircles(userId)
                val circles = circlesResult.getOrNull() ?: emptyList()
                val totalCircles = circles.size
                
                val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
                var totalNewPosts = 0
                
                for (circle in circles) {
                    if (circle.id.isNotBlank()) {
                        val postsResult = repository.getCirclePosts(circle.id)
                        val posts = postsResult.getOrNull() ?: emptyList()
                        
                        // Count posts from last 24 hours (excluding user's own posts)
                        val newPosts = posts.count { post ->
                            val postTime = post.createdAt?.time ?: 0L
                            postTime > oneDayAgo && post.authorId != userId
                        }
                        totalNewPosts += newPosts
                    }
                }
                
                Pair(totalCircles, totalNewPosts)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error loading circles data", e)
                Pair(0, 0)
            }
            
            // Get streak data
            val currentStreak = try {
                val streakResult = repository.getUserStreak(userId)
                streakResult.getOrNull()?.currentStreak ?: 0
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error loading streak data", e)
                0
            }
            
            // Get today's daily log for water and steps
            val (waterMl, steps) = try {
                val dailyLogResult = repository.getDailyLog(userId, todayString)
                val dailyLog = dailyLogResult.getOrNull()
                val water = dailyLog?.water ?: 0
                val stepsCount = dailyLog?.steps ?: 0
                Pair(water, stepsCount)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error loading daily log", e)
                Pair(0, 0)
            }

            return WidgetData(
                coachieScore = coachieScore,
                circlesCount = totalCircles,
                circlesNotificationsCount = circlesNotificationsCount,
                currentStreak = currentStreak,
                waterMl = waterMl,
                steps = steps
            )
        }

        private fun updateWidgetWithData(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            data: WidgetData
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_coachie)

            // Set up click intent for widget container
            val mainIntent = Intent(context, MainActivity::class.java)
            val mainPendingIntent = PendingIntent.getActivity(
                context,
                0,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, mainPendingIntent)

            // Set up quick log button intent
            val quickLogIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_QUICK_LOG
                setData(Uri.parse("coachie://health_tracking"))
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val quickLogPendingIntent = PendingIntent.getActivity(
                context,
                1,
                quickLogIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.quick_log_button, quickLogPendingIntent)

            // Update with data
            views.setTextViewText(R.id.widget_title, "⚡ Coachie")
            views.setTextViewText(R.id.coachie_score_label, "Coachie Score")
            
            val displayScore = data.coachieScore.coerceIn(0, 100)
            views.setTextViewText(R.id.coachie_score_value, "$displayScore / 100")
            views.setProgressBar(R.id.coachie_score_progress, 100, displayScore, false)
            
            // Update circles info
            val circlesText = if (data.circlesCount > 0) {
                "Circles: ${data.circlesCount}"
            } else {
                "No circles"
            }
            views.setTextViewText(R.id.circles_info, circlesText)
            
            // Update circles notifications badge
            if (data.circlesNotificationsCount > 0) {
                views.setTextViewText(R.id.circles_notifications_badge, data.circlesNotificationsCount.toString())
                views.setViewVisibility(R.id.circles_notifications_badge, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.circles_notifications_badge, android.view.View.GONE)
            }
            
            // Update streak info
            val streakText = if (data.currentStreak > 0) {
                "${data.currentStreak} days"
            } else {
                "Start today"
            }
            views.setTextViewText(R.id.streak_info, streakText)
            
            // Update water and steps (convert ml to glasses: 1 glass = 250ml)
            val glassesOfWater = data.waterMl / 250.0
            val waterText = if (glassesOfWater > 0) {
                if (glassesOfWater >= 1) {
                    String.format("%.1f", glassesOfWater)
                } else {
                    "<1"
                }
            } else {
                "0"
            }
            views.setTextViewText(R.id.water_value, waterText)
            
            val stepsText = if (data.steps > 0) {
                if (data.steps >= 1000) {
                    String.format("%.1fk", data.steps / 1000.0)
                } else {
                    "${data.steps}"
                }
            } else {
                "0"
            }
            views.setTextViewText(R.id.steps_value, stepsText)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, CoachieWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }

        private data class WidgetData(
            val coachieScore: Int = 0,
            val circlesCount: Int = 0,
            val circlesNotificationsCount: Int = 0,
            val currentStreak: Int = 0,
            val waterMl: Int = 0,
            val steps: Int = 0
        )
    }
}

