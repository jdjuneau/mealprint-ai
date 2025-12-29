'use client'

import { useState, useEffect, useRef } from 'react'
import { useAuth } from '../lib/contexts/AuthContext'
import { FirebaseService } from '../lib/services/firebase'
import { functions } from '../lib/firebase'
import { httpsCallable } from 'firebase/functions'
import { doc, getDoc, setDoc, updateDoc } from 'firebase/firestore'
import { db } from '../lib/firebase'
import CoachieCard from './ui/CoachieCard'
import CoachieButton from './ui/CoachieButton'

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: number
}

interface JournalEntry {
  entryId: string
  date: string
  prompts: string[]
  conversation: ChatMessage[]
  startedAt: number
  completedAt?: number
  wordCount?: number
  mood?: string
  isCompleted?: boolean
}

function getCurrentDateString(): string {
  const today = new Date()
  return today.toISOString().split('T')[0] // YYYY-MM-DD
}

function getTimeOfDay(): { greeting: string; emoji: string; period: string } {
  const hour = new Date().getHours()
  if (hour >= 5 && hour < 12) {
    return { greeting: 'Good morning', emoji: '☀️', period: 'Morning' }
  } else if (hour >= 12 && hour < 17) {
    return { greeting: 'Good afternoon', emoji: '🌤️', period: 'Afternoon' }
  } else if (hour >= 17 && hour < 21) {
    return { greeting: 'Good evening', emoji: '🌙', period: 'Evening' }
  } else {
    return { greeting: 'Good night', emoji: '🌙', period: 'Night' }
  }
}

