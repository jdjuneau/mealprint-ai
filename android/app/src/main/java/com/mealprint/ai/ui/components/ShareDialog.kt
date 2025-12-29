package com.coachie.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.net.Uri
import com.mealprint.ai.data.model.PublicUserProfile
import com.mealprint.ai.data.model.Recipe

/**
 * Reusable share dialog component for sharing items with friends
 */
@Composable
fun ShareDialog(
    title: String,
    friends: List<PublicUserProfile>,
    selectedFriends: Set<String>,
    onFriendToggle: (String) -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Select friends to share with:",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                if (friends.isEmpty()) {
                    Text(
                        "No friends yet. Add friends to share!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(friends) { friend ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onFriendToggle(friend.uid) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedFriends.contains(friend.uid),
                                    onCheckedChange = { onFriendToggle(friend.uid) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    friend.displayName ?: friend.username ?: "Unknown",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onShare,
                enabled = selectedFriends.isNotEmpty()
            ) {
                Text("Share")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Recipe share dialog with options to share with friends, post to forum, or share to circle
 * Now includes photo capture encouragement for recipe sharing
 */
@Composable
fun RecipeShareDialog(
    recipe: Recipe,
    friends: List<PublicUserProfile>,
    selectedFriends: Set<String>,
    onFriendToggle: (String) -> Unit,
    onShareWithFriends: () -> Unit,
    onShareToForum: () -> Unit,
    onShareToCircle: () -> Unit,
    onDismiss: () -> Unit,
    isSharingToForum: Boolean = false,
    isSharingToCircle: Boolean = false,
    mealPhotoUri: Uri? = null, // Optional photo of the cooked meal
    onCapturePhoto: (() -> Unit)? = null, // Callback to capture photo
    onSelectPhoto: (() -> Unit)? = null, // Callback to select photo from gallery
    onShareToInstagram: (() -> Unit)? = null, // Share to Instagram
    onShareToFacebook: (() -> Unit)? = null, // Share to Facebook
    onShareToTikTok: (() -> Unit)? = null, // Share to TikTok
    onShareToX: (() -> Unit)? = null, // Share to X (Twitter)
    onShareToOther: (() -> Unit)? = null // Share to other platforms (native share)
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Share Recipe: ${recipe.name}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // External Social Media Sharing Section
                Text(
                    "Share to Social Media",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                if (mealPhotoUri == null) {
                    // Both photo options side by side
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Take Photo button
                        Button(
                            onClick = { onCapturePhoto?.invoke() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            enabled = onCapturePhoto != null
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Camera,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Take Photo", style = MaterialTheme.typography.bodyMedium)
                        }
                        
                        // Upload Photo button
                        Button(
                            onClick = { onSelectPhoto?.invoke() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            enabled = onSelectPhoto != null
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Upload Photo", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    
                    Text(
                        "Take a photo or upload one from your gallery to include in your social media post. The post will include the Coachie logo and a link to Google Play. You can also share without a photo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    // Photo selected - show social media buttons
                    Text(
                        "✓ Photo ready",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Instagram Feed
                        OutlinedButton(
                            onClick = { onShareToInstagram?.invoke() },
                            modifier = Modifier.weight(1f),
                            enabled = onShareToInstagram != null
                        ) {
                            Text("📷 Instagram", style = MaterialTheme.typography.bodySmall)
                        }
                        
                        // Facebook
                        OutlinedButton(
                            onClick = { onShareToFacebook?.invoke() },
                            modifier = Modifier.weight(1f),
                            enabled = onShareToFacebook != null
                        ) {
                            Text("👥 Facebook", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // TikTok
                        OutlinedButton(
                            onClick = { onShareToTikTok?.invoke() },
                            modifier = Modifier.weight(1f),
                            enabled = onShareToTikTok != null
                        ) {
                            Text("🎵 TikTok", style = MaterialTheme.typography.bodySmall)
                        }
                        
                        // X (Twitter)
                        OutlinedButton(
                            onClick = { onShareToX?.invoke() },
                            modifier = Modifier.weight(1f),
                            enabled = onShareToX != null
                        ) {
                            Text("X", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Other/Native Share
                        OutlinedButton(
                            onClick = { onShareToOther?.invoke() },
                            modifier = Modifier.weight(1f),
                            enabled = onShareToOther != null
                        ) {
                            Text("📤 More", style = MaterialTheme.typography.bodySmall)
                        }
                        
                        // Change photo button
                        OutlinedButton(
                            onClick = { onCapturePhoto?.invoke() },
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
                
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                Text(
                    "Share within Coachie",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                // Share to Forum button
                Button(
                    onClick = onShareToForum,
                    enabled = !isSharingToForum && !isSharingToCircle,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSharingToForum) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Posting to Forum...")
                    } else {
                        Icon(Icons.Filled.Forum, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Post to Recipe Sharing Forum")
                    }
                }
                
                // Share to Circle button
                Button(
                    onClick = onShareToCircle,
                    enabled = !isSharingToForum && !isSharingToCircle,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSharingToCircle) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Sharing to Circle...")
                    } else {
                        Icon(Icons.Filled.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share to Recipe Sharing Circle")
                    }
                }
                
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                Text(
                    "Share with Friends",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                if (friends.isEmpty()) {
                    Text(
                        "No friends yet. Add friends to share recipes!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(friends) { friend ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onFriendToggle(friend.uid) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedFriends.contains(friend.uid),
                                    onCheckedChange = { onFriendToggle(friend.uid) },
                                    enabled = !isSharingToForum && !isSharingToCircle
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    friend.displayName ?: friend.username ?: "Unknown",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onShareWithFriends,
                enabled = selectedFriends.isNotEmpty() && !isSharingToForum && !isSharingToCircle
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share with Friends")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

