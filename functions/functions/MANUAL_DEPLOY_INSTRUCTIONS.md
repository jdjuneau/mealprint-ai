# 📦 Manual Deployment Instructions for Google Play Functions

## Files Ready for Deployment

The following files are compiled and ready in `functions/lib/`:
- ✅ `googlePlayRTDN.js` - RTDN webhook handler
- ✅ `verifyPurchase.js` - Purchase verification function
- ✅ `subscriptionVerification.js` - Google Play API verification utilities

## Option 1: Deploy Using Standalone Index (Recommended)

1. **Backup your current index.js:**
   ```bash
   cd functions
   cp lib/index.js lib/index.js.backup
   ```

2. **Use the standalone index:**
   ```bash
   cp index-googleplay-standalone.js lib/index.js
   ```

3. **Deploy:**
   ```bash
   cd ..
   firebase deploy --only functions:processGooglePlayRTDN,functions:verifyPurchase
   ```

4. **Restore your original index:**
   ```bash
   cd functions
   cp lib/index.js.backup lib/index.js
   ```

## Option 2: Deploy via Google Cloud Console

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Select project: `vanish-auth-real`
3. Go to **Cloud Functions**
4. Click **Create Function**
5. For each function:

### For `processGooglePlayRTDN`:
- **Name:** `processGooglePlayRTDN`
- **Region:** `us-central1`
- **Trigger:** HTTP
- **Source:** Upload the contents of `functions/lib/googlePlayRTDN.js`
- **Entry point:** `processGooglePlayRTDN`
- **Runtime:** Node.js 20
- **Dependencies:** Copy from `functions/package.json`:
  ```json
  {
    "firebase-admin": "^12.0.0",
    "firebase-functions": "^4.5.0",
    "googleapis": "^144.0.0"
  }
  ```

### For `verifyPurchase`:
- **Name:** `verifyPurchase`
- **Region:** `us-central1`
- **Trigger:** HTTPS (Callable)
- **Source:** Upload the contents of `functions/lib/verifyPurchase.js` AND `functions/lib/subscriptionVerification.js`
- **Entry point:** `verifyPurchase`
- **Runtime:** Node.js 20
- **Dependencies:** Same as above

## Option 3: Use Firebase CLI with Minimal Index

1. **Temporarily modify package.json:**
   ```json
   {
     "main": "index-googleplay-standalone.js"
   }
   ```

2. **Deploy:**
   ```bash
   firebase deploy --only functions
   ```

3. **Restore package.json:**
   ```json
   {
     "main": "lib/index.js"
   }
   ```

## Verification

After deployment, verify the functions exist:

```bash
firebase functions:list | grep -E "processGooglePlayRTDN|verifyPurchase"
```

You should see:
- `processGooglePlayRTDN` - https trigger
- `verifyPurchase` - callable trigger

## Function URLs

Once deployed, your functions will be available at:

- **RTDN Webhook:** `https://us-central1-vanish-auth-real.cloudfunctions.net/processGooglePlayRTDN`
- **Verify Purchase:** Callable via Firebase SDK (no direct URL)

## Next Steps

1. Configure Google Play service account (see `GOOGLE_PLAY_RTDN_SETUP.md`)
2. Set up RTDN in Google Play Console
3. Test with a real purchase
