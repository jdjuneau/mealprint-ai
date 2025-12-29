// STANDALONE BRIEF FUNCTIONS - NO DEPENDENCIES ON MAIN INDEX
// This file can be deployed independently without timeout issues

const functions = require('firebase-functions');
const admin = require('firebase-admin');

if (!admin.apps.length) {
  admin.initializeApp();
}

// Import the sendBriefsToAllUsers function directly from compiled lib
// This function handles all the brief generation logic
const scheduledBriefs = require('../lib/scheduledBriefs');

// Export HTTP endpoints that Cloud Scheduler can call
exports.sendMorningBriefs = functions.https.onRequest(async (req, res) => {
  try {
    console.log('🌅 Sending morning briefs via HTTP trigger');
    const result = await scheduledBriefs.sendBriefsToAllUsers('morning');
    res.status(200).json({ success: true, ...result });
  } catch (error) {
    console.error('Morning briefs error:', error);
    res.status(500).json({ error: error.message || 'Failed to send morning briefs' });
  }
});

exports.sendAfternoonBriefs = functions.https.onRequest(async (req, res) => {
  try {
    console.log('☀️ Sending afternoon briefs via HTTP trigger');
    const result = await scheduledBriefs.sendBriefsToAllUsers('afternoon');
    res.status(200).json({ success: true, ...result });
  } catch (error) {
    console.error('Afternoon briefs error:', error);
    res.status(500).json({ error: error.message || 'Failed to send afternoon briefs' });
  }
});

exports.sendEveningBriefs = functions.https.onRequest(async (req, res) => {
  try {
    console.log('🌙 Sending evening briefs via HTTP trigger');
    const result = await scheduledBriefs.sendBriefsToAllUsers('evening');
    res.status(200).json({ success: true, ...result });
  } catch (error) {
    console.error('Evening briefs error:', error);
    res.status(500).json({ error: error.message || 'Failed to send evening briefs' });
  }
});
