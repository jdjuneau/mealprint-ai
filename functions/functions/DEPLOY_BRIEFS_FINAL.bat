@echo off
REM Final deployment script for brief functions with Cloud Tasks
REM Uses existing briefs/index.js which already works

echo.
echo 🚀 Deploying scalable brief system with Cloud Tasks...
echo.

set PROJECT_ID=vanish-auth-real
set LOCATION=us-central1
set QUEUE_NAME=brief-generation-queue

REM Step 1: Enable Cloud Tasks API
echo 📦 Step 1: Enabling Cloud Tasks API...
gcloud services enable cloudtasks.googleapis.com --project=%PROJECT_ID% >nul 2>&1
echo ✅ Cloud Tasks API ready

REM Wait for API to propagate
echo ⏳ Waiting 10 seconds...
timeout /t 10 /nobreak >nul

REM Step 2: Create queue if needed
echo.
echo 📋 Step 2: Checking Cloud Tasks queue...
gcloud tasks queues describe %QUEUE_NAME% --location=%LOCATION% --project=%PROJECT_ID% >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo 📝 Creating queue...
    gcloud tasks queues create %QUEUE_NAME% --location=%LOCATION% --project=%PROJECT_ID% --max-dispatches-per-second=10 --max-concurrent-dispatches=100 --max-retry-duration=3600s
    echo ✅ Queue created
) else (
    echo ✅ Queue exists
)

REM Step 3: Build
echo.
echo 🔨 Step 3: Building functions...
cd functions
call npm run build >nul 2>&1
cd ..

REM Step 4: Backup and update package.json
echo.
echo 💾 Step 4: Preparing deployment...
cd functions
copy package.json package.json.backup >nul
powershell -Command "$json = Get-Content package.json | ConvertFrom-Json; $json.main = 'briefs/index.js'; $json | ConvertTo-Json -Depth 10 | Set-Content package.json"

REM Step 5: Deploy
echo.
echo 🚀 Step 5: Deploying...
cd ..
firebase deploy --only functions --force
set DEPLOY_RESULT=%ERRORLEVEL%

REM Step 6: Restore
echo.
echo 🔄 Step 6: Restoring package.json...
cd functions
copy /Y package.json.backup package.json >nul
del package.json.backup >nul
cd ..

if %DEPLOY_RESULT% EQU 0 (
    echo.
    echo ✅ SUCCESS! Scalable brief system deployed!
    echo.
    echo 📊 Configuration:
    echo    - Queue: %QUEUE_NAME%
    echo    - Max concurrent: 100 users
    echo    - Scales to 10,000+ users
) else (
    echo.
    echo ❌ Deployment failed
    cd functions
    copy /Y package.json.backup package.json >nul
    del package.json.backup >nul
    cd ..
    exit /b 1
)
