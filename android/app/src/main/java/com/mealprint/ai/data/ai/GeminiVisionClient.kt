package com.coachie.app.data.ai

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.TextContent
import com.aallam.openai.api.image.ImageURL
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.mealprint.ai.data.Secrets
import com.mealprint.ai.data.SubscriptionService
import com.mealprint.ai.data.model.SubscriptionTier
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * OpenAI Vision Client for analyzing meal and supplement images.
 * Uses GPT-4o Vision model for reliable image analysis.
 * Includes caching and rate limiting to optimize costs.
 */
class GeminiVisionClient(private val context: Context) {

    private val openAI: OpenAI by lazy {
        OpenAI(token = Secrets.getOpenAIApiKey())
    }
    private val httpClient: HttpClient by lazy { HttpClient(CIO) }
    private val geminiFlashClient: GeminiFlashClient by lazy { GeminiFlashClient(context) }
    private val subscriptionService = SubscriptionService()

    // Encrypted shared preferences for secure caching
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "ai_cache_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Rate limiting: max 5 AI calls per user per hour
    private val RATE_LIMIT_MAX_CALLS = 5
    private val RATE_LIMIT_WINDOW_HOURS = 1L

    companion object {
        private const val TAG = "GeminiVisionClient"
    }

    /**
     * Check if user is within rate limits for AI calls
     */
    private fun checkRateLimit(userId: String): Boolean {
        val now = System.currentTimeMillis()
        val windowStart = now - (RATE_LIMIT_WINDOW_HOURS * 60 * 60 * 1000)

        // Get call history for this user
        val callHistoryKey = "rate_limit_calls_$userId"
        val callsJson = sharedPreferences.getString(callHistoryKey, "[]")
        val calls = callsJson?.let {
            try {
                it.split(",").filter { it.isNotEmpty() }.map { it.toLong() }
            } catch (e: Exception) {
                emptyList()
            }
        } ?: emptyList()

        // Filter calls within the time window
        val recentCalls = calls.filter { it > windowStart }

        return recentCalls.size < RATE_LIMIT_MAX_CALLS
    }

    /**
     * Record an AI call for rate limiting
     */
    private fun recordRateLimitCall(userId: String) {
        val now = System.currentTimeMillis()
        val callHistoryKey = "rate_limit_calls_$userId"
        val callsJson = sharedPreferences.getString(callHistoryKey, "[]")
        val calls = callsJson?.let {
            try {
                it.split(",").filter { it.isNotEmpty() }.map { it.toLong() }.toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
        } ?: mutableListOf()

        calls.add(now)

        // Keep only recent calls (within window + buffer)
        val cutoff = now - (RATE_LIMIT_WINDOW_HOURS * 60 * 60 * 1000 * 2)
        val filteredCalls = calls.filter { it > cutoff }

        sharedPreferences.edit()
            .putString(callHistoryKey, filteredCalls.joinToString(","))
            .apply()
    }

    /**
     * Generate a hash for image caching based on image content
     */
    private fun generateImageHash(imageUri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                val bytes = input.readBytes()
                val digest = MessageDigest.getInstance("SHA-256")
                val hashBytes = digest.digest(bytes)
                hashBytes.joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate image hash", e)
            null
        }
    }

    /**
     * Get cached analysis result if available
     */
    private fun getCachedAnalysis(cacheKey: String): MealAnalysis? {
        val cachedJson = sharedPreferences.getString(cacheKey, null)
        return cachedJson?.let {
            try {
                val parts = it.split("|")
                if (parts.size >= 6) {
                    MealAnalysis(
                        food = parts[0],
                        calories = parts[1].toIntOrNull() ?: 0,
                        proteinG = parts[2].toIntOrNull() ?: 0,
                        carbsG = parts[3].toIntOrNull() ?: 0,
                        fatG = parts[4].toIntOrNull() ?: 0,
                        confidence = parts[5].toFloatOrNull() ?: 0.0f
                    )
                } else null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse cached analysis", e)
                null
            }
        }
    }

