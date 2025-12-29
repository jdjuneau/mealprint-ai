'use client'

import { useState, useCallback } from 'react'
import { useDropzone } from 'react-dropzone'
import { AIService } from '../lib/services/ai'
import GeminiService from '../lib/services/geminiService'
import { FirebaseService } from '../lib/services/firebase'
import barcodeService from '../lib/services/barcodeService'
import foodLookupService, { type ParsedFoodBarcode } from '../lib/services/foodLookupService'
import SharePlatformDialog from './SharePlatformDialog'
import ShareService from '../lib/services/shareService'
import FriendsService from '../lib/services/friendsService'
import type { MealAnalysis, HealthLog } from '../types'
import toast from 'react-hot-toast'

interface MealLoggerProps {
  onAddLog: (log: HealthLog) => Promise<void>
  userId: string
  onNavigateToRecipeCapture?: () => void
  onNavigateToSavedMeals?: () => void
  onNavigateToMealRecommendation?: () => void
  onNavigateToMyRecipes?: () => void
}

export default function MealLogger({ 
  onAddLog, 
  userId, 
  onNavigateToRecipeCapture,
  onNavigateToSavedMeals,
  onNavigateToMealRecommendation,
  onNavigateToMyRecipes
}: MealLoggerProps) {
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [analyzing, setAnalyzing] = useState(false)
  const [analysis, setAnalysis] = useState<MealAnalysis | null>(null)
  const [saving, setSaving] = useState(false)
  const [manualMode, setManualMode] = useState(false)
  const [barcodeMode, setBarcodeMode] = useState(false)
  const [editing, setEditing] = useState(false)

  // Manual entry form state
  const [manualData, setManualData] = useState({
    foodName: '',
    calories: '',
    protein: '',
    carbs: '',
    fat: '',
  })

  // Editable analysis state
  const [editableAnalysis, setEditableAnalysis] = useState<MealAnalysis | null>(null)
  
  // Sharing state
  const [showShareDialog, setShowShareDialog] = useState(false)
  const [mealToShare, setMealToShare] = useState<HealthLog | null>(null)
  const [friends, setFriends] = useState<Array<{ id: string; name: string }>>([])

  const onDrop = useCallback((acceptedFiles: File[]) => {
    const file = acceptedFiles[0]
    if (file) {
      setSelectedFile(file)
      setPreviewUrl(URL.createObjectURL(file))
      setAnalysis(null)
      setEditableAnalysis(null)
      setManualMode(false)
      setBarcodeMode(false)
      setEditing(false)
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

  const analyzeMeal = async () => {
    if (!selectedFile) return

    setAnalyzing(true)
    try {
      // If in barcode mode, try to scan barcode first
      if (barcodeMode) {
        toast.loading('Scanning barcode...', { id: 'barcode-scan' })
        
        // Scan barcode from image
        const barcode = await barcodeService.scanBarcodeFromFile(selectedFile)
        
        if (!barcode) {
          toast.error('No barcode detected. Please try again or switch to camera mode.', { id: 'barcode-scan' })
          setAnalyzing(false)
          return
        }

        toast.loading('Looking up product information...', { id: 'barcode-scan' })
        
        // Lookup food data
        const foodData = await foodLookupService.lookup(barcode)
        
        if (foodData) {
          const mealAnalysis: MealAnalysis = {
            food: foodData.name,
            calories: foodData.calories,
            proteinG: foodData.protein,
            carbsG: foodData.carbs,
            fatG: foodData.fat,
            confidence: foodData.confidence,
          }
          setAnalysis(mealAnalysis)
          setEditableAnalysis(mealAnalysis)
          toast.success(`Found: ${foodData.name}`, { id: 'barcode-scan' })
          setBarcodeMode(false) // Reset barcode mode after analysis
        } else {
          toast.error(
            `Barcode ${barcode} scanned but product not found in database. Try manual entry or camera mode.`,
            { id: 'barcode-scan', duration: 5000 }
          )
        }
        setAnalyzing(false)
        return
      }
      
      // Regular meal analysis (camera mode)
      const geminiService = GeminiService.getInstance()
      const result = await geminiService.analyzeMealImage(selectedFile, userId)
      if (result) {
        const mealAnalysis: MealAnalysis = {
          food: result.food,
          calories: result.calories,
          proteinG: result.protein,
          carbsG: result.carbs,
          fatG: result.fat,
          confidence: result.confidence,
        }
        setAnalysis(mealAnalysis)
        setEditableAnalysis(mealAnalysis)
        toast.success('Meal analyzed successfully!')
      } else {
        toast.error('Failed to analyze meal image')
      }
    } catch (error: any) {
      console.error('Analysis error:', error)
      toast.dismiss('barcode-scan')
      if (error.message?.includes('rate limit')) {
        toast.error('AI analysis limit reached. Please try again later.')
      } else {
        toast.error('Failed to analyze meal. Please try again.')
      }
    } finally {
      setAnalyzing(false)
    }
  }

  const saveMealLog = async () => {
    if (!editableAnalysis && !manualMode) return

    setSaving(true)
    try {
      let mealData: any

      if (manualMode) {
        mealData = {
          foodName: manualData.foodName,
          calories: parseInt(manualData.calories) || 0,
          protein: parseFloat(manualData.protein) || 0,
          carbs: parseFloat(manualData.carbs) || 0,
          fat: parseFloat(manualData.fat) || 0,
        }
      } else if (editableAnalysis) {
        mealData = {
          foodName: editableAnalysis.food,
          calories: editableAnalysis.calories,
          protein: editableAnalysis.proteinG,
          carbs: editableAnalysis.carbsG,
          fat: editableAnalysis.fatG,
        }
      }

      // Upload image if we have one
      let photoUrl = null
      if (selectedFile) {
        photoUrl = await FirebaseService.uploadImage(userId, selectedFile, 'meal')
      }

      const mealLog: HealthLog = {
        id: `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        userId,
        type: 'meal',
        timestamp: new Date(),
        foodName: mealData.foodName,
        calories: mealData.calories,
        protein: mealData.protein,
        carbs: mealData.carbs,
        fat: mealData.fat,
        ...(photoUrl && { photoUrl }),
      }

      await onAddLog(mealLog)

      // Reset form
      resetForm()
      toast.success('Meal logged successfully!')
    } catch (error) {
      console.error('Error saving meal:', error)
      toast.error('Failed to save meal. Please try again.')
    } finally {
      setSaving(false)
    }
  }

  const resetForm = () => {
    setSelectedFile(null)
    setPreviewUrl(null)
    setAnalysis(null)
    setEditableAnalysis(null)
    setManualMode(false)
    setBarcodeMode(false)
    setEditing(false)
    setManualData({
      foodName: '',
      calories: '',
      protein: '',
      carbs: '',
      fat: '',
    })
  }

  // Load friends when share dialog opens
  const loadFriends = async () => {
    try {
      const friendsService = FriendsService.getInstance()
      const friendsList = await friendsService.getFriends(userId)
      setFriends(friendsList.map(f => ({ id: f.friendId, name: f.friendName })))
    } catch (error) {
      console.error('Error loading friends:', error)
    }
  }

  return (
    <div className="space-y-6">
      <div className="text-center">
        <div className="text-5xl mb-4">🍽️</div>
        <h1 className="text-2xl font-bold text-white mb-2">Log Your Meal</h1>
        <p className="text-white/80 text-sm">Take a photo or enter details manually</p>
      </div>

      {/* Recipe Analysis Button */}
      {onNavigateToRecipeCapture && (
        <div className="text-center">
          <button
            onClick={onNavigateToRecipeCapture}
            className="w-full bg-white/10 backdrop-blur-sm border-2 border-white/30 text-white px-6 py-3 rounded-lg hover:bg-white/20 transition-colors font-medium"
          >
            📝 Analyze Recipe
          </button>
        </div>
      )}

      {/* Mode Selector */}
      <div className="flex gap-2">
        <button
          onClick={() => {
            setManualMode(false)
            setBarcodeMode(false)
            if (!selectedFile) {
              // Trigger file input
              document.getElementById('meal-photo-input')?.click()
            }
          }}
          className={`flex-1 px-4 py-2 rounded-lg border-2 transition-colors ${
            !manualMode && !barcodeMode
              ? 'bg-white/30 border-white/50 text-white font-medium'
              : 'bg-white/10 border-white/20 text-white/80 hover:bg-white/20'
          }`}
        >
          📷 Camera
        </button>
        <button
          onClick={() => {
            setManualMode(false)
            setBarcodeMode(true)
            if (!selectedFile) {
              // Trigger file input for barcode scanning
              document.getElementById('meal-photo-input')?.click()
            }
          }}
          className={`flex-1 px-4 py-2 rounded-lg border-2 transition-colors ${
            barcodeMode
              ? 'bg-white/30 border-white/50 text-white font-medium'
              : 'bg-white/10 border-white/20 text-white/80 hover:bg-white/20'
          }`}
        >
          📱 Barcode
        </button>
        <button
          onClick={() => {
            setManualMode(true)
            setBarcodeMode(false)
          }}
          className={`flex-1 px-4 py-2 rounded-lg border-2 transition-colors ${
            manualMode
              ? 'bg-white/30 border-white/50 text-white font-medium'
              : 'bg-white/10 border-white/20 text-white/80 hover:bg-white/20'
          }`}
        >
          ✍️ Manual
        </button>
      </div>

      {/* Photo Upload Section */}
      {!manualMode && (
        <div className="bg-white/10 backdrop-blur-sm rounded-lg shadow-lg p-6 border border-white/20">
          <div className="text-center mb-4">
            <span className="text-4xl text-white/70">📸</span>
            <h3 className="text-lg font-semibold text-white mt-2">
              {barcodeMode ? '📱 Scan Barcode' : '📸 Photograph your meal'}
            </h3>
            <p className="text-sm text-white/80 mt-1">
              {barcodeMode 
                ? 'Take a photo of the barcode on the product package'
                : 'Hold your phone above the plate so the entire meal is visible. Good lighting helps AI identify foods accurately.'}
            </p>
            {!barcodeMode && (
              <p className="text-xs text-white/70 mt-1">
                Tip: capture everything you ate in one photo. You can add or edit details after the analysis.
              </p>
            )}
          </div>
          
          <div
            {...getRootProps()}
            className={`border-2 border-dashed rounded-lg p-8 text-center cursor-pointer transition-colors ${
              isDragActive
                ? 'border-white/50 bg-white/20'
                : 'border-white/30 hover:border-white/50'
            }`}
          >
            <input {...getInputProps()} id="meal-photo-input" />
            <div className="space-y-4">
              {previewUrl ? (
                <div className="relative inline-block">
                  <img
                    src={previewUrl}
                    alt={barcodeMode ? "Barcode preview" : "Meal preview"}
                    className="max-w-xs max-h-64 rounded-lg shadow-sm"
                  />
                  <button
                    onClick={(e) => {
                      e.stopPropagation()
                      resetForm()
                    }}
                    className="absolute -top-2 -right-2 bg-red-500 text-white rounded-full p-1 hover:bg-red-600"
                  >
                    <span className="text-sm">✕</span>
                  </button>
                </div>
              ) : (
                <>
                  <span className="mx-auto text-5xl text-white/70">{barcodeMode ? '📱' : '📷'}</span>
                  <div>
                    <p className="text-lg font-medium text-white">
                      {isDragActive 
                        ? 'Drop your photo here' 
                        : barcodeMode 
                          ? 'Upload a barcode photo' 
                          : 'Upload a meal photo'}
                    </p>
                    <p className="text-sm text-white/70 mt-1">
                      Drag & drop or click to browse (JPG, PNG up to 10MB)
                    </p>
                  </div>
                </>
              )}
            </div>
          </div>

          {selectedFile && !analysis && (
            <div className="mt-4 text-center">
              <button
                onClick={analyzeMeal}
                disabled={analyzing}
                className="bg-white/20 text-white px-6 py-2 rounded-lg hover:bg-white/30 disabled:opacity-50 disabled:cursor-not-allowed font-medium"
              >
                {analyzing 
                  ? (barcodeMode ? 'Scanning barcode...' : 'Analyzing...') 
                  : (barcodeMode ? '📱 Scan Barcode' : '🤖 Analyze Meal')}
              </button>
            </div>
          )}
          
          {/* Quick Actions - Only show when no photo selected */}
          {!selectedFile && !barcodeMode && (
            <div className="mt-6 space-y-3">
              <div className="grid grid-cols-2 gap-3">
                {onNavigateToSavedMeals && (
                  <button
                    onClick={onNavigateToSavedMeals}
                    className="bg-white/10 border border-white/30 text-white px-4 py-3 rounded-lg hover:bg-white/20 transition-colors"
                  >
                    <div className="text-center">
                      <div className="text-xl mb-1">⭐</div>
                      <div className="text-sm font-medium">Quick Select</div>
                      <div className="text-xs text-white/80">Saved Meals</div>
                    </div>
                  </button>
                )}
                {onNavigateToMealRecommendation && (
                  <button
                    onClick={onNavigateToMealRecommendation}
                    className="bg-white/10 border border-white/30 text-white px-4 py-3 rounded-lg hover:bg-white/20 transition-colors"
                  >
                    <div className="text-center">
                      <div className="text-xl mb-1">🤖</div>
                      <div className="text-sm font-medium">AI Recipe</div>
                      <div className="text-xs text-white/80">Generator</div>
                    </div>
                  </button>
                )}
              </div>
              {onNavigateToMyRecipes && (
                <button
                  onClick={onNavigateToMyRecipes}
                  className="w-full bg-white/10 border border-white/30 text-white px-4 py-3 rounded-lg hover:bg-white/20 transition-colors flex items-center justify-center gap-2"
                >
                  <span>📋</span>
                  <span className="font-medium">View All Recipes</span>
                </button>
              )}
            </div>
          )}
        </div>
      )}

      {/* Analysis Results with Edit Mode */}
      {analysis && !manualMode && (
        <div className="bg-white/10 backdrop-blur-sm rounded-lg shadow-lg p-6 border border-white/20">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-lg font-semibold text-white">🤖 AI Analysis</h3>
            <button
              onClick={() => setEditing(!editing)}
              className="text-white/80 hover:text-white text-sm"
            >
              {editing ? '✓ Done' : '✏️ Edit'}
            </button>
          </div>

          <div className="bg-white/20 rounded-lg p-4 space-y-4">
            {editing ? (
              <>
                <div>
                  <label className="block text-sm font-medium text-white/90 mb-1">Food Name</label>
                  <input
                    type="text"
                    value={editableAnalysis?.food || ''}
                    onChange={(e) => setEditableAnalysis(prev => prev ? { ...prev, food: e.target.value } : null)}
                    className="w-full px-3 py-2 border border-white/30 rounded-lg bg-white/10 text-white placeholder-white/50 focus:ring-2 focus:ring-white/50 focus:border-transparent"
                  />
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-white/90 mb-1">Calories</label>
                    <input
                      type="number"
                      value={editableAnalysis?.calories || 0}
                      onChange={(e) => setEditableAnalysis(prev => prev ? { ...prev, calories: parseInt(e.target.value) || 0 } : null)}
                      className="w-full px-3 py-2 border border-white/30 rounded-lg bg-white/10 text-white placeholder-white/50 focus:ring-2 focus:ring-white/50 focus:border-transparent"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-white/90 mb-1">Protein (g)</label>
                    <input
                      type="number"
                      step="0.1"
                      value={editableAnalysis?.proteinG || 0}
                      onChange={(e) => setEditableAnalysis(prev => prev ? { ...prev, proteinG: parseFloat(e.target.value) || 0 } : null)}
                      className="w-full px-3 py-2 border border-white/30 rounded-lg bg-white/10 text-white placeholder-white/50 focus:ring-2 focus:ring-white/50 focus:border-transparent"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-white/90 mb-1">Carbs (g)</label>
                    <input
                      type="number"
                      step="0.1"
                      value={editableAnalysis?.carbsG || 0}
                      onChange={(e) => setEditableAnalysis(prev => prev ? { ...prev, carbsG: parseFloat(e.target.value) || 0 } : null)}
                      className="w-full px-3 py-2 border border-white/30 rounded-lg bg-white/10 text-white placeholder-white/50 focus:ring-2 focus:ring-white/50 focus:border-transparent"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-white/90 mb-1">Fat (g)</label>
                    <input
                      type="number"
                      step="0.1"
                      value={editableAnalysis?.fatG || 0}
                      onChange={(e) => setEditableAnalysis(prev => prev ? { ...prev, fatG: parseFloat(e.target.value) || 0 } : null)}
                      className="w-full px-3 py-2 border border-white/30 rounded-lg bg-white/10 text-white placeholder-white/50 focus:ring-2 focus:ring-white/50 focus:border-transparent"
                    />
                  </div>
                </div>
              </>
            ) : (
              <>
                <h4 className="font-semibold text-white text-lg mb-3">{editableAnalysis?.food}</h4>
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                  <div>
                    <span className="text-white font-semibold">{editableAnalysis?.calories}</span>
                    <span className="text-white/80"> cal</span>
                  </div>
                  <div>
                    <span className="text-white font-semibold">{editableAnalysis?.proteinG}g</span>
                    <span className="text-white/80"> protein</span>
                  </div>
                  <div>
                    <span className="text-white font-semibold">{editableAnalysis?.carbsG}g</span>
                    <span className="text-white/80"> carbs</span>
                  </div>
                  <div>
                    <span className="text-white font-semibold">{editableAnalysis?.fatG}g</span>
                    <span className="text-white/80"> fat</span>
                  </div>
                </div>
                {analysis.confidence && (
                  <div className="mt-2 text-xs text-white/70">
                    Confidence: {(analysis.confidence * 100).toFixed(0)}%
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      )}

      {/* Manual Entry Form */}
      {manualMode && (
        <div className="bg-white/10 backdrop-blur-sm rounded-lg shadow-lg p-6 border border-white/20 space-y-4">
          <h3 className="text-lg font-semibold text-white">Manual Entry</h3>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="md:col-span-2">
              <label className="block text-sm font-medium text-white/90 mb-1">
                Food Name
              </label>
              <input
                type="text"
                value={manualData.foodName}
                onChange={(e) => setManualData(prev => ({ ...prev, foodName: e.target.value }))}
                className="w-full px-3 py-2 border border-white/30 rounded-lg bg-white/10 text-white placeholder-white/50 focus:ring-2 focus:ring-white/50 focus:border-transparent"
                placeholder="e.g., Grilled Chicken Salad"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-white/90 mb-1">
                Calories
              </label>
              <input
                type="number"
                value={manualData.calories}
                onChange={(e) => setManualData(prev => ({ ...prev, calories: e.target.value }))}
                className="w-full px-3 py-2 border border-white/30 rounded-lg bg-white/10 text-white placeholder-white/50 focus:ring-2 focus:ring-white/50 focus:border-transparent"
                placeholder="0"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-white/90 mb-1">
                Protein (g)
              </label>
              <input
                type="number"
                step="0.1"
                value={manualData.protein}
                onChange={(e) => setManualData(prev => ({ ...prev, protein: e.target.value }))}
                className="w-full px-3 py-2 border border-white/30 rounded-lg bg-white/10 text-white placeholder-white/50 focus:ring-2 focus:ring-white/50 focus:border-transparent"
                placeholder="0"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-white/90 mb-1">
                Carbs (g)
              </label>
              <input
                type="number"
                step="0.1"
                value={manualData.carbs}
                onChange={(e) => setManualData(prev => ({ ...prev, carbs: e.target.value }))}
                className="w-full px-3 py-2 border border-white/30 rounded-lg bg-white/10 text-white placeholder-white/50 focus:ring-2 focus:ring-white/50 focus:border-transparent"
                placeholder="0"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-white/90 mb-1">
                Fat (g)
              </label>
              <input
                type="number"
                step="0.1"
                value={manualData.fat}
                onChange={(e) => setManualData(prev => ({ ...prev, fat: e.target.value }))}
                className="w-full px-3 py-2 border border-white/30 rounded-lg bg-white/10 text-white placeholder-white/50 focus:ring-2 focus:ring-white/50 focus:border-transparent"
                placeholder="0"
              />
            </div>
          </div>
        </div>
      )}

      {/* Action Buttons */}
      {(editableAnalysis || manualMode) && (
        <div className="flex gap-4">
          <button
            onClick={saveMealLog}
            disabled={saving || (!editableAnalysis && !manualMode)}
            className="flex-1 bg-white/20 text-white px-6 py-3 rounded-lg hover:bg-white/30 transition-colors disabled:opacity-50 disabled:cursor-not-allowed font-medium"
          >
            {saving ? 'Saving...' : '💾 Save Meal'}
          </button>

          <button
            onClick={resetForm}
            className="px-6 py-3 border border-white/30 text-white rounded-lg hover:bg-white/10 transition-colors font-medium"
          >
            Start Over
          </button>
        </div>
      )}

      {/* Share Button - Show after meal is analyzed or manually entered */}
      {(editableAnalysis || (manualMode && manualData.foodName)) && (
        <div className="text-center">
          <button
            onClick={async () => {
              // Prepare meal data for sharing
              const mealData: HealthLog = {
                id: `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
                userId,
                type: 'meal',
                timestamp: new Date(),
                foodName: editableAnalysis?.food || manualData.foodName,
                calories: editableAnalysis?.calories || parseInt(manualData.calories) || 0,
                protein: editableAnalysis?.proteinG || parseFloat(manualData.protein) || 0,
                carbs: editableAnalysis?.carbsG || parseFloat(manualData.carbs) || 0,
                fat: editableAnalysis?.fatG || parseFloat(manualData.fat) || 0,
                ...(previewUrl && { photoUrl: previewUrl }),
              }
              setMealToShare(mealData)
              await loadFriends()
              setShowShareDialog(true)
            }}
            className="px-6 py-2 bg-white/20 text-white rounded-lg hover:bg-white/30 transition-colors font-medium flex items-center gap-2 mx-auto"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8.684 13.342C8.885 12.938 9 12.482 9 12c0-.482-.115-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z" />
            </svg>
            Share Meal
          </button>
        </div>
      )}

      {/* Share Dialog */}
      {showShareDialog && mealToShare && (
        <SharePlatformDialog
          onDismiss={() => {
            setShowShareDialog(false)
            setMealToShare(null)
          }}
          showFriendsOption={true}
          friends={friends}
          onShareWithFriends={async (friendIds) => {
            if (friendIds.length === 0) {
              toast.error('Please select at least one friend')
              return
            }
            try {
              await FirebaseService.shareMealWithFriends(userId, mealToShare, friendIds)
              toast.success(`Meal shared with ${friendIds.length} ${friendIds.length === 1 ? 'friend' : 'friends'}!`)
              setShowShareDialog(false)
              setMealToShare(null)
            } catch (error) {
              console.error('Error sharing meal with friends:', error)
              toast.error('Failed to share meal with friends')
            }
          }}
          onShareToPlatform={async (platform) => {
            setShowShareDialog(false)
            if (!mealToShare || mealToShare.type !== 'meal') return
            try {
              const shareService = ShareService.getInstance()
              const mealLog = mealToShare as any // Type assertion since we know it's a meal log
              const mealName = mealLog.foodName || 'My Meal'
              const shareText = `🍽️ ${mealName}\n\n${mealLog.calories || 0} cal • ${mealLog.protein || 0}g protein • ${mealLog.carbs || 0}g carbs • ${mealLog.fat || 0}g fat\n\nTracked with Coachie → coachieai.playspace.games`
              
              // Share to social media
              if (platform) {
                if (navigator.share) {
                  await navigator.share({
                    title: mealName,
                    text: shareText,
                    ...(mealLog.photoUrl && { url: mealLog.photoUrl }),
                  })
                } else {
                  await navigator.clipboard.writeText(shareText)
                  toast.success('Meal details copied to clipboard!')
                }
              } else {
                // Native share
                if (navigator.share) {
                  await navigator.share({
                    title: mealName,
                    text: shareText,
                    ...(mealLog.photoUrl && { url: mealLog.photoUrl }),
                  })
                } else {
                  await navigator.clipboard.writeText(shareText)
                  toast.success('Meal details copied to clipboard!')
                }
              }
            } catch (error) {
              console.error('Error sharing meal:', error)
              toast.error('Failed to share meal')
            }
            setMealToShare(null)
          }}
          photoUrl={(mealToShare as any)?.photoUrl || previewUrl}
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
  )
}
