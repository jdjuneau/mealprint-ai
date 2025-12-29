/**
 * Quest Auto-Completion Service (Web Version)
 * Ported from Android QuestAutoCompletionService.kt
 * Automatically completes quests based on user activity
 */

import { db } from '../firebase'
import { doc, getDoc, setDoc, updateDoc, Timestamp } from 'firebase/firestore'

interface Quest {
  id: string
  title: string
  description: string
  type: string
  target: number
  progress: number
  completed: boolean
  completedAt?: Date
}

class QuestAutoCompletionService {
  private static instance: QuestAutoCompletionService

  private constructor() {}

  static getInstance(): QuestAutoCompletionService {
    if (!QuestAutoCompletionService.instance) {
      QuestAutoCompletionService.instance = new QuestAutoCompletionService()
    }
    return QuestAutoCompletionService.instance
  }

  /**
   * Called when a meal is logged
   */
  async onMealLogged(userId: string): Promise<void> {
    try {
      await this.updateQuestProgress(userId, 'log_meals', 1)
    } catch (error) {
      console.error('Error updating meal quest:', error)
    }
  }

  /**
   * Called when a workout is logged
   */
  async onWorkoutLogged(userId: string): Promise<void> {
    try {
      await this.updateQuestProgress(userId, 'log_workouts', 1)
    } catch (error) {
      console.error('Error updating workout quest:', error)
    }
  }

  /**
   * Update quest progress
   */
  private async updateQuestProgress(
    userId: string,
    questType: string,
    increment: number
  ): Promise<void> {
    try {
      const questRef = doc(db, 'users', userId, 'quests', questType)
      const questSnap = await getDoc(questRef)

      if (questSnap.exists()) {
        const data = questSnap.data()
        const currentProgress = data.progress || 0
        const target = data.target || 1
        const newProgress = currentProgress + increment

        await updateDoc(questRef, {
          progress: newProgress,
          completed: newProgress >= target,
          completedAt: newProgress >= target ? Timestamp.now() : null,
          platform: 'web',
        })
      }
    } catch (error) {
      console.error('Error updating quest progress:', error)
    }
  }
}

export default QuestAutoCompletionService