    /**
     * Cache analysis result
     */
    private fun cacheAnalysis(cacheKey: String, analysis: MealAnalysis) {
        val cacheValue = "${analysis.food}|${analysis.calories}|${analysis.proteinG}|${analysis.carbsG}|${analysis.fatG}|${analysis.confidence}"
        sharedPreferences.edit()
            .putString(cacheKey, cacheValue)
            .apply()
    }

    /**
     * Load and compress image for API call
     */
    private fun loadAndCompressImage(imageUri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(imageUri)
                ?: return null

            // Decode with sampling to reduce size
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Calculate sample size to get image under 512x512
            options.inSampleSize = calculateInSampleSize(options, 512, 512)
            options.inJustDecodeBounds = false

            val newInputStream: InputStream = context.contentResolver.openInputStream(imageUri)
                ?: return null

            val bitmap = BitmapFactory.decodeStream(newInputStream, null, options)
            newInputStream.close()

            bitmap?.let { compressBitmap(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load and compress image", e)
            null
        }
    }

    /**
     * Calculate sample size for image decoding
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     * Compress bitmap to reduce file size
     */
    private fun compressBitmap(bitmap: Bitmap): Bitmap {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val compressedData = outputStream.toByteArray()

        // If still too large, compress more
        return if (compressedData.size > 100 * 1024) { // 100KB limit
            outputStream.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
            BitmapFactory.decodeByteArray(outputStream.toByteArray(), 0, outputStream.size())
        } else {
            bitmap
        }
    }

    /**
     * Convert bitmap to base64 string for API
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val imageBytes = outputStream.toByteArray()
        return Base64.encodeToString(imageBytes, Base64.NO_WRAP)
    }

    /**
     * Search for nutrition information for a menu item online.
     * Uses OpenAI to search the web for restaurant menu item nutrition facts.
     *
     * @param restaurantName Restaurant name (optional)
     * @param menuItemName Menu item name
     * @param menuDescription Menu description (optional)
     * @return Result containing updated MealAnalysis with nutrition from online search, or original analysis if search fails
     */
    suspend fun searchMenuItemNutrition(
        restaurantName: String?,
        menuItemName: String,
        menuDescription: String? = null
    ): Result<MealAnalysis> {
        return withContext(Dispatchers.IO) {
            try {
                val searchQuery = if (restaurantName != null) {
                    "$restaurantName $menuItemName nutrition facts calories protein carbs fat"
                } else {
                    "$menuItemName nutrition facts calories protein carbs fat"
                }

                Log.d(TAG, "Searching for menu item nutrition: $searchQuery")

                // Use OpenAI to search for nutrition information
                val prompt = """
                    Search for nutrition information for this menu item:
                    ${if (restaurantName != null) "Restaurant: $restaurantName\n" else ""}
                    Menu Item: $menuItemName
                    ${if (menuDescription != null) "Description: $menuDescription\n" else ""}
                    
                    Find the official nutrition facts including:
                    - Total calories
                    - Protein (grams)
                    - Carbohydrates (grams)
                    - Fat (grams)
                    
                    If you find the information, return ONLY valid JSON:
                    {
                        "calories": <number>,
                        "protein_g": <number>,
                        "carbs_g": <number>,
                        "fat_g": <number>,
                        "source": "<where you found it>",
                        "found": true
                    }
                    
                    If you cannot find reliable information, return:
                    {
                        "found": false,
                        "message": "Nutrition information not found online"
                    }
                """.trimIndent()

                val payload = JSONObject()
                    .put("model", "gpt-4o-mini")
                    .put("temperature", 0.1)
                    .put("max_tokens", 200)
                    .put("messages", JSONArray()
                        .put(JSONObject()
                            .put("role", "system")
                            .put("content", "You are a nutrition research assistant. Search the web for restaurant menu item nutrition facts and return accurate data."))
                        .put(JSONObject()
                            .put("role", "user")
                            .put("content", prompt)))

                val response: HttpResponse = httpClient.post("https://api.openai.com/v1/chat/completions") {
                    header("Authorization", "Bearer ${Secrets.getOpenAIApiKey()}")
                    header("Content-Type", "application/json")
                    setBody(payload.toString())
                }

                val bodyText = response.bodyAsText()
                val parsedJson = JSONObject(bodyText)
                val choices = parsedJson.optJSONArray("choices")
                val firstChoice = choices?.optJSONObject(0)
                val message = firstChoice?.optJSONObject("message")
                val text = message?.optString("content")

                if (text.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("No response from nutrition search"))
                }

                // Parse the response
                val jsonStart = text.indexOf('{')
                val jsonEnd = text.lastIndexOf('}') + 1
                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    val jsonText = text.substring(jsonStart, jsonEnd)
                    val json = JSONObject(jsonText)

                    if (json.optBoolean("found", false)) {
                        val nutritionAnalysis = MealAnalysis(
                            food = menuItemName,
                            calories = json.optInt("calories", 0),
                            proteinG = json.optInt("protein_g", 0),
                            carbsG = json.optInt("carbs_g", 0),
                            fatG = json.optInt("fat_g", 0),
                            confidence = 0.9f, // High confidence for official nutrition facts
                            isMenuItem = true,
                            restaurantName = restaurantName,
                            menuItemName = menuItemName,
                            menuDescription = menuDescription
                        )
                        Log.i(TAG, "Found nutrition info for menu item: $menuItemName - ${nutritionAnalysis.calories} cal")
                        return@withContext Result.success(nutritionAnalysis)
                    } else {
                        Log.w(TAG, "Nutrition information not found for menu item: $menuItemName")
                        return@withContext Result.failure(Exception(json.optString("message", "Nutrition information not found")))
                    }
                } else {
                    return@withContext Result.failure(Exception("Invalid response format from nutrition search"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error searching for menu item nutrition", e)
                return@withContext Result.failure(e)
            }
        }
    }

    /**
     * Analyze a meal image from URI and return structured meal analysis.
     * Includes caching and rate limiting for cost optimization.
     *
     * @param imageUri URI of the meal image
     * @param userId User ID for rate limiting and caching
     * @param skipCache If true, bypass cache and force fresh analysis
     * @return Result containing MealAnalysis or error
     */
    suspend fun analyzeMealImage(imageUri: Uri, userId: String = "", skipCache: Boolean = false): Result<MealAnalysis> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Processing meal image URI: $imageUri, userId: $userId, skipCache: $skipCache")

                // Check rate limits first
                if (!userId.isEmpty() && !checkRateLimit(userId)) {
                    Log.w(TAG, "Rate limit exceeded for user $userId")
                    return@withContext Result.failure(Exception("AI analysis limit reached. Please try again later. (Max 5 analyses per hour)"))
                }

                // Generate cache key from image hash (only if not skipping cache)
                val cacheKey = if (!skipCache) {
                    generateImageHash(imageUri)?.let { "meal_cache_$it" }
                } else null

                // Check cache first
                if (cacheKey != null && !skipCache) {
                    getCachedAnalysis(cacheKey)?.let { cached ->
                        Log.d(TAG, "Using cached meal analysis: ${cached.food}")
                        return@withContext Result.success(cached)
                    }
                }

                // Record the API call for rate limiting
                if (!userId.isEmpty()) {
                    recordRateLimitCall(userId)
                }

                // Load and compress the image
                val bitmap = loadAndCompressImage(imageUri)
                if (bitmap == null) {
                    Log.e(TAG, "Failed to load image from URI: $imageUri")
                    return@withContext Result.failure(Exception("Failed to load image"))
                }

                // Route based on subscription tier
                val tier = if (userId.isNotEmpty()) {
                    subscriptionService.getUserTier(userId)
                } else {
                    SubscriptionTier.FREE // Default to free if no userId
                }

                val mealAnalysis = if (tier == SubscriptionTier.PRO) {
                    // Pro users: Use OpenAI
                    val base64Image = bitmapToBase64(bitmap)
                    callVisionModel(base64Image)
                } else {
                    // Free users: Use Gemini Flash
                    Log.d(TAG, "Using Gemini Flash for free tier user")
                    geminiFlashClient.analyzeMealImage(imageUri, userId).getOrNull()
                }

                if (mealAnalysis == null) {
                    Log.e(TAG, "Vision model returned null analysis")
                    val errorMsg = if (tier == SubscriptionTier.PRO) {
                        "OpenAI Vision API returned empty response"
                    } else {
                        "Gemini Flash Vision API returned empty response"
                    }
                    return@withContext Result.failure(Exception(errorMsg))
                }
                Log.i(TAG, "Successfully parsed meal analysis: ${mealAnalysis.food}")

                // Cache the result if we have a cache key
                if (cacheKey != null) {
                    cacheAnalysis(cacheKey, mealAnalysis)
                    Log.d(TAG, "Cached meal analysis for key: $cacheKey")
                }

                Result.success(mealAnalysis)

            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing meal image", e)
                Log.e(TAG, "Error message: ${e.message}")
                Log.e(TAG, "Error cause: ${e.cause?.message}")

                val errorMessage = when {
                    e.message?.contains("API_KEY", ignoreCase = true) == true ->
                        "Invalid or missing OpenAI API key"
                    e.message?.contains("quota", ignoreCase = true) == true ->
                        "OpenAI API quota exceeded"
                    e.message?.contains("network", ignoreCase = true) == true ->
                        "Network error connecting to OpenAI API"
                    e.message?.contains("rate limit", ignoreCase = true) == true ->
                        "AI analysis rate limit exceeded. Please try again later."
                    else ->
                        "Failed to analyze meal image: ${e.message}"
                }

                Result.failure(Exception(errorMessage))
            }
        }
    }

    private suspend fun callVisionModel(base64Image: String): MealAnalysis? {
        return try {
            val prompt = """
                Analyze this meal photo. First, check if this appears to be a menu item (restaurant menu, menu board, or menu description visible in the image).
                
                If it's a menu item:
                1. Extract the restaurant name (if visible)
                2. Extract the exact menu item name
                3. Extract the menu description (if visible)
                4. Set is_menu_item to true
                5. Still estimate calories, protein (g), carbs (g), and fat (g) based on the visible food, but note that accurate nutrition can be searched online for this menu item.
                
                If it's NOT a menu item:
                1. Describe the main foods visible
                2. Estimate calories, protein (g), carbs (g), and fat (g) for the portion shown
                3. Set is_menu_item to false
                
                Do not default to chicken or salads—identify the actual proteins, sides, sauces, and toppings visible.
                
                Return ONLY valid JSON with fields:
                - food (string): food description
                - calories (int): estimated calories
                - protein_g (int): estimated protein in grams
                - carbs_g (int): estimated carbs in grams
                - fat_g (int): estimated fat in grams
                - confidence (0-1 float): confidence in the analysis
                - is_menu_item (boolean): true if this appears to be from a menu
                - restaurant_name (string, optional): restaurant name if menu item
                - menu_item_name (string, optional): exact menu item name if menu item
                - menu_description (string, optional): menu description if visible
            """.trimIndent()

            // Use HTTP client directly for vision API since SDK might not support multi-modal content properly
            val payload = JSONObject()
                .put("model", "gpt-4o-mini")
                .put("temperature", 0.2)
                .put("max_tokens", 250)
                .put("messages", JSONArray()
                    .put(JSONObject()
                        .put("role", "system")
                        .put("content", "You are a registered dietitian. Identify foods in meal photos and estimate nutrition solely based on the visible meal."))
                    .put(JSONObject()
                        .put("role", "user")
                        .put("content", JSONArray()
                            .put(JSONObject()
                                .put("type", "text")
                                .put("text", prompt))
                            .put(JSONObject()
                                .put("type", "image_url")
                                .put("image_url", JSONObject()
                                    .put("url", "data:image/jpeg;base64,$base64Image"))))))

            Log.d(TAG, "Calling OpenAI chat completions API with vision support")
            val response: HttpResponse = httpClient.post("https://api.openai.com/v1/chat/completions") {
                header("Authorization", "Bearer ${Secrets.getOpenAIApiKey()}")
                header("Content-Type", "application/json")
                setBody(payload.toString())
            }

            val bodyText = response.bodyAsText()
            Log.d(TAG, "Vision API raw response: $bodyText")

            val parsedJson = JSONObject(bodyText)
            val choices = parsedJson.optJSONArray("choices")
            val firstChoice = choices?.optJSONObject(0)
            val message = firstChoice?.optJSONObject("message")
            val text = message?.optString("content")

            if (text.isNullOrBlank()) {
                Log.e(TAG, "OpenAI Vision API returned empty response")
                return null
            }

            Log.d(TAG, "OpenAI Vision API response: $text")
            parseMealAnalysis(text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call vision model", e)
            Log.e(TAG, "Error details: ${e.message}", e)
            null
        }
    }

    private fun extractTextFromVisionResponse(json: JSONObject): String? {
        json.optJSONArray("output_text")?.let { arr ->
            if (arr.length() > 0) return arr.optString(0)
        }

        val outputArray = json.optJSONArray("output") ?: return null
        for (i in 0 until outputArray.length()) {
            val contentArray = outputArray.optJSONObject(i)?.optJSONArray("content") ?: continue
            for (j in 0 until contentArray.length()) {
                val contentObj = contentArray.optJSONObject(j) ?: continue
                val type = contentObj.optString("type")
                val text = contentObj.optString("text")
                if (!text.isNullOrBlank() && (type.equals("text", true) || type.equals("output_text", true))) {
                    return text
                }
            }
        }
        return null
    }

    /**
     * Analyze a supplement image from URI and return structured supplement analysis.
     *
     * @param imageUri URI of the supplement image
     * @param userId User ID for subscription tier check (optional, defaults to free tier)
     * @return Result containing SupplementAnalysis or error
     */
    suspend fun analyzeSupplementImage(imageUri: Uri, userId: String = ""): Result<SupplementAnalysis> {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("GeminiVisionClient", "Processing supplement image URI: $imageUri")

                // First try OCR to extract the label exactly as printed.
                val ocrResult = SupplementLabelParser.parse(context, imageUri)
                if (ocrResult != null) {
                    android.util.Log.i(
                        "GeminiVisionClient",
                        "OCR extracted ${ocrResult.nutrients.size} nutrients directly from the label"
                    )
                    return@withContext Result.success(
                        SupplementAnalysis(
                            supplementName = ocrResult.name ?: "Unknown Supplement",
                            nutrients = ocrResult.nutrients,
                            confidence = if (ocrResult.nutrients.isNotEmpty()) 0.95f else 0.6f,
                            rawLabelText = ocrResult.rawText
                        )
                    )
                }

                // Route based on subscription tier
                val tier = if (userId.isNotEmpty()) {
                    subscriptionService.getUserTier(userId)
                } else {
                    SubscriptionTier.FREE // Default to free if no userId
                }
                
                if (tier == SubscriptionTier.PRO) {
                    // Pro users: Use OpenAI (existing code)
                    // Fallback: create an AI request for difficult labels.
                    val prompt = """
                    You are analyzing a photograph of a supplement nutrition facts label. Your task is to READ THE EXACT TEXT visible in the image and extract ONLY what is actually written on the label.

                    CRITICAL INSTRUCTIONS - FOLLOW THESE EXACTLY:
                    1. DO NOT GUESS, ESTIMATE, or ASSUME any values that are not clearly visible in the image
                    2. Only extract nutrients that are actually listed on the label you can see
                    3. Use the exact amounts, units, and names as written on the label
                    4. If a nutrient is not visible on the label, DO NOT include it
                    5. Look specifically for the "Supplement Facts" or nutrition information panel
                    6. Read the serving size and amounts per serving exactly as shown

                    Common mistakes to avoid:
                    - Do not assume this is a multivitamin unless it explicitly says so
                    - Do not add typical vitamin amounts if they're not on the label
                    - Do not convert units unless the label shows both
                    - Do not include nutrients that are not actually listed

                    Only extract nutrients from the "Supplement Facts" table that are clearly visible:

                    VITAMINS (convert units as needed):
                    - Vitamin A (IU → mcg: multiply by 0.3)
                    - Vitamin D (IU → mcg: multiply by 0.025)
                    - Vitamin E (mg or IU)
                    - Vitamin K (mcg)
                    - Vitamin C (mg)
                    - Vitamin B1 (Thiamine) (mg)
                    - Vitamin B2 (Riboflavin) (mg)
                    - Vitamin B3 (Niacin) (mg)
                    - Vitamin B5 (Pantothenic Acid) (mg)
                    - Vitamin B6 (mg)
                    - Vitamin B7 (Biotin) (mcg)
                    - Vitamin B9 (Folate) (mcg)
                    - Vitamin B12 (mcg)

                    MINERALS:
                    - Calcium (mg)
                    - Magnesium (mg)
                    - Potassium (mg)
                    - Sodium (mg)
                    - Iron (mg)
                    - Zinc (mg)
                    - Iodine (mcg)
                    - Selenium (mcg)
                    - Phosphorus (mg)
                    - Manganese (mg)
                    - Copper (mcg)

                    OTHER NUTRIENTS (if present):
                    - Protein (g)
                    - Omega-3/EPA/DHA (mg)
                    - Creatine (g)
                    - CoQ10 (mg)
                    - etc.

                    Convert units appropriately:
                    - IU to mcg: Vitamin D (÷40), Vitamin A (÷3.33), Vitamin E (÷1.5 for natural)
                    - All measurements should match the serving size listed

                    Return ONLY valid JSON in this exact format:
                    {
                      "supplementName": "exact name as shown on label (or 'Unknown Supplement' if not visible)",
                      "nutrients": {
                        "EXACT_NUTRIENT_NAME_AS_SHOWN": EXACT_AMOUNT_AS_SHOWN,
                        "ANOTHER_EXACT_NUTRIENT": EXACT_AMOUNT,
                        // Only include nutrients that are actually visible on the photographed label
                      }
                    }

                    If you cannot clearly read any nutrition information from the image, return:
                    {
                      "supplementName": "Unreadable Label",
                      "nutrients": {}
                    }
                """.trimIndent()

                val chatCompletionRequest = ChatCompletionRequest(
                    model = ModelId("gpt-3.5-turbo"),
                    messages = listOf(
                        ChatMessage(
                            role = ChatRole.System,
                            content = "You are a supplement expert. Provide reasonable nutrient estimates for common supplements."
                        ),
                        ChatMessage(
                            role = ChatRole.User,
                            content = prompt
                        )
                    ),
                    temperature = 0.1,
                    maxTokens = 400
                )

                android.util.Log.d("GeminiVisionClient", "Sending supplement analysis request to OpenAI Vision API")

                val response = openAI.chatCompletion(chatCompletionRequest)
                val text = response.choices.firstOrNull()?.message?.content

                if (text.isNullOrBlank()) {
                    android.util.Log.e("GeminiVisionClient", "Empty response from OpenAI Vision API")
                    return@withContext Result.failure(Exception("OpenAI Vision API returned empty response"))
                }

                android.util.Log.d("GeminiVisionClient", "OpenAI Vision supplement response: $text")

                // Parse JSON response
                val supplementAnalysis = parseSupplementAnalysis(text)
                android.util.Log.i("GeminiVisionClient", "Successfully parsed supplement analysis: ${supplementAnalysis.supplementName}")
                Result.success(supplementAnalysis)
                } else {
                    // Free users: Use Gemini Flash
                    android.util.Log.d("GeminiVisionClient", "Using Gemini Flash for free tier user")
                    geminiFlashClient.analyzeSupplementImage(imageUri, userId)
                }

            } catch (e: Exception) {
                android.util.Log.e("GeminiVisionClient", "Error analyzing supplement image", e)
                android.util.Log.e("GeminiVisionClient", "Error message: ${e.message}")
                android.util.Log.e("GeminiVisionClient", "Error cause: ${e.cause?.message}")

                val errorMessage = when {
                    e.message?.contains("API_KEY", ignoreCase = true) == true ->
                        "Invalid or missing OpenAI API key"
                    e.message?.contains("quota", ignoreCase = true) == true ->
                        "OpenAI API quota exceeded"
                    e.message?.contains("network", ignoreCase = true) == true ->
                        "Network error connecting to OpenAI API"
                    else ->
                        "Failed to analyze supplement image: ${e.message}"
                }

                Result.failure(Exception(errorMessage))
            }
        }
    }


    /**
     * Parse meal analysis from OpenAI response text
     */
    private fun parseMealAnalysis(text: String): MealAnalysis {
        return try {
            // Try to extract JSON from the response
            val jsonStart = text.indexOf('{')
            val jsonEnd = text.lastIndexOf('}') + 1
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonText = text.substring(jsonStart, jsonEnd)
                val json = org.json.JSONObject(jsonText)

                MealAnalysis(
                    food = json.optString("food", "Unknown food"),
                    calories = json.optInt("calories", 0),
                    proteinG = json.optInt("protein_g", 0),
                    carbsG = json.optInt("carbs_g", 0),
                    fatG = json.optInt("fat_g", 0),
                    confidence = json.optDouble("confidence", 0.0).toFloat(),
                    isMenuItem = json.optBoolean("is_menu_item", false),
                    restaurantName = json.optString("restaurant_name").takeIf { it.isNotBlank() },
                    menuItemName = json.optString("menu_item_name").takeIf { it.isNotBlank() },
                    menuDescription = json.optString("menu_description").takeIf { it.isNotBlank() }
                )
            } else {
                // Fallback: try to extract info from text
                parseMealAnalysisFromText(text)
            }
        } catch (e: Exception) {
            android.util.Log.e("GeminiVisionClient", "Error parsing meal analysis", e)
            parseMealAnalysisFromText(text)
        }
    }

    /**
     * Fallback parser for meal analysis when JSON parsing fails
     */
    private fun parseMealAnalysisFromText(text: String): MealAnalysis {
        // Try to extract food name (first line or before comma)
        val food = text.split("\n").firstOrNull()?.split(",")?.firstOrNull()?.trim()
            ?: text.split(",").firstOrNull()?.trim() ?: "Unknown food"

        // Try to extract numbers
        val calories = extractNumber(text, "calories", "cal")
        val protein = extractNumber(text, "protein")
        val carbs = extractNumber(text, "carbs", "carbohydrates")
        val fat = extractNumber(text, "fat")

        return MealAnalysis(
            food = food,
            isMenuItem = false,
            restaurantName = null,
            menuItemName = null,
            menuDescription = null,
            calories = calories,
            proteinG = protein,
            carbsG = carbs,
            fatG = fat,
            confidence = 0.5f
        )
    }

    /**
     * Parse supplement analysis from OpenAI response text
     */
    private fun parseSupplementAnalysis(text: String): SupplementAnalysis {
        return try {
            // Try to extract JSON from the response
            val jsonStart = text.indexOf('{')
            val jsonEnd = text.lastIndexOf('}') + 1
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonText = text.substring(jsonStart, jsonEnd)
                val json = org.json.JSONObject(jsonText)

                val supplementName = json.optString("supplementName", json.optString("supplement_name", "Unknown supplement"))
                val nutrientsJson = json.optJSONObject("nutrients")
                val confidence = json.optDouble("confidence", 0.8).toFloat() // Default higher confidence for explicit readings

                val nutrients = mutableMapOf<com.coachie.app.data.model.MicronutrientType, Double>()
                if (nutrientsJson != null) {
                    // Parse nutrients from JSON
                    nutrientsJson.keys().forEach { key ->
                        val amount = nutrientsJson.optDouble(key, Double.NaN)
                        val mappedType = MicronutrientNameMapper.identify(key)
                            ?: MicronutrientNameMapper.identify(key.replace("_", " "))

                        if (!amount.isNaN() && amount > 0 && mappedType != null) {
                            nutrients[mappedType] = amount
                        } else if (mappedType == null) {
                            android.util.Log.w("GeminiVisionClient", "Skipping unknown nutrient key from AI: $key")
                        }
                    }
                }

                SupplementAnalysis(
                    supplementName = supplementName,
                    nutrients = nutrients,
                    confidence = confidence,
                    rawLabelText = text
                )
            } else {
                // Fallback: try to extract info from text
                parseSupplementAnalysisFromText(text)
            }
        } catch (e: Exception) {
            android.util.Log.e("GeminiVisionClient", "Error parsing supplement analysis", e)
            parseSupplementAnalysisFromText(text)
        }
    }

    /**
     * Fallback parser for supplement analysis
     */
    private fun parseSupplementAnalysisFromText(text: String): SupplementAnalysis {
        // Try to extract supplement name (first line or before comma)
        val supplementName = text.split("\n").firstOrNull()?.split(",")?.firstOrNull()?.trim()
            ?: text.split(",").firstOrNull()?.trim() ?: "Unknown supplement"

        // For text parsing, we can't reliably extract nutrients, so return empty
        return SupplementAnalysis(
            supplementName = supplementName,
            nutrients = emptyMap(),
            confidence = 0.3f,
            rawLabelText = text
        )
    }

    /**
     * Extract a number from text based on keywords
     */
    private fun extractNumber(text: String, vararg keywords: String): Int {
        val lowerText = text.lowercase()
        for (keyword in keywords) {
            val index = lowerText.indexOf(keyword.lowercase())
            if (index >= 0) {
                val substring = text.substring(index)
                val regex = Regex("""(\d+)""")
                val match = regex.find(substring)
                return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
            }
        }
        return 0
    }
}

