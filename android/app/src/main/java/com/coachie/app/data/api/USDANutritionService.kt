package com.coachie.app.data.api

import com.coachie.app.data.Secrets
import com.coachie.app.data.model.MicronutrientType
import com.coachie.app.data.model.NutritionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Service for looking up nutrition data from USDA FoodData Central API
 * Provides accurate, government-verified nutrition data instead of GPT estimates
 */
object USDANutritionService {
    private const val BASE_URL = "https://api.nal.usda.gov/fdc/v1"
    
    /**
     * Parse quantity and unit from ingredient text
     * Examples: "8 eggs" -> {quantity: 8, unit: "egg", name: "eggs"}
     *           "1 cup broccoli" -> {quantity: 1, unit: "cup", name: "broccoli"}
     */
    private fun parseIngredient(ingredientText: String): Triple<Double, String, String> {
        val parts = ingredientText.trim().split("\\s+".toRegex())
        if (parts.size < 2) {
            return Triple(1.0, "", ingredientText)
        }

        // Try to parse quantity (first part)
        val quantityStr = parts[0]
        val quantity = quantityStr.toDoubleOrNull() ?: 1.0

        // Remaining parts are unit + name
        val rest = parts.subList(1, parts.size).joinToString(" ")

        // Common units
        val unitPatterns = listOf(
            Regex("^(cup|cups|C|CUP|CUPS)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(tbsp|tablespoon|tablespoons|T|TBSP)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(tsp|teaspoon|teaspoons|t|TSP)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(oz|ounce|ounces|OZ)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(lb|lbs|pound|pounds|LB|LBS)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(g|gram|grams|G|GRAM|GRAMS)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(kg|kilogram|kilograms|KG)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(ml|milliliter|milliliters|mL|ML)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(fl\\s*oz|fluid\\s*ounce|fluid\\s*ounces|FL\\s*OZ)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(l|liter|liters|L|LITER|LITERS)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(egg|eggs|EGG|EGGS)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(piece|pieces|PIECE|PIECES)\\s+", RegexOption.IGNORE_CASE),
            Regex("^(slice|slices|SLICE|SLICES)\\s+", RegexOption.IGNORE_CASE),
        )

        var unit = ""
        var name = rest

        for (pattern in unitPatterns) {
            val match = pattern.find(rest)
            if (match != null) {
                unit = match.groupValues[1].lowercase()
                name = rest.substring(match.range.last + 1).trim()
                break
            }
        }

        // If no unit found and quantity is reasonable, check if name starts with countable noun
        if (unit.isEmpty() && quantity > 0 && quantity < 100) {
            val countablePattern = Regex("^(egg|eggs|apple|apples|banana|bananas|piece|pieces|slice|slices)", RegexOption.IGNORE_CASE)
            val nameMatch = countablePattern.find(name)
            if (nameMatch != null) {
                unit = nameMatch.value.lowercase().replace("s$".toRegex(), "") // Remove plural
                name = name.substring(nameMatch.range.last + 1).trim().takeIf { it.isNotEmpty() } ?: nameMatch.value
            }
        }

        return Triple(quantity, unit, name.ifEmpty { ingredientText })
    }

    /**
     * Get gram weight for a unit from USDA foodPortions
     */
    private fun getGramWeightForUnit(foodPortions: org.json.JSONArray, unit: String, quantity: Double): Double? {
        if (foodPortions.length() == 0) return null

        val unitLower = unit.lowercase()

        // Try to match unit in foodPortions
        for (i in 0 until foodPortions.length()) {
            val portion = foodPortions.getJSONObject(i)
            val measure = portion.optString("measureUnit", "").lowercase()
            val description = portion.optString("measureDescription", "").lowercase()
            val gramWeight = portion.optDouble("gramWeight", 0.0)

            // Check if unit matches
            if ((measure.contains(unitLower) || description.contains(unitLower) ||
                 description.contains(unitLower + "s") || 
                 (unitLower.length > 1 && description.contains(unitLower.dropLast(1))))) {
                if (gramWeight > 0) {
                    return gramWeight * quantity
                }
            }
        }

        // Fallback: if unit is "g" or "gram", quantity is already in grams
        if (unitLower == "g" || unitLower == "gram" || unitLower == "grams") {
            return quantity
        }

        // Fallback: common unit conversions (approximate)
        val unitConversions = mapOf(
            "cup" to 240.0,      // 1 cup ≈ 240g
            "tbsp" to 15.0,      // 1 tbsp ≈ 15g
            "tsp" to 5.0,        // 1 tsp ≈ 5g
            "oz" to 28.35,       // 1 oz ≈ 28.35g
            "lb" to 453.6,       // 1 lb ≈ 453.6g
            "egg" to 50.0,       // 1 large egg ≈ 50g
            "piece" to 100.0,    // Generic piece ≈ 100g
            "slice" to 25.0      // Generic slice ≈ 25g
        )

        val baseGrams = unitConversions[unitLower]
        return baseGrams?.let { it * quantity }
    }

