package com.coachie.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coachie.app.data.HabitRepository
import com.coachie.app.data.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HabitViewModel(
    private val habitRepository: HabitRepository,
    private val userId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitUiState())
    val uiState: StateFlow<HabitUiState> = _uiState.asStateFlow()

    // Use stateIn to share the Flow and avoid multiple collections
    // Use Eagerly to ensure the Flow is always collecting, so new habits appear immediately
    val habits: StateFlow<List<Habit>> = habitRepository.getHabits(userId)
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.Eagerly, // Always collect to pick up new habits immediately
            initialValue = emptyList()
        )

    private val _selectedHabit = MutableStateFlow<Habit?>(null)
    val selectedHabit: StateFlow<Habit?> = _selectedHabit.asStateFlow()

    private val _recentCompletions = MutableStateFlow<List<HabitCompletion>>(emptyList())
    val recentCompletions: StateFlow<List<HabitCompletion>> = _recentCompletions.asStateFlow()

    init {
        loadRecentCompletions()
        // Observe habits Flow to update UI state
        viewModelScope.launch {
            habits.collect { habitList ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = null
                )
                android.util.Log.d("HabitViewModel", "Habits updated: ${habitList.size} habits")
            }
        }
    }

    fun loadHabits(forceRefresh: Boolean = false) {
        // Habits are automatically loaded via the stateIn Flow
        // This function is kept for compatibility but doesn't need to do anything
        // The Flow will automatically update when data changes
        if (forceRefresh) {
            android.util.Log.d("HabitViewModel", "Force refresh requested (habits Flow will auto-update)")
            _uiState.value = _uiState.value.copy(isLoading = true)
        }
    }

    fun loadRecentCompletions() {
        viewModelScope.launch {
            try {
                habitRepository.getRecentCompletions(userId)
                    .collect { completions ->
                        _recentCompletions.value = completions
                    }
            } catch (e: Exception) {
                // Log error but don't update UI state for completions
                e.printStackTrace()
            }
        }
    }

    fun createHabit(
        title: String,
        description: String,
        category: HabitCategory = HabitCategory.HEALTH,
        frequency: HabitFrequency = HabitFrequency.DAILY,
        targetValue: Int = 1,
        unit: String = ""
    ) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                val habit = Habit(
                    userId = this@HabitViewModel.userId,
                    title = title,
                    description = description,
                    category = category,
                    frequency = frequency,
                    targetValue = targetValue,
                    unit = unit
                )

                val result = habitRepository.createHabit(this@HabitViewModel.userId, habit)

                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Habit created successfully!"
                    )
                    clearMessages()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to create habit"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to create habit"
                )
            }
        }
    }

    fun updateHabit(habit: Habit) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                val result = habitRepository.updateHabit(habit.userId, habit)

                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Habit updated successfully!"
                    )
                    clearMessages()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to update habit"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to update habit"
                )
            }
        }
    }

    fun deleteHabit(userId: String, habitId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                val result = habitRepository.deleteHabit(userId, habitId)

                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Habit deleted successfully!"
                    )
                    clearMessages()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to delete habit"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to delete habit"
                )
            }
        }
    }

    fun completeHabit(context: android.content.Context, userId: String, habitId: String, value: Int = 1, notes: String? = null) {
        viewModelScope.launch {
            try {
                val result = habitRepository.completeHabit(userId, habitId, value, notes)

                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Habit completed! 🎉"
                    )
                    clearMessages()

                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to complete habit"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Failed to complete habit"
                )
            }
        }
    }

    fun markHabitMissed(userId: String, habitId: String, reason: String? = null) {
        viewModelScope.launch {
            try {
                val result = habitRepository.markHabitMissed(userId, habitId, reason)

                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Habit marked as missed"
                    )
                    clearMessages()
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to mark habit as missed"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Failed to mark habit as missed"
                )
            }
        }
    }

    fun selectHabit(habit: Habit?) {
        _selectedHabit.value = habit
    }

    fun clearMessages() {
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            _uiState.value = _uiState.value.copy(
                successMessage = null,
                errorMessage = null
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

data class HabitUiState(
    val isLoading: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val showCreateDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val showDeleteDialog: Boolean = false
)
