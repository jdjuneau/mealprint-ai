package com.coachie.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
/**
 * Data class for sleep chart data points
 */
data class SleepDataPoint(
    val date: java.time.LocalDate,
    val hours: Double?,
    val quality: Int? = null
)

@Composable
fun SleepChart(
    data: List<SleepDataPoint>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(vertical = 8.dp)
    ) {
        if (data.isEmpty()) return@Canvas

        val padding = 40.dp.toPx()
        val chartWidth = size.width - padding * 2
        val chartHeight = size.height - padding * 2
        val chartTop = padding
        val chartBottom = size.height - padding

        // Find max sleep duration for scaling
        val maxHours = data.mapNotNull { it.hours }.maxOrNull() ?: 10.0
        val maxY = maxHours.coerceAtLeast(8.0) // Minimum 8 hours for scale

        // Draw grid lines
        val gridLineColor = surfaceVariant.copy(alpha = 0.3f)
        for (i in 0..4) {
            val y = chartTop + (chartHeight / 4) * i
            drawLine(
                color = gridLineColor,
                start = Offset(padding, y),
                end = Offset(size.width - padding, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Draw Y-axis labels (hours)
        for (i in 0..4) {
            val hours = maxY - (maxY / 4) * i
            val y = chartTop + (chartHeight / 4) * i
            val text = textMeasurer.measure("${hours.toInt()}h")
            drawText(
                textLayoutResult = text,
                topLeft = Offset(0f, y - text.size.height / 2),
                color = onSurfaceVariant.copy(alpha = 0.6f)
            )
        }

        // Draw X-axis labels (dates) - only show every other date to prevent overlap
        val barWidth = chartWidth / data.size
        val dateStep = when {
            data.size <= 3 -> 1  // Show all dates for small datasets
            data.size <= 7 -> 2  // Show every other date for weekly data
            else -> 3            // Show every third date for larger datasets
        }

        data.forEachIndexed { index, point ->
            if (index % dateStep == 0) {  // Only draw every nth date
                val x = padding + barWidth * index + barWidth / 2
                val dateText = point.date.format(java.time.format.DateTimeFormatter.ofPattern("MM/dd"))
                val text = textMeasurer.measure(dateText)
                drawText(
                    textLayoutResult = text,
                    topLeft = Offset(x - text.size.width / 2, chartBottom + 8.dp.toPx()),
                    color = onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        // Draw bars
        data.forEachIndexed { index, point ->
            val x = padding + barWidth * index
            val barWidthPx = barWidth * 0.8f // 80% width for spacing
            val centerX = x + barWidth / 2

            if (point.hours != null) {
                val barHeight = (point.hours / maxY * chartHeight).toFloat()
                val barTop = chartBottom - barHeight

                // Draw bar
                drawRect(
                    color = primaryColor.copy(alpha = 0.7f),
                    topLeft = Offset(centerX - barWidthPx / 2, barTop),
                    size = Size(barWidthPx, barHeight)
                )

                // Draw value label on top of bar
                val valueText = String.format("%.1f", point.hours)
                val text = textMeasurer.measure(valueText)
                drawText(
                    textLayoutResult = text,
                    topLeft = Offset(centerX - text.size.width / 2, barTop - text.size.height - 4.dp.toPx()),
                    color = primaryColor
                )
            } else {
                // Draw placeholder for missing data
                drawLine(
                    color = onSurfaceVariant.copy(alpha = 0.3f),
                    start = Offset(centerX - barWidthPx / 2, chartBottom),
                    end = Offset(centerX + barWidthPx / 2, chartBottom),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}
