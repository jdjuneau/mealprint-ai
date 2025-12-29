'use client'

import { useState } from 'react'
import type { HealthLog } from '../types'

interface WorkoutLoggerProps {
  onAddLog: (log: HealthLog) => Promise<void>
}

export default function WorkoutLogger({ onAddLog }: WorkoutLoggerProps) {
  const [workoutType, setWorkoutType] = useState('')
  const [duration, setDuration] = useState('')
  const [calories, setCalories] = useState('')
  const [notes, setNotes] = useState('')
  const [saving, setSaving] = useState(false)

  const handleSave = async () => {
    if (!workoutType || !duration) return

    setSaving(true)
    try {
      const workoutLog: HealthLog = {
        id: `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        userId: '', // Will be set by parent
        type: 'workout',
        timestamp: new Date(),
        workoutType,
        duration: parseInt(duration),
        calories: parseInt(calories) || 0,
        notes: notes || undefined,
      }

      await onAddLog(workoutLog)

      // Reset form
      setWorkoutType('')
      setDuration('')
      setCalories('')
      setNotes('')
    } catch (error) {
      console.error('Error saving workout:', error)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div className="text-center">
        <h1 className="text-2xl font-bold text-gray-900 mb-2">🏋️ Log Workout</h1>
        <p className="text-gray-600">Track your exercise sessions</p>
      </div>

      <div className="bg-white rounded-lg shadow-sm p-6 space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Workout Type
          </label>
          <select
            value={workoutType}
            onChange={(e) => setWorkoutType(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
          >
            <option value="">Select workout type</option>
            <option value="running">Running</option>
            <option value="cycling">Cycling</option>
            <option value="swimming">Swimming</option>
            <option value="weightlifting">Weightlifting</option>
            <option value="yoga">Yoga</option>
            <option value="pilates">Pilates</option>
            <option value="other">Other</option>
          </select>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Duration (minutes)
            </label>
            <input
              type="number"
              value={duration}
              onChange={(e) => setDuration(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
              placeholder="30"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Calories Burned
            </label>
            <input
              type="number"
              value={calories}
              onChange={(e) => setCalories(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
              placeholder="200"
            />
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Notes (optional)
          </label>
          <textarea
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            rows={3}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
            placeholder="How did the workout feel?"
          />
        </div>

        <button
          onClick={handleSave}
          disabled={saving || !workoutType || !duration}
          className="w-full bg-primary-600 text-white px-6 py-3 rounded-md hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed font-medium"
        >
          {saving ? 'Saving...' : '💪 Save Workout'}
        </button>
      </div>
    </div>
  )
}
