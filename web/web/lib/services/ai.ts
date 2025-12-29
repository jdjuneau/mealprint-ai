import { openai, getCachedResponse, setCachedResponse } from '../openai'
import { FirebaseService } from './firebase'
import { db } from '../firebase'
import { collection, doc, addDoc, setDoc, serverTimestamp } from 'firebase/firestore'
import type { MealAnalysis, SupplementAnalysis } from '../../types'

// Rate limiting
const RATE_LIMIT_MAX_CALLS = 5
const RATE_LIMIT_WINDOW_HOURS = 1
const rateLimitCalls: string[] = []

const DIETARY_PREFERENCES: Record<
  string,
  { title: string; summary: string; macroGuidance: string; carbsRatio?: number; proteinRatio?: number; fatRatio?: number }
> = {
  balanced: {
    title: 'Balanced / General',
    summary: 'Flexible eating that mixes fruits, vegetables, grains, lean proteins, and healthy fats.',
    macroGuidance: 'Approx. 40-60% carbs, 20-30% protein, 20-35% fat.',
    carbsRatio: 0.50,
    proteinRatio: 0.25,
    fatRatio: 0.25
  },
  vegetarian: {
    title: 'Vegetarian',
    summary: 'No meat, fish, or poultry. Uses plant proteins plus dairy or eggs if desired.',
    macroGuidance: 'Emphasize beans, lentils, tofu, nuts, seeds, and fortified dairy alternatives.',
    carbsRatio: 0.50,
    proteinRatio: 0.25,
    fatRatio: 0.25
  },
  vegan: {
    title: 'Vegan',
    summary: 'Completely plant-based with no animal products.',
    macroGuidance: 'Prioritize legumes, soy, whole grains, fortified foods, B12, and plant omega-3 sources.',
    carbsRatio: 0.55,
    proteinRatio: 0.20,
    fatRatio: 0.25
  },
  pescatarian: {
    title: 'Pescatarian',
    summary: 'Primarily plant-based but includes fish and seafood.',
    macroGuidance: 'Aim for 2-3 servings of fatty fish weekly while limiting high-mercury choices.',
    carbsRatio: 0.45,
    proteinRatio: 0.30,
    fatRatio: 0.25
  },
  mediterranean: {
    title: 'Mediterranean',
    summary: 'Inspired by coastal cuisines featuring olive oil, seafood, legumes, and produce.',
    macroGuidance: 'Highlight extra-virgin olive oil, vegetables, legumes, nuts, and modest dairy.',
    carbsRatio: 0.45,
    proteinRatio: 0.25,
    fatRatio: 0.30
  },
  keto_low_carb: {
    title: 'Keto / Low-Carb',
    summary: 'Keeps daily carbs below 50 g (keto) or 130 g (low-carb).',
    macroGuidance: 'Approx. 70-80% fat, 15-25% protein, 5-10% carbs from non-starchy vegetables.',
    carbsRatio: 0.10,
    proteinRatio: 0.25,
    fatRatio: 0.65
  },
  paleo: {
    title: 'Paleo',
    summary: 'Whole foods similar to ancestral diets.',
    macroGuidance: 'Include meats, fish, eggs, fruits, vegetables, nuts, and seeds; avoid grains, legumes, dairy, refined sugar.',
    carbsRatio: 0.40,
    proteinRatio: 0.30,
    fatRatio: 0.30
  },
  whole30: {
    title: 'Whole30',
    summary: '30-day paleo-style reset with no alcohol, added sugar, or recreated treats.',
    macroGuidance: 'Follow strict paleo guidelines and reintroduce foods after the 30-day period.',
    carbsRatio: 0.40,
    proteinRatio: 0.30,
    fatRatio: 0.30
  },
  high_protein: {
    title: 'High-Protein',
    summary: 'Targets >1.6 g/kg protein, often for athletes or weight loss.',
    macroGuidance: 'Spread lean proteins, dairy, fish, and supplements evenly across meals.',
    carbsRatio: 0.35,
    proteinRatio: 0.40,
    fatRatio: 0.25
  },
  plant_based: {
    title: 'Plant-Based (Flexitarian)',
    summary: 'Mostly plant foods with occasional animal products if desired.',
    macroGuidance: 'Keep 80-90% of intake plant-based and add small servings of animal foods as needed.',
    carbsRatio: 0.55,
    proteinRatio: 0.20,
    fatRatio: 0.25
  },
  carnivore: {
    title: 'Carnivore',
    summary: 'Exclusively animal-based foods, zero plants.',
    macroGuidance: 'Focus on meats, eggs, animal fats, and organ meats with minimal seasonings beyond salt.',
    carbsRatio: 0.05,
    proteinRatio: 0.30,
    fatRatio: 0.65
  }
}

