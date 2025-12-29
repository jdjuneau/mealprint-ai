/**
 * Firebase Service
 * Handles Firebase Firestore operations for user data
 */

import { db, storage } from '../firebase'
import { doc, getDoc, setDoc, collection, query, where, getDocs, orderBy, limit, serverTimestamp, addDoc, updateDoc, Timestamp } from 'firebase/firestore'
import { ref, uploadBytes, getDownloadURL } from 'firebase/storage'
import type { UserProfile, DailyLog, HealthLog, ChatMessage, Supplement } from '../../types'

class FirebaseService {
  /**
   * Save user profile to Firestore
   */
  static async saveUserProfile(profile: UserProfile): Promise<boolean> {
    try {
      const userRef = doc(db, 'users', profile.id)

      // Build profile data - include all fields, even if they're 0 or empty strings
      // Only exclude undefined and null values
      const profileData: any = {
        id: profile.id,
        name: profile.name || '',
        currentWeight: profile.currentWeight ?? 0,
        goalWeight: profile.goalWeight ?? 0,
        heightCm: profile.heightCm ?? 0,
        age: profile.age ?? 0,
        gender: profile.gender || 'male',
        activityLevel: profile.activityLevel || 'moderately active',
        createdAt: profile.createdAt || serverTimestamp(),
        updatedAt: serverTimestamp(),
      }

      // Add optional fields only if they're defined
      if (profile.dietaryPreference !== undefined) profileData.dietaryPreference = profile.dietaryPreference
      if (profile.goalTrend !== undefined) profileData.goalTrend = profile.goalTrend
      if (profile.estimatedDailyCalories !== undefined) profileData.estimatedDailyCalories = profile.estimatedDailyCalories
      if (profile.platform !== undefined) profileData.platform = profile.platform
      if (profile.platforms !== undefined) profileData.platforms = profile.platforms
      if (profile.username !== undefined) profileData.username = profile.username
      if (profile.startDate !== undefined) profileData.startDate = profile.startDate
      if (profile.nudgesEnabled !== undefined) profileData.nudgesEnabled = profile.nudgesEnabled
      if (profile.fcmToken !== undefined) profileData.fcmToken = profile.fcmToken
      if (profile.fcmTokens !== undefined) profileData.fcmTokens = profile.fcmTokens
      if (profile.subscription !== undefined) profileData.subscription = profile.subscription
      if (profile.ftueCompleted !== undefined) profileData.ftueCompleted = profile.ftueCompleted
      if (profile.preferredCookingMethods !== undefined) profileData.preferredCookingMethods = profile.preferredCookingMethods
      if (profile.mealsPerDay !== undefined) profileData.mealsPerDay = profile.mealsPerDay
      if (profile.snacksPerDay !== undefined) profileData.snacksPerDay = profile.snacksPerDay
      if (profile.notifications !== undefined) profileData.notifications = profile.notifications
      if (profile.mealTimes !== undefined) profileData.mealTimes = profile.mealTimes
      if (profile.menstrualCycleEnabled !== undefined) profileData.menstrualCycleEnabled = profile.menstrualCycleEnabled
      if (profile.averageCycleLength !== undefined) profileData.averageCycleLength = profile.averageCycleLength
      if (profile.averagePeriodLength !== undefined) profileData.averagePeriodLength = profile.averagePeriodLength
      if (profile.lastPeriodStart !== undefined) profileData.lastPeriodStart = profile.lastPeriodStart

      // If useImperial is being updated, also save to goals (matching Android structure)
      if (profile.useImperial !== undefined) {
        profileData.useImperial = profile.useImperial
        await this.saveUserGoals(profile.id, { useImperial: profile.useImperial })
      }

      await setDoc(userRef, profileData, { merge: true })
      console.log('✅ Saved user profile:', { id: profile.id, age: profile.age, heightCm: profile.heightCm, currentWeight: profile.currentWeight, gender: profile.gender })
      return true
    } catch (error) {
      console.error('Error saving user profile:', error)
      return false
    }
  }

