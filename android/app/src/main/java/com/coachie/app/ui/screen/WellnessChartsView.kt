package com.coachie.app.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
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
import com.coachie.app.data.model.HealthLog
import com.coachie.app.ui.components.*
import com.coachie.app.ui.components.MeditationChart
import com.coachie.app.ui.components.MeditationDataPoint
import com.coachie.app.ui.components.JournalChart
import com.coachie.app.ui.components.JournalDataPoint
import androidx.compose.foundation.shape.RoundedCornerShape
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.viewmodel.HomeDashboardViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Wellness Charts View - Shows wellness-related charts (Mood, Meditation, etc.)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WellnessChartsView(
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

    val pagerState = rememberPagerState(pageCount = { 4 })
    val tabs = listOf(
        "Mood" to Icons.Filled.Mood,
        "Meditation" to Icons.Filled.SelfImprovement,
        "Sunshine" to Icons.Filled.WbSunny,
        "Breathing" to Icons.Filled.Air
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
                        icon = {
                            Icon(
                                icon, 
                                contentDescription = title,
                                tint = if (pagerState.currentPage == index) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant // Changed to onSurfaceVariant (dark) instead of primary (blue) for unselected tabs
                            )
                        },
                        text = {
                            Text(
                                title,
                                color = if (pagerState.currentPage == index) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant // Changed to onSurfaceVariant (dark) instead of primary (blue) for unselected tabs
                            )
                        }
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
                        0 -> MoodTrendTab(dashboardViewModel = dashboardViewModel, refreshTrigger = refreshTrigger)
                        1 -> MeditationTrendTab(dashboardViewModel = dashboardViewModel, refreshTrigger = refreshTrigger)
                        2 -> SunshineTrendTab(dashboardViewModel = dashboardViewModel, refreshTrigger = refreshTrigger)
                        3 -> BreathingTrendTab(dashboardViewModel = dashboardViewModel, refreshTrigger = refreshTrigger)
                    }
                }
            }
        }
    }
}

