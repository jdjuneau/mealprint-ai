import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';
import OpenAI from 'openai';
import { logUsage } from './usage';
import * as messaging from 'firebase-admin/messaging';

// Initialize admin if not already initialized
if (!admin.apps.length) {
  admin.initializeApp();
}
const db = admin.firestore();

// Lazy initialization of OpenAI - only when needed and API key is available
function getOpenAIClient(): OpenAI | null {
  // Migration: process.env first (future-proof), functions.config() as fallback
  const apiKey = process.env.OPENAI_API_KEY || functions.config().openai?.key;
  if (!apiKey) {
    return null;
  }
  return new OpenAI({ apiKey });
}

/**
 * Get AI-suggested quests for a user
 */
export const getUserQuests = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = context.auth.uid;

  try {
    // CRITICAL: Check cache first - quests don't change frequently, cache for 24 hours
    const today = new Date().toISOString().split('T')[0];
    const cacheDoc = await db
      .collection('users')
      .doc(userId)
      .collection('cache')
      .doc(`user_quests_${today}`)
      .get();
    
    if (cacheDoc.exists) {
      const cached = cacheDoc.data();
      const cachedAt = cached?.cachedAt?.toMillis() || 0;
      const now = Date.now();
      const hoursSinceCache = (now - cachedAt) / (1000 * 60 * 60);
      
      // Use cache if it's less than 24 hours old
      if (hoursSinceCache < 24 && cached?.quests) {
        const cachedActiveQuests = cached.quests.activeQuests || [];
        
        // CRITICAL: Even with cache, check if we need to maintain 3-5 active quests
        if (cachedActiveQuests.length < 3) {
          console.log(`🔄 Cache found but only ${cachedActiveQuests.length} active quests. Generating new quests...`);
          // Invalidate cache and generate new quests
          await cacheDoc.ref.delete();
          // Continue to quest generation logic below
        } else {
          console.log(`✅ Returning cached quests for ${userId} (cached ${hoursSinceCache.toFixed(1)}h ago)`);
          return {
            activeQuests: cachedActiveQuests,
            completedQuests: cached.quests.completedQuests || [],
            cached: true
          };
        }
      }
    }

    // Get user's active habits and completions
    const habitsSnapshot = await db
      .collection('users')
      .doc(userId)
      .collection('habits')
      .where('isActive', '==', true)
      .get();

    const habits = habitsSnapshot.docs.map((doc) => ({
      id: doc.id,
      ...doc.data(),
    }));

    // Get recent completions (for AI quest generation)
    // Query without orderBy to avoid index requirement, then sort in memory
    const completionsSnapshot = await db
      .collection('users')
      .doc(userId)
      .collection('habitCompletions')
      .get();

    // Sort by completedAt in memory and limit to 100
    const completions = completionsSnapshot.docs
      .map((doc) => doc.data())
      .sort((a: any, b: any) => {
        const aTime = a.completedAt?.toMillis?.() || a.completedAt?._seconds * 1000 || 0;
        const bTime = b.completedAt?.toMillis?.() || b.completedAt?._seconds * 1000 || 0;
        return bTime - aTime; // Descending order
      })
      .slice(0, 100);

    // Get user profile and goals
    const profileDoc = await db
      .collection('users')
      .doc(userId)
      .get();

    const profile = profileDoc.exists ? profileDoc.data() : {};

    // Generate quests using OpenAI
    const quests: any[] = await generateQuestsWithAI(habits, completions, profile, userId);

    // Get user's existing quests - query all and filter in memory to avoid index requirement
    const allQuestsSnapshot = await db
      .collection('users')
      .doc(userId)
      .collection('quests')
      .get();

    // Filter for active/in_progress quests in memory
    const existingQuests = allQuestsSnapshot.docs
      .map((doc) => ({
        id: doc.id,
        ...doc.data(),
      }))
      .filter((quest: any) => quest.status === 'active' || quest.status === 'in_progress');

    // If user has existing quests, check if we need to generate more to maintain 3-5 active quests
    if (existingQuests.length > 0) {
      // AUTO-GENERATE: If we have fewer than 3 active quests, generate new ones
      if (existingQuests.length < 3) {
        const questsNeeded = Math.min(5 - existingQuests.length, 3); // Generate up to 3 new quests
        console.log(`🔄 User has ${existingQuests.length} active quests. Generating ${questsNeeded} new quest(s) to maintain 3-5...`);
        await generateAndSaveNewQuests(userId, questsNeeded);
        
        // Re-fetch active quests after generation
        const updatedQuestsSnapshot = await db
          .collection('users')
          .doc(userId)
          .collection('quests')
          .get();
        
        existingQuests.length = 0; // Clear array
        existingQuests.push(...updatedQuestsSnapshot.docs
          .map((doc) => ({ id: doc.id, ...doc.data() }))
          .filter((quest: any) => quest.status === 'active' || quest.status === 'in_progress'));
      }

      // Query without orderBy to avoid index requirement, then sort in memory
      const completedQuestsSnapshot = await db
        .collection('users')
        .doc(userId)
        .collection('quests')
        .where('status', '==', 'completed')
        .get();

      // Sort by completedAt in memory and limit to 10
      const completedQuests = completedQuestsSnapshot.docs
        .map((doc) => ({
          id: doc.id,
          ...doc.data(),
        }))
        .sort((a: any, b: any) => {
          const aTime = a.completedAt?.toMillis?.() || a.completedAt?._seconds * 1000 || 0;
          const bTime = b.completedAt?.toMillis?.() || b.completedAt?._seconds * 1000 || 0;
          return bTime - aTime; // Descending order
        })
        .slice(0, 10);

      const result = {
        activeQuests: existingQuests,
        completedQuests,
        cached: false
      };

      // Cache the result for 24 hours
      try {
        await db
          .collection('users')
          .doc(userId)
          .collection('cache')
          .doc(`user_quests_${today}`)
          .set({
            quests: result,
            cachedAt: admin.firestore.FieldValue.serverTimestamp(),
            userId,
            date: today
          });
        console.log(`✅ Cached quests for ${userId} for ${today}`);
      } catch (cacheError) {
        console.error('Failed to cache quests (non-critical):', cacheError);
      }

      return result;
    }

    // Create new quests in Firestore
    const createdQuests = [];
    for (const quest of quests) {
      const questDoc = await db
        .collection('users')
        .doc(userId)
        .collection('quests')
        .add({
          ...quest,
          status: 'active',
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        });

      createdQuests.push({
        id: questDoc.id,
        ...quest,
        status: 'active',
        progress: quest.current * 100 / quest.target,
      });
    }

    const result = {
      activeQuests: createdQuests,
      completedQuests: [],
      cached: false
    };

    // Cache the result for 24 hours
    try {
      await db
        .collection('users')
        .doc(userId)
        .collection('cache')
        .doc(`user_quests_${today}`)
        .set({
          quests: result,
          cachedAt: admin.firestore.FieldValue.serverTimestamp(),
          userId,
          date: today
        });
      console.log(`✅ Cached quests for ${userId} for ${today}`);
    } catch (cacheError) {
      console.error('Failed to cache quests (non-critical):', cacheError);
    }

    return result;
  } catch (error: any) {
    console.error('Error getting user quests:', error);
    const errorMessage = error?.message || 'Unknown error';
    const errorDetails = error?.stack || '';
    console.error('Error details:', errorDetails);
    throw new functions.https.HttpsError('internal', `Failed to get quests: ${errorMessage}`);
  }
});

