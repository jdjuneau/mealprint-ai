/**
 * Service Worker Registration Service
 * Handles registration and management of the service worker
 */

class ServiceWorkerService {
  private registration: ServiceWorkerRegistration | null = null

  /**
   * Register the service worker
   */
  async register(): Promise<ServiceWorkerRegistration | null> {
    if (typeof window === 'undefined' || !('serviceWorker' in navigator)) {
      console.warn('Service Workers are not supported in this browser')
      return null
    }

    try {
      // Check if service worker file exists before registering
      const response = await fetch('/sw.js', { method: 'HEAD' })
      if (!response.ok) {
        // Service worker file doesn't exist, skip registration
        return null
      }

      const registration = await navigator.serviceWorker.register('/sw.js', {
        scope: '/',
      })

      this.registration = registration

      // Handle updates
      registration.addEventListener('updatefound', () => {
        const newWorker = registration.installing
        if (newWorker) {
          newWorker.addEventListener('statechange', () => {
            if (newWorker.state === 'installed' && navigator.serviceWorker.controller) {
              // New service worker available
              console.log('New service worker available')
              // You can show a notification to the user here
            }
          })
        }
      })

      // Check for updates periodically (only if registration succeeded)
      setInterval(() => {
        if (registration) {
          registration.update().catch(() => {
            // Silently handle update errors
          })
        }
      }, 60000) // Check every minute

      console.log('Service Worker registered successfully')
      return registration
    } catch (error: any) {
      // Silently handle registration errors - common in development
      if (error.name !== 'InvalidStateError') {
        console.debug('Service Worker registration skipped:', error.message)
      }
      return null
    }
  }

  /**
   * Unregister the service worker
   */
  async unregister(): Promise<boolean> {
    if (typeof window === 'undefined' || !('serviceWorker' in navigator)) {
      return false
    }

    try {
      const registration = await navigator.serviceWorker.ready
      const unregistered = await registration.unregister()
      if (unregistered) {
        this.registration = null
        console.log('Service Worker unregistered successfully')
      }
      return unregistered
    } catch (error) {
      console.error('Service Worker unregistration failed:', error)
      return false
    }
  }

  /**
   * Get the current registration
   */
  getRegistration(): ServiceWorkerRegistration | null {
    return this.registration
  }

  /**
   * Request push notification permission
   */
  async requestNotificationPermission(): Promise<NotificationPermission> {
    if (typeof window === 'undefined' || !('Notification' in window)) {
      return 'denied'
    }

    if (Notification.permission === 'granted') {
      return 'granted'
    }

    if (Notification.permission === 'denied') {
      return 'denied'
    }

    const permission = await Notification.requestPermission()
    return permission
  }

  /**
   * Subscribe to push notifications
   */
  async subscribeToPushNotifications(
    vapidPublicKey: string
  ): Promise<PushSubscription | null> {
    if (typeof window === 'undefined' || !('serviceWorker' in navigator)) {
      return null
    }

    try {
      const registration = await navigator.serviceWorker.ready
      const subscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: vapidPublicKey,
      })

      console.log('Push subscription successful:', subscription)
      return subscription
    } catch (error) {
      console.error('Push subscription failed:', error)
      return null
    }
  }

  /**
   * Get current push subscription
   */
  async getPushSubscription(): Promise<PushSubscription | null> {
    if (typeof window === 'undefined' || !('serviceWorker' in navigator)) {
      return null
    }

    try {
      const registration = await navigator.serviceWorker.ready
      const subscription = await registration.pushManager.getSubscription()
      return subscription
    } catch (error) {
      console.error('Error getting push subscription:', error)
      return null
    }
  }

  /**
   * Unsubscribe from push notifications
   */
  async unsubscribeFromPushNotifications(): Promise<boolean> {
    try {
      const subscription = await this.getPushSubscription()
      if (subscription) {
        const unsubscribed = await subscription.unsubscribe()
        console.log('Push unsubscription successful:', unsubscribed)
        return unsubscribed
      }
      return false
    } catch (error) {
      console.error('Push unsubscription failed:', error)
      return false
    }
  }

  /**
   * Send a message to the service worker
   */
  async sendMessage(message: any): Promise<void> {
    if (typeof window === 'undefined' || !('serviceWorker' in navigator)) {
      return
    }

    try {
      const registration = await navigator.serviceWorker.ready
      if (registration.active) {
        registration.active.postMessage(message)
      }
    } catch (error) {
      console.error('Error sending message to service worker:', error)
    }
  }
}

// Singleton instance
export const serviceWorkerService = new ServiceWorkerService()

