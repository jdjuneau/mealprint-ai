package com.coachie.app.data.model

import com.google.firebase.firestore.PropertyName

/**
 * Scan data class representing body scan data with photo and analysis.
 * Stored in Firestore at: scans/{uid}/{timestamp}
 */
data class Scan(
    @PropertyName("uid")
    val uid: String = "",

    @PropertyName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @PropertyName("photoUrl")
    val photoUrl: String? = null, // Firebase Storage URL

    @PropertyName("bodyFatEstimate")
    val bodyFatEstimate: Double? = null, // Body fat percentage estimate

    @PropertyName("weight")
    val weight: Double? = null, // Weight at time of scan (kg)

    @PropertyName("height")
    val height: Double? = null, // Height at time of scan (cm)

    @PropertyName("notes")
    val notes: String? = null, // Optional scan notes

    @PropertyName("scanType")
    val scanType: String = SCAN_TYPE_FULL_BODY, // Type of scan

    @PropertyName("processed")
    val processed: Boolean = false,

    @PropertyName("processing")
    val processing: Boolean = false, // Whether AI processing is complete

    @PropertyName("processingError")
    val processingError: String? = null, // Error message if processing failed

    // Body measurements from pose detection (in pixels, relative to image)
    @PropertyName("shoulderWidth")
    val shoulderWidth: Float? = null, // Distance between left/right shoulder landmarks

    @PropertyName("hipWidth")
    val hipWidth: Float? = null, // Distance between left/right hip landmarks

    @PropertyName("waistWidth")
    val waistWidth: Float? = null, // Estimated waist width from pose

    @PropertyName("shoulderToHipRatio")
    val shoulderToHipRatio: Float? = null, // Shoulder width / hip width ratio

    // Reference measurements for scaling (if known)
    @PropertyName("knownHeight")
    val knownHeight: Float? = null, // User's known height in cm (for scaling)

    @PropertyName("imageWidth")
    val imageWidth: Int? = null, // Original image dimensions

    @PropertyName("imageHeight")
    val imageHeight: Int? = null
) {

    /**
     * Get formatted timestamp for display
     */
    val formattedDateTime: String
        get() {
            val dateTime = java.time.Instant.ofEpochMilli(timestamp)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime()

            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm")
            return dateTime.format(formatter)
        }

    /**
     * Get just the date part
     */
    val dateString: String
        get() {
            val date = java.time.Instant.ofEpochMilli(timestamp)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()

            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
            return date.format(formatter)
        }

    /**
     * Check if scan has a photo
     */
    val hasPhoto: Boolean
        get() = !photoUrl.isNullOrBlank()

    /**
     * Check if scan has body fat analysis
     */
    val hasAnalysis: Boolean
        get() = bodyFatEstimate != null

    /**
     * Get body fat category based on estimate
     */
    val bodyFatCategory: String?
        get() = bodyFatEstimate?.let { estimate ->
            when {
                estimate < 6 -> "Essential Fat"
                estimate < 14 -> "Athletes"
                estimate < 18 -> "Fitness"
                estimate < 25 -> "Average"
                else -> "Obese"
            }
        }

    /**
     * Calculate BMI if weight and height are available
     */
    val bmi: Double?
        get() {
            return if (weight != null && height != null && height > 0) {
                weight / ((height / 100) * (height / 100))
            } else null
        }

    /**
     * Check if scan has pose detection measurements
     */
    val hasMeasurements: Boolean
        get() = shoulderWidth != null && hipWidth != null

    /**
     * Calculate estimated body fat percentage based on measurements
     * This is a simplified estimation - in production, use more sophisticated algorithms
     */
    val estimatedBodyFatPercentage: Double?
        get() {
            if (!hasMeasurements || imageHeight == null || knownHeight == null) return null

            // Simple estimation based on shoulder-to-hip ratio
            // Lower ratio typically indicates higher body fat (more hip-dominant)
            val ratio = shoulderToHipRatio ?: return null

            // Basic estimation formula (simplified for demo)
            // In reality, this would use machine learning models trained on large datasets
            val baseFat = when {
                ratio > 1.1 -> 12.0  // Athletic/muscular build
                ratio > 1.0 -> 15.0  // Average
                ratio > 0.9 -> 18.0  // Some fat accumulation
                else -> 22.0         // Higher body fat
            }

            // Adjust based on other factors (this is very simplified)
            var adjustment = 0.0
            if (waistWidth != null && hipWidth != null) {
                val waistToHipRatio = waistWidth / hipWidth
                if (waistToHipRatio > 0.85) adjustment += 2.0 // Higher waist indicates more abdominal fat
            }

            return (baseFat + adjustment).coerceIn(5.0, 40.0)
        }

    /**
     * Compare this scan with another scan and calculate body fat change
     */
    fun compareBodyFatWith(other: Scan): BodyFatComparison? {
        val thisFat = this.estimatedBodyFatPercentage ?: return null
        val otherFat = other.estimatedBodyFatPercentage ?: return null

        val change = thisFat - otherFat
        val changePercent = (change / otherFat) * 100

        return BodyFatComparison(
            currentFat = thisFat,
            previousFat = otherFat,
            change = change,
            changePercent = changePercent,
            currentScan = this,
            previousScan = other
        )
    }

    companion object {
        // Scan type constants
        const val SCAN_TYPE_FULL_BODY = "full_body"
        const val SCAN_TYPE_UPPER_BODY = "upper_body"
        const val SCAN_TYPE_LOWER_BODY = "lower_body"
        const val SCAN_TYPE_FRONT = "front"
        const val SCAN_TYPE_SIDE = "side"
        const val SCAN_TYPE_BACK = "back"

        // Body fat categories
        const val BODY_FAT_ESSENTIAL = "Essential Fat"
        const val BODY_FAT_ATHLETES = "Athletes"
        const val BODY_FAT_FITNESS = "Fitness"
        const val BODY_FAT_AVERAGE = "Average"
        const val BODY_FAT_OBESE = "Obese"

        /**
         * Create a new scan with current timestamp
         */
        fun create(uid: String, photoUrl: String? = null): Scan {
            return Scan(
                uid = uid,
                timestamp = System.currentTimeMillis(),
                photoUrl = photoUrl
            )
        }

        /**
         * Create a scan for a specific timestamp
         */
        fun createAtTime(uid: String, timestamp: Long, photoUrl: String? = null): Scan {
            return Scan(
                uid = uid,
                timestamp = timestamp,
                photoUrl = photoUrl
            )
        }
    }
}

