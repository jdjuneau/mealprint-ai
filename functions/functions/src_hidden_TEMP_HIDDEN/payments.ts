/**
 * Payment Processing Cloud Functions
 * Stripe and PayPal integration for subscription management
 */

import * as functions from 'firebase-functions'
import * as admin from 'firebase-admin'
import Stripe from 'stripe'
import axios from 'axios'

/**
 * Get Stripe client with proper key validation
 */
function getStripeClient(): Stripe | null {
  const rawKey = functions.config().stripe?.secret_key
  console.log('🔍 DEBUG: Raw key from config:', {
    exists: !!rawKey,
    type: typeof rawKey,
    length: rawKey ? String(rawKey).length : 0,
    firstChars: rawKey ? String(rawKey).substring(0, 20) : 'N/A',
    lastChars: rawKey ? String(rawKey).substring(Math.max(0, String(rawKey).length - 10)) : 'N/A'
  })
  
  if (!rawKey || typeof rawKey !== 'string') {
    console.error('❌ Stripe secret key is missing or invalid type')
    console.error('   Full config stripe object:', JSON.stringify(functions.config().stripe))
    return null
  }
  
  // Trim leading/trailing whitespace and remove newlines/carriage returns (common copy-paste issues)
  const trimmedKey = rawKey.replace(/[\r\n]/g, '').trim()
  console.log('🔍 DEBUG: Trimmed key:', {
    originalLength: rawKey.length,
    trimmedLength: trimmedKey.length,
    firstChars: trimmedKey.substring(0, 20),
    lastChars: trimmedKey.substring(Math.max(0, trimmedKey.length - 10)),
    startsWithSkTest: trimmedKey.startsWith('sk_test_'),
    startsWithSkLive: trimmedKey.startsWith('sk_live_'),
    hadWhitespace: rawKey.length !== trimmedKey.length
  })
  
  if (trimmedKey.length === 0) {
    console.error('❌ Stripe secret key is empty after trimming')
    return null
  }
  
  // Validate key format (should start with sk_test_ or sk_live_)
  if (!trimmedKey.startsWith('sk_test_') && !trimmedKey.startsWith('sk_live_')) {
    console.error(`❌ Stripe secret key has invalid format. Expected sk_test_... or sk_live_..., got: ${trimmedKey.substring(0, 20)}...`)
    console.error(`   Full key (first 50 chars): ${trimmedKey.substring(0, 50)}`)
    return null
  }
  
  // Additional validation: Stripe secret keys are typically 32+ characters after the prefix
  const keyWithoutPrefix = trimmedKey.startsWith('sk_test_') 
    ? trimmedKey.substring(8) 
    : trimmedKey.substring(8)
  if (keyWithoutPrefix.length < 32) {
    console.error(`❌ Stripe secret key appears too short. Key without prefix length: ${keyWithoutPrefix.length}`)
    return null
  }
  
  try {
    const stripe = new Stripe(trimmedKey, {
  apiVersion: '2023-10-16',
})
    console.log('✅ Stripe client initialized successfully')
    console.log('   Key preview:', `${trimmedKey.substring(0, 12)}...${trimmedKey.substring(Math.max(0, trimmedKey.length - 6))}`)
    return stripe
  } catch (error: any) {
    console.error('❌ Failed to initialize Stripe client:', error.message)
    console.error('   Error details:', error)
    return null
  }
}

// PayPal Configuration
const PAYPAL_CLIENT_ID = functions.config().paypal?.client_id || ''
const PAYPAL_CLIENT_SECRET = functions.config().paypal?.client_secret || ''
const PAYPAL_MODE = functions.config().paypal?.mode || 'sandbox'
const PAYPAL_BASE_URL = PAYPAL_MODE === 'live' 
  ? 'https://api-m.paypal.com' 
  : 'https://api-m.sandbox.paypal.com'

/**
 * Get PayPal access token
 */
