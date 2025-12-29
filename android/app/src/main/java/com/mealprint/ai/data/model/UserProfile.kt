package com.coachie.app.data.model

import com.mealprint.ai.BuildConfig
import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * User profile data class representing a fitness user's complete profile information.
 * Used for storing and managing user data across the Coachie fitness app.
 *
 * Firestore requires a no-argument constructor, so we provide default values for each field
 * and use mutable properties (var) so that documents can be deserialized properly.
 */
@IgnoreExtraProperties
data class UserProfile(
    var uid: String = "",
    var name: String = "",
    var username: String? = null, // Unique username for search and @mentions
    var currentWeight: Double = 0.0,
    var goalWeight: Double = 0.0,
    var heightCm: Double = 0.0,
    var activityLevel: String = "",
    var startDate: Long = 0L,
    var nudgesEnabled: Boolean = true, // Enable nudges by default
    var fcmToken: String? = null, // FCM token for push notifications
    var dietaryPreference: String = DietaryPreference.BALANCED.id,
    var age: Int = 30,
    var isFirstTimeUser: Boolean = true,
    var gender: String = "", // "male", "female", "other"
    var menstrualCycleEnabled: Boolean = false, // Whether menstrual tracking is enabled
    var averageCycleLength: Int = 28,
    var averagePeriodLength: Int = 5,
    var lastPeriodStart: Long? = null,
    var mealsPerDay: Int = 3, // Number of meals per day (default 3)
    var snacksPerDay: Int = 2, // Number of snacks per day (default 2)
    var notifications: Map<String, Boolean>? = null, // { mealPlan: boolean, mealReminders: boolean }
    var mealTimes: Map<String, String>? = null, // { breakfast: "07:30", lunch: "12:30", dinner: "18:30", snack1: "10:00", snack2: "15:30" }
    var fcmTokens: List<String>? = null, // Array of FCM tokens for push notifications
    var subscription: SubscriptionInfo? = null, // Subscription information
    var ftueCompleted: Boolean = false, // Whether the First Time User Experience has been completed
    var preferredCookingMethods: List<String>? = null, // Preferred cooking methods for weekly blueprints (e.g., ["bbq", "sous_vide", "pressure_cook"])
    var platform: String? = null, // Platform tracking: "android" or "web"
    var platforms: List<String>? = null // Array of platforms user has used (for multi-platform tracking)
) {

    /**
     * Calculate BMI (Body Mass Index) using weight in kg and height in cm
     * Formula: BMI = weight (kg) / [height (m)]²
     */
    val bmi: Double
        get() = if (heightCm <= 0.0) {
            0.0
        } else {
            currentWeight / ((heightCm / 100) * (heightCm / 100))
        }

    /**
     * Calculate weight difference between current and goal weight
     */
    val weightDifference: Double
        get() = goalWeight - currentWeight

    /**
     * Check if user has reached their goal weight (within 0.1kg tolerance)
     */
    val hasReachedGoal: Boolean
        get() = kotlin.math.abs(weightDifference) < 0.1

    /**
     * Get BMI category based on standard BMI ranges
     */
    val bmiCategory: String
        get() = when {
            bmi < 18.5 -> "Underweight"
            bmi < 25.0 -> "Normal"
            bmi < 30.0 -> "Overweight"
            else -> "Obese"
        }

    /**
     * Resolve the strongly typed dietary preference for this profile.
     */
    val dietaryPreferenceEnum: DietaryPreference
        get() = DietaryPreference.fromId(dietaryPreference)

    /**
     * Calculate estimated daily calorie needs using Mifflin-St Jeor BMR formula
     * This provides much more accurate calorie estimates
     */
    val estimatedDailyCalories: Int
        get() {
            if (currentWeight <= 0 || heightCm <= 0) return 0

            // Convert weight to kg and height to cm (already in correct units)
            val weightKg = currentWeight
            val heightCm = heightCm

            val effectiveAge = age.takeIf { it in 13..100 } ?: 30

            // Mifflin-St Jeor BMR formula
            // BMR = 10 * weight(kg) + 6.25 * height(cm) - 5 * age + s
            // where s = +5 for males, -161 for females
            val genderFactor = when (gender.lowercase()) {
                "female", "f", "woman" -> -161
                "male", "m", "man" -> 5
                else -> 5 // Default to male if gender not set
            }
            val bmr = 10 * weightKg + 6.25 * heightCm - 5 * effectiveAge + genderFactor

            // Calculate maintenance calories using activity multipliers
            // These multipliers account for daily activity beyond BMR
            val maintenance = when (activityLevel.lowercase()) {
                "sedentary" -> (bmr * 1.2).toInt()       // Little/no exercise
                "lightly active" -> (bmr * 1.375).toInt() // Light exercise 1-3 days/week
                "moderately active" -> (bmr * 1.55).toInt() // Moderate exercise 3-5 days/week
                "very active" -> (bmr * 1.725).toInt()   // Hard exercise 6-7 days/week
                "extremely active" -> (bmr * 1.9).toInt() // Very hard exercise, physical job
                else -> (bmr * 1.375).toInt() // Default to lightly active
            }
            
            // CRITICAL: Cap maximum reasonable calorie goal to prevent unrealistic values
            // Most people should not exceed 3500 calories unless they're professional athletes
            val maxReasonableCalories = 3500

            // Calculate calorie goal based on weight goal
            // Adjustments are based on healthy, sustainable rates:
            // - Weight loss: 1-1.5 lbs/week (500-750 cal deficit)
            // - Weight gain: 0.5-1 lb/week (250-500 cal surplus)
            // - Maintenance: no adjustment
            val goalCalories = when {
                // WEIGHT LOSS: goalWeight is less than currentWeight
                // Create a calorie deficit to promote fat loss
                goalWeight < currentWeight - 0.5 -> {
                    val weightToLose = currentWeight - goalWeight
                    // Progressive deficit based on amount to lose:
                    // - <5kg (11 lbs): 500 cal deficit = ~1 lb/week (standard, sustainable)
                    // - 5-10kg (11-22 lbs): 600 cal deficit = ~1.2 lb/week (moderate)
                    // - >10kg (22+ lbs): 750 cal deficit = ~1.5 lb/week (aggressive, max recommended)
                    val deficit = when {
                        weightToLose > 10 -> 750  // >22 lbs to lose: aggressive deficit
                        weightToLose >= 5 -> 600  // 11-22 lbs to lose: moderate deficit
                        else -> 500               // <11 lbs to lose: standard deficit
                    }
                    // Ensure minimum safe calorie intake (1200 for most adults)
                    // This prevents dangerously low calorie diets
                    (maintenance - deficit).coerceAtLeast(1200)
                }
                
                // WEIGHT GAIN: goalWeight is greater than currentWeight
                // Create a calorie surplus to promote muscle/weight gain
                goalWeight > currentWeight + 0.5 -> {
                    val weightToGain = goalWeight - currentWeight
                    // Progressive surplus based on amount to gain:
                    // - <5kg (11 lbs): 300 cal surplus = ~0.6 lb/week (lean gain, minimal fat)
                    // - 5-10kg (11-22 lbs): 400 cal surplus = ~0.8 lb/week (moderate gain)
                    // - >10kg (22+ lbs): 500 cal surplus = ~1 lb/week (faster gain, more fat)
                    val surplus = when {
                        weightToGain > 10 -> 500  // >22 lbs to gain: larger surplus
                        weightToGain >= 5 -> 400  // 11-22 lbs to gain: moderate surplus
                        else -> 300               // <11 lbs to gain: lean surplus
                    }
                    (maintenance + surplus).coerceAtMost(maxReasonableCalories)
                }
                
                // MAINTENANCE: goalWeight is within 0.5kg of currentWeight
                // No calorie adjustment needed - maintain current weight
                else -> maintenance.coerceAtMost(maxReasonableCalories)
            }

            return goalCalories
        }

    /**
     * Get user progress as a percentage (0.0 to 1.0)
     * Positive values mean weight loss progress, negative means weight gain
     */
    val progressPercentage: Double
        get() {
            val totalWeightToChange = kotlin.math.abs(goalWeight - currentWeight)
            return if (totalWeightToChange > 0) {
                val weightChanged = if (goalWeight < currentWeight) {
                    currentWeight - goalWeight
                } else {
                    goalWeight - currentWeight
                }
                (weightChanged / totalWeightToChange).coerceIn(0.0, 1.0)
            } else {
                1.0 // Already at goal
            }
        }

    /**
     * Check if menstrual tracking should be available for this user
     */
    val shouldShowMenstrualTracker: Boolean
        get() = gender.lowercase() == "female" // TEMP: Only show for females, removed debug mode

    /**
     * Get current cycle phase if menstrual tracking is enabled
     */
    val currentCyclePhase: com.coachie.app.data.model.CyclePhase
        get() = if (menstrualCycleEnabled && lastPeriodStart != null) {
            val daysSinceLastPeriod = ((System.currentTimeMillis() - (lastPeriodStart ?: 0)) / (1000 * 60 * 60 * 24)).toInt()
            when {
                daysSinceLastPeriod < averagePeriodLength -> com.coachie.app.data.model.CyclePhase.MENSTRUAL
                daysSinceLastPeriod < averagePeriodLength + 7 -> com.coachie.app.data.model.CyclePhase.FOLLICULAR
                daysSinceLastPeriod < averagePeriodLength + 14 -> com.coachie.app.data.model.CyclePhase.OVULATION
                daysSinceLastPeriod < averageCycleLength -> com.coachie.app.data.model.CyclePhase.LUTEAL
                else -> com.coachie.app.data.model.CyclePhase.UNKNOWN
            }
        } else {
            com.coachie.app.data.model.CyclePhase.UNKNOWN
        }

    companion object {
        // Activity level constants
        const val ACTIVITY_SEDENTARY = "sedentary"
        const val ACTIVITY_LIGHTLY_ACTIVE = "lightly active"
        const val ACTIVITY_MODERATELY_ACTIVE = "moderately active"
        const val ACTIVITY_VERY_ACTIVE = "very active"
        const val ACTIVITY_EXTREMELY_ACTIVE = "extremely active"

        // BMI category constants
        const val BMI_UNDERWEIGHT = "Underweight"
        const val BMI_NORMAL = "Normal"
        const val BMI_OVERWEIGHT = "Overweight"
        const val BMI_OBESE = "Obese"

        /**
         * Create a UserProfile with validation
         */
        fun create(
            uid: String,
            name: String,
            currentWeight: Double,
            goalWeight: Double,
            heightCm: Double,
            activityLevel: String,
            startDate: Long = System.currentTimeMillis(),
            nudgesEnabled: Boolean = true,
            fcmToken: String? = null,
            dietaryPreference: DietaryPreference = DietaryPreference.BALANCED,
            age: Int = 30,
            isFirstTimeUser: Boolean = true,
            gender: String = "",
            menstrualCycleEnabled: Boolean = false,
            averageCycleLength: Int = 28,
            averagePeriodLength: Int = 5,
            lastPeriodStart: Long? = null,
            mealsPerDay: Int = 3,
            snacksPerDay: Int = 2,
            notifications: Map<String, Boolean>? = null,
            mealTimes: Map<String, String>? = null,
            fcmTokens: List<String>? = null,
            ftueCompleted: Boolean = false
        ): Result<UserProfile> {
            return try {
                validateInputs(name, currentWeight, goalWeight, heightCm, activityLevel, age)
                Result.success(
                    UserProfile(
                        uid = uid,
                        name = name.trim(),
                        currentWeight = currentWeight,
                        goalWeight = goalWeight,
                        heightCm = heightCm,
                        activityLevel = activityLevel.lowercase(),
                        startDate = startDate,
                        nudgesEnabled = nudgesEnabled,
                        fcmToken = fcmToken,
                        dietaryPreference = dietaryPreference.id,
                        age = age,
                        isFirstTimeUser = isFirstTimeUser,
                        gender = gender,
                        menstrualCycleEnabled = menstrualCycleEnabled,
                        averageCycleLength = averageCycleLength,
                        averagePeriodLength = averagePeriodLength,
                        lastPeriodStart = lastPeriodStart,
                        mealsPerDay = mealsPerDay,
                        snacksPerDay = snacksPerDay,
                        notifications = notifications,
                        mealTimes = mealTimes,
                        fcmTokens = fcmTokens,
                        ftueCompleted = ftueCompleted
                    )
                )
            } catch (e: IllegalArgumentException) {
                Result.failure(e)
            }
        }

        /**
         * Validate user inputs before creating profile
         */
        private fun validateInputs(
            name: String,
            currentWeight: Double,
            goalWeight: Double,
            heightCm: Double,
            activityLevel: String,
            age: Int
        ) {
            require(name.isNotBlank() && name.trim().length >= 2) {
                "Name must be at least 2 characters long"
            }
            require(currentWeight > 0 && currentWeight < 500) {
                "Current weight must be between 0 and 500 kg"
            }
            require(goalWeight > 0 && goalWeight < 500) {
                "Goal weight must be between 0 and 500 kg"
            }
            require(heightCm > 0 && heightCm < 300) {
                "Height must be between 0 and 300 cm"
            }
            require(activityLevel.isNotBlank()) {
                "Activity level cannot be empty"
            }
            require(age in 13..100) {
                "Age must be between 13 and 100"
            }
        }
    }
}
