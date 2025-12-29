# Next.js App Router Routing Plan

## Android Navigation Routes to Port

Based on MainNavHost.kt, here are all the routes that need Next.js pages:

### Auth & Onboarding
- `/` (splash) → `app/page.tsx` (already exists, needs enhancement)
- `/welcome` → `app/welcome/page.tsx`
- `/auth` → `app/auth/page.tsx` (already have AuthScreen component)
- `/set-goals` → `app/set-goals/page.tsx`

### Main App
- `/home` → `app/home/page.tsx` (main dashboard - HomeScreen)
- `/profile` → `app/profile/page.tsx`
- `/settings` → `app/settings/page.tsx`

### Logging Screens
- `/meal-log` → `app/meal-log/page.tsx`
- `/workout-log` → `app/workout-log/page.tsx`
- `/sleep-log` → `app/sleep-log/page.tsx`
- `/water-log` → `app/water-log/page.tsx`
- `/weight-log` → `app/weight-log/page.tsx`
- `/mood-tracker` → `app/mood-tracker/page.tsx`
- `/supplement-photo` → `app/supplement-photo/page.tsx`

### AI Features
- `/ai-chat` → `app/ai-chat/page.tsx`
- `/meal-recommendation` → `app/meal-recommendation/page.tsx`
- `/weekly-blueprint` → `app/weekly-blueprint/page.tsx`
- `/recipe-capture` → `app/recipe-capture/page.tsx`

### Social
- `/community` → `app/community/page.tsx`
- `/friends` → `app/friends/page.tsx`
- `/messaging` → `app/messaging/page.tsx`
- `/circle-join` → `app/circle-join/page.tsx`
- `/circle-create` → `app/circle-create/page.tsx`
- `/circle-detail/[circleId]` → `app/circle-detail/[circleId]/page.tsx`
- `/forum-detail/[postId]` → `app/forum-detail/[postId]/page.tsx`

### Habits & Goals
- `/habits` → `app/habits/page.tsx`
- `/habit-creation` → `app/habit-creation/page.tsx`
- `/habit-progress` → `app/habit-progress/page.tsx`
- `/habit-suggestions` → `app/habit-suggestions/page.tsx`
- `/habit-templates` → `app/habit-templates/page.tsx`
- `/habit-intelligence` → `app/habit-intelligence/page.tsx`
- `/habit-timer/[habitId]` → `app/habit-timer/[habitId]/page.tsx`
- `/goals-breakdown` → `app/goals-breakdown/page.tsx`
- `/goals-edit` → `app/goals-edit/page.tsx`

### Wellness
- `/meditation` → `app/meditation/page.tsx`
- `/breathing-exercises` → `app/breathing-exercises/page.tsx`
- `/body-scan` → `app/body-scan/page.tsx`
- `/grounding-exercise` → `app/grounding-exercise/page.tsx`
- `/social-media-break` → `app/social-media-break/page.tsx`
- `/wellness` → `app/wellness/page.tsx`
- `/health-tracking` → `app/health-tracking/page.tsx`

### Analytics & Progress
- `/charts` → `app/charts/page.tsx`
- `/streak-detail` → `app/streak-detail/page.tsx`
- `/flow-score-details` → `app/flow-score-details/page.tsx`
- `/win-details/[winId]` → `app/win-details/[winId]/page.tsx`
- `/progress` → `app/progress/page.tsx`
- `/todays-log-detail` → `app/todays-log-detail/page.tsx`
- `/calories-detail` → `app/calories-detail/page.tsx`
- `/sugar-intake-detail` → `app/sugar-intake-detail/page.tsx`
- `/micronutrient-tracker` → `app/micronutrient-tracker/page.tsx`

### Journal & Wins
- `/journal-flow` → `app/journal-flow/page.tsx`
- `/journal-history` → `app/journal-history/page.tsx`
- `/my-wins` → `app/my-wins/page.tsx`
- `/win-details/[winId]` → `app/win-details/[winId]/page.tsx`

### Quests & Achievements
- `/quests` → `app/quests/page.tsx`
- `/insights` → `app/insights/page.tsx`
- `/achievements` → `app/achievements/page.tsx`

### Library
- `/saved-meals` → `app/saved-meals/page.tsx`
- `/saved-supplements` → `app/saved-supplements/page.tsx`
- `/my-recipes` → `app/my-recipes/page.tsx`
- `/shared-recipes` → `app/shared-recipes/page.tsx`
- `/recipe-detail/[recipeId]` → `app/recipe-detail/[recipeId]/page.tsx`
- `/meal-detail/[mealId]` → `app/meal-detail/[mealId]/page.tsx`

### Profile & Settings
- `/profile-edit` → `app/profile-edit/page.tsx`
- `/personal-info-edit` → `app/personal-info-edit/page.tsx`
- `/physical-stats-edit` → `app/physical-stats-edit/page.tsx`
- `/preferences-edit` → `app/preferences-edit/page.tsx`
- `/subscription` → `app/subscription/page.tsx`
- `/help` → `app/help/page.tsx`
- `/permissions` → `app/permissions/page.tsx`
- `/debug` → `app/debug/page.tsx`

### Other
- `/voice-logging` → `app/voice-logging/page.tsx`
- `/voice-settings` → `app/voice-settings/page.tsx`
- `/behavioral-profile` → `app/behavioral-profile/page.tsx`
- `/smart-scheduling` → `app/smart-scheduling/page.tsx`
- `/user-search` → `app/user-search/page.tsx`
- `/notification-detail/[notificationId]` → `app/notification-detail/[notificationId]/page.tsx`
- `/stretching` → `app/stretching/page.tsx`
- `/wind-down-audio` → `app/wind-down-audio/page.tsx`
- `/menstrual-tracker` → `app/menstrual-tracker/page.tsx`
- `/scan-management` → `app/scan-management/page.tsx`

## Implementation Strategy

1. Create route structure first
2. Port components to match Android screens
3. Add proper navigation
4. Ensure all Firebase calls include platform tracking
