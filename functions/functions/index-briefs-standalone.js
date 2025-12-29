// STANDALONE BRIEF FUNCTIONS DEPLOYMENT
// This file ONLY exports the scheduled brief functions to avoid Firebase CLI timeout
// Deploy with: firebase deploy --only functions:sendMorningBriefs,functions:sendAfternoonBriefs,functions:sendEveningBriefs --force

const functions = require('firebase-functions');
const admin = require('firebase-admin');

if (!admin.apps.length) {
  admin.initializeApp();
}

// Import scheduled brief functions from compiled lib
const scheduledBriefs = require('./lib/scheduledBriefs');

// Export ONLY the scheduled brief functions
exports.sendMorningBriefs = scheduledBriefs.sendMorningBriefs;
exports.sendAfternoonBriefs = scheduledBriefs.sendAfternoonBriefs;
exports.sendEveningBriefs = scheduledBriefs.sendEveningBriefs;

