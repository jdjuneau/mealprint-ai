/**
 * Global Message Notification Service
 * Listens for new messages across all conversations and shows notifications
 */

import { db } from '../firebase'
import { collection, query, where, onSnapshot, Unsubscribe, Timestamp } from 'firebase/firestore'
import toast from 'react-hot-toast'

class MessageNotificationService {
  private unsubscribeMap: Map<string, Unsubscribe> = new Map()
  private userId: string | null = null
  private isEnabled: boolean = true

  /**
   * Start listening for new messages for a user
   */
  startListening(userId: string) {
    if (this.userId === userId && this.unsubscribeMap.size > 0) {
      // Already listening for this user
      return
    }

    this.stopListening() // Clean up any existing listeners
    this.userId = userId

    // Track last message time per conversation to avoid duplicate notifications
    const lastMessageTimes = new Map<string, number>()

    // Get all conversations where user is participant
    // Android uses 'participants' array, Web uses 'userId1'/'userId2' - support both
    const conversationsRef = collection(db, 'conversations')
    
    // Query 1: Web format (userId1/userId2)
    const q1 = query(conversationsRef, where('userId1', '==', userId))
    const q2 = query(conversationsRef, where('userId2', '==', userId))
    
    // Query 2: Android format (participants array)
    const q3 = query(conversationsRef, where('participants', 'array-contains', userId))

    // Listen to conversations where user is userId1 (Web format)
    const unsubscribe1 = onSnapshot(
      q1,
      (snapshot) => {
        snapshot.docChanges().forEach((change) => {
          if (change.type === 'modified' || change.type === 'added') {
            const data = change.doc.data()
            const conversationId = change.doc.id
            // Support both lastMessageTime (Web) and lastMessageAt (Android)
            const lastMessageTime = data.lastMessageTime?.toMillis?.() || 
                                   data.lastMessageAt?.toMillis?.() || 
                                   data.lastMessageTime || 
                                   data.lastMessageAt || 
                                   0
            const lastKnownTime = lastMessageTimes.get(conversationId) || 0

            // Only notify if this is a new message (time is newer)
            if (lastMessageTime > lastKnownTime && data.userId2 !== userId) {
              lastMessageTimes.set(conversationId, lastMessageTime)
              this.checkForNewMessages(conversationId, data.userId2, data.lastMessage, lastMessageTime)
            }
          }
        })
      },
      (error) => {
        console.error('Error listening to conversations (userId1):', error)
      }
    )

    // Listen to conversations where user is userId2 (Web format)
    const unsubscribe2 = onSnapshot(
      q2,
      (snapshot) => {
        snapshot.docChanges().forEach((change) => {
          if (change.type === 'modified' || change.type === 'added') {
            const data = change.doc.data()
            const conversationId = change.doc.id
            // Support both lastMessageTime (Web) and lastMessageAt (Android)
            const lastMessageTime = data.lastMessageTime?.toMillis?.() || 
                                   data.lastMessageAt?.toMillis?.() || 
                                   data.lastMessageTime || 
                                   data.lastMessageAt || 
                                   0
            const lastKnownTime = lastMessageTimes.get(conversationId) || 0

            // Only notify if this is a new message (time is newer)
            if (lastMessageTime > lastKnownTime && data.userId1 !== userId) {
              lastMessageTimes.set(conversationId, lastMessageTime)
              this.checkForNewMessages(conversationId, data.userId1, data.lastMessage, lastMessageTime)
            }
          }
        })
      },
      (error) => {
        console.error('Error listening to conversations (userId2):', error)
      }
    )

    // Listen to conversations where user is in participants array (Android format)
    const unsubscribe3 = onSnapshot(
      q3,
      (snapshot) => {
        snapshot.docChanges().forEach((change) => {
          if (change.type === 'modified' || change.type === 'added') {
            const data = change.doc.data()
            const conversationId = change.doc.id
            const participants = data.participants || []
            
            // Skip if this conversation was already handled by userId1/userId2 queries
            if (data.userId1 || data.userId2) {
              return
            }
            
            // Get the other participant
            const otherParticipant = participants.find((p: string) => p !== userId)
            if (!otherParticipant) return
            
            // Support both lastMessageTime (Web) and lastMessageAt (Android)
            const lastMessageTime = data.lastMessageTime?.toMillis?.() || 
                                   data.lastMessageAt?.toMillis?.() || 
                                   data.lastMessageTime || 
                                   data.lastMessageAt || 
                                   0
            const lastKnownTime = lastMessageTimes.get(conversationId) || 0

            // Only notify if this is a new message (time is newer)
            if (lastMessageTime > lastKnownTime) {
              lastMessageTimes.set(conversationId, lastMessageTime)
              this.checkForNewMessages(conversationId, otherParticipant, data.lastMessage, lastMessageTime)
            }
          }
        })
      },
      (error) => {
        console.error('Error listening to conversations (participants):', error)
      }
    )

    this.unsubscribeMap.set('conversations1', unsubscribe1)
    this.unsubscribeMap.set('conversations2', unsubscribe2)
    this.unsubscribeMap.set('conversations3', unsubscribe3)
  }

  /**
   * Check if a new message was received and show notification
   */
  private async checkForNewMessages(
    conversationId: string,
    senderId: string,
    lastMessage: string | undefined,
    lastMessageTime: any
  ) {
    if (!lastMessage || !lastMessageTime) return

    // Don't show notification if user is currently on messaging page
    if (typeof window !== 'undefined') {
      const currentPath = window.location.pathname
      if (currentPath.includes('/messaging')) {
        return // User is already on messaging page
      }
    }

    // Get sender's name
    try {
      const { doc, getDoc } = await import('firebase/firestore')
      const senderDoc = await getDoc(doc(db, 'users', senderId))
      const senderName = senderDoc.data()?.name || 'Someone'

      // Show notification
      if (this.isEnabled) {
        toast(
          (t) => (
            <div className="flex flex-col cursor-pointer" onClick={() => {
              if (typeof window !== 'undefined') {
                window.location.href = `/messaging?userId=${senderId}`
              }
            }}>
              <div className="font-semibold text-white">{senderName}</div>
              <div className="text-sm text-white/80">{lastMessage.length > 50 ? lastMessage.substring(0, 50) + '...' : lastMessage}</div>
            </div>
          ),
          {
            duration: 5000,
            icon: '💬',
            style: {
              background: 'rgba(0, 0, 0, 0.8)',
              color: '#fff',
              borderRadius: '8px',
            },
          }
        )
      }
    } catch (error) {
      console.error('Error checking for new messages:', error)
    }
  }

  /**
   * Stop listening for messages
   */
  stopListening() {
    this.unsubscribeMap.forEach((unsubscribe) => unsubscribe())
    this.unsubscribeMap.clear()
    this.userId = null
  }

  /**
   * Enable/disable notifications
   */
  setEnabled(enabled: boolean) {
    this.isEnabled = enabled
  }
}

export default new MessageNotificationService()
