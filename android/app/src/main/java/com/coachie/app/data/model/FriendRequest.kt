package com.coachie.app.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Friend request data class.
 * Stored in Firestore at: friendRequests/{requestId}
 */
data class FriendRequest(
    @DocumentId
    val id: String = "",
    
    @PropertyName("fromUserId")
    val fromUserId: String = "",
    
    @PropertyName("toUserId")
    val toUserId: String = "",
    
    @PropertyName("status")
    val status: String = "pending", // "pending", "accepted", "rejected"
    
    @PropertyName("message")
    val message: String? = null, // Optional message with the request
    
    @PropertyName("createdAt")
    @ServerTimestamp
    val createdAt: Date? = null,
    
    @PropertyName("updatedAt")
    @ServerTimestamp
    val updatedAt: Date? = null
) {
    val isPending: Boolean
        get() = status == "pending"
    
    val isAccepted: Boolean
        get() = status == "accepted"
    
    val isRejected: Boolean
        get() = status == "rejected"
}

