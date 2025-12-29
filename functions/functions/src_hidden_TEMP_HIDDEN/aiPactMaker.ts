import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import { Expo, ExpoPushMessage } from 'expo-server-sdk';

// Initialize Firebase Admin (if not already initialized)
if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();
const expo = new Expo();

interface HabitMissData {
  userId: string;
  habitId: string;
  habitTitle: string;
  missedAt: admin.firestore.Timestamp;
}

interface PactProposal {
  id: string;
  userId: string;
  otherUserId: string;
  habitTitle: string;
  habitId: string;
  otherHabitId: string;
  goalCategory?: string;
  status: 'pending' | 'accepted' | 'declined' | 'expired' | 'completed';
  user1Accepted?: boolean;
  user2Accepted?: boolean;
  startDate?: admin.firestore.Timestamp;
  endDate?: admin.firestore.Timestamp;
  createdAt: admin.firestore.Timestamp;
  expiresAt: admin.firestore.Timestamp;
}

/**
 * Scheduled function that runs every day at 9 PM
 * Finds users who missed the same habit 2+ days and proposes pacts
 */
export const aiPactMaker = functions.pubsub
  .schedule('0 21 * * *') // 9 PM daily
  .timeZone('America/New_York')
  .onRun(async (context) => {
    console.log('🤝 Starting AI Pact Maker at', new Date().toISOString());

    try {
      // Get date range: last 3 days (to check for 2+ consecutive days)
      const now = new Date();
      const threeDaysAgo = new Date(now);
      threeDaysAgo.setDate(threeDaysAgo.getDate() - 3);
      threeDaysAgo.setHours(0, 0, 0, 0);

      const threeDaysAgoTimestamp = admin.firestore.Timestamp.fromDate(threeDaysAgo);

      // Get all users with habits
      const usersSnapshot = await db.collection('users').get();
      const userMissesByHabit = new Map<string, Map<string, HabitMissData[]>>(); // userId -> habitTitle -> misses

      // Collect misses for each user grouped by habit
      for (const userDoc of usersSnapshot.docs) {
        const userId = userDoc.id;
        const missesSnapshot = await db
          .collection('users')
          .doc(userId)
          .collection('misses')
          .where('missedAt', '>=', threeDaysAgoTimestamp)
          .get();

        if (missesSnapshot.empty) continue;

        const userHabitMisses = new Map<string, HabitMissData[]>();
        missesSnapshot.docs.forEach((missDoc) => {
          const missData = missDoc.data();
          const habitTitle = (missData.habitTitle || 'Unknown Habit').toLowerCase().trim();
          
          if (!userHabitMisses.has(habitTitle)) {
            userHabitMisses.set(habitTitle, []);
          }
          userHabitMisses.get(habitTitle)!.push({
            userId,
            habitId: missData.habitId,
            habitTitle: missData.habitTitle || 'Unknown Habit',
            missedAt: missData.missedAt,
          });
        });

        if (userHabitMisses.size > 0) {
          userMissesByHabit.set(userId, userHabitMisses);
        }
      }

      console.log(`📊 Found ${userMissesByHabit.size} users with misses in the last 3 days`);

      // Find users who missed the same habit for 2+ consecutive days
      const eligibleHabits: Array<{ 
        title: string; 
        users: Array<{ userId: string; habitId: string; misses: HabitMissData[] }> 
      }> = [];

      // Group by normalized habit title across all users
      const habitGroups = new Map<string, Array<{ userId: string; habitId: string; misses: HabitMissData[] }>>();

      userMissesByHabit.forEach((userHabits, userId) => {
        userHabits.forEach((misses, normalizedTitle) => {
          // Check if user has 2+ consecutive days of misses
          const sortedMisses = misses.sort((a, b) => 
            a.missedAt.toMillis() - b.missedAt.toMillis()
          );
          
          // Get unique dates (normalize to day)
          const uniqueDates = new Set<number>();
          sortedMisses.forEach((miss) => {
            const date = miss.missedAt.toDate();
            // Normalize to start of day
            const normalizedDate = new Date(date.getFullYear(), date.getMonth(), date.getDate());
            uniqueDates.add(normalizedDate.getTime());
          });
          
          const sortedDates = Array.from(uniqueDates).sort((a, b) => a - b);
          
          // Check for consecutive days
          let maxConsecutive = 1;
          let currentConsecutive = 1;
          
          for (let i = 1; i < sortedDates.length; i++) {
            const prevDate = new Date(sortedDates[i - 1]);
            const currDate = new Date(sortedDates[i]);
            const daysDiff = Math.floor(
              (currDate.getTime() - prevDate.getTime()) / (1000 * 60 * 60 * 24)
            );
            
            if (daysDiff === 1) {
              currentConsecutive++;
              maxConsecutive = Math.max(maxConsecutive, currentConsecutive);
            } else {
              currentConsecutive = 1; // Reset if gap > 1 day
            }
          }

          if (maxConsecutive >= 2) {
            if (!habitGroups.has(normalizedTitle)) {
              habitGroups.set(normalizedTitle, []);
            }
            habitGroups.get(normalizedTitle)!.push({
              userId,
              habitId: misses[0].habitId,
              misses: sortedMisses,
            });
          }
        });
      });

      // Filter to habits with 2+ users
      habitGroups.forEach((users, normalizedTitle) => {
        if (users.length >= 2) {
          eligibleHabits.push({
            title: users[0].misses[0].habitTitle, // Get original title
            users,
          });
        }
      });

      console.log(`🎯 Found ${eligibleHabits.length} habits with 2+ users who missed them`);

      let proposalsCreated = 0;

      // Group users by goal category first, then by habit
      const usersByGoalCategory = new Map<string, Map<string, Array<{ userId: string; habitId: string; misses: HabitMissData[] }>>>();
      
      for (const { title, users } of eligibleHabits) {
        for (const user of users) {
          // Get user's goal category
          const userDoc = await db.collection('users').doc(user.userId).get();
          if (!userDoc.exists) continue;
          
          const userData = userDoc.data();
          const goalCategory = userData?.habitGoals?.primaryGoal || 
                              userData?.habitGoals?.selectedGoal || 
                              userData?.selectedGoal || 
                              'general';
          
          if (!usersByGoalCategory.has(goalCategory)) {
            usersByGoalCategory.set(goalCategory, new Map());
          }
          
          const habitsInCategory = usersByGoalCategory.get(goalCategory)!;
          if (!habitsInCategory.has(title)) {
            habitsInCategory.set(title, []);
          }
          
          habitsInCategory.get(title)!.push(user);
        }
      }

      // For each goal category and habit, create pact proposals
      for (const [goalCategory, habitsInCategory] of usersByGoalCategory) {
        for (const [habitTitle, users] of habitsInCategory) {
          // Only create pacts if 2+ users in same goal category
          if (users.length < 2) continue;

          // Create pairs of users (2-person pacts)
          for (let i = 0; i < users.length; i++) {
            for (let j = i + 1; j < users.length; j++) {
              const user1 = users[i];
              const user2 = users[j];

              // Check if a pact proposal already exists between these users for this habit
              const existingProposal = await checkExistingProposal(user1.userId, user2.userId, habitTitle);
              if (existingProposal) {
                console.log(`⏭️  Skipping - proposal already exists between ${user1.userId} and ${user2.userId} for "${habitTitle}"`);
                continue;
              }

              // Get user names and miss counts
              const [user1Doc, user2Doc] = await Promise.all([
                db.collection('users').doc(user1.userId).get(),
                db.collection('users').doc(user2.userId).get(),
              ]);

              const user1Name = user1Doc.data()?.name || 'You';
              const user2Name = user2Doc.data()?.name || 'Someone';

              // Count consecutive missed days
              const user1MissCount = user1.misses.length;
              const user2MissCount = user2.misses.length;
              const missCount = Math.max(user1MissCount, user2MissCount);

              // Create pact proposal
              const proposalId = await createPactProposal(
                user1.userId,
                user2.userId,
                habitTitle,
                user1.habitId,
                user2.habitId,
                goalCategory
              );

              // Send DM/notification to both users with new message format
              await sendPactProposalDM(
                user1.userId,
                user2Name,
                habitTitle,
                missCount,
                proposalId
              );
              await sendPactProposalDM(
                user2.userId,
                user1Name,
                habitTitle,
                missCount,
                proposalId
              );

              proposalsCreated++;
            }
          }
        }
      }

      console.log(`✅ AI Pact Maker completed. Created ${proposalsCreated} pact proposals`);

      return {
        success: true,
        proposalsCreated,
        eligibleHabits: eligibleHabits.length,
      };
    } catch (error) {
      console.error('❌ Error in AI Pact Maker:', error);
      throw error;
    }
  });

