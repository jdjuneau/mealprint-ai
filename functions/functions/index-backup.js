const functions = require('firebase-functions');
const admin = require('firebase-admin');

// Initialize Firebase Admin (only if not already initialized)
if (!admin.apps.length) {
  admin.initializeApp();
}

// CRITICAL: Export createStripeCheckoutSession FIRST before any heavy imports
// This ensures Firebase can detect it quickly before timeout
const { createStripeCheckoutSession } = require('./lib/createStripeCheckoutSession');
exports.createStripeCheckoutSession = createStripeCheckoutSession;

// Import only working TypeScript functions (from compiled lib directory)
const { ensureForumChannels, createThread, replyToThread } = require('./lib/forum');
const { onCirclePostCreated, onCirclePostLiked } = require('./lib/notifications');
const { onFriendRequestCreated, onMessageCreated, onCirclePostCreated: onCirclePostCreatedFCM, onCirclePostCommented } = require('./lib/messageNotifications');
const { calculateReadinessScore, getEnergyScoreHistory } = require('./lib/readinessScore');
const { getScoreHistory } = require('./lib/getScoreHistory');
const { generateMonthlyInsights, generateUserInsights, archiveOldInsights } = require('./lib/generateMonthlyInsights');
const { logOpenAIUsageEvent } = require('./lib/usage');
const { getUserQuests, updateQuestProgress, completeQuest, resetQuests } = require('./lib/getUserQuests');
const { sendMorningBriefs, sendAfternoonBriefs, sendEveningBriefs } = require('./lib/scheduledBriefs');
const { migrateUserUsernames } = require('./lib/migrateUsers');
const { updateUserPlatforms } = require('./lib/updateUserPlatforms');
const { testUpdatePlatforms } = require('./lib/testUpdatePlatforms');
const { testBriefs, testBriefsBatch } = require('./lib/testBriefs');
const { refreshRecipeNutrition } = require('./lib/refreshRecipeNutrition');
// Payment Processing Functions (from compiled lib directory)
const {
  getSubscriptionPlans,
  createPayPalOrder,
  verifyStripePayment,
  verifyPayPalPayment,
  cancelStripeSubscription,
  cancelPayPalSubscription,
  getSubscriptionStatus,
  processStripeWebhook,
  processPayPalWebhook,
  checkStripeConfig,
} = require('./lib/payments');

// Initialize Gemini AI (deferred - only if needed)
let genAI;
function getGenAI() {
  if (!genAI) {
    const { GoogleGenerativeAI } = require('@google/generative-ai');
    genAI = new GoogleGenerativeAI(functions.config().gemini?.api_key || process.env.GEMINI_API_KEY);
  }
  return genAI;
}

/**
 * OLD SCHEDULED FUNCTIONS - DISABLED
 * These have been replaced by sendMorningBriefs, sendAfternoonBriefs, and sendEveningBriefs
 * in scheduledBriefs.ts which send at 8 AM, 2 PM, and 6 PM respectively.
 * 
 * Keeping the sendTimedNudges function below for the testNudge endpoint only.
 */

// DISABLED - Replaced by sendMorningBriefs at 8 AM
// exports.sendMorningNudges = functions.pubsub
//   .schedule('0 8 * * *')
//   .timeZone('America/New_York')
//   .onRun(async (context) => {
//     return await sendTimedNudges('morning');
//   });

// DISABLED - Replaced by sendAfternoonBriefs at 2 PM
// exports.sendMiddayNudges = functions.pubsub
//   .schedule('0 14 * * *')
//   .timeZone('America/New_York')
//   .onRun(async (context) => {
//     return await sendTimedNudges('midday');
//   });

// DISABLED - Replaced by sendEveningBriefs at 6 PM (was 9 PM)
// exports.sendEveningNudges = functions.pubsub
//   .schedule('0 21 * * *')
//   .timeZone('America/New_York')
//   .onRun(async (context) => {
//     return await sendTimedNudges('evening');
//   });

/**
 * Generic function to send nudges based on time of day
 */
