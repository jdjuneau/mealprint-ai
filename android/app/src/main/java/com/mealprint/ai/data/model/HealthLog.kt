package com.coachie.app.data.model

import com.google.firebase.firestore.PropertyName
import java.util.UUID

/**
 * Sealed class representing different types of health logs.
 * Stored in Firestore at: logs/{uid}/{date}/entries/{entryId}
 */
sealed class HealthLog {
    /**
     * Get the timestamp for this log entry
     */
    abstract val timestamp: Long
    
    /**
     * Get the type identifier for Firestore type discrimination
     */
    abstract val type: String
    
    /**
     * Meal log entry
     */
    data class MealLog(
        @PropertyName("entryId")
        val entryId: String = UUID.randomUUID().toString(),

        @PropertyName("foodName")
        val foodName: String,
        
        @PropertyName("calories")
        val calories: Int,
        
        @PropertyName("protein")
        val protein: Int, // grams
        
        @PropertyName("carbs")
        val carbs: Int, // grams
        
        @PropertyName("fat")
        val fat: Int, // grams

        @PropertyName("sugar")
        val sugar: Int = 0, // grams

        @PropertyName("addedSugar")
        val addedSugar: Int = 0, // grams

        @PropertyName("micronutrients")
        val micronutrients: Map<String, Double> = emptyMap(),
        
        @PropertyName("photoUrl")
        val photoUrl: String? = null,
        
        @PropertyName("recipeId")
        val recipeId: String? = null, // Reference to saved recipe (if meal has a recipe)
        
        @PropertyName("servingsConsumed")
        val servingsConsumed: Double? = null, // Number of servings consumed (if from recipe)
        
        @PropertyName("timestamp")
        override val timestamp: Long = System.currentTimeMillis()
    ) : HealthLog() {
        override val type: String = "meal"

        // Note: This is not serialized to Firestore, only used at runtime
        val micronutrientsTyped: Map<MicronutrientType, Double>
            get() = micronutrients.toMicronutrientTypeMap()

        companion object {
            const val TYPE = "meal"
        }
    }

    data class SupplementLog(
        @PropertyName("entryId")
        val entryId: String = UUID.randomUUID().toString(),

        @PropertyName("name")
        val name: String,

        @PropertyName("micronutrients")
        val micronutrients: Map<String, Double>,

        @PropertyName("timestamp")
        override val timestamp: Long = System.currentTimeMillis()
    ) : HealthLog() {
        override val type: String = TYPE

        // Note: This is not serialized to Firestore, only used at runtime
        val micronutrientsTyped: Map<MicronutrientType, Double>
            get() = micronutrients.toMicronutrientTypeMap()

        companion object {
            const val TYPE = "supplement"
        }
    }
    
    /**
     * Workout log entry
     */
    data class WorkoutLog(
        @PropertyName("entryId")
        val entryId: String = UUID.randomUUID().toString(),

        @PropertyName("workoutType")
        val workoutType: String, // e.g., "Running", "Weight Training", "Yoga"
        
        @PropertyName("durationMin")
        val durationMin: Int, // duration in minutes
        
        @PropertyName("caloriesBurned")
        val caloriesBurned: Int,
        
        @PropertyName("intensity")
        val intensity: String, // e.g., "Low", "Medium", "High"
        
        @PropertyName("timestamp")
        override val timestamp: Long = System.currentTimeMillis()
    ) : HealthLog() {
        override val type: String = TYPE
        
        companion object {
            const val TYPE = "workout"
        }
    }
    
    /**
     * Sleep log entry
     */
    data class SleepLog(
        @PropertyName("entryId")
        val entryId: String = UUID.randomUUID().toString(),

        @PropertyName("startTime")
        val startTime: Long, // Unix timestamp
        
        @PropertyName("endTime")
        val endTime: Long, // Unix timestamp
        
        @PropertyName("quality")
        val quality: Int, // 1-5 scale
        
        @PropertyName("timestamp")
        override val timestamp: Long = System.currentTimeMillis()
    ) : HealthLog() {
        override val type: String = TYPE
        
        /**
         * Calculate sleep duration in hours
         */
        val durationHours: Double
            get() = (endTime - startTime) / (1000.0 * 60.0 * 60.0)
        
        /**
         * Calculate sleep duration in minutes
         */
        val durationMinutes: Int
            get() = ((endTime - startTime) / (1000.0 * 60.0)).toInt()
        
        companion object {
            const val TYPE = "sleep"
            const val MIN_QUALITY = 1
            const val MAX_QUALITY = 5
        }
    }
    
