'use client'

import { useEffect } from 'react'
import { serviceWorkerService } from '../lib/services/serviceWorkerService'

export default function ServiceWorkerRegistration() {
  useEffect(() => {
    // Register service worker on mount with error handling
    if (typeof window !== 'undefined' && 'serviceWorker' in navigator) {
      serviceWorkerService.register().catch((error) => {
        // Silently handle service worker registration errors
        // These are common in development when sw.js doesn't exist
        console.debug('Service Worker registration skipped:', error.message)
      })
    }
  }, [])

  return null // This component doesn't render anything
}

