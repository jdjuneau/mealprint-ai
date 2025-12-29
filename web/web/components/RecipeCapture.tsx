'use client'

import { useState, useCallback, useEffect } from 'react'
import { useDropzone } from 'react-dropzone'
import { httpsCallable } from 'firebase/functions'
import { functions } from '../lib/firebase'
import { FirebaseService } from '../lib/services/firebase'
import { useAuth } from '../lib/contexts/AuthContext'
import toast from 'react-hot-toast'
import CoachieCard from './ui/CoachieCard'

interface RecipeIngredient {
  name: string
  quantity: number
  unit: string
  calories: number
  proteinG: number
  carbsG: number
  fatG: number
  sugarG: number
  micronutrients: Record<string, number>
}

interface Recipe {
  name: string
  description?: string
  servings: number
  ingredients: RecipeIngredient[]
  instructions: string[]
  totalCalories: number
  totalProteinG: number
  totalCarbsG: number
  totalFatG: number
  totalSugarG: number
  totalAddedSugarG: number
  micronutrients: Record<string, number>
  perServing: {
    calories: number
    proteinG: number
    carbsG: number
    fatG: number
    sugarG: number
    addedSugarG: number
    micronutrients: Record<string, number>
  }
}

interface RecipeCaptureProps {
  userId: string
  onRecipeSaved?: () => void
  onBack?: () => void
}

type InputMode = 'photo' | 'text'

