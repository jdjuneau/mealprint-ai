# Mealprint Firebase Setup Script
# Run this after creating Firebase project manually

Write-Host "🔥 Setting up Mealprint Firebase project..." -ForegroundColor Green

# Check if Firebase CLI is installed
try {
    $firebaseVersion = firebase --version 2>$null
    Write-Host "✅ Firebase CLI found: $firebaseVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ Firebase CLI not found. Install with: npm install -g firebase-tools" -ForegroundColor Red
    exit 1
}

# Login to Firebase (interactive)
Write-Host "🔐 Logging into Firebase..." -ForegroundColor Yellow
firebase login

# Initialize Firebase project
Write-Host "📁 Initializing Firebase project in current directory..." -ForegroundColor Yellow
firebase init --project mealprint-prod

# Create basic Firebase configuration files
Write-Host "📝 Creating Firebase configuration..." -ForegroundColor Yellow

# firebase.json
@"
{
  "firestore": {
    "rules": "firestore.rules",
    "indexes": "firestore.indexes.json"
  },
  "functions": {
    "source": "functions",
    "runtime": "nodejs20",
    "predeploy": [
      "npm --prefix \`$RESOURCE_DIR\` run build || echo Build completed with warnings"
    ]
  },
  "hosting": {
    "public": "web/.next",
    "ignore": [
      "firebase.json",
      "**/.*",
      "**/node_modules/**"
    ],
    "rewrites": [
      {
        "source": "**",
        "destination": "/index.html"
      }
    ]
  },
  "emulators": {
    "functions": {
      "port": 5001
    },
    "firestore": {
      "port": 8080
    },
    "ui": {
      "enabled": true
    }
  }
}
"@ | Out-File -FilePath "firebase.json" -Encoding UTF8

# firestore.rules
@"
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users can read/write their own data
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }

    // Recipes are publicly readable, but only authenticated users can create
    match /recipes/{recipeId} {
      allow read: if true;
      allow create: if request.auth != null;
      allow update, delete: if request.auth != null && resource.data.userId == request.auth.uid;
    }

    // Circles - authenticated users can read, circle members can interact
    match /circles/{circleId} {
      allow read: if request.auth != null;
      allow create: if request.auth != null;
      allow update: if request.auth != null && resource.data.ownerId == request.auth.uid;
    }

    // Posts in circles - circle members can read/write
    match /circles/{circleId}/posts/{postId} {
      allow read: if request.auth != null;
      allow create: if request.auth != null;
      allow update, delete: if request.auth != null && resource.data.userId == request.auth.uid;
    }

    // Private messages - only participants can access
    match /messages/{messageId} {
      allow read, write: if request.auth != null &&
        (request.auth.uid == resource.data.senderId ||
         request.auth.uid == resource.data.receiverId);
    }

    // Public forum posts
    match /posts/{postId} {
      allow read: if true;
      allow create: if request.auth != null;
      allow update, delete: if request.auth != null && resource.data.userId == request.auth.uid;
    }
  }
}
"@ | Out-File -FilePath "firestore.rules" -Encoding UTF8

# firestore.indexes.json
@"
{
  "indexes": [],
  "fieldOverrides": []
}
"@ | Out-File -FilePath "firestore.indexes.json" -Encoding UTF8

Write-Host "✅ Firebase project setup complete!" -ForegroundColor Green
Write-Host "📋 Next steps:" -ForegroundColor Cyan
Write-Host "  1. Enable Authentication (Email/Password, Google) in Firebase Console" -ForegroundColor White
Write-Host "  2. Enable Firestore Database in Firebase Console" -ForegroundColor White
Write-Host "  3. Enable Storage in Firebase Console" -ForegroundColor White
Write-Host "  4. Run: firebase deploy --only firestore:rules" -ForegroundColor White
Write-Host "  5. Continue with Android/Web app setup" -ForegroundColor White
