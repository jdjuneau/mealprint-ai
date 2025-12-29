/**
 * Saved Meals Service (Web Version)
 * Manages saved meals for quick logging
 */

import { db } from '../firebase'
import {
  collection,
  doc,
  getDoc,
  getDocs,
  setDoc,
  deleteDoc,
  query,
  where,
  orderBy,
  limit,
  Timestamp,
} from 'firebase/firestore'
import type { SavedMeal } from '../../types'

class SavedMealsService {
  private static instance: SavedMealsService

  private constructor() {}

  static getInstance(): SavedMealsService {
    if (!SavedMealsService.instance) {
      SavedMealsService.instance = new SavedMealsService()
    }
    return SavedMealsService.instance
  }

  /**
   * Get all saved meals for a user
   */
  async getSavedMeals(userId: string): Promise<SavedMeal[]> {
    try {
      const mealsRef = collection(db, 'users', userId, 'savedMeals')
      const mealsQuery = query(mealsRef, orderBy('lastUsedAt', 'desc'))
      const mealsSnap = await getDocs(mealsQuery)

      return mealsSnap.docs.map((doc) => {
        const data = doc.data()
        return {
          id: doc.id,
          userId,
          ...data,
          createdAt: data.createdAt?.toDate() || new Date(),
          lastUsedAt: data.lastUsedAt?.toDate() || new Date(),
        } as SavedMeal
      })
    } catch (error) {
      console.error('Error getting saved meals:', error)
      return []
    }
  }

  /**
   * Save a meal for quick access
   */
  async saveMeal(userId: string, meal: Omit<SavedMeal, 'id' | 'userId' | 'createdAt' | 'lastUsedAt' | 'useCount'>): Promise<string> {
    try {
      const mealsRef = collection(db, 'users', userId, 'savedMeals')
      const mealRef = doc(mealsRef)

      await setDoc(mealRef, {
        ...meal,
        userId,
        createdAt: Timestamp.now(),
        lastUsedAt: Timestamp.now(),
        useCount: 1,
        platform: 'web',
      })

      return mealRef.id
    } catch (error) {
      console.error('Error saving meal:', error)
      throw error
    }
  }

  /**
   * Update meal usage (when used for logging)
   */
  async updateMealUsage(userId: string, mealId: string): Promise<void> {
    try {
      const mealRef = doc(db, 'users', userId, 'savedMeals', mealId)
      const mealSnap = await getDoc(mealRef)

      if (mealSnap.exists()) {
        const data = mealSnap.data()
        await setDoc(mealRef, {
          lastUsedAt: Timestamp.now(),
          useCount: (data.useCount || 0) + 1,
          platform: 'web',
        }, { merge: true })
      }
    } catch (error) {
      console.error('Error updating meal usage:', error)
    }
  }

  /**
   * Delete a saved meal
   */
  async deleteMeal(userId: string, mealId: string): Promise<void> {
    try {
      const mealRef = doc(db, 'users', userId, 'savedMeals', mealId)
      await deleteDoc(mealRef)
    } catch (error) {
      console.error('Error deleting meal:', error)
      throw error
    }
  }
}

export default SavedMealsService
