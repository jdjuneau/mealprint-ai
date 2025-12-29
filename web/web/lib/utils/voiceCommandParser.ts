/**
 * Voice Command Parser for Web
 * Matches Android VoiceCommandParser functionality exactly
 * Parses natural language voice commands for logging various health activities
 */

export interface ParsedMealCommand {
  mealType?: string // breakfast, lunch, dinner, snack
  foods: FoodItem[]
  totalCalories?: number
}

export interface ParsedSupplementCommand {
  supplementName: string
  micronutrients: Record<string, number>
  quantity?: string // e.g., "2000 IU", "500 mg"
}

export interface ParsedWorkoutCommand {
  workoutType: string
  durationMinutes?: number
  distance?: number
  distanceUnit?: string // miles, km, meters
  caloriesBurned?: number
}

export interface ParsedWaterCommand {
  amount: number // in ml
  unit: string // ml, ounces, cups, liters
}

export interface ParsedWeightCommand {
  weight: number
  unit: string // kg, lbs, pounds
}

export interface ParsedSleepCommand {
  hours?: number
  quality?: string // "poor", "fair", "good", "excellent"
}

export interface ParsedMoodCommand {
  level: number // 1-5 scale
  emotions: string[]
}

export interface ParsedMeditationCommand {
  durationMinutes: number
  meditationType: string // "guided", "silent", etc.
}

export interface ParsedHabitCommand {
  habitName: string
  notes?: string
}

export interface ParsedJournalCommand {
  content: string
  mood?: string
}

export interface FoodItem {
  name: string
  quantity?: string // e.g., "2", "1 cup"
  unit?: string
}

export type VoiceCommandResult =
  | { type: 'meal'; parsedMeal: ParsedMealCommand }
  | { type: 'supplement'; parsedSupplement: ParsedSupplementCommand }
  | { type: 'workout'; parsedWorkout: ParsedWorkoutCommand }
  | { type: 'water'; parsedWater: ParsedWaterCommand }
  | { type: 'weight'; parsedWeight: ParsedWeightCommand }
  | { type: 'sleep'; parsedSleep: ParsedSleepCommand }
  | { type: 'mood'; parsedMood: ParsedMoodCommand }
  | { type: 'meditation'; parsedMeditation: ParsedMeditationCommand }
  | { type: 'habit'; parsedHabit: ParsedHabitCommand }
  | { type: 'journal'; parsedJournal: ParsedJournalCommand }
  | { type: 'parseError'; originalCommand: string; errorMessage: string }
  | { type: 'unknown'; command: string }

