import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import { Expo } from 'expo-server-sdk';

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();
const expo = new Expo();

/**
 * HTTP callable function to create a graduation post proposal
 * Triggered when user achieves a goal (manually or auto-detected)
 */
export const createGraduationPost = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = context.auth.uid;
  const { circleId, goalTitle, goalType, story, photoUrl } = data;

  if (!circleId || !goalTitle || !goalType || !story) {
    throw new functions.https.HttpsError('invalid-argument', 'circleId, goalTitle, goalType, and story are required');
  }

  if (story.length > 500) {
    throw new functions.https.HttpsError('invalid-argument', 'Story must be 500 characters or less');
  }

  try {
    // Verify user is a member of the circle
    const circleDoc = await db.collection('circles').doc(circleId).get();
    if (!circleDoc.exists) {
      throw new functions.https.HttpsError('not-found', 'Circle not found');
    }

    const circle = circleDoc.data();
    if (!circle?.members?.includes(userId)) {
      throw new functions.https.HttpsError('permission-denied', 'Not a member of this circle');
    }

    // Get user name
    const userDoc = await db.collection('users').doc(userId).get();
    const userName = userDoc.data()?.name || 'Someone';

    // Create graduation post proposal (pending status - requires votes)
    const postData = {
      circleId,
      userId,
      userName,
      goalTitle,
      goalType,
      story: story.trim(),
      photoUrl: photoUrl || undefined,
      votes: {
        userIds: [userId], // User automatically votes to post their own achievement
        count: 1,
      },
      status: 'pending',
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    const postRef = await db.collection('circles').doc(circleId).collection('graduationWall').add(postData);

    // Notify circle members to vote
    await notifyCircleToVote(circleId, userId, userName, goalTitle);

    return { success: true, postId: postRef.id };
  } catch (error) {
    console.error('Error creating graduation post:', error);
    if (error instanceof functions.https.HttpsError) {
      throw error;
    }
    throw new functions.https.HttpsError('internal', 'Failed to create graduation post');
  }
});

/**
 * HTTP callable function to vote on a graduation post
 * Requires majority vote to approve (or can be set to require all members)
 */
export const voteOnGraduationPost = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = context.auth.uid;
  const { circleId, postId, vote } = data;

  if (!circleId || !postId || !vote || !['approve', 'reject'].includes(vote)) {
    throw new functions.https.HttpsError('invalid-argument', 'circleId, postId, and vote (approve/reject) are required');
  }

  try {
    // Verify user is a member of the circle
    const circleDoc = await db.collection('circles').doc(circleId).get();
    if (!circleDoc.exists) {
      throw new functions.https.HttpsError('not-found', 'Circle not found');
    }

    const circle = circleDoc.data();
    if (!circle?.members?.includes(userId)) {
      throw new functions.https.HttpsError('permission-denied', 'Not a member of this circle');
    }

    // Get post
    const postDoc = await db
      .collection('circles')
      .doc(circleId)
      .collection('graduationWall')
      .doc(postId)
      .get();

    if (!postDoc.exists) {
      throw new functions.https.HttpsError('not-found', 'Post not found');
    }

    const post = postDoc.data();
    if (post?.status !== 'pending') {
      throw new functions.https.HttpsError('failed-precondition', 'Post is not pending');
    }

    // Update votes
    const currentVotes = post.votes?.userIds || [];
    const hasVoted = currentVotes.includes(userId);

    if (vote === 'approve' && !hasVoted) {
      // Add vote
      const newVotes = [...currentVotes, userId];
      const voteCount = newVotes.length;
      const totalMembers = circle.members?.length || 1;
      const majorityThreshold = Math.ceil(totalMembers / 2); // 50% + 1

      await postDoc.ref.update({
        'votes.userIds': newVotes,
        'votes.count': voteCount,
      });

      // Check if majority reached
      if (voteCount >= majorityThreshold) {
        await postDoc.ref.update({
          status: 'approved',
          approvedAt: admin.firestore.FieldValue.serverTimestamp(),
        });

        // Notify circle members about the graduation
        await notifyGraduationApproved(circleId, post.userId, post.userName, post.goalTitle);
      }
    } else if (vote === 'reject') {
      // Reject immediately (or could require multiple rejections)
      await postDoc.ref.update({
        status: 'rejected',
      });
    }

    return { success: true };
  } catch (error) {
    console.error('Error voting on graduation post:', error);
    if (error instanceof functions.https.HttpsError) {
      throw error;
    }
    throw new functions.https.HttpsError('internal', 'Failed to vote on post');
  }
});

/**
 * Notify circle members to vote on a graduation post
 */
async function notifyCircleToVote(
  circleId: string,
  userId: string,
  userName: string,
  goalTitle: string
): Promise<void> {
  try {
    const circleDoc = await db.collection('circles').doc(circleId).get();
    if (!circleDoc.exists) return;

    const circle = circleDoc.data();
    const members = circle?.members || [];
    const otherMembers = members.filter((memberId: string) => memberId !== userId);

    if (otherMembers.length === 0) return;

    const pushTokens: string[] = [];
    for (const memberId of otherMembers) {
      const memberDoc = await db.collection('users').doc(memberId).get();
      const memberData = memberDoc.data();
      if (memberData?.expoPushToken && Expo.isExpoPushToken(memberData.expoPushToken)) {
        pushTokens.push(memberData.expoPushToken);
      }
    }

    if (pushTokens.length === 0) return;

    const chunks = expo.chunkPushNotifications(pushTokens);
    for (const chunk of chunks) {
      const messages = chunk.map((token) => ({
        to: token,
        sound: 'default' as const,
        title: '🎓 New Graduation!',
        body: `${userName} achieved: ${goalTitle}! Vote to post on the wall?`,
        data: {
          type: 'graduation_vote',
          circleId,
          deepLink: `coachie://circle_graduation/${circleId}`,
        },
      }));
      await expo.sendPushNotificationsAsync(messages);
    }
  } catch (error) {
    console.error('Error notifying circle to vote:', error);
  }
}

