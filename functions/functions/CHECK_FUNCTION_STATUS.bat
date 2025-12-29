@echo off
REM Check if processBriefTask function already exists

echo.
echo ========================================
echo Checking processBriefTask Function Status
echo ========================================
echo.

cd /d "%~dp0\.."

echo Checking Firebase Functions...
firebase functions:list | findstr /i "processBriefTask"

echo.
echo Checking via gcloud...
gcloud functions describe processBriefTask --region=us-central1 --gen2 --project=vanish-auth-real 2>nul
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ✅ Function exists!
    echo.
    echo To update it, use Google Cloud Console:
    echo https://console.cloud.google.com/functions?project=vanish-auth-real
) else (
    echo.
    echo ℹ️  Function not found (or gcloud not configured)
    echo    This is normal if it hasn't been deployed yet
)

pause