class VoiceCommandParser {
  /**
   * Main entry point - determines command type and parses accordingly
   */
  parseCommand(command: string): VoiceCommandResult {
    const lowerCommand = command.toLowerCase().trim()

    // Water logging - CHECK FIRST before meal logging to avoid conflicts
    if (
      lowerCommand.includes('water') ||
      (lowerCommand.includes('drink') &&
        !lowerCommand.includes('meal') &&
        !lowerCommand.includes('eat'))
    ) {
      return this.parseWaterCommand(command)
    }

    // Weight logging
    if (lowerCommand.includes('weight') || lowerCommand.includes('weigh')) {
      return this.parseWeightCommand(command)
    }

    // Sleep logging
    if (
      lowerCommand.includes('sleep') ||
      lowerCommand.includes('slept') ||
      (lowerCommand.includes('bed') && lowerCommand.includes('hours'))
    ) {
      return this.parseSleepCommand(command)
    }

    // Mood logging
    if (
      lowerCommand.includes('mood') ||
      lowerCommand.includes('feeling') ||
      (lowerCommand.includes('feel') &&
        (lowerCommand.includes('happy') ||
          lowerCommand.includes('sad') ||
          lowerCommand.includes('angry') ||
          lowerCommand.includes('anxious') ||
          lowerCommand.includes('stressed')))
    ) {
      return this.parseMoodCommand(command)
    }

    // Meditation logging
    if (
      lowerCommand.includes('meditation') ||
      lowerCommand.includes('meditate') ||
      lowerCommand.includes('mindfulness')
    ) {
      return this.parseMeditationCommand(command)
    }

    // Habit completion
    if (
      lowerCommand.includes('complete') &&
      (lowerCommand.includes('habit') ||
        lowerCommand.includes('task') ||
        lowerCommand.includes('done'))
    ) {
      return this.parseHabitCommand(command)
    }

    // Journal entry
    if (
      lowerCommand.includes('journal') ||
      lowerCommand.includes('journaling') ||
      (lowerCommand.includes('write') && lowerCommand.includes('about')) ||
      (lowerCommand.includes('log') && lowerCommand.includes('thought'))
    ) {
      return this.parseJournalCommand(command)
    }

    // Supplement logging
    if (
      lowerCommand.includes('supplement') ||
      lowerCommand.includes('vitamin') ||
      lowerCommand.includes('mineral') ||
      (lowerCommand.includes('add') &&
        (lowerCommand.includes('pill') ||
          lowerCommand.includes('capsule') ||
          lowerCommand.includes('tablet')))
    ) {
      return this.parseSupplementCommand(command)
    }

    // Workout logging
    if (
      lowerCommand.includes('workout') ||
      lowerCommand.includes('exercise') ||
      lowerCommand.includes('run') ||
      lowerCommand.includes('walk') ||
      lowerCommand.includes('bike') ||
      lowerCommand.includes('swim') ||
      lowerCommand.includes('lift') ||
      lowerCommand.includes('gym')
    ) {
      return this.parseWorkoutCommand(command)
    }

    // Meal logging - CHECK LAST and require explicit meal keywords
    if (
      lowerCommand.includes('log') &&
      (lowerCommand.includes('breakfast') ||
        lowerCommand.includes('lunch') ||
        lowerCommand.includes('dinner') ||
        lowerCommand.includes('snack') ||
        lowerCommand.includes('meal') ||
        lowerCommand.includes('eat') ||
        lowerCommand.includes('ate') ||
        lowerCommand.includes('food'))
    ) {
      return this.parseMealCommand(command)
    }

    return { type: 'unknown', command }
  }

  private parseMealCommand(command: string): VoiceCommandResult {
    try {
      const mealType = this.extractMealType(command)
      const foodItems = this.extractFoodItems(command)
      const estimatedCalories = this.estimateCalories(foodItems)

      return {
        type: 'meal',
        parsedMeal: {
          mealType,
          foods: foodItems,
          totalCalories: estimatedCalories,
        },
      }
    } catch (e: any) {
      console.error('Error parsing meal command:', command, e)
      return {
        type: 'parseError',
        originalCommand: command,
        errorMessage: 'Could not understand meal description',
      }
    }
  }

  private parseSupplementCommand(command: string): VoiceCommandResult {
    try {
      const supplementName = this.extractSupplementName(command)
      const quantity = this.extractQuantity(command)
      const micronutrients = this.mapToMicronutrients(supplementName, quantity)

      return {
        type: 'supplement',
        parsedSupplement: {
          supplementName,
          micronutrients,
          quantity,
        },
      }
    } catch (e: any) {
      console.error('Error parsing supplement command:', command, e)
      return {
        type: 'parseError',
        originalCommand: command,
        errorMessage: 'Could not understand supplement details',
      }
    }
  }

  private parseWorkoutCommand(command: string): VoiceCommandResult {
    try {
      const workoutType = this.extractWorkoutType(command)
      const duration = this.extractDuration(command)
      const distance = this.extractDistance(command)
      const calories = this.extractCaloriesBurned(command)

      return {
        type: 'workout',
        parsedWorkout: {
          workoutType,
          durationMinutes: duration,
          distance: distance?.distance,
          distanceUnit: distance?.unit,
          caloriesBurned: calories,
        },
      }
    } catch (e: any) {
      console.error('Error parsing workout command:', command, e)
      return {
        type: 'parseError',
        originalCommand: command,
        errorMessage: 'Could not understand workout details',
      }
    }
  }

  private parseWaterCommand(command: string): VoiceCommandResult {
    try {
      const { amount, unit } = this.extractWaterAmount(command)
      const amountInMl = this.convertToMl(amount, unit)

      return {
        type: 'water',
        parsedWater: {
          amount: amountInMl,
          unit,
        },
      }
    } catch (e: any) {
      console.error('Error parsing water command:', command, e)
      return {
        type: 'parseError',
        originalCommand: command,
        errorMessage: 'Could not understand water amount',
      }
    }
  }

