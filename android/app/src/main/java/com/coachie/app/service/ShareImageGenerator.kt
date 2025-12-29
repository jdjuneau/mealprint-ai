package com.coachie.app.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Generates shareable images for social media posts
 */
class ShareImageGenerator(private val context: Context) {
    
    companion object {
        private const val IMAGE_WIDTH = 1080
        private const val IMAGE_HEIGHT = 1080
        private const val PADDING = 80f
        private const val TITLE_TEXT_SIZE = 72f
        private const val SUBTITLE_TEXT_SIZE = 48f
        private const val METRIC_TEXT_SIZE = 120f // Large text for scores/numbers
        private const val LOGO_SIZE = 120f
        private const val CARD_SCREENSHOT_HEIGHT = 600f // Height for card screenshot area
    }
    
    /**
     * Generate a generic share image with title, metric, and subtitle
     */
    fun generateShareImage(
        title: String,
        metric: String,
        subtitle: String? = null,
        backgroundColorStart: Int = Color.parseColor("#667eea"), // Purple
        backgroundColorEnd: Int = Color.parseColor("#764ba2") // Darker purple
    ): Uri? {
        return try {
            val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // Draw background gradient
            val backgroundPaint = Paint().apply {
                shader = android.graphics.LinearGradient(
                    0f, 0f, 0f, IMAGE_HEIGHT.toFloat(),
                    backgroundColorStart,
                    backgroundColorEnd,
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, IMAGE_WIDTH.toFloat(), IMAGE_HEIGHT.toFloat(), backgroundPaint)
            
            // Create paints
            val titlePaint = Paint().apply {
                color = Color.WHITE
                textSize = TITLE_TEXT_SIZE
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            
            val metricPaint = Paint().apply {
                color = Color.WHITE
                textSize = METRIC_TEXT_SIZE
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            
            val subtitlePaint = Paint().apply {
                color = Color.WHITE
                textSize = SUBTITLE_TEXT_SIZE
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            
            // Calculate positions
            val centerX = IMAGE_WIDTH / 2f
            var currentY = PADDING + TITLE_TEXT_SIZE
            
            // Draw title
            canvas.drawText(title, centerX, currentY, titlePaint)
            currentY += TITLE_TEXT_SIZE + 60f
            
            // Draw metric (large, prominent)
            canvas.drawText(metric, centerX, currentY, metricPaint)
            currentY += METRIC_TEXT_SIZE + 40f
            
            // Draw subtitle if available
            subtitle?.let {
                val subtitleLines = breakTextIntoLines(it, subtitlePaint, IMAGE_WIDTH - (PADDING * 2))
                subtitleLines.forEach { line ->
                    canvas.drawText(line, centerX, currentY, subtitlePaint)
                    currentY += SUBTITLE_TEXT_SIZE + 20f
                }
            }
            
            // Draw Coachie logo at bottom
            val logoY = IMAGE_HEIGHT - PADDING - LOGO_SIZE
            drawCoachieLogo(canvas, centerX, logoY, LOGO_SIZE)
            
            // Save bitmap to file
            saveBitmapToFile(bitmap, "share_${System.currentTimeMillis()}.png")
        } catch (e: Exception) {
            android.util.Log.e("ShareImageGenerator", "Error generating share image", e)
            null
        }
    }
    
    /**
     * Generate a shareable image for circle accomplishments
     */
    fun generateCircleShareImage(
        circleName: String,
        accomplishment: String,
        memberCount: Int = 0,
        maxMembers: Int = 0
    ): Uri? {
        return try {
            // Create bitmap
            val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // Draw background gradient (purple to green)
            val backgroundPaint = Paint().apply {
                shader = android.graphics.LinearGradient(
                    0f, 0f, 0f, IMAGE_HEIGHT.toFloat(),
                    Color.parseColor("#667eea"), // Purple
                    Color.parseColor("#764ba2"), // Darker purple
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, IMAGE_WIDTH.toFloat(), IMAGE_HEIGHT.toFloat(), backgroundPaint)
            
            // Draw text
            val titlePaint = Paint().apply {
                color = Color.WHITE
                textSize = TITLE_TEXT_SIZE
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            
            val subtitlePaint = Paint().apply {
                color = Color.WHITE
                textSize = SUBTITLE_TEXT_SIZE
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            
            // Calculate text positions
            val centerX = IMAGE_WIDTH / 2f
            var currentY = PADDING + TITLE_TEXT_SIZE
            
            // Draw circle name
            canvas.drawText(circleName, centerX, currentY, titlePaint)
            currentY += TITLE_TEXT_SIZE + 40f
            
            // Draw accomplishment
            val accomplishmentLines = breakTextIntoLines(accomplishment, subtitlePaint, IMAGE_WIDTH - (PADDING * 2))
            accomplishmentLines.forEach { line ->
                canvas.drawText(line, centerX, currentY, subtitlePaint)
                currentY += SUBTITLE_TEXT_SIZE + 20f
            }
            
            // Draw member count if available
            if (memberCount > 0 && maxMembers > 0) {
                currentY += 40f
                val memberText = "$memberCount/$maxMembers members"
                canvas.drawText(memberText, centerX, currentY, subtitlePaint)
            }
            
            // Draw Coachie logo at bottom
            val logoY = IMAGE_HEIGHT - PADDING - LOGO_SIZE
            drawCoachieLogo(canvas, centerX, logoY, LOGO_SIZE)
            
            // Save bitmap to file
            saveBitmapToFile(bitmap, "circle_share_${System.currentTimeMillis()}.png")
        } catch (e: Exception) {
            android.util.Log.e("ShareImageGenerator", "Error generating circle share image", e)
            null
        }
    }
    
    /**
     * Break text into multiple lines to fit within width
     */
    private fun breakTextIntoLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""
        
        words.forEach { word ->
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val width = paint.measureText(testLine)
            
            if (width <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                }
                currentLine = word
            }
        }
        
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }
        
        return lines
    }
    
    /**
     * Draw Coachie logo - loads at native resolution then scales down for crisp quality
     */
    private fun drawCoachieLogo(canvas: Canvas, centerX: Float, y: Float, size: Float) {
        // Use high-res version with 4x render size for maximum quality
        val renderSize = size * 4f // Render at 4x resolution for crisp quality
        drawCoachieLogoHighRes(canvas, centerX, y, renderSize, size)
    }
    
    /**
     * Draw Coachie logo at native resolution - NEVER scale up (causes pixelation), only scale down if needed
     * Loads logo at its native resolution and only scales down to displaySize if the native size is larger
     */
    private fun drawCoachieLogoHighRes(canvas: Canvas, centerX: Float, y: Float, renderSize: Float, displaySize: Float) {
        android.util.Log.d("CoachieShare", "drawCoachieLogoHighRes: centerX=$centerX, y=$y, renderSize=$renderSize, displaySize=$displaySize")
        
        var logoLoaded = false
        var sourceBitmap: Bitmap? = null
        
        // CRITICAL: Load logo at NATIVE resolution - disable ALL density scaling
        // 512x512 source is plenty for 60-240px display, but Android density scaling can mess it up
        try {
            val resourceId = context.resources.getIdentifier("coachieicon", "drawable", context.packageName)
            android.util.Log.d("CoachieShare", "coachieicon resource ID: $resourceId")
            
            if (resourceId != 0) {
                // Load bitmap at native resolution - disable ALL density scaling
                val options = BitmapFactory.Options().apply {
                    inScaled = false // Don't scale based on density
                    inDensity = 0 // Disable density scaling
                    inTargetDensity = 0 // Disable target density scaling
                    inJustDecodeBounds = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888 // High quality
                    inSampleSize = 1 // Don't downsample
                }
                
                sourceBitmap = BitmapFactory.decodeResource(context.resources, resourceId, options)
                
                if (sourceBitmap != null) {
                    android.util.Log.d("CoachieShare", "Loaded coachieicon at native resolution: ${sourceBitmap.width}x${sourceBitmap.height}")
                } else {
                    android.util.Log.e("CoachieShare", "Failed to decode coachieicon bitmap")
                }
            } else {
                android.util.Log.e("CoachieShare", "coachieicon resource not found (resourceId is 0)")
            }
        } catch (e: Exception) {
            android.util.Log.e("CoachieShare", "Exception loading coachieicon", e)
            e.printStackTrace()
        }
        
        // Fallback: Try coachie if coachieicon failed
        if (sourceBitmap == null) {
            try {
                val resourceId = context.resources.getIdentifier("coachie", "drawable", context.packageName)
                if (resourceId != 0) {
                    android.util.Log.d("CoachieShare", "Trying fallback: coachie resource ID: $resourceId")
                    val options = BitmapFactory.Options().apply {
                        inScaled = false // Don't scale based on density
                        inDensity = 0 // Disable density scaling
                        inTargetDensity = 0 // Disable target density scaling
                        inJustDecodeBounds = false
                        inPreferredConfig = Bitmap.Config.ARGB_8888 // High quality
                        inSampleSize = 1 // Don't downsample
                    }
                    sourceBitmap = BitmapFactory.decodeResource(context.resources, resourceId, options)
                    if (sourceBitmap != null) {
                        android.util.Log.d("CoachieShare", "Loaded fallback logo: coachie at native resolution ${sourceBitmap.width}x${sourceBitmap.height}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CoachieShare", "Error loading fallback coachie", e)
            }
        }
        
        // Draw the logo if loaded - only scale DOWN if native is larger than displaySize
        if (sourceBitmap != null && !sourceBitmap.isRecycled) {
            android.util.Log.d("CoachieShare", "Logo bitmap loaded: ${sourceBitmap.width}x${sourceBitmap.height}, displaySize=$displaySize")
            
            // CRITICAL: Only scale DOWN if needed, never scale UP (prevents pixelation)
            val targetSize = displaySize.toInt()
            val scaledBitmap = if (sourceBitmap.width > targetSize || sourceBitmap.height > targetSize) {
                // Native is larger - scale down with high-quality filtering
                android.util.Log.d("CoachieShare", "Scaling DOWN from ${sourceBitmap.width}x${sourceBitmap.height} to ${targetSize}x${targetSize}")
                Bitmap.createScaledBitmap(sourceBitmap, targetSize, targetSize, true)
            } else if (sourceBitmap.width < targetSize || sourceBitmap.height < targetSize) {
                // Native is smaller - use as-is (don't scale up, causes pixelation)
                android.util.Log.d("CoachieShare", "Native size ${sourceBitmap.width}x${sourceBitmap.height} is smaller than target $targetSize - using native size (no upscaling)")
                sourceBitmap
            } else {
                // Exact match
                sourceBitmap
            }
            
            // Position bitmap centered at (centerX, y)
            val logoX = centerX - (scaledBitmap.width / 2f)
            val logoY = y - (scaledBitmap.height / 2f)
            android.util.Log.d("CoachieShare", "Drawing logo bitmap at ($logoX, $logoY), size: ${scaledBitmap.width}x${scaledBitmap.height}")
            
            // Use Paint with filtering for best quality
            val paint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
            }
            canvas.drawBitmap(scaledBitmap, logoX, logoY, paint)
            
            // Clean up scaled bitmap if it was created separately
            if (scaledBitmap != sourceBitmap && !scaledBitmap.isRecycled) {
                scaledBitmap.recycle()
            }
            
            logoLoaded = true
        } else {
            android.util.Log.e("CoachieShare", "Failed to load logo bitmap - bitmap is null or recycled")
        }
        
        // If logo wasn't loaded, draw text as fallback
        if (!logoLoaded) {
            android.util.Log.w("CoachieShare", "Logo not loaded, using text fallback")
            val logoPaint = Paint().apply {
                color = Color.WHITE
                textSize = displaySize * 0.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText("Coachie", centerX, y + displaySize * 0.3f, logoPaint)
        }
    }
    
    /**
     * Convert drawable to bitmap - CRITICAL: Always scales to requested size
     */
    private fun drawableToBitmap(drawable: Drawable, width: Int, height: Int): Bitmap? {
        return try {
            val sourceBitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap
            } else {
                // Create bitmap from drawable
                val tempBitmap = Bitmap.createBitmap(
                    drawable.intrinsicWidth.takeIf { it > 0 } ?: width,
                    drawable.intrinsicHeight.takeIf { it > 0 } ?: height,
                    Bitmap.Config.ARGB_8888
                )
                val tempCanvas = Canvas(tempBitmap)
                drawable.setBounds(0, 0, tempCanvas.width, tempCanvas.height)
                drawable.draw(tempCanvas)
                tempBitmap
            }
            
            // CRITICAL: Always scale to the requested size
            if (sourceBitmap.width == width && sourceBitmap.height == height) {
                sourceBitmap
            } else {
                Bitmap.createScaledBitmap(sourceBitmap, width, height, true)
            }
        } catch (e: Exception) {
            android.util.Log.e("CoachieShare", "Error converting drawable to bitmap", e)
            null
        }
    }
    
    /**
     * Generate a promotional post image with card screenshot, Coachie icon, and Google Play CTA
     * This creates visually pleasing social media posts for marketing
     * 
     * @param cardScreenshot Bitmap of the card screenshot (can be null to generate placeholder)
     * @param cardTitle Optional title for the card (e.g., "Flow Score", "Win of the Day")
     * @param cardDescription Optional description of what the card shows
     * @return URI of the generated promotional image
     */
    fun generatePromotionalPost(
        cardScreenshot: Bitmap? = null,
        cardTitle: String? = null,
        cardDescription: String? = null,
        mealPhoto: Bitmap? = null // Optional meal photo to display alongside the card
    ): Uri? {
        android.util.Log.d("CoachieShare", "=== generatePromotionalPost START ===")
        android.util.Log.d("CoachieShare", "cardScreenshot: ${if (cardScreenshot != null) "provided (${cardScreenshot.width}x${cardScreenshot.height})" else "null"}")
        android.util.Log.d("CoachieShare", "cardTitle: $cardTitle")
        android.util.Log.d("CoachieShare", "cardDescription: $cardDescription")
        
        // CRITICAL: Delete ALL cached share images BEFORE generating new one
        try {
            val cacheDir = context.cacheDir
            val imagesDir = File(cacheDir, "share_images")
            android.util.Log.d("CoachieShare", "CLEARING CACHE: cacheDir=$cacheDir, imagesDir=$imagesDir, exists=${imagesDir.exists()}")
            if (imagesDir.exists()) {
                android.util.Log.d("CoachieShare", "DELETING ALL CACHED SHARE IMAGES")
                val files = imagesDir.listFiles()
                android.util.Log.d("CoachieShare", "Found ${files?.size ?: 0} files to delete")
                files?.forEach { file ->
                    try {
                        val deleted = file.delete()
                        android.util.Log.d("CoachieShare", "Deleted cached image: ${file.name} - $deleted")
                    } catch (e: Exception) {
                        android.util.Log.w("CoachieShare", "Error deleting ${file.name}", e)
                    }
                }
                // Also try to delete the directory and recreate it
                try {
                    if (imagesDir.delete()) {
                        android.util.Log.d("CoachieShare", "Deleted imagesDir, will recreate")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("CoachieShare", "Could not delete imagesDir", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("CoachieShare", "Error clearing cache (non-critical)", e)
        }
        
        return try {
            android.util.Log.d("CoachieShare", "Creating bitmap: ${IMAGE_WIDTH}x${IMAGE_HEIGHT}")
            val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
            if (bitmap == null) {
                android.util.Log.e("CoachieShare", "Failed to create bitmap - bitmap is null")
                return null
            }
            android.util.Log.d("CoachieShare", "Bitmap created successfully")
            val canvas = Canvas(bitmap)
            
            // Draw background gradient (Coachie purple) - KEEPING PURPLE
            val backgroundPaint = Paint().apply {
                shader = android.graphics.LinearGradient(
                    0f, 0f, 0f, IMAGE_HEIGHT.toFloat(),
                    Color.parseColor("#667eea"), // Purple
                    Color.parseColor("#764ba2"), // Darker purple
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            android.util.Log.d("CoachieShare", "Drawing background gradient - NEW CODE VERSION")
            canvas.drawRect(0f, 0f, IMAGE_WIDTH.toFloat(), IMAGE_HEIGHT.toFloat(), backgroundPaint)
            
            val centerX = IMAGE_WIDTH / 2f
            var currentY = PADDING // Top padding for card
            
            // Check if we have both meal photo and card screenshot - display side by side
            val hasBothImages = mealPhoto != null && cardScreenshot != null
            android.util.Log.d("CoachieShare", "Image check: mealPhoto=${mealPhoto != null}, cardScreenshot=${cardScreenshot != null}, hasBothImages=$hasBothImages")
            if (hasBothImages) {
                android.util.Log.d("CoachieShare", "Meal photo: ${mealPhoto!!.width}x${mealPhoto!!.height}")
                android.util.Log.d("CoachieShare", "Recipe card: ${cardScreenshot!!.width}x${cardScreenshot!!.height}")
            }
            val cardPadding = 30f
            val imageSpacing = 20f // Space between two images when both are present
            val cardHeight = 750f // LARGER to maximize photo and recipe card space
            
            val cardBackgroundPaint = Paint().apply {
                color = Color.parseColor("#1A1A2E") // Dark background for card
                isAntiAlias = true
            }
            
            // Draw shadow
            val shadowPaint = Paint().apply {
                color = Color.parseColor("#000000")
                alpha = 100
                isAntiAlias = true
            }
            val cornerRadius = 24f
            
            if (hasBothImages) {
                // Display both images side by side
                android.util.Log.d("CoachieShare", "Drawing both images side by side")
                val availableWidth = IMAGE_WIDTH - (cardPadding * 2) - imageSpacing
                val singleImageWidth = availableWidth / 2f
                
                // Draw meal photo on the left
                val mealPhotoX = cardPadding
                val mealPhotoY = currentY
                canvas.drawRoundRect(
                    mealPhotoX + 8f, mealPhotoY + 8f,
                    mealPhotoX + singleImageWidth - 8f, mealPhotoY + cardHeight - 8f,
                    cornerRadius, cornerRadius, shadowPaint
                )
                canvas.drawRoundRect(
                    mealPhotoX, mealPhotoY,
                    mealPhotoX + singleImageWidth, mealPhotoY + cardHeight,
                    cornerRadius, cornerRadius, cardBackgroundPaint
                )
                
                // Scale and draw meal photo - FILL the entire card area (cover mode, no black space)
                mealPhoto?.let { photo ->
                    // Use maxOf to ensure photo fills entire area (cover mode - no black space)
                    val scale = maxOf(
                        singleImageWidth / photo.width,
                        cardHeight / photo.height
                    )
                    
                    // Create matrix to scale and center-crop the photo
                    val matrix = android.graphics.Matrix().apply {
                        postScale(scale, scale)
                        // Translate to center-crop: move photo so center aligns with card center
                        val scaledWidth = photo.width * scale
                        val scaledHeight = photo.height * scale
                        postTranslate(
                            mealPhotoX - (scaledWidth - singleImageWidth) / 2f,
                            mealPhotoY - (scaledHeight - cardHeight) / 2f
                        )
                    }
                    
                    // Save canvas state, clip to rounded card area, draw photo, restore
                    canvas.save()
                    val photoPath = Path().apply {
                        addRoundRect(
                            RectF(mealPhotoX, mealPhotoY, mealPhotoX + singleImageWidth, mealPhotoY + cardHeight),
                            cornerRadius, cornerRadius,
                            Path.Direction.CW
                        )
                    }
                    canvas.clipPath(photoPath)
                    canvas.drawBitmap(photo, matrix, null)
                    canvas.restore()
                }
                
                // Draw recipe card on the right
                val cardX = cardPadding + singleImageWidth + imageSpacing
                val cardY = currentY
                canvas.drawRoundRect(
                    cardX + 8f, cardY + 8f,
                    cardX + singleImageWidth - 8f, cardY + cardHeight - 8f,
                    cornerRadius, cornerRadius, shadowPaint
                )
                canvas.drawRoundRect(
                    cardX, cardY,
                    cardX + singleImageWidth, cardY + cardHeight,
                    cornerRadius, cornerRadius, cardBackgroundPaint
                )
                
                // Scale and draw recipe card with rounded corners
                cardScreenshot?.let { card ->
                    val scale = minOf(
                        (singleImageWidth - 20f) / card.width,
                        (cardHeight - 20f) / card.height
                    )
                    val scaledWidth = card.width * scale
                    val scaledHeight = card.height * scale
                    val screenshotX = cardX + (singleImageWidth - scaledWidth) / 2f
                    val screenshotY = cardY + (cardHeight - scaledHeight) / 2f
                    
                    val scaledCard = Bitmap.createScaledBitmap(card, scaledWidth.toInt(), scaledHeight.toInt(), true)
                    scaledCard?.let {
                        // Clip to rounded rectangle for recipe card
                        canvas.save()
                        val cardPath = Path().apply {
                            addRoundRect(
                                RectF(cardX, cardY, cardX + singleImageWidth, cardY + cardHeight),
                                cornerRadius, cornerRadius,
                                Path.Direction.CW
                            )
                        }
                        canvas.clipPath(cardPath)
                        canvas.drawBitmap(it, screenshotX, screenshotY, null)
                        canvas.restore()
                    }
                }
            } else {
                // Single image or placeholder - use full width
                val cardWidth = IMAGE_WIDTH - (cardPadding * 2)
                val cardX = cardPadding
                val cardY = currentY
                
                canvas.drawRoundRect(
                    cardX + 8f, cardY + 8f,
                    cardX + cardWidth - 8f, cardY + cardHeight - 8f,
                    cornerRadius, cornerRadius, shadowPaint
                )
                canvas.drawRoundRect(
                    cardX, cardY,
                    cardX + cardWidth, cardY + cardHeight,
                    cornerRadius, cornerRadius, cardBackgroundPaint
                )
                
                // Draw meal photo if available (single image mode)
                if (mealPhoto != null) {
                    android.util.Log.d("CoachieShare", "Drawing meal photo only: ${mealPhoto.width}x${mealPhoto.height}")
                    // Use cover mode to fill entire card area
                    val scale = maxOf(
                        cardWidth / mealPhoto.width,
                        cardHeight / mealPhoto.height
                    )
                    
                    // Create matrix to scale and center-crop the photo
                    val matrix = android.graphics.Matrix().apply {
                        postScale(scale, scale)
                        val scaledWidth = mealPhoto.width * scale
                        val scaledHeight = mealPhoto.height * scale
                        postTranslate(
                            cardX - (scaledWidth - cardWidth) / 2f,
                            cardY - (scaledHeight - cardHeight) / 2f
                        )
                    }
                    
                    // Save canvas state, clip to rounded card area, draw photo, restore
                    canvas.save()
                    val photoPath = Path().apply {
                        addRoundRect(
                            RectF(cardX, cardY, cardX + cardWidth, cardY + cardHeight),
                            cornerRadius, cornerRadius,
                            Path.Direction.CW
                        )
                    }
                    canvas.clipPath(photoPath)
                    canvas.drawBitmap(mealPhoto, matrix, null)
                    canvas.restore()
                }
                // Draw card screenshot or placeholder with rounded corners
                else if (cardScreenshot != null) {
                    android.util.Log.d("CoachieShare", "Drawing card screenshot: ${cardScreenshot.width}x${cardScreenshot.height}")
                    val scale = minOf(
                        (cardWidth - 20f) / cardScreenshot.width,
                        (cardHeight - 20f) / cardScreenshot.height
                    )
                    val scaledWidth = cardScreenshot.width * scale
                    val scaledHeight = cardScreenshot.height * scale
                    val screenshotX = cardX + (cardWidth - scaledWidth) / 2f
                    val screenshotY = cardY + (cardHeight - scaledHeight) / 2f
                    
                    val scaledBitmap = Bitmap.createScaledBitmap(
                        cardScreenshot,
                        scaledWidth.toInt(),
                        scaledHeight.toInt(),
                        true
                    )
                    scaledBitmap?.let {
                        // Clip to rounded rectangle for recipe card
                        canvas.save()
                        val cardPath = Path().apply {
                            addRoundRect(
                                RectF(cardX, cardY, cardX + cardWidth, cardY + cardHeight),
                                cornerRadius, cornerRadius,
                                Path.Direction.CW
                            )
                        }
                        canvas.clipPath(cardPath)
                        canvas.drawBitmap(it, screenshotX, screenshotY, null)
                        canvas.restore()
                    }
                } else {
                    android.util.Log.d("CoachieShare", "No screenshot provided, drawing placeholder")
                    // Draw placeholder card content
                    val placeholderPaint = Paint().apply {
                        color = Color.WHITE
                        textSize = 48f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        textAlign = Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    
                    val placeholderText = cardTitle ?: "Coachie Card"
                    val textY = cardY + cardHeight / 2f
                    canvas.drawText(placeholderText, centerX, textY, placeholderPaint)
                    
                    if (cardDescription != null) {
                        val descPaint = Paint().apply {
                            color = Color.argb((255 * 0.8f).toInt(), 255, 255, 255)
                            textSize = 32f
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                            textAlign = Paint.Align.CENTER
                            isAntiAlias = true
                        }
                        val descLines = breakTextIntoLines(cardDescription, descPaint, cardWidth - 40f)
                        var descY = textY + 50f
                        descLines.forEach { line ->
                            canvas.drawText(line, centerX, descY, descPaint)
                            descY += 40f
                        }
                    }
                }
            }
            
            // Position logo and text at the bottom - TEXT CENTERED IN SPACE BETWEEN LOGO AND RIGHT EDGE
            val logoDisplaySize = 60f // Display size (25% smaller, was 80f, now 60f = 80 * 0.75)
            val logoRenderSize = 240f // Render at 4x resolution for crisp quality (60 * 4 = 240)
            val bottomPadding = 120f // Increased to create purple buffer between logo and screen bottom
            val logoLeftEdge = 60f // Increased to prevent logo cutoff
            val rightPadding = 100f
            val logoTextSpacing = 80f // Space between logo and text
            
            // Logo positioning - ensure it's not cut off
            val logoCenterX = logoLeftEdge + logoDisplaySize / 2f
            val logoBottom = IMAGE_HEIGHT - bottomPadding
            val logoY = logoBottom - logoDisplaySize / 2f
            val logoRightEdge = logoLeftEdge + logoDisplaySize
            
            // Calculate text area: space between LOGO CENTER and right edge - CENTER TEXT IN THIS SPACE
            // Start text area from logo center + half logo size + spacing
            val textAreaStart = logoCenterX + (logoDisplaySize / 2f) + logoTextSpacing
            val textAreaEnd = IMAGE_WIDTH - rightPadding
            val textAreaWidth = textAreaEnd - textAreaStart
            // EXACT CENTER: midpoint of the text area, then shift 10% to the right
            val textAreaCenter = (textAreaStart + textAreaEnd) / 2f + (textAreaWidth * 0.1f)
            
            android.util.Log.d("CoachieShare", "TEXT CENTERING CALCULATION:")
            android.util.Log.d("CoachieShare", "  Logo right edge: $logoRightEdge")
            android.util.Log.d("CoachieShare", "  Logo-text spacing: $logoTextSpacing")
            android.util.Log.d("CoachieShare", "  Text area start: $textAreaStart")
            android.util.Log.d("CoachieShare", "  Image width: $IMAGE_WIDTH")
            android.util.Log.d("CoachieShare", "  Right padding: $rightPadding")
            android.util.Log.d("CoachieShare", "  Text area end: $textAreaEnd")
            android.util.Log.d("CoachieShare", "  Text area width: $textAreaWidth")
            android.util.Log.d("CoachieShare", "  TEXT CENTER X: $textAreaCenter")
            
            android.util.Log.d("CoachieShare", "=== DRAWING BOTTOM SECTION ===")
            android.util.Log.d("CoachieShare", "Logo: left=$logoLeftEdge, right=$logoRightEdge, centerX=$logoCenterX, Y=$logoY, bottom=$logoBottom")
            android.util.Log.d("CoachieShare", "Text area: start=$textAreaStart, end=$textAreaEnd, width=$textAreaWidth, center=$textAreaCenter")
            
            // USE FONT SIMILAR TO TITLE PAGE - Clean, modern, bold sans-serif
            val brandTitleTypeface = try {
                Typeface.create("sans-serif-medium", Typeface.BOLD) ?:
                Typeface.create("sans-serif", Typeface.BOLD) ?:
                Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) ?:
                Typeface.DEFAULT_BOLD
            } catch (e: Exception) {
                Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) ?: Typeface.DEFAULT_BOLD
            }
            
            // Draw "Coachie AI Health" text - CENTERED in text area, BELOW logo bottom
            val brandTitlePaint = Paint().apply {
                color = Color.WHITE
                textSize = 64f
                typeface = brandTitleTypeface
                textAlign = Paint.Align.CENTER // CRITICAL: Center alignment
                isAntiAlias = true
                isSubpixelText = true
                letterSpacing = 0.05f
            }
            
            val brandTitleText = "Coachie AI Health" // NO PERIOD
            // Position text to align vertically with logo center, with small buffer from bottom
            // Align text center with logo center vertically
            var textY = logoY + (logoDisplaySize / 2f) - 10f // Align with logo center, slightly above
            
            // ABSOLUTE CENTERING: Draw text at the exact center of the text area
            android.util.Log.d("CoachieShare", "Drawing title '$brandTitleText'")
            android.util.Log.d("CoachieShare", "  Text area: $textAreaStart to $textAreaEnd")
            android.util.Log.d("CoachieShare", "  Text center X: $textAreaCenter (should be ${(textAreaStart + textAreaEnd) / 2f})")
            android.util.Log.d("CoachieShare", "  Paint alignment: ${brandTitlePaint.textAlign}")
            
            // Draw text centered at textAreaCenter
            canvas.drawText(brandTitleText, textAreaCenter, textY, brandTitlePaint)
            
            // Draw "Currently on Google Play coming soon to IOS." text - CENTERED in text area, below title
            val brandSubtitleTypeface = try {
                Typeface.create("sans-serif", Typeface.NORMAL) ?:
                Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) ?:
                Typeface.DEFAULT
            } catch (e: Exception) {
                Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) ?: Typeface.DEFAULT
            }
            
            val brandSubtitlePaint = Paint().apply {
                color = Color.argb((255 * 0.9f).toInt(), 255, 255, 255)
                textSize = 34f
                typeface = brandSubtitleTypeface
                textAlign = Paint.Align.CENTER // CRITICAL: Center alignment
                isAntiAlias = true
                isSubpixelText = true
                letterSpacing = 0.03f
            }
            
            // Break subtitle into lines - handle explicit newlines first, then break if too long
            val brandSubtitleText = "coachieai.playspace.games\nComing soon to Android and iOS"
            val maxTextWidth = textAreaWidth - 20f // Available width with padding
            // Split by newline first, then break each part if needed
            val subtitleLines = brandSubtitleText.split("\n").flatMap { line ->
                breakTextIntoLines(line, brandSubtitlePaint, maxTextWidth)
            }
            
            android.util.Log.d("CoachieShare", "Subtitle: '$brandSubtitleText', maxWidth=$maxTextWidth, lines=${subtitleLines.size}")
            textY += 50f // Spacing after title
            subtitleLines.forEachIndexed { index, line ->
                android.util.Log.d("CoachieShare", "Drawing subtitle line $index: '$line' at X=$textAreaCenter (CENTERED), Y=$textY")
                canvas.drawText(line, textAreaCenter, textY, brandSubtitlePaint)
                textY += 38f
            }
            
            // Draw Coachie logo in lower left (proper padding, no cutoff)
            // Render at high resolution then scale down for crisp quality
            android.util.Log.d("CoachieShare", "Drawing Coachie logo in lower left at ($logoCenterX, $logoY)")
            drawCoachieLogoHighRes(canvas, logoCenterX, logoY, logoRenderSize, logoDisplaySize)
            android.util.Log.d("CoachieShare", "=== BOTTOM SECTION COMPLETE ===")
            
            android.util.Log.d("CoachieShare", "All drawing complete, saving bitmap to file")
            // Save bitmap to file
            val resultUri = saveBitmapToFile(bitmap, "promotional_post_${System.currentTimeMillis()}.png")
            if (resultUri != null) {
                android.util.Log.d("CoachieShare", "=== generatePromotionalPost SUCCESS: $resultUri ===")
            } else {
                android.util.Log.e("CoachieShare", "=== generatePromotionalPost FAILED: saveBitmapToFile returned null ===")
            }
            resultUri
        } catch (e: Exception) {
            android.util.Log.e("CoachieShare", "=== generatePromotionalPost EXCEPTION ===", e)
            android.util.Log.e("CoachieShare", "Exception type: ${e.javaClass.simpleName}")
            android.util.Log.e("CoachieShare", "Exception message: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Generate promotional post from a card screenshot file path
     */
    fun generatePromotionalPostFromFile(
        cardScreenshotPath: String?,
        cardTitle: String? = null,
        cardDescription: String? = null
    ): Uri? {
        val cardBitmap = cardScreenshotPath?.let { path ->
            try {
                android.graphics.BitmapFactory.decodeFile(path)
            } catch (e: Exception) {
                android.util.Log.e("ShareImageGenerator", "Error loading card screenshot", e)
                null
            }
        }
        return generatePromotionalPost(cardBitmap, cardTitle, cardDescription)
    }
    
    /**
     * Generate promotional post from a photo URI (for meal photos, etc.)
     * Can accept both a meal photo URI and a recipe card bitmap to display side by side
     */
    fun generatePromotionalPostFromUri(
        photoUri: Uri?,
        cardTitle: String? = null,
        cardDescription: String? = null,
        recipeCardBitmap: Bitmap? = null // Optional recipe card to display alongside meal photo
    ): Uri? {
        android.util.Log.d("CoachieShare", "=== generatePromotionalPostFromUri START ===")
        android.util.Log.d("CoachieShare", "photoUri: $photoUri")
        android.util.Log.d("CoachieShare", "cardTitle: $cardTitle")
        android.util.Log.d("CoachieShare", "cardDescription: $cardDescription")
        android.util.Log.d("CoachieShare", "recipeCardBitmap: ${if (recipeCardBitmap != null) "provided (${recipeCardBitmap.width}x${recipeCardBitmap.height})" else "null"}")
        
        val mealPhotoBitmap = photoUri?.let { uri ->
            try {
                android.util.Log.d("CoachieShare", "Opening input stream from URI")
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    android.util.Log.e("CoachieShare", "Failed to open input stream - inputStream is null")
                    null
                } else {
                    inputStream.use {
                        android.util.Log.d("CoachieShare", "Decoding bitmap from stream")
                        val bitmap = android.graphics.BitmapFactory.decodeStream(it)
                        if (bitmap == null) {
                            android.util.Log.e("CoachieShare", "Failed to decode bitmap - bitmap is null")
                            null
                        } else {
                            android.util.Log.d("CoachieShare", "Bitmap decoded successfully: ${bitmap.width}x${bitmap.height}")
                            // Fix orientation - check EXIF and rotate if needed
                            try {
                                val exif = android.media.ExifInterface(context.contentResolver.openInputStream(uri)!!)
                                val orientation = exif.getAttributeInt(
                                    android.media.ExifInterface.TAG_ORIENTATION,
                                    android.media.ExifInterface.ORIENTATION_NORMAL
                                )
                                android.util.Log.d("CoachieShare", "Image orientation: $orientation")
                                
                                val matrix = android.graphics.Matrix()
                                when (orientation) {
                                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                                    android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                                    android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                                }
                                
                                if (orientation != android.media.ExifInterface.ORIENTATION_NORMAL) {
                                    val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                    bitmap.recycle()
                                    rotatedBitmap
                                } else {
                                    bitmap
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("CoachieShare", "Could not read EXIF data, using original bitmap", e)
                                bitmap
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CoachieShare", "Exception loading photo from URI", e)
                android.util.Log.e("CoachieShare", "Exception type: ${e.javaClass.simpleName}")
                android.util.Log.e("CoachieShare", "Exception message: ${e.message}")
                e.printStackTrace()
                null
            }
        }
        android.util.Log.d("CoachieShare", "mealPhotoBitmap: ${if (mealPhotoBitmap != null) "loaded (${mealPhotoBitmap.width}x${mealPhotoBitmap.height})" else "null"}")
        // If recipe card is provided, use it as cardScreenshot; otherwise use meal photo as cardScreenshot (backward compatibility)
        return generatePromotionalPost(
            cardScreenshot = recipeCardBitmap,
            cardTitle = cardTitle,
            cardDescription = cardDescription,
            mealPhoto = mealPhotoBitmap
        )
    }
    
    /**
     * Generate a recipe card image from recipe data - includes full recipe details
     */
    fun generateRecipeCardImage(recipe: com.coachie.app.data.model.Recipe): Bitmap? {
        android.util.Log.d("CoachieShare", "=== generateRecipeCardImage START ===")
        android.util.Log.d("CoachieShare", "Recipe: ${recipe.name}, ${recipe.ingredients.size} ingredients")
        return try {
            val cardWidth = 500
            val cardHeight = 800 // TALLER to fit full recipe with instructions
            val bitmap = Bitmap.createBitmap(cardWidth, cardHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            android.util.Log.d("CoachieShare", "Created recipe card bitmap: ${cardWidth}x${cardHeight}")
            
            // Draw background
            val backgroundPaint = Paint().apply {
                color = Color.parseColor("#1A1A2E")
                isAntiAlias = true
            }
            canvas.drawRect(0f, 0f, cardWidth.toFloat(), cardHeight.toFloat(), backgroundPaint)
            
            val padding = 25f
            var currentY = padding
            
            // Draw recipe name
            val titlePaint = Paint().apply {
                color = Color.WHITE
                textSize = 28f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.LEFT
                isAntiAlias = true
            }
            val titleLines = breakTextIntoLines(recipe.name, titlePaint, cardWidth - (padding * 2))
            titleLines.forEach { line ->
                canvas.drawText(line, padding, currentY, titlePaint)
                currentY += 35f
            }
            
            currentY += 15f
            
            // Draw "Ingredients:" label
            val sectionLabelPaint = Paint().apply {
                color = Color.argb((255 * 0.95f).toInt(), 255, 255, 255)
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.LEFT
                isAntiAlias = true
            }
            canvas.drawText("Ingredients:", padding, currentY, sectionLabelPaint)
            currentY += 25f
            
            // Draw ingredients list
            val ingredientPaint = Paint().apply {
                color = Color.argb((255 * 0.85f).toInt(), 255, 255, 255)
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.LEFT
                isAntiAlias = true
            }
            
            recipe.ingredients.forEach { ingredient ->
                val quantityStr = if (ingredient.quantity % 1.0 == 0.0) {
                    ingredient.quantity.toInt().toString()
                } else {
                    String.format("%.1f", ingredient.quantity)
                }
                val ingredientText = "• $quantityStr ${ingredient.unit} ${ingredient.name}"
                val ingredientLines = breakTextIntoLines(ingredientText, ingredientPaint, cardWidth - (padding * 2) - 20f)
                ingredientLines.forEach { line ->
                    canvas.drawText(line, padding + 10f, currentY, ingredientPaint)
                    currentY += 20f
                }
            }
            
            currentY += 15f
            
            // Draw "Instructions:" label and instructions
            android.util.Log.d("CoachieShare", "Recipe instructions: ${recipe.instructions?.size ?: 0} steps")
            if (!recipe.instructions.isNullOrEmpty()) {
                android.util.Log.d("CoachieShare", "Drawing ${recipe.instructions.size} instructions")
                canvas.drawText("Instructions:", padding, currentY, sectionLabelPaint)
                currentY += 25f
                
                val instructionPaint = Paint().apply {
                    color = Color.argb((255 * 0.85f).toInt(), 255, 255, 255)
                    textSize = 16f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    textAlign = Paint.Align.LEFT
                    isAntiAlias = true
                }
                
                recipe.instructions.forEachIndexed { index, instruction ->
                    val instructionText = "${index + 1}. $instruction"
                    val instructionLines = breakTextIntoLines(instructionText, instructionPaint, cardWidth - (padding * 2) - 20f)
                    instructionLines.forEach { line ->
                        canvas.drawText(line, padding + 10f, currentY, instructionPaint)
                        currentY += 20f
                    }
                    currentY += 5f // Space between instructions
                }
                
                currentY += 10f
            }
            
            // Draw nutrition info section
            val perServing = recipe.getNutritionPerServing()
            canvas.drawText("Per Serving:", padding, currentY, sectionLabelPaint)
            currentY += 25f
            
            val nutritionPaint = Paint().apply {
                color = Color.argb((255 * 0.85f).toInt(), 255, 255, 255)
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.LEFT
                isAntiAlias = true
            }
            val nutritionText = listOf(
                "${perServing.calories} cal",
                "${perServing.proteinG}g protein",
                "${perServing.carbsG}g carbs",
                "${perServing.fatG}g fat"
            )
            nutritionText.forEach { text ->
                canvas.drawText(text, padding + 10f, currentY, nutritionPaint)
                currentY += 20f
            }
            
            // Draw servings info at bottom
            val servingsPaint = Paint().apply {
                color = Color.argb((255 * 0.8f).toInt(), 255, 255, 255)
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText("Serves ${recipe.servings}", cardWidth / 2f, cardHeight - 15f, servingsPaint)
            
            android.util.Log.d("CoachieShare", "=== generateRecipeCardImage SUCCESS ===")
            android.util.Log.d("CoachieShare", "Recipe card created: ${bitmap.width}x${bitmap.height}")
            android.util.Log.d("CoachieShare", "Recipe: ${recipe.name}, Ingredients: ${recipe.ingredients.size}, Serves: ${recipe.servings}")
            bitmap
        } catch (e: Exception) {
            android.util.Log.e("CoachieShare", "=== generateRecipeCardImage ERROR ===", e)
            android.util.Log.e("CoachieShare", "Error generating recipe card image", e)
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Save bitmap to file and return URI
     * CRITICAL: Always generates fresh images, no caching
     */
    private fun saveBitmapToFile(bitmap: Bitmap, filename: String): Uri? {
        android.util.Log.d("CoachieShare", "=== saveBitmapToFile START ===")
        android.util.Log.d("CoachieShare", "filename: $filename")
        android.util.Log.d("CoachieShare", "bitmap: ${bitmap.width}x${bitmap.height}, config: ${bitmap.config}")
        
        return try {
            val cacheDir = context.cacheDir
            android.util.Log.d("CoachieShare", "cacheDir: $cacheDir")
            
            val imagesDir = File(cacheDir, "share_images")
            android.util.Log.d("CoachieShare", "imagesDir: $imagesDir")
            
            if (!imagesDir.exists()) {
                android.util.Log.d("CoachieShare", "Creating imagesDir")
                imagesDir.mkdirs()
            }
            
            // CRITICAL: Delete ALL files in share_images directory to prevent ANY caching
            try {
                imagesDir.listFiles()?.forEach { oldFile ->
                    try {
                        val deleted = oldFile.delete()
                        android.util.Log.d("CoachieShare", "Deleted ALL cached image: ${oldFile.name} - $deleted")
                    } catch (e: Exception) {
                        android.util.Log.w("CoachieShare", "Error deleting ${oldFile.name}", e)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("CoachieShare", "Error deleting cached images (non-critical)", e)
            }
            
            // CRITICAL: Use completely unique filename with timestamp + nanoTime to prevent ANY caching
            val uniqueFilename = "promotional_${System.currentTimeMillis()}_${System.nanoTime()}.png"
            val imageFile = File(imagesDir, uniqueFilename)
            android.util.Log.d("CoachieShare", "Using unique filename: $uniqueFilename")
            android.util.Log.d("CoachieShare", "imageFile: $imageFile")
            
            // CRITICAL: Delete file if it exists (shouldn't, but just in case)
            if (imageFile.exists()) {
                android.util.Log.d("CoachieShare", "File already exists, deleting: ${imageFile.delete()}")
            }
            
            android.util.Log.d("CoachieShare", "Compressing bitmap to PNG")
            FileOutputStream(imageFile).use { out ->
                val compressed = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                android.util.Log.d("CoachieShare", "Bitmap compressed: $compressed")
            }
            
            android.util.Log.d("CoachieShare", "imageFile exists after write: ${imageFile.exists()}")
            android.util.Log.d("CoachieShare", "imageFile size: ${imageFile.length()} bytes")
            
            android.util.Log.d("CoachieShare", "Creating FileProvider URI")
            android.util.Log.d("CoachieShare", "packageName: ${context.packageName}")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
            android.util.Log.d("CoachieShare", "FileProvider URI created: $uri")
            android.util.Log.d("CoachieShare", "=== saveBitmapToFile SUCCESS: $uri ===")
            uri
        } catch (e: Exception) {
            android.util.Log.e("CoachieShare", "=== saveBitmapToFile EXCEPTION ===", e)
            android.util.Log.e("CoachieShare", "Exception type: ${e.javaClass.simpleName}")
            android.util.Log.e("CoachieShare", "Exception message: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}

