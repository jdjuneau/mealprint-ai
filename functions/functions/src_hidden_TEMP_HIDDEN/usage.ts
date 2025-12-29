import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';

// Initialize admin if not already initialized
if (!admin.apps.length) {
  admin.initializeApp();
}
const db = admin.firestore();

export type OpenAIUsageEvent = {
  userId: string;
  date: string; // YYYY-MM-DD
  timestamp: number;
  source: string; // e.g., 'morningBrief', 'monthlyInsights', 'journal', 'dashboardInsight'
  model: string;
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
  estimatedCostUsd?: number; // optional, best-effort
  metadata?: Record<string, any>;
};

/**
 * Best-effort cost estimator. Keep very conservative to avoid surprises.
 * You can refine per model later if desired.
 */
function estimateCostUsd(model: string, promptTokens: number = 0, completionTokens: number = 0): number {
  const inputPer1k = (() => {
    if (model.includes('gpt-4o')) return 0.005;
    if (model.includes('gpt-4')) return 0.01;
    return 0.0005; // gpt-3.5-turbo and similar (very conservative)
  })();
  const outputPer1k = (() => {
    if (model.includes('gpt-4o')) return 0.015;
    if (model.includes('gpt-4')) return 0.03;
    return 0.0015;
  })();
  return (promptTokens / 1000) * inputPer1k + (completionTokens / 1000) * outputPer1k;
}

/**
 * Internal helper to write an event.
 */
