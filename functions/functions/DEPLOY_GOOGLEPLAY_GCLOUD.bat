@echo off
echo 🚀 DEPLOYING GOOGLE PLAY FUNCTIONS VIA GCLOUD
echo.
echo This uses gcloud CLI to deploy directly, bypassing Firebase CLI detection issues
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
echo 🚀 Step 2: Deploying processGooglePlayRTDN (HTTP function)...
gcloud functions deploy processGooglePlayRTDN ^
    --gen2 ^
    --runtime=nodejs20 ^
    --region=us-central1 ^
    --source=lib ^
    --entry-point=processGooglePlayRTDN ^
    --trigger-http ^
    --allow-unauthenticated ^
    --project=vanish-auth-real

if %errorlevel% neq 0 (
    echo ❌ Failed to deploy processGooglePlayRTDN
    pause
    exit /b 1
)

echo.
echo 🚀 Step 3: Deploying verifyPurchase (Callable function)...
echo Note: Callable functions need to be deployed via Firebase CLI
echo Let's try Firebase deploy with explicit function names...

cd ..
firebase deploy --only functions:verifyPurchase

if %errorlevel% neq 0 (
    echo.
    echo ⚠️ verifyPurchase deployment may have failed
    echo You may need to deploy it manually via Firebase Console
)

echo.
echo ✅ Deployment attempt complete!
echo.
echo Check Google Cloud Console: https://console.cloud.google.com/functions?project=vanish-auth-real
echo.
pause