/**
 * Generate AI-suggested quests using OpenAI
 */
async function generateQuestsWithAI(
  habits: any[],
  completions: any[],
  profile: any,
  userId: string
): Promise<Array<{
  id: string;
  title: string;
  description: string;
  target: number;
  current: number;
  type: 'habit' | 'streak' | 'goal' | 'challenge';
  icon: string;
  color: string;
  reward?: string;
}>> {
  try {
    // Analyze habits and completions
    const habitStreaks = habits.map((habit) => ({
      id: habit.id,
      title: habit.title,
      streak: habit.streakCount || 0,
    }));

    const prompt = `Generate 3-5 personalized quests for a user based on their habits and goals.

Active Habits:
${habitStreaks.map((h) => `- ${h.title}: ${h.streak}-day streak`).join('\n')}

User Goals: ${JSON.stringify(profile.habitGoals || {})}

Generate quests in JSON format. Each quest should have:
- id: unique identifier
- title: short, engaging title (max 40 chars)
- description: what needs to be done
- target: number to reach (e.g., 30 days, 10000 steps)
- current: current progress (0 for new quests)
- type: "habit", "streak", "goal", or "challenge"
- icon: Ionicons icon name (e.g., "water", "fitness", "medal")
- color: hex color code
- reward (optional): reward description

Focus on:
- Building on existing habits
- Achievable but challenging goals
- Variety (not all the same type)
- User's stated goals

Return ONLY valid JSON array, no markdown:
[
  {
    "id": "quest-1",
    "title": "30-Day Hydration Quest",
    "description": "Drink 8 glasses of water daily for 30 days",
    "target": 30,
    "current": 0,
    "type": "habit",
    "icon": "water",
    "color": "#3B82F6"
  },
  ...
]`;

    const openai = getOpenAIClient();
    if (!openai) {
      throw new functions.https.HttpsError('failed-precondition', 'OpenAI API key not configured. Please configure OpenAI API key in Firebase Functions.');
    }

    // Use cheaper model and reduce tokens to save costs
    const response = await openai.chat.completions.create({
      model: 'gpt-3.5-turbo', // Changed from gpt-4o-mini to save ~10x on costs
      messages: [
        {
          role: 'system',
          content: 'You are an AI wellness coach. Generate personalized quests in JSON format only.',
        },
        { role: 'user', content: prompt },
      ],
      temperature: 0.7,
      max_tokens: 800, // Reduced from 1500 to save on output tokens
    });

    // Log OpenAI usage for cost tracking (userId passed from caller)
    const date = new Date().toISOString().split('T')[0];
    try {
      await logUsage({
        userId: userId || 'system',
        date,
        timestamp: Date.now(),
        source: 'getUserQuests',
        model: 'gpt-3.5-turbo', // Changed from gpt-4o-mini to save costs
        promptTokens: response.usage?.prompt_tokens,
        completionTokens: response.usage?.completion_tokens,
        totalTokens: response.usage?.total_tokens,
      });
    } catch (logError) {
      console.error('Failed to log quest generation usage:', logError);
      // Don't fail the request if logging fails
    }

    const content = response.choices[0]?.message?.content || '[]';
    let quests;
    try {
      // Try to parse JSON, handling markdown code blocks
      const cleanedContent = content.replace(/```json\n?/g, '').replace(/```\n?/g, '').trim();
      quests = JSON.parse(cleanedContent);
    } catch (parseError) {
      console.error('Failed to parse OpenAI response as JSON:', parseError);
      console.error('Response content:', content);
      // Fall through to fallback quests
      quests = [];
    }

    // Validate and return quests
    if (Array.isArray(quests) && quests.length > 0) {
      return quests.slice(0, 5); // Max 5 quests
    }

    // No valid quests generated - throw error
    throw new functions.https.HttpsError('internal', 'Failed to generate valid quests from AI. Please try again.');
  } catch (error: any) {
    console.error('Error generating quests with AI:', error);
    // Re-throw if it's already an HttpsError
    if (error instanceof functions.https.HttpsError) {
      throw error;
    }
    // Otherwise wrap in HttpsError
    throw new functions.https.HttpsError('internal', `Failed to generate quests: ${error?.message || 'Unknown error'}`);
  }
}