export class AIService {
  /**
   * Log AI usage to Firestore for dashboard tracking
   * Matches Android app logging format
   */
  private static async logAIUsage(
    userId: string,
    model: string,
    promptTokens: number,
    completionTokens: number,
    source: string
  ): Promise<void> {
    try {
      const today = new Date().toISOString().split('T')[0]
      const totalTokens = promptTokens + completionTokens
      
      // Calculate cost based on model (approximate)
      let costPerToken = 0.000002 // Default for gpt-3.5-turbo
      if (model.includes('gpt-4o-mini')) {
        costPerToken = 0.00000015 // $0.15 per 1M tokens
      } else if (model.includes('gpt-4o')) {
        costPerToken = 0.000005 // $5 per 1M tokens
      } else if (model.includes('gpt-3.5')) {
        costPerToken = 0.000002 // $2 per 1M tokens
      }
      
      const estimatedCostUsd = totalTokens * costPerToken
      
      // Log to logs collection (matches Android format)
      const logsRef = collection(db, 'logs', userId, 'daily', today, 'ai_usage')
      await addDoc(logsRef, {
        userId,
        timestamp: Date.now(),
        source,
        model,
        promptTokens,
        completionTokens,
        totalTokens,
        estimatedCostUsd,
      })
      
      // Also log to openai_usage collection (matches Cloud Functions format)
      const openaiUsageRef = doc(db, 'openai_usage', userId, 'daily', today)
      const eventsRef = collection(openaiUsageRef, 'events')
      
      // Update day document
      await setDoc(openaiUsageRef, {
        userId,
        date: today,
        updatedAt: serverTimestamp(),
        totalTokens: totalTokens,
        estimatedCostUsd: estimatedCostUsd,
        eventsCount: 1,
      }, { merge: true })
      
      // Add event
      await addDoc(eventsRef, {
        userId,
        date: today,
        timestamp: Date.now(),
        source,
        model,
        promptTokens,
        completionTokens,
        totalTokens,
        estimatedCostUsd,
      })
      
      console.log(`✅ Successfully logged AI usage: userId=${userId}, model=${model}, source=${source}, tokens=${totalTokens}`)
    } catch (error) {
      // Don't fail the API call if logging fails, but log the error
      console.error(`❌ FAILED to log AI usage: userId=${userId}, model=${model}, source=${source}`, error)
      console.error('Error details:', {
        name: (error as any)?.name,
        message: (error as any)?.message,
        code: (error as any)?.code,
        stack: (error as any)?.stack
      })
    }
  }

  private static checkRateLimit(userId: string): boolean {
    const now = Date.now()
    const windowStart = now - (RATE_LIMIT_WINDOW_HOURS * 60 * 60 * 1000)

    // Clean old calls
    const validCalls = rateLimitCalls.filter(timestamp => parseInt(timestamp.split('_')[1]) > windowStart)
    rateLimitCalls.length = 0
    rateLimitCalls.push(...validCalls)

    // Count calls for this user
    const userCalls = validCalls.filter(call => call.startsWith(`${userId}_`)).length

    return userCalls < RATE_LIMIT_MAX_CALLS
  }

  private static recordRateLimitCall(userId: string) {
    rateLimitCalls.push(`${userId}_${Date.now()}`)
  }

