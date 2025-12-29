/**
 * Grant Pro subscription to a user by email address
 * Looks up the user by email and grants Pro access
 * Uses onRequest with explicit CORS headers
 */

import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import express = require('express');

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

const app = express();

// Manual CORS headers - more reliable than middleware
const setCorsHeaders = (res: express.Response) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  res.setHeader('Access-Control-Max-Age', '3600');
};

// Handle preflight OPTIONS requests
app.options('*', (req, res) => {
  setCorsHeaders(res);
  res.status(204).end();
});

// Parse JSON bodies
app.use(express.json());

app.post('/', async (req, res) => {
  // Set CORS headers on every request
  setCorsHeaders(res);
  
  try {
    const { email, durationMonths = 3 } = req.body;

    if (!email) {
      res.status(400).json({ error: 'Email is required' });
      return;
    }

    const normalizedEmail = email.toLowerCase().trim();
    let userId: string | null = null;
    let userDoc: admin.firestore.DocumentSnapshot | null = null;

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
    } catch (queryError: any) {
      // If query fails (e.g., missing index), log and continue to next strategy
      console.warn(`Email query failed (may need index): ${queryError.message}`);
    }

    // Strategy 2: If not found in Firestore, try Firebase Auth
    if (!userId) {
      try {
        const userRecord = await admin.auth().getUserByEmail(normalizedEmail);
        userId = userRecord.uid;
        
        // Check if user document exists in Firestore
        userDoc = await db.collection('users').doc(userId).get();
        
        // If document doesn't exist, create it with basic info from Auth
        if (!userDoc.exists) {
          console.log(`Creating Firestore document for user ${userId} found in Auth`);
          await db.collection('users').doc(userId).set({
            uid: userId,
            email: normalizedEmail,
            name: userRecord.displayName || userRecord.email?.split('@')[0] || 'User',
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
            platform: 'web', // Default, will be updated if user logs in from Android
            platforms: ['web']
          }, { merge: true });
          
          // Re-fetch the document
          userDoc = await db.collection('users').doc(userId).get();
        }
      } catch (authError: any) {
        if (authError.code === 'auth/user-not-found') {
          setCorsHeaders(res);
          res.status(404).json({ error: `User with email ${email} not found in Firebase Auth or Firestore` });
          return;
        }
        throw authError;
      }
    }

    // Strategy 3: If still no userId, search all users and check email field manually
    // (This handles cases where email might be stored differently)
    if (!userId) {
      console.log(`Searching all users for email: ${normalizedEmail}`);
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
      setCorsHeaders(res);
      res.status(404).json({ error: `User with email ${email} not found in Firestore or Firebase Auth` });
      return;
    }

    // Grant Pro subscription
    await grantProToUser(userId, durationMonths);

    console.log(`Granted Pro subscription to user ${userId} (${email}) for ${durationMonths} months`);

    setCorsHeaders(res);
    res.json({
      success: true,
      userId: userId,
      email: email,
      message: `Pro subscription granted to ${email} for ${durationMonths} months`
    });
  } catch (error: any) {
    console.error(`Error granting Pro subscription:`, error);
    setCorsHeaders(res);
    res.status(500).json({ error: error.message || 'Failed to grant Pro subscription' });
  }
});

export const grantProByEmail = functions.region('us-central1').https.onRequest(app);

/**
 * Helper function to grant Pro subscription to a user
 * Supports both Android (expiresAt) and Web (status/startDate/endDate) formats
 */
async function grantProToUser(userId: string, durationMonths: number): Promise<void> {
  const now = Date.now();
  const expiresAt = now + (durationMonths * 30 * 24 * 60 * 60 * 1000);
  const startDate = admin.firestore.Timestamp.fromMillis(now);
  const endDate = admin.firestore.Timestamp.fromMillis(expiresAt);

  // Get existing user data to preserve platform info
  const userDoc = await db.collection('users').doc(userId).get();
  const userData = userDoc.data() || {};
  const existingPlatforms = userData.platforms || [];
  const platform = userData.platform || 'web';

  // Update subscription with both Android and Web compatible fields
  await db.collection('users').doc(userId).update({
    subscription: {
      // Android format
      tier: 'pro',
      expiresAt: expiresAt,
      isActive: true,
      purchasedAt: now,
      purchaseToken: `ADMIN_GRANTED_${now}`,
      productId: 'admin_granted_subscription',
      
      // Web format
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
}

