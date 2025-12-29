'use client'

import { useAuth } from '../../lib/contexts/AuthContext'
import { useRouter } from 'next/navigation'
import { useState, useEffect } from 'react'
import LoadingScreen from '../../components/LoadingScreen'
import toast from 'react-hot-toast'
import { SavedMeal } from '../../types'

export default function SavedMealsPage() {
  const { user } = useAuth()
  const router = useRouter()
  const [loading, setLoading] = useState(true)
  const [meals, setMeals] = useState<SavedMeal[]>([])
  const [showServingSizeDialog, setShowServingSizeDialog] = useState(false)
  const [selectedMeal, setSelectedMeal] = useState<SavedMeal | null>(null)
  const [servingSize, setServingSize] = useState('1.0')

  useEffect(() => {
    if (!user) {
      router.push('/auth')
    } else {
      loadMeals()
    }
  }, [user, router])

  const loadMeals = async () => {
    if (!user) return
    try {
      const SavedMealsService = (await import('../../lib/services/savedMealsService')).default
      const savedMealsService = SavedMealsService.getInstance()
      const loadedMeals = await savedMealsService.getSavedMeals(user.uid)
      setMeals(loadedMeals)
    } catch (error) {
      console.error('Error loading saved meals:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleMealClick = (meal: SavedMeal) => {
    setSelectedMeal(meal)
    setServingSize('1.0')
    setShowServingSizeDialog(true)
  }

  const handleUseMeal = async () => {
    if (!user || !selectedMeal) return
    
    const servingMultiplier = parseFloat(servingSize) || 1.0
    
    try {
      // Update usage count
      const SavedMealsService = (await import('../../lib/services/savedMealsService')).default
      const savedMealsService = SavedMealsService.getInstance()
      await savedMealsService.updateMealUsage(user.uid, selectedMeal.id)

      // Log the meal with serving size adjustment
      const today = new Date().toISOString().split('T')[0]
      const { FirebaseService } = await import('../../lib/services/firebase')
      await FirebaseService.saveHealthLog(user.uid, today, {
        id: `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        userId: user.uid,
        type: 'meal',
        timestamp: new Date(),
        foodName: selectedMeal.foodName,
        calories: Math.round(selectedMeal.calories * servingMultiplier),
        protein: Math.round(selectedMeal.proteinG * servingMultiplier * 10) / 10,
        carbs: Math.round(selectedMeal.carbsG * servingMultiplier * 10) / 10,
        fat: Math.round(selectedMeal.fatG * servingMultiplier * 10) / 10,
      })

      toast.success(`Logged ${selectedMeal.name} (${servingSize}x)!`)
      setShowServingSizeDialog(false)
      setSelectedMeal(null)
      router.back()
    } catch (error) {
      console.error('Error using meal:', error)
      toast.error('Failed to log meal')
    }
  }

  if (loading || !user) {
    return <LoadingScreen />
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-4xl mx-auto py-8 px-4">
        <div className="flex items-center justify-between mb-6">
          <h1 className="text-2xl font-bold text-gray-900">Saved Meals</h1>
          <button
            onClick={() => router.push('/meal-log')}
            className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
          >
            + New Meal
          </button>
        </div>

        {meals.length === 0 ? (
          <div className="bg-white rounded-lg shadow-sm p-12 text-center">
            <div className="text-4xl mb-4">🍽️</div>
            <h2 className="text-xl font-semibold text-gray-900 mb-2">No saved meals</h2>
            <p className="text-gray-600 mb-6">Save frequently eaten meals for quick logging</p>
            <button
              onClick={() => router.push('/meal-log')}
              className="px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
            >
              Log Your First Meal
            </button>
          </div>
        ) : (
          <div className="space-y-4">
            {meals.map((meal) => (
              <div
                key={meal.id}
                className="bg-white rounded-lg shadow-sm p-6 flex items-center justify-between"
              >
                <div className="flex-1">
                  <h3 className="font-semibold text-gray-900">{meal.name}</h3>
                  <p className="text-sm text-gray-600 mt-1">{meal.foodName}</p>
                  <div className="flex gap-4 mt-2 text-sm text-gray-600">
                    <span>{meal.calories} cal</span>
                    <span>{meal.proteinG}g protein</span>
                    <span>{meal.carbsG}g carbs</span>
                    <span>{meal.fatG}g fat</span>
                  </div>
                  <p className="text-xs text-gray-500 mt-2">
                    Used {meal.useCount} times • Last used{' '}
                    {new Date(meal.lastUsedAt).toLocaleDateString()}
                  </p>
                </div>
                <button
                  onClick={() => handleMealClick(meal)}
                  className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
                >
                  Use
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Serving Size Dialog */}
      {showServingSizeDialog && selectedMeal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6">
            <h2 className="text-xl font-bold text-gray-900 mb-4">Select Serving Size</h2>
            <p className="text-gray-600 mb-4">
              How many servings of &quot;{selectedMeal.name}&quot;?
            </p>
            
            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Serving Size
              </label>
              <input
                type="text"
                value={servingSize}
                onChange={(e) => {
                  const value = e.target.value
                  // Only allow numbers and decimal point
                  if (/^\d*\.?\d*$/.test(value)) {
                    setServingSize(value)
                  }
                }}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="1.0"
              />
            </div>

            {/* Quick buttons for common serving sizes */}
            <div className="grid grid-cols-3 gap-2 mb-6">
              {['0.5', '1.0', '1.5', '2.0', '2.5', '3.0'].map((size) => (
                <button
                  key={size}
                  onClick={() => setServingSize(size)}
                  className={`px-4 py-2 rounded-lg border-2 transition-colors ${
                    servingSize === size
                      ? 'bg-blue-600 text-white border-blue-600 font-medium'
                      : 'bg-gray-100 text-gray-700 border-gray-300 hover:bg-gray-200'
                  }`}
                >
                  {size}x
                </button>
              ))}
            </div>

            {/* Nutrition preview */}
            <div className="bg-gray-50 rounded-lg p-4 mb-6">
              <p className="text-sm font-medium text-gray-700 mb-2">Nutrition (per {servingSize}x serving):</p>
              <div className="grid grid-cols-2 gap-2 text-sm">
                <div>
                  <span className="text-gray-600">Calories:</span>{' '}
                  <span className="font-semibold">{Math.round(selectedMeal.calories * (parseFloat(servingSize) || 1.0))}</span>
                </div>
                <div>
                  <span className="text-gray-600">Protein:</span>{' '}
                  <span className="font-semibold">{Math.round(selectedMeal.proteinG * (parseFloat(servingSize) || 1.0) * 10) / 10}g</span>
                </div>
                <div>
                  <span className="text-gray-600">Carbs:</span>{' '}
                  <span className="font-semibold">{Math.round(selectedMeal.carbsG * (parseFloat(servingSize) || 1.0) * 10) / 10}g</span>
                </div>
                <div>
                  <span className="text-gray-600">Fat:</span>{' '}
                  <span className="font-semibold">{Math.round(selectedMeal.fatG * (parseFloat(servingSize) || 1.0) * 10) / 10}g</span>
                </div>
              </div>
            </div>

            <div className="flex gap-3">
              <button
                onClick={() => {
                  setShowServingSizeDialog(false)
                  setSelectedMeal(null)
                }}
                className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50"
              >
                Cancel
              </button>
              <button
                onClick={handleUseMeal}
                className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
              >
                Log Meal
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
