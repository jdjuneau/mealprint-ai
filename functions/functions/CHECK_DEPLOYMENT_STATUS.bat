@echo off
echo 🔍 CHECKING DEPLOYMENT STATUS
echo.

cd /d "%~dp0\.."

echo Checking if Google Play functions are deployed...
firebase functions:list | findstr /i "processGooglePlayRTDN verifyPurchase"

if %errorlevel% equ 0 (
    echo.
    echo ✅ Functions are deployed!
    echo.
    echo Next steps:
    echo 1. Go to: https://console.cloud.google.com/functions?project=vanish-auth-real
    echo 2. Click on verifyPurchase or processGooglePlayRTDN
    echo 3. Click EDIT ^> Runtime environment variables ^> ADD VARIABLE
    echo 4. Add GOOGLE_PLAY_SERVICE_ACCOUNT with your JSON key
) else (
    echo.
    echo ⏳ Functions not yet deployed. Deployment may still be in progress.
    echo.
    echo To check deployment progress:
    echo - Check the terminal where you ran the deployment
    echo - Look for "Deploy complete!" message
    echo - Or check: https://console.cloud.google.com/functions?project=vanish-auth-real
    echo.
    echo You can also run: firebase functions:list
)

echo.
pause