  /**
   * Get user goals (including useImperial preference) - matches Android structure
   */
  static async getUserGoals(userId: string): Promise<{ useImperial?: boolean } | null> {
    try {
      const userRef = doc(db, 'users', userId)
      const userDoc = await getDoc(userRef)
      
      if (!userDoc.exists()) {
        return null
      }

      const data = userDoc.data()
      // Filter to only goals-related fields (matching Android)
      return {
        useImperial: data.useImperial !== false, // Default to true (imperial)
      }
    } catch (error) {
      console.error('Error getting user goals:', error)
      return null
    }
  }

  /**
   * Save user goals (including useImperial preference) - matches Android structure
   */
  static async saveUserGoals(userId: string, goals: { useImperial?: boolean }): Promise<void> {
    try {
      const userRef = doc(db, 'users', userId)
      await updateDoc(userRef, {
        ...goals,
        goalsSet: true,
        goalsSetDate: Date.now(),
      })
    } catch (error) {
      console.error('Error saving user goals:', error)
      throw error
    }
  }

  /**
   * Get user profile from Firestore
   */
  static async getUserProfile(userId: string): Promise<UserProfile | null> {
    try {
      const userRef = doc(db, 'users', userId)
      const userDoc = await getDoc(userRef)
      
      if (!userDoc.exists()) {
        return null
      }

      const data = userDoc.data()
      // Get useImperial from user document (stored in goals, matching Android)
      const useImperial = data.useImperial !== false // Default to true (imperial)
      
      return {
        id: userDoc.id,
        name: data.name || '',
        currentWeight: data.currentWeight || 0,
        goalWeight: data.goalWeight || 0,
        heightCm: data.heightCm || 0,
        age: data.age || 0,
        gender: data.gender || 'male',
        activityLevel: data.activityLevel || 'moderately active',
        createdAt: data.createdAt?.toDate() || new Date(),
        updatedAt: data.updatedAt?.toDate() || new Date(),
        estimatedDailyCalories: data.estimatedDailyCalories,
        dietaryPreference: data.dietaryPreference,
        goalTrend: data.goalTrend,
        platform: data.platform,
        platforms: data.platforms,
        username: data.username,
        startDate: data.startDate,
        nudgesEnabled: data.nudgesEnabled,
        fcmToken: data.fcmToken,
        fcmTokens: data.fcmTokens,
        subscription: data.subscription,
        ftueCompleted: data.ftueCompleted,
        preferredCookingMethods: data.preferredCookingMethods,
        mealsPerDay: data.mealsPerDay,
        snacksPerDay: data.snacksPerDay,
        notifications: data.notifications,
        mealTimes: data.mealTimes,
        menstrualCycleEnabled: data.menstrualCycleEnabled,
        averageCycleLength: data.averageCycleLength,
        averagePeriodLength: data.averagePeriodLength,
        lastPeriodStart: data.lastPeriodStart,
        useImperial: useImperial, // Read from user document (goals)
      } as UserProfile
    } catch (error) {
      console.error('Error getting user profile:', error)
      return null
    }
  }

