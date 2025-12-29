@echo off
REM Deploy ONLY brief functions using minimal entry point
REM This avoids deployment timeouts by loading only brief-related code

echo.
echo ========================================
echo 🚨 DEPLOYING BRIEF FUNCTIONS ONLY
echo ========================================
echo.
echo This will temporarily change package.json main to deploy only brief functions.
echo.

cd /d "%~dp0"

echo.
echo 🔨 Building TypeScript...
call npm run build

echo.
echo 📝 Backing up package.json...
copy package.json package.json.backup

echo.
echo 📝 Temporarily changing package.json main to index-briefs-only.js...
powershell -Command "(Get-Content package.json) -replace '\"main\": \"index.js\"', '\"main\": \"index-briefs-only.js\"' | Set-Content package.json"

echo.
echo 📦 Deploying brief functions only...
echo    This will deploy:
echo    - sendMorningBriefs (9 AM)
echo    - sendAfternoonBriefs (2 PM)
echo    - sendEveningBriefs (6 PM)
echo    - processBriefTask (worker)
echo    - triggerMorningBrief (manual)
echo    - triggerAfternoonBrief (manual)
echo    - triggerEveningBrief (manual)
echo.

firebase deploy --only functions --force

set DEPLOY_RESULT=%ERRORLEVEL%

echo.
echo 📝 Restoring package.json...
copy package.json.backup package.json
del package.json.backup

if %DEPLOY_RESULT% EQU 0 (
    echo.
    echo ========================================
    echo ✅ Brief functions deployed!
    echo ========================================
    echo.
    echo 📋 Next steps:
    echo    1. Functions will run automatically at scheduled times
    echo    2. Afternoon briefs will work at 2 PM Eastern Time
    echo    3. Check logs if issues persist
    echo.
) else (
    echo.
    echo ========================================
    echo ❌ Deployment failed
    echo ========================================
    echo.
    echo 💡 Check the error messages above
)

pause
