'use client'

import { useAuth } from '../../lib/contexts/AuthContext'
import { useRouter } from 'next/navigation'
import { useState, useEffect } from 'react'
import { FirebaseService } from '../../lib/services/firebase'
import LoadingScreen from '../../components/LoadingScreen'
import toast from 'react-hot-toast'

const DIETARY_PREFERENCES = [
  { id: 'balanced', title: 'Balanced' },
  { id: 'vegetarian', title: 'Vegetarian' },
  { id: 'vegan', title: 'Vegan' },
  { id: 'keto', title: 'Keto' },
  { id: 'paleo', title: 'Paleo' },
  { id: 'mediterranean', title: 'Mediterranean' },
  { id: 'low_carb', title: 'Low Carb' },
  { id: 'low_fat', title: 'Low Fat' },
  { id: 'high_protein', title: 'High Protein' },
  { id: 'pescatarian', title: 'Pescatarian' },
  { id: 'carnivore', title: 'Carnivore' },
]

export default function DietaryPreferencesEditPage() {
  const { user, userProfile, updateUserProfile } = useAuth()
  const router = useRouter()
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [dietaryPreference, setDietaryPreference] = useState('balanced')

  useEffect(() => {
    if (!user || !userProfile) {
      router.push('/auth')
    } else {
      setDietaryPreference(userProfile.dietaryPreference || 'balanced')
      setLoading(false)
    }
  }, [user, userProfile, router])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!user) return

    setSaving(true)
    try {
      await updateUserProfile({
        dietaryPreference: dietaryPreference,
      })
      toast.success('Dietary preferences updated!')
      router.push('/profile')
    } catch (error) {
      console.error('Error updating dietary preferences:', error)
      toast.error('Failed to update dietary preferences')
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
          <h1 className="text-2xl font-bold text-gray-900 mb-6">Edit Dietary Preferences</h1>

          <form onSubmit={handleSubmit} className="space-y-6">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Dietary Preference *
              </label>
              <select
                value={dietaryPreference}
                onChange={(e) => setDietaryPreference(e.target.value)}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
                required
              >
                {DIETARY_PREFERENCES.map((pref) => (
                  <option key={pref.id} value={pref.id}>
                    {pref.title}
                  </option>
                ))}
              </select>
            </div>

            <button
              type="submit"
              disabled={saving}
              className="w-full px-4 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 font-medium"
            >
              {saving ? 'Saving...' : 'Save Preferences'}
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}

