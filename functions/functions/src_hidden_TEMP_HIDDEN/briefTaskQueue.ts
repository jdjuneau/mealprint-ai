/**
 * SCALABLE BRIEF SYSTEM USING CLOUD TASKS
 * 
 * This system scales to thousands of users by:
 * 1. Scheduler function enqueues tasks (one per user) to Cloud Tasks
 * 2. Worker function processes each task independently
 * 3. Cloud Tasks automatically distributes work across multiple instances
 * 4. No timeout issues - each user is processed in a separate function invocation
 */

import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import { CloudTasksClient } from '@google-cloud/tasks';

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();
const tasksClient = new CloudTasksClient();
const projectId = 'vanish-auth-real';
const location = 'us-central1';
const queueName = 'brief-generation-queue';

/**
 * Get the Cloud Tasks queue path
 */
function getQueuePath(): string {
  return tasksClient.queuePath(projectId, location, queueName);
}

/**
 * Enqueue brief generation tasks for all users
 * This is called by the scheduled function
 */
export async function enqueueBriefTasks(briefType: 'morning' | 'afternoon' | 'evening'): Promise<{ success: boolean; tasksEnqueued: number; error?: string }> {
  try {
    console.log(`📋 Starting to enqueue ${briefType} brief tasks...`);
    
    // Get all users
    const allUsersSnapshot = await db.collection('users').get();
    
    if (allUsersSnapshot.empty) {
      console.log(`No users found for ${briefType} briefs`);
      return { success: true, tasksEnqueued: 0 };
    }

    console.log(`Found ${allUsersSnapshot.size} total users - enqueueing tasks for all`);
    
    let queuePath: string;
    try {
      queuePath = getQueuePath();
    } catch (error: any) {
      console.error(`❌ Failed to get queue path: ${error.message}`);
      throw new Error(`Cloud Tasks queue not configured: ${error.message}`);
    }
    
    let tasksEnqueued = 0;
    const errors: string[] = [];

    // Enqueue a task for each user
    for (const userDoc of allUsersSnapshot.docs) {
      const userId = userDoc.id;
      const userData = userDoc.data();
      const userName = userData.name || userData.email || userId;

      try {
        // Create task payload
        const taskPayload = {
          userId,
          briefType,
          userName,
        };

        // Create Cloud Task
        const task = {
          httpRequest: {
            httpMethod: 'POST' as const,
            url: `https://us-central1-${projectId}.cloudfunctions.net/processBriefTask`,
            headers: {
              'Content-Type': 'application/json',
            },
            body: Buffer.from(JSON.stringify(taskPayload)).toString('base64'),
          },
        };

        // Enqueue the task
        const [response] = await tasksClient.createTask({
          parent: queuePath,
          task,
        });

        tasksEnqueued++;
        if (tasksEnqueued % 10 === 0) {
          console.log(`✅ Enqueued ${tasksEnqueued}/${allUsersSnapshot.size} tasks...`);
        }
      } catch (error: any) {
        const errorMsg = `Failed to enqueue task for user ${userName} (${userId}): ${error.message}`;
        console.error(`❌ ${errorMsg}`);
        errors.push(errorMsg);
      }
    }

    console.log(`✅ Successfully enqueued ${tasksEnqueued}/${allUsersSnapshot.size} tasks for ${briefType} briefs`);
    
    if (errors.length > 0) {
      console.warn(`⚠️ ${errors.length} errors occurred during enqueueing`);
    }

    return {
      success: true,
      tasksEnqueued,
      error: errors.length > 0 ? `${errors.length} errors: ${errors.slice(0, 3).join('; ')}` : undefined,
    };
  } catch (error: any) {
    console.error(`❌ Error enqueueing ${briefType} brief tasks:`, error);
    return {
      success: false,
      tasksEnqueued: 0,
      error: error.message || 'Unknown error',
    };
  }
}

/**
 * Worker function that processes a single brief task
 * This is called by Cloud Tasks for each user
 */
