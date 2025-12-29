const functions = require('firebase-functions');

exports.testFunction = functions.https.onCall(async (data, context) => {
  return { message: 'Test function works!', timestamp: new Date().toISOString() };
});