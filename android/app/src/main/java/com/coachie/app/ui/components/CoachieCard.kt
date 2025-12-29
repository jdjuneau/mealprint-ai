package com.coachie.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.CardColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

object CoachieCardDefaults {
    val Shape: Shape = RoundedCornerShape(24.dp)

    @Composable
    fun colors(
        containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f),
        contentColor: Color = MaterialTheme.colorScheme.onSurface,
        disabledContainerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.05f),
        disabledContentColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    ): CardColors = CardDefaults.cardColors(
        containerColor = containerColor,
        contentColor = contentColor,
        disabledContainerColor = disabledContainerColor,
        disabledContentColor = disabledContentColor
    )

    @Composable
    fun cardColors(
        containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f),
        contentColor: Color = MaterialTheme.colorScheme.onSurface,
        disabledContainerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.05f),
        disabledContentColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    ): CardColors = colors(containerColor, contentColor, disabledContainerColor, disabledContentColor)

    @Composable
    fun outlinedCardColors(
        containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.05f),
        contentColor: Color = MaterialTheme.colorScheme.onSurface,
        disabledContainerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.02f),
        disabledContentColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    ): CardColors = CardDefaults.outlinedCardColors(
        containerColor = containerColor,
        contentColor = contentColor,
        disabledContainerColor = disabledContainerColor,
        disabledContentColor = disabledContentColor
    )

    @Composable
    fun cardElevation(defaultElevation: Dp = 0.dp): CardElevation = CardDefaults.cardElevation(defaultElevation)

    @Composable
    fun border(
        color: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        width: Float = 1f
    ): BorderStroke = BorderStroke(width.dp, color)

    @Composable
    fun outlinedCardBorder(
        width: Dp = 1.dp,
        color: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
    ): BorderStroke = BorderStroke(width, color)

    @Composable
    fun outlinedCardBorder(
        enabled: Boolean,
        width: Dp = 1.dp,
        activeColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        inactiveColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    ): BorderStroke = BorderStroke(width, if (enabled) activeColor else inactiveColor)
}

@Composable
fun CoachieCard(
    modifier: Modifier = Modifier,
    shape: Shape = CoachieCardDefaults.Shape,
    colors: CardColors? = null,
    border: BorderStroke? = null,
    applyDefaultBorder: Boolean = true,
    elevation: CardElevation? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val cardInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    
    // Resolve values - call composable functions directly (not inside remember)
    val resolvedColors = colors ?: CoachieCardDefaults.colors()
    val resolvedBorder = when {
        border != null -> border
        applyDefaultBorder -> CoachieCardDefaults.border()
        else -> null
    }
    val resolvedElevation = elevation ?: CoachieCardDefaults.cardElevation()

    if (onClick != null) {
        Card(
            modifier = modifier,
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            colors = resolvedColors,
            border = resolvedBorder,
            elevation = resolvedElevation,
            interactionSource = cardInteractionSource
        ) {
            content()
        }
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = resolvedColors,
            border = resolvedBorder,
            elevation = resolvedElevation
        ) {
            content()
        }
    }
}