export async function logUsage(event: Omit<OpenAIUsageEvent, 'estimatedCostUsd'> & { estimatedCostUsd?: number }) {
  const { userId, date, timestamp, source, model, promptTokens, completionTokens, totalTokens, estimatedCostUsd, metadata } = event;
  
  // CRITICAL: Log that we're being called
  console.log(`[logUsage] 🚨 FUNCTION CALLED: userId=${userId}, date=${date}, source=${source}, model=${model}`);
  
  // CRITICAL: NEVER throw - always log with fallbacks to ensure tracking never fails
  // Use fallback values for missing required fields instead of throwing
  const safeUserId = userId || 'unknown';
  const safeDate = date || new Date().toISOString().split('T')[0];
  const safeModel = model || 'unknown-model';
  
  if (!userId || !date || !model) {
    console.error('[logUsage] ⚠️⚠️⚠️ MISSING REQUIRED FIELDS - USING FALLBACKS ⚠️⚠️⚠️:', { 
      userId: !!userId, 
      date: !!date, 
      model: !!model, 
      source, 
      actualUserId: userId, 
      actualDate: date, 
      actualModel: model,
      usingFallbacks: { userId: safeUserId, date: safeDate, model: safeModel }
    });
    // DO NOT THROW - continue with fallback values to ensure logging happens
  }

  console.log(`[logUsage] 📊 Logging usage: userId=${safeUserId}, date=${safeDate}, source=${source}, model=${safeModel}, tokens=${totalTokens ?? 'N/A'}`);
  
  const cost = typeof estimatedCostUsd === 'number'
    ? estimatedCostUsd
    : estimateCostUsd(safeModel, promptTokens ?? 0, completionTokens ?? 0);

  console.log(`[logUsage] 💰 Calculated cost: $${cost.toFixed(6)}`);

  try {
    console.log(`[logUsage] 📝 Attempting Firestore write to openai_usage/${safeUserId}/daily/${safeDate}...`);
    
    const dayRef = db
      .collection('openai_usage')
      .doc(safeUserId)
      .collection('daily')
      .doc(safeDate);

    const eventsRef = dayRef.collection('events').doc();

    console.log(`[logUsage] 📝 Writing day document...`);
    // Create day doc if missing and increment summary counters
    await dayRef.set(
      {
        userId: safeUserId,
        date: safeDate,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        totalTokens: admin.firestore.FieldValue.increment(totalTokens ?? (promptTokens ?? 0) + (completionTokens ?? 0)),
        estimatedCostUsd: admin.firestore.FieldValue.increment(cost),
        eventsCount: admin.firestore.FieldValue.increment(1),
      },
      { merge: true }
    );
    console.log(`[logUsage] ✅ Day document written successfully`);

    console.log(`[logUsage] 📝 Writing event document...`);
    const eventData: any = {
      userId: safeUserId,
      date: safeDate,
      timestamp: timestamp || Date.now(),
      source: source || 'unknown',
      model: safeModel,
      estimatedCostUsd: cost,
    };
    
    // Only include optional fields if they exist
    if (promptTokens !== undefined && promptTokens !== null) {
      eventData.promptTokens = promptTokens;
    }
    if (completionTokens !== undefined && completionTokens !== null) {
      eventData.completionTokens = completionTokens;
    }
    if (totalTokens !== undefined && totalTokens !== null) {
      eventData.totalTokens = totalTokens;
    }
    if (metadata !== undefined && metadata !== null) {
      eventData.metadata = metadata;
    }
    
    await eventsRef.set(eventData);
    console.log(`[logUsage] ✅✅✅ EVENT DOCUMENT WRITTEN SUCCESSFULLY ✅✅✅`);

    console.log(`[logUsage] ✅ Successfully logged to openai_usage/${safeUserId}/daily/${safeDate}/events`);
  } catch (error: any) {
    // CRITICAL: NEVER throw - log error but continue to mirror attempt
    console.error(`[logUsage] ❌❌❌ CRITICAL: Failed to log to openai_usage ❌❌❌`);
    console.error(`[logUsage] Error details:`, {
      userId: safeUserId,
      date: safeDate,
      source,
      model: safeModel,
      errorMessage: error.message,
      errorCode: error.code,
      errorStack: error.stack,
      errorName: error.name,
      errorToString: error.toString()
    });
    // DO NOT THROW - try to mirror anyway, and let calling code continue
    // This ensures the app doesn't break even if logging fails
  }

  // Mirror under logs/{uid}/daily/{date}/ai_usage for easier discovery in existing tree
  try {
    const logsDayRef = db
      .collection('logs')
      .doc(safeUserId)
      .collection('daily')
      .doc(safeDate);

    await logsDayRef.set(
      {
        ai_usage: {
          // track aggregates in the day doc as well
          totalTokens: admin.firestore.FieldValue.increment(totalTokens ?? (promptTokens ?? 0) + (completionTokens ?? 0)),
          estimatedCostUsd: admin.firestore.FieldValue.increment(cost),
          eventsCount: admin.firestore.FieldValue.increment(1),
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        },
      },
      { merge: true }
    );

    await logsDayRef
      .collection('ai_usage')
      .add({
        userId: safeUserId,
        timestamp: timestamp || Date.now(),
        source: source || 'unknown',
        model: safeModel,
        promptTokens: promptTokens ?? null,
        completionTokens: completionTokens ?? null,
        totalTokens: totalTokens ?? null,
        estimatedCostUsd: cost,
        metadata: metadata ?? null,
      });
    
    console.log(`[logUsage] ✅ Successfully mirrored to logs/${safeUserId}/daily/${safeDate}/ai_usage`);
  } catch (e: any) {
    // best-effort mirror; log but don't fail
    console.warn(`[logUsage] ⚠️ Failed to mirror to logs collection (non-critical):`, {
      userId: safeUserId,
      date: safeDate,
      error: e.message
    });
  }
}

/**
 * Callable to allow clients to record estimated usage (when server doesn't have exact token counts).
 * data: {
 *   source: string,
 *   model: string,
 *   promptChars?: number,
 *   completionChars?: number,
 *   promptTokensApprox?: number,
 *   completionTokensApprox?: number,
 *   metadata?: Record<string, any>
 * }
 */
export const logOpenAIUsageEvent = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = context.auth.uid;
  const now = new Date();
  const date = now.toISOString().split('T')[0];
  const timestamp = now.getTime();

  // Very rough token approx if not provided: ~4 chars/token
  const promptTokens = typeof data.promptTokensApprox === 'number'
    ? data.promptTokensApprox
    : (typeof data.promptChars === 'number' ? Math.ceil(data.promptChars / 4) : undefined);
  const completionTokens = typeof data.completionTokensApprox === 'number'
    ? data.completionTokensApprox
    : (typeof data.completionChars === 'number' ? Math.ceil(data.completionChars / 4) : undefined);

  await logUsage({
    userId,
    date,
    timestamp,
    source: data.source || 'client',
    model: data.model || 'gpt-3.5-turbo',
    promptTokens,
    completionTokens,
    totalTokens: typeof promptTokens === 'number' && typeof completionTokens === 'number'
      ? promptTokens + completionTokens
      : undefined,
    metadata: data.metadata,
  });

  return { ok: true };
});


