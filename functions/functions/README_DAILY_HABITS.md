# Daily Habits Generation Cloud Function

This Firebase Cloud Function automatically generates personalized tiny habits for users based on their sleep data, health metrics, and behavioral patterns using AI.

## 🚀 Features

- **AI-Powered Habit Generation**: Uses GPT-4o to create personalized tiny habits
- **Sleep-Based Insights**: Analyzes last night's sleep duration and quality
- **Health Data Integration**: Pulls from existing health logs (HRV, activity, etc.)
- **User Profile Context**: Considers Four Tendencies, reward preferences, and habit history
- **Push Notifications**: Sends rich Expo push notifications with deep links
- **Scheduled Execution**: Runs automatically every morning at 7 AM
- **Manual Trigger**: HTTPS callable function for testing and manual execution

## 📋 Function Overview

### `generateDailyHabits` (HTTPS Callable)
- **Trigger**: Manual/API call
- **Parameters**: `{ userId?: string }` (optional, defaults to authenticated user)
- **Returns**: Success status and generation results

### `generateDailyHabitsScheduled` (Cloud Scheduler)
- **Trigger**: Daily at 7:00 AM EST
- **Scope**: All users with nudges enabled
- **Process**: Batches users for efficient processing

## 🔄 Data Flow

```
1. User Profile Fetch
   ├── Four Tendencies assessment
   ├── Reward preferences
   ├── Keystone habits & frictions
   └── Habit goals

2. Sleep Data Analysis
   ├── Last night's duration (hours)
   ├── Sleep quality (1-5 scale)
   ├── Deep/REM sleep (if available)
   └── HRV metrics (if available)

3. Existing Habits Review
   ├── Current active habits
   ├── Success rates & streaks
   └── Completion patterns

4. AI Generation (GPT-4o)
   ├── Personalized habit prescription
   ├── 1-3 tiny habits created
   ├── Rationale for each habit
   └── Expected difficulty assessment

5. Firestore Storage
   ├── New habits saved to users/{userId}/habits
   ├── Automatic metadata generation
   └── Streak initialization

6. Push Notification
   ├── Rich Expo notification
   ├── Deep link to MorningBriefing screen
   ├── Personalized message
   └── Motivation quote
```

## 🛠️ Technical Implementation

### Dependencies
```json
{
  "openai": "^4.28.0",
  "expo-server-sdk": "^3.7.0",
  "firebase-admin": "^12.0.0",
  "firebase-functions": "^5.0.0"
}
```

### Environment Variables
```bash
# OpenAI API Key
OPENAI_API_KEY=your_openai_key_here

# Optional: Firebase config
FIREBASE_CONFIG=your_firebase_config
```

### Firestore Data Structure

#### User Profile (`users/{userId}/profile/habits`)
```json
{
  "fourTendencies": {
    "tendency": "UPHOLDER",
    "scores": { "upholder": 8, "questioner": 6, "obliger": 4, "rebel": 2 },
    "assessedAt": "2024-01-15T10:00:00Z"
  },
  "rewardPreferences": ["ACHIEVEMENT_BADGE", "SOCIAL_RECOGNITION"],
  "keystoneHabits": ["habit123", "habit456"],
  "biggestFrictions": ["morning_routine", "evening_wind_down"],
  "habitGoals": {
    "weight_loss": "lose 5kg",
    "fitness": "run 5k without stopping"
  },
  "updatedAt": "2024-01-15T10:00:00Z"
}
```

#### Generated Habits (`users/{userId}/habits/{habitId}`)
```json
{
  "title": "Morning sunlight exposure",
  "description": "Get 10 minutes of natural sunlight within 1 hour of waking",
  "category": "HEALTH",
  "frequency": "DAILY",
  "targetValue": 10,
  "unit": "minutes",
  "isActive": true,
  "priority": "MEDIUM",
  "color": "#FFD700",
  "icon": "sun",
  "streakCount": 0,
  "longestStreak": 0,
  "totalCompletions": 0,
  "successRate": 0,
  "createdAt": "2024-01-15T07:00:00Z",
  "updatedAt": "2024-01-15T07:00:00Z",
  "userId": "user123"
}
```

## 🤖 AI Prompt Engineering

### Master System Prompt
```
You are Coachie, an AI-powered habit coach that creates personalized tiny habits based on users' sleep data, health metrics, and behavioral patterns.

Your goal is to prescribe 1-3 tiny habits that are:
- Immediately actionable (under 2 minutes)
- Highly likely to succeed (>80% success rate)
- Aligned with user's current energy and health state
- Building toward their long-term goals

Consider:
- Sleep quality and duration from last night
- Current energy levels and circadian rhythm
- User's Four Tendencies personality type
- Existing habit success patterns
- Time of day and daily rhythm
- Weather and environmental factors (if available)

Generate habits that feel effortless and rewarding, not burdensome.
Focus on "bright spots" - things the user is already doing well.
```

