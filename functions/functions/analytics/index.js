// ANALYTICS DOMAIN - Dedicated Firebase Functions Project
// Deploy separately from main functions

const functions = require('firebase-functions');
const admin = require('firebase-admin');

if (!admin.apps.length) {
  admin.initializeApp();
}

// ANALYTICS FUNCTIONS
exports.calculateReadinessScore = require('../lib/readinessScore').calculateReadinessScore;
exports.getEnergyScoreHistory = require('../lib/readinessScore').getEnergyScoreHistory;
exports.getScoreHistory = require('../lib/getScoreHistory').getScoreHistory;
exports.generateMonthlyInsights = require('../lib/generateMonthlyInsights').generateMonthlyInsights;
exports.generateUserInsights = require('../lib/generateMonthlyInsights').generateUserInsights;
exports.archiveOldInsights = require('../lib/generateMonthlyInsights').archiveOldInsights;
exports.logOpenAIUsageEvent = require('../lib/usage').logOpenAIUsageEvent;