package com.coachie.app.util

import com.coachie.app.data.model.MicronutrientType
import android.util.Log
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Parses natural language voice commands for logging various health activities
 */
class VoiceCommandParser {

    private val TAG = "VoiceCommandParser"

    data class ParsedMealCommand(
        val mealType: String? = null, // breakfast, lunch, dinner, snack
        val foods: List<FoodItem> = emptyList(),
        val totalCalories: Int? = null
    )

    data class ParsedSupplementCommand(
        val supplementName: String,
        val micronutrients: Map<MicronutrientType, Double> = emptyMap(),
        val quantity: String? = null // e.g., "2000 IU", "500 mg"
    )

    data class ParsedWorkoutCommand(
        val workoutType: String,
        val durationMinutes: Int? = null,
        val distance: Double? = null,
        val distanceUnit: String? = null, // miles, km, meters
        val caloriesBurned: Int? = null
    )

    data class ParsedWaterCommand(
        val amount: Int, // in ml
        val unit: String // ml, ounces, cups, liters
    )

    data class ParsedWeightCommand(
        val weight: Double,
        val unit: String // kg, lbs, pounds
    )

    data class ParsedSleepCommand(
        val hours: Double? = null,
        val quality: String? = null // "poor", "fair", "good", "excellent"
    )

    data class ParsedMoodCommand(
        val level: Int, // 1-5 scale
        val emotions: List<String> = emptyList()
    )

    data class ParsedMeditationCommand(
        val durationMinutes: Int,
        val meditationType: String = "guided"
    )

    data class ParsedHabitCommand(
        val habitName: String, // Name/title of the habit to complete
        val notes: String? = null // Optional notes about the completion
    )

    data class ParsedJournalCommand(
        val content: String, // Journal entry content
        val mood: String? = null // Optional mood mentioned in the entry
    )

    data class FoodItem(
        val name: String,
        val quantity: String? = null, // e.g., "2", "1 cup"
        val unit: String? = null
    )

    /**
     * Main entry point - determines command type and parses accordingly
     */
    fun parseCommand(command: String): VoiceCommandResult {
        val lowerCommand = command.lowercase().trim()

        return when {
            // Water logging - CHECK FIRST before meal logging to avoid conflicts
            lowerCommand.contains("water") ||
            (lowerCommand.contains("drink") && !lowerCommand.contains("meal") && !lowerCommand.contains("eat")) -> parseWaterCommand(command)

            // Weight logging
            lowerCommand.contains("weight") ||
            lowerCommand.contains("weigh") -> parseWeightCommand(command)

            // Sleep logging
            lowerCommand.contains("sleep") ||
            lowerCommand.contains("slept") ||
            (lowerCommand.contains("bed") && lowerCommand.contains("hours")) -> parseSleepCommand(command)

            // Mood logging
            lowerCommand.contains("mood") ||
            lowerCommand.contains("feeling") ||
            (lowerCommand.contains("feel") && (lowerCommand.contains("happy") || lowerCommand.contains("sad") || lowerCommand.contains("angry") || lowerCommand.contains("anxious") || lowerCommand.contains("stressed"))) -> parseMoodCommand(command)

            // Meditation logging
            lowerCommand.contains("meditation") ||
            lowerCommand.contains("meditate") ||
            lowerCommand.contains("mindfulness") -> parseMeditationCommand(command)

            // Habit completion
            lowerCommand.contains("complete") && (
                lowerCommand.contains("habit") ||
                lowerCommand.contains("task") ||
                lowerCommand.contains("done")
            ) -> parseHabitCommand(command)

            // Journal entry
            lowerCommand.contains("journal") ||
            lowerCommand.contains("journaling") ||
            (lowerCommand.contains("write") && lowerCommand.contains("about")) ||
            (lowerCommand.contains("log") && lowerCommand.contains("thought")) -> parseJournalCommand(command)

            // Supplement logging
            lowerCommand.contains("supplement") ||
            lowerCommand.contains("vitamin") ||
            lowerCommand.contains("mineral") ||
            lowerCommand.contains("add") && (
                lowerCommand.contains("pill") ||
                lowerCommand.contains("capsule") ||
                lowerCommand.contains("tablet")
            ) -> parseSupplementCommand(command)

            // Workout logging
            lowerCommand.contains("workout") ||
            lowerCommand.contains("exercise") ||
            lowerCommand.contains("run") ||
            lowerCommand.contains("walk") ||
            lowerCommand.contains("bike") ||
            lowerCommand.contains("swim") ||
            lowerCommand.contains("lift") ||
            lowerCommand.contains("gym") -> parseWorkoutCommand(command)

            // Meal logging - CHECK LAST and require explicit meal keywords
            lowerCommand.contains("log") && (
                lowerCommand.contains("breakfast") ||
                lowerCommand.contains("lunch") ||
                lowerCommand.contains("dinner") ||
                lowerCommand.contains("snack") ||
                lowerCommand.contains("meal") ||
                lowerCommand.contains("eat") ||
                lowerCommand.contains("ate") ||
                lowerCommand.contains("food")
            ) -> parseMealCommand(command)

            else -> VoiceCommandResult.Unknown(command)
        }
    }

