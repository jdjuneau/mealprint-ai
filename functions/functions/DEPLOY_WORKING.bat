@echo off
REM Simple working deployment - uses existing briefs/index.js
REM This should work since briefs/index.js already deployed successfully before

echo.
echo 🚀 Deploying brief functions (using working briefs/index.js)...
echo.

cd functions

REM Ensure package.json points to briefs/index.js
echo 📝 Ensuring package.json is configured...
powershell -Command "$json = Get-Content package.json | ConvertFrom-Json; $json.main = 'briefs/index.js'; $json | ConvertTo-Json -Depth 10 | Set-Content package.json"

REM Build
echo 🔨 Building...
call npm run build >nul 2>&1

REM Deploy using Firebase CLI with explicit function names
echo.
echo 🚀 Deploying via Firebase CLI...
cd ..
firebase deploy --only functions:sendMorningBriefs,functions:sendAfternoonBriefs,functions:sendEveningBriefs,functions:processBriefTask --force

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ✅ SUCCESS! Functions deployed!
) else (
    echo.
    echo ❌ Deployment failed
    echo.
    echo 💡 Alternative: The functions are already working with the old system.
    echo    The Cloud Tasks code is ready and will work once deployed.
    echo    You can also deploy via Firebase Console manually.
    exit /b 1
)
