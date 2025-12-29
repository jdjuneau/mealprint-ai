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

data class HabitCreationUiState(
    val habit: Habit? = null, // For editing existing habits
    val name: String = "",
    val description: String = "",
    val category: HabitCategory = HabitCategory.HEALTH,
    val frequency: HabitFrequency = HabitFrequency.DAILY,
    val priority: HabitPriority = HabitPriority.MEDIUM,
    val reminderTime: String = "09:00", // HH:MM format
    val targetValue: Int = 1,
    val unit: String = "",
    val isActive: Boolean = true,
    val reminderEnabled: Boolean = true,
    val notes: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isEditMode: Boolean = false
)

class HabitCreationViewModel(
    private val habitRepository: HabitRepository = HabitRepository.getInstance(),
    private val userId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitCreationUiState())
    val uiState: StateFlow<HabitCreationUiState> = _uiState.asStateFlow()

    fun initializeForEdit(habitId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val habit = habitRepository.getHabitById(userId, habitId).getOrNull()
                if (habit != null) {
                    _uiState.value = _uiState.value.copy(
                        habit = habit,
                        name = habit.title,
                        description = habit.description ?: "",
                        category = habit.category,
                        frequency = habit.frequency,
                        priority = habit.priority,
                        reminderTime = habit.reminderTime ?: "09:00",
                        targetValue = habit.targetValue,
                        unit = habit.unit ?: "",
                        isActive = habit.isActive,
                        reminderEnabled = habit.reminderTime != null,
                        notes = "",
                        isEditMode = true,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Habit not found",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load habit: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun updateCategory(category: HabitCategory) {
        _uiState.value = _uiState.value.copy(category = category)
    }

    fun updateFrequency(frequency: HabitFrequency) {
        _uiState.value = _uiState.value.copy(frequency = frequency)
    }

    fun updatePriority(priority: HabitPriority) {
        _uiState.value = _uiState.value.copy(priority = priority)
    }

    fun updateReminderTime(time: String) {
        _uiState.value = _uiState.value.copy(reminderTime = time)
    }

    fun updateTargetValue(value: Int) {
        _uiState.value = _uiState.value.copy(targetValue = value)
    }

    fun updateUnit(unit: String) {
        _uiState.value = _uiState.value.copy(unit = unit)
    }

    fun updateIsActive(active: Boolean) {
        _uiState.value = _uiState.value.copy(isActive = active)
    }

    fun updateReminderEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(reminderEnabled = enabled)
    }

    fun updateNotes(notes: String) {
        _uiState.value = _uiState.value.copy(notes = notes)
    }

    fun saveHabit(onSuccess: (String) -> Unit) {
        if (!validateHabit()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val state = _uiState.value
                val habit = Habit(
                    id = state.habit?.id ?: UUID.randomUUID().toString(),
                    userId = userId,
                    title = state.name.trim(),
                    description = state.description.trim().takeIf { it.isNotEmpty() } ?: "",
                    category = state.category,
                    frequency = state.frequency,
                    priority = state.priority,
                    reminderTime = state.reminderTime.takeIf { state.reminderEnabled },
                    targetValue = state.targetValue,
                    unit = state.unit.trim().takeIf { it.isNotEmpty() } ?: "",
                    isActive = state.isActive,
                    createdAt = state.habit?.createdAt ?: Date(),
                    updatedAt = Date()
                )

                if (state.isEditMode) {
                    habitRepository.updateHabit(userId, habit)
                } else {
                    habitRepository.createHabit(userId, habit)
                }

                _uiState.value = _uiState.value.copy(isLoading = false)
                onSuccess(habit.id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to save habit: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    private fun validateHabit(): Boolean {
        val state = _uiState.value

        if (state.name.trim().isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Habit name is required")
            return false
        }

        if (state.name.trim().length < 3) {
            _uiState.value = _uiState.value.copy(error = "Habit name must be at least 3 characters")
            return false
        }

        if (state.targetValue <= 0) {
            _uiState.value = _uiState.value.copy(error = "Target value must be greater than 0")
            return false
        }

        // Validate reminder time format
        if (state.reminderEnabled && !isValidTimeFormat(state.reminderTime)) {
            _uiState.value = _uiState.value.copy(error = "Invalid reminder time format")
            return false
        }

        return true
    }

    private fun isValidTimeFormat(time: String): Boolean {
        val regex = Regex("^([01]?\\d|2[0-3]):[0-5]\\d$")
        return regex.matches(time)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // Helper functions for UI
    fun getCategoryDisplayName(category: HabitCategory): String {
        return when (category) {
            HabitCategory.HEALTH -> "🏥 Health"
            HabitCategory.FITNESS -> "💪 Fitness"
            HabitCategory.NUTRITION -> "🥗 Nutrition"
            HabitCategory.SLEEP -> "😴 Sleep"
            HabitCategory.MENTAL_HEALTH -> "🧠 Mental Health"
            HabitCategory.PRODUCTIVITY -> "⚡ Productivity"
            HabitCategory.LEARNING -> "📚 Learning"
            HabitCategory.SOCIAL -> "👥 Social"
            HabitCategory.CREATIVE -> "🎨 Creative"
            HabitCategory.SPIRITUAL -> "🙏 Spiritual"
            HabitCategory.FINANCIAL -> "💰 Financial"
            HabitCategory.ENVIRONMENTAL -> "🌱 Environmental"
            HabitCategory.OTHER -> "📝 Other"
        }
    }

    fun getFrequencyDisplayName(frequency: HabitFrequency): String {
        return when (frequency) {
            HabitFrequency.DAILY -> "Daily"
            HabitFrequency.WEEKLY -> "Weekly"
            HabitFrequency.MONTHLY -> "Monthly"
            HabitFrequency.CUSTOM -> "Custom"
        }
    }

    fun getPriorityDisplayName(priority: HabitPriority): String {
        return when (priority) {
            HabitPriority.LOW -> "Low Priority"
            HabitPriority.MEDIUM -> "Medium Priority"
            HabitPriority.HIGH -> "High Priority"
            HabitPriority.CRITICAL -> "Critical Priority"
        }
    }

    fun getPriorityColor(priority: HabitPriority): androidx.compose.ui.graphics.Color {
        return when (priority) {
            HabitPriority.LOW -> androidx.compose.ui.graphics.Color(0xFF4CAF50) // Green
            HabitPriority.MEDIUM -> androidx.compose.ui.graphics.Color(0xFFFF9800) // Orange
            HabitPriority.HIGH -> androidx.compose.ui.graphics.Color(0xFFFF5722) // Deep Orange
            HabitPriority.CRITICAL -> androidx.compose.ui.graphics.Color(0xFFF44336) // Red
        }
    }
}
