/**
 * Strava API Integration Service
 * OAuth2 flow and data sync for Strava activities
 */

export interface StravaToken {
  access_token: string
  refresh_token: string
  expires_at: number
  athlete: {
    id: number
    username: string
  }
}

export interface StravaActivity {
  id: number
  name: string
  type: string
  distance: number // meters
  moving_time: number // seconds
  elapsed_time: number // seconds
  total_elevation_gain: number // meters
  calories?: number
  start_date: string
  average_speed: number // m/s
  max_speed: number // m/s
  average_heartrate?: number
  max_heartrate?: number
  average_cadence?: number
}

class StravaService {
  private static instance: StravaService
  private readonly CLIENT_ID: string
  private readonly REDIRECT_URI: string
  private readonly API_BASE = 'https://www.strava.com/api/v3'

  private constructor() {
    this.CLIENT_ID = process.env.NEXT_PUBLIC_STRAVA_CLIENT_ID || ''
    this.REDIRECT_URI = typeof window !== 'undefined'
      ? `${window.location.origin}/auth/strava/callback`
      : ''
  }

  static getInstance(): StravaService {
    if (!StravaService.instance) {
      StravaService.instance = new StravaService()
    }
    return StravaService.instance
  }

  /**
   * Initiate OAuth2 flow
   */
  initiateAuth(): void {
    if (!this.CLIENT_ID) {
      throw new Error('Strava Client ID not configured')
    }

    const scopes = ['activity:read', 'activity:read_all', 'profile:read_all'].join(',')

    const params = new URLSearchParams({
      client_id: this.CLIENT_ID,
      redirect_uri: this.REDIRECT_URI,
      response_type: 'code',
      approval_prompt: 'force',
      scope: scopes,
    })

    const authUrl = `https://www.strava.com/oauth/authorize?${params.toString()}`
    window.location.href = authUrl
  }

  /**
   * Exchange authorization code for access token
   */
  async exchangeCodeForToken(code: string): Promise<StravaToken> {
    const response = await fetch('/api/strava/token', {
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
  async refreshToken(refreshToken: string): Promise<StravaToken> {
    const response = await fetch('/api/strava/refresh', {
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
   * Get athlete activities
   */
  async getActivities(
    accessToken: string,
    before?: number,
    after?: number,
    perPage: number = 30
  ): Promise<StravaActivity[]> {
    const params = new URLSearchParams({
      per_page: perPage.toString(),
    })

    if (before) {
      params.append('before', before.toString())
    }
    if (after) {
      params.append('after', after.toString())
    }

    const response = await fetch(`${this.API_BASE}/athlete/activities?${params.toString()}`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    })

    if (!response.ok) {
      throw new Error('Failed to fetch Strava activities')
    }

    return response.json()
  }

  /**
   * Get activity details
   */
  async getActivity(accessToken: string, activityId: number): Promise<StravaActivity> {
    const response = await fetch(`${this.API_BASE}/activities/${activityId}`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    })

    if (!response.ok) {
      throw new Error('Failed to fetch Strava activity')
    }

    return response.json()
  }

  /**
   * Get activities for today
   */
  async getTodayActivities(accessToken: string): Promise<StravaActivity[]> {
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    const after = Math.floor(today.getTime() / 1000)

    return this.getActivities(accessToken, undefined, after)
  }
}

export default StravaService

