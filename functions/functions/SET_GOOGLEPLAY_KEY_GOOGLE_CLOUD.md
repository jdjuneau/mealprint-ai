# 🔑 Setting Google Play Service Account Key (Google Cloud Console)

## Step-by-Step Instructions

### 1. Open Google Cloud Console

Go to: https://console.cloud.google.com/functions?project=vanish-auth-real

Or:
1. Go to https://console.cloud.google.com
2. Select project: **vanish-auth-real**
3. In the left menu, go to **Cloud Functions**

### 2. Select a Function

Click on any function (e.g., `verifyPurchase` or `processGooglePlayRTDN`)

### 3. Go to Environment Variables

1. Click the **"EDIT"** button (top right)
2. Scroll down to **"Runtime, build, connections and security settings"** section
3. Expand it
4. Click on **"Runtime environment variables"** tab
5. Click **"ADD VARIABLE"**

### 4. Add the Variable

- **Name:** `GOOGLE_PLAY_SERVICE_ACCOUNT`
- **Value:** Paste the **entire JSON content** from your service account key file

### 5. Deploy

Click **"DEPLOY"** at the bottom

---

## Alternative: Use gcloud CLI (Command Line)

If the UI doesn't work, use this command:

```powershell
# First, read your JSON file and escape it
$json = Get-Content "C:\path\to\your\service-account-key.json" -Raw

# Set the environment variable for all functions (or specific ones)
gcloud functions deploy verifyPurchase --update-env-vars GOOGLE_PLAY_SERVICE_ACCOUNT="$json" --region=us-central1 --project=vanish-auth-real
```

---

## Verify It's Set

After setting it, check one of your functions in Google Cloud Console - you should see `GOOGLE_PLAY_SERVICE_ACCOUNT` in the environment variables list.