  static async analyzeMealImage(
    imageFile: File,
    userId: string
  ): Promise<MealAnalysis | null> {
    try {
      // Check rate limit
      if (!this.checkRateLimit(userId)) {
        throw new Error('AI analysis limit reached. Please try again later. (Max 5 analyses per hour)')
      }

      // Convert image to base64
      const base64Image = await this.fileToBase64(imageFile)

      // Create cache key from image hash (simplified)
      const cacheKey = `meal_${userId}_${imageFile.name}_${imageFile.size}_${imageFile.lastModified}`
      const cached = getCachedResponse(cacheKey)
      if (cached) {
        return JSON.parse(cached)
      }

      this.recordRateLimitCall(userId)

      const prompt = `Analyze this food photo and provide nutritional information.
Estimate the serving size and provide detailed nutritional breakdown.
Return ONLY valid JSON in this exact format:
{
  "food": "specific food description (e.g., 'grilled chicken breast with broccoli')",
  "calories": 350,
  "protein_g": 35,
  "carbs_g": 12,
  "fat_g": 15,
  "confidence": 0.85
}
Be specific about the food type and provide realistic nutritional values based on typical serving sizes.`

      const response = await openai.chat.completions.create({
        model: 'gpt-4o-mini', // Switched from gpt-4o to reduce costs by ~10x for vision tasks
        messages: [
          {
            role: 'user',
            content: [
              { type: 'text', text: prompt },
              {
                type: 'image_url',
                image_url: {
                  url: `data:image/jpeg;base64,${base64Image}`,
                },
              },
            ],
          },
        ],
        max_tokens: 250, // Reduced from 300 to save on output tokens
        temperature: 0.1,
      })

      const text = response.choices[0]?.message?.content
      if (!text) {
        throw new Error('No response from AI service')
      }

      // Log usage for dashboard
      const promptTokens = response.usage?.prompt_tokens || Math.ceil(prompt.length / 4)
      const completionTokens = response.usage?.completion_tokens || Math.ceil(text.length / 4)
      await this.logAIUsage(userId, 'gpt-4o-mini', promptTokens, completionTokens, 'mealAnalysis')

      const analysis = JSON.parse(text) as MealAnalysis

      // Cache the result
      setCachedResponse(cacheKey, JSON.stringify(analysis))

      return analysis
    } catch (error) {
      console.error('Error analyzing meal image:', error)
      throw error
    }
  }

