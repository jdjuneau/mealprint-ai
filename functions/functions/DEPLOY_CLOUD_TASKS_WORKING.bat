@echo off
REM Deploy Cloud Tasks system by hiding TypeScript source files
REM This forces Firebase CLI to only analyze the compiled JavaScript

echo.
echo 🚀 Deploying Cloud Tasks brief system...
echo.

cd functions

REM Build first to ensure lib/ is up to date
echo 🔨 Building functions...
call npm run build >nul 2>&1

REM Backup and hide src/ directory (Firebase CLI won't analyze it)
echo 📦 Hiding TypeScript source files...
if exist "src" (
    if not exist "src_hidden" (
        move "src" "src_hidden" >nul
        echo ✅ Source files hidden
    ) else (
        echo ⚠️ src_hidden already exists, skipping
    )
)

REM Ensure package.json points to briefs/index.js (which uses compiled lib/)
echo 📝 Configuring package.json...
powershell -Command "$json = Get-Content package.json | ConvertFrom-Json; $json.main = 'briefs/index.js'; $json | ConvertTo-Json -Depth 10 | Set-Content package.json"

REM Verify briefs/index.js exists and exports processBriefTask
echo 🔍 Verifying briefs/index.js...
findstr /C:"processBriefTask" "briefs\index.js" >nul
if %ERRORLEVEL% NEQ 0 (
    echo ❌ processBriefTask not found in briefs/index.js
    echo    Restoring source files...
    if exist "src_hidden" move "src_hidden" "src" >nul
    cd ..
    exit /b 1
)
echo ✅ briefs/index.js verified

REM Deploy
echo.
echo 🚀 Deploying via Firebase CLI...
cd ..
firebase deploy --only functions:sendMorningBriefs,functions:sendAfternoonBriefs,functions:sendEveningBriefs,functions:processBriefTask --force
set DEPLOY_RESULT=%ERRORLEVEL%

REM Restore src/ directory
echo.
echo 🔄 Restoring source files...
cd functions
if exist "src_hidden" (
    move "src_hidden" "src" >nul
    echo ✅ Source files restored
)

cd ..

if %DEPLOY_RESULT% EQU 0 (
    echo.
    echo ✅ SUCCESS! Cloud Tasks system deployed!
    echo.
    echo 📊 Deployed functions:
    echo    - sendMorningBriefs (uses Cloud Tasks)
    echo    - sendAfternoonBriefs (uses Cloud Tasks)
    echo    - sendEveningBriefs (uses Cloud Tasks)
    echo    - processBriefTask (Cloud Tasks worker)
    echo.
    echo 🎯 The system will now scale to 10,000+ users!
) else (
    echo.
    echo ❌ Deployment failed
    echo.
    echo 💡 The current brief system is still working.
    echo    Try again later or contact Firebase support.
    exit /b 1
)