/**
 * Data class representing a comparison between two body scans
 * Used to show body fat percentage changes over time
 */
data class BodyFatComparison(
    val currentFat: Double,      // Current body fat percentage
    val previousFat: Double,     // Previous body fat percentage
    val change: Double,          // Absolute change (current - previous)
    val changePercent: Double,   // Percentage change
    val currentScan: Scan,       // Current scan data
    val previousScan: Scan       // Previous scan data
) {
    /**
     * Get a user-friendly message about the body fat change
     */
    val changeMessage: String
        get() = when {
            changePercent > 1.0 -> "🎉 You lost ${String.format("%.1f", changePercent)}% body fat!"
            changePercent > 0.1 -> "👍 You lost ${String.format("%.1f", changePercent)}% body fat."
            changePercent > -0.1 -> "🤝 Your body fat stayed about the same."
            changePercent > -1.0 -> "📈 You gained ${String.format("%.1f", -changePercent)}% body fat."
            else -> "⚠️ You gained ${String.format("%.1f", -changePercent)}% body fat."
        }

    /**
     * Check if this represents fat loss
     */
    val isFatLoss: Boolean
        get() = changePercent < -0.1

    /**
     * Check if this represents fat gain
     */
    val isFatGain: Boolean
        get() = changePercent > 0.1

    /**
     * Check if body fat stayed relatively stable
     */
    val isStable: Boolean
        get() = changePercent in -0.1..0.1
}
