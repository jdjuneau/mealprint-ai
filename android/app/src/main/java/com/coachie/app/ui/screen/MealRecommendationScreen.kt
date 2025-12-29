package com.coachie.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.coachie.app.ui.components.CoachieCard as Card
import com.coachie.app.ui.components.CoachieCardDefaults as CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.background
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import com.coachie.app.ui.theme.rememberCoachieGradient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.local.PreferencesManager
import com.coachie.app.data.model.IngredientCategoryGroup
import com.coachie.app.data.model.IngredientOption
import com.coachie.app.data.model.MealRecommendationRequest
import com.coachie.app.data.model.MacroSnapshot
import com.coachie.app.data.model.MealRecommendation
import com.coachie.app.data.model.CookingMethod
import com.coachie.app.data.model.MealType
import com.coachie.app.viewmodel.MealRecommendationUiState
import com.coachie.app.viewmodel.MealRecommendationViewModel
import com.coachie.app.data.SubscriptionService
import com.coachie.app.data.model.SubscriptionTier
import com.coachie.app.data.model.AIFeature
import com.coachie.app.ui.components.UpgradePromptDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MealRecommendationScreen(
    userId: String?,
    onBack: () -> Unit,
    onNavigateToSubscription: () -> Unit = {}
) {
    val mealContext = LocalContext.current
    val resolvedUserId = remember(userId, mealContext) {
        userId?.takeIf { it.isNotBlank() }
            ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    }

    if (resolvedUserId.isBlank()) {
        MissingUserIdContent(onBack = onBack)
        return
    }

    val viewModel: MealRecommendationViewModel = viewModel(
        factory = MealRecommendationViewModel.Factory(
            repository = FirebaseRepository.getInstance(),
            userId = resolvedUserId,
            context = mealContext
        )
    )

    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    // Subscription state
    var showUpgradeDialog by remember { mutableStateOf(false) }
    var remainingCalls by remember { mutableStateOf<Int?>(null) }
    var currentTier by remember { mutableStateOf<SubscriptionTier?>(null) }
    
    // Load subscription status
    LaunchedEffect(resolvedUserId) {
        coroutineScope.launch {
            currentTier = SubscriptionService.getUserTier(resolvedUserId)
            remainingCalls = SubscriptionService.getRemainingAICalls(resolvedUserId, AIFeature.MEAL_RECOMMENDATION)
        }
    }
    
    // Refresh remaining calls after successful recommendation
    LaunchedEffect(uiState.recommendation) {
        if (uiState.recommendation != null && currentTier == SubscriptionTier.FREE) {
            coroutineScope.launch {
                // Wait a bit for Firestore write to propagate
                kotlinx.coroutines.delay(500)
                remainingCalls = SubscriptionService.getRemainingAICalls(resolvedUserId, AIFeature.MEAL_RECOMMENDATION)
            }
        }
    }

    // CRITICAL: Refresh remaining calls when error occurs (might be stale count)
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            if (message.contains("No meal recommendations remaining", ignoreCase = true) && currentTier == SubscriptionTier.FREE) {
                coroutineScope.launch {
                    // Refresh count to show accurate remaining
                    remainingCalls = SubscriptionService.getRemainingAICalls(resolvedUserId, AIFeature.MEAL_RECOMMENDATION)
                }
            }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            coroutineScope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    // Show success message in snackbar - DO NOT navigate away
    LaunchedEffect(uiState.logSuccessMessage) {
        uiState.logSuccessMessage?.let { message ->
            coroutineScope.launch { 
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    val gradient = rememberCoachieGradient()
    
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("AI Meal Inspiration") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshContext() },
                        enabled = !uiState.isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh context"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            if (uiState.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Loading your nutrition data…")
                }
            }

            MacroSummaryCard(uiState)
            
            MealTypeSection(
                selectedMealType = uiState.selectedMealType,
                onMealTypeSelected = { viewModel.updateSelectedMealType(it) }
            )
            
            IngredientSection(
                uiState = uiState,
                onToggleIngredient = { viewModel.toggleIngredient(it) },
                onRemoveIngredient = { viewModel.removeIngredient(it) },
                onAddCustomIngredient = { viewModel.addCustomIngredient(it) }
            )
            
            CookingMethodSection(
                selectedCookingMethod = uiState.selectedCookingMethod,
                onCookingMethodSelected = { viewModel.updateCookingMethod(it) }
            )

            Button(
                onClick = { viewModel.prepareRecommendationPreview() },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.selectedIngredients.isNotEmpty() && !uiState.isLoading,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("Generate Recommendation Preview")
            }

            uiState.requestPreview?.let { request ->
                RecommendationPreviewCard(request = request)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        // CRITICAL FIX: Refresh count before calling to ensure UI shows accurate value
                        // Then let ViewModel handle the atomic check right before API call
                        coroutineScope.launch {
                            if (currentTier == SubscriptionTier.FREE) {
                                remainingCalls = SubscriptionService.getRemainingAICalls(resolvedUserId, AIFeature.MEAL_RECOMMENDATION)
                            }
                            // ViewModel will do atomic check right before API call
                                viewModel.requestRecommendation()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isRequestingRecommendation,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    if (uiState.isRequestingRecommendation) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        if (uiState.isRequestingRecommendation)
                            "Asking Coachie…"
                        else
                            "Ask Coachie for a Recipe"
                    )
                }
            }

            // Show remaining calls for free tier
            if (currentTier == SubscriptionTier.FREE && remainingCalls != null) {
                Text(
                    text = if (remainingCalls!! > 0) {
                        "$remainingCalls meal recommendations remaining today"
                    } else {
                        "No meal recommendations remaining today"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (remainingCalls!! > 0) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            // Upgrade dialog
            if (showUpgradeDialog) {
                UpgradePromptDialog(
                    onDismiss = { showUpgradeDialog = false },
                    onUpgrade = {
                        showUpgradeDialog = false
                        onNavigateToSubscription()
                    },
                    featureName = "AI Meal Recommendations",
                    remainingCalls = remainingCalls
                )
            }
            
            uiState.recommendation?.let { recommendation ->
                Spacer(modifier = Modifier.height(16.dp))
                RecommendationResultCard(
                    recommendation = recommendation,
                    remaining = uiState.remainingMacros,
                    isLogging = uiState.isLogging,
                    isSavingQuickSelect = uiState.isSavingQuickSelect,
                    selectedServings = uiState.selectedServings,
                    onLogMeal = { viewModel.logRecommendationToMeal(recommendation) },
                    onSaveQuickSelect = { viewModel.saveRecommendationToQuickSelect(recommendation) },
                    onUpdateServings = { viewModel.updateServings(it) }
                )
                // Success message is now shown in snackbar, recipe stays visible
            }

            Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun MacroSummaryCard(uiState: MealRecommendationUiState) {
    val targets = uiState.macroTargets ?: return
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Today's Nutrition Snapshot",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = targets.recommendation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            MacroRow(
                label = "Calories",
                consumed = uiState.currentMacros.calories,
                target = targets.calorieGoal,
                unit = "kcal"
            )
            MacroRow(
                label = "Protein",
                consumed = uiState.currentMacros.protein,
                target = targets.proteinGrams,
                unit = "g"
            )
            MacroRow(
                label = "Carbs",
                consumed = uiState.currentMacros.carbs,
                target = targets.carbsGrams,
                unit = "g"
            )
            MacroRow(
                label = "Fat",
                consumed = uiState.currentMacros.fat,
                target = targets.fatGrams,
                unit = "g"
            )
        }
    }
}

