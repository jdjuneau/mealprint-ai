# 🍽️ Mealprint AI

**Smart Meal Planning Made Social with AI**

Mealprint AI is a modern meal planning application that combines AI-powered meal inspiration, comprehensive meal planning, and social features to make healthy eating enjoyable and social.

## ✨ Features

### Core Features
- 🤖 **AI Meal Inspiration** - Personalized recipe recommendations based on your preferences
- 📋 **Weekly Meal Planning** - Generate complete meal plans with shopping lists
- 👥 **Social Cooking** - Join cooking circles, share recipes, and connect with food lovers
- 🏷️ **Dietary Management** - Comprehensive allergy and preference tracking
- 📱 **Cross-Platform** - Seamless experience on Android and Web

### Social Features
- 🍳 **Cooking Circles** - Join or create groups with shared interests
- 💬 **Community Forums** - Discuss recipes, cooking tips, and food trends
- 👥 **Recipe Sharing** - Share your favorite recipes with the community
- 💌 **Direct Messaging** - Connect with other food enthusiasts

## 🚀 Getting Started

### Prerequisites
- Node.js 18+
- Android Studio (for Android development)
- Firebase CLI
- Git

### Project Structure
```
mealprint/
├── android/          # Android app
├── web/             # Next.js web app
├── functions/       # Firebase Functions
├── docs/            # Documentation
└── scripts/         # Build and deployment scripts
```

## 🛠️ Development Setup

### 1. Clone and Setup
```bash
git clone https://github.com/your-org/mealprint.git
cd mealprint
```

### 2. Firebase Setup
```bash
# Install Firebase CLI
npm install -g firebase-tools

# Login to Firebase
firebase login

# Initialize project (run in project root)
firebase init
```

### 3. Web App Setup
```bash
cd web
npm install
npm run dev
```

### 4. Android App Setup
```bash
cd android
# Follow Android setup instructions in android/README.md
```

### 5. Firebase Functions Setup
```bash
cd functions
npm install
```

## 📱 Platforms

### Android
- **Min SDK:** API 24 (Android 7.0)
- **Target SDK:** API 34 (Android 14)
- **Language:** Kotlin
- **Architecture:** MVVM with Compose

### Web
- **Framework:** Next.js 14
- **Language:** TypeScript
- **Styling:** Tailwind CSS
- **State Management:** React Context

## 🔧 Tech Stack

- **Frontend:** React, Next.js, Jetpack Compose
- **Backend:** Firebase (Firestore, Functions, Auth, Storage)
- **AI/ML:** OpenAI GPT-4 for meal inspiration
- **Database:** Firestore (NoSQL)
- **Authentication:** Firebase Auth
- **Hosting:** Firebase Hosting

## 📊 Data Architecture

### Core Collections
- `users` - User profiles and preferences
- `recipes` - Recipe database
- `weeklyBlueprints` - Meal plans
- `circles` - Social cooking groups
- `posts` - Forum posts
- `messages` - Direct messages

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 📧 Contact

For questions or support, please open an issue on GitHub.

---

**Made with ❤️ for food lovers everywhere**
