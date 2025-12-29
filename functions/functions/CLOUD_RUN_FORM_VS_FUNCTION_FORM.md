# Cloud Run Service Form vs Cloud Function Form

## The Problem

There are **TWO different forms** in Cloud Run:

1. **"Create service"** → Generic Cloud Run service (for containers)
2. **"Write a function"** → Cloud Function (2nd Gen) form

You might be on the wrong one!

## How to Tell Which Form You're On

### Generic "Create service" Form Has:
- ✅ Service name
- ✅ Runtime dropdown
- ✅ Authentication options
- ✅ Billing options
- ✅ Scaling options
- ✅ Ingress options
- ❌ **NO Entry point field** (or it's hidden)
- ❌ **NO Timeout field** (or it's in advanced settings)
- ❌ **NO Memory field** (or it's in container settings)

### "Write a function" Form Has:
- ✅ Function name
- ✅ Runtime dropdown
- ✅ **Entry point field** (visible, usually in "Runtime settings")
- ✅ **Timeout field** (visible, usually in "Runtime settings")
- ✅ **Memory field** (visible, usually in "Runtime settings")
- ✅ Source code section (inline editor or ZIP upload)
- ✅ Trigger configuration

## Solution: Switch to Function Form

1. **Cancel** or go back from current form
2. Look at **top toolbar** (above search/filters)
3. You should see three buttons:
   ```
   [Deploy container]  [Connect repo]  [Write a function {...}]
   ```
4. Click **"Write a function"** (the one with `{...}` icon)
5. This opens the correct form with all the fields you need

## If You Must Use "Create service" Form

If you're stuck on the generic service form, the fields are hidden in expandable sections:

1. **Scroll down** the form
2. Look for **"Container"** section → expand it
   - Memory might be here
3. Look for **"Runtime, build, connections and security settings"** → expand it
   - Entry point might be here
   - Timeout might be here
4. Look for **"Advanced settings"** → expand it
   - Entry point might be here

But honestly, **just use "Write a function"** - it's much easier!
