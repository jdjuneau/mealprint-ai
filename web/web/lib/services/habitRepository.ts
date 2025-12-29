/**
 * Habit Repository (Web Version)
 * Ported from Android HabitRepository.kt
 * Manages habit CRUD operations in Firestore
 */

import { db } from '../firebase'
import {
  collection,
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  deleteDoc,
  query,
  where,
  orderBy,
  onSnapshot,
  Unsubscribe,
  Timestamp,
} from 'firebase/firestore'

export interface Habit {
  id: string
  userId: string
  title: string
  description?: string
  frequency: 'daily' | 'weekly' | 'custom'
  reminderTime?: string
  isActive: boolean
  createdAt: Date
  updatedAt: Date
  streak?: number
  lastCompleted?: Date
  category?: string
  targetValue?: number
  unit?: string
  icon?: string
  color?: string
}

export interface HabitCompletion {
  id: string
  habitId: string
  userId: string
  value: number
  completedAt: Date
  notes?: string
  date: string // YYYY-MM-DD format
}

class HabitRepository {
  private static instance: HabitRepository

  private constructor() {}

  static getInstance(): HabitRepository {
    if (!HabitRepository.instance) {
      HabitRepository.instance = new HabitRepository()
    }
    return HabitRepository.instance
  }

  /**
   * Create a new habit
   */
  async createHabit(userId: string, habit: Omit<Habit, 'id' | 'userId' | 'createdAt' | 'updatedAt'>): Promise<string> {
    try {
      const habitsRef = collection(db, 'users', userId, 'habits')
      const habitRef = doc(habitsRef)

      const newHabit: Habit = {
        ...habit,
        id: habitRef.id,
        userId,
        createdAt: new Date(),
        updatedAt: new Date(),
        isActive: habit.isActive ?? true,
      }

      await setDoc(habitRef, {
        ...newHabit,
        createdAt: Timestamp.fromDate(newHabit.createdAt),
        updatedAt: Timestamp.fromDate(newHabit.updatedAt),
        lastCompleted: newHabit.lastCompleted
          ? Timestamp.fromDate(newHabit.lastCompleted)
          : null,
        platform: 'web', // Current platform: 'android', 'web', or 'ios'
        // Cross-platform compatible - all platforms use same Firestore structure
      })

      return habitRef.id
    } catch (error) {
      console.error('Error creating habit:', error)
      throw error
    }
  }

  /**
   * Update an existing habit
   */
  async updateHabit(userId: string, habit: Habit): Promise<void> {
    try {
      const habitRef = doc(db, 'users', userId, 'habits', habit.id)

      await updateDoc(habitRef, {
        ...habit,
        updatedAt: Timestamp.fromDate(new Date()),
        lastCompleted: habit.lastCompleted ? Timestamp.fromDate(habit.lastCompleted) : null,
        platform: 'web', // Track platform
      })
    } catch (error) {
      console.error('Error updating habit:', error)
      throw error
    }
  }

  /**
   * Delete a habit
   */
  async deleteHabit(userId: string, habitId: string): Promise<void> {
    try {
      const habitRef = doc(db, 'users', userId, 'habits', habitId)
      await deleteDoc(habitRef)
    } catch (error) {
      console.error('Error deleting habit:', error)
      throw error
    }
  }

  /**
   * Get all habits for a user
   */
  async getHabits(userId: string): Promise<Habit[]> {
    try {
      const habitsRef = collection(db, 'users', userId, 'habits')
      const habitsQuery = query(habitsRef, orderBy('createdAt', 'desc'))
      const habitsSnap = await getDocs(habitsQuery)

      return habitsSnap.docs.map((doc) => {
        const data = doc.data()
        return {
          id: doc.id,
          ...data,
          createdAt: data.createdAt?.toDate() || new Date(),
          updatedAt: data.updatedAt?.toDate() || new Date(),
          lastCompleted: data.lastCompleted?.toDate(),
        } as Habit
      })
    } catch (error) {
      console.error('Error getting habits:', error)
      return []
    }
  }

