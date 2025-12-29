package com.coachie.app.viewmodel

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.HealthLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

class BoxBreathingViewModel : ViewModel() {

    enum class BreathingPhase {
        INHALE, HOLD_IN, EXHALE, HOLD_OUT, COMPLETE
    }
    
    enum class BreathingPattern(val displayName: String, val inhale: Int, val holdIn: Int, val exhale: Int, val holdOut: Int, val description: String) {
        QUICK_CALM("Quick Calm", 3, 2, 3, 2, "Fast-paced breathing for quick stress relief"),
        GENTLE("Gentle Breathing", 4, 4, 6, 2, "Gentle breathing with longer exhales for relaxation"),
        DEEP_FOCUS("Deep Focus", 4, 7, 8, 0, "4-7-8 breathing technique for deep focus and calm"),
        BOX("Box Breathing", 4, 4, 4, 4, "Classic 4-4-4-4 box breathing pattern")
    }

    private val _phase = MutableStateFlow(BreathingPhase.INHALE)
    val phase: StateFlow<BreathingPhase> = _phase.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _timeRemaining = MutableStateFlow("00:00")
    val timeRemaining: StateFlow<String> = _timeRemaining.asStateFlow()

    private val _instruction = MutableStateFlow("Get ready...")
    val instruction: StateFlow<String> = _instruction.asStateFlow()

    private val _isComplete = MutableStateFlow(false)
    val isComplete: StateFlow<Boolean> = _isComplete.asStateFlow()

    private var vibrator: Vibrator? = null
    private var textToSpeech: TextToSpeech? = null
    private var breathingJob: Job? = null
    private var totalSecondsRemaining = 60
    private var currentPattern: BreathingPattern = BreathingPattern.BOX

    fun initialize(context: Context) {
        vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)

        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
                textToSpeech?.setSpeechRate(0.8f)
            }
        }
    }

    fun startBreathingExercise(customDurationSeconds: Int? = null, pattern: BreathingPattern = BreathingPattern.BOX) {
        breathingJob?.cancel()
        currentPattern = pattern
        totalSecondsRemaining = customDurationSeconds ?: 60
        _isComplete.value = false
        updateTimeRemaining(totalSecondsRemaining)

        breathingJob = viewModelScope.launch {
            // 3 second preparation
            _instruction.value = "Get ready..."
            speakText("Get ready to begin ${pattern.displayName.lowercase()}")
            for (i in 3 downTo 1) {
                updateTimeRemaining(totalSecondsRemaining)
                delay(1000)
            }

            // Main breathing cycle
            val cycleTime = pattern.inhale + pattern.holdIn + pattern.exhale + pattern.holdOut
            val numCycles = totalSecondsRemaining / cycleTime

            for (cycle in 0 until numCycles) {
                // Inhale
                _phase.value = BreathingPhase.INHALE
                _instruction.value = "Inhale slowly..."
                speakText("Inhale")
                vibrate()
                _progress.value = 0f
                for (i in 0 until pattern.inhale) {
                    _progress.value = (i + 1).toFloat() / pattern.inhale.toFloat()
                    totalSecondsRemaining = (totalSecondsRemaining - 1).coerceAtLeast(0)
                    updateTimeRemaining(totalSecondsRemaining)
                    delay(1000)
                }

                // Hold in
                if (pattern.holdIn > 0) {
                    _phase.value = BreathingPhase.HOLD_IN
                    _instruction.value = "Hold..."
                    speakText("Hold")
                    vibrate()
                    _progress.value = 0f
                    for (i in 0 until pattern.holdIn) {
                        _progress.value = (i + 1).toFloat() / pattern.holdIn.toFloat()
                        totalSecondsRemaining = (totalSecondsRemaining - 1).coerceAtLeast(0)
                        updateTimeRemaining(totalSecondsRemaining)
                        delay(1000)
                    }
                }

                // Exhale
                _phase.value = BreathingPhase.EXHALE
                _instruction.value = "Exhale slowly..."
                speakText("Exhale")
                vibrate()
                _progress.value = 0f
                for (i in 0 until pattern.exhale) {
                    _progress.value = (i + 1).toFloat() / pattern.exhale.toFloat()
                    totalSecondsRemaining = (totalSecondsRemaining - 1).coerceAtLeast(0)
                    updateTimeRemaining(totalSecondsRemaining)
                    delay(1000)
                }

                // Hold out
                if (pattern.holdOut > 0) {
                    _phase.value = BreathingPhase.HOLD_OUT
                    _instruction.value = "Hold..."
                    speakText("Hold")
                    vibrate()
                    _progress.value = 0f
                    for (i in 0 until pattern.holdOut) {
                        _progress.value = (i + 1).toFloat() / pattern.holdOut.toFloat()
                        totalSecondsRemaining = (totalSecondsRemaining - 1).coerceAtLeast(0)
                        updateTimeRemaining(totalSecondsRemaining)
                        delay(1000)
                    }
                }
            }

            // Completion
            _phase.value = BreathingPhase.COMPLETE
            _instruction.value = "Well done! You are back in control ❤️"
            _isComplete.value = true
            updateTimeRemaining(0)
            speakText("Well done! You are back in control")
            vibrate()
        }
    }

    private fun updateTimeRemaining(seconds: Int) {
        val minutes = seconds / 60
        val secs = seconds % 60
        _timeRemaining.value = String.format("%02d:%02d", minutes, secs)
    }

    private suspend fun animateProgress(durationSeconds: Float) {
        val steps = (durationSeconds * 4).toInt() // Smooth animation
        val stepDuration = (durationSeconds * 1000 / steps).toLong()

        for (step in 1..steps) {
            _progress.value = step.toFloat() / steps
            delay(stepDuration)
        }
    }

    private fun speakText(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "breathing_instruction")
    }

    private fun vibrate() {
        try {
            vibrator?.let { vib ->
                // Check if vibrator has permission to vibrate
                if (vib.hasVibrator()) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vib.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        vib.vibrate(100)
                    }
                }
            }
        } catch (e: SecurityException) {
            // Vibration permission not granted - skip vibration silently
            android.util.Log.d("BoxBreathingViewModel", "Vibration permission not available, skipping haptic feedback")
        } catch (e: Exception) {
            // Any other vibration error - skip silently
            android.util.Log.d("BoxBreathingViewModel", "Vibration failed: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        breathingJob?.cancel()
        textToSpeech?.shutdown()
    }
}