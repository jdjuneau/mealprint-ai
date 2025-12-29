@echo off
echo 🔔 Deploying NOTIFICATION functions...
firebase deploy --only functions:onCirclePostCreated,functions:onCirclePostLiked,functions:onFriendRequestCreated,functions:onMessageCreated,functions:onCirclePostCreatedFCM,functions:onCirclePostCommented

if %errorlevel% neq 0 (
    echo ❌ NOTIFICATION FUNCTIONS FAILED
    pause
    exit /b 1
)

echo ✅ Notification functions deployed successfully!
pause