@echo off
echo.
echo ========================================
echo 🌙 MANUALLY TRIGGERING EVENING BRIEF
echo ========================================
echo.

echo Calling sendEveningBriefsHttp...
echo.
$response = Invoke-WebRequest -Uri "https://us-central1-vanish-auth-real.cloudfunctions.net/sendEveningBriefsHttp" -Method GET -UseBasicParsing
echo.
echo Status Code: $response.StatusCode
echo.
echo Response:
echo $response.Content
echo.
echo ========================================
echo ✅ Evening brief triggered
echo ========================================
echo.
echo Check your app for the brief notification
echo.
pause

