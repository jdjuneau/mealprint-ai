/**
 * Web Health Tracking Service
 * Replaces Google Fit / Health Connect functionality for web platform
 */

export interface HealthDevice {
  id: string
  name: string
  type: 'fitness_tracker' | 'smartwatch' | 'heart_rate_monitor' | 'scale' | 'other'
  connected: boolean
  lastSync?: Date
}

export interface HealthData {
  steps?: number
  calories?: number
  heartRate?: number
  distance?: number // meters
  activeMinutes?: number
  sleepHours?: number
  weight?: number // kg
  timestamp: Date
  source: 'manual' | 'bluetooth' | 'fitbit' | 'strava' | 'garmin' | 'other'
}

export interface BluetoothDevice {
  id: string
  name: string
  device: BluetoothDevice
}

class HealthTrackingService {
  private static instance: HealthTrackingService
  private connectedDevices: Map<string, BluetoothDevice> = new Map()
  private bluetoothSupported: boolean = false

  private constructor() {
    this.checkBluetoothSupport()
  }

  static getInstance(): HealthTrackingService {
    if (!HealthTrackingService.instance) {
      HealthTrackingService.instance = new HealthTrackingService()
    }
    return HealthTrackingService.instance
  }

  /**
   * Check if Web Bluetooth API is supported
   */
  private checkBluetoothSupport(): void {
    this.bluetoothSupported = 'bluetooth' in navigator && 'requestDevice' in (navigator as any).bluetooth
  }

  /**
   * Check if Web Bluetooth is available
   */
  isBluetoothSupported(): boolean {
    return this.bluetoothSupported
  }

  /**
   * Request Bluetooth device (fitness tracker, heart rate monitor, etc.)
   */
  async requestBluetoothDevice(
    filters: BluetoothRequestDeviceFilter[] = []
  ): Promise<BluetoothDevice | null> {
    if (!this.bluetoothSupported) {
      throw new Error('Web Bluetooth API is not supported in this browser')
    }

    try {
      // Common fitness device service UUIDs
      const defaultFilters: BluetoothRequestDeviceFilter[] = filters.length > 0
        ? filters
        : [
            // Heart Rate Service
            { services: [0x180d] },
            // Fitness Machine Service
            { services: [0x1826] },
            // Cycling Power Service
            { services: [0x1818] },
            // Running Speed and Cadence Service
            { services: [0x1814] },
            // Device Information Service (for device name)
            { services: [0x180a] },
          ]

      const device = await (navigator as any).bluetooth.requestDevice({
        filters: defaultFilters,
        optionalServices: [
          0x180d, // Heart Rate
          0x1826, // Fitness Machine
          0x1818, // Cycling Power
          0x1814, // Running Speed and Cadence
          0x180a, // Device Information
        ],
      })

      if (device) {
        const deviceInfo: BluetoothDevice = {
          id: device.id,
          name: device.name || 'Unknown Device',
          device: device as any,
        }
        this.connectedDevices.set(device.id, deviceInfo)
        return deviceInfo
      }
      return null
    } catch (error: any) {
      if (error.name === 'NotFoundError') {
        console.log('No device selected')
      } else if (error.name === 'SecurityError') {
        console.error('Bluetooth permission denied')
      } else {
        console.error('Error requesting Bluetooth device:', error)
      }
      return null
    }
  }

  /**
   * Read heart rate from connected BLE device
   */
  async readHeartRate(deviceId: string): Promise<number | null> {
    const deviceInfo = this.connectedDevices.get(deviceId)
    if (!deviceInfo) {
      throw new Error('Device not connected')
    }

    try {
      const server = await (deviceInfo.device as any).gatt?.connect()
      if (!server) {
        throw new Error('Failed to connect to GATT server')
      }

      // Heart Rate Service UUID: 0x180d
      const service = await server.getPrimaryService(0x180d)
      // Heart Rate Measurement Characteristic UUID: 0x2a37
      const characteristic = await service.getCharacteristic(0x2a37)

      // Enable notifications
      await characteristic.startNotifications()

      return new Promise((resolve, reject) => {
        const timeout = setTimeout(() => {
          characteristic.stopNotifications()
          reject(new Error('Heart rate reading timeout'))
        }, 10000)

        characteristic.addEventListener('characteristicvaluechanged', (event: any) => {
          const value = event.target.value
          // Parse heart rate value (format depends on device)
          const heartRate = value.getUint8(1) // Usually at byte 1
          clearTimeout(timeout)
          characteristic.stopNotifications()
          resolve(heartRate)
        })
      })
    } catch (error) {
      console.error('Error reading heart rate:', error)
      return null
    }
  }

  /**
   * Read steps from connected BLE device (Running Speed and Cadence Service)
   */
  async readSteps(deviceId: string): Promise<number | null> {
    const deviceInfo = this.connectedDevices.get(deviceId)
    if (!deviceInfo) {
      throw new Error('Device not connected')
    }

    try {
      const server = await (deviceInfo.device as any).gatt?.connect()
      if (!server) {
        throw new Error('Failed to connect to GATT server')
      }

      // Running Speed and Cadence Service UUID: 0x1814
      const service = await server.getPrimaryService(0x1814)
      // RSC Measurement Characteristic UUID: 0x2a53
      const characteristic = await service.getCharacteristic(0x2a53)

      // Read current value
      const value = await characteristic.readValue()
      // Steps are typically in the first 2 bytes (16-bit value)
      const steps = value.getUint16(0, true) // Little-endian
      return steps
    } catch (error) {
      console.error('Error reading steps:', error)
      return null
    }
  }

