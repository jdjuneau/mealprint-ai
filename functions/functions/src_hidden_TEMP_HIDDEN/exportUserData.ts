import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';

/**
 * Export all user data in JSON format (GDPR Article 15 - Right of Access)
 * 
 * This function exports:
 * - User profile
 * - Health logs (meals, workouts, sleep, water, weight, mood, supplements, menstrual)
 * - Habits and completions
 * - Recipes and saved meals
 * - Weekly blueprints
 * - Conversations and messages
 * - Friends and circles
 * - Settings and preferences
 */
export const exportUserData = functions.runWith({
  timeoutSeconds: 540,
  memory: '1GB'
}).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const uid = context.auth.uid;
  const db = admin.firestore();
  
  console.log(`[EXPORT] Starting data export for user: ${uid}`);

  try {
    const exportData: any = {
      exportDate: new Date().toISOString(),
      userId: uid,
      data: {}
    };

    // 1. User Profile
    try {
      const profileDoc = await db.collection('users').doc(uid).get();
      if (profileDoc.exists) {
        exportData.data.profile = profileDoc.data();
      }
    } catch (e: any) {
      console.error(`[EXPORT] Error exporting profile:`, e.message);
    }

    // 2. Health Logs (from logs/{uid}/daily/{date}/entries)
    try {
      const logsCollection = db.collection('logs').doc(uid).collection('daily');
      const dailyLogsSnapshot = await logsCollection.get();
      
      exportData.data.healthLogs = {};
      for (const dayDoc of dailyLogsSnapshot.docs) {
        const date = dayDoc.id;
        const entriesSnapshot = await dayDoc.ref.collection('entries').get();
        exportData.data.healthLogs[date] = {
          dailyLog: dayDoc.data(),
          entries: entriesSnapshot.docs.map(doc => ({
            id: doc.id,
            ...doc.data()
          }))
        };
      }
    } catch (e: any) {
      console.error(`[EXPORT] Error exporting health logs:`, e.message);
    }

    // 3. Habits and Completions
    try {
      const habitsSnapshot = await db.collection('users').doc(uid).collection('habits').get();
      exportData.data.habits = habitsSnapshot.docs.map(doc => ({
        id: doc.id,
        ...doc.data()
      }));

      const completionsSnapshot = await db.collection('users').doc(uid).collection('habitCompletions').get();
      exportData.data.habitCompletions = completionsSnapshot.docs.map(doc => ({
        id: doc.id,
        ...doc.data()
      }));
    } catch (e: any) {
      console.error(`[EXPORT] Error exporting habits:`, e.message);
    }

    // 4. Recipes and Saved Meals
    try {
      const recipesSnapshot = await db.collection('users').doc(uid).collection('recipes').get();
      exportData.data.recipes = recipesSnapshot.docs.map(doc => ({
        id: doc.id,
        ...doc.data()
      }));

      const savedMealsSnapshot = await db.collection('users').doc(uid).collection('savedMeals').get();
      exportData.data.savedMeals = savedMealsSnapshot.docs.map(doc => ({
        id: doc.id,
        ...doc.data()
      }));
    } catch (e: any) {
      console.error(`[EXPORT] Error exporting recipes:`, e.message);
    }

    // 5. Weekly Blueprints
    try {
      const blueprintsSnapshot = await db.collection('users').doc(uid).collection('weeklyBlueprints').get();
      exportData.data.weeklyBlueprints = blueprintsSnapshot.docs.map(doc => ({
        weekId: doc.id,
        ...doc.data()
      }));
    } catch (e: any) {
      console.error(`[EXPORT] Error exporting blueprints:`, e.message);
    }

    // 6. Conversations and Messages
    try {
      const conversationsSnapshot1 = await db.collection('conversations')
        .where('participant1Id', '==', uid)
        .get();
      const conversationsSnapshot2 = await db.collection('conversations')
        .where('participant2Id', '==', uid)
        .get();

      exportData.data.conversations = [];
      
      for (const convDoc of [...conversationsSnapshot1.docs, ...conversationsSnapshot2.docs]) {
        const messagesSnapshot = await convDoc.ref.collection('messages').get();
        exportData.data.conversations.push({
          id: convDoc.id,
          ...convDoc.data(),
          messages: messagesSnapshot.docs.map(doc => ({
            id: doc.id,
            ...doc.data()
          }))
        });
      }
    } catch (e: any) {
      console.error(`[EXPORT] Error exporting conversations:`, e.message);
    }

    // 7. Friends and Circles
    try {
      const friendRequestsSnapshot = await db.collection('friendRequests')
        .where('fromUserId', '==', uid)
        .get();
      const receivedRequestsSnapshot = await db.collection('friendRequests')
        .where('toUserId', '==', uid)
        .get();

      exportData.data.friendRequests = {
        sent: friendRequestsSnapshot.docs.map(doc => ({ id: doc.id, ...doc.data() })),
        received: receivedRequestsSnapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }))
      };

      // Get circles where user is a member
      const circlesSnapshot = await db.collection('circles').get();
      exportData.data.circles = circlesSnapshot.docs
        .filter(doc => {
          const data = doc.data();
          const members = data.members || [];
          return members.includes(uid);
        })
        .map(doc => ({
          id: doc.id,
          ...doc.data()
        }));
    } catch (e: any) {
      console.error(`[EXPORT] Error exporting social data:`, e.message);
    }

    // 8. Scans (supplement scans)
    try {
      const scansSnapshot = await db.collection('scans').doc(uid).collection('scans').get();
      exportData.data.scans = scansSnapshot.docs.map(doc => ({
        id: doc.id,
        ...doc.data()
      }));
    } catch (e: any) {
      console.error(`[EXPORT] Error exporting scans:`, e.message);
    }

    // 9. Settings and Preferences
    try {
      const profileDoc = await db.collection('users').doc(uid).get();
      if (profileDoc.exists) {
        const profileData = profileDoc.data() || {};
        exportData.data.settings = {
          dietaryPreference: profileData.dietaryPreference,
          mealsPerDay: profileData.mealsPerDay,
          snacksPerDay: profileData.snacksPerDay,
          mealTimes: profileData.mealTimes,
          notifications: profileData.notifications,
          preferredCookingMethods: profileData.preferredCookingMethods,
          useImperial: profileData.useImperial,
          menstrualCycleEnabled: profileData.menstrualCycleEnabled,
          averageCycleLength: profileData.averageCycleLength,
          averagePeriodLength: profileData.averagePeriodLength
        };
      }
    } catch (e: any) {
      console.error(`[EXPORT] Error exporting settings:`, e.message);
    }

    console.log(`[EXPORT] ✅ Data export completed for user: ${uid}`);
    
    return {
      success: true,
      data: exportData,
      exportDate: exportData.exportDate
    };

  } catch (error: any) {
    console.error(`[EXPORT] ❌ Error exporting data for user ${uid}:`, error);
    throw new functions.https.HttpsError('internal', `Failed to export data: ${error.message}`);
  }
});
