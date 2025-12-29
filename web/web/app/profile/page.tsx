'use client'

import { useAuth } from '../../lib/contexts/AuthContext'
import { useRouter } from 'next/navigation'
import { useState, useEffect, useMemo } from 'react'
import { FirebaseService } from '../../lib/services/firebase'
import { MacroTargetsCalculator } from '../../lib/services/macroTargetsCalculator'
import LoadingScreen from '../../components/LoadingScreen'
import toast from 'react-hot-toast'
import type { UserProfile } from '../../types'

function ProfileInfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between items-center">
      <span className="text-gray-400">{label}</span>
      <span className="font-medium text-white">{value}</span>
    </div>
  )
}

export default function ProfilePage() {
  const { user, userProfile, logout } = useAuth()
  const router = useRouter()
  const [loading, setLoading] = useState(true)
  // Get useImperial from userProfile (matches Android)
  const useImperial = userProfile?.useImperial !== false // Default to true (imperial)

  useEffect(() => {
    if (!user) {
      router.push('/auth')
    } else {
      setLoading(false)
    }
  }, [user, router, userProfile])

  const handleSignOut = async () => {
    try {
      await logout()
      router.push('/auth')
    } catch (error) {
      console.error('Error signing out:', error)
      toast.error('Failed to sign out')
    }
  }

  // Calculate macro targets
  const macroTargets = useMemo(() => {
    return MacroTargetsCalculator.calculate(userProfile)
  }, [userProfile])

  if (loading || !user || !userProfile) {
    return <LoadingScreen />
  }

  // Format height
  const heightDisplay = (() => {
    if (!userProfile.heightCm || userProfile.heightCm <= 0) return 'Not set'
    if (useImperial) {
      const totalInches = Math.round(userProfile.heightCm / 2.54)
      const feet = Math.floor(totalInches / 12)
      const inches = totalInches % 12
      return `${feet}'${inches}"`
    }
    return `${Math.round(userProfile.heightCm)} cm`
  })()

  // Format weight
  const weightDisplay = (() => {
    if (!userProfile.currentWeight || userProfile.currentWeight <= 0) return 'Not set'
    if (useImperial) {
      return `${Math.round(userProfile.currentWeight * 2.205)} lbs`
    }
    return `${Math.round(userProfile.currentWeight)} kg`
  })()

  // Format goal weight
  const goalWeightDisplay = (() => {
    if (!userProfile.goalWeight || userProfile.goalWeight <= 0) return 'Not set'
    if (useImperial) {
      return `${Math.round(userProfile.goalWeight * 2.205)} lbs`
    }
    return `${Math.round(userProfile.goalWeight)} kg`
  })()

  // Format activity level
  const activityLevelDisplay = userProfile.activityLevel
    ? userProfile.activityLevel.split('_').map(word => 
        word.charAt(0).toUpperCase() + word.slice(1)
      ).join(' ')
    : 'Not set'

  // Format gender
  const genderDisplay = userProfile.gender
    ? userProfile.gender.charAt(0).toUpperCase() + userProfile.gender.slice(1)
    : 'Not set'

  // Get dietary preference title
  const dietaryPreferenceTitle = (() => {
    const pref = userProfile.dietaryPreference || 'balanced'
    const titles: Record<string, string> = {
      balanced: 'Balanced',
      vegetarian: 'Vegetarian',
      vegan: 'Vegan',
      keto: 'Ketogenic',
      paleo: 'Paleo',
      mediterranean: 'Mediterranean',
      low_carb: 'Low Carb',
      low_fat: 'Low Fat',
      high_protein: 'High Protein',
      pescatarian: 'Pescatarian',
      carnivore: 'Carnivore',
    }
    return titles[pref] || pref
  })()

  // Get user goals (simplified - using profile data)
  const userGoals = {
    selectedGoal: userProfile.goalTrend === 'lose_weight' ? 'Lose Weight'
      : userProfile.goalTrend === 'build_muscle' ? 'Build Muscle'
      : 'Maintain Weight',
    fitnessLevel: activityLevelDisplay,
    weeklyWorkouts: null, // TODO: Get from user goals when available
    dailySteps: null, // TODO: Get from user goals when available
    gender: genderDisplay,
  }

  return (
    <div className="min-h-screen bg-gray-900">
      <div className="max-w-4xl mx-auto py-8 px-4">
        {/* Header */}
        <h1 className="text-3xl font-bold text-white mb-6">My Profile</h1>

        {/* Personal Information Card */}
        <div
          onClick={() => router.push('/personal-info-edit')}
          className="bg-gray-800 rounded-lg shadow-lg p-6 mb-6 border border-gray-700 cursor-pointer hover:bg-gray-750 transition-colors"
        >
          <h2 className="text-xl font-bold text-white mb-4">Personal Information</h2>
          <div className="space-y-3">
            <ProfileInfoRow label="Name" value={userProfile.name || 'Not set'} />
            <ProfileInfoRow 
              label="Age" 
              value={userProfile.age && userProfile.age > 0 ? `${userProfile.age} years` : 'Not set'} 
            />
            <ProfileInfoRow label="Gender" value={genderDisplay} />
            <ProfileInfoRow label="Height" value={heightDisplay} />
            <ProfileInfoRow label="Current Weight" value={weightDisplay} />
            <ProfileInfoRow label="Goal Weight" value={goalWeightDisplay} />
            <ProfileInfoRow label="Activity Level" value={activityLevelDisplay} />
          </div>
        </div>

        {/* My Goals Card */}
        <div
          onClick={() => router.push('/goals-edit')}
          className="bg-gray-800 rounded-lg shadow-lg p-6 mb-6 border border-gray-700 cursor-pointer hover:bg-gray-750 transition-colors"
        >
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-xl font-bold text-white">Your Goals</h2>
            <span className="text-2xl">🚩</span>
          </div>
          
          {userGoals.selectedGoal || userGoals.fitnessLevel || userGoals.weeklyWorkouts || userGoals.dailySteps ? (
            <div className="space-y-3">
              {userGoals.selectedGoal && (
                <ProfileInfoRow label="Main Goal" value={userGoals.selectedGoal} />
              )}
              {userGoals.fitnessLevel && (
                <ProfileInfoRow label="Fitness Level" value={userGoals.fitnessLevel} />
              )}
              {userGoals.weeklyWorkouts && (
                <ProfileInfoRow label="Weekly Workouts" value={`${userGoals.weeklyWorkouts} per week`} />
              )}
              {userGoals.dailySteps && (
                <ProfileInfoRow label="Daily Steps" value={`${userGoals.dailySteps}K steps`} />
              )}
              {userGoals.gender && (
                <ProfileInfoRow label="Gender" value={userGoals.gender} />
              )}
            </div>
          ) : (
            <p className="text-gray-400 text-center py-4">
              Set your fitness goals to get personalized recommendations!
            </p>
          )}
        </div>

        {/* Dietary Preferences Card */}
        <div
          onClick={() => router.push('/dietary-preferences-edit')}
          className="bg-gray-800 rounded-lg shadow-lg p-6 mb-6 border border-gray-700 cursor-pointer hover:bg-gray-750 transition-colors"
        >
          <h2 className="text-xl font-bold text-white mb-4">Dietary Preferences</h2>
          <div className="space-y-3">
            <ProfileInfoRow label="Dietary Preference" value={dietaryPreferenceTitle} />
            <ProfileInfoRow label="Daily Calorie Goal" value={`${macroTargets.calorieGoal} cal`} />
            
            <div className="border-t border-gray-700 my-4"></div>
            
            <h3 className="text-lg font-semibold text-white mb-3">Macro Targets</h3>
            <ProfileInfoRow 
              label="Protein" 
              value={`${macroTargets.proteinGrams}g (${macroTargets.proteinPercent}%)`} 
            />
            <ProfileInfoRow 
              label="Carbs" 
              value={`${macroTargets.carbsGrams}g (${macroTargets.carbsPercent}%)`} 
            />
            <ProfileInfoRow 
              label="Fat" 
              value={`${macroTargets.fatGrams}g (${macroTargets.fatPercent}%)`} 
            />
            
            {macroTargets.recommendation && (
              <>
                <div className="border-t border-gray-700 my-4"></div>
                <p className="text-sm text-gray-300">{macroTargets.recommendation}</p>
              </>
            )}
          </div>
        </div>

        {/* Sign Out Button */}
        <button
          onClick={handleSignOut}
          className="w-full bg-red-900/30 text-red-400 rounded-lg hover:bg-red-900/40 transition-colors border border-red-800 py-3 font-medium"
        >
          Sign Out
        </button>
      </div>
    </div>
  )
}