async function sendTimedNudges(timeOfDay) {
  console.log(`Starting ${timeOfDay} nudge function at`, new Date().toISOString());

  try {
    const usersWithNudges = await getUsersWithNudgesEnabled();
    console.log(`Found ${usersWithNudges.length} users with nudges enabled`);

    const results = await Promise.allSettled(
      usersWithNudges.map(user => sendTimedNudgeToUser(user, timeOfDay))
    );

    const successful = results.filter(r => r.status === 'fulfilled').length;
    const failed = results.filter(r => r.status === 'rejected').length;

    console.log(`${timeOfDay} nudge sending complete. Successful: ${successful}, Failed: ${failed}`);

    return {
      success: true,
      timeOfDay,
      totalUsers: usersWithNudges.length,
      successful,
      failed
    };

  } catch (error) {
    console.error(`Error in send${timeOfDay}Nudges function:`, error);
    throw new functions.https.HttpsError('internal', `Failed to send ${timeOfDay} nudges`);
  }
}

/**
 * HTTP endpoint to test nudge generation (for development/testing)
 * Call with: POST /testNudge
 * Body: { "userId": "user_id", "timeOfDay": "morning|midday|evening" }
 */
exports.testNudge = functions.https.onCall(async (data, context) => {
  // Only allow authenticated users in production
  // if (!context.auth) {
  //   throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  // }

  try {
    const { userId, timeOfDay = 'morning' } = data;

    console.log(`🧪 Testing nudge for user: ${userId}, time: ${timeOfDay}`);

    // Get user data
    const userData = await getUserFitnessData(userId);

    // Get user info
    const userDoc = await admin.firestore().collection('users').doc(userId).get();
    const user = {
      id: userId,
      ...userDoc.data()
    };

    // Generate nudge
    const nudgeMessage = await generateTimedPersonalizedNudge(user, userData, timeOfDay);

    console.log(`✅ Generated test nudge: "${nudgeMessage}"`);

    return {
      success: true,
      message: nudgeMessage,
      timeOfDay,
      userId
    };

  } catch (error) {
    console.error('❌ Error in test nudge function:', error);
    throw new functions.https.HttpsError('internal', 'Failed to generate test nudge');
  }
});

/**
 * Get all users who have daily nudges enabled
 */
async function getUsersWithNudgesEnabled() {
  const db = admin.firestore();

  try {
    // Query users collection for those with nudges enabled
    // Note: You may need to adjust this based on your user data structure
    const usersSnapshot = await db.collection('users')
      .where('nudgesEnabled', '==', true)
      .get();

    const users = [];
    usersSnapshot.forEach(doc => {
      users.push({
        id: doc.id,
        ...doc.data(),
        fcmToken: doc.data().fcmToken // FCM token for push notifications
      });
    });

    return users;

  } catch (error) {
    console.error('Error fetching users with nudges enabled:', error);
    throw error;
  }
}

/**
 * Send a personalized nudge to a specific user based on time of day
 */
async function sendTimedNudgeToUser(user, timeOfDay) {
  try {
    console.log(`Sending ${timeOfDay} nudge to user: ${user.id}`);

    // Get user's recent fitness data for personalized nudge
    const userData = await getUserFitnessData(user.id);

    // Get user's streak to check if confetti should be shown (only for morning)
    const streak = await getUserStreak(user.id);
    const shouldShowConfetti = timeOfDay === 'morning' && streak && streak.currentStreak >= 3;

    // Generate personalized nudge using SmartCoachEngine logic based on time
    const insightMessage = await generateTimedPersonalizedNudge(user, userData, timeOfDay);

    // Send FCM notification with AI insight and confetti flag
    await sendFCMNotification(user.fcmToken, insightMessage, shouldShowConfetti);

    // Log the nudge in Firestore for analytics
    await logNudgeSent(user.id, insightMessage, shouldShowConfetti, timeOfDay);

    console.log(`Successfully sent ${timeOfDay} insight to user: ${user.id}${shouldShowConfetti ? ' (with confetti!)' : ''}`);
    return { userId: user.id, success: true, confetti: shouldShowConfetti, timeOfDay };

  } catch (error) {
    console.error(`Error sending ${timeOfDay} nudge to user ${user.id}:`, error);
    throw error;
  }
}

/**
 * Get user's recent fitness data for personalization
 * Fetches ALL logs from yesterday for SmartCoachEngine analysis
 */