    private fun parseMealCommand(command: String): VoiceCommandResult {
        try {
            // Extract meal type
            val mealType = extractMealType(command)

            // Extract food items - look for patterns like "2 eggs, 1 cup rice, apple"
            val foodItems = extractFoodItems(command)

            // Try to estimate calories (basic estimation)
            val estimatedCalories = estimateCalories(foodItems)

            val parsedMeal = ParsedMealCommand(
                mealType = mealType,
                foods = foodItems,
                totalCalories = estimatedCalories
            )

            return VoiceCommandResult.MealCommand(parsedMeal)

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing meal command: $command", e)
            return VoiceCommandResult.ParseError(command, "Could not understand meal description")
        }
    }

    private fun parseSupplementCommand(command: String): VoiceCommandResult {
        try {
            // Extract supplement name and details
            val supplementName = extractSupplementName(command)
            val quantity = extractQuantity(command)

            // Try to map to known micronutrients
            val micronutrients = mapToMicronutrients(supplementName, quantity)

            val parsedSupplement = ParsedSupplementCommand(
                supplementName = supplementName,
                micronutrients = micronutrients,
                quantity = quantity
            )

            return VoiceCommandResult.SupplementCommand(parsedSupplement)

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing supplement command: $command", e)
            return VoiceCommandResult.ParseError(command, "Could not understand supplement details")
        }
    }

    private fun parseWorkoutCommand(command: String): VoiceCommandResult {
        try {
            val workoutType = extractWorkoutType(command)
            val duration = extractDuration(command)
            val distance = extractDistance(command)
            val calories = extractCaloriesBurned(command)

            val parsedWorkout = ParsedWorkoutCommand(
                workoutType = workoutType,
                durationMinutes = duration,
                distance = distance?.first,
                distanceUnit = distance?.second,
                caloriesBurned = calories
            )

            return VoiceCommandResult.WorkoutCommand(parsedWorkout)

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing workout command: $command", e)
            return VoiceCommandResult.ParseError(command, "Could not understand workout details")
        }
    }

    private fun parseWaterCommand(command: String): VoiceCommandResult {
        try {
            val (amount, unit) = extractWaterAmount(command)

            // Convert to ml
            val amountInMl = convertToMl(amount, unit)

            val parsedWater = ParsedWaterCommand(
                amount = amountInMl,
                unit = unit
            )

            return VoiceCommandResult.WaterCommand(parsedWater)

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing water command: $command", e)
            return VoiceCommandResult.ParseError(command, "Could not understand water amount")
        }
    }

    private fun parseWeightCommand(command: String): VoiceCommandResult {
        try {
            val (weight, unit) = extractWeight(command)

            val parsedWeight = ParsedWeightCommand(
                weight = weight,
                unit = unit
            )

            return VoiceCommandResult.WeightCommand(parsedWeight)

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing weight command: $command", e)
            return VoiceCommandResult.ParseError(command, "Could not understand weight measurement")
        }
    }

    private fun parseSleepCommand(command: String): VoiceCommandResult {
        try {
            val hours = extractSleepHours(command)
            val quality = extractSleepQuality(command)

            val parsedSleep = ParsedSleepCommand(
                hours = hours,
                quality = quality
            )

            return VoiceCommandResult.SleepCommand(parsedSleep)

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing sleep command: $command", e)
            return VoiceCommandResult.ParseError(command, "Could not understand sleep details")
        }
    }

