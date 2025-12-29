package com.coachie.app.viewmodel

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.*
import kotlin.coroutines.resume
import android.speech.tts.UtteranceProgressListener

class VoiceSettingsViewModel : ViewModel() {

    private var textToSpeech: TextToSpeech? = null

    suspend fun initializeTTS(
        context: Context,
        onInitialized: (TextToSpeech?) -> Unit
    ) = suspendCancellableCoroutine { continuation ->
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                onInitialized(textToSpeech)
            } else {
                onInitialized(null)
            }
            continuation.resume(Unit)
        }
    }

    fun testVoice(
        context: Context,
        voiceName: String?,
        pitch: Float,
        rate: Float,
        volume: Float
    ) {
        val tts = TextToSpeech(context, null)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onError(utteranceId: String?) {
                tts.shutdown()
            }
            override fun onDone(utteranceId: String?) {
                tts.shutdown()
            }
        })

        // Set voice if specified
        voiceName?.let { name ->
            tts.voices?.find { it.name == name }?.let { voice ->
                tts.voice = voice
            }
        }

        // Set voice parameters
        tts.setPitch(pitch)
        tts.setSpeechRate(rate)

        // Test message
        val testText = "Hello! I'm Coachie, your AI fitness assistant. How can I help you today?"
        tts.speak(testText, TextToSpeech.QUEUE_FLUSH, null, "voice_test")
    }

    override fun onCleared() {
        super.onCleared()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
