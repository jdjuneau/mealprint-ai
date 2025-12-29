// Script to deploy scheduled briefs functions
// This will temporarily replace index.js with the scheduled functions only

const fs = require('fs');
const path = require('path');

console.log('🚀 DEPLOYING SCHEDULED BRIEFS FUNCTIONS');

try {
  // Backup current index.js
  if (fs.existsSync('index.js')) {
    fs.copyFileSync('index.js', 'index.js.backup');
    console.log('✅ Backed up index.js');
  }

  // Copy scheduled functions to index.js
  fs.copyFileSync('index-scheduled-briefs.js', 'index.js');
  console.log('✅ Prepared scheduled functions for deployment');

  console.log('📋 Functions to deploy:');
  console.log('  - sendMorningBriefs (9 AM Eastern)');
  console.log('  - sendAfternoonBriefs (2 PM Eastern)');
  console.log('  - sendEveningBriefs (6 PM Eastern)');
  console.log('  - processBriefTask (Cloud Tasks worker)');

  console.log('\n🔥 Run this command to deploy:');
  console.log('firebase deploy --only functions:sendMorningBriefs,functions:sendAfternoonBriefs,functions:sendEveningBriefs,functions:processBriefTask --project=vanish-auth-real --force');

} catch (error) {
  console.error('❌ Error preparing deployment:', error);
  process.exit(1);
}
