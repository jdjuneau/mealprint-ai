package com.coachie.app.viewmodel

import android.content.Context
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealprint.ai.data.HabitRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HabitTimerViewModel : ViewModel() {
    enum class TimerState {
        IDLE,
        RUNNING,
        PAUSED,
        COMPLETED
    }
    private val _state = MutableStateFlow(TimerState.IDLE)
    val state: StateFlow<TimerState> = _state.asStateFlow()
    
    private val _timeRemaining = MutableStateFlow(0L)
    val timeRemaining: StateFlow<Long> = _timeRemaining.asStateFlow()
    
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    private val _habitTitle = MutableStateFlow("")
    val habitTitle: StateFlow<String> = _habitTitle.asStateFlow()
    
    private var timerJob: Job? = null
    private var totalDurationSeconds: Long = 0L
    
    fun initialize(durationMinutes: Int, habitTitle: String = "") {
        // CRITICAL FIX: Always treat durationMinutes as minutes (multiply by 60 to get seconds)
        // Special case: If duration is very small (<= 30) AND habit title contains "second" or "sec",
        // then treat it as seconds (for habits like "10-second eye close")
        val titleLower = habitTitle.lowercase()
        val isSeconds = durationMinutes <= 30 && (
            titleLower.contains("second") || 
            titleLower.contains("sec")
        )
        
        totalDurationSeconds = if (isSeconds) {
            // Special case: treat as seconds
            // Try to extract the number from the title (e.g., "10-second" -> 10)
            val numberRegex = Regex("(\\d+)[-\\s]*(?:second|sec)")
            val matchResult = numberRegex.find(titleLower)
            if (matchResult != null) {
                val extractedSeconds = matchResult.groupValues[1].toIntOrNull()
                if (extractedSeconds != null && extractedSeconds > 0) {
                    android.util.Log.d("HabitTimerViewModel", "Extracted $extractedSeconds seconds from title: '$habitTitle'")
                    extractedSeconds.toLong()
                } else {
                    // Fallback to durationMinutes if extraction fails
                    durationMinutes.toLong()
                }
            } else {
                // Fallback to durationMinutes if no number found
                durationMinutes.toLong()
            }
        } else {
            // Normal case: treat as minutes (convert to seconds)
            durationMinutes * 60L
        }
        
        android.util.Log.d("HabitTimerViewModel", "Timer initialized: durationMinutes=$durationMinutes, habitTitle='$habitTitle', isSeconds=$isSeconds, totalDurationSeconds=$totalDurationSeconds")
        _timeRemaining.value = totalDurationSeconds
        _habitTitle.value = habitTitle
        _state.value = TimerState.IDLE
    }
    
    fun start(context: Context? = null, useBackgroundService: Boolean = false) {
        if (_state.value == TimerState.IDLE || _state.value == TimerState.PAUSED) {
            _state.value = TimerState.RUNNING
            _isRunning.value = true
            
            // If it's a reading habit and background service is requested, use service
            if (useBackgroundService && context != null) {
                val totalSeconds = _timeRemaining.value.toInt()
                val habitTitle = _habitTitle.value
                com.coachie.app.service.HabitTimerService.startTimer(context, totalSeconds, habitTitle)
                // Start local timer that syncs with service
                startTimerWithServiceSync(context, totalSeconds)
            } else {
                startTimer()
            }
        }
    }
    
    private fun startTimerWithServiceSync(context: Context, totalSeconds: Int) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            android.util.Log.d("HabitTimerViewModel", "Timer started with service sync, counting down from ${_timeRemaining.value} seconds")
            
            while (_timeRemaining.value > 0 && _state.value == TimerState.RUNNING) {
                delay(1000)
                // Calculate remaining time based on elapsed time
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                val remaining = (totalSeconds - elapsed).coerceAtLeast(0)
                _timeRemaining.value = remaining
                
                if (_timeRemaining.value % 10 == 0L || _timeRemaining.value <= 5) {
                    android.util.Log.d("HabitTimerViewModel", "Time remaining: ${_timeRemaining.value} seconds")
                }
            }

            if (_timeRemaining.value == 0L) {
                android.util.Log.i("HabitTimerViewModel", "⏰ TIMER COMPLETED! Setting state to COMPLETED")
                _state.value = TimerState.COMPLETED
                _isRunning.value = false
                // Stop the service
                com.coachie.app.service.HabitTimerService.stopTimer(context)
            } else {
                android.util.Log.w("HabitTimerViewModel", "Timer stopped but time remaining is ${_timeRemaining.value}, state is ${_state.value}")
            }
        }
    }
    
    fun pause() {
        if (_state.value == TimerState.RUNNING) {
            _state.value = TimerState.PAUSED
            _isRunning.value = false
            timerJob?.cancel()
        }
    }
    
    fun resume(context: Context? = null, useBackgroundService: Boolean = false) {
        if (_state.value == TimerState.PAUSED) {
            _state.value = TimerState.RUNNING
            _isRunning.value = true
            
            // If using background service, restart it
            if (useBackgroundService && context != null) {
                val totalSeconds = _timeRemaining.value.toInt()
                val habitTitle = _habitTitle.value
                com.coachie.app.service.HabitTimerService.startTimer(context, totalSeconds, habitTitle)
            }
            startTimer()
        }
    }
    
    fun stop(context: Context? = null) {
        _state.value = TimerState.IDLE
        _isRunning.value = false
        timerJob?.cancel()
        _timeRemaining.value = totalDurationSeconds
        
        // Stop background service if running
        context?.let {
            com.coachie.app.service.HabitTimerService.stopTimer(it)
        }
    }
    
    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            android.util.Log.d("HabitTimerViewModel", "Timer started, counting down from ${_timeRemaining.value} seconds")
            while (_timeRemaining.value > 0 && _state.value == TimerState.RUNNING) {
                delay(1000)
                _timeRemaining.value = _timeRemaining.value - 1
                if (_timeRemaining.value % 10 == 0L || _timeRemaining.value <= 5) {
                    android.util.Log.d("HabitTimerViewModel", "Time remaining: ${_timeRemaining.value} seconds")
                }
            }

            if (_timeRemaining.value == 0L) {
                android.util.Log.i("HabitTimerViewModel", "⏰ TIMER COMPLETED! Setting state to COMPLETED")
                _state.value = TimerState.COMPLETED
                _isRunning.value = false
                android.util.Log.d("HabitTimerViewModel", "State set to COMPLETED, isRunning set to false")
                android.util.Log.i("HabitTimerViewModel", "🔔 State change to COMPLETED should trigger LaunchedEffect in HabitTimerScreen")
            } else {
                android.util.Log.w("HabitTimerViewModel", "Timer stopped but time remaining is ${_timeRemaining.value}, state is ${_state.value}")
            }
        }
    }

    fun playCompletionNotification(context: Context) {
        viewModelScope.launch {
            try {
                // Play notification sound
                val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context, notification)
                ringtone.play()

                // Vibrate (if vibration is available)
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.let {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        it.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        it.vibrate(500)
                    }
                }

                // Stop ringtone after 2 seconds
                delay(2000)
                if (ringtone.isPlaying) {
                    ringtone.stop()
                }
            } catch (e: Exception) {
                android.util.Log.w("HabitTimerViewModel", "Failed to play completion notification", e)
            }
        }
    }
    
    private val _completionResult = MutableStateFlow<Result<String>?>(null)
    val completionResult: StateFlow<Result<String>?> = _completionResult.asStateFlow()
    
    suspend fun completeHabit(context: Context, userId: String, habitId: String, durationMinutes: Int): Result<String> {
        return try {
            android.util.Log.i("HabitTimerViewModel", "🚀 STARTING habit completion: userId=$userId, habitId=$habitId, duration=$durationMinutes")
            
            if (userId.isBlank() || habitId.isBlank()) {
                val error = IllegalArgumentException("Invalid userId or habitId: userId='$userId', habitId='$habitId'")
                android.util.Log.e("HabitTimerViewModel", "❌ Invalid parameters", error)
                return Result.failure(error)
            }
            
            val habitRepository = HabitRepository.getInstance()
            android.util.Log.d("HabitTimerViewModel", "Calling habitRepository.completeHabit...")
            val result = habitRepository.completeHabit(userId, habitId, durationMinutes, null)
            
            result.onSuccess { completionId ->
                android.util.Log.i("HabitTimerViewModel", "✅ HABIT COMPLETED SUCCESSFULLY! Completion ID: $completionId")
                
                // CRITICAL: Wait for Firestore to propagate before updating UI
                delay(2000) // Wait 2 seconds for Firestore to propagate
                
                // Show success toast
                android.widget.Toast.makeText(
                    context,
                    "Habit completed and saved!",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }.onFailure { error ->
                android.util.Log.e("HabitTimerViewModel", "❌ FAILED to save habit completion", error)
                android.util.Log.e("HabitTimerViewModel", "Error type: ${error.javaClass.simpleName}, message: ${error.message}")
                error.printStackTrace()
                // Show error to user
                android.widget.Toast.makeText(
                    context,
                    "Failed to save habit: ${error.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            
            _completionResult.value = result
            result
        } catch (e: Exception) {
            android.util.Log.e("HabitTimerViewModel", "❌ EXCEPTION completing habit", e)
            android.util.Log.e("HabitTimerViewModel", "Exception type: ${e.javaClass.simpleName}, message: ${e.message}")
            e.printStackTrace()
            val errorResult = Result.failure<String>(e)
            _completionResult.value = errorResult
            android.widget.Toast.makeText(
                context,
                "Error: ${e.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
            errorResult
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

