@echo off
REM Create Cloud Scheduler jobs for brief HTTP functions
REM Run this AFTER deploying the HTTP functions

echo.
echo ========================================
echo 📅 CREATING CLOUD SCHEDULER JOBS
echo ========================================
echo.

REM Check gcloud availability
where gcloud >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ❌ gcloud CLI not found!
    echo.
    echo 💡 Install from: https://cloud.google.com/sdk/docs/install
    pause
    exit /b 1
)

echo.
echo Creating Cloud Scheduler jobs...
echo.

REM Create morning brief scheduler job (9 AM Eastern)
echo [1/3] Creating morning brief scheduler (9 AM Eastern)...
gcloud scheduler jobs create http send-morning-briefs ^
    --location=us-central1 ^
    --schedule="0 9 * * *" ^
    --time-zone="America/New_York" ^
    --uri="https://us-central1-vanish-auth-real.cloudfunctions.net/sendMorningBriefsHttp" ^
    --http-method=GET ^
    --project=vanish-auth-real

if %ERRORLEVEL% NEQ 0 (
    echo ⚠️ Morning brief job may already exist, trying to update...
    gcloud scheduler jobs update http send-morning-briefs ^
        --location=us-central1 ^
        --schedule="0 9 * * *" ^
        --time-zone="America/New_York" ^
        --uri="https://us-central1-vanish-auth-real.cloudfunctions.net/sendMorningBriefsHttp" ^
        --http-method=GET ^
        --project=vanish-auth-real
)

REM Create afternoon brief scheduler job (2 PM Eastern)
echo.
echo [2/3] Creating afternoon brief scheduler (2 PM Eastern)...
gcloud scheduler jobs create http send-afternoon-briefs ^
    --location=us-central1 ^
    --schedule="0 14 * * *" ^
    --time-zone="America/New_York" ^
    --uri="https://us-central1-vanish-auth-real.cloudfunctions.net/sendAfternoonBriefsHttp" ^
    --http-method=GET ^
    --project=vanish-auth-real

if %ERRORLEVEL% NEQ 0 (
    echo ⚠️ Afternoon brief job may already exist, trying to update...
    gcloud scheduler jobs update http send-afternoon-briefs ^
        --location=us-central1 ^
        --schedule="0 14 * * *" ^
        --time-zone="America/New_York" ^
        --uri="https://us-central1-vanish-auth-real.cloudfunctions.net/sendAfternoonBriefsHttp" ^
        --http-method=GET ^
        --project=vanish-auth-real
)

REM Create evening brief scheduler job (6 PM Eastern)
echo.
echo [3/3] Creating evening brief scheduler (6 PM Eastern)...
gcloud scheduler jobs create http send-evening-briefs ^
    --location=us-central1 ^
    --schedule="0 18 * * *" ^
    --time-zone="America/New_York" ^
    --uri="https://us-central1-vanish-auth-real.cloudfunctions.net/sendEveningBriefsHttp" ^
    --http-method=GET ^
    --project=vanish-auth-real

if %ERRORLEVEL% NEQ 0 (
    echo ⚠️ Evening brief job may already exist, trying to update...
    gcloud scheduler jobs update http send-evening-briefs ^
        --location=us-central1 ^
        --schedule="0 18 * * *" ^
        --time-zone="America/New_York" ^
        --uri="https://us-central1-vanish-auth-real.cloudfunctions.net/sendEveningBriefsHttp" ^
        --http-method=GET ^
        --project=vanish-auth-real
)

echo.
echo ========================================
echo ✅ Cloud Scheduler jobs created/updated!
echo ========================================
echo.
echo 📋 Verify jobs are enabled:
echo    gcloud scheduler jobs list --location=us-central1
echo.

pause
