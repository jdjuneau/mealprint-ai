'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import type { UserProfile, DailyLog, HealthLog } from '../types'
import { DailyScoreCalculator } from '../lib/services/dailyScoreCalculator'
import { CoachieScoreService } from '../lib/services/coachieScoreService'
import { useAuth } from '../lib/contexts/AuthContext'
import CirclesService from '../lib/services/circlesService'
import StreakService from '../lib/services/streakService'
import { FirebaseService } from '../lib/services/firebase'
import { collection, query, where, getDocs, orderBy, limit } from 'firebase/firestore'
import { db } from '../lib/firebase'

// Import all card components
import EnergyScoreCard from './dashboard/EnergyScoreCard'
import CirclePulseCard from './dashboard/CirclePulseCard'
import WinOfTheDayCard from './dashboard/WinOfTheDayCard'
import WeeklyBlueprintCard from './dashboard/WeeklyBlueprintCard'
import TodaysResetCard from './dashboard/TodaysResetCard'
import MorningBriefInsightCard from './dashboard/MorningBriefInsightCard'
import StreakBadgeCard from './dashboard/StreakBadgeCard'
import ProgressRingCard from './dashboard/ProgressRingCard'
import TodaysLogCard from './dashboard/TodaysLogCard'
import QuickLogButtonsCard from './dashboard/QuickLogButtonsCard'
import NavigationTileCard from './dashboard/NavigationTileCard'

interface DashboardStatsProps {
  userProfile: UserProfile
  todayLog: DailyLog | null
  onGenerateInsight: () => Promise<string | null>
  onRefresh: () => void
}

