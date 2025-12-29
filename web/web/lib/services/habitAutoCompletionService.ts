/**
 * Habit Auto-Completion Service (Web Version)
 * Ported from Android HabitAutoCompletionService.kt
 * Automatically completes habits based on related activities
 */

import HabitRepository from './habitRepository'

class HabitAutoCompletionService {
  private static instance: HabitAutoCompletionService

  private constructor() {}

  static getInstance(): HabitAutoCompletionService {
    if (!HabitAutoCompletionService.instance) {
      HabitAutoCompletionService.instance = new HabitAutoCompletionService()
    }
    return HabitAutoCompletionService.instance
  }

  /**
   * Called when a meal is logged - auto-complete related habits
   */
  async onMealLogged(userId: string): Promise<void> {
    try {
      const habitRepo = HabitRepository.getInstance()
      const habits = await habitRepo.getHabits(userId)
      
      // Find habits related to meal logging
      const mealHabits = habits.filter(
        (h) =>
          h.title.toLowerCase().includes('meal') ||
          h.title.toLowerCase().includes('food') ||
          h.title.toLowerCase().includes('eat')
      )

      // Auto-complete if not already completed today
      const today = new Date()
      today.setHours(0, 0, 0, 0)

      for (const habit of mealHabits) {
        if (!habit.lastCompleted || new Date(habit.lastCompleted) < today) {
          await habitRepo.completeHabit(userId, habit.id)
        }
      }
    } catch (error) {
      console.error('Error auto-completing meal habits:', error)
    }
  }

  /**
   * Called when a workout is logged - auto-complete related habits
   */
  async onWorkoutLogged(userId: string, durationMin?: number, caloriesBurned?: number): Promise<void> {
    try {
      const habitRepo = HabitRepository.getInstance()
      const habits = await habitRepo.getHabits(userId)

      const workoutHabits = habits.filter(
        (h) =>
          h.isActive &&
          (h.title.toLowerCase().includes('workout') ||
          h.title.toLowerCase().includes('exercise') ||
          h.title.toLowerCase().includes('gym') ||
          h.title.toLowerCase().includes('fitness'))
      )

      const today = new Date()
      today.setHours(0, 0, 0, 0)

      for (const habit of workoutHabits) {
        if (!habit.lastCompleted || new Date(habit.lastCompleted) < today) {
          await habitRepo.completeHabit(userId, habit.id, 1)
        }
      }
    } catch (error) {
      console.error('Error auto-completing workout habits:', error)
    }
  }

  /**
   * Called when water is logged - auto-complete water-related habits
   */
  async onWaterLogged(userId: string, amountMl: number): Promise<void> {
    try {
      const habitRepo = HabitRepository.getInstance()
      const habits = await habitRepo.getHabits(userId)

      const waterHabits = habits.filter(
        (h) =>
          h.isActive &&
          (h.title.toLowerCase().includes('water') ||
          h.title.toLowerCase().includes('hydrate') ||
          h.title.toLowerCase().includes('drink'))
      )

      const today = new Date()
      today.setHours(0, 0, 0, 0)

      for (const habit of waterHabits) {
        const titleLower = habit.title.toLowerCase()
        if (titleLower.includes('water') || titleLower.includes('hydrate') || titleLower.includes('drink')) {
          // Check if target is met
          const totalMlToday = amountMl // Simplified - should get total from daily log
          const totalGlassesToday = totalMlToday / 240.0
          
          const targetMet = habit.unit?.toLowerCase().includes('glass') || habit.unit?.toLowerCase().includes('cup')
            ? totalGlassesToday >= (habit.targetValue || 1)
            : totalMlToday >= (habit.targetValue || 1)
          
          if (targetMet && (!habit.lastCompleted || new Date(habit.lastCompleted) < today)) {
            await habitRepo.completeHabit(userId, habit.id, habit.targetValue || 1)
          }
        }
      }
    } catch (error) {
      console.error('Error auto-completing water habits:', error)
    }
  }

  /**
   * Called when sleep is logged - auto-complete sleep-related habits
   */
  async onSleepLogged(userId: string, hours: number): Promise<void> {
    try {
      const habitRepo = HabitRepository.getInstance()
      const habits = await habitRepo.getHabits(userId)

      const sleepHabits = habits.filter(
        (h) =>
          h.isActive &&
          (h.title.toLowerCase().includes('sleep') ||
          h.title.toLowerCase().includes('rest'))
      )

      const today = new Date()
      today.setHours(0, 0, 0, 0)

      for (const habit of sleepHabits) {
        // Check if sleep hours meet target
        const targetMet = hours >= (habit.targetValue || 7)
        
        if (targetMet && (!habit.lastCompleted || new Date(habit.lastCompleted) < today)) {
          await habitRepo.completeHabit(userId, habit.id, 1)
        }
      }
    } catch (error) {
      console.error('Error auto-completing sleep habits:', error)
    }
  }
}

export default HabitAutoCompletionService
