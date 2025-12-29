@echo off
echo 🚀 Starting Coachie Web App Dev Server
echo.

cd /d "%~dp0"

echo 📦 Checking dependencies...
if not exist "node_modules" (
    echo Installing dependencies...
    call npm install
    if %errorlevel% neq 0 (
        echo ❌ Failed to install dependencies
        pause
        exit /b 1
    )
)

echo.
echo 🔍 Checking if port 3000 is in use...
netstat -ano | findstr ":3000" >nul
if %errorlevel% equ 0 (
    echo ⚠️ Port 3000 is already in use!
    echo Killing processes on port 3000...
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":3000" ^| findstr "LISTENING"') do (
        taskkill /F /PID %%a >nul 2>&1
    )
    timeout /t 2 >nul
)

echo.
echo 🎯 Starting Next.js dev server...
echo.
echo Server will be available at: http://localhost:3000
echo Press Ctrl+C to stop the server
echo.

call npm run dev

@echo off
echo 🚀 Starting Coachie Web App Dev Server
echo.

cd /d "%~dp0"

echo 📦 Checking dependencies...
if not exist "node_modules" (
    echo Installing dependencies...
    call npm install
    if %errorlevel% neq 0 (
        echo ❌ Failed to install dependencies
        pause
        exit /b 1
    )
)

echo.
echo 🔍 Checking if port 3000 is in use...
netstat -ano | findstr ":3000" >nul
if %errorlevel% equ 0 (
    echo ⚠️ Port 3000 is already in use!
    echo Killing processes on port 3000...
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":3000" ^| findstr "LISTENING"') do (
        taskkill /F /PID %%a >nul 2>&1
    )
    timeout /t 2 >nul
)

echo.
echo 🎯 Starting Next.js dev server...
echo.
echo Server will be available at: http://localhost:3000
echo Press Ctrl+C to stop the server
echo.

call npm run dev

