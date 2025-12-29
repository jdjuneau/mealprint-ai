package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.ui.theme.LocalGender
import com.coachie.app.ui.theme.SemanticColorCategory
import com.coachie.app.ui.theme.getSemanticColorPrimary
import com.coachie.app.service.SocialMediaBreakService
import android.content.SharedPreferences
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialMediaBreakScreen(
    userId: String,
    onNavigateBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val gender = LocalGender.current
    val isMale = gender.lowercase() == "male"
    val accentColor = getSemanticColorPrimary(SemanticColorCategory.WELLNESS, isMale)
    val prefs = remember { context.getSharedPreferences("social_media_break_prefs", android.content.Context.MODE_PRIVATE) }
    
    // Duration options: 30 mins, 1 hour, 4 hours, 8 hours, all day (24 hours)
    data class BreakDuration(val minutes: Int, val label: String, val bonusPoints: Int)
    val breakDurations = listOf(
        BreakDuration(30, "30 min", 0),
        BreakDuration(60, "1 hour", 5),
        BreakDuration(240, "4 hours", 15),
        BreakDuration(480, "8 hours", 30),
        BreakDuration(1440, "All day", 50) // 24 hours = 1440 minutes
    )
    
    // Selected duration (default to 30 minutes)
    var selectedDuration by remember { mutableStateOf(breakDurations[0]) }
    val breakDurationSeconds = selectedDuration.minutes * 60
    
    // State for timer
    var isRunning by remember { mutableStateOf(false) }
    var timeRemaining by remember { mutableStateOf(0) }
    
    // Check if timer is already running
    LaunchedEffect(Unit) {
        val savedStartTime = prefs.getLong("start_time", 0)
        val savedDuration = prefs.getInt("duration_seconds", 0)
        val savedIsRunning = prefs.getBoolean("is_running", false)
        
        if (savedIsRunning && savedStartTime > 0 && savedDuration > 0) {
            isRunning = true
            // Update time remaining periodically
            while (true) {
                val elapsed = (System.currentTimeMillis() - savedStartTime) / 1000
                timeRemaining = ((savedDuration - elapsed).coerceAtLeast(0)).toInt()
                if (timeRemaining <= 0) {
                    isRunning = false
                    break
                }
                delay(1000)
            }
        }
    }
    
    // Format time remaining
    val hours = TimeUnit.SECONDS.toHours(timeRemaining.toLong())
    val minutes = TimeUnit.SECONDS.toMinutes(timeRemaining.toLong()) % 60
    val seconds = timeRemaining % 60
    val timeText = if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
    
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Social Media Break") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBackground)
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Icon
                Icon(
                    imageVector = Icons.Filled.PhoneAndroid,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = Color(0xFF1F2937)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Title
                Text(
                    text = "Social Media Break",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F2937),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Description
                Text(
                    text = "Take a mindful break from social media. Use this time to reconnect with yourself.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF374151),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Duration Selection (only show when not running)
                if (!isRunning) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Select Break Duration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1F2937)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            breakDurations.forEach { duration ->
                                OutlinedButton(
                                    onClick = { selectedDuration = duration },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selectedDuration == duration) 
                                            accentColor.copy(alpha = 0.3f) 
                                        else Color.Transparent,
                                        contentColor = if (selectedDuration == duration)
                                            MaterialTheme.colorScheme.onSurface // Dark text for selected
                                        else
                                            MaterialTheme.colorScheme.onSurface // Dark text for unselected (changed from hardcoded color)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.dp,
                                        color = if (selectedDuration == duration) 
                                            accentColor 
                                        else Color.Gray.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = duration.label,
                                            fontWeight = if (selectedDuration == duration) FontWeight.Bold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface // Explicitly set to dark for readability
                                        )
                                        if (duration.bonusPoints > 0) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Star,
                                                    contentDescription = "Bonus",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = accentColor // Keep accent color for icon
                                                )
                                                Text(
                                                    text = "+${duration.bonusPoints} points",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface, // Changed from accentColor (light) to onSurface (dark) for better readability
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                
                // Timer Display
                CoachieCard(
                    modifier = Modifier
                        .size(200.dp),
                    colors = CoachieCardDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.2f)
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F2937),
                            fontSize = 48.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Control Buttons
                if (!isRunning) {
                    Button(
                        onClick = {
                            SocialMediaBreakService.startTimer(context, breakDurationSeconds)
                            isRunning = true
                            timeRemaining = breakDurationSeconds
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = accentColor
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Start",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Break", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Timer running in background",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF374151),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = {
                                SocialMediaBreakService.stopTimer(context)
                                isRunning = false
                                timeRemaining = 0
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF1F2937)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = "Stop",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stop Timer")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Tips Card
                CoachieCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CoachieCardDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.15f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "💡 During Your Break:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F2937)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "• Take a walk outside\n• Read a book\n• Practice deep breathing\n• Do some stretching\n• Connect with nature\n• Journal your thoughts",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF374151),
                            lineHeight = 24.sp
                        )
                    }
                }
                
                // Bottom padding to ensure content isn't cut off
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

