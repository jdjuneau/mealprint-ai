const functions = require('firebase-functions');
const admin = require('firebase-admin');
const Stripe = require('stripe');

if (!admin.apps.length) {
  admin.initializeApp();
}

const stripe = new Stripe('sk_test_51NGexampleYourStripeKeyHere', {
  apiVersion: '2023-10-16',
});

exports.stripeCheckout = functions.https.onRequest(async (req, res) => {
  // Enable CORS
  res.set('Access-Control-Allow-Origin', '*');
  res.set('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.set('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    res.status(204).send('');
    return;
  }

  if (req.method !== 'POST') {
    res.status(405).json({ error: 'Method not allowed' });
    return;
  }

  try {
    const { planId, successUrl, cancelUrl } = req.body;

    if (!planId) {
      res.status(400).json({ error: 'Missing planId' });
      return;
    }

    let priceId = planId;
    if (planId.startsWith('price_')) {
      priceId = planId;
    } else {
      // For demo, use a hardcoded price ID
      priceId = 'price_1NGexamplePriceId'; // Replace with real price ID
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
    };

    const session = await stripe.checkout.sessions.create(sessionConfig);

    res.json({
      sessionId: session.id,
      url: session.url
    });

  } catch (error) {
    console.error('Stripe checkout error:', error);
    res.status(500).json({ error: error.message });
  }
});

