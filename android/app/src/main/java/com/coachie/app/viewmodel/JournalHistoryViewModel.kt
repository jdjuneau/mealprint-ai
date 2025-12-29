package com.coachie.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.HealthLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class JournalHistoryViewModel : ViewModel() {

    private val firebaseRepository = FirebaseRepository.getInstance()

    private val _journalEntries = MutableStateFlow<List<HealthLog.JournalEntry>>(emptyList())
    val journalEntries: StateFlow<List<HealthLog.JournalEntry>> = _journalEntries.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _expandedEntry = MutableStateFlow<String?>(null)
    val expandedEntry: StateFlow<String?> = _expandedEntry.asStateFlow()

    private var userId: String? = null

    fun initialize(userId: String) {
        this.userId = userId
        loadJournalHistory()
    }

    /**
     * Load journal entries from the last 90 days
     */
    private fun loadJournalHistory() {
        val uid = userId ?: return

        viewModelScope.launch {
            _isLoading.value = true

            try {
                // Get all daily logs from the past 90 days
                val journalEntries = mutableListOf<HealthLog.JournalEntry>()
                val calendar = java.util.Calendar.getInstance()

                // Load last 90 days
                for (i in 0..89) {
                    val date = calendar.time
                    val dateString = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        .format(date)

                    try {
                        val healthLogs = firebaseRepository.getHealthLogs(uid, dateString).getOrNull() ?: emptyList()
                        val journalsForDate = healthLogs.filterIsInstance<HealthLog.JournalEntry>()
                        journalEntries.addAll(journalsForDate)
                    } catch (e: Exception) {
                        // Skip this date if there's an error
                        continue
                    }

                    calendar.add(java.util.Calendar.DAY_OF_MONTH, -1)
                }

                // Sort by date descending (most recent first)
                _journalEntries.value = journalEntries.sortedByDescending { entry ->
                    try {
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                            .parse(entry.date)?.time ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e("JournalHistoryViewModel", "Error loading journal history", e)
                _journalEntries.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Toggle expanded state of a journal entry
     */
    fun toggleExpanded(entryId: String) {
        _expandedEntry.value = if (_expandedEntry.value == entryId) null else entryId
    }

    /**
     * Refresh the journal history
     */
    fun refresh() {
        loadJournalHistory()
    }
}
