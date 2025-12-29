import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import OpenAI from 'openai';
import { Expo, ExpoPushMessage, ExpoPushToken } from 'expo-server-sdk';
import { logUsage } from './usage';

// Initialize Firebase Admin
if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

// Initialize OpenAI (you can switch to Groq if preferred)
const openai = new OpenAI({
  // Migration: process.env first (future-proof), functions.config() as fallback
  apiKey: process.env.OPENAI_API_KEY || functions.config().openai?.key || '',
});

// Initialize Expo for push notifications
const expo = new Expo();

// =============================================================================
// MASTER SYSTEM PROMPT - REPLACE WITH YOUR ACTUAL PROMPT
// =============================================================================
const MASTER_SYSTEM_PROMPT = `
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
`;

// =============================================================================
// FUNCTION SCHEMA - REPLACE WITH YOUR ACTUAL SCHEMA
// =============================================================================
const GENERATE_TINY_HABIT_PRESCRIPTION_SCHEMA = {
  type: "function",
  function: {
    name: "generate_tiny_habit_prescription",
    description: "Generate 1-3 personalized tiny habits based on user's current state",
    parameters: {
      type: "object",
      properties: {
        habits: {
          type: "array",
          description: "Array of 1-3 tiny habits to prescribe",
          items: {
            type: "object",
            properties: {
              title: {
                type: "string",
                description: "Brief, actionable habit title (max 50 chars)"
              },
              description: {
                type: "string",
                description: "Encouraging explanation of why this habit helps"
              },
              category: {
                type: "string",
                enum: ["HEALTH", "FITNESS", "NUTRITION", "SLEEP", "HYDRATION", "PRODUCTIVITY", "MINDFULNESS", "SOCIAL", "LEARNING", "CUSTOM"],
                description: "Habit category"
              },
              targetValue: {
                type: "number",
                description: "Quantifiable target (e.g., 5 for 5 minutes, 8 for 8 oz water)"
              },
              unit: {
                type: "string",
                description: "Unit of measurement (e.g., 'minutes', 'oz', 'steps')"
              },
              priority: {
                type: "string",
                enum: ["LOW", "MEDIUM", "HIGH", "CRITICAL"],
                description: "Habit priority level"
              },
              rationale: {
                type: "string",
                description: "Why this specific habit suits the user's current state"
              },
              expectedDifficulty: {
                type: "string",
                enum: ["VERY_EASY", "EASY", "MODERATE", "CHALLENGING"],
                description: "Expected difficulty level"
              }
            },
            required: ["title", "description", "category", "targetValue", "unit", "priority", "rationale", "expectedDifficulty"]
          },
          minItems: 1,
          maxItems: 3
        },
        personalizedMessage: {
          type: "string",
          description: "Personalized morning message explaining today's habit focus"
        },
        motivation: {
          type: "string",
          description: "Brief motivational quote or insight"
        }
      },
      required: ["habits", "personalizedMessage", "motivation"]
    }
  }
};

// =============================================================================
// DATA INTERFACES
// =============================================================================
interface SleepData {
  durationHours: number;
  quality: number; // 1-5 scale
  deepSleepHours?: number;
  remSleepHours?: number;
  hrv?: number;
  restlessMinutes?: number;
}

interface UserProfile {
  fourTendencies?: {
    tendency: string;
    scores: Record<string, number>;
  };
  rewardPreferences: string[];
  keystoneHabits: string[];
  biggestFrictions: string[];
  habitGoals: Record<string, any>;
}

interface TinyHabitPrescription {
  habits: Array<{
    title: string;
    description: string;
    category: string;
    targetValue: number;
    unit: string;
    priority: string;
    rationale: string;
    expectedDifficulty: string;
  }>;
  personalizedMessage: string;
  motivation: string;
}