/**
 * Notify circle members when graduation is approved
 */
async function notifyGraduationApproved(
  circleId: string,
  userId: string,
  userName: string,
  goalTitle: string
): Promise<void> {
  try {
    const circleDoc = await db.collection('circles').doc(circleId).get();
    if (!circleDoc.exists) return;

    const circle = circleDoc.data();
    const members = circle?.members || [];

    const pushTokens: string[] = [];
    for (const memberId of members) {
      const memberDoc = await db.collection('users').doc(memberId).get();
      const memberData = memberDoc.data();
      if (memberData?.expoPushToken && Expo.isExpoPushToken(memberData.expoPushToken)) {
        pushTokens.push(memberData.expoPushToken);
      }
    }

    if (pushTokens.length === 0) return;

    const chunks = expo.chunkPushNotifications(pushTokens);
    for (const chunk of chunks) {
      const messages = chunk.map((token) => ({
        to: token,
        sound: 'default' as const,
        title: '🎉 Graduation Posted!',
        body: `${userName} graduated: ${goalTitle}! Check the wall!`,
        data: {
          type: 'graduation_posted',
          circleId,
          deepLink: `coachie://circle_graduation/${circleId}`,
        },
      }));
      await expo.sendPushNotificationsAsync(messages);
    }
  } catch (error) {
    console.error('Error notifying graduation approved:', error);
  }
}

/**
 * Auto-detect goal achievements and create graduation posts
 * This can be triggered by various events (weight loss, running milestones, etc.)
 */
export const autoDetectGoalAchievement = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = context.auth.uid;
  const { goalType, goalValue, currentValue } = data;

  if (!goalType) {
    throw new functions.https.HttpsError('invalid-argument', 'goalType is required');
  }

  try {
    // Get user's circles
    const userDoc = await db.collection('users').doc(userId).get();
    if (!userDoc.exists) {
      throw new functions.https.HttpsError('not-found', 'User not found');
    }

    const userData = userDoc.data();
    const circleIds = userData?.circles || [];

    if (circleIds.length === 0) {
      return { success: true, postsCreated: 0 };
    }

    // Determine goal title based on type
    let goalTitle = '';
    if (goalType === 'weight_loss' && goalValue && currentValue) {
      const weightLost = goalValue - currentValue;
      goalTitle = `Lost ${weightLost.toFixed(1)} lb`;
    } else if (goalType === 'run_5k') {
      goalTitle = 'Ran 5K';
    } else if (goalType === 'meditation_streak') {
      goalTitle = `${goalValue || 30} Day Meditation Streak`;
    } else {
      goalTitle = `Achieved ${goalType}`;
    }

    // Create graduation post proposals for each circle
    let postsCreated = 0;
    for (const circleId of circleIds) {
      try {
        const postData = {
          circleId,
          userId,
          userName: userData?.name || 'Someone',
          goalTitle,
          goalType,
          story: `I'm so proud to have achieved this goal! ${goalTitle}! 🎉`,
          votes: {
            userIds: [userId],
            count: 1,
          },
          status: 'pending',
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
        };

        await db.collection('circles').doc(circleId).collection('graduationWall').add(postData);
        await notifyCircleToVote(circleId, userId, userData?.name || 'Someone', goalTitle);
        postsCreated++;
      } catch (error) {
        console.error(`Error creating graduation post for circle ${circleId}:`, error);
      }
    }

    return { success: true, postsCreated };
  } catch (error) {
    console.error('Error in autoDetectGoalAchievement:', error);
    if (error instanceof functions.https.HttpsError) {
      throw error;
    }
    throw new functions.https.HttpsError('internal', 'Failed to detect goal achievement');
  }
});

/**
 * Generate PDF certificate (Pro feature)
 * Uses a PDF generation library to create a beautiful certificate
 */
export const generateGraduationCertificate = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = context.auth.uid;
  const { circleId, postId } = data;

  if (!circleId || !postId) {
    throw new functions.https.HttpsError('invalid-argument', 'circleId and postId are required');
  }

  try {
    // TODO: Check if user has Pro subscription
    // For now, allow all users

    // Get post
    const postDoc = await db
      .collection('circles')
      .doc(circleId)
      .collection('graduationWall')
      .doc(postId)
      .get();

    if (!postDoc.exists) {
      throw new functions.https.HttpsError('not-found', 'Post not found');
    }

    const post = postDoc.data();
    if (post?.userId !== userId) {
      throw new functions.https.HttpsError('permission-denied', 'Can only generate certificate for your own graduation');
    }

    if (post?.status !== 'approved') {
      throw new functions.https.HttpsError('failed-precondition', 'Can only generate certificate for approved graduations');
    }

    // TODO: Generate PDF using pdfkit or similar library
    // For now, return a placeholder URL
    // In production, this would:
    // 1. Generate PDF with user name, goal, date, circle name
    // 2. Upload to Firebase Storage
    // 3. Return download URL

    const certificateUrl = `https://storage.googleapis.com/coachie-certificates/${postId}.pdf`;

    // Store certificate URL in post
    await postDoc.ref.update({
      certificateUrl,
      certificateGeneratedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    return { success: true, pdfUrl: certificateUrl };
  } catch (error) {
    console.error('Error generating certificate:', error);
    if (error instanceof functions.https.HttpsError) {
      throw error;
    }
    throw new functions.https.HttpsError('internal', 'Failed to generate certificate');
  }
});

