package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mealprint.ai.data.SubscriptionService
import com.mealprint.ai.data.model.SubscriptionTier
import com.mealprint.ai.ui.components.CoachieCard
import com.mealprint.ai.ui.components.CoachieCardDefaults
import com.mealprint.ai.ui.theme.Primary40
import com.mealprint.ai.ui.theme.rememberCoachieGradient
import com.mealprint.ai.util.AuthUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    onNavigateBack: () -> Unit,
    onUpgrade: () -> Unit = {} // TODO: Implement Google Play Billing
) {
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val userId = AuthUtils.getAuthenticatedUserId() ?: return
    val coroutineScope = rememberCoroutineScope()
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = com.coachie.app.data.FirebaseRepository.getInstance()
    
    var currentTier by remember { mutableStateOf<SubscriptionTier?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var subscriptionExpiresAt by remember { mutableStateOf<Long?>(null) }
    var isActive by remember { mutableStateOf(false) }
    var products by remember { mutableStateOf<List<com.android.billingclient.api.ProductDetails>>(emptyList()) }
    var billingService by remember { mutableStateOf<com.coachie.app.data.BillingService?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) } // Trigger to refresh subscription
    var purchaseMessage by remember { mutableStateOf<String?>(null) } // Success/error message
    
    // Initialize billing service
    LaunchedEffect(Unit) {
        val billing = com.coachie.app.data.BillingService(context as android.app.Activity)
        billing.initialize().onSuccess {
            billingService = billing
            billing.queryProducts().onSuccess {
                products = it
            }
            // Set purchase listener to refresh subscription when purchase completes
            billing.setPurchaseUpdateListener { purchase ->
                android.util.Log.d("SubscriptionScreen", "Purchase completed, refreshing subscription...")
                purchaseMessage = "Purchase successful! Your subscription is being updated..."
                refreshTrigger++ // Trigger refresh
                // Clear message after 5 seconds
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    kotlinx.coroutines.delay(5000)
                    purchaseMessage = null
                }
            }
        }
    }
    
    // Load subscription status from UserProfile
    // Refresh when userId changes OR when refreshTrigger changes (after purchase)
    LaunchedEffect(userId, refreshTrigger) {
        coroutineScope.launch {
            try {
                isLoading = true
                // Force refresh from Firestore (don't use cached profile)
                val profile = repository.getUserProfile(userId).getOrNull()
                val subscription = SubscriptionService.getSubscriptionInfo(userId, profile)
                
                currentTier = subscription?.tier ?: SubscriptionTier.FREE
                subscriptionExpiresAt = subscription?.expiresAt
                isActive = subscription?.isActive ?: false
                
                android.util.Log.d("SubscriptionScreen", "Subscription loaded: tier=${currentTier}, expiresAt=${subscriptionExpiresAt}, isActive=${isActive}")
                isLoading = false
            } catch (e: Exception) {
                android.util.Log.e("SubscriptionScreen", "Error loading subscription", e)
                isLoading = false
            }
        }
    }
    
    // Cleanup billing service
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            billingService?.release()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subscription") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.Black
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.Black,
                    navigationIconContentColor = Color.Black
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBackground)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Purchase success/error message
                    purchaseMessage?.let { message ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text(
                                text = message,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    
                    // Current subscription status
                    CurrentSubscriptionCard(
                        tier = currentTier ?: SubscriptionTier.FREE,
                        expiresAt = subscriptionExpiresAt,
                        isActive = isActive
                    )
                    
                    // Free tier features
                    if (currentTier == SubscriptionTier.FREE) {
                        FreeTierFeaturesCard()
                        ProTierFeaturesCard(onUpgrade = onUpgrade)
                    } else {
                        // Pro tier - show benefits
                        ProTierBenefitsCard(expiresAt = subscriptionExpiresAt)
                    }
                    
                    // Pricing
                    PricingCard(
                        currentTier = currentTier ?: SubscriptionTier.FREE,
                        onUpgrade = { productId ->
                            val product = products.find { it.productId == productId }
                            if (product != null && billingService != null) {
                                coroutineScope.launch {
                                    billingService?.launchPurchaseFlow(product)?.onSuccess {
                                        android.util.Log.d("SubscriptionScreen", "Purchase flow launched - purchase will be handled automatically")
                                        purchaseMessage = "Processing purchase..."
                                        // Purchase will be handled in BillingService listener
                                        // Subscription will be updated via Cloud Function verifyPurchase
                                    }?.onFailure {
                                        android.util.Log.e("SubscriptionScreen", "Purchase flow failed", it)
                                        purchaseMessage = "Purchase failed: ${it.message ?: "Unknown error"}"
                                        // Clear error message after 5 seconds
                                        kotlinx.coroutines.delay(5000)
                                        purchaseMessage = null
                                    }
                                }
                            } else {
                                android.util.Log.w("SubscriptionScreen", "Product or billing service not available")
                            }
                        },
                        products = products
                    )
                    
                    // Note: Purchase flow is handled directly in PricingCard onUpgrade callback
                }
            }
        }
    }
}

