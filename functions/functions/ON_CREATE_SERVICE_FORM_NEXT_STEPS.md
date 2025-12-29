# You're on "Create service" Form - Next Steps

You're on the **"Create service"** form with the "Function" card selected. The fields you need are there, but they're **further down the page** or in **expandable sections**.

## What You've Already Filled (Good!)

- ✅ Service name: `processbrieftask` (correct!)
- ✅ Region: `us-central1`
- ✅ Runtime: `Node.js 22`
- ✅ Authentication: "Allow public access"

## What to Do Next

### Step 1: Scroll Down

**Scroll down** the form. The fields you need are below what's currently visible:

1. **Look for expandable sections** like:
   - "Runtime, build, connections and security settings" → **Click to expand**
   - "Container" or "Container settings" → **Click to expand**
   - "Advanced settings" → **Click to expand**

2. **Inside these sections, you'll find:**
   - **Entry point** field → Type: `processBriefTask`
   - **Timeout** field → Type: `540 seconds` or `540s`
   - **Memory** field → Type: `512 MiB` or select from dropdown

### Step 2: Find Source Code Section

Keep scrolling down. You should see:

- **"Source code"** or **"Code"** section
- Options like:
  - "Inline editor" (with a code editor)
  - "Upload ZIP" or "Zip upload"

### Step 3: Change Ingress (Important!)

While scrolling, also look for:
- **"Ingress"** section
- Change from **"Internal"** to **"All"** (if it's currently set to Internal)

## If You Still Can't Find the Fields

### Option A: Use "Write a function" Button Instead

1. **Cancel** or go back from this form
2. Look at the **top toolbar** (above the page)
3. Click **"Write a function"** button (has `{...}` icon)
4. This form has all fields clearly visible

### Option B: Use Command Line

Deploy via `gcloud` CLI instead:

```powershell
cd d:\Coachie\functions
.\DEPLOY_PROCESS_BRIEF_TASK_WORKING.bat
```

This bypasses the console form entirely.

## Quick Checklist

Before clicking "Create", make sure you've set:

- [x] Service name: `processbrieftask` ✅
- [x] Region: `us-central1` ✅
- [x] Runtime: `Node.js 22` ✅
- [x] Authentication: "Allow public access" ✅
- [ ] **Entry point:** `processBriefTask` (in expandable section)
- [ ] **Timeout:** `540 seconds` (in expandable section)
- [ ] **Memory:** `512 MiB` (in expandable section)
- [ ] **Ingress:** "All" (not "Internal")
- [ ] **Source code:** Upload ZIP or use inline editor

**Try scrolling down first** - the fields are there, just not immediately visible!