// =============================================================================
// MAIN FUNCTION - HTTPS CALLABLE
// =============================================================================
export const generateDailyHabits = functions.https.onCall(async (data, context) => {
  // Verify authentication
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = data.userId || context.auth.uid;
  if (!userId) {
    throw new functions.https.HttpsError('invalid-argument', 'User ID is required');
  }

  try {
    // Generate daily habits for the user
    const result = await generateHabitsForUser(userId);

    return {
      success: true,
      message: 'Daily habits generated successfully',
      habitsCreated: result.habitsCreated,
      notificationSent: result.notificationSent
    };
  } catch (error) {
    console.error('Error generating daily habits:', error);
    throw new functions.https.HttpsError('internal', 'Failed to generate daily habits');
  }
});

// =============================================================================
// CLOUD SCHEDULER VERSION (runs daily)
// =============================================================================
export const generateDailyHabitsScheduled = functions.pubsub
  .schedule('0 7 * * *') // 7 AM daily
  .timeZone('America/New_York')
  .onRun(async (context) => {
    console.log('Running scheduled daily habit generation...');

    try {
      // Get all active users (limit to reduce API costs)
      const usersSnapshot = await db.collection('users').limit(50).get(); // Reduced from 100 to limit API calls

      let totalProcessed = 0;
      let totalHabitsCreated = 0;
      let totalNotificationsSent = 0;
      let totalSkipped = 0;

      // Process users with rate limiting to avoid overwhelming the API
      for (const userDoc of usersSnapshot.docs) {
        const userId = userDoc.id;
        console.log(`Processing user: ${userId}`);

        try {
          const result = await generateHabitsForUser(userId);
          totalHabitsCreated += result.habitsCreated;
          totalNotificationsSent += result.notificationSent ? 1 : 0;
          if (result.habitsCreated === 0) {
            totalSkipped++;
          }
          totalProcessed++;

          // Add small delay between users to avoid rate limits
          await new Promise(resolve => setTimeout(resolve, 200)); // 200ms delay
        } catch (error) {
          console.error(`Failed to process user ${userId}:`, error);
        }
      }

      console.log(`Daily habit generation completed: ${totalProcessed} users processed, ${totalHabitsCreated} habits created, ${totalNotificationsSent} notifications sent, ${totalSkipped} skipped (already had habits)`);

    } catch (error) {
      console.error('Error in scheduled habit generation:', error);
      throw error;
    }
  });

// =============================================================================
// CORE LOGIC
// =============================================================================
async function generateHabitsForUser(userId: string): Promise<{
  habitsCreated: number;
  notificationSent: boolean;
}> {
  console.log(`Generating habits for user: ${userId}`);

  // Check if habits were already generated today (cache check)
  const today = new Date().toISOString().split('T')[0]; // YYYY-MM-DD
  const todayHabitsSnapshot = await db.collection('users').doc(userId).collection('habits')
    .where('createdAt', '>=', admin.firestore.Timestamp.fromDate(new Date(today)))
    .where('createdAt', '<', admin.firestore.Timestamp.fromDate(new Date(new Date(today).getTime() + 24 * 60 * 60 * 1000)))
    .limit(1)
    .get();

  if (!todayHabitsSnapshot.empty) {
    console.log(`Habits already generated today for user ${userId}, skipping to save API costs`);
    return {
      habitsCreated: 0,
      notificationSent: false
    };
  }

  // 1. Fetch user profile
  const userProfile = await getUserProfile(userId);
  console.log('User profile:', userProfile);

  // 2. Fetch last night's sleep data
  const sleepData = await getLastNightSleepData(userId);
  console.log('Sleep data:', sleepData);

  // 3. Get user's existing habits for context
  const existingHabits = await getExistingHabits(userId);
  console.log(`User has ${existingHabits.length} existing habits`);

  // 4. Generate AI-powered habit prescription
  const prescription = await generateHabitPrescription(userProfile, sleepData, existingHabits, userId);
  console.log('AI prescription:', prescription);

  // 5. Create the habits in Firestore
  const habitsCreated = await createHabitsInFirestore(userId, prescription.habits);
  console.log(`Created ${habitsCreated} habits`);

  // 6. Send push notification
  const notificationSent = await sendMorningNotification(userId, prescription);
  console.log(`Notification sent: ${notificationSent}`);

  return {
    habitsCreated,
    notificationSent
  };
}