  private parseWeightCommand(command: string): VoiceCommandResult {
    try {
      const { weight, unit } = this.extractWeight(command)

      return {
        type: 'weight',
        parsedWeight: {
          weight,
          unit,
        },
      }
    } catch (e: any) {
      console.error('Error parsing weight command:', command, e)
      return {
        type: 'parseError',
        originalCommand: command,
        errorMessage: 'Could not understand weight measurement',
      }
    }
  }

  private parseSleepCommand(command: string): VoiceCommandResult {
    try {
      const hours = this.extractSleepHours(command)
      const quality = this.extractSleepQuality(command)

      return {
        type: 'sleep',
        parsedSleep: {
          hours,
          quality,
        },
      }
    } catch (e: any) {
      console.error('Error parsing sleep command:', command, e)
      return {
        type: 'parseError',
        originalCommand: command,
        errorMessage: 'Could not understand sleep details',
      }
    }
  }

  private parseMoodCommand(command: string): VoiceCommandResult {
    try {
      const level = this.extractMoodLevel(command)
      const emotions = this.extractEmotions(command)

      return {
        type: 'mood',
        parsedMood: {
          level,
          emotions,
        },
      }
    } catch (e: any) {
      console.error('Error parsing mood command:', command, e)
      return {
        type: 'parseError',
        originalCommand: command,
        errorMessage: 'Could not understand mood',
      }
    }
  }

  private parseMeditationCommand(command: string): VoiceCommandResult {
    try {
      const duration = this.extractDuration(command) ?? 10 // Default to 10 minutes
      const meditationType = this.extractMeditationType(command)

      return {
        type: 'meditation',
        parsedMeditation: {
          durationMinutes: duration,
          meditationType,
        },
      }
    } catch (e: any) {
      console.error('Error parsing meditation command:', command, e)
      return {
        type: 'parseError',
        originalCommand: command,
        errorMessage: 'Could not understand meditation details',
      }
    }
  }

  private parseHabitCommand(command: string): VoiceCommandResult {
    try {
      const habitName = this.extractHabitName(command)
      const notes = this.extractNotes(command)

      return {
        type: 'habit',
        parsedHabit: {
          habitName,
          notes,
        },
      }
    } catch (e: any) {
      console.error('Error parsing habit command:', command, e)
      return {
        type: 'parseError',
        originalCommand: command,
        errorMessage: 'Could not understand habit name',
      }
    }
  }

  private parseJournalCommand(command: string): VoiceCommandResult {
    try {
      const content = this.extractJournalContent(command)
      const mood = this.extractMoodFromJournal(command)

      return {
        type: 'journal',
        parsedJournal: {
          content,
          mood,
        },
      }
    } catch (e: any) {
      console.error('Error parsing journal command:', command, e)
      return {
        type: 'parseError',
        originalCommand: command,
        errorMessage: 'Could not understand journal entry',
      }
    }
  }

  // Helper functions for parsing

  private extractMealType(command: string): string | undefined {
    const lower = command.toLowerCase()
    if (lower.includes('breakfast')) return 'breakfast'
    if (lower.includes('lunch')) return 'lunch'
    if (lower.includes('dinner')) return 'dinner'
    if (lower.includes('snack')) return 'snack'
    return undefined
  }

  private extractFoodItems(command: string): FoodItem[] {
    const cleanedCommand = command
      .toLowerCase()
      .replace(/\b(log|a|an|of|the|meal|breakfast|lunch|dinner|snack)\b/g, ' ')
      .trim()

    const separators = /[,]|\band\b|\bwith\b|\bplus\b/
    const parts = cleanedCommand.split(separators).map((p) => p.trim()).filter((p) => p.length > 0)

    if (parts.length === 0) {
      const singleFood = cleanedCommand.replace(/^\s*(log|a|an|of|the|meal)\s+/, '').trim()
      if (singleFood.length > 0) {
        return [this.extractSingleFoodItem(singleFood)]
      }
      return []
    }

    return parts.map((part) => this.extractSingleFoodItem(part))
  }

