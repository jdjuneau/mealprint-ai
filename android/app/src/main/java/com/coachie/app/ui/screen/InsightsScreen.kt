package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.ui.theme.LocalGender
import com.coachie.app.ui.theme.SemanticColorCategory
import com.coachie.app.ui.theme.getSemanticColorPrimary
import com.coachie.app.service.ShareService
import com.coachie.app.service.ShareImageData
import com.coachie.app.service.ShareImageType
import com.coachie.app.ui.components.SharePlatformDialog
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import com.coachie.app.data.SubscriptionService
import com.coachie.app.data.model.SubscriptionTier
import com.coachie.app.data.model.AIFeature
import com.coachie.app.ui.components.UpgradePromptDialog
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import kotlin.math.max
import kotlin.math.min

data class Insight(
    val id: String,
    val title: String,
    val text: String,
    val chartData: List<Pair<Int, Double>> = emptyList(),
    val chartType: String = "line", // 'line', 'bar', 'area'
    val action: InsightAction? = null,
    val generatedAt: Long = System.currentTimeMillis()
)

data class InsightAction(
    val label: String,
    val type: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToHabitCreation: () -> Unit = {},
    onNavigateToHabitTemplates: () -> Unit = {},
    onNavigateToGoalsEdit: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSubscription: () -> Unit = {},
    onNavigateToMealLog: () -> Unit = {},
    onNavigateToMeditation: () -> Unit = {},
    onNavigateToWeeklyBlueprint: () -> Unit = {},
    onNavigateToWellness: () -> Unit = {},
    onNavigateToWaterLog: () -> Unit = {},
    onNavigateToHabits: () -> Unit = {},
    userId: String
) {
    val context = LocalContext.current
    val shareService = remember { ShareService.getInstance(context) }
    val db = Firebase.firestore
    val functions = Firebase.functions
    val coroutineScope = rememberCoroutineScope()
    var insights by remember { mutableStateOf<List<Insight>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isGeneratingInsights by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Share dialog state
    var showShareDialog by remember { mutableStateOf(false) }
    var pendingShareData by remember { mutableStateOf<ShareImageData?>(null) }
    
    // Subscription state
    var showUpgradeDialog by remember { mutableStateOf(false) }
    var subscriptionTier by remember { mutableStateOf<SubscriptionTier?>(null) }
    
    LaunchedEffect(userId) {
        coroutineScope.launch {
            subscriptionTier = SubscriptionService.getUserTier(userId)
        }
    }
    
    LaunchedEffect(userId) {
        isLoading = true
        try {
            // Try to load from Firestore first
            val insightsRef = db.collection("users")
                .document(userId)
                .collection("insights")
                .whereEqualTo("status", "active")
                .get()
                .await()

            val firestoreInsights = insightsRef.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                Insight(
                    id = doc.id,
                    title = data["title"] as? String ?: "",
                    text = data["text"] as? String ?: "",
                    chartData = (data["chartData"] as? List<*>)?.mapNotNull { item ->
                        val point = item as? Map<*, *> ?: return@mapNotNull null
                        val x = (point["x"] as? Number)?.toInt() ?: return@mapNotNull null
                        val y = (point["y"] as? Number)?.toDouble() ?: return@mapNotNull null
                        x to y
                    } ?: emptyList(),
                    chartType = data["chartType"] as? String ?: "line",
                    action = (data["action"] as? Map<*, *>)?.let { actionData ->
                        InsightAction(
                            label = actionData["label"] as? String ?: "",
                            type = actionData["type"] as? String ?: ""
                        )
                    },
                    generatedAt = (data["generatedAt"] as? com.google.firebase.Timestamp)?.toDate()?.time
                        ?: System.currentTimeMillis()
                )
            }
                .sortedByDescending { it.generatedAt } // Sort in memory

            if (firestoreInsights.isNotEmpty()) {
                insights = firestoreInsights
                android.util.Log.d("Insights", "Loaded ${firestoreInsights.size} insights from Firestore")
            } else {
                // No insights in Firestore - show empty state, no fake data
                insights = emptyList()
                errorMessage = "No insights available. Generate insights to see personalized analysis of your health data."
                android.util.Log.d("Insights", "No insights in Firestore")
            }
        } catch (e: Exception) {
            android.util.Log.e("Insights", "Error loading insights from Firestore", e)
            insights = emptyList()
            errorMessage = "Failed to load insights: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    suspend fun generateInsights() {
        try {
            // Check subscription before generating (server also verifies)
            val tier = SubscriptionService.getUserTier(userId)
            val canUse = SubscriptionService.canUseAIFeature(userId, AIFeature.MONTHLY_INSIGHTS)
            
            if (!canUse) {
                showUpgradeDialog = true
                errorMessage = "Monthly insights are available for Pro subscribers only"
                return
            }
            
            errorMessage = null
            isGeneratingInsights = true

            // Try Firebase Functions first
            val generateInsightsFunction = functions.getHttpsCallable("generateUserInsights")
            val task = generateInsightsFunction.call(mapOf("forceRegenerate" to false))
            val result = task.await()

            // Reload insights from Firebase
            val insightsRef = db.collection("users")
                .document(userId)
                .collection("insights")
                .whereEqualTo("status", "active")
                .get()
                .await()

            val firebaseInsights = insightsRef.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                Insight(
                    id = doc.id,
                    title = data["title"] as? String ?: "",
                    text = data["text"] as? String ?: "",
                    chartData = (data["chartData"] as? List<*>)?.mapNotNull { item ->
                        val point = item as? Map<*, *> ?: return@mapNotNull null
                        val x = (point["x"] as? Number)?.toInt() ?: return@mapNotNull null
                        val y = (point["y"] as? Number)?.toDouble() ?: return@mapNotNull null
                        x to y
                    } ?: emptyList(),
                    chartType = data["chartType"] as? String ?: "line",
                    action = (data["action"] as? Map<*, *>)?.let { actionData ->
                        InsightAction(
                            label = actionData["label"] as? String ?: "",
                            type = actionData["type"] as? String ?: ""
                        )
                    },
                    generatedAt = (data["generatedAt"] as? com.google.firebase.Timestamp)?.toDate()?.time
                        ?: System.currentTimeMillis()
                )
            }
                .sortedByDescending { it.generatedAt } // Sort in memory

            if (firebaseInsights.isNotEmpty()) {
                insights = firebaseInsights
                errorMessage = null
                android.util.Log.d("Insights", "Generated and loaded ${firebaseInsights.size} insights from Firebase")
                
            } else {
                // No insights generated - show error, no fake data
                insights = emptyList()
                errorMessage = "Failed to generate insights. Make sure you have logged health data and that the OpenAI API key is configured."
                android.util.Log.d("Insights", "Firebase returned empty insights")
            }
        } catch (e: Exception) {
            android.util.Log.e("Insights", "Error generating insights via Firebase", e)
            insights = emptyList()
            errorMessage = "Failed to generate insights: ${e.message ?: "Unknown error"}. Make sure you have logged health data and that Firebase Functions are deployed."
        } finally {
            isGeneratingInsights = false
        }
    }
    
    // Upgrade dialog
    if (showUpgradeDialog) {
        UpgradePromptDialog(
            onDismiss = { showUpgradeDialog = false },
            onUpgrade = {
                showUpgradeDialog = false
                onNavigateToSubscription()
            },
            featureName = "Monthly AI Insights"
        )
    }

    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    
    // Get semantic color for Insights (indigo theme)
    val gender = LocalGender.current
    val isMale = gender.lowercase() == "male"
    val insightsColor = getSemanticColorPrimary(SemanticColorCategory.INSIGHTS, isMale)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBackground)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Insights", color = insightsColor) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Filled.ArrowBack, "Back", tint = insightsColor)
                        }
                    },
                    actions = {
                        if (isGeneratingInsights) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = insightsColor,
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        generateInsights()
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Refresh, "Generate Insights", tint = insightsColor)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = insightsColor,
                        navigationIconContentColor = insightsColor,
                        actionIconContentColor = insightsColor
                    )
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            } else if (insights.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lightbulb,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No insights yet",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "Generate insights to see personalized analysis of your health data.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "How Insights Work",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "• Uses data from the past month (meals, workouts, sleep, water, mood, energy scores)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "• Takes 10-30 seconds to generate",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "• Requires at least some logged health data (no minimum days)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "• Can only generate once every 7 days (cooldown period)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "• Generated using AI analysis of your patterns",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                generateInsights()
                            }
                        },
                        enabled = !isGeneratingInsights,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isGeneratingInsights) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generating...")
                        } else {
                            Text("Generate Insights")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            text = "📊 Tap action buttons to apply insights to your routine!",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF6B7280),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    items(insights) { insight ->
                        InsightCard(
                            insight = insight,
                            insightsColor = insightsColor,
                            onAction = {
                                android.util.Log.d("Insights", "Action clicked for insight: ${insight.title}, type: ${insight.action?.type}, label: ${insight.action?.label}")
                                val label = insight.action?.label?.lowercase() ?: ""
                                
                                // Map action labels to navigation routes
                                when {
                                    // Water/Hydration actions
                                    label.contains("water") || label.contains("hydrate") || label.contains("hydration") -> {
                                        onNavigateToWaterLog()
                                    }
                                    label.contains("track") && label.contains("water") -> {
                                        onNavigateToWaterLog()
                                    }
                                    
                                    // Meal-related actions
                                    label.contains("plan") && (label.contains("meal") || label.contains("balanced")) -> {
                                        onNavigateToWeeklyBlueprint()
                                    }
                                    label.contains("log") && label.contains("meal") -> {
                                        onNavigateToMealLog()
                                    }
                                    label.contains("meal") && (label.contains("plan") || label.contains("blueprint")) -> {
                                        onNavigateToWeeklyBlueprint()
                                    }
                                    
                                    // Meditation/Mindfulness actions
                                    label.contains("mindfulness") || label.contains("meditation") -> {
                                        onNavigateToMeditation()
                                    }
                                    label.contains("relaxation") || label.contains("explore relaxation") -> {
                                        onNavigateToMeditation()
                                    }
                                    label.contains("try mindfulness") -> {
                                        onNavigateToMeditation()
                                    }
                                    
                                    // Rest/Recovery actions
                                    label.contains("rest") && (label.contains("day") || label.contains("plan")) -> {
                                        onNavigateToHabits() // Rest days can be tracked as habits
                                    }
                                    
                                    // Wellness actions
                                    label.contains("wellness") || label.contains("wellbeing") -> {
                                        onNavigateToWellness()
                                    }
                                    
                                    // Habit actions
                                    label.contains("habit") -> {
                                        onNavigateToHabitTemplates()
                                    }
                                    
                                    // Goal actions
                                    label.contains("goal") || label.contains("set") && label.contains("goal") -> {
                                        onNavigateToGoalsEdit()
                                    }
                                    
                                    // Settings actions
                                    label.contains("setting") -> {
                                        onNavigateToSettings()
                                    }
                                    
                                    // Fallback: try action type
                                    else -> {
                                        when (insight.action?.type) {
                                            "motivation" -> {
                                                // Show motivation message
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "Keep up the great work! 💪",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            "goal" -> {
                                                onNavigateToGoalsEdit()
                                            }
                                            "habit" -> {
                                                onNavigateToHabitTemplates()
                                            }
                                            "settings" -> {
                                                onNavigateToSettings()
                                            }
                                            else -> {
                                                // Last resort: show toast
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "Action: ${insight.action?.label ?: "Unknown"}",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            },
                            onShare = {
                                android.util.Log.d("Insights", "Share clicked for insight: ${insight.title}")
                                pendingShareData = ShareImageData(
                                    type = ShareImageType.INSIGHT,
                                    title = insight.title,
                                    metric = insight.text.take(50) + "...",
                                    subtitle = insight.text.take(100)
                                )
                                showShareDialog = true
                            }
                        )
                    }
                }
            }
        }
        }
        
        // Error message display
        errorMessage?.let { error ->
            LaunchedEffect(error) {
                android.widget.Toast.makeText(
                    context,
                    error,
                    android.widget.Toast.LENGTH_LONG
                ).show()
                errorMessage = null
            }
        }
        
        // Share platform dialog
        if (showShareDialog) {
            SharePlatformDialog(
                onDismiss = { showShareDialog = false },
                onShareToPlatform = { platform ->
                    showShareDialog = false
                    pendingShareData?.let { data ->
                        shareService.generateAndShare(data, platform)
                    }
                    pendingShareData = null
                }
            )
        }
    }
}

