# 🚀 Google Play Real-time Developer Notifications (RTDN) Setup

This guide explains how to set up automatic subscription renewal tracking for Google Play subscriptions, similar to Stripe/PayPal webhooks.

## ✅ What's Implemented

1. **Google Play API Verification** - Real verification using Google Play Developer API
2. **RTDN Webhook Handler** - `processGooglePlayRTDN` function to handle renewal notifications
3. **Automatic Subscription Updates** - Subscriptions automatically update when payments are processed
4. **Unified Subscription Structure** - Google Play subscriptions use the same structure as Stripe/PayPal

## 📋 Setup Steps

### Step 1: Create Google Play Service Account

1. Go to [Google Play Console](https://play.google.com/apps/publish/)
2. Navigate to **Settings** > **API access**
3. Link your Google Cloud Project (or create one)
4. Create a **Service Account**:
   - Go to [Google Cloud Console](https://console.cloud.google.com/)
   - Navigate to **IAM & Admin** > **Service Accounts**
   - Click **Create Service Account**
   - Name it (e.g., "coachie-google-play")
   - Grant role: **Service Account User**
   - Click **Done**
5. Download the service account key:
   - Click on the service account
   - Go to **Keys** tab
   - Click **Add Key** > **Create new key**
   - Choose **JSON** format
   - Download the file
6. Grant permissions in Play Console:
   - Back in Play Console > **Settings** > **API access**
   - Find your service account
   - Click **Grant access**
   - Grant **View financial data** permission
   - Click **Invite user**

### Step 2: Configure Firebase Functions

Add the service account key to Firebase Functions config:

```bash
# Option 1: Set as environment variable (recommended for production)
# Upload service account JSON to Firebase Functions config
firebase functions:config:set googleplay.service_account_key="$(cat path/to/service-account-key.json)"

# Option 2: Use environment variable
# Set GOOGLE_PLAY_SERVICE_ACCOUNT environment variable with the JSON content
```

### Step 3: Configure RTDN in Google Play Console

1. Go to [Google Play Console](https://play.google.com/apps/publish/)
2. Select your app: **Coachie**
3. Go to **Monetize** > **Products** > **Subscriptions**
4. Click on any subscription product
5. Scroll to **Real-time developer notifications**
6. Click **Create notification**
7. Enter the RTDN endpoint URL:
   ```
   https://us-central1-vanish-auth-real.cloudfunctions.net/processGooglePlayRTDN
   ```
   (Replace `vanish-auth-real` with your project ID if different)
8. Click **Save**

### Step 4: Deploy Functions

```bash
cd functions
npm install  # Installs googleapis package
npm run build
firebase deploy --only functions:processGooglePlayRTDN,functions:verifyPurchase
```

## 🔄 How It Works

### Initial Purchase
1. User purchases subscription in Android app
2. App calls `verifyPurchase` Cloud Function
3. Function verifies with Google Play API
4. Subscription stored in Firestore with `endDate` and `billingCycle`

### Automatic Renewals
1. Google Play processes recurring payment
2. Google Play sends RTDN notification to `processGooglePlayRTDN`
3. Function verifies subscription with Google Play API
4. Updates `endDate` in Firestore automatically
5. User's subscription stays active

### Subscription Structure

Google Play subscriptions now use the same structure as Stripe/PayPal:

```typescript
{
  tier: 'pro',
  status: 'active',
  paymentProvider: 'google_play',
  subscriptionId: 'com.coachie.pro.monthly_abc123...',
  startDate: FirestoreTimestamp,
  endDate: FirestoreTimestamp,  // Automatically updated on renewal
  billingCycle: 'month' | 'year',
  platforms: ['android'],
  cancelAtPeriodEnd: false,
  autoRenewing: true,
  purchaseToken: '...',
  productId: 'com.coachie.pro.monthly'
}
```

## 📊 RTDN Notification Types Handled

- **SUBSCRIPTION_RENEWED (2)**: Updates `endDate` when payment is processed
- **SUBSCRIPTION_PURCHASED (4)**: Handles new subscriptions
- **SUBSCRIPTION_CANCELED (3)**: Sets `cancelAtPeriodEnd: true`
- **SUBSCRIPTION_EXPIRED (13)**: Sets status to 'expired'
- **SUBSCRIPTION_REVOKED (12)**: Sets status to 'canceled' (refunded)
- **SUBSCRIPTION_ON_HOLD (5)**: Marks subscription as on hold
- **SUBSCRIPTION_IN_GRACE_PERIOD (6)**: Keeps subscription active during grace period
- And more...

## ✅ Result

Once configured, Google Play subscriptions will:
- ✅ Automatically update `endDate` when payments are processed
- ✅ Work exactly like Stripe/PayPal subscriptions
- ✅ Handle cancellations, renewals, and payment issues automatically
- ✅ No manual intervention required

## 🧪 Testing

1. Test RTDN endpoint:
   ```bash
   curl -X POST https://us-central1-vanish-auth-real.cloudfunctions.net/processGooglePlayRTDN \
     -H "Content-Type: application/json" \
     -d '{"message":{"data":"base64encodednotification"}}'
   ```

2. Test purchase verification:
   - Make a test purchase in Android app
   - Check Firestore to see subscription data
   - Verify `endDate` is set correctly

3. Test renewal (requires actual subscription):
   - Wait for Google Play to process renewal
   - Check RTDN logs in Firebase Functions
   - Verify `endDate` is updated in Firestore

## ⚠️ Important Notes

- **Service Account Key**: Keep this secure! Never commit it to git.
- **RTDN Endpoint**: Must be publicly accessible (Firebase Functions are by default)
- **Package Name**: Currently set to `com.coachie.app` - update if different
- **Fallback**: If Google Play API is not configured, system falls back to basic verification (for development)

## 🔧 Troubleshooting

**RTDN not receiving notifications:**
- Check Google Play Console > RTDN configuration
- Verify endpoint URL is correct
- Check Firebase Functions logs for errors

**Subscription not updating:**
- Check service account has correct permissions
- Verify service account key is configured correctly
- Check Firebase Functions logs for API errors

**Authentication errors:**
- Verify service account key JSON is valid
- Check service account has "View financial data" permission in Play Console
- Ensure Google Play Developer API is enabled in Google Cloud Console
