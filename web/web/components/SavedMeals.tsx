'use client'

import { useState, useEffect } from 'react'
import { FirebaseService } from '../lib/services/firebase'
import type { SavedMeal } from '../types'

interface SavedMealsProps {
  userId: string
  onSelectMeal: (meal: SavedMeal) => void
}

export default function SavedMeals({ userId, onSelectMeal }: SavedMealsProps) {
  const [meals, setMeals] = useState<SavedMeal[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    loadSavedMeals()
  }, [userId])

  const loadSavedMeals = async () => {
    try {
      const SavedMealsService = (await import('../lib/services/savedMealsService')).default
      const savedMealsService = SavedMealsService.getInstance()
      const savedMeals = await savedMealsService.getSavedMeals(userId)
      setMeals(savedMeals)
    } catch (error) {
      console.error('Error loading saved meals:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleMealSelect = async (meal: SavedMeal) => {
    // Update usage count
    try {
      const SavedMealsService = (await import('../lib/services/savedMealsService')).default
      const savedMealsService = SavedMealsService.getInstance()
      await savedMealsService.updateMealUsage(userId, meal.id)
      // Update local state
      setMeals(prev => prev.map(m => m.id === meal.id ? { ...m, useCount: m.useCount + 1, lastUsedAt: new Date() } : m))
    } catch (error) {
      console.error('Error updating meal usage:', error)
    }

    onSelectMeal(meal)
  }

  if (loading) {
    return (
      <div className="max-w-4xl mx-auto">
        <div className="text-center py-8">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600 mx-auto mb-4"></div>
          <p className="text-gray-500">Loading saved meals...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div className="text-center">
        <h1 className="text-2xl font-bold text-gray-900 mb-2">⭐ Saved Meals</h1>
        <p className="text-gray-600">Quick-select your frequently eaten meals</p>
      </div>

      {meals.length === 0 ? (
        <div className="bg-white rounded-lg shadow-sm p-8 text-center">
          <div className="text-6xl mb-4">🍽️</div>
          <h3 className="text-lg font-medium text-gray-900 mb-2">
            No saved meals yet
          </h3>
          <p className="text-gray-600">
            Save meals after analyzing them to quickly log them again later.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {meals.map((meal) => (
            <div
              key={meal.id}
              className="bg-white rounded-lg shadow-sm p-4 hover:shadow-md transition-shadow cursor-pointer"
              onClick={() => handleMealSelect(meal)}
            >
              <div className="flex justify-between items-start mb-3">
                <h3 className="font-semibold text-gray-900 text-lg">{meal.name}</h3>
                <span className="text-xs text-gray-500 bg-gray-100 px-2 py-1 rounded">
                  Used {meal.useCount}x
                </span>
              </div>

              <p className="text-sm text-gray-600 mb-3">{meal.foodName}</p>

              <div className="space-y-1 text-sm">
                <div className="flex justify-between">
                  <span className="text-gray-600">Calories:</span>
                  <span className="font-medium text-primary-600">{meal.calories} cal</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600">Protein:</span>
                  <span className="font-medium">{meal.proteinG}g</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600">Carbs:</span>
                  <span className="font-medium">{meal.carbsG}g</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600">Fat:</span>
                  <span className="font-medium">{meal.fatG}g</span>
                </div>
              </div>

              <div className="mt-3 pt-3 border-t border-gray-100">
                <p className="text-xs text-gray-500">
                  Last used: {new Date(meal.lastUsedAt).toLocaleDateString()}
                </p>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
