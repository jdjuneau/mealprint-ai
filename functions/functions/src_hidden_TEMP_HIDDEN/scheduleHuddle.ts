import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import { Expo, ExpoPushMessage } from 'expo-server-sdk';

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();
const expo = new Expo();

interface Circle {
  id: string;
  name: string;
  members: string[];
  timezone?: string;
}

interface Huddle {
  circleId: string;
  scheduledAt: admin.firestore.Timestamp;
  status: 'scheduled' | 'active' | 'completed' | 'cancelled';
  roomId: string;
  participants: string[];
  recordingUrl?: string;
  transcript?: string;
  summary?: string;
  createdAt: admin.firestore.Timestamp;
}

/**
 * Scheduled function that runs every Sunday at 8 PM
 * Schedules huddles for all active circles
 */
export const scheduleWeeklyHuddles = functions.pubsub
  .schedule('0 20 * * 0') // Every Sunday at 8 PM UTC
  .timeZone('UTC')
  .onRun(async (context) => {
    console.log('📅 Starting weekly huddle scheduler at', new Date().toISOString());

    try {
      // Get all active circles
      const circlesSnapshot = await db.collection('circles').get();
      const circles: Circle[] = [];

      circlesSnapshot.forEach((doc) => {
        const circleData = doc.data();
        if (circleData.members && circleData.members.length > 0) {
          circles.push({
            id: doc.id,
            name: circleData.name || 'Circle',
            members: circleData.members || [],
            timezone: circleData.timezone || 'America/New_York',
          });
        }
      });

      console.log(`📊 Found ${circles.length} active circles`);

      let huddlesScheduled = 0;
      const now = new Date();

      // Schedule huddle for each circle at 8 PM in their timezone
      for (const circle of circles) {
        try {
          // Calculate 8 PM in circle's timezone
          const huddleTime = calculateLocalTime(circle.timezone || 'America/New_York', 20, 0); // 8 PM
          
          // If it's already past 8 PM today, schedule for next Sunday
          if (huddleTime < now) {
            huddleTime.setDate(huddleTime.getDate() + 7);
          }

          // Generate unique room ID
          const roomId = `huddle_${circle.id}_${Date.now()}`;

          // Create huddle document
          const huddle: Omit<Huddle, 'id'> = {
            circleId: circle.id,
            scheduledAt: admin.firestore.Timestamp.fromDate(huddleTime),
            status: 'scheduled',
            roomId,
            participants: [],
            createdAt: admin.firestore.Timestamp.now(),
          };

          const huddleRef = await db.collection('huddles').add(huddle);

          // Schedule notification 10 minutes before huddle
          const notificationTime = new Date(huddleTime);
          notificationTime.setMinutes(notificationTime.getMinutes() - 10);

          // Store notification task (will be triggered by another scheduled function)
          await db.collection('huddleNotifications').add({
            huddleId: huddleRef.id,
            circleId: circle.id,
            scheduledAt: admin.firestore.Timestamp.fromDate(notificationTime),
            sent: false,
            createdAt: admin.firestore.Timestamp.now(),
          });

          huddlesScheduled++;
          console.log(`✅ Scheduled huddle for circle ${circle.id} at ${huddleTime.toISOString()}`);
        } catch (error) {
          console.error(`❌ Error scheduling huddle for circle ${circle.id}:`, error);
        }
      }

      console.log(`✅ Weekly huddle scheduler completed. Scheduled ${huddlesScheduled} huddles`);

      return {
        success: true,
        huddlesScheduled,
        totalCircles: circles.length,
      };
    } catch (error) {
      console.error('❌ Error in weekly huddle scheduler:', error);
      throw error;
    }
  });

/**
 * Scheduled function that runs every minute to check for pending notifications
 */
export const sendHuddleNotifications = functions.pubsub
  .schedule('* * * * *') // Every minute
  .onRun(async (context) => {
    const now = admin.firestore.Timestamp.now();

    try {
      // Get pending notifications
      const notificationsSnapshot = await db
        .collection('huddleNotifications')
        .where('sent', '==', false)
        .where('scheduledAt', '<=', now)
        .limit(50)
        .get();

      if (notificationsSnapshot.empty) {
        return { success: true, notificationsSent: 0 };
      }

      let notificationsSent = 0;

      for (const notificationDoc of notificationsSnapshot.docs) {
        const notificationData = notificationDoc.data();
        const { huddleId, circleId } = notificationData;

        try {
          // Get huddle info
          const huddleDoc = await db.collection('huddles').doc(huddleId).get();
          if (!huddleDoc.exists) {
            await notificationDoc.ref.update({ sent: true, error: 'Huddle not found' });
            continue;
          }

          const huddle = huddleDoc.data() as Huddle;

          // Get circle info
          const circleDoc = await db.collection('circles').doc(circleId).get();
          if (!circleDoc.exists) {
            await notificationDoc.ref.update({ sent: true, error: 'Circle not found' });
            continue;
          }

          const circle = circleDoc.data() as Circle;
          const members = circle.members || [];

          // Get push tokens for all members
          const pushTokens: string[] = [];
          for (const memberId of members) {
            const memberDoc = await db.collection('users').doc(memberId).get();
            if (!memberDoc.exists) continue;

            const memberData = memberDoc.data();
            const pushToken = memberData?.expoPushToken;

            if (pushToken && Expo.isExpoPushToken(pushToken)) {
              pushTokens.push(pushToken);
            }
          }

          if (pushTokens.length === 0) {
            await notificationDoc.ref.update({ sent: true, error: 'No push tokens found' });
            continue;
          }

          // Send push notifications
          const messages: ExpoPushMessage[] = pushTokens.map((token) => ({
            to: token,
            sound: 'default',
            title: '🎤 Circle Huddle',
            body: 'Circle huddle in 10 min — join voice?',
            data: {
              type: 'huddle_reminder',
              huddleId,
              circleId,
              roomId: huddle.roomId,
              deepLink: `coachie://huddle/${huddle.roomId}`,
            },
            priority: 'high',
          }));

          await expo.sendPushNotificationsAsync(messages);
          await notificationDoc.ref.update({ sent: true, sentAt: admin.firestore.Timestamp.now() });

          notificationsSent++;
        } catch (error) {
          console.error(`Error sending notification for huddle ${huddleId}:`, error);
          await notificationDoc.ref.update({
            sent: true,
            error: error instanceof Error ? error.message : 'Unknown error',
          });
        }
      }

      return { success: true, notificationsSent };
    } catch (error) {
      console.error('Error in sendHuddleNotifications:', error);
      return { success: false, error: error instanceof Error ? error.message : 'Unknown error' };
    }
  });

