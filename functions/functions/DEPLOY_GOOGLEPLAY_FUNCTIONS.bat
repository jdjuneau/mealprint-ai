@echo off
echo 🚀 DEPLOYING GOOGLE PLAY FUNCTIONS
echo.

cd /d "%~dp0"

echo 📋 Step 1: Building TypeScript...
call npm run build
if %errorlevel% neq 0 (
    echo ❌ Build failed
    pause
    exit /b 1
)

echo.
echo 🚀 Step 2: Deploying Google Play functions...
firebase deploy --only functions:processGooglePlayRTDN,functions:verifyPurchase

if %errorlevel% neq 0 (
    echo.
    echo ❌ Deployment failed
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
echo ⏰ NEXT STEP: Configure RTDN in Google Play Console (see GOOGLE_PLAY_SUBSCRIPTION_SETUP_CHECKLIST.md)
echo.
pause
