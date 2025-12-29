'use client'

import { useRouter } from 'next/navigation'
import { useAuth } from '../../lib/contexts/AuthContext'
import { useEffect } from 'react'

export default function WelcomePage() {
  const router = useRouter()
  const { user } = useAuth()

  useEffect(() => {
    if (user) {
      router.push('/home')
    }
  }, [user, router])

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center">
      <div className="max-w-md mx-auto px-4 text-center text-white">
        <h1 className="text-4xl font-bold mb-4">Welcome to Coachie</h1>
        <p className="text-xl mb-8">Your AI-powered fitness coach</p>
        <div className="space-y-4">
          <button
            onClick={() => router.push('/auth')}
            className="w-full px-6 py-3 bg-white text-blue-600 rounded-lg font-semibold hover:bg-gray-100"
          >
            Get Started
          </button>
          <button
            onClick={() => router.push('/auth')}
            className="w-full px-6 py-3 border-2 border-white text-white rounded-lg font-semibold hover:bg-white/10"
          >
            Sign In
          </button>
        </div>
      </div>
    </div>
  )
}
