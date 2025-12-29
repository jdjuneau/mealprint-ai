package com.coachie.app.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.data.model.Recipe
import com.mealprint.ai.data.model.RecipeIngredient
import com.mealprint.ai.data.model.SavedMeal
import com.mealprint.ai.data.model.HealthLog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.Base64

sealed class RecipeCaptureUiState {
    object Idle : RecipeCaptureUiState()
    data class PhotoTaken(val photoUri: Uri) : RecipeCaptureUiState()
    data class Analyzing(val progress: String) : RecipeCaptureUiState()
    data class AnalysisResult(
        val recipe: Recipe,
        val photoUri: Uri?
    ) : RecipeCaptureUiState()
    data class Error(val message: String) : RecipeCaptureUiState()
    data class Success(val message: String = "Success") : RecipeCaptureUiState()
}

class RecipeCaptureViewModel(
    private val context: Context,
    private val firebaseRepository: FirebaseRepository,
    private val userId: String // This is only used for logging - we always use authenticated user's ID for actual operations
) : ViewModel() {
    
    // Always get the actual authenticated user's ID
    private val authenticatedUserId: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    private val _uiState = MutableStateFlow<RecipeCaptureUiState>(RecipeCaptureUiState.Idle)
    val uiState: StateFlow<RecipeCaptureUiState> = _uiState.asStateFlow()

    private val functions = Firebase.functions

    fun analyzeRecipeFromImage(imageUri: Uri, servings: Int) {
        viewModelScope.launch {
            try {
                _uiState.value = RecipeCaptureUiState.Analyzing("Extracting recipe from image...")
                
                // Convert image to base64
                val imageBase64 = imageUriToBase64(imageUri)
                
                _uiState.value = RecipeCaptureUiState.Analyzing("Analyzing ingredients and calculating nutrition...")
                
                // Call Cloud Function
                val result = analyzeRecipe(imageBase64, null, servings)
                
                _uiState.value = RecipeCaptureUiState.AnalysisResult(result, imageUri)
            } catch (e: Exception) {
                android.util.Log.e("RecipeCaptureViewModel", "Error analyzing recipe from image", e)
                _uiState.value = RecipeCaptureUiState.Error(e.message ?: "Failed to analyze recipe")
            }
        }
    }

    fun analyzeRecipeFromText(recipeText: String, servings: Int) {
        viewModelScope.launch {
            try {
                _uiState.value = RecipeCaptureUiState.Analyzing("Analyzing ingredients and calculating nutrition...")
                
                // Call Cloud Function
                val result = analyzeRecipe(null, recipeText, servings)
                
                _uiState.value = RecipeCaptureUiState.AnalysisResult(result, null)
            } catch (e: Exception) {
                android.util.Log.e("RecipeCaptureViewModel", "Error analyzing recipe from text", e)
                _uiState.value = RecipeCaptureUiState.Error(e.message ?: "Failed to analyze recipe")
            }
        }
    }

    private suspend fun analyzeRecipe(imageBase64: String?, recipeText: String?, servings: Int): Recipe {
        val data = hashMapOf<String, Any>(
            "servings" to servings
        )
        
        imageBase64?.let { data["imageBase64"] = it as Any }
        recipeText?.let { data["recipeText"] = it as Any }

        val result = functions
            .getHttpsCallable("analyzeRecipe")
            .call(data)
            .await()

        val resultData = result.data as? Map<*, *>
        val recipeData = resultData?.get("recipe") as? Map<*, *>
            ?: throw Exception("Invalid response from recipe analysis")

        // Parse recipe from response
        val ingredients = (recipeData["ingredients"] as? List<*>)?.mapNotNull { ing ->
            val ingMap = ing as? Map<*, *> ?: return@mapNotNull null
            RecipeIngredient(
                name = ingMap["name"] as? String ?: "",
                quantity = (ingMap["quantity"] as? Number)?.toDouble() ?: 0.0,
                unit = ingMap["unit"] as? String ?: "",
                calories = (ingMap["calories"] as? Number)?.toInt() ?: 0,
                proteinG = (ingMap["proteinG"] as? Number)?.toInt() ?: 0,
                carbsG = (ingMap["carbsG"] as? Number)?.toInt() ?: 0,
                fatG = (ingMap["fatG"] as? Number)?.toInt() ?: 0,
                sugarG = (ingMap["sugarG"] as? Number)?.toInt() ?: 0,
                micronutrients = (ingMap["micronutrients"] as? Map<*, *>)?.mapNotNull { entry ->
                    val key = entry.key as? String ?: return@mapNotNull null
                    val value = (entry.value as? Number)?.toDouble() ?: 0.0
                    key to value
                }?.toMap() ?: emptyMap()
            )
        } ?: emptyList()

        val instructions = (recipeData["instructions"] as? List<*>)?.mapNotNull { 
            it as? String 
        } ?: emptyList()

        val micronutrients = (recipeData["micronutrients"] as? Map<*, *>)?.mapNotNull { entry ->
            val key = entry.key as? String ?: return@mapNotNull null
            val value = (entry.value as? Number)?.toDouble() ?: 0.0
            key to value
        }?.toMap() ?: emptyMap()

        // ALWAYS use authenticated user's ID, not the ViewModel's userId parameter
        val actualUserId = authenticatedUserId ?: throw Exception("User must be authenticated to analyze recipes")
        
        return Recipe(
            userId = actualUserId,
            name = recipeData["name"] as? String ?: "Recipe",
            description = recipeData["description"] as? String,
            servings = servings,
            ingredients = ingredients,
            instructions = instructions,
            totalCalories = (recipeData["totalCalories"] as? Number)?.toInt() ?: 0,
            totalProteinG = (recipeData["totalProteinG"] as? Number)?.toInt() ?: 0,
            totalCarbsG = (recipeData["totalCarbsG"] as? Number)?.toInt() ?: 0,
            totalFatG = (recipeData["totalFatG"] as? Number)?.toInt() ?: 0,
            totalSugarG = (recipeData["totalSugarG"] as? Number)?.toInt() ?: 0,
            totalAddedSugarG = (recipeData["totalAddedSugarG"] as? Number)?.toInt() ?: 0,
            micronutrients = micronutrients
        )
    }

    private suspend fun imageUriToBase64(uri: Uri): String {
        // Read image file and convert to base64
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Failed to read image")
        
        val bytes = inputStream.readBytes()
        inputStream.close()
        
        return Base64.getEncoder().encodeToString(bytes)
    }

    companion object {
        fun provideFactory(
            context: Context,
            firebaseRepository: FirebaseRepository,
            userId: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RecipeCaptureViewModel(context, firebaseRepository, userId) as T
            }
        }
    }

    /**
     * Log a meal from a recipe (saves recipe and links it to the meal log)
     */
    fun logMealFromRecipe(recipe: Recipe, servingsConsumed: Double = 1.0) {
        viewModelScope.launch {
            try {
                val authenticatedUserId = authenticatedUserId
                    ?: throw Exception("User must be authenticated to log meals")
                
                val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                
                // Save recipe first
                val recipeResult = firebaseRepository.saveRecipe(authenticatedUserId, recipe)
                val recipeId = if (recipeResult.isSuccess) {
                    recipe.id
                } else {
                    android.util.Log.w("RecipeCaptureViewModel", "Failed to save recipe, continuing without recipe link")
                    null
                }
                
                // Create meal log from recipe
                val mealLog = recipe.toMealLog(servingsConsumed.toInt()).copy(
                    recipeId = recipeId,
                    servingsConsumed = servingsConsumed
                )
                
                // Save meal log
                val result = firebaseRepository.saveHealthLog(authenticatedUserId, today, mealLog)
                
                if (result.isSuccess) {
                    // Also save to quick save
                    val savedMeal = recipe.toSavedMeal(authenticatedUserId)
                    firebaseRepository.saveSavedMeal(savedMeal)
                    
                    _uiState.value = RecipeCaptureUiState.Success("Meal logged and recipe saved!")
                } else {
                    _uiState.value = RecipeCaptureUiState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to log meal"
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("RecipeCaptureViewModel", "Error logging meal from recipe", e)
                _uiState.value = RecipeCaptureUiState.Error(e.message ?: "Failed to log meal")
            }
        }
    }

    fun saveRecipeToQuickSave(recipe: Recipe) {
        viewModelScope.launch {
            try {
                val authenticatedUserId = authenticatedUserId
                    ?: throw Exception("User must be authenticated to save recipes")
                
                // Save recipe to user's recipes collection
                val recipeResult = firebaseRepository.saveRecipe(authenticatedUserId, recipe)
                
                if (recipeResult.isFailure) {
                    android.util.Log.w("RecipeCaptureViewModel", "Failed to save recipe, continuing with quick save")
                }
                
                // Convert recipe to SavedMeal (single serving)
                val savedMeal = recipe.toSavedMeal(authenticatedUserId)
                
                // Save to Firestore
                val result = firebaseRepository.saveSavedMeal(savedMeal)
                
                if (result.isSuccess) {
                    _uiState.value = RecipeCaptureUiState.Success("Recipe saved!")
                } else {
                    _uiState.value = RecipeCaptureUiState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to save recipe"
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("RecipeCaptureViewModel", "Error saving recipe", e)
                _uiState.value = RecipeCaptureUiState.Error(e.message ?: "Failed to save recipe")
            }
        }
    }

    fun shareRecipeWithFriends(recipe: Recipe, friendIds: List<String>) {
        viewModelScope.launch {
            try {
                val authenticatedUserId = authenticatedUserId
                    ?: throw Exception("User must be authenticated to share recipes")
                
                // Check subscription tier - recipe sharing is Pro-only
                val tier = com.coachie.app.data.SubscriptionService.getUserTier(authenticatedUserId)
                if (tier != com.coachie.app.data.model.SubscriptionTier.PRO) {
                    throw Exception("Recipe sharing is a Pro feature. Upgrade to Pro to share recipes with friends, post to forums, and share to circles.")
                }
                
                android.util.Log.d("RecipeCaptureViewModel", "=========================================")
                android.util.Log.d("RecipeCaptureViewModel", "🔄🔄🔄 SHARING RECIPE 🔄🔄🔄")
                android.util.Log.d("RecipeCaptureViewModel", "Recipe ID: ${recipe.id}")
                android.util.Log.d("RecipeCaptureViewModel", "Recipe name: ${recipe.name}")
                android.util.Log.d("RecipeCaptureViewModel", "Current user ID: $userId")
                android.util.Log.d("RecipeCaptureViewModel", "Recipe userId: ${recipe.userId}")
                android.util.Log.d("RecipeCaptureViewModel", "Sharing with ${friendIds.size} friends: $friendIds")
                android.util.Log.d("RecipeCaptureViewModel", "=========================================")
                
                // CRITICAL: Save recipe to user's personal recipes collection FIRST
                android.util.Log.d("RecipeCaptureViewModel", "💾 Saving recipe to personal collection first...")
                val saveResult = firebaseRepository.saveRecipe(authenticatedUserId, recipe)
                if (saveResult.isFailure) {
                    android.util.Log.w("RecipeCaptureViewModel", "Failed to save recipe to personal collection, continuing with share")
                } else {
                    android.util.Log.d("RecipeCaptureViewModel", "✅ Recipe saved to personal collection")
                }
                
                // Save recipe with sharing enabled
                val sharedRecipe = recipe.copy(
                    isShared = true,
                    sharedWith = friendIds
                )
                
                android.util.Log.d("RecipeCaptureViewModel", "✅ Created sharedRecipe object")
                android.util.Log.d("RecipeCaptureViewModel", "   sharedRecipe.id: ${sharedRecipe.id}")
                android.util.Log.d("RecipeCaptureViewModel", "   sharedRecipe.userId: ${sharedRecipe.userId}")
                android.util.Log.d("RecipeCaptureViewModel", "   sharedRecipe.sharedWith: ${sharedRecipe.sharedWith}")
                
                // Save to shared recipes collection
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

                val firestore = com.google.firebase.ktx.Firebase.firestore
                val sharedRecipesRef = firestore.collection("sharedRecipes")
                
                val currentAuthUser = FirebaseAuth.getInstance().currentUser
                val currentAuthUserId = currentAuthUser?.uid
                
                android.util.Log.d("RecipeCaptureViewModel", "=========================================")
                android.util.Log.d("RecipeCaptureViewModel", "🔄 ATTEMPTING TO SAVE TO FIRESTORE")
                android.util.Log.d("RecipeCaptureViewModel", "Collection: sharedRecipes")
                android.util.Log.d("RecipeCaptureViewModel", "Document ID: ${sharedRecipe.id}")
                android.util.Log.d("RecipeCaptureViewModel", "Current authenticated user: $currentAuthUserId")
                android.util.Log.d("RecipeCaptureViewModel", "ViewModel userId: $userId")
                android.util.Log.d("RecipeCaptureViewModel", "Recipe userId: ${sharedRecipe.userId}")
                android.util.Log.d("RecipeCaptureViewModel", "Shared with: $friendIds")
                android.util.Log.d("RecipeCaptureViewModel", "Recipe data keys: ${recipeData.keys}")
                android.util.Log.d("RecipeCaptureViewModel", "Recipe data size: ${recipeData.size} fields")
                
                // CRITICAL: ALWAYS use the authenticated user's ID, not the ViewModel's userId
                // The ViewModel's userId might be "anonymous_user" or wrong if passed incorrectly
                // Firestore security rule requires: request.auth.uid == request.resource.data.userId
                if (currentAuthUserId == null) {
                    android.util.Log.e("RecipeCaptureViewModel", "❌❌❌ CRITICAL ERROR: No authenticated user! ❌❌❌")
                    throw Exception("User must be authenticated to share recipes")
                }
                
                // ALWAYS set userId to authenticated user's ID (this is what Firestore security rule checks)
                if (recipeData["userId"] != currentAuthUserId) {
                    android.util.Log.w("RecipeCaptureViewModel", "⚠️ WARNING: Recipe userId (${recipeData["userId"]}) doesn't match authenticated user ($currentAuthUserId)")
                    android.util.Log.w("RecipeCaptureViewModel", "This happens when ViewModel was created with wrong/null userId")
                    android.util.Log.w("RecipeCaptureViewModel", "FIXING: Setting recipe userId to authenticated user")
                    recipeData["userId"] = currentAuthUserId
                    android.util.Log.d("RecipeCaptureViewModel", "✅ Fixed: recipe userId now set to: ${recipeData["userId"]}")
                } else {
                    android.util.Log.d("RecipeCaptureViewModel", "✅ userId matches authenticated user - save should succeed")
                }
                
                android.util.Log.d("RecipeCaptureViewModel", "=========================================")
                
                // CRITICAL: Use set() (not merge) to ensure sharedWith array is properly updated
                // If we use merge() and the recipe already exists with an empty sharedWith,
                // the merge might not properly replace the array
                try {
                    android.util.Log.d("RecipeCaptureViewModel", "⏱️ Calling Firestore .set() at ${System.currentTimeMillis()}")
                    sharedRecipesRef
                        .document(sharedRecipe.id)
                        .set(recipeData)
                        .await()
                    android.util.Log.d("RecipeCaptureViewModel", "✅✅✅ Firestore .set() completed successfully ✅✅✅")
                    
                    // Verify the data was saved correctly
                    val savedDoc = sharedRecipesRef.document(sharedRecipe.id).get().await()
                    if (!savedDoc.exists()) {
                        android.util.Log.e("RecipeCaptureViewModel", "❌ ERROR: Recipe document does not exist after save!")
                        throw Exception("Recipe was not saved to Firestore")
                    }
                    
                    val savedData = savedDoc.data
                    val savedSharedWith = savedData?.get("sharedWith") as? List<*>
                    val savedUserId = savedData?.get("userId") as? String
                    
                    android.util.Log.d("RecipeCaptureViewModel", "✅ Recipe saved to Firestore successfully")
                    android.util.Log.d("RecipeCaptureViewModel", "✅ Recipe ID: ${sharedRecipe.id}")
                    android.util.Log.d("RecipeCaptureViewModel", "✅ Saved to collection: sharedRecipes/${sharedRecipe.id}")
                    android.util.Log.d("RecipeCaptureViewModel", "✅ VERIFICATION: Saved userId: $savedUserId (expected: ${sharedRecipe.userId})")
                    android.util.Log.d("RecipeCaptureViewModel", "✅ VERIFICATION: Saved sharedWith array: $savedSharedWith (expected: $friendIds)")
                    
                    // CRITICAL VERIFICATION: Check that the data was actually saved correctly
                    if (savedUserId != sharedRecipe.userId) {
                        android.util.Log.e("RecipeCaptureViewModel", "❌ ERROR: userId mismatch! Saved: $savedUserId, Expected: ${sharedRecipe.userId}")
                        throw Exception("Recipe userId mismatch - save may have failed")
                    }
                    
                    if (savedSharedWith?.size != friendIds.size || !savedSharedWith.containsAll(friendIds)) {
                        android.util.Log.e("RecipeCaptureViewModel", "❌ ERROR: sharedWith array mismatch!")
                        android.util.Log.e("RecipeCaptureViewModel", "Saved: $savedSharedWith")
                        android.util.Log.e("RecipeCaptureViewModel", "Expected: $friendIds")
                        throw Exception("Recipe sharedWith array mismatch - save may have failed")
                    }
                    
                    // FINAL VERIFICATION: Query the collection to confirm it's actually there
                    val verificationQuery = sharedRecipesRef
                        .whereEqualTo("id", sharedRecipe.id)
                        .get()
                        .await()
                    
                    if (verificationQuery.isEmpty) {
                        android.util.Log.e("RecipeCaptureViewModel", "❌ ERROR: Recipe not found in collection after save!")
                        throw Exception("Recipe not found in collection after save")
                    }
                    
                    android.util.Log.d("RecipeCaptureViewModel", "✅✅✅ FINAL VERIFICATION PASSED: Recipe exists in sharedRecipes collection ✅✅✅")
                    android.util.Log.d("RecipeCaptureViewModel", "✅ Recipe shared successfully: ${sharedRecipe.id} with ${friendIds.size} friends")
                    
                    _uiState.value = RecipeCaptureUiState.Success("Recipe shared successfully!")
                } catch (saveError: Exception) {
                    android.util.Log.e("RecipeCaptureViewModel", "❌ ERROR SAVING RECIPE TO FIRESTORE", saveError)
                    android.util.Log.e("RecipeCaptureViewModel", "Error type: ${saveError.javaClass.simpleName}")
                    android.util.Log.e("RecipeCaptureViewModel", "Error message: ${saveError.message}")
                    saveError.printStackTrace()
                    throw saveError // Re-throw to be caught by outer catch
                }
            } catch (e: Exception) {
                android.util.Log.e("RecipeCaptureViewModel", "❌❌❌ ERROR SHARING RECIPE ❌❌❌", e)
                android.util.Log.e("RecipeCaptureViewModel", "Error type: ${e.javaClass.simpleName}")
                android.util.Log.e("RecipeCaptureViewModel", "Error message: ${e.message}")
                android.util.Log.e("RecipeCaptureViewModel", "Stack trace:")
                e.printStackTrace()
                
                // Show detailed error to user
                val errorMessage = when {
                    e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true -> 
                        "Permission denied. Check Firestore security rules."
                    e.message?.contains("not found", ignoreCase = true) == true -> 
                        "Recipe was not saved to Firestore. Check logs for details."
                    else -> 
                        "Failed to share recipe: ${e.message ?: "Unknown error"}"
                }
                
                _uiState.value = RecipeCaptureUiState.Error(errorMessage)
            }
        }
    }

    fun reset() {
        _uiState.value = RecipeCaptureUiState.Idle
    }

    /**
     * Post recipe to a forum
     */
    fun postRecipeToForum(recipe: Recipe, forumId: String, postTitle: String, postContent: String) {
        viewModelScope.launch {
            try {
                // First share the recipe (so it can be accessed via recipeId)
                val sharedRecipe = recipe.copy(
                    isShared = true,
                    sharedWith = emptyList() // Forum posts are public to all users
                )
                
                // Save to shared recipes collection
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
                    "sharedWith" to emptyList<String>(),
                    "createdAt" to sharedRecipe.createdAt
                )

                com.google.firebase.ktx.Firebase.firestore
                    .collection("sharedRecipes")
                    .document(sharedRecipe.id)
                    .set(recipeData)
                    .await()

                // Now create forum post with recipeId
                val repository = com.coachie.app.data.FirebaseRepository.getInstance()
                val userProfile = repository.getUserProfile(userId).getOrNull()
                val authorName = userProfile?.name ?: userProfile?.username ?: "Anonymous"
                
                val forumPost = com.coachie.app.data.model.ForumPost(
                    title = postTitle,
                    content = postContent,
                    authorId = userId,
                    authorName = authorName,
                    forumId = forumId,
                    recipeId = sharedRecipe.id
                )

                val result = repository.createForumPost(forumId, forumPost)
                result.onSuccess {
                    _uiState.value = RecipeCaptureUiState.Success()
                }.onFailure { error ->
                    _uiState.value = RecipeCaptureUiState.Error("Failed to post to forum: ${error.message}")
                }
            } catch (e: Exception) {
                android.util.Log.e("RecipeCaptureViewModel", "Error posting recipe to forum", e)
                _uiState.value = RecipeCaptureUiState.Error(e.message ?: "Failed to post recipe")
            }
        }
    }
}

