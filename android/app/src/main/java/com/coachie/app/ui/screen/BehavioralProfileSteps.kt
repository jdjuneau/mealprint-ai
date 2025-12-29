package com.coachie.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coachie.app.data.model.FourTendencies
import com.coachie.app.data.model.RewardType
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.viewmodel.StepData

@Composable
fun FourTendenciesStepContent(
    stepData: StepData.FourTendenciesStep,
    onAnswerSelected: (Int, FourTendencies) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Header
        Text(
            text = "🧠 Understanding Your Motivation Style",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Everyone has a unique way of staying motivated and meeting expectations. Answer these questions to discover your behavioral tendencies:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // Current result
        stepData.result?.let { result ->
            CoachieCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CoachieCardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Your Tendency: ${result.tendency.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = getTendencyDescription(result.tendency),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // Questions
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(stepData.questions) { questionIndex, (question, options) ->
                CoachieCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "${questionIndex + 1}. $question",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )

                        options.forEach { (optionText, tendency) ->
                            val isSelected = stepData.currentAnswers[questionIndex.toString()] == tendency.ordinal

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAnswerSelected(questionIndex, tendency) }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = optionText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )

                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (options.last() != Pair(optionText, tendency)) {
                                Divider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RewardPreferencesStepContent(
    stepData: StepData.RewardPreferencesStep,
    onRewardToggle: (RewardType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Header
        Text(
            text = "🎁 What Motivates You?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Different people are motivated by different types of rewards. Select all that resonate with you:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // Reward options
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            stepData.allRewards.forEach { reward ->
                val isSelected = stepData.selectedRewards.contains(reward)

                CoachieCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRewardToggle(reward) },
                    colors = CoachieCardDefaults.cardColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = getRewardDisplayName(reward),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = getRewardDescription(reward),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isSelected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Selection summary
        if (stepData.selectedRewards.isNotEmpty()) {
            CoachieCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CoachieCardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = "Selected ${stepData.selectedRewards.size} reward type(s). Coachie will use these to motivate you!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun KeystoneHabitsStepContent(
    stepData: StepData.KeystoneHabitsStep,
    onAddHabit: (String) -> Unit,
    onRemoveHabit: (String) -> Unit,
    onCustomHabitChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Header
        Text(
            text = "🏗️ Keystone Habits",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Keystone habits are powerful routines that, when established, help other habits fall into place naturally. What habits have this effect on your life?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // Suggestions
        CoachieCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "💡 Suggested Keystone Habits:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                val suggestions = listOf(
                    "Exercise regularly",
                    "Meal planning/preparation",
                    "Meditation or mindfulness",
                    "Reading before bed",
                    "Journaling",
                    "Morning routine",
                    "Evening wind-down"
                )

                suggestions.forEach { suggestion ->
                    AssistChip(
                        onClick = { onAddHabit(suggestion) },
                        label = { Text(suggestion) },
                        modifier = Modifier.padding(end = 8.dp, bottom = 4.dp)
                    )
                }
            }
        }

        // Current habits
        if (stepData.habits.isNotEmpty()) {
            CoachieCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Your Keystone Habits:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )

                    stepData.habits.forEach { habit ->
                        AssistChip(
                            onClick = { onRemoveHabit(habit) },
                            label = { Text(habit) },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }

        // Custom habit input
        CoachieCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add Custom Keystone Habit:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                OutlinedTextField(
                    value = stepData.customHabit,
                    onValueChange = onCustomHabitChange,
                    label = { Text("Describe your keystone habit...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Button(
                    onClick = { onAddHabit(stepData.customHabit) },
                    enabled = stepData.customHabit.isNotBlank(),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Add Habit")
                }
            }
        }
    }
}

@Composable
fun FrictionsStepContent(
    stepData: StepData.FrictionsStep,
    onFrictionToggle: (String) -> Unit,
    onCustomFrictionChange: (String) -> Unit,
    onAddCustomFriction: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Header
        Text(
            text = "🚧 Biggest Challenges",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Understanding your biggest barriers helps Coachie provide better strategies and support. What consistently prevents you from building habits?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // Predefined frictions
        CoachieCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Common Challenges:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                stepData.predefinedFrictions.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { friction ->
                            val isSelected = stepData.selectedFrictions.contains(friction)

                            FilterChip(
                                selected = isSelected,
                                onClick = { onFrictionToggle(friction) },
                                label = { Text(friction) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Selected frictions
        if (stepData.selectedFrictions.isNotEmpty()) {
            CoachieCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Your Challenges:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )

                    stepData.selectedFrictions.forEach { friction ->
                        AssistChip(
                            onClick = { onFrictionToggle(friction) },
                            label = { Text(friction) },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        )
                    }
                }
            }
        }

        // Custom friction input
        CoachieCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add Custom Challenge:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                OutlinedTextField(
                    value = stepData.customFriction,
                    onValueChange = onCustomFrictionChange,
                    label = { Text("Describe your specific challenge...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                Button(
                    onClick = onAddCustomFriction,
                    enabled = stepData.customFriction.isNotBlank(),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Add Challenge")
                }
            }
        }
    }
}

// Helper functions
private fun getTendencyDescription(tendency: FourTendencies): String {
    return when (tendency) {
        FourTendencies.UPHOLDER -> "You meet both inner and outer expectations. You follow through on commitments and maintain high standards."
        FourTendencies.QUESTIONER -> "You need to understand the 'why' behind everything. You resist arbitrary rules but follow those that make sense."
        FourTendencies.OBLIGER -> "You excel at meeting others' expectations but struggle with self-directed goals. External accountability is key."
        FourTendencies.REBEL -> "You resist external control and prefer to do things your own way. Inner freedom and choice drive you."
    }
}

private fun getRewardDisplayName(reward: RewardType): String {
    return when (reward) {
        RewardType.ACHIEVEMENT_BADGE -> "Achievement Badges 🏆"
        RewardType.SOCIAL_RECOGNITION -> "Social Recognition 👏"
        RewardType.PERSONAL_GROWTH -> "Personal Growth 📈"
        RewardType.MATERIAL_REWARD -> "Material Rewards 🎁"
        RewardType.EXPERIENCE_REWARD -> "Experiences 🌟"
        RewardType.COMPETITION -> "Competition 🏁"
        RewardType.NONE -> "No Rewards 🙅‍♂️"
    }
}

private fun getRewardDescription(reward: RewardType): String {
    return when (reward) {
        RewardType.ACHIEVEMENT_BADGE -> "Badges, trophies, certificates, and visible progress markers"
        RewardType.SOCIAL_RECOGNITION -> "Praise, acknowledgment, and sharing achievements with others"
        RewardType.PERSONAL_GROWTH -> "Learning new skills, self-improvement, and personal development"
        RewardType.MATERIAL_REWARD -> "Tangible rewards like treats, purchases, or special items"
        RewardType.EXPERIENCE_REWARD -> "New experiences, adventures, or memorable activities"
        RewardType.COMPETITION -> "Leaderboards, challenges, and friendly competition"
        RewardType.NONE -> "No external rewards needed - intrinsic motivation is enough"
    }
}
