package com.coachie.app.viewmodel

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.mealprint.ai.ui.screen.StretchExercise
import com.mealprint.ai.ui.screen.defaultStretches
import java.util.Locale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

enum class StretchingState {
    IDLE,
    PREPARING,
    STRETCHING,
    TRANSITIONING,
    COMPLETED
}

class StretchingViewModel : ViewModel() {
    private var tts: TextToSpeech? = null
    private var ttsInitialized = false
    
    private val _state = MutableStateFlow(StretchingState.IDLE)
    val state: StateFlow<StretchingState> = _state.asStateFlow()
    
    private val _currentStretchIndex = MutableStateFlow(0)
    val currentStretchIndex: StateFlow<Int> = _currentStretchIndex.asStateFlow()
    
    private val _timeRemaining = MutableStateFlow(0L)
    val timeRemaining: StateFlow<Long> = _timeRemaining.asStateFlow()
    
    private val _currentStretch = MutableStateFlow<StretchExercise?>(null)
    val currentStretch: StateFlow<StretchExercise?> = _currentStretch.asStateFlow()
    
    private val _totalProgress = MutableStateFlow(0f)
    val totalProgress: StateFlow<Float> = _totalProgress.asStateFlow()
    
    private val _selectedStretches = MutableStateFlow<List<StretchExercise>>(emptyList())
    val selectedStretches: StateFlow<List<StretchExercise>> = _selectedStretches.asStateFlow()
    
    private var timerJob: Job? = null
    private var stretches: List<StretchExercise> = emptyList()
    private var totalDuration = 0
    private var elapsedTime = 0
    
    fun initialize(context: Context, durationMinutes: Int) {
        // Select stretches based on duration
        val targetSeconds = durationMinutes * 60
        val selectedStretches = mutableListOf<StretchExercise>()
        var currentDuration = 0
        
        // Add stretches until we reach or slightly exceed the target duration
        // For longer routines, we'll include more exercises and may repeat some
        for (stretch in defaultStretches) {
            if (currentDuration + stretch.duration <= targetSeconds + 30) { // Allow 30 second buffer
                selectedStretches.add(stretch)
                currentDuration += stretch.duration
            } else {
                break
            }
        }
        
        // For longer routines (15+ minutes), add more stretches or repeat some
        if (durationMinutes >= 15 && selectedStretches.size < defaultStretches.size) {
            // Add remaining stretches
            for (stretch in defaultStretches.drop(selectedStretches.size)) {
                if (currentDuration + stretch.duration <= targetSeconds + 60) {
                    selectedStretches.add(stretch)
                    currentDuration += stretch.duration
                }
            }
        }
        
        // For very long routines (20+ minutes), repeat some key stretches
        if (durationMinutes >= 20 && currentDuration < targetSeconds - 60) {
            val keyStretches = listOf(
                defaultStretches[2], // Standing Forward Fold
                defaultStretches[4], // Quad Stretch
                defaultStretches[6], // Hip Circles
                defaultStretches[9]  // Hamstring Stretch
            )
            for (stretch in keyStretches) {
                if (currentDuration + stretch.duration <= targetSeconds + 60) {
                    selectedStretches.add(stretch)
                    currentDuration += stretch.duration
                }
            }
        }
        
        stretches = selectedStretches.ifEmpty { defaultStretches.take(6) } // Fallback to 6 if empty
        totalDuration = stretches.sumOf { it.duration }
        _selectedStretches.value = stretches
        
        // Initialize TTS
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    android.util.Log.e("StretchingViewModel", "TTS language not supported")
                } else {
                    ttsInitialized = true
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {}
                        override fun onError(utteranceId: String?) {}
                    })
                }
            }
        }
    }
    
    fun startStretching() {
        if (stretches.isEmpty()) return
        
        _state.value = StretchingState.PREPARING
        _currentStretchIndex.value = 0
        elapsedTime = 0
        
        viewModelScope.launch {
            delay(2000) // 2 second preparation
            startCurrentStretch()
        }
    }
    
    private fun startCurrentStretch() {
        if (_currentStretchIndex.value >= stretches.size) {
            completeStretching()
            return
        }
        
        val stretch = stretches[_currentStretchIndex.value]
        _currentStretch.value = stretch
        _timeRemaining.value = stretch.duration.toLong()
        _state.value = StretchingState.STRETCHING
        
        // Announce the stretch
        speakText("${stretch.name}. ${stretch.description}")
        
        // Start timer
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var remaining = stretch.duration
            while (remaining > 0 && _state.value == StretchingState.STRETCHING) {
                delay(1000)
                remaining--
                _timeRemaining.value = remaining.toLong()
                elapsedTime++
                updateProgress()
            }
            
            if (remaining == 0) {
                // Move to next stretch
                transitionToNextStretch()
            }
        }
    }
    
    private fun transitionToNextStretch() {
        timerJob?.cancel()
        _state.value = StretchingState.TRANSITIONING
        
        viewModelScope.launch {
            delay(1000) // 1 second transition
            
            _currentStretchIndex.value = _currentStretchIndex.value + 1
            if (_currentStretchIndex.value < stretches.size) {
                startCurrentStretch()
            } else {
                completeStretching()
            }
        }
    }
    
    private fun completeStretching() {
        timerJob?.cancel()
        _state.value = StretchingState.COMPLETED
        speakText("Great job! You've completed your stretching routine.")
    }
    
    fun pause() {
        timerJob?.cancel()
        _state.value = StretchingState.IDLE
    }
    
    fun resume() {
        if (_currentStretch.value != null) {
            val remaining = _timeRemaining.value.toInt()
            if (remaining > 0) {
                _state.value = StretchingState.STRETCHING
                timerJob = viewModelScope.launch {
                    var timeLeft = remaining
                    while (timeLeft > 0 && _state.value == StretchingState.STRETCHING) {
                        delay(1000)
                        timeLeft--
                        _timeRemaining.value = timeLeft.toLong()
                        elapsedTime++
                        updateProgress()
                    }
                    
                    if (timeLeft == 0) {
                        transitionToNextStretch()
                    }
                }
            }
        }
    }
    
    fun stop() {
        timerJob?.cancel()
        _state.value = StretchingState.IDLE
        _currentStretchIndex.value = 0
        _timeRemaining.value = 0
        elapsedTime = 0
        updateProgress()
    }
    
    private fun updateProgress() {
        if (totalDuration > 0) {
            _totalProgress.value = elapsedTime.toFloat() / totalDuration.toFloat()
        }
    }
    
    private fun speakText(text: String) {
        if (ttsInitialized && tts != null) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "stretching_${System.currentTimeMillis()}")
        }
    }
    
    fun formatTime(seconds: Long): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%d:%02d", minutes, secs)
    }
    
    fun cleanup() {
        timerJob?.cancel()
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsInitialized = false
    }
}

