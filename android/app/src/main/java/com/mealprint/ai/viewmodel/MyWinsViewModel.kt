package com.coachie.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.data.model.HealthLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MyWinsViewModel : ViewModel() {

    companion object {
        private const val TAG = "MyWinsViewModel"
        private const val PAGE_SIZE = 20
    }

    private val firebaseRepository = FirebaseRepository.getInstance()

    // UI States
    private val _allWins = MutableStateFlow<List<HealthLog.WinEntry>>(emptyList()) // Store all loaded wins
    private val _wins = MutableStateFlow<List<HealthLog.WinEntry>>(emptyList()) // Filtered wins for display
    val wins: StateFlow<List<HealthLog.WinEntry>> = _wins.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    private val _showCalendar = MutableStateFlow(false)
    val showCalendar: StateFlow<Boolean> = _showCalendar.asStateFlow()

    private val _showConfetti = MutableStateFlow(false)
    val showConfetti: StateFlow<Boolean> = _showConfetti.asStateFlow()

    private val _confettiMessage = MutableStateFlow("")
    val confettiMessage: StateFlow<String> = _confettiMessage.asStateFlow()

    private var userId: String? = null
    private var hasShownConfetti = false

    fun initialize(userId: String) {
        this.userId = userId
        loadWins()

        // Check for confetti trigger (bad day encouragement)
        checkForConfetti()
    }

    /**
     * Load wins from Firebase with pagination
     */
    private fun loadWins() {
        val uid = userId ?: return

        viewModelScope.launch {
            _isLoading.value = true

            try {
                // Load all win entries (in a real app, you'd implement pagination)
                val allWins = mutableListOf<HealthLog.WinEntry>()

                // Get the last 90 days of data
                val endDate = LocalDate.now()
                val startDate = endDate.minusDays(90)

                for (i in 0..89) {
                    val date = startDate.plusDays(i.toLong())
                    val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

                    // Use getHealthLogsByType to specifically get WinEntry logs
                    val dayWinsResult = firebaseRepository.getHealthLogsByType(uid, dateString, HealthLog.WinEntry.TYPE)
                    val dayWins = dayWinsResult.getOrNull()?.filterIsInstance<HealthLog.WinEntry>() ?: emptyList()
                    allWins.addAll(dayWins)
                }

                // Sort by date descending (newest first)
                val sortedWins = allWins.sortedByDescending { it.timestamp }

                _allWins.value = sortedWins
                filterWins() // Apply any active filters
                Log.d(TAG, "Loaded ${sortedWins.size} wins for user: $uid")

            } catch (e: Exception) {
                Log.e(TAG, "Error loading wins", e)
                _wins.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Update search query and filter results
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        filterWins()
    }

    /**
     * Clear search query
     */
    fun clearSearch() {
        _searchQuery.value = ""
        filterWins()
    }

    /**
     * Select date filter
     */
    fun selectDate(date: LocalDate?) {
        _selectedDate.value = date
        filterWins()
    }

    /**
     * Toggle calendar visibility
     */
    fun toggleCalendar() {
        _showCalendar.value = !_showCalendar.value
    }

    /**
     * Filter wins based on search query and date
     */
    private fun filterWins() {
        val query = _searchQuery.value.lowercase()
        val selectedDate = _selectedDate.value

        // Start with all wins
        var filteredWins = _allWins.value

        // Apply search filter
        if (query.isNotBlank()) {
            filteredWins = filteredWins.filter { win ->
                win.win?.lowercase()?.contains(query) == true ||
                win.gratitude?.lowercase()?.contains(query) == true ||
                win.tags.any { tag -> tag.lowercase().contains(query) } ||
                win.mood?.lowercase()?.contains(query) == true
            }
        }

        // Apply date filter
        if (selectedDate != null) {
            val dateString = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
            filteredWins = filteredWins.filter { it.date == dateString }
        }

        // Update filtered results
        _wins.value = filteredWins
    }

    /**
     * Check if we should show confetti (bad day encouragement)
     */
    private fun checkForConfetti() {
        if (hasShownConfetti) return

        viewModelScope.launch {
            try {
                val recentWins = _wins.value.take(7) // Last 7 entries
                if (recentWins.isEmpty()) return@launch

                // Check if user has been having bad days recently
                val recentMoodScores = recentWins.mapNotNull { it.moodScore }
                val avgMoodScore = recentMoodScores.average()

                // Show confetti if average mood is low (≤2) but they still have wins
                if (avgMoodScore <= 2.0 && recentWins.any { it.win != null || it.gratitude != null }) {
                    _confettiMessage.value = "Even on your worst days, here's proof you're still winning. 🌟"
                    _showConfetti.value = true
                    hasShownConfetti = true
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error checking for confetti", e)
            }
        }
    }

    /**
     * Dismiss confetti overlay
     */
    fun dismissConfetti() {
        _showConfetti.value = false
    }

}
