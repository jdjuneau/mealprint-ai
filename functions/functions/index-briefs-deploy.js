// MINIMAL DEPLOYMENT FILE FOR BRIEF FUNCTIONS
// This exports ONLY the brief functions to avoid deployment timeouts

const functions = require('firebase-functions');
const admin = require('firebase-admin');

// Initialize Firebase Admin
if (!admin.apps.length) {
  admin.initializeApp();
}

// Import brief functions (lazy load to avoid timeouts)
const scheduledBriefs = require('./lib/scheduledBriefs');
const briefTaskQueue = require('./lib/briefTaskQueue');

// Export scheduled functions
exports.sendMorningBriefs = scheduledBriefs.sendMorningBriefs;
exports.sendAfternoonBriefs = scheduledBriefs.sendAfternoonBriefs;
exports.sendEveningBriefs = scheduledBriefs.sendEveningBriefs;

// Export Cloud Tasks worker
exports.processBriefTask = briefTaskQueue.processBriefTask;

console.log('✅ Brief functions ready for deployment');