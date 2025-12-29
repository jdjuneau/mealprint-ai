package com.coachie.app.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.HabitRepository
import com.coachie.app.data.model.HealthLog
import com.coachie.app.data.model.Habit
import com.coachie.app.data.model.HabitCompletion
import com.coachie.app.data.model.DailyLog
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.util.DailyScoreCalculator
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.viewmodel.AuthViewModel
import com.coachie.app.viewmodel.AuthState

enum class ScoreTimeRange {
    SEVEN_DAYS,
    MONTHLY,
    QUARTERLY
}

data class ScoreDataPoint(
    val date: String,
    val score: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowScoreDetailsScreen(
    onNavigateBack: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val authState by authViewModel.authState.collectAsState()
    val userId = (authState as? AuthState.Authenticated)?.user?.uid ?: ""

    val repository = remember { FirebaseRepository.getInstance() }
    val habitRepository = remember { HabitRepository.getInstance() }

    var flowScore by remember { mutableStateOf(0) }
    var healthScore by remember { mutableStateOf(0) }
    var wellnessScore by remember { mutableStateOf(0) }
    var habitsScore by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTimeRange by remember { mutableStateOf(ScoreTimeRange.SEVEN_DAYS) }
    var scoreHistory by remember { mutableStateOf<List<ScoreDataPoint>>(emptyList()) }
    var isLoadingHistory by remember { mutableStateOf(false) }

    // Load habits and completions (same as HomeScreen)
    var habits by remember { mutableStateOf<List<Habit>>(emptyList()) }
    var habitCompletions by remember { mutableStateOf<List<HabitCompletion>>(emptyList()) }
    
    LaunchedEffect(userId) {
        if (userId.isNotBlank()) {
            // Load habits
            habitRepository.getHabits(userId).collect { habitList ->
                habits = habitList
            }
        }
    }
    
    LaunchedEffect(userId) {
        if (userId.isNotBlank()) {
            // Load today's completions (same as HomeScreen)
            habitRepository.getRecentCompletions(userId, days = 1).collect { completions ->
                val today = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
                
                val tomorrow = today + (24 * 60 * 60 * 1000)
                
                val filtered = completions.filter {
                    it.completedAt.time >= today && it.completedAt.time < tomorrow
                }
                
                android.util.Log.d("FlowScoreDetailsScreen", "Habit completions updated: ${filtered.size} completions today")
                habitCompletions = filtered
            }
        }
    }

    // Calculate the same score as HomeScreen - always use local calculation to include habits
    LaunchedEffect(userId, habits, habitCompletions) {
        if (userId.isNotBlank()) {
            try {
                val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

                // Get all the same data that HomeScreen uses
                val healthLogsResult = repository.getHealthLogs(userId, dateStr)
                val dailyLogResult = repository.getDailyLog(userId, dateStr)

                val healthLogs = healthLogsResult.getOrNull() ?: emptyList()
                val dailyLog = dailyLogResult.getOrNull()

                val meals = healthLogs.filterIsInstance<HealthLog.MealLog>()
                val workouts = healthLogs.filterIsInstance<HealthLog.WorkoutLog>()
                val sleepLogs = healthLogs.filterIsInstance<HealthLog.SleepLog>()
                val waterLogs = healthLogs.filterIsInstance<HealthLog.WaterLog>()

                android.util.Log.d("FlowScoreDetailsScreen", "Data counts - meals: ${meals.size}, workouts: ${workouts.size}, sleepLogs: ${sleepLogs.size}, waterLogs: ${waterLogs.size}, healthLogs: ${healthLogs.size}, habits: ${habits.size}, completions: ${habitCompletions.size}")
                
                // Check for circle interactions today
                val repository = com.coachie.app.data.FirebaseRepository.getInstance()
                val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                val hasCircleInteraction = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                    repository.hasCircleInteractionToday(userId, today).getOrNull() ?: false
                }
                
                val allTodaysFocusTasksCompleted = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                    repository.areAllTodaysFocusTasksCompleted(userId, today).getOrNull() ?: false
                }

                val categoryScores = DailyScoreCalculator.calculateAllScores(
                    meals = meals,
                    workouts = workouts,
                    sleepLogs = sleepLogs,
                    waterLogs = waterLogs,
                    allHealthLogs = healthLogs,
                    dailyLog = dailyLog,
                    habits = habits,
                    habitCompletions = habitCompletions,
                    hasCircleInteractionToday = hasCircleInteraction,
                    allTodaysFocusTasksCompleted = allTodaysFocusTasksCompleted
                )

                // Use the same calculation as HomeScreen
                val calculatedScore = categoryScores.calculateDailyScore()
                flowScore = if (calculatedScore > 0) calculatedScore else 50 // Same minimum as HomeScreen
                healthScore = categoryScores.healthScore
                wellnessScore = categoryScores.wellnessScore
                habitsScore = categoryScores.habitsScore

                android.util.Log.d("FlowScoreDetailsScreen", "Calculated scores - Flow: $flowScore, Health: $healthScore, Wellness: $wellnessScore, Habits: $habitsScore")

            } catch (e: Exception) {
                android.util.Log.e("FlowScoreDetailsScreen", "Failed to calculate scores", e)
                flowScore = 50 // Same fallback as HomeScreen
                healthScore = 0
                wellnessScore = 0
                habitsScore = 0
            }

            isLoading = false
        } else {
            isLoading = false
        }
    }

    // Load score history when time range changes
    LaunchedEffect(userId, selectedTimeRange) {
        if (userId.isNotBlank() && !isLoading) {
            isLoadingHistory = true
            try {
                val days = when (selectedTimeRange) {
                    ScoreTimeRange.SEVEN_DAYS -> 7
                    ScoreTimeRange.MONTHLY -> 30
                    ScoreTimeRange.QUARTERLY -> 90
                }
                
                val history = mutableListOf<ScoreDataPoint>()
                val today = LocalDate.now()
                
                for (i in days - 1 downTo 0) {
                    val date = today.minusDays(i.toLong())
                    val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    
                    try {
                        val healthLogsResult = repository.getHealthLogs(userId, dateStr)
                        val dailyLogResult = repository.getDailyLog(userId, dateStr)
                        val healthLogs = healthLogsResult.getOrNull() ?: emptyList()
                        val dailyLog = dailyLogResult.getOrNull()
                        
                        // Get habits and completions for that date
                        val completionsFlow = habitRepository.getRecentCompletions(userId, days = days)
                        val allCompletions = try {
                            kotlinx.coroutines.withTimeout(5000) {
                                completionsFlow.first()
                            }
                        } catch (e: Exception) {
                            emptyList()
                        }
                        
                        val dayStart = java.util.Calendar.getInstance().apply {
                            val instant = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
                            time = java.util.Date.from(instant)
                            set(java.util.Calendar.HOUR_OF_DAY, 0)
                            set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        
                        val dayEnd = dayStart + (24 * 60 * 60 * 1000)
                        
                        val dayCompletions = allCompletions.filter {
                            it.completedAt.time >= dayStart && it.completedAt.time < dayEnd
                        }
                        
                        val meals = healthLogs.filterIsInstance<HealthLog.MealLog>()
                        val workouts = healthLogs.filterIsInstance<HealthLog.WorkoutLog>()
                        val sleepLogs = healthLogs.filterIsInstance<HealthLog.SleepLog>()
                        val waterLogs = healthLogs.filterIsInstance<HealthLog.WaterLog>()
                        
                        val categoryScores = DailyScoreCalculator.calculateAllScores(
                            meals = meals,
                            workouts = workouts,
                            sleepLogs = sleepLogs,
                            waterLogs = waterLogs,
                            allHealthLogs = healthLogs,
                            dailyLog = dailyLog,
                            habits = habits,
                            habitCompletions = dayCompletions
                        )
                        
                        val score = categoryScores.calculateDailyScore()
                        history.add(ScoreDataPoint(dateStr, if (score > 0) score else 50))
                    } catch (e: Exception) {
                        android.util.Log.e("FlowScoreDetailsScreen", "Error loading score for $dateStr", e)
                        // Add placeholder score
                        history.add(ScoreDataPoint(dateStr, 50))
                    }
                }
                
                scoreHistory = history
            } catch (e: Exception) {
                android.util.Log.e("FlowScoreDetailsScreen", "Failed to load score history", e)
                scoreHistory = emptyList()
            }
            isLoadingHistory = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBackground)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Coachie Flow Score Details") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.Black,
                        navigationIconContentColor = Color.Black
                    )
                )
            }
        ) { paddingValues ->
            if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Calculating your Flow Score...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                // Overall Score Card - Centered
                CoachieCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CoachieCardDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Your Coachie Flow Score",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Score Circle - Centered
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .background(
                                    color = when {
                                        flowScore >= 80 -> Color(0xFF10B981)
                                        flowScore >= 60 -> Color(0xFFF59E0B)
                                        flowScore >= 40 -> Color(0xFFEF4444)
                                        else -> Color(0xFF6B46C1)
                                    },
                                    shape = androidx.compose.foundation.shape.CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$flowScore",
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Text(
                            text = "out of 100",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Black.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Score Progress Graph
                CoachieCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CoachieCardDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Score Progress",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        
                        // Time Range Tabs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                Pair(ScoreTimeRange.SEVEN_DAYS, "7 Days"),
                                Pair(ScoreTimeRange.MONTHLY, "Monthly"),
                                Pair(ScoreTimeRange.QUARTERLY, "Quarterly")
                            ).forEach { (range, label) ->
                                FilterChip(
                                    selected = selectedTimeRange == range,
                                    onClick = { selectedTimeRange = range },
                                    label = { Text(label) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSurface, // Changed from onPrimaryContainer (white) to onSurface (dark) for readability
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        labelColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        }
                        
                        // Graph
                        if (isLoadingHistory) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else if (scoreHistory.isNotEmpty()) {
                            ScoreProgressGraph(
                                scores = scoreHistory,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No data available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Black.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }

                // Score Breakdown - Much Clearer!
                CoachieCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CoachieCardDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Header explaining the formula
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "How Your Score is Calculated",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Text(
                                text = "Final Score = (Health × 50%) + (Wellness × 30%) + (Habits × 20%)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Black.copy(alpha = 0.8f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }

                        // Health Tracking (50% weight)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "🏥 Health Tracking (50% weight)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                    Text(
                                        text = "Calories, steps, water, sleep, workouts",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Black.copy(alpha = 0.7f)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "$healthScore/100",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            healthScore >= 80 -> Color(0xFF10B981)
                                            healthScore >= 60 -> Color(0xFFF59E0B)
                                            else -> Color(0xFFEF4444)
                                        }
                                    )
                                    Text(
                                        text = "+${(healthScore * 0.5).toInt()} pts",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Black.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        // Wellness (30% weight)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "🧘 Wellness (30% weight)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                    Text(
                                        text = "Mood, meditation, journaling, breathing, wins",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Black.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = "Bonus: Circle interaction (+10), All Today's Focus tasks (+15)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Black.copy(alpha = 0.6f),
                                        fontSize = 11.sp
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "$wellnessScore/100",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            wellnessScore >= 80 -> Color(0xFF10B981)
                                            wellnessScore >= 60 -> Color(0xFFF59E0B)
                                            else -> Color(0xFFEF4444)
                                        }
                                    )
                                    Text(
                                        text = "+${(wellnessScore * 0.3).toInt()} pts",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Black.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        // Habits (20% weight)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "🎯 Habits (20% weight)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                    Text(
                                        text = "Daily habit completion and streaks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Black.copy(alpha = 0.7f)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "$habitsScore/100",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            habitsScore >= 80 -> Color(0xFF10B981)
                                            habitsScore >= 60 -> Color(0xFFF59E0B)
                                            else -> Color(0xFFEF4444)
                                        }
                                    )
                                    Text(
                                        text = "+${(habitsScore * 0.2).toInt()} pts",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Black.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        // Math breakdown
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total Contribution:",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Text(
                                text = "${(healthScore * 0.5).toInt()} + ${(wellnessScore * 0.3).toInt()} + ${(habitsScore * 0.2).toInt()} = ${((healthScore * 0.5) + (wellnessScore * 0.3) + (habitsScore * 0.2)).toInt()}/100",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981) // Green for the total
                            )
                        }
                    }
                }

                // Bonus Points Section
                CoachieCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CoachieCardDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Bonus Points",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        
                        Text(
                            text = "Earn extra points by completing these activities:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Black.copy(alpha = 0.7f)
                        )
                        
                        // Health Tracking Bonuses
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Health Tracking Bonuses:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            BonusPointItem("Hit calorie goal (scaled: 50% = 15, 75% = 20, 100% = 25)", "+15-25 points")
                            BonusPointItem("Hit water goal (scaled by progress)", "+0-20 points")
                            BonusPointItem("Hit steps goal (scaled by progress)", "+0-15 points")
                            BonusPointItem("Hit sleep goal (scaled by progress)", "+0-15 points")
                            BonusPointItem("Log weight", "+10 points")
                            BonusPointItem("Log workouts (1 workout = +8, 2+ = +10, +2 for longer duration)", "+8-12 points")
                            BonusPointItem("Log multiple metrics (2-3 = +3, 4+ = +5)", "+3-5 points")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Wellness Bonuses
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Wellness Bonuses:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            BonusPointItem("Log mood entry", "+30 points")
                            BonusPointItem("Complete meditation session", "+25 points")
                            BonusPointItem("Write journal entry", "+20 points")
                            BonusPointItem("Complete breathing exercise", "+15 points")
                            BonusPointItem("Complete ALL Today's Focus tasks", "+15 points ⭐")
                            BonusPointItem("Log a win", "+10 points")
                            BonusPointItem("Interact with circles (post, like, comment)", "+10 points")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Habits Bonuses
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Habits Bonuses:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            BonusPointItem("Complete habits (scored by percentage completed)", "+0-100 points")
                            BonusPointItem("Complete all habits for the day (streak bonus)", "+5 points")
                        }
                    }
                }

                // What This Means
                CoachieCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CoachieCardDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "What Your Score Means",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )

                        val scoreDescription = when {
                            flowScore >= 80 -> "Excellent! You're crushing your wellness goals. Keep up the great work!"
                            flowScore >= 60 -> "Good job! You're on the right track. Focus on consistency to reach your peak."
                            flowScore >= 40 -> "You're making progress! Small improvements add up. Stay consistent."
                            else -> "Every journey starts with a single step. Focus on building healthy habits."
                        }

                        Text(
                            text = scoreDescription,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Black.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Tips to Improve
                CoachieCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CoachieCardDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Tips to Improve Your Score",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )

                        val tips = listOf(
                            "Complete your daily habits consistently",
                            "Log your meals and track calories",
                            "Stay hydrated and aim for 10,000 steps",
                            "Practice mindfulness and log your mood",
                            "Get quality sleep and track it regularly"
                        )

                        tips.forEach { tip ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color(0xFF10B981),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = tip,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.Black.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }
            }
        }
        } // Scaffold closes
    } // Box closes
}

