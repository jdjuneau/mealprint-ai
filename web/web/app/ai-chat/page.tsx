'use client'

import { useAuth } from '../../lib/contexts/AuthContext'
import { useRouter } from 'next/navigation'
import { useState, useEffect, useRef } from 'react'
import { AIService } from '../../lib/services/ai'
import toast from 'react-hot-toast'

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: Date
}

export default function AIChatPage() {
  const { user, userProfile } = useAuth()
  const router = useRouter()
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)

  // Add welcome message on mount
  useEffect(() => {
    if (user && userProfile && messages.length === 0) {
      setMessages([{
        id: 'welcome',
        role: 'assistant',
        content: `Hi ${userProfile.name}! I'm Coachie, your AI fitness coach. How can I help you today?`,
        timestamp: new Date(),
      }])
    }
  }, [user, userProfile])

  useEffect(() => {
    if (!user) {
      router.push('/auth')
    }
  }, [user, router])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!input.trim() || !user || loading) return

    const userMessage: ChatMessage = {
      id: Date.now().toString(),
      role: 'user',
      content: input,
      timestamp: new Date(),
    }

    setMessages((prev) => [...prev, userMessage])
    setInput('')
    setLoading(true)

    try {
      // Build conversation history (exclude welcome message)
      const conversationHistory = messages
        .filter(msg => msg.id !== 'welcome')
        .map((msg) => ({
          role: msg.role,
          content: msg.content,
        }))

      // Generate AI response
      const response = await AIService.generateChatResponse(
        user.uid,
        input,
        userProfile?.name || 'User',
        conversationHistory
      )

      const assistantMessage: ChatMessage = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: response,
        timestamp: new Date(),
      }

      setMessages((prev) => [...prev, assistantMessage])
    } catch (error) {
      console.error('Error sending message:', error)
      toast.error('Failed to send message')
    } finally {
      setLoading(false)
    }
  }

  if (!user || !userProfile) {
    return <div>Loading...</div>
  }

  const gradientClass = userProfile.gender === 'male'
    ? 'bg-coachie-gradient-male'
    : userProfile.gender === 'female'
    ? 'bg-coachie-gradient-female'
    : 'bg-coachie-gradient'

  return (
    <div className={`flex flex-col h-screen ${gradientClass}`}>
      {/* Header */}
      <div className="bg-white/10 backdrop-blur-sm border-b border-white/20 px-4 py-3">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-xl font-bold text-white">Coachie AI</h1>
            <p className="text-sm text-white/80">Your AI fitness coach</p>
          </div>
          <button
            onClick={() => router.back()}
            className="text-white/90 hover:text-white"
          >
            ✕
          </button>
        </div>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto px-4 py-6 space-y-4">
        {messages.length === 0 && (
          <div className="text-center text-white/80 mt-8">
            <p className="text-lg mb-2">👋 Hi {userProfile.name}!</p>
            <p>Ask me anything about fitness, nutrition, or your goals.</p>
          </div>
        )}

        {messages.map((message) => (
          <div
            key={message.id}
            className={`flex ${
              message.role === 'user' ? 'justify-end' : 'justify-start'
            }`}
          >
            <div
              className={`max-w-xs lg:max-w-md px-4 py-2 rounded-lg ${
                message.role === 'user'
                  ? 'bg-white/20 backdrop-blur-sm text-white border border-white/30'
                  : 'bg-white/10 backdrop-blur-sm text-white border border-white/20'
              }`}
            >
              <p className="whitespace-pre-wrap">{message.content}</p>
            </div>
          </div>
        ))}

        {loading && (
          <div className="flex justify-start">
            <div className="bg-white/10 backdrop-blur-sm border border-white/20 rounded-lg px-4 py-2">
              <div className="flex gap-1">
                <div className="w-2 h-2 bg-white/70 rounded-full animate-bounce" />
                <div className="w-2 h-2 bg-white/70 rounded-full animate-bounce" style={{ animationDelay: '0.1s' }} />
                <div className="w-2 h-2 bg-white/70 rounded-full animate-bounce" style={{ animationDelay: '0.2s' }} />
              </div>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Input */}
      <div className="bg-white/10 backdrop-blur-sm border-t border-white/20 px-4 py-3">
        <form onSubmit={handleSend} className="flex gap-2">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Ask Coachie anything..."
            className="flex-1 px-4 py-2 border border-white/30 rounded-lg bg-white/10 text-white placeholder-white/50 focus:ring-2 focus:ring-white/50 focus:border-transparent"
            disabled={loading}
          />
          <button
            type="submit"
            disabled={loading || !input.trim()}
            className="px-6 py-2 bg-white/20 text-white rounded-lg hover:bg-white/30 disabled:opacity-50 font-medium"
          >
            Send
          </button>
        </form>
      </div>
    </div>
  )
}
