# Fixing Next.js ChunkLoadError

## Quick Fix

If you're getting a `ChunkLoadError: Loading chunk app/layout failed` error, follow these steps:

### 1. Stop the Dev Server
Press `Ctrl+C` in the terminal where `npm run dev` is running.

### 2. Clear Build Cache
Run these commands in the `web` directory:

```bash
# Windows PowerShell
cd web
Remove-Item -Recurse -Force .next -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force node_modules\.cache -ErrorAction SilentlyContinue

# Or on Mac/Linux
cd web
rm -rf .next
rm -rf node_modules/.cache
```

### 3. Restart Dev Server
```bash
npm run dev
```

## If That Doesn't Work

### Option 1: Full Clean Rebuild
```bash
cd web
# Delete build cache
Remove-Item -Recurse -Force .next -ErrorAction SilentlyContinue

# Reinstall dependencies (optional, but can help)
npm install

# Restart dev server
npm run dev
```

### Option 2: Clear Browser Cache
1. Open browser DevTools (F12)
2. Right-click the refresh button
3. Select "Empty Cache and Hard Reload"
4. Or use `Ctrl+Shift+R` (Windows) / `Cmd+Shift+R` (Mac)

### Option 3: Use Incognito/Private Mode
Test in an incognito/private browser window to rule out browser cache issues.

## Why This Happens

ChunkLoadError typically occurs when:
- The `.next` build cache becomes corrupted
- There's a mismatch between server and client chunks
- The dev server was interrupted during a build
- Browser cache conflicts with new chunks

## Prevention

- Always stop the dev server cleanly (Ctrl+C)
- Don't interrupt builds in progress
- Clear cache if you see chunk errors

## Still Having Issues?

1. Check Next.js version: `npm list next`
2. Update Next.js: `npm install next@latest`
3. Check for TypeScript errors: `npm run lint`
4. Review browser console for other errors
