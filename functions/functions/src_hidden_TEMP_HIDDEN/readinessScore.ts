import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';

// Initialize admin if not already initialized
if (!admin.apps.length) {
  admin.initializeApp();
}
const db = admin.firestore();

/**
 * Calculate unified energy/readiness score (0-100)
 * Combines health tracking, wellness activities, and energy indicators
 * HRV is optional - if not available, the score is calculated without it
 */
export const calculateReadinessScore = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = context.auth.uid;

  try {
    // Get today's date
    const today = new Date();
    const dateStr = today.toISOString().split('T')[0];

    // Get user profile for goals
    const profileDoc = await db.collection('users').doc(userId).get();
    const profile = profileDoc.data() || {};
    const calorieGoal = profile.dailyCalorieGoal || 2000;
    const stepsGoal = profile.dailySteps || 10000;
    const waterGoal = 2000; // ml
    const sleepGoal = 8.0; // hours

    // Get today's daily log
    const dailyLogDoc = await db
      .collection('users')
      .doc(userId)
      .collection('daily')
      .doc(dateStr)
      .get();
    const dailyLog = dailyLogDoc.exists ? dailyLogDoc.data() : null;

  // Get today's health logs (try users/{uid}/daily first, then fallback to logs/{uid}/daily)
  let healthLogs: any[] = [];
  try {
    const usersPath = await db
      .collection('users')
      .doc(userId)
      .collection('daily')
      .doc(dateStr)
      .collection('entries')
      .get();
    healthLogs = usersPath.docs.map((doc) => doc.data());
  } catch {}

  if (healthLogs.length === 0) {
    const logsPath = await db
      .collection('logs')
      .doc(userId)
      .collection('daily')
      .doc(dateStr)
      .collection('entries')
      .get();
    healthLogs = logsPath.docs.map((doc) => doc.data());
  }

    // Categorize logs
    const meals = healthLogs.filter((log) => log.type === 'meal');
    const workouts = healthLogs.filter((log) => log.type === 'workout');
    const sleepLogs = healthLogs.filter((log) => log.type === 'sleep');
    const waterLogs = healthLogs.filter((log) => log.type === 'water');
    const moodLogs = healthLogs.filter((log) => log.type === 'mood');
    const journalLogs = healthLogs.filter((log) => log.type === 'journal');
    const meditationLogs = healthLogs.filter((log) => log.type === 'meditation' || log.type === 'mindful_session');

    // Get latest sleep log
    const latestSleep = sleepLogs.length > 0 
      ? sleepLogs.reduce((latest, log) => {
          if (!latest || (log.timestamp > latest.timestamp)) return log;
          return latest;
        }, null)
      : null;

    let score = 0; // Start from 0, build up to 100

    // ===== HEALTH TRACKING (0-50 points) =====
    let healthScore = 0;

    // Calories (0-12 points)
    const caloriesConsumed = meals.reduce((sum, meal) => sum + (meal.calories || 0), 0);
    const calorieProgress = Math.min(caloriesConsumed / calorieGoal, 1);
    healthScore += Math.round(calorieProgress * 12);

    // Steps (0-12 points)
    const steps = dailyLog?.steps || 0;
    if (steps > 0) {
      const stepsProgress = Math.min(steps / stepsGoal, 1);
      healthScore += Math.round(stepsProgress * 12);
    }

    // Water (0-10 points)
    const totalWater = (dailyLog?.water || 0) + waterLogs.reduce((sum, log) => sum + (log.ml || 0), 0);
    const waterProgress = Math.min(totalWater / waterGoal, 1);
    healthScore += Math.round(waterProgress * 10);

    // Sleep quality (0-11 points) - based on goal achievement AND quality
    if (latestSleep) {
      const sleepHours = latestSleep.durationHours || 
        ((latestSleep.endTime - latestSleep.startTime) / (1000 * 60 * 60));
      const sleepProgress = Math.min(sleepHours / sleepGoal, 1);
      
      // Base points for meeting goal (0-6 points)
      healthScore += Math.round(sleepProgress * 6);
      
      // Quality bonus (0-5 points) - optimal range gets bonus
      if (sleepHours >= 7 && sleepHours <= 9) {
        healthScore += 5; // Optimal sleep
      } else if (sleepHours >= 6 && sleepHours <= 10) {
        healthScore += 3; // Good sleep
      } else if (sleepHours >= 5) {
        healthScore += 1; // Acceptable sleep
      }
    }

    // Workouts (0-5 points)
    if (workouts.length > 0) {
      healthScore += 5;
    } else {
      // Check yesterday's activity for energy context
      const yesterday = new Date(today);
      yesterday.setDate(yesterday.getDate() - 1);
      const yesterdayStr = yesterday.toISOString().split('T')[0];
      const yesterdaySnapshot = await db
        .collection('users')
        .doc(userId)
        .collection('daily')
        .doc(yesterdayStr)
        .collection('entries')
        .where('type', '==', 'workout')
        .get();
      if (!yesterdaySnapshot.empty) {
        healthScore += 2; // Had activity yesterday
      }
    }

    healthScore = Math.min(healthScore, 50);
    score += healthScore;

    // ===== WELLNESS ACTIVITIES (0-25 points) =====
    let wellnessScore = 0;

    // Mood (0-8 points) - based on mood level, not just logged
    if (moodLogs.length > 0) {
      const avgMood = moodLogs.reduce((sum, log) => sum + (log.moodLevel || 5), 0) / moodLogs.length;
      wellnessScore += Math.round((avgMood / 10) * 8);
    }

    // Meditation (0-7 points)
    if (meditationLogs.length > 0) {
      wellnessScore += 7;
    }

    // Journal (0-5 points)
    if (journalLogs.length > 0) {
      wellnessScore += 5;
    }

    // Consistency bonus (0-5 points) - engaging with wellness
    if (moodLogs.length > 0 || meditationLogs.length > 0 || journalLogs.length > 0) {
      wellnessScore += 5;
    }

    wellnessScore = Math.min(wellnessScore, 25);
    score += wellnessScore;

    // ===== ENERGY INDICATORS (0-25 points) =====
    let energyScore = 0;

    // HRV (if available) (0-10 points) - OPTIONAL, no penalty if missing
    const hrv = latestSleep?.hrv || null;
    if (hrv) {
      // Normalize HRV (30-100ms range is typical)
      const normalizedHRV = Math.min(Math.max((hrv - 30) / 70, 0), 1);
      energyScore += Math.round(normalizedHRV * 10);
    }
    // If HRV not available, we just don't add these points - no penalty

    // Recent activity energy (0-8 points)
    if (workouts.length > 0) {
      energyScore += 8; // Active today
    } else {
      // Check yesterday for recovery context
      const yesterday = new Date(today);
      yesterday.setDate(yesterday.getDate() - 1);
      const yesterdayStr = yesterday.toISOString().split('T')[0];
      const yesterdaySnapshot = await db
        .collection('users')
        .doc(userId)
        .collection('daily')
        .doc(yesterdayStr)
        .collection('entries')
        .where('type', '==', 'workout')
        .get();
      if (!yesterdaySnapshot.empty) {
        energyScore += 4; // Active yesterday, may be recovery day
      }
    }

    // Sleep quality for energy (0-7 points) - separate from health tracking
    if (latestSleep) {
      const sleepHours = latestSleep.durationHours || 
        ((latestSleep.endTime - latestSleep.startTime) / (1000 * 60 * 60));
      if (sleepHours >= 7 && sleepHours <= 9) {
        energyScore += 7; // Optimal for energy
      } else if (sleepHours >= 6 && sleepHours <= 10) {
        energyScore += 5; // Good for energy
      } else if (sleepHours >= 5) {
        energyScore += 3; // Acceptable
      } else if (sleepHours >= 4) {
        energyScore += 1; // Low but some
      }
    }

    energyScore = Math.min(energyScore, 25);
    score += energyScore;

    // Cap score at 100
    score = Math.min(Math.round(score), 100);

    // Save score to daily log for historical tracking
    try {
      const dailyLogRef = db
        .collection('users')
        .doc(userId)
        .collection('daily')
        .doc(dateStr);
      
      await dailyLogRef.set({
        energyScore: score,
        energyScoreBreakdown: {
          health: healthScore,
          wellness: wellnessScore,
          energy: energyScore,
          hasHRV: hrv !== null
        },
        energyScoreCalculatedAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      }, { merge: true });
    } catch (saveError) {
      console.warn('Failed to save energy score to daily log:', saveError);
      // Don't fail the entire request if saving fails
    }

    return {
      score,
      hrv: hrv || null,
      sleepHours: latestSleep?.durationHours || 
        (latestSleep ? ((latestSleep.endTime - latestSleep.startTime) / (1000 * 60 * 60)) : null),
      breakdown: {
        health: healthScore,
        wellness: wellnessScore,
        energy: energyScore,
        hasHRV: hrv !== null
      }
    };
  } catch (error) {
    console.error('Error calculating readiness score:', error);
    throw new functions.https.HttpsError('internal', 'Failed to calculate readiness score');
  }
});

