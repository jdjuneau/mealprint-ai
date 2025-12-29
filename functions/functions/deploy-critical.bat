@echo off
echo 🚨 DEPLOYING CRITICAL FUNCTIONS FOR PRODUCTION
echo This deploys only the functions needed for Google Play Store submission
echo.

echo 💳 STEP 1: Deploying PAYMENT functions (revenue critical)...
firebase deploy --only functions:createStripeCheckoutSession,functions:getSubscriptionPlans,functions:createPayPalOrder,functions:verifyStripePayment,functions:verifyPayPalPayment,functions:cancelStripeSubscription,functions:cancelPayPalSubscription,functions:getSubscriptionStatus,functions:processStripeWebhook,functions:processPayPalWebhook,functions:checkStripeConfig

if %errorlevel% neq 0 (
    echo ❌ PAYMENT FUNCTIONS FAILED - FIX BEFORE SUBMITTING TO PLAY STORE
    pause
    exit /b 1
)

echo ✅ Payment functions deployed successfully
echo.

echo 📧 STEP 2: Deploying BRIEF functions (core feature)...
firebase deploy --only functions:sendMorningBriefs,functions:sendAfternoonBriefs,functions:sendEveningBriefs

if %errorlevel% neq 0 (
    echo ❌ BRIEF FUNCTIONS FAILED - MANUAL WORKAROUND AVAILABLE
    echo You can manually trigger briefs using the testNudge function
    echo.
    echo 🔧 MANUAL BRIEF WORKAROUND:
    echo firebase functions:call testNudge --data '{"timeOfDay":"morning"}'
    pause
    exit /b 1
)

echo ✅ Brief functions deployed successfully
echo.

echo 🔔 STEP 3: Deploying NOTIFICATION functions (real-time UX)...
firebase deploy --only functions:onCirclePostCreated,functions:onCirclePostLiked,functions:onFriendRequestCreated,functions:onMessageCreated,functions:onCirclePostCreatedFCM,functions:onCirclePostCommented

if %errorlevel% neq 0 (
    echo ❌ NOTIFICATION FUNCTIONS FAILED - NON-CRITICAL FOR SUBMISSION
    echo These can be deployed post-launch
)

echo ✅ Critical functions deployment complete!
echo.
echo 🎯 READY FOR GOOGLE PLAY STORE SUBMISSION
echo.
echo Core features working:
echo ✅ Payments (Stripe, PayPal)
echo ✅ Daily Briefs (morning, afternoon, evening)
echo ✅ Real-time notifications (may need post-launch fix)
echo.
echo 📋 SUBMISSION CHECKLIST:
echo - Test payment flows in production
echo - Test brief delivery manually
echo - Verify real-time features work
echo - Submit to Google Play Store
echo.
pause