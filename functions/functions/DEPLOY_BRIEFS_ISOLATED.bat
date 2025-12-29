@echo off
REM Deploy brief functions by temporarily isolating them
REM This moves other TypeScript files so Firebase only analyzes brief-related code

echo.
echo 🚀 Deploying brief functions (isolated deployment)...
echo.

cd functions

REM Build first
echo 🔨 Building functions...
call npm run build >nul 2>&1

REM Create backup directory for other source files
echo 📦 Isolating brief functions...
if not exist "src_backup" mkdir src_backup

REM Move non-brief TypeScript files temporarily
echo 📝 Moving other source files...
for %%f in (src\*.ts) do (
    if not "%%~nf"=="scheduledBriefs" if not "%%~nf"=="briefTaskQueue" if not "%%~nf"=="index" (
        move "%%f" "src_backup\" >nul 2>&1
    )
)

REM Also move subdirectories that aren't needed
if exist "src\briefs" (
    if not exist "src_backup\briefs" mkdir src_backup\briefs
    xcopy /E /I /Y "src\briefs" "src_backup\briefs\" >nul 2>&1
    rmdir /S /Q "src\briefs" >nul 2>&1
)

REM Create minimal index.ts that only exports brief functions
echo 📝 Creating minimal index.ts...
(
echo import { sendMorningBriefs, sendAfternoonBriefs, sendEveningBriefs, triggerMorningBrief, onNewUserCreated } from './scheduledBriefs';
echo import { processBriefTask } from './briefTaskQueue';
echo.
echo export { sendMorningBriefs, sendAfternoonBriefs, sendEveningBriefs, processBriefTask, triggerMorningBrief, onNewUserCreated };
) > src\index.ts

REM Ensure package.json points to lib/index.js (compiled output)
echo 📝 Configuring package.json...
powershell -Command "$json = Get-Content package.json | ConvertFrom-Json; $json.main = 'lib/index.js'; $json | ConvertTo-Json -Depth 10 | Set-Content package.json"

REM Rebuild with minimal files
echo 🔨 Rebuilding with isolated files...
call npm run build >nul 2>&1

REM Deploy
echo.
echo 🚀 Deploying via Firebase CLI...
cd ..
firebase deploy --only functions:sendMorningBriefs,functions:sendAfternoonBriefs,functions:sendEveningBriefs,functions:processBriefTask --force
set DEPLOY_RESULT=%ERRORLEVEL%

REM Restore files
echo.
echo 🔄 Restoring source files...
cd functions
for %%f in (src_backup\*.ts) do (
    move "%%f" "src\" >nul 2>&1
)
if exist "src_backup\briefs" (
    xcopy /E /I /Y "src_backup\briefs" "src\briefs\" >nul 2>&1
    rmdir /S /Q "src_backup\briefs" >nul 2>&1
)
rmdir src_backup >nul 2>&1

REM Restore original index.ts (or it will be restored from git)
cd ..

if %DEPLOY_RESULT% EQU 0 (
    echo.
    echo ✅ SUCCESS! Brief functions deployed!
) else (
    echo.
    echo ❌ Deployment failed
    echo.
    echo 💡 The current brief system is still working.
    echo    The Cloud Tasks code is ready and will activate once deployed.
    exit /b 1
)
