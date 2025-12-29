# Testing Guide: Coachie Web App

## 🚀 Starting the Development Server

```bash
cd web
npm run dev
```

The app will be available at: **http://localhost:3000**

## ✅ Pre-Testing Checklist

1. **Environment Variables:**
   - ✅ `.env.local` exists with all required variables
   - ✅ Firebase config is set
   - ✅ OpenAI/Gemini keys are set
   - ✅ Fitbit/Strava keys (optional for basic testing)

2. **Dependencies:**
   ```bash
   cd web
   npm install
   ```

## 🧪 Testing Checklist

### 1. Basic Navigation & Authentication

- [ ] **Home Page** (`/`)
  - Page loads without errors
  - Navigation works
  - Login/Register buttons work

- [ ] **Authentication** (`/auth`)
  - Can register new account
  - Can login with existing account
  - Firebase auth works
  - Redirects after login work

### 2. Core Features

- [ ] **Dashboard/Home** (`/home`)
  - Loads user data
  - Shows Coachie score
  - Displays daily stats

- [ ] **Daily Log** (`/daily-log`)
  - Can log meals
  - Can log water
  - Can log workouts
  - Can log sleep
  - Data saves to Firebase

- [ ] **Health Tracking** (`/health-tracking`)
  - Page loads
  - Shows Bluetooth support status
  - Shows Fitbit/Strava connection options
  - Test page works (`/health-tracking/test`)

### 3. AI Features

- [ ] **AI Chat** (`/ai-chat`)
  - Chat interface loads
  - Can send messages
  - AI responds (check OpenAI/Gemini keys)

- [ ] **Meal Logger** (`/meal-log`)
  - Can upload meal photos
  - Gemini analysis works (if configured)
  - Results display correctly

- [ ] **Supplement Logger** (`/supplement-photo`)
  - Can upload supplement photos
  - Analysis works

### 4. Subscription & Debug

- [ ] **Debug Page** (`/debug`)
  - Shows user info
  - Can switch between FREE/PRO tiers
  - Subscription updates work

- [ ] **Subscription Features**
  - Test as FREE user (limited features)
  - Switch to PRO in debug page
  - Test PRO features (unlimited AI, etc.)

### 5. Wearable Integration (Optional)

- [ ] **Fitbit OAuth** (`/health-tracking`)
  - Click "Connect" button
  - OAuth flow redirects correctly
  - Callback page works (`/auth/fitbit/callback`)
  - Token exchange works

- [ ] **Strava OAuth**
  - Click "Connect" button
  - OAuth flow redirects correctly
  - Callback page works (`/auth/strava/callback`)

- [ ] **Web Bluetooth** (Chrome/Edge only)
  - "Connect Bluetooth Device" button works
  - Can pair with fitness tracker
  - Data syncs

### 6. Data Persistence

- [ ] **Firebase Connection**
  - Data saves correctly
  - Data loads on page refresh
  - No console errors related to Firebase

- [ ] **User Profile**
  - Profile loads
  - Can edit profile
  - Changes persist

## 🔍 Testing Tools

### Browser DevTools

1. **Console Tab:**
   - Check for errors (red messages)
   - Check for warnings (yellow messages)
   - Verify Firebase connections

2. **Network Tab:**
   - Check API calls succeed
   - Verify Firebase requests
   - Check OAuth redirects

3. **Application Tab:**
   - Check localStorage/sessionStorage
   - Verify Firebase auth tokens

### Test Pages

- **Health Tracking Test:** `/health-tracking/test`
  - Run diagnostic tests
  - Check service configurations
  - Verify connections

- **Debug Page:** `/debug`
  - View user data
  - Switch subscription tiers
  - Test features as different user types

## 🐛 Common Issues & Fixes

### Issue: "Firebase not initialized"
**Fix:** Check `.env.local` has all Firebase config variables

### Issue: "OAuth redirect not working"
**Fix:** 
- Verify redirect URIs in Fitbit/Strava apps
- Check environment variables are set
- Ensure callback pages exist

### Issue: "AI features not working"
**Fix:**
- Check OpenAI/Gemini keys in `.env.local`
- Verify Firebase Cloud Functions are deployed
- Check browser console for errors

### Issue: "Page not loading"
**Fix:**
- Check `npm run dev` is running
- Verify port 3000 is not in use
- Check for TypeScript/build errors

### Issue: "Styling broken"
**Fix:**
- Run `npm install` to ensure dependencies
- Check Tailwind CSS is configured
- Clear `.next` folder and rebuild

## 📝 Testing Workflow

1. **Start dev server:**
   ```bash
   cd web
   npm run dev
   ```

2. **Open browser:**
   - Go to http://localhost:3000
   - Open DevTools (F12)

3. **Test in order:**
   - Authentication first
   - Basic navigation
   - Core features
   - AI features
   - Advanced features (wearables)

4. **Check console:**
   - No errors should appear
   - Warnings are usually OK

5. **Test different user types:**
   - Use `/debug` to switch between FREE/PRO
   - Test feature limitations

## 🎯 Quick Test Commands

```bash
# Start dev server
npm run dev

# Check for build errors
npm run build

# Run linter
npm run lint

# Verify wearable setup
npm run verify-wearables
```

## 📊 Expected Results

- ✅ All pages load without errors
- ✅ Authentication works
- ✅ Data saves and loads from Firebase
- ✅ AI features work (with proper keys)
- ✅ No console errors
- ✅ Mobile responsive (test on different screen sizes)

## 🚨 Before Deploying

Make sure:
- [ ] All tests pass locally
- [ ] No console errors
- [ ] Environment variables documented
- [ ] OAuth redirect URIs updated for production
- [ ] Build succeeds: `npm run build`

