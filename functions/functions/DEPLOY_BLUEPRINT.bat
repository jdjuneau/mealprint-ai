@echo off
REM Deploy generateWeeklyBlueprint function by hiding src/ directory
REM This prevents Firebase CLI timeout during code analysis

echo.
echo ========================================
echo 🚀 DEPLOYING BLUEPRINT FUNCTION
echo ========================================
echo.

cd functions

REM Step 1: Hide src/ directory
echo [1/5] Hiding src/ directory...
if exist src (
    if exist src_hidden_temp_blueprint (
        echo Removing old src_hidden_temp_blueprint...
        rmdir /S /Q src_hidden_temp_blueprint
    )
    move src src_hidden_temp_blueprint >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        echo ✅ src/ hidden
    ) else (
        echo ❌ Failed to hide src/
        exit /b 1
    )
) else (
    echo ⚠️ src/ not found (may already be hidden)
)

REM Step 2: Create empty src/ (Firebase expects it)
mkdir src >nul 2>&1

REM Step 3: Build
echo [2/5] Building...
call npm run build

REM Step 4: Deploy
echo [3/5] Deploying generateWeeklyBlueprint function...
cd ..
firebase deploy --only functions:generateWeeklyBlueprint --project=vanish-auth-real --force

set DEPLOY_RESULT=%ERRORLEVEL%

REM Step 5: Restore src/
echo [4/5] Restoring src/ directory...
cd functions
if exist src_hidden_temp_blueprint (
    rmdir /S /Q src >nul 2>&1
    move src_hidden_temp_blueprint src >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        echo ✅ src/ restored
    ) else (
        echo ❌ FAILED TO RESTORE src/ - MANUAL FIX NEEDED!
        echo    Rename src_hidden_temp_blueprint back to src manually
    )
)

cd ..

echo.
echo ========================================
if %DEPLOY_RESULT% EQU 0 (
    echo ✅ SUCCESS! generateWeeklyBlueprint is now deployed!
) else (
    echo ❌ DEPLOYMENT FAILED
    echo Check error messages above
)
echo ========================================
echo.

pause

