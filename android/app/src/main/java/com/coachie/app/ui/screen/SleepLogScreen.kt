package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.ui.theme.LocalGender
import com.coachie.app.data.model.HealthLog
import com.coachie.app.utils.DebugLogger
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.coachie.app.ui.components.CoachieCard as Card
import com.coachie.app.ui.components.CoachieCardDefaults as CardDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepLogScreen(
    userId: String? = null,
    onBack: () -> Unit = {},
    viewModel: SleepLogViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = SleepLogViewModel.Factory(FirebaseRepository.getInstance(), userId ?: com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: "anonymous")
    )
) {
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val currentUserId = userId ?: com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: return
    val startTime by viewModel.startTime.collectAsState()
    val endTime by viewModel.endTime.collectAsState()
    val quality by viewModel.quality.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveMessage by viewModel.saveMessage.collectAsState()

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val gender = LocalGender.current
    val isMale = gender.lowercase() == "male"
    val textColor = if (isMale) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    val sleepQualities = listOf(
        SleepQuality(1, "😴", "Poor", "Barely slept", Color(0xFFFF6B6B)),
        SleepQuality(2, "😟", "Fair", "Restless night", Color(0xFFFFB86C)),
        SleepQuality(3, "😐", "Okay", "Decent sleep", Color(0xFFF1FA8C)),
        SleepQuality(4, "😊", "Good", "Well rested", Color(0xFF50FA7B)),
        SleepQuality(5, "🌟", "Excellent", "Perfect sleep", Color(0xFFBD93F9))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "🌙 Sweet Dreams",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.Black,
                    actionIconContentColor = Color.Black,
                    navigationIconContentColor = Color.Black
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBackground)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "💤 Track Your Sleep",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Help us understand your sleep patterns to give you better wellness insights",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = Color.Black
                    )
                }
            }

            // Start Time Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🛏️ When did you go to bed?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    TimePickerCard(
                        label = "Bedtime",
                        time = startTime,
                        onTimeChange = { viewModel.updateStartTime(it) },
                        placeholder = "Tap to set bedtime"
                    )
                }
            }

            // End Time Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🌅 When did you wake up?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    TimePickerCard(
                        label = "Wake time",
                        time = endTime,
                        onTimeChange = { viewModel.updateEndTime(it) },
                        placeholder = "Tap to set wake time"
                    )
                }
            }

            // Sleep Quality Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "✨ How was your sleep?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Text(
                        text = "Rate your sleep quality",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sleepQualities.forEach { sleepQuality ->
                            SleepQualityButton(
                                quality = sleepQuality,
                                isSelected = quality == sleepQuality.rating,
                                onClick = { viewModel.updateQuality(sleepQuality.rating) }
                            )
                        }
                    }
                }
            }

            // Duration Preview (if both times are set)
            if (startTime.isNotBlank() && endTime.isNotBlank()) {
                val duration = calculateDuration(startTime, endTime)
                if (duration != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⏱️",
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Sleep Duration",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = duration,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Save Message
            saveMessage?.let { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (message.contains("success", ignoreCase = true))
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = message,
                        color = if (message.contains("success", ignoreCase = true))
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Save Button
            Button(
                onClick = {
                    scope.launch {
                        viewModel.saveSleep(currentUserId)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving && startTime.isNotBlank() && endTime.isNotBlank() && quality > 0
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isSaving) "💾 Saving..." else "💤 Save Sleep Log")
            }
            }
        }
    }
}

// Data class for sleep quality
data class SleepQuality(
    val rating: Int,
    val emoji: String,
    val title: String,
    val description: String,
    val color: Color
)

// Time picker card composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerCard(
    label: String,
    time: String,
    onTimeChange: (String) -> Unit,
    placeholder: String
) {
    var showTimePicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(
        initialHour = 22,
        initialMinute = 0,
        is24Hour = false
    )

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select $label") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(onClick = {
                    val hour = timePickerState.hour
                    val minute = timePickerState.minute
                    val formattedTime = String.format("%02d:%02d", hour, minute)
                    onTimeChange(formattedTime)
                    showTimePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showTimePicker = true },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🕐",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (time.isBlank()) placeholder else time,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (time.isBlank())
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = "Select time",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// Sleep quality button composable
@Composable
private fun SleepQualityButton(
    quality: SleepQuality,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected)
                quality.color.copy(alpha = 0.2f)
            else
                Color.Unspecified
        ),
        border = CardDefaults.outlinedCardBorder(
            enabled = isSelected
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = quality.emoji,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = quality.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = quality.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = quality.color,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// Helper function to calculate sleep duration
private fun calculateDuration(startTime: String, endTime: String): String? {
    return try {
        val startParts = startTime.split(":").map { it.toInt() }
        val endParts = endTime.split(":").map { it.toInt() }

        if (startParts.size != 2 || endParts.size != 2) return null

        val startHour = startParts[0]
        val startMinute = startParts[1]
        val endHour = endParts[0]
        val endMinute = endParts[1]

        val startTotalMinutes = startHour * 60 + startMinute
        var endTotalMinutes = endHour * 60 + endMinute

        // Handle overnight sleep
        if (endTotalMinutes < startTotalMinutes) {
            endTotalMinutes += 24 * 60 // Add 24 hours
        }

        val durationMinutes = endTotalMinutes - startTotalMinutes
        val hours = durationMinutes / 60
        val minutes = durationMinutes % 60

        when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "0m"
        }
    } catch (e: Exception) {
        null
    }
}

