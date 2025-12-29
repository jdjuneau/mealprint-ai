# Deploy processBriefTask via Google Cloud Console - Step by Step

## Prerequisites Check

Before starting, make sure:
- ✅ You have access to Google Cloud Console
- ✅ You're in the correct project: **vanish-auth-real**
- ✅ Billing is enabled
- ✅ Cloud Functions API is enabled

## Step 1: Open Cloud Run Functions (NOT 1st Gen!)

**IMPORTANT:** You must use **Cloud Run functions**, not the legacy "Functions (1st Gen)" tab!

1. Go to: https://console.cloud.google.com/run?project=vanish-auth-real
2. **OR** if you're on the Functions page:
   - Look for tabs: "Cloud Run functions" and "Functions (1st Gen)"
   - Click **"Cloud Run functions"** tab (NOT "Functions (1st Gen)")
   - You should see a banner saying "Go to Cloud Run" - click that button
3. **OR** navigate manually:
   - Go to https://console.cloud.google.com/
   - Click the hamburger menu (☰) in top left
   - Navigate to: **Serverless** → **Cloud Run** (not Cloud Functions)

## Step 2: Find the Create Button

The **"CREATE FUNCTION"** button is usually:
- **Top of the page** - Large blue/purple button
- **OR** in the **top toolbar** next to search/filter options
- **OR** if you see an empty state, there's usually a prominent "Create" button

**If you still don't see it:**

### Check Your View
- Make sure you're not in a filtered view
- Try refreshing the page (F5)
- Clear any filters/search terms

### Check Permissions
1. Click your profile icon (top right)
2. Check if you see "Cloud Functions Admin" or "Owner" role
3. If not, you need to ask your project admin for permissions

### Enable APIs (If needed)
1. Go to: https://console.cloud.google.com/apis/library?project=vanish-auth-real
2. Search for "Cloud Functions API"
3. Click it and click **"ENABLE"**
4. Also enable "Cloud Build API" and "Cloud Run API"

## Step 3: Create the Function

Once you click **"CREATE FUNCTION"**, you'll see a form. Fill it in:

### Basic Information
- **Function name:** `processBriefTask`
- **Region:** `us-central1` (select from dropdown)
- **Environment:** Select **"2nd gen"** (Gen2)

### Authentication
- Select **"Allow unauthenticated invocations"**
  - This is required because Cloud Tasks will call it

### Click "NEXT" to continue

## Step 4: Configure Runtime

### Runtime Settings
- **Runtime:** `Node.js 20` (from dropdown)
- **Entry point:** `processBriefTask` (type exactly)
- **Timeout:** `540 seconds`
- **Memory:** `512 MiB`
- **Max instances:** `100` (optional)

### Trigger
- **Trigger type:** Select **"HTTP"**
- **Require HTTPS:** ✅ Checked
- **Require authentication:** ❌ **Unchecked** (important!)

### Click "NEXT"

## Step 5: Source Code

### Option A: Upload ZIP (Easiest)

1. **First, create the ZIP:**
   ```powershell
   cd d:\Coachie\functions
   .\CREATE_DEPLOYMENT_ZIP.bat
   ```
   This creates `processBriefTask-deploy.zip`

2. **In the console:**
   - Select **"Upload ZIP"** or **"Zip upload"**
   - Click **"Browse"** or **"Upload"**
   - Select `processBriefTask-deploy.zip`
   - Wait for upload to complete

### Option B: Inline Editor

1. Select **"Inline editor"**
2. You'll see a code editor with a default function
3. Replace it with the contents of `processBriefTask-standalone.js`
4. You'll also need to add files from `lib/` directory:
   - Click **"Add file"** or use the file tree
   - Upload all `.js` files from `lib/` folder

## Step 6: Deploy

1. Scroll to bottom of the page
2. Click **"DEPLOY"** button (blue/purple, usually bottom right)
3. You'll see:
   - Build logs
   - Deployment progress
   - Usually takes 2-5 minutes

## Step 7: Verify

After deployment:
1. You'll be redirected to the function details page
2. Copy the **Trigger URL** (looks like):
   ```
   https://us-central1-vanish-auth-real.cloudfunctions.net/processBriefTask
   ```
3. Test it:
   ```powershell
   curl -X POST https://us-central1-vanish-auth-real.cloudfunctions.net/processBriefTask -H "Content-Type: application/json" -d "{\"userId\":\"test\",\"briefType\":\"morning\"}"
   ```

## Troubleshooting

### "CREATE FUNCTION" button is grayed out or missing

**Solution 1: Check Project**
- Make sure project selector shows "vanish-auth-real"
- If wrong, click it and select the correct project

**Solution 2: Check Billing**
- Go to: https://console.cloud.google.com/billing?project=vanish-auth-real
- Make sure a billing account is linked

**Solution 3: Check Permissions**
- You need "Cloud Functions Admin" role
- Contact project owner to grant permissions

**Solution 4: Enable APIs**
- Go to: https://console.cloud.google.com/apis/library?project=vanish-auth-real
- Enable: Cloud Functions API, Cloud Build API, Cloud Run API

### Function deployment fails

**Check logs:**
- Click on the function name
- Go to "Logs" tab
- Look for error messages

**Common issues:**
- Missing dependencies → Make sure `lib/` folder is included
- Wrong entry point → Must be exactly `processBriefTask`
- Timeout during build → Try uploading ZIP instead of inline editor

## Alternative: Update Existing Function

If the function already exists:

1. Go to Cloud Functions list
2. Click on `processBriefTask`
3. Click **"EDIT"** button (top right)
4. Update source code
5. Click **"DEPLOY"**
