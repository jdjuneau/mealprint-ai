package com.coachie.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            // Show splash screen for 2 seconds
            delay(2000)

            // Navigate to MainActivity
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            finish()
        }
    }
}
