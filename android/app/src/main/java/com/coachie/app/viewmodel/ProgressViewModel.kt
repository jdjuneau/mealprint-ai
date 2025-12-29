package com.coachie.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.DailyLog
import com.coachie.app.data.model.UserProfile
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ProgressUiState {
    object Loading : ProgressUiState()
    data class Success(
        val userProfile: UserProfile? = null,
        val recentLogs: List<DailyLog> = emptyList(),
        val recentWeightChange: Double? = null
    ) : ProgressUiState()
    data class Error(val message: String) : ProgressUiState()
}

class ProgressViewModel(
    private val repository: FirebaseRepository,
    private val userId: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProgressUiState>(ProgressUiState.Loading)
    val uiState: StateFlow<ProgressUiState> = _uiState

    init {
        loadProgress()
    }

    fun loadProgress() {
        if (userId == null) {
            _uiState.value = ProgressUiState.Error("User not authenticated")
            return
        }

        _uiState.value = ProgressUiState.Loading

        viewModelScope.launch {
            try {
                coroutineScope {
                    val profileDeferred = async { repository.getUserProfile(userId) }
                    val logsDeferred = async { repository.getRecentDailyLogs(userId, limit = 7) }

                    val profileResult = profileDeferred.await()
                    val logsResult = logsDeferred.await()

                    if (profileResult.isSuccess || logsResult.isSuccess) {
                        val profile = profileResult.getOrNull()
                        val logs = logsResult.getOrNull() ?: emptyList()

                        // Calculate recent weight change
                        val recentWeightChange = calculateRecentWeightChange(logs)

                        _uiState.value = ProgressUiState.Success(
                            userProfile = profile,
                            recentLogs = logs,
                            recentWeightChange = recentWeightChange
                        )
                    } else {
                        val errorMessage = profileResult.exceptionOrNull()?.message
                            ?: logsResult.exceptionOrNull()?.message
                            ?: "Failed to load progress data"
                        _uiState.value = ProgressUiState.Error(errorMessage)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = ProgressUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun calculateRecentWeightChange(logs: List<DailyLog>): Double? {
        val logsWithWeight = logs.filter { it.weight != null }.sortedBy { it.date }

        return if (logsWithWeight.size >= 2) {
            val mostRecent = logsWithWeight.last().weight!!
            val previous = logsWithWeight[logsWithWeight.size - 2].weight!!
            mostRecent - previous
        } else {
            null
        }
    }

    class Factory(
        private val repository: FirebaseRepository,
        private val userId: String?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ProgressViewModel::class.java)) {
                return ProgressViewModel(repository, userId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
