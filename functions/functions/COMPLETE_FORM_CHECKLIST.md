# Complete Form Checklist for processBriefTask

Based on what you've already filled out, here's what else you need to configure:

## ✅ Already Configured (Good!)

- ✅ Service name: `processbrieftask` (lowercase - correct!)
- ✅ Region: `us-central1`
- ✅ Runtime: `Node.js 22` (or `Node.js 20` - both work)
- ✅ Authentication: "Allow public access" ✅
- ✅ Billing: "Request-based" ✅
- ✅ Service scaling: "Auto scaling" with min 0 ✅

## ⚠️ Needs to be Changed

### 1. Ingress (IMPORTANT!)
- **Current:** "Internal" ❌
- **Change to:** "All" ✅
- **Why:** "Internal" only allows traffic from within your VPC. Since Cloud Tasks calls this from outside, you need "All" to allow external HTTP access.

## 📝 Missing Fields to Fill Out

### 2. Expand "Runtime, build, connections and security settings"

Click to expand this section. You'll need to set:

- **Entry point:** `processBriefTask` (this is the function export name - can have uppercase)
- **Timeout:** `540 seconds` (9 minutes)
- **Memory:** `512 MiB`
- **Maximum number of instances:** `100` (optional, but recommended)

### 3. Source Code Section

Since you selected "Use an inline editor to create a function", you need to:

**Option A: Use Inline Editor**
1. Scroll down to find the code editor
2. Replace the default function code with the contents of `processBriefTask-standalone.js`
3. You'll also need to add files from `lib/` directory:
   - Look for "Add file" button or file tree
   - Upload all `.js` files from `lib/` folder (especially `briefTaskQueue.js`, `scheduledBriefs.js`, `generateBrief.js`, `subscriptionVerification.js`)

**Option B: Switch to ZIP Upload (Easier)**
1. Go back and select "Upload ZIP" instead of inline editor
2. First, create the ZIP:
   ```powershell
   cd d:\Coachie\functions
   .\CREATE_DEPLOYMENT_ZIP.bat
   ```
3. Upload `processBriefTask-deploy.zip`

## 📋 Complete Checklist

Before clicking "Create", make sure:

- [ ] Service name: `processbrieftask` (lowercase) ✅
- [ ] Region: `us-central1` ✅
- [ ] Runtime: `Node.js 22` or `Node.js 20` ✅
- [ ] **Entry point:** `processBriefTask` ⚠️ **MUST FILL THIS!**
- [ ] **Timeout:** `540 seconds` ⚠️ **MUST FILL THIS!**
- [ ] **Memory:** `512 MiB` ⚠️ **MUST FILL THIS!**
- [ ] Maximum instances: `100` (optional but recommended)
- [ ] **Ingress:** Change from "Internal" to **"All"** ⚠️ **IMPORTANT!**
- [ ] Authentication: "Allow public access" ✅
- [ ] **Source code:** Either paste code in inline editor OR upload ZIP ⚠️ **MUST FILL THIS!**

## 🎯 Quick Action Items

1. **Change Ingress to "All"** (critical - otherwise function won't be accessible)
2. **Expand "Runtime, build, connections and security settings"** and fill:
   - Entry point: `processBriefTask`
   - Timeout: `540 seconds`
   - Memory: `512 MiB`
3. **Add source code** (either inline editor with all lib files, or upload ZIP)

Then click **"Create"**!
