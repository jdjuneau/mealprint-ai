package com.coachie.app.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Circle data class representing a community circle for shared goals.
 * Stored in Firestore at: circles/{circleId}
 */
data class Circle(
    @DocumentId
    val id: String = "",
    
    @PropertyName("name")
    val name: String = "",
    
    @PropertyName("goal")
    val goal: String = "", // e.g., "Run a 5K", "Lose 10 lbs", "Meditate daily"
    
    @PropertyName("members")
    val members: List<String> = emptyList(), // Array of user UIDs
    
    @PropertyName("streak")
    val streak: Int = 0, // Current streak count for the circle
    
    @PropertyName("createdBy")
    val createdBy: String = "", // UID of the creator
    
    @PropertyName("tendency")
    val tendency: String? = null, // Optional: "early_bird", "night_owl", "consistent", etc.
    
    @PropertyName("maxMembers")
    val maxMembers: Int = 5, // Maximum number of members (default 5)
    
    @PropertyName("createdAt")
    @ServerTimestamp
    val createdAt: Date? = null,
    
    @PropertyName("updatedAt")
    @ServerTimestamp
    val updatedAt: Date? = null,
    
    @PropertyName("questId")
    val questId: String? = null, // Optional: Link to a quest if circle was created from a quest
    
    @PropertyName("questTitle")
    val questTitle: String? = null, // Quest title for display
    
    @PropertyName("questDescription")
    val questDescription: String? = null, // Quest description
    
    @PropertyName("questTarget")
    val questTarget: Int? = null, // Quest target value
    
    @PropertyName("questType")
    val questType: String? = null // Quest type (habit, streak, goal, challenge)
) {
    /**
     * Check if circle is full
     */
    val isFull: Boolean
        get() = members.size >= maxMembers
    
    /**
     * Check if user is a member
     */
    fun isMember(uid: String): Boolean = members.contains(uid)
    
    /**
     * Check if this circle is linked to a quest
     */
    val hasQuest: Boolean
        get() = questId != null
}

/**
 * Circle post data class for posts within a circle.
 * Stored in Firestore at: circles/{circleId}/posts/{postId}
 */
data class CirclePost(
    @DocumentId
    val id: String = "",

    @PropertyName("authorId")
    val authorId: String = "",

    @PropertyName("authorName")
    val authorName: String = "",

    @PropertyName("content")
    val content: String = "",

    @PropertyName("imageUrl")
    val imageUrl: String? = null,

    @PropertyName("likes")
    val likes: List<String> = emptyList(), // Array of user UIDs who liked

    @PropertyName("commentCount")
    val commentCount: Int = 0,

    @PropertyName("recipeId")
    val recipeId: String? = null, // Optional: Link to a recipe in sharedRecipes collection

    @PropertyName("createdAt")
    @ServerTimestamp
    val createdAt: Date? = null,

    @PropertyName("updatedAt")
    @ServerTimestamp
    val updatedAt: Date? = null
) {
    /**
     * Check if user liked this post
     */
    fun isLikedBy(uid: String): Boolean = likes.contains(uid)
}

/**
 * Circle comment data class for comments on circle posts.
 * Stored in Firestore at: circles/{circleId}/posts/{postId}/comments/{commentId}
 */
data class CircleComment(
    @DocumentId
    val id: String = "",

    @PropertyName("authorId")
    val authorId: String = "",

    @PropertyName("authorName")
    val authorName: String = "",

    @PropertyName("content")
    val content: String = "",

    @PropertyName("likes")
    val likes: List<String> = emptyList(), // Array of user UIDs who liked

    @PropertyName("createdAt")
    @ServerTimestamp
    val createdAt: Date? = null,

    @PropertyName("updatedAt")
    @ServerTimestamp
    val updatedAt: Date? = null
) {
    /**
     * Check if user liked this comment
     */
    fun isLikedBy(uid: String): Boolean = likes.contains(uid)
}

/**
 * Circle win data class for wins posted to circles.
 * Stored in Firestore at: circles/{circleId}/wins/{winId}
 */
