// MINIMAL ENTRY POINT FOR BRIEF FUNCTIONS ONLY
// Deploy with: firebase deploy --only functions --force
// (temporarily set package.json main to "index-briefs-only.js")

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

// Import scheduled brief functions (lazy load to avoid timeout)
let scheduledBriefs;
try {
  scheduledBriefs = require('./lib/scheduledBriefs');
  console.log('✅ scheduledBriefs imported');
} catch (error) {
  console.error('❌ Error importing scheduledBriefs:', error);
  throw error;
}

// Import brief task queue
let briefTaskQueue;
try {
  briefTaskQueue = require('./lib/briefTaskQueue');
  console.log('✅ briefTaskQueue imported');
} catch (error) {
  console.error('❌ Error importing briefTaskQueue:', error);
  throw error;
}

// Export scheduled brief functions
exports.sendMorningBriefs = scheduledBriefs.sendMorningBriefs;
exports.sendAfternoonBriefs = scheduledBriefs.sendAfternoonBriefs;
exports.sendEveningBriefs = scheduledBriefs.sendEveningBriefs;

// Export Cloud Tasks worker
exports.processBriefTask = briefTaskQueue.processBriefTask;

// Export manual trigger functions
exports.triggerMorningBrief = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Admin required');
  const { sendBriefsToAllUsers } = require('./lib/scheduledBriefs');
  console.log('🧪 Manually triggering morning briefs');
  return await sendBriefsToAllUsers('morning');
});

exports.triggerAfternoonBrief = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Admin required');
  const { sendBriefsToAllUsers } = require('./lib/scheduledBriefs');
  console.log('🧪 Manually triggering afternoon briefs');
  return await sendBriefsToAllUsers('afternoon');
});

exports.triggerEveningBrief = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Admin required');
  const { sendBriefsToAllUsers } = require('./lib/scheduledBriefs');
  console.log('🧪 Manually triggering evening briefs');
  return await sendBriefsToAllUsers('evening');
});

console.log('✅ Brief functions exported');
