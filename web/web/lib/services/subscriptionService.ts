/**
 * Subscription Service (Web Version)
 * Ported from Android SubscriptionService.kt
 * Handles subscription tiers, billing, and feature access
 */

import { db } from '../firebase'
import { doc, getDoc, setDoc, updateDoc, Timestamp } from 'firebase/firestore'

export type SubscriptionTier = 'free' | 'pro'
export type PaymentProvider = 'stripe' | 'paypal' | 'google_play' | 'app_store' | null

export interface SubscriptionInfo {
  tier: SubscriptionTier
  status: 'active' | 'canceled' | 'expired' | 'trial'
  startDate: Date
  endDate?: Date
  billingCycle?: 'monthly' | 'yearly'
  paymentProvider?: PaymentProvider // Cross-platform: stripe, paypal, google_play, app_store
  paymentMethod?: string
  subscriptionId?: string // Provider-specific subscription ID
  cancelAtPeriodEnd?: boolean
  platforms?: string[] // All platforms user has subscribed from
  isActive?: boolean // Cross-platform compatibility (Android sets this field)
}

class SubscriptionService {
  private static instance: SubscriptionService

  private constructor() {}

  static getInstance(): SubscriptionService {
    if (!SubscriptionService.instance) {
      SubscriptionService.instance = new SubscriptionService()
    }
    return SubscriptionService.instance
  }

  /**
   * Get user's subscription info
   */
  async getSubscription(userId: string): Promise<SubscriptionInfo | null> {
    try {
      const userRef = doc(db, 'users', userId)
      const userSnap = await getDoc(userRef)

      if (userSnap.exists()) {
        const data = userSnap.data()
        const subscription = data.subscription

      if (subscription) {
        // CRITICAL: Handle both expiresAt (number) and endDate (Firestore Timestamp)
        // Dashboard tool uses expiresAt (number), Stripe/PayPal use endDate (Timestamp)
        let endDate: Date | undefined
        if (subscription.endDate) {
          endDate = subscription.endDate.toDate ? subscription.endDate.toDate() : new Date(subscription.endDate)
        } else if (subscription.expiresAt) {
          // expiresAt is a number (milliseconds) from dashboard tool or Android
          endDate = typeof subscription.expiresAt === 'number' 
            ? new Date(subscription.expiresAt)
            : new Date(subscription.expiresAt)
        }
        
        // Check if subscription is expired
        const isExpired = endDate ? endDate.getTime() < Date.now() : false
        
        // CRITICAL: Log subscription data for debugging
        console.log('[SubscriptionService] Raw subscription data:', {
          tier: subscription.tier,
          status: subscription.status,
          isActive: subscription.isActive,
          expiresAt: subscription.expiresAt,
          endDate: subscription.endDate,
          parsedEndDate: endDate,
          isExpired,
          platforms: subscription.platforms
        })
        
        return {
          tier: subscription.tier || 'free',
          status: subscription.status || 'active',
          startDate: subscription.startDate?.toDate() || new Date(),
          endDate: endDate,
          billingCycle: subscription.billingCycle,
          paymentProvider: subscription.paymentProvider || null,
          paymentMethod: subscription.paymentMethod,
          subscriptionId: subscription.subscriptionId,
          cancelAtPeriodEnd: subscription.cancelAtPeriodEnd,
          platforms: subscription.platforms || [],
          // CRITICAL: Include isActive for cross-platform compatibility (Android sets this)
          // Subscription is active if: isActive is true AND not expired
          isActive: subscription.isActive !== false && !isExpired,
        }
      }
      }

      // Default to free tier
      return {
        tier: 'free',
        status: 'active',
        startDate: new Date(),
      }
    } catch (error) {
      console.error('Error getting subscription:', error)
      return null
    }
  }

