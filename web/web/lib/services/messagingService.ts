/**
 * Messaging Service (Web Version)
 * Manages user messages and conversations
 */

import { db } from '../firebase'
import {
  collection,
  doc,
  getDoc,
  getDocs,
  setDoc,
  query,
  where,
  orderBy,
  limit,
  onSnapshot,
  Unsubscribe,
  Timestamp,
  QueryDocumentSnapshot,
  QuerySnapshot,
} from 'firebase/firestore'

export interface Message {
  id: string
  conversationId: string
  senderId: string
  receiverId: string
  content: string
  timestamp: Date
  read: boolean
}

export interface Conversation {
  id: string
  userId1: string
  userId2: string
  lastMessage?: string
  lastMessageTime?: Date
  unreadCount: number
}

class MessagingService {
  private static instance: MessagingService

  private constructor() {}

  static getInstance(): MessagingService {
    if (!MessagingService.instance) {
      MessagingService.instance = new MessagingService()
    }
    return MessagingService.instance
  }

  /**
   * Get or create conversation ID between two users
   */
  private getConversationId(userId1: string, userId2: string): string {
    // Sort IDs to ensure consistent conversation ID
    const sorted = [userId1, userId2].sort()
    return `${sorted[0]}_${sorted[1]}`
  }

  /**
   * Send a message
   */
  async sendMessage(senderId: string, receiverId: string, content: string): Promise<string> {
    try {
      const conversationId = this.getConversationId(senderId, receiverId)
      const messagesRef = collection(db, 'conversations', conversationId, 'messages')
      const messageRef = doc(messagesRef)

      await setDoc(messageRef, {
        senderId,
        receiverId,
        content,
        timestamp: Timestamp.now(),
        createdAt: Timestamp.now(), // Also set createdAt for Android compatibility
        read: false,
        platform: 'web',
      })

      // Update conversation - support both Web format (userId1/userId2) and Android format (participants)
      const conversationRef = doc(db, 'conversations', conversationId)
      const participants = [senderId, receiverId].sort()
      await setDoc(conversationRef, {
        userId1: participants[0], // Web format
        userId2: participants[1], // Web format
        participants: participants, // Android format
        lastMessage: content,
        lastMessageTime: Timestamp.now(), // Web format
        lastMessageAt: Timestamp.now(), // Android format
        platform: 'web',
      }, { merge: true })

      return messageRef.id
    } catch (error) {
      console.error('Error sending message:', error)
      throw error
    }
  }

  /**
   * Get messages for a conversation
   * Handles both Android format (createdAt) and Web format (timestamp)
   */
  async getMessages(userId1: string, userId2: string): Promise<Message[]> {
    try {
      const conversationId = this.getConversationId(userId1, userId2)
      const messagesRef = collection(db, 'conversations', conversationId, 'messages')
      
      // Try timestamp first (web format), fallback to createdAt (Android format)
      let messagesQuery
      try {
        messagesQuery = query(messagesRef, orderBy('timestamp', 'asc'))
        const messagesSnap = await getDocs(messagesQuery)
        return this.mapMessages(messagesSnap.docs, conversationId)
      } catch (error: any) {
        // If timestamp field doesn't exist, try createdAt (Android format)
        if (error.code === 'failed-precondition' || error.message?.includes('timestamp')) {
          try {
            messagesQuery = query(messagesRef, orderBy('createdAt', 'asc'))
      const messagesSnap = await getDocs(messagesQuery)
            return this.mapMessages(messagesSnap.docs, conversationId)
          } catch (error2) {
            // If ordering fails, get messages without ordering and sort manually
            const messagesSnap = await getDocs(messagesRef)
            const messages = this.mapMessages(messagesSnap.docs, conversationId)
            return messages.sort((a, b) => a.timestamp.getTime() - b.timestamp.getTime())
          }
        }
        throw error
      }
    } catch (error) {
      console.error('Error getting messages:', error)
      return []
    }
  }

  /**
   * Map Firestore documents to Message objects, handling both timestamp and createdAt fields
   */
  private mapMessages(docs: any[], conversationId: string): Message[] {
    return docs.map((doc) => {
      const data = doc.data()
      // Android uses createdAt, Web uses timestamp
      const timestamp = data.timestamp?.toDate() || data.createdAt?.toDate() || new Date()
      return {
        id: doc.id,
        conversationId,
        senderId: data.senderId,
        receiverId: data.receiverId,
        content: data.content,
        timestamp,
        read: data.read || false,
      } as Message
    })
  }

