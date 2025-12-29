/**
 * Circles Service (Web Version)
 * Manages fitness circles (groups)
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

export interface Circle {
  id: string
  name: string
  description: string
  goal: string
  creatorId: string
  memberIds: string[]
  isPrivate: boolean
  createdAt: Date
}

class CirclesService {
  private static instance: CirclesService

  private constructor() {}

  static getInstance(): CirclesService {
    if (!CirclesService.instance) {
      CirclesService.instance = new CirclesService()
    }
    return CirclesService.instance
  }

  /**
   * Create a new circle
   */
  async createCircle(userId: string, circleData: Omit<Circle, 'id' | 'creatorId' | 'memberIds' | 'createdAt'>): Promise<string> {
    try {
      const circlesRef = collection(db, 'circles')
      const circleRef = doc(circlesRef)

      await setDoc(circleRef, {
        ...circleData,
        creatorId: userId,
        memberIds: [userId],
        createdAt: Timestamp.now(),
        platform: 'web',
      })

      return circleRef.id
    } catch (error) {
      console.error('Error creating circle:', error)
      throw error
    }
  }

  /**
   * Get a circle by ID
   */
  async getCircle(circleId: string): Promise<Circle | null> {
    try {
      const circleRef = doc(db, 'circles', circleId)
      const circleSnap = await getDoc(circleRef)

      if (circleSnap.exists()) {
        const data = circleSnap.data()
        return {
          id: circleSnap.id,
          ...data,
          createdAt: data.createdAt?.toDate() || new Date(),
        } as Circle
      }

      return null
    } catch (error) {
      console.error('Error getting circle:', error)
      return null
    }
  }

  /**
   * Join a circle
   */
  async joinCircle(userId: string, circleId: string): Promise<void> {
    try {
      const circleRef = doc(db, 'circles', circleId)
      const circleSnap = await getDoc(circleRef)

      if (circleSnap.exists()) {
        const data = circleSnap.data()
        const memberIds = data.memberIds || []
        
        if (!memberIds.includes(userId)) {
          await updateDoc(circleRef, {
            memberIds: [...memberIds, userId],
            platform: 'web',
          })
        }
      }
    } catch (error) {
      console.error('Error joining circle:', error)
      throw error
    }
  }

  /**
   * Get user's circles
   */
  async getUserCircles(userId: string): Promise<Circle[]> {
    try {
      const circlesRef = collection(db, 'circles')
      const q = query(circlesRef, where('memberIds', 'array-contains', userId))
      const circlesSnap = await getDocs(q)

      return circlesSnap.docs.map((doc) => {
        const data = doc.data()
        return {
          id: doc.id,
          ...data,
          createdAt: data.createdAt?.toDate() || new Date(),
        } as Circle
      })
    } catch (error) {
      console.error('Error getting user circles:', error)
      return []
    }
  }
}

export default CirclesService
