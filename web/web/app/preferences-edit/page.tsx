'use client'

import { useAuth } from '../../lib/contexts/AuthContext'
import { useRouter } from 'next/navigation'
import { useState, useEffect } from 'react'
import LoadingScreen from '../../components/LoadingScreen'
import toast from 'react-hot-toast'
import { FirebaseService } from '../../lib/services/firebase'

export default function PreferencesEditPage() {
  const { user, userProfile, updateUserProfile } = useAuth()
  const router = useRouter()
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [formData, setFormData] = useState({
    dietaryPreference: 'balanced',
    mealsPerDay: 3,
    snacksPerDay: 2,
    nudgesEnabled: true,
    useImperial: true,
  })

  useEffect(() => {
    if (!user || !userProfile) {
      router.push('/auth')
    } else {
      setFormData({
        dietaryPreference: userProfile.dietaryPreference || 'balanced',
        mealsPerDay: userProfile.mealsPerDay || 3,
        snacksPerDay: userProfile.snacksPerDay || 2,
        nudgesEnabled: userProfile.nudgesEnabled ?? true,
        useImperial: userProfile.useImperial !== false, // Default to true (imperial)
      })
      setLoading(false)
    }
  }, [user, userProfile, router])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!user) return

    setSaving(true)
    try {
      // Save useImperial to goals (matching Android structure)
      await FirebaseService.saveUserGoals(user.uid, { useImperial: formData.useImperial })
      // Update userProfile
      await updateUserProfile({
        dietaryPreference: formData.dietaryPreference,
        mealsPerDay: formData.mealsPerDay,
        snacksPerDay: formData.snacksPerDay,
        nudgesEnabled: formData.nudgesEnabled,
        useImperial: formData.useImperial,
      })
      toast.success('Preferences updated!')
      router.push('/profile')
    } catch (error) {
      console.error('Error updating preferences:', error)
      toast.error('Failed to update')
    } finally {
      setSaving(false)
    }
  }

  if (loading || !user || !userProfile) {
    return <LoadingScreen />
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-2xl mx-auto py-8 px-4">
        <div className="bg-white rounded-lg shadow-lg p-6">
          <h1 className="text-2xl font-bold text-gray-900 mb-6">Edit Preferences</h1>

          <form onSubmit={handleSubmit} className="space-y-6">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Dietary Preference
              </label>
              <select
                value={formData.dietaryPreference}
                onChange={(e) => setFormData({ ...formData, dietaryPreference: e.target.value })}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
              >
                <option value="balanced">Balanced</option>
                <option value="vegetarian">Vegetarian</option>
                <option value="vegan">Vegan</option>
                <option value="keto">Keto</option>
                <option value="paleo">Paleo</option>
              </select>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Meals Per Day</label>
                <input
                  type="number"
                  value={formData.mealsPerDay}
                  onChange={(e) => setFormData({ ...formData, mealsPerDay: parseInt(e.target.value) || 3 })}
                  min="2"
                  max="4"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Snacks Per Day</label>
                <input
                  type="number"
                  value={formData.snacksPerDay}
                  onChange={(e) => setFormData({ ...formData, snacksPerDay: parseInt(e.target.value) || 2 })}
                  min="0"
                  max="3"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
                />
              </div>
            </div>

            <div>
              <label className="flex items-center">
                <input
                  type="checkbox"
                  checked={formData.nudgesEnabled}
                  onChange={(e) => setFormData({ ...formData, nudgesEnabled: e.target.checked })}
                  className="mr-2"
                />
                <span className="text-gray-700">Enable nudges and reminders</span>
              </label>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Measurement Units
              </label>
              <div className="flex gap-4">
                <label className="flex items-center">
                  <input
                    type="radio"
                    name="units"
                    checked={formData.useImperial}
                    onChange={() => setFormData({ ...formData, useImperial: true })}
                    className="mr-2"
                  />
                  <span className="text-gray-700">Imperial (lbs, oz, ft, °F)</span>
                </label>
                <label className="flex items-center">
                  <input
                    type="radio"
                    name="units"
                    checked={!formData.useImperial}
                    onChange={() => setFormData({ ...formData, useImperial: false })}
                    className="mr-2"
                  />
                  <span className="text-gray-700">Metric (kg, g, m, °C)</span>
                </label>
              </div>
            </div>

            <div className="flex gap-4">
              <button
                type="button"
                onClick={() => router.back()}
                className="flex-1 px-4 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={saving}
                className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
              >
                {saving ? 'Saving...' : 'Save Preferences'}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}
