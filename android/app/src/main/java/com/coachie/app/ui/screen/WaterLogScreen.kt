package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.ui.theme.LocalGender
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
import android.util.Log
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.DailyLog
import com.coachie.app.utils.DebugLogger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
private data class WaterQuickOption(val amount: Int, val label: String, val icon: String)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterLogScreen(
    userId: String? = null,
    onBack: () -> Unit = {},
    viewModel: WaterLogViewModel = viewModel(
        factory = WaterLogViewModel.Factory(FirebaseRepository.getInstance(), userId ?: com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: "anonymous")
    )
) {
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val currentUserId = userId ?: com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: return
    val waterAmount by viewModel.waterAmount.collectAsState()
    val useImperial by viewModel.useImperial.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val dailyGoal by viewModel.dailyGoalMl.collectAsState()

    val scope = rememberCoroutineScope()
    val gender = LocalGender.current
    val isMale = gender.lowercase() == "male"
    val textColor = if (isMale) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

    // CRITICAL FIX: Reload water amount when screen becomes visible to ensure it's up-to-date
    LaunchedEffect(currentUserId) {
        viewModel.loadCurrentWaterAmount(currentUserId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Water Intake") },
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
                text = "💧",
                style = MaterialTheme.typography.displayLarge
            )

            Text(
                text = "Log Your Water Intake",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Stay hydrated and track your daily water goal!",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Current water amount display
            CoachieCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CoachieCardDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    contentColor = if (isMale) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                ),
                border = CoachieCardDefaults.border(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val amountToAdd = viewModel.amountToAdd.collectAsState().value
                    val totalWater = waterAmount + amountToAdd
                    
                    val consumedValue = if (useImperial) {
                        String.format(Locale.getDefault(), "%.0f oz", totalWater / 29.5735)
                    } else {
                        "$totalWater ml"
                    }
                    val goalValue = if (useImperial) {
                        String.format(Locale.getDefault(), "%.0f oz", dailyGoal / 29.5735)
                    } else {
                        "$dailyGoal ml"
                    }
                    val waterProgress = if (dailyGoal > 0) {
                        (totalWater.toFloat() / dailyGoal.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    val cardTextColor = if (isMale) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer

                    Text(
                        text = "$consumedValue / $goalValue",
                        style = MaterialTheme.typography.headlineMedium,
                        color = cardTextColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Today's Water Intake",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cardTextColor
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = waterProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    )
                }
            }

            // Manual input
            OutlinedTextField(
                value = viewModel.manualAmount.collectAsState().value,
                onValueChange = { viewModel.updateManualAmount(it) },
                label = {
                    Text(
                        if (useImperial) "Add Water (fl oz)" else "Add Water (ml)",
                        color = if (isMale) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = if (isMale) Color.White else MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = if (isMale) Color.White else MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = if (isMale) Color.White else MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = if (isMale) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
                    focusedLabelColor = if (isMale) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = if (isMale) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            // Quick add buttons
            Text(
                text = "Quick Add",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Start)
            )

            val quickOptions = if (useImperial) {
                listOf(
                    WaterQuickOption(8, "Glass", "🥛"),
                    WaterQuickOption(12, "Bottle", "🚰"),
                    WaterQuickOption(16, "Tall Glass", "🍹"),
                    WaterQuickOption(20, "Sport Bottle", "🏃"),
                    WaterQuickOption(24, "Thermos", "🏋️"),
                    WaterQuickOption(32, "Large Bottle", "🚰")
                )
            } else {
                listOf(
                    WaterQuickOption(200, "Glass", "🥛"),
                    WaterQuickOption(300, "Cup", "🍶"),
                    WaterQuickOption(400, "Bottle", "🚰"),
                    WaterQuickOption(500, "Bottle", "💧"),
                    WaterQuickOption(600, "Sport", "🏃"),
                    WaterQuickOption(750, "Thermos", "🏋️")
                )
            }

            // Arrange quick amounts in rows
            quickOptions.chunked(3).forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowOptions.forEach { option ->
                        OutlinedButton(
                            onClick = {
                                val mlAmount = if (useImperial) (option.amount * 29.5735).roundToInt() else option.amount
                                viewModel.addWater(mlAmount)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isMale) Color.White else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(option.icon, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = if (useImperial) "${option.amount} oz" else "${option.amount} ml",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isMale) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isMale) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                                )
                    }
                }
            }
        }
    }

            Spacer(modifier = Modifier.height(32.dp))

            // Save Button
            Button(
                onClick = {
                    scope.launch {
                        viewModel.saveWater(currentUserId)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = viewModel.amountToAdd.collectAsState().value > 0 && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Save Water Intake", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

class WaterLogViewModel(
    private val repository: FirebaseRepository,
    private val userId: String
) : ViewModel() {

    private val _waterAmount = MutableStateFlow(0) // Total water consumed today
    val waterAmount: StateFlow<Int> = _waterAmount.asStateFlow()

    private val _amountToAdd = MutableStateFlow(0) // Amount to add in this session
    val amountToAdd: StateFlow<Int> = _amountToAdd.asStateFlow()

    private val _manualAmount = MutableStateFlow("")
    val manualAmount: StateFlow<String> = _manualAmount.asStateFlow()

    private val _useImperial = MutableStateFlow(true) // Default to imperial
    val useImperial: StateFlow<Boolean> = _useImperial.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _dailyGoalMl = MutableStateFlow(2000)
    val dailyGoalMl: StateFlow<Int> = _dailyGoalMl.asStateFlow()

    init {
        loadUserPreferences(userId)
        loadCurrentWaterAmount(userId)
    }

    fun updateManualAmount(amount: String) {
        _manualAmount.value = amount
        // When manual input changes, update amount to add
        val inputValue = amount.toDoubleOrNull() ?: 0.0
        _amountToAdd.value = if (_useImperial.value) {
            (inputValue * 29.5735).roundToInt() // Use roundToInt() instead of toInt() for proper rounding
        } else {
            inputValue.toInt()
        }
    }

    fun addWater(amountMl: Int) {
        _amountToAdd.value += amountMl
    }

    private fun loadUserPreferences(userId: String) {
        viewModelScope.launch {
            try {
                val goalsResult = FirebaseRepository.getInstance().getUserGoals(userId)
                val goals = goalsResult.getOrNull()
                val useImperial = goals?.get("useImperial") as? Boolean ?: true
                val goalMl = (goals?.get("dailyWater") as? Number)?.toInt() ?: 2000
                _useImperial.value = useImperial
                _dailyGoalMl.value = goalMl
            } catch (e: Exception) {
                DebugLogger.logDebug("WaterLogViewModel", "Failed to load user preferences: ${e.message}")
                _useImperial.value = true // Default to imperial
                _dailyGoalMl.value = 2000
            }
        }
    }

    fun loadCurrentWaterAmount(userId: String) {
        viewModelScope.launch {
            try {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                
                android.util.Log.d("WaterLogViewModel", "🔍 Loading current water amount for userId=$userId, date=$today")
                
                // Get both DailyLog.water and sum of WaterLog entries to detect discrepancies
                val dailyLog = repository.getDailyLog(userId, today).getOrNull()
                val totalWaterFromDailyLog = dailyLog?.water ?: 0
                
                val healthLogs = repository.getHealthLogs(userId, today).getOrNull() ?: emptyList()
                val waterLogs = healthLogs.filterIsInstance<com.coachie.app.data.model.HealthLog.WaterLog>()
                val sumFromLogs = waterLogs.sumOf { it.ml }
                
                android.util.Log.d("WaterLogViewModel", "  DailyLog.water: $totalWaterFromDailyLog ml")
                android.util.Log.d("WaterLogViewModel", "  Sum from WaterLog entries: $sumFromLogs ml (${waterLogs.size} entries)")
                
                // If there's a discrepancy (likely from double-counting bug), recalculate
                if (totalWaterFromDailyLog > 0 && sumFromLogs > 0 && totalWaterFromDailyLog != sumFromLogs) {
                    android.util.Log.w("WaterLogViewModel", "⚠️ Water amount mismatch detected! DailyLog=$totalWaterFromDailyLog ml, WaterLogs sum=$sumFromLogs ml")
                    android.util.Log.d("WaterLogViewModel", "  Recalculating DailyLog.water from actual WaterLog entries...")
                    
                    val recalcResult = repository.recalculateDailyLogWater(userId, today)
                    if (recalcResult.isSuccess) {
                        val correctedAmount = recalcResult.getOrNull() ?: sumFromLogs
                        android.util.Log.d("WaterLogViewModel", "✅ Recalculated water: $correctedAmount ml")
                        _waterAmount.value = correctedAmount
                    } else {
                        android.util.Log.e("WaterLogViewModel", "❌ Failed to recalculate water, using sum from logs")
                        _waterAmount.value = sumFromLogs
                    }
                } else {
                    // Use DailyLog.water if available, otherwise sum from logs
                    val waterAmount = if (totalWaterFromDailyLog > 0) {
                        totalWaterFromDailyLog
                    } else {
                        sumFromLogs
                    }
                    _waterAmount.value = waterAmount
                }
                
                android.util.Log.d("WaterLogViewModel", "✅ Loaded current water: ${_waterAmount.value} ml")
                DebugLogger.logDebug("WaterLogViewModel", "Loaded current water: ${_waterAmount.value} ml")
            } catch (e: Exception) {
                android.util.Log.e("WaterLogViewModel", "❌ Failed to load current water amount: ${e.message}", e)
                DebugLogger.logDebug("WaterLogViewModel", "Failed to load current water amount: ${e.message}")
            }
        }
    }

    suspend fun saveWater(userId: String) {
        // ROOT CAUSE FIX: Always use authenticated user's ID
        val authenticatedUserId = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw Exception("User must be authenticated to save water")
        
        // Use the accumulated amount to add (from quick adds or manual input)
        val amountToAdd = _amountToAdd.value
        
        if (amountToAdd <= 0) {
            DebugLogger.logDebug("WaterLogViewModel", "No water amount to save (amountToAdd: $amountToAdd)")
            return
        }
        
        android.util.Log.d("WaterLogViewModel", "Saving water - authenticated userId: $authenticatedUserId, passed userId: $userId")

        _isSaving.value = true
        try {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            // FIX: Only save as HealthLog.WaterLog entry
            // FirebaseRepository.saveHealthLog() will automatically update DailyLog.water
            // This prevents double-counting the water amount
            val waterLog = com.coachie.app.data.model.HealthLog.WaterLog(
                entryId = java.util.UUID.randomUUID().toString(),
                ml = amountToAdd,
                timestamp = System.currentTimeMillis()
            )
            val saveResult = repository.saveHealthLog(authenticatedUserId, today, waterLog)
            android.util.Log.d("WaterLogViewModel", "✅ Saved WaterLog entry: ${amountToAdd}ml")

            DebugLogger.logDebug("WaterLogViewModel", "Saving water: ${amountToAdd}ml to userId=$authenticatedUserId, date=$today")
            
            if (saveResult.isSuccess) {
                DebugLogger.logDebug("WaterLogViewModel", "Water saved successfully: ${amountToAdd}ml")
                
                // Clear manual input and reset amount to add
                _manualAmount.value = ""
                _amountToAdd.value = 0
                
                // Add a small delay to ensure Firestore write is complete before reloading
                kotlinx.coroutines.delay(500)
                
                // Reload the total water amount from database to show accurate total
                loadCurrentWaterAmount(authenticatedUserId)
            } else {
                val error = saveResult.exceptionOrNull()
                val errorMessage = error?.message ?: "Unknown error"
                DebugLogger.logDebug("WaterLogViewModel", "Failed to save water to Firestore: $errorMessage")
                Log.e("WaterLogViewModel", "Failed to save water", error)
                // Don't clear the amount to add if save failed, so user can try again
            }
        } catch (e: Exception) {
            DebugLogger.logDebug("WaterLogViewModel", "Exception while saving water: ${e.message}")
            Log.e("WaterLogViewModel", "Exception while saving water", e)
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
            if (modelClass.isAssignableFrom(WaterLogViewModel::class.java)) {
                return WaterLogViewModel(repository, userId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
