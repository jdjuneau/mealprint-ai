'use client'

import { useAuth } from '../../../lib/contexts/AuthContext'
import { useRouter, useParams } from 'next/navigation'
import { useState, useEffect } from 'react'
import { collection, query, where, getDocs, limit } from 'firebase/firestore'
import { db } from '../../../lib/firebase'
import LoadingScreen from '../../../components/LoadingScreen'
import toast from 'react-hot-toast'

interface User {
  uid: string
  displayName: string
  username?: string
  email?: string
}

export default function UserSearchInvitePage() {
  const { user } = useAuth()
  const router = useRouter()
  const params = useParams()
  const circleId = params.circleId as string

  const [searchQuery, setSearchQuery] = useState('')
  const [searchResults, setSearchResults] = useState<User[]>([])
  const [loading, setLoading] = useState(false)
  const [inviting, setInviting] = useState<string | null>(null)

  const searchUsers = async (searchTerm: string) => {
    if (!searchTerm.trim() || !user) return

    setLoading(true)
    try {
      const usersRef = collection(db, 'users')
      const q = query(
        usersRef,
        where('name', '>=', searchTerm),
        where('name', '<=', searchTerm + '\uf8ff'),
        limit(20)
      )
      const snapshot = await getDocs(q)
      const users: User[] = []
      snapshot.forEach((doc) => {
        const data = doc.data()
        if (doc.id !== user.uid) {
          users.push({
            uid: doc.id,
            displayName: data.name || data.displayName || 'Unknown',
            username: data.username,
            email: data.email,
          })
        }
      })
      setSearchResults(users)
    } catch (error) {
      console.error('Error searching users:', error)
      toast.error('Failed to search users')
    } finally {
      setLoading(false)
    }
  }

  const handleInvite = async (userId: string) => {
    if (!user || !circleId) return

    setInviting(userId)
    try {
      // Add invitation to Firestore
      const invitationsRef = collection(db, 'users', userId, 'circle_invitations')
      await getDocs(query(invitationsRef, where('circleId', '==', circleId))).then((snapshot) => {
        if (snapshot.empty) {
          // Create invitation
          const { doc, setDoc } = require('firebase/firestore')
          setDoc(doc(db, 'users', userId, 'circle_invitations', circleId), {
            circleId,
            inviterId: user.uid,
            inviterName: user.displayName || user.email,
            createdAt: new Date(),
            status: 'pending',
          })
          toast.success('Invitation sent!')
        } else {
          toast.error('User already invited')
        }
      })
    } catch (error) {
      console.error('Error sending invitation:', error)
      toast.error('Failed to send invitation')
    } finally {
      setInviting(null)
    }
  }

  useEffect(() => {
    if (!user) {
      router.push('/auth')
    }
  }, [user, router])

  if (!user) {
    return <LoadingScreen />
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-2xl mx-auto py-8 px-4">
        <div className="bg-white rounded-lg shadow-lg p-6">
          <div className="flex items-center mb-6">
            <button
              onClick={() => router.back()}
              className="mr-4 text-gray-600 hover:text-gray-800"
            >
              ← Back
            </button>
            <h1 className="text-xl font-bold text-gray-900">Invite Users to Circle</h1>
          </div>

          <div className="space-y-4">
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => {
                setSearchQuery(e.target.value)
                if (e.target.value.trim()) {
                  searchUsers(e.target.value)
                } else {
                  setSearchResults([])
                }
              }}
              placeholder="Search by name or username..."
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
            />

            {loading && (
              <div className="text-center py-8">
                <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
              </div>
            )}

            {!loading && searchResults.length === 0 && searchQuery && (
              <div className="text-center py-8 text-gray-500">No users found</div>
            )}

            {searchResults.length > 0 && (
              <div className="space-y-2">
                {searchResults.map((userResult) => (
                  <div
                    key={userResult.uid}
                    className="flex items-center justify-between p-4 border border-gray-200 rounded-lg"
                  >
                    <div>
                      <div className="font-semibold text-gray-900">{userResult.displayName}</div>
                      {userResult.username && (
                        <div className="text-sm text-gray-500">@{userResult.username}</div>
                      )}
                    </div>
                    <button
                      onClick={() => handleInvite(userResult.uid)}
                      disabled={inviting === userResult.uid}
                      className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
                    >
                      {inviting === userResult.uid ? 'Inviting...' : 'Invite'}
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

