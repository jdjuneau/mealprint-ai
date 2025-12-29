import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import { Expo, ExpoPushMessage, ExpoPushToken } from 'expo-server-sdk';

// Initialize Firebase Admin (will be initialized in main index.js)
const db = admin.firestore();

// Initialize Expo for push notifications
const expo = new Expo();

// =============================================================================
// PUSH NOTIFICATION COPY - REPLACE WITH YOUR ACTUAL COPY
// =============================================================================

// Tier 1: Gentle reminder (miss_streak == 1)
const TIER_1_COPY = {
  title: "💙 Thinking of you",
  body: "We noticed you might have missed your habit today. No worries - tomorrow is a fresh start!",
  data: {
    type: "habit_followup",
    tier: 1,
    action: "gentle_reminder"
  }
};

// Tier 2: Quick reply encouragement (miss_streak == 3)
const TIER_2_COPY = {
  title: "🔄 Ready to bounce back?",
  body: "It's been a few days - what's one small thing you can do today to get back on track?",
  data: {
    type: "habit_followup",
    tier: 2,
    action: "quick_reply",
    reply_options: ["I'm ready!", "Need motivation", "Tell me more"]
  }
};

// Tier 3: Habit autopsy deep link (miss_streak >= 5)
const TIER_3_COPY = {
  title: "🔍 Let's figure this out together",
  body: "It's been a week - let's chat about what's been going on with your habit journey.",
  data: {
    type: "habit_followup",
    tier: 3,
    action: "habit_autopsy",
    screen: "CoachChat",
    deep_link: "coachie://chat/habit-autopsy"
  }
};

// =============================================================================
// MAIN SCHEDULED FUNCTION
// =============================================================================
export const habitFollowUpScheduler = functions.pubsub
  .schedule('0 20 * * *') // 8 PM UTC daily (adjust timezone as needed)
  .timeZone('UTC')
  .onRun(async (context) => {
    console.log('🔔 Starting habit follow-up scheduler at', new Date().toISOString());

    try {
      // Get all users who have habits
      const usersWithHabits = await getUsersWithHabits();
      console.log(`📊 Found ${usersWithHabits.length} users with habits to check`);

      let totalMissedHabits = 0;
      let tier1Sent = 0;
      let tier2Sent = 0;
      let tier3Sent = 0;

      // Process each user
      for (const userId of usersWithHabits) {
        console.log(`👤 Processing user: ${userId}`);

        try {
          const userResults = await processUserHabits(userId);
          totalMissedHabits += userResults.missedHabits;
          tier1Sent += userResults.tier1Sent;
          tier2Sent += userResults.tier2Sent;
          tier3Sent += userResults.tier3Sent;
        } catch (error) {
          console.error(`❌ Error processing user ${userId}:`, error);
        }
      }

      console.log('✅ Habit follow-up scheduler completed:', {
        totalMissedHabits,
        tier1Sent,
        tier2Sent,
        tier3Sent,
        totalUsersProcessed: usersWithHabits.length
      });

      return {
        success: true,
        totalMissedHabits,
        notificationsSent: {
          tier1: tier1Sent,
          tier2: tier2Sent,
          tier3: tier3Sent
        },
        usersProcessed: usersWithHabits.length
      };

    } catch (error) {
      console.error('💥 Critical error in habit follow-up scheduler:', error);
      throw new functions.https.HttpsError('internal', 'Failed to run habit follow-ups');
    }
  });

