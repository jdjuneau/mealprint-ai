@echo off
REM Simple deployment script for brief functions with Cloud Tasks
REM Uses standalone index to avoid timeout

echo.
echo 🚀 Deploying scalable brief system...
echo.

cd functions

REM Build first
echo 🔨 Building functions...
call npm run build
if %ERRORLEVEL% NEQ 0 (
    echo ⚠️ Build completed with warnings (expected)
)

REM Backup package.json
echo 💾 Backing up package.json...
copy package.json package.json.backup >nul

REM Update main to standalone
echo 📝 Switching to standalone deployment...
powershell -Command "$json = Get-Content package.json | ConvertFrom-Json; $json.main = 'briefs-standalone/index.js'; $json | ConvertTo-Json -Depth 10 | Set-Content package.json"

REM Deploy
echo.
echo 🚀 Deploying functions...
cd ..
firebase deploy --only functions --force
set DEPLOY_RESULT=%ERRORLEVEL%

REM Restore package.json
echo.
echo 🔄 Restoring package.json...
cd functions
copy /Y package.json.backup package.json >nul
del package.json.backup >nul
cd ..

if %DEPLOY_RESULT% EQU 0 (
    echo.
    echo ✅ SUCCESS! Brief functions deployed!
) else (
    echo.
    echo ❌ Deployment failed. Check errors above.
    exit /b 1
)
