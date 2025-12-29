'use client'

import { useState, useEffect } from 'react'
import { useAuth } from '../lib/contexts/AuthContext'
import { collection, query, where, getDocs, orderBy, limit, Timestamp } from 'firebase/firestore'
import { db } from '../lib/firebase'
import CoachieCard from './ui/CoachieCard'

interface WinEntry {
  id: string
  date: string
  content: string
  extractedFrom: string
  mood?: string
  createdAt: Date
}

export default function MyWins() {
  const { user } = useAuth()
  const [wins, setWins] = useState<WinEntry[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [searchQuery, setSearchQuery] = useState('')

  useEffect(() => {
    if (user) {
      loadWins()
    }
  }, [user])

  const loadWins = async () => {
    if (!user) return

    try {
      setIsLoading(true)
      
      // Query win entries from Firestore
      // Wins are stored as HealthLog.WinEntry in healthLogs collection
      const winsQuery = query(
        collection(db, 'healthLogs'),
        where('userId', '==', user.uid),
        where('type', '==', 'win'),
        orderBy('timestamp', 'desc'),
        limit(50)
      )
      
      const snapshot = await getDocs(winsQuery)
      const loadedWins: WinEntry[] = snapshot.docs.map(doc => {
        const data = doc.data()
        return {
          id: doc.id,
          date: data.date || '',
          content: data.content || '',
          extractedFrom: data.extractedFrom || '',
          mood: data.mood,
          createdAt: data.timestamp?.toDate() || new Date()
        }
      })
      
      setWins(loadedWins)
    } catch (error) {
      console.error('Error loading wins:', error)
    } finally {
      setIsLoading(false)
    }
  }

  const filteredWins = wins.filter(win =>
    win.content.toLowerCase().includes(searchQuery.toLowerCase()) ||
    win.date.includes(searchQuery)
  )

  const formatDate = (dateString: string) => {
    try {
      const date = new Date(dateString)
      return date.toLocaleDateString('en-US', { 
        year: 'numeric', 
        month: 'long', 
        day: 'numeric' 
      })
    } catch {
      return dateString
    }
  }

  if (isLoading && wins.length === 0) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-center">
          <div className="text-4xl mb-4">🏆</div>
          <p className="text-gray-600">Loading your wins...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <h1 className="text-3xl font-bold text-gray-900">My Wins</h1>

      {/* Search */}
      <div className="relative">
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Search your wins..."
          className="w-full px-4 py-3 pl-10 border border-gray-300 rounded-full focus:outline-none focus:ring-2 focus:ring-primary-500"
        />
        <span className="absolute left-3 top-1/2 transform -translate-y-1/2">🔍</span>
      </div>

      {/* Wins List */}
      {filteredWins.length === 0 ? (
        <CoachieCard>
          <div className="p-12 text-center">
            <div className="text-6xl mb-4">🏆</div>
            <h2 className="text-xl font-semibold text-gray-900 mb-2">No Wins Yet</h2>
            <p className="text-gray-600">
              Your wins will appear here as Coachie extracts achievements from your journal entries.
            </p>
          </div>
        </CoachieCard>
      ) : (
        <div className="space-y-4">
          {filteredWins.map((win) => (
            <CoachieCard key={win.id}>
              <div className="p-6">
                <div className="flex items-start justify-between mb-2">
                  <div className="text-sm text-gray-500">{formatDate(win.date)}</div>
                  {win.mood && (
                    <span className="px-2 py-1 text-xs bg-primary-100 text-primary-700 rounded-full">
                      {win.mood}
                    </span>
                  )}
                </div>
                <p className="text-gray-900 text-lg mb-2">{win.content}</p>
                {win.extractedFrom && (
                  <p className="text-sm text-gray-500 italic">
                    From: {win.extractedFrom}
                  </p>
                )}
              </div>
            </CoachieCard>
          ))}
        </div>
      )}
    </div>
  )
}

