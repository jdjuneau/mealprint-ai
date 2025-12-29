'use client'

import { useAuth } from '../../lib/contexts/AuthContext'
import { useRouter } from 'next/navigation'
import { useState, useEffect } from 'react'
import LoadingScreen from '../../components/LoadingScreen'

interface Recipe {
  id: string
  name: string
  servings: number
  calories: number
  ingredients: string[]
  createdAt: Date
}

export default function MyRecipesPage() {
  const { user } = useAuth()
  const router = useRouter()
  const [loading, setLoading] = useState(true)
  const [recipes, setRecipes] = useState<Recipe[]>([])

  useEffect(() => {
    if (!user) {
      router.push('/auth')
    } else {
      loadRecipes()
    }
  }, [user, router])

  const loadRecipes = async () => {
    if (!user) return
    try {
      const RecipesService = (await import('../../lib/services/recipesService')).default
      const recipesService = RecipesService.getInstance()
      const loadedRecipes = await recipesService.getRecipes(user.uid)
      setRecipes(loadedRecipes)
    } catch (error) {
      console.error('Error loading recipes:', error)
    } finally {
      setLoading(false)
    }
  }

  if (loading || !user) {
    return <LoadingScreen />
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-4xl mx-auto py-8 px-4">
        <div className="flex items-center justify-between mb-6">
          <h1 className="text-2xl font-bold text-gray-900">My Recipes</h1>
          <button
            onClick={() => router.push('/recipe-capture')}
            className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
          >
            + New Recipe
          </button>
        </div>

        {recipes.length === 0 ? (
          <div className="bg-white rounded-lg shadow-sm p-12 text-center">
            <div className="text-4xl mb-4">📝</div>
            <h2 className="text-xl font-semibold text-gray-900 mb-2">No recipes yet</h2>
            <p className="text-gray-600 mb-6">Capture recipes from photos or create your own</p>
            <button
              onClick={() => router.push('/recipe-capture')}
              className="px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
            >
              Capture Recipe
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {recipes.map((recipe) => (
              <div
                key={recipe.id}
                className="bg-white rounded-lg shadow-sm p-6 cursor-pointer hover:shadow-md transition-shadow"
                onClick={() => router.push(`/recipe-detail/${recipe.id}`)}
              >
                <h3 className="font-semibold text-gray-900 mb-2">{recipe.name}</h3>
                <p className="text-sm text-gray-600 mb-2">
                  {recipe.calories} cal • {recipe.servings} servings
                </p>
                <p className="text-xs text-gray-500">
                  {recipe.ingredients.length} ingredients • Created{' '}
                  {new Date(recipe.createdAt).toLocaleDateString()}
                </p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
