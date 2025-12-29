package com.coachie.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.data.HabitRepository
import com.mealprint.ai.data.model.DailyLog
import com.mealprint.ai.data.model.HealthLog
import com.mealprint.ai.util.VoiceCommandParser
import com.mealprint.ai.util.VoiceCommandResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*

class VoiceLoggingViewModel(
    private val firebaseRepository: FirebaseRepository = FirebaseRepository(),
    private val habitRepository: HabitRepository = HabitRepository.getInstance()
) : ViewModel() {

    suspend fun saveParsedCommand(userId: String, result: VoiceCommandResult): Boolean {
        return try {
            val dateString = getCurrentDateString()

            when (result) {
                is VoiceCommandResult.MealCommand -> {
                    saveMealLog(userId, dateString, result.parsedMeal)
                }
                is VoiceCommandResult.SupplementCommand -> {
                    saveSupplementLog(userId, dateString, result.parsedSupplement)
                }
                is VoiceCommandResult.WorkoutCommand -> {
                    saveWorkoutLog(userId, dateString, result.parsedWorkout)
                }
                is VoiceCommandResult.WaterCommand -> {
                    saveWaterLog(userId, dateString, result.parsedWater)
                }
                is VoiceCommandResult.WeightCommand -> {
                    saveWeightLog(userId, dateString, result.parsedWeight)
                }
                is VoiceCommandResult.SleepCommand -> {
                    saveSleepLog(userId, dateString, result.parsedSleep)
                }
                is VoiceCommandResult.MoodCommand -> {
                    saveMoodLog(userId, dateString, result.parsedMood)
                }
                is VoiceCommandResult.MeditationCommand -> {
                    saveMeditationLog(userId, dateString, result.parsedMeditation)
                }
                is VoiceCommandResult.HabitCommand -> {
                    saveHabitCompletion(userId, result.parsedHabit)
                }
                is VoiceCommandResult.JournalCommand -> {
                    saveJournalEntry(userId, dateString, result.parsedJournal)
                }
                else -> false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun saveMealLog(
        userId: String,
        dateString: String,
        meal: VoiceCommandParser.ParsedMealCommand
    ): Boolean {
        android.util.Log.d("VoiceLoggingViewModel", "saveMealLog: Starting save for meal")
        android.util.Log.d("VoiceLoggingViewModel", "saveMealLog: meal.foods.size = ${meal.foods.size}")
        meal.foods.forEachIndexed { index, food ->
            android.util.Log.d("VoiceLoggingViewModel", "saveMealLog: food[$index] = name=\"${food.name}\", quantity=${food.quantity}, unit=${food.unit}")
        }
        
        // Use the first food item's name, or combine all food names, or use meal type as fallback
        val foodName = when {
            meal.foods.isNotEmpty() -> {
                // For single food items, just use the name directly (capitalize first letter)
                // For multiple foods, combine them
                if (meal.foods.size == 1) {
                    val name = meal.foods[0].name.trim()
                    name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                } else {
                    // Combine all food names
                    meal.foods.joinToString(", ") { food ->
                        buildString {
                            food.quantity?.let { append("$it ") }
                            food.unit?.let { append("$it ") }
                            append(food.name)
                        }.trim()
                    }
                }
            }
            meal.mealType != null -> meal.mealType.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            else -> "Meal"
        }

        android.util.Log.d("VoiceLoggingViewModel", "saveMealLog: Final foodName = \"$foodName\"")

        // Estimate macros based on food items if we have them
        val macroEstimate = if (meal.foods.isNotEmpty()) {
            estimateMacros(meal.foods)
        } else {
            MacroEstimate(meal.totalCalories ?: 0, 0, 0, 0)
        }

        android.util.Log.d("VoiceLoggingViewModel", "saveMealLog: Macro estimate - calories=${macroEstimate.calories}, protein=${macroEstimate.protein}, carbs=${macroEstimate.carbs}, fat=${macroEstimate.fat}")

        // Create a meal log from the parsed data
        val mealLog = HealthLog.MealLog(
            foodName = foodName,
            calories = macroEstimate.calories,
            protein = macroEstimate.protein,
            carbs = macroEstimate.carbs,
            fat = macroEstimate.fat,
            sugar = 0, // We don't have sugar data from voice parsing
            addedSugar = 0
        )

        android.util.Log.d("VoiceLoggingViewModel", "saveMealLog: Created MealLog - foodName=\"${mealLog.foodName}\", calories=${mealLog.calories}, protein=${mealLog.protein}, carbs=${mealLog.carbs}, fat=${mealLog.fat}")

        // ROOT CAUSE FIX: Always use authenticated user's ID
        val authenticatedUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: throw Exception("User must be authenticated to save health logs")
        val saveResult = firebaseRepository.saveHealthLog(authenticatedUserId, dateString, mealLog)
        android.util.Log.d("VoiceLoggingViewModel", "saveMealLog: Save result - success=${saveResult.isSuccess}")
        if (saveResult.isFailure) {
            android.util.Log.e("VoiceLoggingViewModel", "saveMealLog: Save failed - ${saveResult.exceptionOrNull()?.message}")
        }
        
        return saveResult.isSuccess
    }

    /**
     * Data class for macro estimates
     */
    private data class MacroEstimate(
        val calories: Int,
        val protein: Int,
        val carbs: Int,
        val fat: Int
    )

    /**
     * Estimate macros based on food items
     */
    private fun estimateMacros(foods: List<VoiceCommandParser.FoodItem>): MacroEstimate {
        android.util.Log.d("VoiceLoggingViewModel", "estimateMacros: Estimating macros for ${foods.size} food items")
        
        var totalCalories = 0
        var totalProtein = 0
        var totalCarbs = 0
        var totalFat = 0

        foods.forEach { food ->
            val quantity = food.quantity?.toDoubleOrNull() ?: 1.0
            val name = food.name.lowercase()
            
            android.util.Log.d("VoiceLoggingViewModel", "estimateMacros: Processing food - name=\"${food.name}\" (lowercase: \"$name\"), quantity=$quantity")

            // Basic macro estimation based on food type
            when {
                name.contains("steak") || name.contains("beef") -> {
                    val calories = (quantity * 250).toInt()
                    val protein = (quantity * 25).toInt()
                    val fat = (quantity * 15).toInt()
                    totalCalories += calories
                    totalProtein += protein
                    totalFat += fat
                    android.util.Log.d("VoiceLoggingViewModel", "estimateMacros: Matched steak/beef - adding calories=$calories, protein=$protein, fat=$fat")
                }
                name.contains("chicken") || name.contains("poultry") -> {
                    totalCalories += (quantity * 200).toInt()
                    totalProtein += (quantity * 30).toInt()
                    totalFat += (quantity * 5).toInt()
                }
                name.contains("fish") || name.contains("salmon") || name.contains("tuna") -> {
                    totalCalories += (quantity * 180).toInt()
                    totalProtein += (quantity * 25).toInt()
                    totalFat += (quantity * 8).toInt()
                }
                name.contains("egg") -> {
                    totalCalories += (quantity * 70).toInt()
                    totalProtein += (quantity * 6).toInt()
                    totalFat += (quantity * 5).toInt()
                }
                name.contains("rice") || name.contains("pasta") -> {
                    totalCalories += (quantity * 130).toInt()
                    totalCarbs += (quantity * 28).toInt()
                    totalProtein += (quantity * 3).toInt()
                }
                name.contains("bread") || name.contains("toast") -> {
                    totalCalories += (quantity * 80).toInt()
                    totalCarbs += (quantity * 15).toInt()
                    totalProtein += (quantity * 3).toInt()
                }
                name.contains("apple") -> {
                    totalCalories += (quantity * 95).toInt()
                    totalCarbs += (quantity * 25).toInt()
                }
                name.contains("banana") -> {
                    totalCalories += (quantity * 105).toInt()
                    totalCarbs += (quantity * 27).toInt()
                }
                name.contains("vegetable") || name.contains("salad") || name.contains("broccoli") || name.contains("spinach") -> {
                    totalCalories += (quantity * 30).toInt()
                    totalCarbs += (quantity * 5).toInt()
                    totalProtein += (quantity * 2).toInt()
                }
                else -> {
                    // Generic estimate for unknown foods
                    val calories = (quantity * 100).toInt()
                    val carbs = (quantity * 15).toInt()
                    val protein = (quantity * 5).toInt()
                    val fat = (quantity * 3).toInt()
                    totalCalories += calories
                    totalCarbs += carbs
                    totalProtein += protein
                    totalFat += fat
                    android.util.Log.d("VoiceLoggingViewModel", "estimateMacros: Using generic estimate - adding calories=$calories, protein=$protein, carbs=$carbs, fat=$fat")
                }
            }
        }

        android.util.Log.d("VoiceLoggingViewModel", "estimateMacros: Final totals - calories=$totalCalories, protein=$totalProtein, carbs=$totalCarbs, fat=$totalFat")
        return MacroEstimate(totalCalories, totalProtein, totalCarbs, totalFat)
    }

    private suspend fun saveSupplementLog(
        userId: String,
        dateString: String,
        supplement: VoiceCommandParser.ParsedSupplementCommand
    ): Boolean {
        val supplementLog = HealthLog.SupplementLog(
            name = supplement.supplementName,
            micronutrients = supplement.micronutrients.mapKeys { it.key.toString() } // Convert to Map<String, Double>
        )

        // ROOT CAUSE FIX: Always use authenticated user's ID
        val authenticatedUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: throw Exception("User must be authenticated to save health logs")
        return firebaseRepository.saveHealthLog(authenticatedUserId, dateString, supplementLog).isSuccess
    }

    private suspend fun saveWorkoutLog(
        userId: String,
        dateString: String,
        workout: VoiceCommandParser.ParsedWorkoutCommand
    ): Boolean {
        val workoutLog = HealthLog.WorkoutLog(
            workoutType = workout.workoutType,
            durationMin = workout.durationMinutes ?: 0,
            caloriesBurned = workout.caloriesBurned ?: 0,
            intensity = "Medium" // Default intensity since we don't parse this
        )

        // ROOT CAUSE FIX: Always use authenticated user's ID
        val authenticatedUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: throw Exception("User must be authenticated to save health logs")
        return firebaseRepository.saveHealthLog(authenticatedUserId, dateString, workoutLog).isSuccess
    }

    private suspend fun saveWaterLog(
        userId: String,
        dateString: String,
        water: VoiceCommandParser.ParsedWaterCommand
    ): Boolean {
        // Save as health log entry
        val waterLog = HealthLog.WaterLog(
            ml = water.amount
        )

        // ROOT CAUSE FIX: Always use authenticated user's ID
        val authenticatedUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: throw Exception("User must be authenticated to save health logs")
        
        // CRITICAL FIX: saveHealthLog() already updates DailyLog.water automatically when saving a WaterLog
        // Do NOT update DailyLog.water again here, as it causes double-counting
        // The saveHealthLog() method in FirebaseRepository handles updating DailyLog.water
        val healthLogResult = firebaseRepository.saveHealthLog(authenticatedUserId, dateString, waterLog)
        
        if (!healthLogResult.isSuccess) {
            android.util.Log.e("VoiceLoggingViewModel", "Failed to save water health log")
            return false
        }
        
        android.util.Log.d("VoiceLoggingViewModel", "✅ Water log saved successfully: ${water.amount}ml (DailyLog.water updated automatically by saveHealthLog)")
        
        return true
    }

    private suspend fun saveWeightLog(
        userId: String,
        dateString: String,
        weight: VoiceCommandParser.ParsedWeightCommand
    ): Boolean {
        val weightLog = HealthLog.WeightLog(
            weight = weight.weight,
            unit = weight.unit
        )

        // ROOT CAUSE FIX: Always use authenticated user's ID
        val authenticatedUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: throw Exception("User must be authenticated to save health logs")
        return firebaseRepository.saveHealthLog(authenticatedUserId, dateString, weightLog).isSuccess
    }

    private suspend fun saveSleepLog(
        userId: String,
        dateString: String,
        sleep: VoiceCommandParser.ParsedSleepCommand
    ): Boolean {
        val hours = sleep.hours ?: 8.0 // Default to 8 hours if not specified
        val now = System.currentTimeMillis()
        val endTime = now
        val startTime = now - (hours * 3600 * 1000).toLong() // Calculate start time from hours ago

        val quality = sleep.quality ?: "good"
        val qualityValue = when (quality) {
            "poor" -> 1
            "fair" -> 2
            "good" -> 3
            "excellent" -> 4
            else -> 3
        }

        val sleepLog = HealthLog.SleepLog(
            startTime = startTime,
            endTime = endTime,
            quality = qualityValue
        )

        // ROOT CAUSE FIX: Always use authenticated user's ID
        val authenticatedUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: throw Exception("User must be authenticated to save health logs")
        val saveResult = firebaseRepository.saveHealthLog(authenticatedUserId, dateString, sleepLog)
        return saveResult.isSuccess
    }

    private suspend fun saveMoodLog(
        userId: String,
        dateString: String,
        mood: VoiceCommandParser.ParsedMoodCommand
    ): Boolean {
        val moodLog = HealthLog.MoodLog(
            level = mood.level,
            emotions = mood.emotions
        )

        // ROOT CAUSE FIX: Always use authenticated user's ID
        val authenticatedUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: throw Exception("User must be authenticated to save health logs")
        val saveResult = firebaseRepository.saveHealthLog(authenticatedUserId, dateString, moodLog)
        return saveResult.isSuccess
    }

    private suspend fun saveMeditationLog(
        userId: String,
        dateString: String,
        meditation: VoiceCommandParser.ParsedMeditationCommand
    ): Boolean {
        val meditationLog = HealthLog.MeditationLog(
            durationMinutes = meditation.durationMinutes,
            meditationType = meditation.meditationType,
            completed = true
        )

        // ROOT CAUSE FIX: Always use authenticated user's ID
        val authenticatedUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: throw Exception("User must be authenticated to save health logs")
        val saveResult = firebaseRepository.saveHealthLog(authenticatedUserId, dateString, meditationLog)
        return saveResult.isSuccess
    }

    private fun buildWorkoutNote(workout: VoiceCommandParser.ParsedWorkoutCommand): String? {
        val parts = mutableListOf<String>()

        workout.durationMinutes?.let { parts.add("$it minutes") }
        workout.distance?.let { distance ->
            workout.distanceUnit?.let { unit ->
                parts.add("$distance $unit")
            }
        }
        workout.caloriesBurned?.let { parts.add("$it calories") }

        return parts.joinToString(", ").takeIf { it.isNotBlank() }
    }

    private suspend fun saveHabitCompletion(
        userId: String,
        habit: VoiceCommandParser.ParsedHabitCommand
    ): Boolean {
        return try {
            // Get all active habits for the user
            val habits = habitRepository.getHabits(userId).first()
            
            // Find matching habit by name (fuzzy match)
            val habitNameLower = habit.habitName.lowercase()
            val matchingHabit = habits.find { habit ->
                habit.title.lowercase().contains(habitNameLower) ||
                habitNameLower.contains(habit.title.lowercase()) ||
                habit.title.lowercase().split(" ").any { word -> 
                    habitNameLower.contains(word) && word.length > 3
                }
            }

            if (matchingHabit == null) {
                android.util.Log.w("VoiceLoggingViewModel", "Could not find habit matching: ${habit.habitName}")
                return false
            }

            android.util.Log.d("VoiceLoggingViewModel", "Found matching habit: ${matchingHabit.title} (id: ${matchingHabit.id})")

            // Complete the habit
            val result = habitRepository.completeHabit(
                userId = userId,
                habitId = matchingHabit.id,
                value = matchingHabit.targetValue,
                notes = habit.notes
            )

            result.isSuccess
        } catch (e: Exception) {
            android.util.Log.e("VoiceLoggingViewModel", "Error completing habit via voice", e)
            false
        }
    }

    private suspend fun saveJournalEntry(
        userId: String,
        dateString: String,
        journal: VoiceCommandParser.ParsedJournalCommand
    ): Boolean {
        return try {
            // Create a simple journal entry with the voice content
            val entryId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            // Create a user message from the journal content
            val userMessage = HealthLog.ChatMessage(
                id = UUID.randomUUID().toString(),
                role = "user",
                content = journal.content,
                timestamp = now
            )

            // Create a simple journal entry
            val journalEntry = HealthLog.JournalEntry(
                entryId = entryId,
                date = dateString,
                prompts = listOf("How are you feeling today?"), // Default prompt
                conversation = listOf(userMessage),
                startedAt = now,
                completedAt = now,
                wordCount = journal.content.split("\\s+".toRegex()).size,
                mood = journal.mood,
                isCompleted = true,
                notes = null
            )

            // ROOT CAUSE FIX: Always use authenticated user's ID
            val authenticatedUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                ?: throw Exception("User must be authenticated to save health logs")
            val saveResult = firebaseRepository.saveHealthLog(authenticatedUserId, dateString, journalEntry)
            saveResult.isSuccess
        } catch (e: Exception) {
            android.util.Log.e("VoiceLoggingViewModel", "Error saving journal entry via voice", e)
            false
        }
    }

    private fun getCurrentDateString(): String {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1 // Calendar.MONTH is 0-based
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        return String.format("%04d-%02d-%02d", year, month, day)
    }
}
