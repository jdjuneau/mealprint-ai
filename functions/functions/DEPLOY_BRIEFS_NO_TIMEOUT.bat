@echo off
echo 🚀 DEPLOYING BRIEFS - NO TIMEOUT METHOD
echo This temporarily switches to a minimal index to avoid Firebase CLI timeout
echo.

cd /d "%~dp0"

echo 📋 Step 1: Backing up package.json...
if not exist "package.json.backup" (
    copy package.json package.json.backup >nul
    echo ✅ Backup created
) else (
    echo ✅ Backup already exists
)

echo.
echo 🔧 Step 2: Switching to standalone briefs index...
(
    echo {
    echo   "name": "coachie-functions",
    echo   "version": "1.0.0",
    echo   "description": "Firebase Cloud Functions for Coachie",
    echo   "scripts": {
    echo     "build": "tsc || true",
    echo     "serve": "npm run build && firebase emulators:start --only functions",
    echo     "shell": "npm run build && firebase functions:shell",
    echo     "start": "npm run shell",
    echo     "deploy": "firebase deploy --only functions",
    echo     "logs": "firebase functions:log"
    echo   },
    echo   "engines": {
    echo     "node": "18"
    echo   },
    echo   "main": "briefs/index.js",
    echo   "dependencies": {
    echo     "axios": "^1.6.2",
    echo     "firebase-admin": "^12.0.0",
    echo     "firebase-functions": "^4.5.0",
    echo     "googleapis": "^144.0.0",
    echo     "openai": "^6.14.0",
    echo     "stripe": "^14.21.0"
    echo   },
    echo   "devDependencies": {
    echo     "@types/node": "^20.10.0",
    echo     "typescript": "^5.3.3"
    echo   },
    echo   "private": true
    echo }
) > package.json.temp
move /Y package.json.temp package.json >nul
echo ✅ Switched to briefs/index.js

echo.
echo 🚀 Step 3: Deploying brief functions...
cd ..
firebase deploy --only functions:sendMorningBriefs,functions:sendAfternoonBriefs,functions:sendEveningBriefs

set DEPLOY_RESULT=%errorlevel%

echo.
echo 🔄 Step 4: Restoring original package.json...
cd functions
if exist "package.json.backup" (
    copy /Y package.json.backup package.json >nul
    del package.json.backup >nul
    echo ✅ Restored original package.json
)

if %DEPLOY_RESULT% neq 0 (
    echo.
    echo ❌ Deployment failed
    echo.
    echo 📋 The brief functions are ready in: functions/briefs/index.js
    echo You can deploy them manually via Google Cloud Console if needed
    pause
    exit /b 1
)

echo.
echo ✅ BRIEF FUNCTIONS DEPLOYED SUCCESSFULLY!
echo.
echo 📋 Function URLs:
echo - Morning: https://us-central1-vanish-auth-real.cloudfunctions.net/sendMorningBriefs
echo - Afternoon: https://us-central1-vanish-auth-real.cloudfunctions.net/sendAfternoonBriefs
echo - Evening: https://us-central1-vanish-auth-real.cloudfunctions.net/sendEveningBriefs
echo.
echo ⏰ NEXT STEP: Set up Cloud Scheduler (see CLOUD_SCHEDULER_SETUP.md)
echo.
pause
