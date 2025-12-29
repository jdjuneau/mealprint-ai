package com.coachie.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

/**
 * A modifier that highlights recompositions by changing background color
 */
fun Modifier.recompositionHighlighter(): Modifier = composed {
    val color = Color(
        red = Random.nextFloat(),
        green = Random.nextFloat(),
        blue = Random.nextFloat(),
        alpha = 0.3f
    )

    drawWithContent {
        drawRect(color = color)
        drawContent()
    }
}

/**
 * A wrapper composable that highlights recompositions
 */
@Composable
fun RecompositionHighlighter(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    SideEffect {
        println("🔄 RecompositionHighlighter: Composable recomposed")
    }

    androidx.compose.foundation.layout.Box(
        modifier = modifier.recompositionHighlighter()
    ) {
        content()
    }
}
