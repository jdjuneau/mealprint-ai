@echo off
REM Deploy processBriefTask directly via gcloud CLI
REM This bypasses Firebase CLI timeout issues

echo.
echo ========================================
echo 🚀 Deploying processBriefTask via gcloud
echo ========================================
echo.

REM Get the script directory
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

REM Verify briefs/index.js exists
if not exist "briefs\index.js" (
    echo ❌ Error: briefs/index.js not found after build!
    pause
    exit /b 1
)

REM Set package.json main to briefs/index.js temporarily
copy package.json package.json.backup >nul 2>&1
powershell -NoProfile -Command "$json = Get-Content package.json -Raw | ConvertFrom-Json; $json.main = 'briefs/index.js'; $json | ConvertTo-Json -Depth 10 | Set-Content package.json"

echo.
echo 🚀 Deploying via gcloud CLI...
echo    This bypasses Firebase CLI timeout issues
echo.

REM Deploy using gcloud functions deploy
gcloud functions deploy processBriefTask ^
    --gen2 ^
    --runtime=nodejs18 ^
    --region=us-central1 ^
    --source=. ^
    --entry-point=processBriefTask ^
    --trigger-http ^
    --allow-unauthenticated ^
    --project=vanish-auth-real ^
    --timeout=540s ^
    --memory=512MB

set DEPLOY_RESULT=%ERRORLEVEL%

REM Restore package.json
copy package.json.backup package.json >nul 2>&1
del package.json.backup >nul 2>&1

echo.
if %DEPLOY_RESULT% EQU 0 (
    echo ========================================
    echo ✅ SUCCESS! processBriefTask deployed!
    echo ========================================
) else (
    echo ========================================
    echo ❌ Deployment failed
    echo ========================================
    echo.
    echo 💡 Make sure gcloud CLI is installed and authenticated:
    echo    gcloud auth login
    echo    gcloud config set project vanish-auth-real
)

pause
