package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.DailyLog
import com.coachie.app.data.model.HealthLog
import com.coachie.app.ui.components.*
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.viewmodel.HomeDashboardViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.coachie.app.ui.components.CoachieCard as Card
import com.coachie.app.ui.components.CoachieCardDefaults as CardDefaults

@Composable
fun WeightTrendTab(dashboardViewModel: HomeDashboardViewModel, refreshTrigger: String) {
    // Load weight data from HealthLogs
    var weightData by remember { mutableStateOf<List<WeightDataPoint>>(emptyList()) }
    val goalWeight = dashboardViewModel.profile.value?.goalWeight
    val useImperial by dashboardViewModel.useImperial.collectAsState()

    LaunchedEffect(dashboardViewModel.userId, refreshTrigger) {
        // Load last 30 days of weight data from HealthLogs
        val today = LocalDate.now()
        val dataPoints = mutableListOf<WeightDataPoint>()
        val repository = FirebaseRepository.getInstance()

        for (i in 0..29) {
            val date = today.minusDays(i.toLong())
            val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            // Get weight logs for this date
            val logsResult = repository.getHealthLogsByType(
                dashboardViewModel.userId,
                dateStr,
                HealthLog.WeightLog.TYPE
            )

            val weightLogs = logsResult.getOrNull()?.filterIsInstance<HealthLog.WeightLog>() ?: emptyList()

            // Get the latest weight log for this date (in case multiple entries)
            weightLogs.maxByOrNull { it.timestamp }?.let { weightLog ->
                // Convert to kg if needed
                val weightInKg = if (weightLog.unit == "lbs") {
                    weightLog.weight / 2.205
                } else {
                    weightLog.weight
                }

                dataPoints.add(
                    WeightDataPoint(
                        date = date,
                        weight = weightInKg
                    )
                )
            }
        }

        weightData = dataPoints.sortedBy { it.date }
    }

    WeightChart(
        weightData = weightData,
        goalWeight = goalWeight,
        useImperial = useImperial,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun SleepAverageTab(dashboardViewModel: HomeDashboardViewModel, refreshTrigger: String) {
    var sleepData by remember { mutableStateOf<List<SleepDataPoint>>(emptyList()) }

    LaunchedEffect(dashboardViewModel.userId, refreshTrigger) {
        val today = LocalDate.now()
        val dataPoints = mutableListOf<SleepDataPoint>()
        val repository = FirebaseRepository.getInstance()

        for (i in 0..6) {
            val date = today.minusDays(i.toLong())
            val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            val logsResult = repository.getHealthLogsByType(dashboardViewModel.userId, dateStr, HealthLog.SleepLog.TYPE)
            val sleepLogs = logsResult.getOrNull()?.filterIsInstance<HealthLog.SleepLog>() ?: emptyList()
            // Filter out invalid sleep durations (>24 hours is impossible) and take the most recent valid one
            // For charts, we want the actual sleep for that day, not a sum of multiple logs
            val validSleepLogs = sleepLogs.filter { it.durationHours in 0.0..24.0 }
            val sleepHours = validSleepLogs.maxByOrNull { it.timestamp }?.durationHours ?: 0.0

            dataPoints.add(SleepDataPoint(date, sleepHours))
        }

        sleepData = dataPoints.reversed()
    }

    SleepChart(data = sleepData, modifier = Modifier.fillMaxWidth())
}

@Composable
fun WorkoutCaloriesTab(dashboardViewModel: HomeDashboardViewModel, refreshTrigger: String) {
    var workoutData by remember { mutableStateOf<List<WorkoutCaloriesDataPoint>>(emptyList()) }

    LaunchedEffect(dashboardViewModel.userId, refreshTrigger) {
        val today = LocalDate.now()
        val dataPoints = mutableListOf<WorkoutCaloriesDataPoint>()
        val repository = FirebaseRepository.getInstance()

        for (i in 0..6) {
            val date = today.minusDays(i.toLong())
            val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            val logsResult = repository.getHealthLogsByType(dashboardViewModel.userId, dateStr, HealthLog.WorkoutLog.TYPE)
            val workoutLogs = logsResult.getOrNull()?.filterIsInstance<HealthLog.WorkoutLog>() ?: emptyList()

            val totalCalories = workoutLogs.sumOf { it.caloriesBurned.toDouble() }

            dataPoints.add(WorkoutCaloriesDataPoint(date, totalCalories))
        }

        workoutData = dataPoints.reversed()
    }

    WorkoutCaloriesChart(workouts = workoutData, modifier = Modifier.fillMaxWidth())
}

@Composable
fun MacrosPieChartTab(dashboardViewModel: HomeDashboardViewModel, refreshTrigger: String) {
    val todayHealthLogs by dashboardViewModel.todayHealthLogs.collectAsState()
    val meals = todayHealthLogs.filterIsInstance<HealthLog.MealLog>()

    val totalProtein = meals.sumOf { it.protein }
    val totalCarbs = meals.sumOf { it.carbs }
    val totalFat = meals.sumOf { it.fat }
    val totalCalories = meals.sumOf { it.calories }
    
    // Debug logging for macro calculation
    LaunchedEffect(meals.size) {
        android.util.Log.d("MacroChart", "=== CHARTS TAB MACRO CALCULATION ===")
        android.util.Log.d("MacroChart", "Meals count: ${meals.size}")
        meals.forEachIndexed { index, meal ->
            android.util.Log.d("MacroChart", "Meal ${index + 1}: ${meal.foodName} - Protein: ${meal.protein}g, Carbs: ${meal.carbs}g, Fat: ${meal.fat}g")
        }
        android.util.Log.d("MacroChart", "TOTAL - Protein: ${totalProtein}g, Carbs: ${totalCarbs}g, Fat: ${totalFat}g")
    }

    val macroTargets = dashboardViewModel.getMacroTargets()

    MacrosPieChart(
        protein = totalProtein,
        carbs = totalCarbs,
        fat = totalFat,
        targetProtein = macroTargets.proteinGrams,
        targetCarbs = macroTargets.carbsGrams,
        targetFat = macroTargets.fatGrams,
        calorieTarget = macroTargets.calorieGoal,
        recommendation = macroTargets.recommendation,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun WaterStreakTab(dashboardViewModel: HomeDashboardViewModel, refreshTrigger: String) {
    var waterData by remember { mutableStateOf<List<WaterStreakDataPoint>>(emptyList()) }
    val useImperial by dashboardViewModel.useImperial.collectAsState()

    LaunchedEffect(dashboardViewModel.userId, refreshTrigger) {
        val today = LocalDate.now()
        val dataPoints = mutableListOf<WaterStreakDataPoint>()
        val repository = FirebaseRepository.getInstance()

        for (i in 0..6) {
            val date = today.minusDays(i.toLong())
            val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            val logResult = repository.getDailyLog(dashboardViewModel.userId, dateStr)

            val dailyLog = logResult.getOrNull()
            val waterMl = dailyLog?.water?.toDouble() ?: 0.0

            dataPoints.add(WaterStreakDataPoint(date, waterMl))
        }

        waterData = dataPoints.reversed()
    }

    WaterStreakChart(
        waterData = waterData,
        useImperial = useImperial,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Charts Tab View with 5 tabs
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChartsTabView(
    dashboardViewModel: HomeDashboardViewModel,
    modifier: Modifier = Modifier
) {
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val todayLog by dashboardViewModel.todayLog.collectAsState()
    val todayHealthLogs by dashboardViewModel.todayHealthLogs.collectAsState()
    val profile by dashboardViewModel.profile.collectAsState()

    // Create a refresh trigger that changes when dashboard data updates
    val refreshTrigger by remember {
        derivedStateOf {
            // Combine multiple state values to create a trigger that changes when any data updates
            "${todayLog?.date ?: "none"}-${todayHealthLogs.size}-${profile?.name ?: "none"}"
        }
    }

    val pagerState = rememberPagerState(pageCount = { 5 })
    val tabs = listOf(
        "Weight" to Icons.Filled.MonitorWeight,
        "Sleep" to Icons.Filled.Bedtime,
        "Workouts" to Icons.Filled.FitnessCenter,
        "Macros" to Icons.Filled.Restaurant,
        "Water" to Icons.Filled.LocalDrink
    )
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradientBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Tab row
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                tabs.forEachIndexed { index, (title, icon) ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        icon = { Icon(icon, contentDescription = title) },
                        text = { Text(title) }
                    )
                }
            }

            // Tab content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (page) {
                        0 -> WeightTrendTab(dashboardViewModel = dashboardViewModel, refreshTrigger = refreshTrigger)
                        1 -> SleepAverageTab(dashboardViewModel = dashboardViewModel, refreshTrigger = refreshTrigger)
                        2 -> WorkoutCaloriesTab(dashboardViewModel = dashboardViewModel, refreshTrigger = refreshTrigger)
                        3 -> MacrosPieChartTab(dashboardViewModel = dashboardViewModel, refreshTrigger = refreshTrigger)
                        4 -> WaterStreakTab(dashboardViewModel = dashboardViewModel, refreshTrigger = refreshTrigger)
                    }
                }
            }
        }
    }
}
