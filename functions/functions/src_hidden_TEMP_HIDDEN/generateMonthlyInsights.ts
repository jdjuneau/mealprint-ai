import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import OpenAI from 'openai';
import { logUsage } from './usage';

// Initialize admin if not already initialized
if (!admin.apps.length) {
  admin.initializeApp();
}
const db = admin.firestore();

// Lazy initialization of OpenAI - only when needed and API key is available
// Supports both legacy config and modern secrets manager
async function getOpenAIClient(): Promise<OpenAI | null> {
  // Try modern secrets manager first (recommended)
  let apiKey = process.env.OPENAI_API_KEY;
  
  // Fallback to legacy config
  if (!apiKey) {
    try {
      apiKey = functions.config().openai?.key;
    } catch (e) {
      // Config might not be available
    }
  }
  
  if (!apiKey) {
    console.warn('OpenAI API key not configured. Set OPENAI_API_KEY secret or openai.key config.');
    return null;
  }
  
  return new OpenAI({ apiKey });
}


/**
 * Manually generate insights for a specific user (callable function)
 */
export const generateUserInsights = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = context.auth.uid;
  const { forceRegenerate = false } = data || {};

  try {
    // Check if user already has recent insights (within last 7 days) unless force regenerate
    // Query all active insights and filter by date in memory to avoid index requirement
    if (!forceRegenerate) {
      const allActiveInsightsSnapshot = await db
        .collection('users')
        .doc(userId)
        .collection('insights')
        .where('status', '==', 'active')
        .get();

      const sevenDaysAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000);
      const recentInsights = allActiveInsightsSnapshot.docs.filter((doc) => {
        const data = doc.data();
        const generatedAt = data.generatedAt?.toDate?.() || data.generatedAt?.toMillis?.() 
          ? new Date(data.generatedAt.toMillis ? data.generatedAt.toMillis() : data.generatedAt._seconds * 1000)
          : null;
        return generatedAt && generatedAt >= sevenDaysAgo;
      });

      if (recentInsights.length > 0) {
        return {
          success: false,
          message: 'Insights were generated recently. Use forceRegenerate=true to generate new ones.',
          existingCount: recentInsights.length,
        };
      }
    }

    // Get user's health logs from the past month
    // Health logs are stored at: logs/{userId}/daily/{date}/entries/{entryId}
    // Document ID is the date in format "yyyy-MM-dd"
    const oneMonthAgo = new Date();
    oneMonthAgo.setMonth(oneMonthAgo.getMonth() - 1);
    const oneMonthAgoDateStr = oneMonthAgo.toISOString().split('T')[0];
    const todayDateStr = new Date().toISOString().split('T')[0];

    // Query all daily log documents and filter by document ID (date) to avoid index requirement
    const allDailyLogsSnapshot = await db
      .collection('logs')
      .doc(userId)
      .collection('daily')
      .get();

    const healthLogs: any[] = [];
    const energyScores: Array<{ date: string; score: number }> = [];

    // Filter documents by date (document ID) in memory
    for (const dayDoc of allDailyLogsSnapshot.docs) {
      const docDate = dayDoc.id; // Document ID is the date
      
      // Skip if date is before one month ago or after today
      if (docDate < oneMonthAgoDateStr || docDate > todayDateStr) {
        continue;
      }

      const dayData = dayDoc.data();

      // Collect energy scores for trend analysis
      if (dayData.energyScore !== undefined) {
        energyScores.push({
          date: dayData.date || docDate,
          score: dayData.energyScore
        });
      }

      // Collect health log entries
      const entriesSnapshot = await dayDoc.ref.collection('entries').get();
      entriesSnapshot.forEach((entryDoc) => {
        healthLogs.push(entryDoc.data());
      });
    }

    if (healthLogs.length === 0) {
      throw new functions.https.HttpsError('failed-precondition', 'Not enough health data to generate insights. Log some activities first.');
    }

    // Get user profile
    const profileDoc = await db
      .collection('users')
      .doc(userId)
      .collection('profile')
      .doc('habits')
      .get();

    const profile = profileDoc.exists ? profileDoc.data() : {};

    // Generate insights using OpenAI
    const insights = await generateInsightsWithAI(healthLogs, profile, energyScores, userId);

    if (insights.length === 0) {
      throw new functions.https.HttpsError('internal', 'Failed to generate insights. Please try again.');
    }

    // Archive existing insights if force regenerating
    if (forceRegenerate) {
      const existingInsightsSnapshot = await db
        .collection('users')
        .doc(userId)
        .collection('insights')
        .where('status', '==', 'active')
        .get();

      const batch = db.batch();
      existingInsightsSnapshot.docs.forEach((doc) => {
        batch.update(doc.ref, {
          status: 'archived',
          archivedAt: admin.firestore.FieldValue.serverTimestamp(),
        });
      });
      await batch.commit();
    }

    // Save new insights to Firestore
    const savedInsights = [];
    for (const insight of insights) {
      const insightRef = await db.collection('users').doc(userId).collection('insights').add({
        ...insight,
        status: 'active',
        generatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      savedInsights.push({
        id: insightRef.id,
        ...insight,
      });
    }

    console.log(`✅ Generated ${insights.length} insights for user ${userId}`);

    return {
      success: true,
      insights: savedInsights,
      count: savedInsights.length,
    };
  } catch (error) {
    console.error('❌ Error generating user insights:', error);
    if (error instanceof functions.https.HttpsError) {
      throw error;
    }
    throw new functions.https.HttpsError('internal', 'Failed to generate insights');
  }
});

