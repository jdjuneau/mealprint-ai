/**
 * Supplement lookup service for barcode scanning
 * Uses multiple providers to find supplement information
 */

interface ParsedSupplementBarcode {
  supplementName: string
  nutrients: Record<string, number>
  rawText?: string
  confidence: number
}

class SupplementLookupService {
  /**
   * Lookup supplement data by barcode
   */
  async lookup(barcode: string): Promise<ParsedSupplementBarcode | null> {
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
  private async lookupOpenFoodFacts(barcode: string): Promise<ParsedSupplementBarcode | null> {
    try {
      const response = await fetch(`https://world.openfoodfacts.org/api/v2/product/${barcode}.json`)
      if (!response.ok) return null

      const data = await response.json()
      const product = data.product
      if (!product) return null

      // Check if this is a supplement (categories, product_name)
      const categories = product.categories_tags || []
      const isSupplement = categories.some((cat: string) => 
        cat.includes('supplement') || 
        cat.includes('vitamin') || 
        cat.includes('mineral') ||
        cat.includes('dietary')
      )

      if (!isSupplement) {
        // Not a supplement, skip
        return null
      }

      const nutriments = product.nutriments
      if (!nutriments) return null

      // Extract supplement name
      const supplementName = product.product_name || product.abbreviated_product_name || 'Unknown Supplement'

      // Extract nutrients (vitamins, minerals)
      const nutrients: Record<string, number> = {}
      
      // Common supplement nutrients
      const nutrientMappings: Record<string, string> = {
        'vitamin-a': 'Vitamin A',
        'vitamin-c': 'Vitamin C',
        'vitamin-d': 'Vitamin D',
        'vitamin-e': 'Vitamin E',
        'vitamin-k': 'Vitamin K',
        'vitamin-b1': 'Thiamin',
        'vitamin-b2': 'Riboflavin',
        'vitamin-b3': 'Niacin',
        'vitamin-b6': 'Vitamin B6',
        'vitamin-b9': 'Folate',
        'vitamin-b12': 'Vitamin B12',
        'biotin': 'Biotin',
        'pantothenic-acid': 'Pantothenic Acid',
        'calcium': 'Calcium',
        'iron': 'Iron',
        'magnesium': 'Magnesium',
        'zinc': 'Zinc',
        'copper': 'Copper',
        'manganese': 'Manganese',
        'selenium': 'Selenium',
        'chromium': 'Chromium',
        'molybdenum': 'Molybdenum',
        'iodine': 'Iodine',
        'potassium': 'Potassium',
        'phosphorus': 'Phosphorus',
      }

      // Extract nutrients from nutriments object
      for (const [key, mappedName] of Object.entries(nutrientMappings)) {
        const value = nutriments[key] || nutriments[`${key}_100g`] || nutriments[`${key}_serving`]
        if (value && typeof value === 'number' && value > 0) {
          nutrients[mappedName] = value
        }
      }

      // Also check for any other vitamin/mineral patterns
      Object.keys(nutriments).forEach((key) => {
        const lowerKey = key.toLowerCase()
        if ((lowerKey.includes('vitamin') || lowerKey.includes('mineral')) && !nutrients[key]) {
          const value = nutriments[key]
          if (value && typeof value === 'number' && value > 0) {
            // Format the key nicely
            const formattedKey = key
              .replace(/_/g, ' ')
              .replace(/\b\w/g, (l) => l.toUpperCase())
            nutrients[formattedKey] = value
          }
        }
      })

      if (Object.keys(nutrients).length === 0) {
        return null // No nutrients found
      }

      return {
        supplementName,
        nutrients,
        rawText: `Found supplement: ${supplementName}`,
        confidence: 0.8
      }
    } catch (error) {
      console.error('Error looking up supplement in OpenFoodFacts:', error)
      return null
    }
  }
}

export default new SupplementLookupService()
