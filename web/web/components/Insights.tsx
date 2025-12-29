'use client'

import { useState, useEffect } from 'react'
import { useAuth } from '../lib/contexts/AuthContext'
import { functions } from '../lib/firebase'
import { httpsCallable } from 'firebase/functions'
import { collection, query, where, getDocs, orderBy, limit } from 'firebase/firestore'
import { db } from '../lib/firebase'
import CoachieCard from './ui/CoachieCard'
import CoachieButton from './ui/CoachieButton'
import SharePlatformDialog from './SharePlatformDialog'
import ShareService from '../lib/services/shareService'
import toast from 'react-hot-toast'

interface Insight {
  id: string
  title: string
  text: string
  chartData?: Array<{ x: number; y: number }>
  chartType?: string
  action?: { label: string; type: string }
  generatedAt: Date
}

export default function Insights() {
  const { user } = useAuth()
  const [insights, setInsights] = useState<Insight[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isGenerating, setIsGenerating] = useState(false)
  const [isPro, setIsPro] = useState<boolean>(false)
  const [checkingPro, setCheckingPro] = useState(true)
  const [showShareDialog, setShowShareDialog] = useState(false)
  const [selectedInsight, setSelectedInsight] = useState<Insight | null>(null)

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
      loadInsights()
    }
  }, [user])

  const loadInsights = async () => {
    if (!user) return

    try {
      setIsLoading(true)
      
      // Load insights from Firestore
      const insightsQuery = query(
        collection(db, 'users', user.uid, 'insights'),
        where('status', '==', 'active'),
        orderBy('generatedAt', 'desc'),
        limit(10)
      )
      
      const snapshot = await getDocs(insightsQuery)
      const loadedInsights: Insight[] = snapshot.docs.map(doc => {
        const data = doc.data()
        return {
          id: doc.id,
          title: data.title || 'Insight',
          text: data.text || '',
          chartData: data.chartData || [],
          chartType: data.chartType || 'line',
          action: data.action,
          generatedAt: data.generatedAt?.toDate() || new Date()
        }
      })
      
      setInsights(loadedInsights)
    } catch (error) {
      console.error('Error loading insights:', error)
    } finally {
      setIsLoading(false)
    }
  }

  const generateInsights = async () => {
    if (!user || isGenerating) return

    try {
      // CRITICAL: Check Pro subscription before generating (monthly insights is Pro-only)
      const SubscriptionService = (await import('../lib/services/subscriptionService')).default
      const subscriptionService = SubscriptionService.getInstance()
      const isPro = await subscriptionService.isPro(user.uid)
      
      if (!isPro) {
        // Show upgrade prompt instead of error
        if (confirm('Monthly Insights is a Pro feature. Upgrade to Pro to unlock AI-generated monthly insights!\n\nWould you like to go to the subscription page?')) {
          window.location.href = '/subscription'
        }
        return
      }

      setIsGenerating(true)
      
      // Call Cloud Function to generate insights
      const generateUserInsights = httpsCallable(functions, 'generateUserInsights')
      await generateUserInsights({ forceRegenerate: false })
      
      // Reload insights after generation
      await loadInsights()
      
      alert('Insights generated successfully!')
    } catch (error: any) {
      console.error('Error generating insights:', error)
      if (error.message?.includes('recently')) {
        alert('Insights were generated recently. Use force regenerate to create new ones.')
      } else {
        alert('Failed to generate insights. Please try again.')
      }
    } finally {
      setIsGenerating(false)
    }
  }

  const formatDate = (date: Date) => {
    return date.toLocaleDateString('en-US', { 
      year: 'numeric', 
      month: 'long', 
      day: 'numeric' 
    })
  }

  const { userProfile } = useAuth()
  const gradientClass = userProfile?.gender === 'male'
    ? 'bg-coachie-gradient-male'
    : userProfile?.gender === 'female'
    ? 'bg-coachie-gradient-female'
    : 'bg-coachie-gradient'

  if (isLoading && insights.length === 0) {
    return (
      <div className={`min-h-screen ${gradientClass} flex items-center justify-center`}>
        <div className="text-center">
          <div className="text-4xl mb-4">📊</div>
          <p className="text-white/80">Loading your insights...</p>
        </div>
      </div>
    )
  }

  return (
    <div className={`min-h-screen ${gradientClass}`}>
      <div className="max-w-4xl mx-auto py-8 px-4 space-y-6">
        <div className="flex justify-between items-center">
          <div className="flex items-center gap-3">
          <h1 className="text-3xl font-bold text-white">Monthly Insights</h1>
            {!isPro && !checkingPro && (
              <span className="px-2 py-1 bg-yellow-100 text-yellow-800 text-xs font-semibold rounded-full flex items-center gap-1">
                <span>🔒</span>
                <span>Pro</span>
              </span>
            )}
          </div>
        <CoachieButton 
          onClick={generateInsights} 
          disabled={isGenerating || (!isPro && !checkingPro)}
          variant="outline"
          className={!isPro && !checkingPro ? 'opacity-75' : ''}
        >
          {isGenerating ? 'Generating...' : !isPro && !checkingPro ? '🔒 Pro Feature' : '🔄 Generate Insights'}
        </CoachieButton>
      </div>
      
      {!isPro && !checkingPro && (
        <div className="mb-4 p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
          <div className="flex items-center justify-center gap-2 text-yellow-800">
            <span className="text-lg">🔒</span>
            <span className="font-semibold">Pro Feature</span>
          </div>
          <p className="text-sm text-yellow-700 mt-1 text-center">
            Upgrade to Pro to unlock AI-generated monthly insights with charts and actionable recommendations
          </p>
          <button
            onClick={() => window.location.href = '/subscription'}
            className="mt-2 w-full px-4 py-2 bg-yellow-500 text-yellow-900 rounded-lg hover:bg-yellow-600 font-medium text-sm"
          >
            Upgrade to Pro →
          </button>
        </div>
      )}

      {insights.length === 0 ? (
        <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-12 text-center">
          <div className="text-6xl mb-4">📊</div>
          <h2 className="text-xl font-semibold text-white mb-2">No Insights Yet</h2>
          <p className="text-white/80 mb-6">
            Generate personalized monthly insights based on your activity and progress.
          </p>
          <CoachieButton 
            onClick={generateInsights} 
            disabled={isGenerating || (!isPro && !checkingPro)}
            className={!isPro && !checkingPro ? 'opacity-75' : ''}
          >
            {isGenerating ? 'Generating...' : !isPro && !checkingPro ? '🔒 Pro Feature' : 'Generate My First Insights'}
          </CoachieButton>
          {!isPro && !checkingPro && (
            <button
              onClick={() => window.location.href = '/subscription'}
              className="mt-3 text-sm text-blue-600 hover:text-blue-800 underline"
            >
              Upgrade to Pro →
            </button>
          )}
        </div>
      ) : (
        <div className="space-y-6">
          {insights.map((insight) => (
            <div key={insight.id} className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6">
              <div className="flex items-start justify-between mb-3">
                <h2 className="text-2xl font-bold text-white">{insight.title}</h2>
                <div className="flex items-center gap-3">
                  <span className="text-sm text-white/70">{formatDate(insight.generatedAt)}</span>
                  <button
                    onClick={() => {
                      setSelectedInsight(insight)
                      setShowShareDialog(true)
                    }}
                    className="p-2 bg-white/20 hover:bg-white/30 rounded-lg transition-colors"
                    title="Share insight"
                  >
                    <svg className="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8.684 13.342C8.885 12.938 9 12.482 9 12c0-.482-.115-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z" />
                    </svg>
                  </button>
                </div>
              </div>
              <p className="text-white/90 whitespace-pre-wrap mb-4">{insight.text}</p>
              
              {insight.chartData && insight.chartData.length > 0 && (
                <div className="mt-4 p-4 bg-white/10 rounded-lg border border-white/20">
                  <p className="text-sm text-white/70 mb-2">Trend Visualization</p>
                  <div className="h-32 flex items-end justify-around gap-1">
                    {insight.chartData.map((point, index) => (
                      <div
                        key={index}
                        className="flex-1 bg-primary-500 rounded-t"
                        style={{
                          height: `${(point.y / Math.max(...insight.chartData!.map(p => p.y))) * 100}%`
                        }}
                        title={`${point.y}`}
                      />
                    ))}
                  </div>
                </div>
              )}
              
              {insight.action && (
                <div className="mt-4">
                  <CoachieButton variant="outline">
                    {insight.action.label}
                  </CoachieButton>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {showShareDialog && selectedInsight && (
        <SharePlatformDialog
          onDismiss={() => {
            setShowShareDialog(false)
            setSelectedInsight(null)
          }}
          onShareToPlatform={async (platform) => {
            setShowShareDialog(false)
            try {
              const shareService = ShareService.getInstance()
              const shareText = `📊 ${selectedInsight.title}\n\n${selectedInsight.text.substring(0, 200)}${selectedInsight.text.length > 200 ? '...' : ''}\n\nTracked with Coachie → coachieai.playspace.games`
              
              if (platform) {
                if (navigator.share) {
                  await navigator.share({
                    title: selectedInsight.title,
                    text: shareText,
                  })
                } else {
                  await navigator.clipboard.writeText(shareText)
                  toast.success('Insight copied to clipboard!')
                }
              } else {
                if (navigator.share) {
                  await navigator.share({
                    title: selectedInsight.title,
                    text: shareText,
                  })
                } else {
                  await navigator.clipboard.writeText(shareText)
                  toast.success('Insight copied to clipboard!')
                }
              }
              setSelectedInsight(null)
            } catch (error) {
              console.error('Error sharing:', error)
              toast.error('Failed to share insight')
              setSelectedInsight(null)
            }
          }}
        />
      )}
      </div>
    </div>
  )
}

