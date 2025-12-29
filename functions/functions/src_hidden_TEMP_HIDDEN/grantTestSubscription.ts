/**
 * Cloud Function to manually grant Pro subscription for testing
 * This bypasses Google Play purchase verification
 * 
 * ⚠️⚠️⚠️ PRODUCTION DEPLOYMENT REMINDER ⚠️⚠️⚠️
 * 
 * BEFORE DEPLOYING TO PRODUCTION, YOU MUST:
 * 1. Find your Firebase Auth User ID (Firebase Console → Authentication → Users)
 * 2. Replace 'YOUR_FIREBASE_USER_ID' in isAdmin() function below
 * 3. Uncomment the admin checks in grantTestSubscription() function
 * 4. Uncomment the admin checks in grantProToAllExistingUsers() function
 * 5. Test that only admins can call these functions
 * 
 * See PRODUCTION_DEPLOYMENT_CHECKLIST.md for complete checklist
 * 
 * ⚠️ WARNING: Without admin checks, ANYONE can grant themselves Pro subscriptions!
 */

import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

/**
 * Check if a user is an admin
 * 
 * ⚠️ TO ENABLE ADMIN VERIFICATION:
 * 1. Find your Firebase Auth User ID:
 *    - Go to Firebase Console → Authentication → Users
 *    - Find your account and copy the "User UID"
 * 2. Replace 'YOUR_FIREBASE_USER_ID' below with your actual User ID
 * 3. Uncomment the admin checks in the functions below
 * 
 * This prevents anyone from granting themselves Pro subscriptions.
 */
function isAdmin(uid: string): boolean {
  // ⚠️ REPLACE WITH YOUR ACTUAL FIREBASE AUTH USER ID
  // To find it: Firebase Console → Authentication → Users → Copy "User UID"
  const adminUids = [
    'YOUR_FIREBASE_USER_ID',  // Replace this with your actual User ID
    // Add more admin IDs as needed: 'admin_user_id_2', 'admin_user_id_3'
  ];
  
  return adminUids.includes(uid);
}

// NOTE: isAdmin is currently only referenced in commented-out production checks.
// To keep TypeScript happy (noUnusedLocals) while still documenting the function,
// we make a no-op reference that will never execute.
if (false) {
  // eslint-disable-next-line no-console
  console.log('isAdmin helper loaded (noop)', isAdmin('test'));
}

/**
 * Grant Pro subscription to a user for testing
 * Can be called from Firebase Console or your app
 */
export const grantTestSubscription = functions.https.onCall(async (data, context) => {
  // ⚠️ SECURITY: Uncomment these lines before production deployment!
  // This ensures only admins can grant subscriptions
  // 
  // if (!context.auth) {
  //   throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  // }
  // 
  // if (!isAdmin(context.auth.uid)) {
  //   console.error(`Unauthorized attempt to grant subscription by user: ${context.auth.uid}`);
  //   throw new functions.https.HttpsError('permission-denied', 'Only admins can grant test subscriptions');
  // }
  
  // ⚠️ TESTING MODE: This function is currently open for testing
  // In production, you MUST uncomment the admin checks above

  const { userId, durationMonths = 3 } = data;

  if (!userId) {
    throw new functions.https.HttpsError('invalid-argument', 'userId is required');
  }

  try {
    // First verify the user document exists
    const userRef = db.collection('users').doc(userId);
    const userDoc = await userRef.get();
    
    if (!userDoc.exists) {
      console.error(`User document does not exist for userId: ${userId}`);
      throw new functions.https.HttpsError('not-found', `User document not found. User must complete profile setup first.`);
    }

    const expiresAt = Date.now() + (durationMonths * 30 * 24 * 60 * 60 * 1000);

    await userRef.update({
      subscription: {
        tier: 'pro',
        status: 'active', // Required for getUserSubscriptionTier to detect Pro
        expiresAt: expiresAt,
        isActive: true,
        purchasedAt: Date.now(),
        purchaseToken: `TEST_TOKEN_${Date.now()}`, // Mark as test
        productId: 'test_subscription'
      }
    });

    console.log(`Granted test Pro subscription to user ${userId} for ${durationMonths} months`);

    return {
      success: true,
      userId: userId,
      expiresAt: expiresAt,
      message: `Pro subscription granted for ${durationMonths} months`
    };
  } catch (error: any) {
    console.error(`Error granting test subscription to user ${userId}:`, error);
    
    // If it's already an HttpsError, re-throw it
    if (error instanceof functions.https.HttpsError) {
      throw error;
    }
    
    // Provide more specific error messages
    if (error.code === 5 || error.message?.includes('NOT_FOUND')) {
      throw new functions.https.HttpsError('not-found', `User document not found for userId: ${userId}. User must complete profile setup first.`);
    }
    
    throw new functions.https.HttpsError('internal', error.message || 'Failed to grant test subscription');
  }
});

