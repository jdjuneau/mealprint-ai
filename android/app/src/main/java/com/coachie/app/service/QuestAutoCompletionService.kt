package com.coachie.app.service

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
/**
 * Service to automatically update quest progress when tasks are completed throughout the app.
 * This eliminates the need for users to manually mark quests as complete.
 */
object QuestAutoCompletionService {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val functions: FirebaseFunctions = Firebase.functions
    private const val TAG = "QuestAutoCompletion"
    
    /**
     * Called when a habit is completed - checks and updates relevant quests
     */
    suspend fun onHabitCompleted(userId: String, habitId: String, habitTitle: String) {
        try {
            Log.d(TAG, "Habit completed: $habitTitle (id: $habitId)")
            updateQuestsForHabit(userId, habitId, habitTitle)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating quests for habit completion", e)
        }
    }
    
    /**
     * Called when a meal is logged - checks and updates relevant quests
     */
    suspend fun onMealLogged(userId: String) {
        try {
            Log.d(TAG, "Meal logged for user: $userId")
            updateQuestsForMeal(userId)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating quests for meal log", e)
        }
    }
    
    /**
     * Called when a workout is logged - checks and updates relevant quests
     */
    suspend fun onWorkoutLogged(userId: String) {
        try {
            Log.d(TAG, "Workout logged for user: $userId")
            updateQuestsForWorkout(userId)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating quests for workout log", e)
        }
    }
    
    /**
     * Called when water is logged - checks and updates relevant quests
     */
    suspend fun onWaterLogged(userId: String, amountMl: Int) {
        try {
            Log.d(TAG, "Water logged: ${amountMl}ml for user: $userId")
            updateQuestsForWater(userId, amountMl)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating quests for water log", e)
        }
    }
    
    /**
     * Called when sleep is logged - checks and updates relevant quests
     */
    suspend fun onSleepLogged(userId: String, hours: Double) {
        try {
            Log.d(TAG, "Sleep logged: ${hours}h for user: $userId")
            updateQuestsForSleep(userId, hours)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating quests for sleep log", e)
        }
    }
    
