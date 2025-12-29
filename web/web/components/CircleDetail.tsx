'use client'

import { useState, useEffect } from 'react'
import { useAuth } from '../lib/contexts/AuthContext'
import { collection, doc, getDoc, query, where, getDocs, addDoc, updateDoc, Timestamp } from 'firebase/firestore'
import { db } from '../lib/firebase'
import CoachieCard from './ui/CoachieCard'
import CoachieButton from './ui/CoachieButton'

interface Circle {
  id: string
  name: string
  goal: string
  members: string[]
  streak: number
  createdBy: string
}

interface CircleCheckIn {
  userId: string
  energy: number
  note?: string
  timestamp: Date
}

interface CirclePost {
  id: string
  authorId: string
  authorName: string
  content: string
  likes: string[]
  comments: CircleComment[]
  timestamp: Date
}

interface CircleComment {
  id: string
  authorId: string
  authorName: string
  content: string
  timestamp: Date
}

export default function CircleDetail({ circleId }: { circleId: string }) {
  const { user } = useAuth()
  const [circle, setCircle] = useState<Circle | null>(null)
  const [checkIns, setCheckIns] = useState<CircleCheckIn[]>([])
  const [posts, setPosts] = useState<CirclePost[]>([])
  const [userCheckIn, setUserCheckIn] = useState<CircleCheckIn | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [showCheckInDialog, setShowCheckInDialog] = useState(false)
  const [energyLevel, setEnergyLevel] = useState(5)
  const [checkInNote, setCheckInNote] = useState('')

  useEffect(() => {
    if (user && circleId) {
      loadCircleData()
    }
  }, [user, circleId])

  const loadCircleData = async () => {
    if (!user || !circleId) return

    try {
      setIsLoading(true)
      
      // Load circle
      const circleDoc = await getDoc(doc(db, 'circles', circleId))
      if (circleDoc.exists()) {
        const data = circleDoc.data()
        setCircle({
          id: circleId,
          name: data.name || '',
          goal: data.goal || '',
          members: data.members || [],
          streak: data.streak || 0,
          createdBy: data.createdBy || ''
        })
      }

      // Load today's check-ins
      const today = new Date().toISOString().split('T')[0]
      const checkInsRef = collection(db, 'circles', circleId, 'checkins', today, 'entries')
      const checkInsSnapshot = await getDocs(checkInsRef)
      const loadedCheckIns: CircleCheckIn[] = []
      checkInsSnapshot.forEach(doc => {
        const data = doc.data()
        if (data.userId === user.uid) {
          setUserCheckIn({
            userId: data.userId,
            energy: data.energy || 5,
            note: data.note,
            timestamp: data.timestamp?.toDate() || new Date()
          })
        }
        loadedCheckIns.push({
          userId: data.userId,
          energy: data.energy || 5,
          note: data.note,
          timestamp: data.timestamp?.toDate() || new Date()
        })
      })
      setCheckIns(loadedCheckIns)

      // Load posts
      const postsRef = collection(db, 'circles', circleId, 'posts')
      const postsSnapshot = await getDocs(postsRef)
      const loadedPosts: CirclePost[] = []
      postsSnapshot.forEach(doc => {
        const data = doc.data()
        loadedPosts.push({
          id: doc.id,
          authorId: data.authorId || '',
          authorName: data.authorName || 'Anonymous',
          content: data.content || '',
          likes: data.likes || [],
          comments: data.comments || [],
          timestamp: data.timestamp?.toDate() || new Date()
        })
      })
      setPosts(loadedPosts.sort((a, b) => b.timestamp.getTime() - a.timestamp.getTime()))
    } catch (error) {
      console.error('Error loading circle data:', error)
    } finally {
      setIsLoading(false)
    }
  }

  const submitCheckIn = async () => {
    if (!user || !circleId) return

    try {
      const today = new Date().toISOString().split('T')[0]
      const checkInRef = doc(db, 'circles', circleId, 'checkins', today, 'entries', user.uid)
      
      const checkIn: CircleCheckIn = {
        userId: user.uid,
        energy: energyLevel,
        note: checkInNote || undefined,
        timestamp: new Date()
      }

      await updateDoc(checkInRef, {
        userId: user.uid,
        energy: energyLevel,
        note: checkInNote || null,
        timestamp: Timestamp.now()
      })

      setUserCheckIn(checkIn)
      setCheckIns([...checkIns.filter(c => c.userId !== user.uid), checkIn])
      setShowCheckInDialog(false)
      setCheckInNote('')
    } catch (error) {
      console.error('Error submitting check-in:', error)
    }
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-center">
          <div className="text-4xl mb-4">👥</div>
          <p className="text-gray-600">Loading circle...</p>
        </div>
      </div>
    )
  }

  if (!circle) {
    return (
      <div className="max-w-4xl mx-auto">
        <CoachieCard>
          <div className="p-12 text-center">
            <div className="text-6xl mb-4">❌</div>
            <h2 className="text-xl font-semibold text-gray-900 mb-2">Circle Not Found</h2>
            <p className="text-gray-600">This circle doesn't exist or you don't have access.</p>
          </div>
        </CoachieCard>
      </div>
    )
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Circle Header */}
      <CoachieCard>
        <div className="p-6">
          <div className="flex items-start justify-between mb-4">
            <div>
              <h1 className="text-3xl font-bold text-gray-900 mb-2">{circle.name}</h1>
              <p className="text-gray-600">{circle.goal}</p>
            </div>
            <div className="text-right">
              <div className="text-2xl font-bold text-primary-600">{circle.streak}</div>
              <div className="text-sm text-gray-600">Day Streak</div>
            </div>
          </div>
          <div className="flex items-center gap-2 text-sm text-gray-600">
            <span>{circle.members.length} members</span>
          </div>
        </div>
      </CoachieCard>

      {/* Check-In Section */}
      <CoachieCard>
        <div className="p-6">
          <h2 className="text-xl font-bold mb-4">Today's Check-In</h2>
          {userCheckIn ? (
            <div className="p-4 bg-green-50 rounded-lg">
              <p className="text-green-800 font-semibold">✓ Checked in!</p>
              <p className="text-sm text-gray-600 mt-1">
                Energy level: {userCheckIn.energy}/10
              </p>
              {userCheckIn.note && (
                <p className="text-sm text-gray-600 mt-1">{userCheckIn.note}</p>
              )}
            </div>
          ) : (
            <>
              <div className="mb-4">
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Energy Level: {energyLevel}/10
                </label>
                <input
                  type="range"
                  min="1"
                  max="10"
                  value={energyLevel}
                  onChange={(e) => setEnergyLevel(parseInt(e.target.value))}
                  className="w-full"
                />
              </div>
              <textarea
                value={checkInNote}
                onChange={(e) => setCheckInNote(e.target.value)}
                placeholder="Optional note..."
                className="w-full px-3 py-2 border border-gray-300 rounded-lg mb-4"
                rows={3}
              />
              <CoachieButton onClick={submitCheckIn}>
                Submit Check-In
              </CoachieButton>
            </>
          )}
        </div>
      </CoachieCard>

      {/* Member Check-Ins */}
      {checkIns.length > 0 && (
        <CoachieCard>
          <div className="p-6">
            <h2 className="text-xl font-bold mb-4">Member Check-Ins</h2>
            <div className="space-y-3">
              {checkIns.map((checkIn, index) => (
                <div key={index} className="p-3 bg-gray-50 rounded-lg">
                  <div className="flex justify-between items-center">
                    <span className="font-medium">Member</span>
                    <span className="text-primary-600">Energy: {checkIn.energy}/10</span>
                  </div>
                  {checkIn.note && (
                    <p className="text-sm text-gray-600 mt-1">{checkIn.note}</p>
                  )}
                </div>
              ))}
            </div>
          </div>
        </CoachieCard>
      )}

      {/* Posts */}
      <CoachieCard>
        <div className="p-6">
          <div className="flex justify-between items-center mb-4">
            <h2 className="text-xl font-bold">Circle Posts</h2>
            <CoachieButton onClick={() => {}}>
              + New Post
            </CoachieButton>
          </div>
          {posts.length === 0 ? (
            <p className="text-gray-600 text-center py-8">No posts yet. Be the first to share!</p>
          ) : (
            <div className="space-y-4">
              {posts.map((post) => (
                <div key={post.id} className="p-4 bg-gray-50 rounded-lg">
                  <div className="flex justify-between items-start mb-2">
                    <div>
                      <p className="font-semibold">{post.authorName}</p>
                      <p className="text-sm text-gray-500">
                        {post.timestamp.toLocaleDateString()}
                      </p>
                    </div>
                    <span className="text-sm text-gray-500">❤️ {post.likes.length}</span>
                  </div>
                  <p className="text-gray-900">{post.content}</p>
                  {post.comments.length > 0 && (
                    <div className="mt-3 pt-3 border-t border-gray-200">
                      <p className="text-sm font-semibold mb-2">Comments ({post.comments.length})</p>
                      {post.comments.map((comment) => (
                        <div key={comment.id} className="mb-2">
                          <p className="text-sm">
                            <span className="font-semibold">{comment.authorName}:</span> {comment.content}
                          </p>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </CoachieCard>
    </div>
  )
}

