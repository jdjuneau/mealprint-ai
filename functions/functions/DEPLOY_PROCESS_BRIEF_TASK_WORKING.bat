@echo off
REM Working deployment script for processBriefTask
REM Uses gcloud with correct Gen2 runtime (nodejs20)

echo.
echo ========================================
echo 🚀 Deploying processBriefTask via gcloud
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

REM Build TypeScript
echo 🔨 Building TypeScript...
call npm run build
if %ERRORLEVEL% NEQ 0 (
    echo ❌ Build failed!
    pause
    exit /b 1
)

REM Verify required files exist
if not exist "index-processBriefTask.js" (
    echo ❌ Error: index-processBriefTask.js not found!
    pause
    exit /b 1
)

if not exist "lib\briefTaskQueue.js" (
    echo ❌ Error: lib/briefTaskQueue.js not found!
    pause
    exit /b 1
)

REM Configure package.json
echo 📝 Configuring package.json...
copy package.json package.json.backup >nul 2>&1
powershell -NoProfile -Command "$json = Get-Content package.json -Raw | ConvertFrom-Json; $json.main = 'index-processBriefTask.js'; $json | ConvertTo-Json -Depth 10 | Set-Content package.json"

echo.
echo 🚀 Deploying via gcloud CLI (Gen2, nodejs20)...
echo.

REM Check gcloud availability
where gcloud >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ❌ gcloud CLI not found!
    echo.
    echo 💡 Install from: https://cloud.google.com/sdk/docs/install
    copy package.json.backup package.json >nul 2>&1
    del package.json.backup >nul 2>&1
    pause
    exit /b 1
)

REM Deploy with correct Gen2 runtime (nodejs20)
gcloud functions deploy processBriefTask ^
    --gen2 ^
    --runtime=nodejs20 ^
    --region=us-central1 ^
    --source=. ^
    --entry-point=processBriefTask ^
    --trigger-http ^
    --allow-unauthenticated ^
    --project=vanish-auth-real ^
    --timeout=540s ^
    --memory=512Mi ^
    --max-instances=100

set DEPLOY_RESULT=%ERRORLEVEL%

REM Restore package.json
copy package.json.backup package.json >nul 2>&1
del package.json.backup >nul 2>&1

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
    echo 💡 Make sure:
    echo    1. gcloud is authenticated: gcloud auth login
    echo    2. Project is set: gcloud config set project vanish-auth-real
    echo    3. APIs are enabled: gcloud services enable cloudfunctions.googleapis.com
)

pause
