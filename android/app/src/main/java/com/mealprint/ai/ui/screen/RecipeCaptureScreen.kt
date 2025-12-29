package com.coachie.app.ui.screen

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Article
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.data.local.PreferencesManager
import com.mealprint.ai.data.model.Recipe
import com.mealprint.ai.data.model.PublicUserProfile
import com.mealprint.ai.ui.components.CoachieCard
import com.mealprint.ai.ui.components.CoachieCardDefaults
import com.mealprint.ai.ui.theme.rememberCoachieGradient
import com.mealprint.ai.viewmodel.RecipeCaptureUiState
import com.mealprint.ai.viewmodel.RecipeCaptureViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeCaptureScreen(
    userId: String? = null,
    onBack: () -> Unit = {},
    onRecipeSaved: () -> Unit = {},
    viewModel: RecipeCaptureViewModel = viewModel(
        factory = RecipeCaptureViewModel.provideFactory(
            context = LocalContext.current,
            firebaseRepository = FirebaseRepository.getInstance(),
            userId = userId ?: com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: "anonymous_user"
        )
    )
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    var inputMode by rememberSaveable { mutableStateOf<InputMode>(InputMode.Photo) }
    var recipeText by rememberSaveable { mutableStateOf("") }
    var servings by rememberSaveable { mutableStateOf("4") }
    var showShareDialog by rememberSaveable { mutableStateOf(false) }
    var showPostToForumDialog by rememberSaveable { mutableStateOf(false) }
    var currentRecipe by remember { mutableStateOf<Recipe?>(null) }
    var friends by remember { mutableStateOf<List<PublicUserProfile>>(emptyList()) }
    var selectedFriends by remember { mutableStateOf<Set<String>>(emptySet()) }
    var recipeSharingForumId by remember { mutableStateOf<String?>(null) }
    val repository = FirebaseRepository.getInstance()

    // Load Recipe Sharing forum ID
    LaunchedEffect(Unit) {
        repository.getForums().fold(
            onSuccess = { forums ->
                recipeSharingForumId = forums.find { it.title == "Recipe Sharing" }?.id
            },
            onFailure = {}
        )
    }
    
    // Load friends when share dialog opens
    LaunchedEffect(showShareDialog) {
        if (showShareDialog) {
            repository.getFriends(userId ?: "").fold(
                onSuccess = { friendsList ->
                    friends = friendsList
                },
                onFailure = {}
            )
        }
    }
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    var currentPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var currentPhotoFile: File? = null

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoUri != null) {
            viewModel.analyzeRecipeFromImage(currentPhotoUri!!, servings.toIntOrNull() ?: 4)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            currentPhotoUri = it
            viewModel.analyzeRecipeFromImage(it, servings.toIntOrNull() ?: 4)
        }
    }

    // Handle success and errors
    LaunchedEffect(uiState) {
        val currentState = uiState
        when (currentState) {
            is RecipeCaptureUiState.Success -> {
                // Show success message if it's a sharing success
                if (currentState.message.contains("shared", ignoreCase = true)) {
                    android.widget.Toast.makeText(
                        context,
                        currentState.message,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                onRecipeSaved()
            }
            is RecipeCaptureUiState.Error -> {
                // Show error message to user
                android.widget.Toast.makeText(
                    context,
                    "Error: ${currentState.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                android.util.Log.e("RecipeCaptureScreen", "Error state: ${currentState.message}")
            }
            else -> {}
        }
    }

    // Update current recipe when analysis completes
    LaunchedEffect(uiState) {
        val currentState = uiState
        if (currentState is RecipeCaptureUiState.AnalysisResult) {
            currentRecipe = currentState.recipe
        }
    }

    val gradient = rememberCoachieGradient()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Recipe Analysis") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.Black,
                    navigationIconContentColor = Color.Black
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (uiState) {
                    is RecipeCaptureUiState.Idle -> {
                        // Input mode selection
                        Text(
                            "Analyze Recipe",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            "Take a photo or upload an image of your recipe to get accurate macro and micronutrient estimates per serving",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )

                        // Mode selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = inputMode == InputMode.Photo,
                                onClick = { inputMode = InputMode.Photo },
                                label = { Text("📷 Photo") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = inputMode == InputMode.Text,
                                onClick = { inputMode = InputMode.Text },
                                label = { Text("✍️ Text") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Servings input
                        OutlinedTextField(
                            value = servings,
                            onValueChange = { servings = it },
                            label = { Text("Number of Servings") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Input based on mode
                        when (inputMode) {
                            InputMode.Photo -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        "Choose how to add your recipe photo:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Take Photo Button
                                        Button(
                                            onClick = {
                                                if (hasCameraPermission) {
                                                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                                    val imageFileName = "RECIPE_${timeStamp}_"
                                                    val storageDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
                                                    val imageFile = File.createTempFile(imageFileName, ".jpg", storageDir)
                                                    currentPhotoFile = imageFile
                                                    
                                                    currentPhotoUri = FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        imageFile
                                                    )
                                                    currentPhotoUri?.let { uri ->
                                                        cameraLauncher.launch(uri)
                                                    }
                                                } else {
                                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFFF9800), // Orange background
                                                contentColor = Color.Black // Black text for readability
                                            )
                                        ) {
                                            Icon(Icons.Filled.Camera, contentDescription = null, tint = Color.Black)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Take Photo", color = Color.Black)
                                        }
                                        
                                        // Upload Photo Button
                                        Button(
                                            onClick = { imagePickerLauncher.launch("image/*") },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFFF9800), // Orange background
                                                contentColor = Color.Black // Black text for readability
                                            )
                                        ) {
                                            Icon(Icons.Filled.Image, contentDescription = null, tint = Color.Black)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Upload Photo", color = Color.Black)
                                        }
                                    }
                                }
                            }
                            InputMode.Text -> {
                                OutlinedTextField(
                                    value = recipeText,
                                    onValueChange = { recipeText = it },
                                    label = { Text("Paste Recipe Text") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    minLines = 8,
                                    maxLines = 12
                                )
                                
                                Button(
                                    onClick = {
                                        if (recipeText.isNotBlank() && servings.toIntOrNull() != null) {
                                            viewModel.analyzeRecipeFromText(recipeText, servings.toIntOrNull() ?: 4)
                                        }
                                    },
                                    enabled = recipeText.isNotBlank() && servings.toIntOrNull() != null,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Analyze Recipe")
                                }
                            }
                        }
                    }

                    is RecipeCaptureUiState.PhotoTaken -> {
                        // Show photo preview
                        currentPhotoUri?.let { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = "Recipe photo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    is RecipeCaptureUiState.Analyzing -> {
                        val analyzingState = uiState as RecipeCaptureUiState.Analyzing
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(analyzingState.progress)
                        }
                    }

                    is RecipeCaptureUiState.AnalysisResult -> {
                        val resultState = uiState as RecipeCaptureUiState.AnalysisResult
                        val recipe = resultState.recipe
                        val perServing = recipe.getNutritionPerServing()

                        // Recipe name
                        Text(
                            recipe.name,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )

                        recipe.description?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // Photo if available
                        resultState.photoUri?.let { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = "Recipe photo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentScale = ContentScale.Crop
                            )
                        }

                        // Nutrition per serving
                        CoachieCard {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Nutrition Per Serving (${recipe.servings} servings total)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    NutritionItem("Calories", "${perServing.calories}")
                                    NutritionItem("Protein", "${perServing.proteinG}g")
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    NutritionItem("Carbs", "${perServing.carbsG}g")
                                    NutritionItem("Fat", "${perServing.fatG}g")
                                }
                            }
                        }

                        // Ingredients
                        CoachieCard {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Ingredients",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                recipe.ingredients.forEach { ingredient ->
                                    Text(
                                        "• ${ingredient.quantity} ${ingredient.unit} ${ingredient.name}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        // Instructions if available
                        recipe.instructions?.takeIf { it.isNotEmpty() }?.let { instructions ->
                            CoachieCard {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "Instructions",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    instructions.forEachIndexed { index, instruction ->
                                        Text(
                                            "${index + 1}. $instruction",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }

                        // Action buttons
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.saveRecipeToQuickSave(recipe)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Save, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Save to Quick Select")
                                }
                                
                                OutlinedButton(
                                    onClick = { showShareDialog = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Share with Friends")
                                }
                            }
                            
                            if (recipeSharingForumId != null) {
                                OutlinedButton(
                                    onClick = { 
                                        currentRecipe = recipe
                                        showPostToForumDialog = true 
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Article, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Post to Recipe Sharing Forum")
                                }
                            }
                        }
                    }

                    is RecipeCaptureUiState.Error -> {
                        val errorState = uiState as RecipeCaptureUiState.Error
                        CoachieCard {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Error",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(errorState.message)
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = { viewModel.reset() }) {
                                    Text("Try Again")
                                }
                            }
                        }
                    }

                    is RecipeCaptureUiState.Success -> {
                        CoachieCard {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(64.dp)
                                )
                                Text(
                                    "Recipe Saved!",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("You can now find it in your saved meals for quick logging.")
                            }
                        }
                    }
                }
            }
        }
        
        // Share Dialog
        if (showShareDialog && currentRecipe != null) {
            RecipeShareDialog(
                recipe = currentRecipe!!,
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
                    if (selectedFriends.isNotEmpty()) {
                        // Don't show toast here - wait for actual success from ViewModel
                        viewModel.shareRecipeWithFriends(currentRecipe!!, selectedFriends.toList())
                        showShareDialog = false
                        selectedFriends = emptySet()
                        // Toast will be shown by LaunchedEffect when RecipeCaptureUiState.Success is received
                    }
                },
                onDismiss = {
                    showShareDialog = false
                    selectedFriends = emptySet()
                }
            )
        }

        // Post to Forum Dialog
        if (showPostToForumDialog && currentRecipe != null && recipeSharingForumId != null) {
            PostRecipeToForumDialog(
                recipe = currentRecipe!!,
                forumId = recipeSharingForumId!!,
                onPost = { title, content ->
                    viewModel.postRecipeToForum(currentRecipe!!, recipeSharingForumId!!, title, content)
                    showPostToForumDialog = false
                },
                onDismiss = {
                    showPostToForumDialog = false
                }
            )
        }
    }
}

