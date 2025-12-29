package com.coachie.app.data

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.*
import com.mealprint.ai.data.model.SubscriptionInfo
import com.mealprint.ai.data.model.SubscriptionTier
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Service for handling Google Play Billing purchases
 */
class BillingService(private val activity: Activity) {
    private var billingClient: BillingClient? = null
    private val TAG = "BillingService"
    private var purchaseUpdateListener: ((Purchase) -> Unit)? = null
    
    companion object {
        // Product IDs - Update these with your actual product IDs from Google Play Console
        const val PRODUCT_ID_MONTHLY = "coachie_pro_monthly"
        const val PRODUCT_ID_YEARLY = "coachie_pro_yearly"
    }
    
    /**
     * Initialize billing client
     */
    suspend fun initialize(): Result<Unit> {
        return suspendCancellableCoroutine { continuation ->
            billingClient = BillingClient.newBuilder(activity)
                .setListener { billingResult, purchases ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                        for (purchase in purchases) {
                            // Handle purchase asynchronously in coroutine scope
                            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                                try {
                                    handlePurchase(purchase)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error handling purchase", e)
                                }
                            }
                        }
                    } else {
                        Log.w(TAG, "Billing updates: responseCode=${billingResult.responseCode}, purchases=${purchases?.size ?: 0}")
                    }
                }
                .enablePendingPurchases()
                .build()
            
            billingClient?.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "Billing client connected")
                        continuation.resume(Result.success(Unit))
                    } else {
                        Log.e(TAG, "Billing setup failed: ${billingResult.responseCode}")
                        continuation.resume(Result.failure(Exception("Billing setup failed: ${billingResult.responseCode}")))
                    }
                }
                
                override fun onBillingServiceDisconnected() {
                    Log.w(TAG, "Billing service disconnected")
                    if (!continuation.isCompleted) {
                        continuation.resume(Result.failure(Exception("Billing service disconnected")))
                    }
                }
            })
        }
    }
    
    /**
     * Query available subscription products
     */
    suspend fun queryProducts(): Result<List<ProductDetails>> {
        return suspendCancellableCoroutine { continuation ->
            val productList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID_MONTHLY)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build(),
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID_YEARLY)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )
            
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()
            
            billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Found ${productDetailsList.size} products")
                    continuation.resume(Result.success(productDetailsList))
                } else {
                    Log.e(TAG, "Query products failed: ${billingResult.responseCode}")
                    continuation.resume(Result.failure(Exception("Query products failed: ${billingResult.responseCode}")))
                }
            }
        }
    }
    
    /**
     * Launch purchase flow for a subscription
     * Returns Result<Unit> - purchase completion is handled automatically via listener
     */
    suspend fun launchPurchaseFlow(productDetails: ProductDetails): Result<Unit> {
        return suspendCancellableCoroutine { continuation ->
            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
                ?: run {
                    continuation.resume(Result.failure(Exception("No offer token found")))
                    return@suspendCancellableCoroutine
                }
            
            val productDetailsParamsList = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .setOfferToken(offerToken)
                    .build()
            )
            
            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build()
            
            val billingResult = billingClient?.launchBillingFlow(activity, billingFlowParams)
            
            if (billingResult?.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase flow launched successfully")
                continuation.resume(Result.success(Unit))
            } else {
                Log.e(TAG, "Launch purchase flow failed: ${billingResult?.responseCode}")
                continuation.resume(Result.failure(Exception("Launch purchase flow failed: ${billingResult?.responseCode}")))
            }
        }
    }
    
    /**
     * Handle a completed purchase
     */
    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                // Acknowledge the purchase
                acknowledgePurchase(purchase)
            }
            
            // Verify purchase with backend and update subscription
            verifyAndUpdateSubscription(purchase)
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            Log.d(TAG, "Purchase is pending")
        }
    }
    
    /**
     * Acknowledge a purchase
     */
    private suspend fun acknowledgePurchase(purchase: Purchase) {
        val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        
        billingClient?.acknowledgePurchase(acknowledgeParams) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged")
            } else {
                Log.e(TAG, "Failed to acknowledge purchase: ${billingResult.responseCode}")
            }
        }
    }
    
    /**
     * Verify purchase with backend and update subscription
     */
    private suspend fun verifyAndUpdateSubscription(purchase: Purchase) {
        try {
            val functions = Firebase.functions
            val verifyPurchase = functions.getHttpsCallable("verifyPurchase")
            
            val data = mapOf(
                "purchaseToken" to purchase.purchaseToken,
                "productId" to purchase.products.firstOrNull()
            )
            
            val result = verifyPurchase.call(data).await()
            Log.d(TAG, "Purchase verified: $result")
            
            // Notify listener that subscription was updated
            purchaseUpdateListener?.invoke(purchase)
            
            // Refresh subscription in Firestore by reloading UserProfile
            // The Cloud Function already updated the subscription, but we need to refresh local cache
            refreshUserProfileSubscription()
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying purchase", e)
            throw e
        }
    }
    
    /**
     * Refresh user profile subscription from Firestore
     * This ensures the local UserProfile is updated after purchase
     */
    private suspend fun refreshUserProfileSubscription() {
        try {
            val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (userId == null) {
                Log.w(TAG, "No user ID - cannot refresh subscription")
                return
            }
            
            // Force refresh UserProfile from Firestore
            val repository = com.coachie.app.data.FirebaseRepository.getInstance()
            repository.getUserProfile(userId).onSuccess {
                Log.d(TAG, "UserProfile refreshed after purchase - subscription updated")
            }.onFailure {
                Log.e(TAG, "Failed to refresh UserProfile after purchase", it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing subscription", e)
        }
    }
    
    /**
     * Set listener for purchase updates
     * This allows UI to refresh when purchase completes
     */
    fun setPurchaseUpdateListener(listener: ((Purchase) -> Unit)?) {
        purchaseUpdateListener = listener
    }
    
    /**
     * Query existing purchases
     */
    suspend fun queryPurchases(): Result<List<Purchase>> {
        return suspendCancellableCoroutine { continuation ->
            billingClient?.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            ) { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Found ${purchases.size} active purchases")
                    continuation.resume(Result.success(purchases))
                } else {
                    Log.e(TAG, "Query purchases failed: ${billingResult.responseCode}")
                    continuation.resume(Result.failure(Exception("Query purchases failed: ${billingResult.responseCode}")))
                }
            }
        }
    }
    
    /**
     * Release billing client
     */
    fun release() {
        billingClient?.endConnection()
        billingClient = null
    }
}

