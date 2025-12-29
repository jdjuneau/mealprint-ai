@echo off
echo 🚀 Deploying Google Play Functions Directly via gcloud
echo This bypasses Firebase CLI analysis timeout
echo.

REM Check if gcloud is installed
where gcloud >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ gcloud CLI not found. Please install Google Cloud SDK first.
    echo Download from: https://cloud.google.com/sdk/docs/install
    pause
    exit /b 1
)

echo 📦 Step 1: Deploying processGooglePlayRTDN...
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
echo ✅ processGooglePlayRTDN deployed successfully!
echo.
echo 📦 Step 2: Deploying verifyPurchase...
gcloud functions deploy verifyPurchase ^
    --gen2 ^
    --runtime=nodejs20 ^
    --region=us-central1 ^
    --source=lib ^
    --entry-point=verifyPurchase ^
    --trigger-http ^
    --allow-unauthenticated ^
    --project=vanish-auth-real

if %errorlevel% neq 0 (
    echo ❌ Failed to deploy verifyPurchase
    pause
    exit /b 1
)

echo.
echo ✅ verifyPurchase deployed successfully!
echo.
echo 🎉 Both Google Play functions deployed!
echo.
echo Function URLs:
echo - RTDN: https://us-central1-vanish-auth-real.cloudfunctions.net/processGooglePlayRTDN
echo - Verify Purchase: Callable via Firebase SDK
echo.
pause
