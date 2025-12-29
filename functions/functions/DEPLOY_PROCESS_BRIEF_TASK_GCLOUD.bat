@echo off
echo.
echo ========================================
echo 🚀 DEPLOYING processBriefTask via gcloud
echo ========================================
echo.
echo Using gcloud to avoid Firebase CLI timeout issues
echo.
echo Deploying processBriefTask function...
echo.

cd /d "%~dp0"

gcloud functions deploy processBriefTask ^
    --gen2 ^
    --runtime=nodejs20 ^
    --region=us-central1 ^
    --source=. ^
    --entry-point=processBriefTask ^
    --trigger-http ^
    --allow-unauthenticated ^
    --timeout=540s ^
    --memory=512MB ^
    --project=vanish-auth-real

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo ✅ DEPLOYMENT SUCCESSFUL
    echo ========================================
    echo.
    echo The processBriefTask function is now deployed.
    echo Brief tasks should now process successfully.
    echo.
) else (
    echo.
    echo ========================================
    echo ❌ DEPLOYMENT FAILED
    echo ========================================
    echo.
    echo Check the error above and try again.
    echo.
)

pause
