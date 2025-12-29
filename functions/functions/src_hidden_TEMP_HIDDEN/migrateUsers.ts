import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

/**
 * Migrate existing users to set username from name if username is missing
 * This is a one-time migration function
 * Can be called from your app or Firebase Console
 */
export const migrateUserUsernames = functions.https.onCall(async (data, context) => {
  // Allow unauthenticated calls for one-time migration
  // No auth check - anyone can call this function

  try {
    console.log('Starting user migration: setting username from name for existing users');

    // Get all users
    const usersSnapshot = await db.collection('users').get();
    console.log(`Found ${usersSnapshot.size} users to check`);

    let updated = 0;
    let skipped = 0;
    let errors = 0;

    let batch = db.batch();
    let batchCount = 0;
    const BATCH_SIZE = 500; // Firestore batch limit

    for (const userDoc of usersSnapshot.docs) {
      try {
        const userData = userDoc.data();
        const name = userData.name || '';
        const username = userData.username || '';

        // Skip if user already has a username
        if (username && username.trim().length > 0) {
          skipped++;
          continue;
        }

        // Skip if user doesn't have a name
        if (!name || name.trim().length === 0) {
          skipped++;
          continue;
        }

        // Set username = name (lowercase, no spaces)
        const newUsername = name.trim().toLowerCase().replace(/\s+/g, '');
        
        if (newUsername.length > 0) {
          const userRef = db.collection('users').doc(userDoc.id);
          batch.update(userRef, { username: newUsername });
          batchCount++;
          updated++;

          // Commit batch if we've reached the limit and create a new batch
          if (batchCount >= BATCH_SIZE) {
            await batch.commit();
            console.log(`Committed batch of ${batchCount} updates`);
            batch = db.batch(); // Create new batch
            batchCount = 0;
          }
        } else {
          skipped++;
        }
      } catch (error: any) {
        console.error(`Error processing user ${userDoc.id}:`, error);
        errors++;
      }
    }

    // Commit any remaining updates
    if (batchCount > 0) {
      await batch.commit();
      console.log(`Committed final batch of ${batchCount} updates`);
    }

    const result = {
      total: usersSnapshot.size,
      updated,
      skipped,
      errors,
      message: `Migration complete: Updated ${updated} users, skipped ${skipped}, errors: ${errors}`
    };

    console.log(result.message);
    return result;
  } catch (error: any) {
    console.error('Migration failed:', error);
    throw new functions.https.HttpsError('internal', `Migration failed: ${error.message}`);
  }
});

