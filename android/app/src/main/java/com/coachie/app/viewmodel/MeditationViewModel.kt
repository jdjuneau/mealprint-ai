package com.coachie.app.viewmodel

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
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
import java.util.Locale
import java.util.concurrent.TimeUnit

class MeditationViewModel : ViewModel() {

    enum class MeditationState {
        IDLE, PREPARING, MEDITATING, COMPLETED
    }

    private val _state = MutableStateFlow(MeditationState.IDLE)
    val state: StateFlow<MeditationState> = _state.asStateFlow()

    private val _timeRemaining = MutableStateFlow(0L) // in seconds
    val timeRemaining: StateFlow<Long> = _timeRemaining.asStateFlow()

    private val _totalDuration = MutableStateFlow(0L) // in seconds
    val totalDuration: StateFlow<Long> = _totalDuration.asStateFlow()

    private val _meditationType = MutableStateFlow("guided")
    val meditationType: StateFlow<String> = _meditationType.asStateFlow()

    private val _moodBefore = MutableStateFlow<Int?>(null)
    val moodBefore: StateFlow<Int?> = _moodBefore.asStateFlow()

    private val _moodAfter = MutableStateFlow<Int?>(null)
    val moodAfter: StateFlow<Int?> = _moodAfter.asStateFlow()

    private val _stressBefore = MutableStateFlow<Int?>(null)
    val stressBefore: StateFlow<Int?> = _stressBefore.asStateFlow()

    private val _stressAfter = MutableStateFlow<Int?>(null)
    val stressAfter: StateFlow<Int?> = _stressAfter.asStateFlow()

    private var meditationJob: Job? = null
    private var vibrator: Vibrator? = null
    private var context: Context? = null
    private var textToSpeech: TextToSpeech? = null
    
    private val _currentInstruction = MutableStateFlow<String?>(null)
    val currentInstruction: StateFlow<String?> = _currentInstruction.asStateFlow()

