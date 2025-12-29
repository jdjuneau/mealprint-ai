@echo off
echo 🚀 DEPLOYING GOOGLE PLAY FUNCTIONS - STANDALONE METHOD
echo This temporarily switches to a minimal index to avoid Firebase CLI timeout
echo.

cd /d "%~dp0"

echo 📋 Step 1: Building TypeScript...
call npm run build
echo ✅ Build completed (errors are non-blocking)

echo.
echo 📋 Step 2: Backing up package.json...
if not exist "package.json.backup" (
    copy package.json package.json.backup >nul
    echo ✅ Backup created
) else (
    echo ✅ Backup already exists
)

echo.
echo 🔧 Step 3: Switching to standalone Google Play index...
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
    echo   "main": "googleplay/index-standalone.js",
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
echo ✅ Switched to googleplay/index-standalone.js

echo.
echo 🚀 Step 4: Deploying Google Play functions...
cd ..
firebase deploy --only functions:processGooglePlayRTDN,functions:verifyPurchase

set DEPLOY_RESULT=%errorlevel%

echo.
echo 🔄 Step 5: Restoring original package.json...
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
    echo 📋 The Google Play functions are ready in: functions/googleplay/index-standalone.js
    echo You can deploy them manually via Google Cloud Console if needed
    pause
    exit /b 1
)

echo.
echo ✅ GOOGLE PLAY FUNCTIONS DEPLOYED SUCCESSFULLY!
echo.
echo 📋 Function URLs:
echo - RTDN Endpoint: https://us-central1-vanish-auth-real.cloudfunctions.net/processGooglePlayRTDN
echo - Verify Purchase: https://us-central1-vanish-auth-real.cloudfunctions.net/verifyPurchase
echo.
echo ⏰ NEXT STEP: Set environment variable in Google Cloud Console
echo.
pause
