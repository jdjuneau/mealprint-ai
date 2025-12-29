import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';

// Initialize Firebase Admin (will be initialized in main index.js)
const db = admin.firestore();

// =============================================================================
// CIRCADIAN RHYTHM OPTIMIZATION - ANALYZE USER TIMING PATTERNS
// =============================================================================
export const optimizeCircadianTiming = functions.https.onCall(async (data, context) => {
  // Verify authentication
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = data.userId || context.auth.uid;
  if (!userId) {
    throw new functions.https.HttpsError('invalid-argument', 'User ID is required');
  }

  try {
    console.log(`⏰ Analyzing circadian patterns for user: ${userId}`);

    // Analyze completion patterns over time
    const circadianAnalysis = await analyzeCircadianPatterns(userId);

    // Generate timing recommendations
    const timingRecommendations = generateTimingRecommendations(circadianAnalysis);

    console.log(`✅ Generated circadian optimization for ${timingRecommendations.peakTimes.length} optimal time slots`);

    return {
      success: true,
      circadianAnalysis,
      timingRecommendations,
      analyzedAt: new Date().toISOString(),
      userId: userId
    };

  } catch (error) {
    console.error('Error optimizing circadian timing:', error);
    throw new functions.https.HttpsError('internal', 'Failed to analyze circadian patterns');
  }
});

// =============================================================================
// CIRCADIAN PATTERN ANALYSIS
// =============================================================================
async function analyzeCircadianPatterns(userId: string) {
  console.log(`📊 Analyzing completion timing patterns for ${userId}`);

  try {
    // Get completions from the last 30 days
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);

    const completionsQuery = db.collection('users').doc(userId).collection('completions')
      .where('completedAt', '>=', admin.firestore.Timestamp.fromDate(thirtyDaysAgo))
      .orderBy('completedAt', 'asc');

    const completionsSnapshot = await completionsQuery.get();
    const completions = completionsSnapshot.docs.map(doc => ({
      id: doc.id,
      ...doc.data(),
      completedAt: doc.data().completedAt?.toDate() || new Date(),
    }));

    console.log(`📅 Found ${completions.length} completions in last 30 days`);

    // Analyze by hour of day
    const hourlyStats = analyzeHourlyPatterns(completions);

    // Analyze by day of week
    const weeklyStats = analyzeWeeklyPatterns(completions);

    // Find peak performance times
    const peakHours = findPeakHours(hourlyStats);

    // Analyze consistency patterns
    const consistencyPatterns = analyzeConsistencyPatterns(completions);

    // Determine chronotype (morning lark vs night owl)
    const chronotype = determineChronotype(hourlyStats);

    return {
      totalCompletions: completions.length,
      averageCompletionsPerDay: completions.length / 30,
      hourlyStats,
      weeklyStats,
      peakHours,
      consistencyPatterns,
      chronotype,
      analysisPeriod: {
        start: thirtyDaysAgo.toISOString(),
        end: new Date().toISOString(),
        days: 30
      }
    };

  } catch (error) {
    console.error('Error analyzing circadian patterns:', error);
    throw error;
  }
}

function analyzeHourlyPatterns(completions: any[]): { [hour: number]: { count: number; success: number; avgDifficulty: number } } {
  const hourlyStats: { [hour: number]: { count: number; success: number; avgDifficulty: number; completions: any[] } } = {};

  // Initialize all hours
  for (let hour = 0; hour < 24; hour++) {
    hourlyStats[hour] = { count: 0, success: 0, avgDifficulty: 0, completions: [] };
  }

  // Group completions by hour
  completions.forEach(completion => {
    const hour = completion.completedAt.getHours();
    hourlyStats[hour].count++;
    hourlyStats[hour].success++;
    hourlyStats[hour].completions.push(completion);

    // Add difficulty if available (assuming it's stored somewhere)
    if (completion.difficulty) {
      hourlyStats[hour].avgDifficulty += completion.difficulty;
    }
  });

  // Calculate averages
  Object.keys(hourlyStats).forEach(hourStr => {
    const hour = parseInt(hourStr);
    const stats = hourlyStats[hour];
    if (stats.count > 0) {
      stats.avgDifficulty = stats.completions.reduce((sum, c) => sum + (c.difficulty || 3), 0) / stats.count;
    }
  });

  return hourlyStats;
}

function analyzeWeeklyPatterns(completions: any[]): { [day: number]: { count: number; avgHour: number } } {
  const weeklyStats: { [day: number]: { count: number; hours: number[]; avgHour: number } } = {};

  // Initialize days (0 = Sunday, 6 = Saturday)
  for (let day = 0; day < 7; day++) {
    weeklyStats[day] = { count: 0, hours: [], avgHour: 0 };
  }

  // Group by day of week
  completions.forEach(completion => {
    const day = completion.completedAt.getDay(); // 0 = Sunday
    weeklyStats[day].count++;
    weeklyStats[day].hours.push(completion.completedAt.getHours());
  });

  // Calculate average hour for each day
  Object.keys(weeklyStats).forEach(dayStr => {
    const day = parseInt(dayStr);
    const stats = weeklyStats[day];
    if (stats.hours.length > 0) {
      stats.avgHour = stats.hours.reduce((sum, hour) => sum + hour, 0) / stats.hours.length;
    }
  });

  return weeklyStats;
}

