package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitIntelligenceScreen(
    onBack: () -> Unit,
    viewModel: HabitIntelligenceViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val gradientBackground = rememberCoachieGradient(endY = 2000f)
    
    // Log state changes to debug
    LaunchedEffect(uiState.selectedTimeRange, uiState.habitPatterns.size, uiState.intelligenceScore.overallScore) {
        android.util.Log.d("HabitIntelligenceScreen", "UI State changed:")
        android.util.Log.d("HabitIntelligenceScreen", "  - Time range: ${uiState.selectedTimeRange}")
        android.util.Log.d("HabitIntelligenceScreen", "  - Patterns: ${uiState.habitPatterns.size}")
        android.util.Log.d("HabitIntelligenceScreen", "  - Intelligence score: ${uiState.intelligenceScore.overallScore}")
        android.util.Log.d("HabitIntelligenceScreen", "  - Is loading: ${uiState.isLoading}")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Habit Intelligence", color = Color.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
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
                .padding(paddingValues)
        ) {
            // Force LazyColumn recreation when time range changes
            key(uiState.selectedTimeRange) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    // Intelligence Score
                    item(key = "intelligence_score") {
                        IntelligenceScoreSection(uiState.intelligenceScore)
                    }

                    // Pattern Recognition
                    if (uiState.habitPatterns.isNotEmpty()) {
                        item(key = "patterns") {
                            PatternRecognitionSection(uiState.habitPatterns, viewModel)
                        }
                    }

                    // Adaptive Suggestions
                    if (uiState.adaptiveSuggestions.isNotEmpty()) {
                        item(key = "suggestions") {
                            AdaptiveSuggestionsSection(uiState.adaptiveSuggestions, viewModel)
                        }
                    }

                    // Performance Insights
                    if (uiState.performanceInsights.isNotEmpty()) {
                        item(key = "insights") {
                            PerformanceInsightsSection(uiState.performanceInsights, viewModel)
                        }
                    }

                    // Difficulty Adjustments
                    if (uiState.difficultyAdjustments.isNotEmpty()) {
                        item(key = "adjustments") {
                            DifficultyAdjustmentsSection(uiState.difficultyAdjustments)
                        }
                    }

                // Empty state
                if (uiState.habitPatterns.isEmpty() &&
                    uiState.adaptiveSuggestions.isEmpty() &&
                    uiState.performanceInsights.isEmpty() &&
                    uiState.difficultyAdjustments.isEmpty()) {
                    item {
                        EmptyIntelligenceState()
                    }
                }

                // Error message
                uiState.error?.let { error ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }

            // Loading overlay
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun IntelligenceScoreSection(score: IntelligenceScore) {
    CoachieCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🧠 AI Intelligence Score",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Overall Score
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        color = when {
                            score.overallScore >= 80 -> MaterialTheme.colorScheme.primary
                            score.overallScore >= 60 -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.tertiary
                        }.copy(alpha = 0.2f),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${score.overallScore.toInt()}",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        score.overallScore >= 80 -> MaterialTheme.colorScheme.primary
                        score.overallScore >= 60 -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.tertiary
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when {
                    score.overallScore >= 80 -> "Expert Level Intelligence 🎉"
                    score.overallScore >= 60 -> "Advanced Intelligence 📈"
                    score.overallScore >= 40 -> "Developing Intelligence 🔄"
                    else -> "Building Intelligence 🌱"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Component Scores
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScoreComponent(
                    label = "Patterns",
                    score = score.patternRecognition,
                    modifier = Modifier.weight(1f)
                )
                ScoreComponent(
                    label = "Adaptation",
                    score = score.adaptiveLearning,
                    modifier = Modifier.weight(1f)
                )
                ScoreComponent(
                    label = "Prediction",
                    score = score.predictiveAccuracy,
                    modifier = Modifier.weight(1f)
                )
                ScoreComponent(
                    label = "Insights",
                    score = score.insightQuality,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Last updated: ${score.lastUpdated}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScoreComponent(label: String, score: Double, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "${score.toInt()}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TimeRangeSelector(viewModel: HabitIntelligenceViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    CoachieCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Analysis Time Range",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimeRange.values().forEach { range ->
                    FilterChip(
                        selected = uiState.selectedTimeRange == range,
                        onClick = { viewModel.setTimeRange(range) },
                        label = {
                            Text(
                                range.name.lowercase().replaceFirstChar { it.uppercase() },
                                color = if (uiState.selectedTimeRange == range) Color.Black else Color.Black
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            selectedLabelColor = Color.Black
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PatternRecognitionSection(patterns: List<HabitPattern>, viewModel: HabitIntelligenceViewModel) {
    CoachieCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🔍 Discovered Patterns",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            patterns.take(5).forEach { pattern ->
                PatternCard(pattern, viewModel)
            }
        }
    }
}

@Composable
private fun PatternCard(pattern: HabitPattern, viewModel: HabitIntelligenceViewModel) {
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = viewModel.getPatternTypeIcon(pattern.patternType),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = pattern.habitName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = viewModel.getPatternTypeDescription(pattern.patternType),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = when {
                        pattern.strength > 0.8 -> MaterialTheme.colorScheme.primaryContainer
                        pattern.strength > 0.6 -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.tertiaryContainer
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${(pattern.strength * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Description and Insight
            Text(
                text = pattern.description,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = pattern.insight,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Actionable Advice
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "💡 ${pattern.actionableAdvice}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(8.dp)
                    )
                }
        }
    }
}

@Composable
private fun AdaptiveSuggestionsSection(suggestions: List<AdaptiveSuggestion>, viewModel: HabitIntelligenceViewModel) {
    CoachieCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🚀 Smart Suggestions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            suggestions.take(3).forEach { suggestion ->
                AdaptiveSuggestionCard(suggestion, viewModel)
            }
        }
    }
}

@Composable
private fun AdaptiveSuggestionCard(suggestion: AdaptiveSuggestion, viewModel: HabitIntelligenceViewModel) {
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = viewModel.getSuggestionTypeIcon(suggestion.suggestionType),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = suggestion.habitName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "+${(suggestion.expectedImprovement * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${(suggestion.confidence * 100).toInt()}% confidence",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Title and Description
            Text(
                text = suggestion.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = suggestion.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Implementation
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "🔧 ${suggestion.implementation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(8.dp)
                )
            }

            // Risks (if any)
            if (suggestion.potentialRisks.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "⚠️ Consider:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Medium
                        )
                        suggestion.potentialRisks.forEach { risk ->
                            Text(
                                text = "• $risk",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformanceInsightsSection(insights: List<PerformanceInsight>, viewModel: HabitIntelligenceViewModel) {
    CoachieCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "📊 Performance Insights",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            insights.take(4).forEach { insight ->
                PerformanceInsightCard(insight, viewModel)
            }
        }
    }
}

@Composable
private fun PerformanceInsightCard(insight: PerformanceInsight, viewModel: HabitIntelligenceViewModel) {
    CoachieCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = viewModel.getInsightTypeIcon(insight.insightType),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = insight.habitName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = viewModel.getTrendIcon(insight.trend),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = viewModel.getTrendDescription(insight.trend),
                        style = MaterialTheme.typography.bodySmall,
                        color = when (insight.trend) {
                            Trend.UP -> MaterialTheme.colorScheme.onSurface
                            Trend.DOWN -> MaterialTheme.colorScheme.error
                            Trend.STABLE -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // Title and Description
            Text(
                text = insight.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = insight.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Time frame and recommendation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = insight.timeFrame,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = insight.recommendation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DifficultyAdjustmentsSection(adjustments: List<DifficultyAdjustment>) {
    CoachieCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "⚖️ Difficulty Recommendations",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            adjustments.take(2).forEach { adjustment ->
                DifficultyAdjustmentCard(adjustment)
            }
        }
    }
}

@Composable
private fun DifficultyAdjustmentCard(adjustment: DifficultyAdjustment) {
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = adjustment.habitName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Current: ${adjustment.currentDifficulty.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Recommended: ${adjustment.recommendedDifficulty.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${(adjustment.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = adjustment.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Expected: ${adjustment.expectedOutcome}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            // Implementation steps
            Text(
                text = "Steps:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            adjustment.implementationSteps.forEach { step ->
                Text(
                    text = "• $step",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyIntelligenceState() {
    CoachieCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🤖",
                style = MaterialTheme.typography.displayLarge
            )

            Text(
                text = "Building Intelligence",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Keep using Coachie and we'll analyze your patterns to provide personalized insights, suggestions, and optimizations for your habits.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Complete more habits to unlock AI-powered intelligence features!",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface // Changed from primary (blue) to onSurface (dark) for better readability
            )
        }
    }
}
