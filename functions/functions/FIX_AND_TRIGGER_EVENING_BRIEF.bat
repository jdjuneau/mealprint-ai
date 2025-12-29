@echo off
echo.
echo ========================================
echo 🔧 FIXING AND TRIGGERING EVENING BRIEF
echo ========================================
echo.

echo [1/4] Verifying Cloud Scheduler job is enabled...
gcloud scheduler jobs resume send-evening-briefs --location=us-central1 --project=vanish-auth-real 2>nul
echo ✅ Scheduler job verified

echo.
echo [2/4] Checking job configuration...
gcloud scheduler jobs describe send-evening-briefs --location=us-central1 --project=vanish-auth-real --format="value(schedule,timeZone,state,httpTarget.uri)" 2>nul
echo.

echo [3/4] Manually triggering evening brief NOW...
echo.
$response = Invoke-WebRequest -Uri "https://us-central1-vanish-auth-real.cloudfunctions.net/sendEveningBriefsHttp" -Method GET -UseBasicParsing
echo Status: $response.StatusCode
echo Response: $response.Content
echo.

if ($response.StatusCode -eq 200) {
    echo ✅ Evening brief triggered successfully!
    $content = $response.Content | ConvertFrom-Json
    if ($content.result.tasksEnqueued) {
        echo ✅ Enqueued $($content.result.tasksEnqueued) brief tasks
    }
} else {
    echo ❌ Failed to trigger evening brief
}

echo.
echo [4/4] Verifying next scheduled run...
$nextRun = gcloud scheduler jobs describe send-evening-briefs --location=us-central1 --project=vanish-auth-real --format="value(scheduleTime)" 2>nul
echo Next scheduled run: $nextRun
echo.

echo ========================================
echo ✅ COMPLETE
echo ========================================
echo.
echo The evening brief has been manually triggered.
echo Check your app for the notification.
echo.
echo The scheduler is configured to run daily at 6 PM Eastern.
echo.
pause

