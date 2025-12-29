'use client'

import { useAuth } from '../../lib/contexts/AuthContext'
import { useRouter } from 'next/navigation'
import { useState, useEffect } from 'react'
import LoadingScreen from '../../components/LoadingScreen'
import { db } from '../../lib/firebase'
import { doc, getDoc, updateDoc, Timestamp } from 'firebase/firestore'
import toast from 'react-hot-toast'

type SubscriptionTier = 'FREE' | 'PRO'

export default function DebugPage() {
  const { user, userProfile } = useAuth()
  const router = useRouter()
  const [loading, setLoading] = useState(true)
  const [subscriptionTier, setSubscriptionTier] = useState<SubscriptionTier>('FREE')
  const [updating, setUpdating] = useState(false)

  useEffect(() => {
    if (!user) {
      router.push('/auth')
    } else {
      loadSubscriptionTier()
      setLoading(false)
    }
  }, [user, router])

  const loadSubscriptionTier = async () => {
    if (!user) return

    try {
      const userRef = doc(db, 'users', user.uid)
      const userDoc = await getDoc(userRef)
      
      if (userDoc.exists()) {
        const data = userDoc.data()
        const subscription = data.subscription
        if (subscription && subscription.tier) {
          const tier = subscription.tier.toLowerCase()
          setSubscriptionTier(tier === 'pro' ? 'PRO' : 'FREE')
        }
      }
    } catch (error) {
      console.error('Error loading subscription tier:', error)
    }
  }

  const updateSubscriptionTier = async (tier: SubscriptionTier) => {
    if (!user || updating) return

    setUpdating(true)
    try {
      const userRef = doc(db, 'users', user.uid)
      
      // Get current subscription data
      const userDoc = await getDoc(userRef)
      const currentData = userDoc.exists() ? userDoc.data() : {}
      const currentSubscription = currentData.subscription || {}

      const tierLower = tier.toLowerCase() as 'free' | 'pro'
      const now = Date.now()
      const oneYearFromNow = now + (365 * 24 * 60 * 60 * 1000) // 1 year in milliseconds
      const endDateTimestamp = tier === 'PRO' 
        ? Timestamp.fromDate(new Date(oneYearFromNow))
        : Timestamp.fromDate(new Date(now))
      const expiresAtMs = tier === 'PRO' ? oneYearFromNow : now

      // Update subscription - include BOTH formats for Android and Web compatibility
      const newSubscription: any = {
        tier: tierLower,
        // Web format
        status: tier === 'PRO' ? 'active' : 'expired',
        startDate: tier === 'PRO' 
          ? (currentSubscription.startDate || Timestamp.now())
          : (currentSubscription.startDate || Timestamp.now()),
        endDate: tier === 'PRO' ? endDateTimestamp : Timestamp.fromDate(new Date(now)),
        // Android format
        expiresAt: expiresAtMs, // Long in milliseconds (Android expects this)
        isActive: tier === 'PRO', // Boolean (Android expects this)
        purchasedAt: tier === 'PRO' ? now : (currentSubscription.purchasedAt || null),
        purchaseToken: tier === 'PRO' ? 'debug_override' : null,
        productId: tier === 'PRO' ? 'debug_override' : null,
        // Common fields
        paymentProvider: tier === 'PRO' ? 'stripe' : null,
        subscriptionId: tier === 'PRO' ? 'debug_override' : null,
        platforms: tier === 'PRO' ? (currentSubscription.platforms || ['web']) : [],
      }

      await updateDoc(userRef, {
        subscription: newSubscription,
      })

      setSubscriptionTier(tier)
      toast.success(`Subscription tier set to ${tier}`)
      
      // Reload page after a short delay to refresh user profile
      setTimeout(() => {
        window.location.reload()
      }, 1000)
    } catch (error: any) {
      console.error('Error updating subscription tier:', error)
      toast.error(`Failed to update subscription: ${error.message}`)
    } finally {
      setUpdating(false)
    }
  }

  if (loading || !user) {
    return <LoadingScreen />
  }

  return (
    <div className="min-h-screen bg-gray-900">
      <div className="max-w-4xl mx-auto py-8 px-4">
        <h1 className="text-2xl font-bold text-white mb-6">Debug Information</h1>

        <div className="space-y-6">
          {/* User Info */}
          <div className="bg-gray-800 rounded-lg shadow-lg p-6 space-y-4 border border-gray-700">
            <h2 className="text-lg font-semibold text-white mb-4">User Information</h2>
            <div>
              <h3 className="font-semibold text-white mb-2">User ID</h3>
              <p className="text-sm text-gray-400 font-mono">{user.uid}</p>
            </div>
            <div>
              <h3 className="font-semibold text-white mb-2">Email</h3>
              <p className="text-sm text-gray-400">{user.email}</p>
            </div>
            <div>
              <h3 className="font-semibold text-white mb-2">Platform</h3>
              <p className="text-sm text-gray-400">Web</p>
            </div>
          </div>

          {/* Subscription Tier Switcher */}
          <div className="bg-gray-800 rounded-lg shadow-lg p-6 border border-gray-700">
            <h2 className="text-lg font-semibold text-white mb-4">Subscription Tier (Debug)</h2>
            <p className="text-sm text-gray-400 mb-4">
              Current tier: <span className="font-bold text-blue-400">{subscriptionTier}</span>
            </p>
            
            <div className="flex gap-4">
              <button
                onClick={() => updateSubscriptionTier('FREE')}
                disabled={updating || subscriptionTier === 'FREE'}
                className={`px-6 py-3 rounded-lg font-semibold transition-colors ${
                  subscriptionTier === 'FREE'
                    ? 'bg-gray-700 text-gray-400 cursor-not-allowed'
                    : 'bg-gray-600 text-white hover:bg-gray-500'
                } disabled:opacity-50`}
              >
                {updating ? 'Updating...' : 'Set to FREE'}
              </button>
              
              <button
                onClick={() => updateSubscriptionTier('PRO')}
                disabled={updating || subscriptionTier === 'PRO'}
                className={`px-6 py-3 rounded-lg font-semibold transition-colors ${
                  subscriptionTier === 'PRO'
                    ? 'bg-blue-700 text-blue-300 cursor-not-allowed'
                    : 'bg-blue-600 text-white hover:bg-blue-500'
                } disabled:opacity-50`}
              >
                {updating ? 'Updating...' : 'Set to PRO'}
              </button>
            </div>

            <div className="mt-4 p-4 bg-yellow-900/30 rounded-lg border border-yellow-800">
              <p className="text-sm text-yellow-300">
                ⚠️ <strong>Debug Mode:</strong> This will update your subscription tier in Firebase.
                The page will reload after updating to refresh your profile.
              </p>
            </div>
          </div>

          {/* Profile Data */}
          {userProfile && (
            <div className="bg-gray-800 rounded-lg shadow-lg p-6 border border-gray-700">
              <h2 className="text-lg font-semibold text-white mb-4">Profile Data</h2>
              <pre className="text-xs text-gray-400 bg-gray-900 p-4 rounded overflow-auto max-h-96 border border-gray-700">
                {JSON.stringify(userProfile, null, 2)}
              </pre>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
