# Payment Integration Guide: Stripe & PayPal

## Overview

The web app supports both **Stripe** and **PayPal** for subscription payments, with support for both **monthly** and **yearly** billing cycles.

## Architecture

### Frontend (Web App)
- **`components/payment/StripeCheckoutButton.tsx`** - Stripe checkout button
- **`components/payment/PayPalCheckoutButton.tsx`** - PayPal checkout button
- **`lib/services/paymentService.ts`** - Payment service client
- **`app/subscription/page.tsx`** - Subscription page with plan selection

### Backend (Cloud Functions)
- **`functions/src/payments.ts`** - All payment processing logic
- Handles Stripe and PayPal checkout, verification, webhooks, and cancellation

## How It Works

### 1. User Flow

```
User visits /subscription
  ↓
Selects payment provider (Stripe or PayPal)
  ↓
Selects billing cycle (Monthly or Yearly)
  ↓
Clicks checkout button
  ↓
Redirected to payment provider (Stripe/PayPal)
  ↓
Completes payment
  ↓
Redirected back to /subscription?success=true&session_id=xxx
  ↓
Payment verified via Cloud Function
  ↓
Subscription activated in Firestore
```

### 2. Monthly vs Yearly Subscriptions

#### Monthly Subscription
- **Price**: $9.99/month
- **Billing Cycle**: `'month'`
- **Stripe Plan ID**: `pro_monthly_stripe`
- **PayPal Plan ID**: `pro_monthly_paypal`
- **Renewal**: Every 30 days
- **End Date Calculation**: `startDate + 1 month`

#### Yearly Subscription
- **Price**: $99.99/year (save 17% vs monthly)
- **Billing Cycle**: `'year'`
- **Stripe Plan ID**: `pro_yearly_stripe`
- **PayPal Plan ID**: `pro_yearly_paypal`
- **Renewal**: Every 365 days
- **End Date Calculation**: `startDate + 12 months`

### 3. Payment Processing Flow

#### Stripe Flow

1. **Create Checkout Session** (`createStripeCheckoutSession`)
   ```typescript
   // User clicks "Pay with Stripe"
   → PaymentService.createStripeCheckoutSession(userId, planId, successUrl, cancelUrl)
   → Cloud Function creates Stripe checkout session
   → Returns checkout URL
   → User redirected to Stripe
   ```

2. **User Completes Payment**
   - User enters card details on Stripe
   - Stripe processes payment
   - User redirected back with `session_id`

3. **Verify Payment** (`verifyStripePayment`)
   ```typescript
   // After redirect
   → PaymentService.handlePaymentSuccess('stripe', sessionId, userId)
   → Cloud Function verifies payment with Stripe
   → Updates Firestore subscription:
     {
       tier: 'pro',
       status: 'active',
       paymentProvider: 'stripe',
       subscriptionId: 'sub_xxx',
       billingCycle: 'month' | 'year',
       startDate: Timestamp,
       endDate: Timestamp (calculated from billing cycle),
       platforms: ['web']
     }
   ```

4. **Webhook Processing** (`processStripeWebhook`)
   - Stripe sends webhooks for subscription events:
     - `checkout.session.completed` - Payment successful
     - `customer.subscription.updated` - Subscription changed
     - `customer.subscription.deleted` - Subscription canceled
   - Cloud Function updates Firestore automatically

#### PayPal Flow

1. **Create Order** (`createPayPalOrder`)
   ```typescript
   // User clicks "Pay with PayPal"
   → PaymentService.createPayPalOrder(userId, planId)
   → Cloud Function creates PayPal order
   → Returns approval URL
   → User redirected to PayPal
   ```

2. **User Approves Payment**
   - User logs into PayPal
   - Approves payment
   - User redirected back with `token` and `PayerID`

3. **Verify Payment** (`verifyPayPalPayment`)
   ```typescript
   // After redirect
   → PaymentService.handlePaymentSuccess('paypal', orderId, userId)
   → Cloud Function captures PayPal order
   → Updates Firestore subscription:
     {
       tier: 'pro',
       status: 'active',
       paymentProvider: 'paypal',
       subscriptionId: orderId,
       billingCycle: 'month' | 'year',
       startDate: Timestamp,
       endDate: Timestamp (calculated from billing cycle),
       platforms: ['web']
     }
   ```

4. **Webhook Processing** (`processPayPalWebhook`)
   - PayPal sends webhooks for subscription events:
     - `PAYMENT.SALE.COMPLETED` - Recurring payment successful
     - `BILLING.SUBSCRIPTION.CANCELLED` - Subscription canceled
   - Cloud Function updates Firestore automatically

### 4. Subscription Data Structure

```typescript
// Firestore: users/{userId}
{
  subscription: {
    tier: 'pro' | 'free',
    status: 'active' | 'canceled' | 'expired' | 'trial',
    paymentProvider: 'stripe' | 'paypal' | 'google_play' | 'app_store' | null,
    subscriptionId: string, // Provider-specific subscription ID
    startDate: Timestamp,
    endDate: Timestamp, // Calculated based on billingCycle
    billingCycle: 'month' | 'year',
    platforms: ['web', 'android', 'ios'], // All platforms subscription is active on
    cancelAtPeriodEnd: boolean, // For Stripe - cancel at period end
  }
}
```

### 5. Billing Cycle Logic

#### Monthly Subscription
```typescript
// In verifyStripePayment or verifyPayPalPayment
const billingCycle = 'month'
const endDate = new Date()
endDate.setMonth(endDate.getMonth() + 1) // Add 1 month
```

