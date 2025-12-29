/**
 * STANDALONE BRIEF FUNCTIONS DEPLOYMENT WITH CLOUD TASKS
 * This file exports ONLY brief-related functions to avoid deployment timeouts
 * Deploy using: firebase deploy --only functions --force
 * (with package.json main pointing to this file)
 */

const functions = require('firebase-functions');
const admin = require('firebase-admin');

if (!admin.apps.length) {
  admin.initializeApp();
}

// Import from compiled TypeScript output
const scheduledBriefs = require('../lib/scheduledBriefs');
const briefTaskQueue = require('../lib/briefTaskQueue');

// Export only brief-related functions
exports.sendMorningBriefs = scheduledBriefs.sendMorningBriefs;
exports.sendAfternoonBriefs = scheduledBriefs.sendAfternoonBriefs;
exports.sendEveningBriefs = scheduledBriefs.sendEveningBriefs;
exports.processBriefTask = briefTaskQueue.processBriefTask;
exports.triggerMorningBrief = scheduledBriefs.triggerMorningBrief;
exports.onNewUserCreated = scheduledBriefs.onNewUserCreated;
