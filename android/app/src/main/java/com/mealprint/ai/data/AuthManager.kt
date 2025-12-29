package com.coachie.app.data

import android.util.Log
import com.mealprint.ai.data.local.PreferencesManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Authentication manager for handling Firebase Authentication.
 * CRITICAL: NO ANONYMOUS AUTHENTICATION - Only email/password or Google sign-in allowed.
 * Manages sign-in state and persists user ID to SharedPreferences.
 */
class AuthManager(
    private val preferencesManager: PreferencesManager,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    companion object {
        private const val TAG = "AuthManager"
    }

    /**
     * Initialize authentication on app startup.
     * CRITICAL: NO ANONYMOUS AUTHENTICATION - Only check for existing valid users with email.
     */
    suspend fun initializeAuth(): AuthResult {
        return try {
            // CRITICAL: Check if there's a current user and sign out if anonymous or missing email
            val currentUser = auth.currentUser
            if (currentUser != null) {
                // CRITICAL: Sign out anonymous users or users without email immediately
                if (currentUser.isAnonymous || currentUser.email.isNullOrBlank()) {
                    Log.w(TAG, "⚠️ Found anonymous user or user without email - signing out immediately")
                    auth.signOut()
                    preferencesManager.clearUserData()
                    return AuthResult.Error(Exception("Anonymous or invalid user signed out"))
                }
                
                // User has proper email authentication
                Log.d(TAG, "User already authenticated with email: ${currentUser.email}")
                AuthResult.Success(currentUser)
            } else {
                // No user - return error (user should use login screen)
                Log.d(TAG, "No authenticated user found - user should sign in")
                AuthResult.Error(Exception("No authenticated user"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auth initialization failed", e)
            AuthResult.Error(e)
        }
    }

    /**
     * Get current authenticated user.
     */
    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    /**
     * Check if user is currently authenticated.
     */
    fun isAuthenticated(): Boolean = auth.currentUser != null

    /**
     * Sign out current user and clear stored data.
     */
    fun signOut() {
        auth.signOut()
        preferencesManager.clearUserData()
        Log.d(TAG, "User signed out and data cleared")
    }

    /**
     * Get stored user ID (may be different from current Firebase user).
     */
    fun getStoredUserId(): String? = preferencesManager.userId
}

/**
 * Result of authentication operations.
 */
sealed class AuthResult {
    data class Success(val user: FirebaseUser) : AuthResult()
    data class Error(val exception: Exception) : AuthResult()
}
