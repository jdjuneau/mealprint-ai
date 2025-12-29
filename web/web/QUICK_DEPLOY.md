# Quick Deploy to playspace.games/coachieAI

## ⚡ Fastest Method: Vercel + GoDaddy

### 1. Push Code to GitHub (if not already)

```bash
cd D:\Coachie
git add .
git commit -m "Ready for deployment"
git push origin main
```

### 2. Deploy to Vercel

1. Go to https://vercel.com
2. Sign up/login with GitHub
3. Click "Add New Project"
4. Import your repository
5. **Configure:**
   - **Root Directory:** `web`
   - **Framework:** Next.js (auto-detected)
6. **Add Environment Variables:**
   - Copy all from `.env.local`
   - Add each one in Vercel dashboard
7. Click "Deploy"

### 3. Connect Domain

**Option A: Subdomain (Recommended)**
- In Vercel: Settings → Domains → Add `coachieai.playspace.games`
- In GoDaddy DNS: Add CNAME record:
  ```
  Name: coachieai
  Value: cname.vercel-dns.com
  ```

**Option B: Subdirectory**
- Requires reverse proxy setup (more complex)
- Better to use subdomain

### 4. Update OAuth Redirects

After deployment, update:
- **Fitbit:** `https://coachieai.playspace.games/auth/fitbit/callback`
- **Strava:** Callback domain: `playspace.games`

### 5. Test

Visit your deployed URL and test!

## 🚀 Alternative: One-Command Deploy

```bash
cd web
npm i -g vercel
vercel --prod
```

Follow prompts, then add domain in Vercel dashboard.

