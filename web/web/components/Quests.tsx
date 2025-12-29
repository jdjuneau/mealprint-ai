'use client'

import { useState, useEffect, useCallback } from 'react'
import { useAuth } from '../lib/contexts/AuthContext'
import { functions } from '../lib/firebase'
import { httpsCallable } from 'firebase/functions'
import { collection, query, where, getDocs } from 'firebase/firestore'
import { db } from '../lib/firebase'
import CoachieCard from './ui/CoachieCard'
import CoachieButton from './ui/CoachieButton'
import SharePlatformDialog from './SharePlatformDialog'
import ShareService from '../lib/services/shareService'
import toast from 'react-hot-toast'

interface Quest {
  id: string
  title: string
  description: string
  progress: number // 0-100
  target: number
  current: number
  type: string // 'habit', 'streak', 'goal', 'challenge'
  icon: string
  color: string
}

export default function Quests() {
  const { user } = useAuth()
  const [quests, setQuests] = useState<Quest[]>([])
  const [completedQuests, setCompletedQuests] = useState<Quest[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isPro, setIsPro] = useState<boolean>(false)
  const [checkingPro, setCheckingPro] = useState(true)
  const [showShareDialog, setShowShareDialog] = useState(false)
  const [selectedQuest, setSelectedQuest] = useState<Quest | null>(null)

  const loadQuests = useCallback(async () => {
    if (!user) return

    try {
      setIsLoading(true)
      
      // Call Cloud Function to get user quests
      const getUserQuests = httpsCallable(functions, 'getUserQuests')
      const result = await getUserQuests()
      const data = result.data as any
      
      if (data?.activeQuests) {
        setQuests(data.activeQuests)
      }
      if (data?.completedQuests) {
        setCompletedQuests(data.completedQuests)
      }
    } catch (error) {
      console.error('Error loading quests:', error)
      // Fallback: show sample quests
      setQuests([
        {
          id: 'hydration_quest',
          title: '7-Day Hydration Quest',
          description: 'Drink 8 glasses of water daily for 7 days',
          progress: 43,
          target: 7,
          current: 3,
          type: 'habit',
          icon: '💧',
          color: '#3B82F6'
        },
        {
          id: 'steps_quest',
          title: '10K Steps Challenge',
          description: 'Walk 10,000 steps daily for 5 days',
          progress: 40,
          target: 5,
          current: 2,
          type: 'goal',
          icon: '🚶',
          color: '#10B981'
        }
      ])
    } finally {
      setIsLoading(false)
    }
  }, [user])

  useEffect(() => {
    if (user) {
      // Load Pro status
      const checkProStatus = async () => {
        try {
          const SubscriptionService = (await import('../lib/services/subscriptionService')).default
          const subscriptionService = SubscriptionService.getInstance()
          const proStatus = await subscriptionService.isPro(user.uid)
          setIsPro(proStatus)
        } catch (error) {
          console.error('Error checking Pro status:', error)
          setIsPro(false)
        } finally {
          setCheckingPro(false)
        }
      }
      checkProStatus()
      loadQuests()
    }
  }, [user, loadQuests])

  const formatProgress = (progress: number) => {
    return Math.round(progress)
  }

  const { userProfile } = useAuth()
  const gradientClass = userProfile?.gender === 'male'
    ? 'bg-coachie-gradient-male'
    : userProfile?.gender === 'female'
    ? 'bg-coachie-gradient-female'
    : 'bg-coachie-gradient';

  if (isLoading) {
    return (
      <div className={`min-h-screen ${gradientClass} flex items-center justify-center`}>
        <div className="text-center">
          <div className="text-4xl mb-4">🎯</div>
          <p className="text-white/80">Loading your quests...</p>
        </div>
      </div>
    )
  }

  return (
    <div className={`min-h-screen ${gradientClass}`}>
      <div className="max-w-4xl mx-auto py-8 px-4 space-y-6">
        <div className="flex justify-between items-center">
          <div className="flex items-center gap-3">
          <h1 className="text-3xl font-bold text-white">Quests</h1>
            {!isPro && !checkingPro && (
              <span className="px-2 py-1 bg-yellow-100 text-yellow-800 text-xs font-semibold rounded-full flex items-center gap-1">
                <span>🔒</span>
                <span>Pro</span>
              </span>
            )}
          </div>
          <CoachieButton onClick={loadQuests} variant="outline">
            🔄 Refresh
          </CoachieButton>
        </div>
        
        {!isPro && !checkingPro && (
          <div className="mb-4 p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
            <div className="flex items-center justify-center gap-2 text-yellow-800">
              <span className="text-lg">🔒</span>
              <span className="font-semibold">Pro Feature</span>
            </div>
            <p className="text-sm text-yellow-700 mt-1 text-center">
              AI quest generation is a Pro feature. Upgrade to unlock personalized quests based on your goals
            </p>
            <button
              onClick={() => window.location.href = '/subscription'}
              className="mt-2 w-full px-4 py-2 bg-yellow-500 text-yellow-900 rounded-lg hover:bg-yellow-600 font-medium text-sm"
            >
              Upgrade to Pro →
            </button>
          </div>
        )}

      {/* Active Quests */}
      <div>
        <h2 className="text-xl font-semibold text-gray-900 mb-4">Active Quests</h2>
        {quests.length === 0 ? (
          <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-12 text-center">
            <div className="text-6xl mb-4">🎯</div>
            <h3 className="text-xl font-semibold text-white mb-2">No Active Quests</h3>
            <p className="text-white/80">
              Quests will appear here as Coachie suggests challenges based on your goals.
            </p>
          </div>
        ) : (
          <div className="space-y-4">
            {quests.map((quest) => (
              <div key={quest.id} className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6">
                <div className="flex items-start gap-4 mb-4">
                  <div className="text-4xl">{quest.icon}</div>
                  <div className="flex-1">
                    <h3 className="text-xl font-bold text-white mb-1">{quest.title}</h3>
                    <p className="text-white/80 mb-3">{quest.description}</p>
                    
                    {/* Progress Bar */}
                    <div className="mb-2">
                      <div className="flex justify-between text-sm text-white/80 mb-1">
                        <span>{quest.current} / {quest.target}</span>
                        <span>{formatProgress(quest.progress)}%</span>
                      </div>
                      <div className="w-full bg-white/20 rounded-full h-3 overflow-hidden">
                        <div
                          className="h-full rounded-full transition-all duration-300"
                          style={{
                            width: `${formatProgress(quest.progress)}%`,
                            backgroundColor: quest.color || '#667eea'
                          }}
                        />
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Completed Quests */}
      {completedQuests.length > 0 && (
        <div>
          <h2 className="text-xl font-semibold text-white mb-4">Completed Quests</h2>
          <div className="space-y-4">
            {completedQuests.map((quest) => (
              <div key={quest.id} className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6 opacity-60">
                <div className="flex items-start gap-4">
                  <div className="text-4xl">{quest.icon}</div>
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-1">
                      <h3 className="text-xl font-bold text-white">{quest.title}</h3>
                      <span className="text-green-400 font-semibold">✓ Completed</span>
                    </div>
                    <p className="text-white/80">{quest.description}</p>
                  </div>
                  <button
                    onClick={() => {
                      setSelectedQuest(quest)
                      setShowShareDialog(true)
                    }}
                    className="p-2 bg-white/20 hover:bg-white/30 rounded-lg transition-colors"
                    title="Share quest"
                  >
                    <svg className="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8.684 13.342C8.885 12.938 9 12.482 9 12c0-.482-.115-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z" />
                    </svg>
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {showShareDialog && selectedQuest && (
        <SharePlatformDialog
          onDismiss={() => {
            setShowShareDialog(false)
            setSelectedQuest(null)
          }}
          onShareToPlatform={async (platform) => {
            setShowShareDialog(false)
            try {
              const shareService = ShareService.getInstance()
              const shareText = `🎯 Quest Completed: ${selectedQuest.title}!\n\n${selectedQuest.description}\n\nTracked with Coachie → coachieai.playspace.games`
              
              if (platform) {
                if (navigator.share) {
                  await navigator.share({
                    title: `Quest Completed: ${selectedQuest.title}`,
                    text: shareText,
                  })
                } else {
                  await navigator.clipboard.writeText(shareText)
                  toast.success('Quest details copied to clipboard!')
                }
              } else {
                if (navigator.share) {
                  await navigator.share({
                    title: `Quest Completed: ${selectedQuest.title}`,
                    text: shareText,
                  })
                } else {
                  await navigator.clipboard.writeText(shareText)
                  toast.success('Quest details copied to clipboard!')
                }
              }
              setSelectedQuest(null)
            } catch (error) {
              console.error('Error sharing:', error)
              toast.error('Failed to share quest')
              setSelectedQuest(null)
            }
          }}
        />
      )}
      </div>
    </div>
  )
}

