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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.data.model.Recipe
import com.mealprint.ai.ui.components.CoachieCard
import com.mealprint.ai.ui.components.CoachieCardDefaults
import com.mealprint.ai.ui.theme.rememberCoachieGradient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipeId: String,
    userId: String,
    onNavigateBack: () -> Unit
) {
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val repository = remember { FirebaseRepository.getInstance() }
    val coroutineScope = rememberCoroutineScope()
    
    var recipe by remember { mutableStateOf<Recipe?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Load recipe
    LaunchedEffect(recipeId) {
        isLoading = true
        errorMessage = null
        
        coroutineScope.launch(Dispatchers.IO) {
            android.util.Log.d("RecipeDetailScreen", "Loading recipe: $recipeId for user: $userId")
            
            // Try shared recipes first (for forum/circle posts), then personal recipes
            val sharedResult = repository.getSharedRecipe(recipeId)
            sharedResult.fold(
                onSuccess = { sharedRecipe ->
                    android.util.Log.d("RecipeDetailScreen", "✅ Found recipe in sharedRecipes: ${sharedRecipe.name}")
                    withContext(Dispatchers.Main) {
                        recipe = sharedRecipe
                        isLoading = false
                    }
                },
                onFailure = { sharedError ->
                    android.util.Log.d("RecipeDetailScreen", "Recipe not in sharedRecipes, trying personal recipes: ${sharedError.message}")
                    // Try personal recipes
                    val result = repository.getRecipe(userId, recipeId)
                    result.fold(
                        onSuccess = { loadedRecipe ->
                            if (loadedRecipe != null) {
                                android.util.Log.d("RecipeDetailScreen", "✅ Found recipe in personal recipes: ${loadedRecipe.name}")
                                withContext(Dispatchers.Main) {
                                    recipe = loadedRecipe
                                    isLoading = false
                                }
                            } else {
                                android.util.Log.e("RecipeDetailScreen", "❌ Recipe not found in personal recipes: $recipeId")
                                withContext(Dispatchers.Main) {
                                    errorMessage = "Recipe not found. It may have been deleted or you don't have access to it."
                                    isLoading = false
                                }
                            }
                        },
                        onFailure = { error ->
                            android.util.Log.e("RecipeDetailScreen", "❌ Failed to load recipe from both sources: ${error.message}")
                            withContext(Dispatchers.Main) {
                                errorMessage = "Failed to load recipe: ${error.message}"
                                isLoading = false
                            }
                        }
                    )
                }
            )
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recipe Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
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
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = errorMessage ?: "Failed to load recipe",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                recipe != null -> {
                    val currentRecipe = recipe!!
                    val perServing = currentRecipe.getNutritionPerServing()
                    
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CoachieCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CoachieCardDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Title
                                Text(
                                    text = currentRecipe.name,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                // Description
                                currentRecipe.description?.let { desc ->
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                Divider()
                                
                                // Nutrition info
                                Text(
                                    text = "Nutrition (per serving):",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Calories", style = MaterialTheme.typography.labelSmall)
                                        Text("${perServing.calories}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text("Protein", style = MaterialTheme.typography.labelSmall)
                                        Text("${perServing.proteinG}g", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text("Carbs", style = MaterialTheme.typography.labelSmall)
                                        Text("${perServing.carbsG}g", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text("Fat", style = MaterialTheme.typography.labelSmall)
                                        Text("${perServing.fatG}g", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    }
                                }
                                
                                // Servings
                                Text(
                                    text = "Servings: ${currentRecipe.servings}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                
                                Divider()
                                
                                // Ingredients
                                if (currentRecipe.ingredients.isNotEmpty()) {
                                    Text(
                                        text = "Ingredients:",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    currentRecipe.ingredients.forEach { ingredient ->
                                        Text(
                                            text = "• ${ingredient.quantity} ${ingredient.unit} ${ingredient.name}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                
                                // Instructions
                                if (currentRecipe.instructions != null && currentRecipe.instructions!!.isNotEmpty()) {
                                    Divider()
                                    Text(
                                        text = "Instructions:",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    currentRecipe.instructions!!.forEachIndexed { index, instruction ->
                                        Text(
                                            text = "${index + 1}. $instruction",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

