package com.coachie.app.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.components.NavigationTileCard
import com.coachie.app.ui.components.SugarIntakeCard
import com.coachie.app.ui.theme.Primary40
import com.coachie.app.ui.theme.Secondary40
import com.coachie.app.ui.theme.Tertiary40
import com.coachie.app.ui.theme.Accent40
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.ui.theme.LocalGender
import com.coachie.app.ui.theme.SemanticColorCategory
import com.coachie.app.ui.theme.getSemanticColor
import com.coachie.app.ui.theme.getSemanticColorPrimary
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.HealthLog
import com.coachie.app.viewmodel.AuthState
import com.coachie.app.viewmodel.CoachieViewModel
import com.coachie.app.viewmodel.HomeDashboardViewModel
import com.coachie.app.domain.MacroTargetsCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlin.math.roundToInt

// Chart types
enum class ChartMetric(val displayName: String) {
    CALORIES("Calories"),
    WATER("Water"),
    SLEEP("Sleep"),
    WEIGHT("Weight"),
    MACROS("Macros"),
    SUGAR("Sugar")
}

enum class HealthTimeRange {
    SEVEN_DAYS,
    MONTHLY,
    QUARTERLY
}

data class ChartDataPoint(
    val date: java.time.LocalDate,
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val steps: Int,
    val water: Int,
    val sleepHours: Double,
    val weight: Double? = null, // in kg
    val sugar: Int = 0,
    val addedSugar: Int = 0
)

// Health Tool data class and composable
data class HealthTool(
    val name: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val onClick: () -> Unit
)

