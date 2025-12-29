import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import Stripe from 'stripe';

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();
const stripe = new Stripe(process.env.STRIPE_SECRET_KEY || '', {
  apiVersion: '2023-10-16',
});

// Challenge templates
const CHALLENGE_TEMPLATES: Record<string, any> = {
  '10k_steps_7days': {
    title: '10K Steps/Day for 7 Days',
    description: 'Walk 10,000 steps every day for a week',
    type: 'steps',
    targetValue: 10000,
    duration: 7,
    unit: 'steps',
    icon: '🚶‍♀️',
  },
  'no_sugar_8pm': {
    title: 'No Sugar After 8 PM',
    description: 'Avoid sugar after 8 PM for 7 days',
    type: 'habit',
    targetValue: 1,
    duration: 7,
    icon: '🍭',
  },
  'meditate_5min': {
    title: 'Meditate 5 Min Daily',
    description: 'Meditate for at least 5 minutes every day',
    type: 'meditation',
    targetValue: 5,
    duration: 7,
    unit: 'minutes',
    icon: '🧘',
  },
};

/**
 * HTTP callable function to create a challenge for a circle
 */
export const createCircleChallenge = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = context.auth.uid;
  const { circleId, templateId, charityName, charityId } = data;

  if (!circleId || !templateId) {
    throw new functions.https.HttpsError('invalid-argument', 'circleId and templateId are required');
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

    // Get template
    const template = CHALLENGE_TEMPLATES[templateId];
    if (!template) {
      throw new functions.https.HttpsError('invalid-argument', 'Invalid template ID');
    }

    // Calculate dates
    const startDate = new Date();
    startDate.setHours(0, 0, 0, 0);
    const endDate = new Date(startDate);
    endDate.setDate(endDate.getDate() + template.duration);

    // Create challenge
    const challengeData = {
      circleId,
      templateId,
      title: template.title,
      description: template.description,
      type: template.type,
      targetValue: template.targetValue,
      duration: template.duration,
      unit: template.unit,
      startDate: admin.firestore.Timestamp.fromDate(startDate),
      endDate: admin.firestore.Timestamp.fromDate(endDate),
      entryFee: 500, // $5.00 in cents
      charityName: charityName || 'Diabetes Research',
      charityId: charityId || 'diabetes_research',
      status: 'upcoming',
      participants: [],
      totalRaised: 0,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    const challengeRef = await db.collection('circleChallenges').add(challengeData);

    return { success: true, challengeId: challengeRef.id };
  } catch (error) {
    console.error('Error creating challenge:', error);
    if (error instanceof functions.https.HttpsError) {
      throw error;
    }
    throw new functions.https.HttpsError('internal', 'Failed to create challenge');
  }
});

/**
 * HTTP callable function to join a challenge (creates Stripe payment intent)
 */
export const joinChallenge = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = context.auth.uid;
  const { challengeId } = data;

  if (!challengeId) {
    throw new functions.https.HttpsError('invalid-argument', 'challengeId is required');
  }

  try {
    // Get challenge
    const challengeDoc = await db.collection('circleChallenges').doc(challengeId).get();
    if (!challengeDoc.exists) {
      throw new functions.https.HttpsError('not-found', 'Challenge not found');
    }

    const challenge = challengeDoc.data();
    if (!challenge) {
      throw new functions.https.HttpsError('not-found', 'Challenge data not found');
    }

    // Verify user is a member of the circle
    const circleDoc = await db.collection('circles').doc(challenge.circleId).get();
    if (!circleDoc.exists) {
      throw new functions.https.HttpsError('not-found', 'Circle not found');
    }

    const circle = circleDoc.data();
    if (!circle?.members?.includes(userId)) {
      throw new functions.https.HttpsError('permission-denied', 'Not a member of this circle');
    }

    // Check if user already joined
    const participantDoc = await db
      .collection('circleChallenges')
      .doc(challengeId)
      .collection('participants')
      .doc(userId)
      .get();

    if (participantDoc.exists) {
      throw new functions.https.HttpsError('already-exists', 'Already joined this challenge');
    }

    // Create Stripe payment intent
    if (!stripe) {
      throw new functions.https.HttpsError('failed-precondition', 'Stripe not configured');
    }

    const paymentIntent = await stripe.paymentIntents.create({
      amount: challenge.entryFee || 500, // $5.00
      currency: 'usd',
      metadata: {
        challengeId,
        userId,
        charityId: challenge.charityId,
        charityName: challenge.charityName,
      },
    });

    // Create participant record
    await db
      .collection('circleChallenges')
      .doc(challengeId)
      .collection('participants')
      .doc(userId)
      .set({
        userId,
        joinedAt: admin.firestore.FieldValue.serverTimestamp(),
        paid: false,
        paymentIntentId: paymentIntent.id,
        dailyProofs: [],
        completionCount: 0,
        isCompleted: false,
      });

    return {
      success: true,
      clientSecret: paymentIntent.client_secret,
      paymentIntentId: paymentIntent.id,
    };
  } catch (error) {
    console.error('Error joining challenge:', error);
    if (error instanceof functions.https.HttpsError) {
      throw error;
    }
    throw new functions.https.HttpsError('internal', 'Failed to join challenge');
  }
});