/**
 * Check if a pact proposal already exists between two users for a habit
 */
async function checkExistingProposal(
  userId1: string,
  userId2: string,
  habitTitle: string
): Promise<boolean> {
  const proposalsSnapshot = await db
    .collection('pactProposals')
    .where('status', '==', 'pending')
    .where('habitTitle', '==', habitTitle)
    .get();

  for (const doc of proposalsSnapshot.docs) {
    const proposal = doc.data() as PactProposal;
    if (
      (proposal.userId === userId1 && proposal.otherUserId === userId2) ||
      (proposal.userId === userId2 && proposal.otherUserId === userId1)
    ) {
      return true;
    }
  }

  return false;
}

/**
 * Create a pact proposal document
 */
async function createPactProposal(
  userId1: string,
  userId2: string,
  habitTitle: string,
  habitId1: string,
  habitId2: string,
  goalCategory?: string
): Promise<string> {
  const now = admin.firestore.Timestamp.now();
  const expiresAt = new Date(now.toDate());
  expiresAt.setDate(expiresAt.getDate() + 1); // Expires in 24 hours

  const proposal: Omit<PactProposal, 'id'> = {
    userId: userId1,
    otherUserId: userId2,
    habitTitle,
    habitId: habitId1,
    otherHabitId: habitId2,
    goalCategory,
    status: 'pending',
    user1Accepted: false,
    user2Accepted: false,
    createdAt: now,
    expiresAt: admin.firestore.Timestamp.fromDate(expiresAt),
  };

  const docRef = await db.collection('pactProposals').add(proposal);
  return docRef.id;
}

