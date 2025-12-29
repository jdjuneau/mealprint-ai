'use client'

import { useState, useEffect } from 'react'
import { useAuth } from '../lib/contexts/AuthContext'
import { FirebaseService } from '../lib/services/firebase'
import { functions } from '../lib/firebase'
import { httpsCallable } from 'firebase/functions'
import { doc, getDoc, updateDoc, onSnapshot, Unsubscribe } from 'firebase/firestore'
import { db } from '../lib/firebase'
import CoachieCard from './ui/CoachieCard'
import CoachieButton from './ui/CoachieButton'

interface ShoppingListItem {
  item: string
  quantity: string
  bought?: boolean
  note?: string
}

interface Meal {
  name: string
  calories: number
  protein: number
  carbs: number
  fat: number
  ingredients: string[]
}

interface DayMeals {
  day: string
  breakfast?: Meal
  lunch?: Meal
  dinner?: Meal
  meal4?: Meal
  meal5?: Meal
  snacks?: Meal[]
}

interface WeeklyBlueprint {
  shoppingList: Record<string, ShoppingListItem[]>
  meals: DayMeals[]
  dailyCalories?: number
  estimatedCost?: number
}

function getWeekId(): string {
  const date = new Date()
  const dayOfWeek = date.getDay() // 0 = Sunday, 1 = Monday, etc.
  const daysToSubtract = dayOfWeek === 0 ? 0 : dayOfWeek - 1 // Monday = 0
  date.setDate(date.getDate() - daysToSubtract)
  return date.toLocaleDateString('en-CA') // YYYY-MM-DD in local timezone
}

