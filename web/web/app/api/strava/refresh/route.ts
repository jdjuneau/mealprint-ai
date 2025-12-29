import { NextRequest, NextResponse } from 'next/server'

export async function POST(request: NextRequest) {
  try {
    const { refresh_token } = await request.json()

    const clientId = process.env.NEXT_PUBLIC_STRAVA_CLIENT_ID
    const clientSecret = process.env.STRAVA_CLIENT_SECRET

    if (!clientId || !clientSecret) {
      return NextResponse.json(
        { error: 'Strava credentials not configured' },
        { status: 500 }
      )
    }

    // Refresh access token
    const tokenResponse = await fetch('https://www.strava.com/oauth/token', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        client_id: clientId,
        client_secret: clientSecret,
        grant_type: 'refresh_token',
        refresh_token,
      }),
    })

    if (!tokenResponse.ok) {
      const error = await tokenResponse.text()
      console.error('Strava token refresh error:', error)
      return NextResponse.json(
        { error: 'Failed to refresh token' },
        { status: tokenResponse.status }
      )
    }

    const tokenData = await tokenResponse.json()

    return NextResponse.json({
      access_token: tokenData.access_token,
      refresh_token: tokenData.refresh_token,
      expires_at: tokenData.expires_at,
      athlete: tokenData.athlete,
    })
  } catch (error: any) {
    console.error('Strava refresh route error:', error)
    return NextResponse.json(
      { error: error.message || 'Internal server error' },
      { status: 500 }
    )
  }
}

