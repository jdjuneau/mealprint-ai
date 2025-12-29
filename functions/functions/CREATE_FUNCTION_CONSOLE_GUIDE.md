# How to Create processBriefTask Function in Google Cloud Console

## Step-by-Step Instructions

### Step 1: Navigate to Cloud Run Functions (NOT 1st Gen!)

**CRITICAL:** You're probably on the "Functions (1st Gen)" tab. You need **Cloud Run functions** for new deployments!

1. Go to: https://console.cloud.google.com/run?project=vanish-auth-real
2. **OR** if you're already on the Functions page:
   - Look for the blue banner at the top that says "Go to Cloud Run"
   - **Click the "Go to Cloud Run" button** in that banner
   - This takes you to the correct page for creating new functions
3. **OR** switch tabs:
   - If you see tabs "Cloud Run functions" and "Functions (1st Gen)"
   - Click **"Cloud Run functions"** tab (NOT "Functions (1st Gen)")

**Why:** Google deprecated creating new 1st Gen functions in the console. All new functions must be 2nd Gen (Cloud Run).

### Step 2: Create Function Button

1. At the top of the Cloud Functions page, you should see a **"CREATE FUNCTION"** button (usually blue/purple)
   - If you don't see it, make sure you have the correct permissions
   - You need "Cloud Functions Admin" or "Owner" role
2. Click **"CREATE FUNCTION"**

### Step 3: Fill in Basic Settings

**Environment:**
- Select **"2nd gen"** (Gen2)

**Basic Settings:**
- **Function name:** `processBriefTask`
- **Region:** Select `us-central1` from dropdown
- **Authentication:** Select **"Allow unauthenticated invocations"** (since it's called by Cloud Tasks)

### Step 4: Configure Runtime

**Runtime, build, connections and security settings:**
- Click to expand this section
- **Runtime:** Select `Node.js 20` from dropdown
- **Entry point:** Type `processBriefTask` (must match the export name)
- **Timeout:** Set to `540 seconds` (9 minutes)
- **Memory:** Set to `512 MiB`
- **Max instances:** `100` (optional)

### Step 5: Configure Trigger

**Trigger type:**
- Select **"HTTP"**
- Under "Require HTTPS": Leave checked
- Under "Require authentication": Leave **unchecked** (unauthenticated)

### Step 6: Source Code

You have two options:

#### Option A: Inline Editor (Easier for testing)

1. Select **"Inline editor"**
2. In the code editor, you'll see a default function
3. Replace it with the contents of `processBriefTask-standalone.js`
4. You'll also need to upload the `lib/` folder contents
   - Click "Add file" and upload files from `lib/` directory

#### Option B: Upload ZIP (Recommended)

1. Select **"Zip upload"** or **"Upload ZIP"**
2. First, create a ZIP file with these contents:
   ```
   processBriefTask-standalone.js
   lib/
     ├── briefTaskQueue.js
     ├── scheduledBriefs.js
     ├── generateBrief.js
     ├── subscriptionVerification.js
     └── ... (all other .js files in lib/)
   package.json
   ```
3. Click **"Browse"** or **"Upload"** and select your ZIP file

**Note:** You can let Cloud Build install `node_modules` automatically, or include them in the ZIP.

### Step 7: Deploy

1. Scroll to the bottom
2. Click **"DEPLOY"** button (blue/purple, usually at bottom right)
3. Wait for deployment (usually 2-5 minutes)
4. You'll see build logs and deployment progress

### Step 8: Verify

After deployment succeeds:
1. You'll see the function in the list
2. Click on `processBriefTask` to see details
3. Copy the **Trigger URL** (looks like: `https://us-central1-vanish-auth-real.cloudfunctions.net/processBriefTask`)

## If You Don't See "CREATE FUNCTION" Button

### Check 1: Permissions
- You need "Cloud Functions Admin" or "Owner" role
- Ask your project admin to grant you permissions
- Or use a service account with proper permissions

### Check 2: APIs Enabled
Make sure these APIs are enabled:
1. Go to [APIs & Services](https://console.cloud.google.com/apis/library?project=vanish-auth-real)
2. Search for and enable:
   - **Cloud Functions API**
   - **Cloud Build API**
   - **Cloud Run API** (for Gen2)

### Check 3: Billing
- Cloud Functions requires billing to be enabled
- Go to [Billing](https://console.cloud.google.com/billing?project=vanish-auth-real)
- Make sure a billing account is linked

### Check 4: Project Selection
- Make sure you're in the correct project: **vanish-auth-real**
- Check the project selector at the top of the page

## Alternative: Use gcloud CLI

If the console doesn't work, you can create it via command line:

```powershell
cd d:\Coachie\functions

# Make sure package.json main points to standalone file
# (You already did this)

# Create the function
gcloud functions deploy processBriefTask ^
    --gen2 ^
    --runtime=nodejs20 ^
    --region=us-central1 ^
    --source=. ^
    --entry-point=processBriefTask ^
    --trigger-http ^
    --allow-unauthenticated ^
    --project=vanish-auth-real ^
    --timeout=540s ^
    --memory=512Mi
```

## Quick ZIP Creation Script

I can create a script to automatically create the ZIP file with all required files. Would you like me to create that?
