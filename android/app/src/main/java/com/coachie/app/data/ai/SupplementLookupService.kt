package com.coachie.app.data.ai

import android.util.Log
import com.coachie.app.BuildConfig
import com.coachie.app.data.ai.SupplementLabelParser.ParsedSupplementLabel
import com.coachie.app.data.model.MicronutrientType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Centralized barcode-based supplement lookup service.
 * Tries a chain of providers until one returns a result.
 */
object SupplementLookupService {

    private const val TAG = "SupplementLookupService"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    private val providers: List<SupplementDataProvider> by lazy {
        listOfNotNull(
            LocalSupplementProvider(),
            OpenFoodFactsProvider(httpClient),
            UpcItemDbProvider(httpClient, BuildConfig.UPC_ITEM_DB_API_KEY, BuildConfig.UPC_ITEM_DB_API_HOST),
            NutritionixProvider(httpClient, BuildConfig.NUTRITIONIX_APP_ID, BuildConfig.NUTRITIONIX_API_KEY),
            BarcodeLookupProvider(httpClient)
        )
    }

    suspend fun lookup(barcode: String): ParsedSupplementLabel? = withContext(Dispatchers.IO) {
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

    private interface SupplementDataProvider {
        val name: String
        suspend fun lookup(barcode: String): ParsedSupplementLabel?
    }

    /**
     * Local curated supplements not found in public APIs.
     */
    private class LocalSupplementProvider : SupplementDataProvider {
        override val name: String = "Local"

        private val supplements: Map<String, ParsedSupplementLabel> = mapOf(
            // Nutricost supplements
            "810139575672" to ParsedSupplementLabel(
                name = "Nutricost Methylated Multivitamin",
                rawText = "Matched curated database entry for Nutricost Methylated Multivitamin",
                confidence = 0.99f,
                nutrients = mapOf(
                    MicronutrientType.VITAMIN_A to 1800.0,
                    MicronutrientType.VITAMIN_C to 250.0,
                    MicronutrientType.VITAMIN_D to 100.0,
                    MicronutrientType.VITAMIN_E to 40.0,
                    MicronutrientType.VITAMIN_K to 100.0,
                    MicronutrientType.VITAMIN_B1 to 75.0,
                    MicronutrientType.VITAMIN_B2 to 23.0,
                    MicronutrientType.VITAMIN_B3 to 38.0,
                    MicronutrientType.VITAMIN_B6 to 15.0,
                    MicronutrientType.VITAMIN_B9 to 2000.0,
                    MicronutrientType.VITAMIN_B12 to 750.0,
                    MicronutrientType.VITAMIN_B7 to 1500.0,
                    MicronutrientType.VITAMIN_B5 to 38.0,
                    MicronutrientType.CALCIUM to 50.0,
                    MicronutrientType.IRON to 10.0,
                    MicronutrientType.IODINE to 113.0,
                    MicronutrientType.MAGNESIUM to 15.0,
                    MicronutrientType.ZINC to 11.0,
                    MicronutrientType.SELENIUM to 140.0,
                    MicronutrientType.COPPER to 2.0,
                    MicronutrientType.MANGANESE to 2.0
                )
            ),
            // Common supplement barcodes - add more as users request them
            "722252116701" to ParsedSupplementLabel( // Centrum Silver Adults 50+
                name = "Centrum Silver Adults 50+",
                rawText = "Matched curated database entry for Centrum Silver Adults 50+",
                confidence = 0.99f,
                nutrients = mapOf(
                    MicronutrientType.VITAMIN_A to 2500.0,
                    MicronutrientType.VITAMIN_C to 100.0,
                    MicronutrientType.VITAMIN_D to 25.0,
                    MicronutrientType.VITAMIN_E to 35.0,
                    MicronutrientType.VITAMIN_K to 30.0,
                    MicronutrientType.VITAMIN_B1 to 1.5,
                    MicronutrientType.VITAMIN_B2 to 1.7,
                    MicronutrientType.VITAMIN_B3 to 20.0,
                    MicronutrientType.VITAMIN_B6 to 3.0,
                    MicronutrientType.VITAMIN_B9 to 400.0,
                    MicronutrientType.VITAMIN_B12 to 25.0,
                    MicronutrientType.VITAMIN_B7 to 30.0,
                    MicronutrientType.VITAMIN_B5 to 10.0,
                    MicronutrientType.CALCIUM to 200.0,
                    MicronutrientType.IRON to 8.0,
                    MicronutrientType.IODINE to 150.0,
                    MicronutrientType.MAGNESIUM to 50.0,
                    MicronutrientType.ZINC to 11.0,
                    MicronutrientType.SELENIUM to 55.0,
                    MicronutrientType.COPPER to 0.9,
                    MicronutrientType.MANGANESE to 2.3
                )
            ),
            "722252110603" to ParsedSupplementLabel( // Centrum Adults
                name = "Centrum Adults",
                rawText = "Matched curated database entry for Centrum Adults",
                confidence = 0.99f,
                nutrients = mapOf(
                    MicronutrientType.VITAMIN_A to 3500.0,
                    MicronutrientType.VITAMIN_C to 90.0,
                    MicronutrientType.VITAMIN_D to 25.0,
                    MicronutrientType.VITAMIN_E to 30.0,
                    MicronutrientType.VITAMIN_K to 25.0,
                    MicronutrientType.VITAMIN_B1 to 1.5,
                    MicronutrientType.VITAMIN_B2 to 1.7,
                    MicronutrientType.VITAMIN_B3 to 20.0,
                    MicronutrientType.VITAMIN_B6 to 2.0,
                    MicronutrientType.VITAMIN_B9 to 400.0,
                    MicronutrientType.VITAMIN_B12 to 6.0,
                    MicronutrientType.VITAMIN_B7 to 30.0,
                    MicronutrientType.VITAMIN_B5 to 10.0,
                    MicronutrientType.CALCIUM to 162.0,
                    MicronutrientType.IRON to 8.0,
                    MicronutrientType.IODINE to 150.0,
                    MicronutrientType.MAGNESIUM to 40.0,
                    MicronutrientType.ZINC to 11.0,
                    MicronutrientType.SELENIUM to 21.0,
                    MicronutrientType.COPPER to 0.9,
                    MicronutrientType.MANGANESE to 2.3
                )
            ),
            "031604016504" to ParsedSupplementLabel( // One A Day 50+
                name = "One A Day 50+",
                rawText = "Matched curated database entry for One A Day 50+",
                confidence = 0.99f,
                nutrients = mapOf(
                    MicronutrientType.VITAMIN_A to 2500.0,
                    MicronutrientType.VITAMIN_C to 135.0,
                    MicronutrientType.VITAMIN_D to 50.0,
                    MicronutrientType.VITAMIN_E to 30.0,
                    MicronutrientType.VITAMIN_K to 30.0,
                    MicronutrientType.VITAMIN_B1 to 1.5,
                    MicronutrientType.VITAMIN_B2 to 1.7,
                    MicronutrientType.VITAMIN_B3 to 20.0,
                    MicronutrientType.VITAMIN_B6 to 3.0,
                    MicronutrientType.VITAMIN_B9 to 400.0,
                    MicronutrientType.VITAMIN_B12 to 25.0,
                    MicronutrientType.VITAMIN_B7 to 30.0,
                    MicronutrientType.VITAMIN_B5 to 10.0,
                    MicronutrientType.CALCIUM to 200.0,
                    MicronutrientType.IRON to 8.0,
                    MicronutrientType.IODINE to 150.0,
                    MicronutrientType.MAGNESIUM to 50.0,
                    MicronutrientType.ZINC to 15.0,
                    MicronutrientType.SELENIUM to 105.0,
                    MicronutrientType.COPPER to 2.0,
                    MicronutrientType.MANGANESE to 2.0
                )
            )
        )

        override suspend fun lookup(barcode: String): ParsedSupplementLabel? =
            supplements[barcode]
    }

    /**
     * Provider using Open Food Facts public API.
     */
    private class OpenFoodFactsProvider(
        private val client: HttpClient
    ) : SupplementDataProvider {

        override val name: String = "OpenFoodFacts"

        override suspend fun lookup(barcode: String): ParsedSupplementLabel? {
            val response = client.get("https://world.openfoodfacts.org/api/v2/product/$barcode.json")
            if (!response.status.isSuccess()) return null

            val body: OpenFoodFactsResponse = response.body()
            val product = body.product ?: return null

            val nutrients = mutableMapOf<MicronutrientType, Double>()
            product.nutriments?.let { nutriments ->
                nutriments.vitaminA?.let { nutrients[MicronutrientType.VITAMIN_A] = it / 1_000_000.0 }
                nutriments.vitaminC?.let { nutrients[MicronutrientType.VITAMIN_C] = it }
                nutriments.vitaminD?.let { nutrients[MicronutrientType.VITAMIN_D] = it / 1_000_000.0 }
                nutriments.vitaminE?.let { nutrients[MicronutrientType.VITAMIN_E] = it }
                nutriments.vitaminK?.let { nutrients[MicronutrientType.VITAMIN_K] = it / 1_000_000.0 }
                nutriments.vitaminB1?.let { nutrients[MicronutrientType.VITAMIN_B1] = it }
                nutriments.vitaminB2?.let { nutrients[MicronutrientType.VITAMIN_B2] = it }
                nutriments.vitaminB3?.let { nutrients[MicronutrientType.VITAMIN_B3] = it }
                nutriments.vitaminB6?.let { nutrients[MicronutrientType.VITAMIN_B6] = it }
                nutriments.vitaminB9?.let { nutrients[MicronutrientType.VITAMIN_B9] = it / 1_000_000.0 }
                nutriments.vitaminB12?.let { nutrients[MicronutrientType.VITAMIN_B12] = it / 1_000_000.0 }
                nutriments.biotin?.let { nutrients[MicronutrientType.VITAMIN_B7] = it / 1_000_000.0 }
                nutriments.calcium?.let { nutrients[MicronutrientType.CALCIUM] = it }
                nutriments.iron?.let { nutrients[MicronutrientType.IRON] = it }
                nutriments.magnesium?.let { nutrients[MicronutrientType.MAGNESIUM] = it }
                nutriments.zinc?.let { nutrients[MicronutrientType.ZINC] = it }
                nutriments.iodine?.let { nutrients[MicronutrientType.IODINE] = it / 1_000_000.0 }
                nutriments.selenium?.let { nutrients[MicronutrientType.SELENIUM] = it / 1_000_000.0 }
                nutriments.copper?.let { nutrients[MicronutrientType.COPPER] = it / 1_000.0 }
                nutriments.manganese?.let { nutrients[MicronutrientType.MANGANESE] = it }
            }

            if (nutrients.isEmpty()) return null

            return ParsedSupplementLabel(
                name = product.productName ?: "Unknown Supplement",
                nutrients = nutrients,
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
    ) : SupplementDataProvider {

        override val name: String = "UPCItemDB"

        override suspend fun lookup(barcode: String): ParsedSupplementLabel? {
            if (apiKey.isBlank()) return null

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

            val nutrients = mutableMapOf<MicronutrientType, Double>()

            fun JsonObject.valueOf(key: String): Double? =
                this[key]?.jsonPrimitive?.doubleOrNull
                    ?: this[key]?.jsonPrimitive?.intOrNull?.toDouble()
                    ?: this[key]?.jsonPrimitive?.floatOrNull?.toDouble()

            nutrition.valueOf("vitamin_a")?.let { nutrients[MicronutrientType.VITAMIN_A] = it }
            nutrition.valueOf("vitamin_c")?.let { nutrients[MicronutrientType.VITAMIN_C] = it }
            nutrition.valueOf("vitamin_d")?.let { nutrients[MicronutrientType.VITAMIN_D] = it }
            nutrition.valueOf("vitamin_e")?.let { nutrients[MicronutrientType.VITAMIN_E] = it }
            nutrition.valueOf("vitamin_k")?.let { nutrients[MicronutrientType.VITAMIN_K] = it }
            nutrition.valueOf("thiamin")?.let { nutrients[MicronutrientType.VITAMIN_B1] = it }
            nutrition.valueOf("riboflavin")?.let { nutrients[MicronutrientType.VITAMIN_B2] = it }
            nutrition.valueOf("niacin")?.let { nutrients[MicronutrientType.VITAMIN_B3] = it }
            nutrition.valueOf("vitamin_b6")?.let { nutrients[MicronutrientType.VITAMIN_B6] = it }
            nutrition.valueOf("vitamin_b12")?.let { nutrients[MicronutrientType.VITAMIN_B12] = it }
            nutrition.valueOf("folate")?.let { nutrients[MicronutrientType.VITAMIN_B9] = it }
            nutrition.valueOf("biotin")?.let { nutrients[MicronutrientType.VITAMIN_B7] = it }
            nutrition.valueOf("pantothenic_acid")?.let { nutrients[MicronutrientType.VITAMIN_B5] = it }
            nutrition.valueOf("calcium")?.let { nutrients[MicronutrientType.CALCIUM] = it }
            nutrition.valueOf("iron")?.let { nutrients[MicronutrientType.IRON] = it }
            nutrition.valueOf("magnesium")?.let { nutrients[MicronutrientType.MAGNESIUM] = it }
            nutrition.valueOf("zinc")?.let { nutrients[MicronutrientType.ZINC] = it }
            nutrition.valueOf("selenium")?.let { nutrients[MicronutrientType.SELENIUM] = it }
            nutrition.valueOf("copper")?.let { nutrients[MicronutrientType.COPPER] = it }
            nutrition.valueOf("manganese")?.let { nutrients[MicronutrientType.MANGANESE] = it }

            if (nutrients.isEmpty()) return null

            val title = item["title"]?.jsonPrimitive?.contentOrNull
            return ParsedSupplementLabel(
                name = title ?: "UPCItemDB Supplement",
                nutrients = nutrients,
                rawText = "Barcode $barcode via UPCItemDB",
                confidence = 0.7f
            )
        }
    }

    /**
     * Provider using Nutritionix Track API.
     */
    private class NutritionixProvider(
        private val client: HttpClient,
        private val appId: String,
        private val apiKey: String
    ) : SupplementDataProvider {

        override val name: String = "Nutritionix"

        override suspend fun lookup(barcode: String): ParsedSupplementLabel? {
            if (appId.isBlank() || apiKey.isBlank()) return null

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

            val payload: NutritionixSearchResponse = response.body()
            val food = payload.items.firstOrNull() ?: return null

            val nutrients = mutableMapOf<MicronutrientType, Double>()
            for (entry in food.fullNutrients) {
                nutritionixAttrToMicronutrient[entry.attrId]?.let { target ->
                    val value = entry.value
                    // Values in Nutritionix are typically per serving in mg unless micro nutrient
                    nutrients[target] = value
                }
            }

            if (nutrients.isEmpty()) return null

            return ParsedSupplementLabel(
                name = food.foodName ?: "Nutritionix Supplement",
                nutrients = nutrients,
                rawText = "Barcode $barcode via Nutritionix",
                confidence = 0.75f
            )
        }

        private val nutritionixAttrToMicronutrient: Map<Int, MicronutrientType> = mapOf(
            320 to MicronutrientType.VITAMIN_A,
            401 to MicronutrientType.VITAMIN_C,
            324 to MicronutrientType.VITAMIN_D,
            323 to MicronutrientType.VITAMIN_E,
            430 to MicronutrientType.VITAMIN_K,
            404 to MicronutrientType.VITAMIN_B1,
            405 to MicronutrientType.VITAMIN_B2,
            406 to MicronutrientType.VITAMIN_B3,
            410 to MicronutrientType.VITAMIN_B5,
            415 to MicronutrientType.VITAMIN_B6,
            417 to MicronutrientType.VITAMIN_B9,
            454 to MicronutrientType.VITAMIN_B12,
            418 to MicronutrientType.VITAMIN_B7,
            301 to MicronutrientType.CALCIUM,
            303 to MicronutrientType.IRON,
            304 to MicronutrientType.MAGNESIUM,
            305 to MicronutrientType.MAGNESIUM,
            306 to MicronutrientType.POTASSIUM,
            307 to MicronutrientType.SODIUM,
            309 to MicronutrientType.ZINC,
            311 to MicronutrientType.SELENIUM,
            312 to MicronutrientType.COPPER,
            313 to MicronutrientType.MANGANESE
        )
    }

    /**
     * Provider using Barcode Lookup API (free tier available).
     */
    private class BarcodeLookupProvider(
        private val client: HttpClient
    ) : SupplementDataProvider {

        override val name: String = "BarcodeLookup"

        override suspend fun lookup(barcode: String): ParsedSupplementLabel? {
            val response = client.get("https://api.barcodelookup.com/v3/products") {
                url {
                    parameters.append("barcode", barcode)
                    parameters.append("key", "your_api_key_here") // Would need API key for full access
                }
            }

            if (!response.status.isSuccess()) return null

            val payload: BarcodeLookupResponse = response.body()
            val product = payload.products.firstOrNull() ?: return null

            val nutrients = mutableMapOf<MicronutrientType, Double>()

            // Parse nutrition facts if available
            product.nutritionFacts?.let { facts ->
                facts.forEach { fact ->
                    when (fact.name.lowercase()) {
                        "vitamin a" -> nutrients[MicronutrientType.VITAMIN_A] = fact.amount.toDoubleOrNull() ?: 0.0
                        "vitamin c" -> nutrients[MicronutrientType.VITAMIN_C] = fact.amount.toDoubleOrNull() ?: 0.0
                        "vitamin d" -> nutrients[MicronutrientType.VITAMIN_D] = fact.amount.toDoubleOrNull() ?: 0.0
                        "vitamin e" -> nutrients[MicronutrientType.VITAMIN_E] = fact.amount.toDoubleOrNull() ?: 0.0
                        "vitamin k" -> nutrients[MicronutrientType.VITAMIN_K] = fact.amount.toDoubleOrNull() ?: 0.0
                        "thiamin", "vitamin b1" -> nutrients[MicronutrientType.VITAMIN_B1] = fact.amount.toDoubleOrNull() ?: 0.0
                        "riboflavin", "vitamin b2" -> nutrients[MicronutrientType.VITAMIN_B2] = fact.amount.toDoubleOrNull() ?: 0.0
                        "niacin", "vitamin b3" -> nutrients[MicronutrientType.VITAMIN_B3] = fact.amount.toDoubleOrNull() ?: 0.0
                        "vitamin b6" -> nutrients[MicronutrientType.VITAMIN_B6] = fact.amount.toDoubleOrNull() ?: 0.0
                        "folate", "vitamin b9" -> nutrients[MicronutrientType.VITAMIN_B9] = fact.amount.toDoubleOrNull() ?: 0.0
                        "vitamin b12" -> nutrients[MicronutrientType.VITAMIN_B12] = fact.amount.toDoubleOrNull() ?: 0.0
                        "biotin", "vitamin b7" -> nutrients[MicronutrientType.VITAMIN_B7] = fact.amount.toDoubleOrNull() ?: 0.0
                        "pantothenic acid", "vitamin b5" -> nutrients[MicronutrientType.VITAMIN_B5] = fact.amount.toDoubleOrNull() ?: 0.0
                        "calcium" -> nutrients[MicronutrientType.CALCIUM] = fact.amount.toDoubleOrNull() ?: 0.0
                        "iron" -> nutrients[MicronutrientType.IRON] = fact.amount.toDoubleOrNull() ?: 0.0
                        "magnesium" -> nutrients[MicronutrientType.MAGNESIUM] = fact.amount.toDoubleOrNull() ?: 0.0
                        "zinc" -> nutrients[MicronutrientType.ZINC] = fact.amount.toDoubleOrNull() ?: 0.0
                        "selenium" -> nutrients[MicronutrientType.SELENIUM] = fact.amount.toDoubleOrNull() ?: 0.0
                        "copper" -> nutrients[MicronutrientType.COPPER] = fact.amount.toDoubleOrNull() ?: 0.0
                        "manganese" -> nutrients[MicronutrientType.MANGANESE] = fact.amount.toDoubleOrNull() ?: 0.0
                    }
                }
            }

            if (nutrients.isEmpty()) return null

            return ParsedSupplementLabel(
                name = product.title ?: "BarcodeLookup Supplement",
                nutrients = nutrients,
                rawText = "Barcode $barcode via BarcodeLookup",
                confidence = 0.8f
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
        val nutriments: Nutriments? = null
    )

    @Serializable
    private data class Nutriments(
        @SerialName("vitamin-a_100g") val vitaminA: Double? = null,
        @SerialName("vitamin-c_100g") val vitaminC: Double? = null,
        @SerialName("vitamin-d_100g") val vitaminD: Double? = null,
        @SerialName("vitamin-e_100g") val vitaminE: Double? = null,
        @SerialName("vitamin-k_100g") val vitaminK: Double? = null,
        @SerialName("vitamin-b1_100g") val vitaminB1: Double? = null,
        @SerialName("vitamin-b2_100g") val vitaminB2: Double? = null,
        @SerialName("vitamin-pp_100g") val vitaminB3: Double? = null,
        @SerialName("vitamin-b6_100g") val vitaminB6: Double? = null,
        @SerialName("vitamin-b9_100g") val vitaminB9: Double? = null,
        @SerialName("vitamin-b12_100g") val vitaminB12: Double? = null,
        @SerialName("biotin_100g") val biotin: Double? = null,
        @SerialName("calcium_100g") val calcium: Double? = null,
        @SerialName("iron_100g") val iron: Double? = null,
        @SerialName("magnesium_100g") val magnesium: Double? = null,
        @SerialName("zinc_100g") val zinc: Double? = null,
        @SerialName("iodine_100g") val iodine: Double? = null,
        @SerialName("selenium_100g") val selenium: Double? = null,
        @SerialName("copper_100g") val copper: Double? = null,
        @SerialName("manganese_100g") val manganese: Double? = null
    )

    @Serializable
    private data class NutritionixSearchResponse(
        @SerialName("foods") val items: List<NutritionixFood> = emptyList()
    )

    @Serializable
    private data class NutritionixFood(
        @SerialName("food_name") val foodName: String? = null,
        @SerialName("full_nutrients") val fullNutrients: List<NutritionixNutrient> = emptyList()
    )

    @Serializable
    private data class NutritionixNutrient(
        @SerialName("attr_id") val attrId: Int,
        val value: Double
    )

    @Serializable
    private data class BarcodeLookupResponse(
        val products: List<BarcodeLookupProduct> = emptyList()
    )

    @Serializable
    private data class BarcodeLookupProduct(
        val title: String? = null,
        val nutritionFacts: List<BarcodeLookupNutrition>? = null
    )

    @Serializable
    private data class BarcodeLookupNutrition(
        val name: String,
        val amount: String
    )
}