/**
 * Helper function to calculate local time in a specific timezone
 */
function calculateLocalTime(timezone: string, hour: number, minute: number): Date {
  // This is a simplified version - in production, use a proper timezone library
  // For now, we'll use UTC and adjust based on common timezones
  const now = new Date();
  const utcHour = now.getUTCHours();
  
  // Simple timezone offset calculation (this should be replaced with proper timezone handling)
  const timezoneOffsets: Record<string, number> = {
    'America/New_York': -5, // EST
    'America/Chicago': -6, // CST
    'America/Denver': -7, // MST
    'America/Los_Angeles': -8, // PST
    'Europe/London': 0, // GMT
    'Europe/Paris': 1, // CET
    'Asia/Tokyo': 9, // JST
  };

  const offset = timezoneOffsets[timezone] || 0;
  const localHour = (utcHour + offset + 24) % 24;

  const targetDate = new Date();
  targetDate.setUTCHours(hour - offset, minute, 0, 0);
  
  // If it's Sunday, keep it; otherwise find next Sunday
  if (targetDate.getDay() !== 0) {
    const daysUntilSunday = (7 - targetDate.getDay()) % 7 || 7;
    targetDate.setDate(targetDate.getDate() + daysUntilSunday);
  }

  return targetDate;
}

/**
 * HTTP callable function to start a huddle
 */
export const startHuddle = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = context.auth.uid;
  const { huddleId } = data;

  if (!huddleId) {
    throw new functions.https.HttpsError('invalid-argument', 'huddleId is required');
  }

  try {
    const huddleRef = db.collection('huddles').doc(huddleId);
    const huddleDoc = await huddleRef.get();

    if (!huddleDoc.exists) {
      throw new functions.https.HttpsError('not-found', 'Huddle not found');
    }

    const huddle = huddleDoc.data() as Huddle;

    // Check if user is a member of the circle
    const circleDoc = await db.collection('circles').doc(huddle.circleId).get();
    if (!circleDoc.exists) {
      throw new functions.https.HttpsError('not-found', 'Circle not found');
    }

    const circle = circleDoc.data() as Circle;
    if (!circle.members.includes(userId)) {
      throw new functions.https.HttpsError('permission-denied', 'Not a member of this circle');
    }

    // Update huddle status
    const participants = huddle.participants || [];
    if (!participants.includes(userId)) {
      participants.push(userId);
    }

    await huddleRef.update({
      status: 'active',
      participants,
      startedAt: admin.firestore.Timestamp.now(),
    });

    return {
      success: true,
      roomId: huddle.roomId,
      huddleId,
    };
  } catch (error) {
    console.error('Error starting huddle:', error);
    if (error instanceof functions.https.HttpsError) {
      throw error;
    }
    throw new functions.https.HttpsError('internal', 'Failed to start huddle');
  }
});

/**
 * HTTP callable function to end a huddle and generate summary
 */
export const endHuddle = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = context.auth.uid;
  const { huddleId, transcript, recordingUrl } = data;

  if (!huddleId) {
    throw new functions.https.HttpsError('invalid-argument', 'huddleId is required');
  }

  try {
    const huddleRef = db.collection('huddles').doc(huddleId);
    const huddleDoc = await huddleRef.get();

    if (!huddleDoc.exists) {
      throw new functions.https.HttpsError('not-found', 'Huddle not found');
    }

    const huddle = huddleDoc.data() as Huddle;

    // Check if user is a member of the circle
    const circleDoc = await db.collection('circles').doc(huddle.circleId).get();
    if (!circleDoc.exists) {
      throw new functions.https.HttpsError('not-found', 'Circle not found');
    }

    const circle = circleDoc.data() as Circle;
    if (!circle.members.includes(userId)) {
      throw new functions.https.HttpsError('permission-denied', 'Not a member of this circle');
    }

    // Generate AI summary from transcript
    let summary = '';
    if (transcript) {
      // TODO: Call OpenAI to generate summary
      // For now, use a placeholder
      summary = 'Amazing — 3 new streaks, 2 PRs!';
    }

    // Update huddle
    await huddleRef.update({
      status: 'completed',
      transcript: transcript || undefined,
      recordingUrl: recordingUrl || undefined,
      summary,
      endedAt: admin.firestore.Timestamp.now(),
    });

    return {
      success: true,
      summary,
    };
  } catch (error) {
    console.error('Error ending huddle:', error);
    if (error instanceof functions.https.HttpsError) {
      throw error;
    }
    throw new functions.https.HttpsError('internal', 'Failed to end huddle');
  }
});