/**
 * Webhook handler for Stripe payment confirmation
 */
export const handleStripeWebhook = functions.https.onRequest(async (req, res) => {
  if (!stripe) {
    res.status(500).send('Stripe not configured');
    return;
  }

  const sig = req.headers['stripe-signature'];
  if (!sig) {
    res.status(400).send('No signature');
    return;
  }

  let event: Stripe.Event;

  try {
    event = stripe.webhooks.constructEvent(
      req.body,
      sig,
      process.env.STRIPE_WEBHOOK_SECRET || ''
    );
  } catch (err) {
    console.error('Webhook signature verification failed:', err);
    res.status(400).send(`Webhook Error: ${err}`);
    return;
  }

  if (event.type === 'payment_intent.succeeded') {
    const paymentIntent = event.data.object as Stripe.PaymentIntent;
    const { challengeId, userId } = paymentIntent.metadata;

    if (challengeId && userId) {
      try {
        // Update participant as paid
        await db
          .collection('circleChallenges')
          .doc(challengeId)
          .collection('participants')
          .doc(userId)
          .update({
            paid: true,
          });

        // Add user to challenge participants
        await db.collection('circleChallenges').doc(challengeId).update({
          participants: admin.firestore.FieldValue.arrayUnion(userId),
          totalRaised: admin.firestore.FieldValue.increment(paymentIntent.amount),
        });

        console.log(`✅ Payment confirmed for challenge ${challengeId}, user ${userId}`);
      } catch (error) {
        console.error('Error updating challenge after payment:', error);
      }
    }
  }

  res.json({ received: true });
});

/**
 * HTTP callable function to submit proof for a challenge day
 */
export const submitChallengeProof = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = context.auth.uid;
  const { challengeId, date, proofType, proofUrl, wearableData } = data;

  if (!challengeId || !date || !proofType) {
    throw new functions.https.HttpsError('invalid-argument', 'Missing required fields');
  }

  try {
    // Verify user is a participant
    const participantDoc = await db
      .collection('circleChallenges')
      .doc(challengeId)
      .collection('participants')
      .doc(userId)
      .get();

    if (!participantDoc.exists) {
      throw new functions.https.HttpsError('permission-denied', 'Not a participant in this challenge');
    }

    const participant = participantDoc.data();
    if (!participant?.paid) {
      throw new functions.https.HttpsError('failed-precondition', 'Payment not confirmed');
    }

    // Create proof document
    const proofData = {
      challengeId,
      userId,
      date,
      proofType,
      proofUrl: proofUrl || undefined,
      wearableData: wearableData || undefined,
      verified: proofType === 'wearable', // Auto-verify wearable data
      verifiedAt: proofType === 'wearable' ? admin.firestore.FieldValue.serverTimestamp() : undefined,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    const proofRef = await db
      .collection('circleChallenges')
      .doc(challengeId)
      .collection('proofs')
      .add(proofData);

    // Update participant's daily proofs
    const dailyProofs = participant.dailyProofs || [];
    const existingProofIndex = dailyProofs.findIndex((p: any) => p.date === date);

    const proofEntry = {
      date,
      proofType,
      proofUrl: proofUrl || undefined,
      verified: proofData.verified,
      verifiedAt: proofData.verifiedAt,
    };

    if (existingProofIndex >= 0) {
      dailyProofs[existingProofIndex] = proofEntry;
    } else {
      dailyProofs.push(proofEntry);
    }

    // Update completion count
    const completionCount = dailyProofs.filter((p: any) => p.verified).length;

    await participantDoc.ref.update({
      dailyProofs,
      completionCount,
      isCompleted: completionCount >= (await getChallengeDuration(challengeId)),
    });

    return { success: true, proofId: proofRef.id };
  } catch (error) {
    console.error('Error submitting proof:', error);
    if (error instanceof functions.https.HttpsError) {
      throw error;
    }
    throw new functions.https.HttpsError('internal', 'Failed to submit proof');
  }
});

/**
 * Helper function to get challenge duration
 */
async function getChallengeDuration(challengeId: string): Promise<number> {
  const challengeDoc = await db.collection('circleChallenges').doc(challengeId).get();
  const challenge = challengeDoc.data();
  return challenge?.duration || 7;
}

/**
 * Scheduled function to check challenge completion and process donations
 */