@Composable
private fun CurrentSubscriptionCard(
    tier: SubscriptionTier,
    expiresAt: Long?,
    isActive: Boolean
) {
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.colors(
            containerColor = if (tier == SubscriptionTier.PRO) {
                Primary40.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current Plan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (tier == SubscriptionTier.PRO) {
                        Primary40
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = tier.name,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (tier == SubscriptionTier.PRO) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            
            if (tier == SubscriptionTier.PRO) {
                if (expiresAt != null) {
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    val dateString = dateFormat.format(Date(expiresAt))
                    Text(
                        text = "Expires: $dateString",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                } else {
                    Text(
                        text = "Active subscription",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            } else {
                Text(
                    text = "Upgrade to unlock Pro features",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun FreeTierFeaturesCard() {
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.colors()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Free Tier Features",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            FeatureItem("✅ All health tracking (meals, workouts, sleep, water, mood)")
            FeatureItem("✅ Manual meal logging with barcode scanning")
            FeatureItem("✅ Basic dashboard with charts and stats")
            FeatureItem("✅ Google Fit & Health Connect sync")
            FeatureItem("✅ Social features (circles, friends, messaging)")
            FeatureItem("✅ Habit tracking")
            FeatureItem("✅ Voice logging")
            FeatureItem("✅ Saved meals and recipes")
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            Text(
                text = "Limited AI Features:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            
            FeatureItem("🤖 Meal Recommendations: 3 per day")
            FeatureItem("📊 Daily Insights: 1 per day")
            FeatureItem("💡 Habit Suggestions: 5 per week")
            FeatureItem("💬 AI Coach Chat: 10 messages per day")
        }
    }
}

@Composable
private fun ProTierFeaturesCard(onUpgrade: () -> Unit) {
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.colors(
            containerColor = Primary40.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = Primary40,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Pro Tier Features",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Text(
                text = "Everything in Free, plus:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            FeatureItem("✨ Unlimited AI-generated weekly blueprints")
            FeatureItem("🤖 Unlimited AI meal recommendations")
            FeatureItem("📊 Unlimited daily insights")
            FeatureItem("🌅 Personalized morning briefs")
            FeatureItem("📈 Monthly AI insights")
            FeatureItem("🎯 AI quest generation")
            FeatureItem("💬 Unlimited AI Coach Chat")
            
            Spacer(modifier = Modifier.height(8.dp))
            
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
        }
    }
}

@Composable
private fun ProTierBenefitsCard(expiresAt: Long?) {
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.colors(
            containerColor = Primary40.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = Primary40,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "You're a Pro Member!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Text(
                text = "Enjoy unlimited access to all Pro features:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            FeatureItem("✨ Unlimited AI-generated weekly blueprints")
            FeatureItem("🤖 Unlimited AI meal recommendations")
            FeatureItem("📊 Unlimited daily insights")
            FeatureItem("🌅 Personalized morning briefs")
            FeatureItem("📈 Monthly AI insights")
            FeatureItem("🎯 AI quest generation")
            FeatureItem("💬 Unlimited AI Coach Chat")
        }
    }
}

@Composable
private fun PricingCard(
    currentTier: SubscriptionTier,
    onUpgrade: (String) -> Unit,
    products: List<com.android.billingclient.api.ProductDetails> = emptyList()
) {
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.colors()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Pricing",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            // Monthly plan
            val monthlyProduct = products.find { it.productId == com.coachie.app.data.BillingService.PRODUCT_ID_MONTHLY }
            val monthlyPrice = monthlyProduct?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                ?: "$9.99"
            PricingOptionCard(
                title = "Monthly",
                price = monthlyPrice,
                period = "per month",
                isSelected = currentTier == SubscriptionTier.PRO,
                onSelect = if (currentTier != SubscriptionTier.PRO && monthlyProduct != null) {
                    { onUpgrade(com.coachie.app.data.BillingService.PRODUCT_ID_MONTHLY) }
                } else null
            )
            
            // Yearly plan (better value)
            val yearlyProduct = products.find { it.productId == com.coachie.app.data.BillingService.PRODUCT_ID_YEARLY }
            val yearlyPrice = yearlyProduct?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                ?: "$99.00"
            PricingOptionCard(
                title = "Yearly",
                price = yearlyPrice,
                period = "per year",
                savings = "Save 17%",
                isSelected = currentTier == SubscriptionTier.PRO,
                isRecommended = true,
                onSelect = if (currentTier != SubscriptionTier.PRO && yearlyProduct != null) {
                    { onUpgrade(com.coachie.app.data.BillingService.PRODUCT_ID_YEARLY) }
                } else null
            )
        }
    }
}

@Composable
private fun PricingOptionCard(
    title: String,
    price: String,
    period: String,
    savings: String? = null,
    isSelected: Boolean,
    isRecommended: Boolean = false,
    onSelect: (() -> Unit)?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) {
            Primary40.copy(alpha = 0.1f)
        } else if (isRecommended) {
            Primary40.copy(alpha = 0.05f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        border = if (isRecommended) {
            androidx.compose.foundation.BorderStroke(2.dp, Primary40)
        } else {
            null
        },
        onClick = onSelect ?: {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (isRecommended) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Primary40
                            ) {
                                Text(
                                    text = "BEST VALUE",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                    if (savings != null) {
                        Text(
                            text = savings,
                            style = MaterialTheme.typography.labelSmall,
                            color = Primary40,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = price,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = period,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            
            if (isSelected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Primary40,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Current Plan",
                        style = MaterialTheme.typography.labelSmall,
                        color = Primary40,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureItem(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}

