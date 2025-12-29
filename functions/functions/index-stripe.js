// Minimal entry point for createStripeCheckoutSession only
// This file loads fast so Firebase can detect it before timeout
const functions = require('firebase-functions');
const admin = require('firebase-admin');

if (!admin.apps.length) {
  admin.initializeApp();
}

const { createStripeCheckoutSession } = require('./lib/createStripeCheckoutSession');
exports.createStripeCheckoutSession = createStripeCheckoutSession;
