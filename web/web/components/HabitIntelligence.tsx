'use client'

import { useState, useEffect } from 'react'
import { useAuth } from '../lib/contexts/AuthContext'
import CoachieCard from './ui/CoachieCard'

export default function HabitIntelligence() {
  const { user } = useAuth()
  const [intelligenceScore, setIntelligenceScore] = useState({
    overallScore: 75,
    consistency: 80,
    engagement: 70,
    growth: 75
  })
  const [patterns, setPatterns] = useState<any[]>([])
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    if (user) {
      loadIntelligence()
    }
  }, [user])

  const loadIntelligence = async () => {
    if (!user) return

    setIsLoading(true)
    try {
      // TODO: Load habit intelligence data
      // Sample data for now
      setPatterns([
        {
          type: 'Best Day',
          value: 'Tuesday',
          description: 'You complete most habits on Tuesdays'
        },
        {
          type: 'Best Time',
          value: 'Morning',
          description: 'You\'re most consistent in the morning'
        }
      ])
    } catch (error) {
      console.error('Error loading intelligence:', error)
    } finally {
      setIsLoading(false)
    }
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-center">
          <div className="text-4xl mb-4">🧠</div>
          <p className="text-gray-600">Analyzing your habits...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <h1 className="text-3xl font-bold text-gray-900">Habit Intelligence</h1>

      {/* Intelligence Score */}
      <CoachieCard>
        <div className="p-6">
          <h2 className="text-xl font-bold mb-4">Overall Intelligence Score</h2>
          <div className="text-center mb-6">
            <div className="text-6xl font-bold text-primary-600 mb-2">
              {intelligenceScore.overallScore}
            </div>
            <p className="text-gray-600">Out of 100</p>
          </div>
          <div className="grid grid-cols-3 gap-4">
            <div className="text-center">
              <div className="text-2xl font-bold text-primary-600">{intelligenceScore.consistency}</div>
              <div className="text-sm text-gray-600">Consistency</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-primary-600">{intelligenceScore.engagement}</div>
              <div className="text-sm text-gray-600">Engagement</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-primary-600">{intelligenceScore.growth}</div>
              <div className="text-sm text-gray-600">Growth</div>
            </div>
          </div>
        </div>
      </CoachieCard>

      {/* Patterns */}
      {patterns.length > 0 && (
        <CoachieCard>
          <div className="p-6">
            <h2 className="text-xl font-bold mb-4">Pattern Recognition</h2>
            <div className="space-y-4">
              {patterns.map((pattern, index) => (
                <div key={index} className="p-4 bg-gray-50 rounded-lg">
                  <div className="font-semibold text-gray-900">{pattern.type}: {pattern.value}</div>
                  <p className="text-sm text-gray-600 mt-1">{pattern.description}</p>
                </div>
              ))}
            </div>
          </div>
        </CoachieCard>
      )}
    </div>
  )
}

