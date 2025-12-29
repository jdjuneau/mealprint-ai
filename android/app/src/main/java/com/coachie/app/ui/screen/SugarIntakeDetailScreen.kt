package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.HealthLog
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.ui.theme.LocalGender
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SugarIntakeDetailScreen(
    userId: String,
    onNavigateBack: () -> Unit
) {
    var meals by remember { mutableStateOf<List<HealthLog.MealLog>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val repository = FirebaseRepository.getInstance()
    
    LaunchedEffect(userId) {
        isLoading = true
        try {
            val today = LocalDate.now()
            val dateStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val healthLogsResult = repository.getHealthLogs(userId, dateStr)
            val healthLogs = healthLogsResult.getOrNull() ?: emptyList()
            meals = healthLogs.filterIsInstance<HealthLog.MealLog>()
                .filter { (it.sugar ?: 0) > 0 || (it.addedSugar ?: 0) > 0 }
                .sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            android.util.Log.e("SugarIntakeDetailScreen", "Failed to load meals", e)
        } finally {
            isLoading = false
        }
    }
    
    val totalSugar = meals.sumOf { it.sugar ?: 0 }
    val totalAddedSugar = meals.sumOf { it.addedSugar ?: 0 }
    
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val gender = LocalGender.current
    val isMale = gender.lowercase() == "male"
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBackground)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            "Sugar Intake Breakdown",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack, 
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Summary Card
                item {
                    CoachieCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CoachieCardDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = CoachieCardDefaults.border(
                            color = Color(0xFFF9A825).copy(alpha = 0.36f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Today's Sugar Intake",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "${totalAddedSugar}g",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF9A825)
                                    )
                                    Text(
                                        text = "Added Sugar",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${totalSugar}g",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Total Sugar",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            LinearProgressIndicator(
                                progress = (totalAddedSugar / 50f).coerceIn(0f, 1.2f),
                                modifier = Modifier.fillMaxWidth(),
                                color = when {
                                    totalAddedSugar < 25 -> Color(0xFF2E7D32)
                                    totalAddedSugar <= 50 -> Color(0xFFF9A825)
                                    else -> Color(0xFFC62828)
                                },
                                trackColor = Color(0xFFF9A825).copy(alpha = 0.2f)
                            )
                            Text(
                                text = "Target: <25g added sugar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // Meals with Sugar
                item {
                    Text(
                        text = "Meals with Sugar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                if (meals.isEmpty()) {
                    item {
                        CoachieCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CoachieCardDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No sugar logged today",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                } else {
                    items(meals) { meal ->
                        CoachieCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CoachieCardDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = meal.foodName ?: "Meal",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        if ((meal.addedSugar ?: 0) > 0) {
                                            Text(
                                                text = "${meal.addedSugar}g added sugar",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color(0xFFF9A825)
                                            )
                                        }
                                        if ((meal.sugar ?: 0) > 0) {
                                            Text(
                                                text = "${meal.sugar}g total sugar",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (meal.calories > 0) {
                                        Text(
                                            text = "${meal.calories} cal",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

