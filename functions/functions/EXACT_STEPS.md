# EXACT STEPS - NO CONFUSION

## You're on "Create service" form. Here's EXACTLY what to do:

### Step 1: Scroll Down
- **Scroll down** the page
- Look for section: **"Runtime, build, connections and security settings"**
- **CLICK THE ARROW** to expand it

### Step 2: Fill These Fields (inside that expanded section)
- **Entry point:** Type `processBriefTask`
- **Timeout:** Type `540` (or `540 seconds`)
- **Memory:** Select `512 MiB` from dropdown

### Step 3: Keep Scrolling
- Scroll down MORE
- Find section: **"Source code"** or **"Code"**
- You'll see either:
  - Inline editor (code box)
  - OR "Upload ZIP" button

### Step 4: Add Source Code

**EASIEST: Upload ZIP**
1. First, run this in PowerShell:
   ```powershell
   cd d:\Coachie\functions
   .\CREATE_DEPLOYMENT_ZIP.bat
   ```
2. In the form, click **"Upload ZIP"** or **"Browse"**
3. Select file: `processBriefTask-deploy.zip`

### Step 5: Change Ingress
- Find **"Ingress"** section
- Change from **"Internal"** to **"All"**

### Step 6: Click "Create"
- Scroll to bottom
- Click blue **"Create"** button

---

## IF YOU STILL DON'T SEE THE FIELDS:

**CANCEL the form and do this:**

1. Click **"Cancel"** button
2. Look at **TOP TOOLBAR** (above the page)
3. You'll see 3 buttons:
   - "Deploy container"
   - "Connect repo"
   - **"Write a function"** ← CLICK THIS ONE
4. This opens a DIFFERENT form with all fields visible

---

## THAT'S IT. NO MORE SCROLLING THROUGH DOCS.