export default function RecipeCapture({ userId, onRecipeSaved, onBack }: RecipeCaptureProps) {
  const { user } = useAuth()
  const [inputMode, setInputMode] = useState<InputMode>('photo')
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [recipeText, setRecipeText] = useState('')
  const [servings, setServings] = useState('4')
  const [analyzing, setAnalyzing] = useState(false)
  const [recipe, setRecipe] = useState<Recipe | null>(null)
  const [saving, setSaving] = useState(false)
  const [showShareDialog, setShowShareDialog] = useState(false)
  const [friends, setFriends] = useState<any[]>([])
  const [selectedFriends, setSelectedFriends] = useState<Set<string>>(new Set())

  const onDrop = useCallback((acceptedFiles: File[]) => {
    const file = acceptedFiles[0]
    if (file) {
      setSelectedFile(file)
      setPreviewUrl(URL.createObjectURL(file))
      setRecipe(null)
    }
  }, [])

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: {
      'image/*': ['.jpeg', '.jpg', '.png', '.webp']
    },
    multiple: false,
    maxSize: 10 * 1024 * 1024, // 10MB
  })

  // Load friends when share dialog opens
  useEffect(() => {
    if (showShareDialog) {
      loadFriends()
    }
  }, [showShareDialog])

  const loadFriends = async () => {
    try {
      // TODO: Implement getFriends in FirebaseService
      // const friendsList = await FirebaseService.getFriends(userId)
      // setFriends(friendsList)
      setFriends([]) // Placeholder
    } catch (error) {
      console.error('Error loading friends:', error)
    }
  }

  const fileToBase64 = (file: File): Promise<string> => {
    return new Promise((resolve, reject) => {
      const reader = new FileReader()
      reader.readAsDataURL(file)
      reader.onload = () => {
        const base64 = (reader.result as string).split(',')[1] // Remove data:image/jpeg;base64, prefix
        resolve(base64)
      }
      reader.onerror = error => reject(error)
    })
  }

  const analyzeRecipe = async () => {
    if ((inputMode === 'photo' && !selectedFile) || (inputMode === 'text' && !recipeText.trim())) {
      toast.error('Please provide a recipe photo or text')
      return
    }

    const servingsNum = parseInt(servings) || 4
    if (servingsNum < 1) {
      toast.error('Servings must be at least 1')
      return
    }

    setAnalyzing(true)
    try {
      const analyzeRecipeFunction = httpsCallable(functions, 'analyzeRecipe')
      
      let data: any = {
        servings: servingsNum
      }

      if (inputMode === 'photo' && selectedFile) {
        const imageBase64 = await fileToBase64(selectedFile)
        data.imageBase64 = imageBase64
      } else if (inputMode === 'text') {
        data.recipeText = recipeText
      }

      const result = await analyzeRecipeFunction(data)
      const responseData = result.data as any

      if (responseData?.success && responseData?.recipe) {
        setRecipe(responseData.recipe)
        toast.success('Recipe analyzed successfully!')
      } else {
        toast.error('Failed to analyze recipe')
      }
    } catch (error: any) {
      console.error('Error analyzing recipe:', error)
      toast.error(error.message || 'Failed to analyze recipe. Please try again.')
    } finally {
      setAnalyzing(false)
    }
  }

  const saveToQuickSave = async () => {
    if (!recipe) return

    setSaving(true)
    try {
      // Convert recipe to SavedMeal (single serving)
      const savedMeal = {
        id: `recipe_${Date.now()}`,
        userId: userId,
        name: recipe.name,
        foodName: recipe.name,
        calories: recipe.perServing.calories,
        proteinG: recipe.perServing.proteinG,
        carbsG: recipe.perServing.carbsG,
        fatG: recipe.perServing.fatG,
        sugarG: recipe.perServing.sugarG || 0,
        addedSugarG: recipe.perServing.addedSugarG || 0,
        createdAt: new Date(),
        lastUsedAt: new Date(),
        useCount: 1
      }

      const SavedMealsService = (await import('../lib/services/savedMealsService')).default
      const savedMealsService = SavedMealsService.getInstance()
      await savedMealsService.saveMeal(userId, savedMeal)
      toast.success('Recipe saved to quick save!')
      
      if (onRecipeSaved) {
        onRecipeSaved()
      }
    } catch (error) {
      console.error('Error saving recipe:', error)
      toast.error('Failed to save recipe. Please try again.')
    } finally {
      setSaving(false)
    }
  }

  const shareWithFriends = async () => {
    if (!recipe || selectedFriends.size === 0) return

    setSaving(true)
    try {
      // TODO: Implement recipe sharing in FirebaseService
      // Save recipe to sharedRecipes collection
      toast.success('Recipe shared with friends!')
      setShowShareDialog(false)
      setSelectedFriends(new Set())
    } catch (error) {
      console.error('Error sharing recipe:', error)
      toast.error('Failed to share recipe. Please try again.')
    } finally {
      setSaving(false)
    }
  }

  const reset = () => {
    setSelectedFile(null)
    setPreviewUrl(null)
    setRecipeText('')
    setRecipe(null)
    setServings('4')
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6 pb-8">
      <div className="text-center">
        <h1 className="text-2xl font-bold text-gray-900 mb-2">📝 Recipe Analysis</h1>
        <p className="text-gray-600">Take a photo or upload a recipe to get macro and micro nutrition estimates per serving</p>
      </div>

      {!recipe ? (
        <>
          {/* Mode Selector */}
          <div className="flex gap-2 justify-center">
            <button
              onClick={() => setInputMode('photo')}
              className={`px-4 py-2 rounded-md transition-colors ${
                inputMode === 'photo'
                  ? 'bg-primary-600 text-white'
                  : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
              }`}
            >
              📷 Photo
            </button>
            <button
              onClick={() => setInputMode('text')}
              className={`px-4 py-2 rounded-md transition-colors ${
                inputMode === 'text'
                  ? 'bg-primary-600 text-white'
                  : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
              }`}
            >
              ✍️ Text
            </button>
          </div>

          {/* Servings Input */}
          <CoachieCard>
            <div className="p-6">
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Number of Servings
              </label>
              <input
                type="number"
                value={servings}
                onChange={(e) => setServings(e.target.value)}
                min="1"
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
            </div>
          </CoachieCard>

          {/* Photo Input */}
          {inputMode === 'photo' && (
            <CoachieCard>
              <div className="p-6">
                <div
                  {...getRootProps()}
                  className={`border-2 border-dashed rounded-lg p-8 text-center cursor-pointer transition-colors ${
                    isDragActive
                      ? 'border-primary-400 bg-primary-50'
                      : 'border-gray-300 hover:border-primary-400'
                  }`}
                >
                  <input {...getInputProps()} />
                  {previewUrl ? (
                    <div className="relative inline-block">
                      <img
                        src={previewUrl}
                        alt="Recipe preview"
                        className="max-w-full max-h-96 rounded-lg shadow-sm"
                      />
                      <button
                        onClick={(e) => {
                          e.stopPropagation()
                          reset()
                        }}
                        className="absolute -top-2 -right-2 bg-red-500 text-white rounded-full p-1 hover:bg-red-600"
                      >
                        ✕
                      </button>
                    </div>
                  ) : (
                    <>
                      <span className="mx-auto text-5xl text-gray-400 block mb-4">📷</span>
                      <p className="text-lg font-medium text-gray-900">
                        {isDragActive ? 'Drop recipe photo here' : 'Upload recipe photo'}
                      </p>
                      <p className="text-sm text-gray-500 mt-1">
                        Drag & drop or click to browse (JPG, PNG up to 10MB)
                      </p>
                    </>
                  )}
                </div>
              </div>
            </CoachieCard>
          )}

          {/* Text Input */}
          {inputMode === 'text' && (
            <CoachieCard>
              <div className="p-6">
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Paste Recipe Text
                </label>
                <textarea
                  value={recipeText}
                  onChange={(e) => setRecipeText(e.target.value)}
                  rows={12}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-primary-500"
                  placeholder="Paste your recipe here with ingredients and instructions..."
                />
              </div>
            </CoachieCard>
          )}

          {/* Analyze Button */}
          <div className="text-center">
            <button
              onClick={analyzeRecipe}
              disabled={analyzing || (inputMode === 'photo' && !selectedFile) || (inputMode === 'text' && !recipeText.trim())}
              className="bg-primary-600 text-white px-6 py-3 rounded-md hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {analyzing ? 'Analyzing Recipe...' : 'Analyze Recipe'}
            </button>
          </div>
        </>
      ) : (
        <>
          {/* Recipe Results */}
          <CoachieCard>
            <div className="p-6 space-y-6">
              <div className="text-center">
                <h2 className="text-2xl font-bold text-gray-900 mb-2">{recipe.name}</h2>
                {recipe.description && (
                  <p className="text-gray-600">{recipe.description}</p>
                )}
              </div>

              {/* Photo Preview */}
              {previewUrl && (
                <div className="flex justify-center">
                  <img
                    src={previewUrl}
                    alt="Recipe"
                    className="max-w-full max-h-64 rounded-lg shadow-sm"
                  />
                </div>
              )}

              {/* Nutrition Per Serving */}
              <div className="bg-gray-50 rounded-lg p-4">
                <h3 className="text-lg font-semibold text-gray-900 mb-3">
                  Nutrition Per Serving ({recipe.servings} servings total)
                </h3>
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                  <div>
                    <p className="text-sm text-gray-600">Calories</p>
                    <p className="text-xl font-bold text-primary-600">{recipe.perServing.calories}</p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-600">Protein</p>
                    <p className="text-xl font-bold">{recipe.perServing.proteinG}g</p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-600">Carbs</p>
                    <p className="text-xl font-bold">{recipe.perServing.carbsG}g</p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-600">Fat</p>
                    <p className="text-xl font-bold">{recipe.perServing.fatG}g</p>
                  </div>
                </div>
              </div>

              {/* Ingredients */}
              <div>
                <h3 className="text-lg font-semibold text-gray-900 mb-3">Ingredients</h3>
                <ul className="space-y-2">
                  {recipe.ingredients.map((ingredient, index) => (
                    <li key={index} className="text-gray-700">
                      • {ingredient.quantity} {ingredient.unit} {ingredient.name}
                    </li>
                  ))}
                </ul>
              </div>

              {/* Instructions */}
              {recipe.instructions && recipe.instructions.length > 0 && (
                <div>
                  <h3 className="text-lg font-semibold text-gray-900 mb-3">Instructions</h3>
                  <ol className="space-y-2 list-decimal list-inside">
                    {recipe.instructions.map((instruction, index) => (
                      <li key={index} className="text-gray-700">{instruction}</li>
                    ))}
                  </ol>
                </div>
              )}

              {/* Action Buttons */}
              <div className="flex gap-4">
                <button
                  onClick={saveToQuickSave}
                  disabled={saving}
                  className="flex-1 bg-primary-600 text-white px-4 py-2 rounded-md hover:bg-primary-700 disabled:opacity-50"
                >
                  {saving ? 'Saving...' : '💾 Save to Quick Save'}
                </button>
                <button
                  onClick={() => setShowShareDialog(true)}
                  className="flex-1 bg-gray-200 text-gray-700 px-4 py-2 rounded-md hover:bg-gray-300"
                >
                  📤 Share
                </button>
                <button
                  onClick={reset}
                  className="bg-gray-200 text-gray-700 px-4 py-2 rounded-md hover:bg-gray-300"
                >
                  🔄 Analyze Another
                </button>
              </div>
            </div>
          </CoachieCard>

          {/* Share Dialog */}
          {showShareDialog && (
            <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
              <div className="bg-white rounded-lg p-6 max-w-md w-full mx-4">
                <h3 className="text-xl font-bold text-gray-900 mb-4">
                  Share Recipe: {recipe.name}
                </h3>
                <p className="text-sm text-gray-600 mb-4">
                  Select friends to share this recipe with:
                </p>
                
                {friends.length === 0 ? (
                  <p className="text-sm text-gray-500 mb-4">
                    No friends yet. Add friends to share recipes!
                  </p>
                ) : (
                  <div className="space-y-2 max-h-64 overflow-y-auto mb-4">
                    {friends.map((friend) => (
                      <label key={friend.uid} className="flex items-center space-x-2 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={selectedFriends.has(friend.uid)}
                          onChange={(e) => {
                            const newSelected = new Set(selectedFriends)
                            if (e.target.checked) {
                              newSelected.add(friend.uid)
                            } else {
                              newSelected.delete(friend.uid)
                            }
                            setSelectedFriends(newSelected)
                          }}
                          className="rounded"
                        />
                        <span>{friend.displayName || friend.username || 'Unknown'}</span>
                      </label>
                    ))}
                  </div>
                )}

                <div className="flex gap-2">
                  <button
                    onClick={() => {
                      setShowShareDialog(false)
                      setSelectedFriends(new Set())
                    }}
                    className="flex-1 bg-gray-200 text-gray-700 px-4 py-2 rounded-md hover:bg-gray-300"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={shareWithFriends}
                    disabled={selectedFriends.size === 0}
                    className="flex-1 bg-primary-600 text-white px-4 py-2 rounded-md hover:bg-primary-700 disabled:opacity-50"
                  >
                    Share
                  </button>
                </div>
              </div>
            </div>
          )}
        </>
      )}

      {/* Back Button */}
      {onBack && (
        <div className="text-center">
          <button
            onClick={onBack}
            className="text-primary-600 hover:text-primary-700 underline"
          >
            ← Back
          </button>
        </div>
      )}
    </div>
  )
}