/**
 * Archive old insights (callable function)
 */
export const archiveOldInsights = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = context.auth.uid;
  const { olderThanDays = 90 } = data || {}; // Default: archive insights older than 90 days

  try {
    const cutoffDate = new Date();
    cutoffDate.setDate(cutoffDate.getDate() - olderThanDays);

    // Query all active insights and filter by date in memory to avoid index requirement
    const allActiveInsightsSnapshot = await db
      .collection('users')
      .doc(userId)
      .collection('insights')
      .where('status', '==', 'active')
      .get();
    
    const oldInsights = allActiveInsightsSnapshot.docs.filter((doc) => {
      const data = doc.data();
      const generatedAt = data.generatedAt?.toDate?.() || data.generatedAt?.toMillis?.() 
        ? new Date(data.generatedAt.toMillis ? data.generatedAt.toMillis() : data.generatedAt._seconds * 1000)
        : null;
      return generatedAt && generatedAt < cutoffDate;
    });

    if (oldInsights.length === 0) {
      return {
        success: true,
        archivedCount: 0,
        message: 'No old insights to archive',
      };
    }

    const batch = db.batch();
    oldInsights.forEach((doc) => {
      batch.update(doc.ref, {
        status: 'archived',
        archivedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    });

    await batch.commit();

    console.log(`✅ Archived ${oldInsights.length} old insights for user ${userId}`);

    return {
      success: true,
      archivedCount: oldInsights.length,
    };
  } catch (error) {
    console.error('❌ Error archiving old insights:', error);
    throw new functions.https.HttpsError('internal', 'Failed to archive old insights');
  }
});

/**
 * Generate 3-5 insights using GPT-4o
 */
