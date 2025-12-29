// Test script for nudge functions
// Run with: node test_nudges.js

const admin = require('firebase-admin');

// Initialize Firebase Admin (you'll need to set up credentials)
if (!admin.apps.length) {
  // For testing locally, you can use service account key
  // admin.initializeApp({
  //   credential: admin.credential.cert('./serviceAccountKey.json'),
  //   databaseURL: 'YOUR_DATABASE_URL'
  // });
}

const functions = require('./index');

// Test user ID - replace with a real user ID from your database
const TEST_USER_ID = 'test_user_id_here';

async function testNudgeGeneration() {
  console.log('🧪 Testing nudge generation...\n');

  try {
    // Mock user data
    const mockUser = {
      id: TEST_USER_ID,
      name: 'Test User'
    };

    const mockUserData = {
      profile: {
        name: 'Test User',
        currentWeight: 70,
        goalWeight: 65,
        activityLevel: 'moderate'
      },
      dailyLog: {
        steps: 8500,
        water: 1800,
        caloriesConsumed: 1850,
        caloriesBurned: 320
      },
      healthLogs: [
        {
          type: 'meal',
          foodName: 'Chicken Salad',
          calories: 350,
          protein: 25,
          timestamp: Date.now() - 1000 * 60 * 60 * 12 // 12 hours ago
        },
        {
          type: 'workout',
          workoutType: 'Running',
          durationMin: 30,
          caloriesBurned: 280,
          timestamp: Date.now() - 1000 * 60 * 60 * 10 // 10 hours ago
        }
      ]
    };

    // Test each time of day
    const timesOfDay = ['morning', 'midday', 'evening'];

    for (const timeOfDay of timesOfDay) {
      console.log(`\n🌅 Testing ${timeOfDay} nudge:`);

      // Call the nudge generation function
      const nudgeMessage = await functions.generateTimedPersonalizedNudge(mockUser, mockUserData, timeOfDay);

      console.log(`Message: "${nudgeMessage}"`);
      console.log(`Length: ${nudgeMessage.length} characters`);
    }

    console.log('\n✅ Nudge generation test completed!');

  } catch (error) {
    console.error('❌ Error testing nudges:', error);
  }
}

// Test the pattern analysis
async function testPatternAnalysis() {
  console.log('\n🔍 Testing pattern analysis...\n');

  const mockHealthLogs = [
    { type: 'meal', calories: 450, protein: 30, timestamp: Date.now() },
    { type: 'meal', calories: 350, protein: 25, timestamp: Date.now() },
    { type: 'workout', caloriesBurned: 280, timestamp: Date.now() },
    { type: 'water', ml: 500, timestamp: Date.now() },
    { type: 'water', ml: 400, timestamp: Date.now() }
  ];

  const mockDailyLog = {
    steps: 8500,
    water: 900,
    caloriesConsumed: 1800
  };

  const patterns = functions.analyzeHealthPatterns(mockHealthLogs, mockDailyLog);

  console.log('📊 Pattern Analysis Results:');
  console.log(`- Total Protein: ${patterns.totalProtein}g`);
  console.log(`- Total Calories Burned: ${patterns.totalCaloriesBurned}`);
  console.log(`- Total Water: ${patterns.totalWater}ml`);
  console.log(`- Sleep Hours: ${patterns.sleepHours}`);
  console.log(`- Habits Logged: ${patterns.habitsLogged}/5`);
  console.log(`- Low Protein: ${patterns.hasLowProtein}`);
  console.log(`- Low Sleep: ${patterns.hasLowSleep}`);
  console.log(`- Quick Insight: "${patterns.quickInsight}"`);
}

// Run tests
async function runTests() {
  console.log('🚀 Starting Coachie Nudge System Tests\n');

  await testPatternAnalysis();
  await testNudgeGeneration();

  console.log('\n🎉 All tests completed!');
}

// Export for use in other files
module.exports = {
  testNudgeGeneration,
  testPatternAnalysis,
  runTests
};

// Run if called directly
if (require.main === module) {
  runTests().catch(console.error);
}
