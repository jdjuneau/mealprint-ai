// Minimal JavaScript entry point for Google Play functions ONLY
// This bypasses TypeScript compilation and loads only the compiled functions
// 
// DEPLOYMENT:
// 1. Temporarily rename: mv lib/index.js lib/index.js.backup
// 2. Copy this file: cp index-googleplay.js lib/index.js  
// 3. Deploy: firebase deploy --only functions:processGooglePlayRTDN,functions:verifyPurchase
// 4. Restore: mv lib/index.js.backup lib/index.js

const functions = require('firebase-functions');
const admin = require('firebase-admin');

if (!admin.apps.length) {
  admin.initializeApp();
}

// Import Google Play RTDN handler (already compiled)
const googlePlayRTDN = require('./lib/googlePlayRTDN');
exports.processGooglePlayRTDN = googlePlayRTDN.processGooglePlayRTDN;

// Import verifyPurchase (already compiled)
const verifyPurchase = require('./lib/verifyPurchase');
exports.verifyPurchase = verifyPurchase.verifyPurchase;
