package com.coachie.app.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Direct message between two users.
 * Stored in Firestore at: conversations/{conversationId}/messages/{messageId}
 */
data class Message(
    @DocumentId
    val id: String = "",
    
    @PropertyName("senderId")
    val senderId: String = "",
    
    @PropertyName("receiverId")
    val receiverId: String = "",
    
    @PropertyName("content")
    val content: String = "",
    
    @PropertyName("read")
    val read: Boolean = false,
    
    @PropertyName("createdAt")
    @ServerTimestamp
    val createdAt: Date? = null
)

/**
 * Conversation between two users.
 * Stored in Firestore at: conversations/{conversationId}
 * Conversation ID is generated as: min(userId1, userId2) + "_" + max(userId1, userId2)
 */
data class Conversation(
    @DocumentId
    val id: String = "",
    
    @PropertyName("participants")
    val participants: List<String> = emptyList(), // [userId1, userId2] sorted
    
    @PropertyName("lastMessage")
    val lastMessage: String? = null,
    
    @PropertyName("lastMessageAt")
    @ServerTimestamp
    val lastMessageAt: Date? = null,
    
    @PropertyName("unreadCount")
    val unreadCount: Map<String, Int> = emptyMap(), // Map of userId -> unread count
    
    @PropertyName("createdAt")
    @ServerTimestamp
    val createdAt: Date? = null,
    
    @PropertyName("updatedAt")
    @ServerTimestamp
    val updatedAt: Date? = null
) {
    /**
     * Get the other participant's ID
     */
    fun getOtherParticipant(currentUserId: String): String? {
        return participants.firstOrNull { it != currentUserId }
    }
    
    /**
     * Get unread count for current user
     */
    fun getUnreadCount(userId: String): Int {
        return unreadCount[userId] ?: 0
    }
}

