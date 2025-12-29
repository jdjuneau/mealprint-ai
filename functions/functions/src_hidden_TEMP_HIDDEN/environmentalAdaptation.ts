import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';

// Initialize Firebase Admin (will be initialized in main index.js)
const db = admin.firestore();

// =============================================================================
// ENVIRONMENTAL ADAPTATION - WEATHER, SEASON, LOCATION AWARENESS
// =============================================================================
export const adaptToEnvironment = functions.https.onCall(async (data, context) => {
  // Verify authentication
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }

  const userId = data.userId || context.auth.uid;
  if (!userId) {
    throw new functions.https.HttpsError('invalid-argument', 'User ID is required');
  }

  const location = data.location; // Optional: {lat, lng, city, country}
  const currentConditions = data.weather; // Optional: weather data

  try {
    console.log(`🌤️ Analyzing environmental factors for user: ${userId}`);

    // Get user's environmental preferences and history
    const environmentalProfile = await getEnvironmentalProfile(userId);

    // Analyze current environmental conditions
    const environmentalFactors = await analyzeEnvironmentalFactors(location, currentConditions);

    // Generate environment-aware habit adaptations
    const adaptations = generateEnvironmentalAdaptations(environmentalProfile, environmentalFactors);

    console.log(`✅ Generated ${adaptations.habitAdaptations.length} environmental adaptations`);

    return {
      success: true,
      environmentalFactors,
      adaptations,
      environmentalProfile,
      analyzedAt: new Date().toISOString(),
      userId: userId
    };

  } catch (error) {
    console.error('Error adapting to environment:', error);
    throw new functions.https.HttpsError('internal', 'Failed to analyze environmental factors');
  }
});

// =============================================================================
// ENVIRONMENTAL PROFILE MANAGEMENT
// =============================================================================
async function getEnvironmentalProfile(userId: string) {
  try {
    // Get user's environmental preferences and history
    const userDoc = await db.collection('users').doc(userId).get();
    const userData = userDoc.data();

    // Get environmental completion patterns
    const environmentalPatterns = await analyzeEnvironmentalCompletionPatterns(userId);

    return {
      preferences: {
        weatherPreference: userData?.weatherPreference || 'moderate', // prefers sunny, cloudy, rainy, etc.
        seasonalPreference: userData?.seasonalPreference || 'balanced',
        locationBased: userData?.locationBased || true,
        timeOfDayPreference: userData?.timeOfDayPreference || 'flexible'
      },
      patterns: environmentalPatterns,
      lastLocation: userData?.lastLocation,
      timezone: userData?.timezone || 'UTC'
    };

  } catch (error) {
    console.error('Error getting environmental profile:', error);
    return {
      preferences: {
        weatherPreference: 'moderate',
        seasonalPreference: 'balanced',
        locationBased: true,
        timeOfDayPreference: 'flexible'
      },
      patterns: {},
      lastLocation: null,
      timezone: 'UTC'
    };
  }
}

