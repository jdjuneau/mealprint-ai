@echo off
echo.
echo ========================================
echo 🔍 CHECKING USER BRIEF STATUS
echo ========================================
echo.
echo This script will help diagnose why you didn't receive the evening brief.
echo.
echo Please provide your user ID (from Firebase Auth or Firestore users collection)
echo Or we can check recent brief processing logs
echo.
echo [1/3] Checking recent brief processing logs...
echo.
gcloud logging read "resource.type=cloud_function AND resource.labels.function_name=processBriefTask AND jsonPayload.briefType=evening" --project=vanish-auth-real --limit=20 --format="table(timestamp,severity,jsonPayload.userId,jsonPayload.briefType,jsonPayload.success,jsonPayload.notificationSent,jsonPayload.skipped)" --freshness=24h 2>&1 | Select-Object -First 25
echo.
echo [2/3] Checking for errors in brief processing...
echo.
gcloud logging read "resource.type=cloud_function AND resource.labels.function_name=processBriefTask AND severity>=ERROR" --project=vanish-auth-real --limit=10 --format="table(timestamp,severity,textPayload,jsonPayload.error)" --freshness=24h 2>&1 | Select-Object -First 15
echo.
echo [3/3] Checking Cloud Tasks queue status...
echo.
gcloud tasks list --queue=brief-generation-queue --location=us-central1 --project=vanish-auth-real --format="table(name,createTime,scheduleTime,state)" 2>&1 | Select-Object -First 10
echo.
echo ========================================
echo 📋 DIAGNOSTIC INFO
echo ========================================
echo.
echo To check your specific user:
echo 1. Get your user ID from Firebase Console or app
echo 2. Run: gcloud logging read "jsonPayload.userId=YOUR_USER_ID AND jsonPayload.briefType=evening" --project=vanish-auth-real --limit=10
echo.
echo Common issues:
echo - User not Pro: Check subscription status in Firestore
echo - No FCM token: Check fcmTokens array in user document
echo - Notifications disabled: Check nudgesEnabled field
echo - Brief generation failed: Check error logs above
echo.
pause
