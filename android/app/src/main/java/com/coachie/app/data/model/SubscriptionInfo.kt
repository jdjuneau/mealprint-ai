package com.coachie.app.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Subscription information for a user
 */
@IgnoreExtraProperties
data class SubscriptionInfo(
    var tier: SubscriptionTier = SubscriptionTier.FREE,
    var expiresAt: Long? = null, // Timestamp in milliseconds
    var isActive: Boolean = true,
    var purchasedAt: Long? = null, // Timestamp when subscription was purchased
    var purchaseToken: String? = null, // Google Play purchase token
    var productId: String? = null // Google Play product ID (e.g., "coachie_pro_monthly")
) {
    /**
     * Check if subscription is currently valid (active and not expired)
     */
    val isValid: Boolean
        get() = isActive && (expiresAt == null || expiresAt!! > System.currentTimeMillis())
    
    /**
     * Check if user has Pro access
     */
    val hasProAccess: Boolean
        get() = isValid && tier == SubscriptionTier.PRO
}

