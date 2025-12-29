package com.coachie.app.data

import com.coachie.app.data.model.AIFeature
import com.coachie.app.data.model.SubscriptionInfo
import com.coachie.app.data.model.SubscriptionTier
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing user subscriptions and checking feature access
 * 
 * ⚠️ SECURITY NOTE: Client-side checks are for UX ONLY (showing/hiding UI elements).
 * ALL subscription verification MUST happen server-side in Cloud Functions.
 * Users can modify client code, so never trust client-side checks for security.
 * 
 * This service provides:
 * - UX checks (show upgrade prompts, hide Pro features)
 * - Caching for performance
 * - Usage tracking display
 * 
 * Server-side verification happens in Cloud Functions before processing AI requests.
 */
@Singleton
class SubscriptionService @Inject constructor() {
    
    private val db = FirebaseFirestore.getInstance()
    
    companion object {
        // Singleton instance for static access
        @Volatile
        private var INSTANCE: SubscriptionService? = null
        
        private fun getInstance(): SubscriptionService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SubscriptionService().also { INSTANCE = it }
            }
        }
        
        /**
         * Get user's subscription tier (static access)
         * Defaults to FREE if no subscription found or subscription is invalid
         */
        suspend fun getUserTier(userId: String): SubscriptionTier {
            return getInstance().getUserTier(userId)
        }
        
        /**
         * Check if user has Pro access (static access)
         */
        suspend fun hasProAccess(userId: String): Boolean {
            return getInstance().hasProAccess(userId)
        }
        
        /**
         * Check if user can use a specific AI feature (static access)
         */
        suspend fun canUseAIFeature(userId: String, feature: AIFeature): Boolean {
            return getInstance().canUseAIFeature(userId, feature)
        }
        
        /**
         * Get remaining AI calls for free tier users (static access)
         */
        suspend fun getRemainingAICalls(userId: String, feature: AIFeature): Int {
            return getInstance().getRemainingAICalls(userId, feature)
        }
        
        /**
         * Record AI feature usage for free tier users (static access)
         */
        suspend fun recordAIFeatureUsage(userId: String, feature: AIFeature) {
            getInstance().recordAIFeatureUsage(userId, feature)
        }
        
        /**
         * Get subscription info (static access)
         */
        suspend fun getSubscriptionInfo(userId: String, userProfile: com.coachie.app.data.model.UserProfile? = null): SubscriptionInfo? {
            return getInstance().getSubscriptionInfo(userId, userProfile)
        }
        
        /**
         * Get maximum number of circles a user can join (static access)
         * Free tier: 3 circles, Pro tier: unlimited
         */
        suspend fun getMaxCircles(userId: String): Int {
            return getInstance().getMaxCircles(userId)
        }
        
        /**
         * Check if user can join another circle (static access)
         * Returns true if user can join, false if limit reached
         */
        suspend fun canJoinCircle(userId: String, currentCircleCount: Int): Boolean {
            return getInstance().canJoinCircle(userId, currentCircleCount)
        }
    }
    
    /**
     * Get user's subscription tier
     * Defaults to FREE if no subscription found or subscription is invalid
     */
    suspend fun getUserTier(userId: String): SubscriptionTier {
        return try {
            val subscription = getSubscriptionInfo(userId)
            if (subscription?.hasProAccess == true) {
                SubscriptionTier.PRO
            } else {
                SubscriptionTier.FREE
            }
        } catch (e: Exception) {
            android.util.Log.e("SubscriptionService", "Error getting subscription tier", e)
            SubscriptionTier.FREE // Default to free on error
        }
    }
    
    /**
     * Get user's subscription information
     * First tries to load from UserProfile, then falls back to direct Firestore query
     */
    suspend fun getSubscriptionInfo(userId: String, userProfile: com.coachie.app.data.model.UserProfile? = null): SubscriptionInfo? {
        return try {
            // First, try to get from UserProfile if provided
            val profileSubscription = userProfile?.subscription
            if (profileSubscription != null) {
                android.util.Log.d("SubscriptionService", "Loaded subscription from UserProfile: ${profileSubscription.tier}")
                return profileSubscription
            }
            
            // Fallback: Load directly from Firestore
            val userDoc = db.collection("users").document(userId).get().await()
            val subscriptionData = userDoc.get("subscription") as? Map<*, *>
            
            if (subscriptionData == null) {
                return SubscriptionInfo() // Default to FREE tier
            }
            
            val tierString = subscriptionData["tier"] as? String ?: "free"
            val tier = when (tierString.lowercase()) {
                "pro", "premium" -> SubscriptionTier.PRO
                else -> SubscriptionTier.FREE
            }
            
            val expiresAt = (subscriptionData["expiresAt"] as? Number)?.toLong()
            val isActive = subscriptionData["isActive"] as? Boolean ?: true
            val purchasedAt = (subscriptionData["purchasedAt"] as? Number)?.toLong()
            val purchaseToken = subscriptionData["purchaseToken"] as? String
            val productId = subscriptionData["productId"] as? String
            
            SubscriptionInfo(
                tier = tier,
                expiresAt = expiresAt,
                isActive = isActive,
                purchasedAt = purchasedAt,
                purchaseToken = purchaseToken,
                productId = productId
            )
        } catch (e: Exception) {
            android.util.Log.e("SubscriptionService", "Error getting subscription info", e)
            null
        }
    }
    
    /**
     * Check if user has Pro access
     */
    suspend fun hasProAccess(userId: String): Boolean {
        return getUserTier(userId) == SubscriptionTier.PRO
    }
    
    /**
     * Check if user can use a specific AI feature
     * Returns true if feature is available (even with limits for free tier)
     */
    suspend fun canUseAIFeature(userId: String, feature: AIFeature): Boolean {
        val tier = getUserTier(userId)
        
        return when (feature) {
            // Pro-only features
            AIFeature.WEEKLY_BLUEPRINT_AI,
            AIFeature.MORNING_BRIEF,
            AIFeature.MONTHLY_INSIGHTS,
            AIFeature.QUEST_GENERATION -> tier == SubscriptionTier.PRO
            
            // Available on both tiers (with limits for free)
            AIFeature.MEAL_RECOMMENDATION,
            AIFeature.DAILY_INSIGHT,
            AIFeature.HABIT_SUGGESTIONS,
            AIFeature.AI_COACH_CHAT -> true
        }
    }
    
    /**
     * Get remaining AI calls for free tier users
     * Returns Int.MAX_VALUE for Pro users (unlimited)
     */
    suspend fun getRemainingAICalls(userId: String, feature: AIFeature): Int {
        val tier = getUserTier(userId)
        if (tier == SubscriptionTier.PRO) {
            return Int.MAX_VALUE // Unlimited for Pro
        }
        
        // Check daily usage for free tier
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        return try {
            val usageDoc = db.collection("users")
                .document(userId)
                .collection("aiUsage")
                .document("daily")
                .get()
                .await()
            
            val usage = usageDoc.get("usage") as? Map<String, Any?> ?: emptyMap<String, Any?>()
            val featureUsage = (usage[feature.name] as? Map<String, Any?>) ?: emptyMap<String, Any?>()
            val lastUsed = (featureUsage["lastUsed"] as? Number)?.toLong() ?: 0L
            val count = (featureUsage["count"] as? Number)?.toInt() ?: 0
            
            // Reset if new day
            if (lastUsed < today) {
                getDailyLimit(feature)
            } else {
                (getDailyLimit(feature) - count).coerceAtLeast(0)
            }
        } catch (e: Exception) {
            android.util.Log.e("SubscriptionService", "Error getting AI usage", e)
            getDailyLimit(feature) // Return full limit on error
        }
    }
    
    /**
     * Record AI feature usage for free tier users
     * Pro users don't need usage tracking
     */
    suspend fun recordAIFeatureUsage(userId: String, feature: AIFeature) {
        val tier = getUserTier(userId)
        if (tier == SubscriptionTier.PRO) {
            return // No need to track for Pro users
        }
        
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        try {
            val usageRef = db.collection("users")
                .document(userId)
                .collection("aiUsage")
                .document("daily")
            
            val currentData = usageRef.get().await().data ?: emptyMap<String, Any?>()
            val usage = (currentData["usage"] as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf<String, Any?>()
            
            val featureUsage = (usage[feature.name] as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf<String, Any?>()
            val lastUsed = (featureUsage["lastUsed"] as? Number)?.toLong() ?: 0L
            val count = if (lastUsed < today) {
                1 // Reset for new day
            } else {
                ((featureUsage["count"] as? Number)?.toInt() ?: 0) + 1
            }
            
            featureUsage["lastUsed"] = today
            featureUsage["count"] = count
            usage[feature.name] = featureUsage
            
            usageRef.set(mapOf("usage" to usage)).await()
            
            android.util.Log.d("SubscriptionService", "Recorded AI usage: ${feature.name}, count: $count")
        } catch (e: Exception) {
            android.util.Log.e("SubscriptionService", "Error recording AI usage", e)
        }
    }
    
    /**
     * Get daily limit for a specific AI feature (free tier)
     */
    private fun getDailyLimit(feature: AIFeature): Int {
        return when (feature) {
            AIFeature.MEAL_RECOMMENDATION -> 1 // 1 per day
            AIFeature.DAILY_INSIGHT -> 1 // 1 per day
            AIFeature.HABIT_SUGGESTIONS -> 5 // 5 per week (but tracked daily, so 5 per day max)
            AIFeature.AI_COACH_CHAT -> 10 // 10 messages per day
            else -> 0 // Not available on free tier
        }
    }
    
    /**
     * Get maximum number of circles a user can join
     * Free tier: 3 circles, Pro tier: unlimited
     */
    suspend fun getMaxCircles(userId: String): Int {
        val tier = getUserTier(userId)
        return if (tier == SubscriptionTier.PRO) {
            Int.MAX_VALUE // Unlimited for Pro
        } else {
            3 // Free tier limit: 3 circles
        }
    }
    
    /**
     * Check if user can join another circle
     * Returns true if user can join, false if limit reached
     */
    suspend fun canJoinCircle(userId: String, currentCircleCount: Int): Boolean {
        val maxCircles = getMaxCircles(userId)
        return currentCircleCount < maxCircles
    }
    
    /**
     * Update user's subscription information
     * CRITICAL: Must set status field for server-side subscription checks to work correctly
     */
    suspend fun updateSubscription(
        userId: String,
        tier: SubscriptionTier,
        expiresAt: Long? = null,
        purchaseToken: String? = null,
        productId: String? = null
    ): Result<Unit> {
        return try {
            // CRITICAL: Load existing subscription to preserve cross-platform data
            val userDoc = db.collection("users").document(userId).get().await()
            val existingSubscription = userDoc.get("subscription") as? Map<*, *> ?: emptyMap<String, Any>()
            val existingPlatforms = (userDoc.get("platforms") as? List<*>)?.mapNotNull { it as? String } ?: emptyList<String>()
            
            // CRITICAL: Set status based on tier - server-side getUserSubscriptionTier() checks status === 'active'
            val status = if (tier == SubscriptionTier.PRO) "active" else "expired"
            val isActive = tier == SubscriptionTier.PRO
            
            // Preserve existing subscription data and merge new fields
            val subscription = hashMapOf<String, Any>().apply {
                // Preserve existing fields
                existingSubscription.forEach { (key, value) ->
                    if (key is String && value != null) {
                        put(key, value)
                    }
                }
                
                // Update with new values
                put("tier", tier.name.lowercase())
                put("status", status) // CRITICAL: Required for server-side checks
                put("isActive", isActive) // CRITICAL: Required for server-side checks
                put("purchasedAt", System.currentTimeMillis())
                
                // Only add optional fields if provided
                val finalExpiresAt = expiresAt ?: if (tier == SubscriptionTier.PRO) System.currentTimeMillis() + (90L * 24 * 60 * 60 * 1000) else null
                finalExpiresAt?.let { put("expiresAt", it) }
                purchaseToken?.let { put("purchaseToken", it) }
                productId?.let { put("productId", it) }
                
                // Preserve paymentProvider if exists, otherwise set to google_play
                if (!containsKey("paymentProvider")) {
                    put("paymentProvider", "google_play")
                }
                
                // Update platforms array to include android
                val updatedPlatforms = if (existingPlatforms.contains("android")) {
                    existingPlatforms
                } else {
                    (existingPlatforms + "android").sorted()
                }
                put("platforms", updatedPlatforms)
            }
            
            // Update subscription and platforms
            db.collection("users")
                .document(userId)
                .update(
                    mapOf(
                        "subscription" to subscription,
                        "platforms" to subscription["platforms"],
                        "platform" to "android"
                    )
                )
                .await()
            
            android.util.Log.d("SubscriptionService", "Updated subscription for user $userId: $tier (status: $status, isActive: $isActive, platforms: ${subscription["platforms"]})")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("SubscriptionService", "Error updating subscription", e)
            android.util.Log.e("SubscriptionService", "Error details: ${e.message}", e)
            Result.failure(e)
        }
    }
}

