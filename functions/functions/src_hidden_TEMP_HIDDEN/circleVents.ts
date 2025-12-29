import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import OpenAI from 'openai';
import { logUsage } from './usage';

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();
const openai = new OpenAI({
  apiKey: process.env.OPENAI_API_KEY || '',
});

/**
 * HTTP callable function to create a vent (anonymous)
 */
export const createVent = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = context.auth.uid;
  const { circleId, content } = data;

  if (!circleId || !content || content.trim().length === 0) {
    throw new functions.https.HttpsError('invalid-argument', 'circleId and content are required');
  }

  if (content.length > 500) {
    throw new functions.https.HttpsError('invalid-argument', 'Content must be 500 characters or less');
  }

  try {
    // Verify user is a member of the circle
    const circleDoc = await db.collection('circles').doc(circleId).get();
    if (!circleDoc.exists) {
      throw new functions.https.HttpsError('not-found', 'Circle not found');
    }

    const circle = circleDoc.data();
    if (!circle?.members?.includes(userId)) {
      throw new functions.https.HttpsError('permission-denied', 'Not a member of this circle');
    }

    // Moderate content using AI (Grok-4 placeholder - using OpenAI for now)
    const moderationResult = await moderateContent(content);

    // Create vent document
    const ventData: any = {
      circleId,
      userId, // Stored but not displayed
      content: content.trim(),
      status: moderationResult.flagged ? 'hidden' : 'active',
      moderationStatus: moderationResult.flagged ? 'flagged' : 'approved',
      moderationFlags: moderationResult.flags,
      replyCount: 0,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    // Generate Coachie reply (only if not flagged)
    if (!moderationResult.flagged) {
      const coachieReply = await generateCoachieReply(content, context.auth.uid);
      ventData.coachieReply = {
        content: coachieReply,
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
      };
    }

    const ventRef = await db.collection('circles').doc(circleId).collection('vents').add(ventData);

    // If flagged, schedule for human review
    if (moderationResult.flagged) {
      await scheduleHumanReview(ventRef.id, circleId, content, moderationResult.flags);
    }

    return { success: true, ventId: ventRef.id };
  } catch (error) {
    console.error('Error creating vent:', error);
    if (error instanceof functions.https.HttpsError) {
      throw error;
    }
    throw new functions.https.HttpsError('internal', 'Failed to create vent');
  }
});

/**
 * HTTP callable function to add a reply to a vent (anonymous)
 */
