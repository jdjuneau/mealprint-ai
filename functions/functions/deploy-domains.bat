@echo off
echo 🚀 Deploying Firebase Functions Domains...

echo.
echo 📧 Deploying BRIEFS domain...
firebase deploy --only functions --config firebase-briefs.json

echo.
echo 💳 Deploying PAYMENTS domain...
firebase deploy --only functions --config firebase-payments.json

echo.
echo 🔔 Deploying NOTIFICATIONS domain...
firebase deploy --only functions --config firebase-notifications.json

echo.
echo 📊 Deploying ANALYTICS domain...
firebase deploy --only functions --config firebase-analytics.json

echo.
echo 👥 Deploying SOCIAL domain...
firebase deploy --only functions --config firebase-social.json

echo.
echo 🍳 Deploying RECIPES domain...
firebase deploy --only functions --config firebase-recipes.json

echo.
echo 🔧 Deploying ADMIN domain...
firebase deploy --only functions --config firebase-admin.json

echo.
echo ✅ All domains deployed successfully!