  /**
   * Check if user can use a specific AI feature
   * Matches Android implementation with rate limiting for free tier
   */
  async canUseAIFeature(userId: string, feature: string): Promise<boolean> {
    const subscription = await this.getSubscription(userId)
    if (!subscription) return false

    const isPro = subscription.tier === 'pro' && subscription.status === 'active'

    // Pro-only features (must be Pro to use)
    const proOnlyFeatures = [
      'weekly_blueprint_ai',
      'WEEKLY_BLUEPRINT_AI',
      'morning_brief',
      'MORNING_BRIEF',
      'monthly_insights',
      'MONTHLY_INSIGHTS',
      'quest_generation',
      'QUEST_GENERATION'
    ]

    if (proOnlyFeatures.includes(feature)) {
      return isPro
    }

    // Pro users can use all other features
    if (isPro) {
      return true
    }

    // Free tier: Check rate limits for limited features
    const remainingCalls = await this.getRemainingAICalls(userId, feature)
    return remainingCalls > 0
  }

  /**
   * Get remaining AI calls for free tier users
   * Matches Android SubscriptionService.kt structure and limits
   */
  async getRemainingAICalls(userId: string, feature: string): Promise<number> {
    try {
      // Get daily limit for this feature (matches Android)
      const dailyLimit = this.getDailyLimit(feature)
      
      // Android uses users/{userId}/aiUsage/daily with nested usage map
      const usageRef = doc(db, 'users', userId, 'aiUsage', 'daily')
      const usageSnap = await getDoc(usageRef)
      
      const today = new Date()
      today.setHours(0, 0, 0, 0)
      const todayTimestamp = today.getTime()
      
      if (usageSnap.exists()) {
        const data = usageSnap.data()
        const usage = (data.usage as Record<string, any>) || {}
        const featureUsage = (usage[feature] as Record<string, any>) || {}
        const lastUsed = (featureUsage.lastUsed as number) || 0
        const count = (featureUsage.count as number) || 0
        
        // Reset if new day
        if (lastUsed < todayTimestamp) {
          return dailyLimit
        } else {
          return Math.max(0, dailyLimit - count)
        }
      }
      
      return dailyLimit // Full daily allowance
    } catch (error) {
      console.error('Error getting remaining AI calls:', error)
      return 0
    }
  }

  /**
   * Get daily limit for a specific AI feature (free tier)
   * Matches Android SubscriptionService.kt getDailyLimit()
   */
  private getDailyLimit(feature: string): number {
    switch (feature) {
      case 'meal_recommendation':
      case 'MEAL_RECOMMENDATION':
        return 1 // 1 per day
      case 'daily_insight':
      case 'DAILY_INSIGHT':
        return 1 // 1 per day
      case 'habit_suggestions':
      case 'HABIT_SUGGESTIONS':
        return 5 // 5 per week (tracked daily, so 5 per day max)
      case 'ai_coach_chat':
      case 'AI_COACH_CHAT':
        return 10 // 10 messages per day
      default:
        return 0 // Not available on free tier
    }
  }

  /**
   * Record AI feature usage
   * Matches Android SubscriptionService.kt structure
   */
  async recordAIFeatureUsage(userId: string, feature: string): Promise<void> {
    try {
      const tier = (await this.getSubscription(userId))?.tier
      if (tier === 'pro') {
        return // No need to track for Pro users
      }
      
      const today = new Date()
      today.setHours(0, 0, 0, 0)
      const todayTimestamp = today.getTime()
      
      // Android uses users/{userId}/aiUsage/daily with nested usage map
      const usageRef = doc(db, 'users', userId, 'aiUsage', 'daily')
      const usageSnap = await getDoc(usageRef)
      
      const currentData = usageSnap.exists() ? usageSnap.data() : {}
      const usage = (currentData.usage as Record<string, any>) || {}
      
      const featureUsage = (usage[feature] as Record<string, any>) || {}
      const lastUsed = (featureUsage.lastUsed as number) || 0
      
      const count = lastUsed < todayTimestamp
        ? 1 // Reset for new day
        : ((featureUsage.count as number) || 0) + 1
      
      usage[feature] = {
        lastUsed: todayTimestamp,
        count: count,
        platform: 'web',
      }
      
      await setDoc(usageRef, {
        usage: usage,
      }, { merge: true })
      
      console.log(`Recorded AI usage: ${feature}, count: ${count}`)
    } catch (error) {
      console.error('Error recording AI usage:', error)
    }
  }

