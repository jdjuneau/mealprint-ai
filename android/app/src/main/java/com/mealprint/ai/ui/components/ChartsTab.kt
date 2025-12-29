package com.coachie.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.mealprint.ai.ui.components.CoachieCard as Card
import com.mealprint.ai.ui.components.CoachieCardDefaults as CardDefaults
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mealprint.ai.data.model.HealthLog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Workout Calories Chart - Shows calories burned over time
 */
@Composable
fun WorkoutCaloriesChart(
    workouts: List<WorkoutCaloriesDataPoint>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Workout Calories",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )

            if (workouts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No workout data",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                ) {
                    val padding = 40.dp.toPx()
                    val chartWidth = size.width - padding * 2
                    val chartHeight = size.height - padding * 2
                    val chartTop = padding
                    val chartBottom = size.height - padding

                    val maxCalories = workouts.maxOf { it.calories }.coerceAtLeast(100.0)
                    val minCalories = 0.0

                    // Draw grid lines
                    for (i in 0..4) {
                        val y = chartTop + (chartHeight / 4) * i
                        drawLine(
                            color = surfaceVariant.copy(alpha = 0.3f),
                            start = Offset(padding, y),
                            end = Offset(size.width - padding, y),
                            strokeWidth = 1.dp.toPx()
                        )

                        // Y-axis labels
                        val calories = maxCalories - (maxCalories / 4) * i
                        val text = textMeasurer.measure("${calories.toInt()} cal")
                        drawText(
                            textLayoutResult = text,
                            topLeft = Offset(0f, y - text.size.height / 2),
                            color = onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    // Draw bars
                    val barWidth = chartWidth / workouts.size
                    
                    // Calculate how many date labels to show (max 7 to avoid overlap)
                    val maxDateLabels = minOf(7, workouts.size)
                    val dateLabelStep = if (workouts.size <= maxDateLabels) {
                        1
                    } else {
                        maxOf(1, (workouts.size - 1) / (maxDateLabels - 1))
                    }
                    
                    workouts.forEachIndexed { index, workout ->
                        val x = padding + barWidth * index
                        val barWidthPx = barWidth * 0.8f
                        val centerX = x + barWidth / 2

                        val barHeight = (workout.calories / maxCalories * chartHeight).toFloat()
                        val barTop = chartBottom - barHeight

                        // Draw bar
                        drawRect(
                            color = primaryColor.copy(alpha = 0.7f),
                            topLeft = Offset(centerX - barWidthPx / 2, barTop),
                            size = Size(barWidthPx, barHeight)
                        )

                        // Value label
                        val valueText = "${workout.calories.toInt()}"
                        val text = textMeasurer.measure(valueText)
                        drawText(
                            textLayoutResult = text,
                            topLeft = Offset(centerX - text.size.width / 2, barTop - text.size.height - 4.dp.toPx()),
                            color = primaryColor
                        )

                        // Date label - only show for every Nth point to avoid overlap
                        if (index % dateLabelStep == 0 || index == workouts.size - 1) {
                            val dateText = workout.date.format(DateTimeFormatter.ofPattern("M/d"))
                            val dateTextLayout = textMeasurer.measure(dateText)
                            drawText(
                                textLayoutResult = dateTextLayout,
                                topLeft = Offset(centerX - dateTextLayout.size.width / 2, chartBottom + 8.dp.toPx()),
                                color = onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Macros Pie Chart - Shows protein/carbs/fat distribution
 */
@Composable
fun MacrosPieChart(
    protein: Int,
    carbs: Int,
    fat: Int,
    targetProtein: Int,
    targetCarbs: Int,
    targetFat: Int,
    calorieTarget: Int,
    recommendation: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Macros Today",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            if (calorieTarget > 0) {
                Text(
                    text = "Goal: ${calorieTarget} kcal • Protein ${targetProtein}g · Carbs ${targetCarbs}g · Fat ${targetFat}g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (recommendation.isNotBlank()) {
                Text(
                    text = recommendation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Calculate calories from macros (protein and carbs: 4 cal/g, fat: 9 cal/g)
            val proteinCalories = protein * 4
            val carbsCalories = carbs * 4
            val fatCalories = fat * 9
            val totalCalories = proteinCalories + carbsCalories + fatCalories
            
            if (totalCalories == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "📊",
                            style = MaterialTheme.typography.displayMedium
                        )
                        Text(
                            text = "No macro data yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        Text(
                            text = "Log meals to see your macro breakdown",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pie chart - based on calories, not grams
                    Canvas(
                        modifier = Modifier
                            .size(200.dp)
                            .weight(1f)
                    ) {
                        val center = Offset(size.width / 2, size.height / 2)
                        val radius = (size.minDimension / 2 - 20.dp.toPx()).coerceAtLeast(0f)

                        val proteinColor = Color(0xFF4CAF50) // Green
                        val carbsColor = Color(0xFF2196F3) // Blue
                        val fatColor = Color(0xFFFF9800) // Orange

                        var startAngle = -90f // Start from top

                        // Protein - based on calories
                        val proteinAngle = (proteinCalories / totalCalories.toFloat()) * 360f
                        drawArc(
                            color = proteinColor,
                            startAngle = startAngle,
                            sweepAngle = proteinAngle,
                            useCenter = true,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2)
                        )
                        startAngle += proteinAngle

                        // Carbs - based on calories
                        val carbsAngle = (carbsCalories / totalCalories.toFloat()) * 360f
                        drawArc(
                            color = carbsColor,
                            startAngle = startAngle,
                            sweepAngle = carbsAngle,
                            useCenter = true,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2)
                        )
                        startAngle += carbsAngle

                        // Fat - based on calories
                        val fatAngle = (fatCalories / totalCalories.toFloat()) * 360f
                        drawArc(
                            color = fatColor,
                            startAngle = startAngle,
                            sweepAngle = fatAngle,
                            useCenter = true,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2, radius * 2)
                        )
                    }

                    // Legend with percentages based on calories
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val proteinPercent = ((proteinCalories / totalCalories.toFloat()) * 100).toInt()
                        val carbsPercent = ((carbsCalories / totalCalories.toFloat()) * 100).toInt()
                        val fatPercent = ((fatCalories / totalCalories.toFloat()) * 100).toInt()
                        
                        MacroLegendItemWithPercent("Protein", protein, targetProtein, "${proteinPercent}%", Color(0xFF4CAF50))
                        MacroLegendItemWithPercent("Carbs", carbs, targetCarbs, "${carbsPercent}%", Color(0xFF2196F3))
                        MacroLegendItemWithPercent("Fat", fat, targetFat, "${fatPercent}%", Color(0xFFFF9800))
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroLegendItemWithPercent(label: String, value: Int, target: Int, percent: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            if (target > 0) {
                val progressPercent = (value.toFloat() / target.toFloat()).coerceIn(0f, 2f) * 100f
                Text(
                    text = "${value}g of ${target}g • $percent",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "${value}g • $percent",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MacroLegendItem(label: String, value: Int, target: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            if (target > 0) {
                val percent = (value.toFloat() / target.toFloat()).coerceIn(0f, 2f) * 100f
                Text(
                    text = "${value}g of ${target}g (${percent.toInt()}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "${value}g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Water Streak Chart - Shows water intake streak
 */
@Composable
fun WaterStreakChart(
    waterData: List<WaterStreakDataPoint>,
    useImperial: Boolean = false,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Water Streak",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )

            if (waterData.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No water data",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                ) {
                    val padding = 40.dp.toPx()
                    val chartWidth = size.width - padding * 2
                    val chartHeight = size.height - padding * 2
                    val chartTop = padding
                    val chartBottom = size.height - padding

                    val goalMl = 2000.0 // 2L goal
                    val maxWaterMl = waterData.maxOfOrNull { it.ml }?.coerceAtLeast(goalMl) ?: goalMl

                    // Draw goal line
                    val goalY = chartBottom - (goalMl / maxWaterMl * chartHeight).toFloat()
                    drawLine(
                        color = primaryColor.copy(alpha = 0.5f),
                        start = Offset(padding, goalY),
                        end = Offset(size.width - padding, goalY),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f))
                    )

                    // Draw grid lines
                    for (i in 0..4) {
                        val y = chartTop + (chartHeight / 4) * i
                        drawLine(
                            color = surfaceVariant.copy(alpha = 0.3f),
                            start = Offset(padding, y),
                            end = Offset(size.width - padding, y),
                            strokeWidth = 1.dp.toPx()
                        )

                        // Y-axis labels
                        val ml = maxWaterMl - (maxWaterMl / 4) * i
                        val displayValue = if (useImperial) ml * 0.033814 else ml
                        val unit = if (useImperial) "oz" else "ml"
                        val text = textMeasurer.measure("${displayValue.toInt()}$unit")
                        drawText(
                            textLayoutResult = text,
                            topLeft = Offset(0f, y - text.size.height / 2),
                            color = onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    // Draw bars
                    val barWidth = chartWidth / waterData.size
                    
                    // Calculate how many date labels to show (max 7 to avoid overlap)
                    val maxDateLabels = minOf(7, waterData.size)
                    val dateLabelStep = if (waterData.size <= maxDateLabels) {
                        1
                    } else {
                        maxOf(1, (waterData.size - 1) / (maxDateLabels - 1))
                    }
                    
                    waterData.forEachIndexed { index, data ->
                        val x = padding + barWidth * index
                        val barWidthPx = barWidth * 0.8f
                        val centerX = x + barWidth / 2

                        val barHeight = (data.ml / maxWaterMl * chartHeight).toFloat()
                        val barTop = chartBottom - barHeight

                        // Color based on goal achievement
                        val barColor = if (data.ml >= goalMl) {
                            primaryColor
                        } else {
                            primaryColor.copy(alpha = 0.5f)
                        }

                        // Draw bar
                        drawRect(
                            color = barColor,
                            topLeft = Offset(centerX - barWidthPx / 2, barTop),
                            size = Size(barWidthPx, barHeight)
                        )

                        // Value label
                        val displayValue = if (useImperial) data.ml * 0.033814 else data.ml
                        val unit = if (useImperial) "oz" else "ml"
                        val valueText = "${displayValue.toInt()}$unit"
                        val text = textMeasurer.measure(valueText)
                        drawText(
                            textLayoutResult = text,
                            topLeft = Offset(centerX - text.size.width / 2, barTop - text.size.height - 4.dp.toPx()),
                            color = barColor
                        )

                        // Date label - only show for every Nth point to avoid overlap
                        if (index % dateLabelStep == 0 || index == waterData.size - 1) {
                            val dateText = data.date.format(DateTimeFormatter.ofPattern("M/d"))
                            val dateTextLayout = textMeasurer.measure(dateText)
                            drawText(
                                textLayoutResult = dateTextLayout,
                                topLeft = Offset(centerX - dateTextLayout.size.width / 2, chartBottom + 8.dp.toPx()),
                                color = onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Data classes for charts
 */
data class WorkoutCaloriesDataPoint(
    val date: LocalDate,
    val calories: Double
)

data class WaterStreakDataPoint(
    val date: LocalDate,
    val ml: Double
)
