import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import express = require('express');
import { getUserSubscriptionTier, SubscriptionTier } from './subscriptionVerification';

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

/**
 * Generate and send briefs to users based on subscription tier
 * - Morning briefs: Generated for ALL users (free + pro)
 * - Afternoon briefs: Generated ONLY for Pro users
 * - Evening briefs: Generated ONLY for Pro users
 * - Briefs are stored in Firestore for app display
 * - Push notifications only sent to users with notifications enabled
 */
export async function sendBriefsToAllUsers(briefType: 'morning' | 'afternoon' | 'evening') {
  try {
    // CRITICAL: Get ALL users, not just those with notifications enabled
    // Briefs should be generated for everyone so they can see them in the app
    const allUsersSnapshot = await db
      .collection('users')
      .get();

    if (allUsersSnapshot.empty) {
      console.log(`No users found for ${briefType} briefs`);
      return { success: true, usersNotified: 0, briefsGenerated: 0 };
    }

    console.log(`Found ${allUsersSnapshot.size} total users - generating briefs for all`);

    // Import generateBriefInternal from compiled lib (runtime require, not compile-time import)
    const { generateBriefInternal } = require('../lib/generateBrief');

    // Process users in batches to avoid timeout and improve error handling
    const batchSize = 10;
    const allUsers = allUsersSnapshot.docs;
    const totalBatches = Math.ceil(allUsers.length / batchSize);
    
    console.log(`Processing ${allUsers.length} users in ${totalBatches} batches of ${batchSize}`);
    
    const allResults: any[] = [];
    
    for (let batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
      const batchStart = batchIndex * batchSize;
      const batchEnd = Math.min(batchStart + batchSize, allUsers.length);
      const batch = allUsers.slice(batchStart, batchEnd);
      
      console.log(`Processing batch ${batchIndex + 1}/${totalBatches} (users ${batchStart + 1}-${batchEnd} of ${allUsers.length})`);
      
      const batchResults = await Promise.allSettled(
        batch.map(async (userDoc) => {
          try {
            const userId = userDoc.id;
            const userData = userDoc.data();
            const userName = userData.name || userData.email || userId;
            
            // Check subscription tier - free users only get morning briefs
            const tier = await getUserSubscriptionTier(userId);
            const isPro = tier === SubscriptionTier.PRO;
            
            // Skip afternoon and evening briefs for free users
            if ((briefType === 'afternoon' || briefType === 'evening') && !isPro) {
              console.log(`Skipping ${briefType} brief for free user ${userName} (${userId}) - Pro only`);
              return { userId, success: true, briefStored: false, notificationSent: false, skipped: true, reason: 'free_tier' };
            }
            
            const nudgesEnabled = userData.nudgesEnabled !== false; // Default to true if not set
            // Support both fcmToken (legacy) and fcmTokens (array)
            const fcmTokens = userData.fcmTokens || (userData.fcmToken ? [userData.fcmToken] : []);

            let brief: string;
            let briefGenerated = false;

            try {
            // Generate the brief using the shared generateBriefInternal function
            // This is done for ALL users, regardless of notification settings
            console.log(`Generating ${briefType} brief for user ${userName} (${userId})...`);
            const result = await generateBriefInternal(userId, briefType);
            brief = result?.brief || getFallbackBrief(briefType);
            briefGenerated = true;
            console.log(`✅ Generated ${briefType} brief for user ${userName} (${userId}) (length: ${brief.length})`);
          } catch (genError: any) {
            console.error(`❌ Error generating ${briefType} brief for user ${userName} (${userId}):`, genError);
            console.error(`   Error message: ${genError?.message || 'Unknown error'}`);
            console.error(`   Error stack: ${genError?.stack || 'No stack trace'}`);
            // Use fallback brief if generation fails - still store it so user sees something
            brief = getFallbackBrief(briefType);
            briefGenerated = false;
          }

        // CRITICAL: Store the ACTUAL brief text (not notification text) in Firestore
        // This is the ONLY storage - no caching, just storing the generated brief until next one
        // Store for ALL users so the app can display it
        const today = new Date().toISOString().split('T')[0];
        try {
          await db
            .collection('users')
            .doc(userId)
            .collection('briefs')
            .doc(`${briefType}_${today}`)
            .set({
              brief, // Store the actual brief text
              briefType,
              generatedAt: admin.firestore.FieldValue.serverTimestamp(),
              date: today,
              generated: briefGenerated, // Track if it was actually generated or fallback
            });
          console.log(`✅ Stored ${briefType} brief for user ${userId} (date: ${today}, generated: ${briefGenerated})`);
        } catch (storeError: any) {
          console.error(`❌ CRITICAL: Failed to store brief for user ${userId}:`, storeError);
          throw storeError; // Re-throw - this is critical
        }

        // Only send push notification if user has notifications enabled AND has FCM token
        let notificationSent = false;
        if (nudgesEnabled && fcmTokens.length > 0) {
          try {
            // Determine deep link based on brief type
            let deepLink: string | undefined;
            let screen: string | undefined;
            
            if (briefType === 'evening') {
              // Evening briefs often suggest reflection/journaling
              deepLink = 'coachie://journal_flow';
              screen = 'Journal';
            } else if (briefType === 'morning') {
              // Morning briefs might suggest checking habits or goals
              deepLink = 'coachie://habits';
              screen = 'Habits';
            }
            
            // Send FCM notification to all tokens (multicast)
            const message = {
              tokens: fcmTokens,
              notification: {
                title: getBriefTitle(briefType),
                body: brief.substring(0, 200) + (brief.length > 200 ? '...' : ''), // Brief text, truncated for notification
              },
              data: {
                type: 'daily_brief',
                briefType: briefType,
                timestamp: new Date().toISOString(),
                ...(deepLink && { deepLink }),
                ...(screen && { screen }),
              },
              android: {
                priority: 'high' as const,
                notification: {
                  sound: 'default',
                  channelId: 'coachie_briefs',
                },
              },
              apns: {
                payload: {
                  aps: {
                    sound: 'default',
                  },
                },
              },
            };

            const response = await admin.messaging().sendEachForMulticast(message);
            console.log(`✅ Sent ${briefType} brief notification to user ${userId} (${response.successCount}/${fcmTokens.length} tokens successful)`);
            notificationSent = response.successCount > 0;
          } catch (notificationError: any) {
            console.error(`Error sending notification to user ${userId}:`, notificationError);
            // Don't fail the whole operation if notification fails - brief is still stored
          }
        } else {
          if (!nudgesEnabled) {
            console.log(`User ${userId} has notifications disabled - brief stored but no notification sent`);
          } else if (fcmTokens.length === 0) {
            console.log(`User ${userId} has no FCM token(s) - brief stored but no notification sent`);
          }
        }

          return { userId, success: true, briefStored: true, notificationSent };
        } catch (error: any) {
          const errorUserData = userDoc.data();
          const userName = errorUserData?.name || errorUserData?.email || userDoc.id;
          console.error(`❌ Error processing ${briefType} brief for user ${userName} (${userDoc.id}):`, error);
          console.error(`   Error message: ${error?.message || 'Unknown error'}`);
          return { userId: userDoc.id, success: false, reason: error.message, briefStored: false, notificationSent: false };
        }
      })
      );
      
      allResults.push(...batchResults);
      console.log(`✅ Batch ${batchIndex + 1}/${totalBatches} completed`);
    }
    
    const results = allResults;

    const successful = results.filter((r) => r.status === 'fulfilled' && r.value.success).length;
    const briefsGenerated = results.filter((r) => r.status === 'fulfilled' && r.value.briefStored).length;
    const notificationsSent = results.filter((r) => r.status === 'fulfilled' && r.value.notificationSent).length;
    const skipped = results.filter((r) => r.status === 'fulfilled' && r.value.skipped).length;
    const failed = results.length - successful;

    console.log(`${briefType} brief processing complete. Briefs generated: ${briefsGenerated}, Notifications sent: ${notificationsSent}, Skipped (free tier): ${skipped}, Failed: ${failed}`);

    return {
      success: true,
      briefType,
      totalUsers: allUsersSnapshot.size,
      briefsGenerated,
      notificationsSent,
      skipped,
      successful,
      failed,
    };
  } catch (error) {
    console.error(`Error in send${briefType}Briefs function:`, error);
    throw new functions.https.HttpsError('internal', `Failed to send ${briefType} briefs`);
  }
}

