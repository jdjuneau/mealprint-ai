package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import com.coachie.app.ui.components.CoachieCard as Card
import com.coachie.app.ui.components.CoachieCardDefaults as CardDefaults
import androidx.compose.material3.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.ui.components.GradientBackground
import com.coachie.app.ui.components.GradientStyle
import com.coachie.app.data.model.UserProfile
import com.coachie.app.data.model.DietaryPreference
import com.coachie.app.utils.DebugLogger
import com.coachie.app.viewmodel.AuthViewModel
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
private fun GoalOptionCard(
    emoji: String,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )

            if (isSelected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun GenderOptionCard(
    emoji: String,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.displayMedium
            )

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            if (isSelected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun FitnessLevelCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isSelected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetGoalsScreen(
    navController: androidx.navigation.NavHostController
) {
    val authViewModel: AuthViewModel = viewModel()
    // User data
    var userName by remember { mutableStateOf("") }
    var isLoadingUser by remember { mutableStateOf(true) }
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }

    // Goal selection state
    var selectedGoal by remember { mutableStateOf<String?>(null) }
    var fitnessLevel by remember { mutableStateOf<String?>(null) }
    var activityLevel by remember { mutableStateOf<String?>(null) }
    var weeklyWorkouts by remember { mutableStateOf(3) }
    var dailySteps by remember { mutableStateOf(8000) }
    var isSaving by remember { mutableStateOf(false) }

    // Measurement state
    var useImperial by remember { mutableStateOf(true) } // Default to imperial
    var gender by remember { mutableStateOf<String?>(null) }
    var age by remember { mutableStateOf("") }
    var dietaryPreference by remember { mutableStateOf<DietaryPreference?>(null) }
    var mealsPerDay by remember { mutableStateOf(3) }
    var snacksPerDay by remember { mutableStateOf(2) }
    var currentWeight by remember { mutableStateOf("") }
    var goalWeight by remember { mutableStateOf("") }
    var heightFeet by remember { mutableStateOf("") }
    var heightInches by remember { mutableStateOf("") }
    var heightCm by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val firebaseRepository = remember { FirebaseRepository.getInstance() }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Function to save goals and continue
    fun saveGoalsAndContinue() {
    val currentUser = FirebaseAuth.getInstance().currentUser
    if (currentUser == null) {
        DebugLogger.logDebug("SetGoalsScreen", "No authenticated user, skipping goal save")
        navController.navigate("home") {
            popUpTo("welcome") { inclusive = true }
        }
        return
    }
    
    // Display account info for user visibility
    val userEmail = currentUser.email ?: "No email"
    val userId = currentUser.uid

        scope.launch {
            isSaving = true
            DebugLogger.logDebug("SetGoalsScreen", "Saving goals for user: ${currentUser.uid}")

            try {
                // Convert measurements to metric for storage
                val heightCmValue = if (useImperial) {
                    // Convert feet and inches to cm
                    val feet = heightFeet.toFloatOrNull() ?: 0f
                    val inches = heightInches.toFloatOrNull() ?: 0f
                    ((feet * 30.48f) + (inches * 2.54f)).toDouble()
                } else {
                    heightCm.toDoubleOrNull() ?: 0.0
                }

                val weightKgValue = if (useImperial) {
                    // Convert lbs to kg
                    ((currentWeight.toFloatOrNull() ?: 0f) * 0.453592f).toDouble()
                } else {
                    currentWeight.toDoubleOrNull() ?: 0.0
                }

                val goalWeightKgValue = if (useImperial) {
                    // Convert lbs to kg
                    ((goalWeight.toFloatOrNull() ?: 0f) * 0.453592f).toDouble()
                } else {
                    goalWeight.toDoubleOrNull() ?: 0.0
                }

                val resolvedAge = age.toIntOrNull()?.takeIf { it in 13..100 } 
                    ?: userProfile?.age?.takeIf { it in 13..100 } 
                    ?: 30
                val firstTimeFlag = userProfile?.isFirstTimeUser ?: true
                val resolvedDietaryPreference = dietaryPreference 
                    ?: userProfile?.dietaryPreferenceEnum 
                    ?: DietaryPreference.BALANCED
                val resolvedActivityLevel = activityLevel?.lowercase() 
                    ?: userProfile?.activityLevel?.takeIf { it.isNotBlank() }
                    ?: "lightly active"

                // Create updated user profile with measurements
                // IMPORTANT: Use name from profile (set during signup), don't override with userName
                val currentProfile = userProfile
                val updatedProfile = currentProfile?.copy(
                    name = currentProfile.name.takeIf { it.isNotBlank() } ?: userName.ifBlank { currentUser.displayName ?: "User" }, // Use profile name from signup, fallback to userName or displayName
                    currentWeight = weightKgValue,
                    goalWeight = goalWeightKgValue,
                    heightCm = heightCmValue,
                    age = resolvedAge,
                    activityLevel = resolvedActivityLevel,
                    isFirstTimeUser = firstTimeFlag,
                    gender = gender ?: "", // Save gender to profile
                    dietaryPreference = resolvedDietaryPreference.id,
                    mealsPerDay = mealsPerDay,
                    snacksPerDay = snacksPerDay
                    // Store measurement preference
                    // Note: We could add a field for useImperial preference in UserProfile
                ) ?: UserProfile.create(
                    uid = currentUser.uid,
                    name = userName.ifBlank { currentUser.displayName ?: "User" },
                    currentWeight = weightKgValue,
                    goalWeight = goalWeightKgValue,
                    heightCm = heightCmValue,
                    activityLevel = resolvedActivityLevel,
                    startDate = System.currentTimeMillis(),
                    nudgesEnabled = true,
                    fcmToken = null,
                    age = resolvedAge,
                    isFirstTimeUser = firstTimeFlag,
                    gender = gender ?: "", // Save gender to profile
                    dietaryPreference = resolvedDietaryPreference,
                    mealsPerDay = mealsPerDay,
                    snacksPerDay = snacksPerDay
                ).getOrNull()

                // Save goals data to Firestore (this is what AuthViewModel.checkUserGoals() looks for)
                val goalsData = mapOf(
                    "selectedGoal" to selectedGoal,
                    "fitnessLevel" to fitnessLevel,
                    "weeklyWorkouts" to weeklyWorkouts,
                    "dailySteps" to dailySteps,
                    "gender" to gender,
                    "useImperial" to useImperial,
                    "goalsSet" to true,
                    "goalsSetDate" to System.currentTimeMillis()
                )

                val saveResult = if (updatedProfile != null) {
                    firebaseRepository.saveUserProfile(updatedProfile)
                } else {
                    // Fallback: save directly to Firestore if profile creation failed
                    try {
                        FirebaseFirestore.getInstance().collection("users").document(currentUser.uid).set(
                            mapOf(
                                "name" to userName,
                                "email" to currentUser.email,
                                "currentWeight" to weightKgValue,
                                "goalWeight" to goalWeightKgValue,
                                "heightCm" to heightCmValue,
                                "activityLevel" to "lightly active",
                                "startDate" to System.currentTimeMillis(),
                                "nudgesEnabled" to true,
                                "authType" to "email",
                                "useImperial" to useImperial, // Store measurement preference
                                // Add goals data
                                "selectedGoal" to selectedGoal,
                                "fitnessLevel" to fitnessLevel,
                                "weeklyWorkouts" to weeklyWorkouts,
                                "dailySteps" to dailySteps,
                                "gender" to gender,
                                "goalsSet" to true,
                                "goalsSetDate" to System.currentTimeMillis()
                            )
                        ).await()
                        Result.success(Unit)
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }

                // Save goals separately to ensure AuthViewModel can find them
                if (saveResult.isSuccess) {
                    try {
                        FirebaseFirestore.getInstance().collection("users").document(currentUser.uid)
                            .update(goalsData).await()
                        DebugLogger.logDebug("SetGoalsScreen", "Goals data saved successfully")
                    } catch (e: Exception) {
                        DebugLogger.logDebug("SetGoalsScreen", "Failed to save goals data: ${e.message}")
                        // Try to set instead of update if document doesn't exist
                        try {
                            FirebaseFirestore.getInstance().collection("users").document(currentUser.uid)
                                .set(goalsData, SetOptions.merge()).await()
                            DebugLogger.logDebug("SetGoalsScreen", "Goals data saved with set+merge")
                        } catch (setException: Exception) {
                            DebugLogger.logDebug("SetGoalsScreen", "Failed to save goals with set+merge: ${setException.message}")
                        }
                    }
                }

                if (saveResult.isSuccess) {
                    DebugLogger.logDebug("SetGoalsScreen", "Goals saved successfully")
                    // Refresh goals in AuthViewModel to ensure navigation works properly
                    authViewModel.refreshUserGoals()
                    
                    // NOTE: Gender reload trigger is now set AFTER FTUE completion
                    // This prevents the gender reload from interfering with FTUE navigation
                    
                    // Check if FTUE needs to be shown
                    val ftueCompleted = updatedProfile?.ftueCompleted ?: false
                    if (!ftueCompleted) {
                        navController.navigate("ftue") {
                            popUpTo("welcome") { inclusive = true }
                        }
                    } else {
                        // If FTUE is already completed, trigger gender reload now
                        // (This handles the case where an existing user updates their profile)
                        try {
                            val prefs = context.getSharedPreferences("coachie_prefs", android.content.Context.MODE_PRIVATE)
                            prefs.edit().putString("gender_reload_trigger", System.currentTimeMillis().toString()).apply()
                            android.util.Log.d("SetGoalsScreen", "✅ Gender reload trigger set (FTUE already completed)")
                        } catch (e: Exception) {
                            android.util.Log.e("SetGoalsScreen", "Failed to set gender reload trigger", e)
                        }
                        
                        navController.navigate("home") {
                            popUpTo("welcome") { inclusive = true }
                        }
                    }
                } else {
                    DebugLogger.logDebug("SetGoalsScreen", "Failed to save goals: ${saveResult.exceptionOrNull()?.message}")
                    // Continue anyway since goals are selected locally
                    // Check if FTUE needs to be shown
                    val ftueCompleted = updatedProfile?.ftueCompleted ?: false
                    if (!ftueCompleted) {
                        navController.navigate("ftue") {
                            popUpTo("welcome") { inclusive = true }
                        }
                    } else {
                        navController.navigate("home") {
                            popUpTo("welcome") { inclusive = true }
                        }
                    }
                }
            } catch (e: Exception) {
                DebugLogger.logDebug("SetGoalsScreen", "Error saving goals: ${e.message}")
                isSaving = false
            } finally {
                isSaving = false
            }
        }
    }

    // Load user profile on composition
    LaunchedEffect(Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            DebugLogger.logDebug("SetGoalsScreen", "Loading user profile for: ${currentUser.uid}")
            try {
                val profileResult = firebaseRepository.getUserProfile(currentUser.uid)
                if (profileResult.isSuccess) {
                    val profile = profileResult.getOrNull()
                    userProfile = profile
                    userName = profile?.name ?: ""
                    // Load existing meals and snacks preferences, default to 3 meals and 2 snacks
                    mealsPerDay = profile?.mealsPerDay ?: 3
                    snacksPerDay = profile?.snacksPerDay ?: 2
                    DebugLogger.logDebug("SetGoalsScreen", "User profile loaded: ${profile?.name}, mealsPerDay: $mealsPerDay, snacksPerDay: $snacksPerDay")
                } else {
                    DebugLogger.logDebug("SetGoalsScreen", "Failed to load user profile: ${profileResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                DebugLogger.logDebug("SetGoalsScreen", "Error loading user profile: ${e.message}")
            } finally {
                isLoadingUser = false
            }
        } else {
            DebugLogger.logDebug("SetGoalsScreen", "No authenticated user found")
            isLoadingUser = false
        }
    }

    val goals = listOf(
        "Lose Weight" to "🏃‍♀️",
        "Build Muscle" to "💪",
        "Improve Fitness" to "❤️",
        "Stay Healthy" to "🫀",
        "Increase Energy" to "⚡"
    )

    val fitnessLevels = listOf(
        "Beginner" to "Beginner-friendly workouts",
        "Intermediate" to "Moderate intensity training",
        "Advanced" to "High-intensity workouts"
    )

    val activityLevels = listOf(
        "sedentary" to "Little or no exercise",
        "lightly active" to "Light exercise 1-3 days/week",
        "moderately active" to "Moderate exercise 3-5 days/week",
        "very active" to "Hard exercise 6-7 days/week",
        "extremely active" to "Very hard exercise, physical job"
    )

    // Use neutral female gradient background for FTUE
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Account Info Card - Show which account is logged in
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.colors(
                        containerColor = Color(0xFFE3F2FD).copy(alpha = 0.8f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Logged in as:",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Black.copy(alpha = 0.7f)
                        )
                        Text(
                            text = currentUser.email ?: "No email",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                        Text(
                            text = "User ID: ${currentUser.uid.take(8)}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Black.copy(alpha = 0.6f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Goal setting icon
            Text(
                text = "🎯",
                style = MaterialTheme.typography.displayLarge
            )

            // Title
            Text(
                text = "Set Your Goals",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                ),
                textAlign = TextAlign.Center,
                color = Color(0xFF1C1B1F)
            )

            // Subtitle
            Text(
                text = "Help us personalize your fitness journey",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = Color.Black.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Goal Selection
            Text(
                text = "What's your main goal?",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.align(Alignment.Start),
                color = Color(0xFF1C1B1F)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                goals.forEach { (goal, emoji) ->
                    GoalOptionCard(
                        emoji = emoji,
                        title = goal,
                        isSelected = selectedGoal == goal,
                        onClick = { selectedGoal = goal }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Fitness Level Selection
            Text(
                text = "What's your current fitness level?",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.align(Alignment.Start),
                color = Color(0xFF1C1B1F)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                fitnessLevels.forEach { (level, description) ->
                    FitnessLevelCard(
                        title = level,
                        description = description,
                        isSelected = fitnessLevel == level,
                        onClick = { fitnessLevel = level }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Activity Level Selection
            Text(
                text = "What's your activity level?",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.align(Alignment.Start),
                color = Color(0xFF1C1B1F)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                activityLevels.forEach { (level, description) ->
                    FitnessLevelCard(
                        title = level.replaceFirstChar { it.uppercase() },
                        description = description,
                        isSelected = activityLevel == level,
                        onClick = { activityLevel = level }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Weekly Workouts
            Text(
                text = "How many workouts per week?",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.align(Alignment.Start),
                color = Color(0xFF1C1B1F)
            )

            Text(
                text = "$weeklyWorkouts workouts per week",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF1C1B1F)
            )

            Slider(
                value = weeklyWorkouts.toFloat(),
                onValueChange = { weeklyWorkouts = it.toInt() },
                valueRange = 1f..7f,
                steps = 6,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Daily Steps Goal
            Text(
                text = "What's your daily steps goal?",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.align(Alignment.Start),
                color = Color(0xFF1C1B1F)
            )

            val stepOptions = listOf(5000, 7500, 8000, 10000, 12000, 15000)
            var selectedSteps by remember { mutableStateOf(dailySteps) }

            Text(
                text = "${selectedSteps / 1000}K steps per day",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF1C1B1F)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                stepOptions.forEach { steps ->
                    FilterChip(
                        selected = selectedSteps == steps,
                        onClick = { selectedSteps = steps; dailySteps = steps },
                        label = { Text("${steps / 1000}K") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Meals Per Day Selection
            Text(
                text = "How many meals per day?",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.align(Alignment.Start),
                color = Color(0xFF1C1B1F)
            )

            val mealOptions = listOf(2, 3, 4)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                mealOptions.forEach { meals ->
                    FilterChip(
                        selected = mealsPerDay == meals,
                        onClick = { mealsPerDay = meals },
                        label = { Text("$meals") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Snacks Per Day Selection
            Text(
                text = "How many snacks per day?",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.align(Alignment.Start),
                color = Color(0xFF1C1B1F)
            )

            val snackOptions = listOf(0, 1, 2)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                snackOptions.forEach { snacks ->
                    FilterChip(
                        selected = snacksPerDay == snacks,
                        onClick = { snacksPerDay = snacks },
                        label = { Text("$snacks") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Height Input
            Text(
                text = "What's your height?",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.align(Alignment.Start),
                color = Color(0xFF1C1B1F)
            )

            if (useImperial) {
                // Imperial height input (feet and inches)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = heightFeet,
                        onValueChange = { heightFeet = it },
                        label = { Text("Feet") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = heightInches,
                        onValueChange = { heightInches = it },
                        label = { Text("Inches") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        singleLine = true
                    )
                }
            } else {
                // Metric height input (cm)
                OutlinedTextField(
                    value = heightCm,
                    onValueChange = { heightCm = it },
                    label = { Text("Height (cm)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Gender Selection
            Text(
                text = "What's your gender?",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.align(Alignment.Start),
                color = Color(0xFF1C1B1F)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GenderOptionCard(
                    emoji = "👨",
                    title = "Male",
                    isSelected = gender == "male",
                    onClick = { gender = "male" },
                    modifier = Modifier.weight(1f)
                )
                GenderOptionCard(
                    emoji = "👩",
                    title = "Female",
                    isSelected = gender == "female",
                    onClick = { gender = "female" },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Age Input
            Text(
                text = "What's your age?",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.align(Alignment.Start),
                color = Color(0xFF1C1B1F)
            )

            OutlinedTextField(
                value = age,
                onValueChange = { age = it },
                label = { Text("Age") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Dietary Preference Selection
            Text(
                text = "What's your dietary preference?",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.align(Alignment.Start),
                color = Color(0xFF1C1B1F)
            )

            // Show ALL dietary preferences in a grid
            val allPreferences = DietaryPreference.values().toList()

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                allPreferences.chunked(2).forEach { rowPreferences ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowPreferences.forEach { preference ->
                            FilterChip(
                                selected = dietaryPreference == preference,
                                onClick = { dietaryPreference = preference },
                                label = { 
                                    Text(
                                        preference.title,
                                        color = if (dietaryPreference == preference) Color.Black else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodySmall
                                    ) 
                                },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Weight Inputs
            Text(
                text = "Weight Goals",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.align(Alignment.Start),
                color = Color(0xFF1C1B1F)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = currentWeight,
                    onValueChange = { currentWeight = it },
                    label = { Text(if (useImperial) "Current Weight (lbs)" else "Current Weight (kg)") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    singleLine = true
                )
                OutlinedTextField(
                    value = goalWeight,
                    onValueChange = { goalWeight = it },
                    label = { Text(if (useImperial) "Goal Weight (lbs)" else "Goal Weight (kg)") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Units Toggle
            Text(
                text = "Measurement Units",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.align(Alignment.Start),
                color = Color(0xFF1C1B1F)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FilterChip(
                    selected = useImperial,
                    onClick = { useImperial = true },
                    label = { Text("Imperial (lbs/ft)") }
                )
                FilterChip(
                    selected = !useImperial,
                    onClick = { useImperial = false },
                    label = { Text("Metric (kg/cm)") }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Continue button
            Button(
                onClick = { saveGoalsAndContinue() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = userName.isNotBlank() &&
                         selectedGoal != null && 
                         fitnessLevel != null && 
                         activityLevel != null &&
                         gender != null &&
                         age.isNotBlank() && age.toIntOrNull()?.let { it in 13..100 } == true &&
                         dietaryPreference != null &&
                         currentWeight.isNotBlank() && goalWeight.isNotBlank() &&
                         (if (useImperial) heightFeet.isNotBlank() && heightInches.isNotBlank() else heightCm.isNotBlank()) &&
                         !isSaving,
                shape = MaterialTheme.shapes.large
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = "Continue to Coachie",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}