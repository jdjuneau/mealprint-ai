# 🚀 Quick Start Guide - Web App

After restarting your computer, the dev server stops. Here's how to get it running again:

## ✅ Quick Start (Easiest)

**Option 1: Use the startup script**
```bash
cd web
start-dev.bat
```

**Option 2: Manual start**
```bash
cd web
npm run dev
```

The app will be available at: **http://localhost:3000**

## 🔧 Common Issues After Restart

### 1. Port 3000 Already in Use
If you see "Port 3000 is already in use":
```bash
# Kill process on port 3000
netstat -ano | findstr ":3000"
taskkill /F /PID [PID_NUMBER]
```

Or use the `start-dev.bat` script - it handles this automatically.

### 2. Dependencies Missing
If you see module errors:
```bash
cd web
npm install
```

### 3. Build Cache Issues
If the app won't start:
```bash
cd web
rm -rf .next
npm run dev
```

### 4. Environment Variables Missing
Make sure `web/.env.local` exists with your Firebase config.

## 📝 What to Check

1. ✅ **Dev server running?** Check http://localhost:3000
2. ✅ **Dependencies installed?** Check if `web/node_modules` exists
3. ✅ **Port free?** Port 3000 should be available
4. ✅ **Firebase config?** Check `web/.env.local` exists

## 🎯 Normal Startup Process

1. Open terminal
2. `cd web`
3. `npm run dev` (or `start-dev.bat`)
4. Wait for "Ready" message
5. Open http://localhost:3000

That's it! The dev server needs to be running for the web app to work.

# 🚀 Quick Start Guide - Web App

After restarting your computer, the dev server stops. Here's how to get it running again:

## ✅ Quick Start (Easiest)

**Option 1: Use the startup script**
```bash
cd web
start-dev.bat
```

**Option 2: Manual start**
```bash
cd web
npm run dev
```

The app will be available at: **http://localhost:3000**

## 🔧 Common Issues After Restart

### 1. Port 3000 Already in Use
If you see "Port 3000 is already in use":
```bash
# Kill process on port 3000
netstat -ano | findstr ":3000"
taskkill /F /PID [PID_NUMBER]
```

Or use the `start-dev.bat` script - it handles this automatically.

### 2. Dependencies Missing
If you see module errors:
```bash
cd web
npm install
```

### 3. Build Cache Issues
If the app won't start:
```bash
cd web
rm -rf .next
npm run dev
```

### 4. Environment Variables Missing
Make sure `web/.env.local` exists with your Firebase config.

## 📝 What to Check

1. ✅ **Dev server running?** Check http://localhost:3000
2. ✅ **Dependencies installed?** Check if `web/node_modules` exists
3. ✅ **Port free?** Port 3000 should be available
4. ✅ **Firebase config?** Check `web/.env.local` exists

## 🎯 Normal Startup Process

1. Open terminal
2. `cd web`
3. `npm run dev` (or `start-dev.bat`)
4. Wait for "Ready" message
5. Open http://localhost:3000

That's it! The dev server needs to be running for the web app to work.

