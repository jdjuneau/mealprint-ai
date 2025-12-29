# Create processBriefTask via Cloud Run (2nd Gen Functions)

## Important: You're on the Wrong Tab!

You're currently viewing **"Functions (1st Gen)"** which only shows legacy functions. New functions must be created via **Cloud Run**.

## Quick Solution

### Option 1: Click the Banner Button (Easiest)

1. On the page you're viewing, look for the **blue informational banner** at the top
2. Click the **"Go to Cloud Run"** button in that banner
3. This will take you to the Cloud Run functions page where you can create new functions

### Option 2: Switch Tabs

1. Look for the tabs below "Cloud Run functions" in the header
2. Click on **"Cloud Run functions"** tab (not "Functions (1st Gen)")
3. You should now see a **"CREATE FUNCTION"** button at the top

### Option 3: Direct Link

Go directly to Cloud Run functions:
https://console.cloud.google.com/run?project=vanish-auth-real

Then click **"CREATE FUNCTION"** at the top.

## Step-by-Step: Create Function in Cloud Run

You're now on the Cloud Run Services page. Here's how to create a function:

### Step 1: Click "Write a function" Button

Look at the **top toolbar** (above the filter/search bar). You should see three buttons:
- "Deploy container"
- "Connect repo"
- **"Write a function"** ← **CLICK THIS ONE!** (has a `{...}` icon)

**OR** you can click the **"Create service"** button in the main content area, but "Write a function" is more direct for functions.

### Step 2: Fill in Basic Settings

- **Function name:** `processBriefTask`
- **Region:** `us-central1`
- **Authentication:** Select **"Allow unauthenticated invocations"**

### Step 3: Configure Runtime

Expand **"Runtime, build, connections and security settings"**:

- **Runtime:** `Node.js 20`
- **Entry point:** `processBriefTask`
- **Timeout:** `540 seconds`
- **Memory:** `512 MiB`

### Step 4: Configure Trigger

- **Trigger type:** `HTTP`
- **Require authentication:** ❌ **Unchecked**

### Step 5: Source Code

**Option A: Upload ZIP (Recommended)**

1. First, create the ZIP:
   ```powershell
   cd d:\Coachie\functions
   .\CREATE_DEPLOYMENT_ZIP.bat
   ```

2. In the console:
   - Select **"Upload ZIP"**
   - Upload `processBriefTask-deploy.zip`

**Option B: Inline Editor**

- Select **"Inline editor"**
- Paste contents of `processBriefTask-standalone.js`
- Add files from `lib/` directory

### Step 6: Deploy

Click **"DEPLOY"** and wait 2-5 minutes.

## Why This Happens

Google deprecated creating new 1st Gen functions in the console. All new functions must be:
- **2nd Gen (Cloud Run)** - Created via Cloud Run console
- **OR** Created via `gcloud CLI`, API, or Terraform

The banner on the 1st Gen page explains this and provides the "Go to Cloud Run" button.

## Summary

**You're on:** Functions (1st Gen) tab ❌  
**You need:** Cloud Run functions tab ✅

**Solution:** Click **"Go to Cloud Run"** button in the banner, or switch to the "Cloud Run functions" tab.
