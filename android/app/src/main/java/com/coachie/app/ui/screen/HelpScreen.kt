package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coachie.app.ui.components.CoachieCard as Card
import com.coachie.app.ui.components.CoachieCardDefaults as CardDefaults
import com.coachie.app.ui.theme.rememberCoachieGradient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onBack: () -> Unit
) {
    val gradientBackground = rememberCoachieGradient(endY = 1600f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help & FAQ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.Black,
                    navigationIconContentColor = Color.Black
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBackground)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Text(
                    text = "🆘",
                    style = MaterialTheme.typography.displayLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Help & FAQ",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )

                // Getting Started
                HelpSection(
                    title = "🚀 Getting Started",
                    content = "Welcome to Coachie! Start by setting up your profile, goals, and tracking your first meal or workout. The AI coach will guide you through building healthy habits. Complete your behavioral profile to get personalized habit suggestions. Access Help & FAQ anytime from the 3-dot menu on the top right of your dashboard."
                )

                // Subscription Tiers & Features
                HelpSection(
                    title = "💎 Subscription Tiers & Features",
                    content = "Coachie offers two subscription tiers to meet your needs:\n\n" +
                            "🆓 FREE TIER:\n" +
                            "• All health tracking (meals, workouts, sleep, water, mood, weight)\n" +
                            "• Manual meal logging with barcode scanning and food database\n" +
                            "• Basic dashboard with charts and stats\n" +
                            "• Google Fit & Health Connect sync\n" +
                            "• Social features: Unlimited friends, messaging, forums\n" +
                            "• Circles: Join up to 3 circles\n" +
                            "• Habit tracking (manual creation and completion)\n" +
                            "• All data storage and sync\n" +
                            "• Voice logging\n" +
                            "• Basic analytics and progress tracking\n" +
                            "• Saved meals and recipes\n" +
                            "• External social media sharing (Instagram, Facebook, TikTok, etc.)\n\n" +
                            "AI Features (Limited):\n" +
                            "• AI Meal Inspiration: 1 per day\n" +
                            "• Daily Insights: 1 per day (includes Pro feature mentions)\n" +
                            "• AI Coach Chat: 10 messages per day\n" +
                            "• Habit Suggestions: 5 per week\n\n" +
                            "💎 PRO TIER ($9.99/month or $99/year):\n" +
                            "Everything in Free, PLUS:\n" +
                            "• Unlimited AI Meal Inspiration\n" +
                            "• Unlimited Daily Insights\n" +
                            "• Unlimited AI Coach Chat\n" +
                            "• Unlimited Habit Suggestions\n" +
                            "• AI-Generated Weekly Blueprint (unlimited meal plans)\n" +
                            "• Morning Briefs (unlimited personalized daily briefings)\n" +
                            "• Monthly Insights (advanced analytics)\n" +
                            "• AI Quest Generation (personalized challenges)\n" +
                            "• Unlimited Circles (join as many as you want)\n" +
                            "• Recipe Sharing (share with friends, post to forums, share to circles)\n" +
                            "• Priority support\n" +
                            "• Advanced analytics and insights\n\n" +
                            "Note: Most core features are available on the free tier. Pro tier unlocks unlimited AI features and premium tools to accelerate your health journey."
                )

                // Health Tracking Features
                HelpSection(
                    title = "📊 Health Tracking",
                    content = "• Meal Logging: Take photos of meals for AI-powered analysis\n• Recipe Analysis: Take photos or paste recipe text to get macro and micro nutrition estimates per serving - automatically calculates ingredient nutrition and scales to your serving size\n• Edit Meal Analysis: Review and adjust food names, calories, and macros before saving\n• Menu Item Detection: Automatically detects restaurant menu items and searches for official nutrition facts online\n• Sugar Estimation: Smart sugar estimation for foods when explicit sugar data isn't available - uses heuristics based on food categories (berries, fruits, etc.)\n• Saved Meals: Save frequently eaten meals for quick logging\n• My Recipes: Access all your saved recipes from the 'View All Recipes' button in the meal logging screen\n• Recipe Quick Select: Save analyzed recipes as single-serving meals for easy logging later\n• Recipe Sharing: Share your recipes and nutrition analysis with friends, post to forums, or share to recipe sharing circles\n• Recipe Sharing Options: Share recipes with friends, post to the Recipe Sharing forum channel, or create a dedicated recipe sharing circle\n• Social Media Sharing: Share your recipes to Instagram, Facebook, TikTok, X (Twitter), or other apps with beautiful promotional images featuring your meal photos and recipe cards\n• Recipe Card Sharing: Generate stunning recipe card images with full ingredients, instructions, and nutrition info to share on social media\n• Meal Photo Sharing: Share your meal photos alongside recipe cards in beautifully designed promotional posts\n• Save Recipes from Forum Posts: Save recipes posted by other users in forums to your personal recipe collection\n• Save Recipes from Circle Posts: Save recipes shared in your circles to your personal recipes\n• Recipe Detail View: View full recipe details including all ingredients, instructions, and complete nutrition breakdown\n• Recipe Capture: Capture recipes from photos, text, or manual entry\n• Save Recipes from Blueprint: Save any meal from your weekly blueprint as a recipe for later use\n• Post Recipes to Forum: Share your favorite recipes with the community in the Recipe Sharing forum channel\n• Shared Recipes: Browse recipes shared by the community in the Recipe Sharing forum\n• AI Meal Inspiration: Get personalized meal recommendations by selecting ingredients (proteins, vegetables, fruits, grains, healthy fats, pantry items) - AI creates recipes using only your selected ingredients\n• Meal Detail View: View comprehensive meal details including full nutrition breakdown, ingredients, and macros\n• Workout Tracking: Log exercises, duration, and calories burned\n• Sleep Logging: Track sleep duration and quality\n• Water Intake: Monitor daily hydration goals with glass-based tracking (1 glass = 8 oz)\n• Weight Tracking: Record weight with progress charts\n• Mood Tracking: Log mood, energy, stress levels with trends\n• Supplement Tracking: Track vitamins and supplements with photo capture\n• Saved Supplements: Save frequently taken supplements for quick logging\n• Vitamins & Minerals: Track micronutrient intake with smart over-limit warnings (only shows red for significant overages >150%)\n• Micronutrient Tracker: Comprehensive view of all vitamins and minerals with daily goals and progress\n• Sugar Intake Tracking: Monitor daily sugar and added sugar intake with detailed breakdowns\n• Calories Detail: Detailed view of daily calorie intake with breakdown by meals\n• Health Connect Integration: Automatic sync of steps, workouts, sleep, and calories from Health Connect (Android's unified health platform). Go to Settings > Permissions to connect.\n• Google Fit Sync: Connect your Google account in Settings > Permissions to automatically sync steps, workouts, and sleep from Google Fit. You'll need to sign in to your Google account and grant fitness permissions. Once connected, data syncs automatically and prevents duplicate workout entries.\n• Activity Recognition: Automatic activity detection for accurate workout and step tracking\n• Daily Log: Comprehensive view of all your daily health logs in one place\n• Health Tracking Dashboard: Centralized dashboard for all health metrics and tracking options"
                )

                // Habit & Behavior Features
                HelpSection(
                    title = "🎯 Habits & Behavior Change",
                    content = "• AI-Powered Suggestions: Get personalized habit recommendations based on your behavioral profile\n• Habit Templates: Quick-add popular habits including breathing exercises, social media breaks, reading, and wellness practices\n• Habit Intelligence: Advanced analytics showing patterns, timing, and success rates\n• Predictive Habits: AI predicts which habits you'll succeed at (70%+ success rate)\n• Automatic Habit Tracking: All habits are automatically tracked when you log related activities (meals complete 'eat breakfast', workouts complete 'gym session', water logs complete hydration habits, sleep logs complete bedtime habits, breathing exercises complete breathing habits)\n• Habit Timers: Built-in timers for time-based habits (reading, meditation, breathing exercises) with customizable durations\n• Habit Progress Tracking: Detailed progress view showing completion rates, streaks, and patterns over time\n• Smart Scheduling: AI-powered habit scheduling that optimizes timing based on your patterns and energy levels\n• Reading Habits: Track reading with duration options (30 minutes, 1 hour)\n• Social Media Breaks: Track social media breaks with duration options (30 minutes, 1 hour, 4 hours, 8 hours, all day) - longer breaks earn bonus Coachie points\n• Habit Creation: Create custom habits with personalized names, descriptions, and tracking options\n• Habit Editing: Edit existing habits including name, description, and tracking preferences\n• Circadian Optimization: Habits scheduled at optimal times based on your energy patterns\n• Environmental Adaptation: Habits adapt to weather, location, and context\n• Four Tendencies: Personalized approach based on Upholder, Questioner, Obliger, or Rebel personality\n• Behavioral Profile: Complete your behavioral profile to get personalized habit suggestions and recommendations\n• Habit Stacking: Build habits on top of existing routines\n• Streak Tracking: Visual progress with streak counters and longest streak records\n• Streak Details: Comprehensive streak history showing daily activity, log counts, and streak patterns\n• Wellness Habits: Track breathing exercises, social media breaks, meditation, and mindfulness practices\n• Habits Dashboard: Centralized dashboard for all your habits with progress overview"
                )

                // Wellness Features
                HelpSection(
                    title = "🧘 Wellness & Mindfulness",
                    content = "• Daily Meditation: AI-generated personalized meditation sessions with guided audio instructions\n• Meditation Sessions: Multiple durations (5, 10, 15, 20 minutes) and categories (Guided, Silent, Mindfulness, Body Scan) with appropriate content for each\n• Meditation Variety: Each session is unique - AI prevents repetitive lines and ensures productive, non-repetitive meditation scripts\n• Breathing Exercises: Guided breathing exercises including Quick Calm (1 min), Gentle Breathing (3 min), Deep Focus (5 min), and Box Breathing (4-4-4-4 technique)\n• Body Scan: Progressive body scan meditation from head to toe for deep relaxation and body awareness\n• Grounding Exercise: 5-4-3-2-1 grounding technique to anchor yourself in the present moment (5 things you see, 4 you touch, 3 you hear, 2 you smell, 1 you taste)\n• Stretching Exercises: Guided stretching routines with timer and instructions for flexibility and recovery\n• Wind Down Audio: Calming audio tracks including body scan meditation, gratitude practice, and peaceful sleep stories\n• Social Media Break: Take intentional breaks from social media to reduce stress and improve focus with duration options (30 min, 1 hour, 4 hours, 8 hours, all day)\n• Journaling: Time-appropriate journal prompts (morning/afternoon/evening) with save confirmation\n• Journal History: Revisit past journal entries and reflections with full history view\n• My Wins: Automatic achievement tracking - Coachie analyzes your daily activities (steps, workouts, water, macros, habits, streaks) and generates wins automatically without requiring journal entries\n• Win of the Day: Daily achievement highlights based on your actual accomplishments with detailed win breakdowns\n• Win Details: View comprehensive details of each win including what triggered it and your progress\n• Mood Charts: Visualize mood trends over time with energy and stress overlays\n• Meditation Charts: Track meditation duration, count, and mood improvement\n• Journal Charts: Monitor word count, completion status, and entry frequency\n• Today's Focus: Personalized daily tasks organized by time of day (morning, afternoon, evening) with automatic water reminder at the end\n• Today's Reset: Daily mindful sessions for stress relief and mental clarity\n• Voice Logging: Log meals, activities, complete habits, and create journal entries using voice commands\n• Voice Settings: Customize voice logging preferences including text-to-speech settings and voice coaching options"
                )

                // Community Features
                HelpSection(
                    title = "👥 Community & Accountability",
                    content = "• Circles: Join groups of 2-5 people with shared goals and daily check-ins\n• Circle Posts: Share updates, photos, and achievements with your circle\n• Like & Comment: Engage with circle posts by liking and commenting to show support\n• Circle Invites: Invite friends to join your circles directly from the circle detail screen - they'll receive a friend request notification with a circle invitation badge\n• Circle Invite Notifications: Pending circle invites appear as friend requests in the Friends screen (Requests tab) with a clear 'Circle Invitation' indicator\n• Automatic Circle Streaks: Circle streaks automatically update based on all members' daily activity (habit completions, health logs, check-ins) - celebrating 7-day streaks together\n• Circle Interaction Bonus: Interacting with your circles (posting, commenting, liking) earns you Flow Score points for wellness\n• Friends: Add friends, send and accept friend requests, and build your support network\n• Messaging: Direct messaging with friends for accountability and support (send button appears in message field when typing)\n• Forum Posts: Participate in community discussions and support forums\n• Forum Post Management: Delete your own forum posts using the 3-dot menu on your posts (only original posters can delete)\n• Upvote Forum Posts: Upvote posts you find helpful or want to see more of - posts are sorted by upvotes by default to show what users want most\n• Forum Sorting: Toggle between 'Top' (sorted by upvotes) and 'New' (sorted by date) to view posts\n• Coachie News Channel: Stay updated with app updates, new feature announcements, roadmaps, and development updates\n• AI Pact Maker: Automatically matched with others who missed the same habit for 7-day accountability pacts\n• Buddy System: 1-on-1 accountability pairs with shared progress tracking\n• Win Feed: Celebrate wins together with emoji reactions and encouragement\n• Weekly Huddles: AI-hosted 15-minute voice check-ins with your circle\n• Challenges: Join circle challenges with entry fees going to charity\n• Vent Tab: Anonymous support space for sharing struggles\n• Graduation Wall: Celebrate major achievements with your circle\n• Push Notifications: Get notified when your circle members check in or complete habits\n• Auto-Update: Circles automatically refresh every 30 seconds to show the latest activity"
                )

                // AI Coaching Features
                HelpSection(
                    title = "🤖 AI Coaching & Insights",
                    content = "• Smart Coach: Personalized daily insights based on your health data\n• AI Chat: Interactive AI coach chat for questions, advice, and personalized guidance\n• Morning Briefing: Daily summary with habits, circle check-ins, and AI insights (uses your preferred unit system - Imperial or Metric)\n• Afternoon Briefing: Mid-day check-in with progress updates and reminders (uses your preferred unit system)\n• Evening Briefing: End-of-day summary with accomplishments and recommendations (uses your preferred unit system)\n• Brief Accuracy: Briefs use accurate user data including correct protein goals, water intake in glasses, and actual macro targets\n• First-Time User Brief: Welcome message for new users with guidance on habits, weekly blueprint, AI meal inspiration, and Help & FAQ access\n• Unit System Support: All briefs and blueprints automatically use your preferred unit system (Imperial: lbs, oz, fl oz OR Metric: g, kg, ml)\n• Quests: AI-suggested quests based on your goals and data with progress tracking\n• Insights: Monthly AI-generated insights (3-5 per month) with charts and actionable recommendations\n• Mood-Based Recommendations: AI suggests activities based on your mood patterns\n• Pattern Recognition: Identifies correlations between sleep, exercise, mood, and nutrition\n• Predictive Analytics: Forecasts habit success and suggests optimizations\n• Adaptive Learning: AI learns your preferences and adjusts recommendations\n• Voice Coaching: Enable text-to-speech for spoken encouragement and tips\n• AI Meal Inspiration: Get personalized meal recommendations by selecting ingredients - AI creates recipes using only your selected ingredients that fit your macros and dietary preferences"
                )

                // Weekly Blueprint Features
                HelpSection(
                    title = "🛒 Weekly Blueprint",
                    content = "• AI-Generated Meal Plans: Get personalized 7-day meal plans with recipes based on your dietary preferences, macros, allergies, and goals\n• Shopping Lists: Automatically generated shopping lists organized by category (Produce, Proteins, Dairy, etc.) with quantities and estimated costs (limited to ~25 items for efficiency)\n• Meal Preferences: Set your preferred number of meals per day (2, 3, or 4) and snacks per day (0, 1, or 2) in Settings\n• Serving Size Adjustment: Adjust servings (1-8 people) to scale recipes and shopping list quantities for individuals or families\n• Unit System Support: All measurements automatically use your preferred unit system (Imperial: lbs, oz, fl oz, cups, tbsp, tsp OR Metric: g, kg, ml, L)\n• Save Recipes from Blueprint: Save any meal from your weekly blueprint as a recipe for later use or sharing\n• Share Recipes from Blueprint: Share recipes from your blueprint with friends or post them to the Recipe Sharing forum\n• Meal Time Reminders: Configure breakfast, lunch, dinner, and snack times to receive push notifications before meals\n• Interactive Shopping List: Mark items as bought, edit quantities, add notes, and regenerate your blueprint anytime\n• Strict Dietary Adherence: Blueprints strictly enforce your dietary preferences (vegetarians get no meat, carnivores get only meat, etc.) - both macro ratios AND food types\n• Macro Goal Alignment: Blueprints are designed to hit your daily macro targets (protein, carbs, fat) across all meals and snacks\n• Meal Variety: Each week generates new, diverse meals with cuisine rotation, varied cooking methods, and unique recipes to prevent repetition (can repeat up to 20% of meals from previous weeks)\n• Budget-Aware: Meal plans consider your budget level and household size\n• Weekly Notifications: Get notified every Sunday when your new weekly blueprint is ready\n• Export & Share: Export shopping lists as PNG images or PDF documents for easy sharing\n• Access from Dashboard: View your weekly blueprint card on the LifeOS Dashboard or navigate directly to the full shopping list"
                )

                // Charts & Analytics
                HelpSection(
                    title = "📈 Charts & Analytics",
                    content = "• Health Charts: Line graphs for calories, water, sleep, and weight with swipe navigation (easier to spot trends)\n• Macro Pie Charts: Visual breakdown of protein, carbs, and fat by calories (daily and weekly views)\n• Wellness Charts: Mood trends, meditation sessions, and journal entries\n• Coachie Flow Score: Comprehensive wellness score (0-100) calculated from nutrition, fitness, sleep, water, habits, wellness activities, and circle interactions\n• Flow Score Details: Detailed breakdown of your score with progress graphs showing trends over 7 days, monthly, and quarterly periods\n• Flow Score Components: Health score (nutrition, fitness, sleep, water), Wellness score (habits, mindfulness, circle interactions), and Habits score (completion rates, streaks)\n• Trend Analysis: View progress over days, weeks, months, or years\n• Weekly Completion Trends: Track habit completion rates by week with accurate current dates\n• Success Rates: Track habit completion rates and patterns\n• Performance Insights: Understand what works best for you\n• Goals Breakdown: Detailed view of all your goals with progress tracking\n• Achievements: View all your achievements and milestones\n• Progress Screen: Comprehensive progress overview with charts and insights\n• Streak Details: Detailed streak history with daily log counts and patterns"
                )

                // Frequently Asked Questions
                Text(
                    text = "❓ Frequently Asked Questions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                FAQItem(
                    question = "How does the AI meal analysis work?",
                    answer = "Take a photo of your meal and Coachie uses AI to analyze ingredients, estimate calories, macronutrients, and provide nutritional insights. The analysis screen opens in edit mode so you can review and adjust the food name, calories, protein, carbs, and fat before saving. You can save meals for quick logging later."
                )

                FAQItem(
                    question = "Can I edit the meal analysis before saving?",
                    answer = "Yes! When you take a photo of a meal, the analysis result screen opens in edit mode by default. You can adjust the food name, calories, protein, carbs, and fat values before submitting. Click the check button to save your edits, or click 'Submit Meal' to log the meal with your adjusted values. All edited values are used when saving the meal."
                )

                FAQItem(
                    question = "How does menu item detection work?",
                    answer = "When you take a photo of a restaurant menu or menu item, Coachie automatically detects if it's a menu item. It extracts the restaurant name, menu item name, and description, then searches online for the official nutrition facts. If found, it uses the accurate nutrition data instead of estimates. If not found, it falls back to the estimated nutrition from the photo analysis."
                )

                FAQItem(
                    question = "What is the Habit & Behavior Change Engine?",
                    answer = "An advanced AI system that learns your patterns, predicts successful habits (70%+ success rate), optimizes timing based on circadian rhythms, and adapts to environmental factors. It uses your Four Tendencies personality type to personalize recommendations."
                )

                FAQItem(
                    question = "How do AI-powered habit suggestions work?",
                    answer = "Coachie analyzes your behavioral profile, existing habits, success patterns, and goals to suggest 3-5 personalized habits you haven't tried yet. Suggestions include confidence scores, difficulty levels, and rationale for why they fit your patterns."
                )

                FAQItem(
                    question = "What is Habit Intelligence?",
                    answer = "Habit Intelligence analyzes your habit patterns to identify consistency, optimal timing, weekday/weekend differences, and habit sequences. It provides adaptive suggestions for difficulty adjustments, timing optimization, and habit stacking."
                )

                FAQItem(
                    question = "How does Google Fit integration work?",
                    answer = "Coachie automatically syncs your steps, calories burned, sleep duration, and workouts from Google Fit. To connect Google Fit, go to Settings > Permissions and tap 'Connect' on the Google Fit section. You'll be asked to sign in to your Google account (if not already signed in) and grant fitness permissions. Once connected, data syncs automatically throughout the day. This data appears in your daily log and contributes to your health score. The sync prevents duplicate workout entries by using stable identifiers for each workout. You can enable/disable sync in settings."
                )

                FAQItem(
                    question = "What is My Wins?",
                    answer = "My Wins automatically tracks your achievements by analyzing your daily activities. Coachie generates wins based on your actual accomplishments like 'Most Steps Ever', 'Hit Step Goal', 'Perfect Macro Day', 'Completed Habit', 'Longest Streak', and more. Wins are created automatically from your logged activities (steps, workouts, water, macros, habits) - you don't need to journal to get wins. The system also extracts achievements from journal entries if you choose to journal."
                )

                FAQItem(
                    question = "How do Circles work?",
                    answer = "Circles are groups of 2-5 people with shared goals. Members check in daily with energy levels and optional notes. You get push notifications when others check in. Circles have a streak counter and support each other's progress."
                )

                FAQItem(
                    question = "What are AI Pacts?",
                    answer = "If you miss the same habit for 2+ consecutive days, Coachie automatically matches you with others in the same situation. If both accept, a 7-day accountability pact is created. You'll get daily check-ins and celebrate completion together."
                )

                FAQItem(
                    question = "How does the Buddy System work?",
                    answer = "Find a 1-on-1 accountability partner with similar goals. AI matches you based on goal, tendency, streak, and timezone. You can see each other's habit completions, send voice notes (Pro feature), and celebrate wins together."
                )

                FAQItem(
                    question = "What are Circle Challenges?",
                    answer = "Pre-built challenge templates like '10K steps/day for 7 days' or 'No sugar after 8 PM'. Members pay an entry fee (donated to charity), submit daily proof, and compete on a leaderboard. Winners get badges and recognition."
                )

                FAQItem(
                    question = "What is the Vent Tab?",
                    answer = "A safe, anonymous space within your circle to share struggles without judgment. Others can reply with empathy. Coachie provides AI-generated supportive responses. All content is moderated for safety."
                )

                FAQItem(
                    question = "What is the Graduation Wall?",
                    answer = "A private circle-only celebration space. When someone achieves a major goal, circle members vote to post it. The wall shows photos, stories, and achievements. Pro members can download graduation certificates."
                )

                FAQItem(
                    question = "How do Weekly Huddles work?",
                    answer = "AI-hosted 15-minute voice check-ins scheduled weekly. Coachie facilitates the conversation, members raise hands to speak, and the session is summarized. Pro members can access recordings and transcripts."
                )

                FAQItem(
                    question = "How does mood tracking help?",
                    answer = "Mood data helps AI personalize meditation sessions, journal prompts, and activity suggestions. It identifies patterns (e.g., mood improves after exercise) and provides insights. Mood contributes to your wellness score."
                )

                FAQItem(
                    question = "What is the Morning Briefing?",
                    answer = "A daily summary showing your habits, circle check-ins, health metrics, and AI insights. It includes mood tracking insights, suggestions for improvement if needed, encouragement if your mood is good, and reminders to track mood if you haven't recently. It helps you start your day with clarity and focus. You can check in with your circle directly from the briefing."
                )

                FAQItem(
                    question = "Can I track multiple goals?",
                    answer = "Yes! Set goals for weight, water intake, macronutrients, steps, and build unlimited custom habits. Coachie adapts to all your health objectives and provides personalized coaching for each."
                )

                FAQItem(
                    question = "How does voice logging work?",
                    answer = "Use the floating microphone button to log meals, workouts, activities, complete habits, or create journal entries by voice. Coachie transcribes and parses your commands automatically. For habits, say 'complete [habit name]' or 'done with [habit name]' and it will find and complete the matching habit. For journaling, say 'journal about [topic]' or 'write about [topic]' to create a journal entry. Enable voice responses in settings to hear spoken coaching tips."
                )

                FAQItem(
                    question = "What are Pro features?",
                    answer = "Pro features include: GIF reactions on wins, pinning wins to top, voice notes with buddies, huddle recordings/transcripts, PDF graduation certificates, and custom challenge creation. Upgrade in settings."
                )

                FAQItem(
                    question = "Is my data private?",
                    answer = "Absolutely. All health data is encrypted and stored securely in Firebase. Coachie never shares your personal information with third parties. Circle data is only visible to circle members. Anonymous vents protect your identity."
                )

                FAQItem(
                    question = "What are Breathing Exercises?",
                    answer = "Coachie offers guided breathing exercises including Quick Calm (1 minute), Gentle Breathing (3 minutes), Deep Focus (5 minutes), and Box Breathing (classic 4-4-4-4 technique). These exercises help reduce stress, improve focus, and regulate your nervous system. Access them from the Wellness dashboard. You can also add breathing exercises as habits to track your practice daily. When you select a breathing exercise from Today's Focus, it takes you directly to the breathing exercises screen."
                )

                FAQItem(
                    question = "Can I track breathing exercises and social media breaks as habits?",
                    answer = "Yes! Breathing exercises and social media breaks are available as habit suggestions and templates. Add 'Daily breathing exercise', 'Morning breathing routine', 'Social media break', or 'No social media before bed' from the habit suggestions or templates screen to track these wellness practices consistently."
                )

                FAQItem(
                    question = "What is Social Media Break?",
                    answer = "A wellness feature that helps you take intentional breaks from social media. Set a duration and Coachie will guide you through the break, helping reduce stress, improve focus, and create healthier digital habits. Track your breaks as habits for accountability."
                )

                FAQItem(
                    question = "How do I invite friends to my circle?",
                    answer = "Open your circle detail screen and tap the group icon in the top bar. This opens the user search where you can find friends and invite them to join your circle. They'll receive a notification and can accept the invitation. Once in a circle, you can create posts, like and comment on others' posts to show support and engagement."
                )

                FAQItem(
                    question = "How does messaging work?",
                    answer = "You can message any of your friends directly from the Friends screen. Messages are real-time and support conversations with multiple friends. The send button appears inside the message field when you start typing, making it easy to send messages. You'll receive notifications when you get new messages. If you encounter permission errors, make sure you're both friends and check your account permissions."
                )

                FAQItem(
                    question = "What are Quests?",
                    answer = "Quests are AI-suggested challenges based on your goals and health data. Complete quests to earn rewards and track your progress. Quests adapt to your current habits and help you achieve your health objectives."
                )

                FAQItem(
                    question = "What are Insights?",
                    answer = "Insights are monthly AI-generated summaries (3-5 per month) that analyze your health patterns, habits, and progress. Each insight includes charts, trends, and actionable recommendations to help you optimize your health journey."
                )

                FAQItem(
                    question = "How do I like or comment on circle posts?",
                    answer = "In any circle, you can like posts by tapping the heart icon and comment by tapping the comment icon. Comments appear below the post, and you can see a preview of the first 2 comments directly on the post card. Tap the comment icon to view all comments and add your own. All circle members can like and comment on posts to show support."
                )

                FAQItem(
                    question = "Why are my charts showing line graphs instead of bars?",
                    answer = "Health tracking charts (calories, water, sleep, weight) now use line graphs to make it easier to spot trends over time. Line graphs connect data points, making it easier to see if your metrics are improving, declining, or staying stable. Macro charts remain as pie charts since they show proportions rather than trends over time."
                )

                FAQItem(
                    question = "Why is my magnesium amount showing in red?",
                    answer = "Magnesium (and other nutrients) only show in red when you significantly exceed the maximum recommended amount (more than 150% of the max). Moderate overages display in normal color. For example, if your max is 500mg and you have 740mg, it will display normally. Only amounts over 750mg would show in red. This prevents unnecessary alarm for slight overages while still alerting you to significant excesses."
                )

                FAQItem(
                    question = "Why are my weekly habit trends showing old dates?",
                    answer = "Weekly completion trends now correctly display the most recent 8 weeks with accurate dates. The dates are sorted chronologically, showing the current week at the bottom and older weeks going back in time. If you see incorrect dates, try refreshing the habit progress screen."
                )

                FAQItem(
                    question = "How do I save meals for quick logging?",
                    answer = "After logging a meal with AI analysis, you can save it to your Saved Meals collection. Access saved meals from the meal logging screen to quickly log frequently eaten meals without re-analyzing."
                )

                FAQItem(
                    question = "How does recipe analysis work?",
                    answer = "From the meal logging screen, tap 'Analyze Recipe' to access the recipe analysis feature. You can take a photo of a recipe, upload a recipe image, or paste recipe text. Enter the number of servings the recipe makes, and Coachie will analyze all ingredients, calculate macros and micronutrients for each ingredient, then provide total nutrition for the entire recipe and per-serving nutrition. You can review ingredients, instructions, and nutrition breakdown before saving to quick select or sharing with friends."
                )

                FAQItem(
                    question = "Can I analyze recipes from photos?",
                    answer = "Yes! You can take a photo of a recipe card, cookbook page, or recipe screenshot. The AI will extract all ingredients with quantities, calculate nutrition for each ingredient, and provide full nutrition breakdown. You can also paste recipe text directly if you prefer typing."
                )

                FAQItem(
                    question = "How does recipe quick select work?",
                    answer = "After analyzing a recipe, tap 'Save to Quick Select' to save it as a single-serving meal in your Saved Meals. The recipe's nutrition is automatically divided by the number of servings, so when you log it later, you're logging one serving. This makes it easy to track meals from your favorite recipes without re-analyzing each time."
                )

                FAQItem(
                    question = "Can I share recipes with friends?",
                    answer = "Yes! After analyzing a recipe, tap 'Share' to open the sharing dialog. Select which friends you want to share the recipe with, and they'll be able to access your recipe with full ingredients, instructions, and nutrition analysis. Shared recipes include all the nutrition data so your friends can easily track the macros and micros."
                )

                FAQItem(
                    question = "What nutrition information does recipe analysis provide?",
                    answer = "Recipe analysis calculates total calories, protein, carbs, fat, sugar, and added sugar for the entire recipe and per serving. It also estimates key micronutrients including vitamins (A, C, D, E, K, B-complex), minerals (calcium, iron, magnesium, zinc, etc.), and other essential nutrients. The per-serving values are automatically calculated based on the number of servings you specify."
                )

                FAQItem(
                    question = "How do I get the most out of Coachie?",
                    answer = "1) Complete your behavioral profile for personalized suggestions, 2) Log consistently to build accurate patterns, 3) Join a circle for accountability, 4) Engage with circle posts by liking and commenting, 5) Participate in forums and upvote helpful posts to help surface the best content, 6) Review your charts weekly to see trends (line graphs make it easy to spot patterns), 7) Use journaling to extract wins, 8) Try breathing exercises and social media breaks for stress relief, 9) Add friends and use messaging for support, 10) Complete quests and review insights, 11) Enable notifications for reminders and circle updates, 12) Track your vitamins and minerals to ensure you're meeting your daily goals."
                )

                FAQItem(
                    question = "How do upvotes work in forums?",
                    answer = "Upvoting forum posts helps surface the most valuable content. Tap the up arrow icon on any forum post to upvote it. Posts are automatically sorted by upvotes (most upvoted first) so you can easily see what the community finds most helpful. You can toggle between 'Top' (sorted by upvotes) and 'New' (sorted by date) using the switch in the forum header. Upvoting is separate from liking - upvotes help determine post visibility, while likes show appreciation."
                )

                FAQItem(
                    question = "What is Weekly Blueprint?",
                    answer = "Weekly Blueprint is an AI-powered meal planning feature that generates personalized 7-day meal plans with full recipes and automatically creates organized shopping lists. It considers your dietary preferences (vegan, keto, paleo, etc.), macro goals, allergies, budget, and meal preferences. All recipes are for 4 servings by default, and you can adjust the serving size in the UI. Access it from the LifeOS Dashboard card or navigate directly to the Weekly Blueprint screen."
                )

                FAQItem(
                    question = "How do I generate my weekly blueprint?",
                    answer = "Go to the LifeOS Dashboard and tap the 'Weekly Blueprint' card. If you don't have a blueprint yet, tap 'Generate my blueprint?' and Coachie will create a personalized 7-day meal plan with recipes and shopping list based on your profile settings. The generation takes about 30-60 seconds. You'll see a summary with item count and estimated cost, then tap 'View Full List' to see the detailed shopping list organized by category."
                )

                FAQItem(
                    question = "How do I customize my meal preferences?",
                    answer = "Go to Settings and scroll to the 'Weekly Blueprint' section. You can set your preferred number of meals per day (2, 3, or 4) and snacks per day (0, 1, or 2). These preferences are used when generating your weekly blueprint. You can also enable 'Weekly Blueprint Sunday Alert' to get notified when your new blueprint is ready each week."
                )

                FAQItem(
                    question = "How do meal time reminders work?",
                    answer = "In Settings, enable 'Daily Meal Reminders' and then set your preferred meal times (breakfast, lunch, dinner, and snacks if applicable). Coachie will send you push notifications before each meal time to remind you to log your meals. Meal reminders only appear if you have snacks enabled and set snack times. You can customize each meal time individually."
                )

                FAQItem(
                    question = "Can I edit my shopping list?",
                    answer = "Yes! In the Weekly Blueprint screen, you can mark items as bought by checking the checkbox, edit quantities by tapping on items, add notes for specific items, and regenerate your blueprint if you want a new plan. All changes are saved automatically. You can also share your shopping list as a PNG image or export it as a PDF."
                )

                FAQItem(
                    question = "How does the blueprint respect my dietary preferences?",
                    answer = "The Weekly Blueprint uses your dietary preference setting (vegan, vegetarian, keto, paleo, etc.), macro goals (protein, carbs, fat), allergies, favorite foods, and avoided foods from your profile. It also considers your budget level and cooking time preferences. All recipes are generated for 4 servings by default, and you can adjust the serving size in the UI to scale ingredients and macros. The AI generates meals and recipes that align with all these constraints."
                )

                FAQItem(
                    question = "When will I get my weekly blueprint notification?",
                    answer = "If you enable 'Weekly Blueprint Sunday Alert' in Settings, you'll receive a push notification every Sunday at 7:00 AM ET when your new weekly blueprint is ready. You can also manually generate a new blueprint anytime from the Weekly Blueprint screen by tapping 'Regenerate'."
                )

                FAQItem(
                    question = "What if I change my meal preferences after generating a blueprint?",
                    answer = "If you change your meals per day or snacks per day in Settings, the Weekly Blueprint screen will detect this change and prompt you to regenerate your blueprint. This ensures your meal plan always matches your current preferences. You can also manually regenerate anytime."
                )

                FAQItem(
                    question = "How does serving size adjustment work for blueprints?",
                    answer = "In the Weekly Blueprint screen, you can adjust the serving size (1-8 people) using the selector. This automatically scales all recipe ingredient quantities and shopping list amounts proportionally. For example, if a recipe calls for '2 cups rice' for 2 servings, selecting 4 servings will update it to '4 cups rice' and adjust the shopping list accordingly. Both recipes and shopping lists update together based on your selected serving size."
                )

                FAQItem(
                    question = "How does the unit system work in blueprints and briefs?",
                    answer = "Coachie automatically uses your preferred unit system throughout the app. If you select Imperial in Settings, all measurements in weekly blueprints, morning/afternoon/evening briefs, and AI meal inspiration will use Imperial units (lbs, oz, fl oz, cups, tbsp, tsp, quarts, inches, feet). If you select Metric, all measurements use Metric units (g, kg, ml, L, liters, cm, centimeters). The system strictly enforces your preference - if Imperial is selected, you'll never see metric units, and vice versa."
                )

                FAQItem(
                    question = "Do habits automatically complete when I log activities?",
                    answer = "Yes! All habits are automatically tracked when you log related activities. For example, logging a meal automatically completes 'Eat breakfast' or 'Eat lunch' habits. Logging water completes hydration habits (including the 'drink 8 glasses of water' task which auto-completes when you've logged 8 glasses). Logging sleep completes bedtime habits. Logging workouts completes exercise habits. Logging breathing exercises completes breathing habits. This prevents duplicate tracking and makes habit completion seamless. Automatic completion only happens once per day to prevent duplicates."
                )

                FAQItem(
                    question = "How do circle streaks work?",
                    answer = "Circle streaks automatically update based on all members' daily activity. When any circle member completes a habit, logs a health entry, or checks in, the circle's streak is recalculated. A 7-day streak means at least one member had activity each day for 7 consecutive days. Circle streaks celebrate collective progress and help maintain accountability across all members. Streaks are updated automatically in real-time as members participate."
                )

                FAQItem(
                    question = "How does dietary preference enforcement work in blueprints?",
                    answer = "Weekly Blueprints strictly enforce your dietary preference at both the macro level AND food type level. For example, if you're vegetarian, the blueprint will never include meat, fish, or seafood - it considers both macro ratios AND actual food types. If you're a carnivore, you'll only get meat-based meals. The AI validates generated meals to ensure they comply with your dietary restrictions and regenerates if violations are found."
                )

                FAQItem(
                    question = "Why do I see the same meals each week in my blueprint?",
                    answer = "Weekly Blueprints now use enhanced randomization and variety algorithms. Each week, the AI generates new, diverse meals with different cuisines, cooking methods, proteins, and vegetables. The system can repeat up to 20% of meals from previous weeks, but 80% will be new and different. If you're seeing repetition, try regenerating your blueprint or adjusting your dietary preferences. The system also rotates through different meal types and ensures unique meal names each week."
                )

                FAQItem(
                    question = "How do I save a recipe from my weekly blueprint?",
                    answer = "In the Weekly Blueprint screen, tap on any meal card to view its details. You'll see options to 'Save Recipe' or 'Share Recipe'. Tap 'Save Recipe' to save it to your recipe collection for later use. You can also share recipes with friends or post them to the Recipe Sharing forum channel."
                )

                FAQItem(
                    question = "Can I post recipes to the forum?",
                    answer = "Yes! After saving or analyzing a recipe, you can post it to the Recipe Sharing forum channel. This allows you to share your favorite recipes with the entire community. Other users can view your recipes, save them to their personal recipe collection, and try your recipes. Recipes posted to forums are publicly viewable by all users."
                )

                FAQItem(
                    question = "How do I access my saved recipes?",
                    answer = "Tap the 'View All Recipes' button in the meal logging screen to see all your saved recipes. You can also access recipes from the menu in meal detail screens. From your recipes, you can edit, share, or delete them."
                )

                FAQItem(
                    question = "Can I save recipes from forum or circle posts?",
                    answer = "Yes! When viewing a recipe in a forum post or circle post, you'll see a 'Save Recipe' button. Tap it to save the recipe to your personal recipe collection. You can then access it anytime from 'View All Recipes' in the meal logging screen."
                )

                FAQItem(
                    question = "How do circle invites work?",
                    answer = "When you invite someone to your circle, they receive a friend request notification with a circle invitation. The invite appears in their Friends screen (Requests tab) with a clear 'Circle Invitation' indicator showing the circle name. When they accept the friend request, they automatically join the circle and become your friend. You'll see a notification badge on the Friends icon in the Community screen when you have pending requests."
                )

                FAQItem(
                    question = "Can I delete my forum posts?",
                    answer = "Yes! Only the original poster can delete their own forum posts. Tap the 3-dot menu button in the top-right corner of your post to see the delete option. You'll be asked to confirm before the post is permanently deleted. Deleting a post also updates the forum's post count."
                )

                FAQItem(
                    question = "What is the Coachie News channel?",
                    answer = "The Coachie News channel is a forum dedicated to app updates, new feature announcements, roadmaps, and development updates from the Coachie team. It's pinned at the top of the forums list so you can stay informed about the latest features and improvements."
                )

                FAQItem(
                    question = "How do I get Flow Score points for circle interactions?",
                    answer = "Interacting with your circles (creating posts, commenting, liking posts) earns you Flow Score points. These interactions contribute to your wellness score, encouraging community engagement and accountability."
                )

                FAQItem(
                    question = "How does water intake tracking work?",
                    answer = "Water intake is tracked in milliliters (ml) internally, but displayed in your preferred unit system. If you use Imperial units, water is measured in glasses (1 glass = 8 oz) in briefs and rounded to the nearest whole number. The 'drink 8 glasses of water' task automatically completes when you've logged 8 glasses (1920ml) and always appears last in Today's Focus."
                )

                FAQItem(
                    question = "What is Today's Focus?",
                    answer = "Today's Focus shows your personalized daily tasks organized by time of day (morning, afternoon, evening). It includes your habits, wellness tasks, and health tasks. The 'drink 8 glasses of water' task always appears last and auto-completes when you've logged 8 glasses. Tasks are arranged chronologically to help you plan your day."
                )

                FAQItem(
                    question = "What is the Coachie Flow Score?",
                    answer = "The Coachie Flow Score is a comprehensive wellness score (0-100) that combines your nutrition, fitness, sleep, water intake, habits, and wellness activities into a single metric. View detailed breakdowns and progress graphs showing trends over 7 days, monthly, and quarterly periods in the Flow Score Details screen."
                )

                FAQItem(
                    question = "How do notifications work?",
                    answer = "Notifications act like deep links - tapping them takes you directly to the relevant screen. For example, a nudge to reflect on your day takes you to your journal, habit reminders take you to habits, meal reminders take you to meal logging, and water reminders take you to water logging. This makes it easy to act on notifications immediately."
                )

                FAQItem(
                    question = "How do I clear my data or delete my account?",
                    answer = "Go to Settings and scroll to the 'Account Management' section. You can 'Clear All Data' to remove all your logged data, habits, and progress while keeping your account active. Or 'Delete Account' to permanently delete your account and all associated data. Both actions require confirmation and cannot be undone. Account deletion will also remove your Firebase authentication."
                )

                FAQItem(
                    question = "What dietary preferences are available?",
                    answer = "All 13 dietary preferences are available during first-time setup (FTUE) and in settings: Balanced, Vegetarian, Vegan, Keto, Paleo, Mediterranean, Low-Carb, High-Protein, Pescatarian, Carnivore, Gluten-Free, Dairy-Free, and Moderate Low-Carb. You can select your preference in a 2-column grid during setup or change it anytime in Settings."
                )

                FAQItem(
                    question = "How do reading and social media break durations work?",
                    answer = "Reading habits support 30-minute and 1-hour duration options. Social media breaks offer 30 minutes, 1 hour, 4 hours, 8 hours, and all day options. Longer social media breaks earn bonus Coachie points to reward extended breaks from social media."
                )

                FAQItem(
                    question = "What is AI Meal Inspiration?",
                    answer = "AI Meal Inspiration lets you select specific ingredients (proteins, vegetables, fruits, grains, healthy fats, pantry items) and get personalized meal recommendations. The AI creates recipes using ONLY your selected ingredients, ensuring you can make meals with what you have available. Recipes are designed to fit your macro goals and dietary preferences."
                )

                FAQItem(
                    question = "What are grounding exercises?",
                    answer = "Grounding exercises use the 5-4-3-2-1 technique to help you anchor yourself in the present moment. You identify 5 things you can see, 4 things you can touch, 3 things you can hear, 2 things you can smell, and 1 thing you can taste. This technique helps reduce anxiety and stress by bringing your attention to the present."
                )

                FAQItem(
                    question = "What is body scan meditation?",
                    answer = "Body scan meditation is a mindfulness practice where you systematically bring attention to different parts of your body, from head to toe. It helps promote relaxation, body awareness, and stress relief. Coachie offers guided body scan meditations in various durations."
                )

                FAQItem(
                    question = "What are stretching exercises?",
                    answer = "Coachie offers guided stretching routines with timers and step-by-step instructions. These exercises help improve flexibility, reduce muscle tension, and aid in recovery. Stretching exercises can be added as habits and tracked for consistency."
                )

                FAQItem(
                    question = "What is wind down audio?",
                    answer = "Wind down audio includes calming tracks like body scan meditation, gratitude practice, and peaceful sleep stories. These are designed to help you relax before bed and improve sleep quality. Access wind down audio from the wellness dashboard."
                )

                FAQItem(
                    question = "How does Health Connect integration work?",
                    answer = "Health Connect is Android's unified health platform. Coachie can sync steps, workouts, sleep, and calories from Health Connect if you grant permissions. This provides more accurate and comprehensive health data. Health Connect permissions must be granted through system settings. Coachie also supports Google Fit as a fallback option."
                )

                FAQItem(
                    question = "What permissions does Coachie need?",
                    answer = "Coachie may request: Camera (for meal photos), Health Connect/Google Fit (for steps, workouts, sleep sync), Activity Recognition (for automatic activity detection), and Notifications (for reminders and updates). All permissions are optional and can be managed in Settings > Permissions."
                )

                FAQItem(
                    question = "How does sugar estimation work?",
                    answer = "When explicit sugar data isn't available for a food, Coachie uses smart estimation based on food categories. For example, berries are estimated to have high sugar content (about 85% of carbs as sugar), while other foods use different heuristics. This ensures accurate sugar tracking even when database entries lack explicit sugar values."
                )

                FAQItem(
                    question = "What is the behavioral profile?",
                    answer = "The behavioral profile helps Coachie understand your personality type (Upholder, Questioner, Obliger, or Rebel) based on Gretchen Rubin's Four Tendencies framework. This allows for personalized habit suggestions and recommendations that align with how you respond to expectations and accountability."
                )

                FAQItem(
                    question = "How do habit timers work?",
                    answer = "Habit timers are built-in timers for time-based habits like reading, meditation, or breathing exercises. When you start a habit with a timer, it tracks the duration and automatically completes the habit when the timer finishes. You can customize timer durations for different habits."
                )

                FAQItem(
                    question = "What is smart scheduling?",
                    answer = "Smart scheduling uses AI to optimize when you should perform habits based on your patterns, energy levels, and success rates. It analyzes when you're most likely to complete habits successfully and suggests optimal times for each habit."
                )

                FAQItem(
                    question = "How do I view my recipe details?",
                    answer = "Tap on any recipe in your recipes list, forum post, or circle post to view full recipe details. The recipe detail screen shows all ingredients with quantities, step-by-step instructions, complete nutrition breakdown (macros and micros), and options to save, share, or edit the recipe."
                )

                FAQItem(
                    question = "What is the difference between saved meals and recipes?",
                    answer = "Saved meals are single-serving meals you've logged before, perfect for quick re-logging. Recipes are full meal plans with multiple servings, complete ingredients lists, and instructions. You can save recipes as single-serving meals (quick select) or keep them as full recipes for sharing and meal planning."
                )

                FAQItem(
                    question = "What is the difference between 'Save Recipe' and 'Quick Select'?",
                    answer = "'Save Recipe' saves the recipe to your personal recipe collection (My Recipes). You can edit, share, and manage these recipes. They're stored as full recipes with all ingredients and instructions. 'Quick Select' saves the recipe as a single-serving meal to your Saved Meals for quick logging. The nutrition is automatically divided by servings, so when you log it later, you're logging one serving. Use 'Save Recipe' if you want to keep the full recipe for future use or sharing. Use 'Quick Select' if you just want to quickly log this meal again later."
                )

                FAQItem(
                    question = "What's the difference between Free and Pro tiers?",
                    answer = "Free tier includes all core health tracking features, unlimited social features (friends, messaging, forums), and limited AI features (1 AI meal inspiration/day, 1 daily insight/day, 10 AI coach chat messages/day, 5 habit suggestions/week). Free users can join up to 3 circles. Pro tier ($9.99/month or $99/year) unlocks unlimited AI features, AI-generated weekly blueprints, morning briefs, monthly insights, AI quest generation, unlimited circles, and recipe sharing within Coachie. See the 'Subscription Tiers & Features' section above for complete details."
                )

                FAQItem(
                    question = "What AI features are available on the free tier?",
                    answer = "Free tier includes limited access to: AI Meal Inspiration (1 per day), Daily Insights (1 per day with Pro feature mentions), AI Coach Chat (10 messages per day), and Habit Suggestions (5 per week). All other AI features (Weekly Blueprint, Morning Briefs, Monthly Insights, Quest Generation) are Pro-only."
                )

                FAQItem(
                    question = "What features are Pro-only?",
                    answer = "Pro-only features include: AI-Generated Weekly Blueprint (unlimited meal plans), Morning Briefs (unlimited personalized daily briefings), Monthly Insights (advanced analytics), AI Quest Generation (personalized challenges), Unlimited Circles (join as many as you want), and Recipe Sharing within Coachie (share with friends, post to forums, share to circles). External social media sharing (Instagram, Facebook, etc.) remains available for all users."
                )

                FAQItem(
                    question = "How do I upgrade to Pro?",
                    answer = "Navigate to the Subscription screen from the Settings menu or tap any upgrade prompt throughout the app. Pro is available as a monthly subscription ($9.99/month) or annual subscription ($99/year, which saves you 17% compared to monthly)."
                )

                FAQItem(
                    question = "Can I use Coachie for free?",
                    answer = "Yes! The free tier includes all core health tracking features (meals, workouts, sleep, water, mood, weight), unlimited social features (friends, messaging, forums), habit tracking, and limited AI features. You can use Coachie effectively on the free tier, with Pro offering unlimited AI features and premium tools to accelerate your health journey."
                )

                FAQItem(
                    question = "How do I access the Help & FAQ page?",
                    answer = "Tap the 3-dot menu icon in the top right corner of your dashboard, then select 'Help & FAQ'. This comprehensive guide covers all features, answers common questions, and helps you get the most out of Coachie."
                )

                FAQItem(
                    question = "What is the Progress screen?",
                    answer = "The Progress screen provides a comprehensive overview of your health journey with charts, trends, and insights. View your progress across nutrition, fitness, wellness, and habits all in one place."
                )

                FAQItem(
                    question = "How do I edit my profile?",
                    answer = "Go to Profile and tap on any section (Personal Information, Goals, Dietary Preferences) to edit. You can update your name, age, gender, height, weight, activity level, goals, dietary preferences, and macro targets. Changes are saved automatically."
                )

                FAQItem(
                    question = "What is the difference between Health Tracking, Habits, and Wellness dashboards?",
                    answer = "Health Tracking Dashboard focuses on physical health metrics (meals, workouts, sleep, water, weight). Habits Dashboard shows your habit progress, streaks, and completion rates. Wellness Dashboard provides access to mindfulness practices (meditation, breathing, journaling, social media breaks). Each dashboard is tailored to its specific focus area."
                )

                FAQItem(
                    question = "How do I share my recipes to social media?",
                    answer = "From your recipe detail screen or My Recipes screen, tap the share button and select a social media platform (Instagram, Facebook, TikTok, X/Twitter). Coachie will generate a beautiful promotional image featuring your meal photo alongside a detailed recipe card with ingredients, instructions, and nutrition information. The image includes the Coachie AI Health branding and is optimized for sharing on your chosen platform. You can also use the native share option to share to any app on your device."
                )

                FAQItem(
                    question = "What information is included in recipe card shares?",
                    answer = "Recipe card shares include your meal photo, the recipe name, all ingredients with quantities, step-by-step instructions, complete nutrition breakdown (calories, macros, and key micronutrients), and serving information. The image is beautifully designed with the Coachie AI Health logo and branding, making it perfect for sharing your healthy recipes with friends and followers on social media."
                )

                // Legal Links
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "📋 Legal",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                val context = LocalContext.current
                
                // Privacy Policy Link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val url = "https://playspace.games/coachie-privacy-policy"
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.util.Log.e("HelpScreen", "Failed to open privacy policy", e)
                            }
                        }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Privacy Policy",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                HorizontalDivider()

                // Terms of Service Link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val url = "https://playspace.games/coachie-terms-of-service"
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.util.Log.e("HelpScreen", "Failed to open terms of service", e)
                            }
                        }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Terms of Service",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun HelpSection(title: String, content: String) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface, // Changed from onSurfaceVariant to onSurface (dark) for better readability on light card
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun FAQItem(question: String, answer: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Q: $question",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "A: $answer",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
}
