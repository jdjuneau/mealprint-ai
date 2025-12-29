'use client'

import { useState } from 'react'
import { useAuth } from '../lib/contexts/AuthContext'
import type { UserProfile } from '../types'

interface ProfileSettingsProps {
  userProfile: UserProfile
  onUpdateProfile: () => void
}

export default function ProfileSettings({ userProfile, onUpdateProfile }: ProfileSettingsProps) {
  const { updateUserProfile } = useAuth()
  const [formData, setFormData] = useState({
    name: userProfile.name,
    currentWeight: userProfile.currentWeight.toString(),
    goalWeight: userProfile.goalWeight.toString(),
    heightCm: userProfile.heightCm.toString(),
    age: userProfile.age.toString(),
    gender: userProfile.gender,
    activityLevel: userProfile.activityLevel,
  })
  const [saving, setSaving] = useState(false)

  const handleSave = async () => {
    setSaving(true)
    try {
      await updateUserProfile({
        name: formData.name,
        currentWeight: parseFloat(formData.currentWeight),
        goalWeight: parseFloat(formData.goalWeight),
        heightCm: parseInt(formData.heightCm),
        age: parseInt(formData.age),
        gender: formData.gender,
        activityLevel: formData.activityLevel,
      })
      onUpdateProfile()
    } catch (error) {
      console.error('Error updating profile:', error)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div className="text-center">
        <h1 className="text-2xl font-bold text-gray-900 mb-2">👤 Profile Settings</h1>
        <p className="text-gray-600">Update your personal information and goals</p>
      </div>

      <div className="bg-white rounded-lg shadow-sm p-6 space-y-6">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Name
            </label>
            <input
              type="text"
              value={formData.name}
              onChange={(e) => setFormData(prev => ({ ...prev, name: e.target.value }))}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Age
            </label>
            <input
              type="number"
              value={formData.age}
              onChange={(e) => setFormData(prev => ({ ...prev, age: e.target.value }))}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Gender
            </label>
            <select
              value={formData.gender}
              onChange={(e) => setFormData(prev => ({ ...prev, gender: e.target.value as 'male' | 'female' }))}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
            >
              <option value="male">Male</option>
              <option value="female">Female</option>
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Activity Level
            </label>
            <select
              value={formData.activityLevel}
              onChange={(e) => setFormData(prev => ({ ...prev, activityLevel: e.target.value as typeof formData.activityLevel }))}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
            >
              <option value="sedentary">Sedentary (little/no exercise)</option>
              <option value="lightly active">Lightly Active (1-3 days/week)</option>
              <option value="moderately active">Moderately Active (3-5 days/week)</option>
              <option value="very active">Very Active (6-7 days/week)</option>
              <option value="extremely active">Extremely Active (physical job)</option>
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Current Weight (kg)
            </label>
            <input
              type="number"
              step="0.1"
              value={formData.currentWeight}
              onChange={(e) => setFormData(prev => ({ ...prev, currentWeight: e.target.value }))}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Goal Weight (kg)
            </label>
            <input
              type="number"
              step="0.1"
              value={formData.goalWeight}
              onChange={(e) => setFormData(prev => ({ ...prev, goalWeight: e.target.value }))}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Height (cm)
            </label>
            <input
              type="number"
              value={formData.heightCm}
              onChange={(e) => setFormData(prev => ({ ...prev, heightCm: e.target.value }))}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </div>
        </div>

        <div className="bg-gray-50 rounded-lg p-4">
          <h3 className="font-medium text-gray-900 mb-2">📊 Your Stats</h3>
          <div className="grid grid-cols-2 gap-4 text-sm">
            <div>
              <span className="text-gray-600">BMI:</span>
              <span className="font-medium ml-2">
                {formData.currentWeight && formData.heightCm
                  ? (parseFloat(formData.currentWeight) / Math.pow(parseFloat(formData.heightCm) / 100, 2)).toFixed(1)
                  : 'N/A'
                }
              </span>
            </div>
            <div>
              <span className="text-gray-600">Daily Calories:</span>
              <span className="font-medium ml-2">
                {formData.currentWeight ? Math.round(parseFloat(formData.currentWeight) * 24) : 0} cal
              </span>
            </div>
          </div>
        </div>

        <button
          onClick={handleSave}
          disabled={saving}
          className="w-full bg-primary-600 text-white px-6 py-3 rounded-md hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed font-medium"
        >
          {saving ? 'Saving...' : '💾 Save Changes'}
        </button>
      </div>
    </div>
  )
}
