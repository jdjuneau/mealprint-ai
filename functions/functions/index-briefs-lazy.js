// ULTRA-MINIMAL ENTRY POINT WITH LAZY LOADING
// This avoids deployment timeouts by loading modules only when functions are called
// Deploy with: firebase deploy --only functions --force
// (temporarily set package.json main to "index-briefs-lazy.js")

const functions = require('firebase-functions');
const admin = require('firebase-admin');

// Initialize Firebase Admin
if (!admin.apps.length) {
  try {
    admin.initializeApp();
    console.log('✅ Firebase Admin initialized');
  } catch (error) {
    console.error('❌ Firebase Admin initialization error:', error);
  }
}

// LAZY LOAD: Only require modules when functions are actually called
// This allows Firebase CLI to analyze exports without loading heavy dependencies

// Morning briefs - 9 AM Eastern Time
exports.sendMorningBriefs = functions.pubsub
  .schedule('0 9 * * *')
  .timeZone('America/New_York')
  .onRun(async (context) => {
    // Lazy load the actual implementation
    const scheduledBriefs = require('./lib/scheduledBriefs');
    return await scheduledBriefs.sendMorningBriefs.run(context);
  });

// Afternoon briefs - 2 PM Eastern Time (Pro users only)
exports.sendAfternoonBriefs = functions.pubsub
  .schedule('0 14 * * *')
  .timeZone('America/New_York')
  .onRun(async (context) => {
    // Lazy load the actual implementation
    const scheduledBriefs = require('./lib/scheduledBriefs');
    return await scheduledBriefs.sendAfternoonBriefs.run(context);
  });

// Evening briefs - 6 PM Eastern Time (Pro users only)
exports.sendEveningBriefs = functions.pubsub
  .schedule('0 18 * * *')
  .timeZone('America/New_York')
  .onRun(async (context) => {
    // Lazy load the actual implementation
    const scheduledBriefs = require('./lib/scheduledBriefs');
    return await scheduledBriefs.sendEveningBriefs.run(context);
  });

// Cloud Tasks worker
exports.processBriefTask = functions.https.onRequest(async (req, res) => {
  // Lazy load the actual implementation
  const briefTaskQueue = require('./lib/briefTaskQueue');
  return await briefTaskQueue.processBriefTask(req, res);
});

// Manual trigger functions
exports.triggerMorningBrief = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Admin required');
  const scheduledBriefs = require('./lib/scheduledBriefs');
  const { sendBriefsToAllUsers } = scheduledBriefs;
  console.log('🧪 Manually triggering morning briefs');
  return await sendBriefsToAllUsers('morning');
});

exports.triggerAfternoonBrief = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Admin required');
  const scheduledBriefs = require('./lib/scheduledBriefs');
  const { sendBriefsToAllUsers } = scheduledBriefs;
  console.log('🧪 Manually triggering afternoon briefs');
  return await sendBriefsToAllUsers('afternoon');
});

exports.triggerEveningBrief = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Admin required');
  const scheduledBriefs = require('./lib/scheduledBriefs');
  const { sendBriefsToAllUsers } = scheduledBriefs;
  console.log('🧪 Manually triggering evening briefs');
  return await sendBriefsToAllUsers('evening');
});

console.log('✅ Brief functions exported (lazy loading)');
