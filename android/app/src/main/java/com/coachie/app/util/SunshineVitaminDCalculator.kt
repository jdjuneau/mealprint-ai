package com.coachie.app.util

import kotlin.math.max

/**
 * Utility for estimating vitamin D (IU) produced from sunshine exposure.
 * These calculations are approximate and based on simplified heuristics.
 */
object SunshineVitaminDCalculator {

    enum class SkinType(val id: String, val label: String, val factor: Double) {
        VERY_FAIR("very_fair", "Very Fair", 1.2),
        FAIR("fair", "Fair", 1.0),
        MEDIUM("medium", "Medium", 0.8),
        DEEP("deep", "Deep", 0.6);

        companion object {
            fun fromId(id: String?): SkinType =
                values().firstOrNull { it.id == id } ?: MEDIUM
        }
    }

    enum class ExposureLevel(val id: String, val label: String, val bodyCoverageFraction: Double) {
        FACE_AND_HANDS("face_hands", "Face & Hands", 0.10),
        FACE_ARMS("face_arms", "Face & Arms", 0.25),
        FACE_ARMS_LEGS("face_arms_legs", "Face, Arms & Legs", 0.40),
        SWIMSUIT("swimsuit", "Swimsuit / Shorts", 0.65);

        companion object {
            fun fromId(id: String?): ExposureLevel =
                values().firstOrNull { it.id == id } ?: FACE_ARMS
        }
    }

    /**
     * Estimate vitamin D production in IU.
     *
     * @param minutes Minutes spent in direct sunlight
     * @param uvIndex UV index during exposure (0-11+)
     * @param exposure Exposure level indicating total skin coverage
     * @param skinType Fitzpatrick-like skin type
     */
    fun estimateVitaminD(
        minutes: Int,
        uvIndex: Double,
        exposure: ExposureLevel,
        skinType: SkinType
    ): Double {
        if (minutes <= 0 || uvIndex <= 0.0) return 0.0

        // Base production rate: 5 IU per minute per UV index at 25% body exposure for medium skin.
        val baseRatePerMinute = 5.0
        val exposureMultiplier = max(exposure.bodyCoverageFraction, 0.05)
        val skinMultiplier = skinType.factor

        val vitaminD = minutes * uvIndex * baseRatePerMinute * exposureMultiplier * skinMultiplier

        // Clamp to a reasonable daily upper bound to avoid unrealistic spikes.
        return vitaminD.coerceIn(0.0, 5000.0)
    }
}