export default function JournalFlow() {
  const { user, userProfile } = useAuth()
  const [prompts, setPrompts] = useState<string[]>([])
  const [conversation, setConversation] = useState<ChatMessage[]>([])
  const [currentInput, setCurrentInput] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isGeneratingResponse, setIsGeneratingResponse] = useState(false)
  const [currentEntry, setCurrentEntry] = useState<JournalEntry | null>(null)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const timeOfDay = getTimeOfDay()
  const today = getCurrentDateString()

  useEffect(() => {
    if (user) {
      loadTodaysEntry()
    }
  }, [user])

  useEffect(() => {
    scrollToBottom()
  }, [conversation, isGeneratingResponse])

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  const loadTodaysEntry = async () => {
    if (!user) return

    try {
      setIsLoading(true)
      
      // Try to load existing journal entry for today
      // Journal entries are stored in healthLogs collection
      const healthLogsRef = doc(db, 'logs', user.uid, 'daily', today, 'entries', 'journal')
      const entryDoc = await getDoc(healthLogsRef)

      if (entryDoc.exists()) {
        const data = entryDoc.data()
        const entry: JournalEntry = {
          entryId: data.entryId || `journal_${Date.now()}`,
          date: data.date || today,
          prompts: data.prompts || [],
          conversation: (data.conversation || []).map((msg: any) => ({
            id: msg.id || `msg_${Date.now()}`,
            role: msg.role || 'user',
            content: msg.content || '',
            timestamp: msg.timestamp || Date.now()
          })),
          startedAt: data.startedAt || Date.now(),
          completedAt: data.completedAt,
          wordCount: data.wordCount,
          mood: data.mood,
          isCompleted: data.isCompleted || false
        }
        
        setCurrentEntry(entry)
        setPrompts(entry.prompts)
        setConversation(entry.conversation)
      } else {
        // Generate new prompts
        await generatePrompts()
      }
    } catch (error) {
      console.error('Error loading journal entry:', error)
      // Fallback prompts
      setPrompts([
        "What drained you most today?",
        "One thing your body carried you through today?",
        "What would make tomorrow feel 1% calmer?"
      ])
    } finally {
      setIsLoading(false)
    }
  }

  const generatePrompts = async () => {
    if (!user || !userProfile) {
      // Fallback prompts
      setPrompts([
        "What drained you most today?",
        "One thing your body carried you through today?",
        "What would make tomorrow feel 1% calmer?"
      ])
      return
    }

    try {
      // Get today's logs for context
      const todayLog = await FirebaseService.getDailyLog(user.uid, today)
      
      // Generate personalized prompts based on today's data
      const generatedPrompts: string[] = []
      
      // Analyze mood/energy levels
      if (todayLog?.logs) {
        const moodLogs = todayLog.logs.filter(log => log.type === 'mood')
        const workoutLogs = todayLog.logs.filter(log => log.type === 'workout')
        const sleepLogs = todayLog.logs.filter(log => log.type === 'sleep')
        
        // Prompt 1: Based on energy/stress
        if (moodLogs.length > 0) {
          generatedPrompts.push("What drained you most today?")
        } else {
          generatedPrompts.push("What drained you most today?")
        }
        
        // Prompt 2: Based on physical activity
        if (workoutLogs.length > 0) {
          generatedPrompts.push("One thing your body carried you through today?")
        } else {
          generatedPrompts.push("How did your body feel throughout the day?")
        }
        
        // Prompt 3: Based on sleep/planning
        if (sleepLogs.length > 0) {
          generatedPrompts.push("What would make tomorrow feel 1% calmer?")
        } else {
          generatedPrompts.push("What would make tomorrow feel 1% calmer?")
        }
      } else {
        // Default prompts
        generatedPrompts.push(
          "What drained you most today?",
          "One thing your body carried you through today?",
          "What would make tomorrow feel 1% calmer?"
        )
      }
      
      setPrompts(generatedPrompts.slice(0, 3))
      
      // Create new entry
      const entry: JournalEntry = {
        entryId: `journal_${Date.now()}`,
        date: today,
        prompts: generatedPrompts.slice(0, 3),
        conversation: [],
        startedAt: Date.now()
      }
      setCurrentEntry(entry)
      
    } catch (error) {
      console.error('Error generating prompts:', error)
      // Fallback prompts
      setPrompts([
        "What drained you most today?",
        "One thing your body carried you through today?",
        "What would make tomorrow feel 1% calmer?"
      ])
    }
  }

  const sendMessage = async () => {
    if (!currentInput.trim() || isGeneratingResponse || !user) return

    const userMessage: ChatMessage = {
      id: `msg_${Date.now()}`,
      role: 'user',
      content: currentInput.trim(),
      timestamp: Date.now()
    }

    // Add user message immediately
    const newConversation = [...conversation, userMessage]
    setConversation(newConversation)
    setCurrentInput('')
    setIsGeneratingResponse(true)

    try {
      // Generate AI response
      const response = await generateAIResponse(userMessage.content, newConversation)

      const aiMessage: ChatMessage = {
        id: `msg_${Date.now()}_ai`,
        role: 'assistant',
        content: response,
        timestamp: Date.now()
      }

      const updatedConversation = [...newConversation, aiMessage]
      setConversation(updatedConversation)

      // Save to Firestore
      await saveConversation(updatedConversation)
    } catch (error) {
      console.error('Error sending message:', error)
      alert('Failed to send message. Please try again.')
      // Remove user message on error
      setConversation(conversation)
    } finally {
      setIsGeneratingResponse(false)
    }
  }

  const generateAIResponse = async (userMessage: string, conversationHistory: ChatMessage[]): Promise<string> => {
    try {
      // Call Cloud Function to generate journal response
      const generateResponse = httpsCallable(functions, 'generateJournalResponse')
      const result = await generateResponse({
        userId: user?.uid,
        userMessage,
        conversationHistory: conversationHistory.map(msg => ({
          role: msg.role,
          content: msg.content
        }))
      })

      return result.data as string
    } catch (error) {
      console.error('Error generating AI response:', error)
      // Fallback response
      return "Thank you for sharing. How did that make you feel?"
    }
  }

  const saveConversation = async (messages: ChatMessage[]) => {
    if (!user || !currentEntry) return

    try {
      const entryRef = doc(db, 'logs', user.uid, 'daily', today, 'entries', 'journal')
      const wordCount = messages
        .filter(m => m.role === 'user')
        .reduce((sum, m) => sum + m.content.split(/\s+/).length, 0)

      await setDoc(entryRef, {
        type: 'journal',
        entryId: currentEntry.entryId,
        date: today,
        prompts: prompts,
        conversation: messages.map(msg => ({
          id: msg.id,
          role: msg.role,
          content: msg.content,
          timestamp: msg.timestamp
        })),
        startedAt: currentEntry.startedAt,
        wordCount: wordCount,
        isCompleted: false
      }, { merge: true })
    } catch (error) {
      console.error('Error saving conversation:', error)
    }
  }

  const completeJournal = async () => {
    if (!user || !currentEntry) return

    try {
      const entryRef = doc(db, 'logs', user.uid, 'daily', today, 'entries', 'journal')
      const wordCount = conversation
        .filter(m => m.role === 'user')
        .reduce((sum, m) => sum + m.content.split(/\s+/).length, 0)

      await updateDoc(entryRef, {
        completedAt: Date.now(),
        wordCount: wordCount,
        isCompleted: true
      })

      alert('Journal entry completed!')
    } catch (error) {
      console.error('Error completing journal:', error)
    }
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-center">
          <div className="text-4xl mb-4">📔</div>
          <p className="text-gray-600">Loading your journal...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6 pb-8">
      {/* Header */}
      <div className="flex justify-between items-center">
        <h1 className="text-3xl font-bold text-gray-900">{timeOfDay.period} Journal</h1>
        {conversation.length > 0 && (
          <CoachieButton onClick={completeJournal}>
            Complete
          </CoachieButton>
        )}
      </div>

      {/* Prompts Section */}
      {prompts.length > 0 && (
        <CoachieCard>
          <div className="p-6">
            <h2 className="text-xl font-bold mb-4">Today's Reflection Prompts</h2>
            <div className="space-y-3">
              {prompts.map((prompt, index) => (
                <div key={index} className="flex gap-3">
                  <span className="font-bold text-primary-600">{index + 1}.</span>
                  <p className="flex-1 text-gray-700">{prompt}</p>
                </div>
              ))}
            </div>
            <p className="text-sm text-gray-500 text-center mt-4">
              Share whatever feels right - Coachie is here to listen.
            </p>
          </div>
        </CoachieCard>
      )}

      {/* Conversation */}
      <CoachieCard>
        <div className="p-6 min-h-[400px] max-h-[600px] overflow-y-auto">
          {conversation.length === 0 && (
            <div className="text-center py-8">
              <div className="text-5xl mb-4">{timeOfDay.emoji}</div>
              <h3 className="text-xl font-semibold text-gray-900 mb-2">
                {timeOfDay.greeting}!
              </h3>
              <p className="text-gray-600">
                Share your thoughts and reflections. Coachie is here to listen and guide you.
              </p>
            </div>
          )}
          
          <div className="space-y-4">
            {conversation.map((message) => (
              <div
                key={message.id}
                className={`flex ${message.role === 'user' ? 'justify-end' : 'justify-start'}`}
              >
                <div
                  className={`max-w-[80%] rounded-lg p-4 ${
                    message.role === 'user'
                      ? 'bg-primary-600 text-white'
                      : 'bg-gray-100 text-gray-900'
                  }`}
                >
                  <p className="whitespace-pre-wrap">{message.content}</p>
                </div>
              </div>
            ))}
            
            {isGeneratingResponse && (
              <div className="flex justify-start">
                <div className="bg-gray-100 rounded-lg p-4">
                  <div className="flex gap-1">
                    <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }}></div>
                    <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }}></div>
                    <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }}></div>
                  </div>
                </div>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>
        </div>
      </CoachieCard>

      {/* Input Section */}
      <CoachieCard>
        <div className="p-4">
          <div className="flex gap-2">
            <textarea
              value={currentInput}
              onChange={(e) => setCurrentInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault()
                  sendMessage()
                }
              }}
              placeholder="Share your thoughts..."
              className="flex-1 px-4 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
              rows={3}
              disabled={isGeneratingResponse}
            />
            <CoachieButton
              onClick={sendMessage}
              disabled={!currentInput.trim() || isGeneratingResponse}
            >
              Send
            </CoachieButton>
          </div>
        </div>
      </CoachieCard>
    </div>
  )
}

