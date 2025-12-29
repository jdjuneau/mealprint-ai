'use client'

import { useAuth } from '../../lib/contexts/AuthContext'
import { useRouter } from 'next/navigation'
import { useState, useEffect } from 'react'
import LoadingScreen from '../../components/LoadingScreen'
import { collection, query, orderBy, limit, getDocs } from 'firebase/firestore'
import { db } from '../../lib/firebase'

interface SharedRecipe {
  id: string
  name: string
  author: string
  servings: number
  calories: number
  createdAt: Date
}

export default function SharedRecipesPage() {
  const { user, userProfile } = useAuth()
  const router = useRouter()
  const [loading, setLoading] = useState(true)
  const [recipes, setRecipes] = useState<SharedRecipe[]>([])

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
      // Load shared recipes from Firestore
      const sharedRecipesRef = collection(db, 'sharedRecipes')
      const sharedRecipesQuery = query(sharedRecipesRef, orderBy('createdAt', 'desc'), limit(20))
      const sharedRecipesSnap = await getDocs(sharedRecipesQuery)

      setRecipes(
        sharedRecipesSnap.docs.map((doc) => {
          const data = doc.data()
          return {
            id: doc.id,
            name: data.name || data.title,
            author: data.authorName || data.author || 'Unknown',
            servings: data.servings || 2,
            calories: data.calories || 0,
            createdAt: data.createdAt?.toDate() || new Date(),
          }
        })
      )
    } catch (error) {
      console.error('Error loading shared recipes:', error)
    } finally {
      setLoading(false)
    }
  }

  if (loading || !user || !userProfile) {
    return <LoadingScreen />
  }

  const gradientClass = userProfile.gender === 'male'
    ? 'bg-coachie-gradient-male'
    : userProfile.gender === 'female'
    ? 'bg-coachie-gradient-female'
    : 'bg-coachie-gradient'

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

        <h1 className="text-2xl font-bold text-white mb-6">Shared Recipes</h1>

        {recipes.length === 0 ? (
          <div className="bg-white/10 backdrop-blur-sm rounded-lg shadow-lg p-12 text-center border border-white/20">
            <div className="text-4xl mb-4">👥</div>
            <h2 className="text-xl font-semibold text-white mb-2">No shared recipes</h2>
            <p className="text-white/70">Recipes shared by the community will appear here</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {recipes.map((recipe) => (
              <button
                key={recipe.id}
                onClick={() => router.push(`/recipe-detail/${recipe.id}`)}
                className="p-6 bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 hover:bg-white/20 transition-colors text-left"
              >
                <h3 className="font-semibold text-white mb-2">{recipe.name}</h3>
                <p className="text-sm text-white/70 mb-2">By {recipe.author}</p>
                <p className="text-sm text-white/60">
                  {recipe.calories} cal • {recipe.servings} servings
                </p>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
