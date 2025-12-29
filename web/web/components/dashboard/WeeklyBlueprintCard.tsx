'use client'

import { useState, useEffect } from 'react'
import { useAuth } from '../../lib/contexts/AuthContext'

interface WeeklyBlueprintCardProps {
  weeklyBlueprint: Record<string, any> | null
  generatingBlueprint: boolean
  onGenerate: () => void
  onNavigate: () => void
}

export default function WeeklyBlueprintCard({
  weeklyBlueprint,
  generatingBlueprint,
  onGenerate,
  onNavigate
}: WeeklyBlueprintCardProps) {
  const { user } = useAuth()
  const [isPro, setIsPro] = useState<boolean>(false)
  const [checkingPro, setCheckingPro] = useState(true)

  useEffect(() => {
    const checkProStatus = async () => {
      if (!user) {
        setCheckingPro(false)
        return
      }
      try {
        const SubscriptionService = (await import('../../lib/services/subscriptionService')).default
        const subscriptionService = SubscriptionService.getInstance()
        const proStatus = await subscriptionService.isPro(user.uid)
        setIsPro(proStatus)
      } catch (error) {
        console.error('Error checking Pro status:', error)
        setIsPro(false)
      } finally {
        setCheckingPro(false)
      }
    }
    checkProStatus()
  }, [user])
  let itemCount = 0
  let totalMeals = 0

  if (weeklyBlueprint) {
    const shoppingList = weeklyBlueprint.shoppingList as Record<string, any> | undefined
    if (shoppingList) {
      itemCount = Object.values(shoppingList).reduce((sum: number, category: any) => {
        return sum + (Array.isArray(category) ? category.length : 0)
      }, 0)
    }

    const mealsData = weeklyBlueprint.meals
    if (Array.isArray(mealsData)) {
      totalMeals = mealsData.reduce((sum, day) => {
        if (typeof day === 'object' && day !== null) {
          let count = 0
          if (day.breakfast) count++
          if (day.lunch) count++
          if (day.dinner) count++
          if (day.meal) count++
          if (Array.isArray(day.snacks)) count += day.snacks.length
          return sum + count
        }
        return sum
      }, 0)
    } else if (typeof mealsData === 'object' && mealsData !== null) {
      totalMeals = Object.values(mealsData).reduce((sum: number, day: any) => {
        if (typeof day === 'object' && day !== null) {
          let count = 0
          if (day.breakfast) count++
          if (day.lunch) count++
          if (day.dinner) count++
          if (day.meal) count++
          if (Array.isArray(day.snacks)) count += day.snacks.length
          return sum + count
        }
        return sum
      }, 0)
    }
  }

  return (
    <div
      className="bg-purple-500/20 rounded-lg border border-purple-500/40 p-5 cursor-pointer hover:bg-purple-500/30 transition-colors"
      onClick={onNavigate}
    >
      <div className="flex items-center justify-between mb-4">
        <div className="flex-1">
          <div className="flex items-center gap-2 mb-2">
            <h3 className="text-xl font-bold text-white">Your Weekly Blueprint</h3>
            {!checkingPro && !isPro && (
              <span className="px-2 py-0.5 bg-yellow-500/80 text-yellow-900 text-xs font-semibold rounded-full flex items-center gap-1">
                <span>🔒</span>
                <span>Pro</span>
              </span>
            )}
          </div>
          {weeklyBlueprint ? (
            <p className="text-white/80 text-sm">
              {itemCount} items • {totalMeals} meals planned
            </p>
          ) : (
            <p className="text-white/70 text-sm">
              {!isPro && !checkingPro ? 'Pro feature - Upgrade to unlock' : 'Generate your weekly meal plan'}
            </p>
          )}
        </div>
        <svg className="w-8 h-8 text-white flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
          <path d="M3 1a1 1 0 000 2h1.22l.305 1.222a.997.997 0 00.01.042l1.358 5.43-.893.892C3.74 11.846 4.632 14 6.414 14H15a1 1 0 000-2H6.414l1-1H14a1 1 0 00.894-.553l3-6A1 1 0 0017 3H6.28l-.31-1.243A1 1 0 005 1H3zM16 16.5a1.5 1.5 0 11-3 0 1.5 1.5 0 013 0zM6.5 18a1.5 1.5 0 100-3 1.5 1.5 0 000 3z" />
        </svg>
      </div>

      {weeklyBlueprint === null && !generatingBlueprint && (
        <>
        <button
          onClick={(e) => {
            e.stopPropagation()
            onGenerate()
          }}
            className={`w-full px-4 py-2 rounded-lg font-medium ${
              !isPro && !checkingPro
                ? 'bg-gray-500 text-white cursor-not-allowed opacity-75'
                : 'bg-purple-600 text-white hover:bg-purple-700'
            }`}
            disabled={!isPro && !checkingPro}
          >
            {!isPro && !checkingPro ? '🔒 Pro Feature' : 'Generate my blueprint'}
          </button>
          {!isPro && !checkingPro && (
            <button
              onClick={(e) => {
                e.stopPropagation()
                window.location.href = '/subscription'
              }}
              className="w-full mt-2 px-4 py-2 bg-yellow-500 text-yellow-900 rounded-lg hover:bg-yellow-600 font-medium text-sm"
            >
              Upgrade to Pro →
        </button>
          )}
        </>
      )}

      {generatingBlueprint && (
        <div className="flex items-center justify-center gap-3">
          <div className="w-6 h-6 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
          <span className="text-white/80 text-sm">Coachie is thinking…</span>
        </div>
      )}

      {weeklyBlueprint !== null && !generatingBlueprint && (
        <button
          onClick={(e) => {
            e.stopPropagation()
            onNavigate()
          }}
          className="w-full px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 font-medium mt-4"
        >
          View Full List
        </button>
      )}
    </div>
  )
}

