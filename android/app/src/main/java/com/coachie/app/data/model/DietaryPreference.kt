package com.coachie.app.data.model

/**
 * Supported dietary preference categories used for tailoring AI guidance.
 */
enum class DietaryPreference(
    val id: String,
    val title: String,
    val summary: String,
    val macroGuidance: String,
    val carbsRatio: Double,
    val proteinRatio: Double,
    val fatRatio: Double,
    val proteinPerKg: String = "1.6 g/kg"
) {
    BALANCED(
        id = "balanced",
        title = "Balanced (Default)",
        summary = "General health, most users, sustainable long-term.",
        macroGuidance = "25% protein, 50% carbs, 25% fat.",
        carbsRatio = 0.50,
        proteinRatio = 0.25,
        fatRatio = 0.25,
        proteinPerKg = "1.6 g/kg"
    ),
    HIGH_PROTEIN(
        id = "high_protein",
        title = "High Protein",
        summary = "Muscle gain, satiety, weight loss, aging.",
        macroGuidance = "35% protein, 40% carbs, 25% fat.",
        carbsRatio = 0.40,
        proteinRatio = 0.35,
        fatRatio = 0.25,
        proteinPerKg = "2.0–2.4 g/kg"
    ),
    MODERATE_LOW_CARB(
        id = "moderate_low_carb",
        title = "Moderate Low-Carb",
        summary = "Blood sugar control, sustainable fat loss.",
        macroGuidance = "30% protein, 25% carbs, 45% fat.",
        carbsRatio = 0.25,
        proteinRatio = 0.30,
        fatRatio = 0.45,
        proteinPerKg = "1.8–2.2 g/kg"
    ),
    KETOGENIC(
        id = "ketogenic",
        title = "Ketogenic (Keto)",
        summary = "Therapeutic keto, epilepsy, rapid fat loss.",
        macroGuidance = "20% protein, 5% carbs, 75% fat.",
        carbsRatio = 0.05,
        proteinRatio = 0.20,
        fatRatio = 0.75,
        proteinPerKg = "1.6–2.0 g/kg"
    ),
    VERY_LOW_CARB(
        id = "very_low_carb",
        title = "Very Low-Carb (Carnivore-leaning)",
        summary = "Carnivore / keto-carnivore hybrid.",
        macroGuidance = "35% protein, 5% carbs, 60% fat.",
        carbsRatio = 0.05,
        proteinRatio = 0.35,
        fatRatio = 0.60,
        proteinPerKg = "2.2–3.0 g/kg"
    ),
    CARNIVORE(
        id = "carnivore",
        title = "Carnivore",
        summary = "Zero plants, animal foods only.",
        macroGuidance = "45% protein, 0–2% carbs, 55% fat.",
        carbsRatio = 0.01, // Average of 0-2%
        proteinRatio = 0.45,
        fatRatio = 0.55,
        proteinPerKg = "2.5–3.5 g/kg"
    ),
    MEDITERRANEAN(
        id = "mediterranean",
        title = "Mediterranean",
        summary = "Heart health, longevity, anti-inflammatory.",
        macroGuidance = "20% protein, 50% carbs, 30% fat.",
        carbsRatio = 0.50,
        proteinRatio = 0.20,
        fatRatio = 0.30,
        proteinPerKg = "1.4–1.8 g/kg"
    ),
    PLANT_BASED(
        id = "plant_based",
        title = "Plant-Based (Flexitarian)",
        summary = "Mostly plants, occasional animal foods.",
        macroGuidance = "20% protein, 55% carbs, 25% fat.",
        carbsRatio = 0.55,
        proteinRatio = 0.20,
        fatRatio = 0.25,
        proteinPerKg = "1.6–2.0 g/kg"
    ),
    VEGETARIAN(
        id = "vegetarian",
        title = "Vegetarian",
        summary = "No meat, includes dairy & eggs.",
        macroGuidance = "20% protein, 55% carbs, 25% fat.",
        carbsRatio = 0.55,
        proteinRatio = 0.20,
        fatRatio = 0.25,
        proteinPerKg = "1.6–2.0 g/kg"
    ),
    VEGAN(
        id = "vegan",
        title = "Vegan",
        summary = "100% plant-based – needs planning for protein.",
        macroGuidance = "18–22% protein, 55–60% carbs, 22–27% fat.",
        carbsRatio = 0.575, // Average of 55-60%
        proteinRatio = 0.20, // Average of 18-22%
        fatRatio = 0.245, // Average of 22-27%
        proteinPerKg = "1.8–2.4 g/kg"
    ),
    PALEO(
        id = "paleo",
        title = "Paleo",
        summary = "Whole foods, no grains/legumes/dairy.",
        macroGuidance = "30% protein, 35% carbs, 35% fat.",
        carbsRatio = 0.35,
        proteinRatio = 0.30,
        fatRatio = 0.35,
        proteinPerKg = "1.8–2.2 g/kg"
    ),
    LOW_FAT(
        id = "low_fat",
        title = "Low Fat (Classic)",
        summary = "Old-school heart disease reversal (Ornish/Pritikin).",
        macroGuidance = "20% protein, 65% carbs, 15% fat.",
        carbsRatio = 0.65,
        proteinRatio = 0.20,
        fatRatio = 0.15,
        proteinPerKg = "1.2–1.6 g/kg"
    ),
    ZONE_DIET(
        id = "zone_diet",
        title = "Zone Diet",
        summary = "40-30-30 style, anti-inflammatory.",
        macroGuidance = "30% protein, 40% carbs, 30% fat.",
        carbsRatio = 0.40,
        proteinRatio = 0.30,
        fatRatio = 0.30,
        proteinPerKg = "1.8–2.0 g/kg"
    );

    companion object {
        fun fromId(id: String?): DietaryPreference {
            if (id == null || id.isBlank()) return BALANCED
            
            // Try exact match first
            values().firstOrNull { it.id.equals(id, ignoreCase = true) }?.let { return it }
            
            // Handle backward compatibility with old IDs
            val oldIdMapping = mapOf(
                "keto_low_carb" to KETOGENIC,
                "pescatarian" to MEDITERRANEAN, // Pescatarian is similar to Mediterranean
                "whole30" to PALEO // Whole30 is similar to Paleo
            )
            
            oldIdMapping[id.lowercase()]?.let { return it }
            
            // Default to balanced if no match found
            return BALANCED
        }

        val all: List<DietaryPreference> = values().toList()
    }
}