  /**
   * Check if user has Pro subscription
   * Cross-platform compatible - works with Android (Google Play) and Web (Stripe/PayPal) subscriptions
   */
  async isPro(userId: string): Promise<boolean> {
    const subscription = await this.getSubscription(userId)
    if (!subscription) {
      console.log('[SubscriptionService] No subscription found for user:', userId)
      return false
    }
    
    // Check tier (case-insensitive for compatibility)
    const tier = subscription.tier?.toLowerCase()
    const isProTier = tier === 'pro'
    
    // Check status - must be 'active' (case-insensitive)
    // Also check isActive field if status is missing (Android compatibility)
    const status = subscription.status?.toLowerCase()
    const isActiveStatus = status === 'active' || (status === undefined && subscription.isActive !== false)
    
    // CRITICAL: For cross-platform compatibility, also check if subscription has isActive field
    // Android sets isActive: true when tier is PRO, even if status might be missing
    const isActive = subscription.isActive !== false
    
    // User is Pro if: tier is 'pro' AND (status is 'active' OR isActive is true)
    const result = isProTier && (isActiveStatus || isActive)
    
    console.log('[SubscriptionService] isPro check:', {
      userId,
      tier,
      isProTier,
      status,
      isActiveStatus,
      isActive,
      result
    })
    
    return result
  }

  /**
   * Update subscription (called after payment processing)
   * Cross-platform compatible - preserves data from all platforms (Android, Web, iOS)
   */
  async updateSubscription(
    userId: string,
    subscriptionInfo: Partial<SubscriptionInfo>
  ): Promise<void> {
    try {
      const userRef = doc(db, 'users', userId)
      const currentData = (await getDoc(userRef)).data()

      // Preserve existing subscription data and merge new info
      const existingSubscription = currentData?.subscription || {}
      const existingPlatforms = currentData?.platforms || []
      
      // Add 'web' to platforms if not present (cross-platform tracking)
      // This ensures subscription works across Android, Web, and iOS
      const updatedPlatforms = existingPlatforms.includes('web')
        ? existingPlatforms
        : [...existingPlatforms, 'web']
      
      // Sort platforms for consistency across all platforms
      // Format: ["android", "ios", "web"] (alphabetically sorted)
      const sortedPlatforms = Array.from(new Set(updatedPlatforms)).sort()

      // Preserve subscription platforms if they exist, otherwise use user's platforms
      const subscriptionPlatforms = existingSubscription.platforms || sortedPlatforms
      const finalPlatforms = subscriptionInfo.platforms || subscriptionPlatforms

      const updatedSubscription = {
        ...existingSubscription,
        ...subscriptionInfo,
        startDate: subscriptionInfo.startDate
          ? Timestamp.fromDate(subscriptionInfo.startDate)
          : existingSubscription.startDate?.toDate
          ? existingSubscription.startDate
          : Timestamp.now(),
        endDate: subscriptionInfo.endDate
          ? Timestamp.fromDate(subscriptionInfo.endDate)
          : existingSubscription.endDate?.toDate
          ? existingSubscription.endDate
          : null,
        // Track all platforms subscription is active on
        platforms: finalPlatforms,
        // Track payment provider (stripe, paypal, google_play, app_store)
        paymentProvider: subscriptionInfo.paymentProvider || existingSubscription.paymentProvider || 'stripe',
      }

      await updateDoc(userRef, {
        subscription: updatedSubscription,
        platform: 'web', // Current platform: 'android', 'web', or 'ios'
        platforms: sortedPlatforms, // All platforms user has used
      })
    } catch (error) {
      console.error('Error updating subscription:', error)
      throw error
    }
  }

  /**
   * Cancel subscription
   */
  async cancelSubscription(userId: string): Promise<void> {
    await this.updateSubscription(userId, {
      cancelAtPeriodEnd: true,
    })
  }

  /**
   * Reactivate subscription
   */
  async reactivateSubscription(userId: string): Promise<void> {
    await this.updateSubscription(userId, {
      cancelAtPeriodEnd: false,
      status: 'active',
    })
  }
}

export default SubscriptionService
