// SOCIAL DOMAIN - Dedicated Firebase Functions Project
// Deploy separately from main functions

const functions = require('firebase-functions');
const admin = require('firebase-admin');

if (!admin.apps.length) {
  admin.initializeApp();
}

// SOCIAL FORUM FUNCTIONS
exports.ensureForumChannels = require('../lib/forum').ensureForumChannels;
exports.createThread = require('../lib/forum').createThread;
exports.replyToThread = require('../lib/forum').replyToThread;
exports.getUserQuests = require('../lib/getUserQuests').getUserQuests;
exports.updateQuestProgress = require('../lib/getUserQuests').updateQuestProgress;
exports.completeQuest = require('../lib/getUserQuests').completeQuest;
exports.resetQuests = require('../lib/getUserQuests').resetQuests;