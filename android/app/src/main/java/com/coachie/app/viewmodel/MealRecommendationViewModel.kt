package com.coachie.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.HealthLog
import com.coachie.app.data.model.IngredientCategory
import com.coachie.app.data.ai.GeminiClient
import com.coachie.app.data.model.IngredientCategoryGroup
import com.coachie.app.data.model.IngredientOption
import com.coachie.app.data.model.MacroSnapshot
import com.coachie.app.data.model.MacroTargetsSnapshot
import com.coachie.app.data.model.MealRecommendation
import com.coachie.app.data.model.MealRecommendationRequest
import com.coachie.app.data.model.SavedMeal
import com.coachie.app.data.model.UserProfile
import com.coachie.app.data.model.Recipe
import com.coachie.app.data.model.RecipeIngredient
import com.coachie.app.domain.MacroTargets
import com.coachie.app.domain.MacroTargetsCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class MealRecommendationUiState(
    val isLoading: Boolean = true,
    val ingredientGroups: List<IngredientCategoryGroup> = emptyList(),
    val selectedIngredients: List<String> = emptyList(),
    val macroTargets: MacroTargets? = null,
    val currentMacros: MacroSnapshot = MacroSnapshot(0, 0, 0, 0, 0, 0),
    val remainingMacros: MacroSnapshot = MacroSnapshot(0, 0, 0, 0, 0, 0),
    val profile: UserProfile? = null,
    val requestPreview: MealRecommendationRequest? = null,
    val recommendation: MealRecommendation? = null,
    val isRequestingRecommendation: Boolean = false,
    val isLogging: Boolean = false,
    val isSavingQuickSelect: Boolean = false,
    val logSuccessMessage: String? = null,
    val errorMessage: String? = null,
    val useImperial: Boolean = true,
    val selectedServings: Int = 1,
    val selectedCookingMethod: String? = null, // Selected cooking method for this meal
    val selectedMealType: String? = null // Selected meal type (breakfast, brunch, lunch, dinner, dessert)
)

