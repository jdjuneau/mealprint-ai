// MINIMAL SCHEDULED BRIEFS FUNCTIONS - FOR TESTING DEPLOYMENT
// This version doesn't use complex modules to avoid deployment issues

const functions = require('firebase-functions');
const admin = require('firebase-admin');

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

// SIMPLE MORNING BRIEFS - Runs at 9 AM Eastern daily
exports.sendMorningBriefs = functions
  .runWith({
    timeoutSeconds: 60,
    memory: '128MB'
  })
  .pubsub
  .schedule('0 9 * * *')
  .timeZone('America/New_York')
  .onRun(async (context) => {
    console.log('🌅 MINIMAL: Morning brief scheduler triggered at 9 AM');
    console.log('✅ MINIMAL: Function executed successfully');
    return { success: true, message: 'Morning brief scheduler ran' };
  });

// SIMPLE AFTERNOON BRIEFS - Runs at 2 PM Eastern daily
exports.sendAfternoonBriefs = functions
  .runWith({
    timeoutSeconds: 60,
    memory: '128MB'
  })
  .pubsub
  .schedule('0 14 * * *')
  .timeZone('America/New_York')
  .onRun(async (context) => {
    console.log('☀️ MINIMAL: Afternoon brief scheduler triggered at 2 PM');
    console.log('✅ MINIMAL: Function executed successfully');
    return { success: true, message: 'Afternoon brief scheduler ran' };
  });

// SIMPLE EVENING BRIEFS - Runs at 6 PM Eastern daily
exports.sendEveningBriefs = functions
  .runWith({
    timeoutSeconds: 60,
    memory: '128MB'
  })
  .pubsub
  .schedule('0 18 * * *')
  .timeZone('America/New_York')
  .onRun(async (context) => {
    console.log('🌙 MINIMAL: Evening brief scheduler triggered at 6 PM');
    console.log('✅ MINIMAL: Function executed successfully');
    return { success: true, message: 'Evening brief scheduler ran' };
  });

// SIMPLE PROCESS BRIEF TASK
exports.processBriefTask = functions
  .runWith({
    timeoutSeconds: 60,
    memory: '128MB'
  })
  .tasks
  .taskQueue()
  .onDispatch(async (data) => {
    console.log('🔄 MINIMAL: Processing brief task:', data);
    console.log('✅ MINIMAL: Task processed successfully');
    return { success: true, message: 'Task processed' };
  });