/**
 * Get brief title based on type
 */
function getBriefTitle(briefType: 'morning' | 'afternoon' | 'evening'): string {
  switch (briefType) {
    case 'morning':
      return '🌅 Good Morning Brief';
    case 'afternoon':
      return '☀️ Afternoon Check-in';
    case 'evening':
      return '🌙 Good Evening Brief';
    default:
      return 'Coachie Brief';
  }
}

/**
 * Get fallback brief if generation fails
 */
function getFallbackBrief(briefType: 'morning' | 'afternoon' | 'evening'): string {
  switch (briefType) {
    case 'morning':
      return 'Good morning! Ready to make today amazing? Check your app for your personalized brief!';
    case 'afternoon':
      return 'Good afternoon! How\'s your day going? Don\'t forget to log your lunch and activities!';
    case 'evening':
      return 'Good evening! Let\'s review your day and finish strong! Check your app for your personalized brief!';
    default:
      return 'Check your app for your personalized brief!';
  }
}

/**
 * Send morning brief notifications at 9 AM
 * Uses Cloud Tasks for scalable processing
 */
export const sendMorningBriefs = functions
  .runWith({
    timeoutSeconds: 540, // 9 minutes (max for pubsub functions)
    memory: '512MB'
  })
  .pubsub
  .schedule('0 9 * * *') // Every day at 9:00 AM
  .timeZone('America/New_York')
  .onRun(async (context) => {
    console.log('🌅 Starting morning brief task queue at 9 AM');
    const startTime = Date.now();
    try {
      // Use Cloud Tasks for scalable processing
      const { enqueueBriefTasks } = require('./briefTaskQueue');
      const result = await enqueueBriefTasks('morning');
      const duration = ((Date.now() - startTime) / 1000).toFixed(2);
      console.log(`✅ Morning brief tasks enqueued in ${duration}s. Result:`, JSON.stringify(result));
      return result;
    } catch (error: any) {
      const duration = ((Date.now() - startTime) / 1000).toFixed(2);
      console.error(`❌ Morning brief task queue failed after ${duration}s:`, error);
      // Fallback to old method if task queue fails
      console.log('⚠️ Falling back to direct processing...');
      const result = await sendBriefsToAllUsers('morning');
      return result;
    }
  });

