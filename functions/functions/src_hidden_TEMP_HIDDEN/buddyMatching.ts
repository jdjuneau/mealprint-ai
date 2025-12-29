import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import { Expo, ExpoPushMessage } from 'expo-server-sdk';

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();
const expo = new Expo();

interface UserProfile {
  name?: string;
  fourTendencies?: {
    tendency: string;
  };
  habitGoals?: Record<string, any>;
  timezone?: string;
  buddyUid?: string;
  expoPushToken?: string;
}

interface Habit {
  id: string;
  title: string;
  streakCount?: number;
}

/**
 * HTTP callable function to find buddy matches
 * Matches users by: goal, tendency, streak, timezone
 */
export const findBuddyMatches = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = context.auth.uid;

  try {
    // Get current user's profile
    const userDoc = await db.collection('users').doc(userId).get();
    if (!userDoc.exists) {
      throw new functions.https.HttpsError('not-found', 'User profile not found');
    }

    const userData = userDoc.data() as UserProfile;

    // Skip if user already has a buddy
    if (userData.buddyUid) {
      return { matches: [] };
    }

    // Get user's goal
    const userGoal = userData.habitGoals?.primaryGoal || 
                     userData.habitGoals?.selectedGoal || 
                     'Improve fitness';
    const userTendency = userData.fourTendencies?.tendency;
    const userTimezone = userData.timezone || 'America/New_York';

    // Get user's current streak (from habits)
    const habitsSnapshot = await db
      .collection('users')
      .doc(userId)
      .collection('habits')
      .where('isActive', '==', true)
      .get();

    let userStreak = 0;
    habitsSnapshot.forEach((doc) => {
      const habit = doc.data() as Habit;
      if (habit.streakCount && habit.streakCount > userStreak) {
        userStreak = habit.streakCount;
      }
    });

    // Find potential matches
    const allUsersSnapshot = await db.collection('users').get();
    const matches: Array<{
      userId: string;
      name: string;
      goal: string;
      streak: number;
      tendency?: string;
      timezone?: string;
      matchScore: number;
    }> = [];

    for (const doc of allUsersSnapshot.docs) {
      const otherUserId = doc.id;
      
      // Skip self
      if (otherUserId === userId) continue;

      const otherData = doc.data() as UserProfile;

      // Skip if they already have a buddy
      if (otherData.buddyUid) continue;

      // Get other user's goal
      const otherGoal = otherData.habitGoals?.primaryGoal || 
                        otherData.habitGoals?.selectedGoal || 
                        'Improve fitness';
      const otherTendency = otherData.fourTendencies?.tendency;
      const otherTimezone = otherData.timezone || 'America/New_York';

      // Get other user's streak
      const otherHabitsSnapshot = await db
        .collection('users')
        .doc(otherUserId)
        .collection('habits')
        .where('isActive', '==', true)
        .get();

      let otherStreak = 0;
      otherHabitsSnapshot.forEach((habitDoc) => {
        const habit = habitDoc.data() as Habit;
        if (habit.streakCount && habit.streakCount > otherStreak) {
          otherStreak = habit.streakCount;
        }
      });

      // Calculate match score (0-100)
      let matchScore = 0;

      // Goal match (40 points)
      if (otherGoal.toLowerCase() === userGoal.toLowerCase()) {
        matchScore += 40;
      } else if (otherGoal.toLowerCase().includes(userGoal.toLowerCase()) || 
                 userGoal.toLowerCase().includes(otherGoal.toLowerCase())) {
        matchScore += 20;
      }

      // Tendency match (30 points)
      if (userTendency && otherTendency && userTendency === otherTendency) {
        matchScore += 30;
      }

      // Streak similarity (20 points) - closer streaks = better match
      const streakDiff = Math.abs(userStreak - otherStreak);
      if (streakDiff === 0) {
        matchScore += 20;
      } else if (streakDiff <= 5) {
        matchScore += 15;
      } else if (streakDiff <= 10) {
        matchScore += 10;
      }

      // Timezone match (10 points)
      if (userTimezone === otherTimezone) {
        matchScore += 10;
      }

      // Only include matches with score >= 50
      if (matchScore >= 50) {
        matches.push({
          userId: otherUserId,
          name: otherData.name || 'User',
          goal: otherGoal,
          streak: otherStreak,
          tendency: otherTendency,
          timezone: otherTimezone,
          matchScore,
        });
      }
    }

    // Sort by match score (highest first)
    matches.sort((a, b) => b.matchScore - a.matchScore);

    // Return top 5 matches
    return { matches: matches.slice(0, 5) };
  } catch (error) {
    console.error('Error finding buddy matches:', error);
    throw new functions.https.HttpsError('internal', 'Failed to find matches');
  }
});

