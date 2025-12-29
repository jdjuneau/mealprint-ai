/**
 * Food lookup service for barcode scanning
 * Uses multiple providers to find food product information
 */

interface ParsedFoodBarcode {
  name: string
  calories: number
  protein: number
  carbs: number
  fat: number
  sugar?: number
  addedSugar?: number
  servingSize?: string
  rawText: string
  confidence: number
}

class FoodLookupService {
  /**
   * Lookup food data by barcode
   */
  async lookup(barcode: string): Promise<ParsedFoodBarcode | null> {
    // Try OpenFoodFacts first (free, no API key needed)
    const openFoodFactsResult = await this.lookupOpenFoodFacts(barcode)
    if (openFoodFactsResult) {
      return openFoodFactsResult
    }

    // Could add other providers here (UPCItemDB, Nutritionix) if API keys are available
    // For now, we'll use OpenFoodFacts as the primary source

    return null
  }

  /**
   * Lookup using OpenFoodFacts API
   */
  private async lookupOpenFoodFacts(barcode: string): Promise<ParsedFoodBarcode | null> {
    try {
      const response = await fetch(`https://world.openfoodfacts.org/api/v2/product/${barcode}.json`)
      if (!response.ok) return null

      const data = await response.json()
      const product = data.product
      if (!product) return null

      const nutriments = product.nutriments
      if (!nutriments) return null

      // Extract nutrition data
      const calories = nutriments['energy-kcal_100g'] || nutriments['energy-kcal'] || 0
      const protein = nutriments['proteins_100g'] || nutriments.proteins || 0
      const carbs = nutriments['carbohydrates_100g'] || nutriments.carbohydrates || 0
      const fat = nutriments['fat_100g'] || nutriments.fat || 0
      const sugar = nutriments['sugars_100g'] || nutriments.sugars || 0
      const addedSugar = nutriments['added-sugars_100g'] || nutriments['added-sugars'] || 0

      // Check if we have valid nutrition data
      if (calories === 0 && protein === 0 && carbs === 0 && fat === 0) {
        return null
      }

      // Get serving size
      const servingSize = product.serving_size || 
        (product.serving_quantity ? `${product.serving_quantity}${product.serving_unit || 'g'}` : null)

      // Estimate sugar if not available
      const estimatedSugar = this.estimateSugarFromCarbs(carbs, product.product_name || '')

      return {
        name: product.product_name || 'Unknown Product',
        calories: Math.round(calories),
        protein: Math.round(protein * 10) / 10,
        carbs: Math.round(carbs * 10) / 10,
        fat: Math.round(fat * 10) / 10,
        sugar: sugar > 0 ? Math.round(sugar * 10) / 10 : estimatedSugar,
        addedSugar: addedSugar > 0 ? Math.round(addedSugar * 10) / 10 : 0,
        servingSize,
        rawText: `Barcode ${barcode} via Open Food Facts`,
        confidence: 0.85
      }
    } catch (error) {
      console.error('OpenFoodFacts lookup error:', error)
      return null
    }
  }

  /**
   * Estimate sugar content from carbs when sugar data is not available
   */
  private estimateSugarFromCarbs(carbs: number, foodName: string): number {
    if (carbs <= 0) return 0

    const nameLower = foodName.toLowerCase()

    // High sugar foods
    const highSugarKeywords = [
      'candy', 'chocolate', 'cookie', 'cake', 'donut', 'muffin', 'pastry',
      'soda', 'juice', 'drink', 'beverage', 'sweet', 'syrup', 'honey',
      'jam', 'jelly', 'preserve', 'marmalade', 'fruit', 'berries'
    ]
    if (highSugarKeywords.some(keyword => nameLower.includes(keyword))) {
      return Math.round(carbs * 0.85 * 10) / 10
    }

    // Medium sugar foods
    const mediumSugarKeywords = [
      'bread', 'cereal', 'crackers', 'granola', 'bar', 'snack',
      'yogurt', 'milk', 'cream', 'ice cream', 'frozen'
    ]
    if (mediumSugarKeywords.some(keyword => nameLower.includes(keyword))) {
      return Math.round(carbs * 0.40 * 10) / 10
    }

    // Low sugar foods
    const lowSugarKeywords = [
      'rice', 'pasta', 'quinoa', 'oats', 'potato', 'sweet potato',
      'chicken', 'beef', 'pork', 'fish', 'salmon', 'turkey',
      'broccoli', 'spinach', 'lettuce', 'cucumber', 'tomato', 'pepper'
    ]
    if (lowSugarKeywords.some(keyword => nameLower.includes(keyword))) {
      return Math.round(carbs * 0.10 * 10) / 10
    }

    // Default estimate
    return Math.round(carbs * 0.30 * 10) / 10
  }
}

export default new FoodLookupService()
export type { ParsedFoodBarcode }
