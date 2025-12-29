// BRIEFS DOMAIN - Dedicated Firebase Functions Project
// Deploy separately from main functions
// CRITICAL: These use functions.pubsub.schedule for AUTOMATIC daily execution
// No Cloud Scheduler needed - Firebase handles scheduling automatically

const functions = require('firebase-functions');
const admin = require('firebase-admin');

if (!admin.apps.length) {
  admin.initializeApp();
}

// Import brief functions
const scheduledBriefs = require('../lib/scheduledBriefs');
const briefTaskQueue = require('../lib/briefTaskQueue');
const { sendBriefsToAllUsers } = scheduledBriefs;

// SCHEDULED BRIEF FUNCTIONS - Use Firebase pubsub.schedule for AUTOMATIC execution
// These now use Cloud Tasks for scalable processing
// These run automatically at the specified times - NO Cloud Scheduler needed!
exports.sendMorningBriefs = scheduledBriefs.sendMorningBriefs;
exports.sendAfternoonBriefs = scheduledBriefs.sendAfternoonBriefs;
exports.sendEveningBriefs = scheduledBriefs.sendEveningBriefs;

// CLOUD TASKS WORKER - Processes individual brief tasks
exports.processBriefTask = briefTaskQueue.processBriefTask;

// MANUAL BRIEF FUNCTIONS - For testing/admin
exports.triggerMorningBrief = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Admin required');
  const { sendBriefsToAllUsers } = require('../lib/scheduledBriefs');
  console.log('🧪 Manually triggering morning briefs');
  return await sendBriefsToAllUsers('morning');
});

exports.triggerAfternoonBrief = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Admin required');
  const { sendBriefsToAllUsers } = require('../lib/scheduledBriefs');
  console.log('🧪 Manually triggering afternoon briefs');
  return await sendBriefsToAllUsers('afternoon');
});

exports.triggerEveningBrief = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError('unauthenticated', 'Admin required');
  const { sendBriefsToAllUsers } = require('../lib/scheduledBriefs');
  console.log('🧪 Manually triggering evening briefs');
  return await sendBriefsToAllUsers('evening');
});