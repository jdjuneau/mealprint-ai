# Mealprint AI Firebase Initialization Script
# Run this after creating the Firebase project

param(
    [Parameter(Mandatory=$true)]
    [string]$ProjectId
)

Write-Host "🔥 Initializing Mealprint AI Firebase project: $ProjectId" -ForegroundColor Green

# Check if Firebase CLI is installed
try {
    $firebaseVersion = firebase --version 2>$null
    Write-Host "✅ Firebase CLI found: $firebaseVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ Firebase CLI not found. Install with: npm install -g firebase-tools" -ForegroundColor Red
    exit 1
}

# Set the project
Write-Host "🎯 Setting Firebase project to: $ProjectId" -ForegroundColor Yellow
firebase use $ProjectId

# Login to Firebase
Write-Host "🔐 Logging into Firebase..." -ForegroundColor Yellow
firebase login

# Deploy Firestore rules and indexes
Write-Host "📋 Deploying Firestore security rules..." -ForegroundColor Yellow
firebase deploy --only firestore

# Enable required APIs
Write-Host "🔧 Enabling required APIs..." -ForegroundColor Yellow

# Note: These would need to be enabled manually in Google Cloud Console
Write-Host "⚠️  MANUAL STEP REQUIRED:" -ForegroundColor Red
Write-Host "   Go to https://console.cloud.google.com/apis/library" -ForegroundColor White
Write-Host "   Enable these APIs for project $ProjectId :" -ForegroundColor White
Write-Host "   • Cloud Firestore API" -ForegroundColor White
Write-Host "   • Firebase Hosting API" -ForegroundColor White
Write-Host "   • Cloud Functions API" -ForegroundColor White
Write-Host "   • Identity Toolkit API" -ForegroundColor White
Write-Host "   • Google Cloud Storage JSON API" -ForegroundColor White

Write-Host "✅ Firebase initialization complete!" -ForegroundColor Green
Write-Host "📋 Next steps:" -ForegroundColor Cyan
Write-Host "  1. Download Firebase config from Console → Project Settings → General → Your apps" -ForegroundColor White
Write-Host "  2. Add Android app in Firebase Console" -ForegroundColor White
Write-Host "  3. Add Web app in Firebase Console" -ForegroundColor White
Write-Host "  4. Copy config files to android/ and web/ directories" -ForegroundColor White
Write-Host "  5. Run Android app setup" -ForegroundColor White
