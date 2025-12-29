'use client'

import { useAuth } from '../../lib/contexts/AuthContext'
import { useEffect, useState } from 'react'
import { FirebaseService } from '../../lib/services/firebase'
import type { DailyLog } from '../../types'
import DashboardStats from '../../components/DashboardStats'
import LoadingScreen from '../../components/LoadingScreen'
import Navigation from '../../components/Navigation'
import { useRouter } from 'next/navigation'
import type { ActiveTab } from '../../types/navigation'

export default function HomePage() {
  const { user, userProfile } = useAuth()
  const router = useRouter()
  const [todayLog, setTodayLog] = useState<DailyLog | null>(null)
  const [loading, setLoading] = useState(true)
  const [activeTab, setActiveTab] = useState<ActiveTab>('dashboard')
  const today = new Date().toISOString().split('T')[0]

  useEffect(() => {
    if (user && userProfile) {
      loadTodayLog()
    } else if (!user) {
      router.push('/auth')
    }
  }, [user, userProfile])

  const loadTodayLog = async () => {
    if (!user) return
    try {
      const log = await FirebaseService.getDailyLog(user.uid, today)
      setTodayLog(log)
    } catch (error) {
      console.error('Error loading today\'s log:', error)
    } finally {
      setLoading(false)
    }
  }

  const generateDailyInsight = async (): Promise<string | null> => {
    if (!user || !userProfile) return null
    try {
      const { AIService } = await import('../../lib/services/ai')
      const recentLogs = await FirebaseService.getRecentDailyLogs(user.uid, 7)
      const insight = await AIService.generateDailyInsight(
        userProfile,
        recentLogs,
        userProfile.name
      )
      return insight
    } catch (error) {
      console.error('Error generating daily insight:', error)
      return null
    }
  }

  if (loading || !user || !userProfile) {
    return <LoadingScreen />
  }


  return (
    <div className={`min-h-screen ${
      userProfile.gender === 'male'
        ? 'bg-coachie-gradient-male'
        : userProfile.gender === 'female'
        ? 'bg-coachie-gradient-female'
        : 'bg-coachie-gradient'
    }`}>
      {/* Navigation Bar */}
      {user && (
        <Navigation
          activeTab={activeTab}
          onTabChange={setActiveTab}
          userName={userProfile?.name || user?.email || 'User'}
          userProfile={userProfile}
        />
      )}

      <main className="max-w-7xl mx-auto py-6 px-4 sm:px-6 lg:px-8">
        <div className="space-y-6 animate-fade-in">
          {/* Dashboard Stats - Contains all cards in correct order */}
          <DashboardStats
            userProfile={userProfile}
            todayLog={todayLog}
            onGenerateInsight={generateDailyInsight}
            onRefresh={loadTodayLog}
          />
        </div>
      </main>

      {/* Floating Microphone Button for Voice Logging */}
      <button
        onClick={() => router.push('/voice-logging')}
        className="fixed bottom-6 right-6 w-16 h-16 bg-blue-600 hover:bg-blue-700 text-white rounded-full shadow-lg flex items-center justify-center text-2xl z-50 transition-all hover:scale-110"
        title="Voice Logging"
      >
        🎤
      </button>
    </div>
  )
}
