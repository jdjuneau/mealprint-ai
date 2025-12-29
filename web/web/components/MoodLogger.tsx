'use client'

import { useState } from 'react'
import CoachieCard from './ui/CoachieCard'
import CoachieButton from './ui/CoachieButton'

interface MoodLoggerProps {
  onAddLog: (log: any) => void
}

const moodOptions = [
  { level: 1, emoji: '😢', label: 'Very Sad', color: 'bg-red-500' },
  { level: 2, emoji: '😞', label: 'Sad', color: 'bg-red-400' },
  { level: 3, emoji: '😐', label: 'Neutral', color: 'bg-yellow-500' },
  { level: 4, emoji: '🙂', label: 'Good', color: 'bg-green-400' },
  { level: 5, emoji: '😊', label: 'Great', color: 'bg-green-500' }
]

const emotionOptions = [
  'Happy', 'Sad', 'Anxious', 'Stressed', 'Excited', 'Tired', 'Motivated', 'Overwhelmed'
]

export default function MoodLogger({ onAddLog }: MoodLoggerProps) {
  const [selectedMood, setSelectedMood] = useState<number | null>(null)
  const [selectedEmotions, setSelectedEmotions] = useState<string[]>([])
  const [energyLevel, setEnergyLevel] = useState<number>(3)
  const [stressLevel, setStressLevel] = useState<number>(3)
  const [notes, setNotes] = useState('')

  const handleEmotionToggle = (emotion: string) => {
    setSelectedEmotions(prev =>
      prev.includes(emotion)
        ? prev.filter(e => e !== emotion)
        : [...prev, emotion]
    )
  }

  const handleSubmit = () => {
    if (!selectedMood) return

    const moodLog = {
      id: `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      userId: 'current-user',
      type: 'mood',
      timestamp: new Date(),
      level: selectedMood,
      emotions: selectedEmotions,
      energyLevel,
      stressLevel,
      notes: notes.trim() || undefined
    }

    onAddLog(moodLog)

    // Reset form
    setSelectedMood(null)
    setSelectedEmotions([])
    setEnergyLevel(3)
    setStressLevel(3)
    setNotes('')
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="text-center">
        <h1 className="text-3xl font-bold text-gray-900 mb-2">How are you feeling?</h1>
        <p className="text-gray-600">Track your mood and emotions</p>
      </div>

      {/* Mood Selection */}
      <CoachieCard>
        <div className="p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Select your mood</h2>

          <div className="grid grid-cols-5 gap-4">
            {moodOptions.map((mood) => (
              <button
                key={mood.level}
                onClick={() => setSelectedMood(mood.level)}
                className={`p-4 rounded-lg border-2 transition-all ${
                  selectedMood === mood.level
                    ? 'border-primary-500 bg-primary-50'
                    : 'border-gray-200 hover:border-gray-300'
                }`}
              >
                <div className="text-3xl mb-2">{mood.emoji}</div>
                <div className="text-sm font-medium">{mood.label}</div>
              </button>
            ))}
          </div>
        </div>
      </CoachieCard>

      {/* Emotions */}
      <CoachieCard>
        <div className="p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">What emotions are you feeling?</h2>

          <div className="flex flex-wrap gap-2">
            {emotionOptions.map((emotion) => (
              <button
                key={emotion}
                onClick={() => handleEmotionToggle(emotion)}
                className={`px-4 py-2 rounded-full text-sm font-medium transition-all ${
                  selectedEmotions.includes(emotion)
                    ? 'bg-primary-100 text-primary-800 border-2 border-primary-300'
                    : 'bg-gray-100 text-gray-700 border-2 border-gray-200 hover:border-gray-300'
                }`}
              >
                {emotion}
              </button>
            ))}
          </div>
        </div>
      </CoachieCard>

      {/* Energy & Stress */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <CoachieCard>
          <div className="p-6">
            <h3 className="text-lg font-semibold text-gray-900 mb-4">Energy Level</h3>

            <div className="space-y-2">
              <div className="flex justify-between text-sm text-gray-600">
                <span>Low</span>
                <span>High</span>
              </div>
              <input
                type="range"
                min="1"
                max="5"
                value={energyLevel}
                onChange={(e) => setEnergyLevel(Number(e.target.value))}
                className="w-full h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer"
              />
              <div className="text-center">
                <span className="text-2xl">
                  {energyLevel === 1 && '😴'}
                  {energyLevel === 2 && '🥱'}
                  {energyLevel === 3 && '😐'}
                  {energyLevel === 4 && '⚡'}
                  {energyLevel === 5 && '🔋'}
                </span>
              </div>
            </div>
          </div>
        </CoachieCard>

        <CoachieCard>
          <div className="p-6">
            <h3 className="text-lg font-semibold text-gray-900 mb-4">Stress Level</h3>

            <div className="space-y-2">
              <div className="flex justify-between text-sm text-gray-600">
                <span>Low</span>
                <span>High</span>
              </div>
              <input
                type="range"
                min="1"
                max="5"
                value={stressLevel}
                onChange={(e) => setStressLevel(Number(e.target.value))}
                className="w-full h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer"
              />
              <div className="text-center">
                <span className="text-2xl">
                  {stressLevel === 1 && '😌'}
                  {stressLevel === 2 && '🙂'}
                  {stressLevel === 3 && '😐'}
                  {stressLevel === 4 && '😟'}
                  {stressLevel === 5 && '😰'}
                </span>
              </div>
            </div>
          </div>
        </CoachieCard>
      </div>

      {/* Notes */}
      <CoachieCard>
        <div className="p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Notes (optional)</h2>

          <textarea
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="What's on your mind? Any specific triggers or thoughts..."
            className="w-full p-3 border border-gray-300 rounded-lg resize-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
            rows={4}
          />
        </div>
      </CoachieCard>

      {/* Submit */}
      <div className="flex justify-center">
        <CoachieButton
          size="lg"
          onClick={handleSubmit}
          disabled={!selectedMood}
        >
          Log My Mood
        </CoachieButton>
      </div>
    </div>
  )
}
