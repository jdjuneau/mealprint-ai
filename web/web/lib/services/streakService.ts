/**
 * Streak Service (Web Version)
 * Ported from Android StreakService.kt
 * Tracks user streaks for daily logging activities
 */

import { db } from '../firebase'
import {
  collection,
  doc,
  getDoc,
  setDoc,
  updateDoc,
  query,
  where,
  orderBy,
  limit,
  getDocs,
  Timestamp,
} from 'firebase/firestore'

export interface StreakData {
  userId: string
  currentStreak: number
  longestStreak: number
  totalDays: number
  lastLogDate: string // YYYY-MM-DD
  streakStartDate: string // YYYY-MM-DD
}

export interface Badge {
  id: string
  type: string
  name: string
  description: string
  unlockedAt: Date
  isNew: boolean
}

class StreakService {
  private static instance: StreakService

  private constructor() {}

  static getInstance(): StreakService {
    if (!StreakService.instance) {
      StreakService.instance = new StreakService()
    }
    return StreakService.instance
  }

  /**
   * Get user's streak data
   * Matches Android implementation: users/{userId}/streaks/current
   */
  async getUserStreak(userId: string): Promise<StreakData | null> {
    try {
      const streakRef = doc(db, 'users', userId, 'streaks', 'current')
      const streakSnap = await getDoc(streakRef)

      if (streakSnap.exists()) {
        const data = streakSnap.data()
        return {
          userId,
          currentStreak: data.currentStreak || 0,
          longestStreak: data.longestStreak || 0,
          totalDays: data.totalLogs || data.totalDays || 0,
          lastLogDate: data.lastLogDate || '',
          streakStartDate: data.streakStartDate || '',
        }
      }

      // Initialize streak if it doesn't exist
      const initialStreak: StreakData = {
        userId,
        currentStreak: 0,
        longestStreak: 0,
        totalDays: 0,
        lastLogDate: '',
        streakStartDate: '',
      }
      await setDoc(streakRef, initialStreak)
      return initialStreak
    } catch (error) {
      console.error('Error getting user streak:', error)
      return null
    }
  }

  /**
   * Update streak after logging an activity
   */
  async updateStreakAfterLog(userId: string, date: string): Promise<void> {
    try {
      const streak = await this.getUserStreak(userId)
      if (!streak) return

      const today = new Date().toISOString().split('T')[0]
      const yesterday = new Date(Date.now() - 24 * 60 * 60 * 1000)
        .toISOString()
        .split('T')[0]

      let newStreak = { ...streak }

      // If logging for today
      if (date === today) {
        if (streak.lastLogDate === yesterday) {
          // Continuing streak from yesterday
          newStreak.currentStreak = (streak.currentStreak || 0) + 1
          newStreak.totalDays = (streak.totalDays || 0) + 1
          if (streak.currentStreak === 0) {
            newStreak.streakStartDate = yesterday
          }
        } else if (streak.lastLogDate === today) {
          // Already logged today, don't increment streak again
          // Just update lastLogDate
        } else if (!streak.lastLogDate || streak.lastLogDate < yesterday) {
          // Streak was broken or first log ever, start new one
          newStreak.currentStreak = 1
          newStreak.streakStartDate = date
          newStreak.totalDays = (streak.totalDays || 0) + 1
        }
      }

      // Update longest streak if current is longer
      if (newStreak.currentStreak > (streak.longestStreak || 0)) {
        newStreak.longestStreak = newStreak.currentStreak
      }

      newStreak.lastLogDate = date

      // Save to Firestore (matches Android: users/{userId}/streaks/current)
      // Cross-platform compatible - Android, Web, and iOS all use same structure
      const streakRef = doc(db, 'users', userId, 'streaks', 'current')
      await setDoc(streakRef, {
        ...newStreak,
        totalLogs: newStreak.totalDays, // Android uses totalLogs
        lastUpdated: Timestamp.now(),
        platform: 'web', // Current platform: 'android', 'web', or 'ios'
        // Note: Streaks don't need platforms array (only UserProfile tracks all platforms)
      }, { merge: true })

      // Check for badges
      await this.checkStreakBadges(userId, newStreak.currentStreak)
    } catch (error) {
      console.error('Error updating streak:', error)
    }
  }

  /**
   * Check and award streak badges
   */
  async checkStreakBadges(userId: string, currentStreak: number): Promise<void> {
    try {
      const badgesRef = collection(db, 'users', userId, 'badges')
      const badgeTypes = [
        { streak: 3, type: 'streak_3', name: 'Getting Started', description: '3 day streak!' },
        { streak: 7, type: 'streak_7', name: 'Week Warrior', description: '7 day streak!' },
        { streak: 14, type: 'streak_14', name: 'Two Week Champion', description: '14 day streak!' },
        { streak: 30, type: 'streak_30', name: 'Monthly Master', description: '30 day streak!' },
        { streak: 60, type: 'streak_60', name: 'Two Month Legend', description: '60 day streak!' },
        { streak: 100, type: 'streak_100', name: 'Century Club', description: '100 day streak!' },
      ]

      for (const badge of badgeTypes) {
        if (currentStreak >= badge.streak) {
          // Check if badge already exists
          const badgeQuery = query(badgesRef, where('type', '==', badge.type))
          const existingBadges = await getDocs(badgeQuery)

          if (existingBadges.empty) {
            // Award new badge
            const newBadgeRef = doc(badgesRef)
            await setDoc(newBadgeRef, {
              type: badge.type,
              name: badge.name,
              description: badge.description,
              unlockedAt: Timestamp.now(),
              isNew: true,
            })
          }
        }
      }
    } catch (error) {
      console.error('Error checking streak badges:', error)
    }
  }

  /**
   * Get user's badges
   */
  async getUserBadges(userId: string): Promise<Badge[]> {
    try {
      const badgesRef = collection(db, 'users', userId, 'badges')
      const badgesQuery = query(badgesRef, orderBy('unlockedAt', 'desc'))
      const badgesSnap = await getDocs(badgesQuery)

      return badgesSnap.docs.map((doc) => ({
        id: doc.id,
        ...doc.data(),
        unlockedAt: doc.data().unlockedAt?.toDate() || new Date(),
      })) as Badge[]
    } catch (error) {
      console.error('Error getting user badges:', error)
      return []
    }
  }

  /**
   * Mark badge as seen
   */
  async markBadgeAsSeen(userId: string, badgeId: string): Promise<void> {
    try {
      const badgeRef = doc(db, 'users', userId, 'badges', badgeId)
      await updateDoc(badgeRef, { isNew: false })
    } catch (error) {
      console.error('Error marking badge as seen:', error)
    }
  }
}

export default StreakService