#### Yearly Subscription
```typescript
// In verifyStripePayment or verifyPayPalPayment
const billingCycle = 'year'
const endDate = new Date()
endDate.setMonth(endDate.getMonth() + 12) // Add 12 months
```

### 6. Subscription Renewal

#### Stripe
- **Automatic**: Stripe automatically charges the card each billing period
- **Webhook**: `customer.subscription.updated` event updates `endDate` in Firestore
- **No Action Required**: Cloud Function handles renewal automatically

#### PayPal
- **Automatic**: PayPal automatically charges the account each billing period
- **Webhook**: `PAYMENT.SALE.COMPLETED` event updates `endDate` in Firestore
- **No Action Required**: Cloud Function handles renewal automatically

### 7. Subscription Cancellation

#### Stripe
```typescript
// User cancels subscription
→ PaymentService.cancelSubscription(userId, subscriptionId, 'stripe')
→ Cloud Function: cancelStripeSubscription()
→ Stripe sets cancel_at_period_end = true
→ Subscription remains active until endDate
→ On endDate, Stripe sends webhook: customer.subscription.deleted
→ Cloud Function updates subscription to 'expired'
```

#### PayPal
```typescript
// User cancels subscription
→ PaymentService.cancelSubscription(userId, subscriptionId, 'paypal')
→ Cloud Function: cancelPayPalSubscription()
→ PayPal cancels subscription immediately
→ Cloud Function updates subscription to 'canceled'
```

## Setup Instructions

### 1. Stripe Setup

1. **Create Stripe Account**
   - Go to https://stripe.com
   - Create account and get API keys

2. **Create Products & Prices**
   - Create "Pro Monthly" product
   - Create recurring price: $9.99/month
   - Create "Pro Yearly" product
   - Create recurring price: $99.99/year

3. **Configure Firebase Functions**
   ```bash
   firebase functions:config:set stripe.secret_key="sk_live_xxx"
   firebase functions:config:set stripe.webhook_secret="whsec_xxx"
   ```

4. **Set Up Webhook**
   - In Stripe Dashboard → Webhooks
   - Add endpoint: `https://YOUR_REGION-YOUR_PROJECT.cloudfunctions.net/processStripeWebhook`
   - Select events:
     - `checkout.session.completed`
     - `customer.subscription.updated`
     - `customer.subscription.deleted`

### 2. PayPal Setup

1. **Create PayPal Business Account**
   - Go to https://www.paypal.com/business
   - Create account and get API credentials

2. **Configure Firebase Functions**
   ```bash
   firebase functions:config:set paypal.client_id="xxx"
   firebase functions:config:set paypal.client_secret="xxx"
   firebase functions:config:set paypal.mode="sandbox" # or "live"
   ```

3. **Set Up Webhook**
   - In PayPal Dashboard → Webhooks
   - Add endpoint: `https://YOUR_REGION-YOUR_PROJECT.cloudfunctions.net/processPayPalWebhook`
   - Select events:
     - `PAYMENT.SALE.COMPLETED`
     - `BILLING.SUBSCRIPTION.CANCELLED`

### 3. Firestore Setup

Create subscription plans in Firestore (optional - fallback to hardcoded plans):

```typescript
// Firestore: subscriptionPlans/{planId}
{
  name: "Pro Monthly",
  price: 9.99,
  currency: "USD",
  interval: "month",
  stripePriceId: "price_xxx", // From Stripe Dashboard
  paypalPlanId: "paypal_pro_monthly",
  features: [
    "Unlimited AI features",
    "Weekly meal blueprints",
    "Recipe analysis",
    "Priority support"
  ]
}
```

## Testing

### Stripe Test Mode
1. Use Stripe test API keys
2. Use test card: `4242 4242 4242 4242`
3. Any future expiry date
4. Any 3-digit CVC

### PayPal Sandbox
1. Use PayPal sandbox credentials
2. Create sandbox test accounts
3. Test with sandbox buyer account

## Key Differences: Monthly vs Yearly

| Feature | Monthly | Yearly |
|---------|---------|--------|
| Price | $9.99/month | $99.99/year |
| Savings | - | 17% discount |
| Billing Cycle | 30 days | 365 days |
| Renewal Frequency | Monthly | Yearly |
| End Date Calculation | +1 month | +12 months |
| Plan ID (Stripe) | `pro_monthly_stripe` | `pro_yearly_stripe` |
| Plan ID (PayPal) | `pro_monthly_paypal` | `pro_yearly_paypal` |

## Cross-Platform Compatibility

- Subscription purchased on **web** works on **Android** and **iOS**
- Subscription purchased on **Android** (Google Play) works on **web** and **iOS**
- Subscription purchased on **iOS** (App Store) works on **web** and **Android**
- `platforms` array tracks all platforms subscription is active on
- `paymentProvider` tracks where subscription was purchased

## Troubleshooting

### Payment Not Processing
1. Check Firebase Functions logs
2. Verify API keys are set correctly
3. Check webhook endpoints are configured
4. Verify Firestore permissions

### Subscription Not Activating
1. Check `paymentSessions` collection in Firestore
2. Verify webhook is receiving events
3. Check user's subscription document in Firestore
4. Verify `verifyPayment` Cloud Function is being called

### Webhook Not Working
1. Check webhook URL is correct
2. Verify webhook secret is set
3. Check Cloud Function logs
4. Test webhook with Stripe/PayPal webhook testing tools

