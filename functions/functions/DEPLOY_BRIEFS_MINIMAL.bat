@echo off
REM Minimal deployment - uses a separate index file to avoid timeout
REM This is the most reliable method when Firebase CLI times out

echo.
echo 🚀 Deploying brief functions (minimal deployment)...
echo.

cd functions

REM Backup original index.ts
echo 💾 Backing up original index.ts...
if exist "src\index.ts" copy "src\index.ts" "src\index.ts.backup" >nul

REM Use briefs-only index
echo 📝 Using minimal index for briefs...
copy "src\index-briefs-only.ts" "src\index.ts" >nul

REM Build
echo 🔨 Building...
call npm run build >nul 2>&1

REM Ensure package.json points to compiled output
echo 📝 Configuring package.json...
powershell -Command "$json = Get-Content package.json | ConvertFrom-Json; $json.main = 'lib/index.js'; $json | ConvertTo-Json -Depth 10 | Set-Content package.json"

REM Deploy
echo.
echo 🚀 Deploying via Firebase CLI...
cd ..
firebase deploy --only functions:sendMorningBriefs,functions:sendAfternoonBriefs,functions:sendEveningBriefs,functions:processBriefTask --force
set DEPLOY_RESULT=%ERRORLEVEL%

REM Restore original index.ts
echo.
echo 🔄 Restoring original index.ts...
cd functions
if exist "src\index.ts.backup" (
    copy /Y "src\index.ts.backup" "src\index.ts" >nul
    del "src\index.ts.backup" >nul
)
cd ..

if %DEPLOY_RESULT% EQU 0 (
    echo.
    echo ✅ SUCCESS! Brief functions deployed!
) else (
    echo.
    echo ❌ Deployment still timed out
    echo.
    echo 💡 The current brief system is still working.
    echo    The Cloud Tasks code is ready and will activate once deployed.
    echo.
    echo 📋 Next steps:
    echo    1. Wait a few hours and try again (timeout may be temporary)
    echo    2. Contact Firebase support about the timeout issue
    echo    3. Use the current system (works for now, may timeout with 1000+ users)
    exit /b 1
)
