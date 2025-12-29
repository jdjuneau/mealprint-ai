# Quick Fix for processBriefTask Deployment Timeout

## Problem
Firebase CLI times out when trying to analyze the codebase: `Error: User code failed to load. Cannot determine backend specification. Timeout after 10000`

## Solution

### Option 1: Use the Deployment Script (Recommended)

```powershell
cd d:\Coachie\functions
.\DEPLOY_PROCESS_BRIEF_TASK_FIXED.bat
```

This script:
1. Builds TypeScript
2. Temporarily changes `package.json` main to `briefs/index.js`
3. Deploys only `processBriefTask`
4. Restores `package.json`

### Option 2: Manual Deployment

**Step 1: Navigate to functions directory**
```powershell
cd d:\Coachie\functions
```

**Step 2: Build TypeScript**
```powershell
npm run build
```

**Step 3: Temporarily change package.json**
```powershell
# Backup
copy package.json package.json.backup

# Change main to briefs/index.js
# Edit package.json and change: "main": "briefs/index.js"
```

**Step 4: Deploy**
```powershell
cd ..
firebase deploy --only functions:processBriefTask --force
```

**Step 5: Restore package.json**
```powershell
cd functions
copy package.json.backup package.json
del package.json.backup
```

### Option 3: Deploy via Google Cloud Console

1. Go to [Google Cloud Console - Cloud Functions](https://console.cloud.google.com/functions?project=vanish-auth-real)
2. Find `processBriefTask` function
3. Click "Edit" or redeploy from source
4. Or use gcloud CLI:
   ```bash
   gcloud functions deploy processBriefTask --gen2 --runtime nodejs18 --source . --entry-point processBriefTask --trigger-http --region us-central1 --project vanish-auth-real
   ```

### Option 4: Deploy All Brief Functions Together

Sometimes deploying multiple functions together works better:

```powershell
cd d:\Coachie\functions
# Set package.json main to briefs/index.js
# Then:
cd ..
firebase deploy --only functions:sendMorningBriefs,functions:sendAfternoonBriefs,functions:sendEveningBriefs,functions:processBriefTask --force
```

## Why This Works

The timeout happens because Firebase CLI tries to analyze ALL TypeScript files in `src/` and `src_hidden/`. By pointing `package.json` main to `briefs/index.js`, Firebase only analyzes that file and its dependencies (which are already compiled in `lib/`).

## Verification

After deployment, verify the function exists:
```powershell
firebase functions:list | findstr processBriefTask
```

Or check the URL:
```
https://us-central1-vanish-auth-real.cloudfunctions.net/processBriefTask
```
