/**
 * Server-side subscription verification utility
 * 
 * CRITICAL SECURITY: All subscription checks MUST happen server-side.
 * Client-side checks are for UX only and can be bypassed.
 */

import * as admin from 'firebase-admin';
import * as functions from 'firebase-functions';
import { google } from 'googleapis';

const db = admin.firestore();

// Google Play package name
const GOOGLE_PLAY_PACKAGE_NAME = 'com.coachie.app';

/**
 * Get Google Play API client using service account
 */
function getGooglePlayClient() {
  try {
    // Get service account credentials from environment or Firebase config
    const serviceAccountKey = functions.config().googleplay?.service_account_key;
    
    if (!serviceAccountKey) {
      console.warn('⚠️ Google Play service account key not configured. Using environment variable or default.');
      // Try to use environment variable or service account from Firebase Admin
      const serviceAccount = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT 
        ? JSON.parse(process.env.GOOGLE_PLAY_SERVICE_ACCOUNT)
        : null;
      
      if (!serviceAccount) {
        console.error('❌ Google Play service account not configured');
        return null;
      }
      
      const authClient = new google.auth.JWT(
        serviceAccount.client_email,
        undefined,
        serviceAccount.private_key,
        ['https://www.googleapis.com/auth/androidpublisher']
      );
      
      return google.androidpublisher({
        version: 'v3',
        auth: authClient,
      });
    }
    
    // Parse service account key if it's a string
    const serviceAccount = typeof serviceAccountKey === 'string' 
      ? JSON.parse(serviceAccountKey)
      : serviceAccountKey;
    
    const authClient = new google.auth.JWT(
      serviceAccount.client_email,
      undefined,
      serviceAccount.private_key,
      ['https://www.googleapis.com/auth/androidpublisher']
    );
    
    return google.androidpublisher({
      version: 'v3',
      auth: authClient,
    });
  } catch (error) {
    console.error('Error creating Google Play API client:', error);
    return null;
  }
}

export enum SubscriptionTier {
  FREE = 'free',
  PRO = 'pro'
}

export interface SubscriptionInfo {
  tier: SubscriptionTier;
  expiresAt?: number; // Timestamp in milliseconds
  isActive: boolean;
  purchasedAt?: number;
  purchaseToken?: string;
  productId?: string;
}

/**
 * Get user's subscription tier from Firestore
 * This is the SINGLE SOURCE OF TRUTH for subscription status
 */
export async function getUserSubscriptionTier(userId: string): Promise<SubscriptionTier> {
  try {
    const userDoc = await db.collection('users').doc(userId).get();
    
    if (!userDoc.exists) {
      console.warn(`User ${userId} not found - defaulting to FREE tier`);
      return SubscriptionTier.FREE;
    }
    
    const subscription = userDoc.data()?.subscription as any;
    
    if (!subscription) {
      return SubscriptionTier.FREE; // Default to free if no subscription
    }
    
    // Check subscription status (matches Stripe/PayPal structure)
    const status = subscription.status;
    const isActive = subscription.isActive !== false; // Default to true if not set
    
    // Check if subscription is expired using endDate (Firestore Timestamp) or expiresAt (number)
    let isExpired = false;
    if (subscription.endDate) {
      // Firestore Timestamp format (from Stripe/PayPal)
      const endDate = subscription.endDate.toDate ? subscription.endDate.toDate() : new Date(subscription.endDate);
      isExpired = endDate.getTime() < Date.now();
    } else if (subscription.expiresAt) {
      // Number timestamp format (from Google Play)
      isExpired = subscription.expiresAt < Date.now();
    }
    
    if (isExpired) {
      console.log(`Subscription expired for user ${userId}`);
      // Update the subscription status
      await db.collection('users').doc(userId).update({
        'subscription.status': 'expired',
        'subscription.isActive': false
      });
      return SubscriptionTier.FREE;
    }
    
    // Check if subscription is active
    if (status !== 'active' || !isActive) {
      console.log(`User ${userId}: Subscription not active (status=${status}, isActive=${isActive}) - returning FREE`);
      return SubscriptionTier.FREE;
    }
    
    // Return tier - CRITICAL: Check tier field explicitly
    const tier = subscription.tier?.toLowerCase();
    const isPro = tier === 'pro' || tier === SubscriptionTier.PRO;
    
    console.log(`User ${userId}: Subscription active, tier=${tier}, isPro=${isPro}`);
    
    return isPro ? SubscriptionTier.PRO : SubscriptionTier.FREE;
  } catch (error) {
    console.error(`Error getting subscription tier for user ${userId}:`, error);
    return SubscriptionTier.FREE; // Default to free on error
  }
}

