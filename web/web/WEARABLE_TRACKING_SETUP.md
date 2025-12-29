# Wearable Tracking Setup Guide

## Overview

The web version now supports multiple wearable tracking options similar to Google Fit/Health Connect on Android:

1. **Google Fit (via Android app)** - Automatically syncs when using the Android app
2. **Web Bluetooth** - Direct connection to BLE fitness devices
3. **Fitbit API** - OAuth2 integration
4. **Strava API** - OAuth2 integration
5. **Garmin** - Structure ready (waiting for official API)

## Setup Instructions

### 1. Environment Variables

Add these to your `.env.local` file:

```bash
# Fitbit API
NEXT_PUBLIC_FITBIT_CLIENT_ID=your_fitbit_client_id
FITBIT_CLIENT_SECRET=your_fitbit_client_secret

# Strava API
NEXT_PUBLIC_STRAVA_CLIENT_ID=your_strava_client_id
STRAVA_CLIENT_SECRET=your_strava_client_secret
```

### 2. Fitbit API Setup

1. Go to https://dev.fitbit.com/apps
2. Click "Register a New App"
3. Fill in:
   - Application Name: Coachie
   - Description: Health and wellness tracking
   - Application Website: Your website URL
   - OAuth 2.0 Application Type: Personal
   - Callback URL: `https://yourdomain.com/auth/fitbit/callback`
   - Default Access Type: Read Only
   - Scopes: Select `activity`, `heartrate`, `sleep`, `profile`
4. Copy the Client ID and Client Secret to your `.env.local`

### 3. Strava API Setup

1. Go to https://www.strava.com/settings/api
2. Click "Create App"
3. Fill in:
   - Application Name: Coachie
   - Category: Fitness
   - Website: Your website URL
   - Authorization Callback Domain: `yourdomain.com`
   - Scopes: Select `activity:read`, `activity:read_all`
4. Copy the Client ID and Client Secret to your `.env.local`

### 4. OAuth Redirect URIs

Make sure your OAuth apps are configured with these redirect URIs:
- Fitbit: `https://yourdomain.com/auth/fitbit/callback`
- Strava: `https://yourdomain.com/auth/strava/callback`

For local development:
- Fitbit: `http://localhost:3000/auth/fitbit/callback`
- Strava: `http://localhost:3000/auth/strava/callback`

## How It Works

### Android Users (Google Fit)

1. Install Coachie Android app
2. Connect Google Fit in the app
3. Data from Samsung watches, Fitbit, Garmin (if synced to Google Fit) automatically appears in the web app
4. No additional setup needed!

### Web Users

#### Option 1: Web Bluetooth
1. Go to Health Tracking page
2. Click "Connect Bluetooth Device"
3. Select your fitness tracker/watch
4. Data syncs automatically every minute

#### Option 2: Fitbit
1. Go to Health Tracking page
2. Click "Connect" next to Fitbit
3. Authorize in Fitbit's OAuth flow
4. Data syncs automatically every hour

#### Option 3: Strava
1. Go to Health Tracking page
2. Click "Connect" next to Strava
3. Authorize in Strava's OAuth flow
4. Workouts sync automatically every hour

## Data Sync

- **Bluetooth devices**: Real-time sync every 1 minute
- **Fitbit/Strava**: Background sync every 60 minutes
- **Conflict resolution**: Device data takes priority over manual entries
- **Steps**: Uses highest value (most accurate)
- **Calories/Distance**: Sums from all sources

## Troubleshooting

### Fitbit/Strava not connecting
- Check environment variables are set correctly
- Verify redirect URIs match exactly
- Check browser console for errors

### Bluetooth not working
- Only works in Chrome/Edge (desktop or Android)
- Make sure device is in pairing mode
- Check device supports BLE (Bluetooth Low Energy)

### Data not syncing
- Check sync status on Health Tracking page
- Verify tokens haven't expired (auto-refreshes)
- Check Firebase permissions for health_services collection

## API Routes Created

- `/api/fitbit/token` - Exchange OAuth code for token
- `/api/fitbit/refresh` - Refresh expired token
- `/api/strava/token` - Exchange OAuth code for token
- `/api/strava/refresh` - Refresh expired token

## Callback Pages Created

- `/auth/fitbit/callback` - Handles Fitbit OAuth callback
- `/auth/strava/callback` - Handles Strava OAuth callback

## Services Created

- `healthTracking.ts` - Web Bluetooth and GPS tracking
- `fitbitService.ts` - Fitbit API integration
- `stravaService.ts` - Strava API integration
- `garminService.ts` - Garmin structure (ready for API)
- `healthSyncService.ts` - Background sync and conflict resolution