  private extractSingleFoodItem(part: string): FoodItem {
    const trimmedPart = part.trim()
    const quantityUnitRegex = /(\d+(?:\.\d+)?)?\s*(cup|cups|oz|ounce|ounces|lb|pound|pounds|g|gram|grams|kg|kilogram|kilograms|piece|pieces|slice|slices|tbsp|tablespoon|tablespoons|tsp|teaspoon|teaspoons)?\s*(.+)/i
    const match = quantityUnitRegex.exec(trimmedPart)

    if (match) {
      const quantity = match[1] || undefined
      const unit = match[2] || undefined
      const name = (match[3] || trimmedPart).trim()

      return {
        name: name || trimmedPart,
        quantity,
        unit,
      }
    }

    return {
      name: trimmedPart,
      quantity: undefined,
      unit: undefined,
    }
  }

  private estimateCalories(foods: FoodItem[]): number | undefined {
    let totalCalories = 0
    let hasEstimates = false

    foods.forEach((food) => {
      const calories = this.estimateFoodCalories(food)
      if (calories > 0) {
        totalCalories += calories
        hasEstimates = true
      }
    })

    return hasEstimates ? totalCalories : undefined
  }

  private estimateFoodCalories(food: FoodItem): number {
    const name = food.name.toLowerCase()
    const quantity = parseFloat(food.quantity || '1') || 1.0

    if (name.includes('egg')) return Math.round(quantity * 70)
    if (name.includes('toast') || name.includes('bread')) return Math.round(quantity * 80)
    if (name.includes('coffee')) return 5
    if (name.includes('apple')) return Math.round(quantity * 95)
    if (name.includes('banana')) return Math.round(quantity * 105)
    if (name.includes('rice') || name.includes('pasta')) return Math.round(quantity * 130)
    if (name.includes('chicken') || name.includes('meat')) return Math.round(quantity * 200)
    if (name.includes('fish')) return Math.round(quantity * 150)
    return 0
  }

  private extractSupplementName(command: string): string {
    const supplementKeywords = ['vitamin', 'mineral', 'supplement', 'pill', 'capsule', 'tablet']
    const words = command.split(/\s+/)
    const startIndex = words.findIndex((word) =>
      supplementKeywords.some((keyword) => word.toLowerCase().includes(keyword))
    )

    if (startIndex >= 0 && startIndex < words.length - 1) {
      return words.slice(startIndex + 1).join(' ')
    }

    return words.filter((w) => w.length > 2).pop() || 'Unknown Supplement'
  }

  private extractQuantity(command: string): string | undefined {
    const quantityRegex = /(\d+(?:\.\d+)?)\s*(mg|g|mcg|iu|units?|capsules?|tablets?)/i
    const match = quantityRegex.exec(command)
    return match ? match[0] : undefined
  }

  private mapToMicronutrients(name: string, quantity: string | undefined): Record<string, number> {
    const lowerName = name.toLowerCase()
    const quantityValue =
      quantity && /(\d+(?:\.\d+)?)/.exec(quantity)?.[1]
        ? parseFloat(/(\d+(?:\.\d+)?)/.exec(quantity)![1])
        : 1.0

    const result: Record<string, number> = {}
    if (lowerName.includes('vitamin d')) result['VITAMIN_D'] = quantityValue
    if (lowerName.includes('vitamin c')) result['VITAMIN_C'] = quantityValue
    if (lowerName.includes('calcium')) result['CALCIUM'] = quantityValue
    if (lowerName.includes('iron')) result['IRON'] = quantityValue
    if (lowerName.includes('magnesium')) result['MAGNESIUM'] = quantityValue
    if (lowerName.includes('zinc')) result['ZINC'] = quantityValue

    return result
  }

  private extractWorkoutType(command: string): string {
    const lower = command.toLowerCase()
    if (lower.includes('run') || lower.includes('running') || lower.includes('jog')) return 'Running'
    if (lower.includes('walk') || lower.includes('walking')) return 'Walking'
    if (lower.includes('bike') || lower.includes('cycling')) return 'Cycling'
    if (lower.includes('swim') || lower.includes('swimming')) return 'Swimming'
    if (lower.includes('lift') || lower.includes('weight')) return 'Weight Training'
    if (lower.includes('yoga')) return 'Yoga'
    return 'Other'
  }

