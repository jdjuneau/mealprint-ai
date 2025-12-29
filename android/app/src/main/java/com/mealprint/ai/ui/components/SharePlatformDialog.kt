package com.coachie.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.net.Uri

@Composable
fun SharePlatformDialog(
    onDismiss: () -> Unit,
    onShareToPlatform: (String?) -> Unit,
    photoUri: Uri? = null,
    onCapturePhoto: (() -> Unit)? = null,
    onSelectPhoto: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share Your Accomplishment") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Photo section
                if (photoUri == null) {
                    Text(
                        "Add a photo (optional)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Camera button
                    if (onCapturePhoto != null) {
                        Button(
                            onClick = onCapturePhoto,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Camera,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Take Photo", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    
                    // Gallery button
                    if (onSelectPhoto != null) {
                        OutlinedButton(
                            onClick = onSelectPhoto,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Choose from Gallery", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    Text(
                        "✓ Photo ready",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onCapturePhoto != null) {
                            OutlinedButton(
                                onClick = onCapturePhoto,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Camera,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Change", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                
                Text(
                    "Choose where to share:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Facebook button
                Button(
                    onClick = { onShareToPlatform("facebook") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1877F2) // Facebook blue
                    )
                ) {
                    Text("Share to Facebook", color = Color.White)
                }
                
                // Instagram Feed button
                Button(
                    onClick = { onShareToPlatform("instagram-feed") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE4405F) // Instagram pink
                    )
                ) {
                    Text("Share to Instagram Feed", color = Color.White)
                }
                
                // Instagram Story button
                Button(
                    onClick = { onShareToPlatform("instagram-story") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF833AB4) // Instagram purple
                    )
                ) {
                    Text("Share to Instagram Story", color = Color.White)
                }
                
                // TikTok button
                Button(
                    onClick = { onShareToPlatform("tiktok") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF000000) // TikTok black
                    )
                ) {
                    Text("Share to TikTok", color = Color.White)
                }
                
                // X (Twitter) button
                Button(
                    onClick = { onShareToPlatform("x") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF000000) // X black
                    )
                ) {
                    Text("Share to X", color = Color.White)
                }
                
                // Other/Native share button
                OutlinedButton(
                    onClick = { onShareToPlatform(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Other Options")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

