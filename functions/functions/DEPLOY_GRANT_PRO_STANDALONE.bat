@echo off
REM Deploy grantProByEmail as standalone function to avoid timeout

echo.
echo 🚀 Deploying grantProByEmail (standalone)...
echo.

REM Get the directory where this script is located
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

REM Backup package.json
copy /Y package.json package.json.backup_grant >nul

REM Temporarily change main entry point to grant-pro/index.js
echo 📝 Configuring package.json for standalone deployment...
powershell -Command "$json = Get-Content '%SCRIPT_DIR%package.json' | ConvertFrom-Json; $json.main = 'grant-pro/index.js'; $json | ConvertTo-Json -Depth 10 | Set-Content '%SCRIPT_DIR%package.json'"

REM Deploy
echo.
echo 🚀 Deploying via Firebase CLI...
cd ..
firebase deploy --only functions:grantProByEmail --force
set DEPLOY_RESULT=%ERRORLEVEL%

REM Restore package.json
echo.
echo 🔄 Restoring package.json...
cd /d "%SCRIPT_DIR%"
copy /Y package.json.backup_grant package.json >nul
del package.json.backup_grant >nul

cd ..

if %DEPLOY_RESULT% EQU 0 (
    echo.
    echo ✅ SUCCESS! grantProByEmail deployed!
    echo.
    echo 📊 Function URL:
    echo    https://us-central1-vanish-auth-real.cloudfunctions.net/grantProByEmail
    echo.
    echo ✅ CORS headers configured - dashboard can now call this function!
) else (
    echo.
    echo ❌ Deployment failed
    exit /b 1
)
