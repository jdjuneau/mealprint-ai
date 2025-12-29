// SCHEDULED BRIEF FUNCTIONS ONLY - No processBriefTask
// This file ONLY exports the 3 scheduled functions to avoid deployment conflicts
// Deploy with: firebase deploy --only functions:sendMorningBriefs,functions:sendAfternoonBriefs,functions:sendEveningBriefs

const functions = require('firebase-functions');
const admin = require('firebase-admin');

if (!admin.apps.length) {
  admin.initializeApp();
}

// Import ONLY the scheduled brief functions (not processBriefTask)
const scheduledBriefs = require('../lib/scheduledBriefs');

// Export ONLY the scheduled functions
exports.sendMorningBriefs = scheduledBriefs.sendMorningBriefs;
exports.sendAfternoonBriefs = scheduledBriefs.sendAfternoonBriefs;
exports.sendEveningBriefs = scheduledBriefs.sendEveningBriefs;

