// PAYMENTS DOMAIN - Dedicated Firebase Functions Project
// Deploy separately from main functions

const functions = require('firebase-functions');
const admin = require('firebase-admin');

if (!admin.apps.length) {
  admin.initializeApp();
}

// STRIPE FUNCTIONS
const { createStripeCheckoutSession } = require('../createStripeCheckoutSession');
exports.createStripeCheckoutSession = createStripeCheckoutSession;

// PAYMENT FUNCTIONS
exports.getSubscriptionPlans = require('../lib/payments').getSubscriptionPlans;
exports.createPayPalOrder = require('../lib/payments').createPayPalOrder;
exports.verifyStripePayment = require('../lib/payments').verifyStripePayment;
exports.verifyPayPalPayment = require('../lib/payments').verifyPayPalPayment;
exports.cancelStripeSubscription = require('../lib/payments').cancelStripeSubscription;
exports.cancelPayPalSubscription = require('../lib/payments').cancelPayPalSubscription;
exports.getSubscriptionStatus = require('../lib/payments').getSubscriptionStatus;

// WEBHOOKS
exports.processStripeWebhook = require('../lib/payments').processStripeWebhook;
exports.processPayPalWebhook = require('../lib/payments').processPayPalWebhook;
exports.checkStripeConfig = require('../lib/payments').checkStripeConfig;