export const checkChallengeCompletion = functions.pubsub
  .schedule('0 0 * * *') // Daily at midnight
  .timeZone('UTC')
  .onRun(async (context) => {
    console.log('🏁 Checking challenge completions at', new Date().toISOString());

    try {
      const now = new Date();
      const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());

      // Get all active challenges that ended
      const challengesSnapshot = await db
        .collection('circleChallenges')
        .where('status', '==', 'active')
        .get();

      let completedChallenges = 0;

      for (const challengeDoc of challengesSnapshot.docs) {
        const challenge = challengeDoc.data();
        const endDate = challenge.endDate?.toDate();

        if (!endDate || endDate > today) continue;

        // Get all participants
        const participantsSnapshot = await db
          .collection('circleChallenges')
          .doc(challengeDoc.id)
          .collection('participants')
          .where('paid', '==', true)
          .get();

        const winners: string[] = [];
        const duration = challenge.duration || 7;

        for (const participantDoc of participantsSnapshot.docs) {
          const participant = participantDoc.data();
          if (participant.completionCount >= duration) {
            winners.push(participant.userId);
          }
        }

        // If there are winners, process donation
        if (winners.length > 0) {
          const totalRaised = challenge.totalRaised || 0;
          const totalDollars = totalRaised / 100;

          // Process donation to charity (this would integrate with charity API)
          // For now, we'll just log it
          console.log(
            `💰 Processing $${totalDollars} donation to ${challenge.charityName} for challenge ${challengeDoc.id}`
          );

          // Get circle data for the message
          const circleDoc = await db.collection('circles').doc(challenge.circleId).get();
          const circleData = circleDoc.data();
          const circleName = circleData?.name || 'Your circle';
          
          // Award badges and post to graduation wall
          for (const winnerId of winners) {
            await awardChallengeBadge(winnerId, challengeDoc.id, challenge.title, challenge.charityName, totalDollars);
            await postToGraduationWall(winnerId, challengeDoc.id, challenge.title, challenge.charityName, totalDollars, circleName);
            
            // Send notification to winner
            await sendChallengeCompletionNotification(winnerId, challenge.title, challenge.charityName, totalDollars, circleName);
          }
          
          // Send notification to all circle members about the completion
          const circleMembers = circleData?.members || [];
          for (const memberId of circleMembers) {
            if (!winners.includes(memberId)) {
              // Notify non-winners about the circle's success
              await sendChallengeCompletionNotification(memberId, challenge.title, challenge.charityName, totalDollars, circleName);
            }
          }

          // Update challenge status
          await challengeDoc.ref.update({
            status: 'completed',
            completedAt: admin.firestore.FieldValue.serverTimestamp(),
            winners,
            donationAmount: totalRaised,
          });
        } else {
          // No winners - refund or keep donation (business decision)
          await challengeDoc.ref.update({
            status: 'completed',
            completedAt: admin.firestore.FieldValue.serverTimestamp(),
          });
        }

        completedChallenges++;
      }

      console.log(`✅ Processed ${completedChallenges} completed challenges`);

      return { success: true, completedChallenges };
    } catch (error) {
      console.error('❌ Error checking challenge completion:', error);
      throw error;
    }
  });

/**
 * Award challenge completion badge
 */
async function awardChallengeBadge(
  userId: string,
  challengeId: string,
  challengeTitle: string,
  charityName: string,
  amountRaised: number
): Promise<void> {
  try {
    const badgeData = {
      type: 'challenge_completion',
      title: `${challengeTitle} Champion`,
      description: `Completed challenge and raised $${amountRaised} for ${charityName}`,
      icon: '🏆',
      challengeId,
      charityName,
      amountRaised,
      earnedAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    await db.collection('users').doc(userId).collection('badges').add(badgeData);
    console.log(`✅ Awarded challenge badge to user ${userId}`);
  } catch (error) {
    console.error(`Error awarding badge to user ${userId}:`, error);
  }
}

/**
 * Post to graduation wall
 */
async function postToGraduationWall(
  userId: string,
  challengeId: string,
  challengeTitle: string,
  charityName: string,
  amountRaised: number,
  circleName: string
): Promise<void> {
  try {
    const userDoc = await db.collection('users').doc(userId).get();
    const userName = userDoc.data()?.name || 'Someone';

    const graduationPost = {
      userId,
      userName,
      type: 'challenge_completion',
      title: challengeTitle,
      message: `${circleName} raised $${amountRaised} for ${charityName}!`,
      challengeId,
      charityName,
      amountRaised,
      circleName,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    await db.collection('graduationWall').add(graduationPost);
    console.log(`✅ Posted to graduation wall for user ${userId}`);
  } catch (error) {
    console.error(`Error posting to graduation wall:`, error);
  }
}

/**
 * Send notification when challenge is completed
 */
async function sendChallengeCompletionNotification(
  userId: string,
  challengeTitle: string,
  charityName: string,
  amountRaised: number,
  circleName: string
): Promise<void> {
  try {
    const { Expo } = await import('expo-server-sdk');
    const expo = new Expo();

    const userDoc = await db.collection('users').doc(userId).get();
    if (!userDoc.exists) return;

    const userData = userDoc.data();
    if (!userData?.expoPushToken) return;

    const pushToken = userData.expoPushToken;
    if (!Expo.isExpoPushToken(pushToken)) return;

    const notification = {
      to: pushToken,
      sound: 'default',
      title: '🎉 Challenge Complete!',
      body: `Your circle raised $${amountRaised} for ${charityName}!`,
      data: {
        type: 'challenge_completion',
        deepLink: 'coachie://graduation_wall',
      },
      priority: 'default',
    };

    await expo.sendPushNotificationsAsync([notification]);
  } catch (error) {
    console.error(`Error sending challenge completion notification:`, error);
  }
}

