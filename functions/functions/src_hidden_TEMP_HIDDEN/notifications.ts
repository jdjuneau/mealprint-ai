import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';

// Initialize admin if not already initialized
if (!admin.apps.length) {
  admin.initializeApp();
}
const db = admin.firestore();

async function getUserFriends(userId: string): Promise<string[]> {
  const snap = await db.collection('users').doc(userId).collection('friends').get();
  return snap.docs.map(d => d.id);
}

async function getUserPushToken(userId: string): Promise<string | null> {
  try {
    const userDoc = await db.collection('users').doc(userId).get();
    const token = userDoc.get('expoPushToken');
    return token || null;
  } catch {
    return null;
  }
}

async function sendExpoNotification(token: string, title: string, body: string, data?: any) {
  // Barebones FCM via Expo Push (for illustration; production should batch)
  await fetch('https://exp.host/--/api/v2/push/send', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ to: token, title, body, data }),
  });
}

export const onCirclePostCreated = functions.firestore
  .document('circles/{circleId}/feed/{postId}')
  .onCreate(async (snap, context) => {
    const data = snap.data();
    const author = data.createdBy as string;
    const content = (data.content as string) || '';
    const friends = await getUserFriends(author);
    await Promise.all(friends.map(async (friendUid) => {
      const token = await getUserPushToken(friendUid);
      if (token) {
        await sendExpoNotification(token, 'Friend posted in a circle', content.slice(0, 120));
      }
    }));
  });

export const onCirclePostLiked = functions.firestore
  .document('circles/{circleId}/feed/{postId}')
  .onUpdate(async (change, context) => {
    const before = change.before.data();
    const after = change.after.data();
    const beforeLikes: string[] = before.likes || [];
    const afterLikes: string[] = after.likes || [];
    if (afterLikes.length <= beforeLikes.length) return;
    // New like added
    const newLiker = afterLikes.find((l: string) => !beforeLikes.includes(l));
    const owner = after.createdBy as string;
    if (!newLiker || !owner || owner === newLiker) return;
    const token = await getUserPushToken(owner);
    if (token) {
      await sendExpoNotification(token, 'Your post got a like', 'Someone liked your circle post!');
    }
  });


