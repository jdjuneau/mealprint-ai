# Cross-Platform Compatibility Guide

## Overview
This document ensures data compatibility across **Android**, **Web**, and **iOS** platforms.

## Platform Tracking

### UserProfile
- **`platform`**: Current platform (`"android"`, `"web"`, or `"ios"`)
- **`platforms`**: Array of all platforms user has used (e.g., `["android", "web"]`)
- **Purpose**: Track which platforms a user has accessed the app from

### Health Logs
- **`platform`**: Platform where log was created (`"android"`, `"web"`, or `"ios"`)
- **No `platforms` array**: Only tracks creation platform, not all platforms

### Subscription
- **`paymentProvider`**: Payment method used (`"stripe"`, `"paypal"`, `"google_play"`, `"app_store"`)
- **`platforms`**: All platforms subscription is active on
- **Cross-platform**: Subscription purchased on one platform works on all platforms

## Data Structure Compatibility

### Firestore Paths (Same Across All Platforms)

#### User Profile
```
users/{userId}
  - platform: "android" | "web" | "ios"
  - platforms: ["android", "web", "ios"] (sorted array)
```

#### Health Logs
```
logs/{userId}/daily/{date}/entries/{entryId}
  - platform: "android" | "web" | "ios"
```

#### Streaks
```
users/{userId}/streaks/current
  - platform: "android" | "web" | "ios"
```

#### Habits
```
users/{userId}/habits/{habitId}
  - platform: "android" | "web" | "ios"
```

### Platform-Specific Considerations

#### Android
- Uses Google Play Billing (`paymentProvider: "google_play"`)
- Platform: `"android"`
- Health data: Google Fit / Health Connect

#### Web
- Uses Stripe or PayPal (`paymentProvider: "stripe" | "paypal"`)
- Platform: `"web"`
- Health data: Web Bluetooth, third-party APIs (Fitbit, Strava, Garmin)

#### iOS (Future)
- Uses App Store Billing (`paymentProvider: "app_store"`)
- Platform: `"ios"`
- Health data: HealthKit

## Payment Processing

### Web (Stripe & PayPal)
- **Stripe**: Credit/debit cards, Apple Pay, Google Pay
- **PayPal**: PayPal account, credit cards via PayPal
- **Cloud Functions**: Handle payment processing server-side

### Android (Google Play)
- **Google Play Billing**: In-app purchases
- **Platform**: `"google_play"`

### iOS (App Store - Future)
- **App Store Billing**: In-app purchases
- **Platform**: `"app_store"`

## Cross-Platform Data Rules

1. **UserProfile.platforms**: Always sorted alphabetically for consistency
2. **Subscription**: Works across all platforms once purchased
3. **Health Logs**: Platform field indicates where log was created
4. **Streaks**: Calculated from all platforms' logs
5. **Habits**: Sync across all platforms

## Implementation Notes

### Adding New Platform (iOS)
1. Set `platform: "ios"` in all writes
2. Add `"ios"` to `platforms` array in UserProfile
3. Use same Firestore structure as Android/Web
4. Implement iOS-specific health tracking (HealthKit)
5. Use App Store billing for subscriptions

### Platform Detection
- **Android**: User agent or native detection
- **Web**: Browser environment
- **iOS**: User agent or native detection

## Testing Cross-Platform

1. Create account on Android → Check `platforms: ["android"]`
2. Login on Web → Check `platforms: ["android", "web"]`
3. Login on iOS → Check `platforms: ["android", "web", "ios"]`
4. Verify subscription works across all platforms
5. Verify health logs sync correctly
6. Verify streaks calculate from all platforms
