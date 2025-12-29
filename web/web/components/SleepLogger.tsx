'use client'

import { useState } from 'react'
import type { HealthLog } from '../types'

interface SleepLoggerProps {
  onAddLog: (log: HealthLog) => Promise<void>
}

export default function SleepLogger({ onAddLog }: SleepLoggerProps) {
  const [hours, setHours] = useState('')
  const [quality, setQuality] = useState<1 | 2 | 3 | 4 | 5>(3)
  const [notes, setNotes] = useState('')
  const [saving, setSaving] = useState(false)

  const handleSave = async () => {
    if (!hours) return

    setSaving(true)
    try {
      const sleepLog: HealthLog = {
        id: `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        userId: '', // Will be set by parent
        type: 'sleep',
        timestamp: new Date(),
        hours: parseFloat(hours),
        quality,
        notes: notes || undefined,
      }

      await onAddLog(sleepLog)

      // Reset form
      setHours('')
      setQuality(3)
      setNotes('')
    } catch (error) {
      console.error('Error saving sleep:', error)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div className="text-center">
        <h1 className="text-2xl font-bold text-gray-900 mb-2">😴 Log Sleep</h1>
        <p className="text-gray-600">Track your sleep quality and duration</p>
      </div>

      <div className="bg-white rounded-lg shadow-sm p-6 space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Hours of Sleep
          </label>
          <input
            type="number"
            step="0.5"
            value={hours}
            onChange={(e) => setHours(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
            placeholder="8.0"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">
            Sleep Quality
          </label>
          <div className="flex space-x-2">
            {[1, 2, 3, 4, 5].map((rating) => (
              <button
                key={rating}
                onClick={() => setQuality(rating as 1 | 2 | 3 | 4 | 5)}
                className={`px-4 py-2 rounded-md text-sm font-medium ${
                  quality === rating
                    ? 'bg-primary-600 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
              >
                {rating} {rating === 5 ? '⭐' : rating === 4 ? '😊' : rating === 3 ? '😐' : rating === 2 ? '😕' : '😞'}
              </button>
            ))}
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
            placeholder="How did you sleep?"
          />
        </div>

        <button
          onClick={handleSave}
          disabled={saving || !hours}
          className="w-full bg-primary-600 text-white px-6 py-3 rounded-md hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed font-medium"
        >
          {saving ? 'Saving...' : '😴 Save Sleep'}
        </button>
      </div>
    </div>
  )
}