async function getUserFitnessData(userId) {
  const db = admin.firestore();

  try {
    // Get user's profile
    const userDoc = await db.collection('users').doc(userId).get();
    const userProfile = userDoc.exists ? userDoc.data() : {};

    // Get yesterday's date (for today's insight, analyze yesterday's data)
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    const yesterdayStr = yesterday.toISOString().split('T')[0]; // Format: YYYY-MM-DD

    console.log(`Fetching logs from ${yesterdayStr} for user ${userId}`);

    // Get yesterday's daily log
    const dailyLogDoc = await db.collection('logs')
      .doc(userId)
      .collection('daily')
      .doc(yesterdayStr)
      .get();

    const dailyLog = dailyLogDoc.exists ? dailyLogDoc.data() : null;

    // Get ALL health logs from yesterday (meals, workouts, sleep, water, mood)
    const healthLogsSnapshot = await db.collection('logs')
      .doc(userId)
      .collection('daily')
      .doc(yesterdayStr)
      .collection('entries')
      .get();

    const healthLogs = [];
    healthLogsSnapshot.forEach(doc => {
      healthLogs.push({ id: doc.id, ...doc.data() });
    });

    console.log(`Found ${healthLogs.length} health logs for ${yesterdayStr}`);

    return {
      profile: userProfile,
      dailyLog: dailyLog,
      healthLogs: healthLogs
    };

  } catch (error) {
    console.error('Error getting user fitness data:', error);
    return { profile: {}, dailyLog: null, healthLogs: [] };
  }
}

/**
 * Get user's current streak from Firestore
 */
async function getUserStreak(userId) {
  const db = admin.firestore();

  try {
    const streakDoc = await db.collection('users')
      .doc(userId)
      .collection('streaks')
      .doc('current')
      .get();

    if (streakDoc.exists) {
      return streakDoc.data();
    }

    return null;
  } catch (error) {
    console.error(`Error getting streak for user ${userId}:`, error);
    return null;
  }
}

/**
 * Generate a personalized insight based on time of day using SmartCoachEngine logic
 * Implements pattern detection and Gemini AI integration
 */
async function generateTimedPersonalizedNudge(user, userData, timeOfDay) {
  try {
    const { profile, healthLogs, dailyLog } = userData;

    // Analyze patterns first
    const patterns = analyzeHealthPatterns(healthLogs, dailyLog);

    // Check for specific patterns that need immediate attention
    if (patterns.quickInsight) {
      return patterns.quickInsight;
    }

    // Generate AI-powered insight with full context based on time of day
    const model = getGenAI().getGenerativeModel({ model: 'gemini-pro' });
    const prompt = buildTimedSmartCoachPrompt(healthLogs, profile, patterns, dailyLog, timeOfDay);

    const result = await model.generateContent(prompt);
    const response = await result.response;
    const insightText = response.text().trim();

    // Fallback if AI generation fails
    if (!insightText || insightText.length < 10) {
      return getTimedFallbackInsight(healthLogs, profile, patterns, timeOfDay);
    }

    // Ensure the insight is not too long for notifications
    return insightText.length > 150 ? insightText.substring(0, 147) + '...' : insightText;

  } catch (error) {
    console.error('Error generating insight with SmartCoachEngine:', error);
    const patterns = analyzeHealthPatterns(userData.healthLogs || [], userData.dailyLog);
    return getTimedFallbackInsight(userData.healthLogs || [], userData.profile || {}, patterns, timeOfDay);
  }
}

/**
 * Analyze health patterns from health logs and daily log
 * Returns patterns object with quick insights
 */
