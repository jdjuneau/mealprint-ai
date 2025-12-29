'use client'

import { useAuth } from '../lib/contexts/AuthContext'
import { useRouter } from 'next/navigation'
import { useEffect } from 'react'
import AuthScreen from '../components/AuthScreen'
import Dashboard from '../components/Dashboard'
import LoadingScreen from '../components/LoadingScreen'

export default function Home() {
  const { user, userProfile, loading } = useAuth()
  const router = useRouter()

  console.log('Main page - user:', !!user, 'userProfile:', !!userProfile, 'loading:', loading)
  console.log('User profile details:', userProfile)

  useEffect(() => {
    console.log('Main page useEffect - checking redirects')
    if (!loading && user && userProfile) {
      console.log('User has profile, checking completeness and FTUE status:', {
        ftueCompleted: userProfile.ftueCompleted,
        currentWeight: userProfile.currentWeight,
        heightCm: userProfile.heightCm,
        goalWeight: userProfile.goalWeight,
        age: userProfile.age,
        hasCompleteProfile: userProfile.currentWeight > 0 && userProfile.heightCm > 0 && userProfile.goalWeight > 0 && userProfile.age > 0
      })

      // Check if user needs to complete FTUE
      if (userProfile.ftueCompleted === false || userProfile.ftueCompleted === undefined) {
        // First check if they have complete profile info
        // For Mealprint AI, focus on dietary preferences and meal planning setup
        const hasCompleteProfile = userProfile.dietaryPreference &&
                                  userProfile.mealsPerDay &&
                                  userProfile.snacksPerDay &&
                                  userProfile.preferredCookingMethods &&
                                  userProfile.preferredCookingMethods.length > 0

        if (!hasCompleteProfile) {
          console.log('Profile incomplete, redirecting to dietary preferences setup')
          // Redirect to dietary preferences setup first - use replace to prevent back navigation
          router.replace('/dietary-preferences-edit')
          return
        } else {
          console.log('Profile complete, redirecting to FTUE')
          // Redirect to FTUE - use replace to prevent back navigation
          router.replace('/ftue')
          return
        }
      } else {
        console.log('FTUE completed, showing dashboard')
      }
    }
  }, [user, userProfile, loading, router])

  if (loading) {
    return <LoadingScreen />
  }

  if (!user) {
    return <AuthScreen />
  }

  // If FTUE not completed, show loading while redirecting
  if (userProfile && (userProfile.ftueCompleted === false || userProfile.ftueCompleted === undefined)) {
    return <LoadingScreen />
  }

  return <Dashboard />
}