/**
 * Helper function to generate and save new quests to maintain 3-5 active quests
 */
async function generateAndSaveNewQuests(userId: string, targetCount: number = 3): Promise<void> {
  try {
    // Get user's active habits and completions for AI generation
    const habitsSnapshot = await db
      .collection('users')
      .doc(userId)
      .collection('habits')
      .where('isActive', '==', true)
      .get();

    const habits = habitsSnapshot.docs.map((doc) => ({
      id: doc.id,
      ...doc.data(),
    }));

    const completionsSnapshot = await db
      .collection('users')
      .doc(userId)
      .collection('habitCompletions')
      .get();

    const completions = completionsSnapshot.docs
      .map((doc) => doc.data())
      .sort((a: any, b: any) => {
        const aTime = a.completedAt?.toMillis?.() || a.completedAt?._seconds * 1000 || 0;
        const bTime = b.completedAt?.toMillis?.() || b.completedAt?._seconds * 1000 || 0;
        return bTime - aTime;
      })
      .slice(0, 100);

    const profileDoc = await db
      .collection('users')
      .doc(userId)
      .get();

    const profile = profileDoc.exists ? profileDoc.data() : {};

    // Generate quests using AI
    const newQuests = await generateQuestsWithAI(habits, completions, profile, userId);

    // Save new quests to Firestore (limit to targetCount)
    const questsToCreate = newQuests.slice(0, targetCount);
    for (const quest of questsToCreate) {
      await db
        .collection('users')
        .doc(userId)
        .collection('quests')
        .add({
          ...quest,
          status: 'active',
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        });
    }

    console.log(`✅ Generated and saved ${questsToCreate.length} new quests for user ${userId}`);

    // Invalidate quest cache to force refresh
    const today = new Date().toISOString().split('T')[0];
    const cacheRef = db
      .collection('users')
      .doc(userId)
      .collection('cache')
      .doc(`user_quests_${today}`);
    
    await cacheRef.delete();
    console.log(`🗑️ Invalidated quest cache for user ${userId}`);
  } catch (error) {
    console.error('Error generating new quests:', error);
    // Don't throw - this is a background enhancement, shouldn't fail the main operation
  }
}