### Function Schema
```json
{
  "type": "function",
  "function": {
    "name": "generate_tiny_habit_prescription",
    "description": "Generate 1-3 personalized tiny habits based on user's current state",
    "parameters": {
      "type": "object",
      "properties": {
        "habits": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "title": { "type": "string" },
              "description": { "type": "string" },
              "category": { "type": "string", "enum": ["HEALTH", "FITNESS", "NUTRITION", "SLEEP", "HYDRATION", "PRODUCTIVITY", "MINDFULNESS", "SOCIAL", "LEARNING", "CUSTOM"] },
              "targetValue": { "type": "number" },
              "unit": { "type": "string" },
              "priority": { "type": "string", "enum": ["LOW", "MEDIUM", "HIGH", "CRITICAL"] },
              "rationale": { "type": "string" },
              "expectedDifficulty": { "type": "string", "enum": ["VERY_EASY", "EASY", "MODERATE", "CHALLENGING"] }
            },
            "required": ["title", "description", "category", "targetValue", "unit", "priority", "rationale", "expectedDifficulty"]
          },
          "minItems": 1,
          "maxItems": 3
        },
        "personalizedMessage": { "type": "string" },
        "motivation": { "type": "string" }
      },
      "required": ["habits", "personalizedMessage", "motivation"]
    }
  }
}
```

## 📱 Push Notifications

### Notification Structure
```typescript
{
  to: user.expoPushToken,
  title: '🌅 Your Morning Habit Prescription',
  body: prescription.personalizedMessage,
  data: {
    screen: 'MorningBriefing',
    habits: prescription.habits.length,
    motivation: prescription.motivation
  },
  sound: 'default',
  priority: 'default',
  ttl: 86400, // 24 hours
  badge: prescription.habits.length
}
```

### Deep Link Handling
The notification deep links to the `MorningBriefing` screen with habit data. The app should:
1. Navigate to MorningBriefing screen
2. Display the new habits
3. Show the personalized message and motivation
4. Allow immediate habit completion

## 🚀 Deployment

### Prerequisites
1. Firebase project with Functions enabled
2. OpenAI API key configured
3. Expo push notification setup
4. Firestore security rules updated

### Deployment Steps
```bash
# Install dependencies
cd functions
npm install

# Build TypeScript
npm run build

# Deploy functions
firebase deploy --only functions
```

### Scheduling Setup
```bash
# Set up Cloud Scheduler (run once)
gcloud scheduler jobs create pubsub generate-daily-habits \
  --schedule="0 7 * * *" \
  --topic="generate-daily-habits" \
  --message-body="{}" \
  --time-zone="America/New_York"
```

## 🧪 Testing

### Manual Testing (HTTPS Callable)
```javascript
// From client app
import { httpsCallable } from 'firebase/functions';
import { functions } from './firebase';

const generateHabits = httpsCallable(functions, 'generateDailyHabits');

// Test for current user
await generateHabits();

// Test for specific user (admin)
await generateHabits({ userId: 'specific-user-id' });
```

### Emulator Testing
```bash
# Start Firebase emulator
firebase emulators:start --only functions

# Test the function
curl -X POST http://localhost:5001/coachie/us-central1/generateDailyHabits \
  -H "Content-Type: application/json" \
  -d '{"data": {"userId": "test-user-id"}}'
```

## 📊 Monitoring & Analytics

### Cloud Functions Logs
```bash
# View function logs
firebase functions:log

# Filter by function
firebase functions:log --only generateDailyHabits
```

### Success Metrics
- **Generation Success Rate**: Functions that complete without errors
- **Habit Creation Rate**: Average habits created per user
- **Notification Delivery Rate**: Push notifications successfully sent
- **User Engagement**: Deep link clicks and habit completions

### Error Handling
- OpenAI API failures → Fallback to predefined habits
- Sleep data unavailable → Use reasonable defaults
- Push token invalid → Skip notification (log for cleanup)
- Firestore errors → Retry logic with exponential backoff

## 🔧 Configuration

### Environment Variables
```bash
# Required
OPENAI_API_KEY=sk-...

# Optional (for different AI providers)
GROQ_API_KEY=gsk-...
ANTHROPIC_API_KEY=sk-ant-...
```

### Firebase Config
```javascript
// functions.config()
{
  openai: {
    key: "sk-..."
  },
  expo: {
    access_token: "..." // if needed
  }
}
```

## 🎯 Example Output

### AI-Generated Habits
```json
{
  "habits": [
    {
      "title": "Gentle morning stretch",
      "description": "3 minutes of gentle stretching to wake up your body",
      "category": "FITNESS",
      "targetValue": 3,
      "unit": "minutes",
      "priority": "MEDIUM",
      "rationale": "Your sleep quality suggests you need gentle movement to start your day",
      "expectedDifficulty": "VERY_EASY"
    },
    {
      "title": "Hydration reminder",
      "description": "Drink a glass of water when you wake up",
      "category": "HYDRATION",
      "targetValue": 8,
      "unit": "oz",
      "priority": "HIGH",
      "rationale": "Morning hydration supports your energy levels throughout the day",
      "expectedDifficulty": "EASY"
    }
  ],
  "personalizedMessage": "Good morning! Based on your restful 7.5 hours of sleep, here are two gentle habits to start your day with intention.",
  "motivation": "Small, consistent actions create lasting change."
}
```

### Push Notification
```
Title: 🌅 Your Morning Habit Prescription
Body: Good morning! Based on your restful 7.5 hours of sleep, here are two gentle habits to start your day with intention.

[Deep link to MorningBriefing screen]
```

This system creates a powerful, personalized habit formation experience that adapts to each user's unique patterns and needs.
