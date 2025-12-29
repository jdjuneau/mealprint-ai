package com.coachie.app.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.ai.GeminiVisionClient
import com.coachie.app.data.ai.SupplementAnalysis
import com.coachie.app.data.local.PreferencesManager
import com.coachie.app.data.model.MicronutrientType
import com.coachie.app.data.model.Supplement
import com.coachie.app.data.model.toPersistedMicronutrientMap
import com.coachie.app.util.AuthUtils
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewModel for supplement photo capture screen
 */
class SupplementPhotoCaptureViewModel(
    private val context: Context,
    private val firebaseRepository: FirebaseRepository,
    private val preferencesManager: PreferencesManager,
    private val userId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<SupplementPhotoCaptureUiState>(SupplementPhotoCaptureUiState.Idle)
    val uiState: StateFlow<SupplementPhotoCaptureUiState> = _uiState.asStateFlow()

    private val _hasCameraPermission = MutableStateFlow(false)
    val hasCameraPermission: StateFlow<Boolean> = _hasCameraPermission.asStateFlow()

    private var currentPhotoFile: File? = null
    private val geminiVisionClient = GeminiVisionClient(context)

    init {
        checkCameraPermission()
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
            val imageFileName = "SUPPLEMENT_${timeStamp}_"
            val storageDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            val imageFile = File.createTempFile(imageFileName, ".jpg", storageDir)
            currentPhotoFile = imageFile

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
        } catch (e: Exception) {
            android.util.Log.e("SupplementPhotoCaptureViewModel", "Error creating photo file", e)
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
            _uiState.value = SupplementPhotoCaptureUiState.PhotoTaken(uri)
        }
    }

    /**
     * Analyze supplement image with Gemini Vision
     */
    fun analyzeSupplementImage(imageUri: Uri) {
        viewModelScope.launch {
            _uiState.value = SupplementPhotoCaptureUiState.Analyzing
            android.util.Log.d("SupplementPhotoCaptureViewModel", "Starting supplement image analysis for URI: $imageUri")

            try {
                val result = geminiVisionClient.analyzeSupplementImage(imageUri, userId)

                if (result.isSuccess) {
                    val analysis = result.getOrNull()
                    val currentState = _uiState.value
                    val photoUri = if (currentState is SupplementPhotoCaptureUiState.PhotoTaken) {
                        currentState.photoUri
                    } else {
                        imageUri
                    }

                    if (analysis != null) {
                        val persistedPath = persistLabelPhoto(photoUri)
                        val enrichedAnalysis = if (persistedPath != null && analysis.labelImagePath != persistedPath) {
                            analysis.copy(labelImagePath = persistedPath)
                        } else {
                            analysis
                        }

                        android.util.Log.i("SupplementPhotoCaptureViewModel", "Supplement analysis successful: ${enrichedAnalysis.supplementName}, confidence: ${enrichedAnalysis.confidence}")
                        _uiState.value = SupplementPhotoCaptureUiState.AnalysisResult(enrichedAnalysis, photoUri)
                    } else {
                        android.util.Log.e("SupplementPhotoCaptureViewModel", "Supplement analysis returned null result")
                        _uiState.value = SupplementPhotoCaptureUiState.Error("Failed to analyze image - no results returned")
                    }
                } else {
                    val exception = result.exceptionOrNull()
                    val errorMessage = exception?.message ?: "Unknown error during analysis"
                    android.util.Log.e("SupplementPhotoCaptureViewModel", "Supplement analysis failed: $errorMessage", exception)

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

                    _uiState.value = SupplementPhotoCaptureUiState.Error(userFriendlyMessage)
                }
            } catch (e: Exception) {
                android.util.Log.e("SupplementPhotoCaptureViewModel", "Unexpected error during supplement analysis", e)

                val userFriendlyMessage = when {
                    e.message?.contains("API key", ignoreCase = true) == true ->
                        "AI analysis unavailable - please check API configuration"
                    e.message?.contains("network", ignoreCase = true) == true ->
                        "Network error - please check your internet connection"
                    else ->
                        "Unexpected error during analysis: ${e.localizedMessage ?: "Unknown error"}"
                }

                _uiState.value = SupplementPhotoCaptureUiState.Error(userFriendlyMessage)
            }
        }
    }

    private fun persistLabelPhoto(photoUri: Uri?): String? {
        if (photoUri == null) return null
        return try {
            val targetDir = File(context.filesDir, "supplement_labels").apply {
                if (!exists()) {
                    mkdirs()
                }
            }
            val fileName = "label_${System.currentTimeMillis()}.jpg"
            val destination = File(targetDir, fileName)
            context.contentResolver.openInputStream(photoUri)?.use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            destination.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("SupplementPhotoCaptureViewModel", "Failed to persist label photo", e)
            null
        }
    }

    /**
     * Set manual entry mode
     */
    fun setManualEntryMode() {
        _uiState.value = SupplementPhotoCaptureUiState.ManualEntry(
            supplementName = "",
            micronutrients = emptyMap(),
            isDaily = false
        )
    }

    /**
     * Search for supplement information using AI
     */
    fun searchSupplement(brand: String, supplementType: String) {
        viewModelScope.launch {
            val current = _uiState.value
            if (current !is SupplementPhotoCaptureUiState.ManualEntry) {
                return@launch
            }

            // Check if user is authenticated
            val userId = AuthUtils.getAuthenticatedUserId()
            if (userId == null) {
                _uiState.value = current.copy(
                    isSearching = false,
                    searchError = "Please sign in to search for supplements"
                )
                return@launch
            }

            _uiState.value = current.copy(isSearching = true, searchError = null)

            try {
                // Explicitly use us-central1 region to match function deployment
                val functions = com.google.firebase.functions.FirebaseFunctions.getInstance("us-central1")
                val searchSupplement = functions.getHttpsCallable("searchSupplement")
                
                android.util.Log.d("SupplementPhotoCaptureViewModel", "Calling searchSupplement with userId: $userId")
                
                val result = searchSupplement.call(mapOf(
                    "brand" to brand,
                    "supplementType" to supplementType
                )).await()

                val data = result.data as? Map<*, *>
                if (data == null) {
                    throw Exception("Invalid response from server")
                }
                
                val supplementName = data.get("supplementName") as? String ?: "$brand $supplementType"
                val nutrientsJson = data.get("nutrients") as? Map<*, *> ?: emptyMap<String, Any>()
                val notes = data.get("notes") as? String

                android.util.Log.d("SupplementPhotoCaptureViewModel", "Search response received: name=$supplementName, nutrients=${nutrientsJson.size}, notes=$notes")

                // Map AI response to MicronutrientType
                val nutrients = mutableMapOf<MicronutrientType, Double>()
                nutrientsJson.forEach { (key, value) ->
                    val nutrientName = key.toString().lowercase()
                    val amount = (value as? Number)?.toDouble() ?: 0.0
                    
                    // Map nutrient names to MicronutrientType
                    val nutrientType = when (nutrientName) {
                        "vitamin_a" -> MicronutrientType.VITAMIN_A
                        "vitamin_c" -> MicronutrientType.VITAMIN_C
                        "vitamin_d" -> MicronutrientType.VITAMIN_D
                        "vitamin_e" -> MicronutrientType.VITAMIN_E
                        "vitamin_k" -> MicronutrientType.VITAMIN_K
                        "vitamin_b1" -> MicronutrientType.VITAMIN_B1
                        "vitamin_b2" -> MicronutrientType.VITAMIN_B2
                        "vitamin_b3" -> MicronutrientType.VITAMIN_B3
                        "vitamin_b5" -> MicronutrientType.VITAMIN_B5
                        "vitamin_b6" -> MicronutrientType.VITAMIN_B6
                        "vitamin_b7" -> MicronutrientType.VITAMIN_B7
                        "vitamin_b9" -> MicronutrientType.VITAMIN_B9
                        "vitamin_b12" -> MicronutrientType.VITAMIN_B12
                        "calcium" -> MicronutrientType.CALCIUM
                        "magnesium" -> MicronutrientType.MAGNESIUM
                        "potassium" -> MicronutrientType.POTASSIUM
                        "sodium" -> MicronutrientType.SODIUM
                        "iron" -> MicronutrientType.IRON
                        "zinc" -> MicronutrientType.ZINC
                        "iodine" -> MicronutrientType.IODINE
                        "selenium" -> MicronutrientType.SELENIUM
                        "phosphorus" -> MicronutrientType.PHOSPHORUS
                        "manganese" -> MicronutrientType.MANGANESE
                        "copper" -> MicronutrientType.COPPER
                        else -> null
                    }

                    if (nutrientType != null && amount > 0) {
                        nutrients[nutrientType] = amount
                    }
                }

                // Update state with search results (preserve isDaily setting, clear any errors)
                _uiState.value = current.copy(
                    supplementName = supplementName,
                    micronutrients = nutrients,
                    isSearching = false,
                    searchError = null, // Clear any previous errors on success
                    searchNotes = notes
                    // isDaily is preserved from current state
                )

                android.util.Log.d("SupplementPhotoCaptureViewModel", "✅ Search successful: Found ${nutrients.size} nutrients, error cleared")
            } catch (e: FirebaseFunctionsException) {
                android.util.Log.e("SupplementPhotoCaptureViewModel", "❌ Search failed with FirebaseFunctionsException", e)
                android.util.Log.e("SupplementPhotoCaptureViewModel", "Error code: ${e.code}, message: ${e.message}")
                val errorMessage = when (e.code) {
                    FirebaseFunctionsException.Code.UNAUTHENTICATED -> {
                        "Please sign in to search for supplements"
                    }
                    FirebaseFunctionsException.Code.NOT_FOUND -> {
                        "Supplement search function not available. Please try again later."
                    }
                    FirebaseFunctionsException.Code.INVALID_ARGUMENT -> {
                        "Please provide both brand and supplement type"
                    }
                    else -> {
                        e.message ?: "Failed to search supplement. Please try again."
                    }
                }
                _uiState.value = current.copy(
                    isSearching = false,
                    searchError = errorMessage
                )
            } catch (e: Exception) {
                android.util.Log.e("SupplementPhotoCaptureViewModel", "Search failed", e)
                _uiState.value = current.copy(
                    isSearching = false,
                    searchError = e.message ?: "Failed to search supplement"
                )
            }
        }
    }

    /**
     * Update manual entry fields
     */
    fun updateSupplementName(name: String) {
        val current = _uiState.value
        if (current is SupplementPhotoCaptureUiState.ManualEntry) {
            _uiState.value = current.copy(supplementName = name)
        }
    }

    fun updateMicronutrient(type: MicronutrientType, amount: Double) {
        val current = _uiState.value
        if (current is SupplementPhotoCaptureUiState.ManualEntry) {
            val updatedNutrients = current.micronutrients.toMutableMap()
            if (amount > 0) {
                updatedNutrients[type] = amount
            } else {
                updatedNutrients.remove(type)
            }
            _uiState.value = current.copy(micronutrients = updatedNutrients)
        }
    }

    fun updateIsDaily(isDaily: Boolean) {
        val current = _uiState.value
        if (current is SupplementPhotoCaptureUiState.ManualEntry) {
            _uiState.value = current.copy(isDaily = isDaily)
        }
    }

    /**
     * Add current supplement to list
     */
    fun addSupplementToList() {
        val current = _uiState.value
        if (current is SupplementPhotoCaptureUiState.ManualEntry) {
            val newSupplement = createSupplementFromInputs(current) ?: return
            val updatedSupplements = current.supplements + newSupplement
            _uiState.value = current.copy(
                supplements = updatedSupplements,
                supplementName = "",
                micronutrients = emptyMap(),
                labelImagePath = null,
                labelText = null
            )
        }
    }

    /**
     * Remove a supplement from list
     */
    fun removeSupplementFromList(index: Int) {
        val current = _uiState.value
        if (current is SupplementPhotoCaptureUiState.ManualEntry) {
            val updatedSupplements = current.supplements.toMutableList()
            if (index in updatedSupplements.indices) {
                updatedSupplements.removeAt(index)
                _uiState.value = current.copy(supplements = updatedSupplements)
            }
        }
    }

    /**
     * Edit analysis result (switch to manual entry with pre-filled data)
     */
    fun editAnalysisResult() {
        val current = _uiState.value
        if (current is SupplementPhotoCaptureUiState.AnalysisResult) {
            _uiState.value = SupplementPhotoCaptureUiState.ManualEntry(
                supplementName = current.analysis.supplementName,
                micronutrients = current.analysis.nutrients,
                supplements = emptyList(),
                labelImagePath = current.analysis.labelImagePath,
                labelText = current.analysis.rawLabelText,
                isDaily = false
            )
        }
    }

    /**
     * Convert AI analysis result into a supplement and continue in manual mode.
     */
    fun addAnalysisResultToSupplements(analysis: SupplementAnalysis) {
        val supplement = Supplement(
            name = analysis.supplementName,
            micronutrients = analysis.nutrients.toPersistedMicronutrientMap(),
            labelImagePath = analysis.labelImagePath,
            labelText = analysis.rawLabelText,
            isDaily = false // Let user decide
        )

        _uiState.value = SupplementPhotoCaptureUiState.ManualEntry(
            supplementName = analysis.supplementName,
            micronutrients = analysis.nutrients,
            supplements = listOf(supplement),
            labelImagePath = analysis.labelImagePath,
            labelText = analysis.rawLabelText,
            isDaily = false
        )
    }

    /**
     * Save single supplement entry
     */
    fun saveSingleSupplementEntry() {
        val current = _uiState.value
        if (current is SupplementPhotoCaptureUiState.ManualEntry) {
            val supplement = createSupplementFromInputs(current) ?: return
            saveSupplements(listOf(supplement))
        }
    }

    /**
     * Save combined supplements from all items
     */
    fun saveCombinedSupplements() {
        val current = _uiState.value
        if (current is SupplementPhotoCaptureUiState.ManualEntry) {
            // Build list of all supplements including current entry if it has data
            val allSupplements = current.supplements.toMutableList()

            // Add current entry if it has data
            createSupplementFromInputs(current)?.let { allSupplements.add(it) }

            if (allSupplements.isEmpty()) {
                return // Nothing to save
            }

            saveSupplements(allSupplements)
        }
    }

    /**
     * Save supplements to Firestore
     */
    fun saveSupplements(supplements: List<Supplement>) {
        if (userId.isBlank()) {
            _uiState.value = SupplementPhotoCaptureUiState.Error("Sign in required before saving supplements.")
            return
        }

        viewModelScope.launch {
            _uiState.value = SupplementPhotoCaptureUiState.Saving

            try {
                val date = java.time.LocalDate.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))

                // Save each supplement
                supplements.forEach { supplement ->
                    val result = firebaseRepository.upsertSupplement(userId, supplement)
                    if (result.isFailure) {
                        val cause = result.exceptionOrNull()
                        val message = cause?.localizedMessage ?: cause?.message ?: "Unknown error"
                        throw Exception("Failed to save supplement: ${supplement.name} ($message)", cause)
                    }

                    // Also create a health log entry for today so it shows up in micronutrient tracker
                    val supplementLog = com.coachie.app.data.model.HealthLog.SupplementLog(
                        name = supplement.name,
                        micronutrients = supplement.micronutrients
                    )
                    val logResult = firebaseRepository.saveHealthLog(userId, date, supplementLog)
                    if (logResult.isFailure) {
                        val cause = logResult.exceptionOrNull()
                        val message = cause?.localizedMessage ?: cause?.message ?: "Unknown error"
                        android.util.Log.w("SupplementPhotoCaptureViewModel", "Failed to create health log entry for ${supplement.name}: $message")
                        // Don't throw here - the supplement was saved successfully, just log the warning
                    }
                }

                _uiState.value = SupplementPhotoCaptureUiState.Success
            } catch (e: Exception) {
                android.util.Log.e("SupplementPhotoCaptureViewModel", "Error saving supplements", e)
                _uiState.value = SupplementPhotoCaptureUiState.Error("Error: ${e.message}")
            }
        }
    }

    /**
     * Reset state
     */
    fun resetState() {
        currentPhotoFile = null
        _uiState.value = SupplementPhotoCaptureUiState.Idle
    }

    private fun createSupplementFromInputs(entry: SupplementPhotoCaptureUiState.ManualEntry): Supplement? {
        val hasNutrients = entry.micronutrients.isNotEmpty()
        val hasName = entry.supplementName.isNotBlank()

        if (!hasName && !hasNutrients) {
            return null
        }

        return Supplement(
            name = entry.supplementName.ifBlank { "Manual Supplement Entry" },
            micronutrients = entry.micronutrients.toPersistedMicronutrientMap(),
            labelImagePath = entry.labelImagePath,
            labelText = entry.labelText,
            isDaily = entry.isDaily
        )
    }

    /**
     * Factory for creating SupplementPhotoCaptureViewModel
     */
    class Factory(
        private val context: Context,
        private val firebaseRepository: FirebaseRepository,
        private val preferencesManager: PreferencesManager,
        private val userId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SupplementPhotoCaptureViewModel::class.java)) {
                return SupplementPhotoCaptureViewModel(context, firebaseRepository, preferencesManager, userId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

/**
 * UI State for supplement photo capture screen
 */
sealed class SupplementPhotoCaptureUiState {
    object Idle : SupplementPhotoCaptureUiState()
    data class PhotoTaken(val photoUri: Uri) : SupplementPhotoCaptureUiState()
    object Analyzing : SupplementPhotoCaptureUiState()
    data class AnalysisResult(val analysis: SupplementAnalysis, val photoUri: Uri?) : SupplementPhotoCaptureUiState()
    data class ManualEntry(
        val supplementName: String = "",
        val micronutrients: Map<MicronutrientType, Double> = emptyMap(),
        val supplements: List<Supplement> = emptyList(),
        val labelImagePath: String? = null,
        val labelText: String? = null,
        val isSearching: Boolean = false,
        val searchError: String? = null,
        val searchNotes: String? = null,
        val isDaily: Boolean = false
    ) : SupplementPhotoCaptureUiState()
    object Saving : SupplementPhotoCaptureUiState()
    object Success : SupplementPhotoCaptureUiState()
    data class Error(val message: String) : SupplementPhotoCaptureUiState()
}
