package com.coachie.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.UserProfile
import com.coachie.app.utils.DebugLogger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CoachieViewModel : ViewModel() {

    private val repository = FirebaseRepository.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // UI State
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile

    init {
        DebugLogger.logDebug("CoachieViewModel", "Initializing CoachieViewModel")
        DebugLogger.logAuthStatus()

        // Listen to authentication state changes
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            val newState = if (user != null) {
                DebugLogger.logDebug("CoachieViewModel", "User authenticated: ${user.uid}")
                loadUserProfile(user.uid)
                AuthState.Authenticated(user)
            } else {
                DebugLogger.logDebug("CoachieViewModel", "User not authenticated")
                AuthState.Unauthenticated
            }
            DebugLogger.logStateChange("CoachieViewModel", "authState", _authState.value, newState)
            _authState.value = newState
        }
    }

    // Authentication Methods
    fun signUp(email: String, password: String) {
        DebugLogger.logUserInteraction("CoachieViewModel", "Sign up attempted with email: ${email.take(3)}***")
        DebugLogger.logStateChange("CoachieViewModel", "authState", _authState.value, AuthState.Loading)

        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                DebugLogger.logFirebaseOperation("CoachieViewModel", "createUserWithEmailAndPassword", null)
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                DebugLogger.logFirebaseOperation("CoachieViewModel", "createUserWithEmailAndPassword", true)
                // User profile will be loaded automatically via auth state listener
            } catch (e: Exception) {
                DebugLogger.logFirebaseOperation("CoachieViewModel", "createUserWithEmailAndPassword", false)
                DebugLogger.logDebug("CoachieViewModel", "Sign up failed: ${e.message}")
                val errorState = AuthState.Error(e.message ?: "Sign up failed")
                DebugLogger.logStateChange("CoachieViewModel", "authState", _authState.value, errorState)
                _authState.value = errorState
            }
        }
    }

    fun signIn(email: String, password: String) {
        DebugLogger.logUserInteraction("CoachieViewModel", "Sign in attempted with email: ${email.take(3)}***")
        DebugLogger.logStateChange("CoachieViewModel", "authState", _authState.value, AuthState.Loading)

        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                DebugLogger.logFirebaseOperation("CoachieViewModel", "signInWithEmailAndPassword", null)
                auth.signInWithEmailAndPassword(email, password).await()
                DebugLogger.logFirebaseOperation("CoachieViewModel", "signInWithEmailAndPassword", true)
                // User profile will be loaded automatically via auth state listener
            } catch (e: Exception) {
                DebugLogger.logFirebaseOperation("CoachieViewModel", "signInWithEmailAndPassword", false)
                DebugLogger.logDebug("CoachieViewModel", "Sign in failed: ${e.message}")
                val errorState = AuthState.Error(e.message ?: "Sign in failed")
                DebugLogger.logStateChange("CoachieViewModel", "authState", _authState.value, errorState)
                _authState.value = errorState
            }
        }
    }

    fun signOut() {
        DebugLogger.logUserInteraction("CoachieViewModel", "Sign out requested")
        DebugLogger.logFirebaseOperation("CoachieViewModel", "signOut", null)
        auth.signOut()
        DebugLogger.logFirebaseOperation("CoachieViewModel", "signOut", true)
    }

    // Data Methods
    private fun loadUserProfile(userId: String) {
        DebugLogger.logFirebaseOperation("CoachieViewModel", "getUserProfile", null)
        viewModelScope.launch {
            try {
                val result = repository.getUserProfile(userId)
                if (result.isSuccess) {
                    DebugLogger.logFirebaseOperation("CoachieViewModel", "getUserProfile", true)
                    val profile = result.getOrNull()
                    DebugLogger.logStateChange("CoachieViewModel", "userProfile", _userProfile.value, profile)
                    _userProfile.value = profile
                } else {
                    DebugLogger.logFirebaseOperation("CoachieViewModel", "getUserProfile", false)
                    DebugLogger.logDebug("CoachieViewModel", "User profile not found or error: ${result.exceptionOrNull()?.message}")
                    DebugLogger.logStateChange("CoachieViewModel", "userProfile", _userProfile.value, null)
                    _userProfile.value = null
                }
            } catch (e: Exception) {
                DebugLogger.logFirebaseOperation("CoachieViewModel", "getUserProfile", false)
                DebugLogger.logDebug("CoachieViewModel", "Error loading user profile: ${e.message}")
                DebugLogger.logStateChange("CoachieViewModel", "userProfile", _userProfile.value, null)
                _userProfile.value = null
            }
        }
    }

    fun createUserProfile(profile: UserProfile) {
        DebugLogger.logUserInteraction("CoachieViewModel", "Creating user profile for: ${profile.name}")
        DebugLogger.logFirebaseOperation("CoachieViewModel", "saveUserProfile", null)

        viewModelScope.launch {
            try {
                val result = repository.saveUserProfile(profile)
                if (result.isSuccess) {
                    DebugLogger.logFirebaseOperation("CoachieViewModel", "saveUserProfile", true)
                    DebugLogger.logStateChange("CoachieViewModel", "userProfile", _userProfile.value, profile)
                    _userProfile.value = profile // Update local state
                } else {
                    DebugLogger.logFirebaseOperation("CoachieViewModel", "saveUserProfile", false)
                    DebugLogger.logDebug("CoachieViewModel", "Failed to save user profile: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                DebugLogger.logFirebaseOperation("CoachieViewModel", "saveUserProfile", false)
                DebugLogger.logDebug("CoachieViewModel", "Error creating user profile: ${e.message}")
            }
        }
    }

    fun saveWorkout(workoutId: String, workoutData: Map<String, Any>) {
        DebugLogger.logUserInteraction("CoachieViewModel", "Saving workout: $workoutId")
        DebugLogger.logFirebaseOperation("CoachieViewModel", "saveWorkout", null)

        viewModelScope.launch {
            try {
                repository.saveWorkout(workoutId, workoutData)
                DebugLogger.logFirebaseOperation("CoachieViewModel", "saveWorkout", true)
            } catch (e: Exception) {
                DebugLogger.logFirebaseOperation("CoachieViewModel", "saveWorkout", false)
                DebugLogger.logDebug("CoachieViewModel", "Error saving workout: ${e.message}")
            }
        }
    }

    fun getUserWorkouts(userId: String, onResult: (List<Map<String, Any>>) -> Unit) {
        DebugLogger.logFirebaseOperation("CoachieViewModel", "getUserWorkouts", null)

        repository.getUserWorkouts(userId)
            .addOnSuccessListener { querySnapshot ->
                DebugLogger.logFirebaseOperation("CoachieViewModel", "getUserWorkouts", true)
                val workouts = querySnapshot.documents.mapNotNull { it.data }
                DebugLogger.logDebug("CoachieViewModel", "Retrieved ${workouts.size} workouts")
                onResult(workouts)
            }
            .addOnFailureListener { e ->
                DebugLogger.logFirebaseOperation("CoachieViewModel", "getUserWorkouts", false)
                DebugLogger.logDebug("CoachieViewModel", "Error getting user workouts: ${e.message}")
                onResult(emptyList())
            }
    }
}

// Authentication States
sealed class AuthState {
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(val user: FirebaseUser) : AuthState()
    data class Error(val message: String) : AuthState()
}
