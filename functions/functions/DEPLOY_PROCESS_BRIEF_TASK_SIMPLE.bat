@echo off
REM Simple deployment script for processBriefTask
REM Uses the existing briefs/index.js standalone file

echo.
echo 🚀 Deploying processBriefTask (simple method)...
echo.

REM Navigate to functions directory
cd /d "%~dp0"
if not exist "package.json" (
    echo ❌ Error: Must run from functions directory!
    exit /b 1
)

REM Check if we're in the right place
if not exist "briefs\index.js" (
    echo ❌ Error: briefs/index.js not found!
    echo    Make sure you're in the functions directory.
    exit /b 1
)

REM Build first
echo 🔨 Building TypeScript...
call npm run build
if %ERRORLEVEL% NEQ 0 (
    echo ❌ Build failed!
    exit /b 1
)

REM Temporarily change package.json main to briefs/index.js
echo 📝 Temporarily configuring package.json...
powershell -Command "$json = Get-Content package.json -Raw | ConvertFrom-Json; $originalMain = $json.main; $json.main = 'briefs/index.js'; $json | ConvertTo-Json -Depth 10 | Set-Content package.json; $originalMain | Out-File -FilePath 'package.json.main.backup' -NoNewline"

REM Deploy
echo.
echo 🚀 Deploying via Firebase CLI...
cd ..
firebase deploy --only functions:processBriefTask --force
set DEPLOY_RESULT=%ERRORLEVEL%

REM Restore package.json
cd functions
powershell -Command "$json = Get-Content package.json -Raw | ConvertFrom-Json; $originalMain = Get-Content 'package.json.main.backup' -Raw; $json.main = $originalMain.Trim(); $json | ConvertTo-Json -Depth 10 | Set-Content package.json; Remove-Item 'package.json.main.backup' -ErrorAction SilentlyContinue"

if %DEPLOY_RESULT% EQU 0 (
    echo.
    echo ✅ SUCCESS! processBriefTask deployed!
) else (
    echo.
    echo ❌ Deployment failed
    exit /b 1
)
