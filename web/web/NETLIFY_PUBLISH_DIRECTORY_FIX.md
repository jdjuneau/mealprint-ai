# Fixing Netlify Publish Directory

If Netlify is locking `web/` in the publish directory, here are the options:

## Option 1: Use netlify.toml (Recommended)

The `netlify.toml` file has been updated. After you commit and push it, Netlify will use those settings instead of the UI:

1. Commit the file:
   ```bash
   git add web/netlify.toml
   git commit -m "Fix Netlify publish directory"
   git push
   ```

2. Netlify will automatically detect the file and use `.next` (relative to base directory)

## Option 2: Clear Base Directory

If the UI is forcing `web/`, try:

1. In Netlify settings, **clear the Base directory** (set it to empty/root)
2. Set **Publish directory** to `web/.next` (relative to repo root)
3. Set **Build command** to `cd web && npm run build`

## Option 3: Keep Current Settings

If `web/.next` is locked and you can't change it:
- This might actually be correct if Netlify is treating paths relative to repo root
- Try deploying with current settings first
- Check if the build succeeds and files are found

## After Fixing

1. Trigger a new deployment
2. Check the build logs to see if it finds the `.next` folder
3. If build succeeds but 404s persist, the issue is routing (Next.js plugin should handle this)