function analyzeHealthPatterns(healthLogs, dailyLog) {
  const meals = healthLogs.filter(log => log.type === 'meal');
  const workouts = healthLogs.filter(log => log.type === 'workout');
  const sleepLogs = healthLogs.filter(log => log.type === 'sleep');
  const waterLogs = healthLogs.filter(log => log.type === 'water');
  const moodLogs = healthLogs.filter(log => log.type === 'mood');

  // Calculate totals
  const totalProtein = meals.reduce((sum, meal) => sum + (meal.protein || 0), 0);
  const totalCaloriesBurned = workouts.reduce((sum, workout) => sum + (workout.caloriesBurned || 0), 0);
  const totalWaterFromLogs = waterLogs.reduce((sum, log) => sum + (log.ml || 0), 0);
  const totalWaterFromDaily = dailyLog && dailyLog.water ? dailyLog.water : 0;
  const totalWater = totalWaterFromLogs + totalWaterFromDaily;

  // Get latest sleep
  const latestSleep = sleepLogs.length > 0 ? 
    sleepLogs.reduce((latest, log) => {
      if (!latest || (log.timestamp > latest.timestamp)) return log;
      return latest;
    }, null) : null;
  
  const sleepHours = latestSleep ? 
    ((latestSleep.endTime - latestSleep.startTime) / (1000 * 60 * 60)) : 0;

  // Count habits logged (at least one entry per type)
  const habitsLogged = [
    meals.length > 0,
    workouts.length > 0,
    sleepLogs.length > 0,
    (waterLogs.length > 0 || totalWater > 0),
    moodLogs.length > 0
  ].filter(Boolean).length;

  // Pattern detection
  const hasLowProtein = totalProtein < 50; // Less than 50g protein
  const hasLowSleep = sleepHours < 6.0; // Less than 6 hours
  const hasHighWorkout = totalCaloriesBurned >= 400; // High calorie burn
  const hasLowWater = totalWater < 1000; // Less than 1L water
  const hasMultipleHabits = habitsLogged >= 3;

  // Quick insight for specific patterns
  let quickInsight = null;
  if (hasLowProtein && hasLowSleep) {
    quickInsight = 'Add eggs to breakfast + sleep by 10 PM';
  } else if (hasHighWorkout && hasLowWater) {
    quickInsight = `Hydrate! You burned ${totalCaloriesBurned} cal`;
  } else if (hasMultipleHabits) {
    quickInsight = "You're crushing it! Keep the streak alive";
  }

  return {
    totalProtein,
    totalCaloriesBurned,
    totalWater,
    sleepHours,
    habitsLogged,
    quickInsight,
    hasLowProtein,
    hasLowSleep,
    hasHighWorkout,
    hasLowWater,
    hasMultipleHabits,
    meals,
    workouts,
    sleepLogs,
    waterLogs,
    moodLogs
  };
}

/**
 * Calculate daily scores for motivation
 */
function calculateDailyScores(healthLogs, dailyLog, profile) {
  const meals = healthLogs.filter(log => log.type === 'meal');
  const workouts = healthLogs.filter(log => log.type === 'workout');
  const sleepLogs = healthLogs.filter(log => log.type === 'sleep');
  const waterLogs = healthLogs.filter(log => log.type === 'water');
  const moodLogs = healthLogs.filter(log => log.type === 'mood');
  const journalLogs = healthLogs.filter(log => log.type === 'journal');
  const meditationLogs = healthLogs.filter(log => log.type === 'meditation' || log.type === 'mindful_session');

  // Get goals from profile or use defaults
  const calorieGoal = profile.dailyCalorieGoal || 2000;
  const stepsGoal = profile.dailySteps || 10000;
  const waterGoal = 2000; // ml
  const sleepGoal = 8.0; // hours

  // Calculate Health Score (0-100)
  let healthScore = 0;
  const caloriesConsumed = meals.reduce((sum, meal) => sum + (meal.calories || 0), 0);
  const calorieProgress = Math.min(caloriesConsumed / calorieGoal, 1);
  healthScore += Math.round(calorieProgress * 25);

  // Steps only count if available (yesterday's data for morning nudges)
  const steps = dailyLog?.steps || 0;
  if (steps > 0) {
    const stepsProgress = Math.min(steps / stepsGoal, 1);
    healthScore += Math.round(stepsProgress * 25);
  }

  const totalWater = (dailyLog?.water || 0) + waterLogs.reduce((sum, log) => sum + (log.ml || 0), 0);
  const waterProgress = Math.min(totalWater / waterGoal, 1);
  healthScore += Math.round(waterProgress * 20);

  const latestSleep = sleepLogs.length > 0 ? sleepLogs.reduce((latest, log) => {
    if (!latest || (log.timestamp > latest.timestamp)) return log;
    return latest;
  }, null) : null;
  const sleepHours = latestSleep ? 
    ((latestSleep.endTime - latestSleep.startTime) / (1000 * 60 * 60)) : 0;
  const sleepProgress = Math.min(sleepHours / sleepGoal, 1);
  healthScore += Math.round(sleepProgress * 15);

  if (workouts.length > 0) healthScore += 10;
  if (meals.length > 0 || workouts.length > 0 || sleepLogs.length > 0 || waterLogs.length > 0) healthScore += 5;
  healthScore = Math.min(healthScore, 100);

  // Calculate Wellness Score (0-100)
  let wellnessScore = 0;
  if (moodLogs.length > 0) wellnessScore += 30;
  if (meditationLogs.length > 0) wellnessScore += 25;
  if (journalLogs.length > 0) wellnessScore += 20;
  if (meditationLogs.length > 0) wellnessScore += 15; // Breathing/mindfulness
  wellnessScore = Math.min(wellnessScore, 100);

  // Calculate Daily Score (weighted average)
  const dailyScore = Math.round((healthScore * 0.50) + (wellnessScore * 0.30));

  return {
    healthScore,
    wellnessScore,
    dailyScore,
    healthBreakdown: {
      calories: Math.round(calorieProgress * 100),
      steps: steps > 0 ? Math.round((steps / stepsGoal) * 100) : null,
      water: Math.round(waterProgress * 100),
      sleep: Math.round(sleepProgress * 100),
      workouts: workouts.length > 0
    }
  };
}

