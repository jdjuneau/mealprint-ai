'use client'

import { useAuth } from '../../lib/contexts/AuthContext'
import { useRouter } from 'next/navigation'
import { useState, useEffect } from 'react'
import LoadingScreen from '../../components/LoadingScreen'
import toast from 'react-hot-toast'
import MealInspirationService, { IngredientCategoryGroup, IngredientOption } from '../../lib/services/mealInspirationService'
import { FirebaseService } from '../../lib/services/firebase'
import type { DailyLog } from '../../types'

interface MealRecommendation {
  recipeTitle: string
  summary: string
  servings: number
  ingredients: string[]
  instructions: string[]
  macrosPerServing: {
    calories: number
    protein: number
    carbs: number
    fat: number
    sugar: number
    addedSugar: number
  }
}

export default function MealRecommendationPage() {
  const { user, userProfile } = useAuth()
  const router = useRouter()
  const [loading, setLoading] = useState(true)
  const [generating, setGenerating] = useState(false)
  const [ingredientGroups, setIngredientGroups] = useState<IngredientCategoryGroup[]>([])
  const [selectedIngredients, setSelectedIngredients] = useState<string[]>([])
  const [customIngredient, setCustomIngredient] = useState('')
  const [expandedCategories, setExpandedCategories] = useState<Set<string>>(new Set())
  const [mealType, setMealType] = useState<string | null>(null)
  const [cookingMethod, setCookingMethod] = useState<string | null>(null)
  const [recommendation, setRecommendation] = useState<MealRecommendation | null>(null)
  const [currentMacros, setCurrentMacros] = useState({ calories: 0, protein: 0, carbs: 0, fat: 0 })
  const [macroTargets, setMacroTargets] = useState({ calories: 0, protein: 0, carbs: 0, fat: 0 })
  const [remainingMacros, setRemainingMacros] = useState({ calories: 0, protein: 0, carbs: 0, fat: 0 })
  const [useImperial, setUseImperial] = useState(true)
  const [remainingCalls, setRemainingCalls] = useState<number | null>(null)
  const [isPro, setIsPro] = useState(false)

  useEffect(() => {
    if (!user) {
      router.push('/auth')
    } else {
      loadData()
    }
  }, [user, router])

  const loadData = async () => {
    if (!user || !userProfile) return

    try {
      // Load ingredient options
      const service = MealInspirationService.getInstance()
      setIngredientGroups(service.getIngredientOptions())

      // Load unit preference
      const goals = await FirebaseService.getUserGoals(user.uid)
      setUseImperial(goals?.useImperial ?? true)

      // Load subscription status
      const SubscriptionService = (await import('../../lib/services/subscriptionService')).default
      const subscriptionService = SubscriptionService.getInstance()
      const proStatus = await subscriptionService.isPro(user.uid)
      setIsPro(proStatus)
      
      if (!proStatus) {
        const remaining = await subscriptionService.getRemainingAICalls(user.uid, 'meal_recommendation')
        setRemainingCalls(remaining)
      }

      // Load today's macros
      await loadTodayMacros()

      setLoading(false)
    } catch (error) {
      console.error('Error loading data:', error)
      setLoading(false)
    }
  }

  const loadTodayMacros = async () => {
    if (!user || !userProfile) return

    try {
      const today = new Date().toISOString().split('T')[0]
      const log = await FirebaseService.getDailyLog(user.uid, today)
      
      if (log) {
        const meals = log.logs?.filter((h: any) => h.type === 'meal') || []
        const macros = {
          calories: meals.reduce((sum: number, m: any) => sum + (m.calories || 0), 0),
          protein: meals.reduce((sum: number, m: any) => sum + (m.protein || 0), 0),
          carbs: meals.reduce((sum: number, m: any) => sum + (m.carbs || 0), 0),
          fat: meals.reduce((sum: number, m: any) => sum + (m.fat || 0), 0),
        }
        setCurrentMacros(macros)

        // Calculate macro targets (simplified - matches Android logic)
        const currentWeight = userProfile?.currentWeight || 75
        const goalWeight = userProfile?.goalWeight || currentWeight
        const activityLevel = userProfile?.activityLevel || 'moderately active'
        
        const bmr = (userProfile?.gender === 'female' ? 655 : 66) + 
                    (9.6 * currentWeight) + 
                    (1.8 * (userProfile?.heightCm || 175)) - 
                    (4.7 * (userProfile?.age || 30))
        
        const activityMultipliers: Record<string, number> = {
          'sedentary': 1.2,
          'lightly active': 1.375,
          'moderately active': 1.55,
          'very active': 1.725,
          'extremely active': 1.9
        }
        
        let calorieGoal = Math.round(bmr * (activityMultipliers[activityLevel] || 1.55))
        
        if (goalWeight < currentWeight - 0.1) {
          calorieGoal -= 500
        } else if (goalWeight > currentWeight + 0.1) {
          calorieGoal += 500
        }

        // Simplified macro targets (matching Android)
        const dietaryPreference = userProfile.dietaryPreference || 'balanced'
        const proteinGrams = Math.round(calorieGoal * 0.25 / 4)
        const carbsGrams = Math.round(calorieGoal * 0.50 / 4)
        const fatGrams = Math.round(calorieGoal * 0.25 / 9)

        const targets = { calories: calorieGoal, protein: proteinGrams, carbs: carbsGrams, fat: fatGrams }
        setMacroTargets(targets)
        setRemainingMacros({
          calories: Math.max(0, calorieGoal - macros.calories),
          protein: Math.max(0, proteinGrams - macros.protein),
          carbs: Math.max(0, carbsGrams - macros.carbs),
          fat: Math.max(0, fatGrams - macros.fat),
        })
      }
    } catch (error) {
      console.error('Error loading macros:', error)
    }
  }

  const toggleIngredient = (option: IngredientOption) => {
    const name = option.name.trim()
    setSelectedIngredients(prev => {
      if (prev.includes(name)) {
        return prev.filter(i => i !== name)
      } else {
        return [...prev, name].sort()
      }
    })
  }

  const addCustomIngredient = () => {
    const trimmed = customIngredient.trim()
    if (trimmed && !selectedIngredients.includes(trimmed)) {
      const formatted = trimmed.charAt(0).toUpperCase() + trimmed.slice(1)
      setSelectedIngredients(prev => [...prev, formatted].sort())
      setCustomIngredient('')
    }
  }

  const removeIngredient = (name: string) => {
    setSelectedIngredients(prev => prev.filter(i => i !== name))
  }

  const toggleCategory = (title: string) => {
    setExpandedCategories(prev => {
      const newSet = new Set(prev)
      if (newSet.has(title)) {
        newSet.delete(title)
      } else {
        newSet.add(title)
      }
      return newSet
    })
  }

  const generateRecommendation = async () => {
    if (!user || !userProfile) return

    if (selectedIngredients.length === 0) {
      toast.error('Please select at least one ingredient')
      return
    }

    setGenerating(true)
    try {
      // Check subscription and rate limits
      const SubscriptionService = (await import('../../lib/services/subscriptionService')).default
      const subscriptionService = SubscriptionService.getInstance()
      
      if (!isPro) {
        const canUse = await subscriptionService.canUseAIFeature(user.uid, 'meal_recommendation')
        if (!canUse) {
          const remaining = await subscriptionService.getRemainingAICalls(user.uid, 'meal_recommendation')
          if (remaining === 0) {
            toast.error('Daily limit reached (1 per day). Upgrade to Pro for unlimited recommendations!')
          } else {
            toast.error(`You have ${remaining} recommendation${remaining === 1 ? '' : 's'} remaining today.`)
          }
          setGenerating(false)
          return
        }
      }

      // Call Firebase Cloud Function
      const { functions } = await import('../../lib/firebase')
      const { httpsCallable } = await import('firebase/functions')
      
      const generateMealRec = httpsCallable(functions, 'generateMealRecommendation')
      const result = await generateMealRec({
        userId: user.uid,
        platform: 'web',
        selectedIngredients: selectedIngredients,
        mealType: mealType,
        cookingMethod: cookingMethod,
        useImperial: useImperial,
      })

      const data = result.data as any
      if (data && data.recommendation) {
        // Ensure instructions is an array
        const rec = data.recommendation
        if (typeof rec.instructions === 'string') {
          rec.instructions = rec.instructions.split('\n').filter((s: string) => s.trim())
        }
        setRecommendation(rec)
        toast.success('Recommendation generated!')
        
        // Record usage for free tier ONLY on success
        if (!isPro) {
          await subscriptionService.recordAIFeatureUsage(user.uid, 'meal_recommendation')
          const remaining = await subscriptionService.getRemainingAICalls(user.uid, 'meal_recommendation')
          setRemainingCalls(remaining)
        }
      } else {
        const errorMsg = data?.error || 'Failed to generate recommendation. The service may be unavailable.'
        toast.error(errorMsg)
        console.error('Empty or invalid response from Cloud Function:', data)
      }
    } catch (error: any) {
      console.error('Error generating recommendation:', error)
      const errorMessage = error.message || error.code || 'Failed to generate recommendation'
      
      if (error.code === 'functions/not-found' || errorMessage.includes('not found')) {
        toast.error('Meal recommendation service is not available. Please try again later.')
      } else if (errorMessage.includes('empty response')) {
        toast.error('AI service returned an empty response. This does not count toward your daily limit. Please try again.')
      } else {
        toast.error(`Failed to generate recommendation: ${errorMessage}`)
      }
    } finally {
      setGenerating(false)
    }
  }

  if (loading || !user || !userProfile) {
    return <LoadingScreen />
  }

  const gradientClass = userProfile?.gender === 'male'
    ? 'bg-coachie-gradient-male'
    : userProfile?.gender === 'female'
    ? 'bg-coachie-gradient-female'
    : 'bg-coachie-gradient'

  return (
    <div className={`min-h-screen ${gradientClass}`}>
      <div className="max-w-4xl mx-auto py-6 px-4">
        {/* Header */}
        <div className="flex items-center justify-between mb-6">
          <button
            onClick={() => router.back()}
            className="text-white hover:text-gray-200"
          >
            ← Back
          </button>
          <h1 className="text-2xl font-bold text-white">AI Meal Inspiration</h1>
          <div className="w-12"></div>
        </div>

        {/* Macro Summary Card */}
        {macroTargets.calories > 0 && (
          <div className="bg-white/90 rounded-lg p-4 mb-6 shadow-lg">
            <h2 className="font-semibold text-gray-900 mb-3">Today's Nutrition Snapshot</h2>
            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-gray-700">Calories</span>
                <span className="text-gray-900">{currentMacros.calories} / {macroTargets.calories} kcal</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-700">Protein</span>
                <span className="text-gray-900">{currentMacros.protein} / {macroTargets.protein}g</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-700">Carbs</span>
                <span className="text-gray-900">{currentMacros.carbs} / {macroTargets.carbs}g</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-700">Fat</span>
                <span className="text-gray-900">{currentMacros.fat} / {macroTargets.fat}g</span>
              </div>
            </div>
          </div>
        )}

        {/* Meal Type Selection */}
        <div className="bg-white/90 rounded-lg p-4 mb-6 shadow-lg">
          <h2 className="font-semibold text-gray-900 mb-3">Meal Type</h2>
          <div className="flex flex-wrap gap-2">
            {['breakfast', 'brunch', 'lunch', 'dinner', 'dessert'].map(type => (
              <button
                key={type}
                onClick={() => setMealType(mealType === type ? null : type)}
                className={`px-4 py-2 rounded-lg text-sm ${
                  mealType === type
                    ? 'bg-orange-600 text-white'
                    : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                }`}
              >
                {type.charAt(0).toUpperCase() + type.slice(1)}
              </button>
            ))}
          </div>
        </div>

        {/* Ingredient Selection */}
        <div className="bg-white/90 rounded-lg p-4 mb-6 shadow-lg">
          <h2 className="font-semibold text-gray-900 mb-2">What ingredients do you have right now?</h2>
          <p className="text-sm text-gray-600 mb-4">
            Select everything you can use. Add specifics (like "roasted veggies" or "leftover steak") in the custom box.
          </p>

          {/* Ingredient Categories */}
          <div className="space-y-4">
            {ingredientGroups.map(group => (
              <div key={group.title} className="border border-gray-200 rounded-lg">
                <button
                  onClick={() => toggleCategory(group.title)}
                  className="w-full flex items-center justify-between p-3 hover:bg-gray-50"
                >
                  <span className="font-medium text-gray-900">{group.title}</span>
                  <span className="text-gray-500">
                    {expandedCategories.has(group.title) ? '−' : '+'}
                  </span>
                </button>
                {expandedCategories.has(group.title) && (
                  <div className="p-3 pt-0 grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-2">
                    {group.options.map(option => (
                      <button
                        key={option.name}
                        onClick={() => toggleIngredient(option)}
                        className={`px-3 py-1.5 rounded text-sm text-left ${
                          selectedIngredients.includes(option.name)
                            ? 'bg-orange-600 text-white'
                            : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                        }`}
                      >
                        {option.name}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </div>

          {/* Custom Ingredient Input */}
          <div className="mt-4 flex gap-2">
            <input
              type="text"
              value={customIngredient}
              onChange={(e) => setCustomIngredient(e.target.value)}
              onKeyPress={(e) => e.key === 'Enter' && addCustomIngredient()}
              placeholder="Add custom ingredient (e.g., leftover steak)"
              className="flex-1 px-3 py-2 border border-gray-300 rounded-lg"
            />
            <button
              onClick={addCustomIngredient}
              className="px-4 py-2 bg-orange-600 text-white rounded-lg hover:bg-orange-700"
            >
              Add
            </button>
          </div>

          {/* Selected Ingredients */}
          {selectedIngredients.length > 0 && (
            <div className="mt-4">
              <h3 className="text-sm font-medium text-gray-700 mb-2">Selected Ingredients:</h3>
              <div className="flex flex-wrap gap-2">
                {selectedIngredients.map(ingredient => (
                  <span
                    key={ingredient}
                    className="inline-flex items-center gap-1 px-3 py-1 bg-orange-100 text-orange-800 rounded-full text-sm"
                  >
                    {ingredient}
                    <button
                      onClick={() => removeIngredient(ingredient)}
                      className="hover:text-orange-900"
                    >
                      ×
                    </button>
                  </span>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Cooking Method Selection */}
        <div className="bg-white/90 rounded-lg p-4 mb-6 shadow-lg">
          <h2 className="font-semibold text-gray-900 mb-3">Cooking Method (Optional)</h2>
          <div className="flex flex-wrap gap-2">
            {['grill', 'bake', 'roast', 'saute', 'stir_fry', 'slow_cook', 'pressure_cook', 'air_fry'].map(method => (
              <button
                key={method}
                onClick={() => setCookingMethod(cookingMethod === method ? null : method)}
                className={`px-4 py-2 rounded-lg text-sm ${
                  cookingMethod === method
                    ? 'bg-orange-600 text-white'
                    : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                }`}
              >
                {method.replace('_', ' ').replace(/\b\w/g, l => l.toUpperCase())}
              </button>
            ))}
          </div>
        </div>

        {/* Generate Button */}
        <button
          onClick={generateRecommendation}
          disabled={selectedIngredients.length === 0 || generating}
          className="w-full py-3 bg-white/90 text-gray-900 rounded-lg font-semibold hover:bg-white disabled:opacity-50 disabled:cursor-not-allowed shadow-lg mb-4"
        >
          {generating ? 'Asking Coachie…' : 'Ask Coachie for a Recipe'}
        </button>

        {/* Remaining Calls */}
        {!isPro && remainingCalls !== null && (
          <p className="text-center text-white text-sm mb-4">
            {remainingCalls > 0
              ? `${remainingCalls} meal recommendation${remainingCalls === 1 ? '' : 's'} remaining today`
              : 'No meal recommendations remaining today'}
          </p>
        )}

        {/* Recommendation Result */}
        {recommendation && (
          <div className="bg-white/90 rounded-lg p-6 shadow-lg">
            <h2 className="text-2xl font-bold text-gray-900 mb-2">{recommendation.recipeTitle}</h2>
            <p className="text-gray-600 mb-4">{recommendation.summary}</p>
            
            <div className="mb-4">
              <h3 className="font-semibold text-gray-900 mb-2">Ingredients ({recommendation.servings} servings):</h3>
              <ul className="list-disc list-inside space-y-1 text-gray-700">
                {recommendation.ingredients.map((ing, idx) => (
                  <li key={idx}>{ing}</li>
                ))}
              </ul>
            </div>

            <div className="mb-4">
              <h3 className="font-semibold text-gray-900 mb-2">Instructions:</h3>
              <ol className="list-decimal list-inside space-y-2 text-gray-700">
                {recommendation.instructions.map((step, idx) => (
                  <li key={idx}>{step}</li>
                ))}
              </ol>
            </div>

            <div className="mb-4 p-3 bg-gray-50 rounded">
              <h3 className="font-semibold text-gray-900 mb-2">Nutrition (per serving):</h3>
              <div className="grid grid-cols-2 gap-2 text-sm">
                <div>Calories: {recommendation.macrosPerServing.calories}</div>
                <div>Protein: {recommendation.macrosPerServing.protein}g</div>
                <div>Carbs: {recommendation.macrosPerServing.carbs}g</div>
                <div>Fat: {recommendation.macrosPerServing.fat}g</div>
              </div>
            </div>

            <div className="flex gap-4">
              <button
                onClick={() => {
                  setRecommendation(null)
                  setSelectedIngredients([])
                }}
                className="flex-1 px-4 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50"
              >
                Get Another
              </button>
              <button
                onClick={() => router.push('/meal-log')}
                className="flex-1 px-4 py-2 bg-orange-600 text-white rounded-lg hover:bg-orange-700"
              >
                Log This Meal
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