    /**
     * Update quests that match habit completion
     */
    private suspend fun updateQuestsForHabit(userId: String, habitId: String, habitTitle: String) {
        try {
            // Get all active quests
            val questsSnapshot = firestore.collection("users")
                .document(userId)
                .collection("quests")
                .whereIn("status", listOf("active", "in_progress"))
                .get()
                .await()
            
            for (questDoc in questsSnapshot.documents) {
                val questData = questDoc.data ?: continue
                val questId = questDoc.id
                val questType = questData["type"] as? String ?: continue
                val questTitle = questData["title"] as? String ?: ""
                val questDescription = questData["description"] as? String ?: ""
                val current = (questData["current"] as? Number)?.toInt() ?: 0
                val target = (questData["target"] as? Number)?.toInt() ?: 1
                
                // Check if quest is related to habits
                if (questType == "habit" || 
                    questTitle.contains("habit", ignoreCase = true) ||
                    questDescription.contains("habit", ignoreCase = true) ||
                    questTitle.contains(habitTitle, ignoreCase = true)) {
                    
                    // Increment quest progress
                    val newCurrent = (current + 1).coerceAtMost(target)
                    
                    try {
                        val updateQuest = functions.getHttpsCallable("updateQuestProgress")
                        updateQuest.call(mapOf(
                            "questId" to questId,
                            "progress" to newCurrent
                        )).await()
                        
                        Log.d(TAG, "✅ Updated quest '$questTitle': $current -> $newCurrent / $target")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update quest $questId", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching quests for habit completion", e)
        }
    }
    
    /**
     * Update quests that match meal logging
     */
    private suspend fun updateQuestsForMeal(userId: String) {
        try {
            val questsSnapshot = firestore.collection("users")
                .document(userId)
                .collection("quests")
                .whereIn("status", listOf("active", "in_progress"))
                .get()
                .await()
            
            for (questDoc in questsSnapshot.documents) {
                val questData = questDoc.data ?: continue
                val questId = questDoc.id
                val questTitle = questData["title"] as? String ?: ""
                val questDescription = questData["description"] as? String ?: ""
                val current = (questData["current"] as? Number)?.toInt() ?: 0
                val target = (questData["target"] as? Number)?.toInt() ?: 1
                
                // Check if quest is related to meals/nutrition
                if (questTitle.contains("meal", ignoreCase = true) ||
                    questTitle.contains("food", ignoreCase = true) ||
                    questTitle.contains("nutrition", ignoreCase = true) ||
                    questTitle.contains("eat", ignoreCase = true) ||
                    questDescription.contains("meal", ignoreCase = true) ||
                    questDescription.contains("food", ignoreCase = true)) {
                    
                    val newCurrent = (current + 1).coerceAtMost(target)
                    
                    try {
                        val updateQuest = functions.getHttpsCallable("updateQuestProgress")
                        updateQuest.call(mapOf(
                            "questId" to questId,
                            "progress" to newCurrent
                        )).await()
                        
                        Log.d(TAG, "✅ Updated meal quest '$questTitle': $current -> $newCurrent / $target")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update quest $questId", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching quests for meal log", e)
        }
    }
    
    /**
     * Update quests that match workout logging
     */
    private suspend fun updateQuestsForWorkout(userId: String) {
        try {
            val questsSnapshot = firestore.collection("users")
                .document(userId)
                .collection("quests")
                .whereIn("status", listOf("active", "in_progress"))
                .get()
                .await()
            
            for (questDoc in questsSnapshot.documents) {
                val questData = questDoc.data ?: continue
                val questId = questDoc.id
                val questTitle = questData["title"] as? String ?: ""
                val questDescription = questData["description"] as? String ?: ""
                val current = (questData["current"] as? Number)?.toInt() ?: 0
                val target = (questData["target"] as? Number)?.toInt() ?: 1
                
                // Check if quest is related to workouts/exercise
                if (questTitle.contains("workout", ignoreCase = true) ||
                    questTitle.contains("exercise", ignoreCase = true) ||
                    questTitle.contains("train", ignoreCase = true) ||
                    questTitle.contains("gym", ignoreCase = true) ||
                    questDescription.contains("workout", ignoreCase = true) ||
                    questDescription.contains("exercise", ignoreCase = true)) {
                    
                    val newCurrent = (current + 1).coerceAtMost(target)
                    
                    try {
                        val updateQuest = functions.getHttpsCallable("updateQuestProgress")
                        updateQuest.call(mapOf(
                            "questId" to questId,
                            "progress" to newCurrent
                        )).await()
                        
                        Log.d(TAG, "✅ Updated workout quest '$questTitle': $current -> $newCurrent / $target")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update quest $questId", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching quests for workout log", e)
        }
    }
    
    /**
     * Update quests that match water logging
     */
    private suspend fun updateQuestsForWater(userId: String, amountMl: Int) {
        try {
            val questsSnapshot = firestore.collection("users")
                .document(userId)
                .collection("quests")
                .whereIn("status", listOf("active", "in_progress"))
                .get()
                .await()
            
            for (questDoc in questsSnapshot.documents) {
                val questData = questDoc.data ?: continue
                val questId = questDoc.id
                val questTitle = questData["title"] as? String ?: ""
                val questDescription = questData["description"] as? String ?: ""
                val current = (questData["current"] as? Number)?.toInt() ?: 0
                val target = (questData["target"] as? Number)?.toInt() ?: 1
                
                // Check if quest is related to water/hydration
                if (questTitle.contains("water", ignoreCase = true) ||
                    questTitle.contains("hydrate", ignoreCase = true) ||
                    questDescription.contains("water", ignoreCase = true) ||
                    questDescription.contains("hydrate", ignoreCase = true)) {
                    
                    // For water, we might want to increment by amount, but for simplicity, just +1 per log
                    val newCurrent = (current + 1).coerceAtMost(target)
                    
                    try {
                        val updateQuest = functions.getHttpsCallable("updateQuestProgress")
                        updateQuest.call(mapOf(
                            "questId" to questId,
                            "progress" to newCurrent
                        )).await()
                        
                        Log.d(TAG, "✅ Updated water quest '$questTitle': $current -> $newCurrent / $target")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update quest $questId", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching quests for water log", e)
        }
    }
    
    /**
     * Update quests that match sleep logging
     */
    private suspend fun updateQuestsForSleep(userId: String, hours: Double) {
        try {
            val questsSnapshot = firestore.collection("users")
                .document(userId)
                .collection("quests")
                .whereIn("status", listOf("active", "in_progress"))
                .get()
                .await()
            
            for (questDoc in questsSnapshot.documents) {
                val questData = questDoc.data ?: continue
                val questId = questDoc.id
                val questTitle = questData["title"] as? String ?: ""
                val questDescription = questData["description"] as? String ?: ""
                val current = (questData["current"] as? Number)?.toInt() ?: 0
                val target = (questData["target"] as? Number)?.toInt() ?: 1
                
                // Check if quest is related to sleep
                if (questTitle.contains("sleep", ignoreCase = true) ||
                    questTitle.contains("rest", ignoreCase = true) ||
                    questDescription.contains("sleep", ignoreCase = true) ||
                    questDescription.contains("rest", ignoreCase = true)) {
                    
                    val newCurrent = (current + 1).coerceAtMost(target)
                    
                    try {
                        val updateQuest = functions.getHttpsCallable("updateQuestProgress")
                        updateQuest.call(mapOf(
                            "questId" to questId,
                            "progress" to newCurrent
                        )).await()
                        
                        Log.d(TAG, "✅ Updated sleep quest '$questTitle': $current -> $newCurrent / $target")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update quest $questId", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching quests for sleep log", e)
        }
    }
}