  private extractDuration(command: string): number | undefined {
    const durationRegex = /(\d+)\s*(?:minutes?|mins?|hours?|hrs?)/i
    const match = durationRegex.exec(command)

    if (match) {
      const number = parseInt(match[1])
      const unit = match[2].toLowerCase()
      return unit.includes('hour') ? number * 60 : number
    }
    return undefined
  }

  private extractDistance(command: string): { distance: number; unit: string } | undefined {
    const distanceRegex = /(\d+(?:\.\d+)?)\s*(miles?|kilometers?|km|meters?|m)/i
    const match = distanceRegex.exec(command)

    if (match) {
      const distance = parseFloat(match[1])
      const unit = match[2].toLowerCase()
      let normalizedUnit = unit
      if (unit.includes('mile')) normalizedUnit = 'miles'
      else if (unit.includes('kilometer') || unit === 'km') normalizedUnit = 'km'
      else if (unit.includes('meter') || unit === 'm') normalizedUnit = 'meters'

      return { distance, unit: normalizedUnit }
    }
    return undefined
  }

  private extractCaloriesBurned(command: string): number | undefined {
    const calorieRegex = /(\d+)\s*(?:calories?|cal|kcal)/i
    const match = calorieRegex.exec(command)
    return match ? parseInt(match[1]) : undefined
  }

  private extractWaterAmount(command: string): { amount: number; unit: string } {
    const waterRegex = /(\d+(?:\.\d+)?)\s*(ounces?|oz|ml|milliliters?|cups?|liters?|l)/i
    const match = waterRegex.exec(command)

    if (match) {
      return {
        amount: parseFloat(match[1]),
        unit: match[2].toLowerCase(),
      }
    }
    return { amount: 8.0, unit: 'ounces' } // default to 8 oz glass
  }

  private convertToMl(amount: number, unit: string): number {
    switch (unit) {
      case 'ounces':
      case 'oz':
        return Math.round(amount * 29.5735)
      case 'cups':
      case 'cup':
        return Math.round(amount * 236.588)
      case 'liters':
      case 'liter':
      case 'l':
        return Math.round(amount * 1000)
      case 'ml':
      case 'milliliters':
      case 'milliliter':
        return Math.round(amount)
      default:
        return Math.round(amount * 29.5735) // default to ounces
    }
  }

  private extractWeight(command: string): { weight: number; unit: string } {
    const weightRegex = /(\d+(?:\.\d+)?)\s*(?:pounds?|lbs?|kilograms?|kgs?|kg)/i
    const match = weightRegex.exec(command)

    if (match) {
      const weight = parseFloat(match[1])
      const unitText = match[0].toLowerCase()
      const unit = unitText.includes('pound') || unitText.includes('lb') ? 'lbs' : 'kg'

      return { weight, unit }
    }
    throw new Error('Could not parse weight from command')
  }

  private extractSleepHours(command: string): number | undefined {
    const sleepRegex = /(\d+(?:\.\d+)?)\s*(?:hours?|hrs?)/i
    const match = sleepRegex.exec(command)
    return match ? parseFloat(match[1]) : undefined
  }

  private extractSleepQuality(command: string): string | undefined {
    const lower = command.toLowerCase()
    if (lower.includes('poor') || lower.includes('bad') || lower.includes('terrible')) return 'poor'
    if (lower.includes('fair') || lower.includes('okay') || lower.includes('ok')) return 'fair'
    if (lower.includes('good') || lower.includes('well')) return 'good'
    if (lower.includes('excellent') || lower.includes('great') || lower.includes('amazing')) return 'excellent'
    return undefined
  }

