@echo off
echo 📧 Deploying BRIEF functions as callable functions...
firebase deploy --only functions:sendMorningBriefs,functions:sendAfternoonBriefs,functions:sendEveningBriefs,functions:triggerMorningBrief,functions:triggerAfternoonBrief,functions:triggerEveningBrief

if %errorlevel% neq 0 (
    echo ❌ BRIEF FUNCTIONS FAILED
    pause
    exit /b 1
)

echo ✅ Brief functions deployed successfully!
echo.
echo 🔧 MANUAL TESTING:
echo firebase functions:call sendMorningBriefs
echo firebase functions:call sendAfternoonBriefs  
echo firebase functions:call sendEveningBriefs
echo.
echo 📅 CLOUD SCHEDULER SETUP (run after deployment):
echo Run: ./setup-cloud-scheduler.sh
pause