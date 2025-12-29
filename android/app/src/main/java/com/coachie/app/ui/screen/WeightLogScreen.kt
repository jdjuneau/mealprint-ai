package com.coachie.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import com.coachie.app.ui.components.CoachieCard as Card
import com.coachie.app.ui.components.CoachieCardDefaults as CardDefaults
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.ui.theme.LocalGender
import com.coachie.app.data.model.HealthLog
import com.coachie.app.utils.DebugLogger
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WeightLogViewModel(
    private val repository: FirebaseRepository,
    private val userId: String
) : ViewModel() {

    private val _weight = MutableStateFlow("")
    val weight: StateFlow<String> = _weight.asStateFlow()

    private val _useImperial = MutableStateFlow(true) // Default to imperial
    val useImperial: StateFlow<Boolean> = _useImperial.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    init {
        if (userId.isNotBlank() && userId != "anonymous") {
            loadUserPreferences(userId)
        }
    }

    fun updateWeight(newWeight: String) {
        _weight.value = newWeight
    }

    private fun loadUserPreferences(userId: String) {
        if (userId.isBlank() || userId == "anonymous") return
        viewModelScope.launch {
            try {
                val goalsResult = FirebaseRepository.getInstance().getUserGoals(userId)
                val goals = goalsResult.getOrNull()
                val useImperial = goals?.get("useImperial") as? Boolean ?: true
                _useImperial.value = useImperial
            } catch (e: Exception) {
                DebugLogger.logDebug("WeightLogViewModel", "Failed to load user preferences: ${e.message}")
                _useImperial.value = true // Default to imperial
            }
        }
    }

    suspend fun saveWeight(userId: String) {
        if (_weight.value.isBlank()) return

        _isSaving.value = true
        _saveSuccess.value = false
        try {
            val weightValue = _weight.value.toDoubleOrNull() ?: return

            val weightLog = HealthLog.WeightLog(
                weight = weightValue,
                unit = if (_useImperial.value) "lbs" else "kg",
                timestamp = System.currentTimeMillis()
            )

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            repository.saveHealthLog(userId, today, weightLog)
            DebugLogger.logDebug("WeightLogViewModel", "Weight saved: $weightValue ${_useImperial.value}")

            // Show success confirmation
            _saveSuccess.value = true
            _weight.value = "" // Clear after save

            // Auto-hide success message after 2 seconds
            kotlinx.coroutines.delay(2000)
            _saveSuccess.value = false

        } catch (e: Exception) {
            DebugLogger.logDebug("WeightLogViewModel", "Failed to save weight: ${e.message}")
        } finally {
            _isSaving.value = false
        }
    }

    class Factory(
        private val repository: FirebaseRepository,
        private val userId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(WeightLogViewModel::class.java)) {
                return WeightLogViewModel(repository, userId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightLogScreen(
    userId: String? = null,
    onBack: () -> Unit = {}
) {
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val currentUserId = userId ?: com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: return
    
    val viewModel: WeightLogViewModel = viewModel(
        factory = WeightLogViewModel.Factory(
            FirebaseRepository.getInstance(),
            currentUserId
        )
    )
    val weight by viewModel.weight.collectAsState()
    val useImperial by viewModel.useImperial.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()

    val scope = rememberCoroutineScope()
    val gender = LocalGender.current
    val isMale = gender.lowercase() == "male"
    // Use white text for male UI on gradient backgrounds
    val textColor = if (isMale) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weight Tracking") },
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
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
            // Header
            Text(
                text = "🏋️‍♀️",
                style = MaterialTheme.typography.displayLarge
            )

            Text(
                text = "Log Your Weight",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Track your progress and stay motivated!",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Weight Input
            OutlinedTextField(
                value = weight,
                onValueChange = { newValue -> viewModel.updateWeight(newValue) },
                label = {
                    Text(if (useImperial) "Weight (lbs)" else "Weight (kg)")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    Text(
                        text = if (useImperial) "lbs" else "kg",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            )

            // Quick weight buttons - expanded range from 90 to 350+ lbs
            val quickWeights = if (useImperial) {
                // 20 lb increments from 90 to 330, covering 90-350+ range
                listOf(90, 110, 130, 150, 170, 190, 210, 230, 250, 270, 290, 310, 330, 350)
            } else {
                // Metric: 5 kg increments from 40 to 160 kg
                listOf(40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100, 110, 120, 130, 140, 150, 160)
            }

            Text(
                text = "Quick Select",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black,
                modifier = Modifier.align(Alignment.Start)
            )

            // Arrange quick weights in rows (3 columns for imperial, 3-4 for metric)
            val columnsPerRow = if (useImperial) 3 else 3
            val chunkedWeights = quickWeights.chunked(columnsPerRow)
            chunkedWeights.forEach { rowWeights ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowWeights.forEach { weightValue ->
                        OutlinedButton(
                            onClick = { viewModel.updateWeight(weightValue.toString()) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.Black
                            )
                        ) {
                            Text(
                                "$weightValue",
                                color = Color.Black,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    // Fill remaining columns if row is incomplete
                    repeat(columnsPerRow - rowWeights.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Save Button
            Button(
                onClick = {
                    scope.launch {
                        viewModel.saveWeight(currentUserId)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = weight.isNotBlank() && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Save Weight", style = MaterialTheme.typography.titleMedium)
                }
            }

            // Success confirmation
            AnimatedVisibility(visible = saveSuccess) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Success",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Weight logged successfully!",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
