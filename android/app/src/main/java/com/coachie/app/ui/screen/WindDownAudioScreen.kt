package com.coachie.app.ui.screen

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.viewmodel.WindDownAudioViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WindDownAudioScreen(
    onNavigateBack: () -> Unit,
    viewModel: WindDownAudioViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val duration by viewModel.duration.collectAsState()

    // Initialize ViewModel
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    // Update progress periodically
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(1000)
            viewModel.updateProgress()
        }
    }

    val tracks = listOf(
        AudioTrack(
            id = "body_scan",
            title = "Body Scan Meditation",
            description = "Gentle awareness practice for deep relaxation",
            duration = "8:30",
            icon = "🧘‍♀️",
            audioResId = null // Would be actual audio resource
        ),
        AudioTrack(
            id = "gratitude",
            title = "Gratitude Practice",
            description = "Cultivate thankfulness and peace of mind",
            duration = "6:15",
            icon = "🙏",
            audioResId = null
        ),
        AudioTrack(
            id = "sleep_story",
            title = "Peaceful Sleep Story",
            description = "A calming story to guide you into restful sleep",
            duration = "12:45",
            icon = "🌙",
            audioResId = null
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E), // Dark blue
                        Color(0xFF16213E), // Darker blue
                        Color(0xFF0F0F23)  // Very dark blue
                    )
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Wind Down",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Current playing track display
                if (currentTrack != null) {
                    PlayingTrackCard(
                        track = currentTrack!!,
                        isPlaying = isPlaying,
                        progress = progress,
                        duration = duration,
                        onPlayPause = { viewModel.playPause() },
                        modifier = Modifier.padding(bottom = 32.dp)
                    )
                }

                // Audio tracks list
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            text = "Choose your wind-down experience",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        )
                    }

                    items(tracks) { track ->
                        AudioTrackCard(
                            track = track,
                            isSelected = currentTrack?.id == track.id,
                            onClick = {
                                coroutineScope.launch {
                                    viewModel.selectTrack(track)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlayingTrackCard(
    track: AudioTrack,
    isPlaying: Boolean,
    progress: Float,
    duration: String,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Large icon
            Text(
                text = track.icon,
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Track title
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f)
            )

            // Time and controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(progress, duration),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )

                // Play/Pause button
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = duration,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun AudioTrackCard(
    track: AudioTrack,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                Color.White.copy(alpha = 0.15f)
            else
                Color.White.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Text(
                text = track.icon,
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(end = 16.dp)
            )

            // Content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = track.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Duration
            Text(
                text = track.duration,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

data class AudioTrack(
    val id: String,
    val title: String,
    val description: String,
    val duration: String,
    val icon: String,
    val audioResId: Int?
)

private fun formatTime(progress: Float, totalDuration: String): String {
    // Simple time formatting - in real implementation would parse totalDuration
    return "0:00" // Placeholder
}
