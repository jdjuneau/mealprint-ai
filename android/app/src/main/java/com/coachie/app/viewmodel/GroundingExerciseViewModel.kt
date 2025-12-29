package com.coachie.app.viewmodel

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coachie.app.ui.screen.GroundingPhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.max

class GroundingExerciseViewModel : ViewModel() {

    private val _currentPhase = MutableStateFlow<GroundingPhase?>(null)
    val currentPhase: StateFlow<GroundingPhase?> = _currentPhase.asStateFlow()

    private val _timeRemaining = MutableStateFlow("01:30")
    val timeRemaining: StateFlow<String> = _timeRemaining.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _isComplete = MutableStateFlow(false)
    val isComplete: StateFlow<Boolean> = _isComplete.asStateFlow()

    private var vibrator: Vibrator? = null
    private var textToSpeech: TextToSpeech? = null
    private var exerciseJob: Job? = null
    private var context: Context? = null

    private val totalDurationSeconds = 90 // 90 seconds total
    private var remainingSeconds = totalDurationSeconds

    // 5-4-3-2-1 grounding phases
    private val phases = listOf(
        GroundingPhase(
            number = 5,
            instruction = "5 things you can see",
            description = "Look around and name 5 things you can see",
            progress = 0.2f
        ),
        GroundingPhase(
            number = 4,
            instruction = "4 things you can touch",
            description = "Notice 4 things you can touch right now",
            progress = 0.4f
        ),
        GroundingPhase(
            number = 3,
            instruction = "3 things you can hear",
            description = "Listen for 3 different sounds around you",
            progress = 0.6f
        ),
        GroundingPhase(
            number = 2,
            instruction = "2 things you can smell",
            description = "Notice 2 scents in your environment",
            progress = 0.8f
        ),
        GroundingPhase(
            number = 1,
            instruction = "1 thing you can taste",
            description = "Focus on one taste sensation",
            progress = 1.0f
        )
    )

    fun initialize(context: Context) {
        this.context = context
        vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)

        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
                textToSpeech?.setSpeechRate(0.9f) // Slightly slower for grounding
                textToSpeech?.setPitch(0.95f) // Slightly lower pitch for calming effect
            }
        }
    }

    fun startExercise() {
        if (_isActive.value) return

        exerciseJob?.cancel()
        _isActive.value = true
        _isComplete.value = false
        remainingSeconds = totalDurationSeconds

        exerciseJob = viewModelScope.launch {
            // Gentle start vibration
            vibrateGentle()

            // Brief introduction
            speakText("Let's do a grounding exercise together. Take a comfortable seat and follow along.")
            delay(3000)

            // Go through each phase
            for (phase in phases) {
                _currentPhase.value = phase

                // Speak the instruction
                speakText(phase.instruction)
                delay(2000) // Pause after instruction

                // Speak the description
                speakText(phase.description)

                // Wait for the phase duration (18 seconds each)
                val phaseStartTime = System.currentTimeMillis()
                val phaseDuration = 18 * 1000L // 18 seconds
                while (System.currentTimeMillis() - phaseStartTime < phaseDuration) {
                    updateTimeRemaining()
                    delay(1000)
                }

                // Gentle transition vibration
                vibrateGentle()
            }

            // Completion
            _currentPhase.value = null
            _isActive.value = false
            _isComplete.value = true

            speakText("Well done. You're now grounded in the present moment.")
            vibrateGentle()
        }
    }

    private fun updateTimeRemaining() {
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        _timeRemaining.value = String.format("%02d:%02d", minutes, seconds)
        remainingSeconds = max(0, remainingSeconds - 1)
    }

    private fun speakText(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_ADD, null, "grounding_instruction")
    }

    private fun vibrateGentle() {
        try {
            vibrator?.let { vib ->
                // Check if vibrator has permission to vibrate
                if (vib.hasVibrator()) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vib.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        vib.vibrate(300)
                    }
                }
            }
        } catch (e: SecurityException) {
            // Vibration permission not granted - skip vibration silently
            android.util.Log.d("GroundingExerciseViewModel", "Vibration permission not available, skipping haptic feedback")
        } catch (e: Exception) {
            // Any other vibration error - skip silently
            android.util.Log.d("GroundingExerciseViewModel", "Vibration failed: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        exerciseJob?.cancel()
        textToSpeech?.shutdown()
    }
}
