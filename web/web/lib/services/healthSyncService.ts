/**
 * Health Data Sync Service
 * Handles background syncing, conflict resolution, and data persistence
 */

import { db } from '../firebase'
import { collection, doc, setDoc, getDoc, serverTimestamp } from 'firebase/firestore'
import HealthTrackingService, { HealthData } from './healthTracking'
import FitbitService from './fitbitService'
import StravaService from './stravaService'
import GarminService from './garminService'

export interface SyncStatus {
  service: 'bluetooth' | 'fitbit' | 'strava' | 'garmin' | 'google_fit'
  lastSync?: Date
  status: 'connected' | 'syncing' | 'error' | 'disconnected'
  error?: string
}

export interface ConnectedService {
  type: 'fitbit' | 'strava' | 'garmin'
  token: string
  refreshToken: string
  expiresAt: number
  userId: string
}

class HealthSyncService {
  private static instance: HealthSyncService
  private syncIntervals: Map<string, NodeJS.Timeout> = new Map()
  private syncStatuses: Map<string, SyncStatus> = new Map()

  private constructor() {}

  static getInstance(): HealthSyncService {
    if (!HealthSyncService.instance) {
      HealthSyncService.instance = new HealthSyncService()
    }
    return HealthSyncService.instance
  }

  /**
   * Save health data to Firebase with conflict resolution
   */
  async saveHealthData(
    userId: string,
    date: string,
    data: HealthData,
    source: 'manual' | 'bluetooth' | 'fitbit' | 'strava' | 'garmin'
  ): Promise<void> {
    try {
      const dailyLogRef = doc(db, 'users', userId, 'daily', date)
      const existingDoc = await getDoc(dailyLogRef)
      const existing = existingDoc.data()

      // Conflict resolution: prefer device data over manual, newer over older
      const updates: any = {
        uid: userId,
        date: date,
        updatedAt: serverTimestamp(),
      }

      // Steps: Use highest value (device might have more accurate count)
      if (data.steps !== undefined) {
        const existingSteps = existing?.steps || 0
        updates.steps = Math.max(existingSteps, data.steps)
        if (data.steps > existingSteps) {
          updates.stepsSource = source
        }
      }

      // Heart rate: Use latest reading
      if (data.heartRate !== undefined) {
        updates.heartRate = data.heartRate
        updates.heartRateSource = source
        updates.heartRateTimestamp = serverTimestamp()
      }

      // Calories: Sum from all sources
      if (data.calories !== undefined) {
        const existingCalories = existing?.caloriesBurned || 0
        // Only add if from device (avoid double counting manual entries)
        if (source !== 'manual') {
          updates.caloriesBurned = existingCalories + data.calories
        }
      }

      // Distance: Sum from all sources
      if (data.distance !== undefined) {
        const existingDistance = existing?.distance || 0
        if (source !== 'manual') {
          updates.distance = existingDistance + data.distance
        }
      }

      await setDoc(dailyLogRef, updates, { merge: true })
    } catch (error) {
      console.error('Error saving health data:', error)
      throw error
    }
  }

  /**
   * Start background sync for a service
   */
  async startBackgroundSync(
    userId: string,
    service: 'fitbit' | 'strava' | 'garmin',
    intervalMinutes: number = 60
  ): Promise<void> {
    const serviceKey = `${userId}_${service}`

    // Stop existing sync if any
    this.stopBackgroundSync(userId, service)

    // Update status
    this.syncStatuses.set(serviceKey, {
      service,
      status: 'syncing',
      lastSync: new Date(),
    })

    // Perform initial sync
    await this.syncService(userId, service)

    // Set up interval
    const interval = setInterval(async () => {
      try {
        await this.syncService(userId, service)
        this.syncStatuses.set(serviceKey, {
          service,
          status: 'connected',
          lastSync: new Date(),
        })
      } catch (error) {
        this.syncStatuses.set(serviceKey, {
          service,
          status: 'error',
          lastSync: new Date(),
          error: (error as Error).message,
        })
      }
    }, intervalMinutes * 60 * 1000)

    this.syncIntervals.set(serviceKey, interval)
  }

  /**
   * Stop background sync for a service
   */
  stopBackgroundSync(userId: string, service: 'fitbit' | 'strava' | 'garmin'): void {
    const serviceKey = `${userId}_${service}`
    const interval = this.syncIntervals.get(serviceKey)
    if (interval) {
      clearInterval(interval)
      this.syncIntervals.delete(serviceKey)
    }

    this.syncStatuses.set(serviceKey, {
      service,
      status: 'disconnected',
    })
  }

