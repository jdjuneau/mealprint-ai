package com.coachie.app.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Service for detecting body pose landmarks using ML Kit
 * Extracts measurements for shoulders, hips, and waist from body scan photos
 */
class PoseDetectionService(context: Context) {

    // Configure ML Kit pose detector with accurate model
    private val options = AccuratePoseDetectorOptions.Builder()
        .setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE)
        .build()

    private val poseDetector: PoseDetector = PoseDetection.getClient(options)

    /**
     * Analyze a photo and extract body measurements
     */
    suspend fun analyzeBodyMeasurements(
        photoPath: String,
        knownHeightCm: Float? = null
    ): BodyMeasurements? {
        return try {
            // Load and decode the image
            val bitmap = BitmapFactory.decodeFile(photoPath) ?: return null
            val image = InputImage.fromBitmap(bitmap, 0)

            // Run pose detection
            val pose = suspendCancellableCoroutine { continuation ->
                poseDetector.process(image)
                    .addOnSuccessListener { pose ->
                        continuation.resume(pose)
                    }
                    .addOnFailureListener { exception ->
                        continuation.resumeWithException(exception)
                    }
            }

            // Extract key landmarks
            val leftShoulder = pose.getPoseLandmark(com.google.mlkit.vision.pose.PoseLandmark.LEFT_SHOULDER)
            val rightShoulder = pose.getPoseLandmark(com.google.mlkit.vision.pose.PoseLandmark.RIGHT_SHOULDER)
            val leftHip = pose.getPoseLandmark(com.google.mlkit.vision.pose.PoseLandmark.LEFT_HIP)
            val rightHip = pose.getPoseLandmark(com.google.mlkit.vision.pose.PoseLandmark.RIGHT_HIP)

            // Calculate measurements if all required landmarks are detected
            if (leftShoulder != null && rightShoulder != null && leftHip != null && rightHip != null) {
                val shoulderWidth = calculateDistance(
                    leftShoulder.position.x, leftShoulder.position.y,
                    rightShoulder.position.x, rightShoulder.position.y
                )

                val hipWidth = calculateDistance(
                    leftHip.position.x, leftHip.position.y,
                    rightHip.position.x, rightHip.position.y
                )

                // Estimate waist width (simplified - midway between shoulders and hips)
                val waistWidth = (shoulderWidth + hipWidth) / 2f

                val shoulderToHipRatio = shoulderWidth / hipWidth

                return BodyMeasurements(
                    shoulderWidth = shoulderWidth,
                    hipWidth = hipWidth,
                    waistWidth = waistWidth,
                    shoulderToHipRatio = shoulderToHipRatio,
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    knownHeightCm = knownHeightCm
                )
            }

            null // Not enough landmarks detected

        } catch (e: Exception) {
            android.util.Log.e("PoseDetectionService", "Pose detection failed", e)
            null
        }
    }

    /**
     * Calculate Euclidean distance between two points
     */
    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /**
     * Clean up resources
     */
    fun close() {
        poseDetector.close()
    }
}

/**
 * Data class containing extracted body measurements from pose detection
 */
data class BodyMeasurements(
    val shoulderWidth: Float,      // Distance between shoulder landmarks (pixels)
    val hipWidth: Float,           // Distance between hip landmarks (pixels)
    val waistWidth: Float,         // Estimated waist width (pixels)
    val shoulderToHipRatio: Float, // Ratio of shoulder to hip width
    val imageWidth: Int,           // Original image width
    val imageHeight: Int,          // Original image height
    val knownHeightCm: Float? = null // User's known height for scaling (optional)
) {
    /**
     * Convert pixel measurements to approximate centimeters
     * This is a rough estimation and would need calibration in production
     */
    fun toCentimeters(): BodyMeasurementsCm? {
        if (knownHeightCm == null) return null

        // Assume the person's height spans roughly 80% of the image height
        // This is a very rough approximation
        val pixelsPerCm = (imageHeight * 0.8f) / knownHeightCm

        return BodyMeasurementsCm(
            shoulderWidthCm = shoulderWidth / pixelsPerCm,
            hipWidthCm = hipWidth / pixelsPerCm,
            waistWidthCm = waistWidth / pixelsPerCm,
            shoulderToHipRatio = shoulderToHipRatio,
            knownHeightCm = knownHeightCm
        )
    }
}

/**
 * Body measurements in centimeters
 */
data class BodyMeasurementsCm(
    val shoulderWidthCm: Float,
    val hipWidthCm: Float,
    val waistWidthCm: Float,
    val shoulderToHipRatio: Float,
    val knownHeightCm: Float
)
