# Quick Guide: Create processBriefTask Function

## You're on the Right Page!

You're on the **Cloud Run Services** page. Here's exactly what to do:

## Step 1: Click "Write a function" Button

Look at the **top toolbar** (above any filters or search bars). You should see:

```
[Deploy container]  [Connect repo]  [Write a function {...}]
```

**Click the "Write a function" button** (the one with the `{...}` icon).

## Step 2: Fill in the Form

After clicking "Write a function", you'll see a form. Fill it in:

### Basic Settings
- **Function name:** `processBriefTask`
- **Region:** `us-central1`
- **Authentication:** Select **"Allow unauthenticated invocations"**

### Runtime Settings (Expand this section)
- **Runtime:** `Node.js 20` or `Node.js 22` (both work)
- **Entry point:** `processBriefTask` (this is the function export name - can have uppercase)
- **Timeout:** `540 seconds`
- **Memory:** `512 MiB`

### Trigger
- **Trigger type:** `HTTP`
- **Require authentication:** ❌ **Unchecked**

### Source Code

**Option 1: Upload ZIP (Easiest)**

1. First, create the ZIP:
   ```powershell
   cd d:\Coachie\functions
   .\CREATE_DEPLOYMENT_ZIP.bat
   ```

2. In the form:
   - Select **"Upload ZIP"** or **"Zip upload"**
   - Click **"Browse"** or **"Upload"**
   - Select `processBriefTask-deploy.zip`

**Option 2: Inline Editor**

- Select **"Inline editor"**
- Paste contents of `processBriefTask-standalone.js`
- Add files from `lib/` directory using "Add file"

## Step 3: Deploy

Click **"DEPLOY"** button (usually at bottom right) and wait 2-5 minutes.

## That's It!

After deployment, you'll get a URL like:
```
https://us-central1-vanish-auth-real.cloudfunctions.net/processBriefTask
```

## If You Don't See "Write a function" Button

- Make sure you're on the **Cloud Run Services** page (not Functions 1st Gen)
- Check the top toolbar - it should be there
- Try refreshing the page (F5)
- Make sure you have "Cloud Run Admin" or "Owner" permissions
