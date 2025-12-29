/**
 * Fitbit API Integration Service
 * OAuth2 flow and data sync for Fitbit devices
 */

export interface FitbitToken {
  access_token: string
  refresh_token: string
  expires_in: number
  token_type: string
  scope: string
  user_id: string
}

export interface FitbitActivity {
  steps: number
  calories: number
  distance: number // meters
  activeMinutes: number
  date: string // YYYY-MM-DD
}

export interface FitbitHeartRate {
  restingHeartRate?: number
  heartRateZones?: Array<{
    name: string
    min: number
    max: number
    minutes: number
  }>
  date: string
}

export interface FitbitSleep {
  duration: number // milliseconds
  efficiency: number
  minutesAsleep: number
  minutesAwake: number
  date: string
}

class FitbitService {
  private static instance: FitbitService
  private readonly CLIENT_ID: string
  private readonly REDIRECT_URI: string
  private readonly API_BASE = 'https://api.fitbit.com/1'

  private constructor() {
    // Get from environment variables
    this.CLIENT_ID = process.env.NEXT_PUBLIC_FITBIT_CLIENT_ID || ''
    this.REDIRECT_URI = typeof window !== 'undefined' 
      ? `${window.location.origin}/auth/fitbit/callback`
      : ''
  }

  static getInstance(): FitbitService {
    if (!FitbitService.instance) {
      FitbitService.instance = new FitbitService()
    }
    return FitbitService.instance
  }

  /**
   * Initiate OAuth2 flow
   */
  initiateAuth(): void {
    if (!this.CLIENT_ID) {
      throw new Error('Fitbit Client ID not configured')
    }

    const scopes = [
      'activity',
      'heartrate',
      'sleep',
      'profile',
    ].join(' ')

    const params = new URLSearchParams({
      response_type: 'code',
      client_id: this.CLIENT_ID,
      redirect_uri: this.REDIRECT_URI,
      scope: scopes,
      expires_in: '2592000', // 30 days
    })

    const authUrl = `https://www.fitbit.com/oauth2/authorize?${params.toString()}`
    window.location.href = authUrl
  }

  /**
   * Exchange authorization code for access token
   */
  async exchangeCodeForToken(code: string): Promise<FitbitToken> {
    const response = await fetch('/api/fitbit/token', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        code,
        redirect_uri: this.REDIRECT_URI,
      }),
    })

    if (!response.ok) {
      const error = await response.json()
      throw new Error(error.error || 'Failed to exchange code for token')
    }

    return response.json()
  }

  /**
   * Refresh access token
   */
  async refreshToken(refreshToken: string): Promise<FitbitToken> {
    const response = await fetch('/api/fitbit/refresh', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ refresh_token: refreshToken }),
    })

    if (!response.ok) {
      throw new Error('Failed to refresh token')
    }

    return response.json()
  }

  /**
   * Get today's activity data
   */
  async getTodayActivity(accessToken: string): Promise<FitbitActivity> {
    const today = new Date().toISOString().split('T')[0]
    const response = await fetch(`${this.API_BASE}/user/-/activities/date/${today}.json`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    })

    if (!response.ok) {
      throw new Error('Failed to fetch Fitbit activity data')
    }

    const data = await response.json()
    return {
      steps: data.summary.steps || 0,
      calories: data.summary.caloriesOut || 0,
      distance: (data.summary.distances?.[0]?.distance || 0) * 1000, // Convert km to meters
      activeMinutes: data.summary.fairlyActiveMinutes + data.summary.veryActiveMinutes || 0,
      date: today,
    }
  }

  /**
   * Get heart rate data for a date
   */
  async getHeartRate(accessToken: string, date: string): Promise<FitbitHeartRate> {
    const response = await fetch(`${this.API_BASE}/user/-/activities/heart/date/${date}/1d.json`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    })

    if (!response.ok) {
      throw new Error('Failed to fetch Fitbit heart rate data')
    }

    const data = await response.json()
    const dayData = data['activities-heart']?.[0]

    return {
      restingHeartRate: dayData?.value?.restingHeartRate,
      heartRateZones: dayData?.value?.heartRateZones,
      date,
    }
  }

  /**
   * Get sleep data for a date
   */
  async getSleep(accessToken: string, date: string): Promise<FitbitSleep | null> {
    const response = await fetch(`${this.API_BASE}/user/-/sleep/date/${date}.json`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    })

    if (!response.ok) {
      throw new Error('Failed to fetch Fitbit sleep data')
    }

    const data = await response.json()
    const sleep = data.sleep?.[0]

    if (!sleep) {
      return null
    }

    return {
      duration: sleep.duration,
      efficiency: sleep.efficiency,
      minutesAsleep: sleep.minutesAsleep,
      minutesAwake: sleep.minutesAwake,
      date,
    }
  }

  /**
   * Get activity data for a date range
   */
  async getActivityRange(
    accessToken: string,
    startDate: string,
    endDate: string
  ): Promise<FitbitActivity[]> {
    const response = await fetch(
      `${this.API_BASE}/user/-/activities/steps/date/${startDate}/${endDate}.json`,
      {
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      }
    )

    if (!response.ok) {
      throw new Error('Failed to fetch Fitbit activity range')
    }

    const data = await response.json()
    return data['activities-steps'].map((item: any) => ({
      steps: parseInt(item.value) || 0,
      date: item.dateTime,
      calories: 0, // Would need separate API call
      distance: 0,
      activeMinutes: 0,
    }))
  }
}

export default FitbitService

