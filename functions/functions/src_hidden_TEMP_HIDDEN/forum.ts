import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';

// Initialize admin if not already initialized
if (!admin.apps.length) {
  admin.initializeApp();
}
const db = admin.firestore();

export const ensureForumChannels = functions.https.onCall(async (_data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'Must be signed in.');
  }

  const channels = [
    { id: 'coachie-news', name: 'Coachie News', description: 'App updates, new feature announcements, roadmaps, and development updates from the Coachie team' },
    { id: 'feature-requests', name: 'Feature Requests', description: 'Ideas and requests for new features' },
    { id: 'bugs-feedback', name: 'Bugs & Feedback', description: 'Report bugs and give feedback' },
    { id: 'general', name: 'General Discussion', description: 'Chat with the community' },
    { id: 'recipes', name: 'Recipe Sharing', description: 'Share your favorite recipes, meal ideas, and nutrition tips with the community' }
  ];

  const batch = db.batch();
  for (const ch of channels) {
    const ref = db.collection('forum_channels').doc(ch.id);
    const snap = await ref.get();
    if (!snap.exists) {
      batch.set(ref, {
        name: ch.name,
        description: ch.description,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        threadsCount: 0,
        postsCount: 0
      });
    }
  }
  await batch.commit();
  return { ok: true };
});

export const createThread = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Must be signed in.');
  const { channelId, title, content } = data || {};
  if (!channelId || !title || !content) {
    throw new functions.https.HttpsError('invalid-argument', 'channelId, title, content required.');
  }
  const uid = context.auth.uid;
  const threadRef = await db.collection('forum_channels').doc(channelId).collection('threads').add({
    title,
    createdBy: uid,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    lastPostAt: admin.firestore.FieldValue.serverTimestamp(),
    postsCount: 1
  });
  await threadRef.collection('posts').add({
    content,
    createdBy: uid,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    replies: 0
  });
  await db.collection('forum_channels').doc(channelId).set({
    threadsCount: admin.firestore.FieldValue.increment(1),
    postsCount: admin.firestore.FieldValue.increment(1),
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  }, { merge: true });
  return { ok: true, threadId: threadRef.id };
});

export const replyToThread = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Must be signed in.');
  const { channelId, threadId, content } = data || {};
  if (!channelId || !threadId || !content) {
    throw new functions.https.HttpsError('invalid-argument', 'channelId, threadId, content required.');
  }
  const uid = context.auth.uid;
  const postRef = db.collection('forum_channels').doc(channelId).collection('threads').doc(threadId).collection('posts').doc();
  await postRef.set({
    content,
    createdBy: uid,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  });
  const threadRef = db.collection('forum_channels').doc(channelId).collection('threads').doc(threadId);
  await threadRef.set({
    postsCount: admin.firestore.FieldValue.increment(1),
    lastPostAt: admin.firestore.FieldValue.serverTimestamp(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  }, { merge: true });
  await db.collection('forum_channels').doc(channelId).set({
    postsCount: admin.firestore.FieldValue.increment(1),
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  }, { merge: true });
  return { ok: true, postId: postRef.id };
});


