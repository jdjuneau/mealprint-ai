'use client'

import React from 'react'
import { useAuth } from '../../lib/contexts/AuthContext'
import { useRouter } from 'next/navigation'
import { useState, useEffect, useCallback } from 'react'
import LoadingScreen from '../../components/LoadingScreen'
import { FirebaseService } from '../../lib/services/firebase'
import type { DailyLog } from '../../types'

export default function TodaysLogDetailPage() {
  const { user, userProfile } = useAuth()
  const router = useRouter()
  const [loading, setLoading] = useState(true)
  const [log, setLog] = useState<DailyLog | null>(null)
  const useImperial = userProfile?.useImperial !== false

  const loadLog = useCallback(async () => {
    if (!user) return
    try {
      // Use local date to match Android app behavior (not UTC)
      const today = new Date().toLocaleDateString('en-CA') // YYYY-MM-DD format in local timezone
      const todayLog = await FirebaseService.getDailyLog(user.uid, today)
      setLog(todayLog)
    } catch (error) {
      console.error('Error loading log:', error)
    } finally {
      setLoading(false)
    }
  }, [user])

  useEffect(() => {
    if (!user) {
      router.push('/auth')
    } else {
      loadLog()
    }
  }, [user, router, loadLog])

  if (loading || !user) {
    return <LoadingScreen />
  }

  const meals = log?.logs?.filter((l) => l.type === 'meal') || []
  const workouts = log?.logs?.filter((l) => l.type === 'workout') || []
  const waterLogs = log?.logs?.filter((l) => l.type === 'water') || []
  const sleepLogs = log?.logs?.filter((l) => l.type === 'sleep') || []
  const weightLogs = log?.logs?.filter((l) => l.type === 'weight') || []
  const supplementLogs = log?.logs?.filter((l) => l.type === 'supplement') || []
  const moodLogs = log?.logs?.filter((l) => l.type === 'mood') || []

  const gradientClass = userProfile?.gender === 'male'
    ? 'bg-coachie-gradient-male'
    : userProfile?.gender === 'female'
    ? 'bg-coachie-gradient-female'
    : 'bg-coachie-gradient'

  return (
    <div className={`min-h-screen ${gradientClass}`}>
      <div className="max-w-4xl mx-auto py-8 px-4">
        <button
          onClick={() => router.back()}
          className="mb-4 text-white/90 hover:text-white flex items-center gap-2"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
          </svg>
          Back
        </button>
        <h1 className="text-2xl font-bold text-white mb-6">Today's Log</h1>

        <div className="space-y-6">
          {meals.length > 0 && (
            <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6">
              <h2 className="text-lg font-semibold text-white mb-4">Meals ({meals.length})</h2>
              <div className="space-y-2">
                {meals.map((meal: any, index) => (
                  <div key={index} className="flex justify-between text-sm">
                    <span className="text-white/90">{meal.foodName || 'Meal'}</span>
                    <span className="font-medium text-white">{meal.calories || 0} cal</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {workouts.length > 0 && (
            <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6">
              <h2 className="text-lg font-semibold text-white mb-4">Workouts ({workouts.length})</h2>
              <div className="space-y-2">
                {workouts.map((workout: any, index) => (
                  <div key={index} className="flex justify-between text-sm">
                    <span className="text-white/90">{workout.workoutType || 'Workout'}</span>
                    <span className="font-medium text-white">{workout.duration || 0} min</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {waterLogs.length > 0 && (
            <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6">
              <h2 className="text-lg font-semibold text-white mb-4">Water</h2>
              <div className="text-2xl font-bold text-blue-300">
                {(() => {
                  const totalMl = waterLogs.reduce((sum: number, log: any) => sum + (log.amount || 0), 0)
                  if (useImperial) {
                    const oz = totalMl / 29.5735
                    return `${oz.toFixed(1)} oz`
                  }
                  return `${totalMl} ml`
                })()}
              </div>
            </div>
          )}

          {sleepLogs.length > 0 && (
            <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6">
              <h2 className="text-lg font-semibold text-white mb-4">Sleep ({sleepLogs.length})</h2>
              <div className="space-y-2">
                {sleepLogs.map((sleep: any, index) => (
                  <div key={index} className="flex justify-between text-sm">
                    <span className="text-white/90">
                      {sleep.hours ? `${sleep.hours.toFixed(1)} hours` : 'Sleep logged'}
                    </span>
                    {sleep.startTime && sleep.endTime && (
                      <span className="text-white/70 text-xs">
                        {new Date(sleep.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} - 
                        {new Date(sleep.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {weightLogs.length > 0 && (
            <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6">
              <h2 className="text-lg font-semibold text-white mb-4">Weight ({weightLogs.length})</h2>
              <div className="space-y-2">
                {weightLogs.map((weight: any, index) => (
                  <div key={index} className="flex justify-between text-sm">
                    <span className="text-white/90">Weight logged</span>
                    <span className="font-medium text-white">
                      {useImperial 
                        ? `${((weight.weight || 0) * 2.205).toFixed(1)} lbs`
                        : `${(weight.weight || 0).toFixed(1)} kg`}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {supplementLogs.length > 0 && (
            <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6">
              <h2 className="text-lg font-semibold text-white mb-4">Supplements ({supplementLogs.length})</h2>
              <div className="space-y-2">
                {supplementLogs.map((supplement: any, index) => (
                  <div key={index} className="flex justify-between text-sm">
                    <span className="text-white/90">{supplement.name || 'Supplement'}</span>
                    {supplement.timestamp && (
                      <span className="text-white/70 text-xs">
                        {new Date(supplement.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {moodLogs.length > 0 && (
            <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6">
              <h2 className="text-lg font-semibold text-white mb-4">Mood ({moodLogs.length})</h2>
              <div className="space-y-2">
                {moodLogs.map((mood: any, index) => (
                  <div key={index} className="flex justify-between items-center text-sm">
                    <div className="flex items-center gap-2">
                      <span className="text-2xl">
                        {mood.mood === 5 && '😄'}
                        {mood.mood === 4 && '🙂'}
                        {mood.mood === 3 && '😐'}
                        {mood.mood === 2 && '😕'}
                        {mood.mood === 1 && '😢'}
                      </span>
                      <span className="text-white/90">{mood.moodText || 'Mood logged'}</span>
                    </div>
                    {mood.timestamp && (
                      <span className="text-white/70 text-xs">
                        {new Date(mood.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {(!log || log.logs.length === 0) && (
            <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-12 text-center">
              <p className="text-white/80">No logs for today</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
