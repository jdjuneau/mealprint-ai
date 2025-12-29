// RECIPES DOMAIN - Dedicated Firebase Functions Project
// Deploy separately from main functions

const functions = require('firebase-functions');
const admin = require('firebase-admin');

if (!admin.apps.length) {
  admin.initializeApp();
}

// RECIPE FUNCTIONS
exports.analyzeRecipe = require('../lib/analyzeRecipe').analyzeRecipe;
exports.verifyPurchase = require('../lib/verifyPurchase').verifyPurchase;
exports.refreshRecipeNutrition = require('../lib/refreshRecipeNutrition').refreshRecipeNutrition;
exports.searchSupplement = require('../lib/searchSupplement').searchSupplement;
exports.generateBrief = require('../lib/generateBrief').generateBrief;