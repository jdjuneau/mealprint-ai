'use client'

import { useAuth } from '../../lib/contexts/AuthContext'
import { useRouter } from 'next/navigation'
import { useState, useEffect } from 'react'
import LoadingScreen from '../../components/LoadingScreen'

interface Friend {
  id: string
  name: string
  username?: string
  status: 'friend' | 'pending' | 'requested'
}

export default function FriendsListPage() {
  const { user, userProfile } = useAuth()
  const router = useRouter()
  const [loading, setLoading] = useState(true)
  const [friends, setFriends] = useState<Friend[]>([])

  useEffect(() => {
    if (!user) {
      router.push('/auth')
    } else {
      loadFriends()
    }
  }, [user, router])

  const loadFriends = async () => {
    if (!user) return
    try {
      const FriendsService = (await import('../../lib/services/friendsService')).default
      const friendsService = FriendsService.getInstance()
      const loadedFriends = await friendsService.getFriends(user.uid)
      setFriends(loadedFriends.map((f) => ({
        id: f.friendId,
        name: f.friendName,
        username: f.friendUsername,
        status: 'friend' as const,
      })))
    } catch (error) {
      console.error('Error loading friends:', error)
    } finally {
      setLoading(false)
    }
  }

  if (loading || !user) {
    return <LoadingScreen />
  }

  const gradientClass = userProfile?.gender === 'male'
    ? 'bg-coachie-gradient-male'
    : userProfile?.gender === 'female'
    ? 'bg-coachie-gradient-female'
    : 'bg-coachie-gradient'

  return (
    <div className={`min-h-screen ${gradientClass}`}>
      <div className="max-w-4xl mx-auto py-8 px-4">
        <div className="flex items-center justify-between mb-6">
          <h1 className="text-2xl font-bold text-white">Friends</h1>
          <button
            onClick={() => router.push('/user-search')}
            className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
          >
            + Find Friends
          </button>
        </div>

        {friends.length === 0 ? (
          <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-12 text-center">
            <div className="text-4xl mb-4">👫</div>
            <h2 className="text-xl font-semibold text-white mb-2">No friends yet</h2>
            <p className="text-white/80 mb-6">Connect with others to share your fitness journey</p>
            <button
              onClick={() => router.push('/user-search')}
              className="px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
            >
              Find Friends
            </button>
          </div>
        ) : (
          <div className="space-y-4">
            {friends.map((friend) => (
              <div
                key={friend.id}
                className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6 flex items-center justify-between"
              >
                <div className="flex items-center gap-4">
                  <div className="w-12 h-12 bg-white/20 rounded-full flex items-center justify-center">
                    <span className="text-white font-semibold">
                      {friend.name.charAt(0).toUpperCase()}
                    </span>
                  </div>
                  <div>
                    <h3 className="font-semibold text-white">{friend.name}</h3>
                    {friend.username && (
                      <p className="text-sm text-white/70">@{friend.username}</p>
                    )}
                  </div>
                </div>
                <button
                  onClick={() => router.push(`/messaging?userId=${friend.id}`)}
                  className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
                >
                  Message
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
