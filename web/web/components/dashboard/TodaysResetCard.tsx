'use client'

import { useState, useEffect } from 'react'
import { collection, query, where, getDocs, doc, updateDoc, addDoc, Timestamp, serverTimestamp } from 'firebase/firestore'
import { db } from '../../lib/firebase'

interface TodaysFocusTask {
  id: string
  title: string
  description: string
  type: 'HEALTH_LOG' | 'HABIT' | 'WELLNESS'
  actionType?: string
  actionData?: Record<string, any>
  date?: string
  completedAt?: any // Firestore Timestamp or Date - null means not completed
}

interface TodaysResetCardProps {
  userId: string
  refreshTrigger?: string
  onNavigateToMealLog: () => void
  onNavigateToWaterLog: () => void
  onNavigateToWeightLog: () => void
  onNavigateToSleepLog: () => void
  onNavigateToWorkoutLog: () => void
  onNavigateToSupplementLog: () => void
  onNavigateToJournal: () => void
  onNavigateToMeditation: () => void
  onNavigateToHabits: () => void
  onNavigateToHealthTracking: () => void
  onNavigateToWellness: () => void
  onNavigateToBreathingExercises: () => void
  onNavigateToHabitTimer?: (habitId: string, title: string, duration: number) => void
}

export default function TodaysResetCard({
  userId,
  refreshTrigger,
  onNavigateToMealLog,
  onNavigateToWaterLog,
  onNavigateToWeightLog,
  onNavigateToSleepLog,
  onNavigateToWorkoutLog,
  onNavigateToSupplementLog,
  onNavigateToJournal,
  onNavigateToMeditation,
  onNavigateToHabits,
  onNavigateToHealthTracking,
  onNavigateToWellness,
  onNavigateToBreathingExercises,
  onNavigateToHabitTimer
}: TodaysResetCardProps) {
  const [tasks, setTasks] = useState<TodaysFocusTask[]>([])
  const [loading, setLoading] = useState(true)
  const [currentTaskIndex, setCurrentTaskIndex] = useState(0)

  const today = new Date().toISOString().split('T')[0]

  useEffect(() => {
    loadTasks()
  }, [userId, today, refreshTrigger])

  const loadTasks = async () => {
    if (!userId) return
    setLoading(true)
    try {
      // First check if any tasks exist for today (including completed)
      const allTasksRef = collection(db, 'users', userId, 'todaysFocusTasks')
      const checkTasksQuery = query(allTasksRef, where('date', '==', today))
      const checkTasksSnapshot = await getDocs(checkTasksQuery)
      
      // If no tasks exist, generate them
      if (checkTasksSnapshot.empty) {
        console.log('No tasks found for today, generating tasks...')
        await generateTasksIfNeeded()
        // Wait a moment for Firestore to propagate, then reload
        await new Promise(resolve => setTimeout(resolve, 1000))
        // Reload tasks after generation
        const tasksRef = collection(db, 'users', userId, 'todaysFocusTasks')
        const tasksQuery = query(tasksRef, where('date', '==', today))
        const tasksSnapshot = await getDocs(tasksQuery)
        const loadedTasks: TodaysFocusTask[] = []
        tasksSnapshot.forEach((doc) => {
          const data = doc.data()
          // Filter out completed tasks (completedAt exists means it's completed)
          if (!data.completedAt) {
            loadedTasks.push({ id: doc.id, ...data } as TodaysFocusTask)
          }
        })
        setTasks(loadedTasks)
        if (currentTaskIndex >= loadedTasks.length && loadedTasks.length > 0) {
          setCurrentTaskIndex(0)
        }
        return // Exit early after generating and loading
      }
      
      // Now load incomplete tasks (Android uses completedAt field - null means not completed)
      const tasksRef = collection(db, 'users', userId, 'todaysFocusTasks')
      const tasksQuery = query(tasksRef, where('date', '==', today))
      const tasksSnapshot = await getDocs(tasksQuery)
      const loadedTasks: TodaysFocusTask[] = []
      tasksSnapshot.forEach((doc) => {
        const data = doc.data()
        // Filter out completed tasks (completedAt exists means it's completed)
        if (!data.completedAt) {
          loadedTasks.push({ id: doc.id, ...data } as TodaysFocusTask)
        }
      })
      setTasks(loadedTasks)
      if (currentTaskIndex >= loadedTasks.length && loadedTasks.length > 0) {
        setCurrentTaskIndex(0)
      }
    } catch (error) {
      console.error('Error loading tasks:', error)
    } finally {
      setLoading(false)
    }
  }

  const generateTasksIfNeeded = async () => {
    if (!userId) return
    
    try {
      // Always use client-side generation (matching Android's TodaysFocusTaskGenerator)
      // This ensures tasks are generated immediately and consistently
      console.log('Generating Today\'s Focus tasks client-side...')
      await generateTasksClientSide()
    } catch (error) {
      console.error('Error generating tasks:', error)
      // Don't throw - just log the error so the UI doesn't break
    }
  }

  const generateTasksClientSide = async () => {
    if (!userId) return
    
    try {
      const { FirebaseService } = await import('../../lib/services/firebase')
      const HabitRepository = (await import('../../lib/services/habitRepository')).default
      const habitRepo = HabitRepository.getInstance()
      
      const userProfile = await FirebaseService.getUserProfile(userId)
      if (!userProfile) return
      
      // Load habits
      const allHabits = await habitRepo.getHabits(userId)
      const activeHabits = allHabits.filter(h => h.isActive)
      
      // Load today's habit completions
      const todayStart = new Date()
      todayStart.setHours(0, 0, 0, 0)
      const todayEnd = new Date(todayStart)
      todayEnd.setDate(todayEnd.getDate() + 1)
      
      const completionsRef = collection(db, 'users', userId, 'habitCompletions')
      const completionsQuery = query(
        completionsRef,
        where('completedAt', '>=', todayStart),
        where('completedAt', '<', todayEnd)
      )
      const completionsSnap = await getDocs(completionsQuery)
      const completedHabitIds = new Set(completionsSnap.docs.map(doc => doc.data().habitId))
      
      // Get today's log to see what's missing
      const todayLog = await FirebaseService.getDailyLog(userId, today)
      const meals = (todayLog?.logs || []).filter((l: any) => l.type === 'meal')
      const workouts = (todayLog?.logs || []).filter((l: any) => l.type === 'workout')
      const waterLogs = (todayLog?.logs || []).filter((l: any) => l.type === 'water')
      const totalWater = (todayLog?.waterAmount || 0) + waterLogs.reduce((sum: number, log: any) => sum + (log.amount || 0), 0)
      const sleepLogs = (todayLog?.logs || []).filter((l: any) => l.type === 'sleep')
      const sleep = todayLog?.sleepHours || (sleepLogs.length > 0 ? (sleepLogs[0] as any).hours : 0)
      const weightLogs = (todayLog?.logs || []).filter((l: any) => l.type === 'weight')
      const journalEntries = (todayLog?.logs || []).filter((l: any) => l.type === 'journal')
      const meditationLogs = (todayLog?.logs || []).filter((l: any) => l.type === 'meditation' || l.type === 'mindful_session')
      
      const tasks: TodaysFocusTask[] = []
      let taskId = 1
      const MIN_TASKS = 7
      const MAX_TASKS = 9
      
      // 1. Health Log Tasks (2-3 tasks)
      if (meals.length === 0) {
        tasks.push({
          id: `task_${taskId++}`,
          title: 'Log Your First Meal',
          description: 'Start tracking your nutrition by logging breakfast or your first meal',
          type: 'HEALTH_LOG',
          actionType: 'LOG_MEAL',
          date: today
        })
      }
      
      if (totalWater < 1000) { // Less than 1L
        tasks.push({
          id: `task_${taskId++}`,
          title: 'Drink Water',
          description: 'Stay hydrated! Log at least one glass of water',
          type: 'HEALTH_LOG',
          actionType: 'LOG_WATER',
          date: today
        })
      }
      
      if (workouts.length === 0) {
        tasks.push({
          id: `task_${taskId++}`,
          title: 'Log a Workout',
          description: 'Track your physical activity for today',
          type: 'HEALTH_LOG',
          actionType: 'LOG_WORKOUT',
          date: today
        })
      }
      
      // 2. Habit Tasks (4-5 tasks from active habits)
      const incompleteHabits = activeHabits.filter(h => !completedHabitIds.has(h.id))
      incompleteHabits.slice(0, 5).forEach(habit => {
        tasks.push({
          id: `task_${taskId++}`,
          title: habit.title,
          description: habit.description || `Complete your ${habit.title} habit`,
          type: 'HABIT',
          actionType: 'COMPLETE_HABIT',
          actionData: {
            habitId: habit.id,
            habitTitle: habit.title
          },
          date: today
        })
      })
      
      // 3. Wellness Tasks (2-3 tasks)
      if (journalEntries.length === 0) {
        tasks.push({
          id: `task_${taskId++}`,
          title: 'Write in Journal',
          description: 'Take a moment to reflect and write in your journal',
          type: 'WELLNESS',
          actionType: 'START_JOURNAL',
          date: today
        })
      }
      
      if (meditationLogs.length === 0) {
        tasks.push({
          id: `task_${taskId++}`,
          title: 'Meditation or Mindfulness',
          description: 'Take 5-10 minutes for meditation or a mindfulness session',
          type: 'WELLNESS',
          actionType: 'START_MEDITATION',
          date: today
        })
      }
      
      // Add breathing exercise if we need more wellness tasks
      if (tasks.filter(t => t.type === 'WELLNESS').length < 2) {
        tasks.push({
          id: `task_${taskId++}`,
          title: 'Breathing Exercise',
          description: 'Practice deep breathing for stress relief and focus',
          type: 'WELLNESS',
          actionType: 'START_MINDFULNESS',
          date: today
        })
      }
      
      // Ensure we have 7-9 tasks
      const currentCount = tasks.length
      if (currentCount < MIN_TASKS) {
        const additionalNeeded = MIN_TASKS - currentCount
        
        // Add more health log tasks if needed
        if (sleepLogs.length === 0 && !tasks.some(t => t.actionType === 'LOG_SLEEP')) {
          tasks.push({
            id: `task_${taskId++}`,
            title: 'Log Sleep',
            description: 'Track your sleep duration and quality',
            type: 'HEALTH_LOG',
            actionType: 'LOG_SLEEP',
          date: today
          })
        }
        
        if (weightLogs.length === 0 && !tasks.some(t => t.actionType === 'LOG_WEIGHT')) {
          tasks.push({
            id: `task_${taskId++}`,
            title: 'Log Weight',
            description: 'Track your weight for today',
            type: 'HEALTH_LOG',
            actionType: 'LOG_WEIGHT',
          date: today
          })
        }
        
        // Add more habit tasks if available
        const remainingHabits = incompleteHabits.slice(5)
        remainingHabits.slice(0, additionalNeeded).forEach(habit => {
          tasks.push({
            id: `task_${taskId++}`,
            title: habit.title,
            description: habit.description || `Complete your ${habit.title} habit`,
            type: 'HABIT',
            actionType: 'COMPLETE_HABIT',
            actionData: {
              habitId: habit.id,
              habitTitle: habit.title
            },
          date: today
          })
        })
      }
      
      // Limit to MAX_TASKS
      const finalTasks = tasks.slice(0, MAX_TASKS)
      
      // Save tasks to Firestore (matching Android structure)
      for (const task of finalTasks) {
        const { id, ...taskData } = task
        await addDoc(collection(db, 'users', userId, 'todaysFocusTasks'), {
          ...taskData,
          userId,
          createdAt: Timestamp.now(),
          updatedAt: Timestamp.now(),
          completedAt: null, // Explicitly set to null (not completed)
          priority: 0 // Default priority
        })
      }
      
      console.log(`Generated ${finalTasks.length} tasks for today (matching Android: 7-9 tasks)`)
    } catch (error) {
      console.error('Error generating tasks client-side:', error)
    }
  }

  const handleTaskAction = async (task: TodaysFocusTask) => {
    const actionType = task.actionType || ''
    
    // For tasks that require navigation, navigate FIRST, then mark as complete
    // This allows user to complete the task in the destination screen
    const navigationActions = [
      'LOG_MEAL', 'LOG_WATER', 'LOG_WEIGHT', 'LOG_SLEEP', 
      'LOG_WORKOUT', 'LOG_SUPPLEMENT', 'START_JOURNAL', 
      'START_MEDITATION', 'VIEW_HABITS', 'VIEW_HEALTH_TRACKING', 
      'VIEW_WELLNESS', 'START_MINDFULNESS'
    ]
    
    if (navigationActions.includes(actionType)) {
      // Navigate first for specific tasks
    switch (actionType) {
      case 'LOG_MEAL':
        onNavigateToMealLog()
        break
      case 'LOG_WATER':
        onNavigateToWaterLog()
        break
      case 'LOG_WEIGHT':
        onNavigateToWeightLog()
        break
      case 'LOG_SLEEP':
        onNavigateToSleepLog()
        break
      case 'LOG_WORKOUT':
        onNavigateToWorkoutLog()
        break
      case 'LOG_SUPPLEMENT':
        onNavigateToSupplementLog()
        break
      case 'START_JOURNAL':
        onNavigateToJournal()
        break
      case 'START_MEDITATION':
        onNavigateToMeditation()
        break
      case 'VIEW_HABITS':
        onNavigateToHabits()
        break
      case 'VIEW_HEALTH_TRACKING':
        onNavigateToHealthTracking()
        break
      case 'VIEW_WELLNESS':
        onNavigateToWellness()
        break
      case 'START_MINDFULNESS':
        onNavigateToBreathingExercises()
        break
      }
      // Don't mark as complete immediately - let the user complete it in the destination
      return
    }
    
    // For COMPLETE_HABIT and other non-navigation tasks, mark as complete
    if (actionType === 'COMPLETE_HABIT' && task.actionData?.habitId) {
      try {
        // Complete the habit
        const HabitRepository = (await import('../../lib/services/habitRepository')).default
        const habitRepo = HabitRepository.getInstance()
        await habitRepo.completeHabit(userId, task.actionData.habitId, 1)
        
        // Mark task as completed
        const taskRef = doc(db, 'users', userId, 'todaysFocusTasks', task.id)
        await updateDoc(taskRef, { 
          completedAt: serverTimestamp(),
          updatedAt: serverTimestamp()
        })
        await loadTasks()
      } catch (error) {
        console.error('Error completing habit task:', error)
      }
    } else {
      // For other tasks without specific navigation, just mark as complete
      try {
        const taskRef = doc(db, 'users', userId, 'todaysFocusTasks', task.id)
        await updateDoc(taskRef, { 
          completedAt: serverTimestamp(),
          updatedAt: serverTimestamp()
        })
        await loadTasks()
      } catch (error) {
        console.error('Error completing task:', error)
      }
    }
  }

  if (loading) {
    return (
      <div className="bg-white/12 rounded-lg border border-blue-500/12 p-5">
        <div className="flex items-center justify-center py-8">
          <div className="w-8 h-8 border-2 border-white/60 border-t-transparent rounded-full animate-spin"></div>
        </div>
      </div>
    )
  }

  if (tasks.length === 0) {
    return (
      <div className="bg-white/12 rounded-lg border border-blue-500/12 p-5">
        <div className="text-center py-8">
          <svg className="w-10 h-10 text-white/80 mx-auto mb-3" fill="currentColor" viewBox="0 0 20 20">
            <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
          </svg>
          <h3 className="text-lg font-medium text-white mb-2">All Caught Up! 🎉</h3>
          <p className="text-sm text-white/80">You've completed all your reminders for today. Great job!</p>
        </div>
      </div>
    )
  }

  const currentTask = tasks[currentTaskIndex]
  const canGoNext = currentTaskIndex < tasks.length - 1
  const canGoPrevious = currentTaskIndex > 0

  return (
    <div className="bg-white/12 rounded-lg border border-blue-500/12 p-5">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-xl font-bold text-white">Today's Focus</h3>
        {tasks.length > 0 && (
          <div className="px-2 py-1 bg-blue-500/20 rounded-xl text-sm text-white/90">
            {tasks.length}
          </div>
        )}
      </div>

      {currentTask && (
        <div className="space-y-4">
          <div className="flex items-center gap-2 mb-2">
            <span className="text-2xl">
              {currentTask.type === 'HEALTH_LOG' ? '💪' : currentTask.type === 'HABIT' ? '✅' : '🧘'}
            </span>
            <div className="flex-1">
              <h4 className="font-semibold text-white">{currentTask.title}</h4>
              <p className="text-sm text-white/80">{currentTask.description}</p>
            </div>
          </div>

          <div className="flex gap-2">
            {canGoPrevious && (
              <button
                onClick={() => setCurrentTaskIndex(Math.max(0, currentTaskIndex - 1))}
                className="flex-1 px-4 py-2 border border-white/30 rounded-lg hover:bg-white/20 text-sm text-white"
              >
                ← Previous
              </button>
            )}
            <button
              onClick={() => handleTaskAction(currentTask)}
              className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 text-sm font-medium"
            >
              Complete
            </button>
            {canGoNext && (
              <button
                onClick={() => setCurrentTaskIndex(Math.min(tasks.length - 1, currentTaskIndex + 1))}
                className="flex-1 px-4 py-2 border border-white/30 rounded-lg hover:bg-white/20 text-sm text-white"
              >
                Next →
              </button>
            )}
          </div>

          <div className="text-center text-xs text-white/70">
            Task {currentTaskIndex + 1} of {tasks.length}
          </div>
        </div>
      )}
    </div>
  )
}
