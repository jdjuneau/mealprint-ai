@echo off
REM Deploy the remaining two brief HTTP functions (afternoon and evening)
REM Morning brief is already deployed

echo.
echo ========================================
echo 🚀 DEPLOYING REMAINING BRIEF FUNCTIONS
echo ========================================
echo.
echo Deploying afternoon and evening brief HTTP functions...
echo.

cd /d "%~dp0"

if not exist "index-briefs-http.js" (
    echo ❌ Error: index-briefs-http.js not found!
    pause
    exit /b 1
)

echo 📝 Backing up package.json...
copy package.json package.json.backup >nul 2>&1

echo.
echo 📝 Temporarily changing package.json main to index-briefs-http.js...
powershell -NoProfile -Command "$json = Get-Content package.json -Raw | ConvertFrom-Json; $json.main = 'index-briefs-http.js'; $json | ConvertTo-Json -Depth 10 | Set-Content package.json"

echo.
echo 🚀 Deploying remaining HTTP functions via gcloud...
echo.

REM Deploy afternoon briefs HTTP function
echo [1/2] Deploying sendAfternoonBriefsHttp...
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
echo [2/2] Deploying sendEveningBriefsHttp...
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
    echo ✅ All brief HTTP functions deployed!
    echo ========================================
    echo.
    echo 📋 Deployed functions:
    echo    ✅ sendMorningBriefsHttp (already deployed)
    echo    ✅ sendAfternoonBriefsHttp (just deployed)
    echo    ✅ sendEveningBriefsHttp (just deployed)
    echo.
    echo 📋 Next step: Create Cloud Scheduler jobs
    echo    Run: .\CREATE_CLOUD_SCHEDULER_JOBS.bat
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
