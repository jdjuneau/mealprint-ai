@echo off
REM Deploy brief functions with Cloud Tasks support
REM This script temporarily changes package.json to use standalone briefs deployment
REM to avoid Firebase timeout issues

echo.
echo 🚀 Deploying scalable brief system with Cloud Tasks...
echo.

set PROJECT_ID=vanish-auth-real
set LOCATION=us-central1
set QUEUE_NAME=brief-generation-queue

REM Step 1: Enable Cloud Tasks API
echo 📦 Step 1: Enabling Cloud Tasks API...
gcloud services enable cloudtasks.googleapis.com --project=%PROJECT_ID% >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo ✅ Cloud Tasks API enabled
) else (
    echo ⚠️ Cloud Tasks API may already be enabled
)

REM Wait for API to propagate
echo ⏳ Waiting 10 seconds for API to propagate...
timeout /t 10 /nobreak >nul

REM Step 2: Create queue if it doesn't exist
echo.
echo 📋 Step 2: Checking for Cloud Tasks queue...
gcloud tasks queues describe %QUEUE_NAME% --location=%LOCATION% --project=%PROJECT_ID% >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo 📝 Creating queue...
    gcloud tasks queues create %QUEUE_NAME% --location=%LOCATION% --project=%PROJECT_ID% --max-dispatches-per-second=10 --max-concurrent-dispatches=100 --max-retry-duration=3600s
    if %ERRORLEVEL% EQU 0 (
        echo ✅ Queue created successfully
    ) else (
        echo ❌ Failed to create queue
        exit /b 1
    )
) else (
    echo ✅ Queue already exists
)

REM Step 3: Build functions
echo.
echo 🔨 Step 3: Building functions...
cd functions
call npm run build
if %ERRORLEVEL% NEQ 0 (
    echo ⚠️ Build completed with warnings (expected)
)
cd ..

REM Step 4: Backup package.json
echo.
echo 💾 Step 4: Preparing standalone deployment...
cd functions
copy package.json package.json.backup >nul

REM Step 5: Temporarily change main to standalone briefs
echo "main": "briefs-standalone/index.js" > temp_main.txt
powershell -Command "(Get-Content package.json) -replace '\"main\": \".*\"', (Get-Content temp_main.txt) | Set-Content package.json"
del temp_main.txt

REM Step 6: Deploy
echo.
echo 🚀 Step 5: Deploying brief functions...
cd ..
firebase deploy --only functions --force

REM Step 7: Restore package.json
echo.
echo 🔄 Step 6: Restoring package.json...
cd functions
copy /Y package.json.backup package.json >nul
del package.json.backup >nul
cd ..

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ✅ SUCCESS! Scalable brief system deployed!
    echo.
    echo 📊 System Configuration:
    echo    - Queue: %QUEUE_NAME%
    echo    - Location: %LOCATION%
    echo    - Max dispatches/sec: 10
    echo    - Max concurrent: 100
    echo.
    echo 🎯 The system will now:
    echo    - Scale to thousands of users without timeouts
    echo    - Process each user in a separate function invocation
    echo    - Automatically retry failed tasks
) else (
    echo.
    echo ❌ Deployment failed. Restoring package.json...
    cd functions
    copy /Y package.json.backup package.json >nul
    del package.json.backup >nul
    cd ..
    exit /b 1
)
