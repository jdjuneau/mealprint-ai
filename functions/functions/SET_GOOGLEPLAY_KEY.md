# 🔑 Setting Google Play Service Account Key

## Quick Setup (Windows)

### Method 1: Use the PowerShell Script (Easiest)

1. Save your service account JSON file somewhere (e.g., `C:\Users\jayju\Downloads\coachie-google-play-key.json`)

2. Run this command:
   ```powershell
   cd d:\Coachie\functions
   .\set-googleplay-key.ps1 "C:\Users\jayju\Downloads\coachie-google-play-key.json"
   ```
   (Replace the path with where you saved your JSON file)

### Method 2: Use Firebase Console (Recommended)

1. Open your service account JSON file
2. Copy the **entire contents** (all of it, including the curly braces)
3. Go to [Firebase Console - Functions Config](https://console.firebase.google.com/project/vanish-auth-real/functions/config)
4. Click **"Add variable"**
5. Enter:
   - **Name:** `GOOGLE_PLAY_SERVICE_ACCOUNT`
   - **Value:** Paste the entire JSON content
6. Click **Save**

### Method 3: Manual PowerShell (If script doesn't work)

```powershell
cd d:\Coachie\functions

# Read the JSON file
$json = Get-Content "C:\path\to\your\service-account-key.json" -Raw

# Escape quotes for Firebase config
$jsonEscaped = $json -replace '\\', '\\' -replace '"', '\"'

# Set config (using single quotes to avoid PowerShell parsing issues)
firebase functions:config:set googleplay.service_account_key='$json'
```

## Verify It's Set

```powershell
firebase functions:config:get googleplay
```

You should see the `service_account_key` field.

## Next Steps

After setting the key, deploy the functions:
```powershell
cd d:\Coachie\functions
npm run build
firebase deploy --only functions:processGooglePlayRTDN,functions:verifyPurchase
```
