package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import com.coachie.app.viewmodel.BehavioralProfileViewModel
import com.coachie.app.viewmodel.StepData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BehavioralProfileScreen(
    onComplete: () -> Unit,
    onBack: () -> Unit,
    viewModel: BehavioralProfileViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val gradientBackground = rememberCoachieGradient(endY = 1600f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Behavioral Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
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
            when {
                uiState.profileCompleted -> {
                    CompletionScreen(onComplete = onComplete)
                }
                else -> {
                    ProfileSetupContent(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun ProfileSetupContent(viewModel: BehavioralProfileViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val stepData = viewModel.getCurrentStepData()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Progress indicator
        LinearProgressIndicator(
            progress = (uiState.currentStep + 1).toFloat() / uiState.totalSteps.toFloat(),
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )

        // Step counter
        Text(
            text = "Step ${uiState.currentStep + 1} of ${uiState.totalSteps}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // Step content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (stepData) {
                is StepData.FourTendenciesStep -> {
                    FourTendenciesStepContent(
                        stepData = stepData,
                        onAnswerSelected = { questionIndex, tendency ->
                            viewModel.answerFourTendenciesQuestion(questionIndex, tendency)
                        }
                    )
                }
                is StepData.RewardPreferencesStep -> {
                    RewardPreferencesStepContent(
                        stepData = stepData,
                        onRewardToggle = { reward ->
                            viewModel.toggleRewardPreference(reward)
                        }
                    )
                }
                is StepData.KeystoneHabitsStep -> {
                    KeystoneHabitsStepContent(
                        stepData = stepData,
                        onAddHabit = { viewModel.addKeystoneHabit(it) },
                        onRemoveHabit = { viewModel.removeKeystoneHabit(it) },
                        onCustomHabitChange = { viewModel.updateCustomKeystoneHabit(it) }
                    )
                }
                is StepData.FrictionsStep -> {
                    FrictionsStepContent(
                        stepData = stepData,
                        onFrictionToggle = { viewModel.toggleFriction(it) },
                        onCustomFrictionChange = { viewModel.updateCustomFriction(it) },
                        onAddCustomFriction = { viewModel.addCustomFriction() }
                    )
                }
            }
        }

        // Error message
        uiState.error?.let { error ->
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

        // Navigation buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Show helpful message if button is disabled
            val canProceed = if (uiState.currentStep < uiState.totalSteps - 1) {
                canProceedToNextStep(stepData)
            } else {
                canCompleteProfile(stepData)
            }
            
            if (!canProceed) {
                val message = when (stepData) {
                    is StepData.FourTendenciesStep -> {
                        val answeredCount = stepData.currentAnswers.size
                        "Please answer all 5 questions ($answeredCount/5 answered)"
                    }
                    is StepData.RewardPreferencesStep -> "Please select at least one reward preference"
                    is StepData.KeystoneHabitsStep -> "Please add at least one keystone habit"
                    is StepData.FrictionsStep -> "Please select at least one challenge"
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (uiState.currentStep > 0) {
                    OutlinedButton(
                        onClick = { viewModel.previousStep() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Previous")
                    }
                }

                if (uiState.currentStep < uiState.totalSteps - 1) {
                    Button(
                        onClick = { 
                            if (canProceed) {
                                viewModel.nextStep()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = canProceed
                    ) {
                        Text("Next")
                    }
                } else {
                    Button(
                        onClick = { viewModel.saveProfile() },
                        modifier = Modifier.weight(1f),
                        enabled = canProceed
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Complete Profile")
                        }
                    }
                }
            }
        }
    }
}

private fun canProceedToNextStep(stepData: StepData): Boolean {
    return when (stepData) {
        is StepData.FourTendenciesStep -> {
            // Check if all 5 questions are answered (indices 0-4)
            val allQuestionsAnswered = (0 until 5).all { index ->
                stepData.currentAnswers.containsKey(index.toString())
            }
            allQuestionsAnswered && stepData.result != null
        }
        is StepData.RewardPreferencesStep -> stepData.selectedRewards.isNotEmpty()
        is StepData.KeystoneHabitsStep -> stepData.habits.isNotEmpty()
        is StepData.FrictionsStep -> stepData.selectedFrictions.isNotEmpty()
    }
}

private fun canCompleteProfile(stepData: StepData): Boolean {
    return when (stepData) {
        is StepData.FrictionsStep -> stepData.selectedFrictions.isNotEmpty()
        else -> false
    }
}

@Composable
private fun CompletionScreen(onComplete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Success icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = androidx.compose.foundation.shape.CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Profile Complete",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(60.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "🎉 Profile Complete!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Your behavioral profile has been saved. Coachie now understands your habits, motivations, and challenges. You'll receive personalized habit suggestions tailored just for you!",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Get Started with Habits!")
        }
    }
}
