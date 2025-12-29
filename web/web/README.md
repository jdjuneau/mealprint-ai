# Coachie Web App

A comprehensive web version of the Coachie fitness tracking app with AI-powered features.

## Features

- **Meal Logging**: Upload photos of meals for AI analysis or manual entry
- **AI Chat**: Get personalized fitness advice from your AI coach
- **Health Tracking**: Log workouts, supplements, sleep, water, and weight
- **Progress Dashboard**: View your daily stats and progress
- **Saved Meals**: Quick-select frequently eaten meals
- **Cross-Platform Sync**: Data syncs with mobile app via Firebase

## Tech Stack

- **Framework**: Next.js 14 with App Router
- **Language**: TypeScript
- **Styling**: Tailwind CSS
- **Backend**: Firebase (Auth, Firestore, Storage)
- **AI**: OpenAI GPT-4 Vision API
- **Icons**: Heroicons

## Setup Instructions

### 1. Prerequisites

- Node.js 18+
- npm or yarn
- Firebase project
- OpenAI API key

### 2. Clone and Install

```bash
cd web
npm install
```

### 3. Environment Configuration

Create a `.env.local` file in the web directory:

```env
# Firebase Configuration (from Firebase Console)
NEXT_PUBLIC_FIREBASE_API_KEY=your_firebase_api_key
NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=your_project.firebaseapp.com
NEXT_PUBLIC_FIREBASE_PROJECT_ID=your_project_id
NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET=your_project.appspot.com
NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID=your_sender_id
NEXT_PUBLIC_FIREBASE_APP_ID=your_app_id

# OpenAI Configuration
NEXT_PUBLIC_OPENAI_API_KEY=your_openai_api_key
```

### 4. Firebase Setup

1. **Enable Authentication**:
   - Go to Firebase Console → Authentication
   - Enable Google and Email/Password sign-in

2. **Configure Firestore Security Rules**:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users can read/write their own data
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }

    // Daily logs
    match /dailyLogs/{logId} {
      allow read, write: if request.auth != null && logId.matches(request.auth.uid + '_.*');
    }

    // Health logs
    match /healthLogs/{logId} {
      allow read, write: if request.auth != null;
    }

    // Chat messages
    match /chats/{userId}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }

    // Saved meals
    match /savedMeals/{mealId} {
      allow read, write: if request.auth != null;
    }

    // Supplements (read-only for users)
    match /supplements/{supplementId} {
      allow read: if request.auth != null;
    }
  }
}
```

3. **Storage Rules** (if using file uploads):

```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /users/{userId}/{allPaths=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

### 5. OpenAI Setup

1. Get your API key from [OpenAI Platform](https://platform.openai.com/api-keys)
2. Add it to your `.env.local` file

### 6. Run Development Server

```bash
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) in your browser.

## Project Structure

```
web/
├── app/                    # Next.js App Router
│   ├── layout.tsx         # Root layout with auth provider
│   ├── page.tsx           # Main dashboard
│   └── globals.css        # Global styles
├── components/            # React components
│   ├── AuthScreen.tsx     # Authentication UI
│   ├── Dashboard.tsx      # Main dashboard container
│   ├── DashboardStats.tsx # Stats and insights
│   ├── MealLogger.tsx     # Meal logging interface
│   ├── AIChat.tsx         # AI chatbot interface
│   ├── Navigation.tsx     # App navigation
│   └── ...
├── lib/                   # Utility libraries
│   ├── contexts/          # React contexts (Auth)
│   ├── services/          # Firebase and AI services
│   └── firebase.ts        # Firebase configuration
├── types/                 # TypeScript type definitions
└── public/               # Static assets
```

## Key Features Implementation

### AI-Powered Meal Analysis

- **Photo Upload**: Drag & drop or click to upload meal photos
- **Vision API**: GPT-4 Vision analyzes food images
- **Smart Caching**: Prevents re-analysis of identical images
- **Rate Limiting**: 5 analyses per hour per user

### Real-time Chat

- **Contextual Responses**: AI remembers conversation history
- **Personalized Advice**: Uses user profile data
- **Caching**: Avoids duplicate API calls for similar questions

### Cross-Platform Sync

- **Firestore**: Real-time data synchronization
- **Shared Schema**: Mobile and web use identical data models
- **Offline Support**: Local caching with sync on reconnect

## Deployment

### Firebase Hosting

```bash
# Install Firebase CLI
npm install -g firebase-tools

# Initialize Firebase in the web directory
firebase init hosting

# Build and deploy
npm run build
firebase deploy
```

### Environment Variables for Production

Set environment variables in Firebase Hosting:

```bash
firebase functions:config:set \
  openai.api_key="your_openai_key"

# For hosting environment variables, use Firebase Console or .env files
```

## Cost Optimization

The app includes several cost-saving features:

- **Response Caching**: Similar questions reuse cached responses
- **Rate Limiting**: Prevents excessive API usage
- **Image Compression**: Reduces Vision API costs
- **Token Limits**: Constrains response lengths
- **Smart Prompts**: Efficient AI prompting

## Contributing

1. Follow TypeScript strict mode
2. Use Tailwind for styling
3. Test on both desktop and mobile
4. Ensure Firebase security rules are updated for new features

## Support

For issues or questions:
- Check Firebase Console for data issues
- Verify OpenAI API key and usage limits
- Ensure environment variables are set correctly
