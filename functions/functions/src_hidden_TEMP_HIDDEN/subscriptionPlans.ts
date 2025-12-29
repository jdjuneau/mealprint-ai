/**
 * Subscription Plans Seed Data
 * Run this to seed subscription plans in Firestore
 */

import * as admin from 'firebase-admin'

export async function seedSubscriptionPlans() {
  const db = admin.firestore()

  // Default plans structure
  const plans = {
    stripe: [
      {
        id: 'pro_monthly_stripe',
        name: 'Pro Monthly',
        price: 9.99,
        currency: 'USD',
        interval: 'month',
        stripePriceId: '', // Set this after creating price in Stripe
        features: [
          'Unlimited AI features',
          'Weekly meal blueprints',
          'Recipe analysis',
          'Priority support',
        ],
      },
      {
        id: 'pro_yearly_stripe',
        name: 'Pro Yearly',
        price: 99.99,
        currency: 'USD',
        interval: 'year',
        stripePriceId: '', // Set this after creating price in Stripe
        features: [
          'Unlimited AI features',
          'Weekly meal blueprints',
          'Recipe analysis',
          'Priority support',
          'Save 17% vs monthly',
        ],
      },
    ],
    paypal: [
      {
        id: 'pro_monthly_paypal',
        name: 'Pro Monthly (PayPal)',
        price: 9.99,
        currency: 'USD',
        interval: 'month',
        paypalPlanId: '', // Set this after creating plan in PayPal
        features: [
          'Unlimited AI features',
          'Weekly meal blueprints',
          'Recipe analysis',
          'Priority support',
        ],
      },
      {
        id: 'pro_yearly_paypal',
        name: 'Pro Yearly (PayPal)',
        price: 99.99,
        currency: 'USD',
        interval: 'year',
        paypalPlanId: '', // Set this after creating plan in PayPal
        features: [
          'Unlimited AI features',
          'Weekly meal blueprints',
          'Recipe analysis',
          'Priority support',
          'Save 17% vs monthly',
        ],
      },
    ],
  }

  // Save each plan as a document
  for (const plan of [...plans.stripe, ...plans.paypal]) {
    await db.collection('subscriptionPlans').doc(plan.id).set(plan)
  }

  // Save default plans collection
  await db.collection('subscriptionPlans').doc('default').set({
    stripePlans: plans.stripe,
    paypalPlans: plans.paypal,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  })

  console.log('Subscription plans seeded successfully!')
}
