package com.coachie.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mealprint.ai.ui.components.CoachieCard
import com.mealprint.ai.ui.components.CoachieCardDefaults
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Data class for mood chart data points
 */
data class MoodDataPoint(
    val date: LocalDate,
    val moodLevel: Int?, // 1-5 scale
    val energyLevel: Int? = null, // 1-10 scale (optional)
    val stressLevel: Int? = null // 1-10 scale (optional)
)

/**
 * Improved mood visualization - cleaner and more intuitive
 * Shows mood as primary focus with separate energy/stress indicators
 */
@Composable
fun MoodChart(
    moodData: List<MoodDataPoint>,
    showEnergy: Boolean = false,
    showStress: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Filter out null moods and sort by date
    val validData = moodData.filter { it.moodLevel != null }.sortedBy { it.date }

    if (validData.isEmpty()) {
        EmptyChart(
            message = "No mood data available\nStart tracking your mood to see trends!",
            modifier = modifier
        )
        return
    }

    // Calculate chart bounds for mood (1-5 scale)
    val minMood = 1
    val maxMood = 5
    val moodRange = maxMood - minMood

    val textMeasurer = rememberTextMeasurer()

    // Extract theme colors
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    // Color mapping for mood levels
    val moodColors = mapOf(
        1 to Color(0xFFE53935), // Red - Bad
        2 to Color(0xFFFF9800), // Orange - Not great
        3 to Color(0xFFFFC107), // Yellow - Okay
        4 to Color(0xFF4CAF50), // Green - Good
        5 to Color(0xFF2196F3)  // Blue - Great
    )
    
    // Mood labels
    val moodLabels = mapOf(
        1 to "😢 Bad",
        2 to "😕 Not Great",
        3 to "😐 Okay",
        4 to "🙂 Good",
        5 to "😀 Great"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Main mood chart - clean and focused
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Mood Trend",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = onSurfaceColor
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(surfaceColor)
                    .padding(20.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val chartAreaHeight = canvasHeight - 60f // Leave space for labels
                    val chartAreaWidth = canvasWidth - 60f // Leave space for left labels

                    // Horizontal grid lines (mood values 1-5)
                    for (i in 0..4) {
                        val y = (chartAreaHeight / 4) * i + 20f
                        drawLine(
                            color = onSurfaceColor.copy(alpha = 0.1f),
                            start = Offset(50f, y),
                            end = Offset(50f + chartAreaWidth, y),
                            strokeWidth = 1f
                        )

                        // Mood labels on left (5 to 1, top to bottom)
                        val moodValue = maxMood - i
                        val moodEmoji = when (moodValue) {
                            5 -> "😀"
                            4 -> "🙂"
                            3 -> "😐"
                            2 -> "😕"
                            1 -> "😢"
                            else -> ""
                        }
                        val moodText = "$moodEmoji $moodValue"
                        val textLayoutResult = textMeasurer.measure(moodText, TextStyle(fontSize = 10.sp))
                        drawText(
                            textLayoutResult,
                            color = onSurfaceVariantColor,
                            topLeft = Offset(5f, y - textLayoutResult.size.height / 2)
                        )
                    }

                    // Vertical grid lines (dates)
                    val dataPoints = validData.size
                    if (dataPoints > 1) {
                        val stepSize = chartAreaWidth / (dataPoints - 1)
                        val labelInterval = maxOf(1, dataPoints / 7) // Show ~7 date labels
                        for (i in 0 until dataPoints step labelInterval) {
                            val x = 50f + stepSize * i
                            drawLine(
                                color = onSurfaceColor.copy(alpha = 0.1f),
                                start = Offset(x, 20f),
                                end = Offset(x, chartAreaHeight + 20f),
                                strokeWidth = 1f
                            )

                            // Date labels on bottom
                            val date = validData[i].date
                            val dateText = date.format(DateTimeFormatter.ofPattern("MM/dd"))
                            val textLayoutResult = textMeasurer.measure(dateText, TextStyle(fontSize = 10.sp))
                            drawText(
                                textLayoutResult,
                                color = onSurfaceVariantColor,
                                topLeft = Offset(x - textLayoutResult.size.width / 2, chartAreaHeight + 25f)
                            )
                        }
                    }

                    // Draw mood trend line - clean and simple
                    if (validData.size > 1) {
                        // Draw filled area under the line for better visual impact
                        val areaPath = Path()
                        val linePath = Path()
                        var firstPoint = true
                        var firstX = 0f
                        var lastX = 0f

                        validData.forEachIndexed { index, dataPoint ->
                            val x = 50f + (chartAreaWidth / (validData.size - 1)) * index
                            val moodValue = dataPoint.moodLevel!!
                            val y = 20f + (chartAreaHeight * (maxMood - moodValue).toFloat() / moodRange.toFloat())

                            if (firstPoint) {
                                areaPath.moveTo(x, chartAreaHeight + 20f) // Start at bottom
                                areaPath.lineTo(x, y)
                                linePath.moveTo(x, y)
                                firstX = x
                                firstPoint = false
                            } else {
                                areaPath.lineTo(x, y)
                                linePath.lineTo(x, y)
                            }
                            lastX = x
                        }
                        
                        // Complete the area path
                        areaPath.lineTo(lastX, chartAreaHeight + 20f)
                        areaPath.lineTo(firstX, chartAreaHeight + 20f)
                        areaPath.close()

                        // Draw filled area with gradient
                        val gradient = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.2f),
                                primaryColor.copy(alpha = 0.05f)
                            ),
                            startY = 20f,
                            endY = chartAreaHeight + 20f
                        )
                        drawPath(path = areaPath, brush = gradient)

                        // Draw the mood line
                        drawPath(
                            path = linePath,
                            color = primaryColor,
                            style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )

                        // Draw data points with color coding and larger size
                        validData.forEachIndexed { index, dataPoint ->
                            val x = 50f + (chartAreaWidth / (validData.size - 1)) * index
                            val moodValue = dataPoint.moodLevel!!
                            val y = 20f + (chartAreaHeight * (maxMood - moodValue).toFloat() / moodRange.toFloat())

                            val pointColor = moodColors[moodValue] ?: primaryColor

                            // Draw larger point circle with mood color
                            drawCircle(
                                color = pointColor,
                                radius = 8f,
                                center = Offset(x, y)
                            )

                            // Draw white border for better visibility
                            drawCircle(
                                color = Color.White,
                                radius = 6f,
                                center = Offset(x, y),
                                style = Stroke(width = 2f)
                            )
                        }
                    } else if (validData.size == 1) {
                        // Single data point
                        val dataPoint = validData[0]
                        val x = 50f + chartAreaWidth / 2
                        val moodValue = dataPoint.moodLevel!!
                        val y = 20f + (chartAreaHeight * (maxMood - moodValue).toFloat() / moodRange.toFloat())
                        val pointColor = moodColors[moodValue] ?: primaryColor

                        drawCircle(
                            color = pointColor,
                            radius = 8f,
                            center = Offset(x, y)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 6f,
                            center = Offset(x, y),
                            style = Stroke(width = 2f)
                        )
                    }
                }
            }
            
            // Energy and Stress indicators - shown as separate cards below the chart
            if (showEnergy || showStress) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (showEnergy && validData.any { it.energyLevel != null }) {
                        val energyData = validData.filter { it.energyLevel != null }
                        val avgEnergy = energyData.mapNotNull { it.energyLevel }.average()
                        val latestEnergy = energyData.lastOrNull()?.energyLevel
                        
                        EnergyStressCard(
                            title = "Energy",
                            average = avgEnergy.toInt(),
                            latest = latestEnergy,
                            maxValue = 10,
                            color = tertiaryColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    if (showStress && validData.any { it.stressLevel != null }) {
                        val stressData = validData.filter { it.stressLevel != null }
                        val avgStress = stressData.mapNotNull { it.stressLevel }.average()
                        val latestStress = stressData.lastOrNull()?.stressLevel
                        
                        EnergyStressCard(
                            title = "Stress",
                            average = avgStress.toInt(),
                            latest = latestStress,
                            maxValue = 10,
                            color = Color(0xFFFF5722),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EnergyStressCard(
    title: String,
    average: Int,
    latest: Int?,
    maxValue: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    CoachieCard(
        modifier = modifier,
        colors = CoachieCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((average.toFloat() / maxValue).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(color)
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Avg: $average/$maxValue",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                latest?.let {
                    Text(
                        text = "Latest: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