  private extractMoodLevel(command: string): number {
    const numberRegex = /(\d+)/
    const numberMatch = numberRegex.exec(command)
    if (numberMatch) {
      const num = parseInt(numberMatch[1])
      if (num >= 1 && num <= 5) return num
    }

    const lower = command.toLowerCase()
    if (lower.includes('terrible') || lower.includes('awful') || lower.includes('horrible')) return 1
    if (lower.includes('bad') || lower.includes('sad') || lower.includes('down')) return 2
    if (lower.includes('okay') || lower.includes('ok') || lower.includes('fine') || lower.includes('meh')) return 3
    if (lower.includes('good') || lower.includes('happy') || lower.includes('great')) return 4
    if (lower.includes('excellent') || lower.includes('amazing') || lower.includes('fantastic')) return 5
    return 3 // default to neutral
  }

  private extractEmotions(command: string): string[] {
    const lower = command.toLowerCase()
    const emotions: string[] = []

    const emotionKeywords: Record<string, string> = {
      happy: 'happy',
      sad: 'sad',
      angry: 'angry',
      anxious: 'anxious',
      stressed: 'stressed',
      calm: 'calm',
      excited: 'excited',
      tired: 'tired',
      energetic: 'energetic',
      frustrated: 'frustrated',
      content: 'content',
      worried: 'worried',
    }

    Object.entries(emotionKeywords).forEach(([keyword, emotion]) => {
      if (lower.includes(keyword)) {
        emotions.push(emotion)
      }
    })

    return emotions
  }

  private extractMeditationType(command: string): string {
    const lower = command.toLowerCase()
    if (lower.includes('guided')) return 'guided'
    if (lower.includes('silent')) return 'silent'
    if (lower.includes('walking')) return 'walking'
    if (lower.includes('body scan') || lower.includes('bodyscan')) return 'body_scan'
    if (lower.includes('loving kindness')) return 'loving_kindness'
    if (lower.includes('transcendental')) return 'transcendental'
    if (lower.includes('mindfulness')) return 'mindfulness'
    return 'guided' // default
  }

  private extractHabitName(command: string): string {
    const cleaned = command
      .toLowerCase()
      .replace(/\b(complete|completed|done|finished|finish|log|logged|mark|marked)\s+(the|a|an|my|our)?\s*/g, ' ')
      .replace(/\b(habit|task|activity)\s*/g, ' ')
      .trim()

    if (cleaned.length < 3) {
      const afterComplete = /(?:complete|done|finished|log)\s+(?:the|a|an|my)?\s*(.+)/i.exec(command)?.[1]?.trim()
      return afterComplete && afterComplete.length >= 3 ? afterComplete : 'Unknown Habit'
    }

    return cleaned.charAt(0).toUpperCase() + cleaned.slice(1)
  }

  private extractNotes(command: string): string | undefined {
    const notesPattern = /(?:with notes?|note:|because|reason:)\s*(.+)/i
    const match = notesPattern.exec(command)
    return match?.[1]?.trim() || undefined
  }

  private extractJournalContent(command: string): string {
    const cleaned = command
      .toLowerCase()
      .replace(/\b(journal|journaling|write|log)\s+(about|that|this|:)?\s*/g, ' ')
      .replace(/\b(thought|thoughts|entry|entry:)\s*/g, ' ')
      .trim()

    if (cleaned.length < 5) {
      const afterJournal = /(?:journal|journaling|write about|log thought)\s*:?\s*(.+)/i.exec(command)?.[1]?.trim()
      return afterJournal && afterJournal.length >= 5 ? afterJournal : command
    }

    return cleaned
  }

  private extractMoodFromJournal(command: string): string | undefined {
    const lower = command.toLowerCase()
    if (lower.includes('happy') || lower.includes('great') || lower.includes('excited')) return 'happy'
    if (lower.includes('sad') || lower.includes('down') || lower.includes('depressed')) return 'sad'
    if (lower.includes('anxious') || lower.includes('worried') || lower.includes('nervous')) return 'anxious'
    if (lower.includes('stressed') || lower.includes('overwhelmed')) return 'stressed'
    if (lower.includes('calm') || lower.includes('peaceful') || lower.includes('relaxed')) return 'calm'
    if (lower.includes('angry') || lower.includes('frustrated') || lower.includes('mad')) return 'angry'
    return undefined
  }
}

export const voiceCommandParser = new VoiceCommandParser()





