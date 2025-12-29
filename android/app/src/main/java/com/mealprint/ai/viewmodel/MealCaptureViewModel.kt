package com.coachie.app.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.data.ai.FoodBarcodeParser
import com.mealprint.ai.data.ai.GeminiVisionClient
import com.mealprint.ai.data.ai.MealAnalysis
import com.mealprint.ai.data.local.PreferencesManager
import com.mealprint.ai.data.model.HealthLog
import com.mealprint.ai.data.model.MicronutrientType
import com.mealprint.ai.data.model.SavedMeal
import com.mealprint.ai.data.model.toPersistedMicronutrientMap
import com.mealprint.ai.util.MicronutrientEstimator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.tasks.await
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * ViewModel for meal capture screen
 */
class MealCaptureViewModel(
    private val context: Context,
    private val firebaseRepository: FirebaseRepository,
    private val preferencesManager: PreferencesManager,
    private val userId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<MealCaptureUiState>(MealCaptureUiState.Idle)
    val uiState: StateFlow<MealCaptureUiState> = _uiState.asStateFlow()

    private val _hasCameraPermission = MutableStateFlow(false)
    val hasCameraPermission: StateFlow<Boolean> = _hasCameraPermission.asStateFlow()

    private val _useImperial = MutableStateFlow(true) // Default to imperial
    val useImperial: StateFlow<Boolean> = _useImperial.asStateFlow()

    private val _gender = MutableStateFlow<String?>(null)
    val gender: StateFlow<String?> = _gender.asStateFlow()

    private val _calculatedMicronutrients = MutableStateFlow<Map<MicronutrientType, Double>>(emptyMap())

    private val _associatedRecipe = MutableStateFlow<com.coachie.app.data.model.Recipe?>(null)
    val associatedRecipe: StateFlow<com.coachie.app.data.model.Recipe?> = _associatedRecipe.asStateFlow()

    private val _servings = MutableStateFlow(1.0)
    val servings: StateFlow<Double> = _servings.asStateFlow()

    private val _saveSuccessMessage = MutableStateFlow<String?>(null)
    val saveSuccessMessage: StateFlow<String?> = _saveSuccessMessage.asStateFlow()

    private var currentPhotoFile: File? = null
    private val geminiVisionClient = GeminiVisionClient(context)

    init {
        checkCameraPermission()
        loadUserPreferences()
    }

    /**
     * Check current camera permission status
     */
    fun checkCameraPermission() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        _hasCameraPermission.value = granted
    }

    /**
     * Update camera permission status
     */
    fun updateCameraPermission(granted: Boolean) {
        _hasCameraPermission.value = granted
    }

    /**
     * Create a photo file for camera capture
     */
    fun createPhotoFile(context: Context): Uri? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val imageFileName = "MEAL_${timeStamp}_"
            val storageDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            val imageFile = File.createTempFile(imageFileName, ".jpg", storageDir)
            currentPhotoFile = imageFile

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
        } catch (e: Exception) {
            android.util.Log.e("MealCaptureViewModel", "Error creating photo file", e)
            null
        }
    }

    /**
     * Called when photo is taken
     */
    fun onPhotoTaken() {
        currentPhotoFile?.let { file ->
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            _uiState.value = MealCaptureUiState.PhotoTaken(uri)
        }
    }

    /**
     * Scan barcode from image and lookup food product
     */
    fun scanBarcode(imageUri: Uri) {
        viewModelScope.launch {
            _uiState.value = MealCaptureUiState.Analyzing
            android.util.Log.d("MealCaptureViewModel", "Starting barcode scan for URI: $imageUri")

            try {
                val barcodeResult = FoodBarcodeParser.parse(context, imageUri)

                if (barcodeResult != null) {
                    android.util.Log.i("MealCaptureViewModel", "Barcode scan successful: ${barcodeResult.name}, ${barcodeResult.calories} cal")
                    
                    // Convert barcode result to a meal item
                    val mealItem = MealItem(
                        foodName = barcodeResult.name ?: "Scanned Food",
                        calories = barcodeResult.calories.roundToInt(),
                        protein = barcodeResult.protein.roundToInt(),
                        carbs = barcodeResult.carbs.roundToInt(),
                        fat = barcodeResult.fat.roundToInt(),
                        sugar = barcodeResult.sugar.roundToInt(),
                        addedSugar = barcodeResult.addedSugar.roundToInt()
                    )

                    // Set manual entry mode with the barcode data pre-filled
                    _uiState.value = MealCaptureUiState.ManualEntry(
                        foodName = barcodeResult.name,
                        calories = barcodeResult.calories.roundToInt().toString(),
                        protein = barcodeResult.protein.toString(),
                        carbs = barcodeResult.carbs.toString(),
                        fat = barcodeResult.fat.toString(),
                        sugar = if (barcodeResult.sugar > 0.0) String.format("%.1f", barcodeResult.sugar) else "",
                        addedSugar = if (barcodeResult.addedSugar > 0.0) String.format("%.1f", barcodeResult.addedSugar) else "",
                        mealItems = listOf(mealItem)
                    )
                } else {
                    android.util.Log.e("MealCaptureViewModel", "Barcode scan returned null result")
                    _uiState.value = MealCaptureUiState.Error("Failed to scan barcode - please try again")
                }
            } catch (e: Exception) {
                android.util.Log.e("MealCaptureViewModel", "Unexpected error during barcode scan", e)
                val userFriendlyMessage = when {
                    e.message?.contains("network", ignoreCase = true) == true ->
                        "Network error - please check your internet connection"
                    else ->
                        "Barcode scan failed: ${e.localizedMessage ?: "Unknown error"}"
                }
                _uiState.value = MealCaptureUiState.Error(userFriendlyMessage)
            }
        }
    }

    /**
     * Analyze food image with Gemini Vision
     */
    fun analyzeFoodImage(imageUri: Uri) {
        viewModelScope.launch {
            // Validate userId before analysis
            val authenticatedUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (authenticatedUserId == null || authenticatedUserId.isBlank()) {
                android.util.Log.e("MealCaptureViewModel", "Cannot analyze meal: user not authenticated")
                _uiState.value = MealCaptureUiState.Error("Please log in to analyze meal photos")
                return@launch
            }
            
            _uiState.value = MealCaptureUiState.Analyzing
            android.util.Log.d("MealCaptureViewModel", "Starting meal image analysis for URI: $imageUri, userId: $authenticatedUserId")

            try {
                val result = geminiVisionClient.analyzeMealImage(imageUri, authenticatedUserId)

                if (result.isSuccess) {
                    val analysis = result.getOrNull()
                    val currentState = _uiState.value
                    val photoUri = if (currentState is MealCaptureUiState.PhotoTaken) {
                        currentState.photoUri
                    } else {
                        imageUri
                    }

                    if (analysis != null) {
                        android.util.Log.i("MealCaptureViewModel", "Meal analysis successful: ${analysis.food}, ${analysis.calories} cal, confidence: ${analysis.confidence}")
                        
                        // If it's a menu item, automatically search for nutrition information
                        if (analysis.isMenuItem && analysis.menuItemName != null) {
                            android.util.Log.d("MealCaptureViewModel", "Menu item detected, searching for nutrition information...")
                            searchMenuItemNutrition(analysis, photoUri)
                        } else {
                            _uiState.value = MealCaptureUiState.AnalysisResult(analysis, photoUri, emptyList())
                        }
                    } else {
                        android.util.Log.e("MealCaptureViewModel", "Meal analysis returned null result")
                        _uiState.value = MealCaptureUiState.Error("Failed to analyze image - no results returned")
                    }
                } else {
                    val exception = result.exceptionOrNull()
                    val errorMessage = exception?.message ?: "Unknown error during analysis"
                    android.util.Log.e("MealCaptureViewModel", "Meal analysis failed: $errorMessage", exception)

                    // Provide user-friendly error messages based on the type of error
                    val userFriendlyMessage = when {
                        errorMessage.contains("API key", ignoreCase = true) ->
                            "AI analysis unavailable - please check API configuration"
                        errorMessage.contains("network", ignoreCase = true) ->
                            "Network error - please check your internet connection"
                        errorMessage.contains("image", ignoreCase = true) ->
                            "Unable to process the image - please try a different photo"
                        errorMessage.contains("quota", ignoreCase = true) ->
                            "AI analysis temporarily unavailable - please try again later"
                        else ->
                            "Analysis failed: $errorMessage"
                    }

                    _uiState.value = MealCaptureUiState.Error(userFriendlyMessage)
                }
            } catch (e: Exception) {
                android.util.Log.e("MealCaptureViewModel", "Unexpected error during meal analysis", e)
                _uiState.value = MealCaptureUiState.Error("Unexpected error: ${e.message}")
            }
        }
    }

    /**
     * Search for nutrition information for a menu item
     */
    private fun searchMenuItemNutrition(analysis: MealAnalysis, photoUri: Uri?) {
        viewModelScope.launch {
            if (!analysis.isMenuItem || analysis.menuItemName == null) {
                android.util.Log.w("MealCaptureViewModel", "Cannot search nutrition - not a menu item")
                return@launch
            }

            android.util.Log.d("MealCaptureViewModel", "Searching for nutrition: ${analysis.restaurantName} - ${analysis.menuItemName}")

            try {
                val result = geminiVisionClient.searchMenuItemNutrition(
                    restaurantName = analysis.restaurantName,
                    menuItemName = analysis.menuItemName,
                    menuDescription = analysis.menuDescription
                )

                if (result.isSuccess) {
                    val nutritionAnalysis = result.getOrNull()
                    if (nutritionAnalysis != null) {
                        android.util.Log.i("MealCaptureViewModel", "Found nutrition info: ${nutritionAnalysis.calories} cal (original was ${analysis.calories} cal)")
                        // IMPORTANT: Use nutritionAnalysis and ensure mealItems is empty to avoid doubling
                        _uiState.value = MealCaptureUiState.AnalysisResult(nutritionAnalysis, photoUri, emptyList())
                    } else {
                        android.util.Log.w("MealCaptureViewModel", "Nutrition search returned null, using original analysis")
                        _uiState.value = MealCaptureUiState.AnalysisResult(analysis, photoUri, emptyList())
                    }
                } else {
                    val exception = result.exceptionOrNull()
                    android.util.Log.w("MealCaptureViewModel", "Nutrition search failed: ${exception?.message}, using original analysis")
                    _uiState.value = MealCaptureUiState.AnalysisResult(analysis, photoUri, emptyList())
                }
            } catch (e: Exception) {
                android.util.Log.e("MealCaptureViewModel", "Error searching for menu item nutrition", e)
                _uiState.value = MealCaptureUiState.AnalysisResult(analysis, photoUri, emptyList())
            }
        }
    }

    /**
     * Manually trigger nutrition search for a menu item (called from UI)
     */
    fun searchNutritionForMenuItem(analysis: MealAnalysis, photoUri: Uri?) {
        viewModelScope.launch {
            if (!analysis.isMenuItem || analysis.menuItemName == null) {
                return@launch
            }

            _uiState.value = MealCaptureUiState.SearchingNutrition(analysis)
            
            try {
                val result = geminiVisionClient.searchMenuItemNutrition(
                    restaurantName = analysis.restaurantName,
                    menuItemName = analysis.menuItemName,
                    menuDescription = analysis.menuDescription
                )

                if (result.isSuccess) {
                    val nutritionAnalysis = result.getOrNull()
                    if (nutritionAnalysis != null) {
                        _uiState.value = MealCaptureUiState.AnalysisResult(nutritionAnalysis, photoUri, emptyList())
                    } else {
                        _uiState.value = MealCaptureUiState.AnalysisResult(analysis, photoUri, emptyList())
                    }
                } else {
                    _uiState.value = MealCaptureUiState.AnalysisResult(analysis, photoUri, emptyList())
                }
            } catch (e: Exception) {
                android.util.Log.e("MealCaptureViewModel", "Error searching nutrition", e)
                _uiState.value = MealCaptureUiState.AnalysisResult(analysis, photoUri, emptyList())
            }
        }
    }

    /**
     * Set manual entry mode
     */
    fun setManualEntryMode() {
        _uiState.value = MealCaptureUiState.ManualEntry(
            foodName = "",
            calories = "",
            protein = "",
            carbs = "",
            fat = "",
            sugar = "",
            addedSugar = "",
            mealItems = emptyList()
        )
        resetCalculatedMicronutrients()
    }
    
    /**
     * Load a saved meal into the UI (ManualEntry state) so user can see/edit before saving
     * Also checks if there's an associated recipe and loads it
     */
    fun loadSavedMeal(savedMeal: SavedMeal, servingSize: Double = 1.0) {
        viewModelScope.launch {
            try {
                android.util.Log.d("MealCaptureViewModel", "Loading saved meal: ${savedMeal.name} with serving size: $servingSize")
                
                // Scale all nutrition values by serving size
                val scaledCalories = (savedMeal.calories * servingSize).roundToInt()
                val scaledProtein = (savedMeal.proteinG * servingSize).roundToInt()
                val scaledCarbs = (savedMeal.carbsG * servingSize).roundToInt()
                val scaledFat = (savedMeal.fatG * servingSize).roundToInt()
                val scaledSugar = (savedMeal.sugarG * servingSize).roundToInt()
                val scaledAddedSugar = (savedMeal.addedSugarG * servingSize).roundToInt()
                
                // Scale micronutrients
                val scaledMicronutrients = savedMeal.micronutrients.mapNotNull { (key, value) ->
                    try {
                        MicronutrientType.valueOf(key.uppercase()) to (value * servingSize)
                    } catch (e: Exception) {
                        null
                    }
                }.toMap()
                
                // Set calculated micronutrients (scaled)
                _calculatedMicronutrients.value = scaledMicronutrients
                
                // Load into ManualEntry state so user can see/edit (with scaled values)
                _uiState.value = MealCaptureUiState.ManualEntry(
                    foodName = savedMeal.foodName,
                    calories = scaledCalories.toString(),
                    protein = scaledProtein.toString(),
                    carbs = scaledCarbs.toString(),
                    fat = scaledFat.toString(),
                    sugar = scaledSugar.toString(),
                    addedSugar = scaledAddedSugar.toString(),
                    mealItems = emptyList() // Saved meals are single items
                )
                
                android.util.Log.d("MealCaptureViewModel", "✅ Loaded saved meal into ManualEntry state")
                android.util.Log.d("MealCaptureViewModel", "   Food: ${savedMeal.foodName}")
                android.util.Log.d("MealCaptureViewModel", "   Serving size: $servingSize")
                android.util.Log.d("MealCaptureViewModel", "   Calories: $scaledCalories (was ${savedMeal.calories})")
                android.util.Log.d("MealCaptureViewModel", "   Macros: P=${scaledProtein}g, C=${scaledCarbs}g, F=${scaledFat}g")
                android.util.Log.d("MealCaptureViewModel", "   Micronutrients: ${scaledMicronutrients.size} types")
                android.util.Log.d("MealCaptureViewModel", "   RecipeId: ${savedMeal.recipeId}")
                
                // Set servings to the selected serving size
                _servings.value = servingSize
                
                // CRITICAL: Load recipe using recipeId if it exists
                savedMeal.recipeId?.let { recipeId ->
                    android.util.Log.d("MealCaptureViewModel", "🔍 Loading recipe by ID: $recipeId")
                    try {
                        // Try personal recipes first
                        val recipeResult = firebaseRepository.getRecipe(userId, recipeId)
                        recipeResult.fold(
                            onSuccess = { recipe ->
                                if (recipe != null) {
                                    android.util.Log.d("MealCaptureViewModel", "✅ Found recipe in personal collection: ${recipe.name}")
                                    android.util.Log.d("MealCaptureViewModel", "   Recipe has ${recipe.ingredients.size} ingredients")
                                    _associatedRecipe.value = recipe
                                } else {
                                    // Try shared recipes
                                    val sharedResult = firebaseRepository.getSharedRecipe(recipeId)
                                    sharedResult.fold(
                                        onSuccess = { sharedRecipe ->
                                            android.util.Log.d("MealCaptureViewModel", "✅ Found recipe in shared collection: ${sharedRecipe.name}")
                                            android.util.Log.d("MealCaptureViewModel", "   Recipe has ${sharedRecipe.ingredients.size} ingredients")
                                            _associatedRecipe.value = sharedRecipe
                                        },
                                        onFailure = {
                                            android.util.Log.w("MealCaptureViewModel", "❌ Recipe not found in personal or shared collections: $recipeId")
                                            _associatedRecipe.value = null
                                        }
                                    )
                                }
                            },
                            onFailure = {
                                // Try shared recipes
                                val sharedResult = firebaseRepository.getSharedRecipe(recipeId)
                                sharedResult.fold(
                                    onSuccess = { sharedRecipe ->
                                        android.util.Log.d("MealCaptureViewModel", "✅ Found recipe in shared collection: ${sharedRecipe.name}")
                                        android.util.Log.d("MealCaptureViewModel", "   Recipe has ${sharedRecipe.ingredients.size} ingredients")
                                        _associatedRecipe.value = sharedRecipe
                                    },
                                    onFailure = {
                                        android.util.Log.w("MealCaptureViewModel", "❌ Recipe not found in personal or shared collections: $recipeId")
                                        _associatedRecipe.value = null
                                    }
                                )
                            }
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("MealCaptureViewModel", "Error loading recipe by ID", e)
                        _associatedRecipe.value = null
                    }
                } ?: run {
                    // Fallback: Check if there's an associated recipe in sharedRecipes by name matching
                    android.util.Log.d("MealCaptureViewModel", "No recipeId found, trying name matching")
                    try {
                        val recipesResult = firebaseRepository.getSharedRecipes(userId)
                        if (recipesResult.isSuccess) {
                            val recipes = recipesResult.getOrNull() ?: emptyList()
                            val foundRecipe = recipes.find { 
                                it.name.equals(savedMeal.name, ignoreCase = true) || 
                                it.name.equals(savedMeal.foodName, ignoreCase = true)
                            }
                            
                            if (foundRecipe != null) {
                                android.util.Log.d("MealCaptureViewModel", "✅ Found associated recipe by name: ${foundRecipe.name}")
                                android.util.Log.d("MealCaptureViewModel", "   Recipe has ${foundRecipe.ingredients.size} ingredients")
                                _associatedRecipe.value = foundRecipe
                            } else {
                                android.util.Log.d("MealCaptureViewModel", "No associated recipe found for: ${savedMeal.name}")
                                _associatedRecipe.value = null
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("MealCaptureViewModel", "Could not check for associated recipe", e)
                        _associatedRecipe.value = null
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MealCaptureViewModel", "Error loading saved meal", e)
                _uiState.value = MealCaptureUiState.Error("Failed to load saved meal: ${e.message}")
            }
        }
    }

    /**
     * Add current food item to meal list
     */
    fun addFoodToMeal() {
        val current = _uiState.value
        if (current is MealCaptureUiState.ManualEntry) {
            val newItem = createMealItemFromInputs(current) ?: return
            val updatedItems = current.mealItems + newItem
            _uiState.value = current.copy(
                mealItems = updatedItems,
                foodName = "",
                calories = "",
                protein = "",
                carbs = "",
                fat = "",
                sugar = "",
                addedSugar = ""
            )
            resetCalculatedMicronutrients()
        }
    }

    /**
     * Remove a food item from meal list
     */
    fun removeFoodFromMeal(index: Int) {
        val current = _uiState.value
        if (current is MealCaptureUiState.ManualEntry) {
            val updatedItems = current.mealItems.toMutableList()
            if (index in updatedItems.indices) {
                updatedItems.removeAt(index)
                _uiState.value = current.copy(mealItems = updatedItems)
            }
        }
    }

    /**
     * Update manual entry fields
     */
    fun updateFoodName(name: String) {
        val current = _uiState.value
        if (current is MealCaptureUiState.ManualEntry) {
            _uiState.value = current.copy(foodName = name)
        }
    }

    fun updateCalories(calories: String) {
        val current = _uiState.value
        if (current is MealCaptureUiState.ManualEntry) {
            _uiState.value = current.copy(calories = calories)
        }
    }

    fun updateProtein(protein: String) {
        val current = _uiState.value
        if (current is MealCaptureUiState.ManualEntry) {
            _uiState.value = current.copy(protein = protein)
        }
    }

    fun updateCarbs(carbs: String) {
        val current = _uiState.value
        if (current is MealCaptureUiState.ManualEntry) {
            _uiState.value = current.copy(carbs = carbs)
        }
    }

    fun updateFat(fat: String) {
        val current = _uiState.value
        if (current is MealCaptureUiState.ManualEntry) {
            _uiState.value = current.copy(fat = fat)
        }
    }

    fun updateSugar(sugar: String) {
        val current = _uiState.value
        if (current is MealCaptureUiState.ManualEntry) {
            _uiState.value = current.copy(sugar = sugar)
        }
    }

    fun updateAddedSugar(addedSugar: String) {
        val current = _uiState.value
        if (current is MealCaptureUiState.ManualEntry) {
            _uiState.value = current.copy(addedSugar = addedSugar)
        }
    }

    fun updateCalculatedMicronutrients(map: Map<MicronutrientType, Double>) {
        _calculatedMicronutrients.value = map.filterValues { it > 0.0 }
    }

    fun updateServings(servings: Double) {
        _servings.value = servings.coerceIn(0.25, 8.0)
    }

    private fun resetCalculatedMicronutrients() {
        _calculatedMicronutrients.value = emptyMap()
    }

    private fun createMealItemFromInputs(entry: MealCaptureUiState.ManualEntry): MealItem? {
        val calories = entry.calories.toIntOrNull() ?: 0
        val protein = entry.protein.toDoubleOrNull()?.roundToInt() ?: 0
        val carbs = entry.carbs.toDoubleOrNull()?.roundToInt() ?: 0
        val fat = entry.fat.toDoubleOrNull()?.roundToInt() ?: 0
        var sugar = entry.sugar.toDoubleOrNull()?.roundToInt() ?: 0
        var addedSugar = entry.addedSugar.toDoubleOrNull()?.roundToInt() ?: 0
        
        // Estimate sugar if not provided but carbs are present
        if (sugar == 0 && carbs > 0 && entry.sugar.isBlank()) {
            sugar = estimateSugarFromCarbs(carbs.toDouble(), entry.foodName).roundToInt()
        }
        var micronutrients = _calculatedMicronutrients.value
        if (micronutrients.isEmpty()) {
            micronutrients = MicronutrientEstimator.estimate(
                foodName = entry.foodName,
                calories = calories.toDouble(),
                protein = protein.toDouble(),
                carbs = carbs.toDouble(),
                fat = fat.toDouble()
            )
        }

        val hasMicronutrients = micronutrients.isNotEmpty()
        val hasMacros = calories > 0 || protein > 0 || carbs > 0 || fat > 0 || sugar > 0 || addedSugar > 0
        val hasName = entry.foodName.isNotBlank()

        if (!hasName && !hasMacros && !hasMicronutrients) {
            return null
        }

        val mealItem = MealItem(
            foodName = entry.foodName.ifBlank { "Manual Entry" },
            calories = calories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            sugar = sugar,
            addedSugar = addedSugar,
            micronutrients = micronutrients
        )
        
        // Debug logging for sugar and micronutrients in MealItem
        android.util.Log.d("MealCaptureViewModel", "🍭 Created MealItem: ${mealItem.foodName}")
        android.util.Log.d("MealCaptureViewModel", "  Sugar: ${mealItem.sugar}g, AddedSugar: ${mealItem.addedSugar}g")
        android.util.Log.d("MealCaptureViewModel", "  Macros: C=${mealItem.calories}, P=${mealItem.protein}g, C=${mealItem.carbs}g, F=${mealItem.fat}g")
        android.util.Log.d("MealCaptureViewModel", "  Micronutrients: ${mealItem.micronutrients.size} types")
        mealItem.micronutrients.forEach { (type, amount) ->
            android.util.Log.d("MealCaptureViewModel", "    ${type.displayName}: ${amount}${type.unit.displaySuffix}")
        }
        
        return mealItem
    }

    private fun buildMealLogFromItems(items: List<MealItem>): HealthLog.MealLog {
        val totalMicronutrients = mutableMapOf<MicronutrientType, Double>()
        items.forEach { item ->
            android.util.Log.d("MealCaptureViewModel", "🔬 Processing item: ${item.foodName}, micronutrients: ${item.micronutrients.size} types")
            item.micronutrients.forEach { (type, amount) ->
                android.util.Log.d("MealCaptureViewModel", "  ${type.displayName}: ${amount}${type.unit.displaySuffix}")
                totalMicronutrients[type] = (totalMicronutrients[type] ?: 0.0) + amount
            }
        }
        val filteredMicronutrients = totalMicronutrients.filterValues { it > 0.0 }
        
        android.util.Log.d("MealCaptureViewModel", "🔬 Total micronutrients after aggregation: ${filteredMicronutrients.size} types")
        filteredMicronutrients.forEach { (type, amount) ->
            android.util.Log.d("MealCaptureViewModel", "  ${type.displayName}: ${amount}${type.unit.displaySuffix}")
        }

        // Build food names with quantity and type information
        val foodNames = items.map { item ->
            buildString {
                append(item.foodName)
                if (item.quantity.isNotBlank() || item.type.isNotBlank()) {
                    append(" (")
                    if (item.quantity.isNotBlank()) {
                        append(item.quantity)
                    }
                    if (item.quantity.isNotBlank() && item.type.isNotBlank()) {
                        append(", ")
                    }
                    if (item.type.isNotBlank()) {
                        append(item.type)
                    }
                    append(")")
                }
            }
        }
        val combinedFoodName = if (foodNames.size == 1) {
            foodNames.first()
        } else {
            foodNames.joinToString(", ")
        }

        val totalSugar = items.sumOf { it.sugar }
        val totalAddedSugar = items.sumOf { it.addedSugar }
        
        // Debug logging for sugar in MealLog
        android.util.Log.d("MealCaptureViewModel", "🍭 Building MealLog from ${items.size} items:")
        items.forEachIndexed { index, item ->
            android.util.Log.d("MealCaptureViewModel", "  Item $index: ${item.foodName}, sugar=${item.sugar}g, addedSugar=${item.addedSugar}g")
        }
        android.util.Log.d("MealCaptureViewModel", "  Total sugar: ${totalSugar}g, Total addedSugar: ${totalAddedSugar}g")
        
        return HealthLog.MealLog(
            foodName = combinedFoodName,
            calories = items.sumOf { it.calories },
            protein = items.sumOf { it.protein },
            carbs = items.sumOf { it.carbs },
            fat = items.sumOf { it.fat },
            sugar = totalSugar, // CRITICAL: Sum sugar from all items
            addedSugar = totalAddedSugar, // CRITICAL: Sum addedSugar from all items
            micronutrients = filteredMicronutrients.toPersistedMicronutrientMap()
        )
    }

    fun saveSingleMealEntry() {
        val current = _uiState.value
        if (current is MealCaptureUiState.ManualEntry) {
            // If we have an associated recipe, use it to create the meal log with servings
            val recipe = _associatedRecipe.value
            val servings = _servings.value
            
            val mealLog = if (recipe != null) {
                // Use recipe to create meal log with servings
                recipe.toMealLog(servings.toInt()).copy(
                    recipeId = recipe.id,
                    servingsConsumed = servings
                )
            } else {
                // Fall back to building from items
                val mealItem = createMealItemFromInputs(current) ?: return
                buildMealLogFromItems(listOf(mealItem))
            }
            saveMealLog(mealLog)
            resetCalculatedMicronutrients()
        }
    }

    /**
     * Edit analysis result (switch to manual entry with pre-filled data)
     */
    fun editAnalysisResult() {
        val current = _uiState.value
        if (current is MealCaptureUiState.AnalysisResult) {
            _uiState.value = MealCaptureUiState.ManualEntry(
                foodName = current.analysis.food,
                calories = current.analysis.calories.toString(),
                protein = current.analysis.proteinG.toString(),
                carbs = current.analysis.carbsG.toString(),
                fat = current.analysis.fatG.toString(),
                sugar = "",
                addedSugar = "",
                mealItems = emptyList()
            )
            resetCalculatedMicronutrients()
        }
    }
    
    /**
     * Update analysis result food name
     */
    fun updateAnalysisFood(food: String) {
        val current = _uiState.value
        if (current is MealCaptureUiState.AnalysisResult) {
            _uiState.value = current.copy(
                analysis = current.analysis.copy(food = food)
            )
        }
    }
    
    /**
     * Update analysis result calories
     */
    fun updateAnalysisCalories(calories: Int) {
        val current = _uiState.value
        if (current is MealCaptureUiState.AnalysisResult) {
            _uiState.value = current.copy(
                analysis = current.analysis.copy(calories = calories)
            )
        }
    }
    
    /**
     * Update analysis result protein
     */
    fun updateAnalysisProtein(protein: Int) {
        val current = _uiState.value
        if (current is MealCaptureUiState.AnalysisResult) {
            _uiState.value = current.copy(
                analysis = current.analysis.copy(proteinG = protein)
            )
        }
    }
    
    /**
     * Update analysis result carbs
     */
    fun updateAnalysisCarbs(carbs: Int) {
        val current = _uiState.value
        if (current is MealCaptureUiState.AnalysisResult) {
            _uiState.value = current.copy(
                analysis = current.analysis.copy(carbsG = carbs)
            )
        }
    }
    
    /**
     * Update analysis result fat
     */
    fun updateAnalysisFat(fat: Int) {
        val current = _uiState.value
        if (current is MealCaptureUiState.AnalysisResult) {
            _uiState.value = current.copy(
                analysis = current.analysis.copy(fatG = fat)
            )
        }
    }

    /**
     * Convert AI analysis result into the first meal item and continue in manual mode.
     */
    fun addAnalysisResultToMeal(analysis: MealAnalysis) {
        val current = _uiState.value
        val existingItems = if (current is MealCaptureUiState.AnalysisResult) {
            // Convert current analysis to meal item and add existing items
            val currentItem = MealItem(
                foodName = current.analysis.food,
                calories = current.analysis.calories,
                protein = current.analysis.proteinG,
                carbs = current.analysis.carbsG,
                fat = current.analysis.fatG,
                sugar = current.analysis.sugarG,
                addedSugar = current.analysis.addedSugarG,
                micronutrients = MicronutrientEstimator.estimate(
                    foodName = current.analysis.food,
                    calories = current.analysis.calories.toDouble(),
                    protein = current.analysis.proteinG.toDouble(),
                    carbs = current.analysis.carbsG.toDouble(),
                    fat = current.analysis.fatG.toDouble()
                )
            )
            listOf(currentItem) + current.mealItems
        } else {
            emptyList()
        }
        
        val mealItem = MealItem(
            foodName = analysis.food,
            calories = analysis.calories,
            protein = analysis.proteinG,
            carbs = analysis.carbsG,
            fat = analysis.fatG,
            sugar = analysis.sugarG,
            addedSugar = analysis.addedSugarG,
            micronutrients = MicronutrientEstimator.estimate(
                foodName = analysis.food,
                calories = analysis.calories.toDouble(),
                protein = analysis.proteinG.toDouble(),
                carbs = analysis.carbsG.toDouble(),
                fat = analysis.fatG.toDouble()
            )
        )

        _uiState.value = MealCaptureUiState.ManualEntry(
            mealItems = existingItems + mealItem,
            sugar = "",
            addedSugar = ""
        )
        resetCalculatedMicronutrients()
    }
    
    /**
     * Add a food item to the current analysis result meal
     */
    fun addFoodItemToAnalysisResult() {
        val current = _uiState.value
        if (current is MealCaptureUiState.AnalysisResult) {
            // Switch to manual entry mode to add a new food item
            // First, convert current analysis to a meal item
            val currentItem = MealItem(
                foodName = current.analysis.food,
                calories = current.analysis.calories,
                protein = current.analysis.proteinG,
                carbs = current.analysis.carbsG,
                fat = current.analysis.fatG,
                sugar = current.analysis.sugarG,
                addedSugar = current.analysis.addedSugarG,
                micronutrients = MicronutrientEstimator.estimate(
                    foodName = current.analysis.food,
                    calories = current.analysis.calories.toDouble(),
                    protein = current.analysis.proteinG.toDouble(),
                    carbs = current.analysis.carbsG.toDouble(),
                    fat = current.analysis.fatG.toDouble()
                )
            )
            
            _uiState.value = MealCaptureUiState.ManualEntry(
                mealItems = listOf(currentItem) + current.mealItems,
                sugar = "",
                addedSugar = ""
            )
            resetCalculatedMicronutrients()
        }
    }
    
    /**
     * Add a manually entered food item to the analysis result (called from manual entry)
     */
    fun addFoodItemToAnalysisResultFromManual(mealItem: MealItem) {
        val current = _uiState.value
        if (current is MealCaptureUiState.ManualEntry && current.mealItems.isNotEmpty()) {
            // Check if we came from AnalysisResult by checking if first item matches an analysis
            // For now, we'll just add the item and let the user continue
            // This is called when user adds a food item in manual mode after coming from analysis
        }
    }
    
    /**
     * Remove a food item from analysis result meal items
     */
    fun removeFoodItemFromAnalysisResult(index: Int) {
        val current = _uiState.value
        if (current is MealCaptureUiState.AnalysisResult) {
            val updatedItems = current.mealItems.toMutableList()
            if (index in updatedItems.indices) {
                updatedItems.removeAt(index)
                _uiState.value = current.copy(mealItems = updatedItems)
            }
        }
    }
    
    /**
     * Update a meal item at the given index
     */
    fun updateMealItem(index: Int, updatedItem: MealItem) {
        val current = _uiState.value
        if (current is MealCaptureUiState.AnalysisResult) {
            val updatedItems = current.mealItems.toMutableList()
            if (index in updatedItems.indices) {
                updatedItems[index] = updatedItem
                _uiState.value = current.copy(mealItems = updatedItems)
            }
        } else if (current is MealCaptureUiState.ManualEntry) {
            val updatedItems = current.mealItems.toMutableList()
            if (index in updatedItems.indices) {
                updatedItems[index] = updatedItem
                _uiState.value = current.copy(mealItems = updatedItems)
            }
        }
    }
    
    /**
     * Split the main analysis into individual food items
     * This allows users to break down "toast with eggs and cheese" into separate items
     */
    fun splitAnalysisIntoItems() {
        val current = _uiState.value
        if (current is MealCaptureUiState.AnalysisResult) {
            // Create a meal item from the current analysis
            // Estimate sugar from carbs if available
            val estimatedSugar = if (current.analysis.carbsG > 0) {
                estimateSugarFromCarbs(current.analysis.carbsG.toDouble(), current.analysis.food).roundToInt()
            } else {
                0
            }
            val mainItem = MealItem(
                foodName = current.analysis.food,
                calories = current.analysis.calories,
                protein = current.analysis.proteinG,
                carbs = current.analysis.carbsG,
                fat = current.analysis.fatG,
                sugar = estimatedSugar,
                addedSugar = 0,
                micronutrients = MicronutrientEstimator.estimate(
                    foodName = current.analysis.food,
                    calories = current.analysis.calories.toDouble(),
                    protein = current.analysis.proteinG.toDouble(),
                    carbs = current.analysis.carbsG.toDouble(),
                    fat = current.analysis.fatG.toDouble()
                )
            )
            
            // Add it to meal items and clear the main analysis
            val updatedItems = listOf(mainItem) + current.mealItems
            _uiState.value = current.copy(
                analysis = current.analysis.copy(food = "", calories = 0, proteinG = 0, carbsG = 0, fatG = 0),
                mealItems = updatedItems
            )
        }
    }

    /**
     * Save combined meal log from all meal items
     */
    fun saveCombinedMeal() {
        val current = _uiState.value
        if (current is MealCaptureUiState.ManualEntry) {
            // If we have an associated recipe, use it to create the meal log with servings
            val recipe = _associatedRecipe.value
            val servings = _servings.value
            
            val mealLog = if (recipe != null) {
                // Use recipe to create meal log with servings
                recipe.toMealLog(servings.toInt()).copy(
                    recipeId = recipe.id,
                    servingsConsumed = servings
                )
            } else {
                // Fall back to building from items
                val allItems = current.mealItems.toMutableList()
                
                // Add current entry if it has data
                createMealItemFromInputs(current)?.let { allItems.add(it) }
                
                if (allItems.isEmpty()) {
                    return // Nothing to save
                }
                
                buildMealLogFromItems(allItems)
            }

            saveMealLog(mealLog)
            resetCalculatedMicronutrients()
        }
    }

    /**
     * Save meal log to Firestore
     */
    fun saveMealLog(mealLog: HealthLog.MealLog) {
        viewModelScope.launch {
            // Debug: Log meal log details before saving
            android.util.Log.d("MealCaptureViewModel", "Saving meal log: ${mealLog.foodName}, ${mealLog.calories} cal, ${mealLog.protein}g protein, ${mealLog.carbs}g carbs, ${mealLog.fat}g fat")
            
            _uiState.value = MealCaptureUiState.Saving

            try {
                val date = java.time.LocalDate.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))

                // ROOT CAUSE FIX: Always use authenticated user's ID for Firestore operations
                val authenticatedUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                    ?: throw Exception("User must be authenticated to save meals")
                
                val result = firebaseRepository.saveHealthLog(authenticatedUserId, date, mealLog)

                if (result.isSuccess) {
                    val entryId = result.getOrNull() ?: "unknown"
                    android.util.Log.i("MealCaptureViewModel", "✅✅✅ Meal log saved successfully: ${mealLog.foodName}, ${mealLog.calories} cal, entryId=$entryId ✅✅✅")
                    _uiState.value = MealCaptureUiState.Success
                } else {
                    val error = result.exceptionOrNull()
                    val errorMessage = error?.message ?: "Unknown error"
                    val errorType = error?.javaClass?.simpleName ?: "Unknown"
                    
                    android.util.Log.e("MealCaptureViewModel", "✗✗✗ FAILED to save meal log ✗✗✗")
                    android.util.Log.e("MealCaptureViewModel", "  Food: ${mealLog.foodName}")
                    android.util.Log.e("MealCaptureViewModel", "  Calories: ${mealLog.calories}")
                    android.util.Log.e("MealCaptureViewModel", "  Error: $errorMessage")
                    android.util.Log.e("MealCaptureViewModel", "  Error type: $errorType")
                    
                    // Check if it's a permission error
                    if (error is com.google.firebase.firestore.FirebaseFirestoreException && 
                        error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        android.util.Log.e("MealCaptureViewModel", "  ⚠️ PERMISSION DENIED - Check Firestore security rules!")
                    }
                    
                    error?.printStackTrace()
                    _uiState.value = MealCaptureUiState.Error("Failed to save meal: $errorMessage")
                }
            } catch (e: Exception) {
                android.util.Log.e("MealCaptureViewModel", "✗✗✗ EXCEPTION saving meal log ✗✗✗", e)
                android.util.Log.e("MealCaptureViewModel", "  Exception type: ${e.javaClass.simpleName}")
                android.util.Log.e("MealCaptureViewModel", "  Exception message: ${e.message}")
                e.printStackTrace()
                _uiState.value = MealCaptureUiState.Error("Error saving meal: ${e.message ?: "Unknown error"}")
            }
        }
    }

    /**
     * Save current meal for quick re-selection
     */
    fun saveMealForQuickSelect(name: String) {
        val current = _uiState.value
        viewModelScope.launch {
            try {
                val savedMeal = when (current) {
                    is MealCaptureUiState.AnalysisResult -> {
                        // Build meal log from analysis + meal items
                        val analysisItem = MealItem(
                            foodName = current.analysis.food,
                            calories = current.analysis.calories,
                            protein = current.analysis.proteinG,
                            carbs = current.analysis.carbsG,
                            fat = current.analysis.fatG,
                            sugar = current.analysis.sugarG,
                            addedSugar = current.analysis.addedSugarG,
                            micronutrients = MicronutrientEstimator.estimate(
                                foodName = current.analysis.food,
                                calories = current.analysis.calories.toDouble(),
                                protein = current.analysis.proteinG.toDouble(),
                                carbs = current.analysis.carbsG.toDouble(),
                                fat = current.analysis.fatG.toDouble()
                            )
                        )
                        val allItems = listOf(analysisItem) + current.mealItems
                        val mealLog = buildMealLogFromItems(allItems)
                        SavedMeal.fromMealLog(userId, name, mealLog)
                    }
                    is MealCaptureUiState.ManualEntry -> {
                        val allItems = current.mealItems.toMutableList()
                        createMealItemFromInputs(current)?.let { allItems.add(it) }
                        if (allItems.isEmpty()) {
                            android.util.Log.w("MealCaptureViewModel", "No meal data available to save for quick select")
                            return@launch
                        }
                        val mealLog = buildMealLogFromItems(allItems)
                        SavedMeal.fromMealLog(userId, name, mealLog)
                    }
                    else -> {
                        android.util.Log.w("MealCaptureViewModel", "SaveMealForQuickSelect ignored for state: ${current::class.simpleName}")
                        return@launch
                    }
                }

                val result = firebaseRepository.saveSavedMeal(savedMeal)

                if (result.isSuccess) {
                    android.util.Log.i("MealCaptureViewModel", "Successfully saved meal: $name")
                    _saveSuccessMessage.value = "Meal saved to Quick Save!"
                    // Clear message after 3 seconds
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(3000)
                        _saveSuccessMessage.value = null
                    }
                } else {
                    android.util.Log.e("MealCaptureViewModel", "Failed to save meal: ${result.exceptionOrNull()?.message}")
                    _saveSuccessMessage.value = "Failed to save meal: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
                }
            } catch (e: Exception) {
                android.util.Log.e("MealCaptureViewModel", "Error saving meal", e)
                _saveSuccessMessage.value = "Failed to save meal: ${e.message}"
            }
        }
    }

    fun clearSaveMessage() {
        _saveSuccessMessage.value = null
    }

    /**
     * Reset state
     */
    fun resetState() {
        currentPhotoFile = null
        _uiState.value = MealCaptureUiState.Idle
        resetCalculatedMicronutrients()
        _associatedRecipe.value = null
    }

    /**
     * Load user preferences for measurement units
     */
    private fun loadUserPreferences() {
        viewModelScope.launch {
            try {
                val goalsResult = firebaseRepository.getUserGoals(userId)
                val goals = goalsResult.getOrNull()
                val useImperial = goals?.get("useImperial") as? Boolean ?: true
                _useImperial.value = useImperial
                val genderValue = goals?.get("gender") as? String
                _gender.value = genderValue
            } catch (e: Exception) {
                // Default to imperial on error
                _useImperial.value = true
            }
        }
    }

    /**
     * Estimate sugar content from carbs when sugar data is not available.
     * Uses heuristics based on food name and carb content.
     */
    private fun estimateSugarFromCarbs(carbs: Double, foodName: String): Double {
        if (carbs <= 0.0) return 0.0
        
        val nameLower = foodName.lowercase()
        
        // High sugar foods (most carbs are sugar)
        val highSugarKeywords = listOf(
            "candy", "chocolate", "cookie", "cake", "donut", "muffin", "pastry",
            "soda", "juice", "drink", "beverage", "sweet", "syrup", "honey",
            "jam", "jelly", "preserve", "marmalade", "fruit", "berries"
        )
        if (highSugarKeywords.any { nameLower.contains(it) }) {
            return carbs * 0.85 // ~85% of carbs are sugar
        }
        
        // Medium sugar foods (processed foods, breads, cereals)
        val mediumSugarKeywords = listOf(
            "bread", "cereal", "crackers", "granola", "bar", "snack",
            "yogurt", "milk", "cream", "ice cream", "frozen"
        )
        if (mediumSugarKeywords.any { nameLower.contains(it) }) {
            return carbs * 0.40 // ~40% of carbs are sugar
        }
        
        // Low sugar foods (whole grains, vegetables, proteins)
        val lowSugarKeywords = listOf(
            "rice", "pasta", "quinoa", "oats", "potato", "sweet potato",
            "chicken", "beef", "pork", "fish", "salmon", "turkey",
            "broccoli", "spinach", "lettuce", "cucumber", "tomato", "pepper"
        )
        if (lowSugarKeywords.any { nameLower.contains(it) }) {
            return carbs * 0.10 // ~10% of carbs are sugar (natural sugars)
        }
        
        // Default: assume 30% of carbs are sugar (moderate estimate)
        return carbs * 0.30
    }

    /**
     * Factory for creating MealCaptureViewModel
     */
    class Factory(
        private val context: Context,
        private val firebaseRepository: FirebaseRepository,
        private val preferencesManager: PreferencesManager,
        private val userId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MealCaptureViewModel::class.java)) {
                return MealCaptureViewModel(context, firebaseRepository, preferencesManager, userId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    /**
     * Share a meal with friends
     * EXACT SAME IMPLEMENTATION AS RECIPE SHARING
     */
    fun shareMealWithFriends(mealLog: HealthLog.MealLog, friendIds: List<String>) {
        viewModelScope.launch {
            try {
                android.util.Log.d("MealCaptureViewModel", "=========================================")
                android.util.Log.d("MealCaptureViewModel", "🔄🔄🔄 SHARING MEAL 🔄🔄🔄")
                android.util.Log.d("MealCaptureViewModel", "Meal ID: ${mealLog.entryId}")
                android.util.Log.d("MealCaptureViewModel", "Meal name: ${mealLog.foodName}")
                android.util.Log.d("MealCaptureViewModel", "Current user ID: $userId")
                android.util.Log.d("MealCaptureViewModel", "Sharing with ${friendIds.size} friends: $friendIds")
                android.util.Log.d("MealCaptureViewModel", "=========================================")
                
                // CRITICAL: Get authenticated user ID FIRST
                val currentAuthUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val currentAuthUserId = currentAuthUser?.uid
                
                if (currentAuthUserId == null) {
                    android.util.Log.e("MealCaptureViewModel", "❌❌❌ CRITICAL ERROR: No authenticated user! ❌❌❌")
                    throw Exception("User must be authenticated to share meals")
                }
                
                // CRITICAL: If meal has a recipe, share the recipe too
                if (mealLog.recipeId != null && mealLog.recipeId!!.isNotBlank()) {
                    try {
                        // Check subscription tier - recipe sharing is Pro-only
                        val tier = com.coachie.app.data.SubscriptionService.getUserTier(currentAuthUserId)
                        if (tier != com.coachie.app.data.model.SubscriptionTier.PRO) {
                            android.util.Log.w("MealCaptureViewModel", "Recipe sharing blocked: Pro feature only")
                            throw Exception("Recipe sharing is a Pro feature. Upgrade to Pro to share recipes with friends, post to forums, and share to circles.")
                        }
                        
                        // Get the recipe
                        val recipeResult = firebaseRepository.getRecipe(currentAuthUserId, mealLog.recipeId!!)
                        recipeResult.fold(
                            onSuccess = { recipe ->
                                if (recipe != null) {
                                    // CRITICAL: Save recipe to user's personal recipes collection FIRST
                                    android.util.Log.d("MealCaptureViewModel", "💾 Saving recipe to personal collection before sharing...")
                                    val saveResult = firebaseRepository.saveRecipe(currentAuthUserId, recipe)
                                    if (saveResult.isFailure) {
                                        android.util.Log.w("MealCaptureViewModel", "Failed to save recipe to personal collection, continuing with share")
                                    } else {
                                        android.util.Log.d("MealCaptureViewModel", "✅ Recipe saved to personal collection")
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
                                    android.util.Log.d("MealCaptureViewModel", "✅ Shared recipe ${recipe.id} with friends")
                                }
                            },
                            onFailure = {
                                android.util.Log.w("MealCaptureViewModel", "Could not load recipe ${mealLog.recipeId} for sharing")
                            }
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("MealCaptureViewModel", "Error sharing recipe", e)
                        // Continue with meal sharing even if recipe sharing fails
                    }
                }
                
                // Save to shared meals collection - ALWAYS use authenticated user's ID
                val mealData = hashMapOf<String, Any>(
                    "id" to mealLog.entryId,
                    "userId" to currentAuthUserId, // CRITICAL: Use authenticated user's ID directly
                    "foodName" to mealLog.foodName,
                    "calories" to mealLog.calories,
                    "protein" to mealLog.protein,
                    "carbs" to mealLog.carbs,
                    "fat" to mealLog.fat,
                    "sugar" to mealLog.sugar,
                    "addedSugar" to mealLog.addedSugar,
                    "micronutrients" to mealLog.micronutrients,
                    "photoUrl" to (mealLog.photoUrl ?: ""),
                    "recipeId" to (mealLog.recipeId ?: ""), // CRITICAL: Include recipeId so friends can access recipe
                    "servingsConsumed" to (mealLog.servingsConsumed ?: 1.0),
                    "isShared" to true,
                    "sharedWith" to friendIds,
                    "timestamp" to mealLog.timestamp,
                    "createdAt" to System.currentTimeMillis()
                )

                val firestore = com.google.firebase.ktx.Firebase.firestore
                val sharedMealsRef = firestore.collection("sharedMeals")
                
                android.util.Log.d("MealCaptureViewModel", "=========================================")
                android.util.Log.d("MealCaptureViewModel", "🔄 ATTEMPTING TO SAVE TO FIRESTORE")
                android.util.Log.d("MealCaptureViewModel", "Collection: sharedMeals")
                android.util.Log.d("MealCaptureViewModel", "Document ID: ${mealLog.entryId}")
                android.util.Log.d("MealCaptureViewModel", "Current authenticated user: $currentAuthUserId")
                android.util.Log.d("MealCaptureViewModel", "ViewModel userId: $userId")
                android.util.Log.d("MealCaptureViewModel", "Meal userId: ${mealData["userId"]}")
                android.util.Log.d("MealCaptureViewModel", "Shared with: $friendIds")
                android.util.Log.d("MealCaptureViewModel", "Meal data keys: ${mealData.keys}")
                android.util.Log.d("MealCaptureViewModel", "Meal data size: ${mealData.size} fields")
                android.util.Log.d("MealCaptureViewModel", "✅ userId is authenticated user's ID - save should succeed")
                
                android.util.Log.d("MealCaptureViewModel", "=========================================")
                
                // CRITICAL: Use set() (not merge) to ensure sharedWith array is properly updated
                // If we use merge() and the meal already exists with an empty sharedWith,
                // the merge might not properly replace the array
                try {
                    android.util.Log.d("MealCaptureViewModel", "⏱️ Calling Firestore .set() at ${System.currentTimeMillis()}")
                    sharedMealsRef
                        .document(mealLog.entryId)
                        .set(mealData)
                        .await()
                    android.util.Log.d("MealCaptureViewModel", "✅✅✅ Firestore .set() completed successfully ✅✅✅")
                    
                    // Verify the data was saved correctly
                    val savedDoc = sharedMealsRef.document(mealLog.entryId).get().await()
                    if (!savedDoc.exists()) {
                        android.util.Log.e("MealCaptureViewModel", "❌ ERROR: Meal document does not exist after save!")
                        throw Exception("Meal was not saved to Firestore")
                    }
                    
                    val savedData = savedDoc.data
                    val savedSharedWith = savedData?.get("sharedWith") as? List<*>
                    val savedUserId = savedData?.get("userId") as? String
                    
                    android.util.Log.d("MealCaptureViewModel", "✅ Meal saved to Firestore successfully")
                    android.util.Log.d("MealCaptureViewModel", "✅ Meal ID: ${mealLog.entryId}")
                    android.util.Log.d("MealCaptureViewModel", "✅ Saved to collection: sharedMeals/${mealLog.entryId}")
                    android.util.Log.d("MealCaptureViewModel", "✅ VERIFICATION: Saved userId: $savedUserId (expected: ${mealData["userId"]})")
                    android.util.Log.d("MealCaptureViewModel", "✅ VERIFICATION: Saved sharedWith array: $savedSharedWith (expected: $friendIds)")
                    
                    // CRITICAL VERIFICATION: Check that the data was actually saved correctly
                    if (savedUserId != mealData["userId"]) {
                        android.util.Log.e("MealCaptureViewModel", "❌ ERROR: userId mismatch! Saved: $savedUserId, Expected: ${mealData["userId"]}")
                        throw Exception("Meal userId mismatch - save may have failed")
                    }
                    
                    if (savedSharedWith?.size != friendIds.size || !savedSharedWith.containsAll(friendIds)) {
                        android.util.Log.e("MealCaptureViewModel", "❌ ERROR: sharedWith array mismatch!")
                        android.util.Log.e("MealCaptureViewModel", "Saved: $savedSharedWith")
                        android.util.Log.e("MealCaptureViewModel", "Expected: $friendIds")
                        throw Exception("Meal sharedWith array mismatch - save may have failed")
                    }
                    
                    // FINAL VERIFICATION: Query the collection to confirm it's actually there
                    val verificationQuery = sharedMealsRef
                        .whereEqualTo("id", mealLog.entryId)
                        .get()
                        .await()
                    
                    if (verificationQuery.isEmpty) {
                        android.util.Log.e("MealCaptureViewModel", "❌ ERROR: Meal not found in collection after save!")
                        throw Exception("Meal not found in collection after save")
                    }
                    
                    android.util.Log.d("MealCaptureViewModel", "✅✅✅ FINAL VERIFICATION PASSED: Meal exists in sharedMeals collection ✅✅✅")
                    android.util.Log.d("MealCaptureViewModel", "✅ Meal shared successfully: ${mealLog.entryId} with ${friendIds.size} friends")
                } catch (saveError: Exception) {
                    android.util.Log.e("MealCaptureViewModel", "❌ ERROR SAVING MEAL TO FIRESTORE", saveError)
                    android.util.Log.e("MealCaptureViewModel", "Error type: ${saveError.javaClass.simpleName}")
                    android.util.Log.e("MealCaptureViewModel", "Error message: ${saveError.message}")
                    saveError.printStackTrace()
                    throw saveError // Re-throw to be caught by outer catch
                }
            } catch (e: Exception) {
                android.util.Log.e("MealCaptureViewModel", "❌❌❌ ERROR SHARING MEAL ❌❌❌", e)
                android.util.Log.e("MealCaptureViewModel", "Error type: ${e.javaClass.simpleName}")
                android.util.Log.e("MealCaptureViewModel", "Error message: ${e.message}")
                android.util.Log.e("MealCaptureViewModel", "Stack trace:")
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
                
                // Update UI state if available
                // Note: MealCaptureViewModel may not have error state, but we log it
            }
        }
    }
}

/**
 * Represents a single food item in a meal
 */
data class MealItem(
    val foodName: String,
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val sugar: Int = 0,
    val addedSugar: Int = 0,
    val micronutrients: Map<MicronutrientType, Double> = emptyMap(),
    val quantity: String = "", // e.g., "2 slices", "3 eggs", "1 cup"
    val type: String = "" // e.g., "whole wheat", "cheddar", "large"
)

/**
 * UI State for meal capture screen
 */
sealed class MealCaptureUiState {
    object Idle : MealCaptureUiState()
    data class PhotoTaken(val photoUri: Uri) : MealCaptureUiState()
    data class SearchingNutrition(val analysis: MealAnalysis) : MealCaptureUiState()
    object Analyzing : MealCaptureUiState()
    data class AnalysisResult(
        val analysis: MealAnalysis, 
        val photoUri: Uri?,
        val mealItems: List<MealItem> = emptyList() // Additional food items added to this meal
    ) : MealCaptureUiState()
    data class ManualEntry(
        val foodName: String = "",
        val calories: String = "",
        val protein: String = "",
        val carbs: String = "",
        val fat: String = "",
        val sugar: String = "",
        val addedSugar: String = "",
        val mealItems: List<MealItem> = emptyList() // List of foods in this meal
    ) : MealCaptureUiState()
    object Saving : MealCaptureUiState()
    object Success : MealCaptureUiState()
    data class Error(val message: String) : MealCaptureUiState()
}
