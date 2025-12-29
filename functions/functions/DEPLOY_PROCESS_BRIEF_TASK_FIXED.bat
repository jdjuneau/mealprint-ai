@echo off
echo.
echo ========================================
echo 🚀 DEPLOYING processBriefTask (FIXED)
echo ========================================
echo.
echo Using standalone HTTP file approach
echo.

cd /d "%~dp0"

REM Backup package.json
copy package.json package.json.backup >nul 2>&1

REM Create minimal index file that only exports processBriefTask from standalone file
(
echo const functions = require('firebase-functions'^);
echo const admin = require('firebase-admin'^);
echo if (!admin.apps.length^) { admin.initializeApp(^); }
echo const processBriefTaskHttp = require('./process-brief-task-http'^);
echo exports.processBriefTask = processBriefTaskHttp.processBriefTask;
) > index-process-brief-only.js

REM Temporarily change main in package.json using PowerShell properly
powershell -NoProfile -Command "$json = Get-Content package.json | ConvertFrom-Json; $json.main = 'index-process-brief-only.js'; $json | ConvertTo-Json -Depth 10 | Set-Content package.json"

echo.
echo Deploying processBriefTask...
echo.
firebase deploy --only functions:processBriefTask --force

set DEPLOY_RESULT=%ERRORLEVEL%

REM Restore package.json
copy package.json.backup package.json >nul 2>&1
del package.json.backup >nul 2>&1
del index-process-brief-only.js >nul 2>&1

echo.
if %DEPLOY_RESULT% EQU 0 (
    echo ========================================
    echo ✅ DEPLOYMENT SUCCESSFUL
    echo ========================================
    echo.
    echo The processBriefTask function is now deployed.
    echo Brief tasks should now process successfully.
    echo.
    echo Test by manually triggering evening brief:
    echo   functions\TRIGGER_EVENING_BRIEF_MANUAL.bat
) else (
    echo ========================================
    echo ❌ DEPLOYMENT FAILED
    echo ========================================
    echo.
    echo Check the error above.
    echo You may need to deploy via gcloud instead.
)

pause
