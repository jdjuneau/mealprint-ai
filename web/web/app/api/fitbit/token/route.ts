import { NextRequest, NextResponse } from 'next/server'

export async function POST(request: NextRequest) {
  try {
    const { code, redirect_uri } = await request.json()

    const clientId = process.env.NEXT_PUBLIC_FITBIT_CLIENT_ID
    const clientSecret = process.env.FITBIT_CLIENT_SECRET

    if (!clientId || !clientSecret) {
      return NextResponse.json(
        { error: 'Fitbit credentials not configured' },
        { status: 500 }
      )
    }

    // Exchange authorization code for access token
    const tokenResponse = await fetch('https://api.fitbit.com/oauth2/token', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${Buffer.from(`${clientId}:${clientSecret}`).toString('base64')}`,
      },
      body: new URLSearchParams({
        client_id: clientId,
        grant_type: 'authorization_code',
        code,
        redirect_uri,
      }),
    })

    if (!tokenResponse.ok) {
      const error = await tokenResponse.text()
      console.error('Fitbit token exchange error:', error)
      return NextResponse.json(
        { error: 'Failed to exchange code for token' },
        { status: tokenResponse.status }
      )
    }

    const tokenData = await tokenResponse.json()

    return NextResponse.json({
      access_token: tokenData.access_token,
      refresh_token: tokenData.refresh_token,
      expires_in: tokenData.expires_in,
      token_type: tokenData.token_type,
      scope: tokenData.scope,
      user_id: tokenData.user_id,
    })
  } catch (error: any) {
    console.error('Fitbit token route error:', error)
    return NextResponse.json(
      { error: error.message || 'Internal server error' },
      { status: 500 }
    )
  }
}

