package com.coachie.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.HealthLog
import com.google.firebase.auth.FirebaseAuth
import com.coachie.app.viewmodel.AuthState
import com.coachie.app.viewmodel.CoachieViewModel
import com.coachie.app.viewmodel.HomeDashboardViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import com.coachie.app.ui.components.CoachieCard as Card
import com.coachie.app.ui.components.CoachieCardDefaults as CardDefaults
import com.coachie.app.ui.theme.Primary40
import com.coachie.app.ui.theme.rememberCoachieGradient
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext

// Helper functions defined before main function
@Composable
fun MealItemCard(
    meal: HealthLog.MealLog,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .clickable(
                onClick = onEdit,
                indication = rememberRipple(),
                interactionSource = remember { MutableInteractionSource() }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = meal.foodName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    // Show recipe icon if meal has a recipe
                    if (meal.recipeId != null) {
                        Icon(
                            Icons.Filled.RestaurantMenu,
                            contentDescription = "Has recipe",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = "${meal.protein}g protein • ${meal.carbs}g carbs • ${meal.fat}g fat",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(meal.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    // Show servings info if from recipe
                    if (meal.recipeId != null && meal.servingsConsumed != null) {
                        Text(
                            text = "• ${meal.servingsConsumed} serving${if (meal.servingsConsumed != 1.0) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${meal.calories} cal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete meal",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun WorkoutItemCard(
    workout: HealthLog.WorkoutLog,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .clickable(
                onClick = onEdit,
                indication = rememberRipple(),
                interactionSource = remember { MutableInteractionSource() }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workout.workoutType,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${workout.durationMin} min • ${workout.intensity} intensity",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(workout.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${workout.caloriesBurned} cal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete workout",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SleepItemCard(
    sleep: HealthLog.SleepLog,
    useImperial: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .clickable(
                onClick = onEdit,
                indication = rememberRipple(),
                interactionSource = remember { MutableInteractionSource() }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = String.format("%.1f hours", sleep.durationHours),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Quality: ${sleep.quality}/5",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(sleep.startTime)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete sleep log",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun WaterItemCard(
    water: HealthLog.WaterLog,
    useImperial: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .clickable(
                onClick = onEdit,
                indication = rememberRipple(),
                interactionSource = remember { MutableInteractionSource() }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (useImperial) {
                        "${(water.ml * 0.033814).roundToInt()} fl oz"
                    } else {
                        "${water.ml} ml"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(water.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete water log",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun WeightItemCard(
    weight: HealthLog.WeightLog,
    useImperial: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .clickable(
                onClick = onEdit,
                indication = rememberRipple(),
                interactionSource = remember { MutableInteractionSource() }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (useImperial) {
                        val weightInLbs = if (weight.unit == "lbs") weight.weight else weight.weight * 2.205
                        String.format("%.1f lbs", weightInLbs)
                    } else {
                        val weightInKg = if (weight.unit == "lbs") weight.weight / 2.205 else weight.weight
                        String.format("%.1f kg", weightInKg)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(weight.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete weight log",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyLogState(
    onNavigateToMealLog: () -> Unit,
    onNavigateToSupplementLog: () -> Unit,
    onNavigateToWorkoutLog: () -> Unit,
    onNavigateToSleepLog: () -> Unit,
    onNavigateToWaterLog: () -> Unit,
    onNavigateToWeightLog: () -> Unit,
    onNavigateToHealthTracking: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Empty state message
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.95f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "📝",
                    style = MaterialTheme.typography.displayMedium
                )
                Text(
                    text = "No logs yet today",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Start logging your meals, workouts, and activities!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Quick Log Section
        Text(
            "Quick Log",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Health Logging Tools - use HealthToolCard from HealthTrackingDashboardScreen
        val healthTools = listOf(
            com.coachie.app.ui.screen.HealthTool("Meals", Icons.Filled.Restaurant, Primary40, onNavigateToMealLog),
            com.coachie.app.ui.screen.HealthTool("Supplements", Icons.Filled.Medication, Primary40, onNavigateToSupplementLog),
            com.coachie.app.ui.screen.HealthTool("Workouts", Icons.Filled.FitnessCenter, Primary40, onNavigateToWorkoutLog),
            com.coachie.app.ui.screen.HealthTool("Sleep", Icons.Filled.Bedtime, Primary40, onNavigateToSleepLog),
            com.coachie.app.ui.screen.HealthTool("Water", Icons.Filled.LocalDrink, Primary40, onNavigateToWaterLog),
            com.coachie.app.ui.screen.HealthTool("Weight", Icons.Filled.MonitorWeight, Primary40, onNavigateToWeightLog)
        )
        
        healthTools.chunked(2).forEach { rowTools ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                rowTools.forEach { tool ->
                    com.coachie.app.ui.screen.HealthToolCard(
                        tool = tool,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Add spacer if odd number
                if (rowTools.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // View All Health Tracking button
        Button(
            onClick = onNavigateToHealthTracking,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Primary40
            )
        ) {
            Text("View All Health Tracking", color = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodaysLogDetailScreen(
    onBack: () -> Unit,
    onNavigateToMealLog: () -> Unit = {},
    onNavigateToMealDetail: (HealthLog.MealLog) -> Unit = {},
    onNavigateToSupplementLog: () -> Unit = {},
    onNavigateToWorkoutLog: () -> Unit = {},
    onNavigateToSleepLog: () -> Unit = {},
    onNavigateToWaterLog: () -> Unit = {},
    onNavigateToWeightLog: () -> Unit = {},
    onNavigateToHealthTracking: () -> Unit = {},
    viewModel: CoachieViewModel = viewModel()
) {
    val authState by viewModel.authState.collectAsState()
    val currentUser = (authState as? AuthState.Authenticated)?.user
    
    // Get userId from multiple sources, prioritizing current auth state - MUST be reactive
    val userId = remember(authState, currentUser) {
        val uid = currentUser?.uid 
            ?: FirebaseAuth.getInstance().currentUser?.uid
            ?: ""
        android.util.Log.d("TodaysLogDetailScreen", "Resolved userId: '$uid' from authState: ${authState::class.simpleName}, currentUser: ${currentUser?.uid}")
        uid
    }
    
    val finalUserId = userId.takeIf { it.isNotBlank() && it != "anonymous" } ?: ""
    
    android.util.Log.d("TodaysLogDetailScreen", "Final userId for screen: '$finalUserId'")
    
    if (finalUserId.isBlank() || finalUserId == "anonymous") {
        // Wait for auth to be ready - show loading
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // Create dashboard ViewModel with proper factory
    val context = androidx.compose.ui.platform.LocalContext.current
    val dashboardViewModel = viewModel<HomeDashboardViewModel>(
        key = finalUserId,
        factory = HomeDashboardViewModel.Factory(
            repository = FirebaseRepository.getInstance(),
            userId = finalUserId,
            context = context
        )
    )

    // Get today's data from dashboard view model
    val todayHealthLogs by dashboardViewModel.todayHealthLogs.collectAsState()
    val useImperial by dashboardViewModel.useImperial.collectAsState()

    // Get today's date in the format expected by Firebase
    val todayDate = remember {
        val calendar = Calendar.getInstance()
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(calendar.time)
    }

    // Load dashboard data when screen appears
    LaunchedEffect(finalUserId) {
        dashboardViewModel.refresh()
    }

    val meals = todayHealthLogs.filterIsInstance<HealthLog.MealLog>()
    val workouts = todayHealthLogs.filterIsInstance<HealthLog.WorkoutLog>()
    val sleepLogs = todayHealthLogs.filterIsInstance<HealthLog.SleepLog>()
    val waterLogs = todayHealthLogs.filterIsInstance<HealthLog.WaterLog>()
    val weightLogs = todayHealthLogs.filterIsInstance<HealthLog.WeightLog>()

    // Delete confirmation dialogs
    var showDeleteMealDialog by remember { mutableStateOf<HealthLog.MealLog?>(null) }
    var showDeleteWorkoutDialog by remember { mutableStateOf<HealthLog.WorkoutLog?>(null) }
    var showDeleteSleepDialog by remember { mutableStateOf<HealthLog.SleepLog?>(null) }
    var showDeleteWaterDialog by remember { mutableStateOf<HealthLog.WaterLog?>(null) }
    var showDeleteWeightDialog by remember { mutableStateOf<HealthLog.WeightLog?>(null) }

    // Delete function - actual implementation
    // Use finalUserId directly since that's what was used to load the entries
    val scope = rememberCoroutineScope()
    val deleteHealthLog = remember(finalUserId) {
        { log: HealthLog ->
            scope.launch {
                try {
                    // Get the entryId from the health log
                    val entryId = when (log) {
                        is HealthLog.MealLog -> log.entryId
                        is HealthLog.WorkoutLog -> log.entryId
                        is HealthLog.SleepLog -> log.entryId
                        is HealthLog.WaterLog -> log.entryId
                        is HealthLog.WeightLog -> log.entryId
                        is HealthLog.SupplementLog -> log.entryId
                        is HealthLog.MoodLog -> log.entryId
                        is HealthLog.MeditationLog -> log.entryId
                        is HealthLog.SunshineLog -> log.entryId
                        is HealthLog.MenstrualLog -> log.entryId
                        else -> {
                            android.util.Log.w("TodaysLogDetailScreen", "Unsupported log type for deletion: ${log.type}")
                            return@launch
                        }
                    }

                    // Use the same userId that was used to load the entries
                    val userIdToUse = finalUserId
                    
                    android.util.Log.d("TodaysLogDetailScreen", "=== DELETE LOG DEBUG ===")
                    android.util.Log.d("TodaysLogDetailScreen", "User ID: '$userIdToUse'")
                    android.util.Log.d("TodaysLogDetailScreen", "Date: '$todayDate'")
                    android.util.Log.d("TodaysLogDetailScreen", "Entry ID: '$entryId'")
                    android.util.Log.d("TodaysLogDetailScreen", "Log type: ${log.type}")
                    
                    if (userIdToUse.isBlank()) {
                        android.util.Log.e("TodaysLogDetailScreen", "Cannot delete - invalid user ID: '$userIdToUse'")
                        return@launch
                    }

                    // Delete from Firestore
                    val repository = FirebaseRepository.getInstance()
                    val result = repository.deleteHealthLog(userIdToUse, todayDate, entryId)
                    
                    android.util.Log.d("TodaysLogDetailScreen", "Delete result: ${if (result.isSuccess) "SUCCESS" else "FAILED"}")
                    if (result.isFailure) {
                        android.util.Log.e("TodaysLogDetailScreen", "Delete error: ${result.exceptionOrNull()?.message}")
                    }

                    if (result.isSuccess) {
                        android.util.Log.d("TodaysLogDetailScreen", "Successfully deleted log: ${log.type}")
                        // Refresh the data to update the UI
                        dashboardViewModel.refresh()
                    } else {
                        android.util.Log.e("TodaysLogDetailScreen", "Failed to delete log: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TodaysLogDetailScreen", "Error deleting log", e)
                }
            }
        }
    }

    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = gradientBackground)
    ) {
        Scaffold(
            topBar = {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.95f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                "Back",
                                tint = Primary40
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Today's Log",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Primary40
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        val coroutineScope = rememberCoroutineScope()
                        val context = androidx.compose.ui.platform.LocalContext.current
                        IconButton(onClick = {
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    android.util.Log.d("TodaysLogDetailScreen", "🚀 Manual sync triggered")
                                    com.coachie.app.service.HealthSyncService.sync(context)
                                } catch (e: Exception) {
                                    android.util.Log.e("TodaysLogDetailScreen", "Sync failed", e)
                                    e.printStackTrace()
                                }
                            }
                        }) {
                            Icon(
                                Icons.Filled.Sync,
                                "Sync Google Fit",
                                tint = Primary40
                            )
                        }
                    }
                }
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // Meals Section
            if (meals.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Restaurant,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Meals (${meals.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${meals.sumOf { it.calories }} cal",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    meals.forEach { meal ->
                        MealItemCard(
                            meal = meal,
                            onEdit = {
                                onNavigateToMealDetail(meal)
                            },
                            onDelete = {
                                showDeleteMealDialog = meal
                            }
                        )
                    }
                }
            }

            // Workouts Section
            if (workouts.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.FitnessCenter,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Workouts (${workouts.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${workouts.sumOf { it.caloriesBurned ?: 0 }} cal",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    workouts.forEach { workout ->
                        WorkoutItemCard(
                            workout = workout,
                            onEdit = {
                                // Navigate to workout log screen
                                onNavigateToWorkoutLog()
                            },
                            onDelete = {
                                showDeleteWorkoutDialog = workout
                            }
                        )
                    }
                }
            }

            // Sleep Section
            if (sleepLogs.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Bedtime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Sleep",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    sleepLogs.forEach { sleep ->
                        SleepItemCard(
                            sleep = sleep,
                            useImperial = useImperial,
                            onEdit = {
                                // Navigate to sleep log screen
                                onNavigateToSleepLog()
                            },
                            onDelete = {
                                showDeleteSleepDialog = sleep
                            }
                        )
                    }
                }
            }

            // Water Section
            if (waterLogs.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.LocalDrink,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Water (${waterLogs.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = if (useImperial) {
                                    val totalOz = waterLogs.sumOf { (it.ml * 0.033814).roundToInt() }
                                    "${totalOz} fl oz"
                                } else {
                                    "${waterLogs.sumOf { it.ml }} ml"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    waterLogs.forEach { water ->
                        WaterItemCard(
                            water = water,
                            useImperial = useImperial,
                            onEdit = {
                                // Navigate to water log screen
                                onNavigateToWaterLog()
                            },
                            onDelete = {
                                showDeleteWaterDialog = water
                            }
                        )
                    }
                }
            }

            // Weight Section
            if (weightLogs.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.MonitorWeight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Weight (${weightLogs.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    weightLogs.forEach { weight ->
                        WeightItemCard(
                            weight = weight,
                            useImperial = useImperial,
                            onEdit = {
                                // Navigate to weight log screen
                                onNavigateToWeightLog()
                            },
                            onDelete = {
                                showDeleteWeightDialog = weight
                            }
                        )
                    }
                }
            }

            // Empty state with quick log buttons
            if (meals.isEmpty() && workouts.isEmpty() && sleepLogs.isEmpty() && waterLogs.isEmpty() && weightLogs.isEmpty()) {
                EmptyLogState(
                    onNavigateToMealLog = onNavigateToMealLog,
                    onNavigateToSupplementLog = onNavigateToSupplementLog,
                    onNavigateToWorkoutLog = onNavigateToWorkoutLog,
                    onNavigateToSleepLog = onNavigateToSleepLog,
                    onNavigateToWaterLog = onNavigateToWaterLog,
                    onNavigateToWeightLog = onNavigateToWeightLog,
                    onNavigateToHealthTracking = onNavigateToHealthTracking
                )
            }
            }
        }
    }

    // Delete confirmation dialogs
    showDeleteMealDialog?.let { meal ->
        AlertDialog(
            onDismissRequest = { showDeleteMealDialog = null },
            title = { Text("Delete Meal") },
            text = { Text("Are you sure you want to delete this meal entry? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteHealthLog(meal)
                        showDeleteMealDialog = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMealDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    showDeleteWorkoutDialog?.let { workout ->
        AlertDialog(
            onDismissRequest = { showDeleteWorkoutDialog = null },
            title = { Text("Delete Workout") },
            text = { Text("Are you sure you want to delete this workout entry? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteHealthLog(workout)
                        showDeleteWorkoutDialog = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteWorkoutDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    showDeleteSleepDialog?.let { sleep ->
        AlertDialog(
            onDismissRequest = { showDeleteSleepDialog = null },
            title = { Text("Delete Sleep Log") },
            text = { Text("Are you sure you want to delete this sleep entry? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteHealthLog(sleep)
                        showDeleteSleepDialog = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSleepDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    showDeleteWaterDialog?.let { water ->
        AlertDialog(
            onDismissRequest = { showDeleteWaterDialog = null },
            title = { Text("Delete Water Log") },
            text = { Text("Are you sure you want to delete this water entry? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteHealthLog(water)
                        showDeleteWaterDialog = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteWaterDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    showDeleteWeightDialog?.let { weight ->
        AlertDialog(
            onDismissRequest = { showDeleteWeightDialog = null },
            title = { Text("Delete Weight Log") },
            text = { Text("Are you sure you want to delete this weight entry? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteHealthLog(weight)
                        showDeleteWeightDialog = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteWeightDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}


