@echo off
REM Deploy brief functions as HTTP endpoints (can be scheduled via Cloud Scheduler)
REM This bypasses Firebase CLI timeout by using gcloud CLI

echo.
echo ========================================
echo 🚀 DEPLOYING BRIEF FUNCTIONS AS HTTP
echo ========================================
echo.
echo This will deploy HTTP functions that can be called by Cloud Scheduler.
echo.

cd /d "%~dp0"

echo.
echo 🔨 Building TypeScript...
call npm run build

if not exist "lib\scheduledBriefs.js" (
    echo ❌ Error: lib/scheduledBriefs.js not found after build!
    pause
    exit /b 1
)

echo.
echo 📝 Creating HTTP wrapper functions...
echo.

REM Create HTTP wrapper entry point
(
echo // HTTP wrapper for scheduled brief functions
echo // These can be called by Cloud Scheduler
echo const functions = require^('firebase-functions'^);
echo const admin = require^('firebase-admin'^);
echo.
echo if ^(!admin.apps.length^) {
echo   admin.initializeApp^(^);
echo }
echo.
echo // HTTP wrapper for morning briefs
echo exports.sendMorningBriefsHttp = functions.https.onRequest^(async ^(req, res^) =^> {
echo   const scheduledBriefs = require^('./lib/scheduledBriefs'^);
echo   const { enqueueBriefTasks } = require^('./lib/briefTaskQueue'^);
echo   try {
echo     const result = await enqueueBriefTasks^('morning'^);
echo     res.status^(200^).json^(^{ success: true, result ^}^);
echo   } catch ^(error^) {
echo     console.error^('Error:', error^);
echo     res.status^(500^).json^(^{ success: false, error: error.message ^}^);
echo   }
echo }^);
echo.
echo // HTTP wrapper for afternoon briefs
echo exports.sendAfternoonBriefsHttp = functions.https.onRequest^(async ^(req, res^) =^> {
echo   const scheduledBriefs = require^('./lib/scheduledBriefs'^);
echo   const { enqueueBriefTasks } = require^('./lib/briefTaskQueue'^);
echo   try {
echo     const result = await enqueueBriefTasks^('afternoon'^);
echo     res.status^(200^).json^(^{ success: true, result ^}^);
echo   } catch ^(error^) {
echo     console.error^('Error:', error^);
echo     res.status^(500^).json^(^{ success: false, error: error.message ^}^);
echo   }
echo }^);
echo.
echo // HTTP wrapper for evening briefs
echo exports.sendEveningBriefsHttp = functions.https.onRequest^(async ^(req, res^) =^> {
echo   const scheduledBriefs = require^('./lib/scheduledBriefs'^);
echo   const { enqueueBriefTasks } = require^('./lib/briefTaskQueue'^);
echo   try {
echo     const result = await enqueueBriefTasks^('evening'^);
echo     res.status^(200^).json^(^{ success: true, result ^}^);
echo   } catch ^(error^) {
echo     console.error^('Error:', error^);
echo     res.status^(500^).json^(^{ success: false, error: error.message ^}^);
echo   }
echo }^);
) > index-briefs-http.js

echo.
echo 📝 Backing up package.json...
copy package.json package.json.backup >nul 2>&1

echo.
echo 📝 Temporarily changing package.json main to index-briefs-http.js...
powershell -NoProfile -Command "$json = Get-Content package.json -Raw | ConvertFrom-Json; $json.main = 'index-briefs-http.js'; $json | ConvertTo-Json -Depth 10 | Set-Content package.json"

echo.
echo 🚀 Deploying HTTP functions via gcloud...
echo.

REM Deploy morning briefs HTTP function
echo Deploying sendMorningBriefsHttp...
gcloud functions deploy sendMorningBriefsHttp ^
    --gen2 ^
    --runtime=nodejs20 ^
    --region=us-central1 ^
    --source=. ^
    --entry-point=sendMorningBriefsHttp ^
    --trigger-http ^
    --allow-unauthenticated ^
    --project=vanish-auth-real ^
    --timeout=540s ^
    --memory=512Mi ^
    --max-instances=10

if %ERRORLEVEL% NEQ 0 (
    echo ❌ Failed to deploy sendMorningBriefsHttp
    goto :restore
)

REM Deploy afternoon briefs HTTP function
echo.
echo Deploying sendAfternoonBriefsHttp...
gcloud functions deploy sendAfternoonBriefsHttp ^
    --gen2 ^
    --runtime=nodejs20 ^
    --region=us-central1 ^
    --source=. ^
    --entry-point=sendAfternoonBriefsHttp ^
    --trigger-http ^
    --allow-unauthenticated ^
    --project=vanish-auth-real ^
    --timeout=540s ^
    --memory=512Mi ^
    --max-instances=10

if %ERRORLEVEL% NEQ 0 (
    echo ❌ Failed to deploy sendAfternoonBriefsHttp
    goto :restore
)

REM Deploy evening briefs HTTP function
echo.
echo Deploying sendEveningBriefsHttp...
gcloud functions deploy sendEveningBriefsHttp ^
    --gen2 ^
    --runtime=nodejs20 ^
    --region=us-central1 ^
    --source=. ^
    --entry-point=sendEveningBriefsHttp ^
    --trigger-http ^
    --allow-unauthenticated ^
    --project=vanish-auth-real ^
    --timeout=540s ^
    --memory=512Mi ^
    --max-instances=10

set DEPLOY_RESULT=%ERRORLEVEL%

:restore
echo.
echo 📝 Restoring package.json...
copy package.json.backup package.json >nul 2>&1
del package.json.backup >nul 2>&1
del index-briefs-http.js >nul 2>&1

if %DEPLOY_RESULT% EQU 0 (
    echo.
    echo ========================================
    echo ✅ HTTP functions deployed!
    echo ========================================
    echo.
    echo 📋 Next steps:
    echo    1. Create Cloud Scheduler jobs to call these HTTP functions:
    echo       - Morning: 9 AM Eastern → sendMorningBriefsHttp
    echo       - Afternoon: 2 PM Eastern → sendAfternoonBriefsHttp
    echo       - Evening: 6 PM Eastern → sendEveningBriefsHttp
    echo    2. Or use manual trigger functions for testing
    echo.
) else (
    echo.
    echo ========================================
    echo ❌ Deployment failed
    echo ========================================
)

pause
