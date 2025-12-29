'use client'

import { useState, useEffect } from 'react'
import { collection, getDocs, query, where, orderBy } from 'firebase/firestore'
import { db } from '../lib/firebase'
import { useRouter } from 'next/navigation'
import CoachieCard from './ui/CoachieCard'
import CoachieButton from './ui/CoachieButton'
import type { UserProfile } from '../types'

interface Forum {
  id: string
  title: string
  description?: string
  category?: string
  postCount?: number
  isActive?: boolean
}

interface CommunityProps {
  userId: string
  userProfile: UserProfile
}

export default function Community({ userId, userProfile }: CommunityProps) {
  const router = useRouter()
  const [activeTab, setActiveTab] = useState<'feed' | 'circles' | 'challenges' | 'forums'>('forums')
  const [forums, setForums] = useState<Forum[]>([])
  const [loadingForums, setLoadingForums] = useState(true)

  // Load forums
  useEffect(() => {
    const loadForums = async () => {
      try {
        setLoadingForums(true)
        const forumsRef = collection(db, 'forums')
        const forumsQuery = query(
          forumsRef,
          where('isActive', '==', true),
          orderBy('title', 'asc')
        )
        
        try {
          const snapshot = await getDocs(forumsQuery)
          const loadedForums: Forum[] = []
          snapshot.forEach((doc) => {
            const data = doc.data()
            loadedForums.push({
              id: doc.id,
              title: data.title || 'Untitled Forum',
              description: data.description,
              category: data.category,
              postCount: data.postCount || 0,
              isActive: data.isActive !== false
            })
          })
          setForums(loadedForums)
        } catch (error: any) {
          // If query fails (no index), try without filter
          if (error.code === 'failed-precondition') {
            const allForumsSnap = await getDocs(forumsRef)
            const loadedForums: Forum[] = []
            allForumsSnap.forEach((doc) => {
              const data = doc.data()
              if (data.isActive !== false) {
                loadedForums.push({
                  id: doc.id,
                  title: data.title || 'Untitled Forum',
                  description: data.description,
                  category: data.category,
                  postCount: data.postCount || 0,
                  isActive: true
                })
              }
            })
            setForums(loadedForums.sort((a, b) => a.title.localeCompare(b.title)))
          } else {
            throw error
          }
        }
      } catch (error) {
        console.error('Error loading forums:', error)
      } finally {
        setLoadingForums(false)
      }
    }
    
    // Load forums on mount and when switching to forums tab
    loadForums()
  }, [activeTab])

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold text-gray-900 mb-2">Community</h1>
        <p className="text-gray-600">Connect with others on their health journey</p>
      </div>

      {/* Navigation */}
      <div className="flex space-x-1 bg-gray-100 p-1 rounded-lg">
        {[
          { id: 'feed' as const, label: 'Activity Feed' },
          { id: 'circles' as const, label: 'My Circles' },
          { id: 'challenges' as const, label: 'Challenges' },
          { id: 'forums' as const, label: 'Forums' }
        ].map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`px-4 py-2 rounded-md text-sm font-medium transition-all ${
              activeTab === tab.id
                ? 'bg-white text-primary-700 shadow-sm'
                : 'text-gray-600 hover:text-gray-900'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab === 'feed' && (
        <div className="space-y-6">
          {/* Share Update */}
          <CoachieCard>
            <div className="p-6">
              <div className="flex space-x-4">
                <div className="w-12 h-12 bg-primary-100 rounded-full flex items-center justify-center">
                  <span className="text-xl">👤</span>
                </div>
                <div className="flex-1">
                  <textarea
                    placeholder="Share your progress or motivation..."
                    className="w-full p-3 border border-gray-300 rounded-lg resize-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                    rows={3}
                  />
                  <div className="flex justify-between items-center mt-3">
                    <div className="flex space-x-2">
                      <button className="text-gray-500 hover:text-primary-600">📷</button>
                      <button className="text-gray-500 hover:text-primary-600">🏆</button>
                      <button className="text-gray-500 hover:text-primary-600">🎯</button>
                    </div>
                    <CoachieButton>Share</CoachieButton>
                  </div>
                </div>
              </div>
            </div>
          </CoachieCard>

          {/* Feed Items */}
          <CoachieCard>
            <div className="p-6">
              <div className="flex space-x-4 mb-4">
                <div className="w-10 h-10 bg-green-100 rounded-full flex items-center justify-center">
                  <span>🏃</span>
                </div>
                <div>
                  <h4 className="font-semibold">Sarah Johnson</h4>
                  <p className="text-sm text-gray-500">2 hours ago</p>
                </div>
              </div>
              <p className="text-gray-800 mb-4">
                Just completed my 10K run! Feeling amazing! 🏃‍♀️💪 The new running shoes are making all the difference.
              </p>
              <div className="flex space-x-4 text-sm text-gray-500">
                <button className="hover:text-primary-600">👍 12 Likes</button>
                <button className="hover:text-primary-600">💬 3 Comments</button>
                <button className="hover:text-primary-600">↗️ Share</button>
              </div>
            </div>
          </CoachieCard>
        </div>
      )}

      {activeTab === 'circles' && (
        <div className="space-y-6">
          <CoachieCard>
            <div className="p-6 text-center">
              <div className="text-6xl mb-4">👥</div>
              <h3 className="text-xl font-semibold text-gray-900 mb-2">Join a Circle</h3>
              <p className="text-gray-600 mb-4">Connect with people who share your fitness goals</p>
              <CoachieButton>Browse Circles</CoachieButton>
            </div>
          </CoachieCard>
        </div>
      )}

      {activeTab === 'challenges' && (
        <div className="space-y-6">
          <CoachieCard>
            <div className="p-6 text-center">
              <div className="text-6xl mb-4">🏆</div>
              <h3 className="text-xl font-semibold text-gray-900 mb-2">Community Challenges</h3>
              <p className="text-gray-600 mb-4">Join group challenges to stay motivated</p>
              <CoachieButton>View Challenges</CoachieButton>
            </div>
          </CoachieCard>
        </div>
      )}

      {activeTab === 'forums' && (
        <div className="space-y-6">
          {loadingForums ? (
            <CoachieCard>
              <div className="p-12 text-center">
                <div className="text-4xl mb-4">💬</div>
                <p className="text-gray-600">Loading forums...</p>
              </div>
            </CoachieCard>
          ) : forums.length === 0 ? (
            <CoachieCard>
              <div className="p-12 text-center">
                <div className="text-6xl mb-4">💬</div>
                <h3 className="text-xl font-semibold text-gray-900 mb-2">No Forums Available</h3>
                <p className="text-gray-600 mb-4">Forums will appear here once they're set up</p>
              </div>
            </CoachieCard>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {forums.map((forum) => (
                <CoachieCard
                  key={forum.id}
                  className="cursor-pointer hover:shadow-lg transition-shadow"
                  onClick={() => router.push(`/forum-detail/${forum.id}`)}
                >
                  <div className="p-6">
                    <h3 className="text-xl font-bold text-gray-900 mb-2">{forum.title}</h3>
                    {forum.description && (
                      <p className="text-gray-600 mb-3">{forum.description}</p>
                    )}
                    <div className="flex items-center justify-between text-sm text-gray-500">
                      {forum.category && (
                        <span className="px-2 py-1 bg-gray-100 rounded">{forum.category}</span>
                      )}
                      <span>{forum.postCount || 0} posts</span>
                    </div>
                  </div>
                </CoachieCard>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
