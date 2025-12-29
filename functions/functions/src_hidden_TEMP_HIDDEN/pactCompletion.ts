import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import { Expo, ExpoPushMessage } from 'expo-server-sdk';

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();
const expo = new Expo();

/**
 * Triggered when a habit is completed in a pact circle
 * Checks if the 7-day pact is complete and awards badges
 */
export const onPactHabitCompletion = functions.firestore
  .document('users/{userId}/completions/{completionId}')
  .onCreate(async (snapshot, context) => {
    const { userId } = context.params;
    const completionData = snapshot.data();

    try {
      const habitId = completionData.habitId;
      if (!habitId) return null;

      // Get user's circles
      const userDoc = await db.collection('users').doc(userId).get();
      if (!userDoc.exists) return null;

      const userData = userDoc.data();
      const circleIds = userData?.circles || [];

      // Check each circle to see if it's a pact
      for (const circleId of circleIds) {
        const circleDoc = await db.collection('circles').doc(circleId).get();
        if (!circleDoc.exists) continue;

        const circle = circleDoc.data();
        if (!circle?.isPact || circle.pactHabitId !== habitId) continue;

        // Check if pact is complete (7 days of completions)
        const pactStartDate = circle.pactStartDate?.toDate();
        const pactEndDate = circle.pactEndDate?.toDate();
        const now = new Date();

        if (!pactStartDate || !pactEndDate) continue;

        // Check if we're past the end date
        if (now < pactEndDate) continue;

        // Count completions for both users in the pact period
        const members = circle.members || [];
        if (members.length !== 2) continue;

        const [user1Id, user2Id] = members;
        const completions1 = await countCompletionsInPeriod(
          user1Id,
          habitId,
          pactStartDate,
          pactEndDate
        );
        const completions2 = await countCompletionsInPeriod(
          user2Id,
          habitId,
          pactStartDate,
          pactEndDate
        );

        // Check if both users completed at least 7 days
        const daysRequired = 7;
        const user1Completed = completions1 >= daysRequired;
        const user2Completed = completions2 >= daysRequired;

        // Check if we've already processed this pact
        const pactProposalQuery = await db
          .collection('pactProposals')
          .where('circleId', '==', circleId)
          .where('status', '!=', 'completed')
          .limit(1)
          .get();

        if (pactProposalQuery.empty) continue;

        const proposalDoc = pactProposalQuery.docs[0];
        const proposal = proposalDoc.data();

        // Award badges and send notifications
        if (user1Completed && user2Completed) {
          // Both completed - mark pact as completed
          await proposalDoc.ref.update({ status: 'completed' });

          // Award badges to both users
          await Promise.all([
            awardPactBadge(user1Id, circle.pactHabitTitle || 'habit'),
            awardPactBadge(user2Id, circle.pactHabitTitle || 'habit'),
            sendPactCompletionNotification(user1Id, user2Id, circle.pactHabitTitle || 'habit'),
            sendPactCompletionNotification(user2Id, user1Id, circle.pactHabitTitle || 'habit'),
          ]);
        } else if (user1Completed || user2Completed) {
          // One completed - send encouragement
          const completedUserId = user1Completed ? user1Id : user2Id;
          const otherUserId = user1Completed ? user2Id : user1Id;
          await sendPactProgressNotification(completedUserId, otherUserId, circle.pactHabitTitle || 'habit');
        }
      }

      return null;
    } catch (error) {
      console.error('Error in onPactHabitCompletion:', error);
      return null;
    }
  });

/**
 * Count habit completions in a date range
 */