    private fun parseMoodCommand(command: String): VoiceCommandResult {
        try {
            val level = extractMoodLevel(command)
            val emotions = extractEmotions(command)

            val parsedMood = ParsedMoodCommand(
                level = level,
                emotions = emotions
            )

            return VoiceCommandResult.MoodCommand(parsedMood)

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing mood command: $command", e)
            return VoiceCommandResult.ParseError(command, "Could not understand mood")
        }
    }

    private fun parseMeditationCommand(command: String): VoiceCommandResult {
        try {
            val duration = extractDuration(command) ?: 10 // Default to 10 minutes
            val meditationType = extractMeditationType(command)

            val parsedMeditation = ParsedMeditationCommand(
                durationMinutes = duration,
                meditationType = meditationType
            )

            return VoiceCommandResult.MeditationCommand(parsedMeditation)

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing meditation command: $command", e)
            return VoiceCommandResult.ParseError(command, "Could not understand meditation details")
        }
    }

    private fun parseHabitCommand(command: String): VoiceCommandResult {
        try {
            // Extract habit name - look for patterns like "complete morning stretch", "done with water habit", "finished my workout"
            val habitName = extractHabitName(command)
            val notes = extractNotes(command)

            val parsedHabit = ParsedHabitCommand(
                habitName = habitName,
                notes = notes
            )

            return VoiceCommandResult.HabitCommand(parsedHabit)

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing habit command: $command", e)
            return VoiceCommandResult.ParseError(command, "Could not understand habit name")
        }
    }

    private fun parseJournalCommand(command: String): VoiceCommandResult {
        try {
            // Extract journal content - everything after "journal", "journaling", or "write about"
            val content = extractJournalContent(command)
            val mood = extractMoodFromJournal(command)

            val parsedJournal = ParsedJournalCommand(
                content = content,
                mood = mood
            )

            return VoiceCommandResult.JournalCommand(parsedJournal)

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing journal command: $command", e)
            return VoiceCommandResult.ParseError(command, "Could not understand journal entry")
        }
    }

    // Helper functions for parsing

    private fun extractMealType(command: String): String? {
        val lower = command.lowercase()
        return when {
            lower.contains("breakfast") -> "breakfast"
            lower.contains("lunch") -> "lunch"
            lower.contains("dinner") -> "dinner"
            lower.contains("snack") -> "snack"
            else -> null
        }
    }

    private fun extractFoodItems(command: String): List<FoodItem> {
        Log.d(TAG, "extractFoodItems: Original command: \"$command\"")
        
        // Remove meal type keywords and "log" to focus on food items
        val cleanedCommand = command.lowercase()
            .replace(Regex("\\b(log|a|an|of|the|meal|breakfast|lunch|dinner|snack)\\b"), " ")
            .trim()

        Log.d(TAG, "extractFoodItems: Cleaned command: \"$cleanedCommand\"")

        // Split by common separators and extract food items
        val separators = Regex("[,]|\\band\\b|\\bwith\\b|\\bplus\\b")
        val parts = cleanedCommand.split(separators).map { it.trim() }.filter { it.isNotBlank() }

        Log.d(TAG, "extractFoodItems: Parts after splitting: $parts")

        if (parts.isEmpty()) {
            // If no separators, try to extract a single food item
            // Remove common prefixes
            val singleFood = cleanedCommand
                .replace(Regex("^\\s*(log|a|an|of|the|meal)\\s+"), "")
                .trim()
            
            Log.d(TAG, "extractFoodItems: Single food extracted: \"$singleFood\"")
            
            if (singleFood.isNotBlank()) {
                val foodItem = extractSingleFoodItem(singleFood)
                Log.d(TAG, "extractFoodItems: FoodItem created: name=\"${foodItem.name}\", quantity=${foodItem.quantity}, unit=${foodItem.unit}")
                return listOf(foodItem)
            }
            Log.w(TAG, "extractFoodItems: No food item found in command")
            return emptyList()
        }

        val foodItems = parts.mapNotNull { part ->
            val foodItem = extractSingleFoodItem(part)
            Log.d(TAG, "extractFoodItems: FoodItem created: name=\"${foodItem.name}\", quantity=${foodItem.quantity}, unit=${foodItem.unit}")
            foodItem
        }
        
        Log.d(TAG, "extractFoodItems: Returning ${foodItems.size} food items")
        return foodItems
    }

