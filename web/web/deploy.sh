#!/bin/bash

# Coachie Web App Deployment Script

echo "🏗️  Building Coachie Web App..."
npm run build

if [ $? -ne 0 ]; then
    echo "❌ Build failed!"
    exit 1
fi

echo "🚀 Deploying to Firebase Hosting..."
firebase deploy --only hosting

if [ $? -eq 0 ]; then
    echo "✅ Deployment successful!"
    echo "🌐 Your app is now live!"
else
    echo "❌ Deployment failed!"
    exit 1
fi
