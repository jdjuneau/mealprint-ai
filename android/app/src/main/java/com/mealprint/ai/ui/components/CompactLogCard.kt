package com.coachie.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.mealprint.ai.ui.components.CoachieCard as Card
import com.mealprint.ai.ui.components.CoachieCardDefaults as CardDefaults
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.viewmodel.CompactLogCardViewModel

/**
 * Compact card for quick daily logging in HomeScreen
 */
@Composable
fun CompactLogCard(
    userId: String,
    modifier: Modifier = Modifier,
    viewModel: CompactLogCardViewModel = viewModel(
        factory = CompactLogCardViewModel.Factory(
            repository = FirebaseRepository.getInstance(),
            userId = userId
        )
    )
) {
    val todayLog by viewModel.todayLog.collectAsState()
    val streak by viewModel.streak.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val useImperial by viewModel.useImperial.collectAsState()

    val currentWater = todayLog?.water ?: 0
    val currentMood = todayLog?.mood
    val currentEnergy = todayLog?.energy

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Streak display
            streak?.let { s ->
                if (s.currentStreak > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔥 ${s.currentStreak} days logging!",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Divider()
                }
            }

            // Water section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Water",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (useImperial) {
                            val waterOz = (currentWater * 0.033814).roundToInt()
                            val goalOz = (2000 * 0.033814).roundToInt()
                            "$waterOz / $goalOz fl oz"
                        } else {
                            "$currentWater / 2,000 ml"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Progress bar
                LinearProgressIndicator(
                    progress = { viewModel.waterProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                // Add water button
                Button(
                    onClick = { viewModel.addWater() },
                    enabled = !isSaving && currentWater < 2000,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+250ml")
                }
            }

            HorizontalDivider()

            // Mood section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Mood",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val moods = listOf(
                        "😢" to 1,
                        "🙁" to 2,
                        "😐" to 3,
                        "🙂" to 4,
                        "😀" to 5
                    )

                    moods.forEach { (emoji, value) ->
                        val isSelected = currentMood == value
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setMood(value) },
                            label = { 
                                Text(
                                    text = emoji,
                                    fontSize = 24.sp
                                )
                            },
                            enabled = !isSaving
                        )
                    }
                }
            }

            HorizontalDivider()

            // Energy section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Energy",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = currentEnergy?.let { "$it/5" } ?: "Not set",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Slider(
                    value = currentEnergy?.toFloat() ?: 3f,
                    onValueChange = { viewModel.setEnergy(it.toInt()) },
                    valueRange = 1f..5f,
                    steps = 3,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Low",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "High",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
