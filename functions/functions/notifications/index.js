// NOTIFICATIONS DOMAIN - Dedicated Firebase Functions Project
// Deploy separately from main functions

const functions = require('firebase-functions');
const admin = require('firebase-admin');

if (!admin.apps.length) {
  admin.initializeApp();
}

// NOTIFICATION FUNCTIONS
exports.onCirclePostCreated = require('../lib/notifications').onCirclePostCreated;
exports.onCirclePostLiked = require('../lib/notifications').onCirclePostLiked;
exports.onFriendRequestCreated = require('../lib/messageNotifications').onFriendRequestCreated;
exports.onMessageCreated = require('../lib/messageNotifications').onMessageCreated;
exports.onCirclePostCreatedFCM = require('../lib/messageNotifications').onCirclePostCreated;
exports.onCirclePostCommented = require('../lib/messageNotifications').onCirclePostCommented;