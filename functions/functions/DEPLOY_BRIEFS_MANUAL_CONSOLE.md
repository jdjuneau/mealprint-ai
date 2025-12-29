# Manual Deployment: Brief Functions via Google Cloud Console

## The Problem

Firebase CLI keeps timing out when trying to deploy brief functions, even with minimal entry points. This is because Firebase CLI needs to load and analyze all dependencies before deployment.

## Solution: Manual Deployment via Google Cloud Console

Since Firebase CLI is unreliable, we'll deploy the scheduled functions manually via Google Cloud Console. This bypasses the CLI timeout issue entirely.

## Step 1: Deploy processBriefTask (Already Done)

✅ `processBriefTask` is already deployed via `gcloud` CLI.

## Step 2: Create Scheduled Functions Manually

The scheduled functions (`sendMorningBriefs`, `sendAfternoonBriefs`, `sendEveningBriefs`) need to be created manually because:

1. They use `functions.pubsub.schedule()` which creates Cloud Scheduler jobs
2. Firebase CLI can't deploy them due to timeout
3. Manual creation in console is more reliable

### Option A: Use Cloud Scheduler + HTTP Functions (Recommended)

Instead of `pubsub.schedule`, create HTTP functions and schedule them with Cloud Scheduler:

1. **Create HTTP functions** (can deploy via gcloud):
   - `sendMorningBriefsHttp` - HTTP endpoint
   - `sendAfternoonBriefsHttp` - HTTP endpoint  
   - `sendEveningBriefsHttp` - HTTP endpoint

2. **Create Cloud Scheduler jobs** that call these HTTP endpoints:
   - Morning: 9 AM Eastern → calls `sendMorningBriefsHttp`
   - Afternoon: 2 PM Eastern → calls `sendAfternoonBriefsHttp`
   - Evening: 6 PM Eastern → calls `sendEveningBriefsHttp`

### Option B: Manual Console Deployment (If Option A doesn't work)

1. Go to Google Cloud Console → Cloud Functions
2. Click "CREATE FUNCTION"
3. Use "Write a function" (not "Create service")
4. Configure each function manually

## Step 3: Use Manual Trigger Functions (Temporary)

Until scheduled functions are deployed, use the manual trigger functions:

- `triggerMorningBrief` - Callable function
- `triggerAfternoonBrief` - Callable function
- `triggerEveningBrief` - Callable function

These can be called from Firebase Console → Functions → Test tab.

---

**Status:** Manual deployment required due to Firebase CLI timeout issues
