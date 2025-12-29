'use client'

import { useState } from 'react'
import { useAuth } from '../lib/contexts/AuthContext'
import toast from 'react-hot-toast'

type SignupStep = 'units' | 'basic' | 'physical' | 'dietary' | 'meal' | 'health' | 'complete'

export default function AuthScreen() {
  const [isSignUp, setIsSignUp] = useState(false)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  
  // Unit selection
  const [useImperial, setUseImperial] = useState(false)
  const [currentStep, setCurrentStep] = useState<SignupStep>('units')
  
  // Basic Information
  const [name, setName] = useState('')
  const [age, setAge] = useState('')
  const [gender, setGender] = useState<'male' | 'female'>('male')
  
  // Physical Information
  const [heightCm, setHeightCm] = useState('')
  const [currentWeight, setCurrentWeight] = useState('')
  const [goalWeight, setGoalWeight] = useState('')
  const [activityLevel, setActivityLevel] = useState<'sedentary' | 'lightly active' | 'moderately active' | 'very active' | 'extremely active'>('moderately active')
  
  // Dietary Preferences
  const [dietaryPreference, setDietaryPreference] = useState('')
  const [goalTrend, setGoalTrend] = useState<'lose_weight' | 'build_muscle' | 'improve_fitness' | 'stay_healthy' | 'increase_energy'>('lose_weight')
  
  // Meal Preferences
  const [mealsPerDay, setMealsPerDay] = useState('3')
  const [snacksPerDay, setSnacksPerDay] = useState('0')
  const [preferredCookingMethods, setPreferredCookingMethods] = useState<string[]>([])
  
  // Health Tracking
  const [menstrualCycleEnabled, setMenstrualCycleEnabled] = useState(false)
  const [averageCycleLength, setAverageCycleLength] = useState('28')
  const [averagePeriodLength, setAveragePeriodLength] = useState('5')
  const [lastPeriodStart, setLastPeriodStart] = useState('')
  
  const [loading, setLoading] = useState(false)

  const { signInWithGoogle, signInWithEmail, signUpWithEmail } = useAuth()

  const handleUnitSelection = (imperial: boolean) => {
    setUseImperial(imperial)
    setCurrentStep('basic')
  }

  const canProceedFromStep = (step: SignupStep): boolean => {
    switch (step) {
      case 'basic':
        return !!(name && age && gender)
      case 'physical':
        return !!(heightCm && currentWeight && goalWeight && activityLevel)
      case 'dietary':
        return !!(dietaryPreference && goalTrend)
      case 'meal':
        return !!(mealsPerDay && snacksPerDay && preferredCookingMethods.length > 0)
      case 'health':
        if (gender === 'female') {
          if (menstrualCycleEnabled) {
            return !!(averageCycleLength && averagePeriodLength)
          }
        }
        return true
      default:
        return true
    }
  }

  const handleNext = () => {
    if (!canProceedFromStep(currentStep)) {
      toast.error('Please fill in all required fields')
      return
    }

    const steps: SignupStep[] = ['units', 'basic', 'physical', 'dietary', 'meal', 'health', 'complete']
    const currentIndex = steps.indexOf(currentStep)
    if (currentIndex < steps.length - 1) {
      setCurrentStep(steps[currentIndex + 1])
    }
  }

  const handleBack = () => {
    const steps: SignupStep[] = ['units', 'basic', 'physical', 'dietary', 'meal', 'health', 'complete']
    const currentIndex = steps.indexOf(currentStep)
    if (currentIndex > 0) {
      setCurrentStep(steps[currentIndex - 1])
    }
  }

  const handleEmailAuth = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!email || !password) return

    if (isSignUp && currentStep !== 'complete') {
      toast.error('Please complete all steps of the profile questionnaire')
      return
    }

    if (isSignUp && (!name || !age || !heightCm || !currentWeight || !goalWeight || !dietaryPreference || preferredCookingMethods.length === 0)) {
      toast.error('Please fill in all required fields')
      return
    }

    setLoading(true)
    try {
      if (isSignUp) {
        // Convert imperial to metric if needed
        let heightCmValue = parseFloat(heightCm) || 0
        let currentWeightKg = parseFloat(currentWeight) || 0
        let goalWeightKg = parseFloat(goalWeight) || 0

        if (useImperial) {
          // Convert inches to cm
          heightCmValue = heightCmValue * 2.54
          // Convert lbs to kg
          currentWeightKg = currentWeightKg * 0.453592
          goalWeightKg = goalWeightKg * 0.453592
        }

        const profileData = {
          name,
          age: parseInt(age),
          gender,
          heightCm: heightCmValue,
          currentWeight: currentWeightKg,
          goalWeight: goalWeightKg,
          activityLevel,
          dietaryPreference,
          goalTrend,
          mealsPerDay: parseInt(mealsPerDay),
          snacksPerDay: parseInt(snacksPerDay),
          preferredCookingMethods,
          menstrualCycleEnabled,
          averageCycleLength: menstrualCycleEnabled ? parseInt(averageCycleLength) : undefined,
          averagePeriodLength: menstrualCycleEnabled ? parseInt(averagePeriodLength) : undefined,
          lastPeriodStart: menstrualCycleEnabled && lastPeriodStart ? parseInt(lastPeriodStart) : undefined,
          useImperial,
          nudgesEnabled: true,
          notifications: {
            morningBrief: true,
            afternoonBrief: true,
            eveningBrief: true,
            mealPlan: true,
            mealReminders: true,
          },
          mealTimes: {
            breakfast: '08:00',
            lunch: '12:00',
            dinner: '18:00',
            snack1: '15:00',
            snack2: '21:00',
          },
        }
        await signUpWithEmail(email, profileData, password)
        toast.success('Account created successfully!')
      } else {
        await signInWithEmail(email, password)
        toast.success('Welcome back!')
      }
    } catch (error: any) {
      toast.error(error.message || 'Authentication failed')
    } finally {
      setLoading(false)
    }
  }

  const handleGoogleAuth = async () => {
    setLoading(true)
    try {
      await signInWithGoogle()
      toast.success('Welcome to Coachie!')
    } catch (error: any) {
      toast.error(error.message || 'Google sign-in failed')
    } finally {
      setLoading(false)
    }
  }

  // Unit selection screen - FIRST STEP
  if (isSignUp && currentStep === 'units') {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-primary-50 to-primary-100 p-4">
        <div className="max-w-md w-full">
          <div className="bg-white rounded-lg shadow-lg p-8">
            <div className="text-center mb-6">
              <h1 className="text-4xl font-bold text-gray-900 mb-2">🏋️ Coachie</h1>
              <h2 className="text-2xl font-semibold text-gray-900 mb-2">Choose Your Units</h2>
              <p className="text-gray-600">Select your preferred unit system for measurements</p>
            </div>

            <div className="space-y-4 mb-6">
              <button
                onClick={() => handleUnitSelection(false)}
                className="w-full px-4 py-4 bg-blue-600 text-white rounded-lg hover:bg-blue-700 font-medium text-lg transition-colors"
              >
                Metric (kg, cm)
              </button>
              <button
                onClick={() => handleUnitSelection(true)}
                className="w-full px-4 py-4 bg-green-600 text-white rounded-lg hover:bg-green-700 font-medium text-lg transition-colors"
              >
                Imperial (lbs, inches)
              </button>
            </div>

            <div className="text-center">
              <button
                onClick={() => {
                  setIsSignUp(false)
                  setCurrentStep('units')
                }}
                className="text-primary-600 hover:text-primary-500 text-sm"
              >
                Already have an account? Sign in
              </button>
            </div>
          </div>
        </div>
      </div>
    )
  }

  // Multi-step signup form
  if (isSignUp && currentStep !== 'complete') {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-primary-50 to-primary-100 p-4">
        <div className="max-w-2xl w-full">
          <div className="bg-white rounded-lg shadow-lg p-8">
            {/* Progress indicator */}
            <div className="mb-6">
              <div className="flex items-center justify-between mb-2">
                <span className="text-sm font-medium text-gray-700">
                  Step {['units', 'basic', 'physical', 'dietary', 'meal', 'health', 'complete'].indexOf(currentStep)} of 6
                </span>
                <span className="text-sm text-gray-500">
                  {Math.round((['units', 'basic', 'physical', 'dietary', 'meal', 'health', 'complete'].indexOf(currentStep) / 6) * 100)}% Complete
                </span>
              </div>
              <div className="w-full bg-gray-200 rounded-full h-2">
                <div
                  className="bg-blue-600 h-2 rounded-full transition-all"
                  style={{ width: `${(['units', 'basic', 'physical', 'dietary', 'meal', 'health', 'complete'].indexOf(currentStep) / 6) * 100}%` }}
                />
              </div>
            </div>

            {/* Step 1: Basic Information */}
            {currentStep === 'basic' && (
              <div className="space-y-4">
                <h2 className="text-2xl font-bold text-gray-900 mb-4">Basic Information</h2>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Full Name *</label>
                  <input
                    type="text"
                    required
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
                    placeholder="Enter your full name"
                  />
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Age *</label>
                    <input
                      type="number"
                      required
                      min="13"
                      max="100"
                      value={age}
                      onChange={(e) => setAge(e.target.value)}
                      className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
                      placeholder="Age"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Gender *</label>
                    <select
                      required
                      value={gender}
                      onChange={(e) => setGender(e.target.value as any)}
                      className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
                    >
                      <option value="male">Male</option>
                      <option value="female">Female</option>
                    </select>
                  </div>
                </div>
              </div>
            )}

            {/* Step 2: Physical Information */}
            {currentStep === 'physical' && (
              <div className="space-y-4">
                <h2 className="text-2xl font-bold text-gray-900 mb-4">Physical Information</h2>
                <p className="text-sm text-gray-600 mb-4">Using {useImperial ? 'Imperial' : 'Metric'} units</p>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      Height ({useImperial ? 'inches' : 'cm'}) *
                    </label>
                    <input
                      type="number"
                      required
                      min={useImperial ? "48" : "100"}
                      max={useImperial ? "96" : "250"}
                      step={useImperial ? "0.5" : "1"}
                      value={heightCm}
                      onChange={(e) => setHeightCm(e.target.value)}
                      className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
                      placeholder={useImperial ? "Height in inches" : "Height in cm"}
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">
                      Current Weight ({useImperial ? 'lbs' : 'kg'}) *
                    </label>
                    <input
                      type="number"
                      required
                      min={useImperial ? "66" : "30"}
                      max={useImperial ? "660" : "300"}
                      step="0.1"
                      value={currentWeight}
                      onChange={(e) => setCurrentWeight(e.target.value)}
                      className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
                      placeholder={useImperial ? "Weight in lbs" : "Weight in kg"}
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Goal Weight ({useImperial ? 'lbs' : 'kg'}) *
                  </label>
                  <input
                    type="number"
                    required
                    min={useImperial ? "66" : "30"}
                    max={useImperial ? "660" : "300"}
                    step="0.1"
                    value={goalWeight}
                    onChange={(e) => setGoalWeight(e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
                    placeholder={useImperial ? "Target weight in lbs" : "Target weight in kg"}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Activity Level *</label>
                  <select
                    required
                    value={activityLevel}
                    onChange={(e) => setActivityLevel(e.target.value as any)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
                  >
                    <option value="sedentary">Sedentary (little/no exercise)</option>
                    <option value="lightly active">Lightly Active (light exercise 1-3 days/week)</option>
                    <option value="moderately active">Moderately Active (moderate exercise 3-5 days/week)</option>
                    <option value="very active">Very Active (hard exercise 6-7 days/week)</option>
                    <option value="extremely active">Extremely Active (very hard exercise, physical job)</option>
                  </select>
                </div>
              </div>
            )}

            {/* Step 3: Dietary Preferences */}
            {currentStep === 'dietary' && (
              <div className="space-y-4">
                <h2 className="text-2xl font-bold text-gray-900 mb-4">Dietary Preferences</h2>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Dietary Preference *</label>
                  <select
                    required
                    value={dietaryPreference}
                    onChange={(e) => setDietaryPreference(e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
                  >
                    <option value="">Select dietary preference</option>
                    <option value="balanced">Balanced (Default)</option>
                    <option value="high_protein">High Protein</option>
                    <option value="moderate_low_carb">Moderate Low-Carb</option>
                    <option value="ketogenic">Ketogenic (Keto)</option>
                    <option value="very_low_carb">Very Low-Carb (Carnivore-leaning)</option>
                    <option value="carnivore">Carnivore</option>
                    <option value="mediterranean">Mediterranean</option>
                    <option value="plant_based">Plant-Based (Flexitarian)</option>
                    <option value="vegetarian">Vegetarian</option>
                    <option value="vegan">Vegan</option>
                    <option value="paleo">Paleo</option>
                    <option value="low_fat">Low Fat (Classic)</option>
                    <option value="zone_diet">Zone Diet</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">Primary Goal *</label>
                  <div className="space-y-2">
                    {[
                      { id: 'lose_weight', emoji: '🏃‍♀️', name: 'Lose Weight' },
                      { id: 'build_muscle', emoji: '💪', name: 'Build Muscle' },
                      { id: 'improve_fitness', emoji: '❤️', name: 'Improve Fitness' },
                      { id: 'stay_healthy', emoji: '🫀', name: 'Stay Healthy' },
                      { id: 'increase_energy', emoji: '⚡', name: 'Increase Energy' }
                    ].map((goal) => (
                      <label
                        key={goal.id}
                        className={`flex items-center p-3 border-2 rounded-lg cursor-pointer transition-colors ${
                          goalTrend === goal.id
                            ? 'border-blue-500 bg-blue-50'
                            : 'border-gray-200 hover:border-gray-300'
                        }`}
                      >
                        <input
                          type="radio"
                          name="goalTrend"
                          value={goal.id}
                          checked={goalTrend === goal.id}
                          onChange={(e) => setGoalTrend(e.target.value as any)}
                          className="w-4 h-4 text-blue-600"
                          required
                        />
                        <span className="text-xl ml-3 mr-2">{goal.emoji}</span>
                        <span className="text-sm font-medium text-gray-700">{goal.name}</span>
                      </label>
                    ))}
                  </div>
                </div>
              </div>
            )}

            {/* Step 4: Meal Preferences */}
            {currentStep === 'meal' && (
              <div className="space-y-4">
                <h2 className="text-2xl font-bold text-gray-900 mb-4">Meal Preferences</h2>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Meals per Day *</label>
                    <select
                      required
                      value={mealsPerDay}
                      onChange={(e) => setMealsPerDay(e.target.value)}
                      className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
                    >
                      <option value="2">2 meals</option>
                      <option value="3">3 meals</option>
                      <option value="4">4 meals</option>
                      <option value="5">5 meals</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Snacks per Day *</label>
                    <select
                      required
                      value={snacksPerDay}
                      onChange={(e) => setSnacksPerDay(e.target.value)}
                      className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
                    >
                      <option value="0">0 snacks</option>
                      <option value="1">1 snack</option>
                      <option value="2">2 snacks</option>
                      <option value="3">3 snacks</option>
                    </select>
                  </div>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">Preferred Cooking Methods *</label>
                  <div className="grid grid-cols-2 gap-2 max-h-64 overflow-y-auto border border-gray-200 rounded-lg p-2">
                    {[
                      { id: 'grill', name: 'Grill', desc: 'Grilling on outdoor or indoor grill' },
                      { id: 'bbq', name: 'BBQ', desc: 'Low and slow barbecue smoking' },
                      { id: 'sous_vide', name: 'Sous Vide', desc: 'Precision temperature cooking' },
                      { id: 'pressure_cook', name: 'Pressure Cook', desc: 'Pressure cooker/Instant Pot' },
                      { id: 'smoke', name: 'Smoke', desc: 'Hot or cold smoking' },
                      { id: 'slow_cook', name: 'Slow Cook', desc: 'Crock pot/slow cooker' },
                      { id: 'bake', name: 'Bake', desc: 'Oven baking' },
                      { id: 'roast', name: 'Roast', desc: 'Oven roasting' },
                      { id: 'saute', name: 'Sauté', desc: 'Pan sautéing' },
                      { id: 'stir_fry', name: 'Stir-Fry', desc: 'Wok stir-frying' },
                      { id: 'braise', name: 'Braise', desc: 'Braising in liquid' },
                      { id: 'steam', name: 'Steam', desc: 'Steaming' },
                      { id: 'poach', name: 'Poach', desc: 'Poaching in liquid' },
                      { id: 'pan_sear', name: 'Pan Sear', desc: 'Pan searing' },
                      { id: 'air_fry', name: 'Air Fry', desc: 'Air fryer cooking' },
                      { id: 'raw', name: 'Raw', desc: 'No cooking required' }
                    ].map((method) => (
                      <label key={method.id} className="flex items-start p-2 border rounded hover:bg-gray-50 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={preferredCookingMethods.includes(method.id)}
                          onChange={(e) => {
                            if (e.target.checked) {
                              setPreferredCookingMethods([...preferredCookingMethods, method.id])
                            } else {
                              setPreferredCookingMethods(preferredCookingMethods.filter(m => m !== method.id))
                            }
                          }}
                          className="w-4 h-4 text-primary-600 rounded mt-0.5"
                        />
                        <div className="ml-2 flex-1">
                          <span className="text-sm font-medium text-gray-700">{method.name}</span>
                          <span className="text-xs text-gray-500 block">{method.desc}</span>
                        </div>
                      </label>
                    ))}
                  </div>
                  {preferredCookingMethods.length === 0 && (
                    <p className="text-xs text-red-500 mt-1">Please select at least one cooking method</p>
                  )}
                </div>
              </div>
            )}

            {/* Step 5: Health Tracking */}
            {currentStep === 'health' && (
              <div className="space-y-4">
                <h2 className="text-2xl font-bold text-gray-900 mb-4">Health Tracking</h2>
                {gender === 'female' && (
                  <>
                    <div>
                      <label className="flex items-center p-3 border rounded hover:bg-gray-50 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={menstrualCycleEnabled}
                          onChange={(e) => setMenstrualCycleEnabled(e.target.checked)}
                          className="w-4 h-4 text-primary-600 rounded"
                        />
                        <span className="ml-2 text-sm font-medium text-gray-700">Track menstrual cycle</span>
                      </label>
                    </div>
                    {menstrualCycleEnabled && (
                      <div className="grid grid-cols-2 gap-4 pl-6 border-l-2 border-primary-200">
                        <div>
                          <label className="block text-sm font-medium text-gray-700 mb-1">Average Cycle Length (days)</label>
                          <input
                            type="number"
                            min="21"
                            max="35"
                            value={averageCycleLength}
                            onChange={(e) => setAverageCycleLength(e.target.value)}
                            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
                            placeholder="28"
                          />
                        </div>
                        <div>
                          <label className="block text-sm font-medium text-gray-700 mb-1">Average Period Length (days)</label>
                          <input
                            type="number"
                            min="3"
                            max="10"
                            value={averagePeriodLength}
                            onChange={(e) => setAveragePeriodLength(e.target.value)}
                            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
                            placeholder="5"
                          />
                        </div>
                      </div>
                    )}
                  </>
                )}
                {gender !== 'female' && (
                  <p className="text-sm text-gray-600">No additional health tracking required.</p>
                )}
              </div>
            )}

            {/* Navigation buttons */}
            <div className="flex gap-4 mt-6">
              {currentStep !== 'basic' && (
                <button
                  type="button"
                  onClick={handleBack}
                  className="flex-1 px-4 py-2 border border-gray-300 rounded-md hover:bg-gray-50"
                >
                  Back
                </button>
              )}
              {currentStep !== 'health' ? (
                <button
                  type="button"
                  onClick={handleNext}
                  className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
                >
                  Next
                </button>
              ) : (
                <button
                  type="button"
                  onClick={() => setCurrentStep('complete')}
                  className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
                >
                  Continue to Account Creation
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
    )
  }

  // Final step: Email and Password
  if (isSignUp && currentStep === 'complete') {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-primary-50 to-primary-100 p-4">
        <div className="max-w-md w-full">
          <div className="bg-white rounded-lg shadow-lg p-8">
            <div className="text-center mb-6">
              <h1 className="text-4xl font-bold text-gray-900 mb-2">🏋️ Coachie</h1>
              <h2 className="text-2xl font-semibold text-gray-900 mb-2">Create Your Account</h2>
              <p className="text-gray-600">Almost there! Just enter your email and password.</p>
            </div>

            <form onSubmit={handleEmailAuth} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Email *</label>
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
                  placeholder="Enter your email"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Password *</label>
                <input
                  type="password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
                  placeholder="Enter your password (min 6 characters)"
                  minLength={6}
                />
              </div>

              <button
                type="submit"
                disabled={loading}
                className="w-full bg-blue-600 text-white py-2 px-4 rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {loading ? 'Creating Account...' : 'Create Account'}
              </button>
            </form>

            <div className="mt-4 text-center">
              <button
                type="button"
                onClick={handleBack}
                className="text-primary-600 hover:text-primary-500 text-sm"
              >
                ← Back to Profile
              </button>
            </div>
          </div>
        </div>
      </div>
    )
  }

  // Sign in form (existing users)
  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-primary-50 to-primary-100 p-4">
      <div className="max-w-md w-full space-y-8">
        <div className="text-center">
          <h1 className="text-4xl font-bold text-gray-900 mb-2">🏋️ Coachie</h1>
          <p className="text-gray-600">Your AI Fitness Coach</p>
        </div>

        <div className="bg-white rounded-lg shadow-lg p-8">
          <div className="text-center mb-6">
            <h2 className="text-2xl font-semibold text-gray-900">Welcome Back</h2>
            <p className="text-gray-600 mt-2">Continue your fitness journey</p>
          </div>

          <button
            onClick={handleGoogleAuth}
            disabled={loading}
            className="w-full flex items-center justify-center gap-3 bg-white border border-gray-300 rounded-lg px-4 py-3 text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:opacity-50 disabled:cursor-not-allowed mb-4"
          >
            <svg className="w-5 h-5" viewBox="0 0 24 24">
              <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
              <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
              <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
              <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
            </svg>
            Continue with Google
          </button>

          <div className="relative mb-6">
            <div className="absolute inset-0 flex items-center">
              <div className="w-full border-t border-gray-300" />
            </div>
            <div className="relative flex justify-center text-sm">
              <span className="px-2 bg-white text-gray-500">Or continue with email</span>
            </div>
          </div>

          <form onSubmit={handleEmailAuth} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                placeholder="Enter your email"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Password</label>
              <input
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                placeholder="Enter your password"
                minLength={6}
              />
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full bg-primary-600 text-white py-2 px-4 rounded-md hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-primary-500 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? 'Please wait...' : 'Sign In'}
            </button>
          </form>

          <div className="mt-6 text-center">
            <button
              onClick={() => {
                setIsSignUp(true)
                setCurrentStep('units')
              }}
              className="text-primary-600 hover:text-primary-500 text-sm"
            >
              Don't have an account? Sign up
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}