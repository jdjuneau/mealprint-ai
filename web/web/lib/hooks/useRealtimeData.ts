/**
 * React hooks for real-time Firestore data
 * Replaces Android ViewModels with React hooks
 */

import { useState, useEffect } from 'react'
import { onSnapshot, doc, collection, query, where, orderBy } from 'firebase/firestore'
import { db } from '../firebase'
import type { DailyLog, HealthLog } from '../../types'

/**
 * Hook for real-time daily log updates
 */
export function useDailyLog(userId: string | null, date: string) {
  const [log, setLog] = useState<DailyLog | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!userId) {
      setLog(null)
      setLoading(false)
      return
    }

    // Android path: logs/{userId}/daily/{date}
    const logRef = doc(db, 'logs', userId, 'daily', date)

    const unsubscribe = onSnapshot(
      logRef,
      (snapshot) => {
        if (snapshot.exists()) {
          const data = snapshot.data()
          setLog({
            id: snapshot.id,
            userId,
            date,
            ...data,
            createdAt: data.createdAt?.toDate() || new Date(),
            updatedAt: data.updatedAt?.toDate() || new Date(),
            logs: (data.entries || []).map((entry: any) => ({
              ...entry,
              timestamp: entry.timestamp?.toDate() || new Date(),
            })),
          } as DailyLog)
        } else {
          setLog(null)
        }
        setLoading(false)
      },
      (error) => {
        console.error('Error listening to daily log:', error)
        setLoading(false)
      }
    )

    return () => unsubscribe()
  }, [userId, date])

  return { log, loading }
}

/**
 * Hook for real-time health logs
 */
export function useHealthLogs(userId: string | null, date: string) {
  const [logs, setLogs] = useState<HealthLog[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!userId) {
      setLogs([])
      setLoading(false)
      return
    }

    // Android path: logs/{userId}/daily/{date}/entries
    const entriesRef = collection(db, 'logs', userId, 'daily', date, 'entries')
    const entriesQuery = query(entriesRef, orderBy('timestamp', 'desc'))

    const unsubscribe = onSnapshot(
      entriesQuery,
      (snapshot) => {
        const healthLogs = snapshot.docs.map((doc) => {
          const data = doc.data()
          return {
            id: doc.id,
            userId,
            type: data.type,
            timestamp: data.timestamp?.toDate() || new Date(),
            ...data,
          } as HealthLog
        })
        setLogs(healthLogs)
        setLoading(false)
      },
      (error) => {
        console.error('Error listening to health logs:', error)
        setLoading(false)
      }
    )

    return () => unsubscribe()
  }, [userId, date])

  return { logs, loading }
}

/**
 * Hook for real-time user profile
 */
export function useUserProfile(userId: string | null) {
  const [profile, setProfile] = useState<any>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!userId) {
      setProfile(null)
      setLoading(false)
      return
    }

    const profileRef = doc(db, 'users', userId)

    const unsubscribe = onSnapshot(
      profileRef,
      (snapshot) => {
        if (snapshot.exists()) {
          setProfile({
            id: snapshot.id,
            ...snapshot.data(),
          })
        } else {
          setProfile(null)
        }
        setLoading(false)
      },
      (error) => {
        console.error('Error listening to user profile:', error)
        setLoading(false)
      }
    )

    return () => unsubscribe()
  }, [userId])

  return { profile, loading }
}
