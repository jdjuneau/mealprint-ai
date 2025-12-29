package com.coachie.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.mealprint.ai.ui.theme.Accent40
import com.mealprint.ai.ui.theme.Accent90
import com.mealprint.ai.ui.theme.Primary40
import com.mealprint.ai.ui.theme.Primary90
import androidx.compose.material3.MaterialTheme

enum class GradientStyle {
    PRIMARY_ENERGY,    // Orange to yellow - energetic
    CALM_SUCCESS,      // Green tones - calming, success
    CREATIVE_FLOW,     // Blue to purple - creative, flow
    MOTIVATIONAL,      // Multi-color motivational
    SOFT_WARM,         // Gentle warm tones
    COOL_FRESH         // Cool refreshing tones
}

@Composable
fun GradientBackground(
    style: GradientStyle = GradientStyle.PRIMARY_ENERGY,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val gradientColors = when (style) {
        GradientStyle.PRIMARY_ENERGY -> listOf(
            Color(0xFFF9FBFF),
            Accent90.copy(alpha = 0.6f),
            Primary40.copy(alpha = 0.45f)
        )
        GradientStyle.CALM_SUCCESS -> listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiaryContainer
        )
        GradientStyle.CREATIVE_FLOW -> listOf(
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary
        )
        GradientStyle.MOTIVATIONAL -> listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.primaryContainer
        )
        GradientStyle.SOFT_WARM -> listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer
        )
        GradientStyle.COOL_FRESH -> listOf(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.primaryContainer
        )
    }

    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = gradientColors
            )
        ),
        content = content
    )
}

@Composable
fun FunBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    // Default to a fun, energetic gradient
    GradientBackground(
        style = GradientStyle.PRIMARY_ENERGY,
        modifier = modifier,
        content = content
    )
}