    fun initialize(context: Context) {
        this.context = context
        vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
        
        // Initialize TextToSpeech for guided meditations
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    android.util.Log.w("MeditationViewModel", "TTS language not supported, using default")
                    textToSpeech?.setLanguage(Locale.US) // Fallback to US English
                }
                textToSpeech?.setSpeechRate(0.85f) // Slightly slower for meditation
                android.util.Log.d("MeditationViewModel", "TextToSpeech initialized successfully")
            } else {
                android.util.Log.e("MeditationViewModel", "TextToSpeech initialization failed with status: $status")
            }
        }
    }

    fun setMeditationType(type: String) {
        _meditationType.value = type
    }

    fun setMoodBefore(mood: Int) {
        _moodBefore.value = mood
    }

    fun setMoodAfter(mood: Int) {
        _moodAfter.value = mood
    }

    fun setStressBefore(stress: Int) {
        _stressBefore.value = stress
    }

    fun setStressAfter(stress: Int) {
        _stressAfter.value = stress
    }

    fun startMeditation(durationMinutes: Int) {
        val durationSeconds = TimeUnit.MINUTES.toSeconds(durationMinutes.toLong())
        _totalDuration.value = durationSeconds
        _state.value = MeditationState.PREPARING

        meditationJob?.cancel()
        meditationJob = viewModelScope.launch {
            val meditationType = _meditationType.value
            val isGuided = meditationType == "guided" || meditationType == "mindfulness" || meditationType == "body_scan"
            
            // Preparation phase with instruction (timer does NOT count down during prep)
            val prepInstructions = getPreparationInstructions(meditationType)
            if (isGuided) {
                prepInstructions.forEach { instruction ->
                    _currentInstruction.value = instruction
                    speakText(instruction)
                    delay(5000)
                }
            } else {
                // Silent - show but don't speak
                prepInstructions.forEach { instruction ->
                    _currentInstruction.value = instruction
                    delay(5000)
                }
            }
            
            // NOW start the timer and begin meditation
            _timeRemaining.value = durationSeconds
            vibrateGentle()
            _state.value = MeditationState.MEDITATING

            // Instructions at intervals based on duration (consistent for all types)
            val instructionInterval = when {
                durationMinutes <= 5 -> 45L // Every 45 seconds for short meditations (5 min = ~6-7 instructions)
                durationMinutes <= 10 -> 60L // Every minute for medium (10 min = ~10 instructions)
                durationMinutes <= 15 -> 90L // Every 90 seconds for medium-long (15 min = ~10 instructions)
                else -> 120L // Every 2 minutes for longer (20+ min)
            }
            
            // Start with initial instruction
            val initialInstruction = getInitialInstruction(meditationType)
            _currentInstruction.value = initialInstruction
            if (isGuided) {
                speakText(initialInstruction)
            }
            
            // Track when to give next instruction (in seconds from start)
            var secondsElapsed = 0L
            var nextInstructionAt = instructionInterval
            
            // Start countdown immediately
            while (_timeRemaining.value > 0) {
                delay(1000)
                _timeRemaining.value = _timeRemaining.value - 1
                secondsElapsed++
                
                // Provide instructions at intervals
                if (secondsElapsed >= nextInstructionAt && _timeRemaining.value > 15) {
                    val instructions = getMeditationInstruction(meditationType, durationMinutes, _timeRemaining.value)
                    _currentInstruction.value = instructions
                    if (isGuided) {
                        speakText(instructions)
                    }
                    nextInstructionAt = secondsElapsed + instructionInterval
                }
                
                // Gentle vibration every minute
                if (_timeRemaining.value % 60L == 0L && _timeRemaining.value > 0) {
                    vibrateGentle()
                }
            }

            // Completion
            val endingInstruction = getEndingInstruction(meditationType)
            _currentInstruction.value = endingInstruction
            if (isGuided) {
                speakText(endingInstruction)
                delay(5000)
            }
            
            _state.value = MeditationState.COMPLETED
            _currentInstruction.value = null
            vibrateGentle()
        }
    }
    
    private fun getPreparationInstructions(meditationType: String): List<String> {
        return when (meditationType) {
            "guided" -> listOf(
                "Find a comfortable position. Close your eyes and relax.",
                "Take three deep breaths. In through your nose, out through your mouth."
            )
            "mindfulness" -> listOf(
                "Find a comfortable seated position. Keep your back straight but relaxed.",
                "Take three deep breaths. Notice how your body feels in this moment."
            )
            "body_scan" -> listOf(
                "Lie down comfortably or sit with your back supported.",
                "Take three deep breaths. We'll begin by bringing awareness to your body."
            )
            else -> listOf( // silent
                "Find a comfortable position. Close your eyes and relax.",
                "Take three deep breaths. In through your nose, out through your mouth."
            )
        }
    }
    
    private fun getInitialInstruction(meditationType: String): String {
        return when (meditationType) {
            "guided" -> "Begin by focusing on your breath. Notice each inhale and exhale."
            "mindfulness" -> "Bring your full attention to this present moment. Notice what you're experiencing right now."
            "body_scan" -> "Start by bringing awareness to the top of your head. Notice any sensations there."
            else -> "Begin by focusing on your breath. Notice each inhale and exhale."
        }
    }
    
    private fun getEndingInstruction(meditationType: String): String {
        return when (meditationType) {
            "guided", "mindfulness" -> "Slowly bring your awareness back. Wiggle your fingers and toes. When you're ready, open your eyes."
            "body_scan" -> "Slowly bring your awareness back to the room. Notice how your entire body feels now. When you're ready, open your eyes."
            else -> "Slowly bring your awareness back. When you're ready, open your eyes."
        }
    }
    
    private fun getMeditationInstruction(meditationType: String, durationMinutes: Int, remainingSeconds: Long): String {
        val progress = 1.0 - (remainingSeconds.toDouble() / (_totalDuration.value.toDouble()))
        
        return when (meditationType) {
            "guided" -> getGuidedInstruction(durationMinutes, progress)
            "mindfulness" -> getMindfulnessInstruction(durationMinutes, progress)
            "body_scan" -> getBodyScanInstruction(durationMinutes, progress)
            else -> getSilentInstruction(durationMinutes, progress)
        }
    }
    
    private fun getGuidedInstruction(durationMinutes: Int, progress: Double): String {
        val earlyInstructions = when (durationMinutes) {
            5 -> listOf(
                "Continue breathing naturally. Let your body relax.",
                "Notice any tension in your body and release it.",
                "Allow your thoughts to come and go without judgment.",
                "Feel the gentle rhythm of your breath.",
                "Let each exhale carry away any stress or worry."
            )
            10 -> listOf(
                "Continue breathing naturally. Let your body relax.",
                "Notice any tension in your body and release it.",
                "Allow your thoughts to come and go without judgment.",
                "Feel the gentle rhythm of your breath.",
                "Let each exhale carry away any stress or worry.",
                "Your body knows how to relax. Trust the process."
            )
            15 -> listOf(
                "Continue breathing naturally. Let your body relax.",
                "Notice any tension in your body and release it.",
                "Allow your thoughts to come and go without judgment.",
                "Feel the gentle rhythm of your breath.",
                "Let each exhale carry away any stress or worry.",
                "Your body knows how to relax. Trust the process.",
                "Sink deeper into this moment of peace."
            )
            else -> listOf( // 20+
                "Continue breathing naturally. Let your body relax.",
                "Notice any tension in your body and release it.",
                "Allow your thoughts to come and go without judgment.",
                "Feel the gentle rhythm of your breath.",
                "Let each exhale carry away any stress or worry.",
                "Your body knows how to relax. Trust the process.",
                "Sink deeper into this moment of peace.",
                "There's nowhere else you need to be right now."
            )
        }
        
        val middleInstructions = when (durationMinutes) {
            5 -> listOf(
                "Stay present with your breath. If your mind wanders, gently return to your breathing.",
                "Feel the sensation of air entering and leaving your body.",
                "You're doing great. Just be here, in this moment.",
                "Notice how your breath naturally slows and deepens."
            )
            10 -> listOf(
                "Stay present with your breath. If your mind wanders, gently return to your breathing.",
                "Feel the sensation of air entering and leaving your body.",
                "You're doing great. Just be here, in this moment.",
                "Notice how your breath naturally slows and deepens.",
                "Each breath is a new beginning, a fresh start.",
                "Let go of any expectations. Just be."
            )
            15 -> listOf(
                "Stay present with your breath. If your mind wanders, gently return to your breathing.",
                "Feel the sensation of air entering and leaving your body.",
                "You're doing great. Just be here, in this moment.",
                "Notice how your breath naturally slows and deepens.",
                "Each breath is a new beginning, a fresh start.",
                "Let go of any expectations. Just be.",
                "Feel the stillness within you growing stronger."
            )
            else -> listOf( // 20+
                "Stay present with your breath. If your mind wanders, gently return to your breathing.",
                "Feel the sensation of air entering and leaving your body.",
                "You're doing great. Just be here, in this moment.",
                "Notice how your breath naturally slows and deepens.",
                "Each breath is a new beginning, a fresh start.",
                "Let go of any expectations. Just be.",
                "Feel the stillness within you growing stronger.",
                "Rest in the space between your thoughts."
            )
        }
        
        val lateInstructions = when (durationMinutes) {
            5 -> listOf(
                "Deepen your relaxation. Let go of any remaining tension.",
                "Your breath is your anchor. Return to it whenever you need.",
                "Notice how calm and centered you feel.",
                "Feel the peace spreading through your entire body."
            )
            10 -> listOf(
                "Deepen your relaxation. Let go of any remaining tension.",
                "Your breath is your anchor. Return to it whenever you need.",
                "Notice how calm and centered you feel.",
                "Feel the peace spreading through your entire body.",
                "You are exactly where you need to be right now.",
                "This moment of stillness is a gift to yourself."
            )
            15 -> listOf(
                "Deepen your relaxation. Let go of any remaining tension.",
                "Your breath is your anchor. Return to it whenever you need.",
                "Notice how calm and centered you feel.",
                "Feel the peace spreading through your entire body.",
                "You are exactly where you need to be right now.",
                "This moment of stillness is a gift to yourself.",
                "Rest in this deep sense of calm and presence."
            )
            else -> listOf( // 20+
                "Deepen your relaxation. Let go of any remaining tension.",
                "Your breath is your anchor. Return to it whenever you need.",
                "Notice how calm and centered you feel.",
                "Feel the peace spreading through your entire body.",
                "You are exactly where you need to be right now.",
                "This moment of stillness is a gift to yourself.",
                "Rest in this deep sense of calm and presence.",
                "Allow yourself to fully receive this peace."
            )
        }
        
        val endingInstructions = listOf(
            "We're almost done. Take a few more deep, cleansing breaths.",
            "Feel gratitude for taking this time for yourself.",
            "Notice how you feel right now. Carry this peace with you."
        )
        
        return when {
            progress < 0.2 -> earlyInstructions.random()
            progress < 0.5 -> middleInstructions.random()
            progress < 0.8 -> lateInstructions.random()
            else -> endingInstructions.random()
        }
    }
    
    private fun getMindfulnessInstruction(durationMinutes: Int, progress: Double): String {
        val earlyInstructions = when (durationMinutes) {
            5 -> listOf(
                "Notice what you're experiencing in this moment. What do you hear?",
                "Bring awareness to your body. How does it feel right now?",
                "Observe your thoughts without getting caught up in them.",
                "What sensations are present? Temperature, texture, pressure.",
                "Simply notice what is, without trying to change anything."
            )
            10 -> listOf(
                "Notice what you're experiencing in this moment. What do you hear?",
                "Bring awareness to your body. How does it feel right now?",
                "Observe your thoughts without getting caught up in them.",
                "What sensations are present? Temperature, texture, pressure.",
                "Simply notice what is, without trying to change anything.",
                "Can you notice the space between your thoughts?"
            )
            15 -> listOf(
                "Notice what you're experiencing in this moment. What do you hear?",
                "Bring awareness to your body. How does it feel right now?",
                "Observe your thoughts without getting caught up in them.",
                "What sensations are present? Temperature, texture, pressure.",
                "Simply notice what is, without trying to change anything.",
                "Can you notice the space between your thoughts?",
                "Be curious about your present-moment experience."
            )
            else -> listOf( // 20+
                "Notice what you're experiencing in this moment. What do you hear?",
                "Bring awareness to your body. How does it feel right now?",
                "Observe your thoughts without getting caught up in them.",
                "What sensations are present? Temperature, texture, pressure.",
                "Simply notice what is, without trying to change anything.",
                "Can you notice the space between your thoughts?",
                "Be curious about your present-moment experience.",
                "Notice how awareness itself feels."
            )
        }
        
        val middleInstructions = when (durationMinutes) {
            5 -> listOf(
                "If your mind wanders, gently acknowledge it and return to the present moment.",
                "Notice the quality of your awareness. Is it sharp or soft?",
                "What emotions are present? Can you observe them without judgment?",
                "Stay curious about each moment as it unfolds."
            )
            10 -> listOf(
                "If your mind wanders, gently acknowledge it and return to the present moment.",
                "Notice the quality of your awareness. Is it sharp or soft?",
                "What emotions are present? Can you observe them without judgment?",
                "Stay curious about each moment as it unfolds.",
                "Notice how awareness can rest in itself.",
                "Can you be present with whatever arises?"
            )
            15 -> listOf(
                "If your mind wanders, gently acknowledge it and return to the present moment.",
                "Notice the quality of your awareness. Is it sharp or soft?",
                "What emotions are present? Can you observe them without judgment?",
                "Stay curious about each moment as it unfolds.",
                "Notice how awareness can rest in itself.",
                "Can you be present with whatever arises?",
                "Rest in open, spacious awareness."
            )
            else -> listOf( // 20+
                "If your mind wanders, gently acknowledge it and return to the present moment.",
                "Notice the quality of your awareness. Is it sharp or soft?",
                "What emotions are present? Can you observe them without judgment?",
                "Stay curious about each moment as it unfolds.",
                "Notice how awareness can rest in itself.",
                "Can you be present with whatever arises?",
                "Rest in open, spacious awareness.",
                "Notice the awareness that is aware of everything."
            )
        }
        
        val lateInstructions = when (durationMinutes) {
            5 -> listOf(
                "Rest in this open, accepting awareness.",
                "Notice how mindfulness feels in your body and mind.",
                "You're cultivating presence, moment by moment.",
                "This awareness is always available to you."
            )
            10 -> listOf(
                "Rest in this open, accepting awareness.",
                "Notice how mindfulness feels in your body and mind.",
                "You're cultivating presence, moment by moment.",
                "This awareness is always available to you.",
                "Notice the peace that comes from simply being present.",
                "You can return to this state anytime."
            )
            15 -> listOf(
                "Rest in this open, accepting awareness.",
                "Notice how mindfulness feels in your body and mind.",
                "You're cultivating presence, moment by moment.",
                "This awareness is always available to you.",
                "Notice the peace that comes from simply being present.",
                "You can return to this state anytime.",
                "Rest deeply in this mindful awareness."
            )
            else -> listOf( // 20+
                "Rest in this open, accepting awareness.",
                "Notice how mindfulness feels in your body and mind.",
                "You're cultivating presence, moment by moment.",
                "This awareness is always available to you.",
                "Notice the peace that comes from simply being present.",
                "You can return to this state anytime.",
                "Rest deeply in this mindful awareness.",
                "Allow this presence to deepen and stabilize."
            )
        }
        
        val endingInstructions = listOf(
            "We're almost done. Notice how you feel after this practice.",
            "Take this mindful awareness with you into your day.",
            "Remember, you can return to this present-moment awareness anytime."
        )
        
        return when {
            progress < 0.2 -> earlyInstructions.random()
            progress < 0.5 -> middleInstructions.random()
            progress < 0.8 -> lateInstructions.random()
            else -> endingInstructions.random()
        }
    }
    
    private fun getBodyScanInstruction(durationMinutes: Int, progress: Double): String {
        // Body scan progresses through different body parts
        val bodyParts = listOf(
            "head", "forehead", "eyes", "jaw", "neck", "shoulders",
            "upper arms", "elbows", "forearms", "hands", "fingers",
            "chest", "upper back", "abdomen", "lower back",
            "hips", "thighs", "knees", "calves", "ankles", "feet", "toes"
        )
        
        val partIndex = ((1.0 - progress) * bodyParts.size).toInt().coerceIn(0, bodyParts.size - 1)
        val currentPart = bodyParts[partIndex]
        
        val earlyInstructions = when (durationMinutes) {
            5 -> listOf(
                "Bring your attention to your $currentPart. Notice any sensations there.",
                "Scan your $currentPart. What do you feel? Tension, warmth, coolness?",
                "Simply observe your $currentPart without trying to change anything.",
                "Notice any sensations in your $currentPart, or the absence of sensation."
            )
            10 -> listOf(
                "Bring your attention to your $currentPart. Notice any sensations there.",
                "Scan your $currentPart. What do you feel? Tension, warmth, coolness?",
                "Simply observe your $currentPart without trying to change anything.",
                "Notice any sensations in your $currentPart, or the absence of sensation.",
                "If you notice tension in your $currentPart, see if you can soften it.",
                "Allow your $currentPart to relax and release."
            )
            15 -> listOf(
                "Bring your attention to your $currentPart. Notice any sensations there.",
                "Scan your $currentPart. What do you feel? Tension, warmth, coolness?",
                "Simply observe your $currentPart without trying to change anything.",
                "Notice any sensations in your $currentPart, or the absence of sensation.",
                "If you notice tension in your $currentPart, see if you can soften it.",
                "Allow your $currentPart to relax and release.",
                "Feel the aliveness and presence in your $currentPart."
            )
            else -> listOf( // 20+
                "Bring your attention to your $currentPart. Notice any sensations there.",
                "Scan your $currentPart. What do you feel? Tension, warmth, coolness?",
                "Simply observe your $currentPart without trying to change anything.",
                "Notice any sensations in your $currentPart, or the absence of sensation.",
                "If you notice tension in your $currentPart, see if you can soften it.",
                "Allow your $currentPart to relax and release.",
                "Feel the aliveness and presence in your $currentPart.",
                "Rest your awareness gently on your $currentPart."
            )
        }
        
        val middleInstructions = when (durationMinutes) {
            5 -> listOf(
                "Now move your awareness to your $currentPart. Notice what's there.",
                "Can you feel the energy or life force in your $currentPart?",
                "Allow your $currentPart to be exactly as it is.",
                "Notice how your $currentPart feels connected to the rest of your body."
            )
            10 -> listOf(
                "Now move your awareness to your $currentPart. Notice what's there.",
                "Can you feel the energy or life force in your $currentPart?",
                "Allow your $currentPart to be exactly as it is.",
                "Notice how your $currentPart feels connected to the rest of your body.",
                "If there's any discomfort, breathe into that area.",
                "Feel the space around your $currentPart."
            )
            15 -> listOf(
                "Now move your awareness to your $currentPart. Notice what's there.",
                "Can you feel the energy or life force in your $currentPart?",
                "Allow your $currentPart to be exactly as it is.",
                "Notice how your $currentPart feels connected to the rest of your body.",
                "If there's any discomfort, breathe into that area.",
                "Feel the space around your $currentPart.",
                "Notice the subtle sensations in your $currentPart."
            )
            else -> listOf( // 20+
                "Now move your awareness to your $currentPart. Notice what's there.",
                "Can you feel the energy or life force in your $currentPart?",
                "Allow your $currentPart to be exactly as it is.",
                "Notice how your $currentPart feels connected to the rest of your body.",
                "If there's any discomfort, breathe into that area.",
                "Feel the space around your $currentPart.",
                "Notice the subtle sensations in your $currentPart.",
                "Rest in the awareness of your $currentPart."
            )
        }
        
        val lateInstructions = when (durationMinutes) {
            5 -> listOf(
                "Notice how your entire body feels now, as a whole.",
                "Feel the connection between all parts of your body.",
                "Your body is relaxed, present, and alive.",
                "Rest in this full-body awareness."
            )
            10 -> listOf(
                "Notice how your entire body feels now, as a whole.",
                "Feel the connection between all parts of your body.",
                "Your body is relaxed, present, and alive.",
                "Rest in this full-body awareness.",
                "Feel gratitude for your body and all it does for you.",
                "Notice the sense of wholeness and integration."
            )
            15 -> listOf(
                "Notice how your entire body feels now, as a whole.",
                "Feel the connection between all parts of your body.",
                "Your body is relaxed, present, and alive.",
                "Rest in this full-body awareness.",
                "Feel gratitude for your body and all it does for you.",
                "Notice the sense of wholeness and integration.",
                "Allow this deep relaxation to settle into every cell."
            )
            else -> listOf( // 20+
                "Notice how your entire body feels now, as a whole.",
                "Feel the connection between all parts of your body.",
                "Your body is relaxed, present, and alive.",
                "Rest in this full-body awareness.",
                "Feel gratitude for your body and all it does for you.",
                "Notice the sense of wholeness and integration.",
                "Allow this deep relaxation to settle into every cell.",
                "Rest in this complete, embodied presence."
            )
        }
        
        val endingInstructions = listOf(
            "We're almost done. Take a moment to feel your whole body.",
            "Notice how different your body feels after this scan.",
            "Carry this sense of relaxation and awareness with you."
        )
        
        return when {
            progress < 0.2 -> earlyInstructions.random()
            progress < 0.5 -> middleInstructions.random()
            progress < 0.8 -> lateInstructions.random()
            else -> endingInstructions.random()
        }
    }
    
    private fun getSilentInstruction(durationMinutes: Int, progress: Double): String {
        // Silent meditation - same as guided but displayed only
        return getGuidedInstruction(durationMinutes, progress)
    }
    
    private fun speakText(text: String) {
        try {
            // Ensure TTS is initialized
            if (textToSpeech == null) {
                android.util.Log.w("MeditationViewModel", "TextToSpeech not initialized, skipping speech")
                return
            }
            // Stop any current speech before starting new one
            textToSpeech?.stop()
            // Use QUEUE_FLUSH to replace any queued speech and ensure it plays immediately
            val result = textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "meditation_instruction")
            if (result == TextToSpeech.ERROR) {
                android.util.Log.e("MeditationViewModel", "Error speaking text: TTS returned ERROR")
            } else {
                android.util.Log.d("MeditationViewModel", "Speaking: $text")
            }
        } catch (e: Exception) {
            android.util.Log.e("MeditationViewModel", "Error speaking text: ${e.message}", e)
        }
    }

    fun stopMeditation() {
        meditationJob?.cancel()
        _state.value = MeditationState.IDLE
        _timeRemaining.value = 0
    }

    fun saveMeditationSession(userId: String, notes: String? = null, habitId: String? = null) {
        viewModelScope.launch {
            try {
                val durationMinutes = TimeUnit.SECONDS.toMinutes(_totalDuration.value).toInt()
                val meditationLog = HealthLog.MeditationLog(
                    durationMinutes = durationMinutes,
                    meditationType = _meditationType.value,
                    moodBefore = _moodBefore.value,
                    moodAfter = _moodAfter.value,
                    stressBefore = _stressBefore.value,
                    stressAfter = _stressAfter.value,
                    notes = notes,
                    completed = _state.value == MeditationState.COMPLETED
                )

                val repository = FirebaseRepository.getInstance()
                val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                repository.saveHealthLog(userId, today, meditationLog)
                
                // If this was started from a habit, complete the habit
                if (habitId != null && _state.value == MeditationState.COMPLETED) {
                    val habitRepository = com.coachie.app.data.HabitRepository.getInstance()
                    habitRepository.completeHabit(userId, habitId, durationMinutes, notes)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun reset() {
        _state.value = MeditationState.IDLE
        _timeRemaining.value = 0
        _totalDuration.value = 0
        _moodBefore.value = null
        _moodAfter.value = null
        _stressBefore.value = null
        _stressAfter.value = null
        meditationJob?.cancel()
    }

    private fun vibrateGentle() {
        try {
            vibrator?.let { vib ->
                // Check if vibrator has permission to vibrate
                if (vib.hasVibrator()) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vib.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        vib.vibrate(200)
                    }
                }
            }
        } catch (e: SecurityException) {
            // Vibration permission not granted - skip vibration silently
            android.util.Log.d("MeditationViewModel", "Vibration permission not available, skipping haptic feedback")
        } catch (e: Exception) {
            // Any other vibration error - skip silently
            android.util.Log.d("MeditationViewModel", "Vibration failed: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        meditationJob?.cancel()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    // Computed properties for UI
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _timeRemainingFormatted = MutableStateFlow("00:00")
    val timeRemainingFormatted: StateFlow<String> = _timeRemainingFormatted.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _isPreparing = MutableStateFlow(false)
    val isPreparing: StateFlow<Boolean> = _isPreparing.asStateFlow()

    private val _isCompleted = MutableStateFlow(false)
    val isCompleted: StateFlow<Boolean> = _isCompleted.asStateFlow()

    init {
        // Update computed properties when state changes
        viewModelScope.launch {
            combine(_state, _timeRemaining, _totalDuration) { state, remaining, total ->
                Triple(state, remaining, total)
            }.collect { (state, remaining, total) ->
                _progress.value = if (total > 0) {
                    1f - (remaining.toFloat() / total.toFloat())
                } else 0f

                val minutes = remaining / 60
                val seconds = remaining % 60
                _timeRemainingFormatted.value = String.format("%02d:%02d", minutes, seconds)

                _isActive.value = state == MeditationState.MEDITATING
                _isPreparing.value = state == MeditationState.PREPARING
                _isCompleted.value = state == MeditationState.COMPLETED
            }
        }
    }
}
