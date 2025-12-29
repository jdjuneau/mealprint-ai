// STANDALONE ENTRY POINT FOR processBriefTask ONLY
// This file exports ONLY processBriefTask to avoid Firebase CLI timeout

const functions = require('firebase-functions');
const admin = require('firebase-admin');

if (!admin.apps.length) {
  admin.initializeApp();
}

// Import ONLY processBriefTask from compiled output
const briefTaskQueue = require('./lib/briefTaskQueue');

// Export ONLY processBriefTask
exports.processBriefTask = briefTaskQueue.processBriefTask;
