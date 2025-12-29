package com.coachie.app.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.mealprint.ai.data.Secrets
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth

/**
 * Gemini Flash 1.5 client for free tier users.
 * Provides text generation and vision capabilities at 5-10x lower cost than OpenAI.
 * 
 * API Endpoint: https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent
 * Using Gemini 2.5 Flash - recommended stable model (replaces deprecated 1.5 and 2.0 series)
 */
class GeminiFlashClient(private val context: Context) {

    private val httpClient: HttpClient by lazy { 
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000 // 120 seconds (2 minutes) for large prompts
                connectTimeoutMillis = 30_000 // 30 seconds to connect
                socketTimeoutMillis = 120_000 // 120 seconds for socket operations
            }
        }
    }
    private val apiKey: String by lazy { Secrets.getGeminiApiKey() }
    // Use gemini-2.5-flash - recommended stable model (released June 2025, supported until June 2026)
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

    companion object {
        private const val TAG = "GeminiFlashClient"
        
        /**
         * Log Gemini API usage to Firestore for tracking
         */
        private fun logGeminiUsage(
            userId: String, // Passed userId (may be empty/null)
            source: String,
            promptTokenCount: Int,
            candidatesTokenCount: Int,
            totalTokenCount: Int
        ) {
            try {
                // CRITICAL: Always get real userId from FirebaseAuth if user is authenticated
                // Only use "unknown" if user is truly not authenticated
                val authenticatedUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                val userIdToLog = when {
                    !authenticatedUserId.isNullOrBlank() -> authenticatedUserId // Always prefer authenticated user
                    !userId.isNullOrBlank() && userId != "unknown" -> userId // Use passed userId if valid
                    else -> {
                        Log.w(TAG, "⚠️ No authenticated user found - logging as 'unknown' (userId=$userId)")
                        "unknown" // Last resort: user is not authenticated
                    }
                }
                
                val dateStr = java.time.LocalDate.now().toString()
                // Gemini Flash pricing: $0.075/$0.30 per 1M tokens (input/output)
                val inputCost = (promptTokenCount / 1_000_000.0) * 0.075
                val outputCost = (candidatesTokenCount / 1_000_000.0) * 0.30
                val estimatedCostUsd = inputCost + outputCost
                
                val event = hashMapOf(
                    "userId" to userIdToLog,
                    "timestamp" to System.currentTimeMillis(),
                    "source" to source,
                    "model" to "gemini-2.5-flash",
                    "promptTokens" to promptTokenCount,
                    "completionTokens" to candidatesTokenCount,
                    "totalTokens" to totalTokenCount,
                    "estimatedCostUsd" to estimatedCostUsd
                )
                
                Log.d(TAG, "📊 ATTEMPTING TO LOG: userId=$userIdToLog (passed=$userId, auth=$authenticatedUserId), date=$dateStr, source=$source, tokens=$totalTokenCount")
                
                val db = FirebaseFirestore.getInstance()
                val docRef = db.collection("logs").document(userIdToLog)
                    .collection("daily").document(dateStr)
                    .collection("ai_usage")
                
                docRef.add(event)
                    .addOnSuccessListener { docRef ->
                        Log.d(TAG, "✅✅✅ SUCCESSFULLY LOGGED Gemini usage: userId=$userIdToLog, $totalTokenCount tokens, \$${String.format("%.6f", estimatedCostUsd)}, docId=${docRef.id}")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌❌❌ FAILED TO LOG Gemini usage ❌❌❌", e)
                        Log.e(TAG, "   userId=$userIdToLog (passed=$userId, auth=$authenticatedUserId), date=$dateStr, source=$source")
                        Log.e(TAG, "   Error: ${e.message}, Code: ${e.javaClass.simpleName}")
                        // CRITICAL: Log to console so we can see failures
                        android.util.Log.e(TAG, "FIREBASE WRITE FAILED - CHECK PERMISSIONS AND NETWORK", e)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "❌❌❌ EXCEPTION IN logGeminiUsage ❌❌❌", e)
                Log.e(TAG, "   userId=$userId, source=$source")
                Log.e(TAG, "   Exception: ${e.message}, Type: ${e.javaClass.simpleName}")
                // Re-throw to surface the error
                throw e
            }
        }
    }

    /**
     * Generate text response from a prompt (chat, recommendations, insights)
     * @param userId Optional user ID for usage tracking
     * @param source Source identifier for usage tracking (e.g., "chat", "insight")
     */
    suspend fun generateText(
        prompt: String,
        systemPrompt: String? = null,
        temperature: Double = 0.7,
        maxTokens: Int = 500,
        userId: String? = null,
        source: String = "gemini_text"
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val fullPrompt = if (systemPrompt != null) {
                    "$systemPrompt\n\n$prompt"
                } else {
                    prompt
                }

                val payload = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", fullPrompt)
                                })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", temperature)
                        put("maxOutputTokens", maxTokens)
                        // CRITICAL: Disable thinking tokens to ensure all budget goes to output
                        // Gemini 2.5 Flash uses thinking tokens for internal reasoning, which can consume
                        // most of the token budget (e.g., 7620 thinking tokens vs 567 output tokens)
                        put("thinkingConfig", JSONObject().apply {
                            put("thinkingBudget", 0)
                        })
                        // CRITICAL: Also set responseModalities to ensure we get full text output
                        put("responseModalities", JSONArray().apply {
                            put("TEXT")
                        })
                    })
                }
                
                Log.d(TAG, "Gemini Flash request: maxOutputTokens=$maxTokens, promptLength=${fullPrompt.length}")

                val response: HttpResponse = httpClient.post {
                    url("$baseUrl?key=$apiKey")
                    contentType(ContentType.Application.Json)
                    setBody(payload.toString())
                }

                val bodyText = response.bodyAsText()
                Log.d(TAG, "Gemini Flash response: $bodyText")

                val parsedJson = JSONObject(bodyText)
                
                // Extract usage metadata for tracking
                val usageMetadata = parsedJson.optJSONObject("usageMetadata")
                if (usageMetadata != null) {
                    val promptTokenCount = usageMetadata.optInt("promptTokenCount", 0)
                    val candidatesTokenCount = usageMetadata.optInt("candidatesTokenCount", 0)
                    val totalTokenCount = usageMetadata.optInt("totalTokenCount", 0)
                    Log.d(TAG, "Gemini usage: prompt=$promptTokenCount, candidates=$candidatesTokenCount, total=$totalTokenCount")
                    
                    // ALWAYS log usage - logGeminiUsage will get real userId from FirebaseAuth if authenticated
                    logGeminiUsage(userId ?: "", source, promptTokenCount, candidatesTokenCount, totalTokenCount)
                } else {
                    // Even without usageMetadata, estimate and log usage to ensure tracking
                    val estimatedPromptTokens = prompt.length / 4
                    val estimatedCompletionTokens = 100 // Conservative estimate
                    val estimatedTotalTokens = estimatedPromptTokens + estimatedCompletionTokens
                    Log.w(TAG, "⚠️ No usageMetadata in Gemini response - using estimated tokens")
                    logGeminiUsage(userId ?: "", source, estimatedPromptTokens, estimatedCompletionTokens, estimatedTotalTokens)
                }
                
                val candidates = parsedJson.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                
                // Check finishReason to detect truncated responses
                val finishReason = firstCandidate?.optString("finishReason")
                if (finishReason == "MAX_TOKENS") {
                    Log.e(TAG, "❌❌❌ Gemini response was truncated (MAX_TOKENS) ❌❌❌")
                    Log.e(TAG, "   Requested maxOutputTokens=$maxTokens")
                    Log.e(TAG, "   Actual candidatesTokenCount=${usageMetadata?.optInt("candidatesTokenCount", 0)}")
                    Log.e(TAG, "   This indicates the API is not respecting maxOutputTokens parameter")
                    // Still try to return what we got, but the caller should handle incomplete JSON
                }
                
                val content = firstCandidate?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val firstPart = parts?.optJSONObject(0)
                val text = firstPart?.optString("text")

                if (text.isNullOrBlank()) {
                    Log.e(TAG, "Empty response from Gemini Flash")
                    return@withContext Result.failure(Exception("Empty response from Gemini Flash"))
                }

                // If response was truncated, fail immediately - don't try to parse incomplete JSON
                val trimmedText = text.trim()
                if (finishReason == "MAX_TOKENS") {
                    Log.e(TAG, "Response was truncated (MAX_TOKENS). Text length: ${trimmedText.length} chars")
                    Log.e(TAG, "Response preview: ${trimmedText.take(200)}...")
                    return@withContext Result.failure(Exception("Response was truncated due to token limit (MAX_TOKENS). Requested $maxTokens tokens but got ${usageMetadata?.optInt("candidatesTokenCount", 0)}. This may be a model limitation or API issue."))
                }

                Result.success(trimmedText)
            } catch (e: Exception) {
                Log.e(TAG, "Error generating text with Gemini Flash", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Analyze meal image using Gemini Flash vision
     * @param userId Optional user ID for usage tracking
     */
    suspend fun analyzeMealImage(imageUri: Uri, userId: String? = null): Result<MealAnalysis> {
        return withContext(Dispatchers.IO) {
            try {
                val bitmap = loadAndCompressImage(imageUri)
                if (bitmap == null) {
                    Log.e(TAG, "Failed to load image from URI: $imageUri")
                    return@withContext Result.failure(Exception("Failed to load image"))
                }

                val base64Image = bitmapToBase64(bitmap)

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

                val payload = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                // Text part
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                                // Image part
                                put(JSONObject().apply {
                                    put("inlineData", JSONObject().apply {
                                        put("mimeType", "image/jpeg")
                                        put("data", base64Image)
                                    })
                                })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.2)
                        put("maxOutputTokens", 250)
                    })
                }

                val response: HttpResponse = httpClient.post {
                    url("$baseUrl?key=$apiKey")
                    contentType(ContentType.Application.Json)
                    setBody(payload.toString())
                }

                val bodyText = response.bodyAsText()
                Log.d(TAG, "Gemini Flash vision response: $bodyText")

                val parsedJson = JSONObject(bodyText)
                
                // Extract usage metadata for tracking
                val usageMetadata = parsedJson.optJSONObject("usageMetadata")
                if (usageMetadata != null) {
                    val promptTokenCount = usageMetadata.optInt("promptTokenCount", 0)
                    val candidatesTokenCount = usageMetadata.optInt("candidatesTokenCount", 0)
                    val totalTokenCount = usageMetadata.optInt("totalTokenCount", 0)
                    Log.d(TAG, "Gemini vision usage: prompt=$promptTokenCount, candidates=$candidatesTokenCount, total=$totalTokenCount")
                    
                    // Always log usage if we have metadata and userId
                    // ALWAYS log usage - logGeminiUsage will get real userId from FirebaseAuth if authenticated
                    logGeminiUsage(userId ?: "", "meal_photo_analysis", promptTokenCount, candidatesTokenCount, totalTokenCount)
                } else {
                    // Even without usageMetadata, estimate and log usage to ensure tracking
                    val estimatedPromptTokens = 500 // Conservative estimate for image + prompt
                    val estimatedCompletionTokens = 200 // Conservative estimate for vision response
                    val estimatedTotalTokens = estimatedPromptTokens + estimatedCompletionTokens
                    Log.w(TAG, "⚠️ No usageMetadata in Gemini vision response - using estimated tokens")
                    logGeminiUsage(userId ?: "", "meal_photo_analysis", estimatedPromptTokens, estimatedCompletionTokens, estimatedTotalTokens)
                }
                
                val candidates = parsedJson.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val content = firstCandidate?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val firstPart = parts?.optJSONObject(0)
                val text = firstPart?.optString("text")

                if (text.isNullOrBlank()) {
                    Log.e(TAG, "Empty response from Gemini Flash vision")
                    return@withContext Result.failure(Exception("Empty response from Gemini Flash vision"))
                }

                val analysis = parseMealAnalysis(text)
                Result.success(analysis)
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing meal image with Gemini Flash", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Analyze supplement image using Gemini Flash vision
     * @param userId Optional user ID for usage tracking
     */
    suspend fun analyzeSupplementImage(imageUri: Uri, userId: String? = null): Result<SupplementAnalysis> {
        return withContext(Dispatchers.IO) {
            try {
                val bitmap = loadAndCompressImage(imageUri)
                if (bitmap == null) {
                    Log.e(TAG, "Failed to load image from URI: $imageUri")
                    return@withContext Result.failure(Exception("Failed to load image"))
                }

                val base64Image = bitmapToBase64(bitmap)

                val prompt = """
                    You are analyzing a photograph of a supplement nutrition facts label. Your task is to READ THE EXACT TEXT visible in the image and extract ONLY what is actually written on the label.

                    CRITICAL INSTRUCTIONS - FOLLOW THESE EXACTLY:
                    1. DO NOT GUESS, ESTIMATE, or ASSUME any values that are not clearly visible in the image
                    2. Only extract nutrients that are actually listed on the label you can see
                    3. Use the exact amounts, units, and names as written on the label
                    4. If a nutrient is not visible on the label, DO NOT include it
                    5. Look specifically for the "Supplement Facts" or nutrition information panel
                    6. Read the serving size and amounts per serving exactly as shown

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

                val payload = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                                put(JSONObject().apply {
                                    put("inlineData", JSONObject().apply {
                                        put("mimeType", "image/jpeg")
                                        put("data", base64Image)
                                    })
                                })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.1)
                        put("maxOutputTokens", 400)
                    })
                }

                val response: HttpResponse = httpClient.post {
                    url("$baseUrl?key=$apiKey")
                    contentType(ContentType.Application.Json)
                    setBody(payload.toString())
                }

                val bodyText = response.bodyAsText()
                Log.d(TAG, "Gemini Flash supplement response: $bodyText")

                val parsedJson = JSONObject(bodyText)
                
                // Extract usage metadata for tracking
                val usageMetadata = parsedJson.optJSONObject("usageMetadata")
                if (usageMetadata != null) {
                    val promptTokenCount = usageMetadata.optInt("promptTokenCount", 0)
                    val candidatesTokenCount = usageMetadata.optInt("candidatesTokenCount", 0)
                    val totalTokenCount = usageMetadata.optInt("totalTokenCount", 0)
                    Log.d(TAG, "Gemini supplement usage: prompt=$promptTokenCount, candidates=$candidatesTokenCount, total=$totalTokenCount")
                    
                    // Always log usage if we have metadata and userId
                    // ALWAYS log usage - logGeminiUsage will get real userId from FirebaseAuth if authenticated
                    logGeminiUsage(userId ?: "", "supplement_photo_analysis", promptTokenCount, candidatesTokenCount, totalTokenCount)
                } else {
                    // Even without usageMetadata, estimate and log usage to ensure tracking
                    val estimatedPromptTokens = 500 // Conservative estimate for image + prompt
                    val estimatedCompletionTokens = 200 // Conservative estimate for vision response
                    val estimatedTotalTokens = estimatedPromptTokens + estimatedCompletionTokens
                    Log.w(TAG, "⚠️ No usageMetadata in Gemini supplement response - using estimated tokens")
                    logGeminiUsage(userId ?: "", "supplement_photo_analysis", estimatedPromptTokens, estimatedCompletionTokens, estimatedTotalTokens)
                }
                
                val candidates = parsedJson.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val content = firstCandidate?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val firstPart = parts?.optJSONObject(0)
                val text = firstPart?.optString("text")

                if (text.isNullOrBlank()) {
                    Log.e(TAG, "Empty response from Gemini Flash supplement vision")
                    return@withContext Result.failure(Exception("Empty response from Gemini Flash supplement vision"))
                }

                val analysis = parseSupplementAnalysis(text)
                Result.success(analysis)
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing supplement image with Gemini Flash", e)
                Result.failure(e)
            }
        }
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    private fun loadAndCompressImage(imageUri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(imageUri)
                ?: return null

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

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

    private fun compressBitmap(bitmap: Bitmap): Bitmap {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val compressedData = outputStream.toByteArray()

        return if (compressedData.size > 100 * 1024) {
            outputStream.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
            BitmapFactory.decodeByteArray(outputStream.toByteArray(), 0, outputStream.size())
        } else {
            bitmap
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val imageBytes = outputStream.toByteArray()
        return Base64.encodeToString(imageBytes, Base64.NO_WRAP)
    }

    private fun parseMealAnalysis(text: String): MealAnalysis {
        return try {
            val jsonStart = text.indexOf('{')
            val jsonEnd = text.lastIndexOf('}') + 1
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonText = text.substring(jsonStart, jsonEnd)
                val json = JSONObject(jsonText)

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
                parseMealAnalysisFromText(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing meal analysis", e)
            parseMealAnalysisFromText(text)
        }
    }

    private fun parseMealAnalysisFromText(text: String): MealAnalysis {
        val food = text.split("\n").firstOrNull()?.split(",")?.firstOrNull()?.trim()
            ?: text.split(",").firstOrNull()?.trim() ?: "Unknown food"

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

    private fun parseSupplementAnalysis(text: String): SupplementAnalysis {
        return try {
            val jsonStart = text.indexOf('{')
            val jsonEnd = text.lastIndexOf('}') + 1
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonText = text.substring(jsonStart, jsonEnd)
                val json = JSONObject(jsonText)

                val supplementName = json.optString("supplementName", json.optString("supplement_name", "Unknown supplement"))
                val nutrientsJson = json.optJSONObject("nutrients")

                val nutrients = mutableMapOf<com.coachie.app.data.model.MicronutrientType, Double>()
                if (nutrientsJson != null) {
                    nutrientsJson.keys().forEach { key ->
                        val amount = nutrientsJson.optDouble(key, Double.NaN)
                        val mappedType = MicronutrientNameMapper.identify(key)
                            ?: MicronutrientNameMapper.identify(key.replace("_", " "))

                        if (!amount.isNaN() && amount > 0 && mappedType != null) {
                            nutrients[mappedType] = amount
                        } else if (mappedType == null) {
                            Log.w(TAG, "Skipping unknown nutrient key from AI: $key")
                        }
                    }
                }

                SupplementAnalysis(
                    supplementName = supplementName,
                    nutrients = nutrients,
                    confidence = 0.8f,
                    rawLabelText = text
                )
            } else {
                parseSupplementAnalysisFromText(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing supplement analysis", e)
            parseSupplementAnalysisFromText(text)
        }
    }

    private fun parseSupplementAnalysisFromText(text: String): SupplementAnalysis {
        val supplementName = text.split("\n").firstOrNull()?.split(",")?.firstOrNull()?.trim()
            ?: text.split(",").firstOrNull()?.trim() ?: "Unknown supplement"

        return SupplementAnalysis(
            supplementName = supplementName,
            nutrients = emptyMap(),
            confidence = 0.3f,
            rawLabelText = text
        )
    }

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