    private fun extractSingleFoodItem(part: String): FoodItem {
        val trimmedPart = part.trim()
        Log.d(TAG, "extractSingleFoodItem: Processing part: \"$trimmedPart\"")
        
        // Match patterns like "2 eggs", "1 cup rice", "steak", "a steak", "an apple"
        // Try to match quantity and unit first
        val quantityUnitRegex = Regex("(\\d+(?:\\.\\d+)?)?\\s*(cup|cups|oz|ounce|ounces|lb|pound|pounds|g|gram|grams|kg|kilogram|kilograms|piece|pieces|slice|slices|tbsp|tablespoon|tablespoons|tsp|teaspoon|teaspoons)?\\s*(.+)", RegexOption.IGNORE_CASE)
        val match = quantityUnitRegex.find(trimmedPart)

        return if (match != null) {
            val quantity = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
            val unit = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
            val name = match.groupValues.getOrNull(3)?.trim()?.takeIf { it.isNotBlank() }

            val foodItem = FoodItem(
                name = name ?: trimmedPart,
                quantity = quantity,
                unit = unit
            )
            Log.d(TAG, "extractSingleFoodItem: Regex matched - name=\"${foodItem.name}\", quantity=${foodItem.quantity}, unit=${foodItem.unit}")
            foodItem
        } else {
            // No quantity/unit found, just use the whole part as the name
            val foodItem = FoodItem(
                name = trimmedPart,
                quantity = null,
                unit = null
            )
            Log.d(TAG, "extractSingleFoodItem: No regex match, using whole part as name: \"${foodItem.name}\"")
            foodItem
        }
    }

    private fun estimateCalories(foods: List<FoodItem>): Int? {
        // Basic calorie estimation - very rough
        var totalCalories = 0
        var hasEstimates = false

        foods.forEach { food ->
            val calories = estimateFoodCalories(food)
            if (calories > 0) {
                totalCalories += calories
                hasEstimates = true
            }
        }

        return if (hasEstimates) totalCalories else null
    }

    private fun estimateFoodCalories(food: FoodItem): Int {
        // Very basic calorie estimation
        val name = food.name.lowercase()
        val quantity = food.quantity?.toDoubleOrNull() ?: 1.0

        return when {
            name.contains("egg") -> (quantity * 70).toInt()
            name.contains("toast") || name.contains("bread") -> (quantity * 80).toInt()
            name.contains("coffee") -> 5 // minimal calories
            name.contains("apple") -> (quantity * 95).toInt()
            name.contains("banana") -> (quantity * 105).toInt()
            name.contains("rice") || name.contains("pasta") -> (quantity * 130).toInt()
            name.contains("chicken") || name.contains("meat") -> (quantity * 200).toInt()
            name.contains("fish") -> (quantity * 150).toInt()
            else -> 0 // unknown food
        }
    }

    private fun extractSupplementName(command: String): String {
        // Extract supplement name - look for common patterns
        val supplementKeywords = listOf(
            "vitamin", "mineral", "supplement", "pill", "capsule", "tablet"
        )

        val words = command.split("\\s+".toRegex())
        val startIndex = words.indexOfFirst { word ->
            supplementKeywords.any { keyword -> word.lowercase().contains(keyword) }
        }

        return if (startIndex >= 0 && startIndex < words.size - 1) {
            // Take everything after the keyword
            words.subList(startIndex + 1, words.size).joinToString(" ")
        } else {
            // Fallback: take the last meaningful part
            words.lastOrNull { it.length > 2 } ?: "Unknown Supplement"
        }
    }

    private fun extractQuantity(command: String): String? {
        // Look for patterns like "2000 IU", "500 mg", "1000 mcg"
        val quantityRegex = Regex("(\\d+(?:\\.\\d+)?)\\s*(mg|g|mcg|iu|units?|capsules?|tablets?)", RegexOption.IGNORE_CASE)
        return quantityRegex.find(command)?.value
    }