    /**
     * Water intake log entry
     */
    data class WaterLog(
        @PropertyName("entryId")
        val entryId: String = UUID.randomUUID().toString(),

        @PropertyName("ml")
        val ml: Int, // milliliters
        
        @PropertyName("timestamp")
        override val timestamp: Long = System.currentTimeMillis()
    ) : HealthLog() {
        override val type: String = TYPE
        
        /**
         * Get water amount in liters
         */
        val liters: Double
            get() = ml / 1000.0
        
        companion object {
            const val TYPE = "water"
        }
    }

    /**
     * Weight log entry
     */
    data class WeightLog(
        @PropertyName("entryId")
        val entryId: String = UUID.randomUUID().toString(),

        @PropertyName("weight")
        val weight: Double, // weight in lbs or kg

        @PropertyName("unit")
        val unit: String, // "lbs" or "kg"

        @PropertyName("timestamp")
        override val timestamp: Long = System.currentTimeMillis()
    ) : HealthLog() {
        override val type: String = TYPE

        /**
         * Get weight in kg (convert if needed)
         */
        val weightKg: Double
            get() = if (unit == "lbs") weight * 0.453592 else weight

        /**
         * Get weight in lbs (convert if needed)
         */
        val weightLbs: Double
            get() = if (unit == "kg") weight / 0.453592 else weight

        companion object {
            const val TYPE = "weight"
        }
    }

    /**
     * Mood log entry with comprehensive emotional tracking
     */
    data class MoodLog(
        @PropertyName("entryId")
        val entryId: String = UUID.randomUUID().toString(),

        @PropertyName("level")
        val level: Int, // 1-5 scale

        @PropertyName("emotions")
        val emotions: List<String> = emptyList(), // Specific emotions like "anxious", "stressed", "happy", etc.

        @PropertyName("energyLevel")
        val energyLevel: Int? = null, // 1-10 scale

        @PropertyName("sleepQuality")
        val sleepQuality: Int? = null, // 1-5 scale (1=very poor, 5=excellent)

        @PropertyName("triggers")
        val triggers: List<String> = emptyList(), // What triggered this mood

        @PropertyName("stressLevel")
        val stressLevel: Int? = null, // 1-10 scale

        @PropertyName("socialInteraction")
        val socialInteraction: String? = null, // "none", "minimal", "moderate", "extensive"

        @PropertyName("physicalActivity")
        val physicalActivity: String? = null, // "none", "light", "moderate", "intense"

        @PropertyName("note")
        val note: String? = null,

        @PropertyName("timestamp")
        override val timestamp: Long = System.currentTimeMillis()
    ) : HealthLog() {
        override val type: String = TYPE

        /**
         * Get mood description based on level
         */
        val moodDescription: String
            get() = when (level) {
                1 -> "Terrible"
                2 -> "Bad"
                3 -> "Okay"
                4 -> "Good"
                5 -> "Great"
                else -> "Unknown"
            }

        /**
         * Get energy level description
         */
        val energyDescription: String
            get() = energyLevel?.let {
                when (it) {
                    in 1..3 -> "Low Energy"
                    in 4..7 -> "Moderate Energy"
                    in 8..10 -> "High Energy"
                    else -> "Unknown"
                }
            } ?: "Not specified"

        /**
         * Get stress level description
         */
        val stressDescription: String
            get() = stressLevel?.let {
                when (it) {
                    in 1..3 -> "Low Stress"
                    in 4..7 -> "Moderate Stress"
                    in 8..10 -> "High Stress"
                    else -> "Unknown"
                }
            } ?: "Not specified"

        companion object {
            const val TYPE = "mood"
            const val MIN_LEVEL = 1
            const val MAX_LEVEL = 5
            const val MIN_ENERGY = 1
            const val MAX_ENERGY = 10
            const val MIN_STRESS = 1
            const val MAX_STRESS = 10
            const val MIN_SLEEP = 1
            const val MAX_SLEEP = 5
        }
    }

