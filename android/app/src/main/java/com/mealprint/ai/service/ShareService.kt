package com.coachie.app.service

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.app.ShareCompat
import androidx.core.content.ContextCompat
import com.mealprint.ai.util.ImageWatermarkUtil
import java.io.File

/**
 * Share image data for generating shareable images
 */
data class ShareImageData(
    val type: ShareImageType,
    val title: String,
    val metric: String, // Can be number or string
    val subtitle: String? = null,
    val icon: String? = null,
    val color: String? = null,
    val streakFlame: Int? = null,
    val progressRing: Int? = null // 0-100
)

enum class ShareImageType {
    READINESS,
    STREAK,
    QUEST,
    INSIGHT,
    CIRCLE_WIN
}

/**
 * Share service for social media sharing (Instagram, Facebook, TikTok, native share)
 */
class ShareService private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ShareService"
        @Volatile
        private var INSTANCE: ShareService? = null
        
        fun getInstance(context: Context): ShareService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ShareService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Generate hashtags for social media sharing
     */
    private fun getHashtags(): String {
        return "#coachie #health #healthy #fitness #wellness #nutrition #workout #fit #healthylifestyle #wellnessjourney #healthtracking #aihealth #coachieai"
    }
    
    /**
     * Generate share text with hashtags
     */
    private fun getShareTextWithHashtags(baseText: String? = null): String {
        val base = baseText ?: "Tracked with Coachie → coachie.app"
        return "$base\n\n${getHashtags()}"
    }
    
    /**
     * Share with Activity context - use this when you have an Activity context available
     */
    fun generateAndShareWithContext(
        data: ShareImageData,
        activityContext: Context,
        platform: String? = null,
        photoUri: Uri? = null
    ) {
        generateAndShareInternal(data, activityContext, platform, photoUri)
    }
    
    /**
     * Share to Instagram Story
     * Android: Uses Intent to share image (with logo watermark)
     */
    fun shareToInstagramStory(imageUri: Uri, deepLink: String? = null, addWatermark: Boolean = true, activityContext: Context? = null) {
        try {
            val finalImageUri = if (addWatermark) {
                ImageWatermarkUtil.addLogoWatermark(context, imageUri) ?: imageUri
            } else {
                imageUri
            }
            
            val shareContext = activityContext ?: context
            val instagramPackages = listOf("com.instagram.android", "com.instagram.lite")
            
            var instagramFound = false
            for (packageName in instagramPackages) {
                try {
                    // Grant temporary read permission to Instagram
                    shareContext.grantUriPermission(
                        packageName,
                        finalImageUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    
                    // Try Instagram Story intent first
                    val storyIntent = Intent("com.instagram.share.ADD_TO_STORY").apply {
                        setDataAndType(finalImageUri, "image/*")
                        putExtra("contentUrl", deepLink ?: "coachie.app")
                        putExtra("topBackgroundColor", "#667eea")
                        putExtra("bottomBackgroundColor", "#764ba2")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        setPackage(packageName)
                    }
                    
                    // Fallback to regular share if Story intent doesn't work
                    val regularIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, finalImageUri)
                        clipData = android.content.ClipData.newUri(shareContext.contentResolver, "Image", finalImageUri)
                        putExtra(Intent.EXTRA_TEXT, getShareTextWithHashtags(deepLink ?: "Tracked with Coachie → coachie.app"))
                        setPackage(packageName)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    
                    // Just try to start Story intent - if it fails, use regular share
                    try {
                        android.util.Log.d("CoachieShare", "Trying Instagram Story intent for $packageName")
                        if (activityContext != null) {
                            shareContext.startActivity(storyIntent)
                        } else {
                            storyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            shareContext.startActivity(storyIntent)
                        }
                        android.util.Log.d("CoachieShare", "✅ Successfully opened Instagram Story: $packageName")
                        instagramFound = true
                        return
                    } catch (e: android.content.ActivityNotFoundException) {
                        android.util.Log.d("CoachieShare", "Instagram Story not available for $packageName, trying regular share")
                        if (activityContext != null) {
                            shareContext.startActivity(regularIntent)
                        } else {
                            regularIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            shareContext.startActivity(regularIntent)
                        }
                        android.util.Log.d("CoachieShare", "✅ Successfully opened Instagram Feed: $packageName")
                        instagramFound = true
                        return
                    }
                } catch (e: android.content.ActivityNotFoundException) {
                    android.util.Log.d("CoachieShare", "Instagram package $packageName not found, trying next")
                } catch (e: Exception) {
                    android.util.Log.w("CoachieShare", "Error opening Instagram $packageName", e)
                }
            }
            
            // If direct package attempts failed, try querying all apps that can handle the intent
            if (!instagramFound) {
                try {
                    val queryIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                    }
                    val resolveInfos = shareContext.packageManager.queryIntentActivities(queryIntent, 0)
                    val instagramResolveInfo = resolveInfos.firstOrNull { 
                        it.activityInfo.packageName.startsWith("com.instagram") 
                    }
                    
                    if (instagramResolveInfo != null) {
                        val packageName = instagramResolveInfo.activityInfo.packageName
                        android.util.Log.d("CoachieShare", "Found Instagram via query: $packageName")
                        
                        shareContext.grantUriPermission(
                            packageName,
                            finalImageUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        
                        // Try Story intent first
                        val storyIntent = Intent("com.instagram.share.ADD_TO_STORY").apply {
                            setDataAndType(finalImageUri, "image/*")
                            putExtra("contentUrl", deepLink ?: "coachie.app")
                            putExtra("topBackgroundColor", "#667eea")
                            putExtra("bottomBackgroundColor", "#764ba2")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            setPackage(packageName)
                        }
                        
                        val regularIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, finalImageUri)
                            clipData = android.content.ClipData.newUri(shareContext.contentResolver, "Image", finalImageUri)
                            putExtra(Intent.EXTRA_TEXT, getShareTextWithHashtags(deepLink ?: "Tracked with Coachie → coachie.app"))
                            setClassName(packageName, instagramResolveInfo.activityInfo.name)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        
                        try {
                            if (activityContext != null) {
                                shareContext.startActivity(storyIntent)
                            } else {
                                storyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                shareContext.startActivity(storyIntent)
                            }
                            android.util.Log.d("CoachieShare", "✅ Successfully opened Instagram Story via query")
                            return
                        } catch (e: Exception) {
                            if (activityContext != null) {
                                shareContext.startActivity(regularIntent)
                            } else {
                                regularIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                shareContext.startActivity(regularIntent)
                            }
                            android.util.Log.d("CoachieShare", "✅ Successfully opened Instagram Feed via query")
                            return
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("CoachieShare", "Error querying for Instagram", e)
                }
                
                android.util.Log.w("CoachieShare", "Instagram app not found (tried: ${instagramPackages.joinToString()}), using fallback")
                shareImage(finalImageUri, "Share to Instagram", "Open Instagram and share this image to your story", addWatermark = false, activityContext = activityContext)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing to Instagram Story", e)
            android.util.Log.e("CoachieShare", "Exception in shareToInstagramStory", e)
            e.printStackTrace()
            shareImage(imageUri, "Share to Instagram", "Open Instagram and share this image", addWatermark = false, activityContext = activityContext)
        }
    }
    
    /**
     * Share to Instagram Feed (with logo watermark)
     * NOTE: Instagram doesn't support pre-filled captions via EXTRA_TEXT, so we copy hashtags to clipboard
     */
    fun shareToInstagramFeed(imageUri: Uri, deepLink: String? = null, addWatermark: Boolean = true, activityContext: Context? = null) {
        try {
            val finalImageUri = if (addWatermark) {
                ImageWatermarkUtil.addLogoWatermark(context, imageUri) ?: imageUri
            } else {
                imageUri
            }
            
            val shareContext = activityContext ?: context
            
            // CRITICAL: Instagram doesn't support EXTRA_TEXT for images, so copy hashtags to clipboard
            val hashtags = getHashtags()
            val clipboard = ContextCompat.getSystemService(shareContext, ClipboardManager::class.java)
            clipboard?.let {
                val clip = ClipData.newPlainText("Coachie Hashtags", hashtags)
                it.setPrimaryClip(clip)
                android.util.Log.d("CoachieShare", "✅ Hashtags copied to clipboard for Instagram")
                // Show toast to user
                if (activityContext != null) {
                    Toast.makeText(activityContext, "Hashtags copied! Paste them in your Instagram caption.", Toast.LENGTH_LONG).show()
                }
            }
            val instagramPackages = listOf("com.instagram.android", "com.instagram.lite")
            
            var instagramFound = false
            for (packageName in instagramPackages) {
                try {
                    // Grant temporary read permission to Instagram
                    shareContext.grantUriPermission(
                        packageName,
                        finalImageUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, finalImageUri)
                        clipData = android.content.ClipData.newUri(shareContext.contentResolver, "Image", finalImageUri)
                        putExtra(Intent.EXTRA_TEXT, getShareTextWithHashtags(deepLink ?: "Tracked with Coachie → coachie.app"))
                        setPackage(packageName)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    
                    android.util.Log.d("CoachieShare", "Attempting to open Instagram Feed: $packageName")
                    if (activityContext != null) {
                        shareContext.startActivity(intent)
                    } else {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        shareContext.startActivity(intent)
                    }
                    android.util.Log.d("CoachieShare", "✅ Successfully opened Instagram Feed: $packageName")
                    instagramFound = true
                    return
                } catch (e: android.content.ActivityNotFoundException) {
                    android.util.Log.d("CoachieShare", "Instagram package $packageName not found, trying next")
                } catch (e: Exception) {
                    android.util.Log.w("CoachieShare", "Error opening Instagram $packageName", e)
                }
            }
            
            // If direct package attempts failed, try querying all apps that can handle the intent
            if (!instagramFound) {
                try {
                    val queryIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                    }
                    val resolveInfos = shareContext.packageManager.queryIntentActivities(queryIntent, 0)
                    val instagramResolveInfo = resolveInfos.firstOrNull { 
                        it.activityInfo.packageName.startsWith("com.instagram") 
                    }
                    
                    if (instagramResolveInfo != null) {
                        val packageName = instagramResolveInfo.activityInfo.packageName
                        android.util.Log.d("CoachieShare", "Found Instagram via query: $packageName")
                        
                        shareContext.grantUriPermission(
                            packageName,
                            finalImageUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, finalImageUri)
                            clipData = android.content.ClipData.newUri(shareContext.contentResolver, "Image", finalImageUri)
                            putExtra(Intent.EXTRA_TEXT, getShareTextWithHashtags(deepLink ?: "Tracked with Coachie → coachie.app"))
                            setClassName(packageName, instagramResolveInfo.activityInfo.name)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        
                        if (activityContext != null) {
                            shareContext.startActivity(intent)
                        } else {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            shareContext.startActivity(intent)
                        }
                        android.util.Log.d("CoachieShare", "✅ Successfully opened Instagram via query: $packageName")
                        return
                    }
                } catch (e: Exception) {
                    android.util.Log.w("CoachieShare", "Error querying for Instagram", e)
                }
                
                android.util.Log.w("CoachieShare", "Instagram app not found (tried: ${instagramPackages.joinToString()}), using fallback")
                shareImage(finalImageUri, "Share to Instagram", "Select Instagram to share", addWatermark = false, activityContext = activityContext)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing to Instagram Feed", e)
            android.util.Log.e("CoachieShare", "Exception in shareToInstagramFeed", e)
            e.printStackTrace()
            shareImage(imageUri, "Share to Instagram", "Select Instagram to share", addWatermark = false, activityContext = activityContext)
        }
    }
    
    /**
     * Share to Facebook (with logo watermark)
     */
    fun shareToFacebook(imageUri: Uri, deepLink: String? = null, addWatermark: Boolean = true, activityContext: Context? = null) {
        try {
            val finalImageUri = if (addWatermark) {
                ImageWatermarkUtil.addLogoWatermark(context, imageUri) ?: imageUri
            } else {
                imageUri
            }
            
            val shareContext = activityContext ?: context
            
            // Try multiple Facebook package names - check if installed first, then try to open
            val facebookPackages = listOf(
                "com.facebook.katana", // Main Facebook app
                "com.facebook.lite",    // Facebook Lite
                "com.facebook.orca"     // Facebook Messenger (can share too)
            )
            
            var facebookFound = false
            for (packageName in facebookPackages) {
                try {
                    // Grant temporary read permission to Facebook
                    shareContext.grantUriPermission(
                        packageName,
                        finalImageUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    
                    // CRITICAL: Use ClipData for better compatibility with Facebook
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, finalImageUri)
                        clipData = android.content.ClipData.newUri(shareContext.contentResolver, "Image", finalImageUri)
                        putExtra(Intent.EXTRA_TEXT, getShareTextWithHashtags(deepLink ?: "Tracked with Coachie → coachie.app"))
                        setPackage(packageName)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    
                    // CRITICAL: Just try to start it - if it fails, catch the exception
                    // This is the most reliable method
                    android.util.Log.d("CoachieShare", "Attempting to open Facebook package: $packageName")
                    if (activityContext != null) {
                        shareContext.startActivity(intent)
                    } else {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        shareContext.startActivity(intent)
                    }
                    android.util.Log.d("CoachieShare", "✅ Successfully opened Facebook: $packageName")
                    facebookFound = true
                    return
                } catch (e: android.content.ActivityNotFoundException) {
                    android.util.Log.d("CoachieShare", "Facebook package $packageName not found or cannot handle intent: ${e.message}")
                } catch (e: Exception) {
                    android.util.Log.w("CoachieShare", "Error opening Facebook package $packageName", e)
                }
            }
            
            // If direct package attempts failed, try querying all apps that can handle the intent
            if (!facebookFound) {
                try {
                    val queryIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                    }
                    val resolveInfos = shareContext.packageManager.queryIntentActivities(queryIntent, 0)
                    val facebookResolveInfo = resolveInfos.firstOrNull { 
                        it.activityInfo.packageName.startsWith("com.facebook") 
                    }
                    
                    if (facebookResolveInfo != null) {
                        val packageName = facebookResolveInfo.activityInfo.packageName
                        android.util.Log.d("CoachieShare", "Found Facebook via query: $packageName")
                        
                        shareContext.grantUriPermission(
                            packageName,
                            finalImageUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, finalImageUri)
                            clipData = android.content.ClipData.newUri(shareContext.contentResolver, "Image", finalImageUri)
                            putExtra(Intent.EXTRA_TEXT, getShareTextWithHashtags(deepLink ?: "Tracked with Coachie → coachie.app"))
                            setClassName(packageName, facebookResolveInfo.activityInfo.name)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        
                        if (activityContext != null) {
                            shareContext.startActivity(intent)
                        } else {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            shareContext.startActivity(intent)
                        }
                        android.util.Log.d("CoachieShare", "✅ Successfully opened Facebook via query: $packageName")
                        return
                    }
                } catch (e: Exception) {
                    android.util.Log.w("CoachieShare", "Error querying for Facebook", e)
                }
                
                android.util.Log.w("CoachieShare", "Facebook app not found (tried: ${facebookPackages.joinToString()}), using fallback")
                // Use shareImage which properly sets up ClipData for image preview
                shareImage(finalImageUri, "Share to Facebook", "Share your Coachie progress", addWatermark = false, activityContext = activityContext)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing to Facebook", e)
            android.util.Log.e("CoachieShare", "Exception in shareToFacebook", e)
            e.printStackTrace()
            shareImage(imageUri, "Share to Facebook", "Open Facebook and share this image", addWatermark = false, activityContext = activityContext)
        }
    }
    
    /**
     * Share to TikTok (with logo watermark)
     * NOTE: TikTok doesn't reliably support pre-filled captions via EXTRA_TEXT, so we copy hashtags to clipboard
     */
    fun shareToTikTok(imageUri: Uri, deepLink: String? = null, addWatermark: Boolean = true, activityContext: Context? = null) {
        try {
            val finalImageUri = if (addWatermark) {
                ImageWatermarkUtil.addLogoWatermark(context, imageUri) ?: imageUri
            } else {
                imageUri
            }
            
            val shareContext = activityContext ?: context
            
            // CRITICAL: TikTok doesn't reliably support EXTRA_TEXT for images, so copy hashtags to clipboard
            val hashtags = getHashtags()
            val clipboard = ContextCompat.getSystemService(shareContext, ClipboardManager::class.java)
            clipboard?.let {
                val clip = ClipData.newPlainText("Coachie Hashtags", hashtags)
                it.setPrimaryClip(clip)
                android.util.Log.d("CoachieShare", "✅ Hashtags copied to clipboard for TikTok")
                // Show toast to user
                if (activityContext != null) {
                    Toast.makeText(activityContext, "Hashtags copied! Paste them in your TikTok caption.", Toast.LENGTH_LONG).show()
                }
            }
            val tiktokPackages = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
            
            var tiktokFound = false
            for (packageName in tiktokPackages) {
                try {
                    // Grant temporary read permission to TikTok
                    shareContext.grantUriPermission(
                        packageName,
                        finalImageUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, finalImageUri)
                        clipData = android.content.ClipData.newUri(shareContext.contentResolver, "Image", finalImageUri)
                        putExtra(Intent.EXTRA_TEXT, getShareTextWithHashtags(deepLink ?: "Tracked with Coachie → coachie.app"))
                        setPackage(packageName)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    
                    android.util.Log.d("CoachieShare", "Attempting to open TikTok: $packageName")
                    if (activityContext != null) {
                        shareContext.startActivity(intent)
                    } else {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        shareContext.startActivity(intent)
                    }
                    android.util.Log.d("CoachieShare", "✅ Successfully opened TikTok: $packageName")
                    tiktokFound = true
                    return
                } catch (e: android.content.ActivityNotFoundException) {
                    android.util.Log.d("CoachieShare", "TikTok package $packageName not found, trying next")
                } catch (e: Exception) {
                    android.util.Log.w("CoachieShare", "Error opening TikTok $packageName", e)
                }
            }
            
            // If direct package attempts failed, try querying all apps that can handle the intent
            if (!tiktokFound) {
                try {
                    val queryIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                    }
                    val resolveInfos = shareContext.packageManager.queryIntentActivities(queryIntent, 0)
                    val tiktokResolveInfo = resolveInfos.firstOrNull { 
                        it.activityInfo.packageName.startsWith("com.zhiliaoapp") || 
                        it.activityInfo.packageName.startsWith("com.ss.android.ugc.trill")
                    }
                    
                    if (tiktokResolveInfo != null) {
                        val packageName = tiktokResolveInfo.activityInfo.packageName
                        android.util.Log.d("CoachieShare", "Found TikTok via query: $packageName")
                        
                        shareContext.grantUriPermission(
                            packageName,
                            finalImageUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, finalImageUri)
                            clipData = android.content.ClipData.newUri(shareContext.contentResolver, "Image", finalImageUri)
                            putExtra(Intent.EXTRA_TEXT, getShareTextWithHashtags(deepLink ?: "Tracked with Coachie → coachie.app"))
                            setClassName(packageName, tiktokResolveInfo.activityInfo.name)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        
                        if (activityContext != null) {
                            shareContext.startActivity(intent)
                        } else {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            shareContext.startActivity(intent)
                        }
                        android.util.Log.d("CoachieShare", "✅ Successfully opened TikTok via query: $packageName")
                        return
                    }
                } catch (e: Exception) {
                    android.util.Log.w("CoachieShare", "Error querying for TikTok", e)
                }
                
                android.util.Log.w("CoachieShare", "TikTok app not found (tried: ${tiktokPackages.joinToString()}), using fallback")
                shareImage(finalImageUri, "Share to TikTok", "Select TikTok to share", addWatermark = false, activityContext = activityContext)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing to TikTok", e)
            android.util.Log.e("CoachieShare", "Exception in shareToTikTok", e)
            e.printStackTrace()
            shareImage(imageUri, "Share to TikTok", "Select TikTok to share", addWatermark = false, activityContext = activityContext)
        }
    }
    
    /**
     * Share to X (Twitter) (with logo watermark)
     * NOTE: X/Twitter may not reliably support pre-filled text via EXTRA_TEXT, so we copy hashtags to clipboard as backup
     */
    fun shareToX(imageUri: Uri, deepLink: String? = null, addWatermark: Boolean = true, activityContext: Context? = null) {
        try {
            val finalImageUri = if (addWatermark) {
                ImageWatermarkUtil.addLogoWatermark(context, imageUri) ?: imageUri
            } else {
                imageUri
            }
            
            val shareContext = activityContext ?: context
            
            // X/Twitter sometimes ignores EXTRA_TEXT, so copy hashtags to clipboard as backup
            val hashtags = getHashtags()
            val clipboard = ContextCompat.getSystemService(shareContext, ClipboardManager::class.java)
            clipboard?.let {
                val clip = ClipData.newPlainText("Coachie Hashtags", hashtags)
                it.setPrimaryClip(clip)
                android.util.Log.d("CoachieShare", "✅ Hashtags copied to clipboard for X/Twitter")
                // Show toast to user
                if (activityContext != null) {
                    Toast.makeText(activityContext, "Hashtags copied! Paste them in your X post if needed.", Toast.LENGTH_LONG).show()
                }
            }
            // Try multiple package names - X rebranded from Twitter
            val twitterPackages = listOf("com.twitter.android", "com.x.android", "com.x.android.tweet")
            
            var twitterFound = false
            for (packageName in twitterPackages) {
                try {
                    // Grant temporary read permission to X (Twitter)
                    shareContext.grantUriPermission(
                        packageName,
                        finalImageUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, finalImageUri)
                        clipData = android.content.ClipData.newUri(shareContext.contentResolver, "Image", finalImageUri)
                        putExtra(Intent.EXTRA_TEXT, getShareTextWithHashtags(deepLink ?: "Tracked with Coachie → coachie.app"))
                        setPackage(packageName)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    
                    android.util.Log.d("CoachieShare", "Attempting to open X/Twitter: $packageName")
                    if (activityContext != null) {
                        shareContext.startActivity(intent)
                    } else {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        shareContext.startActivity(intent)
                    }
                    android.util.Log.d("CoachieShare", "✅ Successfully opened X/Twitter: $packageName")
                    twitterFound = true
                    return
                } catch (e: android.content.ActivityNotFoundException) {
                    android.util.Log.d("CoachieShare", "X/Twitter package $packageName not found, trying next")
                } catch (e: Exception) {
                    android.util.Log.w("CoachieShare", "Error opening X/Twitter $packageName", e)
                }
            }
            
            // If direct package attempts failed, try querying all apps that can handle the intent
            if (!twitterFound) {
                try {
                    val queryIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                    }
                    val resolveInfos = shareContext.packageManager.queryIntentActivities(queryIntent, 0)
                    val twitterResolveInfo = resolveInfos.firstOrNull { 
                        it.activityInfo.packageName.startsWith("com.twitter") || 
                        it.activityInfo.packageName.startsWith("com.x.android")
                    }
                    
                    if (twitterResolveInfo != null) {
                        val packageName = twitterResolveInfo.activityInfo.packageName
                        android.util.Log.d("CoachieShare", "Found X/Twitter via query: $packageName")
                        
                        shareContext.grantUriPermission(
                            packageName,
                            finalImageUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, finalImageUri)
                            clipData = android.content.ClipData.newUri(shareContext.contentResolver, "Image", finalImageUri)
                            putExtra(Intent.EXTRA_TEXT, getShareTextWithHashtags(deepLink ?: "Tracked with Coachie → coachie.app"))
                            setClassName(packageName, twitterResolveInfo.activityInfo.name)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        
                        if (activityContext != null) {
                            shareContext.startActivity(intent)
                        } else {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            shareContext.startActivity(intent)
                        }
                        android.util.Log.d("CoachieShare", "✅ Successfully opened X/Twitter via query: $packageName")
                        return
                    }
                } catch (e: Exception) {
                    android.util.Log.w("CoachieShare", "Error querying for X/Twitter", e)
                }
                
                android.util.Log.w("CoachieShare", "X/Twitter app not found (tried: ${twitterPackages.joinToString()}), using fallback")
                shareImage(finalImageUri, "Share to X", "Select X (Twitter) to share", addWatermark = false, activityContext = activityContext)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing to X", e)
            android.util.Log.e("CoachieShare", "Exception in shareToX", e)
            e.printStackTrace()
            shareImage(imageUri, "Share to X", "Select X (Twitter) to share", addWatermark = false, activityContext = activityContext)
        }
    }
    
    /**
     * Generic image sharing using native share sheet (with logo watermark)
     * CRITICAL: Must use ClipData and proper flags to show image preview in share sheet
     */
    fun shareImage(imageUri: Uri, title: String = "Share", message: String = "Check this out!", addWatermark: Boolean = true, activityContext: Context? = null) {
        try {
            val finalImageUri = if (addWatermark) {
                ImageWatermarkUtil.addLogoWatermark(context, imageUri) ?: imageUri
            } else {
                imageUri
            }
            
            val shareContext = activityContext ?: context
            
            // CRITICAL: Use ClipData to properly attach the image URI so it shows in the share sheet preview
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, finalImageUri)
                putExtra(Intent.EXTRA_TEXT, getShareTextWithHashtags(message))
                // Use ClipData to ensure image preview shows in share sheet
                clipData = android.content.ClipData.newUri(shareContext.contentResolver, "Image", finalImageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            android.util.Log.d("CoachieShare", "Creating share chooser with image URI: $finalImageUri")
            
            val chooserIntent = Intent.createChooser(intent, title).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            if (activityContext != null) {
                shareContext.startActivity(chooserIntent)
            } else {
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                shareContext.startActivity(chooserIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing image", e)
            android.util.Log.e("CoachieShare", "Exception sharing image", e)
            e.printStackTrace()
        }
    }
    
    /**
     * Generate and share image with platform detection
     * For now, uses native share - image generation will be added later
     */
    fun generateAndShare(
        data: ShareImageData,
        platform: String? = null, // 'instagram-story', 'instagram-feed', 'facebook', 'tiktok', 'native'
        photoUri: Uri? = null // Optional photo to include in the promotional post
    ) {
        // Use the stored context (applicationContext) with FLAG_ACTIVITY_NEW_TASK
        generateAndShareInternal(data, context, platform, photoUri)
    }
    
    /**
     * Internal implementation that accepts a context parameter
     */
    private fun generateAndShareInternal(
        data: ShareImageData,
        shareContext: Context,
        platform: String?,
        photoUri: Uri? = null
    ) {
        android.util.Log.d("CoachieShare", "=== generateAndShareInternal START ===")
        android.util.Log.d("CoachieShare", "ShareImageData type: ${data.type}")
        android.util.Log.d("CoachieShare", "ShareImageData title: ${data.title}")
        android.util.Log.d("CoachieShare", "ShareImageData metric: ${data.metric}")
        android.util.Log.d("CoachieShare", "ShareImageData subtitle: ${data.subtitle}")
        android.util.Log.d("CoachieShare", "Platform: $platform")
        
        try {
            android.util.Log.d("CoachieShare", "Creating ShareImageGenerator")
            val imageGenerator = ShareImageGenerator(shareContext)
            android.util.Log.d("CoachieShare", "Generating image based on type: ${data.type}")
            android.util.Log.d("CoachieShare", "Photo URI: $photoUri")
            
            // If photo is provided, use generatePromotionalPostFromUri, otherwise use regular generatePromotionalPost
            val imageUri: Uri? = if (photoUri != null) {
                android.util.Log.d("CoachieShare", "Using photo URI for promotional post")
                when (data.type) {
                    ShareImageType.CIRCLE_WIN -> {
                        val memberParts = data.metric.split("/")
                        val memberCount = memberParts.getOrNull(0)?.toIntOrNull() ?: 0
                        val maxMembers = memberParts.getOrNull(1)?.toIntOrNull() ?: 0
                        val memberText = if (memberCount > 0 && maxMembers > 0) " ($memberCount/$maxMembers members)" else ""
                        imageGenerator.generatePromotionalPostFromUri(
                            photoUri = photoUri,
                            cardTitle = data.title,
                            cardDescription = "${data.subtitle ?: "Join me in this challenge!"}$memberText".trim()
                        )
                    }
                    ShareImageType.READINESS -> {
                        imageGenerator.generatePromotionalPostFromUri(
                            photoUri = photoUri,
                            cardTitle = data.title,
                            cardDescription = "${data.subtitle ?: ""} Score: ${data.metric}".trim()
                        )
                    }
                    ShareImageType.STREAK -> {
                        val streakText = "${data.metric}${if (data.streakFlame != null) " 🔥" else ""}"
                        imageGenerator.generatePromotionalPostFromUri(
                            photoUri = photoUri,
                            cardTitle = data.title,
                            cardDescription = "${data.subtitle ?: "${data.metric}-day streak!"} $streakText".trim()
                        )
                    }
                    ShareImageType.QUEST -> {
                        imageGenerator.generatePromotionalPostFromUri(
                            photoUri = photoUri,
                            cardTitle = data.title,
                            cardDescription = data.subtitle ?: "Quest Completed! ✓"
                        )
                    }
                    ShareImageType.INSIGHT -> {
                        imageGenerator.generatePromotionalPostFromUri(
                            photoUri = photoUri,
                            cardTitle = data.title,
                            cardDescription = data.subtitle ?: data.metric
                        )
                    }
                }
            } else {
                // No photo - use regular promotional post
                when (data.type) {
                    ShareImageType.CIRCLE_WIN -> {
                        // Circle win - use promotional post
                        val memberParts = data.metric.split("/")
                        val memberCount = memberParts.getOrNull(0)?.toIntOrNull() ?: 0
                        val maxMembers = memberParts.getOrNull(1)?.toIntOrNull() ?: 0
                        val memberText = if (memberCount > 0 && maxMembers > 0) " ($memberCount/$maxMembers members)" else ""
                        imageGenerator.generatePromotionalPost(
                            cardScreenshot = null, // TODO: Capture card screenshot in future
                            cardTitle = data.title,
                            cardDescription = "${data.subtitle ?: "Join me in this challenge!"}$memberText".trim()
                        )
                    }
                    ShareImageType.READINESS -> {
                        // Energy/Flow score - use promotional post with card screenshot, logo, and Google Play CTA
                        // For now, generate without screenshot (will show placeholder), but with proper promotional layout
                        imageGenerator.generatePromotionalPost(
                            cardScreenshot = null, // TODO: Capture card screenshot in future
                            cardTitle = data.title,
                            cardDescription = "${data.subtitle ?: ""} Score: ${data.metric}".trim()
                        )
                    }
                    ShareImageType.STREAK -> {
                        // Streak - use promotional post with card screenshot, logo, and Google Play CTA
                        val streakText = "${data.metric}${if (data.streakFlame != null) " 🔥" else ""}"
                        imageGenerator.generatePromotionalPost(
                            cardScreenshot = null, // TODO: Capture card screenshot in future
                            cardTitle = data.title,
                            cardDescription = "${data.subtitle ?: "${data.metric}-day streak!"} $streakText".trim()
                        )
                    }
                    ShareImageType.QUEST -> {
                        // Quest completion - use promotional post
                        imageGenerator.generatePromotionalPost(
                            cardScreenshot = null, // TODO: Capture card screenshot in future
                            cardTitle = data.title,
                            cardDescription = data.subtitle ?: "Quest Completed! ✓"
                        )
                    }
                    ShareImageType.INSIGHT -> {
                        // Insight - use promotional post
                        imageGenerator.generatePromotionalPost(
                            cardScreenshot = null, // TODO: Capture card screenshot in future
                            cardTitle = data.title,
                            cardDescription = data.subtitle ?: data.metric
                        )
                    }
                }
            }
            
            android.util.Log.d("CoachieShare", "Image generation result: ${if (imageUri != null) "SUCCESS - $imageUri" else "FAILED - null"}")
            
            if (imageUri != null) {
                android.util.Log.d("CoachieShare", "Image generated successfully: $imageUri")
                // DON'T add watermark - promotional posts already have the logo at the top!
                // The watermark was adding a huge logo that was covering things up
                android.util.Log.d("CoachieShare", "Skipping watermark - promotional post already has logo")
                
                // Share image to selected platform - MUST use Activity context for direct app opening
                android.util.Log.d("CoachieShare", "Sharing to platform: $platform, using Activity context: ${shareContext is android.app.Activity}")
                when (platform) {
                    "instagram-story" -> {
                        android.util.Log.d("CoachieShare", "Opening Instagram Story directly")
                        shareToInstagramStory(imageUri, addWatermark = false, activityContext = shareContext)
                    }
                    "instagram-feed" -> {
                        android.util.Log.d("CoachieShare", "Opening Instagram Feed directly")
                        shareToInstagramFeed(imageUri, addWatermark = false, activityContext = shareContext)
                    }
                    "facebook" -> {
                        android.util.Log.d("CoachieShare", "Opening Facebook directly")
                        shareToFacebook(imageUri, addWatermark = false, activityContext = shareContext)
                    }
                    "tiktok" -> {
                        android.util.Log.d("CoachieShare", "Opening TikTok directly")
                        shareToTikTok(imageUri, addWatermark = false, activityContext = shareContext)
                    }
                    "x", "twitter" -> {
                        android.util.Log.d("CoachieShare", "Opening X/Twitter directly")
                        shareToX(imageUri, addWatermark = false, activityContext = shareContext)
                    }
                    else -> {
                        // For "Other Options", use share sheet
                        android.util.Log.d("CoachieShare", "Platform is null/other, using shareImage with chooser")
                        shareImage(imageUri, "Share Your Accomplishment", "Share your progress with friends!", addWatermark = false, activityContext = shareContext)
                    }
                }
            } else {
                Log.e(TAG, "Image generation failed for type: ${data.type}")
                // Don't fall back to text - show error and retry with promotional post
                // For READINESS type, try promotional post as fallback
                if (data.type == ShareImageType.READINESS) {
                    Log.d(TAG, "Retrying with promotional post generator for Flow Score")
                    try {
                        val imageGenerator = ShareImageGenerator(shareContext)
                        val promoImageUri = imageGenerator.generatePromotionalPost(
                            cardScreenshot = null,
                            cardTitle = data.title,
                            cardDescription = "${data.subtitle ?: ""} Score: ${data.metric}".trim()
                        )
                        if (promoImageUri != null) {
                            Log.d(TAG, "Promotional post generated successfully: $promoImageUri")
                            when (platform) {
                                "instagram-story" -> shareToInstagramStory(promoImageUri, addWatermark = false)
                                "instagram-feed" -> shareToInstagramFeed(promoImageUri, addWatermark = false)
                                "facebook" -> shareToFacebook(promoImageUri, addWatermark = false)
                                "tiktok" -> shareToTikTok(promoImageUri, addWatermark = false)
                                "x", "twitter" -> shareToX(promoImageUri, addWatermark = false)
                                else -> shareImage(promoImageUri, "Share Coachie", "Check out Coachie on Google Play!", addWatermark = false)
                            }
                            return
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error generating promotional post fallback", e)
                    }
                }
                
                // Final fallback: show error message instead of text sharing
                android.util.Log.e("CoachieShare", "=== generateAndShareInternal FAILED - showing error ===")
                android.widget.Toast.makeText(
                    shareContext,
                    "Unable to generate share image. Please try again.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("CoachieShare", "=== generateAndShareInternal EXCEPTION ===", e)
            android.util.Log.e("CoachieShare", "Exception type: ${e.javaClass.simpleName}")
            android.util.Log.e("CoachieShare", "Exception message: ${e.message}")
            e.printStackTrace()
            Log.e(TAG, "Error sharing", e)
        }
    }
    
    /**
     * Share circle accomplishment to social media
     */
    fun shareCircleAccomplishment(
        circleName: String,
        accomplishment: String,
        memberCount: Int = 0,
        maxMembers: Int = 0,
        platform: String? = null
    ) {
        val data = ShareImageData(
            type = ShareImageType.CIRCLE_WIN,
            title = circleName,
            subtitle = accomplishment,
            metric = if (memberCount > 0 && maxMembers > 0) "$memberCount/$maxMembers" else ""
        )
        generateAndShare(data, platform)
    }
    
    /**
     * Generate and share a promotional post with card screenshot, Coachie icon, and Google Play CTA
     * This creates visually pleasing social media posts for marketing
     * 
     * @param cardScreenshotPath Path to card screenshot image file (optional)
     * @param cardTitle Title of the card (e.g., "Flow Score", "Win of the Day")
     * @param cardDescription Description of what the card shows
     * @param platform Platform to share to (null for native share)
     */
    fun generateAndSharePromotionalPost(
        cardScreenshotPath: String? = null,
        cardTitle: String? = null,
        cardDescription: String? = null,
        platform: String? = null
    ) {
        try {
            val imageGenerator = ShareImageGenerator(context)
            val imageUri = imageGenerator.generatePromotionalPostFromFile(
                cardScreenshotPath = cardScreenshotPath,
                cardTitle = cardTitle,
                cardDescription = cardDescription
            )
            
            if (imageUri != null) {
                Log.d(TAG, "Promotional post generated successfully: $imageUri")
                // Share to selected platform
                when (platform) {
                    "instagram-story" -> shareToInstagramStory(imageUri, addWatermark = false)
                    "instagram-feed" -> shareToInstagramFeed(imageUri, addWatermark = false)
                    "facebook" -> shareToFacebook(imageUri, addWatermark = false)
                    "tiktok" -> shareToTikTok(imageUri, addWatermark = false)
                    "x", "twitter" -> shareToX(imageUri, addWatermark = false)
                    else -> shareImage(
                        imageUri, 
                        "Share Coachie", 
                        "Check out Coachie on Google Play!",
                        addWatermark = false
                    )
                }
            } else {
                Log.e(TAG, "Failed to generate promotional post")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating promotional post", e)
        }
    }
}