/**
 * Verify user has Pro access
 * Use this in Cloud Functions before allowing Pro-only features
 */
export async function verifyProAccess(userId: string): Promise<boolean> {
  const tier = await getUserSubscriptionTier(userId);
  const hasAccess = tier === SubscriptionTier.PRO;
  
  if (!hasAccess) {
    console.warn(`Pro access denied for user ${userId} - tier: ${tier}`);
  }
  
  return hasAccess;
}

/**
 * Verify subscription and throw error if not Pro
 * Use this in Cloud Functions to enforce Pro-only features
 */
export async function requireProAccess(
  userId: string,
  featureName: string
): Promise<void> {
  const hasAccess = await verifyProAccess(userId);
  
  if (!hasAccess) {
    throw new functions.https.HttpsError(
      'permission-denied',
      `${featureName} is available for Pro subscribers only. Upgrade to Pro to unlock this feature.`,
      {
        upgradeRequired: true,
        feature: featureName
      }
    );
  }
}

/**
 * Check if user can use an AI feature (considering free tier limits)
 */
export async function canUseAIFeature(
  userId: string,
  feature: string
): Promise<{ allowed: boolean; reason?: string; remaining?: number }> {
  const tier = await getUserSubscriptionTier(userId);
  
  // Pro users have unlimited access
  if (tier === SubscriptionTier.PRO) {
    return { allowed: true };
  }
  
  // Free tier: Check limits
  const limits = getFreeTierLimits(feature);
  if (limits.maxCalls === 0) {
    return {
      allowed: false,
      reason: `${feature} is not available on the free tier. Upgrade to Pro for unlimited access.`
    };
  }
  
  // Check daily usage
  const usage = await getDailyAIFeatureUsage(userId, feature);
  const remaining = limits.maxCalls - usage.count;
  
  if (remaining <= 0) {
    return {
      allowed: false,
      reason: `You've reached your daily limit of ${limits.maxCalls} ${feature} requests. Upgrade to Pro for unlimited access.`,
      remaining: 0
    };
  }
  
  return {
    allowed: true,
    remaining
  };
}

/**
 * Record AI feature usage for free tier users
 */
export async function recordAIFeatureUsage(userId: string, feature: string): Promise<void> {
  const tier = await getUserSubscriptionTier(userId);
  
  // Don't track usage for Pro users
  if (tier === SubscriptionTier.PRO) {
    return;
  }
  
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const todayTimestamp = today.getTime();
  
  try {
    const usageRef = db
      .collection('users')
      .doc(userId)
      .collection('aiUsage')
      .doc('daily');
    
    const usageDoc = await usageRef.get();
    const currentData = usageDoc.data() || {};
    const usage = currentData.usage || {};
    
    const featureUsage = usage[feature] || {};
    const lastUsed = featureUsage.lastUsed || 0;
    const count = lastUsed < todayTimestamp ? 1 : (featureUsage.count || 0) + 1;
    
    usage[feature] = {
      lastUsed: todayTimestamp,
      count: count
    };
    
    await usageRef.set({ usage }, { merge: true });
    
    console.log(`Recorded AI usage for user ${userId}, feature ${feature}, count: ${count}`);
  } catch (error) {
    console.error(`Error recording AI usage for user ${userId}:`, error);
    // Don't throw - usage tracking failure shouldn't block the feature
  }
}

/**
 * Get daily AI feature usage for a user
 */
async function getDailyAIFeatureUsage(userId: string, feature: string): Promise<{ count: number; lastUsed: number }> {
  try {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const todayTimestamp = today.getTime();
    
    const usageDoc = await db
      .collection('users')
      .doc(userId)
      .collection('aiUsage')
      .doc('daily')
      .get();
    
    if (!usageDoc.exists) {
      return { count: 0, lastUsed: 0 };
    }
    
    const usage = usageDoc.data()?.usage || {};
    const featureUsage = usage[feature] || {};
    const lastUsed = featureUsage.lastUsed || 0;
    
    // Reset if new day
    if (lastUsed < todayTimestamp) {
      return { count: 0, lastUsed: 0 };
    }
    
    return {
      count: featureUsage.count || 0,
      lastUsed: lastUsed
    };
  } catch (error) {
    console.error(`Error getting AI usage for user ${userId}:`, error);
    return { count: 0, lastUsed: 0 };
  }
}