function findPeakHours(hourlyStats: any): {
  primary: number;
  secondary: number[];
  optimalWindows: Array<{ start: number; end: number; score: number }>;
} {
  // Find hours with highest completion rates
  const hourEntries = Object.entries(hourlyStats)
    .map(([hour, stats]: [string, any]) => ({
      hour: parseInt(hour),
      count: stats.count,
      score: stats.count // Could be more sophisticated scoring
    }))
    .sort((a, b) => b.score - a.score);

  const primary = hourEntries[0]?.hour || 9; // Default to 9 AM

  // Find secondary peak hours (within 3 hours of primary)
  const secondary = hourEntries
    .filter(entry => Math.abs(entry.hour - primary) <= 3 && entry.hour !== primary)
    .slice(0, 2)
    .map(entry => entry.hour);

  // Find optimal 2-hour windows
  const optimalWindows = [];
  for (let startHour = 0; startHour < 24; startHour++) {
    const endHour = (startHour + 2) % 24;
    const windowScore = (hourlyStats[startHour]?.count || 0) + (hourlyStats[endHour]?.count || 0);
    optimalWindows.push({ start: startHour, end: endHour, score: windowScore });
  }

  optimalWindows.sort((a, b) => b.score - a.score);

  return {
    primary,
    secondary,
    optimalWindows: optimalWindows.slice(0, 3)
  };
}

function analyzeConsistencyPatterns(completions: any[]): {
  mostConsistentDay: number;
  leastConsistentDay: number;
  streakPatterns: { avgStreak: number; maxStreak: number };
  timeVariability: number;
} {
  // Group by date
  const dailyStats: { [date: string]: { count: number; hours: number[] } } = {};

  completions.forEach(completion => {
    const dateKey = completion.completedAt.toDateString();
    if (!dailyStats[dateKey]) {
      dailyStats[dateKey] = { count: 0, hours: [] };
    }
    dailyStats[dateKey].count++;
    dailyStats[dateKey].hours.push(completion.completedAt.getHours());
  });

  // Analyze daily consistency
  const dailyEntries = Object.entries(dailyStats);
  const dayOfWeekStats: { [day: number]: number[] } = {};

  dailyEntries.forEach(([dateStr, stats]) => {
    const date = new Date(dateStr);
    const dayOfWeek = date.getDay();
    if (!dayOfWeekStats[dayOfWeek]) {
      dayOfWeekStats[dayOfWeek] = [];
    }
    dayOfWeekStats[dayOfWeek].push(stats.count);
  });

  // Find most and least consistent days
  const dayAverages = Object.entries(dayOfWeekStats).map(([day, counts]) => ({
    day: parseInt(day),
    avg: counts.reduce((sum, count) => sum + count, 0) / counts.length,
    consistency: calculateConsistency(counts)
  }));

  dayAverages.sort((a, b) => b.consistency - a.consistency);
  const mostConsistentDay = dayAverages[0]?.day || 1;
  const leastConsistentDay = dayAverages[dayAverages.length - 1]?.day || 0;

  // Calculate time variability (how consistent are completion times)
  const allHours = completions.map(c => c.completedAt.getHours());
  const timeVariability = calculateTimeVariability(allHours);

  return {
    mostConsistentDay,
    leastConsistentDay,
    streakPatterns: {
      avgStreak: 0, // Would need streak calculation logic
      maxStreak: 0
    },
    timeVariability
  };
}

function calculateConsistency(counts: number[]): number {
  if (counts.length <= 1) return 100;

  const mean = counts.reduce((sum, count) => sum + count, 0) / counts.length;
  const variance = counts.reduce((sum, count) => sum + Math.pow(count - mean, 2), 0) / counts.length;
  const stdDev = Math.sqrt(variance);

  // Return consistency score (lower std dev = higher consistency)
  return Math.max(0, 100 - (stdDev / mean) * 100);
}

function calculateTimeVariability(hours: number[]): number {
  if (hours.length <= 1) return 0;

  const mean = hours.reduce((sum, hour) => sum + hour, 0) / hours.length;
  const variance = hours.reduce((sum, hour) => sum + Math.pow(hour - mean, 2), 0) / hours.length;

  return Math.sqrt(variance); // Standard deviation in hours
}

