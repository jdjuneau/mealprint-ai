'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '../lib/contexts/AuthContext'
import { FirebaseService } from '../lib/services/firebase'
import { AIService } from '../lib/services/ai'
import type { DailyLog, HealthLog } from '../types'
import Navigation from './Navigation'
import DashboardStats from './DashboardStats'
import MealLogger from './MealLogger'
import WorkoutLogger from './WorkoutLogger'
import SupplementLogger from './SupplementLogger'
import SleepLogger from './SleepLogger'
import WaterLogger from './WaterLogger'
import WeightLogger from './WeightLogger'
import MoodLogger from './MoodLogger'
import MenstrualLogger from './MenstrualLogger'
import AIChat from './AIChat'
import SavedMeals from './SavedMeals'
import ProfileSettings from './ProfileSettings'
import HabitDashboard from './HabitDashboard'
import GoalsDashboard from './GoalsDashboard'
import Achievements from './Achievements'
import Community from './Community'
import Settings from './Settings'
import ChartsAnalytics from './ChartsAnalytics'
import Mindfulness from './Mindfulness'
import VoiceLogging from './VoiceLogging'
import HelpScreen from './HelpScreen'
import WeeklyBlueprint from './WeeklyBlueprint'
import JournalFlow from './JournalFlow'
import MyWins from './MyWins'
import Quests from './Quests'
import Insights from './Insights'
import Meditation from './Meditation'
import BreathingExercises from './BreathingExercises'
import SocialMediaBreak from './SocialMediaBreak'
import BodyScan from './BodyScan'
import GroundingExercise from './GroundingExercise'
import HabitTemplates from './HabitTemplates'
import HabitSuggestions from './HabitSuggestions'
import HabitIntelligence from './HabitIntelligence'
import CircleDetail from './CircleDetail'
import FriendsList from './FriendsList'
import ForumDetail from './ForumDetail'
import RecipeCapture from './RecipeCapture'
import type { ActiveTab } from '../types/navigation'

