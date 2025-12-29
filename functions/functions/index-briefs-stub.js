// STUB ENTRY POINT - Minimal exports for Firebase CLI analysis
// This allows Firebase CLI to see the functions without loading heavy dependencies
// After deployment, the functions will need to be manually updated or redeployed with full code

const functions = require('firebase-functions');
const admin = require('firebase-admin');

if (!admin.apps.length) {
  admin.initializeApp();
}

// Export stub functions - Firebase CLI can analyze these without loading heavy modules
// These will be replaced with actual implementations after initial deployment

exports.sendMorningBriefs = functions.pubsub
  .schedule('0 9 * * *')
  .timeZone('America/New_York')
  .onRun(async (context) => {
    // Lazy load actual implementation
    const scheduledBriefs = require('./lib/scheduledBriefs');
    return scheduledBriefs.sendMorningBriefs.run(context);
  });

exports.sendAfternoonBriefs = functions.pubsub
  .schedule('0 14 * * *')
  .timeZone('America/New_York')
  .onRun(async (context) => {
    const scheduledBriefs = require('./lib/scheduledBriefs');
    return scheduledBriefs.sendAfternoonBriefs.run(context);
  });

exports.sendEveningBriefs = functions.pubsub
  .schedule('0 18 * * *')
  .timeZone('America/New_York')
  .onRun(async (context) => {
    const scheduledBriefs = require('./lib/scheduledBriefs');
    return scheduledBriefs.sendEveningBriefs.run(context);
  });

exports.processBriefTask = functions.https.onRequest(async (req, res) => {
  const briefTaskQueue = require('./lib/briefTaskQueue');
  return briefTaskQueue.processBriefTask(req, res);
});

exports.triggerMorningBrief = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Admin required');
  const scheduledBriefs = require('./lib/scheduledBriefs');
  const { sendBriefsToAllUsers } = scheduledBriefs;
  return await sendBriefsToAllUsers('morning');
});

exports.triggerAfternoonBrief = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Admin required');
  const scheduledBriefs = require('./lib/scheduledBriefs');
  const { sendBriefsToAllUsers } = scheduledBriefs;
  return await sendBriefsToAllUsers('afternoon');
});

exports.triggerEveningBrief = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Admin required');
  const scheduledBriefs = require('./lib/scheduledBriefs');
  const { sendBriefsToAllUsers } = scheduledBriefs;
  return await sendBriefsToAllUsers('evening');
});
