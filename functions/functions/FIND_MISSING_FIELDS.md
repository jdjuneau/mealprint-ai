# Where to Find the Missing Fields

If you don't see Entry point, Timeout, Memory, etc., here's where to look:

## Important: Are You on the Right Form?

You might be on a **generic Cloud Run service** form instead of a **Cloud Function** form. The forms look different!

### Check 1: What Button Did You Click?

- ❌ **"Create service"** → Generic Cloud Run service (different form)
- ✅ **"Write a function"** → Cloud Function form (has Entry point, Timeout, etc.)

### If You Clicked "Create service"

You need to switch to the function form:

1. **Cancel** the current form (or go back)
2. Look at the **top toolbar** for three buttons:
   - "Deploy container"
   - "Connect repo"  
   - **"Write a function"** ← **CLICK THIS ONE!** (has `{...}` icon)
3. This will open the correct form with Entry point, Timeout, Memory fields

## Where the Fields Are Located

### Option A: Expandable Sections

The fields might be in **collapsed/expandable sections**. Look for:

1. **"Runtime, build, connections and security settings"**
   - Click to **expand** this section
   - Entry point, Timeout, Memory should be inside

2. **"Container"** or **"Container settings"**
   - Click to expand
   - Memory, CPU, Timeout might be here

3. **"Advanced settings"** or **"Configuration"**
   - Click to expand
   - Entry point might be here

### Option B: Scroll Down

The form might be long. Try:
- **Scroll down** the page
- Look for sections below "Ingress"
- The fields might be further down

### Option C: Tabs or Steps

Some forms use tabs or steps:
- Look for tabs like "Configuration", "Runtime", "Source"
- Click through tabs to find Entry point, Timeout, Memory
- Or look for "NEXT" buttons to go to next step

## Field Names Might Be Different

Cloud Run forms sometimes use different names:

- **Entry point** might be called:
  - "Function entry point"
  - "Handler"
  - "Main function"
  - "Entry point function"

- **Timeout** might be called:
  - "Request timeout"
  - "Execution timeout"
  - "Timeout seconds"

- **Memory** might be called:
  - "Memory allocation"
  - "Memory limit"
  - "Container memory"

## Quick Fix: Use "Write a function" Button

**Easiest solution:** Start over with the correct form:

1. **Cancel** or go back
2. Click **"Write a function"** button (top toolbar, has `{...}` icon)
3. This form will definitely have:
   - Entry point field
   - Timeout field
   - Memory field
   - Source code section

## Alternative: Use gcloud CLI

If the console form is confusing, deploy via command line:

```powershell
cd d:\Coachie\functions
.\DEPLOY_PROCESS_BRIEF_TASK_WORKING.bat
```

This bypasses the console form entirely.
