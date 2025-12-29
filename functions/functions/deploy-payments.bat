@echo off
echo 💳 Deploying PAYMENT functions...
firebase deploy --only functions:createStripeCheckoutSession,functions:getSubscriptionPlans,functions:createPayPalOrder,functions:verifyStripePayment,functions:verifyPayPalPayment,functions:cancelStripeSubscription,functions:cancelPayPalSubscription,functions:getSubscriptionStatus,functions:processStripeWebhook,functions:processPayPalWebhook,functions:checkStripeConfig

if %errorlevel% neq 0 (
    echo ❌ PAYMENT FUNCTIONS FAILED
    pause
    exit /b 1
)

echo ✅ Payment functions deployed successfully!
pause