/**
 * Send afternoon brief notifications at 2 PM
 * Uses Cloud Tasks for scalable processing
 */
export const sendAfternoonBriefs = functions
  .runWith({
    timeoutSeconds: 540,
    memory: '512MB'
  })
  .pubsub
  .schedule('0 14 * * *') // Every day at 2:00 PM
  .timeZone('America/New_York')
  .onRun(async (context) => {
    console.log('☀️ Starting afternoon brief task queue at 2 PM');
    try {
      // Use Cloud Tasks for scalable processing
      const { enqueueBriefTasks } = require('./briefTaskQueue');
      return await enqueueBriefTasks('afternoon');
    } catch (error: any) {
      console.error(`❌ Afternoon brief task queue failed:`, error);
      // Fallback to old method if task queue fails
      console.log('⚠️ Falling back to direct processing...');
      return await sendBriefsToAllUsers('afternoon');
    }
  });

/**
 * Send evening brief notifications at 6 PM
 * Uses Cloud Tasks for scalable processing
 */
export const sendEveningBriefs = functions
  .runWith({
    timeoutSeconds: 540,
    memory: '512MB'
  })
  .pubsub
  .schedule('0 18 * * *') // Every day at 6:00 PM
  .timeZone('America/New_York')
  .onRun(async (context) => {
    console.log('🌙 Starting evening brief task queue at 6 PM');
    try {
      // Use Cloud Tasks for scalable processing
      const { enqueueBriefTasks } = require('./briefTaskQueue');
      return await enqueueBriefTasks('evening');
    } catch (error: any) {
      console.error(`❌ Evening brief task queue failed:`, error);
      // Fallback to old method if task queue fails
      console.log('⚠️ Falling back to direct processing...');
      return await sendBriefsToAllUsers('evening');
    }
  });

/**
 * Send welcome brief to a single new user
 * Called when a new user document is created
 */
async function sendWelcomeBriefToUser(userId: string): Promise<void> {
  try {
    console.log(`👋 Sending welcome brief to new user ${userId}`);
    
    // Import generateBriefInternal from compiled lib
    const { generateBriefInternal } = require('../lib/generateBrief');
    
    // Generate welcome brief (will be welcome message for new users)
    const result = await generateBriefInternal(userId, 'morning');
    const brief = result?.brief || getFallbackBrief('morning');
    
    // Store the welcome brief in Firestore
    const today = new Date().toISOString().split('T')[0];
    await db
      .collection('users')
      .doc(userId)
      .collection('briefs')
      .doc(`morning_${today}`)
      .set({
        brief,
        briefType: 'morning',
        generatedAt: admin.firestore.FieldValue.serverTimestamp(),
        date: today,
        generated: true,
        isWelcomeBrief: true, // Mark as welcome brief
      });
    
    console.log(`✅ Stored welcome brief for user ${userId}`);
    
    // Get user data to check notification settings
    const userDoc = await db.collection('users').doc(userId).get();
    const userData = userDoc.data();
    
    if (!userData) {
      console.log(`User ${userId} data not found - skipping notification`);
      return;
    }
    
    const nudgesEnabled = userData.nudgesEnabled !== false; // Default to true
    const fcmTokens = userData.fcmTokens || (userData.fcmToken ? [userData.fcmToken] : []);
    
    // Send push notification if enabled and token exists
    if (nudgesEnabled && fcmTokens.length > 0) {
      try {
        const message = {
          tokens: fcmTokens,
          notification: {
            title: '👋 Welcome to Coachie!',
            body: brief.substring(0, 200) + (brief.length > 200 ? '...' : ''),
          },
          data: {
            type: 'daily_brief',
            briefType: 'morning',
            timestamp: new Date().toISOString(),
            deepLink: 'coachie://habits',
            screen: 'Habits',
            isWelcomeBrief: 'true',
          },
          android: {
            priority: 'high' as const,
            notification: {
              sound: 'default',
              channelId: 'coachie_briefs',
            },
          },
          apns: {
            payload: {
              aps: {
                sound: 'default',
              },
            },
          },
        };
        
        const response = await admin.messaging().sendEachForMulticast(message);
        console.log(`✅ Sent welcome brief notification to user ${userId} (${response.successCount}/${fcmTokens.length} tokens successful)`);
      } catch (notificationError: any) {
        console.error(`Error sending welcome brief notification to user ${userId}:`, notificationError);
        // Don't fail - brief is still stored
      }
    } else {
      console.log(`User ${userId} has notifications disabled or no FCM token - welcome brief stored but no notification sent`);
    }
  } catch (error: any) {
    console.error(`Error sending welcome brief to user ${userId}:`, error);
    // Don't throw - this is a nice-to-have, not critical
  }
}

