package com.coachie.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import com.mealprint.ai.data.model.HealthLog
import com.mealprint.ai.utils.DebugLogger
import com.mealprint.ai.data.model.Streak
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import com.mealprint.ai.ui.theme.Primary40
import com.mealprint.ai.ui.theme.Primary10
import com.mealprint.ai.ui.theme.Secondary40
import com.mealprint.ai.ui.theme.Tertiary40
import com.mealprint.ai.ui.theme.Accent40
import com.mealprint.ai.ui.theme.MaleAccent40
import com.mealprint.ai.ui.theme.MaleSecondary40
import com.mealprint.ai.ui.theme.LocalGender

data class DailyMission(
    val title: String,
    val subtitle: String,
    val progress: Float,
    val accentColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val progressLabel: String,
    val ctaLabel: String,
    val onCtaClick: () -> Unit,
    val secondaryAction: (() -> Unit)? = null,
    val secondaryLabel: String? = null
)

@Composable
fun TodayMissionCard(
    mission: DailyMission,
    modifier: Modifier = Modifier
) {
    val safeProgress = mission.progress.coerceIn(0f, 1f)
    val cardBackground = remember(mission.accentColor) {
        Brush.linearGradient(
            colors = listOf(
                mission.accentColor.copy(alpha = 0.14f),
                mission.accentColor.copy(alpha = 0.05f)
            )
        )
    }

    CoachieCard(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CoachieCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.16f)
        ),
        border = BorderStroke(1.dp, mission.accentColor.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBackground)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = mission.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White // White text for better readability on colored gradient backgrounds
                )
                Text(
                    text = mission.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f) // White text with slight transparency for better readability
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = mission.onCtaClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = mission.accentColor,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(mission.ctaLabel)
                    }

                    if (mission.secondaryAction != null && mission.secondaryLabel != null) {
                        TextButton(onClick = mission.secondaryAction) {
                            Text(mission.secondaryLabel)
                        }
                    }
                }
            }

            Box(
                modifier = Modifier.size(96.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = safeProgress,
                    strokeWidth = 8.dp,
                    strokeCap = StrokeCap.Round,
                    color = mission.accentColor,
                    trackColor = mission.accentColor.copy(alpha = 0.18f),
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(mission.accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = mission.icon,
                        contentDescription = null,
                        tint = mission.accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = mission.progressLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                )
            }
        }
    }
}

/**
 * Today's Log Card - Shows meals, workouts, sleep, water
 */
