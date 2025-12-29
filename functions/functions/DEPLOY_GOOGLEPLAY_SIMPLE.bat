@echo off
echo 🚀 DEPLOYING GOOGLE PLAY FUNCTIONS
echo.
echo This will deploy ALL functions. The Google Play functions will be included.
echo.
pause

cd /d "%~dp0\.."

echo 📋 Building...
cd functions
call npm run build
cd ..

echo.
echo 🚀 Deploying functions (this may take a few minutes)...
firebase deploy --only functions

echo.
echo ✅ Deployment complete!
echo.
echo Check if processGooglePlayRTDN and verifyPurchase are now in the list:
firebase functions:list | findstr /i "google verifyPurchase"
echo.
pause
