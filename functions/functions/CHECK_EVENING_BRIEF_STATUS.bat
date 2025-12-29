@echo off
echo.
echo ========================================
echo 🔍 CHECKING EVENING BRIEF STATUS
echo ========================================
echo.

REM Check if Cloud Scheduler job exists
echo [1/4] Checking Cloud Scheduler job...
echo.
gcloud scheduler jobs describe send-evening-briefs --location=us-central1 --project=vanish-auth-real 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ❌ Cloud Scheduler job 'send-evening-briefs' NOT FOUND!
    echo.
    echo 💡 Create it with: functions\CREATE_CLOUD_SCHEDULER_JOBS.bat
) else (
    echo ✅ Cloud Scheduler job exists
    echo.
    echo Checking if job is enabled...
    gcloud scheduler jobs describe send-evening-briefs --location=us-central1 --project=vanish-auth-real --format="value(state)" | findstr /i "ENABLED"
    if %ERRORLEVEL% EQU 0 (
        echo ✅ Job is ENABLED
    ) else (
        echo ❌ Job is DISABLED - enabling now...
        gcloud scheduler jobs resume send-evening-briefs --location=us-central1 --project=vanish-auth-real
    )
)

echo.
echo [2/4] Checking if HTTP function is deployed...
echo.
gcloud functions describe sendEveningBriefsHttp --region=us-central1 --project=vanish-auth-real 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ❌ HTTP function 'sendEveningBriefsHttp' NOT DEPLOYED!
    echo.
    echo 💡 Deploy with: functions\DEPLOY_BRIEFS_HTTP_FUNCTIONS.bat
) else (
    echo ✅ HTTP function is deployed
)

echo.
echo [3/4] Checking recent Cloud Scheduler execution logs...
echo.
echo Last 5 executions:
gcloud scheduler jobs describe send-evening-briefs --location=us-central1 --project=vanish-auth-real --format="value(schedule,timeZone)" 2>nul
echo.
echo Checking Cloud Functions logs for sendEveningBriefsHttp...
gcloud functions logs read sendEveningBriefsHttp --region=us-central1 --project=vanish-auth-real --limit=10 --format="table(timestamp,severity,textPayload)" 2>nul

echo.
echo [4/4] Testing HTTP function manually...
echo.
echo Calling sendEveningBriefsHttp...
curl -X GET "https://us-central1-vanish-auth-real.cloudfunctions.net/sendEveningBriefsHttp" 2>nul
if %ERRORLEVEL% EQU 0 (
    echo ✅ HTTP function responded
) else (
    echo ❌ HTTP function failed or not accessible
)

echo.
echo ========================================
echo 📋 SUMMARY
echo ========================================
echo.
echo Next steps:
echo 1. If Cloud Scheduler job missing: Run functions\CREATE_CLOUD_SCHEDULER_JOBS.bat
echo 2. If HTTP function missing: Run functions\DEPLOY_BRIEFS_HTTP_FUNCTIONS.bat
echo 3. If job disabled: It will be enabled automatically above
echo 4. Check logs: gcloud functions logs read sendEveningBriefsHttp --region=us-central1 --limit=50
echo.
pause

