'use client'

import { createContext, useContext, useEffect, useState, ReactNode } from 'react'
import {
  User,
  signInWithPopup,
  signOut,
  onAuthStateChanged,
  GoogleAuthProvider,
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  updateProfile
} from 'firebase/auth'
import { auth } from '../firebase'
import { FirebaseService } from '../services/firebase'
import type { UserProfile } from '../../types'

interface AuthContextType {
  user: User | null
  userProfile: UserProfile | null
  loading: boolean
  signInWithGoogle: () => Promise<void>
  signInWithEmail: (email: string, password: string) => Promise<void>
  signUpWithEmail: (email: string, passwordOrProfile: string | object, nameOrPassword?: string) => Promise<void>
  logout: () => Promise<void>
  updateUserProfile: (updates: Partial<UserProfile>) => Promise<void>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [userProfile, setUserProfile] = useState<UserProfile | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    // Import and start message notification service
    import('../services/messageNotificationService').then((module) => {
      const messageNotificationService = module.default
      if (user) {
        messageNotificationService.startListening(user.uid)
      }
    })

    // Safety timeout - force loading to false after 5 seconds MAX
    const timeoutId = setTimeout(() => {
      console.warn('⚠️ [AUTH] Loading timeout - forcing loading to false')
      setLoading(false)
    }, 5000)

    let isMounted = true

    const unsubscribe = onAuthStateChanged(auth, async (firebaseUser) => {
      try {
        setUser(firebaseUser)

        if (firebaseUser) {
          // Load user profile from Firestore with timeout
          try {
            const profilePromise = FirebaseService.getUserProfile(firebaseUser.uid)
            const timeoutPromise = new Promise((_, reject) => 
              setTimeout(() => reject(new Error('getUserProfile timeout')), 3000)
            )
            
            const profile = await Promise.race([profilePromise, timeoutPromise]) as UserProfile | null
            
            if (isMounted && profile) {
              setUserProfile(profile)
            } else if (isMounted && (firebaseUser.displayName || firebaseUser.email)) {
              // Create initial profile if it doesn't exist
              const initialProfile: UserProfile = {
                id: firebaseUser.uid,
                name: firebaseUser.displayName || firebaseUser.email?.split('@')[0] || 'User',
                currentWeight: 0,
                goalWeight: 0,
                heightCm: 0,
                age: 0,
                gender: 'male',
                activityLevel: 'moderately active',
                ftueCompleted: false,
                platform: 'web',
                platforms: ['web'],
                useImperial: true, // Default to imperial (matching Android)
                createdAt: new Date(),
                updatedAt: new Date(),
              }
              // Don't wait for save - just set the profile
              setUserProfile(initialProfile)
              // Try to save in background (don't await)
              FirebaseService.saveUserProfile(initialProfile).catch(err => 
                console.error('Failed to save initial profile:', err)
              )
            }
          } catch (profileError) {
            console.error('❌ [AUTH] Error loading profile:', profileError)
            // Set a minimal profile so app doesn't hang
            if (isMounted) {
              setUserProfile({
                id: firebaseUser.uid,
                name: firebaseUser.displayName || firebaseUser.email?.split('@')[0] || 'User',
                currentWeight: 0,
                goalWeight: 0,
                heightCm: 0,
                age: 0,
                gender: 'male',
                activityLevel: 'moderately active',
                ftueCompleted: false,
                platform: 'web',
                platforms: ['web'],
                createdAt: new Date(),
                updatedAt: new Date(),
              })
            }
          }
        } else {
          if (isMounted) {
            setUserProfile(null)
          }
        }
      } catch (error) {
        console.error('❌ [AUTH] Error in onAuthStateChanged:', error)
      } finally {
        if (isMounted) {
          clearTimeout(timeoutId)
          setLoading(false)
        }
      }
    })

