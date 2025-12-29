package com.coachie.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coachie.app.ui.theme.Primary40

/**
 * Locked feature card component
 * 
 * Shows a greyed-out card with a lock icon for Pro-only features.
 * When clicked, shows an info dialog explaining the feature and upgrade option.
 */
@Composable
fun LockedFeatureCard(
    title: String,
    description: String,
    featureName: String,
    proBenefits: List<String>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    CoachieCard(
        modifier = modifier
            .fillMaxWidth()
            .alpha(0.6f) // Grey out the card
            .clickable(onClick = onClick),
        colors = CoachieCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = "Locked",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                IconButton(
                    onClick = onClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = "Learn more",
                        tint = Primary40
                    )
                }
            }
        }
    }
}

/**
 * Feature info dialog that explains a Pro feature
 */
@Composable
fun FeatureInfoDialog(
    featureName: String,
    description: String,
    proBenefits: List<String>,
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = Primary40,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = "$featureName (Pro)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Text(
                    text = "Pro Benefits:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Primary40
                )
                
                proBenefits.forEach { benefit ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = "• ",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Primary40
                        )
                        Text(
                            text = benefit,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpgrade,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary40
                )
            ) {
                Text("Upgrade to Pro", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Maybe Later")
            }
        }
    )
}

