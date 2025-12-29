'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '../lib/contexts/AuthContext'
import { collection, query, where, getDocs, doc, getDoc, addDoc, updateDoc } from 'firebase/firestore'
import { db } from '../lib/firebase'
import CoachieCard from './ui/CoachieCard'
import CoachieButton from './ui/CoachieButton'

interface Friend {
  id: string
  name: string
  username?: string
  photoUrl?: string
  status: 'friend' | 'pending' | 'requested'
}

export default function FriendsList() {
  const { user } = useAuth()
  const router = useRouter()
  const [friends, setFriends] = useState<Friend[]>([])
  const [pendingRequests, setPendingRequests] = useState<Friend[]>([])
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    if (user) {
      loadFriends()
    }
  }, [user])

  const loadFriends = async () => {
    if (!user) return

    try {
      setIsLoading(true)
      
      // Use friendsService which handles both Android and Web formats
      const FriendsService = (await import('../lib/services/friendsService')).default
      const friendsService = FriendsService.getInstance()
      const loadedFriends = await friendsService.getFriends(user.uid)
      
      setFriends(loadedFriends.map((f) => ({
        id: f.friendId,
        name: f.friendName,
        username: f.friendUsername,
        photoUrl: undefined, // TODO: Load photoUrl from profile if needed
        status: 'friend' as const,
      })))
    } catch (error) {
      console.error('Error loading friends:', error)
    } finally {
      setIsLoading(false)
    }
  }

  const sendFriendRequest = async (userId: string) => {
    if (!user) return

    try {
      // TODO: Implement friend request logic
      alert('Friend request sent!')
    } catch (error) {
      console.error('Error sending friend request:', error)
    }
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-center">
          <div className="text-4xl mb-4">👥</div>
          <p className="text-gray-600">Loading friends...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-3xl font-bold text-gray-900">Friends</h1>
        <CoachieButton onClick={() => router.push('/user-search')}>
          + Find Friends
        </CoachieButton>
      </div>

      {friends.length === 0 ? (
        <CoachieCard>
          <div className="p-12 text-center">
            <div className="text-6xl mb-4">👥</div>
            <h2 className="text-xl font-semibold text-gray-900 mb-2">No Friends Yet</h2>
            <p className="text-gray-600 mb-6">
              Add friends to build your support network and stay accountable together.
            </p>
            <CoachieButton onClick={() => router.push('/user-search')}>
              Find Friends
            </CoachieButton>
          </div>
        </CoachieCard>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {friends.map((friend) => (
            <CoachieCard key={friend.id}>
              <div className="p-6">
                <div className="flex items-center gap-4">
                  <div className="w-12 h-12 bg-primary-100 rounded-full flex items-center justify-center">
                    <span className="text-2xl">👤</span>
                  </div>
                  <div className="flex-1">
                    <h3 className="font-semibold text-gray-900">{friend.name}</h3>
                    {friend.username && (
                      <p className="text-sm text-gray-500">@{friend.username}</p>
                    )}
                  </div>
                  <CoachieButton
                    variant="outline"
                    onClick={() => router.push(`/messaging?userId=${friend.id}`)}
                  >
                    Message
                  </CoachieButton>
                </div>
              </div>
            </CoachieCard>
          ))}
        </div>
      )}
    </div>
  )
}

