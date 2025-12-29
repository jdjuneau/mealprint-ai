/**
 * Recipes Service (Web Version)
 * Manages user recipes
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
  Timestamp,
} from 'firebase/firestore'

export interface Recipe {
  id: string
  userId: string
  name: string
  servings: number
  calories: number
  ingredients: string[]
  instructions: string[]
  imageUrl?: string
  createdAt: Date
  updatedAt: Date
}

class RecipesService {
  private static instance: RecipesService

  private constructor() {}

  static getInstance(): RecipesService {
    if (!RecipesService.instance) {
      RecipesService.instance = new RecipesService()
    }
    return RecipesService.instance
  }

  /**
   * Get all recipes for a user
   */
  async getRecipes(userId: string): Promise<Recipe[]> {
    try {
      const recipesRef = collection(db, 'users', userId, 'recipes')
      const recipesQuery = query(recipesRef, orderBy('createdAt', 'desc'))
      const recipesSnap = await getDocs(recipesQuery)

      return recipesSnap.docs.map((doc) => {
        const data = doc.data()
        return {
          id: doc.id,
          userId,
          ...data,
          createdAt: data.createdAt?.toDate() || new Date(),
          updatedAt: data.updatedAt?.toDate() || new Date(),
        } as Recipe
      })
    } catch (error) {
      console.error('Error getting recipes:', error)
      return []
    }
  }

  /**
   * Save a recipe
   */
  async saveRecipe(userId: string, recipe: Omit<Recipe, 'id' | 'userId' | 'createdAt' | 'updatedAt'>): Promise<string> {
    try {
      const recipesRef = collection(db, 'users', userId, 'recipes')
      const recipeRef = doc(recipesRef)

      await setDoc(recipeRef, {
        ...recipe,
        userId,
        createdAt: Timestamp.now(),
        updatedAt: Timestamp.now(),
        platform: 'web',
      })

      return recipeRef.id
    } catch (error) {
      console.error('Error saving recipe:', error)
      throw error
    }
  }

  /**
   * Get a specific recipe
   */
  async getRecipe(userId: string, recipeId: string): Promise<Recipe | null> {
    try {
      const recipeRef = doc(db, 'users', userId, 'recipes', recipeId)
      const recipeSnap = await getDoc(recipeRef)

      if (recipeSnap.exists()) {
        const data = recipeSnap.data()
        return {
          id: recipeSnap.id,
          userId,
          ...data,
          createdAt: data.createdAt?.toDate() || new Date(),
          updatedAt: data.updatedAt?.toDate() || new Date(),
        } as Recipe
      }

      return null
    } catch (error) {
      console.error('Error getting recipe:', error)
      return null
    }
  }

  /**
   * Delete a recipe
   */
  async deleteRecipe(userId: string, recipeId: string): Promise<void> {
    try {
      const recipeRef = doc(db, 'users', userId, 'recipes', recipeId)
      await deleteDoc(recipeRef)
    } catch (error) {
      console.error('Error deleting recipe:', error)
      throw error
    }
  }
}

export default RecipesService
