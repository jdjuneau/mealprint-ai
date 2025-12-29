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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Data class for weight chart data points
 */
data class WeightDataPoint(
    val date: LocalDate,
    val weight: Double?
)

/**
 * Weight trend chart using Compose Canvas
 */
@Composable
fun WeightChart(
    weightData: List<WeightDataPoint>,
    goalWeight: Double? = null,
    useImperial: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Filter out null weights and sort by date
    val validData = weightData.filter { it.weight != null }.sortedBy { it.date }

    if (validData.isEmpty()) {
        EmptyChart(
            message = "No weight data available\nStart logging your weight to see trends!",
            modifier = modifier
        )
        return
    }

    // Calculate chart bounds
    val minWeight = validData.minOf { it.weight!! } - 1.0
    val maxWeight = validData.maxOf { it.weight!! } + 1.0

    // Include goal line in range if provided
    val chartMin = goalWeight?.let { minOf(minWeight, it - 0.5) } ?: minWeight
    val chartMax = goalWeight?.let { maxOf(maxWeight, it + 0.5) } ?: maxWeight
    val chartRange = chartMax - chartMin

    val textMeasurer = rememberTextMeasurer()

    // Extract theme colors outside Canvas
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(surfaceColor)
            .padding(16.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val chartAreaHeight = canvasHeight - 40f // Leave space for bottom labels
            val chartAreaWidth = canvasWidth - 60f // Leave space for left labels

            // Horizontal grid lines (weight values)
            for (i in 0..4) {
                val y = (chartAreaHeight / 4) * i + 20f
                drawLine(
                    color = onSurfaceColor.copy(alpha = 0.1f),
                    start = Offset(50f, y),
                    end = Offset(50f + chartAreaWidth, y),
                    strokeWidth = 1f
                )

                // Weight labels on left
                val weightValueKg = chartMax - (chartRange / 4) * i
                val weightValue = if (useImperial) weightValueKg * 2.205 else weightValueKg
                val unit = if (useImperial) "lbs" else "kg"
                val weightText = String.format("%.1f%s", weightValue, unit)
                val textLayoutResult = textMeasurer.measure(weightText, TextStyle(fontSize = 10.sp))
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
                for (i in 0 until dataPoints step maxOf(1, dataPoints / 7)) { // Show ~7 date labels
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

            // Draw goal line if provided
            goalWeight?.let { goalKg ->
                val goalY = 20f + (chartAreaHeight * (chartMax - goalKg).toFloat() / chartRange.toFloat())
                drawLine(
                    color = primaryColor,
                    start = Offset(50f, goalY),
                    end = Offset(50f + chartAreaWidth, goalY),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f))
                )

                // Goal line label
                val goalValue = if (useImperial) goalKg * 2.205 else goalKg
                val unit = if (useImperial) "lbs" else "kg"
                val goalText = "Goal: ${String.format("%.1f%s", goalValue, unit)}"
                val textLayoutResult = textMeasurer.measure(goalText, TextStyle(fontSize = 12.sp))
                drawText(
                    textLayoutResult,
                    color = primaryColor,
                    topLeft = Offset(55f, goalY - textLayoutResult.size.height - 5f)
                )
            }

            // Draw weight trend line
            if (validData.size > 1) {
                val path = Path()
                var firstPoint = true

                validData.forEachIndexed { index, dataPoint ->
                    val x = 50f + (chartAreaWidth / (validData.size - 1)) * index
                    val y = 20f + (chartAreaHeight * (chartMax - dataPoint.weight!!).toFloat() / chartRange.toFloat())

                    if (firstPoint) {
                        path.moveTo(x, y)
                        firstPoint = false
                    } else {
                        path.lineTo(x, y)
                    }
                }

                // Draw the line
                drawPath(
                    path = path,
                    color = secondaryColor,
                    style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Draw data points
                validData.forEachIndexed { index, dataPoint ->
                    val x = 50f + (chartAreaWidth / (validData.size - 1)) * index
                    val y = 20f + (chartAreaHeight * (chartMax - dataPoint.weight!!).toFloat() / chartRange.toFloat())

                    // Draw point circle
                    drawCircle(
                        color = secondaryColor,
                        radius = 4f,
                        center = Offset(x, y)
                    )

                    // Draw white inner circle for better visibility
                    drawCircle(
                        color = Color.White,
                        radius = 2f,
                        center = Offset(x, y)
                    )
                }
            }

            // Draw current weight indicator
            validData.lastOrNull()?.let { latest ->
                latest.weight?.let { weightKg ->
                    val x = 50f + chartAreaWidth
                    val y = 20f + (chartAreaHeight * (chartMax - weightKg).toFloat() / chartRange.toFloat())

                    val currentValue = if (useImperial) weightKg * 2.205 else weightKg
                    val unit = if (useImperial) "lbs" else "kg"
                    val currentText = "Current: ${String.format("%.1f%s", currentValue, unit)}"
                    val textLayoutResult = textMeasurer.measure(currentText, TextStyle(fontSize = 12.sp))

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

@Composable
fun EmptyChart(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/**
 * Sample data for testing the chart
 */
fun createSampleWeightData(): List<WeightDataPoint> {
    val today = LocalDate.now()
    return (0..29).map { daysAgo ->
        val date = today.minusDays(daysAgo.toLong())
        val baseWeight = 70.0
        val trend = daysAgo * -0.1 // Losing 0.1kg per day trend
        val noise = (Math.random() - 0.5) * 2.0 // Random noise ±1kg
        val weight = baseWeight + trend + noise

        WeightDataPoint(
            date = date,
            weight = if (daysAgo < 25) weight else null // Some missing data
        )
    }.reversed() // Oldest to newest
}
