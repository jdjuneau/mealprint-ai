'use client'

import { useState, useEffect } from 'react'
import { useAuth } from '../lib/contexts/AuthContext'
import { useRouter } from 'next/navigation'
import { FirebaseService } from '../lib/services/firebase'
import toast from 'react-hot-toast'
import type { UserProfile } from '../types'

interface SettingsProps {
  userId: string
  userProfile: UserProfile
}

export default function Settings({ userId, userProfile }: SettingsProps) {
  const { user, updateUserProfile } = useAuth()
  const router = useRouter()
  const [notifications, setNotifications] = useState({
    dailyReminders: true,
    weeklyReports: true,
    achievementAlerts: true,
    communityUpdates: false
  })

  const [preferences, setPreferences] = useState({
    units: (userProfile?.useImperial !== false ? 'imperial' : 'metric') as 'imperial' | 'metric',
    theme: 'auto' as 'light' | 'dark' | 'auto',
    language: 'en'
  })
  const [savingUnits, setSavingUnits] = useState(false)

  useEffect(() => {
    if (userProfile) {
      setPreferences(prev => ({
        ...prev,
        units: (userProfile.useImperial !== false ? 'imperial' : 'metric') as 'imperial' | 'metric'
      }))
    }
  }, [userProfile])

  const handleUnitsChange = async (units: 'imperial' | 'metric') => {
    if (!user) return
    setSavingUnits(true)
    try {
      const useImperial = units === 'imperial'
      // Save to goals (matching Android structure)
      await FirebaseService.saveUserGoals(user.uid, { useImperial })
      // Also update userProfile for immediate UI update
      await updateUserProfile({ useImperial })
      setPreferences(prev => ({ ...prev, units }))
      toast.success('Units preference saved!')
    } catch (error) {
      console.error('Error saving units preference:', error)
      toast.error('Failed to save units preference')
    } finally {
      setSavingUnits(false)
    }
  }

  return (
    <div className="min-h-screen bg-gray-900 py-8 px-4">
      <div className="max-w-4xl mx-auto space-y-6">
        {/* Header */}
        <div>
          <h1 className="text-3xl font-bold text-white mb-2">Settings</h1>
          <p className="text-gray-400">Customize your Coachie experience</p>
        </div>

      {/* Profile Settings */}
      <div className="bg-gray-800 rounded-lg shadow-lg p-6 border border-gray-700">
        <div>
          <h2 className="text-xl font-semibold text-white mb-4">Profile</h2>

          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-300 mb-1">
                Display Name
              </label>
              <input
                type="text"
                value={userProfile.name}
                className="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded-md text-white focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-300 mb-1">
                User ID
              </label>
              <div className="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded-md text-gray-400">
                {userId}
              </div>
            </div>

            <button className="w-full px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors">
              Save Changes
            </button>
          </div>
        </div>
      </div>

      {/* Notifications */}
      <div className="bg-gray-800 rounded-lg shadow-lg p-6 border border-gray-700">
        <div>
          <h2 className="text-xl font-semibold text-white mb-4">Notifications</h2>

          <div className="space-y-4">
            {Object.entries(notifications).map(([key, value]) => (
              <div key={key} className="flex items-center justify-between">
                <div>
                  <h4 className="font-medium text-white">
                    {key.replace(/([A-Z])/g, ' $1').replace(/^./, str => str.toUpperCase())}
                  </h4>
                  <p className="text-sm text-gray-400">
                    {key === 'dailyReminders' && 'Daily habit and goal reminders'}
                    {key === 'weeklyReports' && 'Weekly progress summaries'}
                    {key === 'achievementAlerts' && 'When you unlock achievements'}
                    {key === 'communityUpdates' && 'Updates from your circles'}
                  </p>
                </div>
                <label className="relative inline-flex items-center cursor-pointer">
                  <input
                    type="checkbox"
                    checked={value}
                    onChange={(e) => setNotifications(prev => ({
                      ...prev,
                      [key]: e.target.checked
                    }))}
                    className="sr-only peer"
                  />
                  <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-primary-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary-600"></div>
                </label>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Preferences */}
      <div className="bg-gray-800 rounded-lg shadow-lg p-6 border border-gray-700">
        <div>
          <h2 className="text-xl font-semibold text-white mb-4">Preferences</h2>

          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-300 mb-2">
                Units
              </label>
              <select
                value={preferences.units}
                onChange={(e) => handleUnitsChange(e.target.value as 'imperial' | 'metric')}
                disabled={savingUnits}
                className="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded-md text-white focus:ring-2 focus:ring-primary-500 focus:border-transparent disabled:opacity-50"
              >
                <option value="imperial">Imperial (lbs, ft, °F)</option>
                <option value="metric">Metric (kg, m, °C)</option>
              </select>
              {savingUnits && <p className="text-xs text-gray-400 mt-1">Saving...</p>}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-300 mb-2">
                Theme
              </label>
              <select
                value={preferences.theme}
                onChange={(e) => setPreferences(prev => ({
                  ...prev,
                  theme: e.target.value as 'light' | 'dark' | 'auto'
                }))}
                className="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded-md text-white focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              >
                <option value="auto">Auto (follow system)</option>
                <option value="light">Light</option>
                <option value="dark">Dark</option>
              </select>
            </div>
          </div>
        </div>
      </div>

      {/* Audio & Voice Settings */}
      <div className="bg-gray-800 rounded-lg shadow-lg p-6 border border-gray-700">
        <div>
          <h2 className="text-xl font-semibold text-white mb-4">Audio & Voice</h2>

          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h4 className="font-medium text-white">Voice Responses</h4>
                <p className="text-sm text-gray-400">
                  Enable text-to-speech for AI responses
                </p>
              </div>
              <label className="relative inline-flex items-center cursor-pointer">
                <input
                  type="checkbox"
                  defaultChecked={true}
                  className="sr-only peer"
                />
                <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-primary-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary-600"></div>
              </label>
            </div>

            <div className="flex items-center justify-between">
              <div>
                <h4 className="font-medium text-white">Microphone Access</h4>
                <p className="text-sm text-gray-400">
                  Allow voice logging and voice commands
                </p>
              </div>
              <button
                onClick={async () => {
                  try {
                    const { speechRecognitionService } = await import('../lib/services/speechRecognitionService')
                    const hasPermission = await speechRecognitionService.checkMicrophonePermission()
                    if (!hasPermission) {
                      const granted = await speechRecognitionService.requestMicrophonePermission()
                      if (granted) {
                        toast.success('Microphone permission granted')
                      } else {
                        toast.error('Microphone permission denied')
                      }
                    } else {
                      toast.success('Microphone permission already granted')
                    }
                  } catch (error) {
                    console.error('Error checking microphone:', error)
                    toast.error('Failed to check microphone permission')
                  }
                }}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors text-sm"
              >
                Check Permission
              </button>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-300 mb-2">
                Voice Input Language
              </label>
              <select
                defaultValue="en-US"
                className="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded-md text-white focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              >
                <option value="en-US">English (US)</option>
                <option value="en-GB">English (UK)</option>
                <option value="es-ES">Spanish</option>
                <option value="fr-FR">French</option>
                <option value="de-DE">German</option>
              </select>
            </div>
          </div>
        </div>
      </div>

      {/* Quick Actions - All 3-dot menu items */}
      <div className="bg-gray-800 rounded-lg shadow-lg p-6 border border-gray-700">
        <div>
          <h2 className="text-xl font-semibold text-white mb-4">Quick Actions</h2>

          <div className="space-y-3">
            <button
              onClick={async () => {
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
              className="w-full text-left px-4 py-3 bg-gray-700 rounded-lg hover:bg-gray-600 transition-colors flex items-center"
            >
              <span className="mr-3 text-xl">🔄</span>
              <div className="flex-1">
                <div className="font-medium text-white">Sync Health Data</div>
                <div className="text-sm text-gray-400">Sync all connected health services</div>
              </div>
            </button>


            <button
              onClick={() => router.push('/profile')}
              className="w-full text-left px-4 py-3 bg-gray-700 rounded-lg hover:bg-gray-600 transition-colors flex items-center"
            >
              <span className="mr-3 text-xl">👤</span>
              <div className="flex-1">
                <div className="font-medium text-white">Profile</div>
                <div className="text-sm text-gray-400">View and edit your profile</div>
              </div>
            </button>

            <button
              onClick={() => router.push('/help')}
              className="w-full text-left px-4 py-3 bg-gray-700 rounded-lg hover:bg-gray-600 transition-colors flex items-center"
            >
              <span className="mr-3 text-xl">❓</span>
              <div className="flex-1">
                <div className="font-medium text-white">Help & QA</div>
                <div className="text-sm text-gray-400">Get help and answers</div>
              </div>
            </button>

            <button
              onClick={async () => {
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
              className="w-full text-left px-4 py-3 bg-gray-700 rounded-lg hover:bg-gray-600 transition-colors flex items-center"
            >
              <span className="mr-3 text-xl">📤</span>
              <div className="flex-1">
                <div className="font-medium text-white">Share App</div>
                <div className="text-sm text-gray-400">Share Coachie with others</div>
              </div>
            </button>

            <button
              onClick={() => router.push('/debug')}
              className="w-full text-left px-4 py-3 bg-gray-700 rounded-lg hover:bg-gray-600 transition-colors flex items-center"
            >
              <span className="mr-3 text-xl">🐛</span>
              <div className="flex-1">
                <div className="font-medium text-white">Debug</div>
                <div className="text-sm text-gray-400">Debug information and tools</div>
              </div>
            </button>
          </div>
        </div>
      </div>

      {/* Account Actions */}
      <div className="bg-gray-800 rounded-lg shadow-lg p-6 border border-gray-700">
        <div>
          <h2 className="text-xl font-semibold text-white mb-4">Account</h2>

          <div className="space-y-3">
            <button className="w-full px-4 py-2 border border-gray-600 rounded-lg text-gray-300 hover:bg-gray-700 transition-colors">
              Export My Data
            </button>

            <button className="w-full px-4 py-2 border border-gray-600 rounded-lg text-gray-300 hover:bg-gray-700 transition-colors">
              Privacy Settings
            </button>

            <button className="w-full px-4 py-2 border border-red-600 rounded-lg text-red-400 hover:bg-red-900/20 transition-colors">
              Delete Account
            </button>
          </div>
        </div>
      </div>
      </div>
    </div>
  )
}
