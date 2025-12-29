'use client'

import { useAuth } from '../../lib/contexts/AuthContext'
import { useRouter } from 'next/navigation'
import { useState, useEffect } from 'react'
import LoadingScreen from '../../components/LoadingScreen'
// Icons - using simple SVG
const ChevronDownIcon = ({ className }: { className?: string }) => (
  <svg className={className || "h-5 w-5 text-gray-400"} fill="none" viewBox="0 0 24 24" stroke="currentColor">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
  </svg>
)
const ChevronUpIcon = ({ className }: { className?: string }) => (
  <svg className={className || "h-5 w-5 text-gray-400"} fill="none" viewBox="0 0 24 24" stroke="currentColor">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 15l7-7 7 7" />
  </svg>
)

interface HelpSectionProps {
  title: string
  content: string
}

function HelpSection({ title, content }: HelpSectionProps) {
  const [expanded, setExpanded] = useState(false)

  return (
    <div className="bg-gray-800 rounded-lg shadow-lg border border-gray-700 overflow-hidden">
      <button
        onClick={() => setExpanded(!expanded)}
        className="w-full px-6 py-4 flex items-center justify-between text-left hover:bg-gray-700 transition-colors"
      >
        <h3 className="text-lg font-bold text-white">{title}</h3>
        {expanded ? (
          <ChevronUpIcon className="h-5 w-5 text-gray-400" />
        ) : (
          <ChevronDownIcon className="h-5 w-5 text-gray-400" />
        )}
      </button>
      {expanded && (
        <div className="px-6 pb-4">
          <p className="text-gray-300 whitespace-pre-line leading-relaxed">{content}</p>
        </div>
      )}
    </div>
  )
}

interface FAQItemProps {
  question: string
  answer: string
}

function FAQItem({ question, answer }: FAQItemProps) {
  return (
    <div className="mb-6">
      <p className="text-white font-semibold mb-2">Q: {question}</p>
      <p className="text-gray-400 ml-4">A: {answer}</p>
    </div>
  )
}

