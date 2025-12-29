'use client'

import { useState } from 'react'
import { useAuth } from '../lib/contexts/AuthContext'
import CoachieCard from './ui/CoachieCard'
import CoachieButton from './ui/CoachieButton'

interface HabitTemplate {
  id: string
  title: string
  description: string
  category: string
  frequency: string
  targetValue: number
  unit: string
}

const templates: HabitTemplate[] = [
  {
    id: 'water',
    title: 'Drink 8 glasses of water',
    description: 'Stay hydrated throughout the day',
    category: 'Health',
    frequency: 'Daily',
    targetValue: 8,
    unit: 'glasses'
  },
  {
    id: 'meditation',
    title: '10-minute meditation',
    description: 'Daily mindfulness practice',
    category: 'Mental Health',
    frequency: 'Daily',
    targetValue: 10,
    unit: 'minutes'
  },
  {
    id: 'walk',
    title: '10,000 steps',
    description: 'Daily walking goal',
    category: 'Fitness',
    frequency: 'Daily',
    targetValue: 10000,
    unit: 'steps'
  },
  {
    id: 'gratitude',
    title: 'Gratitude journaling',
    description: 'Write 3 things you\'re grateful for',
    category: 'Mental Health',
    frequency: 'Daily',
    targetValue: 3,
    unit: 'items'
  },
  {
    id: 'reading',
    title: 'Read for 30 minutes',
    description: 'Daily reading habit',
    category: 'Learning',
    frequency: 'Daily',
    targetValue: 30,
    unit: 'minutes'
  },
  {
    id: 'exercise',
    title: '30-minute workout',
    description: 'Daily exercise routine',
    category: 'Fitness',
    frequency: 'Daily',
    targetValue: 30,
    unit: 'minutes'
  },
  {
    id: 'sleep',
    title: '8 hours of sleep',
    description: 'Consistent sleep schedule',
    category: 'Sleep',
    frequency: 'Daily',
    targetValue: 8,
    unit: 'hours'
  },
  {
    id: 'protein',
    title: 'Eat protein with every meal',
    description: 'Nutrition goal',
    category: 'Nutrition',
    frequency: 'Daily',
    targetValue: 3,
    unit: 'meals'
  }
]

export default function HabitTemplates() {
  const { user } = useAuth()
  const [creating, setCreating] = useState<string | null>(null)

  const createHabit = async (template: HabitTemplate) => {
    if (!user) return

    setCreating(template.id)
    try {
      // TODO: Call habit creation API
      alert(`Created habit: ${template.title}`)
    } catch (error) {
      console.error('Error creating habit:', error)
      alert('Failed to create habit')
    } finally {
      setCreating(null)
    }
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <h1 className="text-3xl font-bold text-gray-900">Habit Templates</h1>
      <p className="text-gray-600">
        Quickly add common habits with pre-configured settings
      </p>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {templates.map((template) => (
          <CoachieCard key={template.id}>
            <div className="p-6">
              <div className="flex items-start justify-between mb-3">
                <div className="flex-1">
                  <h3 className="text-xl font-bold mb-1">{template.title}</h3>
                  <p className="text-gray-600 mb-3">{template.description}</p>
                  <div className="flex gap-2 flex-wrap">
                    <span className="px-2 py-1 text-xs bg-primary-100 text-primary-700 rounded">
                      {template.category}
                    </span>
                    <span className="px-2 py-1 text-xs bg-gray-100 text-gray-700 rounded">
                      {template.frequency}
                    </span>
                    {template.targetValue > 0 && (
                      <span className="px-2 py-1 text-xs bg-gray-100 text-gray-700 rounded">
                        {template.targetValue} {template.unit}
                      </span>
                    )}
                  </div>
                </div>
              </div>
              <CoachieButton
                onClick={() => createHabit(template)}
                disabled={creating === template.id}
                className="w-full mt-4"
              >
                {creating === template.id ? 'Creating...' : 'Add Habit'}
              </CoachieButton>
            </div>
          </CoachieCard>
        ))}
      </div>
    </div>
  )
}