async function analyzeEnvironmentalCompletionPatterns(userId: string) {
  // Analyze how weather, season, and location affect habit completion rates
  const ninetyDaysAgo = new Date();
  ninetyDaysAgo.setDate(ninetyDaysAgo.getDate() - 90);

  try {
    const completionsQuery = db.collection('users').doc(userId).collection('completions')
      .where('completedAt', '>=', admin.firestore.Timestamp.fromDate(ninetyDaysAgo))
      .orderBy('completedAt', 'desc');

    const completionsSnapshot = await completionsQuery.get();
    const completions = completionsSnapshot.docs.map(doc => ({
      ...doc.data(),
      completedAt: doc.data().completedAt?.toDate(),
      weather: doc.data().weather, // Would be populated by app
      season: doc.data().season,
      location: doc.data().location
    }));

    // Analyze patterns (simplified - would be more sophisticated with real data)
    const patterns = {
      weatherImpact: {
        sunny: calculateAverageSuccessRate(completions.filter(c => c.weather === 'sunny')),
        cloudy: calculateAverageSuccessRate(completions.filter(c => c.weather === 'cloudy')),
        rainy: calculateAverageSuccessRate(completions.filter(c => c.weather === 'rainy'))
      },
      seasonalImpact: {
        spring: calculateAverageSuccessRate(completions.filter(c => c.season === 'spring')),
        summer: calculateAverageSuccessRate(completions.filter(c => c.season === 'summer')),
        fall: calculateAverageSuccessRate(completions.filter(c => c.season === 'fall')),
        winter: calculateAverageSuccessRate(completions.filter(c => c.season === 'winter'))
      },
      locationImpact: {
        home: calculateAverageSuccessRate(completions.filter(c => c.location === 'home')),
        work: calculateAverageSuccessRate(completions.filter(c => c.location === 'work')),
        outdoor: calculateAverageSuccessRate(completions.filter(c => c.location === 'outdoor'))
      }
    };

    return patterns;

  } catch (error) {
    console.error('Error analyzing environmental patterns:', error);
    return {};
  }
}

function calculateAverageSuccessRate(completions: any[]): number {
  if (completions.length === 0) return 0;

  // Simplified - would use actual success metrics
  const successful = completions.filter(c => c.value && c.value > 0).length;
  return (successful / completions.length) * 100;
}

// =============================================================================
// ENVIRONMENTAL FACTORS ANALYSIS
// =============================================================================
async function analyzeEnvironmentalFactors(location?: any, weatherData?: any) {
  const now = new Date();
  const currentHour = now.getHours();

  // Determine season
  const month = now.getMonth();
  let season: string;
  if (month >= 2 && month <= 4) season = 'spring';
  else if (month >= 5 && month <= 7) season = 'summer';
  else if (month >= 8 && month <= 10) season = 'fall';
  else season = 'winter';

  // Determine time of day
  let timeOfDay: string;
  if (currentHour >= 5 && currentHour < 12) timeOfDay = 'morning';
  else if (currentHour >= 12 && currentHour < 17) timeOfDay = 'afternoon';
  else if (currentHour >= 17 && currentHour < 22) timeOfDay = 'evening';
  else timeOfDay = 'night';

  // Use provided weather data or generate reasonable defaults
  const weather = weatherData || generateDefaultWeather(season, location);

  // Calculate environmental score (0-100, how conducive environment is for habits)
  const environmentalScore = calculateEnvironmentalScore(weather, season, timeOfDay, location);

  return {
    timestamp: now.toISOString(),
    season,
    timeOfDay,
    weather,
    location: location || { type: 'unknown' },
    environmentalScore,
    factors: {
      weatherSuitability: getWeatherSuitability(weather),
      seasonalEnergy: getSeasonalEnergy(season),
      timeOptimization: getTimeOptimization(timeOfDay),
      locationAccessibility: getLocationAccessibility(location)
    }
  };
}

function generateDefaultWeather(season: string, location?: any): any {
  // Generate reasonable default weather based on season and location
  const defaults = {
    spring: { condition: 'mild', temperature: 65, humidity: 60, uvIndex: 6 },
    summer: { condition: 'warm', temperature: 80, humidity: 70, uvIndex: 8 },
    fall: { condition: 'cool', temperature: 60, humidity: 50, uvIndex: 4 },
    winter: { condition: 'cold', temperature: 35, humidity: 40, uvIndex: 2 }
  };

  return {
    ...defaults[season as keyof typeof defaults],
    description: `${defaults[season as keyof typeof defaults].condition} and comfortable`,
    isDefault: true
  };
}

