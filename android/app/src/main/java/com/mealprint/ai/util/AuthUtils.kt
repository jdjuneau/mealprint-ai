package com.coachie.app.util

import com.google.firebase.auth.FirebaseAuth

/**
 * Utility for getting authenticated user ID.
 * This is the SINGLE SOURCE OF TRUTH for user authentication.
 * 
 * ROOT CAUSE FIX: Instead of passing userId as parameters (which can be wrong),
 * always use this utility to get the actual authenticated user's ID.
 */
object AuthUtils {
    /**
     * Get the current authenticated user's ID.
     * Returns null if user is not authenticated.
     * 
     * Use this for ALL Firestore operations that require authentication.
     */
    fun getAuthenticatedUserId(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
    }
    
    /**
     * Get the current authenticated user's ID, throwing if not authenticated.
     * Use this when authentication is REQUIRED for the operation.
     * 
     * @throws IllegalStateException if user is not authenticated
     */
    fun requireAuthenticatedUserId(): String {
        return getAuthenticatedUserId() 
            ?: throw IllegalStateException("User must be authenticated for this operation")
    }
    
    /**
     * Check if user is authenticated.
     */
    fun isAuthenticated(): Boolean {
        return getAuthenticatedUserId() != null
    }
}

