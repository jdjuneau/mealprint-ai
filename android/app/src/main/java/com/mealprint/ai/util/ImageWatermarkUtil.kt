package com.coachie.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.mealprint.ai.R
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Utility for adding Coachie logo watermark to images
 */
object ImageWatermarkUtil {
    
    /**
     * Add Coachie logo watermark to an image
     * @param context Android context
     * @param imageUri URI of the source image
     * @param logoPosition Position of the logo: "bottom-right", "bottom-left", "top-right", "top-left", "center"
     * @param logoSize Size of the logo as a fraction of image width (0.1 = 10% of width)
     * @param padding Padding from edges in pixels
     * @return URI of the watermarked image, or null if failed
     */
    fun addLogoWatermark(
        context: Context,
        imageUri: Uri,
        logoPosition: String = "bottom-right",
        logoSize: Float = 0.15f,
        padding: Int = 20
    ): Uri? {
        return try {
            // Load source image
            val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
            val sourceBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            if (sourceBitmap == null) {
                android.util.Log.e("ImageWatermarkUtil", "Failed to decode source image")
                return null
            }
            
            // Load logo
            val logoBitmap = BitmapFactory.decodeResource(
                context.resources,
                R.drawable.coachieicon
            ) ?: run {
                android.util.Log.e("ImageWatermarkUtil", "Failed to load logo")
                return null
            }
            
            // Calculate logo size
            val logoWidth = (sourceBitmap.width * logoSize).toInt()
            val logoHeight = (logoBitmap.height * logoWidth / logoBitmap.width.toFloat()).toInt()
            val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, logoWidth, logoHeight, true)
            
            // Create output bitmap
            val watermarkedBitmap = Bitmap.createBitmap(
                sourceBitmap.width,
                sourceBitmap.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(watermarkedBitmap)
            
            // Draw source image
            canvas.drawBitmap(sourceBitmap, 0f, 0f, null)
            
            // Calculate logo position
            val logoX: Float = when {
                logoPosition.contains("right") -> (sourceBitmap.width - scaledLogo.width - padding).toFloat()
                logoPosition.contains("left") -> padding.toFloat()
                else -> (sourceBitmap.width - scaledLogo.width) / 2f
            }
            
            val logoY: Float = when {
                logoPosition.contains("bottom") -> (sourceBitmap.height - scaledLogo.height - padding).toFloat()
                logoPosition.contains("top") -> padding.toFloat()
                else -> (sourceBitmap.height - scaledLogo.height) / 2f
            }
            
            // Draw logo with semi-transparent background for visibility
            val paint = Paint().apply {
                alpha = 230 // Slightly transparent
            }
            
            // Add "Coachie" text next to logo
            val textPaint = Paint().apply {
                color = Color.BLACK
                textSize = scaledLogo.height.toFloat() * 0.4f // Text size relative to logo
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                alpha = 240
                isAntiAlias = true
            }
            
            val text = "Coachie"
            val textWidth = textPaint.measureText(text)
            val textX = logoX + scaledLogo.width.toFloat() + 12f // Space between logo and text
            val textY = logoY + scaledLogo.height.toFloat() * 0.75f // Vertically center with logo
            
            // Calculate combined width (logo + spacing + text)
            val combinedWidth = scaledLogo.width.toFloat() + 12f + textWidth + 16f // Extra padding for text
            val combinedHeight = scaledLogo.height.toFloat()
            
            // Draw a single background behind both logo and text
            val backgroundPaint = Paint().apply {
                color = android.graphics.Color.WHITE
                alpha = 200
            }
            val backgroundRect = RectF(
                logoX - padding / 2f,
                logoY - padding / 2f,
                logoX + combinedWidth,
                logoY + combinedHeight + padding / 2f
            )
            canvas.drawRoundRect(
                backgroundRect,
                12f,
                12f,
                backgroundPaint
            )
            
            // Draw logo
            canvas.drawBitmap(scaledLogo, logoX, logoY, paint)
            
            // Draw text
            canvas.drawText(text, textX, textY, textPaint)
            
            // Save watermarked image
            val outputDir = File(context.cacheDir, "watermarked_images")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            
            val timestamp = System.currentTimeMillis()
            val outputFile = File(outputDir, "watermarked_${timestamp}.jpg")
            
            FileOutputStream(outputFile).use { out ->
                watermarkedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            
            // Clean up
            sourceBitmap.recycle()
            scaledLogo.recycle()
            watermarkedBitmap.recycle()
            
            // Return URI
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile
            )
        } catch (e: Exception) {
            android.util.Log.e("ImageWatermarkUtil", "Error adding watermark", e)
            null
        }
    }
    