/**
 * Get historical energy scores for trend analysis
 * Returns scores for the specified number of days
 */
export const getEnergyScoreHistory = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = context.auth.uid;
  const days = data?.days || 30; // Default to 30 days

  try {
    const today = new Date();
    const startDate = new Date(today);
    startDate.setDate(startDate.getDate() - days);

    const scores: Array<{ date: string; score: number; breakdown?: any }> = [];

    // Get daily logs for the date range
    const dailyLogsSnapshot = await db
      .collection('users')
      .doc(userId)
      .collection('daily')
      .where('date', '>=', startDate.toISOString().split('T')[0])
      .where('date', '<=', today.toISOString().split('T')[0])
      .orderBy('date', 'asc')
      .get();

    dailyLogsSnapshot.forEach((doc) => {
      const data = doc.data();
      if (data.energyScore !== undefined) {
        scores.push({
          date: data.date || doc.id,
          score: data.energyScore,
          breakdown: data.energyScoreBreakdown || null
        });
      }
    });

    // Calculate statistics
    const scoreValues = scores.map(s => s.score);
    const avgScore = scoreValues.length > 0 
      ? scoreValues.reduce((sum, val) => sum + val, 0) / scoreValues.length 
      : 0;
    const maxScore = scoreValues.length > 0 ? Math.max(...scoreValues) : 0;
    const minScore = scoreValues.length > 0 ? Math.min(...scoreValues) : 0;
    
    // Calculate trend (comparing first half to second half)
    let trend = 'stable';
    if (scores.length >= 7) {
      const midpoint = Math.floor(scores.length / 2);
      const firstHalf = scores.slice(0, midpoint).map(s => s.score);
      const secondHalf = scores.slice(midpoint).map(s => s.score);
      const firstAvg = firstHalf.reduce((sum, val) => sum + val, 0) / firstHalf.length;
      const secondAvg = secondHalf.reduce((sum, val) => sum + val, 0) / secondHalf.length;
      const diff = secondAvg - firstAvg;
      
      if (diff > 5) trend = 'improving';
      else if (diff < -5) trend = 'declining';
    }

    return {
      scores,
      statistics: {
        average: Math.round(avgScore),
        max: maxScore,
        min: minScore,
        count: scores.length,
        trend
      }
    };
  } catch (error) {
    console.error('Error getting energy score history:', error);
    throw new functions.https.HttpsError('internal', 'Failed to get energy score history');
  }
});

