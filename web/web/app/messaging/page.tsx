'use client'

import { useAuth } from '../../lib/contexts/AuthContext'
import { useRouter, useSearchParams } from 'next/navigation'
import { useState, useEffect, useRef } from 'react'
import LoadingScreen from '../../components/LoadingScreen'
import toast from 'react-hot-toast'

interface Message {
  id: string
  senderId: string
  content: string
  timestamp: Date
}

export default function MessagingPage() {
  const { user, userProfile } = useAuth()
  const router = useRouter()
  const searchParams = useSearchParams()
  const [loading, setLoading] = useState(true)
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const unsubscribeRef = useRef<(() => void) | null>(null)
  const conversationId = searchParams.get('userId')

  useEffect(() => {
    if (!user) {
      router.push('/auth')
    } else {
      loadMessages()
    }
    
    // Cleanup on unmount or conversation change
    return () => {
      if (unsubscribeRef.current) {
        unsubscribeRef.current()
        unsubscribeRef.current = null
      }
    }
  }, [user, router, conversationId])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const loadMessages = async () => {
    if (!user) return
    
    // Cleanup previous subscription
    if (unsubscribeRef.current) {
      unsubscribeRef.current()
      unsubscribeRef.current = null
    }
    
    try {
      setLoading(true)
      const MessagingService = (await import('../../lib/services/messagingService')).default
      const messagingService = MessagingService.getInstance()

      if (conversationId) {
        // Load specific conversation
        const loadedMessages = await messagingService.getMessages(user.uid, conversationId)
        setMessages(loadedMessages)

        // Subscribe to real-time updates
        const unsubscribe = messagingService.subscribeToMessages(
          user.uid,
          conversationId,
          (newMessages) => {
            setMessages(newMessages)
            setLoading(false)
          }
        )

        unsubscribeRef.current = unsubscribe
        setLoading(false)
      } else {
        // Load all conversations (for conversation list view)
        setMessages([])
        setLoading(false)
      }
    } catch (error) {
      console.error('Error loading messages:', error)
      toast.error('Failed to load messages')
      setLoading(false)
    }
  }

  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!input.trim() || !user || !conversationId) return

    try {
      const MessagingService = (await import('../../lib/services/messagingService')).default
      const messagingService = MessagingService.getInstance()
      await messagingService.sendMessage(user.uid, conversationId, input.trim())
      setInput('')
    } catch (error) {
      console.error('Error sending message:', error)
      toast.error('Failed to send message')
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
    <div className={`flex flex-col h-screen ${gradientClass}`}>
      <div className="bg-white/10 backdrop-blur-sm border-b border-white/20 px-4 py-3">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-xl font-bold text-white">Messages</h1>
            {conversationId && <p className="text-sm text-white/70">Conversation</p>}
          </div>
          <button
            onClick={() => router.back()}
            className="text-white/80 hover:text-white"
          >
            ✕
          </button>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto px-4 py-6 space-y-4">
        {messages.length === 0 && (
          <div className="text-center text-white/80 mt-8">
            <p>No messages yet. Start a conversation!</p>
          </div>
        )}

        {messages.map((message) => (
          <div
            key={message.id}
            className={`flex ${
              message.senderId === user.uid ? 'justify-end' : 'justify-start'
            }`}
          >
            <div
              className={`max-w-xs lg:max-w-md px-4 py-2 rounded-lg ${
                message.senderId === user.uid
                  ? 'bg-blue-600 text-white'
                  : 'bg-white/10 backdrop-blur-sm text-white border border-white/20'
              }`}
            >
              <p>{message.content}</p>
            </div>
          </div>
        ))}

        <div ref={messagesEndRef} />
      </div>

      <div className="bg-white/10 backdrop-blur-sm border-t border-white/20 px-4 py-3">
        <form onSubmit={handleSend} className="flex gap-2">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Type a message..."
            className="flex-1 px-4 py-2 border border-white/30 rounded-lg bg-white/10 text-white placeholder-white/50 focus:ring-2 focus:ring-blue-500"
          />
          <button
            type="submit"
            disabled={!input.trim()}
            className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
          >
            Send
          </button>
        </form>
      </div>
    </div>
  )
}
