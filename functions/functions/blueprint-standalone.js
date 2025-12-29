// STANDALONE BLUEPRINT FUNCTION - DEPLOY THIS DIRECTLY
// This file exports ONLY generateWeeklyBlueprint to avoid deployment timeouts

const functions = require('firebase-functions');

exports.generateWeeklyBlueprint = functions.https.onCall(async (data, context) => {
  const generateWeeklyShoppingList = require('./lib/generateWeeklyShoppingList');
  return await generateWeeklyShoppingList.generateWeeklyBlueprint(data, context);
});

