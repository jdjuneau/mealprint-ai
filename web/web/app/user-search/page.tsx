'use client'

import { useAuth } from '../../lib/contexts/AuthContext'
import { useRouter } from 'next/navigation'
import { useState, useEffect } from 'react'
import LoadingScreen from '../../components/LoadingScreen'
import toast from 'react-hot-toast'
import { collection, getDocs } from 'firebase/firestore'
import { db } from '../../lib/firebase'

interface User {
  id: string
  name: string
  username?: string
}

export default function UserSearchPage() {
  const { user, userProfile } = useAuth()
  const router = useRouter()
  const [loading, setLoading] = useState(true)
  const [searchQuery, setSearchQuery] = useState('')
  const [users, setUsers] = useState<User[]>([])
  const [searching, setSearching] = useState(false)
  const [allUsers, setAllUsers] = useState<User[]>([]) // Cache all users for autofill

  useEffect(() => {
    if (!user) {
      router.push('/auth')
    } else {
      setLoading(false)
      // Preload all users for autofill (only once)
      loadAllUsers()
    }
  }, [user, router])

  const loadAllUsers = async () => {
    if (!user) return
    try {
      const usersRef = collection(db, 'users')
      const usersSnap = await getDocs(usersRef)
      const loadedUsers: User[] = []
      usersSnap.forEach((doc) => {
        if (doc.id !== user.uid) {
          const data = doc.data()
          // Ensure we include all users regardless of platform (device agnostic)
          // Include users with any identifier: name, username, or email
          const name = (data.name || data.displayName || '').trim()
          const username = (data.username || '').trim()
          const email = (data.email || '').trim()
          
          // Include user if they have at least one identifier (name, username, or email)
          // This ensures web users are found even if they only have an email
          if (name || username || email) {
            loadedUsers.push({
              id: doc.id,
              name: name || email?.split('@')[0] || 'User',
              username: username || email?.split('@')[0],
            })
          }
        }
      })
      setAllUsers(loadedUsers)
      console.log(`Loaded ${loadedUsers.length} users for search (device agnostic)`)
    } catch (error) {
      console.error('Error loading users:', error)
    }
  }

  // Autofill search as user types (debounced)
  useEffect(() => {
    if (!searchQuery.trim()) {
      setUsers([])
      return
    }

    const searchLower = searchQuery.toLowerCase()
    const matchingUsers = allUsers
      .filter((user) => {
        const name = (user.name || '').toLowerCase()
        const username = (user.username || '').toLowerCase()
        return name.includes(searchLower) || username.includes(searchLower)
      })
      .slice(0, 10) // Limit to 10 results

    setUsers(matchingUsers)
  }, [searchQuery, allUsers])

  const handleSearch = async () => {
    // Search is now automatic via useEffect, but keep this for manual refresh
    if (!searchQuery.trim() || !user) return

    setSearching(true)
    try {
      // Reload users if needed
      if (allUsers.length === 0) {
        await loadAllUsers()
      }
      // Results are already set by useEffect
    } catch (error) {
      console.error('Error searching users:', error)
      toast.error('Failed to search users')
    } finally {
      setSearching(false)
    }
  }

  const handleAddFriend = async (userId: string) => {
    if (!user) return
    try {
      const FriendsService = (await import('../../lib/services/friendsService')).default
      const friendsService = FriendsService.getInstance()
      await friendsService.sendFriendRequest(user.uid, userId)
      toast.success('Friend request sent!')
    } catch (error) {
      console.error('Error sending friend request:', error)
      toast.error('Failed to send friend request')
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
        <h1 className="text-2xl font-bold text-white mb-6">Search Users</h1>

        <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6 mb-6">
          <div className="flex gap-2">
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
              placeholder="Search by name or username..."
              className="flex-1 px-4 py-2 border border-white/30 rounded-lg bg-white/10 text-white placeholder-white/50 focus:ring-2 focus:ring-blue-500"
            />
            <button
              onClick={handleSearch}
              disabled={searching}
              className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
            >
              {searching ? 'Searching...' : 'Search'}
            </button>
          </div>
        </div>

        {users.length === 0 && searchQuery && !searching && allUsers.length > 0 && (
          <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-12 text-center">
            <p className="text-white/80">No users found matching "{searchQuery}"</p>
          </div>
        )}

        {users.length > 0 && (
          <div className="space-y-4">
            {users.map((userResult) => (
              <div
                key={userResult.id}
                className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6 flex items-center justify-between"
              >
                <div className="flex items-center gap-4">
                  <div className="w-12 h-12 bg-blue-100 rounded-full flex items-center justify-center">
                    <span className="text-blue-600 font-semibold">
                      {userResult.name.charAt(0).toUpperCase()}
                    </span>
                  </div>
                  <div>
                    <h3 className="font-semibold text-white">{userResult.name}</h3>
                    {userResult.username && (
                      <p className="text-sm text-white/70">@{userResult.username}</p>
                    )}
                  </div>
                </div>
                <button
                  onClick={() => handleAddFriend(userResult.id)}
                  className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
                >
                  Add Friend
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
