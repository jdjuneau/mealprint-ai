package com.coachie.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.Conversation
import com.coachie.app.data.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MessagingViewModel(
    private val repository: FirebaseRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadConversations()
    }

    fun loadConversations() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = repository.getConversations(currentUserId)
            result.fold(
                onSuccess = { conversationsList ->
                    _conversations.value = conversationsList
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "Failed to load conversations"
                }
            )

            _isLoading.value = false
        }
    }

    fun loadMessages(conversationId: String) {
        viewModelScope.launch {
            _currentConversationId.value = conversationId
            _isLoading.value = true
            _error.value = null

            val result = repository.getMessages(conversationId)
            result.fold(
                onSuccess = { messagesList ->
                    _messages.value = messagesList
                    // Mark messages as read
                    repository.markMessagesAsRead(conversationId, currentUserId)
                },
                onFailure = { exception ->
                    // CRASH DEBUG: Look for this tag when messaging crashes
                    android.util.Log.e("MESSAGING_CRASH", "❌ FAILED TO LOAD MESSAGES", exception)
                    android.util.Log.e("MESSAGING_CRASH", "ConversationId: $conversationId", exception)
                    android.util.Log.e("MESSAGING_CRASH", "UserId: $currentUserId", exception)
                    _error.value = exception.message ?: "Failed to load messages"
                }
            )

            _isLoading.value = false
        }
    }

    fun sendMessage(receiverId: String, content: String) {
        if (content.isBlank() || receiverId.isBlank()) {
            return
        }

        viewModelScope.launch {
            try {
                _error.value = null
                val result = repository.sendMessage(currentUserId, receiverId, content)
                
                result.fold(
                    onSuccess = { conversationId ->
                        // Reload messages
                        loadMessages(conversationId)
                        loadConversations()
                    },
                    onFailure = { exception ->
                        // CRASH DEBUG: Look for this tag when messaging crashes
                        android.util.Log.e("MESSAGING_CRASH", "❌ FAILED TO SEND MESSAGE", exception)
                        android.util.Log.e("MESSAGING_CRASH", "ReceiverId: $receiverId | Content: ${content.take(50)}...", exception)
                        android.util.Log.e("MESSAGING_CRASH", "UserId: $currentUserId", exception)
                        _error.value = exception.message ?: "Failed to send message"
                    }
                )
            } catch (e: Exception) {
                // CRASH DEBUG: Look for this tag when messaging crashes
                android.util.Log.e("MESSAGING_CRASH", "❌ EXCEPTION IN sendMessage", e)
                android.util.Log.e("MESSAGING_CRASH", "ReceiverId: $receiverId | Content: ${content.take(50)}...", e)
                android.util.Log.e("MESSAGING_CRASH", "UserId: $currentUserId", e)
                _error.value = e.message ?: "Failed to send message"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearCurrentConversation() {
        _currentConversationId.value = null
        _messages.value = emptyList()
    }

    class Factory(
        private val repository: FirebaseRepository,
        private val currentUserId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MessagingViewModel::class.java)) {
                return MessagingViewModel(repository, currentUserId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

