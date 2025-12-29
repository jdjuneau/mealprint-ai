'use client'

import { useAuth } from '../../lib/contexts/AuthContext'
import { useRouter } from 'next/navigation'
import { useEffect } from 'react'
import LoadingScreen from '../../components/LoadingScreen'
import WeeklyBlueprint from '../../components/WeeklyBlueprint'

export default function WeeklyBlueprintPage() {
  const { user, userProfile } = useAuth()
  const router = useRouter()

  useEffect(() => {
    if (!user) {
      router.push('/auth')
    }
  }, [user, router])

  if (!user || !userProfile) {
    return <LoadingScreen />
  }

  return <WeeklyBlueprint />
}
