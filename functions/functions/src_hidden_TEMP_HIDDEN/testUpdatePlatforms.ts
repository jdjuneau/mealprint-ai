import * as functions from 'firebase-functions';

export const testUpdatePlatforms = functions
  .region('us-central1')
  .https.onCall(async (data, context) => {
    console.log('✅ TEST FUNCTION CALLED');
    return { success: true, message: 'Test function works!' };
  });

