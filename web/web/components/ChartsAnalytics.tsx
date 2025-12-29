'use client'

import { useState, useEffect } from 'react'
import { FirebaseService } from '../lib/services/firebase'
import CoachieCard from './ui/CoachieCard'
import CoachieButton from './ui/CoachieButton'
import type { UserProfile } from '../types'

interface ChartsAnalyticsProps {
  userId: string
  userProfile: UserProfile
}

export default function ChartsAnalytics({ userId, userProfile }: ChartsAnalyticsProps) {
  const [selectedPeriod, setSelectedPeriod] = useState<'7d' | '30d' | '90d'>('30d')
  const [chartData, setChartData] = useState<any>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    loadChartData()
  }, [userId, selectedPeriod])

  const loadChartData = async () => {
    try {
      // Mock chart data - in real app this would come from Firebase
      const mockData = {
        weight: [
          { date: '2024-01-01', value: 180 },
          { date: '2024-01-08', value: 178 },
          { date: '2024-01-15', value: 176 },
          { date: '2024-01-22', value: 175 },
        ],
        calories: [
          { date: '2024-01-01', consumed: 2100, burned: 400 },
          { date: '2024-01-02', consumed: 1950, burned: 450 },
          { date: '2024-01-03', consumed: 2200, burned: 380 },
          { date: '2024-01-04', consumed: 1800, burned: 500 },
        ],
        steps: [
          { date: '2024-01-01', value: 8500 },
          { date: '2024-01-02', value: 9200 },
          { date: '2024-01-03', value: 7800 },
          { date: '2024-01-04', value: 10500 },
        ],
        sleep: [
          { date: '2024-01-01', hours: 7.5 },
          { date: '2024-01-02', hours: 8.2 },
          { date: '2024-01-03', hours: 6.8 },
          { date: '2024-01-04', hours: 7.9 },
        ]
      }

      setChartData(mockData)
    } catch (error) {
      console.error('Error loading chart data:', error)
    } finally {
      setLoading(false)
    }
  }

  const gradientClass = userProfile?.gender === 'male'
    ? 'bg-coachie-gradient-male'
    : userProfile?.gender === 'female'
    ? 'bg-coachie-gradient-female'
    : 'bg-coachie-gradient'

  if (loading) {
    return (
      <div className={`min-h-screen ${gradientClass} flex items-center justify-center`}>
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-white"></div>
      </div>
    )
  }

  return (
    <div className={`min-h-screen ${gradientClass} py-8 px-4`}>
      <div className="max-w-6xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold text-white">Charts & Trends</h1>
          <p className="text-white/80 mt-1">Daily, Weekly, and Monthly views of your health data</p>
        </div>

        <div className="flex space-x-2">
          {[
            { key: '7d' as const, label: '7 Days' },
            { key: '30d' as const, label: '30 Days' },
            { key: '90d' as const, label: '90 Days' }
          ].map((period) => (
            <CoachieButton
              key={period.key}
              variant={selectedPeriod === period.key ? 'primary' : 'outline'}
              size="sm"
              onClick={() => setSelectedPeriod(period.key)}
            >
              {period.label}
            </CoachieButton>
          ))}
        </div>
      </div>

      {/* Key Metrics */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6 text-center">
            <div className="text-2xl mb-2">⚖️</div>
          <div className="text-3xl font-bold text-white mb-1">-5 lbs</div>
          <div className="text-sm text-white/70">Weight Change</div>
          </div>

        <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6 text-center">
            <div className="text-2xl mb-2">🔥</div>
          <div className="text-3xl font-bold text-white mb-1">12,450</div>
          <div className="text-sm text-white/70">Avg Daily Calories</div>
          </div>

        <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6 text-center">
            <div className="text-2xl mb-2">👣</div>
          <div className="text-3xl font-bold text-white mb-1">9,200</div>
          <div className="text-sm text-white/70">Avg Daily Steps</div>
          </div>

        <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6 text-center">
            <div className="text-2xl mb-2">😴</div>
          <div className="text-3xl font-bold text-white mb-1">7.6h</div>
          <div className="text-sm text-white/70">Avg Sleep Hours</div>
          </div>
      </div>

      {/* Weight Progress Chart */}
      <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6">
        <h2 className="text-xl font-semibold text-white mb-4">Weight Progress</h2>

          {/* Simple bar chart representation */}
          <div className="space-y-4">
            {chartData?.weight?.map((point: any, index: number) => (
              <div key={index} className="flex items-center space-x-4">
                <div className="w-20 text-sm text-white/70">
                  {new Date(point.date).toLocaleDateString()}
                </div>
                <div className="flex-1">
                  <div className="w-full bg-white/20 rounded-full h-4">
                    <div
                      className="bg-primary-500 h-4 rounded-full"
                      style={{
                        width: `${(point.value / 200) * 100}%` // Mock scaling
                      }}
                    ></div>
                  </div>
                </div>
                <div className="w-16 text-right font-medium text-white">
                  {point.value} lbs
                </div>
              </div>
            ))}
          </div>
        </div>

      {/* Calorie Balance */}
      <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6">
        <h2 className="text-xl font-semibold text-white mb-4">Calorie Balance</h2>

          <div className="space-y-4">
            {chartData?.calories?.map((day: any, index: number) => {
              const net = day.consumed - day.burned
              return (
                <div key={index} className="flex items-center space-x-4">
                  <div className="w-20 text-sm text-white/70">
                    {new Date(day.date).toLocaleDateString()}
                  </div>
                  <div className="flex-1 flex space-x-2">
                    <div className="flex-1">
                      <div className="text-xs text-white/70 mb-1">Consumed</div>
                      <div className="w-full bg-white/20 rounded-full h-3">
                        <div
                          className="bg-red-500 h-3 rounded-full"
                          style={{ width: `${(day.consumed / 2500) * 100}%` }}
                        ></div>
                      </div>
                    </div>
                    <div className="flex-1">
                      <div className="text-xs text-white/70 mb-1">Burned</div>
                      <div className="w-full bg-white/20 rounded-full h-3">
                        <div
                          className="bg-green-500 h-3 rounded-full"
                          style={{ width: `${(day.burned / 600) * 100}%` }}
                        ></div>
                      </div>
                    </div>
                  </div>
                  <div className="w-20 text-right">
                    <div className={`font-medium ${net > 0 ? 'text-red-300' : 'text-green-300'}`}>
                      {net > 0 ? '+' : ''}{net}
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        </div>

      {/* Activity Summary */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6">
          <h3 className="text-lg font-semibold text-white mb-4">Steps Trend</h3>
            <div className="space-y-3">
              {chartData?.steps?.map((day: any, index: number) => (
                <div key={index} className="flex justify-between items-center">
                <span className="text-sm text-white/70">
                    {new Date(day.date).toLocaleDateString('en-US', { weekday: 'short' })}
                  </span>
                  <div className="flex items-center space-x-2">
                  <div className="w-20 bg-white/20 rounded-full h-2">
                      <div
                        className="bg-green-500 h-2 rounded-full"
                        style={{ width: `${(day.value / 12000) * 100}%` }}
                      ></div>
                    </div>
                  <span className="text-sm font-medium w-16 text-right text-white">
                      {day.value.toLocaleString()}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </div>

        <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6">
          <h3 className="text-lg font-semibold text-white mb-4">Sleep Quality</h3>
            <div className="space-y-3">
              {chartData?.sleep?.map((day: any, index: number) => (
                <div key={index} className="flex justify-between items-center">
                <span className="text-sm text-white/70">
                    {new Date(day.date).toLocaleDateString('en-US', { weekday: 'short' })}
                  </span>
                  <div className="flex items-center space-x-2">
                  <div className="w-20 bg-white/20 rounded-full h-2">
                      <div
                        className={`h-2 rounded-full ${
                          day.hours >= 8 ? 'bg-green-500' :
                          day.hours >= 7 ? 'bg-yellow-500' : 'bg-red-500'
                        }`}
                        style={{ width: `${(day.hours / 10) * 100}%` }}
                      ></div>
                    </div>
                  <span className="text-sm font-medium w-12 text-right text-white">
                      {day.hours}h
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </div>
            </div>
          </div>
    </div>
  )
}
