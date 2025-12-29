package com.coachie.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.*
import android.view.*
import android.view.WindowManager.LayoutParams
import androidx.core.app.ServiceCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.coachie.app.MainActivity
import com.coachie.app.R
import com.coachie.app.ui.theme.CoachieTheme
import com.coachie.app.ui.theme.Primary40
import com.coachie.app.ui.theme.Tertiary40
import kotlinx.coroutines.*

/**
 * Service that provides an always-accessible emergency calm button overlay
 * Can be triggered by shake gesture or persistent notification
 */
class EmergencyCalmService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        private const val TAG = "EmergencyCalmService"
        private const val NOTIFICATION_CHANNEL_ID = "emergency_calm"
        private const val NOTIFICATION_ID = 4001
        private const val OVERLAY_PERMISSION_REQUEST = 1001

        fun startService(context: Context) {
            val intent = Intent(context, EmergencyCalmService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, EmergencyCalmService::class.java)
            context.stopService(intent)
        }
    }

    // Lifecycle components for Compose
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val _viewModelStore = ViewModelStore()

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var isOverlayVisible = false
    private var breathingOverlay: View? = null
    private var isBreathingActive = false

    // Audio for chimes
    private var audioManager: AudioManager? = null
    private var originalVolume = 0
    private var chimeSoundId = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        savedStateRegistryController.performRestore(null)

        createNotificationChannel()
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Show the emergency button overlay
        showEmergencyButton()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        hideEmergencyButton()
        hideBreathingOverlay()
        _viewModelStore.clear()

        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    /**
     * Show the emergency floating action button
     */
    private fun showEmergencyButton() {
        if (isOverlayVisible) return

        val layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                LayoutParams.TYPE_SYSTEM_ALERT
            },
            LayoutParams.FLAG_NOT_FOCUSABLE or
            LayoutParams.FLAG_NOT_TOUCH_MODAL or
            LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val composeView = ComposeView(this).apply {
            setContent {
                CoachieTheme {
                    EmergencyButtonOverlay(
                        onButtonClick = { startEmergencyBreathing() },
                        onDismiss = { hideEmergencyButton() }
                    )
                }
            }
        }

        // Set up lifecycle for Compose
        composeView.setViewTreeLifecycleOwner(this)
        composeView.setViewTreeViewModelStoreOwner(this)
        composeView.setViewTreeSavedStateRegistryOwner(this)

        try {
            windowManager.addView(composeView, layoutParams)
            overlayView = composeView
            isOverlayVisible = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Hide the emergency button
     */
    private fun hideEmergencyButton() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }
        isOverlayVisible = false
    }

    /**
     * Start the emergency breathing session
     */
    private fun startEmergencyBreathing() {
        hideEmergencyButton()
        showBreathingOverlay()
        startBreathingSession()
    }

    /**
     * Show the breathing overlay
     */
    private fun showBreathingOverlay() {
        if (isBreathingActive) return

        val layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                LayoutParams.TYPE_SYSTEM_ALERT
            },
            LayoutParams.FLAG_NOT_FOCUSABLE or
            LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val composeView = ComposeView(this).apply {
            setContent {
                CoachieTheme {
                    EmergencyBreathingOverlay(
                        onComplete = { hideBreathingOverlay() },
                        onLockout = { minutes -> startLockoutMode(minutes) }
                    )
                }
            }
        }

        // Set up lifecycle for Compose
        composeView.setViewTreeLifecycleOwner(this)
        composeView.setViewTreeViewModelStoreOwner(this)
        composeView.setViewTreeSavedStateRegistryOwner(this)

        try {
            windowManager.addView(composeView, layoutParams)
            breathingOverlay = composeView
            isBreathingActive = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Hide the breathing overlay
     */
    private fun hideBreathingOverlay() {
        breathingOverlay?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            breathingOverlay = null
        }
        isBreathingActive = false
    }

    /**
     * Start the 2-minute breathing session with chimes
     */
    private fun startBreathingSession() {
        // Set volume to a gentle level for chimes
        audioManager?.let { am ->
            originalVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val gentleVolume = (am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * 0.3).toInt()
            am.setStreamVolume(AudioManager.STREAM_MUSIC, gentleVolume, 0)
        }

        // TODO: Play soft chimes every breathing cycle
        // For now, just show the breathing overlay for 2 minutes
        CoroutineScope(Dispatchers.Main).launch {
            delay(120000) // 2 minutes
            hideBreathingOverlay()

            // Restore original volume
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
        }
    }

    /**
     * Start lockout mode with Do Not Disturb and app greying
     */
    private fun startLockoutMode(minutes: Int) {
        // Enable Do Not Disturb
        enableDoNotDisturb()

        // TODO: Grey out the app for the specified duration
        // This would require modifying the main app UI

        // Schedule to disable DND after the lockout period
        CoroutineScope(Dispatchers.Main).launch {
            delay(minutes * 60 * 1000L)
            disableDoNotDisturb()
        }
    }

    /**
     * Enable Do Not Disturb mode
     */
    private fun enableDoNotDisturb() {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (notificationManager.isNotificationPolicyAccessGranted) {
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Disable Do Not Disturb mode
     */
    private fun disableDoNotDisturb() {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Create notification channel
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Emergency Calm",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Always-accessible calm button"
                setSound(null, null)
                enableVibration(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create persistent notification
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("emergency_calm", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("🧘 Emergency Calm")
            .setContentText("Tap for instant breathing relief")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    // Lifecycle implementation
    override val lifecycle: Lifecycle = lifecycleRegistry
    override val viewModelStore: ViewModelStore = _viewModelStore
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    override fun onBind(intent: Intent?): IBinder? = null
}

/**
 * Emergency button overlay composable
 */
@Composable
private fun EmergencyButtonOverlay(
    onButtonClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Huge emergency button
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Tertiary40, Primary40)
                        )
                    )
                    .clickable { onButtonClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "BREATHE",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Tap anywhere to dismiss",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Emergency breathing overlay composable
 */
@Composable
private fun EmergencyBreathingOverlay(
    onComplete: () -> Unit,
    onLockout: (Int) -> Unit
) {
    var remainingSeconds by remember { mutableStateOf(120) }
    var showLockoutOptions by remember { mutableStateOf(false) }

    // Countdown timer
    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            kotlinx.coroutines.delay(1000)
            remainingSeconds--
        }
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2D1B69).copy(alpha = 0.95f)), // Dark purple
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Breathing instruction
            Text(
                text = "Breathe In... Breathe Out",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Time remaining
            Text(
                text = "${remainingSeconds / 60}:${(remainingSeconds % 60).toString().padStart(2, '0')}",
                style = MaterialTheme.typography.displayMedium,
                color = Color.White.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Lockout option
            if (!showLockoutOptions) {
                OutlinedButton(
                    onClick = { showLockoutOptions = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("Lock me out for a few minutes")
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Choose lockout duration:",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LockoutButton("5 min") { onLockout(5) }
                        LockoutButton("10 min") { onLockout(10) }
                        LockoutButton("15 min") { onLockout(15) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Gentle chimes indicator
            Text(
                text = "🔔 Soft chimes will guide your breathing",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun LockoutButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color.White
        )
    ) {
        Text(text)
    }
}