/**
 * Update quest progress
 */
export const updateQuestProgress = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = context.auth.uid;
  const { questId, progress, notes } = data;

  if (!questId || typeof progress !== 'number') {
    throw new functions.https.HttpsError('invalid-argument', 'Quest ID and progress are required');
  }

  try {
    const questRef = db
      .collection('users')
      .doc(userId)
      .collection('quests')
      .doc(questId);

    const questDoc = await questRef.get();
    if (!questDoc.exists) {
      throw new functions.https.HttpsError('not-found', 'Quest not found');
    }

    const questData = questDoc.data()!;
    const newCurrent = Math.min(progress, questData.target);

    // Update quest progress
    await questRef.update({
      current: newCurrent,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      ...(notes && { notes }),
    });

    // Check if quest is completed
    const isCompleted = newCurrent >= questData.target;
    if (isCompleted) {
      await questRef.update({
        status: 'completed',
        completedAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      const questTitle = questData.title || 'Quest';
      const questDescription = questData.description || '';

      // 🎉 QUEST COMPLETION CELEBRATIONS 🎉
      
      // 1. Send notification
      try {
        const userDoc = await db.collection('users').doc(userId).get();
        const userData = userDoc.data();
        const fcmToken = userData?.fcmToken;
        
        if (fcmToken) {
          await messaging.getMessaging().send({
            token: fcmToken,
            notification: {
              title: '🎉 Quest Completed!',
              body: `You completed "${questTitle}"! Great job!`,
            },
            data: {
              type: 'quest_completed',
              questId: questId,
              questTitle: questTitle,
              deepLink: 'coachie://quests',
              screen: 'Quests',
            },
            android: {
              priority: 'high',
              notification: {
                sound: 'default',
                channelId: 'coachie_messages',
              },
            },
            apns: {
              payload: {
                aps: {
                  sound: 'default',
                  badge: 1,
                },
              },
            },
          });
          console.log(`✅ Sent quest completion notification to ${userId}`);
        }
      } catch (notifError) {
        console.error('Error sending quest completion notification:', notifError);
        // Don't fail the quest completion if notification fails
      }

      // 2. Create Win of the Day entry
      try {
        const today = new Date().toISOString().split('T')[0];
        const winEntry = {
          entryId: `quest_${questId}_${Date.now()}`,
          journalEntryId: 'quest_win', // Mark as quest-based win
          date: today,
          win: `Completed quest: "${questTitle}"! ${questDescription}`,
          gratitude: null,
          mood: null,
          moodScore: null,
          tags: ['quest', 'achievement', questData.type || 'challenge'],
          timestamp: admin.firestore.FieldValue.serverTimestamp(),
          type: 'win',
        };

        await db
          .collection('logs')
          .doc(userId)
          .collection('daily')
          .doc(today)
          .collection('entries')
          .add(winEntry);
        
        console.log(`✅ Created Win of the Day entry for quest completion: ${questTitle}`);
      } catch (winError) {
        console.error('Error creating quest win entry:', winError);
        // Don't fail the quest completion if win creation fails
      }

      // 3. Award flow points (flow score is calculated from wins, so this happens automatically)
      // Flow points are calculated from wins in DailyScoreCalculator, so creating the win above
      // will automatically contribute to the flow score

      // AUTO-GENERATE NEW QUESTS: Check if we need to maintain 3-5 active quests
      const allQuestsSnapshot = await db
        .collection('users')
        .doc(userId)
        .collection('quests')
        .get();

      const activeQuests = allQuestsSnapshot.docs
        .map((doc) => ({ id: doc.id, ...doc.data() }))
        .filter((quest: any) => quest.status === 'active' || quest.status === 'in_progress');

      // If we have fewer than 3 active quests, generate new ones to bring count to 3-5
      if (activeQuests.length < 3) {
        const questsNeeded = Math.min(5 - activeQuests.length, 3); // Generate up to 3 new quests
        console.log(`🔄 Quest completed! Active quests: ${activeQuests.length}. Generating ${questsNeeded} new quest(s)...`);
        await generateAndSaveNewQuests(userId, questsNeeded);
      }
    }

    return {
      success: true,
      completed: isCompleted,
      progress: newCurrent,
    };
  } catch (error) {
    console.error('Error updating quest progress:', error);
    throw new functions.https.HttpsError('internal', 'Failed to update quest progress');
  }
});

