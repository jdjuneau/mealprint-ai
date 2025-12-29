/**
 * Micronutrient targets and utilities
 * Matches Android MicronutrientType model
 */

export interface MicronutrientTarget {
  min?: number
  max?: number
}

export interface MicronutrientInfo {
  id: string
  displayName: string
  unit: 'mg' | 'mcg' | 'IU' | 'g'
  maleTarget: MicronutrientTarget
  femaleTarget: MicronutrientTarget
}

export const MICRONUTRIENT_TYPES: MicronutrientInfo[] = [
  { id: 'vitamin_a', displayName: 'Vitamin A', unit: 'mcg', maleTarget: { min: 900 }, femaleTarget: { min: 700 } },
  { id: 'vitamin_c', displayName: 'Vitamin C', unit: 'mg', maleTarget: { min: 90 }, femaleTarget: { min: 75 } },
  { id: 'vitamin_d', displayName: 'Vitamin D', unit: 'IU', maleTarget: { min: 600 }, femaleTarget: { min: 600 } },
  { id: 'vitamin_e', displayName: 'Vitamin E', unit: 'mg', maleTarget: { min: 15 }, femaleTarget: { min: 15 } },
  { id: 'vitamin_k', displayName: 'Vitamin K', unit: 'mcg', maleTarget: { min: 120 }, femaleTarget: { min: 90 } },
  { id: 'thiamin', displayName: 'Thiamin (B1)', unit: 'mg', maleTarget: { min: 1.2 }, femaleTarget: { min: 1.1 } },
  { id: 'riboflavin', displayName: 'Riboflavin (B2)', unit: 'mg', maleTarget: { min: 1.3 }, femaleTarget: { min: 1.1 } },
  { id: 'niacin', displayName: 'Niacin (B3)', unit: 'mg', maleTarget: { min: 16 }, femaleTarget: { min: 14 } },
  { id: 'vitamin_b6', displayName: 'Vitamin B6', unit: 'mg', maleTarget: { min: 1.3 }, femaleTarget: { min: 1.3 } },
  { id: 'folate', displayName: 'Folate (B9)', unit: 'mcg', maleTarget: { min: 400 }, femaleTarget: { min: 400 } },
  { id: 'vitamin_b12', displayName: 'Vitamin B12', unit: 'mcg', maleTarget: { min: 2.4 }, femaleTarget: { min: 2.4 } },
  { id: 'biotin', displayName: 'Biotin', unit: 'mcg', maleTarget: { min: 30 }, femaleTarget: { min: 30 } },
  { id: 'pantothenic_acid', displayName: 'Pantothenic Acid', unit: 'mg', maleTarget: { min: 5 }, femaleTarget: { min: 5 } },
  { id: 'calcium', displayName: 'Calcium', unit: 'mg', maleTarget: { min: 1000 }, femaleTarget: { min: 1000 } },
  { id: 'iron', displayName: 'Iron', unit: 'mg', maleTarget: { min: 8 }, femaleTarget: { min: 18 } },
  { id: 'magnesium', displayName: 'Magnesium', unit: 'mg', maleTarget: { min: 400 }, femaleTarget: { min: 310 } },
  { id: 'zinc', displayName: 'Zinc', unit: 'mg', maleTarget: { min: 11 }, femaleTarget: { min: 8 } },
  { id: 'copper', displayName: 'Copper', unit: 'mg', maleTarget: { min: 0.9 }, femaleTarget: { min: 0.9 } },
  { id: 'manganese', displayName: 'Manganese', unit: 'mg', maleTarget: { min: 2.3 }, femaleTarget: { min: 1.8 } },
  { id: 'selenium', displayName: 'Selenium', unit: 'mcg', maleTarget: { min: 55 }, femaleTarget: { min: 55 } },
  { id: 'chromium', displayName: 'Chromium', unit: 'mcg', maleTarget: { min: 35 }, femaleTarget: { min: 25 } },
  { id: 'molybdenum', displayName: 'Molybdenum', unit: 'mcg', maleTarget: { min: 45 }, femaleTarget: { min: 45 } },
  { id: 'iodine', displayName: 'Iodine', unit: 'mcg', maleTarget: { min: 150 }, femaleTarget: { min: 150 } },
  { id: 'potassium', displayName: 'Potassium', unit: 'mg', maleTarget: { min: 3400 }, femaleTarget: { min: 2600 } },
  { id: 'phosphorus', displayName: 'Phosphorus', unit: 'mg', maleTarget: { min: 700 }, femaleTarget: { min: 700 } },
]

export function getTargetForGender(type: MicronutrientInfo, gender: string | undefined): MicronutrientTarget {
  if (gender?.toLowerCase() === 'female' || gender?.toLowerCase() === 'f' || gender?.toLowerCase() === 'woman') {
    return type.femaleTarget
  }
  return type.maleTarget
}

export function formatAmount(value: number, unit: string): string {
  if (value >= 1000 && unit === 'mcg') {
    return `${(value / 1000).toFixed(2)} mg`
  }
  if (value >= 1000 && unit === 'mg') {
    return `${(value / 1000).toFixed(2)} g`
  }
  return value.toFixed(1)
}

export function calculateProgress(total: number, target: MicronutrientTarget): number {
  const denominator = target.max || target.min || 1
  if (denominator <= 0) return 0
  return Math.min((total / denominator) * 100, 100) // Cap at 100%
}