function determineChronotype(hourlyStats: any): {
  type: 'morning_lark' | 'intermediate' | 'night_owl';
  peakHour: number;
  confidence: number;
  description: string;
} {
  const morningHours = [6, 7, 8, 9, 10, 11]; // 6 AM - 11 AM
  const eveningHours = [18, 19, 20, 21, 22, 23]; // 6 PM - 11 PM

  const morningScore = morningHours.reduce((sum, hour) => sum + (hourlyStats[hour]?.count || 0), 0);
  const eveningScore = eveningHours.reduce((sum, hour) => sum + (hourlyStats[hour]?.count || 0), 0);

  const totalScore = morningScore + eveningScore;
  const morningRatio = totalScore > 0 ? morningScore / totalScore : 0.5;

  let type: 'morning_lark' | 'intermediate' | 'night_owl';
  let description: string;
  let confidence: number;

  if (morningRatio > 0.7) {
    type = 'morning_lark';
    description = 'You are most productive in the morning hours (6 AM - 12 PM)';
    confidence = Math.min(100, morningRatio * 100);
  } else if (morningRatio < 0.3) {
    type = 'night_owl';
    description = 'You are most productive in the evening hours (6 PM - 12 AM)';
    confidence = Math.min(100, (1 - morningRatio) * 100);
  } else {
    type = 'intermediate';
    description = 'You have flexible energy patterns throughout the day';
    confidence = 50 + Math.abs(0.5 - morningRatio) * 100;
  }

  // Find actual peak hour
  const peakHour = Object.entries(hourlyStats)
    .map(([hour, stats]: [string, any]) => ({ hour: parseInt(hour), count: stats.count }))
    .sort((a, b) => b.count - a.count)[0]?.hour || 9;

  return {
    type,
    peakHour,
    confidence: Math.round(confidence),
    description
  };
}

// =============================================================================
// TIMING RECOMMENDATIONS GENERATION
// =============================================================================
function generateTimingRecommendations(circadianAnalysis: any) {
  const { chronotype, peakHours, hourlyStats, weeklyStats } = circadianAnalysis;

  // Generate personalized timing recommendations
  const recommendations = [];

  // Primary recommendation based on chronotype
  switch (chronotype.type) {
    case 'morning_lark':
      recommendations.push({
        type: 'primary',
        title: 'Morning Focus Strategy',
        description: `Schedule your most important habits between ${chronotype.peakHour - 1} AM and ${chronotype.peakHour + 1} AM when you're naturally most productive.`,
        optimalHours: [chronotype.peakHour - 1, chronotype.peakHour, chronotype.peakHour + 1],
        confidence: chronotype.confidence
      });
      break;

    case 'night_owl':
      recommendations.push({
        type: 'primary',
        title: 'Evening Focus Strategy',
        description: `Schedule your most important habits between ${chronotype.peakHour - 1} PM and ${chronotype.peakHour + 1} PM when you're naturally most productive.`,
        optimalHours: [chronotype.peakHour - 1, chronotype.peakHour, chronotype.peakHour + 1],
        confidence: chronotype.confidence
      });
      break;

    case 'intermediate':
      recommendations.push({
        type: 'primary',
        title: 'Flexible Timing Strategy',
        description: 'You have energy throughout the day. Schedule habits according to your daily schedule rather than fixed times.',
        optimalHours: peakHours.optimalWindows.map(w => w.start),
        confidence: chronotype.confidence
      });
      break;
  }

  // Secondary recommendations
  if (peakHours.secondary.length > 0) {
    recommendations.push({
      type: 'secondary',
      title: 'Backup Time Slots',
      description: `If your primary time doesn't work, try these alternative hours: ${peakHours.secondary.join(', ')}`,
      optimalHours: peakHours.secondary,
      confidence: Math.max(60, chronotype.confidence - 20)
    });
  }

  // Weekly pattern recommendations
  const bestDay = Object.entries(weeklyStats)
    .map(([day, stats]: [string, any]) => ({ day: parseInt(day), count: stats.count }))
    .sort((a, b) => b.count - a.count)[0];

  if (bestDay && bestDay.count > circadianAnalysis.averageCompletionsPerDay * 1.5) {
    const dayNames = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
    recommendations.push({
      type: 'weekly',
      title: 'Weekly Rhythm',
      description: `${dayNames[bestDay.day]} appears to be your most productive day. Consider scheduling more important habits then.`,
      optimalHours: [Math.round(weeklyStats[bestDay.day].avgHour)],
      confidence: 75
    });
  }

  return {
    chronotype,
    peakHours: peakHours.primary,
    optimalTimeWindows: peakHours.optimalWindows,
    recommendations,
    summary: {
      bestTimeOfDay: getTimeOfDayLabel(chronotype.peakHour),
      consistencyScore: Math.round((1 - circadianAnalysis.consistencyPatterns.timeVariability / 12) * 100),
      recommendedHabitsPerDay: Math.max(1, Math.min(5, Math.round(circadianAnalysis.averageCompletionsPerDay * 1.2)))
    }
  };
}

function getTimeOfDayLabel(hour: number): string {
  if (hour >= 5 && hour < 12) return 'morning';
  if (hour >= 12 && hour < 17) return 'afternoon';
  if (hour >= 17 && hour < 22) return 'evening';
  return 'night';
}