/**
 * Triggered when a habit is completed
 * Sends push notification to buddy if both completed the same habit
 */
export const onHabitCompletion = functions.firestore
  .document('users/{userId}/completions/{completionId}')
  .onCreate(async (snapshot, context) => {
    const { userId } = context.params;
    const completionData = snapshot.data();

    try {
      // Get user's profile
      const userDoc = await db.collection('users').doc(userId).get();
      if (!userDoc.exists) return null;

      const userData = userDoc.data() as UserProfile;
      const buddyUid = userData.buddyUid;

      if (!buddyUid) return null; // No buddy, no notification

      // Get habit info
      const habitId = completionData.habitId;
      const habitDoc = await db
        .collection('users')
        .doc(userId)
        .collection('habits')
        .doc(habitId)
        .get();

      if (!habitDoc.exists) return null;

      const habit = habitDoc.data() as Habit;
      const habitTitle = habit.title;

      // Check if buddy also completed this habit today
      const today = new Date();
      today.setHours(0, 0, 0, 0);
      const tomorrow = new Date(today);
      tomorrow.setDate(tomorrow.getDate() + 1);

      const buddyCompletionsSnapshot = await db
        .collection('users')
        .doc(buddyUid)
        .collection('completions')
        .where('habitId', '==', habitId)
        .where('completedAt', '>=', admin.firestore.Timestamp.fromDate(today))
        .where('completedAt', '<', admin.firestore.Timestamp.fromDate(tomorrow))
        .get();

      // Get buddy's profile
      const buddyDoc = await db.collection('users').doc(buddyUid).get();
      if (!buddyDoc.exists) return null;

      const buddyData = buddyDoc.data() as UserProfile;
      const buddyName = buddyData.name || 'Your buddy';

      if (buddyCompletionsSnapshot.empty) {
        // Buddy hasn't completed yet - send encouragement
        if (buddyData.expoPushToken && Expo.isExpoPushToken(buddyData.expoPushToken)) {
          const message: ExpoPushMessage = {
            to: buddyData.expoPushToken,
            sound: 'default',
            title: '💪 Your turn!',
            body: `${userData.name || 'Your buddy'} just crushed ${habitTitle} — your turn?`,
            data: {
              type: 'buddy_activity',
              habitId,
              habitTitle,
              deepLink: 'coachie://habits',
            },
          };

          await expo.sendPushNotificationsAsync([message]);
        }
      } else {
        // Both completed! Send celebration to both
        const celebrationMessage = `We both hit ${habitTitle} today! 🎉`;

        const messages: ExpoPushMessage[] = [];

        if (buddyData.expoPushToken && Expo.isExpoPushToken(buddyData.expoPushToken)) {
          messages.push({
            to: buddyData.expoPushToken,
            sound: 'default',
            title: '🎉 Mutual Win!',
            body: celebrationMessage,
            data: {
              type: 'buddy_celebration',
              habitId,
              habitTitle,
              deepLink: 'coachie://buddy_dashboard',
            },
          });
        }

        if (userData.expoPushToken && Expo.isExpoPushToken(userData.expoPushToken)) {
          messages.push({
            to: userData.expoPushToken,
            sound: 'default',
            title: '🎉 Mutual Win!',
            body: celebrationMessage,
            data: {
              type: 'buddy_celebration',
              habitId,
              habitTitle,
              deepLink: 'coachie://buddy_dashboard',
            },
          });
        }

        if (messages.length > 0) {
          await expo.sendPushNotificationsAsync(messages);
        }
      }

      return null;
    } catch (error) {
      console.error('Error in onHabitCompletion:', error);
      return null;
    }
  });

