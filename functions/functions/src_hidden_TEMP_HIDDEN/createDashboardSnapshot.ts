/**
 * Create Dashboard Snapshot
 * 
 * Captures a snapshot of dashboard metrics for historical analysis.
 * Runs weekly on Sundays (for previous week) and monthly on 1st (for previous month).
 * 
 * Data is stored in Firestore at: dashboard_snapshots/{snapshotId}
 */

import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

interface SnapshotData {
  snapshotType: 'weekly' | 'monthly';
  periodStart: admin.firestore.Timestamp;
  periodEnd: admin.firestore.Timestamp;
  capturedAt: admin.firestore.Timestamp;
  
  // User metrics
  totalUsers: number;
  activeUsers: number;
  inactiveUsers: number;
  webUsers: number;
  androidUsers: number;
  
  // Usage metrics
  totalTokens: number;
  totalCost: number;
  totalCalls: number;
  
  // Token breakdown
  geminiTokens: number;
  geminiCost: number;
  geminiCalls: number;
  openaiTokens: number;
  openaiCost: number;
  openaiCalls: number;
  
  // Daily averages
  dailyAvgTokens: number;
  dailyAvgCost: number;
  dailyAvgCalls: number;
  
  // Monthly estimates
  monthlyEstTokens: number;
  monthlyEstCost: number;
  monthlyEstCalls: number;
  
  // Usage by tier (if available)
  usageByTier?: {
    free: { tokens: number; cost: number; calls: number; users: number };
    pro: { tokens: number; cost: number; calls: number; users: number };
  };
  
  // Complete dashboard data for analysis
  dailyBreakdown?: Array<{
    date: string;
    cost: number;
    tokens: number;
    calls: number;
  }>;
  
  featureBreakdown?: { [feature: string]: {
    cost: number;
    tokens: number;
    calls: number;
  }};
  
  userList?: Array<{
    userId: string;
    tier: string;
    platforms: string[];
  }>;
  
  // Summary of all usage events (limited to prevent document size issues)
  usageEventsSummary?: Array<{
    userId: string;
    date: string;
    source?: string;
    model?: string;
    tokens: number;
    cost: number;
  }>;
}

/**
 * Weekly snapshot - runs every Sunday at 11:59 PM
 * Captures data for the previous week (Monday-Sunday)
 */
export const createWeeklySnapshot = functions.pubsub
  .schedule('59 23 * * 0') // Every Sunday at 11:59 PM
  .timeZone('America/Los_Angeles') // Adjust to your timezone
  .onRun(async (context) => {
    console.log('Creating weekly dashboard snapshot...');
    
    const now = new Date();
    // Previous week: 7 days ago (Sunday) to yesterday (Saturday)
    const periodEnd = new Date(now);
    periodEnd.setDate(periodEnd.getDate() - 1); // Yesterday (Saturday)
    periodEnd.setHours(23, 59, 59, 999);
    
    const periodStart = new Date(periodEnd);
    periodStart.setDate(periodStart.getDate() - 6); // 7 days ago (Monday)
    periodStart.setHours(0, 0, 0, 0);
    
    return await createSnapshot('weekly', periodStart, periodEnd);
  });

/**
 * Monthly snapshot - runs on the 1st of each month at 11:59 PM
 * Captures data for the previous month
 */
export const createMonthlySnapshot = functions.pubsub
  .schedule('59 23 1 * *') // 1st of every month at 11:59 PM
  .timeZone('America/Los_Angeles') // Adjust to your timezone
  .onRun(async (context) => {
    console.log('Creating monthly dashboard snapshot...');
    
    const now = new Date();
    // Previous month
    const periodEnd = new Date(now.getFullYear(), now.getMonth(), 0, 23, 59, 59, 999); // Last day of previous month
    const periodStart = new Date(now.getFullYear(), now.getMonth() - 1, 1, 0, 0, 0, 0); // First day of previous month
    
    return await createSnapshot('monthly', periodStart, periodEnd);
  });

/**
 * Manual snapshot trigger - for testing or on-demand snapshots
 * Call with: { type: 'weekly' | 'monthly', daysBack?: number }
 * If daysBack is provided, creates snapshot for that many days back
 */
