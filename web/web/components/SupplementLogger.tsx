'use client'

import { useState, useEffect } from 'react'
import { FirebaseService } from '../lib/services/firebase'
import type { HealthLog, Supplement } from '../types'

interface SupplementLoggerProps {
  onAddLog: (log: HealthLog) => Promise<void>
  userId: string
}

export default function SupplementLogger({ onAddLog, userId }: SupplementLoggerProps) {
  const [supplements, setSupplements] = useState<Supplement[]>([])
  const [selectedSupplement, setSelectedSupplement] = useState<Supplement | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    loadSupplements()
  }, [])

  const loadSupplements = async () => {
    try {
      const suppList = await FirebaseService.getSupplements()
      setSupplements(suppList)
    } catch (error) {
      console.error('Error loading supplements:', error)
    }
  }

  const handleSave = async () => {
    if (!selectedSupplement) return

    setSaving(true)
    try {
      const supplementLog: HealthLog = {
        id: `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        userId,
        type: 'supplement',
        timestamp: new Date(),
        supplementName: selectedSupplement.name,
        nutrients: selectedSupplement.nutrients,
      }

      await onAddLog(supplementLog)
      setSelectedSupplement(null)
    } catch (error) {
      console.error('Error saving supplement:', error)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div className="text-center">
        <h1 className="text-2xl font-bold text-gray-900 mb-2">💊 Log Supplement</h1>
        <p className="text-gray-600">Track your vitamin and supplement intake</p>
      </div>

      <div className="bg-white rounded-lg shadow-sm p-6">
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Select Supplement
            </label>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              {supplements.map((supplement) => (
                <button
                  key={supplement.id}
                  onClick={() => setSelectedSupplement(supplement)}
                  className={`p-4 border rounded-lg text-left hover:border-primary-300 transition-colors ${
                    selectedSupplement?.id === supplement.id
                      ? 'border-primary-500 bg-primary-50'
                      : 'border-gray-200'
                  }`}
                >
                  <h3 className="font-medium text-gray-900">{supplement.name}</h3>
                  <p className="text-sm text-gray-600 mt-1">
                    {Object.keys(supplement.nutrients).length} nutrients
                  </p>
                </button>
              ))}
            </div>
          </div>

          {selectedSupplement && (
            <div className="bg-gray-50 rounded-lg p-4">
              <h3 className="font-semibold text-gray-900 mb-2">
                {selectedSupplement.name}
              </h3>
              <div className="grid grid-cols-2 gap-2 text-sm">
                {Object.entries(selectedSupplement.nutrients).map(([nutrient, amount]) => (
                  <div key={nutrient} className="flex justify-between">
                    <span className="text-gray-600">{nutrient}:</span>
                    <span className="font-medium">{amount}mg</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          <button
            onClick={handleSave}
            disabled={saving || !selectedSupplement}
            className="w-full bg-primary-600 text-white px-6 py-3 rounded-md hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed font-medium"
          >
            {saving ? 'Saving...' : '💊 Log Supplement'}
          </button>
        </div>
      </div>
    </div>
  )
}
