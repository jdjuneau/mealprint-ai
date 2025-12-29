package com.coachie.app.data.api

import com.mealprint.ai.data.model.FoodItem
import com.mealprint.ai.data.model.NutritionInfo
import com.mealprint.ai.data.model.Portion
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * External nutrition API service for comprehensive food data
 * Currently configured for Nutritionix API
 */
interface NutritionApiService {

    @GET("v2/search/instant")
    suspend fun searchFoods(
        @Header("x-app-id") appId: String,
        @Header("x-app-key") appKey: String,
        @Query("query") query: String,
        @Query("detailed") detailed: Boolean = true
    ): NutritionixSearchResponse

    @GET("v2/natural/nutrients")
    suspend fun getNutrients(
        @Header("x-app-id") appId: String,
        @Header("x-app-key") appKey: String,
        @Query("query") query: String
    ): NutritionixNutrientsResponse
}

// API Response Models
data class NutritionixSearchResponse(
    val common: List<NutritionixCommonFood>,
    val branded: List<NutritionixBrandedFood>
)

data class NutritionixCommonFood(
    val food_name: String,
    val serving_unit: String,
    val serving_qty: Double,
    val tag_name: String? = null,
    val tag_id: String? = null
)

data class NutritionixBrandedFood(
    val food_name: String,
    val brand_name: String,
    val serving_unit: String,
    val serving_qty: Double,
    val nf_calories: Double? = null
)

data class NutritionixNutrientsResponse(
    val foods: List<NutritionixNutrientFood>
)

data class NutritionixNutrientFood(
    val food_name: String,
    val serving_weight_grams: Double,
    val nf_calories: Double,
    val nf_total_fat: Double,
    val nf_saturated_fat: Double,
    val nf_cholesterol: Double,
    val nf_sodium: Double,
    val nf_total_carbohydrate: Double,
    val nf_dietary_fiber: Double,
    val nf_sugars: Double,
    val nf_added_sugars: Double? = null,
    val nf_protein: Double,
    val serving_unit: String,
    val serving_qty: Double
)

/**
 * Nutrition API Client
 */
class NutritionApiClient(
    private val apiKey: String? = null,
    private val appId: String? = null
) {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://trackapi.nutritionix.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val service = retrofit.create(NutritionApiService::class.java)

    /**
     * Search for foods using external API
     */
    suspend fun searchFoods(query: String): Result<List<FoodItem>> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isNullOrEmpty() || appId.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("API credentials not configured"))
            }

            val response = service.searchFoods(
                appId = appId,
                appKey = apiKey,
                query = query
            )

            val foodItems = mutableListOf<FoodItem>()

            // Process common foods
            response.common.take(5).forEach { food ->
                val portions = mutableListOf<Portion>()
                portions.add(Portion("${food.serving_qty} ${food.serving_unit}", food.serving_qty * 100.0))

                // Add some common portion estimates
                when {
                    food.food_name.contains("chicken", ignoreCase = true) -> {
                        portions.add(Portion("4 oz piece", 113.0))
                        portions.add(Portion("6 oz piece", 170.0))
                    }
                    food.food_name.contains("beef", ignoreCase = true) -> {
                        portions.add(Portion("4 oz piece", 113.0))
                        portions.add(Portion("6 oz piece", 170.0))
                    }
                    food.food_name.contains("rice", ignoreCase = true) -> {
                        portions.add(Portion("1/2 cup cooked", 79.0))
                        portions.add(Portion("1 cup cooked", 158.0))
                    }
                }

                foodItems.add(
                    FoodItem(
                        name = food.food_name.replaceFirstChar { it.uppercase() },
                        calories = 0, // Will be calculated from nutrients API
                        protein = 0.0,
                        carbs = 0.0,
                        fat = 0.0,
                        commonPortions = portions.distinctBy { it.name }
                    )
                )
            }

            // Process branded foods (limit to avoid spam)
            response.branded.take(3).forEach { food ->
                foodItems.add(
                    FoodItem(
                        name = "${food.food_name} (${food.brand_name})",
                        calories = food.nf_calories?.toInt() ?: 0,
                        protein = 0.0,
                        carbs = 0.0,
                        fat = 0.0,
                        commonPortions = listOf(
                            Portion("${food.serving_qty} ${food.serving_unit}", food.serving_qty * 100.0)
                        )
                    )
                )
            }

            Result.success(foodItems)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get detailed nutrition for a specific food query
     */
    suspend fun getNutritionInfo(query: String): Result<NutritionInfo> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isNullOrEmpty() || appId.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("API credentials not configured"))
            }

            val response = service.getNutrients(
                appId = appId,
                appKey = apiKey,
                query = query
            )

            if (response.foods.isNotEmpty()) {
                val food = response.foods.first()
                val nutrition = NutritionInfo(
                    calories = food.nf_calories.toInt(),
                    protein = food.nf_protein,
                    carbs = food.nf_total_carbohydrate,
                    fat = food.nf_total_fat,
                    sugar = food.nf_sugars,
                    addedSugar = food.nf_added_sugars ?: 0.0
                )
                Result.success(nutrition)
            } else {
                Result.failure(Exception("No nutrition data found"))
            }

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Enhanced Food Database with API integration
 */
