import { onCall } from "firebase-functions/v2/https";
import { getFirestore } from "firebase-admin/firestore";

if (!require('firebase-admin').apps.length) {
  require('firebase-admin').initializeApp();
}

const db = getFirestore();

/**
 * Delete ALL cached briefs for a user
 * This deletes from both the 'briefs' collection and 'cache' collection
 */
export const deleteAllBriefs = onCall(async (request) => {
  const userId = request.auth?.uid;
  if (!userId) {
    throw new Error('User must be authenticated');
  }

  console.log(`[DELETE_BRIEFS] Deleting ALL briefs for user: ${userId}`);

  let deletedCount = 0;

  try {
    // Delete from users/{userId}/briefs collection
    const briefsRef = db.collection('users').doc(userId).collection('briefs');
    const briefsSnapshot = await briefsRef.get();
    
    const briefsBatch = db.batch();
    briefsSnapshot.docs.forEach(doc => {
      briefsBatch.delete(doc.ref);
      deletedCount++;
    });
    await briefsBatch.commit();
    console.log(`[DELETE_BRIEFS] Deleted ${briefsSnapshot.docs.length} briefs from briefs collection`);

    // Delete from users/{userId}/cache collection (morning_brief, afternoon_brief, evening_brief)
    const cacheRef = db.collection('users').doc(userId).collection('cache');
    const cacheSnapshot = await cacheRef.get();
    
    const cacheBatch = db.batch();
    cacheSnapshot.docs.forEach(doc => {
      const docId = doc.id;
      if (docId.includes('brief') || docId.includes('morning') || docId.includes('afternoon') || docId.includes('evening')) {
        cacheBatch.delete(doc.ref);
        deletedCount++;
      }
    });
    await cacheBatch.commit();
    console.log(`[DELETE_BRIEFS] Deleted brief-related cache documents`);

    console.log(`[DELETE_BRIEFS] ✅ Total deleted: ${deletedCount} brief documents`);
    return { success: true, deletedCount };
  } catch (error: any) {
    console.error(`[DELETE_BRIEFS] ❌ Error deleting briefs:`, error);
    throw new Error(`Failed to delete briefs: ${error.message}`);
  }
});