// =============================================================================
// DATA FETCHING FUNCTIONS
// =============================================================================
async function getUserProfile(userId: string): Promise<UserProfile | null> {
  try {
    const doc = await db.collection('users').doc(userId).collection('profile').doc('habits').get();
    if (doc.exists) {
      return doc.data() as UserProfile;
    }
    return null;
  } catch (error) {
    console.error('Error fetching user profile:', error);
    return null;
  }
}

async function getLastNightSleepData(userId: string): Promise<SleepData | null> {
  try {
    // Calculate last night's date range (assuming function runs in morning)
    const now = new Date();
    const yesterday = new Date(now);
    yesterday.setDate(yesterday.getDate() - 1);

    // Look for sleep data from yesterday evening to this morning
    const startTime = new Date(yesterday.getFullYear(), yesterday.getMonth(), yesterday.getDate(), 20, 0, 0); // 8 PM yesterday
    const endTime = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 8, 0, 0); // 8 AM today

    // Query health logs for sleep data (assuming your existing structure)
    const sleepLogs = await db.collection('users').doc(userId).collection('healthLogs')
      .where('type', '==', 'SLEEP_LOG')
      .where('timestamp', '>=', admin.firestore.Timestamp.fromDate(startTime))
      .where('timestamp', '<=', admin.firestore.Timestamp.fromDate(endTime))
      .orderBy('timestamp', 'desc')
      .limit(5)
      .get();

    if (!sleepLogs.empty) {
      // Calculate aggregate sleep data
      let totalDuration = 0;
      let totalQuality = 0;
      let count = 0;

      sleepLogs.forEach(doc => {
        const data = doc.data();
        if (data.durationHours) {
          totalDuration += data.durationHours;
          totalQuality += data.quality || 3;
          count++;
        }
      });

      if (count > 0) {
        return {
          durationHours: totalDuration / count, // Average if multiple entries
          quality: Math.round(totalQuality / count)
        };
      }
    }

    // Fallback: return reasonable defaults if no data
    console.log('No sleep data found, using defaults');
    return {
      durationHours: 7.5,
      quality: 3
    };

  } catch (error) {
    console.error('Error fetching sleep data:', error);
    return {
      durationHours: 7.5,
      quality: 3
    };
  }
}

async function getExistingHabits(userId: string): Promise<any[]> {
  try {
    const habitsSnapshot = await db.collection('users').doc(userId).collection('habits')
      .where('isActive', '==', true)
      .limit(10)
      .get();

    return habitsSnapshot.docs.map(doc => ({
      id: doc.id,
      ...doc.data()
    }));
  } catch (error) {
    console.error('Error fetching existing habits:', error);
    return [];
  }
}

