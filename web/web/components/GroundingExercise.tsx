'use client'

import { useState, useEffect } from 'react'
import { useAuth } from '../lib/contexts/AuthContext'
import CoachieCard from './ui/CoachieCard'
import CoachieButton from './ui/CoachieButton'

interface GroundingPhase {
  number: number
  name: string
  instruction: string
  items: string[]
}

const phases: GroundingPhase[] = [
  {
    number: 5,
    name: '5 Things You See',
    instruction: 'Look around and name 5 things you can see',
    items: []
  },
  {
    number: 4,
    name: '4 Things You Feel',
    instruction: 'Notice 4 things you can touch or feel',
    items: []
  },
  {
    number: 3,
    name: '3 Things You Hear',
    instruction: 'Listen for 3 things you can hear',
    items: []
  },
  {
    number: 2,
    name: '2 Things You Smell',
    instruction: 'Identify 2 things you can smell',
    items: []
  },
  {
    number: 1,
    name: '1 Thing You Taste',
    instruction: 'Name 1 thing you can taste',
    items: []
  }
]

export default function GroundingExercise() {
  const { user } = useAuth()
  const [currentPhase, setCurrentPhase] = useState(0)
  const [isComplete, setIsComplete] = useState(false)
  const [timeRemaining, setTimeRemaining] = useState(30)

  useEffect(() => {
    if (currentPhase >= phases.length) {
      setIsComplete(true)
      return
    }

    setTimeRemaining(30)
    const interval = setInterval(() => {
      setTimeRemaining(prev => {
        if (prev <= 1) {
          if (currentPhase < phases.length - 1) {
            setCurrentPhase(currentPhase + 1)
            return 30
          } else {
            setIsComplete(true)
            return 0
          }
        }
        return prev - 1
      })
    }, 1000)

    return () => clearInterval(interval)
  }, [currentPhase])

  const startExercise = () => {
    setCurrentPhase(0)
    setIsComplete(false)
    setTimeRemaining(30)
  }

  const nextPhase = () => {
    if (currentPhase < phases.length - 1) {
      setCurrentPhase(currentPhase + 1)
      setTimeRemaining(30)
    } else {
      setIsComplete(true)
    }
  }

  if (isComplete) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-teal-700 to-teal-900">
        <div className="text-center text-white max-w-md mx-auto px-6">
          <div className="text-8xl mb-6">✨</div>
          <h2 className="text-3xl font-bold mb-4">Exercise Complete!</h2>
          <p className="text-xl mb-8">
            You've completed the 5-4-3-2-1 grounding exercise. How do you feel?
          </p>
          <CoachieButton onClick={startExercise} variant="outline">
            Start Again
          </CoachieButton>
        </div>
      </div>
    )
  }

  const phase = phases[currentPhase]
  const progress = ((currentPhase + 1) / phases.length) * 100

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-teal-700 to-teal-900">
      <div className="text-center text-white max-w-md mx-auto px-6">
        <div className="mb-8">
          <div className="text-8xl mb-6 animate-pulse">🌿</div>
          <div className="text-5xl font-bold mb-4">{phase.number}</div>
          <h2 className="text-2xl font-bold mb-4">{phase.name}</h2>
          <p className="text-xl mb-6">{phase.instruction}</p>
          <div className="w-full bg-white/20 rounded-full h-2 mb-4">
            <div 
              className="bg-white h-2 rounded-full transition-all duration-500"
              style={{ width: `${progress}%` }}
            />
          </div>
          <p className="text-sm opacity-75 mb-4">
            {timeRemaining}s remaining • Phase {currentPhase + 1} of {phases.length}
          </p>
        </div>
        <CoachieButton onClick={nextPhase} variant="outline">
          Next Step
        </CoachieButton>
      </div>
    </div>
  )
}

