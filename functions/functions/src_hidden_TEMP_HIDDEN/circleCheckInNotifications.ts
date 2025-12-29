import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import { Expo, ExpoPushMessage } from 'expo-server-sdk';

// Initialize Firebase Admin (if not already initialized)
if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();
const expo = new Expo();

/**
 * Cloud Function triggered when a circle check-in is created
 * Sends push notifications to all other circle members
 */
export const onCircleCheckIn = functions.firestore
  .document('circles/{circleId}/checkins/{date}/entries/{userId}')
  .onCreate(async (snap, context) => {
    const circleId = context.params.circleId;
    const userId = context.params.userId;
    const checkInData = snap.data();

    try {
      // Get circle data
      const circleDoc = await db.collection('circles').doc(circleId).get();
      if (!circleDoc.exists) {
        console.error(`Circle ${circleId} not found`);
        return;
      }

      const circleData = circleDoc.data();
      const members = circleData?.members || [];

      // Get user's name
      const userDoc = await db.collection('users').doc(userId).get();
      const userName = userDoc.data()?.name || 'Someone';

      // Get energy level
      const energy = checkInData.energy || 5;

      // Create notification message - format: "Sarah checked in at 9/10!"
      const message = `${userName} checked in at ${energy}/10!`;

      // Get push tokens for all other members (exclude the user who checked in)
      const otherMembers = members.filter((memberId: string) => memberId !== userId);

      if (otherMembers.length === 0) {
        console.log(`No other members to notify for circle ${circleId}`);
        return;
      }

      // Get push tokens for all members
      const memberDocs = await Promise.all(
        otherMembers.map((memberId: string) =>
          db.collection('users').doc(memberId).get()
        )
      );

      const pushTokens: string[] = [];
      memberDocs.forEach((doc) => {
        const expoPushToken = doc.data()?.expoPushToken;
        if (expoPushToken && Expo.isExpoPushToken(expoPushToken)) {
          pushTokens.push(expoPushToken);
        }
      });

      if (pushTokens.length === 0) {
        console.log(`No valid push tokens found for circle ${circleId} members`);
        return;
      }

      // Create push messages
      const messages: ExpoPushMessage[] = pushTokens.map((token) => ({
        to: token,
        sound: 'default',
        title: 'Circle Check-in',
        body: message,
        data: {
          type: 'circle_checkin',
          circleId: circleId,
          userId: userId,
          energy: energy,
          deepLink: `coachie://circle_detail/${circleId}`,
        },
        priority: 'default',
        ttl: 86400, // 24 hours
      }));

      // Send notifications
      const chunks = expo.chunkPushNotifications(messages);
      const tickets = [];

      for (const chunk of chunks) {
        try {
          const ticketChunk = await expo.sendPushNotificationsAsync(chunk);
          tickets.push(...ticketChunk);
        } catch (error) {
          console.error('Error sending push notification chunk:', error);
        }
      }

      console.log(`Sent ${tickets.length} push notifications for circle ${circleId} check-in`);
      return { success: true, ticketsCount: tickets.length };

    } catch (error) {
      console.error('Error in onCircleCheckIn:', error);
      throw error;
    }
  });

