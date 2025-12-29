'use client'

import { useState, useEffect, useRef } from 'react'
import { useAuth } from '../lib/contexts/AuthContext'
import CoachieCard from './ui/CoachieCard'
import CoachieButton from './ui/CoachieButton'
import { ttsService } from '../lib/services/ttsService'

type MeditationState = 'idle' | 'preparing' | 'meditating' | 'completed'
type MeditationType = 'guided' | 'silent' | 'mindfulness' | 'body_scan'

export default function Meditation() {
  const { user, userProfile } = useAuth()
  const [state, setState] = useState<MeditationState>('idle')
  const [selectedDuration, setSelectedDuration] = useState(10)
  const [selectedType, setSelectedType] = useState<MeditationType>('guided')
  const [timeRemaining, setTimeRemaining] = useState(0)
  const [currentInstruction, setCurrentInstruction] = useState('')
  const instructionIntervalRef = useRef<NodeJS.Timeout | null>(null)
  const voiceEnabled = (userProfile as any)?.voiceSettings?.enabled !== false // Default to enabled

  useEffect(() => {
    let interval: NodeJS.Timeout
    if (state === 'meditating' && timeRemaining > 0) {
      interval = setInterval(() => {
        setTimeRemaining(prev => {
          if (prev <= 1) {
            setState('completed')
            return 0
          }
          return prev - 1
        })
        // Update instruction every 30 seconds
        if (timeRemaining % 30 === 0) {
          updateInstruction()
        }
      }, 1000)
    }
    return () => clearInterval(interval)
  }, [state, timeRemaining])

  const updateInstruction = () => {
    const instructions = [
      'Find a comfortable position...',
      'Close your eyes and take a deep breath...',
      'Focus on your breathing...',
      'Notice any thoughts without judgment...',
      'Return your attention to your breath...',
      'Feel your body relaxing...'
    ]
    setCurrentInstruction(instructions[Math.floor(Math.random() * instructions.length)])
  }

  const startMeditation = () => {
    setState('preparing')
    // Initial instruction
    const initialInstruction = 'Find a comfortable position. Close your eyes and take a deep breath.'
    if (selectedType === 'guided' && voiceEnabled && ttsService.isSupported()) {
      ttsService.speak(initialInstruction, 'mindfulness')
    }
    setTimeout(() => {
      setState('meditating')
      setTimeRemaining(selectedDuration * 60)
      updateInstruction()
      // Speak first instruction
      if (selectedType === 'guided' && voiceEnabled && ttsService.isSupported() && currentInstruction) {
        setTimeout(() => {
          ttsService.speak(currentInstruction, 'mindfulness')
        }, 1000)
      }
    }, 3000)
  }

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60)
    const secs = seconds % 60
    return `${mins}:${secs.toString().padStart(2, '0')}`
  }

  const progress = selectedDuration > 0 
    ? ((selectedDuration * 60 - timeRemaining) / (selectedDuration * 60)) * 100 
    : 0

  if (state === 'preparing') {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-purple-500 to-indigo-600">
        <div className="text-center text-white">
          <div className="text-6xl mb-4 animate-pulse">🧘</div>
          <h2 className="text-2xl font-bold mb-2">Get Ready</h2>
          <p className="text-lg">Find a comfortable place...</p>
        </div>
      </div>
    )
  }

  if (state === 'meditating') {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-purple-500 to-indigo-600">
        <div className="text-center text-white max-w-md mx-auto px-6">
          <div className="mb-8">
            <div className="text-8xl mb-6">🧘</div>
            <div className="text-6xl font-bold mb-4">{formatTime(timeRemaining)}</div>
            <div className="w-full bg-white/20 rounded-full h-2 mb-4">
              <div 
                className="bg-white h-2 rounded-full transition-all duration-1000"
                style={{ width: `${progress}%` }}
              />
            </div>
          </div>
          {currentInstruction && (
            <p className="text-xl mb-8">{currentInstruction}</p>
          )}
          <CoachieButton onClick={() => setState('completed')} variant="outline">
            Stop
          </CoachieButton>
        </div>
      </div>
    )
  }

  if (state === 'completed') {
    return (
      <div className="max-w-4xl mx-auto space-y-6">
        <CoachieCard>
          <div className="p-12 text-center">
            <div className="text-8xl mb-4">✨</div>
            <h2 className="text-3xl font-bold mb-4">Meditation Complete!</h2>
            <p className="text-gray-600 mb-6">
              Great job! You've completed {selectedDuration} minutes of {selectedType} meditation.
            </p>
            <div className="flex gap-4 justify-center">
              <CoachieButton onClick={() => setState('idle')}>
                New Session
              </CoachieButton>
            </div>
          </div>
        </CoachieCard>
      </div>
    )
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <h1 className="text-3xl font-bold text-gray-900">Meditation</h1>

      {/* Duration Selection */}
      <CoachieCard>
        <div className="p-6">
          <h2 className="text-xl font-bold mb-4">Duration</h2>
          <div className="grid grid-cols-4 gap-3">
            {[5, 10, 15, 20].map(duration => (
              <button
                key={duration}
                onClick={() => setSelectedDuration(duration)}
                className={`px-4 py-3 rounded-lg font-semibold transition-all ${
                  selectedDuration === duration
                    ? 'bg-primary-600 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
              >
                {duration} min
              </button>
            ))}
          </div>
        </div>
      </CoachieCard>

      {/* Type Selection */}
      <CoachieCard>
        <div className="p-6">
          <h2 className="text-xl font-bold mb-4">Type</h2>
          <div className="grid grid-cols-2 gap-3">
            {[
              { id: 'guided', label: 'Guided', icon: '🎧' },
              { id: 'silent', label: 'Silent', icon: '🤫' },
              { id: 'mindfulness', label: 'Mindfulness', icon: '🧘' },
              { id: 'body_scan', label: 'Body Scan', icon: '👁️' }
            ].map(type => (
              <button
                key={type.id}
                onClick={() => setSelectedType(type.id as MeditationType)}
                className={`px-4 py-4 rounded-lg font-semibold transition-all flex items-center justify-center gap-2 ${
                  selectedType === type.id
                    ? 'bg-primary-600 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
              >
                <span className="text-2xl">{type.icon}</span>
                {type.label}
              </button>
            ))}
          </div>
        </div>
      </CoachieCard>

      {/* Start Button */}
      <CoachieButton onClick={startMeditation} className="w-full">
        Start Meditation
      </CoachieButton>
    </div>
  )
}