export const createSnapshotManual = functions.https.onCall(async (data, context) => {
  const { type, daysBack } = data;
  
  if (type !== 'weekly' && type !== 'monthly') {
    throw new functions.https.HttpsError('invalid-argument', 'type must be "weekly" or "monthly"');
  }
  
  const now = new Date();
  let periodStart: Date;
  let periodEnd: Date;
  
  if (daysBack !== undefined) {
    // Custom period: last N days
    periodEnd = new Date(now);
    periodEnd.setHours(23, 59, 59, 999);
    periodStart = new Date(periodEnd);
    periodStart.setDate(periodStart.getDate() - daysBack);
    periodStart.setHours(0, 0, 0, 0);
  } else if (type === 'weekly') {
    // Previous week
    periodEnd = new Date(now);
    periodEnd.setDate(periodEnd.getDate() - 1); // Yesterday
    periodEnd.setHours(23, 59, 59, 999);
    periodStart = new Date(periodEnd);
    periodStart.setDate(periodStart.getDate() - 6); // 7 days ago
    periodStart.setHours(0, 0, 0, 0);
  } else {
    // Previous month
    periodEnd = new Date(now.getFullYear(), now.getMonth(), 0, 23, 59, 59, 999);
    periodStart = new Date(now.getFullYear(), now.getMonth() - 1, 1, 0, 0, 0, 0);
  }
  
  await createSnapshot(type, periodStart, periodEnd);
  
  return {
    success: true,
    message: `${type} snapshot created for period ${periodStart.toISOString().split('T')[0]} to ${periodEnd.toISOString().split('T')[0]}`,
    periodStart: periodStart.toISOString(),
    periodEnd: periodEnd.toISOString()
  };
});

/**
 * Create a snapshot of dashboard metrics
 */
