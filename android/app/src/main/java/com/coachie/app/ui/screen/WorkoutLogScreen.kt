package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import com.coachie.app.ui.components.CoachieCard as Card
import com.coachie.app.ui.components.CoachieCardDefaults as CardDefaults
import com.coachie.app.ui.components.ShareDialog
import com.coachie.app.data.model.PublicUserProfile
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.coachie.app.data.health.GoogleFitService

data class WorkoutType(
    val name: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLogScreen(
    userId: String? = null,
    onBack: () -> Unit = {},
    viewModel: WorkoutLogViewModel = viewModel(
        factory = WorkoutLogViewModel.Factory(FirebaseRepository.getInstance(), userId ?: com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: "anonymous")
    )
) {
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val currentUserId = userId ?: com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: return
    val workoutType by viewModel.workoutType.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val caloriesBurned by viewModel.caloriesBurned.collectAsState()
    val intensity by viewModel.intensity.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveMessage by viewModel.saveMessage.collectAsState()

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val gender = LocalGender.current
    val isMale = gender.lowercase() == "male"
    val textColor = if (isMale) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val repository = FirebaseRepository.getInstance()
    val context = LocalContext.current

    // Share dialog state
    var showShareDialog by rememberSaveable { mutableStateOf(false) }
    var friends by remember { mutableStateOf<List<PublicUserProfile>>(emptyList()) }
    var selectedFriends by remember { mutableStateOf<Set<String>>(emptySet()) }
    var workoutToShare by remember { mutableStateOf<HealthLog.WorkoutLog?>(null) }

    // Load friends when share dialog opens
    LaunchedEffect(showShareDialog) {
        if (showShareDialog && currentUserId != "anonymous") {
            repository.getFriends(currentUserId).fold(
                onSuccess = { friendsList ->
                    friends = friendsList
                },
                onFailure = {}
            )
        }
    }

    val workoutTypes = listOf(
        WorkoutType("Running", Icons.Filled.DirectionsRun),
        WorkoutType("Walking", Icons.Filled.DirectionsWalk),
        WorkoutType("Cycling", Icons.Filled.DirectionsBike),
        WorkoutType("Swimming", Icons.Filled.Pool),
        WorkoutType("Weight Training", Icons.Filled.FitnessCenter),
        WorkoutType("Yoga", Icons.Filled.SelfImprovement),
        WorkoutType("Pilates", Icons.Filled.Accessibility),
        WorkoutType("HIIT", Icons.Filled.FlashOn),
        WorkoutType("Dancing", Icons.Filled.MusicNote),
        WorkoutType("Sports", Icons.Filled.SportsSoccer),
        WorkoutType("Other", Icons.Filled.MoreHoriz)
    )

    val intensityOptions = listOf("Low", "Medium", "High")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Workout") },
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // Workout Type
            Column {
                Text(
                    text = "CHOOSE WORKOUT TYPE WITH ICONS:",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Selected workout type display
                if (workoutType.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val selectedWorkoutType = workoutTypes.find { it.name == workoutType }
                            selectedWorkoutType?.let { type ->
                                Icon(
                                    type.icon,
                                    contentDescription = type.name,
                                    tint = textColor
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = workoutType,
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = { viewModel.updateWorkoutType("") }
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Clear selection",
                                    tint = textColor
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Workout type buttons grid
                Text(
                    text = "Select a workout type:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Display workout types in a grid
                val rows = workoutTypes.chunked(3)
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { workoutTypeOption ->
                            OutlinedButton(
                                onClick = { viewModel.updateWorkoutType(workoutTypeOption.name) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (workoutType == workoutTypeOption.name)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        Color.Unspecified
                                )
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        workoutTypeOption.icon,
                                        contentDescription = workoutTypeOption.name,
                                        modifier = Modifier.size(24.dp),
                                        tint = textColor
                                    )
                                    Text(
                                        text = workoutTypeOption.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        color = textColor
                                    )
                                }
                            }
                        }
                        // Fill remaining space if row is not full
                        repeat(3 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Duration
            OutlinedTextField(
                value = duration,
                onValueChange = { value ->
                    // Only allow numbers
                    val filtered = value.filter { it.isDigit() }
                    viewModel.updateDuration(filtered)
                },
                label = { Text("Duration (minutes)") },
                placeholder = { Text("e.g., 30") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Calories Burned
            OutlinedTextField(
                value = caloriesBurned,
                onValueChange = { value ->
                    // Only allow numbers
                    val filtered = value.filter { it.isDigit() }
                    viewModel.updateCaloriesBurned(filtered)
                },
                label = { Text("Calories Burned") },
                placeholder = { Text("e.g., 200") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Intensity
            Column {
                Text(
                    text = "CHOOSE INTENSITY LEVEL:",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    intensityOptions.forEach { option ->
                        OutlinedButton(
                            onClick = { viewModel.updateIntensity(option) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (intensity == option)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    Color.Unspecified,
                                contentColor = textColor
                            )
                        ) {
                            Text(option, color = textColor)
                        }
                    }
                }
            }

            // Save Message
            saveMessage?.let { message ->
                Text(
                    text = message,
                    color = if (message.contains("success", ignoreCase = true))
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save and Share Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            val durationInt = duration.toIntOrNull() ?: 0
                            val caloriesInt = caloriesBurned.toIntOrNull() ?: 0
                            viewModel.saveWorkout(currentUserId)
                            // Wait a bit for the save to complete and check result
                            kotlinx.coroutines.delay(500)
                            // Also write to Google Fit if workout was saved successfully
                            val saveMsg = viewModel.saveMessage.value
                            if (saveMsg != null && saveMsg.contains("success", ignoreCase = true)) {
                                try {
                                    val googleFitService = GoogleFitService(context)
                                    if (googleFitService.hasPermissions()) {
                                        val workoutData = com.coachie.app.data.health.GoogleFitService.WorkoutData(
                                            activityType = workoutType.trim(),
                                            durationMin = durationInt,
                                            caloriesBurned = caloriesInt,
                                            startTime = System.currentTimeMillis() - (durationInt * 60 * 1000L)
                                        )
                                        googleFitService.writeWorkout(workoutData)
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("WorkoutLogScreen", "Failed to write workout to Google Fit", e)
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = workoutType.isNotBlank() &&
                             duration.isNotBlank() &&
                             caloriesBurned.isNotBlank() &&
                             intensity.isNotBlank() &&
                             !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Saving...")
                    } else {
                        Text("Save Workout")
                    }
                }

                OutlinedButton(
                    onClick = {
                        if (workoutType.isNotBlank() && duration.isNotBlank() && 
                            caloriesBurned.isNotBlank() && intensity.isNotBlank()) {
                            val workoutLog = HealthLog.WorkoutLog(
                                workoutType = workoutType.trim(),
                                durationMin = duration.toIntOrNull() ?: 0,
                                caloriesBurned = caloriesBurned.toIntOrNull() ?: 0,
                                intensity = intensity
                            )
                            workoutToShare = workoutLog
                            showShareDialog = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = workoutType.isNotBlank() &&
                             duration.isNotBlank() &&
                             caloriesBurned.isNotBlank() &&
                             intensity.isNotBlank()
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share")
                }
            }

        // Share Dialog
        if (showShareDialog && workoutToShare != null) {
            ShareDialog(
                title = "Share Workout: ${workoutToShare!!.workoutType}",
                friends = friends,
                selectedFriends = selectedFriends,
                onFriendToggle = { friendId ->
                    selectedFriends = if (selectedFriends.contains(friendId)) {
                        selectedFriends - friendId
                    } else {
                        selectedFriends + friendId
                    }
                },
                onShare = {
                    if (selectedFriends.isNotEmpty() && workoutToShare != null) {
                        viewModel.shareWorkoutWithFriends(workoutToShare!!, selectedFriends.toList())
                        showShareDialog = false
                        selectedFriends = emptySet()
                        workoutToShare = null
                    }
                },
                onDismiss = {
                    showShareDialog = false
                    selectedFriends = emptySet()
                    workoutToShare = null
                }
            )
        }
            }
        }
    }
}

class WorkoutLogViewModel(
    private val repository: FirebaseRepository,
    private val userId: String
) : ViewModel() {

    private val _workoutType = MutableStateFlow("")
    val workoutType: StateFlow<String> = _workoutType.asStateFlow()

    private val _duration = MutableStateFlow("")
    val duration: StateFlow<String> = _duration.asStateFlow()

    private val _caloriesBurned = MutableStateFlow("")
    val caloriesBurned: StateFlow<String> = _caloriesBurned.asStateFlow()

    private val _intensity = MutableStateFlow("")
    val intensity: StateFlow<String> = _intensity.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    fun updateWorkoutType(type: String) {
        _workoutType.value = type
        _saveMessage.value = null
    }

    fun updateDuration(durationStr: String) {
        _duration.value = durationStr
        _saveMessage.value = null
    }

    fun updateCaloriesBurned(caloriesStr: String) {
        _caloriesBurned.value = caloriesStr
        _saveMessage.value = null
    }

    fun updateIntensity(intensityLevel: String) {
        _intensity.value = intensityLevel
        _saveMessage.value = null
    }

    suspend fun saveWorkout(userId: String) {
        if (_workoutType.value.isBlank() ||
            _duration.value.isBlank() ||
            _caloriesBurned.value.isBlank() ||
            _intensity.value.isBlank()) {
            _saveMessage.value = "Please fill in all fields"
            return
        }

        _isSaving.value = true
        _saveMessage.value = null

        try {
            val durationInt = _duration.value.toIntOrNull() ?: 0
            val caloriesInt = _caloriesBurned.value.toIntOrNull() ?: 0

            val workoutLog = HealthLog.WorkoutLog(
                workoutType = _workoutType.value.trim(),
                durationMin = durationInt,
                caloriesBurned = caloriesInt,
                intensity = _intensity.value
            )

            val date = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            val result = repository.saveHealthLog(userId, date, workoutLog)

            if (result.isSuccess) {
                DebugLogger.logDebug("WorkoutLog", "Workout saved: ${workoutLog.workoutType}")
                _saveMessage.value = "Workout saved successfully!"

                // Clear form after successful save
                _workoutType.value = ""
                _duration.value = ""
                _caloriesBurned.value = ""
                _intensity.value = ""
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                DebugLogger.logDebug("WorkoutLog", "Failed to save workout: $error")
                _saveMessage.value = "Failed to save workout: $error"
            }
        } catch (e: Exception) {
            DebugLogger.logDebug("WorkoutLog", "Failed to save workout: ${e.message}")
            _saveMessage.value = "Failed to save workout: ${e.message}"
        } finally {
            _isSaving.value = false
        }
    }

    /**
     * Share a workout with friends
     */
    fun shareWorkoutWithFriends(workoutLog: HealthLog.WorkoutLog, friendIds: List<String>) {
        viewModelScope.launch {
            try {
                // Save to shared workouts collection
                val workoutData = hashMapOf<String, Any>(
                    "id" to workoutLog.entryId,
                    "userId" to userId,
                    "workoutType" to workoutLog.workoutType,
                    "durationMin" to workoutLog.durationMin,
                    "caloriesBurned" to workoutLog.caloriesBurned,
                    "intensity" to workoutLog.intensity,
                    "isShared" to true,
                    "sharedWith" to friendIds,
                    "timestamp" to workoutLog.timestamp,
                    "createdAt" to System.currentTimeMillis()
                )

                com.google.firebase.ktx.Firebase.firestore
                    .collection("sharedWorkouts")
                    .document(workoutLog.entryId)
                    .set(workoutData)
                    .await()

                android.util.Log.d("WorkoutLogViewModel", "Workout shared successfully with ${friendIds.size} friends")
            } catch (e: Exception) {
                android.util.Log.e("WorkoutLogViewModel", "Error sharing workout", e)
            }
        }
    }

    class Factory(
        private val repository: FirebaseRepository,
        private val userId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(WorkoutLogViewModel::class.java)) {
                return WorkoutLogViewModel(repository, userId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