  /**
   * Sync data from a specific service
   */
  private async syncService(userId: string, service: 'fitbit' | 'strava' | 'garmin'): Promise<void> {
    // Get stored tokens
    const serviceRef = doc(db, 'users', userId, 'health_services', service)
    const serviceDoc = await getDoc(serviceRef)
    
    if (!serviceDoc.exists()) {
      throw new Error(`${service} not connected`)
    }

    const serviceData = serviceDoc.data() as ConnectedService
    const today = new Date().toISOString().split('T')[0]

    switch (service) {
      case 'fitbit': {
        const fitbitService = FitbitService.getInstance()
        // Check if token needs refresh (expiresAt is already in milliseconds)
        if (Date.now() >= serviceData.expiresAt) {
          const newToken = await fitbitService.refreshToken(serviceData.refreshToken)
          await setDoc(serviceRef, {
            ...serviceData,
            token: newToken.access_token,
            refreshToken: newToken.refresh_token,
            expiresAt: Date.now() + newToken.expires_in * 1000,
            lastSync: new Date(),
          })
          serviceData.token = newToken.access_token
        }

        const activity = await fitbitService.getTodayActivity(serviceData.token)
        await this.saveHealthData(userId, today, {
          steps: activity.steps,
          calories: activity.calories,
          distance: activity.distance,
          activeMinutes: activity.activeMinutes,
          timestamp: new Date(),
          source: 'fitbit',
        }, 'fitbit')

        const sleep = await fitbitService.getSleep(serviceData.token, today)
        if (sleep) {
          // Save sleep data separately
          const sleepRef = doc(collection(db, 'users', userId, 'daily', today, 'entries'))
          await setDoc(sleepRef, {
            type: 'sleep',
            durationHours: sleep.minutesAsleep / 60,
            timestamp: new Date(sleep.date),
            source: 'fitbit',
          })
        }

        // Update last sync time
        await setDoc(serviceRef, { lastSync: new Date() }, { merge: true })
        break
      }

      case 'strava': {
        const stravaService = StravaService.getInstance()
        // Check if token needs refresh (expiresAt is already in milliseconds)
        if (Date.now() >= serviceData.expiresAt) {
          const newToken = await stravaService.refreshToken(serviceData.refreshToken)
          await setDoc(serviceRef, {
            ...serviceData,
            token: newToken.access_token,
            refreshToken: newToken.refresh_token,
            expiresAt: newToken.expires_at * 1000,
            lastSync: new Date(),
          })
          serviceData.token = newToken.access_token
        }

        const activities = await stravaService.getTodayActivities(serviceData.token)
        for (const activity of activities) {
          await this.saveHealthData(userId, today, {
            calories: activity.calories,
            distance: activity.distance,
            activeMinutes: Math.floor(activity.moving_time / 60),
            heartRate: activity.average_heartrate,
            timestamp: new Date(activity.start_date),
            source: 'strava',
          }, 'strava')
        }

        // Update last sync time
        await setDoc(serviceRef, { lastSync: new Date() }, { merge: true })
        break
      }

      case 'garmin':
        // Placeholder for Garmin sync
        throw new Error('Garmin sync not yet implemented')
    }
  }

  /**
   * Get sync status for all services
   */
  getSyncStatuses(userId: string): SyncStatus[] {
    const statuses: SyncStatus[] = []
    this.syncStatuses.forEach((status, key) => {
      if (key.startsWith(`${userId}_`)) {
        statuses.push(status)
      }
    })
    return statuses
  }

  /**
   * Manually trigger sync for a service
   */
  async manualSync(userId: string, service: 'fitbit' | 'strava' | 'garmin'): Promise<void> {
    const serviceKey = `${userId}_${service}`
    
    try {
      this.syncStatuses.set(serviceKey, {
        service,
        status: 'syncing',
        lastSync: new Date(),
      })

      await this.syncService(userId, service)

      this.syncStatuses.set(serviceKey, {
        service,
        status: 'connected',
        lastSync: new Date(),
      })
    } catch (error: any) {
      this.syncStatuses.set(serviceKey, {
        service,
        status: 'error',
        lastSync: new Date(),
        error: error.message,
      })
      throw error
    }
  }

  /**
   * Sync all connected services
   */
  async syncAllServices(userId: string): Promise<void> {
    const services: ('fitbit' | 'strava' | 'garmin')[] = ['fitbit', 'strava', 'garmin']
    const results = await Promise.allSettled(
      services.map(service => this.manualSync(userId, service))
    )
    
    const errors = results.filter(r => r.status === 'rejected')
    if (errors.length > 0) {
      console.warn('Some services failed to sync:', errors)
    }
  }
}

export default HealthSyncService