async function countCompletionsInPeriod(
  userId: string,
  habitId: string,
  startDate: Date,
  endDate: Date
): Promise<number> {
  try {
    const startTimestamp = admin.firestore.Timestamp.fromDate(startDate);
    const endTimestamp = admin.firestore.Timestamp.fromDate(endDate);

    const completionsSnapshot = await db
      .collection('users')
      .doc(userId)
      .collection('completions')
      .where('habitId', '==', habitId)
      .where('completedAt', '>=', startTimestamp)
      .where('completedAt', '<=', endTimestamp)
      .get();

    // Get unique dates
    const uniqueDates = new Set<string>();
    completionsSnapshot.docs.forEach((doc) => {
      const completedAt = doc.data().completedAt?.toDate();
      if (completedAt) {
        const dateStr = completedAt.toISOString().split('T')[0];
        uniqueDates.add(dateStr);
      }
    });

    return uniqueDates.size;
  } catch (error) {
    console.error(`Error counting completions for user ${userId}:`, error);
    return 0;
  }
}

/**
 * Award a pact completion badge to a user
 */
async function awardPactBadge(userId: string, habitTitle: string): Promise<void> {
  try {
    const badgeData = {
      type: 'pact_completion',
      title: `${habitTitle} Pact Champion`,
      description: `Completed 7-day pact for ${habitTitle}`,
      icon: '🤝',
      earnedAt: admin.firestore.FieldValue.serverTimestamp(),
      habitTitle,
    };

    await db.collection('users').doc(userId).collection('badges').add(badgeData);
    console.log(`✅ Awarded pact badge to user ${userId}`);
  } catch (error) {
    console.error(`Error awarding badge to user ${userId}:`, error);
  }
}

/**
 * Send notification when pact is completed
 */
async function sendPactCompletionNotification(
  userId: string,
  otherUserName: string,
  habitTitle: string
): Promise<void> {
  try {
    const userDoc = await db.collection('users').doc(userId).get();
    if (!userDoc.exists) return;

    const userData = userDoc.data();
    if (!userData?.expoPushToken) return;

    const pushToken = userData.expoPushToken;
    if (!Expo.isExpoPushToken(pushToken)) return;

    const notification: ExpoPushMessage = {
      to: pushToken,
      sound: 'default',
      title: '🎉 Pact Complete!',
      body: `You did it! Pact complete — here's your badge! You and ${otherUserName} completed the ${habitTitle} pact!`,
      data: {
        type: 'pact_completed',
        deepLink: 'coachie://badges',
      },
      priority: 'default',
    };

    await expo.sendPushNotificationsAsync([notification]);

    // Also create a DM
    await db.collection('chats').doc(userId).collection('messages').add({
      id: admin.firestore().collection('chats').doc().id,
      userId,
      content: `You did it! Pact complete — here's your badge! You and ${otherUserName} completed the ${habitTitle} pact! 🎉`,
      isUser: false,
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
      messageType: 'pact_completion',
    });
  } catch (error) {
    console.error(`Error sending pact completion notification to user ${userId}:`, error);
  }
}

/**
 * Send notification about pact progress
 */
async function sendPactProgressNotification(
  completedUserId: string,
  otherUserId: string,
  habitTitle: string
): Promise<void> {
  try {
    const otherUserDoc = await db.collection('users').doc(otherUserId).get();
    if (!otherUserDoc.exists) return;

    const otherUserData = otherUserDoc.data();
    if (!otherUserData?.expoPushToken) return;

    const pushToken = otherUserData.expoPushToken;
    if (!Expo.isExpoPushToken(pushToken)) return;

    const completedUserDoc = await db.collection('users').doc(completedUserId).get();
    const completedUserName = completedUserDoc.data()?.name || 'Your partner';

    const notification: ExpoPushMessage = {
      to: pushToken,
      sound: 'default',
      title: '💪 Keep Going!',
      body: `${completedUserName} completed their ${habitTitle} today! You've got this!`,
      data: {
        type: 'pact_progress',
        deepLink: 'coachie://habits',
      },
      priority: 'default',
    };

    await expo.sendPushNotificationsAsync([notification]);
  } catch (error) {
    console.error(`Error sending pact progress notification:`, error);
  }
}

