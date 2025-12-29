'use client'

import { useState, useEffect } from 'react'
import { useAuth } from '../lib/contexts/AuthContext'
import CoachieCard from './ui/CoachieCard'
import CoachieButton from './ui/CoachieButton'

interface HabitSuggestion {
  id: string
  title: string
  description: string
  reason: string
  difficulty: 'easy' | 'medium' | 'hard'
}

export default function HabitSuggestions() {
  const { user, userProfile } = useAuth()
  const [suggestions, setSuggestions] = useState<HabitSuggestion[]>([])
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    if (user) {
      loadSuggestions()
    }
  }, [user])

  const loadSuggestions = async () => {
    if (!user) return

    setIsLoading(true)
    try {
      // TODO: Call AI suggestion API based on behavioral profile
      // For now, show sample suggestions
      setSuggestions([
        {
          id: '1',
          title: 'Morning Walk',
          description: 'Start your day with a 10-minute walk',
          reason: 'Based on your activity level, a morning walk can boost energy',
          difficulty: 'easy'
        },
        {
          id: '2',
          title: 'Evening Reflection',
          description: 'Spend 5 minutes reflecting on your day',
          reason: 'Your journal entries suggest you value introspection',
          difficulty: 'easy'
        }
      ])
    } catch (error) {
      console.error('Error loading suggestions:', error)
    } finally {
      setIsLoading(false)
    }
  }

  const createHabit = async (suggestion: HabitSuggestion) => {
    if (!user) return

    try {
      // TODO: Create habit from suggestion
      alert(`Created habit: ${suggestion.title}`)
    } catch (error) {
      console.error('Error creating habit:', error)
    }
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-center">
          <div className="text-4xl mb-4">🤖</div>
          <p className="text-gray-600">Analyzing your profile...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-gray-900">AI-Powered Recommendations</h1>
        <p className="text-gray-600 mt-2">
          Based on your behavioral profile, Coachie has analyzed thousands of habit success patterns to recommend the best habits for you.
        </p>
      </div>

      {suggestions.length === 0 ? (
        <CoachieCard>
          <div className="p-12 text-center">
            <div className="text-6xl mb-4">💡</div>
            <h2 className="text-xl font-semibold text-gray-900 mb-2">No Suggestions Yet</h2>
            <p className="text-gray-600">
              Complete your behavioral profile to get personalized habit suggestions.
            </p>
          </div>
        </CoachieCard>
      ) : (
        <div className="space-y-4">
          {suggestions.map((suggestion) => (
            <CoachieCard key={suggestion.id}>
              <div className="p-6">
                <div className="flex items-start justify-between mb-3">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-2">
                      <h3 className="text-xl font-bold">{suggestion.title}</h3>
                      <span className={`px-2 py-1 text-xs rounded ${
                        suggestion.difficulty === 'easy' ? 'bg-green-100 text-green-700' :
                        suggestion.difficulty === 'medium' ? 'bg-yellow-100 text-yellow-700' :
                        'bg-red-100 text-red-700'
                      }`}>
                        {suggestion.difficulty}
                      </span>
                    </div>
                    <p className="text-gray-700 mb-2">{suggestion.description}</p>
                    <p className="text-sm text-gray-500 italic">"{suggestion.reason}"</p>
                  </div>
                </div>
                <CoachieButton onClick={() => createHabit(suggestion)} className="w-full mt-4">
                  Add This Habit
                </CoachieButton>
              </div>
            </CoachieCard>
          ))}
        </div>
      )}
    </div>
  )
}

