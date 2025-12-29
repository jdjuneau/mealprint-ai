package com.coachie.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.coachie.app.data.model.HealthLog
import com.coachie.app.data.model.MicronutrientType
import com.coachie.app.data.model.formatMicronutrientAmount
import com.coachie.app.data.model.Recipe
import com.coachie.app.data.FirebaseRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.components.RecipeShareDialog
import com.coachie.app.ui.theme.Primary40
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.data.model.PublicUserProfile
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.coachie.app.service.ShareService
import androidx.compose.foundation.background
import android.net.Uri
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import java.util.*
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealDetailScreen(
    meal: HealthLog.MealLog,
    entryId: String? = null,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onNavigateToMyRecipes: () -> Unit = {}
) {
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val micronutrients = meal.micronutrientsTyped
    val repository = remember { FirebaseRepository.getInstance() }
    val userId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val shareService = remember { ShareService.getInstance(context) }
    
    // Menu state
    var showMenu by remember { mutableStateOf(false) }
    
    // Share dialog state
    var showShareDialog by remember { mutableStateOf(false) }
    var friends by remember { mutableStateOf<List<PublicUserProfile>>(emptyList()) }
    var selectedFriends by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSharingToForum by remember { mutableStateOf(false) }
    var isSharingToCircle by remember { mutableStateOf(false) }
    
    // Photo capture state for recipe sharing
    var mealPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var currentPhotoFile by remember { mutableStateOf<File?>(null) }
    var hasCameraPermission by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    ) }
    
    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && mealPhotoUri != null) {
            // Photo captured successfully, mealPhotoUri already set
        }
    }
    
    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { mealPhotoUri = it }
    }
    
    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            // Create photo file and launch camera
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val imageFileName = "RECIPE_SHARE_${timeStamp}_"
            val storageDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            val imageFile = File.createTempFile(imageFileName, ".jpg", storageDir)
            currentPhotoFile = imageFile
            
            mealPhotoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
            mealPhotoUri?.let { uri ->
                cameraLauncher.launch(uri)
            }
        }
    }
    
    // Recipe expansion state
    var isRecipeExpanded by remember { mutableStateOf(false) }
    
    // Load recipe if meal has a recipeId
    var recipe by remember { mutableStateOf<Recipe?>(null) }
    var isLoadingRecipe by remember { mutableStateOf(false) }
    var isRecipeOwner by remember { mutableStateOf(false) }
    
    LaunchedEffect(meal.recipeId) {
        meal.recipeId?.let { recipeId ->
            isLoadingRecipe = true
            coroutineScope.launch(Dispatchers.IO) {
                // Try personal recipes first, then shared recipes
                val result = repository.getRecipe(userId, recipeId)
                result.fold(
                    onSuccess = { loadedRecipe: Recipe? ->
                        withContext(Dispatchers.Main) {
                            loadedRecipe?.let { recipeValue ->
                                recipe = recipeValue
                                isRecipeOwner = recipeValue.userId == userId
                            }
                            isLoadingRecipe = false
                        }
                    },
                    onFailure = {
                        // Try shared recipes
                        val sharedResult = repository.getSharedRecipe(recipeId)
                        sharedResult.fold(
                            onSuccess = { sharedRecipe ->
                                withContext(Dispatchers.Main) {
                                    recipe = sharedRecipe
                                    isRecipeOwner = sharedRecipe.userId == userId
                                    isLoadingRecipe = false
                                }
                            },
                            onFailure = {
                                withContext(Dispatchers.Main) {
                                    isLoadingRecipe = false
                                }
                            }
                        )
                    }
                )
            }
        } ?: run {
            recipe = null
        }
    }
    
    // Load friends when share dialog opens
    LaunchedEffect(showShareDialog) {
        if (showShareDialog) {
            coroutineScope.launch(Dispatchers.IO) {
                repository.getFriends(userId).fold(
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
    
    // Handle recipe actions
    fun handleSaveRecipe() {
        recipe?.let { currentRecipe ->
            coroutineScope.launch(Dispatchers.IO) {
                val result = repository.saveRecipe(userId, currentRecipe)
                withContext(Dispatchers.Main) {
                    result.fold(
                        onSuccess = {
                            Toast.makeText(context, "Recipe saved successfully!", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { error ->
                            Toast.makeText(context, "Failed to save recipe: ${error.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
        showMenu = false
    }
    
    fun handleShareRecipe() {
        if (recipe != null) {
            showShareDialog = true
            showMenu = false
        }
    }
    
    fun handleShareWithFriends() {
        recipe?.let { currentRecipe ->
            if (selectedFriends.isNotEmpty()) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        // First save the recipe to personal collection if not already saved
                        val saveResult = repository.saveRecipe(userId, currentRecipe)
                        if (saveResult.isFailure) {
                            android.util.Log.w("MealDetailScreen", "Failed to save recipe to personal collection, continuing with share")
                        }
                        
                        // Create shared recipe with sharing enabled
                        val sharedRecipe = currentRecipe.copy(
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
                        
                        val firestore = FirebaseFirestore.getInstance()
                        firestore.collection("sharedRecipes")
                            .document(sharedRecipe.id)
                            .set(recipeData)
                            .await()
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Recipe shared successfully!", Toast.LENGTH_SHORT).show()
                            showShareDialog = false
                            selectedFriends = emptySet()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MealDetailScreen", "Error sharing recipe", e)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = gradientBackground)
    ) {
        Scaffold(
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
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                "Back",
                                tint = Primary40
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                meal.foodName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Primary40
                            )
                            // Show recipe icon if meal has a recipe
                            if (meal.recipeId != null) {
                                Icon(
                                    Icons.Filled.RestaurantMenu,
                                    contentDescription = "Has recipe",
                                    modifier = Modifier.size(20.dp),
                                    tint = Primary40
                                )
                            }
                        }
                        IconButton(onClick = onEdit) {
                            Icon(
                                Icons.Filled.Edit,
                                "Edit",
                                tint = Primary40
                            )
                        }
                        
                        // Recipe menu button (only show if meal has a recipe)
                        if (meal.recipeId != null) {
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        "Recipe Menu",
                                        tint = Primary40
                                    )
                                }
                                
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("View Full Recipe") },
                                        onClick = {
                                            isRecipeExpanded = true
                                            showMenu = false
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Visibility, contentDescription = null)
                                        }
                                    )
                                    
                                    if (isRecipeOwner) {
                                        DropdownMenuItem(
                                            text = { Text("Edit Recipe") },
                                            onClick = {
                                                // TODO: Navigate to recipe edit screen
                                                Toast.makeText(context, "Recipe editing coming soon", Toast.LENGTH_SHORT).show()
                                                showMenu = false
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Filled.Edit, contentDescription = null)
                                            }
                                        )
                                    }
                                    
                                    DropdownMenuItem(
                                        text = { Text("Save Recipe") },
                                        onClick = { handleSaveRecipe() },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Save, contentDescription = null)
                                        }
                                    )
                                    
                                    DropdownMenuItem(
                                        text = { Text("Share Recipe") },
                                        onClick = { handleShareRecipe() },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Share, contentDescription = null)
                                        }
                                    )
                                    
                                    DropdownMenuItem(
                                        text = { Text("View All Recipes") },
                                        onClick = {
                                            showMenu = false
                                            onNavigateToMyRecipes()
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Filled.RestaurantMenu, contentDescription = null)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Time
                CoachieCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CoachieCardDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                    ),
                    elevation = CoachieCardDefaults.cardElevation(defaultElevation = 2.dp),
                    applyDefaultBorder = false // Remove border to prevent double-box effect
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Time",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(meal.timestamp)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Calories
                CoachieCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CoachieCardDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                    ),
                    elevation = CoachieCardDefaults.cardElevation(defaultElevation = 2.dp),
                    applyDefaultBorder = false // Remove border to prevent double-box effect
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Calories",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${meal.calories} cal",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Primary40
                        )
                    }
                }

                // Macros with Pie Chart
                CoachieCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CoachieCardDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                    ),
                    elevation = CoachieCardDefaults.cardElevation(defaultElevation = 2.dp),
                    applyDefaultBorder = false // Remove border to prevent double-box effect
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Macronutrients",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // Pie Chart - calculate based on GRAMS, not calories
                        // Pie charts show the percentage breakdown of total grams (protein + carbs + fat)
                        val totalGrams = meal.protein + meal.carbs + meal.fat
                        
                        if (totalGrams > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Pie chart
                                Canvas(
                                    modifier = Modifier.size(200.dp)
                                ) {
                                    val center = Offset(size.width / 2, size.height / 2)
                                    val radius = (size.minDimension / 2 - 20.dp.toPx()).coerceAtLeast(0f)

                                    val proteinColor = Color(0xFF4CAF50) // Green
                                    val carbsColor = Color(0xFF2196F3) // Blue
                                    val fatColor = Color(0xFFFF9800) // Orange

                                    var startAngle = -90f // Start from top

                                    // Protein - based on GRAMS
                                    val proteinAngle = (meal.protein / totalGrams.toFloat()) * 360f
                                    drawArc(
                                        color = proteinColor,
                                        startAngle = startAngle,
                                        sweepAngle = proteinAngle,
                                        useCenter = true,
                                        topLeft = Offset(center.x - radius, center.y - radius),
                                        size = Size(radius * 2, radius * 2)
                                    )
                                    startAngle += proteinAngle

                                    // Carbs - based on GRAMS
                                    val carbsAngle = (meal.carbs / totalGrams.toFloat()) * 360f
                                    drawArc(
                                        color = carbsColor,
                                        startAngle = startAngle,
                                        sweepAngle = carbsAngle,
                                        useCenter = true,
                                        topLeft = Offset(center.x - radius, center.y - radius),
                                        size = Size(radius * 2, radius * 2)
                                    )
                                    startAngle += carbsAngle

                                    // Fat - based on GRAMS
                                    val fatAngle = (meal.fat / totalGrams.toFloat()) * 360f
                                    drawArc(
                                        color = fatColor,
                                        startAngle = startAngle,
                                        sweepAngle = fatAngle,
                                        useCenter = true,
                                        topLeft = Offset(center.x - radius, center.y - radius),
                                        size = Size(radius * 2, radius * 2)
                                    )
                                }
                                
                                // Legend
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Calculate percentages based on GRAMS
                                    val proteinPercent = ((meal.protein / totalGrams.toFloat()) * 100).toInt()
                                    val carbsPercent = ((meal.carbs / totalGrams.toFloat()) * 100).toInt()
                                    val fatPercent = ((meal.fat / totalGrams.toFloat()) * 100).toInt()
                                    
                                    MacroLegendItem("Protein", meal.protein, "${proteinPercent}%", Color(0xFF4CAF50))
                                    MacroLegendItem("Carbs", meal.carbs, "${carbsPercent}%", Color(0xFF2196F3))
                                    MacroLegendItem("Fat", meal.fat, "${fatPercent}%", Color(0xFFFF9800))
                                }
                            }
                        } else {
                            Text(
                                "No macro data available",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // Additional macro details
                        if (meal.sugar > 0 || meal.addedSugar > 0) {
                            Divider()
                            if (meal.sugar > 0) {
                                MacroRow("Sugar", meal.sugar, "g", MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (meal.addedSugar > 0) {
                                MacroRow("Added Sugar", meal.addedSugar, "g", MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                // Recipe Section (if meal has a recipe)
                if (meal.recipeId != null) {
                    CoachieCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CoachieCardDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        ),
                        elevation = CoachieCardDefaults.cardElevation(defaultElevation = 2.dp),
                        applyDefaultBorder = false // Remove border to prevent double-box effect
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
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.RestaurantMenu,
                                        contentDescription = null,
                                        tint = Primary40
                                    )
                                    Text(
                                        "Recipe",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (meal.servingsConsumed != null) {
                                    Text(
                                        "${meal.servingsConsumed} serving${if (meal.servingsConsumed != 1.0) "s" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            if (isLoadingRecipe) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Primary40
                                )
                            } else {
                                val currentRecipe = recipe
                                if (currentRecipe != null) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        currentRecipe.description?.let { desc ->
                                            Text(
                                                desc,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        
                                        if (currentRecipe.ingredients.isNotEmpty()) {
                                            Text(
                                                "Ingredients:",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            val ingredientsToShow = if (isRecipeExpanded) currentRecipe.ingredients else currentRecipe.ingredients.take(5)
                                            ingredientsToShow.forEach { ingredient ->
                                                Text(
                                                    "• ${ingredient.quantity} ${ingredient.unit} ${ingredient.name}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (!isRecipeExpanded && currentRecipe.ingredients.size > 5) {
                                                Text(
                                                    "... and ${currentRecipe.ingredients.size - 5} more (tap menu → View Full Recipe)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    fontStyle = FontStyle.Italic
                                                )
                                            }
                                        }
                                        
                                        if (isRecipeExpanded && currentRecipe.instructions != null && currentRecipe.instructions!!.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                "Instructions:",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            currentRecipe.instructions!!.forEachIndexed { index, instruction ->
                                                Text(
                                                    "${index + 1}. $instruction",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        
                                        Text(
                                            "Recipe makes ${currentRecipe.servings} serving${if (currentRecipe.servings != 1) "s" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else {
                                    Text(
                                        "Recipe not found",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Micronutrients
                if (micronutrients.isNotEmpty()) {
                    CoachieCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CoachieCardDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        ),
                        elevation = CoachieCardDefaults.cardElevation(defaultElevation = 2.dp),
                        applyDefaultBorder = false // Remove border to prevent double-box effect
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Micronutrients",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            micronutrients.entries
                                .sortedBy { it.key.displayName }
                                .forEach { (type, amount) ->
                                    MicroRow(type, amount)
                                }
                        }
                    }
                }
            }
        }
        
        // Share Dialog
        val currentRecipeForShare = recipe
        if (showShareDialog && currentRecipeForShare != null) {
            RecipeShareDialog(
                recipe = currentRecipeForShare,
                friends = friends,
                selectedFriends = selectedFriends,
                onFriendToggle = { friendId ->
                    selectedFriends = if (selectedFriends.contains(friendId)) {
                        selectedFriends - friendId
                    } else {
                        selectedFriends + friendId
                    }
                },
                onShareWithFriends = { handleShareWithFriends() },
                onShareToForum = {
                    isSharingToForum = true
                    coroutineScope.launch(Dispatchers.IO) {
                        val result = repository.postRecipeToForum(currentRecipeForShare)
                        withContext(Dispatchers.Main) {
                            isSharingToForum = false
                            if (result.isSuccess) {
                                Toast.makeText(context, "Recipe posted to forum!", Toast.LENGTH_SHORT).show()
                                showShareDialog = false
                            } else {
                                Toast.makeText(context, "Failed to post to forum: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onShareToCircle = {
                    isSharingToCircle = true
                    coroutineScope.launch(Dispatchers.IO) {
                        val circleResult = repository.getOrCreateRecipeSharingCircle(userId)
                        circleResult.fold(
                            onSuccess = { circleId ->
                                val postResult = repository.postRecipeToCircle(currentRecipeForShare, circleId, userId)
                                withContext(Dispatchers.Main) {
                                    isSharingToCircle = false
                                    if (postResult.isSuccess) {
                                        Toast.makeText(context, "Recipe shared to your Recipe Sharing circle!", Toast.LENGTH_SHORT).show()
                                        showShareDialog = false
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
                    mealPhotoUri = null // Reset photo when dialog closes
                },
                isSharingToForum = isSharingToForum,
                isSharingToCircle = isSharingToCircle,
                mealPhotoUri = mealPhotoUri,
                onShareToInstagram = {
                    val imageGenerator = com.coachie.app.service.ShareImageGenerator(context)
                    val promoImageUri = if (mealPhotoUri != null) {
                        // Generate promotional post with meal photo
                        imageGenerator.generatePromotionalPostFromUri(
                            photoUri = mealPhotoUri,
                            cardTitle = currentRecipeForShare.name,
                            cardDescription = "Made with Coachie - Track your meals and nutrition!"
                        )
                    } else {
                        // Generate promotional post without photo (placeholder)
                        imageGenerator.generatePromotionalPost(
                            cardScreenshot = null,
                            cardTitle = currentRecipeForShare.name,
                            cardDescription = "Made with Coachie - Track your meals and nutrition!"
                        )
                    }
                    promoImageUri?.let { uri ->
                        shareService.shareToInstagramFeed(uri, addWatermark = false)
                        showShareDialog = false
                    } ?: run {
                        Toast.makeText(context, "Unable to generate share image. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                },
                onShareToFacebook = {
                    val imageGenerator = com.coachie.app.service.ShareImageGenerator(context)
                    val promoImageUri = if (mealPhotoUri != null) {
                        imageGenerator.generatePromotionalPostFromUri(
                            photoUri = mealPhotoUri,
                            cardTitle = currentRecipeForShare.name,
                            cardDescription = "Made with Coachie - Track your meals and nutrition!"
                        )
                    } else {
                        imageGenerator.generatePromotionalPost(
                            cardScreenshot = null,
                            cardTitle = currentRecipeForShare.name,
                            cardDescription = "Made with Coachie - Track your meals and nutrition!"
                        )
                    }
                    promoImageUri?.let { uri ->
                        shareService.shareToFacebook(uri, addWatermark = false)
                        showShareDialog = false
                    } ?: run {
                        Toast.makeText(context, "Unable to generate share image. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                },
                onShareToTikTok = {
                    val imageGenerator = com.coachie.app.service.ShareImageGenerator(context)
                    val promoImageUri = if (mealPhotoUri != null) {
                        imageGenerator.generatePromotionalPostFromUri(
                            photoUri = mealPhotoUri,
                            cardTitle = currentRecipeForShare.name,
                            cardDescription = "Made with Coachie - Track your meals and nutrition!"
                        )
                    } else {
                        imageGenerator.generatePromotionalPost(
                            cardScreenshot = null,
                            cardTitle = currentRecipeForShare.name,
                            cardDescription = "Made with Coachie - Track your meals and nutrition!"
                        )
                    }
                    promoImageUri?.let { uri ->
                        shareService.shareToTikTok(uri, addWatermark = false)
                        showShareDialog = false
                    } ?: run {
                        Toast.makeText(context, "Unable to generate share image. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                },
                onShareToX = {
                    val imageGenerator = com.coachie.app.service.ShareImageGenerator(context)
                    val promoImageUri = if (mealPhotoUri != null) {
                        imageGenerator.generatePromotionalPostFromUri(
                            photoUri = mealPhotoUri,
                            cardTitle = currentRecipeForShare.name,
                            cardDescription = "Made with Coachie - Track your meals and nutrition!"
                        )
                    } else {
                        imageGenerator.generatePromotionalPost(
                            cardScreenshot = null,
                            cardTitle = currentRecipeForShare.name,
                            cardDescription = "Made with Coachie - Track your meals and nutrition!"
                        )
                    }
                    promoImageUri?.let { uri ->
                        shareService.shareToX(uri, addWatermark = false)
                        showShareDialog = false
                    } ?: run {
                        Toast.makeText(context, "Unable to generate share image. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                },
                onShareToOther = {
                    val imageGenerator = com.coachie.app.service.ShareImageGenerator(context)
                    val promoImageUri = if (mealPhotoUri != null) {
                        imageGenerator.generatePromotionalPostFromUri(
                            photoUri = mealPhotoUri,
                            cardTitle = currentRecipeForShare.name,
                            cardDescription = "Made with Coachie - Track your meals and nutrition!"
                        )
                    } else {
                        imageGenerator.generatePromotionalPost(
                            cardScreenshot = null,
                            cardTitle = currentRecipeForShare.name,
                            cardDescription = "Made with Coachie - Track your meals and nutrition!"
                        )
                    }
                    promoImageUri?.let { uri ->
                        shareService.shareImage(
                            uri,
                            "Share Recipe: ${currentRecipeForShare.name}",
                            "Check out Coachie on Google Play!",
                            addWatermark = false
                        )
                        showShareDialog = false
                    } ?: run {
                        Toast.makeText(context, "Unable to generate share image. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                },
                onCapturePhoto = {
                    if (hasCameraPermission) {
                        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        val imageFileName = "RECIPE_SHARE_${timeStamp}_"
                        val storageDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
                        val imageFile = File.createTempFile(imageFileName, ".jpg", storageDir)
                        currentPhotoFile = imageFile
                        
                        mealPhotoUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            imageFile
                        )
                        mealPhotoUri?.let { uri ->
                            cameraLauncher.launch(uri)
                        }
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                onSelectPhoto = {
                    imagePickerLauncher.launch("image/*")
                }
            )
        }
    }
}

@Composable
private fun MacroRow(label: String, value: Int, unit: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "$value $unit",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

@Composable
private fun MacroLegendItem(label: String, value: Int, unit: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(color, androidx.compose.foundation.shape.CircleShape)
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            "$value $unit",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

@Composable
private fun MicroRow(type: MicronutrientType, amount: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            type.displayName,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "${amount.formatMicronutrientAmount()}${type.unit.displaySuffix}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

