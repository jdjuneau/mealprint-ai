'use client'

import CoachieCard from './ui/CoachieCard'

interface HelpSectionProps {
  title: string
  content: string
}

function HelpSection({ title, content }: HelpSectionProps) {
  return (
    <CoachieCard>
      <div className="p-6">
        <h2 className="text-xl font-bold text-primary-600 mb-3">{title}</h2>
        <div className="text-gray-700 space-y-2 whitespace-pre-line">
          {content.split('\n').map((line, index) => (
            <div key={index}>{line}</div>
          ))}
        </div>
      </div>
    </CoachieCard>
  )
}

interface FAQItemProps {
  question: string
  answer: string
}

function FAQItem({ question, answer }: FAQItemProps) {
  return (
    <div className="mb-6">
      <h3 className="text-lg font-semibold text-primary-600 mb-2">Q: {question}</h3>
      <p className="text-gray-700 ml-4">A: {answer}</p>
    </div>
  )
}

export default function HelpScreen() {
  return (
    <div className="max-w-4xl mx-auto space-y-6 pb-8">
      {/* Header */}
      <div className="text-center py-8">
        <div className="text-6xl mb-4">🆘</div>
        <h1 className="text-4xl font-bold text-gray-900 mb-2">Help & FAQ</h1>
      </div>

      {/* Getting Started */}
      <HelpSection
        title="🚀 Getting Started"
        content="Welcome to Coachie! Start by setting up your profile, goals, and tracking your first meal or workout. The AI coach will guide you through building healthy habits. Complete your behavioral profile to get personalized habit suggestions."
      />

      {/* Health Tracking Features */}
      <HelpSection
        title="📊 Health Tracking"
        content={`• Meal Logging: Take photos of meals for AI-powered analysis
• Recipe Analysis: Take photos or paste recipe text to get macro and micro nutrition estimates per serving - automatically calculates ingredient nutrition and scales to your serving size
• Edit Meal Analysis: Review and adjust food names, calories, and macros before saving
• Menu Item Detection: Automatically detects restaurant menu items and searches for official nutrition facts online
• Saved Meals: Save frequently eaten meals for quick logging
• Recipe Quick Save: Save analyzed recipes as single-serving meals for easy logging later
• Recipe Sharing: Share your recipes and nutrition analysis with friends
• Workout Tracking: Log exercises, duration, and calories burned
• Sleep Logging: Track sleep duration and quality
• Water Intake: Monitor daily hydration goals
• Weight Tracking: Record weight with progress charts
• Mood Tracking: Log mood, energy, stress levels with trends
• Supplement Tracking: Track vitamins and supplements
• Vitamins & Minerals: Track micronutrient intake with smart over-limit warnings (only shows red for significant overages >150%)
• Google Fit Sync: Automatic sync of steps, workouts, and sleep from Google Fit (prevents duplicate workout entries)`}
      />

      {/* Habit & Behavior Features */}
      <HelpSection
        title="🎯 Habits & Behavior Change"
        content={`• AI-Powered Suggestions: Get personalized habit recommendations based on your behavioral profile
• Habit Templates: Quick-add popular habits including breathing exercises and social media breaks
• Habit Intelligence: Advanced analytics showing patterns, timing, and success rates
• Predictive Habits: AI predicts which habits you'll succeed at (70%+ success rate)
• Automatic Habit Completion: Habits automatically complete when you log related activities (meals complete 'eat breakfast', workouts complete 'gym session', water logs complete hydration habits, sleep logs complete bedtime habits, breathing exercises complete breathing habits)
• Circadian Optimization: Habits scheduled at optimal times based on your energy patterns
• Environmental Adaptation: Habits adapt to weather, location, and context
• Four Tendencies: Personalized approach based on Upholder, Questioner, Obliger, or Rebel personality
• Habit Stacking: Build habits on top of existing routines
• Streak Tracking: Visual progress with streak counters and longest streak records
• Wellness Habits: Track breathing exercises, social media breaks, meditation, and mindfulness practices`}
      />

      {/* Wellness Features */}
      <HelpSection
        title="🧘 Wellness & Mindfulness"
        content={`• Daily Meditation: AI-generated personalized meditation sessions with guided audio instructions
• Meditation Sessions: Multiple durations (5, 10, 15, 20 minutes) and categories (Guided, Silent, Mindfulness, Body Scan) with appropriate content for each
• Breathing Exercises: Guided breathing exercises including Quick Calm (1 min), Gentle Breathing (3 min), Deep Focus (5 min), and Box Breathing (4-4-4-4 technique)
• Social Media Break: Take intentional breaks from social media to reduce stress and improve focus
• Journaling: Time-appropriate journal prompts (morning/afternoon/evening)
• Journal History: Revisit past journal entries and reflections
• My Wins: AI extracts achievements and gratitudes from journal entries
• Mood Charts: Visualize mood trends over time with energy and stress overlays
• Meditation Charts: Track meditation duration, count, and mood improvement
• Journal Charts: Monitor word count, completion status, and entry frequency
• Today's Reset: Daily mindful sessions for stress relief and mental clarity
• Voice Logging: Log meals, activities, complete habits, and create journal entries using voice commands`}
      />

      {/* Community Features */}
      <HelpSection
        title="👥 Community & Accountability"
        content={`• Circles: Join groups of 2-5 people with shared goals and daily check-ins
• Circle Posts: Share updates, photos, and achievements with your circle
• Like & Comment: Engage with circle posts by liking and commenting to show support
• Circle Invites: Invite friends to join your circles directly from the circle detail screen
• Automatic Circle Streaks: Circle streaks automatically update based on all members' daily activity (habit completions, health logs, check-ins) - celebrating 7-day streaks together
• Friends: Add friends, send and accept friend requests, and build your support network
• Messaging: Direct messaging with friends for accountability and support (send button appears in message field when typing)
• Forum Posts: Participate in community discussions and support forums
• Upvote Forum Posts: Upvote posts you find helpful or want to see more of - posts are sorted by upvotes by default to show what users want most
• Forum Sorting: Toggle between 'Top' (sorted by upvotes) and 'New' (sorted by date) to view posts
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

      {/* AI Coaching Features */}
      <HelpSection
        title="🤖 AI Coaching & Insights"
        content={`• Smart Coach: Personalized daily insights based on your health data
• Morning Briefing: Daily summary with habits, circle check-ins, and AI insights (uses your preferred unit system - Imperial or Metric)
• Afternoon Briefing: Mid-day check-in with progress updates and reminders (uses your preferred unit system)
• Evening Briefing: End-of-day summary with accomplishments and recommendations (uses your preferred unit system)
• Unit System Support: All briefs and blueprints automatically use your preferred unit system (Imperial: lbs, oz, fl oz OR Metric: g, kg, ml)
• Quests: AI-suggested quests based on your goals and data with progress tracking
• Insights: Monthly AI-generated insights (3-5 per month) with charts and actionable recommendations
• Mood-Based Recommendations: AI suggests activities based on your mood patterns
• Pattern Recognition: Identifies correlations between sleep, exercise, mood, and nutrition
• Predictive Analytics: Forecasts habit success and suggests optimizations
• Adaptive Learning: AI learns your preferences and adjusts recommendations
• Voice Coaching: Enable text-to-speech for spoken encouragement and tips`}
      />

      {/* Weekly Blueprint Features */}
      <HelpSection
        title="🛒 Weekly Blueprint"
        content={`• AI-Generated Meal Plans: Get personalized 7-day meal plans with recipes based on your dietary preferences, macros, allergies, and goals
• Shopping Lists: Automatically generated shopping lists organized by category (Produce, Proteins, Dairy, etc.) with quantities and estimated costs (limited to ~25 items for efficiency)
• Meal Preferences: Set your preferred number of meals per day (2, 3, or 4) and snacks per day (0, 1, or 2) in Settings
• Serving Size Adjustment: Adjust servings (1-8 people) to scale recipes and shopping list quantities for individuals or families
• Unit System Support: All measurements automatically use your preferred unit system (Imperial: lbs, oz, fl oz, cups, tbsp, tsp OR Metric: g, kg, ml, L)
• Meal Time Reminders: Configure breakfast, lunch, dinner, and snack times to receive push notifications before meals
• Interactive Shopping List: Mark items as bought, edit quantities, add notes, and regenerate your blueprint anytime
• Strict Dietary Adherence: Blueprints strictly enforce your dietary preferences (vegetarians get no meat, carnivores get only meat, etc.) - both macro ratios AND food types
• Meal Variety: Each week generates new, diverse meals with cuisine rotation, varied cooking methods, and unique recipes to prevent repetition
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
• Trend Analysis: View progress over days, weeks, months, or years
• Weekly Completion Trends: Track habit completion rates by week with accurate current dates
• Success Rates: Track habit completion rates and patterns
• Performance Insights: Understand what works best for you`}
      />

      {/* Frequently Asked Questions */}
      <div className="mt-8">
        <h2 className="text-2xl font-bold text-gray-900 mb-6">❓ Frequently Asked Questions</h2>

        <FAQItem
          question="How does the AI meal analysis work?"
          answer="Take a photo of your meal and Coachie uses AI to analyze ingredients, estimate calories, macronutrients, and provide nutritional insights. The analysis screen opens in edit mode so you can review and adjust the food name, calories, protein, carbs, and fat before saving. You can save meals for quick logging later."
        />

        <FAQItem
          question="Can I edit the meal analysis before saving?"
          answer="Yes! When you take a photo of a meal, the analysis result screen opens in edit mode by default. You can adjust the food name, calories, protein, carbs, and fat values before submitting. Click the check button to save your edits, or click 'Submit Meal' to log the meal with your adjusted values. All edited values are used when saving the meal."
        />

        <FAQItem
          question="How does menu item detection work?"
          answer="When you take a photo of a restaurant menu or menu item, Coachie automatically detects if it's a menu item. It extracts the restaurant name, menu item name, and description, then searches online for the official nutrition facts. If found, it uses the accurate nutrition data instead of estimates. If not found, it falls back to the estimated nutrition from the photo analysis."
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
          question="What is Habit Intelligence?"
          answer="Habit Intelligence analyzes your habit patterns to identify consistency, optimal timing, weekday/weekend differences, and habit sequences. It provides adaptive suggestions for difficulty adjustments, timing optimization, and habit stacking."
        />

        <FAQItem
          question="How does Google Fit integration work?"
          answer="Coachie automatically syncs your steps, calories burned, sleep duration, and workouts from Google Fit. This data appears in your daily log and contributes to your health score. The sync prevents duplicate workout entries by using stable identifiers for each workout. Sync runs periodically throughout the day to keep your data up to date. You can enable/disable sync in settings."
        />

        <FAQItem
          question="What is My Wins?"
          answer="My Wins automatically extracts achievements, gratitudes, and positive moments from your journal entries using AI. It creates a timeline of your wins, helps you see patterns, and provides encouragement on difficult days."
        />

        <FAQItem
          question="How do Circles work?"
          answer="Circles are groups of 2-5 people with shared goals. Members check in daily with energy levels and optional notes. You get push notifications when others check in. Circles have a streak counter and support each other's progress."
        />

        <FAQItem
          question="What are AI Pacts?"
          answer="If you miss the same habit for 2+ consecutive days, Coachie automatically matches you with others in the same situation. If both accept, a 7-day accountability pact is created. You'll get daily check-ins and celebrate completion together."
        />

        <FAQItem
          question="How does the Buddy System work?"
          answer="Find a 1-on-1 accountability partner with similar goals. AI matches you based on goal, tendency, streak, and timezone. You can see each other's habit completions, send voice notes (Pro feature), and celebrate wins together."
        />

        <FAQItem
          question="What are Circle Challenges?"
          answer="Pre-built challenge templates like '10K steps/day for 7 days' or 'No sugar after 8 PM'. Members pay an entry fee (donated to charity), submit daily proof, and compete on a leaderboard. Winners get badges and recognition."
        />

        <FAQItem
          question="What is the Vent Tab?"
          answer="A safe, anonymous space within your circle to share struggles without judgment. Others can reply with empathy. Coachie provides AI-generated supportive responses. All content is moderated for safety."
        />

        <FAQItem
          question="What is the Graduation Wall?"
          answer="A private circle-only celebration space. When someone achieves a major goal, circle members vote to post it. The wall shows photos, stories, and achievements. Pro members can download graduation certificates."
        />

        <FAQItem
          question="How do Weekly Huddles work?"
          answer="AI-hosted 15-minute voice check-ins scheduled weekly. Coachie facilitates the conversation, members raise hands to speak, and the session is summarized. Pro members can access recordings and transcripts."
        />

        <FAQItem
          question="How does mood tracking help?"
          answer="Mood data helps AI personalize meditation sessions, journal prompts, and activity suggestions. It identifies patterns (e.g., mood improves after exercise) and provides insights. Mood contributes to your wellness score."
        />

        <FAQItem
          question="What is the Morning Briefing?"
          answer="A daily summary showing your habits, circle check-ins, health metrics, and AI insights. It helps you start your day with clarity and focus. You can check in with your circle directly from the briefing."
        />

        <FAQItem
          question="Can I track multiple goals?"
          answer="Yes! Set goals for weight, water intake, macronutrients, steps, and build unlimited custom habits. Coachie adapts to all your health objectives and provides personalized coaching for each."
        />

        <FAQItem
          question="How does voice logging work?"
          answer="Use the floating microphone button to log meals, workouts, activities, complete habits, or create journal entries by voice. Coachie transcribes and parses your commands automatically. For habits, say 'complete [habit name]' or 'done with [habit name]' and it will find and complete the matching habit. For journaling, say 'journal about [topic]' or 'write about [topic]' to create a journal entry. Enable voice responses in settings to hear spoken coaching tips."
        />

        <FAQItem
          question="What are Pro features?"
          answer="Pro features include: GIF reactions on wins, pinning wins to top, voice notes with buddies, huddle recordings/transcripts, PDF graduation certificates, and custom challenge creation. Upgrade in settings."
        />

        <FAQItem
          question="Is my data private?"
          answer="Absolutely. All health data is encrypted and stored securely in Firebase. Coachie never shares your personal information with third parties. Circle data is only visible to circle members. Anonymous vents protect your identity."
        />

        <FAQItem
          question="What are Breathing Exercises?"
          answer="Coachie offers guided breathing exercises including Quick Calm (1 minute), Gentle Breathing (3 minutes), Deep Focus (5 minutes), and Box Breathing (classic 4-4-4-4 technique). These exercises help reduce stress, improve focus, and regulate your nervous system. Access them from the Wellness dashboard. You can also add breathing exercises as habits to track your practice daily. When you select a breathing exercise from Today's Focus, it takes you directly to the breathing exercises screen."
        />

        <FAQItem
          question="Can I track breathing exercises and social media breaks as habits?"
          answer="Yes! Breathing exercises and social media breaks are available as habit suggestions and templates. Add 'Daily breathing exercise', 'Morning breathing routine', 'Social media break', or 'No social media before bed' from the habit suggestions or templates screen to track these wellness practices consistently."
        />

        <FAQItem
          question="What is Social Media Break?"
          answer="A wellness feature that helps you take intentional breaks from social media. Set a duration and Coachie will guide you through the break, helping reduce stress, improve focus, and create healthier digital habits. Track your breaks as habits for accountability."
        />

        <FAQItem
          question="How do I invite friends to my circle?"
          answer="Open your circle detail screen and tap the group icon in the top bar. This opens the user search where you can find friends and invite them to join your circle. They'll receive a notification and can accept the invitation. Once in a circle, you can create posts, like and comment on others' posts to show support and engagement."
        />

        <FAQItem
          question="How does messaging work?"
          answer="You can message any of your friends directly from the Friends screen. Messages are real-time and support conversations with multiple friends. The send button appears inside the message field when you start typing, making it easy to send messages. You'll receive notifications when you get new messages. If you encounter permission errors, make sure you're both friends and check your account permissions."
        />

        <FAQItem
          question="What are Quests?"
          answer="Quests are AI-suggested challenges based on your goals and health data. Complete quests to earn rewards and track your progress. Quests adapt to your current habits and help you achieve your health objectives."
        />

        <FAQItem
          question="What are Insights?"
          answer="Insights are monthly AI-generated summaries (3-5 per month) that analyze your health patterns, habits, and progress. Each insight includes charts, trends, and actionable recommendations to help you optimize your health journey."
        />

        <FAQItem
          question="How do I like or comment on circle posts?"
          answer="In any circle, you can like posts by tapping the heart icon and comment by tapping the comment icon. Comments appear below the post, and you can see a preview of the first 2 comments directly on the post card. Tap the comment icon to view all comments and add your own. All circle members can like and comment on posts to show support."
        />

        <FAQItem
          question="Why are my charts showing line graphs instead of bars?"
          answer="Health tracking charts (calories, water, sleep, weight) now use line graphs to make it easier to spot trends over time. Line graphs connect data points, making it easier to see if your metrics are improving, declining, or staying stable. Macro charts remain as pie charts since they show proportions rather than trends over time."
        />

        <FAQItem
          question="Why is my magnesium amount showing in red?"
          answer="Magnesium (and other nutrients) only show in red when you significantly exceed the maximum recommended amount (more than 150% of the max). Moderate overages display in normal color. For example, if your max is 500mg and you have 740mg, it will display normally. Only amounts over 750mg would show in red. This prevents unnecessary alarm for slight overages while still alerting you to significant excesses."
        />

        <FAQItem
          question="Why are my weekly habit trends showing old dates?"
          answer="Weekly completion trends now correctly display the most recent 8 weeks with accurate dates. The dates are sorted chronologically, showing the current week at the bottom and older weeks going back in time. If you see incorrect dates, try refreshing the habit progress screen."
        />

        <FAQItem
          question="How do I save meals for quick logging?"
          answer="After logging a meal with AI analysis, you can save it to your Saved Meals collection. Access saved meals from the meal logging screen to quickly log frequently eaten meals without re-analyzing."
        />

        <FAQItem
          question="How does recipe analysis work?"
          answer="From the meal logging screen, tap 'Analyze Recipe' to access the recipe analysis feature. You can take a photo of a recipe, upload a recipe image, or paste recipe text. Enter the number of servings the recipe makes, and Coachie will analyze all ingredients, calculate macros and micronutrients for each ingredient, then provide total nutrition for the entire recipe and per-serving nutrition. You can review ingredients, instructions, and nutrition breakdown before saving to quick save or sharing with friends."
        />

        <FAQItem
          question="Can I analyze recipes from photos?"
          answer="Yes! You can take a photo of a recipe card, cookbook page, or recipe screenshot. The AI will extract all ingredients with quantities, calculate nutrition for each ingredient, and provide full nutrition breakdown. You can also paste recipe text directly if you prefer typing."
        />

        <FAQItem
          question="How does recipe quick save work?"
          answer="After analyzing a recipe, tap 'Save to Quick Save' to save it as a single-serving meal in your Saved Meals. The recipe's nutrition is automatically divided by the number of servings, so when you log it later, you're logging one serving. This makes it easy to track meals from your favorite recipes without re-analyzing each time."
        />

        <FAQItem
          question="Can I share recipes with friends?"
          answer="Yes! After analyzing a recipe, tap 'Share' to open the sharing dialog. Select which friends you want to share the recipe with, and they'll be able to access your recipe with full ingredients, instructions, and nutrition analysis. Shared recipes include all the nutrition data so your friends can easily track the macros and micros."
        />

        <FAQItem
          question="What nutrition information does recipe analysis provide?"
          answer="Recipe analysis calculates total calories, protein, carbs, fat, sugar, and added sugar for the entire recipe and per serving. It also estimates key micronutrients including vitamins (A, C, D, E, K, B-complex), minerals (calcium, iron, magnesium, zinc, etc.), and other essential nutrients. The per-serving values are automatically calculated based on the number of servings you specify."
        />

        <FAQItem
          question="How do I get the most out of Coachie?"
          answer="1) Complete your behavioral profile for personalized suggestions, 2) Log consistently to build accurate patterns, 3) Join a circle for accountability, 4) Engage with circle posts by liking and commenting, 5) Participate in forums and upvote helpful posts to help surface the best content, 6) Review your charts weekly to see trends (line graphs make it easy to spot patterns), 7) Use journaling to extract wins, 8) Try breathing exercises and social media breaks for stress relief, 9) Add friends and use messaging for support, 10) Complete quests and review insights, 11) Enable notifications for reminders and circle updates, 12) Track your vitamins and minerals to ensure you're meeting your daily goals."
        />

        <FAQItem
          question="How do upvotes work in forums?"
          answer="Upvoting forum posts helps surface the most valuable content. Tap the up arrow icon on any forum post to upvote it. Posts are automatically sorted by upvotes (most upvoted first) so you can easily see what the community finds most helpful. You can toggle between 'Top' (sorted by upvotes) and 'New' (sorted by date) using the switch in the forum header. Upvoting is separate from liking - upvotes help determine post visibility, while likes show appreciation."
        />

        <FAQItem
          question="What is Weekly Blueprint?"
          answer="Weekly Blueprint is an AI-powered meal planning feature that generates personalized 7-day meal plans with full recipes and automatically creates organized shopping lists. It considers your dietary preferences (vegan, keto, paleo, etc.), macro goals, allergies, budget, household size, and meal preferences. Access it from the LifeOS Dashboard card or navigate directly to the Weekly Blueprint screen."
        />

        <FAQItem
          question="How do I generate my weekly blueprint?"
          answer="Go to the LifeOS Dashboard and tap the 'Weekly Blueprint' card. If you don't have a blueprint yet, tap 'Generate my blueprint?' and Coachie will create a personalized 7-day meal plan with recipes and shopping list based on your profile settings. The generation takes about 30-60 seconds. You'll see a summary with item count and estimated cost, then tap 'View Full List' to see the detailed shopping list organized by category."
        />

        <FAQItem
          question="How do I customize my meal preferences?"
          answer="Go to Settings and scroll to the 'Weekly Blueprint' section. You can set your preferred number of meals per day (2, 3, or 4) and snacks per day (0, 1, or 2). These preferences are used when generating your weekly blueprint. You can also enable 'Weekly Blueprint Sunday Alert' to get notified when your new blueprint is ready each week."
        />

        <FAQItem
          question="How do meal time reminders work?"
          answer="In Settings, enable 'Daily Meal Reminders' and then set your preferred meal times (breakfast, lunch, dinner, and snacks if applicable). Coachie will send you push notifications before each meal time to remind you to log your meals. Meal reminders only appear if you have snacks enabled and set snack times. You can customize each meal time individually."
        />

        <FAQItem
          question="Can I edit my shopping list?"
          answer="Yes! In the Weekly Blueprint screen, you can mark items as bought by checking the checkbox, edit quantities by tapping on items, add notes for specific items, and regenerate your blueprint if you want a new plan. All changes are saved automatically. You can also share your shopping list as a PNG image or export it as a PDF."
        />

        <FAQItem
          question="How does the blueprint respect my dietary preferences?"
          answer="The Weekly Blueprint uses your dietary preference setting (vegan, vegetarian, keto, paleo, etc.), macro goals (protein, carbs, fat), allergies, favorite foods, and avoided foods from your profile. It also considers your budget level, household size, and cooking time preferences. The AI generates meals and recipes that align with all these constraints."
        />

        <FAQItem
          question="When will I get my weekly blueprint notification?"
          answer="If you enable 'Weekly Blueprint Sunday Alert' in Settings, you'll receive a push notification every Sunday at 7:00 AM ET when your new weekly blueprint is ready. You can also manually generate a new blueprint anytime from the Weekly Blueprint screen by tapping 'Regenerate'."
        />

        <FAQItem
          question="What if I change my meal preferences after generating a blueprint?"
          answer="If you change your meals per day or snacks per day in Settings, the Weekly Blueprint screen will detect this change and prompt you to regenerate your blueprint. This ensures your meal plan always matches your current preferences. You can also manually regenerate anytime."
        />

        <FAQItem
          question="How does serving size adjustment work for blueprints?"
          answer="In the Weekly Blueprint screen, you can adjust the serving size (1-8 people) using the selector. This automatically scales all recipe ingredient quantities and shopping list amounts proportionally. For example, if a recipe calls for '2 cups rice' for 2 servings, selecting 4 servings will update it to '4 cups rice' and adjust the shopping list accordingly. Both recipes and shopping lists update together based on your selected serving size."
        />

        <FAQItem
          question="How does the unit system work in blueprints and briefs?"
          answer="Coachie automatically uses your preferred unit system throughout the app. If you select Imperial in Settings, all measurements in weekly blueprints, morning/afternoon/evening briefs, and AI meal inspiration will use Imperial units (lbs, oz, fl oz, cups, tbsp, tsp, quarts, inches, feet). If you select Metric, all measurements use Metric units (g, kg, ml, L, liters, cm, centimeters). The system strictly enforces your preference - if Imperial is selected, you'll never see metric units, and vice versa."
        />

        <FAQItem
          question="Do habits automatically complete when I log activities?"
          answer="Yes! Coachie automatically completes habits when you log related activities. For example, logging a meal automatically completes 'Eat breakfast' or 'Eat lunch' habits. Logging water completes hydration habits. Logging sleep completes bedtime habits. Logging workouts completes exercise habits. Logging breathing exercises completes breathing habit. This prevents duplicate tracking and makes habit completion seamless. Automatic completion only happens once per day to prevent duplicates."
        />

        <FAQItem
          question="How do circle streaks work?"
          answer="Circle streaks automatically update based on all members' daily activity. When any circle member completes a habit, logs a health entry, or checks in, the circle's streak is recalculated. A 7-day streak means at least one member had activity each day for 7 consecutive days. Circle streaks celebrate collective progress and help maintain accountability across all members. Streaks are updated automatically in real-time as members participate."
        />

        <FAQItem
          question="How does dietary preference enforcement work in blueprints?"
          answer="Weekly Blueprints strictly enforce your dietary preference at both the macro level AND food type level. For example, if you're vegetarian, the blueprint will never include meat, fish, or seafood - it considers both macro ratios AND actual food types. If you're a carnivore, you'll only get meat-based meals. The AI validates generated meals to ensure they comply with your dietary restrictions and regenerates if violations are found."
        />

        <FAQItem
          question="Why do I see the same meals each week in my blueprint?"
          answer="Weekly Blueprints now use enhanced randomization and variety algorithms. Each week, the AI generates new, diverse meals with different cuisines, cooking methods, proteins, and vegetables. If you're seeing repetition, try regenerating your blueprint or adjusting your dietary preferences. The system also rotates through different meal types and ensures unique meal names each week."
        />
      </div>
    </div>
  )
}