async function generateInsightsWithAI(
  healthLogs: any[],
  profile: any,
  energyScores: Array<{ date: string; score: number }> = [],
  userId?: string
): Promise<Array<{
  title: string;
  text: string;
  chartData?: Array<{ x: number; y: number }>;
  chartType?: 'line' | 'bar' | 'area';
  action?: { label: string; type: string };
}>> {
  try {
    // Prepare data summary for AI
    const meals = healthLogs.filter((log) => log.type === 'meal');
    const workouts = healthLogs.filter((log) => log.type === 'workout');
    const sleepLogs = healthLogs.filter((log) => log.type === 'sleep');
    const moodLogs = healthLogs.filter((log) => log.type === 'mood');
    const waterLogs = healthLogs.filter((log) => log.type === 'water');

    // Calculate energy score statistics
    const energyScoreValues = energyScores.map(s => s.score);
    const avgEnergyScore = energyScoreValues.length > 0
      ? energyScoreValues.reduce((sum, val) => sum + val, 0) / energyScoreValues.length
      : null;
    const maxEnergyScore = energyScoreValues.length > 0 ? Math.max(...energyScoreValues) : null;
    const minEnergyScore = energyScoreValues.length > 0 ? Math.min(...energyScoreValues) : null;
    
    // Calculate energy score trend
    let energyTrend = 'stable';
    if (energyScores.length >= 7) {
      const midpoint = Math.floor(energyScores.length / 2);
      const firstHalf = energyScores.slice(0, midpoint).map(s => s.score);
      const secondHalf = energyScores.slice(midpoint).map(s => s.score);
      const firstAvg = firstHalf.reduce((sum, val) => sum + val, 0) / firstHalf.length;
      const secondAvg = secondHalf.reduce((sum, val) => sum + val, 0) / secondHalf.length;
      const diff = secondAvg - firstAvg;
      
      if (diff > 5) energyTrend = 'improving';
      else if (diff < -5) energyTrend = 'declining';
    }

    const dataSummary = {
      totalMeals: meals.length,
      totalWorkouts: workouts.length,
      avgSleepHours:
        sleepLogs.length > 0
          ? sleepLogs.reduce((sum, log) => sum + (log.durationHours || 0), 0) / sleepLogs.length
          : 0,
      avgMood:
        moodLogs.length > 0
          ? moodLogs.reduce((sum, log) => sum + (log.moodLevel || 5), 0) / moodLogs.length
          : 0,
      totalWater: waterLogs.reduce((sum, log) => sum + (log.ml || 0), 0),
      userGoals: profile.habitGoals || {},
      energyScores: {
        average: avgEnergyScore ? Math.round(avgEnergyScore) : null,
        max: maxEnergyScore,
        min: minEnergyScore,
        count: energyScores.length,
        trend: energyTrend,
        scores: energyScores // Include full array for chart data
      }
    };

    const prompt = `You are an AI wellness coach analyzing a user's health data from the past month.

User Data Summary:
- Total meals logged: ${dataSummary.totalMeals}
- Total workouts: ${dataSummary.totalWorkouts}
- Average sleep hours: ${dataSummary.avgSleepHours.toFixed(1)}
- Average mood: ${dataSummary.avgMood.toFixed(1)}/10
- Total water consumed: ${dataSummary.totalWater}ml
- User goals: ${JSON.stringify(dataSummary.userGoals)}
${dataSummary.energyScores.average !== null ? `- Energy Score: Average ${dataSummary.energyScores.average}/100 (${dataSummary.energyScores.count} days tracked), Trend: ${dataSummary.energyScores.trend}, Range: ${dataSummary.energyScores.min}-${dataSummary.energyScores.max}` : '- Energy Score: Not enough data'}

Generate 3-5 personalized insights in JSON format. Each insight should have:
1. title: A short, engaging title (max 60 chars)
2. text: A warm, encouraging explanation (2-3 sentences)
3. chartData (optional): Array of {x, y} points for visualization (7-30 data points)
4. chartType (optional): "line", "bar", or "area"
5. action (optional): {label: string, type: string} for a suggested action

Focus on:
- Positive trends and improvements (especially energy score trends if available)
- Actionable recommendations based on energy score patterns
- Patterns and correlations between energy scores and other metrics
- Celebration of wins and improvements
- If energy score is improving, highlight what's working
- If energy score is declining, provide supportive guidance

Return ONLY valid JSON array, no markdown, no code blocks:
[
  {
    "title": "Your Sleep Quality is Improving",
    "text": "Over the past month, your average sleep duration increased by 15%. You're getting more consistent rest, which is great for recovery!",
    "chartData": [{"x": 1, "y": 6.5}, {"x": 2, "y": 7.0}, ...],
    "chartType": "line",
    "action": {"label": "Set Sleep Goal", "type": "goal"}
  },
  ...
]`;

    const model = 'gpt-4o';
    const startedAt = Date.now();
    const openai = await getOpenAIClient();
    if (!openai) {
      console.warn('OpenAI API key not configured, using fallback insights');
      return [];
    }

    const response = await openai.chat.completions.create({
      model,
      messages: [
        {
          role: 'system',
          content:
            'You are a warm, encouraging AI wellness coach. Generate personalized insights in JSON format only.',
        },
        { role: 'user', content: prompt },
      ],
      temperature: 0.7,
      max_tokens: 2000,
    });

    const content = response.choices[0]?.message?.content || '[]';
    const insights = JSON.parse(content);

    // Log OpenAI usage for cost tracking (userId passed from caller)
    try {
      const usage: any = (response as any).usage;
      const promptTokens = usage?.prompt_tokens as number | undefined;
      const completionTokens = usage?.completion_tokens as number | undefined;
      const totalTokens = usage?.total_tokens as number | undefined;
      const now = new Date();
      const date = now.toISOString().split('T')[0];
      await logUsage({
        userId: userId || 'system',
        date,
        timestamp: startedAt,
        source: 'generateUserInsights',
        model,
        promptTokens,
        completionTokens,
        totalTokens,
        metadata: userId ? { userId } : { note: 'System-level insight generation' },
      });
    } catch (logError) {
      console.error('Failed to log insight generation usage:', logError);
      // Don't fail the request if logging fails
    }

    // Validate and return insights
    if (Array.isArray(insights) && insights.length > 0) {
      return insights.slice(0, 5); // Max 5 insights
    }

    return [];
  } catch (error) {
    console.error('Error generating insights with AI:', error);
    return [];
  }
}

