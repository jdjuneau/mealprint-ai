package com.coachie.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coachie.app.data.model.Habit
import com.coachie.app.ui.theme.Primary40
import com.coachie.app.ui.theme.Tertiary40
import java.util.Calendar

/**
 * Habit Progress Card - Shows today's habits and completion status
 */
@Composable
fun HabitProgressCard(
    habits: List<Habit>,
    completedCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalHabits = habits.size
    val progress = if (totalHabits > 0) completedCount.toFloat() / totalHabits.toFloat() else 0f
    
    CoachieCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CoachieCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)
        ),
        border = CoachieCardDefaults.border(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Primary40,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Today's Habits",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                if (totalHabits > 0) {
                    Text(
                        text = "$completedCount of $totalHabits completed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = Primary40,
                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                } else {
                    Text(
                        text = "Start building healthy habits!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Icon(
                Icons.Filled.ArrowForward,
                contentDescription = "View habits",
                tint = Primary40,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Smart Suggestion Card - Contextual prompts for habits/wellness (legacy - kept for backward compatibility)
 */
@Composable
fun SmartSuggestionCard(
    suggestion: Suggestion,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gradientBrush = remember(suggestion.type) {
        Brush.linearGradient(
            colors = listOf(
                suggestion.accentColor.copy(alpha = 0.15f),
                suggestion.accentColor.copy(alpha = 0.05f)
            )
        )
    }
    
    CoachieCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onAction() },
        colors = CoachieCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)
        ),
        border = BorderStroke(1.dp, suggestion.accentColor.copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradientBrush)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = suggestion.accentColor.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = suggestion.icon,
                            contentDescription = null,
                            tint = suggestion.accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = suggestion.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = suggestion.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onAction) {
                        Text("Try it", style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

data class Suggestion(
    val id: String,
    val type: SuggestionType,
    val title: String,
    val message: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val accentColor: Color,
    val actionRoute: String,
    val timeOfDay: TimeOfDay = TimeOfDay.MORNING // Default to morning
)

enum class SuggestionType {
    HABIT_SUGGESTION,
    WELLNESS_REMINDER,
    JOURNAL_PROMPT,
    HABIT_PROGRESS,
    FIRST_TIME_HABITS,
    HEALTH_TRACKING,
    COMMUNITY
}

enum class TimeOfDay {
    MORNING,    // 6 AM - 11 AM
    AFTERNOON,  // 12 PM - 5 PM
    EVENING     // 6 PM - 11 PM
}

/**
 * Wellness Quick Access Card
 */
@Composable
fun WellnessQuickAccessCard(
    onJournal: () -> Unit,
    onMeditation: () -> Unit,
    onHabits: () -> Unit,
    modifier: Modifier = Modifier
) {
    CoachieCard(
        modifier = modifier.fillMaxWidth()
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
                    text = "Wellness & Habits",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    Icons.Filled.Spa,
                    contentDescription = null,
                    tint = Tertiary40,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WellnessQuickButton(
                    icon = Icons.Filled.EditNote,
                    label = "Journal",
                    onClick = onJournal,
                    modifier = Modifier.weight(1f)
                )
                WellnessQuickButton(
                    icon = Icons.Filled.SelfImprovement,
                    label = "Meditate",
                    onClick = onMeditation,
                    modifier = Modifier.weight(1f)
                )
                WellnessQuickButton(
                    icon = Icons.Filled.CheckCircle,
                    label = "Habits",
                    onClick = onHabits,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun WellnessQuickButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.16f),
            contentColor = MaterialTheme.colorScheme.primary
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * Generate contextual suggestions based on time and user behavior
 * Returns a mix of habits, wellness, health, and community suggestions
 * Ordered by time of day (morning -> afternoon -> evening)
 */
fun generateContextualSuggestions(
    currentHour: Int,
    hasHabits: Boolean,
    habitCount: Int,
    completedHabitsToday: Int,
    hasJournaledToday: Boolean,
    lastMeditationDaysAgo: Int
): List<Suggestion> {
    val suggestions = mutableListOf<Suggestion>()
    
    // Determine current time period
    val currentTimeOfDay = when {
        currentHour in 6..11 -> TimeOfDay.MORNING
        currentHour in 12..17 -> TimeOfDay.AFTERNOON
        currentHour in 18..23 -> TimeOfDay.EVENING
        else -> TimeOfDay.MORNING // Default to morning for late night/early morning
    }
    
    // ============================================
    // MORNING SUGGESTIONS (6 AM - 11 AM)
    // ============================================
    val morningSuggestions = mutableListOf<Suggestion>()
    
    // Morning Habits
    if (!hasHabits) {
        morningSuggestions.add(
            Suggestion(
                id = "morning_habits",
                type = SuggestionType.FIRST_TIME_HABITS,
                title = "Start Your Day Right",
                message = "Create morning habits to build consistency",
                icon = Icons.Filled.WbSunny,
                accentColor = Color(0xFFFFA726),
                actionRoute = "habit_suggestions",
                timeOfDay = TimeOfDay.MORNING
            )
        )
    } else if (completedHabitsToday < habitCount / 2) {
        morningSuggestions.add(
            Suggestion(
                id = "morning_complete_habits",
                type = SuggestionType.HABIT_PROGRESS,
                title = "Morning Habits",
                message = "Complete your morning habits to start strong",
                icon = Icons.Filled.CheckCircle,
                accentColor = Primary40,
                actionRoute = "habits",
                timeOfDay = TimeOfDay.MORNING
            )
        )
    }
    
    // Morning Wellness
    if (lastMeditationDaysAgo > 0) {
        morningSuggestions.add(
            Suggestion(
                id = "morning_meditation",
                type = SuggestionType.WELLNESS_REMINDER,
                title = "Morning Meditation",
                message = "Start your day with a mindful moment",
                icon = Icons.Filled.SelfImprovement,
                accentColor = Color(0xFF9C27B0),
                actionRoute = "meditation",
                timeOfDay = TimeOfDay.MORNING
            )
        )
    }
    
    // Morning Health
    morningSuggestions.add(
        Suggestion(
            id = "morning_water",
            type = SuggestionType.HEALTH_TRACKING,
            title = "Hydrate First",
            message = "Log your morning water intake",
            icon = Icons.Filled.LocalDrink,
            accentColor = Color(0xFF2196F3),
            actionRoute = "water_log",
            timeOfDay = TimeOfDay.MORNING
        )
    )
    
    // ============================================
    // AFTERNOON SUGGESTIONS (12 PM - 5 PM)
    // ============================================
    val afternoonSuggestions = mutableListOf<Suggestion>()
    
    // Afternoon Habits
    if (hasHabits && completedHabitsToday < habitCount) {
        afternoonSuggestions.add(
            Suggestion(
                id = "afternoon_habits",
                type = SuggestionType.HABIT_PROGRESS,
                title = "Midday Check-in",
                message = "Keep your momentum going",
                icon = Icons.Filled.TrendingUp,
                accentColor = Primary40,
                actionRoute = "habits",
                timeOfDay = TimeOfDay.AFTERNOON
            )
        )
    }
    
    // Afternoon Health
    afternoonSuggestions.add(
        Suggestion(
            id = "afternoon_meal",
            type = SuggestionType.HEALTH_TRACKING,
            title = "Log Your Lunch",
            message = "Track your nutrition for better insights",
            icon = Icons.Filled.Restaurant,
            accentColor = Color(0xFF4CAF50),
            actionRoute = "meal_log",
            timeOfDay = TimeOfDay.AFTERNOON
        )
    )
    
    // Afternoon Wellness
    afternoonSuggestions.add(
        Suggestion(
            id = "afternoon_breathing",
            type = SuggestionType.WELLNESS_REMINDER,
            title = "Afternoon Reset",
            message = "Take a quick breathing break",
            icon = Icons.Filled.Air,
            accentColor = Color(0xFF00BCD4),
            actionRoute = "breathing_exercises",
            timeOfDay = TimeOfDay.AFTERNOON
        )
    )
    
    // Afternoon Community
    afternoonSuggestions.add(
        Suggestion(
            id = "afternoon_community",
            type = SuggestionType.COMMUNITY,
            title = "Connect with Others",
            message = "Share your progress or get inspired",
            icon = Icons.Filled.Group,
            accentColor = Color(0xFFE91E63),
            actionRoute = "community",
            timeOfDay = TimeOfDay.AFTERNOON
        )
    )
    
    // ============================================
    // EVENING SUGGESTIONS (6 PM - 11 PM)
    // ============================================
    val eveningSuggestions = mutableListOf<Suggestion>()
    
    // Evening Wellness
    if (!hasJournaledToday) {
        eveningSuggestions.add(
            Suggestion(
                id = "evening_journal",
                type = SuggestionType.JOURNAL_PROMPT,
                title = "Reflect on Your Day",
                message = "Capture your wins and gratitudes",
                icon = Icons.Filled.EditNote,
                accentColor = Color(0xFF9C27B0),
                actionRoute = "journal_flow",
                timeOfDay = TimeOfDay.EVENING
            )
        )
    }
    
    // Evening Habits
    if (hasHabits) {
        if (completedHabitsToday == habitCount && habitCount > 0) {
            eveningSuggestions.add(
                Suggestion(
                    id = "evening_all_done",
                    type = SuggestionType.HABIT_PROGRESS,
                    title = "Perfect Day! 🎉",
                    message = "You completed all your habits today!",
                    icon = Icons.Filled.EmojiEvents,
                    accentColor = Color(0xFFFFD700),
                    actionRoute = "habit_progress",
                    timeOfDay = TimeOfDay.EVENING
                )
            )
        } else if (completedHabitsToday < habitCount) {
            eveningSuggestions.add(
                Suggestion(
                    id = "evening_complete_habits",
                    type = SuggestionType.HABIT_PROGRESS,
                    title = "Finish Strong",
                    message = "Complete your remaining habits",
                    icon = Icons.Filled.CheckCircle,
                    accentColor = Primary40,
                    actionRoute = "habits",
                    timeOfDay = TimeOfDay.EVENING
                )
            )
        }
    }
    
    // Evening Health
    eveningSuggestions.add(
        Suggestion(
            id = "evening_sleep",
            type = SuggestionType.HEALTH_TRACKING,
            title = "Prepare for Rest",
            message = "Log your sleep to track quality",
            icon = Icons.Filled.Bedtime,
            accentColor = Color(0xFF673AB7),
            actionRoute = "sleep_log",
            timeOfDay = TimeOfDay.EVENING
        )
    )
    
    // Evening Wellness
    eveningSuggestions.add(
        Suggestion(
            id = "evening_meditation",
            type = SuggestionType.WELLNESS_REMINDER,
            title = "Evening Wind Down",
            message = "Meditate to relax before bed",
            icon = Icons.Filled.SelfImprovement,
            accentColor = Color(0xFF9C27B0),
            actionRoute = "meditation",
            timeOfDay = TimeOfDay.EVENING
        )
    )
    
    // ============================================
    // COMBINE AND ORDER BY TIME OF DAY
    // ============================================
    // Add suggestions based on current time, but include a mix
    when (currentTimeOfDay) {
        TimeOfDay.MORNING -> {
            // Show morning suggestions first, then afternoon, then evening
            suggestions.addAll(morningSuggestions.take(2)) // Take 1-2 morning suggestions
            suggestions.addAll(afternoonSuggestions.take(1)) // Add 1 afternoon preview
            suggestions.addAll(eveningSuggestions.take(1)) // Add 1 evening preview
        }
        TimeOfDay.AFTERNOON -> {
            // Show afternoon suggestions first, then evening, then morning (for tomorrow)
            suggestions.addAll(afternoonSuggestions.take(2)) // Take 1-2 afternoon suggestions
            suggestions.addAll(eveningSuggestions.take(1)) // Add 1 evening preview
            suggestions.addAll(morningSuggestions.take(1)) // Add 1 morning (for tomorrow)
        }
        TimeOfDay.EVENING -> {
            // Show evening suggestions first, then morning (for tomorrow)
            suggestions.addAll(eveningSuggestions.take(2)) // Take 1-2 evening suggestions
            suggestions.addAll(morningSuggestions.take(1)) // Add 1 morning (for tomorrow)
        }
    }
    
    // Fallback: Always show habit suggestions if no habits exist
    if (!hasHabits && suggestions.none { it.type == SuggestionType.FIRST_TIME_HABITS }) {
        suggestions.add(
            0, // Add at the beginning
            Suggestion(
                id = "get_started_habits",
                type = SuggestionType.FIRST_TIME_HABITS,
                title = "Build Better Habits",
                message = "Get personalized habit suggestions based on your goals",
                icon = Icons.Filled.Lightbulb,
                accentColor = Primary40,
                actionRoute = "habit_suggestions",
                timeOfDay = TimeOfDay.MORNING
            )
        )
    }
    
    // Ensure we have at least 3-4 suggestions for variety
    // If we don't have enough, fill from other time periods
    if (suggestions.size < 3) {
        val allSuggestions = morningSuggestions + afternoonSuggestions + eveningSuggestions
        val missing = allSuggestions.filter { suggestion ->
            suggestions.none { it.id == suggestion.id }
        }
        suggestions.addAll(missing.take(3 - suggestions.size))
    }
    
    // Sort by time of day order: MORNING -> AFTERNOON -> EVENING
    return suggestions.sortedBy { suggestion ->
        when (suggestion.timeOfDay) {
            TimeOfDay.MORNING -> 0
            TimeOfDay.AFTERNOON -> 1
            TimeOfDay.EVENING -> 2
        }
    }
}

