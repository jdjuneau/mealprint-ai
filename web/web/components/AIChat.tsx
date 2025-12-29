'use client'

import { useState, useEffect, useRef } from 'react'
// Using emoji instead of Heroicons
import { AIService } from '../lib/services/ai'
import { FirebaseService } from '../lib/services/firebase'
import { speechRecognitionService } from '../lib/services/speechRecognitionService'
import { ttsService } from '../lib/services/ttsService'
import type { ChatMessage } from '../types'
import toast from 'react-hot-toast'

interface AIChatProps {
  userId: string
  userName: string
}

export default function AIChat({ userId, userName }: AIChatProps) {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [inputMessage, setInputMessage] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [loading, setLoading] = useState(true)
  const [isListening, setIsListening] = useState(false)
  const [voiceEnabled, setVoiceEnabled] = useState(true)
  const [remainingMessages, setRemainingMessages] = useState<number | null>(null)
  const [isPro, setIsPro] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    loadChatHistory()
    loadRemainingMessages()
    // Check if speech recognition is supported
    if (!speechRecognitionService.isSupported()) {
      setVoiceEnabled(false)
    }
  }, [])

  const loadRemainingMessages = async () => {
    try {
      const SubscriptionService = (await import('../lib/services/subscriptionService')).default
      const subscriptionService = SubscriptionService.getInstance()
      
      const pro = await subscriptionService.isPro(userId)
      setIsPro(pro)
      
      if (!pro) {
        const remaining = await subscriptionService.getRemainingAICalls(userId, 'ai_coach_chat')
        setRemainingMessages(remaining)
      } else {
        setRemainingMessages(null) // Unlimited for Pro
      }
    } catch (error) {
      console.error('Error loading remaining messages:', error)
    }
  }

  useEffect(() => {
    scrollToBottom()
  }, [messages])

  // Auto-speak AI responses if TTS is enabled
  useEffect(() => {
    if (messages.length > 0 && !messages[messages.length - 1].isUser && voiceEnabled) {
      const lastMessage = messages[messages.length - 1]
      if (lastMessage.content && ttsService.isSupported()) {
        ttsService.speak(lastMessage.content, 'insights')
      }
    }
  }, [messages, voiceEnabled])

  const loadChatHistory = async () => {
    try {
      const chatMessages = await FirebaseService.getChatMessages(userId, 20)
      setMessages(chatMessages)
    } catch (error) {
      console.error('Error loading chat history:', error)
      toast.error('Failed to load chat history')
    } finally {
      setLoading(false)
    }
  }

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  const sendMessage = async () => {
    if (!inputMessage.trim() || isLoading) return

    const userMessage: ChatMessage = {
      id: `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      userId,
      content: inputMessage.trim(),
      isUser: true,
      timestamp: new Date(),
    }

    // Add user message immediately
    setMessages(prev => [...prev, userMessage])
    setInputMessage('')
    setIsLoading(true)

    try {
      // Get conversation history for context (last 5 messages)
      const recentMessages = [...messages.slice(-4), userMessage]
      const conversationHistory = recentMessages.map(msg => ({
        role: msg.isUser ? 'user' as const : 'assistant' as const,
        content: msg.content,
      }))

      // Check remaining messages for free users before sending
      if (!isPro && remainingMessages !== null && remainingMessages <= 0) {
        toast.error('You have reached your daily message limit. Upgrade to Pro for unlimited messages!')
        setMessages(prev => prev.slice(0, -1)) // Remove user message
        setIsLoading(false)
        return
      }

      // Generate AI response
      const aiResponse = await AIService.generateChatResponse(
        userId,
        userMessage.content,
        userName,
        conversationHistory
      )

      const aiMessage: ChatMessage = {
        id: `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        userId,
        content: aiResponse,
        isUser: false,
        timestamp: new Date(),
      }

      // Add AI response
      setMessages(prev => [...prev, aiMessage])

      // Save both messages to Firebase
      await FirebaseService.saveChatMessage(userId, userMessage)
      await FirebaseService.saveChatMessage(userId, aiMessage)

      // Record usage and update remaining count for free users
      if (!isPro) {
        try {
          const SubscriptionService = (await import('../lib/services/subscriptionService')).default
          const subscriptionService = SubscriptionService.getInstance()
          await subscriptionService.recordAIFeatureUsage(userId, 'ai_coach_chat')
          await loadRemainingMessages() // Refresh count
        } catch (error) {
          console.error('Error recording AI usage:', error)
        }
      }

    } catch (error) {
      console.error('Error sending message:', error)
      toast.error('Failed to send message. Please try again.')

      // Remove the user message if sending failed
      setMessages(prev => prev.slice(0, -1))
    } finally {
      setIsLoading(false)
    }
  }

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendMessage()
    }
  }

  const startVoiceInput = async () => {
    // Check microphone permission
    const hasPermission = await speechRecognitionService.checkMicrophonePermission()
    if (!hasPermission) {
      const granted = await speechRecognitionService.requestMicrophonePermission()
      if (!granted) {
        toast.error('Microphone permission is required for voice input')
        return
      }
    }

    speechRecognitionService.startListening(
      {
        continuous: false,
        interimResults: true,
        lang: 'en-US',
        maxAlternatives: 1,
      },
      {
        onStart: () => {
          setIsListening(true)
        },
        onResult: (transcript, isFinal) => {
          if (isFinal) {
            setInputMessage(transcript)
            setIsListening(false)
            // Auto-send if voice command
            if (!isLoading) {
              sendMessage()
            }
          } else {
            // Show interim results in input
            setInputMessage(transcript)
          }
        },
        onError: (errorMessage) => {
          setIsListening(false)
          toast.error(errorMessage)
        },
        onEnd: () => {
          setIsListening(false)
        },
      }
    )
  }

  const stopVoiceInput = () => {
    speechRecognitionService.stopListening()
    setIsListening(false)
  }

  if (loading) {
    return (
      <div className="max-w-4xl mx-auto">
        <div className="bg-white rounded-lg shadow-sm h-96 flex items-center justify-center">
          <div className="text-center">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600 mx-auto mb-2"></div>
            <p className="text-gray-500">Loading chat...</p>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="max-w-4xl mx-auto">
      <div className="bg-white rounded-lg shadow-sm h-[600px] flex flex-col">
        {/* Chat Header */}
        <div className="p-4 border-b border-gray-200">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-xl font-semibold text-gray-900">🤖 AI Coach Chat</h1>
              <p className="text-sm text-gray-600">
                Get personalized fitness advice from your AI coach
              </p>
            </div>
          </div>
        </div>

        {/* Remaining Messages Badge for Free Users */}
        {!isPro && remainingMessages !== null && (
          <div className={`px-4 py-2 mx-4 mt-2 rounded-lg ${
            remainingMessages > 0 
              ? 'bg-blue-50 border border-blue-200' 
              : 'bg-red-50 border border-red-200'
          }`}>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="text-lg">{remainingMessages > 0 ? '💬' : '⚠️'}</span>
                <div>
                  <div className={`text-sm font-medium ${
                    remainingMessages > 0 ? 'text-blue-700' : 'text-red-700'
                  }`}>
                    {remainingMessages} {remainingMessages === 1 ? 'message' : 'messages'} remaining today
                  </div>
                  {remainingMessages <= 3 && (
                    <div className="text-xs text-gray-600 mt-0.5">
                      Upgrade to Pro for unlimited messages
                    </div>
                  )}
                </div>
              </div>
              {remainingMessages <= 3 && (
                <button
                  onClick={() => window.location.href = '/subscription'}
                  className="px-3 py-1 text-xs bg-blue-600 text-white rounded-lg hover:bg-blue-700"
                >
                  Upgrade
                </button>
              )}
            </div>
          </div>
        )}

        {/* Messages */}
        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          {messages.length === 0 ? (
            <div className="text-center py-8">
              <div className="text-6xl mb-4">💬</div>
              <h3 className="text-lg font-medium text-gray-900 mb-2">
                Start a conversation with your AI coach!
              </h3>
              <p className="text-gray-600">
                Ask questions about your fitness goals, workout plans, nutrition advice, or anything health-related.
              </p>
            </div>
          ) : (
            messages.map((message) => (
              <div
                key={message.id}
                className={`flex ${message.isUser ? 'justify-end' : 'justify-start'}`}
              >
                <div
                  className={`max-w-xs lg:max-w-md px-4 py-2 rounded-lg ${
                    message.isUser
                      ? 'bg-primary-600 text-white'
                      : 'bg-gray-100 text-gray-900'
                  }`}
                >
                  <p className="text-sm whitespace-pre-wrap">{message.content}</p>
                  <p className={`text-xs mt-1 ${
                    message.isUser ? 'text-primary-100' : 'text-gray-500'
                  }`}>
                    {message.timestamp.toLocaleTimeString([], {
                      hour: '2-digit',
                      minute: '2-digit'
                    })}
                  </p>
                </div>
              </div>
            ))
          )}

          {isLoading && (
            <div className="flex justify-start">
              <div className="bg-gray-100 px-4 py-2 rounded-lg">
                <div className="flex items-center space-x-2">
                  <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-gray-400"></div>
                  <span className="text-sm text-gray-600">Coachie is typing...</span>
                </div>
              </div>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        {/* Input */}
        <div className="p-4 border-t border-gray-200">
          <div className="flex space-x-2">
            <input
              type="text"
              value={inputMessage}
              onChange={(e) => setInputMessage(e.target.value)}
              onKeyPress={handleKeyPress}
              placeholder={isListening ? "Listening..." : "Ask your AI coach anything about fitness..."}
              className="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              disabled={isLoading || isListening}
            />
            {voiceEnabled && (
              <button
                onClick={isListening ? stopVoiceInput : startVoiceInput}
                disabled={isLoading}
                className={`p-2 rounded-lg ${
                  isListening
                    ? 'bg-red-600 text-white hover:bg-red-700'
                    : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                } disabled:opacity-50 disabled:cursor-not-allowed`}
                title={isListening ? 'Stop listening' : 'Start voice input'}
              >
                <span className="text-lg">{isListening ? '⏹️' : '🎤'}</span>
              </button>
            )}
            <button
              onClick={sendMessage}
              disabled={!inputMessage.trim() || isLoading || isListening}
              className="bg-primary-600 text-white p-2 rounded-lg hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <span className="text-lg">📤</span>
            </button>
          </div>
          <p className="text-xs text-gray-500 mt-2">
            {isListening 
              ? '🎤 Listening... Speak your message'
              : 'Press Enter to send, Shift+Enter for new line, or use 🎤 for voice input'
            }
          </p>
        </div>
      </div>

      {/* Quick Suggestions */}
      {messages.length === 0 && (
        <div className="mt-6 bg-white rounded-lg shadow-sm p-6">
          <h3 className="text-lg font-semibold text-gray-900 mb-4">💡 Quick Questions to Get Started</h3>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {[
              "What's a good workout plan for weight loss?",
              "How many calories should I eat per day?",
              "What foods help with muscle recovery?",
              "How can I stay motivated with my fitness goals?",
              "What's the best time to work out?",
              "How do I track my macros properly?"
            ].map((question, index) => (
              <button
                key={index}
                onClick={() => setInputMessage(question)}
                className="text-left p-3 border border-gray-200 rounded-lg hover:bg-gray-50 hover:border-primary-300 transition-colors"
              >
                <span className="text-sm text-gray-700">{question}</span>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
