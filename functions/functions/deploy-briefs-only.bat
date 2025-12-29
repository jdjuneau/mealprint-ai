@echo off
echo 🚀 Deploying Brief Functions ONLY
echo This deploys the critical daily brief functions
echo.

cd /d "%~dp0"

echo 📦 Building functions...
call npm run build
if %errorlevel% neq 0 (
    echo ⚠️ Build had warnings but continuing...
)

echo.
echo 🚀 Deploying brief functions...
firebase deploy --only functions:sendMorningBriefs,functions:sendAfternoonBriefs,functions:sendEveningBriefs

if %errorlevel% neq 0 (
    echo ❌ Brief functions deployment failed
    pause
    exit /b 1
)

echo.
echo ✅ Brief functions deployed successfully!
echo.
echo 📋 Brief Schedule:
echo - Morning: 9 AM EST (all users)
echo - Afternoon: 2 PM EST (Pro users only)
echo - Evening: 6 PM EST (Pro users only)
echo.
pause

@echo off
echo 🚀 Deploying Brief Functions ONLY
echo This deploys the critical daily brief functions
echo.

cd /d "%~dp0"

echo 📦 Building functions...
call npm run build
if %errorlevel% neq 0 (
    echo ⚠️ Build had warnings but continuing...
)

echo.
echo 🚀 Deploying brief functions...
firebase deploy --only functions:sendMorningBriefs,functions:sendAfternoonBriefs,functions:sendEveningBriefs

if %errorlevel% neq 0 (
    echo ❌ Brief functions deployment failed
    pause
    exit /b 1
)

echo.
echo ✅ Brief functions deployed successfully!
echo.
echo 📋 Brief Schedule:
echo - Morning: 9 AM EST (all users)
echo - Afternoon: 2 PM EST (Pro users only)
echo - Evening: 6 PM EST (Pro users only)
echo.
pause

