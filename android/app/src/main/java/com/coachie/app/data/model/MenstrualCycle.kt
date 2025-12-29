package com.coachie.app.data.model

import com.google.firebase.firestore.PropertyName

/**
 * Data model for menstrual cycle tracking
 */
data class MenstrualCycleData(
    @PropertyName("userId")
    val userId: String,

    @PropertyName("averageCycleLength")
    val averageCycleLength: Int = 28, // days

    @PropertyName("averagePeriodLength")
    val averagePeriodLength: Int = 5, // days

    @PropertyName("lastPeriodStart")
    val lastPeriodStart: Long? = null,

    @PropertyName("nextPredictedPeriod")
    val nextPredictedPeriod: Long? = null,

    @PropertyName("isRegular")
    val isRegular: Boolean = false,

    @PropertyName("cycleHistory")
    val cycleHistory: List<CycleEntry> = emptyList()
) {
    /**
     * Calculate current cycle phase based on last period start
     */
    fun getCurrentPhase(): CyclePhase {
        val lastStart = lastPeriodStart ?: return CyclePhase.UNKNOWN

        val daysSinceLastPeriod = ((System.currentTimeMillis() - lastStart) / (1000 * 60 * 60 * 24)).toInt()

        return when {
            daysSinceLastPeriod < averagePeriodLength -> CyclePhase.MENSTRUAL
            daysSinceLastPeriod < averagePeriodLength + 7 -> CyclePhase.FOLLICULAR
            daysSinceLastPeriod < averagePeriodLength + 14 -> CyclePhase.OVULATION
            daysSinceLastPeriod < averageCycleLength -> CyclePhase.LUTEAL
            else -> CyclePhase.UNKNOWN // Cycle ended, waiting for next period
        }
    }

    /**
     * Get days until next period
     */
    fun getDaysUntilNextPeriod(): Int? {
        val nextPeriod = nextPredictedPeriod ?: return null
        val daysUntil = ((nextPeriod - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
        return if (daysUntil >= 0) daysUntil else null
    }

    /**
     * Get current day of cycle (1-based)
     */
    fun getCurrentDayOfCycle(): Int? {
        val lastStart = lastPeriodStart ?: return null
        val daysSinceStart = ((System.currentTimeMillis() - lastStart) / (1000 * 60 * 60 * 24)).toInt()
        return daysSinceStart + 1
    }
}

data class CycleEntry(
    @PropertyName("periodStart")
    val periodStart: Long,

    @PropertyName("periodEnd")
    val periodEnd: Long? = null,

    @PropertyName("cycleLength")
    val cycleLength: Int? = null,

    @PropertyName("periodLength")
    val periodLength: Int? = null,

    @PropertyName("symptoms")
    val symptoms: List<String> = emptyList(),

    @PropertyName("flowIntensity")
    val flowIntensity: FlowIntensity = FlowIntensity.MEDIUM,

    @PropertyName("notes")
    val notes: String? = null
)

enum class CyclePhase(val displayName: String, val description: String) {
    MENSTRUAL("Menstrual", "Period phase - focus on iron-rich foods and gentle exercise"),
    FOLLICULAR("Follicular", "Post-period phase - energy building, good for strength training"),
    OVULATION("Ovulation", "Fertile phase - peak energy, optimal for intense workouts"),
    LUTEAL("Luteal", "Pre-period phase - potential PMS symptoms, focus on magnesium and B vitamins"),
    UNKNOWN("Unknown", "Cycle phase unknown")
}

enum class FlowIntensity(val displayName: String, val emoji: String) {
    LIGHT("Light", "💧"),
    MEDIUM("Medium", "🌊"),
    HEAVY("Heavy", "🌊🌊"),
    SPOTTING("Spotting", "💦")
}

enum class MenstrualSymptom(val displayName: String, val category: String) {
    CRAMPS("Cramps", "Physical"),
    HEADACHE("Headache", "Physical"),
    BLOATING("Bloating", "Physical"),
    FATIGUE("Fatigue", "Physical"),
    BREAST_TENDERNESS("Breast Tenderness", "Physical"),
    BACK_PAIN("Back Pain", "Physical"),
    NAUSEA("Nausea", "Physical"),
    INSOMNIA("Insomnia", "Sleep"),
    MOOD_SWINGS("Mood Swings", "Emotional"),
    IRRITABILITY("Irritability", "Emotional"),
    ANXIETY("Anxiety", "Emotional"),
    DEPRESSION("Depression", "Emotional"),
    FOOD_CRAVINGS("Food Cravings", "Other"),
    ACNE("Acne", "Other"),
    CONSTIPATION("Constipation", "Other"),
    DIARRHEA("Diarrhea", "Other")
}
