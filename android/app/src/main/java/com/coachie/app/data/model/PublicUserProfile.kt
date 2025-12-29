package com.coachie.app.data.model

/**
 * Public user profile for search and friend lists.
 * Contains only information that should be visible to other users.
 */
data class PublicUserProfile(
    val uid: String = "",
    val username: String = "",
    val displayName: String = "",
    val photoUrl: String? = null
)

