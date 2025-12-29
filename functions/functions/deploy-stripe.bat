@echo off
echo 💳 Deploying STRIPE checkout function...
firebase deploy --only functions:createStripeCheckoutSession

if %errorlevel% neq 0 (
    echo ❌ STRIPE FUNCTION FAILED
    pause
    exit /b 1
)

echo ✅ Stripe checkout function deployed successfully!
pause