package com.coachie.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.data.model.Scan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ScanManagementUiState {
    object Loading : ScanManagementUiState()
    data class Success(val scans: List<Scan>) : ScanManagementUiState()
    data class Error(val message: String) : ScanManagementUiState()
}

class ScanManagementViewModel(
    private val repository: FirebaseRepository,
    private val userId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScanManagementUiState>(ScanManagementUiState.Loading)
    val uiState: StateFlow<ScanManagementUiState> = _uiState

    init {
        loadScans()
    }

    fun loadScans() {
        _uiState.value = ScanManagementUiState.Loading

        viewModelScope.launch {
            try {
                val result = repository.getRecentScans(userId)
                if (result.isSuccess) {
                    val scans = result.getOrNull() ?: emptyList()
                    _uiState.value = ScanManagementUiState.Success(scans)
                } else {
                    _uiState.value = ScanManagementUiState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to load scans"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = ScanManagementUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun uploadScan(
        photoUrl: String,
        bodyFatEstimate: Double? = null,
        weight: Double? = null,
        height: Double? = null,
        notes: String? = null,
        scanType: String = Scan.SCAN_TYPE_FULL_BODY
    ) {
        viewModelScope.launch {
            try {
                val scan = Scan.create(
                    uid = userId,
                    photoUrl = photoUrl
                ).copy(
                    bodyFatEstimate = bodyFatEstimate,
                    weight = weight,
                    height = height,
                    notes = notes,
                    scanType = scanType
                )

                val result = repository.saveScan(scan)
                if (result.isSuccess) {
                    // Reload scans to show the new one
                    loadScans()
                } else {
                    _uiState.value = ScanManagementUiState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to save scan"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = ScanManagementUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun deleteScan(scan: Scan) {
        viewModelScope.launch {
            try {
                val result = repository.deleteScan(scan.uid, scan.timestamp)
                if (result.isSuccess) {
                    // Reload scans to remove the deleted one
                    loadScans()
                } else {
                    _uiState.value = ScanManagementUiState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to delete scan"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = ScanManagementUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    class Factory(
        private val repository: FirebaseRepository,
        private val userId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ScanManagementViewModel::class.java)) {
                return ScanManagementViewModel(repository, userId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
