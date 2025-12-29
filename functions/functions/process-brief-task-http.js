// STANDALONE HTTP FUNCTION FOR processBriefTask
// This can be deployed via gcloud to avoid Firebase CLI timeout issues

const functions = require('firebase-functions');
const admin = require('firebase-admin');

if (!admin.apps.length) {
  admin.initializeApp();
}

// HTTP endpoint for processing brief tasks from Cloud Tasks
exports.processBriefTask = functions
  .runWith({
    timeoutSeconds: 540,
    memory: '512MB',
  })
  .region('us-central1')
  .https
  .onRequest(async (req, res) => {
    // Lazy load the actual implementation
    const briefTaskQueue = require('./lib/briefTaskQueue');
    
    // The processBriefTask from briefTaskQueue is already a complete function
    // We need to extract just the handler logic
    // Since it's exported as a function, we'll call it directly
    // But we need to handle it properly
    
    // Set CORS headers
    res.set('Access-Control-Allow-Origin', '*');
    res.set('Access-Control-Allow-Methods', 'POST, OPTIONS');
    res.set('Access-Control-Allow-Headers', 'Content-Type');
    
    if (req.method === 'OPTIONS') {
      res.status(204).end();
      return;
    }
    
    if (req.method !== 'POST') {
      res.status(405).json({ error: 'Method not allowed' });
      return;
    }
    
    try {
      const { userId, briefType, userName } = req.body;
      if (!userId || !briefType) {
        res.status(400).json({ error: 'Missing userId or briefType' });
        return;
      }
      
      console.log(`🔄 Processing ${briefType} brief task for user ${userName || userId} (${userId})`);
      
      // Import required modules
      const { generateBriefInternal } = require('./lib/generateBrief');
      const { getUserSubscriptionTier, SubscriptionTier } = require('./lib/subscriptionVerification');
      const db = admin.firestore();
      
      // Check subscription tier - free users only get morning briefs
      const tier = await getUserSubscriptionTier(userId);
      const isPro = tier === SubscriptionTier.PRO;
      
      // Skip afternoon and evening briefs for free users
      if ((briefType === 'afternoon' || briefType === 'evening') && !isPro) {
        console.log(`⏭️ Skipping ${briefType} brief for free user ${userName || userId} (${userId}) - Pro only`);
        res.status(200).json({ success: true, skipped: true, reason: 'free_tier' });
        return;
      }
      
      // Get user data
      const userDoc = await db.collection('users').doc(userId).get();
      if (!userDoc.exists) {
        res.status(404).json({ error: 'User not found' });
        return;
      }
      
      const userData = userDoc.data();
      const fcmTokens = userData.fcmTokens || (userData.fcmToken ? [userData.fcmToken] : []);
      const nudgesEnabled = userData.nudgesEnabled !== false;
      
      // Generate brief
      let brief;
      let briefGenerated = false;
      try {
        console.log(`📝 Generating ${briefType} brief for user ${userName || userId} (${userId})...`);
        const result = await generateBriefInternal(userId, briefType);
        brief = result?.brief || getFallbackBrief(briefType);
        briefGenerated = true;
        console.log(`✅ Generated ${briefType} brief for user ${userName || userId} (${userId}) (length: ${brief.length})`);
      } catch (genError) {
        console.error(`❌ Error generating ${briefType} brief for user ${userName || userId} (${userId}):`, genError);
        brief = getFallbackBrief(briefType);
        briefGenerated = false;
      }
      
      // Store brief in Firestore
      const today = new Date().toISOString().split('T')[0];
      const briefDocId = `${briefType}_${today}`;
      await db
        .collection('users')
        .doc(userId)
        .collection('briefs')
        .doc(briefDocId)
        .set({
          brief,
          type: briefType,
          date: today,
          generated: briefGenerated,
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
        });
      console.log(`💾 Stored ${briefType} brief for user ${userName || userId} (${userId}) (date: ${today}, generated: ${briefGenerated})`);
      
      // Send notification if enabled and tokens exist
      let notificationSent = false;
      if (nudgesEnabled && fcmTokens.length > 0) {
        try {
          const messaging = admin.messaging();
          const messages = fcmTokens.map((token) => ({
            token,
            notification: {
              title: getBriefTitle(briefType),
              body: brief.substring(0, 100) + (brief.length > 100 ? '...' : ''),
            },
            data: {
              type: 'brief',
              briefType,
              date: today,
            },
          }));
          const responses = await messaging.sendAll(messages);
          const successCount = responses.responses.filter(r => r.success).length;
          notificationSent = successCount > 0;
          console.log(`📤 Sent ${briefType} brief notification to user ${userName || userId} (${userId}) (${successCount}/${fcmTokens.length} tokens successful)`);
        } catch (notifError) {
          console.error(`⚠️ Error sending notification to user ${userName || userId} (${userId}):`, notifError);
        }
      } else {
        console.log(`ℹ️ User ${userName || userId} (${userId}) has no FCM token(s) or notifications disabled - brief stored but no notification sent`);
      }
      
      res.status(200).json({
        success: true,
        userId,
        briefStored: true,
        notificationSent,
        briefGenerated,
      });
    } catch (error) {
      console.error(`❌ Error processing brief task:`, error);
      res.status(500).json({ error: error.message || 'Internal server error' });
    }
  });

function getFallbackBrief(briefType) {
  const briefs = {
    morning: "Good morning! Here's your daily health brief. Check your app for personalized insights and recommendations.",
    afternoon: "Good afternoon! Here's your midday health brief with tips to keep you on track.",
    evening: "Good evening! Here's your evening health brief with a summary of your day and tomorrow's focus.",
  };
  return briefs[briefType];
}

function getBriefTitle(briefType) {
  const titles = {
    morning: '🌅 Morning Brief',
    afternoon: '☀️ Afternoon Brief',
    evening: '🌙 Evening Brief',
  };
  return titles[briefType];
}
