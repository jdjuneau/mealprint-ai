package com.coachie.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coachie.app.data.model.Badge
import com.coachie.app.data.model.Streak
import com.coachie.app.data.model.AchievementProgress
import com.coachie.app.ui.components.CoachieCard as Card
import com.coachie.app.ui.components.CoachieCardDefaults as CardDefaults

/**
 * Badge display component
 */
@Composable
fun BadgeDisplay(
    badge: Badge,
    modifier: Modifier = Modifier
) {
    val (backgroundBrush, icon) = getBadgeStyle(badge.type)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        // Badge icon
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(brush = backgroundBrush)
                .border(
                    width = if (badge.isNew) 3.dp else 2.dp,
                    color = if (badge.isNew) Color(0xFFFFD700) else Color.White.copy(alpha = 0.5f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = badge.name,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )

            // New badge indicator
            if (badge.isNew) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(Color(0xFFFFD700), CircleShape)
                        .align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "NEW",
                        color = Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Badge name
        Text(
            text = badge.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Badge description
        Text(
            text = badge.description,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 80.dp)
        )
    }
}

/**
 * Achievement progress display
 */
@Composable
fun AchievementProgressDisplay(
    progress: AchievementProgress,
    modifier: Modifier = Modifier
) {
    val (backgroundBrush, icon) = getBadgeStyle(progress.badge.type)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        // Badge icon (locked/unlocked state)
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(
                    brush = if (progress.isCompleted) backgroundBrush
                           else Brush.linearGradient(listOf(Color.Gray, Color.DarkGray))
                )
                .border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.3f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = progress.badge.name,
                tint = if (progress.isCompleted) Color.White else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(32.dp)
            )

            // Lock icon for incomplete achievements
            if (!progress.isCompleted) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Locked",
                    tint = Color.White,
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.BottomEnd)
                        .offset(4.dp, 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Progress indicator
        Box(modifier = Modifier.width(60.dp)) {
            LinearProgressIndicator(
                progress = progress.progressPercentage,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (progress.isCompleted) Color(0xFF4CAF50) else Color.Gray,
                trackColor = Color.LightGray
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Progress text
        Text(
            text = progress.progressText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // Badge name
        Text(
            text = progress.badge.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(max = 80.dp)
        )
    }
}

/**
 * Streak display component
 */
@Composable
fun StreakDisplay(
    streak: Streak,
    modifier: Modifier = Modifier
) {
    val isActive = streak.isActive
    val streakColor = if (isActive) Color(0xFFFF6B35) else Color.Gray

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Flame icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = if (isActive) listOf(Color(0xFFFF6B35), Color(0xFFFF8C42))
                                   else listOf(Color.Gray, Color.DarkGray)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = "Streak",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Current streak
            Text(
                text = "${streak.currentStreak}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = streakColor
            )

            Text(
                text = if (streak.currentStreak == 1) "Day Streak" else "Days Streak",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Streak status
            Text(
                text = streak.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Longest streak
            if (streak.longestStreak > streak.currentStreak) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Personal best: ${streak.longestStreak} days",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Badges collection display
 */
@Composable
fun BadgesCollection(
    earnedBadges: List<Badge>,
    inProgressAchievements: List<AchievementProgress>,
    onBadgeClicked: (Badge) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "🏆 Achievements",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Earned badges
        if (earnedBadges.isNotEmpty()) {
            Text(
                text = "Earned Badges",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                items(earnedBadges) { badge ->
                    BadgeDisplay(
                        badge = badge,
                        modifier = Modifier.clickable { onBadgeClicked(badge) }
                    )
                }
            }
        }

        // In progress achievements
        if (inProgressAchievements.isNotEmpty()) {
            Text(
                text = "In Progress",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(inProgressAchievements) { progress ->
                    AchievementProgressDisplay(progress = progress)
                }
            }
        }

        // No achievements yet
        if (earnedBadges.isEmpty() && inProgressAchievements.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Start logging to earn your first badge! 🏃‍♂️",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Get badge styling based on type
 */
private fun getBadgeStyle(badgeType: String): Pair<Brush, androidx.compose.ui.graphics.vector.ImageVector> {
    return when (badgeType) {
        Badge.TYPE_THREE_DAY_HERO -> Brush.linearGradient(
            colors = listOf(Color(0xFFFFD700), Color(0xFFFFA500))
        ) to Icons.Filled.Star

        Badge.TYPE_WEEK_WARRIOR -> Brush.linearGradient(
            colors = listOf(Color(0xFF2196F3), Color(0xFF1976D2))
        ) to Icons.Filled.EmojiEvents

        Badge.TYPE_SCAN_STAR -> Brush.linearGradient(
            colors = listOf(Color(0xFF4CAF50), Color(0xFF2E7D32))
        ) to Icons.Filled.CameraAlt

        else -> Brush.linearGradient(
            colors = listOf(Color.Gray, Color.DarkGray)
        ) to Icons.Filled.Star
    }
}
