/**
 * Daily Mindful Session Generator (Web Version)
 * Ported from Android DailyMindfulSessionGenerator.kt
 * Generates personalized daily mindfulness sessions using AI
 */

import { functions } from '../firebase'
import { httpsCallable } from 'firebase/functions'

export interface MindfulSession {
  sessionId: string
  title: string
  transcript: string
  durationSeconds: number
  generatedDate: string
  personalizedPrompt: string
  audioUrl?: string
  isFavorite: boolean
  playedCount: number
  timestamp: Date
}

class DailyMindfulSessionGenerator {
  private static instance: DailyMindfulSessionGenerator

  private constructor() {}

  static getInstance(): DailyMindfulSessionGenerator {
    if (!DailyMindfulSessionGenerator.instance) {
      DailyMindfulSessionGenerator.instance = new DailyMindfulSessionGenerator()
    }
    return DailyMindfulSessionGenerator.instance
  }

  /**
   * Generate today's mindful session
   */
  async generateDailySession(userId: string): Promise<MindfulSession | null> {
    try {
      // Call Firebase Cloud Function
      const generateSession = httpsCallable(functions, 'generateDailyMindfulSession')
      const result = await generateSession({ userId })

      const data = result.data as any
      if (data && data.session) {
        return {
          sessionId: data.session.sessionId || data.session.id,
          title: data.session.title,
          transcript: data.session.transcript,
          durationSeconds: data.session.durationSeconds || 300,
          generatedDate: data.session.generatedDate || new Date().toISOString().split('T')[0],
          personalizedPrompt: data.session.personalizedPrompt || '',
          audioUrl: data.session.audioUrl,
          isFavorite: false,
          playedCount: 0,
          timestamp: new Date(),
        }
      }

      return null
    } catch (error) {
      console.error('Error generating mindful session:', error)
      return null
    }
  }

  /**
   * Get today's session (generate if doesn't exist)
   */
  async getTodaysSession(userId: string): Promise<MindfulSession | null> {
    // TODO: Check if session exists for today, if not generate
    return this.generateDailySession(userId)
  }
}

export default DailyMindfulSessionGenerator
