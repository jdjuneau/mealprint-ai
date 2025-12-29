import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

/**
 * Get Coachie score history with trends for graphing and AI coach context
 * Returns scores for the last N days with trend analysis
 */
export const getScoreHistory = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const uid = context.auth.uid;
  const days = data?.days || 30; // Default to 30 days, max 365
  const maxDays = Math.min(days, 365);

  try {
    // Calculate date range
    const endDate = new Date();
    const startDate = new Date();
    startDate.setDate(startDate.getDate() - maxDays);

    // Fetch scores from Firestore
    // Document IDs are dates in YYYY-MM-DD format, so we can query by ID range
    const scoresRef = db.collection('users').doc(uid).collection('scores');
    const startDateStr = startDate.toISOString().split('T')[0];
    const endDateStr = endDate.toISOString().split('T')[0];
    
    // Query by document ID range (dates are sortable in YYYY-MM-DD format)
    const snapshot = await scoresRef
      .where(admin.firestore.FieldPath.documentId(), '>=', startDateStr)
      .where(admin.firestore.FieldPath.documentId(), '<=', endDateStr)
      .get();

    const scores: Array<{
      date: string;
      score: number;
      healthScore: number;
      wellnessScore: number;
      habitsScore: number;
    }> = [];

    snapshot.forEach((doc) => {
      const data = doc.data();
      scores.push({
        date: data.date || doc.id, // Use doc.id as fallback
        score: data.score || 0,
        healthScore: data.healthScore || 0,
        wellnessScore: data.wellnessScore || 0,
        habitsScore: data.habitsScore || 0,
      });
    });

    // Calculate trends
    const trend = calculateTrend(scores);
    const stats = calculateStats(scores);

    return {
      success: true,
      scores,
      trend,
      stats,
      daysRequested: maxDays,
      daysReturned: scores.length,
    };
  } catch (error: any) {
    console.error('Error fetching score history:', error);
    throw new functions.https.HttpsError(
      'internal',
      `Failed to fetch score history: ${error.message}`
    );
  }
});

/**
 * Calculate trend analysis from scores
 */
function calculateTrend(scores: Array<{ date: string; score: number }>): {
  direction: 'up' | 'down' | 'stable';
  change: number;
  changePercent: number;
  recentAverage: number;
  previousAverage: number;
  streak: number;
  streakType: 'improving' | 'declining' | 'stable';
} {
  if (scores.length === 0) {
    return {
      direction: 'stable',
      change: 0,
      changePercent: 0,
      recentAverage: 0,
      previousAverage: 0,
      streak: 0,
      streakType: 'stable',
    };
  }

  // Split into recent (last 7 days) and previous (7 days before that)
  const recentScores = scores.slice(-7).map(s => s.score);
  const previousScores = scores.length > 7 
    ? scores.slice(-14, -7).map(s => s.score)
    : scores.slice(0, Math.floor(scores.length / 2)).map(s => s.score);

  const recentAverage = recentScores.length > 0
    ? recentScores.reduce((a, b) => a + b, 0) / recentScores.length
    : 0;
  const previousAverage = previousScores.length > 0
    ? previousScores.reduce((a, b) => a + b, 0) / previousScores.length
    : 0;

  const change = recentAverage - previousAverage;
  const changePercent = previousAverage > 0
    ? (change / previousAverage) * 100
    : 0;

  let direction: 'up' | 'down' | 'stable';
  if (change > 2) {
    direction = 'up';
  } else if (change < -2) {
    direction = 'down';
  } else {
    direction = 'stable';
  }

  // Calculate streak (consecutive days improving or declining)
  let streak = 0;
  let streakType: 'improving' | 'declining' | 'stable' = 'stable';
  
  if (scores.length >= 2) {
    const lastScores = scores.slice(-14); // Check last 14 days for streak
    let currentStreak = 0;
    let currentType: 'improving' | 'declining' | 'stable' = 'stable';

    for (let i = 1; i < lastScores.length; i++) {
      const diff = lastScores[i].score - lastScores[i - 1].score;
      if (diff > 0) {
        if (currentType === 'improving') {
          currentStreak++;
        } else {
          currentStreak = 1;
          currentType = 'improving';
        }
      } else if (diff < 0) {
        if (currentType === 'declining') {
          currentStreak++;
        } else {
          currentStreak = 1;
          currentType = 'declining';
        }
      } else {
        // Same score - maintain streak but don't increment
        if (currentStreak > 0) {
          // Keep streak but don't increment
        } else {
          currentType = 'stable';
        }
      }

      if (currentStreak > streak) {
        streak = currentStreak;
        streakType = currentType;
      }
    }
  }

  return {
    direction,
    change: Math.round(change * 10) / 10,
    changePercent: Math.round(changePercent * 10) / 10,
    recentAverage: Math.round(recentAverage * 10) / 10,
    previousAverage: Math.round(previousAverage * 10) / 10,
    streak,
    streakType,
  };
}

/**
 * Calculate statistics from scores
 */
function calculateStats(scores: Array<{ date: string; score: number }>): {
  average: number;
  highest: number;
  lowest: number;
  highestDate: string | null;
  lowestDate: string | null;
  last7DaysAverage: number | null;
  last30DaysAverage: number | null;
  consistency: number; // Standard deviation (lower = more consistent)
} {
  if (scores.length === 0) {
    return {
      average: 0,
      highest: 0,
      lowest: 0,
      highestDate: null,
      lowestDate: null,
      last7DaysAverage: null,
      last30DaysAverage: null,
      consistency: 0,
    };
  }

  const scoreValues = scores.map(s => s.score);
  const average = scoreValues.reduce((a, b) => a + b, 0) / scoreValues.length;

  const highest = Math.max(...scoreValues);
  const lowest = Math.min(...scoreValues);
  const highestIndex = scoreValues.indexOf(highest);
  const lowestIndex = scoreValues.indexOf(lowest);
  const highestDate = scores[highestIndex]?.date || null;
  const lowestDate = scores[lowestIndex]?.date || null;

  const last7Days = scores.slice(-7);
  const last7DaysAverage = last7Days.length > 0
    ? last7Days.reduce((sum, s) => sum + s.score, 0) / last7Days.length
    : null;

  const last30Days = scores.slice(-30);
  const last30DaysAverage = last30Days.length > 0
    ? last30Days.reduce((sum, s) => sum + s.score, 0) / last30Days.length
    : null;

  // Calculate standard deviation (consistency)
  const variance = scoreValues.reduce((sum, score) => {
    return sum + Math.pow(score - average, 2);
  }, 0) / scoreValues.length;
  const consistency = Math.sqrt(variance);

  return {
    average: Math.round(average * 10) / 10,
    highest,
    lowest,
    highestDate,
    lowestDate,
    last7DaysAverage: last7DaysAverage ? Math.round(last7DaysAverage * 10) / 10 : null,
    last30DaysAverage: last30DaysAverage ? Math.round(last30DaysAverage * 10) / 10 : null,
    consistency: Math.round(consistency * 10) / 10,
  };
}

