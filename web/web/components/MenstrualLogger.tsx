'use client'

import { useState } from 'react'
import CoachieCard from './ui/CoachieCard'
import CoachieButton from './ui/CoachieButton'

interface MenstrualLoggerProps {
  onAddLog: (log: any) => void
}

export default function MenstrualLogger({ onAddLog }: MenstrualLoggerProps) {
  const [isPeriodStart, setIsPeriodStart] = useState(false)
  const [isPeriodEnd, setIsPeriodEnd] = useState(false)
  const [flowIntensity, setFlowIntensity] = useState('')
  const [painLevel, setPainLevel] = useState<number>(1)
  const [symptoms, setSymptoms] = useState<string[]>([])
  const [notes, setNotes] = useState('')

  const symptomOptions = [
    'Cramps', 'Headache', 'Fatigue', 'Bloating', 'Mood swings',
    'Back pain', 'Nausea', 'Breast tenderness', 'Insomnia', 'Food cravings'
  ]

  const handleSymptomToggle = (symptom: string) => {
    setSymptoms(prev =>
      prev.includes(symptom)
        ? prev.filter(s => s !== symptom)
        : [...prev, symptom]
    )
  }

  const handleSubmit = () => {
    const menstrualLog = {
      id: `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      userId: 'current-user',
      type: 'menstrual',
      timestamp: new Date(),
      isPeriodStart,
      isPeriodEnd,
      flowIntensity: flowIntensity || undefined,
      painLevel,
      symptoms,
      notes: notes.trim() || undefined
    }

    onAddLog(menstrualLog)

    // Reset form
    setIsPeriodStart(false)
    setIsPeriodEnd(false)
    setFlowIntensity('')
    setPainLevel(1)
    setSymptoms([])
    setNotes('')
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="text-center">
        <h1 className="text-3xl font-bold text-gray-900 mb-2">Menstrual Tracking</h1>
        <p className="text-gray-600">Track your cycle and symptoms</p>
      </div>

      {/* Period Status */}
      <CoachieCard>
        <div className="p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Period Status</h2>

          <div className="space-y-4">
            <div className="flex items-center space-x-3">
              <input
                type="checkbox"
                id="periodStart"
                checked={isPeriodStart}
                onChange={(e) => setIsPeriodStart(e.target.checked)}
                className="w-5 h-5 text-primary-600 rounded focus:ring-primary-500"
              />
              <label htmlFor="periodStart" className="text-gray-700 font-medium">
                Period started today
              </label>
            </div>

            <div className="flex items-center space-x-3">
              <input
                type="checkbox"
                id="periodEnd"
                checked={isPeriodEnd}
                onChange={(e) => setIsPeriodEnd(e.target.checked)}
                className="w-5 h-5 text-primary-600 rounded focus:ring-primary-500"
              />
              <label htmlFor="periodEnd" className="text-gray-700 font-medium">
                Period ended today
              </label>
            </div>
          </div>
        </div>
      </CoachieCard>

      {/* Flow Intensity */}
      <CoachieCard>
        <div className="p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Flow Intensity</h2>

          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            {['Light', 'Medium', 'Heavy', 'Very Heavy'].map((intensity) => (
              <button
                key={intensity}
                onClick={() => setFlowIntensity(intensity)}
                className={`p-3 rounded-lg border-2 text-center transition-all ${
                  flowIntensity === intensity
                    ? 'border-primary-500 bg-primary-50 text-primary-700'
                    : 'border-gray-200 hover:border-gray-300 text-gray-700'
                }`}
              >
                {intensity}
              </button>
            ))}
          </div>
        </div>
      </CoachieCard>

      {/* Pain Level */}
      <CoachieCard>
        <div className="p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Pain Level</h2>

          <div className="space-y-2">
            <div className="flex justify-between text-sm text-gray-600">
              <span>No pain</span>
              <span>Severe pain</span>
            </div>
            <input
              type="range"
              min="1"
              max="5"
              value={painLevel}
              onChange={(e) => setPainLevel(Number(e.target.value))}
              className="w-full h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer"
            />
            <div className="text-center">
              <span className="text-2xl">
                {painLevel === 1 && '😊'}
                {painLevel === 2 && '🙂'}
                {painLevel === 3 && '😐'}
                {painLevel === 4 && '😟'}
                {painLevel === 5 && '😰'}
              </span>
              <div className="text-sm text-gray-600 mt-1">
                Level {painLevel} of 5
              </div>
            </div>
          </div>
        </div>
      </CoachieCard>

      {/* Symptoms */}
      <CoachieCard>
        <div className="p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Symptoms</h2>

          <div className="grid grid-cols-2 md:grid-cols-3 gap-2">
            {symptomOptions.map((symptom) => (
              <button
                key={symptom}
                onClick={() => handleSymptomToggle(symptom)}
                className={`p-2 rounded-lg text-sm text-center transition-all ${
                  symptoms.includes(symptom)
                    ? 'bg-primary-100 text-primary-800 border-2 border-primary-300'
                    : 'bg-gray-100 text-gray-700 border-2 border-gray-200 hover:border-gray-300'
                }`}
              >
                {symptom}
              </button>
            ))}
          </div>
        </div>
      </CoachieCard>

      {/* Notes */}
      <CoachieCard>
        <div className="p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Notes</h2>

          <textarea
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="Any additional notes about your cycle..."
            className="w-full p-3 border border-gray-300 rounded-lg resize-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
            rows={3}
          />
        </div>
      </CoachieCard>

      {/* Submit */}
      <div className="flex justify-center">
        <CoachieButton size="lg" onClick={handleSubmit}>
          Save Entry
        </CoachieButton>
      </div>
    </div>
  )
}
