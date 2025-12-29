'use client'

import { useState } from 'react'
import CoachieCard from './ui/CoachieCard'
import CoachieButton from './ui/CoachieButton'

interface MindfulnessProps {
  userId: string
  onNavigate?: (tab: string) => void
}

export default function Mindfulness({ userId, onNavigate }: MindfulnessProps) {
  const [selectedSession, setSelectedSession] = useState<string | null>(null)

  const meditationSessions = [
    {
      id: 'morning',
      title: 'Morning Awakening',
      duration: '10 min',
      description: 'Start your day with clarity and intention',
      icon: '🌅'
    },
    {
      id: 'stress',
      title: 'Stress Relief',
      duration: '15 min',
      description: 'Release tension and find inner peace',
      icon: '😌'
    },
    {
      id: 'sleep',
      title: 'Sleep Preparation',
      duration: '20 min',
      description: 'Wind down and prepare for restful sleep',
      icon: '🌙'
    },
    {
      id: 'focus',
      title: 'Deep Focus',
      duration: '25 min',
      description: 'Enhance concentration and mental clarity',
      icon: '🎯'
    }
  ]

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="text-center">
        <h1 className="text-3xl font-bold text-gray-900 mb-2">Mindfulness & Meditation</h1>
        <p className="text-gray-600">Find peace, reduce stress, and improve focus</p>
      </div>

      {/* Quick Actions */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <CoachieCard>
          <div className="p-6 text-center">
            <div className="text-5xl mb-3">🧘</div>
            <h3 className="text-lg font-semibold mb-2">Meditation</h3>
            <CoachieButton
              fullWidth
              onClick={() => onNavigate?.('meditation')}
            >
              Start Session
            </CoachieButton>
          </div>
        </CoachieCard>
        <CoachieCard>
          <div className="p-6 text-center">
            <div className="text-5xl mb-3">🌬️</div>
            <h3 className="text-lg font-semibold mb-2">Breathing</h3>
            <CoachieButton
              fullWidth
              onClick={() => onNavigate?.('breathing')}
            >
              Start Exercise
            </CoachieButton>
          </div>
        </CoachieCard>
        <CoachieCard>
          <div className="p-6 text-center">
            <div className="text-5xl mb-3">📱</div>
            <h3 className="text-lg font-semibold mb-2">Social Break</h3>
            <CoachieButton
              fullWidth
              onClick={() => onNavigate?.('social-media-break')}
            >
              Start Break
            </CoachieButton>
          </div>
        </CoachieCard>
        <CoachieCard>
          <div className="p-6 text-center">
            <div className="text-5xl mb-3">👁️</div>
            <h3 className="text-lg font-semibold mb-2">Body Scan</h3>
            <CoachieButton
              fullWidth
              onClick={() => onNavigate?.('body-scan')}
            >
              Start Scan
            </CoachieButton>
          </div>
        </CoachieCard>
        <CoachieCard>
          <div className="p-6 text-center">
            <div className="text-5xl mb-3">🌿</div>
            <h3 className="text-lg font-semibold mb-2">Grounding</h3>
            <CoachieButton
              fullWidth
              onClick={() => onNavigate?.('grounding')}
            >
              Start Exercise
            </CoachieButton>
          </div>
        </CoachieCard>
      </div>


      {/* Daily Mindfulness Tips */}
      <CoachieCard>
        <div className="p-6">
          <h2 className="text-xl font-semibold text-gray-900 mb-4">Daily Mindfulness Tips</h2>

          <div className="space-y-4">
            <div className="flex items-start space-x-3">
              <div className="text-2xl">🌬️</div>
              <div>
                <h4 className="font-medium text-gray-900">Practice Deep Breathing</h4>
                <p className="text-sm text-gray-600">Take 5 deep breaths, inhaling for 4 counts and exhaling for 6.</p>
              </div>
            </div>

            <div className="flex items-start space-x-3">
              <div className="text-2xl">🎯</div>
              <div>
                <h4 className="font-medium text-gray-900">Mindful Eating</h4>
                <p className="text-sm text-gray-600">Eat your next meal without distractions, savoring each bite.</p>
              </div>
            </div>

            <div className="flex items-start space-x-3">
              <div className="text-2xl">🚶</div>
              <div>
                <h4 className="font-medium text-gray-900">Walking Meditation</h4>
                <p className="text-sm text-gray-600">Take a 10-minute walk, focusing on each step and breath.</p>
              </div>
            </div>
          </div>
        </div>
      </CoachieCard>

      {/* Progress Tracking */}
      <CoachieCard>
        <div className="p-6">
          <h2 className="text-xl font-semibold text-gray-900 mb-4">Your Progress</h2>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="text-center">
              <div className="text-3xl font-bold text-primary-600 mb-2">12</div>
              <div className="text-sm text-gray-600">Sessions This Week</div>
            </div>

            <div className="text-center">
              <div className="text-3xl font-bold text-success-600 mb-2">45</div>
              <div className="text-sm text-gray-600">Minutes Meditated</div>
            </div>

            <div className="text-center">
              <div className="text-3xl font-bold text-purple-600 mb-2">7</div>
              <div className="text-sm text-gray-600">Day Streak</div>
            </div>
          </div>
        </div>
      </CoachieCard>
    </div>
  )
}
