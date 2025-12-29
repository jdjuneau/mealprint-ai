package com.coachie.app.data.ai

import android.util.Log
import com.coachie.app.BuildConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.coachie.app.data.ai.FoodBarcodeParser.ParsedFoodBarcode

/**
 * Centralized barcode-based food lookup service.
 * Uses multiple providers to find food product information.
 */
object FoodLookupService {

    private const val TAG = "FoodLookupService"

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val providers: List<FoodDataProvider> by lazy {
        listOfNotNull(
            OpenFoodFactsProvider(httpClient),
            UpcItemDbProvider(httpClient, BuildConfig.UPC_ITEM_DB_API_KEY, BuildConfig.UPC_ITEM_DB_API_HOST),
            NutritionixProvider(httpClient, BuildConfig.NUTRITIONIX_APP_ID, BuildConfig.NUTRITIONIX_API_KEY)
        )
    }

    suspend fun lookup(barcode: String): ParsedFoodBarcode? = withContext(Dispatchers.IO) {
        for (provider in providers) {
            try {
                val result = provider.lookup(barcode)
                if (result != null) {
                    Log.d(TAG, "Barcode $barcode matched provider ${provider.name}")
                    return@withContext result
                }
            } catch (e: Exception) {
                Log.w(TAG, "Provider ${provider.name} failed for barcode $barcode", e)
            }
        }
        Log.w(TAG, "No providers returned results for barcode $barcode")
        null
    }

    private interface FoodDataProvider {
        val name: String
        suspend fun lookup(barcode: String): ParsedFoodBarcode?
    }

    /**
     * Provider using Open Food Facts public API.
     */
    private class OpenFoodFactsProvider(
        private val client: HttpClient
    ) : FoodDataProvider {

        override val name: String = "OpenFoodFacts"

        override suspend fun lookup(barcode: String): ParsedFoodBarcode? {
            val response = client.get("https://world.openfoodfacts.org/api/v2/product/$barcode.json")
            if (!response.status.isSuccess()) return null

            val body: OpenFoodFactsResponse = response.body()
            val product = body.product ?: return null

            val nutriments = product.nutriments ?: return null

            // Get serving size
            val servingSize = product.servingSize ?: product.servingQuantity?.let { 
                "${it}${product.servingUnit ?: "g"}"
            }

            // Extract macronutrients (per 100g typically, but we'll use per serving if available)
            val calories = nutriments.energyKcal100g ?: nutriments.energyKcal ?: 0.0
            val protein = nutriments.proteins100g ?: nutriments.proteins ?: 0.0
            val carbs = nutriments.carbohydrates100g ?: nutriments.carbohydrates ?: 0.0
            val fat = nutriments.fat100g ?: nutriments.fat ?: 0.0
            val sugar = nutriments.sugars100g ?: nutriments.sugars ?: 0.0
            val addedSugar = nutriments.addedSugars100g ?: nutriments.addedSugars ?: 0.0

            if (calories == 0.0 && protein == 0.0 && carbs == 0.0 && fat == 0.0) {
                return null
            }

            // Estimate sugar if not available but carbs are present
            val estimatedSugar = if (sugar == 0.0 && carbs > 0.0) {
                estimateSugarFromCarbs(carbs, product.productName ?: "")
            } else {
                sugar
            }

            return ParsedFoodBarcode(
                name = product.productName ?: "Unknown Product",
                calories = calories,
                protein = protein,
                carbs = carbs,
                fat = fat,
                sugar = estimatedSugar,
                addedSugar = addedSugar,
                servingSize = servingSize,
                rawText = "Barcode $barcode via Open Food Facts",
                confidence = 0.85f
            )
        }
    }

    /**
     * Provider using UPCItemDB API.
     */
    private class UpcItemDbProvider(
        private val client: HttpClient,
        private val apiKey: String,
        private val host: String
    ) : FoodDataProvider {

        override val name: String = "UPCItemDB"

        override suspend fun lookup(barcode: String): ParsedFoodBarcode? {
            if (apiKey.isBlank() || host.isBlank()) return null

            val response = client.get(host) {
                headers {
                    append("user_key", apiKey)
                }
                url {
                    parameters.append("upc", barcode)
                }
            }

            if (!response.status.isSuccess()) return null

            val payload: JsonObject = response.body()
            val items = payload["items"]?.jsonArray ?: return null
            if (items.isEmpty()) return null

            val item = items.first().jsonObject
            val nutrition = item["nutrition"]?.jsonObject ?: return null

            fun JsonObject.valueOf(key: String): Double? {
                val valueObj = this[key]?.jsonObject?.get("value")?.jsonPrimitive
                val directValue = this[key]?.jsonPrimitive
                val value = valueObj ?: directValue
                return when {
                    value?.doubleOrNull != null -> value.doubleOrNull
                    value?.intOrNull != null -> value.intOrNull?.toDouble()
                    value?.isString == true -> value.content.toDoubleOrNull()
                    else -> null
                }
            }

            val calories = nutrition.valueOf("calories") ?: 0.0
            val protein = nutrition.valueOf("protein") ?: 0.0
            val carbs = nutrition.valueOf("carbohydrates") ?: 0.0
            val fat = nutrition.valueOf("fat") ?: 0.0
            val sugar = nutrition.valueOf("sugars") ?: nutrition.valueOf("sugar") ?: 0.0
            val addedSugar = nutrition.valueOf("added_sugars") ?: nutrition.valueOf("added-sugars") ?: 0.0

            if (calories == 0.0 && protein == 0.0 && carbs == 0.0 && fat == 0.0) {
                return null
            }

            val title = item["title"]?.jsonPrimitive?.content ?: "Unknown Product"
            val servingSize = item["serving_size"]?.jsonPrimitive?.content

            // Estimate sugar if not available but carbs are present
            val estimatedSugar = if (sugar == 0.0 && carbs > 0.0) {
                estimateSugarFromCarbs(carbs, title)
            } else {
                sugar
            }

            return ParsedFoodBarcode(
                name = title,
                calories = calories,
                protein = protein,
                carbs = carbs,
                fat = fat,
                sugar = estimatedSugar,
                addedSugar = addedSugar,
                servingSize = servingSize,
                rawText = "Barcode $barcode via UPCItemDB",
                confidence = 0.8f
            )
        }
    }

