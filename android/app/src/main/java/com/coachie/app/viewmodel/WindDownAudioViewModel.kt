package com.coachie.app.viewmodel

import android.content.Context
import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coachie.app.ui.screen.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WindDownAudioViewModel : ViewModel() {

    private val _currentTrack = MutableStateFlow<AudioTrack?>(null)
    val currentTrack: StateFlow<AudioTrack?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _duration = MutableStateFlow("0:00")
    val duration: StateFlow<String> = _duration.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var context: Context? = null

    fun initialize(context: Context) {
        this.context = context
    }

    fun selectTrack(track: AudioTrack) {
        // Stop current playback
        stopPlayback()

        _currentTrack.value = track

        // In a real implementation, you would load the actual audio file
        // For now, we'll simulate playback
        simulatePlayback(track)
    }

    fun playPause() {
        if (_isPlaying.value) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    fun updateProgress() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                val currentPosition = player.currentPosition.toFloat()
                val totalDuration = player.duration.toFloat()
                if (totalDuration > 0) {
                    _progress.value = currentPosition / totalDuration
                }
            }
        }
    }

    private fun simulatePlayback(track: AudioTrack) {
        // Simulate audio playback with a timer
        // In a real implementation, you would load actual audio files
        viewModelScope.launch {
            _isPlaying.value = true
            _progress.value = 0f
            _duration.value = track.duration

            // Simulate progress over time
            val totalSeconds = parseDurationToSeconds(track.duration)
            var currentSecond = 0

            while (currentSecond < totalSeconds && _isPlaying.value) {
                kotlinx.coroutines.delay(1000)
                currentSecond++
                _progress.value = currentSecond.toFloat() / totalSeconds.toFloat()
            }

            if (_isPlaying.value) {
                _isPlaying.value = false
                _progress.value = 0f
            }
        }
    }

    private fun startPlayback() {
        mediaPlayer?.start()
        _isPlaying.value = true
    }

    private fun pausePlayback() {
        mediaPlayer?.pause()
        _isPlaying.value = false
    }

    private fun stopPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        _isPlaying.value = false
        _progress.value = 0f
    }

    private fun parseDurationToSeconds(duration: String): Int {
        // Parse "MM:SS" format to seconds
        return try {
            val parts = duration.split(":")
            if (parts.size == 2) {
                val minutes = parts[0].toInt()
                val seconds = parts[1].toInt()
                minutes * 60 + seconds
            } else {
                300 // Default 5 minutes
            }
        } catch (e: Exception) {
            300 // Default 5 minutes
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
    }
}
