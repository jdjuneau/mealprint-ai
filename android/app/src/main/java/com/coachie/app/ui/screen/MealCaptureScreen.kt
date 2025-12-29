package com.coachie.app.ui.screen

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.*
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.theme.rememberCoachieGradient
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import coil.compose.AsyncImage
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.ai.MealAnalysis
import com.coachie.app.data.api.EnhancedFoodDatabase
import com.coachie.app.data.local.PreferencesManager
import com.coachie.app.data.model.FoodDatabase
import com.coachie.app.util.MicronutrientEstimator
import com.coachie.app.data.model.FoodItem
import com.coachie.app.data.model.NutritionInfo
import com.coachie.app.data.model.MicronutrientType
import com.coachie.app.data.model.SavedMeal
import com.coachie.app.data.model.formatMicronutrientAmount
import com.coachie.app.data.model.Portion
import com.coachie.app.data.model.HealthLog
import com.coachie.app.data.model.PublicUserProfile
import com.coachie.app.ui.components.ShareDialog
import com.coachie.app.viewmodel.MealCaptureUiState
import com.coachie.app.viewmodel.MealCaptureViewModel
import com.coachie.app.viewmodel.MealItem
import androidx.compose.material.icons.filled.Share
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealCaptureScreen(
    userId: String? = null,
    onBack: () -> Unit = {},
    onMealSaved: () -> Unit = {},
    onNavigateToSavedMeals: () -> Unit = {},
    onNavigateToMealRecommendation: () -> Unit = {},
    onNavigateToRecipeCapture: () -> Unit = {},
    onNavigateToMyRecipes: () -> Unit = {},
    navBackStackEntry: NavBackStackEntry? = null,
    viewModel: MealCaptureViewModel = viewModel(
        factory = MealCaptureViewModel.Factory(
            context = LocalContext.current,
            firebaseRepository = FirebaseRepository.getInstance(),
            preferencesManager = PreferencesManager(LocalContext.current),
            userId = userId ?: com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: "anonymous_user"
        )
    )
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val hasCameraPermission by viewModel.hasCameraPermission.collectAsState()
    val useImperial by viewModel.useImperial.collectAsState()
    val gender by viewModel.gender.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val repository = FirebaseRepository.getInstance()

    // Share dialog state
    var showShareDialog by rememberSaveable { mutableStateOf(false) }
    var friends by remember { mutableStateOf<List<PublicUserProfile>>(emptyList()) }
    var selectedFriends by remember { mutableStateOf<Set<String>>(emptySet()) }
    var mealToShare by remember { mutableStateOf<HealthLog.MealLog?>(null) }

    // Load friends when share dialog opens
    LaunchedEffect(showShareDialog) {
        if (showShareDialog && userId != null) {
            repository.getFriends(userId).fold(
                onSuccess = { friendsList ->
                    friends = friendsList
                },
                onFailure = {}
            )
        }
    }

    // EnhancedFoodDatabase is a singleton object, no need to instantiate

    // Handle selected saved meal from navigation - show serving size dialog first
    var showServingSizeDialog by rememberSaveable { mutableStateOf(false) }
    var selectedSavedMeal by remember { mutableStateOf<SavedMeal?>(null) }
    var servingSize by rememberSaveable { mutableStateOf("1.0") }
    
    LaunchedEffect(navBackStackEntry) {
        navBackStackEntry?.savedStateHandle?.get<SavedMeal>("selected_saved_meal")?.let { savedMeal ->
            android.util.Log.d("MealCaptureScreen", "🔄 Saved meal selected: ${savedMeal.name}")
            // Show serving size dialog first
            selectedSavedMeal = savedMeal
            servingSize = "1.0"
            showServingSizeDialog = true
            // Clear the saved state
            navBackStackEntry.savedStateHandle.remove<SavedMeal>("selected_saved_meal")
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.updateCameraPermission(granted)
    }

    // Track if we're scanning barcode or taking meal photo
    var isBarcodeMode by rememberSaveable { mutableStateOf(false) }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            viewModel.onPhotoTaken()
            // Check if we're in barcode mode after photo is taken
            if (isBarcodeMode) {
                // Wait a bit for state to update, then scan barcode
                coroutineScope.launch {
                    kotlinx.coroutines.delay(100)
                    val currentState = uiState
                    if (currentState is MealCaptureUiState.PhotoTaken) {
                        viewModel.scanBarcode(currentState.photoUri)
                        isBarcodeMode = false // Reset after scanning
                    }
                }
            }
        }
    }

    // Request camera permission when screen opens
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Handle successful save - only navigate after meal is actually saved
    // This should NOT trigger during photo analysis
    LaunchedEffect(uiState) {
        if (uiState is MealCaptureUiState.Success) {
            android.util.Log.d("MealCaptureScreen", "Meal saved successfully, navigating back")
            onMealSaved()
        }
    }

    val gradient = rememberCoachieGradient()
    val snackbarHostState = remember { SnackbarHostState() }
    val saveSuccessMessage by viewModel.saveSuccessMessage.collectAsState()
    
    // Show snackbar when meal is saved
    LaunchedEffect(saveSuccessMessage) {
        saveSuccessMessage?.let { message ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(message)
                viewModel.clearSaveMessage()
            }
        }
    }
    
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Log Meal") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToMealRecommendation) {
                        Text(
                            "🤖",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.Black,
                    navigationIconContentColor = Color.Black,
                    actionIconContentColor = Color.Black
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
            // Recipe Analysis Button
            OutlinedButton(
                onClick = { onNavigateToRecipeCapture() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(Icons.Filled.Restaurant, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("📝 Analyze Recipe")
            }

            // Mode selector: Camera, Barcode, or Manual
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState is MealCaptureUiState.Idle || uiState is MealCaptureUiState.PhotoTaken,
                    onClick = {
                        isBarcodeMode = false
                        if (hasCameraPermission) {
                            viewModel.createPhotoFile(context)?.let { uri ->
                                cameraLauncher.launch(uri)
                            }
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    label = { Text("📷 Camera", color = Color.Black) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        selectedLabelColor = Color.Black
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        if (hasCameraPermission) {
                            viewModel.createPhotoFile(context)?.let { uri ->
                                cameraLauncher.launch(uri)
                                // After photo is taken, we'll scan for barcode
                            }
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    label = { Text("📱 Barcode", color = Color.Black) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        selectedLabelColor = Color.Black
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = uiState is MealCaptureUiState.ManualEntry,
                    onClick = { viewModel.setManualEntryMode() },
                    label = { Text("✍️ Manual", color = Color.Black) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        selectedLabelColor = Color.Black
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            when (val state = uiState) {
                is MealCaptureUiState.Idle -> {
                    // Show camera button
                    CoachieCard(
                        modifier = Modifier.fillMaxWidth(),
                        border = CoachieCardDefaults.border()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Camera,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "📸 Photograph your meal",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Hold your phone above the plate so the entire meal is visible. Good lighting helps AI identify foods accurately.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Tip: capture everything you ate in one photo. You can add or edit details after the analysis.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = {
                                    if (hasCameraPermission) {
                                        viewModel.createPhotoFile(context)?.let { uri ->
                                            cameraLauncher.launch(uri)
                                        }
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            ) {
                                Text("Take Photo")
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onNavigateToSavedMeals,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("⭐ Quick Select")
                                        Text("Saved Meals", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                OutlinedButton(
                                    onClick = onNavigateToMealRecommendation,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("🤖 AI Recipe")
                                        Text("Generator", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            
                            // View All Recipes button
                            OutlinedButton(
                                onClick = onNavigateToMyRecipes,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(Icons.Filled.RestaurantMenu, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("📋 View All Recipes")
                            }
                        }
                    }
                }

                is MealCaptureUiState.PhotoTaken -> {
                    PhotoPreviewContent(
                        photoUri = state.photoUri,
                        onRetake = { viewModel.resetState() },
                        onAnalyze = { uri ->
                            viewModel.analyzeFoodImage(uri)
                        }
                    )
                }

                is MealCaptureUiState.Analyzing -> {
                    AnalyzingContent()
                }

                is MealCaptureUiState.SearchingNutrition -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Searching for menu item nutrition…",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = state.analysis.menuItemName ?: "Menu item",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is MealCaptureUiState.AnalysisResult -> {
                    AnalysisResultContent(
                        analysis = state.analysis,
                        photoUri = state.photoUri,
                        mealItems = state.mealItems,
                        onEdit = { viewModel.editAnalysisResult() },
                        onFoodNameChange = { viewModel.updateAnalysisFood(it) },
                        onCaloriesChange = { viewModel.updateAnalysisCalories(it) },
                        onProteinChange = { viewModel.updateAnalysisProtein(it) },
                        onCarbsChange = { viewModel.updateAnalysisCarbs(it) },
                        onFatChange = { viewModel.updateAnalysisFat(it) },
                        onAddFoodItem = { viewModel.addFoodItemToAnalysisResult() },
                        onRemoveFoodItem = { index -> viewModel.removeFoodItemFromAnalysisResult(index) },
                        onEditFoodItem = { index, item -> viewModel.updateMealItem(index, item) },
                        onSplitIntoItems = { viewModel.splitAnalysisIntoItems() },
                        onAddMore = { viewModel.addAnalysisResultToMeal(it) },
                        onSave = { mealLog ->
                            viewModel.saveMealLog(mealLog)
                        },
                        onShare = { mealLog ->
                            mealToShare = mealLog
                            showShareDialog = true
                        },
                        onSaveForQuickSelect = { name ->
                            viewModel.saveMealForQuickSelect(name)
                        }
                    )
                }

                is MealCaptureUiState.ManualEntry -> {
                    val associatedRecipe by viewModel.associatedRecipe.collectAsState()
                    val servingsState by viewModel.servings.collectAsState()
                    ManualEntryContent(
                        foodName = state.foodName,
                        calories = state.calories,
                        protein = state.protein,
                        carbs = state.carbs,
                        fat = state.fat,
                        sugar = state.sugar,
                        addedSugar = state.addedSugar,
                        mealItems = state.mealItems,
                        useImperial = useImperial,
                        associatedRecipe = associatedRecipe,
                        initialServings = servingsState,
                        onFoodNameChange = { viewModel.updateFoodName(it) },
                        onCaloriesChange = { viewModel.updateCalories(it) },
                        onProteinChange = { viewModel.updateProtein(it) },
                        onCarbsChange = { viewModel.updateCarbs(it) },
                        onFatChange = { viewModel.updateFat(it) },
                        onSugarChange = { viewModel.updateSugar(it) },
                        onAddedSugarChange = { viewModel.updateAddedSugar(it) },
                        onMicronutrientsCalculated = { viewModel.updateCalculatedMicronutrients(it) },
                        onServingsChange = { viewModel.updateServings(it) },
                        onSaveSingleMeal = { viewModel.saveSingleMealEntry() },
                        onAddToMeal = { viewModel.addFoodToMeal() },
                        onRemoveFromMeal = { index -> viewModel.removeFoodFromMeal(index) },
                        onSaveCombinedMeal = { viewModel.saveCombinedMeal() },
                        onSaveForQuickSelect = { viewModel.saveMealForQuickSelect(it) },
                        onShare = { mealLog ->
                            mealToShare = mealLog
                            showShareDialog = true
                        }
                    )
                }

                is MealCaptureUiState.Saving -> {
                    SavingContent()
                }

                is MealCaptureUiState.Success -> {
                    SuccessContent(
                        onDone = {
                            viewModel.resetState()
                            onBack()
                        }
                    )
                }

                is MealCaptureUiState.Error -> {
                    ErrorContent(
                        error = state.message,
                        onRetry = { viewModel.resetState() },
                        onBack = onBack
                    )
                }
            }
            }
        }

        // Share Dialog
        if (showShareDialog && mealToShare != null) {
            ShareDialog(
                title = "Share Meal: ${mealToShare!!.foodName}",
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
                        viewModel.shareMealWithFriends(mealToShare!!, selectedFriends.toList())
                        showShareDialog = false
                        selectedFriends = emptySet()
                        mealToShare = null
                    }
                },
                onDismiss = {
                    showShareDialog = false
                    selectedFriends = emptySet()
                    mealToShare = null
                }
            )
        }
        
        // Serving Size Dialog for Quick Saved Meals
        if (showServingSizeDialog && selectedSavedMeal != null) {
            AlertDialog(
                onDismissRequest = {
                    showServingSizeDialog = false
                    selectedSavedMeal = null
                },
                title = {
                    Text(
                        "Select Serving Size",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "How many servings of \"${selectedSavedMeal!!.name}\"?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        OutlinedTextField(
                            value = servingSize,
                            onValueChange = { newValue ->
                                // Only allow numbers and decimal point
                                if (newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                                    servingSize = newValue
                                }
                            },
                            label = { Text("Serving Size") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        // Quick buttons for common serving sizes
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isSelected05 = servingSize == "0.5"
                            Button(
                                onClick = { servingSize = "0.5" },
                                modifier = Modifier.weight(1f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected05) 
                                        MaterialTheme.colorScheme.primaryContainer 
                                    else 
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = Color.Black
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    "0.5x",
                                    fontWeight = if (isSelected05) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip
                                )
                            }
                            val isSelected10 = servingSize == "1.0"
                            Button(
                                onClick = { servingSize = "1.0" },
                                modifier = Modifier.weight(1f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected10) 
                                        MaterialTheme.colorScheme.primaryContainer 
                                    else 
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = Color.Black
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    "1x",
                                    fontWeight = if (isSelected10) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip
                                )
                            }
                            val isSelected15 = servingSize == "1.5"
                            Button(
                                onClick = { servingSize = "1.5" },
                                modifier = Modifier.weight(1f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected15) 
                                        MaterialTheme.colorScheme.primaryContainer 
                                    else 
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = Color.Black
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    "1.5x",
                                    fontWeight = if (isSelected15) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip
                                )
                            }
                            val isSelected20 = servingSize == "2.0"
                            Button(
                                onClick = { servingSize = "2.0" },
                                modifier = Modifier.weight(1f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected20) 
                                        MaterialTheme.colorScheme.primaryContainer 
                                    else 
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = Color.Black
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    "2x",
                                    fontWeight = if (isSelected20) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val servingSizeValue = servingSize.toDoubleOrNull() ?: 1.0
                            if (servingSizeValue > 0) {
                                viewModel.loadSavedMeal(selectedSavedMeal!!, servingSizeValue)
                                showServingSizeDialog = false
                                selectedSavedMeal = null
                            }
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            "Confirm",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showServingSizeDialog = false
                            selectedSavedMeal = null
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            "Cancel",
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun ManualEntryContent(
    foodName: String,
    calories: String,
    protein: String,
    carbs: String,
    fat: String,
    sugar: String,
    addedSugar: String,
    mealItems: List<MealItem>,
    useImperial: Boolean,
    associatedRecipe: com.coachie.app.data.model.Recipe? = null,
    initialServings: Double = 1.0,
    onFoodNameChange: (String) -> Unit,
    onCaloriesChange: (String) -> Unit,
    onProteinChange: (String) -> Unit,
    onCarbsChange: (String) -> Unit,
    onFatChange: (String) -> Unit,
    onSugarChange: (String) -> Unit,
    onAddedSugarChange: (String) -> Unit,
    onMicronutrientsCalculated: (Map<MicronutrientType, Double>) -> Unit,
    onServingsChange: (Double) -> Unit,
    onSaveSingleMeal: () -> Unit,
    onAddToMeal: () -> Unit,
    onRemoveFromMeal: (Int) -> Unit,
    onSaveCombinedMeal: () -> Unit,
    onSaveForQuickSelect: (String) -> Unit,
    onShare: (HealthLog.MealLog) -> Unit
) {
    var showQuickSaveDialog by remember { mutableStateOf(false) }
    var showRecipe by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Enter Meal Details",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        // Display recipe if available
        associatedRecipe?.let { recipe ->
            CoachieCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CoachieCardDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📖 Recipe Available",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = { showRecipe = !showRecipe }) {
                            Icon(
                                imageVector = if (showRecipe) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showRecipe) "Hide recipe" else "Show recipe"
                            )
                        }
                    }
                    
                    if (showRecipe) {
                        Divider()
                        
                        recipe.description?.let { desc ->
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        if (recipe.ingredients.isNotEmpty()) {
                            Text(
                                text = "Ingredients:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            recipe.ingredients.forEach { ingredient ->
                                Text(
                                    text = "• ${ingredient.quantity} ${ingredient.unit} ${ingredient.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        recipe.instructions?.takeIf { it.isNotEmpty() }?.let { instructions ->
                            Text(
                                text = "Instructions:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            instructions.forEachIndexed { index, instruction ->
                                Text(
                                    text = "${index + 1}. $instruction",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                        
                        Text(
                            text = "Serves: ${recipe.servings}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }

        var foodSuggestions by remember { mutableStateOf<List<FoodItem>>(emptyList()) }
        var selectedFood by remember { mutableStateOf<FoodItem?>(null) }
        var showSuggestions by remember { mutableStateOf(false) }
        var portionMultiplier by remember { mutableStateOf(1f) }

        val minMultiplier = 0.25f
        val maxMultiplier = 4f
        val multiplierStep = 0.25f
        val sliderSteps = ((maxMultiplier - minMultiplier) / multiplierStep).roundToInt() - 1

        val coroutineScope = rememberCoroutineScope()
        
        LaunchedEffect(foodName) {
            if (foodName.isNotBlank() && foodName.length >= 2) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val results = EnhancedFoodDatabase.searchFoodsEnhanced(foodName)
                    foodSuggestions = results.take(8)
                    showSuggestions = results.isNotEmpty()
                }

                val exactMatch = FoodDatabase.getFoodByName(foodName)
                selectedFood = when {
                    exactMatch != null -> exactMatch
                    foodSuggestions.size == 1 -> foodSuggestions.first()
                    else -> null
                }
            } else {
                foodSuggestions = emptyList()
                showSuggestions = false
                selectedFood = null
            }
        }

        val basePortionGrams by remember(selectedFood) {
            mutableStateOf(selectedFood?.commonPortions?.firstOrNull()?.grams ?: 100.0)
        }

        val portionGrams = (basePortionGrams * portionMultiplier).coerceAtLeast(1.0)

        LaunchedEffect(selectedFood) {
            portionMultiplier = 1f
        }

        LaunchedEffect(selectedFood, portionGrams) {
            selectedFood?.let { food ->
                val multiplier = portionGrams / 100.0
                val calculatedCalories = (food.calories * multiplier).toInt()
                val calculatedProtein = food.protein * multiplier
                val calculatedCarbs = food.carbs * multiplier
                val calculatedFat = food.fat * multiplier
                
                // Estimate sugar if missing (sugar = 0) but carbs are present
                val baseSugar = if (food.sugar == 0.0 && food.carbs > 0) {
                    estimateSugarFromCarbsForFood(food.carbs, food.name)
                } else {
                    food.sugar
                }
                val calculatedSugar = baseSugar * multiplier
                val calculatedAddedSugar = food.addedSugar * multiplier
                
                // Debug logging for sugar calculation
                android.util.Log.d("MealCaptureScreen", "🍭 Sugar calculation for ${food.name}:")
                android.util.Log.d("MealCaptureScreen", "  Base sugar per 100g: ${food.sugar}g (estimated: ${if (food.sugar == 0.0 && food.carbs > 0) "$baseSugar" else "N/A"}), addedSugar: ${food.addedSugar}g")
                android.util.Log.d("MealCaptureScreen", "  Portion: ${portionGrams}g, multiplier: $multiplier")
                android.util.Log.d("MealCaptureScreen", "  Calculated sugar: ${calculatedSugar}g, addedSugar: ${calculatedAddedSugar}g")
                val calculatedMicronutrients = if (food.micronutrients.isNotEmpty()) {
                    android.util.Log.d("MealCaptureScreen", "🔬 Using micronutrients from database for ${food.name}")
                    android.util.Log.d("MealCaptureScreen", "  Portion: ${portionGrams}g, multiplier: $multiplier")
                    val scaled = food.micronutrients.mapValues { (type, value) -> 
                        val scaledValue = value * multiplier
                        android.util.Log.d("MealCaptureScreen", "    ${type.displayName}: ${value}${type.unit.displaySuffix} -> ${scaledValue}${type.unit.displaySuffix}")
                        scaledValue
                    }
                    scaled
                } else {
                    android.util.Log.d("MealCaptureScreen", "🔬 Estimating micronutrients for ${food.name} (not in database)")
                    MicronutrientEstimator.estimate(
                        foodName = food.name,
                        calories = calculatedCalories.toDouble(),
                        protein = calculatedProtein,
                        carbs = calculatedCarbs,
                        fat = calculatedFat
                    )
                }
                
                android.util.Log.d("MealCaptureScreen", "🔬 Final calculated micronutrients: ${calculatedMicronutrients.size} types")
                calculatedMicronutrients.forEach { (type, amount) ->
                    android.util.Log.d("MealCaptureScreen", "  ${type.displayName}: ${amount}${type.unit.displaySuffix}")
                }

                val caloriesString = calculatedCalories.toString()
                val proteinString = String.format("%.1f", calculatedProtein)
                val carbsString = String.format("%.1f", calculatedCarbs)
                val fatString = String.format("%.1f", calculatedFat)
                val sugarString = String.format("%.1f", calculatedSugar)
                val addedSugarString = String.format("%.1f", calculatedAddedSugar)

                if (calories != caloriesString) onCaloriesChange(caloriesString)
                if (protein != proteinString) onProteinChange(proteinString)
                if (carbs != carbsString) onCarbsChange(carbsString)
                if (fat != fatString) onFatChange(fatString)
                if (sugar != sugarString) onSugarChange(sugarString)
                if (addedSugar != addedSugarString) onAddedSugarChange(addedSugarString)

                onMicronutrientsCalculated(calculatedMicronutrients)
            } ?: onMicronutrientsCalculated(emptyMap())
        }

        Column {
            OutlinedTextField(
                value = foodName,
                onValueChange = {
                    onFoodNameChange(it)
                    showSuggestions = it.isNotBlank() && it.length >= 2
                },
                label = { Text("Food Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("e.g., chicken breast, salmon, rice, or brand name (e.g., Oreo cookies)") }
            )

            if (showSuggestions && foodSuggestions.isNotEmpty()) {
                CoachieCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    border = CoachieCardDefaults.border(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        foodSuggestions.forEach { food ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Check if this is a branded food (from API) that needs full nutrition
                                        val isBrandedFood = food.name.contains("(", ignoreCase = true) && 
                                                           (food.calories == 0 || food.protein == 0.0)
                                        
                                        if (isBrandedFood) {
                                            // Fetch full nutrition for branded food
                                            coroutineScope.launch {
                                                try {
                                                    val fullNutrition = EnhancedFoodDatabase.getFullNutritionForBrandedFood(food.name)
                                                    val fullFoodItem = fullNutrition.getOrNull()
                                                    if (fullFoodItem != null) {
                                                        selectedFood = fullFoodItem
                                                        onFoodNameChange(fullFoodItem.name)
                                                        showSuggestions = false
                                                    } else {
                                                        // Fallback to basic food item if API fails
                                                        android.util.Log.w("MealCaptureScreen", "Failed to get full nutrition, using basic food item: ${fullNutrition.exceptionOrNull()?.message}")
                                                        selectedFood = food
                                                        onFoodNameChange(food.name)
                                                        showSuggestions = false
                                                    }
                                                } catch (e: Exception) {
                                                    android.util.Log.e("MealCaptureScreen", "Error fetching full nutrition", e)
                                                    selectedFood = food
                                                    onFoodNameChange(food.name)
                                                    showSuggestions = false
                                                }
                                            }
                                        } else {
                                            selectedFood = food
                                            onFoodNameChange(food.name)
                                            showSuggestions = false
                                        }
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = food.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = if (food.calories > 0 || food.protein > 0) {
                                            "${food.calories} cal/100g • ${food.protein}g protein • ${food.carbs}g carbs • ${food.fat}g fat"
                                        } else {
                                            "Tap to load full nutrition"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Servings adjustment (if recipe is present) - scales entire meal
        var servings by remember(associatedRecipe, initialServings) { 
            mutableStateOf(if (associatedRecipe != null) initialServings else 1.0) 
        }
        
        // Sync with initialServings when it changes (from ViewModel)
        LaunchedEffect(initialServings) {
            if (associatedRecipe != null) {
                servings = initialServings
            }
        }
        
        // Update ViewModel when servings change
        LaunchedEffect(servings) {
            if (associatedRecipe != null) {
                onServingsChange(servings)
            }
        }
        
        // Calculate meal macros/micros from recipe based on servings
        LaunchedEffect(associatedRecipe, servings) {
            associatedRecipe?.let { recipe ->
                val perServing = recipe.getNutritionPerServing()
                val scaleFactor = servings
                
                val calculatedCalories = (perServing.calories * scaleFactor).toInt()
                val calculatedProtein = perServing.proteinG * scaleFactor
                val calculatedCarbs = perServing.carbsG * scaleFactor
                val calculatedFat = perServing.fatG * scaleFactor
                val calculatedSugar = perServing.sugarG * scaleFactor
                val calculatedAddedSugar = perServing.addedSugarG * scaleFactor
                val calculatedMicronutrients = perServing.micronutrients.mapValues { (_, value) -> value * scaleFactor }
                
                val caloriesString = calculatedCalories.toString()
                val proteinString = String.format("%.1f", calculatedProtein)
                val carbsString = String.format("%.1f", calculatedCarbs)
                val fatString = String.format("%.1f", calculatedFat)
                val sugarString = String.format("%.1f", calculatedSugar)
                val addedSugarString = String.format("%.1f", calculatedAddedSugar)
                
                if (calories != caloriesString) onCaloriesChange(caloriesString)
                if (protein != proteinString) onProteinChange(proteinString)
                if (carbs != carbsString) onCarbsChange(carbsString)
                if (fat != fatString) onFatChange(fatString)
                if (sugar != sugarString) onSugarChange(sugarString)
                if (addedSugar != addedSugarString) onAddedSugarChange(addedSugarString)
                
                onMicronutrientsCalculated(calculatedMicronutrients.mapKeys { 
                    com.coachie.app.data.model.MicronutrientType.valueOf(it.key.uppercase()) 
                })
            }
        }
        
        // Show servings adjustment for recipes
        associatedRecipe?.let { recipe ->
            CoachieCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CoachieCardDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
                ),
                border = CoachieCardDefaults.border(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                        val servingsSliderSteps = ((maxServings - minServings) / servingsStep).roundToInt() - 1
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { servings = (servings - servingsStep).coerceAtLeast(minServings) }
                            ) {
                                Text("-")
                            }

                            Slider(
                                value = servings.toFloat(),
                                onValueChange = { servings = it.toDouble() },
                                valueRange = minServings.toFloat()..maxServings.toFloat(),
                                steps = servingsSliderSteps,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = { servings = (servings + servingsStep).coerceAtMost(maxServings) }
                            ) {
                                Text("+")
                            }
                        }

                        Text(
                            text = "${String.format("%.2f", servings)} serving${if (servings != 1.0) "s" else ""} (Recipe serves ${recipe.servings})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // Portion selection (if food is selected AND no recipe) - only show for individual ingredients
        selectedFood?.takeIf { associatedRecipe == null }?.let { food ->
            CoachieCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CoachieCardDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
                ),
                border = CoachieCardDefaults.border(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Adjust Portion Size",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )

                        val sliderEnabled = true // Always enabled for this simplified version
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { portionMultiplier = (portionMultiplier - multiplierStep).coerceAtLeast(minMultiplier) },
                                enabled = sliderEnabled
                            ) {
                                Text("-")
                            }

                            Slider(
                                value = portionMultiplier,
                                onValueChange = { portionMultiplier = it },
                                valueRange = minMultiplier..maxMultiplier,
                                steps = sliderSteps,
                                enabled = sliderEnabled,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = { portionMultiplier = (portionMultiplier + multiplierStep).coerceAtMost(maxMultiplier) },
                                enabled = sliderEnabled
                            ) {
                                Text("+")
                            }
                        }

                        Text(
                            text = "${portionGrams.roundToInt()} g (${String.format("%.2fx", portionMultiplier)} of ${(food.commonPortions.firstOrNull()?.name ?: "100g")})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Calories
        OutlinedTextField(
            value = calories,
            onValueChange = onCaloriesChange,
            label = { Text("Calories") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            placeholder = { Text("e.g., 250") },
            suffix = { Text("cal") }
        )

        // Macros Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Protein
            OutlinedTextField(
                value = protein,
                onValueChange = onProteinChange,
                label = { Text("Protein") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                placeholder = { Text("0") },
                suffix = { Text("g") }
            )

            // Carbs
            OutlinedTextField(
                value = carbs,
                onValueChange = onCarbsChange,
                label = { Text("Carbs") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                placeholder = { Text("0") },
                suffix = { Text("g") }
            )

            // Fat
            OutlinedTextField(
                value = fat,
                onValueChange = onFatChange,
                label = { Text("Fat") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                placeholder = { Text("0") },
                suffix = { Text("g") }
            )
        }

        OutlinedTextField(
            value = sugar,
            onValueChange = onSugarChange,
            label = { Text("Sugar (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            placeholder = { Text("0") },
            suffix = { Text("g") }
        )

        OutlinedTextField(
            value = addedSugar,
            onValueChange = onAddedSugarChange,
            label = { Text("Added Sugar (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            placeholder = { Text("0") },
            suffix = { Text("g") }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Show list of added meal items
        if (mealItems.isNotEmpty()) {
            CoachieCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CoachieCardDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                ),
                border = CoachieCardDefaults.border(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Meal Items (${mealItems.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    mealItems.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.foodName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${item.calories} cal • P:${item.protein}g C:${item.carbs}g F:${item.fat}g S:${item.sugar}g Added:${item.addedSugar}g",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (item.micronutrients.isNotEmpty()) {
                                    Text(
                                        text = item.micronutrients.entries
                                            .sortedBy { it.key.displayName }
                                            .joinToString(", ") { entry ->
                                                val amount = entry.value.formatMicronutrientAmount()
                                                "${entry.key.displayName}: ${amount}${entry.key.unit.displaySuffix}"
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onRemoveFromMeal(index) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    
                    // Show totals
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Total:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${mealItems.sumOf { it.calories }} cal • P:${mealItems.sumOf { it.protein }}g C:${mealItems.sumOf { it.carbs }}g F:${mealItems.sumOf { it.fat }}g S:${mealItems.sumOf { it.sugar }}g Added:${mealItems.sumOf { it.addedSugar }}g",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    val micronutrientTotals = mealItems
                        .flatMap { it.micronutrients.entries }
                        .groupBy({ it.key }) { it.value }
                        .mapValues { (_, values) -> values.sum() }

                    if (micronutrientTotals.isNotEmpty()) {
                        Text(
                            text = micronutrientTotals.entries
                                .sortedBy { it.key.displayName }
                                .joinToString(", ") { entry ->
                                    val amount = entry.value.formatMicronutrientAmount()
                                    "${entry.key.displayName}: ${amount}${entry.key.unit.displaySuffix}"
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Add to Meal / Save Meal buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Always show "Add to Meal" button if there's food data
            OutlinedButton(
                onClick = onAddToMeal,
                modifier = Modifier.weight(1f),
                enabled = foodName.isNotBlank() || calories.isNotBlank(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface // Changed from primary (orange) to onSurface for readability
                )
            ) {
                Text("Add to Meal")
            }
            
            // Show "Save Meal" button - combines all items including current entry
            Button(
                onClick = {
                    if (mealItems.isNotEmpty() || (foodName.isNotBlank() || calories.isNotBlank())) {
                        onSaveCombinedMeal()
                    } else {
                        onSaveSingleMeal()
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = mealItems.isNotEmpty() || foodName.isNotBlank() || calories.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                val totalItems = mealItems.size + if (foodName.isNotBlank() || calories.isNotBlank()) 1 else 0
                Text(if (totalItems > 1) "Submit Meal ($totalItems items)" else "Submit Meal")
            }
        }

        OutlinedButton(
            onClick = { showQuickSaveDialog = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = mealItems.isNotEmpty() || foodName.isNotBlank() || calories.isNotBlank(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface // Changed from primary (orange) to onSurface for readability
            )
        ) {
            Text("Save for Quick Select")
        }

        if (showQuickSaveDialog) {
            SaveMealDialog(
                onDismiss = { showQuickSaveDialog = false },
                onSave = { name ->
                    onSaveForQuickSelect(name)
                    showQuickSaveDialog = false
                }
            )
        }
    }
}

@Composable
private fun PhotoPreviewContent(
    photoUri: Uri,
    onRetake: () -> Unit,
    onAnalyze: (Uri) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Preview",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        CoachieCard(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp),
            shape = RoundedCornerShape(16.dp),
            applyDefaultBorder = false
        ) {
            AsyncImage(
                model = photoUri,
                contentDescription = "Meal photo preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onRetake,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("Retake")
            }

            Button(
                onClick = { onAnalyze(photoUri) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Analyze")
            }
        }
    }
}

@Composable
private fun AnalyzingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator()
        Text(
            text = "Analyzing your meal…",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AnalysisResultContent(
    analysis: MealAnalysis,
    photoUri: Uri?,
    mealItems: List<com.coachie.app.viewmodel.MealItem>,
    onEdit: () -> Unit,
    onFoodNameChange: (String) -> Unit,
    onCaloriesChange: (Int) -> Unit,
    onProteinChange: (Int) -> Unit,
    onCarbsChange: (Int) -> Unit,
    onFatChange: (Int) -> Unit,
    onAddFoodItem: () -> Unit,
    onRemoveFoodItem: (Int) -> Unit,
    onEditFoodItem: (Int, com.coachie.app.viewmodel.MealItem) -> Unit,
    onSplitIntoItems: () -> Unit,
    onAddMore: (MealAnalysis) -> Unit,
    onSave: (HealthLog.MealLog) -> Unit,
    onSaveForQuickSelect: (String) -> Unit,
    onShare: (HealthLog.MealLog) -> Unit
) {
    // Start in editing mode by default so users can easily adjust values
    var isEditing by remember { mutableStateOf(true) }
    var editedFoodName by remember { mutableStateOf(analysis.food) }
    var editedCalories by remember { mutableStateOf(analysis.calories.toString()) }
    var editedProtein by remember { mutableStateOf(analysis.proteinG.toString()) }
    var editedCarbs by remember { mutableStateOf(analysis.carbsG.toString()) }
    var editedFat by remember { mutableStateOf(analysis.fatG.toString()) }
    
    // Track the last analysis we've seen to detect when it changes
    var lastAnalysisFood by remember { mutableStateOf(analysis.food) }
    var lastAnalysisCalories by remember { mutableStateOf(analysis.calories) }
    
    // Update local state when analysis changes (but preserve edits if user is actively editing)
    // IMPORTANT: Always update when analysis changes, even if editing, to handle menu item nutrition search
    LaunchedEffect(analysis.food, analysis.calories, analysis.proteinG, analysis.carbsG, analysis.fatG) {
        // Check if analysis actually changed (not just a recomposition)
        val analysisChanged = analysis.food != lastAnalysisFood || analysis.calories != lastAnalysisCalories
        
        if (analysisChanged) {
            android.util.Log.d("MealCaptureScreen", "Analysis changed - updating local state. New: ${analysis.food}, ${analysis.calories} cal. Old: $lastAnalysisFood, $lastAnalysisCalories cal")
            
            // Always update local state when analysis changes (e.g., when nutrition search completes)
            // This ensures menu item nutrition search results are properly reflected
            editedFoodName = analysis.food
            editedCalories = analysis.calories.toString()
            editedProtein = analysis.proteinG.toString()
            editedCarbs = analysis.carbsG.toString()
            editedFat = analysis.fatG.toString()
            
            lastAnalysisFood = analysis.food
            lastAnalysisCalories = analysis.calories
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Analysis Result",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (isEditing) {
                Text(
                    text = "✏️ Editing",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        if (isEditing) {
            Text(
                text = "Review and adjust the values below before submitting your meal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        photoUri?.let {
            CoachieCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp),
                shape = RoundedCornerShape(16.dp),
                applyDefaultBorder = false
            ) {
                AsyncImage(
                    model = it,
                    contentDescription = "Analyzed meal photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        CoachieCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CoachieCardDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = CoachieCardDefaults.border(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Food name - editable
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isEditing) {
                        OutlinedTextField(
                            value = editedFoodName,
                            onValueChange = { editedFoodName = it },
                            label = { Text("Food Name") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                } else {
                    Text(
                        text = editedFoodName.ifBlank { analysis.food },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                }
                    IconButton(onClick = { 
                        if (isEditing) {
                            // Save changes to ViewModel
                            if (editedFoodName != analysis.food) {
                                onFoodNameChange(editedFoodName)
                            }
                            editedCalories.toIntOrNull()?.let { onCaloriesChange(it) }
                            editedProtein.toIntOrNull()?.let { onProteinChange(it) }
                            editedCarbs.toIntOrNull()?.let { onCarbsChange(it) }
                            editedFat.toIntOrNull()?.let { onFatChange(it) }
                        }
                        isEditing = !isEditing 
                    }) {
                        Icon(
                            imageVector = if (isEditing) Icons.Filled.Check else Icons.Filled.Edit,
                            contentDescription = if (isEditing) "Done Editing" else "Edit"
                        )
                    }
                }
                
                // Calories - editable
                if (isEditing) {
                    OutlinedTextField(
                        value = editedCalories,
                        onValueChange = { 
                            if (it.all { char -> char.isDigit() }) {
                                editedCalories = it
                            }
                        },
                        label = { Text("Calories") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                } else {
                    Text(
                        text = "${editedCalories.toIntOrNull() ?: analysis.calories} calories",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                // Macros - editable
                if (isEditing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editedProtein,
                            onValueChange = { 
                                if (it.all { char -> char.isDigit() }) {
                                    editedProtein = it
                                }
                            },
                            label = { Text("Protein (g)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = editedCarbs,
                            onValueChange = { 
                                if (it.all { char -> char.isDigit() }) {
                                    editedCarbs = it
                                }
                            },
                            label = { Text("Carbs (g)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = editedFat,
                            onValueChange = { 
                                if (it.all { char -> char.isDigit() }) {
                                    editedFat = it
                                }
                            },
                            label = { Text("Fat (g)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                } else {
                    val displayProtein = editedProtein.toIntOrNull() ?: analysis.proteinG
                    val displayCarbs = editedCarbs.toIntOrNull() ?: analysis.carbsG
                    val displayFat = editedFat.toIntOrNull() ?: analysis.fatG
                    Text(
                        text = "Protein: ${displayProtein}g • Carbs: ${displayCarbs}g • Fat: ${displayFat}g",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                LinearProgressIndicator(
                    progress = analysis.confidence.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Confidence ${(analysis.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Show "Split into Items" button if main analysis has food
        if (analysis.food.isNotBlank() && analysis.calories > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSplitIntoItems,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountTree,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Split into Individual Items")
            }
        }

        // Display food items (now includes main analysis if split)
        if (mealItems.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Food Items (${mealItems.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            mealItems.forEachIndexed { index, item ->
                EditableMealItemCard(
                    item = item,
                    index = index,
                    onEdit = { editedItem -> onEditFoodItem(index, editedItem) },
                    onRemove = { onRemoveFoodItem(index) }
                )
            }
        }

        var showSaveDialog by remember { mutableStateOf(false) }

        if (showSaveDialog) {
            SaveMealDialog(
                onDismiss = { showSaveDialog = false },
                onSave = { name ->
                    onSaveForQuickSelect(name)
                    showSaveDialog = false
                }
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onAddMore(analysis) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Another Photo & Continue")
            }
            
            OutlinedButton(
                onClick = onAddFoodItem,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Food Item Manually")
            }

            OutlinedButton(
                onClick = { isEditing = !isEditing },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(if (isEditing) "Done Editing" else "Edit Details")
            }

            OutlinedButton(
                onClick = { 
                    // Save any pending edits before showing save dialog
                    if (isEditing) {
                        if (editedFoodName != analysis.food) {
                            onFoodNameChange(editedFoodName)
                        }
                        editedCalories.toIntOrNull()?.let { onCaloriesChange(it) }
                        editedProtein.toIntOrNull()?.let { onProteinChange(it) }
                        editedCarbs.toIntOrNull()?.let { onCarbsChange(it) }
                        editedFat.toIntOrNull()?.let { onFatChange(it) }
                        isEditing = false
                    }
                    showSaveDialog = true 
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("💾 Save Meal to Quick Select")
            }

            OutlinedButton(
                onClick = {
                    // Save any pending edits first
                    if (isEditing) {
                        if (editedFoodName != analysis.food) {
                            onFoodNameChange(editedFoodName)
                        }
                        editedCalories.toIntOrNull()?.let { onCaloriesChange(it) }
                        editedProtein.toIntOrNull()?.let { onProteinChange(it) }
                        editedCarbs.toIntOrNull()?.let { onCarbsChange(it) }
                        editedFat.toIntOrNull()?.let { onFatChange(it) }
                    }
                    
                    // Use edited values (or current analysis if not editing)
                    val finalFoodName = if (isEditing) editedFoodName else analysis.food
                    val finalCalories = if (isEditing) editedCalories.toIntOrNull() ?: analysis.calories else analysis.calories
                    val finalProtein = if (isEditing) editedProtein.toIntOrNull() ?: analysis.proteinG else analysis.proteinG
                    val finalCarbs = if (isEditing) editedCarbs.toIntOrNull() ?: analysis.carbsG else analysis.carbsG
                    val finalFat = if (isEditing) editedFat.toIntOrNull() ?: analysis.fatG else analysis.fatG
                    
                    // Build meal log from edited analysis + meal items
                    // DEBUG: Log to help identify doubling issue
                    android.util.Log.d("MealCaptureScreen", "Building meal log - Analysis: ${finalFoodName}, ${finalCalories} cal. MealItems count: ${mealItems.size}")
                    mealItems.forEachIndexed { index, item ->
                        android.util.Log.d("MealCaptureScreen", "  MealItem[$index]: ${item.foodName}, ${item.calories} cal")
                    }
                    
                    val analysisItem = MealItem(
                        foodName = finalFoodName,
                        calories = finalCalories,
                        protein = finalProtein,
                        carbs = finalCarbs,
                        fat = finalFat,
                        sugar = 0,
                        addedSugar = 0,
                        micronutrients = emptyMap()
                    )
                    val allItems = listOf(analysisItem) + mealItems
                    val totalCalories = allItems.sumOf { it.calories }
                    val totalProtein = allItems.sumOf { it.protein }
                    val totalCarbs = allItems.sumOf { it.carbs }
                    val totalFat = allItems.sumOf { it.fat }
                    
                    android.util.Log.d("MealCaptureScreen", "Total calculated: ${totalCalories} cal, ${totalProtein}g protein, ${totalCarbs}g carbs, ${totalFat}g fat")
                    
                    val combinedFoodName = if (mealItems.isEmpty()) {
                        finalFoodName
                    } else {
                        "$finalFoodName + ${mealItems.size} more"
                    }
                    
                    val mealLog = HealthLog.MealLog(
                        foodName = combinedFoodName,
                        calories = totalCalories,
                        protein = totalProtein,
                        carbs = totalCarbs,
                        fat = totalFat,
                        sugar = 0,
                        addedSugar = 0,
                        micronutrients = emptyMap()
                    )
                    onSave(mealLog)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("Submit Meal")
            }

            OutlinedButton(
                onClick = {
                    // Save any pending edits first
                    if (isEditing) {
                        if (editedFoodName != analysis.food) {
                            onFoodNameChange(editedFoodName)
                        }
                        editedCalories.toIntOrNull()?.let { onCaloriesChange(it) }
                        editedProtein.toIntOrNull()?.let { onProteinChange(it) }
                        editedCarbs.toIntOrNull()?.let { onCarbsChange(it) }
                        editedFat.toIntOrNull()?.let { onFatChange(it) }
                    }
                    
                    // Build meal log for sharing (same logic as save)
                    val finalFoodName = if (isEditing) editedFoodName else analysis.food
                    val finalCalories = if (isEditing) editedCalories.toIntOrNull() ?: analysis.calories else analysis.calories
                    val finalProtein = if (isEditing) editedProtein.toIntOrNull() ?: analysis.proteinG else analysis.proteinG
                    val finalCarbs = if (isEditing) editedCarbs.toIntOrNull() ?: analysis.carbsG else analysis.carbsG
                    val finalFat = if (isEditing) editedFat.toIntOrNull() ?: analysis.fatG else analysis.fatG
                    
                    val analysisItem = MealItem(
                        foodName = finalFoodName,
                        calories = finalCalories,
                        protein = finalProtein,
                        carbs = finalCarbs,
                        fat = finalFat,
                        sugar = 0,
                        addedSugar = 0,
                        micronutrients = emptyMap()
                    )
                    val allItems = listOf(analysisItem) + mealItems
                    val totalCalories = allItems.sumOf { it.calories }
                    val totalProtein = allItems.sumOf { it.protein }
                    val totalCarbs = allItems.sumOf { it.carbs }
                    val totalFat = allItems.sumOf { it.fat }
                    
                    val combinedFoodName = if (mealItems.isEmpty()) {
                        finalFoodName
                    } else {
                        "$finalFoodName + ${mealItems.size} more"
                    }
                    
                    val mealLog = HealthLog.MealLog(
                        foodName = combinedFoodName,
                        calories = totalCalories,
                        protein = totalProtein,
                        carbs = totalCarbs,
                        fat = totalFat,
                        sugar = 0,
                        addedSugar = 0,
                        micronutrients = emptyMap()
                    )
                    onShare(mealLog)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Share Meal")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveMealDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var mealName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Save Meal for Quick Select")
        },
        text = {
            Column {
                Text(
                    "Give this meal a name so you can easily find it later:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = mealName,
                    onValueChange = { mealName = it },
                    label = { Text("Meal Name") },
                    placeholder = { Text("e.g., Chicken Stir Fry, Protein Shake") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (mealName.isNotBlank()) {
                        onSave(mealName.trim())
                    }
                },
                enabled = mealName.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SavingContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator()
        Text(
            text = "Saving meal...",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun SuccessContent(
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Meal Saved!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Button(onClick = onDone) {
            Text("Done")
        }
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Error: $error",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("Back")
            }

            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f)
            ) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun EditableMealItemCard(
    item: com.coachie.app.viewmodel.MealItem,
    index: Int,
    onEdit: (com.coachie.app.viewmodel.MealItem) -> Unit,
    onRemove: () -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.foodName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (item.quantity.isNotBlank() || item.type.isNotBlank()) {
                    Text(
                        text = buildString {
                            if (item.quantity.isNotBlank()) append(item.quantity)
                            if (item.quantity.isNotBlank() && item.type.isNotBlank()) append(" • ")
                            if (item.type.isNotBlank()) append(item.type)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "${item.calories} cal • P: ${item.protein}g C: ${item.carbs}g F: ${item.fat}g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                IconButton(onClick = { showEditDialog = true }) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
    
    if (showEditDialog) {
        EditMealItemDialog(
            item = item,
            onDismiss = { showEditDialog = false },
            onSave = { editedItem ->
                onEdit(editedItem)
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun EditMealItemDialog(
    item: com.coachie.app.viewmodel.MealItem,
    onDismiss: () -> Unit,
    onSave: (com.coachie.app.viewmodel.MealItem) -> Unit
) {
    var foodName by remember { mutableStateOf(item.foodName) }
    var quantity by remember { mutableStateOf(item.quantity) }
    var type by remember { mutableStateOf(item.type) }
    var calories by remember { mutableStateOf(item.calories.toString()) }
    var protein by remember { mutableStateOf(item.protein.toString()) }
    var carbs by remember { mutableStateOf(item.carbs.toString()) }
    var fat by remember { mutableStateOf(item.fat.toString()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Food Item") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = foodName,
                    onValueChange = { foodName = it },
                    label = { Text("Food Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Quantity") },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("e.g., 2 slices, 3 eggs") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = type,
                        onValueChange = { type = it },
                        label = { Text("Type") },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("e.g., whole wheat, cheddar") },
                        singleLine = true
                    )
                }
                
                OutlinedTextField(
                    value = calories,
                    onValueChange = { if (it.all { char -> char.isDigit() }) calories = it },
                    label = { Text("Calories") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = protein,
                        onValueChange = { if (it.all { char -> char.isDigit() }) protein = it },
                        label = { Text("Protein (g)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = carbs,
                        onValueChange = { if (it.all { char -> char.isDigit() }) carbs = it },
                        label = { Text("Carbs (g)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = fat,
                        onValueChange = { if (it.all { char -> char.isDigit() }) fat = it },
                        label = { Text("Fat (g)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        item.copy(
                            foodName = foodName,
                            quantity = quantity,
                            type = type,
                            calories = calories.toIntOrNull() ?: item.calories,
                            protein = protein.toIntOrNull() ?: item.protein,
                            carbs = carbs.toIntOrNull() ?: item.carbs,
                            fat = fat.toIntOrNull() ?: item.fat
                        )
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Estimate sugar content from carbs when sugar data is not available.
 * Uses heuristics based on food name and carb content.
 */
private fun estimateSugarFromCarbsForFood(carbs: Double, foodName: String): Double {
    if (carbs <= 0.0) return 0.0
    
    val nameLower = foodName.lowercase()
    
    // High sugar foods (most carbs are sugar)
    val highSugarKeywords = listOf(
        "candy", "chocolate", "cookie", "cake", "donut", "muffin", "pastry",
        "soda", "juice", "drink", "beverage", "sweet", "syrup", "honey",
        "jam", "jelly", "preserve", "marmalade", "fruit", "berries", "blackberr"
    )
    if (highSugarKeywords.any { nameLower.contains(it) }) {
        return carbs * 0.85 // ~85% of carbs are sugar
    }
    
    // Medium sugar foods (processed foods, breads, cereals)
    val mediumSugarKeywords = listOf(
        "bread", "cereal", "crackers", "granola", "bar", "snack",
        "yogurt", "milk", "cream", "ice cream", "frozen"
    )
    if (mediumSugarKeywords.any { nameLower.contains(it) }) {
        return carbs * 0.40 // ~40% of carbs are sugar
    }
    
    // Low sugar foods (whole grains, vegetables, proteins)
    val lowSugarKeywords = listOf(
        "rice", "pasta", "quinoa", "oats", "potato", "sweet potato",
        "chicken", "beef", "pork", "fish", "salmon", "turkey",
        "broccoli", "spinach", "lettuce", "cucumber", "tomato", "pepper"
    )
    if (lowSugarKeywords.any { nameLower.contains(it) }) {
        return carbs * 0.10 // ~10% of carbs are sugar (natural sugars)
    }
    
    // Default: assume 30% of carbs are sugar (moderate estimate)
    return carbs * 0.30
}