data class CircleWin(
    @DocumentId
    val id: String = "",

    @PropertyName("uid")
    val uid: String = "", // User who posted the win

    @PropertyName("type")
    val type: String = "", // "habit", "streak", "goal"

    @PropertyName("message")
    val message: String = "", // The win message to display

    @PropertyName("habitId")
    val habitId: String? = null,

    @PropertyName("habitTitle")
    val habitTitle: String? = null,

    @PropertyName("streakCount")
    val streakCount: Int? = null,

    @PropertyName("goalTitle")
    val goalTitle: String? = null,

    @PropertyName("timestamp")
    @ServerTimestamp
    val timestamp: Date? = null,

    @PropertyName("reactions")
    val reactions: Map<String, List<String>>? = null, // "fire", "heart", "hug", "highFive" -> list of user IDs

    @PropertyName("pinned")
    val pinned: Boolean = false
)

/**
 * Forum data class for community forums.
 * Stored in Firestore at: forums/{forumId}
 */
data class Forum(
    @DocumentId
    val id: String = "",

    @PropertyName("title")
    val title: String = "",

    @PropertyName("description")
    val description: String = "",

    @PropertyName("category")
    val category: String = "", // "general", "fitness", "nutrition", "mental_health", etc.

    @PropertyName("createdBy")
    val createdBy: String = "",

    @PropertyName("createdByName")
    val createdByName: String = "",

    @PropertyName("postCount")
    val postCount: Int = 0,

    @PropertyName("lastPostAt")
    @ServerTimestamp
    val lastPostAt: Date? = null,

    @PropertyName("isActive")
    val isActive: Boolean = true,

    @PropertyName("createdAt")
    @ServerTimestamp
    val createdAt: Date? = null,

    @PropertyName("updatedAt")
    @ServerTimestamp
    val updatedAt: Date? = null
)

/**
 * Forum post data class for posts within forums.
 * Stored in Firestore at: forums/{forumId}/posts/{postId}
 */
data class ForumPost(
    @DocumentId
    val id: String = "",

    @PropertyName("title")
    val title: String = "",

    @PropertyName("content")
    val content: String = "",

    @PropertyName("authorId")
    val authorId: String = "",

    @PropertyName("authorName")
    val authorName: String = "",

    @PropertyName("forumId")
    val forumId: String = "",

    @PropertyName("forumTitle")
    val forumTitle: String = "",

    @PropertyName("likes")
    val likes: List<String> = emptyList(),

    @PropertyName("upvotes")
    val upvotes: List<String> = emptyList(), // List of user IDs who upvoted

    @PropertyName("commentCount")
    val commentCount: Int = 0,

    @PropertyName("viewCount")
    val viewCount: Int = 0,

    @PropertyName("isPinned")
    val isPinned: Boolean = false,

    @PropertyName("tags")
    val tags: List<String> = emptyList(),

    @PropertyName("recipeId")
    val recipeId: String? = null, // Optional: Link to a recipe in sharedRecipes collection

    @PropertyName("createdAt")
    @ServerTimestamp
    val createdAt: Date? = null,

    @PropertyName("updatedAt")
    @ServerTimestamp
    val updatedAt: Date? = null
) {
    /**
     * Check if user liked this post
     */
    fun isLikedBy(uid: String): Boolean = likes.contains(uid)
    
    /**
     * Check if user upvoted this post
     */
    fun isUpvotedBy(uid: String): Boolean = upvotes.contains(uid)
    
    /**
     * Get upvote count
     */
    val upvoteCount: Int
        get() = upvotes.size
}

/**
 * Forum comment data class for comments on forum posts.
 * Stored in Firestore at: forums/{forumId}/posts/{postId}/comments/{commentId}
 */
data class ForumComment(
    @DocumentId
    val id: String = "",

    @PropertyName("content")
    val content: String = "",

    @PropertyName("authorId")
    val authorId: String = "",

    @PropertyName("authorName")
    val authorName: String = "",

    @PropertyName("postId")
    val postId: String = "",

    @PropertyName("likes")
    val likes: List<String> = emptyList(),

    @PropertyName("createdAt")
    @ServerTimestamp
    val createdAt: Date? = null,

    @PropertyName("updatedAt")
    @ServerTimestamp
    val updatedAt: Date? = null
) {
    /**
     * Check if user liked this comment
     */
    fun isLikedBy(uid: String): Boolean = likes.contains(uid)
}

