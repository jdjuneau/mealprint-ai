@echo off
REM Deploy generateWeeklyBlueprint using standalone file to avoid timeout

echo.
echo ========================================
echo 🚀 DEPLOYING BLUEPRINT (STANDALONE)
echo ========================================
echo.

cd functions

REM Backup current index.js
if exist index.js (
    copy index.js index.js.backup >nul 2>&1
    echo ✅ Backed up index.js
)

REM Create minimal index that only exports blueprint
echo Creating minimal index.js...
(
echo // TEMPORARY MINIMAL INDEX FOR BLUEPRINT DEPLOYMENT
echo const admin = require^('firebase-admin'^);
echo if ^(!admin.apps.length^) {
echo   admin.initializeApp^(^);
echo }
echo // Just re-export the function directly from the module
echo const generateWeeklyShoppingList = require^('./lib/generateWeeklyShoppingList'^);
echo exports.generateWeeklyBlueprint = generateWeeklyShoppingList.generateWeeklyBlueprint;
) > index.js

echo ✅ Created minimal index.js

REM Deploy
echo.
echo Deploying generateWeeklyBlueprint...
cd ..
firebase deploy --only functions:generateWeeklyBlueprint --project=vanish-auth-real --force

set DEPLOY_RESULT=%ERRORLEVEL%

REM Restore original index.js
echo.
echo Restoring original index.js...
cd functions
if exist index.js.backup (
    move /Y index.js.backup index.js >nul 2>&1
    echo ✅ Restored index.js
)

cd ..

echo.
echo ========================================
if %DEPLOY_RESULT% EQU 0 (
    echo ✅ SUCCESS! generateWeeklyBlueprint deployed!
) else (
    echo ❌ DEPLOYMENT FAILED
)
echo ========================================
echo.

pause

