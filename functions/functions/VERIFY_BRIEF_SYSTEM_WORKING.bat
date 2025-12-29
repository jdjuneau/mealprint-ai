@echo off
echo.
echo ========================================
echo ✅ VERIFYING BRIEF SYSTEM IS WORKING
echo ========================================
echo.

echo [1/4] Checking processBriefTask function...
gcloud functions describe processBriefTask --region=us-central1 --project=vanish-auth-real --format="value(name,state)" 2>nul
if %ERRORLEVEL% EQU 0 (
    echo ✅ processBriefTask is deployed
) else (
    echo ❌ processBriefTask NOT deployed
)

echo.
echo [2/4] Checking Cloud Tasks queue...
gcloud tasks list --queue=brief-generation-queue --location=us-central1 --project=vanish-auth-real --limit=3 --format="table(name,state,lastAttemptTime)" 2>&1 | Select-Object -First 5

echo.
echo [3/4] Checking recent brief processing logs...
gcloud logging read "resource.type=cloud_function AND resource.labels.function_name=processBriefTask AND jsonPayload.briefType=evening" --project=vanish-auth-real --limit=5 --format="table(timestamp,severity,jsonPayload.userId,jsonPayload.success,jsonPayload.notificationSent)" --freshness=1h 2>&1 | Select-Object -First 10

echo.
echo [4/4] Testing evening brief trigger...
$response = Invoke-WebRequest -Uri "https://us-central1-vanish-auth-real.cloudfunctions.net/sendEveningBriefsHttp" -Method GET -UseBasicParsing
echo Status: $response.StatusCode
$content = $response.Content | ConvertFrom-Json
echo Tasks Enqueued: $($content.result.tasksEnqueued)

echo.
echo ========================================
echo 📋 SUMMARY
echo ========================================
echo.
echo If processBriefTask is deployed and tasks are enqueued:
echo   - Briefs should process automatically
echo   - Check your app for notifications
echo   - Check logs above for processing status
echo.
pause
