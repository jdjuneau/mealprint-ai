'use client'

import { useState } from 'react'
import { useAuth } from '../lib/contexts/AuthContext'
import CoachieCard from './ui/CoachieCard'
import CoachieButton from './ui/CoachieButton'

export default function BodyScan() {
  const { user } = useAuth()
  const [isActive, setIsActive] = useState(false)
  const [currentStep, setCurrentStep] = useState(0)

  const bodyParts = [
    { name: 'Head', instruction: 'Focus on your head. Notice any tension.' },
    { name: 'Neck', instruction: 'Move to your neck. Feel the muscles relaxing.' },
    { name: 'Shoulders', instruction: 'Pay attention to your shoulders. Release any tightness.' },
    { name: 'Chest', instruction: 'Notice your chest rising and falling with each breath.' },
    { name: 'Arms', instruction: 'Scan your arms. Feel them getting heavier and more relaxed.' },
    { name: 'Stomach', instruction: 'Focus on your stomach. Notice any sensations.' },
    { name: 'Hips', instruction: 'Move to your hips. Release any tension here.' },
    { name: 'Legs', instruction: 'Scan your legs. Feel them becoming relaxed.' },
    { name: 'Feet', instruction: 'Finally, focus on your feet. Feel them grounding you.' }
  ]

  const startScan = () => {
    setIsActive(true)
    setCurrentStep(0)
  }

  const nextStep = () => {
    if (currentStep < bodyParts.length - 1) {
      setCurrentStep(currentStep + 1)
    } else {
      setIsActive(false)
    }
  }

  const prevStep = () => {
    if (currentStep > 0) {
      setCurrentStep(currentStep - 1)
    }
  }

  if (isActive) {
    const part = bodyParts[currentStep]
    const progress = ((currentStep + 1) / bodyParts.length) * 100

    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-teal-500 to-cyan-600">
        <div className="text-center text-white max-w-md mx-auto px-6">
          <div className="mb-8">
            <div className="text-6xl mb-6 animate-pulse">👁️</div>
            <div className="text-4xl font-bold mb-4">{part.name}</div>
            <p className="text-xl mb-6">{part.instruction}</p>
            <div className="w-full bg-white/20 rounded-full h-2 mb-4">
              <div 
                className="bg-white h-2 rounded-full transition-all duration-500"
                style={{ width: `${progress}%` }}
              />
            </div>
            <p className="text-sm opacity-75">
              Step {currentStep + 1} of {bodyParts.length}
            </p>
          </div>
          <div className="flex gap-4 justify-center">
            {currentStep > 0 && (
              <CoachieButton onClick={prevStep} variant="outline">
                Previous
              </CoachieButton>
            )}
            <CoachieButton onClick={nextStep}>
              {currentStep < bodyParts.length - 1 ? 'Next' : 'Complete'}
            </CoachieButton>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <h1 className="text-3xl font-bold text-gray-900">Body Scan</h1>

      <CoachieCard>
        <div className="p-6">
          <div className="text-center mb-6">
            <div className="text-6xl mb-4">👁️</div>
            <h2 className="text-2xl font-bold mb-2">Progressive Body Scan</h2>
            <p className="text-gray-600">
              A mindfulness practice that helps you connect with your body and release tension
            </p>
          </div>

          <div className="space-y-3 mb-8">
            <div className="p-4 bg-gray-50 rounded-lg">
              <h3 className="font-semibold mb-2">What to expect:</h3>
              <ul className="text-sm text-gray-600 space-y-1">
                <li>• Systematic scan from head to toe</li>
                <li>• Focus on each body part for awareness</li>
                <li>• Release tension and stress</li>
                <li>• Takes about 10-15 minutes</li>
              </ul>
            </div>
          </div>

          <CoachieButton onClick={startScan} className="w-full">
            Start Body Scan
          </CoachieButton>
        </div>
      </CoachieCard>
    </div>
  )
}

