/**
 * Verification script for wearable tracking setup
 * Run with: node scripts/verify-wearable-setup.js
 */

const fs = require('fs')
const path = require('path')

console.log('🔍 Verifying Wearable Tracking Setup...\n')

const envPath = path.join(__dirname, '..', '.env.local')
const envExamplePath = path.join(__dirname, '..', 'env-example.txt')

let hasErrors = false

// Check if .env.local exists
if (!fs.existsSync(envPath)) {
  console.log('❌ .env.local file not found')
  console.log('   Create it from env-example.txt\n')
  hasErrors = true
} else {
  console.log('✅ .env.local file exists')
  
  // Read and check environment variables
  const envContent = fs.readFileSync(envPath, 'utf8')
  const requiredVars = [
    'NEXT_PUBLIC_FITBIT_CLIENT_ID',
    'FITBIT_CLIENT_SECRET',
    'NEXT_PUBLIC_STRAVA_CLIENT_ID',
    'STRAVA_CLIENT_SECRET',
  ]
  
  const optionalVars = [
    'NEXT_PUBLIC_OPENAI_API_KEY',
    'GEMINI_API_KEY',
  ]

  console.log('\n📋 Checking environment variables:')
  requiredVars.forEach((varName) => {
    if (envContent.includes(`${varName}=`) && !envContent.includes(`${varName}=your_`)) {
      console.log(`   ✅ ${varName}`)
    } else {
      console.log(`   ❌ ${varName} - Not set or using placeholder`)
      hasErrors = true
    }
  })
  
  console.log('\n📋 Optional environment variables:')
  optionalVars.forEach((varName) => {
    if (envContent.includes(`${varName}=`) && !envContent.includes(`${varName}=your_`)) {
      console.log(`   ✅ ${varName}`)
    } else {
      console.log(`   ⚠️  ${varName} - Not set (optional)`)
    }
  })
}

// Check if API routes exist
console.log('\n📁 Checking API routes:')
const apiRoutes = [
  'app/api/fitbit/token/route.ts',
  'app/api/fitbit/refresh/route.ts',
  'app/api/strava/token/route.ts',
  'app/api/strava/refresh/route.ts',
]

apiRoutes.forEach((route) => {
  const routePath = path.join(__dirname, '..', route)
  if (fs.existsSync(routePath)) {
    console.log(`   ✅ ${route}`)
  } else {
    console.log(`   ❌ ${route} - Missing`)
    hasErrors = true
  }
})

// Check if callback pages exist
console.log('\n📄 Checking callback pages:')
const callbacks = [
  'app/auth/fitbit/callback/page.tsx',
  'app/auth/strava/callback/page.tsx',
]

callbacks.forEach((callback) => {
  const callbackPath = path.join(__dirname, '..', callback)
  if (fs.existsSync(callbackPath)) {
    console.log(`   ✅ ${callback}`)
  } else {
    console.log(`   ❌ ${callback} - Missing`)
    hasErrors = true
  }
})

// Check if services exist
console.log('\n🔧 Checking services:')
const services = [
  'lib/services/fitbitService.ts',
  'lib/services/stravaService.ts',
  'lib/services/garminService.ts',
  'lib/services/healthSyncService.ts',
  'lib/services/healthTracking.ts',
]

services.forEach((service) => {
  const servicePath = path.join(__dirname, '..', service)
  if (fs.existsSync(servicePath)) {
    console.log(`   ✅ ${service}`)
  } else {
    console.log(`   ❌ ${service} - Missing`)
    hasErrors = true
  }
})

console.log('\n' + '='.repeat(50))
if (hasErrors) {
  console.log('❌ Setup incomplete. Please fix the errors above.')
  console.log('\nNext steps:')
  console.log('1. Create .env.local from env-example.txt')
  console.log('2. Register Fitbit app at https://dev.fitbit.com/apps')
  console.log('3. Register Strava app at https://www.strava.com/settings/api')
  console.log('4. Add credentials to .env.local')
  process.exit(1)
} else {
  console.log('✅ All files present!')
  console.log('\nNext steps:')
  console.log('1. Register Fitbit app at https://dev.fitbit.com/apps')
  console.log('2. Register Strava app at https://www.strava.com/settings/api')
  console.log('3. Add credentials to .env.local')
  console.log('4. Test at /health-tracking/test')
  process.exit(0)
}

