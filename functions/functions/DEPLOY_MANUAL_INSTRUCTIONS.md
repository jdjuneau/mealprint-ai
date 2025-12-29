# Manual Deployment Instructions (When Firebase CLI Times Out)

Since Firebase CLI keeps timing out, here are alternative deployment methods:

## Method 1: Firebase Console (Web UI)

1. Go to: https://console.firebase.google.com/project/vanish-auth-real/functions
2. Click "Deploy" or use the inline editor
3. Upload the compiled functions from `functions/lib/`

## Method 2: Wait and Retry

The timeout may be temporary. Try:
```bash
cd functions
npm run build
cd ..
firebase deploy --only functions:sendMorningBriefs,functions:sendAfternoonBriefs,functions:sendEveningBriefs,functions:processBriefTask --force
```

## Method 3: Deploy from Clean Directory

Create a minimal deployment:

```bash
# Create temporary deployment directory
mkdir briefs-deploy
cd briefs-deploy

# Copy only brief-related files
cp -r ../functions/lib/scheduledBriefs.js .
cp -r ../functions/lib/briefTaskQueue.js .
cp -r ../functions/briefs/index.js .
cp ../functions/package.json .

# Install dependencies
npm install

# Deploy
firebase deploy --only functions --force
```

## Method 4: Use Current System

The current brief system works and is deployed. It may timeout with 1000+ users, but it's functional. The Cloud Tasks code is ready and will activate once deployed.

## Verification

After deployment, verify:
```bash
firebase functions:list | grep -i brief
```

You should see:
- `sendMorningBriefs`
- `sendAfternoonBriefs`  
- `sendEveningBriefs`
- `processBriefTask`
