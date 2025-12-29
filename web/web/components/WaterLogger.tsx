'use client'

import { useState } from 'react'
import type { HealthLog } from '../types'

interface WaterLoggerProps {
  onAddLog: (log: HealthLog) => Promise<void>
}

const commonAmounts = [250, 500, 750, 1000] // ml

export default function WaterLogger({ onAddLog }: WaterLoggerProps) {
  const [amount, setAmount] = useState('')
  const [saving, setSaving] = useState(false)

  const handleQuickAmount = (ml: number) => {
    setAmount(ml.toString())
  }

  const handleSave = async () => {
    if (!amount) return

    setSaving(true)
    try {
      const waterLog: HealthLog = {
        id: `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        userId: '', // Will be set by parent
        type: 'water',
        timestamp: new Date(),
        amount: parseInt(amount),
      }

      await onAddLog(waterLog)
      setAmount('')
    } catch (error) {
      console.error('Error saving water:', error)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div className="text-center">
        <h1 className="text-2xl font-bold text-gray-900 mb-2">💧 Log Water</h1>
        <p className="text-gray-600">Track your daily water intake</p>
      </div>

      <div className="bg-white rounded-lg shadow-sm p-6 space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">
            Quick Amounts
          </label>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            {commonAmounts.map((ml) => (
              <button
                key={ml}
                onClick={() => handleQuickAmount(ml)}
                className="p-3 border border-gray-200 rounded-lg hover:border-primary-300 hover:bg-primary-50 transition-colors"
              >
                <div className="text-lg font-semibold text-gray-900">{ml}ml</div>
                <div className="text-xs text-gray-500">
                  {(ml / 1000 * 33.814).toFixed(1)} oz
                </div>
              </button>
            ))}
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Custom Amount (ml)
          </label>
          <input
            type="number"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
            placeholder="Enter amount in ml"
          />
        </div>

        {amount && (
          <div className="bg-blue-50 rounded-lg p-3">
            <p className="text-blue-800">
              💧 Logging {amount}ml ({(parseInt(amount) / 1000 * 33.814).toFixed(1)} oz)
            </p>
          </div>
        )}

        <button
          onClick={handleSave}
          disabled={saving || !amount}
          className="w-full bg-primary-600 text-white px-6 py-3 rounded-md hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed font-medium"
        >
          {saving ? 'Saving...' : '💧 Save Water'}
        </button>
      </div>
    </div>
  )
}
