package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.data.model.HealthLog
import com.mealprint.ai.data.model.PublicUserProfile
import com.mealprint.ai.data.model.Recipe
import com.mealprint.ai.ui.components.CoachieCard
import com.mealprint.ai.ui.components.CoachieCardDefaults
import com.mealprint.ai.ui.components.RecipeShareDialog
import com.mealprint.ai.ui.components.UpgradePromptDialog
import com.mealprint.ai.ui.theme.rememberCoachieGradient
import com.mealprint.ai.ui.theme.Primary40
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import android.net.Uri
import java.io.File
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyRecipesScreen(
    userId: String,
    onNavigateBack: () -> Unit,
    onRecipeSelected: (Recipe) -> Unit = {},
    firebaseRepository: FirebaseRepository = FirebaseRepository.getInstance(),
    onNavigateToSubscription: () -> Unit = {}
) {
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var recipes by remember { mutableStateOf<List<Recipe>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Share dialog state
    var showShareDialog by rememberSaveable { mutableStateOf(false) }
    var friends by remember { mutableStateOf<List<PublicUserProfile>>(emptyList()) }
    var selectedFriends by remember { mutableStateOf<Set<String>>(emptySet()) }
    var recipeToShare by remember { mutableStateOf<Recipe?>(null) }
    var isSharingToForum by remember { mutableStateOf(false) }
    var isSharingToCircle by remember { mutableStateOf(false) }
    
    // Delete dialog state
    var showDeleteDialog by remember { mutableStateOf(false) }
    var recipeToDelete by remember { mutableStateOf<Recipe?>(null) }
    
    // Log meal dialog state
    var showLogMealDialog by remember { mutableStateOf(false) }
    var recipeToLog by remember { mutableStateOf<Recipe?>(null) }
    var servingsToLog by remember { mutableStateOf(1.0) }
    var isLoggingMeal by remember { mutableStateOf(false) }
    
    // Refresh nutrition state
    var isRefreshingNutrition by remember { mutableStateOf(false) }
    var recipeToRefresh by remember { mutableStateOf<Recipe?>(null) }
    
    // Photo handling for social media sharing
    var mealPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var sharePhotoUri by remember { mutableStateOf<Uri?>(null) }
    
    // Subscription state
    var showUpgradeDialog by remember { mutableStateOf(false) }
    var subscriptionTier by remember { mutableStateOf<com.coachie.app.data.model.SubscriptionTier?>(null) }
    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && sharePhotoUri != null) {
            mealPhotoUri = sharePhotoUri
        }
    }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        mealPhotoUri = uri
    }
    
    // Load recipes and subscription tier
    LaunchedEffect(userId) {
        isLoading = true
        errorMessage = null
        
        coroutineScope.launch(Dispatchers.IO) {
            val result = firebaseRepository.getUserRecipes(userId)
            val tier = com.coachie.app.data.SubscriptionService.getUserTier(userId)
            withContext(Dispatchers.Main) {
                subscriptionTier = tier
                result.fold(
                    onSuccess = { recipesList ->
                        recipes = recipesList
                        isLoading = false
                    },
                    onFailure = { error ->
                        errorMessage = "Failed to load recipes: ${error.message}"
                        isLoading = false
                    }
                )
            }
        }
    }
    
    // Load friends when share dialog opens
    LaunchedEffect(showShareDialog) {
        if (showShareDialog) {
            coroutineScope.launch(Dispatchers.IO) {
                firebaseRepository.getFriends(userId).fold(
                    onSuccess = { friendsList ->
                        withContext(Dispatchers.Main) {
                            friends = friendsList
                        }
                    },
                    onFailure = {
                        withContext(Dispatchers.Main) {
                            friends = emptyList()
                        }
                    }
                )
            }
        }
    }
    
    // Handle share
    fun handleShareRecipe(recipe: Recipe) {
        // Check subscription tier before showing share dialog
        if (subscriptionTier != com.coachie.app.data.model.SubscriptionTier.PRO) {
            showUpgradeDialog = true
            return
        }
        recipeToShare = recipe
        mealPhotoUri = null // Reset photo when opening share dialog
        showShareDialog = true
    }
    
    fun performShare() {
        recipeToShare?.let { recipe ->
            if (selectedFriends.isNotEmpty()) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        // Check subscription tier - recipe sharing is Pro-only
                        val tier = com.coachie.app.data.SubscriptionService.getUserTier(userId)
                        if (tier != com.coachie.app.data.model.SubscriptionTier.PRO) {
                            withContext(Dispatchers.Main) {
                                showUpgradeDialog = true
                            }
                            return@launch
                        }
                        
                        // Save recipe to personal collection first
                        val saveResult = firebaseRepository.saveRecipe(userId, recipe)
                        if (saveResult.isFailure) {
                            android.util.Log.w("MyRecipesScreen", "Failed to save recipe to personal collection, continuing with share")
                        }
                        
                        // Create shared recipe
                        val sharedRecipe = recipe.copy(
                            isShared = true,
                            sharedWith = selectedFriends.toList()
                        )
                        
                        // Save to sharedRecipes collection
                        val recipeData = hashMapOf<String, Any>(
                            "id" to sharedRecipe.id,
                            "userId" to sharedRecipe.userId,
                            "name" to sharedRecipe.name,
                            "description" to (sharedRecipe.description ?: ""),
                            "servings" to sharedRecipe.servings,
                            "ingredients" to sharedRecipe.ingredients.map { ing ->
                                hashMapOf(
                                    "name" to ing.name,
                                    "quantity" to ing.quantity,
                                    "unit" to ing.unit,
                                    "calories" to ing.calories,
                                    "proteinG" to ing.proteinG,
                                    "carbsG" to ing.carbsG,
                                    "fatG" to ing.fatG,
                                    "sugarG" to ing.sugarG,
                                    "micronutrients" to ing.micronutrients
                                )
                            },
                            "instructions" to (sharedRecipe.instructions ?: emptyList()),
                            "totalCalories" to sharedRecipe.totalCalories,
                            "totalProteinG" to sharedRecipe.totalProteinG,
                            "totalCarbsG" to sharedRecipe.totalCarbsG,
                            "totalFatG" to sharedRecipe.totalFatG,
                            "totalSugarG" to sharedRecipe.totalSugarG,
                            "micronutrients" to sharedRecipe.micronutrients,
                            "isShared" to true,
                            "sharedWith" to selectedFriends.toList(),
                            "createdAt" to sharedRecipe.createdAt
                        )
                        
                        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        firestore.collection("sharedRecipes")
                            .document(sharedRecipe.id)
                            .set(recipeData)
                            .await()
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Recipe shared successfully!", Toast.LENGTH_SHORT).show()
                            showShareDialog = false
                            selectedFriends = emptySet()
                            recipeToShare = null
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MyRecipesScreen", "Error sharing recipe", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to share recipe: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                Toast.makeText(context, "Please select at least one friend", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // Handle delete
    fun handleDeleteRecipe(recipe: Recipe) {
        recipeToDelete = recipe
        showDeleteDialog = true
    }
    
    fun performDelete() {
        recipeToDelete?.let { recipe ->
            coroutineScope.launch(Dispatchers.IO) {
                val result = firebaseRepository.deleteRecipe(userId, recipe.id)
                withContext(Dispatchers.Main) {
                    result.fold(
                        onSuccess = {
                            recipes = recipes.filter { it.id != recipe.id }
                            Toast.makeText(context, "Recipe deleted", Toast.LENGTH_SHORT).show()
                            showDeleteDialog = false
                            recipeToDelete = null
                        },
                        onFailure = { error ->
                            Toast.makeText(context, "Failed to delete recipe: ${error.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
    
    // Handle log meal
    fun handleLogMeal(recipe: Recipe) {
        recipeToLog = recipe
        servingsToLog = 1.0
        showLogMealDialog = true
    }
    
    fun performLogMeal() {
        recipeToLog?.let { recipe ->
            isLoggingMeal = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    
                    // Convert recipe to meal log with selected servings
                    val perServing = recipe.getNutritionPerServing()
                    val mealLog = HealthLog.MealLog(
                        foodName = recipe.name,
                        calories = (perServing.calories * servingsToLog).toInt(),
                        protein = (perServing.proteinG * servingsToLog).toInt(),
                        carbs = (perServing.carbsG * servingsToLog).toInt(),
                        fat = (perServing.fatG * servingsToLog).toInt(),
                        sugar = (perServing.sugarG * servingsToLog).toInt(),
                        addedSugar = (perServing.addedSugarG * servingsToLog).toInt(),
                        micronutrients = perServing.micronutrients.mapValues { (_, value) -> value * servingsToLog },
                        recipeId = recipe.id,
                        servingsConsumed = servingsToLog
                    )
                    
                    // Save meal log
                    val result = firebaseRepository.saveHealthLog(userId, today, mealLog)
                    
                    withContext(Dispatchers.Main) {
                        isLoggingMeal = false
                        result.fold(
                            onSuccess = {
                                Toast.makeText(context, "Meal logged successfully!", Toast.LENGTH_SHORT).show()
                                showLogMealDialog = false
                                recipeToLog = null
                                servingsToLog = 1.0
                            },
                            onFailure = { error ->
                                Toast.makeText(context, "Failed to log meal: ${error.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MyRecipesScreen", "Error logging meal", e)
                    withContext(Dispatchers.Main) {
                        isLoggingMeal = false
                        Toast.makeText(context, "Failed to log meal: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    // Handle refresh nutrition
    fun handleRefreshNutrition(recipe: Recipe) {
        recipeToRefresh = recipe
        isRefreshingNutrition = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val functions = Firebase.functions
                val refreshFunction = functions.getHttpsCallable("refreshRecipeNutrition")
                
                val data = hashMapOf(
                    "userId" to userId,
                    "recipeId" to recipe.id
                )
                
                val result = refreshFunction.call(data).await()
                val resultData = result.data as? Map<*, *>
                val results = resultData?.get("results") as? Map<*, *>
                val updated = results?.get("updated") as? Int ?: 0
                
                withContext(Dispatchers.Main) {
                    isRefreshingNutrition = false
                    if (updated > 0) {
                        // Reload recipes to get updated data
                        coroutineScope.launch(Dispatchers.IO) {
                            val reloadResult = firebaseRepository.getUserRecipes(userId)
                            withContext(Dispatchers.Main) {
                                reloadResult.fold(
                                    onSuccess = { recipesList ->
                                        recipes = recipesList
                                        Toast.makeText(context, "Recipe nutrition refreshed successfully!", Toast.LENGTH_SHORT).show()
                                    },
                                    onFailure = { error ->
                                        Toast.makeText(context, "Nutrition refreshed but failed to reload: ${error.message}", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    } else {
                        Toast.makeText(context, "No changes needed - nutrition is already up to date", Toast.LENGTH_SHORT).show()
                    }
                    recipeToRefresh = null
                }
            } catch (e: Exception) {
                android.util.Log.e("MyRecipesScreen", "Error refreshing nutrition", e)
                withContext(Dispatchers.Main) {
                    isRefreshingNutrition = false
                    Toast.makeText(context, "Failed to refresh nutrition: ${e.message}", Toast.LENGTH_SHORT).show()
                    recipeToRefresh = null
                }
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
                                contentDescription = "Back",
                                tint = Primary40
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "My Recipes",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Primary40
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
                                text = "Error",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage ?: "Unknown error",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    
                    recipes.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Filled.RestaurantMenu,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No Recipes Yet",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Save recipes from meal logs, weekly blueprints, or recipe analysis to see them here",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(recipes) { recipe ->
                                RecipeCard(
                                    recipe = recipe,
                                    onRecipeClick = { onRecipeSelected(recipe) },
                                    onLogMeal = { handleLogMeal(recipe) },
                                    onEdit = {
                                        // TODO: Navigate to recipe edit screen
                                        Toast.makeText(context, "Recipe editing coming soon", Toast.LENGTH_SHORT).show()
                                    },
                                    onShare = { handleShareRecipe(recipe) },
                                    onDelete = { handleDeleteRecipe(recipe) },
                                    onRefreshNutrition = { handleRefreshNutrition(recipe) },
                                    isRefreshing = isRefreshingNutrition && recipeToRefresh?.id == recipe.id
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Share Dialog
        if (showShareDialog && recipeToShare != null) {
            RecipeShareDialog(
                recipe = recipeToShare!!,
                friends = friends,
                selectedFriends = selectedFriends,
                onFriendToggle = { friendId ->
                    selectedFriends = if (selectedFriends.contains(friendId)) {
                        selectedFriends - friendId
                    } else {
                        selectedFriends + friendId
                    }
                },
                onShareWithFriends = { performShare() },
                onShareToForum = {
                    isSharingToForum = true
                    coroutineScope.launch(Dispatchers.IO) {
                        val result = firebaseRepository.postRecipeToForum(recipeToShare!!)
                        withContext(Dispatchers.Main) {
                            isSharingToForum = false
                            if (result.isSuccess) {
                                Toast.makeText(context, "Recipe posted to forum!", Toast.LENGTH_SHORT).show()
                                showShareDialog = false
                                recipeToShare = null
                                mealPhotoUri = null
                            } else {
                                Toast.makeText(context, "Failed to post to forum: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onShareToCircle = {
                    isSharingToCircle = true
                    coroutineScope.launch(Dispatchers.IO) {
                        val circleResult = firebaseRepository.getOrCreateRecipeSharingCircle(userId)
                        circleResult.fold(
                            onSuccess = { circleId ->
                                val postResult = firebaseRepository.postRecipeToCircle(recipeToShare!!, circleId, userId)
                                withContext(Dispatchers.Main) {
                                    isSharingToCircle = false
                                    if (postResult.isSuccess) {
                                        Toast.makeText(context, "Recipe shared to your Recipe Sharing circle!", Toast.LENGTH_SHORT).show()
                                        showShareDialog = false
                                        recipeToShare = null
                                        mealPhotoUri = null
                                    } else {
                                        Toast.makeText(context, "Failed to share to circle: ${postResult.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onFailure = { error ->
                                withContext(Dispatchers.Main) {
                                    isSharingToCircle = false
                                    Toast.makeText(context, "Failed to create/find circle: ${error.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                },
                onDismiss = {
                    showShareDialog = false
                    selectedFriends = emptySet()
                    recipeToShare = null
                    mealPhotoUri = null
                },
                isSharingToForum = isSharingToForum,
                isSharingToCircle = isSharingToCircle,
                mealPhotoUri = mealPhotoUri,
                onCapturePhoto = {
                    // Create a file for the photo
                    val photoFile = File(context.cacheDir, "recipe_photo_${System.currentTimeMillis()}.jpg")
                    val photoUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        photoFile
                    )
                    sharePhotoUri = photoUri
                    cameraLauncher.launch(photoUri)
                },
                onSelectPhoto = {
                    imagePickerLauncher.launch("image/*")
                },
                onShareToInstagram = {
                    mealPhotoUri?.let { photoUri ->
                        coroutineScope.launch(Dispatchers.IO) {
                            val shareService = com.coachie.app.service.ShareService.getInstance(context)
                            val imageGenerator = com.coachie.app.service.ShareImageGenerator(context)
                            
                            // Generate recipe card image
                            val recipeCardBitmap = imageGenerator.generateRecipeCardImage(recipeToShare!!)
                            
                            // Generate promotional post with both images
                            val imageUri = imageGenerator.generatePromotionalPostFromUri(
                                photoUri = photoUri,
                                cardTitle = recipeToShare!!.name,
                                cardDescription = "${recipeToShare!!.totalCalories} cal • Check out this recipe!",
                                recipeCardBitmap = recipeCardBitmap
                            )
                            
                            withContext(Dispatchers.Main) {
                                if (imageUri != null) {
                                    shareService.shareToInstagramFeed(imageUri, activityContext = context as? android.app.Activity)
                                } else {
                                    android.widget.Toast.makeText(context, "Failed to generate share image", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                showShareDialog = false
                                mealPhotoUri = null
                            }
                        }
                    }
                },
                onShareToFacebook = {
                    mealPhotoUri?.let { photoUri ->
                        coroutineScope.launch(Dispatchers.IO) {
                            val shareService = com.coachie.app.service.ShareService.getInstance(context)
                            val imageGenerator = com.coachie.app.service.ShareImageGenerator(context)
                            
                            // Generate recipe card image
                            val recipeCardBitmap = imageGenerator.generateRecipeCardImage(recipeToShare!!)
                            
                            // Generate promotional post with both images
                            val imageUri = imageGenerator.generatePromotionalPostFromUri(
                                photoUri = photoUri,
                                cardTitle = recipeToShare!!.name,
                                cardDescription = "${recipeToShare!!.totalCalories} cal • Check out this recipe!",
                                recipeCardBitmap = recipeCardBitmap
                            )
                            
                            withContext(Dispatchers.Main) {
                                if (imageUri != null) {
                                    shareService.shareToFacebook(imageUri, activityContext = context as? android.app.Activity)
                                } else {
                                    android.widget.Toast.makeText(context, "Failed to generate share image", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                showShareDialog = false
                                mealPhotoUri = null
                            }
                        }
                    }
                },
                onShareToTikTok = {
                    mealPhotoUri?.let { photoUri ->
                        coroutineScope.launch(Dispatchers.IO) {
                            val shareService = com.coachie.app.service.ShareService.getInstance(context)
                            val imageGenerator = com.coachie.app.service.ShareImageGenerator(context)
                            
                            // Generate recipe card image
                            val recipeCardBitmap = imageGenerator.generateRecipeCardImage(recipeToShare!!)
                            
                            // Generate promotional post with both images
                            val imageUri = imageGenerator.generatePromotionalPostFromUri(
                                photoUri = photoUri,
                                cardTitle = recipeToShare!!.name,
                                cardDescription = "${recipeToShare!!.totalCalories} cal • Check out this recipe!",
                                recipeCardBitmap = recipeCardBitmap
                            )
                            
                            withContext(Dispatchers.Main) {
                                if (imageUri != null) {
                                    shareService.shareToTikTok(imageUri, activityContext = context as? android.app.Activity)
                                } else {
                                    android.widget.Toast.makeText(context, "Failed to generate share image", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                showShareDialog = false
                                mealPhotoUri = null
                            }
                        }
                    }
                },
                onShareToX = {
                    mealPhotoUri?.let { photoUri ->
                        coroutineScope.launch(Dispatchers.IO) {
                            val shareService = com.coachie.app.service.ShareService.getInstance(context)
                            val imageGenerator = com.coachie.app.service.ShareImageGenerator(context)
                            
                            // Generate recipe card image
                            val recipeCardBitmap = imageGenerator.generateRecipeCardImage(recipeToShare!!)
                            
                            // Generate promotional post with both images
                            val imageUri = imageGenerator.generatePromotionalPostFromUri(
                                photoUri = photoUri,
                                cardTitle = recipeToShare!!.name,
                                cardDescription = "${recipeToShare!!.totalCalories} cal • Check out this recipe!",
                                recipeCardBitmap = recipeCardBitmap
                            )
                            
                            withContext(Dispatchers.Main) {
                                if (imageUri != null) {
                                    shareService.shareToX(imageUri, activityContext = context as? android.app.Activity)
                                } else {
                                    android.widget.Toast.makeText(context, "Failed to generate share image", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                showShareDialog = false
                                mealPhotoUri = null
                            }
                        }
                    }
                },
                onShareToOther = {
                    mealPhotoUri?.let { photoUri ->
                        coroutineScope.launch(Dispatchers.IO) {
                            val shareService = com.coachie.app.service.ShareService.getInstance(context)
                            val imageGenerator = com.coachie.app.service.ShareImageGenerator(context)
                            
                            // Generate recipe card image
                            val recipeCardBitmap = imageGenerator.generateRecipeCardImage(recipeToShare!!)
                            
                            // Generate promotional post with both images
                            val imageUri = imageGenerator.generatePromotionalPostFromUri(
                                photoUri = photoUri,
                                cardTitle = recipeToShare!!.name,
                                cardDescription = "${recipeToShare!!.totalCalories} cal • Check out this recipe!",
                                recipeCardBitmap = recipeCardBitmap
                            )
                            
                            withContext(Dispatchers.Main) {
                                if (imageUri != null) {
                                    shareService.shareImage(imageUri, "Share Recipe", "Check out this recipe!", addWatermark = false, activityContext = context as? android.app.Activity)
                                } else {
                                    android.widget.Toast.makeText(context, "Failed to generate share image", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                showShareDialog = false
                                mealPhotoUri = null
                            }
                        }
                    }
                }
            )
        }
        
        // Delete Confirmation Dialog
        if (showDeleteDialog && recipeToDelete != null) {
            AlertDialog(
                onDismissRequest = {
                    showDeleteDialog = false
                    recipeToDelete = null
                },
                title = { Text("Delete Recipe") },
                text = { Text("Are you sure you want to delete \"${recipeToDelete!!.name}\"? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = { performDelete() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            recipeToDelete = null
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        // Log Meal Dialog
        if (showLogMealDialog && recipeToLog != null) {
            val recipe = recipeToLog!!
            val perServing = recipe.getNutritionPerServing()
            val calculatedCalories = (perServing.calories * servingsToLog).toInt()
            val calculatedProtein = perServing.proteinG * servingsToLog
            val calculatedCarbs = perServing.carbsG * servingsToLog
            val calculatedFat = perServing.fatG * servingsToLog
            
            AlertDialog(
                onDismissRequest = {
                    if (!isLoggingMeal) {
                        showLogMealDialog = false
                        recipeToLog = null
                        servingsToLog = 1.0
                    }
                },
                title = { 
                    Text("Log Meal: ${recipe.name}")
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Serving size selector
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Servings",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            
                            val minServings = 0.25
                            val maxServings = 8.0
                            val servingsStep = 0.25
                            val servingsSliderSteps = ((maxServings - minServings) / servingsStep).toInt() - 1
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { 
                                        servingsToLog = (servingsToLog - servingsStep).coerceAtLeast(minServings)
                                    },
                                    enabled = !isLoggingMeal
                                ) {
                                    Text("-")
                                }
                                
                                Slider(
                                    value = servingsToLog.toFloat(),
                                    onValueChange = { servingsToLog = it.toDouble() },
                                    valueRange = minServings.toFloat()..maxServings.toFloat(),
                                    steps = servingsSliderSteps,
                                    modifier = Modifier.weight(1f),
                                    enabled = !isLoggingMeal
                                )
                                
                                IconButton(
                                    onClick = { 
                                        servingsToLog = (servingsToLog + servingsStep).coerceAtMost(maxServings)
                                    },
                                    enabled = !isLoggingMeal
                                ) {
                                    Text("+")
                                }
                            }
                            
                            Text(
                                text = "${String.format("%.2f", servingsToLog)} serving${if (servingsToLog != 1.0) "s" else ""} (Recipe serves ${recipe.servings})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Divider()
                        
                        // Nutrition preview
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Nutrition (for ${String.format("%.2f", servingsToLog)} serving${if (servingsToLog != 1.0) "s" else ""}):",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Calories: $calculatedCalories cal",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Protein: ${String.format("%.1f", calculatedProtein)}g",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF4CAF50)
                            )
                            Text(
                                text = "Carbs: ${String.format("%.1f", calculatedCarbs)}g",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF2196F3)
                            )
                            Text(
                                text = "Fat: ${String.format("%.1f", calculatedFat)}g",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFF9800)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { performLogMeal() },
                        enabled = !isLoggingMeal
                    ) {
                        if (isLoggingMeal) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Log Meal")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            if (!isLoggingMeal) {
                                showLogMealDialog = false
                                recipeToLog = null
                                servingsToLog = 1.0
                            }
                        },
                        enabled = !isLoggingMeal
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun RecipeCard(
    recipe: Recipe,
    onRecipeClick: () -> Unit,
    onLogMeal: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRefreshNutrition: () -> Unit = {},
    isRefreshing: Boolean = false
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recipe.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    recipe.description?.let { desc ->
                        if (desc.isNotBlank()) {
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                    }
                }
                
                // Menu button
                Box {
                    var showMenu by remember { mutableStateOf(false) }
                    
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Menu",
                            tint = Primary40
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("View") },
                            onClick = {
                                showMenu = false
                                onRecipeClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.RestaurantMenu, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Log Meal") },
                            onClick = {
                                showMenu = false
                                onLogMeal()
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Add, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Edit, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isRefreshing) "Refreshing..." else "Refresh Nutrition") },
                            onClick = {
                                if (!isRefreshing) {
                                    showMenu = false
                                    onRefreshNutrition()
                                }
                            },
                            enabled = !isRefreshing,
                            leadingIcon = {
                                if (isRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Filled.Refresh, contentDescription = null)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = {
                                showMenu = false
                                onShare()
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Share, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            }
                        )
                    }
                }
            }
            
            // Nutrition info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${recipe.totalCalories} cal",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${recipe.totalProteinG}g protein",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4CAF50)
                )
                Text(
                    text = "${recipe.totalCarbsG}g carbs",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2196F3)
                )
                Text(
                    text = "${recipe.totalFatG}g fat",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF9800)
                )
            }
            
            Text(
                text = "${recipe.servings} serving${if (recipe.servings != 1) "s" else ""} • ${recipe.ingredients.size} ingredients",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