function calculateEnvironmentalScore(weather: any, season: string, timeOfDay: string, location?: any): number {
  let score = 70; // Base score

  // Weather impact
  if (weather.condition === 'sunny') score += 10;
  else if (weather.condition === 'rainy') score -= 15;
  else if (weather.condition === 'snowy') score -= 20;

  // Temperature impact (optimal range: 60-75°F)
  const temp = weather.temperature || 70;
  if (temp >= 60 && temp <= 75) score += 5;
  else if (temp >= 45 && temp <= 85) score += 0; // Acceptable
  else score -= 10; // Too hot or cold

  // Time of day impact
  if (timeOfDay === 'morning') score += 5; // Natural energy boost
  else if (timeOfDay === 'night') score -= 10; // Lower energy

  // Seasonal impact
  if (season === 'spring') score += 5; // Renewal energy
  else if (season === 'winter') score -= 5; // Lower energy

  return Math.max(0, Math.min(100, score));
}

function getWeatherSuitability(weather: any): { score: number; recommendations: string[] } {
  const score = weather.condition === 'sunny' ? 90 :
                weather.condition === 'cloudy' ? 75 :
                weather.condition === 'rainy' ? 60 : 70;

  const recommendations = [];
  if (weather.condition === 'rainy') {
    recommendations.push('Consider indoor habits like reading or meditation');
    recommendations.push('Great weather for habit stacking with indoor activities');
  } else if (weather.condition === 'sunny') {
    recommendations.push('Perfect for outdoor habits like walking or vitamin D exposure');
    recommendations.push('Higher energy levels may support more challenging habits');
  }

  return { score, recommendations };
}

function getSeasonalEnergy(season: string): { level: string; recommendations: string[] } {
  const seasonalData = {
    spring: { level: 'renewal', recommendations: ['Focus on new beginnings', 'Higher motivation for fresh starts'] },
    summer: { level: 'peak', recommendations: ['Take advantage of high energy', 'Great for challenging habits'] },
    fall: { level: 'stable', recommendations: ['Build routines for winter', 'Focus on consistency'] },
    winter: { level: 'conservation', recommendations: ['Prioritize recovery habits', 'Lower energy may require simpler habits'] }
  };

  return seasonalData[season as keyof typeof seasonalData] || seasonalData.spring;
}

function getTimeOptimization(timeOfDay: string): { score: number; optimalHabits: string[] } {
  const timeData = {
    morning: {
      score: 85,
      optimalHabits: ['Exercise', 'Meditation', 'Planning', 'Learning']
    },
    afternoon: {
      score: 75,
      optimalHabits: ['Work tasks', 'Creative activities', 'Social habits', 'Light exercise']
    },
    evening: {
      score: 60,
      optimalHabits: ['Reflection', 'Reading', 'Light stretching', 'Planning for tomorrow']
    },
    night: {
      score: 40,
      optimalHabits: ['Sleep preparation', 'Journaling', 'Gentle habits only']
    }
  };

  return timeData[timeOfDay as keyof typeof timeData] || timeData.morning;
}

function getLocationAccessibility(location?: any): { score: number; factors: string[] } {
  if (!location) {
    return { score: 70, factors: ['Location unknown', 'Assuming standard accessibility'] };
  }

  // Simplified location analysis
  let score = 70;
  const factors = [];

  if (location.type === 'home') {
    score += 15;
    factors.push('Home environment - high control over conditions');
  } else if (location.type === 'work') {
    score += 5;
    factors.push('Work environment - limited control over conditions');
  } else if (location.type === 'outdoor') {
    score -= 10;
    factors.push('Outdoor environment - weather dependent');
  }

  return { score, factors };
}

