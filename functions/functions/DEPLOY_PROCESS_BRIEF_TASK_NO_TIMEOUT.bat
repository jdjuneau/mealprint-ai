@echo off
REM Deploy processBriefTask by temporarily hiding src_hidden directory
REM This prevents Firebase CLI from analyzing unnecessary files

echo.
echo ========================================
echo 🚀 Deploying processBriefTask (No Timeout)
echo ========================================
echo.

REM Get the script directory (functions folder)
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

REM Verify we're in the right place
if not exist "package.json" (
    echo ❌ ERROR: Not in functions directory!
    pause
    exit /b 1
)

echo ✅ Found functions directory: %CD%
echo.

REM Build TypeScript first
echo 🔨 Building TypeScript...
call npm run build
if %ERRORLEVEL% NEQ 0 (
    echo ❌ Build failed!
    pause
    exit /b 1
)

REM Temporarily rename src_hidden to hide it from Firebase CLI
echo 📝 Temporarily hiding src_hidden directory...
if exist "src_hidden" (
    ren src_hidden src_hidden_HIDDEN
    set HIDDEN_SRC=1
) else (
    set HIDDEN_SRC=0
)

REM Also hide src if it exists and has many files
if exist "src" (
    set SRC_EXISTS=1
) else (
    set SRC_EXISTS=0
)

REM Backup package.json
echo 📝 Configuring package.json...
copy package.json package.json.backup >nul 2>&1

REM Set main to briefs/index.js
powershell -NoProfile -Command "$json = Get-Content package.json -Raw | ConvertFrom-Json; $json.main = 'briefs/index.js'; $json | ConvertTo-Json -Depth 10 | Set-Content package.json"

REM Navigate to project root
cd ..

echo.
echo 🚀 Deploying processBriefTask via Firebase CLI...
echo    (This may take 1-2 minutes...)
echo.

firebase deploy --only functions:processBriefTask --force
set DEPLOY_RESULT=%ERRORLEVEL%

REM Restore everything
cd functions
copy package.json.backup package.json >nul 2>&1
del package.json.backup >nul 2>&1

if %HIDDEN_SRC% EQU 1 (
    if exist "src_hidden_HIDDEN" (
        ren src_hidden_HIDDEN src_hidden
        echo ✅ Restored src_hidden directory
    )
)

echo.
if %DEPLOY_RESULT% EQU 0 (
    echo ========================================
    echo ✅ SUCCESS! processBriefTask deployed!
    echo ========================================
    echo.
    echo 📍 Function URL:
    echo    https://us-central1-vanish-auth-real.cloudfunctions.net/processBriefTask
) else (
    echo ========================================
    echo ❌ Deployment failed
    echo ========================================
    echo.
    echo 💡 Alternative: Use Google Cloud Console to deploy
    echo    https://console.cloud.google.com/functions?project=vanish-auth-real
)

pause
