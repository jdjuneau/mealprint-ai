package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.ripple.rememberRipple
import androidx.work.WorkManager
import com.coachie.app.ui.components.CoachieCard as Card
import com.coachie.app.ui.components.CoachieCardDefaults as CardDefaults
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.utils.DebugLogger
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.ktx.Firebase
import com.google.firebase.functions.ktx.functions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onBack: () -> Unit = {}
) {
    var clickCount by remember { mutableStateOf(0) }
    var lastClickTime by remember { mutableStateOf(0L) }
    val gradientBackground = rememberCoachieGradient(endY = 1600f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Screen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            Text(
                text = "Debug Test Screen",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            Text(
                text = "This screen has no overlays or modals to test pure tap detection.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Click Count: $clickCount",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(
                        text = "Test different interaction methods:",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Test button with debouncing
                    Text(
                        text = "Debug Me",
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(bounded = false)
                            ) {
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastClickTime > 500) { // 500ms debounce
                                    lastClickTime = currentTime
                                    clickCount++
                                    DebugLogger.logDebug("DebugScreen", "Text clicked - Count: $clickCount")
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    DebugLogger.logDebug("TapDebug", "Raw tap detected at offset: $offset on Debug Me text")
                                }
                            }
                            .padding(16.dp),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Check logs for TapDebug and DebugScreen messages",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Google Fit Sync Debug Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Google Fit Debug",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )

                    Text(
                        text = "Manually trigger Google Fit sync to test sleep data import",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )

                    val context = LocalContext.current
                    val coroutineScope = rememberCoroutineScope()
                    var isSyncing by remember { mutableStateOf(false) }

                    Button(
                        onClick = {
                            isSyncing = true
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    android.util.Log.d("DebugScreen", "🚀 Manual sync triggered")
                                    com.coachie.app.service.HealthSyncService.sync(context)
                                } catch (e: Exception) {
                                    android.util.Log.e("DebugScreen", "Sync failed", e)
                                    e.printStackTrace()
                                } finally {
                                    kotlinx.coroutines.delay(1000)
                                    isSyncing = false
                                }
                            }
                        },
                        enabled = !isSyncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isSyncing) "Syncing..." else "Trigger Google Fit Sync")
                    }

                    Text(
                        text = "Check Android Studio Logcat for sync results",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Subscription Testing Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Subscription Testing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )

                    Text(
                        text = "⚠️ TESTING ONLY - Use to test FREE and PRO tiers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )

                    val userId = com.coachie.app.util.AuthUtils.getAuthenticatedUserId()
                    var currentTier by remember { mutableStateOf<com.coachie.app.data.model.SubscriptionTier?>(null) }
                    var isLoadingTier by remember { mutableStateOf(false) }
                    var testMessage by remember { mutableStateOf<String?>(null) }
                    val subscriptionCoroutineScope = rememberCoroutineScope()

                    // Load current tier
                    LaunchedEffect(userId) {
                        if (userId != null) {
                            isLoadingTier = true
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val subscriptionService = com.coachie.app.data.SubscriptionService()
                                    currentTier = subscriptionService.getUserTier(userId)
                                } catch (e: Exception) {
                                    android.util.Log.e("DebugScreen", "Error loading tier", e)
                                } finally {
                                    isLoadingTier = false
                                }
                            }
                        }
                    }

                    if (userId != null) {
                        if (isLoadingTier) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                text = "Current Tier: ${currentTier?.name ?: "Unknown"}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Grant Pro button
                        Button(
                            onClick = {
                                testMessage = null
                                subscriptionCoroutineScope.launch {
                                    try {
                                        val functions = Firebase.functions
                                        val grantTestSubscription = functions.getHttpsCallable("grantTestSubscription")
                                        val result = grantTestSubscription.call(hashMapOf(
                                            "userId" to userId,
                                            "durationMonths" to 3
                                        )).await()
                                        android.util.Log.d("DebugScreen", "✅ Pro subscription granted: $result")
                                        testMessage = "✅ Pro subscription granted for 3 months"
                                        // Reload tier
                                        val subscriptionService = com.coachie.app.data.SubscriptionService()
                                        currentTier = subscriptionService.getUserTier(userId)
                                    } catch (e: Exception) {
                                        android.util.Log.e("DebugScreen", "❌ Failed to grant Pro", e)
                                        testMessage = "❌ Error: ${e.message}"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Grant Pro (3 months)")
                        }

                        // Reset to Free button
                        Button(
                            onClick = {
                                testMessage = null
                                subscriptionCoroutineScope.launch {
                                    try {
                                        val subscriptionService = com.coachie.app.data.SubscriptionService()
                                        subscriptionService.updateSubscription(
                                            userId = userId,
                                            tier = com.coachie.app.data.model.SubscriptionTier.FREE,
                                            expiresAt = null,
                                            purchaseToken = null,
                                            productId = null
                                        )
                                        android.util.Log.d("DebugScreen", "✅ Reset to FREE tier")
                                        testMessage = "✅ Reset to FREE tier"
                                        currentTier = subscriptionService.getUserTier(userId)
                                    } catch (e: Exception) {
                                        android.util.Log.e("DebugScreen", "❌ Failed to reset", e)
                                        testMessage = "❌ Error: ${e.message}"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Reset to FREE")
                        }

                        if (testMessage != null) {
                            Text(
                                text = testMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (testMessage!!.startsWith("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        }

                        Text(
                            text = "After changing tier, restart app or navigate to Subscription screen to see changes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = "Not logged in",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            }
        }
    }
}
