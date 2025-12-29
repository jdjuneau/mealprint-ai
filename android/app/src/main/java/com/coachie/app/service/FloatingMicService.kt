package com.coachie.app.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import com.coachie.app.R

class FloatingMicService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var micButton: ImageButton

    override fun onCreate() {
        super.onCreate()

        // Create floating view
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_mic_button, null)

        // Window manager parameters
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        // Position the button
        params.gravity = Gravity.BOTTOM or Gravity.END
        params.x = 50 // margin from right
        params.y = 150 // margin from bottom

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.addView(floatingView, params)

        // Setup microphone button
        micButton = floatingView.findViewById(R.id.floating_mic_button)
        micButton.setOnClickListener {
            // Launch voice logging activity
            val intent = Intent(this, com.coachie.app.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("navigate_to", "voice_logging")
            }
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
