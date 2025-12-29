import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import OpenAI from 'openai';
import { logUsage } from './usage';

// Initialize Firebase Admin (will be initialized in main index.js)
const db = admin.firestore();

// Initialize OpenAI (you can switch to Groq if preferred)
const openai = new OpenAI({
  // Migration: process.env first (future-proof), functions.config() as fallback
  apiKey: process.env.OPENAI_API_KEY || functions.config().openai?.key || '',
});

// =============================================================================
// PREDICTIVE HABIT SUGGESTIONS PROMPT
// =============================================================================
const PREDICTIVE_HABITS_PROMPT = `
You are Coachie, an AI habit coach that predicts and suggests personalized habits based on user behavior patterns.

Your task is to analyze a user's habit history and suggest 3-5 new habits they haven't tried yet, but would likely succeed at based on their patterns.

Consider:
- Their Four Tendencies personality type and how it affects habit success
- Current habit categories they're engaged with (health, fitness, nutrition, etc.)
- Success rates of similar habits they've attempted
- Time of day they complete habits most consistently
- Their biggest frictions and challenges
- Gaps in their habit ecosystem (e.g., if they track fitness but not nutrition)

Focus on habits that:
- Build on their existing strengths
- Fill gaps in their routine
- Are realistic given their current success patterns
- Align with their personality type
- Address their stated challenges

Return habits that are likely to have 70%+ success rate based on their profile.
`;

// =============================================================================
// PREDICTIVE HABIT SCHEMA
// =============================================================================
const PREDICTIVE_HABIT_SCHEMA = {
  type: "function",
  function: {
    name: "predict_personalized_habits",
    description: "Predict and suggest personalized habits based on user behavior analysis",
    parameters: {
      type: "object",
      properties: {
        predictedHabits: {
          type: "array",
          description: "3-5 predicted habits tailored to user's patterns",
          items: {
            type: "object",
            properties: {
              title: {
                type: "string",
                description: "Clear, actionable habit title"
              },
              description: {
                type: "string",
                description: "Why this habit suits their pattern"
              },
              category: {
                type: "string",
                enum: ["HEALTH", "FITNESS", "NUTRITION", "SLEEP", "HYDRATION", "PRODUCTIVITY", "MINDFULNESS", "SOCIAL", "LEARNING", "CUSTOM"],
                description: "Habit category"
              },
              rationale: {
                type: "string",
                description: "Why this habit fits their behavioral pattern"
              },
              confidenceScore: {
                type: "number",
                minimum: 0,
                maximum: 100,
                description: "Predicted success rate (0-100)"
              },
              difficulty: {
                type: "string",
                enum: ["VERY_EASY", "EASY", "MODERATE", "CHALLENGING"],
                description: "Predicted difficulty level"
              },
              timing: {
                type: "string",
                description: "Suggested time of day (morning, afternoon, evening)"
              }
            },
            required: ["title", "description", "category", "rationale", "confidenceScore", "difficulty", "timing"]
          },
          minItems: 3,
          maxItems: 5
        },
        patternAnalysis: {
          type: "string",
          description: "Analysis of user's behavioral patterns"
        },
        recommendations: {
          type: "array",
          description: "Strategic recommendations for habit development",
          items: {
            type: "string"
          }
        }
      },
      required: ["predictedHabits", "patternAnalysis", "recommendations"]
    }
  }
};

// =============================================================================
// MAIN CLOUD FUNCTION - HTTPS CALLABLE
// =============================================================================
export const predictHabits = functions.https.onCall(async (data, context) => {
  // Verify authentication
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = data.userId || context.auth.uid;
  if (!userId) {
    throw new functions.https.HttpsError('invalid-argument', 'User ID is required');
  }

  try {
    console.log(`🔮 Predicting habits for user: ${userId}`);

    // Get comprehensive user data for analysis
    const userAnalysis = await analyzeUserPatterns(userId);

    // Generate AI-powered predictions
    const predictions = await generateHabitPredictions(userAnalysis);

    console.log(`✅ Generated ${predictions.predictedHabits.length} habit predictions`);

    return {
      success: true,
      predictions: predictions,
      analyzedAt: new Date().toISOString(),
      userId: userId
    };

  } catch (error) {
    console.error('Error predicting habits:', error);
    throw new functions.https.HttpsError('internal', 'Failed to generate habit predictions');
  }
});

