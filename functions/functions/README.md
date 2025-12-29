# Coachie Firebase Cloud Functions

Payment processing and subscription management Cloud Functions.

## Setup

1. Install dependencies:
```bash
npm install
```

2. Set up Firebase config:
```bash
firebase functions:config:set stripe.secret_key="sk_test_..."
firebase functions:config:set stripe.webhook_secret="whsec_..."
firebase functions:config:set paypal.client_id="..."
firebase functions:config:set paypal.client_secret="..."
firebase functions:config:set paypal.mode="sandbox"
firebase functions:config:set app.base_url="https://coachie.app"
```

3. Build:
```bash
npm run build
```

4. Deploy:
```bash
npm run deploy
```

## Functions

### Payment Processing

- `getSubscriptionPlans` - Get available subscription plans
- `createStripeCheckoutSession` - Create Stripe checkout session
- `createPayPalOrder` - Create PayPal order
- `verifyStripePayment` - Verify Stripe payment
- `verifyPayPalPayment` - Verify PayPal payment
- `cancelStripeSubscription` - Cancel Stripe subscription
- `cancelPayPalSubscription` - Cancel PayPal subscription
- `getSubscriptionStatus` - Get user's subscription status

### Webhooks

- `processStripeWebhook` - Handle Stripe webhook events
- `processPayPalWebhook` - Handle PayPal webhook events

## Webhook Setup

### Stripe

1. Go to Stripe Dashboard > Developers > Webhooks
2. Add endpoint: `https://<region>-<project>.cloudfunctions.net/processStripeWebhook`
3. Select events:
   - `checkout.session.completed`
   - `customer.subscription.updated`
   - `customer.subscription.deleted`
4. Copy webhook signing secret to Firebase config

### PayPal

1. Go to PayPal Developer Dashboard > My Apps & Credentials
2. Under Webhooks, add endpoint: `https://<region>-<project>.cloudfunctions.net/processPayPalWebhook`
3. Select events:
   - `PAYMENT.SALE.COMPLETED`
   - `BILLING.SUBSCRIPTION.CANCELLED`

## Testing

### Local Development

```bash
npm run serve
```

### Test Stripe Webhook

```bash
stripe listen --forward-to localhost:5001/coachie-functions/us-central1/processStripeWebhook
stripe trigger checkout.session.completed
```
