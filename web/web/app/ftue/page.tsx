'use client'

import { useAuth } from '../../lib/contexts/AuthContext'
import { useRouter } from 'next/navigation'
import { useState, useEffect } from 'react'
import LoadingScreen from '../../components/LoadingScreen'
import toast from 'react-hot-toast'

export default function FTUEPage() {
  const { user, userProfile, updateUserProfile } = useAuth()
  const router = useRouter()
  const [loading, setLoading] = useState(true)
  const [currentStep, setCurrentStep] = useState(0)

  useEffect(() => {
    if (!user) {
      router.push('/auth')
    } else {
      setLoading(false)
    }
  }, [user, router])

  const steps = [
    {
      title: 'Welcome to Coachie!',
      description: 'Your AI-powered fitness coach is here to help you achieve your goals.',
      icon: '👋',
    },
    {
      title: 'Track Everything',
      description: 'Log meals, workouts, sleep, and more to get personalized insights.',
      icon: '📊',
    },
    {
      title: 'AI-Powered Insights',
      description: 'Get personalized recommendations and insights based on your data.',
      icon: '🤖',
    },
    {
      title: 'Build Healthy Habits',
      description: 'Create and track habits that help you reach your goals.',
      icon: '🔥',
    },
  ]

  const handleComplete = async () => {
    if (!user) return
    try {
      await updateUserProfile({ ftueCompleted: true })
      toast.success('Welcome to Coachie!')
      router.push('/home')
    } catch (error) {
      console.error('Error completing FTUE:', error)
      toast.error('Failed to complete onboarding')
    }
  }

  if (loading || !user) {
    return <LoadingScreen />
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center">
      <div className="max-w-md mx-auto px-4">
        <div className="bg-white rounded-lg shadow-lg p-8 text-center">
          <div className="text-6xl mb-6">{steps[currentStep].icon}</div>
          <h1 className="text-2xl font-bold text-gray-900 mb-4">{steps[currentStep].title}</h1>
          <p className="text-gray-600 mb-8">{steps[currentStep].description}</p>

          <div className="mb-6">
            <div className="flex justify-center gap-2">
              {steps.map((_, index) => (
                <div
                  key={index}
                  className={`w-2 h-2 rounded-full ${
                    index === currentStep ? 'bg-blue-600' : 'bg-gray-300'
                  }`}
                />
              ))}
            </div>
          </div>

          <div className="space-y-4">
            {currentStep < steps.length - 1 ? (
              <button
                onClick={() => setCurrentStep(currentStep + 1)}
                className="w-full px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
              >
                Next
              </button>
            ) : (
              <button
                onClick={handleComplete}
                className="w-full px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
              >
                Get Started
              </button>
            )}
            <button
              onClick={handleComplete}
              className="w-full px-6 py-2 text-gray-600 hover:text-gray-800"
            >
              Skip
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