async function getPayPalAccessToken(): Promise<string> {
  const auth = Buffer.from(`${PAYPAL_CLIENT_ID}:${PAYPAL_CLIENT_SECRET}`).toString('base64')
  
  const response = await axios.post(
    `${PAYPAL_BASE_URL}/v1/oauth2/token`,
    'grant_type=client_credentials',
    {
      headers: {
        'Authorization': `Basic ${auth}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    }
  )
  
  return response.data.access_token
}

/**
 * Get subscription plans
 */
export const getSubscriptionPlans = functions.https.onCall(async (data, context) => {
  try {
    if (!context.auth) {
      throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated')
    }

    const platform = data.platform || 'web'

    // Get Stripe plans with proper error handling
    let stripePlans: Stripe.ApiList<Stripe.Price> = { data: [], has_more: false, object: 'list', url: '' }
    const stripe = getStripeClient()
    
    if (!stripe) {
      throw new functions.https.HttpsError(
        'failed-precondition',
        'Stripe is not properly configured. Please check Firebase Functions config: stripe.secret_key'
      )
    }
    
    try {
      stripePlans = await stripe.prices.list({
      active: true,
      type: 'recurring',
      limit: 100,
    })
      console.log(`✅ Successfully fetched ${stripePlans.data.length} Stripe plans`)
    } catch (stripeError: any) {
      const rawKey = functions.config().stripe?.secret_key
      const keyInfo = rawKey ? {
        length: String(rawKey).length,
        firstChars: String(rawKey).substring(0, 20),
        lastChars: String(rawKey).substring(Math.max(0, String(rawKey).length - 10)),
        hasNewlines: String(rawKey).includes('\n') || String(rawKey).includes('\r'),
        trimmedLength: String(rawKey).trim().length,
      } : { error: 'Key is missing' }
      
      console.error('❌ Stripe API error:', stripeError.message)
      console.error('❌ Error type:', stripeError.type)
      console.error('❌ Error code:', stripeError.code)
      console.error('❌ Key info:', JSON.stringify(keyInfo, null, 2))
      
      // Provide specific error messages
      if (stripeError.type === 'StripeInvalidRequestError' || stripeError.code === 'api_key_invalid') {
        if (stripeError.message.includes('Invalid API Key') || stripeError.message.includes('api_key_invalid')) {
          throw new functions.https.HttpsError(
            'failed-precondition',
            `Invalid Stripe API key. The key stored in Firebase config (stripe.secret_key) is being rejected by Stripe. ` +
            `Please verify: 1) The key is correct and complete, 2) The key hasn't been rotated/revoked, ` +
            `3) The key matches your Stripe account. Use the checkStripeConfig function to diagnose. ` +
            `Error: ${stripeError.message}`
          )
        }
      }
      
      throw new functions.https.HttpsError(
        'internal',
        `Failed to fetch Stripe plans: ${stripeError.message}`
      )
    }

    // Get PayPal plans from Firestore
    const db = admin.firestore()
    const plansDoc = await db.collection('subscriptionPlans').doc('default').get()
    const paypalPlans = plansDoc.exists ? plansDoc.data()?.paypalPlans || [] : []

    // If Stripe plans failed or empty, use fallback
    const stripePlanList = stripePlans.data.length > 0 
      ? stripePlans.data.map(price => ({
        id: `pro_${price.recurring?.interval}_stripe`,
        name: `Pro ${price.recurring?.interval === 'month' ? 'Monthly' : 'Yearly'}`,
        price: price.unit_amount ? price.unit_amount / 100 : 9.99,
        currency: price.currency.toUpperCase(),
        interval: price.recurring?.interval || 'month',
        provider: 'stripe' as const,
        providerPlanId: price.id,
        features: [
          'Unlimited AI features',
          'Weekly meal blueprints',
          'Recipe analysis',
          'Priority support',
        ],
        }))
      : [
          // Fallback Stripe plans when API fails
          {
            id: 'pro_monthly_stripe',
            name: 'Pro Monthly',
            price: 9.99,
            currency: 'USD',
            interval: 'month' as const,
            provider: 'stripe' as const,
            providerPlanId: 'price_pro_monthly',
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
            interval: 'year' as const,
            provider: 'stripe' as const,
            providerPlanId: 'price_pro_yearly',
            features: [
              'Unlimited AI features',
              'Weekly meal blueprints',
              'Recipe analysis',
              'Priority support',
              'Save 17% vs monthly',
            ],
          },
        ]

    const plans = [
      ...stripePlanList,
      ...(paypalPlans.length === 0 ? [
        {
          id: 'pro_monthly_paypal',
          name: 'Pro Monthly (PayPal)',
          price: 9.99,
          currency: 'USD',
          interval: 'month' as const,
          provider: 'paypal' as const,
          providerPlanId: 'paypal_pro_monthly',
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
          interval: 'year' as const,
          provider: 'paypal' as const,
          providerPlanId: 'paypal_pro_yearly',
          features: [
            'Unlimited AI features',
            'Weekly meal blueprints',
            'Recipe analysis',
            'Priority support',
            'Save 17% vs monthly',
          ],
        },
      ] : paypalPlans),
    ]

    return { plans, platform }
  } catch (error: any) {
    console.error('Error getting subscription plans:', error)
    throw new functions.https.HttpsError('internal', error.message || 'Failed to get plans')
  }
})

/**
 * Create Stripe checkout session
 */