    /**
     * Meditation session log entry
     */
    data class MeditationLog(
        @PropertyName("entryId")
        val entryId: String = UUID.randomUUID().toString(),

        @PropertyName("durationMinutes")
        val durationMinutes: Int,

        @PropertyName("meditationType")
        val meditationType: String = "guided", // "guided", "silent", "walking", "body_scan", etc.

        @PropertyName("moodBefore")
        val moodBefore: Int? = null, // 1-10 scale

        @PropertyName("moodAfter")
        val moodAfter: Int? = null, // 1-10 scale

        @PropertyName("stressBefore")
        val stressBefore: Int? = null, // 1-10 scale

        @PropertyName("stressAfter")
        val stressAfter: Int? = null, // 1-10 scale

        @PropertyName("notes")
        val notes: String? = null,

        @PropertyName("completed")
        val completed: Boolean = true,

        @PropertyName("timestamp")
        override val timestamp: Long = System.currentTimeMillis()
    ) : HealthLog() {
        override val type: String = TYPE

        /**
         * Get meditation type display name
         */
        val meditationTypeDisplay: String
            get() = when (meditationType) {
                "guided" -> "Guided Meditation"
                "silent" -> "Silent Meditation"
                "walking" -> "Walking Meditation"
                "body_scan" -> "Body Scan"
                "loving_kindness" -> "Loving Kindness"
                "transcendental" -> "Transcendental"
                "mindfulness" -> "Mindfulness"
                else -> meditationType.replaceFirstChar { it.uppercase() }
            }

        companion object {
            const val TYPE = "meditation"
        }
    }

    /**
     * Sunshine exposure log entry (to estimate vitamin D production)
     */
    data class SunshineLog(
        @PropertyName("entryId")
        val entryId: String = UUID.randomUUID().toString(),

        @PropertyName("minutes")
        val minutes: Int,

        @PropertyName("uvIndex")
        val uvIndex: Double,

        @PropertyName("bodyCoverage")
        val bodyCoverage: Double,

        @PropertyName("skinType")
        val skinType: String,

        @PropertyName("vitaminDIu")
        val vitaminDIu: Double,

        @PropertyName("timestamp")
        override val timestamp: Long = System.currentTimeMillis()
    ) : HealthLog() {
        override val type: String = TYPE

        companion object {
            const val TYPE = "sunshine"
        }
    }

    /**
     * Menstrual cycle log entry
     */
    data class MenstrualLog(
        @PropertyName("entryId")
        val entryId: String = UUID.randomUUID().toString(),

        @PropertyName("isPeriodStart")
        val isPeriodStart: Boolean = false,

        @PropertyName("isPeriodEnd")
        val isPeriodEnd: Boolean = false,

        @PropertyName("flowIntensity")
        val flowIntensity: String? = null, // FlowIntensity enum name

        @PropertyName("symptoms")
        val symptoms: List<String> = emptyList(), // MenstrualSymptom enum names

        @PropertyName("painLevel")
        val painLevel: Int? = null, // 1-10 scale

        @PropertyName("notes")
        val notes: String? = null,

        @PropertyName("timestamp")
        override val timestamp: Long = System.currentTimeMillis()
    ) : HealthLog() {
        override val type: String = TYPE

        companion object {
            const val TYPE = "menstrual"
        }
    }

    /**
     * Mindful session entry for daily 3-minute audio reset
     */
    data class MindfulSession(
        @PropertyName("sessionId")
        val sessionId: String,

        @PropertyName("title")
        val title: String,

        @PropertyName("transcript")
        val transcript: String,

        @PropertyName("audioUrl")
        val audioUrl: String? = null,

        @PropertyName("durationSeconds")
        val durationSeconds: Int,

        @PropertyName("generatedDate")
        val generatedDate: String, // YYYY-MM-DD format

        @PropertyName("personalizedPrompt")
        val personalizedPrompt: String,

        @PropertyName("isFavorite")
        val isFavorite: Boolean = false,

        @PropertyName("playedCount")
        val playedCount: Int = 0,

        @PropertyName("lastPlayedAt")
        val lastPlayedAt: Long? = null,

        @PropertyName("notes")
        val notes: String? = null,

        @PropertyName("timestamp")
        override val timestamp: Long = System.currentTimeMillis()
    ) : HealthLog() {
        override val type: String = TYPE

        companion object {
            const val TYPE = "mindful_session"
        }
    }

