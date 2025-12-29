package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.Circle
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.theme.Accent40
import com.coachie.app.ui.theme.Secondary40
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.ui.theme.LocalGender
import com.coachie.app.ui.theme.SemanticColorCategory
import com.coachie.app.ui.theme.getSemanticColorPrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleCreateScreen(
    onNavigateBack: () -> Unit,
    onCreateSuccess: () -> Unit,
    userId: String
) {
    val repository = FirebaseRepository.getInstance()
    var circleName by remember { mutableStateOf("") }
    var circleGoal by remember { mutableStateOf("") }
    var maxMembers by remember { mutableStateOf(5) }
    var isCreating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    
    // Get semantic color for Community (purple theme)
    val gender = LocalGender.current
    val isMale = gender.lowercase() == "male"
    val communityColor = getSemanticColorPrimary(SemanticColorCategory.COMMUNITY, isMale)

    fun handleCreateCircle() {
        if (circleName.isBlank() || circleGoal.isBlank()) {
            errorMessage = "Please fill in all fields"
            return
        }

        scope.launch {
            isCreating = true
            errorMessage = null

            val circle = Circle(
                id = "", // Will be set by Firestore
                name = circleName.trim(),
                goal = circleGoal.trim(),
                members = listOf(userId),
                streak = 0,
                createdBy = userId,
                tendency = null,
                maxMembers = maxMembers,
                createdAt = java.util.Date(),
                updatedAt = java.util.Date()
            )

            val result = repository.createCircle(circle)
            
            result.onSuccess { circleId ->
                // Add circle to user's circles list (user is already in members from creation)
                val addToUserResult = repository.addCircleToUser(userId, circleId)
                addToUserResult.onSuccess {
                    android.util.Log.d("CircleCreate", "Circle created and added to user successfully: $circleId")
                    isCreating = false
                    onCreateSuccess()
                }.onFailure { error ->
                    android.util.Log.e("CircleCreate", "Failed to add circle to user, but circle was created", error)
                    // Circle was created, so still navigate back
                    isCreating = false
                    onCreateSuccess()
                }
            }.onFailure { error ->
                android.util.Log.e("CircleCreate", "Failed to create circle", error)
                isCreating = false
                errorMessage = error.message ?: "Failed to create circle"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBackground)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CoachieCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CoachieCardDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.95f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                "Back",
                                tint = communityColor
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Create Circle",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = communityColor
                        )
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CoachieCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CoachieCardDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.95f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Create Your Circle",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "Start a circle to connect with others working toward similar goals!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Circle Name
                        OutlinedTextField(
                            value = circleName,
                            onValueChange = { circleName = it },
                            label = { Text("Circle Name") },
                            placeholder = { Text("e.g., Morning Runners") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isCreating
                        )

                        // Circle Goal
                        OutlinedTextField(
                            value = circleGoal,
                            onValueChange = { circleGoal = it },
                            label = { Text("Goal") },
                            placeholder = { Text("e.g., Run a 5K") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isCreating
                        )

                        // Max Members
                        Column {
                            Text(
                                "Maximum Members: $maxMembers",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = maxMembers.toFloat(),
                                onValueChange = { maxMembers = it.toInt() },
                                valueRange = 2f..10f,
                                steps = 7,
                                enabled = !isCreating
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("2", style = MaterialTheme.typography.bodySmall)
                                Text("10", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        // Error message
                        errorMessage?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        // Create button
                        Button(
                            onClick = { handleCreateCircle() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isCreating && circleName.isNotBlank() && circleGoal.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = communityColor
                            )
                        ) {
                            if (isCreating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Creating...")
                            } else {
                                Text("Create Circle")
                            }
                        }
                    }
                }
            }
        }
    }
}

