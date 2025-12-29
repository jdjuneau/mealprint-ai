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
  Timestamp
} from 'firebase/firestore'
import { db } from '../firebase'

export interface CoachieScore {
  date: string
  score: number
  healthScore: number
  wellnessScore: number
  habitsScore: number
  calculatedAt: Date
}

export interface CoachieScoreStats {
  averageScore: number
  totalDays: number
  highestScore: number
  highestScoreDate: string | null
  last7DaysAverage: number | null
  last30DaysAverage: number | null
}

/**
 * Service for managing Coachie scores (cross-platform with Android)
 * Scores are stored in users/{userId}/scores/{date} collection
 */
export class CoachieScoreService {
  /**
   * Save or update today's Coachie score
   */
  static async saveTodayScore(
    userId: string,
    score: number,
    healthScore: number,
    wellnessScore: number,
    habitsScore: number
  ): Promise<boolean> {
    try {
      const today = new Date().toISOString().split('T')[0]
      
      const scoreData = {
        date: today,
        score,
        healthScore,
        wellnessScore,
        habitsScore,
        platform: 'web', // Track platform for analytics
        calculatedAt: Timestamp.now()
      }
      
      await setDoc(
        doc(db, 'users', userId, 'scores', today),
        scoreData,
        { merge: true } // Merge to preserve existing data
      )
      
      return true
    } catch (error) {
      console.error('Error saving Coachie score:', error)
      return false
    }
  }
  
  /**
   * Get today's score
   */
  static async getTodayScore(userId: string): Promise<CoachieScore | null> {
    try {
      const today = new Date().toISOString().split('T')[0]
      return await this.getScoreForDate(userId, today)
    } catch (error) {
      console.error('Error getting today\'s score:', error)
      return null
    }
  }
  
  /**
   * Get score for a specific date (YYYY-MM-DD)
   */
  static async getScoreForDate(userId: string, date: string): Promise<CoachieScore | null> {
    try {
      const scoreDoc = await getDoc(
        doc(db, 'users', userId, 'scores', date)
      )
      
      if (scoreDoc.exists()) {
        const data = scoreDoc.data()
        return {
          date: data.date,
          score: data.score,
          healthScore: data.healthScore,
          wellnessScore: data.wellnessScore,
          habitsScore: data.habitsScore,
          calculatedAt: data.calculatedAt?.toDate() || new Date()
        }
      }
      
      return null
    } catch (error) {
      console.error('Error getting score for date:', error)
      return null
    }
  }
  
  /**
   * Get all scores for a user
   */
  static async getAllScores(userId: string): Promise<CoachieScore[]> {
    try {
      const scoresQuery = query(
        collection(db, 'users', userId, 'scores'),
        orderBy('date', 'desc')
      )
      
      const snapshot = await getDocs(scoresQuery)
      return snapshot.docs.map(doc => {
        const data = doc.data()
        return {
          date: data.date,
          score: data.score,
          healthScore: data.healthScore,
          wellnessScore: data.wellnessScore,
          habitsScore: data.habitsScore,
          calculatedAt: data.calculatedAt?.toDate() || new Date()
        }
      })
    } catch (error) {
      console.error('Error getting all scores:', error)
      return []
    }
  }
  
  /**
   * Get score statistics
   */
  static async getScoreStats(userId: string): Promise<CoachieScoreStats> {
    try {
      const scores = await this.getAllScores(userId)
      
      if (scores.length === 0) {
        return {
          averageScore: 0,
          totalDays: 0,
          highestScore: 0,
          highestScoreDate: null,
          last7DaysAverage: null,
          last30DaysAverage: null
        }
      }
      
      const averageScore = scores.reduce((sum, s) => sum + s.score, 0) / scores.length
      const highestScoreEntry = scores.reduce((max, s) => s.score > max.score ? s : max, scores[0])
      const highestScore = highestScoreEntry?.score || 0
      const highestScoreDate = highestScoreEntry?.date || null
      
      const last7Days = scores.slice(0, 7)
      const last7DaysAverage = last7Days.length > 0
        ? last7Days.reduce((sum, s) => sum + s.score, 0) / last7Days.length
        : null
      
      const last30Days = scores.slice(0, 30)
      const last30DaysAverage = last30Days.length > 0
        ? last30Days.reduce((sum, s) => sum + s.score, 0) / last30Days.length
        : null
      
      return {
        averageScore,
        totalDays: scores.length,
        highestScore,
        highestScoreDate,
        last7DaysAverage,
        last30DaysAverage
      }
    } catch (error) {
      console.error('Error getting score stats:', error)
      return {
        averageScore: 0,
        totalDays: 0,
        highestScore: 0,
        highestScoreDate: null,
        last7DaysAverage: null,
        last30DaysAverage: null
      }
    }
  }
}