@Composable
private fun RecipeShareDialog(
    recipe: Recipe,
    friends: List<PublicUserProfile>,
    selectedFriends: Set<String>,
    onFriendToggle: (String) -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share Recipe: ${recipe.name}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Select friends to share this recipe with:",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                if (friends.isEmpty()) {
                    Text(
                        "No friends yet. Add friends to share recipes!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    friends.forEach { friend ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onFriendToggle(friend.uid) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedFriends.contains(friend.uid),
                                onCheckedChange = { onFriendToggle(friend.uid) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(friend.displayName ?: friend.username ?: "Unknown")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onShare,
                enabled = selectedFriends.isNotEmpty()
            ) {
                Text("Share")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PostRecipeToForumDialog(
    recipe: Recipe,
    forumId: String,
    onPost: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var postTitle by remember { mutableStateOf(recipe.name) }
    var postContent by remember { mutableStateOf(recipe.description ?: "") }
    var isPosting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Post Recipe to Forum") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Share this recipe with the community!",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                OutlinedTextField(
                    value = postTitle,
                    onValueChange = { postTitle = it },
                    label = { Text("Post Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = postContent,
                    onValueChange = { postContent = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
                
                Text(
                    "The recipe (with all macros and micros) will be attached to your post.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (postTitle.isNotBlank()) {
                        isPosting = true
                        onPost(postTitle.trim(), postContent.trim())
                    }
                },
                enabled = !isPosting && postTitle.isNotBlank()
            ) {
                if (isPosting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Post")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun NutritionItem(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

private enum class InputMode {
    Photo, Text
}

