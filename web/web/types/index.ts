// User Profile Types
export interface UserProfile {
  id: string
  name: string
  currentWeight: number
  goalWeight: number
  heightCm: number
  age: number
  gender: 'male' | 'female'
  activityLevel: 'sedentary' | 'lightly active' | 'moderately active' | 'very active' | 'extremely active'
  createdAt: Date
  updatedAt: Date
  estimatedDailyCalories?: number // Calculated BMR * activity factor
  dietaryPreference?: string
  goalTrend?: 'lose_weight' | 'build_muscle' | 'improve_fitness' | 'stay_healthy' | 'increase_energy'
  platform?: string // Platform tracking: "android", "web", or "ios"
  platforms?: string[] // Array of platforms user has used (for multi-platform tracking)
  username?: string | null
  startDate?: number
  nudgesEnabled?: boolean
  fcmToken?: string | null
  fcmTokens?: string[]
  subscription?: any
  ftueCompleted?: boolean
  preferredCookingMethods?: string[]
  mealsPerDay?: number
  snacksPerDay?: number
  notifications?: Record<string, boolean>
  mealTimes?: Record<string, string>
  menstrualCycleEnabled?: boolean
  averageCycleLength?: number
  averagePeriodLength?: number
  lastPeriodStart?: number | null
  useImperial?: boolean
}

// Health Log Types
export type HealthLogType = 'meal' | 'supplement' | 'workout' | 'sleep' | 'water' | 'weight' | 'mood' | 'sun_exposure'

export interface BaseHealthLog {
  id: string
  userId: string
  type: HealthLogType
  timestamp: Date
}

export interface MealLog extends BaseHealthLog {
  type: 'meal'
  foodName: string
  calories: number
  protein: number
  carbs: number
  fat: number
  photoUrl?: string
  micronutrients?: Record<string, number>
}

export interface SupplementLog extends BaseHealthLog {
  type: 'supplement'
  supplementName: string
  nutrients: Record<string, number>
  photoUrl?: string
}

export interface MoodLog extends BaseHealthLog {
  type: 'mood'
  level: number // 1-5 scale
  emotions?: string[]
  notes?: string
}

export interface WorkoutLog extends BaseHealthLog {
  type: 'workout'
  workoutType: string
  duration: number // minutes
  calories: number
  notes?: string
}

export interface SleepLog extends BaseHealthLog {
  type: 'sleep'
  hours: number
  quality: 1 | 2 | 3 | 4 | 5 // 1-5 scale
  notes?: string
}

export interface WaterLog extends BaseHealthLog {
  type: 'water'
  amount: number // ml
}

export interface WeightLog extends BaseHealthLog {
  type: 'weight'
  weight: number // kg
}

export interface MoodLog extends BaseHealthLog {
  type: 'mood'
  level: number // 1-5 scale
  emotions?: string[]
  notes?: string
}

export interface SunExposureLog extends BaseHealthLog {
  type: 'sun_exposure'
  duration: number // minutes
  timeOfDay?: string
  estimatedVitaminD?: number
}

export type HealthLog = MealLog | SupplementLog | WorkoutLog | SleepLog | WaterLog | WeightLog | MoodLog | SunExposureLog

// Ensure all log types have required fields
// Note: Module augmentation removed - types are already defined above

// Daily Log Summary
export interface DailyLog {
  id: string
  userId: string
  date: string // YYYY-MM-DD
  steps?: number
  workouts?: number
  sleepHours?: number
  waterAmount?: number
  caloriesConsumed?: number
  caloriesBurned?: number
  weight?: number
  micronutrientExtras?: Record<string, number>
  logs: HealthLog[]
  createdAt: Date
  updatedAt: Date
}

// Chat Message Types
export interface ChatMessage {
  id: string
  userId: string
  content: string
  isUser: boolean
  timestamp: Date
}

// AI Analysis Types
export interface MealAnalysis {
  food: string
  calories: number
  proteinG: number
  carbsG: number
  fatG: number
  confidence: number
}

export interface SupplementAnalysis {
  supplementName: string
  nutrients: Record<string, number>
}

// Micronutrient Types
export interface MicronutrientType {
  id: string
  displayName: string
  unit: string
  maleTarget?: { min?: number; max?: number }
  femaleTarget?: { min?: number; max?: number }
}

// Supplement Definition
export interface Supplement {
  id: string
  name: string
  nutrients: Record<string, number>
  createdAt: Date
}

// Quick Select Meal
export interface SavedMeal {
  id: string
  userId: string
  name: string
  foodName: string
  calories: number
  proteinG: number
  carbsG: number
  fatG: number
  createdAt: Date
  lastUsedAt: Date
  useCount: number
}

// Auth Types
export interface AuthUser {
  uid: string
  email?: string
  displayName?: string
  photoURL?: string
}

// API Response Types
export interface ApiResponse<T> {
  success: boolean
  data?: T
  error?: string
}

// Dashboard Stats
export interface DashboardStats {
  todayCalories: number
  todaySteps: number
  weeklyAverage: number
  streakDays: number
  goalProgress: number
}
