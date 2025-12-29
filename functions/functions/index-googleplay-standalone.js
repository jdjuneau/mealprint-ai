// Minimal standalone entry point for Google Play functions
// Use this file to deploy processGooglePlayRTDN and verifyPurchase manually
// 
// To deploy:
// 1. Temporarily rename this to index.js (backup your current index.js first!)
// 2. firebase deploy --only functions:processGooglePlayRTDN,functions:verifyPurchase
// 3. Restore your original index.js

const functions = require('firebase-functions');
const admin = require('firebase-admin');

if (!admin.apps.length) {
  admin.initializeApp();
}

// Import Google Play RTDN handler
const googlePlayRTDN = require('./lib/googlePlayRTDN');
exports.processGooglePlayRTDN = googlePlayRTDN.processGooglePlayRTDN;

// Import verifyPurchase
const verifyPurchase = require('./lib/verifyPurchase');
exports.verifyPurchase = verifyPurchase.verifyPurchase;
