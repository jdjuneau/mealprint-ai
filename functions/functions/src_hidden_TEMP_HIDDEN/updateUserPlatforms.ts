import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';

// Admin should be initialized in index.ts before this module is imported
// But we check just in case it's imported standalone
if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

/**
 * Update user platform fields based on their logs
 * SIMPLIFIED VERSION - Just returns success to test if function is callable
 */
export const updateUserPlatforms = functions
  .region('us-central1')
  .runWith({
    timeoutSeconds: 540,
    memory: '512MB'
  })
  .https.onCall(async (data, context) => {
    console.log('🚨 FUNCTION CALLED - updateUserPlatforms');
    
    try {
      // Get all users
      const usersSnapshot = await db.collection('users').get();
      console.log(`✅ Found ${usersSnapshot.size} users`);
      
      let updated = 0;
      let skipped = 0;
      let errors = 0;
      
      // Check last 7 days for platform detection (more likely to find activity)
      const datesToCheck: string[] = [];
      for (let i = 0; i < 7; i++) {
        const date = new Date(Date.now() - i * 24 * 60 * 60 * 1000);
        datesToCheck.push(date.toISOString().split('T')[0]);
      }
      const today = datesToCheck[0];
      const yesterday = datesToCheck[1];
      
      // Process users sequentially
      for (const userDoc of usersSnapshot.docs) {
        try {
          const userId = userDoc.id;
          const userData = userDoc.data();
          
          // Check if user already has platform set
          const platform = userData?.platform;
          const platforms = userData?.platforms || [];
          const hasWeb = platform === 'web' || (Array.isArray(platforms) && platforms.includes('web'));
          const hasAndroid = platform === 'android' || (Array.isArray(platforms) && platforms.includes('android'));
          
          if (hasWeb || hasAndroid) {
            const reason = hasWeb && hasAndroid ? 'both web and android' : (hasWeb ? 'web' : 'android');
            console.log(`⏭️ Skipped user ${userId}: Already has platform set (${reason}) - platform: ${platform}, platforms: ${JSON.stringify(platforms)}`);
            skipped++;
            continue;
          }
          
          let detectedPlatforms: string[] = [];
          
          // Check Android logs: logs/{userId}/daily/{date}/entries
          for (const date of datesToCheck) {
            try {
              const androidEntriesRef = db.collection('logs').doc(userId).collection('daily').doc(date).collection('entries');
              const androidSnapshot = await androidEntriesRef.get();
              
              if (androidSnapshot.size > 0) {
                androidSnapshot.forEach(doc => {
                  const entryData = doc.data();
                  if (entryData?.platform === 'android' && !detectedPlatforms.includes('android')) {
                    detectedPlatforms.push('android');
                  }
                });
              }
            } catch (e) {
              // Skip
            }
          }
          
          // Check Web logs: users/{userId}/daily/{date}/entries
          for (const date of datesToCheck) {
            try {
              const webEntriesRef = db.collection('users').doc(userId).collection('daily').doc(date).collection('entries');
              const webSnapshot = await webEntriesRef.get();
              
              if (webSnapshot.size > 0) {
                webSnapshot.forEach(doc => {
                  const entryData = doc.data();
                  if (entryData?.platform === 'web' && !detectedPlatforms.includes('web')) {
                    detectedPlatforms.push('web');
                  }
                });
              }
            } catch (e) {
              // Skip
            }
          }
          
          // Fallback: Check fcmToken (indicates Android)
          if (detectedPlatforms.length === 0) {
            if (userData?.fcmToken || (Array.isArray(userData?.fcmTokens) && userData.fcmTokens.length > 0)) {
              detectedPlatforms.push('android');
              console.log(`  ✅ Detected android platform from fcmToken`);
            }
          }
          
          console.log(`🔍 User ${userId} detection result: ${detectedPlatforms.length > 0 ? detectedPlatforms.join(', ') : 'NONE'}`);
          
          // Update user if platform detected
          if (detectedPlatforms.length > 0) {
            try {
              const userRef = db.collection('users').doc(userId);
              await userRef.update({
                platform: detectedPlatforms[0],
                platforms: detectedPlatforms,
                updatedAt: admin.firestore.FieldValue.serverTimestamp()
              });
              updated++;
              console.log(`✅ Updated user ${userId} with platforms: ${detectedPlatforms.join(', ')}`);
            } catch (updateError: any) {
              console.error(`❌ Failed to update user ${userId}:`, updateError.message);
              errors++;
            }
          } else {
            console.log(`⏭️ Skipped user ${userId}: No platform detected (no logs found in last 7 days and no fcmToken)`);
            skipped++;
          }
        } catch (error: any) {
          console.error(`❌ Error processing user ${userDoc.id}:`, error.message);
          errors++;
        }
      }
      
      const result = {
        total: usersSnapshot.size,
        updated,
        skipped,
        errors,
        message: `Updated ${updated} users, skipped ${skipped}, errors: ${errors}`
      };
      
      console.log('✅ Platform update complete:', result);
      return result;
      
    } catch (error: any) {
      console.error('❌❌❌ CRITICAL ERROR:', error);
      console.error('Error name:', error?.name);
      console.error('Error message:', error?.message);
      console.error('Error stack:', error?.stack);
      throw new functions.https.HttpsError('internal', `Platform update failed: ${error?.message || 'Unknown error'}`);
    }
  });