/**
 * Grant Pro subscription to ALL existing users (grandfathering)
 * Use this once to grant Pro access to all current testers
 */
export const grantProToAllExistingUsers = functions.https.onCall(async (data, context) => {
  // ⚠️ SECURITY: Uncomment these lines before production deployment!
  // This function can grant Pro to ALL users - it MUST be admin-only!
  // 
  // if (!context.auth) {
  //   throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  // }
  // 
  // if (!isAdmin(context.auth.uid)) {
  //   console.error(`Unauthorized attempt to grant Pro to all users by: ${context.auth.uid}`);
  //   throw new functions.https.HttpsError('permission-denied', 'Only admins can run this');
  // }

  // durationMonths: Number of months (default 12 for grandfathering)
  // Set to null or a very large number for "lifetime" access
  const { durationMonths = 12 } = data;

  try {
    const usersSnapshot = await db.collection('users').get();
    // If durationMonths is null or very large, set expiration far in the future (10 years)
    const expiresAt = durationMonths == null || durationMonths > 120 
      ? Date.now() + (10 * 365 * 24 * 60 * 60 * 1000) // 10 years
      : Date.now() + (durationMonths * 30 * 24 * 60 * 60 * 1000);

    let updated = 0;
    let skipped = 0;
    let errors = 0;

    const batch = db.batch();
    let batchCount = 0;
    const BATCH_SIZE = 500; // Firestore batch limit

    for (const userDoc of usersSnapshot.docs) {
      const userData = userDoc.data();
      
      // Skip if user already has Pro subscription
      if (userData.subscription?.tier === 'pro' && userData.subscription?.isActive) {
        skipped++;
        continue;
      }

      batch.update(userDoc.ref, {
        subscription: {
          tier: 'pro',
          status: 'active', // Required for getUserSubscriptionTier to detect Pro
          expiresAt: expiresAt,
          isActive: true,
          purchasedAt: Date.now(),
          purchaseToken: `GRANDFATHERED_${Date.now()}`,
          productId: 'grandfathered_subscription'
        }
      });

      batchCount++;
      updated++;

      // Commit batch when it reaches limit
      if (batchCount >= BATCH_SIZE) {
        await batch.commit();
        batchCount = 0;
      }
    }

    // Commit remaining updates
    if (batchCount > 0) {
      await batch.commit();
    }

    console.log(`Granted Pro subscription to ${updated} users, skipped ${skipped}, errors: ${errors}`);

    return {
      success: true,
      updated: updated,
      skipped: skipped,
      errors: errors,
      expiresAt: expiresAt,
      message: `Granted Pro subscription to ${updated} existing users for ${durationMonths} months`
    };
  } catch (error: any) {
    console.error('Error granting Pro to all users:', error);
    throw new functions.https.HttpsError('internal', error.message || 'Failed to grant Pro subscriptions');
  }
});