/**
 * Build comprehensive prompt for Gemini AI with full context based on time of day
 */
function buildTimedSmartCoachPrompt(healthLogs, profile, patterns, dailyLog, timeOfDay) {
  const name = profile.name || 'there';
  const currentWeight = profile.currentWeight || 'Not set';
  const goalWeight = profile.goalWeight || 'Not set';
  const progress = profile.progressPercentage ? 
    (profile.progressPercentage * 100).toFixed(1) + '%' : '0%';

  // Calculate daily scores
  const scores = calculateDailyScores(healthLogs, dailyLog, profile);

  // Format meals summary
  const mealsSummary = patterns.meals.length > 0 ?
    patterns.meals.map(meal => 
      `- ${meal.foodName || 'Meal'}: ${meal.calories || 0} cal, ${meal.protein || 0}g protein, ${meal.carbs || 0}g carbs, ${meal.fat || 0}g fat`
    ).join('\n') :
    'No meals logged';

  // Format workouts summary
  const workoutsSummary = patterns.workouts.length > 0 ?
    patterns.workouts.map(workout =>
      `- ${workout.workoutType || 'Workout'}: ${workout.durationMin || 0} min, ${workout.caloriesBurned || 0} cal burned, ${workout.intensity || 'Medium'} intensity`
    ).join('\n') :
    'No workouts logged';

  // Format sleep summary
  const sleepSummary = patterns.sleepLogs.length > 0 ?
    `Latest: ${patterns.sleepHours.toFixed(1)} hours, quality ${patterns.sleepLogs[0].quality || 3}/5` :
    'No sleep logged';

  // Format water summary
  const waterSummary = patterns.totalWater > 0 ?
    `${patterns.totalWater}ml (${patterns.waterLogs.length} entries)` :
    'No water logged';

  // Format mood summary
  const moodSummary = patterns.moodLogs.length > 0 ?
    `Latest: ${patterns.moodLogs[0].level || 3}/5` :
    'No mood logged';

  // Steps summary - only include for morning nudges (yesterday's data is available)
  const stepsSummary = timeOfDay === 'morning' && dailyLog?.steps ?
    `Yesterday: ${dailyLog.steps} steps (${Math.round((dailyLog.steps / (profile.dailySteps || 10000)) * 100)}% of goal)` :
    null;

  return `
You are Coachie, an AI fitness coach providing personalized daily insights.

USER PROFILE:
- Name: ${name}
- Current Weight: ${currentWeight}kg
- Goal Weight: ${goalWeight}kg
- Progress: ${progress} towards goal
- Activity Level: ${profile.activityLevel ? profile.activityLevel.replace(/_/g, ' ') : 'Not set'}
- BMI: ${profile.bmi ? profile.bmi.toFixed(1) : 'N/A'} (${profile.bmiCategory || 'N/A'})

YESTERDAY'S ACTIVITY SUMMARY:
- Total Protein: ${patterns.totalProtein}g
- Total Calories Burned: ${patterns.totalCaloriesBurned} cal
- Total Water: ${patterns.totalWater}ml
- Sleep: ${patterns.sleepHours.toFixed(1)} hours
- Habits Logged: ${patterns.habitsLogged}/5
${stepsSummary ? `- ${stepsSummary}` : ''}

DAILY SCORES (for motivation and context):
- Overall Daily Score: ${scores.dailyScore}/100
- Health Tracking Score: ${scores.healthScore}/100
  * Calories: ${scores.healthBreakdown.calories}% of goal
  ${scores.healthBreakdown.steps !== null ? `  * Steps: ${scores.healthBreakdown.steps}% of goal` : ''}
  * Water: ${scores.healthBreakdown.water}% of goal
  * Sleep: ${scores.healthBreakdown.sleep}% of goal
  * Workouts: ${scores.healthBreakdown.workouts ? 'Completed' : 'None logged'}
- Wellness Score: ${scores.wellnessScore}/100

DETAILED LOGS:

MEALS:
${mealsSummary}

WORKOUTS:
${workoutsSummary}

SLEEP:
${sleepSummary}

WATER:
${waterSummary}

MOOD:
${moodSummary}

PATTERNS DETECTED:
- Low Protein: ${patterns.hasLowProtein}
- Low Sleep: ${patterns.hasLowSleep}
- High Workout: ${patterns.hasHighWorkout}
- Low Water: ${patterns.hasLowWater}
- Multiple Habits: ${patterns.hasMultipleHabits}

TIME OF DAY: ${timeOfDay.toUpperCase()}

INSTRUCTIONS:
- Generate a concise, actionable insight (1-2 sentences) appropriate for ${timeOfDay} time
- Be encouraging and positive
- Use the daily scores to provide motivation:
  * If daily score is high (70+): Celebrate their success and encourage maintaining momentum
  * If daily score is medium (40-69): Acknowledge progress and suggest one specific improvement
  * If daily score is low (<40): Be supportive and suggest the easiest win they can achieve today
- For MORNING: Focus on motivation, goal setting, and starting the day right. ${stepsSummary ? 'You can mention yesterday\'s step count as it\'s now available.' : 'Do NOT mention steps as real-time data is not available.'}
- For MIDDAY: Check progress, encourage logging recent activities, mid-day motivation. Do NOT mention steps as real-time data is not available.
- For EVENING: Review the day, celebrate wins, prepare for tomorrow. Do NOT mention steps as real-time data is not available.
- Reference specific data from their logs when relevant
- Provide actionable advice for that time of day
- Use their name for personalization
- Include an emoji if appropriate
- Keep it under 150 characters

Generate the ${timeOfDay} insight:
  `;
}

