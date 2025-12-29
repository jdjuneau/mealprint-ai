@echo off
REM Deploy brief functions as HTTP endpoints via gcloud CLI
REM These can then be scheduled via Cloud Scheduler
REM This bypasses Firebase CLI timeout issues

echo.
echo ========================================
echo 🚀 DEPLOYING BRIEF FUNCTIONS AS HTTP
echo ========================================
echo.
echo This will deploy HTTP functions that Cloud Scheduler can call.
echo After deployment, create Cloud Scheduler jobs for:
echo   - 9 AM Eastern → sendMorningBriefsHttp
echo   - 2 PM Eastern → sendAfternoonBriefsHttp
echo   - 6 PM Eastern → sendEveningBriefsHttp
echo.

cd /d "%~dp0"

echo.
echo 🔨 Building TypeScript...
call npm run build

if not exist "lib\briefTaskQueue.js" (
    echo ❌ Error: lib/briefTaskQueue.js not found after build!
    pause
    exit /b 1
)

if not exist "index-briefs-http.js" (
    echo ❌ Error: index-briefs-http.js not found!
    pause
    exit /b 1
)

echo.
echo 📝 Backing up package.json...
copy package.json package.json.backup >nul 2>&1

echo.
echo 📝 Temporarily changing package.json main to index-briefs-http.js...
powershell -NoProfile -Command "$json = Get-Content package.json -Raw | ConvertFrom-Json; $json.main = 'index-briefs-http.js'; $json | ConvertTo-Json -Depth 10 | Set-Content package.json"

echo.
echo 🚀 Deploying HTTP functions via gcloud...
echo.

REM Deploy morning briefs HTTP function
echo [1/3] Deploying sendMorningBriefsHttp...
gcloud functions deploy sendMorningBriefsHttp ^
    --gen2 ^
    --runtime=nodejs20 ^
    --region=us-central1 ^
    --source=. ^
    --entry-point=sendMorningBriefsHttp ^
    --trigger-http ^
    --allow-unauthenticated ^
    --project=vanish-auth-real ^
    --timeout=540s ^
    --memory=512Mi ^
    --max-instances=10

if %ERRORLEVEL% NEQ 0 (
    echo ❌ Failed to deploy sendMorningBriefsHttp
    goto :restore
)

REM Deploy afternoon briefs HTTP function
echo.
echo [2/3] Deploying sendAfternoonBriefsHttp...
gcloud functions deploy sendAfternoonBriefsHttp ^
    --gen2 ^
    --runtime=nodejs20 ^
    --region=us-central1 ^
    --source=. ^
    --entry-point=sendAfternoonBriefsHttp ^
    --trigger-http ^
    --allow-unauthenticated ^
    --project=vanish-auth-real ^
    --timeout=540s ^
    --memory=512Mi ^
    --max-instances=10

if %ERRORLEVEL% NEQ 0 (
    echo ❌ Failed to deploy sendAfternoonBriefsHttp
    goto :restore
)

REM Deploy evening briefs HTTP function
echo.
echo [3/3] Deploying sendEveningBriefsHttp...
gcloud functions deploy sendEveningBriefsHttp ^
    --gen2 ^
    --runtime=nodejs20 ^
    --region=us-central1 ^
    --source=. ^
    --entry-point=sendEveningBriefsHttp ^
    --trigger-http ^
    --allow-unauthenticated ^
    --project=vanish-auth-real ^
    --timeout=540s ^
    --memory=512Mi ^
    --max-instances=10

set DEPLOY_RESULT=%ERRORLEVEL%

:restore
echo.
echo 📝 Restoring package.json...
copy package.json.backup package.json >nul 2>&1
del package.json.backup >nul 2>&1

if %DEPLOY_RESULT% EQU 0 (
    echo.
    echo ========================================
    echo ✅ HTTP functions deployed successfully!
    echo ========================================
    echo.
    echo 📋 Next steps - Create Cloud Scheduler jobs:
    echo.
    echo   1. Go to: https://console.cloud.google.com/cloudscheduler
    echo   2. Click "CREATE JOB"
    echo   3. Configure each job:
    echo.
    echo   MORNING BRIEF:
    echo     - Name: send-morning-briefs
    echo     - Schedule: 0 9 * * * (9 AM Eastern)
    echo     - Timezone: America/New_York
    echo     - Target: HTTP
    echo     - URL: https://us-central1-vanish-auth-real.cloudfunctions.net/sendMorningBriefsHttp
    echo     - HTTP method: GET
    echo.
    echo   AFTERNOON BRIEF:
    echo     - Name: send-afternoon-briefs
    echo     - Schedule: 0 14 * * * (2 PM Eastern)
    echo     - Timezone: America/New_York
    echo     - Target: HTTP
    echo     - URL: https://us-central1-vanish-auth-real.cloudfunctions.net/sendAfternoonBriefsHttp
    echo     - HTTP method: GET
    echo.
    echo   EVENING BRIEF:
    echo     - Name: send-evening-briefs
    echo     - Schedule: 0 18 * * * (6 PM Eastern)
    echo     - Timezone: America/New_York
    echo     - Target: HTTP
    echo     - URL: https://us-central1-vanish-auth-real.cloudfunctions.net/sendEveningBriefsHttp
    echo     - HTTP method: GET
    echo.
) else (
    echo.
    echo ========================================
    echo ❌ Deployment failed
    echo ========================================
    echo.
    echo 💡 Check the error messages above
)

pause
