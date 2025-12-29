package com.coachie.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.FriendRequest
import com.coachie.app.data.model.PublicUserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FriendsViewModel(
    private val repository: FirebaseRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _friends = MutableStateFlow<List<PublicUserProfile>>(emptyList())
    val friends: StateFlow<List<PublicUserProfile>> = _friends.asStateFlow()

    private val _pendingRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val pendingRequests: StateFlow<List<FriendRequest>> = _pendingRequests.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadFriends()
        loadPendingRequests()
    }

    fun loadFriends() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = repository.getFriends(currentUserId)
            result.fold(
                onSuccess = { friendsList ->
                    _friends.value = friendsList
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "Failed to load friends"
                }
            )

            _isLoading.value = false
        }
    }

    fun loadPendingRequests() {
        viewModelScope.launch {
            val result = repository.getPendingFriendRequests(currentUserId)
            result.fold(
                onSuccess = { requests ->
                    _pendingRequests.value = requests
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "Failed to load friend requests"
                }
            )
        }
    }

    fun acceptFriendRequest(requestId: String) {
        viewModelScope.launch {
            val result = repository.acceptFriendRequest(requestId, currentUserId)
            result.fold(
                onSuccess = {
                    loadFriends()
                    loadPendingRequests()
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "Failed to accept friend request"
                }
            )
        }
    }

    fun rejectFriendRequest(requestId: String) {
        viewModelScope.launch {
            val result = repository.rejectFriendRequest(requestId, currentUserId)
            result.fold(
                onSuccess = {
                    loadPendingRequests()
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "Failed to reject friend request"
                }
            )
        }
    }

    fun sendFriendRequest(toUserId: String, message: String? = null) {
        viewModelScope.launch {
            val result = repository.sendFriendRequest(currentUserId, toUserId, message)
            result.fold(
                onSuccess = {
                    // Success - could show a toast or update UI
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "Failed to send friend request"
                }
            )
        }
    }

    fun removeFriend(friendId: String) {
        viewModelScope.launch {
            val result = repository.removeFriend(currentUserId, friendId)
            result.fold(
                onSuccess = {
                    loadFriends()
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "Failed to remove friend"
                }
            )
        }
    }

    fun clearError() {
        _error.value = null
    }

    class Factory(
        private val repository: FirebaseRepository,
        private val currentUserId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FriendsViewModel::class.java)) {
                return FriendsViewModel(repository, currentUserId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

