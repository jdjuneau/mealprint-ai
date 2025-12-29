import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

/**
 * Triggered when a habit completion is created
 * Updates circle streaks based on member activity
 */
export const onHabitCompletionForCircleStreak = functions.firestore
  .document('users/{userId}/completions/{completionId}')
  .onCreate(async (snapshot, context) => {
    const { userId } = context.params;
    
    try {
      // Get user's circles
      const userDoc = await db.collection('users').doc(userId).get();
      if (!userDoc.exists) return null;

      const userData = userDoc.data();
      const circleIds = userData?.circles || [];

      if (circleIds.length === 0) return null;

      // Update streaks for all user's circles
      await Promise.all(
        circleIds.map((circleId: string) => updateCircleStreak(circleId))
      );

      return null;
    } catch (error) {
      console.error('Error in onHabitCompletionForCircleStreak:', error);
      return null;
    }
  });

/**
 * Triggered when a health log entry is created
 * Updates circle streaks based on member activity
 */
export const onHealthLogForCircleStreak = functions.firestore
  .document('logs/{userId}/daily/{date}/entries/{entryId}')
  .onCreate(async (snapshot, context) => {
    const { userId, date } = context.params;
    
    try {
      // Get user's circles
      const userDoc = await db.collection('users').doc(userId).get();
      if (!userDoc.exists) return null;

      const userData = userDoc.data();
      const circleIds = userData?.circles || [];

      if (circleIds.length === 0) return null;

      // Only update if the log is for today (not historical data)
      const today = new Date().toISOString().split('T')[0];
      if (date !== today) return null;

      // Update streaks for all user's circles
      await Promise.all(
        circleIds.map((circleId: string) => updateCircleStreak(circleId))
      );

      return null;
    } catch (error) {
      console.error('Error in onHealthLogForCircleStreak:', error);
      return null;
    }
  });

/**
 * Triggered when a circle check-in is created
 * Updates circle streaks based on member activity
 * Note: This listens to the same path as circleCheckInNotifications.ts
 */
export const onCircleCheckInForStreak = functions.firestore
  .document('circles/{circleId}/checkins/{date}/entries/{userId}')
  .onCreate(async (snapshot, context) => {
    const { circleId, date } = context.params;
    
    try {
      // Only update if the check-in is for today (not historical data)
      const today = new Date().toISOString().split('T')[0];
      if (date !== today) return null;

      // Update streak for this circle
      await updateCircleStreak(circleId);

      return null;
    } catch (error) {
      console.error('Error in onCircleCheckInForStreak:', error);
      return null;
    }
  });

/**
 * Update circle streak based on whether all members have activity today
 */
async function updateCircleStreak(circleId: string): Promise<void> {
  try {
    const circleDoc = await db.collection('circles').doc(circleId).get();
    if (!circleDoc.exists) return;

    const circle = circleDoc.data();
    const members = circle?.members || [];

    if (members.length === 0) return;

    const today = new Date().toISOString().split('T')[0];
    const yesterday = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString().split('T')[0];

    // Check if all members have activity today
    const memberActivityPromises = members.map((memberId: string) =>
      hasActivityToday(memberId, today)
    );
    const memberActivities = await Promise.all(memberActivityPromises);
    const allMembersActive = memberActivities.every((active) => active === true);

    if (!allMembersActive) {
      // Not all members active today - reset streak to 0
      await db.collection('circles').doc(circleId).update({
        streak: 0,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      console.log(`Circle ${circleId}: Streak reset to 0 (not all members active today)`);
      return;
    }

    // All members active today - check if we should increment streak
    const currentStreak = circle?.streak || 0;
    const lastStreakDate = circle?.lastStreakDate || '';

    if (lastStreakDate === today) {
      // Already updated today, no need to increment again
      return;
    }

    // Check if we should increment or start a new streak
    if (lastStreakDate === yesterday && currentStreak > 0) {
      // Continue streak - increment (all members active yesterday and today)
      const newStreak = currentStreak + 1;
      await db.collection('circles').doc(circleId).update({
        streak: newStreak,
        lastStreakDate: today,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      console.log(`Circle ${circleId}: Streak incremented to ${newStreak}`);
    } else {
      // Start new streak (first day or gap in streak)
      await db.collection('circles').doc(circleId).update({
        streak: 1,
        lastStreakDate: today,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      console.log(`Circle ${circleId}: New streak started (1 day)`);
    }
  } catch (error) {
    console.error(`Error updating circle streak for ${circleId}:`, error);
  }
}

/**
 * Check if a user has any activity today
 * Activity includes: habit completions, health logs (meals, workouts, water, sleep), check-ins
 */
async function hasActivityToday(userId: string, date: string): Promise<boolean> {
  try {
    // Check for habit completions today
    const todayStart = new Date(date);
    todayStart.setHours(0, 0, 0, 0);
    const todayEnd = new Date(date);
    todayEnd.setHours(23, 59, 59, 999);

    const completionsSnapshot = await db
      .collection('users')
      .doc(userId)
      .collection('completions')
      .where('completedAt', '>=', admin.firestore.Timestamp.fromDate(todayStart))
      .where('completedAt', '<=', admin.firestore.Timestamp.fromDate(todayEnd))
      .limit(1)
      .get();

    if (!completionsSnapshot.empty) {
      return true;
    }

    // Check for health log entries today
    const healthLogsSnapshot = await db
      .collection('logs')
      .doc(userId)
      .collection('daily')
      .doc(date)
      .collection('entries')
      .limit(1)
      .get();

    if (!healthLogsSnapshot.empty) {
      return true;
    }

    // Check for circle check-ins today
    const userDoc = await db.collection('users').doc(userId).get();
    if (!userDoc.exists) return false;

    const userData = userDoc.data();
    const circleIds = userData?.circles || [];

    // Check for circle check-ins today (try both possible paths)
    for (const circleId of circleIds) {
      // Try path: circles/{circleId}/checkIns/{date}/members/{userId}
      const checkInDoc1 = await db
        .collection('circles')
        .doc(circleId)
        .collection('checkIns')
        .doc(date)
        .collection('members')
        .doc(userId)
        .get();

      if (checkInDoc1.exists) {
        return true;
      }

      // Try path: circles/{circleId}/checkins/{date}/entries/{userId}
      const checkInDoc2 = await db
        .collection('circles')
        .doc(circleId)
        .collection('checkins')
        .doc(date)
        .collection('entries')
        .doc(userId)
        .get();

      if (checkInDoc2.exists) {
        return true;
      }
    }

    return false;
  } catch (error) {
    console.error(`Error checking activity for user ${userId} on ${date}:`, error);
    return false;
  }
}