object EnhancedFoodDatabase {

    private val API_KEY: String?
        get() = try {
            com.coachie.app.BuildConfig.NUTRITIONIX_API_KEY.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    
    private val APP_ID: String?
        get() = try {
            com.coachie.app.BuildConfig.NUTRITIONIX_APP_ID.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }

    private val apiClient: NutritionApiClient by lazy {
        NutritionApiClient(
            apiKey = API_KEY,
            appId = APP_ID
        )
    }

    /**
     * Detect if query contains a brand name
     * Common patterns: "Brand Product", "Product by Brand", "Brand's Product"
     */
    private fun detectBrandInQuery(query: String): Boolean {
        val lowerQuery = query.lowercase()
        // Common brand indicators
        val brandIndicators = listOf(" by ", "'s ", " brand", "branded")
        return brandIndicators.any { lowerQuery.contains(it) } ||
               // Check if query has multiple capitalized words (potential brand + product)
               query.split(" ").count { it.firstOrNull()?.isUpperCase() == true } >= 2
    }

    /**
     * Search foods using local database, USDA API (FREE), and optionally Nutritionix API
     * USDA API is FREE and supports branded foods - use it as primary source
     */
    suspend fun searchFoodsEnhanced(query: String): List<FoodItem> {
        val hasBrand = detectBrandInQuery(query)
        val results = mutableListOf<FoodItem>()

        // First, try local database for quick results
        val localResults = com.coachie.app.data.model.FoodDatabase.searchFood(query)
        results.addAll(localResults)

        // Always try USDA API (FREE) - it supports branded foods and has full nutrition data
        try {
            val usdaResults = com.coachie.app.data.model.FoodDatabase.searchUSDAFoods(query)
            // Prioritize branded results if brand detected
            val (branded, nonBranded) = usdaResults.partition { food ->
                food.name.contains("BRANDED", ignoreCase = true) ||
                food.name.matches(Regex(".*\\(.*\\).*", RegexOption.IGNORE_CASE)) ||
                food.name.contains("brand", ignoreCase = true)
            }
            
            if (hasBrand) {
                // Add branded first, then non-branded
                branded.forEach { usdaFood ->
                    if (results.none { existingFood -> existingFood.name.equals(usdaFood.name, ignoreCase = true) }) {
                        results.add(usdaFood)
                    }
                }
                nonBranded.take(5).forEach { usdaFood ->
                    if (results.none { existingFood -> existingFood.name.equals(usdaFood.name, ignoreCase = true) }) {
                        results.add(usdaFood)
                    }
                }
                android.util.Log.d("EnhancedFoodDatabase", "✅ Found ${branded.size} branded + ${nonBranded.size} common results from USDA API")
            } else {
                // Normal order - add all USDA results
                usdaResults.take(10).forEach { usdaFood ->
                    if (results.none { existingFood -> existingFood.name.equals(usdaFood.name, ignoreCase = true) }) {
                        results.add(usdaFood)
                    }
                }
                android.util.Log.d("EnhancedFoodDatabase", "✅ Found ${usdaResults.size} results from USDA API")
            }
        } catch (e: Exception) {
            android.util.Log.w("NutritionApiService", "USDA API failed", e)
        }

        // Optionally try Nutritionix API if configured (paid service - $499/month)
        // Only use if USDA didn't return enough results
        if (results.size < 8 && isApiConfigured()) {
            try {
                val nutritionixResults = apiClient.searchFoods(query).getOrNull()
                if (nutritionixResults != null && nutritionixResults.isNotEmpty()) {
                    nutritionixResults.take(5).forEach { foodItem ->
                        if (results.none { it.name.equals(foodItem.name, ignoreCase = true) }) {
                            results.add(foodItem)
                        }
                    }
                    android.util.Log.d("EnhancedFoodDatabase", "✅ Added ${nutritionixResults.size} results from Nutritionix API (paid)")
                }
            } catch (e: Exception) {
                android.util.Log.w("NutritionApiService", "Nutritionix API failed", e)
            }
        }

        return results.take(20) // Increased limit to 20 for better brand coverage
    }

    /**
     * Get nutrition info using external API
     */
    suspend fun getNutritionFromApi(query: String): Result<NutritionInfo> {
        return apiClient.getNutritionInfo(query)
    }

    /**
     * Get full nutrition (macros + micros) for a branded food item
     * Uses USDA API (FREE) as primary source, falls back to Nutritionix if configured
     */
    suspend fun getFullNutritionForBrandedFood(foodName: String): Result<com.coachie.app.data.model.FoodItem> = withContext(Dispatchers.IO) {
        try {
            // First try USDA API (FREE) - it has full nutrition data including micronutrients
            val usdaResult = com.coachie.app.data.api.USDANutritionService.lookupIngredient(foodName)
            val usdaNutrition = usdaResult.getOrNull()
            
            if (usdaNutrition != null) {
                // USDA provides full nutrition including micronutrients
                val usdaFoods = com.coachie.app.data.model.FoodDatabase.searchUSDAFoods(foodName)
                val matchingFood = usdaFoods.firstOrNull { 
                    it.name.equals(foodName, ignoreCase = true) || 
                    foodName.contains(it.name, ignoreCase = true) ||
                    it.name.contains(foodName, ignoreCase = true)
                }
                
                if (matchingFood != null) {
                    android.util.Log.d("EnhancedFoodDatabase", "✅ Found full nutrition from USDA for: $foodName")
                    return@withContext Result.success(matchingFood)
                }
                
                // If no exact match, create FoodItem from USDA nutrition data
                val foodItem = com.coachie.app.data.model.FoodItem(
                    name = foodName,
                    calories = usdaNutrition.calories,
                    protein = usdaNutrition.protein,
                    carbs = usdaNutrition.carbs,
                    fat = usdaNutrition.fat,
                    sugar = usdaNutrition.sugar,
                    addedSugar = usdaNutrition.addedSugar,
                    micronutrients = emptyMap(), // USDA micronutrients can be extracted from full response if needed
                    commonPortions = listOf(
                        com.coachie.app.data.model.Portion("1 serving", 100.0)
                    )
                )
                return@withContext Result.success(foodItem)
            }

            // Fallback to Nutritionix API if configured (paid service)
            if (isApiConfigured()) {
                val nutritionResult = apiClient.getNutritionInfo(foodName)
                val nutritionInfo = nutritionResult.getOrNull()
                
                if (nutritionInfo != null) {
                    val foodItem = com.coachie.app.data.model.FoodItem(
                        name = foodName,
                        calories = nutritionInfo.calories,
                        protein = nutritionInfo.protein,
                        carbs = nutritionInfo.carbs,
                        fat = nutritionInfo.fat,
                        sugar = nutritionInfo.sugar,
                        addedSugar = nutritionInfo.addedSugar,
                        micronutrients = emptyMap(),
                        commonPortions = listOf(
                            com.coachie.app.data.model.Portion("1 serving", 100.0)
                        )
                    )
                    android.util.Log.d("EnhancedFoodDatabase", "✅ Found nutrition from Nutritionix for: $foodName")
                    return@withContext Result.success(foodItem)
                }
            }

            return@withContext Result.failure(Exception("No nutrition data found for: $foodName"))
        } catch (e: Exception) {
            android.util.Log.e("EnhancedFoodDatabase", "Error getting full nutrition", e)
            Result.failure(e)
        }
    }

    /**
     * Check if API is configured
     */
    fun isApiConfigured(): Boolean {
        return !API_KEY.isNullOrEmpty() && !APP_ID.isNullOrEmpty()
    }
}

