'use client'

import { useAuth } from '../../../lib/contexts/AuthContext'
import { useRouter, useParams } from 'next/navigation'
import { useState, useEffect } from 'react'
import LoadingScreen from '../../../components/LoadingScreen'

export default function MealDetailPage() {
  const { user } = useAuth()
  const router = useRouter()
  const params = useParams()
  const mealId = params.mealId as string
  const [loading, setLoading] = useState(true)
  const [meal, setMeal] = useState<any>(null)

  useEffect(() => {
    if (!user) {
      router.push('/auth')
    } else {
      loadMeal()
    }
  }, [user, router, mealId])

  const loadMeal = async () => {
    if (!user || !mealId) return
    try {
      // Search through recent logs to find the meal
      const { FirebaseService } = await import('../../../lib/services/firebase')
      const recentLogs = await FirebaseService.getRecentDailyLogs(user.uid, 30)
      
      for (const log of recentLogs) {
        const mealLog = log.logs?.find((l: any) => (l.id === mealId || l.entryId === mealId) && l.type === 'meal')
        if (mealLog) {
          setMeal(mealLog)
          break
        }
      }
    } catch (error) {
      console.error('Error loading meal:', error)
    } finally {
      setLoading(false)
    }
  }

  if (loading || !user) {
    return <LoadingScreen />
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-4xl mx-auto py-8 px-4">
        {meal ? (
          <div className="bg-white rounded-lg shadow-sm p-6">
            <h1 className="text-2xl font-bold text-gray-900 mb-4">{meal.foodName || 'Meal'}</h1>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
              <div>
                <p className="text-sm text-gray-600">Calories</p>
                <p className="text-xl font-bold">{meal.calories || 0}</p>
              </div>
              <div>
                <p className="text-sm text-gray-600">Protein</p>
                <p className="text-xl font-bold">{meal.protein || 0}g</p>
              </div>
              <div>
                <p className="text-sm text-gray-600">Carbs</p>
                <p className="text-xl font-bold">{meal.carbs || 0}g</p>
              </div>
              <div>
                <p className="text-sm text-gray-600">Fat</p>
                <p className="text-xl font-bold">{meal.fat || 0}g</p>
              </div>
            </div>
            {meal.photoUrl && (
              <img
                src={meal.photoUrl}
                alt={meal.foodName}
                className="w-full h-64 object-cover rounded-lg mb-4"
              />
            )}
            <p className="text-sm text-gray-500">
              Logged {new Date(meal.timestamp).toLocaleString()}
            </p>
          </div>
        ) : (
          <div className="bg-white rounded-lg shadow-sm p-12 text-center">
            <p className="text-gray-600">Meal not found</p>
          </div>
        )}
      </div>
    </div>
  )
}
