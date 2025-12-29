/**
 * Test function to verify brief subscription filtering
 * Tests that:
 * - Morning briefs work for all users (free + pro)
 * - Afternoon/evening briefs only work for Pro users
 */

import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import { getUserSubscriptionTier, SubscriptionTier } from './subscriptionVerification';

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

/**
 * Test brief generation for a specific user and brief type
 * Call with: { userId: "user_id", briefType: "morning" | "afternoon" | "evening" }
 */
export const testBriefs = functions.https.onCall(async (data, context) => {
  try {
    const { userId, briefType = 'morning' } = data;

    if (!userId) {
      throw new functions.https.HttpsError('invalid-argument', 'userId is required');
    }

    if (!['morning', 'afternoon', 'evening'].includes(briefType)) {
      throw new functions.https.HttpsError('invalid-argument', 'briefType must be morning, afternoon, or evening');
    }

    console.log(`🧪 Testing ${briefType} brief for user: ${userId}`);

    // Get user subscription tier
    const tier = await getUserSubscriptionTier(userId);
    const isPro = tier === SubscriptionTier.PRO;
    const isFree = tier === SubscriptionTier.FREE;

    console.log(`   User tier: ${tier} (isPro: ${isPro}, isFree: ${isFree})`);

    // Check if brief should be generated based on subscription tier
    let shouldGenerate = false;
    let skipReason: string | null = null;

    if (briefType === 'morning') {
      // Morning briefs: All users (free + pro)
      shouldGenerate = true;
      console.log(`   ✅ Morning brief: Should generate for all users`);
    } else if (briefType === 'afternoon' || briefType === 'evening') {
      // Afternoon/evening briefs: Pro only
      if (isPro) {
        shouldGenerate = true;
        console.log(`   ✅ ${briefType} brief: Should generate for Pro user`);
      } else {
        shouldGenerate = false;
        skipReason = 'free_tier';
        console.log(`   ⏭️ ${briefType} brief: Should skip for free user`);
      }
    }

    // Get user document to check if they exist
    const userDoc = await db.collection('users').doc(userId).get();
    const userExists = userDoc.exists;

    // Check if brief would actually be stored (simulate the logic)
    const today = new Date().toISOString().split('T')[0];
    const briefDocId = `${briefType}_${today}`;
    const briefDoc = await db
      .collection('users')
      .doc(userId)
      .collection('briefs')
      .doc(briefDocId)
      .get();
    const hasExistingBrief = briefDoc.exists;

    // Get user data for additional info
    const userData = userDoc.exists ? userDoc.data() : null;
    const nudgesEnabled = userData?.nudgesEnabled !== false;
    const fcmTokens = userData?.fcmTokens || (userData?.fcmToken ? [userData.fcmToken] : []);

    const result = {
      success: true,
      userId,
      briefType,
      subscriptionTier: tier,
      isPro,
      isFree,
      shouldGenerate,
      skipReason,
      userExists,
      hasExistingBrief,
      existingBriefDate: hasExistingBrief ? briefDoc.data()?.date : null,
      nudgesEnabled,
      hasFcmToken: fcmTokens.length > 0,
      fcmTokenCount: fcmTokens.length,
      testDate: today,
      message: shouldGenerate
        ? `✅ ${briefType} brief should be generated for ${tier} user`
        : `⏭️ ${briefType} brief should be skipped for ${tier} user (${skipReason})`,
    };

    console.log(`   Result: ${result.message}`);
    return result;
  } catch (error: any) {
    console.error('❌ Error in test briefs function:', error);
    throw new functions.https.HttpsError('internal', `Failed to test briefs: ${error.message}`);
  }
});

/**
 * Test brief generation for multiple users at once
 * Useful for testing with a list of user IDs
 * Call with: { userIds: ["user1", "user2"], briefType: "morning" | "afternoon" | "evening" }
 */
export const testBriefsBatch = functions.https.onCall(async (data, context) => {
  try {
    const { userIds, briefType = 'morning' } = data;

    if (!userIds || !Array.isArray(userIds) || userIds.length === 0) {
      throw new functions.https.HttpsError('invalid-argument', 'userIds array is required');
    }

    if (!['morning', 'afternoon', 'evening'].includes(briefType)) {
      throw new functions.https.HttpsError('invalid-argument', 'briefType must be morning, afternoon, or evening');
    }

    console.log(`🧪 Testing ${briefType} briefs for ${userIds.length} users`);

    const results = await Promise.all(
      userIds.map(async (userId: string) => {
        try {
          const tier = await getUserSubscriptionTier(userId);
          const isPro = tier === SubscriptionTier.PRO;
          
          let shouldGenerate = false;
          let skipReason: string | null = null;

          if (briefType === 'morning') {
            shouldGenerate = true;
          } else if (briefType === 'afternoon' || briefType === 'evening') {
            if (isPro) {
              shouldGenerate = true;
            } else {
              shouldGenerate = false;
              skipReason = 'free_tier';
            }
          }

          return {
            userId,
            tier,
            isPro,
            shouldGenerate,
            skipReason,
            message: shouldGenerate
              ? `✅ Should generate`
              : `⏭️ Should skip (${skipReason})`,
          };
        } catch (error: any) {
          return {
            userId,
            error: error.message,
            shouldGenerate: false,
          };
        }
      })
    );

    const summary = {
      total: results.length,
      shouldGenerate: results.filter((r) => r.shouldGenerate).length,
      shouldSkip: results.filter((r) => !r.shouldGenerate && !r.error).length,
      errors: results.filter((r) => r.error).length,
      proUsers: results.filter((r) => r.isPro).length,
      freeUsers: results.filter((r) => !r.isPro && !r.error).length,
    };

    console.log(`   Summary: ${summary.shouldGenerate} should generate, ${summary.shouldSkip} should skip, ${summary.errors} errors`);

    return {
      success: true,
      briefType,
      summary,
      results,
    };
  } catch (error: any) {
    console.error('❌ Error in test briefs batch function:', error);
    throw new functions.https.HttpsError('internal', `Failed to test briefs batch: ${error.message}`);
  }
});
