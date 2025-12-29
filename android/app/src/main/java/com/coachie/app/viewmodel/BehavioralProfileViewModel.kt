package com.coachie.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coachie.app.data.HabitRepository
import com.coachie.app.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

data class BehavioralProfileUiState(
    val currentStep: Int = 0,
    val totalSteps: Int = 4,
    val isLoading: Boolean = false,
    val error: String? = null,

    // Four Tendencies Quiz
    val fourTendenciesAnswers: Map<String, Int> = emptyMap(), // question -> score
    val fourTendenciesResult: FourTendenciesResult? = null,

    // Reward Preferences
    val selectedRewards: Set<RewardType> = emptySet(),

    // Keystone Habits
    val keystoneHabits: List<String> = emptyList(),
    val customKeystoneHabit: String = "",

    // Biggest Frictions
    val selectedFrictions: Set<String> = emptySet(),
    val customFriction: String = "",

    // Completion
    val profileCompleted: Boolean = false
)


class BehavioralProfileViewModel(
    private val habitRepository: HabitRepository = HabitRepository.getInstance(),
    private val userId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(BehavioralProfileUiState())
    val uiState: StateFlow<BehavioralProfileUiState> = _uiState.asStateFlow()

    private val fourTendenciesQuestions = listOf(
        "When someone asks you to do something, your first reaction is:" to listOf(
            "I want to understand why this matters to me" to FourTendencies.QUESTIONER,
            "I feel obligated to do it" to FourTendencies.OBLIGER,
            "I'll do it if it aligns with my goals" to FourTendencies.UPHOLDER,
            "I resist being told what to do" to FourTendencies.REBEL
        ),
        "When you set a goal for yourself, you:" to listOf(
            "Research and plan thoroughly first" to FourTendencies.QUESTIONER,
            "Feel committed and follow through" to FourTendencies.UPHOLDER,
            "Do it only if others are counting on you" to FourTendencies.OBLIGER,
            "Do it your own way or not at all" to FourTendencies.REBEL
        ),
        "Your approach to rules and expectations is:" to listOf(
            "I follow rules that make sense to me" to FourTendencies.QUESTIONER,
            "I meet both internal and external expectations" to FourTendencies.UPHOLDER,
            "I prioritize others' expectations over my own" to FourTendencies.OBLIGER,
            "I prefer to make my own rules" to FourTendencies.REBEL
        ),
        "When you fail to meet an expectation, you feel:" to listOf(
            "I need to understand what went wrong" to FourTendencies.QUESTIONER,
            "Disappointed in myself for not following through" to FourTendencies.UPHOLDER,
            "Guilty for letting others down" to FourTendencies.OBLIGER,
            "Frustrated by the pressure to conform" to FourTendencies.REBEL
        ),
        "Your motivation style is best described as:" to listOf(
            "Understanding the 'why' behind everything" to FourTendencies.QUESTIONER,
            "Self-discipline and personal standards" to FourTendencies.UPHOLDER,
            "Responsibility to others and commitments" to FourTendencies.OBLIGER,
            "Inner drive and personal freedom" to FourTendencies.REBEL
        )
    )

    private val predefinedFrictions = listOf(
        "Lack of time",
        "Lack of motivation",
        "Social pressure/influence",
        "Environmental factors",
        "Lack of accountability",
        "Inconsistent routine",
        "Poor habit stacking",
        "Lack of clear goals",
        "Stress and overwhelm",
        "Lack of support system"
    )

    init {
        loadExistingProfile()
    }

    private fun loadExistingProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val profile = habitRepository.getUserHabitProfile(userId).getOrNull()
                if (profile != null) {
                    // Profile exists, pre-populate if needed
                    _uiState.value = _uiState.value.copy(
                        fourTendenciesResult = profile.fourTendencies,
                        selectedRewards = profile.rewardPreferences.toSet(),
                        keystoneHabits = profile.keystoneHabits,
                        selectedFrictions = profile.biggestFrictions.toSet(),
                        profileCompleted = profile.profileCompleted,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load existing profile: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun answerFourTendenciesQuestion(questionIndex: Int, selectedTendency: FourTendencies) {
        val currentAnswers = _uiState.value.fourTendenciesAnswers.toMutableMap()
        currentAnswers[questionIndex.toString()] = selectedTendency.ordinal

        // Calculate current result
        val tendencyCounts = mutableMapOf<FourTendencies, Int>()
        currentAnswers.values.forEach { tendencyOrdinal ->
            val tendency = FourTendencies.values()[tendencyOrdinal]
            tendencyCounts[tendency] = tendencyCounts.getOrDefault(tendency, 0) + 1
        }

        val dominantTendency = tendencyCounts.maxByOrNull { it.value }?.key ?: FourTendencies.QUESTIONER
        val scores = FourTendencies.values().associate { tendency ->
            tendency.name to tendencyCounts.getOrDefault(tendency, 0)
        }

        val result = FourTendenciesResult(
            tendency = dominantTendency,
            scores = scores,
            assessedAt = Date()
        )

        _uiState.value = _uiState.value.copy(
            fourTendenciesAnswers = currentAnswers,
            fourTendenciesResult = result
        )
    }

    fun toggleRewardPreference(reward: RewardType) {
        val currentRewards = _uiState.value.selectedRewards.toMutableSet()
        if (currentRewards.contains(reward)) {
            currentRewards.remove(reward)
        } else {
            currentRewards.add(reward)
        }
        _uiState.value = _uiState.value.copy(selectedRewards = currentRewards)
    }

    fun addKeystoneHabit(habit: String) {
        if (habit.isNotBlank() && !_uiState.value.keystoneHabits.contains(habit)) {
            val newHabits = _uiState.value.keystoneHabits + habit
            _uiState.value = _uiState.value.copy(keystoneHabits = newHabits, customKeystoneHabit = "")
        }
    }

    fun removeKeystoneHabit(habit: String) {
        val newHabits = _uiState.value.keystoneHabits - habit
        _uiState.value = _uiState.value.copy(keystoneHabits = newHabits)
    }

    fun updateCustomKeystoneHabit(habit: String) {
        _uiState.value = _uiState.value.copy(customKeystoneHabit = habit)
    }

    fun toggleFriction(friction: String) {
        val currentFrictions = _uiState.value.selectedFrictions.toMutableSet()
        if (currentFrictions.contains(friction)) {
            currentFrictions.remove(friction)
        } else {
            currentFrictions.add(friction)
        }
        _uiState.value = _uiState.value.copy(selectedFrictions = currentFrictions)
    }

    fun updateCustomFriction(friction: String) {
        _uiState.value = _uiState.value.copy(customFriction = friction)
    }

    fun addCustomFriction() {
        if (_uiState.value.customFriction.isNotBlank()) {
            toggleFriction(_uiState.value.customFriction)
            _uiState.value = _uiState.value.copy(customFriction = "")
        }
    }

    fun nextStep() {
        val currentStep = _uiState.value.currentStep
        if (currentStep < _uiState.value.totalSteps - 1) {
            _uiState.value = _uiState.value.copy(currentStep = currentStep + 1)
        }
    }

    fun previousStep() {
        val currentStep = _uiState.value.currentStep
        if (currentStep > 0) {
            _uiState.value = _uiState.value.copy(currentStep = currentStep - 1)
        }
    }

    fun saveProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val profile = UserHabitProfile(
                    fourTendencies = _uiState.value.fourTendenciesResult,
                    rewardPreferences = _uiState.value.selectedRewards.toList(),
                    keystoneHabits = _uiState.value.keystoneHabits,
                    biggestFrictions = _uiState.value.selectedFrictions.toList(),
                    profileCompleted = true,
                    profileCompletionDate = Date()
                )

                habitRepository.updateUserHabitProfile(userId, profile)
                _uiState.value = _uiState.value.copy(
                    profileCompleted = true,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to save profile: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun getCurrentStepData(): StepData {
        return when (_uiState.value.currentStep) {
            0 -> StepData.FourTendenciesStep(
                questions = fourTendenciesQuestions,
                currentAnswers = _uiState.value.fourTendenciesAnswers,
                result = _uiState.value.fourTendenciesResult
            )
            1 -> StepData.RewardPreferencesStep(
                allRewards = RewardType.values().toList(),
                selectedRewards = _uiState.value.selectedRewards
            )
            2 -> StepData.KeystoneHabitsStep(
                habits = _uiState.value.keystoneHabits,
                customHabit = _uiState.value.customKeystoneHabit
            )
            3 -> StepData.FrictionsStep(
                predefinedFrictions = predefinedFrictions,
                selectedFrictions = _uiState.value.selectedFrictions,
                customFriction = _uiState.value.customFriction
            )
            else -> throw IllegalStateException("Invalid step")
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

sealed class StepData {
    data class FourTendenciesStep(
        val questions: List<Pair<String, List<Pair<String, FourTendencies>>>>,
        val currentAnswers: Map<String, Int>,
        val result: FourTendenciesResult?
    ) : StepData()

    data class RewardPreferencesStep(
        val allRewards: List<RewardType>,
        val selectedRewards: Set<RewardType>
    ) : StepData()

    data class KeystoneHabitsStep(
        val habits: List<String>,
        val customHabit: String
    ) : StepData()

    data class FrictionsStep(
        val predefinedFrictions: List<String>,
        val selectedFrictions: Set<String>,
        val customFriction: String
    ) : StepData()
}