    /**
     * Look up nutrition for a single ingredient with quantity scaling
     * Example: "8 eggs" or "1 cup broccoli" or "200g chicken breast"
     * 
     * @param ingredientText The ingredient with quantity (e.g., "8 eggs", "1 cup broccoli", "200g chicken breast")
     * @return Result containing NutritionInfo with macros and micronutrients SCALED to the requested quantity
     */
    suspend fun lookupIngredient(ingredientText: String): Result<NutritionInfo> = withContext(Dispatchers.IO) {
        try {
            val apiKey = Secrets.getUSDAApiKey()
            
            // Parse quantity, unit, and name from ingredient text
            val (quantity, unit, name) = parseIngredient(ingredientText)
            
            // Search for the food (use just the name, not quantity)
            val encodedQuery = java.net.URLEncoder.encode(name, "UTF-8")
            val searchUrl = URL("$BASE_URL/foods/search?api_key=$apiKey&query=$encodedQuery&pageSize=1")
            
            val searchConnection = searchUrl.openConnection() as HttpURLConnection
            searchConnection.requestMethod = "GET"
            searchConnection.connectTimeout = 15000
            searchConnection.readTimeout = 15000
            
            if (searchConnection.responseCode == 200) {
                val searchResponse = searchConnection.inputStream.bufferedReader().use { it.readText() }
                val searchJson = JSONObject(searchResponse)
                val foods = searchJson.optJSONArray("foods")
                
                if (foods != null && foods.length() > 0) {
                    val food = foods.getJSONObject(0)
                    val nutritionPer100g = parseUSDAFood(food)
                    
                    // Try fallback unit conversions first (fast, no extra API call)
                    val fallbackGrams = getGramWeightForUnit(org.json.JSONArray(), unit, quantity)
                    
                    if (fallbackGrams != null && fallbackGrams > 0) {
                        // Use fallback conversion - single API call!
                        val scaleFactor = fallbackGrams / 100.0
                        
                        val scaledNutrition = NutritionInfo(
                            calories = (nutritionPer100g.calories * scaleFactor).toInt(),
                            protein = nutritionPer100g.protein * scaleFactor,
                            carbs = nutritionPer100g.carbs * scaleFactor,
                            fat = nutritionPer100g.fat * scaleFactor,
                            sugar = nutritionPer100g.sugar * scaleFactor,
                            addedSugar = nutritionPer100g.addedSugar * scaleFactor,
                            micronutrients = nutritionPer100g.micronutrients.mapValues { (_, value) -> value * scaleFactor }
                        )
                        
                        android.util.Log.d("USDANutritionService", "Scaled $ingredientText using fallback: ${fallbackGrams}g = ${String.format("%.2f", scaleFactor)}x factor")
                        Result.success(scaledNutrition)
                    } else {
                        // Fallback didn't work, get full food details for accurate foodPortions
                        // (Only happens for uncommon units or when we need precise measurements)
                        val fdcId = food.optInt("fdcId", -1)
                        
                        if (fdcId > 0) {
                            val detailUrl = URL("$BASE_URL/food/$fdcId?api_key=$apiKey")
                            val detailConnection = detailUrl.openConnection() as HttpURLConnection
                            detailConnection.requestMethod = "GET"
                            detailConnection.connectTimeout = 15000
                            detailConnection.readTimeout = 15000
                            
                            if (detailConnection.responseCode == 200) {
                                val detailResponse = detailConnection.inputStream.bufferedReader().use { it.readText() }
                                val detailJson = JSONObject(detailResponse)
                                val detailNutritionPer100g = parseUSDAFood(detailJson)
                                
                                // Get gram weight for the requested unit from foodPortions
                                val foodPortions = detailJson.optJSONArray("foodPortions") ?: org.json.JSONArray()
                                val totalGrams = getGramWeightForUnit(foodPortions, unit, quantity)
                                
                                if (totalGrams != null && totalGrams > 0) {
                                    // Scale nutrition by: (nutritionPer100g × totalGrams) / 100
                                    val scaleFactor = totalGrams / 100.0
                                    
                                    val scaledNutrition = NutritionInfo(
                                        calories = (detailNutritionPer100g.calories * scaleFactor).toInt(),
                                        protein = detailNutritionPer100g.protein * scaleFactor,
                                        carbs = detailNutritionPer100g.carbs * scaleFactor,
                                        fat = detailNutritionPer100g.fat * scaleFactor,
                                        sugar = detailNutritionPer100g.sugar * scaleFactor,
                                        addedSugar = detailNutritionPer100g.addedSugar * scaleFactor,
                                        micronutrients = detailNutritionPer100g.micronutrients.mapValues { (_, value) -> value * scaleFactor }
                                    )
                                    
                                    android.util.Log.d("USDANutritionService", "Scaled $ingredientText using foodPortions: ${totalGrams}g = ${String.format("%.2f", scaleFactor)}x factor")
                                    Result.success(scaledNutrition)
                                } else {
                                    // No unit match found, return per-100g values
                                    android.util.Log.w("USDANutritionService", "Could not find gram weight for \"$unit\" in $ingredientText, returning per-100g values")
                                    Result.success(detailNutritionPer100g)
                                }
                            } else {
                                // Fallback to search result if detail fails
                                android.util.Log.w("USDANutritionService", "Detail lookup failed for FDC $fdcId, using search result")
                                Result.success(nutritionPer100g)
                            }
                        } else {
                            Result.success(nutritionPer100g)
                        }
                    }
                } else {
                    Result.failure(Exception("No food found for: $ingredientText"))
                }
            } else {
                val errorBody = try {
                    searchConnection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                } catch (e: Exception) {
                    "Unknown error"
                }
                Result.failure(Exception("USDA API error: ${searchConnection.responseCode} - $errorBody"))
            }
        } catch (e: Exception) {
            android.util.Log.e("USDANutritionService", "Error looking up ingredient: $ingredientText", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get detailed nutrition for a specific food by FDC ID
     * Use this when you already have the FDC ID from a search
     */
    suspend fun getFoodDetails(fdcId: Int): Result<NutritionInfo> = withContext(Dispatchers.IO) {
        try {
            val apiKey = Secrets.getUSDAApiKey()
            val url = URL("$BASE_URL/food/$fdcId?api_key=$apiKey")
            
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val food = JSONObject(response)
                val nutrition = parseUSDAFood(food)
                Result.success(nutrition)
            } else {
                Result.failure(Exception("USDA API error: ${connection.responseCode}"))
            }
        } catch (e: Exception) {
            android.util.Log.e("USDANutritionService", "Error getting food details for FDC ID: $fdcId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Parse USDA food JSON into NutritionInfo with macros and micronutrients
     */
    private fun parseUSDAFood(foodJson: JSONObject): NutritionInfo {
        val nutrients = foodJson.optJSONArray("foodNutrients") ?: return NutritionInfo(0, 0.0, 0.0, 0.0)
        
        var calories = 0
        var protein = 0.0
        var carbs = 0.0
        var fat = 0.0
        var sugar = 0.0
        var addedSugar = 0.0
        val micronutrients = mutableMapOf<MicronutrientType, Double>()
        
        for (i in 0 until nutrients.length()) {
            val nutrient = nutrients.getJSONObject(i)
            val nutrientId = nutrient.optInt("nutrientId", -1)
            val amount = nutrient.optDouble("value", 0.0)
            val unitName = nutrient.optString("unitName", "").lowercase()
            
            // Map USDA nutrient IDs to our nutrition values
            when (nutrientId) {
                1008 -> calories = amount.toInt() // Energy (kcal)
                1003 -> protein = amount // Protein
                1005 -> carbs = amount // Carbohydrate, by difference
                1004 -> fat = amount // Total lipid (fat)
                2000 -> sugar = amount // Sugars, total including NLEA
                1235 -> addedSugar = amount // Added sugars
                
                // Micronutrients
                1106 -> micronutrients[MicronutrientType.VITAMIN_A] = amount // Vitamin A, RAE (mcg)
                1162 -> micronutrients[MicronutrientType.VITAMIN_C] = amount // Vitamin C (mg)
                1110 -> micronutrients[MicronutrientType.VITAMIN_D] = amount * 40.0 // Vitamin D: Convert mcg to IU (1 mcg = 40 IU)
                1109 -> micronutrients[MicronutrientType.VITAMIN_E] = amount // Vitamin E (alpha-tocopherol) (mg)
                1185 -> micronutrients[MicronutrientType.VITAMIN_K] = amount // Vitamin K (phylloquinone) (mcg)
                1165 -> micronutrients[MicronutrientType.VITAMIN_B1] = amount // Thiamin (mg)
                1166 -> micronutrients[MicronutrientType.VITAMIN_B2] = amount // Riboflavin (mg)
                1167 -> micronutrients[MicronutrientType.VITAMIN_B3] = amount // Niacin (mg)
                1170 -> micronutrients[MicronutrientType.VITAMIN_B5] = amount // Pantothenic acid (mg)
                1175 -> micronutrients[MicronutrientType.VITAMIN_B6] = amount // Vitamin B6 (mg)
                1176 -> micronutrients[MicronutrientType.VITAMIN_B7] = amount // Biotin (mcg)
                1177 -> micronutrients[MicronutrientType.VITAMIN_B9] = amount // Folate, total (mcg)
                1178 -> micronutrients[MicronutrientType.VITAMIN_B12] = amount // Vitamin B12 (mcg)
                1180 -> micronutrients[MicronutrientType.CHOLINE] = amount // Choline, total (mg)
                1087 -> micronutrients[MicronutrientType.CALCIUM] = amount // Calcium, Ca (mg)
                1089 -> micronutrients[MicronutrientType.IRON] = amount // Iron, Fe (mg)
                1090 -> micronutrients[MicronutrientType.MAGNESIUM] = amount // Magnesium, Mg (mg)
                1091 -> micronutrients[MicronutrientType.PHOSPHORUS] = amount // Phosphorus, P (mg)
                1092 -> micronutrients[MicronutrientType.POTASSIUM] = amount // Potassium, K (mg)
                1093 -> micronutrients[MicronutrientType.SODIUM] = amount // Sodium, Na (mg)
                1095 -> micronutrients[MicronutrientType.ZINC] = amount // Zinc, Zn (mg)
                1103 -> micronutrients[MicronutrientType.COPPER] = amount // Copper, Cu (mg)
                1104 -> micronutrients[MicronutrientType.MANGANESE] = amount // Manganese, Mn (mg)
                1105 -> micronutrients[MicronutrientType.SELENIUM] = amount // Selenium, Se (mcg)
                1100 -> micronutrients[MicronutrientType.IODINE] = amount // Iodine, I (mcg)
                1101 -> micronutrients[MicronutrientType.CHROMIUM] = amount // Chromium, Cr (mcg)
                1102 -> micronutrients[MicronutrientType.MOLYBDENUM] = amount // Molybdenum, Mo (mcg)
            }
        }
        
        // Filter out zero values and convert to immutable map
        val filteredMicros = micronutrients.filterValues { it > 0.0 }
        
        return NutritionInfo(
            calories = calories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            sugar = sugar,
            addedSugar = addedSugar,
            micronutrients = filteredMicros
        )
    }
}