export default function HelpPage() {
  const { user } = useAuth()
  const router = useRouter()
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!user) {
      router.push('/auth')
    } else {
      setLoading(false)
    }
  }, [user, router])

  if (loading || !user) {
    return <LoadingScreen />
  }

  return (
    <div className="min-h-screen bg-gray-900">
      <div className="max-w-4xl mx-auto py-8 px-4">
        {/* Header */}
        <div className="text-center mb-8">
          <div className="text-6xl mb-4">🆘</div>
          <h1 className="text-3xl font-bold text-white mb-2">Help & FAQ</h1>
        </div>

        <div className="space-y-4">
          {/* Getting Started */}
          <HelpSection
            title="🚀 Getting Started"
            content="Welcome to Coachie! Start by setting up your profile, goals, and tracking your first meal or workout. The AI coach will guide you through building healthy habits. Complete your behavioral profile to get personalized habit suggestions. Access Help & FAQ anytime from the 3-dot menu on the top right of your dashboard."
          />

          {/* Subscription Tiers */}
          <HelpSection
            title="💎 Subscription Tiers & Features"
            content={`Coachie offers two subscription tiers to meet your needs:

🆓 FREE TIER:
• All health tracking (meals, workouts, sleep, water, mood, weight)
• Manual meal logging with barcode scanning and food database
• Basic dashboard with charts and stats
• Google Fit & Health Connect sync
• Social features: Unlimited friends, messaging, forums
• Circles: Join up to 3 circles
• Habit tracking (manual creation and completion)
• All data storage and sync
• Voice logging
• Basic analytics and progress tracking
• Saved meals and recipes
• External social media sharing (Instagram, Facebook, TikTok, etc.)

AI Features (Limited):
• AI Meal Inspiration: 1 per day
• Daily Insights: 1 per day (includes Pro feature mentions)
• AI Coach Chat: 10 messages per day
• Habit Suggestions: 5 per week

💎 PRO TIER ($9.99/month or $99/year):
Everything in Free, PLUS:
• Unlimited AI Meal Inspiration
• Unlimited Daily Insights
• Unlimited AI Coach Chat
• Unlimited Habit Suggestions
• AI-Generated Weekly Blueprint (unlimited meal plans)
• Morning Briefs (unlimited personalized daily briefings)
• Monthly Insights (advanced analytics)
• AI Quest Generation (personalized challenges)
• Unlimited Circles (join as many as you want)
• Recipe Sharing (share with friends, post to forums, share to circles)
• Priority support
• Advanced analytics and insights

Note: Most core features are available on the free tier. Pro tier unlocks unlimited AI features and premium tools to accelerate your health journey.`}
          />

          {/* Health Tracking */}
          <HelpSection
            title="📊 Health Tracking"
            content={`• Meal Logging: Take photos of meals for AI-powered analysis
• Google Fit Sync: Connect your Google account in Settings > Permissions to automatically sync steps, workouts, and sleep from Google Fit. You'll need to sign in to your Google account and grant fitness permissions.
• Recipe Analysis: Take photos or paste recipe text to get macro and micro nutrition estimates per serving - automatically calculates ingredient nutrition and scales to your serving size
• Edit Meal Analysis: Review and adjust food names, calories, and macros before saving
• Menu Item Detection: Automatically detects restaurant menu items and searches for official nutrition facts online
• Sugar Estimation: Smart sugar estimation for foods when explicit sugar data isn't available - uses heuristics based on food categories (berries, fruits, etc.)
• Saved Meals: Save frequently eaten meals for quick logging
• My Recipes: Access all your saved recipes from the 'View All Recipes' button in the meal logging screen
• Recipe Quick Select: Save analyzed recipes as single-serving meals for easy logging later
• Recipe Sharing: Share your recipes and nutrition analysis with friends, post to forums, or share to recipe sharing circles
• Recipe Sharing Options: Share recipes with friends, post to the Recipe Sharing forum channel, or create a dedicated recipe sharing circle
• Social Media Sharing: Share your recipes to Instagram, Facebook, TikTok, X (Twitter), or other apps with beautiful promotional images featuring your meal photos and recipe cards
• Recipe Card Sharing: Generate stunning recipe card images with full ingredients, instructions, and nutrition info to share on social media
• Meal Photo Sharing: Share your meal photos alongside recipe cards in beautifully designed promotional posts
• Save Recipes from Forum Posts: Save recipes posted by other users in forums to your personal recipe collection
• Save Recipes from Circle Posts: Save recipes shared in your circles to your personal recipes
• Recipe Detail View: View full recipe details including all ingredients, instructions, and complete nutrition breakdown
• Recipe Capture: Capture recipes from photos, text, or manual entry
• Save Recipes from Blueprint: Save any meal from your weekly blueprint as a recipe for later use
• Post Recipes to Forum: Share your favorite recipes with the community in the Recipe Sharing forum channel
• Shared Recipes: Browse recipes shared by the community in the Recipe Sharing forum
• AI Meal Inspiration: Get personalized meal recommendations by selecting ingredients (proteins, vegetables, fruits, grains, healthy fats, pantry items) - AI creates recipes using only your selected ingredients
• Meal Detail View: View comprehensive meal details including full nutrition breakdown, ingredients, and macros
• Workout Tracking: Log exercises, duration, and calories burned
• Sleep Logging: Track sleep duration and quality
• Water Intake: Monitor daily hydration goals with glass-based tracking (1 glass = 8 oz)
• Weight Tracking: Record weight with progress charts
• Mood Tracking: Log mood, energy, stress levels with trends
• Supplement Tracking: Track vitamins and supplements with photo capture
• Saved Supplements: Save frequently taken supplements for quick logging
• Vitamins & Minerals: Track micronutrient intake with smart over-limit warnings (only shows red for significant overages >150%)
• Micronutrient Tracker: Comprehensive view of all vitamins and minerals with daily goals and progress
• Sugar Intake Tracking: Monitor daily sugar and added sugar intake with detailed breakdowns
• Calories Detail: Detailed view of daily calorie intake with breakdown by meals
• Health Connect Integration: Automatic sync of steps, workouts, sleep, and calories from Health Connect (Android's unified health platform)
• Google Fit Sync: Connect your Google account in Settings > Permissions to automatically sync steps, workouts, and sleep from Google Fit. You'll need to sign in to your Google account and grant fitness permissions. Once connected, data syncs automatically and prevents duplicate workout entries.
• Activity Recognition: Automatic activity detection for accurate workout and step tracking
• Daily Log: Comprehensive view of all your daily health logs in one place
• Health Tracking Dashboard: Centralized dashboard for all health metrics and tracking options`}
          />

          {/* Habits */}
          <HelpSection
            title="🎯 Habits & Behavior Change"
            content={`• AI-Powered Suggestions: Get personalized habit recommendations based on your behavioral profile
• Habit Templates: Quick-add popular habits including breathing exercises, social media breaks, reading, and wellness practices
• Habit Intelligence: Advanced analytics showing patterns, timing, and success rates
• Predictive Habits: AI predicts which habits you'll succeed at (70%+ success rate)
• Automatic Habit Tracking: All habits are automatically tracked when you log related activities (meals complete 'eat breakfast', workouts complete 'gym session', water logs complete hydration habits, sleep logs complete bedtime habits, breathing exercises complete breathing habits)
• Habit Timers: Built-in timers for time-based habits (reading, meditation, breathing exercises) with customizable durations
• Habit Progress Tracking: Detailed progress view showing completion rates, streaks, and patterns over time
• Smart Scheduling: AI-powered habit scheduling that optimizes timing based on your patterns and energy levels
• Reading Habits: Track reading with duration options (30 minutes, 1 hour)
• Social Media Breaks: Track social media breaks with duration options (30 minutes, 1 hour, 4 hours, 8 hours, all day) - longer breaks earn bonus Coachie points
• Habit Creation: Create custom habits with personalized names, descriptions, and tracking options
• Habit Editing: Edit existing habits including name, description, and tracking preferences
• Circadian Optimization: Habits scheduled at optimal times based on your energy patterns
• Environmental Adaptation: Habits adapt to weather, location, and context
• Four Tendencies: Personalized approach based on Upholder, Questioner, Obliger, or Rebel personality
• Behavioral Profile: Complete your behavioral profile to get personalized habit suggestions and recommendations
• Habit Stacking: Build habits on top of existing routines
• Streak Tracking: Visual progress with streak counters and longest streak records
• Streak Details: Comprehensive streak history showing daily activity, log counts, and streak patterns
• Wellness Habits: Track breathing exercises, social media breaks, meditation, and mindfulness practices
• Habits Dashboard: Centralized dashboard for all your habits with progress overview`}
          />

          {/* Wellness */}
          <HelpSection
            title="🧘 Wellness & Mindfulness"
            content={`• Daily Meditation: AI-generated personalized meditation sessions with guided audio instructions
• Meditation Sessions: Multiple durations (5, 10, 15, 20 minutes) and categories (Guided, Silent, Mindfulness, Body Scan) with appropriate content for each
• Meditation Variety: Each session is unique - AI prevents repetitive lines and ensures productive, non-repetitive meditation scripts
• Breathing Exercises: Guided breathing exercises including Quick Calm (1 min), Gentle Breathing (3 min), Deep Focus (5 min), and Box Breathing (4-4-4-4 technique)
• Body Scan: Progressive body scan meditation from head to toe for deep relaxation and body awareness
• Grounding Exercise: 5-4-3-2-1 grounding technique to anchor yourself in the present moment (5 things you see, 4 you touch, 3 you hear, 2 you smell, 1 you taste)
• Stretching Exercises: Guided stretching routines with timer and instructions for flexibility and recovery
• Wind Down Audio: Calming audio tracks including body scan meditation, gratitude practice, and peaceful sleep stories
• Social Media Break: Take intentional breaks from social media to reduce stress and improve focus with duration options (30 min, 1 hour, 4 hours, 8 hours, all day)
• Journaling: Time-appropriate journal prompts (morning/afternoon/evening) with save confirmation
• Journal History: Revisit past journal entries and reflections with full history view
• My Wins: Automatic achievement tracking - Coachie analyzes your daily activities (steps, workouts, water, macros, habits, streaks) and generates wins automatically without requiring journal entries
• Win of the Day: Daily achievement highlights based on your actual accomplishments with detailed win breakdowns
• Win Details: View comprehensive details of each win including what triggered it and your progress
• Mood Charts: Visualize mood trends over time with energy and stress overlays
• Meditation Charts: Track meditation duration, count, and mood improvement
• Journal Charts: Monitor word count, completion status, and entry frequency
• Today's Focus: Personalized daily tasks organized by time of day (morning, afternoon, evening) with automatic water reminder at the end
• Today's Reset: Daily mindful sessions for stress relief and mental clarity
• Voice Logging: Log meals, activities, complete habits, and create journal entries using voice commands
• Voice Settings: Customize voice logging preferences including text-to-speech settings and voice coaching options`}
          />

          {/* Community */}
          <HelpSection
            title="👥 Community & Accountability"
            content={`• Circles: Join groups of 2-5 people with shared goals and daily check-ins
• Circle Posts: Share updates, photos, and achievements with your circle
• Like & Comment: Engage with circle posts by liking and commenting to show support
• Circle Invites: Invite friends to join your circles directly from the circle detail screen - they'll receive a friend request notification with a circle invitation badge
• Circle Invite Notifications: Pending circle invites appear as friend requests in the Friends screen (Requests tab) with a clear 'Circle Invitation' indicator
• Automatic Circle Streaks: Circle streaks automatically update based on all members' daily activity (habit completions, health logs, check-ins) - celebrating 7-day streaks together
• Circle Interaction Bonus: Interacting with your circles (posting, commenting, liking) earns you Flow Score points for wellness
• Friends: Add friends, send and accept friend requests, and build your support network
• Messaging: Direct messaging with friends for accountability and support (send button appears in message field when typing)
• Forum Posts: Participate in community discussions and support forums
• Forum Post Management: Delete your own forum posts using the 3-dot menu on your posts (only original posters can delete)
• Upvote Forum Posts: Upvote posts you find helpful or want to see more of - posts are sorted by upvotes by default to show what users want most
• Forum Sorting: Toggle between 'Top' (sorted by upvotes) and 'New' (sorted by date) to view posts
• Coachie News Channel: Stay updated with app updates, new feature announcements, roadmaps, and development updates
• AI Pact Maker: Automatically matched with others who missed the same habit for 7-day accountability pacts
• Buddy System: 1-on-1 accountability pairs with shared progress tracking
• Win Feed: Celebrate wins together with emoji reactions and encouragement
• Weekly Huddles: AI-hosted 15-minute voice check-ins with your circle
• Challenges: Join circle challenges with entry fees going to charity
• Vent Tab: Anonymous support space for sharing struggles
• Graduation Wall: Celebrate major achievements with your circle
• Push Notifications: Get notified when your circle members check in or complete habits
• Auto-Update: Circles automatically refresh every 30 seconds to show the latest activity`}
          />

          {/* AI Coaching */}
          <HelpSection
            title="🤖 AI Coaching & Insights"
            content={`• Smart Coach: Personalized daily insights based on your health data
• AI Chat: Interactive AI coach chat for questions, advice, and personalized guidance
• Morning Briefing: Daily summary with habits, circle check-ins, and AI insights (uses your preferred unit system - Imperial or Metric)
• Afternoon Briefing: Mid-day check-in with progress updates and reminders (uses your preferred unit system)
• Evening Briefing: End-of-day summary with accomplishments and recommendations (uses your preferred unit system)
• Brief Accuracy: Briefs use accurate user data including correct protein goals, water intake in glasses, and actual macro targets
• First-Time User Brief: Welcome message for new users with guidance on habits, weekly blueprint, AI meal inspiration, and Help & FAQ access
• Unit System Support: All briefs and blueprints automatically use your preferred unit system (Imperial: lbs, oz, fl oz OR Metric: g, kg, ml)
• Quests: AI-suggested quests based on your goals and data with progress tracking
• Insights: Monthly AI-generated insights (3-5 per month) with charts and actionable recommendations
• Mood-Based Recommendations: AI suggests activities based on your mood patterns
• Pattern Recognition: Identifies correlations between sleep, exercise, mood, and nutrition
• Predictive Analytics: Forecasts habit success and suggests optimizations
• Adaptive Learning: AI learns your preferences and adjusts recommendations
• Voice Coaching: Enable text-to-speech for spoken encouragement and tips
• AI Meal Inspiration: Get personalized meal recommendations by selecting ingredients - AI creates recipes using only your selected ingredients that fit your macros and dietary preferences`}
          />

          {/* Weekly Blueprint */}
          <HelpSection
            title="🛒 Weekly Blueprint"
            content={`• AI-Generated Meal Plans: Get personalized 7-day meal plans with recipes based on your dietary preferences, macros, allergies, and goals
• Shopping Lists: Automatically generated shopping lists organized by category (Produce, Proteins, Dairy, etc.) with quantities and estimated costs (limited to ~25 items for efficiency)
• Meal Preferences: Set your preferred number of meals per day (2, 3, or 4) and snacks per day (0, 1, or 2) in Settings
• Serving Size Adjustment: Adjust servings (1-8 people) to scale recipes and shopping list quantities for individuals or families
• Unit System Support: All measurements automatically use your preferred unit system (Imperial: lbs, oz, fl oz, cups, tbsp, tsp OR Metric: g, kg, ml, L)
• Save Recipes from Blueprint: Save any meal from your weekly blueprint as a recipe for later use or sharing
• Share Recipes from Blueprint: Share recipes from your blueprint with friends or post them to the Recipe Sharing forum
• Meal Time Reminders: Configure breakfast, lunch, dinner, and snack times to receive push notifications before meals
• Interactive Shopping List: Mark items as bought, edit quantities, add notes, and regenerate your blueprint anytime
• Strict Dietary Adherence: Blueprints strictly enforce your dietary preferences (vegetarians get no meat, carnivores get only meat, etc.) - both macro ratios AND food types
• Macro Goal Alignment: Blueprints are designed to hit your daily macro targets (protein, carbs, fat) across all meals and snacks
• Meal Variety: Each week generates new, diverse meals with cuisine rotation, varied cooking methods, and unique recipes to prevent repetition (can repeat up to 20% of meals from previous weeks)
• Budget-Aware: Meal plans consider your budget level and household size
• Weekly Notifications: Get notified every Sunday when your new weekly blueprint is ready
• Export & Share: Export shopping lists as PNG images or PDF documents for easy sharing
• Access from Dashboard: View your weekly blueprint card on the LifeOS Dashboard or navigate directly to the full shopping list`}
          />

          {/* Charts & Analytics */}
          <HelpSection
            title="📈 Charts & Analytics"
            content={`• Health Charts: Line graphs for calories, water, sleep, and weight with swipe navigation (easier to spot trends)
• Macro Pie Charts: Visual breakdown of protein, carbs, and fat by calories (daily and weekly views)
• Wellness Charts: Mood trends, meditation sessions, and journal entries
• Coachie Flow Score: Comprehensive wellness score (0-100) calculated from nutrition, fitness, sleep, water, habits, wellness activities, and circle interactions
• Flow Score Details: Detailed breakdown of your score with progress graphs showing trends over 7 days, monthly, and quarterly periods
• Flow Score Components: Health score (nutrition, fitness, sleep, water), Wellness score (habits, mindfulness, circle interactions), and Habits score (completion rates, streaks)
• Trend Analysis: View progress over days, weeks, months, or years
• Weekly Completion Trends: Track habit completion rates by week with accurate current dates
• Success Rates: Track habit completion rates and patterns
• Performance Insights: Understand what works best for you
• Goals Breakdown: Detailed view of all your goals with progress tracking
• Achievements: View all your achievements and milestones
• Progress Screen: Comprehensive progress overview with charts and insights
• Streak Details: Detailed streak history with daily log counts and patterns`}
          />
        </div>

        {/* FAQ Section */}
        <div className="mt-12 mb-8">
          <h2 className="text-2xl font-bold text-white mb-6">❓ Frequently Asked Questions</h2>
          
          <div className="space-y-4">
            <FAQItem
              question="How does the AI meal analysis work?"
              answer="Take a photo of your meal and Coachie uses AI to analyze ingredients, estimate calories, macronutrients, and provide nutritional insights. The analysis screen opens in edit mode so you can review and adjust the food name, calories, protein, carbs, and fat before saving. You can save meals for quick logging later."
            />
            <FAQItem
              question="Can I edit the meal analysis before saving?"
              answer="Yes! When you take a photo of a meal, the analysis result screen opens in edit mode by default. You can adjust the food name, calories, protein, carbs, and fat values before submitting. Click the check button to save your edits, or click 'Submit Meal' to log the meal with your adjusted values. All edited values are used when saving the meal."
            />
            <FAQItem
              question="What is the Habit & Behavior Change Engine?"
              answer="An advanced AI system that learns your patterns, predicts successful habits (70%+ success rate), optimizes timing based on circadian rhythms, and adapts to environmental factors. It uses your Four Tendencies personality type to personalize recommendations."
            />
            <FAQItem
              question="How do AI-powered habit suggestions work?"
              answer="Coachie analyzes your behavioral profile, existing habits, success patterns, and goals to suggest 3-5 personalized habits you haven't tried yet. Suggestions include confidence scores, difficulty levels, and rationale for why they fit your patterns."
            />
            <FAQItem
              question="What is My Wins?"
              answer="My Wins automatically tracks your achievements by analyzing your daily activities. Coachie generates wins based on your actual accomplishments like 'Most Steps Ever', 'Hit Step Goal', 'Perfect Macro Day', 'Completed Habit', 'Longest Streak', and more. Wins are created automatically from your logged activities (steps, workouts, water, macros, habits) - you don't need to journal to get wins."
            />
            <FAQItem
              question="How do Circles work?"
              answer="Circles are groups of 2-5 people with shared goals. Members check in daily with energy levels and optional notes. You get push notifications when others check in. Circles have a streak counter and support each other's progress."
            />
            <FAQItem
              question="What is the Coachie Flow Score?"
              answer="The Coachie Flow Score is a comprehensive wellness score (0-100) that combines your nutrition, fitness, sleep, water intake, habits, and wellness activities into a single metric. View detailed breakdowns and progress graphs showing trends over 7 days, monthly, and quarterly periods in the Flow Score Details screen."
            />
            <FAQItem
              question="How do I upgrade to Pro?"
              answer="Navigate to the Subscription screen from the Settings menu or tap any upgrade prompt throughout the app. Pro is available as a monthly subscription ($9.99/month) or annual subscription ($99/year, which saves you 17% compared to monthly)."
            />
            <FAQItem
              question="Can I use Coachie for free?"
              answer="Yes! The free tier includes all core health tracking features (meals, workouts, sleep, water, mood, weight), unlimited social features (friends, messaging, forums), habit tracking, and limited AI features. You can use Coachie effectively on the free tier, with Pro offering unlimited AI features and premium tools to accelerate your health journey."
            />
            <FAQItem
              question="How do I access the Help & FAQ page?"
              answer="Tap the 3-dot menu icon in the top right corner of your dashboard, then select 'Help & FAQ'. This comprehensive guide covers all features, answers common questions, and helps you get the most out of Coachie."
            />
            </div>
        </div>

        {/* Legal Links */}
        <div className="mt-12 mb-8">
          <h2 className="text-2xl font-bold text-white mb-6">📋 Legal</h2>
          
          <div className="space-y-4">
            <a
              href="https://playspace.games/coachie-privacy-policy"
              target="_blank"
              rel="noopener noreferrer"
              className="block bg-gray-800 rounded-lg border border-gray-700 p-4 hover:bg-gray-700 transition-colors"
            >
              <div className="flex items-center justify-between">
                <span className="text-white font-medium">Privacy Policy</span>
                <ChevronDownIcon className="h-5 w-5 text-gray-400 rotate-[-90deg]" />
              </div>
            </a>
            
            <a
              href="https://playspace.games/coachie-terms-of-service"
              target="_blank"
              rel="noopener noreferrer"
              className="block bg-gray-800 rounded-lg border border-gray-700 p-4 hover:bg-gray-700 transition-colors"
            >
              <div className="flex items-center justify-between">
                <span className="text-white font-medium">Terms of Service</span>
                <ChevronDownIcon className="h-5 w-5 text-gray-400 rotate-[-90deg]" />
              </div>
            </a>
          </div>
        </div>
      </div>
    </div>
  )
}
