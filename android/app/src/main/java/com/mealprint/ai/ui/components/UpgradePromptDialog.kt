package com.coachie.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mealprint.ai.ui.theme.Primary40

/**
 * Upgrade prompt dialog component
 * 
 * Use this to show upgrade prompts when users try to access Pro features.
 * This is for UX only - actual verification happens server-side.
 */
@Composable
fun UpgradePromptDialog(
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit,
    featureName: String? = null,
    remainingCalls: Int? = null,
    modifier: Modifier = Modifier
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                // Pro icon
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Primary40
                )
                
                // Title
                Text(
                    text = "Upgrade to Pro",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                // Feature-specific message
                if (featureName != null) {
                    Text(
                        text = "$featureName is available for Pro subscribers",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                
                // Limit message
                if (remainingCalls != null && remainingCalls == 0) {
                    Text(
                        text = "You've reached your daily limit. Upgrade for unlimited access!",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (remainingCalls != null) {
                    Text(
                        text = "You have $remainingCalls requests remaining today. Upgrade for unlimited access!",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                
                // Benefits list
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProFeatureItem("✨ Unlimited AI-generated weekly blueprints")
                    ProFeatureItem("🤖 Unlimited AI meal recommendations")
                    ProFeatureItem("📊 Unlimited daily insights")
                    ProFeatureItem("🌅 Personalized morning briefs")
                    ProFeatureItem("📈 Monthly AI insights")
                    ProFeatureItem("🎯 AI quest generation")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Upgrade button
                Button(
                    onClick = onUpgrade,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary40
                    )
                ) {
                    Text(
                        text = "Upgrade to Pro",
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Cancel button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Maybe Later")
                }
            }
        }
    }
}

@Composable
private fun ProFeatureItem(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Primary40,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}