@Composable
fun InsightCard(
    insight: Insight,
    insightsColor: Color,
    onAction: () -> Unit,
    onShare: () -> Unit
) {
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.95f)
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
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = insight.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F2937) // Dark gray for better contrast
                    )
                }
                
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share",
                        tint = insightsColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = insight.text,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF374151), // Dark gray for readability
                lineHeight = 24.sp
            )
            
            // Chart visualization
            if (insight.chartData.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                SimpleChart(
                    data = insight.chartData,
                    chartType = insight.chartType,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    chartColor = insightsColor
                )
            }
            
            if (insight.action != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = insightsColor
                    )
                ) {
                    Text(
                        text = "🎯 ${insight.action.label}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                    .format(java.util.Date(insight.generatedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9CA3AF) // Light gray for secondary text
            )
        }
    }
}

@Composable
fun SimpleChart(
    data: List<Pair<Int, Double>>,
    chartType: String,
    modifier: Modifier = Modifier,
    chartColor: Color = Color(0xFF6B46C1) // Default to indigo if not provided
) {
    if (data.isEmpty()) return
    
    val maxValue = data.maxOfOrNull { it.second } ?: 1.0
    val minValue = data.minOfOrNull { it.second } ?: 0.0
    val valueRange = maxValue - minValue
    val padding = 40.dp
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF3F4F6)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = this.size.width
            val canvasHeight = this.size.height
            val chartWidth = canvasWidth - padding.toPx() * 2
            val chartHeight = canvasHeight - padding.toPx() * 2
            val startX = padding.toPx()
            val startY = padding.toPx()
            
            // Draw grid lines
            drawLine(
                color = Color(0xFFE5E7EB),
                start = Offset(startX, startY),
                end = Offset(startX, startY + chartHeight),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color(0xFFE5E7EB),
                start = Offset(startX, startY + chartHeight),
                end = Offset(startX + chartWidth, startY + chartHeight),
                strokeWidth = 1.dp.toPx()
            )
            
            // Draw chart based on type
            when (chartType.lowercase()) {
                "line" -> {
                    // Draw line chart
                    val path = Path()
                    val pointSize = 4.dp.toPx()
                    val strokeWidth = 2.dp.toPx()
                    
                    data.forEachIndexed { index, (_, value) ->
                        val x = startX + (index.toFloat() / (data.size - 1).coerceAtLeast(1)) * chartWidth
                        val normalizedValue = if (valueRange > 0) {
                            ((value - minValue) / valueRange).toFloat()
                        } else {
                            0.5f
                        }
                        val y = startY + chartHeight - (normalizedValue * chartHeight)
                        val point = Offset(x, y)
                        
                        if (index == 0) {
                            path.moveTo(point.x, point.y)
                        } else {
                            path.lineTo(point.x, point.y)
                        }
                        
                        // Draw point
                        drawCircle(
                            color = chartColor,
                            radius = pointSize,
                            center = point
                        )
                    }
                    
                    // Draw line
                    drawPath(
                        path = path,
                        color = chartColor,
                        style = Stroke(width = strokeWidth)
                    )
                }
                "bar" -> {
                    // Draw bar chart
                    val barWidth = chartWidth / data.size * 0.7f
                    val barSpacing = chartWidth / data.size * 0.3f
                    
                    data.forEachIndexed { index, (_, value) ->
                        val normalizedValue = if (valueRange > 0) {
                            ((value - minValue) / valueRange).toFloat()
                        } else {
                            0.5f
                        }
                        val barHeight = normalizedValue * chartHeight
                        val x = startX + index.toFloat() * (barWidth + barSpacing) + barSpacing / 2
                        val y = startY + chartHeight - barHeight
                        
                        drawRect(
                            color = chartColor,
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight)
                        )
                    }
                }
                "area" -> {
                    // Draw area chart
                    val path = Path()
                    val fillPath = Path()
                    
                    data.forEachIndexed { index, (_, value) ->
                        val x = startX + (index.toFloat() / (data.size - 1).coerceAtLeast(1)) * chartWidth
                        val normalizedValue = if (valueRange > 0) {
                            ((value - minValue) / valueRange).toFloat()
                        } else {
                            0.5f
                        }
                        val y = startY + chartHeight - (normalizedValue * chartHeight)
                        val point = Offset(x, y)
                        
                        if (index == 0) {
                            path.moveTo(point.x, point.y)
                            fillPath.moveTo(startX, startY + chartHeight)
                            fillPath.lineTo(point.x, point.y)
                        } else {
                            path.lineTo(point.x, point.y)
                            fillPath.lineTo(point.x, point.y)
                        }
                    }
                    
                    // Close the fill path
                    fillPath.lineTo(startX + chartWidth, startY + chartHeight)
                    fillPath.close()
                    
                    // Draw filled area
                    drawPath(
                        path = fillPath,
                        color = chartColor.copy(alpha = 0.3f),
                        style = Fill
                    )
                    
                    // Draw line
                    drawPath(
                        path = path,
                        color = chartColor,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
            
            // Draw Y-axis labels
            val yLabels = 5
            for (i in 0..yLabels) {
                val value = minValue + (maxValue - minValue) * (yLabels - i) / yLabels
                val y = startY + (i * chartHeight / yLabels)
                val label = if (value % 1.0 == 0.0) {
                    value.toInt().toString()
                } else {
                    String.format("%.1f", value)
                }
                
                // Draw label tick mark (simplified - just draw a small line)
                drawLine(
                    color = Color(0xFF9CA3AF),
                    start = Offset(startX - 5.dp.toPx(), y),
                    end = Offset(startX, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }
}

