# ACTUAL STEPS FOR THE FORM YOU'RE ON

You're on the "Create service" form with "Function" selected. Here's what to do:

## Step 1: Fill the Basic Fields You See

Fill in what's visible:
- **Service name:** `processbrieftask` (lowercase)
- **Region:** Change to `us-central1` (you have europe-west1 selected)
- **Runtime:** `Node.js 22` ✅ (already correct)

## Step 2: Look for These Options

After filling the basic fields, look for:

### Option A: "Next" or "Continue" Button
- Scroll down
- Look for a **"Next"** or **"Continue"** button
- Click it - this might show the Entry point, Timeout, Memory fields on the next page

### Option B: Expandable Section
- Scroll down past "Authentication"
- Look for a section that says:
  - **"Runtime, build, connections and security settings"** → Click arrow to expand
  - OR **"Advanced settings"** → Click arrow to expand
  - OR **"Container settings"** → Click arrow to expand
- Entry point, Timeout, Memory should be inside

### Option C: Tabs at Top
- Look for tabs like: "Basic", "Configuration", "Runtime", "Source"
- Click through tabs to find Entry point, Timeout, Memory

## Step 3: If You Still Don't See Them

**The fields might appear AFTER you fill in the source code section.**

1. **Scroll ALL the way down** the form
2. Look for **"Source code"** or **"Code"** section
3. You should see either:
   - Inline code editor
   - OR "Upload ZIP" button
4. **After you add source code**, the Entry point field might appear

## Step 4: Alternative - Use Command Line

If the form is too confusing, just use command line:

```powershell
cd d:\Coachie\functions
.\DEPLOY_PROCESS_BRIEF_TASK_WORKING.bat
```

This bypasses the form entirely.

## What to Fill (When You Find the Fields)

- **Entry point:** `processBriefTask`
- **Timeout:** `540` (seconds)
- **Memory:** `512 MiB`
- **Ingress:** Change to "All" (not "Internal")

---

**Try scrolling down and looking for a "Next" button or expandable sections first.**
