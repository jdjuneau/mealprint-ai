import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import { Expo, ExpoPushMessage } from 'expo-server-sdk';

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();
const expo = new Expo();

interface CircleWin {
  uid: string;
  type: 'habit' | 'streak' | 'goal';
  message: string;
  habitId?: string;
  habitTitle?: string;
  streakCount?: number;
  goalTitle?: string;
  timestamp: admin.firestore.Timestamp;
  reactions?: {
    fire: string[];
    heart: string[];
    hug: string[];
    highFive: string[];
  };
}

/**
 * Triggered when a habit completion is created
 * Checks if it's a 7-day streak milestone and posts to circle wins
 */
export const onHabitCompletionForWins = functions.firestore
  .document('users/{userId}/completions/{completionId}')
  .onCreate(async (snapshot, context) => {
    const { userId } = context.params;
    const completionData = snapshot.data();

    try {
      const habitId = completionData.habitId;
      if (!habitId) return null;

      // Get habit info
      const habitDoc = await db
        .collection('users')
        .doc(userId)
        .collection('habits')
        .doc(habitId)
        .get();

      if (!habitDoc.exists) return null;

      const habit = habitDoc.data();
      const habitTitle = habit.title || 'habit';
      const streakCount = habit.streakCount || 0;

      // Check if this is a 7-day milestone (or multiple of 7)
      if (streakCount > 0 && streakCount % 7 === 0) {
        // Get user's circles
        const userDoc = await db.collection('users').doc(userId).get();
        if (!userDoc.exists) return null;

        const userData = userDoc.data();
        const circleIds = userData?.circles || [];
        const userName = userData?.name || 'Someone';

        // Post win to each circle
        for (const circleId of circleIds) {
          const winMessage = `${userName} hit ${streakCount}-day ${habitTitle} streak! 🔥`;

          const win: Omit<CircleWin, 'id'> = {
            uid: userId,
            type: 'streak',
            message: winMessage,
            habitId,
            habitTitle,
            streakCount,
            timestamp: admin.firestore.FieldValue.serverTimestamp() as admin.firestore.Timestamp,
            reactions: {
              fire: [],
              heart: [],
              hug: [],
              highFive: [],
            },
          };

          await db.collection('circles').doc(circleId).collection('wins').add(win);

          // Send push notification to other circle members
          await notifyCircleMembers(circleId, userId, winMessage);
        }
      }

      return null;
    } catch (error) {
      console.error('Error in onHabitCompletionForWins:', error);
      return null;
    }
  });

/**
 * Triggered when a user achieves a goal
 * Posts to circle wins
 */
export const onGoalAchievement = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = context.auth.uid;
  const { goalTitle, goalType } = data;

  if (!goalTitle) {
    throw new functions.https.HttpsError('invalid-argument', 'goalTitle is required');
  }

  try {
    // Get user's circles
    const userDoc = await db.collection('users').doc(userId).get();
    if (!userDoc.exists) {
      throw new functions.https.HttpsError('not-found', 'User not found');
    }

    const userData = userDoc.data();
    const circleIds = userData?.circles || [];
    const userName = userData?.name || 'Someone';

    // Create win message based on goal type
    let winMessage = '';
    if (goalType === 'run_5k' || goalTitle.toLowerCase().includes('5k')) {
      winMessage = `${userName} ran their first 5K! 🏃‍♀️`;
    } else if (goalType === 'weight_loss') {
      winMessage = `${userName} hit their weight loss goal! 🎯`;
    } else {
      winMessage = `${userName} achieved: ${goalTitle}! 🎉`;
    }

    // Post win to each circle
    for (const circleId of circleIds) {
      const win: Omit<CircleWin, 'id'> = {
        uid: userId,
        type: 'goal',
        message: winMessage,
        goalTitle,
        timestamp: admin.firestore.FieldValue.serverTimestamp() as admin.firestore.Timestamp,
        reactions: {
          fire: [],
          heart: [],
          hug: [],
          highFive: [],
        },
      };

      await db.collection('circles').doc(circleId).collection('wins').add(win);

      // Send push notification to other circle members
      await notifyCircleMembers(circleId, userId, winMessage);
    }

    return { success: true };
  } catch (error) {
    console.error('Error in onGoalAchievement:', error);
    throw new functions.https.HttpsError('internal', 'Failed to post goal achievement');
  }
});

/**
 * Helper function to notify circle members about a new win
 */
async function notifyCircleMembers(
  circleId: string,
  winUserId: string,
  message: string
): Promise<void> {
  try {
    const circleDoc = await db.collection('circles').doc(circleId).get();
    if (!circleDoc.exists) return;

    const circle = circleDoc.data();
    const members = circle?.members || [];

    // Get push tokens for all members except the one who posted
    const pushTokens: string[] = [];
    for (const memberId of members) {
      if (memberId === winUserId) continue;

      const memberDoc = await db.collection('users').doc(memberId).get();
      if (!memberDoc.exists) continue;

      const memberData = memberDoc.data();
      const pushToken = memberData?.expoPushToken;

      if (pushToken && Expo.isExpoPushToken(pushToken)) {
        pushTokens.push(pushToken);
      }
    }

    if (pushTokens.length === 0) return;

    const messages: ExpoPushMessage[] = pushTokens.map((token) => ({
      to: token,
      sound: 'default',
      title: '🎉 Circle Win!',
      body: message,
      data: {
        type: 'circle_win',
        circleId,
        deepLink: `coachie://circle_detail/${circleId}`,
      },
    }));

    await expo.sendPushNotificationsAsync(messages);
  } catch (error) {
    console.error('Error notifying circle members:', error);
  }
}

