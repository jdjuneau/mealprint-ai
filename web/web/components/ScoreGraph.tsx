'use client'

import { useEffect, useState } from 'react'
import { useAuth } from '../lib/contexts/AuthContext'
import { httpsCallable } from 'firebase/functions'
import { functions } from '../lib/firebase'

interface ScoreData {
  date: string
  score: number
  healthScore: number
  wellnessScore: number
  habitsScore: number
}

interface ScoreHistoryResponse {
  success: boolean
  scores: ScoreData[]
  trend: {
    direction: 'up' | 'down' | 'stable'
    change: number
    changePercent: number
    recentAverage: number
    previousAverage: number
    streak: number
    streakType: 'improving' | 'declining' | 'stable'
  }
  stats: {
    average: number
    highest: number
    lowest: number
    highestDate: string | null
    lowestDate: string | null
    last7DaysAverage: number | null
    last30DaysAverage: number | null
    consistency: number
  }
  daysRequested: number
  daysReturned: number
}

export default function ScoreGraph({ days = 30 }: { days?: number }) {
  const { user } = useAuth()
  const [data, setData] = useState<ScoreHistoryResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!user) return

    const fetchScoreHistory = async () => {
      try {
        setLoading(true)
        setError(null)
        const getScoreHistory = httpsCallable(functions, 'getScoreHistory')
        const result = await getScoreHistory({ days })
        const response = result.data as ScoreHistoryResponse
        setData(response)
      } catch (err: any) {
        console.error('Error fetching score history:', err)
        setError(err.message || 'Failed to load score history')
      } finally {
        setLoading(false)
      }
    }

    fetchScoreHistory()
  }, [user, days])

  if (loading) {
    return (
      <div className="bg-white rounded-lg shadow p-6">
        <div className="animate-pulse">
          <div className="h-6 bg-gray-200 rounded w-1/4 mb-4"></div>
          <div className="h-64 bg-gray-200 rounded"></div>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="bg-white rounded-lg shadow p-6">
        <p className="text-red-600">Error: {error}</p>
      </div>
    )
  }

  if (!data || data.scores.length === 0) {
    return (
      <div className="bg-white rounded-lg shadow p-6">
        <h2 className="text-xl font-bold mb-4">Coachie Score History</h2>
        <p className="text-gray-500">No score data available yet. Start logging your activities to see your progress!</p>
      </div>
    )
  }

  const { scores, trend, stats } = data

  // Prepare data for graph
  const maxScore = Math.max(...scores.map(s => s.score), 100)
  const minScore = Math.min(...scores.map(s => s.score), 0)
  const scoreRange = maxScore - minScore || 100

  // Get trend color and icon
  const trendColor = trend.direction === 'up' ? 'text-green-600' : trend.direction === 'down' ? 'text-red-600' : 'text-gray-600'
  const trendIcon = trend.direction === 'up' ? '📈' : trend.direction === 'down' ? '📉' : '➡️'
  const trendText = trend.direction === 'up' 
    ? `Up ${Math.abs(trend.changePercent).toFixed(1)}%` 
    : trend.direction === 'down' 
    ? `Down ${Math.abs(trend.changePercent).toFixed(1)}%`
    : 'Stable'

  return (
    <div className="bg-white rounded-lg shadow p-6">
      <div className="mb-6">
        <h2 className="text-2xl font-bold mb-2">Coachie Score History</h2>
        <div className="flex items-center gap-4 text-sm text-gray-600">
          <div className="flex items-center gap-2">
            <span className={trendColor}>{trendIcon}</span>
            <span className={trendColor}>{trendText}</span>
            <span className="text-gray-400">vs previous week</span>
          </div>
          {trend.streak > 0 && (
            <div className="flex items-center gap-1">
              <span>🔥</span>
              <span>{trend.streak} day {trend.streakType === 'improving' ? 'improvement' : 'decline'} streak</span>
            </div>
          )}
        </div>
      </div>

      {/* Simple line graph */}
      <div className="mb-6">
        <div className="relative h-64 bg-gradient-to-b from-blue-50 to-white rounded-lg p-4 border border-gray-200">
          <svg className="w-full h-full" viewBox="0 0 800 200" preserveAspectRatio="none">
            {/* Grid lines */}
            {[0, 25, 50, 75, 100].map((value) => (
              <g key={value}>
                <line
                  x1="0"
                  y1={200 - (value / 100) * 200}
                  x2="800"
                  y2={200 - (value / 100) * 200}
                  stroke="#e5e7eb"
                  strokeWidth="1"
                  strokeDasharray="4,4"
                />
                <text
                  x="0"
                  y={200 - (value / 100) * 200}
                  fill="#6b7280"
                  fontSize="12"
                  textAnchor="start"
                  dy="4"
                >
                  {value}
                </text>
              </g>
            ))}

            {/* Score line */}
            <polyline
              points={scores.map((score, index) => {
                const x = (index / (scores.length - 1 || 1)) * 800
                const y = 200 - ((score.score - minScore) / scoreRange) * 200
                return `${x},${y}`
              }).join(' ')}
              fill="none"
              stroke="#3b82f6"
              strokeWidth="3"
              strokeLinecap="round"
              strokeLinejoin="round"
            />

            {/* Data points */}
            {scores.map((score, index) => {
              const x = (index / (scores.length - 1 || 1)) * 800
              const y = 200 - ((score.score - minScore) / scoreRange) * 200
              return (
                <circle
                  key={index}
                  cx={x}
                  cy={y}
                  r="4"
                  fill="#3b82f6"
                  className="hover:r-6 transition-all"
                />
              )
            })}
          </svg>
        </div>
      </div>

      {/* Statistics */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="bg-blue-50 rounded-lg p-4">
          <div className="text-sm text-gray-600 mb-1">Average</div>
          <div className="text-2xl font-bold text-blue-600">{stats.average.toFixed(1)}</div>
        </div>
        <div className="bg-green-50 rounded-lg p-4">
          <div className="text-sm text-gray-600 mb-1">Highest</div>
          <div className="text-2xl font-bold text-green-600">{stats.highest}</div>
          {stats.highestDate && (
            <div className="text-xs text-gray-500 mt-1">{new Date(stats.highestDate).toLocaleDateString()}</div>
          )}
        </div>
        <div className="bg-orange-50 rounded-lg p-4">
          <div className="text-sm text-gray-600 mb-1">7-Day Avg</div>
          <div className="text-2xl font-bold text-orange-600">
            {stats.last7DaysAverage ? stats.last7DaysAverage.toFixed(1) : 'N/A'}
          </div>
        </div>
        <div className="bg-purple-50 rounded-lg p-4">
          <div className="text-sm text-gray-600 mb-1">30-Day Avg</div>
          <div className="text-2xl font-bold text-purple-600">
            {stats.last30DaysAverage ? stats.last30DaysAverage.toFixed(1) : 'N/A'}
          </div>
        </div>
      </div>

      {/* Trend details */}
      <div className="mt-6 p-4 bg-gray-50 rounded-lg">
        <div className="text-sm text-gray-600 mb-2">Recent Performance</div>
        <div className="flex items-center justify-between">
          <div>
            <div className="text-xs text-gray-500">Previous 7 days</div>
            <div className="text-lg font-semibold">{trend.previousAverage.toFixed(1)}</div>
          </div>
          <div className="text-2xl">→</div>
          <div>
            <div className="text-xs text-gray-500">Last 7 days</div>
            <div className={`text-lg font-semibold ${trendColor}`}>{trend.recentAverage.toFixed(1)}</div>
          </div>
          <div className="text-sm text-gray-500">
            {trend.change > 0 ? '+' : ''}{trend.change.toFixed(1)} points
          </div>
        </div>
      </div>
    </div>
  )
}