    private fun mapToMicronutrients(name: String, quantity: String?): Map<MicronutrientType, Double> {
        val lowerName = name.lowercase()
        val quantityValue = quantity?.let {
            Regex("(\\d+(?:\\.\\d+)?)").find(it)?.groupValues?.get(1)?.toDoubleOrNull()
        } ?: 1.0

        return when {
            lowerName.contains("vitamin d") -> mapOf(MicronutrientType.VITAMIN_D to quantityValue)
            lowerName.contains("vitamin c") -> mapOf(MicronutrientType.VITAMIN_C to quantityValue)
            lowerName.contains("calcium") -> mapOf(MicronutrientType.CALCIUM to quantityValue)
            lowerName.contains("iron") -> mapOf(MicronutrientType.IRON to quantityValue)
            lowerName.contains("magnesium") -> mapOf(MicronutrientType.MAGNESIUM to quantityValue)
            lowerName.contains("zinc") -> mapOf(MicronutrientType.ZINC to quantityValue)
            else -> emptyMap()
        }
    }

    private fun extractWorkoutType(command: String): String {
        val lower = command.lowercase()

        return when {
            lower.contains("run") || lower.contains("running") || lower.contains("jog") -> "Running"
            lower.contains("walk") || lower.contains("walking") -> "Walking"
            lower.contains("bike") || lower.contains("cycling") -> "Cycling"
            lower.contains("swim") || lower.contains("swimming") -> "Swimming"
            lower.contains("lift") || lower.contains("weight") -> "Weight Training"
            lower.contains("yoga") -> "Yoga"
            else -> "Other"
        }
    }

    private fun extractDuration(command: String): Int? {
        // Look for patterns like "30 minutes", "1 hour", "45 min"
        val durationRegex = Regex("(\\d+)\\s*(?:minutes?|mins?|hours?|hrs?)", RegexOption.IGNORE_CASE)
        val match = durationRegex.find(command)

        return match?.let {
            val number = it.groupValues[1].toInt()
            val unit = it.groupValues[2].lowercase()

            when {
                unit.contains("hour") -> number * 60 // convert to minutes
                else -> number // already in minutes
            }
        }
    }

    private fun extractDistance(command: String): Pair<Double, String>? {
        // Look for patterns like "5 miles", "10 km", "3.5 kilometers"
        val distanceRegex = Regex("(\\d+(?:\\.\\d+)?)\\s*(miles?|kilometers?|km|meters?|m)", RegexOption.IGNORE_CASE)
        val match = distanceRegex.find(command)

        return match?.let {
            val distance = it.groupValues[1].toDouble()
            val unit = it.groupValues[2].lowercase()

            // Normalize unit
            val normalizedUnit = when {
                unit.contains("mile") -> "miles"
                unit.contains("kilometer") || unit == "km" -> "km"
                unit.contains("meter") || unit == "m" -> "meters"
                else -> unit
            }

            Pair(distance, normalizedUnit)
        }
    }

    private fun extractCaloriesBurned(command: String): Int? {
        // Look for patterns like "burned 300 calories", "300 cal"
        val calorieRegex = Regex("(\\d+)\\s*(?:calories?|cal|kcal)", RegexOption.IGNORE_CASE)
        return calorieRegex.find(command)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractWaterAmount(command: String): Pair<Double, String> {
        // Look for patterns like "16 ounces", "500 ml", "2 cups"
        val waterRegex = Regex("(\\d+(?:\\.\\d+)?)\\s*(ounces?|oz|ml|milliliters?|cups?|liters?|l)", RegexOption.IGNORE_CASE)
        val match = waterRegex.find(command)

        return match?.let {
            val amount = it.groupValues[1].toDouble()
            val unit = it.groupValues[2].lowercase()
            Pair(amount, unit)
        } ?: Pair(8.0, "ounces") // default to 8 oz glass
    }

    private fun convertToMl(amount: Double, unit: String): Int {
        return when (unit) {
            "ounces", "oz" -> (amount * 29.5735).roundToInt() // 1 oz = 29.5735 ml, use roundToInt for proper rounding
            "cups", "cup" -> (amount * 236.588).roundToInt() // 1 cup = 236.588 ml
            "liters", "liter", "l" -> (amount * 1000).roundToInt()
            "ml", "milliliters", "milliliter" -> amount.roundToInt()
            else -> (amount * 29.5735).roundToInt() // default to ounces
        }
    }

    private fun extractWeight(command: String): Pair<Double, String> {
        // Look for patterns like "150 pounds", "70 kg", "180 lbs"
        val weightRegex = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:pounds?|lbs?|kilograms?|kgs?|kg)", RegexOption.IGNORE_CASE)
        val match = weightRegex.find(command)

        return match?.let {
            val weight = it.groupValues[1].toDouble()
            val unitText = it.groupValues[0].lowercase()

            val unit = when {
                unitText.contains("pound") || unitText.contains("lb") -> "lbs"
                unitText.contains("kilogram") || unitText.contains("kg") -> "kg"
                else -> "lbs"
            }

            Pair(weight, unit)
        } ?: throw IllegalArgumentException("Could not parse weight from command")
    }

