package com.coachie.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.data.model.SavedMeal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.tasks.await
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore

/**
 * ViewModel for saved meals screen
 */
class SavedMealsViewModel(
    private val firebaseRepository: FirebaseRepository,
    private val userId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<SavedMealsUiState>(SavedMealsUiState.Loading)
    val uiState: StateFlow<SavedMealsUiState> = _uiState.asStateFlow()

    init {
        loadSavedMeals()
    }

    /**
     * Load saved meals from Firestore
     */
    fun loadSavedMeals() {
        viewModelScope.launch {
            _uiState.value = SavedMealsUiState.Loading

            try {
                android.util.Log.w("SavedMealsViewModel", "💾💾💾 LOADING SAVED MEALS for userId: $userId 💾💾💾")
                val result = firebaseRepository.getSavedMeals(userId)
                if (result.isSuccess) {
                    val meals = result.getOrNull() ?: emptyList()
                    android.util.Log.w("SavedMealsViewModel", "💾💾💾 FOUND ${meals.size} SAVED MEALS 💾💾💾")
                    meals.forEach { meal ->
                        android.util.Log.w("SavedMealsViewModel", "  - ${meal.name} (id=${meal.id}, userId=${meal.userId})")
                    }
                    _uiState.value = SavedMealsUiState.Success(meals)
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    android.util.Log.e("SavedMealsViewModel", "❌❌❌ ERROR LOADING MEALS: $error ❌❌❌")
                    _uiState.value = SavedMealsUiState.Error(error)
                }
            } catch (e: Exception) {
                android.util.Log.e("SavedMealsViewModel", "❌❌❌ EXCEPTION LOADING MEALS: ${e.message} ❌❌❌", e)
                _uiState.value = SavedMealsUiState.Error("Failed to load saved meals: ${e.message}")
            }
        }
    }

    /**
     * Update meal usage when selected
     */
    fun onMealSelected(meal: SavedMeal) {
        viewModelScope.launch {
            try {
                val updatedMeal = meal.withUpdatedUsage()
                firebaseRepository.updateSavedMeal(updatedMeal)
                // Refresh the list to show updated usage stats
                loadSavedMeals()
            } catch (e: Exception) {
                // Log error but don't fail the selection
                android.util.Log.e("SavedMealsViewModel", "Failed to update meal usage", e)
            }
        }
    }

    /**
     * Delete a saved meal
     */
    fun deleteMeal(meal: SavedMeal) {
        viewModelScope.launch {
            try {
                val result = firebaseRepository.deleteSavedMeal(meal.id)
                if (result.isSuccess) {
                    loadSavedMeals() // Refresh list
                } else {
                    // Could show error to user
                    android.util.Log.e("SavedMealsViewModel", "Failed to delete meal: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                android.util.Log.e("SavedMealsViewModel", "Error deleting meal", e)
            }
        }
    }

    /**
     * Share a saved meal with friends
     * EXACT SAME IMPLEMENTATION AS RECIPE SHARING
     */
    fun shareSavedMealWithFriends(meal: SavedMeal, friendIds: List<String>) {
        viewModelScope.launch {
            try {
                android.util.Log.d("SavedMealsViewModel", "=========================================")
                android.util.Log.d("SavedMealsViewModel", "🔄🔄🔄 SHARING SAVED MEAL 🔄🔄🔄")
                android.util.Log.d("SavedMealsViewModel", "Meal ID: ${meal.id}")
                android.util.Log.d("SavedMealsViewModel", "Meal name: ${meal.name}")
                android.util.Log.d("SavedMealsViewModel", "Current user ID: $userId")
                android.util.Log.d("SavedMealsViewModel", "Meal userId: ${meal.userId}")
                android.util.Log.d("SavedMealsViewModel", "Sharing with ${friendIds.size} friends: $friendIds")
                android.util.Log.d("SavedMealsViewModel", "=========================================")
                
                // Update saved meal with sharing info
                val sharedMeal = meal.copy(
                    isShared = true,
                    sharedWith = friendIds
                )
                
                android.util.Log.d("SavedMealsViewModel", "✅ Created sharedMeal object")
                android.util.Log.d("SavedMealsViewModel", "   sharedMeal.id: ${sharedMeal.id}")
                android.util.Log.d("SavedMealsViewModel", "   sharedMeal.userId: ${sharedMeal.userId}")
                android.util.Log.d("SavedMealsViewModel", "   sharedMeal.sharedWith: ${sharedMeal.sharedWith}")
                
                // Update the original saved meal to mark it as shared
                firebaseRepository.updateSavedMeal(sharedMeal)
                
                // CRITICAL: If meal has a recipe, share the recipe too
                if (sharedMeal.recipeId != null && sharedMeal.recipeId!!.isNotBlank()) {
                    try {
                        // Check subscription tier - recipe sharing is Pro-only
                        val tier = com.coachie.app.data.SubscriptionService.getUserTier(userId)
                        if (tier != com.coachie.app.data.model.SubscriptionTier.PRO) {
                            android.util.Log.w("SavedMealsViewModel", "Recipe sharing blocked: Pro feature only")
                            throw Exception("Recipe sharing is a Pro feature. Upgrade to Pro to share recipes with friends, post to forums, and share to circles.")
                        }
                        
                        // Get the recipe
                        val recipeResult = firebaseRepository.getRecipe(userId, sharedMeal.recipeId!!)
                        recipeResult.fold(
                            onSuccess = { recipe ->
                                if (recipe != null) {
                                    // CRITICAL: Save recipe to user's personal recipes collection FIRST
                                    android.util.Log.d("SavedMealsViewModel", "💾 Saving recipe to personal collection before sharing...")
                                    val saveResult = firebaseRepository.saveRecipe(userId, recipe)
                                    if (saveResult.isFailure) {
                                        android.util.Log.w("SavedMealsViewModel", "Failed to save recipe to personal collection, continuing with share")
                                    } else {
                                        android.util.Log.d("SavedMealsViewModel", "✅ Recipe saved to personal collection")
                                    }
                                    
                                    // Share the recipe with the same friends using the same pattern as RecipeCaptureViewModel
                                    val sharedRecipe = recipe.copy(
                                        isShared = true,
                                        sharedWith = friendIds
                                    )
                                    // Use the same sharing logic as RecipeCaptureViewModel
                                    val recipeData = hashMapOf<String, Any>(
                                        "id" to sharedRecipe.id,
                                        "userId" to sharedRecipe.userId,
                                        "name" to sharedRecipe.name,
                                        "description" to (sharedRecipe.description ?: ""),
                                        "servings" to sharedRecipe.servings,
                                        "ingredients" to sharedRecipe.ingredients.map { ing ->
                                            hashMapOf(
                                                "name" to ing.name,
                                                "quantity" to ing.quantity,
                                                "unit" to ing.unit,
                                                "calories" to ing.calories,
                                                "proteinG" to ing.proteinG,
                                                "carbsG" to ing.carbsG,
                                                "fatG" to ing.fatG,
                                                "sugarG" to ing.sugarG,
                                                "micronutrients" to ing.micronutrients
                                            )
                                        },
                                        "instructions" to (sharedRecipe.instructions ?: emptyList()),
                                        "totalCalories" to sharedRecipe.totalCalories,
                                        "totalProteinG" to sharedRecipe.totalProteinG,
                                        "totalCarbsG" to sharedRecipe.totalCarbsG,
                                        "totalFatG" to sharedRecipe.totalFatG,
                                        "totalSugarG" to sharedRecipe.totalSugarG,
                                        "micronutrients" to sharedRecipe.micronutrients,
                                        "isShared" to true,
                                        "sharedWith" to friendIds,
                                        "createdAt" to sharedRecipe.createdAt
                                    )
                                    com.google.firebase.ktx.Firebase.firestore
                                        .collection("sharedRecipes")
                                        .document(sharedRecipe.id)
                                        .set(recipeData)
                                        .await()
                                    android.util.Log.d("SavedMealsViewModel", "✅ Shared recipe ${recipe.id} with friends")
                                }
                            },
                            onFailure = {
                                android.util.Log.w("SavedMealsViewModel", "Could not load recipe ${sharedMeal.recipeId} for sharing")
                            }
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("SavedMealsViewModel", "Error sharing recipe", e)
                        // Continue with meal sharing even if recipe sharing fails
                    }
                }
                
                // Save to shared saved meals collection
                val mealData = hashMapOf<String, Any>(
                    "id" to sharedMeal.id,
                    "userId" to sharedMeal.userId,
                    "name" to sharedMeal.name,
                    "foodName" to sharedMeal.foodName,
                    "calories" to sharedMeal.calories,
                    "proteinG" to sharedMeal.proteinG,
                    "carbsG" to sharedMeal.carbsG,
                    "fatG" to sharedMeal.fatG,
                    "sugarG" to sharedMeal.sugarG,
                    "addedSugarG" to sharedMeal.addedSugarG,
                    "recipeId" to (sharedMeal.recipeId ?: ""), // CRITICAL: Include recipeId so friends can access recipe
                    "isShared" to true,
                    "sharedWith" to friendIds,
                    "createdAt" to sharedMeal.createdAt
                )

                val firestore = com.google.firebase.ktx.Firebase.firestore
                val sharedMealsRef = firestore.collection("sharedSavedMeals")
                
                val currentAuthUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val currentAuthUserId = currentAuthUser?.uid
                
                android.util.Log.d("SavedMealsViewModel", "=========================================")
                android.util.Log.d("SavedMealsViewModel", "🔄 ATTEMPTING TO SAVE TO FIRESTORE")
                android.util.Log.d("SavedMealsViewModel", "Collection: sharedSavedMeals")
                android.util.Log.d("SavedMealsViewModel", "Document ID: ${sharedMeal.id}")
                android.util.Log.d("SavedMealsViewModel", "Current authenticated user: $currentAuthUserId")
                android.util.Log.d("SavedMealsViewModel", "ViewModel userId: $userId")
                android.util.Log.d("SavedMealsViewModel", "Meal userId: ${sharedMeal.userId}")
                android.util.Log.d("SavedMealsViewModel", "Shared with: $friendIds")
                android.util.Log.d("SavedMealsViewModel", "Meal data keys: ${mealData.keys}")
                android.util.Log.d("SavedMealsViewModel", "Meal data size: ${mealData.size} fields")
                
                // CRITICAL: ALWAYS use the authenticated user's ID, not the ViewModel's userId
                // The ViewModel's userId might be "anonymous_user" or wrong if passed incorrectly
                // Firestore security rule requires: request.auth.uid == request.resource.data.userId
                if (currentAuthUserId == null) {
                    android.util.Log.e("SavedMealsViewModel", "❌❌❌ CRITICAL ERROR: No authenticated user! ❌❌❌")
                    throw Exception("User must be authenticated to share meals")
                }
                
                // ALWAYS set userId to authenticated user's ID (this is what Firestore security rule checks)
                if (mealData["userId"] != currentAuthUserId) {
                    android.util.Log.w("SavedMealsViewModel", "⚠️ WARNING: Meal userId (${mealData["userId"]}) doesn't match authenticated user ($currentAuthUserId)")
                    android.util.Log.w("SavedMealsViewModel", "This happens when ViewModel was created with wrong/null userId")
                    android.util.Log.w("SavedMealsViewModel", "FIXING: Setting meal userId to authenticated user")
                    mealData["userId"] = currentAuthUserId
                    android.util.Log.d("SavedMealsViewModel", "✅ Fixed: meal userId now set to: ${mealData["userId"]}")
                } else {
                    android.util.Log.d("SavedMealsViewModel", "✅ userId matches authenticated user - save should succeed")
                }
                
                android.util.Log.d("SavedMealsViewModel", "=========================================")
                
                // CRITICAL: Use set() (not merge) to ensure sharedWith array is properly updated
                // If we use merge() and the meal already exists with an empty sharedWith,
                // the merge might not properly replace the array
                try {
                    android.util.Log.d("SavedMealsViewModel", "⏱️ Calling Firestore .set() at ${System.currentTimeMillis()}")
                    sharedMealsRef
                        .document(sharedMeal.id)
                        .set(mealData)
                        .await()
                    android.util.Log.d("SavedMealsViewModel", "✅✅✅ Firestore .set() completed successfully ✅✅✅")
                    
                    // Verify the data was saved correctly
                    val savedDoc = sharedMealsRef.document(sharedMeal.id).get().await()
                    if (!savedDoc.exists()) {
                        android.util.Log.e("SavedMealsViewModel", "❌ ERROR: Meal document does not exist after save!")
                        throw Exception("Meal was not saved to Firestore")
                    }
                    
                    val savedData = savedDoc.data
                    val savedSharedWith = savedData?.get("sharedWith") as? List<*>
                    val savedUserId = savedData?.get("userId") as? String
                    
                    android.util.Log.d("SavedMealsViewModel", "✅ Meal saved to Firestore successfully")
                    android.util.Log.d("SavedMealsViewModel", "✅ Meal ID: ${sharedMeal.id}")
                    android.util.Log.d("SavedMealsViewModel", "✅ Saved to collection: sharedSavedMeals/${sharedMeal.id}")
                    android.util.Log.d("SavedMealsViewModel", "✅ VERIFICATION: Saved userId: $savedUserId (expected: ${sharedMeal.userId})")
                    android.util.Log.d("SavedMealsViewModel", "✅ VERIFICATION: Saved sharedWith array: $savedSharedWith (expected: $friendIds)")
                    
                    // CRITICAL VERIFICATION: Check that the data was actually saved correctly
                    if (savedUserId != sharedMeal.userId) {
                        android.util.Log.e("SavedMealsViewModel", "❌ ERROR: userId mismatch! Saved: $savedUserId, Expected: ${sharedMeal.userId}")
                        throw Exception("Meal userId mismatch - save may have failed")
                    }
                    
                    if (savedSharedWith?.size != friendIds.size || !savedSharedWith.containsAll(friendIds)) {
                        android.util.Log.e("SavedMealsViewModel", "❌ ERROR: sharedWith array mismatch!")
                        android.util.Log.e("SavedMealsViewModel", "Saved: $savedSharedWith")
                        android.util.Log.e("SavedMealsViewModel", "Expected: $friendIds")
                        throw Exception("Meal sharedWith array mismatch - save may have failed")
                    }
                    
                    // FINAL VERIFICATION: Query the collection to confirm it's actually there
                    val verificationQuery = sharedMealsRef
                        .whereEqualTo("id", sharedMeal.id)
                        .get()
                        .await()
                    
                    if (verificationQuery.isEmpty) {
                        android.util.Log.e("SavedMealsViewModel", "❌ ERROR: Meal not found in collection after save!")
                        throw Exception("Meal not found in collection after save")
                    }
                    
                    android.util.Log.d("SavedMealsViewModel", "✅✅✅ FINAL VERIFICATION PASSED: Meal exists in sharedSavedMeals collection ✅✅✅")
                    android.util.Log.d("SavedMealsViewModel", "✅ Meal shared successfully: ${sharedMeal.id} with ${friendIds.size} friends")
                    
                    // Update UI state to show success message
                    val friendCount = friendIds.size
                    val successMessage = if (friendCount == 1) {
                        "Meal shared successfully!"
                    } else {
                        "Meal shared with $friendCount friends!"
                    }
                    _uiState.value = SavedMealsUiState.ShareSuccess(successMessage)
                    
                    // Reset to Success state after a brief moment
                    kotlinx.coroutines.delay(100)
                    loadSavedMeals() // Reload to refresh the list
                } catch (saveError: Exception) {
                    android.util.Log.e("SavedMealsViewModel", "❌ ERROR SAVING MEAL TO FIRESTORE", saveError)
                    android.util.Log.e("SavedMealsViewModel", "Error type: ${saveError.javaClass.simpleName}")
                    android.util.Log.e("SavedMealsViewModel", "Error message: ${saveError.message}")
                    saveError.printStackTrace()
                    throw saveError // Re-throw to be caught by outer catch
                }
            } catch (e: Exception) {
                android.util.Log.e("SavedMealsViewModel", "❌❌❌ ERROR SHARING MEAL ❌❌❌", e)
                android.util.Log.e("SavedMealsViewModel", "Error type: ${e.javaClass.simpleName}")
                android.util.Log.e("SavedMealsViewModel", "Error message: ${e.message}")
                android.util.Log.e("SavedMealsViewModel", "Stack trace:")
                e.printStackTrace()
                
                // Show detailed error to user
                val errorMessage = when {
                    e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true -> 
                        "Permission denied. Check Firestore security rules."
                    e.message?.contains("not found", ignoreCase = true) == true -> 
                        "Meal was not saved to Firestore. Check logs for details."
                    else -> 
                        "Failed to share meal: ${e.message ?: "Unknown error"}"
                }
                
                _uiState.value = SavedMealsUiState.Error(errorMessage)
            }
        }
    }

    companion object {
        fun provideFactory(
            firebaseRepository: FirebaseRepository,
            userId: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SavedMealsViewModel(firebaseRepository, userId) as T
            }
        }
    }
}

/**
 * UI states for saved meals screen
 */
sealed class SavedMealsUiState {
    object Loading : SavedMealsUiState()
    data class Success(val meals: List<SavedMeal>) : SavedMealsUiState()
    data class Error(val message: String) : SavedMealsUiState()
    data class ShareSuccess(val message: String) : SavedMealsUiState()
}
