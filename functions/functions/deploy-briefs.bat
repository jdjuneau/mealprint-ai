@echo off
echo 📧 Deploying BRIEF functions...
firebase deploy --only functions:sendMorningBriefs,functions:sendAfternoonBriefs,functions:sendEveningBriefs

if %errorlevel% neq 0 (
    echo ❌ BRIEF FUNCTIONS FAILED
    pause
    exit /b 1
)

echo ✅ Brief functions deployed successfully!
pause