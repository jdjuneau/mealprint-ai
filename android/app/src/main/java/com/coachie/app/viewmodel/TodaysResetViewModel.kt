package com.coachie.app.viewmodel

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.local.PreferencesManager
import com.coachie.app.data.model.HealthLog
import com.coachie.app.service.DailyMindfulSessionGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TodaysResetViewModel : ViewModel() {

    companion object {
        private const val TAG = "TodaysResetViewModel"
    }

    private val firebaseRepository = FirebaseRepository.getInstance()

    private var context: Context? = null
    private var userId: String? = null
    private var mediaPlayer: MediaPlayer? = null
    private var textToSpeech: TextToSpeech? = null
    private var preferencesManager: PreferencesManager? = null

    // UI States
    private val _todaysSession = MutableStateFlow<HealthLog.MindfulSession?>(null)
    val todaysSession: StateFlow<HealthLog.MindfulSession?> = _todaysSession.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val _isTranscriptExpanded = MutableStateFlow(false)
    val isTranscriptExpanded: StateFlow<Boolean> = _isTranscriptExpanded.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun initialize(context: Context, userId: String) {
        this.context = context
        this.userId = userId
        this.preferencesManager = PreferencesManager(context)
        
        // Initialize TextToSpeech
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                Log.d(TAG, "TextToSpeech initialized successfully")
                
                // Apply saved voice settings
                preferencesManager?.let { prefs ->
                    prefs.voiceLanguage?.let { voiceName ->
                        textToSpeech?.voices?.find { it.name == voiceName }?.let { voice ->
                            textToSpeech?.voice = voice
                            Log.d(TAG, "Set voice to: ${voice.name}")
                        }
                    } ?: run {
                        textToSpeech?.language = Locale.getDefault()
                        Log.d(TAG, "Using default locale: ${Locale.getDefault()}")
                    }
                    
                    textToSpeech?.setPitch(prefs.voicePitch)
                    textToSpeech?.setSpeechRate(prefs.voiceRate)
                    Log.d(TAG, "Voice settings applied - pitch: ${prefs.voicePitch}, rate: ${prefs.voiceRate}")
                }
                
                // Set up utterance progress listener
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "TTS playback started")
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "TTS playback completed")
                        viewModelScope.launch {
                            _isPlaying.value = false
                            _playbackProgress.value = 1f
                            delay(500) // Brief delay before resetting
                            _playbackProgress.value = 0f
                            updatePlayCount()
                        }
                    }
                    
                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "TTS playback error")
                        viewModelScope.launch {
                            _isPlaying.value = false
                            _playbackProgress.value = 0f
                        }
                    }
                })
            } else {
                Log.e(TAG, "TextToSpeech initialization failed with status: $status")
            }
        }
        
        loadTodaysSession()
    }

    /**
     * Generate a session on demand
     */
    fun generateSessionOnDemand() {
        Log.i(TAG, "=========================================")
        Log.i(TAG, "GENERATE SESSION ON DEMAND CALLED")
        Log.i(TAG, "=========================================")
        Log.d(TAG, "UserId: $userId")
        Log.d(TAG, "Context: ${if (context != null) "Available" else "NULL"}")
        
        val uid = userId
        if (uid == null) {
            Log.e(TAG, "ERROR: userId is null! Cannot generate session.")
            _errorMessage.value = "User not initialized. Please try again."
            return
        }
        
        val ctx = context
        if (ctx == null) {
            Log.e(TAG, "ERROR: context is null! Cannot generate session.")
            _errorMessage.value = "App context not available. Please try again."
            return
        }

        viewModelScope.launch {
            Log.d(TAG, "Starting generation in coroutine scope")
            _isGenerating.value = true
            _errorMessage.value = null // Clear any previous errors
            
            try {
                Log.d(TAG, "Creating DailyMindfulSessionGenerator...")
                val generator = DailyMindfulSessionGenerator(ctx)
                Log.d(TAG, "Calling generateTodaysSession for user: $uid")
                val result = generator.generateTodaysSession(uid)
                Log.d(TAG, "Generation result received. Success: ${result.isSuccess}")
                
                if (result.isSuccess) {
                    val session = result.getOrNull()
                    if (session != null) {
                        // Use the session directly - don't reload as it might not be immediately available in Firebase
                        _todaysSession.value = session
                        _isFavorite.value = session.isFavorite
                        Log.d(TAG, "Using generated session directly: ${session.sessionId}")
                        
                        // Optionally reload after a longer delay to sync with Firebase, but don't overwrite if we have it
                        delay(2000)
                        // Only reload if we still have the session (don't overwrite with null)
                        if (_todaysSession.value?.sessionId == session.sessionId) {
                            loadTodaysSession()
                        }
                    } else {
                        // If no session returned, reload after delay
                        delay(2000)
                        loadTodaysSession()
                    }
                } else {
                    val exception = result.exceptionOrNull()
                    val errorMsg = exception?.message ?: "Unknown error"
                    Log.e(TAG, "Failed to generate session on demand", exception)
                    
                    // Provide user-friendly error messages
                    val userFriendlyMessage = when {
                        errorMsg.contains("API key", ignoreCase = true) || 
                        errorMsg.contains("IllegalStateException", ignoreCase = true) ->
                            "AI service unavailable - please check API configuration"
                        errorMsg.contains("network", ignoreCase = true) ||
                        errorMsg.contains("connection", ignoreCase = true) ->
                            "Network error - please check your internet connection"
                        errorMsg.contains("quota", ignoreCase = true) ||
                        errorMsg.contains("rate limit", ignoreCase = true) ->
                            "Service temporarily unavailable - please try again later"
                        else ->
                            "Failed to generate reset: ${errorMsg.take(100)}"
                    }
                    
                    _errorMessage.value = userFriendlyMessage
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating session on demand", e)
                
                val userFriendlyMessage = when {
                    e.message?.contains("API key", ignoreCase = true) == true ||
                    e is IllegalStateException ->
                        "AI service unavailable - please check API configuration"
                    e.message?.contains("network", ignoreCase = true) == true ||
                    e.message?.contains("connection", ignoreCase = true) == true ->
                        "Network error - please check your internet connection"
                    else ->
                        "Unexpected error: ${e.localizedMessage ?: "Unknown error"}"
                }
                
                _errorMessage.value = userFriendlyMessage
            } finally {
                _isGenerating.value = false
            }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Load today's mindful session from Firebase
     */
    private fun loadTodaysSession() {
        val uid = userId ?: return
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        viewModelScope.launch {
            _isLoading.value = true

            try {
                val healthLogs = firebaseRepository.getHealthLogs(uid, today).getOrNull() ?: emptyList()
                Log.d(TAG, "Retrieved ${healthLogs.size} health logs for $today")
                val sessions = healthLogs.filterIsInstance<HealthLog.MindfulSession>()
                Log.d(TAG, "Found ${sessions.size} mindful sessions")
                sessions.forEach { s ->
                    Log.d(TAG, "Session: ${s.sessionId}, generatedDate: ${s.generatedDate}, today: $today")
                }
                val session = sessions.firstOrNull { it.generatedDate == today }

                // Only update if we found a session, or if we don't have one already set
                if (session != null || _todaysSession.value == null) {
                    _todaysSession.value = session
                    _isFavorite.value = session?.isFavorite ?: false
                }

                Log.d(TAG, "Loaded today's session: ${session?.sessionId ?: "null"}")

            } catch (e: Exception) {
                Log.e(TAG, "Error loading today's session", e)
                _todaysSession.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Toggle audio playback
     */
    fun togglePlayback() {
        if (_isPlaying.value) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    /**
     * Start audio playback using TextToSpeech
     */
    private fun startPlayback() {
        val session = _todaysSession.value ?: run {
            Log.e(TAG, "No session available to play")
            return
        }
        val tts = textToSpeech ?: run {
            Log.e(TAG, "TextToSpeech not initialized")
            return
        }

        // Check if transcript is valid
        val transcript = session.transcript?.takeIf { it.isNotBlank() } ?: run {
            Log.e(TAG, "Session transcript is empty or null")
            _isPlaying.value = false
            _playbackProgress.value = 0f
            return
        }

        Log.d(TAG, "Starting playback for session: ${session.sessionId}")
        Log.d(TAG, "Transcript length: ${transcript.length} characters")
        
        try {
            _isPlaying.value = true
            _playbackProgress.value = 0f

            // Start speaking the transcript
            val utteranceId = "mindful_session_${session.sessionId}"
            val result = tts.speak(transcript, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            
            if (result == TextToSpeech.ERROR) {
                Log.e(TAG, "Error starting TTS playback")
                _isPlaying.value = false
                _playbackProgress.value = 0f
            } else {
                Log.d(TAG, "TTS playback started successfully")
                
                // Simulate progress updates (TTS doesn't provide real-time progress)
                // Estimate ~150 words per minute for progress calculation
                val wordCount = transcript.split("\\s+".toRegex()).size
                val estimatedDurationMs = (wordCount / 150f * 60000f).toLong().coerceAtLeast(1000L)
                val updateInterval = 100L // Update every 100ms
                val progressIncrement = updateInterval.toFloat() / estimatedDurationMs
                
                viewModelScope.launch {
                    var progress = 0f
                    while (_isPlaying.value && progress < 0.99f) {
                        delay(updateInterval)
                        progress += progressIncrement
                        _playbackProgress.value = progress.coerceAtMost(0.99f)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during playback start", e)
            _isPlaying.value = false
            _playbackProgress.value = 0f
        }
    }

    /**
     * Pause audio playback
     */
    private fun pausePlayback() {
        Log.d(TAG, "Pausing playback")
        _isPlaying.value = false
        textToSpeech?.stop()
    }

    /**
     * Toggle transcript expansion
     */
    fun toggleTranscriptExpanded() {
        _isTranscriptExpanded.value = !_isTranscriptExpanded.value
    }

    /**
     * Toggle favorite status
     */
    fun toggleFavorite() {
        val currentSession = _todaysSession.value ?: return
        val newFavoriteStatus = !_isFavorite.value

        _isFavorite.value = newFavoriteStatus

        // Update session in Firebase
        viewModelScope.launch {
            try {
                val updatedSession = currentSession.copy(
                    isFavorite = newFavoriteStatus,
                    playedCount = if (newFavoriteStatus && currentSession.playedCount == 0) 1 else currentSession.playedCount
                )

                // Save updated session
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                firebaseRepository.saveHealthLog(userId!!, today, updatedSession)

                _todaysSession.value = updatedSession

                Log.d(TAG, "Updated favorite status to $newFavoriteStatus for session ${currentSession.sessionId}")

            } catch (e: Exception) {
                Log.e(TAG, "Error updating favorite status", e)
                // Revert on error
                _isFavorite.value = currentSession.isFavorite
            }
        }
    }

    /**
     * Update play count when session is completed
     */
    private fun updatePlayCount() {
        val currentSession = _todaysSession.value ?: return

        viewModelScope.launch {
            try {
                val updatedSession = currentSession.copy(
                    playedCount = currentSession.playedCount + 1,
                    lastPlayedAt = System.currentTimeMillis()
                )

                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                firebaseRepository.saveHealthLog(userId!!, today, updatedSession)

                _todaysSession.value = updatedSession

            } catch (e: Exception) {
                Log.e(TAG, "Error updating play count", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