async function createSnapshot(
  snapshotType: 'weekly' | 'monthly',
  periodStart: Date,
  periodEnd: Date
): Promise<void> {
  try {
    console.log(`Creating ${snapshotType} snapshot for period: ${periodStart.toISOString()} to ${periodEnd.toISOString()}`);
    
    // Calculate date strings for the period
    const dateStrings: string[] = [];
    const currentDate = new Date(periodStart);
    while (currentDate <= periodEnd) {
      const dateStr = currentDate.toISOString().split('T')[0]; // YYYY-MM-DD
      dateStrings.push(dateStr);
      currentDate.setDate(currentDate.getDate() + 1);
    }
    
    const daysInPeriod = dateStrings.length;
    
    // Fetch user data
    const usersSnapshot = await db.collection('users').get();
    const totalUsers = usersSnapshot.size;
    
    // Track active users (users with usage data in this period)
    const activeUserIds = new Set<string>();
    const userPlatforms: { [userId: string]: string[] } = {};
    
    usersSnapshot.forEach((doc) => {
      const userData = doc.data();
      const platforms = (userData.platforms || []) as string[];
      const platformList = [...platforms];
      if (userData.platform) {
        platformList.push(userData.platform as string);
      }
      userPlatforms[doc.id] = [...new Set(platformList)];
    });
    
    // Track user tiers for breakdown
    const userTiers: { [userId: string]: string } = {};
    
    // Get all user IDs from both collections first
    const logsRef = db.collection('logs');
    const logsSnapshot = await logsRef.get();
    const allUserIds = new Set<string>();
    
    logsSnapshot.forEach((doc) => allUserIds.add(doc.id));
    
    const openaiUsageRef = db.collection('openai_usage');
    const openaiUsageSnapshot = await openaiUsageRef.get();
    openaiUsageSnapshot.forEach((doc) => allUserIds.add(doc.id));
    
    // Pre-fetch all user tiers in parallel
    const tierPromises = Array.from(allUserIds).map(async (userId) => {
      const userDocRef = db.collection('users').doc(userId);
      const userDocSnap = await userDocRef.get();
      if (userDocSnap.exists) {
        const userData = userDocSnap.data();
        const tier = userData?.subscription?.tier || 'free';
        userTiers[userId] = tier.toLowerCase();
      } else {
        userTiers[userId] = 'free';
      }
    });
    
    await Promise.all(tierPromises);
    
    // Fetch usage data for the period
    let totalTokens = 0;
    let totalCost = 0;
    let totalCalls = 0;
    let geminiTokens = 0;
    let geminiCost = 0;
    let geminiCalls = 0;
    let openaiTokens = 0;
    let openaiCost = 0;
    let openaiCalls = 0;
    
    // Track usage by tier
    const usageByTier: { [userId: string]: { tokens: number; cost: number; calls: number } } = {};
    
    // Track daily breakdown
    const dailyBreakdown: { [date: string]: { cost: number; tokens: number; calls: number } } = {};
    dateStrings.forEach(dateStr => {
      dailyBreakdown[dateStr] = { cost: 0, tokens: 0, calls: 0 };
    });
    
    // Track feature breakdown
    const featureBreakdown: { [feature: string]: { cost: number; tokens: number; calls: number } } = {};
    
    // Track all usage events (for complete snapshot)
    const usageEvents: Array<{
      userId: string;
      date: string;
      source?: string;
      model?: string;
      tokens: number;
      cost: number;
    }> = [];
    
    // Query logs collection
    for (const userDoc of logsSnapshot.docs) {
      const userId = userDoc.id;
      
      for (const dateStr of dateStrings) {
        const dailyRef = userDoc.ref.collection('daily').doc(dateStr);
        const aiUsageRef = dailyRef.collection('ai_usage');
        const aiUsageSnapshot = await aiUsageRef.get();
        
        aiUsageSnapshot.forEach((usageDoc) => {
          const usageDataItem = usageDoc.data();
          activeUserIds.add(userId);
          
          const tokens = usageDataItem.totalTokens || 0;
          const cost = usageDataItem.estimatedCostUsd || 0;
          const model = (usageDataItem.model || 'unknown').toLowerCase();
          const source = usageDataItem.source || 'unknown';
          
          totalTokens += tokens;
          totalCost += cost;
          totalCalls += 1;
          
          // Track daily breakdown
          if (dailyBreakdown[dateStr]) {
            dailyBreakdown[dateStr].cost += cost;
            dailyBreakdown[dateStr].tokens += tokens;
            dailyBreakdown[dateStr].calls += 1;
          }
          
          // Track feature breakdown
          if (!featureBreakdown[source]) {
            featureBreakdown[source] = { cost: 0, tokens: 0, calls: 0 };
          }
          featureBreakdown[source].cost += cost;
          featureBreakdown[source].tokens += tokens;
          featureBreakdown[source].calls += 1;
          
          // Track usage event
          usageEvents.push({
            userId,
            date: dateStr,
            source,
            model,
            tokens,
            cost
          });
          
          // Track by tier
          const tier = userTiers[userId] || 'free';
          if (!usageByTier[userId]) {
            usageByTier[userId] = { tokens: 0, cost: 0, calls: 0 };
          }
          usageByTier[userId].tokens += tokens;
          usageByTier[userId].cost += cost;
          usageByTier[userId].calls += 1;
          
          if (model.includes('gemini')) {
            geminiTokens += tokens;
            geminiCost += cost;
            geminiCalls += 1;
          } else {
            openaiTokens += tokens;
            openaiCost += cost;
            openaiCalls += 1;
          }
        });
      }
    }
    
    // Query openai_usage collection
    for (const userDoc of openaiUsageSnapshot.docs) {
      const userId = userDoc.id;
      
      for (const dateStr of dateStrings) {
        const dailyRef = userDoc.ref.collection('daily').doc(dateStr);
        const eventsRef = dailyRef.collection('events');
        const eventsSnapshot = await eventsRef.get();
        
        eventsSnapshot.forEach((eventDoc) => {
          const eventData = eventDoc.data();
          activeUserIds.add(userId);
          
          const tokens = eventData.totalTokens || 0;
          const cost = eventData.estimatedCostUsd || 0;
          const model = (eventData.model || 'unknown').toLowerCase();
          
          totalTokens += tokens;
          totalCost += cost;
          totalCalls += 1;
          
          // Track by tier
          const tier = userTiers[userId] || 'free';
          if (!usageByTier[userId]) {
            usageByTier[userId] = { tokens: 0, cost: 0, calls: 0 };
          }
          usageByTier[userId].tokens += tokens;
          usageByTier[userId].cost += cost;
          usageByTier[userId].calls += 1;
          
          if (model.includes('gemini')) {
            geminiTokens += tokens;
            geminiCost += cost;
            geminiCalls += 1;
          } else {
            openaiTokens += tokens;
            openaiCost += cost;
            openaiCalls += 1;
          }
        });
      }
    }
    
    // Calculate usage by tier
    const tierUsage = {
      free: { tokens: 0, cost: 0, calls: 0, users: 0 },
      pro: { tokens: 0, cost: 0, calls: 0, users: 0 }
    };
    
    const tierUsers = { free: new Set<string>(), pro: new Set<string>() };
    
    Object.keys(usageByTier).forEach((userId) => {
      const tier = userTiers[userId] || 'free';
      const usage = usageByTier[userId];
      
      if (tier === 'pro') {
        tierUsage.pro.tokens += usage.tokens;
        tierUsage.pro.cost += usage.cost;
        tierUsage.pro.calls += usage.calls;
        tierUsers.pro.add(userId);
      } else {
        tierUsage.free.tokens += usage.tokens;
        tierUsage.free.cost += usage.cost;
        tierUsage.free.calls += usage.calls;
        tierUsers.free.add(userId);
      }
    });
    
    tierUsage.free.users = tierUsers.free.size;
    tierUsage.pro.users = tierUsers.pro.size;
    
    const activeUsers = activeUserIds.size;
    const inactiveUsers = totalUsers - activeUsers;
    
    // Count users by platform
    let webUsers = 0;
    let androidUsers = 0;
    
    // Build user list
    const userList: Array<{ userId: string; tier: string; platforms: string[] }> = [];
    activeUserIds.forEach((userId) => {
      const platforms = userPlatforms[userId] || [];
      if (platforms.includes('web')) webUsers++;
      if (platforms.includes('android')) androidUsers++;
      
      userList.push({
        userId,
        tier: userTiers[userId] || 'free',
        platforms
      });
    });
    
    // Convert daily breakdown to array
    const dailyBreakdownArray = Object.keys(dailyBreakdown)
      .sort()
      .map(date => ({
        date,
        ...dailyBreakdown[date]
      }));
    
    // Calculate daily averages
    const dailyAvgTokens = daysInPeriod > 0 ? totalTokens / daysInPeriod : 0;
    const dailyAvgCost = daysInPeriod > 0 ? totalCost / daysInPeriod : 0;
    const dailyAvgCalls = daysInPeriod > 0 ? totalCalls / daysInPeriod : 0;
    
    // Calculate monthly estimates (extrapolate from period)
    const daysInMonth = snapshotType === 'monthly' ? daysInPeriod : 30;
    const monthlyEstTokens = dailyAvgTokens * daysInMonth;
    const monthlyEstCost = dailyAvgCost * daysInMonth;
    const monthlyEstCalls = dailyAvgCalls * daysInMonth;
    
    // Build snapshot data
    const snapshotData: SnapshotData = {
      snapshotType,
      periodStart: admin.firestore.Timestamp.fromDate(periodStart),
      periodEnd: admin.firestore.Timestamp.fromDate(periodEnd),
      capturedAt: admin.firestore.Timestamp.now(),
      
      totalUsers,
      activeUsers,
      inactiveUsers,
      webUsers,
      androidUsers,
      
      totalTokens,
      totalCost,
      totalCalls,
      
      geminiTokens,
      geminiCost,
      geminiCalls,
      openaiTokens,
      openaiCost,
      openaiCalls,
      
      dailyAvgTokens,
      dailyAvgCost,
      dailyAvgCalls,
      
      monthlyEstTokens,
      monthlyEstCost,
      monthlyEstCalls,
      
      usageByTier: tierUsage,
      
      // Complete dashboard data
      dailyBreakdown: dailyBreakdownArray,
      featureBreakdown: featureBreakdown,
      userList: userList,
      // Store up to 5000 events to avoid document size limits (Firestore max is 1MB)
      usageEventsSummary: usageEvents.slice(0, 5000),
    };
    
    // Generate snapshot ID (YYYY-MM-DD-type format)
    const snapshotId = `${periodEnd.toISOString().split('T')[0]}-${snapshotType}`;
    
    // Save snapshot to Firestore
    await db.collection('dashboard_snapshots').doc(snapshotId).set(snapshotData);
    
    console.log(`✅ Snapshot created successfully: ${snapshotId}`);
    console.log(`   Active users: ${activeUsers}, Total tokens: ${totalTokens}, Total cost: $${totalCost.toFixed(2)}`);
    
    return;
  } catch (error: any) {
    console.error(`Error creating ${snapshotType} snapshot:`, error);
    throw error;
  }
}

