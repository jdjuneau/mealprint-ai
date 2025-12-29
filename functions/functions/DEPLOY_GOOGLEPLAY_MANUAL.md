# 🚀 Manual Deployment Guide for Google Play Functions

Since Firebase CLI is timing out during code analysis, here are alternative deployment methods:

## ✅ Files Ready

All compiled files are in `functions/lib/`:
- `googlePlayRTDN.js` ✅
- `verifyPurchase.js` ✅  
- `subscriptionVerification.js` ✅ (dependency)

## Method 1: Direct gcloud Deployment (Recommended)

### Prerequisites
1. Install [Google Cloud SDK](https://cloud.google.com/sdk/docs/install)
2. Authenticate: `gcloud auth login`
3. Set project: `gcloud config set project vanish-auth-real`

### Deploy via Script
```bash
cd functions
deploy-googleplay-direct.bat
```

### Or Deploy Manually

**Deploy processGooglePlayRTDN:**
```bash
cd functions
gcloud functions deploy processGooglePlayRTDN \
  --gen2 \
  --runtime=nodejs20 \
  --region=us-central1 \
  --source=lib \
  --entry-point=processGooglePlayRTDN \
  --trigger-http \
  --allow-unauthenticated \
  --project=vanish-auth-real
```

**Deploy verifyPurchase:**
```bash
gcloud functions deploy verifyPurchase \
  --gen2 \
  --runtime=nodejs20 \
  --region=us-central1 \
  --source=lib \
  --entry-point=verifyPurchase \
  --trigger-http \
  --allow-unauthenticated \
  --project=vanish-auth-real
```

## Method 2: Google Cloud Console

1. Go to [Cloud Functions](https://console.cloud.google.com/functions)
2. Select project: `vanish-auth-real`
3. Click **Create Function**

### For processGooglePlayRTDN:
- **Name:** `processGooglePlayRTDN`
- **Region:** `us-central1`
- **Trigger:** HTTP
- **Runtime:** Node.js 20
- **Entry point:** `processGooglePlayRTDN`
- **Source:** Upload `functions/lib/googlePlayRTDN.js` and `functions/lib/subscriptionVerification.js`
- **Dependencies:** Copy `package.json` dependencies:
  ```json
  {
    "firebase-admin": "^12.0.0",
    "firebase-functions": "^4.5.0",
    "googleapis": "^144.0.0"
  }
  ```

### For verifyPurchase:
- **Name:** `verifyPurchase`
- **Region:** `us-central1`
- **Trigger:** HTTPS (Callable)
- **Runtime:** Node.js 20
- **Entry point:** `verifyPurchase`
- **Source:** Upload `functions/lib/verifyPurchase.js` and `functions/lib/subscriptionVerification.js`
- **Dependencies:** Same as above

## Method 3: Fix TypeScript Errors (Long-term)

The timeout is caused by TypeScript errors in other files. To fix:

1. Install missing dependency:
   ```bash
   cd functions
   npm install expo-server-sdk
   ```

2. Fix TypeScript errors in:
   - `src/circadianOptimization.ts` (line 30)
   - `src/circleVents.ts` (line 196)
   - `src/generateDailyHabits.ts` (line 422)
   - `src/predictiveHabits.ts` (lines 280, 343, 351)
   - `src/index.ts` (line 25)
   - `src/circleCheckInNotifications.ts` (return statements)

3. Then deploy normally:
   ```bash
   firebase deploy --only functions:processGooglePlayRTDN,functions:verifyPurchase
   ```

## Verification

After deployment, verify functions exist:

```bash
firebase functions:list | findstr "processGooglePlayRTDN verifyPurchase"
```

Or check in [Cloud Console](https://console.cloud.google.com/functions)

## Function URLs

Once deployed:
- **RTDN Webhook:** `https://us-central1-vanish-auth-real.cloudfunctions.net/processGooglePlayRTDN`
- **Verify Purchase:** Callable via Firebase SDK (no direct URL)

## Next Steps

1. Configure Google Play service account (see `GOOGLE_PLAY_RTDN_SETUP.md`)
2. Set up RTDN endpoint in Google Play Console
3. Test with a real purchase