export default function Dashboard() {
  const { user, userProfile } = useAuth()
  const router = useRouter()
  const [activeTab, setActiveTab] = useState<string>('dashboard')
  const [todayLog, setTodayLog] = useState<DailyLog | null>(null)
  const [loading, setLoading] = useState(true)

  // Get today's date in YYYY-MM-DD format
  const today = new Date().toISOString().split('T')[0]

  useEffect(() => {
    if (user) {
      loadTodayLog()
    }
  }, [user])

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

  const addHealthLog = async (log: HealthLog) => {
    if (!user) return

    try {
      await FirebaseService.saveHealthLog(user.uid, today, log)
      
      // Auto-complete related habits (like Android)
      try {
        const HabitAutoCompletionService = (await import('../lib/services/habitAutoCompletionService')).default
        const habitAutoService = HabitAutoCompletionService.getInstance()
        
        if (log.type === 'meal') {
          await habitAutoService.onMealLogged(user.uid)
        } else if (log.type === 'workout') {
          await habitAutoService.onWorkoutLogged(user.uid)
        } else if (log.type === 'water') {
          const waterAmount = (log as any).amount || 0
          await habitAutoService.onWaterLogged(user.uid, waterAmount)
        } else if (log.type === 'sleep') {
          const sleepHours = (log as any).hours || 0
          await habitAutoService.onSleepLogged(user.uid, sleepHours)
        }
      } catch (autoCompleteError) {
        console.warn('Error auto-completing habits (non-critical):', autoCompleteError)
      }
      
      await loadTodayLog() // Refresh the data
    } catch (error) {
      console.error('Error adding health log:', error)
      throw error
    }
  }

  const generateDailyInsight = async () => {
    if (!user || !userProfile) return null

    try {
      // Get recent logs for the past 7 days
      const recentLogs = []
      for (let i = 0; i < 7; i++) {
        const date = new Date()
        date.setDate(date.getDate() - i)
        const dateStr = date.toISOString().split('T')[0]

        const log = await FirebaseService.getDailyLog(user.uid, dateStr)
        if (log) {
          recentLogs.push(log)
        }
      }

      return await AIService.generateDailyInsight(
        userProfile,
        recentLogs,
        userProfile.name
      )
    } catch (error) {
      console.error('Error generating daily insight:', error)
      return null
    }
  }

  if (!user || !userProfile) {
    return (
      <div className="min-h-screen bg-coachie-gradient flex items-center justify-center">
        <div className="animate-fade-in text-center">
          <div className="text-6xl mb-4">🏋️</div>
          <h1 className="text-2xl font-bold text-white mb-2">Welcome to Coachie</h1>
          <p className="text-white/80">Your AI-powered health coach</p>
        </div>
      </div>
    )
  }

  const renderActiveTab = () => {
    switch (activeTab) {
      case 'dashboard':
        return (
          <DashboardStats
            userProfile={userProfile}
            todayLog={todayLog}
            onGenerateInsight={generateDailyInsight}
            onRefresh={loadTodayLog}
          />
        )
      case 'habits':
        return <HabitDashboard userId={user.uid} userProfile={userProfile} />
      case 'goals':
        return <GoalsDashboard userId={user.uid} userProfile={userProfile} />
      case 'mindfulness':
        return (
          <Mindfulness 
            userId={user.uid} 
            onNavigate={(tab) => {
              setActiveTab(tab as ActiveTab)
            }} 
          />
        )
      case 'charts':
        return <ChartsAnalytics userId={user.uid} userProfile={userProfile} />
      case 'meal':
        return (
          <MealLogger
            onAddLog={addHealthLog}
            userId={user.uid}
            onNavigateToRecipeCapture={() => setActiveTab('recipe-capture')}
          />
        )
      case 'workout':
        return (
          <WorkoutLogger
            onAddLog={addHealthLog}
          />
        )
      case 'supplement':
        return (
          <SupplementLogger
            onAddLog={addHealthLog}
            userId={user.uid}
          />
        )
      case 'sleep':
        return (
          <SleepLogger
            onAddLog={addHealthLog}
          />
        )
      case 'water':
        return (
          <WaterLogger
            onAddLog={addHealthLog}
          />
        )
      case 'weight':
        return (
          <WeightLogger
            onAddLog={addHealthLog}
          />
        )
      case 'mood':
        return (
          <MoodLogger
            onAddLog={addHealthLog}
          />
        )
      case 'chat':
        return (
          <AIChat
            userId={user.uid}
            userName={userProfile.name}
          />
        )
      case 'saved-meals':
        return (
          <SavedMeals
            userId={user.uid}
            onSelectMeal={(savedMeal) => {
              // Convert saved meal to meal log and add it
              const mealLog: HealthLog = {
                id: `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
                userId: user.uid,
                type: 'meal',
                timestamp: new Date(),
                foodName: savedMeal.foodName,
                calories: savedMeal.calories,
                protein: savedMeal.proteinG,
                carbs: savedMeal.carbsG,
                fat: savedMeal.fatG,
              }
              addHealthLog(mealLog)
            }}
          />
        )
      case 'achievements':
        return <Achievements userId={user.uid} />
      case 'community':
        return <Community userId={user.uid} userProfile={userProfile} />
      case 'voice':
        return <VoiceLogging userId={user.uid} />
      case 'profile':
        return (
          <ProfileSettings
            userProfile={userProfile}
            onUpdateProfile={() => {
              // Profile will be updated via AuthContext
              window.location.reload() // Simple refresh for now
            }}
          />
        )
      case 'settings':
        return <Settings userId={user.uid} userProfile={userProfile} />
      case 'preferences-edit':
        router.push('/preferences-edit')
        return null
      case 'dietary-preferences-edit':
        router.push('/dietary-preferences-edit')
        return null
      case 'help':
        return <HelpScreen />
      case 'weekly-blueprint':
        return <WeeklyBlueprint />
      case 'journal':
        return <JournalFlow />
      case 'my-wins':
        return <MyWins />
      case 'quests':
        return <Quests />
      case 'insights':
        return <Insights />
      case 'meditation':
        return <Meditation />
      case 'breathing':
        return <BreathingExercises />
      case 'social-media-break':
        return <SocialMediaBreak />
      case 'body-scan':
        return <BodyScan />
      case 'grounding':
        return <GroundingExercise />
      case 'habit-templates':
        return <HabitTemplates />
      case 'habit-suggestions':
        return <HabitSuggestions />
      case 'habit-intelligence':
        return <HabitIntelligence />
      case 'circle-detail':
        // TODO: Get circleId from route
        return <CircleDetail circleId={''} />
      case 'friends':
        return <FriendsList />
      case 'forum-detail':
        // TODO: Get postId from route
        return <ForumDetail postId={''} />
      case 'recipe-capture':
        return (
          <RecipeCapture
            userId={user.uid}
            onRecipeSaved={() => {
              setActiveTab('saved-meals')
            }}
            onBack={() => setActiveTab('meal')}
          />
        )
      default:
        return (
          <div className="text-center py-12 animate-fade-in">
            <div className="text-6xl mb-4">🚧</div>
            <h2 className="text-2xl font-bold text-gray-900 mb-2">Coming Soon</h2>
            <p className="text-gray-600">This feature is being developed and will be available soon!</p>
          </div>
        )
    }
  }

  console.log('🔵 Dashboard rendering with Navigation', { 
    hasUser: !!user, 
    hasUserProfile: !!userProfile, 
    userName: userProfile?.name || user?.email 
  })

  return (
    <div className={`min-h-screen ${
      userProfile?.gender === 'male'
        ? 'bg-coachie-gradient-male'
        : userProfile?.gender === 'female'
        ? 'bg-coachie-gradient-female'
        : 'bg-coachie-gradient'
    }`}>
      {/* Navigation Bar - Always render if we have user */}
      {user && (
        <Navigation
          activeTab={activeTab as ActiveTab}
          onTabChange={setActiveTab}
          userName={userProfile?.name || user?.email || 'User'}
          userProfile={userProfile}
        />
      )}

      <main className="max-w-7xl mx-auto py-6 px-4 sm:px-6 lg:px-8">
        <div className="animate-fade-in">
          {renderActiveTab()}
        </div>
      </main>
    </div>
  )
}
