/**
 * Cross-Platform Utilities
 * Ensures data compatibility across Android, Web, and iOS
 */

export type Platform = 'android' | 'web' | 'ios'

/**
 * Get current platform
 */
export function getCurrentPlatform(): Platform {
  if (typeof window === 'undefined') {
    return 'web' // Server-side rendering
  }
  
  // Detect platform from user agent or environment
  const userAgent = window.navigator.userAgent.toLowerCase()
  
  if (/android/.test(userAgent)) {
    return 'android'
  } else if (/iphone|ipad|ipod|iphone/.test(userAgent)) {
    return 'ios'
  }
  
  return 'web'
}

/**
 * Add platform to platforms array (preserves existing platforms)
 */
export function addPlatformToArray(
  existingPlatforms: string[] | undefined,
  newPlatform: Platform
): string[] {
  const platforms = existingPlatforms || []
  if (!platforms.includes(newPlatform)) {
    return [...platforms, newPlatform].sort() // Sort for consistency
  }
  return platforms
}

/**
 * Ensure data structure is cross-platform compatible
 */
export function ensureCrossPlatformCompatibility<T extends { platform?: string; platforms?: string[] }>(
  data: T,
  currentPlatform: Platform
): T {
  return {
    ...data,
    platform: currentPlatform,
    platforms: addPlatformToArray(data.platforms, currentPlatform),
  }
}

/**
 * Check if user has used a specific platform
 */
export function hasUsedPlatform(platforms: string[] | undefined, platform: Platform): boolean {
  return platforms?.includes(platform) || false
}

/**
 * Get all platforms user has used
 */
export function getUserPlatforms(platforms: string[] | undefined): Platform[] {
  return (platforms || []) as Platform[]
}

/**
 * Format platform for display
 */
export function formatPlatform(platform: Platform): string {
  switch (platform) {
    case 'android':
      return 'Android'
    case 'web':
      return 'Web'
    case 'ios':
      return 'iOS'
    default:
      return platform
  }
}
