/**
 * Quests Service (Web Version)
 * Manages user quests and achievements
 */

import { db } from '../firebase'
import {
  collection,
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  query,
  where,
  orderBy,
  Timestamp,
} from 'firebase/firestore'

export interface Quest {
  id: string
  userId: string
  title: string
  description: string
  type: string
  target: number
  progress: number
  status: 'active' | 'completed' | 'locked'
  reward?: string
  createdAt: Date
  completedAt?: Date
}

class QuestsService {
  private static instance: QuestsService

  private constructor() {}

  static getInstance(): QuestsService {
    if (!QuestsService.instance) {
      QuestsService.instance = new QuestsService()
    }
    return QuestsService.instance
  }

  /**
   * Get all active quests for a user
   */
  async getQuests(userId: string): Promise<Quest[]> {
    try {
      const questsRef = collection(db, 'users', userId, 'quests')
      const questsQuery = query(
        questsRef,
        where('status', 'in', ['active', 'in_progress']),
        orderBy('createdAt', 'desc')
      )
      const questsSnap = await getDocs(questsQuery)

      return questsSnap.docs.map((doc) => {
        const data = doc.data()
        return {
          id: doc.id,
          userId,
          ...data,
          progress: data.current || data.progress || 0,
          target: data.target || 1,
          createdAt: data.createdAt?.toDate() || new Date(),
          completedAt: data.completedAt?.toDate(),
        } as Quest
      })
    } catch (error) {
      console.error('Error getting quests:', error)
      return []
    }
  }

  /**
   * Update quest progress
   */
  async updateQuestProgress(userId: string, questId: string, increment: number): Promise<void> {
    try {
      const questRef = doc(db, 'users', userId, 'quests', questId)
      const questSnap = await getDoc(questRef)

      if (questSnap.exists()) {
        const data = questSnap.data()
        const currentProgress = data.current || data.progress || 0
        const target = data.target || 1
        const newProgress = currentProgress + increment

        await updateDoc(questRef, {
          current: newProgress,
          progress: newProgress,
          status: newProgress >= target ? 'completed' : 'active',
          completedAt: newProgress >= target ? Timestamp.now() : null,
          platform: 'web',
        })
      }
    } catch (error) {
      console.error('Error updating quest progress:', error)
    }
  }
}

export default QuestsService