    /**
     * Provider using Nutritionix API.
     */
    private class NutritionixProvider(
        private val client: HttpClient,
        private val appId: String?,
        private val apiKey: String?
    ) : FoodDataProvider {

        override val name: String = "Nutritionix"

        override suspend fun lookup(barcode: String): ParsedFoodBarcode? {
            if (appId.isNullOrBlank() || apiKey.isNullOrBlank()) return null

            val response = client.get("https://trackapi.nutritionix.com/v2/search/item") {
                headers {
                    append("x-app-id", appId)
                    append("x-app-key", apiKey)
                    append("x-remote-user-id", "0")
                }
                url {
                    parameters.append("upc", barcode)
                }
            }

            if (!response.status.isSuccess()) return null

            val body: JsonObject = response.body()
            val foods = body["foods"]?.jsonArray ?: body["items"]?.jsonArray ?: return null
            if (foods.isEmpty()) return null

            val food = foods.first().jsonObject

            fun getDoubleValue(obj: JsonObject, vararg keys: String): Double {
                for (key in keys) {
                    val primitive = obj[key]?.jsonPrimitive
                    if (primitive != null) {
                        val doubleVal = primitive.doubleOrNull
                        if (doubleVal != null) return doubleVal
                        val intVal = primitive.intOrNull
                        if (intVal != null) return intVal.toDouble()
                        if (primitive.isString) {
                            primitive.content.toDoubleOrNull()?.let { return it }
                        }
                    }
                }
                return 0.0
            }
            
            val calories = getDoubleValue(food, "nf_calories", "calories")
            val protein = getDoubleValue(food, "nf_protein", "protein")
            val carbs = getDoubleValue(food, "nf_total_carbohydrate", "total_carbohydrate")
            val fat = getDoubleValue(food, "nf_total_fat", "total_fat")
            val sugar = getDoubleValue(food, "nf_sugars", "sugars", "sugar")
            val addedSugar = getDoubleValue(food, "nf_added_sugars", "added_sugars", "added-sugars")

            if (calories == 0.0 && protein == 0.0 && carbs == 0.0 && fat == 0.0) {
                return null
            }

            val name = food["food_name"]?.jsonPrimitive?.content 
                ?: food["item_name"]?.jsonPrimitive?.content ?: "Unknown Product"
            val servingSize = food["serving_weight_grams"]?.jsonPrimitive?.content?.let { "${it}g" }
                ?: food["serving_size"]?.jsonPrimitive?.content

            // Estimate sugar if not available but carbs are present
            val estimatedSugar = if (sugar == 0.0 && carbs > 0.0) {
                estimateSugarFromCarbs(carbs, name)
            } else {
                sugar
            }

            return ParsedFoodBarcode(
                name = name,
                calories = calories,
                protein = protein,
                carbs = carbs,
                fat = fat,
                sugar = estimatedSugar,
                addedSugar = addedSugar,
                servingSize = servingSize,
                rawText = "Barcode $barcode via Nutritionix",
                confidence = 0.9f
            )
        }
    }

    // ----- Serialization models -----

    @Serializable
    private data class OpenFoodFactsResponse(
        val product: Product? = null
    )

    @Serializable
    private data class Product(
        @SerialName("product_name") val productName: String? = null,
        @SerialName("serving_size") val servingSize: String? = null,
        @SerialName("serving_quantity") val servingQuantity: Double? = null,
        @SerialName("serving_unit") val servingUnit: String? = null,
        val nutriments: Nutriments? = null
    )

    @Serializable
    private data class Nutriments(
        @SerialName("energy-kcal_100g") val energyKcal100g: Double? = null,
        @SerialName("energy-kcal") val energyKcal: Double? = null,
        @SerialName("proteins_100g") val proteins100g: Double? = null,
        val proteins: Double? = null,
        @SerialName("carbohydrates_100g") val carbohydrates100g: Double? = null,
        val carbohydrates: Double? = null,
        @SerialName("fat_100g") val fat100g: Double? = null,
        val fat: Double? = null,
        @SerialName("sugars_100g") val sugars100g: Double? = null,
        val sugars: Double? = null,
        @SerialName("added-sugars_100g") val addedSugars100g: Double? = null,
        @SerialName("added-sugars") val addedSugars: Double? = null
    )

    /**
     * Estimate sugar content from carbs when sugar data is not available.
     * Uses heuristics based on food name and carb content.
     */
    private fun estimateSugarFromCarbs(carbs: Double, foodName: String): Double {
        if (carbs <= 0.0) return 0.0
        
        val nameLower = foodName.lowercase()
        
        // High sugar foods (most carbs are sugar)
        val highSugarKeywords = listOf(
            "candy", "chocolate", "cookie", "cake", "donut", "muffin", "pastry",
            "soda", "juice", "drink", "beverage", "sweet", "syrup", "honey",
            "jam", "jelly", "preserve", "marmalade", "fruit", "berries"
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
}