// =============================================================================
// ENVIRONMENTAL ADAPTATIONS GENERATION
// =============================================================================
function generateEnvironmentalAdaptations(environmentalProfile: any, environmentalFactors: any) {
  const adaptations = {
    habitAdaptations: [] as any[],
    environmentalModifiers: [] as any[],
    timingAdjustments: [] as any[],
    locationBasedSuggestions: [] as any[]
  };

  const { weather, season, timeOfDay, environmentalScore } = environmentalFactors;
  const { preferences, patterns } = environmentalProfile;

  // Weather-based adaptations
  if (weather.condition === 'rainy') {
    adaptations.habitAdaptations.push({
      type: 'weather_adaptation',
      trigger: 'rainy_weather',
      adaptation: 'Switch outdoor habits to indoor alternatives',
      examples: ['Walking → Indoor cardio video', 'Sun exposure → Indoor light therapy'],
      confidence: 85
    });
  } else if (weather.condition === 'sunny' && weather.temperature > 75) {
    adaptations.habitAdaptations.push({
      type: 'weather_adaptation',
      trigger: 'hot_sunny_weather',
      adaptation: 'Schedule outdoor activities early morning or evening',
      examples: ['Morning walk before heat', 'Evening outdoor meditation'],
      confidence: 80
    });
  }

  // Seasonal adaptations
  if (season === 'winter') {
    adaptations.habitAdaptations.push({
      type: 'seasonal_adaptation',
      trigger: 'winter_season',
      adaptation: 'Focus on indoor habits and light exposure',
      examples: ['Vitamin D supplementation', 'Indoor exercise routines', 'Light therapy'],
      confidence: 75
    });
  } else if (season === 'summer') {
    adaptations.habitAdaptations.push({
      type: 'seasonal_adaptation',
      trigger: 'summer_season',
      adaptation: 'Leverage higher energy for challenging habits',
      examples: ['Intensive exercise programs', 'Outdoor social activities', 'Extended learning sessions'],
      confidence: 70
    });
  }

  // Time-based adaptations
  if (timeOfDay === 'morning' && season === 'winter') {
    adaptations.timingAdjustments.push({
      type: 'circadian_adaptation',
      trigger: 'winter_morning',
      adaptation: 'Extend morning routine due to shorter daylight',
      recommendations: ['Use light therapy', 'Start habits slightly later', 'Focus on indoor morning activities'],
      confidence: 65
    });
  }

  // Environmental modifiers
  adaptations.environmentalModifiers.push({
    factor: 'weather',
    currentValue: weather.condition,
    impact: weather.condition === 'sunny' ? 'positive' : weather.condition === 'rainy' ? 'negative' : 'neutral',
    suggestions: getWeatherSuitability(weather).recommendations
  });

  adaptations.environmentalModifiers.push({
    factor: 'season',
    currentValue: season,
    impact: getSeasonalEnergy(season).level,
    suggestions: getSeasonalEnergy(season).recommendations
  });

  // Overall environmental assessment
  const assessment = {
    overallScore: environmentalScore,
    summary: `Environmental conditions are ${environmentalScore > 75 ? 'highly' : environmentalScore > 50 ? 'moderately' : 'somewhat'} conducive to habit formation`,
    recommendations: generateEnvironmentalRecommendations(environmentalFactors, environmentalProfile)
  };

  return {
    ...adaptations,
    assessment
  };
}

function generateEnvironmentalRecommendations(environmentalFactors: any, environmentalProfile: any): string[] {
  const recommendations = [];
  const { weather, season, timeOfDay, environmentalScore } = environmentalFactors;

  if (environmentalScore < 60) {
    recommendations.push('Consider simplifying habits due to challenging environmental conditions');
    recommendations.push('Focus on indoor, low-energy habits when weather is suboptimal');
  } else if (environmentalScore > 80) {
    recommendations.push('Take advantage of excellent conditions for more challenging habits');
    recommendations.push('Consider outdoor activities when weather supports them');
  }

  // Weather-specific recommendations
  if (weather.condition === 'sunny') {
    recommendations.push('Perfect conditions for outdoor exercise or vitamin D habits');
  } else if (weather.condition === 'rainy') {
    recommendations.push('Great day for indoor habits like reading or meditation');
  }

  // Seasonal recommendations
  if (season === 'spring') {
    recommendations.push('Spring energy is perfect for starting new habit challenges');
  } else if (season === 'winter') {
    recommendations.push('Winter is ideal for building consistent indoor routines');
  }

  return recommendations;
}