// @ts-ignore - Fallback function kept for potential future use
function buildFallbackInsights(
  energyScores: Array<{ date: string; score: number }> = [],
  profile: any
): Array<{
  title: string;
  text: string;
  chartData?: Array<{ x: number; y: number }>;
  chartType?: 'line' | 'bar' | 'area';
  action?: { label: string; type: string };
}> {
  const insights: Array<{
    title: string;
    text: string;
    chartData?: Array<{ x: number; y: number }>;
    chartType?: 'line' | 'bar' | 'area';
    action?: { label: string; type: string };
  }> = [];

  // Basic energy score insight
  if (energyScores.length > 0) {
    const avgScore = energyScores.reduce((sum, s) => sum + s.score, 0) / energyScores.length;
    const trend = energyScores.length >= 3 ?
      (energyScores[energyScores.length - 1].score > energyScores[0].score ? 'improving' : 'declining') : 'stable';

    insights.push({
      title: `Your Energy ${trend === 'improving' ? 'is Improving' : trend === 'declining' ? 'Needs Attention' : 'is Stable'}`,
      text: `Your average energy score over the past ${energyScores.length} days is ${Math.round(avgScore)}/100. ${trend === 'improving' ? 'Keep up the great habits!' : trend === 'declining' ? 'Consider reviewing your sleep and nutrition patterns.' : 'You\'re maintaining good consistency.'}`,
      chartData: energyScores.map((s, i) => ({ x: i + 1, y: s.score })),
      chartType: 'line',
      action: trend === 'declining' ? { label: 'Review Habits', type: 'goal' } : undefined
    });
  }

  // Add a general wellness insight
  insights.push({
    title: 'Stay Consistent',
    text: 'Consistency is key to building lasting habits. Focus on small, daily improvements rather than big changes.',
    action: { label: 'Set Daily Goal', type: 'goal' }
  });

  return insights.slice(0, 3); // Return up to 3 insights
}

/**
 * Generate monthly insights for all users (scheduled function)
 * Runs on the 1st of each month at 9 AM
 */
export const generateMonthlyInsights = functions.pubsub
  .schedule('0 9 1 * *') // 9 AM on the 1st of each month
  .timeZone('America/New_York')
  .onRun(async (context) => {
    console.log('📊 Starting monthly insights generation');

    try {
      // Get all active users
      const usersSnapshot = await db.collection('users').get();
      const users = usersSnapshot.docs;

      let insightsGenerated = 0;

      for (const userDoc of users) {
        const userId = userDoc.id;

        try {
          // Get user's health logs from the past month
          // Health logs are stored at: logs/{userId}/daily/{date}/entries/{entryId}
          // Document ID is the date in format "yyyy-MM-dd"
          const oneMonthAgo = new Date();
          oneMonthAgo.setMonth(oneMonthAgo.getMonth() - 1);
          const oneMonthAgoDateStr = oneMonthAgo.toISOString().split('T')[0];
          const todayDateStr = new Date().toISOString().split('T')[0];

          // Query all daily log documents and filter by document ID (date) to avoid index requirement
          const allDailyLogsSnapshot = await db
            .collection('logs')
            .doc(userId)
            .collection('daily')
            .get();

          const healthLogs: any[] = [];
          const energyScores: Array<{ date: string; score: number }> = [];

          // Filter documents by date (document ID) in memory
          for (const dayDoc of allDailyLogsSnapshot.docs) {
            const docDate = dayDoc.id; // Document ID is the date
            
            // Skip if date is before one month ago or after today
            if (docDate < oneMonthAgoDateStr || docDate > todayDateStr) {
              continue;
            }

            const dayData = dayDoc.data();

            // Collect energy scores for trend analysis
            if (dayData.energyScore !== undefined) {
              energyScores.push({
                date: dayData.date || docDate,
                score: dayData.energyScore
              });
            }

            // Collect health log entries
            const entriesSnapshot = await dayDoc.ref.collection('entries').get();
            entriesSnapshot.forEach((entryDoc) => {
              healthLogs.push(entryDoc.data());
            });
          }

          if (healthLogs.length === 0) {
            console.log(`⏭️  Skipping user ${userId} - no health logs`);
            continue;
          }

          // Get user profile
          const profileDoc = await db
            .collection('users')
            .doc(userId)
            .collection('profile')
            .doc('habits')
            .get();

          const profile = profileDoc.exists ? profileDoc.data() : {};

          // Generate insights using GPT-4o (including energy score trends)
          const insights = await generateInsightsWithAI(healthLogs, profile, energyScores);

          // Save insights to Firestore
          for (const insight of insights) {
            await db.collection('users').doc(userId).collection('insights').add({
              ...insight,
              status: 'active',
              generatedAt: admin.firestore.FieldValue.serverTimestamp(),
            });
          }

          insightsGenerated++;
          console.log(`✅ Generated ${insights.length} insights for user ${userId}`);
        } catch (error) {
          console.error(`❌ Error generating insights for user ${userId}:`, error);
        }
      }

      console.log(`✅ Monthly insights generation completed. Generated for ${insightsGenerated} users`);

      return {
        success: true,
        usersProcessed: insightsGenerated,
      };
    } catch (error) {
      console.error('❌ Error in monthly insights generation:', error);
      throw error;
    }
  });

