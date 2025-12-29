'use client'

interface HealthLog {
  type: string
  calories?: number
  hours?: number
  amount?: number
  weight?: number
  unit?: string
  timestamp?: Date
}

interface TodaysLogCardProps {
  meals: HealthLog[]
  workouts: HealthLog[]
  sleepLogs: HealthLog[]
  waterMl: number | null
  weightLogs?: HealthLog[]
  useImperial?: boolean
  onClick?: () => void
}

export default function TodaysLogCard({
  meals,
  workouts,
  sleepLogs,
  waterMl,
  weightLogs = [],
  useImperial = true,
  onClick
}: TodaysLogCardProps) {
  const validSleepLogs = sleepLogs.filter(log => {
    const hours = log.hours || 0
    return hours >= 0 && hours <= 24
  })
  const sleepHours = validSleepLogs.length > 0
    ? validSleepLogs.reduce((max, log) => Math.max(max, log.hours || 0), 0)
    : null

  // Convert ml to glasses (1 glass = 8 oz = 237 ml US standard)
  // Note: Water should always be stored in ml in Firestore
  // If user logged 14 oz, it should be converted to 414 ml before saving
  // 14 oz * 29.5735 = 414 ml
  // 414 ml / 237 ml per glass = 1.75 glasses
  const glassesOfWater = waterMl ? Math.round((waterMl / 237) * 10) / 10 : 0
  const waterDisplay = waterMl
    ? glassesOfWater >= 1
      ? `${glassesOfWater.toFixed(1)} glasses`
      : '<1 glass'
    : 'Not logged'

  return (
    <div
      className="bg-white/10 backdrop-blur-sm rounded-lg border border-white/20 p-4 cursor-pointer hover:bg-white/20 transition-colors"
      onClick={onClick}
    >
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-lg font-bold text-white">Today's Log</h3>
        <svg className="w-6 h-6 text-white" fill="currentColor" viewBox="0 0 20 20">
          <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
        </svg>
      </div>

      <div className="space-y-3">
        <div className="flex justify-between items-center">
          <div className="flex items-center gap-2">
            <svg className="w-5 h-5 text-white/80" fill="currentColor" viewBox="0 0 20 20">
              <path d="M3 1a1 1 0 000 2h1.22l.305 1.222a.997.997 0 00.01.042l1.358 5.43-.893.892C3.74 11.846 4.632 14 6.414 14H15a1 1 0 000-2H6.414l1-1H14a1 1 0 00.894-.553l3-6A1 1 0 0017 3H6.28l-.31-1.243A1 1 0 005 1H3zM16 16.5a1.5 1.5 0 11-3 0 1.5 1.5 0 013 0zM6.5 18a1.5 1.5 0 100-3 1.5 1.5 0 000 3z" />
            </svg>
            <span className="text-white/90">Meals</span>
          </div>
          <span className="text-sm font-semibold text-white">{meals.length} logged</span>
        </div>

        <div className="flex justify-between items-center">
          <div className="flex items-center gap-2">
            <svg className="w-5 h-5 text-white/80" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM9.555 7.168A1 1 0 008 8v4a1 1 0 001.555.832l3-2a1 1 0 000-1.664l-3-2z" clipRule="evenodd" />
            </svg>
            <span className="text-white/90">Workouts</span>
          </div>
          <span className="text-sm font-semibold text-white">{workouts.length} logged</span>
        </div>

        <div className="flex justify-between items-center">
          <div className="flex items-center gap-2">
            <svg className="w-5 h-5 text-white/80" fill="currentColor" viewBox="0 0 20 20">
              <path d="M10.394 2.08a1 1 0 00-.788 0l-7 3a1 1 0 000 1.84L5.25 8.051a.999.999 0 01.356-.257l4-1.714a1 1 0 11.788 1.838L7.667 9.088l1.94.831a1 1 0 00.787 0l7-3a1 1 0 000-1.838l-7-3zM3.31 9.397L5 10.12v4.102a8.969 8.969 0 00-1.05-.174 1 1 0 01-.89-.89 11.115 11.115 0 01.25-3.762zM9.3 11.09a8.97 8.97 0 00.7.074V9.647l1.818.78a3 3 0 002.364 0l5.508-2.361a11.026 11.026 0 01.25 3.762 1 1 0 01-.89.89 8.968 8.968 0 00-5.35 2.524 1 1 0 01-1.4 0l-1.2-1.2a8.97 8.97 0 00-1.4-.174V10.12z" />
            </svg>
            <span className="text-white/90">Sleep</span>
          </div>
          <span className="text-sm font-semibold text-white">
            {sleepHours ? `${sleepHours.toFixed(1)} hrs` : 'Not logged'}
          </span>
        </div>

        <div className="flex justify-between items-center">
          <div className="flex items-center gap-2">
            <svg className="w-5 h-5 text-white/80" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clipRule="evenodd" />
            </svg>
            <span className="text-white/90">Water</span>
          </div>
          <span className="text-sm font-semibold text-white">{waterDisplay}</span>
        </div>

        {weightLogs.length > 0 && (
          <div className="flex justify-between items-center">
            <div className="flex items-center gap-2">
              <svg className="w-5 h-5 text-white/80" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M3 3a1 1 0 000 2v8a2 2 0 002 2h2.586l-1.293 1.293a1 1 0 101.414 1.414L10 15.414l2.293 2.293a1 1 0 001.414-1.414L12.414 15H15a2 2 0 002-2V5a1 1 0 100-2H3zm11.707 4.707a1 1 0 00-1.414-1.414L10 9.586 8.707 8.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
              </svg>
              <span className="text-white/90">Weight</span>
            </div>
            <span className="text-sm font-semibold text-white">
              {(() => {
                const latestWeight = weightLogs.reduce((max, log) => 
                  (log.timestamp || 0) > (max.timestamp || 0) ? log : max
                )
                if (!latestWeight) return 'Not logged'
                const weightInLbs = latestWeight.unit === 'lbs' 
                  ? latestWeight.weight || 0 
                  : (latestWeight.weight || 0) * 2.205
                if (useImperial) {
                  return `${weightInLbs.toFixed(1)} lbs`
                } else {
                  return `${(weightInLbs / 2.205).toFixed(1)} kg`
                }
              })()}
            </span>
          </div>
        )}
      </div>
    </div>
  )
}

