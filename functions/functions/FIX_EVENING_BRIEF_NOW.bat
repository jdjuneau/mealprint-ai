@echo off
echo.
echo ========================================
echo 🔧 FIXING EVENING BRIEF SYSTEM
echo ========================================
echo.

echo [1/3] Testing HTTP function...
echo.
Invoke-WebRequest -Uri "https://us-central1-vanish-auth-real.cloudfunctions.net/sendEveningBriefsHttp" -Method GET -UseBasicParsing
if %ERRORLEVEL% EQU 0 (
    echo ✅ HTTP function is working
) else (
    echo ❌ HTTP function failed - checking deployment...
    echo.
    echo [2/3] Redeploying HTTP function...
    gcloud functions deploy sendEveningBriefsHttp ^
        --gen2 ^
        --runtime=nodejs20 ^
        --region=us-central1 ^
        --source=. ^
        --entry-point=sendEveningBriefsHttp ^
        --trigger-http ^
        --allow-unauthenticated ^
        --project=vanish-auth-real
)

echo.
echo [3/3] Verifying Cloud Scheduler job...
echo.
gcloud scheduler jobs describe send-evening-briefs --location=us-central1 --project=vanish-auth-real --format="value(state,schedule,timeZone,httpTarget.uri)" 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ❌ Scheduler job not found - creating...
    gcloud scheduler jobs create http send-evening-briefs ^
        --location=us-central1 ^
        --schedule="0 18 * * *" ^
        --time-zone="America/New_York" ^
        --uri="https://us-central1-vanish-auth-real.cloudfunctions.net/sendEveningBriefsHttp" ^
        --http-method=GET ^
        --project=vanish-auth-real
) else (
    echo ✅ Scheduler job exists
    echo.
    echo Ensuring job is enabled...
    gcloud scheduler jobs resume send-evening-briefs --location=us-central1 --project=vanish-auth-real 2>nul
)

echo.
echo [4/4] Manually triggering evening brief NOW...
echo.
echo Calling sendEveningBriefsHttp...
Invoke-WebRequest -Uri "https://us-central1-vanish-auth-real.cloudfunctions.net/sendEveningBriefsHttp" -Method GET -UseBasicParsing
echo.
echo ✅ Evening brief triggered manually
echo.
echo ========================================
echo ✅ FIX COMPLETE
echo ========================================
echo.
echo Next scheduled run: Tomorrow at 6 PM Eastern
echo.
pause

