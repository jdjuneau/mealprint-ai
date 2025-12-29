// GEN2 CLOUD FUNCTION ENTRY POINT
// This is specifically for gcloud functions deploy --gen2
// It properly exports the function for Cloud Run

const functions = require('firebase-functions/v2');
const admin = require('firebase-admin');

if (!admin.apps.length) {
  admin.initializeApp();
}

// Import the function from briefTaskQueue
const briefTaskQueue = require('./lib/briefTaskQueue');

// Export using Gen2 syntax
exports.processBriefTask = functions
  .runWith({
    timeoutSeconds: 540,
    memory: '512MiB',
  })
  .region('us-central1')
  .https
  .onRequest(briefTaskQueue.processBriefTask);