    private fun extractSleepHours(command: String): Double? {
        // Look for patterns like "8 hours", "7.5 hours", "slept 6 hours"
        val sleepRegex = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:hours?|hrs?)", RegexOption.IGNORE_CASE)
        val match = sleepRegex.find(command)
        return match?.groupValues?.get(1)?.toDoubleOrNull()
    }

    private fun extractSleepQuality(command: String): String? {
        val lower = command.lowercase()
        return when {
            lower.contains("poor") || lower.contains("bad") || lower.contains("terrible") -> "poor"
            lower.contains("fair") || lower.contains("okay") || lower.contains("ok") -> "fair"
            lower.contains("good") || lower.contains("well") -> "good"
            lower.contains("excellent") || lower.contains("great") || lower.contains("amazing") -> "excellent"
            else -> null
        }
    }

    private fun extractMoodLevel(command: String): Int {
        val lower = command.lowercase()
        // Try to extract explicit number (1-5)
        val numberRegex = Regex("(\\d+)")
        val numberMatch = numberRegex.find(command)
        numberMatch?.let {
            val num = it.groupValues[1].toIntOrNull()
            if (num != null && num in 1..5) return num
        }
        
        // Map words to mood levels
        return when {
            lower.contains("terrible") || lower.contains("awful") || lower.contains("horrible") -> 1
            lower.contains("bad") || lower.contains("sad") || lower.contains("down") -> 2
            lower.contains("okay") || lower.contains("ok") || lower.contains("fine") || lower.contains("meh") -> 3
            lower.contains("good") || lower.contains("happy") || lower.contains("great") -> 4
            lower.contains("excellent") || lower.contains("amazing") || lower.contains("fantastic") -> 5
            else -> 3 // default to neutral
        }
    }

    private fun extractEmotions(command: String): List<String> {
        val lower = command.lowercase()
        val emotions = mutableListOf<String>()
        
        val emotionKeywords = mapOf(
            "happy" to "happy",
            "sad" to "sad",
            "angry" to "angry",
            "anxious" to "anxious",
            "stressed" to "stressed",
            "calm" to "calm",
            "excited" to "excited",
            "tired" to "tired",
            "energetic" to "energetic",
            "frustrated" to "frustrated",
            "content" to "content",
            "worried" to "worried"
        )
        
        emotionKeywords.forEach { (keyword, emotion) ->
            if (lower.contains(keyword)) {
                emotions.add(emotion)
            }
        }
        
        return emotions
    }

    private fun extractMeditationType(command: String): String {
        val lower = command.lowercase()
        return when {
            lower.contains("guided") -> "guided"
            lower.contains("silent") -> "silent"
            lower.contains("walking") -> "walking"
            lower.contains("body scan") || lower.contains("bodyscan") -> "body_scan"
            lower.contains("loving kindness") -> "loving_kindness"
            lower.contains("transcendental") -> "transcendental"
            lower.contains("mindfulness") -> "mindfulness"
            else -> "guided" // default
        }
    }

    private fun extractHabitName(command: String): String {
        // Remove common prefixes like "complete", "done with", "finished", "log"
        val cleaned = command.lowercase()
            .replace(Regex("\\b(complete|completed|done|finished|finish|log|logged|mark|marked)\\s+(the|a|an|my|our)?\\s*"), " ")
            .replace(Regex("\\b(habit|task|activity)\\s*"), " ")
            .trim()

        // Take the remaining text as the habit name
        // If it's too short or empty, try to extract from the original command
        if (cleaned.length < 3) {
            // Try to find a meaningful phrase after "complete" or "done"
            val afterComplete = Regex("(?:complete|done|finished|log)\\s+(?:the|a|an|my)?\\s*(.+)", RegexOption.IGNORE_CASE)
                .find(command)?.groupValues?.getOrNull(1)?.trim()
            
            return afterComplete?.takeIf { it.length >= 3 } ?: "Unknown Habit"
        }

        // Capitalize first letter
        return cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    private fun extractNotes(command: String): String? {
        // Look for notes after phrases like "with notes", "note:", "because"
        val notesPattern = Regex("(?:with notes?|note:|because|reason:)\\s*(.+)", RegexOption.IGNORE_CASE)
        val match = notesPattern.find(command)
        return match?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun extractJournalContent(command: String): String {
        // Remove journal keywords and extract the actual content
        val cleaned = command.lowercase()
            .replace(Regex("\\b(journal|journaling|write|log)\\s+(about|that|this|:)?\\s*"), " ")
            .replace(Regex("\\b(thought|thoughts|entry|entry:)\\s*"), " ")
            .trim()

        // If cleaned is too short, try to get everything after "journal" or "write about"
        if (cleaned.length < 5) {
            val afterJournal = Regex("(?:journal|journaling|write about|log thought)\\s*:?\\s*(.+)", RegexOption.IGNORE_CASE)
                .find(command)?.groupValues?.getOrNull(1)?.trim()
            
            return afterJournal?.takeIf { it.length >= 5 } ?: command
        }

        return cleaned
    }

    private fun extractMoodFromJournal(command: String): String? {
        // Look for mood indicators in journal content
        val lower = command.lowercase()
        return when {
            lower.contains("happy") || lower.contains("great") || lower.contains("excited") -> "happy"
            lower.contains("sad") || lower.contains("down") || lower.contains("depressed") -> "sad"
            lower.contains("anxious") || lower.contains("worried") || lower.contains("nervous") -> "anxious"
            lower.contains("stressed") || lower.contains("overwhelmed") -> "stressed"
            lower.contains("calm") || lower.contains("peaceful") || lower.contains("relaxed") -> "calm"
            lower.contains("angry") || lower.contains("frustrated") || lower.contains("mad") -> "angry"
            else -> null
        }
    }
}

// Result types
sealed class VoiceCommandResult {
    data class MealCommand(val parsedMeal: VoiceCommandParser.ParsedMealCommand) : VoiceCommandResult()
    data class SupplementCommand(val parsedSupplement: VoiceCommandParser.ParsedSupplementCommand) : VoiceCommandResult()
    data class WorkoutCommand(val parsedWorkout: VoiceCommandParser.ParsedWorkoutCommand) : VoiceCommandResult()
    data class WaterCommand(val parsedWater: VoiceCommandParser.ParsedWaterCommand) : VoiceCommandResult()
    data class WeightCommand(val parsedWeight: VoiceCommandParser.ParsedWeightCommand) : VoiceCommandResult()
    data class SleepCommand(val parsedSleep: VoiceCommandParser.ParsedSleepCommand) : VoiceCommandResult()
    data class MoodCommand(val parsedMood: VoiceCommandParser.ParsedMoodCommand) : VoiceCommandResult()
    data class MeditationCommand(val parsedMeditation: VoiceCommandParser.ParsedMeditationCommand) : VoiceCommandResult()
    data class HabitCommand(val parsedHabit: VoiceCommandParser.ParsedHabitCommand) : VoiceCommandResult()
    data class JournalCommand(val parsedJournal: VoiceCommandParser.ParsedJournalCommand) : VoiceCommandResult()
    data class ParseError(val originalCommand: String, val errorMessage: String) : VoiceCommandResult()
    data class Unknown(val command: String) : VoiceCommandResult()
}