  static async analyzeSupplementImage(
    imageFile: File,
    userId: string
  ): Promise<SupplementAnalysis | null> {
    try {
      // Check rate limit
      if (!this.checkRateLimit(userId)) {
        throw new Error('AI analysis limit reached. Please try again later. (Max 5 analyses per hour)')
      }

      // Convert image to base64
      const base64Image = await this.fileToBase64(imageFile)

      // Create cache key
      const cacheKey = `supplement_${userId}_${imageFile.name}_${imageFile.size}_${imageFile.lastModified}`
      const cached = getCachedResponse(cacheKey)
      if (cached) {
        return JSON.parse(cached)
      }

      this.recordRateLimitCall(userId)

      const prompt = `You are analyzing a photograph of a supplement nutrition facts label. Your task is to READ THE EXACT TEXT visible in the image and extract ONLY what is actually written on the label.

CRITICAL INSTRUCTIONS:
1. DO NOT GUESS, ESTIMATE, or ASSUME any values that are not clearly visible in the image
2. Only extract nutrients that are actually listed on the label you can see
3. Use the exact amounts, units, and names as written on the label
4. If a nutrient is not visible on the label, DO NOT include it
5. Look specifically for the "Supplement Facts" or nutrition information panel
6. Read the serving size and amounts per serving exactly as shown

Common mistakes to avoid:
- Do not assume this is a multivitamin unless it explicitly says so
- Do not add typical vitamin amounts if they're not on the label
- Do not convert units unless the label shows both
- Do not include nutrients that are not actually listed

Return ONLY valid JSON in this exact format:
{
  "supplementName": "exact name as shown on label (or 'Unknown Supplement' if not visible)",
  "nutrients": {
    "EXACT_NUTRIENT_NAME_AS_SHOWN": EXACT_AMOUNT_AS_SHOWN,
    "ANOTHER_EXACT_NUTRIENT": EXACT_AMOUNT,
    // Only include nutrients that are actually visible on the photographed label
  }
}

If you cannot clearly read any nutrition information from the image, return:
{
  "supplementName": "Unreadable Label",
  "nutrients": {}
}`

      const response = await openai.chat.completions.create({
        model: 'gpt-4o-mini', // Switched from gpt-4o to reduce costs by ~10x for vision tasks
        messages: [
          {
            role: 'user',
            content: [
              { type: 'text', text: prompt },
              {
                type: 'image_url',
                image_url: {
                  url: `data:image/jpeg;base64,${base64Image}`,
                },
              },
            ],
          },
        ],
        max_tokens: 400, // Reduced from 500 to save on output tokens
        temperature: 0.1,
      })

      const text = response.choices[0]?.message?.content
      if (!text) {
        throw new Error('No response from AI service')
      }

      // Log usage for dashboard
      const promptTokens = response.usage?.prompt_tokens || Math.ceil(prompt.length / 4)
      const completionTokens = response.usage?.completion_tokens || Math.ceil(text.length / 4)
      await this.logAIUsage(userId, 'gpt-4o-mini', promptTokens, completionTokens, 'supplementAnalysis')

      const analysis = JSON.parse(text) as SupplementAnalysis

      // Cache the result
      setCachedResponse(cacheKey, JSON.stringify(analysis))

      return analysis
    } catch (error) {
      console.error('Error analyzing supplement image:', error)
      throw error
    }
  }

  static async generateChatResponse(
    userId: string,
    userMessage: string,
    userName: string,
    conversationHistory: Array<{ role: 'user' | 'assistant'; content: string }> = []
  ): Promise<string> {
    try {
      const [profile, recentLogs, scoreHistory] = await Promise.all([
        FirebaseService.getUserProfile(userId),
        FirebaseService.getRecentDailyLogs(userId, 7),
        this.getScoreHistoryForContext(userId).catch(() => null), // Don't fail if score fetch fails
      ])

      const logSignature = recentLogs
        .map((log) => `${log.date}_${log.caloriesConsumed ?? 'x'}_${log.steps ?? 'x'}_${log.sleepHours ?? 'x'}`)
        .join('|')
      const profileSignature = profile?.updatedAt?.getTime() ?? 0
      const cacheKey = `chat_${userId}_${userMessage.length}_${userMessage.slice(0, 50)}_${profileSignature}_${logSignature}`
      const cached = getCachedResponse(cacheKey)
      if (cached) {
        return cached
      }

      const context = await this.buildCoachContext(profile, recentLogs, scoreHistory)

      const messages = [
        {
          role: 'system' as const,
          content: `You are Coachie, a no-nonsense AI fitness coach. Base every answer on the user's actual goals and logged data shown below. If information is missing, call that out and tell them exactly what to log next. Provide actionable guidance under 180 words.\n\n${context}\n\nAlways reference specific numbers (calories, steps, sleep, weight, vitamin D, etc.) when available, and keep food or supplement suggestions within the user's dietary preference.`
        },
        ...conversationHistory.slice(-5), // Last 5 messages for context
        {
          role: 'user' as const,
          content: userMessage
        }
      ]

      const response = await openai.chat.completions.create({
        model: 'gpt-3.5-turbo',
        messages,
        max_tokens: 120, // Reduced from 150 to save on output tokens
        temperature: 0.7,
      })

      const text = response.choices[0]?.message?.content || 'I apologize, but I had trouble generating a response. Please try again.'

      // Log usage for dashboard
      const promptTokens = response.usage?.prompt_tokens || Math.ceil(messages.map(m => m.content).join('').length / 4)
      const completionTokens = response.usage?.completion_tokens || Math.ceil(text.length / 4)
      await this.logAIUsage(userId, 'gpt-3.5-turbo', promptTokens, completionTokens, 'chat')

      // Cache the response
      setCachedResponse(cacheKey, text)

      return text
    } catch (error) {
      console.error('Error generating chat response:', error)
      throw error
    }
  }

