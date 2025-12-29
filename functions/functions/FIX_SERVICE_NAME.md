# Fix: Service Name Validation Error

## The Problem

You're seeing this error:
> "Service name may only start with a letter and contain up to 49 lowercase letters, numbers or hyphens"

The issue: **Cloud Run service names must be ALL LOWERCASE** (no uppercase letters).

Your current name: `processBriefTask` ❌ (has uppercase 'B' and 'T')

## The Solution

Change the service name to one of these:

### Option 1: All Lowercase (Recommended)
```
processbrieftask
```

### Option 2: With Hyphens (More Readable)
```
process-brief-task
```

## How to Fix

1. In the "Service name" field, change:
   - From: `processBriefTask`
   - To: `processbrieftask` or `process-brief-task`

2. The red error should disappear

3. Continue with the rest of the form:
   - **Runtime:** `Node.js 22` (or `Node.js 20` - both work)
   - **Entry point:** `processBriefTask` (this can have uppercase - it's the function export name)
   - **Authentication:** "Allow public access" ✅ (correct)
   - **Source:** Upload ZIP or use inline editor

4. Click **"Create"**

## Important Notes

- **Service name** (Cloud Run) = Must be lowercase: `processbrieftask`
- **Entry point** (Function export) = Can have uppercase: `processBriefTask`

These are two different things:
- Service name = The Cloud Run service identifier (lowercase)
- Entry point = The exported function name in your code (can be any case)

## After Deployment

The function will be accessible at:
```
https://processbrieftask-244622801791.us-central1.run.app
```

But it will also have the Cloud Functions URL:
```
https://us-central1-vanish-auth-real.cloudfunctions.net/processBriefTask
```

Both URLs will work, but the Cloud Run URL uses the service name (lowercase).
