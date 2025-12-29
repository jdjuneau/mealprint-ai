# Deployment Guide: Coachie Web App to playspace.games/coachieAI

## Overview

Your Next.js app requires server-side API routes (for Fitbit/Strava OAuth), so you need a hosting service that supports Node.js. GoDaddy's basic hosting doesn't support this.

## Recommended: Deploy to Vercel (Free)

Vercel is made by the Next.js creators and offers free hosting with automatic deployments.

### Step 1: Prepare Your Code

1. **Update Next.js config for production:**
   ```bash
   cd web
   ```

2. **Create a `.gitignore` entry** (if not already):
   - `.env.local` should already be ignored

### Step 2: Deploy to Vercel

#### Option A: Via Vercel Dashboard (Easiest)

1. **Push your code to GitHub:**
   ```bash
   git add .
   git commit -m "Prepare for deployment"
   git push origin main
   ```

2. **Go to Vercel:**
   - Visit https://vercel.com
   - Sign up/login with GitHub

3. **Import your project:**
   - Click "Add New Project"
   - Select your GitHub repository
   - Vercel will auto-detect Next.js

4. **Configure build settings:**
   - **Framework Preset:** Next.js (auto-detected)
   - **Root Directory:** `web` (if repo root is D:\Coachie)
   - **Build Command:** `npm run build` (default)
   - **Output Directory:** `.next` (default)

5. **Add Environment Variables:**
   - Click "Environment Variables"
   - Add all variables from `.env.local`:
     ```
     NEXT_PUBLIC_FIREBASE_API_KEY=...
     NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=...
     NEXT_PUBLIC_FIREBASE_PROJECT_ID=...
     NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET=...
     NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID=...
     NEXT_PUBLIC_FIREBASE_APP_ID=...
     NEXT_PUBLIC_OPENAI_API_KEY=...
     GEMINI_API_KEY=...
     NEXT_PUBLIC_FITBIT_CLIENT_ID=...
     FITBIT_CLIENT_SECRET=...
     NEXT_PUBLIC_STRAVA_CLIENT_ID=...
     STRAVA_CLIENT_SECRET=...
     ```

6. **Deploy:**
   - Click "Deploy"
   - Wait for build to complete
   - You'll get a URL like: `coachie-xyz.vercel.app`

#### Option B: Via Vercel CLI

```bash
# Install Vercel CLI
npm i -g vercel

# Login
vercel login

# Deploy
cd web
vercel

# Follow prompts:
# - Set up and deploy? Yes
# - Which scope? (your account)
# - Link to existing project? No
# - Project name? coachie-web
# - Directory? ./
# - Override settings? No
```

### Step 3: Connect GoDaddy Domain

1. **In Vercel Dashboard:**
   - Go to your project → Settings → Domains
   - Add domain: `playspace.games`
   - Add subdomain path: `/coachieAI` (or use subdomain: `coachieai.playspace.games`)

2. **In GoDaddy DNS Settings:**
   - Log into GoDaddy
   - Go to DNS Management for `playspace.games`
   - Add/Update DNS records:
     ```
     Type: CNAME
     Name: coachieai (or @ for root)
     Value: cname.vercel-dns.com
     TTL: 600
     ```

   **OR** if using subdirectory:
   - Add A record pointing to Vercel's IP (Vercel will provide this)
   - Or use CNAME if supported

3. **Wait for DNS propagation** (5-60 minutes)

### Step 4: Update OAuth Redirect URIs

After deployment, update your OAuth apps:

1. **Fitbit:**
   - Go to https://dev.fitbit.com/apps
   - Edit your app
   - Add callback URL: `https://playspace.games/coachieAI/auth/fitbit/callback`
   - Save

2. **Strava:**
   - Go to https://www.strava.com/settings/api
   - Edit your app
   - Update callback domain: `playspace.games`
   - Save

### Step 5: Test Deployment

1. Visit `https://playspace.games/coachieAI`
2. Test authentication
3. Test Fitbit/Strava OAuth flows
4. Check browser console for errors

## Alternative: Deploy to Netlify

If you prefer Netlify:

1. **Push to GitHub** (same as above)

2. **Go to Netlify:**
   - Visit https://netlify.com
   - Sign up/login with GitHub
   - Click "Add new site" → "Import an existing project"
   - Select your repo

3. **Configure build:**
   - **Base directory:** `web`
   - **Build command:** `npm run build`
   - **Publish directory:** `web/.next`

4. **Add environment variables** (same as Vercel)

5. **Custom domain:**
   - Settings → Domain management
   - Add custom domain: `playspace.games`
   - Follow DNS instructions

## Alternative: Railway or Render

Both support Node.js and are free tier friendly:

- **Railway:** https://railway.app
- **Render:** https://render.com

## Troubleshooting

### Build Fails
- Check environment variables are set
- Check Node.js version (should be 18+)
- Review build logs in Vercel dashboard

### OAuth Not Working
- Verify redirect URIs match exactly (including https, trailing slashes)
- Check environment variables are set in production
- Check browser console for errors

### Domain Not Working
- Wait for DNS propagation (can take up to 48 hours)
- Verify DNS records are correct
- Check Vercel domain settings

## Production Checklist

- [ ] All environment variables set in Vercel
- [ ] OAuth redirect URIs updated
- [ ] Domain connected and verified
- [ ] Test authentication flow
- [ ] Test Fitbit/Strava OAuth
- [ ] Test all major features
- [ ] Check mobile responsiveness
- [ ] Verify Firebase rules allow production domain

