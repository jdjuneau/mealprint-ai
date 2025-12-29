/**
 * Cloud Function to verify Google Play purchases and update subscription
 */

import * as functions from 'firebase-functions';
import { verifyGooglePlayPurchase, updateSubscriptionFromPurchase } from './subscriptionVerification';

export const verifyPurchase = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }
  
  const userId = context.auth.uid;
  const { purchaseToken, productId } = data;
  
  if (!purchaseToken || !productId) {
    throw new functions.https.HttpsError('invalid-argument', 'Missing purchaseToken or productId');
  }
  
  try {
    // Verify purchase with Google Play Billing API
    const verification = await verifyGooglePlayPurchase(purchaseToken, productId);
    
    if (!verification.valid) {
      throw new functions.https.HttpsError('invalid-argument', 'Invalid purchase token');
    }
    
    // Update subscription in Firestore
    await updateSubscriptionFromPurchase(userId, purchaseToken, productId);
    
    console.log(`Subscription updated for user ${userId} from purchase ${productId}`);
    
    return { 
      success: true,
      expiresAt: verification.expiresAt
    };
  } catch (error: any) {
    console.error(`Error verifying purchase for user ${userId}:`, error);
    throw new functions.https.HttpsError('internal', error.message || 'Failed to verify purchase');
  }
});

