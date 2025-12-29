'use client'

import { useAuth } from '../../../lib/contexts/AuthContext'
import { useRouter, useParams } from 'next/navigation'
import { useState, useEffect } from 'react'
import LoadingScreen from '../../../components/LoadingScreen'

export default function CircleDetailPage() {
  const { user } = useAuth()
  const router = useRouter()
  const params = useParams()
  const circleId = params.circleId as string
  const [loading, setLoading] = useState(true)
  const [circle, setCircle] = useState<any>(null)

  useEffect(() => {
    if (!user) {
      router.push('/auth')
    } else {
      loadCircle()
    }
  }, [user, router, circleId])

  const loadCircle = async () => {
    if (!user || !circleId) return
    try {
      const CirclesService = (await import('../../../lib/services/circlesService')).default
      const circlesService = CirclesService.getInstance()
      const loadedCircle = await circlesService.getCircle(circleId)
      setCircle(loadedCircle)
    } catch (error) {
      console.error('Error loading circle:', error)
    } finally {
      setLoading(false)
    }
  }

  if (loading || !user) {
    return <LoadingScreen />
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-4xl mx-auto py-8 px-4">
        {circle ? (
          <>
            <div className="bg-white rounded-lg shadow-sm p-6 mb-6">
              <h1 className="text-2xl font-bold text-gray-900 mb-2">{circle.name}</h1>
              <p className="text-gray-600">{circle.description}</p>
            </div>

            <div className="bg-white rounded-lg shadow-sm p-6">
              <h2 className="text-lg font-semibold text-gray-900 mb-4">Members</h2>
              <p className="text-gray-600">Member list coming soon</p>
            </div>
          </>
        ) : (
          <div className="bg-white rounded-lg shadow-sm p-12 text-center">
            <p className="text-gray-600">Circle not found</p>
          </div>
        )}
      </div>
    </div>
  )
}
