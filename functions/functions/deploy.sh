#!/bin/bash

# Coachie Firebase Functions Deployment Script

echo "🚀 Deploying Coachie Firebase Functions..."

# Check if Firebase CLI is installed
if ! command -v firebase &> /dev/null; then
    echo "❌ Firebase CLI is not installed. Please install it first:"
    echo "npm install -g firebase-tools"
    exit 1
fi

# Check if user is logged in
if ! firebase projects:list &> /dev/null; then
    echo "❌ Not logged in to Firebase. Please login first:"
    echo "firebase login"
    exit 1
fi

# Install dependencies
echo "📦 Installing dependencies..."
npm install

# Set Gemini API key (you'll need to provide this)
echo "🤖 Setting up Gemini API key..."
echo "Make sure to set your Gemini API key:"
echo "firebase functions:config:set gemini.api_key=\"YOUR_API_KEY_HERE\""

# Deploy functions
echo "⚡ Deploying functions..."
firebase deploy --only functions

echo "✅ Deployment complete!"
echo "📊 Check function logs with: firebase functions:log --only sendDailyNudges"
echo "🧪 Test the function with: firebase functions:shell"
