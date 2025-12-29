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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.mealprint.ai.ui.components.EmptyChart

/**
 * Data class for meditation chart data points
 */
data class MeditationDataPoint(
    val date: LocalDate,
    val durationMinutes: Int? = null,
    val count: Int = 0, // Number of sessions on this date
    val moodImprovement: Int? = null // moodAfter - moodBefore (if available)
)

/**
 * Meditation chart showing meditation sessions over time
 */
@Composable
fun MeditationChart(
    meditationData: List<MeditationDataPoint>,
    modifier: Modifier = Modifier
) {
    // Filter out dates with no data and sort by date
    val validData = meditationData.filter { it.count > 0 || it.durationMinutes != null }.sortedBy { it.date }

    if (validData.isEmpty()) {
        EmptyChart(
            message = "No meditation data available\nStart meditating to see your progress!",
            modifier = modifier
        )
        return
    }

    val textMeasurer = rememberTextMeasurer()

    // Extract theme colors
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    // Calculate chart bounds
    val maxDuration = validData.maxOfOrNull { it.durationMinutes ?: 0 }?.coerceAtLeast(10) ?: 30
    val maxCount = validData.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Chart title and legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Meditation Duration Over Time",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = onSurfaceColor
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(tertiaryColor, RoundedCornerShape(2.dp))
                    )
                    Text(
                        text = "20+ min",
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVariantColor,
                        fontSize = 10.sp
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(primaryColor, RoundedCornerShape(2.dp))
                    )
                    Text(
                        text = "10-19 min",
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVariantColor,
                        fontSize = 10.sp
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(primaryColor.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                    )
                    Text(
                        text = "<10 min",
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVariantColor,
                        fontSize = 10.sp
                    )
                }
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(surfaceColor)
                .padding(16.dp)
        ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val chartAreaHeight = canvasHeight - 60f // Leave space for labels
            val chartAreaWidth = canvasWidth - 60f // Leave space for left labels

            // Horizontal grid lines (duration values)
            for (i in 0..4) {
                val y = (chartAreaHeight / 4) * i + 20f
                drawLine(
                    color = onSurfaceColor.copy(alpha = 0.1f),
                    start = Offset(50f, y),
                    end = Offset(50f + chartAreaWidth, y),
                    strokeWidth = 1f
                )

                // Duration labels on left (max to 0, top to bottom)
                val durationValue = maxDuration - (maxDuration / 4) * i
                val durationText = "${durationValue}min"
                val textLayoutResult = textMeasurer.measure(durationText, TextStyle(fontSize = 10.sp))
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

            // Draw meditation duration bars
            if (validData.isNotEmpty()) {
                val barWidth = if (dataPoints > 1) {
                    (chartAreaWidth / dataPoints) * 0.6f // 60% of available space
                } else {
                    chartAreaWidth * 0.6f
                }

                validData.forEachIndexed { index, dataPoint ->
                    val x = if (dataPoints > 1) {
                        50f + (chartAreaWidth / (dataPoints - 1)) * index - barWidth / 2
                    } else {
                        50f + chartAreaWidth / 2 - barWidth / 2
                    }

                    val duration = dataPoint.durationMinutes ?: 0
                    if (duration > 0) {
                        val barHeight = (chartAreaHeight * duration.toFloat() / maxDuration.toFloat())
                        val barY = 20f + chartAreaHeight - barHeight

                        // Draw bar with gradient color based on duration
                        val barColor = when {
                            duration >= 20 -> tertiaryColor
                            duration >= 10 -> primaryColor
                            else -> primaryColor.copy(alpha = 0.7f)
                        }

                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x, barY),
                            size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
                        )

                        // Draw count indicator on top if multiple sessions
                        if (dataPoint.count > 1) {
                            val countText = "${dataPoint.count}x"
                            val countTextLayout = textMeasurer.measure(countText, TextStyle(fontSize = 8.sp))
                            drawText(
                                countTextLayout,
                                color = onSurfaceVariantColor,
                                topLeft = Offset(x + barWidth / 2 - countTextLayout.size.width / 2, barY - countTextLayout.size.height - 2f)
                            )
                        }
                    }
                }
            }

            // Draw current/latest meditation indicator
            validData.lastOrNull()?.let { latest ->
                latest.durationMinutes?.let { duration ->
                    val x = if (dataPoints > 1) {
                        50f + chartAreaWidth
                    } else {
                        50f + chartAreaWidth / 2
                    }
                    val barHeight = (chartAreaHeight * duration.toFloat() / maxDuration.toFloat())
                    val y = 20f + chartAreaHeight - barHeight

                    val currentText = "Latest: ${duration}min"
                    val textLayoutResult = textMeasurer.measure(currentText, TextStyle(fontSize = 11.sp))

                    // Background for text
                    drawRoundRect(
                        color = surfaceVariantColor,
                        topLeft = Offset(x - textLayoutResult.size.width - 10f, y - textLayoutResult.size.height - 5f),
                        size = androidx.compose.ui.geometry.Size(
                            textLayoutResult.size.width + 20f,
                            textLayoutResult.size.height + 10f
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
                    )

                    drawText(
                        textLayoutResult,
                        color = onSurfaceVariantColor,
                        topLeft = Offset(x - textLayoutResult.size.width - 5f, y - textLayoutResult.size.height)
                    )
                }
            }
        }
        }
    }
}