  static async generateDailyInsight(
    userProfile: any,
    recentLogs: any[],
    userName: string
  ): Promise<string> {
    const userId = userProfile?.id || 'unknown'
    try {
      // Create cache key based on key data
      const logsSummary = recentLogs.map(log => `${log.date}_${log.logs?.length || 0}`).join('_')
      const cacheKey = `insight_${userName}_${logsSummary}`
      const cached = getCachedResponse(cacheKey)
      if (cached) {
        return cached
      }

      const dietInfo = DIETARY_PREFERENCES[(userProfile?.dietaryPreference || 'balanced').toLowerCase()] || DIETARY_PREFERENCES['balanced']

      const prompt = `You are Coachie, an AI fitness coach. Analyze the user's recent activity and provide a personalized daily insight.

User Profile:
- Name: ${userName}
- Current Weight: ${userProfile.currentWeight}kg
- Goal Weight: ${userProfile.goalWeight}kg
- Activity Level: ${userProfile.activityLevel}
- Dietary Preference: ${dietInfo.title} (${dietInfo.summary})

Recent Activity Summary:
${this.formatRecentActivity(recentLogs)}

Provide a concise, encouraging insight (under 200 words) about their progress, patterns, and suggestions for improvement. Keep all nutrition guidance aligned with their dietary preference and focus on actionable advice.`

      const response = await openai.chat.completions.create({
        model: 'gpt-3.5-turbo',
        messages: [{ role: 'user', content: prompt }],
        max_tokens: 150, // Reduced from 200 to save on output tokens
        temperature: 0.6,
      })

      const text = response.choices[0]?.message?.content || 'Keep up the great work with your fitness goals!'

      // Log usage for dashboard
      const promptTokens = response.usage?.prompt_tokens || Math.ceil(prompt.length / 4)
      const completionTokens = response.usage?.completion_tokens || Math.ceil(text.length / 4)
      await this.logAIUsage(userId, 'gpt-3.5-turbo', promptTokens, completionTokens, 'dailyInsight')

      // Cache the result (insights change less frequently)
      setCachedResponse(cacheKey, text)

      return text
    } catch (error) {
      console.error('Error generating daily insight:', error)
      throw error
    }
  }

