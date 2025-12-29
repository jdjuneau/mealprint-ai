@echo off
echo.
echo ========================================
echo 🚀 DEPLOYING processBriefTask FUNCTION
echo ========================================
echo.
echo This function processes individual brief tasks from Cloud Tasks queue.
echo It was missing from index.js exports, causing all brief tasks to fail!
echo.
echo Deploying now...
echo.
firebase deploy --only functions:processBriefTask --force
echo.
echo ========================================
echo ✅ DEPLOYMENT COMPLETE
echo ========================================
echo.
echo The processBriefTask function is now deployed.
echo Brief tasks should now process successfully.
echo.
echo You may want to manually trigger evening brief again to test:
echo   functions\TRIGGER_EVENING_BRIEF_MANUAL.bat
echo.
pause