@Composable
fun HealthToolCard(
    tool: HealthTool,
    modifier: Modifier = Modifier
) {
    CoachieCard(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        onClick = tool.onClick,
        colors = CoachieCardDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.95f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.name,
                modifier = Modifier.size(32.dp),
                tint = tool.color
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = tool.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthTrackingDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMealLog: () -> Unit = {},
    onNavigateToSupplementLog: () -> Unit = {},
    onNavigateToWorkoutLog: () -> Unit = {},
    onNavigateToSleepLog: () -> Unit = {},
    onNavigateToWaterLog: () -> Unit = {},
    onNavigateToWeightLog: () -> Unit = {},
    onNavigateToCycleTracker: () -> Unit = {},
    onNavigateToDailyLog: () -> Unit = {},
    onNavigateToMicronutrientTracker: () -> Unit = {},
    onNavigateToSugarDetail: () -> Unit = {},
    viewModel: CoachieViewModel = viewModel()
) {
    var isSyncing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val authState by viewModel.authState.collectAsState()
    val currentUser = (authState as? AuthState.Authenticated)?.user
    val userId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: return
    val userProfile by viewModel.userProfile.collectAsState()

    // Create dashboard ViewModel with proper factory
    val dashboardViewModel = viewModel<HomeDashboardViewModel>(
        key = userId,
        factory = HomeDashboardViewModel.Factory(
            repository = FirebaseRepository.getInstance(),
            userId = userId,
            context = context
        )
    )

    // Get today's data from dashboard view model
    val todayHealthLogs by dashboardViewModel.todayHealthLogs.collectAsState()
    val todayLog by dashboardViewModel.todayLog.collectAsState()
    val userGoals by dashboardViewModel.userGoals.collectAsState()
    val useImperial by dashboardViewModel.useImperial.collectAsState()
    val profile by dashboardViewModel.profile.collectAsState()

    // Load dashboard data when screen appears and refresh when returning
    LaunchedEffect(userId) {
        dashboardViewModel.refresh()
    }
    
    // CRITICAL: Refresh when screen becomes visible to ensure data matches dashboard
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(userId) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                android.util.Log.d("HealthTrackingDashboardScreen", "Screen resumed - refreshing data")
                dashboardViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Calculate stats from health logs
    val meals = todayHealthLogs.filterIsInstance<HealthLog.MealLog>()
    val workouts = todayHealthLogs.filterIsInstance<HealthLog.WorkoutLog>()
    val sleepLogs = todayHealthLogs.filterIsInstance<HealthLog.SleepLog>()
    val waterLogs = todayHealthLogs.filterIsInstance<HealthLog.WaterLog>()
    
    val caloriesConsumed = meals.sumOf { it.calories }
    // Calories burned: prefer Google Fit energy expended (total daily energy), otherwise use workout calories
    // Google Fit calories = Total Daily Energy Expenditure (TDEE) = BMR + activity calories
    // This includes all calories burned throughout the day, not just workouts
    val workoutCalories = workouts.sumOf { it.caloriesBurned ?: 0 }
    val caloriesBurned = workoutCalories
    // CRITICAL FIX: Voice logging saves water to BOTH DailyLog.water AND WaterLog entries
    // So we should use DailyLog.water as the source of truth (it already includes voice logs)
    // Only add WaterLog entries if DailyLog.water is not set
    // This matches the logic in HomeScreen's TodaysLogCard
    val currentLog = todayLog
    val waterMl = if (currentLog?.water != null && currentLog.water!! > 0) {
        // DailyLog.water already includes voice logs, so use it directly
        currentLog.water!!
    } else {
        // Fallback: sum WaterLog entries if DailyLog.water is not set
        waterLogs.sumOf { it.ml }
    }
    // Filter out invalid sleep logs (duration > 24 hours is impossible) and take the most recent valid one
    val validSleepLogs = sleepLogs.filter { it.durationHours in 0.0..24.0 }
    val sleepHours = validSleepLogs.maxByOrNull { it.timestamp }?.durationHours ?: 0.0
    val steps = todayLog?.steps ?: 0

    // Goals
    // Get daily calorie goal from profile's computed property (calculated using Mifflin-St Jeor BMR formula)
    // This is calculated based on: weight, height, age, activity level, and weight goal
    val dailyCalorieGoal = profile?.estimatedDailyCalories ?: 2000
    val waterGoal = 2000 // 2 liters in ml
    val stepsGoal = (userGoals?.get("dailySteps") as? Number)?.toInt() ?: 10000
    val sleepGoal = 8.0 // hours

    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    
    // Get semantic color for Health Tracking (orange theme)
    val gender = LocalGender.current
    val isMale = gender.lowercase() == "male"
    val healthColor = getSemanticColorPrimary(SemanticColorCategory.HEALTH_TRACKING, isMale)
    val healthPalette = getSemanticColor(SemanticColorCategory.HEALTH_TRACKING, isMale)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBackground)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CoachieCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CoachieCardDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.95f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                "Back",
                                tint = healthColor
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Health Tracking",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = healthColor,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (!isSyncing) {
                                    isSyncing = true
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            android.util.Log.d("HealthTrackingDashboard", "🚀 Manual sync triggered")
                                            com.coachie.app.service.HealthSyncService.sync(context)
                                        } catch (e: Exception) {
                                            android.util.Log.e("HealthTrackingDashboard", "Sync failed", e)
                                            e.printStackTrace()
                                        } finally {
                                            kotlinx.coroutines.delay(1000)
                                            isSyncing = false
                                            // Refresh dashboard data to show synced data
                                            dashboardViewModel.refresh()
                                        }
                                    }
                                }
                            },
                            enabled = !isSyncing
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = healthColor,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Sync,
                                    "Sync Google Fit",
                                    tint = healthColor
                                )
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(
                    top = 16.dp,
                    bottom = 16.dp
                )
            ) {
                // Daily Progress Section
                item {
                    Text(
                        "Today's Progress",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Daily Progress Bars
                item {
                    DailyProgressCard(
                        caloriesConsumed = caloriesConsumed,
                        caloriesBurned = caloriesBurned,
                        caloriesGoal = dailyCalorieGoal,
                        waterMl = waterMl,
                        waterGoal = waterGoal,
                        steps = steps,
                        stepsGoal = stepsGoal,
                        sleepHours = sleepHours,
                        sleepGoal = sleepGoal,
                        weeklyWorkouts = remember { 
                            mutableStateOf(0)
                        },
                        useImperial = useImperial,
                        userId = userId,
                        primaryColor = healthColor,
                        palette = healthPalette
                    )
                }

                // Sugar Intake Card
                item {
                    // CRITICAL: Use null-safe access and ensure we're summing all sugar values
                    val totalSugar = meals.sumOf { it.sugar ?: 0 }
                    val totalAddedSugar = meals.sumOf { it.addedSugar ?: 0 }
                    
                    // Debug logging
                    if (meals.isNotEmpty()) {
                        android.util.Log.d("HealthTrackingDashboard", "Sugar calculation: ${meals.size} meals")
                        meals.forEach { meal ->
                            android.util.Log.d("HealthTrackingDashboard", "  Meal: ${meal.foodName}, sugar=${meal.sugar}, addedSugar=${meal.addedSugar}")
                        }
                        android.util.Log.d("HealthTrackingDashboard", "  Total sugar: $totalSugar, Total added sugar: $totalAddedSugar")
                    }
                    
                    SugarIntakeCard(
                        totalSugar = totalSugar,
                        totalAddedSugar = totalAddedSugar,
                        onClick = onNavigateToSugarDetail
                    )
                }

                // Quick Access Section
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Quick Log",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Health Logging Tools - use health tracking semantic color
                val healthTools = listOf(
                    HealthTool("Meals", Icons.Filled.Restaurant, healthColor, onNavigateToMealLog),
                    HealthTool("Supplements", Icons.Filled.Medication, healthColor, onNavigateToSupplementLog),
                    HealthTool("Workouts", Icons.Filled.FitnessCenter, healthColor, onNavigateToWorkoutLog),
                    HealthTool("Sleep", Icons.Filled.Bedtime, healthColor, onNavigateToSleepLog),
                    HealthTool("Water", Icons.Filled.LocalDrink, healthColor, onNavigateToWaterLog),
                    HealthTool("Weight", Icons.Filled.MonitorWeight, healthColor, onNavigateToWeightLog)
                )
                
                items(healthTools.chunked(2)) { rowTools ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        rowTools.forEach { tool ->
                            HealthToolCard(
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
                
                // Tracking & Analysis Section
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tracking & Analysis",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Only show Cycle Tracker for females
                val shouldShowCycleTracker = userProfile?.shouldShowMenstrualTracker == true
                android.util.Log.d("HealthTracking", "Cycle tracker visibility: gender=${userProfile?.gender}, shouldShowMenstrualTracker=$shouldShowCycleTracker")
                if (shouldShowCycleTracker) {
                    item {
                        NavigationTileCard(
                            title = "Cycle Tracker",
                            description = "Track your menstrual cycle and symptoms",
                            icon = Icons.Filled.CalendarToday,
                            iconTint = healthColor,
                            backgroundColor = healthColor,
                            onClick = onNavigateToCycleTracker
                        )
                    }
                }
                
                item {
                    NavigationTileCard(
                        title = "Today's Log",
                        description = "View and edit today's complete health log",
                        icon = Icons.Filled.List,
                        iconTint = healthColor,
                        backgroundColor = healthColor,
                        onClick = onNavigateToDailyLog
                    )
                }
                
                item {
                    NavigationTileCard(
                        title = "Vitamins & Minerals",
                        description = "Track your micronutrient intake and supplements",
                        icon = Icons.Filled.Medication,
                        iconTint = healthColor,
                        backgroundColor = healthColor,
                        onClick = onNavigateToMicronutrientTracker
                    )
                }

                // Charts Section
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Charts & Trends",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                item {
                    ChartsAndTrendsCard(
                        userId = userId,
                        useImperial = useImperial,
                        primaryColor = healthColor,
                        palette = healthPalette
                    )
                }
            }
        }
    }
}

@Composable
fun DailyProgressCard(
    caloriesConsumed: Int,
    caloriesBurned: Int,
    caloriesGoal: Int,
    waterMl: Int,
    waterGoal: Int,
    steps: Int,
    stepsGoal: Int,
    sleepHours: Double,
    sleepGoal: Double,
    weeklyWorkouts: MutableState<Int>,
    useImperial: Boolean,
    userId: String,
    primaryColor: Color = Primary40,
    palette: com.coachie.app.ui.theme.SemanticColorPalette? = null
) {
    // Load weekly workouts count
    LaunchedEffect(userId) {
        val repository = FirebaseRepository.getInstance()
        val today = java.time.LocalDate.now()
        var count = 0
        
        for (i in 0..6) {
            val date = today.minusDays(i.toLong())
            val dateStr = date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val healthLogsResult = repository.getHealthLogs(userId, dateStr)
            val healthLogs = healthLogsResult.getOrNull() ?: emptyList()
            val workouts = healthLogs.filterIsInstance<HealthLog.WorkoutLog>()
            if (workouts.isNotEmpty()) {
                count++
            }
        }
        
        weeklyWorkouts.value = count
    }
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Calories
            ProgressBarItem(
                icon = Icons.Filled.LocalFireDepartment,
                label = "Calories",
                current = caloriesConsumed,
                goal = caloriesGoal,
                unit = "cal",
                color = primaryColor,
                progress = (caloriesConsumed.toFloat() / caloriesGoal).coerceIn(0f, 1f)
            )

            // Water - ALWAYS display in GLASSES (8 oz per glass), rounded to nearest whole number
            // Convert ml to glasses: ml -> oz -> glasses (8 oz per glass)
            val waterOz = waterMl * 0.033814 // Convert ml to fl oz
            val waterGlasses = (waterOz / 8.0).roundToInt() // Convert oz to glasses and round to nearest whole number
            val goalOz = waterGoal * 0.033814 // Convert goal ml to fl oz
            val goalGlasses = (goalOz / 8.0).roundToInt() // Convert goal oz to glasses and round
            val waterProgress = if (goalGlasses > 0) (waterGlasses.toFloat() / goalGlasses.toFloat()).coerceIn(0f, 1f) else 0f
            val waterColor = palette?.color80 ?: primaryColor.copy(alpha = 0.7f)
            ProgressBarItem(
                icon = Icons.Filled.LocalDrink,
                label = "Water",
                current = waterGlasses,
                goal = goalGlasses,
                unit = "glasses",
                color = waterColor,
                progress = waterProgress
            )

            // Steps
            val stepsColor = palette?.color30 ?: primaryColor.copy(alpha = 0.8f)
            ProgressBarItem(
                icon = Icons.Filled.DirectionsWalk,
                label = "Steps",
                current = steps,
                goal = stepsGoal,
                unit = "steps",
                color = stepsColor,
                progress = (steps.toFloat() / stepsGoal).coerceIn(0f, 1f)
            )

            // Sleep
            val sleepColor = palette?.color20 ?: primaryColor.copy(alpha = 0.9f)
            ProgressBarItem(
                icon = Icons.Filled.Bedtime,
                label = "Sleep",
                current = sleepHours.toInt(),
                goal = sleepGoal.toInt(),
                unit = "hours",
                color = sleepColor,
                progress = (sleepHours.toFloat() / sleepGoal.toFloat()).coerceIn(0f, 1f),
                showDecimal = true,
                decimalValue = sleepHours
            )

            // Weekly Workouts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FitnessCenter,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Weekly Workouts",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = "${weeklyWorkouts.value} / 7 days",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ProgressBarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    current: Int,
    goal: Int,
    unit: String,
    color: Color,
    progress: Float,
    showDecimal: Boolean = false,
    decimalValue: Double = 0.0
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = if (showDecimal) {
                    String.format("%.1f / %d %s", decimalValue, goal, unit)
                } else {
                    "$current / $goal $unit"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.2f)
        )
    }
}