/**
 * Complete a quest manually
 */
export const completeQuest = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = context.auth.uid;
  const { questId } = data;

  if (!questId) {
    throw new functions.https.HttpsError('invalid-argument', 'Quest ID is required');
  }

  try {
    const questRef = db
      .collection('users')
      .doc(userId)
      .collection('quests')
      .doc(questId);

    const questDoc = await questRef.get();
    if (!questDoc.exists) {
      throw new functions.https.HttpsError('not-found', 'Quest not found');
    }

    const questData = questDoc.data()!;

    // Complete the quest
    await questRef.update({
      status: 'completed',
      current: questData.target,
      completedAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    return {
      success: true,
      questId,
    };
  } catch (error) {
    console.error('Error completing quest:', error);
    throw new functions.https.HttpsError('internal', 'Failed to complete quest');
  }
});

/**
 * Reset quests (generate new ones)
 */
export const resetQuests = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = context.auth.uid;

  try {
    // Mark existing active quests as expired - query all and filter in memory to avoid index requirement
    const allQuestsSnapshot = await db
      .collection('users')
      .doc(userId)
      .collection('quests')
      .get();
    
    // Filter for active/in_progress quests in memory
    const activeQuestsSnapshot = {
      docs: allQuestsSnapshot.docs.filter((doc) => {
        const data = doc.data();
        return data.status === 'active' || data.status === 'in_progress';
      }),
      size: 0
    };
    activeQuestsSnapshot.size = activeQuestsSnapshot.docs.length;

    const batch = db.batch();
    activeQuestsSnapshot.docs.forEach((doc) => {
      batch.update(doc.ref, {
        status: 'expired',
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    });

    await batch.commit();

    // This will trigger new quest generation on next getUserQuests call
    return {
      success: true,
      expiredCount: activeQuestsSnapshot.size,
    };
  } catch (error) {
    console.error('Error resetting quests:', error);
    throw new functions.https.HttpsError('internal', 'Failed to reset quests');
  }
});

// @ts-ignore - Fallback function kept for potential future use
function buildFallbackQuests(habits: any[], profile: any) {
  const topHabit = habits[0]?.title || 'Daily Walk';
  return [
    {
      id: 'q_hydration',
      title: '7-Day Hydration',
      description: 'Drink 8 glasses of water daily for 7 days',
      target: 7,
      current: 0,
      type: 'habit',
      icon: 'water',
      color: '#3B82F6',
    },
    {
      id: 'q_steps',
      title: '10k Steps Streak',
      description: 'Hit 10,000 steps 5 days this week',
      target: 5,
      current: 0,
      type: 'goal',
      icon: 'walk',
      color: '#10B981',
    },
    {
      id: 'q_top_habit',
      title: `Keep ${topHabit}`,
      description: `Complete "${topHabit}" 10 times`,
      target: 10,
      current: 0,
      type: 'habit',
      icon: 'fitness',
      color: '#F59E0B',
    },
  ];
}