/**
 * Get time-appropriate fallback insight when AI generation fails
 */
function getTimedFallbackInsight(healthLogs, profile, patterns, timeOfDay) {
  const name = profile.name || 'there';

  if (timeOfDay === 'morning') {
    if (patterns.hasLowProtein && patterns.hasLowSleep) {
      return `Good morning ${name}! Start with protein + aim for 8 hours sleep tonight 💪`;
    }
    if (patterns.hasMultipleHabits) {
      return `Morning ${name}! You're on fire - let's keep that momentum going 🔥`;
    }
    return `Good morning ${name}! Ready to crush your goals today? 🌅`;
  }

  if (timeOfDay === 'midday') {
    if (patterns.habitsLogged === 0) {
      return `Afternoon ${name}! Time to log your meals and workouts 📝`;
    }
    if (patterns.hasLowWater) {
      return `Midday check-in ${name}! Don't forget to stay hydrated 🚰`;
    }
    return `How's your day going ${name}? Keep up the great habits! 💪`;
  }

  if (timeOfDay === 'evening') {
    if (patterns.hasLowSleep) {
      return `Evening ${name}! Wind down early for better rest tonight 🌙`;
    }
    if (patterns.hasMultipleHabits) {
      return `Evening review ${name}! You had a productive day - well done! 🎉`;
    }
    return `Evening ${name}! Reflect on your wins and prepare for tomorrow 🌟`;
  }

  // Generic fallback
  return `Keep up the great work, ${name}! Consistency is key 💪`;
}

/**
 * Create a personalized prompt for Gemini AI (legacy - kept for compatibility)
 */
function createNudgePrompt(user, userData) {
  const { profile, recentLogs } = userData;

  const name = profile.name || user.displayName || 'there';
  const goalWeight = profile.goalWeight;
  const currentWeight = profile.currentWeight;

  // Analyze recent activity
  const hasRecentLogs = recentLogs.length > 0;
  const recentWeights = recentLogs.filter(log => log.weight).map(log => log.weight);
  const avgWeight = recentWeights.length > 0 ?
    recentWeights.reduce((a, b) => a + b, 0) / recentWeights.length : null;

  const recentSteps = recentLogs.filter(log => log.steps).map(log => log.steps);
  const avgSteps = recentSteps.length > 0 ?
    recentSteps.reduce((a, b) => a + b, 0) / recentSteps.length : null;

  return `
You are Coachie, a friendly and encouraging AI fitness coach. Create a personalized morning motivation message.

USER PROFILE:
- Name: ${name}
- Goal Weight: ${goalWeight ? goalWeight + 'kg' : 'Not set'}
- Current Weight: ${currentWeight ? currentWeight + 'kg' : 'Not logged'}
- Recent Activity: ${hasRecentLogs ? 'Active' : 'Limited data'}

RECENT STATS:
- Average weight (7 days): ${avgWeight ? avgWeight.toFixed(1) + 'kg' : 'No data'}
- Average steps (7 days): ${avgSteps ? Math.round(avgSteps) + ' steps' : 'No data'}
- Days with logged activity: ${recentLogs.length}/7

INSTRUCTIONS:
- Keep message under 150 characters
- Be encouraging and positive
- Reference their actual data when available
- Focus on starting the day right
- Use their name for personalization
- Include an emoji
- End with motivation

Create a morning nudge:
  `;
}