    return () => {
      isMounted = false
      clearTimeout(timeoutId)
      unsubscribe()
    }
  }, [])

  const signInWithGoogle = async () => {
    const provider = new GoogleAuthProvider()
    await signInWithPopup(auth, provider)
  }

  const signInWithEmail = async (email: string, password: string) => {
    try {
      await signInWithEmailAndPassword(auth, email, password)
    } catch (error: any) {
      console.error('Sign in error:', error)
      // Provide user-friendly error messages
      if (error.code === 'auth/user-not-found') {
        throw new Error('No account found with this email. Please sign up first.')
      } else if (error.code === 'auth/wrong-password') {
        throw new Error('Incorrect password. Please try again.')
      } else if (error.code === 'auth/invalid-email') {
        throw new Error('Invalid email address.')
      } else if (error.code === 'auth/user-disabled') {
        throw new Error('This account has been disabled.')
      } else if (error.code === 'auth/too-many-requests') {
        throw new Error('Too many failed attempts. Please try again later.')
      } else {
        throw new Error(error.message || 'Failed to sign in. Please check your credentials.')
      }
    }
  }

  const signUpWithEmail = async (email: string, passwordOrProfile: string | object, nameOrPassword?: string) => {
    let actualEmail = email
    let actualPassword = passwordOrProfile as string
    let profileData: any = {}

    // Handle new signature with profile data
    if (typeof passwordOrProfile === 'object') {
      actualPassword = nameOrPassword as string
      profileData = passwordOrProfile
    } else {
      // Handle old signature (backwards compatibility)
      profileData = { name: nameOrPassword }
    }

    const result = await createUserWithEmailAndPassword(auth, actualEmail, actualPassword)
    await updateProfile(result.user, { displayName: profileData.name })

    // Create initial profile with ALL provided data
    const initialProfile: UserProfile = {
      id: result.user.uid,
      name: profileData.name,
      currentWeight: profileData.currentWeight || 0,
      goalWeight: profileData.goalWeight || 0,
      heightCm: profileData.heightCm || 0,
      age: profileData.age || 0,
      gender: profileData.gender || 'male',
      activityLevel: profileData.activityLevel || 'moderately active',
      dietaryPreference: profileData.dietaryPreference,
      goalTrend: profileData.goalTrend,
      mealsPerDay: profileData.mealsPerDay,
      snacksPerDay: profileData.snacksPerDay,
      preferredCookingMethods: profileData.preferredCookingMethods || [],
      useImperial: profileData.useImperial !== false, // Default to true (imperial)
      menstrualCycleEnabled: profileData.menstrualCycleEnabled,
      averageCycleLength: profileData.averageCycleLength,
      averagePeriodLength: profileData.averagePeriodLength,
      lastPeriodStart: profileData.lastPeriodStart,
      nudgesEnabled: profileData.nudgesEnabled,
      notifications: profileData.notifications,
      mealTimes: profileData.mealTimes,
      ftueCompleted: false,
      platform: 'web',
      platforms: ['web'],
      createdAt: new Date(),
      updatedAt: new Date(),
    }

    // Save the profile (this will also save useImperial to goals via saveUserProfile)
    const success = await FirebaseService.saveUserProfile(initialProfile)
    if (success) {
      setUserProfile(initialProfile)
      // Also explicitly save to goals to ensure it's stored (matching Android)
      if (initialProfile.useImperial !== undefined) {
        FirebaseService.saveUserGoals(result.user.uid, { useImperial: initialProfile.useImperial }).catch(err =>
          console.error('Failed to save useImperial to goals:', err)
        )
      }
    }
  }

  const logout = async () => {
    // Stop message notifications
    const messageNotificationService = (await import('../services/messageNotificationService')).default
    messageNotificationService.stopListening()
    
    await signOut(auth)
    setUser(null)
    setUserProfile(null)
  }

  const updateUserProfile = async (updates: Partial<UserProfile>) => {
    if (!user) {
      console.error('Cannot update profile: no user logged in')
      return
    }

    // If userProfile doesn't exist yet (new user), create a basic profile first
    const baseProfile = userProfile || {
      id: user.uid,
      name: user.displayName || '',
      email: user.email || '',
      currentWeight: 0,
      goalWeight: 0,
      heightCm: 0,
      age: 0,
      gender: 'male' as const,
      activityLevel: 'moderately active' as const,
      createdAt: new Date(),
      updatedAt: new Date(),
    }

    const updatedProfile = {
      ...baseProfile,
      ...updates,
      id: user.uid, // Ensure ID is always set
      updatedAt: new Date(),
    }

    const success = await FirebaseService.saveUserProfile(updatedProfile)
    if (success) {
      setUserProfile(updatedProfile)
    } else {
      console.error('Failed to save user profile')
      throw new Error('Failed to save user profile')
    }
  }

  const value: AuthContextType = {
    user,
    userProfile,
    loading,
    signInWithGoogle,
    signInWithEmail,
    signUpWithEmail,
    logout,
    updateUserProfile,
  }

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