    /**
     * Add logo watermark to a bitmap directly
     */
    fun addLogoWatermarkToBitmap(
        context: Context,
        sourceBitmap: Bitmap,
        logoPosition: String = "bottom-right",
        logoSize: Float = 0.15f,
        padding: Int = 20
    ): Bitmap? {
        return try {
            // Load logo
            val logoBitmap = BitmapFactory.decodeResource(
                context.resources,
                R.drawable.coachieicon
            ) ?: return null
            
            // Calculate logo size
            val logoWidth = (sourceBitmap.width * logoSize).toInt()
            val logoHeight = (logoBitmap.height * logoWidth / logoBitmap.width.toFloat()).toInt()
            val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, logoWidth, logoHeight, true)
            
            // Create output bitmap
            val watermarkedBitmap = Bitmap.createBitmap(
                sourceBitmap.width,
                sourceBitmap.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(watermarkedBitmap)
            
            // Draw source image
            canvas.drawBitmap(sourceBitmap, 0f, 0f, null)
            
            // Calculate logo position
            val logoX: Float = when {
                logoPosition.contains("right") -> (sourceBitmap.width - scaledLogo.width - padding).toFloat()
                logoPosition.contains("left") -> padding.toFloat()
                else -> (sourceBitmap.width - scaledLogo.width) / 2f
            }
            
            val logoY: Float = when {
                logoPosition.contains("bottom") -> (sourceBitmap.height - scaledLogo.height - padding).toFloat()
                logoPosition.contains("top") -> padding.toFloat()
                else -> (sourceBitmap.height - scaledLogo.height) / 2f
            }
            
            // Draw logo with semi-transparent background for visibility
            val paint = Paint().apply {
                alpha = 230
            }
            
            // Add "Coachie" text next to logo
            val textPaint = Paint().apply {
                color = Color.BLACK
                textSize = scaledLogo.height.toFloat() * 0.4f // Text size relative to logo
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                alpha = 240
                isAntiAlias = true
            }
            
            val text = "Coachie"
            val textWidth = textPaint.measureText(text)
            val textX = logoX + scaledLogo.width.toFloat() + 12f // Space between logo and text
            val textY = logoY + scaledLogo.height.toFloat() * 0.75f // Vertically center with logo
            
            // Calculate combined width (logo + spacing + text)
            val combinedWidth = scaledLogo.width.toFloat() + 12f + textWidth + 16f // Extra padding for text
            val combinedHeight = scaledLogo.height.toFloat()
            
            // Draw a single background behind both logo and text
            val backgroundPaint = Paint().apply {
                color = android.graphics.Color.WHITE
                alpha = 200
            }
            val backgroundRect = RectF(
                logoX - padding / 2f,
                logoY - padding / 2f,
                logoX + combinedWidth,
                logoY + combinedHeight + padding / 2f
            )
            canvas.drawRoundRect(
                backgroundRect,
                12f,
                12f,
                backgroundPaint
            )
            
            // Draw logo
            canvas.drawBitmap(scaledLogo, logoX, logoY, paint)
            
            // Draw text
            canvas.drawText(text, textX, textY, textPaint)
            
            // Clean up
            scaledLogo.recycle()
            
            watermarkedBitmap
        } catch (e: Exception) {
            android.util.Log.e("ImageWatermarkUtil", "Error adding watermark to bitmap", e)
            null
        }
    }
}

