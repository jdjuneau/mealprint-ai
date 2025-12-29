// MINIMAL ENTRY POINT FOR gcloud functions deploy --gen2
// This exports ONLY processBriefTask for Gen2 deployment

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

// Import the compiled briefTaskQueue
let briefTaskQueue;
try {
  briefTaskQueue = require('./lib/briefTaskQueue');
  console.log('✅ briefTaskQueue imported');
} catch (error) {
  console.error('❌ Error importing briefTaskQueue:', error);
  throw error;
}

// Export the function directly (it's already fully configured)
if (!briefTaskQueue || !briefTaskQueue.processBriefTask) {
  throw new Error('processBriefTask not found in briefTaskQueue');
}

exports.processBriefTask = briefTaskQueue.processBriefTask;
console.log('✅ processBriefTask exported');
