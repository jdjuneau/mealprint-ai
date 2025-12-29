# Wearable Tracking Setup Checklist

## ✅ Step 1: Environment Variables

Create `.env.local` in the `web` directory with:

```bash
# Fitbit API (get from https://dev.fitbit.com/apps)
NEXT_PUBLIC_FITBIT_CLIENT_ID=your_client_id_here
FITBIT_CLIENT_SECRET=your_client_secret_here

# Strava API (get from https://www.strava.com/settings/api)
NEXT_PUBLIC_STRAVA_CLIENT_ID=your_client_id_here
STRAVA_CLIENT_SECRET=your_client_secret_here
```

## ✅ Step 2: Register Fitbit App

1. Go to https://dev.fitbit.com/apps
2. Click "Register a New App"
3. Fill in:
   - **Application Name**: Coachie
   - **Description**: Health and wellness tracking app
   - **Application Website**: Your website URL
   - **OAuth 2.0 Application Type**: Personal
   - **Callback URL**: 
     - Production: `https://yourdomain.com/auth/fitbit/callback`
     - Development: `http://localhost:3000/auth/fitbit/callback`
   - **Default Access Type**: Read Only
   - **Scopes**: Select all of:
     - `activity` - Read activity data
     - `heartrate` - Read heart rate data
     - `sleep` - Read sleep data
     - `profile` - Read basic profile info
4. Copy **Client ID** and **Client Secret** to `.env.local`

## ✅ Step 3: Register Strava App

1. Go to https://www.strava.com/settings/api
2. Click "Create App"
3. Fill in:
   - **Application Name**: Coachie
   - **Category**: Fitness
   - **Website**: Your website URL
   - **Authorization Callback Domain**: 
     - Production: `yourdomain.com`
     - Development: `localhost`
   - **Scopes**: Select:
     - `activity:read` - Read activities
     - `activity:read_all` - Read all activities (including private)
4. Copy **Client ID** and **Client Secret** to `.env.local`

## ✅ Step 4: Test the Integration

1. Start the dev server: `npm run dev`
2. Go to `/health-tracking` page
3. Test Fitbit:
   - Click "Connect" next to Fitbit
   - Complete OAuth flow
   - Should redirect back and show "Connected"
   - Click "Sync Now" to test manual sync
4. Test Strava:
   - Click "Connect" next to Strava
   - Complete OAuth flow
   - Should redirect back and show "Connected"
   - Click "Sync Now" to test manual sync
5. Test Bluetooth:
   - Click "Connect Bluetooth Device"
   - Select a BLE fitness device
   - Data should sync automatically

## ✅ Step 5: Verify Data Sync

1. Check Firebase Console:
   - Go to `users/{userId}/health_services/` - Should see connected services
   - Go to `users/{userId}/daily/{date}` - Should see synced data
2. Check Health Tracking page:
   - Sync status should show "connected"
   - Last sync time should update
3. Check dashboard:
   - Steps, calories, distance should appear
   - Data should match your wearable device

## Troubleshooting

### OAuth not working
- ✅ Check redirect URIs match exactly (including http/https, trailing slashes)
- ✅ Verify environment variables are set
- ✅ Check browser console for errors
- ✅ Verify API credentials are correct

### Data not syncing
- ✅ Check sync status on Health Tracking page
- ✅ Click "Sync Now" to manually trigger
- ✅ Check Firebase permissions for `health_services` collection
- ✅ Verify tokens haven't expired (auto-refreshes)

### Bluetooth not working
- ✅ Only works in Chrome/Edge (desktop or Android)
- ✅ Device must support BLE (Bluetooth Low Energy)
- ✅ Device must be in pairing mode
- ✅ Check browser permissions for Bluetooth

## Next Steps After Setup

1. ✅ Test with real devices
2. ✅ Monitor sync logs in browser console
3. ✅ Check Firebase for synced data
4. ✅ Verify data appears in dashboard
5. ✅ Set up production environment variables