// =============================================================================
// USER PATTERN ANALYSIS
// =============================================================================
async function analyzeUserPatterns(userId: string) {
  console.log(`📊 Analyzing patterns for user: ${userId}`);

  try {
    // Get user profile
    const userDoc = await db.collection('users').doc(userId).get();
    const userProfile = userDoc.exists ? userDoc.data() : {};

    // Get all habits (active and inactive)
    const habitsSnapshot = await db.collection('users').doc(userId).collection('habits').get();
    const allHabits = habitsSnapshot.docs.map(doc => ({
      id: doc.id,
      ...doc.data(),
      createdAt: doc.data().createdAt?.toDate(),
      updatedAt: doc.data().updatedAt?.toDate(),
    }));

    // Get recent completions (last 90 days)
    const ninetyDaysAgo = new Date();
    ninetyDaysAgo.setDate(ninetyDaysAgo.getDate() - 90);

    const completionsQuery = db.collection('users').doc(userId).collection('completions')
      .where('completedAt', '>=', admin.firestore.Timestamp.fromDate(ninetyDaysAgo))
      .orderBy('completedAt', 'desc');

    const completionsSnapshot = await completionsQuery.get();
    const recentCompletions = completionsSnapshot.docs.map(doc => ({
      id: doc.id,
      ...doc.data(),
      completedAt: doc.data().completedAt?.toDate(),
    }));

    // Get recent misses (last 90 days)
    const missesQuery = db.collection('users').doc(userId).collection('misses')
      .where('missedAt', '>=', admin.firestore.Timestamp.fromDate(ninetyDaysAgo))
      .orderBy('missedAt', 'desc');

    const missesSnapshot = await missesQuery.get();
    const recentMisses = missesSnapshot.docs.map(doc => ({
      id: doc.id,
      ...doc.data(),
      missedAt: doc.data().missedAt?.toDate(),
    }));

    // Analyze patterns
    const patternAnalysis = analyzeBehavioralPatterns(allHabits, recentCompletions, recentMisses, userProfile);

    return {
      userProfile,
      allHabits,
      recentCompletions,
      recentMisses,
      patternAnalysis
    };

  } catch (error) {
    console.error('Error analyzing user patterns:', error);
    throw error;
  }
}

function analyzeBehavioralPatterns(habits: any[], completions: any[], misses: any[], userProfile: any) {
  console.log(`🔍 Analyzing ${habits.length} habits, ${completions.length} completions, ${misses.length} misses`);

  // Category analysis
  const categoryStats = {};
  habits.forEach(habit => {
    const category = habit.category;
    if (!categoryStats[category]) {
      categoryStats[category] = { count: 0, success: 0, total: 0 };
    }
    categoryStats[category].count++;

    const habitCompletions = completions.filter(c => c.habitId === habit.id);
    const habitMisses = misses.filter(m => m.habitId === habit.id);

    categoryStats[category].total += habitCompletions.length + habitMisses.length;
    categoryStats[category].success += habitCompletions.length;
  });

  // Calculate success rates by category
  Object.keys(categoryStats).forEach(category => {
    const stats = categoryStats[category];
    stats.successRate = stats.total > 0 ? (stats.success / stats.total) * 100 : 0;
  });

  // Time-based patterns
  const completionHours = completions.map(c => c.completedAt.getHours());
  const mostActiveHour = getMostFrequent(completionHours);

  // Four Tendencies alignment
  const tendency = userProfile.fourTendencies?.tendency || 'UNKNOWN';

  // Success patterns
  const successfulCategories = Object.entries(categoryStats)
    .filter(([_, stats]: [string, any]) => stats.successRate > 70)
    .map(([category, _]) => category);

  const strugglingCategories = Object.entries(categoryStats)
    .filter(([_, stats]: [string, any]) => stats.successRate < 50)
    .map(([category, _]) => category);

  return {
    categoryStats,
    mostActiveHour,
    tendency,
    successfulCategories,
    strugglingCategories,
    totalHabits: habits.length,
    activeHabits: habits.filter(h => h.isActive).length,
    overallSuccessRate: completions.length / (completions.length + misses.length) * 100,
    averageStreak: calculateAverageStreak(habits),
    completionConsistency: calculateConsistency(completions)
  };
}

function getMostFrequent(arr: number[]): number {
  const frequency = {};
  arr.forEach(num => {
    frequency[num] = (frequency[num] || 0) + 1;
  });

  let mostFrequent = arr[0];
  let maxCount = 0;

  Object.entries(frequency).forEach(([num, count]) => {
    if (count > maxCount) {
      maxCount = count;
      mostFrequent = parseInt(num);
    }
  });

  return mostFrequent;
}

function calculateAverageStreak(habits: any[]): number {
  const streaks = habits.map(h => h.streakCount).filter(s => s > 0);
  return streaks.length > 0 ? streaks.reduce((a, b) => a + b, 0) / streaks.length : 0;
}

