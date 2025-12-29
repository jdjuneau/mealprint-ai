package com.coachie.app.data.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import com.coachie.app.util.await
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Extracts food product information by scanning barcodes and looking up data online.
 */
object FoodBarcodeParser {

    private const val TAG = "FoodBarcodeParser"

    /**
     * Scan barcode and lookup food data online.
     */
    suspend fun parse(context: Context, imageUri: Uri): ParsedFoodBarcode? {
        return try {
            // Step 1: Try barcode scanning first
            val barcodeData = scanBarcode(context, imageUri)
            if (barcodeData != null) {
                Log.d(TAG, "Found barcode: $barcodeData")

                // Step 2: Look up food data online
                val lookupResult = FoodLookupService.lookup(barcodeData)
                if (lookupResult != null) {
                    return lookupResult
                }

                Log.w(TAG, "Barcode $barcodeData not found in configured providers")
                return ParsedFoodBarcode(
                    name = "Unknown Product",
                    calories = 0.0,
                    protein = 0.0,
                    carbs = 0.0,
                    fat = 0.0,
                    sugar = 0.0,
                    addedSugar = 0.0,
                    servingSize = null,
                    rawText = """Barcode: $barcodeData

📷 Scanned successfully, but this product isn't in our database yet.

🎯 What would you like to do?

• **Search by name**: Enter the food name for a manual search
• **Add manually**: Input the nutritional facts yourself
• **Try again**: Rescan the barcode or try a different angle

💡 Popular food products are available in our database. Specialty or newer products may need to be added manually.

🔄 We continuously expand our food database based on user requests!""",
                    confidence = 0.0f
                )
            }

            // Step 3: No barcode found - ask user to rescan
            Log.d(TAG, "No barcode found in image")
            return ParsedFoodBarcode(
                name = "No barcode detected",
                calories = 0.0,
                protein = 0.0,
                carbs = 0.0,
                fat = 0.0,
                sugar = 0.0,
                addedSugar = 0.0,
                servingSize = null,
                rawText = "No barcode detected in the photo. Please retake the picture focusing on the barcode.",
                confidence = 0.0f
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse food barcode", e)
            null
        }
    }

    /**
     * Scan for barcodes in the image.
     */
    private suspend fun scanBarcode(context: Context, imageUri: Uri): String? {
        return try {
            val inputImage = InputImage.fromFilePath(context, imageUri)
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_CODE_39
                )
                .build()

            val scanner = BarcodeScanning.getClient(options)
            val barcodes = scanner.process(inputImage).await()

            // Return the first barcode found
            barcodes.firstOrNull()?.rawValue?.also {
                Log.d(TAG, "Scanned barcode: $it")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Barcode scanning failed", e)
            null
        }
    }

    data class ParsedFoodBarcode(
        val name: String,
        val calories: Double,
        val protein: Double,
        val carbs: Double,
        val fat: Double,
        val sugar: Double = 0.0,
        val addedSugar: Double = 0.0,
        val servingSize: String? = null,
        val rawText: String,
        val confidence: Float = 0.5f
    )
}

