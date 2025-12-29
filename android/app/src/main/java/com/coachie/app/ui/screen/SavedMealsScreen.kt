package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.PublicUserProfile
import com.coachie.app.data.model.SavedMeal
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.components.ShareDialog
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.viewmodel.SavedMealsUiState
import com.coachie.app.viewmodel.SavedMealsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedMealsScreen(
    userId: String,
    onNavigateBack: () -> Unit,
    onMealSelected: (SavedMeal) -> Unit,
    firebaseRepository: FirebaseRepository = FirebaseRepository.getInstance()
) {
    val viewModel: SavedMealsViewModel = viewModel(
        factory = SavedMealsViewModel.provideFactory(firebaseRepository, userId)
    )

    val uiState by viewModel.uiState.collectAsState()

    // Share dialog state
    var showShareDialog by rememberSaveable { mutableStateOf(false) }
    var friends by remember { mutableStateOf<List<PublicUserProfile>>(emptyList()) }
    var selectedFriends by remember { mutableStateOf<Set<String>>(emptySet()) }
    var mealToShare by remember { mutableStateOf<SavedMeal?>(null) }

    val context = LocalContext.current
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    
    // Load friends when share dialog opens
    LaunchedEffect(showShareDialog) {
        if (showShareDialog) {
            firebaseRepository.getFriends(userId).fold(
                onSuccess = { friendsList ->
                    friends = friendsList
                },
                onFailure = {}
            )
        }
    }
    
    // Handle share success
    LaunchedEffect(uiState) {
        if (uiState is SavedMealsUiState.ShareSuccess) {
            val message = (uiState as SavedMealsUiState.ShareSuccess).message
            android.widget.Toast.makeText(
                context,
                message,
                android.widget.Toast.LENGTH_SHORT
            ).show()
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
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Saved Meals",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
            when (uiState) {
                is SavedMealsUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is SavedMealsUiState.ShareSuccess -> {
                    // Share success is handled by LaunchedEffect, just show success state
                    // Reload meals to show updated sharing status
                    LaunchedEffect(Unit) {
                        viewModel.loadSavedMeals()
                    }
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is SavedMealsUiState.Success -> {
                    val meals = (uiState as SavedMealsUiState.Success).meals

                    if (meals.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "No saved meals yet",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Meals you save will appear here for quick re-selection",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(meals) { meal ->
                                CoachieCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CoachieCardDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                                    )
                                ) {
                                    SavedMealCard(
                                        meal = meal,
                                        onMealSelected = {
                                            viewModel.onMealSelected(meal)
                                            onMealSelected(meal)
                                        },
                                        onDeleteMeal = { viewModel.deleteMeal(meal) },
                                        onShare = {
                                            mealToShare = meal
                                            showShareDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                is SavedMealsUiState.Error -> {
                    val error = (uiState as SavedMealsUiState.Error).message

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Error loading saved meals",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadSavedMeals() }) {
                            Text("Retry")
                        }
                    }
                }
            }
            }

            // Share Dialog
            if (showShareDialog && mealToShare != null) {
                ShareDialog(
                    title = "Share Saved Meal: ${mealToShare!!.name}",
                    friends = friends,
                    selectedFriends = selectedFriends,
                    onFriendToggle = { friendId ->
                        selectedFriends = if (selectedFriends.contains(friendId)) {
                            selectedFriends - friendId
                        } else {
                            selectedFriends + friendId
                        }
                    },
                    onShare = {
                        if (selectedFriends.isNotEmpty() && mealToShare != null) {
                            viewModel.shareSavedMealWithFriends(mealToShare!!, selectedFriends.toList())
                            showShareDialog = false
                            selectedFriends = emptySet()
                            mealToShare = null
                            // Note: Success toast will be shown via LaunchedEffect watching uiState
                        }
                    },
                    onDismiss = {
                        showShareDialog = false
                        selectedFriends = emptySet()
                        mealToShare = null
                    }
                )
            }
        }
    }
}

@Composable
fun SavedMealCard(
    meal: SavedMeal,
    onMealSelected: () -> Unit,
    onDeleteMeal: () -> Unit,
    onShare: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onMealSelected)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = meal.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    // Show recipe icon if meal has a recipe
                    if (meal.recipeId != null) {
                        Icon(
                            Icons.Filled.RestaurantMenu,
                            contentDescription = "Has recipe",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = meal.foodName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = "Share meal",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onDeleteMeal,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete meal",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Nutritional info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "${meal.calories} cal",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "${meal.proteinG}g protein",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${meal.carbsG}g carbs",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${meal.fatG}g fat",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Usage stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Used ${meal.useCount} time${if (meal.useCount != 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
            Text(
                text = "Last: ${dateFormat.format(Date(meal.lastUsedAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
