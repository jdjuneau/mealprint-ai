'use client'

import { useEffect } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { useAuth } from '../../../../lib/contexts/AuthContext'
import FitbitService from '../../../../lib/services/fitbitService'
import { db } from '../../../../lib/firebase'
import { doc, setDoc } from 'firebase/firestore'
import HealthSyncService from '../../../../lib/services/healthSyncService'
import toast from 'react-hot-toast'

export default function FitbitCallbackPage() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { user } = useAuth()
  const fitbitService = FitbitService.getInstance()
  const syncService = HealthSyncService.getInstance()

  useEffect(() => {
    const handleCallback = async () => {
      if (!user) {
        router.push('/auth')
        return
      }

      const code = searchParams.get('code')
      const error = searchParams.get('error')

      if (error) {
        toast.error(`Fitbit authorization failed: ${error}`)
        router.push('/health-tracking')
        return
      }

      if (!code) {
        toast.error('No authorization code received')
        router.push('/health-tracking')
        return
      }

      try {
        // Exchange code for token
        const tokenData = await fitbitService.exchangeCodeForToken(code)

        // Save token to Firebase
        const serviceRef = doc(db, 'users', user.uid, 'health_services', 'fitbit')
        await setDoc(serviceRef, {
          type: 'fitbit',
          token: tokenData.access_token,
          refreshToken: tokenData.refresh_token,
          expiresAt: Date.now() + tokenData.expires_in * 1000,
          userId: user.uid,
          connectedAt: new Date(),
          lastSync: new Date(),
        })

        // Start background sync
        await syncService.startBackgroundSync(user.uid, 'fitbit')

        toast.success('Fitbit connected successfully!')
        router.push('/health-tracking')
      } catch (error: any) {
        console.error('Fitbit callback error:', error)
        toast.error(`Failed to connect Fitbit: ${error.message}`)
        router.push('/health-tracking')
      }
    }

    handleCallback()
  }, [user, searchParams, router])

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center">
      <div className="text-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
        <p className="text-gray-600">Connecting Fitbit...</p>
      </div>
    </div>
  )
}

