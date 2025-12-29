'use client'

import { useAuth } from '../../lib/contexts/AuthContext'
import { useRouter } from 'next/navigation'
import { useState, useCallback } from 'react'
import { useDropzone } from 'react-dropzone'
import toast from 'react-hot-toast'

export default function RecipeCapturePage() {
  const { user } = useAuth()
  const router = useRouter()
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [analyzing, setAnalyzing] = useState(false)
  const [recipe, setRecipe] = useState<any>(null)

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
      'image/*': ['.jpeg', '.jpg', '.png', '.webp'],
    },
    multiple: false,
    maxSize: 10 * 1024 * 1024,
  })

  const analyzeRecipe = async () => {
    if (!selectedFile || !user) return

    setAnalyzing(true)
    try {
      // Call Firebase Cloud Function for recipe analysis (uses GPT-4o Vision)
      const { functions } = await import('../../lib/firebase')
      const { httpsCallable } = await import('firebase/functions')
      
      const base64Image = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = () => {
          const base64 = (reader.result as string).split(',')[1]
          resolve(base64)
        }
        reader.onerror = reject
        reader.readAsDataURL(selectedFile)
      })

      const analyzeRecipe = httpsCallable(functions, 'analyzeRecipe')
      const result = await analyzeRecipe({
        userId: user.uid,
        image: base64Image,
        platform: 'web',
      })

      const data = result.data as any
      if (data && data.recipe) {
        setRecipe({
          name: data.recipe.name || data.recipe.title,
          ingredients: data.recipe.ingredients || [],
          instructions: data.recipe.instructions || data.recipe.steps || [],
          servings: data.recipe.servings || 2,
          calories: data.recipe.calories || 0,
        })
        toast.success('Recipe analyzed!')
      } else {
        toast.error('Failed to analyze recipe')
      }
    } catch (error) {
      console.error('Error analyzing recipe:', error)
      toast.error('Failed to analyze recipe')
    } finally {
      setAnalyzing(false)
    }
  }

  if (!user) {
    return <div>Loading...</div>
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-yellow-50 to-orange-100">
      <div className="max-w-2xl mx-auto py-8 px-4">
        <div className="bg-white rounded-lg shadow-lg p-6">
          <h1 className="text-2xl font-bold text-gray-900 mb-6">Capture Recipe</h1>

          {!previewUrl && (
            <div
              {...getRootProps()}
              className={`border-2 border-dashed rounded-lg p-12 text-center cursor-pointer transition-colors ${
                isDragActive
                  ? 'border-orange-500 bg-orange-50'
                  : 'border-gray-300 hover:border-orange-400'
              }`}
            >
              <input {...getInputProps()} />
              <div className="space-y-2">
                <div className="text-4xl">📸</div>
                <p className="text-gray-600">
                  {isDragActive
                    ? 'Drop the recipe image here'
                    : 'Drag & drop a recipe photo, or click to select'}
                </p>
                <p className="text-sm text-gray-500">Supports JPEG, PNG, WebP (max 10MB)</p>
              </div>
            </div>
          )}

          {previewUrl && (
            <div className="space-y-4">
              <div className="relative">
                <img
                  src={previewUrl}
                  alt="Recipe"
                  className="w-full h-64 object-contain rounded-lg border border-gray-200"
                />
                <button
                  onClick={() => {
                    setSelectedFile(null)
                    setPreviewUrl(null)
                    setRecipe(null)
                  }}
                  className="absolute top-2 right-2 bg-red-500 text-white rounded-full w-8 h-8 flex items-center justify-center hover:bg-red-600"
                >
                  ×
                </button>
              </div>

              {!recipe && (
                <button
                  onClick={analyzeRecipe}
                  disabled={analyzing}
                  className="w-full px-4 py-2 bg-orange-600 text-white rounded-lg hover:bg-orange-700 disabled:opacity-50"
                >
                  {analyzing ? 'Analyzing Recipe...' : 'Analyze Recipe'}
                </button>
              )}

              {recipe && (
                <div className="bg-orange-50 rounded-lg p-6 space-y-4">
                  <div>
                    <h3 className="font-semibold text-gray-900 text-lg">{recipe.name}</h3>
                    <p className="text-sm text-gray-600">{recipe.calories} calories • {recipe.servings} servings</p>
                  </div>
                  <div>
                    <h4 className="font-medium text-gray-900 mb-2">Ingredients</h4>
                    <ul className="list-disc list-inside text-sm text-gray-700">
                      {recipe.ingredients.map((ing: string, i: number) => (
                        <li key={i}>{ing}</li>
                      ))}
                    </ul>
                  </div>
                  <div>
                    <h4 className="font-medium text-gray-900 mb-2">Instructions</h4>
                    <ol className="list-decimal list-inside text-sm text-gray-700">
                      {recipe.instructions.map((step: string, i: number) => (
                        <li key={i}>{step}</li>
                      ))}
                    </ol>
                  </div>
                  <div className="flex gap-4">
                    <button
                      onClick={() => router.back()}
                      className="flex-1 px-4 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50"
                    >
                      Cancel
                    </button>
                    <button
                    onClick={async () => {
                      if (!user) return
                      try {
                        const RecipesService = (await import('../../lib/services/recipesService')).default
                        const recipesService = RecipesService.getInstance()
                        await recipesService.saveRecipe(user.uid, {
                          name: recipe.name,
                          servings: recipe.servings,
                          calories: recipe.calories,
                          ingredients: recipe.ingredients,
                          instructions: recipe.instructions,
                        })
                        toast.success('Recipe saved!')
                        router.push('/my-recipes')
                      } catch (error) {
                        console.error('Error saving recipe:', error)
                        toast.error('Failed to save recipe')
                      }
                    }}
                      className="flex-1 px-4 py-2 bg-orange-600 text-white rounded-lg hover:bg-orange-700"
                    >
                      Save Recipe
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