@Composable
fun BonusPointItem(description: String, points: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "• $description",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Black.copy(alpha = 0.8f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = points,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF10B981)
        )
    }
}

@Composable
fun ScoreProgressGraph(
    scores: List<ScoreDataPoint>,
    modifier: Modifier = Modifier
) {
    if (scores.isEmpty()) return
    
    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    
    val padding = 40.dp
    val strokeWidth = 3.dp
    
    Canvas(modifier = modifier.padding(horizontal = padding, vertical = 16.dp)) {
        val width = size.width
        val height = size.height
        val maxScore = 100f
        val minScore = 0f
        val scoreRange = maxScore - minScore
        
        // Calculate points
        val points = scores.mapIndexed { index, dataPoint ->
            val x = (index.toFloat() / (scores.size - 1).coerceAtLeast(1)) * width
            val normalizedScore = ((dataPoint.score - minScore) / scoreRange).coerceIn(0f, 1f)
            val y = height - (normalizedScore * height)
            Offset(x, y)
        }
        
        // Draw grid lines
        val gridLines = 5
        for (i in 0..gridLines) {
            val y = (i.toFloat() / gridLines) * height
            val scoreValue = maxScore - (i.toFloat() / gridLines) * scoreRange
            
            // Draw horizontal line
            drawLine(
                color = Color.Black.copy(alpha = 0.1f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx()
            )
        }
        
        // Draw line graph
        if (points.size > 1) {
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
            }
            
            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )
        }
        
        // Draw points
        points.forEach { point ->
            drawCircle(
                color = primaryColor,
                radius = 4.dp.toPx(),
                center = point
            )
        }
        
        // Draw Y-axis labels
        for (i in 0..gridLines) {
            val y = (i.toFloat() / gridLines) * height
            val scoreValue = (maxScore - (i.toFloat() / gridLines) * scoreRange).toInt()
            
            val textLayoutResult = textMeasurer.measure(
                scoreValue.toString(),
                TextStyle(fontSize = 10.sp)
            )
            
            drawText(
                textLayoutResult,
                color = onSurfaceVariant,
                topLeft = Offset(-textLayoutResult.size.width - 8.dp.toPx(), y - textLayoutResult.size.height / 2)
            )
        }
        
        // Draw X-axis labels (dates)
        if (scores.size <= 7) {
            // Show all dates for 7 days
            scores.forEachIndexed { index, dataPoint ->
                val x = (index.toFloat() / (scores.size - 1).coerceAtLeast(1)) * width
                val date = LocalDate.parse(dataPoint.date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                val dayLabel = date.format(DateTimeFormatter.ofPattern("MM/dd"))
                
                val textLayoutResult = textMeasurer.measure(
                    dayLabel,
                    TextStyle(fontSize = 10.sp)
                )
                
                drawText(
                    textLayoutResult,
                    color = onSurfaceVariant,
                    topLeft = Offset(x - textLayoutResult.size.width / 2, height + 8.dp.toPx())
                )
            }
        } else {
            // Show first, middle, and last dates for longer ranges
            val indices = listOf(0, scores.size / 2, scores.size - 1)
            indices.forEach { index ->
                val x = (index.toFloat() / (scores.size - 1).coerceAtLeast(1)) * width
                val date = LocalDate.parse(scores[index].date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                val dayLabel = date.format(DateTimeFormatter.ofPattern("MM/dd"))
                
                val textLayoutResult = textMeasurer.measure(
                    dayLabel,
                    TextStyle(fontSize = 10.sp)
                )
                
                drawText(
                    textLayoutResult,
                    color = onSurfaceVariant,
                    topLeft = Offset(x - textLayoutResult.size.width / 2, height + 8.dp.toPx())
                )
            }
        }
    }
}
