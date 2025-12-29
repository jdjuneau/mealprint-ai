package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coachie.app.data.model.*
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.theme.rememberCoachieGradient
import kotlinx.coroutines.launch

data class HabitTemplate(
    val id: String,
    val title: String,
    val description: String,
    val category: HabitCategory,
    val frequency: HabitFrequency,
    val targetValue: Int = 1,
    val unit: String = "",
    val icon: String = "target"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitTemplatesScreen(
    onBack: () -> Unit,
    onCreateHabit: (HabitTemplate) -> Unit,
    userId: String = ""
) {
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val coroutineScope = rememberCoroutineScope()
    val habitRepository = remember { com.coachie.app.data.HabitRepository.getInstance() }
    var isCreating by remember { mutableStateOf<String?>(null) } // Track which template is being created
    
    // Popular habit templates
    val templates = remember {
        listOf(
            HabitTemplate(
                id = "water",
                title = "Drink 8 glasses of water",
                description = "Stay hydrated throughout the day",
                category = HabitCategory.HEALTH,
                frequency = HabitFrequency.DAILY,
                targetValue = 8,
                unit = "glasses"
            ),
            HabitTemplate(
                id = "meditation",
                title = "10-minute meditation",
                description = "Daily mindfulness practice",
                category = HabitCategory.MENTAL_HEALTH,
                frequency = HabitFrequency.DAILY,
                targetValue = 10,
                unit = "minutes"
            ),
            HabitTemplate(
                id = "walk",
                title = "10,000 steps",
                description = "Daily walking goal",
                category = HabitCategory.FITNESS,
                frequency = HabitFrequency.DAILY,
                targetValue = 10000,
                unit = "steps"
            ),
            HabitTemplate(
                id = "gratitude",
                title = "Gratitude journaling",
                description = "Write 3 things you're grateful for",
                category = HabitCategory.MENTAL_HEALTH,
                frequency = HabitFrequency.DAILY,
                targetValue = 3,
                unit = "items"
            ),
            HabitTemplate(
                id = "reading",
                title = "Read for 30 minutes",
                description = "Daily reading habit",
                category = HabitCategory.LEARNING,
                frequency = HabitFrequency.DAILY,
                targetValue = 30,
                unit = "minutes"
            ),
            HabitTemplate(
                id = "exercise",
                title = "30-minute workout",
                description = "Daily exercise routine",
                category = HabitCategory.FITNESS,
                frequency = HabitFrequency.DAILY,
                targetValue = 30,
                unit = "minutes"
            ),
            HabitTemplate(
                id = "sleep",
                title = "8 hours of sleep",
                description = "Consistent sleep schedule",
                category = HabitCategory.SLEEP,
                frequency = HabitFrequency.DAILY,
                targetValue = 8,
                unit = "hours"
            ),
            HabitTemplate(
                id = "protein",
                title = "Eat protein with every meal",
                description = "Nutrition goal",
                category = HabitCategory.NUTRITION,
                frequency = HabitFrequency.DAILY,
                targetValue = 3,
                unit = "meals"
            ),
            HabitTemplate(
                id = "stretching",
                title = "Morning stretching",
                description = "5-minute morning routine",
                category = HabitCategory.FITNESS,
                frequency = HabitFrequency.DAILY,
                targetValue = 5,
                unit = "minutes"
            ),
            HabitTemplate(
                id = "stretching_10",
                title = "10-minute stretching",
                description = "Full body flexibility routine",
                category = HabitCategory.FITNESS,
                frequency = HabitFrequency.DAILY,
                targetValue = 10,
                unit = "minutes"
            ),
            HabitTemplate(
                id = "stretching_15",
                title = "15-minute stretching",
                description = "Comprehensive flexibility session",
                category = HabitCategory.FITNESS,
                frequency = HabitFrequency.DAILY,
                targetValue = 15,
                unit = "minutes"
            ),
            HabitTemplate(
                id = "stretching_20",
                title = "20-minute stretching",
                description = "Deep stretching and mobility",
                category = HabitCategory.FITNESS,
                frequency = HabitFrequency.DAILY,
                targetValue = 20,
                unit = "minutes"
            ),
            HabitTemplate(
                id = "stretching_30",
                title = "30-minute stretching",
                description = "Extended flexibility and recovery session",
                category = HabitCategory.FITNESS,
                frequency = HabitFrequency.DAILY,
                targetValue = 30,
                unit = "minutes"
            ),
            HabitTemplate(
                id = "screen_time",
                title = "No phone before bed",
                description = "Better sleep hygiene",
                category = HabitCategory.SLEEP,
                frequency = HabitFrequency.DAILY,
                targetValue = 1,
                unit = "hour before bed"
            ),
            HabitTemplate(
                id = "breathing_exercise",
                title = "Daily breathing exercise",
                description = "3-5 minutes of guided breathing for stress relief",
                category = HabitCategory.MENTAL_HEALTH,
                frequency = HabitFrequency.DAILY,
                targetValue = 3,
                unit = "minutes"
            ),
            HabitTemplate(
                id = "morning_breathing",
                title = "Morning breathing routine",
                description = "Start your day with 3 minutes of box breathing",
                category = HabitCategory.MENTAL_HEALTH,
                frequency = HabitFrequency.DAILY,
                targetValue = 3,
                unit = "minutes"
            ),
            HabitTemplate(
                id = "social_media_break",
                title = "Social media break",
                description = "Take a 30-minute break from social media daily",
                category = HabitCategory.MENTAL_HEALTH,
                frequency = HabitFrequency.DAILY,
                targetValue = 30,
                unit = "minutes"
            ),
            HabitTemplate(
                id = "no_social_media_bed",
                title = "No social media before bed",
                description = "Avoid social media 1 hour before bedtime",
                category = HabitCategory.SLEEP,
                frequency = HabitFrequency.DAILY,
                targetValue = 1,
                unit = "hour before bed"
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Habit Templates", color = Color.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.Black
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.Black,
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Popular Habit Templates",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Quickly add common habits with pre-configured settings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                items(templates) { template ->
                    HabitTemplateCard(
                        template = template,
                        onClick = {
                            if (userId.isNotEmpty()) {
                                isCreating = template.id
                                coroutineScope.launch {
                                    try {
                                        val habit = com.coachie.app.data.model.Habit(
                                            userId = userId,
                                            title = template.title,
                                            description = template.description,
                                            category = template.category,
                                            frequency = template.frequency,
                                            targetValue = template.targetValue,
                                            unit = template.unit,
                                            isActive = true,
                                            createdAt = java.util.Date(),
                                            updatedAt = java.util.Date()
                                        )
                                        
                                        val result = habitRepository.createHabit(userId, habit)
                                        if (result.isSuccess) {
                                            android.util.Log.d("HabitTemplates", "Habit created successfully: ${habit.title} with ID: ${result.getOrNull()}")
                                            // Wait a moment for Firestore to propagate the change
                                            kotlinx.coroutines.delay(500)
                                            isCreating = null
                                            onCreateHabit(template)
                                        } else {
                                            android.util.Log.e("HabitTemplates", "Failed to create habit: ${result.exceptionOrNull()?.message}")
                                            isCreating = null
                                        }
                                    } catch (e: Exception) {
                                        isCreating = null
                                    }
                                }
                            } else {
                                onCreateHabit(template)
                            }
                        },
                        isLoading = isCreating == template.id
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitTemplateCard(
    template: HabitTemplate,
    onClick: () -> Unit,
    isLoading: Boolean = false
) {
    CoachieCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = template.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = template.category.name.replace("_", " "),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = template.frequency.name,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (template.targetValue > 0 && template.unit.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${template.targetValue} ${template.unit}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                IconButton(onClick = onClick) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Add habit",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

