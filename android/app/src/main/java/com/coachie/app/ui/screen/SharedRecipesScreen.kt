package com.coachie.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.Recipe
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.viewmodel.AuthState
import com.coachie.app.viewmodel.CoachieViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedRecipesScreen(
    onNavigateBack: () -> Unit,
    viewModel: CoachieViewModel = viewModel()
) {
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val authState by viewModel.authState.collectAsState()
    val currentUser = (authState as? AuthState.Authenticated)?.user
    val userId = currentUser?.uid ?: return

    val repository = FirebaseRepository.getInstance()
    var recipes by remember { mutableStateOf<List<Recipe>>(emptyList()) }
    var sharedMeals by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var authorNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var savingRecipeId by remember { mutableStateOf<String?>(null) }
    var showDeleteRecipeDialog by remember { mutableStateOf<Recipe?>(null) }
    var showDeleteMealDialog by remember { mutableStateOf<Map<String, Any>?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    // Function to reload data
    fun reloadData() {
        coroutineScope.launch {
            isLoading = true
            val recipesResult = repository.getSharedRecipes(userId)
            val mealsResult = repository.getSharedMeals(userId)
            
            recipesResult.fold(
                onSuccess = { recipesList -> recipes = recipesList },
                onFailure = { error -> errorMessage = "Failed to load recipes: ${error.message}" }
            )
            
            mealsResult.fold(
                onSuccess = { mealsList -> sharedMeals = mealsList },
                onFailure = { error -> errorMessage = "Failed to load meals: ${error.message}" }
            )
            isLoading = false
        }
    }

    LaunchedEffect(userId) {
        isLoading = true
        errorMessage = null
        
        // Load both shared recipes AND shared meals
        android.util.Log.d("SharedRecipesScreen", "========== LOADING SHARED RECIPES AND MEALS ==========")
        android.util.Log.d("SharedRecipesScreen", "User ID: $userId")
        
        // Load recipes
        val recipesResult = repository.getSharedRecipes(userId)
        val mealsResult = repository.getSharedMeals(userId)
        
        recipesResult.fold(
            onSuccess = { recipesList ->
                android.util.Log.d("SharedRecipesScreen", "✅ Loaded ${recipesList.size} shared recipes")
                recipes = recipesList
            },
            onFailure = { error ->
                android.util.Log.e("SharedRecipesScreen", "❌ Error loading shared recipes: ${error.message}")
                errorMessage = "Failed to load recipes: ${error.message}"
            }
        )
        
        mealsResult.fold(
            onSuccess = { mealsList ->
                android.util.Log.d("SharedRecipesScreen", "✅ Loaded ${mealsList.size} shared meals")
                sharedMeals = mealsList
            },
            onFailure = { error ->
                android.util.Log.e("SharedRecipesScreen", "❌ Error loading shared meals: ${error.message}")
                if (errorMessage == null) {
                    errorMessage = "Failed to load meals: ${error.message}"
                }
            }
        )
        
        // Load author names for both recipes and meals
        val allUserIds = (recipes.map { it.userId } + sharedMeals.mapNotNull { it["userId"] as? String }).distinct()
        val namesMap = mutableMapOf<String, String>()
        allUserIds.forEach { authorId ->
            repository.getUserProfile(authorId).fold(
                onSuccess = { profile ->
                    if (profile != null) {
                        val name = profile.name?.takeIf { it.isNotBlank() } 
                            ?: profile.username?.takeIf { it.isNotBlank() }
                            ?: "Anonymous"
                        namesMap[authorId] = name
                        android.util.Log.d("SharedRecipesScreen", "✅ Loaded profile for $authorId: $name")
                    } else {
                        android.util.Log.w("SharedRecipesScreen", "⚠️ Profile is null for $authorId")
                        namesMap[authorId] = "Anonymous"
                    }
                },
                onFailure = { error ->
                    android.util.Log.e("SharedRecipesScreen", "❌ Failed to load profile for $authorId: ${error.message}")
                    namesMap[authorId] = "Anonymous"
                }
            )
        }
        authorNames = namesMap
        
        android.util.Log.d("SharedRecipesScreen", "Total items: ${recipes.size} recipes + ${sharedMeals.size} meals = ${recipes.size + sharedMeals.size}")
        android.util.Log.d("SharedRecipesScreen", "==========================================")
        
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shared Recipes & Meals") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBackground)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (errorMessage != null) {
                    item {
                        Text(
                            text = "Error: $errorMessage",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else if (recipes.isEmpty() && sharedMeals.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "No shared recipes or meals yet",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Recipes and meals shared by your friends will appear here",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Show recipes first
                    items(recipes) { recipe ->
                        SharedRecipeCard(
                            recipe = recipe,
                            authorName = authorNames[recipe.userId] ?: "Anonymous",
                            isOwner = true, // Allow everyone to delete (removes from their view only)
                            onSave = { recipeToSave ->
                                savingRecipeId = recipeToSave.id
                                coroutineScope.launch {
                                    repository.saveRecipeToSavedMeals(userId, recipeToSave).fold(
                                        onSuccess = {
                                            savingRecipeId = null
                                            // Could show snackbar here
                                        },
                                        onFailure = {
                                            savingRecipeId = null
                                            errorMessage = "Failed to save recipe: ${it.message}"
                                        }
                                    )
                                }
                            },
                            onDelete = {
                                showDeleteRecipeDialog = recipe
                            },
                            isSaving = savingRecipeId == recipe.id
                        )
                    }
                    
                    // Then show shared meals
                    items(sharedMeals) { meal ->
                        val mealUserId = meal["userId"] as? String ?: ""
                        SharedMealCard(
                            meal = meal,
                            authorName = authorNames[mealUserId] ?: "Anonymous",
                            isOwner = true, // Allow everyone to delete (removes from their view only)
                            onSave = {
                                // Save meal to saved meals
                                coroutineScope.launch {
                                    try {
                                        // CRITICAL: Generate a NEW unique ID for the saved meal
                                        // This is a copy saved to the user's saved meals, not the original shared meal
                                        val savedMeal = com.coachie.app.data.model.SavedMeal(
                                            id = java.util.UUID.randomUUID().toString(), // New unique ID for user's saved copy
                                            userId = userId, // Current user's ID (will be verified/overridden in saveSavedMeal)
                                            name = (meal["name"] as? String) ?: (meal["foodName"] as? String) ?: "Unknown",
                                            foodName = (meal["foodName"] as? String) ?: (meal["name"] as? String) ?: "Unknown",
                                            calories = (meal["calories"] as? Number)?.toInt() ?: 0,
                                            proteinG = ((meal["proteinG"] as? Number)?.toDouble() ?: 0.0).toInt(),
                                            carbsG = ((meal["carbsG"] as? Number)?.toDouble() ?: 0.0).toInt(),
                                            fatG = ((meal["fatG"] as? Number)?.toDouble() ?: 0.0).toInt(),
                                            sugarG = ((meal["sugarG"] as? Number)?.toDouble() ?: 0.0).toInt(),
                                            addedSugarG = ((meal["addedSugarG"] as? Number)?.toDouble() ?: 0.0).toInt(),
                                            createdAt = System.currentTimeMillis(), // Current time for when user saved it
                                            lastUsedAt = System.currentTimeMillis()
                                        )
                                        repository.saveSavedMeal(savedMeal).fold(
                                            onSuccess = {
                                                android.util.Log.d("SharedRecipesScreen", "✅ Saved shared meal to saved meals")
                                            },
                                            onFailure = { error ->
                                                android.util.Log.e("SharedRecipesScreen", "❌ Error saving shared meal", error)
                                                errorMessage = "Failed to save meal: ${error.message}"
                                            }
                                        )
                                    } catch (e: Exception) {
                                        android.util.Log.e("SharedRecipesScreen", "❌ Exception saving shared meal", e)
                                        errorMessage = "Failed to save meal: ${e.message}"
                                    }
                                }
                            },
                            onDelete = {
                                showDeleteMealDialog = meal
                            }
                        )
                    }
                }
            }
        }
        
        // Delete recipe confirmation dialog
        showDeleteRecipeDialog?.let { recipe ->
            AlertDialog(
                onDismissRequest = { showDeleteRecipeDialog = null },
                title = { Text("Remove Recipe") },
                text = { Text("Remove \"${recipe.name}\" from your shared recipes? Others will still be able to see it.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                repository.deleteSharedRecipe(userId, recipe.id).fold(
                                    onSuccess = {
                                        showDeleteRecipeDialog = null
                                        reloadData()
                                    },
                                    onFailure = { error ->
                                        errorMessage = "Failed to delete recipe: ${error.message}"
                                        showDeleteRecipeDialog = null
                                    }
                                )
                            }
                        }
                    ) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteRecipeDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        // Delete meal confirmation dialog
        showDeleteMealDialog?.let { meal ->
            val mealName = (meal["name"] as? String) ?: (meal["foodName"] as? String) ?: "Unknown Meal"
            AlertDialog(
                onDismissRequest = { showDeleteMealDialog = null },
                title = { Text("Remove Meal") },
                text = { Text("Remove \"$mealName\" from your shared meals? Others will still be able to see it.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val mealId = (meal["id"] as? String) ?: (meal["entryId"] as? String) ?: ""
                            if (mealId.isNotEmpty()) {
                                coroutineScope.launch {
                                    repository.deleteSharedMeal(userId, mealId).fold(
                                        onSuccess = {
                                            showDeleteMealDialog = null
                                            reloadData()
                                        },
                                        onFailure = { error ->
                                            errorMessage = "Failed to delete meal: ${error.message}"
                                            showDeleteMealDialog = null
                                        }
                                    )
                                }
                            }
                        }
                    ) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteMealDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun SharedRecipeCard(
    recipe: Recipe,
    authorName: String,
    isOwner: Boolean,
    onSave: (Recipe) -> Unit,
    onDelete: () -> Unit,
    isSaving: Boolean
) {
    val perServing = recipe.getNutritionPerServing()
    
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.colors()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with name and save button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recipe.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Shared by $authorName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    recipe.description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isOwner) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Button(
                        onClick = { onSave(recipe) },
                        enabled = !isSaving,
                        modifier = Modifier.padding(start = if (isOwner) 0.dp else 8.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Filled.Save, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Save")
                        }
                    }
                }
            }

            // Nutrition info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Nutrition (per serving, ${recipe.servings} servings total)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        NutritionChip("Cal", "${perServing.calories}")
                        NutritionChip("Protein", "${perServing.proteinG}g")
                        NutritionChip("Carbs", "${perServing.carbsG}g")
                        NutritionChip("Fat", "${perServing.fatG}g")
                    }
                }
            }

            // Ingredients
            if (recipe.ingredients.isNotEmpty()) {
                Text(
                    text = "Ingredients:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                recipe.ingredients.take(5).forEach { ingredient ->
                    Text(
                        text = "• ${ingredient.quantity} ${ingredient.unit} ${ingredient.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (recipe.ingredients.size > 5) {
                    Text(
                        text = "... and ${recipe.ingredients.size - 5} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedMealCard(
    meal: Map<String, Any>,
    authorName: String,
    isOwner: Boolean,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    val mealName = (meal["name"] as? String) ?: (meal["foodName"] as? String) ?: "Unknown Meal"
    val calories = (meal["calories"] as? Number)?.toInt() ?: 0
    val proteinG = (meal["proteinG"] as? Number)?.toDouble() ?: 0.0
    val carbsG = (meal["carbsG"] as? Number)?.toDouble() ?: 0.0
    val fatG = (meal["fatG"] as? Number)?.toDouble() ?: 0.0
    
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.colors()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with name and save button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mealName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Shared by $authorName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isOwner) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Button(
                        onClick = onSave,
                        modifier = Modifier.padding(start = if (isOwner) 0.dp else 8.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                }
            }

            // Nutrition info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Nutrition",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        NutritionChip("Cal", "$calories")
                        NutritionChip("Protein", "${proteinG.toInt()}g")
                        NutritionChip("Carbs", "${carbsG.toInt()}g")
                        NutritionChip("Fat", "${fatG.toInt()}g")
                    }
                }
            }
        }
    }
}

@Composable
private fun NutritionChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

