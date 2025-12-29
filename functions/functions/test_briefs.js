/**
 * Test script for brief subscription filtering
 * 
 * Usage:
 *   node test_briefs.js <userId> <briefType>
 * 
 * Examples:
 *   node test_briefs.js USER_ID_123 morning
 *   node test_briefs.js USER_ID_123 afternoon
 *   node test_briefs.js USER_ID_123 evening
 * 
 * Or test multiple users:
 *   node test_briefs.js USER_ID_1,USER_ID_2 morning
 */

const admin = require('firebase-admin');
const { initializeApp } = require('firebase/app');
const { getFunctions, httpsCallable } = require('firebase/functions');

// Initialize Firebase Admin (for direct Firestore access if needed)
if (!admin.apps.length) {
  const serviceAccount = require('./serviceAccountKey.json'); // You'll need this file
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
  });
}

// Or use Firebase SDK (if you have it configured)
// const app = initializeApp(/* your config */);
// const functions = getFunctions(app);

async function testBrief(userId, briefType) {
  console.log(`\n🧪 Testing ${briefType} brief for user: ${userId}`);
  console.log('─'.repeat(60));
  
  try {
    // Option 1: Call the test function via HTTP (if deployed)
    // const testBriefs = httpsCallable(functions, 'testBriefs');
    // const result = await testBriefs({ userId, briefType });
    
    // Option 2: Direct function call (if running locally)
    const { testBriefs } = require('./lib/testBriefs');
    const result = await testBriefs({ data: { userId, briefType } });
    
    console.log('\n📊 Test Results:');
    console.log(`   User ID: ${result.userId}`);
    console.log(`   Brief Type: ${result.briefType}`);
    console.log(`   Subscription Tier: ${result.subscriptionTier}`);
    console.log(`   Is Pro: ${result.isPro}`);
    console.log(`   Is Free: ${result.isFree}`);
    console.log(`   Should Generate: ${result.shouldGenerate ? '✅ YES' : '❌ NO'}`);
    if (result.skipReason) {
      console.log(`   Skip Reason: ${result.skipReason}`);
    }
    console.log(`   User Exists: ${result.userExists ? '✅' : '❌'}`);
    console.log(`   Has Existing Brief: ${result.hasExistingBrief ? '✅' : '❌'}`);
    console.log(`   Nudges Enabled: ${result.nudgesEnabled ? '✅' : '❌'}`);
    console.log(`   Has FCM Token: ${result.hasFcmToken ? '✅' : '❌'}`);
    console.log(`\n   ${result.message}`);
    
    // Validation
    console.log('\n✅ Validation:');
    if (briefType === 'morning') {
      if (result.shouldGenerate) {
        console.log('   ✅ PASS: Morning briefs should generate for all users');
      } else {
        console.log('   ❌ FAIL: Morning briefs should generate for all users');
      }
    } else if (briefType === 'afternoon' || briefType === 'evening') {
      if (result.isPro && result.shouldGenerate) {
        console.log(`   ✅ PASS: ${briefType} briefs should generate for Pro users`);
      } else if (result.isFree && !result.shouldGenerate && result.skipReason === 'free_tier') {
        console.log(`   ✅ PASS: ${briefType} briefs should skip for free users`);
      } else {
        console.log(`   ❌ FAIL: Unexpected result for ${briefType} brief`);
      }
    }
    
    return result;
  } catch (error) {
    console.error(`\n❌ Error testing brief:`, error);
    throw error;
  }
}

async function testBriefsBatch(userIds, briefType) {
  console.log(`\n🧪 Testing ${briefType} briefs for ${userIds.length} users`);
  console.log('─'.repeat(60));
  
  try {
    const { testBriefsBatch } = require('./lib/testBriefs');
    const result = await testBriefsBatch({ data: { userIds, briefType } });
    
    console.log('\n📊 Batch Test Results:');
    console.log(`   Brief Type: ${result.briefType}`);
    console.log(`   Total Users: ${result.summary.total}`);
    console.log(`   Should Generate: ${result.summary.shouldGenerate}`);
    console.log(`   Should Skip: ${result.summary.shouldSkip}`);
    console.log(`   Errors: ${result.summary.errors}`);
    console.log(`   Pro Users: ${result.summary.proUsers}`);
    console.log(`   Free Users: ${result.summary.freeUsers}`);
    
    console.log('\n📋 Individual Results:');
    result.results.forEach((r, i) => {
      console.log(`   ${i + 1}. ${r.userId}: ${r.tier} - ${r.message}`);
    });
    
    return result;
  } catch (error) {
    console.error(`\n❌ Error testing briefs batch:`, error);
    throw error;
  }
}

// CLI usage
if (require.main === module) {
  const args = process.argv.slice(2);
  
  if (args.length < 2) {
    console.log(`
Usage: node test_briefs.js <userId|userIds> <briefType>

Examples:
  node test_briefs.js USER_ID_123 morning
  node test_briefs.js USER_ID_123 afternoon
  node test_briefs.js USER_ID_123 evening
  node test_briefs.js USER_ID_1,USER_ID_2 morning  (batch test)
    `);
    process.exit(1);
  }
  
  const userIdArg = args[0];
  const briefType = args[1];
  
  if (!['morning', 'afternoon', 'evening'].includes(briefType)) {
    console.error('❌ briefType must be: morning, afternoon, or evening');
    process.exit(1);
  }
  
  (async () => {
    try {
      if (userIdArg.includes(',')) {
        // Batch test
        const userIds = userIdArg.split(',').map(id => id.trim());
        await testBriefsBatch(userIds, briefType);
      } else {
        // Single user test
        await testBrief(userIdArg, briefType);
      }
      process.exit(0);
    } catch (error) {
      console.error('Test failed:', error);
      process.exit(1);
    }
  })();
}

module.exports = { testBrief, testBriefsBatch };
