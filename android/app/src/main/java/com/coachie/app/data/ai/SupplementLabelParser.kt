package com.coachie.app.data.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import com.coachie.app.data.model.MicronutrientType
import com.coachie.app.data.model.MicronutrientUnit
import com.coachie.app.util.await
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Extracts supplement nutrient amounts by scanning barcodes and looking up data online.
 */
object SupplementLabelParser {

    private const val TAG = "SupplementLabelParser"

    /**
     * Scan barcode and lookup supplement data online.
     */
    suspend fun parse(context: Context, imageUri: Uri): ParsedSupplementLabel? {
        return try {
            // Step 1: Try barcode scanning first
            val barcodeData = scanBarcode(context, imageUri)
            if (barcodeData != null) {
                Log.d(TAG, "Found barcode: $barcodeData")

                // Step 2: Look up supplement data online
                val lookupResult = SupplementLookupService.lookup(barcodeData)
                if (lookupResult != null) {
                    return lookupResult
                }

                Log.w(TAG, "Barcode $barcodeData not found in configured providers")
                return ParsedSupplementLabel(
                    name = "Unknown Supplement",
                    nutrients = emptyMap(),
                    rawText = """Barcode: $barcodeData

📷 Scanned successfully, but this supplement isn't in our database yet.

🎯 What would you like to do?

• **Search by name**: Enter the supplement name for a manual search
• **Add manually**: Input the nutritional facts yourself
• **Try again**: Rescan the barcode or try a different angle

💡 Popular supplements like Centrum, One A Day, and Nature Made are already in our database. Specialty or newer supplements may need to be added manually.

🔄 We continuously expand our supplement database based on user requests!""",
                    confidence = 0.0f
                )
            }

            // Step 3: No barcode found - ask user to rescan
            Log.d(TAG, "No barcode found in image")
            return ParsedSupplementLabel(
                name = "No barcode detected",
                nutrients = emptyMap(),
                rawText = "No barcode detected in the photo. Please retake the picture focusing on the barcode.",
                confidence = 0.0f
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse supplement", e)
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
                .setBarcodeFormats(Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8)
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

    data class ParsedSupplementLabel(
        val name: String?,
        val nutrients: Map<MicronutrientType, Double>,
        val rawText: String,
        val confidence: Float = 0.5f
    )
}