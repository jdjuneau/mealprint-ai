package com.coachie.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.utils.DebugLogger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ViewModel for handling authentication operations
 */
class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firebaseRepository = FirebaseRepository.getInstance()

    // UI State
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    private val _authState = MutableStateFlow<AuthState>(com.coachie.app.viewmodel.AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState

    private val _userGoals = MutableStateFlow<Map<String, Any>?>(null)
    val userGoals: StateFlow<Map<String, Any>?> = _userGoals

    private val _areGoalsLoading = MutableStateFlow(true)
    val areGoalsLoading: StateFlow<Boolean> = _areGoalsLoading

    init {
        // CRITICAL: Add auth state listener to immediately sign out anonymous users
        // This listener runs whenever auth state changes (sign in, sign out, anonymous user created, etc.)
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _currentUser.value = user
            
            if (user != null) {
                // CRITICAL: Sign out anonymous users or users without email IMMEDIATELY
                if (user.isAnonymous || user.email.isNullOrBlank()) {
                    android.util.Log.w("AuthViewModel", "⚠️⚠️⚠️ CRITICAL: Found anonymous user or user without email - signing out IMMEDIATELY ⚠️⚠️⚠️")
                    android.util.Log.w("AuthViewModel", "User UID: ${user.uid}, isAnonymous: ${user.isAnonymous}, email: ${user.email}")
                    viewModelScope.launch {
                        auth.signOut()
                        _currentUser.value = null
                        _authState.value = com.coachie.app.viewmodel.AuthState.Unauthenticated
                        android.util.Log.w("AuthViewModel", "✅ Anonymous/invalid user signed out")
                    }
                } else {
                    // User has proper email authentication
                    android.util.Log.d("AuthViewModel", "✅ User has proper email authentication: ${user.email}")
                    _authState.value = com.coachie.app.viewmodel.AuthState.Authenticated(user)
                    checkUserGoals(user)
                }
            } else {
                _authState.value = com.coachie.app.viewmodel.AuthState.Unauthenticated
            }
        }
    }

    private fun checkUserGoals(user: FirebaseUser) {
        _areGoalsLoading.value = true
        viewModelScope.launch {
            try {
                val doc = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.uid)
                    .get()
                    .await()

                _userGoals.value = doc.data
                DebugLogger.logDebug("AuthViewModel", "Goals loaded: ${doc.data != null}")
            } catch (e: Exception) {
                DebugLogger.logDebug("AuthViewModel", "Failed to check user goals: ${e.message}")
                _userGoals.value = null
            } finally {
                _areGoalsLoading.value = false
            }
        }
    }

    /**
     * Sign in with email and password
     */
    fun signIn(email: String, password: String): Result<Unit> {
        return try {
            if (email.isBlank() || password.isBlank()) {
                Result.failure(IllegalArgumentException("Email and password cannot be empty"))
            } else {
                viewModelScope.launch {
                    _isLoading.value = true
                    _errorMessage.value = null

                    try {
                        DebugLogger.logUserInteraction("AuthViewModel", "Attempting sign in for: $email")

                        val result = auth.signInWithEmailAndPassword(email, password).await()
                        val user = result.user

                        if (user != null) {
                            DebugLogger.logDebug("AuthViewModel", "Sign in successful for user: ${user.uid}")

                            // If display name is not set, try to get it from Firestore
                            if (user.displayName.isNullOrBlank()) {
                                try {
                                    val profile = firebaseRepository.getUserProfile(user.uid)
                                    if (profile.isSuccess) {
                                        val userProfile = profile.getOrNull()
                                        if (userProfile != null && userProfile.name.isNotBlank()) {
                                            // Update Firebase user profile with display name from Firestore
                                            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                                .setDisplayName(userProfile.name)
                                                .build()
                                            user.updateProfile(profileUpdates).await()
                                            DebugLogger.logDebug("AuthViewModel", "Display name updated from Firestore: ${userProfile.name}")
                                        }
                                    }
                                } catch (e: Exception) {
                                    DebugLogger.logDebug("AuthViewModel", "Failed to fetch user profile for display name: ${e.message}")
                                }
                            }

                            _currentUser.value = user
                            _authState.value = AuthState.Authenticated(user)
                            checkUserGoals(user)
                            _isLoading.value = false
                            // Navigation will be handled by the UI layer
                        } else {
                            throw FirebaseAuthException("ERROR_USER_NOT_FOUND", "Sign in failed")
                        }

                    } catch (e: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                        DebugLogger.logDebug("AuthViewModel", "Sign in failed: email already in use")
                        _errorMessage.value = "This email is already registered. Try signing in."
                        _isLoading.value = false
                    } catch (e: com.google.firebase.auth.FirebaseAuthWeakPasswordException) {
                        DebugLogger.logDebug("AuthViewModel", "Sign in failed: weak password")
                        _errorMessage.value = "Password must be at least 6 characters."
                        _isLoading.value = false
                    } catch (e: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                        DebugLogger.logDebug("AuthViewModel", "Sign in failed: invalid credentials")
                        _errorMessage.value = "Invalid email format."
                        _isLoading.value = false
                    } catch (e: Exception) {
                        DebugLogger.logDebug("AuthViewModel", "Sign in failed: ${e.message}")
                        _errorMessage.value = e.message ?: "Authentication failed. Try again."
                        _isLoading.value = false
                    }
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sign up with name, email and password
     */
    fun signUp(name: String, email: String, password: String): Result<Unit> {
        return try {
            if (name.isBlank() || email.isBlank() || password.isBlank()) {
                Result.failure(IllegalArgumentException("Name, email and password cannot be empty"))
            } else if (password.length < 6) {
                Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
            } else {
                viewModelScope.launch {
                    _isLoading.value = true
                    _errorMessage.value = null

                    try {
                        DebugLogger.logUserInteraction("AuthViewModel", "Attempting sign up for: $email")

                        val result = auth.createUserWithEmailAndPassword(email, password).await()
                        val user = result.user

                        if (user != null) {
                            DebugLogger.logDebug("AuthViewModel", "Account created for user: ${user.uid}")

                            // Update Firebase user profile with display name
                            try {
                                val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .build()
                                user.updateProfile(profileUpdates).await()
                                DebugLogger.logDebug("AuthViewModel", "Firebase user display name updated")
                            } catch (e: Exception) {
                                DebugLogger.logDebug("AuthViewModel", "Failed to update display name: ${e.message}")
                            }

                            // Save user profile to Firestore
                            try {
                                val uid = result.user!!.uid
                                val currentTime = System.currentTimeMillis()
                                FirebaseFirestore.getInstance().collection("users").document(uid).set(
                                    mapOf(
                                        "uid" to uid,
                                        "name" to name,
                                        "email" to email,
                                        "createdAt" to currentTime,
                                        "startDate" to currentTime, // CRITICAL: Set startDate to prevent showing data from before account creation
                                        "authType" to "email",
                                        "isFirstTimeUser" to true,
                                        "goalsSet" to false,
                                        "platform" to "android", // CRITICAL: Set platform for Android users
                                        "platforms" to listOf("android") // CRITICAL: Set platforms array
                                    )
                                ).await()
                                DebugLogger.logDebug("AuthViewModel", "User profile saved to Firestore with startDate: $currentTime, platform: android")
                            } catch (e: Exception) {
                                DebugLogger.logDebug("AuthViewModel", "Failed to save user profile: ${e.message}")
                                // Don't fail the sign up if profile save fails
                            }

                            _currentUser.value = user
                            _authState.value = AuthState.Authenticated(user)
                            checkUserGoals(user)
                            _isLoading.value = false
                            // Navigation will be handled by the UI layer

                        } else {
                            throw FirebaseAuthException("ERROR_ACCOUNT_CREATION_FAILED", "Account creation failed")
                        }

                    } catch (e: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                        DebugLogger.logDebug("AuthViewModel", "Sign up failed: email already in use")
                        _errorMessage.value = "This email is already registered. Try signing in."
                        _isLoading.value = false
                    } catch (e: com.google.firebase.auth.FirebaseAuthWeakPasswordException) {
                        DebugLogger.logDebug("AuthViewModel", "Sign up failed: weak password")
                        _errorMessage.value = "Password must be at least 6 characters."
                        _isLoading.value = false
                    } catch (e: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                        DebugLogger.logDebug("AuthViewModel", "Sign up failed: invalid credentials")
                        _errorMessage.value = "Invalid email format."
                        _isLoading.value = false
                    } catch (e: Exception) {
                        DebugLogger.logDebug("AuthViewModel", "Sign up failed: ${e.message}")
                        _errorMessage.value = e.message ?: "Authentication failed. Try again."
                        _isLoading.value = false
                    }
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // REMOVED: signInAnonymously() - Anonymous authentication is NOT allowed
    // Users must sign in with email/password or Google

    /**
     * Sign out current user
     * @param preferencesManager Optional preferences manager to clear stored user data
     */
    fun signOut(preferencesManager: com.coachie.app.data.local.PreferencesManager? = null) {
        viewModelScope.launch {
            try {
                DebugLogger.logUserInteraction("AuthViewModel", "Signing out user")
                
                // CRITICAL SECURITY: Disconnect Google Fit before signing out
                // Google Fit connection must be user-specific, not device-wide
                // Note: Google Fit disconnect requires Activity context, so we'll handle it in MainActivity
                
                auth.signOut()
                // Clear stored user ID to prevent auto-login
                preferencesManager?.clearUserData()
                _currentUser.value = null
                _authState.value = AuthState.Unauthenticated
                _userGoals.value = null
                _errorMessage.value = null
                DebugLogger.logDebug("AuthViewModel", "Sign out successful")
            } catch (e: Exception) {
                DebugLogger.logDebug("AuthViewModel", "Sign out failed: ${e.message}")
                _errorMessage.value = e.message
            }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Force refresh user goals from Firebase
     */
    fun refreshUserGoals() {
        val user = auth.currentUser
        if (user != null) {
            checkUserGoals(user)
        }
    }

    /**
     * Convert Firebase Auth exceptions to user-friendly messages
     */
    private fun getAuthErrorMessage(exception: Exception): String {
        return when (exception) {
            is FirebaseAuthException -> {
                when (exception.errorCode) {
                    "ERROR_INVALID_EMAIL" -> "Invalid email address format"
                    "ERROR_USER_NOT_FOUND" -> "No account found with this email"
                    "ERROR_WRONG_PASSWORD" -> "Incorrect password"
                    "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with this email already exists"
                    "ERROR_WEAK_PASSWORD" -> "Password is too weak"
                    "ERROR_INVALID_CREDENTIAL" -> "Invalid login credentials"
                    "ERROR_USER_DISABLED" -> "This account has been disabled"
                    "ERROR_TOO_MANY_REQUESTS" -> "Too many failed attempts. Please try again later"
                    else -> "Authentication failed: ${exception.localizedMessage ?: "Unknown error"}"
                }
            }
            else -> exception.localizedMessage ?: "An unexpected error occurred"
        }
    }
}
