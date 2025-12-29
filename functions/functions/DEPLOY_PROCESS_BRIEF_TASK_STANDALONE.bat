@echo off
echo.
echo ========================================
echo 🚀 DEPLOYING processBriefTask STANDALONE
echo ========================================
echo.
echo This will temporarily modify package.json to deploy only processBriefTask
echo.

cd /d "%~dp0"

REM Backup package.json
copy package.json package.json.backup >nul 2>&1

REM Create minimal index file that only exports processBriefTask
echo const functions = require('firebase-functions'); > index-process-brief-only.js
echo const admin = require('firebase-admin'); >> index-process-brief-only.js
echo if (!admin.apps.length) { admin.initializeApp(); } >> index-process-brief-only.js
echo const briefTaskQueue = require('./lib/briefTaskQueue'); >> index-process-brief-only.js
echo exports.processBriefTask = briefTaskQueue.processBriefTask; >> index-process-brief-only.js

REM Temporarily change main in package.json
powershell -Command "$content = Get-Content package.json -Raw; $content = $content -replace '\"main\": \"index.js\"', '\"main\": \"index-process-brief-only.js\"'; Set-Content package.json -Value $content -NoNewline"

echo Deploying processBriefTask...
firebase deploy --only functions:processBriefTask --force

set DEPLOY_RESULT=%ERRORLEVEL%

REM Restore package.json
copy package.json.backup package.json >nul 2>&1
del package.json.backup >nul 2>&1
del index-process-brief-only.js >nul 2>&1

if %DEPLOY_RESULT% EQU 0 (
    echo.
    echo ========================================
    echo ✅ DEPLOYMENT SUCCESSFUL
    echo ========================================
) else (
    echo.
    echo ========================================
    echo ❌ DEPLOYMENT FAILED
    echo ========================================
)

pause