export default function WeeklyBlueprint() {
  const { user, userProfile } = useAuth()
  const [blueprint, setBlueprint] = useState<WeeklyBlueprint | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [regenerating, setRegenerating] = useState(false)
  const [expandedCategories, setExpandedCategories] = useState<Set<string>>(new Set())
  const [expandedDays, setExpandedDays] = useState<Set<string>>(new Set())
  const [editingItem, setEditingItem] = useState<{ category: string; index: number; field: 'quantity' | 'note' } | null>(null)
  const [editValue, setEditValue] = useState('')
  const [selectedServings, setSelectedServings] = useState(1)
  const [useImperial, setUseImperial] = useState<boolean>(true)
  const [isPro, setIsPro] = useState<boolean>(false)
  const [checkingPro, setCheckingPro] = useState(true)
  const weekStarting = getWeekId()

  useEffect(() => {
    if (!user) return
    // Update useImperial when userProfile changes
    if (userProfile) {
      setUseImperial(userProfile.useImperial !== false) // Default to true (imperial)
    }

    // Load Pro status
    const checkProStatus = async () => {
      try {
        const SubscriptionService = (await import('../lib/services/subscriptionService')).default
        const subscriptionService = SubscriptionService.getInstance()
        const proStatus = await subscriptionService.isPro(user.uid)
        setIsPro(proStatus)
      } catch (error) {
        console.error('Error checking Pro status:', error)
        setIsPro(false)
      } finally {
        setCheckingPro(false)
      }
    }
    checkProStatus()

    loadBlueprint()
    loadPreferences()

    // Listen for real-time updates
    const unsubscribe = onSnapshot(
      doc(db, 'users', user.uid, 'weeklyBlueprints', weekStarting),
      (snapshot) => {
        if (snapshot.exists()) {
          setBlueprint(snapshot.data() as WeeklyBlueprint)
          setIsLoading(false)
          
          // Expand all categories by default
          const shoppingList = snapshot.data()?.shoppingList as Record<string, ShoppingListItem[]>
          if (shoppingList) {
            setExpandedCategories(new Set(Object.keys(shoppingList)))
          }
        }
      },
      (error) => {
        console.error('Error listening to blueprint:', error)
        setIsLoading(false)
      }
    )

    return () => unsubscribe()
  }, [user, weekStarting])

  const loadBlueprint = async () => {
    if (!user) return

    try {
      setIsLoading(true)
      const data = await FirebaseService.getWeeklyBlueprint(user.uid, weekStarting)
      
      if (data) {
        setBlueprint(data)
        
        // Expand all categories by default
        if (data.shoppingList) {
          setExpandedCategories(new Set(Object.keys(data.shoppingList)))
        }
        
        // Load servings from plan
        const planDoc = await getDoc(doc(db, 'users', user.uid, 'weeklyPlans', weekStarting))
        if (planDoc.exists()) {
          const servings = planDoc.data()?.servings || 1
          setSelectedServings(servings)
        }
      }
    } catch (error) {
      console.error('Error loading blueprint:', error)
    } finally {
      setIsLoading(false)
    }
  }

  const loadPreferences = async () => {
    if (!user) return

    try {
      // Read from user document (matching Android structure)
      // Android stores useImperial in users/{userId} document, not in a subcollection
      const { FirebaseService } = await import('../lib/services/firebase')
      const goals = await FirebaseService.getUserGoals(user.uid)
      if (goals) {
        setUseImperial(goals.useImperial !== false) // Default to true (imperial)
      } else {
        // Fallback: try to read from userProfile if available
        const profile = await FirebaseService.getUserProfile(user.uid)
        if (profile) {
          setUseImperial(profile.useImperial !== false) // Default to true (imperial)
        }
      }
    } catch (error) {
      console.error('Error loading preferences:', error)
      // Default to imperial if error
      setUseImperial(true)
    }
  }

  const generateBlueprint = async () => {
    if (!user) return

    try {
      // CRITICAL: Check Pro subscription before generating (blueprint is Pro-only)
      const SubscriptionService = (await import('../lib/services/subscriptionService')).default
      const subscriptionService = SubscriptionService.getInstance()
      const isPro = await subscriptionService.isPro(user.uid)
      
      if (!isPro) {
        // Show upgrade prompt instead of error
        if (confirm('Weekly Blueprint is a Pro feature. Upgrade to Pro to unlock unlimited weekly meal plans!\n\nWould you like to go to the subscription page?')) {
          window.location.href = '/subscription'
        }
        return
      }

      setRegenerating(true)

      const weekStarting = getWeekId()

      // Get the timestamp of the existing blueprint (if any) before generating
      const existingBlueprintDoc = await getDoc(doc(db, 'users', user.uid, 'weeklyBlueprints', weekStarting))
      const existingGeneratedAt = existingBlueprintDoc.exists() ? existingBlueprintDoc.data()?.generatedAt : null

      console.log('Starting blueprint generation...', { weekStarting, existingGeneratedAt })

      // Try generateWeeklyBlueprint first, fallback to generateWeeklyShoppingList
      let generateFunction
      try {
        generateFunction = httpsCallable(functions, 'generateWeeklyBlueprint')
      } catch (e) {
        generateFunction = httpsCallable(functions, 'generateWeeklyShoppingList')
      }

      // CRITICAL: Handle timeout - function can take 2-4 minutes but client times out
      // Call the function but don't wait for response - poll Firestore instead
      generateFunction().catch(error => {
        // Function call failed/timeout - this is expected, we'll poll for results
        console.log('Function call completed/failed (expected):', error?.message)
      })

      // Poll Firestore for the result (up to 120 seconds)
      const maxPollTime = 120000 // 2 minutes
      const pollInterval = 6000 // 6 seconds
      const startTime = Date.now()

      console.log('Starting to poll for blueprint...')

      while (Date.now() - startTime < maxPollTime) {
        try {
          // Check if blueprint exists and is newer than the existing one
          const blueprintDoc = await getDoc(doc(db, 'users', user.uid, 'weeklyBlueprints', weekStarting))

          if (blueprintDoc.exists()) {
            const blueprintData = blueprintDoc.data()
            const newGeneratedAt = blueprintData?.generatedAt

            // Check if this is a NEW blueprint (generated after we started)
            if (!existingGeneratedAt || !newGeneratedAt || newGeneratedAt.toMillis() > existingGeneratedAt.toMillis()) {
              console.log('✅ NEW blueprint found in Firestore!')
              // Reload blueprint
              await loadBlueprint()
              return
            } else {
              console.log('⏳ Old blueprint still exists, waiting for new one...')
            }
          } else {
            console.log('⏳ Blueprint not ready yet...')
          }

          // Wait before next poll
          await new Promise(resolve => setTimeout(resolve, pollInterval))
        } catch (pollError) {
          console.error('Error polling for blueprint:', pollError)
          await new Promise(resolve => setTimeout(resolve, pollInterval))
        }
      }

      // Timeout reached
      console.error('❌ Blueprint generation timeout - no new blueprint found')
      alert('Blueprint generation is taking longer than expected. Please check back in a few minutes.')
    } catch (error) {
      console.error('Error generating blueprint:', error)
      alert('Failed to generate blueprint. Please try again.')
    } finally {
      setRegenerating(false)
    }
  }

  const toggleCategory = (category: string) => {
    setExpandedCategories(prev => {
      const next = new Set(prev)
      if (next.has(category)) {
        next.delete(category)
      } else {
        next.add(category)
      }
      return next
    })
  }

  const toggleDay = (day: string) => {
    setExpandedDays(prev => {
      const next = new Set(prev)
      if (next.has(day)) {
        next.delete(day)
      } else {
        next.add(day)
      }
      return next
    })
  }

  const toggleBought = async (category: string, index: number) => {
    if (!user || !blueprint) return

    const categoryItems = blueprint.shoppingList[category]
    if (!categoryItems) return

    const newBoughtState = !categoryItems[index].bought
    
    // Update local state immediately
    const updatedBlueprint = {
      ...blueprint,
      shoppingList: {
        ...blueprint.shoppingList,
        [category]: categoryItems.map((item, i) => 
          i === index ? { ...item, bought: newBoughtState } : item
        )
      }
    }
    setBlueprint(updatedBlueprint)

    // Save to Firestore
    try {
      await FirebaseService.updateWeeklyBlueprint(user.uid, weekStarting, {
        [`shoppingList.${category}.${index}.bought`]: newBoughtState
      })
    } catch (error) {
      console.error('Error updating bought status:', error)
      // Revert on error
      setBlueprint(blueprint)
    }
  }

  const startEditing = (category: string, index: number, field: 'quantity' | 'note') => {
    if (!blueprint) return
    
    const categoryItems = blueprint.shoppingList[category]
    if (!categoryItems) return
    
    const item = categoryItems[index]
    setEditingItem({ category, index, field })
    setEditValue(field === 'quantity' ? item.quantity : (item.note || ''))
  }

  const saveEdit = async () => {
    if (!user || !blueprint || !editingItem) return

    const { category, index, field } = editingItem
    const categoryItems = blueprint.shoppingList[category]
    if (!categoryItems) return

    // Update local state
    const updatedBlueprint = {
      ...blueprint,
      shoppingList: {
        ...blueprint.shoppingList,
        [category]: categoryItems.map((item, i) => 
          i === index ? { ...item, [field]: editValue } : item
        )
      }
    }
    setBlueprint(updatedBlueprint)

    // Save to Firestore
    try {
      await FirebaseService.updateWeeklyBlueprint(user.uid, weekStarting, {
        [`shoppingList.${category}.${index}.${field}`]: editValue
      })
      setEditingItem(null)
      setEditValue('')
    } catch (error) {
      console.error('Error updating item:', error)
      // Revert on error
      setBlueprint(blueprint)
    }
  }

  const cancelEdit = () => {
    setEditingItem(null)
    setEditValue('')
  }

  // Calculate summary stats
  const itemCount = blueprint?.shoppingList 
    ? Object.values(blueprint.shoppingList).reduce((sum, items) => sum + items.length, 0)
    : 0
  
  const boughtCount = blueprint?.shoppingList
    ? Object.values(blueprint.shoppingList).reduce((sum, items) => 
        sum + items.filter(item => item.bought).length, 0
      )
    : 0

  // Calculate scaled quantities based on servings
  const scaleQuantity = (quantity: string, originalServings: number, newServings: number): string => {
    // Extract number and unit
    const match = quantity.match(/^([\d.]+)\s*(.*)$/)
    if (!match) return quantity
    
    const amount = parseFloat(match[1])
    const unit = match[2]
    const scaledAmount = (amount / originalServings) * newServings
    
    // Format based on unit
    if (unit.includes('cup') || unit.includes('tbsp') || unit.includes('tsp')) {
      return `${scaledAmount.toFixed(1)} ${unit}`
    }
    if (unit.includes('oz') || unit.includes('lbs')) {
      return `${scaledAmount.toFixed(1)} ${unit}`
    }
    return `${Math.round(scaledAmount)} ${unit}`
  }

  const daysOfWeek = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-center">
          <div className="text-4xl mb-4">⏳</div>
          <p className="text-gray-600">Loading your blueprint...</p>
        </div>
      </div>
    )
  }

  if (!blueprint) {
    return (
      <div className="max-w-4xl mx-auto space-y-6">
        <div className="text-center py-12">
          <div className="text-6xl mb-4">📋</div>
          <h2 className="text-2xl font-bold text-gray-900 mb-2">No Blueprint Yet</h2>
          <p className="text-gray-600 mb-6">Generate your personalized weekly meal plan and shopping list</p>
          
          {/* Pro Feature Indicator */}
          {!checkingPro && !isPro && (
            <div className="mb-4 p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
              <div className="flex items-center justify-center gap-2 text-yellow-800">
                <span className="text-lg">🔒</span>
                <span className="font-semibold">Pro Feature</span>
              </div>
              <p className="text-sm text-yellow-700 mt-1">
                Upgrade to Pro to unlock unlimited weekly meal plans
              </p>
            </div>
          )}
          
          <CoachieButton 
            onClick={generateBlueprint} 
            disabled={regenerating || checkingPro}
            className={!isPro && !checkingPro ? 'opacity-75' : ''}
          >
            {regenerating ? 'Generating...' : 'Generate My Blueprint'}
          </CoachieButton>
          
          {!isPro && !checkingPro && (
            <button
              onClick={() => window.location.href = '/subscription'}
              className="mt-3 text-sm text-blue-600 hover:text-blue-800 underline"
            >
              Upgrade to Pro →
            </button>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Header Actions */}
      <div className="flex justify-between items-center">
        <div className="flex items-center gap-3">
        <h1 className="text-3xl font-bold text-gray-900">Your Weekly Blueprint</h1>
          {!isPro && (
            <span className="px-2 py-1 bg-yellow-100 text-yellow-800 text-xs font-semibold rounded-full flex items-center gap-1">
              <span>🔒</span>
              <span>Pro</span>
            </span>
          )}
        </div>
        <div className="flex gap-2">
          <CoachieButton variant="outline" onClick={generateBlueprint} disabled={regenerating || !isPro}>
            {regenerating ? 'Regenerating...' : '🔄 Regenerate'}
          </CoachieButton>
        </div>
      </div>

      {/* Summary Card */}
      <CoachieCard>
        <div className="p-6">
          <h2 className="text-xl font-bold mb-4">Shopping List Summary</h2>
          <div className="grid grid-cols-3 gap-4 text-center">
            <div>
              <div className="text-3xl font-bold text-primary-600">{itemCount}</div>
              <div className="text-sm text-gray-600">Items</div>
            </div>
            <div>
              <div className="text-3xl font-bold text-primary-600">{boughtCount}</div>
              <div className="text-sm text-gray-600">Bought</div>
            </div>
            {blueprint.dailyCalories && (
              <div>
                <div className="text-3xl font-bold text-primary-600">{blueprint.dailyCalories}</div>
                <div className="text-sm text-gray-600">kcal/day</div>
              </div>
            )}
          </div>
        </div>
      </CoachieCard>

      {/* Serving Size Selector */}
      <CoachieCard>
        <div className="p-6">
          <h3 className="text-lg font-semibold mb-2">Serving Size</h3>
          <p className="text-sm text-gray-600 mb-4">Adjust recipes and shopping list for your household size</p>
          <div className="flex items-center gap-4">
            <label className="text-sm font-medium">Servings:</label>
            <select
              value={selectedServings}
              onChange={(e) => setSelectedServings(parseInt(e.target.value))}
              className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
            >
              {[1, 2, 3, 4, 5, 6, 7, 8].map(num => (
                <option key={num} value={num}>{num}</option>
              ))}
            </select>
          </div>
        </div>
      </CoachieCard>

      {/* Shopping List */}
      <CoachieCard>
        <div className="p-6">
          <h2 className="text-xl font-bold mb-4">🛒 Shopping List</h2>
          <div className="space-y-4">
            {Object.entries(blueprint.shoppingList || {}).map(([category, items]) => (
              <div key={category}>
                <button
                  onClick={() => toggleCategory(category)}
                  className="flex items-center justify-between w-full text-left font-semibold text-lg mb-2 p-2 hover:bg-gray-50 rounded"
                >
                  <span>{category}</span>
                  <span>{expandedCategories.has(category) ? '▼' : '▶'}</span>
                </button>
                {expandedCategories.has(category) && (
                  <ul className="space-y-2 ml-4">
                    {items.map((item, index) => (
                      <li key={index} className="flex items-center gap-2 p-2 hover:bg-gray-50 rounded">
                        <input
                          type="checkbox"
                          checked={item.bought || false}
                          onChange={() => toggleBought(category, index)}
                          className="w-5 h-5"
                        />
                        {editingItem?.category === category && editingItem?.index === index ? (
                          <div className="flex-1 flex gap-2">
                            <input
                              type="text"
                              value={editValue}
                              onChange={(e) => setEditValue(e.target.value)}
                              className="flex-1 px-2 py-1 border border-gray-300 rounded"
                              autoFocus
                            />
                            <button
                              onClick={saveEdit}
                              className="px-3 py-1 bg-primary-600 text-white rounded text-sm"
                            >
                              ✓
                            </button>
                            <button
                              onClick={cancelEdit}
                              className="px-3 py-1 bg-gray-300 rounded text-sm"
                            >
                              ✕
                            </button>
                          </div>
                        ) : (
                          <>
                            <span className="flex-1">
                              <span className={item.bought ? 'line-through text-gray-500' : ''}>
                                {item.item} - {scaleQuantity(item.quantity, 1, selectedServings)}
                              </span>
                              {item.note && (
                                <span className="text-sm text-gray-500 ml-2">({item.note})</span>
                              )}
                            </span>
                            <button
                              onClick={() => startEditing(category, index, 'quantity')}
                              className="text-sm text-primary-600 hover:underline"
                            >
                              Edit
                            </button>
                          </>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            ))}
          </div>
        </div>
      </CoachieCard>

      {/* Meal Plan */}
      {blueprint.meals && blueprint.meals.length > 0 && (
        <CoachieCard>
          <div className="p-6">
            <h2 className="text-xl font-bold mb-4">🍽️ 7-Day Meal Plan</h2>
            <div className="space-y-4">
              {blueprint.meals.map((dayMeals, dayIndex) => {
                const dayName = dayMeals.day || daysOfWeek[dayIndex] || `Day ${dayIndex + 1}`
                const isExpanded = expandedDays.has(dayName)
                
                return (
                  <div key={dayIndex}>
                    <button
                      onClick={() => toggleDay(dayName)}
                      className="flex items-center justify-between w-full text-left font-semibold text-lg mb-2 p-2 hover:bg-gray-50 rounded"
                    >
                      <span>{dayName}</span>
                      <span>{isExpanded ? '▼' : '▶'}</span>
                    </button>
                    {isExpanded && (
                      <div className="ml-4 space-y-3">
                        {dayMeals.breakfast && (
                          <div className="p-3 bg-gray-50 rounded">
                            <div className="font-medium">🌅 Breakfast: {dayMeals.breakfast.name}</div>
                            <div className="text-sm text-gray-600 mt-1">
                              {dayMeals.breakfast.calories} cal | {dayMeals.breakfast.protein}g protein | {dayMeals.breakfast.carbs}g carbs | {dayMeals.breakfast.fat}g fat
                            </div>
                            {dayMeals.breakfast.ingredients && dayMeals.breakfast.ingredients.length > 0 && (
                              <div className="text-sm text-gray-500 mt-1">
                                Ingredients: {dayMeals.breakfast.ingredients.join(', ')}
                              </div>
                            )}
                          </div>
                        )}
                        {dayMeals.lunch && (
                          <div className="p-3 bg-gray-50 rounded">
                            <div className="font-medium">☀️ Lunch: {dayMeals.lunch.name}</div>
                            <div className="text-sm text-gray-600 mt-1">
                              {dayMeals.lunch.calories} cal | {dayMeals.lunch.protein}g protein | {dayMeals.lunch.carbs}g carbs | {dayMeals.lunch.fat}g fat
                            </div>
                            {dayMeals.lunch.ingredients && dayMeals.lunch.ingredients.length > 0 && (
                              <div className="text-sm text-gray-500 mt-1">
                                Ingredients: {dayMeals.lunch.ingredients.join(', ')}
                              </div>
                            )}
                          </div>
                        )}
                        {dayMeals.dinner && (
                          <div className="p-3 bg-gray-50 rounded">
                            <div className="font-medium">🌙 Dinner: {dayMeals.dinner.name}</div>
                            <div className="text-sm text-gray-600 mt-1">
                              {dayMeals.dinner.calories} cal | {dayMeals.dinner.protein}g protein | {dayMeals.dinner.carbs}g carbs | {dayMeals.dinner.fat}g fat
                            </div>
                            {dayMeals.dinner.ingredients && dayMeals.dinner.ingredients.length > 0 && (
                              <div className="text-sm text-gray-500 mt-1">
                                Ingredients: {dayMeals.dinner.ingredients.join(', ')}
                              </div>
                            )}
                          </div>
                        )}
                        {dayMeals.meal4 && (
                          <div className="p-3 bg-gray-50 rounded">
                            <div className="font-medium">🍽️ Meal 4: {dayMeals.meal4.name}</div>
                            <div className="text-sm text-gray-600 mt-1">
                              {dayMeals.meal4.calories} cal | {dayMeals.meal4.protein}g protein | {dayMeals.meal4.carbs}g carbs | {dayMeals.meal4.fat}g fat
                            </div>
                          </div>
                        )}
                        {dayMeals.snacks && dayMeals.snacks.length > 0 && (
                          <div className="space-y-2">
                            {dayMeals.snacks.map((snack, snackIndex) => (
                              <div key={snackIndex} className="p-3 bg-gray-50 rounded">
                                <div className="font-medium">🍎 Snack: {snack.name}</div>
                                <div className="text-sm text-gray-600 mt-1">
                                  {snack.calories} cal | {snack.protein}g protein | {snack.carbs}g carbs | {snack.fat}g fat
                                </div>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          </div>
        </CoachieCard>
      )}
    </div>
  )
}