// =============================================================================
// AI GENERATION FUNCTIONS
// =============================================================================
async function generateHabitPrescription(
  userProfile: UserProfile | null,
  sleepData: SleepData | null,
  existingHabits: any[],
  userId?: string
): Promise<TinyHabitPrescription> {
  try {
    // Build context for AI
    const context = {
      userProfile: userProfile || {},
      sleepData: sleepData || { durationHours: 7.5, quality: 3 },
      existingHabits: existingHabits.slice(0, 5), // Limit for token efficiency
      currentTime: new Date().toISOString(),
      timeOfDay: new Date().getHours() < 12 ? 'morning' : 'afternoon'
    };

    const prompt = `Generate 1-3 tiny habits for user. Sleep: ${sleepData?.durationHours || '7.5'}h (quality ${sleepData?.quality || '3'}/5). Tendency: ${userProfile?.fourTendencies?.tendency || 'unknown'}. Time: ${context.timeOfDay}. Active habits: ${existingHabits.length}. Make habits tiny, actionable, and aligned with current energy state.`;

    const completion = await openai.chat.completions.create({
      model: 'gpt-4o-mini', // Switched from gpt-4o to reduce costs by ~10x
      messages: [
        { role: 'system', content: MASTER_SYSTEM_PROMPT },
        { role: 'user', content: prompt }
      ],
      tools: [{
        type: 'function',
        function: GENERATE_TINY_HABIT_PRESCRIPTION_SCHEMA.function
      }],
      tool_choice: { type: 'function', function: { name: 'generate_tiny_habit_prescription' } },
      temperature: 0.7,
      max_tokens: 800 // Reduced from 1000 to save on output tokens
    });

    const toolCall = completion.choices[0]?.message?.tool_calls?.[0];
    if (!toolCall) {
      throw new Error('No tool call returned from AI');
    }

    const prescription = JSON.parse(toolCall.function.arguments) as TinyHabitPrescription;
    
    // Log usage for daily habits generation
    try {
      const now = new Date();
      const date = now.toISOString().split('T')[0];
      await logUsage({
        userId: userId || 'system',
        date,
        timestamp: now.getTime(),
        source: 'generateDailyHabits',
        model: 'gpt-4o-mini',
        promptTokens: completion.usage?.prompt_tokens,
        completionTokens: completion.usage?.completion_tokens,
        totalTokens: completion.usage?.total_tokens,
        metadata: { habitCount: prescription.habits.length }
      });
    } catch (logError) {
      console.error('Failed to log daily habits generation usage:', logError);
    }
    
    return prescription;

  } catch (error) {
    console.error('Error generating AI prescription:', error);

    // Fallback prescription if AI fails
    return {
      habits: [{
        title: "Take 3 deep breaths",
        description: "A tiny mindfulness practice to center yourself",
        category: "MINDFULNESS",
        targetValue: 3,
        unit: "breaths",
        priority: "MEDIUM",
        rationale: "Based on your sleep quality, this gentle practice will help you start your day calmly",
        expectedDifficulty: "VERY_EASY"
      }],
      personalizedMessage: "Good morning! Based on your recent activity, here's a gentle habit to start your day.",
      motivation: "Small steps lead to big changes."
    };
  }
}

// =============================================================================
// FIRESTORE OPERATIONS
// =============================================================================
async function createHabitsInFirestore(userId: string, habits: any[]): Promise<number> {
  try {
    const batch = db.batch();
    let created = 0;

    for (const habitData of habits.slice(0, 3)) { // Max 3 habits
      const habitRef = db.collection('users').doc(userId).collection('habits').doc();

      const habit = {
        ...habitData,
        userId,
        isActive: true,
        streakCount: 0,
        longestStreak: 0,
        totalCompletions: 0,
        successRate: 0,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      };

      batch.set(habitRef, habit);
      created++;
    }

    await batch.commit();
    return created;

  } catch (error) {
    console.error('Error creating habits in Firestore:', error);
    throw error;
  }
}

// =============================================================================
// PUSH NOTIFICATIONS
// =============================================================================
async function sendMorningNotification(userId: string, prescription: TinyHabitPrescription): Promise<boolean> {
  try {
    // Get user's push token (assuming you store it in user profile)
    const userDoc = await db.collection('users').doc(userId).get();
    const userData = userDoc.data();

    if (!userData?.expoPushToken) {
      console.log('No push token found for user:', userId);
      return false;
    }

    const pushToken = userData.expoPushToken as string;

    // Validate token
    if (!Expo.isExpoPushToken(pushToken)) {
      console.error('Invalid Expo push token:', pushToken);
      return false;
    }

    // Create rich notification
    const message: ExpoPushMessage = {
      to: pushToken,
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
      expiration: Math.floor(Date.now() / 1000) + 86400,
      badge: prescription.habits.length
    };

    // Send notification
    const ticket = await expo.sendPushNotificationsAsync([message]);
    console.log('Push notification sent:', ticket);

    return ticket[0]?.status === 'ok';

  } catch (error) {
    console.error('Error sending push notification:', error);
    return false;
  }
}