class SleepLogViewModel(
    private val repository: FirebaseRepository,
    private val userId: String
) : ViewModel() {

    private val _startTime = MutableStateFlow("")
    val startTime: StateFlow<String> = _startTime.asStateFlow()

    private val _endTime = MutableStateFlow("")
    val endTime: StateFlow<String> = _endTime.asStateFlow()

    private val _quality = MutableStateFlow(0)
    val quality: StateFlow<Int> = _quality.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    fun updateStartTime(time: String) {
        _startTime.value = time
        _saveMessage.value = null
    }

    fun updateEndTime(time: String) {
        _endTime.value = time
        _saveMessage.value = null
    }

    fun updateQuality(qualityLevel: Int) {
        _quality.value = qualityLevel
        _saveMessage.value = null
    }

    suspend fun saveSleep(userId: String) {
        if (_startTime.value.isBlank() || _endTime.value.isBlank() || _quality.value == 0) {
            _saveMessage.value = "Please fill in all fields"
            return
        }

        _isSaving.value = true
        _saveMessage.value = null

        try {
            // Parse times - get hour and minute from input
            val startParts = _startTime.value.split(":")
            val endParts = _endTime.value.split(":")
            
            if (startParts.size != 2 || endParts.size != 2) {
                _saveMessage.value = "Invalid time format. Use HH:MM"
                return
            }
            
            val startHour = startParts[0].toIntOrNull() ?: run {
                _saveMessage.value = "Invalid start time"
                return
            }
            val startMinute = startParts[1].toIntOrNull() ?: run {
                _saveMessage.value = "Invalid start time"
                return
            }
            val endHour = endParts[0].toIntOrNull() ?: run {
                _saveMessage.value = "Invalid end time"
                return
            }
            val endMinute = endParts[1].toIntOrNull() ?: run {
                _saveMessage.value = "Invalid end time"
                return
            }
            
            // Determine which date the sleep belongs to
            // Sleep belongs to the date when you went to bed
            // If bedtime is late (after 6 PM), it's likely from yesterday (last night)
            // If bedtime is early morning (before 6 AM), it's also from yesterday (last night)
            val today = java.time.LocalDate.now()
            val sleepDate = when {
                startHour >= 18 -> {
                    // Bedtime 6 PM or later - sleep is from last night (yesterday)
                    today.minusDays(1)
                }
                startHour < 6 -> {
                    // Bedtime before 6 AM - sleep is from last night (yesterday)
                    today.minusDays(1)
                }
                else -> {
                    // Bedtime between 6 AM and 6 PM - unusual but could be a nap, use today
                    today
                }
            }
            
            // Create Calendar instances for the correct date
            val startCalendar = Calendar.getInstance().apply {
                set(sleepDate.year, sleepDate.monthValue - 1, sleepDate.dayOfMonth, startHour, startMinute, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            // End time might be next day if it's overnight sleep
            val endDate = if (endHour < startHour || (endHour == startHour && endMinute < startMinute)) {
                // End time is earlier than start time, so it's the next day
                sleepDate.plusDays(1)
            } else {
                sleepDate
            }
            
            val endCalendar = Calendar.getInstance().apply {
                set(endDate.year, endDate.monthValue - 1, endDate.dayOfMonth, endHour, endMinute, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            val startTimeMillis = startCalendar.timeInMillis
            val endTimeMillis = endCalendar.timeInMillis
            
            android.util.Log.d("SleepLog", "Saving sleep: bedtime=$startHour:$startMinute (${sleepDate}), wake=$endHour:$endMinute (${endDate}), date=$sleepDate")
            android.util.Log.d("SleepLog", "  Start timestamp: ${java.util.Date(startTimeMillis)}")
            android.util.Log.d("SleepLog", "  End timestamp: ${java.util.Date(endTimeMillis)}")
            android.util.Log.d("SleepLog", "  Duration: ${(endTimeMillis - startTimeMillis) / (1000.0 * 60.0 * 60.0)} hours")

            val sleepLog = HealthLog.SleepLog(
                startTime = startTimeMillis,
                endTime = endTimeMillis,
                quality = _quality.value
            )

            val date = sleepDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            val result = repository.saveHealthLog(userId, date, sleepLog)

            if (result.isSuccess) {
                DebugLogger.logDebug("SleepLog", "Sleep saved successfully")
                _saveMessage.value = "Sleep logged successfully!"

                // Clear form
                _startTime.value = ""
                _endTime.value = ""
                _quality.value = 0
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                DebugLogger.logDebug("SleepLog", "Failed to save sleep: $error")
                _saveMessage.value = "Failed to save sleep: $error"
            }
        } catch (e: Exception) {
            DebugLogger.logDebug("SleepLog", "Failed to save sleep: ${e.message}")
            _saveMessage.value = "Failed to save sleep: ${e.message}"
        } finally {
            _isSaving.value = false
        }
    }

    private fun parseTimeToMillis(timeString: String): Long? {
        return try {
            val parts = timeString.split(":")
            if (parts.size != 2) return null

            val hours = parts[0].toIntOrNull() ?: return null
            val minutes = parts[1].toIntOrNull() ?: return null

            if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) return null

            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, hours)
            calendar.set(Calendar.MINUTE, minutes)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            calendar.timeInMillis
        } catch (e: Exception) {
            null
        }
    }

    class Factory(
        private val repository: FirebaseRepository,
        private val userId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SleepLogViewModel::class.java)) {
                return SleepLogViewModel(repository, userId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
