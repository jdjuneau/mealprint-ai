// Standalone grantProByEmail function with CORS support
// Deploy separately to avoid timeout issues

const functions = require('firebase-functions');
const admin = require('firebase-admin');

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

// Grant Pro by Email - HTTP endpoint with CORS
exports.grantProByEmail = functions.region('us-central1').https.onRequest(async (req, res) => {
  // Set CORS headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  res.setHeader('Access-Control-Max-Age', '3600');
  
  // Handle preflight OPTIONS request
  if (req.method === 'OPTIONS') {
    res.status(204).end();
    return;
  }
  
  // Only allow POST
  if (req.method !== 'POST') {
    res.status(405).json({ error: 'Method not allowed' });
    return;
  }
  
  try {
    const { email, durationMonths = 3 } = req.body;
    
    if (!email) {
      res.status(400).json({ error: 'Email is required' });
      return;
    }
    
    const normalizedEmail = email.toLowerCase().trim();
    let userId = null;
    let userDoc = null;
    
    // Strategy 1: Try to find user by email in Firestore
    try {
      const usersSnapshot = await db.collection('users')
        .where('email', '==', normalizedEmail)
        .limit(1)
        .get();
      
      if (!usersSnapshot.empty) {
        userDoc = usersSnapshot.docs[0];
        userId = userDoc.id;
      }
    } catch (queryError) {
      console.warn(`Email query failed (may need index): ${queryError.message}`);
    }
    
    // Strategy 2: If not found in Firestore, try Firebase Auth
    if (!userId) {
      try {
        const userRecord = await admin.auth().getUserByEmail(normalizedEmail);
        userId = userRecord.uid;
        userDoc = await db.collection('users').doc(userId).get();
        
        if (!userDoc.exists) {
          await db.collection('users').doc(userId).set({
            uid: userId,
            email: normalizedEmail,
            name: userRecord.displayName || userRecord.email?.split('@')[0] || 'User',
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
            platform: 'web',
            platforms: ['web']
          }, { merge: true });
          userDoc = await db.collection('users').doc(userId).get();
        }
      } catch (authError) {
        if (authError.code === 'auth/user-not-found') {
          res.status(404).json({ error: `User with email ${email} not found in Firebase Auth or Firestore` });
          return;
        }
        throw authError;
      }
    }
    
    // Strategy 3: Search all users manually
    if (!userId) {
      const allUsersSnapshot = await db.collection('users').limit(500).get();
      for (const doc of allUsersSnapshot.docs) {
        const data = doc.data();
        const docEmail = data.email?.toLowerCase()?.trim();
        if (docEmail === normalizedEmail) {
          userId = doc.id;
          userDoc = doc;
          break;
        }
      }
    }
    
    if (!userId || !userDoc) {
      res.status(404).json({ error: `User with email ${email} not found in Firestore or Firebase Auth` });
      return;
    }
    
    // Grant Pro subscription
    const now = Date.now();
    const expiresAt = now + (durationMonths * 30 * 24 * 60 * 60 * 1000);
    const startDate = admin.firestore.Timestamp.fromMillis(now);
    const endDate = admin.firestore.Timestamp.fromMillis(expiresAt);
    
    const userData = userDoc.data() || {};
    const existingPlatforms = userData.platforms || [];
    const platform = userData.platform || 'web';
    
    await db.collection('users').doc(userId).update({
      subscription: {
        tier: 'pro',
        expiresAt: expiresAt,
        isActive: true,
        purchasedAt: now,
        purchaseToken: `ADMIN_GRANTED_${now}`,
        productId: 'admin_granted_subscription',
        status: 'active',
        startDate: startDate,
        endDate: endDate,
        billingCycle: durationMonths >= 12 ? 'yearly' : 'monthly',
        paymentProvider: 'admin',
        subscriptionId: `admin_${userId}_${now}`,
        platforms: existingPlatforms.length > 0 ? existingPlatforms : [platform],
        cancelAtPeriodEnd: false
      },
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });
    
    console.log(`Granted Pro subscription to user ${userId} (${email}) for ${durationMonths} months`);
    
    res.json({
      success: true,
      userId: userId,
      email: email,
      message: `Pro subscription granted to ${email} for ${durationMonths} months`
    });
  } catch (error) {
    console.error(`Error granting Pro subscription:`, error);
    res.status(500).json({ error: error.message || 'Failed to grant Pro subscription' });
  }
});
