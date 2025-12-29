@echo off
echo 🧪 Testing Brief Functions Manually
echo This will trigger brief generation for testing
echo.

cd /d "%~dp0\.."

echo 📧 Testing morning briefs...
firebase functions:call sendMorningBriefs --data "{}"

echo.
echo ☀️ Testing afternoon briefs...
firebase functions:call sendAfternoonBriefs --data "{}"

echo.
echo 🌙 Testing evening briefs...
firebase functions:call sendEveningBriefs --data "{}"

echo.
echo ✅ Manual brief tests complete!
echo Check Firebase Functions logs to see results
echo.
pause

@echo off
echo 🧪 Testing Brief Functions Manually
echo This will trigger brief generation for testing
echo.

cd /d "%~dp0\.."

echo 📧 Testing morning briefs...
firebase functions:call sendMorningBriefs --data "{}"

echo.
echo ☀️ Testing afternoon briefs...
firebase functions:call sendAfternoonBriefs --data "{}"

echo.
echo 🌙 Testing evening briefs...
firebase functions:call sendEveningBriefs --data "{}"

echo.
echo ✅ Manual brief tests complete!
echo Check Firebase Functions logs to see results
echo.
pause