@Composable
fun MoodTrendTab(dashboardViewModel: HomeDashboardViewModel, refreshTrigger: String) {
    var moodData by remember { mutableStateOf<List<MoodDataPoint>>(emptyList()) }
    var showEnergy by remember { mutableStateOf(false) }
    var showStress by remember { mutableStateOf(false) }

    LaunchedEffect(dashboardViewModel.userId, refreshTrigger) {
        // Load last 30 days of mood data from HealthLogs
        val today = LocalDate.now()
        val dataPoints = mutableListOf<MoodDataPoint>()
        val repository = FirebaseRepository.getInstance()

        for (i in 0..29) {
            val date = today.minusDays(i.toLong())
            val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            // Get all health logs for this date, then filter for mood logs
            val allLogsResult = repository.getHealthLogs(dashboardViewModel.userId, dateStr)
            val allLogs = allLogsResult.getOrNull() ?: emptyList()
            val moodLogs = allLogs.filterIsInstance<HealthLog.MoodLog>()

            // Get the latest mood log for this date (in case multiple entries)
            moodLogs.maxByOrNull { it.timestamp }?.let { moodLog ->
                dataPoints.add(
                    MoodDataPoint(
                        date = date,
                        moodLevel = moodLog.level,
                        energyLevel = moodLog.energyLevel,
                        stressLevel = moodLog.stressLevel
                    )
                )
            }
        }

        moodData = dataPoints.sortedBy { it.date }
        
        // Enable overlays if data is available
        showEnergy = dataPoints.any { it.energyLevel != null }
        showStress = dataPoints.any { it.stressLevel != null }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MoodChart(
            moodData = moodData,
            showEnergy = showEnergy,
            showStress = showStress,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Summary statistics
        if (moodData.isNotEmpty()) {
            val validMoods = moodData.filter { it.moodLevel != null }
            if (validMoods.isNotEmpty()) {
                val avgMood = validMoods.mapNotNull { it.moodLevel }.average()
                val recentMoods = validMoods.takeLast(7).mapNotNull { it.moodLevel }
                val weeklyAvg = if (recentMoods.isNotEmpty()) recentMoods.average() else 0.0
                
                CoachieCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CoachieCardDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "30-Day Average",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format("%.1f", avgMood),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "7-Day Average",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format("%.1f", weeklyAvg),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Total Entries",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${validMoods.size}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MeditationTrendTab(dashboardViewModel: HomeDashboardViewModel, refreshTrigger: String) {
    var meditationData by remember { mutableStateOf<List<MeditationDataPoint>>(emptyList()) }

    LaunchedEffect(dashboardViewModel.userId, refreshTrigger) {
        // Load last 30 days of meditation data from HealthLogs
        val today = LocalDate.now()
        val dataPointsMap = mutableMapOf<LocalDate, MutableList<HealthLog.MeditationLog>>()
        val repository = FirebaseRepository.getInstance()

        for (i in 0..29) {
            val date = today.minusDays(i.toLong())
            val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            // Get meditation logs for this date
            val logsResult = repository.getHealthLogsByType(
                dashboardViewModel.userId,
                dateStr,
                HealthLog.MeditationLog.TYPE
            )

            val meditationLogs = logsResult.getOrNull()?.filterIsInstance<HealthLog.MeditationLog>()?.filter { it.completed } ?: emptyList()
            
            if (meditationLogs.isNotEmpty()) {
                dataPointsMap[date] = meditationLogs.toMutableList()
            }
        }

        // Convert to data points
        val dataPoints = dataPointsMap.map { (date, logs) ->
            val totalDuration = logs.sumOf { it.durationMinutes }
            val moodImprovement = logs.firstOrNull()?.let { log ->
                if (log.moodBefore != null && log.moodAfter != null) {
                    log.moodAfter - log.moodBefore
                } else null
            }
            
            MeditationDataPoint(
                date = date,
                durationMinutes = totalDuration,
                count = logs.size,
                moodImprovement = moodImprovement
            )
        }

        meditationData = dataPoints.sortedBy { it.date }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MeditationChart(
            meditationData = meditationData,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Summary statistics
        if (meditationData.isNotEmpty()) {
            val validSessions = meditationData.filter { it.durationMinutes != null }
            if (validSessions.isNotEmpty()) {
                val totalSessions = meditationData.sumOf { it.count }
                val totalMinutes = validSessions.sumOf { it.durationMinutes ?: 0 }
                val avgDuration = validSessions.mapNotNull { it.durationMinutes }.average()
                
                CoachieCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CoachieCardDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Total Sessions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$totalSessions",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Total Minutes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$totalMinutes",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Avg Duration",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format("%.0f min", avgDuration),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun JournalTrendTab(dashboardViewModel: HomeDashboardViewModel, refreshTrigger: String) {
    var journalData by remember { mutableStateOf<List<JournalDataPoint>>(emptyList()) }

    LaunchedEffect(dashboardViewModel.userId, refreshTrigger) {
        // Load last 30 days of journal data from HealthLogs
        val today = LocalDate.now()
        val dataPointsMap = mutableMapOf<LocalDate, MutableList<HealthLog.JournalEntry>>()
        val repository = FirebaseRepository.getInstance()

        for (i in 0..29) {
            val date = today.minusDays(i.toLong())
            val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            // Get journal entries for this date
            val logsResult = repository.getHealthLogsByType(
                dashboardViewModel.userId,
                dateStr,
                HealthLog.JournalEntry.TYPE
            )

            val journalEntries = logsResult.getOrNull()?.filterIsInstance<HealthLog.JournalEntry>() ?: emptyList()
            
            if (journalEntries.isNotEmpty()) {
                dataPointsMap[date] = journalEntries.toMutableList()
            }
        }

        // Convert to data points
        val dataPoints = dataPointsMap.map { (date, entries) ->
            val totalWordCount = entries.sumOf { it.wordCount }
            val completedCount = entries.count { it.isCompleted }
            
            JournalDataPoint(
                date = date,
                wordCount = totalWordCount,
                isCompleted = completedCount > 0,
                entryCount = entries.size
            )
        }

        journalData = dataPoints.sortedBy { it.date }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        JournalChart(
            journalData = journalData,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Summary statistics
        if (journalData.isNotEmpty()) {
            val validEntries = journalData.filter { it.wordCount > 0 }
            if (validEntries.isNotEmpty()) {
                val totalEntries = journalData.sumOf { it.entryCount }
                val totalWords = validEntries.sumOf { it.wordCount }
                val completedEntries = journalData.count { it.isCompleted }
                
                CoachieCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CoachieCardDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Total Entries",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$totalEntries",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Total Words",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (totalWords >= 1000) "${totalWords / 1000}k" else "$totalWords",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Completed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$completedEntries",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SunshineTrendTab(dashboardViewModel: HomeDashboardViewModel, refreshTrigger: String) {
    var sunshineData by remember { mutableStateOf<List<Pair<LocalDate, Double>>>(emptyList()) }

    LaunchedEffect(dashboardViewModel.userId, refreshTrigger) {
        // Load last 30 days of sunshine data from HealthLogs
        val today = LocalDate.now()
        val dataPoints = mutableListOf<Pair<LocalDate, Double>>()
        val repository = FirebaseRepository.getInstance()

        for (i in 0..29) {
            val date = today.minusDays(i.toLong())
            val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            // Get all health logs for this date, then filter for sunshine logs
            val allLogsResult = repository.getHealthLogs(dashboardViewModel.userId, dateStr)
            val allLogs = allLogsResult.getOrNull() ?: emptyList()
            
            // Filter for sunshine logs
            val sunshineLogs = allLogs.filterIsInstance<HealthLog.SunshineLog>()
            val totalVitaminD = sunshineLogs.sumOf { it.vitaminDIu }
            
            android.util.Log.d("WellnessCharts", "Date: $dateStr, All logs: ${allLogs.size}, Sunshine logs: ${sunshineLogs.size}, Total Vitamin D: $totalVitaminD")
            
            if (sunshineLogs.isNotEmpty() || totalVitaminD > 0) {
                dataPoints.add(Pair(date, totalVitaminD))
            }
        }

        sunshineData = dataPoints.sortedBy { it.first }
        android.util.Log.d("WellnessCharts", "Sunshine data points: ${sunshineData.size}")
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (sunshineData.isEmpty()) {
            CoachieCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CoachieCardDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = "No sunshine data available\nLog sunshine exposure to see your vitamin D progress!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            // Simple bar chart representation
            val maxVitaminD = sunshineData.maxOfOrNull { it.second } ?: 1000.0
            
            CoachieCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CoachieCardDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Sunshine & Vitamin D (Last 30 Days)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    sunshineData.takeLast(7).forEach { (date, vitaminD) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = date.format(DateTimeFormatter.ofPattern("MMM dd")),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width((vitaminD / maxVitaminD * 200).dp.coerceAtLeast(4.dp))
                                        .height(20.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(4.dp)
                                        )
                                )
                                Text(
                                    text = "${vitaminD.toInt()} IU",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    
                    val totalVitaminD = sunshineData.sumOf { it.second }
                    val avgVitaminD = totalVitaminD / sunshineData.size
                    Text(
                        text = "Total: ${totalVitaminD.toInt()} IU | Avg: ${avgVitaminD.toInt()} IU/day",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BreathingTrendTab(dashboardViewModel: HomeDashboardViewModel, refreshTrigger: String) {
    var breathingData by remember { mutableStateOf<List<Pair<LocalDate, Int>>>(emptyList()) }

    LaunchedEffect(dashboardViewModel.userId, refreshTrigger) {
        // Load last 30 days of breathing exercise data from HealthLogs
        val today = LocalDate.now()
        val dataPoints = mutableListOf<Pair<LocalDate, Int>>()
        val repository = FirebaseRepository.getInstance()

        for (i in 0..29) {
            val date = today.minusDays(i.toLong())
            val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            // Get all health logs for this date, then filter for breathing exercise logs
            val allLogsResult = repository.getHealthLogs(dashboardViewModel.userId, dateStr)
            val allLogs = allLogsResult.getOrNull() ?: emptyList()
            
            // Filter for breathing exercise logs
            val breathingLogs = allLogs.filterIsInstance<HealthLog.BreathingExerciseLog>()
            val totalMinutes = breathingLogs.sumOf { it.durationMinutes }
            
            android.util.Log.d("WellnessCharts", "Date: $dateStr, All logs: ${allLogs.size}, Breathing logs: ${breathingLogs.size}, Total minutes: $totalMinutes")
            
            if (breathingLogs.isNotEmpty() || totalMinutes > 0) {
                dataPoints.add(Pair(date, totalMinutes))
            }
        }

        breathingData = dataPoints.sortedBy { it.first }
        android.util.Log.d("WellnessCharts", "Breathing data points: ${breathingData.size}")
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (breathingData.isEmpty()) {
            CoachieCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CoachieCardDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = "No breathing exercise data available\nComplete breathing exercises to see your progress!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            // Simple bar chart representation
            val maxMinutes = breathingData.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 5
            
            CoachieCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CoachieCardDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Breathing Exercises (Last 30 Days)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    breathingData.takeLast(7).forEach { (date, minutes) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = date.format(DateTimeFormatter.ofPattern("MMM dd")),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width((minutes.toFloat() / maxMinutes * 200).dp.coerceAtLeast(4.dp))
                                        .height(20.dp)
                                        .background(
                                            MaterialTheme.colorScheme.tertiary,
                                            RoundedCornerShape(4.dp)
                                        )
                                )
                                Text(
                                    text = "${minutes} min",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    
                    val totalMinutes = breathingData.sumOf { it.second }
                    val avgMinutes = if (breathingData.isNotEmpty()) totalMinutes.toDouble() / breathingData.size else 0.0
                    Text(
                        text = "Total: ${totalMinutes} min | Avg: ${avgMinutes.toInt()} min/day",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}
