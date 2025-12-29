/**
 * Auto-detect the best Cloud Functions region based on user's location
 * For global deployment, we deploy to multiple regions and route to the nearest one
 */

/**
 * Get the best region for Cloud Functions based on user's timezone/location
 * Returns one of: 'us-central1', 'europe-west1', 'asia-southeast1'
 */
export function getBestRegion(): string {
  try {
    // Get user's timezone
    const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
    
    // Map timezones to regions
    if (timezone.includes('America') || timezone.includes('US') || timezone.includes('Canada')) {
      return 'us-central1'; // Americas
    }
    
    if (timezone.includes('Europe') || timezone.includes('Africa') || timezone.includes('Asia/Dubai')) {
      return 'europe-west1'; // Europe, Middle East, Africa
    }
    
    if (timezone.includes('Asia') || timezone.includes('Australia') || timezone.includes('Pacific')) {
      return 'asia-southeast1'; // Asia-Pacific
    }
    
    // Default to US for unknown timezones
    return 'us-central1';
  } catch (error) {
    console.warn('Failed to detect timezone, defaulting to us-central1:', error);
    return 'us-central1';
  }
}

/**
 * Get Firebase Functions instance for the best region
 */
export function getFunctionsForRegion(app: any, region?: string) {
  const { getFunctions } = require('firebase/functions');
  const targetRegion = region || getBestRegion();
  return getFunctions(app, targetRegion);
}

