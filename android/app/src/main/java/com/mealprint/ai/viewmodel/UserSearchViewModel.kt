package com.coachie.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.data.model.PublicUserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserSearchViewModel(
    private val repository: FirebaseRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<PublicUserProfile>>(emptyList())
    val searchResults: StateFlow<List<PublicUserProfile>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun searchUsers(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = repository.searchUsersByUsername(query)
            result.fold(
                onSuccess = { users ->
                    // Filter out current user
                    _searchResults.value = users.filter { it.uid != currentUserId }
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "Failed to search users"
                    _searchResults.value = emptyList()
                }
            )

            _isLoading.value = false
        }
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
        _error.value = null
    }

    class Factory(
        private val repository: FirebaseRepository,
        private val currentUserId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(UserSearchViewModel::class.java)) {
                return UserSearchViewModel(repository, currentUserId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