    /**
     * Journal entry for evening reflection conversations
     */
    data class JournalEntry(
        @PropertyName("entryId")
        val entryId: String,

        @PropertyName("date")
        val date: String, // YYYY-MM-DD format

        @PropertyName("prompts")
        val prompts: List<String>, // The 3 AI-generated prompts for the day

        @PropertyName("conversation")
        val conversation: List<ChatMessage>, // Full conversation history

        @PropertyName("startedAt")
        val startedAt: Long,

        @PropertyName("completedAt")
        val completedAt: Long? = null,

        @PropertyName("wordCount")
        val wordCount: Int = 0,

        @PropertyName("mood")
        val mood: String? = null, // User's mood during journaling

        @PropertyName("insights")
        val insights: List<String> = emptyList(), // AI-generated insights from the session

        @PropertyName("isCompleted")
        val isCompleted: Boolean = false,

        @PropertyName("notes")
        val notes: String? = null,

        @PropertyName("timestamp")
        override val timestamp: Long = System.currentTimeMillis()
    ) : HealthLog() {
        override val type: String = TYPE

        companion object {
            const val TYPE = "journal"
        }
    }

    /**
     * Chat message within a journal conversation
     */
    data class ChatMessage(
        @PropertyName("id")
        val id: String,

        @PropertyName("role")
        val role: String, // "user" or "coachie"

        @PropertyName("content")
        val content: String,

        @PropertyName("timestamp")
        val timestamp: Long,

        @PropertyName("isAudio")
        val isAudio: Boolean = false,

        @PropertyName("audioUrl")
        val audioUrl: String? = null
    )

    /**
     * Breathing exercise log entry
     */
    data class BreathingExerciseLog(
        @PropertyName("entryId")
        val entryId: String = UUID.randomUUID().toString(),

        @PropertyName("durationSeconds")
        val durationSeconds: Int,

        @PropertyName("exerciseType")
        val exerciseType: String = "box_breathing", // "box_breathing", "quick_calm", "gentle", "deep_focus"

        @PropertyName("stressLevelBefore")
        val stressLevelBefore: Int? = null, // 1-10 scale

        @PropertyName("stressLevelAfter")
        val stressLevelAfter: Int? = null, // 1-10 scale

        @PropertyName("timestamp")
        override val timestamp: Long = System.currentTimeMillis()
    ) : HealthLog() {
        override val type: String = TYPE

        /**
         * Get duration in minutes
         */
        val durationMinutes: Int
            get() = durationSeconds / 60

        companion object {
            const val TYPE = "breathing_exercise"
        }
    }

    /**
     * Extracted wins and gratitudes from journal sessions
     */
    data class WinEntry(
        @PropertyName("entryId")
        val entryId: String,

        @PropertyName("journalEntryId")
        val journalEntryId: String, // Reference to the journal entry this was extracted from

        @PropertyName("date")
        val date: String, // YYYY-MM-DD format

        @PropertyName("win")
        val win: String?, // One win extracted from the journal

        @PropertyName("gratitude")
        val gratitude: String?, // One gratitude extracted from the journal

        @PropertyName("mood")
        val mood: String?, // User's mood during journaling

        @PropertyName("moodScore")
        val moodScore: Int?, // 1-5 mood score

        @PropertyName("tags")
        val tags: List<String> = emptyList(), // Auto-generated tags like "health", "relationships", "work"

        @PropertyName("timestamp")
        override val timestamp: Long = System.currentTimeMillis()
    ) : HealthLog() {
        override val type: String = TYPE

        companion object {
            const val TYPE = "win_entry"
        }
    }
}
