@echo off
REM Final solution: Hide src_hidden and use minimal entry point
REM This prevents Firebase CLI from analyzing unnecessary TypeScript files

echo.
echo ========================================
echo 🚀 Deploying processBriefTask (Final Solution)
echo ========================================
echo.

set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

if not exist "package.json" (
    echo ❌ ERROR: Not in functions directory!
    pause
    exit /b 1
)

echo ✅ Found functions directory: %CD%
echo.

REM Step 1: Build TypeScript
echo 🔨 Step 1: Building TypeScript...
call npm run build
if %ERRORLEVEL% NEQ 0 (
    echo ❌ Build failed!
    pause
    exit /b 1
)

REM Step 2: Temporarily hide src_hidden directory
echo 📝 Step 2: Hiding src_hidden directory from Firebase CLI...
if exist "src_hidden" (
    ren src_hidden src_hidden_TEMP_HIDDEN 2>nul
    if %ERRORLEVEL% EQU 0 (
        set HIDDEN_SRC=1
        echo ✅ src_hidden hidden
    ) else (
        set HIDDEN_SRC=0
        echo ⚠️  Could not hide src_hidden (may be in use)
    )
) else (
    set HIDDEN_SRC=0
    echo ℹ️  src_hidden doesn't exist
)

REM Step 3: Configure package.json
echo 📝 Step 3: Configuring package.json...
copy package.json package.json.backup >nul 2>&1
powershell -NoProfile -Command "$json = Get-Content package.json -Raw | ConvertFrom-Json; $json.main = 'briefs/index.js'; $json | ConvertTo-Json -Depth 10 | Set-Content package.json"

REM Step 4: Deploy
cd ..
echo.
echo 🚀 Step 4: Deploying processBriefTask...
echo    (This bypasses timeout by hiding src_hidden)
echo.

firebase deploy --only functions:processBriefTask --force
set DEPLOY_RESULT=%ERRORLEVEL%

REM Step 5: Restore everything
cd functions
copy package.json.backup package.json >nul 2>&1
del package.json.backup >nul 2>&1

if %HIDDEN_SRC% EQU 1 (
    if exist "src_hidden_TEMP_HIDDEN" (
        ren src_hidden_TEMP_HIDDEN src_hidden 2>nul
        if %ERRORLEVEL% EQU 0 (
            echo ✅ Restored src_hidden directory
        )
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
    echo.
    echo 💡 The function is now live and ready to process brief tasks!
) else (
    echo ========================================
    echo ❌ Deployment still failed
    echo ========================================
    echo.
    echo 💡 Try these alternatives:
    echo.
    echo    Option 1: Use gcloud CLI (bypasses Firebase CLI):
    echo       cd functions
    echo       .\DEPLOY_VIA_GCLOUD.bat
    echo.
    echo    Option 2: Deploy via Google Cloud Console:
    echo       1. Go to: https://console.cloud.google.com/functions?project=vanish-auth-real
    echo       2. Find processBriefTask
    echo       3. Click Edit and redeploy
    echo.
    echo    Option 3: Deploy all brief functions together:
    echo       firebase deploy --only functions:sendMorningBriefs,functions:processBriefTask
)

pause
