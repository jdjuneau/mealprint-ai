# Fix: Container Healthcheck Failed

The deployment is failing because the container isn't starting properly. This is a common issue with Gen2 Cloud Functions.

## The Problem

The error says:
> "Container Healthcheck failed. The user-provided container failed to start and listen on the port defined provided by the PORT=8080 environment variable"

This means the function code isn't initializing correctly when deployed as a Cloud Run service.

## Solution: Check the Logs

1. **Click the logs URL** from the error message:
   ```
   https://console.cloud.google.com/logs/viewer?project=vanish-auth-real&resource=cloud_run_revision/service_name/processbrieftask/revision_name/processbrieftask-00001-log
   ```

2. **Look for errors** like:
   - Module not found
   - Syntax errors
   - Initialization failures
   - Missing dependencies

## Common Fixes

### Fix 1: Ensure All Dependencies Are Installed

The function might be missing `node_modules`. Make sure `package.json` dependencies are installed:

```powershell
cd d:\Coachie\functions
npm install
npm run build
```

### Fix 2: Verify Entry Point

The entry point file (`index-processBriefTask.js`) must:
- Export `processBriefTask` correctly
- Initialize Firebase Admin
- Import the function from `lib/briefTaskQueue.js`

### Fix 3: Check Function Export

The function in `lib/briefTaskQueue.js` must be properly exported as an `onRequest` handler.

## Try Deployment Again

After checking logs and fixing any issues:

```powershell
cd d:\Coachie\functions
.\DEPLOY_PROCESS_BRIEF_TASK_WORKING.bat
```

## Alternative: Deploy via Console

If command line keeps failing, use the Google Cloud Console form and upload a ZIP file with all dependencies included.
