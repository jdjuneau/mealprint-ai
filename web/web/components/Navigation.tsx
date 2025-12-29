'use client'

import { useState, useEffect, useRef } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '../lib/contexts/AuthContext'
import type { UserProfile } from '../types'
import type { ActiveTab } from '../types/navigation'

interface NavigationProps {
  activeTab: ActiveTab
  onTabChange: (tab: ActiveTab) => void
  userName: string
  userProfile?: UserProfile
}

const navigationItems = [
  { id: 'dashboard' as const, label: 'Dashboard', icon: '🏠', category: 'main' },
  { id: 'meal' as const, label: 'Meals', icon: '🍽️', category: 'health' },
  { id: 'workout' as const, label: 'Workouts', icon: '🏋️', category: 'health' },
  { id: 'sleep' as const, label: 'Sleep', icon: '😴', category: 'health' },
  { id: 'water' as const, label: 'Water', icon: '💧', category: 'health' },
  { id: 'weight' as const, label: 'Weight', icon: '⚖️', category: 'health' },
  { id: 'mood' as const, label: 'Mood', icon: '😊', category: 'health' },
  { id: 'supplement' as const, label: 'Supplements', icon: '💊', category: 'supplements' },
  { id: 'chat' as const, label: 'AI Coach', icon: '🤖', category: 'ai' },
  { id: 'voice' as const, label: 'Voice Log', icon: '🎤', category: 'ai' },
  { id: 'habits' as const, label: 'Habits', icon: '🎯', category: 'goals' },
  { id: 'goals' as const, label: 'Goals', icon: '📈', category: 'goals' },
  { id: 'mindfulness' as const, label: 'Mindfulness', icon: '🧘', category: 'wellness' },
  { id: 'meditation' as const, label: 'Meditation', icon: '🧘‍♀️', category: 'wellness' },
  { id: 'charts' as const, label: 'Analytics', icon: '📊', category: 'data' },
  { id: 'achievements' as const, label: 'Achievements', icon: '🏆', category: 'data' },
  { id: 'my-wins' as const, label: 'My Wins', icon: '🎉', category: 'data' },
  { id: 'quests' as const, label: 'Quests', icon: '🎯', category: 'data' },
  { id: 'insights' as const, label: 'Insights', icon: '💡', category: 'data' },
  { id: 'saved-meals' as const, label: 'Saved Meals', icon: '⭐', category: 'library' },
  { id: 'weekly-blueprint' as const, label: 'Weekly Blueprint', icon: '📋', category: 'library' },
  { id: 'journal' as const, label: 'Journal', icon: '📔', category: 'library' },
  { id: 'community' as const, label: 'Community', icon: '👥', category: 'social' },
  { id: 'profile' as const, label: 'Profile', icon: '👤', category: 'settings' },
  { id: 'settings' as const, label: 'Settings', icon: '⚙️', category: 'settings' },
  { id: 'help' as const, label: 'Help', icon: '🆘', category: 'settings' },
]

const categoryLabels = {
  main: 'Dashboard',
  health: 'Health Tracking',
  supplements: 'Supplements',
  ai: 'AI & Voice',
  goals: 'Goals & Habits',
  wellness: 'Wellness',
  social: 'Community',
  data: 'Progress',
  library: 'Library',
  settings: 'Account'
}