  /**
   * Get daily log for a specific date
   * CRITICAL: Reads from users/{userId}/daily/{date} (primary) or logs/{userId}/daily/{date} (fallback for Android data)
   */
  static async getDailyLog(userId: string, date: string): Promise<DailyLog | null> {
    try {
      // Try new path first (users/{userId}/daily/{date}) - matches current structure
      let dailyDoc = await getDoc(doc(db, 'users', userId, 'daily', date))
      let logsRef = collection(db, 'users', userId, 'daily', date, 'entries')
      
      // If not found, try old path (logs/{userId}/daily/{date}) for backward compatibility with Android
      if (!dailyDoc.exists()) {
        dailyDoc = await getDoc(doc(db, 'logs', userId, 'daily', date))
        logsRef = collection(db, 'logs', userId, 'daily', date, 'entries')
      }
      
      if (!dailyDoc.exists()) {
        return null
      }

      const data = dailyDoc.data()
      
      // Get logs for this day - try timestamp first (web format), fallback to createdAt (Android format) or no ordering
      let logsSnapshot
      try {
        // Try with timestamp ordering first (web format)
        const logsQuery = query(logsRef, orderBy('timestamp', 'desc'))
        logsSnapshot = await getDocs(logsQuery)
      } catch (error: any) {
        // If timestamp ordering fails (no index or field doesn't exist), try createdAt (Android format)
        if (error.code === 'failed-precondition' || error.message?.includes('timestamp') || error.message?.includes('index')) {
          try {
            const logsQuery = query(logsRef, orderBy('createdAt', 'desc'))
            logsSnapshot = await getDocs(logsQuery)
          } catch (error2: any) {
            // If that also fails, get all entries without ordering and sort manually
            console.warn(`Could not order entries by timestamp or createdAt for ${date}, loading without order:`, error2)
            try {
              logsSnapshot = await getDocs(logsRef)
            } catch (error3: any) {
              console.error(`Failed to load entries for ${date}:`, error3)
              logsSnapshot = { docs: [], empty: true, size: 0 } as any
            }
          }
        } else {
          // Other error, try without ordering
          console.warn(`Error querying entries for ${date}, trying without order:`, error)
          try {
            logsSnapshot = await getDocs(logsRef)
          } catch (error3: any) {
            console.error(`Failed to load entries for ${date}:`, error3)
            logsSnapshot = { docs: [], empty: true, size: 0 } as any
          }
        }
      }
      
      // Log how many entries we found for debugging
      if (logsSnapshot && logsSnapshot.docs) {
        console.log(`Loaded ${logsSnapshot.docs.length} entries for ${date}`)
      }
      
      const logs: HealthLog[] = []
      logsSnapshot.forEach((doc: any) => {
        const logData = doc.data()
        // Handle both timestamp (web) and createdAt (Android) fields, and both Firestore Timestamp and Long formats
        // Android saves timestamp as Long (milliseconds), which Firestore stores as a number
        let timestamp: Date
        if (logData.timestamp !== undefined && logData.timestamp !== null) {
          if (logData.timestamp.toDate && typeof logData.timestamp.toDate === 'function') {
            // Firestore Timestamp object
            timestamp = logData.timestamp.toDate()
          } else if (typeof logData.timestamp === 'number') {
            // Long timestamp in milliseconds (Android format)
            timestamp = new Date(logData.timestamp)
          } else if (logData.timestamp.toMillis && typeof logData.timestamp.toMillis === 'function') {
            // Firestore Timestamp with toMillis method
            timestamp = new Date(logData.timestamp.toMillis())
          } else {
            // Try to convert to Date
            timestamp = new Date(logData.timestamp)
          }
        } else if (logData.createdAt !== undefined && logData.createdAt !== null) {
          if (logData.createdAt.toDate && typeof logData.createdAt.toDate === 'function') {
            timestamp = logData.createdAt.toDate()
          } else if (typeof logData.createdAt === 'number') {
            timestamp = new Date(logData.createdAt)
          } else if (logData.createdAt.toMillis && typeof logData.createdAt.toMillis === 'function') {
            timestamp = new Date(logData.createdAt.toMillis())
          } else {
            timestamp = new Date(logData.createdAt)
          }
        } else {
          // Fallback: use current time if no timestamp found
          timestamp = new Date()
        }
        
        logs.push({
          id: doc.id,
          userId,
          type: logData.type,
          timestamp,
          ...logData,
        } as HealthLog)
      })
      
      // Sort logs by timestamp descending if we loaded without ordering
      logs.sort((a, b) => b.timestamp.getTime() - a.timestamp.getTime())

      return {
        id: dailyDoc.id,
        userId,
        date,
        steps: data.steps,
        workouts: data.workouts,
        sleepHours: data.sleepHours,
        // CRITICAL: Android uses 'water', Web uses 'waterAmount' - support both for cross-platform sync
        waterAmount: data.waterAmount || data.waterMl || data.water || 0,
        caloriesConsumed: data.caloriesConsumed,
        caloriesBurned: data.caloriesBurned,
        weight: data.weight,
        micronutrientExtras: data.micronutrientExtras,
        logs,
        createdAt: data.createdAt?.toDate() || new Date(),
        updatedAt: data.updatedAt?.toDate() || new Date(),
      } as DailyLog
    } catch (error) {
      console.error('Error getting daily log:', error)
      return null
    }
  }

