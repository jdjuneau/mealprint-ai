/**
 * Gemini Service (Web Version)
 * Ported from Android GeminiFlashClient.kt
 * Handles Gemini AI calls for free tier users
 */

import { functions } from '../firebase'
import { httpsCallable } from 'firebase/functions'

export interface GeminiMealAnalysis {
  food: string
  calories: number
  protein: number
  carbs: number
  fat: number
  confidence: number
  micronutrients?: Record<string, number>
}

export interface GeminiSupplementAnalysis {
  supplementName: string
  nutrients: Record<string, number>
}

class GeminiService {
  private static instance: GeminiService

  private constructor() {}

  static getInstance(): GeminiService {
    if (!GeminiService.instance) {
      GeminiService.instance = new GeminiService()
    }
    return GeminiService.instance
  }

  /**
   * Analyze meal image using Gemini Vision API
   * Calls Firebase Cloud Function which uses Gemini
   */
  async analyzeMealImage(imageFile: File, userId: string): Promise<GeminiMealAnalysis | null> {
    try {
      // Convert file to base64
      const base64Image = await this.fileToBase64(imageFile)

      // Call Firebase Cloud Function
      const analyzeMeal = httpsCallable(functions, 'analyzeMealImage')
      const result = await analyzeMeal({
        userId,
        image: base64Image,
        platform: 'web',
      })

      const data = result.data as any
      if (data && data.analysis) {
        return {
          food: data.analysis.food || data.analysis.foodName,
          calories: data.analysis.calories || 0,
          protein: data.analysis.protein || data.analysis.proteinG || 0,
          carbs: data.analysis.carbs || data.analysis.carbsG || 0,
          fat: data.analysis.fat || data.analysis.fatG || 0,
          confidence: data.analysis.confidence || 0.8,
          micronutrients: data.analysis.micronutrients,
        }
      }

      return null
    } catch (error) {
      console.error('Error analyzing meal image:', error)
      throw error
    }
  }

  /**
   * Analyze supplement image using Gemini Vision API
   */
  async analyzeSupplementImage(imageFile: File, userId: string): Promise<GeminiSupplementAnalysis | null> {
    try {
      const base64Image = await this.fileToBase64(imageFile)

      // Call Firebase Cloud Function
      const analyzeSupplement = httpsCallable(functions, 'analyzeSupplementImage')
      const result = await analyzeSupplement({
        userId,
        image: base64Image,
        platform: 'web',
      })

      const data = result.data as any
      if (data && data.analysis) {
        return {
          supplementName: data.analysis.supplementName || data.analysis.name,
          nutrients: data.analysis.nutrients || {},
        }
      }

      return null
    } catch (error) {
      console.error('Error analyzing supplement image:', error)
      throw error
    }
  }

  /**
   * Generate text response using Gemini
   */
  async generateText(prompt: string, userId: string): Promise<string | null> {
    try {
      // Call Firebase Cloud Function
      const generateText = httpsCallable(functions, 'generateGeminiText')
      const result = await generateText({
        userId,
        prompt,
        platform: 'web',
      })

      const data = result.data as any
      return data?.text || null
    } catch (error) {
      console.error('Error generating text:', error)
      return null
    }
  }

  /**
   * Convert file to base64 string
   */
  private fileToBase64(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => {
        const base64 = (reader.result as string).split(',')[1]
        resolve(base64)
      }
      reader.onerror = reject
      reader.readAsDataURL(file)
    })
  }
}

export default GeminiService
