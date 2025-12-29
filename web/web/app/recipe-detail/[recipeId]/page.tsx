'use client'

import { useAuth } from '../../../lib/contexts/AuthContext'
import { useRouter } from 'next/navigation'
import { useState, useEffect, useCallback } from 'react'
import LoadingScreen from '../../../components/LoadingScreen'
import { doc, getDoc } from 'firebase/firestore'
import { db } from '../../../lib/firebase'
import toast from 'react-hot-toast'
import SharePlatformDialog from '../../../components/SharePlatformDialog'
import ShareService from '../../../lib/services/shareService'

interface Recipe {
  id: string
  name: string
  author: string
  servings: number
  calories: number
  ingredients?: string[]
  instructions?: string[]
  prepTime?: number
  cookTime?: number
  createdAt: Date
}

export default function RecipeDetailPage({ params }: { params: Promise<{ recipeId: string }> }) {
  const { user, userProfile } = useAuth()
  const router = useRouter()
  const [loading, setLoading] = useState(true)
  const [recipe, setRecipe] = useState<Recipe | null>(null)
  const [recipeId, setRecipeId] = useState<string>('')
  const [showShareDialog, setShowShareDialog] = useState(false)
  const [photoUrl, setPhotoUrl] = useState<string | null>(null)

  useEffect(() => {
    const loadParams = async () => {
      try {
        const p = await params
        setRecipeId(p.recipeId)
      } catch (error) {
        console.error('Error getting recipe ID from params:', error)
      }
    }
    loadParams()
  }, [params])

  const loadRecipe = useCallback(async () => {
    if (!recipeId) return
    try {
      setLoading(true)
      console.log('Loading recipe:', recipeId)
      
      // Try sharedRecipes collection first
      let recipeDoc = await getDoc(doc(db, 'sharedRecipes', recipeId))
      console.log('sharedRecipes lookup:', recipeDoc.exists() ? 'found' : 'not found')
      
      if (!recipeDoc.exists()) {
        // Try users/{userId}/recipes collection
        if (user) {
          recipeDoc = await getDoc(doc(db, 'users', user.uid, 'recipes', recipeId))
          console.log('users recipes lookup:', recipeDoc.exists() ? 'found' : 'not found')
        }
      }
      
      // If still not found, try searching all sharedRecipes for a matching ID field
      if (!recipeDoc.exists()) {
        console.log('Recipe not found by ID, trying to search by id field...')
        try {
          const { collection: col, query: q, where: w, getDocs: getDocsQuery } = await import('firebase/firestore')
          const sharedRecipesRef = col(db, 'sharedRecipes')
          const searchQuery = q(sharedRecipesRef, w('id', '==', recipeId))
          const searchSnap = await getDocsQuery(searchQuery)
          if (!searchSnap.empty) {
            recipeDoc = searchSnap.docs[0]
            console.log('Found recipe by id field:', recipeDoc.id)
          }
        } catch (searchError) {
          console.error('Error searching for recipe:', searchError)
        }
      }

      if (recipeDoc.exists()) {
        const data = recipeDoc.data()
        
        // Safely extract ingredients - handle both array and object formats
        // Android stores ingredients as objects: [{name: "flour", quantity: 2, unit: "cups"}, ...]
        // Web stores ingredients as strings: ["2 cups flour", ...]
        let ingredients: string[] = []
        if (Array.isArray(data.ingredients)) {
          // Check if it's an array of objects (Android format) or strings (Web format)
          if (data.ingredients.length > 0 && typeof data.ingredients[0] === 'object' && data.ingredients[0] !== null && 'name' in data.ingredients[0]) {
            // Android format: array of objects with name, quantity, unit
            ingredients = data.ingredients.map((ing: any) => {
              if (ing && typeof ing === 'object' && ing.name) {
                const qty = ing.quantity || ''
                const unit = ing.unit || ''
                const name = ing.name || ''
                return `${qty} ${unit} ${name}`.trim()
              }
              return String(ing)
            }).filter(Boolean)
          } else {
            // Web format: array of strings
            ingredients = data.ingredients.filter((ing: any) => typeof ing === 'string')
          }
        } else if (Array.isArray(data.ingredientList)) {
          ingredients = data.ingredientList.filter((ing: any) => typeof ing === 'string')
        } else if (data.ingredients && typeof data.ingredients === 'object' && !Array.isArray(data.ingredients)) {
          // Handle object format (e.g., {0: "ingredient1", 1: "ingredient2"})
          ingredients = Object.values(data.ingredients).filter((ing: any) => typeof ing === 'string') as string[]
        }
        
        // Safely extract instructions - handle both array and object formats
        let instructions: string[] = []
        if (Array.isArray(data.instructions)) {
          // Instructions are typically strings in both formats
          instructions = data.instructions.filter((inst: any) => typeof inst === 'string')
        } else if (Array.isArray(data.instructionList)) {
          instructions = data.instructionList.filter((inst: any) => typeof inst === 'string')
        } else if (data.instructions && typeof data.instructions === 'object' && !Array.isArray(data.instructions)) {
          // Handle object format
          instructions = Object.values(data.instructions).filter((inst: any) => typeof inst === 'string') as string[]
        }
        
        setRecipe({
          id: recipeDoc.id,
          name: data.name || data.title || 'Untitled Recipe',
          author: data.authorName || data.author || 'Unknown',
          servings: data.servings || 2,
          calories: data.calories || data.totalCalories || 0,
          ingredients,
          instructions,
          prepTime: data.prepTime,
          cookTime: data.cookTime,
          createdAt: data.createdAt?.toDate() || new Date(),
        })
      } else {
        toast.error('Recipe not found')
        router.push('/shared-recipes')
      }
    } catch (error) {
      console.error('Error loading recipe:', error)
      toast.error('Failed to load recipe')
      router.push('/shared-recipes')
    } finally {
      setLoading(false)
    }
  }, [recipeId, user, router])

  useEffect(() => {
    if (!user) {
      router.push('/auth')
      return
    }
    if (recipeId) {
      loadRecipe()
    }
  }, [user, recipeId, router, loadRecipe])

  if (loading || !user || !userProfile) {
    return <LoadingScreen />
  }

  const gradientClass = userProfile.gender === 'male'
    ? 'bg-coachie-gradient-male'
    : userProfile.gender === 'female'
    ? 'bg-coachie-gradient-female'
    : 'bg-coachie-gradient'

  if (!recipe) {
    return (
      <div className={`min-h-screen ${gradientClass}`}>
        <div className="max-w-4xl mx-auto py-8 px-4">
          <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-12 text-center">
            <p className="text-white">Recipe not found</p>
            <button
              onClick={() => router.push('/shared-recipes')}
              className="mt-4 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
            >
              Back to Recipes
            </button>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className={`min-h-screen ${gradientClass}`}>
      <div className="max-w-4xl mx-auto py-8 px-4">
        <button
          onClick={() => router.back()}
          className="mb-4 text-white/90 hover:text-white flex items-center gap-2"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
          </svg>
          Back
        </button>

        <div className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-6">
          <div className="flex items-start justify-between mb-4">
            <div className="flex-1">
              <h1 className="text-3xl font-bold text-white mb-2">{recipe.name}</h1>
              <p className="text-white/80">By {recipe.author}</p>
            </div>
            <button
              onClick={() => setShowShareDialog(true)}
              className="ml-4 p-2 bg-white/20 hover:bg-white/30 rounded-lg transition-colors"
              title="Share recipe"
            >
              <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8.684 13.342C8.885 12.938 9 12.482 9 12c0-.482-.115-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z" />
              </svg>
            </button>
          </div>

          <div className="grid grid-cols-3 gap-4 mb-6">
            <div className="text-center">
              <div className="text-2xl font-bold text-white">{recipe.calories}</div>
              <div className="text-sm text-white/70">Calories</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-white">{recipe.servings}</div>
              <div className="text-sm text-white/70">Servings</div>
            </div>
            {(recipe.prepTime || recipe.cookTime) && (
              <div className="text-center">
                <div className="text-2xl font-bold text-white">
                  {(recipe.prepTime || 0) + (recipe.cookTime || 0)}
                </div>
                <div className="text-sm text-white/70">Minutes</div>
              </div>
            )}
          </div>

          {recipe.ingredients && Array.isArray(recipe.ingredients) && recipe.ingredients.length > 0 && (
            <div className="mb-6">
              <h2 className="text-xl font-semibold text-white mb-3">Ingredients</h2>
              <ul className="space-y-2">
                {recipe.ingredients.map((ingredient: string, index: number) => (
                  <li key={index} className="text-white/90 flex items-start">
                    <span className="mr-2">•</span>
                    <span>{ingredient}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {recipe.instructions && Array.isArray(recipe.instructions) && recipe.instructions.length > 0 && (
            <div>
              <h2 className="text-xl font-semibold text-white mb-3">Instructions</h2>
              <ol className="space-y-3">
                {recipe.instructions.map((instruction: string, index: number) => (
                  <li key={index} className="text-white/90">
                    <span className="font-semibold mr-2">{index + 1}.</span>
                    {instruction}
                  </li>
                ))}
              </ol>
            </div>
          )}
        </div>

        {showShareDialog && recipe && (
          <SharePlatformDialog
            onDismiss={() => setShowShareDialog(false)}
            onShareToPlatform={async (platform) => {
              setShowShareDialog(false)
              if (!recipe) {
                toast.error('Recipe not available to share')
                return
              }
              try {
                // For now, generate a simple share image or use text
                // In a full implementation, you'd generate an image with the recipe details
                const shareService = ShareService.getInstance()
                const ingredientsText = Array.isArray(recipe.ingredients) && recipe.ingredients.length > 0
                  ? recipe.ingredients.slice(0, 3).join(', ')
                  : 'Delicious recipe'
                const shareText = `Check out this recipe: ${recipe.name}!\n\n${ingredientsText}...\n\nTracked with Coachie → coachieai.playspace.games`
                
                if (platform) {
                  // For image sharing, we'd need to generate an image first
                  // For now, use text sharing
                  if (navigator.share) {
                    await navigator.share({
                      title: recipe.name,
                      text: shareText,
                    })
                  } else {
                    await navigator.clipboard.writeText(shareText)
                    toast.success('Recipe details copied to clipboard!')
                  }
                } else {
                  // Native share
                  if (navigator.share) {
                    await navigator.share({
                      title: recipe.name,
                      text: shareText,
                    })
                  } else {
                    await navigator.clipboard.writeText(shareText)
                    toast.success('Recipe details copied to clipboard!')
                  }
                }
              } catch (error) {
                console.error('Error sharing:', error)
                toast.error('Failed to share recipe')
              }
            }}
            photoUrl={photoUrl}
            onCapturePhoto={() => {
              // TODO: Implement photo capture
              toast('Photo capture coming soon')
            }}
            onSelectPhoto={() => {
              // TODO: Implement photo selection
              toast('Photo selection coming soon')
            }}
          />
        )}
      </div>
    </div>
  )
}