/**
 * Send pact proposal DM to user (new format)
 */
async function sendPactProposalDM(
  userId: string,
  otherUserName: string,
  habitTitle: string,
  missCount: number,
  proposalId: string
): Promise<void> {
  try {
    // Get user's push token
    const userDoc = await db.collection('users').doc(userId).get();
    const userData = userDoc.data();

    if (!userData?.expoPushToken) {
      console.log(`No push token found for user ${userId}, skipping notification`);
      return;
    }

    const pushToken = userData.expoPushToken;

    if (!Expo.isExpoPushToken(pushToken)) {
      console.error(`Invalid Expo push token for user ${userId}`);
      return;
    }

    // Create DM message with new format
    const message = `You and ${otherUserName} both skipped '${habitTitle}' ${missCount} ${missCount === 1 ? 'night' : 'nights'}.\n\nWant to start a 7-day pact together? I'll check in daily.`;

    // Send push notification
    const notification: ExpoPushMessage = {
      to: pushToken,
      sound: 'default',
      title: '🤝 Pact Opportunity',
      body: `You and ${otherUserName} both skipped '${habitTitle}' ${missCount} ${missCount === 1 ? 'night' : 'nights'}. Want a 7-day pact?`,
      data: {
        type: 'pact_proposal',
        proposalId,
        habitTitle,
        otherUserName,
        deepLink: `coachie://pact_proposal/${proposalId}`,
      },
      priority: 'default',
      ttl: 86400, // 24 hours
    };

    const ticket = await expo.sendPushNotificationsAsync([notification]);
    console.log(`📤 Sent pact proposal notification to user ${userId}:`, ticket[0]?.status);

    // Create a chat message/DM in Firestore
    await createPactProposalDM(userId, message, proposalId);
  } catch (error) {
    console.error(`Error sending pact proposal DM to user ${userId}:`, error);
  }
}

/**
 * Create a DM/chat message for the pact proposal
 */
async function createPactProposalDM(
  userId: string,
  message: string,
  proposalId: string
): Promise<void> {
  try {
    // Store in chat messages collection (matches chat structure)
    const messageId = admin.firestore().collection('chats').doc().id;
    await db.collection('chats').doc(userId).collection('messages').doc(messageId).set({
      id: messageId,
      userId: userId,
      content: message,
      isUser: false,
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
      messageType: 'pact_proposal',
      proposalId: proposalId,
    });
  } catch (error) {
    console.error(`Error creating DM for user ${userId}:`, error);
  }
}

/**
 * HTTP callable function to accept a pact proposal
 * Creates a 2-person circle when both users accept
 */
export const acceptPactProposal = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = context.auth.uid;
  const { proposalId } = data;

  if (!proposalId) {
    throw new functions.https.HttpsError('invalid-argument', 'proposalId is required');
  }

  try {
    const proposalDoc = await db.collection('pactProposals').doc(proposalId).get();

    if (!proposalDoc.exists) {
      throw new functions.https.HttpsError('not-found', 'Pact proposal not found');
    }

    const proposal = proposalDoc.data() as PactProposal;

    // Check if proposal is still valid
    if (proposal.status !== 'pending') {
      throw new functions.https.HttpsError('failed-precondition', 'Proposal is no longer pending');
    }

    const now = admin.firestore.Timestamp.now();
    if (proposal.expiresAt < now) {
      // Mark as expired
      await proposalDoc.ref.update({ status: 'expired' });
      throw new functions.https.HttpsError('deadline-exceeded', 'Proposal has expired');
    }

    // Check if user is part of this proposal
    if (proposal.userId !== userId && proposal.otherUserId !== userId) {
      throw new functions.https.HttpsError('permission-denied', 'Not authorized for this proposal');
    }

    // Update proposal status based on which user accepted
    const isUser1 = proposal.userId === userId;
    const acceptField = isUser1 ? 'user1Accepted' : 'user2Accepted';

    await proposalDoc.ref.update({
      [acceptField]: true,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    // Check if both users have accepted
    const updatedProposal = (await proposalDoc.ref.get()).data() as any;
    const bothAccepted = updatedProposal.user1Accepted && updatedProposal.user2Accepted;

    if (bothAccepted) {
      // Create the circle (add id to proposal)
      const proposalWithId = { ...proposal, id: proposalDoc.id } as PactProposal;
      await createPactCircle(proposalWithId);
      
      // Mark proposal as accepted
      await proposalDoc.ref.update({ status: 'accepted' });

      return { success: true, circleCreated: true };
    }

    return { success: true, circleCreated: false, waitingForOther: true };
  } catch (error) {
    console.error('Error accepting pact proposal:', error);
    if (error instanceof functions.https.HttpsError) {
      throw error;
    }
    throw new functions.https.HttpsError('internal', 'Failed to accept pact proposal');
  }
});

