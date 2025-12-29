// STANDALONE GOOGLE PLAY FUNCTIONS - NO DEPENDENCIES ON MAIN INDEX
// This file can be deployed independently without timeout issues

const functions = require('firebase-functions');
const admin = require('firebase-admin');

if (!admin.apps.length) {
  admin.initializeApp();
}

// Import the functions directly from compiled lib
const googlePlayRTDN = require('../lib/googlePlayRTDN');
const verifyPurchase = require('../lib/verifyPurchase');

// Export the functions
exports.processGooglePlayRTDN = googlePlayRTDN.processGooglePlayRTDN;
exports.verifyPurchase = verifyPurchase.verifyPurchase;
