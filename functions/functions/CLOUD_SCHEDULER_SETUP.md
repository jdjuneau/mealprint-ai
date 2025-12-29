# 🚀 MANUAL CLOUD SCHEDULER SETUP

Since `gcloud` CLI is not installed, follow these manual steps to set up automatic brief scheduling:

## Step 1: Open Google Cloud Console
Go to: https://console.cloud.google.com/

## Step 2: Navigate to Cloud Scheduler
1. Select your project: `vanish-auth-real`
2. Go to **Cloud Scheduler** in the left menu
3. Click **Create Job**

## Step 3: Create Morning Briefs Job
```
Name: morning-briefs
Frequency: 0 9 * * *  (Every day at 9 AM)
Time Zone: America/New_York
Target: HTTP
URL: https://us-central1-vanish-auth-real.cloudfunctions.net/sendMorningBriefs
HTTP Method: POST
Body: {}  (empty JSON)
Auth Header: None (functions are designed to work without auth for scheduling)
```

## Step 4: Create Afternoon Briefs Job
```
Name: afternoon-briefs
Frequency: 0 14 * * *  (Every day at 2 PM)
Time Zone: America/New_York
Target: HTTP
URL: https://us-central1-vanish-auth-real.cloudfunctions.net/sendAfternoonBriefs
HTTP Method: POST
Body: {}  (empty JSON)
Auth Header: None
```

## Step 5: Create Evening Briefs Job
```
Name: evening-briefs
Frequency: 0 18 * * *  (Every day at 6 PM)
Time Zone: America/New_York
Target: HTTP
URL: https://us-central1-vanish-auth-real.cloudfunctions.net/sendEveningBriefs
HTTP Method: POST
Body: {}  (empty JSON)
Auth Header: None
```

## Step 6: Test the Jobs
After creating each job:
1. Click on the job name
2. Click **Force Run**
3. Check Firebase Functions logs to see if briefs were sent

## ✅ RESULT
Once set up, your app will automatically send:
- 🌅 Morning briefs at 9 AM EST daily
- ☀️ Afternoon briefs at 2 PM EST daily (Pro users only)
- 🌙 Evening briefs at 6 PM EST daily (Pro users only)

This gives you **fully automatic brief scheduling** as designed - no manual intervention required!

## Alternative: GitHub Actions (If Preferred)
If you prefer not to use Cloud Scheduler, we can set up GitHub Actions to trigger the functions daily.

**Your briefs will work automatically once Cloud Scheduler is configured!** ⏰