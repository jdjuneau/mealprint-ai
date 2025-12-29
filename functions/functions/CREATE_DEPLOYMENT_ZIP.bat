@echo off
REM Create a ZIP file with all files needed to deploy processBriefTask
REM This ZIP can be uploaded directly to Google Cloud Console

echo.
echo ========================================
echo Creating Deployment ZIP for processBriefTask
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

REM Check if required files exist
if not exist "processBriefTask-standalone.js" (
    echo ❌ Error: processBriefTask-standalone.js not found!
    pause
    exit /b 1
)

if not exist "lib\briefTaskQueue.js" (
    echo ❌ Error: lib/briefTaskQueue.js not found!
    echo    Run: npm run build
    pause
    exit /b 1
)

REM Create temp directory for ZIP contents
echo 📦 Creating deployment package...
if exist "deploy_temp" rmdir /s /q deploy_temp
mkdir deploy_temp

REM Copy required files
echo 📝 Copying files...
copy processBriefTask-standalone.js deploy_temp\ >nul
copy package.json deploy_temp\ >nul

REM Copy lib directory
echo 📝 Copying lib directory...
xcopy /E /I /Y lib deploy_temp\lib\ >nul

REM Create ZIP file (requires PowerShell)
echo 📦 Creating ZIP file...
powershell -NoProfile -Command "Compress-Archive -Path deploy_temp\* -DestinationPath processBriefTask-deploy.zip -Force"

REM Cleanup
rmdir /s /q deploy_temp

if exist "processBriefTask-deploy.zip" (
    echo.
    echo ========================================
    echo ✅ ZIP file created successfully!
    echo ========================================
    echo.
    echo 📁 File: processBriefTask-deploy.zip
    echo 📍 Location: %CD%\processBriefTask-deploy.zip
    echo.
    echo 💡 Next steps:
    echo    1. Go to Google Cloud Console
    echo    2. Create new Cloud Function
    echo    3. Select "Upload ZIP"
    echo    4. Upload this ZIP file
    echo.
) else (
    echo.
    echo ❌ Failed to create ZIP file
    echo    Make sure PowerShell is available
)

pause
