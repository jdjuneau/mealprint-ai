'use client'

import { useAuth } from '../../lib/contexts/AuthContext'
import MealLogger from '../../components/MealLogger'
import { useRouter } from 'next/navigation'
import { useState } from 'react'
import type { HealthLog } from '../../types'
import { FirebaseService } from '../../lib/services/firebase'

export default function MealLogPage() {
  const { user, userProfile } = useAuth()
  const router = useRouter()
  const [todayLog, setTodayLog] = useState<any>(null)

  const today = new Date().toISOString().split('T')[0]

  const addHealthLog = async (log: HealthLog) => {
    if (!user) return

    try {
      await FirebaseService.saveHealthLog(user.uid, today, log)
      // Refresh today's log
      const updatedLog = await FirebaseService.getDailyLog(user.uid, today)
      setTodayLog(updatedLog)
    } catch (error) {
      console.error('Error adding health log:', error)
      throw error
    }
  }

  if (!user || !userProfile) {
    return <div>Loading...</div>
  }

  const gradientClass = userProfile?.gender === 'male'
    ? 'bg-coachie-gradient-male'
    : userProfile?.gender === 'female'
    ? 'bg-coachie-gradient-female'
    : 'bg-coachie-gradient'

  return (
    <div className={`min-h-screen ${gradientClass} py-8 px-4`}>
      <div className="max-w-2xl mx-auto">
        <button
          onClick={() => router.back()}
          className="mb-4 text-white/90 hover:text-white flex items-center gap-2"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
          </svg>
          Back
        </button>
        <MealLogger
          onAddLog={addHealthLog}
          userId={user.uid}
          onNavigateToRecipeCapture={() => router.push('/recipe-capture')}
          onNavigateToSavedMeals={() => router.push('/saved-meals')}
          onNavigateToMealRecommendation={() => router.push('/meal-recommendation')}
          onNavigateToMyRecipes={() => router.push('/my-recipes')}
        />
      </div>
    </div>
  )
}