/**
 * Get a fallback nudge when AI generation fails
 */
function getFallbackNudge(user) {
  const name = user.displayName || user.name || 'there';
  const nudges = [
    `Good morning ${name}! 🌅 Ready to crush your goals today?`,
    `Rise and shine ${name}! 💪 Your fitness journey continues!`,
    `Morning ${name}! ☀️ Every healthy choice today builds momentum.`,
    `Hey ${name}! 🌟 Today is another opportunity to get closer to your goals!`,
    `Good morning ${name}! 💪 Start strong and stay consistent!`
  ];

  return nudges[Math.floor(Math.random() * nudges.length)];
}

/**
 * Send FCM notification to user with AI insight and optional confetti
 */
async function sendFCMNotification(fcmToken, insightMessage, showConfetti = false) {
  if (!fcmToken) {
    throw new Error('No FCM token available for user');
  }

  // Determine deep link based on message content
  let deepLink = undefined;
  let screen = undefined;
  
  const messageLower = insightMessage.toLowerCase();
  if (messageLower.includes('reflect') || messageLower.includes('journal') || messageLower.includes('reflection')) {
    deepLink = 'coachie://journal_flow';
    screen = 'Journal';
  } else if (messageLower.includes('habit')) {
    deepLink = 'coachie://habits';
    screen = 'Habits';
  } else if (messageLower.includes('meal') || messageLower.includes('food') || messageLower.includes('log')) {
    deepLink = 'coachie://meal_log';
    screen = 'MealLog';
  } else if (messageLower.includes('water') || messageLower.includes('hydrate')) {
    deepLink = 'coachie://water_log';
    screen = 'WaterLog';
  } else if (messageLower.includes('goal')) {
    deepLink = 'coachie://set_goals';
    screen = 'Goals';
  } else if (messageLower.includes('blueprint') || messageLower.includes('weekly')) {
    deepLink = 'coachie://weekly_blueprint';
    screen = 'WeeklyBlueprint';
  }
  
  const message = {
    token: fcmToken,
    notification: {
      title: 'Coachie',
      body: insightMessage
    },
    data: {
      type: 'daily_insight',
      showConfetti: showConfetti ? 'true' : 'false',
      ...(deepLink && { deepLink }),
      ...(screen && { screen }),
    },
    android: {
      priority: 'high',
      notification: {
        sound: 'default',
        channel_id: 'coachie_nudges'
      }
    },
    apns: {
      payload: {
        aps: {
          sound: 'default'
        }
      }
    }
  };

  try {
    const response = await admin.messaging().send(message);
    console.log(`FCM notification sent successfully${showConfetti ? ' with confetti' : ''}:`, response);
    return response;
  } catch (error) {
    console.error('Error sending FCM notification:', error);
    throw error;
  }
}

/**
 * Log the insight that was sent for analytics
 */
async function logNudgeSent(userId, insightMessage, showConfetti = false, timeOfDay = 'morning') {
  const db = admin.firestore();

  try {
    await db.collection('nudge_logs').add({
      userId: userId,
      message: insightMessage,
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
      type: 'daily_insight',
      timeOfDay: timeOfDay,
      deliveryMethod: 'fcm',
      showConfetti: showConfetti
    });
  } catch (error) {
    console.error('Error logging insight:', error);
    // Don't throw - logging failure shouldn't stop the insight
  }
}

