// SCHEDULED BRIEFS FUNCTIONS - DEPLOY THESE FOR AUTOMATIC EXECUTION
// This file exports only the scheduled Pub/Sub functions that run automatically

const functions = require('firebase-functions');
const admin = require('firebase-admin');
const scheduledBriefs = require('./lib/scheduledBriefs');
const briefTaskQueue = require('./lib/briefTaskQueue');

if (!admin.apps.length) {
  admin.initializeApp();
}

// Use the correct function from briefTaskQueue
const { processBriefTask } = briefTaskQueue;

// MORNING BRIEFS - Runs at 9 AM Eastern daily
exports.sendMorningBriefs = functions
  .runWith({
    timeoutSeconds: 540, // 9 minutes (max for pubsub functions)
    memory: '512MB'
  })
  .pubsub
  .schedule('0 9 * * *') // Every day at 9:00 AM
  .timeZone('America/New_York')
  .onRun(async (context) => {
    console.log('🌅 Starting morning brief task queue at 9 AM');
    const startTime = Date.now();
    try {
      // Use Cloud Tasks for scalable processing
      const result = await briefTaskQueue.enqueueBriefTasks('morning');
      const duration = ((Date.now() - startTime) / 1000).toFixed(2);
      console.log(`✅ Morning brief tasks enqueued in ${duration}s. Result:`, JSON.stringify(result));
      return result;
    }
    catch (error) {
      const duration = ((Date.now() - startTime) / 1000).toFixed(2);
      console.error(`❌ Morning brief task queue failed after ${duration}s:`, error);
      // Fallback to old method if task queue fails
      console.log('⚠️ Falling back to direct processing...');
      const result = await scheduledBriefs.sendBriefsToAllUsers('morning');
      return result;
    }
  });

// AFTERNOON BRIEFS - Runs at 2 PM Eastern daily
exports.sendAfternoonBriefs = functions
  .runWith({
    timeoutSeconds: 540,
    memory: '512MB'
  })
  .pubsub
  .schedule('0 14 * * *') // Every day at 2:00 PM (14:00)
  .timeZone('America/New_York')
  .onRun(async (context) => {
    console.log('☀️ Starting afternoon brief task queue at 2 PM');
    const startTime = Date.now();
    try {
      const result = await briefTaskQueue.enqueueBriefTasks('afternoon');
      const duration = ((Date.now() - startTime) / 1000).toFixed(2);
      console.log(`✅ Afternoon brief tasks enqueued in ${duration}s. Result:`, JSON.stringify(result));
      return result;
    }
    catch (error) {
      const duration = ((Date.now() - startTime) / 1000).toFixed(2);
      console.error(`❌ Afternoon brief task queue failed after ${duration}s:`, error);
      console.log('⚠️ Falling back to direct processing...');
      const result = await scheduledBriefs.sendBriefsToAllUsers('afternoon');
      return result;
    }
  });

// EVENING BRIEFS - Runs at 6 PM Eastern daily
exports.sendEveningBriefs = functions
  .runWith({
    timeoutSeconds: 540,
    memory: '512MB'
  })
  .pubsub
  .schedule('0 18 * * *') // Every day at 6:00 PM (18:00)
  .timeZone('America/New_York')
  .onRun(async (context) => {
    console.log('🌙 Starting evening brief task queue at 6 PM');
    const startTime = Date.now();
    try {
      const result = await briefTaskQueue.enqueueBriefTasks('evening');
      const duration = ((Date.now() - startTime) / 1000).toFixed(2);
      console.log(`✅ Evening brief tasks enqueued in ${duration}s. Result:`, JSON.stringify(result));
      return result;
    }
    catch (error) {
      const duration = ((Date.now() - startTime) / 1000).toFixed(2);
      console.error(`❌ Evening brief task queue failed after ${duration}s:`, error);
      console.log('⚠️ Falling back to direct processing...');
      const result = await scheduledBriefs.sendBriefsToAllUsers('evening');
      return result;
    }
  });

// PROCESS BRIEF TASK - Cloud Tasks worker for individual user processing
// Use the function from briefTaskQueue
exports.processBriefTask = processBriefTask;