  private static async fileToBase64(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => {
        const result = reader.result as string
        // Remove the data:image/jpeg;base64, prefix
        const base64 = result.split(',')[1]
        resolve(base64)
      }
      reader.onerror = reject
      reader.readAsDataURL(file)
    })
  }

  private static async getImageHash(file: File): Promise<string> {
    // Simplified hash - in production, you'd want a proper image hash
    const buffer = await file.arrayBuffer()
    const hashBuffer = await crypto.subtle.digest('SHA-256', buffer)
    const hashArray = Array.from(new Uint8Array(hashBuffer))
    return hashArray.map(b => b.toString(16).padStart(2, '0')).join('').substring(0, 16)
  }

  private static formatRecentActivity(logs: any[]): string {
    // Group logs by date and summarize
    const summary: string[] = []

    // This would be more sophisticated in a real implementation
    const meals = logs.filter(log => log.type === 'meal').length
    const workouts = logs.filter(log => log.type === 'workout').length
    const totalCalories = logs
      .filter(log => log.type === 'meal')
      .reduce((sum, log) => sum + (log.calories || 0), 0)

    summary.push(`Meals logged: ${meals}`)
    summary.push(`Workouts: ${workouts}`)
    summary.push(`Calories consumed: ${totalCalories}`)

    return summary.join('\n')
  }

  private static async getScoreHistoryForContext(userId: string): Promise<any> {
    try {
      const { httpsCallable } = await import('firebase/functions')
      const { functions } = await import('../firebase')
      const getScoreHistory = httpsCallable(functions, 'getScoreHistory')
      const result = await getScoreHistory({ days: 30 })
      return result.data
    } catch (error) {
      console.warn('Failed to fetch score history for AI context:', error)
      return null
    }
  }

  private static async buildCoachContext(profile: any | null, recentLogs: any[], scoreHistory: any): Promise<string> {
    const profileLines: string[] = []
    const macroTargets = this.calculateMacroTargets(profile)

    if (profile) {
      const weightDiff = profile.goalWeight
        ? (profile.currentWeight - profile.goalWeight).toFixed(1)
        : null
      profileLines.push(`Name: ${profile.name || 'Unknown'}`)
      profileLines.push(`Current weight: ${profile.currentWeight ?? 'N/A'} kg`)
      profileLines.push(`Goal weight: ${profile.goalWeight ?? 'N/A'} kg${weightDiff ? ` (difference ${weightDiff} kg)` : ''}`)
      profileLines.push(`Activity level: ${profile.activityLevel ?? 'N/A'}`)
      if (profile.estimatedDailyCalories) {
        profileLines.push(`Estimated daily calorie target: ${profile.estimatedDailyCalories} kcal`)
      }
      const preferenceKey = (profile.dietaryPreference || 'balanced').toLowerCase()
      const preference = DIETARY_PREFERENCES[preferenceKey]
      if (preference) {
        profileLines.push(`Dietary preference: ${preference.title}`)
        profileLines.push(`Diet summary: ${preference.summary}`)
        profileLines.push(`Diet macros: ${preference.macroGuidance}`)
      }
      if (profile.goalTrend) {
        profileLines.push(`Goal trend: ${profile.goalTrend}`)
      }
      if (profile.ingredientsOnHand && Array.isArray(profile.ingredientsOnHand) && profile.ingredientsOnHand.length > 0) {
        profileLines.push(`Ingredients on hand: ${profile.ingredientsOnHand.join(', ')}`)
      }
      profileLines.push(`Macro targets: Protein ${macroTargets.protein} g, Carbs ${macroTargets.carbs} g, Fat ${macroTargets.fat} g`)
    } else {
      profileLines.push('No profile information available. Encourage the user to set their profile and goals.')
    }

    const logLines: string[] = []
    if (recentLogs && recentLogs.length > 0) {
      recentLogs.forEach((log) => {
        const calories = log.caloriesConsumed ?? 'n/a'
        const caloriesBurned = log.caloriesBurned ?? 'n/a'
        const steps = log.steps ?? 'n/a'
        const sleep = log.sleepHours ?? 'n/a'
        const water = log.waterAmount ?? 'n/a'
        const extras = log.micronutrientExtras ?? {}
        const vitaminD = extras['vitamin_d'] ?? extras['vitaminD'] ?? extras['vitamin_d3'] ?? 'n/a'
        const weight =
          log.weight ??
          log.logs?.find((entry: any) => entry.type === 'weight')?.weight ??
          'n/a'

        let calorieGap: string | null = null
        if (macroTargets.calories && log.caloriesConsumed != null) {
          const remaining = macroTargets.calories - log.caloriesConsumed
          calorieGap = `${remaining > 0 ? '+' : ''}${remaining}`
        }

        logLines.push(
          `${log.date}: calories ${calories}${calorieGap ? ` (goal gap ${calorieGap})` : ''}, burned ${caloriesBurned}, steps ${steps}, sleep ${sleep}h, water ${water}ml, vitamin D ${vitaminD}, weight ${weight}kg`
        )
      })
    } else {
      logLines.push('No recent daily logs found. Ask the user to log meals, workouts, sleep, water, and supplements.')
    }

    // Add score context if available
    let scoreContext = ''
    if (scoreHistory && scoreHistory.success && scoreHistory.scores && scoreHistory.scores.length > 0) {
      const { trend, stats } = scoreHistory
      const todayScore = scoreHistory.scores[scoreHistory.scores.length - 1]?.score
      const trendEmoji = trend.direction === 'up' ? '📈' : trend.direction === 'down' ? '📉' : '➡️'
      const trendText = trend.direction === 'up' 
        ? `improving (+${Math.abs(trend.changePercent).toFixed(1)}%)`
        : trend.direction === 'down'
        ? `declining (${trend.changePercent.toFixed(1)}%)`
        : 'stable'
      
      scoreContext = `\n\nCOACHIE SCORE TRENDS:\n`
      scoreContext += `- Today's Score: ${todayScore || 'N/A'}/100\n`
      scoreContext += `- 7-Day Average: ${stats.last7DaysAverage?.toFixed(1) || 'N/A'}/100\n`
      scoreContext += `- 30-Day Average: ${stats.last30DaysAverage?.toFixed(1) || 'N/A'}/100\n`
      scoreContext += `- Overall Average: ${stats.average.toFixed(1)}/100\n`
      scoreContext += `- Highest Score: ${stats.highest}/100${stats.highestDate ? ` (on ${new Date(stats.highestDate).toLocaleDateString()})` : ''}\n`
      scoreContext += `- Trend: ${trendEmoji} ${trendText} (${trend.change > 0 ? '+' : ''}${trend.change.toFixed(1)} points vs previous week)\n`
      if (trend.streak > 0) {
        scoreContext += `- Streak: ${trend.streak} day ${trend.streakType === 'improving' ? 'improvement' : 'decline'} streak 🔥\n`
      }
      scoreContext += `\nUse this score data to:`
      scoreContext += `\n- Celebrate improvements and encourage continued progress`
      scoreContext += `\n- Identify declining trends and provide specific actionable advice to improve`
      scoreContext += `\n- Reference specific score numbers when motivating the user`
      scoreContext += `\n- Suggest concrete actions to improve their score (e.g., "Your score dropped 5 points this week - let's focus on logging meals and getting 8 hours of sleep")`
    }

    return `USER PROFILE:\n${profileLines.join('\n')}\n\nRECENT DAILY METRICS (most recent first):\n${logLines.join('\n')}${scoreContext}`
  }

  private static calculateMacroTargets(profile: any | null): { calories: number | null; protein: number; carbs: number; fat: number } {
    if (!profile) {
      return { calories: null, protein: 0, carbs: 0, fat: 0 }
    }

    const calories: number = profile.estimatedDailyCalories ?? 2000
    const preferenceKey = (profile.dietaryPreference || 'balanced').toLowerCase()
    const preference = DIETARY_PREFERENCES[preferenceKey] || DIETARY_PREFERENCES['balanced']

    let carbsRatio = preference.carbsRatio ?? 0.45
    let proteinRatio = preference.proteinRatio ?? 0.25
    let fatRatio = preference.fatRatio ?? 0.30

    const goalTrend = profile.goalTrend || 'maintain_weight'
    if (preferenceKey !== 'keto_low_carb' && preferenceKey !== 'carnivore') {
      if (goalTrend === 'lose_weight') {
        proteinRatio += 0.05
        carbsRatio -= 0.05
      } else if (goalTrend === 'gain_weight') {
        carbsRatio += 0.05
        fatRatio += 0.02
        proteinRatio -= 0.02
      }
    }

    carbsRatio = Math.min(Math.max(carbsRatio, 0), 0.65)
    proteinRatio = Math.min(Math.max(proteinRatio, 0.15), 0.45)
    fatRatio = Math.min(Math.max(fatRatio, 0.2), 0.8)

    const total = carbsRatio + proteinRatio + fatRatio || 1
    carbsRatio /= total
    proteinRatio /= total
    fatRatio /= total

    return {
      calories,
      protein: Math.round((calories * proteinRatio) / 4),
      carbs: Math.round((calories * carbsRatio) / 4),
      fat: Math.round((calories * fatRatio) / 9),
    }
  }
}