class MealRecommendationViewModel(
    private val repository: FirebaseRepository,
    private val userId: String,
    private val context: Context? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(MealRecommendationUiState())
    val uiState: StateFlow<MealRecommendationUiState> = _uiState.asStateFlow()

    private val selectedIngredients = mutableSetOf<String>()
    private val customIngredients = mutableSetOf<String>()

    init {
        loadIngredientOptions()
        loadUserContext()
    }

    private fun loadIngredientOptions() {
        val groups = listOf(
            // PROTEINS
            IngredientCategoryGroup(
                category = IngredientCategory.PROTEIN,
                title = "Proteins",
                options = listOf(
                    option("Chicken Breast", IngredientCategory.PROTEIN),
                    option("Chicken Thighs", IngredientCategory.PROTEIN),
                    option("Ground Turkey", IngredientCategory.PROTEIN),
                    option("Ground Chicken", IngredientCategory.PROTEIN),
                    option("Lean Beef", IngredientCategory.PROTEIN),
                    option("Ground Beef", IngredientCategory.PROTEIN),
                    option("Ground Pork", IngredientCategory.PROTEIN),
                    option("Steak", IngredientCategory.PROTEIN),
                    option("Pork Tenderloin", IngredientCategory.PROTEIN),
                    option("Bacon", IngredientCategory.PROTEIN),
                    option("Ham", IngredientCategory.PROTEIN),
                    option("Salmon", IngredientCategory.PROTEIN),
                    option("Tuna", IngredientCategory.PROTEIN),
                    option("Cod", IngredientCategory.PROTEIN),
                    option("Pollock", IngredientCategory.PROTEIN),
                    option("Tilapia", IngredientCategory.PROTEIN),
                    option("Halibut", IngredientCategory.PROTEIN),
                    option("Mackerel", IngredientCategory.PROTEIN),
                    option("Sardines", IngredientCategory.PROTEIN),
                    option("Trout", IngredientCategory.PROTEIN),
                    option("Sea Bass", IngredientCategory.PROTEIN),
                    option("Mahi Mahi", IngredientCategory.PROTEIN),
                    option("Snapper", IngredientCategory.PROTEIN),
                    option("Swordfish", IngredientCategory.PROTEIN),
                    option("Haddock", IngredientCategory.PROTEIN),
                    option("Anchovies", IngredientCategory.PROTEIN),
                    option("Tuna Steak", IngredientCategory.PROTEIN),
                    option("Arctic Char", IngredientCategory.PROTEIN),
                    option("Perch", IngredientCategory.PROTEIN),
                    option("Grouper", IngredientCategory.PROTEIN),
                    option("Shrimp", IngredientCategory.PROTEIN),
                    option("Scallops", IngredientCategory.PROTEIN),
                    option("Crab", IngredientCategory.PROTEIN),
                    option("Lobster", IngredientCategory.PROTEIN),
                    option("Mussels", IngredientCategory.PROTEIN),
                    option("Oysters", IngredientCategory.PROTEIN),
                    option("Clams", IngredientCategory.PROTEIN),
                    option("Octopus", IngredientCategory.PROTEIN),
                    option("Squid", IngredientCategory.PROTEIN),
                    option("Flounder", IngredientCategory.PROTEIN),
                    option("Sole", IngredientCategory.PROTEIN),
                    option("Monkfish", IngredientCategory.PROTEIN),
                    option("Branzino", IngredientCategory.PROTEIN),
                    option("Barramundi", IngredientCategory.PROTEIN),
                    option("Rainbow Trout", IngredientCategory.PROTEIN),
                    option("Black Cod", IngredientCategory.PROTEIN),
                    option("Yellowtail", IngredientCategory.PROTEIN),
                    option("Crawfish", IngredientCategory.PROTEIN),
                    option("Langoustine", IngredientCategory.PROTEIN),
                    option("Red Snapper", IngredientCategory.PROTEIN),
                    option("Tofu", IngredientCategory.PROTEIN),
                    option("Tempeh", IngredientCategory.PROTEIN),
                    option("Eggs", IngredientCategory.PROTEIN),
                    option("Egg Whites", IngredientCategory.PROTEIN),
                    option("Black Beans", IngredientCategory.PROTEIN),
                    option("Kidney Beans", IngredientCategory.PROTEIN),
                    option("Chickpeas", IngredientCategory.PROTEIN),
                    option("Lentils", IngredientCategory.PROTEIN),
                    option("Edamame", IngredientCategory.PROTEIN),
                    option("Turkey Breast", IngredientCategory.PROTEIN),
                    option("Duck Breast", IngredientCategory.PROTEIN),
                    option("Lamb Chop", IngredientCategory.PROTEIN),
                    option("Ground Lamb", IngredientCategory.PROTEIN),
                    option("Pork Shoulder", IngredientCategory.PROTEIN),
                    option("Beef Brisket", IngredientCategory.PROTEIN),
                    option("Beef Short Ribs", IngredientCategory.PROTEIN),
                    option("Lamb Leg", IngredientCategory.PROTEIN),
                    option("Ground Bison", IngredientCategory.PROTEIN),
                    option("Elk", IngredientCategory.PROTEIN),
                    option("Rabbit", IngredientCategory.PROTEIN),
                    option("Oxtail", IngredientCategory.PROTEIN)
                )
            ),
            // FRUITS
            IngredientCategoryGroup(
                category = IngredientCategory.FRUIT,
                title = "Fruits",
                options = listOf(
                    option("Banana", IngredientCategory.FRUIT),
                    option("Apple", IngredientCategory.FRUIT),
                    option("Blueberries", IngredientCategory.FRUIT),
                    option("Strawberries", IngredientCategory.FRUIT),
                    option("Raspberries", IngredientCategory.FRUIT),
                    option("Blackberries", IngredientCategory.FRUIT),
                    option("Avocado", IngredientCategory.FRUIT),
                    option("Pineapple", IngredientCategory.FRUIT),
                    option("Mango", IngredientCategory.FRUIT),
                    option("Orange", IngredientCategory.FRUIT),
                    option("Grapefruit", IngredientCategory.FRUIT),
                    option("Grapes", IngredientCategory.FRUIT),
                    option("Peach", IngredientCategory.FRUIT),
                    option("Pear", IngredientCategory.FRUIT),
                    option("Cherries", IngredientCategory.FRUIT),
                    option("Kiwi", IngredientCategory.FRUIT),
                    option("Watermelon", IngredientCategory.FRUIT),
                    option("Cantaloupe", IngredientCategory.FRUIT),
                    option("Lemon", IngredientCategory.FRUIT),
                    option("Lime", IngredientCategory.FRUIT)
                )
            ),
            // VEGETABLES
            IngredientCategoryGroup(
                category = IngredientCategory.VEGETABLE,
                title = "Vegetables",
                options = listOf(
                    option("Spinach", IngredientCategory.VEGETABLE),
                    option("Kale", IngredientCategory.VEGETABLE),
                    option("Arugula", IngredientCategory.VEGETABLE),
                    option("Lettuce", IngredientCategory.VEGETABLE),
                    option("Broccoli", IngredientCategory.VEGETABLE),
                    option("Brussels Sprouts", IngredientCategory.VEGETABLE),
                    option("Bell Peppers", IngredientCategory.VEGETABLE),
                    option("Onion", IngredientCategory.VEGETABLE),
                    option("Red Onion", IngredientCategory.VEGETABLE),
                    option("Tomato", IngredientCategory.VEGETABLE),
                    option("Cherry Tomatoes", IngredientCategory.VEGETABLE),
                    option("Carrots", IngredientCategory.VEGETABLE),
                    option("Cauliflower", IngredientCategory.VEGETABLE),
                    option("Zucchini", IngredientCategory.VEGETABLE),
                    option("Yellow Squash", IngredientCategory.VEGETABLE),
                    option("Mushrooms", IngredientCategory.VEGETABLE),
                    option("Asparagus", IngredientCategory.VEGETABLE),
                    option("Green Beans", IngredientCategory.VEGETABLE),
                    option("Snap Peas", IngredientCategory.VEGETABLE),
                    option("Cucumber", IngredientCategory.VEGETABLE),
                    option("Celery", IngredientCategory.VEGETABLE),
                    option("Cabbage", IngredientCategory.VEGETABLE),
                    option("Eggplant", IngredientCategory.VEGETABLE),
                    option("Okra", IngredientCategory.VEGETABLE),
                    option("Radish", IngredientCategory.VEGETABLE),
                    option("Beets", IngredientCategory.VEGETABLE),
                    option("Corn", IngredientCategory.VEGETABLE),
                    option("Peas", IngredientCategory.VEGETABLE)
                )
            ),
            // NUTS AND SEEDS
            IngredientCategoryGroup(
                category = IngredientCategory.HEALTHY_FAT,
                title = "Nuts and Seeds",
                options = listOf(
                    option("Almonds", IngredientCategory.HEALTHY_FAT),
                    option("Walnuts", IngredientCategory.HEALTHY_FAT),
                    option("Cashews", IngredientCategory.HEALTHY_FAT),
                    option("Pistachios", IngredientCategory.HEALTHY_FAT),
                    option("Pecans", IngredientCategory.HEALTHY_FAT),
                    option("Macadamia Nuts", IngredientCategory.HEALTHY_FAT),
                    option("Peanuts", IngredientCategory.HEALTHY_FAT),
                    option("Hazelnuts", IngredientCategory.HEALTHY_FAT),
                    option("Brazil Nuts", IngredientCategory.HEALTHY_FAT),
                    option("Chia Seeds", IngredientCategory.HEALTHY_FAT),
                    option("Flaxseed", IngredientCategory.HEALTHY_FAT),
                    option("Hemp Seeds", IngredientCategory.HEALTHY_FAT),
                    option("Pumpkin Seeds", IngredientCategory.HEALTHY_FAT),
                    option("Sunflower Seeds", IngredientCategory.HEALTHY_FAT),
                    option("Sesame Seeds", IngredientCategory.HEALTHY_FAT),
                    option("Pine Nuts", IngredientCategory.HEALTHY_FAT)
                )
            ),
            // DAIRY
            IngredientCategoryGroup(
                category = IngredientCategory.PANTRY,
                title = "Dairy",
                options = listOf(
                    option("Greek Yogurt", IngredientCategory.PANTRY),
                    option("Cottage Cheese", IngredientCategory.PANTRY),
                    option("Milk", IngredientCategory.PANTRY),
                    option("Almond Milk", IngredientCategory.PANTRY),
                    option("Oat Milk", IngredientCategory.PANTRY),
                    option("Soy Milk", IngredientCategory.PANTRY),
                    option("Coconut Milk", IngredientCategory.PANTRY),
                    option("Parmesan Cheese", IngredientCategory.PANTRY),
                    option("Feta Cheese", IngredientCategory.PANTRY),
                    option("Mozzarella Cheese", IngredientCategory.PANTRY),
                    option("Cheddar Cheese", IngredientCategory.PANTRY),
                    option("Cream Cheese", IngredientCategory.PANTRY),
                    option("Sour Cream", IngredientCategory.PANTRY),
                    option("Butter", IngredientCategory.PANTRY),
                    option("Heavy Cream", IngredientCategory.PANTRY),
                    option("Half and Half", IngredientCategory.PANTRY),
                    option("Whipped Cream", IngredientCategory.PANTRY),
                    option("Ricotta Cheese", IngredientCategory.PANTRY),
                    option("Mascarpone", IngredientCategory.PANTRY),
                    option("Goat Cheese", IngredientCategory.PANTRY),
                    option("Blue Cheese", IngredientCategory.PANTRY),
                    option("Ghee", IngredientCategory.PANTRY),
                    option("Buttermilk", IngredientCategory.PANTRY),
                    option("Evaporated Milk", IngredientCategory.PANTRY),
                    option("Sweetened Condensed Milk", IngredientCategory.PANTRY),
                    option("Clotted Cream", IngredientCategory.PANTRY),
                    option("Crème Fraîche", IngredientCategory.PANTRY),
                    option("Swiss Cheese", IngredientCategory.PANTRY),
                    option("Provolone Cheese", IngredientCategory.PANTRY),
                    option("Gouda Cheese", IngredientCategory.PANTRY),
                    option("Brie Cheese", IngredientCategory.PANTRY),
                    option("Camembert Cheese", IngredientCategory.PANTRY)
                )
            ),
            // PANTRY ITEMS
            IngredientCategoryGroup(
                category = IngredientCategory.PANTRY,
                title = "Pantry Items",
                options = listOf(
                    // Grains & Starches
                    option("Brown Rice", IngredientCategory.PANTRY),
                    option("White Rice", IngredientCategory.PANTRY),
                    option("Jasmine Rice", IngredientCategory.PANTRY),
                    option("Quinoa", IngredientCategory.PANTRY),
                    option("Whole Wheat Pasta", IngredientCategory.PANTRY),
                    option("Regular Pasta", IngredientCategory.PANTRY),
                    option("Sweet Potato", IngredientCategory.PANTRY),
                    option("Regular Potato", IngredientCategory.PANTRY),
                    option("Oats", IngredientCategory.PANTRY),
                    option("Steel Cut Oats", IngredientCategory.PANTRY),
                    option("Whole Grain Bread", IngredientCategory.PANTRY),
                    option("Tortillas", IngredientCategory.PANTRY),
                    option("Corn Tortillas", IngredientCategory.PANTRY),
                    option("Barley", IngredientCategory.PANTRY),
                    option("Farro", IngredientCategory.PANTRY),
                    option("Bulgur", IngredientCategory.PANTRY),
                    option("Couscous", IngredientCategory.PANTRY),
                    option("Wild Rice", IngredientCategory.PANTRY),
                    option("Polenta", IngredientCategory.PANTRY),
                    option("Breadcrumbs", IngredientCategory.PANTRY),
                    // Canned & Preserved
                    option("Canned Tomatoes", IngredientCategory.PANTRY),
                    option("Tomato Paste", IngredientCategory.PANTRY),
                    option("Black Beans (Canned)", IngredientCategory.PANTRY),
                    option("Kidney Beans (Canned)", IngredientCategory.PANTRY),
                    option("Chickpeas (Canned)", IngredientCategory.PANTRY),
                    option("Corn (Frozen)", IngredientCategory.PANTRY),
                    option("Peas (Frozen)", IngredientCategory.PANTRY),
                    // Broths & Stocks
                    option("Chicken Broth", IngredientCategory.PANTRY),
                    option("Vegetable Broth", IngredientCategory.PANTRY),
                    option("Beef Broth", IngredientCategory.PANTRY),
                    // Oils & Fats
                    option("Olive Oil", IngredientCategory.PANTRY),
                    option("Coconut Oil", IngredientCategory.PANTRY),
                    option("Avocado Oil", IngredientCategory.PANTRY),
                    option("Sesame Oil", IngredientCategory.PANTRY),
                    option("Peanut Butter", IngredientCategory.PANTRY),
                    option("Almond Butter", IngredientCategory.PANTRY),
                    option("Cashew Butter", IngredientCategory.PANTRY),
                    option("Tahini", IngredientCategory.PANTRY),
                    option("Olives", IngredientCategory.PANTRY),
                    option("Sun Dried Tomatoes", IngredientCategory.PANTRY),
                    option("Hearts of Palm", IngredientCategory.PANTRY),
                    // Sauces & Condiments
                    option("Soy Sauce", IngredientCategory.PANTRY),
                    option("Tamari", IngredientCategory.PANTRY),
                    option("Worcestershire Sauce", IngredientCategory.PANTRY),
                    option("Salsa", IngredientCategory.PANTRY),
                    option("Hot Sauce", IngredientCategory.PANTRY),
                    option("Mayonnaise", IngredientCategory.PANTRY),
                    option("Mustard", IngredientCategory.PANTRY),
                    option("Ketchup", IngredientCategory.PANTRY),
                    option("BBQ Sauce", IngredientCategory.PANTRY),
                    option("Balsamic Vinegar", IngredientCategory.PANTRY),
                    option("Apple Cider Vinegar", IngredientCategory.PANTRY),
                    option("Rice Vinegar", IngredientCategory.PANTRY),
                    // Sweeteners
                    option("Honey", IngredientCategory.PANTRY),
                    option("Maple Syrup", IngredientCategory.PANTRY),
                    // Spices & Herbs
                    option("Garlic", IngredientCategory.PANTRY),
                    option("Ginger", IngredientCategory.PANTRY),
                    option("Cinnamon", IngredientCategory.PANTRY),
                    option("Paprika", IngredientCategory.PANTRY),
                    option("Cumin", IngredientCategory.PANTRY),
                    option("Curry Powder", IngredientCategory.PANTRY),
                    option("Chili Powder", IngredientCategory.PANTRY),
                    option("Oregano", IngredientCategory.PANTRY),
                    option("Basil", IngredientCategory.PANTRY),
                    option("Thyme", IngredientCategory.PANTRY),
                    option("Rosemary", IngredientCategory.PANTRY),
                    option("Turmeric", IngredientCategory.PANTRY),
                    option("Garlic Powder", IngredientCategory.PANTRY),
                    option("Onion Powder", IngredientCategory.PANTRY),
                    option("Coriander", IngredientCategory.PANTRY),
                    option("Cardamom", IngredientCategory.PANTRY),
                    option("Nutmeg", IngredientCategory.PANTRY),
                    option("Allspice", IngredientCategory.PANTRY),
                    option("Cloves", IngredientCategory.PANTRY),
                    option("Cilantro", IngredientCategory.PANTRY),
                    option("Crushed Red Pepper", IngredientCategory.PANTRY),
                    option("Old Bay", IngredientCategory.PANTRY),
                    // Hot Peppers
                    option("Jalapeño", IngredientCategory.PANTRY),
                    option("Serrano Pepper", IngredientCategory.PANTRY),
                    option("Habanero", IngredientCategory.PANTRY),
                    option("Cayenne Pepper", IngredientCategory.PANTRY),
                    option("Bell Pepper", IngredientCategory.PANTRY),
                    option("Thai Chili", IngredientCategory.PANTRY),
                    option("Scotch Bonnet", IngredientCategory.PANTRY),
                    option("Chipotle", IngredientCategory.PANTRY),
                    option("Poblano", IngredientCategory.PANTRY),
                    option("Anaheim Pepper", IngredientCategory.PANTRY),
                    // Baking
                    option("Flour", IngredientCategory.PANTRY),
                    option("Baking Powder", IngredientCategory.PANTRY),
                    option("Baking Soda", IngredientCategory.PANTRY),
                    option("Vanilla Extract", IngredientCategory.PANTRY)
                )
            )
        )

        _uiState.update { it.copy(ingredientGroups = groups) }
    }

    private fun option(name: String, category: IngredientCategory): IngredientOption {
        val id = name.lowercase(Locale.getDefault())
            .replace("[^a-z0-9]+".toRegex(), "_")
            .trim('_')
        return IngredientOption(id = id, name = name, category = category)
    }

    private fun loadUserContext() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, recommendation = null) }
        viewModelScope.launch {
            try {
                val profile = repository.getUserProfile(userId).getOrNull()
                if (profile == null) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Profile not found. Please complete your profile first.") }
                    return@launch
                }

                // Load useImperial preference
                val useImperial = try {
                    val goals = repository.getUserGoals(userId).getOrNull()
                    goals?.get("useImperial") as? Boolean ?: true
                } catch (e: Exception) {
                    true // Default to imperial
                }

                val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                val healthLogs = repository.getHealthLogs(userId, today).getOrNull() ?: emptyList()

                val mealLogs = healthLogs.filterIsInstance<HealthLog.MealLog>()
                val currentMacros = MacroSnapshot(
                    calories = mealLogs.sumOf { it.calories },
                    protein = mealLogs.sumOf { it.protein },
                    carbs = mealLogs.sumOf { it.carbs },
                    fat = mealLogs.sumOf { it.fat },
                    sugar = mealLogs.sumOf { it.sugar },
                    addedSugar = mealLogs.sumOf { it.addedSugar }
                )

                val macroTargets = MacroTargetsCalculator.calculate(profile)
                val remaining = MacroSnapshot(
                    calories = (macroTargets.calorieGoal - currentMacros.calories).coerceAtLeast(0),
                    protein = (macroTargets.proteinGrams - currentMacros.protein).coerceAtLeast(0),
                    carbs = (macroTargets.carbsGrams - currentMacros.carbs).coerceAtLeast(0),
                    fat = (macroTargets.fatGrams - currentMacros.fat).coerceAtLeast(0),
                    sugar = 0, // No target for sugar
                    addedSugar = 0 // No target for added sugar
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        profile = profile,
                        macroTargets = macroTargets,
                        currentMacros = currentMacros,
                        remainingMacros = remaining,
                        useImperial = useImperial
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Unable to load data: ${e.localizedMessage ?: "Unknown error"}") }
            }
        }
    }

    fun toggleIngredient(option: IngredientOption) {
        val normalized = option.name.trim()
        if (selectedIngredients.contains(normalized)) {
            selectedIngredients.remove(normalized)
        } else {
            selectedIngredients.add(normalized)
        }
        updateSelectedIngredients()
    }

    fun addCustomIngredient(input: String) {
        val normalized = input.trim()
        if (normalized.isEmpty()) return
        val formatted = normalized.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        customIngredients.add(formatted)
        selectedIngredients.add(formatted)
        updateSelectedIngredients()
    }

    fun removeIngredient(name: String) {
        selectedIngredients.remove(name)
        customIngredients.remove(name)
        updateSelectedIngredients()
    }

    private fun updateSelectedIngredients() {
        _uiState.update {
            it.copy(
                selectedIngredients = selectedIngredients.sorted(),
                requestPreview = null,
                recommendation = null,
                logSuccessMessage = null,
                errorMessage = null
            )
        }
    }

    fun refreshContext() {
        loadUserContext()
    }

    fun updateServings(servings: Int) {
        _uiState.update { it.copy(selectedServings = servings.coerceIn(1, 20)) }
    }

    fun updateCookingMethod(cookingMethod: String?) {
        _uiState.update { 
            it.copy(
                selectedCookingMethod = cookingMethod,
                requestPreview = null, // Clear preview when method changes
                recommendation = null
            )
        }
    }
    
    fun updateSelectedMealType(mealType: String?) {
        _uiState.update { 
            it.copy(
                selectedMealType = mealType,
                requestPreview = null, // Clear preview when meal type changes
                recommendation = null
            )
        }
    }

    fun prepareRecommendationPreview() {
        val state = _uiState.value
        val profile = state.profile
        val macroTargets = state.macroTargets

        if (profile == null || macroTargets == null) {
            _uiState.update { it.copy(errorMessage = "Profile data not ready yet.") }
            return
        }

        if (state.selectedIngredients.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Select at least one ingredient to generate a recommendation.") }
            return
        }

        val goalTrend = when {
            profile.goalWeight < profile.currentWeight - 0.1 -> "lose_weight"
            profile.goalWeight > profile.currentWeight + 0.1 -> "gain_weight"
            else -> "maintain_weight"
        }

        // Get cycle phase if user has menstrual tracking enabled
        val cyclePhase = if (profile.menstrualCycleEnabled && profile.currentCyclePhase != com.coachie.app.data.model.CyclePhase.UNKNOWN) {
            profile.currentCyclePhase.name
        } else {
            null
        }

        val request = MealRecommendationRequest(
            userId = userId,
            dietaryPreference = profile.dietaryPreferenceEnum.id,
            goalTrend = goalTrend,
            calorieGoal = macroTargets.calorieGoal,
            macroTargets = MacroTargetsSnapshot(
                proteinGrams = macroTargets.proteinGrams,
                carbsGrams = macroTargets.carbsGrams,
                fatGrams = macroTargets.fatGrams,
                calorieGoal = macroTargets.calorieGoal
            ),
            currentMacros = state.currentMacros,
            remainingMacros = state.remainingMacros,
            selectedIngredients = state.selectedIngredients,
            cookingMethod = state.selectedCookingMethod,
            mealType = state.selectedMealType,
            cyclePhase = cyclePhase,
            timestamp = System.currentTimeMillis()
        )

        _uiState.update {
            it.copy(
                requestPreview = request,
                recommendation = null,
                logSuccessMessage = null,
                errorMessage = null
            )
        }
    }

    fun requestRecommendation() {
        // CRITICAL: Prevent multiple simultaneous calls
        if (_uiState.value.isRequestingRecommendation) {
            android.util.Log.w("MealRecommendationViewModel", "⚠️ Recommendation request already in progress, ignoring duplicate call")
            return
        }
        
        val preview = _uiState.value.requestPreview
        if (preview == null) {
            _uiState.update { it.copy(errorMessage = "Generate a preview first to review the context.") }
            return
        }

        _uiState.update { it.copy(isRequestingRecommendation = true, errorMessage = null, logSuccessMessage = null) }

        viewModelScope.launch {
            // CRITICAL: Check remaining calls BEFORE making API call to prevent race conditions
            // This must happen atomically with the API call, not in the UI
            val tier = com.coachie.app.data.SubscriptionService.getUserTier(userId)
            val remaining = if (tier == com.coachie.app.data.model.SubscriptionTier.FREE) {
                com.coachie.app.data.SubscriptionService.getRemainingAICalls(userId, com.coachie.app.data.model.AIFeature.MEAL_RECOMMENDATION)
            } else {
                Int.MAX_VALUE // Pro users have unlimited
            }
            
            if (remaining <= 0) {
                android.util.Log.e("MealRecommendationViewModel", "❌ No meal recommendations remaining (remaining: $remaining)")
                _uiState.update {
                    it.copy(
                        errorMessage = "No meal recommendations remaining today. Upgrade to Pro for unlimited recommendations!",
                        isRequestingRecommendation = false
                    )
                }
                return@launch
            }
            
            android.util.Log.d("MealRecommendationViewModel", "✅ Remaining calls: $remaining, proceeding with API call")
            
            val useImperial = _uiState.value.useImperial
            val result = GeminiClient.getInstance(context).generateMealRecommendation(preview, useImperial, userId)
            result
                .onSuccess { recommendation ->
                    // CRITICAL: Validate that the recommendation actually has a recipe (instructions)
                    if (recommendation.instructions.isEmpty()) {
                        android.util.Log.e("MealRecommendationViewModel", "❌ Recommendation returned with empty instructions - treating as failure")
                        // Don't record usage - this is a failed generation
                        _uiState.update {
                            it.copy(
                                errorMessage = "The AI didn't provide recipe instructions. This does not count toward your daily limit. Please try again.",
                                isRequestingRecommendation = false
                            )
                        }
                        return@launch
                    }
                    
                    // Validate that we have at least some ingredients
                    if (recommendation.ingredients.isEmpty()) {
                        android.util.Log.e("MealRecommendationViewModel", "❌ Recommendation returned with empty ingredients - treating as failure")
                        // Don't record usage - this is a failed generation
                        _uiState.update {
                            it.copy(
                                errorMessage = "The AI didn't provide recipe ingredients. This does not count toward your daily limit. Please try again.",
                                isRequestingRecommendation = false
                            )
                        }
                        return@launch
                    }
                    
                    // Only record usage if we have a valid recipe with instructions AND ingredients
                    try {
                        val tier = com.coachie.app.data.SubscriptionService.getUserTier(userId)
                        if (tier == com.coachie.app.data.model.SubscriptionTier.FREE) {
                            com.coachie.app.data.SubscriptionService.recordAIFeatureUsage(userId, com.coachie.app.data.model.AIFeature.MEAL_RECOMMENDATION)
                            android.util.Log.d("MealRecommendationViewModel", "✅ Recorded AI usage for successful meal recommendation with ${recommendation.instructions.size} instructions")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MealRecommendationViewModel", "Error recording AI usage (non-critical)", e)
                    }
                    
                    _uiState.update {
                        it.copy(
                            recommendation = recommendation,
                            isRequestingRecommendation = false,
                            selectedServings = recommendation.servings // Initialize with recipe's default servings
                        )
                    }
                }
                .onFailure { throwable ->
                    // Don't record usage on failure - the call failed
                    val errorMsg = when {
                        throwable.message?.contains("Gemini Flash client not available") == true -> 
                            "Gemini is not available. Please check your internet connection and try again."
                        throwable.message?.contains("Empty response") == true ->
                            "AI service returned an empty response. This does not count toward your daily limit. Please try again."
                        throwable.message?.contains("Incomplete JSON") == true ->
                            "The AI response was incomplete. This does not count toward your daily limit. Please try again."
                        else -> throwable.localizedMessage
                            ?: "Unable to generate a meal recommendation. Please try again."
                    }
                    android.util.Log.e("MealRecommendationViewModel", "Failed to generate recommendation: ${throwable.message}")
                    _uiState.update {
                        it.copy(
                            errorMessage = errorMsg,
                            isRequestingRecommendation = false
                        )
                    }
                }
        }
    }

    /**
     * Convert MealRecommendation to Recipe
     */
    private fun recommendationToRecipe(recommendation: MealRecommendation): Recipe {
        // Parse ingredients from string list to RecipeIngredient objects
        // Format: "1 cup chicken breast" or "2 tbsp olive oil"
        val recipeIngredients = recommendation.ingredients.mapNotNull { ingStr ->
            // Simple parsing - extract quantity, unit, and name
            val parts = ingStr.trim().split(" ", limit = 3)
            if (parts.size >= 2) {
                val quantity = parts[0].toDoubleOrNull() ?: 1.0
                val unit = parts[1]
                val name = if (parts.size > 2) parts[2] else parts[1]
                
                RecipeIngredient(
                    name = name,
                    quantity = quantity,
                    unit = unit,
                    calories = 0, // Will be calculated if needed
                    proteinG = 0,
                    carbsG = 0,
                    fatG = 0,
                    sugarG = 0
                )
            } else {
                RecipeIngredient(
                    name = ingStr,
                    quantity = 1.0,
                    unit = "item",
                    calories = 0,
                    proteinG = 0,
                    carbsG = 0,
                    fatG = 0,
                    sugarG = 0
                )
            }
        }

        // Calculate total nutrition from per-serving macros
        val totalCalories = recommendation.macrosPerServing.calories * recommendation.servings
        val totalProtein = recommendation.macrosPerServing.protein * recommendation.servings
        val totalCarbs = recommendation.macrosPerServing.carbs * recommendation.servings
        val totalFat = recommendation.macrosPerServing.fat * recommendation.servings
        val totalSugar = recommendation.macrosPerServing.sugar * recommendation.servings
        val totalAddedSugar = recommendation.macrosPerServing.addedSugar * recommendation.servings

        return Recipe(
            userId = userId,
            name = recommendation.recipeTitle,
            description = recommendation.summary,
            servings = recommendation.servings,
            ingredients = recipeIngredients,
            instructions = recommendation.instructions,
            totalCalories = totalCalories,
            totalProteinG = totalProtein,
            totalCarbsG = totalCarbs,
            totalFatG = totalFat,
            totalSugarG = totalSugar,
            totalAddedSugarG = totalAddedSugar
        )
    }

    fun logRecommendationToMeal(recommendation: MealRecommendation) {
        val profile = _uiState.value.profile ?: return
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        
        // Convert recommendation to recipe and save it
        val recipe = recommendationToRecipe(recommendation)
        val servingsConsumed = _uiState.value.selectedServings.toDouble()
        
        // Create meal log from recipe
        val mealLog = recipe.toMealLog(servingsConsumed.toInt())

        _uiState.update { it.copy(isLogging = true, errorMessage = null, logSuccessMessage = null) }

        viewModelScope.launch {
            // CRITICAL: Save recipe FIRST - this is mandatory
            android.util.Log.d("MealRecommendationViewModel", "💾 Saving recipe to personal collection: ${recipe.name}")
            val recipeResult = repository.saveRecipe(userId, recipe)
            val recipeId = if (recipeResult.isSuccess) {
                android.util.Log.d("MealRecommendationViewModel", "✅ Recipe saved successfully: ${recipe.id}")
                recipe.id
            } else {
                val error = recipeResult.exceptionOrNull()
                android.util.Log.e("MealRecommendationViewModel", "❌ FAILED to save recipe: ${error?.message}")
                android.util.Log.e("MealRecommendationViewModel", "Recipe: ${recipe.name}, ID: ${recipe.id}")
                // Still continue with meal log, but recipe won't be linked
                null
            }

            // Create meal log with recipe reference
            val mealLogWithRecipe = mealLog.copy(
                recipeId = recipeId,
                servingsConsumed = servingsConsumed
            )

            val result = repository.saveHealthLog(userId, today, mealLogWithRecipe)
            if (result.isSuccess) {
                repository.saveSavedMeal(
                    SavedMeal.fromMealLog(
                        userId = userId,
                        name = recommendation.recipeTitle,
                        mealLog = mealLogWithRecipe
                    )
                )
                loadUserContext()
                _uiState.update {
                    it.copy(
                        isLogging = false,
                        logSuccessMessage = "Meal logged and recipe saved!",
                        // Keep recommendation visible - don't clear it
                        recommendation = it.recommendation
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLogging = false,
                        errorMessage = result.exceptionOrNull()?.localizedMessage
                            ?: "Failed to log meal."
                    )
                }
            }
        }
    }

    fun saveRecommendationToQuickSelect(recommendation: MealRecommendation) {
        _uiState.update {
            it.copy(
                isSavingQuickSelect = true,
                errorMessage = null,
                logSuccessMessage = null
            )
        }

        viewModelScope.launch {
            // CRITICAL: Convert recommendation to Recipe and save it FIRST
            val recipe = recommendationToRecipe(recommendation)
            android.util.Log.d("MealRecommendationViewModel", "💾 Saving recipe to personal collection: ${recipe.name}")
            val recipeResult = repository.saveRecipe(userId, recipe)
            val recipeId = if (recipeResult.isSuccess) {
                android.util.Log.d("MealRecommendationViewModel", "✅ Recipe saved successfully: ${recipe.id}")
                recipe.id
            } else {
                val error = recipeResult.exceptionOrNull()
                android.util.Log.e("MealRecommendationViewModel", "❌ FAILED to save recipe: ${error?.message}")
                android.util.Log.e("MealRecommendationViewModel", "Recipe: ${recipe.name}, ID: ${recipe.id}")
                // Still continue with saved meal, but recipe won't be linked
                null
            }

            // Create SavedMeal from Recipe (includes recipeId link)
            val savedMeal = recipe.toSavedMeal(userId).copy(
                recipeId = recipeId // Ensure recipeId is set
            )

            val result = repository.saveSavedMeal(savedMeal)

            if (result.isSuccess) {
                loadUserContext()
                _uiState.update {
                    it.copy(
                        isSavingQuickSelect = false,
                        logSuccessMessage = "Recipe saved! You can access it anytime from your saved meals.",
                        // Keep recommendation visible - don't clear it
                        recommendation = it.recommendation
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isSavingQuickSelect = false,
                        errorMessage = result.exceptionOrNull()?.localizedMessage
                            ?: "Failed to save recipe."
                    )
                }
            }
        }
    }

    class Factory(
        private val repository: FirebaseRepository,
        private val userId: String,
        private val context: Context? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MealRecommendationViewModel::class.java)) {
                return MealRecommendationViewModel(repository, userId, context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

