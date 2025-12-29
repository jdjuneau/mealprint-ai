// Mealprint AI Firebase Functions
// Core functions for meal planning
const admin = require('firebase-admin');
if (!admin.apps.length) {
  admin.initializeApp();
}

// Blueprint generation - the core meal planning function
const generateWeeklyShoppingList = require('./lib/generateWeeklyShoppingList');
exports.generateWeeklyBlueprint = generateWeeklyShoppingList.generateWeeklyBlueprint;

// Basic subscription verification
const subscriptionVerification = require('./lib/subscriptionVerification');
exports.verifySubscription = subscriptionVerification.verifySubscription;

// User data export (for GDPR compliance)
const exportUserData = require('./lib/exportUserData');
exports.exportUserData = exportUserData.exportUserData;
