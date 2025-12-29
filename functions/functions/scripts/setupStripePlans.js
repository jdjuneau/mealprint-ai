/**
 * Script to set up Stripe plans in Firestore
 * Run this once to store Price IDs for reliability
 * 
 * Usage: node setupStripePlans.js
 */

const admin = require('firebase-admin');

// Initialize Firebase Admin (you'll need to set GOOGLE_APPLICATION_CREDENTIALS or use service account)
// For now, this is a reference - you can also set these manually in Firestore

const plans = {
  'pro_monthly_stripe': {
    name: 'Pro Monthly',
    price: 9.99,
    currency: 'USD',
    interval: 'month',
    stripePriceId: 'price_1SfRRSCTYzpYqKhFEktdOYtf', // Monthly Price ID
    features: [
      'Unlimited AI features',
      'Weekly meal blueprints',
      'Recipe analysis',
      'Priority support'
    ]
  },
  'pro_yearly_stripe': {
    name: 'Pro Yearly',
    price: 99.99,
    currency: 'USD',
    interval: 'year',
    stripePriceId: 'price_1SfRSCCTYzpYqKhFBu0qpAoi', // Yearly Price ID
    features: [
      'Unlimited AI features',
      'Weekly meal blueprints',
      'Recipe analysis',
      'Priority support',
      'Save 17% vs monthly'
    ]
  }
};

console.log('Stripe Plans Configuration:');
console.log(JSON.stringify(plans, null, 2));
console.log('\nTo set these in Firestore:');
console.log('1. Go to Firebase Console → Firestore Database');
console.log('2. Create collection: subscriptionPlans');
console.log('3. Create documents with IDs: pro_monthly_stripe and pro_yearly_stripe');
console.log('4. Copy the plan data above into each document');

