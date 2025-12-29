package com.coachie.app.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.data.ai.BodyMeasurements
import com.mealprint.ai.data.ai.PoseDetectionService
import com.mealprint.ai.data.local.PreferencesManager
import com.mealprint.ai.data.model.BodyFatComparison
import com.mealprint.ai.data.model.Scan
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel for BodyScanScreen functionality.
 * Handles camera permissions, photo capture, storage, and Firebase upload.
 */
class BodyScanViewModel(
    private val context: Context,
    private val firebaseRepository: FirebaseRepository,
    private val preferencesManager: PreferencesManager,
    private val userId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<BodyScanUiState>(BodyScanUiState.Idle)
    val uiState: StateFlow<BodyScanUiState> = _uiState.asStateFlow()

    private val _hasCameraPermission = MutableStateFlow(false)
    val hasCameraPermission: StateFlow<Boolean> = _hasCameraPermission.asStateFlow()

    private var currentPhotoUri: Uri? = null
    private var currentPhotoFile: File? = null

    private val storage = FirebaseStorage.getInstance()
    private val storageRef = storage.reference
    private val poseDetectionService = PoseDetectionService(context)

    private var bodyMeasurements: BodyMeasurements? = null
    private var bodyFatComparison: BodyFatComparison? = null

    init {
        checkCameraPermission()
    }

    /**
     * Check if camera permission is granted
     */
    private fun checkCameraPermission() {
        val permission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        )
        _hasCameraPermission.value = permission == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Update camera permission status after user grants/denies permission
     */
    fun updateCameraPermission(granted: Boolean) {
        _hasCameraPermission.value = granted
    }

    /**
     * Create a file for the photo in internal storage
     */
    fun createPhotoFile(): Uri? {
        return try {
            val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir: File = context.filesDir
            val photoDir = File(storageDir, "body_scans").apply {
                if (!exists()) mkdirs()
            }

            val photoFile = File(photoDir, "body_scan_${timeStamp}.jpg")
            currentPhotoFile = photoFile

            val photoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )

            currentPhotoUri = photoUri
            photoUri
        } catch (e: Exception) {
            _uiState.value = BodyScanUiState.Error("Failed to create photo file: ${e.message}")
            null
        }
    }

    /**
     * Handle successful photo capture
     */
    fun onPhotoTaken() {
        currentPhotoFile?.let { file ->
            if (file.exists() && file.length() > 0) {
                _uiState.value = BodyScanUiState.PhotoTaken(file)
                analyzePhoto(file)
            } else {
                _uiState.value = BodyScanUiState.Error("Photo file is empty or doesn't exist")
            }
        } ?: run {
            _uiState.value = BodyScanUiState.Error("No photo file available")
        }
    }

    /**
     * Analyze photo using pose detection
     */
    private fun analyzePhoto(photoFile: File) {
        _uiState.value = BodyScanUiState.Analyzing

        viewModelScope.launch {
            try {
                // Get user's known height from preferences (this would come from their profile)
                val knownHeight = preferencesManager.currentWeight?.toFloatOrNull() ?: 170f // Default fallback

                bodyMeasurements = poseDetectionService.analyzeBodyMeasurements(
                    photoPath = photoFile.absolutePath,
                    knownHeightCm = knownHeight
                )

                // Analysis complete, stay on photo taken state so user can confirm upload
                _uiState.value = BodyScanUiState.PhotoTaken(photoFile)

            } catch (e: Exception) {
                _uiState.value = BodyScanUiState.Error("Analysis failed: ${e.message}")
            }
        }
    }

    /**
     * Confirm upload after analysis
     */
    fun confirmUpload() {
        currentPhotoFile?.let { file ->
            uploadPhotoToFirebase(file)
        } ?: run {
            _uiState.value = BodyScanUiState.Error("No photo file available")
        }
    }

    /**
     * Upload photo to Firebase Storage
     */
    private fun uploadPhotoToFirebase(photoFile: File) {
        _uiState.value = BodyScanUiState.Uploading

        viewModelScope.launch {
            try {
                val timestamp = System.currentTimeMillis()
                val fileName = "body_scan_${userId}_${timestamp}.jpg"
                val photoRef = storageRef.child("body_scans/$userId/$fileName")

                val uploadTask = photoRef.putFile(Uri.fromFile(photoFile))
                uploadTask.await()

                val downloadUrl = photoRef.downloadUrl.await()

                // Save scan data to Firestore
                saveScanToFirestore(downloadUrl.toString(), timestamp)

                // Compare with previous scan
                bodyFatComparison = compareWithPreviousScan()

                _uiState.value = BodyScanUiState.Success(downloadUrl.toString(), bodyFatComparison)

            } catch (e: Exception) {
                _uiState.value = BodyScanUiState.Error("Upload failed: ${e.message}")
            }
        }
    }

    /**
     * Save scan information to Firestore
     */
    private suspend fun saveScanToFirestore(
        photoUrl: String,
        timestamp: Long
    ) {
        try {
            val scan = createScanWithMeasurements(
                uid = userId,
                photoUrl = photoUrl,
                timestamp = timestamp,
                measurements = bodyMeasurements
            )
            firebaseRepository.saveScan(scan)
        } catch (e: Exception) {
            // Log error but don't fail the upload for Firestore issues
            android.util.Log.e("BodyScanViewModel", "Failed to save scan to Firestore", e)
        }
    }

    /**
     * Create a scan object with body measurements
     */
    private fun createScanWithMeasurements(
        uid: String,
        photoUrl: String,
        timestamp: Long,
        measurements: BodyMeasurements?
    ): Scan {
        val scan = Scan.create(uid, photoUrl)

        // Add measurements if available
        measurements?.let { measurement ->
            // Update the scan with measurement data
            // Note: In a real implementation, you'd modify the Scan object or create a new one
            // For now, we'll store measurements in Firestore when saving
        }

        return scan
    }

    /**
     * Compare current scan with the most recent previous scan
     */
    private suspend fun compareWithPreviousScan(): BodyFatComparison? {
        return try {
            // Get the most recent scan (excluding the current one)
            val recentScansResult = firebaseRepository.getRecentScans(userId, limit = 2)
            if (recentScansResult.isFailure) return null

            val recentScans = recentScansResult.getOrNull() ?: return null
            val previousScan = recentScans.find { it.timestamp < System.currentTimeMillis() - 1000 } // Exclude very recent

            if (previousScan != null && bodyMeasurements != null) {
                // Create a temporary scan object for comparison
                val currentScanForComparison = createScanWithMeasurements(
                    uid = userId,
                    photoUrl = "", // Not needed for comparison
                    timestamp = System.currentTimeMillis(),
                    measurements = bodyMeasurements
                )

                currentScanForComparison.compareBodyFatWith(previousScan)
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("BodyScanViewModel", "Failed to compare with previous scan", e)
            null
        }
    }

    /**
     * Reset the UI state back to idle
     */
    fun resetState() {
        _uiState.value = BodyScanUiState.Idle
        currentPhotoUri = null
        currentPhotoFile = null
    }

    /**
     * Clean up temporary files
     */
    fun cleanup() {
        currentPhotoFile?.let { file ->
            if (file.exists()) {
                try {
                    file.delete()
                } catch (e: Exception) {
                    // Ignore cleanup errors
                }
            }
        }
        currentPhotoFile = null
        currentPhotoUri = null
    }

    /**
     * Get current photo URI for camera
     */
    fun getCurrentPhotoUri(): Uri? = currentPhotoUri

    /**
     * Factory for creating BodyScanViewModel
     */
    class Factory(
        private val context: Context,
        private val firebaseRepository: FirebaseRepository,
        private val preferencesManager: PreferencesManager,
        private val userId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BodyScanViewModel::class.java)) {
                return BodyScanViewModel(context, firebaseRepository, preferencesManager, userId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

/**
 * UI states for the BodyScan screen
 */
sealed class BodyScanUiState {
    object Idle : BodyScanUiState()
    object Analyzing : BodyScanUiState() // Analyzing pose in photo
    object Uploading : BodyScanUiState()
    data class PhotoTaken(val photoFile: File) : BodyScanUiState()
    data class Success(val photoUrl: String, val comparison: BodyFatComparison? = null) : BodyScanUiState()
    data class Error(val message: String) : BodyScanUiState()
}