  /**
   * Start auto-sync for a connected device
   */
  startAutoSync(
    deviceId: string,
    intervalMs: number = 60000, // Default: 1 minute
    onData: (data: HealthData) => void,
    onError?: (error: Error) => void
  ): () => void {
    let syncInterval: NodeJS.Timeout | null = null
    let isRunning = true

    const sync = async () => {
      if (!isRunning) return

      try {
        const steps = await this.readSteps(deviceId)
        const heartRate = await this.readHeartRate(deviceId)

        if (steps !== null || heartRate !== null) {
          const data: HealthData = {
            steps: steps || undefined,
            heartRate: heartRate || undefined,
            timestamp: new Date(),
            source: 'bluetooth',
          }
          onData(data)
        }
      } catch (error) {
        if (onError) {
          onError(error as Error)
        } else {
          console.error('Auto-sync error:', error)
        }
      }
    }

    // Initial sync
    sync()

    // Set up interval
    syncInterval = setInterval(sync, intervalMs)

    // Return stop function
    return () => {
      isRunning = false
      if (syncInterval) {
        clearInterval(syncInterval)
      }
    }
  }

  /**
   * Get connected devices
   */
  getConnectedDevices(): HealthDevice[] {
    return Array.from(this.connectedDevices.values()).map((device) => ({
      id: device.id,
      name: device.name,
      type: 'fitness_tracker' as const, // Could be enhanced to detect actual type
      connected: true,
      lastSync: new Date(),
    }))
  }

  /**
   * Disconnect a device
   */
  async disconnectDevice(deviceId: string): Promise<void> {
    const deviceInfo = this.connectedDevices.get(deviceId)
    if (deviceInfo) {
      try {
        const device = deviceInfo.device as any
        if (device.gatt?.connected) {
          device.gatt.disconnect()
        }
      } catch (error) {
        console.error('Error disconnecting device:', error)
      }
      this.connectedDevices.delete(deviceId)
    }
  }

  /**
   * Manual health data entry (fallback)
   */
  createManualHealthData(data: Partial<HealthData>): HealthData {
    return {
      ...data,
      timestamp: new Date(),
      source: 'manual',
    } as HealthData
  }

  /**
   * Check if browser supports geolocation for distance tracking
   */
  isGeolocationSupported(): boolean {
    return 'geolocation' in navigator
  }

  /**
   * Track distance using geolocation (for running/walking)
   */
  async trackDistanceWithGPS(
    onUpdate: (distance: number, speed: number) => void,
    onError: (error: Error) => void
  ): Promise<number> {
    if (!this.isGeolocationSupported()) {
      throw new Error('Geolocation is not supported')
    }

    return new Promise((resolve, reject) => {
      let startPosition: GeolocationPosition | null = null
      let totalDistance = 0
      let lastPosition: GeolocationPosition | null = null

      const watchId = navigator.geolocation.watchPosition(
        (position) => {
          if (!startPosition) {
            startPosition = position
            lastPosition = position
            return
          }

          if (lastPosition) {
            const distance = this.calculateDistance(
              lastPosition.coords.latitude,
              lastPosition.coords.longitude,
              position.coords.latitude,
              position.coords.longitude
            )
            totalDistance += distance

            const timeDiff = (position.timestamp - lastPosition.timestamp) / 1000 // seconds
            const speed = timeDiff > 0 ? distance / timeDiff : 0 // m/s

            onUpdate(totalDistance, speed)
          }

          lastPosition = position
        },
        (error) => {
          navigator.geolocation.clearWatch(watchId)
          const err = new Error(`Geolocation error: ${error.message}`)
          onError(err)
          reject(err)
        },
        {
          enableHighAccuracy: true,
          timeout: 5000,
          maximumAge: 0,
        }
      )

      // Return watch ID for manual cleanup
      resolve(watchId as any)
    })
  }

  /**
   * Calculate distance between two coordinates (Haversine formula)
   */
  private calculateDistance(
    lat1: number,
    lon1: number,
    lat2: number,
    lon2: number
  ): number {
    const R = 6371e3 // Earth radius in meters
    const φ1 = (lat1 * Math.PI) / 180
    const φ2 = (lat2 * Math.PI) / 180
    const Δφ = ((lat2 - lat1) * Math.PI) / 180
    const Δλ = ((lon2 - lon1) * Math.PI) / 180

    const a =
      Math.sin(Δφ / 2) * Math.sin(Δφ / 2) +
      Math.cos(φ1) * Math.cos(φ2) * Math.sin(Δλ / 2) * Math.sin(Δλ / 2)
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

    return R * c // Distance in meters
  }

  /**
   * Stop GPS tracking
   */
  stopGPSTracking(watchId: number): void {
    navigator.geolocation.clearWatch(watchId)
  }
}

// Type definitions for Web Bluetooth API
interface BluetoothRequestDeviceFilter {
  services?: number[]
  name?: string
  namePrefix?: string
}

export default HealthTrackingService
