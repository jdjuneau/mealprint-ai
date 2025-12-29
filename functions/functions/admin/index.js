// ADMIN DOMAIN - Dedicated Firebase Functions Project
// Deploy separately from main functions

const functions = require('firebase-functions');
const admin = require('firebase-admin');

if (!admin.apps.length) {
  admin.initializeApp();
}

// ADMIN UTILITY FUNCTIONS
exports.migrateUserUsernames = require('../lib/migrateUsers').migrateUserUsernames;
exports.updateUserPlatforms = require('../lib/updateUserPlatforms').updateUserPlatforms;
exports.testUpdatePlatforms = require('../lib/testUpdatePlatforms').testUpdatePlatforms;
exports.generateWeeklyShoppingList = require('../lib/generateWeeklyShoppingList').generateWeeklyShoppingList;
exports.generateWeeklyBlueprint = require('../lib/generateWeeklyShoppingList').generateWeeklyBlueprint;
exports.exportUserData = require('../lib/exportUserData').exportUserData;
exports.grantTestSubscription = require('../lib/grantTestSubscription').grantTestSubscription;
exports.grantProToAllExistingUsers = require('../lib/grantTestSubscription').grantProToAllExistingUsers;
exports.createMonthlySnapshot = require('../lib/createDashboardSnapshot').createMonthlySnapshot;
exports.createWeeklySnapshot = require('../lib/createDashboardSnapshot').createWeeklySnapshot;
exports.createSnapshotManual = require('../lib/createDashboardSnapshot').createSnapshotManual;

// LEGACY FUNCTIONS
exports.testNudge = functions.https.onCall(async (data, context) => {
  const { getUsersWithNudgesEnabled, sendTimedNudgeToUser } = require('../lib/testNudgeHelpers');
  const timeOfDay = data?.timeOfDay || 'morning';
  const usersWithNudges = await getUsersWithNudgesEnabled();
  const results = await Promise.allSettled(
    usersWithNudges.map(user => sendTimedNudgeToUser(user, timeOfDay))
  );
  return {
    success: true,
    timeOfDay,
    totalUsers: usersWithNudges.length,
    successful: results.filter(r => r.status === 'fulfilled').length,
    failed: results.filter(r => r.status === 'rejected').length,
  };
});