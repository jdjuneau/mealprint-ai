// HTTP WRAPPER FUNCTIONS FOR BRIEF SCHEDULING
// These HTTP functions can be called by Cloud Scheduler
// Deploy via gcloud CLI to avoid Firebase CLI timeout

const functions = require('firebase-functions');
const admin = require('firebase-admin');

if (!admin.apps.length) {
  admin.initializeApp();
}

// HTTP wrapper for morning briefs (9 AM Eastern)
exports.sendMorningBriefsHttp = functions.https.onRequest(async (req, res) => {
  try {
    console.log('🌅 Morning brief HTTP endpoint called');
    // Lazy load to avoid timeout during deployment
    const { enqueueBriefTasks } = require('./lib/briefTaskQueue');
    const result = await enqueueBriefTasks('morning');
    console.log('✅ Morning brief tasks enqueued:', JSON.stringify(result));
    res.status(200).json({ success: true, result });
  } catch (error) {
    console.error('❌ Error in morning brief HTTP:', error);
    res.status(500).json({ success: false, error: error.message });
  }
});

// HTTP wrapper for afternoon briefs (2 PM Eastern)
exports.sendAfternoonBriefsHttp = functions.https.onRequest(async (req, res) => {
  try {
    console.log('☀️ Afternoon brief HTTP endpoint called');
    const { enqueueBriefTasks } = require('./lib/briefTaskQueue');
    const result = await enqueueBriefTasks('afternoon');
    console.log('✅ Afternoon brief tasks enqueued:', JSON.stringify(result));
    res.status(200).json({ success: true, result });
  } catch (error) {
    console.error('❌ Error in afternoon brief HTTP:', error);
    res.status(500).json({ success: false, error: error.message });
  }
});

// HTTP wrapper for evening briefs (6 PM Eastern)
exports.sendEveningBriefsHttp = functions.https.onRequest(async (req, res) => {
  try {
    console.log('🌙 Evening brief HTTP endpoint called');
    const { enqueueBriefTasks } = require('./lib/briefTaskQueue');
    const result = await enqueueBriefTasks('evening');
    console.log('✅ Evening brief tasks enqueued:', JSON.stringify(result));
    res.status(200).json({ success: true, result });
  } catch (error) {
    console.error('❌ Error in evening brief HTTP:', error);
    res.status(500).json({ success: false, error: error.message });
  }
});

console.log('✅ HTTP brief functions exported');
