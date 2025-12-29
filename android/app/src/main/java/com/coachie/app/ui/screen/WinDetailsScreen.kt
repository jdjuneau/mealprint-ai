package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.service.ShareService
import com.coachie.app.service.ShareImageData
import com.coachie.app.service.ShareImageType
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.components.SharePlatformDialog
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.viewmodel.AuthState
import com.coachie.app.viewmodel.CoachieViewModel
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WinDetailsScreen(
    win: String,
    onNavigateBack: () -> Unit,
    viewModel: CoachieViewModel = viewModel()
) {
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val authState by viewModel.authState.collectAsState()
    val currentUser = (authState as? AuthState.Authenticated)?.user
    val userId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: return
    val context = LocalContext.current
    val shareService = ShareService.getInstance(context)
    
    // Share dialog state
    var showShareDialog by remember { mutableStateOf(false) }
    var pendingShareData by remember { mutableStateOf<ShareImageData?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBackground)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Win of the Day") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                "Back",
                                tint = Color.Black
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            pendingShareData = ShareImageData(
                                type = ShareImageType.CIRCLE_WIN,
                                title = win,
                                metric = "Win of the Day"
                            )
                            showShareDialog = true
                        }) {
                            Icon(
                                androidx.compose.material.icons.Icons.Filled.Share,
                                "Share",
                                tint = Color.Black
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.Black, // Changed to black for readability on light gradient
                        navigationIconContentColor = Color.Black, // Changed to black for readability
                        actionIconContentColor = Color.Black // Changed to black for readability
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Main win card
                CoachieCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CoachieCardDefaults.colors(
                        containerColor = Color(0xFF10B981).copy(alpha = 0.9f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "🎉",
                            style = MaterialTheme.typography.displayLarge
                        )

                        Text(
                            text = win,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Great job celebrating your win! Every victory, no matter how small, contributes to your overall progress and well-being.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Black.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Motivation section
                CoachieCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CoachieCardDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Keep the Momentum Going",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "Remember, consistency is key. Each win builds upon the last, creating a foundation of positive habits and achievements. Celebrate your progress and stay motivated for what's next!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Justify
                        )
                    }
                }

                // Action suggestions
                CoachieCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CoachieCardDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Next Steps",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val suggestions = listOf(
                            "Share this win with your circle for extra motivation",
                            "Add this achievement to your journal",
                            "Set a new goal based on this success",
                            "Review your progress in the insights section"
                        )

                        suggestions.forEach { suggestion ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = suggestion,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Share platform dialog
    if (showShareDialog) {
        SharePlatformDialog(
            onDismiss = { showShareDialog = false },
            onShareToPlatform = { platform ->
                showShareDialog = false
                pendingShareData?.let { data ->
                    shareService.generateAndShareWithContext(data, context, platform)
                }
                pendingShareData = null
            }
        )
    }
}