/**
 * Create a 2-person circle for the pact
 */
async function createPactCircle(proposal: PactProposal): Promise<string> {
  try {
    // Get user names for circle name
    const [user1Doc, user2Doc] = await Promise.all([
      db.collection('users').doc(proposal.userId).get(),
      db.collection('users').doc(proposal.otherUserId).get(),
    ]);

    const user1Name = user1Doc.data()?.name || 'User 1';
    const user2Name = user2Doc.data()?.name || 'User 2';

    // Calculate start and end dates (7-day pact)
    const startDate = admin.firestore.Timestamp.now();
    const endDate = new Date(startDate.toDate());
    endDate.setDate(endDate.getDate() + 7);
    const endDateTimestamp = admin.firestore.Timestamp.fromDate(endDate);

    // Create circle with pact-specific data
    const circleData = {
      name: `${proposal.habitTitle} Pact`,
      goal: `Complete ${proposal.habitTitle} for 7 days`,
      members: [proposal.userId, proposal.otherUserId],
      streak: 0,
      createdBy: proposal.userId,
      maxMembers: 2,
      isPact: true, // Mark as a pact circle
      pactStartDate: startDate,
      pactEndDate: endDateTimestamp,
      pactHabitId: proposal.habitId,
      pactHabitTitle: proposal.habitTitle,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    const circleRef = await db.collection('circles').add(circleData);

    // Update both users' circles array
    await Promise.all([
      db.collection('users').doc(proposal.userId).update({
        circles: admin.firestore.FieldValue.arrayUnion(circleRef.id),
      }),
      db.collection('users').doc(proposal.otherUserId).update({
        circles: admin.firestore.FieldValue.arrayUnion(circleRef.id),
      }),
    ]);

    // Update proposal with circle ID and dates
    await db.collection('pactProposals').doc(proposal.id).update({
      circleId: circleRef.id,
      startDate,
      endDate: endDateTimestamp,
    });

    console.log(`✅ Created pact circle ${circleRef.id} for users ${proposal.userId} and ${proposal.otherUserId}`);

    // Send notifications to both users with "Pact ends in 7 days" message
    await Promise.all([
      sendCircleCreatedNotification(proposal.userId, circleRef.id, user2Name, proposal.habitTitle),
      sendCircleCreatedNotification(proposal.otherUserId, circleRef.id, user1Name, proposal.habitTitle),
    ]);

    return circleRef.id;
  } catch (error) {
    console.error('Error creating pact circle:', error);
    throw error;
  }
}

/**
 * Send notification when circle is created
 */
async function sendCircleCreatedNotification(
  userId: string,
  circleId: string,
  otherUserName: string,
  habitTitle: string
): Promise<void> {
  try {
    const userDoc = await db.collection('users').doc(userId).get();
    const userData = userDoc.data();

    if (!userData?.expoPushToken) return;

    const pushToken = userData.expoPushToken;
    if (!Expo.isExpoPushToken(pushToken)) return;

    const notification: ExpoPushMessage = {
      to: pushToken,
      sound: 'default',
      title: '🤝 Pact Started!',
      body: `Pact ends in 7 days — let's do this! You and ${otherUserName} are now accountable for ${habitTitle}.`,
      data: {
        type: 'pact_started',
        circleId,
        deepLink: `coachie://circle_detail/${circleId}`,
      },
      priority: 'default',
    };

    await expo.sendPushNotificationsAsync([notification]);

    // Also create a DM
    const messageId = db.collection('chats').doc().id;
    await db.collection('chats').doc(userId).collection('messages').doc(messageId).set({
      id: messageId,
      userId: userId,
      content: `Pact ends in 7 days — let's do this! You and ${otherUserName} are now accountable for ${habitTitle}.`,
      isUser: false,
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
      messageType: 'pact_started',
      circleId: circleId,
    });
  } catch (error) {
    console.error(`Error sending circle created notification to user ${userId}:`, error);
  }
}

