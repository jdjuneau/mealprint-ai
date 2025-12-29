'use client'

import { useState, useRef, useEffect } from 'react'
import CoachieCard from './ui/CoachieCard'
import CoachieButton from './ui/CoachieButton'
import { speechRecognitionService } from '../lib/services/speechRecognitionService'
import { ttsService } from '../lib/services/ttsService'
import { voiceCommandParser, type VoiceCommandResult } from '../lib/utils/voiceCommandParser'
import { FirebaseService } from '../lib/services/firebase'
import HabitRepository from '../lib/services/habitRepository'
import type { HealthLog } from '../types'
import toast from 'react-hot-toast'

interface VoiceLoggingProps {
  userId: string
}

export default function VoiceLogging({ userId }: VoiceLoggingProps) {
  const [isListening, setIsListening] = useState(false)
  const [isProcessing, setIsProcessing] = useState(false)
  const [transcript, setTranscript] = useState('')
  const [interimTranscript, setInterimTranscript] = useState('')
  const [parsedResult, setParsedResult] = useState<VoiceCommandResult | null>(null)
  const [logs, setLogs] = useState<any[]>([])
  const [error, setError] = useState<string | null>(null)
  const [isSupported, setIsSupported] = useState(false)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    // Check if speech recognition is supported
    setIsSupported(speechRecognitionService.isSupported())
    if (!speechRecognitionService.isSupported()) {
      setError('Speech recognition is not supported in your browser. Please use Chrome, Edge, or Safari.')
    }
  }, [])

  const startListening = async () => {
    // Check microphone permission
    const hasPermission = await speechRecognitionService.checkMicrophonePermission()
    if (!hasPermission) {
      const granted = await speechRecognitionService.requestMicrophonePermission()
      if (!granted) {
        toast.error('Microphone permission is required for voice logging')
        return
      }
    }

    setError(null)
    setTranscript('')
    setInterimTranscript('')
    setParsedResult(null)

    speechRecognitionService.startListening(
      {
        continuous: false,
        interimResults: true,
        lang: 'en-US',
        maxAlternatives: 1,
      },
      {
        onStart: () => {
          setIsListening(true)
          setIsProcessing(false)
        },
        onResult: (transcript, isFinal) => {
          if (isFinal) {
            setTranscript(transcript)
            setInterimTranscript('')
            setIsProcessing(true)
            parseCommand(transcript)
          } else {
            setInterimTranscript(transcript)
          }
        },
        onError: (errorMessage) => {
          setIsListening(false)
          setIsProcessing(false)
          setError(errorMessage)
          toast.error(errorMessage)
        },
        onEnd: () => {
          setIsListening(false)
        },
      }
    )
  }

  const stopListening = () => {
    speechRecognitionService.stopListening()
    setIsListening(false)
  }

  const parseCommand = async (command: string) => {
    try {
      const result = voiceCommandParser.parseCommand(command)
      
      if (result.type === 'unknown' || result.type === 'parseError') {
        setError(result.type === 'parseError' ? result.errorMessage : 'Could not understand the command. Please try again with clearer speech.')
        ttsService.speak('I did not understand that. Please try again.', 'insights')
        setParsedResult(null)
      } else {
        setParsedResult(result)
        ttsService.speak('Command recognized. Please confirm to save.', 'insights')
      }
    } catch (error) {
      console.error('Error parsing command:', error)
      setError('Failed to parse command')
      setParsedResult(null)
    } finally {
      setIsProcessing(false)
    }
  }

  const saveLog = async () => {
    if (!parsedResult || parsedResult.type === 'unknown' || parsedResult.type === 'parseError') return

    setSaving(true)
    try {
      const today = new Date().toISOString().split('T')[0]
      const habitRepo = HabitRepository.getInstance()

      switch (parsedResult.type) {
        case 'meal': {
          const meal = parsedResult.parsedMeal
          const foodName = meal.foods.length > 0
            ? meal.foods.length === 1
              ? meal.foods[0].name.charAt(0).toUpperCase() + meal.foods[0].name.slice(1)
              : meal.foods.map(f => `${f.quantity || ''} ${f.unit || ''} ${f.name}`.trim()).join(', ')
            : meal.mealType
            ? meal.mealType.charAt(0).toUpperCase() + meal.mealType.slice(1)
            : 'Meal'

          // Estimate macros (simplified - matching Android logic)
          const calories = meal.totalCalories || estimateCalories(meal.foods)
          const protein = Math.round(calories * 0.25 / 4) // 25% protein
          const carbs = Math.round(calories * 0.45 / 4) // 45% carbs
          const fat = Math.round(calories * 0.30 / 9) // 30% fat

          const mealLog: HealthLog = {
            id: `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
            userId,
            type: 'meal',
            timestamp: new Date(),
            foodName,
            calories: calories || 400,
            protein,
            carbs,
            fat,
          }
          await FirebaseService.saveHealthLog(userId, today, mealLog)
          break
        }

        case 'water': {
          const water = parsedResult.parsedWater
          const waterLog: HealthLog = {
            id: `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
            userId,
            type: 'water',
            timestamp: new Date(),
            amount: water.amount,
          }
          await FirebaseService.saveHealthLog(userId, today, waterLog)
          break
        }

        case 'workout': {
          const workout = parsedResult.parsedWorkout
          const workoutLog: HealthLog = {
            id: `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
            userId,
            type: 'workout',
            timestamp: new Date(),
            workoutType: workout.workoutType,
            duration: workout.durationMinutes || 30,
            calories: workout.caloriesBurned || 0, // Default to 0 if not provided
          }
          await FirebaseService.saveHealthLog(userId, today, workoutLog)
          break
        }

        case 'weight': {
          const weight = parsedResult.parsedWeight
          const weightInKg = weight.unit === 'lbs' ? weight.weight * 0.453592 : weight.weight
          const weightLog: HealthLog = {
            id: `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
            userId,
            type: 'weight',
            timestamp: new Date(),
            weight: weightInKg,
          }
          await FirebaseService.saveHealthLog(userId, today, weightLog)
          break
        }

        case 'sleep': {
          const sleep = parsedResult.parsedSleep
          if (!sleep.hours) {
            throw new Error('Sleep hours required')
          }
          const sleepLog: HealthLog = {
            id: `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
            userId,
            type: 'sleep',
            timestamp: new Date(),
            hours: sleep.hours,
            quality: 3, // Default quality if not provided
          }
          await FirebaseService.saveHealthLog(userId, today, sleepLog)
          break
        }

        case 'mood': {
          const mood = parsedResult.parsedMood
          const moodLog: HealthLog = {
            id: `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
            userId,
            type: 'mood',
            timestamp: new Date(),
            level: mood.level,
            emotions: mood.emotions.length > 0 ? mood.emotions : undefined,
          }
          await FirebaseService.saveHealthLog(userId, today, moodLog)
          break
        }

        case 'supplement': {
          const supplement = parsedResult.parsedSupplement
          const quantity = typeof supplement.quantity === 'number' ? supplement.quantity : parseFloat(supplement.quantity || '0') || 0
          const supplementLog: HealthLog = {
            id: `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
            userId,
            type: 'supplement',
            timestamp: new Date(),
            supplementName: supplement.supplementName,
            nutrients: quantity > 0 ? { [supplement.supplementName]: quantity } : {},
          }
          await FirebaseService.saveHealthLog(userId, today, supplementLog)
          break
        }

        case 'meditation': {
          // Meditation logging not yet supported in HealthLog type
          // TODO: Add meditation log type or save via different method
          const meditation = parsedResult.parsedMeditation
          toast.success(`Meditation logged: ${meditation.durationMinutes} minutes of ${meditation.meditationType || 'meditation'}`)
          // Skip saving for now - meditation type not in HealthLog union
          break
        }

        case 'habit': {
          const habit = parsedResult.parsedHabit
          // Find habit by name and complete it
          const habits = await habitRepo.getHabits(userId)
          const matchingHabit = habits.find(
            h => h.title.toLowerCase().includes(habit.habitName.toLowerCase()) ||
                 habit.habitName.toLowerCase().includes(h.title.toLowerCase())
          )
          
          if (matchingHabit) {
            await habitRepo.completeHabit(userId, matchingHabit.id)
            toast.success(`Completed habit: ${matchingHabit.title}`)
          } else {
            throw new Error(`Habit "${habit.habitName}" not found`)
          }
          break
        }

        case 'journal': {
          const journal = parsedResult.parsedJournal
          const journalLog = {
            id: `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
            userId,
            type: 'journal' as const,
            timestamp: new Date(),
            content: journal.content,
            wordCount: journal.content.split(/\s+/).length,
            mood: journal.mood,
          }
          // Journal type not in HealthLog union, use 'as any' to bypass type check
          await FirebaseService.saveHealthLog(userId, today, journalLog as any)
          break
        }
      }

      setLogs(prev => [...prev, { parsedResult, transcript, timestamp: new Date() }])
      setParsedResult(null)
      setTranscript('')
      toast.success('Log saved successfully!')
      ttsService.speak('Log saved successfully!', 'wins')
    } catch (error: any) {
      console.error('Error saving log:', error)
      toast.error(error.message || 'Failed to save log')
      ttsService.speak('Failed to save log. Please try again.', 'insights')
    } finally {
      setSaving(false)
    }
  }

  // Helper function to estimate calories from food items
  const estimateCalories = (foods: any[]): number => {
    let total = 0
    foods.forEach(food => {
      const name = food.name.toLowerCase()
      const qty = parseFloat(food.quantity || '1') || 1
      if (name.includes('egg')) total += qty * 70
      else if (name.includes('toast') || name.includes('bread')) total += qty * 80
      else if (name.includes('coffee')) total += 5
      else if (name.includes('apple')) total += qty * 95
      else if (name.includes('banana')) total += qty * 105
      else if (name.includes('rice') || name.includes('pasta')) total += qty * 130
      else if (name.includes('chicken') || name.includes('meat')) total += qty * 200
      else if (name.includes('fish')) total += qty * 150
      else total += qty * 150 // default estimate
    })
    return total || 400
  }


  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="text-center">
        <h1 className="text-3xl font-bold text-gray-900 mb-2">Voice Logging</h1>
        <p className="text-gray-600">Speak naturally about your health activities</p>
      </div>

      {/* Voice Recorder */}
      <CoachieCard>
        <div className="p-6 text-center">
          <div className="mb-6">
            <div className={`w-24 h-24 mx-auto rounded-full flex items-center justify-center mb-4 transition-all ${
              isListening
                ? 'bg-red-100 border-4 border-red-500 animate-pulse'
                : isProcessing
                ? 'bg-yellow-100 border-4 border-yellow-500'
                : 'bg-primary-100 border-4 border-primary-500'
            }`}>
              <span className="text-4xl">
                {isListening ? '🎤' : isProcessing ? '⏳' : '🎙️'}
              </span>
            </div>

            <h3 className="text-xl font-semibold text-gray-900 mb-2">
              {isListening ? 'Listening...' : isProcessing ? 'Processing...' : 'Ready to Record'}
            </h3>

            <p className="text-gray-600 mb-6">
              {isListening
                ? 'Speak clearly about your meals, workouts, or water intake'
                : isProcessing
                ? 'Converting your speech to health data...'
                : 'Tap the button below and tell me about your health activities'
              }
            </p>

            {!isSupported && (
              <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-3 mb-4">
                <p className="text-sm text-yellow-800">
                  ⚠️ Speech recognition requires Chrome, Edge, or Safari browser
                </p>
              </div>
            )}

            {error && (
              <div className="bg-red-50 border border-red-200 rounded-lg p-3 mb-4">
                <p className="text-sm text-red-800">{error}</p>
              </div>
            )}
          </div>

          <div className="flex justify-center space-x-4">
            {!isListening && !isProcessing && isSupported && (
              <CoachieButton
                size="lg"
                onClick={startListening}
                className="px-8"
              >
                <span className="mr-2">🎤</span>
                Start Listening
              </CoachieButton>
            )}

            {isListening && (
              <CoachieButton
                size="lg"
                variant="error"
                onClick={stopListening}
                className="px-8"
              >
                <span className="mr-2">⏹️</span>
                Stop Listening
              </CoachieButton>
            )}

            {isProcessing && (
              <div className="flex items-center space-x-2 text-gray-600">
                <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-primary-600"></div>
                <span>Processing...</span>
              </div>
            )}
          </div>
        </div>
      </CoachieCard>

      {/* Transcript */}
      {(transcript || interimTranscript) && (
        <CoachieCard>
          <div className="p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">Transcript</h2>
            <div className="bg-gray-50 rounded-lg p-4">
              <p className="text-gray-800 italic">
                "{transcript || interimTranscript}"
                {interimTranscript && !transcript && (
                  <span className="text-gray-400"> (listening...)</span>
                )}
              </p>
            </div>
          </div>
        </CoachieCard>
      )}

      {/* Parsed Result */}
      {parsedResult && parsedResult.type !== 'unknown' && parsedResult.type !== 'parseError' && (
        <CoachieCard>
          <div className="p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">Recognized Command</h2>
            <div className="border border-gray-200 rounded-lg p-4 mb-4">
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center space-x-2">
                  <span className="text-2xl">
                    {parsedResult.type === 'meal' && '🍽️'}
                    {parsedResult.type === 'water' && '💧'}
                    {parsedResult.type === 'workout' && '🏋️'}
                    {parsedResult.type === 'weight' && '⚖️'}
                    {parsedResult.type === 'sleep' && '😴'}
                    {parsedResult.type === 'mood' && '😊'}
                    {parsedResult.type === 'supplement' && '💊'}
                    {parsedResult.type === 'meditation' && '🧘'}
                    {parsedResult.type === 'habit' && '✅'}
                    {parsedResult.type === 'journal' && '📔'}
                  </span>
                  <span className="font-medium text-gray-900 capitalize">{parsedResult.type}</span>
                </div>
              </div>

              <div className="text-sm text-gray-600">
                {parsedResult.type === 'meal' && (
                  <div>
                    <div>Food: {parsedResult.parsedMeal.foods.length > 0 
                      ? parsedResult.parsedMeal.foods.map(f => f.name).join(', ')
                      : parsedResult.parsedMeal.mealType || 'Meal'}</div>
                    {parsedResult.parsedMeal.totalCalories && (
                      <div>Estimated calories: {parsedResult.parsedMeal.totalCalories}</div>
                    )}
                  </div>
                )}
                {parsedResult.type === 'water' && (
                  <div>{parsedResult.parsedWater.amount}ml of water</div>
                )}
                {parsedResult.type === 'workout' && (
                  <div>
                    <div>Type: {parsedResult.parsedWorkout.workoutType}</div>
                    {parsedResult.parsedWorkout.durationMinutes && (
                      <div>Duration: {parsedResult.parsedWorkout.durationMinutes} minutes</div>
                    )}
                    {parsedResult.parsedWorkout.distance && (
                      <div>Distance: {parsedResult.parsedWorkout.distance} {parsedResult.parsedWorkout.distanceUnit}</div>
                    )}
                  </div>
                )}
                {parsedResult.type === 'weight' && (
                  <div>Weight: {parsedResult.parsedWeight.weight} {parsedResult.parsedWeight.unit}</div>
                )}
                {parsedResult.type === 'sleep' && (
                  <div>
                    {parsedResult.parsedSleep.hours && (
                      <div>Duration: {parsedResult.parsedSleep.hours} hours</div>
                    )}
                    {parsedResult.parsedSleep.quality && (
                      <div>Quality: {parsedResult.parsedSleep.quality}</div>
                    )}
                  </div>
                )}
                {parsedResult.type === 'mood' && (
                  <div>
                    <div>Level: {parsedResult.parsedMood.level}/5</div>
                    {parsedResult.parsedMood.emotions.length > 0 && (
                      <div>Emotions: {parsedResult.parsedMood.emotions.join(', ')}</div>
                    )}
                  </div>
                )}
                {parsedResult.type === 'supplement' && (
                  <div>
                    <div>Name: {parsedResult.parsedSupplement.supplementName}</div>
                    {parsedResult.parsedSupplement.quantity && (
                      <div>Quantity: {parsedResult.parsedSupplement.quantity}</div>
                    )}
                  </div>
                )}
                {parsedResult.type === 'meditation' && (
                  <div>
                    <div>Duration: {parsedResult.parsedMeditation.durationMinutes} minutes</div>
                    <div>Type: {parsedResult.parsedMeditation.meditationType}</div>
                  </div>
                )}
                {parsedResult.type === 'habit' && (
                  <div>
                    <div>Habit: {parsedResult.parsedHabit.habitName}</div>
                    {parsedResult.parsedHabit.notes && (
                      <div>Notes: {parsedResult.parsedHabit.notes}</div>
                    )}
                  </div>
                )}
                {parsedResult.type === 'journal' && (
                  <div>
                    <div>Content: {parsedResult.parsedJournal.content.substring(0, 100)}{parsedResult.parsedJournal.content.length > 100 ? '...' : ''}</div>
                    {parsedResult.parsedJournal.mood && (
                      <div>Mood: {parsedResult.parsedJournal.mood}</div>
                    )}
                  </div>
                )}
              </div>
            </div>

            <div className="flex space-x-2">
              <CoachieButton
                onClick={saveLog}
                className="flex-1"
                disabled={saving}
              >
                <span className="mr-2">✅</span>
                {saving ? 'Saving...' : 'Save Log'}
              </CoachieButton>
              <CoachieButton
                variant="secondary"
                onClick={() => {
                  setParsedResult(null)
                  setTranscript('')
                }}
                className="flex-1"
                disabled={saving}
              >
                <span className="mr-2">❌</span>
                Cancel
              </CoachieButton>
            </div>
          </div>
        </CoachieCard>
      )}

      {/* Extracted Logs */}
      {logs.length > 0 && (
        <CoachieCard>
          <div className="p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">Extracted Health Data</h2>

            <div className="space-y-4">
              {logs.map((log, index) => (
                <div key={index} className="border border-gray-200 rounded-lg p-4">
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center space-x-2">
                      <span className="text-2xl">
                        {log.type === 'meal' && '🍽️'}
                        {log.type === 'water' && '💧'}
                        {log.type === 'workout' && '🏋️'}
                      </span>
                      <span className="font-medium text-gray-900 capitalize">{log.type}</span>
                    </div>
                    <span className="text-sm text-gray-500">
                      {log.timestamp.toLocaleTimeString()}
                    </span>
                  </div>

                  <div className="text-sm text-gray-600">
                    {log.type === 'meal' && `Food: ${log.foodName} (${log.calories} cal)`}
                    {log.type === 'water' && `${log.ml}ml of water`}
                    {log.type === 'workout' && `${log.workoutType} for ${log.durationMin} minutes`}
                  </div>

                  {log.notes && (
                    <div className="mt-2 text-xs text-gray-500 italic">
                      "{log.notes}"
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        </CoachieCard>
      )}

      {/* Tips */}
      <CoachieCard>
        <div className="p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Voice Logging Tips</h2>

          <div className="space-y-3">
            <div className="flex items-start space-x-2">
              <span className="text-primary-600 mt-1">💡</span>
              <p className="text-sm text-gray-600">
                <strong>Meals:</strong> "I ate a chicken salad with dressing for lunch"
              </p>
            </div>

            <div className="flex items-start space-x-2">
              <span className="text-primary-600 mt-1">💡</span>
              <p className="text-sm text-gray-600">
                <strong>Water:</strong> "I drank 3 glasses of water today"
              </p>
            </div>

            <div className="flex items-start space-x-2">
              <span className="text-primary-600 mt-1">💡</span>
              <p className="text-sm text-gray-600">
                <strong>Workouts:</strong> "I went for a 45 minute run this morning"
              </p>
            </div>

            <div className="flex items-start space-x-2">
              <span className="text-primary-600 mt-1">💡</span>
              <p className="text-sm text-gray-600">
                <strong>Habits:</strong> "Complete morning stretch" or "Done with water habit"
              </p>
            </div>

            <div className="flex items-start space-x-2">
              <span className="text-primary-600 mt-1">💡</span>
              <p className="text-sm text-gray-600">
                <strong>Journal:</strong> "Journal about my day" or "Write about feeling grateful"
              </p>
            </div>

            <div className="flex items-start space-x-2">
              <span className="text-primary-600 mt-1">💡</span>
              <p className="text-sm text-gray-600">
                <strong>Speak naturally</strong> - the AI understands casual conversation
              </p>
            </div>
          </div>
        </div>
      </CoachieCard>
    </div>
  )
}
