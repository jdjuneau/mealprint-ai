package com.coachie.app.data

import android.content.Context
import com.coachie.app.BuildConfig
import java.io.FileInputStream
import java.io.IOException
import java.util.Properties

/**
 * Secure secrets manager for API keys and sensitive configuration.
 * Reads from local.properties file which is excluded from version control.
 *
 * For production apps, consider using BuildConfig injection instead of runtime file reading.
 */
object Secrets {

    private var properties: Properties? = null
    private var isInitialized = false

    /**
     * Initialize the secrets manager with application context.
     * Call this once in Application.onCreate() or MainActivity.onCreate()
     */
    fun initialize(context: Context) {
        if (isInitialized) return

        try {
            val properties = Properties()

            // Try to read from project root directory (for development)
            try {
                val localPropsFile = java.io.File(context.getExternalFilesDir(null)?.parentFile?.parentFile?.parentFile, "local.properties")
                if (localPropsFile.exists()) {
                    FileInputStream(localPropsFile).use { input ->
                        properties.load(input)
                    }
                }
            } catch (e: Exception) {
                // Ignore, will fall back to BuildConfig
            }

            this.properties = properties
            isInitialized = true
        } catch (e: IOException) {
            throw IllegalStateException(
                "Failed to initialize secrets. Ensure local.properties exists in project root.",
                e
            )
        }
    }

    /**
     * Get the Gemini AI API key.
     * First tries BuildConfig (most secure), then falls back to local.properties.
     * @throws IllegalStateException if key is missing
     */
    fun getGeminiApiKey(): String {
        // First try BuildConfig (injected at build time - most secure)
        val buildConfigKey = BuildConfig.GEMINI_API_KEY
        if (!buildConfigKey.isNullOrBlank() && buildConfigKey != "YOUR_KEY_HERE") {
            return buildConfigKey
        }

        // Fall back to local.properties (development only)
        if (!isInitialized) {
            throw IllegalStateException(
                "Secrets not initialized. Call Secrets.initialize(context) first."
            )
        }
        val localKey = properties?.getProperty("gemini_api_key")?.takeIf {
            it.isNotBlank() && it != "YOUR_KEY_HERE"
        }

        return localKey ?: throw IllegalStateException(
            "Gemini API key not found. Please add it to:\n" +
            "1. local.properties: gemini_api_key=\"YOUR_ACTUAL_KEY\"\n" +
            "2. Or set GEMINI_API_KEY in BuildConfig (recommended for production)"
        )
    }

    /**
     * Get the Gemini API key safely (returns null if not available).
     * Use this for optional API key access.
     */
    fun getGeminiApiKeyOrNull(): String? {
        return try {
            getGeminiApiKey()
        } catch (e: IllegalStateException) {
            null
        }
    }

    /**
     * Check if Secrets has been properly initialized.
     */
    fun isInitialized(): Boolean = isInitialized

    /**
     * Check if the Gemini API key is available.
     */
    fun hasGeminiApiKey(): Boolean {
        return try {
            getGeminiApiKey()
            true
        } catch (e: IllegalStateException) {
            false
        }
    }

    /**
     * Get the OpenAI API key.
     * First tries BuildConfig (most secure), then falls back to local.properties.
     * @throws IllegalStateException if key is missing
     */
    fun getOpenAIApiKey(): String {
        // First try BuildConfig (injected at build time - most secure)
        val buildConfigKey = BuildConfig.OPENAI_API_KEY
        if (!buildConfigKey.isNullOrBlank() && buildConfigKey != "YOUR_KEY_HERE") {
            return buildConfigKey
        }

        // Fall back to local.properties (development only)
        if (!isInitialized) {
            throw IllegalStateException(
                "Secrets not initialized. Call Secrets.initialize(context) first."
            )
        }
        val localKey = properties?.getProperty("openai_api_key")?.takeIf {
            it.isNotBlank() && it != "YOUR_KEY_HERE"
        }

        return localKey ?: throw IllegalStateException(
            "OpenAI API key not found. Please add it to:\n" +
            "1. local.properties: openai_api_key=\"YOUR_ACTUAL_KEY\"\n" +
            "2. Or set OPENAI_API_KEY in BuildConfig (recommended for production)"
        )
    }

    /**
     * Get the OpenAI API key safely (returns null if not available).
     * Use this for optional API key access.
     */
    fun getOpenAIApiKeyOrNull(): String? {
        return try {
            getOpenAIApiKey()
        } catch (e: IllegalStateException) {
            null
        }
    }

    /**
     * Check if the OpenAI API key is available.
     */
    fun hasOpenAIApiKey(): Boolean {
        return try {
            getOpenAIApiKey()
            true
        } catch (e: IllegalStateException) {
            false
        }
    }

    /**
     * Get the USDA FoodData Central API key.
     * First tries BuildConfig (most secure), then falls back to local.properties.
     * Falls back to hardcoded key if not configured (for development).
     * 
     * NOTE: For production, add to local.properties:
     * usda_api_key=hFfdKmbbg6otRUUa4cT2LGsX4Q5uyao0iag6ttk7
     */
    fun getUSDAApiKey(): String {
        // First try BuildConfig (injected at build time - most secure)
        // Note: BuildConfig.USDA_API_KEY may not exist if not configured in build.gradle
        val buildConfigKey: String? = try {
            val field = BuildConfig::class.java.getField("USDA_API_KEY")
            field.get(null) as? String
        } catch (e: Exception) {
            null
        }
        if (!buildConfigKey.isNullOrBlank() && buildConfigKey != "YOUR_KEY_HERE") {
            return buildConfigKey
        }

        // Fall back to local.properties (development only)
        if (isInitialized) {
            val localKey = properties?.getProperty("usda_api_key")?.takeIf {
                it.isNotBlank() && it != "YOUR_KEY_HERE"
            }
            if (localKey != null) {
                return localKey
            }
        }

        // Fallback to hardcoded key (should be moved to local.properties for production)
        return "hFfdKmbbg6otRUUa4cT2LGsX4Q5uyao0iag6ttk7"
    }

    /**
     * Get the USDA API key safely (returns DEMO_KEY if not available).
     * Use this for optional API key access.
     */
    fun getUSDAApiKeyOrNull(): String? {
        return try {
            getUSDAApiKey()
        } catch (e: Exception) {
            "DEMO_KEY"
        }
    }

    /**
     * Check if the USDA API key is available.
     */
    fun hasUSDAApiKey(): Boolean {
        return try {
            val key = getUSDAApiKey()
            key.isNotBlank() && key != "DEMO_KEY" && key != "YOUR_KEY_HERE"
        } catch (e: Exception) {
            false
        }
    }
}
