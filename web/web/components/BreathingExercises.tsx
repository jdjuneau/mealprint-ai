'use client'

import { useState, useEffect } from 'react'
import { useAuth } from '../lib/contexts/AuthContext'
import CoachieCard from './ui/CoachieCard'
import CoachieButton from './ui/CoachieButton'

interface BreathingPattern {
  name: string
  description: string
  duration: number
  pattern: string
  icon: string
}

const patterns: BreathingPattern[] = [
  {
    name: 'Quick Calm',
    description: '1-minute fast breathing (3-2-3-2) for quick stress relief',
    duration: 60,
    pattern: '3-2-3-2',
    icon: '💨'
  },
  {
    name: 'Gentle Breathing',
    description: '3-minute gentle breathing (4-4-6-2) with longer exhales',
    duration: 180,
    pattern: '4-4-6-2',
    icon: '🌊'
  },
  {
    name: 'Deep Focus',
    description: '5-minute deep breathing (4-4-4-4) for focus and calm',
    duration: 300,
    pattern: '4-4-4-4',
    icon: '🎯'
  },
  {
    name: 'Box Breathing',
    description: 'Classic 4-4-4-4 technique for anxiety relief',
    duration: 300,
    pattern: '4-4-4-4',
    icon: '📦'
  }
]

export default function BreathingExercises() {
  const { user } = useAuth()
  const [selectedPattern, setSelectedPattern] = useState<BreathingPattern | null>(null)
  const [isActive, setIsActive] = useState(false)
  const [phase, setPhase] = useState<'inhale' | 'hold' | 'exhale' | 'hold2'>('inhale')
  const [timeRemaining, setTimeRemaining] = useState(0)
  const [totalTime, setTotalTime] = useState(0)

  const startExercise = (pattern: BreathingPattern) => {
    setSelectedPattern(pattern)
    setIsActive(true)
    setTotalTime(pattern.duration)
    setTimeRemaining(pattern.duration)
    setPhase('inhale')
  }

  useEffect(() => {
    if (!isActive || !selectedPattern) return

    const patternParts = selectedPattern.pattern.split('-').map(Number)
    const [inhale, hold1, exhale, hold2] = patternParts
    let currentCycle = 0
    let currentPhaseIndex = 0

    const phases = [
      { name: 'inhale' as const, duration: inhale },
      { name: 'hold' as const, duration: hold1 },
      { name: 'exhale' as const, duration: exhale },
      { name: 'hold2' as const, duration: hold2 }
    ]

    const interval = setInterval(() => {
      setTimeRemaining(prev => {
        if (prev <= 1) {
          setIsActive(false)
          return 0
        }
        return prev - 1
      })

      // Update phase based on cycle
      const totalCycleTime = inhale + hold1 + exhale + hold2
      const cycleProgress = (selectedPattern.duration - timeRemaining) % totalCycleTime
      
      let phaseTime = 0
      for (let i = 0; i < phases.length; i++) {
        phaseTime += phases[i].duration
        if (cycleProgress < phaseTime) {
          setPhase(phases[i].name)
          break
        }
      }
    }, 1000)

    return () => clearInterval(interval)
  }, [isActive, selectedPattern, timeRemaining])

  const getPhaseLabel = () => {
    switch (phase) {
      case 'inhale': return 'Breathe In'
      case 'exhale': return 'Breathe Out'
      case 'hold':
      case 'hold2': return 'Hold'
    }
  }

  const getPhaseColor = () => {
    switch (phase) {
      case 'inhale': return 'bg-blue-500'
      case 'exhale': return 'bg-green-500'
      case 'hold':
      case 'hold2': return 'bg-yellow-500'
    }
  }

  if (isActive && selectedPattern) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-500 to-purple-600">
        <div className="text-center text-white max-w-md mx-auto px-6">
          <div className="mb-8">
            <div className="text-8xl mb-6 animate-pulse">{selectedPattern.icon}</div>
            <div className="text-6xl font-bold mb-4">{getPhaseLabel()}</div>
            <div className="text-2xl mb-4">
              {Math.floor(timeRemaining / 60)}:{String(timeRemaining % 60).padStart(2, '0')}
            </div>
            <div className={`w-64 h-64 mx-auto rounded-full ${getPhaseColor()} transition-all duration-1000 flex items-center justify-center`}>
              <div className="text-6xl font-bold">{selectedPattern.pattern}</div>
            </div>
          </div>
          <CoachieButton onClick={() => setIsActive(false)} variant="outline">
            Stop
          </CoachieButton>
        </div>
      </div>
    )
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <h1 className="text-3xl font-bold text-gray-900">Breathing Exercises</h1>
      <p className="text-gray-600">
        Choose a breathing exercise to help you relax and focus
      </p>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {patterns.map((pattern) => (
          <CoachieCard key={pattern.name} onClick={() => startExercise(pattern)}>
            <div className="p-6">
              <div className="flex items-start gap-4">
                <div className="text-5xl">{pattern.icon}</div>
                <div className="flex-1">
                  <h3 className="text-xl font-bold mb-2">{pattern.name}</h3>
                  <p className="text-gray-600 mb-3">{pattern.description}</p>
                  <div className="flex items-center gap-2 text-sm text-gray-500">
                    <span>⏱️ {Math.floor(pattern.duration / 60)} min</span>
                    <span>•</span>
                    <span>🎯 {pattern.pattern}</span>
                  </div>
                </div>
              </div>
            </div>
          </CoachieCard>
        ))}
      </div>
    </div>
  )
}

