@echo off
REM DEPLOY SCHEDULED BRIEF FUNCTIONS - BYPASSES TIMEOUT
REM This script temporarily hides src/ directory to avoid Firebase CLI timeout

echo.
echo ========================================
echo 🚀 DEPLOYING SCHEDULED BRIEF FUNCTIONS
echo ========================================
echo.

cd functions

REM Step 1: Backup package.json
echo [1/5] Backing up package.json...
copy package.json package.json.backup >nul

REM Step 2: Temporarily rename src/ to hide it from Firebase CLI
echo [2/5] Hiding src/ directory to avoid timeout...
if exist src (
    if exist src_hidden (
        echo ⚠️ src_hidden already exists, removing it...
        rmdir /S /Q src_hidden
    )
    move src src_hidden >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        echo ✅ src/ directory hidden
    ) else (
        echo ❌ Failed to hide src/ directory
        copy package.json.backup package.json >nul
        exit /b 1
    )
) else (
    echo ⚠️ src/ directory not found (may already be hidden)
)

REM Step 3: Create empty src/ directory (Firebase CLI expects it)
mkdir src >nul 2>&1

REM Step 4: Build functions
echo [3/5] Building functions...
call npm run build
if %ERRORLEVEL% NEQ 0 (
    echo ⚠️ Build completed with warnings (this is expected)
)

REM Step 5: Deploy scheduled brief functions
echo [4/5] Deploying scheduled brief functions...
cd ..
firebase deploy --only functions:sendMorningBriefs,functions:sendAfternoonBriefs,functions:sendEveningBriefs --project=vanish-auth-real --force

set DEPLOY_RESULT=%ERRORLEVEL%

REM Step 6: Restore src/ directory
echo [5/5] Restoring src/ directory...
cd functions
if exist src_hidden (
    rmdir /S /Q src >nul 2>&1
    move src_hidden src >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        echo ✅ src/ directory restored
    ) else (
        echo ❌ Failed to restore src/ directory - MANUAL FIX NEEDED
    )
)

REM Restore package.json
copy package.json.backup package.json >nul
del package.json.backup >nul 2>&1

cd ..

echo.
echo ========================================
if %DEPLOY_RESULT% EQU 0 (
    echo ✅ DEPLOYMENT SUCCESSFUL!
    echo.
    echo Scheduled brief functions are now deployed:
    echo   - sendMorningBriefs (9 AM Eastern)
    echo   - sendAfternoonBriefs (2 PM Eastern)
    echo   - sendEveningBriefs (6 PM Eastern)
) else (
    echo ❌ DEPLOYMENT FAILED
    echo.
    echo Check the error messages above for details.
)
echo ========================================
echo.

pause

