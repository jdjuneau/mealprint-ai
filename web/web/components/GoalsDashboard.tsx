'use client'

import { useState, useEffect } from 'react'
import { FirebaseService } from '../lib/services/firebase'
import CoachieCard from './ui/CoachieCard'
import CoachieButton from './ui/CoachieButton'
import type { UserProfile } from '../types'

interface GoalsDashboardProps {
  userId: string
  userProfile: UserProfile
}

interface Goal {
  id: string
  title: string
  description: string
  category: 'weight' | 'fitness' | 'nutrition' | 'mindfulness' | 'sleep'
  targetValue: number
  currentValue: number
  unit: string
  deadline: Date
  status: 'active' | 'completed' | 'paused'
}

export default function GoalsDashboard({ userId, userProfile }: GoalsDashboardProps) {
  const [goals, setGoals] = useState<Goal[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    loadGoals()
  }, [userId])

  const loadGoals = async () => {
    try {
      // Mock goals based on user profile
      const mockGoals: Goal[] = []

      if (userProfile.goalWeight && userProfile.currentWeight) {
        const weightDiff = userProfile.goalWeight - userProfile.currentWeight
        mockGoals.push({
          id: 'weight_goal',
          title: weightDiff > 0 ? 'Gain Weight' : 'Lose Weight',
          description: `Reach your target weight of ${userProfile.goalWeight} lbs`,
          category: 'weight',
          targetValue: userProfile.goalWeight,
          currentValue: userProfile.currentWeight,
          unit: 'lbs',
          deadline: new Date(Date.now() + 90 * 24 * 60 * 60 * 1000), // 90 days
          status: 'active'
        })
      }

      // Add default fitness goal
      mockGoals.push({
        id: 'fitness_goal',
        title: 'Improve Fitness',
        description: 'Complete 150 minutes of moderate exercise per week',
        category: 'fitness',
        targetValue: 150,
        currentValue: 45, // Mock current progress
        unit: 'minutes/week',
        deadline: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000), // 30 days
        status: 'active'
      })

      // Add nutrition goal
      mockGoals.push({
        id: 'nutrition_goal',
        title: 'Healthy Eating',
        description: 'Eat 5 servings of fruits and vegetables daily',
        category: 'nutrition',
        targetValue: 5,
        currentValue: 3, // Mock current progress
        unit: 'servings/day',
        deadline: new Date(Date.now() + 60 * 24 * 60 * 60 * 1000), // 60 days
        status: 'active'
      })

      setGoals(mockGoals)
    } catch (error) {
      console.error('Error loading goals:', error)
    } finally {
      setLoading(false)
    }
  }

  const getGoalProgress = (goal: Goal) => {
    return Math.min((goal.currentValue / goal.targetValue) * 100, 100)
  }

  const getGoalColor = (category: Goal['category']) => {
    const colors = {
      weight: 'bg-blue-500',
      fitness: 'bg-green-500',
      nutrition: 'bg-orange-500',
      mindfulness: 'bg-purple-500',
      sleep: 'bg-indigo-500'
    }
    return colors[category] || 'bg-gray-500'
  }

  const getDaysRemaining = (deadline: Date) => {
    const now = new Date()
    const diffTime = deadline.getTime() - now.getTime()
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24))
    return diffDays
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Goals & Progress</h1>
          <p className="text-gray-600 mt-1">Track your journey towards better health</p>
        </div>
        <CoachieButton icon="🎯">
          Set New Goal
        </CoachieButton>
      </div>

      {/* Active Goals */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {goals.map((goal) => {
          const progress = getGoalProgress(goal)
          const daysRemaining = getDaysRemaining(goal.deadline)

          return (
            <CoachieCard key={goal.id}>
              <div className="p-6">
                <div className="flex items-start justify-between mb-4">
                  <div className="flex-1">
                    <div className="flex items-center space-x-2 mb-2">
                      <div className={`w-3 h-3 rounded-full ${getGoalColor(goal.category)}`}></div>
                      <span className="text-sm font-medium text-gray-600 uppercase tracking-wide">
                        {goal.category}
                      </span>
                    </div>
                    <h3 className="text-lg font-semibold text-gray-900">{goal.title}</h3>
                    <p className="text-gray-600 text-sm mt-1">{goal.description}</p>
                  </div>
                  <div className="text-right">
                    <div className="text-2xl font-bold text-primary-600">
                      {Math.round(progress)}%
                    </div>
                    <div className="text-xs text-gray-500">Complete</div>
                  </div>
                </div>

                {/* Progress Bar */}
                <div className="mb-4">
                  <div className="flex justify-between text-sm text-gray-600 mb-2">
                    <span>{goal.currentValue} {goal.unit}</span>
                    <span>{goal.targetValue} {goal.unit}</span>
                  </div>
                  <div className="w-full bg-gray-200 rounded-full h-3">
                    <div
                      className="bg-primary-600 h-3 rounded-full transition-all duration-500"
                      style={{ width: `${progress}%` }}
                    ></div>
                  </div>
                </div>

                {/* Deadline */}
                <div className="flex justify-between items-center text-sm">
                  <div className="flex items-center space-x-1">
                    <span className="text-gray-500">⏰</span>
                    <span className="text-gray-600">
                      {daysRemaining > 0 ? `${daysRemaining} days left` : 'Overdue'}
                    </span>
                  </div>

                  <div className={`px-2 py-1 rounded-full text-xs font-medium ${
                    goal.status === 'completed'
                      ? 'bg-success-100 text-success-800'
                      : goal.status === 'active'
                      ? 'bg-primary-100 text-primary-800'
                      : 'bg-gray-100 text-gray-800'
                  }`}>
                    {goal.status}
                  </div>
                </div>
              </div>
            </CoachieCard>
          )
        })}
      </div>

      {/* Goal Categories */}
      <CoachieCard>
        <div className="p-6">
          <h2 className="text-xl font-semibold text-gray-900 mb-4">Goal Categories</h2>

          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
            {[
              { name: 'Weight', icon: '⚖️', color: 'bg-blue-500' },
              { name: 'Fitness', icon: '🏃', color: 'bg-green-500' },
              { name: 'Nutrition', icon: '🥗', color: 'bg-orange-500' },
              { name: 'Sleep', icon: '😴', color: 'bg-indigo-500' },
              { name: 'Mindfulness', icon: '🧘', color: 'bg-purple-500' }
            ].map((category) => (
              <div key={category.name} className="text-center">
                <div className={`w-16 h-16 ${category.color} rounded-full flex items-center justify-center mx-auto mb-2`}>
                  <span className="text-2xl">{category.icon}</span>
                </div>
                <div className="text-sm font-medium text-gray-900">{category.name}</div>
              </div>
            ))}
          </div>
        </div>
      </CoachieCard>

      {/* Achievement Milestones */}
      <CoachieCard>
        <div className="p-6">
          <h2 className="text-xl font-semibold text-gray-900 mb-4">Recent Milestones</h2>

          <div className="space-y-4">
            <div className="flex items-center space-x-4 p-4 bg-success-50 rounded-lg">
              <div className="text-2xl">🏆</div>
              <div className="flex-1">
                <h4 className="font-medium text-gray-900">7-Day Streak!</h4>
                <p className="text-sm text-gray-600">Completed daily habits for a week straight</p>
              </div>
              <div className="text-sm text-success-600 font-medium">2 days ago</div>
            </div>

            <div className="flex items-center space-x-4 p-4 bg-primary-50 rounded-lg">
              <div className="text-2xl">💪</div>
              <div className="flex-1">
                <h4 className="font-medium text-gray-900">Workout Goal Reached</h4>
                <p className="text-sm text-gray-600">Completed 150 minutes of exercise this week</p>
              </div>
              <div className="text-sm text-primary-600 font-medium">5 days ago</div>
            </div>

            <div className="flex items-center space-x-4 p-4 bg-warning-50 rounded-lg">
              <div className="text-2xl">🎯</div>
              <div className="flex-1">
                <h4 className="font-medium text-gray-900">Weight Milestone</h4>
                <p className="text-sm text-gray-600">Lost 5 pounds towards your goal</p>
              </div>
              <div className="text-sm text-warning-600 font-medium">1 week ago</div>
            </div>
          </div>
        </div>
      </CoachieCard>

      {goals.length === 0 && (
        <CoachieCard>
          <div className="p-12 text-center">
            <div className="text-6xl mb-4">🎯</div>
            <h3 className="text-2xl font-semibold text-gray-900 mb-2">No goals set yet</h3>
            <p className="text-gray-600 mb-6">Set your first goal to start your health journey</p>
            <CoachieButton size="lg">
              Create Your First Goal
            </CoachieButton>
          </div>
        </CoachieCard>
      )}
    </div>
  )
}