  /**
   * Subscribe to habits changes (real-time)
   */
  subscribeToHabits(
    userId: string,
    callback: (habits: Habit[]) => void
  ): Unsubscribe {
    const habitsRef = collection(db, 'users', userId, 'habits')
    const habitsQuery = query(habitsRef, orderBy('createdAt', 'desc'))

    return onSnapshot(
      habitsQuery,
      (snapshot) => {
        const habits = snapshot.docs.map((doc) => {
          const data = doc.data()
          return {
            id: doc.id,
            ...data,
            createdAt: data.createdAt?.toDate() || new Date(),
            updatedAt: data.updatedAt?.toDate() || new Date(),
            lastCompleted: data.lastCompleted?.toDate(),
          } as Habit
        })
        callback(habits)
      },
      (error) => {
        console.error('Error subscribing to habits:', error)
        callback([])
      }
    )
  }

  /**
   * Mark habit as completed
   */
  async completeHabit(userId: string, habitId: string, value: number = 1, notes?: string): Promise<string> {
    try {
      const habitRef = doc(db, 'users', userId, 'habits', habitId)
      const habitSnap = await getDoc(habitRef)

      if (!habitSnap.exists()) {
        throw new Error('Habit not found')
      }

      const habitData = habitSnap.data()
      const habitTitle = habitData.title || 'Habit'
      
      // Create completion record (matches Android structure: users/{userId}/completions)
      const completionsRef = collection(db, 'users', userId, 'completions')
      const completionRef = doc(completionsRef)
      
      const completion: HabitCompletion = {
        id: completionRef.id,
        habitId,
        userId,
        value,
        completedAt: new Date(),
        notes,
        date: new Date().toISOString().split('T')[0]
      }

      await setDoc(completionRef, {
        habitId,
        userId,
        habitTitle: habitTitle,
        value,
        completedAt: Timestamp.fromDate(completion.completedAt),
        notes: notes || null,
        createdAt: Timestamp.fromDate(new Date()),
        platform: 'web',
      })

      // Update habit streak and lastCompleted
      const currentStreak = habitData.streak || 0
      const lastCompleted = habitData.lastCompleted?.toDate()
      const todayDate = new Date()
      todayDate.setHours(0, 0, 0, 0)

        let newStreak = currentStreak
      if (!lastCompleted || lastCompleted < todayDate) {
          // Check if last completion was yesterday (continuing streak)
        const yesterday = new Date(todayDate)
          yesterday.setDate(yesterday.getDate() - 1)

          if (lastCompleted && lastCompleted >= yesterday) {
            newStreak = currentStreak + 1
          } else {
            newStreak = 1 // Start new streak
          }
        }

        await updateDoc(habitRef, {
          lastCompleted: Timestamp.fromDate(new Date()),
          streak: newStreak,
          updatedAt: Timestamp.fromDate(new Date()),
          platform: 'web',
        })

      return completionRef.id
    } catch (error) {
      console.error('Error completing habit:', error)
      throw error
    }
  }

  /**
   * Get habit completions for a specific date (matches Android structure: users/{userId}/completions)
   */
  async getHabitCompletions(userId: string, date: string): Promise<HabitCompletion[]> {
    try {
      const completionsRef = collection(db, 'users', userId, 'completions')
      const startOfDay = new Date(date)
      startOfDay.setHours(0, 0, 0, 0)
      const endOfDay = new Date(date)
      endOfDay.setHours(23, 59, 59, 999)

      const completionsQuery = query(
        completionsRef,
        where('completedAt', '>=', Timestamp.fromDate(startOfDay)),
        where('completedAt', '<=', Timestamp.fromDate(endOfDay))
      )
      
      const completionsSnap = await getDocs(completionsQuery)

      return completionsSnap.docs.map((doc) => {
        const data = doc.data()
        return {
          id: doc.id,
          habitId: data.habitId || '',
          userId,
          value: data.value || 1,
          completedAt: data.completedAt?.toDate() || new Date(),
          notes: data.notes,
          date: date
        }
      })
    } catch (error) {
      console.error('Error getting habit completions:', error)
      return []
    }
  }
}

export default HabitRepository
