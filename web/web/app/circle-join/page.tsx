'use client'

import { useAuth } from '../../lib/contexts/AuthContext'
import { useRouter } from 'next/navigation'
import { useState, useEffect } from 'react'
import LoadingScreen from '../../components/LoadingScreen'
import toast from 'react-hot-toast'
import { collection, query, where, orderBy, getDocs } from 'firebase/firestore'
import { db } from '../../lib/firebase'

interface Circle {
  id: string
  name: string
  description: string
  memberCount: number
  goal: string
}

export default function CircleJoinPage() {
  const { user, userProfile } = useAuth()
  const router = useRouter()
  const [loading, setLoading] = useState(true)
  const [circles, setCircles] = useState<Circle[]>([])

  useEffect(() => {
    if (!user) {
      router.push('/auth')
    } else {
      loadCircles()
    }
  }, [user, router])

  const loadCircles = async () => {
    if (!user) return
    try {
      const CirclesService = (await import('../../lib/services/circlesService')).default
      const circlesService = CirclesService.getInstance()
      const userCircles = await circlesService.getUserCircles(user.uid)
      
      // Get all public circles (excluding user's existing circles)
      const circlesRef = collection(db, 'circles')
      const publicCirclesQuery = query(
        circlesRef,
        where('isPrivate', '==', false),
        orderBy('createdAt', 'desc')
      )
      const publicCirclesSnap = await getDocs(publicCirclesQuery)
      
      const allCircles = publicCirclesSnap.docs.map((doc) => {
        const data = doc.data()
        return {
          id: doc.id,
          name: data.name,
          description: data.description || '',
          memberCount: (data.memberIds as string[])?.length || 0,
          goal: data.goal || '',
        }
      })

      // Filter out circles user is already a member of
      const userCircleIds = new Set(userCircles.map((c) => c.id))
      const availableCircles = allCircles.filter((c) => !userCircleIds.has(c.id))
      
      setCircles(availableCircles)
    } catch (error) {
      console.error('Error loading circles:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleJoin = async (circleId: string) => {
    if (!user) return
    try {
      const CirclesService = (await import('../../lib/services/circlesService')).default
      const circlesService = CirclesService.getInstance()
      await circlesService.joinCircle(user.uid, circleId)
      toast.success('Joined circle!')
      router.push(`/circle-detail/${circleId}`)
    } catch (error) {
      console.error('Error joining circle:', error)
      toast.error('Failed to join circle')
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
      <div className="max-w-4xl mx-auto py-8 px-4">
        <h1 className="text-2xl font-bold text-white mb-6">Join a Circle</h1>

        {circles.length === 0 ? (
          <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-12 text-center">
            <div className="text-4xl mb-4">👥</div>
            <h2 className="text-xl font-semibold text-white mb-2">No circles available</h2>
            <p className="text-white/80 mb-6">Create your own circle or wait for invitations</p>
            <button
              onClick={() => router.push('/circle-create')}
              className="px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
            >
              Create Circle
            </button>
          </div>
        ) : (
          <div className="space-y-4">
            {circles.map((circle) => (
              <div
                key={circle.id}
                className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6 flex items-center justify-between"
              >
                <div className="flex-1">
                  <h3 className="font-semibold text-white">{circle.name}</h3>
                  <p className="text-sm text-white/80 mt-1">{circle.description}</p>
                  <div className="flex gap-4 mt-2 text-sm text-white/70">
                    <span>{circle.memberCount} members</span>
                    <span>Goal: {circle.goal}</span>
                  </div>
                </div>
                <button
                  onClick={() => handleJoin(circle.id)}
                  className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
                >
                  Join
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
