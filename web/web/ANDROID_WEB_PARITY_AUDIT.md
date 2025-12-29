# Android vs Web Parity Audit

## Android Routes (87 total)

### Authentication & Onboarding
- [x] splash
- [x] welcome  
- [x] auth (with returnTo parameter)
- [x] ftue
- [x] set_goals

### Main Screens
- [x] home (HomeScreenWithMindfulness)
- [x] daily_log
- [x] health_tracking
- [x] wellness
- [x] habits
- [x] my_habits

### Health Logging
- [x] meal_log
- [x] meal_detail
- [x] meal_recommendation
- [x] saved_meals
- [x] recipe_capture
- [x] supplement_photo_log
- [x] saved_supplements
- [x] water_log
- [x] weight_log
- [x] sleep_log
- [x] workout_log
- [x] mood_tracker
- [x] menstrual_tracker
- [x] voice_logging
- [x] voice_settings
- [x] micronutrients
- [x] sugar_intake_detail

### Habits
- [x] habit_creation (with habitId parameter)
- [x] habit_suggestions
- [x] habit_progress
- [x] smart_scheduling
- [x] habit_intelligence
- [x] habit_templates
- [x] habit_timer (with habitId, title, duration)
- [x] behavioral_profile

### Wellness & Mindfulness
- [x] meditation (with duration, habitId)
- [x] breathing_exercises
- [x] social_media_break
- [x] journal_flow
- [x] journal_history
- [x] my_wins
- [x] win_details/{win}
- [x] flow_score_details

### Community & Social
- [x] community
- [x] circle_create
- [x] circle_join
- [x] circle_detail/{circleId}
- [x] user_search_invite/{circleId}
- [x] user_search
- [x] friends_list
- [x] messaging (with optional userId)
- [x] forum_detail/{forumId}
- [x] shared_recipes
- [x] my_recipes
- [x] recipe_detail

### Profile & Settings
- [x] profile
- [x] profile_edit
- [x] personal_info_edit
- [x] physical_stats_edit
- [x] preferences_edit
- [x] settings
- [x] goals_edit
- [x] dietary_preferences_edit
- [x] subscription
- [x] permissions

### Analytics & Insights
- [x] calories_detail
- [x] streak_detail
- [x] goals_breakdown
- [x] achievements
- [x] quests
- [x] insights
- [x] weekly_blueprint

### AI & Chat
- [x] ai_chat

### Other
- [x] help
- [x] debug
- [x] notification_detail/{title}/{message}/{deepLink?}

## Missing Features to Port

### Critical Missing Features
1. **HomeScreenWithMindfulness** - The main home screen with Coachie Score prominently displayed
2. **Sugar Intake Detail** - Sugar tracking detail screen
3. **Dietary Preferences Edit** - Edit dietary preferences screen
4. **Notification Detail** - Handle notification deep links
5. **Recipe Detail** - View individual recipe details
6. **User Search Invite** - Invite users to circles
7. **Stretching Screen** - Special stretching habit timer variant

### UI/UX Parity Issues
1. **Home Screen Layout** - Must match Android exactly (Coachie Score card, quick stats, navigation)
2. **Navigation Structure** - Bottom nav vs side nav differences
3. **Color Themes** - Gender-based theming (male/female/other)
4. **Widget Parity** - Web dashboard should match Android widget layout

### Feature Completeness
1. **All navigation routes** - Every Android route must have web equivalent
2. **All screen functionality** - Every screen must work identically
3. **All data flows** - All data operations must match
4. **All UI components** - All UI elements must match

## Action Plan

1. ✅ Port DailyScoreCalculator
2. ✅ Add Coachie Score to Dashboard
3. ✅ Add three-dot menu
4. ⏳ Port HomeScreenWithMindfulness layout exactly
5. ⏳ Port all missing screens
6. ⏳ Ensure all navigation matches
7. ⏳ Test feature parity