  /**
   * Get recent daily logs (for analytics, insights, etc.)
   */
  static async getRecentDailyLogs(userId: string, limitCount: number = 7): Promise<DailyLog[]> {
    try {
      const logs: DailyLog[] = []
      const today = new Date()
      
      // Get logs for the last N days
      for (let i = 0; i < limitCount; i++) {
        const date = new Date(today)
        date.setDate(date.getDate() - i)
        const dateStr = date.toLocaleDateString('en-CA') // YYYY-MM-DD in local timezone
        
        const log = await this.getDailyLog(userId, dateStr)
        if (log) {
          logs.push(log)
        }
      }
      
      return logs.sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime())
    } catch (error) {
      console.error('Error getting recent daily logs:', error)
      return []
    }
  }

  /**
   * Save health log to Firestore
   */
  static async saveHealthLog(userId: string, date: string, log: HealthLog): Promise<void> {
    try {
      // Ensure daily log document exists
      const dailyRef = doc(db, 'users', userId, 'daily', date)
      const dailyDoc = await getDoc(dailyRef)
      
      if (!dailyDoc.exists()) {
        await setDoc(dailyRef, {
          uid: userId,
          date: date,
          platform: 'web', // Track platform for analytics
          createdAt: serverTimestamp(),
          updatedAt: serverTimestamp(),
        })
      }

      // Add log to entries subcollection
      const entriesRef = collection(db, 'users', userId, 'daily', date, 'entries')
      const logData: any = {
        ...log,
        timestamp: log.timestamp instanceof Date ? log.timestamp : serverTimestamp(),
        platform: 'web',
      }
      
      await addDoc(entriesRef, logData)

      // Auto-complete related habits (like Android)
      try {
        const HabitAutoCompletionService = (await import('./habitAutoCompletionService')).default
        const habitAutoService = HabitAutoCompletionService.getInstance()
        
        if (log.type === 'meal') {
          await habitAutoService.onMealLogged(userId)
        } else if (log.type === 'workout') {
          const workoutLog = log as any
          await habitAutoService.onWorkoutLogged(userId, workoutLog.durationMin, workoutLog.caloriesBurned)
        } else if (log.type === 'water') {
          const waterLog = log as any
          const waterAmount = waterLog.amount || 0
          await habitAutoService.onWaterLogged(userId, waterAmount)
        } else if (log.type === 'sleep') {
          const sleepLog = log as any
          const sleepHours = sleepLog.hours || 0
          await habitAutoService.onSleepLogged(userId, sleepHours)
        }
      } catch (autoCompleteError) {
        console.warn('Error auto-completing habits (non-critical):', autoCompleteError)
        // Don't fail the health log save if autotracking fails
      }

      // Update daily log summary
      const updates: any = {
        updatedAt: serverTimestamp(),
      }

      if (log.type === 'meal') {
        const mealLog = log as any
        updates.caloriesConsumed = (dailyDoc.data()?.caloriesConsumed || 0) + (mealLog.calories || 0)
      } else if (log.type === 'workout') {
        const workoutLog = log as any
        updates.caloriesBurned = (dailyDoc.data()?.caloriesBurned || 0) + (workoutLog.calories || 0)
        updates.workouts = (dailyDoc.data()?.workouts || 0) + 1
      } else if (log.type === 'water') {
        const waterLog = log as any
        // CRITICAL: Save as both 'water' (Android) and 'waterAmount' (Web) for cross-platform sync
        const currentWater = dailyDoc.data()?.waterAmount || dailyDoc.data()?.water || dailyDoc.data()?.waterMl || 0
        const newWaterAmount = currentWater + (waterLog.amount || 0)
        updates.waterAmount = newWaterAmount
        updates.water = newWaterAmount // Also save as 'water' for Android compatibility
      } else if (log.type === 'sleep') {
        const sleepLog = log as any
        updates.sleepHours = sleepLog.hours || 0
      } else if (log.type === 'weight') {
        const weightLog = log as any
        updates.weight = weightLog.weight || 0
      }

      await updateDoc(dailyRef, updates)

      // Update streak after logging (for streak tracking)
      try {
        const StreakService = (await import('./streakService')).default
        const streakService = StreakService.getInstance()
        await streakService.updateStreakAfterLog(userId, date)
      } catch (streakError) {
        // Don't fail the log save if streak update fails
        console.error('Error updating streak:', streakError)
      }
    } catch (error) {
      console.error('Error saving health log:', error)
      throw error
    }
  }

  /**
   * Share meal with friends
   * Saves meal to sharedMeals collection (matching Android format)
   */
  static async shareMealWithFriends(userId: string, mealLog: HealthLog, friendIds: string[]): Promise<void> {
    try {
      if (!mealLog || friendIds.length === 0) {
        throw new Error('Meal and friend IDs are required')
      }

      const mealData: any = {
        id: mealLog.id,
        userId: userId,
        foodName: (mealLog as any).foodName || '',
        calories: (mealLog as any).calories || 0,
        protein: (mealLog as any).protein || 0,
        carbs: (mealLog as any).carbs || 0,
        fat: (mealLog as any).fat || 0,
        sugar: (mealLog as any).sugar || 0,
        addedSugar: (mealLog as any).addedSugar || 0,
        micronutrients: (mealLog as any).micronutrients || {},
        photoUrl: (mealLog as any).photoUrl || '',
        recipeId: (mealLog as any).recipeId || '',
        servingsConsumed: (mealLog as any).servingsConsumed || 1.0,
        isShared: true,
        sharedWith: friendIds,
        timestamp: mealLog.timestamp?.getTime() || Date.now(),
        createdAt: Date.now(),
        platform: 'web',
      }

      const sharedMealsRef = collection(db, 'sharedMeals')
      await setDoc(doc(sharedMealsRef, mealLog.id), mealData)
    } catch (error) {
      console.error('Error sharing meal with friends:', error)
      throw error
    }
  }

  /**
   * Upload image to Firebase Storage
   */
  static async uploadImage(userId: string, file: File, category: string): Promise<string> {
    try {
      const timestamp = Date.now()
      const fileName = `${category}/${userId}/${timestamp}_${file.name}`
      const storageRef = ref(storage, fileName)
      
      await uploadBytes(storageRef, file)
      const downloadURL = await getDownloadURL(storageRef)
      
      return downloadURL
    } catch (error) {
      console.error('Error uploading image:', error)
      throw error
    }
  }

  /**
   * Get chat messages for a user
   */
  static async getChatMessages(userId: string, limitCount: number = 20): Promise<ChatMessage[]> {
    try {
      const chatRef = collection(db, 'users', userId, 'chat')
      const chatQuery = query(chatRef, orderBy('timestamp', 'desc'), limit(limitCount))
      const chatSnap = await getDocs(chatQuery)

      return chatSnap.docs.map((doc) => ({
        id: doc.id,
        userId,
        ...doc.data(),
        timestamp: doc.data().timestamp?.toDate() || new Date(),
      })) as ChatMessage[]
    } catch (error) {
      console.error('Error getting chat messages:', error)
      return []
    }
  }

  /**
   * Save a chat message
   */
  static async saveChatMessage(userId: string, message: ChatMessage): Promise<void> {
    try {
      const chatRef = collection(db, 'users', userId, 'chat')
      const messageData = {
        ...message,
        timestamp: serverTimestamp(),
      }
      delete (messageData as any).id // Remove id for Firestore auto-generation

      await addDoc(chatRef, messageData)
    } catch (error) {
      console.error('Error saving chat message:', error)
      throw error
    }
  }

  /**
   * Get list of available supplements
   */
  static async getSupplements(): Promise<Supplement[]> {
    // Return a static list of common supplements
    const supplementNames = [
      'Multivitamin',
      'Vitamin C',
      'Vitamin D',
      'Calcium',
      'Iron',
      'Omega-3',
      'Probiotics',
      'Magnesium',
      'Zinc',
      'B-Complex',
      'Protein Powder',
      'Creatine',
      'Collagen',
      'Melatonin',
      'Fish Oil',
      'CoQ10',
      'Ashwagandha',
      'Turmeric',
      'Green Tea Extract',
      'Other'
    ]

    return supplementNames.map((name, index) => ({
      id: `supp_${index}`,
      name,
      nutrients: {},
      createdAt: new Date()
    }))
  }

  /**
   * Get weekly blueprint for a user
   */
  static async getWeeklyBlueprint(userId: string, weekStarting: string): Promise<any> {
    try {
      const blueprintRef = doc(db, 'users', userId, 'weeklyBlueprints', weekStarting)
      const blueprintDoc = await getDoc(blueprintRef)

      if (blueprintDoc.exists()) {
        return blueprintDoc.data()
      }
      return null
    } catch (error) {
      console.error('Error getting weekly blueprint:', error)
      return null
    }
  }

  /**
   * Update weekly blueprint for a user
   */
  static async updateWeeklyBlueprint(userId: string, weekStarting: string, updates: any): Promise<void> {
    try {
      const blueprintRef = doc(db, 'users', userId, 'weeklyBlueprints', weekStarting)
      await updateDoc(blueprintRef, updates)
    } catch (error) {
      console.error('Error updating weekly blueprint:', error)
      throw error
    }
  }

  /**
   * Get brief for a user by type and date
   * @param userId - User ID
   * @param briefType - 'morning' | 'afternoon' | 'evening'
   * @param date - Date string in YYYY-MM-DD format (defaults to today)
   */
  static async getBrief(userId: string, briefType: 'morning' | 'afternoon' | 'evening', date?: string): Promise<string | null> {
    try {
      const briefDate = date || new Date().toLocaleDateString('en-CA')
      const briefRef = doc(db, 'users', userId, 'briefs', `${briefType}_${briefDate}`)
      const briefDoc = await getDoc(briefRef)

      if (briefDoc.exists()) {
        const data = briefDoc.data()
        return data.brief || null
      }
      return null
    } catch (error) {
      console.error('Error getting brief:', error)
      return null
    }
  }

  /**
   * Get today's briefs for a user (all types)
   * Returns an object with morning, afternoon, and evening briefs
   */
  static async getTodayBriefs(userId: string): Promise<{
    morning: string | null
    afternoon: string | null
    evening: string | null
  }> {
    try {
      const today = new Date().toLocaleDateString('en-CA')
      const [morning, afternoon, evening] = await Promise.all([
        this.getBrief(userId, 'morning', today),
        this.getBrief(userId, 'afternoon', today),
        this.getBrief(userId, 'evening', today)
      ])

      return { morning, afternoon, evening }
    } catch (error) {
      console.error('Error getting today\'s briefs:', error)
      return { morning: null, afternoon: null, evening: null }
    }
  }

  /**
   * Get the most recent brief for a user based on current time
   * Matches Android's getMostRecentBrief logic
   * - Before 9 AM: show yesterday's evening brief (or today's if it exists)
   * - 9 AM - 2 PM: show today's morning brief
   * - 2 PM - 6 PM: show today's afternoon brief
   * - After 6 PM: show today's evening brief
   */
  static async getMostRecentBrief(userId: string): Promise<string | null> {
    try {
      const now = new Date()
      const hour = now.getHours()
      const minute = now.getMinutes()
      const timeInMinutes = hour * 60 + minute

      const today = new Date().toLocaleDateString('en-CA')
      const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000).toLocaleDateString('en-CA')

      let briefType: 'morning' | 'afternoon' | 'evening'
      let date: string

      if (timeInMinutes < 540) {
        // Before 9 AM - try yesterday's evening brief first
        briefType = 'evening'
        date = yesterday
      } else if (timeInMinutes < 840) {
        // 9 AM - 2 PM - show today's morning brief
        briefType = 'morning'
        date = today
      } else if (timeInMinutes < 1080) {
        // 2 PM - 6 PM - show today's afternoon brief
        briefType = 'afternoon'
        date = today
      } else {
        // After 6 PM - show today's evening brief
        briefType = 'evening'
        date = today
      }

      // Try to get the determined brief
      let brief = await this.getBrief(userId, briefType, date)

      // If before 9 AM and yesterday's evening brief doesn't exist, try today's evening
      if (!brief && timeInMinutes < 540 && briefType === 'evening' && date === yesterday) {
        brief = await this.getBrief(userId, 'evening', today)
      }

      return brief
    } catch (error) {
      console.error('Error getting most recent brief:', error)
      return null
    }
  }
}

export { FirebaseService }