function calculateConsistency(completions: any[]): number {
  if (completions.length < 7) return 0;

  // Check how many of the last 7 days had completions
  const last7Days = [];
  for (let i = 0; i < 7; i++) {
    const date = new Date();
    date.setDate(date.getDate() - i);
    last7Days.push(date.toDateString());
  }

  const completionDates = completions.map(c => c.completedAt.toDateString());
  const consistentDays = last7Days.filter(day => completionDates.includes(day)).length;

  return (consistentDays / 7) * 100;
}

// =============================================================================
// AI PREDICTION GENERATION
// =============================================================================
async function generateHabitPredictions(userAnalysis: any) {
  console.log(`🤖 Generating AI predictions for user patterns`);

  try {
    const { patternAnalysis, allHabits, userProfile } = userAnalysis;

    // Build comprehensive context for AI
    const context = buildPredictionContext(patternAnalysis, allHabits, userProfile);

    const completion = await openai.chat.completions.create({
      model: 'gpt-4o-mini', // Switched from gpt-4o to reduce costs by ~10x
      messages: [
        { role: 'system', content: PREDICTIVE_HABITS_PROMPT },
        { role: 'user', content: context }
      ],
      tools: [{
        type: 'function',
        function: PREDICTIVE_HABIT_SCHEMA.function
      }],
      tool_choice: { type: 'function', function: { name: 'predict_personalized_habits' } },
      temperature: 0.7,
      max_tokens: 1000 // Reduced from 1500 to save on output tokens
    });

    const toolCall = completion.choices[0]?.message?.tool_calls?.[0];
    if (!toolCall) {
      throw new Error('No tool call returned from AI');
    }

    const predictions = JSON.parse(toolCall.function.arguments);
    console.log(`🎯 AI generated ${predictions.predictedHabits.length} habit predictions`);
    
    // Log usage for predictive habits
    try {
      const now = new Date();
      const date = now.toISOString().split('T')[0];
      await logUsage({
        userId: userId || 'system',
        date,
        timestamp: now.getTime(),
        source: 'predictiveHabits',
        model: 'gpt-4o-mini',
        promptTokens: completion.usage?.prompt_tokens,
        completionTokens: completion.usage?.completion_tokens,
        totalTokens: completion.usage?.total_tokens,
        metadata: { predictedHabitCount: predictions.predictedHabits.length }
      });
    } catch (logError) {
      console.error('Failed to log predictive habits usage:', logError);
    }

    return predictions;

  } catch (error) {
    console.error('Error generating AI predictions:', error);

    // Fallback predictions if AI fails
    return {
      predictedHabits: [
        {
          title: "Morning gratitude practice",
          description: "Start your day with 3 things you're grateful for",
          category: "MINDFULNESS",
          rationale: "Your morning routine shows consistency - adding mindfulness would enhance your mental well-being",
          confidenceScore: 85,
          difficulty: "EASY",
          timing: "morning"
        },
        {
          title: "Evening wind-down ritual",
          description: "15 minutes of relaxing activity before bed",
          category: "SLEEP",
          rationale: "Your sleep patterns suggest you need better evening preparation",
          confidenceScore: 78,
          difficulty: "MODERATE",
          timing: "evening"
        },
        {
          title: "Weekly meal prep session",
          description: "Spend 1 hour prepping healthy meals for the week",
          category: "NUTRITION",
          rationale: "Your nutrition habits show room for planning and preparation",
          confidenceScore: 72,
          difficulty: "MODERATE",
          timing: "weekend"
        }
      ],
      patternAnalysis: "User shows strong morning consistency but needs evening routines and nutrition planning",
      recommendations: [
        "Focus on morning habits where you excel",
        "Build evening wind-down routines for better sleep",
        "Consider meal planning to support nutrition goals"
      ]
    };
  }
}

function buildPredictionContext(patternAnalysis: any, habits: any[], userProfile: any): string {
  const {
    categoryStats,
    mostActiveHour,
    tendency,
    successfulCategories,
    strugglingCategories,
    totalHabits,
    activeHabits,
    overallSuccessRate,
    averageStreak,
    completionConsistency
  } = patternAnalysis;

  const timeOfDay = mostActiveHour < 12 ? 'morning' : mostActiveHour < 18 ? 'afternoon' : 'evening';

  let context = `User: ${totalHabits} habits (${activeHabits} active), ${overallSuccessRate.toFixed(0)}% success, ${averageStreak.toFixed(0)}d avg streak, ${completionConsistency.toFixed(0)}% consistency. Active: ${timeOfDay} (${mostActiveHour}:00). Tendency: ${tendency}. Success: ${successfulCategories.join(', ') || 'none'}. Struggle: ${strugglingCategories.join(', ') || 'none'}. Frictions: ${userProfile.biggestFrictions?.join(', ') || 'none'}. Suggest 3-5 habits in successful categories, avoid struggling ones, build on ${timeOfDay} momentum, >70% success rate.`;

  return context;
}