@Composable
fun MacroPieChartLegendItem(label: String, value: Int, percent: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(color = color, shape = RoundedCornerShape(4.dp))
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall
            )
        }
        // Show the display text (either with targets or percentage)
        Text(
            text = if (value == 0) percent else "${value}g ($percent)",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun MacrosPieChartView(
    protein: Int,
    carbs: Int,
    fat: Int,
    targetProtein: Int? = null,
    targetCarbs: Int? = null,
    targetFat: Int? = null
) {
    // Calculate percentages based on GRAMS, not calories
    val totalGrams = protein + carbs + fat

    if (totalGrams == 0) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No macro data",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pie chart - smaller and with fixed width
            Canvas(
                modifier = Modifier
                    .size(120.dp)
                    .padding(end = 16.dp)
            ) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = (size.minDimension / 2 - 20.dp.toPx()).coerceAtLeast(0f)

                val proteinColor = Color(0xFF4CAF50) // Green
                val carbsColor = Color(0xFF2196F3) // Blue
                val fatColor = Color(0xFFFF9800) // Orange

                var startAngle = -90f // Start from top

                // Protein - based on GRAMS
                val proteinAngle = if (totalGrams > 0) (protein / totalGrams.toFloat()) * 360f else 0f
                drawArc(
                    color = proteinColor,
                    startAngle = startAngle,
                    sweepAngle = proteinAngle,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2)
                )
                startAngle += proteinAngle

                // Carbs - based on GRAMS
                val carbsAngle = if (totalGrams > 0) (carbs / totalGrams.toFloat()) * 360f else 0f
                drawArc(
                    color = carbsColor,
                    startAngle = startAngle,
                    sweepAngle = carbsAngle,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2)
                )
                startAngle += carbsAngle

                // Fat - based on GRAMS
                val fatAngle = if (totalGrams > 0) (fat / totalGrams.toFloat()) * 360f else 0f
                drawArc(
                    color = fatColor,
                    startAngle = startAngle,
                    sweepAngle = fatAngle,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2)
                )
            }

            // Legend
            Column(
                modifier = Modifier.padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Calculate percentages based on GRAMS, not calories
                val proteinPercent = if (totalGrams > 0) {
                    ((protein / totalGrams.toFloat()) * 100).toInt()
                } else 0
                val carbsPercent = if (totalGrams > 0) {
                    ((carbs / totalGrams.toFloat()) * 100).toInt()
                } else 0
                val fatPercent = if (totalGrams > 0) {
                    ((fat / totalGrams.toFloat()) * 100).toInt()
                } else 0
                
                // Show target comparison if targets are provided
                val proteinDisplay = if (targetProtein != null) {
                    val diff = protein - targetProtein
                    val diffText = if (diff > 0) "+$diff" else "$diff"
                    "${protein}g (target: ${targetProtein}g, $diffText)"
                } else {
                    "${protein}g (${proteinPercent}%)"
                }

                val carbsDisplay = if (targetCarbs != null) {
                    val diff = carbs - targetCarbs
                    val diffText = if (diff > 0) "+$diff" else "$diff"
                    "${carbs}g (target: ${targetCarbs}g, $diffText)"
                } else {
                    "${carbs}g (${carbsPercent}%)"
                }

                val fatDisplay = if (targetFat != null) {
                    val diff = fat - targetFat
                    val diffText = if (diff > 0) "+$diff" else "$diff"
                    "${fat}g (target: ${targetFat}g, $diffText)"
                } else {
                    "${fat}g (${fatPercent}%)"
                }

                // Use the new display format that shows value and target comparison in one line
                MacroPieChartLegendItem("Protein", 0, proteinDisplay, Color(0xFF4CAF50))
                MacroPieChartLegendItem("Carbs", 0, carbsDisplay, Color(0xFF2196F3))
                MacroPieChartLegendItem("Fat", 0, fatDisplay, Color(0xFFFF9800))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChartsAndTrendsCard(
    userId: String,
    useImperial: Boolean,
    primaryColor: Color = Primary40,
    palette: com.coachie.app.ui.theme.SemanticColorPalette? = null
) {
    var trendData by remember { mutableStateOf<List<ChartDataPoint>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var profile by remember { mutableStateOf<com.coachie.app.data.model.UserProfile?>(null) }
    var selectedTimeRange by remember { mutableStateOf(HealthTimeRange.SEVEN_DAYS) }
    
    val metrics = ChartMetric.values()
    val pagerState = rememberPagerState(pageCount = { metrics.size }, initialPage = 0)
    val selectedMetric = metrics[pagerState.currentPage]
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(userId, selectedTimeRange) {
        isLoading = true
        
        // Load user profile for macro targets
        try {
            val repository = FirebaseRepository.getInstance()
            val profileResult = repository.getUserProfile(userId)
            profile = profileResult.getOrNull()
        } catch (e: Exception) {
            android.util.Log.w("ChartsAndTrendsCard", "Failed to load profile", e)
        }
        
        // Load chart data
        val repository = FirebaseRepository.getInstance()
        val today = java.time.LocalDate.now()
        val dataPoints = mutableListOf<ChartDataPoint>()

        val days = when (selectedTimeRange) {
            HealthTimeRange.SEVEN_DAYS -> 7
            HealthTimeRange.MONTHLY -> 30
            HealthTimeRange.QUARTERLY -> 90
        }

        for (i in days - 1 downTo 0) {
            val date = today.minusDays(i.toLong())
            val dateStr = date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            val logResult = repository.getDailyLog(userId, dateStr)
            val healthLogsResult = repository.getHealthLogs(userId, dateStr)

            val dailyLog = logResult.getOrNull()
            val healthLogs = healthLogsResult.getOrNull() ?: emptyList()

            val meals = healthLogs.filterIsInstance<HealthLog.MealLog>()
            val calories = meals.sumOf { it.calories }
            val protein = meals.sumOf { it.protein }
            val carbs = meals.sumOf { it.carbs }
            val fat = meals.sumOf { it.fat }
            val sugar = meals.sumOf { it.sugar ?: 0 }
            val addedSugar = meals.sumOf { it.addedSugar ?: 0 }
            
            // Debug logging for macro calculation
            if (dateStr == java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))) {
                android.util.Log.d("MacroChart", "=== TODAY'S MACRO CALCULATION ===")
                android.util.Log.d("MacroChart", "Date: $dateStr, Meals count: ${meals.size}")
                meals.forEachIndexed { index, meal ->
                    android.util.Log.d("MacroChart", "Meal ${index + 1}: ${meal.foodName} - Protein: ${meal.protein}g, Carbs: ${meal.carbs}g, Fat: ${meal.fat}g")
                }
                android.util.Log.d("MacroChart", "TOTAL - Protein: ${protein}g, Carbs: ${carbs}g, Fat: ${fat}g")
            }
            val steps = dailyLog?.steps ?: 0
            val water = dailyLog?.water ?: 0
            
            val sleepLogs = healthLogs.filterIsInstance<HealthLog.SleepLog>()
            // Filter out invalid sleep durations (>24 hours is impossible) and take the most recent valid one
            val validSleepLogs = sleepLogs.filter { it.durationHours in 0.0..24.0 }
            val sleepHours = validSleepLogs.maxByOrNull { it.timestamp }?.durationHours ?: 0.0
            
            val weightLogs = healthLogs.filterIsInstance<HealthLog.WeightLog>()
            val weight = weightLogs.maxByOrNull { it.timestamp }?.let { log ->
                if (log.unit == "lbs") log.weight / 2.205 else log.weight
            }

            dataPoints.add(
                ChartDataPoint(
                    date = date,
                    calories = calories,
                    protein = protein,
                    carbs = carbs,
                    fat = fat,
                    steps = steps,
                    water = water,
                    sleepHours = sleepHours,
                    weight = weight,
                    sugar = sugar,
                    addedSugar = addedSugar
                )
            )
        }

        trendData = dataPoints
        isLoading = false
        
        // Debug: Log trend macro totals
        val trendProtein = trendData.sumOf { it.protein }
        val trendCarbs = trendData.sumOf { it.carbs }
        val trendFat = trendData.sumOf { it.fat }
        android.util.Log.d("ChartsAndTrendsCard", "Trend macros loaded ($days days) - Protein: ${trendProtein}g, Carbs: ${trendCarbs}g, Fat: ${trendFat}g")
    }

    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Trends",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Time Range Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Pair(HealthTimeRange.SEVEN_DAYS, "7 Days"),
                    Pair(HealthTimeRange.MONTHLY, "Monthly"),
                    Pair(HealthTimeRange.QUARTERLY, "Quarterly")
                ).forEach { (range, label) ->
                    FilterChip(
                        selected = selectedTimeRange == range,
                        onClick = { selectedTimeRange = range },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }

            // Metric selector - scrollable row for better UX (clickable to jump to page)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(metrics.size) { index ->
                    val metric = metrics[index]
                    FilterChip(
                        selected = selectedMetric == metric,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        label = { 
                            Text(
                                metric.displayName, 
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Black
                            ) 
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else if (trendData.isEmpty()) {
                Text(
                    text = "No data available yet. Start logging to see trends!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Swipeable chart container
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) { page ->
                    val currentMetric = metrics[page]
                    // Chart based on current metric
                    if (currentMetric == ChartMetric.MACROS) {
                        // Macros pie charts - daily and trend period
                        val today = java.time.LocalDate.now()
                        val todayData = trendData.find { it.date == today } ?: trendData.lastOrNull()
                        val dailyProtein = todayData?.protein ?: 0
                        val dailyCarbs = todayData?.carbs ?: 0
                        val dailyFat = todayData?.fat ?: 0
                        
                        val trendProtein = trendData.sumOf { it.protein }
                        val trendCarbs = trendData.sumOf { it.carbs }
                        val trendFat = trendData.sumOf { it.fat }
                        
                        // Calculate macro targets based on dietary preference
                        val macroTargets = remember(profile) {
                            MacroTargetsCalculator.calculate(profile)
                        }
                        
                        // Calculate trend period targets (daily * days)
                        val days = when (selectedTimeRange) {
                            HealthTimeRange.SEVEN_DAYS -> 7
                            HealthTimeRange.MONTHLY -> 30
                            HealthTimeRange.QUARTERLY -> 90
                        }
                        val trendProteinTarget = macroTargets.proteinGrams * days
                        val trendCarbsTarget = macroTargets.carbsGrams * days
                        val trendFatTarget = macroTargets.fatGrams * days
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 64.dp), // Add bottom padding to avoid microphone icon
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            // Daily Macros Pie Chart
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Daily Macros",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (dailyProtein == 0 && dailyCarbs == 0 && dailyFat == 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No macro data for today",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    MacrosPieChartView(
                                        protein = dailyProtein,
                                        carbs = dailyCarbs,
                                        fat = dailyFat,
                                        targetProtein = macroTargets.proteinGrams,
                                        targetCarbs = macroTargets.carbsGrams,
                                        targetFat = macroTargets.fatGrams
                                    )
                                }
                            }
                            
                            // Trend Period Macros Pie Chart
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = when (selectedTimeRange) {
                                        HealthTimeRange.SEVEN_DAYS -> "Weekly Macros (7 days)"
                                        HealthTimeRange.MONTHLY -> "Monthly Macros (30 days)"
                                        HealthTimeRange.QUARTERLY -> "Quarterly Macros (90 days)"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (trendProtein == 0 && trendCarbs == 0 && trendFat == 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No macro data for this week",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    MacrosPieChartView(
                                        protein = trendProtein,
                                        carbs = trendCarbs,
                                        fat = trendFat,
                                        targetProtein = trendProteinTarget,
                                        targetCarbs = trendCarbsTarget,
                                        targetFat = trendFatTarget
                                    )
                                }
                            }
                        }
                    } else {
                        // Other metrics as line graphs
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val timeRangeLabel = when (selectedTimeRange) {
                                HealthTimeRange.SEVEN_DAYS -> "7 days"
                                HealthTimeRange.MONTHLY -> "30 days"
                                HealthTimeRange.QUARTERLY -> "90 days"
                            }
                            
                            val metricData: Triple<String, Float, Color> = when (currentMetric) {
                                ChartMetric.CALORIES -> Triple(
                                    "Calories ($timeRangeLabel)",
                                    trendData.maxOfOrNull { it.calories }?.toFloat() ?: 1f,
                                    primaryColor
                                )
                                ChartMetric.WATER -> Triple(
                                    if (useImperial) "Water ($timeRangeLabel) - fl oz" else "Water ($timeRangeLabel) - ml",
                                    trendData.maxOfOrNull { 
                                        if (useImperial) (it.water * 0.033814).roundToInt() else it.water
                                    }?.toFloat() ?: 1f,
                                    palette?.color80 ?: primaryColor.copy(alpha = 0.7f)
                                )
                                ChartMetric.SLEEP -> Triple(
                                    "Sleep ($timeRangeLabel) - hours",
                                    trendData.maxOfOrNull { it.sleepHours.toFloat() } ?: 1f,
                                    palette?.color20 ?: primaryColor.copy(alpha = 0.9f)
                                )
                                ChartMetric.WEIGHT -> {
                                    val maxWeight = trendData
                                        .mapNotNull { it.weight }
                                        .maxOfOrNull { weight ->
                                            (if (useImperial) weight * 2.205 else weight).toFloat()
                                        } ?: 1f
                                    Triple(
                                        if (useImperial) "Weight ($timeRangeLabel) - lbs" else "Weight ($timeRangeLabel) - kg",
                                        maxWeight,
                                        palette?.color30 ?: primaryColor.copy(alpha = 0.8f)
                                    )
                                }
                                ChartMetric.SUGAR -> Triple(
                                    "Sugar ($timeRangeLabel) - g",
                                    trendData.maxOfOrNull { (it.sugar + it.addedSugar).toFloat() } ?: 1f,
                                    Color(0xFFF9A825)
                                )
                                ChartMetric.MACROS -> Triple("", 1f, primaryColor) // Should not reach here
                            }
                        
                        val (label, maxValue, color) = metricData
                        
                        // Prepare data points for line chart
                        val dataPoints = trendData.mapIndexed { index, dataPoint ->
                            val value: Float = when (currentMetric) {
                                ChartMetric.CALORIES -> dataPoint.calories.toFloat()
                                ChartMetric.SUGAR -> (dataPoint.sugar + dataPoint.addedSugar).toFloat() // Total sugar (regular + added)
                                ChartMetric.WATER -> if (useImperial) {
                                    (dataPoint.water * 0.033814).roundToInt().toFloat()
                                } else {
                                    dataPoint.water.toFloat()
                                }
                                ChartMetric.SLEEP -> dataPoint.sleepHours.toFloat()
                                ChartMetric.WEIGHT -> dataPoint.weight?.let {
                                    if (useImperial) (it * 2.205).toFloat() else it.toFloat()
                                } ?: 0f
                                ChartMetric.MACROS -> 0f // Should not reach here
                            }
                            Pair(index, value)
                        }.filter { it.second > 0f } // Only include points with data
                        
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // Line chart using Canvas
                        val gridLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        val textMeasurer = rememberTextMeasurer()
                        val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .padding(bottom = 64.dp) // Add bottom padding to avoid microphone icon
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val chartWidth = size.width - 40.dp.toPx() // Padding on sides
                                val chartHeight = size.height - 40.dp.toPx() // Padding top/bottom
                                val startX = 20.dp.toPx()
                                val startY = 20.dp.toPx()
                                
                                if (dataPoints.isNotEmpty() && maxValue > 0f) {
                                    // Draw grid lines
                                    
                                    // Draw horizontal grid lines
                                    for (i in 0..4) {
                                        val y = startY + (chartHeight / 4) * i
                                        drawLine(
                                            color = gridLineColor,
                                            start = androidx.compose.ui.geometry.Offset(startX, y),
                                            end = androidx.compose.ui.geometry.Offset(startX + chartWidth, y),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }
                                    
                                    // Calculate point positions
                                    val pointSpacing = chartWidth / (trendData.size - 1).coerceAtLeast(1)
                                    val points = trendData.mapIndexed { index, dataPoint ->
                                        val value: Float = when (currentMetric) {
                                            ChartMetric.CALORIES -> dataPoint.calories.toFloat()
                                            ChartMetric.WATER -> if (useImperial) {
                                                (dataPoint.water * 0.033814).roundToInt().toFloat()
                                            } else {
                                                dataPoint.water.toFloat()
                                            }
                                            ChartMetric.SLEEP -> dataPoint.sleepHours.toFloat()
                                            ChartMetric.WEIGHT -> dataPoint.weight?.let {
                                                if (useImperial) (it * 2.205).toFloat() else it.toFloat()
                                            } ?: 0f
                                            ChartMetric.MACROS -> 0f
                                            ChartMetric.SUGAR -> (dataPoint.sugar + dataPoint.addedSugar).toFloat() // Total sugar (regular + added)
                                        }
                                        
                                        val x = startX + (index * pointSpacing)
                                        val normalizedValue = if (maxValue > 0f) (value / maxValue).coerceIn(0f, 1f) else 0f
                                        val y = startY + chartHeight - (normalizedValue * chartHeight)
                                        
                                        androidx.compose.ui.geometry.Offset(x, y) to value
                                    }
                                    
                                    // Draw line connecting points
                                    val validPoints = points.filter { it.second > 0f }
                                    if (validPoints.size > 1) {
                                        for (i in 0 until validPoints.size - 1) {
                                            drawLine(
                                                color = color,
                                                start = validPoints[i].first,
                                                end = validPoints[i + 1].first,
                                                strokeWidth = 3.dp.toPx(),
                                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                                pathEffect = null
                                            )
                                        }
                                    }
                                    
                                    // Draw points
                                    validPoints.forEach { (point, value) ->
                                        drawCircle(
                                            color = color,
                                            radius = 5.dp.toPx(),
                                            center = point
                                        )
                                    }
                                    
                                    // Draw X-axis labels (dates) - matching Flow Score format
                                    val chartBottom = startY + chartHeight
                                    if (trendData.size <= 7) {
                                        // Show all dates for 7 days or less
                                        trendData.forEachIndexed { index, dataPoint ->
                                            val x = startX + (index * pointSpacing)
                                            val dateLabel = dataPoint.date.format(DateTimeFormatter.ofPattern("MM/dd"))
                                            
                                            val textLayoutResult = textMeasurer.measure(
                                                dateLabel,
                                                TextStyle(fontSize = 10.sp)
                                            )
                                            
                                            drawText(
                                                textLayoutResult,
                                                color = onSurfaceVariant,
                                                topLeft = Offset(x - textLayoutResult.size.width / 2, chartBottom + 8.dp.toPx())
                                            )
                                        }
                                    } else {
                                        // Show first, middle, and last dates for longer ranges
                                        val indices = listOf(0, trendData.size / 2, trendData.size - 1)
                                        indices.forEach { index ->
                                            val x = startX + (index * pointSpacing)
                                            val dateLabel = trendData[index].date.format(DateTimeFormatter.ofPattern("MM/dd"))
                                            
                                            val textLayoutResult = textMeasurer.measure(
                                                dateLabel,
                                                TextStyle(fontSize = 10.sp)
                                            )
                                            
                                            drawText(
                                                textLayoutResult,
                                                color = onSurfaceVariant,
                                                topLeft = Offset(x - textLayoutResult.size.width / 2, chartBottom + 8.dp.toPx())
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }
                }
            }
        }
    }
}