// Export only working functions
// Forum
exports.ensureForumChannels = ensureForumChannels;
exports.createThread = createThread;
exports.replyToThread = replyToThread;
// Notifications
exports.onCirclePostCreated = onCirclePostCreated;
exports.onCirclePostLiked = onCirclePostLiked;
// Message and friend request notifications (FCM)
exports.onFriendRequestCreated = onFriendRequestCreated;
exports.onMessageCreated = onMessageCreated;
exports.onCirclePostCreatedFCM = onCirclePostCreatedFCM;
exports.onCirclePostCommented = onCirclePostCommented;
// Readiness Score
exports.calculateReadinessScore = calculateReadinessScore;
exports.getEnergyScoreHistory = getEnergyScoreHistory;
exports.getScoreHistory = getScoreHistory;
// Insights
exports.generateMonthlyInsights = generateMonthlyInsights;
exports.generateUserInsights = generateUserInsights;
exports.archiveOldInsights = archiveOldInsights;
// Quests
exports.getUserQuests = getUserQuests;
exports.updateQuestProgress = updateQuestProgress;
exports.completeQuest = completeQuest;
exports.resetQuests = resetQuests;
// Usage logging
exports.logOpenAIUsageEvent = logOpenAIUsageEvent;
// Briefs - NEW scheduled functions (8 AM, 2 PM, 6 PM)
exports.sendMorningBriefs = sendMorningBriefs;
exports.sendAfternoonBriefs = sendAfternoonBriefs;
exports.sendEveningBriefs = sendEveningBriefs;
// Migration
exports.migrateUserUsernames = migrateUserUsernames;
exports.updateUserPlatforms = updateUserPlatforms;
exports.testUpdatePlatforms = testUpdatePlatforms;
// Test Brief Functions
exports.testBriefs = testBriefs;
exports.testBriefsBatch = testBriefsBatch;
// Weekly Blueprint (v2 functions - need to import from compiled lib)
const { generateWeeklyShoppingList, generateWeeklyBlueprint } = require('./lib/generateWeeklyShoppingList');
exports.generateWeeklyShoppingList = generateWeeklyShoppingList;

// GDPR/CCPA Compliance Functions
const { exportUserData } = require('./lib/exportUserData');
exports.exportUserData = exportUserData;
exports.generateWeeklyBlueprint = generateWeeklyBlueprint;
// Recipe Analysis
const { analyzeRecipe } = require('./lib/analyzeRecipe');
exports.analyzeRecipe = analyzeRecipe;
// Subscription - Purchase Verification
const { verifyPurchase } = require('./lib/verifyPurchase');
exports.verifyPurchase = verifyPurchase;
// Refresh Recipe Nutrition
exports.refreshRecipeNutrition = refreshRecipeNutrition;
// Supplement Search
const { searchSupplement } = require('./lib/searchSupplement');
exports.searchSupplement = searchSupplement;
// Generate Brief (callable and internal)
const { generateBrief, generateBriefInternal } = require('./lib/generateBrief');
exports.generateBrief = generateBrief;

// Subscription - Test Functions (for testing with existing users)
// ⚠️⚠️⚠️ CRITICAL PRODUCTION REMINDER ⚠️⚠️⚠️
// BEFORE DEPLOYING TO PRODUCTION:
// 1. Open functions/src/grantTestSubscription.ts
// 2. Add your Firebase Auth User ID to adminUids array
// 3. Uncomment admin checks in both functions
// 4. Test that only admins can grant subscriptions
// 5. See PRODUCTION_DEPLOYMENT_CHECKLIST.md for full checklist
// ⚠️⚠️⚠️ DO NOT DEPLOY WITHOUT SECURING THESE FUNCTIONS ⚠️⚠️⚠️
const { grantTestSubscription, grantProToAllExistingUsers } = require('./lib/grantTestSubscription');
exports.grantTestSubscription = grantTestSubscription;
exports.grantProToAllExistingUsers = grantProToAllExistingUsers;
// Payment Processing Functions
exports.getSubscriptionPlans = getSubscriptionPlans;
// createStripeCheckoutSession already exported at top of file
exports.createPayPalOrder = createPayPalOrder;
exports.verifyStripePayment = verifyStripePayment;
exports.verifyPayPalPayment = verifyPayPalPayment;
exports.cancelStripeSubscription = cancelStripeSubscription;
exports.cancelPayPalSubscription = cancelPayPalSubscription;
exports.getSubscriptionStatus = getSubscriptionStatus;
exports.processStripeWebhook = processStripeWebhook;
exports.processPayPalWebhook = processPayPalWebhook;
exports.checkStripeConfig = checkStripeConfig;
// OLD scheduled functions are DISABLED (commented out above) - do not export:
// - sendMorningNudges (replaced by sendMorningBriefs)
// - sendMiddayNudges (replaced by sendAfternoonBriefs)
// - sendEveningNudges (replaced by sendEveningBriefs at 6 PM instead of 9 PM)
