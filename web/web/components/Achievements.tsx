'use client'

import { useState, useEffect } from 'react'
import CoachieCard from './ui/CoachieCard'

interface AchievementsProps {
  userId: string
}

interface Achievement {
  id: string
  title: string
  description: string
  icon: string
  unlocked: boolean
  unlockedDate?: Date
  progress?: number
  maxProgress?: number
}

export default function Achievements({ userId }: AchievementsProps) {
  const [achievements, setAchievements] = useState<Achievement[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    loadAchievements()
  }, [userId])

  const loadAchievements = async () => {
    // Mock achievements
    const mockAchievements: Achievement[] = [
      {
        id: 'first_steps',
        title: 'First Steps',
        description: 'Log your first 10,000 steps',
        icon: '👣',
        unlocked: true,
        unlockedDate: new Date('2024-01-15')
      },
      {
        id: 'week_warrior',
        title: 'Week Warrior',
        description: 'Complete workouts 7 days in a row',
        icon: '💪',
        unlocked: true,
        unlockedDate: new Date('2024-01-20')
      },
      {
        id: 'calorie_crusher',
        title: 'Calorie Crusher',
        description: 'Burn 10,000 calories this month',
        icon: '🔥',
        unlocked: false,
        progress: 7500,
        maxProgress: 10000
      },
      {
        id: 'sleep_champion',
        title: 'Sleep Champion',
        description: 'Get 8+ hours of sleep for 30 nights',
        icon: '😴',
        unlocked: false,
        progress: 12,
        maxProgress: 30
      },
      {
        id: 'habit_master',
        title: 'Habit Master',
        description: 'Maintain 5 habits for 30 days each',
        icon: '🎯',
        unlocked: false,
        progress: 3,
        maxProgress: 5
      }
    ]

    setAchievements(mockAchievements)
    setLoading(false)
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
      </div>
    )
  }

  const unlockedAchievements = achievements.filter(a => a.unlocked)
  const lockedAchievements = achievements.filter(a => !a.unlocked)

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="text-center">
        <h1 className="text-3xl font-bold text-gray-900 mb-2">Achievements</h1>
        <p className="text-gray-600">Your fitness journey milestones</p>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <CoachieCard>
          <div className="p-6 text-center">
            <div className="text-3xl font-bold text-primary-600 mb-2">
              {unlockedAchievements.length}
            </div>
            <div className="text-sm text-gray-600">Unlocked</div>
          </div>
        </CoachieCard>

        <CoachieCard>
          <div className="p-6 text-center">
            <div className="text-3xl font-bold text-warning-600 mb-2">
              {lockedAchievements.length}
            </div>
            <div className="text-sm text-gray-600">In Progress</div>
          </div>
        </CoachieCard>

        <CoachieCard>
          <div className="p-6 text-center">
            <div className="text-3xl font-bold text-success-600 mb-2">
              {Math.round((unlockedAchievements.length / achievements.length) * 100)}%
            </div>
            <div className="text-sm text-gray-600">Complete</div>
          </div>
        </CoachieCard>
      </div>

      {/* Unlocked Achievements */}
      <CoachieCard>
        <div className="p-6">
          <h2 className="text-xl font-semibold text-gray-900 mb-4">Unlocked Achievements</h2>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {unlockedAchievements.map((achievement) => (
              <div key={achievement.id} className="flex items-center space-x-4 p-4 bg-success-50 rounded-lg border border-success-200">
                <div className="text-3xl">{achievement.icon}</div>
                <div className="flex-1">
                  <h3 className="font-semibold text-gray-900">{achievement.title}</h3>
                  <p className="text-sm text-gray-600">{achievement.description}</p>
                  {achievement.unlockedDate && (
                    <p className="text-xs text-success-600 mt-1">
                      Unlocked {achievement.unlockedDate.toLocaleDateString()}
                    </p>
                  )}
                </div>
                <div className="text-2xl">✅</div>
              </div>
            ))}
          </div>
        </div>
      </CoachieCard>

      {/* In Progress */}
      <CoachieCard>
        <div className="p-6">
          <h2 className="text-xl font-semibold text-gray-900 mb-4">In Progress</h2>

          <div className="space-y-4">
            {lockedAchievements.map((achievement) => (
              <div key={achievement.id} className="p-4 border border-gray-200 rounded-lg">
                <div className="flex items-center space-x-4 mb-3">
                  <div className="text-3xl opacity-50">{achievement.icon}</div>
                  <div className="flex-1">
                    <h3 className="font-semibold text-gray-900">{achievement.title}</h3>
                    <p className="text-sm text-gray-600">{achievement.description}</p>
                  </div>
                </div>

                {achievement.progress !== undefined && achievement.maxProgress && (
                  <div>
                    <div className="flex justify-between text-sm text-gray-600 mb-2">
                      <span>{achievement.progress} / {achievement.maxProgress}</span>
                      <span>{Math.round((achievement.progress / achievement.maxProgress) * 100)}%</span>
                    </div>
                    <div className="w-full bg-gray-200 rounded-full h-2">
                      <div
                        className="bg-primary-600 h-2 rounded-full transition-all duration-300"
                        style={{ width: `${(achievement.progress / achievement.maxProgress) * 100}%` }}
                      ></div>
                    </div>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      </CoachieCard>
    </div>
  )
}
