'use client'

import { useState, useEffect } from 'react'
import { FirebaseService } from '../lib/services/firebase'
import CoachieCard from './ui/CoachieCard'
import CoachieButton from './ui/CoachieButton'
import type { UserProfile } from '../types'

interface HabitDashboardProps {
  userId: string
  userProfile: UserProfile
}

interface Habit {
  id: string
  name: string
  description: string
  category: string
  frequency: 'daily' | 'weekly' | 'monthly' | 'custom'
  targetCount: number
  color: string
  icon: string
}

interface HabitCompletion {
  id: string
  habitId: string
  completedAt: Date
  notes?: string
}

export default function HabitDashboard({ userId, userProfile }: HabitDashboardProps) {
  const [habits, setHabits] = useState<Habit[]>([])
  const [completions, setCompletions] = useState<HabitCompletion[]>([])
  const [loading, setLoading] = useState(true)
  const [showCreateForm, setShowCreateForm] = useState(false)

  useEffect(() => {
    loadHabits()
  }, [userId])

  const loadHabits = async () => {
    try {
      setLoading(true)
      const HabitRepository = (await import('../lib/services/habitRepository')).default
      const habitRepo = HabitRepository.getInstance()
      
      // Load real habits from Firebase
      const loadedHabits = await habitRepo.getHabits(userId)
      
      // Filter to only active habits and convert to component format
      const formattedHabits: Habit[] = loadedHabits
        .filter(habit => habit.isActive)
        .map(habit => ({
          id: habit.id,
          name: habit.title,
          description: habit.description || '',
          category: habit.category || 'health',
          frequency: (habit.frequency === 'custom' ? 'daily' : habit.frequency) || 'daily' as 'daily' | 'weekly' | 'monthly' | 'custom',
          targetCount: habit.targetValue || 1,
          color: habit.color || 'blue',
          icon: habit.icon || '🎯'
        }))
      
      // Load today's completions
      const today = new Date().toISOString().split('T')[0]
      const completions = await habitRepo.getHabitCompletions(userId, today)
      
      const formattedCompletions: HabitCompletion[] = completions.map(completion => ({
        id: completion.id,
        habitId: completion.habitId,
        completedAt: completion.completedAt instanceof Date ? completion.completedAt : new Date(completion.completedAt),
        notes: completion.notes
      }))
      
      setHabits(formattedHabits)
      setCompletions(formattedCompletions)
    } catch (error) {
      console.error('Error loading habits:', error)
    } finally {
      setLoading(false)
    }
  }

  const completeHabit = async (habitId: string) => {
    try {
      const HabitRepository = (await import('../lib/services/habitRepository')).default
      const habitRepo = HabitRepository.getInstance()
      
      await habitRepo.completeHabit(userId, habitId, 1)

      // Reload habits and completions to refresh UI
      await loadHabits()
    } catch (error) {
      console.error('Error completing habit:', error)
    }
  }

  const getTodayCompletions = (habitId: string) => {
    const today = new Date().toDateString()
    return completions.filter(c =>
      c.habitId === habitId &&
      new Date(c.completedAt).toDateString() === today
    )
  }

  const getHabitProgress = (habit: Habit) => {
    const todayCompletions = getTodayCompletions(habit.id)
    return Math.min((todayCompletions.length / habit.targetCount) * 100, 100)
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
      </div>
    )
  }

  const gradientClass = userProfile?.gender === 'male'
    ? 'bg-coachie-gradient-male'
    : userProfile?.gender === 'female'
    ? 'bg-coachie-gradient-female'
    : 'bg-coachie-gradient'

  return (
    <div className={`min-h-screen ${gradientClass} py-8 px-4`}>
      <div className="max-w-6xl mx-auto space-y-6">
        {/* Header */}
        <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold text-white">Habits & Goals</h1>
          <p className="text-white/80 mt-1">Build better routines and achieve your objectives</p>
        </div>
        <CoachieButton
          onClick={() => setShowCreateForm(true)}
          icon="➕"
        >
          New Habit
        </CoachieButton>
      </div>

      {/* Today's Habits */}
      <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6">
        <h2 className="text-xl font-semibold text-white mb-4">Today's Habits</h2>

          <div className="space-y-4">
            {habits.map((habit) => {
              const progress = getHabitProgress(habit)
              const todayCompletions = getTodayCompletions(habit.id)

              return (
                <div key={habit.id} className="flex items-center justify-between p-4 bg-white/10 rounded-lg border border-white/20">
                  <div className="flex items-center space-x-4">
                    <div className="text-2xl">{habit.icon}</div>
                    <div>
                      <h3 className="font-medium text-white">{habit.name}</h3>
                      <p className="text-sm text-white/80">{habit.description}</p>
                      <div className="flex items-center space-x-2 mt-1">
                        <div className="text-xs text-white/70">
                          {todayCompletions.length}/{habit.targetCount} completed
                        </div>
                        <div className="w-24 bg-white/20 rounded-full h-1">
                          <div
                            className="bg-primary-500 h-1 rounded-full transition-all duration-300"
                            style={{ width: `${progress}%` }}
                          ></div>
                        </div>
                      </div>
                    </div>
                  </div>

                  <CoachieButton
                    size="sm"
                    onClick={() => completeHabit(habit.id)}
                    disabled={progress >= 100}
                    variant={progress >= 100 ? 'success' : 'primary'}
                  >
                    {progress >= 100 ? '✓ Done' : 'Complete'}
                  </CoachieButton>
                </div>
              )
            })}
          </div>

          {habits.length === 0 && (
            <div className="text-center py-12">
              <div className="text-6xl mb-4">🎯</div>
              <h3 className="text-xl font-semibold text-white mb-2">No habits yet</h3>
              <p className="text-white/80 mb-4">Create your first habit to start building better routines</p>
              <CoachieButton onClick={() => setShowCreateForm(true)}>
                Create Your First Habit
              </CoachieButton>
            </div>
          )}
        </div>
      </div>

      {/* Habit Categories */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6 text-center">
          <div className="text-4xl mb-4">🏃</div>
          <h3 className="text-lg font-semibold text-white mb-2">Fitness Habits</h3>
          <p className="text-white/80 text-sm">Exercise regularly, stay active</p>
        </div>

        <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6 text-center">
          <div className="text-4xl mb-4">🧘</div>
          <h3 className="text-lg font-semibold text-white mb-2">Mindfulness</h3>
          <p className="text-white/80 text-sm">Meditate, practice gratitude</p>
        </div>

        <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6 text-center">
          <div className="text-4xl mb-4">📚</div>
          <h3 className="text-lg font-semibold text-white mb-2">Learning</h3>
          <p className="text-white/80 text-sm">Read, learn new skills</p>
        </div>
      </div>

      {/* Habit Insights */}
      <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6">
        <h2 className="text-xl font-semibold text-white mb-4">Habit Insights</h2>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="text-center">
            <div className="text-3xl font-bold text-blue-400 mb-2">
              {habits.length}
            </div>
            <div className="text-sm text-white/80">Active Habits</div>
          </div>

          <div className="text-center">
            <div className="text-3xl font-bold text-green-400 mb-2">
              {Math.round(habits.reduce((sum, habit) => sum + getHabitProgress(habit), 0) / habits.length) || 0}%
            </div>
            <div className="text-sm text-white/80">Average Completion</div>
          </div>

          <div className="text-center">
            <div className="text-3xl font-bold text-yellow-400 mb-2">
              {completions.filter(c => {
                const today = new Date().toDateString()
                return new Date(c.completedAt).toDateString() === today
              }).length}
            </div>
            <div className="text-sm text-white/80">Completed Today</div>
          </div>
        </div>
      </div>
    </div>
  )
}