export const createStripeCheckoutSession = functions.https.onCall(async (data, context) => {
  try {
    if (!context.auth) {
      throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated')
    }

    const { userId, planId, successUrl, cancelUrl, platform, providerPlanId } = data

    if (!userId || !planId) {
      throw new functions.https.HttpsError('invalid-argument', 'Missing required parameters')
    }

    const stripe = getStripeClient()
    if (!stripe) {
      throw new functions.https.HttpsError(
        'failed-precondition',
        'Stripe is not properly configured. Please check Firebase Functions config: stripe.secret_key'
      )
    }

    let priceId: string
    
    // Priority 1: Use providerPlanId if provided (direct Stripe price ID)
    if (providerPlanId && typeof providerPlanId === 'string' && providerPlanId.startsWith('price_')) {
      priceId = providerPlanId
      console.log(`✅ Using provided providerPlanId: ${priceId}`)
    }
    // Priority 2: If planId is already a Stripe price ID, use it directly
    else if (planId.startsWith('price_')) {
      priceId = planId
      console.log(`✅ Using planId as Stripe price ID: ${priceId}`)
    }
    // Priority 3: Try to get plan from Firestore
    else {
    const planDoc = await admin.firestore().collection('subscriptionPlans').doc(planId).get()
    const plan = planDoc.exists ? planDoc.data() : null
    
    if (plan && plan.stripePriceId) {
      priceId = plan.stripePriceId
        console.log(`✅ Using stripePriceId from Firestore plan: ${priceId}`)
    } else {
        // Priority 4: Look up from Stripe API by matching interval
        console.log(`🔍 Plan not in Firestore, looking up from Stripe API for planId: ${planId}`)
        let prices
        try {
          prices = await stripe.prices.list({ 
            active: true, 
            type: 'recurring',
            limit: 100 
          })
          console.log(`✅ Retrieved ${prices.data.length} active recurring prices from Stripe`)
        } catch (stripeError: any) {
          console.error(`❌ Failed to list Stripe prices:`, stripeError.message)
          throw new functions.https.HttpsError(
            'internal',
            `Failed to retrieve Stripe prices: ${stripeError.message}`
          )
        }
        
        const isYearly = planId.includes('yearly') || planId.includes('year')
        const targetInterval = isYearly ? 'year' : 'month'
        console.log(`🔍 Looking for ${targetInterval}ly plan (planId: ${planId})`)
        
      const matchingPrice = prices.data.find(p => 
          p.recurring?.interval === targetInterval &&
          p.active === true
        )
        
        if (matchingPrice) {
          priceId = matchingPrice.id
          console.log(`✅ Found matching Stripe price: ${priceId} (${matchingPrice.recurring?.interval}, ${matchingPrice.unit_amount ? matchingPrice.unit_amount / 100 : 'N/A'} ${matchingPrice.currency})`)
        } else {
          console.error(`❌ No matching Stripe price found for planId: ${planId}`)
          console.error(`   Target interval: ${targetInterval}`)
          console.error(`   Available prices (${prices.data.length}):`)
          prices.data.forEach(p => {
            console.error(`     - ${p.id}: ${p.recurring?.interval} (${p.unit_amount ? p.unit_amount / 100 : 'N/A'} ${p.currency})`)
          })
          throw new functions.https.HttpsError(
            'not-found', 
            `Plan not found. No matching Stripe price for planId: ${planId} (looking for ${targetInterval}ly plan). ` +
            `Found ${prices.data.length} active recurring prices in Stripe, but none match the interval. ` +
            `Please create a ${targetInterval}ly subscription price in your Stripe dashboard.`
          )
        }
      }
    }

    if (!priceId || !priceId.startsWith('price_')) {
      console.error(`❌ Invalid priceId: ${priceId}`)
      throw new functions.https.HttpsError('not-found', `Invalid Stripe price ID: ${priceId}`)
    }
    
    console.log(`✅ Final priceId to use: ${priceId}`)

    // Log the key being used (first/last chars only for security)
    const rawKey = functions.config().stripe?.secret_key
    const keyPreview = rawKey ? `${String(rawKey).substring(0, 12)}...${String(rawKey).substring(Math.max(0, String(rawKey).length - 6))}` : 'MISSING'
    console.log('🔍 Creating checkout session with Stripe key:', keyPreview)
    
    let session
    try {
      session = await stripe.checkout.sessions.create({
      customer_email: context.auth.token.email,
      payment_method_types: ['card'],
      line_items: [{ price: priceId, quantity: 1 }],
      mode: 'subscription',
      success_url: successUrl.replace('{CHECKOUT_SESSION_ID}', '{CHECKOUT_SESSION_ID}'),
      cancel_url: cancelUrl,
      client_reference_id: userId,
      metadata: { userId, platform: platform || 'web', planId },
    })
    } catch (stripeError: any) {
      // Log detailed error info
      console.error('❌ Stripe API error creating checkout session:')
      console.error('   Error type:', stripeError.type)
      console.error('   Error code:', stripeError.code)
      console.error('   Error message:', stripeError.message)
      console.error('   Key being used:', keyPreview)
      console.error('   Key length:', rawKey ? String(rawKey).length : 0)
      
      if (stripeError.type === 'StripeInvalidRequestError' || stripeError.code === 'api_key_invalid') {
        throw new functions.https.HttpsError(
          'failed-precondition',
          `Invalid Stripe API key. The key stored in Firebase config is being rejected by Stripe. ` +
          `Key preview: ${keyPreview}. ` +
          `Please verify the key is correct: firebase functions:config:get stripe.secret_key`
        )
      }
      throw stripeError
    }

    await admin.firestore().collection('paymentSessions').doc(session.id).set({
      userId,
      platform: platform || 'web',
      planId,
      provider: 'stripe',
      status: 'pending',
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    })

    return { sessionId: session.id, checkoutUrl: session.url }
  } catch (error: any) {
    console.error('Error creating Stripe checkout session:', error)
    throw new functions.https.HttpsError('internal', error.message || 'Failed to create checkout session')
  }
})

