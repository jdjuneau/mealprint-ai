package com.coachie.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * Confetti animation component that shows celebratory confetti particles
 */
@Composable
fun ConfettiAnimation(
    visible: Boolean,
    modifier: Modifier = Modifier,
    duration: Int = 2000,
    particleCount: Int = 50
) {
    if (!visible) return

    val particles = remember {
        (0 until particleCount).map {
            ConfettiParticle(
                x = Random.nextFloat(),
                y = -0.1f, // Start above screen
                velocityX = (Random.nextFloat() - 0.5f) * 0.02f,
                velocityY = Random.nextFloat() * 0.03f + 0.01f,
                rotation = Random.nextFloat() * 360f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 5f,
                color = getRandomColor(),
                size = Random.nextFloat() * 8f + 4f
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "confetti")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "confetti_progress"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            particles.forEachIndexed { index, particle ->
                val currentX = (particle.x + particle.velocityX * progress * 100) * width
                val currentY = (particle.y + particle.velocityY * progress * 100) * height
                val currentRotation = particle.rotation + particle.rotationSpeed * progress * 100

                // Draw confetti piece (diamond shape)
                val path = Path().apply {
                    val halfSize = particle.size / 2
                    moveTo(currentX, currentY - halfSize)
                    lineTo(currentX + halfSize, currentY)
                    lineTo(currentX, currentY + halfSize)
                    lineTo(currentX - halfSize, currentY)
                    close()
                }

                // Draw confetti piece (diamond shape) - simplified without rotation for now
                drawPath(path, particle.color)
            }
        }
    }
}

/**
 * Get a random color for confetti
 */
private fun getRandomColor(): Color {
    val colors = listOf(
        Color(0xFFFF6B6B), // Red
        Color(0xFF4ECDC4), // Teal
        Color(0xFF45B7D1), // Blue
        Color(0xFFFFA07A), // Light Salmon
        Color(0xFF98D8C8), // Mint
        Color(0xFFF7DC6F), // Yellow
        Color(0xFFBB8FCE), // Purple
        Color(0xFF85C1E2)  // Sky Blue
    )
    return colors[Random.nextInt(colors.size)]
}

/**
 * Data class for confetti particles
 */
private data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val velocityX: Float,
    val velocityY: Float,
    val rotation: Float,
    val rotationSpeed: Float,
    val color: Color,
    val size: Float
)