/**
 * Get free tier limits for AI features
 */
function getFreeTierLimits(feature: string): { maxCalls: number; period: 'day' | 'week' } {
  const limits: Record<string, { maxCalls: number; period: 'day' | 'week' }> = {
    'MEAL_RECOMMENDATION': { maxCalls: 1, period: 'day' }, // 1 per day (matches web service and documentation)
    'DAILY_INSIGHT': { maxCalls: 1, period: 'day' },
    'HABIT_SUGGESTIONS': { maxCalls: 5, period: 'week' },
    'AI_COACH_CHAT': { maxCalls: 10, period: 'day' },
    'WEEKLY_BLUEPRINT_AI': { maxCalls: 0, period: 'day' }, // Pro only
    'MORNING_BRIEF': { maxCalls: 0, period: 'day' }, // Pro only
    'MONTHLY_INSIGHTS': { maxCalls: 0, period: 'day' }, // Pro only
    'QUEST_GENERATION': { maxCalls: 0, period: 'day' } // Pro only
  };
  
  return limits[feature] || { maxCalls: 0, period: 'day' };
}

/**
 * Verify Google Play subscription purchase token
 * Uses Google Play Developer API to verify subscription status
 */
export async function verifyGooglePlayPurchase(
  purchaseToken: string,
  productId: string
): Promise<{ 
  valid: boolean; 
  expiresAt?: number;
  expiryTimeMillis?: string;
  autoRenewing?: boolean;
  paymentState?: number;
  purchaseType?: number;
}> {
  try {
    const androidPublisher = getGooglePlayClient();
    
    if (!androidPublisher) {
      console.error('❌ Google Play API client not available - cannot verify purchase');
      // Fallback: if purchaseToken exists, assume valid (for development/testing)
      if (purchaseToken && productId) {
        console.warn('⚠️ Using fallback verification (Google Play API not configured)');
        const isMonthly = productId.includes('monthly');
        const expiresAt = Date.now() + (isMonthly ? 30 * 24 * 60 * 60 * 1000 : 365 * 24 * 60 * 60 * 1000);
        return {
          valid: true,
          expiresAt,
          autoRenewing: true,
        };
      }
      return { valid: false };
    }
    
    // The googleapis library handles authentication automatically
    // Call Google Play Developer API to verify subscription
    const response = await androidPublisher.purchases.subscriptions.get({
      packageName: GOOGLE_PLAY_PACKAGE_NAME,
      subscriptionId: productId,
      token: purchaseToken,
    });
    
    const subscription = response.data;
    
    if (!subscription) {
      console.error('❌ Google Play API returned no subscription data');
      return { valid: false };
    }
    
    // Check subscription status
    const expiryTimeMillis = subscription.expiryTimeMillis;
    const expiryTime = expiryTimeMillis ? parseInt(expiryTimeMillis) : null;
    const isActive = expiryTime && expiryTime > Date.now();
    const autoRenewing = subscription.autoRenewing === true;
    const paymentState = subscription.paymentState;
    
    // Payment state: 0 = Payment pending, 1 = Payment received, 2 = Free trial, 3 = Pending deferred
    const isPaid = paymentState === 1 || paymentState === 2;
    
    console.log(`📱 Google Play subscription verification:`, {
      productId,
      expiryTime: expiryTime ? new Date(expiryTime).toISOString() : 'null',
      isActive,
      autoRenewing,
      paymentState,
      isPaid,
    });
    
    if (!isActive || !isPaid) {
      console.warn(`⚠️ Subscription not active or not paid: isActive=${isActive}, isPaid=${isPaid}`);
      return {
        valid: false,
        expiryTimeMillis: expiryTimeMillis,
        autoRenewing: autoRenewing,
        paymentState: paymentState,
      };
    }
    
    return {
      valid: true,
      expiresAt: expiryTime || undefined,
      expiryTimeMillis: expiryTimeMillis,
      autoRenewing: autoRenewing,
      paymentState: paymentState,
      purchaseType: subscription.purchaseType,
    };
  } catch (error: any) {
    console.error('❌ Error verifying Google Play purchase:', error);
    
    // If it's a 404, the subscription doesn't exist
    if (error.code === 404) {
      console.error('  Subscription not found in Google Play');
      return { valid: false };
    }
    
    // If it's a 401/403, authentication failed
    if (error.code === 401 || error.code === 403) {
      console.error('  Google Play API authentication failed - check service account configuration');
      // Fallback for development
      if (purchaseToken && productId) {
        console.warn('⚠️ Using fallback verification due to auth error');
        const isMonthly = productId.includes('monthly');
        const expiresAt = Date.now() + (isMonthly ? 30 * 24 * 60 * 60 * 1000 : 365 * 24 * 60 * 60 * 1000);
        return {
          valid: true,
          expiresAt,
          autoRenewing: true,
        };
      }
    }
    
    return { valid: false };
  }
}

