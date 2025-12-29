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
 * Data class for journal chart data points
 */
data class JournalDataPoint(
    val date: LocalDate,
    val wordCount: Int = 0,
    val isCompleted: Boolean = false,
    val entryCount: Int = 0 // Number of journal entries on this date
)

/**
 * Journal entries chart showing journaling activity over time
 */
@Composable
fun JournalChart(
    journalData: List<JournalDataPoint>,
    modifier: Modifier = Modifier
) {
    // Filter out dates with no data and sort by date
    val validData = journalData.filter { it.entryCount > 0 || it.wordCount > 0 }.sortedBy { it.date }

    if (validData.isEmpty()) {
        EmptyChart(
            message = "No journal entries available\nStart journaling to see your progress!",
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
    val maxWordCount = validData.maxOfOrNull { it.wordCount }?.coerceAtLeast(100) ?: 500
    val maxCount = validData.maxOfOrNull { it.entryCount }?.coerceAtLeast(1) ?: 1

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
                text = "Journal Word Count Over Time",
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
                        text = "Completed Entry",
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
                        text = "In Progress",
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

            // Horizontal grid lines (word count values)
            for (i in 0..4) {
                val y = (chartAreaHeight / 4) * i + 20f
                drawLine(
                    color = onSurfaceColor.copy(alpha = 0.1f),
                    start = Offset(50f, y),
                    end = Offset(50f + chartAreaWidth, y),
                    strokeWidth = 1f
                )

                // Word count labels on left (max to 0, top to bottom)
                val wordValue = maxWordCount - (maxWordCount / 4) * i
                val wordText = if (wordValue >= 1000) "${wordValue / 1000}k" else "$wordValue"
                val textLayoutResult = textMeasurer.measure(wordText, TextStyle(fontSize = 10.sp))
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

            // Draw journal word count bars
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

                    val wordCount = dataPoint.wordCount
                    if (wordCount > 0) {
                        val barHeight = (chartAreaHeight * wordCount.toFloat() / maxWordCount.toFloat())
                        val barY = 20f + chartAreaHeight - barHeight

                        // Draw bar with color based on completion status
                        val barColor = if (dataPoint.isCompleted) {
                            tertiaryColor
                        } else {
                            primaryColor.copy(alpha = 0.7f)
                        }

                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x, barY),
                            size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
                        )

                        // Draw completion indicator
                        if (dataPoint.isCompleted) {
                            // Small checkmark indicator at top
                            val checkY = barY - 8f
                            drawCircle(
                                color = Color(0xFF4CAF50),
                                radius = 4f,
                                center = Offset(x + barWidth / 2, checkY)
                            )
                        }
                    }
                }
            }

            // Draw current/latest journal indicator
            validData.lastOrNull()?.let { latest ->
                if (latest.wordCount > 0) {
                    val x = if (dataPoints > 1) {
                        50f + chartAreaWidth
                    } else {
                        50f + chartAreaWidth / 2
                    }
                    val barHeight = (chartAreaHeight * latest.wordCount.toFloat() / maxWordCount.toFloat())
                    val y = 20f + chartAreaHeight - barHeight

                    val statusText = if (latest.isCompleted) "✓ Completed" else "In Progress"
                    val currentText = "Latest: ${latest.wordCount} words ($statusText)"
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