export const processBriefTask = functions
  .runWith({
    timeoutSeconds: 540, // 9 minutes per user (shouldn't need this much, but safe)
    memory: '512MB',
  })
  .region('us-central1')
  .https
  .onRequest(async (req, res) => {
    // Set CORS headers
    res.set('Access-Control-Allow-Origin', '*');
    res.set('Access-Control-Allow-Methods', 'POST, OPTIONS');
    res.set('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') {
      res.status(204).end();
      return;
    }

    if (req.method !== 'POST') {
      res.status(405).json({ error: 'Method not allowed' });
      return;
    }

    try {
      const { userId, briefType, userName } = req.body;

      if (!userId || !briefType) {
        res.status(400).json({ error: 'Missing userId or briefType' });
        return;
      }

      console.log(`🔄 Processing ${briefType} brief task for user ${userName || userId} (${userId})`);

      // Import generateBriefInternal
      const { generateBriefInternal } = require('../lib/generateBrief');
      const { getUserSubscriptionTier, SubscriptionTier } = require('./subscriptionVerification');

      // Check subscription tier - free users only get morning briefs
      const tier = await getUserSubscriptionTier(userId);
      const isPro = tier === SubscriptionTier.PRO;
      
      console.log(`📊 User ${userName || userId} (${userId}): tier=${tier}, isPro=${isPro}, briefType=${briefType}`);

      // Skip afternoon and evening briefs for free users
      if ((briefType === 'afternoon' || briefType === 'evening') && !isPro) {
        console.log(`⏭️ Skipping ${briefType} brief for free user ${userName || userId} (${userId}) - Pro only`);
        res.status(200).json({ success: true, skipped: true, reason: 'free_tier' });
        return;
      }
      
      // CRITICAL: Morning briefs should ALWAYS be generated for ALL users (free + pro)
      if (briefType === 'morning') {
        console.log(`✅ Processing morning brief for ${isPro ? 'PRO' : 'FREE'} user ${userName || userId} (${userId})`);
      }

      // Get user data
      const userDoc = await db.collection('users').doc(userId).get();
      if (!userDoc.exists) {
        console.error(`❌ User ${userId} not found in Firestore`);
        res.status(404).json({ error: 'User not found' });
        return;
      }

      const userData = userDoc.data()!;
      const fcmTokens = userData.fcmTokens || (userData.fcmToken ? [userData.fcmToken] : []);
      const nudgesEnabled = userData.nudgesEnabled !== false;
      
      console.log(`📋 User ${userName || userId} (${userId}): nudgesEnabled=${nudgesEnabled}, fcmTokens=${fcmTokens.length}`);

      // Generate brief
      let brief: string;
      let briefGenerated = false;

      try {
        console.log(`📝 Generating ${briefType} brief for ${isPro ? 'PRO' : 'FREE'} user ${userName || userId} (${userId})...`);
        const result = await generateBriefInternal(userId, briefType);
        brief = result?.brief || getFallbackBrief(briefType);
        briefGenerated = true;
        console.log(`✅ Generated ${briefType} brief for ${isPro ? 'PRO' : 'FREE'} user ${userName || userId} (${userId}) (length: ${brief.length})`);
      } catch (genError: any) {
        console.error(`❌ Error generating ${briefType} brief for ${isPro ? 'PRO' : 'FREE'} user ${userName || userId} (${userId}):`, genError);
        console.error(`   Error details: ${genError.message || 'Unknown error'}, stack: ${genError.stack || 'No stack trace'}`);
        brief = getFallbackBrief(briefType);
        briefGenerated = false;
        // CRITICAL: Still store the fallback brief so user sees something
        console.log(`⚠️ Using fallback brief for ${userName || userId} (${userId}) due to generation error`);
      }

      // Store brief in Firestore - CRITICAL: Always store, even if generation failed (fallback brief)
      const today = new Date().toISOString().split('T')[0];
      const briefDocId = `${briefType}_${today}`;
      
      try {
        await db
          .collection('users')
          .doc(userId)
          .collection('briefs')
          .doc(briefDocId)
          .set({
            brief,
            type: briefType,
            date: today,
            generated: briefGenerated,
            tier: isPro ? 'PRO' : 'FREE', // Store tier for debugging
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
          });

        console.log(`💾 Stored ${briefType} brief for ${isPro ? 'PRO' : 'FREE'} user ${userName || userId} (${userId}) (date: ${today}, generated: ${briefGenerated})`);
      } catch (storeError: any) {
        console.error(`❌ CRITICAL: Failed to store brief for ${userName || userId} (${userId}):`, storeError);
        res.status(500).json({ 
          error: 'Failed to store brief', 
          details: storeError.message,
          userId,
          briefType 
        });
        return;
      }

      // Send notification if enabled and tokens exist
      let notificationSent = false;
      if (nudgesEnabled && fcmTokens.length > 0) {
        try {
          const messaging = admin.messaging();
          const messages = fcmTokens.map((token: string) => ({
            token,
            notification: {
              title: getBriefTitle(briefType),
              body: brief.substring(0, 100) + (brief.length > 100 ? '...' : ''),
            },
            data: {
              type: 'brief',
              briefType,
              date: today,
            },
          }));

          const responses = await messaging.sendAll(messages);
          const successCount = responses.responses.filter(r => r.success).length;
          notificationSent = successCount > 0;

          console.log(`📤 Sent ${briefType} brief notification to user ${userName || userId} (${userId}) (${successCount}/${fcmTokens.length} tokens successful)`);
        } catch (notifError: any) {
          console.error(`⚠️ Error sending notification to user ${userName || userId} (${userId}):`, notifError);
        }
      } else {
        console.log(`ℹ️ User ${userName || userId} (${userId}) has no FCM token(s) or notifications disabled - brief stored but no notification sent`);
      }

      res.status(200).json({
        success: true,
        userId,
        briefStored: true,
        notificationSent,
        briefGenerated,
      });
    } catch (error: any) {
      console.error(`❌ Error processing brief task:`, error);
      res.status(500).json({ error: error.message || 'Internal server error' });
    }
  });

/**
 * Fallback brief if generation fails
 */
function getFallbackBrief(briefType: 'morning' | 'afternoon' | 'evening'): string {
  const briefs = {
    morning: "Good morning! Here's your daily health brief. Check your app for personalized insights and recommendations.",
    afternoon: "Good afternoon! Here's your midday health brief with tips to keep you on track.",
    evening: "Good evening! Here's your evening health brief with a summary of your day and tomorrow's focus.",
  };
  return briefs[briefType];
}

/**
 * Get brief title for notifications
 */
function getBriefTitle(briefType: 'morning' | 'afternoon' | 'evening'): string {
  const titles = {
    morning: '🌅 Morning Brief',
    afternoon: '☀️ Afternoon Brief',
    evening: '🌙 Evening Brief',
  };
  return titles[briefType];
}
