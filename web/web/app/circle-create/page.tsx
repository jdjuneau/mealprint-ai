'use client'

import { useAuth } from '../../lib/contexts/AuthContext'
import { useRouter } from 'next/navigation'
import { useState, useEffect } from 'react'
import LoadingScreen from '../../components/LoadingScreen'
import toast from 'react-hot-toast'

export default function CircleCreatePage() {
  const { user, userProfile } = useAuth()
  const router = useRouter()
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    goal: '',
    isPrivate: false,
  })

  useEffect(() => {
    if (!user) {
      router.push('/auth')
    } else {
      setLoading(false)
    }
  }, [user, router])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!user) return

    setSaving(true)
    try {
      const CirclesService = (await import('../../lib/services/circlesService')).default
      const circlesService = CirclesService.getInstance()
      const circleId = await circlesService.createCircle(user.uid, {
        name: formData.name,
        description: formData.description,
        goal: formData.goal,
        isPrivate: formData.isPrivate,
      })
      toast.success('Circle created!')
      router.push(`/circle-detail/${circleId}`)
    } catch (error) {
      console.error('Error creating circle:', error)
      toast.error('Failed to create circle')
    } finally {
      setSaving(false)
    }
  }

  if (loading || !user || !userProfile) {
    return <LoadingScreen />
  }

  const gradientClass = userProfile.gender === 'male'
    ? 'bg-coachie-gradient-male'
    : userProfile.gender === 'female'
    ? 'bg-coachie-gradient-female'
    : 'bg-coachie-gradient'

  return (
    <div className={`min-h-screen ${gradientClass}`}>
      <div className="max-w-2xl mx-auto py-8 px-4">
        <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6">
          <h1 className="text-2xl font-bold text-white mb-6">Create Circle</h1>

          <form onSubmit={handleSubmit} className="space-y-6">
            <div>
              <label className="block text-sm font-medium text-white mb-2">Circle Name *</label>
              <input
                type="text"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                className="w-full px-4 py-2 border border-white/30 rounded-lg bg-white/10 text-white placeholder-white/50 focus:ring-2 focus:ring-blue-500"
                required
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-white mb-2">Description</label>
              <textarea
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                rows={3}
                className="w-full px-4 py-2 border border-white/30 rounded-lg bg-white/10 text-white placeholder-white/50 focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-white mb-2">Goal</label>
              <input
                type="text"
                value={formData.goal}
                onChange={(e) => setFormData({ ...formData, goal: e.target.value })}
                placeholder="e.g., Lose weight, Build muscle, Run marathon"
                className="w-full px-4 py-2 border border-white/30 rounded-lg bg-white/10 text-white placeholder-white/50 focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div>
              <label className="flex items-center">
                <input
                  type="checkbox"
                  checked={formData.isPrivate}
                  onChange={(e) => setFormData({ ...formData, isPrivate: e.target.checked })}
                  className="mr-2"
                />
                <span className="text-sm text-white">Private circle (invite only)</span>
              </label>
            </div>

            <div className="flex gap-4">
              <button
                type="button"
                onClick={() => router.back()}
                className="flex-1 px-4 py-2 border border-white/30 rounded-lg text-white hover:bg-white/20"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={saving}
                className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
              >
                {saving ? 'Creating...' : 'Create Circle'}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}