@Composable
private fun MacroRow(label: String, consumed: Int, target: Int, unit: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(
            text = "$consumed / $target $unit",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun IngredientSection(
    uiState: MealRecommendationUiState,
    onToggleIngredient: (IngredientOption) -> Unit,
    onRemoveIngredient: (String) -> Unit,
    onAddCustomIngredient: (String) -> Unit
) {
    // Track which categories are expanded (initially all collapsed)
    var expandedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "What ingredients do you have right now?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Select everything you can use. Add specifics (like \"roasted veggies\" or \"leftover steak\") in the custom box.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        uiState.ingredientGroups.forEach { group ->
            IngredientGroupCard(
                group = group,
                selected = uiState.selectedIngredients,
                onToggle = onToggleIngredient,
                isExpanded = expandedCategories.contains(group.title),
                onExpandToggle = { 
                    expandedCategories = if (expandedCategories.contains(group.title)) {
                        expandedCategories - group.title
                    } else {
                        expandedCategories + group.title
                    }
                }
            )
        }

        CustomIngredientInput(onAddIngredient = onAddCustomIngredient)

        if (uiState.selectedIngredients.isNotEmpty()) {
            SelectedIngredientsRow(
                ingredients = uiState.selectedIngredients,
                onRemoveIngredient = onRemoveIngredient
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IngredientGroupCard(
    group: IngredientCategoryGroup,
    selected: List<String>,
    onToggle: (IngredientOption) -> Unit,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header with expand/collapse button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Expandable ingredient options
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    group.options.forEach { option ->
                        val isSelected = selected.contains(option.name)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onToggle(option) },
                            label = { Text(option.name) },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurface // Dark color instead of white for better readability
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealTypeSection(
    selectedMealType: String?,
    onMealTypeSelected: (String?) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.colors()
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Meal Type",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Select the type of meal you want to prepare",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Show selected meal type if any
            selectedMealType?.let { mealTypeId ->
                MealType.fromId(mealTypeId)?.let { mealType ->
                    AssistChip(
                        onClick = { onMealTypeSelected(null) },
                        label = { Text("${mealType.displayName} (tap to remove)") },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Remove",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
            
            // Expandable meal type options
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    MealType.values().forEach { mealType ->
                        val isSelected = selectedMealType == mealType.id
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                onMealTypeSelected(if (isSelected) null else mealType.id)
                            },
                            label = { Text(mealType.displayName) },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CookingMethodSection(
    selectedCookingMethod: String?,
    onCookingMethodSelected: (String?) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.colors()
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cooking Method (Optional)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Select a cooking method for this meal, or leave empty for AI to choose",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Show selected method if any
            selectedCookingMethod?.let { methodId ->
                CookingMethod.fromId(methodId)?.let { method ->
                    AssistChip(
                        onClick = { onCookingMethodSelected(null) },
                        label = { Text("${method.displayName} (tap to remove)") },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Remove",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
            
            // Expandable cooking method options
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    CookingMethod.values().forEach { method ->
                        val isSelected = selectedCookingMethod == method.id
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                onCookingMethodSelected(if (isSelected) null else method.id)
                            },
                            label = { Text(method.displayName) },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomIngredientInput(onAddIngredient: (String) -> Unit) {
    var customIngredient by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Custom Ingredient",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = customIngredient,
                onValueChange = { customIngredient = it },
                placeholder = { Text("e.g. leftover steak, feta cheese") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Button(
                onClick = {
                    onAddIngredient(customIngredient)
                    customIngredient = ""
                },
                enabled = customIngredient.isNotBlank(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text("Add")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedIngredientsRow(
    ingredients: List<String>,
    onRemoveIngredient: (String) -> Unit
) {
    Column {
        Text(
            text = "Selected Ingredients",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            ingredients.forEach { ingredient ->
                AssistChip(
                    onClick = { onRemoveIngredient(ingredient) },
                    label = { Text(ingredient) },
                    colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        leadingIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    }
}

@Composable
private fun RecommendationPreviewCard(request: MealRecommendationRequest) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Recommendation Preview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Diet: ${request.dietaryPreference}, Goal: ${request.goalTrend}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Ingredients: ${request.selectedIngredients.joinToString()}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Remaining targets • Protein ${request.remainingMacros.protein}g • Carbs ${request.remainingMacros.carbs}g • Fat ${request.remainingMacros.fat}g",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "The AI will use this context to build a recipe that fits your macros and available ingredients. In the next step, we’ll call the model and display the full recipe.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
private fun RecommendationResultCard(
    recommendation: MealRecommendation,
    remaining: MacroSnapshot,
    isLogging: Boolean,
    isSavingQuickSelect: Boolean,
    selectedServings: Int,
    onLogMeal: () -> Unit,
    onSaveQuickSelect: () -> Unit,
    onUpdateServings: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = recommendation.recipeTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = recommendation.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Serving size selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Serves ${recommendation.servings} • Prep ${recommendation.prepTimeMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Adjust for:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedButton(
                        onClick = { onUpdateServings((selectedServings - 1).coerceAtLeast(1)) },
                        modifier = Modifier.size(36.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("-", fontSize = 18.sp)
                    }
                    Text(
                        text = "$selectedServings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(48.dp),
                        textAlign = TextAlign.Center
                    )
                    OutlinedButton(
                        onClick = { onUpdateServings((selectedServings + 1).coerceAtMost(20)) },
                        modifier = Modifier.size(36.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("+", fontSize = 18.sp)
                    }
                    Text(
                        text = "people",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val scaleFactor = selectedServings.toDouble() / recommendation.servings
            MacroSnapshotSection(
                macros = MacroSnapshot(
                    calories = (recommendation.macrosPerServing.calories * scaleFactor).toInt(),
                    protein = (recommendation.macrosPerServing.protein * scaleFactor).toInt(),
                    carbs = (recommendation.macrosPerServing.carbs * scaleFactor).toInt(),
                    fat = (recommendation.macrosPerServing.fat * scaleFactor).toInt(),
                    sugar = (recommendation.macrosPerServing.sugar * scaleFactor).toInt(),
                    addedSugar = (recommendation.macrosPerServing.addedSugar * scaleFactor).toInt()
                ),
                remaining = remaining,
                isScaled = selectedServings != recommendation.servings
            )

            Text(
                text = "Ingredients",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                recommendation.ingredients.forEach { item ->
                    val scaledItem = if (selectedServings != recommendation.servings) {
                        scaleIngredientQuantity(item, scaleFactor)
                    } else {
                        item
                    }
                    Text(
                        "• $scaledItem",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                text = "Instructions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recommendation.instructions.forEachIndexed { index, step ->
                    Text(
                        "${index + 1}. $step",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                text = "Why it fits",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = recommendation.fitExplanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (recommendation.groceryList.isNotEmpty()) {
                Text(
                    text = "Grocery Add-ons",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    recommendation.groceryList.forEach { item ->
                        Text(
                            "• $item",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Divider()
            Button(
                onClick = onLogMeal,
                enabled = !isLogging,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                if (isLogging) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Logging Meal…")
                } else {
                    Text("Log This Meal")
                }
            }

            OutlinedButton(
                onClick = onSaveQuickSelect,
                enabled = !isSavingQuickSelect,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                if (isSavingQuickSelect) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Saving…")
                } else {
                    Text("Save to Quick Select")
                }
            }
        }
    }
}

@Composable
private fun MacroSnapshotSection(macros: MacroSnapshot, remaining: MacroSnapshot, isScaled: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = if (isScaled) "Macros (scaled)" else "Macros per serving",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Calories: ${macros.calories} kcal",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Protein: ${macros.protein}g (remaining: ${remaining.protein}g)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Carbs: ${macros.carbs}g (remaining: ${remaining.carbs}g)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Fat: ${macros.fat}g (remaining: ${remaining.fat}g)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// scaleIngredientQuantity is defined in WeeklyBlueprintScreen.kt and available here

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MissingUserIdContent(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Meal Inspiration") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Sign in required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "We need your profile to tailor recipes. Please sign in again and try this feature.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