// =============================================================================
// USER PROCESSING LOGIC
// =============================================================================
async function processUserHabits(userId: string): Promise<{
  missedHabits: number;
  tier1Sent: number;
  tier2Sent: number;
  tier3Sent: number;
}> {
  let missedHabits = 0;
  let tier1Sent = 0;
  let tier2Sent = 0;
  let tier3Sent = 0;

  try {
    // Get today's habits that were created but not completed
    const missedHabitsData = await getMissedHabitsForUser(userId);

    for (const missedHabit of missedHabitsData) {
      missedHabits++;

      // Increment miss_streak
      const newMissStreak = await incrementMissStreak(userId, missedHabit.id);

      console.log(`📈 Habit ${missedHabit.id} miss_streak now: ${newMissStreak}`);

      // Determine notification tier and send
      const notificationSent = await sendTieredNotification(userId, missedHabit, newMissStreak);

      if (notificationSent) {
        if (newMissStreak === 1) tier1Sent++;
        else if (newMissStreak === 3) tier2Sent++;
        else if (newMissStreak >= 5) tier3Sent++;
      }
    }

  } catch (error) {
    console.error(`Error processing habits for user ${userId}:`, error);
  }

  return { missedHabits, tier1Sent, tier2Sent, tier3Sent };
}

// =============================================================================
// DATA FETCHING FUNCTIONS
// =============================================================================
async function getUsersWithHabits(): Promise<string[]> {
  try {
    // Get users who have active habits
    const habitsSnapshot = await db.collectionGroup('habits')
      .where('isActive', '==', true)
      .get();

    // Extract unique user IDs
    const userIds = new Set<string>();
    habitsSnapshot.forEach(doc => {
      // Extract userId from document path: users/{userId}/habits/{habitId}
      const pathParts = doc.ref.path.split('/');
      if (pathParts.length >= 2) {
        userIds.add(pathParts[1]); // userId is at index 1
      }
    });

    return Array.from(userIds);
  } catch (error) {
    console.error('Error getting users with habits:', error);
    return [];
  }
}

async function getMissedHabitsForUser(userId: string): Promise<any[]> {
  try {
    // Get today's date range
    const today = new Date();
    const startOfDay = new Date(today.getFullYear(), today.getMonth(), today.getDate());
    const endOfDay = new Date(today.getFullYear(), today.getMonth(), today.getDate() + 1);

    // Get habits created today
    const habitsQuery = db.collection('users').doc(userId).collection('habits')
      .where('isActive', '==', true)
      .where('createdAt', '>=', admin.firestore.Timestamp.fromDate(startOfDay))
      .where('createdAt', '<', admin.firestore.Timestamp.fromDate(endOfDay));

    const habitsSnapshot = await habitsQuery.get();

    if (habitsSnapshot.empty) {
      return [];
    }

    const missedHabits = [];

    // Check each habit for completion today
    for (const habitDoc of habitsSnapshot.docs) {
      const habitData = habitDoc.data();
      const habitId = habitDoc.id;

      // Check if there's a completion record for today
      const completionQuery = db.collection('users').doc(userId).collection('completions')
        .where('habitId', '==', habitId)
        .where('completedAt', '>=', admin.firestore.Timestamp.fromDate(startOfDay))
        .where('completedAt', '<', admin.firestore.Timestamp.fromDate(endOfDay))
        .limit(1);

      const completionSnapshot = await completionQuery.get();

      // If no completion found, this habit was missed
      if (completionSnapshot.empty) {
        missedHabits.push({
          id: habitId,
          ...habitData,
          title: habitData.title || 'Unnamed Habit'
        });
      }
    }

    console.log(`User ${userId} has ${missedHabits.length} missed habits today`);
    return missedHabits;

  } catch (error) {
    console.error(`Error getting missed habits for user ${userId}:`, error);
    return [];
  }
}

