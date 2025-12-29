/**
 * Cross-Platform Data Service
 * Ensures data compatibility and synchronization across Android, Web, and iOS
 */

import { db } from '../firebase'
import { doc, getDoc, updateDoc, Timestamp } from 'firebase/firestore'
import type { Platform } from '../utils/crossPlatformUtils'
import { addPlatformToArray } from '../utils/crossPlatformUtils'

class CrossPlatformDataService {
  private static instance: CrossPlatformDataService

  private constructor() {}

  static getInstance(): CrossPlatformDataService {
    if (!CrossPlatformDataService.instance) {
      CrossPlatformDataService.instance = new CrossPlatformDataService()
    }
    return CrossPlatformDataService.instance
  }

  /**
   * Ensure UserProfile is cross-platform compatible
   * Called when saving profile to add current platform to platforms array
   */
  async ensureUserProfileCompatibility(
    userId: string,
    currentPlatform: Platform
  ): Promise<void> {
    try {
      const userRef = doc(db, 'users', userId)
      const userSnap = await getDoc(userRef)

      if (userSnap.exists()) {
        const data = userSnap.data()
        const existingPlatforms = data.platforms || []
        const updatedPlatforms = addPlatformToArray(existingPlatforms, currentPlatform)

        // Only update if platforms array changed
        if (updatedPlatforms.length !== existingPlatforms.length) {
          await updateDoc(userRef, {
            platform: currentPlatform,
            platforms: updatedPlatforms,
            updatedAt: Timestamp.now(),
          })
        }
      }
    } catch (error) {
      console.error('Error ensuring user profile compatibility:', error)
    }
  }

  /**
   * Get user's subscription status across all platforms
   */
  async getCrossPlatformSubscription(userId: string): Promise<{
    active: boolean
    platforms: Platform[]
    provider?: string
  }> {
    try {
      const userRef = doc(db, 'users', userId)
      const userSnap = await getDoc(userRef)

      if (userSnap.exists()) {
        const data = userSnap.data()
        const subscription = data.subscription

        return {
          active: subscription?.status === 'active',
          platforms: (subscription?.platforms || data.platforms || []) as Platform[],
          provider: subscription?.paymentProvider,
        }
      }

      return { active: false, platforms: [] }
    } catch (error) {
      console.error('Error getting cross-platform subscription:', error)
      return { active: false, platforms: [] }
    }
  }

  /**
   * Verify subscription works across all platforms
   */
  async verifyCrossPlatformSubscription(userId: string): Promise<boolean> {
    try {
      const subscription = await this.getCrossPlatformSubscription(userId)
      return subscription.active
    } catch (error) {
      console.error('Error verifying cross-platform subscription:', error)
      return false
    }
  }
}

export default CrossPlatformDataService
