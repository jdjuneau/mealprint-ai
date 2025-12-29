import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import Stripe from 'stripe';

if (!admin.apps.length) {
  admin.initializeApp();
}

function getStripeClient(): Stripe | null {
  const rawKey = functions.config().stripe?.secret_key;
  if (!rawKey || typeof rawKey !== 'string') {
    return null;
  }
  const trimmedKey = rawKey.replace(/[\r\n]/g, '').trim();
  if (!trimmedKey.startsWith('sk_test_') && !trimmedKey.startsWith('sk_live_')) {
    return null;
  }
  try {
    return new Stripe(trimmedKey, { apiVersion: '2023-10-16' });
  } catch (error) {
    return null;
  }
}

export const createStripeCheckoutSession = functions.https.onCall(async (data, context) => {
  try {
    if (!context.auth) {
      throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
    }
    
    const { userId, planId, successUrl, cancelUrl, platform, providerPlanId } = data;
    if (!userId || !planId) {
      throw new functions.https.HttpsError('invalid-argument', 'Missing required parameters');
    }
    
    const stripe = getStripeClient();
    if (!stripe) {
      throw new functions.https.HttpsError('failed-precondition', 'Stripe is not properly configured. Please check Firebase Functions config: stripe.secret_key');
    }
    
    // Determine price ID
    let priceId: string | undefined;
    
    if (providerPlanId && typeof providerPlanId === 'string' && providerPlanId.startsWith('price_')) {
      priceId = providerPlanId;
      console.log(`✅ Using provided providerPlanId: ${priceId}`);
    } else if (planId.startsWith('price_')) {
      priceId = planId;
      console.log(`✅ Using planId as Stripe price ID: ${priceId}`);
    } else {
      // Try Firestore first
      const planDoc = await admin.firestore().collection('subscriptionPlans').doc(planId).get();
      const plan = planDoc.exists ? planDoc.data() : null;
      
      if (plan && plan.stripePriceId) {
        priceId = plan.stripePriceId;
        console.log(`✅ Using stripePriceId from Firestore plan: ${priceId}`);
      } else {
        // Fallback: search Stripe API
        console.log(`🔍 Plan not in Firestore, looking up from Stripe API for planId: ${planId}`);
        try {
          const prices = await stripe.prices.list({ active: true, type: 'recurring', limit: 100 });
          console.log(`✅ Retrieved ${prices.data.length} active recurring prices from Stripe`);
          
          const isYearly = planId.includes('yearly') || planId.includes('year');
          const targetInterval = isYearly ? 'year' : 'month';
          console.log(`🔍 Looking for ${targetInterval}ly plan (planId: ${planId})`);
          
          const matchingPrice = prices.data.find(p => p.recurring?.interval === targetInterval && p.active === true);
          if (matchingPrice) {
            priceId = matchingPrice.id;
            console.log(`✅ Found matching Stripe price: ${priceId} (${matchingPrice.recurring?.interval})`);
          } else {
            throw new functions.https.HttpsError('not-found', `Plan not found. No matching Stripe price for planId: ${planId} (looking for ${targetInterval}ly plan). Please create a ${targetInterval}ly subscription price in your Stripe dashboard.`);
          }
        } catch (stripeError: any) {
          console.error(`❌ Failed to list Stripe prices:`, stripeError.message);
          throw new functions.https.HttpsError('internal', `Failed to retrieve Stripe prices: ${stripeError.message}`);
        }
      }
    }
    
    if (!priceId || !priceId.startsWith('price_')) {
      console.error(`❌ Invalid priceId: ${priceId}`);
      throw new functions.https.HttpsError('not-found', `Invalid Stripe price ID: ${priceId}`);
    }
    
    console.log(`✅ Final priceId to use: ${priceId}`);
    
    // Create checkout session
    const session = await stripe.checkout.sessions.create({
      customer_email: context.auth.token.email,
      payment_method_types: ['card'],
      line_items: [{ price: priceId, quantity: 1 }],
      mode: 'subscription',
      success_url: successUrl.replace('{CHECKOUT_SESSION_ID}', '{CHECKOUT_SESSION_ID}'),
      cancel_url: cancelUrl,
      client_reference_id: userId,
      metadata: { userId, platform: platform || 'web', planId },
    });
    
    // Save session to Firestore
    await admin.firestore().collection('paymentSessions').doc(session.id).set({
      userId,
      platform: platform || 'web',
      planId,
      provider: 'stripe',
      status: 'pending',
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    
    return { sessionId: session.id, checkoutUrl: session.url };
  } catch (error: any) {
    console.error('Error creating Stripe checkout session:', error);
    if (error instanceof functions.https.HttpsError) {
      throw error;
    }
    throw new functions.https.HttpsError('internal', error.message || 'Failed to create checkout session');
  }
});