@Composable
fun TodaysLogCard(
    meals: List<HealthLog.MealLog>,
    workouts: List<HealthLog.WorkoutLog>,
    sleepLogs: List<HealthLog.SleepLog>,
    waterMl: Int?,
    weightLogs: List<HealthLog.WeightLog> = emptyList(),
    useImperial: Boolean = true,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    CoachieCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        border = CoachieCardDefaults.border(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today's Log",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }

            // Meals
            LogItem(
                icon = Icons.Filled.Restaurant,
                label = "Meals",
                value = "${meals.size} logged",
                color = MaterialTheme.colorScheme.primary
            )

            // Workouts
            LogItem(
                icon = Icons.Filled.FitnessCenter,
                label = "Workouts",
                value = "${workouts.size} logged",
                color = MaterialTheme.colorScheme.primary
            )

            // Sleep - filter out invalid durations (>24 hours is impossible) and take most recent valid one
            val validSleepLogs = sleepLogs.filter { it.durationHours in 0.0..24.0 }
            val sleepHours = validSleepLogs.maxByOrNull { it.timestamp }?.durationHours
            LogItem(
                icon = Icons.Filled.Bedtime,
                label = "Sleep",
                value = sleepHours?.let { String.format("%.1f hrs", it) } ?: "Not logged",
                color = MaterialTheme.colorScheme.primary
            )

            // Water - ALWAYS display in GLASSES (8 oz per glass), rounded to nearest whole number
            LogItem(
                icon = Icons.Filled.LocalDrink,
                label = "Water",
                value = waterMl?.let {
                    // Convert ml to glasses: ml -> oz -> glasses (8 oz per glass)
                    val oz = it * 0.033814 // Convert ml to fl oz
                    val glasses = (oz / 8.0).roundToInt() // Convert oz to glasses and round to nearest whole number
                    val goalOz = 2000 * 0.033814 // Default goal: 2000ml = ~68oz
                    val goalGlasses = (goalOz / 8.0).roundToInt() // Goal in glasses
                    "$glasses of $goalGlasses glasses"
                } ?: "Not logged",
                color = MaterialTheme.colorScheme.primary
            )

            // Weight
            if (weightLogs.isNotEmpty()) {
                val latestWeight = weightLogs.maxByOrNull { it.timestamp }
                LogItem(
                    icon = Icons.Filled.MonitorWeight,
                    label = "Weight",
                    value = latestWeight?.let {
                        val weightInLbs = if (it.unit == "lbs") it.weight else it.weight * 2.205
                        if (useImperial) {
                            String.format("%.1f lbs", weightInLbs)
                        } else {
                            val weightInKg = weightInLbs / 2.205
                            String.format("%.1f kg", weightInKg)
                        }
                    } ?: "Not logged",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun LogItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
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
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * AI Insight Card
 */
@Composable
fun AIInsightCard(
    insight: String?,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    CoachieCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CoachieCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)
        ),
        border = CoachieCardDefaults.border(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI Insight",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }

            if (insight != null) {
                Text(
                    text = insight,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "💬 Tap to chat with AI Coachie for personalized insights!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun MicronutrientQuickCard(
    completed: Int,
    total: Int,
    supplements: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CoachieCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CoachieCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)
        ),
        border = CoachieCardDefaults.border(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Vitamins & Minerals",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (total > 0) {
                        Text(
                            text = "$completed of $total goals hit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Supplements logged: $supplements",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            LinearProgressIndicator(
                progress = if (total <= 0) 0f else (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )

            AssistChip(
                onClick = onClick,
                label = { Text("Open Tracker") },
                leadingIcon = {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    labelColor = MaterialTheme.colorScheme.primary,
                    leadingIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
fun SugarIntakeCard(
    totalSugar: Int,
    totalAddedSugar: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    // Color coding for added sugar (<25g green, 25-50g yellow, >50g red)
    val (addedTargetRange, addedStatusColor, addedEmoji) = when {
        totalAddedSugar < 25 -> Triple(
            "<25g",
            Color(0xFF2E7D32), // Green
            "🟢"
        )
        totalAddedSugar <= 50 -> Triple(
            "25-50g",
            Color(0xFFF9A825), // Yellow
            "🟡"
        )
        else -> Triple(
            ">50g",
            Color(0xFFC62828), // Red
            "🔴"
        )
    }

    // Color coding for total sugar (<50g green, 50-100g yellow, >100g red)
    val (totalTargetRange, totalStatusColor, totalEmoji) = when {
        totalSugar < 50 -> Triple(
            "<50g",
            Color(0xFF2E7D32), // Green
            "🟢"
        )
        totalSugar <= 100 -> Triple(
            "50-100g",
            Color(0xFFF9A825), // Yellow
            "🟡"
        )
        else -> Triple(
            ">100g",
            Color(0xFFC62828), // Red
            "🔴"
        )
    }

    // Use the more severe status color for card background
    val cardStatusColor = if (totalAddedSugar > 50 || totalSugar > 100) {
        Color(0xFFC62828)
    } else if (totalAddedSugar > 25 || totalSugar > 50) {
        Color(0xFFF9A825)
    } else {
        Color(0xFF2E7D32)
    }

    val addedProgress = (totalAddedSugar / 50f).coerceIn(0f, 1.2f)
    val totalProgress = (totalSugar / 100f).coerceIn(0f, 1.2f)

    CoachieCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CoachieCardDefaults.colors(
            containerColor = cardStatusColor.copy(alpha = 0.12f)
        ),
        border = BorderStroke(1.dp, cardStatusColor.copy(alpha = 0.36f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "Sugar Intake",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Added Sugar Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Traffic light circle for added sugar
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = addedStatusColor,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = addedEmoji,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Added Sugar",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${totalAddedSugar}g",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Progress indicator for added sugar
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        color = addedStatusColor.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Target: $addedTargetRange",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    LinearProgressIndicator(
                        progress = addedProgress,
                        modifier = Modifier.width(80.dp).height(6.dp),
                        color = addedStatusColor,
                        trackColor = addedStatusColor.copy(alpha = 0.2f)
                    )
                }
            }

            // Total Sugar Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Traffic light circle for total sugar
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = totalStatusColor,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = totalEmoji,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Total Sugar",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${totalSugar}g",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Progress indicator for total sugar
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        color = totalStatusColor.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Target: $totalTargetRange",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    LinearProgressIndicator(
                        progress = totalProgress,
                        modifier = Modifier.width(80.dp).height(6.dp),
                        color = totalStatusColor,
                        trackColor = totalStatusColor.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}

/**
 * User Goals Card - Shows user's saved fitness goals
 */
@Composable
fun UserGoalsCard(
    userGoals: Map<String, Any>?,
    modifier: Modifier = Modifier
) {
    CoachieCard(
        modifier = modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)
        ),
        border = CoachieCardDefaults.border(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Goals",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(Icons.Filled.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }

            if (userGoals != null && userGoals.isNotEmpty()) {
                // Display saved goals
                val selectedGoal = userGoals["selectedGoal"] as? String
                val fitnessLevel = userGoals["fitnessLevel"] as? String
                val weeklyWorkouts = userGoals["weeklyWorkouts"] as? Number
                val dailySteps = userGoals["dailySteps"] as? Number
                val gender = userGoals["gender"] as? String

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedGoal != null) {
                        GoalItem("Main Goal", selectedGoal)
                    }
                    if (fitnessLevel != null) {
                        GoalItem("Fitness Level", fitnessLevel)
                    }
                    if (weeklyWorkouts != null) {
                        GoalItem("Weekly Workouts", "${weeklyWorkouts} per week")
                    }
                    if (dailySteps != null) {
                        GoalItem("Daily Steps", "${dailySteps}K steps")
                    }
                    if (gender != null) {
                        GoalItem("Gender", gender.replaceFirstChar { it.uppercase() })
                    }
                }
            } else {
                // No goals set yet
                Text(
                    text = "Set your fitness goals to get personalized recommendations!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun GoalItem(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Calories Card - Matches streak badge format
 */
@Composable
fun ProgressRingCard(
    caloriesConsumed: Int,
    caloriesBurned: Int,
    dailyGoal: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    CoachieCard(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        border = CoachieCardDefaults.border(),
        colors = CoachieCardDefaults.colors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        ),
        elevation = CoachieCardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Restaurant,
                    contentDescription = null,
                    modifier = Modifier
                        .width(48.dp)
                        .height(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Calculate calories remaining to goal (more intuitive than net calories)
            // Net calories = consumed - burned, but Google Fit includes BMR which makes it misleading early in the day
            // Instead, show calories remaining to reach daily goal
            val caloriesRemaining = dailyGoal - caloriesConsumed
            Text(
                text = caloriesRemaining.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = if (caloriesRemaining >= 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
            )

            Text(
                text = "Calories Remaining",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Add explanation text
            Text(
                text = "To reach your daily goal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$caloriesConsumed",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface // Changed from primary (orange) to onSurface for readability
                    )
                    Text(
                        text = "Consumed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "from meals",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$dailyGoal",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface // Changed from tertiary to onSurface for readability
                    )
                    Text(
                        text = "Daily Goal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "target",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$caloriesBurned",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFF4444) // Red color for "burning" calories
                    )
                    Text(
                        text = "Burned",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "from workouts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

/**
 * Streak Badge Card
 */
@Composable
fun StreakBadgeCard(
    streak: Streak?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    hasActualLogData: Boolean = false
) {
    CoachieCard(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        border = CoachieCardDefaults.border(),
        colors = CoachieCardDefaults.colors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        ),
        elevation = CoachieCardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    modifier = Modifier
                        .width(48.dp)
                        .height(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Show streak if:
            // 1. Streak exists and has valid data
            // 2. User has logged today (hasActualLogData) OR lastLogDate is today
            // 3. No bad data (currentStreak > 0 but totalLogs == 0 or lastLogDate blank)
            val today = java.time.LocalDate.now()
            val lastLogDate = try {
                if (streak?.lastLogDate?.isNotEmpty() == true) {
                    java.time.LocalDate.parse(streak.lastLogDate, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
            
            // Check for bad data first
            val hasBadData = streak != null && (
                (streak.currentStreak > 0 && streak.totalLogs == 0) ||
                (streak.currentStreak > 0 && streak.lastLogDate.isBlank())
            )
            
            // If user has logged today, trust the streak even if lastLogDate isn't updated yet
            // The streak will be updated when updateStreakAfterLog is called
            val hasActivityToday = hasActualLogData || (lastLogDate == today)
            
            val displayStreak = when {
                streak == null -> 0
                hasBadData -> 0
                streak.currentStreak == 0 -> 0
                !hasActivityToday -> 0 // No activity today and lastLogDate is not today
                else -> streak.currentStreak // Show the streak if there's activity today
            }
            
            Text(
                text = displayStreak.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Day Streak",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (displayStreak > 0 && streak?.longestStreak != null && streak.longestStreak > 0) {
                Text(
                    text = "Best: ${streak.longestStreak} days",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Quick Log Buttons Card
 */
@Composable
fun QuickLogButtonsCard(
    onLogMeal: () -> Unit,
    onLogSupplement: () -> Unit,
    onLogWorkout: () -> Unit,
    onLogSleep: () -> Unit,
    onLogWater: () -> Unit,
    onLogSunshine: () -> Unit,
    onMealIdea: () -> Unit = {},
    onLogWeight: () -> Unit = {},
    onVoiceLogging: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    CoachieCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Quick Log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // Row 1: Core daily activities (Meal, Supplement, Workout)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickLogButton(
                    icon = Icons.Filled.Restaurant,
                    label = "Meal",
                    onClick = onLogMeal,
                    modifier = Modifier.weight(1f)
                )
                QuickLogButton(
                    icon = Icons.Filled.Medication,
                    label = "Supplement",
                    onClick = onLogSupplement,
                    modifier = Modifier.weight(1f)
                )
                QuickLogButton(
                    icon = Icons.Filled.FitnessCenter,
                    label = "Workout",
                    onClick = onLogWorkout,
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 2: Health & wellness tracking (Water, Sunshine, Sleep)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickLogButton(
                    icon = Icons.Filled.LocalDrink,
                    label = "Water",
                    onClick = onLogWater,
                    modifier = Modifier.weight(1f)
                )
                QuickLogButton(
                    icon = Icons.Filled.WbSunny,
                    label = "Sunshine",
                    onClick = onLogSunshine,
                    modifier = Modifier.weight(1f)
                )
                QuickLogButton(
                    icon = Icons.Filled.Bedtime,
                    label = "Sleep",
                    onClick = onLogSleep,
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 3: Additional tools (Meal Idea, Weight) - maintained grid alignment
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickLogButton(
                    icon = Icons.Filled.Lightbulb,
                    label = "Meal Idea",
                    onClick = onMealIdea,
                    modifier = Modifier.weight(1f)
                )
                QuickLogButton(
                    icon = Icons.Filled.MonitorWeight,
                    label = "Weight",
                    onClick = onLogWeight,
                    modifier = Modifier.weight(1f)
                )
                // Invisible spacer maintains consistent 3-column grid alignment
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Navigation Tile Card - Large tile for navigating to major sections
 */
@Composable
fun NavigationTileCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    backgroundColor: Color,
    onClick: () -> Unit,
    score: Int? = null,
    modifier: Modifier = Modifier
) {
    // Use white text for better readability on colored gradient backgrounds
    val titleColor = Color.White
    val descriptionColor = Color.White.copy(alpha = 0.9f)
    val arrowColor = Color.White.copy(alpha = 0.7f)
    
    CoachieCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        applyDefaultBorder = false,
        colors = CoachieCardDefaults.colors(
            containerColor = backgroundColor.copy(alpha = 0.2f)
        ),
        border = BorderStroke(1.dp, backgroundColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        color = backgroundColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = titleColor
                    )
                    
                    // Score display
                    score?.let {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = backgroundColor.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "$it",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White // White text for better readability on colored background
                            )
                        }
                    }
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = descriptionColor
                )
            }
            
            // Arrow
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = arrowColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun QuickLogButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var lastClickTime by remember { mutableStateOf(0L) }

    OutlinedButton(
        onClick = {
            DebugLogger.logDebug("QuickLogButton", "Button clicked: $label")
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime > 500) { // 500ms debounce
                lastClickTime = currentTime
                DebugLogger.logDebug("QuickLogButton", "Calling onClick for: $label")
                onClick()
            } else {
                DebugLogger.logDebug("QuickLogButton", "Click ignored (debounce): $label")
            }
        },
        modifier = modifier
            .height(64.dp) // Slightly taller for better touch targets
            .fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(8.dp), // Consistent padding
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.16f),
            contentColor = MaterialTheme.colorScheme.primary
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp), // Slightly more space
            modifier = Modifier.fillMaxHeight()
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp), // Slightly larger icons
                tint = MaterialTheme.colorScheme.primary // Consistent icon color
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium, // Slightly larger text
                fontWeight = FontWeight.Medium, // Medium weight for better readability
                textAlign = TextAlign.Center,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// Mood Tracker Tile for Dashboard
@Composable
fun MoodTrackerTile(
    userProfile: com.coachie.app.data.model.UserProfile?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    CoachieCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Mood emoji based on profile or default
            val moodEmoji = when {
                userProfile?.currentCyclePhase?.displayName?.contains("Menstrual") == true -> "😌" // Calm during period
                userProfile?.currentCyclePhase?.displayName?.contains("Follicular") == true -> "⚡" // Energetic
                userProfile?.currentCyclePhase?.displayName?.contains("Ovulation") == true -> "💪" // Strong
                userProfile?.currentCyclePhase?.displayName?.contains("Luteal") == true -> "🧘‍♀️" // Restorative
                else -> "😊"
            }

            Text(
                text = moodEmoji,
                style = MaterialTheme.typography.headlineLarge
            )

            Text(
                text = "Mood Tracker",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Track your daily mood and emotions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Cycle Tracker Tile for Dashboard
@Composable
fun CycleTrackerTile(
    userProfile: com.coachie.app.data.model.UserProfile?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shouldShow = userProfile?.shouldShowMenstrualTracker == true

    if (!shouldShow) {
        // Don't show the tile if user shouldn't see menstrual tracking
        Box(modifier = modifier) {} // Empty space
        return
    }

    CoachieCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Cycle phase emoji
            val cycleEmoji = when (userProfile?.currentCyclePhase?.displayName) {
                "Menstrual" -> "🌸"
                "Follicular" -> "🌱"
                "Ovulation" -> "🌟"
                "Luteal" -> "🌙"
                else -> "🔄"
            }

            Text(
                text = cycleEmoji,
                style = MaterialTheme.typography.headlineLarge
            )

            Text(
                text = "Cycle Tracker",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Show current phase if available
            userProfile?.currentCyclePhase?.let { phase ->
                if (phase != com.coachie.app.data.model.CyclePhase.UNKNOWN) {
                    Text(
                        text = phase.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = "Track your menstrual cycle",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Daily Score Card - Shows overall daily score and category breakdown
 */
@Composable
fun DailyScoreCard(
    dailyScore: Int,
    healthScore: Int,
    wellnessScore: Int,
    habitsScore: Int,
    modifier: Modifier = Modifier
) {
    CoachieCard(
        modifier = modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daily Score",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Large score display
                Box(
                    modifier = Modifier
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Primary40.copy(alpha = 0.3f),
                                    Secondary40.copy(alpha = 0.3f)
                                )
                            ),
                            shape = CircleShape
                        )
                        .size(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "$dailyScore",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White // White text for better readability on colored gradient background
                        )
                        Text(
                            text = "/100",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Category breakdown
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Health Score
                ScoreBreakdownRow(
                    label = "Health",
                    score = healthScore,
                    weight = "50%",
                    color = Secondary40
                )
                
                // Wellness Score
                ScoreBreakdownRow(
                    label = "Wellness",
                    score = wellnessScore,
                    weight = "30%",
                    color = Secondary40
                )
                
                // Habits Score
                ScoreBreakdownRow(
                    label = "Habits",
                    score = habitsScore,
                    weight = "20%",
                    color = Primary40
                )
            }
        }
    }
}

@Composable
private fun ScoreBreakdownRow(
    label: String,
    score: Int,
    weight: String,
    color: Color
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
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "($weight)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Progress bar
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((score / 100f).coerceIn(0f, 1f))
                        .background(color, RoundedCornerShape(3.dp))
                )
            }
            
            Text(
                text = "$score",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = color,
                modifier = Modifier.width(30.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
fun CirclePulseCard(
    circle: com.coachie.app.data.model.Circle,
    pulseScale: Float,
    onNavigateToCircle: (String) -> Unit,
    modifier: Modifier = Modifier,
    totalCircles: Int = 1,
    hasNotifications: Boolean = false
) {
    CoachieCard(
        modifier = modifier.fillMaxWidth(),
        onClick = { 
            // Always navigate - callback will navigate to community screen
            // This ensures the card is always clickable, even for new accounts with no circles
            onNavigateToCircle(if (circle.id.isNotBlank()) circle.id else "")
        },
        applyDefaultBorder = false,
        colors = CoachieCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp), // Match WinOfTheDayCard padding for equal sizing
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My Circles",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Notification indicator badge
                if (hasNotifications) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = MaterialTheme.colorScheme.error,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasNotifications) {
                    // Show notification indicator instead of "Join a circle"
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = "New notifications",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "New activity",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Text(
                        text = if (circle.name.lowercase().trim() == "test" || circle.name.isBlank()) "Join a circle" else circle.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability
                    )
                }
                
                Icon(
                    imageVector = Icons.Filled.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Text(
                text = "Tap for more details",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability
            )
        }
    }
}

/**
 * Energy Score Card - Displays the Coachie Flow Score
 */
@Composable
fun EnergyScoreCard(
    score: Int,
    hrv: Double?,
    sleepHours: Double?,
    color: Color,
    scale: Float,
    isShowingYesterdayScore: Boolean = false,
    scoreLabel: String = "Building today's score...",
    onShare: () -> Unit,
    onClick: () -> Unit = {}
) {
    CoachieCard(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable { onClick() },
        colors = CoachieCardDefaults.colors(
            containerColor = color.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Coachie Flow Score",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f)
            )

            if (isShowingYesterdayScore) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = scoreLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(if (isShowingYesterdayScore) 8.dp else 16.dp))

            Text(
                text = "$score",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 72.sp
            )

            if (hrv != null || sleepHours != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (hrv != null) {
                        Text(
                            text = "HRV: ${hrv.toInt()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    if (sleepHours != null) {
                        Text(
                            text = "Sleep: ${sleepHours.toInt()}h",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "Share",
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * Win of the Day Card - Displays the most recent circle win
 */
@Composable
fun WinOfTheDayCard(
    win: String,
    onShare: () -> Unit,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    CoachieCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CoachieCardDefaults.colors(
            containerColor = Color(0xFF10B981).copy(alpha = 0.2f)
        )
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
                    text = "Win of the Day",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = win,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Black.copy(alpha = 0.9f),
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap for more details",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black.copy(alpha = 0.7f)
                )
            }

            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = "Win of the Day",
                tint = Color(0xFFFFD700), // Gold color
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/**
 * Morning Brief Insight Card - Displays AI-generated morning brief
 */
@Composable
fun MorningBriefInsightCard(
    greeting: String,
    insight: String,
    isSpeaking: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onClick: () -> Unit = {},
    subscriptionTier: com.coachie.app.data.model.SubscriptionTier? = null,
    onUpgrade: () -> Unit = {}
) {
    // Show locked card for free users
    if (subscriptionTier == com.coachie.app.data.model.SubscriptionTier.FREE) {
        com.coachie.app.ui.components.LockedFeatureCard(
            title = "AI Coach $greeting Brief",
            description = "Get personalized daily insights from your AI coach",
            featureName = "Morning Brief",
            proBenefits = listOf(
                "Unlimited personalized morning briefs",
                "AI-powered daily insights",
                "Personalized recommendations"
            ),
            onClick = { onUpgrade() }
        )
    } else {
        CoachieCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            colors = CoachieCardDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.1f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AI Coach $greeting Brief",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

            Spacer(modifier = Modifier.height(8.dp))

            if (insight.isNotBlank()) {
                Text(
                    text = insight,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    lineHeight = 24.sp
                )
            } else {
                Text(
                    text = "💬 Tap to chat with AI Coachie for personalized insights!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

                if (isSpeaking) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "🔊 Speaking...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * Weekly Blueprint Card - Displays weekly meal plan and shopping list summary
 */
@Composable
fun WeeklyBlueprintCard(
    weeklyBlueprint: Map<String, Any>?,
    generatingBlueprint: Boolean,
    onGenerate: () -> Unit,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier
) {
    CoachieCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onNavigate() },
        colors = CoachieCardDefaults.colors(
            containerColor = Color(0xFF6B46C1).copy(alpha = 0.2f) // Purple accent
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Your Weekly Blueprint",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (weeklyBlueprint != null) {
                        // Calculate total items from shopping list categories
                        val shoppingList = weeklyBlueprint["shoppingList"] as? Map<*, *>
                        val itemCount = shoppingList?.values?.sumOf { category ->
                            (category as? List<*>)?.size ?: 0
                        } ?: 0
                        
                        // Calculate total meals from meals array
                        val mealsData = weeklyBlueprint["meals"]
                        val totalMeals = when {
                            mealsData is List<*> -> {
                                // Meals is an array of day objects
                                mealsData.sumOf { day ->
                                    val dayMap = day as? Map<*, *> ?: return@sumOf 0
                                    var count = 0
                                    // Check all possible meal fields (breakfast, lunch, dinner, meal)
                                    if (dayMap["breakfast"] != null) count++
                                    if (dayMap["lunch"] != null) count++
                                    if (dayMap["dinner"] != null) count++
                                    if (dayMap["meal"] != null) count++ // Also check "meal" field for 1-meal-per-day plans
                                    // Count snacks
                                    val snacks = dayMap["snacks"] as? List<*>
                                    count + (snacks?.size ?: 0)
                                }
                            }
                            mealsData is Map<*, *> -> {
                                // Meals might be stored as a map (day names as keys)
                                mealsData.values.sumOf { day ->
                                    val dayMap = day as? Map<*, *> ?: return@sumOf 0
                                    var count = 0
                                    if (dayMap["breakfast"] != null) count++
                                    if (dayMap["lunch"] != null) count++
                                    if (dayMap["dinner"] != null) count++
                                    if (dayMap["meal"] != null) count++
                                    val snacks = dayMap["snacks"] as? List<*>
                                    count + (snacks?.size ?: 0)
                                }
                            }
                            else -> {
                                // Debug: Log the actual type and structure
                                android.util.Log.w("WeeklyBlueprintCard", "⚠️ Meals data type: ${mealsData?.javaClass?.simpleName}, value: $mealsData")
                                0
                            }
                        }
                        
                        // Debug: Log if meals count is 0 but meals data exists
                        if (totalMeals == 0 && mealsData != null) {
                            android.util.Log.w("WeeklyBlueprintCard", "⚠️ Found meals data but 0 meals counted. Type: ${mealsData.javaClass.simpleName}")
                            if (mealsData is List<*> && mealsData.isNotEmpty()) {
                                val firstDay = mealsData.firstOrNull() as? Map<*, *>
                                android.util.Log.w("WeeklyBlueprintCard", "⚠️ First day keys: ${firstDay?.keys?.joinToString()}")
                            }
                        }
                        
                        Text(
                            text = "$itemCount items • $totalMeals meals planned",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    } else {
                        Text(
                            text = "Generate your weekly meal plan",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Filled.RestaurantMenu,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (weeklyBlueprint == null && !generatingBlueprint) {
                Button(
                    onClick = onGenerate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6B46C1)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Generate my blueprint", color = Color.White)
                }
            } else if (generatingBlueprint) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Coachie is thinking…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            } else {
                Button(
                    onClick = onNavigate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6B46C1)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View Full List", color = Color.White)
                }
            }
        }
    }
}