/**
 * Verify Stripe payment
 */
export const verifyStripePayment = functions.https.onCall(async (data, context) => {
  try {
    if (!context.auth) {
      throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated')
    }

    const { sessionId, userId, platform } = data

    if (!sessionId || !userId) {
      throw new functions.https.HttpsError('invalid-argument', 'Missing required parameters')
    }

    const stripe = getStripeClient()
    if (!stripe) {
      throw new functions.https.HttpsError(
        'failed-precondition',
        'Stripe is not properly configured. Please check Firebase Functions config: stripe.secret_key'
      )
    }

    const session = await stripe.checkout.sessions.retrieve(sessionId)

    if (session.payment_status !== 'paid' || session.status !== 'complete') {
      throw new functions.https.HttpsError('failed-precondition', 'Payment not completed')
    }

    if (session.client_reference_id !== userId) {
      throw new functions.https.HttpsError('permission-denied', 'User mismatch')
    }

    const subscription = await stripe.subscriptions.retrieve(session.subscription as string)

    await admin.firestore().collection('paymentSessions').doc(sessionId).update({
      status: 'completed',
      subscriptionId: subscription.id,
      completedAt: admin.firestore.FieldValue.serverTimestamp(),
    })

    const userRef = admin.firestore().collection('users').doc(userId)
    const userDoc = await userRef.get()
    const userData = userDoc.data() || {}
    const existingPlatforms = userData.platforms || []
    const updatedPlatforms = existingPlatforms.includes(platform || 'web')
      ? existingPlatforms
      : [...existingPlatforms, platform || 'web']

    const subscriptionData = {
      tier: 'pro',
      status: 'active',
      paymentProvider: 'stripe',
      subscriptionId: subscription.id,
      startDate: admin.firestore.Timestamp.fromDate(new Date()),
      endDate: subscription.current_period_end
        ? admin.firestore.Timestamp.fromDate(new Date(subscription.current_period_end * 1000))
        : null,
      billingCycle: subscription.items.data[0]?.price.recurring?.interval || 'month',
      platforms: updatedPlatforms,
      cancelAtPeriodEnd: subscription.cancel_at_period_end || false,
    }

    await userRef.update({
      subscription: subscriptionData,
      platform: platform || 'web',
      platforms: [...new Set(updatedPlatforms)].sort(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    })

    return { success: true, subscriptionId: subscription.id }
  } catch (error: any) {
    console.error('Error verifying Stripe payment:', error)
    throw new functions.https.HttpsError('internal', error.message || 'Payment verification failed')
  }
})

/**
 * Create PayPal order
 */
export const createPayPalOrder = functions.https.onCall(async (data, context) => {
  try {
    if (!context.auth) {
      throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated')
    }

    const { userId, planId, platform, returnUrl, cancelUrl } = data

    if (!userId || !planId) {
      throw new functions.https.HttpsError('invalid-argument', 'Missing required parameters')
    }

    const planDoc = await admin.firestore().collection('subscriptionPlans').doc(planId).get()
    const plan = planDoc.exists ? planDoc.data() : null
    
    const isYearly = planId.includes('yearly')
    const amount = plan?.price || (isYearly ? 99.99 : 9.99)
    const currency = plan?.currency || 'USD'

    const accessToken = await getPayPalAccessToken()

    // Use provided URLs or fall back to base_url config (for local testing support)
    const baseUrl = functions.config().app?.base_url || 'https://coachie.app'
    // PayPal requires valid URLs - remove {TOKEN} placeholder as PayPal will append the token automatically
    let finalReturnUrl = returnUrl || `${baseUrl}/subscription?success=true`
    let finalCancelUrl = cancelUrl || `${baseUrl}/subscription?canceled=true`
    
    // Remove {TOKEN} placeholder if present - PayPal will add it automatically
    finalReturnUrl = finalReturnUrl.replace('{TOKEN}', '')
    finalCancelUrl = finalCancelUrl.replace('{TOKEN}', '')

    // Validate amount is a valid number
    const amountValue = parseFloat(amount.toFixed(2))
    if (isNaN(amountValue) || amountValue <= 0) {
      throw new functions.https.HttpsError('invalid-argument', `Invalid amount: ${amount}`)
    }

    const orderData = {
      intent: 'CAPTURE',
      purchase_units: [{
        amount: { 
          currency_code: currency, 
          value: amountValue.toFixed(2) 
        },
        description: `Coachie Pro ${isYearly ? 'Yearly' : 'Monthly'} Subscription`,
      }],
      application_context: {
        brand_name: 'Coachie',
        landing_page: 'BILLING',
        user_action: 'PAY_NOW',
        return_url: finalReturnUrl,
        cancel_url: finalCancelUrl,
      },
    }

    console.log('PayPal order request:', JSON.stringify(orderData, null, 2))

    let response
    try {
      response = await axios.post(
      `${PAYPAL_BASE_URL}/v2/checkout/orders`,
      orderData,
      {
        headers: {
          'Authorization': `Bearer ${accessToken}`,
          'Content-Type': 'application/json',
            'Accept': 'application/json',
        },
      }
    )
    } catch (error: any) {
      console.error('PayPal API error:', {
        status: error.response?.status,
        statusText: error.response?.statusText,
        data: error.response?.data,
        message: error.message,
      })
      throw error
    }

    const order = response.data

    await admin.firestore().collection('paymentSessions').doc(order.id).set({
      userId,
      platform: platform || 'web',
      planId,
      provider: 'paypal',
      status: 'pending',
      amount,
      currency,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    })

    const approvalUrl = order.links?.find((link: any) => link.rel === 'approve')?.href

    return { orderId: order.id, approvalUrl }
  } catch (error: any) {
    console.error('Error creating PayPal order:', error)
    throw new functions.https.HttpsError('internal', error.message || 'Failed to create PayPal order')
  }
})

/**
 * Verify PayPal payment
 */
export const verifyPayPalPayment = functions.https.onCall(async (data, context) => {
  try {
    if (!context.auth) {
      throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated')
    }

    const { sessionId, userId, platform } = data

    if (!sessionId || !userId) {
      throw new functions.https.HttpsError('invalid-argument', 'Missing required parameters')
    }

    const orderDoc = await admin.firestore().collection('paymentSessions').doc(sessionId).get()
    if (!orderDoc.exists) {
      throw new functions.https.HttpsError('not-found', 'Order not found')
    }

    const orderData = orderDoc.data()
    if (orderData?.userId !== userId) {
      throw new functions.https.HttpsError('permission-denied', 'User mismatch')
    }

    const accessToken = await getPayPalAccessToken()

    const response = await axios.post(
      `${PAYPAL_BASE_URL}/v2/checkout/orders/${sessionId}/capture`,
      {},
      {
        headers: {
          'Authorization': `Bearer ${accessToken}`,
          'Content-Type': 'application/json',
        },
      }
    )

    const order = response.data

    if (order.status !== 'COMPLETED') {
      throw new functions.https.HttpsError('failed-precondition', 'Payment not completed')
    }

    await admin.firestore().collection('paymentSessions').doc(sessionId).update({
      status: 'completed',
      paypalOrderId: order.id,
      completedAt: admin.firestore.FieldValue.serverTimestamp(),
    })

    const isYearly = orderData.planId?.includes('yearly')
    const endDate = new Date()
    endDate.setMonth(endDate.getMonth() + (isYearly ? 12 : 1))

    const userRef = admin.firestore().collection('users').doc(userId)
    const userDoc = await userRef.get()
    const userData = userDoc.data() || {}
    const existingPlatforms = userData.platforms || []
    const updatedPlatforms = existingPlatforms.includes(platform || 'web')
      ? existingPlatforms
      : [...existingPlatforms, platform || 'web']

    const subscriptionData = {
      tier: 'pro',
      status: 'active',
      paymentProvider: 'paypal',
      subscriptionId: order.id,
      startDate: admin.firestore.Timestamp.fromDate(new Date()),
      endDate: admin.firestore.Timestamp.fromDate(endDate),
      billingCycle: isYearly ? 'year' : 'month',
      platforms: updatedPlatforms,
      cancelAtPeriodEnd: false,
    }

    await userRef.update({
      subscription: subscriptionData,
      platform: platform || 'web',
      platforms: [...new Set(updatedPlatforms)].sort(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    })

    return { success: true, subscriptionId: order.id }
  } catch (error: any) {
    console.error('Error verifying PayPal payment:', error)
    throw new functions.https.HttpsError('internal', error.message || 'Payment verification failed')
  }
})

/**
 * Process Stripe webhook
 */
export const processStripeWebhook = functions.https.onRequest(async (req, res) => {
  const sig = req.headers['stripe-signature']

  if (!sig) {
    res.status(400).send('No signature')
    return
  }

  try {
    const stripe = getStripeClient()
    if (!stripe) {
      res.status(500).send('Stripe is not properly configured')
      return
    }

    const webhookSecret = functions.config().stripe?.webhook_secret || ''
    if (!webhookSecret) {
      res.status(500).send('Stripe webhook secret is not configured')
      return
    }

    const event = stripe.webhooks.constructEvent(req.body, sig, webhookSecret)

    switch (event.type) {
      case 'checkout.session.completed':
        await handleStripeCheckoutCompleted(event.data.object as Stripe.Checkout.Session)
        break
      case 'customer.subscription.updated':
        await handleStripeSubscriptionUpdated(event.data.object as Stripe.Subscription)
        break
      case 'customer.subscription.deleted':
        await handleStripeSubscriptionDeleted(event.data.object as Stripe.Subscription)
        break
      default:
        console.log(`Unhandled event type: ${event.type}`)
    }

    res.json({ received: true })
  } catch (error: any) {
    console.error('Webhook error:', error)
    res.status(400).send(`Webhook Error: ${error.message}`)
  }
})

async function handleStripeCheckoutCompleted(session: Stripe.Checkout.Session) {
  const userId = session.client_reference_id || session.metadata?.userId
  if (!userId || !session.subscription) return

  const stripe = getStripeClient()
  if (!stripe) {
    console.error('❌ Cannot handle checkout: Stripe not configured')
    return
  }

  const subscription = await stripe.subscriptions.retrieve(session.subscription as string)
  const userRef = admin.firestore().collection('users').doc(userId)
  const userDoc = await userRef.get()
  const userData = userDoc.data() || {}
  const existingPlatforms = userData.platforms || []
  const platform = session.metadata?.platform || 'web'
  const updatedPlatforms = existingPlatforms.includes(platform)
    ? existingPlatforms
    : [...existingPlatforms, platform]

  const subscriptionData = {
    tier: 'pro',
    status: 'active',
    paymentProvider: 'stripe',
    subscriptionId: subscription.id,
    startDate: admin.firestore.Timestamp.fromDate(new Date()),
    endDate: subscription.current_period_end
      ? admin.firestore.Timestamp.fromDate(new Date(subscription.current_period_end * 1000))
      : null,
    billingCycle: subscription.items.data[0]?.price.recurring?.interval || 'month',
    platforms: updatedPlatforms,
    cancelAtPeriodEnd: subscription.cancel_at_period_end || false,
  }

  await userRef.update({
    subscription: subscriptionData,
    platform: platform,
    platforms: [...new Set(updatedPlatforms)].sort(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  })

  await admin.firestore().collection('paymentSessions').doc(session.id).update({
    status: 'completed',
    subscriptionId: subscription.id,
    completedAt: admin.firestore.FieldValue.serverTimestamp(),
  })
}

async function handleStripeSubscriptionUpdated(subscription: Stripe.Subscription) {
  const usersSnapshot = await admin.firestore()
    .collection('users')
    .where('subscription.subscriptionId', '==', subscription.id)
    .limit(1)
    .get()

  if (usersSnapshot.empty) return

  const userRef = usersSnapshot.docs[0].ref
  const userData = usersSnapshot.docs[0].data()

  const subscriptionData = {
    ...userData.subscription,
    status: subscription.status === 'active' ? 'active' : 'canceled',
    endDate: subscription.current_period_end
      ? admin.firestore.Timestamp.fromDate(new Date(subscription.current_period_end * 1000))
      : userData.subscription?.endDate,
    cancelAtPeriodEnd: subscription.cancel_at_period_end || false,
  }

  await userRef.update({
    subscription: subscriptionData,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  })
}

async function handleStripeSubscriptionDeleted(subscription: Stripe.Subscription) {
  const usersSnapshot = await admin.firestore()
    .collection('users')
    .where('subscription.subscriptionId', '==', subscription.id)
    .limit(1)
    .get()

  if (usersSnapshot.empty) return

  const userRef = usersSnapshot.docs[0].ref

  await userRef.update({
    subscription: {
      tier: 'free',
      status: 'expired',
      paymentProvider: null,
      subscriptionId: null,
      endDate: admin.firestore.Timestamp.fromDate(new Date()),
      cancelAtPeriodEnd: false,
    },
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  })
}

/**
 * Process PayPal webhook
 */
export const processPayPalWebhook = functions.https.onRequest(async (req, res) => {
  try {
    const event = req.body

    switch (event.event_type) {
      case 'PAYMENT.SALE.COMPLETED':
        await handlePayPalPaymentCompleted(event.resource)
        break
      case 'BILLING.SUBSCRIPTION.CANCELLED':
        await handlePayPalSubscriptionCancelled(event.resource)
        break
      default:
        console.log(`Unhandled PayPal event type: ${event.event_type}`)
    }

    res.status(200).send('OK')
  } catch (error: any) {
    console.error('PayPal webhook error:', error)
    res.status(400).send(`Webhook Error: ${error.message}`)
  }
})

async function handlePayPalPaymentCompleted(resource: any) {
  const orderId = resource.billing_agreement_id || resource.id
  const orderDoc = await admin.firestore().collection('paymentSessions').doc(orderId).get()
  if (!orderDoc.exists) return

  const orderData = orderDoc.data()
  const userId = orderData?.userId
  if (!userId) return

  const userRef = admin.firestore().collection('users').doc(userId)
  const userDoc = await userRef.get()
  const userData = userDoc.data() || {}
  const existingPlatforms = userData.platforms || []
  const platform = orderData.platform || 'web'
  const updatedPlatforms = existingPlatforms.includes(platform)
    ? existingPlatforms
    : [...existingPlatforms, platform]

  const isYearly = orderData.planId?.includes('yearly')
  const endDate = new Date()
  endDate.setMonth(endDate.getMonth() + (isYearly ? 12 : 1))

  const subscriptionData = {
    tier: 'pro',
    status: 'active',
    paymentProvider: 'paypal',
    subscriptionId: orderId,
    startDate: admin.firestore.Timestamp.fromDate(new Date()),
    endDate: admin.firestore.Timestamp.fromDate(endDate),
    billingCycle: isYearly ? 'year' : 'month',
    platforms: updatedPlatforms,
    cancelAtPeriodEnd: false,
  }

  await userRef.update({
    subscription: subscriptionData,
    platform: platform,
    platforms: [...new Set(updatedPlatforms)].sort(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  })
}

async function handlePayPalSubscriptionCancelled(resource: any) {
  const subscriptionId = resource.id

  const usersSnapshot = await admin.firestore()
    .collection('users')
    .where('subscription.subscriptionId', '==', subscriptionId)
    .where('subscription.paymentProvider', '==', 'paypal')
    .limit(1)
    .get()

  if (usersSnapshot.empty) return

  const userRef = usersSnapshot.docs[0].ref

  await userRef.update({
    subscription: {
      tier: 'free',
      status: 'canceled',
      paymentProvider: null,
      subscriptionId: null,
      endDate: admin.firestore.Timestamp.fromDate(new Date()),
      cancelAtPeriodEnd: false,
    },
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  })
}

/**
 * Cancel Stripe subscription
 */
export const cancelStripeSubscription = functions.https.onCall(async (data, context) => {
  try {
    if (!context.auth) {
      throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated')
    }

    const { userId, subscriptionId } = data

    if (!userId || !subscriptionId) {
      throw new functions.https.HttpsError('invalid-argument', 'Missing required parameters')
    }

    const userDoc = await admin.firestore().collection('users').doc(userId).get()
    const userData = userDoc.data()

    if (userData?.subscription?.subscriptionId !== subscriptionId) {
      throw new functions.https.HttpsError('permission-denied', 'Subscription not found')
    }

    const stripe = getStripeClient()
    if (!stripe) {
      throw new functions.https.HttpsError(
        'failed-precondition',
        'Stripe is not properly configured. Please check Firebase Functions config: stripe.secret_key'
      )
    }

    await stripe.subscriptions.update(subscriptionId, {
      cancel_at_period_end: true,
    })

    await admin.firestore().collection('users').doc(userId).update({
      'subscription.cancelAtPeriodEnd': true,
      'subscription.status': 'active',
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    })

    return { success: true }
  } catch (error: any) {
    console.error('Error canceling Stripe subscription:', error)
    throw new functions.https.HttpsError('internal', error.message || 'Failed to cancel subscription')
  }
})

/**
 * Cancel PayPal subscription
 */
export const cancelPayPalSubscription = functions.https.onCall(async (data, context) => {
  try {
    if (!context.auth) {
      throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated')
    }

    const { userId, subscriptionId } = data

    if (!userId || !subscriptionId) {
      throw new functions.https.HttpsError('invalid-argument', 'Missing required parameters')
    }

    const userDoc = await admin.firestore().collection('users').doc(userId).get()
    const userData = userDoc.data()

    if (userData?.subscription?.subscriptionId !== subscriptionId) {
      throw new functions.https.HttpsError('permission-denied', 'Subscription not found')
    }

    const accessToken = await getPayPalAccessToken()

    await axios.post(
      `${PAYPAL_BASE_URL}/v1/billing/subscriptions/${subscriptionId}/cancel`,
      { reason: 'User requested cancellation' },
      {
        headers: {
          'Authorization': `Bearer ${accessToken}`,
          'Content-Type': 'application/json',
        },
      }
    )

    await admin.firestore().collection('users').doc(userId).update({
      subscription: {
        tier: 'free',
        status: 'canceled',
        paymentProvider: null,
        subscriptionId: null,
        endDate: admin.firestore.Timestamp.fromDate(new Date()),
        cancelAtPeriodEnd: false,
      },
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    })

    return { success: true }
  } catch (error: any) {
    console.error('Error canceling PayPal subscription:', error)
    throw new functions.https.HttpsError('internal', error.message || 'Failed to cancel subscription')
  }
})

/**
 * DIAGNOSTIC: Check Stripe key configuration
 * This function helps debug Stripe key issues
 */
export const checkStripeConfig = functions.https.onCall(async (data, context) => {
  try {
    if (!context.auth) {
      throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated')
    }

    const rawKey = functions.config().stripe?.secret_key
    const config: any = {
      keyExists: !!rawKey,
      keyType: typeof rawKey,
      keyLength: rawKey ? String(rawKey).length : 0,
      keyFirstChars: rawKey ? String(rawKey).substring(0, 20) : 'N/A',
      keyLastChars: rawKey ? String(rawKey).substring(Math.max(0, String(rawKey).length - 10)) : 'N/A',
      startsWithSkTest: rawKey ? String(rawKey).trim().startsWith('sk_test_') : false,
      startsWithSkLive: rawKey ? String(rawKey).trim().startsWith('sk_live_') : false,
      keyValid: false,
      keyTestMessage: '',
      keyErrorType: null as string | null,
      keyErrorCode: null as string | null,
    }

    // Try to create a Stripe client to test the key
    let stripeTest = null
    let stripeError = null
    if (rawKey) {
      try {
        const trimmedKey = String(rawKey).replace(/[\r\n]/g, '').trim()
        stripeTest = new Stripe(trimmedKey, { apiVersion: '2023-10-16' })
        // Try a simple API call to validate the key
        await stripeTest.prices.list({ limit: 1 })
        config.keyValid = true
        config.keyTestMessage = 'Key is valid and working'
      } catch (error: any) {
        config.keyValid = false
        config.keyTestMessage = error.message || 'Unknown error'
        config.keyErrorType = error.type || null
        config.keyErrorCode = error.code || null
        stripeError = error
      }
    } else {
      config.keyValid = false
      config.keyTestMessage = 'Key is missing'
    }

    return {
      success: true,
      config,
      message: 'Stripe configuration check complete. Check the config object for details.',
    }
  } catch (error: any) {
    console.error('Error checking Stripe config:', error)
    throw new functions.https.HttpsError('internal', error.message || 'Failed to check Stripe config')
  }
})

/**
 * Get subscription status
 */
export const getSubscriptionStatus = functions.https.onCall(async (data, context) => {
  try {
    if (!context.auth) {
      throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated')
    }

    const { userId } = data
    const targetUserId = userId || context.auth.uid

    const userDoc = await admin.firestore().collection('users').doc(targetUserId).get()

    if (!userDoc.exists) {
      throw new functions.https.HttpsError('not-found', 'User not found')
    }

    const userData = userDoc.data()
    const subscription = userData?.subscription

    if (!subscription) {
      return { active: false }
    }

    const isActive = subscription.status === 'active' && 
      (!subscription.endDate || subscription.endDate.toDate() > new Date())

    return {
      active: isActive,
      provider: subscription.paymentProvider,
      subscriptionId: subscription.subscriptionId,
    }
  } catch (error: any) {
    console.error('Error getting subscription status:', error)
    throw new functions.https.HttpsError('internal', error.message || 'Failed to get subscription status')
  }
})