export const addVentReply = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = context.auth.uid;
  const { ventId, circleId, content } = data;

  if (!ventId || !circleId || !content || content.trim().length === 0) {
    throw new functions.https.HttpsError('invalid-argument', 'ventId, circleId, and content are required');
  }

  if (content.length > 200) {
    throw new functions.https.HttpsError('invalid-argument', 'Content must be 200 characters or less');
  }

  try {
    // Verify user is a member of the circle
    const circleDoc = await db.collection('circles').doc(circleId).get();
    if (!circleDoc.exists) {
      throw new functions.https.HttpsError('not-found', 'Circle not found');
    }

    const circle = circleDoc.data();
    if (!circle?.members?.includes(userId)) {
      throw new functions.https.HttpsError('permission-denied', 'Not a member of this circle');
    }

    // Verify vent exists
    const ventDoc = await db.collection('circles').doc(circleId).collection('vents').doc(ventId).get();
    if (!ventDoc.exists) {
      throw new functions.https.HttpsError('not-found', 'Vent not found');
    }

    // Moderate content
    const moderationResult = await moderateContent(content);

    // Create reply document
    const replyData: any = {
      ventId,
      userId, // Stored but not displayed
      content: content.trim(),
      status: moderationResult.flagged ? 'hidden' : 'active',
      moderationStatus: moderationResult.flagged ? 'flagged' : 'approved',
      moderationFlags: moderationResult.flags,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    const replyRef = await db
      .collection('circles')
      .doc(circleId)
      .collection('vents')
      .doc(ventId)
      .collection('replies')
      .add(replyData);

    // Update vent reply count (only if not flagged)
    if (!moderationResult.flagged) {
      await ventDoc.ref.update({
        replyCount: admin.firestore.FieldValue.increment(1),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    }

    // If flagged, schedule for human review
    if (moderationResult.flagged) {
      await scheduleHumanReview(replyRef.id, circleId, content, moderationResult.flags, ventId);
    }

    return { success: true, replyId: replyRef.id };
  } catch (error) {
    console.error('Error adding vent reply:', error);
    if (error instanceof functions.https.HttpsError) {
      throw error;
    }
    throw new functions.https.HttpsError('internal', 'Failed to add reply');
  }
});

/**
 * Moderate content using AI (Grok-4 placeholder - using OpenAI for now)
 * Flags: hate, self_harm, promotion
 */
async function moderateContent(content: string): Promise<{
  flagged: boolean;
  flags: string[];
}> {
  try {
    // TODO: Replace with Grok-4 API when available
    // For now, using OpenAI moderation API
    const moderation = await openai.moderations.create({
      input: content,
    });

    const result = moderation.results[0];
    const flags: string[] = [];

    // Map OpenAI categories to our flags
    if (result.categories.hate || result.categories['hate/threatening']) {
      flags.push('hate');
    }
    if (result.categories['self-harm'] || result.categories['self-harm/intent']) {
      flags.push('self_harm');
    }
    // Check for promotional content (spam, promotional keywords)
    if (result.categories.spam || containsPromotionalContent(content)) {
      flags.push('promotion');
    }

    return {
      flagged: flags.length > 0 || result.flagged,
      flags,
    };
  } catch (error) {
    console.error('Error moderating content:', error);
    // On error, approve content (fail open) but log for review
    return { flagged: false, flags: [] };
  }
}

/**
 * Check for promotional content (simple keyword-based check)
 * TODO: Enhance with Grok-4 when available
 */
function containsPromotionalContent(content: string): boolean {
  const promotionalKeywords = [
    'buy now',
    'limited time',
    'click here',
    'discount',
    'sale',
    'promo code',
    'affiliate',
    'sponsor',
    'advertisement',
    'check out my',
    'follow me on',
    'subscribe to',
    'visit my website',
    'link in bio',
  ];

  const lowerContent = content.toLowerCase();
  return promotionalKeywords.some((keyword) => lowerContent.includes(keyword));
}

/**
 * Generate Coachie's empathetic reply
 */
async function generateCoachieReply(ventContent: string, userId?: string): Promise<string> {
  try {
    const response = await openai.chat.completions.create({
      model: 'gpt-4o-mini', // Using cost-effective model
      messages: [
        {
          role: 'system',
          content: `You are Coachie, a compassionate wellness coach. When users vent about struggles (binging, setbacks, frustration), respond with:
- Empathy and validation ("That frustration is valid")
- Brief support (1-2 sentences max)
- Offer a helpful resource ("Want a 2-min reset?" or "Want a quick breathing exercise?")
Keep responses under 50 words. Be warm, understanding, and non-judgmental.`,
        },
        {
          role: 'user',
          content: ventContent,
        },
      ],
      max_tokens: 80,
      temperature: 0.7,
    });

    const reply = response.choices[0]?.message?.content?.trim() || "That frustration is valid. Want a 2-min reset?";
    
    // Log usage for vent reply
    try {
      const now = new Date();
      const date = now.toISOString().split('T')[0];
      await logUsage({
        userId: userId || 'system',
        date,
        timestamp: now.getTime(),
        source: 'circleVentReply',
        model: 'gpt-4o-mini',
        promptTokens: response.usage?.prompt_tokens,
        completionTokens: response.usage?.completion_tokens,
        totalTokens: response.usage?.total_tokens,
      });
    } catch (logError) {
      console.error('Failed to log vent reply usage:', logError);
    }
    
    return reply;
  } catch (error) {
    console.error('Error generating Coachie reply:', error);
    // Fallback reply
    return "That frustration is valid. Want a 2-min reset?";
  }
}

/**
 * Schedule content for human review (creates a review task)
 */
async function scheduleHumanReview(
  contentId: string,
  circleId: string,
  content: string,
  flags: string[],
  ventId?: string
): Promise<void> {
  try {
    const reviewData = {
      contentId,
      circleId,
      ventId: ventId || null,
      content,
      flags,
      type: ventId ? 'reply' : 'vent',
      status: 'pending',
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      priority: flags.includes('self_harm') ? 'high' : 'normal',
    };

    await db.collection('moderationReviews').add(reviewData);
    console.log(`📋 Scheduled human review for ${ventId ? 'reply' : 'vent'} ${contentId} with flags: ${flags.join(', ')}`);
  } catch (error) {
    console.error('Error scheduling human review:', error);
  }
}

/**
 * Scheduled function to process pending moderation reviews
 * Runs every 2 minutes to ensure reviews are processed quickly
 */
export const processModerationReviews = functions.pubsub
  .schedule('*/2 * * * *') // Every 2 minutes
  .onRun(async (context) => {
    console.log('🔍 Processing moderation reviews at', new Date().toISOString());

    try {
      // Get pending reviews older than 1 minute (to allow for processing time)
      const oneMinuteAgo = new Date(Date.now() - 60 * 1000);
      const reviewsSnapshot = await db
        .collection('moderationReviews')
        .where('status', '==', 'pending')
        .where('createdAt', '<', admin.firestore.Timestamp.fromDate(oneMinuteAgo))
        .limit(10) // Process 10 at a time
        .get();

      if (reviewsSnapshot.empty) {
        console.log('No pending reviews to process');
        return { success: true, processed: 0 };
      }

      let processed = 0;
      for (const reviewDoc of reviewsSnapshot.docs) {
        const review = reviewDoc.data();
        
        // For now, auto-approve if no self-harm flag (human review would happen here)
        // In production, this would notify human moderators
        if (review.flags.includes('self_harm')) {
          // Keep hidden, wait for human review
          console.log(`⚠️  High-priority review (self-harm) for ${review.contentId} - requires human review`);
        } else {
          // Auto-approve after 2 minutes if no self-harm
          await approveContent(review.circleId, review.contentId, review.type, review.ventId);
          await reviewDoc.ref.update({ status: 'approved', reviewedAt: admin.firestore.FieldValue.serverTimestamp() });
          processed++;
        }
      }

      console.log(`✅ Processed ${processed} moderation reviews`);
      return { success: true, processed };
    } catch (error) {
      console.error('❌ Error processing moderation reviews:', error);
      throw error;
    }
  });

/**
 * Approve content (make it visible)
 */
async function approveContent(
  circleId: string,
  contentId: string,
  type: 'vent' | 'reply',
  ventId?: string
): Promise<void> {
  try {
    if (type === 'vent') {
      await db
        .collection('circles')
        .doc(circleId)
        .collection('vents')
        .doc(contentId)
        .update({
          status: 'active',
          moderationStatus: 'approved',
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        });
    } else if (type === 'reply' && ventId) {
      await db
        .collection('circles')
        .doc(circleId)
        .collection('vents')
        .doc(ventId)
        .collection('replies')
        .doc(contentId)
        .update({
          status: 'active',
          moderationStatus: 'approved',
        });

      // Update vent reply count
      const ventDoc = await db.collection('circles').doc(circleId).collection('vents').doc(ventId).get();
      if (ventDoc.exists) {
        await ventDoc.ref.update({
          replyCount: admin.firestore.FieldValue.increment(1),
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        });
      }
    }
  } catch (error) {
    console.error('Error approving content:', error);
  }
}

