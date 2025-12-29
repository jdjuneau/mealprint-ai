/**
 * Friends Service (Web Version)
 * Manages friend relationships and requests
 */

import { db } from '../firebase'
import {
  collection,
  doc,
  getDoc,
  getDocs,
  setDoc,
  deleteDoc,
  query,
  where,
  orderBy,
  Timestamp,
} from 'firebase/firestore'

export interface Friend {
  id: string
  userId: string
  friendId: string
  friendName: string
  friendUsername?: string
  status: 'pending' | 'accepted' | 'blocked'
  createdAt: Date
}

export interface FriendRequest {
  id: string
  fromUserId: string
  toUserId: string
  status: 'pending' | 'accepted' | 'rejected'
  createdAt: Date
}

class FriendsService {
  private static instance: FriendsService

  private constructor() {}

  static getInstance(): FriendsService {
    if (!FriendsService.instance) {
      FriendsService.instance = new FriendsService()
    }
    return FriendsService.instance
  }

  /**
   * Get all friends for a user
   * Handles both Android format (no status field, just addedAt) and Web format (with status field)
   */
  async getFriends(userId: string): Promise<Friend[]> {
    try {
      const friendsRef = collection(db, 'users', userId, 'friends')
      const friendsSnap = await getDocs(friendsRef)
      
      const friends: Friend[] = []
      
      // Process all friend documents
      await Promise.all(
        friendsSnap.docs.map(async (friendDoc) => {
          const data = friendDoc.data()
          const friendId = friendDoc.id
          
          // Android format: just has addedAt, no status field - treat as accepted
          // Web format: has status field
          const hasStatus = 'status' in data
          const isAccepted = hasStatus ? data.status === 'accepted' : true
          
          if (isAccepted) {
            // Get friend's profile for name and username
            try {
              const friendProfileRef = doc(db, 'users', friendId)
              const friendProfileSnap = await getDoc(friendProfileRef)

              if (friendProfileSnap.exists()) {
                const profileData = friendProfileSnap.data()
                friends.push({
                  id: friendId,
          userId,
                  friendId,
                  friendName: profileData.name || 'Unknown',
                  friendUsername: profileData.username,
                  status: 'accepted',
                  createdAt: data.addedAt?.toDate() || data.createdAt?.toDate() || new Date(),
                })
              }
            } catch (error) {
              console.error(`Error loading profile for friend ${friendId}:`, error)
            }
          }
        })
      )
      
      // Sort by creation date (most recent first)
      return friends.sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime())
    } catch (error) {
      console.error('Error getting friends:', error)
      return []
    }
  }

  /**
   * Send a friend request
   */
  async sendFriendRequest(fromUserId: string, toUserId: string): Promise<string> {
    try {
      const requestsRef = collection(db, 'friendRequests')
      const requestRef = doc(requestsRef)

      await setDoc(requestRef, {
        fromUserId,
        toUserId,
        status: 'pending',
        createdAt: Timestamp.now(),
        platform: 'web',
      })

      return requestRef.id
    } catch (error) {
      console.error('Error sending friend request:', error)
      throw error
    }
  }

  /**
   * Accept a friend request
   */
  async acceptFriendRequest(requestId: string): Promise<void> {
    try {
      const requestRef = doc(db, 'friendRequests', requestId)
      const requestSnap = await getDoc(requestRef)

      if (requestSnap.exists()) {
        const data = requestSnap.data()
        const fromUserId = data.fromUserId
        const toUserId = data.toUserId

        // Update request status
        await setDoc(requestRef, { status: 'accepted', platform: 'web' }, { merge: true })

        // Create friend relationship (bidirectional)
        const friend1Ref = doc(db, 'users', fromUserId, 'friends', toUserId)
        const friend2Ref = doc(db, 'users', toUserId, 'friends', fromUserId)

        // Get user profiles for names
        const fromUserRef = doc(db, 'users', fromUserId)
        const toUserRef = doc(db, 'users', toUserId)
        const [fromUserSnap, toUserSnap] = await Promise.all([
          getDoc(fromUserRef),
          getDoc(toUserRef),
        ])

        await Promise.all([
          setDoc(friend1Ref, {
            userId: fromUserId,
            friendId: toUserId,
            friendName: toUserSnap.data()?.name || 'User',
            friendUsername: toUserSnap.data()?.username,
            status: 'accepted',
            createdAt: Timestamp.now(),
            platform: 'web',
          }),
          setDoc(friend2Ref, {
            userId: toUserId,
            friendId: fromUserId,
            friendName: fromUserSnap.data()?.name || 'User',
            friendUsername: fromUserSnap.data()?.username,
            status: 'accepted',
            createdAt: Timestamp.now(),
            platform: 'web',
          }),
        ])
      }
    } catch (error) {
      console.error('Error accepting friend request:', error)
      throw error
    }
  }
}

export default FriendsService
