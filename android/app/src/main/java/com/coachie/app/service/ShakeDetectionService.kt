package com.coachie.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.util.Log
import kotlin.math.sqrt

/**
 * Background service that detects shake gestures to trigger emergency calm
 */
class ShakeDetectionService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "ShakeDetectionService"

        // Shake detection parameters
        private const val SHAKE_THRESHOLD_GRAVITY = 2.7f
        private const val SHAKE_SLOP_TIME_MS = 500
        private const val SHAKE_COUNT_RESET_TIME_MS = 3000

        fun startService(context: Context) {
            val intent = Intent(context, ShakeDetectionService::class.java)
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ShakeDetectionService::class.java)
            context.stopService(intent)
        }
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private var shakeTimestamp: Long = 0
    private var shakeCount: Int = 0

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ShakeDetectionService created")

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        accelerometer?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "ShakeDetectionService started")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "ShakeDetectionService destroyed")
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val gX = x / SensorManager.GRAVITY_EARTH
            val gY = y / SensorManager.GRAVITY_EARTH
            val gZ = z / SensorManager.GRAVITY_EARTH

            // Calculate total acceleration
            val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

            if (gForce > SHAKE_THRESHOLD_GRAVITY) {
                val now = System.currentTimeMillis()

                // Ignore shake events too close to each other
                if (shakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
                    return
                }

                // Reset shake count after 3 seconds of no shakes
                if (shakeTimestamp + SHAKE_COUNT_RESET_TIME_MS < now) {
                    shakeCount = 0
                }

                shakeTimestamp = now
                shakeCount++

                Log.d(TAG, "Shake detected! Count: $shakeCount")

                // Trigger emergency calm on 3 shakes
                if (shakeCount >= 3) {
                    Log.d(TAG, "Emergency shake pattern detected! Triggering calm service.")
                    triggerEmergencyCalm()
                    shakeCount = 0 // Reset for next emergency trigger
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for shake detection
    }

    /**
     * Trigger the emergency calm service
     */
    private fun triggerEmergencyCalm() {
        try {
            EmergencyCalmService.startService(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start emergency calm service", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