export default function Navigation({ activeTab, onTabChange, userName, userProfile }: NavigationProps) {
  console.log('Navigation function called with:', { activeTab, userName, userProfile })

  const { logout, user } = useAuth()
  const router = useRouter()
  const [menuOpen, setMenuOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  // Close menu when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setMenuOpen(false)
      }
    }

    if (menuOpen) {
      document.addEventListener('mousedown', handleClickOutside)
    }

    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [menuOpen])

  console.log('Navigation state:', { menuOpen, user: !!user })

  // Group items by category
  const groupedItems = navigationItems.reduce((acc: Record<string, typeof navigationItems>, item) => {
    if (!acc[item.category]) {
      acc[item.category] = []
    }
    acc[item.category].push(item)
    return acc
  }, {} as Record<string, typeof navigationItems>)

  console.log('Navigation groupedItems:', Object.keys(groupedItems))

  console.log('🔵 Navigation component rendering', { activeTab, userName, hasUserProfile: !!userProfile, user })

  if (!userName) {
    console.warn('⚠️ Navigation: userName is missing!', { userName, userProfile })
  }

  // Force render even if userName is missing
  const displayName = userName || user?.email || 'User'

  try {
    return (
    <nav 
      className="bg-white border-b-2 border-gray-300 sticky top-0 z-[9999] shadow-lg" 
      style={{ 
        display: 'block', 
        visibility: 'visible', 
        position: 'sticky', 
        top: 0,
        width: '100%',
        backgroundColor: 'white',
        minHeight: '64px'
      } as React.CSSProperties}
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between h-16">
          <div className="flex items-center">
            <div className="flex-shrink-0 flex items-center">
              <h1 className="text-xl font-bold text-blue-600">
                🏋️ Coachie
              </h1>
            </div>
          </div>

          <div className="flex items-center space-x-4">
            <span className="text-sm text-gray-700 font-medium">Welcome, {displayName}</span>

            <div className="relative" ref={menuRef}>
              <button
                onClick={(e) => {
                  e.stopPropagation()
                  console.log('3-DOT MENU CLICKED!')
                  setMenuOpen(!menuOpen)
                }}
                className="inline-flex items-center justify-center px-4 py-2 rounded-lg text-gray-700 font-medium bg-gray-100 hover:bg-gray-200 border border-gray-300"
              >
                <span className="mr-2">⋯</span>
                <span className="text-xs">MENU</span>
              </button>

              {menuOpen && (
                <div 
                  className="absolute right-0 top-full mt-1 w-56 bg-white rounded-lg shadow-xl border border-gray-200 z-50"
                  onClick={(e) => e.stopPropagation()}
                >
                  <div className="py-1">
                    <button
                      onClick={async () => {
                        setMenuOpen(false)
                        if (!user) return
                        try {
                          const HealthSyncService = (await import('../lib/services/healthSyncService')).default
                          const syncService = HealthSyncService.getInstance()
                          await syncService.syncAllServices(user.uid)
                          alert('Health data sync completed')
                        } catch (error) {
                          console.error('Sync error:', error)
                          alert('Failed to sync health data')
                        }
                      }}
                      className="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 flex items-center"
                    >
                      <span className="mr-3">🔄</span>
                      Sync Health Data
                    </button>
                    <button
                      onClick={() => {
                        setMenuOpen(false)
                        router.push('/profile')
                      }}
                      className="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 flex items-center"
                    >
                      <span className="mr-3">👤</span>
                      Profile
                    </button>
                    <button
                      onClick={() => {
                        setMenuOpen(false)
                        router.push('/settings')
                      }}
                      className="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 flex items-center"
                    >
                      <span className="mr-3">⚙️</span>
                      Settings
                    </button>
                    <button
                      onClick={() => {
                        setMenuOpen(false)
                        router.push('/help')
                      }}
                      className="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 flex items-center"
                    >
                      <span className="mr-3">❓</span>
                      Help & QA
                    </button>
                    <button
                      onClick={async () => {
                        setMenuOpen(false)
                        if (navigator.share) {
                          try {
                            await navigator.share({
                              title: 'Coachie - Your AI Health Coach',
                              text: 'Check out Coachie, your AI-powered health and wellness coach!',
                              url: window.location.origin
                            })
                          } catch (error) {
                            // User cancelled or error
                          }
                        } else {
                          // Fallback: copy to clipboard
                          await navigator.clipboard.writeText(window.location.origin)
                          alert('App link copied to clipboard!')
                        }
                      }}
                      className="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 flex items-center"
                    >
                      <span className="mr-3">📤</span>
                      Share App
                    </button>
                    {/* Debug menu item removed */}
                    <button
                      onClick={async () => {
                        setMenuOpen(false)
                        if (window.confirm('Are you sure you want to sign out?')) {
                          await logout()
                          router.push('/auth')
                        }
                      }}
                      className="w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-red-50 flex items-center"
                    >
                      <span className="mr-3">🚪</span>
                      Sign Out
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </nav>
  )
  } catch (error) {
    console.error('Error rendering Navigation component:', error)
    return (
      <nav className="bg-white border-b border-gray-200 sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16">
            <div className="flex items-center">
              <h1 className="text-xl font-bold text-red-600">
                Navigation Error - Check Console
              </h1>
            </div>
          </div>
        </div>
      </nav>
    )
  }
}