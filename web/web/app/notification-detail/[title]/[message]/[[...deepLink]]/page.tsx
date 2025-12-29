'use client'

import { useRouter, useParams } from 'next/navigation'
import { useEffect, useState } from 'react'
import Link from 'next/link'

const DEEP_LINK_ROUTES: Record<string, string> = {
  habits: '/habits',
  meal_log: '/meal-log',
  water_log: '/water-log',
  sleep_log: '/sleep-log',
  workout_log: '/workout-log',
  journal_flow: '/journal-flow',
  set_goals: '/set-goals',
  weekly_blueprint: '/weekly-blueprint',
  health_tracking: '/health-tracking',
  ai_chat: '/ai-chat',
  coach_chat: '/ai-chat',
}

export default function NotificationDetailPage() {
  const router = useRouter()
  const params = useParams()
  const [title, setTitle] = useState('')
  const [message, setMessage] = useState('')
  const [deepLink, setDeepLink] = useState<string | null>(null)

  useEffect(() => {
    if (params.title && params.message) {
      setTitle(decodeURIComponent(params.title as string))
      setMessage(decodeURIComponent(params.message as string))
    }
    if (params.deepLink && Array.isArray(params.deepLink) && params.deepLink.length > 0) {
      setDeepLink(decodeURIComponent(params.deepLink[0] as string))
    }
  }, [params])

  const actionRoute = deepLink ? DEEP_LINK_ROUTES[deepLink] : null
  const actionText = deepLink
    ? {
        habits: 'View Habits',
        meal_log: 'Log Meal',
        water_log: 'Log Water',
        sleep_log: 'Log Sleep',
        workout_log: 'Log Workout',
        journal_flow: 'Open Journal',
        set_goals: 'View Goals',
        weekly_blueprint: 'View Blueprint',
        health_tracking: 'View Health Tracking',
        ai_chat: 'Chat with Coach',
        coach_chat: 'Chat with Coach',
      }[deepLink] || null
    : null

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-500 to-purple-600">
      <div className="max-w-2xl mx-auto py-8 px-4">
        <div className="bg-white rounded-lg shadow-lg p-6">
          <div className="flex items-center mb-6">
            <button
              onClick={() => router.back()}
              className="mr-4 text-gray-600 hover:text-gray-800"
            >
              ← Back
            </button>
            <h1 className="text-xl font-bold text-gray-900">Notification</h1>
          </div>

          <div className="space-y-4">
            <h2 className="text-2xl font-bold text-gray-900">{title}</h2>
            <p className="text-gray-700 leading-relaxed">{message}</p>

            {actionText && actionRoute && (
              <Link
                href={actionRoute}
                className="block w-full px-4 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 text-center font-medium"
              >
                {actionText} →
              </Link>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

