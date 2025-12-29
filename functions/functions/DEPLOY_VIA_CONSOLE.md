# Deploy processBriefTask via Google Cloud Console

Since Firebase CLI keeps timing out, the most reliable way is to deploy directly via Google Cloud Console.

## Step-by-Step Instructions

### Option 1: Update Existing Function (If it exists)

1. Go to [Google Cloud Console - Cloud Functions](https://console.cloud.google.com/functions?project=vanish-auth-real)
2. Find `processBriefTask` in the list
3. Click on it to open details
4. Click **"EDIT"** button (top right)
5. Scroll to **"Source code"** section
6. Click **"Upload ZIP"** or **"Browse"**
7. Create a ZIP file with:
   - `processBriefTask-standalone.js` (or `briefs/index.js`)
   - `lib/` directory (all compiled JavaScript)
   - `package.json` (with main pointing to entry file)
   - `node_modules/` (or let Cloud Build install them)
8. Click **"DEPLOY"**

### Option 2: Create New Function

1. Go to [Google Cloud Console - Cloud Functions](https://console.cloud.google.com/functions?project=vanish-auth-real)
2. Click **"CREATE FUNCTION"**
3. Fill in:
   - **Function name:** `processBriefTask`
   - **Region:** `us-central1`
   - **Environment:** `2nd gen`
   - **Runtime:** `Node.js 20`
   - **Entry point:** `processBriefTask`
   - **Trigger type:** `HTTP`
   - **Authentication:** `Allow unauthenticated invocations`
4. Under **"Source code"**:
   - Select **"Inline editor"** or **"Upload ZIP"**
   - If uploading ZIP, include:
     - `processBriefTask-standalone.js`
     - `lib/` directory
     - `package.json`
5. Under **"Runtime, build, connections and security settings"**:
   - **Timeout:** `540 seconds`
   - **Memory:** `512 MiB`
   - **Max instances:** `100`
6. Click **"DEPLOY"**

### Option 3: Use Cloud Build (Recommended for large codebases)

1. Go to [Google Cloud Console - Cloud Build](https://console.cloud.google.com/cloud-build/triggers?project=vanish-auth-real)
2. Create a new trigger that builds from your repository
3. Or use the inline editor in Cloud Functions and let it build automatically

## What Files to Include

When uploading source code, include:

```
functions/
├── processBriefTask-standalone.js  (or briefs/index.js)
├── lib/
│   ├── briefTaskQueue.js
│   ├── scheduledBriefs.js
│   ├── generateBrief.js
│   ├── subscriptionVerification.js
│   └── ... (all other dependencies)
├── package.json
└── node_modules/  (optional - Cloud Build can install)
```

## Verify Deployment

After deployment, test the function:

```bash
curl -X POST https://us-central1-vanish-auth-real.cloudfunctions.net/processBriefTask \
  -H "Content-Type: application/json" \
  -d '{"userId":"test","briefType":"morning"}'
```

Or check logs:
```bash
gcloud functions logs read processBriefTask --region=us-central1
```
