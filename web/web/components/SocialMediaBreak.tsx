'use client'

import { useState, useEffect } from 'react'
import { useAuth } from '../lib/contexts/AuthContext'
import CoachieCard from './ui/CoachieCard'
import CoachieButton from './ui/CoachieButton'

export default function SocialMediaBreak() {
  const { user } = useAuth()
  const [isRunning, setIsRunning] = useState(false)
  const [timeRemaining, setTimeRemaining] = useState(30 * 60) // 30 minutes default
  const [startTime, setStartTime] = useState<number | null>(null)

  useEffect(() => {
    // Load saved state from localStorage
    const savedStartTime = localStorage.getItem('social_break_start_time')
    const savedDuration = localStorage.getItem('social_break_duration')
    
    if (savedStartTime && savedDuration) {
      const elapsed = Math.floor((Date.now() - parseInt(savedStartTime)) / 1000)
      const remaining = parseInt(savedDuration) - elapsed
      if (remaining > 0) {
        setIsRunning(true)
        setStartTime(parseInt(savedStartTime))
        setTimeRemaining(remaining)
      } else {
        localStorage.removeItem('social_break_start_time')
        localStorage.removeItem('social_break_duration')
      }
    }
  }, [])

  useEffect(() => {
    if (!isRunning || !startTime) return

    const interval = setInterval(() => {
      const elapsed = Math.floor((Date.now() - startTime) / 1000)
      const remaining = (30 * 60) - elapsed
      
      if (remaining <= 0) {
        setIsRunning(false)
        setTimeRemaining(0)
        localStorage.removeItem('social_break_start_time')
        localStorage.removeItem('social_break_duration')
      } else {
        setTimeRemaining(remaining)
      }
    }, 1000)

    return () => clearInterval(interval)
  }, [isRunning, startTime])

  const startBreak = () => {
    const now = Date.now()
    setIsRunning(true)
    setStartTime(now)
    setTimeRemaining(30 * 60)
    localStorage.setItem('social_break_start_time', now.toString())
    localStorage.setItem('social_break_duration', (30 * 60).toString())
  }

  const stopBreak = () => {
    setIsRunning(false)
    setStartTime(null)
    localStorage.removeItem('social_break_start_time')
    localStorage.removeItem('social_break_duration')
  }

  const formatTime = (seconds: number) => {
    const hours = Math.floor(seconds / 3600)
    const minutes = Math.floor((seconds % 3600) / 60)
    const secs = seconds % 60
    if (hours > 0) {
      return `${hours}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
    }
    return `${minutes}:${secs.toString().padStart(2, '0')}`
  }

  const progress = (30 * 60 - timeRemaining) / (30 * 60) * 100

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <h1 className="text-3xl font-bold text-gray-900">Social Media Break</h1>

      <CoachieCard>
        <div className="p-12 text-center">
          <div className="text-8xl mb-6">📱</div>
          <h2 className="text-2xl font-bold mb-4">Take a Mindful Break</h2>
          <p className="text-gray-600 mb-8 max-w-md mx-auto">
            Take a mindful break from social media. Use this time to reconnect with yourself.
          </p>

          {isRunning ? (
            <>
              <div className="mb-8">
                <div className="text-6xl font-bold mb-4 text-primary-600">
                  {formatTime(timeRemaining)}
                </div>
                <div className="w-full max-w-md mx-auto bg-gray-200 rounded-full h-3 mb-4">
                  <div 
                    className="bg-primary-600 h-3 rounded-full transition-all duration-1000"
                    style={{ width: `${progress}%` }}
                  />
                </div>
                <p className="text-gray-600">Time remaining</p>
              </div>
              
              <div className="space-y-4 max-w-md mx-auto mb-8">
                <div className="p-4 bg-gray-50 rounded-lg text-left">
                  <h3 className="font-semibold mb-2">During Your Break</h3>
                  <ul className="text-sm text-gray-600 space-y-1">
                    <li>• Take a walk outside</li>
                    <li>• Read a book or article</li>
                    <li>• Practice meditation</li>
                    <li>• Do some light exercise</li>
                    <li>• Journal your thoughts</li>
                    <li>• Connect with nature</li>
                  </ul>
                </div>
              </div>

              <CoachieButton onClick={stopBreak} variant="outline">
                End Break
              </CoachieButton>
            </>
          ) : (
            <>
              <div className="mb-8">
                <div className="text-4xl font-bold text-primary-600 mb-2">30 minutes</div>
                <p className="text-gray-600">Recommended break duration</p>
              </div>
              <CoachieButton onClick={startBreak}>
                Start Break
              </CoachieButton>
            </>
          )}
        </div>
      </CoachieCard>
    </div>
  )
}

