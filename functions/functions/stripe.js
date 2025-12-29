const functions = require('firebase-functions');
const admin = require('firebase-admin');
const Stripe = require('stripe');

if (!admin.apps.length) {
  admin.initializeApp();
}

const stripe = new Stripe('sk_test_51NGexampleYourStripeKeyHere', {
  apiVersion: '2023-10-16',
});

exports.stripeCheckout = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const { planId, successUrl, cancelUrl, providerPlanId } = data;
  const userId = context.auth.uid;

  if (!userId || !planId) {
    throw new functions.https.HttpsError('invalid-argument', 'Missing required parameters');
  }

  let priceId = planId;
  if (planId.startsWith('price_')) {
    priceId = planId;
  } else {
    const planDoc = await admin.firestore().collection('subscriptionPlans').doc(planId).get();
    if (planDoc.exists) {
      priceId = planDoc.data()?.stripePriceId || planId;
    }
  }

  const sessionConfig = {
    payment_method_types: ['card'],
    line_items: [{
      price: priceId,
      quantity: 1,
    }],
    mode: 'subscription',
    success_url: successUrl || 'https://coachie.ai/success',
    cancel_url: cancelUrl || 'https://coachie.ai/cancel',
    client_reference_id: userId,
    metadata: {
      userId,
      planId,
      platform: 'web'
    }
  };

  const session = await stripe.checkout.sessions.create(sessionConfig);

  console.log(`✅ Created Stripe checkout session: ${session.id} for user ${userId}`);
  return {
    sessionId: session.id,
    url: session.url
  };
});