/**
 * Data class for meal analysis results
 */
data class MealAnalysis(
    val food: String,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val sugarG: Int = 0,
    val addedSugarG: Int = 0,
    val confidence: Float,
    val isMenuItem: Boolean = false,
    val restaurantName: String? = null,
    val menuItemName: String? = null,
    val menuDescription: String? = null
) {
    /**
     * Get formatted summary string
     */
    val summary: String
        get() = if (isMenuItem && menuItemName != null) {
            "Menu Item: $menuItemName${restaurantName?.let { " from $it" } ?: ""}, ~$calories cal, ${proteinG}g protein"
        } else {
            "Detected: $food, ~$calories cal, ${proteinG}g protein"
        }
}

/**
 * Data class for supplement analysis results
 */
data class SupplementAnalysis(
    val supplementName: String,
    val nutrients: Map<com.coachie.app.data.model.MicronutrientType, Double>,
    val confidence: Float,
    val rawLabelText: String? = null,
    val labelImagePath: String? = null
) {
    /**
     * Get formatted summary string.
     */
    val summary: String
        get() {
            val nutrientSummary = if (nutrients.isNotEmpty()) {
                nutrients.entries.take(3).joinToString(", ") { (type, amount) ->
                    "${type.displayName}: ${amount}${type.unit.displaySuffix}"
                }
            } else {
                "No nutrients detected"
            }
            return "Detected: $supplementName - $nutrientSummary"
        }

    /**
     * Check if analysis is valid (not unknown and has nutrients).
     */
    val isValid: Boolean
        get() = supplementName.lowercase() != "unknown supplement" &&
                supplementName.lowercase() != "unknown" &&
                confidence > 0.3f
}