  /**
   * Subscribe to messages (real-time)
   * Handles both Android format (createdAt) and Web format (timestamp)
   */
  subscribeToMessages(
    userId1: string,
    userId2: string,
    callback: (messages: Message[]) => void
  ): Unsubscribe {
    const conversationId = this.getConversationId(userId1, userId2)
    const messagesRef = collection(db, 'conversations', conversationId, 'messages')
    
    let currentUnsubscribe: Unsubscribe | null = null
    let isUnsubscribed = false

    const cleanup = () => {
      if (currentUnsubscribe) {
        currentUnsubscribe()
        currentUnsubscribe = null
      }
    }

    // Try both timestamp and createdAt - Android uses createdAt, Web uses timestamp
    // First try createdAt (Android format) since it's more common
    const trySubscribe = (orderByField: 'createdAt' | 'timestamp') => {
      cleanup()
      try {
        const messagesQuery = query(messagesRef, orderBy(orderByField, 'asc'))
        currentUnsubscribe = onSnapshot(
          messagesQuery,
          (snapshot) => {
            if (isUnsubscribed) return
            const messages = this.mapMessages(snapshot.docs, conversationId)
            const sortedMessages = messages.sort((a, b) => a.timestamp.getTime() - b.timestamp.getTime())
            callback(sortedMessages)
          },
          (error) => {
            if (isUnsubscribed) return
            console.error(`Error subscribing to messages with ${orderByField}:`, error)
            // Try the other field
            if (orderByField === 'createdAt') {
              trySubscribe('timestamp')
            } else {
              // Last resort: unordered query
              cleanup()
              currentUnsubscribe = onSnapshot(
                messagesRef,
                (snapshot) => {
                  if (isUnsubscribed) return
                  const messages = this.mapMessages(snapshot.docs, conversationId)
                  const sorted = messages.sort((a, b) => a.timestamp.getTime() - b.timestamp.getTime())
                  callback(sorted)
                },
                (error3) => {
                  if (isUnsubscribed) return
                  console.error('Error subscribing to messages (unordered):', error3)
                  callback([])
                }
              )
            }
          }
        )
      } catch (error) {
        console.error(`Error creating query with ${orderByField}:`, error)
        if (orderByField === 'createdAt') {
          trySubscribe('timestamp')
        } else {
          // Last resort: unordered query
          cleanup()
          currentUnsubscribe = onSnapshot(
            messagesRef,
            (snapshot) => {
              if (isUnsubscribed) return
              const messages = this.mapMessages(snapshot.docs, conversationId)
              const sorted = messages.sort((a, b) => a.timestamp.getTime() - b.timestamp.getTime())
              callback(sorted)
            },
            (error3) => {
              if (isUnsubscribed) return
              console.error('Error subscribing to messages (unordered):', error3)
              callback([])
            }
          )
        }
      }
    }

    // Start with createdAt (Android format) first
    trySubscribe('createdAt')

    return () => {
      isUnsubscribed = true
      cleanup()
    }
  }

  /**
   * Get all conversations for a user
   * Supports both Web format (userId1/userId2) and Android format (participants array)
   */
  async getConversations(userId: string): Promise<Conversation[]> {
    try {
      const conversationsRef = collection(db, 'conversations')
      
      // Query 1: Web format (userId1/userId2)
      const q1 = query(conversationsRef, where('userId1', '==', userId))
      const q2 = query(conversationsRef, where('userId2', '==', userId))
      
      // Query 2: Android format (participants array)
      const q3 = query(conversationsRef, where('participants', 'array-contains', userId))

      let snap1: QuerySnapshot
      let snap2: QuerySnapshot
      let snap3: QuerySnapshot
      
      try {
        snap1 = await getDocs(q1)
      } catch {
        snap1 = { docs: [], empty: true, size: 0, metadata: {} as any, query: q1, forEach: () => {}, docChanges: () => [] } as unknown as QuerySnapshot
      }
      
      try {
        snap2 = await getDocs(q2)
      } catch {
        snap2 = { docs: [], empty: true, size: 0, metadata: {} as any, query: q2, forEach: () => {}, docChanges: () => [] } as unknown as QuerySnapshot
      }
      
      try {
        snap3 = await getDocs(q3)
      } catch {
        snap3 = { docs: [], empty: true, size: 0, metadata: {} as any, query: q3, forEach: () => {}, docChanges: () => [] } as unknown as QuerySnapshot
      }

      const conversationsMap = new Map<string, Conversation>()

      // Process Web format conversations
      snap1.docs.forEach((doc: QueryDocumentSnapshot) => {
        const data = doc.data()
        const otherUserId = data.userId2
        conversationsMap.set(doc.id, {
          id: doc.id,
          userId1: data.userId1 || userId,
          userId2: data.userId2 || otherUserId,
          lastMessage: data.lastMessage,
          lastMessageTime: data.lastMessageTime?.toDate() || data.lastMessageAt?.toDate(),
          unreadCount: 0, // TODO: Calculate unread count
        })
      })

      snap2.docs.forEach((doc: any) => {
        const data = doc.data()
        const otherUserId = data.userId1
        if (!conversationsMap.has(doc.id)) {
          conversationsMap.set(doc.id, {
            id: doc.id,
            userId1: data.userId1 || otherUserId,
            userId2: data.userId2 || userId,
            lastMessage: data.lastMessage,
            lastMessageTime: data.lastMessageTime?.toDate() || data.lastMessageAt?.toDate(),
            unreadCount: 0,
          })
        }
      })

      // Process Android format conversations (participants array)
      snap3.docs.forEach((doc: QueryDocumentSnapshot) => {
        if (!conversationsMap.has(doc.id)) {
          const data = doc.data()
          const participants = data.participants || []
          const otherUserId = participants.find((id: string) => id !== userId) || ''
          
          conversationsMap.set(doc.id, {
            id: doc.id,
            userId1: participants[0] || userId,
            userId2: participants[1] || otherUserId,
            lastMessage: data.lastMessage,
            lastMessageTime: data.lastMessageTime?.toDate() || data.lastMessageAt?.toDate(),
            unreadCount: 0,
          })
        }
      })

      // Convert map to array and sort by last message time
      const conversations = Array.from(conversationsMap.values())
      return conversations.sort((a, b) => {
        const timeA = a.lastMessageTime?.getTime() || 0
        const timeB = b.lastMessageTime?.getTime() || 0
        return timeB - timeA
      })
    } catch (error) {
      console.error('Error getting conversations:', error)
      return []
    }
  }
}

export default MessagingService
