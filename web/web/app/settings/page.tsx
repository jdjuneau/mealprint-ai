'use client'

import { useAuth } from '../../lib/contexts/AuthContext'
import { useRouter } from 'next/navigation'
import { useState, useEffect } from 'react'
import { FirebaseService } from '../../lib/services/firebase'
import { db } from '../../lib/firebase'
import { doc, getDoc, setDoc, deleteDoc, collection, getDocs, query, where } from 'firebase/firestore'
import { deleteUser } from 'firebase/auth'
import { auth } from '../../lib/firebase'
import toast from 'react-hot-toast'

export default function SettingsPage() {
  const { user, userProfile, updateUserProfile } = useAuth()
  const router = useRouter()
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [notifications, setNotifications] = useState({
    nudgesEnabled: false,
    morningBriefNotifications: false,
    afternoonBriefNotifications: false,
    eveningBriefNotifications: false,
    mealPlanNotifications: false,
    mealReminders: false,
  })
  const [mealTimes, setMealTimes] = useState({
    breakfast: '08:00',
    lunch: '12:00',
    dinner: '18:00',
    snack1: '15:00',
    snack2: '21:00',
  })
  const [blueprintPreferences, setBlueprintPreferences] = useState({
    mealsPerDay: 3,
    snacksPerDay: 0,
  })
  const [showClearDataConfirm, setShowClearDataConfirm] = useState(false)
  const [showDeleteAccountConfirm, setShowDeleteAccountConfirm] = useState(false)
  const [exportingData, setExportingData] = useState(false)

  useEffect(() => {
    if (!user || !userProfile) return

    // Load notification settings
    const loadSettings = async () => {
      try {
        const userRef = doc(db, 'users', user.uid)
        const userDoc = await getDoc(userRef)
        
        if (userDoc.exists()) {
          const data = userDoc.data()
          
          setNotifications({
            nudgesEnabled: data.nudgesEnabled ?? false,
            morningBriefNotifications: data.notifications?.morningBrief ?? false,
            afternoonBriefNotifications: data.notifications?.afternoonBrief ?? false,
            eveningBriefNotifications: data.notifications?.eveningBrief ?? false,
            mealPlanNotifications: data.notifications?.mealPlan ?? false,
            mealReminders: data.notifications?.mealReminders ?? false,
          })
          
          setMealTimes({
            breakfast: data.mealTimes?.breakfast || '08:00',
            lunch: data.mealTimes?.lunch || '12:00',
            dinner: data.mealTimes?.dinner || '18:00',
            snack1: data.mealTimes?.snack1 || '15:00',
            snack2: data.mealTimes?.snack2 || '21:00',
          })
          
          setBlueprintPreferences({
            mealsPerDay: data.mealsPerDay ?? 3,
            snacksPerDay: data.snacksPerDay ?? 0,
          })
        }
      } catch (error) {
        console.error('Error loading settings:', error)
      } finally {
        setLoading(false)
      }
    }

    loadSettings()
  }, [user, userProfile])

  const handleSave = async () => {
    if (!user || !userProfile) return

    setSaving(true)
    try {
      await updateUserProfile({
        nudgesEnabled: notifications.nudgesEnabled,
        notifications: {
          morningBrief: notifications.morningBriefNotifications,
          afternoonBrief: notifications.afternoonBriefNotifications,
          eveningBrief: notifications.eveningBriefNotifications,
          mealPlan: notifications.mealPlanNotifications,
          mealReminders: notifications.mealReminders,
        },
        mealTimes,
        mealsPerDay: blueprintPreferences.mealsPerDay,
        snacksPerDay: blueprintPreferences.snacksPerDay,
      })
      toast.success('Settings saved!')
    } catch (error) {
      console.error('Error saving settings:', error)
      toast.error('Failed to save settings')
    } finally {
      setSaving(false)
    }
  }

  const handleExportData = async () => {
    if (!user) return

    setExportingData(true)
    try {
      // Collect all user data
      const userRef = doc(db, 'users', user.uid)
      const userDoc = await getDoc(userRef)
      const userData = userDoc.data()

      // Get daily logs
      const dailyRef = collection(db, 'users', user.uid, 'daily')
      const dailySnap = await getDocs(dailyRef)
      const dailyLogs = dailySnap.docs.map(doc => ({ id: doc.id, ...doc.data() }))

      // Get health logs
      const healthLogsRef = collection(db, 'healthLogs')
      const healthLogsQuery = query(healthLogsRef, where('userId', '==', user.uid))
      const healthLogsSnap = await getDocs(healthLogsQuery)
      const healthLogs = healthLogsSnap.docs.map(doc => ({ id: doc.id, ...doc.data() }))

      const exportData = {
        user: userData,
        dailyLogs,
        healthLogs,
        exportedAt: new Date().toISOString(),
      }

      // Create download
      const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `coachie-data-export-${new Date().toISOString().split('T')[0]}.json`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)

      toast.success('Data exported successfully!')
    } catch (error) {
      console.error('Error exporting data:', error)
      toast.error('Failed to export data')
    } finally {
      setExportingData(false)
    }
  }

  const handleClearData = async () => {
    if (!user) return

    try {
      // Delete all daily logs
      const dailyRef = collection(db, 'users', user.uid, 'daily')
      const dailySnap = await getDocs(dailyRef)
      for (const docSnap of dailySnap.docs) {
        await deleteDoc(doc(db, 'users', user.uid, 'daily', docSnap.id))
      }

      // Delete all health logs
      const healthLogsRef = collection(db, 'healthLogs')
      const healthLogsQuery = query(healthLogsRef, where('userId', '==', user.uid))
      const healthLogsSnap = await getDocs(healthLogsQuery)
      for (const docSnap of healthLogsSnap.docs) {
        await deleteDoc(doc(db, 'healthLogs', docSnap.id))
      }

      // Reset FTUE
      await updateUserProfile({
        ftueCompleted: false,
        currentWeight: 0,
        goalWeight: 0,
        heightCm: 0,
      })

      toast.success('All data cleared!')
      setShowClearDataConfirm(false)
      router.push('/set-goals')
    } catch (error) {
      console.error('Error clearing data:', error)
      toast.error('Failed to clear data')
    }
  }

  const handleDeleteAccount = async () => {
    if (!user) return

    try {
      // Delete all user data first
      await handleClearData()

      // Delete Firebase Auth account
      await deleteUser(user)
      
      toast.success('Account deleted successfully')
      router.push('/auth')
    } catch (error: any) {
      console.error('Error deleting account:', error)
      if (error.code === 'auth/requires-recent-login') {
        toast.error('Please sign in again to delete your account')
      } else {
        toast.error('Failed to delete account')
      }
    } finally {
      setShowDeleteAccountConfirm(false)
    }
  }

  if (loading || !user || !userProfile) {
    return <div className="min-h-screen bg-gray-900 flex items-center justify-center">
      <div className="text-white">Loading...</div>
    </div>
  }

  const gradientClass = userProfile.gender === 'male'
    ? 'bg-coachie-gradient-male'
    : userProfile.gender === 'female'
    ? 'bg-coachie-gradient-female'
    : 'bg-coachie-gradient'

  return (
    <div className={`min-h-screen ${gradientClass}`}>
      <div className="max-w-4xl mx-auto py-8 px-4">
        <button
          onClick={() => router.back()}
          className="mb-4 text-white/90 hover:text-white flex items-center gap-2"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
          </svg>
          Back
        </button>

        <div className="bg-white/10 backdrop-blur-sm rounded-lg shadow-lg p-6 border border-white/20 space-y-6">
          <h1 className="text-2xl font-bold text-white mb-6">Settings</h1>

          {/* Notifications Section */}
          <div className="bg-white/5 rounded-lg p-4 border border-white/10">
            <h2 className="text-lg font-semibold text-white mb-4">Notifications</h2>
            <div className="space-y-4">
              <label className="flex items-center justify-between">
                <div>
                  <div className="text-white font-medium">Daily Nudges</div>
                  <div className="text-sm text-white/70">Receive reminders to log your habits</div>
                </div>
                <input
                  type="checkbox"
                  checked={notifications.nudgesEnabled}
                  onChange={(e) => setNotifications({ ...notifications, nudgesEnabled: e.target.checked })}
                  className="w-5 h-5 text-blue-600 rounded"
                />
              </label>

              <label className="flex items-center justify-between">
                <div>
                  <div className="text-white font-medium">Morning Brief</div>
                  <div className="text-sm text-white/70">Receive morning brief notifications (9 AM)</div>
                </div>
                <input
                  type="checkbox"
                  checked={notifications.morningBriefNotifications}
                  onChange={(e) => setNotifications({ ...notifications, morningBriefNotifications: e.target.checked })}
                  className="w-5 h-5 text-blue-600 rounded"
                />
              </label>

              <label className="flex items-center justify-between">
                <div>
                  <div className="text-white font-medium">Afternoon Brief</div>
                  <div className="text-sm text-white/70">Receive afternoon brief notifications (2 PM)</div>
                </div>
                <input
                  type="checkbox"
                  checked={notifications.afternoonBriefNotifications}
                  onChange={(e) => setNotifications({ ...notifications, afternoonBriefNotifications: e.target.checked })}
                  className="w-5 h-5 text-blue-600 rounded"
                />
              </label>

              <label className="flex items-center justify-between">
                <div>
                  <div className="text-white font-medium">Evening Brief</div>
                  <div className="text-sm text-white/70">Receive evening brief notifications (6 PM)</div>
                </div>
                <input
                  type="checkbox"
                  checked={notifications.eveningBriefNotifications}
                  onChange={(e) => setNotifications({ ...notifications, eveningBriefNotifications: e.target.checked })}
                  className="w-5 h-5 text-blue-600 rounded"
                />
              </label>

              <label className="flex items-center justify-between">
                <div>
                  <div className="text-white font-medium">Weekly Blueprint Sunday Alert</div>
                  <div className="text-sm text-white/70">Get notified when your weekly meal plan is ready (Sundays)</div>
                </div>
                <input
                  type="checkbox"
                  checked={notifications.mealPlanNotifications}
                  onChange={(e) => setNotifications({ ...notifications, mealPlanNotifications: e.target.checked })}
                  className="w-5 h-5 text-blue-600 rounded"
                />
              </label>

              <label className="flex items-center justify-between">
                <div>
                  <div className="text-white font-medium">Daily Meal Reminders</div>
                  <div className="text-sm text-white/70">Get reminders at your scheduled meal times</div>
                </div>
                <input
                  type="checkbox"
                  checked={notifications.mealReminders}
                  onChange={(e) => setNotifications({ ...notifications, mealReminders: e.target.checked })}
                  className="w-5 h-5 text-blue-600 rounded"
                />
              </label>
            </div>

            {/* Meal Times (only show if meal reminders enabled) */}
            {notifications.mealReminders && (
              <div className="mt-6 pt-6 border-t border-white/20">
                <h3 className="text-md font-semibold text-white mb-4">Meal Times</h3>
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="text-white">Breakfast</span>
                    <input
                      type="time"
                      value={mealTimes.breakfast}
                      onChange={(e) => setMealTimes({ ...mealTimes, breakfast: e.target.value })}
                      className="px-3 py-1 bg-white/10 border border-white/20 rounded text-white"
                    />
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-white">Lunch</span>
                    <input
                      type="time"
                      value={mealTimes.lunch}
                      onChange={(e) => setMealTimes({ ...mealTimes, lunch: e.target.value })}
                      className="px-3 py-1 bg-white/10 border border-white/20 rounded text-white"
                    />
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-white">Dinner</span>
                    <input
                      type="time"
                      value={mealTimes.dinner}
                      onChange={(e) => setMealTimes({ ...mealTimes, dinner: e.target.value })}
                      className="px-3 py-1 bg-white/10 border border-white/20 rounded text-white"
                    />
                  </div>
                  {blueprintPreferences.snacksPerDay >= 1 && (
                    <div className="flex items-center justify-between">
                      <span className="text-white">Snack 1</span>
                      <input
                        type="time"
                        value={mealTimes.snack1}
                        onChange={(e) => setMealTimes({ ...mealTimes, snack1: e.target.value })}
                        className="px-3 py-1 bg-white/10 border border-white/20 rounded text-white"
                      />
                    </div>
                  )}
                  {blueprintPreferences.snacksPerDay >= 2 && (
                    <div className="flex items-center justify-between">
                      <span className="text-white">Snack 2</span>
                      <input
                        type="time"
                        value={mealTimes.snack2}
                        onChange={(e) => setMealTimes({ ...mealTimes, snack2: e.target.value })}
                        className="px-3 py-1 bg-white/10 border border-white/20 rounded text-white"
                      />
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>

          {/* Weekly Blueprint Preferences */}
          <div className="bg-white/5 rounded-lg p-4 border border-white/10">
            <div className="flex items-center gap-3 mb-4">
              <span className="text-2xl">📋</span>
              <div>
                <h2 className="text-lg font-semibold text-white">Weekly Blueprint Preferences</h2>
                <p className="text-sm text-white/70">Customize your weekly shopping list generation</p>
              </div>
            </div>

            <div className="space-y-4">
              <div>
                <div className="text-white mb-2">Meals per day</div>
                <div className="flex gap-2">
                  {[2, 3, 4].map((count) => (
                    <button
                      key={count}
                      onClick={() => setBlueprintPreferences({ ...blueprintPreferences, mealsPerDay: count })}
                      className={`flex-1 px-4 py-2 rounded ${
                        blueprintPreferences.mealsPerDay === count
                          ? 'bg-white/20 text-white border-2 border-white/40'
                          : 'bg-white/10 text-white/70 border border-white/20'
                      }`}
                    >
                      {count}
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <div className="text-white mb-2">Snacks per day</div>
                <div className="flex gap-2">
                  {[0, 1, 2].map((count) => (
                    <button
                      key={count}
                      onClick={() => setBlueprintPreferences({ ...blueprintPreferences, snacksPerDay: count })}
                      className={`flex-1 px-4 py-2 rounded ${
                        blueprintPreferences.snacksPerDay === count
                          ? 'bg-white/20 text-white border-2 border-white/40'
                          : 'bg-white/10 text-white/70 border border-white/20'
                      }`}
                    >
                      {count}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </div>

          {/* Privacy & Legal */}
          <div className="bg-white/5 rounded-lg p-4 border border-white/10">
            <h2 className="text-lg font-semibold text-white mb-4">Privacy & Legal</h2>
            <div className="space-y-3">
              <button
                onClick={() => window.open('https://playspace.games/coachie-privacy-policy', '_blank')}
                className="w-full text-left px-4 py-3 bg-white/10 rounded-lg hover:bg-white/20 transition-colors flex items-center justify-between"
              >
                <span className="text-white">Privacy Policy</span>
                <svg className="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                </svg>
              </button>

              <button
                onClick={() => window.open('https://playspace.games/coachie-terms-of-service', '_blank')}
                className="w-full text-left px-4 py-3 bg-white/10 rounded-lg hover:bg-white/20 transition-colors flex items-center justify-between"
              >
                <span className="text-white">Terms of Service</span>
                <svg className="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                </svg>
              </button>

              <button
                onClick={handleExportData}
                disabled={exportingData}
                className="w-full text-left px-4 py-3 bg-white/10 rounded-lg hover:bg-white/20 transition-colors flex items-center justify-between disabled:opacity-50"
              >
                <div>
                  <div className="text-white">Export My Data</div>
                  <div className="text-sm text-white/70">Download all your data (GDPR/CCPA)</div>
                </div>
                {exportingData ? (
                  <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-white"></div>
                ) : (
                  <svg className="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                  </svg>
                )}
              </button>
            </div>
          </div>

          {/* Account Management */}
          <div className="bg-yellow-500/20 rounded-lg p-4 border border-yellow-500/40">
            <h2 className="text-lg font-semibold text-yellow-200 mb-2">Account Management</h2>
            <p className="text-sm text-yellow-200/80 mb-4">
              Clear all your logged data, habits, and progress. Your account will remain active.
            </p>
            <button
              onClick={() => setShowClearDataConfirm(true)}
              className="w-full px-4 py-2 bg-yellow-500/30 text-yellow-200 rounded-lg hover:bg-yellow-500/40 transition-colors font-medium"
            >
              Clear All Data
            </button>
          </div>

          {/* Delete Account */}
          <div className="bg-red-500/20 rounded-lg p-4 border border-red-500/40">
            <h2 className="text-lg font-semibold text-red-200 mb-2">Delete Account</h2>
            <p className="text-sm text-red-200/80 mb-4">
              Permanently delete your account and all associated data. This action cannot be undone.
            </p>
            <button
              onClick={() => setShowDeleteAccountConfirm(true)}
              className="w-full px-4 py-2 bg-red-500/30 text-red-200 rounded-lg hover:bg-red-500/40 transition-colors font-medium"
            >
              Delete Account
            </button>
          </div>

          {/* Save Button */}
          <div className="flex gap-4 pt-4">
            <button
              onClick={() => router.back()}
              className="flex-1 px-4 py-2 border border-white/30 rounded-lg text-white hover:bg-white/10 transition-colors"
            >
              Cancel
            </button>
            <button
              onClick={handleSave}
              disabled={saving}
              className="flex-1 px-4 py-2 bg-white/20 text-white rounded-lg hover:bg-white/30 transition-colors disabled:opacity-50 font-medium"
            >
              {saving ? 'Saving...' : 'Save Settings'}
            </button>
          </div>
        </div>
      </div>

      {/* Clear Data Confirmation Dialog */}
      {showClearDataConfirm && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-gray-800 rounded-lg p-6 max-w-md mx-4 border border-gray-700">
            <h3 className="text-xl font-bold text-white mb-4">Clear All Data</h3>
            <p className="text-gray-300 mb-6">
              Are you sure you want to clear all your data? This will delete:
              <br />• All health logs (meals, workouts, water, sleep)
              <br />• All daily logs and progress
              <br />• All recipes and saved meals
              <br />• All habits and completions
              <br />• All weekly blueprints
              <br />
              <br />
              Your account and profile settings will remain. This action cannot be undone.
            </p>
            <div className="flex gap-4">
              <button
                onClick={() => setShowClearDataConfirm(false)}
                className="flex-1 px-4 py-2 border border-gray-600 rounded-lg text-gray-300 hover:bg-gray-700"
              >
                Cancel
              </button>
              <button
                onClick={handleClearData}
                className="flex-1 px-4 py-2 bg-yellow-600 text-white rounded-lg hover:bg-yellow-700"
              >
                Clear Data
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Account Confirmation Dialog */}
      {showDeleteAccountConfirm && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-gray-800 rounded-lg p-6 max-w-md mx-4 border border-red-500/50">
            <h3 className="text-xl font-bold text-red-400 mb-4">Delete Account</h3>
            <p className="text-gray-300 mb-6">
              Are you absolutely sure you want to delete your account?
              <br />
              <br />
              This will permanently delete:
              <br />• Your account and profile
              <br />• All your data and progress
              <br />• All your habits and logs
              <br />• Your account from Firebase
              <br />
              <br />
              This action CANNOT be undone. You will need to create a new account to use Coachie again.
            </p>
            <div className="flex gap-4">
              <button
                onClick={() => setShowDeleteAccountConfirm(false)}
                className="flex-1 px-4 py-2 border border-gray-600 rounded-lg text-gray-300 hover:bg-gray-700"
              >
                Cancel
              </button>
              <button
                onClick={handleDeleteAccount}
                className="flex-1 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700"
              >
                Delete Account
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
