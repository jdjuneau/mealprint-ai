# Files Needed for Inline Editor

If you're using the inline editor, you need to add these files:

## Main Entry Point

**File:** `index.js` (or `processBriefTask-standalone.js`)
**Content:**
```javascript
// STANDALONE ENTRY POINT FOR processBriefTask ONLY
const functions = require('firebase-functions');
const admin = require('firebase-admin');

if (!admin.apps.length) {
  admin.initializeApp();
}

// Import ONLY processBriefTask from compiled output
const briefTaskQueue = require('./lib/briefTaskQueue');

// Export ONLY processBriefTask
exports.processBriefTask = briefTaskQueue.processBriefTask;
```

## Required Files from `lib/` Directory

You need to upload ALL of these files to the inline editor:

1. **lib/briefTaskQueue.js** - Contains the `processBriefTask` function
2. **lib/scheduledBriefs.js** - May be imported by briefTaskQueue
3. **lib/generateBrief.js** - Used to generate briefs
4. **lib/subscriptionVerification.js** - Used to check user tiers
5. **Any other .js files in lib/** that are imported

## Easier Option: Use ZIP Upload

Instead of manually adding all files, use ZIP upload:

1. Run: `.\CREATE_DEPLOYMENT_ZIP.bat`
2. In the form, select "Upload ZIP" instead of inline editor
3. Upload `processBriefTask-deploy.zip`

This is much easier and less error-prone!
