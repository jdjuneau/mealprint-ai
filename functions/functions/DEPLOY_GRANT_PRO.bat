@echo off
REM Deploy grantProByEmail function with CORS support

echo.
echo 🚀 Deploying grantProByEmail function...
echo.

cd functions
cd ..

REM Deploy only the grantProByEmail function
firebase deploy --only functions:grantProByEmail

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ✅ SUCCESS! grantProByEmail deployed!
    echo.
    echo 📊 Function URL:
    echo    https://us-central1-vanish-auth-real.cloudfunctions.net/grantProByEmail
    echo.
    echo ✅ CORS headers are configured - dashboard can now call this function!
) else (
    echo.
    echo ❌ Deployment failed
    exit /b 1
)