/**
 * Manual trigger for morning briefs (for testing/admin)
 * HTTP function that can be called directly via URL
 */
const triggerApp = express();
triggerApp.use(express.json());

triggerApp.get('/', async (req, res) => {
  console.log('🧪 Manually triggering morning briefs (GET request)');
  const startTime = Date.now();
  
  try {
    const result = await sendBriefsToAllUsers('morning');
    const duration = ((Date.now() - startTime) / 1000).toFixed(2);
    console.log(`✅ Manual morning briefs completed in ${duration}s`);
    console.log(`Result: ${JSON.stringify(result)}`);
    res.json({ 
      success: true, 
      duration: `${duration}s`,
      ...result 
    });
  } catch (error: any) {
    const duration = ((Date.now() - startTime) / 1000).toFixed(2);
    console.error(`❌ Manual morning briefs failed after ${duration}s:`, error);
    console.error(`Error details:`, error);
    res.status(500).json({ 
      success: false, 
      error: error.message || 'Unknown error',
      duration: `${duration}s`
    });
  }
});

triggerApp.post('/', async (req, res) => {
  console.log('🧪 Manually triggering morning briefs (POST request)');
  const startTime = Date.now();
  
  try {
    const result = await sendBriefsToAllUsers('morning');
    const duration = ((Date.now() - startTime) / 1000).toFixed(2);
    console.log(`✅ Manual morning briefs completed in ${duration}s`);
    console.log(`Result: ${JSON.stringify(result)}`);
    res.json({ 
      success: true, 
      duration: `${duration}s`,
      ...result 
    });
  } catch (error: any) {
    const duration = ((Date.now() - startTime) / 1000).toFixed(2);
    console.error(`❌ Manual morning briefs failed after ${duration}s:`, error);
    console.error(`Error details:`, error);
    res.status(500).json({ 
      success: false, 
      error: error.message || 'Unknown error',
      duration: `${duration}s`
    });
  }
});

export const triggerMorningBrief = functions
  .runWith({
    timeoutSeconds: 540, // 9 minutes
    memory: '512MB'
  })
  .region('us-central1')
  .https
  .onRequest(triggerApp);

/**
 * Firebase trigger: Send welcome brief when a new user is created
 */
export const onNewUserCreated = functions.firestore
  .document('users/{userId}')
  .onCreate(async (snapshot, context) => {
    const userId = context.params.userId;
    const userData = snapshot.data();
    
    // Only send welcome brief if this is a truly new user
    // Since this is onCreate, the document was just created, so we can safely send welcome brief
    // But we'll add a small delay check to ensure it's not a migration/import
    const startDate = userData?.startDate || userData?.createdAt;
    let startTime: number;
    
    if (startDate) {
      // Handle Firestore Timestamp
      if (startDate?.toMillis) {
        startTime = startDate.toMillis();
      } else if (startDate?.toDate) {
        startTime = startDate.toDate().getTime();
      } else if (typeof startDate === 'number') {
        startTime = startDate;
      } else {
        startTime = Date.now();
      }
    } else {
      // No startDate - assume it's new (will be set by the app)
      startTime = Date.now();
    }
    
    const timeSinceCreation = Date.now() - startTime;
    const oneHour = 60 * 60 * 1000;
    
    // Only send if account was created within the last hour (truly new)
    // This prevents sending welcome briefs for old accounts being migrated
    if (timeSinceCreation < oneHour && timeSinceCreation >= 0) {
      console.log(`🆕 New user detected: ${userId} (created ${Math.round(timeSinceCreation / 1000)}s ago) - sending welcome brief`);
      await sendWelcomeBriefToUser(userId);
    } else {
      console.log(`User ${userId} has old startDate (${Math.round(timeSinceCreation / (1000 * 60 * 60))}h ago) or invalid timestamp - skipping welcome brief`);
    }
  });