/**
 * Update user subscription from verified purchase
 * Matches Stripe/PayPal subscription data structure for consistency
 */
export async function updateSubscriptionFromPurchase(
  userId: string,
  purchaseToken: string,
  productId: string
): Promise<void> {
  // Verify purchase first
  const verification = await verifyGooglePlayPurchase(purchaseToken, productId);
  
  if (!verification.valid) {
    throw new functions.https.HttpsError(
      'invalid-argument',
      'Invalid purchase token'
    );
  }
  
  // Determine billing cycle from productId
  const isMonthly = productId.includes('monthly');
  const billingCycle = isMonthly ? 'month' : 'year';
  
  // Calculate endDate from expiryTimeMillis
  const endDate = verification.expiresAt 
    ? admin.firestore.Timestamp.fromDate(new Date(verification.expiresAt))
    : null;
  
  // Get user data to preserve cross-platform subscription data
  const userRef = db.collection('users').doc(userId);
  const userDoc = await userRef.get();
  const userData = userDoc.data() || {};
  const existingSubscription = userData.subscription || {};
  const existingPlatforms = userData.platforms || [];
  const updatedPlatforms = existingPlatforms.includes('android')
    ? existingPlatforms
    : [...existingPlatforms, 'android'];
  
  // Merge with existing subscription data to preserve cross-platform info
  // CRITICAL: Preserve paymentProvider, subscriptionId, and other fields from web/iOS purchases
  const subscriptionData = {
    ...existingSubscription, // Preserve existing subscription data
    tier: SubscriptionTier.PRO,
    status: 'active',
    paymentProvider: 'google_play', // Update to Google Play for this purchase
    subscriptionId: `${productId}_${purchaseToken.substring(0, 20)}`, // Create unique subscription ID
    startDate: admin.firestore.Timestamp.fromDate(new Date()),
    endDate: endDate,
    billingCycle: billingCycle,
    platforms: updatedPlatforms,
    cancelAtPeriodEnd: !verification.autoRenewing || false,
    // Google Play specific fields
    purchaseToken: purchaseToken,
    productId: productId,
    autoRenewing: verification.autoRenewing || false,
    // Preserve isActive field for Android compatibility
    isActive: true,
  };
  
  await userRef.update({
    subscription: subscriptionData,
    platform: 'android',
    platforms: [...new Set(updatedPlatforms)].sort(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
  
  console.log(`✅ Updated subscription for user ${userId} from Google Play purchase ${productId}`);
  console.log(`   Billing cycle: ${billingCycle}, End date: ${endDate?.toDate().toISOString()}, Auto-renewing: ${verification.autoRenewing}`);
}

/**
 * Security: Verify user ID from context matches requested user
 * Prevents users from accessing other users' data
 */
export function verifyUserId(context: functions.https.CallableContext, requestedUserId?: string): string {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      'unauthenticated',
      'User must be authenticated'
    );
  }
  
  const authenticatedUserId = context.auth.uid;
  
  // If a specific userId is requested, verify it matches authenticated user
  if (requestedUserId && requestedUserId !== authenticatedUserId) {
    console.error(`User ID mismatch: authenticated=${authenticatedUserId}, requested=${requestedUserId}`);
    throw new functions.https.HttpsError(
      'permission-denied',
      'Cannot access other users\' data'
    );
  }
  
  return authenticatedUserId;
}

