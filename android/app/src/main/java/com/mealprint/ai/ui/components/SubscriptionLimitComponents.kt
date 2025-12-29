package com.coachie.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Badge showing remaining AI feature calls for free tier users
 */
@Composable
fun RemainingCountBadge(
    remaining: Int,
    total: Int,
    featureName: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (remaining > 0) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$remaining/$total",
                fontWeight = FontWeight.Bold,
                color = if (remaining > 0) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$featureName remaining",
                style = MaterialTheme.typography.bodySmall,
                color = if (remaining > 0) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
        }
    }
}

/**
 * Progress bar showing usage of limited AI features
 */
@Composable
fun LimitProgressBar(
    used: Int,
    total: Int,
    featureName: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = featureName,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "$used/$total",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = (used.toFloat() / total.toFloat()).coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = when {
                used >= total -> MaterialTheme.colorScheme.error
                used >= total * 0.8f -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.primaryContainer
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
