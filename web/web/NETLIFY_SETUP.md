# Netlify Build Settings Fix

Your Netlify build settings show "Not set" for build command and publish directory. Here's how to fix it:

## Option 1: Set in Netlify UI (Quick Fix)

1. Go to: https://app.netlify.com/sites/coachieai/settings/deploys
2. Scroll to **"Build settings"**
3. Click **"Edit settings"**
4. Set:
   - **Build command:** `npm run build`
   - **Publish directory:** `.next`
5. Click **"Save"**
6. Go to **"Deploys"** tab and click **"Trigger deploy"** → **"Deploy site"**

## Option 2: Use netlify.toml (After committing)

The `netlify.toml` file is already created. After you commit and push it:

1. Commit the file:
   ```bash
   git add web/netlify.toml
   git commit -m "Add Netlify configuration"
   git push
   ```

2. Netlify will automatically detect the file and use those settings

## Why This Fixes 404 Errors

Next.js needs:
- **Build command:** To compile the app
- **Publish directory:** To know where the built files are (`.next` folder)
- **Next.js plugin:** To handle routing (already in netlify.toml)

Without these, Netlify doesn't know how to build or serve your Next.js app, causing 404 errors.

## After Setting Build Settings

1. Trigger a new deployment
2. Wait for build to complete
3. Test your site - links should work now!