export default function DashboardStats({
  userProfile,
  todayLog,
  onGenerateInsight,
  onRefresh
}: DashboardStatsProps) {
  const { user } = useAuth()
  const router = useRouter()
  
  // State for all dashboard data
  const [coachieScore, setCoachieScore] = useState<number | null>(null)
  const [categoryScores, setCategoryScores] = useState<{ health: number; wellness: number; habits: number } | null>(null)
  const [userCircles, setUserCircles] = useState<any[]>([])
  const [winOfTheDay, setWinOfTheDay] = useState<string | null>(null)
  const [hasNotifications, setHasNotifications] = useState(false)
  const [streak, setStreak] = useState<any>(null)
  const [habits, setHabits] = useState<any[]>([])
  const [completedHabits, setCompletedHabits] = useState(0)
  const [weeklyBlueprint, setWeeklyBlueprint] = useState<Record<string, any> | null>(null)
  const [generatingBlueprint, setGeneratingBlueprint] = useState(false)
  const [morningBrief, setMorningBrief] = useState<string>('')
  const [isSpeaking, setIsSpeaking] = useState(false)
  const [dailyInsight, setDailyInsight] = useState<string | null>(null)
  const [generatingInsight, setGeneratingInsight] = useState(false)

  const today = new Date().toISOString().split('T')[0]
  const circlesService = CirclesService.getInstance()
  const streakService = StreakService.getInstance()

  // Calculate today's stats
  const meals = (todayLog?.logs || []).filter(log => log.type === 'meal') as HealthLog[]
  const workouts = (todayLog?.logs || []).filter(log => log.type === 'workout') as HealthLog[]
  const sleepLogs = (todayLog?.logs || []).filter(log => log.type === 'sleep') as HealthLog[]
  const waterLogs = (todayLog?.logs || []).filter(log => log.type === 'water') as HealthLog[]
  const weightLogs = (todayLog?.logs || []).filter(log => log.type === 'weight') as HealthLog[]
  const allHealthLogs = todayLog?.logs || []

  const caloriesConsumed = meals.reduce((sum, log: any) => sum + (log.calories || 0), 0)
  const caloriesBurned = workouts.reduce((sum, log: any) => sum + (log.calories || 0), 0)
  const calorieGoal = userProfile.estimatedDailyCalories || 2000
  // Water: Use waterAmount from daily log (already includes all water logs), don't double count
  // If waterAmount exists, use it; otherwise sum individual water logs
  const waterMl = todayLog?.waterAmount || waterLogs.reduce((sum, log: any) => sum + (log.amount || 0), 0)

  // Load circles and notifications (refresh every 30 seconds)
  useEffect(() => {
    if (!user) return
    const loadCircles = async () => {
      try {
        const circles = await circlesService.getUserCircles(user.uid)
        setUserCircles(circles)
        
        // Check for notifications (posts from last 24 hours)
        const oneDayAgo = Date.now() - (24 * 60 * 60 * 1000)
        let totalNewPosts = 0
        for (const circle of circles) {
          if (circle.id) {
            const postsRef = collection(db, 'circles', circle.id, 'posts')
            const postsQuery = query(postsRef, orderBy('createdAt', 'desc'), limit(50))
            const postsSnap = await getDocs(postsQuery)
            postsSnap.forEach((doc) => {
              const post = doc.data()
              const postTime = post.createdAt?.toMillis?.() || post.createdAt || 0
              if (postTime > oneDayAgo && post.authorId !== user.uid) {
                totalNewPosts++
              }
            })
          }
        }
        setHasNotifications(totalNewPosts > 0)
      } catch (error) {
        console.error('Error loading circles:', error)
      }
    }
    loadCircles()
    
    // Refresh every 30 seconds
    const interval = setInterval(loadCircles, 30000)
    return () => clearInterval(interval)
  }, [user])

  // Load win of the day
  useEffect(() => {
    if (!user) return
    const loadWin = async () => {
      try {
        // Try with orderBy first (requires index)
        let snapshot
        try {
          const winsQuery = query(
            collection(db, 'healthLogs'),
            where('userId', '==', user.uid),
            where('type', '==', 'win'),
            where('date', '==', today),
            orderBy('timestamp', 'desc'),
            limit(1)
          )
          snapshot = await getDocs(winsQuery)
        } catch (indexError: any) {
          // If orderBy fails (no index), try without it and sort in memory
          if (indexError.code === 'failed-precondition') {
            const winsQuery = query(
              collection(db, 'healthLogs'),
              where('userId', '==', user.uid),
              where('type', '==', 'win'),
              where('date', '==', today)
            )
            snapshot = await getDocs(winsQuery)
            // Sort by timestamp descending
            const sortedDocs = snapshot.docs.sort((a, b) => {
              const aTime = a.data().timestamp?.toMillis?.() || a.data().timestamp || 0
              const bTime = b.data().timestamp?.toMillis?.() || b.data().timestamp || 0
              return bTime - aTime
            })
            snapshot = { ...snapshot, docs: sortedDocs.slice(0, 1) } as any
          } else {
            throw indexError
          }
        }
        if (!snapshot.empty) {
          const win = snapshot.docs[0].data()
          setWinOfTheDay(win.content || win.win || win.gratitude || null)
        }
      } catch (error: any) {
        // Only log if it's not a permission/index error
        if (error.code !== 'permission-denied' && error.code !== 'failed-precondition') {
          console.error('Error loading win of the day:', error)
        }
      }
    }
    loadWin()
  }, [user, today])

  // Load streak
  useEffect(() => {
    if (!user) return
    const loadStreak = async () => {
      try {
        const streakData = await streakService.getUserStreak(user.uid)
        setStreak(streakData)
      } catch (error) {
        console.error('Error loading streak:', error)
      }
    }
    loadStreak()
  }, [user, todayLog]) // Reload streak when todayLog changes

  // Load briefs from Firestore - use getMostRecentBrief to match Android behavior
  useEffect(() => {
    if (!user) return
    const loadBriefs = async () => {
      try {
        // Use getMostRecentBrief to match Android's logic
        const brief = await FirebaseService.getMostRecentBrief(user.uid)
        if (brief) {
          setMorningBrief(brief)
        } else {
          // Fallback: try today's briefs if most recent not found
          const briefs = await FirebaseService.getTodayBriefs(user.uid)
          const hour = new Date().getHours()
          if (hour < 12 && briefs.morning) {
            setMorningBrief(briefs.morning)
          } else if (hour < 17 && briefs.afternoon) {
            setMorningBrief(briefs.afternoon)
          } else if (briefs.evening) {
            setMorningBrief(briefs.evening)
          } else if (briefs.morning) {
            // Fallback to morning brief if current time brief not available
            setMorningBrief(briefs.morning)
          }
        }
      } catch (error) {
        console.error('Error loading briefs:', error)
      }
    }
    loadBriefs()
  }, [user, today])

  // Load weekly blueprint
  useEffect(() => {
    if (!user) return
    const loadBlueprint = async () => {
      try {
        // Get current week starting date (Monday)
        const date = new Date()
        const dayOfWeek = date.getDay() // 0 = Sunday, 1 = Monday, etc.
        const daysToSubtract = dayOfWeek === 0 ? 6 : dayOfWeek - 1 // Monday = 0
        date.setDate(date.getDate() - daysToSubtract)
        const weekStarting = date.toISOString().split('T')[0]
        
        const blueprint = await FirebaseService.getWeeklyBlueprint(user.uid, weekStarting)
        setWeeklyBlueprint(blueprint)
      } catch (error) {
        console.error('Error loading weekly blueprint:', error)
      }
    }
    loadBlueprint()
  }, [user])

  // Load habits
  useEffect(() => {
    if (!user) return
    const loadHabits = async () => {
      try {
        const habitsRef = collection(db, 'users', user.uid, 'habits')
        const habitsQuery = query(habitsRef, where('isActive', '==', true))
        const habitsSnap = await getDocs(habitsQuery)
        const loadedHabits = habitsSnap.docs.map(doc => ({ id: doc.id, ...doc.data() }))
        setHabits(loadedHabits)

        // Load today's completions
        const todayStart = new Date()
        todayStart.setHours(0, 0, 0, 0)
        const completionsRef = collection(db, 'users', user.uid, 'habitCompletions')
        const completionsQuery = query(
          completionsRef,
          where('completedAt', '>=', todayStart),
          where('completedAt', '<', new Date(todayStart.getTime() + 24 * 60 * 60 * 1000))
        )
        const completionsSnap = await getDocs(completionsQuery)
        const completedIds = new Set(completionsSnap.docs.map(doc => doc.data().habitId))
        setCompletedHabits(completedIds.size)
      } catch (error) {
        console.error('Error loading habits:', error)
      }
    }
    loadHabits()
  }, [user])

  // Load Coachie Score from Firebase first, then calculate if needed
  useEffect(() => {
    if (!user) return

    const loadScore = async () => {
      // Try to load saved score from Firebase first (from Android or previous web calculation)
      const savedScore = await CoachieScoreService.getTodayScore(user.uid)
      
      if (savedScore) {
        // Use saved score from Firebase (synced from Android or previous calculation)
        setCoachieScore(savedScore.score)
        setCategoryScores({
          health: savedScore.healthScore,
          wellness: savedScore.wellnessScore,
          habits: savedScore.habitsScore,
        })
        return
      }

      // If no saved score, calculate it
      const stepsGoal = 10000
      const waterGoal = 2000
      const sleepGoal = 8.0

      // Calculate score even if todayLog is null (will be 0)
      const scores = DailyScoreCalculator.calculateAllScores(
        meals,
        workouts,
        sleepLogs,
        waterLogs,
        allHealthLogs,
        todayLog || { id: '', userId: user.uid, date: today, logs: [], createdAt: new Date(), updatedAt: new Date() } as DailyLog,
        habits.length,
        completedHabits,
        calorieGoal,
        stepsGoal,
        waterGoal,
        sleepGoal,
        hasNotifications, // hasCircleInteractionToday
        false // allTodaysFocusTasksCompleted
      )

      const dailyScore = DailyScoreCalculator.calculateDailyScore(scores)
      setCoachieScore(dailyScore)
      setCategoryScores({
        health: scores.healthScore,
        wellness: scores.wellnessScore,
        habits: scores.habitsScore,
      })

      // Save score to Firestore for cross-platform sync
      CoachieScoreService.saveTodayScore(
        user.uid,
        dailyScore,
        scores.healthScore,
        scores.wellnessScore,
        scores.habitsScore
      ).catch(err => console.error('Error saving score:', err))
    }

    loadScore()
  }, [todayLog, userProfile, user, habits.length, completedHabits, hasNotifications])

  // Get greeting based on time of day
  const getGreeting = () => {
    const hour = new Date().getHours()
    if (hour < 12) return 'Morning'
    if (hour < 17) return 'Afternoon'
    return 'Evening'
  }

  // Calculate energy score color
  const getEnergyColor = (score: number | null) => {
    if (score === null) return '#6B46C1'
    if (score >= 80) return '#10B981'
    if (score >= 60) return '#F59E0B'
    if (score >= 40) return '#EF4444'
    return '#6B46C1'
  }

  const handleGenerateInsight = async () => {
    setGeneratingInsight(true)
    try {
      const insight = await onGenerateInsight()
      setDailyInsight(insight)
      setMorningBrief(insight || '')
    } catch (error) {
      console.error('Error generating insight:', error)
    } finally {
      setGeneratingInsight(false)
    }
  }

  const subscriptionTier = userProfile.subscription?.tier || 'FREE'

  return (
    <div className="space-y-4 pb-6">
      {/* 1. Energy Score Card - Prominently Displayed - MUST BE FIRST - ALWAYS VISIBLE */}
      <div className="w-full">
        <EnergyScoreCard
          score={coachieScore ?? 0}
          hrv={null}
          sleepHours={todayLog?.sleepHours || null}
          color={getEnergyColor(coachieScore)}
          scale={1}
          onClick={() => router.push('/flow-score-details')}
        />
      </div>

      {/* 2. Circle and Win Cards Row - Side by Side */}
      <div className="grid grid-cols-2 gap-4">
        <CirclePulseCard
          circle={userCircles[0] || { id: '', name: 'Join a circle' }}
          onNavigateToCircle={() => router.push('/community')}
          totalCircles={userCircles.length}
          hasNotifications={hasNotifications}
        />
        <WinOfTheDayCard
          win={winOfTheDay || 'Share a win from today!'}
          onClick={() => router.push('/my-wins')}
        />
      </div>

      {/* 3. Weekly Blueprint Card */}
      <WeeklyBlueprintCard
        weeklyBlueprint={weeklyBlueprint}
        generatingBlueprint={generatingBlueprint}
        onGenerate={async () => {
          if (!user) return
          try {
            // CRITICAL: Check Pro subscription before generating (blueprint is Pro-only)
            const SubscriptionService = (await import('../lib/services/subscriptionService')).default
            const subscriptionService = SubscriptionService.getInstance()
            const isPro = await subscriptionService.isPro(user.uid)
            
            if (!isPro) {
              // Show upgrade prompt instead of error
              if (confirm('Weekly Blueprint is a Pro feature. Upgrade to Pro to unlock unlimited weekly meal plans!\n\nWould you like to go to the subscription page?')) {
                router.push('/subscription')
              }
              return
            }

            setGeneratingBlueprint(true)
            const { functions } = await import('../lib/firebase')
            const { httpsCallable } = await import('firebase/functions')
            
            // Try generateWeeklyBlueprint first, fallback to generateWeeklyShoppingList
            let generateFunction
            try {
              generateFunction = httpsCallable(functions, 'generateWeeklyBlueprint')
            } catch (e) {
              generateFunction = httpsCallable(functions, 'generateWeeklyShoppingList')
            }
            
            await generateFunction()
            
            // Reload blueprint
            const date = new Date()
            const dayOfWeek = date.getDay()
            const daysToSubtract = dayOfWeek === 0 ? 6 : dayOfWeek - 1
            date.setDate(date.getDate() - daysToSubtract)
            const weekStarting = date.toISOString().split('T')[0]
            
            const blueprint = await FirebaseService.getWeeklyBlueprint(user.uid, weekStarting)
            setWeeklyBlueprint(blueprint)
          } catch (error) {
            console.error('Error generating blueprint:', error)
            alert('Failed to generate blueprint. Please try again.')
          } finally {
            setGeneratingBlueprint(false)
          }
        }}
        onNavigate={() => router.push('/weekly-blueprint')}
      />

      {/* 4. Today's Focus Card */}
      <TodaysResetCard
        userId={user?.uid || ''}
        onNavigateToMealLog={() => router.push('/meal-log')}
        onNavigateToWaterLog={() => router.push('/water-log')}
        onNavigateToWeightLog={() => router.push('/weight-log')}
        onNavigateToSleepLog={() => router.push('/sleep-log')}
        onNavigateToWorkoutLog={() => router.push('/workout-log')}
        onNavigateToSupplementLog={() => router.push('/supplement-log')}
        onNavigateToJournal={() => router.push('/journal-flow')}
        onNavigateToMeditation={() => router.push('/meditation')}
        onNavigateToHabits={() => router.push('/habits')}
        onNavigateToHealthTracking={() => router.push('/health-tracking')}
        onNavigateToWellness={() => router.push('/wellness')}
        onNavigateToBreathingExercises={() => router.push('/breathing-exercises')}
      />

      {/* 5. Morning Brief / AI Insight Card */}
      <MorningBriefInsightCard
        greeting={getGreeting()}
        insight={morningBrief || dailyInsight || ''}
        isSpeaking={isSpeaking}
        onClick={() => router.push('/ai-chat')}
        subscriptionTier={subscriptionTier as 'FREE' | 'PRO'}
        onUpgrade={() => router.push('/subscription')}
      />

      {/* 6. Streak and Calories Row - Side by Side */}
      <div className="grid grid-cols-2 gap-4">
        <StreakBadgeCard
          streak={streak}
          hasActualLogData={allHealthLogs.length > 0}
          onClick={() => router.push('/streak-detail')}
        />
        <ProgressRingCard
          caloriesConsumed={caloriesConsumed}
          caloriesBurned={caloriesBurned}
          dailyGoal={calorieGoal}
          onClick={() => router.push('/calories-detail')}
        />
      </div>

      {/* 7. Today's Log Card */}
      <TodaysLogCard
        meals={meals}
        workouts={workouts}
        sleepLogs={sleepLogs}
        waterMl={waterMl}
        weightLogs={weightLogs}
        useImperial={userProfile.useImperial !== false} // Default to true (imperial)
        onClick={() => router.push('/daily-log')}
      />

      {/* 8. Quick Log Buttons Card */}
      <QuickLogButtonsCard
        onLogMeal={() => router.push('/meal-log')}
        onLogSupplement={() => router.push('/supplement-log')}
        onLogWorkout={() => router.push('/workout-log')}
        onLogSleep={() => router.push('/sleep-log')}
        onLogWater={() => router.push('/water-log')}
        onLogWeight={() => router.push('/weight-log')}
        onVoiceLogging={() => router.push('/voice-logging')}
      />

      {/* Habit tasks are included in Today's Focus card above, matching Android implementation */}

      {/* 10. Navigation Tile Cards */}
      <div className="space-y-3">
        <NavigationTileCard
          title="Health Tracking"
          description="Log meals, workouts, and more"
          icon="💪"
          iconTint="#3B82F6"
          backgroundColor="#3B82F6"
          onClick={() => router.push('/health-tracking')}
          score={coachieScore || undefined}
        />
        <NavigationTileCard
          title="Wellness"
          description="Mindfulness, meditation, and self-care"
          icon="🧘"
          iconTint="#8B5CF6"
          backgroundColor="#8B5CF6"
          onClick={() => router.push('/wellness')}
        />
        <NavigationTileCard
          title="Habits"
          description="Build healthy routines"
          icon="✅"
          iconTint="#10B981"
          backgroundColor="#10B981"
          onClick={() => router.push('/habits')}
        />
        <NavigationTileCard
          title="Community"
          description="Connect with your circles"
          icon="👥"
          iconTint="#F59E0B"
          backgroundColor="#F59E0B"
          onClick={() => router.push('/community')}
        />
        <NavigationTileCard
          title="Quests"
          description="Complete challenges and earn rewards"
          icon="🎯"
          iconTint="#EC4899"
          backgroundColor="#EC4899"
          onClick={() => router.push('/quests')}
          isProOnly={true}
        />
        <NavigationTileCard
          title="Insights"
          description="Analytics and personalized recommendations"
          icon="💡"
          iconTint="#06B6D4"
          backgroundColor="#06B6D4"
          onClick={() => router.push('/insights')}
        />
      </div>
    </div>
  )
}
