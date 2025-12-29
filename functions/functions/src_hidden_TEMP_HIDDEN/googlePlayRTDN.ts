/**
 * Google Play Real-time Developer Notifications (RTDN) Handler
 * 
 * This handles webhook notifications from Google Play when subscription events occur:
 * - Subscription renewals
 * - Subscription cancellations
 * - Payment failures
 * - Grace period changes
 * 
 * Setup: Configure RTDN in Google Play Console to send notifications to this endpoint
 */

import * as admin from 'firebase-admin';
import * as functions from 'firebase-functions';
import { google } from 'googleapis';

const db = admin.firestore();
const GOOGLE_PLAY_PACKAGE_NAME = 'com.coachie.app';

/**
 * Get Google Play API client
 */
function getGooglePlayClient() {
  try {
    const serviceAccountKey = functions.config().googleplay?.service_account_key;
    
    let serviceAccount: any;
    
    if (!serviceAccountKey) {
      const envServiceAccount = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT;
      if (!envServiceAccount) {
        console.error('❌ Google Play service account not configured');
        return null;
      }
      serviceAccount = JSON.parse(envServiceAccount);
    } else {
      serviceAccount = typeof serviceAccountKey === 'string' 
        ? JSON.parse(serviceAccountKey)
        : serviceAccountKey;
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
  } catch (error) {
    console.error('Error creating Google Play API client:', error);
    return null;
  }
}

/**
 * Process Google Play Real-time Developer Notification
 * 
 * RTDN sends notifications for:
 * - SUBSCRIPTION_RECOVERED: Subscription recovered from account hold
 * - SUBSCRIPTION_RENEWED: Subscription renewed successfully
 * - SUBSCRIPTION_CANCELED: User canceled subscription
 * - SUBSCRIPTION_PURCHASED: New subscription purchased
 * - SUBSCRIPTION_ON_HOLD: Subscription on hold (payment issue)
 * - SUBSCRIPTION_IN_GRACE_PERIOD: Subscription in grace period
 * - SUBSCRIPTION_RESTARTED: User restarted subscription
 * - SUBSCRIPTION_PRICE_CHANGE_CONFIRMED: User confirmed price change
 * - SUBSCRIPTION_DEFERRED: Subscription deferred
 * - SUBSCRIPTION_PAUSED: Subscription paused
 * - SUBSCRIPTION_PAUSE_SCHEDULE_CHANGED: Pause schedule changed
 * - SUBSCRIPTION_REVOKED: Subscription revoked (refunded)
 * - SUBSCRIPTION_EXPIRED: Subscription expired
 */
export const processGooglePlayRTDN = functions.https.onRequest(async (req, res) => {
  try {
    console.log('📱 Google Play RTDN notification received');
    
    // Verify the notification is from Google Play (optional but recommended)
    // You can verify the JWT signature if needed
    
    const message = req.body.message;
    if (!message) {
      console.error('❌ No message in RTDN notification');
      res.status(400).send('No message');
      return;
    }
    
    // Decode the base64 message
    const decodedMessage = Buffer.from(message.data, 'base64').toString('utf-8');
    const notification = JSON.parse(decodedMessage);
    
    console.log('📋 RTDN notification:', JSON.stringify(notification, null, 2));
    
    const subscriptionNotification = notification.subscriptionNotification;
    if (!subscriptionNotification) {
      console.warn('⚠️ No subscriptionNotification in message');
      res.status(200).send('OK'); // Return 200 to acknowledge receipt
      return;
    }
    
    const {
      version,
      notificationType,
      purchaseToken,
      subscriptionId,
    } = subscriptionNotification;
    
    console.log(`📱 Processing RTDN: type=${notificationType}, subscriptionId=${subscriptionId}, version=${version}`);
    
    // Find user by purchaseToken or subscriptionId
    const usersSnapshot = await db.collection('users')
      .where('subscription.purchaseToken', '==', purchaseToken)
      .limit(1)
      .get();
    
    if (usersSnapshot.empty) {
      console.warn(`⚠️ No user found with purchaseToken: ${purchaseToken}`);
      res.status(200).send('OK'); // Acknowledge even if user not found
      return;
    }
    
    const userRef = usersSnapshot.docs[0].ref;
    const userId = usersSnapshot.docs[0].id;
    const userData = usersSnapshot.docs[0].data();
    const existingSubscription = userData.subscription || {};
    
    console.log(`✅ Found user: ${userId}`);
    
    // Handle different notification types
    switch (notificationType) {
      case 1: // SUBSCRIPTION_RECOVERED
        console.log('🔄 Subscription recovered from account hold');
        await handleSubscriptionRecovered(userRef, subscriptionId, purchaseToken);
        break;
        
      case 2: // SUBSCRIPTION_RENEWED
        console.log('✅ Subscription renewed - updating endDate');
        await handleSubscriptionRenewed(userRef, subscriptionId, purchaseToken);
        break;
        
      case 3: // SUBSCRIPTION_CANCELED
        console.log('❌ Subscription canceled');
        await handleSubscriptionCanceled(userRef, subscriptionId);
        break;
        
      case 4: // SUBSCRIPTION_PURCHASED
        console.log('🆕 New subscription purchased');
        await handleSubscriptionPurchased(userRef, subscriptionId, purchaseToken);
        break;
        
      case 5: // SUBSCRIPTION_ON_HOLD
        console.log('⏸️ Subscription on hold (payment issue)');
        await handleSubscriptionOnHold(userRef, subscriptionId);
        break;
        
      case 6: // SUBSCRIPTION_IN_GRACE_PERIOD
        console.log('⏳ Subscription in grace period');
        await handleSubscriptionInGracePeriod(userRef, subscriptionId, purchaseToken);
        break;
        
      case 7: // SUBSCRIPTION_RESTARTED
        console.log('🔄 Subscription restarted');
        await handleSubscriptionRestarted(userRef, subscriptionId, purchaseToken);
        break;
        
      case 8: // SUBSCRIPTION_PRICE_CHANGE_CONFIRMED
        console.log('💰 Price change confirmed');
        await handleSubscriptionRenewed(userRef, subscriptionId, purchaseToken);
        break;
        
      case 9: // SUBSCRIPTION_DEFERRED
        console.log('⏸️ Subscription deferred');
        // Keep subscription active but note it's deferred
        break;
        
      case 10: // SUBSCRIPTION_PAUSED
        console.log('⏸️ Subscription paused');
        await handleSubscriptionPaused(userRef, subscriptionId);
        break;
        
      case 11: // SUBSCRIPTION_PAUSE_SCHEDULE_CHANGED
        console.log('📅 Pause schedule changed');
        // Update pause schedule info
        break;
        
      case 12: // SUBSCRIPTION_REVOKED
        console.log('❌ Subscription revoked (refunded)');
        await handleSubscriptionRevoked(userRef, subscriptionId);
        break;
        
      case 13: // SUBSCRIPTION_EXPIRED
        console.log('⏰ Subscription expired');
        await handleSubscriptionExpired(userRef, subscriptionId);
        break;
        
      default:
        console.warn(`⚠️ Unknown notification type: ${notificationType}`);
    }
    
    res.status(200).send('OK');
  } catch (error: any) {
    console.error('❌ Error processing Google Play RTDN:', error);
    // Always return 200 to acknowledge receipt (don't retry)
    res.status(200).send('OK');
  }
});

/**
 * Verify Google Play subscription and get expiry time
 */
async function verifyAndGetExpiry(
  subscriptionId: string,
  purchaseToken: string
): Promise<{ expiresAt: number | null; autoRenewing: boolean } | null> {
  try {
    const androidPublisher = getGooglePlayClient();
    if (!androidPublisher) {
      console.error('❌ Google Play API client not available');
      return null;
    }
    
    // The googleapis library handles authentication automatically
    const response = await androidPublisher.purchases.subscriptions.get({
      packageName: GOOGLE_PLAY_PACKAGE_NAME,
      subscriptionId: subscriptionId,
      token: purchaseToken,
    });
    
    const subscription = response.data;
    if (!subscription || !subscription.expiryTimeMillis) {
      console.warn('⚠️ No expiry time in subscription data');
      return null;
    }
    
    const expiryTime = parseInt(subscription.expiryTimeMillis);
    const autoRenewing = subscription.autoRenewing === true;
    
    console.log(`📱 Subscription verification: expiry=${new Date(expiryTime).toISOString()}, autoRenewing=${autoRenewing}`);
    
    return {
      expiresAt: expiryTime,
      autoRenewing: autoRenewing,
    };
  } catch (error: any) {
    console.error('❌ Error verifying subscription:', error);
    if (error.code === 404) {
      console.error('  Subscription not found in Google Play');
    } else if (error.code === 401 || error.code === 403) {
      console.error('  Authentication failed - check service account configuration');
    }
    return null;
  }
}

/**
 * Handle subscription renewed - update endDate
 */
async function handleSubscriptionRenewed(
  userRef: admin.firestore.DocumentReference,
  subscriptionId: string,
  purchaseToken: string
) {
  try {
    // Verify subscription with Google Play API to get latest expiry time
    const verification = await verifyAndGetExpiry(subscriptionId, purchaseToken);
    
    if (!verification || !verification.expiresAt) {
      console.error('❌ Could not verify subscription renewal');
      return;
    }
    
    const endDate = admin.firestore.Timestamp.fromDate(new Date(verification.expiresAt));
    
    // Update subscription endDate
    await userRef.update({
      'subscription.endDate': endDate,
      'subscription.status': 'active',
      'subscription.autoRenewing': verification.autoRenewing || true,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    
    console.log(`✅ Updated subscription endDate to ${endDate.toDate().toISOString()}`);
  } catch (error) {
    console.error('Error handling subscription renewal:', error);
  }
}

/**
 * Handle subscription recovered
 */
async function handleSubscriptionRecovered(
  userRef: admin.firestore.DocumentReference,
  subscriptionId: string,
  purchaseToken: string
) {
  await handleSubscriptionRenewed(userRef, subscriptionId, purchaseToken);
}

/**
 * Handle subscription purchased (new subscription)
 */
async function handleSubscriptionPurchased(
  userRef: admin.firestore.DocumentReference,
  subscriptionId: string,
  purchaseToken: string
) {
  await handleSubscriptionRenewed(userRef, subscriptionId, purchaseToken);
}

/**
 * Handle subscription restarted
 */
async function handleSubscriptionRestarted(
  userRef: admin.firestore.DocumentReference,
  subscriptionId: string,
  purchaseToken: string
) {
  await handleSubscriptionRenewed(userRef, subscriptionId, purchaseToken);
}

/**
 * Handle subscription in grace period
 */
async function handleSubscriptionInGracePeriod(
  userRef: admin.firestore.DocumentReference,
  subscriptionId: string,
  purchaseToken: string
) {
  // Keep subscription active during grace period
  await userRef.update({
    'subscription.status': 'active', // Still active during grace period
    'subscription.inGracePeriod': true,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
}

/**
 * Handle subscription canceled
 */
async function handleSubscriptionCanceled(
  userRef: admin.firestore.DocumentReference,
  subscriptionId: string
) {
  await userRef.update({
    'subscription.cancelAtPeriodEnd': true,
    'subscription.autoRenewing': false,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
}

/**
 * Handle subscription on hold
 */
async function handleSubscriptionOnHold(
  userRef: admin.firestore.DocumentReference,
  subscriptionId: string
) {
  // Keep subscription active but mark as on hold
  await userRef.update({
    'subscription.status': 'active', // Still active, just on hold
    'subscription.onHold': true,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
}

/**
 * Handle subscription paused
 */
async function handleSubscriptionPaused(
  userRef: admin.firestore.DocumentReference,
  subscriptionId: string
) {
  await userRef.update({
    'subscription.status': 'active', // Still active, just paused
    'subscription.paused': true,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
}

/**
 * Handle subscription revoked (refunded)
 */
async function handleSubscriptionRevoked(
  userRef: admin.firestore.DocumentReference,
  subscriptionId: string
) {
  await userRef.update({
    'subscription.status': 'canceled',
    'subscription.isActive': false,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
}

/**
 * Handle subscription expired
 */
async function handleSubscriptionExpired(
  userRef: admin.firestore.DocumentReference,
  subscriptionId: string
) {
  await userRef.update({
    'subscription.status': 'expired',
    'subscription.isActive': false,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
}
