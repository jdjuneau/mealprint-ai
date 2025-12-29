package com.coachie.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.theme.LocalGender
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.functions.ktx.functions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.coachie.app.data.model.Recipe
import com.coachie.app.data.model.RecipeIngredient
import com.coachie.app.data.model.HealthLog
import android.content.Intent
import androidx.core.app.ShareCompat
import androidx.compose.runtime.DisposableEffect
import android.graphics.pdf.PdfDocument
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest
import android.os.Build
import java.util.regex.Pattern
import kotlin.math.roundToInt
import com.coachie.app.data.SubscriptionService
import com.coachie.app.data.model.SubscriptionTier
import com.coachie.app.data.model.AIFeature
import com.coachie.app.ui.components.UpgradePromptDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

data class ShoppingListItem(
    val item: String,
    val quantity: String,
    val bought: Boolean = false,
    val note: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyBlueprintScreen(
    onNavigateBack: () -> Unit,
    userId: String,
    onNavigateToSubscription: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var weeklyBlueprint by remember { mutableStateOf<Map<String, Any>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var regenerating by remember { mutableStateOf(false) }
    var expandedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    var editingItem by remember { mutableStateOf<Triple<String, Int, String>?>(null) } // category, index, field
    var editValue by remember { mutableStateOf("") }
    var showRegenerateDialog by remember { mutableStateOf(false) }
    var userMealsPerDay by remember { mutableStateOf<Int?>(null) }
    var lastMealsPerDay by remember { mutableStateOf<Int?>(null) }
    var expandedDays by remember { mutableStateOf<Set<String>>(emptySet()) }
    var useImperial by remember { mutableStateOf<Boolean?>(null) }
    var selectedServings by remember { mutableStateOf(4) } // Default to 4 servings (all recipes are for 4 people)
    var originalServings by remember { mutableStateOf(4) } // All recipes are generated for 4 servings
    
    // Recipe save/share state
    var showShareDialog by remember { mutableStateOf(false) }
    var recipeToShare by remember { mutableStateOf<Recipe?>(null) }
    var friends by remember { mutableStateOf<List<com.coachie.app.data.model.PublicUserProfile>>(emptyList()) }
    var selectedFriends by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    // Subscription state
    var currentTier by remember { mutableStateOf<SubscriptionTier?>(null) }
    var showUpgradeDialog by remember { mutableStateOf(false) }
    
    val db = Firebase.firestore
    val functions = Firebase.functions
    val repository = com.coachie.app.data.FirebaseRepository.getInstance()
    
    // Check subscription tier
    LaunchedEffect(userId) {
        currentTier = SubscriptionService.getUserTier(userId)
    }
    
    /**
     * Convert weekly blueprint meal data to Recipe
     */
    fun mealDataToRecipe(meal: Map<*, *>, userId: String, servings: Int = 1, originalServings: Int = 1): Recipe {
        val name = meal["name"] as? String ?: "Meal"
        val ingredientsList = meal["ingredients"] as? List<*> ?: emptyList<Any>()
        val steps = meal["steps"] as? List<*> ?: emptyList<Any>()
        val calories = (meal["calories"] as? Number)?.toInt() ?: 0
        val protein = (meal["protein"] as? Number)?.toInt() ?: 0
        val carbs = (meal["carbs"] as? Number)?.toInt() ?: 0
        val fat = (meal["fat"] as? Number)?.toInt() ?: 0
        
        // Scale factor for ingredients (ingredients in blueprint are for originalServings)
        val ingredientScaleFactor = if (originalServings > 0) {
            servings.toDouble() / originalServings
        } else {
            1.0
        }
        
        // Parse ingredients from string list and scale them
        val recipeIngredients = ingredientsList.mapNotNull { ingStr ->
            val ingString = ingStr.toString()
            if (ingString.isBlank()) return@mapNotNull null
            
            // Try to parse "quantity unit name" format
            val parts = ingString.trim().split(" ", limit = 3)
            if (parts.size >= 2) {
                val originalQuantity = parts[0].toDoubleOrNull() ?: 1.0
                val scaledQuantity = originalQuantity * ingredientScaleFactor
                val unit = parts[1]
                val name = if (parts.size > 2) parts[2] else parts[1]
                
                RecipeIngredient(
                    name = name,
                    quantity = scaledQuantity,
                    unit = unit,
                    calories = 0, // Nutrition already in meal totals
                    proteinG = 0,
                    carbsG = 0,
                    fatG = 0,
                    sugarG = 0
                )
            } else {
                RecipeIngredient(
                    name = ingString,
                    quantity = 1.0 * ingredientScaleFactor,
                    unit = "item",
                    calories = 0,
                    proteinG = 0,
                    carbsG = 0,
                    fatG = 0,
                    sugarG = 0
                )
            }
        }
        
        // Parse instructions
        val instructions = steps.mapNotNull { it?.toString() }
        
        // Calculate total nutrition (per-serving * servings)
        val totalCalories = calories * servings
        val totalProtein = protein * servings
        val totalCarbs = carbs * servings
        val totalFat = fat * servings
        
        return Recipe(
            userId = userId,
            name = name,
            description = "From Weekly Blueprint",
            servings = servings,
            ingredients = recipeIngredients,
            instructions = instructions,
            totalCalories = totalCalories,
            totalProteinG = totalProtein,
            totalCarbsG = totalCarbs,
            totalFatG = totalFat,
            totalSugarG = 0,
            totalAddedSugarG = 0
        )
    }
    
    /**
     * Log a meal from weekly blueprint (saves recipe and links it to meal log)
     */
    fun logMealFromBlueprint(meal: Map<*, *>, servingsConsumed: Double = 1.0) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                
                // The meal data is per-serving, ingredients in blueprint are for originalServings
                // Create recipe with selectedServings servings (ingredients will be scaled)
                val recipe = mealDataToRecipe(meal, userId, selectedServings, originalServings)
                
                // Save recipe
                val recipeResult = repository.saveRecipe(userId, recipe)
                val recipeId = if (recipeResult.isSuccess) {
                    recipe.id
                } else {
                    android.util.Log.w("WeeklyBlueprint", "Failed to save recipe, continuing without recipe link")
                    null
                }
                
                // Create meal log from recipe
                // Log 1 serving = the full recipe (already scaled to selectedServings)
                val mealLog = recipe.toMealLog(1).copy(
                    recipeId = recipeId,
                    servingsConsumed = 1.0
                )
                
                // Save meal log
                val result = repository.saveHealthLog(userId, today, mealLog)
                
                if (result.isSuccess) {
                    android.util.Log.d("WeeklyBlueprint", "✅ Meal logged with recipe: ${recipe.name}")
                } else {
                    android.util.Log.e("WeeklyBlueprint", "Failed to log meal: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                android.util.Log.e("WeeklyBlueprint", "Error logging meal from blueprint", e)
            }
        }
    }
    
    /**
     * Save a recipe from weekly blueprint
     */
    fun saveRecipeFromBlueprint(recipe: Recipe) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val result = repository.saveRecipe(userId, recipe)
                result.onSuccess {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "Recipe saved successfully!",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }.onFailure { error ->
                    android.util.Log.e("WeeklyBlueprint", "Error saving recipe", error)
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "Failed to save recipe: ${error.message}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("WeeklyBlueprint", "Error saving recipe", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "Failed to save recipe",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    /**
     * Share a recipe from weekly blueprint
     */
    fun shareRecipeFromBlueprint(recipe: Recipe) {
        // Check subscription tier before showing share dialog
        coroutineScope.launch(Dispatchers.IO) {
            val tier = com.coachie.app.data.SubscriptionService.getUserTier(userId)
            withContext(Dispatchers.Main) {
                if (tier != com.coachie.app.data.model.SubscriptionTier.PRO) {
                    showUpgradeDialog = true
                } else {
                    recipeToShare = recipe
                    showShareDialog = true
                }
            }
        }
    }
    
    /**
     * Actually share the recipe with selected friends
     */
    fun performShareRecipe() {
        val recipe = recipeToShare ?: return
        if (selectedFriends.isEmpty()) return
        
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
                
                // CRITICAL: Save recipe to user's personal recipes collection FIRST
                android.util.Log.d("WeeklyBlueprint", "💾 Saving recipe to personal collection before sharing...")
                val saveResult = repository.saveRecipe(userId, recipe)
                if (saveResult.isFailure) {
                    android.util.Log.w("WeeklyBlueprint", "Failed to save recipe to personal collection, continuing with share")
                } else {
                    android.util.Log.d("WeeklyBlueprint", "✅ Recipe saved to personal collection")
                }
                
                val viewModel = com.coachie.app.viewmodel.RecipeCaptureViewModel(
                    context = context,
                    firebaseRepository = repository,
                    userId = userId
                )
                viewModel.shareRecipeWithFriends(recipe, selectedFriends.toList())
                
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "Recipe shared with ${selectedFriends.size} friend(s)!",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    showShareDialog = false
                    recipeToShare = null
                    selectedFriends = emptySet()
                }
            } catch (e: Exception) {
                android.util.Log.e("WeeklyBlueprint", "Error sharing recipe", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "Failed to share recipe: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    fun getWeekId(): String {
        // Calculate Monday of the current week (matches function's getWeekStarting logic)
        val date = LocalDate.now()
        val dayOfWeek = date.dayOfWeek.value // Monday=1, Sunday=7
        val daysToSubtract = if (dayOfWeek == 7) 6 else dayOfWeek - 1 // Sunday: subtract 6 to get Monday, others: subtract to get Monday
        return date.minusDays(daysToSubtract.toLong()).toString()
    }
    
    // Function to convert all metric units in the blueprint to imperial
    // ONLY converts if the blueprint was generated in metric but user wants imperial
    fun convertBlueprintToImperial(blueprint: Map<String, Any>?): Map<String, Any>? {
        if (blueprint == null || useImperial != true) return blueprint
        
        // CRITICAL: Check if blueprint was already generated in imperial
        // If useImperial flag is true in the blueprint, it means OpenAI already returned imperial units
        // DO NOT convert again - just return as-is
        val blueprintUseImperial = blueprint["useImperial"] as? Boolean
        if (blueprintUseImperial == true) {
            android.util.Log.d("WeeklyBlueprint", "Blueprint already in imperial - skipping conversion")
            return blueprint
        }
        
        android.util.Log.d("WeeklyBlueprint", "Blueprint in metric - converting to imperial")
        
        val converted = blueprint.toMutableMap()
        
        // Convert shopping list
        val shoppingList = converted["shoppingList"] as? MutableMap<String, Any>
        if (shoppingList != null) {
            shoppingList.forEach { (category, items) ->
                val categoryItems = (items as? MutableList<*>)?.toMutableList()
                if (categoryItems != null) {
                    categoryItems.forEachIndexed { index, item ->
                        val itemMap = (item as? MutableMap<*, *>)?.toMutableMap()
                        if (itemMap != null) {
                            val quantity = itemMap["quantity"] as? String ?: ""
                            if (quantity.isNotBlank()) {
                                itemMap["quantity"] = convertQuantityToImperial(quantity)
                            }
                            // Also convert item name if it contains quantities
                            val itemName = itemMap["item"] as? String ?: ""
                            if (itemName.isNotBlank()) {
                                itemMap["item"] = convertQuantityToImperial(itemName)
                            }
                            categoryItems[index] = itemMap
                        }
                    }
                    shoppingList[category] = categoryItems
                }
            }
            converted["shoppingList"] = shoppingList
        }
        
        // Convert meal ingredients - meals is a list of day objects
        val meals = converted["meals"] as? MutableList<*>
        if (meals != null) {
            val mealsMutable = meals.toMutableList()
            mealsMutable.forEachIndexed { dayIndex, day ->
                val dayMap = (day as? MutableMap<*, *>)?.toMutableMap()
                if (dayMap != null) {
                    // Convert each meal type (breakfast, lunch, dinner, snacks)
                    listOf("breakfast", "lunch", "dinner").forEach { mealType ->
                        val meal = dayMap[mealType] as? MutableMap<*, *>
                        if (meal != null) {
                            val mealMutable = meal.toMutableMap()
                            val ingredients = mealMutable["ingredients"] as? MutableList<*>
                            if (ingredients != null) {
                                val convertedIngredients = ingredients.map { ingredient ->
                                    val ingredientStr = ingredient.toString()
                                    if (ingredientStr.isNotBlank()) {
                                        convertQuantityToImperial(ingredientStr)
                                    } else {
                                        ingredientStr
                                    }
                                }
                                mealMutable["ingredients"] = convertedIngredients
                            }
                            dayMap[mealType] = mealMutable
                        }
                    }
                    // Convert snacks
                    val snacks = dayMap["snacks"] as? MutableList<*>
                    if (snacks != null) {
                        val convertedSnacks = snacks.map { snack ->
                            val snackMap = (snack as? MutableMap<*, *>)?.toMutableMap()
                            if (snackMap != null) {
                                val ingredients = snackMap["ingredients"] as? MutableList<*>
                                if (ingredients != null) {
                                    val convertedIngredients = ingredients.map { ingredient ->
                                        val ingredientStr = ingredient.toString()
                                        if (ingredientStr.isNotBlank()) {
                                            convertQuantityToImperial(ingredientStr)
                                        } else {
                                            ingredientStr
                                        }
                                    }
                                    snackMap["ingredients"] = convertedIngredients
                                }
                                snackMap
                            } else {
                                snack
                            }
                        }
                        dayMap["snacks"] = convertedSnacks
                    }
                    mealsMutable[dayIndex] = dayMap
                }
            }
            converted["meals"] = mealsMutable
        }
        
        return converted
    }
    
    fun loadWeeklyPlan() {
        if (userId.isBlank()) {
            android.util.Log.e("WeeklyBlueprint", "Cannot load blueprint: userId is blank")
            return
        }
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                isLoading = true
                val weekStarting = getWeekId()
                
                // Try weeklyBlueprints first, then weeklyPlans
                val blueprintDoc = db.collection("users")
                    .document(userId)
                    .collection("weeklyBlueprints")
                    .document(weekStarting)
                    .get()
                    .await()
                
                val loadedBlueprint = if (blueprintDoc.exists()) {
                    blueprintDoc.data
                } else {
                    val planDoc = db.collection("users")
                        .document(userId)
                        .collection("weeklyPlans")
                        .document(weekStarting)
                        .get()
                        .await()
                    if (planDoc.exists()) {
                        planDoc.data
                    } else {
                        null
                    }
                }
                
                // Debug logging to see the blueprint structure
                if (loadedBlueprint != null) {
                    android.util.Log.d("WeeklyBlueprint", "Loaded blueprint keys: ${loadedBlueprint.keys.joinToString()}")
                    val meals = loadedBlueprint["meals"]
                    if (meals is List<*>) {
                        android.util.Log.d("WeeklyBlueprint", "Meals array has ${meals.size} days")
                        meals.forEachIndexed { index, dayMeal ->
                            if (dayMeal is Map<*, *>) {
                                android.util.Log.d("WeeklyBlueprint", "Day $index: ${dayMeal["day"]}, keys: ${dayMeal.keys.joinToString()}")
                                android.util.Log.d("WeeklyBlueprint", "  breakfast: ${dayMeal["breakfast"] != null}")
                                android.util.Log.d("WeeklyBlueprint", "  lunch: ${dayMeal["lunch"] != null}")
                                android.util.Log.d("WeeklyBlueprint", "  dinner: ${dayMeal["dinner"] != null}")
                                android.util.Log.d("WeeklyBlueprint", "  snacks: ${dayMeal["snacks"] != null}")
                            }
                        }
                    } else {
                        android.util.Log.w("WeeklyBlueprint", "Meals is not a List: ${meals?.javaClass?.simpleName}")
                    }
                } else {
                    android.util.Log.w("WeeklyBlueprint", "No blueprint found for week $weekStarting")
                }
                
                // Update state on main thread
                withContext(Dispatchers.Main) {
                    // ALWAYS use user's actual preference, not the stored blueprint value
                    // The blueprint might have been generated with wrong units, so we trust the user's preference
                    try {
                        val goalsResult = repository.getUserGoals(userId)
                        val goals = goalsResult.getOrNull()
                        val useImperialValue = goals?.get("useImperial") as? Boolean ?: true
                        useImperial = useImperialValue
                        android.util.Log.d("WeeklyBlueprint", "Using user's actual useImperial preference: $useImperialValue (ignoring blueprint value)")
                    } catch (e: Exception) {
                        android.util.Log.w("WeeklyBlueprint", "Could not load unit preference, defaulting to imperial", e)
                        useImperial = true
                    }
                    
                    // Convert all metric units to imperial if needed BEFORE setting weeklyBlueprint
                    weeklyBlueprint = convertBlueprintToImperial(loadedBlueprint)
                    
                    // All recipes are generated for 4 servings by default
                    val servingsFromBlueprint = 4
                    
                    android.util.Log.d("WeeklyBlueprint", "Recipes generated for: $servingsFromBlueprint servings")
                    
                    // Initialize servings - all recipes are for 4 servings
                    originalServings = servingsFromBlueprint
                    selectedServings = servingsFromBlueprint
                    
                    // Initialize all categories as expanded
                    val shoppingList = loadedBlueprint?.get("shoppingList") as? Map<*, *>
                    if (shoppingList != null) {
                        expandedCategories = shoppingList.keys.map { it.toString() }.toSet()
                    }
                    isLoading = false
                }
            } catch (e: Exception) {
                android.util.Log.e("WeeklyBlueprint", "Error loading blueprint", e)
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }
    
    // Listen for user profile changes (mealsPerDay) and load unit preference
    LaunchedEffect(userId) {
        // Load initial profile and unit preference FIRST (before loading blueprint)
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Load profile for mealsPerDay
                val profileDoc = db.collection("users").document(userId).get().await()
                if (profileDoc.exists()) {
                    val currentMealsPerDay = profileDoc.getLong("mealsPerDay")?.toInt() ?: 3
                    withContext(Dispatchers.Main) {
                        userMealsPerDay = currentMealsPerDay
                        lastMealsPerDay = currentMealsPerDay
                    }
                }
                
                // Load unit preference from goals (as fallback - blueprint will override if it has useImperial)
                try {
                    val goalsResult = repository.getUserGoals(userId)
                    val goals = goalsResult.getOrNull()
                    val useImperialValue = goals?.get("useImperial") as? Boolean ?: true
                    withContext(Dispatchers.Main) {
                        // Only set if not already set from blueprint
                        if (useImperial == null) {
                            useImperial = useImperialValue
                        }
                    }
                    android.util.Log.d("WeeklyBlueprint", "Loaded useImperial preference (fallback): $useImperialValue")
                } catch (e: Exception) {
                    android.util.Log.w("WeeklyBlueprint", "Could not load unit preference, defaulting to imperial", e)
                    withContext(Dispatchers.Main) {
                        if (useImperial == null) {
                            useImperial = true
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("WeeklyBlueprint", "Error loading profile", e)
            }
        }
        
                // Load blueprint AFTER preference is loaded (blueprint will override with its stored useImperial)
        loadWeeklyPlan()
        
        // Load friends list for sharing
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val friendsResult = repository.getFriends(userId)
                friendsResult.onSuccess { friendsList ->
                    withContext(Dispatchers.Main) {
                        friends = friendsList
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("WeeklyBlueprint", "Could not load friends list", e)
            }
        }
    }
    
    // Load friends when share dialog opens
    LaunchedEffect(showShareDialog) {
        if (showShareDialog) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val friendsResult = repository.getFriends(userId)
                    friendsResult.onSuccess { friendsList ->
                        withContext(Dispatchers.Main) {
                            friends = friendsList
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("WeeklyBlueprint", "Could not load friends list", e)
                }
            }
        }
    }
    
    // Listen for profile changes using DisposableEffect
    DisposableEffect(userId) {
        val profileListener = db.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("WeeklyBlueprint", "Profile listener error", error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null && snapshot.exists()) {
                    val currentMealsPerDay = snapshot.getLong("mealsPerDay")?.toInt() ?: 3
                    val previousMealsPerDay = userMealsPerDay
                    userMealsPerDay = currentMealsPerDay
                    
                    // Check if mealsPerDay changed and we need to regenerate
                    if (previousMealsPerDay != null && previousMealsPerDay != currentMealsPerDay && weeklyBlueprint != null) {
                        // Show regenerate dialog
                        showRegenerateDialog = true
                    }
                }
            }
        
        onDispose {
            profileListener.remove()
        }
    }
    
    fun toggleCategory(category: String) {
        expandedCategories = if (expandedCategories.contains(category)) {
            expandedCategories - category
        } else {
            expandedCategories + category
        }
    }
    
    fun toggleBought(category: String, index: Int) {
        // Optimistic update - update UI immediately
        val currentBlueprint = weeklyBlueprint
        if (currentBlueprint == null) {
            android.util.Log.w("WeeklyBlueprint", "Cannot toggle bought: blueprint is null")
            return
        }
        
        val shoppingList = currentBlueprint.get("shoppingList") as? MutableMap<String, Any>
        if (shoppingList == null) {
            android.util.Log.w("WeeklyBlueprint", "Cannot toggle bought: shoppingList is null")
            return
        }
        
        val categoryItems = (shoppingList[category] as? MutableList<*>)?.toMutableList() ?: return
        if (index < 0 || index >= categoryItems.size) {
            android.util.Log.w("WeeklyBlueprint", "Cannot toggle bought: invalid index $index")
            return
        }
        
        val itemMap = (categoryItems[index] as? MutableMap<*, *>)?.toMutableMap() ?: return
        val currentBoughtState = itemMap["bought"] as? Boolean ?: false
        val newBoughtState = !currentBoughtState
        
        android.util.Log.d("WeeklyBlueprint", "Toggling bought for $category[$index]: $currentBoughtState -> $newBoughtState")
        
        itemMap["bought"] = newBoughtState
        categoryItems[index] = itemMap
        shoppingList[category] = categoryItems as Any
        
        // Create a completely new map structure to ensure Compose detects the change
        // Deep copy the shopping list to force recomposition
        val updatedShoppingList = mutableMapOf<String, Any>()
        shoppingList.forEach { (cat, items) ->
            val itemsList = (items as? List<*>)?.toMutableList() ?: mutableListOf<Any>()
            val updatedItems = itemsList.mapIndexed { idx, item ->
                if (idx == index && cat == category) {
                    // This is the item we're updating
                    (item as? Map<*, *>)?.toMutableMap()?.apply {
                        put("bought", newBoughtState)
                    } ?: item
                } else {
                    // Deep copy other items
                    (item as? Map<*, *>)?.toMutableMap() ?: item
                }
            }
            updatedShoppingList[cat] = updatedItems
        }
        
        val updatedBlueprint = currentBlueprint.toMutableMap()
        updatedBlueprint["shoppingList"] = updatedShoppingList
        
        // Update state immediately (optimistic update) on main thread
        coroutineScope.launch(Dispatchers.Main) {
            weeklyBlueprint = convertBlueprintToImperial(updatedBlueprint)
            android.util.Log.d("WeeklyBlueprint", "Updated weeklyBlueprint state - bought: $newBoughtState, new bought count should recalculate")
        }
        
        // Save to Firestore in background
        coroutineScope.launch(Dispatchers.IO) {
            try {
                
                // Save to Firestore
                val weekStarting = getWeekId()
                val updateData = mapOf("shoppingList" to updatedShoppingList)
                
                // Update both collections for compatibility
                try {
                    db.collection("users")
                        .document(userId)
                        .collection("weeklyPlans")
                        .document(weekStarting)
                        .update(updateData)
                        .await()
                } catch (e: Exception) {
                    android.util.Log.w("WeeklyBlueprint", "Could not update weeklyPlans", e)
                }
                
                try {
                    val blueprintDoc = db.collection("users")
                        .document(userId)
                        .collection("weeklyBlueprints")
                        .document(weekStarting)
                        .get()
                        .await()
                    if (blueprintDoc.exists()) {
                        db.collection("users")
                            .document(userId)
                            .collection("weeklyBlueprints")
                            .document(weekStarting)
                            .update(updateData)
                            .await()
                    }
                } catch (e: Exception) {
                    android.util.Log.w("WeeklyBlueprint", "Could not update weeklyBlueprints", e)
                }
            } catch (e: Exception) {
                android.util.Log.e("WeeklyBlueprint", "Error updating bought status", e)
                loadWeeklyPlan() // Revert on error
            }
        }
    }
    
    fun startEditing(category: String, index: Int, field: String) {
        val shoppingList = weeklyBlueprint?.get("shoppingList") as? Map<*, *>
        val categoryItems = shoppingList?.get(category) as? List<*>
        val itemMap = categoryItems?.get(index) as? Map<*, *>
        
        editingItem = Triple(category, index, field)
        editValue = when (field) {
            "quantity" -> itemMap?.get("quantity") as? String ?: ""
            "note" -> itemMap?.get("note") as? String ?: ""
            else -> ""
        }
    }
    
    fun saveEdit() {
        if (editingItem == null) return
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val (category, index, field) = editingItem!!
                val currentBlueprint = weeklyBlueprint
                if (currentBlueprint == null) {
                    android.util.Log.w("WeeklyBlueprint", "Cannot save edit: blueprint is null")
                    return@launch
                }
                
                val shoppingList = currentBlueprint.get("shoppingList") as? MutableMap<String, Any>
                if (shoppingList == null) {
                    android.util.Log.w("WeeklyBlueprint", "Cannot save edit: shoppingList is null")
                    return@launch
                }
                
                val categoryItems = (shoppingList[category] as? MutableList<*>)?.toMutableList() ?: return@launch
                if (index < 0 || index >= categoryItems.size) {
                    android.util.Log.w("WeeklyBlueprint", "Cannot save edit: invalid index $index")
                    return@launch
                }
                
                val itemMap = (categoryItems[index] as? MutableMap<*, *>)?.toMutableMap() ?: return@launch
                
                itemMap[field] = editValue
                categoryItems[index] = itemMap
                shoppingList[category] = categoryItems as Any
                
                val updatedBlueprint = currentBlueprint.toMutableMap()
                updatedBlueprint["shoppingList"] = shoppingList
                
                // Update state on main thread
                withContext(Dispatchers.Main) {
                    weeklyBlueprint = convertBlueprintToImperial(updatedBlueprint)
                    editingItem = null
                    editValue = ""
                }
                
                // Save to Firestore
                val weekStarting = getWeekId()
                val updateData = mapOf("shoppingList" to shoppingList)
                
                // Update both collections for compatibility
                try {
                    db.collection("users")
                        .document(userId)
                        .collection("weeklyPlans")
                        .document(weekStarting)
                        .update(updateData)
                        .await()
                } catch (e: Exception) {
                    android.util.Log.w("WeeklyBlueprint", "Could not update weeklyPlans", e)
                }
                
                try {
                    val blueprintDoc = db.collection("users")
                        .document(userId)
                        .collection("weeklyBlueprints")
                        .document(weekStarting)
                        .get()
                        .await()
                    if (blueprintDoc.exists()) {
                        db.collection("users")
                            .document(userId)
                            .collection("weeklyBlueprints")
                            .document(weekStarting)
                            .update(updateData)
                            .await()
                    }
                } catch (e: Exception) {
                    android.util.Log.w("WeeklyBlueprint", "Could not update weeklyBlueprints", e)
                }
                
                editingItem = null
                editValue = ""
            } catch (e: Exception) {
                android.util.Log.e("WeeklyBlueprint", "Error saving edit", e)
            }
        }
    }
    
    fun handleRegenerate() {
        if (userId.isBlank()) {
            android.util.Log.e("WeeklyBlueprint", "Cannot regenerate: userId is blank")
            return
        }
        
        // Check subscription before generating
        coroutineScope.launch {
            val tier = SubscriptionService.getUserTier(userId)
            val canUse = SubscriptionService.canUseAIFeature(userId, AIFeature.WEEKLY_BLUEPRINT_AI)
            
            if (!canUse || tier == SubscriptionTier.FREE) {
                showUpgradeDialog = true
                showRegenerateDialog = false
                return@launch
            }
        }
        
        showRegenerateDialog = false
        coroutineScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    regenerating = true
                }
                
                val generateFunction = try {
                    functions.getHttpsCallable("generateWeeklyBlueprint")
                } catch (e: Exception) {
                    functions.getHttpsCallable("generateWeeklyShoppingList")
                }
                
                // CRITICAL: Handle timeout - function can take 2-4 minutes but client times out at 60s
                // If we get a timeout, poll Firestore instead of waiting for function response
                val weekStarting = getWeekId()
                
                // Get the timestamp of the existing blueprint (if any) before generating
                val existingBlueprintDoc = db.collection("users")
                    .document(userId)
                    .collection("weeklyBlueprints")
                    .document(weekStarting)
                    .get()
                    .await()
                
                val existingGeneratedAt = if (existingBlueprintDoc.exists()) {
                    val existingData = existingBlueprintDoc.data
                    (existingData?.get("generatedAt") as? com.google.firebase.Timestamp)?.toDate()?.time
                } else {
                    null
                }
                
                android.util.Log.d("WeeklyBlueprint", "Existing blueprint generatedAt: $existingGeneratedAt")
                
                try {
                    // Try to call and wait - but with timeout handling
                    generateFunction.call().await()
                    android.util.Log.d("WeeklyBlueprint", "✅ Blueprint generation completed successfully")
                } catch (e: java.io.InterruptedIOException) {
                    // Client timeout - function is still running in background
                    android.util.Log.w("WeeklyBlueprint", "⏱️ Client timeout - function still running, polling Firestore...")
                    // Fall through to polling logic below
                } catch (e: java.io.IOException) {
                    // Check if it's a timeout/canceled error
                    if (e.message?.contains("timeout", ignoreCase = true) == true || 
                        e.message?.contains("Canceled", ignoreCase = true) == true) {
                        android.util.Log.w("WeeklyBlueprint", "⏱️ Timeout/Canceled - function still running, polling Firestore...")
                        // Fall through to polling logic below
                    } else {
                        throw e // Re-throw if it's a different IOException
                    }
                } catch (e: com.google.firebase.functions.FirebaseFunctionsException) {
                    // Check if it's DEADLINE_EXCEEDED
                    if (e.code == com.google.firebase.functions.FirebaseFunctionsException.Code.DEADLINE_EXCEEDED) {
                        android.util.Log.w("WeeklyBlueprint", "⏱️ DEADLINE_EXCEEDED - function still running, polling Firestore...")
                        // Fall through to polling logic below
                    } else {
                        throw e // Re-throw other FirebaseFunctionsException
                    }
                }
                
                // Poll Firestore for blueprint (works for both successful calls and timeouts)
                android.util.Log.d("WeeklyBlueprint", "Polling Firestore for blueprint (week: $weekStarting)...")
                var blueprintFound = false
                var pollAttempts = 0
                val maxPollAttempts = 20 // Poll for up to 2 minutes (20 * 6 seconds = 120 seconds) - user requested max 20 attempts
                
                // Wait 6 seconds before first poll (give function time to start)
                kotlinx.coroutines.delay(6000)
                
                while (!blueprintFound && pollAttempts < maxPollAttempts) {
                    pollAttempts++
                    
                    try {
                        // CRITICAL: Use correct path - users/{userId}/weeklyBlueprints/{week}
                        val blueprintDoc = db.collection("users")
                            .document(userId)
                            .collection("weeklyBlueprints")
                            .document(weekStarting)
                            .get()
                            .await()
                        
                        if (blueprintDoc.exists()) {
                            val data = blueprintDoc.data
                            if (data != null && data.containsKey("meals")) {
                                // Check if this is a NEW blueprint (generated after we started)
                                val newGeneratedAt = (data.get("generatedAt") as? com.google.firebase.Timestamp)?.toDate()?.time
                                if (existingGeneratedAt == null || newGeneratedAt == null || newGeneratedAt > existingGeneratedAt) {
                                    android.util.Log.d("WeeklyBlueprint", "✅ NEW blueprint found in Firestore after ${pollAttempts * 6} seconds")
                                    blueprintFound = true
                                    break
                                } else {
                                    android.util.Log.d("WeeklyBlueprint", "⏳ Old blueprint still in Firestore, waiting for new one...")
                                }
                            }
                        }
                        
                        if (!blueprintFound) {
                            android.util.Log.d("WeeklyBlueprint", "⏳ Blueprint not ready yet (attempt $pollAttempts/$maxPollAttempts)...")
                            // Wait 6 seconds before next poll
                            kotlinx.coroutines.delay(6000)
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("WeeklyBlueprint", "Error polling for blueprint: ${e.message}")
                        // Wait before retrying on error
                        kotlinx.coroutines.delay(6000)
                    }
                }
                
                if (!blueprintFound) {
                    android.util.Log.w("WeeklyBlueprint", "⚠️ Blueprint not found after ${maxPollAttempts * 6} seconds of polling")
                }
                
                // Small delay to ensure Firestore has propagated
                kotlinx.coroutines.delay(2000)
                
                // Reload the plan
                loadWeeklyPlan()
            } catch (e: Exception) {
                android.util.Log.e("WeeklyBlueprint", "Error regenerating blueprint", e)
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "Failed to regenerate blueprint: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    regenerating = false
                }
            }
        }
    }
    
    fun handleSharePNG() {
        coroutineScope.launch(Dispatchers.Main) {
            try {
                // Calculate summary values from weeklyBlueprint
                val currentShoppingList = weeklyBlueprint?.get("shoppingList") as? Map<*, *>
                val currentItemCount = currentShoppingList?.values?.sumOf { (it as? List<*>)?.size ?: 0 } ?: 0
                val currentBoughtCount = currentShoppingList?.values?.sumOf { category ->
                    (category as? List<*>)?.count { (it as? Map<*, *>)?.get("bought") == true } ?: 0
                } ?: 0
                
                // Generate a shareable image using ShareImageGenerator
                val shareImageGenerator = com.coachie.app.service.ShareImageGenerator(context)
                
                // Build summary text for the image
                val title = "Weekly Blueprint"
                val metric = "$currentItemCount items"
                val subtitle = "$currentBoughtCount/$currentItemCount bought"
                
                val imageUri = shareImageGenerator.generateShareImage(
                    title = title,
                    metric = metric,
                    subtitle = subtitle,
                    backgroundColorStart = android.graphics.Color.parseColor("#667eea"),
                    backgroundColorEnd = android.graphics.Color.parseColor("#764ba2")
                )
                
                if (imageUri != null) {
                    ShareCompat.IntentBuilder(context)
                        .setStream(imageUri)
                        .setType("image/png")
                        .setChooserTitle("Share Weekly Blueprint")
                        .startChooser()
                } else {
                    // Fallback: Share as text
                    val shareText = buildString {
                        append("My Weekly Blueprint\n")
                        append("$currentItemCount items • $currentBoughtCount/$currentItemCount bought\n\n")
                        if (currentShoppingList != null) {
                            (currentShoppingList as Map<String, *>).forEach { (category, items) ->
                                append("$category:\n")
                                (items as? List<*>)?.forEach { item ->
                                    val itemMap = item as? Map<*, *>
                                    val itemName = itemMap?.get("item") as? String ?: ""
                                    val quantity = itemMap?.get("quantity") as? String ?: ""
                                    // Convert to imperial ONLY if blueprint was generated in metric
                                    val blueprintUseImperial = weeklyBlueprint?.get("useImperial") as? Boolean
                                    val displayQuantity = if (useImperial == true && blueprintUseImperial != true) {
                                        convertQuantityToImperial(quantity)
                                    } else {
                                        quantity
                                    }
                                    val bought = itemMap?.get("bought") as? Boolean ?: false
                                    append("  ${if (bought) "✓" else "○"} $itemName - $displayQuantity\n")
                                }
                                append("\n")
                            }
                        }
                    }
                    
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        putExtra(Intent.EXTRA_SUBJECT, "My Weekly Blueprint")
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Weekly Blueprint"))
                }
            } catch (e: Exception) {
                android.util.Log.e("WeeklyBlueprint", "Error sharing PNG", e)
                android.widget.Toast.makeText(
                    context,
                    "Failed to share: ${e.message}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    fun handleExportText() {
        coroutineScope.launch(Dispatchers.Main) {
            try {
                val shoppingList = weeklyBlueprint?.get("shoppingList") as? Map<*, *>
                val meals = weeklyBlueprint?.get("meals") as? List<*>
                
                val shareText = buildString {
                    append("📋 MY WEEKLY BLUEPRINT\n")
                    append("=".repeat(50))
                    append("\n\n")
                    
                    // Shopping List Section
                    append("🛒 SHOPPING LIST\n")
                    append("-".repeat(50))
                    append("\n\n")
                    
                    if (shoppingList != null) {
                        var totalItems = 0
                        var boughtItems = 0
                        
                        (shoppingList as Map<String, *>).forEach { (category, items) ->
                            val categoryItems = items as? List<*>
                            if (categoryItems != null && categoryItems.isNotEmpty()) {
                                append("$category:\n")
                                categoryItems.forEach { item ->
                                    val itemMap = item as? Map<*, *>
                                    val itemName = itemMap?.get("item") as? String ?: ""
                                    val quantity = itemMap?.get("quantity") as? String ?: ""
                                    // Convert to imperial ONLY if blueprint was generated in metric
                                    val blueprintUseImperial = weeklyBlueprint?.get("useImperial") as? Boolean
                                    val displayQuantity = if (useImperial == true && blueprintUseImperial != true) {
                                        convertQuantityToImperial(quantity)
                                    } else {
                                        quantity
                                    }
                                    val bought = itemMap?.get("bought") as? Boolean ?: false
                                    val note = itemMap?.get("note") as? String
                                    
                                    append("  ${if (bought) "✓" else "○"} $itemName - $displayQuantity")
                                    if (!note.isNullOrBlank()) {
                                        append(" (Note: $note)")
                                    }
                                    append("\n")
                                    
                                    totalItems++
                                    if (bought) boughtItems++
                                }
                                append("\n")
                            }
                        }
                        
                        append("\nTotal: $totalItems items ($boughtItems bought)\n\n")
                    } else {
                        append("No shopping list available\n\n")
                    }
                    
                    // Meal Plan Section
                    append("🍽️ 7-DAY MEAL PLAN\n")
                    append("=".repeat(50))
                    append("\n\n")
                    
                    if (meals != null && meals.isNotEmpty()) {
                        val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                        meals.forEachIndexed { index, dayMeal ->
                            if (dayMeal is Map<*, *>) {
                                val dayName = dayMeal["day"] as? String ?: daysOfWeek.getOrNull(index) ?: "Day ${index + 1}"
                                append("$dayName\n")
                                append("-".repeat(30))
                                append("\n")
                                
                                // Breakfast
                                val breakfast = dayMeal["breakfast"] as? Map<*, *>
                                if (breakfast != null) {
                                    append("🌅 Breakfast: ${breakfast["name"] as? String ?: "N/A"}\n")
                                    append("   Calories: ${breakfast["calories"] ?: 0} | Protein: ${breakfast["protein"] ?: 0}g | Carbs: ${breakfast["carbs"] ?: 0}g | Fat: ${breakfast["fat"] ?: 0}g\n")
                                    val breakfastIngredients = breakfast["ingredients"] as? List<*>
                                    if (breakfastIngredients != null && breakfastIngredients.isNotEmpty()) {
                                        append("   Ingredients: ${breakfastIngredients.joinToString(", ")}\n")
                                    }
                                    append("\n")
                                }
                                
                                // Lunch
                                val lunch = dayMeal["lunch"] as? Map<*, *>
                                if (lunch != null) {
                                    append("☀️ Lunch: ${lunch["name"] as? String ?: "N/A"}\n")
                                    append("   Calories: ${lunch["calories"] ?: 0} | Protein: ${lunch["protein"] ?: 0}g | Carbs: ${lunch["carbs"] ?: 0}g | Fat: ${lunch["fat"] ?: 0}g\n")
                                    val lunchIngredients = lunch["ingredients"] as? List<*>
                                    if (lunchIngredients != null && lunchIngredients.isNotEmpty()) {
                                        append("   Ingredients: ${lunchIngredients.joinToString(", ")}\n")
                                    }
                                    append("\n")
                                }
                                
                                // Dinner
                                val dinner = dayMeal["dinner"] as? Map<*, *>
                                if (dinner != null) {
                                    append("🌙 Dinner: ${dinner["name"] as? String ?: "N/A"}\n")
                                    append("   Calories: ${dinner["calories"] ?: 0} | Protein: ${dinner["protein"] ?: 0}g | Carbs: ${dinner["carbs"] ?: 0}g | Fat: ${dinner["fat"] ?: 0}g\n")
                                    val dinnerIngredients = dinner["ingredients"] as? List<*>
                                    if (dinnerIngredients != null && dinnerIngredients.isNotEmpty()) {
                                        append("   Ingredients: ${dinnerIngredients.joinToString(", ")}\n")
                                    }
                                    append("\n")
                                }
                                
                                // Snacks
                                val snacks = dayMeal["snacks"] as? List<*>
                                if (snacks != null && snacks.isNotEmpty()) {
                                    snacks.forEach { snack ->
                                        if (snack is Map<*, *>) {
                                            append("🍎 Snack: ${snack["name"] as? String ?: "N/A"}\n")
                                            append("   Calories: ${snack["calories"] ?: 0} | Protein: ${snack["protein"] ?: 0}g | Carbs: ${snack["carbs"] ?: 0}g | Fat: ${snack["fat"] ?: 0}g\n")
                                            val snackIngredients = snack["ingredients"] as? List<*>
                                            if (snackIngredients != null && snackIngredients.isNotEmpty()) {
                                                append("   Ingredients: ${snackIngredients.joinToString(", ")}\n")
                                            }
                                            append("\n")
                                        }
                                    }
                                }
                                
                                append("\n")
                            }
                        }
                    } else {
                        append("No meal plan available\n")
                    }
                    
                    append("\n")
                    append("Generated by Coachie\n")
                }
                
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    putExtra(Intent.EXTRA_SUBJECT, "My Weekly Blueprint")
                }
                context.startActivity(Intent.createChooser(intent, "Share Weekly Blueprint"))
            } catch (e: Exception) {
                android.util.Log.e("WeeklyBlueprint", "Error exporting text", e)
                android.widget.Toast.makeText(
                    context,
                    "Failed to export: ${e.message}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    fun handleExportPDF() {
        coroutineScope.launch(Dispatchers.Main) {
            try {
                val shoppingList = weeklyBlueprint?.get("shoppingList") as? Map<*, *>
                val meals = weeklyBlueprint?.get("meals") as? List<*>
                
                // Create PDF document
                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size in points
                var page = pdfDocument.startPage(pageInfo)
                var canvas = page.canvas
                
                // Set up paint
                val titlePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#6B46C1")
                    textSize = 24f
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    isAntiAlias = true
                }
                
                val headingPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#1F2937")
                    textSize = 18f
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    isAntiAlias = true
                }
                
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#374151")
                    textSize = 12f
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
                    isAntiAlias = true
                }
                
                val smallTextPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#6B7280")
                    textSize = 10f
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
                    isAntiAlias = true
                }
                
                var yPos = 50f
                val margin = 50f
                val lineHeight = 20f
                val pageHeight = 842f - margin
                
                // Helper function to check and create new page if needed
                fun checkNewPage(requiredSpace: Float) {
                    if (yPos + requiredSpace > pageHeight) {
                        pdfDocument.finishPage(page)
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        yPos = 50f
                    }
                }
                
                // Title
                canvas.drawText("MY WEEKLY BLUEPRINT", margin, yPos, titlePaint)
                yPos += 40f
                
                // Date
                val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                val weekId = getWeekId()
                val weekDate = try {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(weekId)
                    dateFormat.format(date ?: java.util.Date())
                } catch (e: Exception) {
                    weekId
                }
                canvas.drawText("Week of $weekDate", margin, yPos, textPaint)
                yPos += 30f
                
                // Shopping List Section
                checkNewPage(100f)
                canvas.drawText("SHOPPING LIST", margin, yPos, headingPaint)
                yPos += 25f
                
                if (shoppingList != null) {
                    var totalItems = 0
                    var boughtItems = 0
                    
                    (shoppingList as Map<String, *>).forEach { (category, items) ->
                        val categoryItems = items as? List<*>
                        if (categoryItems != null && categoryItems.isNotEmpty()) {
                            checkNewPage(100f)
                            
                            val categoryHeadingPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.parseColor("#1F2937")
                                textSize = 14f
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                isAntiAlias = true
                            }
                            
                            canvas.drawText("$category:", margin, yPos, categoryHeadingPaint)
                            yPos += lineHeight
                            
                            categoryItems.forEach { item ->
                                val itemMap = item as? Map<*, *>
                                val itemName = itemMap?.get("item") as? String ?: ""
                                val quantity = itemMap?.get("quantity") as? String ?: ""
                                val displayQuantity = if (useImperial == true) convertQuantityToImperial(quantity) else quantity
                                val bought = itemMap?.get("bought") as? Boolean ?: false
                                
                                checkNewPage(50f)
                                
                                val checkmark = if (bought) "✓" else "○"
                                canvas.drawText("  $checkmark $itemName - $displayQuantity", margin + 20f, yPos, textPaint)
                                yPos += lineHeight
                                
                                totalItems++
                                if (bought) boughtItems++
                            }
                            yPos += 5f
                        }
                    }
                    
                    checkNewPage(50f)
                    canvas.drawText("Total: $totalItems items ($boughtItems bought)", margin, yPos, textPaint)
                    yPos += 30f
                } else {
                    canvas.drawText("No shopping list available", margin, yPos, textPaint)
                    yPos += 30f
                }
                
                // Meal Plan Section
                checkNewPage(100f)
                canvas.drawText("7-DAY MEAL PLAN", margin, yPos, headingPaint)
                yPos += 25f
                
                if (meals != null && meals.isNotEmpty()) {
                    val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                    meals.forEachIndexed { index, dayMeal ->
                        if (dayMeal is Map<*, *>) {
                            val dayName = dayMeal["day"] as? String ?: daysOfWeek.getOrNull(index) ?: "Day ${index + 1}"
                            
                            checkNewPage(150f)
                            
                            val dayHeadingPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.parseColor("#1F2937")
                                textSize = 14f
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                isAntiAlias = true
                            }
                            
                            canvas.drawText(dayName, margin, yPos, dayHeadingPaint)
                            yPos += lineHeight + 5f
                            
                            // Breakfast
                            val breakfast = dayMeal["breakfast"] as? Map<*, *>
                            if (breakfast != null) {
                                checkNewPage(50f)
                                val breakfastName = breakfast["name"] as? String ?: "N/A"
                                canvas.drawText("  Breakfast: $breakfastName", margin + 10f, yPos, textPaint)
                                yPos += lineHeight
                                val breakfastCal = breakfast["calories"] as? Number ?: 0
                                val breakfastProt = breakfast["protein"] as? Number ?: 0
                                val breakfastCarbs = breakfast["carbs"] as? Number ?: 0
                                val breakfastFat = breakfast["fat"] as? Number ?: 0
                                canvas.drawText("    ${breakfastCal} cal | ${breakfastProt}g protein | ${breakfastCarbs}g carbs | ${breakfastFat}g fat", margin + 20f, yPos, smallTextPaint)
                                yPos += lineHeight + 5f
                            }
                            
                            // Lunch
                            val lunch = dayMeal["lunch"] as? Map<*, *>
                            if (lunch != null) {
                                checkNewPage(50f)
                                val lunchName = lunch["name"] as? String ?: "N/A"
                                canvas.drawText("  Lunch: $lunchName", margin + 10f, yPos, textPaint)
                                yPos += lineHeight
                                val lunchCal = lunch["calories"] as? Number ?: 0
                                val lunchProt = lunch["protein"] as? Number ?: 0
                                val lunchCarbs = lunch["carbs"] as? Number ?: 0
                                val lunchFat = lunch["fat"] as? Number ?: 0
                                canvas.drawText("    ${lunchCal} cal | ${lunchProt}g protein | ${lunchCarbs}g carbs | ${lunchFat}g fat", margin + 20f, yPos, smallTextPaint)
                                yPos += lineHeight + 5f
                            }
                            
                            // Dinner
                            val dinner = dayMeal["dinner"] as? Map<*, *>
                            if (dinner != null) {
                                checkNewPage(50f)
                                val dinnerName = dinner["name"] as? String ?: "N/A"
                                canvas.drawText("  Dinner: $dinnerName", margin + 10f, yPos, textPaint)
                                yPos += lineHeight
                                val dinnerCal = dinner["calories"] as? Number ?: 0
                                val dinnerProt = dinner["protein"] as? Number ?: 0
                                val dinnerCarbs = dinner["carbs"] as? Number ?: 0
                                val dinnerFat = dinner["fat"] as? Number ?: 0
                                canvas.drawText("    ${dinnerCal} cal | ${dinnerProt}g protein | ${dinnerCarbs}g carbs | ${dinnerFat}g fat", margin + 20f, yPos, smallTextPaint)
                                yPos += lineHeight + 5f
                            }
                            
                            // Snacks
                            val snacks = dayMeal["snacks"] as? List<*>
                            if (snacks != null && snacks.isNotEmpty()) {
                                snacks.forEach { snack ->
                                    if (snack is Map<*, *>) {
                                        checkNewPage(50f)
                                        val snackName = snack["name"] as? String ?: "N/A"
                                        canvas.drawText("  Snack: $snackName", margin + 10f, yPos, textPaint)
                                        yPos += lineHeight
                                        val snackCal = snack["calories"] as? Number ?: 0
                                        val snackProt = snack["protein"] as? Number ?: 0
                                        val snackCarbs = snack["carbs"] as? Number ?: 0
                                        val snackFat = snack["fat"] as? Number ?: 0
                                        canvas.drawText("    ${snackCal} cal | ${snackProt}g protein | ${snackCarbs}g carbs | ${snackFat}g fat", margin + 20f, yPos, smallTextPaint)
                                        yPos += lineHeight + 5f
                                    }
                                }
                            }
                            
                            yPos += 10f
                        }
                    }
                } else {
                    canvas.drawText("No meal plan available", margin, yPos, textPaint)
                }
                
                // Footer
                checkNewPage(30f)
                yPos = pageHeight - 20f
                canvas.drawText("Generated by Coachie", margin, yPos, smallTextPaint)
                
                pdfDocument.finishPage(page)
                
                // Save PDF to file
                val fileName = "weekly_blueprint_${getWeekId()}.pdf"
                val pdfFile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Use app-specific directory for Android 10+
                    File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
                } else {
                    // Use Downloads folder for older Android versions (requires permission)
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!downloadsDir.exists()) {
                        downloadsDir.mkdirs()
                    }
                    File(downloadsDir, fileName)
                }
                
                // Check for write permission if needed
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        android.widget.Toast.makeText(
                            context,
                            "Storage permission needed to save PDF",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        pdfDocument.close()
                        return@launch
                    }
                }
                
                FileOutputStream(pdfFile).use { out ->
                    pdfDocument.writeTo(out)
                }
                pdfDocument.close()
                
                // Share the PDF
                val pdfUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    pdfFile
                )
                
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, pdfUri)
                    putExtra(Intent.EXTRA_SUBJECT, "My Weekly Blueprint")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                context.startActivity(Intent.createChooser(shareIntent, "Share Weekly Blueprint PDF"))
                
                android.widget.Toast.makeText(
                    context,
                    "PDF saved and ready to share",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                android.util.Log.e("WeeklyBlueprint", "Error exporting PDF", e)
                android.widget.Toast.makeText(
                    context,
                    "Failed to export PDF: ${e.message}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    // Get gender to ensure gradient updates correctly
    val gender = LocalGender.current
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val shoppingList = weeklyBlueprint?.get("shoppingList") as? Map<*, *>
    // Get categories from shopping list (only those that are arrays)
    val categories: List<String> = shoppingList?.keys
        ?.map { it.toString() }
        ?.filter { (shoppingList[it] as? List<*>) != null }
        ?: emptyList()
    
    // Calculate summary - use remember to ensure it recalculates when weeklyBlueprint changes
    val itemCount = remember(weeklyBlueprint) {
        shoppingList?.values?.sumOf { (it as? List<*>)?.size ?: 0 } ?: 0
    }
    val boughtCount = remember(weeklyBlueprint) {
        shoppingList?.values?.sumOf { category ->
            (category as? List<*>)?.count { (it as? Map<*, *>)?.get("bought") == true } ?: 0
        } ?: 0
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBackground)
    ) {
        Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Your Weekly Blueprint",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { handleSharePNG() }) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading your blueprint...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else if (weeklyBlueprint == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.RestaurantMenu,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Blueprint Yet",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Generate your personalized weekly meal plan and shopping list",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    // Show generate button - will check subscription on click
                    Button(
                        onClick = { 
                            if (currentTier == SubscriptionTier.FREE) {
                                showUpgradeDialog = true
                            } else {
                                handleRegenerate()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Generate Weekly Blueprint")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Summary Card
                    item {
                        CoachieCard(
                            colors = CoachieCardDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                            ) {
                                Text(
                                    text = "Shopping List Summary",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "$itemCount",
                                            style = MaterialTheme.typography.displaySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Items",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "$boughtCount",
                                            style = MaterialTheme.typography.displaySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Bought",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability
                                        )
                                    }
                                }
                                weeklyBlueprint?.get("dailyCalories")?.let { calories ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "$calories kcal/day",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability
                                    )
                                }
                            }
                        }
                    }
                    
                    // Unit mismatch warning
                    if (useImperial == true && shoppingList != null) {
                        val hasMetricUnits = shoppingList.values.any { category ->
                            (category as? List<*>)?.any { item ->
                                val quantity = (item as? Map<*, *>)?.get("quantity") as? String ?: ""
                                quantity.contains("kg", ignoreCase = true) ||
                                (quantity.contains("g", ignoreCase = true) && !quantity.contains("fl oz", ignoreCase = true)) ||
                                quantity.contains("ml", ignoreCase = true) ||
                                (quantity.contains("l", ignoreCase = true) && !quantity.contains("fl", ignoreCase = true))
                            } ?: false
                        }
                        
                        if (hasMetricUnits) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Units Mismatch",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Text(
                                                text = "This blueprint uses metric units, but you have imperial selected. Refresh to fix.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Serving Size Selector
                    item {
                        CoachieCard(
                            colors = CoachieCardDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Serving Size",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Adjust recipes and shopping list for number of servings",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Shopping for:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    OutlinedButton(
                                        onClick = { selectedServings = (selectedServings - 1).coerceAtLeast(1) },
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
                                        onClick = { selectedServings = (selectedServings + 1).coerceAtMost(20) },
                                        modifier = Modifier.size(36.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("+", fontSize = 18.sp)
                                    }
                                    Text(
                                        text = "people",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability
                                    )
                                }
                                if (selectedServings != originalServings) {
                                    Text(
                                        text = "Quantities adjusted for $selectedServings ${if (selectedServings == 1) "person" else "people"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontStyle = FontStyle.Italic
                                    )
                                }
                            }
                        }
                    }
                    
                    // Meals/Recipes Section Header
                    weeklyBlueprint?.get("meals")?.let { meals ->
                        if (meals is List<*> && meals.isNotEmpty()) {
                            item {
                                CoachieCard(
                                    colors = CoachieCardDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp)
                                    ) {
                                        Text(
                                            text = "7-Day Meal Plan",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Tap any meal to see ingredients and instructions",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface, // Changed from onSurfaceVariant to onSurface (dark) for better readability
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                            
                            // Individual day meals
                            meals.forEachIndexed { dayIndex, dayMeal ->
                                val dayMap = dayMeal as? Map<*, *> ?: return@forEachIndexed
                                val dayName = dayMap["day"] as? String ?: "Day ${dayIndex + 1}"
                                val isDayExpanded = expandedDays.contains(dayName)
                                
                                // Debug logging to see what meals are available
                                android.util.Log.d("WeeklyBlueprint", "Day: $dayName")
                                android.util.Log.d("WeeklyBlueprint", "  Has breakfast: ${dayMap.containsKey("breakfast")}")
                                android.util.Log.d("WeeklyBlueprint", "  Has lunch: ${dayMap.containsKey("lunch")}")
                                android.util.Log.d("WeeklyBlueprint", "  Has dinner: ${dayMap.containsKey("dinner")}")
                                android.util.Log.d("WeeklyBlueprint", "  Has snacks: ${dayMap.containsKey("snacks")}")
                                android.util.Log.d("WeeklyBlueprint", "  All keys: ${dayMap.keys.joinToString()}")
                                
                                // Day header - clickable to expand/collapse
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { 
                                                expandedDays = if (isDayExpanded) {
                                                    expandedDays - dayName
                                                } else {
                                                    expandedDays + dayName
                                                }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = dayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Icon(
                                            imageVector = if (isDayExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                            contentDescription = if (isDayExpanded) "Collapse" else "Expand",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                
                                // Day meals - only show if expanded
                                if (isDayExpanded) {
                                    // Breakfast
                                    val breakfast = dayMap["breakfast"]
                                    if (breakfast != null && breakfast is Map<*, *>) {
                                        item {
                                            MealCard(breakfast, "Breakfast", selectedServings, originalServings, useImperial, weeklyBlueprint = weeklyBlueprint, onLogMeal = { meal, servings -> logMealFromBlueprint(meal, servings) }, onSaveRecipe = { recipe -> saveRecipeFromBlueprint(recipe) }, onShareRecipe = { recipe -> shareRecipeFromBlueprint(recipe) }, mealDataToRecipe = { meal, userId, servings, origServings -> mealDataToRecipe(meal, userId, servings, origServings) }, userId = userId)
                                        }
                                    } else {
                                        android.util.Log.w("WeeklyBlueprint", "  Breakfast is null or not a Map for $dayName")
                                    }
                                    
                                    // Lunch
                                    val lunch = dayMap["lunch"]
                                    if (lunch != null && lunch is Map<*, *>) {
                                        item {
                                            MealCard(lunch, "Lunch", selectedServings, originalServings, useImperial, weeklyBlueprint = weeklyBlueprint, onLogMeal = { meal, servings -> logMealFromBlueprint(meal, servings) }, onSaveRecipe = { recipe -> saveRecipeFromBlueprint(recipe) }, onShareRecipe = { recipe -> shareRecipeFromBlueprint(recipe) }, mealDataToRecipe = { meal, userId, servings, origServings -> mealDataToRecipe(meal, userId, servings, origServings) }, userId = userId)
                                        }
                                    } else {
                                        android.util.Log.w("WeeklyBlueprint", "  Lunch is null or not a Map for $dayName")
                                    }
                                    
                                    // Dinner
                                    val dinner = dayMap["dinner"]
                                    if (dinner != null && dinner is Map<*, *>) {
                                        item {
                                            MealCard(dinner, "Dinner", selectedServings, originalServings, useImperial, weeklyBlueprint = weeklyBlueprint, onLogMeal = { meal, servings -> logMealFromBlueprint(meal, servings) }, onSaveRecipe = { recipe -> saveRecipeFromBlueprint(recipe) }, onShareRecipe = { recipe -> shareRecipeFromBlueprint(recipe) }, mealDataToRecipe = { meal, userId, servings, origServings -> mealDataToRecipe(meal, userId, servings, origServings) }, userId = userId)
                                        }
                                    } else {
                                        android.util.Log.w("WeeklyBlueprint", "  Dinner is null or not a Map for $dayName")
                                    }
                                    
                                    // Snacks
                                    val snacks = dayMap["snacks"]
                                    if (snacks != null && snacks is List<*>) {
                                        snacks.forEach { snack ->
                                            if (snack is Map<*, *>) {
                                                item {
                                                    MealCard(snack, "Snack", selectedServings, originalServings, useImperial, weeklyBlueprint = weeklyBlueprint, onLogMeal = { meal, servings -> logMealFromBlueprint(meal, servings) }, onSaveRecipe = { recipe -> saveRecipeFromBlueprint(recipe) }, onShareRecipe = { recipe -> shareRecipeFromBlueprint(recipe) }, mealDataToRecipe = { meal, userId, servings, origServings -> mealDataToRecipe(meal, userId, servings, origServings) }, userId = userId)
                                                }
                                            }
                                        }
                                    } else {
                                        android.util.Log.w("WeeklyBlueprint", "  Snacks is null or not a List for $dayName")
                                    }
                                }
                            }
                        }
                    }
                    
                    // Action Buttons
                    item {
                        CoachieCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CoachieCardDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Refresh Button (Full Width)
                                FilledTonalButton(
                                    onClick = { 
                                        if (currentTier == SubscriptionTier.FREE) {
                                            showUpgradeDialog = true
                                        } else {
                                            handleRegenerate()
                                        }
                                    },
                                    enabled = !regenerating && currentTier != SubscriptionTier.FREE,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color(0xFF6B46C1),
                                        contentColor = Color.White // Keep white for readability on dark purple background
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        if (regenerating) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = Color.White, // Keep white for readability on dark purple background
                                                strokeWidth = 2.5.dp
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                "Generating...",
                                                color = Color.White, // Keep white for readability on dark purple background
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Filled.Refresh,
                                                contentDescription = null,
                                                tint = Color.White, // Keep white for readability on dark purple background
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                "Refresh Blueprint",
                                                color = Color.White, // Keep white for readability on dark purple background
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                                
                                // Export Buttons Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Share PNG Button
                                    FilledTonalButton(
                                        onClick = { handleSharePNG() },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color(0xFFF3F4F6),
                                            contentColor = Color(0xFF6B46C1)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Image,
                                                contentDescription = null,
                                                tint = Color(0xFF6B46C1),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                "PNG",
                                                color = Color(0xFF6B46C1),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    
                                    // Export Text Button
                                    FilledTonalButton(
                                        onClick = { handleExportText() },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color(0xFFF3F4F6),
                                            contentColor = Color(0xFF6B46C1)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.TextFields,
                                                contentDescription = null,
                                                tint = Color(0xFF6B46C1),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                "Text",
                                                color = Color(0xFF6B46C1),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    
                                    // Export PDF Button
                                    FilledTonalButton(
                                        onClick = { handleExportPDF() },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color(0xFFF3F4F6),
                                            contentColor = Color(0xFF6B46C1)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Description,
                                                contentDescription = null,
                                                tint = Color(0xFF6B46C1),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                "PDF",
                                                color = Color(0xFF6B46C1),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Shopping List by Category
                    // Include selectedServings in key to force recomposition when serving size changes
                    items(
                        count = categories.size,
                        key = { index -> "${categories[index]}_$selectedServings" }
                    ) { index ->
                        val category = categories[index]
                        // CRITICAL: Recalculate categoryItems when selectedServings changes
                        val categoryItems: List<*> = remember(weeklyBlueprint, category, selectedServings) {
                            when {
                                shoppingList != null -> (shoppingList[category] as? List<*>) ?: listOf<Any>()
                                else -> listOf<Any>()
                            }
                        }
                        val isExpanded = expandedCategories.contains(category)
                        val categoryBoughtCount = categoryItems.count { (it as? Map<*, *>)?.get("bought") == true }
                        
                        CoachieCard(
                            colors = CoachieCardDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                // Category Header
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { toggleCategory(category) },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = category,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "($categoryBoughtCount/${categoryItems.size})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability
                                        )
                                    }
                                }
                                
                                // Category Items
                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    // CRITICAL: Use items() composable to ensure recomposition when selectedServings changes
                                    Column {
                                        categoryItems.forEachIndexed { itemIndex, item ->
                                            // Handle both object format {item: "...", quantity: "..."} and string format "Item – quantity"
                                            val itemMap = item as? Map<*, *>
                                            var itemName: String
                                            var rawQuantity: String
                                            val bought: Boolean
                                            val note: String?
                                            
                                            if (itemMap != null) {
                                                // Object format
                                                itemName = itemMap.get("item") as? String ?: ""
                                                rawQuantity = itemMap.get("quantity") as? String ?: ""
                                                bought = itemMap.get("bought") as? Boolean ?: false
                                                note = itemMap.get("note") as? String
                                                
                                                // Handle case where quantity might be embedded in item name
                                                if (itemName.isNotBlank() && rawQuantity.isBlank() && itemName.contains("–")) {
                                                    val parts = itemName.split("–", limit = 2)
                                                    if (parts.size == 2) {
                                                        itemName = parts[0].trim()
                                                        rawQuantity = parts[1].trim()
                                                    }
                                                }
                                            } else if (item is String) {
                                                // String format: "Item – quantity"
                                                val parts = item.split("–", limit = 2)
                                                itemName = if (parts.isNotEmpty()) parts[0].trim() else item
                                                rawQuantity = if (parts.size > 1) parts[1].trim() else ""
                                                bought = false
                                                note = null
                                            } else {
                                                // Fallback
                                                itemName = item.toString()
                                                rawQuantity = ""
                                                bought = false
                                                note = null
                                            }
                                            
                                            // Scale quantity based on serving size - recalculate when selectedServings changes
                                            // Note: These values are recalculated on every recomposition when selectedServings changes
                                            val scaleFactor = if (originalServings > 0) {
                                                selectedServings.toDouble() / originalServings
                                            } else {
                                                1.0
                                            }
                                            
                                            // CRITICAL: Scale quantity - handle both "2.0 lbs" format and "Item – 2.0 lbs" format
                                            val quantity = if (selectedServings != originalServings && rawQuantity.isNotBlank() && originalServings > 0) {
                                                scaleQuantityString(rawQuantity, scaleFactor)
                                            } else {
                                                rawQuantity
                                            }
                                            
                                            // Also scale item name if it contains quantities (for items stored as strings)
                                            val scaledItemName = if (selectedServings != originalServings && itemName.isNotBlank() && originalServings > 0) {
                                                // Check if item name contains a quantity pattern (number followed by unit)
                                                val quantityPattern = Regex("""(\d+\.?\d*)\s*(lbs?|oz|ounces?|cups?|fl\s*oz|tbsp|tsp|quarts?|pints?|inches?|ft|feet|g|kg|ml|L|cm)\b""")
                                                if (quantityPattern.containsMatchIn(itemName)) {
                                                    quantityPattern.replace(itemName) { matchResult ->
                                                        val numStr = matchResult.groupValues[1]
                                                        val unit = matchResult.groupValues[2]
                                                        val num = numStr.toDoubleOrNull() ?: return@replace matchResult.value
                                                        val scaledNum = num * scaleFactor
                                                        val formattedNum = if (scaledNum % 1.0 == 0.0) {
                                                            scaledNum.toInt().toString()
                                                        } else {
                                                            String.format("%.1f", scaledNum)
                                                        }
                                                        "$formattedNum $unit"
                                                    }
                                                } else {
                                                    itemName
                                                }
                                            } else {
                                                itemName
                                            }
                                            
                                            // DISABLED: Metric unit detection causing too many false positives
                                            // Items like "celery", "peppers", "garlic", "lettuce" were being flagged incorrectly
                                            val hasMetricUnits = false
                                            
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Checkbox
                                                IconButton(
                                                    onClick = { toggleBought(category, index) },
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (bought) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                                        contentDescription = if (bought) "Mark as not bought" else "Mark as bought",
                                                        tint = if (bought) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface, // Changed from onSurfaceVariant to onSurface (dark) for better readability
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                                
                                                // Item Content
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = scaledItemName,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color = MaterialTheme.colorScheme.onSurface, // Changed from conditional onSurfaceVariant to always onSurface (dark) for better readability
                                                        textDecoration = if (bought) TextDecoration.LineThrough else null
                                                    )
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        // Quantity - Convert to imperial if needed
                                                        Row(
                                                            modifier = Modifier.clickable { startEditing(category, itemIndex, "quantity") },
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            // Convert to imperial ONLY if blueprint was generated in metric
                                                            val blueprintUseImperial = weeklyBlueprint?.get("useImperial") as? Boolean
                                                            val displayQuantity = if (useImperial == true && blueprintUseImperial != true) {
                                                                // Blueprint is in metric, user wants imperial - convert
                                                                convertQuantityToImperial(quantity)
                                                            } else {
                                                                // Blueprint already in correct units - use as-is
                                                                quantity
                                                            }
                                                            Text(
                                                                text = displayQuantity,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = if (hasMetricUnits) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability
                                                            )
                                                            if (hasMetricUnits) {
                                                                Text(
                                                                    text = "⚠",
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.error,
                                                                    modifier = Modifier.padding(start = 4.dp)
                                                                )
                                                            }
                                                            Icon(
                                                                imageVector = Icons.Filled.Edit,
                                                                contentDescription = "Edit quantity",
                                                                tint = MaterialTheme.colorScheme.onSurface, // Changed from onSurfaceVariant to onSurface (dark) for better readability
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                        
                                                        // Note
                                                        IconButton(
                                                            onClick = { startEditing(category, index, "note") },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = if (note != null) Icons.Filled.Note else Icons.Filled.AddCircleOutline,
                                                                contentDescription = if (note != null) "Edit note" else "Add note",
                                                                tint = if (note != null) Color(0xFF6B46C1) else MaterialTheme.colorScheme.onSurface, // Changed from onSurfaceVariant to onSurface (dark) for better readability
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                    
                                                    // Note text
                                                    if (note != null) {
                                                        Text(
                                                            text = note,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurface, // Changed from onSurfaceVariant to onSurface (dark) for better readability
                                                            fontStyle = FontStyle.Italic,
                                                            modifier = Modifier.padding(start = 40.dp, top = 4.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            
                                            if (itemIndex < categoryItems.size - 1) {
                                                HorizontalDivider(
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                                    modifier = Modifier.padding(horizontal = 40.dp)
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
        
        // Edit Dialog
        editingItem?.let { (category, index, field) ->
            AlertDialog(
                onDismissRequest = { editingItem = null },
                title = {
                    Text(
                        text = "Edit ${if (field == "quantity") "Quantity" else "Note"}",
                        color = Color(0xFF1F2937)
                    )
                },
                text = {
                    OutlinedTextField(
                        value = editValue,
                        onValueChange = { editValue = it },
                        placeholder = {
                            Text(
                                text = if (field == "quantity") "Enter quantity (e.g., 2 lbs, 1 pack)" else "Add a note...",
                                color = Color.Gray
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1F2937),
                            unfocusedTextColor = Color(0xFF1F2937)
                        ),
                        maxLines = if (field == "note") 5 else 1
                    )
                },
                confirmButton = {
                    TextButton(onClick = { saveEdit() }) {
                        Text("Save", color = Color(0xFF667eea))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingItem = null }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color.White
            )
        }
        
        // Regenerate Dialog (when mealsPerDay changes)
        if (showRegenerateDialog) {
            AlertDialog(
                onDismissRequest = { showRegenerateDialog = false },
                title = {
                    Text(
                        text = "Meals per day changed",
                        color = Color(0xFF1F2937)
                    )
                },
                text = {
                    Text(
                        text = "Your meals per day setting has changed from $lastMealsPerDay to $userMealsPerDay. Would you like to refresh your weekly blueprint?",
                        color = Color(0xFF1F2937)
                    )
                },
                confirmButton = {
                    TextButton(onClick = { handleRegenerate() }) {
                        Text("Refresh", color = Color(0xFF667eea))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRegenerateDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color.White
            )
        }
        
        // Recipe Share Dialog
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
                onShare = {
                    if (selectedFriends.isNotEmpty()) {
                        performShareRecipe()
                    }
                },
                onPostToForum = {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val result = repository.postRecipeToForum(recipeToShare!!)
                            result.fold(
                                onSuccess = { threadId ->
                                    withContext(Dispatchers.Main) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Recipe posted to forum!",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                        showShareDialog = false
                                        recipeToShare = null
                                    }
                                },
                                onFailure = { error ->
                                    withContext(Dispatchers.Main) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Failed to post recipe: ${error.message}",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Failed to post recipe: ${e.message}",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                },
                onDismiss = {
                    showShareDialog = false
                    recipeToShare = null
                    selectedFriends = emptySet()
                }
            )
        }
        
        // Upgrade dialog (function level, outside Box)
        if (showUpgradeDialog) {
            UpgradePromptDialog(
                onDismiss = { showUpgradeDialog = false },
                onUpgrade = {
                    showUpgradeDialog = false
                    onNavigateToSubscription()
                },
                featureName = "AI-Generated Weekly Blueprint",
                remainingCalls = null // Pro-only feature
            )
        }
    } // Close Scaffold content lambda (also closes Scaffold)
    } // Close outer Box
} // Close WeeklyBlueprintScreen

/**
 * Scale ingredient quantity by a factor
 * Handles formats like "1.5 lbs chicken", "2 cups milk", "1 tbsp oil"
 */
fun scaleIngredientQuantity(ingredient: String, scaleFactor: Double): String {
    // Pattern to match numbers at the start (including decimals)
    val numberPattern = Regex("""^(\d+\.?\d*)\s+""")
    val match = numberPattern.find(ingredient)
    
    if (match != null) {
        val originalQuantity = match.groupValues[1].toDoubleOrNull()
        if (originalQuantity != null) {
            val scaledQuantity = originalQuantity * scaleFactor
            // Format to 1 decimal place if needed, otherwise whole number
            val formattedQuantity = if (scaledQuantity % 1.0 == 0.0) {
                scaledQuantity.toInt().toString()
            } else {
                String.format("%.1f", scaledQuantity)
            }
            return ingredient.replaceFirst(numberPattern, "$formattedQuantity ")
        }
    }
    // If no number found, return as-is
    return ingredient
}

/**
 * Scale quantity string - handles "2.0 lbs", "1.5 cups", etc.
 * Works for quantities anywhere in the string, not just at the start
 */
fun scaleQuantityString(quantity: String, scaleFactor: Double): String {
    if (quantity.isBlank()) return quantity
    
    // Pattern to match number followed by unit (e.g., "2.0 lbs", "1.5 cups")
    val quantityPattern = Regex("""(\d+\.?\d*)\s*(lbs?|oz|ounces?|cups?|fl\s*oz|tbsp|tsp|quarts?|pints?|g|kg|ml|L|item|items)\b""")
    val match = quantityPattern.find(quantity)
    
    if (match != null) {
        val numStr = match.groupValues[1]
        val unit = match.groupValues[2]
        val num = numStr.toDoubleOrNull()
        
        if (num != null) {
            val scaledNum = num * scaleFactor
            val formattedNum = if (scaledNum % 1.0 == 0.0) {
                scaledNum.toInt().toString()
            } else {
                String.format("%.1f", scaledNum)
            }
            return quantity.replaceFirst(quantityPattern, "$formattedNum $unit")
        }
    }
    
    // Fallback: try to match just a number at the start
    val simpleNumberPattern = Regex("""^(\d+\.?\d*)""")
    val simpleMatch = simpleNumberPattern.find(quantity)
    if (simpleMatch != null) {
        val numStr = simpleMatch.groupValues[1]
        val num = numStr.toDoubleOrNull()
        if (num != null) {
            val scaledNum = num * scaleFactor
            val formattedNum = if (scaledNum % 1.0 == 0.0) {
                scaledNum.toInt().toString()
            } else {
                String.format("%.1f", scaledNum)
            }
            return quantity.replaceFirst(simpleNumberPattern, formattedNum)
        }
    }
    
    // If no number found, return as-is
    return quantity
}

/**
 * Convert metric units to imperial in a quantity string
 * Handles: g -> oz, kg -> lbs, ml -> fl oz/cups, L -> cups/quarts
 */
fun convertQuantityToImperial(quantity: String): String {
    if (quantity.isBlank()) return quantity
    
    var result = quantity
    
    // Multiple passes to catch all variations
    for (pass in 1..3) {
        val beforePass = result
        
        // kg -> lbs (with space)
        result = Regex("""(\d+\.?\d*)\s+(kg|kilogram|kilograms|kilogramme|kilogrammes)\b""", RegexOption.IGNORE_CASE).replace(result) { match ->
            val num = match.groupValues[1].toDoubleOrNull() ?: return@replace match.value
            val lbs = num * 2.20462
            "${String.format("%.1f", lbs)} lbs"
        }
        
        // kg -> lbs (without space, like "1kg", "2.5kg")
        result = Regex("""(\d+\.?\d*)(kg)\b(?!\s*(oz|fl\s*oz|lbs?|cups?|tbsp|tsp|quarts?|pints?|inches?|ft|feet))""", RegexOption.IGNORE_CASE).replace(result) { match ->
            val num = match.groupValues[1].toDoubleOrNull() ?: return@replace match.value
            val lbs = num * 2.20462
            "${String.format("%.1f", lbs)} lbs"
        }
        
        // g -> oz (with space, but not if part of "fl oz" or already imperial)
        result = Regex("""(\d+\.?\d*)\s+(g|gram|grams|gramme|grammes)\b(?!\s*(oz|fl\s*oz|lbs?|cups?|tbsp|tsp|quarts?|pints?|inches?|ft|feet|of|protein|carbs|fat))""", RegexOption.IGNORE_CASE).replace(result) { match ->
            val num = match.groupValues[1].toDoubleOrNull() ?: return@replace match.value
            val oz = num * 0.035274
            if (oz >= 16) {
                val lbs = (oz / 16).toInt()
                val remainingOz = (oz % 16).roundToInt()
                if (remainingOz > 0) "$lbs lbs $remainingOz oz" else "$lbs lbs"
            } else {
                "${String.format("%.1f", oz)} oz"
            }
        }
        
        // g -> oz (without space, like "500g", "250g")
        result = Regex("""(\d+\.?\d*)(g|gram|grams)\b(?!\s*(oz|fl\s*oz|lbs?|cups?|tbsp|tsp|quarts?|pints?|inches?|ft|feet|of|protein|carbs|fat))""", RegexOption.IGNORE_CASE).replace(result) { match ->
            val num = match.groupValues[1].toDoubleOrNull() ?: return@replace match.value
            val oz = num * 0.035274
            if (oz >= 16) {
                val lbs = (oz / 16).toInt()
                val remainingOz = (oz % 16).roundToInt()
                if (remainingOz > 0) "$lbs lbs $remainingOz oz" else "$lbs lbs"
            } else {
                "${String.format("%.1f", oz)} oz"
            }
        }
        
        // L/liters -> cups/fl oz (with space)
        result = Regex("""(\d+\.?\d*)\s+(L|l|liter|liters|litre|litres)\b(?!\s*(fl\s*oz|cups?|tbsp|tsp|quarts?|pints?|inches?|ft|feet|of|protein|carbs|fat))""", RegexOption.IGNORE_CASE).replace(result) { match ->
            val num = match.groupValues[1].toDoubleOrNull() ?: return@replace match.value
            val flOz = num * 33.814
            if (flOz >= 32) {
                val quarts = (flOz / 32).toInt()
                val remainingFlOz = (flOz % 32).roundToInt()
                if (remainingFlOz > 0) "$quarts qt $remainingFlOz fl oz" else "$quarts qt"
            } else {
                val cups = (flOz / 8).toInt()
                val remainingFlOz = (flOz % 8).roundToInt()
                if (remainingFlOz > 0) "$cups cups $remainingFlOz fl oz" else "$cups cups"
            }
        }
        
        // L/liters -> cups/fl oz (without space, like "1L", "2.5L")
        result = Regex("""(\d+\.?\d*)(L|l)\b(?!\s*(fl\s*oz|cups?|tbsp|tsp|quarts?|pints?|inches?|ft|feet|of|protein|carbs|fat))""", RegexOption.IGNORE_CASE).replace(result) { match ->
            val num = match.groupValues[1].toDoubleOrNull() ?: return@replace match.value
            val flOz = num * 33.814
            if (flOz >= 32) {
                val quarts = (flOz / 32).toInt()
                val remainingFlOz = (flOz % 32).roundToInt()
                if (remainingFlOz > 0) "$quarts qt $remainingFlOz fl oz" else "$quarts qt"
            } else {
                val cups = (flOz / 8).toInt()
                val remainingFlOz = (flOz % 8).roundToInt()
                if (remainingFlOz > 0) "$cups cups $remainingFlOz fl oz" else "$cups cups"
            }
        }
        
        // ml -> fl oz/cups/tsp/tbsp (with space)
        result = Regex("""(\d+\.?\d*)\s+(ml|mL|milliliter|milliliters|millilitre|millilitres)\b""", RegexOption.IGNORE_CASE).replace(result) { match ->
            val num = match.groupValues[1].toDoubleOrNull() ?: return@replace match.value
            val flOz = num * 0.033814
            if (flOz >= 32) {
                val quarts = (flOz / 32).toInt()
                val remainingFlOz = (flOz % 32).roundToInt()
                if (remainingFlOz > 0) "$quarts qt $remainingFlOz fl oz" else "$quarts qt"
            } else if (flOz >= 8) {
                val cups = (flOz / 8).toInt()
                val remainingFlOz = (flOz % 8).roundToInt()
                if (remainingFlOz > 0) "$cups cups $remainingFlOz fl oz" else "$cups cups"
            } else if (flOz >= 0.5) {
                "${String.format("%.1f", flOz)} fl oz"
            } else {
                val tsp = num * 0.202884
                if (tsp >= 3) {
                    val tbsp = (tsp / 3).toInt()
                    val remainingTsp = (tsp % 3).roundToInt()
                    if (remainingTsp > 0) "$tbsp tbsp $remainingTsp tsp" else "$tbsp tbsp"
                } else {
                    "${String.format("%.1f", tsp)} tsp"
                }
            }
        }
        
        // ml -> fl oz/cups/tsp/tbsp (without space, like "500ml", "250ml")
        result = Regex("""(\d+\.?\d*)(ml|mL|milliliter|milliliters)\b""", RegexOption.IGNORE_CASE).replace(result) { match ->
            val num = match.groupValues[1].toDoubleOrNull() ?: return@replace match.value
            val flOz = num * 0.033814
            if (flOz >= 32) {
                val quarts = (flOz / 32).toInt()
                val remainingFlOz = (flOz % 32).roundToInt()
                if (remainingFlOz > 0) "$quarts qt $remainingFlOz fl oz" else "$quarts qt"
            } else if (flOz >= 8) {
                val cups = (flOz / 8).toInt()
                val remainingFlOz = (flOz % 8).roundToInt()
                if (remainingFlOz > 0) "$cups cups $remainingFlOz fl oz" else "$cups cups"
            } else if (flOz >= 0.5) {
                "${String.format("%.1f", flOz)} fl oz"
            } else {
                val tsp = num * 0.202884
                if (tsp >= 3) {
                    val tbsp = (tsp / 3).toInt()
                    val remainingTsp = (tsp % 3).roundToInt()
                    if (remainingTsp > 0) "$tbsp tbsp $remainingTsp tsp" else "$tbsp tbsp"
                } else {
                    "${String.format("%.1f", tsp)} tsp"
                }
            }
        }
        
        // cm -> inches (with space)
        result = Regex("""(\d+\.?\d*)\s+cm\b(?!\s*(in|inches?|ft|feet|'|"))""", RegexOption.IGNORE_CASE).replace(result) { match ->
            val num = match.groupValues[1].toDoubleOrNull() ?: return@replace match.value
            val inches = num * 0.393701
            if (inches >= 12) {
                val feet = (inches / 12).toInt()
                val remainingInches = (inches % 12).roundToInt()
                if (remainingInches > 0) "$feet ft $remainingInches in" else "$feet ft"
            } else {
                "${String.format("%.1f", inches)} in"
            }
        }
        
        // cm -> inches (without space, like "30cm", "50cm")
        result = Regex("""(\d+\.?\d*)(cm|centimeter|centimeters)\b(?!\s*(in|inches?|ft|feet|'|"))""", RegexOption.IGNORE_CASE).replace(result) { match ->
            val num = match.groupValues[1].toDoubleOrNull() ?: return@replace match.value
            val inches = num * 0.393701
            if (inches >= 12) {
                val feet = (inches / 12).toInt()
                val remainingInches = (inches % 12).roundToInt()
                if (remainingInches > 0) "$feet ft $remainingInches in" else "$feet ft"
            } else {
                "${String.format("%.1f", inches)} in"
            }
        }
        
        // Stop if no changes were made
        if (beforePass == result) break
    }
    
    // Final safety check - catch any remaining metric units that might have been missed
    // This is a catch-all for edge cases
    val finalCheck = result
    if (finalCheck.contains(Regex("""\d+\.?\d*\s*(kg|g|ml|mL|L|l|cm)\b""", RegexOption.IGNORE_CASE)) ||
        finalCheck.contains(Regex("""\d+\.?\d*(kg|g|ml|mL|L|l|cm)\b""", RegexOption.IGNORE_CASE))) {
        // Run one more aggressive pass
        result = finalCheck
            .replace(Regex("""(\d+\.?\d*)\s*kg\b""", RegexOption.IGNORE_CASE)) { match ->
                val num = match.groupValues[1].toDoubleOrNull() ?: return@replace match.value
                "${String.format("%.1f", num * 2.20462)} lbs"
            }
            .replace(Regex("""(\d+\.?\d*)g\b(?!\s*(oz|fl|protein|carbs|fat))""", RegexOption.IGNORE_CASE)) { match ->
                val num = match.groupValues[1].toDoubleOrNull() ?: return@replace match.value
                val oz = num * 0.035274
                if (oz >= 16) {
                    val lbs = (oz / 16).toInt()
                    val remainingOz = (oz % 16).roundToInt()
                    if (remainingOz > 0) "$lbs lbs $remainingOz oz" else "$lbs lbs"
                } else {
                    "${String.format("%.1f", oz)} oz"
                }
            }
            .replace(Regex("""(\d+\.?\d*)\s*ml\b""", RegexOption.IGNORE_CASE)) { match ->
                val num = match.groupValues[1].toDoubleOrNull() ?: return@replace match.value
                val flOz = num * 0.033814
                if (flOz >= 8) {
                    val cups = (flOz / 8).toInt()
                    val remainingFlOz = (flOz % 8).roundToInt()
                    if (remainingFlOz > 0) "$cups cups $remainingFlOz fl oz" else "$cups cups"
                } else {
                    "${String.format("%.1f", flOz)} fl oz"
                }
            }
            .replace(Regex("""(\d+\.?\d*)(ml|mL)\b""", RegexOption.IGNORE_CASE)) { match ->
                val num = match.groupValues[1].toDoubleOrNull() ?: return@replace match.value
                val flOz = num * 0.033814
                if (flOz >= 8) {
                    val cups = (flOz / 8).toInt()
                    val remainingFlOz = (flOz % 8).roundToInt()
                    if (remainingFlOz > 0) "$cups cups $remainingFlOz fl oz" else "$cups cups"
                } else {
                    "${String.format("%.1f", flOz)} fl oz"
                }
            }
            .replace(Regex("""(\d+\.?\d*)\s*L\b(?!\s*(fl|oz|cups))""", RegexOption.IGNORE_CASE)) { match ->
                val num = match.groupValues[1].toDoubleOrNull() ?: return@replace match.value
                val flOz = num * 33.814
                if (flOz >= 32) {
                    val quarts = (flOz / 32).toInt()
                    val remainingFlOz = (flOz % 32).roundToInt()
                    if (remainingFlOz > 0) "$quarts qt $remainingFlOz fl oz" else "$quarts qt"
                } else {
                    val cups = (flOz / 8).toInt()
                    val remainingFlOz = (flOz % 8).roundToInt()
                    if (remainingFlOz > 0) "$cups cups $remainingFlOz fl oz" else "$cups cups"
                }
            }
            .replace(Regex("""(\d+\.?\d*)L\b(?!\s*(fl|oz|cups))""", RegexOption.IGNORE_CASE)) { match ->
                val num = match.groupValues[1].toDoubleOrNull() ?: return@replace match.value
                val flOz = num * 33.814
                if (flOz >= 32) {
                    val quarts = (flOz / 32).toInt()
                    val remainingFlOz = (flOz % 32).roundToInt()
                    if (remainingFlOz > 0) "$quarts qt $remainingFlOz fl oz" else "$quarts qt"
                } else {
                    val cups = (flOz / 8).toInt()
                    val remainingFlOz = (flOz % 8).roundToInt()
                    if (remainingFlOz > 0) "$cups cups $remainingFlOz fl oz" else "$cups cups"
                }
            }
    }
    
    return result
}

@Composable
fun MealCard(
    meal: Map<*, *>?, 
    mealType: String, 
    selectedServings: Int = 1, 
    originalServings: Int = 1, 
    useImperial: Boolean? = null,
    weeklyBlueprint: Map<String, Any>? = null,
    onLogMeal: ((Map<*, *>, Double) -> Unit)? = null,
    onSaveRecipe: ((Recipe) -> Unit)? = null,
    onShareRecipe: ((Recipe) -> Unit)? = null,
    mealDataToRecipe: ((Map<*, *>, String, Int, Int) -> Recipe)? = null,
    userId: String = ""
) {
    if (meal == null) return
    
    val name = meal["name"] as? String ?: ""
    val calories = meal["calories"] as? Number ?: 0
    val protein = meal["protein"] as? Number ?: 0
    val carbs = meal["carbs"] as? Number ?: 0
    val fat = meal["fat"] as? Number ?: 0
    
    // IMPORTANT: Blueprint macros are now calculated from ingredients using analyzeRecipe system
    // They are stored for FULL RECIPE (all originalServings), not per-serving
    // Scale to selectedServings if different
    val scaleFactor = if (originalServings > 0) {
        selectedServings.toDouble() / originalServings
    } else {
        1.0
    }
    val scaledCalories = (calories.toDouble() * scaleFactor).toInt()
    val scaledProtein = (protein.toDouble() * scaleFactor).toInt()
    val scaledCarbs = (carbs.toDouble() * scaleFactor).toInt()
    val scaledFat = (fat.toDouble() * scaleFactor).toInt()
    val ingredients = meal["ingredients"] as? List<*> ?: emptyList<Any>()
    val steps = meal["steps"] as? List<*> ?: emptyList<Any>()
    
    var isExpanded by remember { mutableStateOf(false) }
    // Remove per-recipe serving selector - use top-level selectedServings instead
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$mealType: $name",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${scaledCalories} cal • ${scaledProtein}g P • ${scaledCarbs}g C • ${scaledFat}g F${if (selectedServings != originalServings) " (for $selectedServings)" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability on light card
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (ingredients.isNotEmpty()) {
                        Text(
                            text = "Ingredients:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        ingredients.forEach { ingredient ->
                            val ingredientStr = ingredient.toString()
                            var scaledIngredient = if (selectedServings != originalServings) {
                                scaleIngredientQuantity(ingredientStr, scaleFactor)
                            } else {
                                ingredientStr
                            }
                            // Convert to imperial ONLY if blueprint was generated in metric
                            // If blueprint.useImperial == true, OpenAI already returned imperial - don't convert again
                            val blueprintUseImperial = weeklyBlueprint?.get("useImperial") as? Boolean
                            if (useImperial == true && blueprintUseImperial != true) {
                                // Blueprint is in metric, user wants imperial - convert
                                scaledIngredient = convertQuantityToImperial(scaledIngredient)
                            }
                            // Otherwise, blueprint already in correct units - use as-is
                            Text(
                                text = "• $scaledIngredient",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    if (steps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Instructions:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        steps.forEachIndexed { index, step ->
                            Text(
                                text = "${index + 1}. ${step}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // Add "Log Meal" button if callback provided
                    if (onLogMeal != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { 
                                // Log 1 serving - the recipe is already scaled to selectedServings at the top level
                                onLogMeal(meal, 1.0) 
                            },
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
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Log Meal")
                        }
                        
                        // Save and Share Recipe buttons
                        if (onSaveRecipe != null || onShareRecipe != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (onSaveRecipe != null && mealDataToRecipe != null) {
                                    OutlinedButton(
                                        onClick = {
                                            val recipe = mealDataToRecipe(meal, userId, selectedServings, originalServings)
                                            onSaveRecipe(recipe)
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Bookmark,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Save Recipe")
                                    }
                                }
                                if (onShareRecipe != null && mealDataToRecipe != null) {
                                    OutlinedButton(
                                        onClick = {
                                            val recipe = mealDataToRecipe(meal, userId, selectedServings, originalServings)
                                            onShareRecipe(recipe)
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Share,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Share")
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

@Composable
fun RecipeShareDialog(
    recipe: Recipe,
    friends: List<com.coachie.app.data.model.PublicUserProfile>,
    selectedFriends: Set<String>,
    onFriendToggle: (String) -> Unit,
    onShare: () -> Unit,
    onPostToForum: () -> Unit,
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Post to Forum button
                Button(
                    onClick = onPostToForum,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Forum,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Post to Recipe Forum")
                }
                
                Divider()
                
                Text(
                    "Or share with friends:",
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
                Text("Share with Friends")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