// =============================================================================
// MISS STREAK MANAGEMENT
// =============================================================================
async function incrementMissStreak(userId: string, habitId: string): Promise<number> {
  try {
    const habitRef = db.collection('users').doc(userId).collection('habits').doc(habitId);

    // Get current habit data
    const habitDoc = await habitRef.get();
    if (!habitDoc.exists) {
      console.error(`Habit ${habitId} not found for user ${userId}`);
      return 0;
    }

    const habitData = habitDoc.data();
    const currentMissStreak = habitData?.missStreak || 0;
    const newMissStreak = currentMissStreak + 1;

    // Update the miss_streak
    await habitRef.update({
      missStreak: newMissStreak,
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    return newMissStreak;

  } catch (error) {
    console.error(`Error incrementing miss streak for habit ${habitId}:`, error);
    return 0;
  }
}

// =============================================================================
// TIERED NOTIFICATION SYSTEM
// =============================================================================
async function sendTieredNotification(
  userId: string,
  habit: any,
  missStreak: number
): Promise<boolean> {
  try {
    // Get user's push token
    const userDoc = await db.collection('users').doc(userId).get();
    const userData = userDoc.data();

    if (!userData?.expoPushToken) {
      console.log(`No push token found for user ${userId}, skipping notification`);
      return false;
    }

    const pushToken = userData.expoPushToken;

    // Validate token
    if (!Expo.isExpoPushToken(pushToken)) {
      console.error(`Invalid Expo push token for user ${userId}:`, pushToken);
      return false;
    }

    // Determine notification tier and content
    let notificationContent;
    let shouldSend = false;

    if (missStreak === 1) {
      notificationContent = TIER_1_COPY;
      shouldSend = true;
    } else if (missStreak === 3) {
      notificationContent = TIER_2_COPY;
      shouldSend = true;
    } else if (missStreak >= 5) {
      notificationContent = TIER_3_COPY;
      shouldSend = true;
    }

    if (!shouldSend || !notificationContent) {
      console.log(`No notification needed for user ${userId}, habit ${habit.id}, miss_streak ${missStreak}`);
      return false;
    }

    // Create personalized message
    const personalizedMessage = personalizeMessage(notificationContent, habit, missStreak);

    // Create notification
    const message: ExpoPushMessage = {
      to: pushToken,
      title: personalizedMessage.title,
      body: personalizedMessage.body,
      data: {
        ...personalizedMessage.data,
        habitId: habit.id,
        habitTitle: habit.title,
        missStreak: missStreak,
        timestamp: new Date().toISOString()
      },
      sound: 'default',
      priority: 'default',
      ttl: 86400, // 24 hours
      expiration: Math.floor(Date.now() / 1000) + 86400,
    };

    // Send notification
    const ticket = await expo.sendPushNotificationsAsync([message]);

    const success = ticket[0]?.status === 'ok';
    if (success) {
      console.log(`✅ Sent tier ${getTierFromStreak(missStreak)} notification to user ${userId} for habit "${habit.title}"`);

      // Log the notification
      await logNotificationSent(userId, habit.id, missStreak, notificationContent);
    } else {
      console.error(`❌ Failed to send notification to user ${userId}:`, ticket[0]);
    }

    return success;

  } catch (error) {
    console.error(`Error sending notification to user ${userId}:`, error);
    return false;
  }
}

function personalizeMessage(notificationContent: any, habit: any, missStreak: number): any {
  // Create a copy to avoid mutating the original
  const personalized = { ...notificationContent };

  // Add habit context to the message
  if (missStreak >= 5) {
    personalized.body = personalized.body.replace(
      'your habit journey',
      `your "${habit.title}" habit journey`
    );
  }

  // Add encouragement based on miss streak
  if (missStreak === 1) {
    personalized.body += " Remember, consistency builds over time. 🌱";
  } else if (missStreak === 3) {
    personalized.body += " Small steps add up to big changes! 💪";
  }

  return personalized;
}

function getTierFromStreak(missStreak: number): number {
  if (missStreak === 1) return 1;
  if (missStreak === 3) return 2;
  if (missStreak >= 5) return 3;
  return 0;
}

// =============================================================================
// LOGGING AND ANALYTICS
// =============================================================================
async function logNotificationSent(
  userId: string,
  habitId: string,
  missStreak: number,
  notificationContent: any
): Promise<void> {
  try {
    await db.collection('notification_logs').add({
      userId,
      habitId,
      missStreak,
      tier: getTierFromStreak(missStreak),
      notificationType: 'habit_followup',
      title: notificationContent.title,
      body: notificationContent.body,
      sentAt: admin.firestore.FieldValue.serverTimestamp(),
      data: notificationContent.data
    });
  } catch (error) {
    console.error('Error logging notification:', error);
    // Don't throw - logging failure shouldn't stop the main process
  }
}
