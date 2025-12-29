'use client'

import { useState } from 'react'
import type { HealthLog } from '../types'

interface WeightLoggerProps {
  onAddLog: (log: HealthLog) => Promise<void>
}

export default function WeightLogger({ onAddLog }: WeightLoggerProps) {
  const [weight, setWeight] = useState('')
  const [saving, setSaving] = useState(false)

  const handleSave = async () => {
    if (!weight) return

    setSaving(true)
    try {
      const weightLog: HealthLog = {
        id: `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        userId: '', // Will be set by parent
        type: 'weight',
        timestamp: new Date(),
        weight: parseFloat(weight),
      }

      await onAddLog(weightLog)
      setWeight('')
    } catch (error) {
      console.error('Error saving weight:', error)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div className="text-center">
        <h1 className="text-2xl font-bold text-gray-900 mb-2">⚖️ Log Weight</h1>
        <p className="text-gray-600">Track your weight progress over time</p>
      </div>

      <div className="bg-white rounded-lg shadow-sm p-6 space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Weight (kg)
          </label>
          <input
            type="number"
            step="0.1"
            value={weight}
            onChange={(e) => setWeight(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
            placeholder="70.5"
          />
          {weight && (
            <p className="text-sm text-gray-500 mt-1">
              ≈ {(parseFloat(weight) * 2.20462).toFixed(1)} lbs
            </p>
          )}
        </div>

        <div className="bg-gray-50 rounded-lg p-4">
          <h3 className="font-medium text-gray-900 mb-2">💡 Weight Tracking Tips</h3>
          <ul className="text-sm text-gray-600 space-y-1">
            <li>• Weigh yourself at the same time each day</li>
            <li>• Use the same scale for consistency</li>
            <li>• Track trends rather than daily fluctuations</li>
          </ul>
        </div>

        <button
          onClick={handleSave}
          disabled={saving || !weight}
          className="w-full bg-primary-600 text-white px-6 py-3 rounded-md hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed font-medium"
        >
          {saving ? 'Saving...' : '⚖️ Save Weight'}
        </button>
      </div>
    </div>
  )
}
