package com.coachie.app.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.mealprint.ai.data.local.PreferencesManager
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Voice styles for different contexts
 */
enum class VoiceStyle {
    MORNING,      // rate=1.1, pitch=1.08 - Fast, energetic, engaging
    WINS,         // rate=1.1, pitch=1.08 - Fast, celebratory, warm
    MINDFULNESS,  // rate=0.9, pitch=0.98 - Calm but not too slow
    INSIGHTS,     // rate=1.05, pitch=1.02 - Fast, clear, informative
    JOURNAL       // rate=1.05, pitch=1.02 - Fast, conversational
}

/**
 * Centralized TTS service for the app
 * Handles text-to-speech with different voice styles
 * Auto-stops on navigation, caches recent phrases
 */
class TtsService private constructor(private val context: Context) {
    
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private val preferencesManager = PreferencesManager(context)
    
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()
    
    // Cache for recent phrases (last 5, skip repeat <5 min)
    private val recentPhrases = mutableListOf<Pair<String, Long>>()
    private val cacheWindowMs = 5 * 60 * 1000L // 5 minutes
    
    companion object {
        private const val TAG = "TtsService"
        @Volatile
        private var INSTANCE: TtsService? = null
        
        fun getInstance(context: Context): TtsService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TtsService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    init {
        initialize()
    }
    
    private fun initialize() {
        // CRITICAL: Try to use Google TTS engine explicitly for best quality neural voices
        // Google's TTS engine has the best neural voices available
        val googleTtsEngine = "com.google.android.tts"
        
        // Try to initialize with Google TTS engine explicitly
        try {
            val intent = android.content.Intent(android.speech.tts.TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
            val resolveInfos = context.packageManager.queryIntentActivities(intent, 0)
            val hasGoogleTts = resolveInfos.any { it.activityInfo.packageName == googleTtsEngine }
            
            if (hasGoogleTts) {
                Log.d(TAG, "Google TTS engine is available - initializing with it")
                textToSpeech = TextToSpeech(context, { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        // Verify we're using Google TTS
                        val currentEngine = textToSpeech?.defaultEngine
                        if (currentEngine == googleTtsEngine) {
                            Log.d(TAG, "✅ Successfully using Google TTS engine")
                        } else {
                            Log.w(TAG, "⚠️ Using engine: $currentEngine (not Google TTS)")
                        }
                        finishInitialization()
                    } else {
                        Log.e(TAG, "Failed to initialize with Google TTS, trying default")
                        initializeWithDefault()
                    }
                }, googleTtsEngine)
            } else {
                Log.w(TAG, "Google TTS engine not available, using default")
                initializeWithDefault()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for Google TTS, using default", e)
            initializeWithDefault()
        }
    }
    
    private fun initializeWithDefault() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                finishInitialization()
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
                isInitialized = false
            }
        }
    }
    
    private fun finishInitialization() {
        isInitialized = true
        Log.d(TAG, "TTS initialized successfully")
        
        // Verify we're using Google TTS engine
        val currentEngine = textToSpeech?.defaultEngine
        Log.d(TAG, "Current TTS engine: $currentEngine")
        if (currentEngine == "com.google.android.tts") {
            Log.d(TAG, "✅ Using Google TTS engine - best neural voices available")
        } else {
            Log.w(TAG, "⚠️ Not using Google TTS engine! Current: $currentEngine")
            Log.w(TAG, "⚠️ Google TTS engine has the best neural voices. Consider switching in device settings.")
        }
        
        // Apply saved voice settings, or auto-select best voice
        preferencesManager.voiceLanguage?.let { voiceName ->
            textToSpeech?.voices?.find { it.name == voiceName }?.let { voice ->
                textToSpeech?.voice = voice
                Log.d(TAG, "Set voice to saved preference: ${voice.name}")
            } ?: run {
                // Saved voice not found, auto-select best voice
                autoSelectBestVoice()
            }
        } ?: run {
            // No saved preference, auto-select best voice
            autoSelectBestVoice()
        }
        
        // Set default pitch and rate from preferences (with VERY FAST, natural defaults)
        val defaultPitch = if (preferencesManager.voicePitch == 1.0f) 1.12f else preferencesManager.voicePitch
        val defaultRate = if (preferencesManager.voiceRate == 1.0f) 1.35f else preferencesManager.voiceRate
        textToSpeech?.setPitch(defaultPitch)
        textToSpeech?.setSpeechRate(defaultRate)
        Log.d(TAG, "Set default TTS rate: $defaultRate, pitch: $defaultPitch")
        
        // Log ALL available voices for debugging - especially neural ones
        val availableVoices = textToSpeech?.voices ?: emptySet()
        Log.d(TAG, "=========================================")
        Log.d(TAG, "AVAILABLE TTS VOICES: ${availableVoices.size} total")
        Log.d(TAG, "=========================================")
        
        // Log all neural voices first
        val neuralVoices = availableVoices.filter { isNeuralVoice(it) }
        Log.d(TAG, "NEURAL VOICES (${neuralVoices.size}):")
        neuralVoices.take(10).forEach { voice ->
            Log.d(TAG, "  ✅ ${voice.name} (${voice.locale}) - NEURAL")
        }
        
        // Log locale-matched voices
        val localeVoices = availableVoices.filter { it.locale.language == Locale.getDefault().language }
        Log.d(TAG, "LOCALE-MATCHED VOICES (${localeVoices.size}):")
        localeVoices.take(10).forEach { voice ->
            val isNeural = isNeuralVoice(voice)
            Log.d(TAG, "  ${if (isNeural) "✅" else "  "} ${voice.name} (${voice.locale})${if (isNeural) " - NEURAL" else ""}")
        }
        Log.d(TAG, "=========================================")
        
        // Set up utterance progress listener
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
                Log.d(TAG, "TTS started: $utteranceId")
            }
            
            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                Log.d(TAG, "TTS completed: $utteranceId")
            }
            
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                Log.e(TAG, "TTS error: $utteranceId")
            }
        })
    }
    
    /**
     * Auto-select the best available voice (prefers Neural/Network voices)
     * Priority: Neural Network > Network > Local Neural > Local Standard
     */
    private fun autoSelectBestVoice() {
        val voices = textToSpeech?.voices ?: return
        val defaultLocale = Locale.getDefault()
        
        Log.d(TAG, "Auto-selecting best voice from ${voices.size} available voices")
        
        // Priority order for voice selection - prioritize Google's best neural voices
        // Google Neural TTS voices are the highest quality and most natural sounding
        val voicePriority = listOf(
            // Google Neural TTS voices (best quality, most natural) - highest priority
            { v: Voice -> v.name.contains("en-us-x-tpf", ignoreCase = true) }, // Premium Neural (best)
            { v: Voice -> v.name.contains("en-us-x-tpd", ignoreCase = true) }, // Deep Neural
            { v: Voice -> v.name.contains("en-us-x-tpc", ignoreCase = true) }, // Clear Neural
            { v: Voice -> v.name.contains("en-us-x-iob", ignoreCase = true) }, // Balanced Neural
            { v: Voice -> v.name.contains("en-us-x-iol", ignoreCase = true) }, // Light Neural
            { v: Voice -> v.name.contains("en-us-x-iom", ignoreCase = true) }, // Medium Neural
            { v: Voice -> v.name.contains("en-us-x-iof", ignoreCase = true) }, // Female Neural
            { v: Voice -> v.name.contains("en-us-x-kpf", ignoreCase = true) }, // Premium Female
            { v: Voice -> v.name.contains("en-us-x-sfg", ignoreCase = true) }, // Standard Neural
            // Any Google Neural voice (en-us-x-* pattern indicates neural)
            { v: Voice -> v.name.contains("en-us-x-", ignoreCase = true) && 
                         v.locale.language == defaultLocale.language },
            // Network neural voices (good quality, requires internet)
            { v: Voice -> v.name.contains("network", ignoreCase = true) && 
                         v.name.contains("neural", ignoreCase = true) &&
                         v.locale.language == defaultLocale.language },
            // Any neural voice with locale match
            { v: Voice -> v.name.contains("neural", ignoreCase = true) && 
                         v.locale.language == defaultLocale.language },
            // Network voices (good quality, requires internet)
            { v: Voice -> v.name.contains("network", ignoreCase = true) &&
                         v.locale.language == defaultLocale.language },
            // Local neural voices
            { v: Voice -> v.name.contains("neural", ignoreCase = true) && 
                         !v.name.contains("network", ignoreCase = true) &&
                         v.locale.language == defaultLocale.language },
            // Any voice matching locale (fallback)
            { v: Voice -> v.locale.language == defaultLocale.language }
        )
        
        // Find best voice - try locale match first, then any neural voice
        for (priorityCheck in voicePriority) {
            val matchingVoice = voices.find { voice ->
                priorityCheck(voice)
            }
            if (matchingVoice != null) {
                textToSpeech?.voice = matchingVoice
                Log.d(TAG, "✅ Auto-selected best voice: ${matchingVoice.name} (${matchingVoice.locale})")
                Log.d(TAG, "   Voice quality: ${if (isNeuralVoice(matchingVoice)) "NEURAL (High Quality)" else "Standard"}")
                return
            }
        }
        
        // Last resort: try any neural voice regardless of locale
        val anyNeuralVoice = voices.find { isNeuralVoice(it) }
        if (anyNeuralVoice != null) {
            textToSpeech?.voice = anyNeuralVoice
            Log.d(TAG, "✅ Selected neural voice (different locale): ${anyNeuralVoice.name} (${anyNeuralVoice.locale})")
            return
        }
        
        // Final fallback: use default locale
        textToSpeech?.language = defaultLocale
        Log.w(TAG, "⚠️ Using default locale: $defaultLocale (no preferred voice found)")
    }
    
    private fun isNeuralVoice(voice: Voice): Boolean {
        val name = voice.name.lowercase()
        return name.contains("neural", ignoreCase = true) || 
               name.contains("network", ignoreCase = true) ||
               name.contains("en-us-x-tp", ignoreCase = true) ||
               name.contains("en-us-x-io", ignoreCase = true) ||
               name.contains("en-us-x-kp", ignoreCase = true) ||
               name.contains("en-us-x-sf", ignoreCase = true)
    }
    
    /**
     * Preprocess text to make it sound more natural and engaging
     * Aggressively removes pauses and makes speech flow like natural conversation
     */
    private fun preprocessTextForSpeech(text: String, style: VoiceStyle): String {
        var processed = text.trim()
        
        // Remove excessive whitespace
        processed = processed.replace(Regex("\\s+"), " ")
        
        // For fast, engaging speech, make it flow like natural conversation
        if (style == VoiceStyle.MORNING || style == VoiceStyle.WINS || style == VoiceStyle.INSIGHTS || style == VoiceStyle.JOURNAL) {
            // Replace periods with commas for faster flow (except at end)
            processed = processed.replace(Regex("\\.\\s+([a-z])"), ", $1")
            // Remove commas before conjunctions - makes speech flow faster
            processed = processed.replace(Regex(",\\s+(and|or|but|so|then|also|plus|with|for|to|the|a|an)\\s+", RegexOption.IGNORE_CASE), " $1 ")
            // Remove commas in lists - replace with natural flow
            processed = processed.replace(Regex(",\\s*([^,]+),\\s*and\\s+"), ", $1 and ")
            // Remove bullet points and list markers that create pauses
            processed = processed.replace(Regex("^[•\\-\\*]\\s+", RegexOption.MULTILINE), "")
            // Remove colons that create pauses
            processed = processed.replace(Regex(":\\s+"), " ")
            // Remove semicolons
            processed = processed.replace(Regex(";\\s+"), ", ")
        }
        
        // Remove unnecessary pauses from numbers and units
        processed = processed.replace(Regex("(\\d+)\\s+(percent|%)", RegexOption.IGNORE_CASE), "$1$2")
        processed = processed.replace(Regex("(\\d+)\\s+(grams?|g|calories?|cal|hours?|h|minutes?|min|steps?|oz|fl oz)", RegexOption.IGNORE_CASE), "$1$2")
        
        // Remove multiple spaces
        processed = processed.replace(Regex("\\s{2,}"), " ")
        
        // Make contractions more natural
        processed = processed.replace(Regex("\\b(do not|cannot|will not|should not|would not|could not|did not|has not|have not|is not|are not|was not|were not)\\b", RegexOption.IGNORE_CASE)) { matchResult ->
            val full = matchResult.value.lowercase()
            when {
                full.contains("do not") -> "don't"
                full.contains("cannot") -> "can't"
                full.contains("will not") -> "won't"
                full.contains("should not") -> "shouldn't"
                full.contains("would not") -> "wouldn't"
                full.contains("could not") -> "couldn't"
                full.contains("did not") -> "didn't"
                full.contains("has not") -> "hasn't"
                full.contains("have not") -> "haven't"
                full.contains("is not") -> "isn't"
                full.contains("are not") -> "aren't"
                full.contains("was not") -> "wasn't"
                full.contains("were not") -> "weren't"
                else -> matchResult.value
            }
        }
        
        return processed.trim()
    }
    
    /**
     * Speak text with specified voice style
     */
    fun speak(text: String, style: VoiceStyle = VoiceStyle.MORNING) {
        if (!isInitialized || textToSpeech == null) {
            Log.w(TAG, "TTS not initialized, cannot speak")
            return
        }
        
        if (text.isBlank()) {
            Log.w(TAG, "Empty text, skipping TTS")
            return
        }
        
        // Check cache - skip if same phrase spoken recently
        val now = System.currentTimeMillis()
        val recentPhrase = recentPhrases.find { it.first == text }
        if (recentPhrase != null) {
            val timeSince = now - recentPhrase.second
            if (timeSince < cacheWindowMs) {
                Log.d(TAG, "Skipping cached phrase (spoken ${timeSince / 1000}s ago)")
                return
            }
        }
        
        // FORCE voice re-selection to ensure we're using the best neural voice
        // This ensures we always use the best available voice, even if it changed
        autoSelectBestVoice()
        
        // Preprocess text for more natural speech
        val processedText = preprocessTextForSpeech(text, style)
        
        // Apply voice style with MAXIMUM speed for natural, conversational feel
        // Higher rates make speech sound more natural and less robotic
        // Google's neural voices handle high rates much better than standard voices
        val (rate, pitch) = when (style) {
            VoiceStyle.MORNING, VoiceStyle.WINS -> 1.5f to 1.15f  // MAXIMUM speed, energetic, natural
            VoiceStyle.MINDFULNESS -> 1.2f to 1.05f  // Faster for calm but still natural
            VoiceStyle.INSIGHTS, VoiceStyle.JOURNAL -> 1.45f to 1.1f  // MAXIMUM speed, clear, conversational
        }
        
        // Override with user preferences if they've customized (but ensure minimum speed for natural speech)
        val finalRate = if (preferencesManager.voiceRate != 1.0f && preferencesManager.voiceRate > 0.5f) {
            preferencesManager.voiceRate.coerceAtLeast(1.2f) // Don't go below 1.2 (fast enough to sound natural)
        } else {
            rate
        }
        val finalPitch = if (preferencesManager.voicePitch != 1.0f && preferencesManager.voicePitch > 0.5f) {
            preferencesManager.voicePitch
        } else {
            pitch
        }
        
        // CRITICAL: Set rate and pitch BEFORE speaking, and verify they're set
        // Force re-apply settings right before speaking to ensure they take effect
        textToSpeech?.setSpeechRate(finalRate)
        textToSpeech?.setPitch(finalPitch)
        
        // Verify voice is set
        val currentVoice = textToSpeech?.voice
        val isNeural = currentVoice?.let { isNeuralVoice(it) } ?: false
        
        Log.d(TAG, "=========================================")
        Log.d(TAG, "TTS SPEAK CONFIGURATION:")
        Log.d(TAG, "  Voice: ${currentVoice?.name ?: "DEFAULT"} (${if (isNeural) "NEURAL ✅" else "STANDARD ⚠️"})")
        Log.d(TAG, "  Locale: ${currentVoice?.locale ?: "DEFAULT"}")
        Log.d(TAG, "  Rate: $finalRate (${(finalRate * 100).toInt()}% of normal)")
        Log.d(TAG, "  Pitch: $finalPitch")
        Log.d(TAG, "  Style: $style")
        Log.d(TAG, "  Text length: ${processedText.length} chars")
        Log.d(TAG, "=========================================")
        
        // Speak with processed text
        val utteranceId = "coachie_${System.currentTimeMillis()}"
        val result = textToSpeech?.speak(processedText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "❌ ERROR speaking text")
            _isSpeaking.value = false
        } else {
            // Add to cache
            recentPhrases.add(text to now)
            // Keep only last 5
            if (recentPhrases.size > 5) {
                recentPhrases.removeAt(0)
            }
            Log.d(TAG, "✅ Speaking text with style $style (rate=$finalRate, pitch=$finalPitch, voice=${currentVoice?.name ?: "default"})")
        }
    }
    
    /**
     * Stop speaking
     */
    fun stop() {
        textToSpeech?.stop()
        _isSpeaking.value = false
        Log.d(TAG, "TTS stopped")
    }
    
    /**
     * Check if currently speaking
     */
    fun isCurrentlySpeaking(): Boolean = _isSpeaking.value
    
    /**
     * Get current voice information
     */
    fun getCurrentVoice(): Voice? = textToSpeech?.voice
    
    /**
     * Check if current voice is a Neural/Network voice (better quality)
     */
    fun isUsingNeuralVoice(): Boolean {
        val voice = textToSpeech?.voice ?: return false
        val name = voice.name.lowercase()
        return name.contains("neural", ignoreCase = true) || 
               name.contains("network", ignoreCase = true) ||
               name.contains("en-us-x-tp", ignoreCase = true) || // Neural TTS pattern
               name.contains("en-us-x-io", ignoreCase = true) || // Neural TTS pattern
               name.contains("en-us-x-kp", ignoreCase = true)    // Neural TTS pattern
    }
    
    /**
     * Cleanup - call when app is closing or service no longer needed
     */
    fun cleanup() {
        stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false
        recentPhrases.clear()
        Log.d(TAG, "TTS service cleaned up")
    }
}

